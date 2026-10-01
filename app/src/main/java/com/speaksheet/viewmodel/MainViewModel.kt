package com.speaksheet.viewmodel

import android.app.Application
import android.content.ContentValues
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.speaksheet.data.AppDatabase
import com.speaksheet.data.AppSettings
import com.speaksheet.data.DeleteMode
import com.speaksheet.data.InteractionMode
import com.speaksheet.data.RecentFile
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
    private val sheetDao = AppDatabase.getDatabase(application).sheetDao()
    
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

    private val _canUndo = MutableStateFlow(false)
    val canUndo: StateFlow<Boolean> = _canUndo.asStateFlow()

    private val _canRedo = MutableStateFlow(false)
    val canRedo: StateFlow<Boolean> = _canRedo.asStateFlow()

    fun updateUndoRedoState() {
        _canUndo.value = spreadsheetEngine.canUndo
        _canRedo.value = spreadsheetEngine.canRedo
    }

    private var autoSaveJob: kotlinx.coroutines.Job? = null

    fun switchSheet(index: Int): Boolean {
        val success = spreadsheetEngine.switchSheet(index)
        if (success) {
            updateUndoRedoState()
            _gridRefreshTrigger.value += 1
            ttsManager.speak("Switched to ${spreadsheetEngine.getActiveSheetName()}")
            autoSaveCurrentFile()
        }
        return success
    }

    fun addSheet(name: String = ""): Int {
        val idx = spreadsheetEngine.addSheet(name)
        updateUndoRedoState()
        _gridRefreshTrigger.value += 1
        ttsManager.speak("Added and switched to ${spreadsheetEngine.getActiveSheetName()}")
        autoSaveCurrentFile()
        return idx
    }

    fun renameSheet(index: Int, newName: String): Boolean {
        val success = spreadsheetEngine.renameSheet(index, newName)
        if (success) {
            _gridRefreshTrigger.value += 1
            ttsManager.speak("Sheet renamed to $newName")
            autoSaveCurrentFile()
        }
        return success
    }

    fun deleteSheet(index: Int): Boolean {
        val success = spreadsheetEngine.deleteSheet(index)
        if (success) {
            updateUndoRedoState()
            _gridRefreshTrigger.value += 1
            ttsManager.speak("Sheet deleted. Switched to ${spreadsheetEngine.getActiveSheetName()}")
            autoSaveCurrentFile()
        }
        return success
    }

    fun openSampleSectionedReport() {
        _currentFileUri.value = null
        _currentFileName.value = "Sectioned_Report_Sample.xlsx"
        spreadsheetEngine.newSpreadsheet()
        com.speaksheet.utils.SampleSheets.createSectionedReport(spreadsheetEngine)
        spreadsheetEngine.clearHistory()
        updateUndoRedoState()
        _gridRefreshTrigger.value += 1
        ttsManager.speak("Opened Sectioned Report sample spreadsheet")
    }

    init {
        // Clean out any legacy sample files from recent files database
        viewModelScope.launch(Dispatchers.IO) {
            try {
                recentFileDao.deleteSampleFiles()
            } catch (_: Throwable) {
                // Ignore initialization cleanup error gracefully
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

    fun updateLastActionMenuTab(tabIndex: Int) {
        viewModelScope.launch {
            val current = appSettings.value
            settingsRepository.updateSettings(current.copy(lastActionMenuTab = tabIndex))
        }
    }

    fun updateQuickActions(newActions: List<String>) {
        viewModelScope.launch {
            val current = appSettings.value
            settingsRepository.updateSettings(current.copy(quickActionIds = newActions))
        }
    }

    fun removeQuickAction(actionId: String) {
        viewModelScope.launch {
            val current = appSettings.value
            val updated = current.quickActionIds.filter { it != actionId }
            settingsRepository.updateSettings(current.copy(quickActionIds = updated))
        }
    }

    fun openNewSpreadsheet() {
        val defaultR = appSettings.value.defaultRows
        val defaultC = appSettings.value.defaultCols
        
        // 1. Immediately reset spreadsheet engine synchronously
        spreadsheetEngine.newSpreadsheet(rows = defaultR, cols = defaultC)
        spreadsheetEngine.clearHistory()
        updateUndoRedoState()
        _gridRefreshTrigger.value += 1

        viewModelScope.launch {
            // Cancel any pending auto-save from previous sheet
            autoSaveJob?.cancel()

            // 2. Offload directory and file existence checks to Dispatchers.IO
            val (newFile, docName) = withContext(Dispatchers.IO) {
                val docsDir = File(getApplication<Application>().filesDir, "spreadsheets").apply { mkdirs() }
                val existingFiles = recentFileDao.getRecentFilesList()
                var count = 1
                var name: String
                while (true) {
                    name = "Spreadsheet $count.xlsx"
                    val file = File(docsDir, name)
                    val existsInDb = existingFiles.any { it.name.equals(name, ignoreCase = true) }
                    if (!file.exists() && !existsInDb) {
                        break
                    }
                    count++
                }
                Pair(File(docsDir, name), name)
            }

            spreadsheetEngine.saveToFile(newFile)

            val fileUri = Uri.fromFile(newFile)
            _currentFileUri.value = fileUri
            _currentFileName.value = docName

            // 3. Register in Room DB so it appears in Recent Files immediately
            val targetUriStr = fileUri.toString()
            recentFileDao.upsertRecentFile(
                RecentFile(
                    uri = targetUriStr,
                    name = docName,
                    path = newFile.absolutePath,
                    lastModified = System.currentTimeMillis(),
                    sizeBytes = newFile.length()
                )
            )

            val sheetEntities = spreadsheetEngine.sheets.mapIndexed { idx, s ->
                com.speaksheet.data.SheetEntity(
                    fileUri = targetUriStr,
                    sheetName = s.name,
                    sheetIndex = idx,
                    zoom = s.zoom,
                    scrollX = s.scrollX,
                    scrollY = s.scrollY,
                    frozenRows = s.frozenRows,
                    frozenCols = s.frozenCols
                )
            }
            sheetDao.replaceSheetsForFile(targetUriStr, sheetEntities)

            _gridRefreshTrigger.value += 1
            ttsManager.speak("Created new empty spreadsheet $docName")
        }
    }

    private fun autoSaveCurrentFile() {
        val fileName = _currentFileName.value
        val uri = _currentFileUri.value
        autoSaveJob?.cancel()
        autoSaveJob = viewModelScope.launch(Dispatchers.IO) {
            kotlinx.coroutines.delay(400) // Debounce rapid edits and multi-step mutations
            try {
                val context = getApplication<Application>()
                val targetUriStr = uri?.toString() ?: ""

                // 1. Save Sheet states to Room DB
                if (targetUriStr.isNotEmpty()) {
                    val sheetEntities = spreadsheetEngine.sheets.mapIndexed { idx, s ->
                        com.speaksheet.data.SheetEntity(
                            fileUri = targetUriStr,
                            sheetName = s.name,
                            sheetIndex = idx,
                            zoom = s.zoom,
                            scrollX = s.scrollX,
                            scrollY = s.scrollY,
                            frozenRows = s.frozenRows,
                            frozenCols = s.frozenCols
                        )
                    }
                    sheetDao.replaceSheetsForFile(targetUriStr, sheetEntities)
                }

                // 2. Commit in-place to the opened document (content:// or file://)
                if (uri != null && uri.scheme == "content" && !uri.toString().startsWith("sample://")) {
                    try {
                        val stream = context.contentResolver.openOutputStream(uri, "wt")
                            ?: context.contentResolver.openOutputStream(uri, "w")
                        stream?.use { out ->
                            if (fileName.endsWith(".csv", ignoreCase = true)) {
                                spreadsheetEngine.saveCSV(out)
                            } else {
                                spreadsheetEngine.saveXLSX(out)
                            }
                        }
                        recentFileDao.upsertRecentFile(
                            RecentFile(
                                uri = uri.toString(),
                                name = fileName,
                                path = uri.toString(),
                                lastModified = System.currentTimeMillis(),
                                sizeBytes = 0L
                            )
                        )
                    } catch (_: Throwable) {}
                } else if (uri != null && uri.scheme == "file") {
                    val filePath = uri.path
                    if (filePath != null) {
                        val targetFile = File(filePath)
                        targetFile.parentFile?.mkdirs()
                        targetFile.outputStream().use { out ->
                            if (fileName.endsWith(".csv", ignoreCase = true)) {
                                spreadsheetEngine.saveCSV(out)
                            } else {
                                spreadsheetEngine.saveXLSX(out)
                            }
                        }
                        recentFileDao.upsertRecentFile(
                            RecentFile(
                                uri = uri.toString(),
                                name = fileName,
                                path = targetFile.absolutePath,
                                lastModified = System.currentTimeMillis(),
                                sizeBytes = targetFile.length()
                            )
                        )
                    }
                } else {
                    // Fallback for new/unsaved internal document
                    val docsDir = File(context.filesDir, "spreadsheets").apply { mkdirs() }
                    val localFile = File(docsDir, fileName)
                    spreadsheetEngine.saveToFile(localFile)
                    val localUri = Uri.fromFile(localFile)
                    _currentFileUri.value = localUri
                    recentFileDao.upsertRecentFile(
                        RecentFile(
                            uri = localUri.toString(),
                            name = fileName,
                            path = localFile.absolutePath,
                            lastModified = System.currentTimeMillis(),
                            sizeBytes = localFile.length()
                        )
                    )
                }
            } catch (_: Throwable) {
                // Handled gracefully
            }
        }
    }

    fun saveDocument(onSaved: ((Boolean, String) -> Unit)? = null) {
        val fileName = _currentFileName.value.let { name ->
            if (name.endsWith(".xlsx", ignoreCase = true) || name.endsWith(".csv", ignoreCase = true)) {
                name
            } else {
                "$name.xlsx"
            }
        }
        val isCsv = fileName.endsWith(".csv", ignoreCase = true)
        val mimeType = if (isCsv) "text/csv" else "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
        val context = getApplication<Application>()

        autoSaveJob?.cancel()
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val curUri = _currentFileUri.value
                var saveSuccess = false
                var displayPath = fileName

                // 1. If currently opened from an external writable content URI (SAF file picker / File Explorer), update existing file in place!
                if (curUri != null && curUri.scheme == "content" && !curUri.toString().startsWith("sample://")) {
                    try {
                        val stream = context.contentResolver.openOutputStream(curUri, "wt")
                            ?: context.contentResolver.openOutputStream(curUri, "w")
                        stream?.use { out ->
                            if (isCsv) {
                                spreadsheetEngine.saveCSV(out)
                            } else {
                                spreadsheetEngine.saveXLSX(out)
                            }
                            saveSuccess = true
                            displayPath = fileName
                        }
                        if (saveSuccess) {
                            recentFileDao.upsertRecentFile(
                                RecentFile(
                                    uri = curUri.toString(),
                                    name = fileName,
                                    path = curUri.toString(),
                                    lastModified = System.currentTimeMillis(),
                                    sizeBytes = 0L
                                )
                            )
                        }
                    } catch (_: Throwable) {
                        saveSuccess = false
                    }
                } else if (curUri != null && curUri.scheme == "file" && !curUri.toString().startsWith("sample://")) {
                    val filePath = curUri.path
                    if (filePath != null) {
                        try {
                            val targetFile = File(filePath)
                            targetFile.parentFile?.mkdirs()
                            targetFile.outputStream().use { out ->
                                if (isCsv) {
                                    spreadsheetEngine.saveCSV(out)
                                } else {
                                    spreadsheetEngine.saveXLSX(out)
                                }
                                saveSuccess = true
                                displayPath = fileName
                            }
                            if (saveSuccess) {
                                recentFileDao.upsertRecentFile(
                                    RecentFile(
                                        uri = curUri.toString(),
                                        name = fileName,
                                        path = targetFile.absolutePath,
                                        lastModified = System.currentTimeMillis(),
                                        sizeBytes = targetFile.length()
                                    )
                                )
                            }
                        } catch (_: Throwable) {
                            saveSuccess = false
                        }
                    }
                }

                // 2. Only if no existing file was open (brand new unsaved document or external content write failed), save to Downloads
                if (!saveSuccess) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        try {
                            val resolver = context.contentResolver
                            val contentValues = ContentValues().apply {
                                put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                                put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
                                put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                                put(MediaStore.MediaColumns.IS_PENDING, 1)
                            }
                            val collection = MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
                            val insertedUri = resolver.insert(collection, contentValues)
                            if (insertedUri != null) {
                                resolver.openOutputStream(insertedUri, "wt")?.use { out ->
                                    if (isCsv) {
                                        spreadsheetEngine.saveCSV(out)
                                    } else {
                                        spreadsheetEngine.saveXLSX(out)
                                    }
                                }
                                contentValues.clear()
                                contentValues.put(MediaStore.MediaColumns.IS_PENDING, 0)
                                resolver.update(insertedUri, contentValues, null, null)
                                _currentFileUri.value = insertedUri
                                displayPath = "Downloads/$fileName"
                                saveSuccess = true
                                recentFileDao.upsertRecentFile(
                                    RecentFile(
                                        uri = insertedUri.toString(),
                                        name = fileName,
                                        path = insertedUri.toString(),
                                        lastModified = System.currentTimeMillis(),
                                        sizeBytes = 15_360L
                                    )
                                )
                            }
                        } catch (_: Throwable) {}
                    }

                    if (!saveSuccess) {
                        val docsDir = File(context.filesDir, "spreadsheets").apply { mkdirs() }
                        val localFile = File(docsDir, fileName)
                        spreadsheetEngine.saveToFile(localFile)
                        val localUri = Uri.fromFile(localFile)
                        _currentFileUri.value = localUri
                        displayPath = fileName
                        saveSuccess = true
                        recentFileDao.upsertRecentFile(
                            RecentFile(
                                uri = localUri.toString(),
                                name = fileName,
                                path = localFile.absolutePath,
                                lastModified = System.currentTimeMillis(),
                                sizeBytes = localFile.length()
                            )
                        )
                    }
                }

                withContext(Dispatchers.Main) {
                    if (saveSuccess) {
                        onSaved?.invoke(true, displayPath)
                        ttsManager.speak("Document saved: $fileName")
                    } else {
                        onSaved?.invoke(false, "Could not write to storage")
                        ttsManager.speak("Save failed")
                    }
                }
            } catch (e: Throwable) {
                withContext(Dispatchers.Main) {
                    onSaved?.invoke(false, e.localizedMessage ?: "Save failed")
                    ttsManager.speak("Save failed")
                }
            }
        }
    }

    fun exportToUri(destinationUri: Uri, onResult: ((Boolean, String) -> Unit)? = null) {
        val fileName = _currentFileName.value
        val isCsv = fileName.endsWith(".csv", ignoreCase = true)
        val context = getApplication<Application>()

        autoSaveJob?.cancel()
        viewModelScope.launch(Dispatchers.IO) {
            try {
                context.contentResolver.openOutputStream(destinationUri, "w")?.use { out ->
                    if (isCsv) {
                        spreadsheetEngine.saveCSV(out)
                    } else {
                        spreadsheetEngine.saveXLSX(out)
                    }
                }
                try {
                    context.contentResolver.takePersistableUriPermission(
                        destinationUri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                    )
                } catch (_: Throwable) {}
                _currentFileUri.value = destinationUri
                recentFileDao.upsertRecentFile(
                    RecentFile(
                        uri = destinationUri.toString(),
                        name = fileName,
                        path = destinationUri.toString(),
                        lastModified = System.currentTimeMillis(),
                        sizeBytes = 15_360L
                    )
                )
                withContext(Dispatchers.Main) {
                    onResult?.invoke(true, "Saved to device as $fileName")
                    ttsManager.speak("Document saved to device as $fileName")
                }
            } catch (e: Throwable) {
                withContext(Dispatchers.Main) {
                    onResult?.invoke(false, e.localizedMessage ?: "Export failed")
                    ttsManager.speak("Save failed")
                }
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
            viewModelScope.launch(Dispatchers.IO) {
                val oldFile = File(uri.path ?: return@launch)
                val newFile = File(oldFile.parentFile, finalName)
                if (oldFile.exists() && oldFile.renameTo(newFile)) {
                    val newUri = Uri.fromFile(newFile)
                    _currentFileUri.value = newUri
                    _currentFileName.value = finalName
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
                    withContext(Dispatchers.Main) {
                        ttsManager.speak("Renamed to $finalName")
                    }
                }
            }
            return
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

    fun openFile(uri: Uri, name: String) {
        _currentFileUri.value = uri
        _currentFileName.value = name
        viewModelScope.launch {
            spreadsheetEngine.loadFromUri(getApplication(), uri)
            spreadsheetEngine.clearHistory()
            
            // Restore sheet states if present in Room
            try {
                val storedSheets = sheetDao.getSheetsForFile(uri.toString())
                for (stored in storedSheets) {
                    if (stored.sheetIndex in spreadsheetEngine.sheets.indices) {
                        val targetSheet = spreadsheetEngine.sheets[stored.sheetIndex]
                        targetSheet.zoom = stored.zoom
                        targetSheet.scrollX = stored.scrollX
                        targetSheet.scrollY = stored.scrollY
                        if (stored.frozenRows > 0) targetSheet.frozenRows = stored.frozenRows
                        if (stored.frozenCols > 0) targetSheet.frozenCols = stored.frozenCols
                    }
                }
            } catch (_: Throwable) {}

            updateUndoRedoState()
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
        val currentVal = spreadsheetEngine.getCellFormulaOrValue(row, col)
        if (currentVal != value) {
            val colName = spreadsheetEngine.getColumnName(col)
            spreadsheetEngine.pushUndo("Edit $colName${row + 1}")
            updateUndoRedoState()
        }
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
        if (spreadsheetEngine.isBannerRow(row)) {
            val text = spreadsheetEngine.getCellValue(row, 0)
            ttsManager.speak("Section text: $text")
            return
        }
        if (spreadsheetEngine.isHeaderRow(row)) {
            val headerTexts = (0 until spreadsheetEngine.maxCol)
                .map { spreadsheetEngine.getCellValue(row, it) }
                .filter { it.isNotEmpty() }
                .joinToString(", ")
            ttsManager.speak("Header row: $headerTexts")
            return
        }

        val value = spreadsheetEngine.getCellValue(row, col)
        val formula = spreadsheetEngine.getCellFormulaOrValue(row, col)
        val colLetter = spreadsheetEngine.getColumnName(col)
        val headerName = spreadsheetEngine.getColumnHeaderName(col, row)
        
        val settings = appSettings.value

        fun formatErrorForTts(err: String): String = when (err) {
            "#SPILL!" -> "Spill error. Overlapping cells contain data."
            "#CIRCULAR!" -> "Circular reference error"
            "#REF!" -> "Reference error"
            "#N/A", "#N/A!" -> "Value not available"
            "#DIV/0!" -> "Divide by zero error"
            "#NAME?" -> "Invalid name error"
            "#VALUE!" -> "Invalid value error"
            else -> err
        }
        
        val cellDescription: String = if (value.isEmpty() && formula.isEmpty()) {
            if (settings.speakEmptyCells) "Empty" else ""
        } else if (value.startsWith("#")) {
            formatErrorForTts(value)
        } else {
            if (settings.speakFormulas && formula.startsWith("=")) {
                val spokenFormula = formula.substring(1).replace(":", " to ")
                val spokenVal = if (value.startsWith("#")) formatErrorForTts(value) else value
                if (spokenVal.isNotEmpty()) {
                    "Formula equals $spokenFormula, evaluates to $spokenVal"
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
        val colHeader = spreadsheetEngine.getColumnHeaderName(col)
        val dir = if (ascending) "ascending" else "descending"
        spreadsheetEngine.pushUndo("Sort $colHeader $dir")
        updateUndoRedoState()
        viewModelScope.launch {
            spreadsheetEngine.sortColumn(col, ascending)
            _gridRefreshTrigger.value += 1
            autoSaveCurrentFile()
            ttsManager.speak("Sorted $colHeader $dir")
        }
    }

    fun clearColumn(col: Int) {
        val colLetter = spreadsheetEngine.getColumnName(col)
        spreadsheetEngine.pushUndo("Clear Column $colLetter")
        updateUndoRedoState()
        viewModelScope.launch {
            spreadsheetEngine.clearColumn(col)
            _gridRefreshTrigger.value += 1
            autoSaveCurrentFile()
            ttsManager.speak("Column $colLetter cleared.")
        }
    }

    fun clearRow(row: Int) {
        val rowNum = row + 1
        spreadsheetEngine.pushUndo("Clear Row $rowNum")
        updateUndoRedoState()
        viewModelScope.launch {
            spreadsheetEngine.clearRow(row)
            _gridRefreshTrigger.value += 1
            autoSaveCurrentFile()
            ttsManager.speak("Row $rowNum cleared.")
        }
    }

    fun deleteRow(row: Int) {
        val rowNum = row + 1
        spreadsheetEngine.pushUndo("Delete Row $rowNum")
        updateUndoRedoState()
        viewModelScope.launch {
            spreadsheetEngine.deleteRow(row)
            _gridRefreshTrigger.value += 1
            autoSaveCurrentFile()
            ttsManager.speak("Row $rowNum deleted.")
        }
    }

    fun insertRow(row: Int) {
        spreadsheetEngine.pushUndo("Insert Row ${row + 1}")
        updateUndoRedoState()
        viewModelScope.launch {
            spreadsheetEngine.insertRow(row)
            _gridRefreshTrigger.value += 1
            autoSaveCurrentFile()
            ttsManager.speak("Inserted row above ${row + 1}")
        }
    }

    fun insertRowAbove(row: Int) {
        insertRow(row)
    }

    fun insertRowBelow(row: Int) {
        spreadsheetEngine.pushUndo("Insert Row ${row + 2}")
        updateUndoRedoState()
        viewModelScope.launch {
            spreadsheetEngine.insertRow(row + 1)
            _gridRefreshTrigger.value += 1
            autoSaveCurrentFile()
            ttsManager.speak("Inserted row below ${row + 1}")
        }
    }

    fun convertRowToBanner(row: Int) {
        spreadsheetEngine.pushUndo("Convert to Banner")
        updateUndoRedoState()
        viewModelScope.launch {
            spreadsheetEngine.mergeRange(row, 0, row, spreadsheetEngine.maxCol - 1)
            _gridRefreshTrigger.value += 1
            autoSaveCurrentFile()
            ttsManager.speak("Converted row ${row + 1} to banner")
        }
    }

    fun setHeaderBgColor(color: Int?) {
        spreadsheetEngine.pushUndo("Set Header Background Color")
        spreadsheetEngine.setHeaderBgColor(color)
        updateUndoRedoState()
        viewModelScope.launch {
            _gridRefreshTrigger.value += 1
            autoSaveCurrentFile()
            ttsManager.speak("Header background color updated")
        }
    }

    fun setHeaderTextColor(color: Int?) {
        spreadsheetEngine.pushUndo("Set Header Text Color")
        spreadsheetEngine.setHeaderTextColor(color)
        updateUndoRedoState()
        viewModelScope.launch {
            _gridRefreshTrigger.value += 1
            autoSaveCurrentFile()
            ttsManager.speak("Header text color updated")
        }
    }

    fun unmergeBanner(row: Int) {
        spreadsheetEngine.pushUndo("Unmerge Banner")
        updateUndoRedoState()
        viewModelScope.launch {
            spreadsheetEngine.unmergeRow(row)
            _gridRefreshTrigger.value += 1
            autoSaveCurrentFile()
            ttsManager.speak("Unmerged banner at row ${row + 1}")
        }
    }

    fun setHeaderRow(row: Int) {
        spreadsheetEngine.pushUndo("Set Header Row")
        spreadsheetEngine.setHeaderRow(row)
        updateUndoRedoState()
        viewModelScope.launch {
            _gridRefreshTrigger.value += 1
            autoSaveCurrentFile()
            ttsManager.speak("Set row ${row + 1} as header")
        }
    }

    fun clearHeaderRow(row: Int) {
        spreadsheetEngine.pushUndo("Clear Header Row")
        spreadsheetEngine.clearHeaderRow(row)
        updateUndoRedoState()
        viewModelScope.launch {
            _gridRefreshTrigger.value += 1
            autoSaveCurrentFile()
            ttsManager.speak("Cleared header row ${row + 1}")
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
            val (r, c) = selectedCell
            val colName = spreadsheetEngine.getColumnName(c)
            spreadsheetEngine.pushUndo("Voice input $colName${r + 1}")
            updateUndoRedoState()
            viewModelScope.launch {
                spreadsheetEngine.setCell(r, c, text)
                _gridRefreshTrigger.value += 1
                autoSaveCurrentFile()
                if (appSettings.value.speakAfterEditing) {
                    val rowNum = r + 1
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
        val colLetter = spreadsheetEngine.getColumnName(col)
        val colHeader = spreadsheetEngine.getColumnHeaderName(col, 0)
        val values = mutableListOf<String>()
        var totalNonEmpty = 0
        val limit = 50
        for (r in 0 until spreadsheetEngine.maxRow) {
            val v = spreadsheetEngine.getCellValue(r, col).trim()
            if (v.isNotEmpty()) {
                totalNonEmpty++
                if (values.size < limit) {
                    values.add(v)
                }
            }
        }
        val headerPrefix = if (colHeader.isNotEmpty() && !colHeader.equals("Column $colLetter", ignoreCase = true)) {
            "Column $colLetter, $colHeader"
        } else {
            "Column $colLetter"
        }
        val speechText = if (values.isEmpty()) {
            "$headerPrefix. Empty column."
        } else {
            val moreText = if (totalNonEmpty > limit) ", and ${totalNonEmpty - limit} more" else ""
            "$headerPrefix. Values: ${values.joinToString(", ")}$moreText."
        }
        ttsManager.speak(speechText)
    }

    fun speakRow(row: Int) {
        if (spreadsheetEngine.isBannerRow(row)) {
            val text = spreadsheetEngine.getCellValue(row, 0)
            ttsManager.speak("Section text: $text")
            return
        }
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
    private var copiedColumnValues: List<String>? = null
    private var copiedColumnColors: Map<Int, Int>? = null
    private var copiedColumnTextColors: Map<Int, Int>? = null

    fun copyCell(context: android.content.Context, row: Int, col: Int) {
        val value = spreadsheetEngine.getCellFormulaOrValue(row, col)
        copiedCellBgColor = spreadsheetEngine.getCellColor(row, col)
        copiedCellTextColor = spreadsheetEngine.getCellTextColor(row, col)
        copiedColumnValues = null
        copiedColumnColors = null
        copiedColumnTextColors = null
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

    fun copyColumn(context: android.content.Context, col: Int) {
        val colLetter = spreadsheetEngine.getColumnName(col)
        val colHeader = spreadsheetEngine.getColumnHeaderName(col, 0)
        val values = (0 until spreadsheetEngine.maxRow).map { r ->
            spreadsheetEngine.getCellFormulaOrValue(r, col)
        }
        val lastNonEmpty = values.indexOfLast { it.isNotEmpty() }
        val trimmedValues = if (lastNonEmpty >= 0) values.subList(0, lastNonEmpty + 1) else emptyList()
        val clipText = trimmedValues.joinToString("\n")
        
        copiedColumnValues = trimmedValues
        val colColors = mutableMapOf<Int, Int>()
        val colTextColors = mutableMapOf<Int, Int>()
        for (r in 0 until spreadsheetEngine.maxRow) {
            spreadsheetEngine.getCellColor(r, col)?.let { colColors[r] = it }
            spreadsheetEngine.getCellTextColor(r, col)?.let { colTextColors[r] = it }
        }
        copiedColumnColors = colColors
        copiedColumnTextColors = colTextColors
        copiedCellBgColor = null
        copiedCellTextColor = null

        val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as? android.content.ClipboardManager
        val clip = android.content.ClipData.newPlainText("Column Content", clipText)
        clipboard?.setPrimaryClip(clip)

        val headerName = if (colHeader.isNotEmpty() && !colHeader.equals("Column $colLetter", ignoreCase = true)) {
            "Column $colLetter, $colHeader"
        } else {
            "Column $colLetter"
        }
        ttsManager.speak("Copied entire $headerName, ${trimmedValues.size} rows")
    }

    fun pasteColumn(context: android.content.Context, targetCol: Int, startRow: Int = 0) {
        val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as? android.content.ClipboardManager
        val item = clipboard?.primaryClip?.getItemAt(0)
        val text = item?.text?.toString() ?: ""
        val colLetter = spreadsheetEngine.getColumnName(targetCol)
        val colHeader = spreadsheetEngine.getColumnHeaderName(targetCol, 0)
        val headerName = if (colHeader.isNotEmpty() && !colHeader.equals("Column $colLetter", ignoreCase = true)) {
            "Column $colLetter, $colHeader"
        } else {
            "Column $colLetter"
        }

        if (text.isNotEmpty()) {
            val lines = text.split("\n")
            spreadsheetEngine.pushUndo("Paste Column into $headerName")
            for (i in lines.indices) {
                val targetR = startRow + i
                if (targetR < spreadsheetEngine.maxRow) {
                    spreadsheetEngine.setCell(targetR, targetCol, lines[i])
                    copiedColumnColors?.get(i)?.let { spreadsheetEngine.setCellColor(targetR, targetCol, it) }
                    copiedColumnTextColors?.get(i)?.let { spreadsheetEngine.setCellTextColor(targetR, targetCol, it) }
                }
            }
            updateUndoRedoState()
            _gridRefreshTrigger.value += 1
            autoSaveCurrentFile()
            ttsManager.speak("Pasted ${lines.size} values into $headerName")
        } else {
            ttsManager.speak("Clipboard is empty")
        }
    }

    fun pasteCell(context: android.content.Context, row: Int, col: Int) {
        val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as? android.content.ClipboardManager
        val item = clipboard?.primaryClip?.getItemAt(0)
        val text = item?.text?.toString() ?: ""
        val colName = spreadsheetEngine.getColumnName(col)
        val cellName = "$colName${row + 1}"

        if (text.contains("\n")) {
            val lines = text.split("\n")
            spreadsheetEngine.pushUndo("Paste Column into $cellName")
            for (i in lines.indices) {
                val targetR = row + i
                if (targetR < spreadsheetEngine.maxRow) {
                    spreadsheetEngine.setCell(targetR, col, lines[i])
                    copiedColumnColors?.get(i)?.let { spreadsheetEngine.setCellColor(targetR, col, it) }
                    copiedColumnTextColors?.get(i)?.let { spreadsheetEngine.setCellTextColor(targetR, col, it) }
                }
            }
            updateUndoRedoState()
            _gridRefreshTrigger.value += 1
            autoSaveCurrentFile()
            ttsManager.speak("Pasted ${lines.size} rows starting at $cellName")
            return
        }

        if (text.isNotEmpty() || copiedCellBgColor != null || copiedCellTextColor != null) {
            spreadsheetEngine.pushUndo("Paste into $cellName")
            if (text.isNotEmpty()) {
                spreadsheetEngine.setCell(row, col, text)
            }
            if (copiedCellBgColor != null) {
                spreadsheetEngine.setCellColor(row, col, copiedCellBgColor)
            }
            if (copiedCellTextColor != null) {
                spreadsheetEngine.setCellTextColor(row, col, copiedCellTextColor)
            }
            updateUndoRedoState()
            _gridRefreshTrigger.value += 1
            autoSaveCurrentFile()
            ttsManager.speak("Pasted into $cellName")
        } else {
            ttsManager.speak("Clipboard is empty")
        }
    }

    fun setCellColor(r: Int, c: Int, color: Int?) {
        val colName = spreadsheetEngine.getColumnName(c)
        val cellName = "$colName${r + 1}"
        spreadsheetEngine.pushUndo("Background color $cellName")
        spreadsheetEngine.setCellColor(r, c, color)
        updateUndoRedoState()
        _gridRefreshTrigger.value += 1
        autoSaveCurrentFile()
        if (color != null) {
            ttsManager.speak("Background color set for $cellName")
        } else {
            ttsManager.speak("Background color cleared for $cellName")
        }
    }

    fun setCellTextColor(r: Int, c: Int, color: Int?) {
        val colName = spreadsheetEngine.getColumnName(c)
        val cellName = "$colName${r + 1}"
        spreadsheetEngine.pushUndo("Text color $cellName")
        spreadsheetEngine.setCellTextColor(r, c, color)
        updateUndoRedoState()
        _gridRefreshTrigger.value += 1
        autoSaveCurrentFile()
        if (color != null) {
            ttsManager.speak("Text color set for $cellName")
        } else {
            ttsManager.speak("Text color cleared for $cellName")
        }
    }

    fun setColumnColor(c: Int, color: Int?) {
        val colName = spreadsheetEngine.getColumnName(c)
        val headerName = spreadsheetEngine.getColumnHeaderName(c)
        spreadsheetEngine.pushUndo("Column color $colName")
        spreadsheetEngine.setColumnColor(c, color)
        updateUndoRedoState()
        _gridRefreshTrigger.value += 1
        autoSaveCurrentFile()
        if (color != null) {
            ttsManager.speak("Background color set for column $colName $headerName")
        } else {
            ttsManager.speak("Background color cleared for column $colName $headerName")
        }
    }

    fun setColumnTextColor(c: Int, color: Int?) {
        val colName = spreadsheetEngine.getColumnName(c)
        val headerName = spreadsheetEngine.getColumnHeaderName(c)
        spreadsheetEngine.pushUndo("Column text color $colName")
        spreadsheetEngine.setColumnTextColor(c, color)
        updateUndoRedoState()
        _gridRefreshTrigger.value += 1
        autoSaveCurrentFile()
        if (color != null) {
            ttsManager.speak("Text color set for column $colName $headerName")
        } else {
            ttsManager.speak("Text color cleared for column $colName $headerName")
        }
    }

    fun setRowColor(r: Int, color: Int?) {
        val rowNum = r + 1
        spreadsheetEngine.pushUndo("Row color $rowNum")
        spreadsheetEngine.setRowColor(r, color)
        updateUndoRedoState()
        _gridRefreshTrigger.value += 1
        autoSaveCurrentFile()
        if (color != null) {
            ttsManager.speak("Background color set for row $rowNum")
        } else {
            ttsManager.speak("Background color cleared for row $rowNum")
        }
    }

    fun setRowTextColor(r: Int, color: Int?) {
        val rowNum = r + 1
        spreadsheetEngine.pushUndo("Row text color $rowNum")
        spreadsheetEngine.setRowTextColor(r, color)
        updateUndoRedoState()
        _gridRefreshTrigger.value += 1
        autoSaveCurrentFile()
        if (color != null) {
            ttsManager.speak("Text color set for row $rowNum")
        } else {
            ttsManager.speak("Text color cleared for row $rowNum")
        }
    }

    fun deleteCell(row: Int, col: Int) {
        val colName = spreadsheetEngine.getColumnName(col)
        val cellName = "$colName${row + 1}"
        spreadsheetEngine.pushUndo("Delete $cellName")
        spreadsheetEngine.setCell(row, col, "")
        spreadsheetEngine.setCellColor(row, col, null)
        spreadsheetEngine.setCellTextColor(row, col, null)
        updateUndoRedoState()
        _gridRefreshTrigger.value += 1
        autoSaveCurrentFile()
        ttsManager.speak("Deleted cell $cellName")
    }

    fun undo() {
        val desc = spreadsheetEngine.undo()
        updateUndoRedoState()
        if (desc != null) {
            _gridRefreshTrigger.value += 1
            autoSaveCurrentFile()
            ttsManager.speak("Undone: $desc")
        } else {
            ttsManager.speak("Nothing to undo")
        }
    }

    fun redo() {
        val desc = spreadsheetEngine.redo()
        updateUndoRedoState()
        if (desc != null) {
            _gridRefreshTrigger.value += 1
            autoSaveCurrentFile()
            ttsManager.speak("Redone: $desc")
        } else {
            ttsManager.speak("Nothing to redo")
        }
    }

    fun fillDown(startR: Int, startC: Int, endR: Int, endC: Int) {
        spreadsheetEngine.pushUndo("Fill Down")
        spreadsheetEngine.fillDown(startR, startC, endR, endC)
        updateUndoRedoState()
        _gridRefreshTrigger.value += 1
        autoSaveCurrentFile()
        ttsManager.speak("Filled down")
    }

    fun fillRight(startR: Int, startC: Int, endR: Int, endC: Int) {
        spreadsheetEngine.pushUndo("Fill Right")
        spreadsheetEngine.fillRight(startR, startC, endR, endC)
        updateUndoRedoState()
        _gridRefreshTrigger.value += 1
        autoSaveCurrentFile()
        ttsManager.speak("Filled right")
    }

    fun pasteSpecial(targetR: Int, targetC: Int, sourceR: Int, sourceC: Int, mode: SpreadsheetEngine.PasteMode) {
        spreadsheetEngine.pushUndo("Paste Special")
        spreadsheetEngine.pasteSpecial(targetR, targetC, sourceR, sourceC, mode)
        updateUndoRedoState()
        _gridRefreshTrigger.value += 1
        autoSaveCurrentFile()
        ttsManager.speak("Paste special applied")
    }

    fun findAndReplace(find: String, replace: String, matchCase: Boolean) {
        spreadsheetEngine.pushUndo("Find and Replace")
        val count = spreadsheetEngine.findAndReplace(find, replace, matchCase)
        updateUndoRedoState()
        _gridRefreshTrigger.value += 1
        autoSaveCurrentFile()
        ttsManager.speak("Replaced $count occurrences")
    }

    fun replaceSingleMatch(r: Int, c: Int, find: String, replace: String, matchCase: Boolean): Boolean {
        val replaced = spreadsheetEngine.replaceSingleMatch(r, c, find, replace, matchCase)
        if (replaced) {
            updateUndoRedoState()
            _gridRefreshTrigger.value += 1
            autoSaveCurrentFile()
            ttsManager.speak("Replaced in cell ${spreadsheetEngine.getColumnName(c)}${r + 1}")
        }
        return replaced
    }

    fun setCellBold(r: Int, c: Int, bold: Boolean) {
        spreadsheetEngine.pushUndo("Toggle Bold")
        spreadsheetEngine.setCellBold(r, c, bold)
        updateUndoRedoState()
        _gridRefreshTrigger.value += 1
        autoSaveCurrentFile()
        ttsManager.speak(if (bold) "Bold applied" else "Bold removed")
    }

    fun setCellItalic(r: Int, c: Int, italic: Boolean) {
        spreadsheetEngine.pushUndo("Toggle Italic")
        spreadsheetEngine.setCellItalic(r, c, italic)
        updateUndoRedoState()
        _gridRefreshTrigger.value += 1
        autoSaveCurrentFile()
        ttsManager.speak(if (italic) "Italic applied" else "Italic removed")
    }

    fun setCellAlignment(r: Int, c: Int, align: Int) {
        spreadsheetEngine.pushUndo("Change Alignment")
        spreadsheetEngine.setCellAlignment(r, c, align)
        updateUndoRedoState()
        _gridRefreshTrigger.value += 1
        autoSaveCurrentFile()
        val alignName = when(align) { 1 -> "Center"; 2 -> "Right"; else -> "Left" }
        ttsManager.speak("Aligned $alignName")
    }

    fun setCellNumberFormat(r: Int, c: Int, fmt: String) {
        spreadsheetEngine.pushUndo("Change Number Format")
        spreadsheetEngine.setCellNumberFormat(r, c, fmt)
        updateUndoRedoState()
        _gridRefreshTrigger.value += 1
        autoSaveCurrentFile()
        ttsManager.speak("Format set to $fmt")
    }

    fun setCellBorders(r: Int, c: Int, border: Int) {
        spreadsheetEngine.pushUndo("Change Borders")
        spreadsheetEngine.setCellBorders(r, c, border)
        updateUndoRedoState()
        _gridRefreshTrigger.value += 1
        autoSaveCurrentFile()
        ttsManager.speak("Borders updated")
    }

    fun setFreezePanes(rows: Int, cols: Int) {
        spreadsheetEngine.frozenRows = rows
        spreadsheetEngine.frozenCols = cols
        _gridRefreshTrigger.value += 1
        ttsManager.speak("Freeze panes updated")
    }

    override fun onCleared() {
        super.onCleared()
        ttsManager.shutdown()
    }
}
