package com.example.viewmodel

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.AppDatabase
import com.example.data.AppSettings
import com.example.data.InteractionMode
import com.example.data.RecentFile
import com.example.data.SampleSheets
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

    init {
        // Pre-populate sample sheets into Recent Files on first launch
        viewModelScope.launch {
            try {
                if (recentFileDao.getRecentFilesList().isEmpty()) {
                    SampleSheets.ALL_SAMPLES.forEachIndexed { index, sample ->
                        recentFileDao.insertRecentFile(
                            RecentFile(
                                name = "${sample.title}.xlsx",
                                path = "sample://${sample.id}",
                                uri = "sample://${sample.id}",
                                lastModified = System.currentTimeMillis() - (index * 60_000L),
                                sizeBytes = 15_360L
                            )
                        )
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

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

    fun openSampleSpreadsheet(sampleId: String) {
        val sample = SampleSheets.getSample(sampleId) ?: return
        _currentFileUri.value = Uri.parse("sample://${sample.id}")
        _currentFileName.value = "${sample.title}.xlsx"
        spreadsheetEngine.loadSampleData(sample.title, sample.rows)
        _gridRefreshTrigger.value += 1
        ttsManager.speak("Loaded ${sample.title}")
        
        viewModelScope.launch {
            recentFileDao.insertRecentFile(
                RecentFile(
                    name = "${sample.title}.xlsx",
                    path = "sample://${sample.id}",
                    uri = "sample://${sample.id}",
                    lastModified = System.currentTimeMillis(),
                    sizeBytes = 15_360L
                )
            )
        }
    }

    fun openFile(uri: Uri, name: String) {
        if (uri.toString().startsWith("sample://")) {
            val sampleId = uri.toString().removePrefix("sample://")
            openSampleSpreadsheet(sampleId)
            return
        }
        
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
        val colLetter = spreadsheetEngine.getColumnName(col)
        val headerName = spreadsheetEngine.getColumnHeaderName(col)
        
        val settings = appSettings.value
        
        val cellDescription: String = if (value.isEmpty() && formula.isEmpty()) {
            if (settings.speakEmptyCells) "Empty" else ""
        } else {
            if (settings.speakFormulas && formula.startsWith("=")) {
                "Formula equals ${formula.substring(1).replace(":", " to ")}"
            } else {
                value
            }
        }

        val locationDescription: String = buildString {
            if (settings.speakColumnName) {
                if (row == 0) {
                    append("Header $headerName")
                } else {
                    append(headerName)
                }
            }
            if (settings.showRowNumbers && settings.speakRowNumber && row > 0) {
                if (isNotEmpty()) append(", ")
                append("Row ${row + 1}")
            }
        }.trim()

        val textToSpeak = if (row == 0) {
            // Header row: don't repeat the header text twice
            if (locationDescription.isNotEmpty()) locationDescription else cellDescription
        } else if (settings.announceColumnFirst) {
            // When ON: first announce column name/header, then cell content
            if (locationDescription.isNotEmpty() && cellDescription.isNotEmpty()) {
                "$locationDescription: $cellDescription"
            } else {
                "$locationDescription $cellDescription".trim()
            }
        } else {
            // When OFF: first read cell content, after announce column name and label
            if (cellDescription.isNotEmpty() && locationDescription.isNotEmpty()) {
                "$cellDescription, $locationDescription"
            } else {
                "$cellDescription $locationDescription".trim()
            }
        }
        
        if (textToSpeak.isNotEmpty()) {
            ttsManager.speak(textToSpeak)
        }
    }

    fun toggleAnnounceColumnFirst() {
        val current = appSettings.value.announceColumnFirst
        val updated = !current
        updateSettings(appSettings.value.copy(announceColumnFirst = updated))
        ttsManager.speak(if (updated) "Column header first" else "Cell content first")
    }

    fun toggleShowRowNumbers() {
        val current = appSettings.value.showRowNumbers
        val updated = !current
        updateSettings(appSettings.value.copy(showRowNumbers = updated))
        ttsManager.speak(if (updated) "Left row numbers enabled" else "Left row numbers disabled")
    }

    fun copyCell(context: android.content.Context, row: Int, col: Int) {
        val value = spreadsheetEngine.getCellFormulaOrValue(row, col)
        val colName = spreadsheetEngine.getColumnName(col)
        val cellName = "$colName${row + 1}"
        val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as? android.content.ClipboardManager
        val clip = android.content.ClipData.newPlainText("Cell Content", value)
        clipboard?.setPrimaryClip(clip)
        if (value.isNotEmpty()) {
            ttsManager.speak("Copied cell $cellName: $value")
        } else {
            ttsManager.speak("Copied empty cell $cellName")
        }
    }

    fun pasteCell(context: android.content.Context, row: Int, col: Int) {
        val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as? android.content.ClipboardManager
        val item = clipboard?.primaryClip?.getItemAt(0)
        val text = item?.text?.toString() ?: ""
        val colName = spreadsheetEngine.getColumnName(col)
        val cellName = "$colName${row + 1}"
        if (text.isNotEmpty()) {
            updateCell(row, col, text)
            ttsManager.speak("Pasted $text into $cellName")
        } else {
            ttsManager.speak("Clipboard is empty")
        }
    }

    fun deleteCell(row: Int, col: Int) {
        val colName = spreadsheetEngine.getColumnName(col)
        val cellName = "$colName${row + 1}"
        updateCell(row, col, "")
        ttsManager.speak("Deleted cell $cellName")
    }

    override fun onCleared() {
        super.onCleared()
        ttsManager.shutdown()
    }
}
