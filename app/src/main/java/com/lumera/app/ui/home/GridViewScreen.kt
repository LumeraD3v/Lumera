package com.lumera.app.ui.home

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.BringIntoViewSpec
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import com.lumera.app.R
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.lumera.app.data.model.stremio.MetaItem
import com.lumera.app.ui.components.LumeraCard
import com.lumera.app.ui.utils.ImagePrefetcher

private const val COLUMNS = 6
private const val DPAD_REPEAT_INTERVAL_HORIZONTAL_MS = 150L
private const val DPAD_REPEAT_INTERVAL_VERTICAL_MS = 200L

private class GridFocusPivotSpec(
    private val pivotOffset: Float,
    private val stiffnessProvider: (() -> Float)? = null
) : BringIntoViewSpec {
    
    @Deprecated("", level = DeprecationLevel.HIDDEN)
    override val scrollAnimationSpec: androidx.compose.animation.core.AnimationSpec<Float>
        get() = androidx.compose.animation.core.spring(
            stiffness = stiffnessProvider?.invoke() ?: androidx.compose.animation.core.Spring.StiffnessLow,
            dampingRatio = androidx.compose.animation.core.Spring.DampingRatioNoBouncy,
            visibilityThreshold = 0.1f
        )
    
    override fun calculateScrollDistance(offset: Float, size: Float, containerSize: Float): Float {
        // Calculate where we want the focused item to appear (at pivot point from top)
        val targetPosition = pivotOffset
        val currentPosition = offset
        return currentPosition - targetPosition
    }
}

@OptIn(ExperimentalTvMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun GridViewScreen(
    title: String,
    items: List<MetaItem>,
    lastFocusedIndex: Int?,
    onFocusChange: (Int) -> Unit,
    onMovieClick: (MetaItem) -> Unit,
    onBack: () -> Unit,
    onLoadMore: () -> Unit = {},
    // Scroll position persistence for instant restoration
    initialScrollIndex: Int = 0,
    initialScrollOffset: Int = 0,
    onScrollPositionChange: (Int, Int) -> Unit = { _, _ -> }
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    
    val startPadding = 50.dp
    
    // Header height for pivot calculation
    val headerHeight = 48.dp
    val headerHeightPx = with(density) { headerHeight.toPx() }
    
    val dpadRepeatGate = remember {
        DpadRepeatGate(
            horizontalRepeatIntervalMs = DPAD_REPEAT_INTERVAL_HORIZONTAL_MS,
            verticalRepeatIntervalMs = DPAD_REPEAT_INTERVAL_VERTICAL_MS
        )
    }
    
    // Create pivot spec - items scroll to just below the fixed header
    val pivotSpec = remember(headerHeightPx) { 
        GridFocusPivotSpec(
            pivotOffset = headerHeightPx + 16f,
            stiffnessProvider = { androidx.compose.animation.core.Spring.StiffnessLow }
        )
    }
    
    // Grid state with restored position - start at exact item index for instant restoration
    val gridState = rememberLazyGridState(
        initialFirstVisibleItemIndex = lastFocusedIndex ?: initialScrollIndex,
        initialFirstVisibleItemScrollOffset = initialScrollOffset
    )
    var lastSavedScrollIndex by remember { mutableIntStateOf(Int.MIN_VALUE) }
    var lastSavedScrollOffset by remember { mutableIntStateOf(Int.MIN_VALUE) }

    // Persist scroll state with throttling, then flush exact final position on dispose.
    LaunchedEffect(gridState) {
        snapshotFlow { Pair(gridState.firstVisibleItemIndex, gridState.firstVisibleItemScrollOffset) }
            .collect { (index, offset) ->
                val shouldPersist =
                    lastSavedScrollIndex == Int.MIN_VALUE ||
                        index != lastSavedScrollIndex ||
                        kotlin.math.abs(offset - lastSavedScrollOffset) >= 36

                if (shouldPersist) {
                    lastSavedScrollIndex = index
                    lastSavedScrollOffset = offset
                    onScrollPositionChange(index, offset)
                }
            }
    }

    DisposableEffect(gridState) {
        onDispose {
            val finalIndex = gridState.firstVisibleItemIndex
            val finalOffset = gridState.firstVisibleItemScrollOffset
            if (finalIndex != lastSavedScrollIndex || finalOffset != lastSavedScrollOffset) {
                onScrollPositionChange(finalIndex, finalOffset)
            }
        }
    }
    
    // Lazy pagination: load more items when scrolling near the end
    val lastVisibleIndex = gridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
    LaunchedEffect(lastVisibleIndex, items.size) {
        if (items.size > 0 && lastVisibleIndex >= items.size - 12) {
            onLoadMore()
        }
    }
    
    // Entry focus requester
    val entryRequester = remember { FocusRequester() }
    val backIconRequester = remember { FocusRequester() }
    val cardRequesters = remember { mutableMapOf<Int, FocusRequester>() }
    var pendingDirectionalTargetIndex by remember { mutableStateOf<Int?>(null) }
    var isBackIconFocused by remember { mutableStateOf(false) }
    val backIconScale by animateFloatAsState(
        targetValue = if (isBackIconFocused) 1.05f else 1f,
        label = "grid_back_icon_scale"
    )
    var lastFocusedPosterIndex by remember {
        mutableIntStateOf((lastFocusedIndex ?: 0).coerceIn(0, (items.lastIndex).coerceAtLeast(0)))
    }
    
    // Prefetch image URLs list
    val imageUrls = remember(items) { items.map { it.poster } }
    
    // Track if focus has been restored to prevent re-triggering
    var focusRestored by remember { mutableStateOf(false) }

    BackHandler {
        if (isBackIconFocused) {
            onBack()
        } else {
            backIconRequester.requestFocus()
        }
    }

    // Focus restoration - just request focus, no scrolling needed since grid starts at correct position
    LaunchedEffect(lastFocusedIndex, gridState.layoutInfo.totalItemsCount) {
        if (!focusRestored && gridState.layoutInfo.totalItemsCount > 0) {
            // Small delay to ensure item is composed
            kotlinx.coroutines.delay(16)
            entryRequester.requestFocus()
            focusRestored = true
        }
    }

    // If UP/DOWN fallback used native scroll, re-apply focus as soon as the intended target is composed.
    LaunchedEffect(gridState) {
        snapshotFlow { pendingDirectionalTargetIndex to gridState.layoutInfo.visibleItemsInfo.map { it.index } }
            .collect { (pendingTarget, visibleIndexes) ->
                if (pendingTarget != null && visibleIndexes.contains(pendingTarget)) {
                    cardRequesters[pendingTarget]?.requestFocus()
                }
            }
    }

    // Use theme background color
    val backgroundColor = MaterialTheme.colorScheme.background
    
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(backgroundColor)
    ) {
        // ══════════════════════════════════════════════════════════════
        // SCROLLABLE GRID - Positioned first so header overlays it
        // ══════════════════════════════════════════════════════════════
        CompositionLocalProvider(LocalBringIntoViewSpec provides pivotSpec) {
            LazyVerticalGrid(
                columns = GridCells.Fixed(COLUMNS),
                state = gridState,
                contentPadding = PaddingValues(
                    start = startPadding,
                    end = 50.dp,
                    top = headerHeight + 8.dp, // Start below header
                    bottom = 78.dp
                ),
                horizontalArrangement = Arrangement.spacedBy(20.dp),
                verticalArrangement = Arrangement.spacedBy(24.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                itemsIndexed(
                    items = items,
                    key = { index, item -> "${item.id}_$index" }
                ) { index, item ->
                    
                    val shouldRequestFocus = if (lastFocusedIndex != null) {
                        index == lastFocusedIndex
                    } else {
                        index == 0
                    }
                    val itemRequester = remember(index) { FocusRequester() }
                    val effectiveRequester = if (shouldRequestFocus) entryRequester else itemRequester
                    DisposableEffect(index, effectiveRequester) {
                        cardRequesters[index] = effectiveRequester
                        onDispose {
                            if (cardRequesters[index] === effectiveRequester) {
                                cardRequesters.remove(index)
                            }
                        }
                    }
                    
                    LumeraCard(
                        title = item.name,
                        posterUrl = item.poster,
                        onClick = { onMovieClick(item) },
                        modifier = Modifier
                            .aspectRatio(2f / 3f)
                            .onPreviewKeyEvent { keyEvent ->
                                if (dpadRepeatGate.shouldConsume(keyEvent)) return@onPreviewKeyEvent true
                                if (keyEvent.type == KeyEventType.KeyDown) {
                                    when (keyEvent.key) {
                                        // Block UP navigation on first row
                                        Key.DirectionUp -> {
                                            if (index < COLUMNS) {
                                                pendingDirectionalTargetIndex = null
                                                backIconRequester.requestFocus()
                                                return@onPreviewKeyEvent true
                                            }
                                            val targetIndex = index - COLUMNS
                                            val targetRequester = cardRequesters[targetIndex]
                                            if (targetRequester != null) {
                                                pendingDirectionalTargetIndex = null
                                                targetRequester.requestFocus()
                                                return@onPreviewKeyEvent true
                                            } else {
                                                // Let native focus/scroll happen, then correct to the intended same-column target.
                                                pendingDirectionalTargetIndex = targetIndex
                                                return@onPreviewKeyEvent false
                                            }
                                        }
                                        Key.DirectionDown -> {
                                            val targetIndex = index + COLUMNS
                                            if (targetIndex >= items.size) {
                                                pendingDirectionalTargetIndex = null
                                                return@onPreviewKeyEvent true
                                            }
                                            val targetRequester = cardRequesters[targetIndex]
                                            if (targetRequester != null) {
                                                pendingDirectionalTargetIndex = null
                                                targetRequester.requestFocus()
                                                return@onPreviewKeyEvent true
                                            } else {
                                                // Let native focus/scroll happen, then correct to the intended same-column target.
                                                pendingDirectionalTargetIndex = targetIndex
                                                return@onPreviewKeyEvent false
                                            }
                                        }
                                        // Block LEFT navigation on first column (no exit on left key)
                                        Key.DirectionLeft -> {
                                            pendingDirectionalTargetIndex = null
                                            if (index % COLUMNS == 0) {
                                                return@onPreviewKeyEvent true
                                            }
                                        }
                                        Key.DirectionRight -> {
                                            pendingDirectionalTargetIndex = null
                                        }
                                    }
                                }
                                false
                            }
                            .onFocusChanged {
                                if (it.isFocused) {
                                    ImagePrefetcher.prefetchAround(context, imageUrls, index, count = 12)
                                    onFocusChange(index)
                                    lastFocusedPosterIndex = index
                                    val pendingTarget = pendingDirectionalTargetIndex
                                    if (pendingTarget != null) {
                                        if (index == pendingTarget) {
                                            // Correction reached intended item.
                                            pendingDirectionalTargetIndex = null
                                        } else {
                                            // Keep pending until correction actually succeeds.
                                            cardRequesters[pendingTarget]?.requestFocus()
                                        }
                                    }
                                }
                            }
                            .focusRequester(effectiveRequester)
                    )
                }
            }
        }
        
        // ══════════════════════════════════════════════════════════════
        // FIXED HEADER - Overlays grid with gradient fade
        // ══════════════════════════════════════════════════════════════
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.TopStart)
                .zIndex(10f)
        ) {
            // Gradient background - extends beyond header for smooth fade
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(headerHeight + 48.dp) // Extended gradient
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                backgroundColor,
                                backgroundColor,
                                backgroundColor,
                                backgroundColor.copy(alpha = 0.95f),
                                backgroundColor.copy(alpha = 0.7f),
                                backgroundColor.copy(alpha = 0.3f),
                                Color.Transparent
                            ),
                            startY = 0f,
                            endY = with(density) { (headerHeight + 48.dp).toPx() }
                        )
                    )
            )
            
            // Header content - tight padding
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(
                        start = startPadding,
                        top = 10.dp,
                        end = 50.dp,
                        bottom = 0.dp
                    ),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Back indicator (mirrored view more arrow)
                Box(
                    modifier = Modifier
                        .focusRequester(backIconRequester)
                        .onFocusChanged { isBackIconFocused = it.isFocused }
                        .onPreviewKeyEvent { event ->
                            if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                            when (event.key) {
                                Key.DirectionDown -> {
                                    if (items.isNotEmpty()) {
                                        val targetIndex = lastFocusedPosterIndex.coerceIn(0, items.lastIndex)
                                        val targetRequester = cardRequesters[targetIndex]
                                        if (targetRequester != null) {
                                            pendingDirectionalTargetIndex = null
                                            targetRequester.requestFocus()
                                        } else {
                                            pendingDirectionalTargetIndex = targetIndex
                                        }
                                    }
                                    true
                                }
                                Key.Enter, Key.NumPadEnter, Key.DirectionCenter, Key.Back -> {
                                    onBack()
                                    true
                                }
                                Key.DirectionUp, Key.DirectionLeft -> true
                                else -> false
                            }
                        }
                        .focusable()
                        .size(30.dp)
                        .graphicsLayer {
                            scaleX = -backIconScale // Mirror horizontally + animated scale
                            scaleY = backIconScale
                        }
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_view_more_arrow),
                        contentDescription = "Back",
                        tint = if (isBackIconFocused) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.5f),
                        modifier = Modifier.fillMaxSize()
                    )
                }
                
                Spacer(modifier = Modifier.width(10.dp))
                
                // Category title
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White
                    )
                )
            }
        }
    }
}
