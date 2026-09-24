package com.speaksheet

import com.speaksheet.utils.SpreadsheetEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SpreadsheetEngineTest {

    @Test
    fun testFormulaDynamicInvalidation() {
        val engine = SpreadsheetEngine()
        engine.setCell(0, 0, "10") // A1
        engine.setCell(1, 0, "20") // A2
        engine.setCell(2, 0, "30") // A3
        engine.setCell(0, 1, "=SUM(A1:A3)") // B1

        assertEquals("60", engine.getCellValue(0, 1))

        // Change A2 from 20 to 50
        engine.setCell(1, 0, "50")

        // B1 must immediately reflect the new sum (10 + 50 + 30 = 90)
        assertEquals("90", engine.getCellValue(0, 1))

        // Change A1 to 100
        engine.setCell(0, 0, "100")
        assertEquals("180", engine.getCellValue(0, 1))
    }

    @Test
    fun testCircularFormulaSafeguard() {
        val engine = SpreadsheetEngine()
        engine.setCell(0, 0, "=B1") // A1 = B1
        engine.setCell(0, 1, "=A1") // B1 = A1

        // Should return #CIRCULAR! without throwing StackOverflow
        assertEquals("#CIRCULAR!", engine.getCellValue(0, 0))
    }

    @Test
    fun testNumericRegexAlignment() {
        val engine = SpreadsheetEngine()
        engine.setCell(0, 0, "123")
        engine.setCell(1, 0, "-45.67")
        engine.setCell(2, 0, "+89,01")
        engine.setCell(3, 0, "$500")
        engine.setCell(4, 0, "25%")
        engine.setCell(5, 0, "Hello World")

        assertTrue("Integer should be right-aligned", engine.isRightAligned(0, 0))
        assertTrue("Negative float should be right-aligned", engine.isRightAligned(1, 0))
        assertTrue("Plus float with comma should be right-aligned", engine.isRightAligned(2, 0))
        assertTrue("Currency should be right-aligned", engine.isRightAligned(3, 0))
        assertTrue("Percentage should be right-aligned", engine.isRightAligned(4, 0))
        assertFalse("Text should not be right-aligned", engine.isRightAligned(5, 0))
    }

    @Test
    fun testColumnDirtyLayoutRecompute() {
        val engine = SpreadsheetEngine()
        engine.updateLayoutIfNeeded(density = 1f, largeTouch = false)
        val initialCol0Width = engine.getColWidth(0)

        // Set a long string in column 0
        engine.setCell(0, 0, "Very long text that increases column width significantly")
        engine.updateLayoutIfNeeded(density = 1f, largeTouch = false)

        val updatedCol0Width = engine.getColWidth(0)
        assertTrue(updatedCol0Width > initialCol0Width)
    }

    @Test
    fun testCommaSeparatedCsvLoading() {
        val engine = SpreadsheetEngine()
        val csvData = "Name,Age,Score\nAlice,25,95\nBob,30,88".byteInputStream()
        engine.loadCSV(csvData)

        assertEquals("Name", engine.getCellValue(0, 0))
        assertEquals("Age", engine.getCellValue(0, 1))
        assertEquals("Score", engine.getCellValue(0, 2))
        assertEquals("Alice", engine.getCellValue(1, 0))
        assertEquals("25", engine.getCellValue(1, 1))
        assertEquals("95", engine.getCellValue(1, 2))
    }

    @Test
    fun testTabSeparatedTsvLoading() {
        val engine = SpreadsheetEngine()
        val tsvData = "Item\tPrice\tQty\nApple\t$1.50\t10\nBanana\t$0.80\t20".byteInputStream()
        engine.loadCSV(tsvData)

        assertEquals("Item", engine.getCellValue(0, 0))
        assertEquals("Price", engine.getCellValue(0, 1))
        assertEquals("Qty", engine.getCellValue(0, 2))
        assertEquals("Apple", engine.getCellValue(1, 0))
        assertEquals("$1.50", engine.getCellValue(1, 1))
        assertEquals("10", engine.getCellValue(1, 2))
    }
}
