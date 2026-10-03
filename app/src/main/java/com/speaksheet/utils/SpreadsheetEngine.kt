package com.speaksheet.utils

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.opencsv.CSVParserBuilder
import com.opencsv.CSVReader
import com.opencsv.CSVReaderBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.File
import java.io.InputStream
import java.io.InputStreamReader
import java.io.OutputStream
import java.io.OutputStreamWriter
import java.util.Locale
import java.util.Date
import java.text.SimpleDateFormat
import java.math.BigDecimal
import java.math.RoundingMode
import kotlin.math.*
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

class SpreadsheetEngine {

    data class CellRange(
        var startRow: Int,
        var startCol: Int,
        var endRow: Int,
        var endCol: Int
    ) {
        fun contains(r: Int, c: Int): Boolean =
            r in startRow..endRow && c in startCol..endCol

        fun isTopLeft(r: Int, c: Int): Boolean =
            r == startRow && c == startCol
    }

    data class CellData(
        var raw: String = "",
        var evaluated: String? = null
    )

    data class EngineState(
        val cells: Map<Long, CellData>,
        val cellColors: Map<Long, Int>,
        val cellTextColors: Map<Long, Int>,
        val cellBold: Map<Long, Boolean>,
        val cellItalic: Map<Long, Boolean>,
        val cellAlign: Map<Long, Int>,
        val cellNumFmt: Map<Long, String>,
        val cellBorders: Map<Long, Int>,
        val rowColors: Map<Int, Int>,
        val rowTextColors: Map<Int, Int>,
        val columnColors: Map<Int, Int>,
        val columnTextColors: Map<Int, Int>,
        val maxRow: Int,
        val maxCol: Int,
        val mergedRanges: Set<CellRange>,
        val headerRows: Set<Int>,
        val headerBgColor: Int? = null,
        val headerTextColor: Int? = null,
        val description: String
    )

    data class SheetState(
        var name: String = "Sheet1",
        var maxRow: Int = 50,
        var maxCol: Int = 15,
        var frozenRows: Int = 0,
        var frozenCols: Int = 0,
        var headerBgColor: Int? = null,
        var headerTextColor: Int? = null,
        var zoom: Float = 1.0f,
        var scrollX: Float = 0f,
        var scrollY: Float = 0f,
        val cells: HashMap<Long, CellData> = HashMap(),
        val cellColors: HashMap<Long, Int> = HashMap(),
        val cellTextColors: HashMap<Long, Int> = HashMap(),
        val columnColors: HashMap<Int, Int> = HashMap(),
        val columnTextColors: HashMap<Int, Int> = HashMap(),
        val rowColors: HashMap<Int, Int> = HashMap(),
        val rowTextColors: HashMap<Int, Int> = HashMap(),
        val cellBold: HashMap<Long, Boolean> = HashMap(),
        val cellItalic: HashMap<Long, Boolean> = HashMap(),
        val cellAlign: HashMap<Long, Int> = HashMap(),
        val cellNumFmt: HashMap<Long, String> = HashMap(),
        val cellBorders: HashMap<Long, Int> = HashMap(),
        var wrapEnabled: BooleanArray = BooleanArray(15),
        val mergedRanges: HashSet<CellRange> = HashSet(),
        val headerRows: HashSet<Int> = HashSet(),
        val undoStack: ArrayDeque<EngineState> = ArrayDeque(),
        val redoStack: ArrayDeque<EngineState> = ArrayDeque()
    )

    val sheets = ArrayList<SheetState>().apply { add(SheetState("Sheet1")) }
    var currentSheetIndex = 0

    val currentSheet: SheetState
        get() = sheets.getOrElse(currentSheetIndex) { sheets.first() }

    var maxRow: Int
        get() = currentSheet.maxRow
        set(value) { currentSheet.maxRow = value }

    var maxCol: Int
        get() = currentSheet.maxCol
        set(value) { currentSheet.maxCol = value }

    var frozenRows: Int
        get() = currentSheet.frozenRows
        set(value) { currentSheet.frozenRows = value }

    var frozenCols: Int
        get() = currentSheet.frozenCols
        set(value) { currentSheet.frozenCols = value }

    val headerBgColor: Int?
        get() = currentSheet.headerBgColor

    val headerTextColor: Int?
        get() = currentSheet.headerTextColor

    val cells: HashMap<Long, CellData>
        get() = currentSheet.cells

    val cellColors: HashMap<Long, Int>
        get() = currentSheet.cellColors

    val cellTextColors: HashMap<Long, Int>
        get() = currentSheet.cellTextColors

    val columnColors: HashMap<Int, Int>
        get() = currentSheet.columnColors

    val columnTextColors: HashMap<Int, Int>
        get() = currentSheet.columnTextColors

    val rowColors: HashMap<Int, Int>
        get() = currentSheet.rowColors

    val rowTextColors: HashMap<Int, Int>
        get() = currentSheet.rowTextColors

    val cellBold: HashMap<Long, Boolean>
        get() = currentSheet.cellBold

    val cellItalic: HashMap<Long, Boolean>
        get() = currentSheet.cellItalic

    val cellAlign: HashMap<Long, Int>
        get() = currentSheet.cellAlign

    val cellNumFmt: HashMap<Long, String>
        get() = currentSheet.cellNumFmt

    val cellBorders: HashMap<Long, Int>
        get() = currentSheet.cellBorders

    val mergedRanges: HashSet<CellRange>
        get() = currentSheet.mergedRanges

    val headerRows: HashSet<Int>
        get() = currentSheet.headerRows

    var wrapEnabled: BooleanArray
        get() = currentSheet.wrapEnabled
        set(value) { currentSheet.wrapEnabled = value }

    val undoStack: ArrayDeque<EngineState>
        get() = currentSheet.undoStack

    val redoStack: ArrayDeque<EngineState>
        get() = currentSheet.redoStack

    val canUndo: Boolean get() = undoStack.isNotEmpty()
    val canRedo: Boolean get() = redoStack.isNotEmpty()

    val defaultRowHeightDp = 32f
    val defaultColWidthDp = 90f

    private val cellRightAlignedCache = HashMap<Long, Boolean>()
    private val formulaCellKeys = HashSet<Long>()
    private val evaluatingCells = HashSet<Long>()
    private val spillOutputs = HashMap<Long, String>()
    private val spillSources = HashMap<Long, Long>()

    private var rowOffsetsPx = FloatArray(0)
    private var rowHeightsPx = FloatArray(0)
    private var colOffsetsPx = FloatArray(0)
    private var colWidthsDp = FloatArray(0)
    val customColWidthsDp = HashMap<Int, Float>()
    val customRowHeightsDp = HashMap<Int, Float>()
    private var isFullLayoutDirty = true
    private val dirtyColumns = HashSet<Int>()
    private val dirtyRows = HashSet<Int>()

    var currentDensity = 1f
        private set
    var currentZoom = 1.0f
        private set
    var currentLargeTouch = false
        private set

    var totalWidthPx = 0f
        private set
    var totalHeightPx = 0f
        private set

    fun markStructureDirty() {
        isFullLayoutDirty = true
    }

    fun checkAutoExtend(scrollX: Float, scrollY: Float, viewW: Float, viewH: Float, zoom: Float): Boolean {
        if (zoom <= 0.01f || viewW <= 0f || viewH <= 0f) return false
        val visibleRightPx = (-scrollX + viewW) / zoom
        val visibleBottomPx = (-scrollY + viewH) / zoom
        val screenDistWPx = viewW / zoom
        val screenDistHPx = viewH / zoom

        var changed = false
        if (totalHeightPx > 0 && totalHeightPx - visibleBottomPx < screenDistHPx && maxRow < 1000) {
            maxRow = minOf(maxRow + 25, 1000)
            changed = true
        }
        if (totalWidthPx > 0 && totalWidthPx - visibleRightPx < screenDistWPx && maxCol < 50) {
            maxCol = minOf(maxCol + 5, 50)
            changed = true
        }
        if (changed) {
            markStructureDirty()
        }
        return changed
    }

    fun getSheetNames(): List<String> = sheets.map { it.name }

    fun getActiveSheetName(): String = currentSheet.name

    fun addSheet(name: String = ""): Int {
        val sheetName = if (name.isNotBlank()) name else {
            var counter = sheets.size + 1
            var candidate = "Sheet$counter"
            while (sheets.any { it.name.equals(candidate, ignoreCase = true) }) {
                counter++
                candidate = "Sheet$counter"
            }
            candidate
        }
        val newSheet = SheetState(name = sheetName, maxRow = 50, maxCol = 15, wrapEnabled = BooleanArray(15))
        sheets.add(newSheet)
        switchSheet(sheets.lastIndex)
        return sheets.lastIndex
    }

    fun switchSheet(index: Int): Boolean {
        if (index in sheets.indices && index != currentSheetIndex) {
            currentSheetIndex = index
            clearCellCaches()
            markStructureDirty()
            recalculateAllFormulas()
            return true
        }
        return false
    }

    fun renameSheet(index: Int, newName: String): Boolean {
        if (index in sheets.indices && newName.isNotBlank()) {
            sheets[index].name = newName
            return true
        }
        return false
    }

    fun deleteSheet(index: Int): Boolean {
        if (sheets.size <= 1) return false
        if (index in sheets.indices) {
            sheets.removeAt(index)
            if (currentSheetIndex >= sheets.size) {
                currentSheetIndex = sheets.size - 1
            }
            clearCellCaches()
            markStructureDirty()
            recalculateAllFormulas()
            return true
        }
        return false
    }

    private fun cellKey(r: Int, c: Int): Long = (r.toLong() shl 32) or (c.toLong() and 0xFFFFFFFFL)

    fun isWrapEnabled(col: Int): Boolean {
        return if (col in wrapEnabled.indices) wrapEnabled[col] else false
    }

    fun setColumnWrap(col: Int, enabled: Boolean) {
        if (col >= wrapEnabled.size) {
            val newArr = BooleanArray(maxOf(col + 1, maxCol))
            wrapEnabled.copyInto(newArr)
            wrapEnabled = newArr
        }
        if (wrapEnabled[col] != enabled) {
            wrapEnabled[col] = enabled
            isFullLayoutDirty = true
        }
    }

    fun toggleColumnWrap(col: Int): Boolean {
        val newState = !isWrapEnabled(col)
        setColumnWrap(col, newState)
        return newState
    }

    fun setCellColor(r: Int, c: Int, color: Int?) {
        val key = cellKey(r, c)
        if (color != null) {
            cellColors[key] = color
        } else {
            cellColors.remove(key)
        }
    }

    fun getCellColor(r: Int, c: Int): Int? = cellColors[cellKey(r, c)]

    fun setCellTextColor(r: Int, c: Int, color: Int?) {
        val key = cellKey(r, c)
        if (color != null) {
            cellTextColors[key] = color
        } else {
            cellTextColors.remove(key)
        }
    }

    fun getCellTextColor(r: Int, c: Int): Int? = cellTextColors[cellKey(r, c)]

    fun setColumnColor(c: Int, color: Int?) {
        if (color != null) {
            columnColors[c] = color
        } else {
            columnColors.remove(c)
        }
    }

    fun getColumnColor(c: Int): Int? = columnColors[c]

    fun setColumnTextColor(c: Int, color: Int?) {
        if (color != null) {
            columnTextColors[c] = color
        } else {
            columnTextColors.remove(c)
        }
    }

    fun getColumnTextColor(c: Int): Int? = columnTextColors[c]

    fun setRowColor(r: Int, color: Int?) {
        if (color != null) {
            rowColors[r] = color
        } else {
            rowColors.remove(r)
        }
    }

    fun getRowColor(r: Int): Int? = rowColors[r]

    fun setRowTextColor(r: Int, color: Int?) {
        if (color != null) {
            rowTextColors[r] = color
        } else {
            rowTextColors.remove(r)
        }
    }

    fun getRowTextColor(r: Int): Int? = rowTextColors[r]

    fun serializeColors(): String {
        val sb = StringBuilder()
        sb.append("[CELL_COLORS]\n")
        for ((key, color) in cellColors) {
            val r = (key ushr 32).toInt()
            val c = (key and 0xFFFFFFFFL).toInt()
            sb.append("$r,$c,$color\n")
        }
        sb.append("[CELL_TEXT_COLORS]\n")
        for ((key, color) in cellTextColors) {
            val r = (key ushr 32).toInt()
            val c = (key and 0xFFFFFFFFL).toInt()
            sb.append("$r,$c,$color\n")
        }
        sb.append("[COLUMN_COLORS]\n")
        for ((c, color) in columnColors) {
            sb.append("$c,$color\n")
        }
        sb.append("[COLUMN_TEXT_COLORS]\n")
        for ((c, color) in columnTextColors) {
            sb.append("$c,$color\n")
        }
        sb.append("[ROW_COLORS]\n")
        for ((r, color) in rowColors) {
            sb.append("$r,$color\n")
        }
        sb.append("[ROW_TEXT_COLORS]\n")
        for ((r, color) in rowTextColors) {
            sb.append("$r,$color\n")
        }
        sb.append("[MERGED_RANGES]\n")
        for (range in mergedRanges) {
            sb.append("${range.startRow},${range.startCol},${range.endRow},${range.endCol}\n")
        }
        sb.append("[HEADER_ROWS]\n")
        for (r in headerRows) {
            sb.append("$r\n")
        }
        headerBgColor?.let { sb.append("[HEADER_BG_COLOR]\n$it\n") }
        headerTextColor?.let { sb.append("[HEADER_TEXT_COLOR]\n$it\n") }
        return sb.toString()
    }

    fun deserializeColors(content: String) {
        cellColors.clear()
        cellTextColors.clear()
        columnColors.clear()
        columnTextColors.clear()
        rowColors.clear()
        rowTextColors.clear()
        mergedRanges.clear()
        headerRows.clear()
        var section = ""
        for (rawLine in content.lines()) {
            val line = rawLine.trim()
            if (line.isEmpty() || line.startsWith("#")) continue
            if (line.startsWith("[") && line.endsWith("]")) {
                section = line.uppercase(Locale.ROOT)
                continue
            }
            val parts = line.split(",")
            if (section == "[CELL_COLORS]" && parts.size >= 3) {
                val r = parts[0].trim().toIntOrNull()
                val c = parts[1].trim().toIntOrNull()
                val color = parts[2].trim().toIntOrNull()
                if (r != null && c != null && color != null) {
                    cellColors[cellKey(r, c)] = color
                }
            } else if (section == "[CELL_TEXT_COLORS]" && parts.size >= 3) {
                val r = parts[0].trim().toIntOrNull()
                val c = parts[1].trim().toIntOrNull()
                val color = parts[2].trim().toIntOrNull()
                if (r != null && c != null && color != null) {
                    cellTextColors[cellKey(r, c)] = color
                }
            } else if (section == "[COLUMN_COLORS]" && parts.size >= 2) {
                val c = parts[0].trim().toIntOrNull()
                val color = parts[1].trim().toIntOrNull()
                if (c != null && color != null) {
                    columnColors[c] = color
                }
            } else if (section == "[COLUMN_TEXT_COLORS]" && parts.size >= 2) {
                val c = parts[0].trim().toIntOrNull()
                val color = parts[1].trim().toIntOrNull()
                if (c != null && color != null) {
                    columnTextColors[c] = color
                }
            } else if (section == "[ROW_COLORS]" && parts.size >= 2) {
                val r = parts[0].trim().toIntOrNull()
                val color = parts[1].trim().toIntOrNull()
                if (r != null && color != null) {
                    rowColors[r] = color
                }
            } else if (section == "[ROW_TEXT_COLORS]" && parts.size >= 2) {
                val r = parts[0].trim().toIntOrNull()
                val color = parts[1].trim().toIntOrNull()
                if (r != null && color != null) {
                    rowTextColors[r] = color
                }
            } else if (section == "[MERGED_RANGES]" && parts.size >= 4) {
                val sR = parts[0].trim().toIntOrNull()
                val sC = parts[1].trim().toIntOrNull()
                val eR = parts[2].trim().toIntOrNull()
                val eC = parts[3].trim().toIntOrNull()
                if (sR != null && sC != null && eR != null && eC != null) {
                    mergedRanges.add(CellRange(sR, sC, eR, eC))
                }
            } else if (section == "[DIVIDER_ROWS]" && parts.size >= 1) {
                val r = parts[0].trim().toIntOrNull()
                if (r != null) {
                    mergedRanges.add(CellRange(r, 0, r, maxCol - 1))
                }
            } else if (section == "[HEADER_ROWS]" && parts.isNotEmpty()) {
                val r = parts[0].trim().toIntOrNull()
                if (r != null) {
                    headerRows.add(r)
                }
            } else if ((section == "[HEADER_BG_COLOR]" || section == "[HEADER_COLOR]") && parts.isNotEmpty()) {
                val color = parts[0].trim().toIntOrNull()
                if (color != null) {
                    currentSheet.headerBgColor = color
                }
            } else if (section == "[HEADER_TEXT_COLOR]" && parts.isNotEmpty()) {
                val color = parts[0].trim().toIntOrNull()
                if (color != null) {
                    currentSheet.headerTextColor = color
                }
            }
        }
    }

    fun clearCellCaches() {
        cellRightAlignedCache.clear()
        for (fKey in formulaCellKeys) {
            cells[fKey]?.evaluated = null
        }
        spillOutputs.clear()
        spillSources.clear()
        dirtyColumns.clear()
        dirtyRows.clear()
    }

    fun isSpilledCell(r: Int, c: Int): Boolean = spillOutputs.containsKey(cellKey(r, c))

    fun getSpillSource(r: Int, c: Int): Pair<Int, Int>? {
        val sKey = spillSources[cellKey(r, c)] ?: return null
        return Pair((sKey ushr 32).toInt(), (sKey and 0xFFFFFFFFL).toInt())
    }

    fun formatDisplayValue(raw: String, fmt: String): String {
        if (raw.startsWith("#") || raw.isEmpty()) return raw
        val clean = raw.trim().removePrefix("$").removeSuffix("%").replace(",", "")
        val num = clean.toDoubleOrNull() ?: return raw
        return when (fmt) {
            "Currency" -> {
                if (num < 0) "-$" + String.format(Locale.US, "%,.2f", -num)
                else "$" + String.format(Locale.US, "%,.2f", num)
            }
            "Percent" -> {
                val pct = if (num in -1.0..1.0 && num != 0.0) num * 100.0 else num
                String.format(Locale.US, "%.1f%%", pct)
            }
            "Number" -> {
                if (num == num.toLong().toDouble()) {
                    String.format(Locale.US, "%,d", num.toLong())
                } else {
                    String.format(Locale.US, "%,.2f", num)
                }
            }
            "Date" -> {
                try {
                    if (num > 100000000000L) {
                        val sdf = java.text.SimpleDateFormat("yyyy-MM-dd", Locale.US)
                        sdf.format(java.util.Date(num.toLong()))
                    } else if (num in 1.0..100000.0) {
                        val millis = (num - 25569.0) * 86400000.0
                        val sdf = java.text.SimpleDateFormat("yyyy-MM-dd", Locale.US)
                        sdf.format(java.util.Date(millis.toLong()))
                    } else {
                        raw
                    }
                } catch (_: Exception) {
                    raw
                }
            }
            else -> raw
        }
    }

    fun getCellValue(r: Int, c: Int): String {
        if (r < 0 || c < 0) return ""
        val key = cellKey(r, c)
        if (r >= maxRow || c >= maxCol) {
            val cell = cells[key]
            if (cell == null || cell.raw.isEmpty()) {
                return spillOutputs[key] ?: ""
            }
        }
        val cell = cells[key]
        val rawVal = if (cell != null && cell.raw.isNotEmpty()) {
            if (!cell.raw.startsWith("=")) {
                cell.raw
            } else {
                cell.evaluated ?: run {
                    if (!evaluatingCells.add(key)) {
                        "#CIRCULAR!"
                    } else {
                        try {
                            val eval = evaluateFormula(cell.raw, r, c)
                            cell.evaluated = eval
                            eval
                        } finally {
                            evaluatingCells.remove(key)
                        }
                    }
                }
            }
        } else {
            spillOutputs[key] ?: ""
        }
        val fmt = getCellNumberFormat(r, c)
        return if (fmt != "General") formatDisplayValue(rawVal, fmt) else rawVal
    }

    fun getCellFormulaOrValue(r: Int, c: Int): String {
        if (r < 0 || c < 0) return ""
        val targetC = if (isBannerRow(r)) 0 else c
        return cells[cellKey(r, targetC)]?.raw ?: ""
    }

    fun recalculateAllFormulas() {
        spillOutputs.clear()
        spillSources.clear()
        for (fKey in formulaCellKeys) {
            cells[fKey]?.evaluated = null
            cellRightAlignedCache.remove(fKey)
            val fCol = (fKey and 0xFFFFFFFFL).toInt()
            val fRow = (fKey ushr 32).toInt()
            if (fCol in 0 until maxCol) {
                dirtyColumns.add(fCol)
            }
            if (isWrapEnabled(fCol) && fRow in 0 until maxRow) {
                dirtyRows.add(fRow)
            }
        }
        val keys = formulaCellKeys.toList()
        for (fKey in keys) {
            val r = (fKey ushr 32).toInt()
            val c = (fKey and 0xFFFFFFFFL).toInt()
            getCellValue(r, c)
        }
    }

    fun setCell(r: Int, c: Int, value: String) {
        val targetC = if (isBannerRow(r)) 0 else c
        val key = cellKey(r, targetC)
        if (value.isEmpty()) {
            cells.remove(key)
            formulaCellKeys.remove(key)
        } else {
            val cell = cells.getOrPut(key) { CellData() }
            cell.raw = value
            cell.evaluated = null
            if (value.startsWith("=")) {
                formulaCellKeys.add(key)
            } else {
                formulaCellKeys.remove(key)
            }
        }
        cellRightAlignedCache.remove(key)

        if (r >= maxRow || targetC >= maxCol) {
            maxRow = maxOf(maxRow, r + 1)
            maxCol = maxOf(maxCol, targetC + 1)
            if (wrapEnabled.size < maxCol) {
                val newArr = BooleanArray(maxCol)
                wrapEnabled.copyInto(newArr)
                wrapEnabled = newArr
            }
            isFullLayoutDirty = true
        } else {
            dirtyColumns.add(targetC)
            if (isWrapEnabled(targetC) || isBannerRow(r)) {
                dirtyRows.add(r)
            }
            if (isBannerRow(r)) {
                isFullLayoutDirty = true
            }
        }

        recalculateAllFormulas()
    }

    fun isRightAligned(r: Int, c: Int): Boolean {
        val key = cellKey(r, c)
        val cached = cellRightAlignedCache[key]
        if (cached != null) return cached

        val value = getCellValue(r, c).trim()
        val isNumeric = value.isNotEmpty() && (
            value.toDoubleOrNull() != null ||
            value.startsWith("$") ||
            value.endsWith("%") ||
            value.matches(NUMERIC_REGEX)
        )
        cellRightAlignedCache[key] = isNumeric
        return isNumeric
    }

    fun newSpreadsheet(rows: Int = 100, cols: Int = 26) {
        cells.clear()
        cellColors.clear()
        cellTextColors.clear()
        columnColors.clear()
        columnTextColors.clear()
        rowColors.clear()
        rowTextColors.clear()
        mergedRanges.clear()
        headerRows.clear()
        cellRightAlignedCache.clear()
        formulaCellKeys.clear()
        evaluatingCells.clear()
        spillOutputs.clear()
        spillSources.clear()
        dirtyColumns.clear()
        dirtyRows.clear()
        maxRow = rows.coerceAtLeast(10)
        maxCol = cols.coerceAtLeast(5)
        wrapEnabled = BooleanArray(maxCol)
        frozenRows = 0
        frozenCols = 0
        rowOffsetsPx = FloatArray(0)
        rowHeightsPx = FloatArray(0)
        colOffsetsPx = FloatArray(0)
        colWidthsDp = FloatArray(0)
        isFullLayoutDirty = true
    }

    suspend fun loadFromUri(context: Context, uri: Uri) = withContext(Dispatchers.IO) {
        val type = context.contentResolver.getType(uri) ?: ""
        var fileName = ""
        if (uri.scheme == "content") {
            try {
                context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                    val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (idx != -1 && cursor.moveToFirst()) {
                        fileName = cursor.getString(idx) ?: ""
                    }
                }
            } catch (_: Throwable) {}
        }
        if (fileName.isEmpty()) {
            fileName = uri.lastPathSegment?.substringAfterLast('/') ?: (uri.path ?: "")
        }

        val isCsvOrText = fileName.endsWith(".csv", ignoreCase = true) ||
                fileName.endsWith(".tsv", ignoreCase = true) ||
                fileName.endsWith(".tab", ignoreCase = true) ||
                fileName.endsWith(".txt", ignoreCase = true) ||
                type.contains("csv", ignoreCase = true) ||
                type.contains("comma-separated", ignoreCase = true) ||
                type.contains("tab-separated", ignoreCase = true)

        try {
            if (isCsvOrText) {
                context.contentResolver.openInputStream(uri)?.use { loadCSV(it) }
            } else {
                val success = context.contentResolver.openInputStream(uri)?.use { loadXLSX(it) } ?: false
                if (!success) {
                    context.contentResolver.openInputStream(uri)?.use { loadCSV(it) }
                }
            }
        } catch (_: Throwable) {
            try {
                context.contentResolver.openInputStream(uri)?.use { loadCSV(it) }
            } catch (_: Throwable) {
                // Graceful handling
            }
        }

        if (uri.scheme == "file") {
            try {
                val file = File(uri.path ?: "")
                val colorsFile = File(file.parentFile, "${file.name}.colors")
                if (colorsFile.exists()) {
                    deserializeColors(colorsFile.readText(Charsets.UTF_8))
                }
            } catch (_: Throwable) {}
        }
    }

    internal fun loadCSV(inputStream: InputStream) {
        cells.clear()
        cellColors.clear()
        cellTextColors.clear()
        columnColors.clear()
        columnTextColors.clear()
        rowColors.clear()
        rowTextColors.clear()
        mergedRanges.clear()
        headerRows.clear()
        clearCellCaches()
        val bytes = inputStream.readBytes()
        if (bytes.isEmpty()) return

        // Auto-detect delimiter from first line (comma, tab, semicolon)
        val sampleText = String(bytes.take(2048).toByteArray(), Charsets.UTF_8)
        val firstLine = sampleText.lines().firstOrNull() ?: ""
        val commaCount = firstLine.count { it == ',' }
        val tabCount = firstLine.count { it == '\t' }
        val semicolonCount = firstLine.count { it == ';' }

        val separator = when {
            tabCount > commaCount && tabCount > semicolonCount -> '\t'
            semicolonCount > commaCount && semicolonCount > tabCount -> ';'
            else -> ','
        }

        val parser = CSVParserBuilder()
            .withSeparator(separator)
            .build()
        val reader = CSVReaderBuilder(InputStreamReader(bytes.inputStream()))
            .withCSVParser(parser)
            .build()

        var r = 0
        var maxC = 0
        reader.forEach { rowData ->
            rowData.forEachIndexed { c, value ->
                if (value.isNotEmpty()) {
                    setCell(r, c, value)
                }
                if (c > maxC) maxC = c
            }
            r++
        }
        maxRow = maxOf(30, r + 10).coerceAtMost(300)
        maxCol = maxOf(10, maxC + 4).coerceAtMost(30)
        wrapEnabled = BooleanArray(maxCol)
        isFullLayoutDirty = true
    }

    data class StyleRecord(
        val fontBold: Boolean = false,
        val fontItalic: Boolean = false,
        val fontColor: Int? = null,
        val fillColor: Int? = null,
        val border: Int = 0,
        val align: Int = 0,
        val wrapText: Boolean = false,
        val numFmt: String = "General"
    )

    class OpenXmlStyles {
        val cellXfs = ArrayList<StyleRecord>()
    }

    private fun parseStylesXml(stream: InputStream): OpenXmlStyles {
        val result = OpenXmlStyles()
        val factory = XmlPullParserFactory.newInstance()
        val parser = factory.newPullParser()
        parser.setInput(stream, "UTF-8")

        val fonts = ArrayList<Triple<Boolean, Boolean, Int?>>() // bold, italic, color
        val fills = ArrayList<Int?>() // color or null

        var event = parser.eventType
        var section = ""

        var curFontBold = false
        var curFontItalic = false
        var curFontColor: Int? = null
        var curFillColor: Int? = null

        while (event != XmlPullParser.END_DOCUMENT) {
            when (event) {
                XmlPullParser.START_TAG -> {
                    when (parser.name) {
                        "fonts" -> section = "fonts"
                        "fills" -> section = "fills"
                        "cellXfs" -> section = "cellXfs"
                        "font" -> {
                            curFontBold = false
                            curFontItalic = false
                            curFontColor = null
                        }
                        "b" -> if (section == "fonts") curFontBold = true
                        "i" -> if (section == "fonts") curFontItalic = true
                        "color" -> {
                            if (section == "fonts") {
                                val rgb = parser.getAttributeValue(null, "rgb")
                                if (rgb != null && rgb.length in 6..8) {
                                    val full = if (rgb.length == 6) "FF$rgb" else rgb
                                    curFontColor = full.toLongOrNull(16)?.toInt()
                                }
                            }
                        }
                        "fgColor" -> {
                            if (section == "fills") {
                                val rgb = parser.getAttributeValue(null, "rgb")
                                if (rgb != null && rgb.length in 6..8) {
                                    val full = if (rgb.length == 6) "FF$rgb" else rgb
                                    curFillColor = full.toLongOrNull(16)?.toInt()
                                }
                            }
                        }
                        "fill" -> {
                            curFillColor = null
                        }
                        "xf" -> {
                            if (section == "cellXfs") {
                                val fontId = parser.getAttributeValue(null, "fontId")?.toIntOrNull() ?: 0
                                val fillId = parser.getAttributeValue(null, "fillId")?.toIntOrNull() ?: 0
                                val borderId = parser.getAttributeValue(null, "borderId")?.toIntOrNull() ?: 0
                                val numFmtId = parser.getAttributeValue(null, "numFmtId")?.toIntOrNull() ?: 0

                                val fontInfo = fonts.getOrNull(fontId) ?: Triple(false, false, null)
                                val fillInfo = fills.getOrNull(fillId)

                                val numFmt = when (numFmtId) {
                                    1, 2, 3, 4 -> "Number"
                                    5, 6, 7, 8, 44, 164 -> "Currency"
                                    9, 10 -> "Percent"
                                    14, 15, 16, 17, 22 -> "Date"
                                    else -> "General"
                                }

                                result.cellXfs.add(
                                    StyleRecord(
                                        fontBold = fontInfo.first,
                                        fontItalic = fontInfo.second,
                                        fontColor = fontInfo.third,
                                        fillColor = fillInfo,
                                        border = if (borderId > 0) 1 else 0,
                                        align = 0,
                                        wrapText = false,
                                        numFmt = numFmt
                                    )
                                )
                            }
                        }
                        "alignment" -> {
                            if (section == "cellXfs" && result.cellXfs.isNotEmpty()) {
                                val lastIdx = result.cellXfs.size - 1
                                val curXf = result.cellXfs[lastIdx]
                                val horiz = parser.getAttributeValue(null, "horizontal") ?: ""
                                val wrap = parser.getAttributeValue(null, "wrapText") == "1" || parser.getAttributeValue(null, "wrapText") == "true"
                                val alignInt = when (horiz) {
                                    "center" -> 1
                                    "right" -> 2
                                    else -> 0
                                }
                                result.cellXfs[lastIdx] = curXf.copy(align = alignInt, wrapText = wrap)
                            }
                        }
                    }
                }
                XmlPullParser.END_TAG -> {
                    when (parser.name) {
                        "font" -> fonts.add(Triple(curFontBold, curFontItalic, curFontColor))
                        "fill" -> fills.add(curFillColor)
                        "fonts", "fills", "cellXfs" -> section = ""
                    }
                }
            }
            event = parser.next()
        }
        return result
    }

    private fun parseSharedStrings(stream: InputStream, list: ArrayList<String>) {
        val factory = XmlPullParserFactory.newInstance()
        val parser = factory.newPullParser()
        parser.setInput(stream, "UTF-8")

        var eventType = parser.eventType
        var inText = false
        val currentText = StringBuilder()

        while (eventType != XmlPullParser.END_DOCUMENT) {
            when (eventType) {
                XmlPullParser.START_TAG -> {
                    if (parser.name == "t") {
                        inText = true
                        currentText.clear()
                    }
                }
                XmlPullParser.TEXT -> {
                    if (inText) {
                        currentText.append(parser.text)
                    }
                }
                XmlPullParser.END_TAG -> {
                    if (parser.name == "t") {
                        inText = false
                        list.add(currentText.toString())
                    }
                }
            }
            eventType = parser.next()
        }
    }

    private fun parseSheetXmlInto(
        sheet: SheetState,
        stream: InputStream,
        sharedStrings: List<String>,
        styles: OpenXmlStyles
    ) {
        val factory = XmlPullParserFactory.newInstance()
        val parser = factory.newPullParser()
        parser.setInput(stream, "UTF-8")

        var eventType = parser.eventType
        var currentCellRef = ""
        var cellType = ""
        var styleIdx = -1
        var inValue = false
        var inFormula = false
        val cellText = StringBuilder()
        val formulaText = StringBuilder()
        var maxR = 0
        var maxC = 0

        while (eventType != XmlPullParser.END_DOCUMENT) {
            when (eventType) {
                XmlPullParser.START_TAG -> {
                    when (parser.name) {
                        "pane" -> {
                            val ySplit = parser.getAttributeValue(null, "ySplit")?.toIntOrNull() ?: 0
                            val xSplit = parser.getAttributeValue(null, "xSplit")?.toIntOrNull() ?: 0
                            sheet.frozenRows = ySplit
                            sheet.frozenCols = xSplit
                        }
                        "mergeCell" -> {
                            val ref = parser.getAttributeValue(null, "ref") ?: ""
                            val parts = ref.split(":")
                            if (parts.size == 2) {
                                val start = parseCellReference(parts[0].trim())
                                val end = parseCellReference(parts[1].trim())
                                if (start != null && end != null) {
                                    sheet.mergedRanges.add(
                                        CellRange(
                                            minOf(start.first, end.first),
                                            minOf(start.second, end.second),
                                            maxOf(start.first, end.first),
                                            maxOf(start.second, end.second)
                                        )
                                    )
                                }
                            }
                        }
                        "c" -> {
                            currentCellRef = parser.getAttributeValue(null, "r") ?: ""
                            cellType = parser.getAttributeValue(null, "t") ?: ""
                            styleIdx = parser.getAttributeValue(null, "s")?.toIntOrNull() ?: -1
                            cellText.clear()
                            formulaText.clear()
                        }
                        "v", "t" -> {
                            inValue = true
                        }
                        "f" -> {
                            inFormula = true
                            formulaText.clear()
                        }
                    }
                }
                XmlPullParser.TEXT -> {
                    if (inValue) cellText.append(parser.text)
                    if (inFormula) formulaText.append(parser.text)
                }
                XmlPullParser.END_TAG -> {
                    when (parser.name) {
                        "v", "t" -> inValue = false
                        "f" -> inFormula = false
                        "c" -> {
                            if (currentCellRef.isNotEmpty()) {
                                val coords = parseCellReference(currentCellRef)
                                if (coords != null) {
                                    val (r, c) = coords
                                    val key = cellKey(r, c)
                                    val finalVal = if (formulaText.isNotEmpty()) {
                                        "=" + formulaText.toString().trim()
                                    } else if (cellType == "s") {
                                        val idx = cellText.toString().trim().toIntOrNull()
                                        if (idx != null && idx in sharedStrings.indices) {
                                            sharedStrings[idx]
                                        } else {
                                            cellText.toString()
                                        }
                                    } else {
                                        cellText.toString()
                                    }
                                    sheet.cells[key] = CellData(raw = finalVal)
                                    if (finalVal.startsWith("=")) {
                                        formulaCellKeys.add(key)
                                    }

                                    if (styleIdx in styles.cellXfs.indices) {
                                        val st = styles.cellXfs[styleIdx]
                                        if (st.fillColor != null) sheet.cellColors[key] = st.fillColor
                                        if (st.fontColor != null) sheet.cellTextColors[key] = st.fontColor
                                        if (st.fontBold) sheet.cellBold[key] = true
                                        if (st.fontItalic) sheet.cellItalic[key] = true
                                        if (st.align != 0) sheet.cellAlign[key] = st.align
                                        if (st.border != 0) sheet.cellBorders[key] = st.border
                                        if (st.numFmt != "General") sheet.cellNumFmt[key] = st.numFmt
                                        if (st.wrapText) {
                                            if (c >= sheet.wrapEnabled.size) {
                                                val newArr = BooleanArray(c + 1)
                                                sheet.wrapEnabled.copyInto(newArr)
                                                sheet.wrapEnabled = newArr
                                            }
                                            sheet.wrapEnabled[c] = true
                                        }
                                    }

                                    if (r > maxR) maxR = r
                                    if (c > maxC) maxC = c
                                }
                            }
                        }
                    }
                }
            }
            eventType = parser.next()
        }

        sheet.maxRow = maxOf(30, maxR + 10).coerceAtMost(5000)
        sheet.maxCol = maxOf(10, maxC + 4).coerceAtMost(200)
        if (sheet.wrapEnabled.size < sheet.maxCol) {
            val newArr = BooleanArray(sheet.maxCol)
            sheet.wrapEnabled.copyInto(newArr)
            sheet.wrapEnabled = newArr
        }
    }

    fun loadXLSX(inputStream: InputStream): Boolean {
        sheets.clear()
        formulaCellKeys.clear()
        evaluatingCells.clear()
        spillOutputs.clear()
        spillSources.clear()
        cellRightAlignedCache.clear()
        clearCellCaches()

        val sharedStrings = ArrayList<String>()
        val sheetBytes = HashMap<String, ByteArray>()
        var workbookXmlBytes: ByteArray? = null
        var workbookRelsBytes: ByteArray? = null
        var stylesXmlBytes: ByteArray? = null
        var legacyColors: String? = null

        fun readEntryBytes(z: ZipInputStream): ByteArray {
            val baos = java.io.ByteArrayOutputStream()
            val buf = ByteArray(4096)
            var count: Int
            while (z.read(buf).also { count = it } != -1) {
                baos.write(buf, 0, count)
            }
            return baos.toByteArray()
        }

        try {
            val zip = ZipInputStream(inputStream)
            var entry: ZipEntry? = zip.nextEntry
            while (entry != null) {
                val entryName = entry.name
                when {
                    entryName == "xl/sharedStrings.xml" -> {
                        parseSharedStrings(readEntryBytes(zip).inputStream(), sharedStrings)
                    }
                    entryName == "xl/workbook.xml" -> {
                        workbookXmlBytes = readEntryBytes(zip)
                    }
                    entryName == "xl/_rels/workbook.xml.rels" -> {
                        workbookRelsBytes = readEntryBytes(zip)
                    }
                    entryName == "xl/styles.xml" -> {
                        stylesXmlBytes = readEntryBytes(zip)
                    }
                    entryName.startsWith("xl/worksheets/sheet") && entryName.endsWith(".xml") -> {
                        sheetBytes[entryName] = readEntryBytes(zip)
                    }
                    entryName.startsWith("worksheets/sheet") && entryName.endsWith(".xml") -> {
                        sheetBytes["xl/$entryName"] = readEntryBytes(zip)
                    }
                    entryName == "xl/speaksheet_colors.txt" -> {
                        legacyColors = String(readEntryBytes(zip), Charsets.UTF_8)
                    }
                }
                zip.closeEntry()
                entry = zip.nextEntry
            }

            val styles = if (stylesXmlBytes != null) parseStylesXml(stylesXmlBytes.inputStream()) else OpenXmlStyles()

            data class SheetDef(val name: String, val rId: String)
            val sheetDefs = ArrayList<SheetDef>()
            if (workbookXmlBytes != null) {
                val factory = XmlPullParserFactory.newInstance()
                val parser = factory.newPullParser()
                parser.setInput(workbookXmlBytes.inputStream(), "UTF-8")
                var event = parser.eventType
                while (event != XmlPullParser.END_DOCUMENT) {
                    if (event == XmlPullParser.START_TAG && parser.name == "sheet") {
                        val name = parser.getAttributeValue(null, "name") ?: "Sheet${sheetDefs.size + 1}"
                        var rId: String? = null
                        for (i in 0 until parser.attributeCount) {
                            val attrName = parser.getAttributeName(i)
                            if (attrName == "id" || attrName == "r:id" || attrName.endsWith(":id")) {
                                rId = parser.getAttributeValue(i)
                                break
                            }
                        }
                        sheetDefs.add(SheetDef(name, rId ?: "rId${sheetDefs.size + 1}"))
                    }
                    event = parser.next()
                }
            }

            val rIdToTarget = HashMap<String, String>()
            if (workbookRelsBytes != null) {
                val factory = XmlPullParserFactory.newInstance()
                val parser = factory.newPullParser()
                parser.setInput(workbookRelsBytes.inputStream(), "UTF-8")
                var event = parser.eventType
                while (event != XmlPullParser.END_DOCUMENT) {
                    if (event == XmlPullParser.START_TAG && parser.name == "Relationship") {
                        val id = parser.getAttributeValue(null, "Id") ?: ""
                        var target = parser.getAttributeValue(null, "Target") ?: ""
                        if (!target.startsWith("xl/")) {
                            target = "xl/" + target.removePrefix("/")
                        }
                        if (id.isNotEmpty() && target.isNotEmpty()) {
                            rIdToTarget[id] = target
                        }
                    }
                    event = parser.next()
                }
            }

            val sheetsToParse = ArrayList<Pair<String, ByteArray>>()
            for (def in sheetDefs) {
                val target = rIdToTarget[def.rId] ?: "xl/worksheets/sheet${sheetsToParse.size + 1}.xml"
                val b = sheetBytes[target] ?: sheetBytes.entries.firstOrNull { it.key.endsWith(target.substringAfterLast("/")) }?.value
                if (b != null) {
                    sheetsToParse.add(Pair(def.name, b))
                }
            }

            if (sheetsToParse.isEmpty()) {
                val sortedKeys = sheetBytes.keys.sorted()
                for ((idx, key) in sortedKeys.withIndex()) {
                    sheetBytes[key]?.let {
                        sheetsToParse.add(Pair("Sheet${idx + 1}", it))
                    }
                }
            }

            if (sheetsToParse.isEmpty()) return false

            for (pair in sheetsToParse) {
                val (sheetName, bytes) = pair
                val sheetState = SheetState(name = sheetName)
                parseSheetXmlInto(sheetState, bytes.inputStream(), sharedStrings, styles)
                sheets.add(sheetState)
            }

            currentSheetIndex = 0
            if (legacyColors != null) {
                deserializeColors(legacyColors)
            }
            recalculateAllFormulas()
            markStructureDirty()
            return true
        } catch (_: Throwable) {
            return false
        }
    }

    fun getLastUsedRow(): Int {
        var lastR = -1
        for ((key, cell) in cells) {
            if (cell.raw.isNotEmpty()) {
                val r = (key ushr 32).toInt()
                if (r > lastR) lastR = r
            }
        }
        for ((key, text) in spillOutputs) {
            if (text.isNotEmpty()) {
                val r = (key ushr 32).toInt()
                if (r > lastR) lastR = r
            }
        }
        return if (lastR >= 0) lastR else 0
    }

    fun getLastUsedCol(): Int {
        var lastC = -1
        for ((key, cell) in cells) {
            if (cell.raw.isNotEmpty()) {
                val c = (key and 0xFFFFFFFFL).toInt()
                if (c > lastC) lastC = c
            }
        }
        for ((key, text) in spillOutputs) {
            if (text.isNotEmpty()) {
                val c = (key and 0xFFFFFFFFL).toInt()
                if (c > lastC) lastC = c
            }
        }
        return if (lastC >= 0) lastC else 0
    }

    fun parseRange(token: String): Pair<Pair<Int, Int>, Pair<Int, Int>>? {
        val parts = token.uppercase(Locale.ROOT).split(":")
        if (parts.size != 2) return null
        val start = parseSingleCellRef(parts[0].trim(), defaultRow = 0) ?: return null
        val isOpenEnded = parts[1].trim().none { it.isDigit() }
        val defaultEndRow = if (isOpenEnded) maxOf(start.first, getLastUsedRow()) else maxOf(maxRow - 1, 0)
        val end = parseSingleCellRef(parts[1].trim(), defaultRow = defaultEndRow) ?: return null
        return Pair(start, end)
    }

    private fun parseSingleCellRef(ref: String, defaultRow: Int = -1): Pair<Int, Int>? {
        var col = 0
        var rowStr = ""
        for (ch in ref.uppercase(Locale.ROOT)) {
            if (ch in 'A'..'Z') {
                col = col * 26 + (ch - 'A' + 1)
            } else if (ch.isDigit()) {
                rowStr += ch
            }
        }
        if (col == 0) return null
        val row = if (rowStr.isEmpty()) {
            if (defaultRow >= 0) defaultRow + 1 else return null
        } else {
            rowStr.toIntOrNull() ?: return null
        }
        return Pair(row - 1, col - 1)
    }

    fun parseCellReference(ref: String, defaultRow: Int = -1): Pair<Int, Int>? {
        if (ref.contains(":")) {
            return parseRange(ref)?.first
        }
        return parseSingleCellRef(ref, defaultRow)
    }

    suspend fun saveToFile(file: File) = withContext(Dispatchers.IO) {
        file.parentFile?.mkdirs()
        file.outputStream().use { out ->
            if (file.name.endsWith(".csv", ignoreCase = true)) {
                saveCSV(out)
            } else {
                saveXLSX(out)
            }
        }
        try {
            val colorsFile = File(file.parentFile, "${file.name}.colors")
            if (cellColors.isNotEmpty() || columnColors.isNotEmpty()) {
                colorsFile.writeText(serializeColors(), Charsets.UTF_8)
            } else if (colorsFile.exists()) {
                colorsFile.delete()
            }
        } catch (_: Throwable) {}
    }

    suspend fun saveToUri(context: Context, uri: Uri) = withContext(Dispatchers.IO) {
        try {
            context.contentResolver.openOutputStream(uri)?.use { out ->
                val name = uri.path ?: ""
                if (name.endsWith(".xlsx", ignoreCase = true)) {
                    saveXLSX(out)
                } else {
                    saveCSV(out)
                }
            }
        } catch (_: Throwable) {
            // Handled gracefully
        }
    }

    fun saveCSV(out: OutputStream) {
        val writer = OutputStreamWriter(out)
        for (r in 0 until maxRow) {
            val line = (0 until maxCol).joinToString(",") { c ->
                val valStr = getCellValue(r, c).replace("\"", "\"\"")
                if (valStr.contains(",") || valStr.contains("\n") || valStr.contains("\"")) {
                    "\"$valStr\""
                } else {
                    valStr
                }
            }
            writer.write(line + "\n")
        }
        writer.flush()
    }

    fun saveXLSX(out: OutputStream) {
        val zip = ZipOutputStream(out)
        val allSheets = if (sheets.isNotEmpty()) sheets else listOf(currentSheet)

        // 1. Collect all styles across all sheets
        // Fonts: (bold: Boolean, italic: Boolean, color: Int?)
        val fontMap = LinkedHashMap<Triple<Boolean, Boolean, Int?>, Int>()
        // default font
        fontMap[Triple(false, false, null)] = 0

        // Fills: color: Int?
        val fillMap = LinkedHashMap<Int, Int>()
        // fill 0 is none, fill 1 is gray125. custom fills start at 2

        fun getNumFmtId(fmt: String): Int = when (fmt) {
            "Number" -> 4
            "Currency" -> 44
            "Percent" -> 9
            "Date" -> 14
            else -> 0
        }

        data class StyleKey(
            val fontId: Int,
            val fillId: Int,
            val borderId: Int,
            val numFmtId: Int,
            val align: Int,
            val wrapText: Boolean
        )

        val xfList = ArrayList<StyleKey>()
        // xf 0: default
        xfList.add(StyleKey(0, 0, 0, 0, 0, false))
        val xfMap = HashMap<StyleKey, Int>()
        xfMap[xfList[0]] = 0

        for (sheet in allSheets) {
            for (r in 0 until sheet.maxRow) {
                val isHeaderRow = sheet.headerRows.contains(r)
                val isBannerRow = sheet.mergedRanges.any { it.startRow == r && it.endRow == r && it.startCol == 0 && (it.endCol >= sheet.maxCol - 2 || it.endCol >= 3) }
                for (c in 0 until sheet.maxCol) {
                    val key = cellKey(r, c)
                    val raw = sheet.cells[key]?.raw ?: ""
                    val hasColor = sheet.cellColors.containsKey(key) || sheet.columnColors.containsKey(c) || sheet.rowColors.containsKey(r) || (isHeaderRow && sheet.headerBgColor != null)
                    val hasTextColor = sheet.cellTextColors.containsKey(key) || sheet.columnTextColors.containsKey(c) || sheet.rowTextColors.containsKey(r) || (isHeaderRow && sheet.headerTextColor != null)
                    val isBold = sheet.cellBold[key] == true || isHeaderRow || isBannerRow
                    val isItalic = sheet.cellItalic[key] == true
                    val align = sheet.cellAlign[key] ?: 0
                    val numFmt = sheet.cellNumFmt[key] ?: "General"
                    val border = sheet.cellBorders[key] ?: 0
                    val isWrap = if (c in sheet.wrapEnabled.indices) sheet.wrapEnabled[c] else false

                    if (raw.isNotEmpty() || hasColor || hasTextColor || isBold || isItalic || align != 0 || numFmt != "General" || border != 0 || isWrap) {
                        val textColor = sheet.cellTextColors[key] ?: sheet.columnTextColors[c] ?: sheet.rowTextColors[r] ?: if (isHeaderRow) sheet.headerTextColor else null
                        val fontTriple = Triple(isBold, isItalic, textColor)
                        val fontId = fontMap.getOrPut(fontTriple) { fontMap.size }

                        val bgColor = sheet.cellColors[key] ?: sheet.columnColors[c] ?: sheet.rowColors[r] ?: if (isHeaderRow) sheet.headerBgColor else null
                        val fillId = if (bgColor != null) {
                            fillMap.getOrPut(bgColor) { fillMap.size + 2 }
                        } else {
                            0
                        }

                        val numFmtId = getNumFmtId(numFmt)
                        val borderId = if (border > 0) 1 else 0
                        val styleKey = StyleKey(fontId, fillId, borderId, numFmtId, align, isWrap)
                        if (!xfMap.containsKey(styleKey)) {
                            val newXfId = xfList.size
                            xfList.add(styleKey)
                            xfMap[styleKey] = newXfId
                        }
                    }
                }
            }
        }

        // [Content_Types].xml
        zip.putNextEntry(ZipEntry("[Content_Types].xml"))
        val ctXml = StringBuilder("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
<Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>
<Default Extension="xml" ContentType="application/xml"/>
<Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/>
<Override PartName="/xl/styles.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml"/>
""")
        for (i in allSheets.indices) {
            ctXml.append("<Override PartName=\"/xl/worksheets/sheet${i + 1}.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml\"/>\n")
        }
        ctXml.append("</Types>")
        zip.write(ctXml.toString().toByteArray())
        zip.closeEntry()

        // _rels/.rels
        zip.putNextEntry(ZipEntry("_rels/.rels"))
        zip.write("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
<Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="xl/workbook.xml"/>
</Relationships>""".toByteArray())
        zip.closeEntry()

        // xl/_rels/workbook.xml.rels
        zip.putNextEntry(ZipEntry("xl/_rels/workbook.xml.rels"))
        val wbRels = StringBuilder("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
""")
        for (i in allSheets.indices) {
            wbRels.append("<Relationship Id=\"rId${i + 1}\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet\" Target=\"worksheets/sheet${i + 1}.xml\"/>\n")
        }
        wbRels.append("<Relationship Id=\"rIdStyles\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles\" Target=\"styles.xml\"/>\n")
        wbRels.append("</Relationships>")
        zip.write(wbRels.toString().toByteArray())
        zip.closeEntry()

        // xl/workbook.xml
        zip.putNextEntry(ZipEntry("xl/workbook.xml"))
        val wbXml = StringBuilder("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships">
<sheets>
""")
        for (i in allSheets.indices) {
            val sName = allSheets[i].name.replace("&", "&amp;").replace("\"", "&quot;").replace("<", "&lt;").replace(">", "&gt;")
            wbXml.append("<sheet name=\"$sName\" sheetId=\"${i + 1}\" r:id=\"rId${i + 1}\"/>\n")
        }
        wbXml.append("</sheets>\n</workbook>")
        zip.write(wbXml.toString().toByteArray())
        zip.closeEntry()

        // xl/styles.xml
        zip.putNextEntry(ZipEntry("xl/styles.xml"))
        val stylesXml = StringBuilder("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<styleSheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">
""")
        // Fonts
        stylesXml.append("<fonts count=\"${fontMap.size}\">\n")
        for ((fontTriple, _) in fontMap) {
            val (bold, italic, color) = fontTriple
            stylesXml.append("<font>")
            if (bold) stylesXml.append("<b/>")
            if (italic) stylesXml.append("<i/>")
            stylesXml.append("<sz val=\"11\"/><name val=\"Calibri\"/>")
            if (color != null) {
                val hex = String.format(Locale.ROOT, "%08X", color or 0xFF000000.toInt())
                stylesXml.append("<color rgb=\"$hex\"/>")
            }
            stylesXml.append("</font>\n")
        }
        stylesXml.append("</fonts>\n")

        // Fills
        val totalFills = 2 + fillMap.size
        stylesXml.append("<fills count=\"$totalFills\">\n")
        stylesXml.append("<fill><patternFill patternType=\"none\"/></fill>\n")
        stylesXml.append("<fill><patternFill patternType=\"gray125\"/></fill>\n")
        for ((colorInt, _) in fillMap) {
            val hex = String.format(Locale.ROOT, "%08X", colorInt or 0xFF000000.toInt())
            stylesXml.append("<fill><patternFill patternType=\"solid\"><fgColor rgb=\"$hex\"/><bgColor indexed=\"64\"/></patternFill></fill>\n")
        }
        stylesXml.append("</fills>\n")

        // Borders
        stylesXml.append("""<borders count="2">
<border><left/><right/><top/><bottom/><diagonal/></border>
<border><left style="thin"><color auto="1"/></left><right style="thin"><color auto="1"/></right><top style="thin"><color auto="1"/></top><bottom style="thin"><color auto="1"/></bottom><diagonal/></border>
</borders>
""")

        // CellStyleXfs
        stylesXml.append("<cellStyleXfs count=\"1\"><xf numFmtId=\"0\" fontId=\"0\" fillId=\"0\" borderId=\"0\"/></cellStyleXfs>\n")

        // CellXfs
        stylesXml.append("<cellXfs count=\"${xfList.size}\">\n")
        for (xf in xfList) {
            val horiz = when (xf.align) {
                1 -> "center"
                2 -> "right"
                else -> "left"
            }
            val applyFont = if (xf.fontId > 0) " applyFont=\"1\"" else ""
            val applyFill = if (xf.fillId > 0) " applyFill=\"1\"" else ""
            val applyBorder = if (xf.borderId > 0) " applyBorder=\"1\"" else ""
            val applyNumFmt = if (xf.numFmtId > 0) " applyNumberFormat=\"1\"" else ""
            val applyAlignment = if (xf.align != 0 || xf.wrapText) " applyAlignment=\"1\"" else ""
            stylesXml.append("<xf numFmtId=\"${xf.numFmtId}\" fontId=\"${xf.fontId}\" fillId=\"${xf.fillId}\" borderId=\"${xf.borderId}\" xfId=\"0\"$applyFont$applyFill$applyBorder$applyNumFmt$applyAlignment>")
            stylesXml.append("<alignment horizontal=\"$horiz\"${if (xf.wrapText) " wrapText=\"1\"" else ""}/>")
            stylesXml.append("</xf>\n")
        }
        stylesXml.append("</cellXfs>\n</styleSheet>")
        zip.write(stylesXml.toString().toByteArray())
        zip.closeEntry()

        // Worksheets: sheet1.xml, sheet2.xml, ...
        for ((sheetIdx, sheet) in allSheets.withIndex()) {
            zip.putNextEntry(ZipEntry("xl/worksheets/sheet${sheetIdx + 1}.xml"))
            val sheetXml = StringBuilder("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">
""")
            // sheetViews for frozen panes & gridlines
            sheetXml.append("<sheetViews><sheetView workbookViewId=\"0\" showGridLines=\"1\">")
            if (sheet.frozenRows > 0 || sheet.frozenCols > 0) {
                val topCell = getColumnName(sheet.frozenCols) + (sheet.frozenRows + 1)
                sheetXml.append("<pane xSplit=\"${sheet.frozenCols}\" ySplit=\"${sheet.frozenRows}\" topLeftCell=\"$topCell\" activePane=\"bottomRight\" state=\"frozen\"/>")
            }
            sheetXml.append("</sheetView></sheetViews>\n")

            // Column Widths (<cols>)
            sheetXml.append("<cols>\n")
            for (c in 0 until sheet.maxCol) {
                val colWidthDp = getColWidthDp(c)
                val excelWidth = (colWidthDp / 7.5f).coerceIn(4f, 255f)
                sheetXml.append("<col min=\"${c + 1}\" max=\"${c + 1}\" width=\"${String.format(Locale.ROOT, "%.2f", excelWidth)}\" customWidth=\"1\"/>\n")
            }
            sheetXml.append("</cols>\n")

            // sheetData
            sheetXml.append("<sheetData>\n")
            for (r in 0 until sheet.maxRow) {
                val isHeaderRow = sheet.headerRows.contains(r)
                val isBannerRow = sheet.mergedRanges.any { it.startRow == r && it.endRow == r && it.startCol == 0 && (it.endCol >= sheet.maxCol - 2 || it.endCol >= 3) }
                var rowHasData = false
                for (c in 0 until sheet.maxCol) {
                    val key = cellKey(r, c)
                    if (sheet.cells[key]?.raw?.isNotEmpty() == true ||
                        sheet.cellColors.containsKey(key) ||
                        sheet.columnColors.containsKey(c) ||
                        sheet.rowColors.containsKey(r) ||
                        (isHeaderRow && sheet.headerBgColor != null) ||
                        (isHeaderRow && sheet.headerTextColor != null) ||
                        sheet.cellBold.containsKey(key) ||
                        sheet.cellItalic.containsKey(key) ||
                        sheet.cellAlign.containsKey(key) ||
                        sheet.cellNumFmt.containsKey(key) ||
                        sheet.cellBorders.containsKey(key)
                    ) {
                        rowHasData = true
                        break
                    }
                }
                if (rowHasData) {
                    val rowHeightDp = getRowHeightDp(r)
                    val rowHtPt = (rowHeightDp * 0.75f).coerceAtLeast(15f)
                    sheetXml.append("<row r=\"${r + 1}\" ht=\"${String.format(Locale.ROOT, "%.2f", rowHtPt)}\" customHeight=\"1\">")
                    for (c in 0 until sheet.maxCol) {
                        val key = cellKey(r, c)
                        val raw = sheet.cells[key]?.raw ?: ""
                        val disp = getCellValue(r, c)
                        val ref = getColumnName(c) + (r + 1)

                        val textColor = sheet.cellTextColors[key] ?: sheet.columnTextColors[c] ?: sheet.rowTextColors[r] ?: if (isHeaderRow) sheet.headerTextColor else null
                        val isBold = sheet.cellBold[key] == true || isHeaderRow || isBannerRow
                        val isItalic = sheet.cellItalic[key] == true
                        val fontId = fontMap[Triple(isBold, isItalic, textColor)] ?: 0

                        val bgColor = sheet.cellColors[key] ?: sheet.columnColors[c] ?: sheet.rowColors[r] ?: if (isHeaderRow) sheet.headerBgColor else null
                        val fillId = if (bgColor != null) fillMap[bgColor] ?: 0 else 0
                        val numFmtId = getNumFmtId(sheet.cellNumFmt[key] ?: "General")
                        val borderId = if ((sheet.cellBorders[key] ?: 0) > 0) 1 else 0
                        val isWrap = if (c in sheet.wrapEnabled.indices) sheet.wrapEnabled[c] else false
                        val align = sheet.cellAlign[key] ?: 0
                        val styleKey = StyleKey(fontId, fillId, borderId, numFmtId, align, isWrap)
                        val sIdx = xfMap[styleKey] ?: 0
                        val sAttr = if (sIdx > 0) " s=\"$sIdx\"" else ""

                        if (raw.startsWith("=")) {
                            val fClean = raw.removePrefix("=").replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                            sheetXml.append("<c r=\"$ref\"$sAttr><f>$fClean</f><v>$disp</v></c>")
                        } else if (raw.toDoubleOrNull() != null && !(raw.startsWith("0") && raw.length > 1 && !raw.contains("."))) {
                            sheetXml.append("<c r=\"$ref\"$sAttr><v>$raw</v></c>")
                        } else if (raw.isNotEmpty()) {
                            val escaped = raw.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                            sheetXml.append("<c r=\"$ref\"$sAttr t=\"inlineStr\"><is><t xml:space=\"preserve\">$escaped</t></is></c>")
                        } else if (sIdx > 0) {
                            sheetXml.append("<c r=\"$ref\"$sAttr/>")
                        }
                    }
                    sheetXml.append("</row>\n")
                }
            }
            sheetXml.append("</sheetData>\n")

            // mergeCells
            if (sheet.mergedRanges.isNotEmpty()) {
                sheetXml.append("<mergeCells count=\"${sheet.mergedRanges.size}\">\n")
                for (m in sheet.mergedRanges) {
                    val mRef = "${getColumnName(m.startCol)}${m.startRow + 1}:${getColumnName(m.endCol)}${m.endRow + 1}"
                    sheetXml.append("<mergeCell ref=\"$mRef\"/>\n")
                }
                sheetXml.append("</mergeCells>\n")
            }
            sheetXml.append("</worksheet>")
            zip.write(sheetXml.toString().toByteArray())
            zip.closeEntry()
        }

        zip.finish()
        zip.flush()
    }

    private fun evaluateFormula(formula: String, originR: Int = -1, originC: Int = -1): String {
        try {
            var clean = formula.trim()
            if (clean.startsWith("=")) clean = clean.removePrefix("=").trim()

            // Handle string concatenation operator '&'
            if (clean.contains("&") && !clean.startsWith("\"")) {
                val parts = clean.split("&")
                val sb = StringBuilder()
                for (part in parts) {
                    sb.append(evaluateExpression(part.trim(), originR, originC))
                }
                return sb.toString()
            }

            val upper = clean.uppercase(Locale.ROOT)
            if (upper.startsWith("FILTER(") && upper.endsWith(")")) {
                return evaluateFilter(formula, clean, originR, originC)
            }
            if (upper.startsWith("SORT(") && upper.endsWith(")")) {
                return evaluateSort(formula, clean, originR, originC)
            }
            val openParen = upper.indexOf('(')
            if (openParen != -1 && upper.endsWith(")")) {
                val funcName = upper.substring(0, openParen).trim()
                val inner = clean.substring(openParen + 1, clean.length - 1).trim()
                val args = splitArguments(inner)

                when (funcName) {
                    "SUM" -> {
                        val sum = args.sumOf { evaluateRange(evaluateExpression(it, originR, originC)).sum() }
                        return formatNumber(sum)
                    }
                    "AVERAGE" -> {
                        val vals = args.flatMap { evaluateRange(evaluateExpression(it, originR, originC)) }
                        if (vals.isEmpty()) return "0"
                        return formatNumber(vals.average())
                    }
                    "COUNT" -> {
                        val vals = args.flatMap { evaluateRange(evaluateExpression(it, originR, originC)) }
                        return vals.size.toString()
                    }
                    "MIN" -> {
                        val vals = args.flatMap { evaluateRange(evaluateExpression(it, originR, originC)) }
                        return if (vals.isNotEmpty()) formatNumber(vals.minOrNull() ?: 0.0) else "0"
                    }
                    "MAX" -> {
                        val vals = args.flatMap { evaluateRange(evaluateExpression(it, originR, originC)) }
                        return if (vals.isNotEmpty()) formatNumber(vals.maxOrNull() ?: 0.0) else "0"
                    }
                    "SORT" -> return evaluateSort(formula, clean, originR, originC)
                    "FILTER" -> return evaluateFilter(formula, clean, originR, originC)
                    "IF" -> return evaluateIf(inner)
                    "SUMIF" -> return evaluateSumIf(inner)
                    "COUNTIF" -> return evaluateCountIf(inner)
                    "VLOOKUP" -> return evaluateVLookup(inner)
                    "XLOOKUP" -> return evaluateXLookup(inner)

                    // Math
                    "ROUND" -> {
                        val v = evaluateExpression(args.getOrNull(0) ?: "0", originR, originC).toDoubleOrNull() ?: 0.0
                        val decimals = evaluateExpression(args.getOrNull(1) ?: "0", originR, originC).toIntOrNull() ?: 0
                        return formatNumber(BigDecimal(v).setScale(decimals, RoundingMode.HALF_UP).toDouble())
                    }
                    "ROUNDUP" -> {
                        val v = evaluateExpression(args.getOrNull(0) ?: "0", originR, originC).toDoubleOrNull() ?: 0.0
                        val decimals = evaluateExpression(args.getOrNull(1) ?: "0", originR, originC).toIntOrNull() ?: 0
                        return formatNumber(BigDecimal(v).setScale(decimals, RoundingMode.UP).toDouble())
                    }
                    "ROUNDDOWN" -> {
                        val v = evaluateExpression(args.getOrNull(0) ?: "0", originR, originC).toDoubleOrNull() ?: 0.0
                        val decimals = evaluateExpression(args.getOrNull(1) ?: "0", originR, originC).toIntOrNull() ?: 0
                        return formatNumber(BigDecimal(v).setScale(decimals, RoundingMode.DOWN).toDouble())
                    }
                    "ABS" -> {
                        val v = evaluateExpression(args.getOrNull(0) ?: "0", originR, originC).toDoubleOrNull() ?: 0.0
                        return formatNumber(kotlin.math.abs(v))
                    }
                    "SQRT" -> {
                        val v = evaluateExpression(args.getOrNull(0) ?: "0", originR, originC).toDoubleOrNull() ?: 0.0
                        return if (v >= 0) formatNumber(kotlin.math.sqrt(v)) else "#NUM!"
                    }
                    "POWER" -> {
                        val base = evaluateExpression(args.getOrNull(0) ?: "0", originR, originC).toDoubleOrNull() ?: 0.0
                        val exp = evaluateExpression(args.getOrNull(1) ?: "1", originR, originC).toDoubleOrNull() ?: 1.0
                        return formatNumber(Math.pow(base, exp))
                    }
                    "MOD" -> {
                        val a = evaluateExpression(args.getOrNull(0) ?: "0", originR, originC).toDoubleOrNull() ?: 0.0
                        val b = evaluateExpression(args.getOrNull(1) ?: "1", originR, originC).toDoubleOrNull() ?: 1.0
                        if (b == 0.0) return "#DIV/0!"
                        return formatNumber(a % b)
                    }
                    "INT" -> {
                        val v = evaluateExpression(args.getOrNull(0) ?: "0", originR, originC).toDoubleOrNull() ?: 0.0
                        return v.toInt().toString()
                    }

                    // Text
                    "CONCATENATE" -> {
                        return args.joinToString("") { evaluateExpression(it, originR, originC) }
                    }
                    "LEFT" -> {
                        val text = evaluateExpression(args.getOrNull(0) ?: "", originR, originC)
                        val num = evaluateExpression(args.getOrNull(1) ?: "1", originR, originC).toIntOrNull() ?: 1
                        return text.take(num)
                    }
                    "RIGHT" -> {
                        val text = evaluateExpression(args.getOrNull(0) ?: "", originR, originC)
                        val num = evaluateExpression(args.getOrNull(1) ?: "1", originR, originC).toIntOrNull() ?: 1
                        return text.takeLast(num)
                    }
                    "MID" -> {
                        val text = evaluateExpression(args.getOrNull(0) ?: "", originR, originC)
                        val start = (evaluateExpression(args.getOrNull(1) ?: "1", originR, originC).toIntOrNull() ?: 1) - 1
                        val len = evaluateExpression(args.getOrNull(2) ?: "1", originR, originC).toIntOrNull() ?: 1
                        val startIdx = start.coerceIn(0, text.length)
                        val endIdx = (startIdx + len).coerceIn(0, text.length)
                        return text.substring(startIdx, endIdx)
                    }
                    "LEN" -> {
                        val text = evaluateExpression(args.getOrNull(0) ?: "", originR, originC)
                        return text.length.toString()
                    }
                    "TRIM" -> {
                        val text = evaluateExpression(args.getOrNull(0) ?: "", originR, originC)
                        return text.trim()
                    }
                    "PROPER" -> {
                        val text = evaluateExpression(args.getOrNull(0) ?: "", originR, originC)
                        return text.split(" ").joinToString(" ") { it.lowercase(Locale.ROOT).replaceFirstChar { c -> c.uppercase(Locale.ROOT) } }
                    }
                    "UPPER" -> {
                        val text = evaluateExpression(args.getOrNull(0) ?: "", originR, originC)
                        return text.uppercase(Locale.ROOT)
                    }
                    "LOWER" -> {
                        val text = evaluateExpression(args.getOrNull(0) ?: "", originR, originC)
                        return text.lowercase(Locale.ROOT)
                    }
                    "SUBSTITUTE" -> {
                        val text = evaluateExpression(args.getOrNull(0) ?: "", originR, originC)
                        val oldStr = evaluateExpression(args.getOrNull(1) ?: "", originR, originC)
                        val newStr = evaluateExpression(args.getOrNull(2) ?: "", originR, originC)
                        return text.replace(oldStr, newStr)
                    }
                    "TEXT" -> {
                        return evaluateExpression(args.getOrNull(0) ?: "0", originR, originC)
                    }

                    // Logical
                    "AND" -> {
                        val allTrue = args.all { evaluateCondition(evaluateExpression(it, originR, originC)) }
                        return allTrue.toString().uppercase(Locale.ROOT)
                    }
                    "OR" -> {
                        val anyTrue = args.any { evaluateCondition(evaluateExpression(it, originR, originC)) }
                        return anyTrue.toString().uppercase(Locale.ROOT)
                    }
                    "NOT" -> {
                        val res = evaluateCondition(evaluateExpression(args.getOrNull(0) ?: "FALSE", originR, originC))
                        return (!res).toString().uppercase(Locale.ROOT)
                    }
                    "IFERROR" -> {
                        val v = evaluateExpression(args.getOrNull(0) ?: "", originR, originC)
                        val fallback = evaluateExpression(args.getOrNull(1) ?: "", originR, originC)
                        if (v.startsWith("#") || v.contains("ERROR") || v.contains("DIV/0")) return fallback
                        return v
                    }
                    "ISBLANK" -> {
                        val v = evaluateExpression(args.getOrNull(0) ?: "", originR, originC)
                        return v.isEmpty().toString().uppercase(Locale.ROOT)
                    }
                    "ISNUMBER" -> {
                        val v = evaluateExpression(args.getOrNull(0) ?: "", originR, originC)
                        return (v.toDoubleOrNull() != null).toString().uppercase(Locale.ROOT)
                    }
                    "ISTEXT" -> {
                        val v = evaluateExpression(args.getOrNull(0) ?: "", originR, originC)
                        return (v.toDoubleOrNull() == null && v.isNotEmpty()).toString().uppercase(Locale.ROOT)
                    }

                    // Date / Time
                    "TODAY" -> {
                        return java.time.LocalDate.now().toString()
                    }
                    "NOW" -> {
                        return java.time.LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
                    }
                    "DATE" -> {
                        val y = evaluateExpression(args.getOrNull(0) ?: "2026", originR, originC).toIntOrNull() ?: 2026
                        val m = evaluateExpression(args.getOrNull(1) ?: "1", originR, originC).toIntOrNull() ?: 1
                        val d = evaluateExpression(args.getOrNull(2) ?: "1", originR, originC).toIntOrNull() ?: 1
                        return String.format(Locale.ROOT, "%04d-%02d-%02d", y, m, d)
                    }
                    "YEAR" -> {
                        val dateStr = evaluateExpression(args.getOrNull(0) ?: "", originR, originC)
                        return dateStr.take(4)
                    }
                    "MONTH" -> {
                        val dateStr = evaluateExpression(args.getOrNull(0) ?: "", originR, originC)
                        val parts = dateStr.split("-")
                        return if (parts.size >= 2) parts[1].toInt().toString() else "1"
                    }
                    "DAY" -> {
                        val dateStr = evaluateExpression(args.getOrNull(0) ?: "", originR, originC)
                        val parts = dateStr.split("-")
                        return if (parts.size >= 3) parts[2].take(2).toInt().toString() else "1"
                    }
                    "DATEDIF" -> {
                        val startStr = evaluateExpression(args.getOrNull(0) ?: "", originR, originC)
                        val endStr = evaluateExpression(args.getOrNull(1) ?: "", originR, originC)
                        val unit = evaluateExpression(args.getOrNull(2) ?: "D", originR, originC).uppercase(Locale.ROOT)
                        try {
                            val start = java.time.LocalDate.parse(startStr.take(10))
                            val end = java.time.LocalDate.parse(endStr.take(10))
                            val p = java.time.Period.between(start, end)
                            return when (unit) {
                                "Y" -> p.years.toString()
                                "M" -> (p.years * 12 + p.months).toString()
                                else -> java.time.temporal.ChronoUnit.DAYS.between(start, end).toString()
                            }
                        } catch (_: Throwable) {
                            return "0"
                        }
                    }

                    // Lookup / Array
                    "UNIQUE" -> {
                        return evaluateUnique(args.getOrNull(0) ?: "", originR, originC)
                    }
                    "SORTN" -> {
                        return evaluateSortN(args, originR, originC)
                    }
                    "INDEX" -> {
                        return evaluateIndex(args, originR, originC)
                    }
                    "MATCH" -> {
                        return evaluateMatch(args, originR, originC)
                    }

                    // Statistical
                    "COUNTA" -> {
                        val rangeVals = evaluateRangeWithCoords(args.getOrNull(0) ?: "")
                        return rangeVals.count { it.third.isNotEmpty() }.toString()
                    }
                    "COUNTBLANK" -> {
                        val rangeVals = evaluateRangeWithCoords(args.getOrNull(0) ?: "")
                        return rangeVals.count { it.third.isEmpty() }.toString()
                    }
                    "MEDIAN" -> {
                        val vals = args.flatMap { evaluateRange(evaluateExpression(it, originR, originC)) }.sorted()
                        if (vals.isEmpty()) return "0"
                        val mid = vals.size / 2
                        val med = if (vals.size % 2 == 1) vals[mid] else (vals[mid - 1] + vals[mid]) / 2.0
                        return formatNumber(med)
                    }
                    "MODE" -> {
                        val vals = args.flatMap { evaluateRange(evaluateExpression(it, originR, originC)) }
                        if (vals.isEmpty()) return "0"
                        val freq = vals.groupingBy { it }.eachCount()
                        val maxFreq = freq.maxOfOrNull { it.value } ?: 1
                        val modeVal = freq.entries.firstOrNull { it.value == maxFreq }?.key ?: vals[0]
                        return formatNumber(modeVal)
                    }
                }
            }

            val refCoords = parseCellReference(clean)
            if (refCoords != null) {
                return getCellValue(refCoords.first, refCoords.second)
            }

            return clean
        } catch (_: Throwable) {
            return formula
        }
    }

    private fun evaluateExpression(expr: String, originR: Int, originC: Int): String {
        val trimmed = expr.trim()
        if (trimmed.startsWith("\"") && trimmed.endsWith("\"")) {
            return trimmed.removeSurrounding("\"")
        }
        val coords = parseCellReference(trimmed.uppercase(Locale.ROOT))
        if (coords != null) {
            return getCellValue(coords.first, coords.second)
        }
        val dVal = trimmed.toDoubleOrNull()
        if (dVal != null) {
            return formatNumber(dVal)
        }
        if (trimmed.startsWith("=")) {
            return evaluateFormula(trimmed, originR, originC)
        }
        val upper = trimmed.uppercase(Locale.ROOT)
        if (upper.contains("(") && upper.endsWith(")")) {
            return evaluateFormula("=$trimmed", originR, originC)
        }
        return trimmed
    }

    private fun getRowsDataForArgument(arg: String, originR: Int, originC: Int): List<List<String>> {
        var trimmed = arg.trim()
        if (trimmed.startsWith("=")) trimmed = trimmed.removePrefix("=").trim()
        val upper = trimmed.uppercase(Locale.ROOT)
        if (upper.startsWith("FILTER(")) {
            return getFilteredRowsData(trimmed, originR, originC)
        }
        if (upper.startsWith("UNIQUE(")) {
            val rangeVals = evaluateRangeWithCoords(trimmed.substring(7, trimmed.length - 1).trim())
            return rangeVals.map { listOf(it.third) }
        }
        val rangeStr = trimmed.uppercase(Locale.ROOT)
        val parts = rangeStr.split(":")
        if (parts.size != 2) return emptyList()
        val start = parseCellReference(parts[0].trim(), defaultRow = 0) ?: return emptyList()
        val isOpenEnded = parts[1].trim().none { it.isDigit() }
        val defaultEndR = if (isOpenEnded) maxOf(start.first, getLastUsedRow()) else maxOf(maxRow - 1, 0)
        val end = parseCellReference(parts[1].trim(), defaultRow = defaultEndR) ?: return emptyList()
        val rMin = minOf(start.first, end.first)
        val rMax = maxOf(start.first, end.first)
        val cMin = minOf(start.second, end.second)
        val cMax = maxOf(start.second, end.second)

        val rowsData = ArrayList<List<String>>()
        for (r in rMin..rMax) {
            val row = ArrayList<String>()
            for (c in cMin..cMax) {
                row.add(getCellValue(r, c))
            }
            rowsData.add(row)
        }
        return rowsData
    }

    private fun getFilteredRowsData(formula: String, originR: Int, originC: Int): List<List<String>> {
        var clean = formula.trim()
        if (clean.startsWith("=")) clean = clean.removePrefix("=").trim()
        val openParen = clean.indexOf('(')
        val closeParen = clean.lastIndexOf(')')
        if (openParen == -1 || closeParen <= openParen) return emptyList()
        val inner = clean.substring(openParen + 1, closeParen).trim()
        val args = splitArguments(inner)
        if (args.size < 2) return emptyList()

        val rangeStr = args[0].trim().uppercase(Locale.ROOT)
        val parts = rangeStr.split(":")
        if (parts.size != 2) return emptyList()

        val start = parseCellReference(parts[0].trim(), defaultRow = 0) ?: return emptyList()
        val isOpenEndedFilter = parts[1].trim().none { it.isDigit() }
        val defaultEndRFilter = if (isOpenEndedFilter) maxOf(start.first, getLastUsedRow()) else maxOf(maxRow - 1, 0)
        val end = parseCellReference(parts[1].trim(), defaultRow = defaultEndRFilter) ?: return emptyList()

        val rMin = minOf(start.first, end.first)
        val rMax = maxOf(start.first, end.first)
        val cMin = minOf(start.second, end.second)
        val cMax = maxOf(start.second, end.second)

        val numRows = maxOf(0, rMax - rMin + 1)
        if (numRows == 0) return emptyList()

        val rowMatches = BooleanArray(numRows) { true }

        if (args.size == 3 && !args[1].contains(">") && !args[1].contains("<") && !args[1].contains("=")) {
            val condRangeStr = args[1].trim()
            val criteria = args[2].trim()
            val condList = evaluateConditionArray("$condRangeStr$criteria", rMin, rMax, originR, originC)
            for (idx in 0 until numRows) {
                if (idx >= condList.size || !condList[idx]) {
                    rowMatches[idx] = false
                }
            }
        } else {
            for (argIdx in 1 until args.size) {
                val condExpr = args[argIdx].trim()
                if (condExpr.isEmpty()) continue
                val condList = evaluateConditionArray(condExpr, rMin, rMax, originR, originC)
                for (idx in 0 until numRows) {
                    if (idx >= condList.size || !condList[idx]) {
                        rowMatches[idx] = false
                    }
                }
            }
        }

        val rowsData = ArrayList<List<String>>()
        for (idx in 0 until numRows) {
            if (rowMatches[idx]) {
                val r = rMin + idx
                val row = ArrayList<String>(cMax - cMin + 1)
                for (c in cMin..cMax) {
                    row.add(getCellValue(r, c))
                }
                rowsData.add(row)
            }
        }
        return rowsData
    }

    private fun evaluateFilter(formula: String, clean: String, originR: Int, originC: Int): String {
        val rowsData = getFilteredRowsData(clean, originR, originC)
        if (rowsData.isEmpty()) return "#N/A"

        if (originR < 0 || originC < 0) {
            return rowsData[0].firstOrNull() ?: ""
        }

        val numRows = rowsData.size
        val numCols = rowsData[0].size

        var hasCollision = false
        for (dr in 0 until numRows) {
            for (dc in 0 until numCols) {
                if (dr == 0 && dc == 0) continue
                val targetKey = cellKey(originR + dr, originC + dc)
                val existing = cells[targetKey]
                if (existing != null && existing.raw.isNotEmpty()) {
                    hasCollision = true
                    break
                }
            }
            if (hasCollision) break
        }

        if (hasCollision) {
            return "#SPILL!"
        }

        for (dr in 0 until numRows) {
            val row = rowsData[dr]
            for (dc in 0 until row.size) {
                if (dr == 0 && dc == 0) continue
                val targetKey = cellKey(originR + dr, originC + dc)
                spillOutputs[targetKey] = row[dc]
                spillSources[targetKey] = cellKey(originR, originC)
                if (originC + dc in 0 until maxCol) {
                    dirtyColumns.add(originC + dc)
                }
            }
        }

        val reqR = originR + numRows
        val reqC = originC + numCols
        if (reqR > maxRow || reqC > maxCol) {
            maxRow = maxOf(maxRow, reqR)
            maxCol = maxOf(maxCol, reqC)
            isFullLayoutDirty = true
        }

        return rowsData[0].firstOrNull() ?: ""
    }

    private fun matchesFilterCondition(value: String, criteria: String): Boolean {
        val cleanCrit = criteria.trim()
        val op = listOf(">=", "<=", "<>", ">", "<", "=").find { cleanCrit.startsWith(it) }
        if (op != null) {
            val targetStr = cleanCrit.removePrefix(op).trim().removeSurrounding("\"")
            val vNum = value.toDoubleOrNull()
            val tNum = targetStr.toDoubleOrNull()
            if (vNum != null && tNum != null) {
                return when (op) {
                    ">" -> vNum > tNum; "<" -> vNum < tNum; ">=" -> vNum >= tNum; "<=" -> vNum <= tNum; "=" -> vNum == tNum; "<>" -> vNum != tNum; else -> false
                }
            } else {
                val cmp = value.compareTo(targetStr, ignoreCase = true)
                return when (op) { "=" -> cmp == 0; "<>" -> cmp != 0; ">" -> cmp > 0; "<" -> cmp < 0; ">=" -> cmp >= 0; "<=" -> cmp <= 0; else -> false }
            }
        }
        return value.equals(cleanCrit.removeSurrounding("\""), ignoreCase = true)
    }

    private fun evaluateUnique(rangeStr: String, originR: Int, originC: Int): String {
        val rangeVals = evaluateRangeWithCoords(rangeStr.trim().uppercase(Locale.ROOT))
        if (rangeVals.isEmpty()) return "#VALUE!"
        val uniqueVals = rangeVals.map { it.third }.distinct()
        if (originR < 0 || originC < 0) return uniqueVals.firstOrNull() ?: ""

        val numRows = uniqueVals.size
        for (dr in 0 until numRows) {
            if (dr == 0) continue
            val targetKey = cellKey(originR + dr, originC)
            spillOutputs[targetKey] = uniqueVals[dr]
            spillSources[targetKey] = cellKey(originR, originC)
        }
        if (numRows + originR > maxRow) {
            maxRow = maxOf(maxRow, originR + numRows)
            isFullLayoutDirty = true
        }
        return uniqueVals.firstOrNull() ?: ""
    }

    private fun evaluateSortN(args: List<String>, originR: Int, originC: Int): String {
        return evaluateSort("", "SORT(${args.joinToString(",")})", originR, originC)
    }

    private fun evaluateIndex(args: List<String>, originR: Int, originC: Int): String {
        val rangeVals = evaluateRangeWithCoords(args.getOrNull(0)?.trim()?.uppercase(Locale.ROOT) ?: "")
        val rIdx = (args.getOrNull(1)?.trim()?.toIntOrNull() ?: 1) - 1
        return rangeVals.getOrNull(rIdx)?.third ?: "#REF!"
    }

    private fun evaluateMatch(args: List<String>, originR: Int, originC: Int): String {
        val lookupVal = evaluateExpression(args.getOrNull(0) ?: "", originR, originC)
        val rangeVals = evaluateRangeWithCoords(args.getOrNull(1)?.trim()?.uppercase(Locale.ROOT) ?: "")
        for (i in rangeVals.indices) {
            if (rangeVals[i].third.equals(lookupVal, ignoreCase = true)) {
                return (i + 1).toString()
            }
        }
        return "#N/A"
    }

    private fun evaluateSort(formula: String, clean: String, originR: Int, originC: Int): String {
        val openParen = clean.indexOf('(')
        val closeParen = clean.lastIndexOf(')')
        if (openParen == -1 || closeParen <= openParen) return "#VALUE!"
        val inner = clean.substring(openParen + 1, closeParen).trim()
        val args = splitArguments(inner)
        if (args.isEmpty()) return "#VALUE!"

        val rowsData = getRowsDataForArgument(args[0], originR, originC)
        if (rowsData.isEmpty()) return "#N/A"

        var sortCol = 1
        var isAscending = true

        if (args.size == 2) {
            val arg1 = args[1].trim().uppercase(Locale.ROOT).removeSurrounding("\"").removeSurrounding("'")
            val colNum = arg1.toIntOrNull()
            if (colNum != null) {
                sortCol = colNum
            } else {
                isAscending = arg1 != "DESC" && arg1 != "-1" && arg1 != "FALSE"
            }
        } else if (args.size >= 3) {
            val arg1 = args[1].trim().toIntOrNull()
            if (arg1 != null) sortCol = arg1
            val arg2 = args[2].trim().uppercase(Locale.ROOT).removeSurrounding("\"").removeSurrounding("'")
            isAscending = arg2 != "DESC" && arg2 != "-1" && arg2 != "FALSE"
        }

        val numColsInRange = rowsData[0].size
        val sortColIdx = (sortCol - 1).coerceIn(0, numColsInRange - 1)

        val comparator = Comparator<List<String>> { row1, row2 ->
            val v1 = row1.getOrElse(sortColIdx) { "" }.trim()
            val v2 = row2.getOrElse(sortColIdx) { "" }.trim()

            if (v1.isEmpty() && v2.isEmpty()) return@Comparator 0
            if (v1.isEmpty()) return@Comparator 1
            if (v2.isEmpty()) return@Comparator -1

            val n1 = v1.removePrefix("$").removeSuffix("%").toDoubleOrNull()
            val n2 = v2.removePrefix("$").removeSuffix("%").toDoubleOrNull()

            val cmp = if (n1 != null && n2 != null) {
                n1.compareTo(n2)
            } else if (n1 != null) {
                -1
            } else if (n2 != null) {
                1
            } else {
                v1.compareTo(v2, ignoreCase = true)
            }
            if (isAscending) cmp else -cmp
        }

        val sortedRows = rowsData.sortedWith(comparator)
        if (sortedRows.isEmpty()) return ""

        if (originR < 0 || originC < 0) {
            return sortedRows[0].firstOrNull() ?: ""
        }

        val numRows = sortedRows.size
        val numCols = sortedRows[0].size

        // Spill collision check with non-empty non-origin cells
        var hasCollision = false
        for (dr in 0 until numRows) {
            for (dc in 0 until numCols) {
                if (dr == 0 && dc == 0) continue
                val targetKey = cellKey(originR + dr, originC + dc)
                val existing = cells[targetKey]
                if (existing != null && existing.raw.isNotEmpty()) {
                    hasCollision = true
                    break
                }
            }
            if (hasCollision) break
        }

        if (hasCollision) {
            return "#SPILL!"
        }

        // Spill sorted outputs
        for (dr in 0 until numRows) {
            val row = sortedRows[dr]
            for (dc in 0 until row.size) {
                if (dr == 0 && dc == 0) continue
                val targetKey = cellKey(originR + dr, originC + dc)
                spillOutputs[targetKey] = row[dc]
                spillSources[targetKey] = cellKey(originR, originC)
                if (originC + dc in 0 until maxCol) {
                    dirtyColumns.add(originC + dc)
                }
            }
        }

        // Expand layout bounds if spill extends beyond current grid
        val reqR = originR + numRows
        val reqC = originC + numCols
        if (reqR > maxRow || reqC > maxCol) {
            maxRow = maxOf(maxRow, reqR)
            maxCol = maxOf(maxCol, reqC)
            isFullLayoutDirty = true
        }

        return sortedRows[0].firstOrNull() ?: ""
    }

    private fun splitArguments(inner: String): List<String> {
        val args = ArrayList<String>()
        var current = StringBuilder()
        var inQuotes = false
        var parenDepth = 0
        for (ch in inner) {
            when (ch) {
                '"' -> {
                    inQuotes = !inQuotes
                    current.append(ch)
                }
                '(' -> {
                    if (!inQuotes) parenDepth++
                    current.append(ch)
                }
                ')' -> {
                    if (!inQuotes) parenDepth--
                    current.append(ch)
                }
                ',' -> {
                    if (!inQuotes && parenDepth == 0) {
                        args.add(current.toString().trim())
                        current = StringBuilder()
                    } else {
                        current.append(ch)
                    }
                }
                else -> current.append(ch)
            }
        }
        if (current.isNotEmpty()) {
            args.add(current.toString().trim())
        }
        return args
    }

    data class RowSortState(
        val values: List<String>,
        val rowColor: Int?,
        val rowTextColor: Int?,
        val cellColors: Map<Int, Int>,
        val cellTextColors: Map<Int, Int>
    )

    fun sortColumn(col: Int, ascending: Boolean) {
        if (col !in 0 until maxCol) return
        val startRow = 1
        var lastDataRow = startRow
        for (r in startRow until maxRow) {
            val hasContent = (0 until maxCol).any { c -> getCellValue(r, c).isNotEmpty() }
            if (hasContent) {
                lastDataRow = r
            }
        }
        if (lastDataRow <= startRow) return

        val rowsData = ArrayList<RowSortState>()
        for (r in startRow..lastDataRow) {
            val rowValues = (0 until maxCol).map { c -> getCellFormulaOrValue(r, c) }
            val rColor = getRowColor(r)
            val rTextColor = getRowTextColor(r)
            val cColors = mutableMapOf<Int, Int>()
            val cTextColors = mutableMapOf<Int, Int>()
            for (c in 0 until maxCol) {
                getCellColor(r, c)?.let { cColors[c] = it }
                getCellTextColor(r, c)?.let { cTextColors[c] = it }
            }
            rowsData.add(RowSortState(rowValues, rColor, rTextColor, cColors, cTextColors))
        }
        if (rowsData.size <= 1) return

        val comparator = Comparator<RowSortState> { p1, p2 ->
            val v1 = p1.values.getOrElse(col) { "" }.trim()
            val v2 = p2.values.getOrElse(col) { "" }.trim()

            val empty1 = v1.isEmpty()
            val empty2 = v2.isEmpty()
            if (empty1 && empty2) return@Comparator 0
            if (empty1) return@Comparator 1
            if (empty2) return@Comparator -1

            val n1 = v1.removePrefix("$").removeSuffix("%").toDoubleOrNull()
            val n2 = v2.removePrefix("$").removeSuffix("%").toDoubleOrNull()

            val cmp = if (n1 != null && n2 != null) {
                n1.compareTo(n2)
            } else if (n1 != null) {
                -1
            } else if (n2 != null) {
                1
            } else {
                v1.compareTo(v2, ignoreCase = true)
            }
            if (ascending) cmp else -cmp
        }

        val sorted = rowsData.sortedWith(comparator)

        for (r in startRow..lastDataRow) {
            for (c in 0 until maxCol) {
                setCell(r, c, "")
                setCellColor(r, c, null)
                setCellTextColor(r, c, null)
            }
            setRowColor(r, null)
            setRowTextColor(r, null)
        }

        for (i in sorted.indices) {
            val targetRow = startRow + i
            val state = sorted[i]
            for (c in 0 until maxCol) {
                val value = state.values.getOrElse(c) { "" }
                if (value.isNotEmpty()) {
                    setCell(targetRow, c, value)
                }
                state.cellColors[c]?.let { setCellColor(targetRow, c, it) }
                state.cellTextColors[c]?.let { setCellTextColor(targetRow, c, it) }
            }
            setRowColor(targetRow, state.rowColor)
            setRowTextColor(targetRow, state.rowTextColor)
        }
        isFullLayoutDirty = true
    }

    fun clearColumn(col: Int) {
        if (col !in 0 until maxCol) return
        for (r in 0 until maxRow) {
            setCell(r, col, "")
            setCellColor(r, col, null)
            setCellTextColor(r, col, null)
        }
        isFullLayoutDirty = true
    }

    fun clearRow(row: Int) {
        if (row !in 0 until maxRow) return
        for (c in 0 until maxCol) {
            setCell(row, c, "")
            setCellColor(row, c, null)
            setCellTextColor(row, c, null)
        }
        setRowColor(row, null)
        setRowTextColor(row, null)
        isFullLayoutDirty = true
    }

    private fun evaluateRange(rangeStr: String): List<Double> {
        val parts = rangeStr.split(":")
        if (parts.size == 2) {
            val start = parseCellReference(parts[0].trim(), defaultRow = 0) ?: return emptyList()
            val isOpen = parts[1].trim().none { it.isDigit() }
            val dEnd = if (isOpen) maxOf(start.first, getLastUsedRow()) else maxOf(maxRow - 1, 0)
            val end = parseCellReference(parts[1].trim(), defaultRow = dEnd) ?: return emptyList()
            val rMin = minOf(start.first, end.first)
            val rMax = maxOf(start.first, end.first)
            val cMin = minOf(start.second, end.second)
            val cMax = maxOf(start.second, end.second)

            val result = ArrayList<Double>()
            for (r in rMin..rMax) {
                for (c in cMin..cMax) {
                    val raw = getCellValue(r, c).trim().removePrefix("$").removeSuffix("%")
                    val num = raw.toDoubleOrNull()
                    if (num != null) result.add(num)
                }
            }
            return result
        }
        return emptyList()
    }

    private fun formatNumber(value: Double): String {
        return if (value == value.toLong().toDouble()) {
            value.toLong().toString()
        } else {
            String.format(Locale.US, "%.2f", value)
        }
    }

    fun getRowHeightDp(r: Int, largeTouch: Boolean = currentLargeTouch): Float {
        if (customRowHeightsDp.containsKey(r)) {
            return customRowHeightsDp[r]!!
        }
        if (r in rowHeightsPx.indices && rowHeightsPx[r] > 0f && currentDensity > 0f) {
            return rowHeightsPx[r] / currentDensity
        }
        return if (largeTouch) 44f else defaultRowHeightDp
    }

    fun getColWidthDp(c: Int): Float {
        if (customColWidthsDp.containsKey(c)) {
            return customColWidthsDp[c]!!
        }
        if (c in colWidthsDp.indices && colWidthsDp[c] > 0f) {
            return colWidthsDp[c]
        }
        return defaultColWidthDp
    }

    fun setColWidthDp(c: Int, widthDp: Float) {
        if (c in 0 until maxCol) {
            val clamped = widthDp.coerceIn(20f, 500f)
            customColWidthsDp[c] = clamped
            if (c in colWidthsDp.indices) {
                colWidthsDp[c] = clamped
            }
            isFullLayoutDirty = true
        }
    }

    fun setRowHeightDp(r: Int, heightDp: Float) {
        if (r in 0 until maxRow) {
            val clamped = heightDp.coerceIn(20f, 500f)
            customRowHeightsDp[r] = clamped
            if (r in rowHeightsPx.indices && currentDensity > 0f) {
                rowHeightsPx[r] = clamped * currentDensity
            }
            isFullLayoutDirty = true
        }
    }

    fun isFullWidthRow(r: Int): Boolean {
        val range = getMergedRange(r, 0)
        return range != null && range.startCol == 0 && range.startRow == r && range.endRow == r && (range.endCol >= maxCol - 2 || (range.endCol - range.startCol >= 3 && range.endCol >= maxCol / 2) || range.endCol >= 3)
    }
    fun isTitleRow(r: Int): Boolean = isFullWidthRow(r)
    fun isDescriptionRow(r: Int): Boolean = isFullWidthRow(r)
    fun isBannerRow(r: Int): Boolean = isFullWidthRow(r)
    fun isHeaderRow(r: Int): Boolean = headerRows.contains(r)
    fun isDataRow(r: Int): Boolean = !isFullWidthRow(r) && !isHeaderRow(r) && r < maxRow && getCellValue(r, 0).isNotEmpty()

    fun getUsedColCount(): Int {
        var maxUsedCol = -1
        for ((key, cell) in cells) {
            if (cell.raw.isNotEmpty()) {
                val r = (key ushr 32).toInt()
                val c = (key and 0xFFFFFFFFL).toInt()
                if (!isBannerRow(r)) {
                    if (c > maxUsedCol) maxUsedCol = c
                }
            }
        }
        if (maxUsedCol < 0) {
            for ((key, cell) in cells) {
                if (cell.raw.isNotEmpty()) {
                    val c = (key and 0xFFFFFFFFL).toInt()
                    if (c > maxUsedCol) maxUsedCol = c
                }
            }
        }
        return if (maxUsedCol >= 0) {
            (maxUsedCol + 1).coerceIn(1, maxCol)
        } else {
            minOf(maxCol, 5)
        }
    }

    fun getUsedWidthPx(): Float {
        val usedCount = getUsedColCount()
        val lastIdx = (usedCount - 1).coerceIn(0, maxCol - 1)
        return getColOffsetPx(lastIdx) + getColWidthPx(lastIdx)
    }

    private fun computeRowHeightPx(
        r: Int,
        density: Float,
        largeTouch: Boolean,
        measureRowCellHeight: ((r: Int, c: Int, text: String, availableWidthPx: Float) -> Float)?
    ): Float {
        if (customRowHeightsDp.containsKey(r)) {
            return customRowHeightsDp[r]!! * density
        }
        val baseH = (if (largeTouch) 44f else defaultRowHeightDp) * density
        var maxH = baseH
        if (isFullWidthRow(r)) {
            val text = getCellValue(r, 0)
            if (text.isNotEmpty()) {
                val totalW = getUsedWidthPx()
                val h = measureRowCellHeight?.invoke(r, 0, text, totalW) ?: run {
                    val approxCharsPerLine = ((totalW - 20f * density) / (8.5f * density)).coerceAtLeast(1f)
                    val lines = text.split("\n").sumOf { line ->
                        maxOf(1, Math.ceil(line.length / approxCharsPerLine.toDouble()).toInt())
                    }
                    (lines * 18f * density + 16f * density).coerceAtLeast(baseH)
                }
                maxH = h.coerceAtLeast(baseH)
            }
        } else {
            for (c in 0 until maxCol) {
                if (isWrapEnabled(c)) {
                    val text = getCellValue(r, c)
                    if (text.isNotEmpty()) {
                        val colW = getColWidthPx(c)
                        val h = measureRowCellHeight?.invoke(r, c, text, colW) ?: run {
                            val approxCharsPerLine = ((colW - 10f * density) / (8.5f * density)).coerceAtLeast(1f)
                            val lines = text.split("\n").sumOf { line ->
                                maxOf(1, Math.ceil(line.length / approxCharsPerLine.toDouble()).toInt())
                            }
                            (lines * 16f * density + 10f * density).coerceAtLeast(baseH)
                        }
                        if (h > maxH) {
                            maxH = h
                        }
                    }
                }
            }
        }
        return maxH
    }

    fun updateLayoutIfNeeded(
        density: Float,
        largeTouch: Boolean = false,
        measureRowCellHeight: ((r: Int, c: Int, text: String, availableWidthPx: Float) -> Float)? = null
    ) {
        val structureOrStyleDirty = isFullLayoutDirty ||
            density != currentDensity ||
            currentLargeTouch != largeTouch ||
            rowOffsetsPx.size != maxRow ||
            rowHeightsPx.size != maxRow ||
            colOffsetsPx.size != maxCol ||
            colWidthsDp.size != maxCol

        if (structureOrStyleDirty) {
            currentDensity = density
            currentZoom = 1.0f
            currentLargeTouch = largeTouch
            rowOffsetsPx = FloatArray(maxRow)
            rowHeightsPx = FloatArray(maxRow)
            colOffsetsPx = FloatArray(maxCol)
            colWidthsDp = FloatArray(maxCol)
            if (wrapEnabled.size < maxCol) {
                val newArr = BooleanArray(maxCol)
                wrapEnabled.copyInto(newArr)
                wrapEnabled = newArr
            }

            for (c in 0 until maxCol) {
                colWidthsDp[c] = customColWidthsDp[c] ?: defaultColWidthDp
            }

            var currentX = 0f
            for (c in 0 until maxCol) {
                colOffsetsPx[c] = currentX
                currentX += getColWidthDp(c) * density
            }
            totalWidthPx = currentX

            var currentY = 0f
            for (r in 0 until maxRow) {
                rowOffsetsPx[r] = currentY
                val h = computeRowHeightPx(r, density, largeTouch, measureRowCellHeight)
                rowHeightsPx[r] = h
                currentY += h
            }
            totalHeightPx = currentY

            isFullLayoutDirty = false
            dirtyColumns.clear()
            dirtyRows.clear()
            return
        }

        if (dirtyColumns.isNotEmpty()) {
            dirtyColumns.clear()
        }

        if (dirtyRows.isNotEmpty()) {
            val minDirtyRow = dirtyRows.minOrNull() ?: 0
            for (r in dirtyRows) {
                if (r in rowHeightsPx.indices) {
                    rowHeightsPx[r] = computeRowHeightPx(r, density, largeTouch, measureRowCellHeight)
                }
            }
            dirtyRows.clear()

            var currentY = if (minDirtyRow in rowOffsetsPx.indices) rowOffsetsPx[minDirtyRow] else 0f
            for (r in minDirtyRow until maxRow) {
                rowOffsetsPx[r] = currentY
                currentY += rowHeightsPx[r]
            }
            totalHeightPx = currentY
        }
    }

    fun updateLayoutIfNeeded(density: Float, zoom: Float, largeTouch: Boolean) {
        updateLayoutIfNeeded(density, largeTouch)
    }

    fun getRowOffsetPx(r: Int): Float = if (r in rowOffsetsPx.indices) rowOffsetsPx[r] else r * defaultRowHeightDp * currentDensity
    fun getColOffsetPx(c: Int): Float = if (c in colOffsetsPx.indices) colOffsetsPx[c] else c * defaultColWidthDp * currentDensity
    fun getRowHeightPx(r: Int): Float = if (r in rowHeightsPx.indices) rowHeightsPx[r] else getRowHeightDp(r) * currentDensity
    fun getColWidthPx(c: Int): Float = getColWidthDp(c) * currentDensity

    fun getRowOffset(r: Int): Float = getRowOffsetPx(r)
    fun getColOffset(c: Int): Float = getColOffsetPx(c)
    fun getRowHeight(r: Int): Float = getRowHeightDp(r)
    fun getColWidth(c: Int): Float = getColWidthDp(c)

    fun getRowAt(yPx: Float): Int {
        if (rowOffsetsPx.isEmpty() || yPx <= 0f) return 0
        var low = 0
        var high = rowOffsetsPx.size - 1
        var best = 0
        while (low <= high) {
            val mid = (low + high) ushr 1
            val start = rowOffsetsPx[mid]
            val end = start + getRowHeightPx(mid)
            if (yPx >= start && yPx < end) return mid
            if (yPx < start) {
                high = mid - 1
            } else {
                best = mid
                low = mid + 1
            }
        }
        return best.coerceIn(0, maxRow - 1)
    }

    fun getColAt(xPx: Float): Int {
        if (colOffsetsPx.isEmpty() || xPx <= 0f) return 0
        var low = 0
        var high = colOffsetsPx.size - 1
        var best = 0
        while (low <= high) {
            val mid = (low + high) ushr 1
            val start = colOffsetsPx[mid]
            val end = start + getColWidthPx(mid)
            if (xPx >= start && xPx < end) return mid
            if (xPx < start) {
                high = mid - 1
            } else {
                best = mid
                low = mid + 1
            }
        }
        return best.coerceIn(0, maxCol - 1)
    }

    fun getColumnName(col: Int): String {
        var c = col
        var name = ""
        while (c >= 0) {
            name = ('A' + (c % 26)) + name
            c = (c / 26) - 1
        }
        return name
    }

    fun getColumnHeaderName(col: Int, forRow: Int = 0): String {
        for (r in forRow downTo 0) {
            if (isBannerRow(r)) {
                return "Column ${getColumnName(col)}"
            }
            if (isHeaderRow(r)) {
                val cellVal = getCellValue(r, col).trim()
                return if (cellVal.isNotEmpty()) cellVal else "Column ${getColumnName(col)}"
            }
        }
        return "Column ${getColumnName(col)}"
    }

    fun setHeaderBgColor(color: Int?) {
        currentSheet.headerBgColor = color
        for (hr in headerRows) {
            setRowColor(hr, color)
        }
        isFullLayoutDirty = true
    }

    fun setHeaderTextColor(color: Int?) {
        currentSheet.headerTextColor = color
        for (hr in headerRows) {
            setRowTextColor(hr, color)
        }
        isFullLayoutDirty = true
    }

    fun mergeRange(startRow: Int, startCol: Int, endRow: Int, endCol: Int) {
        val sR = minOf(startRow, endRow)
        val eR = maxOf(startRow, endRow)
        val sC = minOf(startCol, endCol)
        val eC = maxOf(startCol, endCol)

        mergedRanges.removeIf { it.contains(sR, sC) || it.contains(eR, eC) || (it.startRow >= sR && it.endRow <= eR && it.startCol >= sC && it.endCol <= eC) }

        // If top-left cell is empty, preserve the first non-empty value in the range
        var topLeftVal = getCellValue(sR, sC)
        if (topLeftVal.isEmpty()) {
            for (r in sR..eR) {
                for (c in sC..eC) {
                    val cellVal = getCellValue(r, c)
                    if (cellVal.isNotEmpty()) {
                        setCell(sR, sC, cellVal)
                        topLeftVal = cellVal
                        break
                    }
                }
                if (topLeftVal.isNotEmpty()) break
            }
        }

        // Excel behavior: keep top-left value, clear others in the range
        for (r in sR..eR) {
            for (c in sC..eC) {
                if (r != sR || c != sC) {
                    setCell(r, c, "")
                }
            }
        }

        mergedRanges.add(CellRange(sR, sC, eR, eC))
        isFullLayoutDirty = true
    }

    fun unmergeAt(r: Int, c: Int) {
        mergedRanges.removeIf { it.contains(r, c) }
        isFullLayoutDirty = true
    }

    fun unmergeRow(r: Int) {
        mergedRanges.removeIf { r in it.startRow..it.endRow }
        isFullLayoutDirty = true
    }

    fun getMergedRange(r: Int, c: Int): CellRange? {
        return mergedRanges.find { it.contains(r, c) }
    }

    fun isMergedTopLeft(r: Int, c: Int): Boolean {
        val range = getMergedRange(r, c)
        return range != null && range.isTopLeft(r, c)
    }

    fun isMergedPart(r: Int, c: Int): Boolean {
        val range = getMergedRange(r, c)
        return range != null && !range.isTopLeft(r, c)
    }

    fun insertRow(r: Int) {
        if (r < 0 || r >= maxRow) return
        for (row in maxRow - 1 downTo r) {
            for (c in 0 until maxCol) {
                val oldKey = cellKey(row, c)
                val newKey = cellKey(row + 1, c)
                cells[oldKey]?.let { cells[newKey] = it; cells.remove(oldKey) }
                cellColors[oldKey]?.let { cellColors[newKey] = it; cellColors.remove(oldKey) }
                cellTextColors[oldKey]?.let { cellTextColors[newKey] = it; cellTextColors.remove(oldKey) }
            }
            if (rowColors.containsKey(row)) {
                rowColors[row + 1] = rowColors[row]!!
                rowColors.remove(row)
            }
            if (rowTextColors.containsKey(row)) {
                rowTextColors[row + 1] = rowTextColors[row]!!
                rowTextColors.remove(row)
            }
        }
        for (range in mergedRanges) {
            if (range.startRow >= r) {
                range.startRow++
                range.endRow++
            } else if (range.endRow >= r) {
                range.endRow++
            }
        }
        val shiftedHeaders = HashSet<Int>()
        for (hr in headerRows) {
            if (hr >= r) shiftedHeaders.add(hr + 1) else shiftedHeaders.add(hr)
        }
        headerRows.clear()
        headerRows.addAll(shiftedHeaders)
        maxRow++
        isFullLayoutDirty = true
    }

    fun deleteRow(r: Int) {
        if (r < 0 || r >= maxRow) return
        for (row in r until maxRow - 1) {
            for (c in 0 until maxCol) {
                val nextKey = cellKey(row + 1, c)
                val currKey = cellKey(row, c)
                cells.remove(currKey)
                cellColors.remove(currKey)
                cellTextColors.remove(currKey)
                cells[nextKey]?.let { cells[currKey] = it; cells.remove(nextKey) }
                cellColors[nextKey]?.let { cellColors[currKey] = it; cellColors.remove(nextKey) }
                cellTextColors[nextKey]?.let { cellTextColors[currKey] = it; cellTextColors.remove(nextKey) }
            }
            if (rowColors.containsKey(row + 1)) {
                rowColors[row] = rowColors[row + 1]!!
            } else {
                rowColors.remove(row)
            }
            if (rowTextColors.containsKey(row + 1)) {
                rowTextColors[row] = rowTextColors[row + 1]!!
            } else {
                rowTextColors.remove(row)
            }
        }
        for (c in 0 until maxCol) {
            cells.remove(cellKey(maxRow - 1, c))
            cellColors.remove(cellKey(maxRow - 1, c))
            cellTextColors.remove(cellKey(maxRow - 1, c))
        }
        rowColors.remove(maxRow - 1)
        rowTextColors.remove(maxRow - 1)

        val iterator = mergedRanges.iterator()
        while (iterator.hasNext()) {
            val range = iterator.next()
            if (range.startRow == r && range.endRow == r) {
                iterator.remove()
            } else if (range.endRow < r) {
                // unaffected
            } else {
                if (range.startRow > r) range.startRow--
                if (range.endRow >= r) range.endRow--
            }
        }

        headerRows.remove(r)
        val shiftedDelHeaders = HashSet<Int>()
        for (hr in headerRows) {
            if (hr > r) shiftedDelHeaders.add(hr - 1) else if (hr < r) shiftedDelHeaders.add(hr)
        }
        headerRows.clear()
        headerRows.addAll(shiftedDelHeaders)

        maxRow = (maxRow - 1).coerceAtLeast(1)
        isFullLayoutDirty = true
    }

    fun setHeaderRow(r: Int) {
        headerRows.add(r)
        isFullLayoutDirty = true
    }

    fun clearHeaderRow(r: Int) {
        headerRows.remove(r)
        isFullLayoutDirty = true
    }

    fun toggleHeaderRow(r: Int) {
        if (headerRows.contains(r)) {
            headerRows.remove(r)
        } else {
            headerRows.add(r)
        }
        isFullLayoutDirty = true
    }

    fun setCellBold(r: Int, c: Int, bold: Boolean) {
        val key = cellKey(r, c)
        if (bold) cellBold[key] = true else cellBold.remove(key)
    }
    fun getCellBold(r: Int, c: Int): Boolean = cellBold[cellKey(r, c)] == true

    fun setCellItalic(r: Int, c: Int, italic: Boolean) {
        val key = cellKey(r, c)
        if (italic) cellItalic[key] = true else cellItalic.remove(key)
    }
    fun getCellItalic(r: Int, c: Int): Boolean = cellItalic[cellKey(r, c)] == true

    fun setCellAlignment(r: Int, c: Int, align: Int) {
        val key = cellKey(r, c)
        if (align in 0..2) cellAlign[key] = align else cellAlign.remove(key)
    }
    fun getCellAlignment(r: Int, c: Int): Int = cellAlign[cellKey(r, c)] ?: 0

    fun setCellNumberFormat(r: Int, c: Int, fmt: String) {
        val key = cellKey(r, c)
        if (fmt == "General") cellNumFmt.remove(key) else cellNumFmt[key] = fmt
    }
    fun getCellNumberFormat(r: Int, c: Int): String = cellNumFmt[cellKey(r, c)] ?: "General"

    fun setCellBorders(r: Int, c: Int, border: Int) {
        val key = cellKey(r, c)
        if (border in 0..2) cellBorders[key] = border else cellBorders.remove(key)
    }
    fun getCellBorders(r: Int, c: Int): Int = cellBorders[cellKey(r, c)] ?: 0

    fun pushUndo(description: String) {
        val cellMap = HashMap<Long, CellData>()
        for ((k, v) in cells) {
            cellMap[k] = CellData(v.raw, v.evaluated)
        }
        undoStack.addFirst(EngineState(
            cellMap,
            HashMap(cellColors),
            HashMap(cellTextColors),
            HashMap(cellBold),
            HashMap(cellItalic),
            HashMap(cellAlign),
            HashMap(cellNumFmt),
            HashMap(cellBorders),
            HashMap(rowColors),
            HashMap(rowTextColors),
            HashMap(columnColors),
            HashMap(columnTextColors),
            maxRow,
            maxCol,
            HashSet(mergedRanges),
            HashSet(headerRows),
            headerBgColor,
            headerTextColor,
            description
        ))
        if (undoStack.size > 50) {
            undoStack.removeLast()
        }
        redoStack.clear()
    }

    fun undo(): String? {
        if (undoStack.isEmpty()) return null
        val currentState = EngineState(
            cells.entries.associate { it.key to CellData(it.value.raw, it.value.evaluated) },
            HashMap(cellColors),
            HashMap(cellTextColors),
            HashMap(cellBold),
            HashMap(cellItalic),
            HashMap(cellAlign),
            HashMap(cellNumFmt),
            HashMap(cellBorders),
            HashMap(rowColors),
            HashMap(rowTextColors),
            HashMap(columnColors),
            HashMap(columnTextColors),
            maxRow,
            maxCol,
            HashSet(mergedRanges),
            HashSet(headerRows),
            headerBgColor,
            headerTextColor,
            "Current State"
        )
        val prevState = undoStack.removeFirst()
        redoStack.addFirst(currentState)

        cells.clear()
        for ((k, v) in prevState.cells) {
            cells[k] = CellData(v.raw, v.evaluated)
        }
        cellColors.clear(); cellColors.putAll(prevState.cellColors)
        cellTextColors.clear(); cellTextColors.putAll(prevState.cellTextColors)
        cellBold.clear(); cellBold.putAll(prevState.cellBold)
        cellItalic.clear(); cellItalic.putAll(prevState.cellItalic)
        cellAlign.clear(); cellAlign.putAll(prevState.cellAlign)
        cellNumFmt.clear(); cellNumFmt.putAll(prevState.cellNumFmt)
        cellBorders.clear(); cellBorders.putAll(prevState.cellBorders)
        rowColors.clear(); rowColors.putAll(prevState.rowColors)
        rowTextColors.clear(); rowTextColors.putAll(prevState.rowTextColors)
        columnColors.clear(); columnColors.putAll(prevState.columnColors)
        columnTextColors.clear(); columnTextColors.putAll(prevState.columnTextColors)
        maxRow = prevState.maxRow
        maxCol = prevState.maxCol
        mergedRanges.clear(); mergedRanges.addAll(prevState.mergedRanges)
        headerRows.clear(); headerRows.addAll(prevState.headerRows)
        currentSheet.headerBgColor = prevState.headerBgColor
        currentSheet.headerTextColor = prevState.headerTextColor
        clearCellCaches()
        recalculateAllFormulas()
        isFullLayoutDirty = true
        return prevState.description
    }

    fun redo(): String? {
        if (redoStack.isEmpty()) return null
        val currentState = EngineState(
            cells.entries.associate { it.key to CellData(it.value.raw, it.value.evaluated) },
            HashMap(cellColors),
            HashMap(cellTextColors),
            HashMap(cellBold),
            HashMap(cellItalic),
            HashMap(cellAlign),
            HashMap(cellNumFmt),
            HashMap(cellBorders),
            HashMap(rowColors),
            HashMap(rowTextColors),
            HashMap(columnColors),
            HashMap(columnTextColors),
            maxRow,
            maxCol,
            HashSet(mergedRanges),
            HashSet(headerRows),
            headerBgColor,
            headerTextColor,
            "Current State"
        )
        val nextState = redoStack.removeFirst()
        undoStack.addFirst(currentState)

        cells.clear()
        for ((k, v) in nextState.cells) {
            cells[k] = CellData(v.raw, v.evaluated)
        }
        cellColors.clear(); cellColors.putAll(nextState.cellColors)
        cellTextColors.clear(); cellTextColors.putAll(nextState.cellTextColors)
        cellBold.clear(); cellBold.putAll(nextState.cellBold)
        cellItalic.clear(); cellItalic.putAll(nextState.cellItalic)
        cellAlign.clear(); cellAlign.putAll(nextState.cellAlign)
        cellNumFmt.clear(); cellNumFmt.putAll(nextState.cellNumFmt)
        cellBorders.clear(); cellBorders.putAll(nextState.cellBorders)
        rowColors.clear(); rowColors.putAll(nextState.rowColors)
        rowTextColors.clear(); rowTextColors.putAll(nextState.rowTextColors)
        columnColors.clear(); columnColors.putAll(nextState.columnColors)
        columnTextColors.clear(); columnTextColors.putAll(nextState.columnTextColors)
        maxRow = nextState.maxRow
        maxCol = nextState.maxCol
        mergedRanges.clear(); mergedRanges.addAll(nextState.mergedRanges)
        headerRows.clear(); headerRows.addAll(nextState.headerRows)
        currentSheet.headerBgColor = nextState.headerBgColor
        currentSheet.headerTextColor = nextState.headerTextColor
        clearCellCaches()
        recalculateAllFormulas()
        isFullLayoutDirty = true
        return nextState.description
    }

    fun clearHistory() {
        undoStack.clear()
        redoStack.clear()
    }

    fun fillDown(startR: Int, startC: Int, endR: Int, endC: Int) {
        pushUndo("Fill down")
        for (c in startC..endC) {
            val sourceVal = getCellFormulaOrValue(startR, c)
            for (r in (startR + 1)..endR) {
                val num = sourceVal.toDoubleOrNull()
                val adjusted = if (num != null) formatNumber(num + (r - startR)) else sourceVal
                setCell(r, c, adjusted)
            }
        }
    }

    fun fillRight(startR: Int, startC: Int, endR: Int, endC: Int) {
        pushUndo("Fill right")
        for (r in startR..endR) {
            val sourceVal = getCellFormulaOrValue(r, startC)
            for (c in (startC + 1)..endC) {
                val num = sourceVal.toDoubleOrNull()
                val adjusted = if (num != null) formatNumber(num + (c - startC)) else sourceVal
                setCell(r, c, adjusted)
            }
        }
    }

    enum class PasteMode { VALUES_ONLY, FORMATS_ONLY, FORMULAS_ONLY }

    fun pasteSpecial(targetR: Int, targetC: Int, sourceR: Int, sourceC: Int, mode: PasteMode) {
        pushUndo("Paste special")
        val sKey = cellKey(sourceR, sourceC)
        val tKey = cellKey(targetR, targetC)
        when (mode) {
            PasteMode.VALUES_ONLY -> {
                val valStr = getCellValue(sourceR, sourceC)
                setCell(targetR, targetC, valStr)
            }
            PasteMode.FORMATS_ONLY -> {
                cellColors[sKey]?.let { cellColors[tKey] = it }
                cellTextColors[sKey]?.let { cellTextColors[tKey] = it }
                cellBold[sKey]?.let { cellBold[tKey] = it }
                cellItalic[sKey]?.let { cellItalic[tKey] = it }
                cellAlign[sKey]?.let { cellAlign[tKey] = it }
                cellNumFmt[sKey]?.let { cellNumFmt[tKey] = it }
                cellBorders[sKey]?.let { cellBorders[tKey] = it }
            }
            PasteMode.FORMULAS_ONLY -> {
                val raw = cells[sKey]?.raw ?: ""
                if (raw.startsWith("=")) {
                    setCell(targetR, targetC, raw)
                }
            }
        }
    }

    fun findAndReplace(find: String, replace: String, matchCase: Boolean): Int {
        if (find.isEmpty()) return 0
        pushUndo("Find & Replace")
        var count = 0
        for ((key, cell) in cells) {
            val text = cell.raw
            val newText = if (matchCase) {
                text.replace(find, replace)
            } else {
                text.replace(find, replace, ignoreCase = true)
            }
            if (newText != text) {
                cell.raw = newText
                cell.evaluated = null
                formulaCellKeys.remove(key)
                if (newText.startsWith("=")) {
                    formulaCellKeys.add(key)
                }
                count++
            }
        }
        recalculateAllFormulas()
        isFullLayoutDirty = true
        return count
    }

    fun findMatches(query: String, matchCase: Boolean): List<Pair<Int, Int>> {
        if (query.isEmpty()) return emptyList()
        val matches = mutableListOf<Pair<Int, Int>>()
        for (r in 0 until maxRow) {
            for (c in 0 until maxCol) {
                val text = getCellValue(r, c)
                val matchesCell = if (matchCase) {
                    text.contains(query)
                } else {
                    text.contains(query, ignoreCase = true)
                }
                if (matchesCell) {
                    matches.add(Pair(r, c))
                }
            }
        }
        return matches
    }

    fun replaceSingleMatch(r: Int, c: Int, find: String, replace: String, matchCase: Boolean): Boolean {
        if (find.isEmpty()) return false
        val key = cellKey(r, c)
        val cell = cells[key] ?: return false
        val text = cell.raw
        val newText = if (matchCase) {
            text.replaceFirst(find, replace)
        } else {
            val idx = text.indexOf(find, ignoreCase = true)
            if (idx != -1) {
                text.substring(0, idx) + replace + text.substring(idx + find.length)
            } else text
        }
        if (newText != text) {
            pushUndo("Replace")
            cell.raw = newText
            cell.evaluated = null
            formulaCellKeys.remove(key)
            if (newText.startsWith("=")) {
                formulaCellKeys.add(key)
            }
            recalculateAllFormulas()
            isFullLayoutDirty = true
            return true
        }
        return false
    }

    private fun evaluateIf(inner: String): String {
        val args = splitArguments(inner)
        if (args.size < 3) return "#VALUE!"
        val cond = args[0].trim()
        val trueVal = args[1].trim().removeSurrounding("\"")
        val falseVal = args[2].trim().removeSurrounding("\"")
        return if (evaluateCondition(cond)) trueVal else falseVal
    }

    private fun findConditionOperator(condition: String): Pair<String, Int>? {
        var inQuotes = false
        var i = 0
        while (i < condition.length) {
            val ch = condition[i]
            if (ch == '"') {
                inQuotes = !inQuotes
                i++
                continue
            }
            if (!inQuotes) {
                if (i + 1 < condition.length) {
                    val two = condition.substring(i, i + 2)
                    if (two == ">=" || two == "<=" || two == "<>") {
                        return Pair(two, i)
                    }
                }
                val one = condition.substring(i, i + 1)
                if (one == ">" || one == "<" || one == "=") {
                    return Pair(one, i)
                }
            }
            i++
        }
        return null
    }

    private fun compareTwoValues(leftStr: String, op: String, rightStr: String): Boolean {
        val lClean = leftStr.trim()
        val rClean = rightStr.trim().removeSurrounding("\"").removeSurrounding("'")
        val lNum = lClean.toDoubleOrNull()
        val rNum = rClean.toDoubleOrNull()
        return if (lNum != null && rNum != null) {
            when (op) {
                ">" -> lNum > rNum
                "<" -> lNum < rNum
                ">=" -> lNum >= rNum
                "<=" -> lNum <= rNum
                "=" -> lNum == rNum
                "<>" -> lNum != rNum
                else -> false
            }
        } else {
            val cmp = lClean.compareTo(rClean, ignoreCase = true)
            when (op) {
                "=" -> cmp == 0
                "<>" -> cmp != 0
                ">" -> cmp > 0
                "<" -> cmp < 0
                ">=" -> cmp >= 0
                "<=" -> cmp <= 0
                else -> false
            }
        }
    }

    private fun evaluateConditionArray(
        cond: String,
        rMin: Int,
        rMax: Int,
        originR: Int = -1,
        originC: Int = -1
    ): List<Boolean> {
        val opPair = findConditionOperator(cond)
        val numRows = maxOf(0, rMax - rMin + 1)
        if (numRows == 0) return emptyList()

        if (opPair == null) {
            val trimmed = cond.trim()
            if (trimmed.contains(":")) {
                val p = trimmed.split(":")
                val start = parseCellReference(p[0].trim(), defaultRow = 0)
                val isOpen = p[1].trim().none { it.isDigit() }
                val dEnd = if (isOpen && start != null) maxOf(start.first, getLastUsedRow()) else maxOf(maxRow - 1, 0)
                val end = parseCellReference(p[1].trim(), defaultRow = dEnd)
                if (start != null && end != null) {
                    val c = start.second
                    return (rMin..rMax).map { r ->
                        val v = getCellValue(r, c).trim()
                        v.isNotEmpty() && v != "0" && !v.equals("FALSE", ignoreCase = true)
                    }
                }
            }
            val single = evaluateCondition(cond)
            return List(numRows) { single }
        }

        val op = opPair.first
        val opIdx = opPair.second
        val left = cond.substring(0, opIdx).trim()
        val right = cond.substring(opIdx + op.length).trim()

        val leftIsRange = left.contains(":")
        val rightIsRange = right.contains(":")

        val leftRange = if (leftIsRange) {
            val p = left.split(":")
            val s = parseCellReference(p[0].trim(), defaultRow = 0)
            val isOpen = p[1].trim().none { it.isDigit() }
            val dEnd = if (isOpen && s != null) maxOf(s.first, getLastUsedRow()) else maxOf(maxRow - 1, 0)
            val e = parseCellReference(p[1].trim(), defaultRow = dEnd)
            if (s != null && e != null) Pair(s, e) else null
        } else null

        val rightRange = if (rightIsRange) {
            val p = right.split(":")
            val s = parseCellReference(p[0].trim(), defaultRow = 0)
            val isOpen = p[1].trim().none { it.isDigit() }
            val dEnd = if (isOpen && s != null) maxOf(s.first, getLastUsedRow()) else maxOf(maxRow - 1, 0)
            val e = parseCellReference(p[1].trim(), defaultRow = dEnd)
            if (s != null && e != null) Pair(s, e) else null
        } else null

        if (leftRange == null && rightRange == null) {
            val single = evaluateCondition(cond)
            return List(numRows) { single }
        }

        val rightConst = if (rightRange == null) {
            if (right == "\"\"" || right == "''") "" else getValOrRaw(right)
        } else ""

        val leftConst = if (leftRange == null) {
            if (left == "\"\"" || left == "''") "" else getValOrRaw(left)
        } else ""

        val result = ArrayList<Boolean>(numRows)
        for (r in rMin..rMax) {
            val lVal = if (leftRange != null) {
                val lCol = leftRange.first.second
                getCellValue(r, lCol)
            } else {
                leftConst
            }

            val rVal = if (rightRange != null) {
                val rCol = rightRange.first.second
                getCellValue(r, rCol)
            } else {
                rightConst
            }

            result.add(compareTwoValues(lVal, op, rVal))
        }
        return result
    }

    private fun evaluateCondition(cond: String): Boolean {
        val opPair = findConditionOperator(cond) ?: return false
        val op = opPair.first
        val opIdx = opPair.second
        val left = cond.substring(0, opIdx).trim()
        val right = cond.substring(opIdx + op.length).trim()

        if (left.contains(":") || right.contains(":")) {
            val list = evaluateConditionArray(cond, 0, maxOf(maxRow - 1, 0))
            return list.any { it }
        }

        val leftStr = if (left == "\"\"" || left == "''") "" else getValOrRaw(left)
        val rightStr = if (right == "\"\"" || right == "''") "" else getValOrRaw(right)
        return compareTwoValues(leftStr, op, rightStr)
    }

    private fun getValOrRaw(token: String): String {
        val coords = parseCellReference(token.uppercase(Locale.ROOT))
        if (coords != null) return getCellValue(coords.first, coords.second)
        return token.removeSurrounding("\"")
    }

    private fun evaluateSumIf(inner: String): String {
        val args = splitArguments(inner)
        if (args.size < 2) return "#VALUE!"
        val rangeVals = evaluateRangeWithCoords(args[0].trim())
        val criteria = args[1].trim().removeSurrounding("\"")
        val sumVals = if (args.size >= 3) evaluateRangeWithCoords(args[2].trim()) else rangeVals
        var sum = 0.0
        for (i in rangeVals.indices) {
            if (matchesCriteria(rangeVals[i].third, criteria)) {
                sum += sumVals.getOrNull(i)?.third?.toDoubleOrNull() ?: 0.0
            }
        }
        return formatNumber(sum)
    }

    private fun evaluateCountIf(inner: String): String {
        val args = splitArguments(inner)
        if (args.size < 2) return "#VALUE!"
        val rangeVals = evaluateRangeWithCoords(args[0].trim())
        val criteria = args[1].trim().removeSurrounding("\"")
        var count = 0
        for (item in rangeVals) {
            if (matchesCriteria(item.third, criteria)) count++
        }
        return count.toString()
    }

    private fun matchesCriteria(value: String, criteria: String): Boolean {
        val cleanCrit = criteria.trim()
        val op = listOf(">=", "<=", "<>", ">", "<", "=").find { cleanCrit.startsWith(it) }
        if (op != null) {
            val targetStr = cleanCrit.removePrefix(op).trim()
            val vNum = value.toDoubleOrNull()
            val tNum = targetStr.toDoubleOrNull()
            if (vNum != null && tNum != null) {
                return when (op) {
                    ">" -> vNum > tNum; "<" -> vNum < tNum; ">=" -> vNum >= tNum; "<=" -> vNum <= tNum; "=" -> vNum == tNum; "<>" -> vNum != tNum; else -> false
                }
            }
        }
        return value.equals(cleanCrit, ignoreCase = true)
    }

    private fun evaluateRangeWithCoords(rangeStr: String): List<Triple<Int, Int, String>> {
        val parts = rangeStr.uppercase(Locale.ROOT).split(":")
        if (parts.size == 2) {
            val start = parseCellReference(parts[0].trim(), defaultRow = 0) ?: return emptyList()
            val isOpen = parts[1].trim().none { it.isDigit() }
            val dEnd = if (isOpen) maxOf(start.first, getLastUsedRow()) else maxOf(maxRow - 1, 0)
            val end = parseCellReference(parts[1].trim(), defaultRow = dEnd) ?: return emptyList()
            val rMin = minOf(start.first, end.first)
            val rMax = maxOf(start.first, end.first)
            val cMin = minOf(start.second, end.second)
            val cMax = maxOf(start.second, end.second)
            val result = ArrayList<Triple<Int, Int, String>>()
            for (r in rMin..rMax) {
                for (c in cMin..cMax) {
                    result.add(Triple(r, c, getCellValue(r, c)))
                }
            }
            return result
        }
        return emptyList()
    }

    private fun evaluateVLookup(inner: String): String {
        val args = splitArguments(inner)
        if (args.size < 3) return "#VALUE!"
        val lookupVal = getValOrRaw(args[0].trim())
        val rangeStr = args[1].trim().uppercase(Locale.ROOT)
        val colIndex = args[2].trim().toIntOrNull() ?: return "#VALUE!"
        val parts = rangeStr.split(":")
        if (parts.size != 2) return "#VALUE!"
        val start = parseCellReference(parts[0].trim(), defaultRow = 0) ?: return "#VALUE!"
        val isOpen = parts[1].trim().none { it.isDigit() }
        val dEnd = if (isOpen) maxOf(start.first, getLastUsedRow()) else maxOf(maxRow - 1, 0)
        val end = parseCellReference(parts[1].trim(), defaultRow = dEnd) ?: return "#VALUE!"
        val rMin = minOf(start.first, end.first)
        val rMax = maxOf(start.first, end.first)
        val cMin = minOf(start.second, end.second)
        val cMax = maxOf(start.second, end.second)
        val targetCol = cMin + colIndex - 1
        if (targetCol > cMax) return "#REF!"
        for (r in rMin..rMax) {
            if (getCellValue(r, cMin).equals(lookupVal, ignoreCase = true)) {
                return getCellValue(r, targetCol)
            }
        }
        return "#N/A"
    }

    private fun evaluateXLookup(inner: String): String {
        val args = splitArguments(inner)
        if (args.size < 3) return "#VALUE!"
        val lookupVal = getValOrRaw(args[0].trim())
        val lookupRange = evaluateRangeWithCoords(args[1].trim())
        val returnRange = evaluateRangeWithCoords(args[2].trim())
        val notFound = args.getOrNull(3)?.trim()?.removeSurrounding("\"") ?: "#N/A"
        for (i in lookupRange.indices) {
            if (lookupRange[i].third.equals(lookupVal, ignoreCase = true)) {
                return returnRange.getOrNull(i)?.third ?: notFound
            }
        }
        return notFound
    }

    companion object {
        private val NUMERIC_REGEX = Regex("^[+-]?\\d+([.,]\\d+)?$")
    }
}
