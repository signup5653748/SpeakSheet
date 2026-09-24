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

    private var rowOffsetsPx = FloatArray(0)
    private var rowHeightsPx = FloatArray(0)
    private var colOffsetsPx = FloatArray(0)
    private var colWidthsDp = FloatArray(0)
    private var isFullLayoutDirty = true
    private val dirtyColumns = HashSet<Int>()

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

    fun clearCellCaches() {
        cellRightAlignedCache.clear()
        for (fKey in formulaCellKeys) {
            cells[fKey]?.evaluated = null
        }
        dirtyColumns.clear()
    }

    fun getCellValue(r: Int, c: Int): String {
        val cell = cells[cellKey(r, c)] ?: return ""
        if (!cell.raw.startsWith("=")) {
            return cell.raw
        }
        cell.evaluated?.let { return it }

        val key = cellKey(r, c)
        if (!evaluatingCells.add(key)) {
            return "#CIRCULAR!"
        }
        return try {
            val eval = evaluateFormula(cell.raw)
            cell.evaluated = eval
            eval
        } finally {
            evaluatingCells.remove(key)
        }
    }

    fun getCellFormulaOrValue(r: Int, c: Int): String {
        return cells[cellKey(r, c)]?.raw ?: ""
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
            isFullLayoutDirty = true
        } else {
            dirtyColumns.add(c)
        }

        // Invalidate all dependent formula cells so any formulas referencing this cell recalculate
        for (fKey in formulaCellKeys) {
            cells[fKey]?.evaluated = null
            cellRightAlignedCache.remove(fKey)
            val fCol = (fKey and 0xFFFFFFFFL).toInt()
            if (fCol in 0 until maxCol) {
                dirtyColumns.add(fCol)
            }
        }
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

    fun loadSampleData(title: String, data: List<List<String>>) {
        cells.clear()
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
        maxRow = maxOf(25, data.size + 10)
        maxCol = maxOf(10, maxC + 3)
        frozenRows = 0
        frozenCols = 0
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
    }

    internal fun loadCSV(inputStream: InputStream) {
        cells.clear()
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
        isFullLayoutDirty = true
    }

    private fun loadXLSX(inputStream: InputStream): Boolean {
        cells.clear()
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

    private fun saveCSV(out: OutputStream) {
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

    private fun saveXLSX(out: OutputStream) {
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

        zip.finish()
        zip.flush()
    }

    private fun evaluateFormula(formula: String): String {
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
        return if (largeTouch) 44f else defaultRowHeightDp
    }

    fun getColWidthDp(c: Int): Float {
        if (c in colWidthsDp.indices && colWidthsDp[c] > 0f) {
            return colWidthsDp[c]
        }
        return defaultColWidthDp
    }

    fun updateLayoutIfNeeded(density: Float, largeTouch: Boolean = false) {
        val structureOrStyleDirty = isFullLayoutDirty ||
            density != currentDensity ||
            currentLargeTouch != largeTouch ||
            rowOffsetsPx.size != maxRow ||
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

            var currentY = 0f
            for (r in 0 until maxRow) {
                rowOffsetsPx[r] = currentY
                val h = getRowHeightDp(r, largeTouch) * density
                rowHeightsPx[r] = h
                currentY += h
            }
            totalHeightPx = currentY

            var currentX = 0f
            for (c in 0 until maxCol) {
                colOffsetsPx[c] = currentX
                currentX += getColWidthDp(c) * density
            }
            totalWidthPx = currentX

            isFullLayoutDirty = false
            dirtyColumns.clear()
            return
        }

        if (dirtyColumns.isEmpty()) {
            return
        }

        // Incremental column width update for dirty columns only
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
                colWidthsDp[c] = calculatedW
            }
        }
        dirtyColumns.clear()

        // Recompute colOffsetsPx starting from minDirty to maxCol
        var currentX = if (minDirty in colOffsetsPx.indices) colOffsetsPx[minDirty] else 0f
        for (c in minDirty until maxCol) {
            colOffsetsPx[c] = currentX
            currentX += getColWidthDp(c) * density
        }
        totalWidthPx = currentX
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

    companion object {
        private val NUMERIC_REGEX = Regex("^[+-]?\\d+([.,]\\d+)?$")
    }
}
