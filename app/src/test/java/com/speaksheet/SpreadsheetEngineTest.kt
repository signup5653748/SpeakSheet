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
}
