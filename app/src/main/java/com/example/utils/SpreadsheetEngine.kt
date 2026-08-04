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

    var maxRow = 1000
    var maxCol = 26

    val defaultRowHeight = 24f // dp
    val defaultColWidth = 80f // dp

    private var rowOffsets = FloatArray(0)
    private var colOffsets = FloatArray(0)
    private var isLayoutDirty = true
    private var currentDensity = 1f

    suspend fun loadFromUri(context: Context, uri: Uri) = withContext(Dispatchers.IO) {
        val type = context.contentResolver.getType(uri)
        val name = uri.path ?: ""
        
        try {
            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                if (name.endsWith(".csv") || type == "text/comma-separated-values" || type == "text/csv") {
                    loadCSV(inputStream)
                } else {
                    workbook = WorkbookFactory.create(inputStream)
                    sheet = workbook.getSheetAt(0) ?: workbook.createSheet("Sheet1")
                    evaluator = workbook.creationHelper.createFormulaEvaluator()
                    
                    maxRow = maxOf(1000, sheet.lastRowNum + 10)
                    var mCol = 26
                    for (row in sheet) {
                        if (row.lastCellNum > mCol) mCol = row.lastCellNum.toInt()
                    }
                    maxCol = maxOf(26, mCol + 5)
                    
                    val pane = sheet.paneInformation
                    if (pane != null && pane.isFreezePane) {
                        frozenCols = pane.verticalSplitLeftColumn.toInt()
                        frozenRows = pane.horizontalSplitTopRow.toInt()
                    }
                }
                isLayoutDirty = true
            }
        } catch (e: Exception) {
            e.printStackTrace()
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
        maxRow = maxOf(1000, r + 10)
        maxCol = maxOf(26, maxC + 5)
        isLayoutDirty = true
    }
    
    suspend fun saveToUri(context: Context, uri: Uri) = withContext(Dispatchers.IO) {
        try {
            context.contentResolver.openOutputStream(uri)?.use { outputStream ->
                workbook.write(outputStream)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun getCellValue(r: Int, c: Int): String {
        val row = sheet.getRow(r) ?: return ""
        val cell = row.getCell(c) ?: return ""
        return try {
            dataFormatter.formatCellValue(cell, evaluator)
        } catch (e: Exception) {
            "#ERROR!"
        }
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
        val row = sheet.getRow(r) ?: return false
        val cell = row.getCell(c) ?: return false
        
        // Match Excel: Numbers/Dates are right aligned, text is left aligned
        val cellType = cell.cellType
        if (cellType == CellType.NUMERIC) return true
        if (cellType == CellType.FORMULA) {
            try {
                val cv = evaluator.evaluate(cell)
                if (cv != null && cv.cellType == CellType.NUMERIC) return true
            } catch(e: Exception) {}
        }
        return false
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
            evaluator.evaluateFormulaCell(cell)
        } catch (e: Exception) {}
    }

    fun getRowHeight(r: Int): Float {
        val row = sheet.getRow(r)
        return if (row != null && row.heightInPoints != sheet.defaultRowHeightInPoints) {
            row.heightInPoints * 1.33f // Approx pt to dp
        } else {
            defaultRowHeight
        }
    }

    fun getColWidth(c: Int): Float {
        val w = sheet.getColumnWidth(c)
        return if (w != sheet.defaultColumnWidth * 256) {
            (w / 256f) * 7f // Approx char width to dp
        } else {
            defaultColWidth
        }
    }

    fun updateLayoutIfNeeded(density: Float) {
        if (!isLayoutDirty && density == currentDensity && rowOffsets.size == maxRow) return
        
        currentDensity = density
        rowOffsets = FloatArray(maxRow)
        colOffsets = FloatArray(maxCol)
        
        var currentY = 0f
        for (r in 0 until maxRow) {
            rowOffsets[r] = currentY
            currentY += getRowHeight(r) * density
        }
        
        var currentX = 0f
        for (c in 0 until maxCol) {
            colOffsets[c] = currentX
            currentX += getColWidth(c) * density
        }
        isLayoutDirty = false
    }

    fun getRowOffset(r: Int): Float = if (r < rowOffsets.size) rowOffsets[r] else 0f
    fun getColOffset(c: Int): Float = if (c < colOffsets.size) colOffsets[c] else 0f

    fun getRowAt(y: Float): Int {
        var low = 0
        var high = rowOffsets.size - 1
        while (low <= high) {
            val mid = (low + high) / 2
            val start = rowOffsets[mid]
            val end = start + getRowHeight(mid) * currentDensity
            if (y in start..end) return mid
            if (y < start) high = mid - 1
            else low = mid + 1
        }
        return rowOffsets.size - 1
    }

    fun getColAt(x: Float): Int {
        var low = 0
        var high = colOffsets.size - 1
        while (low <= high) {
            val mid = (low + high) / 2
            val start = colOffsets[mid]
            val end = start + getColWidth(mid) * currentDensity
            if (x in start..end) return mid
            if (x < start) high = mid - 1
            else low = mid + 1
        }
        return colOffsets.size - 1
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
}
