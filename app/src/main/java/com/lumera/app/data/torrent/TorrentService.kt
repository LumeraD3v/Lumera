package com.lumera.app.data.torrent

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.lumera.app.BuildConfig
import org.libtorrent4j.Priority
import org.libtorrent4j.swig.libtorrent
import org.libtorrent4j.swig.error_code
import org.libtorrent4j.swig.torrent_flags_t

import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.*
import java.io.File
import java.net.URLEncoder
import javax.inject.Inject

@AndroidEntryPoint
class TorrentService : Service() {

    @Inject lateinit var engine: TorrentEngine

    private val job = SupervisorJob()
    private val scope = CoroutineScope(Dispatchers.IO + job)
    private var streamProxy: StreamProxy? = null
    private var downloadJob: Job? = null
    private var currentHandle: org.libtorrent4j.TorrentHandle? = null

    companion object {
        var onStreamReady: ((String) -> Unit)? = null
        var onStreamError: ((String) -> Unit)? = null
        var onStreamProgress: ((TorrentProgress) -> Unit)? = null
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val magnetLink = intent?.getStringExtra("MAGNET_LINK") ?: return START_NOT_STICKY
        val fileIdx = intent.getIntExtra("FILE_IDX", -1)

        try {
            startForegroundService()
            engine.start()
            startDownload(magnetLink, fileIdx)
        } catch (e: Exception) {
            if (BuildConfig.DEBUG) Log.e("LumeraTorrent", "Critical Error starting service: ${e.message}")
            scope.launch(Dispatchers.Main) {
                onStreamError?.invoke(e.message ?: "Failed to start torrent engine")
            }
            stopSelf()
        }

        return START_STICKY
    }

    private fun startForegroundService() {
        val channelId = "torrent_channel"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(channelId, "Torrent Download", NotificationManager.IMPORTANCE_LOW)
            getSystemService(NotificationManager::class.java)?.createNotificationChannel(channel)
        }

        val notification: Notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("Lumera Streaming")
            .setContentText("Downloading metadata...")
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .build()

        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(1, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            startForeground(1, notification)
        }
    }

    private fun removeCurrentTorrent() {
        try {
            currentHandle?.let { handle ->
                engine.getSession().remove(handle)
                if (BuildConfig.DEBUG) Log.d("LumeraTorrent", "Removed old torrent from session")
            }
            currentHandle = null
        } catch (e: Exception) {
            if (BuildConfig.DEBUG) Log.w("LumeraTorrent", "Error removing torrent", e)
        }
    }

    @Volatile
    private var progressStatus = "Connecting to peers..."

    private fun emitProgress(session: org.libtorrent4j.SessionManager) {
        val handle = currentHandle
        val progress = if (handle != null) {
            try {
                val status = handle.status()
                TorrentProgress(
                    status = progressStatus,
                    downloadSpeed = status.downloadRate().toLong(),
                    peers = status.numPeers(),
                    seeds = status.numSeeds()
                )
            } catch (_: Exception) {
                TorrentProgress(status = progressStatus)
            }
        } else {
            TorrentProgress(status = progressStatus)
        }
        onStreamProgress?.invoke(progress)
    }

    private fun startDownload(magnet: String, fileIdx: Int) {
        downloadJob?.cancel()
        stopProxy()
        removeCurrentTorrent()
        cleanupDownloads()
        engine.getDownloadPath().mkdirs()
        downloadJob = scope.launch {
            if (BuildConfig.DEBUG) Log.d("LumeraTorrent", "Adding magnet: ${magnet.take(120)}...")

            // Launch stats reporter that polls every second until cancelled
            val session = engine.getSession()
            val progressJob = launch {
                while (isActive) {
                    withContext(Dispatchers.Main) { emitProgress(session) }
                    delay(1000)
                }
            }

            try {
                val saveDir = engine.getDownloadPath()

                // No DHT wait — trackers handle peer discovery in 2-3s.
                // DHT bootstraps in the background and supplements later.
                if (BuildConfig.DEBUG) Log.d("LumeraTorrent", "Session running: ${session.isRunning()}, DHT: ${session.isDhtRunning()}, nodes: ${session.stats().dhtNodes()}")

                // Enrich magnet with public trackers for faster peer discovery.
                // DHT alone can take 30+ seconds; trackers find peers in 2-5 seconds.
                val enrichedMagnet = enrichMagnetWithTrackers(magnet)

                // Add magnet directly — keeps peer connections alive for faster piece downloads.
                // Unlike fetchMagnet() which adds a temp torrent, fetches metadata, then removes it,
                // this adds the torrent once and waits for metadata in-place.
                progressStatus = "Fetching metadata..."
                if (BuildConfig.DEBUG) Log.d("LumeraTorrent", "Adding magnet directly (30s timeout)...")

                // Parse magnet to extract info hash for handle lookup
                val ec = error_code()
                val params = libtorrent.parse_magnet_uri(enrichedMagnet, ec)
                if (ec.value() != 0) {
                    if (BuildConfig.DEBUG) Log.e("LumeraTorrent", "Invalid magnet URI: ${ec.message()}")
                    withContext(Dispatchers.Main) {
                        onStreamError?.invoke("Invalid magnet link.")
                    }
                    stopSelf()
                    return@launch
                }
                val infoHash = org.libtorrent4j.Sha1Hash(params.getInfo_hashes().get_best())

                session.download(enrichedMagnet, saveDir, torrent_flags_t())

                // Find the handle (should be available almost immediately after download() call)
                var handle: org.libtorrent4j.TorrentHandle? = null
                var attempts = 0
                while (handle == null && attempts < 200) {
                    handle = session.find(infoHash)
                    if (handle == null) delay(50)
                    attempts++
                }

                if (handle == null) {
                    if (BuildConfig.DEBUG) Log.e("LumeraTorrent", "Failed to get handle after adding magnet.")
                    withContext(Dispatchers.Main) {
                        onStreamError?.invoke("Torrent not found. Check your connection.")
                    }
                    stopSelf()
                    return@launch
                }

                currentHandle = handle

                // Wait for metadata — peers are already connected and downloading it
                val metadataDeadline = System.currentTimeMillis() + 30_000L
                while (!handle.status().hasMetadata()) {
                    ensureActive()
                    if (System.currentTimeMillis() >= metadataDeadline) {
                        if (BuildConfig.DEBUG) Log.e("LumeraTorrent", "Timeout waiting for metadata")
                        if (BuildConfig.DEBUG) Log.d("LumeraTorrent", "Session stats: dhtNodes=${session.stats().dhtNodes()}")
                        withContext(Dispatchers.Main) {
                            onStreamError?.invoke("Could not fetch torrent metadata. Try a different source.")
                        }
                        stopSelf()
                        return@launch
                    }
                    delay(100)
                }

                val torrentInfo = handle.torrentFile()
                if (BuildConfig.DEBUG) Log.d("LumeraTorrent", "Metadata received! Peers already connected.")
                progressStatus = "Preparing stream..."

                // Phase 3: Select file and set priorities
                val numFiles = torrentInfo.numFiles()
                val fileStorage = torrentInfo.files()

                val targetFileIndex = if (fileIdx in 0 until numFiles) {
                    fileIdx
                } else {
                    var largestIdx = 0
                    var largestSize = 0L
                    for (i in 0 until numFiles) {
                        if (fileStorage.fileSize(i) > largestSize) {
                            largestSize = fileStorage.fileSize(i)
                            largestIdx = i
                        }
                    }
                    largestIdx
                }

                // Select only the target file, ignore all others
                val filePriorities = Array(numFiles) { Priority.IGNORE }
                filePriorities[targetFileIndex] = Priority.DEFAULT
                handle.prioritizeFiles(filePriorities)

                // Phase 4: Create stream mapping and set on-demand piece priorities
                val torrentStream = TorrentStream.create(handle, torrentInfo, targetFileIndex)
                val movieFile = File(saveDir, fileStorage.filePath(targetFileIndex))

                if (BuildConfig.DEBUG) Log.d("LumeraTorrent", "Streaming file[$targetFileIndex]: ${fileStorage.filePath(targetFileIndex)} " +
                    "(${torrentStream.fileSize / 1024 / 1024} MB, ${torrentStream.numPieces} pieces)")

                // Prioritize three groups:
                // 1. Head (piece 0): container format header (ftyp/EBML) — ExoPlayer reads this first
                // 2. Tail (~20 MB): moov atom / MKV Cues — ExoPlayer seeks here for metadata
                // 3. Runway (pieces 1-N, ~20 MB): first video data — so playback starts immediately
                //    after moov parse, instead of blocking on piece 1, 2, 3... sequentially
                val pieceLen = torrentStream.pieceLength.toLong()
                val headCount = (2L * 1024 * 1024 / pieceLen).toInt().coerceAtLeast(1)
                val tailCount = (20L * 1024 * 1024 / pieceLen).toInt().coerceAtLeast(2)
                val runwayCount = (20L * 1024 * 1024 / pieceLen).toInt().coerceAtLeast(3)

                // Build priority array for ALL pieces atomically — avoids the transient
                // "finished" state that occurs when setting pieces to IGNORE one-by-one,
                // which causes libtorrent to flush its peer request queues.
                val totalPieces = torrentInfo.numPieces()
                val piecePriorities = Array(totalPieces) { Priority.IGNORE }

                val piecesToWait = mutableSetOf<Int>()

                // Head — container header, must wait for these before starting proxy
                for (i in 0 until headCount) {
                    val idx = torrentStream.firstPiece + i
                    piecePriorities[idx] = Priority.TOP_PRIORITY
                    piecesToWait.add(idx)
                }

                // Tail — moov/cues, wait for the very last piece only.
                // For large files, tail pieces arrive alongside head (same timeframe).
                // For small files, moov fits in 1 piece. Either way, 1-tail is optimal.
                for (i in 0 until tailCount) {
                    val idx = torrentStream.lastPiece - i
                    if (idx > torrentStream.firstPiece + headCount - 1) {
                        piecePriorities[idx] = Priority.TOP_PRIORITY
                        if (i == 0) piecesToWait.add(idx)
                    }
                }

                // Runway — sequential data after head, prioritize but don't wait
                for (i in 0 until runwayCount) {
                    val idx = torrentStream.firstPiece + headCount + i
                    if (idx <= torrentStream.lastPiece - tailCount) {
                        piecePriorities[idx] = Priority.TOP_PRIORITY
                    }
                }

                // Set ALL priorities in one atomic call — prevents transient "finished" state
                handle.prioritizePieces(piecePriorities)

                // Deadline 0 (time-critical) only for wait pieces — libtorrent sends
                // duplicate block requests for these, concentrating bandwidth on them.
                // Other pieces get a normal deadline so they download without wasting
                // bandwidth on duplicates.
                for (idx in piecesToWait) {
                    handle.setPieceDeadline(idx, 0)
                }
                for (i in 0 until headCount) {
                    val idx = torrentStream.firstPiece + i
                    if (idx !in piecesToWait) handle.setPieceDeadline(idx, 5000)
                }
                for (i in 0 until tailCount) {
                    val idx = torrentStream.lastPiece - i
                    if (idx > torrentStream.firstPiece + headCount - 1 && idx !in piecesToWait) {
                        handle.setPieceDeadline(idx, 5000)
                    }
                }
                for (i in 0 until runwayCount) {
                    val idx = torrentStream.firstPiece + headCount + i
                    if (idx <= torrentStream.lastPiece - tailCount) {
                        handle.setPieceDeadline(idx, 5000)
                    }
                }

                if (BuildConfig.DEBUG) Log.d("LumeraTorrent",
                    "On-demand mode: $totalPieces pieces, " +
                    "prioritized head=$headCount + tail=$tailCount + runway=$runwayCount " +
                    "(pieceSize=${pieceLen / 1024}KB, waiting for ${piecesToWait.size} pieces: $piecesToWait)")

                // Phase 5: Wait for head pieces only before starting proxy.
                // Tail and runway pieces download in the background —
                // TorrentInputStream blocks on-demand while progress overlay shows stats.
                progressStatus = "Buffering..."
                val pieceDeadline = System.currentTimeMillis() + 45_000L
                while (piecesToWait.isNotEmpty()) {
                    ensureActive()
                    piecesToWait.removeAll { handle.havePiece(it) }
                    if (piecesToWait.isEmpty()) break
                    if (System.currentTimeMillis() >= pieceDeadline) {
                        if (BuildConfig.DEBUG) Log.w("LumeraTorrent", "Timeout waiting for header pieces (${piecesToWait.size} remaining), proceeding anyway")
                        break
                    }
                    delay(100)
                }
                if (BuildConfig.DEBUG) Log.d("LumeraTorrent", "Header pieces ready, starting proxy")

                // Phase 6: Start proxy — critical pieces already downloaded
                stopProxy()
                val proxy = StreamProxy(movieFile, torrentStream)
                proxy.start()
                streamProxy = proxy

                val fileName = movieFile.name
                val localUrl = "http://127.0.0.1:${proxy.listeningPort}/$fileName"
                if (BuildConfig.DEBUG) Log.d("LumeraTorrent", "Stream ready at: $localUrl")
                updateNotification("Streaming...")

                // Keep progressJob running — the player overlay shows torrent stats
                // (speed, peers) while ExoPlayer buffers tail/runway pieces.
                // progressJob is cancelled when downloadJob is cancelled or service destroyed.
                progressStatus = "Starting playback..."
                withContext(Dispatchers.Main) {
                    onStreamReady?.invoke(localUrl)
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                progressJob.cancel()
                if (BuildConfig.DEBUG) Log.d("LumeraTorrent", "Download coroutine cancelled (replaced by new download)")
                throw e // Don't call stopSelf — a new download is taking over
            } catch (e: Exception) {
                progressJob.cancel()
                if (BuildConfig.DEBUG) Log.e("LumeraTorrent", "Error inside download loop: ${e.message}", e)
                withContext(Dispatchers.Main) {
                    onStreamError?.invoke("Torrent error: ${e.message}")
                }
                stopSelf()
            }
        }
    }

    private fun updateNotification(text: String) {
        val channelId = "torrent_channel"
        val notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("Lumera Streaming")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .build()
        getSystemService(NotificationManager::class.java)?.notify(1, notification)
    }

    private fun stopProxy() {
        try {
            streamProxy?.stop()
            streamProxy = null
        } catch (e: Exception) {
            if (BuildConfig.DEBUG) Log.w("LumeraTorrent", "Error stopping proxy", e)
        }
    }

    override fun onDestroy() {
        stopProxy()
        downloadJob?.cancel()
        removeCurrentTorrent()
        engine.saveState()
        cleanupDownloads()
        job.cancel()
        onStreamReady = null
        onStreamError = null
        onStreamProgress = null
        super.onDestroy()
    }

    private fun enrichMagnetWithTrackers(magnet: String): String {
        val trackerParams = TorrentEngine.PUBLIC_TRACKERS.joinToString("") { tracker ->
            "&tr=${URLEncoder.encode(tracker, "UTF-8")}"
        }
        return magnet + trackerParams
    }

    private fun cleanupDownloads() {
        try {
            val downloadDir = engine.getDownloadPath()
            if (downloadDir.exists()) {
                downloadDir.deleteRecursively()
                if (BuildConfig.DEBUG) Log.d("LumeraTorrent", "Cleaned up downloads: ${downloadDir.absolutePath}")
            }
        } catch (e: Exception) {
            if (BuildConfig.DEBUG) Log.w("LumeraTorrent", "Failed to cleanup downloads", e)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
