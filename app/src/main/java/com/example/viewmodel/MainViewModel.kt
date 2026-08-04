package com.example.viewmodel

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.AppDatabase
import com.example.data.AppSettings
import com.example.data.InteractionMode
import com.example.data.RecentFile
import com.example.data.SettingsRepository
import com.example.utils.SpreadsheetEngine
import com.example.utils.TtsManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val settingsRepository = SettingsRepository(application)
    private val recentFileDao = AppDatabase.getDatabase(application).recentFileDao()
    
    val ttsManager = TtsManager(application)
    val spreadsheetEngine = SpreadsheetEngine()

    val appSettings: StateFlow<AppSettings> = settingsRepository.appSettingsFlow
        .stateIn(viewModelScope, SharingStarted.Lazily, AppSettings())

    val recentFiles: StateFlow<List<RecentFile>> = recentFileDao.getAllRecentFiles()
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    private val _currentFileUri = MutableStateFlow<Uri?>(null)
    val currentFileUri: StateFlow<Uri?> = _currentFileUri.asStateFlow()

    private val _currentFileName = MutableStateFlow("Untitled")
    val currentFileName: StateFlow<String> = _currentFileName.asStateFlow()

    // Triggers recomposition of grid
    private val _gridRefreshTrigger = MutableStateFlow(0)
    val gridRefreshTrigger: StateFlow<Int> = _gridRefreshTrigger.asStateFlow()

    fun updateSettings(settings: AppSettings) {
        viewModelScope.launch {
            settingsRepository.updateSettings(settings)
            
            // Apply TTS settings immediately
            ttsManager.setSpeechRate(settings.speechRate)
            ttsManager.setPitch(settings.pitch)
            if (settings.voiceName.isNotEmpty()) {
                ttsManager.setVoice(settings.voiceName)
            }
        }
    }

    fun openNewSpreadsheet() {
        _currentFileUri.value = null
        _currentFileName.value = "New Spreadsheet"
        spreadsheetEngine.maxRow = appSettings.value.defaultRows
        spreadsheetEngine.maxCol = appSettings.value.defaultCols
        spreadsheetEngine.setCell(0, 0, "") // Initialize empty
        _gridRefreshTrigger.value += 1
    }

    fun openFile(uri: Uri, name: String) {
        _currentFileUri.value = uri
        _currentFileName.value = name
        viewModelScope.launch {
            spreadsheetEngine.loadFromUri(getApplication(), uri)
            _gridRefreshTrigger.value += 1
            
            // Add to recent files
            recentFileDao.insertRecentFile(
                RecentFile(
                    name = name,
                    path = uri.path ?: "",
                    uri = uri.toString(),
                    lastModified = System.currentTimeMillis(),
                    sizeBytes = 0L // Optional: resolve actual size
                )
            )
        }
    }

    fun updateCell(row: Int, col: Int, value: String) {
        spreadsheetEngine.setCell(row, col, value)
        _gridRefreshTrigger.value += 1
        
        if (appSettings.value.speakAfterEditing) {
            ttsManager.speak("Cell updated")
        }
    }

    fun speakCell(row: Int, col: Int) {
        val value = spreadsheetEngine.getCellValue(row, col)
        val formula = spreadsheetEngine.getCellFormulaOrValue(row, col)
        val colName = spreadsheetEngine.getColumnName(col)
        
        val settings = appSettings.value
        val sb = java.lang.StringBuilder()
        
        if (settings.speakColumnName) sb.append("$colName ")
        if (settings.speakRowNumber) sb.append("${row + 1} ")
        
        if (value.isEmpty() && formula.isEmpty()) {
            if (settings.speakEmptyCells) sb.append("Empty")
        } else {
            if (settings.speakFormulas && formula.startsWith("=")) {
                sb.append("Formula equals ${formula.substring(1).replace(":", " to ")}")
            } else {
                sb.append(value)
            }
        }
        
        ttsManager.speak(sb.toString())
    }

    override fun onCleared() {
        super.onCleared()
        ttsManager.shutdown()
    }
}
