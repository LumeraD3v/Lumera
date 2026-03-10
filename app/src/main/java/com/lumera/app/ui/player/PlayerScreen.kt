package com.lumera.app.ui.player

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.compose.runtime.collectAsState
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.delay
import com.lumera.app.ui.player.base.BasePlayerScaffold
import com.lumera.app.ui.player.base.NextEpisodeInfo
import com.lumera.app.ui.player.base.PlaybackSettings
import com.lumera.app.ui.player.base.ExoPlayerBackend
import com.lumera.app.ui.player.base.PlayerBackendFactory
import com.lumera.app.ui.player.base.PlayerBackendType
import com.lumera.app.ui.player.base.PlayerLoadRequest
import com.lumera.app.ui.player.base.PlayerSourceOption
import com.lumera.app.ui.player.base.PlayerSubtitleSource
import com.lumera.app.ui.player.base.SkipSegmentInfo
import com.lumera.app.data.model.stremio.MetaVideo
import com.lumera.app.data.torrent.TorrentProgress

data class PlayerSessionResult(
    val positionMs: Long,
    val durationMs: Long?,
    val isCompleted: Boolean,
    val selectedSourceUrl: String?,
    val selectedAudioTrackId: String?,
    val selectedSubtitleTrackId: String?,
    val subtitleVerticalOffsetPercent: Int = 0,
    val subtitleSizePercent: Int = 100,
    val subtitleDelayMs: Long = 0L
)

@Composable
fun PlayerScreen(
    videoUrl: String,
    title: String,
    seriesTitle: String? = null,
    logoUrl: String? = null,
    poster: String,
    movieId: String,
    mediaType: String,
    onBack: (PlayerSessionResult) -> Unit,
    backendType: PlayerBackendType = PlayerBackendType.EXOPLAYER,
    sources: List<PlayerSourceOption> = emptyList(),
    subtitles: List<PlayerSubtitleSource> = emptyList(),
    preferredAudioTrackId: String? = null,
    preferredSubtitleTrackId: String? = null,
    initialSubtitleVerticalOffsetPercent: Int = 0,
    initialSubtitleSizePercent: Int = 100,
    initialSubtitleDelayMs: Long = 0L,
    playbackSettings: PlaybackSettings = PlaybackSettings(),
    skipSegmentInfo: SkipSegmentInfo? = null,
    nextEpisodeInfo: NextEpisodeInfo? = null,
    onAutoplayNextEpisode: ((currentSourceUrl: String?) -> Unit)? = null,
    episodes: List<MetaVideo> = emptyList(),
    currentPlaybackId: String? = null,
    onEpisodeSelected: ((episode: MetaVideo, currentSourceUrl: String?) -> Unit)? = null,
    episodeSwitchSources: List<PlayerSourceOption>? = null,
    isEpisodeSwitchLoading: Boolean = false,
    episodeSwitchTitle: String? = null,
    onEpisodeSwitchSourceSelected: ((sourceUrl: String) -> Unit)? = null,
    onEpisodeSwitchDismissed: (() -> Unit)? = null,
    onMagnetSourceSelected: ((magnetUrl: String, fileIdx: Int, fileName: String, onReady: (localUrl: String) -> Unit) -> Unit)? = null,
    torrentProgress: TorrentProgress? = null,
    viewModel: PlayerViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val hostView = LocalView.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val runtime = remember(movieId, backendType, playbackSettings) {
        PlayerBackendFactory.create(context, backendType, playbackSettings)
    }
    val playbackController = runtime.playbackController
    val renderSurface = runtime.renderSurface

    LaunchedEffect(playbackController, onMagnetSourceSelected) {
        (playbackController as? ExoPlayerBackend)?.onMagnetSourceSelected = onMagnetSourceSelected
    }

    // Pre-create ExoPlayer + OkHttpClient while torrent pieces are still downloading.
    // By the time the URL arrives, the player is ready — prepareSource() just calls prepare().
    LaunchedEffect(playbackController, videoUrl) {
        if (videoUrl.isBlank()) {
            (playbackController as? ExoPlayerBackend)?.warmup()
        }
    }
    val uiState by playbackController.uiState.collectAsState()
    val shouldKeepScreenOn = uiState.playWhenReady || uiState.isPlaying || uiState.isBuffering

    DisposableEffect(hostView, shouldKeepScreenOn) {
        hostView.keepScreenOn = shouldKeepScreenOn
        onDispose {
            hostView.keepScreenOn = false
        }
    }

    DisposableEffect(playbackController) {
        onDispose {
            playbackController.release()
        }
    }

    DisposableEffect(lifecycleOwner, playbackController) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) {
                playbackController.pause()
                val state = playbackController.uiState.value
                val pos = state.positionMs.coerceAtLeast(0L)
                val dur = state.durationMs.takeIf { it > 0L }
                viewModel.saveProgress(
                    id = movieId,
                    type = mediaType,
                    title = title,
                    poster = poster,
                    position = pos,
                    duration = dur
                )
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    LaunchedEffect(playbackController) {
        while (true) {
            delay(30_000L)
            val state = playbackController.uiState.value
            if (state.isPlaying && state.positionMs > 0L) {
                viewModel.saveProgress(
                    id = movieId,
                    type = mediaType,
                    title = title,
                    poster = poster,
                    position = state.positionMs.coerceAtLeast(0L),
                    duration = state.durationMs.takeIf { it > 0L }
                )
            }
        }
    }

    LaunchedEffect(movieId, videoUrl, backendType) {
        if (videoUrl.isBlank()) return@LaunchedEffect // Wait for torrent stream URL
        val resumePosition = viewModel.getResumePosition(movieId)
        playbackController.load(
            PlayerLoadRequest(
                mediaUrl = videoUrl,
                title = title,
                startPositionMs = resumePosition,
                autoPlay = true,
                sources = sources,
                subtitles = subtitles,
                preferredAudioTrackId = preferredAudioTrackId,
                preferredSubtitleTrackId = preferredSubtitleTrackId
            )
        )
        if (initialSubtitleVerticalOffsetPercent != 0) {
            playbackController.setSubtitleVerticalOffset(initialSubtitleVerticalOffsetPercent)
        }
        if (initialSubtitleSizePercent != 100) {
            playbackController.setSubtitleSize(initialSubtitleSizePercent)
        }
        if (initialSubtitleDelayMs != 0L) {
            playbackController.setSubtitleDelay(initialSubtitleDelayMs)
        }
    }

    val persistAndBack = {
        val hasError = !uiState.errorMessage.isNullOrBlank()
        val position = uiState.positionMs.coerceAtLeast(0L)
        val duration = uiState.durationMs.takeIf { it > 0L }
        val remaining = duration?.minus(position) ?: Long.MAX_VALUE
        val completionRatio = if (duration != null && duration > 0L) {
            position.toDouble() / duration.toDouble()
        } else {
            0.0
        }

        // Don't save progress when exiting due to error
        if (!hasError) {
            if (completionRatio >= 0.98 || remaining <= 30_000L) {
                viewModel.markCompleted(movieId)
            } else {
                viewModel.saveProgress(
                    id = movieId,
                    type = mediaType,
                    title = title,
                    poster = poster,
                    position = position,
                    duration = duration
                )
            }
        }
        val selectedSourceUrl = sources.firstOrNull { it.id == uiState.currentSourceId }?.url
            ?: videoUrl
        onBack(
            PlayerSessionResult(
                positionMs = if (hasError) 0L else position,
                durationMs = duration,
                isCompleted = !hasError && (completionRatio >= 0.98 || remaining <= 30_000L),
                selectedSourceUrl = selectedSourceUrl,
                selectedAudioTrackId = uiState.selectedAudioTrackId,
                selectedSubtitleTrackId = uiState.selectedSubtitleTrackId,
                subtitleVerticalOffsetPercent = uiState.subtitleVerticalOffsetPercent,
                subtitleSizePercent = uiState.subtitleSizePercent,
                subtitleDelayMs = uiState.subtitleDelayMs
            )
        )
    }

    Box(modifier = Modifier.fillMaxSize()) {
        BasePlayerScaffold(
            playbackController = playbackController,
            renderSurface = renderSurface,
            title = title,
            seriesTitle = seriesTitle,
            logoUrl = logoUrl,
            mediaType = mediaType,
            onBack = persistAndBack,
            skipSegmentInfo = skipSegmentInfo,
            nextEpisodeInfo = nextEpisodeInfo,
            onAutoplayNextEpisode = onAutoplayNextEpisode,
            autoplayEnabled = playbackSettings.autoplayNextEpisode,
            autoplayThresholdMode = playbackSettings.autoplayThresholdMode,
            autoplayThresholdPercent = playbackSettings.autoplayThresholdPercent,
            autoplayThresholdSeconds = playbackSettings.autoplayThresholdSeconds,
            episodes = episodes,
            currentPlaybackId = currentPlaybackId,
            onEpisodeSelected = onEpisodeSelected,
            episodeSwitchSources = episodeSwitchSources,
            isEpisodeSwitchLoading = isEpisodeSwitchLoading,
            episodeSwitchTitle = episodeSwitchTitle,
            onEpisodeSwitchSourceSelected = onEpisodeSwitchSourceSelected,
            onEpisodeSwitchDismissed = onEpisodeSwitchDismissed,
            torrentProgress = torrentProgress
        )
    }
}
