package com.example.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.data.AppSettings
import com.example.data.InteractionMode
import com.example.ui.theme.GreenPrimary
import com.example.viewmodel.MainViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: MainViewModel,
    onNavigateBack: () -> Unit
) {
    val settings by viewModel.appSettings.collectAsState()
    val voices by viewModel.ttsManager.availableVoices.collectAsState()
    
    var showVoiceDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Back")
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
                    Text("Speech Rate: ${String.format("%.1f", settings.speechRate)}x")
                    Slider(
                        value = settings.speechRate,
                        onValueChange = { viewModel.updateSettings(settings.copy(speechRate = it)) },
                        valueRange = 0.5f..2.0f,
                        colors = SliderDefaults.colors(thumbColor = GreenPrimary, activeTrackColor = GreenPrimary)
                    )
                }
            }
            
            item {
                Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                    Text("Pitch: ${String.format("%.1f", settings.pitch)}")
                    Slider(
                        value = settings.pitch,
                        onValueChange = { viewModel.updateSettings(settings.copy(pitch = it)) },
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
                    items(voices.size) { index ->
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
