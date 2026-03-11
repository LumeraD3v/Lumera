package com.lumera.app.data.model

enum class StreamQuality(val sortOrder: Int) {
    UHD_4K(5),
    FHD_1080P(4),
    HD_720P(3),
    SD_480P(2),
    CAM(1),
    UNKNOWN(0);

    companion object {
        // Explicit resolution numbers — highest priority
        private val explicit2160 = Regex("""(?i)\b2160[pi]?\b""")
        private val explicit1080 = Regex("""(?i)\b1080[pi]?\b""")
        private val explicit720 = Regex("""(?i)\b720[pi]?\b""")
        private val explicit480 = Regex("""(?i)\b480[pi]?\b""")

        // Ambiguous terms — lower priority, only used if no explicit number found
        private val fuzzy4K = Regex("""(?i)\b(4k|uhd|ultra\s*hd)\b""")
        private val fuzzy1080 = Regex("""(?i)\bfhd\b""")
        private val fuzzyHD = Regex("""(?i)\bHD\b""")
        private val fuzzySD = Regex("""(?i)\b(sd|dvd|dvdrip)\b""")
        private val patternCAM = Regex("""(?i)\b(cam|camrip|ts|telesync|hdts|hdcam|telecine|tc)\b""")

        fun fromString(input: String): StreamQuality {
            // Pass 1: Explicit resolution numbers always win
            return when {
                explicit2160.containsMatchIn(input) -> UHD_4K
                explicit1080.containsMatchIn(input) -> FHD_1080P
                explicit720.containsMatchIn(input) -> HD_720P
                explicit480.containsMatchIn(input) -> SD_480P
                // Pass 2: Ambiguous/fuzzy terms (only if no explicit resolution found)
                fuzzy4K.containsMatchIn(input) -> UHD_4K
                fuzzy1080.containsMatchIn(input) -> FHD_1080P
                patternCAM.containsMatchIn(input) -> CAM
                fuzzySD.containsMatchIn(input) -> SD_480P
                fuzzyHD.containsMatchIn(input) -> HD_720P
                else -> UNKNOWN
            }
        }

        fun fromKey(key: String): StreamQuality? = when (key) {
            "4k" -> UHD_4K
            "1080p" -> FHD_1080P
            "720p" -> HD_720P
            "sd" -> SD_480P
            "cam" -> CAM
            "unknown" -> UNKNOWN
            else -> null
        }

        fun toKey(quality: StreamQuality): String = when (quality) {
            UHD_4K -> "4k"
            FHD_1080P -> "1080p"
            HD_720P -> "720p"
            SD_480P -> "sd"
            CAM -> "cam"
            UNKNOWN -> "unknown"
        }
    }
}
