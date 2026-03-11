package com.lumera.app.data.stream

import com.lumera.app.data.model.ParsedStreamInfo
import com.lumera.app.data.model.StreamQuality
import com.lumera.app.data.model.stremio.Stream

object StreamParser {

    private val seedPatterns = listOf(
        Regex("""👤\s*(\d[\d,.]*)"""),
        Regex("""(?i)\bseeds?[:\s]+(\d[\d,.]*)"""),
        Regex("""(?i)\bpeers?[:\s]+(\d[\d,.]*)"""),
        Regex("""(?i)\bS[:\s]*(\d[\d,.]*)""")
    )

    private val sizePattern = Regex("""(\d+(?:\.\d+)?)\s?(KB|MB|GB|TB)""", RegexOption.IGNORE_CASE)

    private val dvPattern = Regex("""(?i)\b(dolby\s*vision|dovi|dv)\b""")
    private val hdrPattern = Regex("""(?i)\b(hdr10\+?|hdr|hlg)\b""")
    private val dtsPattern = Regex("""(?i)\b(dts[-\s]?(hd|x|ma)?)\b""")
    private val dolbyPattern = Regex("""(?i)\b(dolby\s*(digital|atmos)?|dd[+\s]?[257]\.?1?|atmos|ac-?3|eac-?3)\b""")
    private val hevcPattern = Regex("""(?i)\b(hevc|h\.?265|x\.?265)\b""")
    private val av1Pattern = Regex("""(?i)\bav1\b""")
    private val threeDPattern = Regex("""(?i)\b(3d|sbs|half.?sbs|hou)\b""")

    fun parse(stream: Stream): ParsedStreamInfo {
        val text = combinedText(stream)
        return ParsedStreamInfo(
            quality = extractQuality(stream),
            sizeBytes = stream.behaviorHints?.videoSize ?: extractSizeBytes(text),
            seeds = extractSeeds(text),
            formats = extractFormats(text)
        )
    }

    /**
     * Extract quality by checking each stream field individually (most specific first).
     * This avoids false positives where e.g. a filename contains "2160p" but the
     * stream.name says "1080p" — the name/title from the addon is more authoritative.
     */
    private fun extractQuality(stream: Stream): StreamQuality {
        // Check each source individually; return the first that yields a known quality
        val sources = listOfNotNull(
            stream.behaviorHints?.filename,
            stream.title,
            stream.description,
            stream.name
        )
        for (source in sources) {
            val quality = StreamQuality.fromString(source)
            if (quality != StreamQuality.UNKNOWN) return quality
        }
        return StreamQuality.UNKNOWN
    }

    fun combinedText(stream: Stream): String {
        return listOfNotNull(
            stream.name,
            stream.title,
            stream.description,
            stream.behaviorHints?.filename
        ).joinToString(" ")
    }

    fun extractSizeBytes(text: String?): Long? {
        if (text.isNullOrBlank()) return null
        val match = sizePattern.find(text) ?: return null
        val value = match.groupValues[1].toDoubleOrNull() ?: return null
        val unit = match.groupValues[2].uppercase()
        val bytes = when (unit) {
            "TB" -> value * 1_099_511_627_776L
            "GB" -> value * 1_073_741_824L
            "MB" -> value * 1_048_576L
            "KB" -> value * 1_024L
            else -> return null
        }
        return bytes.toLong().takeIf { it > 0 }
    }

    fun extractSeeds(text: String?): Int? {
        if (text.isNullOrBlank()) return null
        for (pattern in seedPatterns) {
            val match = pattern.find(text)
            if (match != null) {
                val raw = match.groupValues[1].replace(",", "").replace(".", "")
                return raw.toIntOrNull()
            }
        }
        return null
    }

    fun extractFormats(text: String?): Set<String> {
        if (text.isNullOrBlank()) return emptySet()
        val formats = mutableSetOf<String>()
        if (dvPattern.containsMatchIn(text)) formats.add("dv")
        if (hdrPattern.containsMatchIn(text)) formats.add("hdr")
        if (dtsPattern.containsMatchIn(text)) formats.add("dts")
        if (dolbyPattern.containsMatchIn(text)) formats.add("dolby")
        if (hevcPattern.containsMatchIn(text)) formats.add("hevc")
        if (av1Pattern.containsMatchIn(text)) formats.add("av1")
        if (threeDPattern.containsMatchIn(text)) formats.add("3d")
        return formats
    }
}
