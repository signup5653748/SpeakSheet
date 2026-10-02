package com.speaksheet.ui.screens

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.speech.RecognizerIntent
import java.util.Locale
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import com.speaksheet.data.DeleteMode
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.FormatAlignLeft
import androidx.compose.material.icons.automirrored.filled.FormatAlignRight
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.AttachMoney
import androidx.compose.material.icons.filled.BorderAll
import androidx.compose.material.icons.filled.BorderClear
import androidx.compose.material.icons.filled.BorderOuter
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ColorLens
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.FitScreen
import androidx.compose.material.icons.filled.FormatAlignCenter
import androidx.compose.material.icons.filled.FormatBold
import androidx.compose.material.icons.filled.FormatColorText
import androidx.compose.material.icons.filled.FormatItalic
import androidx.compose.material.icons.filled.Functions
import androidx.compose.material.icons.filled.GridOn
import androidx.compose.material.icons.filled.Height
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Percent
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.South
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Tag
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material.icons.filled.VerticalAlignBottom
import androidx.compose.material.icons.filled.VerticalAlignTop
import androidx.compose.material.icons.filled.WidthNormal
import androidx.compose.material.icons.filled.WrapText
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.material.icons.filled.ZoomOut
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import com.speaksheet.utils.SpreadsheetEngine
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.speaksheet.ui.theme.GreenPrimary
import com.speaksheet.viewmodel.MainViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

class CellLayoutCache(private val textMeasurer: TextMeasurer) {
    private val cellCache = object : LinkedHashMap<Long, CachedCellLayout>(512, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Long, CachedCellLayout>?): Boolean {
            return size > 600
        }
    }
    private val colHeaderCache = HashMap<Long, CachedHeaderLayout>()
    private val rowHeaderCache = HashMap<Long, CachedHeaderLayout>()

    data class CachedCellLayout(
        val text: String,
        val isHeader: Boolean,
        val isWrapped: Boolean,
        val maxWidthPx: Int,
        val textColor: Color,
        val layout: TextLayoutResult
    )

    data class CachedHeaderLayout(
        val text: String,
        val isSelected: Boolean,
        val layout: TextLayoutResult
    )

    fun getCellLayout(
        r: Int,
        c: Int,
        text: String,
        isHeader: Boolean,
        style: TextStyle,
        isWrapped: Boolean = false,
        maxWidthPx: Int = Int.MAX_VALUE
    ): TextLayoutResult {
        val key = (r.toLong() shl 32) or (c.toLong() and 0xFFFFFFFFL)
        val cached = cellCache[key]
        if (cached != null && cached.text == text && cached.isHeader == isHeader && cached.isWrapped == isWrapped && cached.maxWidthPx == maxWidthPx && cached.textColor == style.color) {
            return cached.layout
        }
        val layout = if (isWrapped) {
            textMeasurer.measure(
                text = text,
                style = style,
                constraints = Constraints(maxWidth = maxWidthPx.coerceAtLeast(1))
            )
        } else {
            textMeasurer.measure(
                text = text,
                style = style,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        cellCache[key] = CachedCellLayout(text, isHeader, isWrapped, maxWidthPx, style.color, layout)
        return layout
    }

    fun getColHeaderLayout(
        c: Int,
        label: String,
        isSelected: Boolean,
        style: TextStyle
    ): TextLayoutResult {
        val key = (c.toLong() shl 1) or (if (isSelected) 1L else 0L)
        val cached = colHeaderCache[key]
        if (cached != null && cached.text == label && cached.isSelected == isSelected) {
            return cached.layout
        }
        val layout = textMeasurer.measure(
            text = label,
            style = style,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        colHeaderCache[key] = CachedHeaderLayout(label, isSelected, layout)
        return layout
    }

    fun getRowHeaderLayout(
        r: Int,
        label: String,
        isSelected: Boolean,
        style: TextStyle
    ): TextLayoutResult {
        val key = (r.toLong() shl 1) or (if (isSelected) 1L else 0L)
        val cached = rowHeaderCache[key]
        if (cached != null && cached.text == label && cached.isSelected == isSelected) {
            return cached.layout
        }
        val layout = textMeasurer.measure(
            text = label,
            style = style
        )
        rowHeaderCache[key] = CachedHeaderLayout(label, isSelected, layout)
        return layout
    }

    fun clear() {
        cellCache.clear()
        colHeaderCache.clear()
        rowHeaderCache.clear()
    }
}

enum class ColorPickerTab { BACKGROUND, TEXT }

const val MIN_ZOOM = 0.20f
const val MAX_ZOOM = 3.0f

val ACTION_MENU_TABS = listOf(
    "Quick Actions",
    "Cell",
    "Alignment & Borders",
    "Clipboard",
    "Banner",
    "View",
    "Formulas",
    "Number Format",
    "Data",
    "Row",
    "Column"
)
const val TAB_INDEX_QUICK_ACTIONS = 0
const val TAB_INDEX_CELL = 1
const val TAB_INDEX_ALIGNMENT_BORDERS = 2
const val TAB_INDEX_CLIPBOARD = 3
const val TAB_INDEX_BANNER = 4
const val TAB_INDEX_VIEW = 5
const val TAB_INDEX_FORMULAS = 6
const val TAB_INDEX_NUMBER_FORMAT = 7
const val TAB_INDEX_DATA = 8
const val TAB_INDEX_ROW = 9
const val TAB_INDEX_COLUMN = 10

data class MenuItemData(
    val title: String,
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
    val actionId: String,
    val subtitle: String? = null
)

fun getAllActionsMap(
    r: Int,
    c: Int,
    engine: SpreadsheetEngine,
    settings: com.speaksheet.data.AppSettings
): Map<String, MenuItemData> {
    val allList = mutableListOf<MenuItemData>()
    for (tab in 1..10) {
        allList.addAll(getActionsForTab(tab, r, c, engine, settings))
    }
    return allList.associateBy { it.actionId }
}

fun getQuickActionsList(
    r: Int,
    c: Int,
    engine: SpreadsheetEngine,
    settings: com.speaksheet.data.AppSettings
): List<MenuItemData> {
    val allMap = getAllActionsMap(r, c, engine, settings)
    return settings.quickActionIds.mapNotNull { allMap[it] }
}

fun getActionsForTab(
    tabIndex: Int,
    r: Int,
    c: Int,
    engine: SpreadsheetEngine,
    settings: com.speaksheet.data.AppSettings
): List<MenuItemData> {
    return when (tabIndex) {
        TAB_INDEX_QUICK_ACTIONS -> {
            getQuickActionsList(r, c, engine, settings)
        }
        TAB_INDEX_CELL -> listOf(
            MenuItemData("Edit cell", Icons.Default.Edit, "edit"),
            MenuItemData("Clear text", Icons.Default.Delete, "clear_text"),
            MenuItemData("Clear formatting", Icons.Default.Clear, "clear_formatting"),
            MenuItemData("Clear cell", Icons.Default.DeleteSweep, "clear_cell"),
            MenuItemData(if (engine.getCellBold(r, c)) "Remove bold" else "Make bold", Icons.Default.FormatBold, "toggle_bold"),
            MenuItemData(if (engine.getCellItalic(r, c)) "Remove italic" else "Make italic", Icons.Default.FormatItalic, "toggle_italic"),
            MenuItemData(if (engine.isWrapEnabled(c)) "Disable wrap" else "Wrap text", Icons.Default.WrapText, "wrap_text"),
            MenuItemData("Background Color", Icons.Default.Palette, "cell_bg_color"),
            MenuItemData("Text Color", Icons.Default.FormatColorText, "cell_text_color")
        )
        TAB_INDEX_ALIGNMENT_BORDERS -> listOf(
            MenuItemData("Align left", Icons.AutoMirrored.Filled.FormatAlignLeft, "align_left"),
            MenuItemData("Align center", Icons.Default.FormatAlignCenter, "align_center"),
            MenuItemData("Align right", Icons.AutoMirrored.Filled.FormatAlignRight, "align_right"),
            MenuItemData("Border: None", Icons.Default.BorderClear, "border_none"),
            MenuItemData("Border: All", Icons.Default.BorderAll, "border_all"),
            MenuItemData("Border: Outer", Icons.Default.BorderOuter, "border_outer")
        )
        TAB_INDEX_CLIPBOARD -> listOf(
            MenuItemData("Copy", Icons.Default.ContentCopy, "copy"),
            MenuItemData("Paste", Icons.Default.ContentPaste, "paste"),
            MenuItemData("Paste values", Icons.Default.ContentPaste, "paste_values"),
            MenuItemData("Paste formats", Icons.Default.ContentPaste, "paste_formats"),
            MenuItemData("Paste formulas", Icons.Default.ContentPaste, "paste_formulas"),
            MenuItemData("Fill down", Icons.Default.South, "fill_down"),
            MenuItemData("Fill right", Icons.AutoMirrored.Filled.ArrowForward, "fill_right")
        )
        TAB_INDEX_BANNER -> listOf(
            MenuItemData("Banner Row", Icons.Default.Edit, "convert_to_banner"),
            MenuItemData("Unmerge banner", Icons.Default.Clear, "unmerge_banner"),
            MenuItemData("Banner background color", Icons.Default.ColorLens, "banner_color", "Color of the selected banner/divider row"),
            MenuItemData("Header cell background color", Icons.Default.Palette, "header_bg_color", "Background fill color of the header row"),
            MenuItemData("Header cell text color", Icons.Default.FormatColorText, "header_text_color", "Text color of the header row"),
            MenuItemData(if (engine.isHeaderRow(r)) "Clear header row" else "Set header row", Icons.Default.Check, if (engine.isHeaderRow(r)) "clear_header_row" else "set_header_row")
        )
        TAB_INDEX_VIEW -> listOf(
            MenuItemData("Freeze row", Icons.Default.VerticalAlignTop, "freeze_top_row"),
            MenuItemData("Freeze col", Icons.Default.VerticalAlignBottom, "freeze_first_col"),
            MenuItemData("Freeze selected", Icons.Default.Lock, "freeze_selected"),
            MenuItemData("Unfreeze panes", Icons.Default.LockOpen, "unfreeze_panes"),
            MenuItemData(if (settings.showGridlines) "Hide grid" else "Show grid", Icons.Default.GridOn, "toggle_gridlines"),
            MenuItemData("Zoom", Icons.Default.ZoomIn, "zoom_controls")
        )
        TAB_INDEX_FORMULAS -> listOf(
            MenuItemData("=SUM", Icons.Default.Functions, "formula_sum"),
            MenuItemData("=AVERAGE", Icons.Default.Functions, "formula_avg"),
            MenuItemData("=COUNT", Icons.Default.Functions, "formula_count"),
            MenuItemData("=MIN", Icons.Default.Functions, "formula_min"),
            MenuItemData("=MAX", Icons.Default.Functions, "formula_max"),
            MenuItemData("=SORT", Icons.Default.Functions, "formula_sort"),
            MenuItemData("=IF", Icons.Default.Functions, "formula_if"),
            MenuItemData("=VLOOKUP", Icons.Default.Functions, "formula_vlookup")
        )
        TAB_INDEX_NUMBER_FORMAT -> listOf(
            MenuItemData("Fmt General", Icons.Default.TextFields, "fmt_general"),
            MenuItemData("Fmt Number", Icons.Default.Tag, "fmt_number"),
            MenuItemData("Fmt Currency", Icons.Default.AttachMoney, "fmt_currency"),
            MenuItemData("Fmt Percent", Icons.Default.Percent, "fmt_percent"),
            MenuItemData("Fmt Date", Icons.Default.CalendarToday, "fmt_date")
        )
        TAB_INDEX_DATA -> listOf(
            MenuItemData("Sort A-Z", Icons.Default.ArrowUpward, "sort_asc"),
            MenuItemData("Sort Z-A", Icons.Default.ArrowDownward, "sort_desc"),
            MenuItemData("Find & Replace", Icons.Default.Search, "find_replace")
        )
        TAB_INDEX_ROW -> listOf(
            MenuItemData("Resize row", Icons.Default.Height, "resize_row", "Change height of selected row"),
            MenuItemData("Insert above", Icons.Default.Add, "insert_row_above"),
            MenuItemData("Insert below", Icons.Default.Add, "insert_row_below"),
            MenuItemData("Delete row", Icons.Default.Delete, "delete_row"),
            MenuItemData("Clear row", Icons.Default.Clear, "clear_row"),
            MenuItemData("Row color", Icons.Default.Palette, "row_color"),
            MenuItemData("Row text color", Icons.Default.FormatColorText, "row_text_color"),
            MenuItemData("Speak row", Icons.AutoMirrored.Filled.VolumeUp, "speak_row")
        )
        else -> listOf(
            MenuItemData("Resize col", Icons.Default.WidthNormal, "resize_column", "Change width of selected column"),
            MenuItemData("Speak col", Icons.AutoMirrored.Filled.VolumeUp, "speak_column"),
            MenuItemData("Col color", Icons.Default.Palette, "column_color"),
            MenuItemData("Clear col", Icons.Default.Clear, "clear_column"),
            MenuItemData("Delete col", Icons.Default.Delete, "delete_column")
        )
    }
}

fun executeSpreadsheetAction(
    actionId: String,
    r: Int,
    c: Int,
    engine: SpreadsheetEngine,
    viewModel: MainViewModel,
    settings: com.speaksheet.data.AppSettings,
    context: Context,
    selectedColumn: Int? = null,
    onDismiss: () -> Unit = {},
    onEditCell: (Pair<Int, Int>, String?) -> Unit,
    onOpenColorPicker: (ColorTarget, ColorPickerTab) -> Unit,
    onOpenZoom: () -> Unit,
    onOpenFindReplace: () -> Unit,
    onConfirmClearCol: (Int) -> Unit,
    onConfirmClearRow: (Int) -> Unit,
    onConfirmDeleteRow: (Int) -> Unit,
    onOpenResizeColumn: (Int) -> Unit = {},
    onOpenResizeRow: (Int) -> Unit = {}
) {
    when (actionId) {
        "edit" -> {
            onDismiss()
            onEditCell(Pair(r, c), null)
        }
        "resize_column" -> {
            onDismiss()
            onOpenResizeColumn(selectedColumn ?: c)
        }
        "resize_row" -> {
            onDismiss()
            onOpenResizeRow(r)
        }
        "copy" -> {
            if (selectedColumn != null) {
                viewModel.copyColumn(context, selectedColumn)
            } else {
                viewModel.copyCell(context, r, c)
            }
        }
        "paste" -> {
            if (selectedColumn != null) {
                viewModel.pasteColumn(context, selectedColumn, startRow = 0)
            } else {
                viewModel.pasteCell(context, r, c)
            }
        }
        "paste_values" -> viewModel.pasteSpecial(r, c, r, c, SpreadsheetEngine.PasteMode.VALUES_ONLY)
        "paste_formats" -> viewModel.pasteSpecial(r, c, r, c, SpreadsheetEngine.PasteMode.FORMATS_ONLY)
        "paste_formulas" -> viewModel.pasteSpecial(r, c, r, c, SpreadsheetEngine.PasteMode.FORMULAS_ONLY)
        "fill_down" -> viewModel.fillDown(r, c, minOf(r + 5, engine.maxRow - 1), c)
        "fill_right" -> viewModel.fillRight(r, c, r, minOf(c + 3, engine.maxCol - 1))
        "clear_text" -> viewModel.clearCellText(r, c)
        "clear_formatting" -> viewModel.clearCellFormatting(r, c)
        "clear_cell" -> viewModel.deleteCell(r, c)

        "toggle_bold" -> viewModel.setCellBold(r, c, !engine.getCellBold(r, c))
        "toggle_italic" -> viewModel.setCellItalic(r, c, !engine.getCellItalic(r, c))
        "align_left" -> viewModel.setCellAlignment(r, c, 0)
        "align_center" -> viewModel.setCellAlignment(r, c, 1)
        "align_right" -> viewModel.setCellAlignment(r, c, 2)
        "wrap_text" -> viewModel.toggleColumnWrap(c)
        "cell_bg_color" -> {
            onDismiss()
            onOpenColorPicker(ColorTarget.Cell(r, c), ColorPickerTab.BACKGROUND)
        }
        "cell_text_color" -> {
            onDismiss()
            onOpenColorPicker(ColorTarget.Cell(r, c), ColorPickerTab.TEXT)
        }

        "convert_to_banner" -> viewModel.convertRowToBanner(r)
        "unmerge_banner" -> viewModel.unmergeBanner(r)
        "border_none" -> viewModel.setCellBorders(r, c, 0)
        "border_all" -> viewModel.setCellBorders(r, c, 1)
        "border_outer" -> viewModel.setCellBorders(r, c, 2)

        "freeze_top_row" -> viewModel.setFreezePanes(1, engine.frozenCols)
        "freeze_first_col" -> viewModel.setFreezePanes(engine.frozenRows, 1)
        "freeze_selected" -> viewModel.setFreezePanes(r + 1, c + 1)
        "unfreeze_panes" -> viewModel.setFreezePanes(0, 0)
        "toggle_gridlines" -> viewModel.updateSettings(settings.copy(showGridlines = !settings.showGridlines))
        "zoom_controls" -> {
            onDismiss()
            onOpenZoom()
        }
        "banner_color" -> {
            onDismiss()
            onOpenColorPicker(ColorTarget.Row(r), ColorPickerTab.BACKGROUND)
        }
        "header_bg_color" -> {
            onDismiss()
            onOpenColorPicker(ColorTarget.Header(r), ColorPickerTab.BACKGROUND)
        }
        "header_text_color" -> {
            onDismiss()
            onOpenColorPicker(ColorTarget.Header(r), ColorPickerTab.TEXT)
        }

        "formula_sum" -> {
            onDismiss()
            onEditCell(Pair(r, c), "=SUM(")
        }
        "formula_avg" -> {
            onDismiss()
            onEditCell(Pair(r, c), "=AVERAGE(")
        }
        "formula_count" -> {
            onDismiss()
            onEditCell(Pair(r, c), "=COUNT(")
        }
        "formula_min" -> {
            onDismiss()
            onEditCell(Pair(r, c), "=MIN(")
        }
        "formula_max" -> {
            onDismiss()
            onEditCell(Pair(r, c), "=MAX(")
        }
        "formula_sort" -> {
            onDismiss()
            onEditCell(Pair(r, c), "=SORT(")
        }
        "formula_if" -> {
            onDismiss()
            onEditCell(Pair(r, c), "=IF(")
        }
        "formula_vlookup" -> {
            onDismiss()
            onEditCell(Pair(r, c), "=VLOOKUP(")
        }

        "sort_asc" -> viewModel.sortColumn(c, true)
        "sort_desc" -> viewModel.sortColumn(c, false)
        "find_replace" -> {
            onDismiss()
            onOpenFindReplace()
        }
        "fmt_general" -> viewModel.setCellNumberFormat(r, c, "General")
        "fmt_number" -> viewModel.setCellNumberFormat(r, c, "Number")
        "fmt_currency" -> viewModel.setCellNumberFormat(r, c, "Currency")
        "fmt_percent" -> viewModel.setCellNumberFormat(r, c, "Percent")
        "fmt_date" -> viewModel.setCellNumberFormat(r, c, "Date")

        "insert_row_above" -> viewModel.insertRowAbove(r)
        "insert_row_below" -> viewModel.insertRowBelow(r)
        "delete_row" -> {
            onDismiss()
            onConfirmDeleteRow(r)
        }
        "clear_row" -> {
            onDismiss()
            onConfirmClearRow(r)
        }
        "row_color" -> {
            onDismiss()
            onOpenColorPicker(ColorTarget.Row(r), ColorPickerTab.BACKGROUND)
        }
        "row_text_color" -> {
            onDismiss()
            onOpenColorPicker(ColorTarget.Row(r), ColorPickerTab.TEXT)
        }
        "set_header_row" -> viewModel.setHeaderRow(r)
        "clear_header_row" -> viewModel.clearHeaderRow(r)
        "speak_row" -> viewModel.speakRow(r)

        "speak_column" -> viewModel.speakColumn(c)
        "column_color" -> {
            onDismiss()
            onOpenColorPicker(ColorTarget.Column(c), ColorPickerTab.BACKGROUND)
        }
        "clear_column" -> {
            onDismiss()
            onConfirmClearCol(c)
        }
        "delete_column" -> viewModel.clearColumn(c)
    }
}

@Composable
fun QuickActionsCustomizationDialog(
    currentSelectedIds: List<String>,
    onDismiss: () -> Unit,
    onSave: (List<String>) -> Unit,
    r: Int = 0,
    c: Int = 0,
    engine: SpreadsheetEngine,
    settings: com.speaksheet.data.AppSettings
) {
    val themeAccentColor = Color(settings.themeColor)
    var selectedIds by remember { mutableStateOf(currentSelectedIds.toSet()) }
    val categories = remember(r, c) {
        listOf(
            "Cell" to getActionsForTab(TAB_INDEX_CELL, r, c, engine, settings),
            "Alignment & Borders" to getActionsForTab(TAB_INDEX_ALIGNMENT_BORDERS, r, c, engine, settings),
            "Clipboard" to getActionsForTab(TAB_INDEX_CLIPBOARD, r, c, engine, settings),
            "Banner" to getActionsForTab(TAB_INDEX_BANNER, r, c, engine, settings),
            "View" to getActionsForTab(TAB_INDEX_VIEW, r, c, engine, settings),
            "Formulas" to getActionsForTab(TAB_INDEX_FORMULAS, r, c, engine, settings),
            "Number Format" to getActionsForTab(TAB_INDEX_NUMBER_FORMAT, r, c, engine, settings),
            "Data" to getActionsForTab(TAB_INDEX_DATA, r, c, engine, settings),
            "Row" to getActionsForTab(TAB_INDEX_ROW, r, c, engine, settings),
            "Column" to getActionsForTab(TAB_INDEX_COLUMN, r, c, engine, settings)
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Star,
                    contentDescription = null,
                    tint = themeAccentColor,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text("Customize Quick Actions", fontWeight = FontWeight.Bold, fontSize = 18.sp)
            }
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth().heightIn(max = 420.dp)) {
                Text(
                    "Choose favorite actions from any menu tab to display in your Quick Actions list (saved across restarts).",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f, fill = false)
                ) {
                    categories.forEach { (categoryName, actions) ->
                        item {
                            Surface(
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                shape = RoundedCornerShape(4.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 10.dp, bottom = 4.dp)
                            ) {
                                Text(
                                    text = categoryName.uppercase(),
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = themeAccentColor,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        }
                        items(actions, key = { it.actionId }) { action ->
                            val isChecked = selectedIds.contains(action.actionId)
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .semantics(mergeDescendants = true) {
                                        role = androidx.compose.ui.semantics.Role.Checkbox
                                    }
                                    .clickable {
                                        selectedIds = if (isChecked) {
                                            selectedIds - action.actionId
                                        } else {
                                            selectedIds + action.actionId
                                        }
                                    }
                                    .padding(vertical = 4.dp, horizontal = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Checkbox(
                                    checked = isChecked,
                                    onCheckedChange = {
                                        selectedIds = if (it) selectedIds + action.actionId else selectedIds - action.actionId
                                    }
                                )
                                Spacer(Modifier.width(6.dp))
                                Icon(
                                    imageVector = action.icon,
                                    contentDescription = null,
                                    modifier = Modifier.size(20.dp),
                                    tint = if (isChecked) themeAccentColor else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(Modifier.width(10.dp))
                                Column {
                                    Text(
                                        text = action.title,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = if (isChecked) FontWeight.SemiBold else FontWeight.Normal
                                    )
                                    if (!action.subtitle.isNullOrBlank()) {
                                        Text(
                                            text = action.subtitle,
                                            style = MaterialTheme.typography.bodySmall,
                                            fontSize = 11.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onSave(selectedIds.toList())
                    onDismiss()
                },
                colors = ButtonDefaults.buttonColors(containerColor = themeAccentColor),
                modifier = Modifier.testTag("button_save_custom_quick_actions")
            ) {
                Text("Save Favorites (${selectedIds.size})")
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.testTag("button_cancel_custom_quick_actions")
            ) {
                Text("Cancel")
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun ActionMenuSheet(
    onDismiss: () -> Unit,
    pagerState: PagerState,
    targetCell: Pair<Int, Int>,
    engine: SpreadsheetEngine,
    viewModel: MainViewModel,
    settings: com.speaksheet.data.AppSettings,
    coroutineScope: kotlinx.coroutines.CoroutineScope,
    selectedColumn: Int? = null,
    onEditCell: (Pair<Int, Int>, String?) -> Unit,
    onOpenColorPicker: (ColorTarget, ColorPickerTab) -> Unit,
    onOpenZoom: () -> Unit,
    onOpenFindReplace: () -> Unit,
    onConfirmClearCol: (Int) -> Unit,
    onConfirmClearRow: (Int) -> Unit,
    onConfirmDeleteRow: (Int) -> Unit,
    onOpenResizeColumn: (Int) -> Unit = {},
    onOpenResizeRow: (Int) -> Unit = {}
) {
    val context = LocalContext.current
    val hapticFeedback = LocalHapticFeedback.current
    val (r, c) = targetCell
    val cellCoord = "${engine.getColumnName(c)}${r + 1}"
    var showCustomizeDialog by remember { mutableStateOf(false) }
    var focusedActionId by remember { mutableStateOf<String?>(null) }
    val themeAccentColor = Color(settings.themeColor)
    val selectedCellColor = Color(settings.selectedCellColor)

    LaunchedEffect(pagerState.currentPage) {
        focusedActionId = null
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surface,
        dragHandle = { BottomSheetDefaults.DragHandle() },
        modifier = Modifier.testTag("action_menu_sheet")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 2.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = themeAccentColor
                    ) {
                        Text(
                            text = cellCoord,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            fontSize = 12.sp,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "Actions Menu",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.size(32.dp).testTag("close_action_menu")
                ) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = "Close Menu",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            HorizontalDivider(
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                thickness = 0.5.dp,
                modifier = Modifier.padding(vertical = 4.dp)
            )

            ScrollableTabRow(
                selectedTabIndex = pagerState.currentPage,
                edgePadding = 8.dp,
                containerColor = Color.Transparent,
                contentColor = themeAccentColor,
                divider = {},
                modifier = Modifier
                    .fillMaxWidth()
                    .height(40.dp)
            ) {
                ACTION_MENU_TABS.forEachIndexed { index, tabName ->
                    val isSelected = pagerState.currentPage == index
                    Tab(
                        selected = isSelected,
                        onClick = {
                            if (pagerState.currentPage != index) {
                                coroutineScope.launch {
                                    pagerState.animateScrollToPage(index)
                                }
                                viewModel.ttsManager.speak("$tabName tab selected")
                                viewModel.updateSettings(settings.copy(lastActionMenuTab = index))
                            }
                        },
                        text = {
                            Text(
                                text = tabName,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                fontSize = 12.sp,
                                color = if (isSelected) themeAccentColor else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        },
                        modifier = Modifier
                            .semantics {
                                role = androidx.compose.ui.semantics.Role.Tab
                                selected = isSelected
                                contentDescription = "$tabName tab, ${index + 1} of ${ACTION_MENU_TABS.size}, ${if (isSelected) "selected" else "not selected"}"
                            }
                            .testTag("tab_${tabName.lowercase().replace(" ", "_").replace("&", "and")}")
                    )
                }
            }

            Spacer(Modifier.height(8.dp))

            HorizontalPager(
                state = pagerState,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(180.dp)
            ) { page ->
                if (page == TAB_INDEX_QUICK_ACTIONS) {
                    val quickActions = getActionsForTab(TAB_INDEX_QUICK_ACTIONS, r, c, engine, settings)
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 12.dp)
                    ) {
                        if (quickActions.isEmpty()) {
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxWidth(),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(
                                        "No Quick Actions added yet.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Spacer(Modifier.height(8.dp))
                                    Button(
                                        onClick = { showCustomizeDialog = true },
                                        colors = ButtonDefaults.buttonColors(containerColor = themeAccentColor),
                                        modifier = Modifier.testTag("button_add_quick_action_empty")
                                    ) {
                                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(Modifier.width(6.dp))
                                        Text("Add Quick Actions")
                                    }
                                }
                            }
                        } else {
                            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                                LazyVerticalGrid(
                                    columns = GridCells.Fixed(3),
                                    modifier = Modifier.fillMaxSize(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    items(quickActions, key = { it.actionId }) { action ->
                                        val isFocused = focusedActionId == action.actionId
                                        Surface(
                                            shape = RoundedCornerShape(8.dp),
                                            color = if (isFocused) selectedCellColor.copy(alpha = 0.22f) else MaterialTheme.colorScheme.surfaceVariant,
                                            border = if (isFocused) BorderStroke(2.dp, selectedCellColor) else null,
                                            tonalElevation = if (isFocused) 6.dp else 2.dp,
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .heightIn(min = 48.dp)
                                                .testTag("action_${action.actionId}")
                                                .semantics(mergeDescendants = true) {
                                                    role = androidx.compose.ui.semantics.Role.Button
                                                    contentDescription = action.title + (if (!action.subtitle.isNullOrBlank()) ", ${action.subtitle}" else "") + (if (isFocused) ", selected. Tap again to execute" else "") + ". Long press to remove from favorites."
                                                }
                                                .combinedClickable(
                                                    onClick = {
                                                        if (settings.overflowMenuTwoStepMode) {
                                                            if (focusedActionId != action.actionId) {
                                                                focusedActionId = action.actionId
                                                                if (settings.vibrateOnSelect) {
                                                                    hapticFeedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                                                }
                                                                val announceText = action.title + if (!action.subtitle.isNullOrBlank()) ", ${action.subtitle}" else ""
                                                                viewModel.ttsManager.speak(announceText)
                                                            } else {
                                                                if (settings.vibrateOnSelect) {
                                                                    hapticFeedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                                                }
                                                                executeSpreadsheetAction(
                                                                    actionId = action.actionId,
                                                                    r = r,
                                                                    c = c,
                                                                    engine = engine,
                                                                    viewModel = viewModel,
                                                                    settings = settings,
                                                                    context = context,
                                                                    selectedColumn = selectedColumn,
                                                                    onDismiss = onDismiss,
                                                                    onEditCell = onEditCell,
                                                                    onOpenColorPicker = onOpenColorPicker,
                                                                    onOpenZoom = onOpenZoom,
                                                                    onOpenFindReplace = onOpenFindReplace,
                                                                    onConfirmClearCol = onConfirmClearCol,
                                                                    onConfirmClearRow = onConfirmClearRow,
                                                                    onConfirmDeleteRow = onConfirmDeleteRow,
                                                                    onOpenResizeColumn = onOpenResizeColumn,
                                                                    onOpenResizeRow = onOpenResizeRow
                                                                )
                                                            }
                                                        } else {
                                                            focusedActionId = null
                                                            if (settings.vibrateOnSelect) {
                                                                hapticFeedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                                            }
                                                            executeSpreadsheetAction(
                                                                actionId = action.actionId,
                                                                r = r,
                                                                c = c,
                                                                engine = engine,
                                                                viewModel = viewModel,
                                                                settings = settings,
                                                                context = context,
                                                                selectedColumn = selectedColumn,
                                                                onDismiss = onDismiss,
                                                                onEditCell = onEditCell,
                                                                onOpenColorPicker = onOpenColorPicker,
                                                                onOpenZoom = onOpenZoom,
                                                                onOpenFindReplace = onOpenFindReplace,
                                                                onConfirmClearCol = onConfirmClearCol,
                                                                onConfirmClearRow = onConfirmClearRow,
                                                                onConfirmDeleteRow = onConfirmDeleteRow,
                                                                onOpenResizeColumn = onOpenResizeColumn,
                                                                onOpenResizeRow = onOpenResizeRow
                                                            )
                                                        }
                                                    },
                                                    onLongClick = {
                                                        if (settings.vibrateOnSelect) {
                                                            hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                                                        }
                                                        viewModel.removeQuickAction(action.actionId)
                                                        viewModel.ttsManager.speak("Removed ${action.title} from favorites")
                                                    }
                                                )
                                        ) {
                                            Column(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(horizontal = 4.dp, vertical = 6.dp),
                                                horizontalAlignment = Alignment.CenterHorizontally,
                                                verticalArrangement = Arrangement.Center
                                            ) {
                                                Row(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.Center
                                                ) {
                                                    Icon(
                                                        imageVector = action.icon,
                                                        contentDescription = null,
                                                        tint = if (isFocused) selectedCellColor else themeAccentColor.copy(alpha = 0.85f),
                                                        modifier = Modifier.size(17.dp)
                                                    )
                                                    Spacer(Modifier.width(4.dp))
                                                    Text(
                                                        text = action.title,
                                                        style = MaterialTheme.typography.labelSmall,
                                                        fontWeight = if (isFocused) FontWeight.Bold else FontWeight.SemiBold,
                                                        color = if (isFocused) selectedCellColor else MaterialTheme.colorScheme.onSurface,
                                                        maxLines = 1,
                                                        overflow = TextOverflow.Ellipsis
                                                    )
                                                }
                                                if (!action.subtitle.isNullOrBlank()) {
                                                    Text(
                                                        text = action.subtitle,
                                                        fontSize = 8.sp,
                                                        color = if (isFocused) selectedCellColor.copy(alpha = 0.8f) else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                                        maxLines = 1,
                                                        overflow = TextOverflow.Ellipsis,
                                                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                                        lineHeight = 10.sp
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }

                                FloatingActionButton(
                                    onClick = { showCustomizeDialog = true },
                                    containerColor = themeAccentColor,
                                    contentColor = Color.White,
                                    modifier = Modifier
                                        .align(Alignment.BottomEnd)
                                        .size(36.dp)
                                        .semantics {
                                            role = androidx.compose.ui.semantics.Role.Button
                                            contentDescription = "Add Quick Action"
                                        }
                                        .testTag("fab_add_quick_action")
                                ) {
                                    Icon(Icons.Default.Add, contentDescription = "Add Quick Action", modifier = Modifier.size(20.dp))
                                }
                            }
                        }
                    }
                } else {
                    val actions = getActionsForTab(page, r, c, engine, settings)
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(3),
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(actions, key = { it.actionId }) { action ->
                            val isFocused = focusedActionId == action.actionId
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = if (isFocused) selectedCellColor.copy(alpha = 0.22f) else MaterialTheme.colorScheme.surfaceVariant,
                                border = if (isFocused) BorderStroke(2.dp, selectedCellColor) else null,
                                tonalElevation = if (isFocused) 6.dp else 2.dp,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 48.dp)
                                    .testTag("action_${action.actionId}")
                                    .semantics(mergeDescendants = true) {
                                        role = androidx.compose.ui.semantics.Role.Button
                                        contentDescription = action.title + (if (!action.subtitle.isNullOrBlank()) ", ${action.subtitle}" else "") + (if (isFocused) ", selected. Tap again to execute" else "")
                                    }
                                    .clickable {
                                        if (settings.overflowMenuTwoStepMode) {
                                            if (focusedActionId != action.actionId) {
                                                focusedActionId = action.actionId
                                                if (settings.vibrateOnSelect) {
                                                    hapticFeedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                                }
                                                val announceText = action.title + if (!action.subtitle.isNullOrBlank()) ", ${action.subtitle}" else ""
                                                viewModel.ttsManager.speak(announceText)
                                            } else {
                                                if (settings.vibrateOnSelect) {
                                                    hapticFeedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                                }
                                                executeSpreadsheetAction(
                                                    actionId = action.actionId,
                                                    r = r,
                                                    c = c,
                                                    engine = engine,
                                                    viewModel = viewModel,
                                                    settings = settings,
                                                    context = context,
                                                    selectedColumn = selectedColumn,
                                                    onDismiss = onDismiss,
                                                    onEditCell = onEditCell,
                                                    onOpenColorPicker = onOpenColorPicker,
                                                    onOpenZoom = onOpenZoom,
                                                    onOpenFindReplace = onOpenFindReplace,
                                                    onConfirmClearCol = onConfirmClearCol,
                                                    onConfirmClearRow = onConfirmClearRow,
                                                    onConfirmDeleteRow = onConfirmDeleteRow,
                                                    onOpenResizeColumn = onOpenResizeColumn,
                                                    onOpenResizeRow = onOpenResizeRow
                                                )
                                            }
                                        } else {
                                            focusedActionId = null
                                            if (settings.vibrateOnSelect) {
                                                hapticFeedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                            }
                                            executeSpreadsheetAction(
                                                actionId = action.actionId,
                                                r = r,
                                                c = c,
                                                engine = engine,
                                                viewModel = viewModel,
                                                settings = settings,
                                                context = context,
                                                selectedColumn = selectedColumn,
                                                onDismiss = onDismiss,
                                                onEditCell = onEditCell,
                                                onOpenColorPicker = onOpenColorPicker,
                                                onOpenZoom = onOpenZoom,
                                                onOpenFindReplace = onOpenFindReplace,
                                                onConfirmClearCol = onConfirmClearCol,
                                                onConfirmClearRow = onConfirmClearRow,
                                                onConfirmDeleteRow = onConfirmDeleteRow,
                                                onOpenResizeColumn = onOpenResizeColumn,
                                                onOpenResizeRow = onOpenResizeRow
                                            )
                                        }
                                    }
                            ) {
                                Column(
                                    modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 6.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Center
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.Center
                                    ) {
                                        Icon(
                                            imageVector = action.icon,
                                            contentDescription = null,
                                            tint = if (isFocused) selectedCellColor else themeAccentColor.copy(alpha = 0.85f),
                                            modifier = Modifier.size(17.dp)
                                        )
                                        Spacer(Modifier.width(4.dp))
                                        Text(
                                            text = action.title,
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = if (isFocused) FontWeight.Bold else FontWeight.SemiBold,
                                            color = if (isFocused) selectedCellColor else MaterialTheme.colorScheme.onSurface,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                    if (!action.subtitle.isNullOrBlank()) {
                                        Text(
                                            text = action.subtitle,
                                            fontSize = 8.sp,
                                            color = if (isFocused) selectedCellColor.copy(alpha = 0.8f) else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                            lineHeight = 10.sp
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showCustomizeDialog) {
        QuickActionsCustomizationDialog(
            currentSelectedIds = settings.quickActionIds,
            onDismiss = { showCustomizeDialog = false },
            onSave = { newIds ->
                viewModel.updateQuickActions(newIds)
                viewModel.ttsManager.speak("Quick actions updated")
            },
            r = r,
            c = c,
            engine = engine,
            settings = settings
        )
    }
}

@Composable
fun FindAndReplaceBar(
    query: String,
    onQueryChange: (String) -> Unit,
    replaceText: String,
    onReplaceTextChange: (String) -> Unit,
    matchCase: Boolean,
    onMatchCaseToggle: () -> Unit,
    matchCount: Int,
    currentMatchIndex: Int,
    onPrevMatch: () -> Unit,
    onNextMatch: () -> Unit,
    onReplace: () -> Unit,
    onReplaceAll: () -> Unit,
    onClose: () -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        tonalElevation = 4.dp,
        modifier = Modifier.fillMaxWidth().testTag("find_replace_bar")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 6.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                OutlinedTextField(
                    value = query,
                    onValueChange = onQueryChange,
                    placeholder = { Text("Find in sheet...", fontSize = 13.sp) },
                    singleLine = true,
                    leadingIcon = {
                        Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(18.dp))
                    },
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 44.dp)
                        .testTag("find_input_field"),
                    textStyle = MaterialTheme.typography.bodyMedium
                )

                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = MaterialTheme.colorScheme.surface,
                    modifier = Modifier.testTag("find_match_counter")
                ) {
                    Text(
                        text = if (matchCount == 0) "0/0" else "${currentMatchIndex + 1}/$matchCount",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (matchCount > 0) GreenPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 6.dp)
                    )
                }

                FilledIconToggleButton(
                    checked = matchCase,
                    onCheckedChange = { onMatchCaseToggle() },
                    modifier = Modifier.size(36.dp).testTag("find_match_case_toggle")
                ) {
                    Text("Aa", fontSize = 12.sp, fontWeight = if (matchCase) FontWeight.Bold else FontWeight.Normal)
                }

                IconButton(
                    onClick = onPrevMatch,
                    enabled = matchCount > 0,
                    modifier = Modifier.size(36.dp).testTag("find_prev_match")
                ) {
                    Icon(Icons.Default.KeyboardArrowUp, contentDescription = "Previous Match", modifier = Modifier.size(20.dp))
                }

                IconButton(
                    onClick = onNextMatch,
                    enabled = matchCount > 0,
                    modifier = Modifier.size(36.dp).testTag("find_next_match")
                ) {
                    Icon(Icons.Default.KeyboardArrowDown, contentDescription = "Next Match", modifier = Modifier.size(20.dp))
                }

                IconButton(
                    onClick = onClose,
                    modifier = Modifier.size(36.dp).testTag("find_close_button")
                ) {
                    Icon(Icons.Default.Close, contentDescription = "Close Find and Replace", modifier = Modifier.size(20.dp))
                }
            }

            Spacer(Modifier.height(4.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                OutlinedTextField(
                    value = replaceText,
                    onValueChange = onReplaceTextChange,
                    placeholder = { Text("Replace with...", fontSize = 13.sp) },
                    singleLine = true,
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 44.dp)
                        .testTag("replace_input_field"),
                    textStyle = MaterialTheme.typography.bodyMedium
                )

                FilledTonalButton(
                    onClick = onReplace,
                    enabled = matchCount > 0 && query.isNotEmpty(),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                    modifier = Modifier.height(40.dp).testTag("btn_replace")
                ) {
                    Text("Replace", fontSize = 12.sp)
                }

                Button(
                    onClick = onReplaceAll,
                    enabled = matchCount > 0 && query.isNotEmpty(),
                    colors = ButtonDefaults.buttonColors(containerColor = GreenPrimary),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                    modifier = Modifier.height(40.dp).testTag("btn_replace_all")
                ) {
                    Text("Replace all", fontSize = 12.sp)
                }
            }
        }
    }
}

sealed class ColorTarget {
    data class Cell(val r: Int, val c: Int) : ColorTarget()
    data class Column(val c: Int) : ColorTarget()
    data class Row(val r: Int) : ColorTarget()
    data class Header(val r: Int) : ColorTarget()
}

data class ColorPickerState(
    val target: ColorTarget,
    val initialTab: ColorPickerTab = ColorPickerTab.BACKGROUND
)

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun SpreadsheetScreen(
    viewModel: MainViewModel,
    onNavigateBack: () -> Unit
) {
    val fileName by viewModel.currentFileName.collectAsStateWithLifecycle()
    val settings by viewModel.appSettings.collectAsStateWithLifecycle()
    val refreshTrigger by viewModel.gridRefreshTrigger.collectAsStateWithLifecycle()
    val canUndo by viewModel.canUndo.collectAsStateWithLifecycle()
    val canRedo by viewModel.canRedo.collectAsStateWithLifecycle()
    val engine = viewModel.spreadsheetEngine
    val context = LocalContext.current
    val density = LocalDensity.current.density
    val hapticFeedback = LocalHapticFeedback.current
    val coroutineScope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    
    var selectedCell by remember { mutableStateOf<Pair<Int, Int>?>(Pair(0, 0)) }
    var selectedColumn by remember { mutableStateOf<Int?>(null) }
    var editingCell by remember { mutableStateOf<Pair<Int, Int>?>(null) }
    var showMenuForCell by remember { mutableStateOf<Pair<Int, Int>?>(null) }
    var showActionMenuSheet by remember { mutableStateOf(false) }
    var showOptionsMenu by remember { mutableStateOf(false) }
    var showZoomControlsMenu by remember { mutableStateOf(false) }
    var showColumnMenu by remember { mutableStateOf<Int?>(null) }
    var showRowMenu by remember { mutableStateOf<Int?>(null) }
    var showResizeColumnDialog by remember { mutableStateOf<Int?>(null) }
    var showResizeRowDialog by remember { mutableStateOf<Int?>(null) }
    var activeColResizeDrag by remember { mutableStateOf<Int?>(null) }
    var activeColResizeWidthDp by remember { mutableFloatStateOf(0f) }
    var activeRowResizeDrag by remember { mutableStateOf<Int?>(null) }
    var activeRowResizeHeightDp by remember { mutableFloatStateOf(0f) }
    var colorPickerState by remember { mutableStateOf<ColorPickerState?>(null) }
    var showQuickActionsCustomizer by remember { mutableStateOf(false) }
    var focusedOverflowMenuButtonId by remember { mutableStateOf<String?>(null) }
    var activeEditTextFieldUpdater by remember { mutableStateOf<((String) -> Unit)?>(null) }
    var quickActionToManage by remember { mutableStateOf<MenuItemData?>(null) }

    val initialTab = settings.lastActionMenuTab.coerceIn(0, ACTION_MENU_TABS.lastIndex)
    val pagerState = rememberPagerState(
        initialPage = initialTab,
        pageCount = { ACTION_MENU_TABS.size }
    )

    var showFindReplace by remember { mutableStateOf(false) }
    var findQuery by remember { mutableStateOf("") }
    var replaceQuery by remember { mutableStateOf("") }
    var matchCase by remember { mutableStateOf(false) }
    var currentMatchIndex by remember { mutableIntStateOf(0) }

    var showSheetsDialog by remember { mutableStateOf(false) }
    var sheetToRename by remember { mutableStateOf<Pair<Int, String>?>(null) }
    var sheetRenameInput by remember { mutableStateOf("") }
    var sheetToDelete by remember { mutableStateOf<Pair<Int, String>?>(null) }
    var showAddSheetDialog by remember { mutableStateOf(false) }
    var newSheetNameInput by remember { mutableStateOf("") }

    val matches = remember(findQuery, matchCase, refreshTrigger) {
        if (findQuery.isEmpty()) emptyList() else engine.findMatches(findQuery, matchCase)
    }

    LaunchedEffect(pagerState.currentPage) {
        viewModel.updateLastActionMenuTab(pagerState.currentPage)
    }

    LaunchedEffect(showColumnMenu) {
        showColumnMenu?.let { col ->
            pagerState.scrollToPage(TAB_INDEX_COLUMN)
            showActionMenuSheet = true
            viewModel.ttsManager.speak("Column actions menu opened for ${engine.getColumnName(col)}")
            showColumnMenu = null
        }
    }

    LaunchedEffect(showRowMenu) {
        showRowMenu?.let { row ->
            pagerState.scrollToPage(TAB_INDEX_ROW)
            showActionMenuSheet = true
            viewModel.ttsManager.speak("Row actions menu opened for row ${row + 1}")
            showRowMenu = null
        }
    }

    LaunchedEffect(showMenuForCell) {
        showMenuForCell?.let { cell ->
            pagerState.scrollToPage(TAB_INDEX_CELL)
            showActionMenuSheet = true
            viewModel.ttsManager.speak("Cell actions menu opened for cell ${engine.getColumnName(cell.second)}${cell.first + 1}")
            showMenuForCell = null
        }
    }

    LaunchedEffect(findQuery, matchCase) {
        if (findQuery.isNotEmpty()) {
            viewModel.ttsManager.speak(if (matches.isEmpty()) "No matches found" else "${matches.size} matches found")
        }
    }
    
    val currentFileUri by viewModel.currentFileUri.collectAsStateWithLifecycle()
    var showRenameDialog by remember { mutableStateOf(false) }
    var renameBaseName by remember { mutableStateOf("") }
    var renameExtension by remember { mutableStateOf("") }
    var showSaveConfirmDialog by remember { mutableStateOf(false) }
    var clearColConfirm by remember { mutableStateOf<Int?>(null) }
    var clearRowConfirm by remember { mutableStateOf<Int?>(null) }
    var deleteRowConfirm by remember { mutableStateOf<Int?>(null) }
    var showDeleteModeChooser by remember { mutableStateOf(false) }
    var showLanguagePicker by remember { mutableStateOf(false) }

    val isCsv = fileName.endsWith(".csv", ignoreCase = true)
    val exportMimeType = if (isCsv) "text/csv" else "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"

    val saveAsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument(exportMimeType)
    ) { destinationUri ->
        if (destinationUri != null) {
            viewModel.exportToUri(destinationUri) { success, message ->
                coroutineScope.launch {
                    snackbarHostState.showSnackbar(message)
                }
            }
        }
    }

    val speechLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val matches = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
            val spoken = matches?.firstOrNull()
            if (!spoken.isNullOrBlank()) {
                if (activeEditTextFieldUpdater != null) {
                    activeEditTextFieldUpdater?.invoke(spoken)
                } else {
                    viewModel.insertVoiceText(spoken, selectedCell, editingCell)
                }
            }
        }
    }

    val renameSpeechLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val matches = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
            val spoken = matches?.firstOrNull()
            if (!spoken.isNullOrBlank()) {
                val cleanSpoken = spoken.trim().replace(Regex("[/\\\\:*?\"<>|]"), "_")
                renameBaseName = cleanSpoken
            }
        }
    }

    // Pan offset state with smooth animation support
    val animPanOffset = remember { Animatable(Offset.Zero, Offset.VectorConverter) }
    val panChannel = remember { Channel<Offset>(Channel.CONFLATED) }
    LaunchedEffect(Unit) {
        for (pan in panChannel) {
            animPanOffset.snapTo(pan)
        }
    }
    var scrollJob by remember { mutableStateOf<Job?>(null) }

    // Pure image/PDF-like canvas scale-transform zoom
    var userZoom by remember { mutableFloatStateOf(1.0f) }
    val zoomPercent by remember { derivedStateOf { (userZoom * 100).roundToInt() } }
    val isZoomed by remember { derivedStateOf { zoomPercent != 100 } }
    var viewportSize by remember { mutableStateOf(IntSize.Zero) }
    var lastTapTimestamp by remember { mutableLongStateOf(0L) }
    var lastTapPosition by remember { mutableStateOf(Offset.Zero) }

    LaunchedEffect(currentFileUri, fileName) {
        selectedCell = Pair(0, 0)
        selectedColumn = null
        editingCell = null
        userZoom = 1.0f
        animPanOffset.snapTo(Offset.Zero)
    }
    
    val vibrator = remember {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                vibratorManager?.defaultVibrator ?: (context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator)
            } else {
                @Suppress("DEPRECATION")
                context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            }
        } catch (e: Exception) {
            null
        }
    }
    
    fun triggerHaptic() {
        if (settings.vibrateOnSelect && vibrator?.hasVibrator() == true) {
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    vibrator.vibrate(VibrationEffect.createOneShot(20, VibrationEffect.DEFAULT_AMPLITUDE))
                } else {
                    @Suppress("DEPRECATION")
                    vibrator.vibrate(20)
                }
            } catch (e: Exception) {
                // Ignore
            }
        }
    }

    val themeAccentColor = Color(settings.themeColor)
    val selectedCellColor = Color(settings.selectedCellColor)

    val textMeasurer = rememberTextMeasurer(cacheSize = 512)
    val textStyle = MaterialTheme.typography.bodyMedium.copy(
        color = MaterialTheme.colorScheme.onBackground,
        fontSize = 13.sp
    )
    val headerRowStyle = textStyle.copy(fontWeight = FontWeight.Bold, color = themeAccentColor)
    val headerStyle = MaterialTheme.typography.labelMedium.copy(
        color = MaterialTheme.colorScheme.onSurface,
        fontWeight = FontWeight.Bold,
        fontSize = 11.sp
    )
    val headerStyleNormal = remember(headerStyle) { headerStyle }
    val headerStyleSelected = remember(headerStyle, themeAccentColor) { headerStyle.copy(color = themeAccentColor) }

    val gridColor = if (settings.highContrastGrid) Color(0xFF888888) else Color(0xFF444444)
    val defaultHeaderBg = themeAccentColor.copy(alpha = 0.22f)
    val headerBg = if (engine.headerBgColor != null) Color(engine.headerBgColor!!) else defaultHeaderBg
    val highlightFill = selectedCellColor.copy(alpha = 0.22f)
    val defaultBannerBg = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.85f)

    val cellLayoutCache = remember(textStyle, headerRowStyle, headerStyleNormal, headerStyleSelected) {
        CellLayoutCache(textMeasurer)
    }

    LaunchedEffect(refreshTrigger) {
        cellLayoutCache.clear()
    }

    val rowCellHeightMeasurer: (Int, Int, String, Float) -> Float = remember(textMeasurer, textStyle, headerRowStyle, density) {
        { r: Int, c: Int, text: String, availableWidthPx: Float ->
            val pad = 5f * density
            val maxTextW = (availableWidthPx - 2 * pad).roundToInt().coerceAtLeast(1)
            val style = if (engine.isHeaderRow(r)) headerRowStyle else textStyle
            val res = textMeasurer.measure(
                text = text,
                style = style,
                constraints = Constraints(maxWidth = maxTextW)
            )
            res.size.height.toFloat() + 2 * pad
        }
    }

    // Ensure layout cache is updated for unscaled density and largeTouchMode only (never on zoom)
    LaunchedEffect(refreshTrigger, density, settings.largeTouchMode) {
        engine.updateLayoutIfNeeded(density, settings.largeTouchMode, rowCellHeightMeasurer)
    }

    fun clampPan(offset: Offset, zoom: Float): Offset {
        val viewW = if (viewportSize.width > 0) viewportSize.width.toFloat() else 1000f
        val viewH = if (viewportSize.height > 0) viewportSize.height.toFloat() else 1500f
        val headerW = if (settings.showRowNumbers) 44f * density else 0f
        val headerH = 32f * density
        val contentViewW = (viewW - headerW).coerceAtLeast(100f)
        val contentViewH = (viewH - headerH).coerceAtLeast(100f)
        engine.checkAutoExtend(offset.x, offset.y, contentViewW, contentViewH, zoom)
        val contentW = engine.totalWidthPx * zoom
        val contentH = engine.totalHeightPx * zoom
        val margin = 48f * density

        val minX: Float
        val maxX: Float
        if (contentW > contentViewW) {
            minX = contentViewW - contentW - margin
            maxX = margin
        } else {
            minX = -margin
            maxX = (contentViewW - contentW) + margin
        }

        val minY: Float
        val maxY: Float
        if (contentH > contentViewH) {
            minY = contentViewH - contentH - margin
            maxY = margin
        } else {
            minY = -margin
            maxY = (contentViewH - contentH) + margin
        }

        return Offset(
            x = offset.x.coerceIn(minOf(minX, maxX), maxOf(minX, maxX)),
            y = offset.y.coerceIn(minOf(minY, maxY), maxOf(minY, maxY))
        )
    }

    fun applyZoom(
        newZoom: Float,
        pivot: Offset = Offset(
            (if (settings.showRowNumbers) 44f * density else 0f) + ((if (viewportSize.width > 0) viewportSize.width.toFloat() else 1000f) - (if (settings.showRowNumbers) 44f * density else 0f)) / 2f,
            32f * density + ((if (viewportSize.height > 0) viewportSize.height.toFloat() else 1500f) - 32f * density) / 2f
        )
    ) {
        val clampedZoom = newZoom.coerceIn(MIN_ZOOM, MAX_ZOOM)
        if (userZoom == clampedZoom) return
        val oldZoom = userZoom
        userZoom = clampedZoom

        val headerW = if (settings.showRowNumbers) 44f * density else 0f
        val headerH = 32f * density
        val curPan = animPanOffset.value
        val pivotContent = Offset(pivot.x - headerW, pivot.y - headerH)
        val targetPan = pivotContent - (pivotContent - curPan) * (clampedZoom / oldZoom)
        val clampedPan = clampPan(targetPan, clampedZoom)

        scrollJob?.cancel()
        scrollJob = coroutineScope.launch {
            animPanOffset.animateTo(
                clampedPan,
                animationSpec = tween(durationMillis = 200, easing = FastOutSlowInEasing)
            )
        }
    }

    fun applyZoomChange(
        oldZoom: Float,
        newZoom: Float,
        pivotX: Float = (viewportSize.width / 2f).coerceAtLeast(0f),
        pivotY: Float = (viewportSize.height / 2f).coerceAtLeast(0f)
    ) {
        applyZoom(newZoom, Offset(pivotX, pivotY))
    }

    fun scrollToCell(targetR: Int, targetC: Int) {
        val headerW = if (settings.showRowNumbers) 44f * density else 0f
        val headerH = 32f * density
        val cellLeftUnscaled = engine.getColOffsetPx(targetC)
        val cellRightUnscaled = cellLeftUnscaled + engine.getColWidthPx(targetC)
        val cellTopUnscaled = engine.getRowOffsetPx(targetR)
        val cellBottomUnscaled = cellTopUnscaled + engine.getRowHeightPx(targetR)

        val viewW = if (viewportSize.width > 0) viewportSize.width.toFloat() else 1000f
        val viewH = if (viewportSize.height > 0) viewportSize.height.toFloat() else 1500f
        val contentViewW = (viewW - headerW).coerceAtLeast(100f)
        val contentViewH = (viewH - headerH).coerceAtLeast(100f)

        val curPan = animPanOffset.value
        val cellScreenLeft = headerW + curPan.x + cellLeftUnscaled * userZoom
        val cellScreenRight = headerW + curPan.x + cellRightUnscaled * userZoom
        val cellScreenTop = headerH + curPan.y + cellTopUnscaled * userZoom
        val cellScreenBottom = headerH + curPan.y + cellBottomUnscaled * userZoom

        // 40dp margin so cell lands comfortably in view without edge clipping
        val margin = 40f * density
        var targetPanX = curPan.x
        var targetPanY = curPan.y

        if (cellScreenLeft < headerW + margin) {
            targetPanX = margin - cellLeftUnscaled * userZoom
        } else if (cellScreenRight > viewW - margin) {
            targetPanX = contentViewW - margin - cellRightUnscaled * userZoom
        }

        if (cellScreenTop < headerH + margin) {
            targetPanY = margin - cellTopUnscaled * userZoom
        } else if (cellScreenBottom > viewH - margin) {
            targetPanY = contentViewH - margin - cellBottomUnscaled * userZoom
        }

        val clampedPan = clampPan(Offset(targetPanX, targetPanY), userZoom)

        scrollJob?.cancel()
        scrollJob = coroutineScope.launch {
            animPanOffset.animateTo(
                clampedPan,
                animationSpec = tween(durationMillis = 180, easing = FastOutSlowInEasing)
            )
        }
    }

    // Smooth navigation with clear landing feedback mapped to current scale & offset
    fun moveSelection(deltaRow: Int, deltaCol: Int) {
        selectedColumn = null
        val current = selectedCell ?: Pair(0, 0)
        val newR = (current.first + deltaRow).coerceIn(0, engine.maxRow - 1)
        var newC = (current.second + deltaCol).coerceIn(0, engine.maxCol - 1)
        val isBanner = engine.isBannerRow(newR)
        if (isBanner) {
            newC = 0
        }
        selectedCell = Pair(newR, newC)
        scrollToCell(newR, newC)
        viewModel.speakCell(newR, newC)
        triggerHaptic()
    }

    Scaffold(
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        topBar = {
            Column {
                TopAppBar(
                title = { 
                    Column(
                        modifier = Modifier
                            .clickable {
                                val dotIdx = fileName.lastIndexOf('.')
                                renameBaseName = if (dotIdx > 0) fileName.substring(0, dotIdx) else fileName
                                renameExtension = if (dotIdx > 0) fileName.substring(dotIdx) else ""
                                showRenameDialog = true
                            }
                            .testTag("spreadsheet_title_header")
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(fileName, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Spacer(Modifier.width(4.dp))
                            Icon(
                                Icons.Default.Edit,
                                contentDescription = "Rename spreadsheet",
                                modifier = Modifier.size(14.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        if (isZoomed) {
                            Text(
                                text = "Zoom: $zoomPercent%",
                                style = MaterialTheme.typography.labelSmall,
                                color = GreenPrimary
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    // Undo Button
                    IconButton(
                        onClick = { viewModel.undo() },
                        enabled = canUndo,
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("top_bar_undo_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Undo,
                            contentDescription = "Undo",
                            tint = if (canUndo) GreenPrimary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                        )
                    }

                    // Redo Button
                    IconButton(
                        onClick = { viewModel.redo() },
                        enabled = canRedo,
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("top_bar_redo_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Redo,
                            contentDescription = "Redo",
                            tint = if (canRedo) GreenPrimary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                        )
                    }

                    // Save document button
                    IconButton(
                        onClick = { showSaveConfirmDialog = true },
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("top_bar_save_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Save,
                            contentDescription = "Save Spreadsheet",
                            tint = GreenPrimary
                        )
                    }

                    Box {
                        IconButton(
                            onClick = { showOptionsMenu = true },
                            modifier = Modifier.testTag("three_dot_menu_button")
                        ) {
                            Icon(Icons.Default.MoreVert, contentDescription = "Options Menu")
                        }
                        
                        DropdownMenu(
                            expanded = showOptionsMenu,
                            onDismissRequest = {
                                showOptionsMenu = false
                                focusedOverflowMenuButtonId = null
                            },
                            modifier = Modifier.widthIn(min = 280.dp)
                        ) {
                            val handleOverflowClick: (String, String, () -> Unit) -> Unit = { itemId, announceText, onExecute ->
                                if (settings.overflowMenuTwoStepMode) {
                                    if (focusedOverflowMenuButtonId != itemId) {
                                        focusedOverflowMenuButtonId = itemId
                                        if (settings.vibrateOnSelect) {
                                            hapticFeedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                            triggerHaptic()
                                        }
                                        viewModel.ttsManager.speak(announceText)
                                    } else {
                                        focusedOverflowMenuButtonId = null
                                        if (settings.vibrateOnSelect) {
                                            triggerHaptic()
                                        }
                                        onExecute()
                                    }
                                } else {
                                    focusedOverflowMenuButtonId = null
                                    if (settings.vibrateOnSelect) {
                                        triggerHaptic()
                                    }
                                    onExecute()
                                }
                            }

                            // Quick Actions / Favorites Section
                            val curR = selectedCell?.first ?: 0
                            val curC = selectedCell?.second ?: 0
                            val topQuickActions = getQuickActionsList(curR, curC, engine, settings)
                            
                            val isAddHeaderFocused = focusedOverflowMenuButtonId == "menu_add_quick_action"
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 14.dp, vertical = 6.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        Icons.Default.Star,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp),
                                        tint = themeAccentColor
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    Text(
                                        "Quick Actions",
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = themeAccentColor
                                    )
                                }
                                IconButton(
                                    onClick = {
                                        handleOverflowClick("menu_add_quick_action", "Add or customize quick actions") {
                                            showOptionsMenu = false
                                            showQuickActionsCustomizer = true
                                        }
                                    },
                                    modifier = Modifier
                                        .size(24.dp)
                                        .background(
                                            if (isAddHeaderFocused) selectedCellColor.copy(alpha = 0.22f) else Color.Transparent,
                                            shape = RoundedCornerShape(4.dp)
                                        )
                                        .then(
                                            if (isAddHeaderFocused) Modifier.border(1.5.dp, selectedCellColor, RoundedCornerShape(4.dp))
                                            else Modifier
                                        )
                                        .testTag("menu_add_quick_action_button")
                                ) {
                                    Icon(
                                        Icons.Default.Add,
                                        contentDescription = "Add / Customize Quick Actions",
                                        modifier = Modifier.size(16.dp),
                                        tint = if (isAddHeaderFocused) selectedCellColor else themeAccentColor
                                    )
                                }
                            }

                            if (topQuickActions.isEmpty()) {
                                val isAddEmptyFocused = focusedOverflowMenuButtonId == "menu_quick_action_add_empty"
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            "+ Add Quick Actions",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = if (isAddEmptyFocused) selectedCellColor else themeAccentColor,
                                            fontWeight = if (isAddEmptyFocused) FontWeight.Bold else FontWeight.Normal
                                        )
                                    },
                                    onClick = {
                                        handleOverflowClick("menu_quick_action_add_empty", "Add Quick Actions") {
                                            showOptionsMenu = false
                                            showQuickActionsCustomizer = true
                                        }
                                    },
                                    modifier = Modifier
                                        .padding(horizontal = 4.dp, vertical = 2.dp)
                                        .background(
                                            if (isAddEmptyFocused) selectedCellColor.copy(alpha = 0.22f) else Color.Transparent,
                                            shape = RoundedCornerShape(6.dp)
                                        )
                                        .then(
                                            if (isAddEmptyFocused) Modifier.border(2.dp, selectedCellColor, RoundedCornerShape(6.dp))
                                            else Modifier
                                        )
                                        .testTag("menu_quick_action_add_empty")
                                )
                            } else {
                                topQuickActions.forEach { action ->
                                    val itemId = "menu_quick_action_${action.actionId}"
                                    val isFocused = focusedOverflowMenuButtonId == itemId
                                    DropdownMenuItem(
                                        text = {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Icon(
                                                    action.icon,
                                                    contentDescription = null,
                                                    modifier = Modifier.size(18.dp),
                                                    tint = if (isFocused) selectedCellColor else themeAccentColor
                                                )
                                                Spacer(Modifier.width(12.dp))
                                                Column {
                                                    Text(
                                                        action.title,
                                                        fontSize = 14.sp,
                                                        fontWeight = if (isFocused) FontWeight.Bold else FontWeight.Normal,
                                                        color = if (isFocused) selectedCellColor else MaterialTheme.colorScheme.onSurface
                                                    )
                                                    if (!action.subtitle.isNullOrBlank()) {
                                                        Text(
                                                            action.subtitle,
                                                            fontSize = 11.sp,
                                                            color = if (isFocused) selectedCellColor.copy(alpha = 0.8f) else MaterialTheme.colorScheme.onSurfaceVariant
                                                        )
                                                    }
                                                }
                                            }
                                        },
                                        onClick = {
                                            val announce = action.title + (if (!action.subtitle.isNullOrBlank()) ", ${action.subtitle}" else "")
                                            handleOverflowClick(itemId, announce) {
                                                showOptionsMenu = false
                                                executeSpreadsheetAction(
                                                    actionId = action.actionId,
                                                    r = curR,
                                                    c = curC,
                                                    engine = engine,
                                                    viewModel = viewModel,
                                                    settings = settings,
                                                    context = context,
                                                    selectedColumn = selectedColumn,
                                                    onDismiss = { showOptionsMenu = false },
                                                    onEditCell = { cellPair, initialFormula ->
                                                        editingCell = cellPair
                                                        if (initialFormula != null) {
                                                            engine.setCell(cellPair.first, cellPair.second, initialFormula)
                                                        }
                                                    },
                                                    onOpenColorPicker = { target, tab ->
                                                        colorPickerState = ColorPickerState(target, tab)
                                                    },
                                                    onOpenZoom = { showZoomControlsMenu = true },
                                                    onOpenFindReplace = { showFindReplace = true },
                                                    onConfirmClearCol = { col -> clearColConfirm = col },
                                                    onConfirmClearRow = { row -> clearRowConfirm = row },
                                                    onConfirmDeleteRow = { row -> deleteRowConfirm = row },
                                                    onOpenResizeColumn = { col -> showResizeColumnDialog = col },
                                                    onOpenResizeRow = { row -> showResizeRowDialog = row }
                                                )
                                            }
                                        },
                                        modifier = Modifier
                                            .padding(horizontal = 4.dp, vertical = 2.dp)
                                            .background(
                                                if (isFocused) selectedCellColor.copy(alpha = 0.22f) else Color.Transparent,
                                                shape = RoundedCornerShape(6.dp)
                                            )
                                            .then(
                                                if (isFocused) Modifier.border(2.dp, selectedCellColor, RoundedCornerShape(6.dp))
                                                else Modifier
                                            )
                                            .testTag("menu_quick_action_${action.actionId}")
                                    )
                                }
                            }

                            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                            val isSaveFocused = focusedOverflowMenuButtonId == "menu_save_document"
                            DropdownMenuItem(
                                text = {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(20.dp), tint = if (isSaveFocused) selectedCellColor else GreenPrimary)
                                        Spacer(Modifier.width(12.dp))
                                        Text(
                                            "Quick Save (Downloads)",
                                            fontWeight = if (isSaveFocused) FontWeight.Bold else FontWeight.SemiBold,
                                            color = if (isSaveFocused) selectedCellColor else MaterialTheme.colorScheme.onSurface
                                        )
                                    }
                                },
                                onClick = {
                                    handleOverflowClick("menu_save_document", "Quick Save to Downloads") {
                                        showOptionsMenu = false
                                        viewModel.saveDocument { success, pathOrError ->
                                            coroutineScope.launch {
                                                snackbarHostState.showSnackbar(
                                                    if (success) "Saved to device: $pathOrError" else "Save failed: $pathOrError"
                                                )
                                            }
                                        }
                                    }
                                },
                                modifier = Modifier
                                    .padding(horizontal = 4.dp, vertical = 2.dp)
                                    .background(
                                        if (isSaveFocused) selectedCellColor.copy(alpha = 0.22f) else Color.Transparent,
                                        shape = RoundedCornerShape(6.dp)
                                    )
                                    .then(
                                        if (isSaveFocused) Modifier.border(2.dp, selectedCellColor, RoundedCornerShape(6.dp))
                                        else Modifier
                                    )
                                    .testTag("menu_save_document")
                            )

                            val isSaveAsFocused = focusedOverflowMenuButtonId == "menu_save_as_document"
                            DropdownMenuItem(
                                text = {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Default.FileDownload, contentDescription = null, modifier = Modifier.size(20.dp), tint = if (isSaveAsFocused) selectedCellColor else themeAccentColor)
                                        Spacer(Modifier.width(12.dp))
                                        Text(
                                            "Save As... (Choose Location)",
                                            fontWeight = if (isSaveAsFocused) FontWeight.Bold else FontWeight.Normal,
                                            color = if (isSaveAsFocused) selectedCellColor else MaterialTheme.colorScheme.onSurface
                                        )
                                    }
                                },
                                onClick = {
                                    handleOverflowClick("menu_save_as_document", "Save As, choose location") {
                                        showOptionsMenu = false
                                        val suggestedName = if (fileName.endsWith(".xlsx", ignoreCase = true) || fileName.endsWith(".csv", ignoreCase = true)) fileName else "$fileName.xlsx"
                                        saveAsLauncher.launch(suggestedName)
                                    }
                                },
                                modifier = Modifier
                                    .padding(horizontal = 4.dp, vertical = 2.dp)
                                    .background(
                                        if (isSaveAsFocused) selectedCellColor.copy(alpha = 0.22f) else Color.Transparent,
                                        shape = RoundedCornerShape(6.dp)
                                    )
                                    .then(
                                        if (isSaveAsFocused) Modifier.border(2.dp, selectedCellColor, RoundedCornerShape(6.dp))
                                        else Modifier
                                    )
                                    .testTag("menu_save_as_document")
                            )

                            val isRenameFocused = focusedOverflowMenuButtonId == "menu_rename_document"
                            DropdownMenuItem(
                                text = {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(20.dp), tint = if (isRenameFocused) selectedCellColor else MaterialTheme.colorScheme.onSurfaceVariant)
                                        Spacer(Modifier.width(12.dp))
                                        Text(
                                            "Rename Document",
                                            fontWeight = if (isRenameFocused) FontWeight.Bold else FontWeight.Normal,
                                            color = if (isRenameFocused) selectedCellColor else MaterialTheme.colorScheme.onSurface
                                        )
                                    }
                                },
                                onClick = {
                                    handleOverflowClick("menu_rename_document", "Rename Document") {
                                        showOptionsMenu = false
                                        val dotIdx = fileName.lastIndexOf('.')
                                        renameBaseName = if (dotIdx > 0) fileName.substring(0, dotIdx) else fileName
                                        renameExtension = if (dotIdx > 0) fileName.substring(dotIdx) else ""
                                        showRenameDialog = true
                                    }
                                },
                                modifier = Modifier
                                    .padding(horizontal = 4.dp, vertical = 2.dp)
                                    .background(
                                        if (isRenameFocused) selectedCellColor.copy(alpha = 0.22f) else Color.Transparent,
                                        shape = RoundedCornerShape(6.dp)
                                    )
                                    .then(
                                        if (isRenameFocused) Modifier.border(2.dp, selectedCellColor, RoundedCornerShape(6.dp))
                                        else Modifier
                                    )
                                    .testTag("menu_rename_document")
                            )

                            val isNewSpreadsheetFocused = focusedOverflowMenuButtonId == "menu_new_spreadsheet"
                            DropdownMenuItem(
                                text = {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(20.dp), tint = if (isNewSpreadsheetFocused) selectedCellColor else themeAccentColor)
                                        Spacer(Modifier.width(12.dp))
                                        Text(
                                            "New Blank Spreadsheet",
                                            fontWeight = if (isNewSpreadsheetFocused) FontWeight.Bold else FontWeight.Normal,
                                            color = if (isNewSpreadsheetFocused) selectedCellColor else MaterialTheme.colorScheme.onSurface
                                        )
                                    }
                                },
                                onClick = {
                                    handleOverflowClick("menu_new_spreadsheet", "New Blank Spreadsheet") {
                                        showOptionsMenu = false
                                        viewModel.openNewSpreadsheet()
                                    }
                                },
                                modifier = Modifier
                                    .padding(horizontal = 4.dp, vertical = 2.dp)
                                    .background(
                                        if (isNewSpreadsheetFocused) selectedCellColor.copy(alpha = 0.22f) else Color.Transparent,
                                        shape = RoundedCornerShape(6.dp)
                                    )
                                    .then(
                                        if (isNewSpreadsheetFocused) Modifier.border(2.dp, selectedCellColor, RoundedCornerShape(6.dp))
                                        else Modifier
                                    )
                                    .testTag("menu_new_spreadsheet")
                            )

                            val isSheetsFocused = focusedOverflowMenuButtonId == "menu_sheets"
                            DropdownMenuItem(
                                text = {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(Icons.Default.Layers, contentDescription = null, modifier = Modifier.size(20.dp), tint = if (isSheetsFocused) selectedCellColor else themeAccentColor)
                                            Spacer(Modifier.width(12.dp))
                                            Text(
                                                "Sheets",
                                                fontWeight = if (isSheetsFocused) FontWeight.Bold else FontWeight.SemiBold,
                                                color = if (isSheetsFocused) selectedCellColor else MaterialTheme.colorScheme.onSurface
                                            )
                                        }
                                        Surface(
                                            shape = RoundedCornerShape(6.dp),
                                            color = if (isSheetsFocused) selectedCellColor.copy(alpha = 0.25f) else themeAccentColor.copy(alpha = 0.15f)
                                        ) {
                                            Text(
                                                text = "${engine.sheets.size}",
                                                color = if (isSheetsFocused) selectedCellColor else themeAccentColor,
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.Bold,
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                            )
                                        }
                                    }
                                },
                                onClick = {
                                    handleOverflowClick("menu_sheets", "Sheets, ${engine.sheets.size} sheets available") {
                                        showOptionsMenu = false
                                        showSheetsDialog = true
                                    }
                                },
                                modifier = Modifier
                                    .padding(horizontal = 4.dp, vertical = 2.dp)
                                    .background(
                                        if (isSheetsFocused) selectedCellColor.copy(alpha = 0.22f) else Color.Transparent,
                                        shape = RoundedCornerShape(6.dp)
                                    )
                                    .then(
                                        if (isSheetsFocused) Modifier.border(2.dp, selectedCellColor, RoundedCornerShape(6.dp))
                                        else Modifier
                                    )
                                    .testTag("menu_sheets")
                            )

                            val isFindReplaceFocused = focusedOverflowMenuButtonId == "menu_find_and_replace"
                            DropdownMenuItem(
                                text = {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(20.dp), tint = if (isFindReplaceFocused) selectedCellColor else themeAccentColor)
                                        Spacer(Modifier.width(12.dp))
                                        Text(
                                            "Find & Replace",
                                            fontWeight = if (isFindReplaceFocused) FontWeight.Bold else FontWeight.SemiBold,
                                            color = if (isFindReplaceFocused) selectedCellColor else MaterialTheme.colorScheme.onSurface
                                        )
                                    }
                                },
                                onClick = {
                                    handleOverflowClick("menu_find_and_replace", "Find and Replace") {
                                        showOptionsMenu = false
                                        showFindReplace = true
                                    }
                                },
                                modifier = Modifier
                                    .padding(horizontal = 4.dp, vertical = 2.dp)
                                    .background(
                                        if (isFindReplaceFocused) selectedCellColor.copy(alpha = 0.22f) else Color.Transparent,
                                        shape = RoundedCornerShape(6.dp)
                                    )
                                    .then(
                                        if (isFindReplaceFocused) Modifier.border(2.dp, selectedCellColor, RoundedCornerShape(6.dp))
                                        else Modifier
                                    )
                                    .testTag("menu_find_and_replace")
                            )

                            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                            
                            val isZoomFocused = focusedOverflowMenuButtonId == "menu_zoom_controls"
                            DropdownMenuItem(
                                text = {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            "Zoom Controls",
                                            fontWeight = if (isZoomFocused) FontWeight.Bold else FontWeight.SemiBold,
                                            color = if (isZoomFocused) selectedCellColor else MaterialTheme.colorScheme.onSurface
                                        )
                                        Surface(
                                            shape = RoundedCornerShape(6.dp),
                                            color = if (isZoomFocused) selectedCellColor.copy(alpha = 0.25f) else themeAccentColor.copy(alpha = 0.15f)
                                        ) {
                                            Text(
                                                text = "${(userZoom * 100).roundToInt()}%",
                                                color = if (isZoomFocused) selectedCellColor else themeAccentColor,
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.Bold,
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                            )
                                        }
                                    }
                                },
                                leadingIcon = {
                                    Icon(
                                        Icons.Default.ZoomIn,
                                        contentDescription = null,
                                        modifier = Modifier.size(20.dp),
                                        tint = if (isZoomFocused) selectedCellColor else themeAccentColor
                                    )
                                },
                                onClick = {
                                    handleOverflowClick("menu_zoom_controls", "Zoom Controls, ${(userZoom * 100).roundToInt()} percent") {
                                        showOptionsMenu = false
                                        showZoomControlsMenu = true
                                    }
                                },
                                modifier = Modifier
                                    .padding(horizontal = 4.dp, vertical = 2.dp)
                                    .background(
                                        if (isZoomFocused) selectedCellColor.copy(alpha = 0.22f) else Color.Transparent,
                                        shape = RoundedCornerShape(6.dp)
                                    )
                                    .then(
                                        if (isZoomFocused) Modifier.border(2.dp, selectedCellColor, RoundedCornerShape(6.dp))
                                        else Modifier
                                    )
                                    .testTag("menu_zoom_controls")
                            )

                            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                            // 1. Column Header Announcement Order
                            val isColOrderFocused = focusedOverflowMenuButtonId == "menu_toggle_column_first"
                            DropdownMenuItem(
                                text = {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                                            Text(
                                                "Announce Column Name First",
                                                fontWeight = if (isColOrderFocused) FontWeight.Bold else FontWeight.SemiBold,
                                                fontSize = 14.sp,
                                                color = if (isColOrderFocused) selectedCellColor else MaterialTheme.colorScheme.onSurface
                                            )
                                            Text(
                                                text = if (settings.announceColumnFirst) "Reads column header, then cell" else "Reads cell, then column header",
                                                fontSize = 12.sp,
                                                color = if (isColOrderFocused) selectedCellColor.copy(alpha = 0.8f) else MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                        Switch(
                                            checked = settings.announceColumnFirst,
                                            onCheckedChange = {
                                                handleOverflowClick("menu_toggle_column_first", "Announce Column Name First, currently ${if (settings.announceColumnFirst) "enabled" else "disabled"}") {
                                                    viewModel.toggleAnnounceColumnFirst()
                                                }
                                            }
                                        )
                                    }
                                },
                                onClick = {
                                    handleOverflowClick("menu_toggle_column_first", "Announce Column Name First, currently ${if (settings.announceColumnFirst) "enabled" else "disabled"}") {
                                        viewModel.toggleAnnounceColumnFirst()
                                    }
                                },
                                modifier = Modifier
                                    .padding(horizontal = 4.dp, vertical = 2.dp)
                                    .background(
                                        if (isColOrderFocused) selectedCellColor.copy(alpha = 0.22f) else Color.Transparent,
                                        shape = RoundedCornerShape(6.dp)
                                    )
                                    .then(
                                        if (isColOrderFocused) Modifier.border(2.dp, selectedCellColor, RoundedCornerShape(6.dp))
                                        else Modifier
                                    )
                                    .testTag("menu_toggle_column_first")
                            )

                            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                            // 2. Show / Hide Left Side Numbers
                            val isRowNumbersFocused = focusedOverflowMenuButtonId == "menu_toggle_row_numbers"
                            DropdownMenuItem(
                                text = {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                                            Text(
                                                "Show Left Side Numbers",
                                                fontWeight = if (isRowNumbersFocused) FontWeight.Bold else FontWeight.SemiBold,
                                                fontSize = 14.sp,
                                                color = if (isRowNumbersFocused) selectedCellColor else MaterialTheme.colorScheme.onSurface
                                            )
                                            Text(
                                                text = if (settings.showRowNumbers) "Row numbers (1, 2, 3...) shown" else "Row numbers hidden",
                                                fontSize = 12.sp,
                                                color = if (isRowNumbersFocused) selectedCellColor.copy(alpha = 0.8f) else MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                        Switch(
                                            checked = settings.showRowNumbers,
                                            onCheckedChange = {
                                                handleOverflowClick("menu_toggle_row_numbers", "Show Left Side Numbers, currently ${if (settings.showRowNumbers) "enabled" else "disabled"}") {
                                                    viewModel.toggleShowRowNumbers()
                                                }
                                            }
                                        )
                                    }
                                },
                                onClick = {
                                    handleOverflowClick("menu_toggle_row_numbers", "Show Left Side Numbers, currently ${if (settings.showRowNumbers) "enabled" else "disabled"}") {
                                        viewModel.toggleShowRowNumbers()
                                    }
                                },
                                modifier = Modifier
                                    .padding(horizontal = 4.dp, vertical = 2.dp)
                                    .background(
                                        if (isRowNumbersFocused) selectedCellColor.copy(alpha = 0.22f) else Color.Transparent,
                                        shape = RoundedCornerShape(6.dp)
                                    )
                                    .then(
                                        if (isRowNumbersFocused) Modifier.border(2.dp, selectedCellColor, RoundedCornerShape(6.dp))
                                        else Modifier
                                    )
                                    .testTag("menu_toggle_row_numbers")
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )

            if (showFindReplace) {
                FindAndReplaceBar(
                    query = findQuery,
                    onQueryChange = {
                        findQuery = it
                        currentMatchIndex = 0
                    },
                    replaceText = replaceQuery,
                    onReplaceTextChange = { replaceQuery = it },
                    matchCase = matchCase,
                    onMatchCaseToggle = {
                        matchCase = !matchCase
                        currentMatchIndex = 0
                    },
                    matchCount = matches.size,
                    currentMatchIndex = currentMatchIndex,
                    onPrevMatch = {
                        if (matches.isNotEmpty()) {
                            currentMatchIndex = if (currentMatchIndex - 1 < 0) matches.size - 1 else currentMatchIndex - 1
                            val (mr, mc) = matches[currentMatchIndex]
                            selectedCell = Pair(mr, mc)
                            scrollToCell(mr, mc)
                            viewModel.ttsManager.speak("Match ${currentMatchIndex + 1} of ${matches.size}: cell ${engine.getColumnName(mc)}${mr + 1}, ${engine.getCellValue(mr, mc)}")
                        }
                    },
                    onNextMatch = {
                        if (matches.isNotEmpty()) {
                            currentMatchIndex = (currentMatchIndex + 1) % matches.size
                            val (mr, mc) = matches[currentMatchIndex]
                            selectedCell = Pair(mr, mc)
                            scrollToCell(mr, mc)
                            viewModel.ttsManager.speak("Match ${currentMatchIndex + 1} of ${matches.size}: cell ${engine.getColumnName(mc)}${mr + 1}, ${engine.getCellValue(mr, mc)}")
                        }
                    },
                    onReplace = {
                        if (matches.isNotEmpty() && findQuery.isNotEmpty()) {
                            val currentMatch = matches.getOrNull(currentMatchIndex)
                            if (currentMatch != null) {
                                viewModel.replaceSingleMatch(currentMatch.first, currentMatch.second, findQuery, replaceQuery, matchCase)
                            }
                        }
                    },
                    onReplaceAll = {
                        if (findQuery.isNotEmpty()) {
                            viewModel.findAndReplace(findQuery, replaceQuery, matchCase)
                        }
                    },
                    onClose = {
                        showFindReplace = false
                        findQuery = ""
                        replaceQuery = ""
                    }
                )
            }
        }
    },
        bottomBar = {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("bottom_control_panel"),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 6.dp,
                shadowElevation = 8.dp
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                ) {
                    val curRow = selectedCell?.first ?: 0
                    val isBanner = engine.isBannerRow(curRow)
                    val curCol = if (isBanner) 0 else (selectedCell?.second ?: 0)
                    val cellLetter = engine.getColumnName(curCol)
                    val cellHeader = if (isBanner) "Banner Row ${curRow + 1}" else engine.getColumnHeaderName(curCol, curRow)
                    val cellCoord = if (isBanner) "Banner ${curRow + 1}" else "$cellLetter${curRow + 1}"
                    val cellVal = engine.getCellValue(curRow, curCol)
                    
                    // Top Row: Prominent Active Cell Card (Click to Edit) & Actions (Copy, Paste, Delete, Speak)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            tonalElevation = 2.dp,
                            modifier = Modifier
                                .weight(1f)
                                .padding(end = 8.dp)
                                .testTag("selected_cell_card")
                                .clickable(
                                    onClickLabel = if (isBanner) "Edit banner row ${curRow + 1}" else "Edit cell $cellCoord"
                                ) {
                                    editingCell = Pair(curRow, curCol)
                                }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = themeAccentColor
                                ) {
                                    Text(
                                        text = cellCoord,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White,
                                        fontSize = 13.sp,
                                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    if (curRow > 0 && cellHeader.isNotEmpty() && !cellHeader.startsWith("Column ") && !isBanner) {
                                        Text(
                                            text = cellHeader,
                                            fontWeight = FontWeight.SemiBold,
                                            fontSize = 11.sp,
                                            color = themeAccentColor,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    } else if (isBanner) {
                                        Text(
                                            text = "Full-width Banner",
                                            fontWeight = FontWeight.SemiBold,
                                            fontSize = 11.sp,
                                            color = themeAccentColor,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                    Text(
                                        text = if (cellVal.isEmpty()) (if (isBanner) "(empty banner - tap to edit)" else "(empty cell - tap to edit)") else cellVal,
                                        fontWeight = FontWeight.Medium,
                                        color = if (cellVal.isEmpty()) MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f) else MaterialTheme.colorScheme.onSurface,
                                        fontSize = 12.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                Spacer(modifier = Modifier.width(4.dp))
                                Icon(
                                    imageVector = Icons.Default.Edit,
                                    contentDescription = null,
                                    modifier = Modifier.size(14.dp),
                                    tint = themeAccentColor.copy(alpha = 0.6f)
                                )
                            }
                        }
                        
                        Row(
                            modifier = Modifier.horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            FilledTonalIconButton(
                                onClick = {
                                    if (selectedColumn != null) {
                                        viewModel.copyColumn(context, selectedColumn!!)
                                    } else {
                                        viewModel.copyCell(context, curRow, curCol)
                                    }
                                },
                                modifier = Modifier.size(38.dp).testTag("action_copy")
                            ) {
                                Icon(Icons.Default.ContentCopy, contentDescription = "Copy", modifier = Modifier.size(18.dp))
                            }
                            FilledTonalIconButton(
                                onClick = {
                                    if (selectedColumn != null) {
                                        viewModel.pasteColumn(context, selectedColumn!!, startRow = 0)
                                    } else {
                                        viewModel.pasteCell(context, curRow, curCol)
                                    }
                                },
                                modifier = Modifier.size(38.dp).testTag("action_paste")
                            ) {
                                Icon(Icons.Default.ContentPaste, contentDescription = "Paste", modifier = Modifier.size(18.dp))
                            }
                            val deleteMode = settings.deleteMode
                            val deleteIcon = when (deleteMode) {
                                DeleteMode.CLEAR_TEXT -> Icons.Default.Delete
                                DeleteMode.CLEAR_FORMATTING -> Icons.Default.Clear
                                DeleteMode.CLEAR_ROW -> Icons.Default.DeleteSweep
                                DeleteMode.CLEAR_COLUMN -> Icons.Default.DeleteOutline
                            }
                            val deleteDesc = when (deleteMode) {
                                DeleteMode.CLEAR_TEXT -> "Clear Text"
                                DeleteMode.CLEAR_FORMATTING -> "Clear Formatting"
                                DeleteMode.CLEAR_ROW -> "Clear Row"
                                DeleteMode.CLEAR_COLUMN -> "Clear Column"
                            }
                            Surface(
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.secondaryContainer,
                                modifier = Modifier
                                    .size(38.dp)
                                    .combinedClickable(
                                        onClick = {
                                            when (deleteMode) {
                                                DeleteMode.CLEAR_TEXT -> viewModel.clearCellText(curRow, curCol)
                                                DeleteMode.CLEAR_FORMATTING -> viewModel.clearCellFormatting(curRow, curCol)
                                                DeleteMode.CLEAR_ROW -> clearRowConfirm = curRow
                                                DeleteMode.CLEAR_COLUMN -> clearColConfirm = curCol
                                            }
                                        },
                                        onLongClick = { showDeleteModeChooser = true }
                                    )
                                    .testTag("action_delete")
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(deleteIcon, contentDescription = deleteDesc, modifier = Modifier.size(18.dp))
                                }
                            }
                            Surface(
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.secondaryContainer,
                                modifier = Modifier
                                    .size(38.dp)
                                    .combinedClickable(
                                        onClick = {
                                            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                                                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                                                val lang = settings.voiceTypingLanguage.takeIf { it.isNotBlank() } ?: Locale.getDefault().toString()
                                                putExtra(RecognizerIntent.EXTRA_LANGUAGE, lang)
                                                putExtra(RecognizerIntent.EXTRA_PROMPT, "Speak into spreadsheet...")
                                            }
                                            try {
                                                speechLauncher.launch(intent)
                                            } catch (_: Exception) {
                                                viewModel.ttsManager.speak("Voice typing not available")
                                            }
                                        },
                                        onLongClick = { showLanguagePicker = true }
                                    )
                                    .testTag("action_mic")
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(Icons.Default.Mic, contentDescription = "Voice Typing (Long press to change language)", modifier = Modifier.size(18.dp))
                                }
                            }
                            FilledTonalIconButton(
                                onClick = { viewModel.speakCell(curRow, curCol) },
                                modifier = Modifier.size(38.dp).testTag("action_speak")
                            ) {
                                Icon(Icons.AutoMirrored.Filled.VolumeUp, contentDescription = "Speak Cell", modifier = Modifier.size(18.dp))
                            }
                            FilledTonalIconButton(
                                onClick = {
                                    val targetTab = settings.lastActionMenuTab.coerceIn(0, ACTION_MENU_TABS.lastIndex)
                                    coroutineScope.launch {
                                        pagerState.scrollToPage(targetTab)
                                    }
                                    showActionMenuSheet = true
                                    triggerHaptic()
                                    viewModel.ttsManager.speak("${ACTION_MENU_TABS[targetTab]} actions menu opened")
                                },
                                modifier = Modifier.size(38.dp).testTag("action_menu")
                            ) {
                                Icon(Icons.Default.MoreHoriz, contentDescription = "Actions Menu", modifier = Modifier.size(18.dp))
                            }
                        }
                    }

                    // Bottom Row: Navigation 4 Buttons (Left, Up, Down, Right) with smooth scrolling & clear landing
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        FilledTonalButton(
                            onClick = { moveSelection(deltaRow = 0, deltaCol = -1) },
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp),
                            colors = ButtonDefaults.filledTonalButtonColors(
                                containerColor = Color(0xFF2A4D69),
                                contentColor = Color(0xFFE2E8F0)
                            ),
                            modifier = Modifier.weight(1f).height(44.dp).testTag("nav_left")
                        ) {
                            Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "Left", modifier = Modifier.size(22.dp))
                            Spacer(Modifier.width(2.dp))
                            Text("Left", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                        }

                        FilledTonalButton(
                            onClick = { moveSelection(deltaRow = -1, deltaCol = 0) },
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp),
                            colors = ButtonDefaults.filledTonalButtonColors(
                                containerColor = Color(0xFF1E3A5F),
                                contentColor = Color(0xFFE2E8F0)
                            ),
                            modifier = Modifier.weight(1f).height(44.dp).testTag("nav_up")
                        ) {
                            Icon(Icons.Default.KeyboardArrowUp, contentDescription = "Up", modifier = Modifier.size(22.dp))
                            Spacer(Modifier.width(2.dp))
                            Text("Up", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                        }

                        FilledTonalButton(
                            onClick = { moveSelection(deltaRow = 1, deltaCol = 0) },
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp),
                            colors = ButtonDefaults.filledTonalButtonColors(
                                containerColor = Color(0xFF1E3A5F),
                                contentColor = Color(0xFFE2E8F0)
                            ),
                            modifier = Modifier.weight(1f).height(44.dp).testTag("nav_down")
                        ) {
                            Icon(Icons.Default.KeyboardArrowDown, contentDescription = "Down", modifier = Modifier.size(22.dp))
                            Spacer(Modifier.width(2.dp))
                            Text("Down", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                        }

                        FilledTonalButton(
                            onClick = { moveSelection(deltaRow = 0, deltaCol = 1) },
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp),
                            colors = ButtonDefaults.filledTonalButtonColors(
                                containerColor = Color(0xFF2A4D69),
                                contentColor = Color(0xFFE2E8F0)
                            ),
                            modifier = Modifier.weight(1f).height(44.dp).testTag("nav_right")
                        ) {
                            Text("Right", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                            Spacer(Modifier.width(2.dp))
                            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "Right", modifier = Modifier.size(22.dp))
                        }
                    }
                }
            }
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .onSizeChanged { viewportSize = it }
        ) {
            val showRowNumbers = settings.showRowNumbers

            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(showRowNumbers, density) {
                        awaitEachGesture {
                            val down = awaitFirstDown(requireUnconsumed = false)
                            val downTime = System.currentTimeMillis()
                            var startPos = down.position
                            var lastPos = startPos
                            val touchSlop = viewConfiguration.touchSlop
                            var isMultiTouch = false
                            var isDragging = false
                            var longPressTriggered = false
                            var previousPointerCount = 1

                            val headerW = if (showRowNumbers) 44f * density else 0f
                            val headerH = 32f * density
                            val headerTouchH = headerH + 10f * density
                            val curPan = animPanOffset.value

                            var grabbedCol: Int? = null
                            var grabbedColInitW = 0f
                            var grabbedRow: Int? = null
                            var grabbedRowInitH = 0f

                            // Detect touch on column resize handle (touch target at least 48dp: ±24dp around boundary)
                            if (startPos.y <= headerTouchH && startPos.x >= headerW) {
                                val visibleLeftUnscaled = -curPan.x / userZoom
                                val startCol = engine.getColAt(visibleLeftUnscaled).coerceIn(0, engine.maxCol - 1)
                                for (c in startCol until minOf(engine.maxCol, startCol + 25)) {
                                    val colLeft = headerW + curPan.x + engine.getColOffsetPx(c) * userZoom
                                    val colWidth = engine.getColWidthPx(c) * userZoom
                                    val colRight = colLeft + colWidth
                                    if (kotlin.math.abs(startPos.x - colRight) <= 24f * density) {
                                        grabbedCol = c
                                        grabbedColInitW = engine.getColWidthDp(c)
                                        activeColResizeDrag = c
                                        activeColResizeWidthDp = grabbedColInitW
                                        break
                                    }
                                }
                            } else if (showRowNumbers && startPos.x <= headerW + 10f * density && startPos.y >= headerH) {
                                val visibleTopUnscaled = -curPan.y / userZoom
                                val startRow = engine.getRowAt(visibleTopUnscaled).coerceIn(0, engine.maxRow - 1)
                                for (r in startRow until minOf(engine.maxRow, startRow + 35)) {
                                    val rowTop = headerH + curPan.y + engine.getRowOffsetPx(r) * userZoom
                                    val rowHeight = engine.getRowHeightPx(r) * userZoom
                                    val rowBottom = rowTop + rowHeight
                                    if (kotlin.math.abs(startPos.y - rowBottom) <= 24f * density) {
                                        grabbedRow = r
                                        grabbedRowInitH = engine.getRowHeightDp(r)
                                        activeRowResizeDrag = r
                                        activeRowResizeHeightDp = grabbedRowInitH
                                        break
                                    }
                                }
                            }

                            var longPressJob: Job? = if (grabbedCol == null && grabbedRow == null) {
                                coroutineScope.launch {
                                    delay(viewConfiguration.longPressTimeoutMillis)
                                    if (!isDragging && !isMultiTouch && !longPressTriggered) {
                                        longPressTriggered = true
                                        val screenX = startPos.x
                                        val screenY = startPos.y

                                        if (screenX >= headerW && screenY <= headerTouchH) {
                                            val gridX = (screenX - headerW - curPan.x) / userZoom
                                            val c = engine.getColAt(gridX).coerceIn(0, engine.maxCol - 1)
                                            selectedCell = Pair(0, c)
                                            showColumnMenu = c
                                            triggerHaptic()
                                        } else if (screenX >= headerW && screenY > headerTouchH) {
                                            val gridX = (screenX - headerW - curPan.x) / userZoom
                                            val gridY = (screenY - headerH - curPan.y) / userZoom
                                            val r = engine.getRowAt(gridY).coerceIn(0, engine.maxRow - 1)
                                            val c = engine.getColAt(gridX).coerceIn(0, engine.maxCol - 1)
                                            selectedCell = Pair(r, c)
                                            showMenuForCell = Pair(r, c)
                                            triggerHaptic()
                                        } else if (showRowNumbers && screenX < headerW && screenY > headerTouchH) {
                                            val gridY = (screenY - headerH - curPan.y) / userZoom
                                            val r = engine.getRowAt(gridY).coerceIn(0, engine.maxRow - 1)
                                            val currentC = selectedCell?.second ?: 0
                                            selectedCell = Pair(r, currentC)
                                            showRowMenu = r
                                            triggerHaptic()
                                        }
                                    }
                                }
                            } else null

                            // Light smoothing state for centroid & zoom
                            var smoothedCentroid: Offset? = null
                            var smoothedZoom = 1.0f

                            while (true) {
                                val event = awaitPointerEvent(PointerEventPass.Main)
                                val activePointers = event.changes.filter { it.pressed }
                                val count = activePointers.size

                                if (count >= 2) {
                                    longPressJob?.cancel()
                                    isMultiTouch = true
                                    scrollJob?.cancel()
                                    if (grabbedCol != null) {
                                        activeColResizeDrag = null
                                        grabbedCol = null
                                    }
                                    if (grabbedRow != null) {
                                        activeRowResizeDrag = null
                                        grabbedRow = null
                                    }

                                    val rawCentroid = event.calculateCentroid(useCurrent = true)
                                    val rawZoom = event.calculateZoom()
                                    val rawPan = event.calculatePan()

                                    // If just entered multi-touch, initialize smoothed state without jumping
                                    if (previousPointerCount < 2 || smoothedCentroid == null) {
                                        smoothedCentroid = rawCentroid
                                        smoothedZoom = 1.0f
                                    } else {
                                        // Exponential moving average smoothing for centroid and zoom factor
                                        val centroidAlpha = 0.65f
                                        smoothedCentroid = Offset(
                                            x = smoothedCentroid.x * (1f - centroidAlpha) + rawCentroid.x * centroidAlpha,
                                            y = smoothedCentroid.y * (1f - centroidAlpha) + rawCentroid.y * centroidAlpha
                                        )

                                        val zoomAlpha = 0.60f
                                        smoothedZoom = smoothedZoom * (1f - zoomAlpha) + rawZoom * zoomAlpha
                                    }

                                    val oldZoom = userZoom
                                    // Deadzone check: ignore micro-jitter when zoom factor is extremely close to 1.0
                                    val newZoom = if (kotlin.math.abs(smoothedZoom - 1.0f) > 0.002f) {
                                        (oldZoom * smoothedZoom).coerceIn(MIN_ZOOM, MAX_ZOOM)
                                    } else {
                                        oldZoom
                                    }
                                    userZoom = newZoom

                                    val headerW = if (showRowNumbers) 44f * density else 0f
                                    val headerH = 32f * density
                                    val curPan = animPanOffset.value
                                    val pivotContent = (smoothedCentroid ?: rawCentroid) - Offset(headerW, headerH)
                                    val targetPan = pivotContent - (pivotContent - curPan) * (newZoom / oldZoom) + rawPan
                                    val clampedPan = clampPan(targetPan, newZoom)

                                    panChannel.trySend(clampedPan)
                                    event.changes.forEach { it.consume() }
                                    previousPointerCount = count
                                } else if (count == 1) {
                                    val pointer = activePointers[0]

                                    if (grabbedCol != null) {
                                        longPressJob?.cancel()
                                        val deltaPx = pointer.position.x - startPos.x
                                        val deltaDp = deltaPx / (density * userZoom)
                                        val newW = (grabbedColInitW + deltaDp).coerceIn(20f, 500f)
                                        activeColResizeWidthDp = newW
                                        pointer.consume()
                                    } else if (grabbedRow != null) {
                                        longPressJob?.cancel()
                                        val deltaPx = pointer.position.y - startPos.y
                                        val deltaDp = deltaPx / (density * userZoom)
                                        val newH = (grabbedRowInitH + deltaDp).coerceIn(20f, 500f)
                                        activeRowResizeHeightDp = newH
                                        pointer.consume()
                                    } else if (previousPointerCount >= 2) {
                                        longPressJob?.cancel()
                                        lastPos = pointer.position
                                        startPos = pointer.position
                                        previousPointerCount = 1
                                        pointer.consume()
                                        continue
                                    }

                                    if (isDragging) {
                                        scrollJob?.cancel()
                                        val drag = pointer.position - lastPos
                                        lastPos = pointer.position
                                        val targetPan = animPanOffset.value + drag
                                        val clampedPan = clampPan(targetPan, userZoom)
                                        panChannel.trySend(clampedPan)
                                        pointer.consume()
                                    } else {
                                        val dist = (pointer.position - startPos).getDistance()
                                        val elapsed = System.currentTimeMillis() - downTime
                                        val headerW = if (showRowNumbers) 44f * density else 0f
                                        val headerH = 32f * density
                                        val headerTouchH = headerH + 10f * density
                                        val isHeaderStart = startPos.x >= headerW && startPos.y <= headerTouchH
                                        val isRowStart = showRowNumbers && startPos.x < headerW && startPos.y > headerH

                                        // For touches starting in the column or row header, protect against accidental cancellation
                                        // due to natural thumb tremor. Only start drag if clear intentional swipe is detected.
                                        val shouldStartDrag = if (isHeaderStart) {
                                            val deltaX = kotlin.math.abs(pointer.position.x - startPos.x)
                                            deltaX > touchSlop * 2.5f
                                        } else if (isRowStart) {
                                            val deltaY = kotlin.math.abs(pointer.position.y - startPos.y)
                                            deltaY > touchSlop * 2.5f
                                        } else {
                                            (dist > touchSlop && elapsed >= 60L) || dist > touchSlop * 2.2f
                                        }

                                        if (shouldStartDrag) {
                                            longPressJob?.cancel()
                                            isDragging = true
                                            scrollJob?.cancel()
                                            lastPos = pointer.position
                                            pointer.consume()
                                        } else if (!isMultiTouch && !longPressTriggered) {
                                            if (elapsed >= viewConfiguration.longPressTimeoutMillis) {
                                                longPressTriggered = true
                                                val screenX = startPos.x
                                                val screenY = startPos.y
                                                val curPan = animPanOffset.value

                                                if (screenX >= headerW && screenY <= headerTouchH) {
                                                    val gridX = (screenX - headerW - curPan.x) / userZoom
                                                    val c = engine.getColAt(gridX).coerceIn(0, engine.maxCol - 1)
                                                    selectedCell = Pair(0, c)
                                                    showColumnMenu = c
                                                    triggerHaptic()
                                                } else if (screenX >= headerW && screenY > headerTouchH) {
                                                    val gridX = (screenX - headerW - curPan.x) / userZoom
                                                    val gridY = (screenY - headerH - curPan.y) / userZoom
                                                    val r = engine.getRowAt(gridY).coerceIn(0, engine.maxRow - 1)
                                                    val c = engine.getColAt(gridX).coerceIn(0, engine.maxCol - 1)
                                                    selectedCell = Pair(r, c)
                                                    showMenuForCell = Pair(r, c)
                                                    triggerHaptic()
                                                } else if (showRowNumbers && screenX < headerW && screenY > headerTouchH) {
                                                    val gridY = (screenY - headerH - curPan.y) / userZoom
                                                    val r = engine.getRowAt(gridY).coerceIn(0, engine.maxRow - 1)
                                                    val currentC = selectedCell?.second ?: 0
                                                    selectedCell = Pair(r, currentC)
                                                    showRowMenu = r
                                                    triggerHaptic()
                                                }
                                                pointer.consume()
                                            }
                                        }
                                    }
                                    previousPointerCount = 1
                                } else {
                                    // All fingers lifted
                                    longPressJob?.cancel()
                                    val now = System.currentTimeMillis()
                                    val elapsed = now - downTime
                                    val wasLongPress = elapsed >= viewConfiguration.longPressTimeoutMillis

                                    if (grabbedCol != null) {
                                        viewModel.setColumnWidth(grabbedCol!!, activeColResizeWidthDp)
                                        activeColResizeDrag = null
                                        grabbedCol = null
                                    } else if (grabbedRow != null) {
                                        viewModel.setRowHeight(grabbedRow!!, activeRowResizeHeightDp)
                                        activeRowResizeDrag = null
                                        grabbedRow = null
                                    } else if (!isMultiTouch && !isDragging) {
                                        val headerW = if (showRowNumbers) 44f * density else 0f
                                        val headerH = 32f * density
                                        val headerTouchH = headerH + 10f * density
                                        val screenX = startPos.x
                                        val screenY = startPos.y
                                        val curPan = animPanOffset.value

                                        if (!longPressTriggered && wasLongPress) {
                                            longPressTriggered = true
                                            if (screenX >= headerW && screenY <= headerTouchH) {
                                                val gridX = (screenX - headerW - curPan.x) / userZoom
                                                val c = engine.getColAt(gridX).coerceIn(0, engine.maxCol - 1)
                                                selectedCell = Pair(0, c)
                                                showColumnMenu = c
                                                triggerHaptic()
                                            } else if (screenX >= headerW && screenY > headerTouchH) {
                                                val gridX = (screenX - headerW - curPan.x) / userZoom
                                                val gridY = (screenY - headerH - curPan.y) / userZoom
                                                val r = engine.getRowAt(gridY).coerceIn(0, engine.maxRow - 1)
                                                val c = engine.getColAt(gridX).coerceIn(0, engine.maxCol - 1)
                                                selectedCell = Pair(r, c)
                                                showMenuForCell = Pair(r, c)
                                                triggerHaptic()
                                            } else if (showRowNumbers && screenX < headerW && screenY > headerTouchH) {
                                                val gridY = (screenY - headerH - curPan.y) / userZoom
                                                val r = engine.getRowAt(gridY).coerceIn(0, engine.maxRow - 1)
                                                val currentC = selectedCell?.second ?: 0
                                                selectedCell = Pair(r, currentC)
                                                showRowMenu = r
                                                triggerHaptic()
                                            }
                                        } else if (!longPressTriggered) {
                                            val isDoubleTap = (now - lastTapTimestamp < 320) &&
                                                    ((startPos - lastTapPosition).getDistance() < 24 * density)

                                            if (screenX >= headerW && screenY <= headerTouchH) {
                                                // Tapped column header
                                                val gridX = (screenX - headerW - curPan.x) / userZoom
                                                val c = engine.getColAt(gridX).coerceIn(0, engine.maxCol - 1)
                                                if (isDoubleTap) {
                                                    lastTapTimestamp = 0L
                                                    selectedColumn = c
                                                    selectedCell = Pair(0, c)
                                                    viewModel.speak("Column ${engine.getColumnName(c)} selected")
                                                    triggerHaptic()
                                                } else {
                                                    lastTapTimestamp = now
                                                    lastTapPosition = startPos
                                                    selectedColumn = null
                                                    selectedCell = Pair(0, c)
                                                    viewModel.speakColumn(c)
                                                    triggerHaptic()
                                                }
                                            } else if (screenX >= headerW && screenY > headerTouchH) {
                                                // Cell grid tapped
                                                selectedColumn = null
                                                val gridX = (screenX - headerW - curPan.x) / userZoom
                                                val gridY = (screenY - headerH - curPan.y) / userZoom
                                                val r = engine.getRowAt(gridY).coerceIn(0, engine.maxRow - 1)
                                                val c = engine.getColAt(gridX).coerceIn(0, engine.maxCol - 1)
                                                val targetC = if (engine.isBannerRow(r)) 0 else c

                                                if (isDoubleTap) {
                                                    // Keep double-tap strictly for editing cell — do NOT double-tap-to-zoom
                                                    lastTapTimestamp = 0L
                                                    selectedCell = Pair(r, targetC)
                                                    editingCell = Pair(r, targetC)
                                                } else {
                                                    lastTapTimestamp = now
                                                    lastTapPosition = startPos
                                                    selectedCell = Pair(r, targetC)
                                                    viewModel.speakCell(r, targetC)
                                                    triggerHaptic()
                                                }
                                            } else if (screenX < headerW && screenY > headerH) {
                                                // Tapped row header
                                                selectedColumn = null
                                                val gridY = (screenY - headerH - curPan.y) / userZoom
                                                val r = engine.getRowAt(gridY).coerceIn(0, engine.maxRow - 1)
                                                val currentC = selectedCell?.second ?: 0
                                                selectedCell = Pair(r, currentC)
                                                if (isDoubleTap) {
                                                    lastTapTimestamp = 0L
                                                    showRowMenu = r
                                                    triggerHaptic()
                                                } else {
                                                    lastTapTimestamp = now
                                                    lastTapPosition = startPos
                                                    viewModel.speakRow(r)
                                                    triggerHaptic()
                                                }
                                            }
                                        }
                                    }
                                    break
                                }
                            }
                        }
                    }
            ) {
                val t = refreshTrigger // observe trigger
                engine.updateLayoutIfNeeded(density, settings.largeTouchMode, rowCellHeightMeasurer)
                val panOffset = animPanOffset.value
                val headerW = if (showRowNumbers) 44f * density else 0f
                val headerH = 32f * density
                val pad = 5f * density

                // --- 1. Cell Content Area (Grid Cells) ---
                // Clipped strictly to viewport area below and to the right of frozen headers
                clipRect(left = headerW, top = headerH, right = size.width, bottom = size.height) {
                    withTransform({
                        translate(left = headerW + panOffset.x, top = headerH + panOffset.y)
                        scale(scaleX = userZoom, scaleY = userZoom, pivot = Offset.Zero)
                    }) {
                        val visibleLeftUnscaled = -panOffset.x / userZoom
                        val visibleTopUnscaled = -panOffset.y / userZoom
                        val visibleRightUnscaled = (size.width - headerW - panOffset.x) / userZoom
                        val visibleBottomUnscaled = (size.height - headerH - panOffset.y) / userZoom

                        val startRow = engine.getRowAt(visibleTopUnscaled).coerceIn(0, engine.maxRow - 1)
                        val startCol = engine.getColAt(visibleLeftUnscaled).coerceIn(0, engine.maxCol - 1)

                        var r = startRow
                        while (r < engine.maxRow) {
                            val rowTop = engine.getRowOffsetPx(r)
                            val rowHeight = engine.getRowHeightPx(r)
                            val rowBottom = rowTop + rowHeight

                            if (rowBottom < visibleTopUnscaled) {
                                r++
                                continue
                            }
                            if (rowTop > visibleBottomUnscaled) {
                                break
                            }

                            val bannerRange = engine.getMergedRange(r, 0)
                            val isBanner = engine.isBannerRow(r)

                            if (isBanner) {
                                val bannerLeft = 0f
                                val bannerWidth = engine.getUsedWidthPx()
                                val isSelected = selectedCell?.first == r

                                val cellColorInt = engine.getCellColor(r, 0)
                                val rowColorInt = engine.getRowColor(r)
                                val customBgColor = when {
                                    cellColorInt != null -> Color(cellColorInt)
                                    rowColorInt != null -> Color(rowColorInt)
                                    else -> defaultBannerBg
                                }

                                drawRect(
                                    color = customBgColor,
                                    topLeft = Offset(bannerLeft, rowTop),
                                    size = Size(bannerWidth, rowHeight)
                                )

                                if (isSelected) {
                                    drawRect(
                                        color = highlightFill,
                                        topLeft = Offset(bannerLeft, rowTop),
                                        size = Size(bannerWidth, rowHeight)
                                    )
                                }

                                drawRect(
                                    color = gridColor,
                                    topLeft = Offset(bannerLeft, rowTop),
                                    size = Size(bannerWidth, rowHeight),
                                    style = Stroke(width = 1f * density)
                                )

                                val text = engine.getCellValue(r, 0).ifEmpty {
                                    (0 until engine.maxCol).firstNotNullOfOrNull { col -> engine.getCellValue(r, col).takeIf { it.isNotEmpty() } } ?: ""
                                }
                                if (text.isNotEmpty()) {
                                    clipRect(
                                        left = bannerLeft + pad,
                                        top = rowTop + pad,
                                        right = bannerLeft + bannerWidth - pad,
                                        bottom = rowBottom - pad
                                    ) {
                                        val cellTextColorInt = engine.getCellTextColor(r, 0)
                                        val rowTextColorInt = engine.getRowTextColor(r)
                                        val customTextColor = when {
                                            cellTextColorInt != null -> Color(cellTextColorInt)
                                            rowTextColorInt != null -> Color(rowTextColorInt)
                                            else -> null
                                        }
                                        val effectiveStyle = if (customTextColor != null) {
                                            headerRowStyle.copy(color = customTextColor)
                                        } else {
                                            val lum = 0.2126f * customBgColor.red + 0.7152f * customBgColor.green + 0.0722f * customBgColor.blue
                                            val contrastingColor = if (lum > 0.5f) Color(0xFF111111) else Color(0xFFF5F5F5)
                                            headerRowStyle.copy(color = contrastingColor)
                                        }

                                        val availableW = (bannerWidth - 2 * pad).roundToInt().coerceAtLeast(1)
                                        val textLayout = cellLayoutCache.getCellLayout(
                                            r = r,
                                            c = 0,
                                            text = text,
                                            isHeader = true,
                                            style = effectiveStyle,
                                            isWrapped = true,
                                            maxWidthPx = availableW
                                        )

                                        val align = engine.getCellAlignment(r, 0)
                                        val isRight = engine.isRightAligned(r, 0) || align == 2
                                        val isCenter = align == 1
                                        val textX = when {
                                            isRight -> (bannerLeft + bannerWidth - pad - textLayout.size.width).coerceAtLeast(bannerLeft + pad)
                                            isCenter -> bannerLeft + (bannerWidth - textLayout.size.width) / 2f
                                            else -> bannerLeft + pad
                                        }
                                        val textY = rowTop + (rowHeight - textLayout.size.height) / 2f

                                        drawText(
                                            textLayoutResult = textLayout,
                                            topLeft = Offset(textX, textY)
                                        )
                                    }
                                }

                                if (isSelected) {
                                    drawRect(
                                        color = Color.White,
                                        topLeft = Offset(bannerLeft + 1.5f * density, rowTop + 1.5f * density),
                                        size = Size(bannerWidth - 3f * density, rowHeight - 3f * density),
                                        style = Stroke(width = 1.5f * density)
                                    )
                                    drawRect(
                                        color = selectedCellColor,
                                        topLeft = Offset(bannerLeft, rowTop),
                                        size = Size(bannerWidth, rowHeight),
                                        style = Stroke(width = 3.5f * density)
                                    )
                                }

                                r++
                                continue
                            }

                            var c = startCol
                            while (c < engine.maxCol) {
                                val cellMerged = engine.getMergedRange(r, c)
                                if (cellMerged != null && !cellMerged.isTopLeft(r, c)) {
                                    c++
                                    continue
                                }
                                val colLeft = engine.getColOffsetPx(c)
                                val colWidth = engine.getColWidthPx(c)
                                val colRight = colLeft + colWidth

                                if (colRight < visibleLeftUnscaled) {
                                    c++
                                    continue
                                }
                                if (colLeft > visibleRightUnscaled) {
                                    break
                                }

                                val isSelected = selectedCell?.first == r && selectedCell?.second == c

                                // Rendering priority:
                                // 1. Cell color
                                // 2. Row color
                                // 3. Column color
                                // 4. Default background
                                val isHeaderRow = engine.isHeaderRow(r)
                                val cellColorInt = engine.getCellColor(r, c)
                                val rowColorInt = engine.getRowColor(r)
                                val colColorInt = engine.getColumnColor(c)
                                val customBgColor = when {
                                    cellColorInt != null -> Color(cellColorInt)
                                    rowColorInt != null -> Color(rowColorInt)
                                    isHeaderRow && engine.headerBgColor != null -> Color(engine.headerBgColor!!)
                                    isHeaderRow -> headerBg
                                    colColorInt != null -> Color(colColorInt)
                                    else -> null
                                }

                                if (customBgColor != null) {
                                    drawRect(
                                        color = customBgColor,
                                        topLeft = Offset(colLeft, rowTop),
                                        size = Size(colWidth, rowHeight)
                                    )
                                }

                                // Cell / Column Selection Highlight (Overlay stays visible on top)
                                if (isSelected || selectedColumn == c) {
                                    drawRect(
                                        color = highlightFill,
                                        topLeft = Offset(colLeft, rowTop),
                                        size = Size(colWidth, rowHeight)
                                    )
                                }

                                // Find & Replace Match Highlight
                                if (showFindReplace && matches.contains(Pair(r, c))) {
                                    val isCurrent = matches.getOrNull(currentMatchIndex) == Pair(r, c)
                                    drawRect(
                                        color = if (isCurrent) Color(0xFFFF9800).copy(alpha = 0.55f) else Color(0xFFFFEB3B).copy(alpha = 0.4f),
                                        topLeft = Offset(colLeft, rowTop),
                                        size = Size(colWidth, rowHeight)
                                    )
                                }

                                // Cell Border
                                drawRect(
                                    color = gridColor,
                                    topLeft = Offset(colLeft, rowTop),
                                    size = Size(colWidth, rowHeight),
                                    style = Stroke(width = 1f * density)
                                )

                                // Cell Content
                                val text = engine.getCellValue(r, c)
                                if (text.isNotEmpty()) {
                                    clipRect(
                                        left = colLeft + pad,
                                        top = rowTop + pad,
                                        right = colRight - pad,
                                        bottom = rowBottom - pad
                                    ) {
                                        val isWrapped = engine.isWrapEnabled(c)
                                        val cellTextColorInt = engine.getCellTextColor(r, c)
                                        val rowTextColorInt = engine.getRowTextColor(r)
                                        val colTextColorInt = engine.getColumnTextColor(c)
                                        val customTextColor = when {
                                            cellTextColorInt != null -> Color(cellTextColorInt)
                                            rowTextColorInt != null -> Color(rowTextColorInt)
                                            isHeaderRow && engine.headerTextColor != null -> Color(engine.headerTextColor!!)
                                            colTextColorInt != null -> Color(colTextColorInt)
                                            else -> null
                                        }

                                        val effectiveStyle = if (customTextColor != null) {
                                            if (isHeaderRow) headerRowStyle.copy(color = customTextColor) else textStyle.copy(color = customTextColor)
                                        } else if (isHeaderRow) {
                                            headerRowStyle
                                        } else if (customBgColor != null) {
                                            val lum = 0.2126f * customBgColor.red + 0.7152f * customBgColor.green + 0.0722f * customBgColor.blue
                                            val contrastingColor = if (lum > 0.5f) Color(0xFF111111) else Color(0xFFF5F5F5)
                                            textStyle.copy(color = contrastingColor)
                                        } else {
                                            textStyle
                                        }
                                        val availableTextW = (colWidth - 2 * pad).roundToInt().coerceAtLeast(1)

                                        val textLayout = cellLayoutCache.getCellLayout(
                                            r = r,
                                            c = c,
                                            text = text,
                                            isHeader = isHeaderRow,
                                            style = effectiveStyle,
                                            isWrapped = isWrapped,
                                            maxWidthPx = availableTextW
                                        )

                                        val isRight = engine.isRightAligned(r, c)
                                        val textX = if (isRight && !isHeaderRow) {
                                            (colRight - pad - textLayout.size.width).coerceAtLeast(colLeft + pad)
                                        } else {
                                            colLeft + pad
                                        }
                                        val textY = if (isWrapped) {
                                            rowTop + pad
                                        } else {
                                            rowTop + (rowHeight - textLayout.size.height) / 2
                                        }

                                        drawText(
                                            textLayoutResult = textLayout,
                                            topLeft = Offset(textX, textY)
                                        )
                                    }
                                }

                                // Active Cell Focus Outline
                                if (isSelected) {
                                    drawRect(
                                        color = Color.White,
                                        topLeft = Offset(colLeft + 1.5f * density, rowTop + 1.5f * density),
                                        size = Size(colWidth - 3f * density, rowHeight - 3f * density),
                                        style = Stroke(width = 1.5f * density)
                                    )
                                    drawRect(
                                        color = selectedCellColor,
                                        topLeft = Offset(colLeft, rowTop),
                                        size = Size(colWidth, rowHeight),
                                        style = Stroke(width = 3.5f * density)
                                    )
                                }

                                c++
                            }
                            r++
                        }
                    }
                }

                // --- 2. Frozen Top Column Headers ---
                // Pinned to the top edge (y in 0..headerH), tracking content horizontally
                clipRect(left = headerW, top = 0f, right = size.width, bottom = headerH) {
                    val headerBgColorInt = engine.headerBgColor
                    val effectiveHeaderBg = if (headerBgColorInt != null) Color(headerBgColorInt) else headerBg
                    drawRect(
                        color = effectiveHeaderBg,
                        topLeft = Offset(headerW, 0f),
                        size = Size(size.width - headerW, headerH)
                    )

                    val visibleLeftUnscaled = -panOffset.x / userZoom
                    val startCol = engine.getColAt(visibleLeftUnscaled).coerceIn(0, engine.maxCol - 1)

                    var hc = startCol
                    while (hc < engine.maxCol) {
                        val colLeft = headerW + panOffset.x + engine.getColOffsetPx(hc) * userZoom
                        val colWidth = engine.getColWidthPx(hc) * userZoom
                        val colRight = colLeft + colWidth

                        if (colRight < headerW) {
                            hc++
                            continue
                        }
                        if (colLeft > size.width) {
                            break
                        }

                        val colLetter = engine.getColumnName(hc)
                        val displayLabel = colLetter

                        val isColSelected = selectedCell?.second == hc || selectedColumn == hc
                        val isColFullySelected = selectedColumn == hc
                        val headerTextColorInt = engine.headerTextColor
                        val baseHeaderStyle = if (isColSelected) headerStyleSelected else headerStyleNormal
                        val effectiveHeaderStyle = if (headerTextColorInt != null) {
                            baseHeaderStyle.copy(color = Color(headerTextColorInt))
                        } else {
                            baseHeaderStyle
                        }

                        if (isColFullySelected) {
                            drawRect(
                                color = selectedCellColor.copy(alpha = 0.35f),
                                topLeft = Offset(colLeft, 0f),
                                size = Size(colWidth, headerH)
                            )
                            drawRect(
                                color = selectedCellColor,
                                topLeft = Offset(colLeft, headerH - 3.5f * density),
                                size = Size(colWidth, 3.5f * density)
                            )
                        } else {
                            val colColorInt = engine.getColumnColor(hc)
                            if (colColorInt != null) {
                                drawRect(
                                    color = Color(colColorInt).copy(alpha = 0.35f),
                                    topLeft = Offset(colLeft, 0f),
                                    size = Size(colWidth, headerH)
                                )
                                drawRect(
                                    color = Color(colColorInt),
                                    topLeft = Offset(colLeft, headerH - 3.5f * density),
                                    size = Size(colWidth, 3.5f * density)
                                )
                            }
                        }

                        drawRect(
                            color = gridColor,
                            topLeft = Offset(colLeft, 0f),
                            size = Size(colWidth, headerH),
                            style = Stroke(width = 1f * density)
                        )

                        // Draggable resize handle on right edge of Column Header
                        val handleH = 16f * density
                        val handleW = 3f * density
                        val handleX = colRight - handleW / 2
                        val handleY = (headerH - handleH) / 2
                        drawRoundRect(
                            color = if (activeColResizeDrag == hc) themeAccentColor else Color.LightGray.copy(alpha = 0.65f),
                            topLeft = Offset(handleX, handleY),
                            size = Size(handleW, handleH),
                            cornerRadius = CornerRadius(1.5f * density, 1.5f * density)
                        )

                        clipRect(
                            left = maxOf(headerW, colLeft),
                            top = 0f,
                            right = minOf(size.width, colRight),
                            bottom = headerH
                        ) {
                            val textLayout = cellLayoutCache.getColHeaderLayout(
                                c = hc,
                                label = displayLabel,
                                isSelected = isColSelected,
                                style = effectiveHeaderStyle
                            )
                            drawText(
                                textLayoutResult = textLayout,
                                topLeft = Offset(
                                    colLeft + (colWidth - textLayout.size.width) / 2,
                                    (headerH - textLayout.size.height) / 2
                                )
                            )
                        }

                        hc++
                    }

                    // Bottom divider line separating frozen header from cell content
                    drawLine(
                        color = gridColor,
                        start = Offset(headerW, headerH),
                        end = Offset(size.width, headerH),
                        strokeWidth = 1f * density
                    )
                }

                // --- 3. Frozen Left Row Headers (if enabled) ---
                // Pinned to the left edge (x in 0..headerW), tracking content vertically
                if (showRowNumbers && headerW > 0f) {
                    clipRect(left = 0f, top = headerH, right = headerW, bottom = size.height) {
                        drawRect(
                            color = headerBg,
                            topLeft = Offset(0f, headerH),
                            size = Size(headerW, size.height - headerH)
                        )

                        val visibleTopUnscaled = -panOffset.y / userZoom
                        val startRow = engine.getRowAt(visibleTopUnscaled).coerceIn(0, engine.maxRow - 1)

                        var hr = startRow
                        while (hr < engine.maxRow) {
                            val rowTop = headerH + panOffset.y + engine.getRowOffsetPx(hr) * userZoom
                            val rowHeight = engine.getRowHeightPx(hr) * userZoom
                            val rowBottom = rowTop + rowHeight

                            if (rowBottom < headerH) {
                                hr++
                                continue
                            }
                            if (rowTop > size.height) {
                                break
                            }

                            val rowName = "${hr + 1}"
                            val isRowSelected = selectedCell?.first == hr
                            val headerColor = if (isRowSelected) themeAccentColor else headerStyle.color

                            val rowColorInt = engine.getRowColor(hr)
                            if (rowColorInt != null) {
                                drawRect(
                                    color = Color(rowColorInt).copy(alpha = 0.35f),
                                    topLeft = Offset(0f, rowTop),
                                    size = Size(headerW, rowHeight)
                                )
                                drawRect(
                                    color = Color(rowColorInt),
                                    topLeft = Offset(headerW - 3.5f * density, rowTop),
                                    size = Size(3.5f * density, rowHeight)
                                )
                            }

                            drawRect(
                                color = gridColor,
                                topLeft = Offset(0f, rowTop),
                                size = Size(headerW, rowHeight),
                                style = Stroke(width = 1f * density)
                            )

                            // Draggable resize handle on bottom edge of Row Header
                            val handleW = 16f * density
                            val handleH = 3f * density
                            val handleX = (headerW - handleW) / 2
                            val handleY = rowBottom - handleH / 2
                            drawRoundRect(
                                color = if (activeRowResizeDrag == hr) themeAccentColor else Color.LightGray.copy(alpha = 0.65f),
                                topLeft = Offset(handleX, handleY),
                                size = Size(handleW, handleH),
                                cornerRadius = CornerRadius(1.5f * density, 1.5f * density)
                            )

                            val textLayout = cellLayoutCache.getRowHeaderLayout(
                                r = hr,
                                label = rowName,
                                isSelected = isRowSelected,
                                style = headerStyle.copy(color = headerColor)
                            )
                            drawText(
                                textLayoutResult = textLayout,
                                topLeft = Offset(
                                    (headerW - textLayout.size.width) / 2,
                                    rowTop + (rowHeight - textLayout.size.height) / 2
                                )
                            )

                            hr++
                        }

                        // Right divider line separating frozen row headers from cell content
                        drawLine(
                            color = gridColor,
                            start = Offset(headerW, headerH),
                            end = Offset(headerW, size.height),
                            strokeWidth = 1f * density
                        )
                    }

                    // --- 4. Top-Left Frozen Corner Box ---
                    drawRect(
                        color = headerBg,
                        topLeft = Offset(0f, 0f),
                        size = Size(headerW, headerH)
                    )
                    drawRect(
                        color = gridColor,
                        topLeft = Offset(0f, 0f),
                        size = Size(headerW, headerH),
                        style = Stroke(width = 1f * density)
                    )
                }

                // --- 5. Visual Drag Overlay & Tooltip while resizing ---
                if (activeColResizeDrag != null) {
                    val hc = activeColResizeDrag!!
                    val colLeft = headerW + panOffset.x + engine.getColOffsetPx(hc) * userZoom
                    val dragLineX = colLeft + activeColResizeWidthDp * density * userZoom

                    drawLine(
                        color = themeAccentColor,
                        start = Offset(dragLineX, 0f),
                        end = Offset(dragLineX, size.height),
                        strokeWidth = 2.5f * density,
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f * density, 6f * density), 0f)
                    )

                    val tooltipText = "Col ${engine.getColumnName(hc)}: ${activeColResizeWidthDp.roundToInt()} dp"
                    val tooltipLayout = textMeasurer.measure(
                        text = tooltipText,
                        style = androidx.compose.ui.text.TextStyle(
                            color = Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    )
                    val badgeW = (tooltipLayout.size.width + 18f * density)
                    val badgeH = 26f * density
                    val badgeX = (dragLineX - badgeW / 2).coerceIn(headerW + 4f * density, size.width - badgeW - 4f * density)
                    val badgeY = headerH + 8f * density

                    drawRoundRect(
                        color = Color(0xFF1E293B),
                        topLeft = Offset(badgeX, badgeY),
                        size = Size(badgeW, badgeH),
                        cornerRadius = CornerRadius(6f * density, 6f * density)
                    )
                    drawRoundRect(
                        color = themeAccentColor,
                        topLeft = Offset(badgeX, badgeY),
                        size = Size(badgeW, badgeH),
                        cornerRadius = CornerRadius(6f * density, 6f * density),
                        style = Stroke(width = 1.5f * density)
                    )
                    drawText(
                        textLayoutResult = tooltipLayout,
                        topLeft = Offset(
                            badgeX + (badgeW - tooltipLayout.size.width) / 2,
                            badgeY + (badgeH - tooltipLayout.size.height) / 2
                        )
                    )
                }

                if (activeRowResizeDrag != null) {
                    val hr = activeRowResizeDrag!!
                    val rowTop = headerH + panOffset.y + engine.getRowOffsetPx(hr) * userZoom
                    val dragLineY = rowTop + activeRowResizeHeightDp * density * userZoom

                    drawLine(
                        color = themeAccentColor,
                        start = Offset(0f, dragLineY),
                        end = Offset(size.width, dragLineY),
                        strokeWidth = 2.5f * density,
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f * density, 6f * density), 0f)
                    )

                    val tooltipText = "Row ${hr + 1}: ${activeRowResizeHeightDp.roundToInt()} dp"
                    val tooltipLayout = textMeasurer.measure(
                        text = tooltipText,
                        style = androidx.compose.ui.text.TextStyle(
                            color = Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    )
                    val badgeW = (tooltipLayout.size.width + 18f * density)
                    val badgeH = 26f * density
                    val badgeX = headerW + 8f * density
                    val badgeY = (dragLineY - badgeH / 2).coerceIn(headerH + 4f * density, size.height - badgeH - 4f * density)

                    drawRoundRect(
                        color = Color(0xFF1E293B),
                        topLeft = Offset(badgeX, badgeY),
                        size = Size(badgeW, badgeH),
                        cornerRadius = CornerRadius(6f * density, 6f * density)
                    )
                    drawRoundRect(
                        color = themeAccentColor,
                        topLeft = Offset(badgeX, badgeY),
                        size = Size(badgeW, badgeH),
                        cornerRadius = CornerRadius(6f * density, 6f * density),
                        style = Stroke(width = 1.5f * density)
                    )
                    drawText(
                        textLayoutResult = tooltipLayout,
                        topLeft = Offset(
                            badgeX + (badgeW - tooltipLayout.size.width) / 2,
                            badgeY + (badgeH - tooltipLayout.size.height) / 2
                        )
                    )
                }
            }
        }
    }
    
    // Edit Dialog
    editingCell?.let { (r, c) ->
        val formulaOrValue = engine.getCellFormulaOrValue(r, c)
        var textFieldValue by remember(r, c) {
            mutableStateOf(TextFieldValue(formulaOrValue, selection = TextRange(formulaOrValue.length)))
        }
        DisposableEffect(r, c) {
            activeEditTextFieldUpdater = { spoken ->
                val cur = textFieldValue.text
                val updated = if (cur.isEmpty() || cur == "=") spoken else "$cur $spoken"
                textFieldValue = TextFieldValue(updated, selection = TextRange(updated.length))
            }
            onDispose {
                activeEditTextFieldUpdater = null
            }
        }
        var isFormulaExpanded by remember(r, c) {
            mutableStateOf(formulaOrValue.startsWith("="))
        }
        val isBanner = engine.isBannerRow(r)
        val headerName = engine.getColumnHeaderName(c, r)
        val cellTitle = if (isBanner) {
            "Edit Banner Row ${r + 1}"
        } else if (r > 0 && headerName.isNotEmpty() && !headerName.startsWith("Column ")) {
            "Edit ${engine.getColumnName(c)}${r + 1} ($headerName)"
        } else {
            "Edit ${engine.getColumnName(c)}${r + 1}"
        }

        data class FormulaChipItem(
            val name: String,
            val template: String,
            val cursorOffset: Int,
            val description: String,
            val category: String
        )

        val formulaCategories = listOf("Math", "Text", "Logical", "Date", "Lookup", "Statistical")
        var selectedFormulaCategory by remember { mutableStateOf("Math") }

        val formulaOptions = remember {
            listOf(
                // Math
                FormulaChipItem("SUM", "=SUM(:)", 5, "Insert SUM formula", "Math"),
                FormulaChipItem("AVERAGE", "=AVERAGE(:)", 9, "Insert AVERAGE formula", "Math"),
                FormulaChipItem("COUNT", "=COUNT(:)", 7, "Insert COUNT formula", "Math"),
                FormulaChipItem("MIN", "=MIN(:)", 5, "Insert MIN formula", "Math"),
                FormulaChipItem("MAX", "=MAX(:)", 5, "Insert MAX formula", "Math"),
                FormulaChipItem("ROUND", "=ROUND(, 2)", 7, "Insert ROUND formula", "Math"),
                FormulaChipItem("ROUNDUP", "=ROUNDUP(, 2)", 10, "Insert ROUNDUP formula", "Math"),
                FormulaChipItem("ROUNDDOWN", "=ROUNDDOWN(, 2)", 12, "Insert ROUNDDOWN formula", "Math"),
                FormulaChipItem("ABS", "=ABS()", 5, "Insert ABS formula", "Math"),
                FormulaChipItem("SQRT", "=SQRT()", 6, "Insert SQRT formula", "Math"),
                FormulaChipItem("POWER", "=POWER(, )", 7, "Insert POWER formula", "Math"),
                FormulaChipItem("MOD", "=MOD(, )", 5, "Insert MOD formula", "Math"),
                FormulaChipItem("INT", "=INT()", 5, "Insert INT formula", "Math"),

                // Text
                FormulaChipItem("CONCATENATE", "=CONCATENATE(, )", 13, "Insert CONCATENATE formula", "Text"),
                FormulaChipItem("LEFT", "=LEFT(, )", 6, "Insert LEFT formula", "Text"),
                FormulaChipItem("RIGHT", "=RIGHT(, )", 7, "Insert RIGHT formula", "Text"),
                FormulaChipItem("MID", "=MID(, , )", 5, "Insert MID formula", "Text"),
                FormulaChipItem("LEN", "=LEN()", 5, "Insert LEN formula", "Text"),
                FormulaChipItem("TRIM", "=TRIM()", 6, "Insert TRIM formula", "Text"),
                FormulaChipItem("PROPER", "=PROPER()", 8, "Insert PROPER formula", "Text"),
                FormulaChipItem("UPPER", "=UPPER()", 7, "Insert UPPER formula", "Text"),
                FormulaChipItem("LOWER", "=LOWER()", 7, "Insert LOWER formula", "Text"),
                FormulaChipItem("SUBSTITUTE", "=SUBSTITUTE(, , )", 12, "Insert SUBSTITUTE formula", "Text"),
                FormulaChipItem("TEXT", "=TEXT(, \"\")", 7, "Insert TEXT formula", "Text"),

                // Logical
                FormulaChipItem("IF", "=IF(, , )", 4, "Insert IF formula", "Logical"),
                FormulaChipItem("AND", "=AND(, )", 5, "Insert AND formula", "Logical"),
                FormulaChipItem("OR", "=OR(, )", 4, "Insert OR formula", "Logical"),
                FormulaChipItem("NOT", "=NOT()", 5, "Insert NOT formula", "Logical"),
                FormulaChipItem("IFERROR", "=IFERROR(, )", 9, "Insert IFERROR formula", "Logical"),
                FormulaChipItem("ISBLANK", "=ISBLANK()", 9, "Insert ISBLANK formula", "Logical"),
                FormulaChipItem("ISNUMBER", "=ISNUMBER()", 10, "Insert ISNUMBER formula", "Logical"),
                FormulaChipItem("ISTEXT", "=ISTEXT()", 8, "Insert ISTEXT formula", "Logical"),

                // Date
                FormulaChipItem("TODAY", "=TODAY()", 7, "Insert TODAY formula", "Date"),
                FormulaChipItem("NOW", "=NOW()", 5, "Insert NOW formula", "Date"),
                FormulaChipItem("DATE", "=DATE(, , )", 6, "Insert DATE formula", "Date"),
                FormulaChipItem("YEAR", "=YEAR()", 6, "Insert YEAR formula", "Date"),
                FormulaChipItem("MONTH", "=MONTH()", 7, "Insert MONTH formula", "Date"),
                FormulaChipItem("DAY", "=DAY()", 5, "Insert DAY formula", "Date"),
                FormulaChipItem("DATEDIF", "=DATEDIF(, , \"D\")", 9, "Insert DATEDIF formula", "Date"),

                // Lookup
                FormulaChipItem("VLOOKUP", "=VLOOKUP(, , )", 9, "Insert VLOOKUP formula", "Lookup"),
                FormulaChipItem("XLOOKUP", "=XLOOKUP(, , )", 9, "Insert XLOOKUP formula", "Lookup"),
                FormulaChipItem("UNIQUE", "=UNIQUE(:)", 8, "Insert UNIQUE formula", "Lookup"),
                FormulaChipItem("SORT", "=SORT(:)", 6, "Insert SORT formula", "Lookup"),
                FormulaChipItem("SORTN", "=SORTN(:)", 7, "Insert SORTN formula", "Lookup"),
                FormulaChipItem("FILTER", "=FILTER(:)", 8, "Insert FILTER formula", "Lookup"),
                FormulaChipItem("INDEX", "=INDEX(: , )", 7, "Insert INDEX formula", "Lookup"),
                FormulaChipItem("MATCH", "=MATCH(, :)", 7, "Insert MATCH formula", "Lookup"),

                // Statistical
                FormulaChipItem("SUMIF", "=SUMIF(, )", 7, "Insert SUMIF formula", "Statistical"),
                FormulaChipItem("COUNTIF", "=COUNTIF(, )", 9, "Insert COUNTIF formula", "Statistical"),
                FormulaChipItem("COUNTA", "=COUNTA(:)", 8, "Insert COUNTA formula", "Statistical"),
                FormulaChipItem("COUNTBLANK", "=COUNTBLANK(:)", 12, "Insert COUNTBLANK formula", "Statistical"),
                FormulaChipItem("MEDIAN", "=MEDIAN(:)", 8, "Insert MEDIAN formula", "Statistical"),
                FormulaChipItem("MODE", "=MODE(:)", 6, "Insert MODE formula", "Statistical")
            )
        }

        fun insertTemplate(item: FormulaChipItem) {
            val curText = textFieldValue.text
            val selStart = textFieldValue.selection.min.coerceIn(0, curText.length)
            val selEnd = textFieldValue.selection.max.coerceIn(0, curText.length)

            if (curText.isEmpty() || curText == "=") {
                textFieldValue = TextFieldValue(
                    text = item.template,
                    selection = TextRange(item.cursorOffset)
                )
            } else {
                val before = curText.substring(0, selStart)
                val after = curText.substring(selEnd)
                val insertText = if (before.isEmpty() || before.endsWith("=")) {
                    if (before.endsWith("=")) item.template.removePrefix("=") else item.template
                } else {
                    item.template.removePrefix("=")
                }
                val offsetInInsert = if (before.isEmpty()) item.cursorOffset else (item.cursorOffset - 1)
                val newText = before + insertText + after
                val newCursor = (selStart + offsetInInsert).coerceIn(0, newText.length)
                textFieldValue = TextFieldValue(
                    text = newText,
                    selection = TextRange(newCursor)
                )
            }
        }
        
        AlertDialog(
            onDismissRequest = { editingCell = null },
            title = { Text(cellTitle) },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedTextField(
                        value = textFieldValue,
                        onValueChange = { textFieldValue = it },
                        label = { Text("Cell Value or Formula") },
                        placeholder = { Text("Enter text, number, or insert formula") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 90.dp)
                            .testTag("cell_edit_textfield"),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = themeAccentColor,
                            focusedLabelColor = themeAccentColor
                        )
                    )

                    // Collapsible Formula Section
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("formula_section_toggle")
                            .clickable(
                                onClickLabel = if (isFormulaExpanded) "Collapse formula section" else "Expand formula section"
                            ) {
                                isFormulaExpanded = !isFormulaExpanded
                            }
                            .semantics {
                                contentDescription = "Formula section, ${if (isFormulaExpanded) "Expanded" else "Collapsed"}"
                            }
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    Icons.Default.Functions,
                                    contentDescription = null,
                                    tint = themeAccentColor,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Formula",
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 14.sp,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                            Icon(
                                imageVector = if (isFormulaExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                contentDescription = if (isFormulaExpanded) "Collapse" else "Expand",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    if (isFormulaExpanded) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 2.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = "Formula categories:",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            ScrollableTabRow(
                                selectedTabIndex = formulaCategories.indexOf(selectedFormulaCategory).coerceAtLeast(0),
                                edgePadding = 4.dp,
                                containerColor = Color.Transparent,
                                contentColor = themeAccentColor,
                                divider = {},
                                modifier = Modifier.height(36.dp)
                            ) {
                                formulaCategories.forEach { cat ->
                                    val isSelected = selectedFormulaCategory == cat
                                    Tab(
                                        selected = isSelected,
                                        onClick = { selectedFormulaCategory = cat },
                                        text = {
                                            Text(
                                                text = cat,
                                                fontSize = 11.sp,
                                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                                color = if (isSelected) themeAccentColor else MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    )
                                }
                            }

                            val filteredChips = formulaOptions.filter { it.category == selectedFormulaCategory }
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState())
                                    .padding(vertical = 2.dp),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                filteredChips.forEach { fItem ->
                                    SuggestionChip(
                                        onClick = {
                                            insertTemplate(fItem)
                                            triggerHaptic()
                                        },
                                        label = {
                                            Text(
                                                text = fItem.name,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 12.sp
                                            )
                                        },
                                        colors = SuggestionChipDefaults.suggestionChipColors(
                                            containerColor = themeAccentColor.copy(alpha = 0.12f),
                                            labelColor = themeAccentColor
                                        ),
                                        border = SuggestionChipDefaults.suggestionChipBorder(
                                            enabled = true,
                                            borderColor = themeAccentColor.copy(alpha = 0.4f)
                                        ),
                                        modifier = Modifier
                                            .testTag("chip_formula_${fItem.name.lowercase()}")
                                            .semantics {
                                                contentDescription = fItem.description
                                            }
                                    )
                                }
                            }

                            Text(
                                text = "Examples: =SORT(A2:A20) sorts single column ascending, =SORT(A2:B20, 1) keeps rows together, =SORT(A2:B20, 1, \"DESC\") sorts descending.",
                                fontSize = 10.sp,
                                lineHeight = 13.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f)
                            )
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.updateCell(r, c, textFieldValue.text)
                        editingCell = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = themeAccentColor),
                    modifier = Modifier.testTag("save_cell_button")
                ) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { editingCell = null },
                    modifier = Modifier.testTag("cancel_cell_button")
                ) {
                    Text("Cancel")
                }
            }
        )
    }

    // Unified Color Picker Dialog (Background & Text with Preset & Custom Options)
    colorPickerState?.let { state ->
        UnifiedColorPickerDialog(
            state = state,
            engine = engine,
            onSetBgColor = { color ->
                when (val target = state.target) {
                    is ColorTarget.Cell -> viewModel.setCellColor(target.r, target.c, color)
                    is ColorTarget.Column -> viewModel.setColumnColor(target.c, color)
                    is ColorTarget.Row -> viewModel.setRowColor(target.r, color)
                    is ColorTarget.Header -> viewModel.setHeaderBgColor(color)
                }
            },
            onSetTextColor = { color ->
                when (val target = state.target) {
                    is ColorTarget.Cell -> viewModel.setCellTextColor(target.r, target.c, color)
                    is ColorTarget.Column -> viewModel.setColumnTextColor(target.c, color)
                    is ColorTarget.Row -> viewModel.setRowTextColor(target.r, color)
                    is ColorTarget.Header -> viewModel.setHeaderTextColor(color)
                }
            },
            onDismiss = { colorPickerState = null }
        )
    }

    if (showSaveConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showSaveConfirmDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Save, contentDescription = null, tint = themeAccentColor)
                    Spacer(Modifier.width(8.dp))
                    Text("Save Document to Device")
                }
            },
            text = {
                Column {
                    Text(
                        text = "Do you want to store '$fileName' on your device?",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(Modifier.height(10.dp))
                    Text(
                        text = "• Save to Downloads: Quick save directly into your device's Downloads directory.\n• Choose Location: Open file picker to save anywhere (Documents, SD Card, etc.).",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        showSaveConfirmDialog = false
                        viewModel.saveDocument { success, pathOrError ->
                            coroutineScope.launch {
                                snackbarHostState.showSnackbar(
                                    if (success) "Saved to device: $pathOrError" else "Save failed: $pathOrError"
                                )
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = themeAccentColor),
                    modifier = Modifier.testTag("confirm_save_yes_button")
                ) {
                    Text("Save to Downloads")
                }
            },
            dismissButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = {
                            showSaveConfirmDialog = false
                            saveAsLauncher.launch(fileName)
                        },
                        modifier = Modifier.testTag("confirm_save_choose_location_button")
                    ) {
                        Text("Choose Location")
                    }
                    TextButton(
                        onClick = { showSaveConfirmDialog = false },
                        modifier = Modifier.testTag("confirm_save_cancel_button")
                    ) {
                        Text("Cancel")
                    }
                }
            }
        )
    }

    clearColConfirm?.let { col ->
        val colLetter = engine.getColumnName(col)
        AlertDialog(
            onDismissRequest = { clearColConfirm = null },
            title = { Text("Clear Column $colLetter") },
            text = { Text("Are you sure you want to clear all text in Column $colLetter?") },
            confirmButton = {
                Button(
                    onClick = {
                        clearColConfirm = null
                        viewModel.clearColumn(col)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = themeAccentColor),
                    modifier = Modifier.testTag("confirm_clear_column")
                ) { Text("Clear") }
            },
            dismissButton = {
                TextButton(onClick = { clearColConfirm = null }) { Text("Cancel") }
            }
        )
    }

    clearRowConfirm?.let { row ->
        val rowNum = row + 1
        AlertDialog(
            onDismissRequest = { clearRowConfirm = null },
            title = { Text("Clear Row $rowNum") },
            text = { Text("Are you sure you want to clear all cells in Row $rowNum?") },
            confirmButton = {
                Button(
                    onClick = {
                        clearRowConfirm = null
                        viewModel.clearRow(row)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = themeAccentColor),
                    modifier = Modifier.testTag("confirm_clear_row")
                ) { Text("Clear") }
            },
            dismissButton = {
                TextButton(onClick = { clearRowConfirm = null }) { Text("Cancel") }
            }
        )
    }

    deleteRowConfirm?.let { row ->
        val rowNum = row + 1
        AlertDialog(
            onDismissRequest = { deleteRowConfirm = null },
            title = { Text("Delete Row $rowNum") },
            text = { Text("Are you sure you want to delete Row $rowNum and shift rows below up?") },
            confirmButton = {
                Button(
                    onClick = {
                        deleteRowConfirm = null
                        viewModel.deleteRow(row)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = themeAccentColor),
                    modifier = Modifier.testTag("confirm_delete_row")
                ) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { deleteRowConfirm = null }) { Text("Cancel") }
            }
        )
    }

    if (showDeleteModeChooser) {
        AlertDialog(
            onDismissRequest = { showDeleteModeChooser = false },
            title = { Text("Choose Delete Action") },
            text = {
                Column {
                    listOf(
                        DeleteMode.CLEAR_TEXT to "Clear text",
                        DeleteMode.CLEAR_FORMATTING to "Clear formatting",
                        DeleteMode.CLEAR_ROW to "Clear row",
                        DeleteMode.CLEAR_COLUMN to "Clear column"
                    ).forEach { (mode, label) ->
                        Text(
                            text = label,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    showDeleteModeChooser = false
                                    viewModel.updateDeleteMode(mode)
                                }
                                .padding(16.dp),
                            color = themeAccentColor,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showDeleteModeChooser = false }) { Text("Cancel") }
            }
        )
    }

    if (showLanguagePicker) {
        var searchQuery by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showLanguagePicker = false },
            title = { Text("Voice Typing Languages") },
            text = {
                Column(modifier = Modifier.fillMaxWidth().heightIn(max = 400.dp)) {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        label = { Text("Search language") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
                    )
                    val allLocales = listOf(
                        "en-US" to "English (United States)",
                        "en-GB" to "English (United Kingdom)",
                        "es-ES" to "Spanish (Spain)",
                        "fr-FR" to "French (France)",
                        "de-DE" to "German (Germany)",
                        "it-IT" to "Italian (Italy)",
                        "ja-JP" to "Japanese (Japan)",
                        "ko-KR" to "Korean (South Korea)",
                        "zh-CN" to "Chinese (Simplified)",
                        "pt-BR" to "Portuguese (Brazil)",
                        "ru-RU" to "Russian (Russia)",
                        "hi-IN" to "Hindi (India)"
                    )
                    val filtered = allLocales.filter { it.second.contains(searchQuery, ignoreCase = true) || it.first.contains(searchQuery, ignoreCase = true) }
                    
                    LazyColumn(modifier = Modifier.fillMaxWidth().weight(1f)) {
                        item {
                            Text("Downloaded (works offline)", fontWeight = FontWeight.Bold, color = themeAccentColor, fontSize = 12.sp, modifier = Modifier.padding(vertical = 4.dp))
                        }
                        items(filtered.take(3)) { (code, name) ->
                            Text(
                                text = name,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        showLanguagePicker = false
                                        viewModel.updateVoiceTypingLanguage(code)
                                    }
                                    .padding(vertical = 10.dp, horizontal = 4.dp),
                                fontSize = 14.sp
                            )
                        }
                        item {
                            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                            Text("Online", fontWeight = FontWeight.Bold, color = themeAccentColor, fontSize = 12.sp, modifier = Modifier.padding(vertical = 4.dp))
                        }
                        items(filtered.drop(3)) { (code, name) ->
                            Text(
                                text = name,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        showLanguagePicker = false
                                        viewModel.updateVoiceTypingLanguage(code)
                                    }
                                    .padding(vertical = 10.dp, horizontal = 4.dp),
                                fontSize = 14.sp
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showLanguagePicker = false }) { Text("Close") }
            }
        )
    }

    if (showRenameDialog) {
        AlertDialog(
            onDismissRequest = { showRenameDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Edit, contentDescription = null, tint = themeAccentColor, modifier = Modifier.size(22.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Rename Document")
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Enter a new name for this spreadsheet.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedTextField(
                        value = renameBaseName,
                        onValueChange = { renameBaseName = it },
                        label = { Text("Document Name") },
                        singleLine = true,
                        trailingIcon = {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(end = 4.dp)
                            ) {
                                if (renameExtension.isNotEmpty()) {
                                    Surface(
                                        color = MaterialTheme.colorScheme.surfaceVariant,
                                        shape = RoundedCornerShape(4.dp)
                                    ) {
                                        Text(
                                            text = renameExtension,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                                        )
                                    }
                                    Spacer(Modifier.width(4.dp))
                                }
                                IconButton(
                                    onClick = {
                                        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                                            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                                            val lang = settings.voiceTypingLanguage.takeIf { it.isNotBlank() } ?: Locale.getDefault().toString()
                                            putExtra(RecognizerIntent.EXTRA_LANGUAGE, lang)
                                            putExtra(RecognizerIntent.EXTRA_PROMPT, "Speak new document name...")
                                        }
                                        try {
                                            renameSpeechLauncher.launch(intent)
                                        } catch (_: Exception) {
                                            viewModel.ttsManager.speak("Voice input not available")
                                        }
                                    },
                                    modifier = Modifier.size(36.dp).testTag("rename_mic_button")
                                ) {
                                    Icon(
                                        Icons.Default.Mic,
                                        contentDescription = "Speak new document name",
                                        tint = themeAccentColor,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth().testTag("rename_document_input")
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val trimmedBase = renameBaseName.trim()
                        if (trimmedBase.isNotBlank()) {
                            val finalFullName = if (renameExtension.isNotEmpty()) "$trimmedBase$renameExtension" else trimmedBase
                            viewModel.renameDocument(finalFullName)
                        }
                        showRenameDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = themeAccentColor),
                    modifier = Modifier.testTag("confirm_rename_button")
                ) {
                    Text("Rename")
                }
            },
            dismissButton = {
                TextButton(onClick = { showRenameDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showZoomControlsMenu) {
        AlertDialog(
            onDismissRequest = { showZoomControlsMenu = false },
            icon = {
                Icon(
                    imageVector = Icons.Default.ZoomIn,
                    contentDescription = null,
                    tint = themeAccentColor,
                    modifier = Modifier.size(32.dp)
                )
            },
            title = {
                Text(
                    text = "Zoom Controls",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Large Current Zoom Display
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = themeAccentColor.copy(alpha = 0.12f)
                    ) {
                        Text(
                            text = "${(userZoom * 100).roundToInt()}%",
                            color = themeAccentColor,
                            fontWeight = FontWeight.Bold,
                            fontSize = 26.sp,
                            modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)
                        )
                    }

                    // Stepper Row: [-] [Slider] [+]
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        FilledTonalIconButton(
                            onClick = {
                                applyZoom((userZoom - 0.15f).coerceIn(MIN_ZOOM, MAX_ZOOM))
                            },
                            modifier = Modifier.testTag("dialog_zoom_out_button")
                        ) {
                            Icon(Icons.Default.Remove, contentDescription = "Zoom Out")
                        }

                        Slider(
                            value = userZoom,
                            onValueChange = { applyZoom(it) },
                            valueRange = MIN_ZOOM..MAX_ZOOM,
                            steps = 28,
                            modifier = Modifier
                                .weight(1f)
                                .padding(horizontal = 8.dp)
                                .testTag("zoom_slider"),
                            colors = SliderDefaults.colors(
                                thumbColor = themeAccentColor,
                                activeTrackColor = themeAccentColor
                            )
                        )

                        FilledTonalIconButton(
                            onClick = {
                                applyZoom((userZoom + 0.15f).coerceIn(MIN_ZOOM, MAX_ZOOM))
                            },
                            modifier = Modifier.testTag("dialog_zoom_in_button")
                        ) {
                            Icon(Icons.Default.Add, contentDescription = "Zoom In")
                        }
                    }

                    // Preset Chips Row
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = "Preset Levels",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(bottom = 6.dp)
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            listOf(0.5f, 0.75f, 1.0f, 1.5f, 2.0f).forEach { preset ->
                                val isCurrent = (userZoom * 100).roundToInt() == (preset * 100).roundToInt()
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = if (isCurrent) themeAccentColor else MaterialTheme.colorScheme.surfaceVariant,
                                    modifier = Modifier
                                        .weight(1f)
                                        .clickable { applyZoom(preset) }
                                        .testTag("zoom_preset_${(preset * 100).roundToInt()}")
                                ) {
                                    Text(
                                        text = "${(preset * 100).roundToInt()}%",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isCurrent) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(vertical = 8.dp),
                                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                    )
                                }
                            }
                        }
                    }

                    // Action buttons: Fit to Screen & 100%
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                val headerW = if (settings.showRowNumbers) 44f * density else 0f
                                val headerH = 32f * density
                                val viewW = (if (viewportSize.width > 0) viewportSize.width.toFloat() else 1000f) - headerW
                                val viewH = (if (viewportSize.height > 0) viewportSize.height.toFloat() else 1500f) - headerH
                                val fitZoom = if (viewW > 0 && viewH > 0 && engine.totalWidthPx > 0 && engine.totalHeightPx > 0) {
                                    minOf(viewW / engine.totalWidthPx, viewH / engine.totalHeightPx).coerceIn(MIN_ZOOM, 1.0f)
                                } else {
                                    0.25f
                                }
                                applyZoom(fitZoom)
                            },
                            modifier = Modifier.weight(1f).testTag("dialog_zoom_fit_button")
                        ) {
                            Icon(Icons.Default.FitScreen, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Fit to Screen")
                        }

                        OutlinedButton(
                            onClick = { applyZoom(1.0f) },
                            modifier = Modifier.weight(1f).testTag("dialog_zoom_reset_button")
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("100%")
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = { showZoomControlsMenu = false },
                    colors = ButtonDefaults.buttonColors(containerColor = themeAccentColor),
                    modifier = Modifier.testTag("dialog_zoom_done_button")
                ) {
                    Text("Done")
                }
            }
        )
    }

    // Sheets Management Dialog (Top-right menu only)
    if (showSheetsDialog) {
        AlertDialog(
            onDismissRequest = { showSheetsDialog = false },
            icon = {
                Icon(
                    imageVector = Icons.Default.Layers,
                    contentDescription = null,
                    tint = themeAccentColor,
                    modifier = Modifier.size(32.dp)
                )
            },
            title = {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Sheets (${engine.sheets.size})", fontWeight = FontWeight.Bold)
                    IconButton(
                        onClick = {
                            newSheetNameInput = "Sheet${engine.sheets.size + 1}"
                            showAddSheetDialog = true
                        },
                        modifier = Modifier.testTag("dialog_add_sheet_button")
                    ) {
                        Icon(Icons.Default.Add, contentDescription = "Add sheet", tint = themeAccentColor)
                    }
                }
            },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "Tap to switch. Long-press to rename or delete.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(4.dp))
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 280.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        items(engine.sheets.size) { idx ->
                            val sheet = engine.sheets[idx]
                            val isActive = idx == engine.currentSheetIndex
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .combinedClickable(
                                        onClick = {
                                            viewModel.switchSheet(idx)
                                            showSheetsDialog = false
                                        },
                                        onLongClick = {
                                            sheetToRename = Pair(idx, sheet.name)
                                            sheetRenameInput = sheet.name
                                        }
                                    )
                                    .testTag("sheet_item_$idx"),
                                shape = RoundedCornerShape(10.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = if (isActive) themeAccentColor.copy(alpha = 0.15f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                                )
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 14.dp, vertical = 12.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            Icons.Default.Layers,
                                            contentDescription = null,
                                            tint = if (isActive) themeAccentColor else MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(20.dp)
                                        )
                                        Spacer(Modifier.width(12.dp))
                                        Text(
                                            text = sheet.name,
                                            fontWeight = if (isActive) FontWeight.Bold else FontWeight.Medium,
                                            color = if (isActive) themeAccentColor else MaterialTheme.colorScheme.onSurface
                                        )
                                    }
                                    if (isActive) {
                                        Surface(
                                            shape = RoundedCornerShape(6.dp),
                                            color = themeAccentColor
                                        ) {
                                            Text(
                                                text = "Active",
                                                color = Color.White,
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold,
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                            )
                                        }
                                    } else {
                                        IconButton(
                                            onClick = {
                                                sheetToDelete = Pair(idx, sheet.name)
                                            },
                                            modifier = Modifier.size(28.dp).testTag("delete_sheet_btn_$idx")
                                        ) {
                                            Icon(
                                                Icons.Default.Close,
                                                contentDescription = "Delete sheet",
                                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = { showSheetsDialog = false },
                    colors = ButtonDefaults.buttonColors(containerColor = themeAccentColor),
                    modifier = Modifier.testTag("dialog_sheets_done_button")
                ) {
                    Text("Close")
                }
            }
        )
    }

    // Add Sheet Dialog
    if (showAddSheetDialog) {
        AlertDialog(
            onDismissRequest = { showAddSheetDialog = false },
            title = { Text("Add New Sheet") },
            text = {
                OutlinedTextField(
                    value = newSheetNameInput,
                    onValueChange = { newSheetNameInput = it },
                    label = { Text("Sheet Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("add_sheet_name_input")
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (newSheetNameInput.isNotBlank()) {
                            viewModel.addSheet(newSheetNameInput.trim())
                        }
                        showAddSheetDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = themeAccentColor),
                    modifier = Modifier.testTag("confirm_add_sheet_button")
                ) {
                    Text("Add")
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddSheetDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Rename Sheet Dialog
    sheetToRename?.let { target ->
        AlertDialog(
            onDismissRequest = { sheetToRename = null },
            title = { Text("Rename Sheet") },
            text = {
                OutlinedTextField(
                    value = sheetRenameInput,
                    onValueChange = { sheetRenameInput = it },
                    label = { Text("Sheet Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("rename_sheet_name_input")
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (sheetRenameInput.isNotBlank()) {
                            viewModel.renameSheet(target.first, sheetRenameInput.trim())
                        }
                        sheetToRename = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = themeAccentColor),
                    modifier = Modifier.testTag("confirm_rename_sheet_button")
                ) {
                    Text("Rename")
                }
            },
            dismissButton = {
                TextButton(onClick = { sheetToRename = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Delete Sheet Confirmation Dialog
    sheetToDelete?.let { target ->
        AlertDialog(
            onDismissRequest = { sheetToDelete = null },
            title = { Text("Delete Sheet?") },
            text = { Text("Are you sure you want to delete sheet \"${target.second}\"?") },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteSheet(target.first)
                        sheetToDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    modifier = Modifier.testTag("confirm_delete_sheet_button")
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { sheetToDelete = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showActionMenuSheet) {
        val curR = selectedCell?.first ?: 0
        val curC = selectedCell?.second ?: 0
        ActionMenuSheet(
            onDismiss = { showActionMenuSheet = false },
            pagerState = pagerState,
            targetCell = Pair(curR, curC),
            engine = engine,
            viewModel = viewModel,
            settings = settings,
            coroutineScope = coroutineScope,
            selectedColumn = selectedColumn,
            onEditCell = { cellPair, initialFormula ->
                editingCell = cellPair
                if (initialFormula != null) {
                    engine.setCell(cellPair.first, cellPair.second, initialFormula)
                }
            },
            onOpenColorPicker = { target, tab ->
                colorPickerState = ColorPickerState(target, tab)
            },
            onOpenZoom = {
                showZoomControlsMenu = true
            },
            onOpenFindReplace = {
                showFindReplace = true
            },
            onConfirmClearCol = { col -> clearColConfirm = col },
            onConfirmClearRow = { row -> clearRowConfirm = row },
            onConfirmDeleteRow = { row -> deleteRowConfirm = row }
        )
    }

    if (showQuickActionsCustomizer) {
        val curR = selectedCell?.first ?: 0
        val curC = selectedCell?.second ?: 0
        QuickActionsCustomizationDialog(
            currentSelectedIds = settings.quickActionIds,
            onDismiss = { showQuickActionsCustomizer = false },
            onSave = { newIds ->
                viewModel.updateQuickActions(newIds)
                viewModel.ttsManager.speak("Quick actions updated")
            },
            r = curR,
            c = curC,
            engine = engine,
            settings = settings
        )
    }

    quickActionToManage?.let { action ->
        AlertDialog(
            onDismissRequest = { quickActionToManage = null },
            title = { Text("Manage Quick Action: ${action.title}") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Choose an action:",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    
                    TextButton(
                        onClick = {
                            viewModel.moveQuickAction(action.actionId, -1)
                            viewModel.ttsManager.speak("Moved ${action.title} left")
                            quickActionToManage = null
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null, tint = themeAccentColor)
                        Spacer(Modifier.width(8.dp))
                        Text("Move Left / Earlier", color = themeAccentColor)
                    }

                    TextButton(
                        onClick = {
                            viewModel.moveQuickAction(action.actionId, 1)
                            viewModel.ttsManager.speak("Moved ${action.title} right")
                            quickActionToManage = null
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, tint = themeAccentColor)
                        Spacer(Modifier.width(8.dp))
                        Text("Move Right / Later", color = themeAccentColor)
                    }

                    TextButton(
                        onClick = {
                            quickActionToManage = null
                            showQuickActionsCustomizer = true
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Edit, contentDescription = null, tint = themeAccentColor)
                        Spacer(Modifier.width(8.dp))
                        Text("Arrange Quick Actions (Custom Order)", color = themeAccentColor)
                    }

                    TextButton(
                        onClick = {
                            viewModel.removeQuickAction(action.actionId)
                            viewModel.ttsManager.speak("Removed ${action.title} from favorites")
                            quickActionToManage = null
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                        Spacer(Modifier.width(8.dp))
                        Text("Remove from Quick Actions", color = MaterialTheme.colorScheme.error)
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { quickActionToManage = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    showResizeColumnDialog?.let { col ->
        val currentWidth = engine.getColWidthDp(col)
        ResizeDimensionDialog(
            title = "Resize Column ${engine.getColumnName(col)}",
            dimensionName = "Column Width",
            initialValueDp = currentWidth,
            presets = listOf(40, 60, 85, 120, 160, 220, 300),
            themeColor = themeAccentColor,
            onConfirm = { newW ->
                viewModel.setColumnWidth(col, newW)
                showResizeColumnDialog = null
            },
            onDismiss = { showResizeColumnDialog = null }
        )
    }

    showResizeRowDialog?.let { row ->
        val currentHeight = engine.getRowHeightDp(row)
        ResizeDimensionDialog(
            title = "Resize Row ${row + 1}",
            dimensionName = "Row Height",
            initialValueDp = currentHeight,
            presets = listOf(28, 36, 44, 60, 80, 120, 180),
            themeColor = themeAccentColor,
            onConfirm = { newH ->
                viewModel.setRowHeight(row, newH)
                showResizeRowDialog = null
            },
            onDismiss = { showResizeRowDialog = null }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ResizeDimensionDialog(
    title: String,
    dimensionName: String,
    initialValueDp: Float,
    minValueDp: Float = 20f,
    maxValueDp: Float = 500f,
    presets: List<Int>,
    themeColor: Color,
    onConfirm: (Float) -> Unit,
    onDismiss: () -> Unit
) {
    var sliderValue by remember { mutableFloatStateOf(initialValueDp.coerceIn(minValueDp, maxValueDp)) }
    var textValue by remember { mutableStateOf(sliderValue.roundToInt().toString()) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surface,
        dragHandle = { BottomSheetDefaults.DragHandle() },
        modifier = Modifier.testTag("resize_dimension_sheet")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp)
                .navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = if (dimensionName.contains("Column", ignoreCase = true) || dimensionName.contains("Width", ignoreCase = true)) Icons.Default.WidthNormal else Icons.Default.Height,
                        contentDescription = null,
                        tint = themeColor,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
                IconButton(onClick = onDismiss, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Default.Close, contentDescription = "Close", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            OutlinedTextField(
                value = textValue,
                onValueChange = { input ->
                    val digits = input.filter { it.isDigit() }
                    textValue = digits
                    digits.toFloatOrNull()?.let { num ->
                        sliderValue = num.coerceIn(minValueDp, maxValueDp)
                    }
                },
                label = { Text("$dimensionName (20 - 500 dp)") },
                suffix = { Text("dp") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth().testTag("resize_text_input")
            )

            // Visual Slider with Two-Way Binding
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Visual Slider",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = "${sliderValue.roundToInt()} dp",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = themeColor
                    )
                }

                Slider(
                    value = sliderValue,
                    onValueChange = { newVal ->
                        sliderValue = newVal
                        textValue = newVal.roundToInt().toString()
                    },
                    valueRange = minValueDp..maxValueDp,
                    colors = SliderDefaults.colors(
                        thumbColor = themeColor,
                        activeTrackColor = themeColor
                    ),
                    modifier = Modifier.fillMaxWidth().testTag("resize_slider")
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("${minValueDp.toInt()} dp", fontSize = 11.sp, color = MaterialTheme.colorScheme.outline)
                    Text("${maxValueDp.toInt()} dp", fontSize = 11.sp, color = MaterialTheme.colorScheme.outline)
                }
            }

            // Quick Preset Chips
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                presets.forEach { preset ->
                    val isSelected = sliderValue.roundToInt() == preset
                    AssistChip(
                        onClick = {
                            sliderValue = preset.toFloat()
                            textValue = preset.toString()
                        },
                        label = { Text("${preset} dp", fontSize = 12.sp) },
                        colors = AssistChipDefaults.assistChipColors(
                            containerColor = if (isSelected) themeColor.copy(alpha = 0.15f) else Color.Transparent
                        ),
                        border = AssistChipDefaults.assistChipBorder(
                            enabled = true,
                            borderColor = if (isSelected) themeColor else MaterialTheme.colorScheme.outlineVariant
                        )
                    )
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier.testTag("resize_cancel_button")
                ) {
                    Text("Cancel")
                }
                Spacer(Modifier.width(8.dp))
                Button(
                    onClick = {
                        val finalVal = textValue.toFloatOrNull()?.coerceIn(minValueDp, maxValueDp) ?: sliderValue
                        onConfirm(finalVal)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = themeColor),
                    modifier = Modifier.testTag("resize_apply_button")
                ) {
                    Text("Apply")
                }
            }
        }
    }
}

data class ColorSwatch(val name: String, val colorInt: Int)

val presetBgSwatches = listOf(
    ColorSwatch("Soft Red", 0xFFFFCDD2.toInt()),
    ColorSwatch("Coral", 0xFFFFAB91.toInt()),
    ColorSwatch("Peach", 0xFFFFE0B2.toInt()),
    ColorSwatch("Warm Yellow", 0xFFFFF59D.toInt()),
    ColorSwatch("Mint Green", 0xFFC8E6C9.toInt()),
    ColorSwatch("Teal", 0xFFB2DFDB.toInt()),
    ColorSwatch("Sky Blue", 0xFFBBDEFB.toInt()),
    ColorSwatch("Indigo", 0xFFC5CAE9.toInt()),
    ColorSwatch("Lavender", 0xFFE1BEE7.toInt()),
    ColorSwatch("Rose", 0xFFF8BBD0.toInt()),
    ColorSwatch("Slate Grey", 0xFFCFD8DC.toInt()),
    ColorSwatch("Dark Forest", 0xFF2D5A27.toInt()),
    ColorSwatch("Navy Blue", 0xFF1A237E.toInt()),
    ColorSwatch("Charcoal", 0xFF263238.toInt()),
    ColorSwatch("Gold", 0xFFFFD54F.toInt()),
    ColorSwatch("Pure White", 0xFFFFFFFF.toInt())
)

val presetTextSwatches = listOf(
    ColorSwatch("Pure Black", 0xFF000000.toInt()),
    ColorSwatch("Dark Charcoal", 0xFF212121.toInt()),
    ColorSwatch("Pure White", 0xFFFFFFFF.toInt()),
    ColorSwatch("Crimson", 0xFFD32F2F.toInt()),
    ColorSwatch("Deep Orange", 0xFFE64A19.toInt()),
    ColorSwatch("Forest Green", 0xFF2E7D32.toInt()),
    ColorSwatch("Emerald", 0xFF00897B.toInt()),
    ColorSwatch("Royal Blue", 0xFF1976D2.toInt()),
    ColorSwatch("Navy", 0xFF0D47A1.toInt()),
    ColorSwatch("Purple", 0xFF7B1FA2.toInt()),
    ColorSwatch("Hot Pink", 0xFFC2185B.toInt()),
    ColorSwatch("Slate Grey", 0xFF455A64.toInt()),
    ColorSwatch("Gold Amber", 0xFFFFA000.toInt()),
    ColorSwatch("Brown", 0xFF5D4037.toInt()),
    ColorSwatch("Cyan", 0xFF0097A7.toInt()),
    ColorSwatch("Lime Green", 0xFF689F38.toInt())
)

@Composable
fun UnifiedColorPickerDialog(
    state: ColorPickerState,
    engine: com.speaksheet.utils.SpreadsheetEngine,
    onSetBgColor: (Int?) -> Unit,
    onSetTextColor: (Int?) -> Unit,
    onDismiss: () -> Unit
) {
    var activeTab by remember(state) { mutableStateOf(state.initialTab) }

    val currentBgColor = when (val target = state.target) {
        is ColorTarget.Cell -> engine.getCellColor(target.r, target.c)
        is ColorTarget.Column -> engine.getColumnColor(target.c)
        is ColorTarget.Row -> engine.getRowColor(target.r)
        is ColorTarget.Header -> engine.headerBgColor
    }

    val currentTextColor = when (val target = state.target) {
        is ColorTarget.Cell -> engine.getCellTextColor(target.r, target.c)
        is ColorTarget.Column -> engine.getColumnTextColor(target.c)
        is ColorTarget.Row -> engine.getRowTextColor(target.r)
        is ColorTarget.Header -> engine.headerTextColor
    }

    var workingBgColor by remember(state.target) { mutableStateOf(currentBgColor) }
    var workingTextColor by remember(state.target) { mutableStateOf(currentTextColor) }

    val currentWorkingColor = if (activeTab == ColorPickerTab.BACKGROUND) workingBgColor else workingTextColor
    val defaultColor = if (activeTab == ColorPickerTab.BACKGROUND) 0xFFFFFFFF.toInt() else 0xFF000000.toInt()
    val effectiveColorInt = currentWorkingColor ?: defaultColor

    val hsv = remember(activeTab, currentWorkingColor) {
        val arr = FloatArray(3)
        android.graphics.Color.colorToHSV(effectiveColorInt, arr)
        arr
    }
    var hue by remember(activeTab, currentWorkingColor) { mutableFloatStateOf(hsv[0]) }
    var saturation by remember(activeTab, currentWorkingColor) { mutableFloatStateOf(hsv[1]) }
    var value by remember(activeTab, currentWorkingColor) { mutableFloatStateOf(hsv[2]) }

    fun updateFromHsv(newHue: Float, newSat: Float, newVal: Float) {
        hue = newHue
        saturation = newSat
        value = newVal
        val newColor = android.graphics.Color.HSVToColor(floatArrayOf(newHue, newSat, newVal)) or (0xFF shl 24)
        if (activeTab == ColorPickerTab.BACKGROUND) {
            workingBgColor = newColor
        } else {
            workingTextColor = newColor
        }
    }

    val titleTarget = when (val target = state.target) {
        is ColorTarget.Cell -> "${engine.getColumnName(target.c)}${target.r + 1}"
        is ColorTarget.Column -> "Column ${engine.getColumnName(target.c)}"
        is ColorTarget.Row -> "Row ${target.r + 1}"
        is ColorTarget.Header -> "Header Row"
    }

    val themeColor = MaterialTheme.colorScheme.primary

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Color Options: $titleTarget",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Background vs Text Tabs
                TabRow(
                    selectedTabIndex = if (activeTab == ColorPickerTab.BACKGROUND) 0 else 1,
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                    contentColor = themeColor
                ) {
                    Tab(
                        selected = activeTab == ColorPickerTab.BACKGROUND,
                        onClick = {
                            activeTab = ColorPickerTab.BACKGROUND
                        },
                        text = { Text("Background", fontWeight = FontWeight.Bold) },
                        modifier = Modifier.testTag("tab_background_color")
                    )
                    Tab(
                        selected = activeTab == ColorPickerTab.TEXT,
                        onClick = {
                            activeTab = ColorPickerTab.TEXT
                        },
                        text = { Text("Text Color", fontWeight = FontWeight.Bold) },
                        modifier = Modifier.testTag("tab_text_color")
                    )
                }

                // Color Preview & Hex Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(56.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color(effectiveColorInt))
                            .border(2.dp, Color.Black.copy(alpha = 0.2f), RoundedCornerShape(12.dp))
                            .testTag("color_preview_box")
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (activeTab == ColorPickerTab.BACKGROUND) "Selected Background" else "Selected Text Color",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = if (currentWorkingColor != null) String.format("#%06X", effectiveColorInt and 0xFFFFFF) else "(Default)",
                            style = MaterialTheme.typography.titleMedium,
                            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                // Preset Palette Swatches
                Text(
                    text = "Quick Palette",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold
                )
                val swatches = if (activeTab == ColorPickerTab.BACKGROUND) presetBgSwatches else presetTextSwatches
                LazyVerticalGrid(
                    columns = GridCells.Fixed(4),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 140.dp)
                ) {
                    items(swatches) { swatch ->
                        val swatchColor = Color(swatch.colorInt)
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(swatchColor)
                                .border(1.dp, Color.Black.copy(alpha = 0.25f), RoundedCornerShape(8.dp))
                                .clickable {
                                    val newColor = swatch.colorInt
                                    val tempHsv = FloatArray(3)
                                    android.graphics.Color.colorToHSV(newColor, tempHsv)
                                    hue = tempHsv[0]
                                    saturation = tempHsv[1]
                                    value = tempHsv[2]
                                    if (activeTab == ColorPickerTab.BACKGROUND) {
                                        workingBgColor = newColor
                                    } else {
                                        workingTextColor = newColor
                                    }
                                }
                                .testTag("swatch_${swatch.name.lowercase().replace(" ", "_")}"),
                            contentAlignment = Alignment.Center
                        ) {}
                    }
                }

                HorizontalDivider()

                // Graphical Color Picker Canvas & Brightness
                Text(
                    text = "Graphical Color Picker",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold
                )

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(160.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .pointerInput(activeTab) {
                            detectTapGestures { offset ->
                                val newHue = (offset.x / size.width).coerceIn(0f, 1f) * 360f
                                val newSat = (1f - (offset.y / size.height)).coerceIn(0f, 1f)
                                updateFromHsv(newHue, newSat, value)
                            }
                        }
                        .pointerInput(activeTab) {
                            detectDragGestures { change, _ ->
                                val offset = change.position
                                val newHue = (offset.x / size.width).coerceIn(0f, 1f) * 360f
                                val newSat = (1f - (offset.y / size.height)).coerceIn(0f, 1f)
                                updateFromHsv(newHue, newSat, value)
                            }
                        }
                ) {
                    val hueBrush = remember {
                        Brush.horizontalGradient(
                            listOf(
                                Color.Red,
                                Color.Yellow,
                                Color.Green,
                                Color.Cyan,
                                Color.Blue,
                                Color.Magenta,
                                Color.Red
                            )
                        )
                    }
                    val satBrush = remember {
                        Brush.verticalGradient(
                            listOf(
                                Color.Transparent,
                                Color.White
                            )
                        )
                    }

                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val width = size.width
                        val height = size.height

                        // 1. Horizontal hue spectrum
                        drawRect(brush = hueBrush)

                        // 2. Vertical saturation overlay (white to transparent)
                        drawRect(brush = satBrush)

                        // 3. Brightness/Value darkening overlay
                        if (value < 1.0f) {
                            drawRect(color = Color.Black.copy(alpha = (1.0f - value).coerceIn(0f, 1f)))
                        }

                        val thumbX = (hue / 360f) * width
                        val thumbY = (1f - saturation) * height
                        drawCircle(color = Color.White, radius = 7.dp.toPx(), center = Offset(thumbX, thumbY))
                        drawCircle(color = Color.Black, radius = 5.dp.toPx(), center = Offset(thumbX, thumbY))
                    }
                }

                // Brightness Slider
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
                        .padding(12.dp)
                ) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Brightness (Value)", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = themeColor)
                        Text("${(value * 100).roundToInt()}%", fontSize = 12.sp, fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace, fontWeight = FontWeight.Bold)
                    }
                    Slider(
                        value = value,
                        onValueChange = { updateFromHsv(hue, saturation, it) },
                        valueRange = 0f..1f,
                        colors = SliderDefaults.colors(thumbColor = themeColor, activeTrackColor = themeColor),
                        modifier = Modifier.testTag("graphical_brightness_slider")
                    )
                }

                // Reset / Clear Button
                OutlinedButton(
                    onClick = {
                        val defaultC = if (activeTab == ColorPickerTab.BACKGROUND) 0xFFFFFFFF.toInt() else 0xFF000000.toInt()
                        val tempHsv = FloatArray(3)
                        android.graphics.Color.colorToHSV(defaultC, tempHsv)
                        hue = tempHsv[0]
                        saturation = tempHsv[1]
                        value = tempHsv[2]
                        if (activeTab == ColorPickerTab.BACKGROUND) {
                            workingBgColor = null
                        } else {
                            workingTextColor = null
                        }
                    },
                    modifier = Modifier.fillMaxWidth().height(40.dp)
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(if (activeTab == ColorPickerTab.BACKGROUND) "Reset Background Color" else "Reset Text Color", fontSize = 12.sp)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (workingBgColor != currentBgColor) {
                        onSetBgColor(workingBgColor)
                    }
                    if (workingTextColor != currentTextColor) {
                        onSetTextColor(workingTextColor)
                    }
                    onDismiss()
                },
                colors = ButtonDefaults.buttonColors(containerColor = themeColor),
                modifier = Modifier.testTag("dialog_color_done_button")
            ) {
                Text("Done")
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.testTag("dialog_color_cancel_button")
            ) {
                Text("Cancel")
            }
        }
    )
}
