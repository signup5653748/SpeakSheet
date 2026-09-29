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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Functions
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.material.icons.filled.ZoomOut
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.*
import com.speaksheet.utils.SpreadsheetEngine
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
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
    private val cellCache = HashMap<Long, CachedCellLayout>()
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

sealed class ActionMenuTarget {
    data class Cell(val r: Int, val c: Int) : ActionMenuTarget()
    data class Column(val c: Int) : ActionMenuTarget()
    data class Row(val r: Int) : ActionMenuTarget()
    data class General(val c: Int, val r: Int) : ActionMenuTarget()
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ActionMenuBottomSheet(
    initialTab: Int,
    targetCell: Pair<Int, Int>,
    engine: SpreadsheetEngine,
    viewModel: MainViewModel,
    onTabSelected: (Int) -> Unit,
    onDismiss: () -> Unit,
    onEditCell: (Pair<Int, Int>) -> Unit,
    onOpenColorPicker: (ColorTarget, ColorPickerTab) -> Unit,
    onConfirmClearCol: (Int) -> Unit,
    onConfirmClearRow: (Int) -> Unit,
    onConfirmDeleteRow: (Int) -> Unit
) {
    var selectedTab by remember { mutableIntStateOf(initialTab) }
    val tabs = listOf("Cell", "Format", "Banner", "Column", "Row")
    val context = LocalContext.current
    val (r, c) = targetCell

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 32.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                tabs.forEachIndexed { index, tabName ->
                    val isSelected = (selectedTab == index)
                    Surface(
                        onClick = {
                            if (selectedTab != index) {
                                selectedTab = index
                                onTabSelected(index)
                                viewModel.ttsManager.speak("$tabName tab selected")
                            }
                        },
                        shape = RoundedCornerShape(12.dp),
                        color = if (isSelected) GreenPrimary else MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .semantics {
                                role = Role.Tab
                                contentDescription = "$tabName tab, ${index + 1} of 5, ${if (isSelected) "selected" else "not selected"}"
                            }
                            .testTag("tab_${tabName.lowercase()}")
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                text = tabName,
                                fontWeight = FontWeight.Bold,
                                color = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurface,
                                fontSize = 13.sp
                            )
                        }
                    }
                }
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

            val actions = when (selectedTab) {
                0 -> listOf(
                    Triple("Edit", Icons.Default.Edit, "edit"),
                    Triple("Copy", Icons.Default.ContentCopy, "copy"),
                    Triple("Paste", Icons.Default.ContentPaste, "paste"),
                    Triple("Paste values only", Icons.Default.ContentPaste, "paste_values"),
                    Triple("Paste formats only", Icons.Default.ContentPaste, "paste_formats"),
                    Triple("Paste formulas only", Icons.Default.ContentPaste, "paste_formulas"),
                    Triple("Fill down", Icons.Default.Add, "fill_down"),
                    Triple("Fill right", Icons.Default.Add, "fill_right"),
                    Triple("Clear cell", Icons.Default.Clear, "clear_cell"),
                    Triple("Speak cell", Icons.AutoMirrored.Filled.VolumeUp, "speak_cell")
                )
                1 -> listOf(
                    Triple(if (engine.getCellBold(r, c)) "Remove bold" else "Make bold", Icons.Default.Edit, "toggle_bold"),
                    Triple(if (engine.getCellItalic(r, c)) "Remove italic" else "Make italic", Icons.Default.Edit, "toggle_italic"),
                    Triple("Align left", Icons.Default.Check, "align_left"),
                    Triple("Align center", Icons.Default.Check, "align_center"),
                    Triple("Align right", Icons.Default.Check, "align_right"),
                    Triple("Format: General", Icons.Default.Check, "fmt_general"),
                    Triple("Format: Number", Icons.Default.Check, "fmt_number"),
                    Triple("Format: Currency", Icons.Default.Check, "fmt_currency"),
                    Triple("Format: Percent", Icons.Default.Check, "fmt_percent"),
                    Triple("Format: Date", Icons.Default.Check, "fmt_date"),
                    Triple("Border: None", Icons.Default.Clear, "border_none"),
                    Triple("Border: All", Icons.Default.Check, "border_all"),
                    Triple("Border: Outer", Icons.Default.Check, "border_outer"),
                    Triple(if (engine.isWrapEnabled(c)) "Disable wrap" else "Wrap text", Icons.Default.Edit, "wrap_text"),
                    Triple("Cell background color", Icons.Default.Check, "cell_bg_color"),
                    Triple("Cell text color", Icons.Default.Check, "cell_text_color")
                )
                2 -> listOf(
                    Triple("Insert banner above", Icons.Default.Add, "insert_banner_above"),
                    Triple("Insert banner below", Icons.Default.Add, "insert_banner_below"),
                    Triple("Convert to banner", Icons.Default.Edit, "convert_to_banner"),
                    Triple("Unmerge banner", Icons.Default.Clear, "unmerge_banner"),
                    Triple("Banner color", Icons.Default.Check, "banner_color"),
                    Triple("Set header row", Icons.Default.Check, "set_header_row"),
                    Triple("Clear header row", Icons.Default.Clear, "clear_header_row"),
                    Triple("Header row color", Icons.Default.Check, "header_row_color")
                )
                3 -> listOf(
                    Triple("Sort A-Z", Icons.Default.Check, "sort_asc"),
                    Triple("Sort Z-A", Icons.Default.Check, "sort_desc"),
                    Triple("Filter", Icons.Default.Check, "filter"),
                    Triple("Freeze first column", Icons.Default.Check, "freeze_first_col"),
                    Triple("Unfreeze column", Icons.Default.Clear, "unfreeze_col"),
                    Triple("Column color", Icons.Default.Check, "column_color"),
                    Triple("Speak column", Icons.AutoMirrored.Filled.VolumeUp, "speak_column"),
                    Triple("Clear column", Icons.Default.Clear, "clear_column"),
                    Triple("Delete column", Icons.Default.Delete, "delete_column")
                )
                else -> listOf(
                    Triple("Insert row above", Icons.Default.Add, "insert_row_above"),
                    Triple("Insert row below", Icons.Default.Add, "insert_row_below"),
                    Triple("Delete row", Icons.Default.Delete, "delete_row"),
                    Triple("Freeze top row", Icons.Default.Check, "freeze_top_row"),
                    Triple("Freeze up to selected", Icons.Default.Check, "freeze_selected"),
                    Triple("Unfreeze row", Icons.Default.Clear, "unfreeze_row"),
                    Triple("Row color", Icons.Default.Check, "row_color"),
                    Triple("Row text color", Icons.Default.Check, "row_text_color"),
                    Triple("Speak row", Icons.AutoMirrored.Filled.VolumeUp, "speak_row"),
                    Triple("Clear row", Icons.Default.Clear, "clear_row")
                )
            }

            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 380.dp)
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(actions, key = { it.third }) { action ->
                    Button(
                        onClick = {
                            onDismiss()
                            when (action.third) {
                                "edit" -> onEditCell(Pair(r, c))
                                "copy" -> viewModel.copyCell(context, r, c)
                                "paste" -> viewModel.pasteCell(context, r, c)
                                "paste_values" -> viewModel.pasteSpecial(r, c, r, c, SpreadsheetEngine.PasteMode.VALUES_ONLY)
                                "paste_formats" -> viewModel.pasteSpecial(r, c, r, c, SpreadsheetEngine.PasteMode.FORMATS_ONLY)
                                "paste_formulas" -> viewModel.pasteSpecial(r, c, r, c, SpreadsheetEngine.PasteMode.FORMULAS_ONLY)
                                "fill_down" -> viewModel.fillDown(r, c, minOf(r + 5, engine.maxRow - 1), c)
                                "fill_right" -> viewModel.fillRight(r, c, r, minOf(c + 3, engine.maxCol - 1))
                                "clear_cell" -> viewModel.deleteCell(r, c)
                                "speak_cell" -> viewModel.speakCell(r, c)

                                "toggle_bold" -> viewModel.setCellBold(r, c, !engine.getCellBold(r, c))
                                "toggle_italic" -> viewModel.setCellItalic(r, c, !engine.getCellItalic(r, c))
                                "align_left" -> viewModel.setCellAlignment(r, c, 0)
                                "align_center" -> viewModel.setCellAlignment(r, c, 1)
                                "align_right" -> viewModel.setCellAlignment(r, c, 2)
                                "fmt_general" -> viewModel.setCellNumberFormat(r, c, "General")
                                "fmt_number" -> viewModel.setCellNumberFormat(r, c, "Number")
                                "fmt_currency" -> viewModel.setCellNumberFormat(r, c, "Currency")
                                "fmt_percent" -> viewModel.setCellNumberFormat(r, c, "Percent")
                                "fmt_date" -> viewModel.setCellNumberFormat(r, c, "Date")
                                "border_none" -> viewModel.setCellBorders(r, c, 0)
                                "border_all" -> viewModel.setCellBorders(r, c, 1)
                                "border_outer" -> viewModel.setCellBorders(r, c, 2)
                                "wrap_text" -> viewModel.toggleColumnWrap(c)
                                "cell_bg_color" -> onOpenColorPicker(ColorTarget.Cell(r, c), ColorPickerTab.BACKGROUND)
                                "cell_text_color" -> onOpenColorPicker(ColorTarget.Cell(r, c), ColorPickerTab.TEXT)

                                "insert_banner_above" -> viewModel.insertBannerAbove(r) { onEditCell(it) }
                                "insert_banner_below" -> viewModel.insertBannerBelow(r) { onEditCell(it) }
                                "convert_to_banner" -> viewModel.convertRowToBanner(r)
                                "unmerge_banner" -> viewModel.unmergeBanner(r)
                                "banner_color" -> onOpenColorPicker(ColorTarget.Row(r), ColorPickerTab.BACKGROUND)
                                "set_header_row" -> viewModel.setHeaderRow(r)
                                "clear_header_row" -> viewModel.clearHeaderRow(r)
                                "header_row_color" -> onOpenColorPicker(ColorTarget.Row(0), ColorPickerTab.BACKGROUND)

                                "sort_asc" -> viewModel.sortColumn(c, true)
                                "sort_desc" -> viewModel.sortColumn(c, false)
                                "filter" -> viewModel.ttsManager.speak("Filter active")
                                "freeze_first_col" -> viewModel.setFreezePanes(engine.frozenRows, 1)
                                "unfreeze_col" -> viewModel.setFreezePanes(engine.frozenRows, 0)
                                "column_color" -> onOpenColorPicker(ColorTarget.Column(c), ColorPickerTab.BACKGROUND)
                                "speak_column" -> viewModel.speakColumn(c)
                                "clear_column" -> onConfirmClearCol(c)
                                "delete_column" -> viewModel.clearColumn(c)

                                "insert_row_above" -> viewModel.insertRowAbove(r)
                                "insert_row_below" -> viewModel.insertRowBelow(r)
                                "freeze_top_row" -> viewModel.setFreezePanes(1, engine.frozenCols)
                                "freeze_selected" -> viewModel.setFreezePanes(r + 1, c + 1)
                                "unfreeze_row" -> viewModel.setFreezePanes(0, engine.frozenCols)
                                "row_color" -> onOpenColorPicker(ColorTarget.Row(r), ColorPickerTab.BACKGROUND)
                                "row_text_color" -> onOpenColorPicker(ColorTarget.Row(r), ColorPickerTab.TEXT)
                                "speak_row" -> viewModel.speakRow(r)
                                "clear_row" -> onConfirmClearRow(r)
                                "delete_row" -> onConfirmDeleteRow(r)
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                            .testTag("action_${action.third}"),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant
                        ),
                        contentPadding = PaddingValues(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Start
                        ) {
                            Icon(
                                imageVector = action.second,
                                contentDescription = null,
                                tint = GreenPrimary,
                                modifier = Modifier.size(22.dp)
                            )
                            Spacer(Modifier.width(10.dp))
                            Text(
                                text = action.first,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ActionItem(text: String, onClick: () -> Unit) {
    Text(
        text = text,
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(vertical = 10.dp, horizontal = 4.dp),
        fontSize = 14.sp,
        fontWeight = FontWeight.Medium,
        color = MaterialTheme.colorScheme.onSurface
    )
}

sealed class ColorTarget {
    data class Cell(val r: Int, val c: Int) : ColorTarget()
    data class Column(val c: Int) : ColorTarget()
    data class Row(val r: Int) : ColorTarget()
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
    val engine = viewModel.spreadsheetEngine
    val context = LocalContext.current
    val density = LocalDensity.current.density
    val coroutineScope = rememberCoroutineScope()
    
    var selectedCell by remember { mutableStateOf<Pair<Int, Int>?>(Pair(0, 0)) }
    var editingCell by remember { mutableStateOf<Pair<Int, Int>?>(null) }
    var showMenuForCell by remember { mutableStateOf<Pair<Int, Int>?>(null) }
    var showOptionsMenu by remember { mutableStateOf(false) }
    var showZoomControlsMenu by remember { mutableStateOf(false) }
    var showColumnMenu by remember { mutableStateOf<Int?>(null) }
    var showRowMenu by remember { mutableStateOf<Int?>(null) }
    var actionMenuTarget by remember { mutableStateOf<ActionMenuTarget?>(null) }
    var colorPickerState by remember { mutableStateOf<ColorPickerState?>(null) }
    
    val currentFileUri by viewModel.currentFileUri.collectAsStateWithLifecycle()
    var showRenameDialog by remember { mutableStateOf(false) }
    var renameText by remember { mutableStateOf("") }
    var showSaveConfirmDialog by remember { mutableStateOf(false) }
    var clearColConfirm by remember { mutableStateOf<Int?>(null) }
    var clearRowConfirm by remember { mutableStateOf<Int?>(null) }
    var deleteRowConfirm by remember { mutableStateOf<Int?>(null) }
    var showDeleteModeChooser by remember { mutableStateOf(false) }
    var showLanguagePicker by remember { mutableStateOf(false) }

    val speechLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val matches = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
            val spoken = matches?.firstOrNull()
            if (!spoken.isNullOrBlank()) {
                viewModel.insertVoiceText(spoken, selectedCell, editingCell)
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
    var viewportSize by remember { mutableStateOf(IntSize.Zero) }
    var lastTapTimestamp by remember { mutableLongStateOf(0L) }
    var lastTapPosition by remember { mutableStateOf(Offset.Zero) }

    LaunchedEffect(currentFileUri, fileName) {
        selectedCell = Pair(0, 0)
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

    val textMeasurer = rememberTextMeasurer(cacheSize = 512)
    val textStyle = MaterialTheme.typography.bodyMedium.copy(
        color = MaterialTheme.colorScheme.onBackground,
        fontSize = 13.sp
    )
    val headerRowStyle = textStyle.copy(fontWeight = FontWeight.Bold, color = GreenPrimary)
    val headerStyle = MaterialTheme.typography.labelMedium.copy(
        color = MaterialTheme.colorScheme.onSurface,
        fontWeight = FontWeight.Bold,
        fontSize = 11.sp
    )
    val headerStyleNormal = remember(headerStyle) { headerStyle }
    val headerStyleSelected = remember(headerStyle) { headerStyle.copy(color = GreenPrimary) }

    val gridColor = if (settings.highContrastGrid) Color(0xFF888888) else Color(0xFF444444)
    val headerBg = MaterialTheme.colorScheme.surface
    val highlightFill = GreenPrimary.copy(alpha = 0.22f)

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
            val style = if (r == 0) headerRowStyle else textStyle
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
        val clampedZoom = newZoom.coerceIn(0.7f, 3.0f)
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

    // Smooth navigation with clear landing feedback mapped to current scale & offset
    fun moveSelection(deltaRow: Int, deltaCol: Int) {
        val current = selectedCell ?: Pair(0, 0)
        val newR = (current.first + deltaRow).coerceIn(0, engine.maxRow - 1)
        val newC = (current.second + deltaCol).coerceIn(0, engine.maxCol - 1)
        selectedCell = Pair(newR, newC)
        
        val headerW = if (settings.showRowNumbers) 44f * density else 0f
        val headerH = 32f * density
        val cellLeftUnscaled = engine.getColOffsetPx(newC)
        val cellRightUnscaled = cellLeftUnscaled + engine.getColWidthPx(newC)
        val cellTopUnscaled = engine.getRowOffsetPx(newR)
        val cellBottomUnscaled = cellTopUnscaled + engine.getRowHeightPx(newR)

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
        
        viewModel.speakCell(newR, newC)
        triggerHaptic()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { 
                    Column(
                        modifier = Modifier
                            .clickable {
                                renameText = fileName
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
                        if (userZoom != 1.0f) {
                            Text(
                                text = "Zoom: ${(userZoom * 100).roundToInt()}%",
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
                        enabled = engine.canUndo,
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("top_bar_undo_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Undo",
                            tint = if (engine.canUndo) GreenPrimary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                        )
                    }

                    // Redo Button
                    IconButton(
                        onClick = { viewModel.redo() },
                        enabled = engine.canRedo,
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("top_bar_redo_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Redo",
                            tint = if (engine.canRedo) GreenPrimary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                        )
                    }

                    // Save document button
                    IconButton(
                        onClick = { showSaveConfirmDialog = true },
                        modifier = Modifier.testTag("top_bar_save_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Save,
                            contentDescription = "Save Spreadsheet",
                            tint = GreenPrimary
                        )
                    }

                    // Quick Reset Zoom chip if zoomed
                    if (userZoom != 1.0f) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = GreenPrimary.copy(alpha = 0.15f),
                            modifier = Modifier
                                .padding(end = 4.dp)
                                .clickable { applyZoom(1.0f) }
                                .testTag("top_bar_zoom_reset")
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Icon(
                                    Icons.Default.Refresh,
                                    contentDescription = null,
                                    tint = GreenPrimary,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(Modifier.width(2.dp))
                                Text(
                                    text = "Reset ${(userZoom * 100).roundToInt()}%",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = GreenPrimary
                                )
                            }
                        }
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
                            onDismissRequest = { showOptionsMenu = false },
                            modifier = Modifier.widthIn(min = 280.dp)
                        ) {
                            DropdownMenuItem(
                                text = {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(20.dp), tint = GreenPrimary)
                                        Spacer(Modifier.width(12.dp))
                                        Text("Save Document", fontWeight = FontWeight.SemiBold)
                                    }
                                },
                                onClick = {
                                    showOptionsMenu = false
                                    showSaveConfirmDialog = true
                                },
                                modifier = Modifier.testTag("menu_save_document")
                            )

                            DropdownMenuItem(
                                text = {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(20.dp))
                                        Spacer(Modifier.width(12.dp))
                                        Text("Rename Document")
                                    }
                                },
                                onClick = {
                                    showOptionsMenu = false
                                    renameText = fileName
                                    showRenameDialog = true
                                },
                                modifier = Modifier.testTag("menu_rename_document")
                            )

                            DropdownMenuItem(
                                text = {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(20.dp), tint = GreenPrimary)
                                        Spacer(Modifier.width(12.dp))
                                        Text("New Blank Spreadsheet")
                                    }
                                },
                                onClick = {
                                    showOptionsMenu = false
                                    viewModel.openNewSpreadsheet()
                                },
                                modifier = Modifier.testTag("menu_new_spreadsheet")
                            )

                            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                            DropdownMenuItem(
                                text = {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text("Zoom Controls", fontWeight = FontWeight.SemiBold)
                                        Surface(
                                            shape = RoundedCornerShape(6.dp),
                                            color = GreenPrimary.copy(alpha = 0.15f)
                                        ) {
                                            Text(
                                                text = "${(userZoom * 100).roundToInt()}%",
                                                color = GreenPrimary,
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
                                        tint = GreenPrimary
                                    )
                                },
                                onClick = {
                                    showOptionsMenu = false
                                    showZoomControlsMenu = true
                                },
                                modifier = Modifier.testTag("menu_zoom_controls")
                            )

                            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                            // 1. Column Header Announcement Order
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
                                                fontWeight = FontWeight.SemiBold,
                                                fontSize = 14.sp
                                            )
                                            Text(
                                                text = if (settings.announceColumnFirst) "Reads column header, then cell" else "Reads cell, then column header",
                                                fontSize = 12.sp,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                        Switch(
                                            checked = settings.announceColumnFirst,
                                            onCheckedChange = { viewModel.toggleAnnounceColumnFirst() }
                                        )
                                    }
                                },
                                onClick = { viewModel.toggleAnnounceColumnFirst() },
                                modifier = Modifier.testTag("menu_toggle_column_first")
                            )

                            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                            // 2. Show / Hide Left Side Numbers
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
                                                fontWeight = FontWeight.SemiBold,
                                                fontSize = 14.sp
                                            )
                                            Text(
                                                text = if (settings.showRowNumbers) "Row numbers (1, 2, 3...) shown" else "Row numbers hidden",
                                                fontSize = 12.sp,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                        Switch(
                                            checked = settings.showRowNumbers,
                                            onCheckedChange = { viewModel.toggleShowRowNumbers() }
                                        )
                                    }
                                },
                                onClick = { viewModel.toggleShowRowNumbers() },
                                modifier = Modifier.testTag("menu_toggle_row_numbers")
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
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
                    val curCol = selectedCell?.second ?: 0
                    val cellLetter = engine.getColumnName(curCol)
                    val cellHeader = engine.getColumnHeaderName(curCol)
                    val cellCoord = "$cellLetter${curRow + 1}"
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
                                    onClickLabel = "Edit cell $cellCoord"
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
                                    color = GreenPrimary
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
                                    if (curRow > 0 && cellHeader.isNotEmpty() && !cellHeader.startsWith("Column ")) {
                                        Text(
                                            text = cellHeader,
                                            fontWeight = FontWeight.SemiBold,
                                            fontSize = 11.sp,
                                            color = GreenPrimary,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                    Text(
                                        text = if (cellVal.isEmpty()) "(empty cell - tap to edit)" else cellVal,
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
                                    tint = GreenPrimary.copy(alpha = 0.6f)
                                )
                            }
                        }
                        
                        Row(
                            modifier = Modifier.horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            FilledTonalIconButton(
                                onClick = { viewModel.copyCell(context, curRow, curCol) },
                                modifier = Modifier.size(38.dp).testTag("action_copy")
                            ) {
                                Icon(Icons.Default.ContentCopy, contentDescription = "Copy Cell", modifier = Modifier.size(18.dp))
                            }
                            FilledTonalIconButton(
                                onClick = { viewModel.pasteCell(context, curRow, curCol) },
                                modifier = Modifier.size(38.dp).testTag("action_paste")
                            ) {
                                Icon(Icons.Default.ContentPaste, contentDescription = "Paste Cell", modifier = Modifier.size(18.dp))
                            }
                            val deleteMode = settings.deleteMode
                            val deleteIcon = when (deleteMode) {
                                DeleteMode.CLEAR_CELL -> Icons.Default.Delete
                                DeleteMode.CLEAR_ROW -> Icons.Default.DeleteSweep
                                DeleteMode.CLEAR_COLUMN -> Icons.Default.DeleteOutline
                            }
                            val deleteDesc = when (deleteMode) {
                                DeleteMode.CLEAR_CELL -> "Clear Cell"
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
                                                DeleteMode.CLEAR_CELL -> viewModel.deleteCell(curRow, curCol)
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
                                onClick = { actionMenuTarget = ActionMenuTarget.General(curCol, curRow) },
                                modifier = Modifier.size(38.dp).testTag("action_column_menu")
                            ) {
                                Icon(Icons.Default.MoreVert, contentDescription = "Column Options", modifier = Modifier.size(18.dp))
                            }
                        }
                    }

                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f),
                        thickness = 0.5.dp
                    )

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
                                containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                                contentColor = MaterialTheme.colorScheme.onTertiaryContainer
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
                                containerColor = MaterialTheme.colorScheme.primaryContainer,
                                contentColor = MaterialTheme.colorScheme.onPrimaryContainer
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
                                containerColor = MaterialTheme.colorScheme.primaryContainer,
                                contentColor = MaterialTheme.colorScheme.onPrimaryContainer
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
                                containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                                contentColor = MaterialTheme.colorScheme.onTertiaryContainer
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

                            var longPressJob: Job? = coroutineScope.launch {
                                delay(viewConfiguration.longPressTimeoutMillis)
                                if (!isDragging && !isMultiTouch && !longPressTriggered) {
                                    longPressTriggered = true
                                    val headerW = if (showRowNumbers) 44f * density else 0f
                                    val headerH = 32f * density
                                    val headerTouchH = headerH + 10f * density
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
                                }
                            }

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
                                        (oldZoom * smoothedZoom).coerceIn(0.7f, 3.0f)
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

                                    // On a 2 -> 1 finger transition (one finger lifted mid-pinch),
                                    // reset lastPos and startPos cleanly on this frame instead of treating the previous
                                    // centroid as a new drag start — preventing the automatic zoom-out/jump glitch.
                                    if (previousPointerCount >= 2) {
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

                                    if (!isMultiTouch && !isDragging) {
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
                                                    selectedCell = Pair(0, c)
                                                    showColumnMenu = c
                                                    triggerHaptic()
                                                } else {
                                                    lastTapTimestamp = now
                                                    lastTapPosition = startPos
                                                    selectedCell = Pair(0, c)
                                                    viewModel.speakCell(0, c)
                                                    triggerHaptic()
                                                }
                                            } else if (screenX >= headerW && screenY > headerTouchH) {
                                                // Cell grid tapped
                                                val gridX = (screenX - headerW - curPan.x) / userZoom
                                                val gridY = (screenY - headerH - curPan.y) / userZoom
                                                val r = engine.getRowAt(gridY).coerceIn(0, engine.maxRow - 1)
                                                val c = engine.getColAt(gridX).coerceIn(0, engine.maxCol - 1)

                                                if (isDoubleTap) {
                                                    // Keep double-tap strictly for editing cell — do NOT double-tap-to-zoom
                                                    lastTapTimestamp = 0L
                                                    selectedCell = Pair(r, c)
                                                    editingCell = Pair(r, c)
                                                } else {
                                                    lastTapTimestamp = now
                                                    lastTapPosition = startPos
                                                    selectedCell = Pair(r, c)
                                                    viewModel.speakCell(r, c)
                                                    triggerHaptic()
                                                }
                                            } else if (screenX < headerW && screenY > headerH) {
                                                // Tapped row header
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

                            var c = startCol
                            while (c < engine.maxCol) {
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
                                val cellColorInt = engine.getCellColor(r, c)
                                val rowColorInt = engine.getRowColor(r)
                                val colColorInt = engine.getColumnColor(c)
                                val customBgColor = when {
                                    cellColorInt != null -> Color(cellColorInt)
                                    rowColorInt != null -> Color(rowColorInt)
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

                                // Cell Selection Highlight (Overlay stays visible on top)
                                if (isSelected) {
                                    drawRect(
                                        color = highlightFill,
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
                                        val isHeaderRow = r == 0
                                        val isWrapped = engine.isWrapEnabled(c)
                                        val cellTextColorInt = engine.getCellTextColor(r, c)
                                        val rowTextColorInt = engine.getRowTextColor(r)
                                        val colTextColorInt = engine.getColumnTextColor(c)
                                        val customTextColor = when {
                                            cellTextColorInt != null -> Color(cellTextColorInt)
                                            rowTextColorInt != null -> Color(rowTextColorInt)
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
                                        color = GreenPrimary,
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
                    drawRect(
                        color = headerBg,
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

                        val isColSelected = selectedCell?.second == hc
                        val effectiveHeaderStyle = if (isColSelected) headerStyleSelected else headerStyleNormal

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

                        drawRect(
                            color = gridColor,
                            topLeft = Offset(colLeft, 0f),
                            size = Size(colWidth, headerH),
                            style = Stroke(width = 1f * density)
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
                            val headerColor = if (isRowSelected) GreenPrimary else headerStyle.color

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
            }
        }
    }
    
    // Edit Dialog
    editingCell?.let { (r, c) ->
        val formulaOrValue = engine.getCellFormulaOrValue(r, c)
        var textFieldValue by remember(r, c) {
            mutableStateOf(TextFieldValue(formulaOrValue, selection = TextRange(formulaOrValue.length)))
        }
        var isFormulaExpanded by remember(r, c) {
            mutableStateOf(formulaOrValue.startsWith("="))
        }
        val headerName = engine.getColumnHeaderName(c)
        val cellTitle = if (r > 0 && headerName.isNotEmpty() && !headerName.startsWith("Column ")) {
            "Edit ${engine.getColumnName(c)}${r + 1} ($headerName)"
        } else {
            "Edit ${engine.getColumnName(c)}${r + 1}"
        }

        data class FormulaChipItem(
            val name: String,
            val template: String,
            val cursorOffset: Int,
            val description: String
        )

        val formulaOptions = remember {
            listOf(
                FormulaChipItem("SUM", "=SUM(:)", 5, "Insert SUM formula: calculate sum of range values"),
                FormulaChipItem("AVERAGE", "=AVERAGE(:)", 9, "Insert AVERAGE formula: calculate average of range values"),
                FormulaChipItem("COUNT", "=COUNT(:)", 7, "Insert COUNT formula: count numeric values in range"),
                FormulaChipItem("MIN", "=MIN(:)", 5, "Insert MIN formula: calculate minimum value in range"),
                FormulaChipItem("MAX", "=MAX(:)", 5, "Insert MAX formula: calculate maximum value in range"),
                FormulaChipItem("SORT", "=SORT(:)", 6, "Insert SORT formula: sort range values ascending or descending"),
                FormulaChipItem("IF", "=IF(, , )", 4, "Insert IF conditional formula"),
                FormulaChipItem("SUMIF", "=SUMIF(, )", 7, "Insert SUMIF conditional sum formula"),
                FormulaChipItem("COUNTIF", "=COUNTIF(, )", 9, "Insert COUNTIF conditional count formula"),
                FormulaChipItem("VLOOKUP", "=VLOOKUP(, , )", 9, "Insert VLOOKUP value lookup formula"),
                FormulaChipItem("XLOOKUP", "=XLOOKUP(, , )", 9, "Insert XLOOKUP advanced lookup formula")
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
                            focusedBorderColor = GreenPrimary,
                            focusedLabelColor = GreenPrimary
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
                                    tint = GreenPrimary,
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
                                text = "Available formulas (tap to insert at cursor):",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            // Tappable formula chips
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState())
                                    .padding(vertical = 2.dp),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                formulaOptions.forEach { fItem ->
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
                                            containerColor = GreenPrimary.copy(alpha = 0.12f),
                                            labelColor = GreenPrimary
                                        ),
                                        border = SuggestionChipDefaults.suggestionChipBorder(
                                            enabled = true,
                                            borderColor = GreenPrimary.copy(alpha = 0.4f)
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
                    colors = ButtonDefaults.buttonColors(containerColor = GreenPrimary),
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
    
    // Tabbed Action Menu Bottom Sheet
    actionMenuTarget?.let { target ->
        val (r, c) = when (target) {
            is ActionMenuTarget.Cell -> Pair(target.r, target.c)
            is ActionMenuTarget.Column -> Pair(selectedCell?.first ?: 0, target.c)
            is ActionMenuTarget.Row -> Pair(target.r, selectedCell?.second ?: 0)
            is ActionMenuTarget.General -> Pair(target.r, target.c)
        }
        val initialTab = when (target) {
            is ActionMenuTarget.Cell -> 0
            is ActionMenuTarget.Column -> 2
            is ActionMenuTarget.Row -> 3
            is ActionMenuTarget.General -> settings.lastActionMenuTab.coerceIn(0, 3)
        }

        ActionMenuBottomSheet(
            initialTab = initialTab,
            targetCell = Pair(r, c),
            engine = engine,
            viewModel = viewModel,
            onTabSelected = { tabIdx ->
                viewModel.updateLastActionMenuTab(tabIdx)
            },
            onDismiss = { actionMenuTarget = null },
            onEditCell = { cellPair -> editingCell = cellPair },
            onOpenColorPicker = { colorTarget, tab ->
                colorPickerState = ColorPickerState(colorTarget, tab)
            },
            onConfirmClearCol = { col -> clearColConfirm = col },
            onConfirmClearRow = { row -> clearRowConfirm = row },
            onConfirmDeleteRow = { row -> deleteRowConfirm = row }
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
                }
            },
            onSetTextColor = { color ->
                when (val target = state.target) {
                    is ColorTarget.Cell -> viewModel.setCellTextColor(target.r, target.c, color)
                    is ColorTarget.Column -> viewModel.setColumnTextColor(target.c, color)
                    is ColorTarget.Row -> viewModel.setRowTextColor(target.r, color)
                }
            },
            onDismiss = { colorPickerState = null }
        )
    }

    if (showSaveConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showSaveConfirmDialog = false },
            title = { Text("Update File") },
            text = { Text("Do you want to update the file?") },
            confirmButton = {
                Button(
                    onClick = {
                        showSaveConfirmDialog = false
                        viewModel.saveDocument()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = GreenPrimary),
                    modifier = Modifier.testTag("confirm_save_yes_button")
                ) {
                    Text("Yes")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showSaveConfirmDialog = false },
                    modifier = Modifier.testTag("confirm_save_cancel_button")
                ) {
                    Text("Cancel")
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
                    colors = ButtonDefaults.buttonColors(containerColor = GreenPrimary),
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
                    colors = ButtonDefaults.buttonColors(containerColor = GreenPrimary),
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
                    colors = ButtonDefaults.buttonColors(containerColor = GreenPrimary),
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
                        DeleteMode.CLEAR_CELL to "Clear cell",
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
                            color = GreenPrimary,
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
                            Text("Downloaded (works offline)", fontWeight = FontWeight.Bold, color = GreenPrimary, fontSize = 12.sp, modifier = Modifier.padding(vertical = 4.dp))
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
                            Text("Online", fontWeight = FontWeight.Bold, color = GreenPrimary, fontSize = 12.sp, modifier = Modifier.padding(vertical = 4.dp))
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
            title = { Text("Rename Spreadsheet") },
            text = {
                OutlinedTextField(
                    value = renameText,
                    onValueChange = { renameText = it },
                    label = { Text("Spreadsheet Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("rename_document_input")
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (renameText.isNotBlank()) {
                            viewModel.renameDocument(renameText)
                        }
                        showRenameDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = GreenPrimary),
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
                    tint = GreenPrimary,
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
                        color = GreenPrimary.copy(alpha = 0.12f)
                    ) {
                        Text(
                            text = "${(userZoom * 100).roundToInt()}%",
                            color = GreenPrimary,
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
                                applyZoom((userZoom - 0.15f).coerceIn(0.7f, 3.0f))
                            },
                            modifier = Modifier.testTag("dialog_zoom_out_button")
                        ) {
                            Icon(Icons.Default.Remove, contentDescription = "Zoom Out")
                        }

                        Slider(
                            value = userZoom,
                            onValueChange = { applyZoom(it) },
                            valueRange = 0.7f..3.0f,
                            steps = 22,
                            modifier = Modifier
                                .weight(1f)
                                .padding(horizontal = 8.dp)
                                .testTag("zoom_slider"),
                            colors = SliderDefaults.colors(
                                thumbColor = GreenPrimary,
                                activeTrackColor = GreenPrimary
                            )
                        )

                        FilledTonalIconButton(
                            onClick = {
                                applyZoom((userZoom + 0.15f).coerceIn(0.7f, 3.0f))
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
                            listOf(0.75f, 1.0f, 1.25f, 1.5f, 2.0f).forEach { preset ->
                                val isCurrent = (userZoom * 100).roundToInt() == (preset * 100).roundToInt()
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = if (isCurrent) GreenPrimary else MaterialTheme.colorScheme.surfaceVariant,
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

                    // Quick Reset to 100% button
                    OutlinedButton(
                        onClick = { applyZoom(1.0f) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("dialog_zoom_reset_button")
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Reset to 100%")
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = { showZoomControlsMenu = false },
                    colors = ButtonDefaults.buttonColors(containerColor = GreenPrimary),
                    modifier = Modifier.testTag("dialog_zoom_done_button")
                ) {
                    Text("Done")
                }
            }
        )
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
    var activeTab by remember { mutableStateOf(state.initialTab) }

    val currentBgColor = when (val target = state.target) {
        is ColorTarget.Cell -> engine.getCellColor(target.r, target.c)
        is ColorTarget.Column -> engine.getColumnColor(target.c)
        is ColorTarget.Row -> engine.getRowColor(target.r)
    }

    val currentTextColor = when (val target = state.target) {
        is ColorTarget.Cell -> engine.getCellTextColor(target.r, target.c)
        is ColorTarget.Column -> engine.getColumnTextColor(target.c)
        is ColorTarget.Row -> engine.getRowTextColor(target.r)
    }

    var workingBgColor by remember { mutableStateOf(currentBgColor ?: 0xFFFFFFFF.toInt()) }
    var workingTextColor by remember { mutableStateOf(currentTextColor ?: 0xFF000000.toInt()) }
    var brightnessMultiplier by remember { mutableFloatStateOf(1.0f) }

    val activeColor = if (activeTab == ColorPickerTab.BACKGROUND) workingBgColor else workingTextColor

    var baseRed by remember(activeTab, activeColor) { mutableFloatStateOf(((activeColor shr 16) and 0xFF).toFloat()) }
    var baseGreen by remember(activeTab, activeColor) { mutableFloatStateOf(((activeColor shr 8) and 0xFF).toFloat()) }
    var baseBlue by remember(activeTab, activeColor) { mutableFloatStateOf((activeColor and 0xFF).toFloat()) }

    val finalColorInt = remember(baseRed, baseGreen, baseBlue, brightnessMultiplier) {
        val r = (baseRed * brightnessMultiplier).roundToInt().coerceIn(0, 255)
        val g = (baseGreen * brightnessMultiplier).roundToInt().coerceIn(0, 255)
        val b = (baseBlue * brightnessMultiplier).roundToInt().coerceIn(0, 255)
        (0xFF shl 24) or (r shl 16) or (g shl 8) or b
    }

    val titleTarget = when (val target = state.target) {
        is ColorTarget.Cell -> "${engine.getColumnName(target.c)}${target.r + 1}"
        is ColorTarget.Column -> "Column ${engine.getColumnName(target.c)}"
        is ColorTarget.Row -> "Row ${target.r + 1}"
    }

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
                    contentColor = GreenPrimary
                ) {
                    Tab(
                        selected = activeTab == ColorPickerTab.BACKGROUND,
                        onClick = {
                            activeTab = ColorPickerTab.BACKGROUND
                            brightnessMultiplier = 1.0f
                        },
                        text = { Text("Background", fontWeight = FontWeight.Bold) },
                        modifier = Modifier.testTag("tab_background_color")
                    )
                    Tab(
                        selected = activeTab == ColorPickerTab.TEXT,
                        onClick = {
                            activeTab = ColorPickerTab.TEXT
                            brightnessMultiplier = 1.0f
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
                            .background(Color(finalColorInt))
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
                            text = String.format("#%06X", finalColorInt and 0xFFFFFF),
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
                                    baseRed = ((swatch.colorInt shr 16) and 0xFF).toFloat()
                                    baseGreen = ((swatch.colorInt shr 8) and 0xFF).toFloat()
                                    baseBlue = (swatch.colorInt and 0xFF).toFloat()
                                    brightnessMultiplier = 1.0f
                                    if (activeTab == ColorPickerTab.BACKGROUND) {
                                        workingBgColor = swatch.colorInt
                                        onSetBgColor(swatch.colorInt)
                                    } else {
                                        workingTextColor = swatch.colorInt
                                        onSetTextColor(swatch.colorInt)
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

                // Canvas color picker for Hue & Saturation
                val hsv = FloatArray(3)
                android.graphics.Color.colorToHSV(finalColorInt, hsv)
                var hue by remember(activeTab) { mutableFloatStateOf(hsv[0]) }
                var saturation by remember(activeTab) { mutableFloatStateOf(hsv[1]) }
                var value by remember(activeTab) { mutableFloatStateOf(hsv[2]) }

                val canvasColorInt = remember(hue, saturation, value) {
                    android.graphics.Color.HSVToColor(floatArrayOf(hue, saturation, value)) or (0xFF shl 24)
                }

                LaunchedEffect(canvasColorInt) {
                    if (activeTab == ColorPickerTab.BACKGROUND) {
                        workingBgColor = canvasColorInt
                        onSetBgColor(canvasColorInt)
                    } else {
                        workingTextColor = canvasColorInt
                        onSetTextColor(canvasColorInt)
                    }
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(160.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .pointerInput(Unit) {
                            detectTapGestures { offset ->
                                hue = (offset.x / size.width).coerceIn(0f, 1f) * 360f
                                saturation = (1f - (offset.y / size.height)).coerceIn(0f, 1f)
                            }
                        }
                        .pointerInput(Unit) {
                            detectDragGestures { change, _ ->
                                val offset = change.position
                                hue = (offset.x / size.width).coerceIn(0f, 1f) * 360f
                                saturation = (1f - (offset.y / size.height)).coerceIn(0f, 1f)
                            }
                        }
                ) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val width = size.width
                        val height = size.height
                        val stepsX = 50
                        val stepsY = 25
                        val stepW = width / stepsX
                        val stepH = height / stepsY

                        for (x in 0 until stepsX) {
                            for (y in 0 until stepsY) {
                                val h = (x.toFloat() / stepsX) * 360f
                                val s = 1f - (y.toFloat() / stepsY)
                                val cInt = android.graphics.Color.HSVToColor(floatArrayOf(h, s, value))
                                drawRect(
                                    color = Color(cInt),
                                    topLeft = Offset(x * stepW, y * stepH),
                                    size = Size(stepW + 1f, stepH + 1f)
                                )
                            }
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
                        Text("Brightness (Value)", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = GreenPrimary)
                        Text("${(value * 100).roundToInt()}%", fontSize = 12.sp, fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace, fontWeight = FontWeight.Bold)
                    }
                    Slider(
                        value = value,
                        onValueChange = { value = it },
                        valueRange = 0f..1f,
                        colors = SliderDefaults.colors(thumbColor = GreenPrimary, activeTrackColor = GreenPrimary),
                        modifier = Modifier.testTag("graphical_brightness_slider")
                    )
                }

                // Reset / Clear Button
                OutlinedButton(
                    onClick = {
                        if (activeTab == ColorPickerTab.BACKGROUND) {
                            onSetBgColor(null)
                        } else {
                            onSetTextColor(null)
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
                onClick = onDismiss,
                colors = ButtonDefaults.buttonColors(containerColor = GreenPrimary),
                modifier = Modifier.testTag("dialog_color_done_button")
            ) {
                Text("Done")
            }
        }
    )
}
