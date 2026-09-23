package com.example.utils

import android.content.Context
import android.net.Uri
import com.opencsv.CSVReader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.apache.poi.ss.usermodel.*
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import java.io.InputStreamReader

class SpreadsheetEngine {

    var workbook: Workbook = XSSFWorkbook()
    var sheet: Sheet = workbook.createSheet("Sheet1")
    var evaluator: FormulaEvaluator = workbook.creationHelper.createFormulaEvaluator()
    var dataFormatter = DataFormatter()

    var frozenRows = 0
    var frozenCols = 0

    var maxRow = 50
    var maxCol = 15

    val defaultRowHeightDp = 32f // dp: spacious, comfortable touch & reading
    val defaultColWidthDp = 90f // dp

    private var rowOffsetsPx = FloatArray(0)
    private var rowHeightsPx = FloatArray(0)
    private var colOffsetsPx = FloatArray(0)
    private var colWidthsDp = FloatArray(0)
    private var isLayoutDirty = true
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

    private val cellValueCache = HashMap<Long, String>()
    private val cellRightAlignedCache = HashMap<Long, Boolean>()

    fun clearCellCaches() {
        cellValueCache.clear()
        cellRightAlignedCache.clear()
    }

    suspend fun loadFromUri(context: Context, uri: Uri) = withContext(Dispatchers.IO) {
        val type = context.contentResolver.getType(uri)
        val name = uri.path ?: ""
        
        try {
            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                if (name.endsWith(".csv", ignoreCase = true) || type == "text/comma-separated-values" || type == "text/csv") {
                    loadCSV(inputStream)
                } else {
                    workbook = WorkbookFactory.create(inputStream)
                    sheet = workbook.getSheetAt(0) ?: workbook.createSheet("Sheet1")
                    evaluator = workbook.creationHelper.createFormulaEvaluator()
                    
                    val detectedRows = sheet.lastRowNum + 1
                    var mCol = 0
                    for (row in sheet) {
                        if (row.lastCellNum > mCol) mCol = row.lastCellNum.toInt()
                    }
                    maxRow = maxOf(30, detectedRows + 10).coerceAtMost(300)
                    maxCol = maxOf(10, mCol + 3).coerceAtMost(30)
                    
                    val pane = sheet.paneInformation
                    if (pane != null && pane.isFreezePane) {
                        frozenCols = pane.verticalSplitLeftColumn.toInt()
                        frozenRows = pane.horizontalSplitTopRow.toInt()
                    }
                }
                clearCellCaches()
                isLayoutDirty = true
            }
        } catch (e: Exception) {
            // Handled gracefully without leaking stack trace
        }
    }

    private fun loadCSV(inputStream: java.io.InputStream) {
        workbook = XSSFWorkbook()
        sheet = workbook.createSheet("Sheet1")
        evaluator = workbook.creationHelper.createFormulaEvaluator()
        val reader = CSVReader(InputStreamReader(inputStream))
        var r = 0
        var maxC = 0
        reader.forEach { rowData ->
            val row = sheet.createRow(r)
            rowData.forEachIndexed { c, value ->
                val cell = row.createCell(c)
                val num = value.toDoubleOrNull()
                if (num != null) cell.setCellValue(num)
                else cell.setCellValue(value)
                if (c > maxC) maxC = c
            }
            r++
        }
        maxRow = maxOf(30, r + 10).coerceAtMost(300)
        maxCol = maxOf(10, maxC + 4).coerceAtMost(30)
        clearCellCaches()
        isLayoutDirty = true
    }

    fun loadSampleData(title: String, data: List<List<String>>) {
        workbook = XSSFWorkbook()
        sheet = workbook.createSheet(title.take(31))
        evaluator = workbook.creationHelper.createFormulaEvaluator()
        var maxC = 0
        data.forEachIndexed { r, rowValues ->
            val row = sheet.createRow(r)
            rowValues.forEachIndexed { c, value ->
                val cell = row.createCell(c)
                val num = value.toDoubleOrNull()
                if (num != null) {
                    cell.setCellValue(num)
                } else {
                    cell.setCellValue(value)
                }
                if (c > maxC) maxC = c
            }
        }
        maxRow = maxOf(25, data.size + 10)
        maxCol = maxOf(10, maxC + 3)
        clearCellCaches()
        isLayoutDirty = true
    }
    
    suspend fun saveToUri(context: Context, uri: Uri) = withContext(Dispatchers.IO) {
        try {
            context.contentResolver.openOutputStream(uri)?.use { outputStream ->
                workbook.write(outputStream)
            }
        } catch (e: Exception) {
            // Handled gracefully without leaking stack trace
        }
    }

    fun getCellValue(r: Int, c: Int): String {
        val key = (r.toLong() shl 32) or (c.toLong() and 0xFFFFFFFFL)
        val cached = cellValueCache[key]
        if (cached != null) return cached

        val row = sheet.getRow(r) ?: run {
            cellValueCache[key] = ""
            return ""
        }
        val cell = row.getCell(c) ?: run {
            cellValueCache[key] = ""
            return ""
        }
        val formatted = try {
            dataFormatter.formatCellValue(cell, evaluator)
        } catch (e: Exception) {
            try {
                cell.stringCellValue
            } catch (e2: Exception) {
                ""
            }
        }
        cellValueCache[key] = formatted
        return formatted
    }

    fun getCellFormulaOrValue(r: Int, c: Int): String {
        val row = sheet.getRow(r) ?: return ""
        val cell = row.getCell(c) ?: return ""
        if (cell.cellType == CellType.FORMULA) {
            return "=" + cell.cellFormula
        }
        return getCellValue(r, c)
    }
    
    fun isRightAligned(r: Int, c: Int): Boolean {
        val key = (r.toLong() shl 32) or (c.toLong() and 0xFFFFFFFFL)
        val cached = cellRightAlignedCache[key]
        if (cached != null) return cached

        val row = sheet.getRow(r) ?: run {
            cellRightAlignedCache[key] = false
            return false
        }
        val cell = row.getCell(c) ?: run {
            cellRightAlignedCache[key] = false
            return false
        }
        val cellType = cell.cellType
        if (cellType == CellType.NUMERIC) {
            cellRightAlignedCache[key] = true
            return true
        }
        if (cellType == CellType.FORMULA) {
            try {
                val cv = evaluator.evaluate(cell)
                if (cv != null && cv.cellType == CellType.NUMERIC) {
                    cellRightAlignedCache[key] = true
                    return true
                }
            } catch (e: Exception) {}
        }
        val text = getCellValue(r, c).trim()
        val result = text.isNotEmpty() && (text.toDoubleOrNull() != null || text.startsWith("$") || text.endsWith("%"))
        cellRightAlignedCache[key] = result
        return result
    }

    fun setCell(r: Int, c: Int, value: String) {
        val row = sheet.getRow(r) ?: sheet.createRow(r)
        val cell = row.getCell(c) ?: row.createCell(c)

        if (value.startsWith("=")) {
            try {
                cell.cellFormula = value.substring(1)
            } catch (e: Exception) {
                cell.setCellValue(value)
            }
        } else {
            val num = value.toDoubleOrNull()
            if (num != null) cell.setCellValue(num)
            else cell.setCellValue(value)
        }
        
        try {
            evaluator.clearAllCachedResultValues()
            evaluator.evaluateFormulaCell(cell)
        } catch (e: Exception) {}
        
        clearCellCaches()
        isLayoutDirty = true
    }

    fun getRowHeightDp(r: Int, largeTouch: Boolean = currentLargeTouch): Float {
        val base = if (largeTouch) 44f else defaultRowHeightDp
        val row = sheet.getRow(r)
        return if (row != null && row.heightInPoints != sheet.defaultRowHeightInPoints && row.heightInPoints > 15f) {
            (row.heightInPoints * 1.33f).coerceIn(base, 70f)
        } else {
            base
        }
    }

    fun getColWidthDp(c: Int): Float {
        if (c in colWidthsDp.indices && colWidthsDp[c] > 0f) {
            return colWidthsDp[c]
        }
        return defaultColWidthDp
    }

    fun updateLayoutIfNeeded(density: Float, largeTouch: Boolean = false) {
        if (!isLayoutDirty && density == currentDensity && currentLargeTouch == largeTouch &&
            rowOffsetsPx.size == maxRow && colOffsetsPx.size == maxCol) {
            return
        }
        
        currentDensity = density
        currentZoom = 1.0f
        currentLargeTouch = largeTouch
        rowOffsetsPx = FloatArray(maxRow)
        rowHeightsPx = FloatArray(maxRow)
        colOffsetsPx = FloatArray(maxCol)
        colWidthsDp = FloatArray(maxCol)

        // Calculate intelligent column widths so text is never truncated
        for (c in 0 until maxCol) {
            var maxLen = 4
            // Check header and first 25 rows
            val checkLimit = minOf(maxRow, 25)
            for (r in 0 until checkLimit) {
                val len = getCellValue(r, c).length
                if (len > maxLen) maxLen = len
            }
            // Auto width: 9dp per character + 24dp cell padding, clamped between 85dp and 180dp
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
        
        isLayoutDirty = false
    }

    // Overload for compatibility
    fun updateLayoutIfNeeded(density: Float, zoom: Float, largeTouch: Boolean) {
        updateLayoutIfNeeded(density, largeTouch)
    }

    fun getRowOffsetPx(r: Int): Float {
        return if (r in rowOffsetsPx.indices) {
            rowOffsetsPx[r]
        } else {
            r * defaultRowHeightDp * currentDensity
        }
    }

    fun getColOffsetPx(c: Int): Float {
        return if (c in colOffsetsPx.indices) {
            colOffsetsPx[c]
        } else {
            c * defaultColWidthDp * currentDensity
        }
    }

    fun getRowHeightPx(r: Int): Float {
        return if (r in rowHeightsPx.indices) {
            rowHeightsPx[r]
        } else {
            getRowHeightDp(r) * currentDensity
        }
    }
    fun getColWidthPx(c: Int): Float = getColWidthDp(c) * currentDensity

    // Backwards-compatible aliases for Px
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
}
