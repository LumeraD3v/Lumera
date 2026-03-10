package com.lumera.app.ui.player.base

import androidx.compose.runtime.Immutable

@Immutable
data class PlayerSourceOption(
    val id: String,
    val url: String,
    val label: String,
    val name: String? = null,
    val title: String? = null,
    val description: String? = null,
    val fileIdx: Int = -1,
    val fileName: String = ""
)

@Immutable
data class PlayerSubtitleSource(
    val id: String,
    val url: String,
    val label: String,
    val language: String? = null
)

data class PlayerTrackOption(
    val id: String,
    val label: String,
    val language: String? = null,
    val selected: Boolean = false,
    val supported: Boolean = true,
    val isExternal: Boolean = false,
    val roleFlags: Int = 0,
    val selectionFlags: Int = 0,
    val subtitleFormat: String? = null,
    val audioFormat: String? = null
)

data class PlayerLoadRequest(
    val mediaUrl: String,
    val title: String,
    val startPositionMs: Long = 0L,
    val autoPlay: Boolean = true,
    val sources: List<PlayerSourceOption> = emptyList(),
    val subtitles: List<PlayerSubtitleSource> = emptyList(),
    val preferredAudioTrackId: String? = null,
    val preferredSubtitleTrackId: String? = null
)

@Immutable
data class PlaybackSettings(
    val tunnelingEnabled: Boolean = false,
    val mapDV7ToHevc: Boolean = false,
    val decoderPriority: Int = 1,
    val frameRateMatching: Boolean = false,
    val autoplayNextEpisode: Boolean = false,
    val autoSelectSource: Boolean = false,
    val autoplayThresholdMode: String = "percentage",
    val autoplayThresholdPercent: Int = 95,
    val autoplayThresholdSeconds: Int = 30,
    val preferredAudioLanguage: String = "",
    val preferredAudioLanguageSecondary: String = "",
    val preferredSubtitleLanguage: String = "",
    val preferredSubtitleLanguageSecondary: String = "",
    val subtitleSize: Int = 100,
    val subtitleOffset: Int = 0,
    val subtitleTextColor: Int = 0xFFFFFFFF.toInt(),
    val subtitleBackgroundColor: Int = 0x00000000
)

@Immutable
data class NextEpisodeInfo(
    val title: String,
    val thumbnail: String?,
    val seasonNumber: Int,
    val episodeNumber: Int
)

@Immutable
data class SkipSegmentInfo(
    val introStartMs: Long? = null,
    val introEndMs: Long? = null,
    val outroStartMs: Long? = null,
    val outroEndMs: Long? = null
)

data class PlayerUiState(
    val isReady: Boolean = false,
    val isBuffering: Boolean = false,
    val isPlaying: Boolean = false,
    val playWhenReady: Boolean = false,
    val hasRenderedFirstFrame: Boolean = false,
    val durationMs: Long = 0L,
    val positionMs: Long = 0L,
    val bufferedPositionMs: Long = 0L,
    val playbackSpeed: Float = 1f,
    val currentSourceId: String? = null,
    val selectedAudioTrackId: String? = null,
    val selectedSubtitleTrackId: String? = null,
    val subtitleVerticalOffsetPercent: Int = 0,
    val subtitleSizePercent: Int = 100,
    val subtitleDelayMs: Long = 0L,
    val subtitleTextColor: Int = 0xFFFFFFFF.toInt(),
    val subtitleBackgroundColor: Int = 0x00000000,
    val isEnded: Boolean = false,
    val errorMessage: String? = null
)
