package com.lumera.app.ui.player.base

import android.view.KeyEvent
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ClosedCaption
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.VerticalAlignCenter
import androidx.compose.material.icons.filled.FormatSize
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Icon
import androidx.tv.material3.IconButton
import androidx.tv.material3.IconButtonDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.lumera.app.data.model.stremio.MetaVideo
import com.lumera.app.data.model.stremio.Stream
import com.lumera.app.data.torrent.TorrentProgress
import com.lumera.app.ui.details.GlassSidebar
import com.lumera.app.ui.details.GlassSidebarScaffold
import com.lumera.app.ui.details.SidebarState
import java.text.Collator
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import coil.compose.AsyncImage

private const val CONTROLS_AUTO_HIDE_MS = 3_000L
private const val SEEK_OVERLAY_AUTO_HIDE_MS = 1_500L
private const val PAUSE_OVERLAY_IDLE_MS = 10_000L
private const val SUBTITLE_OFF_TRACK_ID = "#none"

private enum class PlayerPanel {
    NONE,
    SOURCES,
    AUDIO,
    SUBTITLES,
    EPISODES
}

private data class PanelItem(
    val id: String,
    val title: String,
    val subtitle: String? = null
)

private data class PlayerHeaderInfo(
    val primaryText: String,
    val secondaryText: String?
)

private data class SubtitleLanguageGroup(
    val key: String,
    val displayName: String,
    val tracks: List<PlayerTrackOption>,
    val isOffGroup: Boolean = false
)

private data class AudioLanguageGroup(
    val key: String,
    val displayName: String,
    val tracks: List<PlayerTrackOption>
)

private val seriesEpisodePattern = Regex(
    pattern = """^\s*[Ss]\s*(\d+)\s*[:x]?\s*[Ee]\s*(\d+)\s*-\s*(.+)$"""
)


@Composable
fun BasePlayerScaffold(
    playbackController: PlayerPlaybackController,
    renderSurface: PlayerRenderSurface,
    title: String,
    mediaType: String,
    seriesTitle: String? = null,
    logoUrl: String? = null,
    onBack: () -> Unit,
    skipSegmentInfo: SkipSegmentInfo? = null,
    nextEpisodeInfo: NextEpisodeInfo? = null,
    onAutoplayNextEpisode: ((currentSourceUrl: String?) -> Unit)? = null,
    autoplayEnabled: Boolean = false,
    autoplayThresholdMode: String = "percentage",
    autoplayThresholdPercent: Int = 95,
    autoplayThresholdSeconds: Int = 30,
    episodes: List<MetaVideo> = emptyList(),
    currentPlaybackId: String? = null,
    onEpisodeSelected: ((episode: MetaVideo, currentSourceUrl: String?) -> Unit)? = null,
    episodeSwitchSources: List<PlayerSourceOption>? = null,
    isEpisodeSwitchLoading: Boolean = false,
    episodeSwitchTitle: String? = null,
    onEpisodeSwitchSourceSelected: ((sourceUrl: String) -> Unit)? = null,
    onEpisodeSwitchDismissed: (() -> Unit)? = null,
    torrentProgress: TorrentProgress? = null,
    modifier: Modifier = Modifier
) {
    val uiState by playbackController.uiState.collectAsState()
    val sources by playbackController.sourceOptions.collectAsState()
    val audioTracks by playbackController.audioTracks.collectAsState()
    val subtitleTracks by playbackController.subtitleTracks.collectAsState()

    val currentSourceUrl = sources.firstOrNull { it.id == uiState.currentSourceId }?.url

    val containerFocusRequester = remember { FocusRequester() }
    val playPauseFocusRequester = remember { FocusRequester() }
    val seekBarFocusRequester = remember { FocusRequester() }
    val nextEpisodeFocusRequester = remember { FocusRequester() }
    val skipIntroFocusRequester = remember { FocusRequester() }
    val playNextFocusRequester = remember { FocusRequester() }

    var activePanel by remember { mutableStateOf(PlayerPanel.NONE) }
    var showControls by remember { mutableStateOf(true) }
    var showSeekOverlay by remember { mutableStateOf(false) }
    var showPauseOverlay by remember { mutableStateOf(false) }
    var showSubtitleOffsetBar by remember { mutableStateOf(false) }
    var showSubtitleSizeBar by remember { mutableStateOf(false) }
    var showSubtitleDelayBar by remember { mutableStateOf(false) }
    var pendingPreviewSeekPosition by remember { mutableStateOf<Long?>(null) }
    var hideControlsSignal by remember { mutableIntStateOf(0) }
    var hideSeekOverlaySignal by remember { mutableIntStateOf(0) }
    var interactionSignal by remember { mutableIntStateOf(0) }
    var backHideArmed by remember { mutableStateOf(false) }
    var consumeNextBackHandler by remember { mutableStateOf(false) }

    val hasError = !uiState.errorMessage.isNullOrBlank()

    // --- Autoplay next episode (reset when playback controller changes, i.e. new episode) ---
    var autoplayCancelled by remember(playbackController) { mutableStateOf(false) }
    var countdownSeconds by remember(playbackController) { mutableIntStateOf(10) }
    var countdownActive by remember(playbackController) { mutableStateOf(false) }

    val isNearCompletion = remember(uiState.positionMs, uiState.durationMs, skipSegmentInfo, autoplayThresholdMode, autoplayThresholdPercent, autoplayThresholdSeconds, hasError) {
        if (hasError) return@remember false
        val duration = uiState.durationMs
        val position = uiState.positionMs
        if (duration <= 0L) false
        else {
            // Priority 1: Outro timestamp from IntroDB
            val outroStart = skipSegmentInfo?.outroStartMs
            if (outroStart != null && outroStart > 0) {
                position >= outroStart
            } else if (autoplayThresholdMode == "introdb") {
                // Only IntroDB mode — no fallback threshold
                false
            } else {
                // Configurable threshold fallback
                if (autoplayThresholdMode == "time") {
                    val remaining = duration - position
                    remaining <= autoplayThresholdSeconds * 1000L
                } else {
                    val ratio = position.toDouble() / duration.toDouble()
                    ratio >= autoplayThresholdPercent / 100.0
                }
            }
        }
    }

    // Skip intro visibility — never show during error
    val showSkipIntro = remember(uiState.positionMs, skipSegmentInfo, hasError) {
        if (hasError) return@remember false
        val info = skipSegmentInfo ?: return@remember false
        val start = info.introStartMs ?: return@remember false
        val end = info.introEndMs ?: return@remember false
        start > 0 && end > start && uiState.positionMs in start..end
    }

    val shouldShowNextEpisode = autoplayEnabled &&
        isNearCompletion &&
        nextEpisodeInfo != null &&
        onAutoplayNextEpisode != null &&
        !autoplayCancelled &&
        uiState.errorMessage.isNullOrBlank()

    val overlayVisible = countdownActive && nextEpisodeInfo != null && !autoplayCancelled
    val showPlayNextButton = uiState.isEnded &&
        (!autoplayEnabled || autoplayCancelled || !countdownActive) &&
        nextEpisodeInfo != null &&
        onAutoplayNextEpisode != null

    // Start countdown when near completion
    LaunchedEffect(shouldShowNextEpisode) {
        if (shouldShowNextEpisode) {
            countdownSeconds = 10
            countdownActive = true
        } else {
            countdownActive = false
        }
    }

    // Countdown timer — pauses when video is paused or buffering
    LaunchedEffect(countdownActive, uiState.isPlaying) {
        if (!countdownActive || !uiState.isPlaying) return@LaunchedEffect
        while (countdownSeconds > 0) {
            delay(1_000L)
            if (!countdownActive) return@LaunchedEffect
            countdownSeconds--
        }
        // Countdown finished
        if (countdownActive && !autoplayCancelled) {
            onAutoplayNextEpisode?.invoke(currentSourceUrl)
        }
    }

    // Auto-trigger on STATE_ENDED if overlay is showing
    LaunchedEffect(uiState.isEnded) {
        if (uiState.isEnded && countdownActive && !autoplayCancelled) {
            onAutoplayNextEpisode?.invoke(currentSourceUrl)
        }
    }

    // Re-focus buttons when controls hide
    LaunchedEffect(showControls, overlayVisible, showPlayNextButton) {
        if (!showControls) {
            when {
                overlayVisible -> runCatching { nextEpisodeFocusRequester.requestFocus() }
                showPlayNextButton -> runCatching { playNextFocusRequester.requestFocus() }
                showSkipIntro -> runCatching { skipIntroFocusRequester.requestFocus() }
            }
        }
    }

    // Also re-focus skip intro when it first appears and controls are not showing
    LaunchedEffect(showSkipIntro) {
        if (showSkipIntro && !showControls && !overlayVisible) {
            runCatching { skipIntroFocusRequester.requestFocus() }
        }
    }

    val headerInfo = remember(title, mediaType, seriesTitle) {
        resolveHeaderInfo(
            title = title,
            mediaType = mediaType,
            seriesTitle = seriesTitle
        )
    }
    val panelOpen = activePanel != PlayerPanel.NONE
    val displayPositionMs = pendingPreviewSeekPosition ?: uiState.positionMs
    val isPlaybackIntended = uiState.playWhenReady
    val showLoadingOverlay = uiState.errorMessage.isNullOrBlank() &&
        (uiState.isBuffering || !uiState.hasRenderedFirstFrame)
    val canShowPauseOverlay = !isPlaybackIntended &&
        !uiState.isBuffering &&
        uiState.isReady &&
        uiState.hasRenderedFirstFrame &&
        !panelOpen &&
        !showSubtitleOffsetBar &&
        !showSubtitleSizeBar &&
        !showSubtitleDelayBar &&
        uiState.errorMessage.isNullOrBlank()

    fun markInteraction() {
        interactionSignal++
        showPauseOverlay = false
        backHideArmed = false
    }

    fun scheduleHideControls() {
        hideControlsSignal++
    }

    fun showControlsTemporarily() {
        showControls = true
        showSeekOverlay = false
        scheduleHideControls()
    }

    fun showSeekOverlayTemporarily() {
        showSeekOverlay = true
        hideSeekOverlaySignal++
    }

    fun closePanel() {
        markInteraction()
        activePanel = PlayerPanel.NONE
        if (showControls) {
            scheduleHideControls()
        }
    }

    fun handleBackAction() {
        when {
            episodeSwitchSources != null || isEpisodeSwitchLoading -> {
                onEpisodeSwitchDismissed?.invoke()
                activePanel = PlayerPanel.EPISODES
                showControls = true
            }
            !uiState.errorMessage.isNullOrBlank() && !panelOpen -> onBack()
            showSubtitleDelayBar -> {
                markInteraction()
                showSubtitleDelayBar = false
            }
            showSubtitleSizeBar -> {
                markInteraction()
                showSubtitleSizeBar = false
            }
            showSubtitleOffsetBar -> {
                markInteraction()
                showSubtitleOffsetBar = false
            }
            showPauseOverlay -> {
                markInteraction()
                showPauseOverlay = false
                showControls = true
                showSeekOverlay = false
                if (isPlaybackIntended) {
                    scheduleHideControls()
                }
            }
            panelOpen -> closePanel()
            showControls -> {
                markInteraction()
                showControls = false
                showSeekOverlay = false
                backHideArmed = true
            }
            overlayVisible -> {
                autoplayCancelled = true
                countdownActive = false
                showControls = true
                scheduleHideControls()
            }
            !isPlaybackIntended -> onBack()
            backHideArmed && !showSkipIntro && !showPlayNextButton -> onBack()
            else -> {
                markInteraction()
                showControls = true
                showSeekOverlay = false
                backHideArmed = false
                scheduleHideControls()
            }
        }
    }

    fun previewSeekBy(deltaMs: Long) {
        val maxDuration = uiState.durationMs.takeIf { it > 0L } ?: Long.MAX_VALUE
        val basePosition = pendingPreviewSeekPosition ?: uiState.positionMs.coerceAtLeast(0L)
        val target = (basePosition + deltaMs)
            .coerceAtLeast(0L)
            .coerceAtMost(maxDuration)
        pendingPreviewSeekPosition = target
        showSeekOverlayTemporarily()
    }

    fun commitPreviewSeek() {
        val target = pendingPreviewSeekPosition ?: return
        playbackController.seekTo(target)
        pendingPreviewSeekPosition = null
        showSeekOverlayTemporarily()
    }

    BackHandler {
        if (consumeNextBackHandler) {
            consumeNextBackHandler = false
        } else {
            handleBackAction()
        }
    }

    val episodeSwitchOpen = episodeSwitchSources != null || isEpisodeSwitchLoading

    LaunchedEffect(showControls, isPlaybackIntended, panelOpen, showSubtitleOffsetBar, showSubtitleSizeBar, showSubtitleDelayBar, hideControlsSignal, episodeSwitchOpen) {
        if (!showControls || !isPlaybackIntended || panelOpen || showSubtitleOffsetBar || showSubtitleSizeBar || showSubtitleDelayBar) return@LaunchedEffect
        if (!uiState.errorMessage.isNullOrBlank()) return@LaunchedEffect
        if (episodeSwitchOpen) return@LaunchedEffect
        delay(CONTROLS_AUTO_HIDE_MS)
        if (showControls && isPlaybackIntended && !panelOpen && !showSubtitleOffsetBar && !showSubtitleSizeBar && !showSubtitleDelayBar) {
            showControls = false
        }
    }

    LaunchedEffect(showSeekOverlay, showControls, hideSeekOverlaySignal) {
        if (!showSeekOverlay || showControls) return@LaunchedEffect
        delay(SEEK_OVERLAY_AUTO_HIDE_MS)
        if (showSeekOverlay && !showControls) {
            showSeekOverlay = false
        }
    }

    LaunchedEffect(canShowPauseOverlay, interactionSignal) {
        if (!canShowPauseOverlay) {
            showPauseOverlay = false
            return@LaunchedEffect
        }
        delay(PAUSE_OVERLAY_IDLE_MS)
        if (canShowPauseOverlay) {
            showControls = false
            showSeekOverlay = false
            showPauseOverlay = true
        }
    }

    LaunchedEffect(showControls, panelOpen, showSubtitleOffsetBar, showSubtitleSizeBar, showSubtitleDelayBar, hasError, episodeSwitchOpen) {
        if (showSubtitleOffsetBar || showSubtitleSizeBar || showSubtitleDelayBar) return@LaunchedEffect
        if (overlayVisible || showPlayNextButton || showSkipIntro || hasError) return@LaunchedEffect
        if (episodeSwitchOpen) return@LaunchedEffect

        if (showControls && !panelOpen) {
            delay(250)
            runCatching { playPauseFocusRequester.requestFocus() }
        } else if (!showControls && !panelOpen) {
            runCatching { containerFocusRequester.requestFocus() }
        }
    }

    // Close panels when episode switch source sidebar or loading spinner appears
    LaunchedEffect(episodeSwitchSources, isEpisodeSwitchLoading) {
        if (episodeSwitchSources != null || isEpisodeSwitchLoading) {
            activePanel = PlayerPanel.NONE
            showControls = false
        }
    }

    LaunchedEffect(Unit) {
        runCatching { containerFocusRequester.requestFocus() }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
            .focusRequester(containerFocusRequester)
            .focusable()
            .onPreviewKeyEvent { keyEvent ->
                if (isBackKey(keyEvent.nativeKeyEvent.keyCode)) {
                    if (keyEvent.nativeKeyEvent.action == KeyEvent.ACTION_DOWN) {
                        handleBackAction()
                        consumeNextBackHandler = true
                    }
                    return@onPreviewKeyEvent true
                }

                if (!isBackKey(keyEvent.nativeKeyEvent.keyCode) &&
                    (keyEvent.nativeKeyEvent.action == KeyEvent.ACTION_DOWN ||
                        keyEvent.nativeKeyEvent.action == KeyEvent.ACTION_UP)
                ) {
                    markInteraction()
                }

                if (keyEvent.nativeKeyEvent.keyCode != KeyEvent.KEYCODE_CAPTIONS) {
                    return@onPreviewKeyEvent false
                }

                if (keyEvent.nativeKeyEvent.action != KeyEvent.ACTION_UP) {
                    return@onPreviewKeyEvent true
                }

                if (!panelOpen && !episodeSwitchOpen && subtitleTracks.isNotEmpty()) {
                    showControls = true
                    activePanel = PlayerPanel.SUBTITLES
                }
                true
            }
            .onKeyEvent { keyEvent ->
                if (!isBackKey(keyEvent.nativeKeyEvent.keyCode) &&
                    (keyEvent.nativeKeyEvent.action == KeyEvent.ACTION_DOWN ||
                        keyEvent.nativeKeyEvent.action == KeyEvent.ACTION_UP)
                ) {
                    markInteraction()
                }

                // During error, only let back key through (handled in onPreviewKeyEvent)
                if (hasError) return@onKeyEvent false
                if (panelOpen || episodeSwitchOpen) return@onKeyEvent false

                if (keyEvent.nativeKeyEvent.action == KeyEvent.ACTION_UP) {
                    when (keyEvent.nativeKeyEvent.keyCode) {
                        KeyEvent.KEYCODE_DPAD_LEFT,
                        KeyEvent.KEYCODE_DPAD_RIGHT -> {
                            if (!showControls) {
                                commitPreviewSeek()
                                return@onKeyEvent true
                            }
                        }
                    }
                    return@onKeyEvent false
                }

                if (keyEvent.nativeKeyEvent.action != KeyEvent.ACTION_DOWN) {
                    return@onKeyEvent false
                }

                when (keyEvent.nativeKeyEvent.keyCode) {
                    KeyEvent.KEYCODE_DPAD_CENTER,
                    KeyEvent.KEYCODE_ENTER -> {
                        if (!showControls) {
                            playbackController.togglePlayPause()
                            showControlsTemporarily()
                            true
                        } else {
                            false
                        }
                    }

                    KeyEvent.KEYCODE_DPAD_RIGHT -> {
                        if (!showControls) {
                            previewSeekBy(adaptiveSeekDeltaMs(keyEvent.nativeKeyEvent.repeatCount))
                            true
                        } else {
                            false
                        }
                    }

                    KeyEvent.KEYCODE_DPAD_LEFT -> {
                        if (!showControls) {
                            previewSeekBy(-adaptiveSeekDeltaMs(keyEvent.nativeKeyEvent.repeatCount))
                            true
                        } else {
                            false
                        }
                    }

                    KeyEvent.KEYCODE_DPAD_UP -> {
                        if (!showControls) {
                            showControlsTemporarily()
                            true
                        } else {
                            false
                        }
                    }

                    KeyEvent.KEYCODE_DPAD_DOWN -> {
                        if (!showControls) {
                            showControlsTemporarily()
                            true
                        } else {
                            false
                        }
                    }

                    KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE -> {
                        playbackController.togglePlayPause()
                        showControlsTemporarily()
                        true
                    }

                    KeyEvent.KEYCODE_MEDIA_FAST_FORWARD -> {
                        playbackController.seekBy(10_000L)
                        if (showControls) scheduleHideControls() else showSeekOverlayTemporarily()
                        true
                    }

                    KeyEvent.KEYCODE_MEDIA_REWIND -> {
                        playbackController.seekBy(-10_000L)
                        if (showControls) scheduleHideControls() else showSeekOverlayTemporarily()
                        true
                    }

                    else -> false
                }
            }
    ) {
        ComposePlayerSurface(
            renderSurface = renderSurface,
            modifier = Modifier.fillMaxSize()
        )

        AnimatedVisibility(
            visible = showPauseOverlay,
            enter = fadeIn(animationSpec = tween(320)),
            exit = fadeOut(animationSpec = tween(220))
        ) {
            PauseBrandOverlay(
                logoUrl = logoUrl,
                primaryText = headerInfo.primaryText
            )
        }

        AnimatedVisibility(
            visible = showLoadingOverlay,
            enter = fadeIn(animationSpec = tween(150)),
            exit = fadeOut(animationSpec = tween(120))
        ) {
            LoadingOverlay(torrentProgress = torrentProgress)
        }

        AnimatedVisibility(
            visible = showControls && !panelOpen && !showSubtitleOffsetBar && !showSubtitleSizeBar && !showSubtitleDelayBar && uiState.errorMessage.isNullOrBlank(),
            enter = slideInVertically(animationSpec = tween(200)) { -it } + fadeIn(animationSpec = tween(200)),
            exit = slideOutVertically(animationSpec = tween(200)) { -it } + fadeOut(animationSpec = tween(200)),
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = 74.dp, end = 36.dp)
        ) {
            PlayerHeader(
                primaryText = headerInfo.primaryText,
                secondaryText = headerInfo.secondaryText
            )
        }

        AnimatedVisibility(
            visible = showControls && !panelOpen && !showSubtitleOffsetBar && !showSubtitleSizeBar && !showSubtitleDelayBar && uiState.errorMessage.isNullOrBlank(),
            enter = slideInVertically(animationSpec = tween(200)) { it } + fadeIn(animationSpec = tween(200)),
            exit = slideOutVertically(animationSpec = tween(200)) { it } + fadeOut(animationSpec = tween(200))
        ) {
            PlayerControlsOverlay(
                currentPositionMs = displayPositionMs,
                durationMs = uiState.durationMs,
                isPlaying = isPlaybackIntended,
                showSourceControl = sources.size > 1,
                showAudioControl = audioTracks.isNotEmpty(),
                showSubtitleControl = subtitleTracks.isNotEmpty(),
                playPauseFocusRequester = playPauseFocusRequester,
                seekBarFocusRequester = seekBarFocusRequester,
                onPlayPause = {
                    markInteraction()
                    playbackController.togglePlayPause()
                    showControlsTemporarily()
                },
                onSeekBy = { deltaMs ->
                    markInteraction()
                    pendingPreviewSeekPosition = null
                    playbackController.seekBy(deltaMs)
                    scheduleHideControls()
                },
                onShowSourcesPanel = {
                    markInteraction()
                    activePanel = PlayerPanel.SOURCES
                    showControls = true
                    showSeekOverlay = false
                },
                onShowAudioPanel = {
                    markInteraction()
                    activePanel = PlayerPanel.AUDIO
                    showControls = true
                    showSeekOverlay = false
                },
                onShowSubtitlePanel = {
                    markInteraction()
                    activePanel = PlayerPanel.SUBTITLES
                    showControls = true
                    showSeekOverlay = false
                },
                showEpisodesControl = episodes.isNotEmpty() && onEpisodeSelected != null,
                onShowEpisodesPanel = {
                    markInteraction()
                    activePanel = PlayerPanel.EPISODES
                    showControls = true
                    showSeekOverlay = false
                },
                onResetHideTimer = {
                    markInteraction()
                    scheduleHideControls()
                }
            )
        }

        AnimatedVisibility(
            visible = showSeekOverlay && !showControls && !panelOpen && uiState.errorMessage.isNullOrBlank(),
            enter = fadeIn(animationSpec = tween(150)),
            exit = fadeOut(animationSpec = tween(150)),
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            SeekOverlay(
                currentPositionMs = displayPositionMs,
                durationMs = uiState.durationMs
            )
        }

        if (!uiState.errorMessage.isNullOrBlank()) {
            PlayerErrorOverlay(
                errorMessage = uiState.errorMessage.orEmpty(),
                onBack = onBack,
                onSwitchSource = if (sources.size > 1) {
                    {
                        markInteraction()
                        activePanel = PlayerPanel.SOURCES
                        showControls = true
                        showSeekOverlay = false
                    }
                } else null
            )
        }

        PlayerSourceSidebar(
            visible = activePanel == PlayerPanel.SOURCES,
            title = headerInfo.primaryText,
            onClose = { closePanel() },
            sources = sources,
            currentSourceId = uiState.currentSourceId,
            onSelectSource = { sourceId ->
                playbackController.selectSource(sourceId)
                closePanel()
                showSubtitleOffsetBar = false
                showSubtitleSizeBar = false
                showSubtitleDelayBar = false
            }
        )

        PlayerEpisodeSidebar(
            visible = activePanel == PlayerPanel.EPISODES && episodeSwitchSources == null && !isEpisodeSwitchLoading,
            episodes = episodes,
            currentPlaybackId = currentPlaybackId,
            onClose = { closePanel() },
            onEpisodeSelected = { episode ->
                closePanel()
                onEpisodeSelected?.invoke(episode, currentSourceUrl)
            }
        )

        EpisodeSwitchSourceSidebar(
            visible = episodeSwitchSources != null,
            title = episodeSwitchTitle ?: "",
            sources = episodeSwitchSources,
            onClose = {
                onEpisodeSwitchDismissed?.invoke()
                activePanel = PlayerPanel.EPISODES
                showControls = true
            },
            onSelectSource = { sourceUrl ->
                onEpisodeSwitchSourceSelected?.invoke(sourceUrl)
            }
        )

        // Centered loading spinner for auto-resolve paths (binge group, auto-select)
        if (isEpisodeSwitchLoading && episodeSwitchSources == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
            }
        }

        AudioSelectionSidePanel(
            visible = activePanel == PlayerPanel.AUDIO,
            title = "Audio Tracks",
            audioTracks = audioTracks,
            selectedAudioId = uiState.selectedAudioTrackId,
            onClose = { closePanel() },
            onSelectTrack = { trackId ->
                markInteraction()
                playbackController.selectAudioTrack(trackId)
            }
        )

        SubtitleSelectionSidePanel(
            visible = activePanel == PlayerPanel.SUBTITLES,
            title = "Subtitles",
            subtitleTracks = subtitleTracks,
            selectedSubtitleId = uiState.selectedSubtitleTrackId,
            onClose = { closePanel() },
            onSelectTrack = { trackId ->
                markInteraction()
                playbackController.selectSubtitleTrack(trackId)
            },
            onShowOffsetBar = {
                closePanel()
                showSubtitleOffsetBar = true
            },
            onShowSizeBar = {
                closePanel()
                showSubtitleSizeBar = true
            },
            onShowDelayBar = {
                closePanel()
                showSubtitleDelayBar = true
            }
        )

        SubtitleOffsetTopBar(
            visible = showSubtitleOffsetBar,
            offsetPercent = uiState.subtitleVerticalOffsetPercent,
            isPlaying = isPlaybackIntended,
            onPlayPause = {
                markInteraction()
                playbackController.togglePlayPause()
            },
            onDecrement = {
                markInteraction()
                playbackController.setSubtitleVerticalOffset(uiState.subtitleVerticalOffsetPercent - 1)
            },
            onIncrement = {
                markInteraction()
                playbackController.setSubtitleVerticalOffset(uiState.subtitleVerticalOffsetPercent + 1)
            },
            onClose = {
                markInteraction()
                showSubtitleOffsetBar = false
                showControls = true
                scheduleHideControls()
                runCatching { playPauseFocusRequester.requestFocus() }
            }
        )

        SubtitleSizeTopBar(
            visible = showSubtitleSizeBar,
            sizePercent = uiState.subtitleSizePercent,
            isPlaying = isPlaybackIntended,
            onPlayPause = {
                markInteraction()
                playbackController.togglePlayPause()
            },
            onDecrement = {
                markInteraction()
                playbackController.setSubtitleSize(uiState.subtitleSizePercent - 10)
            },
            onIncrement = {
                markInteraction()
                playbackController.setSubtitleSize(uiState.subtitleSizePercent + 10)
            },
            onClose = {
                markInteraction()
                showSubtitleSizeBar = false
                showControls = true
                scheduleHideControls()
                runCatching { playPauseFocusRequester.requestFocus() }
            }
        )

        SubtitleDelayTopBar(
            visible = showSubtitleDelayBar,
            delayMs = uiState.subtitleDelayMs,
            isPlaying = isPlaybackIntended,
            onPlayPause = {
                markInteraction()
                playbackController.togglePlayPause()
            },
            onDecrement = {
                markInteraction()
                playbackController.setSubtitleDelay(uiState.subtitleDelayMs - 100L)
            },
            onIncrement = {
                markInteraction()
                playbackController.setSubtitleDelay(uiState.subtitleDelayMs + 100L)
            },
            onClose = {
                markInteraction()
                showSubtitleDelayBar = false
                showControls = true
                scheduleHideControls()
                runCatching { playPauseFocusRequester.requestFocus() }
            }
        )

        // Animate bottom padding so buttons move above controls when visible
        val controlsVisible = showControls && !panelOpen && !showSubtitleOffsetBar && !showSubtitleSizeBar && !showSubtitleDelayBar && uiState.errorMessage.isNullOrBlank()
        val buttonBottomPadding by animateDpAsState(
            targetValue = if (controlsVisible) 120.dp else 32.dp,
            animationSpec = tween(200),
            label = "buttonBottomPadding"
        )

        // Skip intro button
        AnimatedVisibility(
            visible = showSkipIntro && !overlayVisible,
            enter = fadeIn(animationSpec = tween(300)),
            exit = fadeOut(animationSpec = tween(200)),
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(bottom = buttonBottomPadding)
        ) {
            SkipIntroButton(
                onSkip = {
                    skipSegmentInfo?.introEndMs?.let { endMs ->
                        playbackController.seekTo(endMs)
                    }
                },
                focusRequester = skipIntroFocusRequester
            )
        }

        // Next episode countdown button
        AnimatedVisibility(
            visible = countdownActive && nextEpisodeInfo != null && !autoplayCancelled,
            enter = fadeIn(animationSpec = tween(300)),
            exit = fadeOut(animationSpec = tween(200)),
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(bottom = buttonBottomPadding)
        ) {
            nextEpisodeInfo?.let { info ->
                NextEpisodeButton(
                    info = info,
                    countdownSeconds = countdownSeconds,
                    onPlayNow = { onAutoplayNextEpisode?.invoke(currentSourceUrl) },
                    focusRequester = nextEpisodeFocusRequester
                )
            }
        }

        // Play next episode button (shown after ended + user cancelled countdown)
        AnimatedVisibility(
            visible = showPlayNextButton,
            enter = fadeIn(animationSpec = tween(300)),
            exit = fadeOut(animationSpec = tween(200)),
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(bottom = buttonBottomPadding)
        ) {
            PlayNextEpisodeButton(
                onPlayNext = { onAutoplayNextEpisode?.invoke(currentSourceUrl) },
                focusRequester = playNextFocusRequester
            )
        }
    }
}

@Composable
private fun NextEpisodeButton(
    info: NextEpisodeInfo,
    countdownSeconds: Int,
    onPlayNow: () -> Unit,
    focusRequester: FocusRequester = remember { FocusRequester() }
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    val scale by animateFloatAsState(if (isFocused) 1.05f else 1f, label = "nextEpScale")
    val accentColor = MaterialTheme.colorScheme.primary

    LaunchedEffect(Unit) {
        runCatching { focusRequester.requestFocus() }
    }

    Column(
        modifier = Modifier.padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(
            modifier = Modifier
                .height(40.dp)
                .scale(scale)
                .clip(RoundedCornerShape(8.dp))
                .background(Color.White.copy(0.07f))
                .border(
                    if (isFocused) 2.dp else 1.dp,
                    if (isFocused) accentColor else Color.White.copy(0.15f),
                    RoundedCornerShape(8.dp)
                )
                .clickable(interactionSource = interactionSource, indication = null) { onPlayNow() }
                .focusRequester(focusRequester)
                .focusable(interactionSource = interactionSource)
                .padding(horizontal = 14.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Default.SkipNext,
                contentDescription = null,
                tint = accentColor,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                "NEXT EPISODE IN $countdownSeconds...",
                color = if (isFocused) accentColor else Color.White.copy(0.8f),
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                maxLines = 1
            )
        }
        Spacer(Modifier.height(6.dp))
        Text(
            text = "Press back to cancel",
            style = MaterialTheme.typography.labelSmall,
            color = Color.White.copy(0.5f)
        )
    }
}

@Composable
private fun PlayNextEpisodeButton(
    onPlayNext: () -> Unit,
    focusRequester: FocusRequester = remember { FocusRequester() }
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    val scale by animateFloatAsState(if (isFocused) 1.05f else 1f, label = "playNextScale")
    val accentColor = MaterialTheme.colorScheme.primary

    LaunchedEffect(Unit) {
        runCatching { focusRequester.requestFocus() }
    }

    Row(
        modifier = Modifier
            .padding(32.dp)
            .height(40.dp)
            .scale(scale)
            .clip(RoundedCornerShape(8.dp))
            .background(Color.White.copy(0.07f))
            .border(
                if (isFocused) 2.dp else 1.dp,
                if (isFocused) accentColor else Color.White.copy(0.15f),
                RoundedCornerShape(8.dp)
            )
            .clickable(interactionSource = interactionSource, indication = null) { onPlayNext() }
            .focusRequester(focusRequester)
            .focusable(interactionSource = interactionSource)
            .padding(horizontal = 14.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            Icons.Default.SkipNext,
            contentDescription = null,
            tint = accentColor,
            modifier = Modifier.size(18.dp)
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            "PLAY NEXT EPISODE",
            color = if (isFocused) accentColor else Color.White.copy(0.8f),
            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
            maxLines = 1
        )
    }
}

@Composable
private fun SkipIntroButton(
    onSkip: () -> Unit,
    focusRequester: FocusRequester = remember { FocusRequester() }
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    val scale by animateFloatAsState(if (isFocused) 1.05f else 1f, label = "skipIntroScale")
    val accentColor = MaterialTheme.colorScheme.primary

    LaunchedEffect(Unit) {
        runCatching { focusRequester.requestFocus() }
    }

    Row(
        modifier = Modifier
            .padding(32.dp)
            .height(40.dp)
            .scale(scale)
            .clip(RoundedCornerShape(8.dp))
            .background(Color.White.copy(0.07f))
            .border(
                if (isFocused) 2.dp else 1.dp,
                if (isFocused) accentColor else Color.White.copy(0.15f),
                RoundedCornerShape(8.dp)
            )
            .clickable(interactionSource = interactionSource, indication = null) { onSkip() }
            .focusRequester(focusRequester)
            .focusable(interactionSource = interactionSource)
            .padding(horizontal = 14.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            Icons.Default.SkipNext,
            contentDescription = null,
            tint = accentColor,
            modifier = Modifier.size(18.dp)
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            "SKIP INTRO",
            color = if (isFocused) accentColor else Color.White.copy(0.8f),
            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
            maxLines = 1
        )
    }
}

@Composable
private fun PlayerControlsOverlay(
    currentPositionMs: Long,
    durationMs: Long,
    isPlaying: Boolean,
    showSourceControl: Boolean,
    showAudioControl: Boolean,
    showSubtitleControl: Boolean,
    playPauseFocusRequester: FocusRequester,
    seekBarFocusRequester: FocusRequester,
    onPlayPause: () -> Unit,
    onSeekBy: (Long) -> Unit,
    onShowSourcesPanel: () -> Unit,
    onShowAudioPanel: () -> Unit,
    onShowSubtitlePanel: () -> Unit,
    showEpisodesControl: Boolean = false,
    onShowEpisodesPanel: () -> Unit = {},
    onResetHideTimer: () -> Unit
) {
    Box(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(196.dp)
                .align(Alignment.BottomCenter)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Transparent,
                            Color.Black.copy(alpha = 0.82f)
                        )
                    )
                )
        )

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .padding(horizontal = 28.dp, vertical = 20.dp)
        ) {
            FocusableSeekBar(
                currentPosition = currentPositionMs,
                duration = durationMs,
                onSeekBy = onSeekBy,
                onFocused = onResetHideTimer,
                focusRequester = seekBarFocusRequester,
                downFocusRequester = playPauseFocusRequester
            )

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    ControlButton(
                        icon = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = if (isPlaying) "Pause" else "Play",
                        onClick = onPlayPause,
                        focusRequester = playPauseFocusRequester,
                        onFocused = onResetHideTimer,
                        upFocusRequester = seekBarFocusRequester,
                        buttonSize = 54.dp,
                        iconSize = 29.dp
                    )

                    if (showSubtitleControl) {
                        ControlButton(
                            icon = Icons.Default.ClosedCaption,
                            contentDescription = "Subtitles",
                            onClick = onShowSubtitlePanel,
                            onFocused = onResetHideTimer,
                            buttonSize = 36.dp,
                            iconSize = 18.dp
                        )
                    }

                    if (showAudioControl) {
                        ControlButton(
                            icon = Icons.AutoMirrored.Filled.VolumeUp,
                            contentDescription = "Audio tracks",
                            onClick = onShowAudioPanel,
                            onFocused = onResetHideTimer,
                            buttonSize = 36.dp,
                            iconSize = 20.dp
                        )
                    }

                    if (showEpisodesControl) {
                        ControlButton(
                            icon = Icons.Default.VideoLibrary,
                            contentDescription = "Episodes",
                            onClick = onShowEpisodesPanel,
                            onFocused = onResetHideTimer,
                            buttonSize = 36.dp,
                            iconSize = 18.dp
                        )
                    }

                    if (showSourceControl || showAudioControl || showSubtitleControl) {
                        ControlButton(
                            icon = Icons.Default.SwapHoriz,
                            contentDescription = "More sources",
                            onClick = onShowSourcesPanel,
                            onFocused = onResetHideTimer,
                            buttonSize = 36.dp,
                            iconSize = 18.dp
                        )
                    }
                }

                Text(
                    text = "${formatTime(currentPositionMs)} \u2022 ${formatTime(durationMs)}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.95f)
                )
            }
        }
    }
}

@Composable
private fun ControlButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    focusRequester: FocusRequester? = null,
    onFocused: (() -> Unit)? = null,
    upFocusRequester: FocusRequester? = null,
    downFocusRequester: FocusRequester? = null,
    buttonSize: Dp = 40.dp,
    iconSize: Dp = 20.dp
) {
    IconButton(
        onClick = onClick,
        modifier = Modifier
            .size(buttonSize)
            .then(
                if (focusRequester != null) Modifier.focusRequester(focusRequester)
                else Modifier
            )
            .focusProperties {
                if (upFocusRequester != null) up = upFocusRequester
                if (downFocusRequester != null) down = downFocusRequester
            }
            .onFocusChanged {
                if (it.isFocused) onFocused?.invoke()
            },
        colors = IconButtonDefaults.colors(
            containerColor = Color.Transparent,
            focusedContainerColor = Color.White,
            contentColor = Color.White,
            focusedContentColor = Color.Black
        ),
        shape = IconButtonDefaults.shape(shape = CircleShape)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            modifier = Modifier.size(iconSize)
        )
    }
}

@Composable
private fun FocusableSeekBar(
    currentPosition: Long,
    duration: Long,
    onSeekBy: (Long) -> Unit,
    onFocused: () -> Unit,
    focusRequester: FocusRequester? = null,
    downFocusRequester: FocusRequester? = null
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(20.dp)
            .then(
                if (focusRequester != null) Modifier.focusRequester(focusRequester)
                else Modifier
            )
            .focusProperties {
                if (downFocusRequester != null) down = downFocusRequester
            }
            .onFocusChanged {
                if (it.isFocused) onFocused()
            }
            .onKeyEvent { keyEvent ->
                if (keyEvent.nativeKeyEvent.action != KeyEvent.ACTION_DOWN) {
                    return@onKeyEvent false
                }
                when (keyEvent.nativeKeyEvent.keyCode) {
                    KeyEvent.KEYCODE_DPAD_RIGHT -> {
                        onFocused()
                        onSeekBy(adaptiveSeekDeltaMs(keyEvent.nativeKeyEvent.repeatCount))
                        true
                    }
                    KeyEvent.KEYCODE_DPAD_LEFT -> {
                        onFocused()
                        onSeekBy(-adaptiveSeekDeltaMs(keyEvent.nativeKeyEvent.repeatCount))
                        true
                    }
                    else -> false
                }
            }
            .focusable(interactionSource = interactionSource)
    ) {
        ProgressBar(
            currentPosition = currentPosition,
            duration = duration,
            isFocused = isFocused
        )
    }
}

@Composable
private fun ProgressBar(
    currentPosition: Long,
    duration: Long,
    isFocused: Boolean = false
) {
    val progress = if (duration > 0) {
        (currentPosition.toFloat() / duration.toFloat()).coerceIn(0f, 1f)
    } else {
        0f
    }

    val animatedProgress by animateFloatAsState(
        targetValue = progress,
        animationSpec = tween(100),
        label = "progress"
    )

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .height(18.dp)
    ) {
        val trackHeight = if (isFocused) 5.dp else 4.dp
        val thumbSize = if (isFocused) 12.dp else 9.dp
        val clampedProgress = animatedProgress.coerceIn(0f, 1f)
        val thumbOffset = (maxWidth - thumbSize) * clampedProgress

        Box(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .fillMaxWidth()
                .height(trackHeight)
                .clip(RoundedCornerShape(999.dp))
                .background(Color.White.copy(alpha = 0.34f))
        )

        val primaryColor = MaterialTheme.colorScheme.primary

        Box(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .fillMaxWidth(clampedProgress)
                .height(trackHeight)
                .clip(RoundedCornerShape(999.dp))
                .background(primaryColor)
        )

        Box(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .offset(x = thumbOffset)
                .size(thumbSize)
                .clip(CircleShape)
                .background(primaryColor)
        )
    }
}

@Composable
private fun SeekOverlay(
    currentPositionMs: Long,
    durationMs: Long
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 32.dp, vertical = 24.dp)
    ) {
        ProgressBar(
            currentPosition = currentPositionMs,
            duration = durationMs
        )

        Spacer(modifier = Modifier.height(12.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "${formatTime(currentPositionMs)} / ${formatTime(durationMs)}",
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White.copy(alpha = 0.9f)
            )
        }
    }
}

@Composable
private fun PlayerHeader(
    primaryText: String,
    secondaryText: String?
) {
    Column(
        horizontalAlignment = Alignment.End,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(
            text = primaryText,
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
            color = Color.White,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        if (!secondaryText.isNullOrBlank()) {
            Text(
                text = secondaryText,
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White.copy(alpha = 0.9f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun PauseBrandOverlay(
    logoUrl: String?,
    primaryText: String
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.42f))
    ) {
        Column(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .padding(start = 66.dp, end = 36.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (!logoUrl.isNullOrBlank()) {
                AsyncImage(
                    model = logoUrl,
                    contentDescription = primaryText,
                    modifier = Modifier
                        .width(360.dp)
                        .height(120.dp),
                    contentScale = ContentScale.Fit
                )
            } else {
                Text(
                    text = primaryText,
                    style = MaterialTheme.typography.headlineLarge,
                    color = Color.White,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun PlayerErrorOverlay(
    errorMessage: String,
    onBack: () -> Unit,
    onSwitchSource: (() -> Unit)? = null
) {
    val backFocusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        delay(100)
        runCatching { backFocusRequester.requestFocus() }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .focusable(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            PlayerStatusPill(
                text = errorMessage,
                background = Color(0xFF8B1E1E).copy(alpha = 0.85f)
            )
            Spacer(modifier = Modifier.height(24.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                PlayerErrorButton(
                    icon = Icons.AutoMirrored.Filled.ArrowBack,
                    label = "BACK",
                    onClick = onBack,
                    focusRequester = backFocusRequester
                )
                if (onSwitchSource != null) {
                    PlayerErrorButton(
                        icon = Icons.Default.SwapHoriz,
                        label = "SWITCH SOURCE",
                        onClick = onSwitchSource
                    )
                }
            }
        }
    }
}

@Composable
private fun PlayerErrorButton(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    focusRequester: FocusRequester = remember { FocusRequester() }
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    val scale by animateFloatAsState(if (isFocused) 1.05f else 1f, label = "errorBtnScale")
    val accentColor = MaterialTheme.colorScheme.primary

    Row(
        modifier = Modifier
            .height(40.dp)
            .scale(scale)
            .clip(RoundedCornerShape(8.dp))
            .background(Color.White.copy(0.07f))
            .border(
                if (isFocused) 2.dp else 1.dp,
                if (isFocused) accentColor else Color.White.copy(0.15f),
                RoundedCornerShape(8.dp)
            )
            .clickable(interactionSource = interactionSource, indication = null) { onClick() }
            .focusRequester(focusRequester)
            .focusable(interactionSource = interactionSource)
            .padding(horizontal = 14.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = if (isFocused) accentColor else Color.White.copy(0.8f),
            modifier = Modifier.size(18.dp)
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            label,
            color = if (isFocused) accentColor else Color.White.copy(0.8f),
            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
            maxLines = 1
        )
    }
}

@Composable
private fun PlayerStatusPill(
    text: String,
    background: Color
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(background)
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        Text(
            text = text,
            color = Color.White,
            style = MaterialTheme.typography.labelLarge,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun LoadingOverlay(torrentProgress: TorrentProgress? = null) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.22f)),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator(
                color = MaterialTheme.colorScheme.primary
            )
            if (torrentProgress != null) {
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = torrentProgress.status,
                    color = Color.White.copy(alpha = 0.9f),
                    style = MaterialTheme.typography.bodyMedium
                )
                if (torrentProgress.peers > 0 || torrentProgress.downloadSpeed > 0) {
                    Spacer(modifier = Modifier.height(4.dp))
                    val parts = mutableListOf<String>()
                    if (torrentProgress.downloadSpeed > 0) {
                        parts.add(formatSpeed(torrentProgress.downloadSpeed))
                    }
                    if (torrentProgress.peers > 0) {
                        parts.add("${torrentProgress.peers} peers")
                    }
                    if (torrentProgress.seeds > 0) {
                        parts.add("${torrentProgress.seeds} seeds")
                    }
                    Text(
                        text = parts.joinToString("  \u2022  "),
                        color = Color.White.copy(alpha = 0.6f),
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }
    }
}

private fun formatSpeed(bytesPerSec: Long): String {
    return when {
        bytesPerSec >= 1_048_576 -> "${"%.1f".format(bytesPerSec / 1_048_576.0)} MB/s"
        bytesPerSec >= 1_024 -> "${"%.0f".format(bytesPerSec / 1_024.0)} KB/s"
        else -> "$bytesPerSec B/s"
    }
}

@Composable
private fun BoxScope.AudioSelectionSidePanel(
    visible: Boolean,
    title: String,
    audioTracks: List<PlayerTrackOption>,
    selectedAudioId: String?,
    onClose: () -> Unit,
    onSelectTrack: (String?) -> Unit
) {
    if (!visible) return

    val languageGroups = remember(audioTracks) {
        buildAudioLanguageGroups(audioTracks)
    }
    val selectedGroupKeyFromTrack = remember(languageGroups, selectedAudioId) {
        resolveSelectedAudioLanguageKey(
            groups = languageGroups,
            selectedAudioId = selectedAudioId
        )
    }

    var selectedLanguageKey by remember(visible, selectedGroupKeyFromTrack, languageGroups) {
        mutableStateOf(
            selectedGroupKeyFromTrack ?: languageGroups.firstOrNull()?.key
        )
    }
    var optimisticSelectedAudioId by remember(visible) {
        mutableStateOf<String?>(null)
    }
    val effectiveSelectedAudioId = optimisticSelectedAudioId ?: selectedAudioId

    val selectedLanguageGroup = languageGroups.firstOrNull { it.key == selectedLanguageKey }
        ?: languageGroups.firstOrNull()
    val selectedLanguageTracks = selectedLanguageGroup?.tracks.orEmpty()
    val selectedTrackIndex = remember(selectedLanguageTracks, effectiveSelectedAudioId) {
        val index = selectedLanguageTracks.indexOfFirst { track -> track.id == effectiveSelectedAudioId }
        if (index >= 0) index else 0
    }
    val selectedLanguageIndex = remember(languageGroups, selectedLanguageKey) {
        val index = languageGroups.indexOfFirst { group -> group.key == selectedLanguageKey }
        if (index >= 0) index else 0
    }
    val languageGroupsStructureKey = remember(languageGroups) {
        languageGroups.joinToString(separator = "|") { group ->
            "${group.key}:${group.tracks.size}"
        }
    }

    val languageFocusRequesters = remember(languageGroups.size) {
        List(languageGroups.size) { FocusRequester() }
    }
    val trackFocusRequesters = remember(selectedLanguageTracks.size) {
        List(selectedLanguageTracks.size) { FocusRequester() }
    }
    val selectedTrackFocusRequester = trackFocusRequesters.getOrNull(selectedTrackIndex)
    val panelScope = rememberCoroutineScope()
    val languageListState = rememberLazyListState()
    val trackListState = rememberLazyListState()
    val selectedTrack = remember(audioTracks, effectiveSelectedAudioId) {
        audioTracks.firstOrNull { track -> track.id == effectiveSelectedAudioId }
            ?: audioTracks.firstOrNull { track -> track.selected }
    }
    val currentSelectionText = remember(selectedTrack, selectedLanguageGroup) {
        when {
            selectedTrack == null -> "Current: None"
            else -> {
                val language = selectedLanguageGroup?.displayName?.takeIf { it.isNotBlank() } ?: "Unknown"
                val format = selectedTrack.audioFormat
                if (format != null) "Current: $language - $format" else "Current: $language"
            }
        }
    }
    var lastFocusedLanguageIndex by remember(visible, languageGroupsStructureKey) {
        mutableIntStateOf(selectedLanguageIndex)
    }

    LaunchedEffect(visible, languageGroupsStructureKey) {
        if (!visible || languageGroups.isEmpty()) return@LaunchedEffect

        val targetKey = selectedGroupKeyFromTrack
            ?: selectedLanguageKey
            ?: languageGroups.firstOrNull()?.key
            ?: return@LaunchedEffect
        selectedLanguageKey = targetKey

        val targetIndex = languageGroups.indexOfFirst { group -> group.key == targetKey }
            .takeIf { it >= 0 }
            ?: 0
        lastFocusedLanguageIndex = targetIndex

        runCatching { languageListState.scrollToItem(targetIndex) }
        withFrameNanos { }
        runCatching { languageFocusRequesters[targetIndex].requestFocus() }
    }

    LaunchedEffect(selectedAudioId, languageGroups) {
        val key = resolveSelectedAudioLanguageKey(languageGroups, selectedAudioId) ?: return@LaunchedEffect
        selectedLanguageKey = key
    }
    LaunchedEffect(selectedAudioId) {
        if (
            selectedAudioId != null &&
            optimisticSelectedAudioId != null &&
            selectedAudioId == optimisticSelectedAudioId
        ) {
            optimisticSelectedAudioId = null
        }
    }

    val moveFocusToSelectedTrack = remember(
        selectedLanguageTracks,
        selectedTrackIndex,
        selectedTrackFocusRequester
    ) {
        {
            if (selectedLanguageTracks.isEmpty() || selectedTrackFocusRequester == null) {
                true
            } else {
                panelScope.launch {
                    runCatching { trackListState.scrollToItem(selectedTrackIndex) }
                    withFrameNanos { }
                    runCatching { selectedTrackFocusRequester.requestFocus() }
                }
                true
            }
        }
    }
    val requestAudioSelection: (String?) -> Unit = remember(
        panelScope,
        effectiveSelectedAudioId,
        onSelectTrack
    ) {
        { targetTrackId ->
            if (targetTrackId != null && targetTrackId != effectiveSelectedAudioId) {
                optimisticSelectedAudioId = targetTrackId
                panelScope.launch {
                    onSelectTrack(targetTrackId)
                }
            }
        }
    }

    GlassSidebarScaffold(
        visible = visible,
        onDismiss = onClose,
        panelWidth = 500.dp,
        panelPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp, vertical = 14.dp),
        overlayAlpha = 0.45f,
        enter = EnterTransition.None,
        exit = slideOutHorizontally(
            targetOffsetX = { it },
            animationSpec = tween(durationMillis = 180)
        ) + fadeOut(animationSpec = tween(durationMillis = 120))
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = title,
                        color = Color.White,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = currentSelectionText,
                        color = Color.White.copy(alpha = 0.78f),
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "Languages",
                    color = Color.White.copy(alpha = 0.85f),
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.weight(0.46f)
                )
                Spacer(modifier = Modifier.width(16.dp))
                Text(
                    text = "Tracks",
                    color = Color.White.copy(alpha = 0.85f),
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.weight(0.54f)
                )
            }

            Row(
                modifier = Modifier.fillMaxSize()
            ) {
                Box(
                    modifier = Modifier
                        .weight(0.46f)
                        .fillMaxHeight()
                ) {
                    LazyColumn(
                        state = languageListState,
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.fillMaxHeight()
                    ) {
                        itemsIndexed(
                            items = languageGroups,
                            key = { _, group -> group.key }
                        ) { index, group ->
                            AudioLanguageListItem(
                                group = group,
                                selectedLanguage = group.key == selectedLanguageGroup?.key,
                                activeTrackInGroup = group.tracks.any { it.id == effectiveSelectedAudioId },
                                rightFocusRequester = selectedTrackFocusRequester,
                                onMoveRight = moveFocusToSelectedTrack,
                                focusRequester = languageFocusRequesters[index],
                                onFocused = {
                                    lastFocusedLanguageIndex = index
                                },
                                onClick = {
                                    selectedLanguageKey = group.key
                                    val topTrack = group.tracks.firstOrNull() ?: return@AudioLanguageListItem
                                    requestAudioSelection(topTrack.id)
                                }
                            )
                        }
                        item {
                            Spacer(modifier = Modifier.height(12.dp))
                        }
                    }
                }

                Box(
                    modifier = Modifier
                        .padding(horizontal = 8.dp)
                        .width(1.dp)
                        .fillMaxHeight()
                        .background(Color.White.copy(alpha = 0.3f))
                )

                Box(
                    modifier = Modifier
                        .weight(0.54f)
                        .fillMaxHeight()
                ) {
                    if (selectedLanguageTracks.isEmpty()) {
                        Text(
                            text = "No audio tracks available",
                            color = Color.White.copy(alpha = 0.75f),
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.align(Alignment.TopStart)
                        )
                    } else {
                        LazyColumn(
                            state = trackListState,
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.fillMaxHeight()
                        ) {
                            itemsIndexed(
                                items = selectedLanguageTracks,
                                key = { _, track -> track.id }
                            ) { index, track ->
                                AudioVariantListItem(
                                    track = track,
                                    selected = track.id == effectiveSelectedAudioId,
                                    leftFocusRequester = languageFocusRequesters.getOrNull(lastFocusedLanguageIndex),
                                    focusRequester = trackFocusRequesters.getOrNull(index),
                                    onClick = { requestAudioSelection(track.id) }
                                )
                            }
                            item {
                                Spacer(modifier = Modifier.height(12.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AudioLanguageListItem(
    group: AudioLanguageGroup,
    selectedLanguage: Boolean,
    activeTrackInGroup: Boolean,
    onClick: () -> Unit,
    onMoveRight: (() -> Boolean)? = null,
    rightFocusRequester: FocusRequester? = null,
    onFocused: () -> Unit = {},
    focusRequester: FocusRequester? = null
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()

    val targetBackground = when {
        isFocused -> Color.White.copy(alpha = 0.95f)
        selectedLanguage -> MaterialTheme.colorScheme.primary.copy(alpha = 0.42f)
        else -> Color.Transparent
    }

    val background by animateColorAsState(
        targetValue = targetBackground,
        animationSpec = tween(durationMillis = 120),
        label = "audioLanguageBackground"
    )
    val textColor by animateColorAsState(
        targetValue = if (isFocused) Color.Black else Color.White.copy(alpha = 0.98f),
        animationSpec = tween(durationMillis = 120),
        label = "audioLanguageText"
    )
    val borderColor by animateColorAsState(
        targetValue = if (selectedLanguage && !isFocused) Color.White.copy(alpha = 0.38f) else Color.Transparent,
        animationSpec = tween(durationMillis = 120),
        label = "audioLanguageBorder"
    )
    val scale by animateFloatAsState(
        targetValue = if (isFocused) 1.01f else 1f,
        animationSpec = tween(durationMillis = 120),
        label = "audioLanguageScale"
    )

    val labelText = buildString {
        if (activeTrackInGroup) append("\u2022 ")
        append(group.displayName)
    }

    Box(
        modifier = Modifier
            .scale(scale)
            .fillMaxWidth()
            .clip(RoundedCornerShape(4.dp))
            .background(background)
            .border(1.dp, borderColor, RoundedCornerShape(4.dp))
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
            .focusProperties {
                if (rightFocusRequester != null) right = rightFocusRequester
            }
            .onFocusChanged { focusState ->
                if (focusState.isFocused) onFocused()
            }
            .onPreviewKeyEvent { keyEvent ->
                if (
                    keyEvent.nativeKeyEvent.action == KeyEvent.ACTION_DOWN &&
                    keyEvent.nativeKeyEvent.keyCode == KeyEvent.KEYCODE_DPAD_RIGHT
                ) {
                    onMoveRight?.invoke() == true
                } else {
                    false
                }
            }
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            )
            .focusable(interactionSource = interactionSource)
            .padding(horizontal = 12.dp, vertical = 9.dp)
    ) {
        Text(
            text = labelText,
            style = MaterialTheme.typography.titleMedium,
            color = textColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun AudioVariantListItem(
    track: PlayerTrackOption,
    selected: Boolean,
    onClick: () -> Unit,
    leftFocusRequester: FocusRequester? = null,
    focusRequester: FocusRequester? = null
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()

    val targetBackground = when {
        isFocused -> Color.White.copy(alpha = 0.93f)
        selected -> MaterialTheme.colorScheme.primary.copy(alpha = 0.44f)
        else -> Color.Transparent
    }

    val background by animateColorAsState(
        targetValue = targetBackground,
        animationSpec = tween(durationMillis = 120),
        label = "audioVariantBackground"
    )
    val textColor by animateColorAsState(
        targetValue = when {
            isFocused -> Color.Black
            else -> Color.White.copy(alpha = if (selected) 1f else 0.92f)
        },
        animationSpec = tween(durationMillis = 120),
        label = "audioVariantText"
    )
    val borderColor by animateColorAsState(
        targetValue = when {
            isFocused -> MaterialTheme.colorScheme.primary.copy(alpha = 0.75f)
            selected -> Color.White.copy(alpha = 0.3f)
            else -> Color.Transparent
        },
        animationSpec = tween(durationMillis = 120),
        label = "audioVariantBorder"
    )
    val scale by animateFloatAsState(
        targetValue = if (isFocused) 1.01f else 1f,
        animationSpec = tween(durationMillis = 120),
        label = "audioVariantScale"
    )

    val formatChip = remember(track.audioFormat) {
        track.audioFormat
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
    }

    val labelText = buildString {
        if (selected) append("\u2022 ")
        append(track.label)
    }

    Box(
        modifier = Modifier
            .scale(scale)
            .fillMaxWidth()
            .clip(RoundedCornerShape(4.dp))
            .background(background)
            .border(1.dp, borderColor, RoundedCornerShape(4.dp))
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
            .focusProperties {
                if (leftFocusRequester != null) left = leftFocusRequester
            }
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            )
            .focusable(interactionSource = interactionSource)
            .padding(horizontal = 12.dp, vertical = 9.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = labelText,
                style = MaterialTheme.typography.titleMedium,
                color = textColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            if (formatChip != null) {
                SubtitleMetaChip(
                    text = formatChip,
                    inverted = isFocused
                )
            }
        }
    }
}

@Composable
private fun BoxScope.SubtitleSelectionSidePanel(
    visible: Boolean,
    title: String,
    subtitleTracks: List<PlayerTrackOption>,
    selectedSubtitleId: String?,
    onClose: () -> Unit,
    onSelectTrack: (String?) -> Unit,
    onShowOffsetBar: () -> Unit = {},
    onShowSizeBar: () -> Unit = {},
    onShowDelayBar: () -> Unit = {}
) {
    if (!visible) return

    val languageGroups = remember(subtitleTracks) {
        buildSubtitleLanguageGroups(subtitleTracks)
    }
    val selectedGroupKeyFromTrack = remember(languageGroups, selectedSubtitleId) {
        resolveSelectedSubtitleLanguageKey(
            groups = languageGroups,
            selectedSubtitleId = selectedSubtitleId
        )
    }

    var selectedLanguageKey by remember(visible, selectedGroupKeyFromTrack, languageGroups) {
        mutableStateOf(
            selectedGroupKeyFromTrack ?: languageGroups.firstOrNull()?.key
        )
    }
    var optimisticSelectedSubtitleId by remember(visible) {
        mutableStateOf<String?>(null)
    }
    val effectiveSelectedSubtitleId = optimisticSelectedSubtitleId ?: selectedSubtitleId

    val selectedLanguageGroup = languageGroups.firstOrNull { it.key == selectedLanguageKey }
        ?: languageGroups.firstOrNull()
    val selectedLanguageTracks = if (selectedLanguageGroup?.isOffGroup == true) {
        emptyList()
    } else {
        selectedLanguageGroup?.tracks.orEmpty()
    }
    val selectedTrackIndex = remember(selectedLanguageTracks, effectiveSelectedSubtitleId) {
        val index = selectedLanguageTracks.indexOfFirst { track -> track.id == effectiveSelectedSubtitleId }
        if (index >= 0) index else 0
    }
    val selectedLanguageIndex = remember(languageGroups, selectedLanguageKey) {
        val index = languageGroups.indexOfFirst { group -> group.key == selectedLanguageKey }
        if (index >= 0) index else 0
    }
    val languageGroupsStructureKey = remember(languageGroups) {
        languageGroups.joinToString(separator = "|") { group ->
            "${group.key}:${group.tracks.size}"
        }
    }

    val languageFocusRequesters = remember(languageGroups.size) {
        List(languageGroups.size) { FocusRequester() }
    }
    val trackFocusRequesters = remember(selectedLanguageTracks.size) {
        List(selectedLanguageTracks.size) { FocusRequester() }
    }
    val selectedTrackFocusRequester = trackFocusRequesters.getOrNull(selectedTrackIndex)
    val panelScope = rememberCoroutineScope()
    val languageListState = rememberLazyListState()
    val trackListState = rememberLazyListState()
    val selectedTrack = remember(subtitleTracks, effectiveSelectedSubtitleId) {
        subtitleTracks.firstOrNull { track -> track.id == effectiveSelectedSubtitleId }
            ?: subtitleTracks.firstOrNull { track -> track.selected }
    }
    val currentSelectionText = remember(selectedTrack, selectedLanguageGroup) {
        when {
            selectedTrack == null || isSubtitleOffTrack(selectedTrack) -> "Current: Off"
            else -> {
                val language = selectedLanguageGroup?.displayName?.takeIf { it.isNotBlank() } ?: "Unknown"
                "Current: $language - ${selectedTrack.label}"
            }
        }
    }
    var lastFocusedLanguageIndex by remember(visible, languageGroupsStructureKey) {
        mutableIntStateOf(selectedLanguageIndex)
    }

    LaunchedEffect(visible, languageGroupsStructureKey) {
        if (!visible || languageGroups.isEmpty()) return@LaunchedEffect

        val targetKey = selectedGroupKeyFromTrack
            ?: selectedLanguageKey
            ?: languageGroups.firstOrNull()?.key
            ?: return@LaunchedEffect
        selectedLanguageKey = targetKey

        val targetIndex = languageGroups.indexOfFirst { group -> group.key == targetKey }
            .takeIf { it >= 0 }
            ?: 0
        lastFocusedLanguageIndex = targetIndex

        runCatching { languageListState.scrollToItem(targetIndex) }
        withFrameNanos { }
        runCatching { languageFocusRequesters[targetIndex].requestFocus() }
    }

    LaunchedEffect(selectedSubtitleId, languageGroups) {
        val key = resolveSelectedSubtitleLanguageKey(languageGroups, selectedSubtitleId) ?: return@LaunchedEffect
        selectedLanguageKey = key
    }
    LaunchedEffect(selectedSubtitleId) {
        if (
            selectedSubtitleId != null &&
            optimisticSelectedSubtitleId != null &&
            selectedSubtitleId == optimisticSelectedSubtitleId
        ) {
            optimisticSelectedSubtitleId = null
        }
    }

    val moveFocusToSelectedSubtitle = remember(
        selectedLanguageTracks,
        selectedTrackIndex,
        selectedTrackFocusRequester
    ) {
        {
            if (selectedLanguageTracks.isEmpty() || selectedTrackFocusRequester == null) {
                true
            } else {
                panelScope.launch {
                    runCatching { trackListState.scrollToItem(selectedTrackIndex) }
                    withFrameNanos { }
                    runCatching { selectedTrackFocusRequester.requestFocus() }
                }
                true
            }
        }
    }
    val requestSubtitleSelection: (String?) -> Unit = remember(
        panelScope,
        effectiveSelectedSubtitleId,
        onSelectTrack
    ) {
        { targetTrackId ->
            val resolvedTargetId = targetTrackId ?: SUBTITLE_OFF_TRACK_ID
            if (resolvedTargetId != effectiveSelectedSubtitleId) {
                optimisticSelectedSubtitleId = resolvedTargetId
                panelScope.launch {
                    onSelectTrack(targetTrackId)
                }
            }
        }
    }

    GlassSidebarScaffold(
        visible = visible,
        onDismiss = onClose,
        panelWidth = 500.dp,
        panelPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp, vertical = 14.dp),
        overlayAlpha = 0.45f,
        enter = EnterTransition.None,
        exit = slideOutHorizontally(
            targetOffsetX = { it },
            animationSpec = tween(durationMillis = 180)
        ) + fadeOut(animationSpec = tween(durationMillis = 120))
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onShowOffsetBar,
                    colors = IconButtonDefaults.colors(
                        containerColor = Color.White.copy(alpha = 0.12f),
                        contentColor = Color.White
                    ),
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.VerticalAlignCenter,
                        contentDescription = "Subtitle offset",
                        modifier = Modifier.size(20.dp)
                    )
                }
                Spacer(modifier = Modifier.width(6.dp))
                IconButton(
                    onClick = onShowSizeBar,
                    colors = IconButtonDefaults.colors(
                        containerColor = Color.White.copy(alpha = 0.12f),
                        contentColor = Color.White
                    ),
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.FormatSize,
                        contentDescription = "Subtitle size",
                        modifier = Modifier.size(20.dp)
                    )
                }
                Spacer(modifier = Modifier.width(6.dp))
                IconButton(
                    onClick = onShowDelayBar,
                    colors = IconButtonDefaults.colors(
                        containerColor = Color.White.copy(alpha = 0.12f),
                        contentColor = Color.White
                    ),
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Timer,
                        contentDescription = "Subtitle delay",
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = title,
                    color = Color.White,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = currentSelectionText,
                    color = Color.White.copy(alpha = 0.78f),
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "Languages",
                    color = Color.White.copy(alpha = 0.85f),
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.weight(0.46f)
                )
                Spacer(modifier = Modifier.width(16.dp))
                Text(
                    text = "Subtitles",
                    color = Color.White.copy(alpha = 0.85f),
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.weight(0.54f)
                )
            }

            Row(
                modifier = Modifier.fillMaxSize()
            ) {
                Box(
                    modifier = Modifier
                        .weight(0.46f)
                        .fillMaxHeight()
                ) {
                    LazyColumn(
                        state = languageListState,
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.fillMaxHeight()
                    ) {
                        itemsIndexed(
                            items = languageGroups,
                            key = { _, group -> group.key }
                        ) { index, group ->
                            SubtitleLanguageListItem(
                                group = group,
                                selectedLanguage = group.key == selectedLanguageGroup?.key,
                                activeTrackInGroup = group.tracks.any { it.id == effectiveSelectedSubtitleId },
                                rightFocusRequester = selectedTrackFocusRequester,
                                onMoveRight = moveFocusToSelectedSubtitle,
                                focusRequester = languageFocusRequesters[index],
                                onFocused = {
                                    lastFocusedLanguageIndex = index
                                },
                                onClick = {
                                    selectedLanguageKey = group.key
                                    val topTrack = group.tracks.firstOrNull() ?: return@SubtitleLanguageListItem
                                    requestSubtitleSelection(topTrack.id)
                                }
                            )
                        }
                        item {
                            Spacer(modifier = Modifier.height(12.dp))
                        }
                    }
                }

                Box(
                    modifier = Modifier
                        .padding(horizontal = 8.dp)
                        .width(1.dp)
                        .fillMaxHeight()
                        .background(Color.White.copy(alpha = 0.3f))
                )

                Box(
                    modifier = Modifier
                        .weight(0.54f)
                        .fillMaxHeight()
                ) {
                    if (selectedLanguageGroup?.isOffGroup == true) {
                        Text(
                            text = "Subtitles are off",
                            color = Color.White.copy(alpha = 0.78f),
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.align(Alignment.TopStart)
                        )
                    } else if (selectedLanguageTracks.isEmpty()) {
                        Text(
                            text = "No subtitles available",
                            color = Color.White.copy(alpha = 0.75f),
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.align(Alignment.TopStart)
                        )
                    } else {
                        LazyColumn(
                            state = trackListState,
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.fillMaxHeight()
                        ) {
                            itemsIndexed(
                                items = selectedLanguageTracks,
                                key = { _, track -> track.id }
                            ) { index, track ->
                                SubtitleVariantListItem(
                                    track = track,
                                    selected = track.id == effectiveSelectedSubtitleId,
                                    leftFocusRequester = languageFocusRequesters.getOrNull(lastFocusedLanguageIndex),
                                    focusRequester = trackFocusRequesters.getOrNull(index),
                                    enabled = track.supported,
                                    onClick = { requestSubtitleSelection(track.id) }
                                )
                            }
                            item {
                                Spacer(modifier = Modifier.height(12.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SubtitleLanguageListItem(
    group: SubtitleLanguageGroup,
    selectedLanguage: Boolean,
    activeTrackInGroup: Boolean,
    onClick: () -> Unit,
    onMoveRight: (() -> Boolean)? = null,
    rightFocusRequester: FocusRequester? = null,
    onFocused: () -> Unit = {},
    focusRequester: FocusRequester? = null
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()

    val targetBackground = when {
        isFocused -> Color.White.copy(alpha = 0.95f)
        selectedLanguage -> MaterialTheme.colorScheme.primary.copy(alpha = 0.42f)
        else -> Color.Transparent
    }

    val background by animateColorAsState(
        targetValue = targetBackground,
        animationSpec = tween(durationMillis = 120),
        label = "subtitleLanguageBackground"
    )
    val textColor by animateColorAsState(
        targetValue = if (isFocused) Color.Black else Color.White.copy(alpha = 0.98f),
        animationSpec = tween(durationMillis = 120),
        label = "subtitleLanguageText"
    )
    val borderColor by animateColorAsState(
        targetValue = if (selectedLanguage && !isFocused) Color.White.copy(alpha = 0.38f) else Color.Transparent,
        animationSpec = tween(durationMillis = 120),
        label = "subtitleLanguageBorder"
    )
    val scale by animateFloatAsState(
        targetValue = if (isFocused) 1.01f else 1f,
        animationSpec = tween(durationMillis = 120),
        label = "subtitleLanguageScale"
    )

    val labelText = buildString {
        if (activeTrackInGroup) append("\u2022 ")
        append(group.displayName)
    }

    Box(
        modifier = Modifier
            .scale(scale)
            .fillMaxWidth()
            .clip(RoundedCornerShape(4.dp))
            .background(background)
            .border(1.dp, borderColor, RoundedCornerShape(4.dp))
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
            .focusProperties {
                if (rightFocusRequester != null) right = rightFocusRequester
            }
            .onFocusChanged { focusState ->
                if (focusState.isFocused) onFocused()
            }
            .onPreviewKeyEvent { keyEvent ->
                if (
                    keyEvent.nativeKeyEvent.action == KeyEvent.ACTION_DOWN &&
                    keyEvent.nativeKeyEvent.keyCode == KeyEvent.KEYCODE_DPAD_RIGHT
                ) {
                    onMoveRight?.invoke() == true
                } else {
                    false
                }
            }
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            )
            .focusable(interactionSource = interactionSource)
            .padding(horizontal = 12.dp, vertical = 9.dp)
    ) {
        Text(
            text = labelText,
            style = MaterialTheme.typography.titleMedium,
            color = textColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun SubtitleVariantListItem(
    track: PlayerTrackOption,
    selected: Boolean,
    onClick: () -> Unit,
    leftFocusRequester: FocusRequester? = null,
    focusRequester: FocusRequester? = null,
    enabled: Boolean = true
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()

    val targetBackground = when {
        !enabled -> Color.White.copy(alpha = 0.06f)
        isFocused -> Color.White.copy(alpha = 0.93f)
        selected -> MaterialTheme.colorScheme.primary.copy(alpha = 0.44f)
        else -> Color.Transparent
    }

    val background by animateColorAsState(
        targetValue = targetBackground,
        animationSpec = tween(durationMillis = 120),
        label = "subtitleVariantBackground"
    )
    val textColor by animateColorAsState(
        targetValue = when {
            !enabled -> Color.White.copy(alpha = 0.48f)
            isFocused -> Color.Black
            else -> Color.White.copy(alpha = if (selected) 1f else 0.92f)
        },
        animationSpec = tween(durationMillis = 120),
        label = "subtitleVariantText"
    )
    val borderColor by animateColorAsState(
        targetValue = when {
            isFocused && enabled -> MaterialTheme.colorScheme.primary.copy(alpha = 0.75f)
            selected -> Color.White.copy(alpha = 0.3f)
            else -> Color.Transparent
        },
        animationSpec = tween(durationMillis = 120),
        label = "subtitleVariantBorder"
    )
    val scale by animateFloatAsState(
        targetValue = if (isFocused && enabled) 1.01f else 1f,
        animationSpec = tween(durationMillis = 120),
        label = "subtitleVariantScale"
    )
    val formatChip = remember(track.subtitleFormat) {
        track.subtitleFormat
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
            ?.uppercase(Locale.ROOT)
    }
    val chips = remember(track) { buildSubtitleTrackChips(track) }
    val displayLabel = remember(track.label, track.language, track.id) {
        buildSubtitleVariantDisplayLabel(track)
    }

    val labelText = buildString {
        if (selected) append("\u2022 ")
        append(displayLabel)
    }

    Box(
        modifier = Modifier
            .scale(scale)
            .fillMaxWidth()
            .clip(RoundedCornerShape(4.dp))
            .background(background)
            .border(1.dp, borderColor, RoundedCornerShape(4.dp))
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
            .focusProperties {
                if (leftFocusRequester != null) left = leftFocusRequester
            }
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                enabled = enabled,
                onClick = onClick
            )
            .focusable(interactionSource = interactionSource)
            .padding(horizontal = 12.dp, vertical = 9.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = labelText,
                    style = MaterialTheme.typography.titleMedium,
                    color = textColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                if (formatChip != null) {
                    SubtitleMetaChip(
                        text = formatChip,
                        inverted = isFocused
                    )
                }
            }
            if (chips.isNotEmpty()) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    chips.forEach { chip ->
                        SubtitleMetaChip(
                            text = chip,
                            inverted = isFocused
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SubtitleMetaChip(
    text: String,
    inverted: Boolean
) {
    val background = if (inverted) Color.Black.copy(alpha = 0.12f) else Color.White.copy(alpha = 0.14f)
    val contentColor = if (inverted) Color.Black.copy(alpha = 0.82f) else Color.White.copy(alpha = 0.9f)
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(background)
            .padding(horizontal = 8.dp, vertical = 3.dp)
    ) {
        Text(
            text = text,
            color = contentColor,
            style = MaterialTheme.typography.labelSmall
        )
    }
}

@Composable
private fun BoxScope.PlayerSourceSidebar(
    visible: Boolean,
    title: String,
    sources: List<PlayerSourceOption>,
    currentSourceId: String?,
    onClose: () -> Unit,
    onSelectSource: (String) -> Unit
) {
    val sourceStreams = remember(sources) {
        sources.map { source ->
            Stream(
                name = source.name,
                title = source.title ?: source.label,
                description = source.description,
                url = source.url,
                addonTransportUrl = source.id
            )
        }
    }

    val sidebarState = if (visible) {
        SidebarState.Sources(
            streamTitle = title,
            streams = sourceStreams,
            selectedStreamId = currentSourceId
        )
    } else {
        SidebarState.Closed
    }

    GlassSidebar(
        state = sidebarState,
        onEpisodeSelected = {},
        onSourceSelected = { stream ->
            val sourceId = stream.addonTransportUrl ?: stream.url ?: return@GlassSidebar
            onSelectSource(sourceId)
        },
        onBack = onClose,
        onDismiss = onClose
    )
}

@Composable
private fun BoxScope.PlayerEpisodeSidebar(
    visible: Boolean,
    episodes: List<MetaVideo>,
    currentPlaybackId: String?,
    onClose: () -> Unit,
    onEpisodeSelected: (MetaVideo) -> Unit
) {
    val sidebarState = if (visible) {
        SidebarState.Episodes(episodes)
    } else {
        SidebarState.Closed
    }

    GlassSidebar(
        state = sidebarState,
        currentEpisodeId = currentPlaybackId,
        onEpisodeSelected = onEpisodeSelected,
        onSourceSelected = {},
        onBack = onClose,
        onDismiss = onClose
    )
}

@Composable
private fun BoxScope.EpisodeSwitchSourceSidebar(
    visible: Boolean,
    title: String,
    sources: List<PlayerSourceOption>?,
    onClose: () -> Unit,
    onSelectSource: (String) -> Unit
) {
    val sourceStreams = remember(sources) {
        sources?.map { source ->
            Stream(
                name = source.name,
                title = source.title ?: source.label,
                description = source.description,
                url = source.url,
                addonTransportUrl = source.url
            )
        }
    }

    val sidebarState = if (visible) {
        SidebarState.Sources(
            streamTitle = title,
            streams = sourceStreams,
            selectedStreamId = null
        )
    } else {
        SidebarState.Closed
    }

    GlassSidebar(
        state = sidebarState,
        onEpisodeSelected = {},
        onSourceSelected = { stream ->
            val url = stream.url ?: return@GlassSidebar
            onSelectSource(url)
        },
        onBack = onClose,
        onDismiss = onClose
    )
}

@Composable
private fun BoxScope.SelectionSidePanel(
    visible: Boolean,
    title: String,
    items: List<PanelItem>,
    selectedId: String?,
    onClose: () -> Unit,
    onSelect: (PanelItem) -> Unit
) {
    if (!visible) return

    val firstItemFocusRequester = remember { FocusRequester() }

    LaunchedEffect(visible, items) {
        if (!visible || items.isEmpty()) return@LaunchedEffect
        delay(120)
        runCatching { firstItemFocusRequester.requestFocus() }
    }

    GlassSidebarScaffold(
        visible = visible,
        onDismiss = onClose,
        panelWidth = 500.dp,
        overlayAlpha = 0.45f,
        enter = slideInHorizontally(
            initialOffsetX = { it },
            animationSpec = tween(durationMillis = 220)
        ) + fadeIn(animationSpec = tween(durationMillis = 180)),
        exit = slideOutHorizontally(
            targetOffsetX = { it },
            animationSpec = tween(durationMillis = 180)
        ) + fadeOut(animationSpec = tween(durationMillis = 120))
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = title,
                    color = Color.White,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
            }

            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxHeight()
            ) {
                itemsIndexed(items) { index, item ->
                    PanelListItem(
                        item = item,
                        selected = item.id == selectedId,
                        focusRequester = if (index == 0) firstItemFocusRequester else null,
                        onClick = { onSelect(item) }
                    )
                }
                item {
                    Spacer(modifier = Modifier.height(20.dp))
                }
            }
        }
    }
}

@Composable
private fun PanelListItem(
    item: PanelItem,
    selected: Boolean,
    onClick: () -> Unit,
    focusRequester: FocusRequester? = null
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()

    val targetBackground = when {
        isFocused -> MaterialTheme.colorScheme.primary.copy(alpha = 0.38f)
        selected -> Color.White.copy(alpha = 0.18f)
        else -> Color.White.copy(alpha = 0.06f)
    }

    val background by animateColorAsState(
        targetValue = targetBackground,
        animationSpec = tween(durationMillis = 120),
        label = "panelItemBackground"
    )
    val borderColor by animateColorAsState(
        targetValue = if (isFocused) MaterialTheme.colorScheme.primary else Color.Transparent,
        animationSpec = tween(durationMillis = 120),
        label = "panelItemBorder"
    )
    val scale by animateFloatAsState(
        targetValue = if (isFocused) 1.02f else 1f,
        animationSpec = tween(durationMillis = 120),
        label = "panelItemScale"
    )

    Box(
        modifier = Modifier
            .scale(scale)
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(background)
            .border(1.dp, borderColor, RoundedCornerShape(12.dp))
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            )
            .focusable(interactionSource = interactionSource)
            .padding(horizontal = 14.dp, vertical = 12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = item.title,
                    color = Color.White,
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (!item.subtitle.isNullOrBlank()) {
                    Text(
                        text = item.subtitle,
                        color = Color.White.copy(alpha = 0.72f),
                        style = MaterialTheme.typography.labelLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            if (selected) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

private fun buildSubtitleTrackChips(track: PlayerTrackOption): List<String> {
    if (isSubtitleOffTrack(track)) return emptyList()

    val chips = mutableListOf<String>()
    chips += if (track.isExternal) "Add-on" else "Embedded"

    if (!track.supported) {
        chips += "Unsupported"
    }

    return chips.distinct()
}

private fun buildSubtitleVariantDisplayLabel(track: PlayerTrackOption): String {
    val baseLabel = track.label.trim().ifBlank { "Subtitle" }
    if (isSubtitleOffTrack(track)) return baseLabel

    if (!isGenericSubtitleDescriptorLabel(baseLabel)) return baseLabel

    val languageName = subtitleLanguageDisplayName(
        groupKey = subtitleLanguageKey(track.language),
        rawLanguage = track.language
    ).takeIf { displayName ->
        displayName.isNotBlank() &&
            !displayName.equals("Unknown", ignoreCase = true) &&
            !displayName.equals("Off", ignoreCase = true)
    } ?: return baseLabel

    if (labelAlreadyContainsLanguage(baseLabel, languageName, track.language)) return baseLabel

    return "$languageName [$baseLabel]"
}

private fun isGenericSubtitleDescriptorLabel(rawLabel: String): Boolean {
    val normalized = rawLabel
        .lowercase(Locale.ROOT)
        .replace(Regex("[^\\p{L}\\p{N}]+"), " ")
        .replace(Regex("\\s+"), " ")
        .trim()

    return normalized in setOf(
        "cc",
        "cc1",
        "cc2",
        "cc3",
        "cc4",
        "sdh",
        "forced",
        "caption",
        "captions",
        "closed caption",
        "closed captions",
        "hearing impaired"
    )
}

private fun labelAlreadyContainsLanguage(
    label: String,
    displayLanguage: String,
    rawLanguage: String?
): Boolean {
    val normalizedLabel = label.lowercase(Locale.ROOT)
    if (normalizedLabel.contains(displayLanguage.lowercase(Locale.ROOT))) return true

    val primaryLanguageCode = rawLanguage
        ?.trim()
        ?.replace('_', '-')
        ?.substringBefore('-')
        ?.lowercase(Locale.ROOT)
        ?.takeIf { it.isNotEmpty() }
        ?: return false

    return Regex("""\b$primaryLanguageCode\b""").containsMatchIn(normalizedLabel)
}

private fun buildSubtitleLanguageGroups(
    subtitleTracks: List<PlayerTrackOption>
): List<SubtitleLanguageGroup> {
    if (subtitleTracks.isEmpty()) return emptyList()

    val groups = mutableListOf<SubtitleLanguageGroup>()
    val offTrack = subtitleTracks.firstOrNull { isSubtitleOffTrack(it) }

    if (offTrack != null) {
        groups += SubtitleLanguageGroup(
            key = "__off__",
            displayName = offTrack.label.ifBlank { "Off" },
            tracks = listOf(offTrack),
            isOffGroup = true
        )
    }

    val groupedByLanguage = linkedMapOf<String, MutableList<PlayerTrackOption>>()
    subtitleTracks
        .filterNot { track -> offTrack != null && track.id == offTrack.id }
        .forEach { track ->
            val languageKey = subtitleLanguageKey(track.language)
            groupedByLanguage.getOrPut(languageKey) { mutableListOf() }.add(track)
        }

    val sortedLanguageGroups = groupedByLanguage.map { (languageKey, tracks) ->
        SubtitleLanguageGroup(
            key = languageKey,
            displayName = subtitleLanguageDisplayName(
                groupKey = languageKey,
                rawLanguage = tracks.firstOrNull()?.language
            ),
            tracks = tracks.toList()
        )
    }.let { groupsToSort ->
        val collator = Collator.getInstance(Locale.getDefault()).apply {
            strength = Collator.PRIMARY
        }
        groupsToSort.sortedWith { a, b ->
            val aUnknown = a.key == "und"
            val bUnknown = b.key == "und"
            if (aUnknown != bUnknown) {
                return@sortedWith if (aUnknown) 1 else -1
            }
            val byName = collator.compare(a.displayName, b.displayName)
            if (byName != 0) byName else a.key.compareTo(b.key)
        }
    }

    groups += sortedLanguageGroups

    return groups
}

private fun resolveSelectedSubtitleLanguageKey(
    groups: List<SubtitleLanguageGroup>,
    selectedSubtitleId: String?
): String? {
    if (groups.isEmpty()) return null

    val selectedTrackIdFromOptions = groups
        .asSequence()
        .flatMap { group -> group.tracks.asSequence() }
        .firstOrNull { track -> track.selected }
        ?.id

    val resolvedSelectedId = selectedSubtitleId
        ?: selectedTrackIdFromOptions
        ?: SUBTITLE_OFF_TRACK_ID
    return groups.firstOrNull { group ->
        group.tracks.any { track -> track.id == resolvedSelectedId }
    }?.key ?: groups.firstOrNull()?.key
}

private fun subtitleLanguageKey(language: String?): String = normalizeLanguageToIso2(language)

private fun subtitleLanguageDisplayName(groupKey: String, rawLanguage: String?): String {
    if (groupKey == "__off__") return "Off"
    if (groupKey == "und") return "Unknown"

    if (groupKey.length in 2..3) {
        val canonicalDisplay = Locale.forLanguageTag(groupKey).displayLanguage
            ?.trim()
            ?.takeIf { it.isNotEmpty() && !it.equals(groupKey, ignoreCase = true) }
        if (canonicalDisplay != null) return canonicalDisplay
    }

    val normalizedTag = rawLanguage
        ?.trim()
        ?.replace('_', '-')
        ?.takeIf { it.isNotEmpty() }

    if (groupKey == "und" && normalizedTag == null) {
        return "Unknown"
    }

    val tag = normalizedTag ?: groupKey
    val localeLanguage = Locale.forLanguageTag(tag).displayLanguage
        ?.trim()
        ?.takeIf { it.isNotEmpty() }

    return localeLanguage ?: tag
}

private fun isSubtitleOffTrack(track: PlayerTrackOption): Boolean {
    return track.id == SUBTITLE_OFF_TRACK_ID ||
        (track.language.isNullOrBlank() && track.label.equals("off", ignoreCase = true))
}

private fun buildAudioLanguageGroups(
    audioTracks: List<PlayerTrackOption>
): List<AudioLanguageGroup> {
    if (audioTracks.isEmpty()) return emptyList()

    val groupedByLanguage = linkedMapOf<String, MutableList<PlayerTrackOption>>()
    audioTracks.forEach { track ->
        val languageKey = subtitleLanguageKey(track.language)
        groupedByLanguage.getOrPut(languageKey) { mutableListOf() }.add(track)
    }

    return groupedByLanguage.map { (languageKey, tracks) ->
        AudioLanguageGroup(
            key = languageKey,
            displayName = subtitleLanguageDisplayName(
                groupKey = languageKey,
                rawLanguage = tracks.firstOrNull()?.language
            ),
            tracks = tracks.toList()
        )
    }.let { groupsToSort ->
        val collator = Collator.getInstance(Locale.getDefault()).apply {
            strength = Collator.PRIMARY
        }
        groupsToSort.sortedWith { a, b ->
            val aUnknown = a.key == "und"
            val bUnknown = b.key == "und"
            if (aUnknown != bUnknown) {
                return@sortedWith if (aUnknown) 1 else -1
            }
            val byName = collator.compare(a.displayName, b.displayName)
            if (byName != 0) byName else a.key.compareTo(b.key)
        }
    }
}

private fun resolveSelectedAudioLanguageKey(
    groups: List<AudioLanguageGroup>,
    selectedAudioId: String?
): String? {
    if (groups.isEmpty()) return null

    val selectedTrackIdFromOptions = groups
        .asSequence()
        .flatMap { group -> group.tracks.asSequence() }
        .firstOrNull { track -> track.selected }
        ?.id

    val resolvedSelectedId = selectedAudioId
        ?: selectedTrackIdFromOptions
        ?: return groups.firstOrNull()?.key

    return groups.firstOrNull { group ->
        group.tracks.any { track -> track.id == resolvedSelectedId }
    }?.key ?: groups.firstOrNull()?.key
}



private fun formatTime(millis: Long): String {
    if (millis <= 0L) return "0:00"

    val hours = TimeUnit.MILLISECONDS.toHours(millis)
    val minutes = TimeUnit.MILLISECONDS.toMinutes(millis) % 60
    val seconds = TimeUnit.MILLISECONDS.toSeconds(millis) % 60

    return if (hours > 0L) {
        String.format("%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format("%d:%02d", minutes, seconds)
    }
}

private fun resolveHeaderInfo(
    title: String,
    mediaType: String,
    seriesTitle: String?
): PlayerHeaderInfo {
    val cleanTitle = title.trim().ifBlank { "Untitled" }
    val cleanSeriesTitle = seriesTitle?.trim()?.takeIf { it.isNotBlank() }
    val isSeries = mediaType.equals("series", ignoreCase = true) ||
        mediaType.equals("tv", ignoreCase = true)

    if (!isSeries) {
        return PlayerHeaderInfo(primaryText = cleanTitle, secondaryText = null)
    }

    val normalizedEpisodeLine = normalizeEpisodeLine(cleanTitle)
    val primary = cleanSeriesTitle ?: cleanTitle
    val secondary = when {
        normalizedEpisodeLine != null && cleanSeriesTitle != null -> normalizedEpisodeLine
        cleanSeriesTitle != null && !cleanTitle.equals(cleanSeriesTitle, ignoreCase = true) -> cleanTitle
        else -> null
    }?.takeUnless { candidate ->
        candidate.equals(primary, ignoreCase = true)
    }

    return PlayerHeaderInfo(
        primaryText = primary,
        secondaryText = secondary
    )
}

private fun normalizeEpisodeLine(rawTitle: String): String? {
    val match = seriesEpisodePattern.find(rawTitle.trim()) ?: return null
    val season = match.groupValues[1].toIntOrNull() ?: return null
    val episode = match.groupValues[2].toIntOrNull() ?: return null
    val episodeTitle = match.groupValues[3].trim()
    return "S$season E$episode - $episodeTitle"
}

private fun adaptiveSeekDeltaMs(repeatCount: Int): Long {
    return when {
        repeatCount >= 8 -> 30_000L
        repeatCount >= 3 -> 20_000L
        else -> 10_000L
    }
}

private fun isBackKey(keyCode: Int): Boolean {
    return keyCode == KeyEvent.KEYCODE_BACK || keyCode == KeyEvent.KEYCODE_ESCAPE
}

@Composable
private fun BoxScope.SubtitleOffsetTopBar(
    visible: Boolean,
    offsetPercent: Int,
    isPlaying: Boolean,
    onPlayPause: () -> Unit,
    onDecrement: () -> Unit,
    onIncrement: () -> Unit,
    onClose: () -> Unit
) {
    if (!visible) return

    val playPauseFocus = remember { FocusRequester() }
    val decrementFocus = remember { FocusRequester() }
    val incrementFocus = remember { FocusRequester() }
    val closeFocus = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        withFrameNanos { }
        runCatching { decrementFocus.requestFocus() }
    }

    AnimatedVisibility(
        visible = true,
        enter = fadeIn(animationSpec = tween(180)),
        exit = fadeOut(animationSpec = tween(140)),
        modifier = Modifier
            .align(Alignment.TopCenter)
            .padding(top = 32.dp)
    ) {
        Row(
            modifier = Modifier
                .background(
                    color = Color.Black.copy(alpha = 0.75f),
                    shape = RoundedCornerShape(16.dp)
                )
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            IconButton(
                onClick = onPlayPause,
                colors = IconButtonDefaults.colors(
                    containerColor = Color.White.copy(alpha = 0.15f),
                    contentColor = Color.White
                ),
                modifier = Modifier
                    .size(44.dp)
                    .focusRequester(playPauseFocus)
                    .focusProperties {
                        left = playPauseFocus
                        right = decrementFocus
                        up = playPauseFocus
                        down = playPauseFocus
                    }
            ) {
                Icon(
                    imageVector = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                    contentDescription = if (isPlaying) "Pause" else "Play",
                    modifier = Modifier.size(26.dp)
                )
            }

            IconButton(
                onClick = onDecrement,
                colors = IconButtonDefaults.colors(
                    containerColor = Color.Transparent,
                    contentColor = Color.White
                ),
                modifier = Modifier
                    .size(40.dp)
                    .focusRequester(decrementFocus)
                    .focusProperties {
                        left = playPauseFocus
                        right = incrementFocus
                        up = decrementFocus
                        down = decrementFocus
                    }
            ) {
                Icon(
                    imageVector = Icons.Filled.Remove,
                    contentDescription = "Decrease offset",
                    modifier = Modifier.size(22.dp)
                )
            }

            Text(
                text = "${offsetPercent}%",
                color = Color.White,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.width(56.dp),
                maxLines = 1,
                overflow = TextOverflow.Clip,
                textAlign = TextAlign.Center
            )

            IconButton(
                onClick = onIncrement,
                colors = IconButtonDefaults.colors(
                    containerColor = Color.Transparent,
                    contentColor = Color.White
                ),
                modifier = Modifier
                    .size(40.dp)
                    .focusRequester(incrementFocus)
                    .focusProperties {
                        left = decrementFocus
                        right = closeFocus
                        up = incrementFocus
                        down = incrementFocus
                    }
            ) {
                Icon(
                    imageVector = Icons.Filled.Add,
                    contentDescription = "Increase offset",
                    modifier = Modifier.size(22.dp)
                )
            }

            IconButton(
                onClick = onClose,
                colors = IconButtonDefaults.colors(
                    containerColor = Color.Transparent,
                    contentColor = Color.White
                ),
                modifier = Modifier
                    .size(40.dp)
                    .focusRequester(closeFocus)
                    .focusProperties {
                        left = incrementFocus
                        right = closeFocus
                        up = closeFocus
                        down = closeFocus
                    }
            ) {
                Icon(
                    imageVector = Icons.Filled.Close,
                    contentDescription = "Close offset bar",
                    modifier = Modifier.size(22.dp)
                )
            }
        }
    }
}

@Composable
private fun BoxScope.SubtitleSizeTopBar(
    visible: Boolean,
    sizePercent: Int,
    isPlaying: Boolean,
    onPlayPause: () -> Unit,
    onDecrement: () -> Unit,
    onIncrement: () -> Unit,
    onClose: () -> Unit
) {
    if (!visible) return

    val playPauseFocus = remember { FocusRequester() }
    val decrementFocus = remember { FocusRequester() }
    val incrementFocus = remember { FocusRequester() }
    val closeFocus = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        withFrameNanos { }
        runCatching { decrementFocus.requestFocus() }
    }

    AnimatedVisibility(
        visible = true,
        enter = fadeIn(animationSpec = tween(180)),
        exit = fadeOut(animationSpec = tween(140)),
        modifier = Modifier
            .align(Alignment.TopCenter)
            .padding(top = 32.dp)
    ) {
        Row(
            modifier = Modifier
                .background(
                    color = Color.Black.copy(alpha = 0.75f),
                    shape = RoundedCornerShape(16.dp)
                )
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            IconButton(
                onClick = onPlayPause,
                colors = IconButtonDefaults.colors(
                    containerColor = Color.White.copy(alpha = 0.15f),
                    contentColor = Color.White
                ),
                modifier = Modifier
                    .size(44.dp)
                    .focusRequester(playPauseFocus)
                    .focusProperties {
                        left = playPauseFocus
                        right = decrementFocus
                        up = playPauseFocus
                        down = playPauseFocus
                    }
            ) {
                Icon(
                    imageVector = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                    contentDescription = if (isPlaying) "Pause" else "Play",
                    modifier = Modifier.size(26.dp)
                )
            }

            IconButton(
                onClick = onDecrement,
                colors = IconButtonDefaults.colors(
                    containerColor = Color.Transparent,
                    contentColor = Color.White
                ),
                modifier = Modifier
                    .size(40.dp)
                    .focusRequester(decrementFocus)
                    .focusProperties {
                        left = playPauseFocus
                        right = incrementFocus
                        up = decrementFocus
                        down = decrementFocus
                    }
            ) {
                Icon(
                    imageVector = Icons.Filled.Remove,
                    contentDescription = "Decrease size",
                    modifier = Modifier.size(22.dp)
                )
            }

            Text(
                text = "${sizePercent}%",
                color = Color.White,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.width(56.dp),
                maxLines = 1,
                overflow = TextOverflow.Clip,
                textAlign = TextAlign.Center
            )

            IconButton(
                onClick = onIncrement,
                colors = IconButtonDefaults.colors(
                    containerColor = Color.Transparent,
                    contentColor = Color.White
                ),
                modifier = Modifier
                    .size(40.dp)
                    .focusRequester(incrementFocus)
                    .focusProperties {
                        left = decrementFocus
                        right = closeFocus
                        up = incrementFocus
                        down = incrementFocus
                    }
            ) {
                Icon(
                    imageVector = Icons.Filled.Add,
                    contentDescription = "Increase size",
                    modifier = Modifier.size(22.dp)
                )
            }

            IconButton(
                onClick = onClose,
                colors = IconButtonDefaults.colors(
                    containerColor = Color.Transparent,
                    contentColor = Color.White
                ),
                modifier = Modifier
                    .size(40.dp)
                    .focusRequester(closeFocus)
                    .focusProperties {
                        left = incrementFocus
                        right = closeFocus
                        up = closeFocus
                        down = closeFocus
                    }
            ) {
                Icon(
                    imageVector = Icons.Filled.Close,
                    contentDescription = "Close size bar",
                    modifier = Modifier.size(22.dp)
                )
            }
        }
    }
}

@Composable
private fun BoxScope.SubtitleDelayTopBar(
    visible: Boolean,
    delayMs: Long,
    isPlaying: Boolean,
    onPlayPause: () -> Unit,
    onDecrement: () -> Unit,
    onIncrement: () -> Unit,
    onClose: () -> Unit
) {
    if (!visible) return

    val playPauseFocus = remember { FocusRequester() }
    val decrementFocus = remember { FocusRequester() }
    val incrementFocus = remember { FocusRequester() }
    val closeFocus = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        withFrameNanos { }
        runCatching { decrementFocus.requestFocus() }
    }

    val delayText = remember(delayMs) {
        val seconds = delayMs / 1000.0
        when {
            delayMs > 0L -> "+%.1fs".format(seconds)
            delayMs < 0L -> "%.1fs".format(seconds)
            else -> "0.0s"
        }
    }

    AnimatedVisibility(
        visible = true,
        enter = fadeIn(animationSpec = tween(180)),
        exit = fadeOut(animationSpec = tween(140)),
        modifier = Modifier
            .align(Alignment.TopCenter)
            .padding(top = 32.dp)
    ) {
        Row(
            modifier = Modifier
                .background(
                    color = Color.Black.copy(alpha = 0.75f),
                    shape = RoundedCornerShape(16.dp)
                )
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            IconButton(
                onClick = onPlayPause,
                colors = IconButtonDefaults.colors(
                    containerColor = Color.White.copy(alpha = 0.15f),
                    contentColor = Color.White
                ),
                modifier = Modifier
                    .size(44.dp)
                    .focusRequester(playPauseFocus)
                    .focusProperties {
                        left = playPauseFocus
                        right = decrementFocus
                        up = playPauseFocus
                        down = playPauseFocus
                    }
            ) {
                Icon(
                    imageVector = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                    contentDescription = if (isPlaying) "Pause" else "Play",
                    modifier = Modifier.size(26.dp)
                )
            }

            IconButton(
                onClick = onDecrement,
                colors = IconButtonDefaults.colors(
                    containerColor = Color.Transparent,
                    contentColor = Color.White
                ),
                modifier = Modifier
                    .size(40.dp)
                    .focusRequester(decrementFocus)
                    .focusProperties {
                        left = playPauseFocus
                        right = incrementFocus
                        up = decrementFocus
                        down = decrementFocus
                    }
            ) {
                Icon(
                    imageVector = Icons.Filled.Remove,
                    contentDescription = "Decrease delay",
                    modifier = Modifier.size(22.dp)
                )
            }

            Text(
                text = delayText,
                color = Color.White,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.width(64.dp),
                maxLines = 1,
                overflow = TextOverflow.Clip,
                textAlign = TextAlign.Center
            )

            IconButton(
                onClick = onIncrement,
                colors = IconButtonDefaults.colors(
                    containerColor = Color.Transparent,
                    contentColor = Color.White
                ),
                modifier = Modifier
                    .size(40.dp)
                    .focusRequester(incrementFocus)
                    .focusProperties {
                        left = decrementFocus
                        right = closeFocus
                        up = incrementFocus
                        down = incrementFocus
                    }
            ) {
                Icon(
                    imageVector = Icons.Filled.Add,
                    contentDescription = "Increase delay",
                    modifier = Modifier.size(22.dp)
                )
            }

            IconButton(
                onClick = onClose,
                colors = IconButtonDefaults.colors(
                    containerColor = Color.Transparent,
                    contentColor = Color.White
                ),
                modifier = Modifier
                    .size(40.dp)
                    .focusRequester(closeFocus)
                    .focusProperties {
                        left = incrementFocus
                        right = closeFocus
                        up = closeFocus
                        down = closeFocus
                    }
            ) {
                Icon(
                    imageVector = Icons.Filled.Close,
                    contentDescription = "Close delay bar",
                    modifier = Modifier.size(22.dp)
                )
            }
        }
    }
}
