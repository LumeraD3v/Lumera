package com.lumera.app.data.profile

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson
import com.lumera.app.data.auth.StremioAuthManager
import com.lumera.app.data.local.AddonDao
import com.lumera.app.data.model.AddonEntity
import com.lumera.app.data.model.CatalogConfigEntity
import com.lumera.app.data.model.HubRowEntity
import com.lumera.app.data.model.HubRowItemEntity
import com.lumera.app.data.model.WatchHistoryEntity
import com.lumera.app.data.model.stremio.CatalogManifest
import com.lumera.app.data.repository.AddonRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.firstOrNull
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

data class ProfileRuntimeSnapshot(
    val addons: List<AddonEntity> = emptyList(),
    val catalogConfigs: List<CatalogConfigEntity> = emptyList(),
    val hubRows: List<HubRowEntity> = emptyList(),
    val hubRowItems: List<HubRowItemEntity> = emptyList(),
    val watchHistory: List<WatchHistoryEntity> = emptyList()
)

@Singleton
class ProfileConfigurationManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val dao: AddonDao,
    private val stremioAuthManager: StremioAuthManager,
    private val addonRepository: AddonRepository
) {
    companion object {
        private const val PREFS_FILE = "profile_configuration_prefs"
        private const val KEY_PENDING_SETUP_PROFILES = "pending_setup_profiles"
        private const val KEY_LAST_ACTIVE_PROFILE_ID = "last_active_profile_id"
        private const val SNAPSHOT_DIR = "profile_snapshots"
        private const val DEFAULT_CINEMETA_MANIFEST_URL = "https://v3-cinemeta.strem.io/manifest.json"
        private const val DEFAULT_CINEMETA_TRANSPORT_URL = "https://v3-cinemeta.strem.io"
    }

    private val gson = Gson()
    private val prefs: SharedPreferences by lazy {
        context.getSharedPreferences(PREFS_FILE, Context.MODE_PRIVATE)
    }

    private var startupRuntimeCaptured = false

    fun resetStartupCapture() { startupRuntimeCaptured = false }

    fun markPendingSetup(profileId: Int) {
        val updated = getPendingSetupIds().toMutableSet().apply { add(profileId.toString()) }
        prefs.edit().putStringSet(KEY_PENDING_SETUP_PROFILES, updated).apply()
    }

    fun needsInitialSetup(profileId: Int): Boolean {
        return getPendingSetupIds().contains(profileId.toString())
    }

    fun clearPendingSetup(profileId: Int) {
        val updated = getPendingSetupIds().toMutableSet().apply { remove(profileId.toString()) }
        prefs.edit().putStringSet(KEY_PENDING_SETUP_PROFILES, updated).apply()
    }

    suspend fun captureStartupRuntimeIfNeeded() {
        if (startupRuntimeCaptured) return
        delay(500L)
        startupRuntimeCaptured = true

        val lastActive = getLastActiveProfileId() ?: return
        if (needsInitialSetup(lastActive)) return
        saveRuntimeState(lastActive)
    }

    suspend fun saveRuntimeState(profileId: Int) {
        val snapshot = captureRuntimeSnapshot()
        writeSnapshot(profileId, snapshot)
        stremioAuthManager.saveCredentialsForProfile(profileId)
        setLastActiveProfileId(profileId)
    }

    suspend fun saveActiveRuntimeState() {
        val activeId = getLastActiveProfileId() ?: return
        saveRuntimeState(activeId)
    }

    suspend fun loadRuntimeState(profileId: Int) {
        val existingSnapshot = readSnapshot(profileId)
        val snapshot = existingSnapshot ?: if (!needsInitialSetup(profileId)) {
            captureRuntimeSnapshot().also {
                writeSnapshot(profileId, it)
                stremioAuthManager.saveCredentialsForProfile(profileId)
            }
        } else {
            ProfileRuntimeSnapshot()
        }
        dao.replaceRuntimeState(
            addons = snapshot.addons,
            catalogConfigs = snapshot.catalogConfigs,
            hubRows = snapshot.hubRows,
            hubRowItems = snapshot.hubRowItems,
            watchHistory = snapshot.watchHistory
        )
        stremioAuthManager.loadCredentialsForProfile(profileId)
        setLastActiveProfileId(profileId)
    }

    suspend fun initializeFromScratch(profileId: Int) {
        writeSnapshot(profileId, createDefaultRuntimeSnapshot())
        stremioAuthManager.clearCredentialsForProfile(profileId)
        clearPendingSetup(profileId)
    }

    suspend fun initializeByCopying(targetProfileId: Int, sourceProfileId: Int) {
        captureStartupRuntimeIfNeeded()
        val sourceSnapshot = readSnapshot(sourceProfileId) ?: captureRuntimeSnapshot().also {
            writeSnapshot(sourceProfileId, it)
            stremioAuthManager.saveCredentialsForProfile(sourceProfileId)
        }
        writeSnapshot(targetProfileId, sourceSnapshot.copy(watchHistory = emptyList()))
        stremioAuthManager.copyCredentialsBetweenProfiles(sourceProfileId, targetProfileId)
        copyProfileDisplayAndDashboardConfig(targetProfileId, sourceProfileId)
        clearPendingSetup(targetProfileId)
    }

    fun deleteProfileState(profileId: Int) {
        clearPendingSetup(profileId)
        snapshotFile(profileId).delete()
        stremioAuthManager.clearCredentialsForProfile(profileId)

        if (getLastActiveProfileId() == profileId) {
            prefs.edit().remove(KEY_LAST_ACTIVE_PROFILE_ID).apply()
        }
    }

    private suspend fun copyProfileDisplayAndDashboardConfig(targetProfileId: Int, sourceProfileId: Int) {
        val sourceProfile = dao.getProfileById(sourceProfileId) ?: return
        val targetProfile = dao.getProfileById(targetProfileId) ?: return

        dao.insertProfile(
            targetProfile.copy(
                roundCorners = sourceProfile.roundCorners,
                hubRoundCorners = sourceProfile.hubRoundCorners,
                navPosition = sourceProfile.navPosition,
                homeTabLayout = sourceProfile.homeTabLayout,
                moviesTabLayout = sourceProfile.moviesTabLayout,
                seriesTabLayout = sourceProfile.seriesTabLayout,
                homeHeroCategory = sourceProfile.homeHeroCategory,
                homeHeroPosterCount = sourceProfile.homeHeroPosterCount,
                homeHeroAutoScrollSeconds = sourceProfile.homeHeroAutoScrollSeconds,
                moviesHeroCategory = sourceProfile.moviesHeroCategory,
                moviesHeroPosterCount = sourceProfile.moviesHeroPosterCount,
                moviesHeroAutoScrollSeconds = sourceProfile.moviesHeroAutoScrollSeconds,
                seriesHeroCategory = sourceProfile.seriesHeroCategory,
                seriesHeroPosterCount = sourceProfile.seriesHeroPosterCount,
                seriesHeroAutoScrollSeconds = sourceProfile.seriesHeroAutoScrollSeconds
            )
        )
    }

    private suspend fun captureRuntimeSnapshot(): ProfileRuntimeSnapshot {
        return ProfileRuntimeSnapshot(
            addons = dao.getAllAddons().firstOrNull() ?: emptyList(),
            catalogConfigs = dao.getAllCatalogConfigs().firstOrNull() ?: emptyList(),
            hubRows = dao.getAllHubRows().firstOrNull() ?: emptyList(),
            hubRowItems = dao.getAllHubRowItems().firstOrNull() ?: emptyList(),
            watchHistory = dao.getWatchHistory().firstOrNull() ?: emptyList()
        )
    }

    private suspend fun createDefaultRuntimeSnapshot(): ProfileRuntimeSnapshot {
        val fallbackCatalogs = listOf(
            CatalogManifest(type = "movie", id = "top", name = "Top"),
            CatalogManifest(type = "series", id = "top", name = "Top")
        )

        val manifest = runCatching { addonRepository.fetchManifest(DEFAULT_CINEMETA_MANIFEST_URL) }.getOrNull()
        val catalogs = manifest?.catalogs
            ?.filter { it.type == "movie" || it.type == "series" }
            ?.ifEmpty { fallbackCatalogs }
            ?: fallbackCatalogs

        val addonName = manifest?.name ?: "Cinemeta"
        val addonEntity = AddonEntity(
            transportUrl = DEFAULT_CINEMETA_TRANSPORT_URL,
            id = manifest?.id ?: "org.stremio.cinemeta",
            name = addonName,
            version = manifest?.version ?: "1.0.0",
            description = manifest?.description ?: "Official Stremio metadata addon",
            iconUrl = manifest?.logo,
            isTrusted = false,
            isEnabled = true,
            nickname = null,
            catalogsJson = gson.toJson(catalogs)
        )

        val configs = catalogs.mapIndexed { index, catalog ->
            val isMovie = catalog.type == "movie"
            val isSeries = catalog.type == "series"
            CatalogConfigEntity(
                uniqueId = "${DEFAULT_CINEMETA_TRANSPORT_URL}/${catalog.type}/${catalog.id}",
                transportUrl = DEFAULT_CINEMETA_TRANSPORT_URL,
                addonName = addonName,
                catalogType = catalog.type,
                catalogId = catalog.id,
                catalogName = catalog.name,
                customTitle = null,
                showInHome = true,
                showInMovies = isMovie,
                showInSeries = isSeries,
                homeOrder = index,
                moviesOrder = if (isMovie) index else 999,
                seriesOrder = if (isSeries) index else 999
            )
        }

        return ProfileRuntimeSnapshot(
            addons = listOf(addonEntity),
            catalogConfigs = configs,
            hubRows = emptyList(),
            hubRowItems = emptyList(),
            watchHistory = emptyList()
        )
    }

    private fun writeSnapshot(profileId: Int, snapshot: ProfileRuntimeSnapshot) {
        val file = snapshotFile(profileId)
        file.parentFile?.mkdirs()
        file.writeText(gson.toJson(snapshot))
    }

    private fun readSnapshot(profileId: Int): ProfileRuntimeSnapshot? {
        val file = snapshotFile(profileId)
        if (!file.exists()) return null
        return runCatching {
            gson.fromJson(file.readText(), ProfileRuntimeSnapshot::class.java)
        }.getOrNull()
    }

    private fun snapshotFile(profileId: Int): File {
        return File(File(context.filesDir, SNAPSHOT_DIR), "profile_$profileId.json")
    }

    private fun getPendingSetupIds(): Set<String> {
        return prefs.getStringSet(KEY_PENDING_SETUP_PROFILES, emptySet()) ?: emptySet()
    }

    fun getLastActiveProfileId(): Int? {
        val value = prefs.getInt(KEY_LAST_ACTIVE_PROFILE_ID, -1)
        return if (value == -1) null else value
    }

    fun clearLastActiveProfileId() {
        prefs.edit().remove(KEY_LAST_ACTIVE_PROFILE_ID).apply()
    }

    private fun setLastActiveProfileId(profileId: Int) {
        prefs.edit().putInt(KEY_LAST_ACTIVE_PROFILE_ID, profileId).apply()
    }
}
