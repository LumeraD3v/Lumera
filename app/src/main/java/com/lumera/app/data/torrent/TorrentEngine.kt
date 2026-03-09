package com.lumera.app.data.torrent

import android.content.Context
import android.util.Log
import com.lumera.app.BuildConfig
import dagger.hilt.android.qualifiers.ApplicationContext
import org.libtorrent4j.AlertListener
import org.libtorrent4j.SessionManager
import org.libtorrent4j.SessionParams
import org.libtorrent4j.SettingsPack
import org.libtorrent4j.alerts.Alert
import org.libtorrent4j.swig.settings_pack
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TorrentEngine @Inject constructor(
    @ApplicationContext private val context: Context
) {

    companion object {
        /** Public trackers appended to every magnet link for fast peer discovery. */
        val PUBLIC_TRACKERS = listOf(
            "udp://tracker.opentrackr.org:1337/announce",
            "udp://open.stealth.si:80/announce",
            "udp://tracker.torrent.eu.org:451/announce",
            "udp://exodus.desync.com:6969/announce",
            "udp://tracker.openbittorrent.com:6969/announce",
            "udp://open.demonii.com:1337/announce",
            "udp://explodie.org:6969/announce",
            "udp://tracker.dler.org:6969/announce",
            "udp://tracker.moeking.me:6969/announce",
            "udp://tracker.bittor.pw:1337/announce",
            "udp://public.popcorn-tracker.org:6969/announce",
            "udp://tracker.tiny-vps.com:6969/announce",
            "udp://tracker.theoks.net:6969/announce",
            "udp://tracker-udp.gbitt.info:80/announce",
            "http://tracker.opentrackr.org:1337/announce",
            "https://tracker.tamersunion.org:443/announce",
            "http://tracker.bt4g.com:2095/announce",
        )
    }

    private val session = SessionManager()
    private var isStarted = false
    /** Non-null if the native library failed to load (e.g. API < 28 missing aligned_alloc). */
    private var nativeLoadError: String? = null
    private val stateFile = File(context.filesDir, "session_state.dat")

    init {
        // Clean up leftover torrent files from a previous crash (normal exits clean up in TorrentService.onDestroy)
        val downloadDir = getDownloadPath()
        if (downloadDir.exists()) {
            downloadDir.deleteRecursively()
            if (BuildConfig.DEBUG) Log.d("LumeraTorrent", "Cleaned up stale downloads from previous crash")
        }

        // Pre-warm: start the session immediately so DHT bootstraps in the background
        try {
            start()
        } catch (e: LinkageError) {
            // Native library failed to load — libtorrent4j requires API 28+ (aligned_alloc).
            // Swallow here so the singleton doesn't crash the whole app at injection time.
            nativeLoadError = "Torrent streaming requires Android 9.0 or newer. Your device is not supported."
            Log.e("LumeraTorrent", "Native library failed to load", e)
        }
    }

    /**
     * Throws [IllegalStateException] if the native library failed to load on this device.
     */
    fun ensureNativeLoaded() {
        nativeLoadError?.let { throw IllegalStateException(it) }
    }

    fun start() {
        if (isStarted) return
        ensureNativeLoaded()

        session.addListener(object : AlertListener {
            override fun types(): IntArray? = null
            override fun alert(alert: Alert<*>) {
                val type = alert.type().toString()
                val msg = alert.message()
                if (type.contains("LISTEN", ignoreCase = true) ||
                    type.contains("ERROR", ignoreCase = true) ||
                    type.contains("DHT", ignoreCase = true) ||
                    type.contains("PORTMAP", ignoreCase = true) ||
                    msg.contains("listen", ignoreCase = true) ||
                    msg.contains("error", ignoreCase = true) ||
                    msg.contains("bind", ignoreCase = true)) {
                    if (BuildConfig.DEBUG) Log.w("LumeraTorrent", "ALERT [$type]: $msg")
                } else {
                    if (BuildConfig.DEBUG) Log.v("LumeraTorrent", "alert [$type]: $msg")
                }
            }
        })

        val settings = SettingsPack().apply {
            setEnableDht(true)
            setDhtBootstrapNodes(
                "router.bittorrent.com:6881," +
                "router.utorrent.com:6881," +
                "dht.transmissionbt.com:6881," +
                "dht.aelitis.com:6881," +
                "dht.libtorrent.org:25401," +
                "router.nuh.dev:6881," +
                "dht.bitcoin.sprovoost.nl:6881"
            )
            connectionsLimit(200)
            activeDownloads(1)
            activeSeeds(1)

            // Announce to ALL trackers/tiers simultaneously — don't wait for one to fail
            setBoolean(settings_pack.bool_types.announce_to_all_trackers.swigValue(), true)
            setBoolean(settings_pack.bool_types.announce_to_all_tiers.swigValue(), true)

            // Enable UPnP/NAT-PMP for better connectivity through NAT
            setBoolean(settings_pack.bool_types.enable_upnp.swigValue(), true)
            setBoolean(settings_pack.bool_types.enable_natpmp.swigValue(), true)

            // Cap upload to save CPU — TV devices have weak SoCs, hashing for
            // upload verification steals cycles from video decoding
            setInteger(settings_pack.int_types.upload_rate_limit.swigValue(), 1024 * 1024) // 1 MB/s

            // Faster peer turnover — disconnect slow/idle peers sooner
            setInteger(settings_pack.int_types.peer_timeout.swigValue(), 30)       // default 120
            setInteger(settings_pack.int_types.request_timeout.swigValue(), 10)     // default 60
            setInteger(settings_pack.int_types.unchoke_interval.swigValue(), 5)     // default 15

            // Faster initial piece downloads — ramp up peer connections aggressively
            setInteger(settings_pack.int_types.connection_speed.swigValue(), 100)   // default 30
            setBoolean(settings_pack.bool_types.allow_multiple_connections_per_ip.swigValue(), true)
        }

        // Try restoring saved session state (DHT routing table) for faster bootstrap
        val params = try {
            if (stateFile.exists()) {
                val saved = stateFile.readBytes()
                if (BuildConfig.DEBUG) Log.d("LumeraTorrent", "Restoring session state (${saved.size} bytes)")
                SessionParams(saved)
            } else {
                SessionParams(settings)
            }
        } catch (e: Exception) {
            if (BuildConfig.DEBUG) Log.w("LumeraTorrent", "Failed to restore session state, starting fresh", e)
            stateFile.delete()
            SessionParams(settings)
        }

        try {
            session.start(params)
        } catch (e: LinkageError) {
            nativeLoadError = "Torrent streaming requires Android 9.0 or newer. Your device is not supported."
            Log.e("LumeraTorrent", "Native library failed to load during session.start()", e)
            throw e
        }

        // Always apply our settings on top — ensures current config even when restoring old state
        session.applySettings(settings)

        if (BuildConfig.DEBUG) Log.d("LumeraTorrent", "Endpoints after start: ${session.listenEndpoints()}")

        val savePath = File(context.getExternalFilesDir(null), "downloads")
        if (!savePath.exists()) savePath.mkdirs()

        if (BuildConfig.DEBUG) Log.d("LumeraTorrent", "Engine Started. Saving to: ${savePath.absolutePath}")
        isStarted = true
    }

    fun getSession(): SessionManager {
        start() // Ensure started
        return session
    }

    fun isDhtWarmed(): Boolean {
        return isStarted && session.isDhtRunning() &&
                session.listenEndpoints().isNotEmpty() &&
                session.stats().dhtNodes() > 0
    }

    fun saveState() {
        if (!isStarted) return
        try {
            val state = session.saveState()
            stateFile.writeBytes(state)
            if (BuildConfig.DEBUG) Log.d("LumeraTorrent", "Saved session state (${state.size} bytes, dhtNodes=${session.stats().dhtNodes()})")
        } catch (e: Exception) {
            if (BuildConfig.DEBUG) Log.w("LumeraTorrent", "Failed to save session state", e)
        }
    }

    fun getDownloadPath(): File {
        return File(context.getExternalFilesDir(null), "downloads")
    }
}
