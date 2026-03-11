package com.lumera.app.ui.details

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import coil.compose.AsyncImage
import coil.compose.SubcomposeAsyncImage
import com.lumera.app.domain.AddonSubtitle
import com.lumera.app.domain.episodePlaybackId
import com.lumera.app.domain.episodeDisplayTitle
import com.lumera.app.data.model.stremio.MetaVideo
import com.lumera.app.data.model.stremio.Stream
import com.lumera.app.R

@Composable
fun DetailsScreen(
    type: String,
    id: String,
    resumePlaybackHint: String? = null,
    autoSelectSource: Boolean = false,
    rememberSourceSelection: Boolean = true,
    onPlayClick: (String, String, String, String, String, String, Stream, List<AddonSubtitle>, List<Stream>, List<MetaVideo>) -> Unit,
    viewModel: DetailsViewModel = hiltViewModel()
) {
    LaunchedEffect(type, id) { viewModel.loadDetails(type, id) }

    val state by viewModel.state.collectAsState()
    val movie = state.meta
    val isCurrentMovie = movie?.id == id && movie.type == type
    val showMovieContent = movie != null && isCurrentMovie && !state.isLoading
    val sidebarState = if (isCurrentMovie) state.sidebarState else SidebarState.Closed

    val accentColor = MaterialTheme.colorScheme.primary
    val bg = MaterialTheme.colorScheme.background
    val textColor = MaterialTheme.colorScheme.onBackground
    val lifecycleOwner = LocalLifecycleOwner.current

    var pendingPlaybackId by remember(type, id) { mutableStateOf(id) }
    var pendingPlaybackType by remember(type, id) { mutableStateOf(type) }
    var pendingPlaybackTitle by remember(type, id) { mutableStateOf("") }
    val autoPlayStream = state.autoPlayStream
    val addonSubtitles = state.addonSubtitles
    val availableStreams = state.availableStreams

    LaunchedEffect(autoPlayStream) {
        val stream = autoPlayStream ?: return@LaunchedEffect
        viewModel.commitClearProgress()
        val urlToPlay = resolvePlayableUrl(stream)
        if (!urlToPlay.isNullOrEmpty()) {
            val playbackId = pendingPlaybackId.ifBlank { movie?.id ?: id }
            val playbackType = pendingPlaybackType.ifBlank { movie?.type ?: type }
            val playbackTitle = pendingPlaybackTitle.ifBlank { movie?.name ?: "" }
            onPlayClick(
                urlToPlay,
                playbackId,
                playbackType,
                playbackTitle,
                movie?.name ?: "",
                movie?.logo ?: "",
                stream,
                addonSubtitles,
                availableStreams,
                movie?.videos ?: emptyList()
            )
        }
        viewModel.consumeAutoPlayStream()
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.refreshResumeState()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            viewModel.commitClearProgress()
        }
    }

    BackHandler(enabled = sidebarState !is SidebarState.Closed) {
        viewModel.goBackInSidebar()
    }

    val firstButtonFocusRequester = remember { FocusRequester() }

    LaunchedEffect(showMovieContent) {
        if (showMovieContent) {
            kotlinx.coroutines.delay(200)
            runCatching { firstButtonFocusRequester.requestFocus() }
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(bg)) {
        if (!showMovieContent) {
            CircularProgressIndicator(modifier = Modifier.align(Alignment.Center), color = accentColor)
        } else {
            val currentMovie = requireNotNull(movie)
            val bgImage = currentMovie.background ?: currentMovie.poster
            AsyncImage(
                model = bgImage,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize().alpha(0.6f)
            )

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.horizontalGradient(
                            colors = listOf(bg, Color.Transparent),
                            startX = 0f,
                            endX = 1500f
                        )
                    )
            )

            Column(
                modifier = Modifier
                    .fillMaxHeight()
                    .width(600.dp)
                    .padding(48.dp),
                verticalArrangement = Arrangement.Center
            ) {
                val titleStyle = MaterialTheme.typography.displaySmall.copy(
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 32.sp,
                    lineHeight = 34.sp
                )

                if (!currentMovie.logo.isNullOrEmpty()) {
                    SubcomposeAsyncImage(
                        model = currentMovie.logo,
                        contentDescription = currentMovie.name,
                        contentScale = ContentScale.Fit,
                        alignment = Alignment.BottomStart,
                        modifier = Modifier
                            .widthIn(max = 450.dp)
                            .heightIn(max = 90.dp),
                        error = {
                            Text(
                                text = currentMovie.name,
                                style = titleStyle,
                                color = textColor,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                softWrap = true
                            )
                        }
                    )
                } else {
                    Text(
                        text = currentMovie.name,
                        style = titleStyle,
                        color = textColor,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        softWrap = true
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                val typeLabel = currentMovie.type.replaceFirstChar { it.uppercase() }
                val genreLabel = currentMovie.genres
                    ?.firstOrNull()
                    ?.replaceFirstChar { it.uppercase() }
                    ?: "Unknown"
                val yearLabel = extractPrimaryYear(currentMovie.releaseInfo)

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = typeLabel,
                        style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
                        color = textColor.copy(alpha = 0.95f)
                    )
                    MetaDot(textColor)
                    Text(
                        text = genreLabel,
                        style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
                        color = textColor.copy(alpha = 0.95f)
                    )
                    MetaDot(textColor)
                    Text(
                        text = yearLabel,
                        style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
                        color = textColor.copy(alpha = 0.95f)
                    )

                    currentMovie.imdbRating?.takeIf { it.isNotBlank() }?.let { rating ->
                        Spacer(modifier = Modifier.width(4.dp))
                        ImdbBadge()
                        Text(
                            text = rating,
                            style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
                            color = textColor.copy(alpha = 0.95f)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))
                Text(
                    text = currentMovie.description ?: "",
                    style = MaterialTheme.typography.bodyLarge,
                    color = textColor,
                    maxLines = 5
                )
                Spacer(modifier = Modifier.height(32.dp))

                val firstEpisode = remember(currentMovie.id, currentMovie.videos) {
                    findFirstEpisode(currentMovie.videos)
                }
                val hintedResumePlaybackId = remember(type, id, resumePlaybackHint) {
                    when (type) {
                        "series" -> resumePlaybackHint?.takeIf { playbackIdBelongsToSeries(id, it) }
                        else -> resumePlaybackHint?.takeIf { it == id }
                    }
                }
                val resumePlaybackId = if (state.progressCleared) null
                    else hintedResumePlaybackId ?: state.resumePlaybackId
                val resumeEpisode = remember(currentMovie.id, currentMovie.videos, resumePlaybackId) {
                    if (type == "series") {
                        resolveEpisodeForPlaybackId(currentMovie.id, currentMovie.videos, resumePlaybackId)
                    } else {
                        null
                    }
                }
                val parsedResumeSeasonEpisode = remember(resumePlaybackId) {
                    parseSeasonEpisodeFromPlaybackId(resumePlaybackId)
                }
                val firstEpisodeSeason = firstEpisode?.season?.takeIf { it > 0 } ?: 1
                val firstEpisodeNumber = firstEpisode?.episode?.takeIf { it > 0 } ?: 1

                if (type == "series") {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        val playLabel = if (resumePlaybackId != null) {
                            val resumeSeason = resumeEpisode?.season?.takeIf { it > 0 } ?: parsedResumeSeasonEpisode?.first
                            val resumeNumber = resumeEpisode?.episode?.takeIf { it > 0 } ?: parsedResumeSeasonEpisode?.second
                            if (resumeSeason != null && resumeNumber != null) {
                                "Resume S${resumeSeason} E${resumeNumber}"
                            } else {
                                "Resume"
                            }
                        } else {
                            "Play S${firstEpisodeSeason} E${firstEpisodeNumber}"
                        }

                        VoidActionButton(
                            label = playLabel,
                            icon = Icons.Default.PlayArrow,
                            modifier = Modifier.focusRequester(firstButtonFocusRequester),
                            onClick = {
                                val epId = resumePlaybackId ?: episodePlaybackId(id, firstEpisode ?: return@VoidActionButton)
                                val epTitle = when {
                                    resumePlaybackId != null && resumeEpisode != null -> episodeDisplayTitle(resumeEpisode)
                                    resumePlaybackId != null && parsedResumeSeasonEpisode != null ->
                                        "S${parsedResumeSeasonEpisode.first}:E${parsedResumeSeasonEpisode.second} - ${currentMovie.name}"
                                    resumePlaybackId != null -> currentMovie.name
                                    else -> episodeDisplayTitle(firstEpisode ?: return@VoidActionButton)
                                }
                                pendingPlaybackId = epId
                                pendingPlaybackType = type
                                pendingPlaybackTitle = epTitle
                                viewModel.loadStreams(type, epId, epTitle, autoSelectSource = autoSelectSource, rememberSourceSelection = rememberSourceSelection)
                            }
                        )

                        VoidActionButton(
                            label = "More Episodes",
                            icon = Icons.Default.List,
                            onClick = { viewModel.openEpisodes() }
                        )

                        if (resumePlaybackId != null || state.progressCleared) {
                            VoidActionButton(
                                label = if (state.progressCleared) "Undo" else "Clear Progress",
                                icon = if (state.progressCleared) Icons.Default.Refresh else Icons.Default.Close,
                                onClick = {
                                    if (state.progressCleared) {
                                        viewModel.undoClearProgress()
                                    } else {
                                        viewModel.clearProgress()
                                    }
                                }
                            )
                        }
                    }
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        VoidActionButton(
                            label = if (resumePlaybackId != null) "Resume" else "Play Movie",
                            icon = Icons.Default.PlayArrow,
                            modifier = Modifier.focusRequester(firstButtonFocusRequester),
                            onClick = {
                                pendingPlaybackId = id
                                pendingPlaybackType = type
                                pendingPlaybackTitle = currentMovie.name
                                viewModel.loadStreams(type, id, currentMovie.name, autoSelectSource = autoSelectSource, rememberSourceSelection = rememberSourceSelection)
                            }
                        )

                        if (resumePlaybackId != null || state.progressCleared) {
                            VoidActionButton(
                                label = if (state.progressCleared) "Undo" else "Clear Progress",
                                icon = if (state.progressCleared) Icons.Default.Refresh else Icons.Default.Close,
                                onClick = {
                                    if (state.progressCleared) {
                                        viewModel.undoClearProgress()
                                    } else {
                                        viewModel.clearProgress()
                                    }
                                }
                            )
                        }

                        // Works around a Compose focus-tree bug where requestFocus()
                        // silently fails when a container has a single focusable child.
                        if (resumePlaybackId == null && !state.progressCleared) {
                            Spacer(modifier = Modifier
                                .size(0.dp)
                                .onFocusChanged {
                                    if (it.isFocused) firstButtonFocusRequester.requestFocus()
                                }
                                .focusable()
                            )
                        }
                    }
                }
            }
        }

        GlassSidebar(
            state = sidebarState,
            onDismiss = { viewModel.closeSidebar() },
            onBack = { viewModel.goBackInSidebar() },
            onEpisodeSelected = { episode ->
                val epId = episodePlaybackId(id, episode)
                val epTitle = episodeDisplayTitle(episode)
                pendingPlaybackId = epId
                pendingPlaybackType = type
                pendingPlaybackTitle = epTitle
                viewModel.loadStreams(type, epId, epTitle, autoSelectSource = autoSelectSource, rememberSourceSelection = rememberSourceSelection)
            },
            onSourceSelected = { stream ->
                viewModel.closeSidebar()
                val playbackId = pendingPlaybackId.ifBlank { movie?.id ?: id }
                val urlToPlay = resolvePlayableUrl(stream)
                if (!urlToPlay.isNullOrEmpty()) {
                    val playbackType = pendingPlaybackType.ifBlank { movie?.type ?: type }
                    val playbackTitle = pendingPlaybackTitle.ifBlank { movie?.name ?: "" }
                    onPlayClick(
                        urlToPlay,
                        playbackId,
                        playbackType,
                        playbackTitle,
                        movie?.name ?: "",
                        movie?.logo ?: "",
                        stream,
                        addonSubtitles,
                        availableStreams,
                        movie?.videos ?: emptyList()
                    )
                }
            }
        )

        // Centered loading spinner for auto-resolve paths (remembered source, auto-select)
        if (state.isLoadingStreams && sidebarState is SidebarState.Closed) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = accentColor)
            }
        }
    }
}

@Composable
private fun VoidActionButton(
    label: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    val scale by animateFloatAsState(if (isFocused) 1.05f else 1f, label = "btnScale")
    val accentColor = MaterialTheme.colorScheme.primary

    Row(
        modifier = modifier
            .height(40.dp)
            .scale(scale)
            .clip(RoundedCornerShape(8.dp))
            .background(Color.White.copy(0.07f))
            .border(
                if (isFocused) 2.dp else 1.dp,
                if (isFocused) accentColor else Color.White.copy(0.15f),
                RoundedCornerShape(8.dp)
            )
            .clickable(interactionSource = interactionSource, indication = null) { onClick() }
            .focusable(interactionSource = interactionSource)
            .padding(horizontal = 14.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = accentColor,
            modifier = Modifier.size(18.dp)
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            label.uppercase(),
            color = if (isFocused) accentColor else Color.White.copy(0.8f),
            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun MetaDot(textColor: Color) {
    Text(
        text = ".",
        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
        color = textColor.copy(alpha = 0.55f)
    )
}

@Composable
private fun ImdbBadge() {
    Image(
        painter = painterResource(id = R.drawable.imdb_logo),
        contentDescription = "IMDb",
        modifier = Modifier.height(20.dp)
    )
}

private fun extractPrimaryYear(releaseInfo: String?): String {
    if (releaseInfo.isNullOrBlank()) return "----"
    return Regex("\\d{4}").find(releaseInfo)?.value ?: releaseInfo.take(4)
}

private fun findFirstEpisode(videos: List<MetaVideo>?): MetaVideo? {
    if (videos.isNullOrEmpty()) return null

    val numbered = videos.filter { it.season > 0 && it.episode > 0 }
    val candidates = if (numbered.isNotEmpty()) numbered else videos
    return candidates.minWithOrNull(
        compareBy<MetaVideo>({ if (it.season > 0) it.season else Int.MAX_VALUE })
            .thenBy { if (it.episode > 0) it.episode else Int.MAX_VALUE }
            .thenBy { it.title }
    )
}


private fun resolveEpisodeForPlaybackId(
    seriesId: String,
    videos: List<MetaVideo>?,
    playbackId: String?
): MetaVideo? {
    val targetId = playbackId ?: return null
    val episodeList = videos ?: return null
    return episodeList.firstOrNull { episodePlaybackId(seriesId, it) == targetId }
}

private fun parseSeasonEpisodeFromPlaybackId(playbackId: String?): Pair<Int, Int>? {
    val id = playbackId ?: return null
    val parts = id.split(":")
    if (parts.size < 3) return null
    val season = parts[parts.lastIndex - 1].toIntOrNull() ?: return null
    val episode = parts.last().toIntOrNull() ?: return null
    if (season <= 0 || episode <= 0) return null
    return season to episode
}

private fun playbackIdBelongsToSeries(seriesId: String, playbackId: String): Boolean {
    val parts = playbackId.split(":")
    if (parts.size < 3) return playbackId == seriesId
    val season = parts[parts.lastIndex - 1].toIntOrNull()
    val episode = parts.last().toIntOrNull()
    if (season == null || episode == null) return playbackId == seriesId
    return parts.dropLast(2).joinToString(":") == seriesId
}

private val TORRENT_TRACKERS = listOf(
    // HTTP trackers (TCP — work even when UDP is blocked)
    "http://tracker.opentrackr.org:1337/announce",
    "http://tracker.openbittorrent.com:80/announce",
    "http://tracker1.bt.moack.co.kr:80/announce",
    "http://tracker.gbitt.info:80/announce",
    // UDP trackers (fallback)
    "udp://tracker.opentrackr.org:1337/announce",
    "udp://open.stealth.si:80/announce",
    "udp://tracker.openbittorrent.com:6969/announce",
    "udp://exodus.desync.com:6969/announce"
)

private fun resolvePlayableUrl(stream: com.lumera.app.data.model.stremio.Stream): String? {
    if (!stream.url.isNullOrEmpty()) return stream.url
    if (!stream.infoHash.isNullOrEmpty()) {
        val trackerParams = TORRENT_TRACKERS.joinToString("") {
            "&tr=${java.net.URLEncoder.encode(it, "UTF-8")}"
        }
        return "magnet:?xt=urn:btih:${stream.infoHash}&dn=Video${trackerParams}"
    }
    return null
}
