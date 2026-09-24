package com.SpeakSheet.data

data class SampleSheet(
    val id: String,
    val title: String,
    val description: String,
    val category: String,
    val rows: List<List<String>>
)

object SampleSheets {
    val BUDGET = SampleSheet(
        id = "monthly_budget",
        title = "Monthly Budget 2026",
        description = "Personal finances with categories, budgeted vs actual, and differences",
        category = "Finance",
        rows = listOf(
            listOf("Category", "Budget ($)", "Actual ($)", "Difference ($)", "Status"),
            listOf("Rent & Mortgage", "1800", "1800", "0", "On Budget"),
            listOf("Groceries & Food", "600", "645", "-45", "Over Budget"),
            listOf("Utilities & Internet", "250", "230", "20", "Under Budget"),
            listOf("Transportation & Gas", "300", "280", "20", "Under Budget"),
            listOf("Healthcare & Insurance", "200", "200", "0", "On Budget"),
            listOf("Entertainment & Dining", "250", "310", "-60", "Over Budget"),
            listOf("Savings & Investment", "800", "850", "50", "Exceeded Goal"),
            listOf("Miscellaneous", "150", "95", "55", "Under Budget"),
            listOf("Total Expenses", "4350", "4410", "-60", "Review Needed")
        )
    )

    val SALES = SampleSheet(
        id = "sales_performance",
        title = "Sales Performance Q3",
        description = "Team quotas, actual revenue, commission payout, and performance status",
        category = "Business",
        rows = listOf(
            listOf("Sales Rep", "Region", "Target ($)", "Actual Sales ($)", "Commission ($)", "Status"),
            listOf("Sarah Johnson", "North America", "50000", "62400", "6240", "Top Performer"),
            listOf("Marcus Chen", "Asia Pacific", "45000", "48200", "4820", "Target Met"),
            listOf("Elena Rodriguez", "Europe", "40000", "37500", "3750", "Near Target"),
            listOf("David Kim", "Latin America", "35000", "41200", "4120", "Target Met"),
            listOf("Amina Diallo", "Middle East", "30000", "35800", "3580", "Target Met"),
            listOf("Liam O'Connor", "North America", "50000", "54000", "5400", "Target Met"),
            listOf("Team Totals", "All Regions", "250000", "279100", "27910", "Goal Achieved")
        )
    )

    val GRADEBOOK = SampleSheet(
        id = "student_gradebook",
        title = "Student Gradebook",
        description = "Course records with assignments, midterm, final grade, and attendance",
        category = "Education",
        rows = listOf(
            listOf("Student ID", "Student Name", "Assignment 1", "Assignment 2", "Midterm", "Final Project", "Grade", "Attendance"),
            listOf("STU-101", "Alice Walker", "92", "95", "88", "94", "A", "98%"),
            listOf("STU-102", "Brian Smith", "78", "82", "75", "80", "C+", "85%"),
            listOf("STU-103", "Chloe Davis", "96", "98", "94", "99", "A+", "100%"),
            listOf("STU-104", "Daniel Martinez", "85", "88", "82", "90", "B", "92%"),
            listOf("STU-105", "Emma Wilson", "90", "91", "89", "93", "A-", "95%"),
            listOf("Class Avg", "All Students", "88.2", "90.8", "85.6", "91.2", "B+", "94%")
        )
    )

    val ALL_SAMPLES = listOf(BUDGET, SALES, GRADEBOOK)

    fun getSample(id: String): SampleSheet? {
        return ALL_SAMPLES.find { it.id == id }
    }
}
