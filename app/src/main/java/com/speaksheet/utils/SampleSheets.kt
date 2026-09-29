package com.speaksheet.utils

object SampleSheets {

    fun createSectionedReport(engine: SpreadsheetEngine) {
        engine.newSpreadsheet(rows = 45, cols = 10)

        // 1. Title Banner (Row 0)
        engine.setCell(0, 0, "Annual Performance & Departmental Operations Report")
        engine.mergeRange(0, 0, 0, 9)
        engine.setCellBold(0, 0, true)
        engine.setCellColor(0, 0, 0xFF1B5E20L.toInt()) // Dark green
        engine.setCellTextColor(0, 0, 0xFFFFFFFFL.toInt())

        // 2. Description Banner (Row 1)
        engine.setCell(1, 0, "Comprehensive quarterly breakdown across Executive, Operations, Sales, and R&D divisions with subtotal calculations.")
        engine.mergeRange(1, 0, 1, 9)
        engine.setCellItalic(1, 0, true)
        engine.setCellColor(1, 0, 0xFFE8F5E9L.toInt()) // Very light green
        engine.setCellTextColor(1, 0, 0xFF2E7D32L.toInt())

        val headers = listOf("ID", "Member", "Department", "Role", "Quarter 1", "Quarter 2", "Quarter 3", "Quarter 4", "Total", "Status")

        fun setupHeaderRow(row: Int, bgColor: Int) {
            headers.forEachIndexed { col, title ->
                engine.setCell(row, col, title)
                engine.setCellBold(row, col, true)
                engine.setCellColor(row, col, bgColor)
                engine.setCellAlignment(row, col, if (col in 4..8) 2 else 0)
            }
            engine.setHeaderRow(row)
        }

        fun applyCurrencyFormatting(row: Int) {
            for (c in 4..8) {
                engine.setCellNumberFormat(row, c, "Currency")
                engine.setCellAlignment(row, c, 2)
            }
        }

        // Section 1: Executive & Leadership
        engine.setCell(3, 0, "Section 1: Executive & Leadership")
        engine.mergeRange(3, 0, 3, 9)
        engine.setCellBold(3, 0, true)
        engine.setCellColor(3, 0, 0xFF2E7D32L.toInt())
        engine.setCellTextColor(3, 0, 0xFFFFFFFFL.toInt())

        setupHeaderRow(4, 0xFFC8E6C9L.toInt())

        val execData = listOf(
            listOf("E101", "Sarah Connor", "Executive", "CEO", "45000", "48000", "52000", "55000", "=SUM(E6:H6)", "Active"),
            listOf("E102", "Michael Scott", "Executive", "Regional Mgr", "28000", "29500", "31000", "33000", "=SUM(E7:H7)", "Active"),
            listOf("E103", "Donna Paulsen", "Executive", "COO", "38000", "39000", "41000", "43000", "=SUM(E8:H8)", "Active"),
            listOf("E104", "Harvey Specter", "Executive", "Managing Partner", "42000", "44000", "46000", "49000", "=SUM(E9:H9)", "Active"),
            listOf("E105", "Louis Litt", "Executive", "Partner", "35000", "36500", "38000", "40000", "=SUM(E10:H10)", "Active")
        )
        execData.forEachIndexed { i, rowData ->
            val r = 5 + i
            rowData.forEachIndexed { c, value ->
                engine.setCell(r, c, value)
            }
            applyCurrencyFormatting(r)
        }
        // Subtotal row 10
        engine.setCell(10, 0, "Section 1 Total")
        engine.setCellBold(10, 0, true)
        engine.setCell(10, 4, "=SUM(E6:E10)")
        engine.setCell(10, 5, "=SUM(F6:F10)")
        engine.setCell(10, 6, "=SUM(G6:G10)")
        engine.setCell(10, 7, "=SUM(H6:H10)")
        engine.setCell(10, 8, "=SUM(I6:I10)")
        applyCurrencyFormatting(10)
        for (c in 0..9) {
            engine.setCellBold(10, c, true)
            engine.setCellColor(10, c, 0xFFE8F5E9L.toInt())
        }

        // Section 2: Engineering & Technology
        engine.setCell(12, 0, "Section 2: Engineering & Technology")
        engine.mergeRange(12, 0, 12, 9)
        engine.setCellBold(12, 0, true)
        engine.setCellColor(12, 0, 0xFF1565C0L.toInt())
        engine.setCellTextColor(12, 0, 0xFFFFFFFFL.toInt())

        setupHeaderRow(13, 0xFFBBDEFBL.toInt())

        val engData = listOf(
            listOf("T201", "Ada Lovelace", "Engineering", "Principal Arch", "36000", "38000", "40000", "42000", "=SUM(E15:H15)", "Active"),
            listOf("T202", "Alan Turing", "Engineering", "Lead Scientist", "34000", "36000", "37500", "39000", "=SUM(E16:H16)", "Active"),
            listOf("T203", "Grace Hopper", "Engineering", "Systems Dir", "35000", "37000", "39000", "41000", "=SUM(E17:H17)", "Active"),
            listOf("T204", "Linus Torvalds", "Engineering", "Kernel Lead", "37000", "38500", "40500", "43000", "=SUM(E18:H18)", "Active"),
            listOf("T205", "Margaret Hamilton", "Engineering", "Apollo Lead", "36500", "38000", "39500", "42000", "=SUM(E19:H19)", "Active")
        )
        engData.forEachIndexed { i, rowData ->
            val r = 14 + i
            rowData.forEachIndexed { c, value ->
                engine.setCell(r, c, value)
            }
            applyCurrencyFormatting(r)
        }
        // Subtotal row 19
        engine.setCell(19, 0, "Section 2 Total")
        engine.setCellBold(19, 0, true)
        engine.setCell(19, 4, "=SUM(E15:E19)")
        engine.setCell(19, 5, "=SUM(F15:F19)")
        engine.setCell(19, 6, "=SUM(G15:G19)")
        engine.setCell(19, 7, "=SUM(H15:H19)")
        engine.setCell(19, 8, "=SUM(I15:I19)")
        applyCurrencyFormatting(19)
        for (c in 0..9) {
            engine.setCellBold(19, c, true)
            engine.setCellColor(19, c, 0xFFE3F2FBL.toInt())
        }

        // Section 3: Operations & Logistics
        engine.setCell(21, 0, "Section 3: Operations & Logistics")
        engine.mergeRange(21, 0, 21, 9)
        engine.setCellBold(21, 0, true)
        engine.setCellColor(21, 0, 0xFFE65100L.toInt())
        engine.setCellTextColor(21, 0, 0xFFFFFFFFL.toInt())

        setupHeaderRow(22, 0xFFFFE0B2L.toInt())

        val opsData = listOf(
            listOf("O301", "James Holden", "Operations", "Ops Director", "25000", "26500", "28000", "29500", "=SUM(E24:H24)", "Active"),
            listOf("O302", "Naomi Nagata", "Operations", "Chief Engineer", "27000", "28500", "30000", "32000", "=SUM(E25:H25)", "Active"),
            listOf("O303", "Amos Burton", "Operations", "Field Lead", "23000", "24000", "25500", "27000", "=SUM(E26:H26)", "Active"),
            listOf("O304", "Alex Kamal", "Operations", "Logistics Pilot", "22000", "23000", "24500", "26000", "=SUM(E27:H27)", "Active"),
            listOf("O305", "Roberta Draper", "Operations", "Security Spec", "24000", "25500", "27000", "28500", "=SUM(E28:H28)", "Active")
        )
        opsData.forEachIndexed { i, rowData ->
            val r = 23 + i
            rowData.forEachIndexed { c, value ->
                engine.setCell(r, c, value)
            }
            applyCurrencyFormatting(r)
        }
        // Subtotal row 28
        engine.setCell(28, 0, "Section 3 Total")
        engine.setCellBold(28, 0, true)
        engine.setCell(28, 4, "=SUM(E24:E28)")
        engine.setCell(28, 5, "=SUM(F24:F28)")
        engine.setCell(28, 6, "=SUM(G24:G28)")
        engine.setCell(28, 7, "=SUM(H24:H28)")
        engine.setCell(28, 8, "=SUM(I24:I28)")
        applyCurrencyFormatting(28)
        for (c in 0..9) {
            engine.setCellBold(28, c, true)
            engine.setCellColor(28, c, 0xFFFFF3E0L.toInt())
        }

        // Section 4: Sales & Client Growth
        engine.setCell(30, 0, "Section 4: Sales & Client Growth")
        engine.mergeRange(30, 0, 30, 9)
        engine.setCellBold(30, 0, true)
        engine.setCellColor(30, 0, 0xFF6A1B9AL.toInt())
        engine.setCellTextColor(30, 0, 0xFFFFFFFFL.toInt())

        setupHeaderRow(31, 0xFFE1BEE7L.toInt())

        val salesData = listOf(
            listOf("S401", "Don Draper", "Sales", "Creative Dir", "31000", "33000", "35000", "38000", "=SUM(E33:H33)", "Active"),
            listOf("S402", "Peggy Olson", "Sales", "Copy Chief", "26000", "28000", "30000", "32500", "=SUM(E34:H34)", "Active"),
            listOf("S403", "Pete Campbell", "Sales", "Acct Exec", "25000", "26500", "28000", "30000", "=SUM(E35:H35)", "Active"),
            listOf("S404", "Joan Harris", "Sales", "Ops Partner", "29000", "30500", "32500", "35000", "=SUM(E36:H36)", "Active"),
            listOf("S405", "Roger Sterling", "Sales", "Senior Partner", "33000", "34500", "36500", "39000", "=SUM(E37:H37)", "Active")
        )
        salesData.forEachIndexed { i, rowData ->
            val r = 32 + i
            rowData.forEachIndexed { c, value ->
                engine.setCell(r, c, value)
            }
            applyCurrencyFormatting(r)
        }
        // Subtotal row 37
        engine.setCell(37, 0, "Section 4 Total")
        engine.setCellBold(37, 0, true)
        engine.setCell(37, 4, "=SUM(E33:E37)")
        engine.setCell(37, 5, "=SUM(F33:F37)")
        engine.setCell(37, 6, "=SUM(G33:G37)")
        engine.setCell(37, 7, "=SUM(H33:H37)")
        engine.setCell(37, 8, "=SUM(I33:I37)")
        applyCurrencyFormatting(37)
        for (c in 0..9) {
            engine.setCellBold(37, c, true)
            engine.setCellColor(37, c, 0xFFF3E5F5L.toInt())
        }

        // Grand Total row 39
        engine.setCell(39, 0, "Grand Company Total")
        engine.setCellBold(39, 0, true)
        engine.setCell(39, 4, "=SUM(E11,E20,E29,E38)")
        engine.setCell(39, 5, "=SUM(F11,F20,F29,F38)")
        engine.setCell(39, 6, "=SUM(G11,G20,G29,G38)")
        engine.setCell(39, 7, "=SUM(H11,H20,H29,H38)")
        engine.setCell(39, 8, "=SUM(I11,I20,I29,I38)")
        engine.setCell(39, 9, "AUDITED")
        applyCurrencyFormatting(39)
        for (c in 0..9) {
            engine.setCellBold(39, c, true)
            engine.setCellColor(39, c, 0xFFFFF9C4L.toInt()) // Yellow highlight
        }

        engine.recalculateAllFormulas()
    }
}
