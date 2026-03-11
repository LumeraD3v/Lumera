package com.lumera.app.ui.settings

import androidx.activity.compose.BackHandler
import androidx.annotation.DrawableRes
import androidx.compose.animation.*
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.*
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.lumera.app.R
import com.lumera.app.data.model.ProfileEntity
import com.lumera.app.ui.addons.AddonsScreen
import com.lumera.app.ui.theme.ThemeManager
import com.lumera.app.ui.utils.rememberLastFocus
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

import androidx.compose.ui.ExperimentalComposeUiApi

enum class SettingsSection(val label: String, @DrawableRes val iconRes: Int) {
    Personalization("Personalization", R.drawable.personalization_icon),
    Theme("Theme", R.drawable.palette_icon),
    Dashboard("Home Screen", R.drawable.home_icon),
    Playback("Playback", R.drawable.playback_icon),
    SourcePreferences("Source Preferences", R.drawable.source_preferences_icon),
    Addons("Addons", R.drawable.puzzle_icon),
    Integrations("Integrations", R.drawable.integrations_icon),
    About("About", R.drawable.info_icon)
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun SettingsScreen(
    currentProfile: ProfileEntity?,
    onBack: () -> Unit,
    entryRequester: FocusRequester,
    drawerRequester: FocusRequester,
    onDashboardChanged: () -> Unit = {},
    onContentFocusChanged: (Boolean) -> Unit = {},
    viewModel: SettingsViewModel = hiltViewModel()
) {
    var selectedSection by remember { mutableStateOf(SettingsSection.Personalization) }
    var displayedSection by remember { mutableStateOf(SettingsSection.Personalization) }
    var isContentFocused by remember { mutableStateOf(false) }
    var isTransitioning by remember { mutableStateOf(false) } // LOCK: prevent re-entry

    val sidebarListRequester = remember { FocusRequester() }
    val contentPaneRequester = remember { FocusRequester() }
    val itemRequesters = remember { SettingsSection.entries.associateWith { FocusRequester() } }

    val scope = rememberCoroutineScope()

    // DEBOUNCE LOGIC (Only for Scrolling)
    LaunchedEffect(selectedSection) {
        delay(300)
        displayedSection = selectedSection
    }

    val isTopNav = currentProfile?.navPosition == "top"

    // SCREEN FOCUS TRACKING (Content + Sidebar)
    var isScreenFocused by remember { mutableStateOf(false) }

    // BACK LOGIC
    // Enabled when:
    // 1. Side Nav (Always)
    // 2. Top Nav AND Screen is Focused (Handle = Open Nav/Go Back)
    // Disabled when Top Nav AND Screen NOT Focused (Nav is focused) -> Let Nav handle Close.
    BackHandler(enabled = !isTopNav || isScreenFocused) {
        if (isContentFocused) {
            itemRequesters[selectedSection]?.requestFocus()
        } else {
            drawerRequester.requestFocus()
        }
    }
    
    // Animated padding values for smooth transition when nav mode changes
    val rowStartPadding by animateDpAsState(
        targetValue = if (isTopNav) 0.dp else 80.dp,
        animationSpec = androidx.compose.animation.core.tween(300),
        label = "rowStartPadding"
    )
    val colTopPadding by animateDpAsState(
        targetValue = if (isTopNav) 70.dp else 40.dp,
        animationSpec = androidx.compose.animation.core.tween(300),
        label = "colTopPadding"
    )
    val contentTopPadding by animateDpAsState(
        targetValue = if (isTopNav) 70.dp else 40.dp,
        animationSpec = androidx.compose.animation.core.tween(300),
        label = "contentTopPadding"
    )
    val colStartPadding by animateDpAsState(
        targetValue = if (isTopNav) 50.dp else 32.dp,
        animationSpec = androidx.compose.animation.core.tween(300),
        label = "colStartPadding"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .onFocusChanged { isScreenFocused = it.hasFocus }
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(start = rowStartPadding)
        ) {

        // --- LEFT SIDEBAR ---
        Column(
            modifier = Modifier
                .weight(0.28f)
                .fillMaxHeight()
                .padding(top = colTopPadding, start = colStartPadding, end = 16.dp)
                .onFocusChanged { if (it.hasFocus) { isContentFocused = false; onContentFocusChanged(false) } }
                .rememberLastFocus()
        ) {
            Text(
                "Settings",
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 20.sp
                ),
                color = Color.White,
                modifier = Modifier.padding(bottom = 32.dp, start = 16.dp)
            )

            Column(
                verticalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier
                    .focusRequester(sidebarListRequester)
                    .padding(top = 0.dp, bottom = 10.dp)
            ) {
                SettingsSection.entries.forEach { section ->
                    key(section) {
                        val isSelected = selectedSection == section

                        // FUNCTION: Enter Content Logic (with lock)
                        fun enterContent() {
                            if (isTransitioning) return // BLOCKED
                            isTransitioning = true
                            selectedSection = section
                            displayedSection = section
                            scope.launch {
                                delay(400) // Wait for animation
                                contentPaneRequester.requestFocus()
                                isTransitioning = false // UNLOCK
                            }
                        }

                        val isFirstSection = section == SettingsSection.entries.first()

                        val focusModifier = Modifier
                            .focusRequester(itemRequesters[section]!!)
                            .then(if (isSelected) Modifier.focusRequester(entryRequester) else Modifier)
                            .onPreviewKeyEvent {
                                if (it.type == KeyEventType.KeyDown) {
                                    when (it.key) {
                                        Key.DirectionLeft -> {
                                            if (!isTransitioning) {
                                                drawerRequester.requestFocus()
                                            }
                                            true
                                        }
                                        Key.Back -> {
                                            if (!isTransitioning) {
                                                drawerRequester.requestFocus()
                                                true
                                            } else false
                                        }
                                        Key.DirectionUp -> {
                                            // Up on first section -> go to drawer/topnav (ONLY in top nav mode)
                                            if (isFirstSection && !isTransitioning && isTopNav) {
                                                drawerRequester.requestFocus()
                                                true
                                            } else false
                                        }
                                        Key.DirectionRight, Key.DirectionCenter, Key.Enter -> {
                                            enterContent()
                                            true
                                        }
                                        else -> false
                                    }
                                } else false
                            }

                        SettingsSidebarItem(
                            label = section.label,
                            iconRes = section.iconRes,
                            isSelected = isSelected,
                            modifier = focusModifier,
                            onClick = { if (!isTransitioning) enterContent() }, // GATED
                            onFocus = { selectedSection = section }
                        )
                    }
                }
            }
        }

        // --- RIGHT CONTENT ---
        Box(
            modifier = Modifier
                .weight(0.72f) // Expanded content area
                .fillMaxHeight()
                .focusProperties {
                    if (isTopNav) {
                        up = FocusRequester.Cancel
                    }
                }
                .focusGroup()
                .focusRequester(contentPaneRequester)
                .rememberLastFocus()
                .onFocusChanged { if (it.hasFocus) { isContentFocused = true; onContentFocusChanged(true) } }
        ) {
            // INCREASED PADDING: Gutter 64dp (Sidebar ends at weight 0.28, this starts at 0 without extra padding logic, so we add start padding here)
            Column(modifier = Modifier.fillMaxSize().padding(top = contentTopPadding, start = 64.dp, end = 80.dp)) {
                AnimatedContent(
                    targetState = displayedSection,
                    transitionSpec = { fadeIn(tween(300)) togetherWith fadeOut(tween(300)) },
                    label = "SettingsContent"
                ) { target ->
                    when (target) {
                        SettingsSection.Personalization -> {
                            PersonalizationSettings(
                                currentProfile = currentProfile,
                                viewModel = viewModel,
                                onGoBack = { itemRequesters[selectedSection]?.requestFocus() }
                            )
                        }
                        SettingsSection.Theme -> {
                            ThemeSettings(
                                currentProfile = currentProfile,
                                onGoBack = { itemRequesters[selectedSection]?.requestFocus() },
                                isTopNav = isTopNav
                            )
                        }
                        SettingsSection.Dashboard -> {
                            DashboardEditorScreen(
                                onBack = {
                                    onDashboardChanged() // Invalidate home screen cache
                                    itemRequesters[selectedSection]?.requestFocus()
                                },
                                isTopNav = isTopNav,
                                currentProfile = currentProfile
                            )
                        }
                        SettingsSection.Playback -> {
                            PlaybackSettings(
                                currentProfile = currentProfile,
                                viewModel = viewModel,
                                onGoBack = { itemRequesters[selectedSection]?.requestFocus() }
                            )
                        }
                        SettingsSection.SourcePreferences -> {
                            SourcePreferencesSettings(
                                currentProfile = currentProfile,
                                viewModel = viewModel,
                                onGoBack = { itemRequesters[selectedSection]?.requestFocus() }
                            )
                        }
                        SettingsSection.Addons -> {
                            AddonsScreen(
                                onBack = { itemRequesters[selectedSection]?.requestFocus() },
                                isTopNav = isTopNav
                            )
                        }
                        SettingsSection.Integrations -> {
                            IntegrationsScreen(
                                onBack = { itemRequesters[selectedSection]?.requestFocus() }
                            )
                        }
                        SettingsSection.About -> {
                            AboutSettings(
                                onGoBack = { itemRequesters[selectedSection]?.requestFocus() }
                            )
                        }
                    }
                }
            }
        }
    }
    }
}

@Composable
fun SettingsSidebarItem(
    label: String,
    @DrawableRes iconRes: Int,
    isSelected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    onFocus: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()

    LaunchedEffect(isFocused) {
        if (isFocused) onFocus()
    }

    // Visual logic (like VoidTabBtn):
    // - If focused: full highlight (white text)
    // - If selected but not focused: partial highlight (white text)
    val showHighlight = isFocused || isSelected
    
    val contentColor by animateColorAsState(
        if (showHighlight) Color.White else Color.White.copy(0.4f), 
        label = "Content"
    )
    
    // Scale only when focused
    val scale by animateFloatAsState(if (isFocused) 1.05f else 1f)

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Start,
        modifier = Modifier
            .fillMaxWidth()
            .scale(scale)
            .then(modifier)
            .focusable(interactionSource = interactionSource)
            .clickable(interactionSource = interactionSource, indication = null) { onClick() }
            .padding(vertical = 12.dp, horizontal = 16.dp)
    ) {
        Icon(
            painter = painterResource(id = iconRes), 
            contentDescription = null, 
            tint = contentColor, 
            modifier = Modifier.size(20.dp)
        )
        Spacer(Modifier.width(16.dp))
        Text(
            text = label, 
            style = MaterialTheme.typography.bodyLarge.copy(
                fontSize = 16.sp,
                fontWeight = if (showHighlight) FontWeight.SemiBold else FontWeight.Normal
            ), 
            color = contentColor
        )
    }
}