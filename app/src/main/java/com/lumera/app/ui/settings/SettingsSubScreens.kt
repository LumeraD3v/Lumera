package com.lumera.app.ui.settings

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.hilt.navigation.compose.hiltViewModel
import com.lumera.app.BuildConfig
import com.lumera.app.data.model.ProfileEntity
import com.lumera.app.data.model.ThemeEntity
import com.lumera.app.data.update.AppUpdateManager
import com.lumera.app.data.update.UpdateState
import com.lumera.app.ui.details.GlassSidebarScaffold
import com.lumera.app.ui.theme.ThemeManager
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Theme settings section - allows selecting preset or creating custom themes
 */
@Composable
fun ThemeSettings(
    currentProfile: ProfileEntity?,
    themeManager: ThemeManager = hiltViewModel(),
    onGoBack: () -> Unit,
    isTopNav: Boolean = false
) {
    if (currentProfile == null) return
    
    var editingTheme by remember { mutableStateOf<ThemeEntity?>(null) }
    var isCreatingNew by remember { mutableStateOf(false) }
    
    // Show editor when creating or editing
    if (isCreatingNew || editingTheme != null) {
        ThemeEditorScreen(
            editingTheme = editingTheme,
            onSave = { name, primary, background ->
                if (editingTheme != null) {
                    // Update existing custom theme
                    themeManager.updateCustomTheme(
                        editingTheme!!.copy(
                            name = name,
                            primaryColor = primary,
                            backgroundColor = background
                        )
                    )
                } else {
                    // Create new custom theme and assign to profile
                    val newId = themeManager.createCustomTheme(name, primary, background)
                    themeManager.selectTheme(currentProfile.id, newId)
                }
                editingTheme = null
                isCreatingNew = false
                onGoBack() // Focus back to Sidebar as requested
            },
            onCancel = {
                editingTheme = null
                isCreatingNew = false
                onGoBack() // Focus back to Sidebar as requested
            }
        )
    } else {
        // Show theme selection
        ThemeScreen(
            currentProfile = currentProfile,
            themeManager = themeManager,
            onBack = onGoBack,
            onCreateCustom = { isCreatingNew = true },
            onEditTheme = { editingTheme = it },
            isTopNav = isTopNav
        )
    }
}

@Composable
fun PersonalizationSettings(
    currentProfile: ProfileEntity?,
    viewModel: SettingsViewModel,
    onGoBack: () -> Unit
) {
    if (currentProfile == null) return

    val roundCorners = currentProfile.roundCorners
    val hubRoundCorners = currentProfile.hubRoundCorners
    val navPos = currentProfile.navPosition

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.Top,
        horizontalAlignment = Alignment.Start
    ) {
        // HEADER
        Text(
            "Personalization",
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold, fontSize = 20.sp),
            color = Color.White
        )
        Text(
            "Customize your viewing experience.",
            style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp),
            color = Color.White.copy(0.6f),
            modifier = Modifier.padding(top = 4.dp)
        )

        Spacer(Modifier.height(15.dp))

        // POSTER CORNERS
        SettingRow("Poster Corners") {
            VoidSegmentedControl(
                options = listOf("Round" to true, "Sharp" to false),
                selectedOption = roundCorners,
                onOptionSelected = { viewModel.updateRoundCorners(currentProfile.id, it) },
                onBack = onGoBack,
                blockUp = true
            )
        }
        Spacer(Modifier.height(15.dp))
        Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(Color.White.copy(0.1f)))
        Spacer(Modifier.height(15.dp))

        // HUB SHAPE
        SettingRow("Hub Shape") {
            VoidSegmentedControl(
                options = listOf("Round" to true, "Sharp" to false),
                selectedOption = hubRoundCorners,
                onOptionSelected = { viewModel.updateHubRoundCorners(currentProfile.id, it) },
                onBack = onGoBack,
                blockUp = false
            )
        }
        Spacer(Modifier.height(15.dp))
        Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(Color.White.copy(0.1f)))
        Spacer(Modifier.height(15.dp))

        // CONTINUE WATCHING SHAPE
        SettingRow("Continue Watching") {
            VoidSegmentedControl(
                options = listOf("Poster" to "poster", "Landscape" to "landscape"),
                selectedOption = currentProfile.continueWatchingShape,
                onOptionSelected = { viewModel.updateContinueWatchingShape(currentProfile.id, it) },
                onBack = onGoBack,
                blockUp = false
            )
        }
        Spacer(Modifier.height(15.dp))
        Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(Color.White.copy(0.1f)))
        Spacer(Modifier.height(15.dp))

        // MENU POSITION
        SettingRow("Menu Position") {
            VoidSegmentedControl(
                options = listOf("Left" to "left", "Top" to "top"),
                selectedOption = navPos,
                onOptionSelected = { viewModel.updateNavPosition(currentProfile.id, it) },
                onBack = onGoBack,
                blockUp = false
            )
        }
        Spacer(Modifier.height(15.dp))
        Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(Color.White.copy(0.1f)))
        Spacer(Modifier.height(15.dp))

        // SPLASH SCREEN
        SettingRow("Splash Screen") {
            VoidSegmentedControl(
                options = listOf("On" to true, "Off" to false),
                selectedOption = currentProfile.splashEnabled,
                onOptionSelected = { viewModel.updateSplashEnabled(currentProfile.id, it) },
                onBack = onGoBack,
                blockUp = false
            )
        }
    }
}

/**
 * Playback settings section - tunneling, DV7 fallback, decoder priority, frame rate matching
 */
@Composable
fun PlaybackSettings(
    currentProfile: ProfileEntity?,
    viewModel: SettingsViewModel,
    onGoBack: () -> Unit
) {
    if (currentProfile == null) return

    var activeLanguageField by remember { mutableStateOf<LanguageField?>(null) }
    val sidebarFocusRequester = remember { FocusRequester() }
    val audioPrimaryFR = remember { FocusRequester() }
    val audioSecondaryFR = remember { FocusRequester() }
    val subtitlePrimaryFR = remember { FocusRequester() }
    val subtitleSecondaryFR = remember { FocusRequester() }
    var previousLanguageField by remember { mutableStateOf<LanguageField?>(null) }

    LaunchedEffect(activeLanguageField) {
        if (activeLanguageField != null) {
            previousLanguageField = activeLanguageField
            delay(200)
            runCatching { sidebarFocusRequester.requestFocus() }
        } else if (previousLanguageField != null) {
            delay(50)
            runCatching {
                when (previousLanguageField) {
                    LanguageField.AUDIO_PRIMARY -> audioPrimaryFR.requestFocus()
                    LanguageField.AUDIO_SECONDARY -> audioSecondaryFR.requestFocus()
                    LanguageField.SUBTITLE_PRIMARY -> subtitlePrimaryFR.requestFocus()
                    LanguageField.SUBTITLE_SECONDARY -> subtitleSecondaryFR.requestFocus()
                    null -> {}
                }
            }
            previousLanguageField = null
        }
    }

    var showSubtitleStyleSidebar by remember { mutableStateOf(false) }

    Box(Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.Top,
            horizontalAlignment = Alignment.Start
        ) {
            // HEADER
            Text(
                "Playback",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold, fontSize = 20.sp),
                color = Color.White
            )
            Text(
                "Configure video decoding and display settings.",
                style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp),
                color = Color.White.copy(0.6f),
                modifier = Modifier.padding(top = 4.dp)
            )

            Spacer(Modifier.height(15.dp))

            // TUNNELED PLAYBACK
            SettingToggleRow(
                label = "Tunneled Playback",
                subtitle = "Better 4K/HDR support, may not work on all devices",
                isChecked = currentProfile.tunnelingEnabled,
                onCheckedChange = { viewModel.updateTunnelingEnabled(currentProfile.id, it) },
                onBack = onGoBack,
                blockUp = true
            )

            // DV7 → HEVC FALLBACK
            SettingToggleRow(
                label = "Dolby Vision Profile 7 Fallback",
                subtitle = "Play DV7 content as HDR on compatible displays",
                isChecked = currentProfile.mapDV7ToHevc,
                onCheckedChange = { viewModel.updateMapDV7ToHevc(currentProfile.id, it) },
                onBack = onGoBack
            )

            // FRAME RATE MATCHING
            SettingToggleRow(
                label = "Frame Rate Matching",
                subtitle = "Match display refresh rate to video frame rate",
                isChecked = currentProfile.frameRateMatching,
                onCheckedChange = { viewModel.updateFrameRateMatching(currentProfile.id, it) },
                onBack = onGoBack
            )

            // DECODER PRIORITY
            SettingOptionRow(
                label = "Decoder Priority",
                options = listOf("Device" to 0, "Prefer Device" to 1, "Prefer App" to 2),
                selectedOption = currentProfile.decoderPriority,
                onOptionSelected = { viewModel.updateDecoderPriority(currentProfile.id, it) },
                onBack = onGoBack
            )

            // PLAYER PREFERENCE
            SettingOptionRow(
                label = "Player",
                options = listOf("Internal" to "internal", "External" to "external", "Ask" to "ask"),
                selectedOption = currentProfile.playerPreference,
                onOptionSelected = { viewModel.updatePlayerPreference(currentProfile.id, it) },
                onBack = onGoBack
            )

            // REMEMBER SOURCE SELECTION
            SettingToggleRow(
                label = "Remember Source Selection",
                subtitle = "Save your source choice and reuse it on resume",
                isChecked = currentProfile.rememberSourceSelection,
                onCheckedChange = { viewModel.updateRememberSourceSelection(currentProfile.id, it) },
                onBack = onGoBack
            )

            // AUTO-SELECT SOURCE
            SettingToggleRow(
                label = "Auto-select Source",
                subtitle = "Automatically pick the first available source for new content",
                isChecked = currentProfile.autoSelectSource,
                onCheckedChange = { viewModel.updateAutoSelectSource(currentProfile.id, it) },
                onBack = onGoBack
            )

            // SKIP INTRO
            SettingToggleRow(
                label = "Skip Intro",
                subtitle = "Show a skip button during intro segments if available in IntroDB",
                isChecked = currentProfile.skipIntro,
                onCheckedChange = { viewModel.updateSkipIntro(currentProfile.id, it) },
                onBack = onGoBack
            )

            // AUTOPLAY NEXT EPISODE
            SettingToggleRow(
                label = "Autoplay Next Episode",
                subtitle = "Automatically play the next episode when one finishes",
                isChecked = currentProfile.autoplayNextEpisode,
                onCheckedChange = { viewModel.updateAutoplayNextEpisode(currentProfile.id, it) },
                onBack = onGoBack
            )

            // AUTOPLAY THRESHOLD (only visible when autoplay is enabled)
            if (currentProfile.autoplayNextEpisode) {
                Spacer(Modifier.height(4.dp))

                SettingOptionRow(
                    label = "Threshold",
                    options = listOf("Only IntroDB" to "introdb", "Percentage" to "percentage", "Time" to "time"),
                    selectedOption = currentProfile.autoplayThresholdMode,
                    onOptionSelected = { viewModel.updateAutoplayThresholdMode(currentProfile.id, it) },
                    onBack = onGoBack
                )

                if (currentProfile.autoplayThresholdMode == "percentage") {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "Trigger at ${currentProfile.autoplayThresholdPercent}%",
                        color = Color.White.copy(0.6f),
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                        modifier = Modifier.padding(start = 4.dp, bottom = 4.dp)
                    )
                    VoidSlider(
                        value = currentProfile.autoplayThresholdPercent.toFloat(),
                        onValueChange = { viewModel.updateAutoplayThresholdPercent(currentProfile.id, it.toInt()) },
                        valueRange = 50f..99f,
                        steps = 48
                    )
                } else if (currentProfile.autoplayThresholdMode == "time") {
                    Spacer(Modifier.height(4.dp))
                    val secs = currentProfile.autoplayThresholdSeconds
                    val display = if (secs >= 60) "${secs / 60}m ${secs % 60}s remaining" else "${secs}s remaining"
                    Text(
                        text = "Trigger with $display",
                        color = Color.White.copy(0.6f),
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                        modifier = Modifier.padding(start = 4.dp, bottom = 4.dp)
                    )
                    VoidSlider(
                        value = secs.toFloat(),
                        onValueChange = { viewModel.updateAutoplayThresholdSeconds(currentProfile.id, it.toInt()) },
                        valueRange = 10f..300f,
                        steps = 28
                    )
                }
            }

            // LANGUAGE PREFERENCES SECTION
            Spacer(Modifier.height(12.dp))
            Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(Color.White.copy(0.1f)))
            Spacer(Modifier.height(12.dp))

            Text(
                "Language Preferences",
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold, fontSize = 16.sp),
                color = Color.White.copy(0.7f),
                modifier = Modifier.padding(bottom = 8.dp)
            )

            SettingLanguageRow(
                label = "Audio Language",
                currentDisplayName = languageDisplayName(currentProfile.preferredAudioLanguage),
                isSet = currentProfile.preferredAudioLanguage.isNotEmpty(),
                onClick = { activeLanguageField = LanguageField.AUDIO_PRIMARY },
                onBack = onGoBack,
                focusRequester = audioPrimaryFR
            )

            SettingLanguageRow(
                label = "Audio Language (Secondary)",
                currentDisplayName = languageDisplayName(currentProfile.preferredAudioLanguageSecondary),
                isSet = currentProfile.preferredAudioLanguageSecondary.isNotEmpty(),
                onClick = { activeLanguageField = LanguageField.AUDIO_SECONDARY },
                onBack = onGoBack,
                focusRequester = audioSecondaryFR
            )

            SettingLanguageRow(
                label = "Subtitle Language",
                currentDisplayName = languageDisplayName(currentProfile.preferredSubtitleLanguage),
                isSet = currentProfile.preferredSubtitleLanguage.isNotEmpty(),
                onClick = { activeLanguageField = LanguageField.SUBTITLE_PRIMARY },
                onBack = onGoBack,
                focusRequester = subtitlePrimaryFR
            )

            SettingLanguageRow(
                label = "Subtitle Language (Secondary)",
                currentDisplayName = languageDisplayName(currentProfile.preferredSubtitleLanguageSecondary),
                isSet = currentProfile.preferredSubtitleLanguageSecondary.isNotEmpty(),
                onClick = { activeLanguageField = LanguageField.SUBTITLE_SECONDARY },
                onBack = onGoBack,
                focusRequester = subtitleSecondaryFR
            )

            // SUBTITLE STYLE SECTION
            Spacer(Modifier.height(12.dp))
            Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(Color.White.copy(0.1f)))
            Spacer(Modifier.height(12.dp))

            Text(
                "Subtitle Style",
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold, fontSize = 16.sp),
                color = Color.White.copy(0.7f),
                modifier = Modifier.padding(bottom = 8.dp)
            )

            val subtitleStyleFR = remember { FocusRequester() }

            SettingLanguageRow(
                label = "Subtitle Appearance",
                currentDisplayName = "${currentProfile.subtitleSize}%",
                isSet = currentProfile.subtitleSize != 100 ||
                        currentProfile.subtitleOffset != 0 ||
                        currentProfile.subtitleTextColor != 0xFFFFFFFFL ||
                        currentProfile.subtitleBackgroundColor != 0x00000000L,
                onClick = { showSubtitleStyleSidebar = true },
                onBack = onGoBack,
                focusRequester = subtitleStyleFR
            )
        }

        // Subtitle style sidebar
        if (showSubtitleStyleSidebar) {
            val subtitleStyleSidebarFR = remember { FocusRequester() }

            LaunchedEffect(Unit) {
                delay(200)
                runCatching { subtitleStyleSidebarFR.requestFocus() }
            }

            Dialog(
                onDismissRequest = { showSubtitleStyleSidebar = false },
                properties = DialogProperties(usePlatformDefaultWidth = false)
            ) {
                GlassSidebarScaffold(
                    visible = true,
                    onDismiss = { showSubtitleStyleSidebar = false },
                    panelWidth = 380.dp
                ) {
                    SubtitleStyleSidebarContent(
                        profile = currentProfile,
                        viewModel = viewModel,
                        focusRequester = subtitleStyleSidebarFR,
                        onDismiss = { showSubtitleStyleSidebar = false }
                    )
                }
            }
        }

        // Language picker sidebar
        val sidebarOptions = when (activeLanguageField) {
            LanguageField.AUDIO_PRIMARY, LanguageField.AUDIO_SECONDARY -> AUDIO_LANGUAGE_OPTIONS
            LanguageField.SUBTITLE_PRIMARY, LanguageField.SUBTITLE_SECONDARY -> SUBTITLE_LANGUAGE_OPTIONS
            null -> emptyList()
        }
        val sidebarSelectedValue = when (activeLanguageField) {
            LanguageField.AUDIO_PRIMARY -> currentProfile.preferredAudioLanguage
            LanguageField.AUDIO_SECONDARY -> currentProfile.preferredAudioLanguageSecondary
            LanguageField.SUBTITLE_PRIMARY -> currentProfile.preferredSubtitleLanguage
            LanguageField.SUBTITLE_SECONDARY -> currentProfile.preferredSubtitleLanguageSecondary
            null -> ""
        }
        val sidebarTitle = when (activeLanguageField) {
            LanguageField.AUDIO_PRIMARY -> "Audio Language"
            LanguageField.AUDIO_SECONDARY -> "Audio Language (Secondary)"
            LanguageField.SUBTITLE_PRIMARY -> "Subtitle Language"
            LanguageField.SUBTITLE_SECONDARY -> "Subtitle Language (Secondary)"
            null -> ""
        }

        if (activeLanguageField != null) {
            Dialog(
                onDismissRequest = { activeLanguageField = null },
                properties = DialogProperties(usePlatformDefaultWidth = false)
            ) {
                GlassSidebarScaffold(
                    visible = true,
                    onDismiss = { activeLanguageField = null },
                    panelWidth = 260.dp
                ) {
                    LanguagePickerContent(
                        title = sidebarTitle,
                        options = sidebarOptions,
                        selectedValue = sidebarSelectedValue,
                        focusRequester = sidebarFocusRequester,
                        onSelect = { value ->
                            when (activeLanguageField) {
                                LanguageField.AUDIO_PRIMARY -> viewModel.updatePreferredAudioLanguage(currentProfile.id, value)
                                LanguageField.AUDIO_SECONDARY -> viewModel.updatePreferredAudioLanguageSecondary(currentProfile.id, value)
                                LanguageField.SUBTITLE_PRIMARY -> viewModel.updatePreferredSubtitleLanguage(currentProfile.id, value)
                                LanguageField.SUBTITLE_SECONDARY -> viewModel.updatePreferredSubtitleLanguageSecondary(currentProfile.id, value)
                                null -> {}
                            }
                            activeLanguageField = null
                        },
                        onDismiss = { activeLanguageField = null }
                    )
                }
            }
        }
    }
}

// --- COMPACT VOID COMPONENTS ---

@Composable
fun SettingToggleRow(
    label: String,
    subtitle: String? = null,
    isChecked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    onBack: (() -> Unit)? = null,
    blockUp: Boolean = false,
    onFocus: () -> Unit = {}
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    val accentColor = MaterialTheme.colorScheme.primary

    LaunchedEffect(isFocused) {
        if (isFocused) onFocus()
    }

    val scale by animateFloatAsState(if (isFocused) 1.02f else 1f)
    val borderColor by animateColorAsState(
        when {
            isFocused -> Color.White
            isChecked -> accentColor.copy(0.5f)
            else -> Color.Transparent
        }
    )

    val backModifier = if (onBack != null) {
        Modifier.onPreviewKeyEvent {
            if (it.key == Key.DirectionLeft && it.type == KeyEventType.KeyDown) {
                onBack(); true
            } else false
        }
    } else Modifier

    val upBlockModifier = if (blockUp) {
        Modifier.onPreviewKeyEvent {
            it.key == Key.DirectionUp && it.type == KeyEventType.KeyDown
        }
    } else Modifier

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp)
            .then(backModifier)
            .then(upBlockModifier)
            .scale(scale)
            .clip(RoundedCornerShape(8.dp))
            .background(Color.White.copy(0.05f))
            .border(if (isFocused || isChecked) 1.dp else 0.dp, borderColor, RoundedCornerShape(8.dp))
            .clickable(interactionSource = interactionSource, indication = null) {
                onCheckedChange(!isChecked)
            }
            .focusable(interactionSource = interactionSource)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                color = when {
                    isChecked -> accentColor
                    isFocused -> Color.White
                    else -> Color.White.copy(0.8f)
                },
                style = MaterialTheme.typography.bodyLarge.copy(
                    fontWeight = FontWeight.Medium,
                    fontSize = 15.sp
                )
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    color = Color.White.copy(0.4f),
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp)
                )
            }
        }
        Checkbox(
            checked = isChecked,
            onCheckedChange = null,
            colors = CheckboxDefaults.colors(
                checkedColor = accentColor,
                uncheckedColor = if (isFocused) Color.White.copy(0.7f) else Color.White.copy(0.3f),
                checkmarkColor = Color.White
            )
        )
    }
    Spacer(Modifier.height(6.dp))
}

@Composable
fun <T> SettingOptionRow(
    label: String,
    options: List<Pair<String, T>>,
    selectedOption: T,
    onOptionSelected: (T) -> Unit,
    onBack: (() -> Unit)? = null,
    blockUp: Boolean = false,
    onFocus: () -> Unit = {}
) {
    val accentColor = MaterialTheme.colorScheme.primary

    val backModifier = if (onBack != null) {
        Modifier.onPreviewKeyEvent {
            if (it.key == Key.DirectionLeft && it.type == KeyEventType.KeyDown) {
                onBack(); true
            } else false
        }
    } else Modifier

    val upBlockModifier = if (blockUp) {
        Modifier.onPreviewKeyEvent {
            it.key == Key.DirectionUp && it.type == KeyEventType.KeyDown
        }
    } else Modifier

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(Color.White.copy(0.05f))
            .padding(start = 16.dp, end = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            color = Color.White.copy(0.8f),
            style = MaterialTheme.typography.bodyLarge.copy(
                fontWeight = FontWeight.Medium,
                fontSize = 15.sp
            ),
            modifier = Modifier.weight(1f)
        )

        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            options.forEachIndexed { index, (optionLabel, value) ->
                val isSelected = selectedOption == value
                val interactionSource = remember { MutableInteractionSource() }
                val isFocused by interactionSource.collectIsFocusedAsState()

                LaunchedEffect(isFocused) {
                    if (isFocused) onFocus()
                }

                val scale by animateFloatAsState(if (isFocused) 1.06f else 1f)
                val chipBorder by animateColorAsState(
                    when {
                        isFocused -> Color.White
                        isSelected -> accentColor
                        else -> Color.White.copy(0.15f)
                    }
                )
                val chipTextColor = when {
                    isSelected -> accentColor
                    isFocused -> Color.White
                    else -> Color.White.copy(0.6f)
                }

                Box(
                    modifier = Modifier
                        .height(32.dp)
                        .then(if (index == 0) backModifier else Modifier)
                        .then(if (index == 0) upBlockModifier else Modifier)
                        .scale(scale)
                        .clip(RoundedCornerShape(6.dp))
                        .background(if (isSelected) accentColor.copy(0.1f) else Color.Transparent)
                        .border(
                            if (isFocused || isSelected) 1.5.dp else 1.dp,
                            chipBorder,
                            RoundedCornerShape(6.dp)
                        )
                        .clickable(interactionSource = interactionSource, indication = null) {
                            onOptionSelected(value)
                        }
                        .focusable(interactionSource = interactionSource)
                        .padding(horizontal = 12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = optionLabel,
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                            fontSize = 13.sp
                        ),
                        color = chipTextColor
                    )
                }
            }
        }
    }
    Spacer(Modifier.height(6.dp))
}

@Composable
fun SettingRow(label: String, content: @Composable () -> Unit) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = label,
            style = MaterialTheme.typography.titleMedium.copy(
                fontWeight = FontWeight.SemiBold, 
                fontSize = 18.sp
            ),
            color = Color.White,
            modifier = Modifier.padding(bottom = 12.dp)
        )
        content()
    }
}

@Composable
fun <T> VoidSegmentedControl(
    options: List<Pair<String, T>>,
    selectedOption: T,
    onOptionSelected: (T) -> Unit,
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
    blockUp: Boolean = false,
    onFocus: () -> Unit = {}
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(40.dp)
            .then(modifier),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        options.forEachIndexed { index, (label, value) ->
            val isSelected = selectedOption == value
            
            // Logic: Only the first item handles "Back" on Left Arrow.
            val backModifier = if (index == 0 && onBack != null) {
                Modifier.onPreviewKeyEvent {
                    if (it.key == Key.DirectionLeft && it.type == KeyEventType.KeyDown) {
                        onBack()
                        true
                    } else false
                }
            } else Modifier
            
            // Block Up navigation only if blockUp is true
            val upBlockModifier = if (blockUp) {
                Modifier.onPreviewKeyEvent {
                    if (it.key == Key.DirectionUp && it.type == KeyEventType.KeyDown) {
                        true // Consume the event to block focus escape
                    } else false
                }
            } else Modifier

            // SegmentItem is now a RowScope extension, weight applied inside
            SegmentItem(
                label = label,
                isSelected = isSelected,
                onClick = { onOptionSelected(value) },
                onFocus = onFocus,
                keyModifier = backModifier.then(upBlockModifier)
            )
        }
    }
}

@Composable
fun RowScope.SegmentItem(
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    onFocus: () -> Unit,
    keyModifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()

    LaunchedEffect(isFocused) {
        if (isFocused) onFocus()
    }
    
    val scale by animateFloatAsState(if (isFocused) 1.04f else 1f)

    val bgColor = Color.White.copy(0.05f)
    val textColor = when {
        isSelected -> MaterialTheme.colorScheme.primary
        isFocused -> Color.White
        else -> Color.White.copy(0.7f)
    }
    
    val borderColor by animateColorAsState(
        when {
            isFocused -> Color.White
            isSelected -> MaterialTheme.colorScheme.primary
            else -> Color.Transparent
        }
    )
    val borderWidth = if (isFocused || isSelected) 2.dp else 0.dp

    Box(
        modifier = Modifier
            .weight(1f)
            .widthIn(min = 100.dp)
            .fillMaxHeight()
            .then(keyModifier)
            .scale(scale)
            .clip(RoundedCornerShape(6.dp))
            .background(bgColor)
            .border(borderWidth, borderColor, RoundedCornerShape(6.dp))
            .clickable(interactionSource = interactionSource, indication = null) { onClick() }
            .focusable(interactionSource = interactionSource)
            .padding(horizontal = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Medium),
            color = textColor
        )
    }
}

@Composable
fun ColorItemFixed(
    color: Color,
    isSelected: Boolean,
    modifier: Modifier = Modifier, // ADDED: Now accepts modifier for key events
    onClick: () -> Unit,
    onFocus: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()

    LaunchedEffect(isFocused) {
        if (isFocused) onFocus()
    }

    val scale by animateFloatAsState(if (isFocused) 1.2f else 1.0f)
    val borderWidth by animateDpAsState(if (isFocused || isSelected) 2.dp else 0.dp)
    val borderColor by animateColorAsState(
        when {
            isFocused -> Color.White
            isSelected -> MaterialTheme.colorScheme.primary
            else -> Color.Transparent
        }
    )

    Box(
        modifier = modifier // Applied here
            .size(40.dp)
            .scale(scale)
            .clip(CircleShape)
            .background(color)
            .border(borderWidth, borderColor, CircleShape)
            .clickable(interactionSource = interactionSource, indication = null) { onClick() }
            .focusable(interactionSource = interactionSource)
    )
}




// --- LANGUAGE PREFERENCES ---

private enum class LanguageField {
    AUDIO_PRIMARY, AUDIO_SECONDARY, SUBTITLE_PRIMARY, SUBTITLE_SECONDARY
}

private val AUDIO_LANGUAGE_OPTIONS: List<Pair<String, String>> = listOf(
    "Default" to "",
    "English" to "en",
    "Spanish" to "es",
    "Spanish (Latin America)" to "es-419",
    "French" to "fr",
    "German" to "de",
    "Italian" to "it",
    "Portuguese" to "pt",
    "Portuguese (Brazil)" to "pt-BR",
    "Russian" to "ru",
    "Japanese" to "ja",
    "Korean" to "ko",
    "Chinese" to "zh",
    "Arabic" to "ar",
    "Hindi" to "hi",
    "Turkish" to "tr",
    "Polish" to "pl",
    "Dutch" to "nl",
    "Swedish" to "sv",
    "Norwegian" to "no",
    "Danish" to "da",
    "Finnish" to "fi",
    "Czech" to "cs",
    "Hungarian" to "hu",
    "Romanian" to "ro",
    "Thai" to "th",
    "Vietnamese" to "vi",
    "Indonesian" to "id",
    "Ukrainian" to "uk",
    "Greek" to "el",
    "Hebrew" to "he",
    "Malay" to "ms",
    "Croatian" to "hr",
    "Bulgarian" to "bg",
    "Slovak" to "sk",
    "Serbian" to "sr",
    "Filipino" to "tl",
    "Persian" to "fa",
    "Bengali" to "bn",
    "Tamil" to "ta",
    "Telugu" to "te"
)

// --- SUBTITLE STYLE COLORS ---

@Composable
private fun SubtitleStyleSidebarContent(
    profile: ProfileEntity,
    viewModel: SettingsViewModel,
    focusRequester: FocusRequester,
    onDismiss: () -> Unit
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(
            text = "Subtitle Style",
            color = Color.White,
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
        )

        // Preview area — dark cinematic gradient with sample subtitle text
        // Uses proportional sizing to match real player behavior:
        // Real player: 24sp base font on ~1080dp screen, offset = bottomPaddingFraction (0.08 + percent/100)
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .clip(RoundedCornerShape(8.dp))
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color(0xFF1a1a2e),
                            Color(0xFF16213e),
                            Color(0xFF0f3460),
                            Color(0xFF1a1a2e)
                        )
                    )
                ),
            contentAlignment = Alignment.BottomCenter
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.radialGradient(
                            colors = listOf(
                                Color.White.copy(alpha = 0.03f),
                                Color.Transparent
                            )
                        )
                    )
            )

            val previewTextColor = Color(profile.subtitleTextColor.toInt())
            val previewBgColor = Color(profile.subtitleBackgroundColor.toInt())

            // Readable base size that still responds to the size slider
            val previewHeight = maxHeight
            val baseFontSp = 13f
            val scaledFontSize = baseFontSp * (profile.subtitleSize / 100f)

            // Offset: matches real player's bottomPaddingFraction (0.08 base + percent/100)
            val bottomPaddingFraction = (0.08f + profile.subtitleOffset / 100f).coerceIn(0f, 0.5f)
            val bottomOffset = previewHeight * bottomPaddingFraction

            Text(
                text = "Your subtitles will\nlook like this",
                color = previewTextColor,
                style = MaterialTheme.typography.bodyLarge.copy(
                    fontSize = scaledFontSize.sp,
                    fontWeight = FontWeight.Medium,
                    lineHeight = (scaledFontSize * 1.3f).sp,
                    shadow = if (previewBgColor == Color.Transparent) {
                        androidx.compose.ui.graphics.Shadow(
                            color = Color.Black,
                            blurRadius = 4f
                        )
                    } else null
                ),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                modifier = Modifier
                    .padding(bottom = bottomOffset)
                    .background(previewBgColor, RoundedCornerShape(4.dp))
                    .padding(horizontal = 8.dp, vertical = 2.dp)
            )
        }

        // Size
        Text(
            text = "Size: ${profile.subtitleSize}%",
            color = Color.White.copy(0.7f),
            style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp)
        )
        CompactVoidSlider(
            value = profile.subtitleSize.toFloat(),
            onValueChange = { viewModel.updateSubtitleSize(profile.id, it.toInt()) },
            valueRange = 50f..200f,
            steps = 14,
            focusRequester = focusRequester
        )

        // Offset
        Text(
            text = "Offset: ${profile.subtitleOffset}%",
            color = Color.White.copy(0.7f),
            style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp)
        )
        CompactVoidSlider(
            value = profile.subtitleOffset.toFloat(),
            onValueChange = { viewModel.updateSubtitleOffset(profile.id, it.toInt()) },
            valueRange = -20f..20f,
            steps = 39
        )

        // Text Color
        Text(
            text = "Text Color",
            color = Color.White.copy(0.7f),
            style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp)
        )
        SubtitleColorChipRow(
            colors = SUBTITLE_TEXT_COLORS,
            selectedColor = profile.subtitleTextColor,
            onSelect = { viewModel.updateSubtitleTextColor(profile.id, it) }
        )

        // Background Color
        Text(
            text = "Background",
            color = Color.White.copy(0.7f),
            style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp)
        )
        SubtitleColorChipRow(
            colors = SUBTITLE_BACKGROUND_COLORS,
            selectedColor = profile.subtitleBackgroundColor,
            onSelect = { viewModel.updateSubtitleBackgroundColor(profile.id, it) }
        )
    }
}

private val SUBTITLE_TEXT_COLORS: List<Pair<String, Long>> = listOf(
    "White" to 0xFFFFFFFFL,
    "Gray" to 0xFFBDBDBDL,
    "Yellow" to 0xFFFFEB3BL,
    "Cyan" to 0xFF00BCD4L,
    "Red" to 0xFFF44336L,
    "Orange" to 0xFFFF9800L,
    "Green" to 0xFF8BC34AL
)

private val SUBTITLE_BACKGROUND_COLORS: List<Pair<String, Long>> = listOf(
    "None" to 0x00000000L,
    "Black" to 0xFF000000L,
    "Semi" to 0x80000000L,
    "Dark" to 0xFF212121L
)

@Composable
private fun SubtitleColorChipRow(
    colors: List<Pair<String, Long>>,
    selectedColor: Long,
    onSelect: (Long) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        colors.forEach { (label, colorValue) ->
            SubtitleColorChip(
                label = label,
                color = Color(colorValue.toInt()),
                isSelected = selectedColor == colorValue,
                onClick = { onSelect(colorValue) },
                isTransparent = colorValue == 0x00000000L
            )
        }
    }
}

@Composable
private fun SubtitleColorChip(
    label: String,
    color: Color,
    isSelected: Boolean,
    onClick: () -> Unit,
    isTransparent: Boolean = false
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    val scale by animateFloatAsState(if (isFocused) 1.1f else 1f, label = "chipScale")
    val accentColor = MaterialTheme.colorScheme.primary

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .scale(scale)
            .clickable(interactionSource = interactionSource, indication = null, onClick = onClick)
            .focusable(interactionSource = interactionSource)
            .padding(2.dp)
    ) {
        Box(
            modifier = Modifier
                .size(24.dp)
                .clip(CircleShape)
                .then(
                    if (isTransparent) {
                        Modifier
                            .background(Color.White.copy(0.05f))
                            .border(1.dp, Color.White.copy(0.3f), CircleShape)
                    } else {
                        Modifier.background(color)
                    }
                )
                .then(
                    if (isSelected) Modifier.border(2.dp, accentColor, CircleShape)
                    else if (isFocused) Modifier.border(2.dp, Color.White, CircleShape)
                    else Modifier
                )
        )
        Spacer(Modifier.height(2.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
            color = if (isFocused || isSelected) Color.White else Color.Gray,
            maxLines = 1
        )
    }
}

@Composable
private fun CompactVoidSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    steps: Int,
    focusRequester: FocusRequester? = null
) {
    val accentColor = MaterialTheme.colorScheme.primary
    val stepSize = (valueRange.endInclusive - valueRange.start) / (steps + 1)
    val progress = (value - valueRange.start) / (valueRange.endInclusive - valueRange.start)

    Row(
        modifier = Modifier.fillMaxWidth().height(36.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        CompactSliderButton(
            text = "−",
            enabled = value > valueRange.start,
            onClick = { onValueChange((value - stepSize).coerceIn(valueRange)) },
            focusRequester = focusRequester
        )

        Box(
            modifier = Modifier
                .weight(1f)
                .height(6.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(Color.White.copy(0.2f))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(progress)
                    .clip(RoundedCornerShape(3.dp))
                    .background(accentColor)
            )
        }

        CompactSliderButton(
            text = "+",
            enabled = value < valueRange.endInclusive,
            onClick = { onValueChange((value + stepSize).coerceIn(valueRange)) }
        )
    }
}

@Composable
private fun CompactSliderButton(
    text: String,
    enabled: Boolean,
    onClick: () -> Unit,
    focusRequester: FocusRequester? = null
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    val accentColor = MaterialTheme.colorScheme.primary
    val scale by animateFloatAsState(if (isFocused) 1.1f else 1f, label = "compactBtnScale")

    Box(
        modifier = Modifier
            .size(32.dp)
            .scale(scale)
            .clip(RoundedCornerShape(6.dp))
            .background(Color.White.copy(0.1f))
            .border(
                if (isFocused) 2.dp else 1.dp,
                when {
                    isFocused && enabled -> accentColor
                    isFocused -> Color.White.copy(0.3f)
                    else -> Color.White.copy(0.2f)
                },
                RoundedCornerShape(6.dp)
            )
            .clickable(interactionSource = interactionSource, indication = null) { if (enabled) onClick() }
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
            .focusable(interactionSource = interactionSource),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = if (enabled) Color.White else Color.White.copy(0.3f),
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

private val SUBTITLE_LANGUAGE_OPTIONS: List<Pair<String, String>> = listOf(
    "Default" to "",
    "Off" to "#off"
) + AUDIO_LANGUAGE_OPTIONS.drop(1)

private fun languageDisplayName(code: String): String {
    if (code.isEmpty()) return "Default"
    if (code == "#off") return "Off"
    return AUDIO_LANGUAGE_OPTIONS.firstOrNull { it.second == code }?.first ?: code.uppercase()
}

@Composable
private fun SettingLanguageRow(
    label: String,
    currentDisplayName: String,
    isSet: Boolean,
    onClick: () -> Unit,
    onBack: (() -> Unit)? = null,
    focusRequester: FocusRequester? = null,
    onFocus: () -> Unit = {}
) {
    val accentColor = MaterialTheme.colorScheme.primary
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()

    LaunchedEffect(isFocused) {
        if (isFocused) onFocus()
    }

    val scale by animateFloatAsState(if (isFocused) 1.02f else 1f)

    val backModifier = if (onBack != null) {
        Modifier.onPreviewKeyEvent {
            if (it.key == Key.DirectionLeft && it.type == KeyEventType.KeyDown) {
                onBack(); true
            } else false
        }
    } else Modifier

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp)
            .then(backModifier)
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
            .scale(scale)
            .clip(RoundedCornerShape(8.dp))
            .background(Color.White.copy(0.05f))
            .border(
                if (isFocused) 1.dp else 0.dp,
                if (isFocused) Color.White else Color.Transparent,
                RoundedCornerShape(8.dp)
            )
            .clickable(interactionSource = interactionSource, indication = null) { onClick() }
            .focusable(interactionSource = interactionSource)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            color = if (isFocused) Color.White else Color.White.copy(0.8f),
            style = MaterialTheme.typography.bodyLarge.copy(
                fontWeight = FontWeight.Medium,
                fontSize = 15.sp
            ),
            modifier = Modifier.weight(1f)
        )
        Text(
            text = currentDisplayName,
            color = if (isSet) accentColor
                    else if (isFocused) Color.White else Color.White.copy(0.6f),
            style = MaterialTheme.typography.bodyMedium.copy(
                fontWeight = if (isSet) FontWeight.SemiBold else FontWeight.Normal,
                fontSize = 14.sp
            )
        )
    }
    Spacer(Modifier.height(6.dp))
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun LanguagePickerContent(
    title: String,
    options: List<Pair<String, String>>,
    selectedValue: String,
    focusRequester: FocusRequester,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val selectedIndex = options.indexOfFirst { it.second == selectedValue }.coerceAtLeast(0)
    val listState = rememberLazyListState()
    val accentColor = MaterialTheme.colorScheme.primary

    LaunchedEffect(selectedIndex) {
        if (selectedIndex > 0) {
            runCatching { listState.scrollToItem(selectedIndex) }
        }
    }

    Column {
        Text(
            title,
            style = MaterialTheme.typography.headlineSmall,
            color = Color.White,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        LazyColumn(
            state = listState,
            verticalArrangement = Arrangement.spacedBy(4.dp),
            contentPadding = PaddingValues(bottom = 32.dp),
            modifier = Modifier.onPreviewKeyEvent {
                when {
                    it.key == Key.DirectionLeft && it.type == KeyEventType.KeyDown -> true
                    it.key == Key.Back && it.type == KeyEventType.KeyUp -> { onDismiss(); true }
                    else -> false
                }
            }
        ) {
            itemsIndexed(options) { index, (displayName, value) ->
                val isSelected = value == selectedValue
                var isFocused by remember { mutableStateOf(false) }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(
                            when {
                                isFocused -> Color.White.copy(0.1f)
                                isSelected -> accentColor.copy(0.1f)
                                else -> Color.Transparent
                            }
                        )
                        .border(
                            when {
                                isFocused -> 2.dp
                                isSelected -> 1.dp
                                else -> 0.dp
                            },
                            when {
                                isFocused -> accentColor
                                isSelected -> accentColor.copy(0.5f)
                                else -> Color.Transparent
                            },
                            RoundedCornerShape(8.dp)
                        )
                        .then(if (index == selectedIndex) Modifier.focusRequester(focusRequester) else Modifier)
                        .then(
                            if (index == 0) Modifier.focusProperties { up = FocusRequester.Cancel }
                            else Modifier
                        )
                        .onFocusChanged { isFocused = it.isFocused }
                        .clickable { onSelect(value) }
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = displayName,
                        color = when {
                            isSelected -> accentColor
                            isFocused -> Color.White
                            else -> Color.LightGray
                        },
                        style = MaterialTheme.typography.bodyLarge.copy(
                            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal
                        ),
                        modifier = Modifier.weight(1f)
                    )
                    if (isSelected) {
                        Text(
                            "Selected",
                            style = MaterialTheme.typography.labelSmall,
                            color = accentColor
                        )
                    }
                }
            }
        }
    }
}

// --- SOURCE PREFERENCES SETTINGS ---

@Composable
fun SourcePreferencesSettings(
    currentProfile: ProfileEntity?,
    viewModel: SettingsViewModel,
    onGoBack: () -> Unit
) {
    if (currentProfile == null) return

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.Top,
        horizontalAlignment = Alignment.Start
    ) {
        // HEADER
        Text(
            "Source Preferences",
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold, fontSize = 20.sp),
            color = Color.White
        )
        Text(
            "Configure how sources are sorted and filtered.",
            style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp),
            color = Color.White.copy(0.6f),
            modifier = Modifier.padding(top = 4.dp)
        )

        Spacer(Modifier.height(15.dp))

        // ENABLE SORTING
        SettingToggleRow(
            label = "Sort Sources",
            subtitle = "Rank sources by quality, file size, and seed count",
            isChecked = currentProfile.sourceSortingEnabled,
            onCheckedChange = { viewModel.updateSourceSortingEnabled(currentProfile.id, it) },
            onBack = onGoBack,
            blockUp = true
        )

        if (currentProfile.sourceSortingEnabled) {
            Spacer(Modifier.height(15.dp))
            Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(Color.White.copy(0.1f)))
            Spacer(Modifier.height(15.dp))

            // SORT PRIORITY
            Text(
                "Sort Priority",
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold, fontSize = 16.sp),
                color = Color.White
            )
            Text(
                "Choose what matters most when ranking sources.",
                style = MaterialTheme.typography.bodyMedium.copy(fontSize = 12.sp),
                color = Color.White.copy(0.6f),
                modifier = Modifier.padding(top = 2.dp, bottom = 8.dp)
            )

            SettingOptionRow(
                label = "Primary Sort",
                options = listOf("Quality" to "quality", "File Size" to "size", "Seeds" to "seeds"),
                selectedOption = currentProfile.sourceSortPrimary,
                onOptionSelected = { viewModel.updateSourceSortPrimary(currentProfile.id, it) },
                onBack = onGoBack
            )

            SettingOptionRow(
                label = "Secondary Sort",
                options = listOf("Quality" to "quality", "File Size" to "size", "Seeds" to "seeds"),
                selectedOption = currentProfile.sourceSortSecondary,
                onOptionSelected = { viewModel.updateSourceSortSecondary(currentProfile.id, it) },
                onBack = onGoBack
            )

            Spacer(Modifier.height(15.dp))
            Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(Color.White.copy(0.1f)))
            Spacer(Modifier.height(15.dp))

            // QUALITY FILTER
            Text(
                "Allowed Qualities",
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold, fontSize = 16.sp),
                color = Color.White
            )
            Text(
                "Sources with disabled qualities will be hidden.",
                style = MaterialTheme.typography.bodyMedium.copy(fontSize = 12.sp),
                color = Color.White.copy(0.6f),
                modifier = Modifier.padding(top = 2.dp, bottom = 8.dp)
            )

            val enabledKeys = remember(currentProfile.sourceEnabledQualities) {
                currentProfile.sourceEnabledQualities.split(",").map { it.trim() }.filter { it.isNotEmpty() }.toSet()
            }

            fun toggleQuality(key: String) {
                val newKeys = if (key in enabledKeys) enabledKeys - key else enabledKeys + key
                viewModel.updateSourceEnabledQualities(currentProfile.id, newKeys.joinToString(","))
            }

            SettingToggleRow(label = "4K / UHD", isChecked = "4k" in enabledKeys, onCheckedChange = { toggleQuality("4k") }, onBack = onGoBack)
            SettingToggleRow(label = "1080p / FHD", isChecked = "1080p" in enabledKeys, onCheckedChange = { toggleQuality("1080p") }, onBack = onGoBack)
            SettingToggleRow(label = "720p / HD", isChecked = "720p" in enabledKeys, onCheckedChange = { toggleQuality("720p") }, onBack = onGoBack)
            SettingToggleRow(label = "480p / SD", isChecked = "sd" in enabledKeys, onCheckedChange = { toggleQuality("sd") }, onBack = onGoBack)
            SettingToggleRow(label = "CAM / Telesync", isChecked = "cam" in enabledKeys, onCheckedChange = { toggleQuality("cam") }, onBack = onGoBack)
            SettingToggleRow(label = "Unknown Quality", isChecked = "unknown" in enabledKeys, onCheckedChange = { toggleQuality("unknown") }, onBack = onGoBack)

            Spacer(Modifier.height(15.dp))
            Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(Color.White.copy(0.1f)))
            Spacer(Modifier.height(15.dp))

            // EXCLUDE PHRASES
            Text(
                "Exclude Phrases",
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold, fontSize = 16.sp),
                color = Color.White
            )
            Text(
                "Sources containing these words will be hidden. Comma-separated.",
                style = MaterialTheme.typography.bodyMedium.copy(fontSize = 12.sp),
                color = Color.White.copy(0.6f),
                modifier = Modifier.padding(top = 2.dp, bottom = 8.dp)
            )

            var excludeInput by remember(currentProfile.sourceExcludePhrases) {
                mutableStateOf(currentProfile.sourceExcludePhrases)
            }
            BasicTextField(
                value = excludeInput,
                onValueChange = { excludeInput = it },
                textStyle = MaterialTheme.typography.bodyMedium.copy(color = Color.White, fontSize = 14.sp),
                cursorBrush = SolidColor(Color.White),
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(
                    onDone = { viewModel.updateSourceExcludePhrases(currentProfile.id, excludeInput) }
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.White.copy(0.08f), RoundedCornerShape(8.dp))
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                decorationBox = { innerTextField ->
                    Box {
                        if (excludeInput.isEmpty()) {
                            Text("e.g. 3D, Sample", color = Color.White.copy(0.3f), fontSize = 14.sp)
                        }
                        innerTextField()
                    }
                }
            )
        }

        Spacer(Modifier.height(30.dp))
    }
}

// --- ABOUT SETTINGS ---

@Composable
fun AboutSettings(
    onGoBack: () -> Unit,
    updateManager: AppUpdateManager = hiltViewModel<AboutViewModel>().updateManager
) {
    val updateState by updateManager.state.collectAsState()
    val scope = rememberCoroutineScope()
    val accentColor = MaterialTheme.colorScheme.primary

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.Top,
        horizontalAlignment = Alignment.Start
    ) {
        // HEADER
        Text(
            "About",
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold, fontSize = 20.sp),
            color = Color.White
        )
        Text(
            "App information and updates.",
            style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp),
            color = Color.White.copy(0.6f),
            modifier = Modifier.padding(top = 4.dp)
        )

        Spacer(Modifier.height(20.dp))

        // VERSION ROW
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(Color.White.copy(0.05f))
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                "Version",
                color = Color.White.copy(0.8f),
                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium, fontSize = 15.sp)
            )
            Text(
                BuildConfig.VERSION_NAME,
                color = accentColor,
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
            )
        }

        Spacer(Modifier.height(12.dp))

        // UPDATE POPUP TOGGLE
        var popupEnabled by remember { mutableStateOf(updateManager.isPopupEnabled) }
        SettingToggleRow(
            label = "Show update popup on launch",
            isChecked = popupEnabled,
            onCheckedChange = {
                popupEnabled = it
                updateManager.setPopupEnabled(it)
            },
            onBack = onGoBack,
            blockUp = true
        )

        // CHECK FOR UPDATES BUTTON
        val checkInteraction = remember { MutableInteractionSource() }
        val isCheckFocused by checkInteraction.collectIsFocusedAsState()
        val checkScale by animateFloatAsState(if (isCheckFocused) 1.02f else 1f)
        val isChecking = updateState is UpdateState.Checking
        val isDownloading = updateState is UpdateState.Downloading

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .onPreviewKeyEvent {
                    if (it.key == Key.DirectionLeft && it.type == KeyEventType.KeyDown) {
                        onGoBack(); true
                    } else false
                }
                .scale(checkScale)
                .clip(RoundedCornerShape(8.dp))
                .background(if (isCheckFocused) accentColor.copy(0.15f) else Color.White.copy(0.05f))
                .border(
                    if (isCheckFocused) 1.dp else 0.dp,
                    if (isCheckFocused) accentColor else Color.Transparent,
                    RoundedCornerShape(8.dp)
                )
                .clickable(interactionSource = checkInteraction, indication = null) {
                    if (!isChecking && !isDownloading) {
                        scope.launch { updateManager.checkForUpdate() }
                    }
                }
                .focusable(interactionSource = checkInteraction)
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = when (updateState) {
                    is UpdateState.Checking -> "Checking..."
                    is UpdateState.UpToDate -> "You're up to date"
                    is UpdateState.Error -> (updateState as UpdateState.Error).message
                    else -> "Check for Updates"
                },
                color = if (isCheckFocused) Color.White else Color.White.copy(0.8f),
                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium, fontSize = 15.sp)
            )
        }

        // UPDATE AVAILABLE SECTION
        if (updateState is UpdateState.UpdateAvailable) {
            val info = (updateState as UpdateState.UpdateAvailable).info

            Spacer(Modifier.height(16.dp))
            Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(Color.White.copy(0.1f)))
            Spacer(Modifier.height(16.dp))

            Text(
                "Update Available: v${info.versionName}",
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold, fontSize = 16.sp),
                color = accentColor
            )

            if (info.changelog.isNotBlank()) {
                Text(
                    info.changelog,
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp),
                    color = Color.White.copy(0.6f),
                    modifier = Modifier.padding(top = 8.dp)
                )
            }

            Spacer(Modifier.height(12.dp))

            // DOWNLOAD BUTTON
            val dlInteraction = remember { MutableInteractionSource() }
            val isDlFocused by dlInteraction.collectIsFocusedAsState()
            val dlScale by animateFloatAsState(if (isDlFocused) 1.02f else 1f)

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .scale(dlScale)
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (isDlFocused) accentColor.copy(0.3f) else accentColor.copy(0.15f))
                    .border(
                        if (isDlFocused) 1.dp else 0.dp,
                        if (isDlFocused) accentColor else Color.Transparent,
                        RoundedCornerShape(8.dp)
                    )
                    .clickable(interactionSource = dlInteraction, indication = null) {
                        scope.launch { updateManager.downloadAndInstall(info.apkUrl) }
                    }
                    .focusable(interactionSource = dlInteraction)
                    .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Text(
                    "Download & Install",
                    color = Color.White,
                    style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                )
            }
        }

        // DOWNLOAD PROGRESS
        if (updateState is UpdateState.Downloading) {
            val progress = (updateState as UpdateState.Downloading).progress
            Spacer(Modifier.height(16.dp))
            Text(
                "Downloading... ${(progress * 100).toInt()}%",
                color = accentColor,
                style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp)
            )
            Spacer(Modifier.height(8.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(Color.White.copy(0.1f))
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(progress)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(2.dp))
                        .background(accentColor)
                )
            }
        }

        // READY TO INSTALL
        if (updateState is UpdateState.ReadyToInstall) {
            Spacer(Modifier.height(16.dp))
            Text(
                "Download complete. Installing...",
                color = accentColor,
                style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp)
            )
        }
    }
}
