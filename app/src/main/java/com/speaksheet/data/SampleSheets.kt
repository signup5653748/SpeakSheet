package com.speaksheet.data

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

    val SECTIONED_REPORT = SampleSheet(
        id = "sectioned_report",
        title = "Sectioned Report",
        description = "Sectioned report template with title, description, and 4 sections of 5 rows each",
        category = "Reports",
        rows = listOf(
            listOf("EXAMPLE FILE NAME"),
            listOf("Description line 1 - introduction and overview of the sectioned report data"),
            listOf("Description line 2 - additional context, metrics, and operational notes"),
            listOf("Description line 3 - summary instructions and review guidelines"),
            listOf("Item", "Category", "Status", "Score"),
            listOf("Item 1", "Category 1", "Active", "95"),
            listOf("Item 2", "Category 1", "Pending", "82"),
            listOf("Item 3", "Category 2", "Active", "88"),
            listOf("Item 4", "Category 2", "Pending", "91"),
            listOf("Item 5", "Category 3", "Active", "78"),
            listOf("Section 1 description - this is a full-width text area after every 5 data rows. There are no internal column dividers here."),
            listOf("Item", "Category", "Status", "Score"),
            listOf("Item 6", "Category 3", "Pending", "85"),
            listOf("Item 7", "Category 4", "Active", "92"),
            listOf("Item 8", "Category 4", "Pending", "76"),
            listOf("Item 9", "Category 1", "Active", "89"),
            listOf("Item 10", "Category 2", "Pending", "94"),
            listOf("Section 2 description - this is a full-width text area after every 5 data rows. There are no internal column dividers here."),
            listOf("Item", "Category", "Status", "Score"),
            listOf("Item 11", "Category 2", "Active", "80"),
            listOf("Item 12", "Category 3", "Pending", "88"),
            listOf("Item 13", "Category 3", "Active", "93"),
            listOf("Item 14", "Category 4", "Pending", "82"),
            listOf("Item 15", "Category 1", "Active", "96"),
            listOf("Section 3 description - this is a full-width text area after every 5 data rows. There are no internal column dividers here."),
            listOf("Item", "Category", "Status", "Score"),
            listOf("Item 16", "Category 1", "Pending", "79"),
            listOf("Item 17", "Category 2", "Active", "90"),
            listOf("Item 18", "Category 2", "Pending", "84"),
            listOf("Item 19", "Category 3", "Active", "95"),
            listOf("Item 20", "Category 4", "Pending", "88"),
            listOf("Section 4 description - this is a full-width text area after every 5 data rows. There are no internal column dividers here.")
        )
    )

    val ALL_SAMPLES = listOf(BUDGET, SALES, GRADEBOOK, SECTIONED_REPORT)

    fun getSample(id: String): SampleSheet? {
        return ALL_SAMPLES.find { it.id == id }
    }
}
