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
import org.libtorrent4j.TorrentInfo

import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.*
import java.io.File
import javax.inject.Inject

@AndroidEntryPoint
class TorrentService : Service() {

    @Inject lateinit var engine: TorrentEngine

    private val job = SupervisorJob()
    private val scope = CoroutineScope(Dispatchers.IO + job)
    private var streamProxy: StreamProxy? = null
    private var downloadJob: Job? = null

    companion object {
        var onStreamReady: ((String) -> Unit)? = null
        var onStreamError: ((String) -> Unit)? = null
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
                onStreamError?.invoke("Failed to start torrent engine: ${e.message}")
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

    private fun startDownload(magnet: String, fileIdx: Int) {
        downloadJob?.cancel()
        stopProxy()
        cleanupDownloads()
        downloadJob = scope.launch {
            if (BuildConfig.DEBUG) Log.d("LumeraTorrent", "Adding magnet: ${magnet.take(120)}...")

            try {
                val session = engine.getSession()
                val saveDir = engine.getDownloadPath()

                // Diagnostics: check session state
                if (BuildConfig.DEBUG) Log.d("LumeraTorrent", "Session running: ${session.isRunning()}, DHT running: ${session.isDhtRunning()}")
                if (BuildConfig.DEBUG) Log.d("LumeraTorrent", "Listen endpoints: ${session.listenEndpoints()}")

                // Wait for session to fully initialize (listeners + DHT)
                var dhtWait = 0
                while (dhtWait < 5) {
                    delay(1000)
                    dhtWait++
                    val endpoints = session.listenEndpoints()
                    val dhtRunning = session.isDhtRunning()
                    val dhtNodes = session.stats().dhtNodes()
                    if (dhtWait % 5 == 0 || (endpoints.isNotEmpty() && dhtRunning)) {
                        if (BuildConfig.DEBUG) Log.d("LumeraTorrent", "Init wait ${dhtWait}s: endpoints=$endpoints, dht=$dhtRunning, nodes=$dhtNodes")
                    }
                    if (endpoints.isNotEmpty() && dhtRunning && dhtNodes > 0) break
                }
                if (BuildConfig.DEBUG) Log.d("LumeraTorrent", "After init wait: endpoints=${session.listenEndpoints()}, dht=${session.isDhtRunning()}, nodes=${session.stats().dhtNodes()}")

                // Use fetchMagnet — the dedicated API for resolving magnet metadata
                // This runs on Dispatchers.IO so blocking is OK
                if (BuildConfig.DEBUG) Log.d("LumeraTorrent", "Calling fetchMagnet (30s timeout)...")
                val torrentData = session.fetchMagnet(magnet, 30, saveDir)

                if (torrentData == null) {
                    if (BuildConfig.DEBUG) Log.e("LumeraTorrent", "fetchMagnet returned null — no metadata found")
                    if (BuildConfig.DEBUG) Log.d("LumeraTorrent", "Session stats: dhtNodes=${session.stats().dhtNodes()}")
                    withContext(Dispatchers.Main) {
                        onStreamError?.invoke("Could not fetch torrent metadata. Try a different source.")
                    }
                    stopSelf()
                    return@launch
                }

                if (BuildConfig.DEBUG) Log.d("LumeraTorrent", "Metadata received! (${torrentData.size} bytes)")

                // Add the torrent for downloading using the resolved metadata
                val torrentInfo = TorrentInfo(torrentData)
                session.download(torrentInfo, saveDir)

                // Get the handle
                var handle: org.libtorrent4j.TorrentHandle? = null
                var attempts = 0
                while (handle == null && attempts < 20) {
                    handle = session.find(torrentInfo.infoHash())
                    delay(500)
                    attempts++
                }

                if (handle == null) {
                    if (BuildConfig.DEBUG) Log.e("LumeraTorrent", "Failed to get handle after metadata.")
                    withContext(Dispatchers.Main) {
                        onStreamError?.invoke("Torrent not found. Check your connection.")
                    }
                    stopSelf()
                    return@launch
                }

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
                val priorities = Array(numFiles) { Priority.IGNORE }
                priorities[targetFileIndex] = Priority.DEFAULT
                handle.prioritizeFiles(priorities)

                // Phase 4: Create stream mapping and set on-demand piece priorities
                val torrentStream = TorrentStream.create(handle, torrentInfo, targetFileIndex)
                val movieFile = File(saveDir, fileStorage.filePath(targetFileIndex))

                if (BuildConfig.DEBUG) Log.d("LumeraTorrent", "Streaming file[$targetFileIndex]: ${fileStorage.filePath(targetFileIndex)} " +
                    "(${torrentStream.fileSize / 1024 / 1024} MB, ${torrentStream.numPieces} pieces)")

                // Set ALL pieces to IGNORE — only download what the player actually requests.
                // This prevents libtorrent from downloading the entire file (critical for large files on limited storage).
                for (i in torrentStream.firstPiece..torrentStream.lastPiece) {
                    handle.piecePriority(i, Priority.IGNORE)
                }

                // Prioritize first/last 1% of pieces for container headers (moov atom, EBML, MKV cues, etc.)
                val headCount = (torrentStream.numPieces / 100).coerceAtLeast(1)
                val tailCount = (torrentStream.numPieces / 100).coerceAtLeast(1)

                for (i in 0 until headCount) {
                    val idx = torrentStream.firstPiece + i
                    handle.piecePriority(idx, Priority.TOP_PRIORITY)
                    handle.setPieceDeadline(idx, 1000)
                }
                for (i in 0 until tailCount) {
                    val idx = torrentStream.lastPiece - i
                    if (idx > torrentStream.firstPiece + headCount - 1) {
                        handle.piecePriority(idx, Priority.TOP_PRIORITY)
                        handle.setPieceDeadline(idx, 1000)
                    }
                }

                if (BuildConfig.DEBUG) Log.d("LumeraTorrent",
                    "On-demand mode: ${torrentStream.numPieces} pieces set to IGNORE, " +
                    "prioritized first $headCount + last $tailCount for headers")

                // Phase 5: Start proxy immediately — TorrentInputStream handles blocking
                stopProxy()
                val proxy = StreamProxy(movieFile, torrentStream)
                proxy.start()
                streamProxy = proxy

                val fileName = movieFile.name
                val localUrl = "http://127.0.0.1:${proxy.listeningPort}/$fileName"
                if (BuildConfig.DEBUG) Log.d("LumeraTorrent", "Stream ready at: $localUrl")
                updateNotification("Streaming...")

                withContext(Dispatchers.Main) {
                    onStreamReady?.invoke(localUrl)
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                if (BuildConfig.DEBUG) Log.d("LumeraTorrent", "Download coroutine cancelled (replaced by new download)")
                throw e // Don't call stopSelf — a new download is taking over
            } catch (e: Exception) {
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
        cleanupDownloads()
        job.cancel()
        onStreamReady = null
        onStreamError = null
        super.onDestroy()
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
