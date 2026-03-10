package com.lumera.app.ui.player.base

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import kotlinx.coroutines.flow.StateFlow

interface PlayerPlaybackController {
    val backendType: PlayerBackendType
    val uiState: StateFlow<PlayerUiState>
    val sourceOptions: StateFlow<List<PlayerSourceOption>>
    val audioTracks: StateFlow<List<PlayerTrackOption>>
    val subtitleTracks: StateFlow<List<PlayerTrackOption>>

    fun load(request: PlayerLoadRequest)

    fun play()
    fun pause()
    fun togglePlayPause() {
        if (uiState.value.playWhenReady) pause() else play()
    }

    fun seekTo(positionMs: Long)
    fun seekBy(deltaMs: Long)
    fun setPlaybackSpeed(speed: Float)

    fun selectSource(sourceId: String)
    fun selectAudioTrack(trackId: String?)
    fun selectSubtitleTrack(trackId: String?)

    fun setSubtitleVerticalOffset(percent: Int)
    fun setSubtitleSize(percent: Int)
    fun setSubtitleDelay(delayMs: Long)
    fun setSubtitleTextColor(color: Int)
    fun setSubtitleBackgroundColor(color: Int)

    fun release()
}

interface PlayerRenderSurface {
    val backendType: PlayerBackendType

    @Composable
    fun Content(modifier: Modifier)
}

data class PlayerRuntime(
    val playbackController: PlayerPlaybackController,
    val renderSurface: PlayerRenderSurface
)
