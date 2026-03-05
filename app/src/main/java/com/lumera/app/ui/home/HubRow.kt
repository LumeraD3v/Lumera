package com.lumera.app.ui.home

import androidx.compose.animation.core.Spring
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.lumera.app.domain.HubGroupRow
import com.lumera.app.domain.HubItem
import kotlinx.coroutines.delay

private class HubKeyRepeatDebouncer {
    var lastTime: Long = 0L
}

/**
 * ============================================================================
 * HUB ROW - Container for Hub Cards
 * ============================================================================
 * 
 * A horizontal scrolling row that displays Hub Cards for category navigation.
 * Matches the pivot/padding behavior of movie rows for consistent UX.
 * 
 * Features:
 * - LazyRow with smooth horizontal scrolling
 * - Title header above cards
 * - Configurable shape passed to all child HubCards
 * - Focus-aware scrolling with pivot alignment
 * ============================================================================
 */
@OptIn(ExperimentalTvMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun HubRow(
    hubGroup: HubGroupRow,
    startPadding: Dp,
    onHubClick: (HubItem) -> Unit,
    onFocused: ((HubItem, String) -> Unit)? = null,
    entryRequester: FocusRequester,
    drawerRequester: FocusRequester,
    locallyFocusedItemId: String?,
    isTopNav: Boolean,
    rowIndex: Int,
    isFirstRow: Boolean = false,
    isLastRow: Boolean = false,
    externalListState: androidx.compose.foundation.lazy.LazyListState? = null,
    upKeyDebouncer: UpKeyDebouncer,
    repeatGate: DpadRepeatGate,
    pivotFocusRequester: FocusRequester? = null,
    isGlobalFocusPresent: Boolean = false
) {
    val density = LocalDensity.current
    val paddingPx = remember(density, startPadding) { with(density) { startPadding.toPx() } }

    // Use external state if provided, otherwise create local state
    val internalListState = rememberLazyListState()
    val listState = externalListState ?: internalListState
    
    // Detect if this is a restoration (coming back from details screen)
    val isRestoration = remember(locallyFocusedItemId, rowIndex, externalListState) {
        locallyFocusedItemId != null && externalListState != null
    }
    
    // Skip scroll flag - true during restoration, resets after focus is established
    var skipBringIntoViewScroll by remember { mutableStateOf(isRestoration) }
    
    // Reset skip flag after restoration is complete (focus is established)
    LaunchedEffect(isRestoration) {
        if (isRestoration) {
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
    
    // Debounce navbar escape: track last LEFT key time to prevent escape during long-press
    val leftKeyDebouncer = remember { HubKeyRepeatDebouncer() }
    val navbarEscapeDebounceMs = 300L

    // Calculate end padding to allow last item to align to left (pivot position)
    // End padding = Screen Width - Start Padding - Item Width based on shape
    val configuration = androidx.compose.ui.platform.LocalConfiguration.current
    val screenWidth = configuration.screenWidthDp.dp
    
    val itemWidth = when (hubGroup.shape) {
        com.lumera.app.domain.HubShape.HORIZONTAL -> 190.dp
        com.lumera.app.domain.HubShape.VERTICAL -> 120.dp
        com.lumera.app.domain.HubShape.SQUARE -> 140.dp
    }
    
    val endPadding = remember(screenWidth, startPadding, itemWidth) {
        (screenWidth - startPadding - itemWidth).coerceAtLeast(120.dp)
    }
    
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 12.dp)
            .graphicsLayer { clip = false }
    ) {
        // Row Title
        Text(
            text = hubGroup.title,
            color = Color.White.copy(0.9f),
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
            modifier = Modifier.padding(start = startPadding, bottom = 12.dp)
        )
        
        // Hub Cards Row
        CompositionLocalProvider(LocalBringIntoViewSpec provides pivotSpec) {
            LazyRow(
                state = listState,
                contentPadding = PaddingValues(start = startPadding, end = endPadding),
                horizontalArrangement = Arrangement.spacedBy(20.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .graphicsLayer { clip = false }
            ) {
                itemsIndexed(
                    items = hubGroup.items,
                    key = { index, item -> "hub_${hubGroup.id}_${item.id}_$index" }
                ) { index, hubItem ->
                    val isFirstItem = index == 0
                    val isLastItem = index == hubGroup.items.lastIndex
                    val uniqueKey = "${hubGroup.id}_${index}"
                    
                    // Parse last focused key to check for match
                    // Key format from HomeScreen: "hub_$uniqueKey" = "hub_${hubGroup.id}_${index}"
                    // locallyFocusedItemId passed here should effectively be "hub_${hubGroup.id}_${index}" if matched in HomeScreen
                    // BUT HomeScreen passes `wrappedOnFocusChange("hub_$focusKey")` where focusKey is "gid_index"
                    
                    val shouldRequestFocus = when {
                        locallyFocusedItemId != null && locallyFocusedItemId.endsWith(uniqueKey) -> true
                        !isGlobalFocusPresent && isFirstRow && isFirstItem -> true
                        else -> false
                    }

                    // Modifier chaining for focus requesters
                    // We attach entryRequester if this is the default focus target
                    // We attach pivotFocusRequester if this is the current pivot item
                    // An item can have BOTH if it's the first item of the first row
                    val focusModifier = Modifier
                        .then(if (shouldRequestFocus) Modifier.focusRequester(entryRequester) else Modifier)
                        .then(if (pivotFocusRequester != null && index == listState.firstVisibleItemIndex) Modifier.focusRequester(pivotFocusRequester) else Modifier)

                    Box(
                        modifier = Modifier
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
                                                // Only escape to navbar if this is a deliberate press
                                                if (!isTopNav && timeSinceLastLeft > navbarEscapeDebounceMs) {
                                                    drawerRequester.requestFocus()
                                                }
                                                true // Consume at first item to prevent focus escaping
                                            } else {
                                                false
                                            }
                                        }
                                        keyEvent.key == Key.DirectionUp -> {
                                            if (isTopNav) {
                                                val now = System.currentTimeMillis()
                                                val timeSinceLastUp = now - upKeyDebouncer.lastTime
                                                upKeyDebouncer.lastTime = now

                                                if (isFirstRow) { // Strict check: isFirstRow
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
                        HubCard(
                            hubItem = hubItem,
                            shape = hubGroup.shape,
                            onClick = { onHubClick(hubItem) },
                            onFocused = {
                                onFocused?.invoke(hubItem, uniqueKey)
                            },
                            modifier = focusModifier
                        )
                    }
                }
            }
        }
        
        // Bottom spacing - matches standard row height reduction (210dp -> 193dp = 17dp delta)
        // Standard rows are spaced by 15dp in LazyColumn, hub rows use internal spacer to mimic this visual gap + row height difference
        val bottomSpacerHeight = if (isTopNav && isLastRow) 0.dp else 17.dp
        Spacer(modifier = Modifier.height(bottomSpacerHeight))
    }
}
