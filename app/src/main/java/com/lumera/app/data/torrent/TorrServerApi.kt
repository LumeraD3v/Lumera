package com.lumera.app.data.torrent

import android.util.Log
import com.lumera.app.BuildConfig
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

class TorrServerApi(private val baseUrl: String = "http://127.0.0.1:8090") {

    private val client = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    private val jsonType = "application/json; charset=utf-8".toMediaType()

    suspend fun addTorrent(magnetLink: String, title: String = ""): JsonObject =
        withContext(Dispatchers.IO) {
            val body = JsonObject().apply {
                addProperty("action", "add")
                addProperty("link", magnetLink)
                addProperty("title", title)
                addProperty("save_to_db", false)
            }
            val request = Request.Builder()
                .url("$baseUrl/torrents")
                .post(body.toString().toRequestBody(jsonType))
                .build()
            val response = client.newCall(request).execute()
            val responseBody = response.body?.string() ?: "{}"
            response.close()
            if (!response.isSuccessful) {
                throw Exception("Failed to add torrent: HTTP ${response.code}")
            }
            JsonParser.parseString(responseBody).asJsonObject
        }

    suspend fun getTorrentStats(magnetLink: String): TorrentStats =
        withContext(Dispatchers.IO) {
            val hash = extractHash(magnetLink)
            val body = JsonObject().apply {
                addProperty("action", "get")
                addProperty("hash", hash)
            }
            val request = Request.Builder()
                .url("$baseUrl/torrents")
                .post(body.toString().toRequestBody(jsonType))
                .build()
            val response = client.newCall(request).execute()
            val responseBody = response.body?.string() ?: "{}"
            response.close()
            if (!response.isSuccessful) {
                return@withContext TorrentStats()
            }
            parseTorrentStats(JsonParser.parseString(responseBody).asJsonObject)
        }

    suspend fun dropTorrent(magnetLink: String) = withContext(Dispatchers.IO) {
        val hash = extractHash(magnetLink)
        val body = JsonObject().apply {
            addProperty("action", "drop")
            addProperty("hash", hash)
        }
        val request = Request.Builder()
            .url("$baseUrl/torrents")
            .post(body.toString().toRequestBody(jsonType))
            .build()
        try {
            client.newCall(request).execute().close()
        } catch (_: Exception) {}
    }

    fun getStreamUrl(magnetLink: String, fileIndex: Int): String {
        val encoded = URLEncoder.encode(magnetLink, "UTF-8")
        return "$baseUrl/stream?link=$encoded&index=$fileIndex&play"
    }

    suspend fun getFileList(magnetLink: String): List<TorrServerFile> =
        withContext(Dispatchers.IO) {
            // Use info hash for lookup — TorrServer matches by hash, not full magnet
            val hash = extractHash(magnetLink)
            val body = JsonObject().apply {
                addProperty("action", "get")
                addProperty("hash", hash)
            }
            val request = Request.Builder()
                .url("$baseUrl/torrents")
                .post(body.toString().toRequestBody(jsonType))
                .build()
            try {
                val response = client.newCall(request).execute()
                val responseBody = response.body?.string() ?: "{}"
                response.close()
                if (!response.isSuccessful) {
                    if (BuildConfig.DEBUG) Log.w("LumeraTorrent", "getFileList HTTP ${response.code}")
                    return@withContext emptyList()
                }

                val json = JsonParser.parseString(responseBody).asJsonObject
                if (BuildConfig.DEBUG) Log.v("LumeraTorrent", "getFileList stat=${json.get("stat")}, file_stats=${json.has("file_stats")}")
                val files = json.getAsJsonArray("file_stats") ?: return@withContext emptyList()
                if (BuildConfig.DEBUG) Log.v("LumeraTorrent", "file_stats count: ${files.size()}")
                files.mapIndexed { index, element ->
                    val file = element.asJsonObject
                    TorrServerFile(
                        id = file.get("id")?.asInt ?: index,
                        path = file.get("path")?.asString ?: "",
                        length = file.get("length")?.asLong ?: 0L
                    )
                }
            } catch (e: Exception) {
                if (BuildConfig.DEBUG) Log.w("LumeraTorrent", "getFileList error: ${e.message}")
                emptyList()
            }
        }

    private fun extractHash(magnetLink: String): String {
        val regex = Regex("btih:([a-fA-F0-9]{40})")
        return regex.find(magnetLink)?.groupValues?.get(1) ?: magnetLink
    }

    suspend fun configureSettings(cacheSizeMB: Int = 128) = withContext(Dispatchers.IO) {
        try {
            // Read current settings first to preserve TorrServer defaults
            val getRequest = Request.Builder().url("$baseUrl/settings").build()
            val getResponse = client.newCall(getRequest).execute()
            val currentSettings = getResponse.body?.string() ?: "{}"
            getResponse.close()

            val body = JsonParser.parseString(currentSettings).asJsonObject.apply {
                addProperty("CacheSize", cacheSizeMB.toLong() * 1024 * 1024)
                addProperty("PreloadCache", 50)
                addProperty("ReaderReadAHead", 95)
                addProperty("ConnectionsLimit", 50)
                addProperty("UseDisk", false)
            }

            val postRequest = Request.Builder()
                .url("$baseUrl/settings")
                .post(body.toString().toRequestBody(jsonType))
                .build()
            client.newCall(postRequest).execute().close()
            if (BuildConfig.DEBUG) Log.d("LumeraTorrent", "Settings applied: CacheSize=${cacheSizeMB}MB, ConnectionsLimit=50")
        } catch (e: Exception) {
            if (BuildConfig.DEBUG) Log.w("LumeraTorrent", "configureSettings error: ${e.message}")
        }
    }

    private fun parseTorrentStats(json: JsonObject): TorrentStats {
        return TorrentStats(
            stat = json.get("stat")?.asInt ?: 0,
            activePeers = json.get("active_peers")?.asInt ?: 0,
            totalPeers = json.get("total_peers")?.asInt ?: 0,
            connectedSeeders = json.get("connected_seeders")?.asInt ?: 0,
            downloadSpeed = json.get("download_speed")?.asLong ?: 0L,
            uploadSpeed = json.get("upload_speed")?.asLong ?: 0L,
            bytesRead = json.get("bytes_read")?.asLong ?: 0L,
            torrentSize = json.get("torrent_size")?.asLong ?: 0L
        )
    }
}

data class TorrentStats(
    val stat: Int = 0,          // 0=Added, 1=GettingInfo, 2=Preload, 3=Working, 4=Closed
    val activePeers: Int = 0,
    val totalPeers: Int = 0,
    val connectedSeeders: Int = 0,
    val downloadSpeed: Long = 0L,
    val uploadSpeed: Long = 0L,
    val bytesRead: Long = 0L,
    val torrentSize: Long = 0L
) {
    fun statusText(): String = when (stat) {
        0 -> "Connecting to peers..."
        1 -> "Fetching metadata..."
        2 -> "Buffering..."
        3 -> "Streaming"
        4 -> "Stopped"
        else -> "Connecting..."
    }
}

data class TorrServerFile(
    val id: Int,
    val path: String,
    val length: Long
)
