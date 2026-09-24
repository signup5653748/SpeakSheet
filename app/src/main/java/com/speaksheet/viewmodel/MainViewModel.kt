package com.speaksheet.viewmodel

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.speaksheet.data.AppDatabase
import com.speaksheet.data.AppSettings
import com.speaksheet.data.InteractionMode
import com.speaksheet.data.RecentFile
import com.speaksheet.data.SampleSheets
import com.speaksheet.data.SettingsRepository
import com.speaksheet.utils.SpreadsheetEngine
import com.speaksheet.utils.TtsManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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
        // Pre-populate sample sheets into Recent Files on first launch if empty
        viewModelScope.launch {
            try {
                if (recentFileDao.getRecentFilesCount() == 0) {
                    SampleSheets.ALL_SAMPLES.forEachIndexed { index, sample ->
                        recentFileDao.upsertRecentFile(
                            RecentFile(
                                uri = "sample://${sample.id}",
                                name = "${sample.title}.xlsx",
                                path = "sample://${sample.id}",
                                lastModified = System.currentTimeMillis() - (index * 60_000L),
                                sizeBytes = 15_360L
                            )
                        )
                    }
                }
            } catch (e: Throwable) {
                // Ignore initialization error gracefully
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
        val defaultR = appSettings.value.defaultRows
        val defaultC = appSettings.value.defaultCols
        
        // 1. Immediately reset spreadsheet engine synchronously
        spreadsheetEngine.newSpreadsheet(rows = defaultR, cols = defaultC)
        _gridRefreshTrigger.value += 1

        viewModelScope.launch {
            // 2. Find next available name e.g. "Spreadsheet 1.xlsx", "Spreadsheet 2.xlsx", etc.
            val docsDir = File(getApplication<Application>().filesDir, "spreadsheets").apply { mkdirs() }
            val existingFiles = recentFileDao.getRecentFilesList()
            var count = 1
            var docName: String
            while (true) {
                docName = "Spreadsheet $count.xlsx"
                val file = File(docsDir, docName)
                val existsInDb = existingFiles.any { it.name.equals(docName, ignoreCase = true) }
                if (!file.exists() && !existsInDb) {
                    break
                }
                count++
            }

            val newFile = File(docsDir, docName)
            spreadsheetEngine.saveToFile(newFile)

            val fileUri = Uri.fromFile(newFile)
            _currentFileUri.value = fileUri
            _currentFileName.value = docName

            // 3. Register in Room DB so it appears in Recent Files immediately
            recentFileDao.upsertRecentFile(
                RecentFile(
                    uri = fileUri.toString(),
                    name = docName,
                    path = newFile.absolutePath,
                    lastModified = System.currentTimeMillis(),
                    sizeBytes = newFile.length()
                )
            )

            _gridRefreshTrigger.value += 1
            ttsManager.speak("Created new empty spreadsheet $docName")
        }
    }

    private fun autoSaveCurrentFile() {
        val uri = _currentFileUri.value ?: return
        viewModelScope.launch(Dispatchers.IO) {
            try {
                if (uri.scheme == "file") {
                    val file = File(uri.path ?: return@launch)
                    spreadsheetEngine.saveToFile(file)
                    recentFileDao.upsertRecentFile(
                        RecentFile(
                            uri = uri.toString(),
                            name = _currentFileName.value,
                            path = file.absolutePath,
                            lastModified = System.currentTimeMillis(),
                            sizeBytes = file.length()
                        )
                    )
                } else if (uri.scheme == "content") {
                    getApplication<Application>().contentResolver.openOutputStream(uri)?.use { out ->
                        if (_currentFileName.value.endsWith(".csv", ignoreCase = true)) {
                            spreadsheetEngine.saveCSV(out)
                        } else {
                            spreadsheetEngine.saveXLSX(out)
                        }
                    }
                }
            } catch (_: Throwable) {
                // Handled gracefully
            }
        }
    }

    fun saveDocument(onSaved: ((Boolean) -> Unit)? = null) {
        val uri = _currentFileUri.value
        viewModelScope.launch(Dispatchers.IO) {
            try {
                if (uri != null && uri.scheme == "file") {
                    val file = File(uri.path ?: return@launch)
                    spreadsheetEngine.saveToFile(file)
                    recentFileDao.upsertRecentFile(
                        RecentFile(
                            uri = uri.toString(),
                            name = _currentFileName.value,
                            path = file.absolutePath,
                            lastModified = System.currentTimeMillis(),
                            sizeBytes = file.length()
                        )
                    )
                    withContext(Dispatchers.Main) {
                        onSaved?.invoke(true)
                    }
                    ttsManager.speak("Spreadsheet saved")
                } else if (uri != null && uri.scheme == "content") {
                    getApplication<Application>().contentResolver.openOutputStream(uri)?.use { out ->
                        if (_currentFileName.value.endsWith(".csv", ignoreCase = true)) {
                            spreadsheetEngine.saveCSV(out)
                        } else {
                            spreadsheetEngine.saveXLSX(out)
                        }
                    }
                    withContext(Dispatchers.Main) {
                        onSaved?.invoke(true)
                    }
                    ttsManager.speak("Spreadsheet saved")
                } else {
                    val docsDir = File(getApplication<Application>().filesDir, "spreadsheets").apply { mkdirs() }
                    val docName = _currentFileName.value.takeIf { it.endsWith(".xlsx") || it.endsWith(".csv") }
                        ?: "${_currentFileName.value}.xlsx"
                    val newFile = File(docsDir, docName)
                    spreadsheetEngine.saveToFile(newFile)
                    val newUri = Uri.fromFile(newFile)
                    _currentFileUri.value = newUri
                    recentFileDao.upsertRecentFile(
                        RecentFile(
                            uri = newUri.toString(),
                            name = docName,
                            path = newFile.absolutePath,
                            lastModified = System.currentTimeMillis(),
                            sizeBytes = newFile.length()
                        )
                    )
                    withContext(Dispatchers.Main) {
                        onSaved?.invoke(true)
                    }
                    ttsManager.speak("Spreadsheet saved")
                }
            } catch (_: Throwable) {
                withContext(Dispatchers.Main) {
                    onSaved?.invoke(false)
                }
                ttsManager.speak("Save failed")
            }
        }
    }

    fun renameDocument(newName: String) {
        val trimmed = newName.trim()
        if (trimmed.isEmpty()) return
        val finalName = if (trimmed.endsWith(".xlsx", ignoreCase = true) || trimmed.endsWith(".csv", ignoreCase = true)) {
            trimmed
        } else {
            "$trimmed.xlsx"
        }
        val uri = _currentFileUri.value
        if (uri != null && uri.scheme == "file") {
            val oldFile = File(uri.path ?: return)
            val newFile = File(oldFile.parentFile, finalName)
            if (oldFile.exists() && oldFile.renameTo(newFile)) {
                val newUri = Uri.fromFile(newFile)
                _currentFileUri.value = newUri
                _currentFileName.value = finalName
                viewModelScope.launch {
                    recentFileDao.deleteRecentFileByUri(uri.toString())
                    recentFileDao.upsertRecentFile(
                        RecentFile(
                            uri = newUri.toString(),
                            name = finalName,
                            path = newFile.absolutePath,
                            lastModified = System.currentTimeMillis(),
                            sizeBytes = newFile.length()
                        )
                    )
                }
                ttsManager.speak("Renamed to $finalName")
                return
            }
        }
        _currentFileName.value = finalName
        ttsManager.speak("Renamed to $finalName")
    }

    fun deleteRecentFile(file: RecentFile) {
        viewModelScope.launch {
            if (file.uri.startsWith("file://")) {
                try {
                    val p = Uri.parse(file.uri).path
                    if (p != null) File(p).delete()
                } catch (_: Throwable) {}
            }
            recentFileDao.deleteByUriOrPath(file.uri, file.path)
            ttsManager.speak("Removed ${file.name}")
        }
    }

    fun openSampleSpreadsheet(sampleId: String) {
        val sample = SampleSheets.getSample(sampleId) ?: return
        val sampleUri = "sample://${sample.id}"
        _currentFileUri.value = Uri.parse(sampleUri)
        _currentFileName.value = "${sample.title}.xlsx"
        
        viewModelScope.launch {
            withContext(Dispatchers.Default) {
                spreadsheetEngine.loadSampleData(sample.title, sample.rows)
            }
            _gridRefreshTrigger.value += 1
            ttsManager.speak("Loaded ${sample.title}")

            recentFileDao.upsertRecentFile(
                RecentFile(
                    uri = sampleUri,
                    name = "${sample.title}.xlsx",
                    path = sampleUri,
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
            
            // Add or update recent file
            recentFileDao.upsertRecentFile(
                RecentFile(
                    uri = uri.toString(),
                    name = name,
                    path = uri.path ?: "",
                    lastModified = System.currentTimeMillis(),
                    sizeBytes = 0L // Optional: resolve actual size
                )
            )
        }
    }

    fun updateCell(row: Int, col: Int, value: String) {
        viewModelScope.launch {
            withContext(Dispatchers.Default) {
                spreadsheetEngine.setCell(row, col, value)
            }
            _gridRefreshTrigger.value += 1
            
            autoSaveCurrentFile()

            if (appSettings.value.speakAfterEditing) {
                ttsManager.speak("Cell updated")
            }
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
