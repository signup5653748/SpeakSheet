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

    @Test
    fun testNewSpreadsheetClearsAllCells() {
        val engine = SpreadsheetEngine()
        engine.setCell(0, 0, "Header1")
        engine.setCell(0, 1, "Header2")
        engine.setCell(1, 0, "100")
        engine.setCell(1, 1, "=A2")
        assertEquals("100", engine.getCellValue(1, 1))

        // Create new spreadsheet
        engine.newSpreadsheet(50, 15)

        assertEquals("", engine.getCellValue(0, 0))
        assertEquals("", engine.getCellValue(0, 1))
        assertEquals("", engine.getCellValue(1, 0))
        assertEquals("", engine.getCellValue(1, 1))
        assertEquals(50, engine.maxRow)
        assertEquals(15, engine.maxCol)
    }

    @Test
    fun testSortFormulaSingleColumnAscendingAndDescending() {
        val engine = SpreadsheetEngine()
        engine.setCell(0, 0, "Cherry") // A1
        engine.setCell(1, 0, "Apple")  // A2
        engine.setCell(2, 0, "Banana") // A3

        // B1 = =SORT(A1:A3)
        engine.setCell(0, 1, "=SORT(A1:A3)")

        // B1..B3 should spill sorted ascending: Apple, Banana, Cherry
        assertEquals("Apple", engine.getCellValue(0, 1))
        assertEquals("Banana", engine.getCellValue(1, 1))
        assertEquals("Cherry", engine.getCellValue(2, 1))

        // Descending sort in C1: =SORT(A1:A3, "DESC")
        engine.setCell(0, 2, "=SORT(A1:A3, \"DESC\")")
        assertEquals("Cherry", engine.getCellValue(0, 2))
        assertEquals("Banana", engine.getCellValue(1, 2))
        assertEquals("Apple", engine.getCellValue(2, 2))
    }

    @Test
    fun testSortFormulaMultiColumn() {
        val engine = SpreadsheetEngine()
        // A1:B3 -> Product and Price
        engine.setCell(0, 0, "Orange")
        engine.setCell(0, 1, "30")

        engine.setCell(1, 0, "Apple")
        engine.setCell(1, 1, "10")

        engine.setCell(2, 0, "Banana")
        engine.setCell(2, 1, "20")

        // C1 = =SORT(A1:B3, 2) -> sort by 2nd column (Price) ascending
        engine.setCell(0, 2, "=SORT(A1:B3, 2)")

        // Price 10 (Apple), 20 (Banana), 30 (Orange)
        assertEquals("Apple", engine.getCellValue(0, 2))  // C1
        assertEquals("10", engine.getCellValue(0, 3))     // D1
        assertEquals("Banana", engine.getCellValue(1, 2)) // C2
        assertEquals("20", engine.getCellValue(1, 3))     // D2
        assertEquals("Orange", engine.getCellValue(2, 2)) // C3
        assertEquals("30", engine.getCellValue(2, 3))     // D3

        // C5 = =SORT(A1:B3, 2, "DESC") -> sort by 2nd column descending
        engine.setCell(4, 2, "=SORT(A1:B3, 2, \"DESC\")")
        assertEquals("Orange", engine.getCellValue(4, 2))
        assertEquals("30", engine.getCellValue(4, 3))
        assertEquals("Banana", engine.getCellValue(5, 2))
        assertEquals("20", engine.getCellValue(5, 3))
        assertEquals("Apple", engine.getCellValue(6, 2))
        assertEquals("10", engine.getCellValue(6, 3))
    }

    @Test
    fun testSortFormulaSpillCollision() {
        val engine = SpreadsheetEngine()
        engine.setCell(0, 0, "Charlie")
        engine.setCell(1, 0, "Alice")
        engine.setCell(2, 0, "Bob")

        // Put a blocking value in B2
        engine.setCell(1, 1, "Occupied")

        // B1 = =SORT(A1:A3) tries to spill into B1..B3
        engine.setCell(0, 1, "=SORT(A1:A3)")

        // Must report #SPILL! because B2 is occupied
        assertEquals("#SPILL!", engine.getCellValue(0, 1))

        // Clear B2, B1 should now successfully spill
        engine.setCell(1, 1, "")
        assertEquals("Alice", engine.getCellValue(0, 1))
        assertEquals("Bob", engine.getCellValue(1, 1))
        assertEquals("Charlie", engine.getCellValue(2, 1))
    }

    @Test
    fun testWrapTextToggleAndAutoRowHeight() {
        val engine = SpreadsheetEngine()
        assertFalse(engine.isWrapEnabled(0))

        // Toggle wrap on column 0
        val enabled = engine.toggleColumnWrap(0)
        assertTrue(enabled)
        assertTrue(engine.isWrapEnabled(0))

        // Toggle again to disable
        val disabled = engine.toggleColumnWrap(0)
        assertFalse(disabled)
        assertFalse(engine.isWrapEnabled(0))

        // Enable wrap on column 1
        engine.setColumnWrap(1, true)
        assertTrue(engine.isWrapEnabled(1))

        // Set short text in row 40, long multiline text in row 41
        engine.setCell(40, 1, "Short")
        engine.setCell(41, 1, "Line 1\nLine 2\nLine 3\nLine 4")

        // Custom measurer simulation: 24px per line for multiline text
        engine.updateLayoutIfNeeded(density = 1f, largeTouch = false) { r, c, text, _ ->
            val lines = text.split("\n").size
            if (lines <= 1) 20f else lines * 24f + 10f
        }

        val row0Height = engine.getRowHeightPx(40)
        val row1Height = engine.getRowHeightPx(41)

        // Row 40 height should stay at default (32f)
        assertEquals(32f, row0Height, 0.01f)

        // Row 41 height should expand to fit the 4 lines (4 * 24 + 10 = 106f)
        assertTrue("Row 41 height ($row1Height) should be taller than row 40 ($row0Height)", row1Height > row0Height)
        assertEquals(106f, row1Height, 0.01f)

        // getRowHeightDp should also return the expanded height
        assertEquals(106f, engine.getRowHeightDp(41), 0.01f)

        // Row 42 without content in wrapped column stays default
        assertEquals(32f, engine.getRowHeightPx(42), 0.01f)
    }

    @Test
    fun testCellAndColumnColorsAndSerialization() {
        val engine = SpreadsheetEngine()

        // Initially no colors
        assertEquals(null, engine.getCellColor(0, 0))
        assertEquals(null, engine.getCellTextColor(0, 0))
        assertEquals(null, engine.getColumnColor(1))
        assertEquals(null, engine.getColumnTextColor(1))
        assertEquals(null, engine.getRowColor(2))
        assertEquals(null, engine.getRowTextColor(2))

        // Set cell bg and text color
        val softRed = 0xFFFFCDD2.toInt()
        val darkCharcoal = 0xFF212121.toInt()
        engine.setCellColor(2, 3, softRed)
        engine.setCellTextColor(2, 3, darkCharcoal)
        assertEquals(softRed, engine.getCellColor(2, 3))
        assertEquals(darkCharcoal, engine.getCellTextColor(2, 3))

        // Set column bg and text color
        val skyBlue = 0xFFBBDEFB.toInt()
        val navy = 0xFF0D47A1.toInt()
        engine.setColumnColor(3, skyBlue)
        engine.setColumnTextColor(3, navy)
        assertEquals(skyBlue, engine.getColumnColor(3))
        assertEquals(navy, engine.getColumnTextColor(3))

        // Set row bg and text color (custom hex)
        val customRowBg = 0xFFE0F2F1.toInt()
        val customRowText = 0xFF004D40.toInt()
        engine.setRowColor(5, customRowBg)
        engine.setRowTextColor(5, customRowText)
        assertEquals(customRowBg, engine.getRowColor(5))
        assertEquals(customRowText, engine.getRowTextColor(5))

        // Serialize colors
        val serialized = engine.serializeColors()
        assertTrue(serialized.contains("[CELL_COLORS]"))
        assertTrue(serialized.contains("2,3,$softRed"))
        assertTrue(serialized.contains("[CELL_TEXT_COLORS]"))
        assertTrue(serialized.contains("2,3,$darkCharcoal"))
        assertTrue(serialized.contains("[COLUMN_COLORS]"))
        assertTrue(serialized.contains("3,$skyBlue"))
        assertTrue(serialized.contains("[COLUMN_TEXT_COLORS]"))
        assertTrue(serialized.contains("3,$navy"))
        assertTrue(serialized.contains("[ROW_COLORS]"))
        assertTrue(serialized.contains("5,$customRowBg"))
        assertTrue(serialized.contains("[ROW_TEXT_COLORS]"))
        assertTrue(serialized.contains("5,$customRowText"))

        // Create new engine and deserialize
        val newEngine = SpreadsheetEngine()
        newEngine.deserializeColors(serialized)
        assertEquals(softRed, newEngine.getCellColor(2, 3))
        assertEquals(darkCharcoal, newEngine.getCellTextColor(2, 3))
        assertEquals(skyBlue, newEngine.getColumnColor(3))
        assertEquals(navy, newEngine.getColumnTextColor(3))
        assertEquals(customRowBg, newEngine.getRowColor(5))
        assertEquals(customRowText, newEngine.getRowTextColor(5))

        // Clear cell colors
        newEngine.setCellColor(2, 3, null)
        newEngine.setCellTextColor(2, 3, null)
        assertEquals(null, newEngine.getCellColor(2, 3))
        assertEquals(null, newEngine.getCellTextColor(2, 3))

        // Clear column colors
        newEngine.setColumnColor(3, null)
        newEngine.setColumnTextColor(3, null)
        assertEquals(null, newEngine.getColumnColor(3))
        assertEquals(null, newEngine.getColumnTextColor(3))

        // Clear row colors
        newEngine.setRowColor(5, null)
        newEngine.setRowTextColor(5, null)
        assertEquals(null, newEngine.getRowColor(5))
        assertEquals(null, newEngine.getRowTextColor(5))
    }
}
