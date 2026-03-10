package com.lumera.app.ui.details

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lumera.app.data.local.AddonDao
import com.lumera.app.data.model.stremio.MetaItem
import com.lumera.app.data.model.stremio.Stream
import com.lumera.app.data.player.PlaybackTrackSelectionStore
import com.lumera.app.data.player.SourceSelectionStore
import com.lumera.app.data.profile.ProfileConfigurationManager
import com.lumera.app.data.repository.AddonRepository
import com.lumera.app.data.repository.SubtitleRepository
import com.lumera.app.domain.AddonSubtitle
import dagger.hilt.android.lifecycle.HiltViewModel
import com.lumera.app.data.model.WatchHistoryEntity
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class DetailsViewModel @Inject constructor(
    private val dao: AddonDao,
    private val sourceSelectionStore: SourceSelectionStore,
    private val playbackTrackSelectionStore: PlaybackTrackSelectionStore,
    private val repository: AddonRepository,
    private val subtitleRepository: SubtitleRepository,
    private val profileConfigurationManager: ProfileConfigurationManager
) : ViewModel() {

    data class DetailsState(
        val meta: MetaItem? = null,
        val isLoading: Boolean = true,
        val isLoadingStreams: Boolean = false,
        val resumePlaybackId: String? = null,
        val autoPlayStream: Stream? = null,
        val addonSubtitles: List<AddonSubtitle> = emptyList(),
        val availableStreams: List<Stream> = emptyList(),
        val sidebarState: SidebarState = SidebarState.Closed,
        val progressCleared: Boolean = false
    )

    private val _state = MutableStateFlow(DetailsState())
    val state: StateFlow<DetailsState> = _state
    private var loadDetailsJob: Job? = null
    private var loadStreamsJob: Job? = null
    private var loadRequestVersion: Long = 0L
    private var loadedContentKey: String? = null

    // Stash for undo: holds deleted history entries until the user leaves the screen
    private var clearedHistoryStash: List<WatchHistoryEntity> = emptyList()
    private var clearedResumeId: String? = null

    fun loadDetails(type: String, id: String) {
        val requestKey = "$type:$id"

        // Keep current details when reopening the same item (e.g., returning from player).
        if (
            loadedContentKey == requestKey &&
            _state.value.meta != null &&
            !_state.value.isLoading
        ) {
            refreshResumeStateIfNeeded(_state.value.meta)
            if (_state.value.sidebarState !is SidebarState.Closed) {
                _state.value = _state.value.copy(sidebarState = SidebarState.Closed)
            }
            return
        }

        loadDetailsJob?.cancel()
        loadRequestVersion += 1
        val requestVersion = loadRequestVersion

        // Reset immediately so previous movie details never flash for a new item.
        _state.value = DetailsState(
            isLoading = true,
            resumePlaybackId = null,
            autoPlayStream = null,
            addonSubtitles = emptyList(),
            availableStreams = emptyList(),
            sidebarState = SidebarState.Closed
        )

        loadDetailsJob = viewModelScope.launch {
            try {
                val details = repository.resolveMetaDetails(type, id)
                    ?: throw Exception("No meta found")
                if (requestVersion != loadRequestVersion) return@launch
                loadedContentKey = requestKey
                val resumePlaybackId = if (details.type == "series") {
                    dao.getLatestSeriesEpisodeHistory("${details.id}:%")?.id
                } else {
                    dao.getHistoryItem(details.id)?.id
                }
                _state.value = _state.value.copy(
                    meta = details,
                    isLoading = false,
                    resumePlaybackId = resumePlaybackId,
                    autoPlayStream = null,
                    addonSubtitles = emptyList(),
                    availableStreams = emptyList()
                )
            } catch (ce: CancellationException) {
                throw ce
            } catch (e: Exception) {
                if (requestVersion != loadRequestVersion) return@launch
                loadedContentKey = null
                _state.value = _state.value.copy(
                    meta = null,
                    isLoading = false,
                    resumePlaybackId = null,
                    autoPlayStream = null,
                    addonSubtitles = emptyList(),
                    availableStreams = emptyList()
                )
            }
        }
    }

    private fun refreshResumeStateIfNeeded(meta: MetaItem?) {
        if (meta == null) {
            if (_state.value.resumePlaybackId != null) {
                _state.value = _state.value.copy(resumePlaybackId = null)
            }
            return
        }

        viewModelScope.launch {
            val resumePlaybackId = if (meta.type == "series") {
                dao.getLatestSeriesEpisodeHistory("${meta.id}:%")?.id
            } else {
                dao.getHistoryItem(meta.id)?.id
            }
            if (_state.value.meta?.id == meta.id && _state.value.meta?.type == meta.type) {
                _state.value = _state.value.copy(
                    resumePlaybackId = resumePlaybackId,
                    autoPlayStream = null
                )
            }
        }
    }

    fun refreshResumeState() {
        refreshResumeStateIfNeeded(_state.value.meta)
    }

    // 1. Open Episodes (Series)
    fun openEpisodes() {
        val videos = _state.value.meta?.videos ?: emptyList()
        _state.value = _state.value.copy(
            autoPlayStream = null,
            addonSubtitles = emptyList(),
            availableStreams = emptyList(),
            sidebarState = SidebarState.Episodes(videos)
        )
    }

    // 2. Open Sources (Movie OR Specific Episode)
    fun loadStreams(
        type: String,
        id: String,
        displayTitle: String,
        forceSourcePicker: Boolean = false,
        autoSelectSource: Boolean = false,
        rememberSourceSelection: Boolean = true
    ) {
        loadStreamsJob?.cancel()
        loadStreamsJob = viewModelScope.launch {
            // Show immediate loading feedback:
            // - Show sources sidebar when user needs to pick manually
            // - Centered spinner when auto-resolve is expected (auto-select or remembered source)
            val hasRemembered = rememberSourceSelection && sourceSelectionStore.hasRememberedSelection(id)
            val showSidebar = forceSourcePicker || (!autoSelectSource && !hasRemembered)

            _state.value = _state.value.copy(
                autoPlayStream = null,
                addonSubtitles = emptyList(),
                availableStreams = emptyList(),
                isLoadingStreams = true,
                sidebarState = if (showSidebar) SidebarState.Sources(displayTitle, null)
                               else SidebarState.Closed
            )

            try {
                val streamsDeferred = async { repository.getStreams(type, id) }
                val subtitlesDeferred = async { subtitleRepository.getSubtitles(type, id) }

                val streams = streamsDeferred.await()
                val addonSubtitles = subtitlesDeferred.await()
                val preferredStream = if (forceSourcePicker || !rememberSourceSelection) {
                    null
                } else {
                    sourceSelectionStore.findPreferredStream(id, streams)
                }

                if (preferredStream != null) {
                    _state.value = _state.value.copy(
                        isLoadingStreams = false,
                        sidebarState = SidebarState.Closed,
                        autoPlayStream = preferredStream,
                        addonSubtitles = addonSubtitles,
                        availableStreams = streams
                    )
                    return@launch
                }

                // Auto-select first playable source when enabled
                if (autoSelectSource && !forceSourcePicker) {
                    val firstPlayable = streams.firstOrNull {
                        !it.url.isNullOrBlank() || !it.infoHash.isNullOrBlank()
                    }
                    if (firstPlayable != null) {
                        _state.value = _state.value.copy(
                            isLoadingStreams = false,
                            sidebarState = SidebarState.Closed,
                            autoPlayStream = firstPlayable,
                            addonSubtitles = addonSubtitles,
                            availableStreams = streams
                        )
                        return@launch
                    }
                }

                // Update sidebar with results
                _state.value = _state.value.copy(
                    isLoadingStreams = false,
                    autoPlayStream = null,
                    addonSubtitles = addonSubtitles,
                    availableStreams = streams,
                    sidebarState = SidebarState.Sources(displayTitle, streams)
                )
            } catch (e: Exception) {
                _state.value = _state.value.copy(
                    isLoadingStreams = false,
                    autoPlayStream = null,
                    addonSubtitles = emptyList(),
                    availableStreams = emptyList(),
                    sidebarState = SidebarState.Sources(displayTitle, emptyList())
                )
            }
        }
    }

    fun consumeAutoPlayStream() {
        if (_state.value.autoPlayStream == null) return
        _state.value = _state.value.copy(autoPlayStream = null)
    }

    // --- Clear Progress (with undo) ---

    fun clearProgress() {
        val meta = _state.value.meta ?: return
        val currentResumeId = _state.value.resumePlaybackId ?: return
        clearedResumeId = currentResumeId

        viewModelScope.launch(Dispatchers.IO + NonCancellable) {
            clearedHistoryStash = if (meta.type == "series") {
                dao.getSeriesEpisodeHistory("${meta.id}:%")
            } else {
                listOfNotNull(dao.getHistoryItem(meta.id))
            }

            if (meta.type == "series") {
                dao.deleteSeriesHistory("${meta.id}:%")
                sourceSelectionStore.clearSelectionsForPrefix(meta.id)
                playbackTrackSelectionStore.clearSelectionsForPrefix(meta.id)
            } else {
                dao.deleteHistoryItem(meta.id)
                sourceSelectionStore.clearSelection(meta.id)
                playbackTrackSelectionStore.clearSelection(meta.id)
            }

            profileConfigurationManager.saveActiveRuntimeState()

            _state.value = _state.value.copy(
                resumePlaybackId = null,
                progressCleared = true
            )
        }
    }

    fun undoClearProgress() {
        if (clearedHistoryStash.isEmpty()) return
        val stash = clearedHistoryStash
        val resumeId = clearedResumeId

        viewModelScope.launch(Dispatchers.IO + NonCancellable) {
            dao.upsertHistoryItems(stash)
            profileConfigurationManager.saveActiveRuntimeState()
            clearedHistoryStash = emptyList()
            clearedResumeId = null

            _state.value = _state.value.copy(
                resumePlaybackId = resumeId,
                progressCleared = false
            )
        }
    }

    /** Drop the undo stash so progress deletion becomes permanent. */
    fun commitClearProgress() {
        clearedHistoryStash = emptyList()
        clearedResumeId = null
        if (_state.value.progressCleared) {
            _state.value = _state.value.copy(progressCleared = false)
        }
    }

    // 3. Close Logic
    fun closeSidebar() {
        loadStreamsJob?.cancel()
        loadStreamsJob = null
        _state.value = _state.value.copy(
            isLoadingStreams = false,
            autoPlayStream = null,
            availableStreams = emptyList(),
            sidebarState = SidebarState.Closed
        )
    }

    // 4. Back Button Logic (Drill Up)
    fun goBackInSidebar() {
        val currentState = _state.value.sidebarState

        // If viewing Sources for a Series, go back to Episode List
        if (currentState is SidebarState.Sources && _state.value.meta?.type == "series") {
            openEpisodes()
        } else {
            closeSidebar()
        }
    }
}
