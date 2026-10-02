package com.speaksheet.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.speaksheet.data.InteractionMode
import com.speaksheet.viewmodel.MainViewModel
import kotlin.math.roundToInt

data class PresetColorSwatch(val name: String, val colorInt: Int)

val PRESET_THEME_SWATCHES = listOf(
    PresetColorSwatch("SpeakSheet Green", 0xFF4CAF50.toInt()),
    PresetColorSwatch("Emerald", 0xFF00897B.toInt()),
    PresetColorSwatch("Ocean Blue", 0xFF1976D2.toInt()),
    PresetColorSwatch("Sky Blue", 0xFF0288D1.toInt()),
    PresetColorSwatch("Royal Purple", 0xFF7B1FA2.toInt()),
    PresetColorSwatch("Deep Indigo", 0xFF3F51B5.toInt()),
    PresetColorSwatch("Crimson Red", 0xFFD32F2F.toInt()),
    PresetColorSwatch("Coral Amber", 0xFFFF5722.toInt()),
    PresetColorSwatch("Golden Orange", 0xFFFFA000.toInt()),
    PresetColorSwatch("Teal Cyan", 0xFF00ACC1.toInt()),
    PresetColorSwatch("Slate Gray", 0xFF455A64.toInt()),
    PresetColorSwatch("Rose Pink", 0xFFE91E63.toInt())
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: MainViewModel,
    onNavigateBack: () -> Unit
) {
    val settings by viewModel.appSettings.collectAsStateWithLifecycle()
    val voices by viewModel.ttsManager.availableVoices.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val currentThemeColor = Color(settings.themeColor)

    var showVoiceDialog by remember { mutableStateOf(false) }
    var showThemeColorDialog by remember { mutableStateOf(false) }
    var showSelectedCellColorDialog by remember { mutableStateOf(false) }
    var localSpeechRate by remember(settings.speechRate) { mutableFloatStateOf(settings.speechRate) }
    var localPitch by remember(settings.pitch) { mutableFloatStateOf(settings.pitch) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onBackground
                )
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            item { SettingsHeader("Appearance Settings", currentThemeColor) }

            item {
                ColorSettingRow(
                    title = "Theme color",
                    subtitle = "Changes the app's main accent color (currently green)",
                    currentColor = Color(settings.themeColor),
                    testTag = "setting_theme_color",
                    onClick = { showThemeColorDialog = true }
                )
            }

            item {
                ColorSettingRow(
                    title = "Selected-cell color",
                    subtitle = "Changes the cell highlight border color (currently green)",
                    currentColor = Color(settings.selectedCellColor),
                    testTag = "setting_selected_cell_color",
                    onClick = { showSelectedCellColorDialog = true }
                )
            }

            item { SettingsHeader("Speech Settings", currentThemeColor) }
            
            item {
                ListItem(
                    headlineContent = { Text("TTS Voice") },
                    supportingContent = { Text(if (settings.voiceName.isEmpty()) "System Default" else settings.voiceName) },
                    modifier = Modifier.clickable { showVoiceDialog = true }.testTag("setting_tts_voice")
                )
            }
            
            item {
                Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                    Text("Speech Rate: ${String.format("%.1f", localSpeechRate)}x")
                    Slider(
                        value = localSpeechRate,
                        onValueChange = { localSpeechRate = it },
                        onValueChangeFinished = {
                            viewModel.updateSettings(settings.copy(speechRate = localSpeechRate))
                        },
                        valueRange = 0.5f..2.0f,
                        colors = SliderDefaults.colors(thumbColor = currentThemeColor, activeTrackColor = currentThemeColor)
                    )
                }
            }
            
            item {
                Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                    Text("Pitch: ${String.format("%.1f", localPitch)}")
                    Slider(
                        value = localPitch,
                        onValueChange = { localPitch = it },
                        onValueChangeFinished = {
                            viewModel.updateSettings(settings.copy(pitch = localPitch))
                        },
                        valueRange = 0.5f..2.0f,
                        colors = SliderDefaults.colors(thumbColor = currentThemeColor, activeTrackColor = currentThemeColor)
                    )
                }
            }
            
            item {
                Button(
                    onClick = { viewModel.ttsManager.speak("This is a sample voice.") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = currentThemeColor)
                ) {
                    Text("Test Voice")
                }
            }

            item { SettingsHeader("Interaction Settings", currentThemeColor) }
            
            item {
                InteractionRadioGroup(
                    currentMode = settings.interactionMode,
                    themeColor = currentThemeColor,
                    onModeSelected = { viewModel.updateSettings(settings.copy(interactionMode = it)) }
                )
            }

            item { SettingsHeader("Accessibility Settings", currentThemeColor) }
            
            item { SwitchSetting("Show gridlines", settings.showGridlines, currentThemeColor) { viewModel.updateSettings(settings.copy(showGridlines = it)) } }
            item { SwitchSetting("Show left side row numbers", settings.showRowNumbers, currentThemeColor) { viewModel.updateSettings(settings.copy(showRowNumbers = it)) } }
            item { SwitchSetting("Speak column header first (otherwise content first)", settings.announceColumnFirst, currentThemeColor) { viewModel.updateSettings(settings.copy(announceColumnFirst = it)) } }
            item { SwitchSetting("Speak row number", settings.speakRowNumber, currentThemeColor) { viewModel.updateSettings(settings.copy(speakRowNumber = it)) } }
            item { SwitchSetting("Speak column name", settings.speakColumnName, currentThemeColor) { viewModel.updateSettings(settings.copy(speakColumnName = it)) } }
            item { SwitchSetting("Speak formulas", settings.speakFormulas, currentThemeColor) { viewModel.updateSettings(settings.copy(speakFormulas = it)) } }
            item { SwitchSetting("Speak formatting", settings.speakFormatting, currentThemeColor) { viewModel.updateSettings(settings.copy(speakFormatting = it)) } }
            item { SwitchSetting("Speak empty cells", settings.speakEmptyCells, currentThemeColor) { viewModel.updateSettings(settings.copy(speakEmptyCells = it)) } }
            item { SwitchSetting("Speak after editing", settings.speakAfterEditing, currentThemeColor) { viewModel.updateSettings(settings.copy(speakAfterEditing = it)) } }
            item { SwitchSetting("Vibrate on selection & actions", settings.vibrateOnSelect, currentThemeColor) { viewModel.updateSettings(settings.copy(vibrateOnSelect = it)) } }
            item { SwitchSetting("Overflow menu 2-step tap mode (announce on 1st tap)", settings.overflowMenuTwoStepMode, currentThemeColor) { viewModel.updateSettings(settings.copy(overflowMenuTwoStepMode = it)) } }
            item { SwitchSetting("Large touch mode", settings.largeTouchMode, currentThemeColor) { viewModel.updateSettings(settings.copy(largeTouchMode = it)) } }
            item { SwitchSetting("High contrast grid", settings.highContrastGrid, currentThemeColor) { viewModel.updateSettings(settings.copy(highContrastGrid = it)) } }
        }
    }
    
    if (showVoiceDialog) {
        AlertDialog(
            onDismissRequest = { showVoiceDialog = false },
            title = { Text("Select Voice") },
            text = {
                LazyColumn {
                    items(voices.size, key = { voices[it].name }) { index ->
                        val voice = voices[index]
                        val name = "${voice.locale.displayLanguage} - ${voice.name}"
                        Text(
                            text = name,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    viewModel.updateSettings(settings.copy(voiceName = voice.name))
                                    showVoiceDialog = false
                                }
                                .padding(16.dp)
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showVoiceDialog = false }) { Text("Cancel") }
            }
        )
    }

    if (showThemeColorDialog) {
        SettingColorPickerDialog(
            title = "Theme Color",
            initialColorInt = settings.themeColor,
            defaultColorInt = 0xFF4CAF50.toInt(),
            onColorSelected = { selectedColor ->
                viewModel.updateSettings(settings.copy(themeColor = selectedColor))
            },
            onDismiss = { showThemeColorDialog = false }
        )
    }

    if (showSelectedCellColorDialog) {
        SettingColorPickerDialog(
            title = "Selected-cell Color",
            initialColorInt = settings.selectedCellColor,
            defaultColorInt = 0xFF4CAF50.toInt(),
            onColorSelected = { selectedColor ->
                viewModel.updateSettings(settings.copy(selectedCellColor = selectedColor))
            },
            onDismiss = { showSelectedCellColorDialog = false }
        )
    }
}

@Composable
fun ColorSettingRow(
    title: String,
    subtitle: String,
    currentColor: Color,
    testTag: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .testTag(testTag),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(end = 16.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Surface(
            modifier = Modifier.size(24.dp),
            shape = CircleShape,
            color = currentColor,
            border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.6f)),
            shadowElevation = 2.dp
        ) {}
    }
}

@Composable
fun SettingColorPickerDialog(
    title: String,
    initialColorInt: Int,
    defaultColorInt: Int,
    onColorSelected: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    var workingColorInt by remember { mutableIntStateOf(initialColorInt) }

    val hsv = remember(initialColorInt) {
        val array = FloatArray(3)
        android.graphics.Color.colorToHSV(initialColorInt, array)
        array
    }
    var hue by remember { mutableFloatStateOf(hsv[0]) }
    var saturation by remember { mutableFloatStateOf(hsv[1]) }
    var value by remember { mutableFloatStateOf(hsv[2]) }

    fun updateFromHSV(h: Float, s: Float, v: Float) {
        hue = h
        saturation = s
        value = v
        val color = android.graphics.Color.HSVToColor(floatArrayOf(h, s, v)) or (0xFF shl 24)
        workingColorInt = color
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Color Preview & Hex Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(52.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color(workingColorInt))
                            .border(2.dp, Color.Black.copy(alpha = 0.25f), RoundedCornerShape(12.dp))
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Selected Color",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = String.format("#%06X", workingColorInt and 0xFFFFFF),
                            style = MaterialTheme.typography.titleMedium,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                // Preset Swatches Grid
                Text(
                    text = "Presets",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold
                )

                LazyVerticalGrid(
                    columns = GridCells.Fixed(4),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 130.dp)
                ) {
                    items(PRESET_THEME_SWATCHES) { swatch ->
                        val swatchColor = Color(swatch.colorInt)
                        val isSelected = workingColorInt == swatch.colorInt
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(swatchColor)
                                .border(
                                    if (isSelected) 2.5.dp else 1.dp,
                                    if (isSelected) Color.White else Color.Black.copy(alpha = 0.25f),
                                    RoundedCornerShape(8.dp)
                                )
                                .clickable {
                                    workingColorInt = swatch.colorInt
                                    val tempHsv = FloatArray(3)
                                    android.graphics.Color.colorToHSV(swatch.colorInt, tempHsv)
                                    hue = tempHsv[0]
                                    saturation = tempHsv[1]
                                    value = tempHsv[2]
                                }
                                .testTag("swatch_${swatch.name.lowercase().replace(" ", "_")}"),
                            contentAlignment = Alignment.Center
                        ) {}
                    }
                }

                HorizontalDivider()

                // Graphical Color Picker Canvas
                Text(
                    text = "Custom Palette",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold
                )

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(140.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .pointerInput(Unit) {
                            detectTapGestures { offset ->
                                val newHue = (offset.x / size.width).coerceIn(0f, 1f) * 360f
                                val newSat = (1f - (offset.y / size.height)).coerceIn(0f, 1f)
                                updateFromHSV(newHue, newSat, value)
                            }
                        }
                        .pointerInput(Unit) {
                            detectDragGestures { change, _ ->
                                val offset = change.position
                                val newHue = (offset.x / size.width).coerceIn(0f, 1f) * 360f
                                val newSat = (1f - (offset.y / size.height)).coerceIn(0f, 1f)
                                updateFromHSV(newHue, newSat, value)
                            }
                        }
                ) {
                    val hueBrush = remember {
                        Brush.horizontalGradient(
                            listOf(
                                Color.Red,
                                Color.Yellow,
                                Color.Green,
                                Color.Cyan,
                                Color.Blue,
                                Color.Magenta,
                                Color.Red
                            )
                        )
                    }
                    val satBrush = remember {
                        Brush.verticalGradient(
                            listOf(
                                Color.Transparent,
                                Color.White
                            )
                        )
                    }

                    Canvas(modifier = Modifier.fillMaxSize()) {
                        drawRect(brush = hueBrush)
                        drawRect(brush = satBrush)
                        if (value < 1.0f) {
                            drawRect(color = Color.Black.copy(alpha = (1.0f - value).coerceIn(0f, 1f)))
                        }
                        val thumbX = (hue / 360f) * size.width
                        val thumbY = (1f - saturation) * size.height
                        drawCircle(color = Color.White, radius = 7.dp.toPx(), center = Offset(thumbX, thumbY))
                        drawCircle(color = Color.Black, radius = 5.dp.toPx(), center = Offset(thumbX, thumbY))
                    }
                }

                // Brightness Slider
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
                        .padding(12.dp)
                ) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Brightness", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        Text("${(value * 100).roundToInt()}%", fontSize = 12.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                    }
                    Slider(
                        value = value,
                        onValueChange = { updateFromHSV(hue, saturation, it) },
                        valueRange = 0f..1f,
                        colors = SliderDefaults.colors(thumbColor = Color(workingColorInt), activeTrackColor = Color(workingColorInt)),
                        modifier = Modifier.testTag("setting_brightness_slider")
                    )
                }

                // Reset to Default Button
                OutlinedButton(
                    onClick = {
                        workingColorInt = defaultColorInt
                        val tempHsv = FloatArray(3)
                        android.graphics.Color.colorToHSV(defaultColorInt, tempHsv)
                        hue = tempHsv[0]
                        saturation = tempHsv[1]
                        value = tempHsv[2]
                    },
                    modifier = Modifier.fillMaxWidth().height(40.dp)
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Reset to Default (SpeakSheet Green)", fontSize = 12.sp)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onColorSelected(workingColorInt)
                    onDismiss()
                },
                modifier = Modifier.testTag("dialog_setting_color_save_button")
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
fun SettingsHeader(title: String, themeColor: Color) {
    Text(
        text = title,
        color = themeColor,
        style = MaterialTheme.typography.titleMedium,
        modifier = Modifier.padding(start = 16.dp, top = 24.dp, bottom = 8.dp)
    )
}

@Composable
fun SwitchSetting(title: String, checked: Boolean, themeColor: Color, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(title)
        Switch(
            checked = checked,
            onCheckedChange = null,
            colors = SwitchDefaults.colors(checkedThumbColor = themeColor, checkedTrackColor = themeColor.copy(alpha = 0.5f))
        )
    }
}

@Composable
fun InteractionRadioGroup(currentMode: InteractionMode, themeColor: Color, onModeSelected: (InteractionMode) -> Unit) {
    Column {
        InteractionMode.values().forEach { mode ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onModeSelected(mode) }
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                RadioButton(
                    selected = mode == currentMode,
                    onClick = null,
                    colors = RadioButtonDefaults.colors(selectedColor = themeColor)
                )
                Spacer(modifier = Modifier.width(8.dp))
                val label = when(mode) {
                    InteractionMode.SINGLE_TAP_SPEAK_DOUBLE_TAP_EDIT -> "Single Tap → Speak\nDouble Tap → Edit"
                    InteractionMode.SINGLE_TAP_SPEAK_DOUBLE_TAP_MENU -> "Single Tap → Speak\nDouble Tap → Menu"
                    InteractionMode.SINGLE_TAP_SPEAK_LONG_PRESS_MENU -> "Single Tap → Speak\nLong Press → Menu"
                }
                Text(label)
            }
        }
    }
}
