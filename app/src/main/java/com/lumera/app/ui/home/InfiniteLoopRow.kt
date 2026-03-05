package com.lumera.app.ui.home

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.lumera.app.data.model.stremio.MetaItem
import com.lumera.app.ui.components.LumeraCard
import com.lumera.app.ui.components.LumeraLandscapeCard
import com.lumera.app.ui.utils.ImagePrefetcher
import kotlinx.coroutines.delay

/**
 * ============================================================================
 * CONTENT ROW - THREE MODE SUPPORT
 * ============================================================================
 *
 * This composable supports three distinct modes:
 *
 * CASE A: Grid View OFF (isInfiniteLoopEnabled = false)
 * - Shows the FULL original list, no truncation
 * - Standard linear LazyRow with itemsIndexed
 * - No ViewMore card, no infinite logic, no modulo math
 *
 * CASE B: Grid View ON + Infinite Loop ON
 * - Truncates list to `visibleItemCount` items + ViewMore card
 * - Uses items(count = Int.MAX_VALUE) with modulo for infinite looping
 * - Unique keys: "${movie.id}_$scrollIndex" to prevent duplicate key crashes
 * - One-way loop: LEFT at loop start exits to Navbar
 * - ViewMore card has directional alpha fade
 *
 * CASE C: Grid View ON + Infinite Loop OFF
 * - Truncates list to `visibleItemCount` items + ViewMore card
 * - Finite list: count = visibleItemCount + 1
 * - Focus stops at ViewMore card, no looping
 * - No modulo math needed
 *
 * SAFETY: When switching modes while scrolled far out,
 * the list state is reset to prevent crashes from invalid indices.
 * ============================================================================
 */

private val ITEM_WIDTH = 120.dp
private val ITEM_SPACING = 20.dp

sealed class GridRowItem {
    data class MovieItem(val movie: MetaItem) : GridRowItem()
    data object ViewMoreItem : GridRowItem()
}

class UpKeyDebouncer {
    var lastTime: Long = 0L
}

private class RowKeyRepeatDebouncer {
    var lastTime: Long = 0L
}

@OptIn(ExperimentalTvMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun InfiniteLoopRow(
    startPadding: Dp,
    isTopNav: Boolean,
    rowIndex: Int,
    title: String,
    items: List<MetaItem>,
    onMovieClick: (MetaItem) -> Unit,
    onViewMore: () -> Unit,
    onFocused: (MetaItem?, String) -> Unit,
    entryRequester: FocusRequester,
    drawerRequester: FocusRequester,
    locallyFocusedItemId: String?,  // Specific item ID to focus in THIS row (e.g. "movie123_g0"), or null if focus is elsewhere
    isGlobalFocusPresent: Boolean, // True if the app has a focused item tracked (lastFocusedKey != null)
    isFirstRow: Boolean,
    isInfiniteLoopEnabled: Boolean = false,
    visibleItemCount: Int = 15,
    isInfiniteScrollingEnabled: Boolean = true,
    externalListState: androidx.compose.foundation.lazy.LazyListState? = null,
    rowHeight: Dp = 210.dp,
    upKeyDebouncer: UpKeyDebouncer,
    repeatGate: DpadRepeatGate,
    pivotFocusRequester: FocusRequester? = null,
    isLandscapeCards: Boolean = false,
    enrichedItems: Map<String, MetaItem> = emptyMap()
) {
    val density = LocalDensity.current
    val paddingPx = remember(density, startPadding) { with(density) { startPadding.toPx() } }
    
    // Detect if this is a restoration (coming back from details screen)
    // We check if we have a local focus target AND a saved scroll position
    val isRestoration = remember(locallyFocusedItemId, rowIndex, externalListState) {
        locallyFocusedItemId != null && externalListState != null
    }
    
    // Skip scroll flag - true during restoration, resets after focus is established
    var skipBringIntoViewScroll by remember { mutableStateOf(isRestoration) }
    
    // Reset skip flag after restoration is complete (focus is established)
    LaunchedEffect(isRestoration) {
        if (isRestoration) {
            // Wait for composition and focus to settle, then re-enable scrolling
            delay(300)
            skipBringIntoViewScroll = false
        }
    }
    
    // Create pivot spec with skip provider and dynamic stiffness
    val pivotSpec = remember(paddingPx) { 
        FocusPivotSpec(
            customOffset = paddingPx,
            skipScrollProvider = { skipBringIntoViewScroll },
            stiffnessProvider = { Spring.StiffnessLow }
        ) 
    }

    // Calculate end padding to allow last item to align to left (pivot position)
    // End padding = Screen Width - Start Padding - Item Width
    val effectiveItemWidth = if (isLandscapeCards) 190.dp else ITEM_WIDTH
    val configuration = LocalConfiguration.current
    val screenWidth = configuration.screenWidthDp.dp
    val endPadding = remember(screenWidth, startPadding, effectiveItemWidth) {
        (screenWidth - startPadding - effectiveItemWidth).coerceAtLeast(120.dp)
    }

    // Use external state if provided, otherwise create local state
    val internalListState = rememberLazyListState()
    val listState = externalListState ?: internalListState

    // Calculate the max valid index based on current mode
    val maxValidIndex = when {
        !isInfiniteLoopEnabled -> items.size - 1
        !isInfiniteScrollingEnabled -> visibleItemCount // visibleItemCount items + 1 ViewMore
        else -> Int.MAX_VALUE
    }

    // SAFETY: Reset scroll position when switching modes
    LaunchedEffect(isInfiniteLoopEnabled, isInfiniteScrollingEnabled) {
        if (listState.firstVisibleItemIndex > maxValidIndex) {
            listState.scrollToItem(0)
        }
    }

    Column(modifier = Modifier.graphicsLayer { clip = false }) {
        Text(
            text = title,
            color = Color.White.copy(0.9f),
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
            modifier = Modifier.padding(start = startPadding, bottom = 12.dp)
        )

        // Stable reference to avoid recomposition from lambda re-allocation
        val wrappedOnFocused = onFocused
        
        when {
            // CASE A: Grid View OFF - Standard linear list
            // Uses BringIntoViewSpec for automatic pivot-aligned scrolling
            !isInfiniteLoopEnabled -> {
                CompositionLocalProvider(LocalBringIntoViewSpec provides pivotSpec) {
                    LinearContent(
                        listState = listState,
                        startPadding = startPadding,
                        endPadding = endPadding,
                        isTopNav = isTopNav,
                        rowIndex = rowIndex,
                        items = items,
                        onMovieClick = onMovieClick,
                        onFocused = wrappedOnFocused,
                        entryRequester = entryRequester,
                        drawerRequester = drawerRequester,
                        locallyFocusedItemId = locallyFocusedItemId,
                        isGlobalFocusPresent = isGlobalFocusPresent,
                        isFirstRow = isFirstRow,
                        rowHeight = rowHeight,
                        upKeyDebouncer = upKeyDebouncer,
                        repeatGate = repeatGate,
                        pivotFocusRequester = pivotFocusRequester,
                        isLandscapeCards = isLandscapeCards,
                        enrichedItems = enrichedItems,
                        effectiveItemWidth = effectiveItemWidth
                    )
                }
            }
            // CASE B: Grid View ON + Infinite Loop ON
            // BringIntoViewSpec handles alignment when returning from navbar
            // SmoothScrollEffect handles spring animation during normal navigation
            isInfiniteScrollingEnabled -> {
                CompositionLocalProvider(LocalBringIntoViewSpec provides pivotSpec) {
                    InfiniteGridContent(
                        listState = listState,
                        startPadding = startPadding,
                        endPadding = endPadding,
                        isTopNav = isTopNav,
                        rowIndex = rowIndex,
                        items = items,
                        visibleItemCount = visibleItemCount,
                        onMovieClick = onMovieClick,
                        onViewMore = onViewMore,
                        onFocused = wrappedOnFocused,
                        entryRequester = entryRequester,
                        drawerRequester = drawerRequester,
                        locallyFocusedItemId = locallyFocusedItemId,
                        isGlobalFocusPresent = isGlobalFocusPresent,
                        isFirstRow = isFirstRow,
                        isRestoredState = externalListState != null,
                        rowHeight = rowHeight,
                        upKeyDebouncer = upKeyDebouncer,
                        repeatGate = repeatGate,
                        pivotFocusRequester = pivotFocusRequester
                    )
                }
            }
            // CASE C: Grid View ON + Infinite Loop OFF
            // Uses BringIntoViewSpec for automatic pivot-aligned scrolling
            else -> {
                CompositionLocalProvider(LocalBringIntoViewSpec provides pivotSpec) {
                    FiniteGridContent(
                        listState = listState,
                        startPadding = startPadding,
                        endPadding = endPadding,
                        isTopNav = isTopNav,
                        rowIndex = rowIndex,
                        items = items,
                        visibleItemCount = visibleItemCount,
                        onMovieClick = onMovieClick,
                        onViewMore = onViewMore,
                        onFocused = wrappedOnFocused,
                        entryRequester = entryRequester,
                        drawerRequester = drawerRequester,
                        locallyFocusedItemId = locallyFocusedItemId,
                        isGlobalFocusPresent = isGlobalFocusPresent,
                        isFirstRow = isFirstRow,
                        rowHeight = rowHeight,
                        upKeyDebouncer = upKeyDebouncer,
                        repeatGate = repeatGate,
                        pivotFocusRequester = pivotFocusRequester
                    )
                }
            }
        }
    }
}

/**
 * CASE A: LINEAR SCROLLING (Grid View OFF - DEFAULT)
 * Full list, no truncation, no infinite logic, no ViewMore
 * 
 * Netflix-grade optimizations:
 * - Image prefetching ahead of focus
 * - beyondBoundsItemCount for precomposition
 * - Stable keys for proper recycling
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun LinearContent(
    listState: androidx.compose.foundation.lazy.LazyListState,
    startPadding: Dp,
    endPadding: Dp,
    isTopNav: Boolean,
    rowIndex: Int,
    items: List<MetaItem>,
    onMovieClick: (MetaItem) -> Unit,
    onFocused: (MetaItem?, String) -> Unit,
    entryRequester: FocusRequester,
    drawerRequester: FocusRequester,
    locallyFocusedItemId: String?,
    isGlobalFocusPresent: Boolean,
    isFirstRow: Boolean,
    rowHeight: Dp,
    upKeyDebouncer: UpKeyDebouncer,
    repeatGate: DpadRepeatGate,
    pivotFocusRequester: FocusRequester? = null,
    isLandscapeCards: Boolean = false,
    enrichedItems: Map<String, MetaItem> = emptyMap(),
    effectiveItemWidth: Dp = ITEM_WIDTH
) {
    val context = LocalContext.current

    // Prefetch image URLs list (cached to avoid allocation during scroll)
    val imageUrls = remember(items, isLandscapeCards, enrichedItems) {
        if (isLandscapeCards) {
            items.map { item ->
                val enriched = enrichedItems["${item.type}:${item.id}"]
                enriched?.background ?: enriched?.poster ?: item.poster
            }
        } else {
            items.map { it.poster }
        }
    }

    // Debounce navbar escape: track last LEFT key time to prevent escape during long-press
    val leftKeyDebouncer = remember { RowKeyRepeatDebouncer() }
    val navbarEscapeDebounceMs = 300L // Only allow escape if 300ms since last LEFT press

    // Pre-scroll warmup: compose off-screen items to populate recycler + compile GPU shaders
    // Scrolls forward 3 items and back in ~2 frames (invisible at 60fps)
    LaunchedEffect(Unit) {
        withFrameNanos { } // Wait for initial layout
        val idx = listState.firstVisibleItemIndex
        val off = listState.firstVisibleItemScrollOffset
        listState.scrollToItem(idx + 3)
        listState.scrollToItem(idx, off)
    }

    LazyRow(
        state = listState,
        modifier = Modifier
            .height(rowHeight)
            .graphicsLayer { clip = false },
        contentPadding = PaddingValues(start = startPadding, end = endPadding),
        horizontalArrangement = Arrangement.spacedBy(ITEM_SPACING)
    ) {
        itemsIndexed(
            items = items,
            key = { index, item -> "${rowIndex}_${item.id}_$index" },
            contentType = { _, _ -> if (isLandscapeCards) "landscape" else "movie" }
        ) { index, item ->
            val isFirstItem = index == 0
            val isLastItem = index == items.lastIndex
            val uniqueKey = "${rowIndex}_${item.id}_$index"

            val shouldRequestFocus = when {
                uniqueKey == locallyFocusedItemId -> true
                !isGlobalFocusPresent && isFirstRow && isFirstItem -> true
                else -> false
            }

            Box(
                modifier = Modifier
                    .width(effectiveItemWidth)
                    .graphicsLayer { clip = false }
                    .onPreviewKeyEvent { keyEvent ->
                        if (repeatGate.shouldConsume(keyEvent)) return@onPreviewKeyEvent true
                        if (keyEvent.type == KeyEventType.KeyDown) {
                            when {
                                keyEvent.key == Key.DirectionRight -> {
                                    if (isTopNav && isLastItem) {
                                        true // Block escape to top navbar from end of row
                                    } else {
                                        false
                                    }
                                }
                                keyEvent.key == Key.DirectionLeft -> {
                                    // Always update time on ANY left press for debounce tracking
                                    val now = System.currentTimeMillis()
                                    val timeSinceLastLeft = now - leftKeyDebouncer.lastTime
                                    leftKeyDebouncer.lastTime = now

                                    if (isFirstItem) {
                                        // Only escape to navbar if this is a deliberate press (not rapid long-press repeat)
                                        if (!isTopNav && timeSinceLastLeft > navbarEscapeDebounceMs) {
                                            drawerRequester.requestFocus()
                                        }
                                        true // Consume at first item to prevent focus escaping
                                    } else {
                                        false // Let normal navigation happen
                                    }
                                }
                                keyEvent.key == Key.DirectionUp -> {
                                    if (isTopNav) {
                                        // TRACK UP KEY on ALL rows to handle rapid scrolling from bottom
                                        val now = System.currentTimeMillis()
                                        val timeSinceLastUp = now - upKeyDebouncer.lastTime
                                        upKeyDebouncer.lastTime = now

                                        if (isFirstRow) {
                                            // Only escape to navbar if slow press
                                            if (timeSinceLastUp > 300L) {
                                                drawerRequester.requestFocus()
                                            }
                                            true // Block default
                                        } else {
                                            false // Allow up nav
                                        }
                                    } else false
                                }
                                else -> false
                            }
                        } else false
                    }
            ) {
                if (isLandscapeCards) {
                    val enriched = enrichedItems["${item.type}:${item.id}"]
                    LumeraLandscapeCard(
                        title = item.name,
                        backdropUrl = enriched?.background,
                        logoUrl = enriched?.logo,
                        posterUrl = item.poster,
                        onClick = { onMovieClick(item) },
                        progress = item.progress,
                        onFocused = {
                            ImagePrefetcher.prefetchAroundLandscape(context, imageUrls, index)
                            onFocused(item, uniqueKey)
                        },
                        modifier = Modifier.then(
                            if (shouldRequestFocus) Modifier.focusRequester(entryRequester)
                            else if (pivotFocusRequester != null && index == listState.firstVisibleItemIndex) Modifier.focusRequester(pivotFocusRequester)
                            else Modifier
                        )
                    )
                } else {
                    LumeraCard(
                        title = item.name,
                        posterUrl = item.poster,
                        onClick = { onMovieClick(item) },
                        progress = item.progress,
                        onFocused = {
                            ImagePrefetcher.prefetchAround(context, imageUrls, index)
                            onFocused(item, uniqueKey)
                        },
                        modifier = Modifier.then(
                            if (shouldRequestFocus) Modifier.focusRequester(entryRequester)
                            else if (pivotFocusRequester != null && index == listState.firstVisibleItemIndex) Modifier.focusRequester(pivotFocusRequester)
                            else Modifier
                        )
                    )
                }
            }
        }
    }
}

/**
 * ============================================================================
 * CASE B: INFINITE GRID (Grid View ON + Infinite Loop ON)
 * ============================================================================
 * 
 * Netflix-grade truly infinite scrolling implementation:
 * 
 * KEY INSIGHT: Use a VERY large bounded list (100+ repetitions) starting from
 * the middle. Users will NEVER reach the end in practice, making it feel
 * truly infinite without any visible jumps or recentering.
 * 
 * Optimizations:
 * 1. 100 repetitions = 1500+ items - practically infinite
 * 2. Start at section 50 (middle) - can scroll 50 sections in either direction
 * 3. Stable keys with generation suffix - bounded memory, proper recycling
 * 4. No recentering needed - eliminates all visible jumps
 * 5. Image prefetching for smooth loading
 * ============================================================================
 */
private const val INFINITE_LOOP_GENERATIONS = 100000 // 100,000 repetitions = practically infinite

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun InfiniteGridContent(
    listState: androidx.compose.foundation.lazy.LazyListState,
    startPadding: Dp,
    endPadding: Dp,
    isTopNav: Boolean,
    rowIndex: Int,
    items: List<MetaItem>,
    visibleItemCount: Int,
    onMovieClick: (MetaItem) -> Unit,
    onViewMore: () -> Unit,
    onFocused: (MetaItem?, String) -> Unit,
    entryRequester: FocusRequester,
    drawerRequester: FocusRequester,
    locallyFocusedItemId: String?,
    isGlobalFocusPresent: Boolean,
    isFirstRow: Boolean,
    isRestoredState: Boolean = false,
    rowHeight: Dp,
    upKeyDebouncer: UpKeyDebouncer,
    repeatGate: DpadRepeatGate,
    pivotFocusRequester: FocusRequester? = null
) {
    val context = LocalContext.current

    val truncatedMovies = remember(items, visibleItemCount) { 
        items.take(visibleItemCount.coerceIn(5, 50)) 
    }

    // Build base data list (one section) - movies + ViewMore card
    val baseDataList: List<GridRowItem> = remember(truncatedMovies) {
        truncatedMovies.map { GridRowItem.MovieItem(it) } + GridRowItem.ViewMoreItem
    }
    val sectionSize = baseDataList.size
    
    // Calculate total items and start position
    val totalItems = sectionSize * INFINITE_LOOP_GENERATIONS

    // Prefetch image URLs list
    val imageUrls = remember(truncatedMovies) { truncatedMovies.map { it.poster } }
    
    // Track focused index for ViewMore card's directional alpha
    var currentFocusedIndex by remember(listState) { 
        mutableIntStateOf(listState.firstVisibleItemIndex) 
    }
    
    // Debounce navbar escape: track last LEFT key time to prevent escape during long-press
    val leftKeyDebouncer = remember { RowKeyRepeatDebouncer() }
    val rightKeyDebouncer = remember { RowKeyRepeatDebouncer() }
    val navbarEscapeDebounceMs = 300L

    // Parse last focused key to find the item ID (not scroll index)
    // Key format: "rowIndex_movieId_scrollIndex" or "rowIndex_viewmore_scrollIndex"
    val lastFocusedItemId = remember(locallyFocusedItemId) {
        locallyFocusedItemId?.let { key ->
            // It is already filtered to start with "${rowIndex}_" by parent
             val withoutPrefix = key.removePrefix("${rowIndex}_")
             if (withoutPrefix.startsWith("viewmore")) {
                 "viewmore"
             } else {
                 withoutPrefix.substringBeforeLast("_")
             }
        }
    }

    // Pre-scroll warmup: compose off-screen items to populate recycler + compile GPU shaders
    LaunchedEffect(Unit) {
        withFrameNanos { }
        val idx = listState.firstVisibleItemIndex
        val off = listState.firstVisibleItemScrollOffset
        listState.scrollToItem(idx + 3)
        listState.scrollToItem(idx, off)
    }

    LazyRow(
        state = listState,
        modifier = Modifier
            .height(rowHeight)
            .graphicsLayer { clip = false },
        contentPadding = PaddingValues(start = startPadding, end = endPadding),
        horizontalArrangement = Arrangement.spacedBy(ITEM_SPACING)
    ) {
        items(
            count = totalItems,
            // Keys use generation (scrollIndex / sectionSize) for uniqueness
            // This bounds memory to INFINITE_LOOP_GENERATIONS unique keys per item
            key = { scrollIndex ->
                val logicalIndex = scrollIndex % sectionSize
                val generation = scrollIndex / sectionSize
                val item = baseDataList[logicalIndex]
                when (item) {
                    is GridRowItem.MovieItem -> "${rowIndex}_${item.movie.id}_g$generation"
                    is GridRowItem.ViewMoreItem -> "${rowIndex}_viewmore_g$generation"
                }
            },
            contentType = { scrollIndex ->
                when (baseDataList[scrollIndex % sectionSize]) {
                    is GridRowItem.MovieItem -> "movie"
                    is GridRowItem.ViewMoreItem -> "viewmore"
                }
            }
        ) { scrollIndex ->
            val logicalIndex = scrollIndex % sectionSize
            val item = baseDataList[logicalIndex]
            val isEntryItem = scrollIndex == 0
            val isLoopStart = logicalIndex == 0

            // For focus restoration: match by item ID, only in the current visible section
            // to avoid multiple items requesting focus across different generations
            val currentSectionStart = (listState.firstVisibleItemIndex / sectionSize) * sectionSize
            val isInCurrentSection = scrollIndex >= currentSectionStart && scrollIndex < currentSectionStart + sectionSize
            
            val shouldRequestFocus = when {
                // First row, no previous focus - focus the entry item
                !isGlobalFocusPresent && isFirstRow && isEntryItem -> true
                // Match by item ID in the current visible section only
                isInCurrentSection && item is GridRowItem.MovieItem && lastFocusedItemId == item.movie.id -> true
                isInCurrentSection && item is GridRowItem.ViewMoreItem && lastFocusedItemId == "viewmore" -> true
                else -> false
            }

            when (item) {
                is GridRowItem.MovieItem -> {
                    Box(
                        modifier = Modifier
                            .width(ITEM_WIDTH)
                            .graphicsLayer { clip = false }
                            .onPreviewKeyEvent { keyEvent ->
                                if (repeatGate.shouldConsume(keyEvent)) return@onPreviewKeyEvent true
                                if (keyEvent.type == KeyEventType.KeyDown) {
                                    when {
                                        keyEvent.key == Key.DirectionRight -> {
                                            // Prime RIGHT debounce while moving through posters so ViewMore can
                                            // block long-press escape on arrival.
                                            rightKeyDebouncer.lastTime = System.currentTimeMillis()
                                            false
                                        }
                                        keyEvent.key == Key.DirectionLeft -> {
                                            // Always update time on ANY left press for debounce tracking
                                            val now = System.currentTimeMillis()
                                            val timeSinceLastLeft = now - leftKeyDebouncer.lastTime
                                            leftKeyDebouncer.lastTime = now
                                            
                                            if (isLoopStart) {
                                                // Only escape to navbar if this is a deliberate press (not rapid long-press repeat)
                                                if (!isTopNav && timeSinceLastLeft > navbarEscapeDebounceMs) {
                                                    drawerRequester.requestFocus()
                                                }
                                                true // Consume at loop start to prevent focus escaping
                                            } else {
                                                false // Let normal navigation happen
                                            }
                                        }
                                        keyEvent.key == Key.DirectionUp -> {
                                            if (isTopNav) {
                                                // TRACK UP KEY on ALL rows
                                                val now = System.currentTimeMillis()
                                                val timeSinceLastUp = now - upKeyDebouncer.lastTime
                                                upKeyDebouncer.lastTime = now

                                                if (isFirstRow) {
                                                    if (timeSinceLastUp > 300L) {
                                                        drawerRequester.requestFocus()
                                                    }
                                                    true
                                                } else {
                                                    false
                                                }
                                            } else false
                                        }
                                        else -> false
                                    }
                                } else false
                            }
                    ) {
                        val uniqueKey = "${rowIndex}_${item.movie.id}_$scrollIndex"
                        
                        LumeraCard(
                            title = item.movie.name,
                            posterUrl = item.movie.poster,
                            onClick = { onMovieClick(item.movie) },
                            progress = item.movie.progress,
                            onFocused = {
                                currentFocusedIndex = scrollIndex
                                val logicalIndex = scrollIndex % sectionSize
                                if (logicalIndex < imageUrls.size) {
                                    ImagePrefetcher.prefetchAround(context, imageUrls, logicalIndex)
                                }
                                onFocused(item.movie, uniqueKey)
                            },
                            modifier = Modifier.then(
                                if (shouldRequestFocus) Modifier.focusRequester(entryRequester)
                                else if (pivotFocusRequester != null && scrollIndex == listState.firstVisibleItemIndex) Modifier.focusRequester(pivotFocusRequester)
                                else Modifier
                            )
                        )
                    }
                }

                is GridRowItem.ViewMoreItem -> {
                    val uniqueKey = "${rowIndex}_viewmore_$scrollIndex"
                    
                    InfiniteViewMoreCard(
                        isTopNav = isTopNav,
                        isFirstRow = isFirstRow,
                        drawerRequester = drawerRequester,
                        upKeyDebouncer = upKeyDebouncer,
                        rightKeyDebouncer = rightKeyDebouncer,
                        navbarEscapeDebounceMs = navbarEscapeDebounceMs,
                        repeatGate = repeatGate,
                        scrollIndex = scrollIndex,
                        currentFocusedIndex = currentFocusedIndex,
                        onClick = onViewMore,
                        onFocused = {
                            currentFocusedIndex = scrollIndex
                            onFocused(null, uniqueKey)
                        },
                        modifier = Modifier
                            .then(if (shouldRequestFocus) Modifier.focusRequester(entryRequester) else Modifier)
                            .then(
                                if (pivotFocusRequester != null && scrollIndex == listState.firstVisibleItemIndex) {
                                    Modifier.focusRequester(pivotFocusRequester)
                                } else Modifier
                            )
                    )
                }
            }
        }
    }
    
    // Initialize scroll position to middle on first composition
    // Skip initialization if this is a restored state (coming back to this row)
    LaunchedEffect(Unit) {
        if (!isRestoredState && listState.firstVisibleItemIndex == 0) {
            listState.scrollToItem(0)
        }
    }
}

/**
 * CASE C: FINITE GRID (Grid View ON + Infinite Loop OFF)
 * Truncated list that ends at ViewMore card, no looping
 * 
 * Netflix-grade optimizations:
 * - Image prefetching ahead of focus
 * - beyondBoundsItemCount for precomposition
 * - Stable keys for proper recycling
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FiniteGridContent(
    listState: androidx.compose.foundation.lazy.LazyListState,
    startPadding: Dp,
    endPadding: Dp,
    isTopNav: Boolean,
    rowIndex: Int,
    items: List<MetaItem>,
    visibleItemCount: Int,
    onMovieClick: (MetaItem) -> Unit,
    onViewMore: () -> Unit,
    onFocused: (MetaItem?, String) -> Unit,
    entryRequester: FocusRequester,
    drawerRequester: FocusRequester,
    locallyFocusedItemId: String?,
    isGlobalFocusPresent: Boolean,
    isFirstRow: Boolean,
    rowHeight: Dp,
    upKeyDebouncer: UpKeyDebouncer,
    repeatGate: DpadRepeatGate,
    pivotFocusRequester: FocusRequester? = null
) {
    val context = LocalContext.current
    
    val truncatedMovies = remember(items, visibleItemCount) { 
        items.take(visibleItemCount.coerceIn(5, 50)) 
    }
    
    // Prefetch image URLs list
    val imageUrls = remember(truncatedMovies) { truncatedMovies.map { it.poster } }
    
    // Debounce navbar escape: track last LEFT key time to prevent escape during long-press
    val leftKeyDebouncer = remember { RowKeyRepeatDebouncer() }
    val navbarEscapeDebounceMs = 300L

    // Build finite list: [Movie 0 ... Movie N, ViewMoreItem]
    val dataList: List<GridRowItem> = remember(truncatedMovies) {
        truncatedMovies.map { GridRowItem.MovieItem(it) } + GridRowItem.ViewMoreItem
    }

    // Pre-scroll warmup: compose off-screen items to populate recycler + compile GPU shaders
    LaunchedEffect(Unit) {
        withFrameNanos { }
        val idx = listState.firstVisibleItemIndex
        val off = listState.firstVisibleItemScrollOffset
        listState.scrollToItem(idx + 3)
        listState.scrollToItem(idx, off)
    }

    LazyRow(
        state = listState,
        modifier = Modifier
            .height(rowHeight)
            .graphicsLayer { clip = false },
        contentPadding = PaddingValues(start = startPadding, end = endPadding),
        horizontalArrangement = Arrangement.spacedBy(ITEM_SPACING)
    ) {
        itemsIndexed(
            items = dataList,
            key = { index, item ->
                when (item) {
                    is GridRowItem.MovieItem -> "${rowIndex}_${item.movie.id}_$index"
                    is GridRowItem.ViewMoreItem -> "${rowIndex}_viewmore"
                }
            },
            contentType = { _, item ->
                when (item) {
                    is GridRowItem.MovieItem -> "movie"
                    is GridRowItem.ViewMoreItem -> "viewmore"
                }
            }
        ) { index, item ->
            val isFirstItem = index == 0
            val isLastItem = index == dataList.size - 1

            val uniqueKey = when (item) {
                is GridRowItem.MovieItem -> "${rowIndex}_${item.movie.id}_$index"
                is GridRowItem.ViewMoreItem -> "${rowIndex}_viewmore_$index"
            }

            val shouldRequestFocus = when {
                uniqueKey == locallyFocusedItemId -> true
                !isGlobalFocusPresent && isFirstRow && isFirstItem -> true
                else -> false
            }

            when (item) {
                is GridRowItem.MovieItem -> {
                    Box(
                        modifier = Modifier
                            .width(ITEM_WIDTH)
                            .graphicsLayer { clip = false }
                            .onPreviewKeyEvent { keyEvent ->
                                if (repeatGate.shouldConsume(keyEvent)) return@onPreviewKeyEvent true
                                if (keyEvent.type == KeyEventType.KeyDown) {
                                    when {
                                        keyEvent.key == Key.DirectionLeft -> {
                                            // Always update time on ANY left press for debounce tracking
                                            val now = System.currentTimeMillis()
                                            val timeSinceLastLeft = now - leftKeyDebouncer.lastTime
                                            leftKeyDebouncer.lastTime = now
                                            
                                            if (isFirstItem) {
                                                // Only escape to navbar if this is a deliberate press (not rapid long-press repeat)
                                                if (!isTopNav && timeSinceLastLeft > navbarEscapeDebounceMs) {
                                                    drawerRequester.requestFocus()
                                                }
                                                true // Consume at first item to prevent focus escaping
                                            } else {
                                                false // Let normal navigation happen
                                            }
                                        }
                                        keyEvent.key == Key.DirectionUp -> {
                                            if (isTopNav) {
                                                // TRACK UP KEY on ALL rows
                                                val now = System.currentTimeMillis()
                                                val timeSinceLastUp = now - upKeyDebouncer.lastTime
                                                upKeyDebouncer.lastTime = now

                                                if (isFirstRow) {
                                                    if (timeSinceLastUp > 300L) {
                                                        drawerRequester.requestFocus()
                                                    }
                                                    true
                                                } else {
                                                    false
                                                }
                                            } else false
                                        }
                                        else -> false
                                    }
                                } else false
                            }
                    ) {
                        LumeraCard(
                            title = item.movie.name,
                            posterUrl = item.movie.poster,
                            onClick = { onMovieClick(item.movie) },
                            progress = item.movie.progress,
                            onFocused = {
                                ImagePrefetcher.prefetchAround(context, imageUrls, index)
                                onFocused(item.movie, uniqueKey)
                            },
                            modifier = Modifier.then(
                                if (shouldRequestFocus) Modifier.focusRequester(entryRequester)
                                else if (pivotFocusRequester != null && index == listState.firstVisibleItemIndex) Modifier.focusRequester(pivotFocusRequester)
                                else Modifier
                            )
                        )
                    }
                }

                is GridRowItem.ViewMoreItem -> {
                    Box(
                        modifier = Modifier
                            .width(ITEM_WIDTH)
                            .graphicsLayer { clip = false }
                            .onPreviewKeyEvent { keyEvent ->
                                if (repeatGate.shouldConsume(keyEvent)) return@onPreviewKeyEvent true
                                if (keyEvent.type == KeyEventType.KeyDown) {
                                    when (keyEvent.key) {
                                        Key.DirectionRight -> true // Block RIGHT at ViewMore (end of list)
                                        Key.DirectionUp -> {
                                            if (isTopNav) {
                                                val now = System.currentTimeMillis()
                                                val timeSinceLastUp = now - upKeyDebouncer.lastTime
                                                upKeyDebouncer.lastTime = now

                                                if (isFirstRow) {
                                                    if (timeSinceLastUp > 300L) {
                                                        drawerRequester.requestFocus()
                                                    }
                                                    true
                                                } else {
                                                    false
                                                }
                                            } else false
                                        }
                                        else -> false
                                    }
                                } else false
                            }
                    ) {
                        ViewMoreCard(
                            onClick = onViewMore,
                            onFocused = { 
                                onFocused(null, uniqueKey) 
                            },
                            modifier = Modifier
                                .then(if (shouldRequestFocus) Modifier.focusRequester(entryRequester) else Modifier)
                                .then(
                                    if (pivotFocusRequester != null && index == listState.firstVisibleItemIndex) {
                                        Modifier.focusRequester(pivotFocusRequester)
                                    } else Modifier
                                )
                        )
                    }
                }
            }
        }
    }
}

/**
 * ViewMore card with directional alpha animation (Infinite mode only)
 */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun InfiniteViewMoreCard(
    isTopNav: Boolean,
    isFirstRow: Boolean,
    drawerRequester: FocusRequester,
    upKeyDebouncer: UpKeyDebouncer,
    rightKeyDebouncer: RowKeyRepeatDebouncer,
    navbarEscapeDebounceMs: Long,
    repeatGate: DpadRepeatGate,
    scrollIndex: Int,
    currentFocusedIndex: Int,
    onClick: () -> Unit,
    onFocused: () -> Unit,
    modifier: Modifier = Modifier
) {
    var isFocused by remember { mutableStateOf(false) }

    val isToTheLeft = scrollIndex < currentFocusedIndex
    val targetAlpha = if (isToTheLeft) 0f else 1f

    val animatedAlpha by animateFloatAsState(
        targetValue = targetAlpha,
        animationSpec = spring(
            stiffness = Spring.StiffnessMediumLow,
            dampingRatio = Spring.DampingRatioNoBouncy
        ),
        label = "viewMoreAlpha"
    )

    Box(
        modifier = modifier
            .width(ITEM_WIDTH)
            .graphicsLayer {
                clip = false
                alpha = animatedAlpha
            }
            .onPreviewKeyEvent { keyEvent ->
                if (repeatGate.shouldConsume(keyEvent)) return@onPreviewKeyEvent true
                if (keyEvent.type == KeyEventType.KeyDown) {
                    when (keyEvent.key) {
                        Key.DirectionRight -> {
                            // Debounce RIGHT long-press repeats so focus does not escape unexpectedly.
                            val now = System.currentTimeMillis()
                            val timeSinceLastRight = now - rightKeyDebouncer.lastTime
                            rightKeyDebouncer.lastTime = now
                            timeSinceLastRight <= navbarEscapeDebounceMs
                        }
                        Key.DirectionUp -> {
                            if (isTopNav) {
                                val now = System.currentTimeMillis()
                                val timeSinceLastUp = now - upKeyDebouncer.lastTime
                                upKeyDebouncer.lastTime = now

                                if (isFirstRow) {
                                    if (timeSinceLastUp > 300L) {
                                        drawerRequester.requestFocus()
                                    }
                                    true
                                } else {
                                    false
                                }
                            } else false
                        }
                        else -> false
                    }
                } else false
            }
            .onFocusChanged { focusState ->
                isFocused = focusState.isFocused
                if (focusState.isFocused) {
                    onFocused()
                }
            }
    ) {
        ViewMoreCard(
            onClick = onClick,
            onFocused = null
        )
    }
}
