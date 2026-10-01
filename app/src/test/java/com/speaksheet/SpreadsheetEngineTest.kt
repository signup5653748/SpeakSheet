package com.speaksheet

import com.speaksheet.utils.SampleSheets
import com.speaksheet.utils.SpreadsheetEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

@RunWith(RobolectricTestRunner::class)
class SpreadsheetEngineTest {

    private lateinit var engine: SpreadsheetEngine

    @Before
    fun setUp() {
        engine = SpreadsheetEngine()
        engine.newSpreadsheet(rows = 20, cols = 10)
    }

    @Test
    fun testMultiSheetSupport() {
        assertEquals(1, engine.sheets.size)
        assertEquals("Sheet1", engine.getActiveSheetName())

        val sheet2Idx = engine.addSheet("Sales")
        assertEquals(2, engine.sheets.size)
        assertEquals("Sales", engine.getActiveSheetName())

        engine.setCell(0, 0, "Sales Data")
        assertEquals("Sales Data", engine.getCellValue(0, 0))

        engine.switchSheet(0)
        assertEquals("Sheet1", engine.getActiveSheetName())
        assertEquals("", engine.getCellValue(0, 0))

        engine.switchSheet(sheet2Idx)
        assertEquals("Sales Data", engine.getCellValue(0, 0))

        assertTrue(engine.renameSheet(sheet2Idx, "Q1 Sales"))
        assertEquals("Q1 Sales", engine.getActiveSheetName())

        assertTrue(engine.deleteSheet(sheet2Idx))
        assertEquals(1, engine.sheets.size)
        assertEquals("Sheet1", engine.getActiveSheetName())
    }

    @Test
    fun testNumberFormatting() {
        engine.setCell(0, 0, "1234.56")
        engine.setCellNumberFormat(0, 0, "Currency")
        assertEquals("$1,234.56", engine.getCellValue(0, 0))

        engine.setCell(1, 0, "0.25")
        engine.setCellNumberFormat(1, 0, "Percent")
        assertEquals("25.0%", engine.getCellValue(1, 0))

        engine.setCell(2, 0, "5000")
        engine.setCellNumberFormat(2, 0, "Number")
        assertEquals("5,000", engine.getCellValue(2, 0))
    }

    @Test
    fun testFormulas() {
        engine.setCell(0, 0, "10")
        engine.setCell(1, 0, "20")
        engine.setCell(2, 0, "30")
        engine.setCell(3, 0, "=SUM(A1:A3)")

        assertEquals("60", engine.getCellValue(3, 0))
    }

    @Test
    fun testColumnFilter() {
        engine.setHeaderRow(0)
        engine.setCell(0, 0, "Category")
        engine.setCell(1, 0, "Electronics")
        engine.setCell(2, 0, "Furniture")
        engine.setCell(3, 0, "Electronics")
        engine.setCell(4, 0, "Clothing")

        val distinct = engine.getDistinctValuesForColumn(0)
        assertTrue(distinct.contains("Electronics"))
        assertTrue(distinct.contains("Furniture"))
        assertTrue(distinct.contains("Clothing"))

        val (visible, total) = engine.applyColumnFilter(0, setOf("Electronics"))
        assertTrue(visible >= 2)
        assertFalse(engine.isRowHidden(1))
        assertTrue(engine.isRowHidden(2))
        assertFalse(engine.isRowHidden(3))
        assertTrue(engine.isRowHidden(4))

        engine.clearColumnFilter()
        assertFalse(engine.isRowHidden(2))
        assertFalse(engine.isRowHidden(4))
    }

    @Test
    fun testBannerAndMergedRange() {
        engine.insertRow(0)
        engine.mergeRange(0, 0, 0, engine.maxCol - 1)
        engine.setCell(0, 0, "Company Financial Report 2026")

        val merged = engine.getMergedRange(0, 0)
        assertTrue(merged != null)
        assertEquals(0, merged?.startRow)
        assertEquals(0, merged?.startCol)
        assertEquals(0, merged?.endRow)
        assertEquals(engine.maxCol - 1, merged?.endCol)

        assertTrue(engine.isBannerRow(0))
        assertTrue(engine.isFullWidthRow(0))
        assertEquals("Company Financial Report 2026", engine.getCellValue(0, 0))

        engine.unmergeRow(0)
        assertFalse(engine.isBannerRow(0))
    }

    @Test
    fun testUsedColCountAndBannerWidth() {
        // Initially empty spreadsheet of 20 rows x 10 cols, auto-extended to 50 cols
        engine.newSpreadsheet(rows = 20, cols = 50)
        
        // Put data in columns A (0), B (1), C (2) only
        engine.setCell(1, 0, "Item")
        engine.setCell(1, 1, "Price")
        engine.setCell(1, 2, "Qty")
        
        // Set a banner in row 0
        engine.mergeRange(0, 0, 0, engine.maxCol - 1)
        engine.setCell(0, 0, "Inventory Summary")
        
        // Used column count should be 3 (columns 0, 1, 2), not 50
        assertEquals(3, engine.getUsedColCount())
        
        val expectedWidth = engine.getColOffsetPx(2) + engine.getColWidthPx(2)
        assertEquals(expectedWidth, engine.getUsedWidthPx(), 0.01f)
    }

    @Test
    fun testBannerAndHeaderColumnNameResolution() {
        // Row 0: Banner / Title row
        engine.newSpreadsheet(rows = 20, cols = 10)
        engine.mergeRange(0, 0, 0, engine.maxCol - 1)
        engine.setCell(0, 0, "Q3 Regional Performance Report")

        // Row 1: Subtitle banner
        engine.mergeRange(1, 0, 1, engine.maxCol - 1)
        engine.setCell(1, 0, "North America Division")

        // Row 2: Real marked header row
        engine.setHeaderRow(2)
        engine.setCell(2, 0, "Region")
        engine.setCell(2, 1, "Sales (\$M)")
        engine.setCell(2, 2, "Growth (%)")

        // Row 3: Data row
        engine.setCell(3, 0, "Northeast")
        engine.setCell(3, 1, "45.2")
        engine.setCell(3, 2, "12.4%")

        // Confirm getColumnHeaderName for row 3 correctly reads the real header row 2
        assertEquals("Region", engine.getColumnHeaderName(0, forRow = 3))
        assertEquals("Sales (\$M)", engine.getColumnHeaderName(1, forRow = 3))
        assertEquals("Growth (%)", engine.getColumnHeaderName(2, forRow = 3))

        // Confirm getColumnHeaderName for row 0 / 1 (banner rows) does NOT return banner text, but "Column A"
        assertEquals("Column A", engine.getColumnHeaderName(0, forRow = 0))
        assertEquals("Column A", engine.getColumnHeaderName(0, forRow = 1))
    }

    @Test
    fun testSplitHeaderColors() {
        engine.setHeaderRow(1)
        engine.setHeaderBgColor(0xFF1976D2.toInt())
        engine.setHeaderTextColor(0xFFFFFFFF.toInt())

        assertEquals(0xFF1976D2.toInt(), engine.headerBgColor)
        assertEquals(0xFFFFFFFF.toInt(), engine.headerTextColor)

        // Test Undo/Redo preserves both fields
        engine.pushUndo("Edit")
        engine.setHeaderBgColor(0xFF00897B.toInt())
        assertEquals(0xFF00897B.toInt(), engine.headerBgColor)

        engine.undo()
        assertEquals(0xFF1976D2.toInt(), engine.headerBgColor)
        assertEquals(0xFFFFFFFF.toInt(), engine.headerTextColor)
    }

    @Test
    fun testOpenXmlExportAndImport() {
        SampleSheets.createSectionedReport(engine)
        val out = ByteArrayOutputStream()
        engine.saveXLSX(out)

        val bytes = out.toByteArray()
        assertTrue(bytes.isNotEmpty())

        val newEngine = SpreadsheetEngine()
        val inStream = ByteArrayInputStream(bytes)
        val loaded = newEngine.loadXLSX(inStream)
        assertTrue(loaded)
        assertEquals("Annual Performance & Departmental Operations Report", newEngine.getCellValue(0, 0))
    }

    @Test
    fun testCellColorSingleStepUndo() {
        val initialRed = 0xFFFF0000.toInt()
        val yellow = 0xFFFFFF00.toInt()

        // Set initial color
        engine.setCellColor(0, 0, initialRed)
        assertEquals(initialRed, engine.getCellColor(0, 0))

        // Simulate picking yellow: push exactly one undo entry for the final confirmed color
        engine.pushUndo("Background color A1")
        engine.setCellColor(0, 0, yellow)
        assertEquals(yellow, engine.getCellColor(0, 0))

        // Verify single Undo returns directly to initialRed in one step
        assertTrue(engine.canUndo)
        engine.undo()
        assertEquals(initialRed, engine.getCellColor(0, 0))
        assertFalse(engine.canUndo)
    }

    @Test
    fun testRowAndColumnColorSingleStepUndo() {
        val originalColColor = 0xFF00FF00.toInt()
        val newColColor = 0xFF0000FF.toInt()

        engine.setColumnColor(1, originalColColor)
        assertEquals(originalColColor, engine.getColumnColor(1))

        // Confirm color picker commit pushes exactly one undo
        engine.pushUndo("Column color B")
        engine.setColumnColor(1, newColColor)
        assertEquals(newColColor, engine.getColumnColor(1))

        // 1 undo restores original color
        engine.undo()
        assertEquals(originalColColor, engine.getColumnColor(1))

        // Row color test
        val originalRowColor = 0xFF123456.toInt()
        val newRowColor = 0xFF654321.toInt()
        engine.setRowColor(2, originalRowColor)
        assertEquals(originalRowColor, engine.getRowColor(2))

        engine.pushUndo("Row color 3")
        engine.setRowColor(2, newRowColor)
        assertEquals(newRowColor, engine.getRowColor(2))

        engine.undo()
        assertEquals(originalRowColor, engine.getRowColor(2))
    }

    @Test
    fun testXLSXExportStylingAndWrap() {
        engine.setCell(0, 0, "Styled Header")
        engine.setHeaderRow(0)
        engine.setHeaderBgColor(0xFF2E7D32.toInt())
        engine.setHeaderTextColor(0xFFFFFFFF.toInt())

        engine.setCell(1, 0, "Wrapped Text In Cell")
        engine.toggleColumnWrap(0)
        engine.setCellColor(1, 0, 0xFFFFF59D.toInt())
        engine.setCellBold(1, 0, true)

        val out = ByteArrayOutputStream()
        engine.saveXLSX(out)
        val bytes = out.toByteArray()
        assertTrue(bytes.isNotEmpty())

        // Verify ZIP contains styles.xml with cellXfs, applyAlignment, and fills
        val zipIn = java.util.zip.ZipInputStream(ByteArrayInputStream(bytes))
        var foundStyles = false
        var foundSheet = false
        var entry = zipIn.nextEntry
        while (entry != null) {
            if (entry.name == "xl/styles.xml") {
                foundStyles = true
                val stylesContent = zipIn.bufferedReader().readText()
                assertTrue(stylesContent.contains("applyAlignment=\"1\""))
                assertTrue(stylesContent.contains("wrapText=\"1\""))
                assertTrue(stylesContent.contains("applyFill=\"1\""))
                assertTrue(stylesContent.contains("patternType=\"solid\""))
            } else if (entry.name == "xl/worksheets/sheet1.xml") {
                foundSheet = true
                val sheetContent = zipIn.bufferedReader().readText()
                assertTrue(sheetContent.contains("<cols>"))
                assertTrue(sheetContent.contains("<col min=\"1\" max=\"1\""))
                assertTrue(sheetContent.contains("customWidth=\"1\""))
                assertTrue(sheetContent.contains("<row r=\"1\""))
                assertTrue(sheetContent.contains("customHeight=\"1\""))
            }
            entry = zipIn.nextEntry
        }
        assertTrue(foundStyles)
        assertTrue(foundSheet)
    }
}
