package com.lumera.app.domain

import com.lumera.app.data.model.stremio.MetaVideo

/**
 * Tracking ID for watch history. Always uses seriesId:season:episode format
 * so that LIKE prefix queries ("seriesId:%") reliably find episode progress
 * regardless of what format the addon uses for episode.id.
 */
fun episodePlaybackId(seriesId: String, episode: MetaVideo): String {
    return "$seriesId:${episode.season}:${episode.episode}"
}

/**
 * Stream fetch ID. Uses the addon's original episode.id for stream endpoint
 * compatibility, falling back to the constructed format if episode.id is empty.
 */
fun episodeStreamId(seriesId: String, episode: MetaVideo): String {
    return episode.id.ifBlank { "$seriesId:${episode.season}:${episode.episode}" }
}

fun episodeDisplayTitle(episode: MetaVideo): String {
    val season = episode.season.takeIf { it > 0 } ?: 1
    val number = episode.episode.takeIf { it > 0 } ?: 1
    return "S${season}:E${number} - ${episode.title}"
}

fun findNextEpisode(
    seriesId: String,
    currentPlaybackId: String,
    episodes: List<MetaVideo>
): MetaVideo? {
    if (episodes.isEmpty()) return null
    // Only consider regular episodes (season > 0 and episode > 0)
    val regular = episodes.filter { it.season > 0 && it.episode > 0 }
    if (regular.isEmpty()) return null
    val sorted = regular.sortedWith(
        compareBy<MetaVideo> { it.season }
            .thenBy { it.episode }
    )
    var currentIndex = sorted.indexOfFirst {
        episodePlaybackId(seriesId, it) == currentPlaybackId
    }
    // Fallback: match by season/episode numbers for old-format playback IDs
    if (currentIndex < 0) {
        val parts = currentPlaybackId.split(":")
        if (parts.size >= 3) {
            val s = parts[parts.lastIndex - 1].toIntOrNull()
            val e = parts.last().toIntOrNull()
            if (s != null && e != null) {
                currentIndex = sorted.indexOfFirst { it.season == s && it.episode == e }
            }
        }
    }
    if (currentIndex < 0 || currentIndex >= sorted.lastIndex) return null
    return sorted[currentIndex + 1]
}
