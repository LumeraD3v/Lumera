package com.lumera.app.ui.details

import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.*
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.*
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.lumera.app.data.model.stremio.MetaVideo
import com.lumera.app.data.model.stremio.Stream
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// --- STATE & UTILS ---

sealed class SidebarState {
    data object Closed : SidebarState()
    data class Episodes(val videos: List<MetaVideo>) : SidebarState()
    data class Sources(val streamTitle: String, val streams: List<Stream>?, val selectedStreamId: String? = null) : SidebarState()
}

// Reusable modifier for D-Pad navigation (Traps Left, Handles Back)
private fun Modifier.dpadNavigation(onBack: () -> Unit) = this.onPreviewKeyEvent {
    when {
        it.key == Key.DirectionLeft && it.type == KeyEventType.KeyDown -> true
        it.key == Key.Back && it.type == KeyEventType.KeyUp -> { onBack(); true }
        else -> false
    }
}

// --- MAIN SIDEBAR ---

@Composable
fun GlassSidebarScaffold(
    visible: Boolean,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    panelWidth: Dp = 500.dp,
    panelPadding: PaddingValues = PaddingValues(start = 24.dp, top = 24.dp, end = 24.dp, bottom = 0.dp),
    overlayAlpha: Float = 0.4f,
    enter: EnterTransition = slideInHorizontally { it },
    exit: ExitTransition = slideOutHorizontally { it },
    content: @Composable ColumnScope.() -> Unit
) {
    if (visible) {
        Box(
            modifier = modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = overlayAlpha))
                .clickable(onClick = onDismiss)
        )
    }

    AnimatedVisibility(
        visible = visible,
        enter = enter,
        exit = exit,
        modifier = modifier.fillMaxSize()
    ) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.CenterEnd) {
            Column(
                Modifier
                    .fillMaxHeight()
                    .width(panelWidth)
                    .background(Brush.horizontalGradient(listOf(Color(0xCC000000), Color(0xFA000000))))
                    .pointerInput(Unit) { detectTapGestures { } }
                    .padding(panelPadding)
            ) {
                content()
            }
        }
    }
}

@Composable
fun GlassSidebar(
    state: SidebarState,
    currentEpisodeId: String? = null,
    onEpisodeSelected: (MetaVideo) -> Unit,
    onSourceSelected: (Stream) -> Unit,
    onBack: () -> Unit,
    onDismiss: () -> Unit
) {
    val isVisible = state !is SidebarState.Closed
    val focusRequester = remember { FocusRequester() }
    val episodesListState = rememberLazyListState()
    var savedSeason by remember { mutableStateOf<Int?>(null) }
    var savedIndex by remember { mutableIntStateOf(0) }

    // When sidebar opens with episodes, auto-navigate to the currently playing episode
    LaunchedEffect(state, currentEpisodeId) {
        if (state is SidebarState.Episodes && currentEpisodeId != null) {
            val seasonMap = state.videos.filter { it.season > 0 }.groupBy { it.season }
            for ((season, eps) in seasonMap) {
                val idx = eps.indexOfFirst { ep ->
                    ep.id == currentEpisodeId || currentEpisodeId.endsWith(":${ep.season}:${ep.episode}")
                }
                if (idx >= 0) {
                    savedSeason = season
                    savedIndex = idx
                    break
                }
            }
        }
    }

    LaunchedEffect(state) {
        if (isVisible) { delay(200); runCatching { focusRequester.requestFocus() } }
    }

    GlassSidebarScaffold(
        visible = isVisible,
        onDismiss = onDismiss
    ) {
        Crossfade(targetState = state, label = "Sidebar") { current ->
            when (current) {
                is SidebarState.Episodes -> EpisodesContent(
                    videos = current.videos,
                    listState = episodesListState, // Pass the hoisted state
                    savedSeason = savedSeason,
                    savedIndex = savedIndex,
                    currentEpisodeId = currentEpisodeId,
                    focusRequester = focusRequester,
                    onEpisodeClick = { ep, s, i -> savedSeason = s; savedIndex = i; onEpisodeSelected(ep) },
                    onSeasonChange = { savedSeason = it },
                    onDismiss = onDismiss
                )
                is SidebarState.Sources -> SourcesContent(
                    title = current.streamTitle,
                    streams = current.streams,
                    selectedStreamId = current.selectedStreamId,
                    focusRequester = focusRequester,
                    onSourceClick = onSourceSelected,
                    onBack = onBack
                )
                else -> {}
                }
            }
        }
}

// --- CONTENT: SEASONS/EPISODES ---

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun EpisodesContent(
    videos: List<MetaVideo>,
    listState: LazyListState, // Received from parent
    savedSeason: Int?,
    savedIndex: Int,
    currentEpisodeId: String? = null,
    focusRequester: FocusRequester,
    onEpisodeClick: (MetaVideo, Int, Int) -> Unit,
    onSeasonChange: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    val seasons = remember(videos) { videos.filter { it.season > 0 }.groupBy { it.season }.toSortedMap() }
    var selectedSeason by remember(savedSeason) { mutableIntStateOf(savedSeason ?: seasons.keys.minOrNull() ?: 1) }
    val episodes = seasons[selectedSeason] ?: emptyList()

    val tabRequester = remember { FocusRequester() }
    val scope = rememberCoroutineScope() // Needed for manual scrolling

    LaunchedEffect(selectedSeason) { onSeasonChange(selectedSeason) }

    // Scroll to the current episode when the sidebar opens
    LaunchedEffect(savedIndex, selectedSeason) {
        if (episodes.isNotEmpty() && savedIndex > 0 && savedIndex in episodes.indices) {
            runCatching { listState.scrollToItem(savedIndex) }
        }
    }

    Column {
        Text("More Episodes", style = MaterialTheme.typography.headlineSmall, color = Color.White, modifier = Modifier.padding(bottom = 16.dp))

        if (seasons.isNotEmpty()) {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(bottom = 8.dp)) {
                itemsIndexed(seasons.keys.toList()) { idx, num ->
                    SeasonTab(
                        number = num,
                        isSelected = num == selectedSeason,
                        modifier = Modifier
                            .then(if (idx == 0) Modifier.onPreviewKeyEvent { it.key == Key.DirectionLeft && it.type == KeyEventType.KeyDown } else Modifier)
                            .then(if (num == selectedSeason) Modifier.focusRequester(tabRequester) else Modifier)
                            .focusProperties { up = FocusRequester.Cancel },
                        onClick = {
                            selectedSeason = num
                            // Only reset scroll to top when manually changing seasons
                            scope.launch { listState.scrollToItem(0) }
                        }
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
        }

        LazyColumn(
            state = listState, // Using the hoisted state
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.dpadNavigation(onDismiss)
        ) {
            if (episodes.isEmpty()) item { Text("No episodes found.", color = Color.Gray) }
            else {
                itemsIndexed(episodes) { index, ep ->
                    // Focus Logic: Attach requester to saved index; Attach 'Up' navigation to first index
                    val isTarget = index == (if (savedIndex in episodes.indices) savedIndex else 0)
                    val mod = Modifier
                        .then(if (isTarget) Modifier.focusRequester(focusRequester) else Modifier)
                        .then(if (index == 0) Modifier.focusProperties { up = if (seasons.isNotEmpty()) tabRequester else FocusRequester.Cancel } else Modifier)

                    val isCurrentEpisode = currentEpisodeId != null && (
                        ep.id == currentEpisodeId ||
                        currentEpisodeId.endsWith(":${ep.season}:${ep.episode}")
                    )
                    EpisodeItem(
                        episode = ep,
                        isPlaying = isCurrentEpisode,
                        modifier = mod
                    ) { onEpisodeClick(ep, selectedSeason, index) }
                }
            }
        }
    }
}

// --- CONTENT: SOURCES ---

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun SourcesContent(
    title: String,
    streams: List<Stream>?,
    selectedStreamId: String? = null,
    focusRequester: FocusRequester,
    onSourceClick: (Stream) -> Unit,
    onBack: () -> Unit
) {
    val actualStreams = streams ?: emptyList()
    var filter by remember { mutableStateOf("All Addons") }

    val addonNames = remember(actualStreams) {
        listOf("All Addons") + actualStreams.mapNotNull { it.name?.substringAfter("[")?.substringBefore("]") }.distinct()
    }

    val filtered = remember(actualStreams, filter) {
        actualStreams.filter { filter == "All Addons" || (it.name?.contains(filter) == true) }
    }

    val selectedIndex = remember(filtered, selectedStreamId) {
        if (selectedStreamId == null) 0
        else {
            val idx = filtered.indexOfFirst { s ->
                (s.addonTransportUrl ?: s.url) == selectedStreamId
            }
            if (idx >= 0) idx else 0
        }
    }

    val listState = rememberLazyListState()

    LaunchedEffect(selectedIndex, filtered) {
        if (filtered.isNotEmpty() && selectedIndex > 0) {
            runCatching { listState.scrollToItem(selectedIndex) }
        }
    }

    Column {
        Text("Select Source", style = MaterialTheme.typography.titleLarge, color = Color.White)
        Text(title, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(16.dp))

        if (streams == null) {
            Box(
                Modifier.fillMaxSize()
                    .focusRequester(focusRequester)
                    .dpadNavigation(onBack)
                    .focusable(),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.height(16.dp))
                    Text("Loading streams...", color = Color.LightGray)
                }
            }
        } else {
            FilterDropdown(
                currentValue = filter,
                options = addonNames,
                modifier = Modifier
                    .focusProperties { up = FocusRequester.Cancel }
                    .onPreviewKeyEvent { it.key == Key.DirectionLeft && it.type == KeyEventType.KeyDown },
                onSelect = { filter = it }
            )
            Spacer(Modifier.height(16.dp))
            LazyColumn(
                state = listState,
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.dpadNavigation(onBack)
            ) {
                if (filtered.isEmpty()) item {
                    Box(Modifier.fillMaxWidth().padding(top = 32.dp), contentAlignment = Alignment.Center) {
                        Text("No streams found.", color = Color.Gray, style = MaterialTheme.typography.bodyLarge)
                    }
                } else {
                    itemsIndexed(filtered) { index, s ->
                        RawSourceItem(
                            stream = s,
                            isPlaying = index == selectedIndex && selectedStreamId != null,
                            modifier = if (index == selectedIndex) Modifier.focusRequester(focusRequester) else Modifier
                        ) { onSourceClick(s) }
                    }
                }
            }
        }
    }
}

// --- ITEMS & UI COMPONENTS ---

@Composable
fun FilterDropdown(currentValue: String, options: List<String>, modifier: Modifier, onSelect: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    var isFocused by remember { mutableStateOf(false) }

    // Size calculation for symmetry
    var rowWidth by remember { mutableStateOf(0.dp) }
    val density = LocalDensity.current

    val primary = MaterialTheme.colorScheme.primary

    Box(modifier) {
        Button(
            onClick = { expanded = true },
            colors = ButtonDefaults.buttonColors(containerColor = if (isFocused) Color.White.copy(0.1f) else Color.White.copy(0.05f)),
            modifier = Modifier
                .fillMaxWidth()
                .onFocusChanged { isFocused = it.isFocused }
                .onSizeChanged {
                    // Capture width of the button to apply to dropdown
                    rowWidth = with(density) { it.width.toDp() }
                }
                .border(if (isFocused) 2.dp else 0.dp, if (isFocused) primary else Color.Transparent, RoundedCornerShape(8.dp)),
            shape = RoundedCornerShape(8.dp)
        ) {
            Text(currentValue, color = if(isFocused) Color.White else Color.LightGray, modifier = Modifier.weight(1f))
            Icon(Icons.Default.ArrowDropDown, null, tint = if(isFocused) primary else Color.Gray)
        }

        // HACK: DropdownMenu in standard M3 doesn't support 'containerColor' param easily.
        // It uses the internal Surface color. To get transparency, we override the theme
        // strictly for this block to force the Surface to be transparent.
        MaterialTheme(
            colorScheme = MaterialTheme.colorScheme.copy(surface = Color.Transparent),
            shapes = MaterialTheme.shapes.copy(extraSmall = RoundedCornerShape(8.dp))
        ) {
            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
                modifier = Modifier
                    .width(rowWidth) // Enforce symmetry
                    // Apply the glass gradient here manually
                    .background(
                        brush = Brush.horizontalGradient(listOf(Color(0xCC000000), Color(0xFA000000))),
                        shape = RoundedCornerShape(8.dp)
                    )
                    // Clip ensures the background respects the rounded shape
                    .clip(RoundedCornerShape(8.dp))
                    .border(1.dp, Color.Gray.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
            ) {
                // Calculate which item should be focused based on currentValue
                val selectedIndex = remember(options, currentValue) { options.indexOf(currentValue).coerceAtLeast(0) }

                // Create a FocusRequester for every item so we can target the specific one
                val itemRequesters = remember(options.size) { List(options.size) { FocusRequester() } }

                // Trigger focus on the selected item when the menu opens
                LaunchedEffect(Unit) {
                    delay(100)
                    runCatching { itemRequesters[selectedIndex].requestFocus() }
                }

                options.forEachIndexed { index, opt ->
                    var isItemFocused by remember { mutableStateOf(false) }

                    DropdownMenuItem(
                        text = {
                            Text(
                                text = opt,
                                color = if (isItemFocused) primary else Color.White
                            )
                        },
                        onClick = { onSelect(opt); expanded = false },
                        modifier = Modifier
                            .focusRequester(itemRequesters[index])
                            .onFocusChanged { isItemFocused = it.isFocused }
                    )
                }
            }
        }
    }
}

@Composable
fun SeasonTab(number: Int, isSelected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    var isFocused by remember { mutableStateOf(false) }
    val primary = MaterialTheme.colorScheme.primary
    Box(
        modifier.clip(RoundedCornerShape(4.dp))
            .background(if (isFocused) Color.White else if (isSelected) primary else Color.DarkGray)
            .onFocusChanged { isFocused = it.isFocused }
            .clickable(onClick = onClick).focusable().padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Text("Season $number", color = if (isSelected || isFocused) Color.Black else Color.White, style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
fun EpisodeItem(episode: MetaVideo, isPlaying: Boolean = false, modifier: Modifier = Modifier, onClick: () -> Unit) {
    var isFocused by remember { mutableStateOf(false) }
    val primary = MaterialTheme.colorScheme.primary
    val title = if (episode.title.isBlank() || episode.title == "Episode") "Episode ${episode.episode}" else episode.title

    Row(
        modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp))
            .background(if (isFocused) Color.White.copy(0.1f) else Color.Transparent)
            .border(if (isFocused) 3.dp else 0.dp, if (isFocused) primary else Color.Transparent, RoundedCornerShape(8.dp))
            .onFocusChanged { isFocused = it.isFocused }.clickable(onClick = onClick).padding(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (episode.thumbnail != null) {
            AsyncImage(episode.thumbnail, null, Modifier.width(120.dp).aspectRatio(16f/9f).clip(RoundedCornerShape(4.dp)), contentScale = ContentScale.Crop)
            Spacer(Modifier.width(12.dp))
        }
        Text("${episode.episode}. $title", color = if (isFocused) Color.White else Color.LightGray, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
        if (isPlaying) {
            Text(
                "Playing",
                style = MaterialTheme.typography.labelSmall,
                color = primary,
                modifier = Modifier.padding(start = 8.dp)
            )
        }
    }
}

@Composable
fun RawSourceItem(stream: Stream, isPlaying: Boolean = false, modifier: Modifier = Modifier, onClick: () -> Unit) {
    var isFocused by remember { mutableStateOf(false) }
    val primary = MaterialTheme.colorScheme.primary
    val mainText = stream.description ?: stream.title ?: stream.name ?: "Unknown"
    val subText = stream.name ?: ""

    Card(
        colors = CardDefaults.cardColors(containerColor = if (isFocused) Color.White.copy(0.1f) else Color.White.copy(0.05f)),
        modifier = modifier.fillMaxWidth().onFocusChanged { isFocused = it.isFocused }
            .border(if (isFocused) 3.dp else 0.dp, if (isFocused) primary else Color.Transparent, RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(mainText, style = MaterialTheme.typography.bodyLarge, color = if (isFocused) Color.White else Color.LightGray, modifier = Modifier.weight(1f))
                if (isPlaying) {
                    Text(
                        "Playing",
                        style = MaterialTheme.typography.labelSmall,
                        color = primary,
                        modifier = Modifier.padding(start = 8.dp)
                    )
                }
            }
            if (subText.isNotEmpty() && subText != mainText) {
                Text(subText, style = MaterialTheme.typography.labelSmall, color = if (isFocused) primary else Color.Gray, modifier = Modifier.padding(top = 4.dp))
            }
        }
    }
}
