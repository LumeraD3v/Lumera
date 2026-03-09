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

import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.*
import javax.inject.Inject

@AndroidEntryPoint
class TorrentService : Service() {

    @Inject lateinit var engine: TorrServerEngine

    private val job = SupervisorJob()
    private val scope = CoroutineScope(Dispatchers.IO + job)
    private val api = TorrServerApi()
    private var downloadJob: Job? = null
    private var currentMagnet: String? = null

    companion object {
        private const val TAG = "LumeraTorrent"
        var onStreamReady: ((String) -> Unit)? = null
        var onStreamError: ((String) -> Unit)? = null
        var onStreamProgress: ((TorrentProgress) -> Unit)? = null
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val magnetLink = intent?.getStringExtra("MAGNET_LINK") ?: return START_NOT_STICKY
        val fileIdx = intent.getIntExtra("FILE_IDX", -1)

        try {
            startForegroundService()
            startDownload(magnetLink, fileIdx)
        } catch (e: Exception) {
            if (BuildConfig.DEBUG) Log.e(TAG, "Critical error starting service: ${e.message}")
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
            .setContentText("Starting torrent engine...")
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
        currentMagnet = magnet

        downloadJob = scope.launch {
            try {
                // Phase 1: Start TorrServer process
                if (BuildConfig.DEBUG) Log.d(TAG, "Starting TorrServer engine...")
                withContext(Dispatchers.Main) {
                    onStreamProgress?.invoke(TorrentProgress(status = "Starting engine..."))
                }
                engine.start()

                // Configure cache settings
                api.configureSettings(cacheSizeMB = 128)

                // Phase 2: Add torrent
                if (BuildConfig.DEBUG) Log.d(TAG, "Adding magnet: ${magnet.take(120)}...")
                withContext(Dispatchers.Main) {
                    onStreamProgress?.invoke(TorrentProgress(status = "Fetching metadata..."))
                }
                api.addTorrent(magnet)

                // Phase 3: Wait for metadata and resolve file index
                // Always resolve to largest video file — addon-provided fileIdx
                // may not match TorrServer's file ordering in multi-file torrents
                val targetFileIndex = resolveLargestFile(magnet, fileIdx)

                if (BuildConfig.DEBUG) Log.d(TAG, "Streaming file index: $targetFileIndex")

                // Phase 4: Build stream URL and notify player
                val streamUrl = api.getStreamUrl(magnet, targetFileIndex)
                if (BuildConfig.DEBUG) Log.d(TAG, "Stream ready at: $streamUrl")
                updateNotification("Streaming...")

                withContext(Dispatchers.Main) {
                    onStreamProgress?.invoke(TorrentProgress(status = "Starting playback..."))
                    onStreamReady?.invoke(streamUrl)
                }

                // Phase 5: Poll progress until cancelled
                while (isActive) {
                    delay(1000)
                    try {
                        val stats = api.getTorrentStats(magnet)
                        withContext(Dispatchers.Main) {
                            onStreamProgress?.invoke(
                                TorrentProgress(
                                    status = stats.statusText(),
                                    downloadSpeed = stats.downloadSpeed,
                                    peers = stats.activePeers,
                                    seeds = stats.connectedSeeders
                                )
                            )
                        }
                    } catch (_: Exception) {}
                }
            } catch (e: CancellationException) {
                if (BuildConfig.DEBUG) Log.d(TAG, "Download coroutine cancelled")
                throw e
            } catch (e: Exception) {
                if (BuildConfig.DEBUG) Log.e(TAG, "Error in download: ${e.message}", e)
                withContext(Dispatchers.Main) {
                    onStreamError?.invoke("Torrent error: ${e.message}")
                }
                stopSelf()
            }
        }
    }

    private val videoExtensions = setOf("mkv", "mp4", "avi", "webm", "ts", "m4v", "mov", "wmv", "flv")

    private suspend fun resolveLargestFile(magnet: String, hintIdx: Int): Int {
        val deadline = System.currentTimeMillis() + 30_000L
        while (System.currentTimeMillis() < deadline) {
            val files = api.getFileList(magnet)
            if (files.isNotEmpty()) {
                // Filter to video files only, then pick the largest
                val videoFiles = files.filter { f ->
                    val ext = f.path.substringAfterLast('.', "").lowercase()
                    ext in videoExtensions
                }
                val target = videoFiles.maxByOrNull { it.length }
                    ?: files.maxByOrNull { it.length }!!
                if (BuildConfig.DEBUG) Log.d(TAG, "Resolved file: ${target.path} (${target.length / 1024 / 1024} MB, id=${target.id})")
                return target.id
            }
            delay(500)
        }
        // Fallback: use hint or 0
        if (BuildConfig.DEBUG) Log.w(TAG, "Timeout resolving file list, using index ${hintIdx.coerceAtLeast(0)}")
        return hintIdx.coerceAtLeast(0)
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

    override fun onDestroy() {
        downloadJob?.cancel()
        scope.launch {
            currentMagnet?.let { api.dropTorrent(it) }
            engine.stop()
        }
        currentMagnet = null
        job.cancel()
        onStreamReady = null
        onStreamError = null
        onStreamProgress = null
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
