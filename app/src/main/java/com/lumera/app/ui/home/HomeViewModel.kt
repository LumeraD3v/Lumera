package com.lumera.app.ui.home

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lumera.app.data.local.AddonDao
import com.lumera.app.data.model.WatchHistoryEntity
import com.lumera.app.data.repository.AddonRepository
import com.lumera.app.domain.HomeRow
import com.lumera.app.domain.HubGroupRow
import com.lumera.app.ui.utils.ImagePrefetcher
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.async
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

import com.lumera.app.domain.HomeRowItem
import com.lumera.app.domain.CategoryRow
import com.lumera.app.data.model.stremio.MetaItem
import com.lumera.app.domain.DashboardTab
import com.lumera.app.domain.heroFor

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val repository: AddonRepository,
    private val dao: AddonDao,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private var loadJob: kotlinx.coroutines.Job? = null
    private var lastFocusedKeyMemory: String? = null
    private val rowScrollPositionsMemory = mutableMapOf<String, Pair<Int, Int>>()
    private var verticalScrollPositionMemory: Pair<Int, Int> = Pair(0, 0)
    private var hadHistoryWhenPositionSaved: Boolean = false
    private var isRestoringPosition: Boolean = false

    data class HomeState(
        val mixedRows: List<HomeRowItem> = emptyList(),
        val rows: List<HomeRow> = emptyList(),
        val hubRows: List<HubGroupRow> = emptyList(),
        val history: List<WatchHistoryEntity> = emptyList(),
        val isLoading: Boolean = true,
        val lastFocusedKey: String? = null,
        val loadedScreen: String? = null,
        // Row scroll positions: rowKey -> Pair(firstVisibleItemIndex, firstVisibleItemScrollOffset)
        val rowScrollPositions: Map<String, Pair<Int, Int>> = emptyMap(),
        // Vertical list scroll position: Pair(firstVisibleItemIndex, firstVisibleItemScrollOffset)
        val verticalScrollPosition: Pair<Int, Int> = Pair(0, 0),
        val heroRow: HomeRow? = null,
        val loadedProfileId: Int? = null,
        val enrichedMeta: Map<String, MetaItem> = emptyMap()
    )

    private val _state = MutableStateFlow(HomeState())
    val state: StateFlow<HomeState> = _state

    fun getRowScrollPositions(): Map<String, Pair<Int, Int>> = rowScrollPositionsMemory

    fun getVerticalScrollPosition(): Pair<Int, Int> = verticalScrollPositionMemory

    fun setLastFocusedKey(key: String?) {
        if (lastFocusedKeyMemory == key) return
        lastFocusedKeyMemory = key
        _state.value = _state.value.copy(lastFocusedKey = key)
    }
    
    fun setRowScrollPosition(rowKey: String, position: Pair<Int, Int>) {
        if (rowScrollPositionsMemory[rowKey] == position) return
        rowScrollPositionsMemory[rowKey] = position
    }
    
    fun setVerticalScrollPosition(position: Pair<Int, Int>, hasHistory: Boolean = hadHistoryWhenPositionSaved) {
        if (verticalScrollPositionMemory == position && hadHistoryWhenPositionSaved == hasHistory) return
        verticalScrollPositionMemory = position
        hadHistoryWhenPositionSaved = hasHistory
    }

    fun needsHistoryScrollAdjustment(hasHistory: Boolean): Boolean {
        return hasHistory && !hadHistoryWhenPositionSaved && isRestoringPosition
    }

    // Track which rows are currently loading more items to prevent duplicate fetches
    private val loadingMoreRows = mutableSetOf<String>()
    private val metadataFallbackCache = mutableMapOf<String, MetadataFallback?>()
    private val metadataRequestsInFlight = mutableSetOf<String>()
    private val hubInitialLoadCount = 100
    private val initialDashboardBatchSize = 6
    private val initialDashboardTimeoutMs = 2_500L

    // UI batching: per-row pending buffers and dedup tracking
    companion object {
        private const val ROW_BATCH_SIZE = 50
    }
    private val pendingRowItems = mutableMapOf<String, MutableList<MetaItem>>()
    private val allFetchedRowIds = mutableMapOf<String, MutableSet<String>>()

    private data class MetadataFallback(
        val poster: String?,
        val background: String?,
        val logo: String?,
        val description: String?,
        val releaseInfo: String?,
        val imdbRating: String?,
        val runtime: String?,
        val genres: List<String>?
    )

    /**
     * Loads the next page of items for a specific catalog row.
     * Called when the user scrolls near the end of the row's current items.
     * Uses UI batching: reveals ROW_BATCH_SIZE items at a time from a pending buffer,
     * only fetching from the API when the buffer is empty.
     */
    fun loadMoreItems(configId: String) {
        if (configId in loadingMoreRows) return // Already loading

        // Find the row in the current state
        val currentRows = _state.value.rows
        val row = currentRows.find { it.configId == configId } ?: return
        if (row.catalogUrl.isEmpty() || !row.supportsSkip) return

        // First: reveal items from the pending buffer (no API call needed)
        val pending = pendingRowItems[configId]
        if (pending != null && pending.isNotEmpty()) {
            val batch = pending.take(ROW_BATCH_SIZE)
            pendingRowItems[configId] = pending.drop(ROW_BATCH_SIZE).toMutableList()
            appendItemsToRow(configId, batch)
            return
        }

        // Pending buffer empty — fetch next page from API
        loadingMoreRows.add(configId)

        viewModelScope.launch {
            try {
                // Initialize dedup set from current row items if not yet tracked
                val fetchedIds = allFetchedRowIds.getOrPut(configId) {
                    row.items.map { "${it.type}:${it.id}" }.toMutableSet()
                }

                // Skip = total fetched count for this row
                val nextSkip = fetchedIds.size
                val newItems = repository.fetchNextCatalogPage(row.catalogUrl, nextSkip)

                // Deduplicate against all previously fetched items
                val newUniqueItems = newItems.filter { item ->
                    fetchedIds.add("${item.type}:${item.id}")
                }

                if (newUniqueItems.isNotEmpty()) {
                    // Show first batch immediately, buffer the rest
                    val batch = newUniqueItems.take(ROW_BATCH_SIZE)
                    if (newUniqueItems.size > ROW_BATCH_SIZE) {
                        pendingRowItems.getOrPut(configId) { mutableListOf() }
                            .addAll(newUniqueItems.drop(ROW_BATCH_SIZE))
                    }
                    appendItemsToRow(configId, batch)
                }
            } catch (_: Exception) {
                // Silently fail - user can try scrolling again
            } finally {
                loadingMoreRows.remove(configId)
            }
        }
    }

    private fun appendItemsToRow(configId: String, newItems: List<MetaItem>) {
        val updatedRows = _state.value.rows.map { r ->
            if (r.configId == configId) r.copy(items = r.items + newItems)
            else r
        }

        val updatedMixed = _state.value.mixedRows.map { item ->
            if (item is CategoryRow && item.id == configId) {
                val updatedRow = updatedRows.find { it.configId == configId }
                if (updatedRow != null) CategoryRow.fromHomeRow(updatedRow) else item
            } else item
        }

        _state.value = _state.value.copy(
            rows = updatedRows,
            mixedRows = updatedMixed
        )
    }

    /**
     * Invalidates the cached screen data, forcing a reload on next loadScreen() call.
     * Call this after making changes in the Dashboard Editor.
     */
    fun invalidate() {
        pendingRowItems.clear()
        allFetchedRowIds.clear()
        _state.value = _state.value.copy(
            loadedScreen = null,
            loadedProfileId = null
        )
    }
    
    /**
     * Prefetch first visible images from loaded rows.
     * Called after data loads to ensure images are in cache before scroll starts.
     */
    private fun prefetchFirstVisibleImages(rows: List<HomeRow>) {
        // Prefetch first 10 images from first 3 rows (most likely to be visible on screen)
        val imagesToPrefetch = rows
            .take(3)
            .flatMap { row -> row.items.take(10) }
            .mapNotNull { it.poster }
        
        imagesToPrefetch.forEach { url ->
            ImagePrefetcher.prefetch(context, url)
        }
    }

    /**
     * Prefetch a small set of likely-visible metadata so cinematic/hero surfaces render smoothly.
     * Keep this intentionally tiny to avoid unnecessary metadata requests.
     */
    private fun prefetchLikelyVisibleMetadata(rows: List<HomeRow>, heroRow: HomeRow?) {
        val candidates = buildList {
            addAll(heroRow?.items?.take(4) ?: emptyList())
            addAll(rows.getOrNull(0)?.items?.take(3) ?: emptyList())
            addAll(rows.getOrNull(1)?.items?.take(2) ?: emptyList())
        }
            .distinctBy { "${it.type}:${it.id}" }
            .take(6)

        candidates.forEach { ensureMetadataFallback(it) }
    }

    private fun needsMetadataFallback(item: MetaItem): Boolean {
        return item.poster.isNullOrBlank() ||
            item.background.isNullOrBlank() ||
            item.logo.isNullOrBlank() ||
            item.description.isNullOrBlank() ||
            item.releaseInfo.isNullOrBlank() ||
            item.imdbRating.isNullOrBlank() ||
            item.runtime.isNullOrBlank() ||
            item.genres.isNullOrEmpty()
    }

    fun ensureMetadataFallback(item: MetaItem?) {
        if (item == null || !needsMetadataFallback(item)) return
        val key = "${item.type}:${item.id}"

        if (metadataFallbackCache.containsKey(key)) {
            val cachedFallback = metadataFallbackCache[key]
            if (cachedFallback != null) {
                applyMetadataFallbackToState(type = item.type, id = item.id, fallback = cachedFallback, sourceItem = item)
            }
            return
        }

        if (!metadataRequestsInFlight.add(key)) return

        viewModelScope.launch {
            try {
                val meta = repository.resolveMetaDetails(item.type, item.id)
                    ?: throw Exception("No meta found")
                val fallback = MetadataFallback(
                    poster = meta.poster,
                    background = meta.background,
                    logo = meta.logo,
                    description = meta.description,
                    releaseInfo = meta.releaseInfo,
                    imdbRating = meta.imdbRating,
                    runtime = meta.runtime,
                    genres = meta.genres
                )
                metadataFallbackCache[key] = fallback
                applyMetadataFallbackToState(type = item.type, id = item.id, fallback = fallback, sourceItem = item)
            } catch (_: Exception) {
                metadataFallbackCache[key] = null
            } finally {
                metadataRequestsInFlight.remove(key)
            }
        }
    }

    private fun applyFallbackToMeta(meta: MetaItem, fallback: MetadataFallback): MetaItem {
        val patchedPoster = if (meta.poster.isNullOrBlank()) fallback.poster else meta.poster
        val patchedBackground = if (meta.background.isNullOrBlank()) fallback.background else meta.background
        val patchedLogo = if (meta.logo.isNullOrBlank()) fallback.logo else meta.logo
        val patchedDescription = if (meta.description.isNullOrBlank()) fallback.description else meta.description
        val patchedReleaseInfo = if (meta.releaseInfo.isNullOrBlank()) fallback.releaseInfo else meta.releaseInfo
        val patchedImdbRating = if (meta.imdbRating.isNullOrBlank()) fallback.imdbRating else meta.imdbRating
        val patchedRuntime = if (meta.runtime.isNullOrBlank()) fallback.runtime else meta.runtime
        val patchedGenres = if (meta.genres.isNullOrEmpty()) fallback.genres else meta.genres

        return meta.copy(
            poster = patchedPoster,
            background = patchedBackground,
            logo = patchedLogo,
            description = patchedDescription,
            releaseInfo = patchedReleaseInfo,
            imdbRating = patchedImdbRating,
            runtime = patchedRuntime,
            genres = patchedGenres
        )
    }

    private fun patchMetaListWithFallback(
        items: List<MetaItem>,
        type: String,
        id: String,
        fallback: MetadataFallback
    ): Pair<List<MetaItem>, Boolean> {
        var changed = false
        val patched = items.map { meta ->
            if (meta.type == type && meta.id == id) {
                val merged = applyFallbackToMeta(meta, fallback)
                if (merged != meta) changed = true
                merged
            } else {
                meta
            }
        }
        return patched to changed
    }

    private fun applyMetadataFallbackToState(type: String, id: String, fallback: MetadataFallback, sourceItem: MetaItem? = null) {
        val current = _state.value
        var stateChanged = false

        val updatedRows = current.rows.map { row ->
            val (patchedItems, changed) = patchMetaListWithFallback(row.items, type, id, fallback)
            if (changed) {
                stateChanged = true
                row.copy(items = patchedItems)
            } else {
                row
            }
        }

        val updatedMixedRows = current.mixedRows.map { rowItem ->
            if (rowItem is CategoryRow) {
                val (patchedItems, changed) = patchMetaListWithFallback(rowItem.items, type, id, fallback)
                if (changed) {
                    stateChanged = true
                    rowItem.copy(items = patchedItems)
                } else {
                    rowItem
                }
            } else {
                rowItem
            }
        }

        val updatedHeroRow = current.heroRow?.let { hero ->
            val (patchedItems, changed) = patchMetaListWithFallback(hero.items, type, id, fallback)
            if (changed) {
                stateChanged = true
                hero.copy(items = patchedItems)
            } else {
                hero
            }
        }

        // Store enriched preview for items not in any category row (e.g., search-discovered history items)
        val enrichedKey = "$type:$id"
        val updatedEnrichedMeta = if (sourceItem != null && !current.enrichedMeta.containsKey(enrichedKey)) {
            val enriched = applyFallbackToMeta(sourceItem, fallback)
            if (enriched != sourceItem) {
                stateChanged = true
                current.enrichedMeta + (enrichedKey to enriched)
            } else current.enrichedMeta
        } else current.enrichedMeta

        if (!stateChanged) return

        _state.value = current.copy(
            rows = updatedRows,
            mixedRows = updatedMixedRows,
            heroRow = updatedHeroRow,
            enrichedMeta = updatedEnrichedMeta
        )
    }

    fun loadScreen(screenName: String, currentProfile: com.lumera.app.data.model.ProfileEntity?) {
        val currentProfileId = currentProfile?.id
        // ... (existing code)
        // Skip reload if this screen is already loaded with data
        if (
            _state.value.loadedScreen == screenName &&
            _state.value.loadedProfileId == currentProfileId &&
            _state.value.rows.isNotEmpty()
        ) {
            isRestoringPosition = true
            return
        }

        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            // Clear 'rows' immediately so the UI doesn't show "Ghost Data" from the previous tab
            pendingRowItems.clear()
            allFetchedRowIds.clear()
            _state.value = _state.value.copy(
                isLoading = true,
                mixedRows = emptyList(),
                rows = emptyList(),
                hubRows = emptyList(),
                lastFocusedKey = null,  // Reset focus when loading new screen
                rowScrollPositions = emptyMap(),  // Reset scroll positions for new screen
                verticalScrollPosition = Pair(0, 0),  // Reset vertical scroll for new screen
                loadedProfileId = null
            )
            lastFocusedKeyMemory = null
            rowScrollPositionsMemory.clear()
            verticalScrollPositionMemory = Pair(0, 0)
            hadHistoryWhenPositionSaved = false
            isRestoringPosition = false

            // Load History only for Home
            if (screenName == "home") {
                launch {
                    dao.getWatchHistory().collect {
                        _state.value = _state.value.copy(history = it)
                    }
                }
            } else {
                _state.value = _state.value.copy(history = emptyList())
            }

            try {
                // Stage 1: Load only a small first batch so the screen opens quickly.
                val initialRowsDeferred = async {
                    repository.getDashboardRows(
                        screen = screenName,
                        skipConfigs = 0,
                        maxConfigs = initialDashboardBatchSize,
                        catalogTimeoutMs = initialDashboardTimeoutMs
                    )
                }
                val hubRowsDeferred = async { repository.getHubRows(screenName) }

                val initialRows = initialRowsDeferred.await()
                val hubRows = hubRowsDeferred.await()

                // Fetch HERO row separately (even if hidden in dashboard).
                val tabEnum = DashboardTab.fromString(screenName)
                val heroConfig = currentProfile?.heroFor(tabEnum)
                val heroRow = if (heroConfig?.categoryId != null) {
                    initialRows.find { it.configId == heroConfig.categoryId }
                        ?: repository.getCategoryRowPreview(
                            configId = heroConfig.categoryId,
                            maxItems = heroConfig.posterCount,
                            timeoutMs = initialDashboardTimeoutMs
                        )
                } else null

                // Prefetch first visible images BEFORE updating state.
                prefetchFirstVisibleImages(initialRows)

                val initialMixedList = (hubRows + initialRows.map { CategoryRow.fromHomeRow(it) })
                    .sortedBy { it.order }

                _state.value = _state.value.copy(
                    mixedRows = initialMixedList,
                    rows = initialRows,
                    hubRows = hubRows,
                    heroRow = heroRow,
                    isLoading = false,
                    loadedScreen = screenName,
                    loadedProfileId = currentProfileId
                )

                // Start a tiny metadata warmup pass for items likely to render first.
                prefetchLikelyVisibleMetadata(rows = initialRows, heroRow = heroRow)

                // Stage 2: Load remaining categories in the background and append.
                launch {
                    val remainingRows = repository.getDashboardRows(
                        screen = screenName,
                        skipConfigs = initialDashboardBatchSize
                    )
                    if (remainingRows.isEmpty()) return@launch

                    val currentState = _state.value
                    if (currentState.loadedScreen != screenName || currentState.loadedProfileId != currentProfileId) {
                        return@launch
                    }

                    val allRows = (currentState.rows + remainingRows)
                        .distinctBy { it.configId }
                        .sortedBy { it.order }

                    val resolvedHeroRow = when {
                        currentState.heroRow != null -> currentState.heroRow
                        heroConfig?.categoryId != null -> allRows.find { it.configId == heroConfig.categoryId }
                        else -> null
                    }

                    val mixedList = (hubRows + allRows.map { CategoryRow.fromHomeRow(it) })
                        .sortedBy { it.order }

                    _state.value = currentState.copy(
                        mixedRows = mixedList,
                        rows = allRows,
                        heroRow = resolvedHeroRow
                    )
                }
            } catch (e: Exception) {
                _state.value = _state.value.copy(isLoading = false)
            }
        }
    }

    /**
     * Opens a hub item, fetching the category content if it's not already loaded.
     */
    fun openHub(
        hubItem: com.lumera.app.domain.HubItem,
        onResult: (String, List<MetaItem>) -> Unit
    ) {
        // 1. Try to find in currently loaded rows (Legacy)
        val legacyRow = _state.value.rows.find { it.configId == hubItem.categoryId }
        if (legacyRow != null) {
            onResult(legacyRow.title, legacyRow.items)
            return
        }

        // 2. Try to find in mixed rows (New Architecture)
        val mixedRow = _state.value.mixedRows.find { it.id == hubItem.categoryId } as? CategoryRow
        if (mixedRow != null) {
            onResult(mixedRow.title, mixedRow.items)
            return
        }

        // 3. If not found, fetch from repository
        viewModelScope.launch {
            try {
                val fetchedRow = repository.getCategoryRowPreview(
                    configId = hubItem.categoryId,
                    maxItems = hubInitialLoadCount
                )
                if (fetchedRow != null) {
                    // Cache fetched row so GridView can lazy-load additional pages via loadMoreItems().
                    val updatedRows = _state.value.rows
                        .filterNot { it.configId == fetchedRow.configId } + fetchedRow
                    _state.value = _state.value.copy(rows = updatedRows)
                    onResult(fetchedRow.title, fetchedRow.items)
                }
            } catch (_: Exception) {
                // Ignore error
            }
        }
    }
}
