package com.lumera.app.data.model

data class ParsedStreamInfo(
    val quality: StreamQuality,
    val sizeBytes: Long?,
    val seeds: Int?,
    val formats: Set<String> = emptySet()
)
