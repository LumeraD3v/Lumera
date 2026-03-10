package com.lumera.app.data.torrent

data class TorrentProgress(
    val status: String,
    val downloadSpeed: Long = 0,
    val peers: Int = 0,
    val seeds: Int = 0,
    val progress: Float? = null  // null = indeterminate, 0.0-1.0 = determinate
)
