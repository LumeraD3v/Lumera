package com.lumera.app.data.player

import android.content.Context
import com.lumera.app.data.model.stremio.Stream
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SourceSelectionStore @Inject constructor(
    @ApplicationContext context: Context
) {
    private val prefs = context.getSharedPreferences(PREFS_FILE, Context.MODE_PRIVATE)

    fun rememberSelection(playbackId: String, stream: Stream) {
        val scopedId = canonicalSourceScopeId(playbackId)
        val streamFingerprint = streamFingerprint(stream) ?: return
        val addonTag = addonTag(stream)

        prefs.edit().apply {
            putString(streamKey(scopedId), streamFingerprint)
            if (addonTag.isNullOrBlank()) {
                remove(addonPrefKey(scopedId))
            } else {
                putString(addonPrefKey(scopedId), addonTag)
            }
        }.apply()
    }

    fun clearSelection(playbackId: String) {
        val scopedId = canonicalSourceScopeId(playbackId)
        prefs.edit()
            .remove(streamKey(scopedId))
            .remove(addonPrefKey(scopedId))
            .apply()
    }

    fun clearSelectionsForPrefix(prefix: String) {
        val editor = prefs.edit()
        prefs.all.keys.forEach { key ->
            if (key.startsWith("${KEY_STREAM_PREFIX}$prefix") ||
                key.startsWith("${KEY_ADDON_PREFIX}$prefix")
            ) {
                editor.remove(key)
            }
        }
        editor.apply()
    }

    fun hasRememberedSelection(playbackId: String): Boolean {
        val scopedId = canonicalSourceScopeId(playbackId)
        return !prefs.getString(streamKey(scopedId), null).isNullOrBlank()
            || !prefs.getString(addonPrefKey(scopedId), null).isNullOrBlank()
    }

    fun findPreferredStream(playbackId: String, streams: List<Stream>): Stream? {
        if (streams.isEmpty()) return null

        val playableStreams = streams.filter(::isPlayableStream)
        if (playableStreams.isEmpty()) return null

        val scopedId = canonicalSourceScopeId(playbackId)
        val savedFingerprint = prefs.getString(streamKey(scopedId), null)
        val savedAddon = prefs.getString(addonPrefKey(scopedId), null)

        if (!savedFingerprint.isNullOrBlank()) {
            val exactMatch = playableStreams.firstOrNull { streamFingerprint(it) == savedFingerprint }
            if (exactMatch != null) return exactMatch
        }

        if (!savedAddon.isNullOrBlank()) {
            val addonMatch = playableStreams.firstOrNull { addonTag(it) == savedAddon }
            if (addonMatch != null) return addonMatch
        }

        return null
    }

    private fun streamKey(scopedId: String): String = "${KEY_STREAM_PREFIX}$scopedId"

    private fun addonPrefKey(scopedId: String): String = "${KEY_ADDON_PREFIX}$scopedId"

    private fun streamFingerprint(stream: Stream): String? {
        val normalizedInfoHash = normalize(stream.infoHash)
        val normalizedUrl = normalize(stream.url)
        val normalizedFileIdx = stream.fileIdx?.toString()
        val normalizedName = normalize(stream.name)
        val normalizedTitle = normalize(stream.title)

        if (
            normalizedInfoHash == null &&
            normalizedUrl == null &&
            normalizedFileIdx == null &&
            normalizedName == null &&
            normalizedTitle == null
        ) {
            return null
        }

        return listOf(
            normalizedInfoHash ?: "",
            normalizedUrl ?: "",
            normalizedFileIdx ?: "",
            normalizedName ?: "",
            normalizedTitle ?: ""
        ).joinToString("|")
    }

    private fun addonTag(stream: Stream): String? {
        val sourceName = stream.name ?: return null
        if (!sourceName.contains("[") || !sourceName.contains("]")) return null
        return normalize(sourceName.substringAfter("[").substringBefore("]"))
    }

    private fun canonicalSourceScopeId(playbackId: String): String {
        return playbackId.trim()
    }

    private fun normalize(value: String?): String? {
        if (value == null) return null
        val trimmed = value.trim()
        if (trimmed.isEmpty()) return null
        return trimmed.lowercase(Locale.ROOT)
    }

    private fun isPlayableStream(stream: Stream): Boolean {
        return !stream.url.isNullOrBlank() || !stream.infoHash.isNullOrBlank()
    }

    companion object {
        private const val PREFS_FILE = "source_selection_prefs"
        private const val KEY_STREAM_PREFIX = "stream_"
        private const val KEY_ADDON_PREFIX = "addon_"
    }
}
