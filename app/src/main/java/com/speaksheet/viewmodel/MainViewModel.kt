package com.speaksheet.viewmodel

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.speaksheet.data.AppDatabase
import com.speaksheet.data.AppSettings
import com.speaksheet.data.DeleteMode
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
        } else if (value == "#SPILL!") {
            "Spill error. Overlapping cells contain data."
        } else if (value == "#CIRCULAR!") {
            "Circular reference error"
        } else {
            if (settings.speakFormulas && formula.startsWith("=")) {
                val spokenFormula = formula.substring(1).replace(":", " to ")
                if (value.isNotEmpty()) {
                    "Formula equals $spokenFormula, evaluates to $value"
                } else {
                    "Formula equals $spokenFormula"
                }
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

    fun sortColumn(col: Int, ascending: Boolean) {
        viewModelScope.launch {
            spreadsheetEngine.sortColumn(col, ascending)
            _gridRefreshTrigger.value += 1
            autoSaveCurrentFile()
            val colHeader = spreadsheetEngine.getColumnHeaderName(col)
            val dir = if (ascending) "ascending" else "descending"
            ttsManager.speak("Sorted $colHeader $dir")
        }
    }

    fun clearColumn(col: Int) {
        viewModelScope.launch {
            spreadsheetEngine.clearColumn(col)
            _gridRefreshTrigger.value += 1
            autoSaveCurrentFile()
            val colLetter = spreadsheetEngine.getColumnName(col)
            ttsManager.speak("Column $colLetter cleared.")
        }
    }

    fun clearRow(row: Int) {
        viewModelScope.launch {
            spreadsheetEngine.clearRow(row)
            _gridRefreshTrigger.value += 1
            autoSaveCurrentFile()
            val rowNum = row + 1
            ttsManager.speak("Row $rowNum cleared.")
        }
    }

    fun deleteRow(row: Int) {
        viewModelScope.launch {
            spreadsheetEngine.deleteRow(row)
            _gridRefreshTrigger.value += 1
            autoSaveCurrentFile()
            val rowNum = row + 1
            ttsManager.speak("Row $rowNum deleted.")
        }
    }

    fun insertRow(row: Int) {
        viewModelScope.launch {
            spreadsheetEngine.insertRow(row)
            _gridRefreshTrigger.value += 1
            autoSaveCurrentFile()
            ttsManager.speak("Inserted row above ${row + 1}")
        }
    }

    fun addDividersEveryNRows(n: Int) {
        viewModelScope.launch {
            spreadsheetEngine.addDividersEveryNRows(n)
            _gridRefreshTrigger.value += 1
            autoSaveCurrentFile()
            ttsManager.speak("Added dividers every $n rows")
        }
    }

    fun removeDivider(row: Int) {
        viewModelScope.launch {
            spreadsheetEngine.removeDivider(row)
            _gridRefreshTrigger.value += 1
            autoSaveCurrentFile()
            ttsManager.speak("Removed divider at row ${row + 1}")
        }
    }

    fun removeAllDividers() {
        viewModelScope.launch {
            spreadsheetEngine.removeAllDividers()
            _gridRefreshTrigger.value += 1
            autoSaveCurrentFile()
            ttsManager.speak("Removed all dividers")
        }
    }

    fun setHeaderRowColor(color: Int?) {
        viewModelScope.launch {
            spreadsheetEngine.setHeaderRowColor(color)
            _gridRefreshTrigger.value += 1
            autoSaveCurrentFile()
            ttsManager.speak("Header row color updated")
        }
    }

    fun updateDeleteMode(mode: DeleteMode) {
        viewModelScope.launch {
            updateSettings(appSettings.value.copy(deleteMode = mode))
            val modeName = when (mode) {
                DeleteMode.CLEAR_CELL -> "cell"
                DeleteMode.CLEAR_ROW -> "row"
                DeleteMode.CLEAR_COLUMN -> "column"
            }
            ttsManager.speak("Delete button now clears $modeName")
        }
    }

    fun updateVoiceTypingLanguage(lang: String) {
        viewModelScope.launch {
            updateSettings(appSettings.value.copy(voiceTypingLanguage = lang))
            ttsManager.speak("Voice typing language set to $lang")
        }
    }

    fun insertVoiceText(text: String, selectedCell: Pair<Int, Int>?, editingCell: Pair<Int, Int>?, onUpdateEditingText: ((String) -> Unit)? = null) {
        if (editingCell != null) {
            onUpdateEditingText?.invoke(text)
        } else if (selectedCell != null) {
            viewModelScope.launch {
                spreadsheetEngine.setCell(selectedCell.first, selectedCell.second, text)
                _gridRefreshTrigger.value += 1
                autoSaveCurrentFile()
                if (appSettings.value.speakAfterEditing) {
                    val colName = spreadsheetEngine.getColumnName(selectedCell.second)
                    val rowNum = selectedCell.first + 1
                    ttsManager.speak("$colName$rowNum: $text")
                }
            }
        }
    }

    fun toggleColumnWrap(col: Int): Boolean {
        val newState = spreadsheetEngine.toggleColumnWrap(col)
        _gridRefreshTrigger.value += 1
        val colHeader = spreadsheetEngine.getColumnHeaderName(col)
        val stateStr = if (newState) "enabled" else "disabled"
        ttsManager.speak("Text wrap $stateStr for $colHeader")
        return newState
    }

    fun speakColumn(col: Int) {
        val colHeader = spreadsheetEngine.getColumnHeaderName(col)
        val colLetter = spreadsheetEngine.getColumnName(col)
        var count = 0
        for (r in 1 until spreadsheetEngine.maxRow) {
            if (spreadsheetEngine.getCellValue(r, col).isNotEmpty()) count++
        }
        val wrapState = if (spreadsheetEngine.isWrapEnabled(col)) "Text wrap enabled." else "Text wrap disabled."
        ttsManager.speak("Column $colLetter $colHeader. $count items. $wrapState")
    }

    fun speakRow(row: Int) {
        val rowNum = row + 1
        val items = mutableListOf<String>()
        val maxColToCheck = minOf(spreadsheetEngine.maxCol, 26)
        for (c in 0 until maxColToCheck) {
            val v = spreadsheetEngine.getCellValue(row, c)
            if (v.isNotEmpty()) {
                val colName = spreadsheetEngine.getColumnName(c)
                items.add("$colName$rowNum: $v")
            }
        }
        if (items.isNotEmpty()) {
            ttsManager.speak("Row $rowNum. ${items.joinToString(", ")}")
        } else {
            ttsManager.speak("Row $rowNum is empty")
        }
    }

    fun speak(text: String) {
        ttsManager.speak(text)
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

    private var copiedCellBgColor: Int? = null
    private var copiedCellTextColor: Int? = null

    fun copyCell(context: android.content.Context, row: Int, col: Int) {
        val value = spreadsheetEngine.getCellFormulaOrValue(row, col)
        copiedCellBgColor = spreadsheetEngine.getCellColor(row, col)
        copiedCellTextColor = spreadsheetEngine.getCellTextColor(row, col)
        val colName = spreadsheetEngine.getColumnName(col)
        val cellName = "$colName${row + 1}"
        val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as? android.content.ClipboardManager
        val clip = android.content.ClipData.newPlainText("Cell Content", value)
        clipboard?.setPrimaryClip(clip)
        if (value.isNotEmpty()) {
            ttsManager.speak("Copied cell $cellName: $value")
        } else {
            ttsManager.speak("Copied cell $cellName")
        }
    }

    fun pasteCell(context: android.content.Context, row: Int, col: Int) {
        val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as? android.content.ClipboardManager
        val item = clipboard?.primaryClip?.getItemAt(0)
        val text = item?.text?.toString() ?: ""
        val colName = spreadsheetEngine.getColumnName(col)
        val cellName = "$colName${row + 1}"
        if (text.isNotEmpty() || copiedCellBgColor != null || copiedCellTextColor != null) {
            if (text.isNotEmpty()) {
                updateCell(row, col, text)
            }
            if (copiedCellBgColor != null) {
                setCellColor(row, col, copiedCellBgColor)
            }
            if (copiedCellTextColor != null) {
                setCellTextColor(row, col, copiedCellTextColor)
            }
            ttsManager.speak("Pasted into $cellName")
        } else {
            ttsManager.speak("Clipboard is empty")
        }
    }

    fun setCellColor(r: Int, c: Int, color: Int?) {
        spreadsheetEngine.setCellColor(r, c, color)
        _gridRefreshTrigger.value += 1
        autoSaveCurrentFile()
        val colName = spreadsheetEngine.getColumnName(c)
        val cellName = "$colName${r + 1}"
        if (color != null) {
            ttsManager.speak("Background color set for $cellName")
        } else {
            ttsManager.speak("Background color cleared for $cellName")
        }
    }

    fun setCellTextColor(r: Int, c: Int, color: Int?) {
        spreadsheetEngine.setCellTextColor(r, c, color)
        _gridRefreshTrigger.value += 1
        autoSaveCurrentFile()
        val colName = spreadsheetEngine.getColumnName(c)
        val cellName = "$colName${r + 1}"
        if (color != null) {
            ttsManager.speak("Text color set for $cellName")
        } else {
            ttsManager.speak("Text color cleared for $cellName")
        }
    }

    fun setColumnColor(c: Int, color: Int?) {
        spreadsheetEngine.setColumnColor(c, color)
        _gridRefreshTrigger.value += 1
        autoSaveCurrentFile()
        val colName = spreadsheetEngine.getColumnName(c)
        val headerName = spreadsheetEngine.getColumnHeaderName(c)
        if (color != null) {
            ttsManager.speak("Background color set for column $colName $headerName")
        } else {
            ttsManager.speak("Background color cleared for column $colName $headerName")
        }
    }

    fun setColumnTextColor(c: Int, color: Int?) {
        spreadsheetEngine.setColumnTextColor(c, color)
        _gridRefreshTrigger.value += 1
        autoSaveCurrentFile()
        val colName = spreadsheetEngine.getColumnName(c)
        val headerName = spreadsheetEngine.getColumnHeaderName(c)
        if (color != null) {
            ttsManager.speak("Text color set for column $colName $headerName")
        } else {
            ttsManager.speak("Text color cleared for column $colName $headerName")
        }
    }

    fun setRowColor(r: Int, color: Int?) {
        spreadsheetEngine.setRowColor(r, color)
        _gridRefreshTrigger.value += 1
        autoSaveCurrentFile()
        val rowNum = r + 1
        if (color != null) {
            ttsManager.speak("Background color set for row $rowNum")
        } else {
            ttsManager.speak("Background color cleared for row $rowNum")
        }
    }

    fun setRowTextColor(r: Int, color: Int?) {
        spreadsheetEngine.setRowTextColor(r, color)
        _gridRefreshTrigger.value += 1
        autoSaveCurrentFile()
        val rowNum = r + 1
        if (color != null) {
            ttsManager.speak("Text color set for row $rowNum")
        } else {
            ttsManager.speak("Text color cleared for row $rowNum")
        }
    }

    fun deleteCell(row: Int, col: Int) {
        val colName = spreadsheetEngine.getColumnName(col)
        val cellName = "$colName${row + 1}"
        updateCell(row, col, "")
        setCellColor(row, col, null)
        setCellTextColor(row, col, null)
        ttsManager.speak("Deleted cell $cellName")
    }

    override fun onCleared() {
        super.onCleared()
        ttsManager.shutdown()
    }
}
