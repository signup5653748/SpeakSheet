package com.speaksheet.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.speaksheet.data.AppSettings
import com.speaksheet.data.InteractionMode
import com.speaksheet.ui.theme.GreenPrimary
import com.speaksheet.viewmodel.MainViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: MainViewModel,
    onNavigateBack: () -> Unit
) {
    val settings by viewModel.appSettings.collectAsStateWithLifecycle()
    val voices by viewModel.ttsManager.availableVoices.collectAsStateWithLifecycle()
    
    var showVoiceDialog by remember { mutableStateOf(false) }
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
            item { SettingsHeader("Speech Settings") }
            
            item {
                ListItem(
                    headlineContent = { Text("TTS Voice") },
                    supportingContent = { Text(if (settings.voiceName.isEmpty()) "System Default" else settings.voiceName) },
                    modifier = Modifier.clickable { showVoiceDialog = true }
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
                        colors = SliderDefaults.colors(thumbColor = GreenPrimary, activeTrackColor = GreenPrimary)
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
                        colors = SliderDefaults.colors(thumbColor = GreenPrimary, activeTrackColor = GreenPrimary)
                    )
                }
            }
            
            item {
                Button(
                    onClick = { viewModel.ttsManager.speak("This is a sample voice.") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = GreenPrimary)
                ) {
                    Text("Test Voice")
                }
            }

            item { SettingsHeader("Interaction Settings") }
            
            item {
                InteractionRadioGroup(
                    currentMode = settings.interactionMode,
                    onModeSelected = { viewModel.updateSettings(settings.copy(interactionMode = it)) }
                )
            }

            item { SettingsHeader("Accessibility Settings") }
            
            item { SwitchSetting("Show left side row numbers", settings.showRowNumbers) { viewModel.updateSettings(settings.copy(showRowNumbers = it)) } }
            item { SwitchSetting("Speak column header first (otherwise content first)", settings.announceColumnFirst) { viewModel.updateSettings(settings.copy(announceColumnFirst = it)) } }
            item { SwitchSetting("Speak row number", settings.speakRowNumber) { viewModel.updateSettings(settings.copy(speakRowNumber = it)) } }
            item { SwitchSetting("Speak column name", settings.speakColumnName) { viewModel.updateSettings(settings.copy(speakColumnName = it)) } }
            item { SwitchSetting("Speak formulas", settings.speakFormulas) { viewModel.updateSettings(settings.copy(speakFormulas = it)) } }
            item { SwitchSetting("Speak formatting", settings.speakFormatting) { viewModel.updateSettings(settings.copy(speakFormatting = it)) } }
            item { SwitchSetting("Speak empty cells", settings.speakEmptyCells) { viewModel.updateSettings(settings.copy(speakEmptyCells = it)) } }
            item { SwitchSetting("Speak after editing", settings.speakAfterEditing) { viewModel.updateSettings(settings.copy(speakAfterEditing = it)) } }
            item { SwitchSetting("Vibrate on cell selection", settings.vibrateOnSelect) { viewModel.updateSettings(settings.copy(vibrateOnSelect = it)) } }
            item { SwitchSetting("Large touch mode", settings.largeTouchMode) { viewModel.updateSettings(settings.copy(largeTouchMode = it)) } }
            item { SwitchSetting("High contrast grid", settings.highContrastGrid) { viewModel.updateSettings(settings.copy(highContrastGrid = it)) } }
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
}

@Composable
fun SettingsHeader(title: String) {
    Text(
        text = title,
        color = GreenPrimary,
        style = MaterialTheme.typography.titleMedium,
        modifier = Modifier.padding(start = 16.dp, top = 24.dp, bottom = 8.dp)
    )
}

@Composable
fun SwitchSetting(title: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
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
            colors = SwitchDefaults.colors(checkedThumbColor = GreenPrimary, checkedTrackColor = GreenPrimary.copy(alpha = 0.5f))
        )
    }
}

@Composable
fun InteractionRadioGroup(currentMode: InteractionMode, onModeSelected: (InteractionMode) -> Unit) {
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
                    colors = RadioButtonDefaults.colors(selectedColor = GreenPrimary)
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
