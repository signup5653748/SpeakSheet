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
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

class SpreadsheetEngine {

    var maxRow = 50
    var maxCol = 15
    var frozenRows = 0
    var frozenCols = 0

    val defaultRowHeightDp = 32f
    val defaultColWidthDp = 90f

    private val cells = HashMap<Long, CellData>()
    private val cellRightAlignedCache = HashMap<Long, Boolean>()
    private val formulaCellKeys = HashSet<Long>()
    private val evaluatingCells = HashSet<Long>()
    private val spillOutputs = HashMap<Long, String>()
    private val spillSources = HashMap<Long, Long>()

    private var rowOffsetsPx = FloatArray(0)
    private var rowHeightsPx = FloatArray(0)
    private var colOffsetsPx = FloatArray(0)
    private var colWidthsDp = FloatArray(0)
    private var wrapEnabled = BooleanArray(0)
    private val cellColors = HashMap<Long, Int>()
    private val cellTextColors = HashMap<Long, Int>()
    private val columnColors = HashMap<Int, Int>()
    private val columnTextColors = HashMap<Int, Int>()
    private val rowColors = HashMap<Int, Int>()
    private val rowTextColors = HashMap<Int, Int>()
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

    data class CellData(
        var raw: String = "",
        var evaluated: String? = null
    )

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

    fun getCellValue(r: Int, c: Int): String {
        val key = cellKey(r, c)
        val cell = cells[key]
        if (cell != null && cell.raw.isNotEmpty()) {
            if (!cell.raw.startsWith("=")) {
                return cell.raw
            }
            cell.evaluated?.let { return it }

            if (!evaluatingCells.add(key)) {
                return "#CIRCULAR!"
            }
            return try {
                val eval = evaluateFormula(cell.raw, r, c)
                cell.evaluated = eval
                eval
            } finally {
                evaluatingCells.remove(key)
            }
        }
        return spillOutputs[key] ?: ""
    }

    fun getCellFormulaOrValue(r: Int, c: Int): String {
        return cells[cellKey(r, c)]?.raw ?: ""
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
        val key = cellKey(r, c)
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

        if (r >= maxRow || c >= maxCol) {
            maxRow = maxOf(maxRow, r + 1)
            maxCol = maxOf(maxCol, c + 1)
            if (wrapEnabled.size < maxCol) {
                val newArr = BooleanArray(maxCol)
                wrapEnabled.copyInto(newArr)
                wrapEnabled = newArr
            }
            isFullLayoutDirty = true
        } else {
            dirtyColumns.add(c)
            if (isWrapEnabled(c)) {
                dirtyRows.add(r)
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

    fun loadSampleData(title: String, data: List<List<String>>) {
        cells.clear()
        cellColors.clear()
        cellTextColors.clear()
        columnColors.clear()
        columnTextColors.clear()
        rowColors.clear()
        rowTextColors.clear()
        clearCellCaches()
        var maxC = 0
        data.forEachIndexed { r, rowValues ->
            rowValues.forEachIndexed { c, value ->
                if (value.isNotEmpty()) {
                    setCell(r, c, value)
                }
                if (c > maxC) maxC = c
            }
        }
        maxRow = maxOf(40, data.size + 10)
        maxCol = maxOf(10, maxC + 3)
        wrapEnabled = BooleanArray(maxCol)
        frozenRows = 0
        frozenCols = 0

        if (title.contains("Sectioned Report", ignoreCase = true) || data.size > 20) {
            setColumnWrap(0, true)
            val bannerRowsList = listOf(10, 17, 24, 31)
            val colors = listOf(0xFFFFF9C4.toInt(), 0xFFE3F2FD.toInt(), 0xFFE8F5E9.toInt(), 0xFFFFF3E0.toInt())
            bannerRowsList.forEachIndexed { idx, br ->
                if (br < maxRow) {
                    setRowColor(br, colors[idx])
                    mergeRange(br, 0, br, maxCol - 1)
                }
            }
        }
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
        columnColors.clear()
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

    private fun loadXLSX(inputStream: InputStream): Boolean {
        cells.clear()
        cellColors.clear()
        columnColors.clear()
        clearCellCaches()
        val sharedStrings = ArrayList<String>()
        val sheetBytes = HashMap<String, ByteArray>()

        try {
            val zip = ZipInputStream(inputStream)
            var entry: ZipEntry? = zip.nextEntry
            while (entry != null) {
                val entryName = entry.name
                if (entryName == "xl/sharedStrings.xml") {
                    parseSharedStrings(zip, sharedStrings)
                } else if (entryName.startsWith("xl/worksheets/sheet") && entryName.endsWith(".xml")) {
                    sheetBytes[entryName] = zip.readBytes()
                } else if (entryName == "xl/speaksheet_colors.txt") {
                    deserializeColors(String(zip.readBytes(), Charsets.UTF_8))
                }
                zip.closeEntry()
                entry = zip.nextEntry
            }

            val firstSheetBytes = sheetBytes["xl/worksheets/sheet1.xml"]
                ?: sheetBytes.values.firstOrNull()

            if (firstSheetBytes != null) {
                parseSheetXml(firstSheetBytes.inputStream(), sharedStrings)
                return true
            }
        } catch (_: Throwable) {
            return false
        }
        return false
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

    private fun parseSheetXml(stream: InputStream, sharedStrings: List<String>) {
        val factory = XmlPullParserFactory.newInstance()
        val parser = factory.newPullParser()
        parser.setInput(stream, "UTF-8")

        var eventType = parser.eventType
        var currentCellRef = ""
        var cellType = ""
        var inValue = false
        var inFormula = false
        var cellText = StringBuilder()
        var formulaText = StringBuilder()
        var maxR = 0
        var maxC = 0

        while (eventType != XmlPullParser.END_DOCUMENT) {
            when (eventType) {
                XmlPullParser.START_TAG -> {
                    when (parser.name) {
                        "c" -> {
                            currentCellRef = parser.getAttributeValue(null, "r") ?: ""
                            cellType = parser.getAttributeValue(null, "t") ?: ""
                            cellText.clear()
                            formulaText.clear()
                        }
                        "v" -> {
                            inValue = true
                            cellText.clear()
                        }
                        "f" -> {
                            inFormula = true
                            formulaText.clear()
                        }
                    }
                }
                XmlPullParser.TEXT -> {
                    if (inValue) {
                        cellText.append(parser.text)
                    }
                    if (inFormula) {
                        formulaText.append(parser.text)
                    }
                }
                XmlPullParser.END_TAG -> {
                    when (parser.name) {
                        "v" -> inValue = false
                        "f" -> inFormula = false
                        "c" -> {
                            if (currentCellRef.isNotEmpty()) {
                                val coords = parseCellReference(currentCellRef)
                                if (coords != null) {
                                    val (r, c) = coords
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
                                    setCell(r, c, finalVal)
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
        maxRow = maxOf(30, maxR + 10).coerceAtMost(300)
        maxCol = maxOf(10, maxC + 4).coerceAtMost(30)
        wrapEnabled = BooleanArray(maxCol)
        isFullLayoutDirty = true
    }

    private fun parseCellReference(ref: String): Pair<Int, Int>? {
        var col = 0
        var rowStr = ""
        for (ch in ref) {
            if (ch in 'A'..'Z') {
                col = col * 26 + (ch - 'A' + 1)
            } else if (ch.isDigit()) {
                rowStr += ch
            }
        }
        val row = rowStr.toIntOrNull() ?: return null
        return Pair(row - 1, col - 1)
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
        
        // Write simple workbook structure
        zip.putNextEntry(ZipEntry("[Content_Types].xml"))
        zip.write(
            """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
<Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>
<Default Extension="xml" ContentType="application/xml"/>
<Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/>
<Override PartName="/xl/worksheets/sheet1.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/>
</Types>""".toByteArray()
        )
        zip.closeEntry()

        zip.putNextEntry(ZipEntry("_rels/.rels"))
        zip.write(
            """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
<Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="xl/workbook.xml"/>
</Relationships>""".toByteArray()
        )
        zip.closeEntry()

        zip.putNextEntry(ZipEntry("xl/_rels/workbook.xml.rels"))
        zip.write(
            """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
<Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet1.xml"/>
</Relationships>""".toByteArray()
        )
        zip.closeEntry()

        zip.putNextEntry(ZipEntry("xl/workbook.xml"))
        zip.write(
            """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">
<sheets><sheet name="Sheet1" sheetId="1" r:id="rId1" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships"/></sheets>
</workbook>""".toByteArray()
        )
        zip.closeEntry()

        zip.putNextEntry(ZipEntry("xl/worksheets/sheet1.xml"))
        val sheetXml = StringBuilder("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main"><sheetData>""")
        
        for (r in 0 until maxRow) {
            var rowHasData = false
            for (c in 0 until maxCol) {
                if (getCellValue(r, c).isNotEmpty()) {
                    rowHasData = true
                    break
                }
            }
            if (rowHasData) {
                sheetXml.append("<row r=\"${r + 1}\">")
                for (c in 0 until maxCol) {
                    val raw = getCellFormulaOrValue(r, c)
                    val disp = getCellValue(r, c)
                    val ref = getColumnName(c) + (r + 1)
                    if (raw.startsWith("=")) {
                        sheetXml.append("<c r=\"$ref\"><f>${raw.removePrefix("=")}</f><v>$disp</v></c>")
                    } else if (raw.toDoubleOrNull() != null) {
                        sheetXml.append("<c r=\"$ref\"><v>$raw</v></c>")
                    } else if (raw.isNotEmpty()) {
                        sheetXml.append("<c r=\"$ref\" t=\"inlineStr\"><is><t>${raw.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")}</t></is></c>")
                    }
                }
                sheetXml.append("</row>")
            }
        }
        sheetXml.append("</sheetData></worksheet>")
        zip.write(sheetXml.toString().toByteArray())
        zip.closeEntry()

        if (cellColors.isNotEmpty() || columnColors.isNotEmpty()) {
            zip.putNextEntry(ZipEntry("xl/speaksheet_colors.txt"))
            zip.write(serializeColors().toByteArray(Charsets.UTF_8))
            zip.closeEntry()
        }

        zip.finish()
        zip.flush()
    }

    private fun evaluateFormula(formula: String, originR: Int = -1, originC: Int = -1): String {
        try {
            val clean = formula.trim().removePrefix("=").trim()
            val upper = clean.uppercase(Locale.ROOT)

            if (upper.startsWith("SUM(") && upper.endsWith(")")) {
                val rangeStr = upper.substring(4, upper.length - 1)
                val sum = evaluateRange(rangeStr).sum()
                return formatNumber(sum)
            }
            if (upper.startsWith("AVERAGE(") && upper.endsWith(")")) {
                val rangeStr = upper.substring(8, upper.length - 1)
                val vals = evaluateRange(rangeStr)
                if (vals.isEmpty()) return "0"
                return formatNumber(vals.average())
            }
            if (upper.startsWith("COUNT(") && upper.endsWith(")")) {
                val rangeStr = upper.substring(6, upper.length - 1)
                val vals = evaluateRange(rangeStr)
                return vals.size.toString()
            }
            if (upper.startsWith("MIN(") && upper.endsWith(")")) {
                val rangeStr = upper.substring(4, upper.length - 1)
                val vals = evaluateRange(rangeStr)
                return if (vals.isNotEmpty()) formatNumber(vals.minOrNull() ?: 0.0) else "0"
            }
            if (upper.startsWith("MAX(") && upper.endsWith(")")) {
                val rangeStr = upper.substring(4, upper.length - 1)
                val vals = evaluateRange(rangeStr)
                return if (vals.isNotEmpty()) formatNumber(vals.maxOrNull() ?: 0.0) else "0"
            }
            if (upper.startsWith("SORT(") && upper.endsWith(")")) {
                return evaluateSort(formula, clean, originR, originC)
            }
            if (upper.startsWith("IF(") && upper.endsWith(")")) {
                return evaluateIf(upper.substring(3, upper.length - 1))
            }
            if (upper.startsWith("SUMIF(") && upper.endsWith(")")) {
                return evaluateSumIf(upper.substring(6, upper.length - 1))
            }
            if (upper.startsWith("COUNTIF(") && upper.endsWith(")")) {
                return evaluateCountIf(upper.substring(8, upper.length - 1))
            }
            if (upper.startsWith("VLOOKUP(") && upper.endsWith(")")) {
                return evaluateVLookup(upper.substring(8, upper.length - 1))
            }
            if (upper.startsWith("XLOOKUP(") && upper.endsWith(")")) {
                return evaluateXLookup(upper.substring(8, upper.length - 1))
            }

            // Simple cell reference like =A1
            val refCoords = parseCellReference(upper)
            if (refCoords != null) {
                return getCellValue(refCoords.first, refCoords.second)
            }

            return clean
        } catch (_: Throwable) {
            return formula
        }
    }

    private fun evaluateSort(formula: String, clean: String, originR: Int, originC: Int): String {
        val inner = clean.substring(5, clean.length - 1).trim()
        val args = splitArguments(inner)
        if (args.isEmpty()) return "#VALUE!"

        val rangeStr = args[0].trim().uppercase(Locale.ROOT)
        val parts = rangeStr.split(":")
        if (parts.size != 2) return "#VALUE!"

        val start = parseCellReference(parts[0].trim()) ?: return "#VALUE!"
        val end = parseCellReference(parts[1].trim()) ?: return "#VALUE!"

        val rMin = minOf(start.first, end.first)
        val rMax = maxOf(start.first, end.first)
        val cMin = minOf(start.second, end.second)
        val cMax = maxOf(start.second, end.second)

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

        // Circular reference check
        if (originR in rMin..rMax && originC in cMin..cMax) {
            return "#CIRCULAR!"
        }

        val rowsData = ArrayList<List<String>>()
        for (r in rMin..rMax) {
            val row = ArrayList<String>()
            for (c in cMin..cMax) {
                row.add(getCellValue(r, c))
            }
            rowsData.add(row)
        }

        if (rowsData.isEmpty()) return ""

        val numColsInRange = (cMax - cMin + 1)
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
        val endRow = maxRow - 1
        if (endRow < startRow) return

        val rowsData = ArrayList<RowSortState>()
        for (r in startRow..endRow) {
            val rowValues = (0 until maxCol).map { c -> getCellFormulaOrValue(r, c) }
            val hasContent = (0 until maxCol).any { c -> getCellValue(r, c).isNotEmpty() }
            if (hasContent) {
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
        }
        if (rowsData.size <= 1) return

        val comparator = Comparator<RowSortState> { p1, p2 ->
            val v1 = p1.values.getOrElse(col) { "" }.trim()
            val v2 = p2.values.getOrElse(col) { "" }.trim()

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
            if (ascending) cmp else -cmp
        }

        val sorted = rowsData.sortedWith(comparator)
        for (i in rowsData.indices) {
            val targetRow = startRow + i
            val state = sorted[i]
            for (c in 0 until maxCol) {
                setCell(targetRow, c, "")
                setCellColor(targetRow, c, null)
                setCellTextColor(targetRow, c, null)
            }
            setRowColor(targetRow, null)
            setRowTextColor(targetRow, null)

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
            val start = parseCellReference(parts[0].trim()) ?: return emptyList()
            val end = parseCellReference(parts[1].trim()) ?: return emptyList()
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
        if (r in rowHeightsPx.indices && rowHeightsPx[r] > 0f && currentDensity > 0f) {
            return rowHeightsPx[r] / currentDensity
        }
        return if (largeTouch) 44f else defaultRowHeightDp
    }

    fun getColWidthDp(c: Int): Float {
        if (c in colWidthsDp.indices && colWidthsDp[c] > 0f) {
            return colWidthsDp[c]
        }
        return defaultColWidthDp
    }

    fun isTitleRow(r: Int): Boolean = (r == 0 && getCellValue(0, 0) == "EXAMPLE FILE NAME")
    fun isDescriptionRow(r: Int): Boolean = (r in 1..3)
    fun isBannerRow(r: Int): Boolean = (r == 10 || r == 17 || r == 24 || r == 31 || getCellValue(r, 0).startsWith("Section "))
    fun isHeaderRow(r: Int): Boolean = headerRows.contains(r) || r == 0 || r == 4 || r == 11 || r == 18 || r == 25 || getCellValue(r, 0).equals("Item", ignoreCase = true)
    fun isDataRow(r: Int): Boolean = !isTitleRow(r) && !isDescriptionRow(r) && !isHeaderRow(r) && !isBannerRow(r) && r < maxRow && getCellValue(r, 0).isNotEmpty()
    fun isFullWidthRow(r: Int): Boolean = isTitleRow(r) || isDescriptionRow(r) || isBannerRow(r)

    private fun computeRowHeightPx(
        r: Int,
        density: Float,
        largeTouch: Boolean,
        measureRowCellHeight: ((r: Int, c: Int, text: String, availableWidthPx: Float) -> Float)?
    ): Float {
        val baseH = (if (largeTouch) 44f else defaultRowHeightDp) * density
        var maxH = baseH
        if (isFullWidthRow(r)) {
            val text = getCellValue(r, 0)
            if (text.isNotEmpty()) {
                val totalW = (0 until maxCol).sumOf { getColWidthPx(it).toDouble() }.toFloat()
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

            val checkLimit = minOf(maxRow, 25)
            for (c in 0 until maxCol) {
                var maxLen = 4
                for (r in 0 until checkLimit) {
                    val len = getCellValue(r, c).length
                    if (len > maxLen) maxLen = len
                }
                val calculatedW = (maxLen * 8.5f + 24f).coerceIn(85f, 180f)
                colWidthsDp[c] = calculatedW
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

        var colLayoutChanged = false
        if (dirtyColumns.isNotEmpty()) {
            val minDirty = dirtyColumns.minOrNull() ?: 0
            val checkLimit = minOf(maxRow, 25)
            for (c in dirtyColumns) {
                if (c in colWidthsDp.indices) {
                    var maxLen = 4
                    for (r in 0 until checkLimit) {
                        val len = getCellValue(r, c).length
                        if (len > maxLen) maxLen = len
                    }
                    val calculatedW = (maxLen * 8.5f + 24f).coerceIn(85f, 180f)
                    if (colWidthsDp[c] != calculatedW) {
                        colWidthsDp[c] = calculatedW
                        colLayoutChanged = true
                        if (isWrapEnabled(c)) {
                            for (r in 0 until maxRow) {
                                if (getCellValue(r, c).isNotEmpty()) {
                                    dirtyRows.add(r)
                                }
                            }
                        }
                    }
                }
            }
            dirtyColumns.clear()

            if (colLayoutChanged) {
                var currentX = if (minDirty in colOffsetsPx.indices) colOffsetsPx[minDirty] else 0f
                for (c in minDirty until maxCol) {
                    colOffsetsPx[c] = currentX
                    currentX += getColWidthDp(c) * density
                }
                totalWidthPx = currentX
            }
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

    fun getColumnHeaderName(col: Int): String {
        val firstCell = getCellValue(0, col).trim()
        return if (firstCell.isNotEmpty()) {
            firstCell
        } else {
            "Column ${getColumnName(col)}"
        }
    }

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

    private val mergedRanges = HashSet<CellRange>()
    private val headerRows = HashSet<Int>()
    private var headerColor: Int? = null

    fun mergeRange(startRow: Int, startCol: Int, endRow: Int, endCol: Int) {
        val sR = minOf(startRow, endRow)
        val eR = maxOf(startRow, endRow)
        val sC = minOf(startCol, endCol)
        val eC = maxOf(startCol, endCol)

        mergedRanges.removeIf { it.contains(sR, sC) || it.contains(eR, eC) || (it.startRow >= sR && it.endRow <= eR && it.startCol >= sC && it.endCol <= eC) }

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

        maxRow = (maxRow - 1).coerceAtLeast(1)
        isFullLayoutDirty = true
    }

    fun setHeaderRowColor(color: Int?) {
        headerColor = color
        setRowColor(0, color)
        for (hr in headerRows) {
            setRowColor(hr, color)
        }
        isFullLayoutDirty = true
    }

    private val cellBold = HashMap<Long, Boolean>()
    private val cellItalic = HashMap<Long, Boolean>()
    private val cellAlign = HashMap<Long, Int>() // 0=left, 1=center, 2=right
    private val cellNumFmt = HashMap<Long, String>() // "General", "Number", "Currency", "Percent", "Date"
    private val cellBorders = HashMap<Long, Int>() // 0=none, 1=all, 2=outer

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

    data class EngineState(
        val cells: Map<Long, CellData>,
        val cellColors: Map<Long, Int>,
        val cellTextColors: Map<Long, Int>,
        val cellBold: Map<Long, Boolean>,
        val cellItalic: Map<Long, Boolean>,
        val cellAlign: Map<Long, Int>,
        val cellNumFmt: Map<Long, String>,
        val cellBorders: Map<Long, Int>,
        val mergedRanges: Set<CellRange>,
        val description: String
    )

    private val undoStack = ArrayDeque<EngineState>()
    private val redoStack = ArrayDeque<EngineState>()

    val canUndo: Boolean get() = undoStack.isNotEmpty()
    val canRedo: Boolean get() = redoStack.isNotEmpty()

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
            HashSet(mergedRanges),
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
            HashSet(mergedRanges),
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
        mergedRanges.clear(); mergedRanges.addAll(prevState.mergedRanges)
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
            HashSet(mergedRanges),
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
        mergedRanges.clear(); mergedRanges.addAll(nextState.mergedRanges)
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

    private fun evaluateIf(inner: String): String {
        val args = splitArguments(inner)
        if (args.size < 3) return "#VALUE!"
        val cond = args[0].trim()
        val trueVal = args[1].trim().removeSurrounding("\"")
        val falseVal = args[2].trim().removeSurrounding("\"")
        return if (evaluateCondition(cond)) trueVal else falseVal
    }

    private fun evaluateCondition(cond: String): Boolean {
        val op = listOf(">=", "<=", "<>", ">", "<", "=").find { cond.contains(it) } ?: return false
        val parts = cond.split(op, limit = 2)
        if (parts.size != 2) return false
        val leftStr = getValOrRaw(parts[0].trim())
        val rightStr = parts[1].trim().removeSurrounding("\"")
        val lNum = leftStr.toDoubleOrNull()
        val rNum = rightStr.toDoubleOrNull()
        return if (lNum != null && rNum != null) {
            when (op) {
                ">" -> lNum > rNum; "<" -> lNum < rNum; ">=" -> lNum >= rNum; "<=" -> lNum <= rNum; "=" -> lNum == rNum; "<>" -> lNum != rNum; else -> false
            }
        } else {
            val cmp = leftStr.compareTo(rightStr, ignoreCase = true)
            when (op) { "=" -> cmp == 0; "<>" -> cmp != 0; ">" -> cmp > 0; "<" -> cmp < 0; ">=" -> cmp >= 0; "<=" -> cmp <= 0; else -> false }
        }
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
            val start = parseCellReference(parts[0].trim()) ?: return emptyList()
            val end = parseCellReference(parts[1].trim()) ?: return emptyList()
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
        val start = parseCellReference(parts[0].trim()) ?: return "#VALUE!"
        val end = parseCellReference(parts[1].trim()) ?: return "#VALUE!"
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
