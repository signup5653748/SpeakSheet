package com.speaksheet.ui.screens

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
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
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.speaksheet.ui.theme.GreenPrimary
import com.speaksheet.viewmodel.MainViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

class CellLayoutCache(private val textMeasurer: TextMeasurer) {
    private val cellCache = HashMap<Long, CachedCellLayout>()
    private val colHeaderCache = HashMap<Long, CachedHeaderLayout>()
    private val rowHeaderCache = HashMap<Long, CachedHeaderLayout>()

    data class CachedCellLayout(
        val text: String,
        val isHeader: Boolean,
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
        style: TextStyle
    ): TextLayoutResult {
        val key = (r.toLong() shl 32) or (c.toLong() and 0xFFFFFFFFL)
        val cached = cellCache[key]
        if (cached != null && cached.text == text && cached.isHeader == isHeader) {
            return cached.layout
        }
        val layout = textMeasurer.measure(
            text = text,
            style = style,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        cellCache[key] = CachedCellLayout(text, isHeader, layout)
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

@OptIn(ExperimentalMaterial3Api::class)
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
    
    val currentFileUri by viewModel.currentFileUri.collectAsStateWithLifecycle()
    var showRenameDialog by remember { mutableStateOf(false) }
    var renameText by remember { mutableStateOf("") }

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

    // Ensure layout cache is updated for unscaled density and largeTouchMode only (never on zoom)
    LaunchedEffect(refreshTrigger, density, settings.largeTouchMode) {
        engine.updateLayoutIfNeeded(density, settings.largeTouchMode)
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
                    // Save document button
                    IconButton(
                        onClick = { viewModel.saveDocument() },
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
                                    viewModel.saveDocument()
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
                    
                    // Top Row: Prominent Active Cell Card & Actions (Edit, Copy, Paste, Delete, Speak)
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
                                .clickable {
                                    viewModel.speakCell(curRow, curCol)
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
                                        text = if (cellVal.isEmpty()) "(empty cell)" else cellVal,
                                        fontWeight = FontWeight.Medium,
                                        color = if (cellVal.isEmpty()) MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f) else MaterialTheme.colorScheme.onSurface,
                                        fontSize = 12.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                        
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            FilledTonalIconButton(
                                onClick = { editingCell = Pair(curRow, curCol) },
                                modifier = Modifier.size(38.dp).testTag("action_edit")
                            ) {
                                Icon(Icons.Default.Edit, contentDescription = "Edit Cell", modifier = Modifier.size(18.dp))
                            }
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
                            FilledTonalIconButton(
                                onClick = { viewModel.deleteCell(curRow, curCol) },
                                modifier = Modifier.size(38.dp).testTag("action_delete")
                            ) {
                                Icon(Icons.Default.Delete, contentDescription = "Delete Cell", modifier = Modifier.size(18.dp))
                            }
                            FilledTonalIconButton(
                                onClick = { viewModel.speakCell(curRow, curCol) },
                                modifier = Modifier.size(38.dp).testTag("action_speak")
                            ) {
                                Icon(Icons.AutoMirrored.Filled.VolumeUp, contentDescription = "Speak Cell", modifier = Modifier.size(18.dp))
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
                                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                                contentColor = MaterialTheme.colorScheme.onSecondaryContainer
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
                                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                                contentColor = MaterialTheme.colorScheme.onSecondaryContainer
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

                            // Light smoothing state for centroid & zoom
                            var smoothedCentroid: Offset? = null
                            var smoothedZoom = 1.0f

                            while (true) {
                                val event = awaitPointerEvent(PointerEventPass.Main)
                                val activePointers = event.changes.filter { it.pressed }
                                val count = activePointers.size

                                if (count >= 2) {
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

                                        // Do not start single-finger panning immediately on touch-slop breach —
                                        // briefly hold/confirm pointer count first (~60ms or distinct 2.2x slop drag),
                                        // so a second finger landing a few ms after the first isn't misread as a 1-finger drag.
                                        if ((dist > touchSlop && elapsed >= 60L) || dist > touchSlop * 2.2f) {
                                            isDragging = true
                                            scrollJob?.cancel()
                                            lastPos = pointer.position
                                            pointer.consume()
                                        } else if (!isMultiTouch && !longPressTriggered) {
                                            if (elapsed >= viewConfiguration.longPressTimeoutMillis) {
                                                longPressTriggered = true
                                                val headerW = if (showRowNumbers) 44f * density else 0f
                                                val headerH = 32f * density
                                                val screenX = startPos.x
                                                val screenY = startPos.y
                                                val curPan = animPanOffset.value

                                                if (screenX >= headerW && screenY > headerH) {
                                                    val gridX = (screenX - headerW - curPan.x) / userZoom
                                                    val gridY = (screenY - headerH - curPan.y) / userZoom
                                                    val r = engine.getRowAt(gridY).coerceIn(0, engine.maxRow - 1)
                                                    val c = engine.getColAt(gridX).coerceIn(0, engine.maxCol - 1)
                                                    selectedCell = Pair(r, c)
                                                    showMenuForCell = Pair(r, c)
                                                    triggerHaptic()
                                                }
                                                pointer.consume()
                                            }
                                        }
                                    }
                                    previousPointerCount = 1
                                } else {
                                    // All fingers lifted
                                    if (!isMultiTouch && !isDragging && !longPressTriggered) {
                                        val now = System.currentTimeMillis()
                                        val isDoubleTap = (now - lastTapTimestamp < 320) &&
                                                ((startPos - lastTapPosition).getDistance() < 24 * density)

                                        val headerW = if (showRowNumbers) 44f * density else 0f
                                        val headerH = 32f * density
                                        val screenX = startPos.x
                                        val screenY = startPos.y
                                        val curPan = animPanOffset.value

                                        if (screenX >= headerW && screenY > headerH) {
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
                                        } else if (screenY <= headerH && screenX >= headerW) {
                                            // Tapped column header
                                            val gridX = (screenX - headerW - curPan.x) / userZoom
                                            val c = engine.getColAt(gridX).coerceIn(0, engine.maxCol - 1)
                                            selectedCell = Pair(0, c)
                                            viewModel.speakCell(0, c)
                                            triggerHaptic()
                                        } else if (screenX < headerW && screenY > headerH) {
                                            // Tapped row header
                                            val gridY = (screenY - headerH - curPan.y) / userZoom
                                            val r = engine.getRowAt(gridY).coerceIn(0, engine.maxRow - 1)
                                            val currentC = selectedCell?.second ?: 0
                                            selectedCell = Pair(r, currentC)
                                            viewModel.speakCell(r, currentC)
                                            triggerHaptic()
                                        }
                                    }
                                    break
                                }
                            }
                        }
                    }
            ) {
                val t = refreshTrigger // observe trigger
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

                                // Cell Background
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
                                        val effectiveStyle = if (isHeaderRow) headerRowStyle else textStyle
                                        val textLayout = cellLayoutCache.getCellLayout(
                                            r = r,
                                            c = c,
                                            text = text,
                                            isHeader = isHeaderRow,
                                            style = effectiveStyle
                                        )

                                        val isRight = engine.isRightAligned(r, c)
                                        val textX = if (isRight && !isHeaderRow) {
                                            (colRight - pad - textLayout.size.width).coerceAtLeast(colLeft + pad)
                                        } else {
                                            colLeft + pad
                                        }
                                        val textY = rowTop + (rowHeight - textLayout.size.height) / 2

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
                        val headerName = engine.getColumnHeaderName(hc)
                        val displayLabel = if (headerName.isNotEmpty() && !headerName.startsWith("Column ")) {
                            "$colLetter: $headerName"
                        } else {
                            colLetter
                        }

                        val isColSelected = selectedCell?.second == hc
                        val effectiveHeaderStyle = if (isColSelected) headerStyleSelected else headerStyleNormal

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
        var textValue by remember { mutableStateOf(formulaOrValue) }
        val headerName = engine.getColumnHeaderName(c)
        val cellTitle = if (r > 0 && headerName.isNotEmpty() && !headerName.startsWith("Column ")) {
            "Edit ${engine.getColumnName(c)}${r + 1} ($headerName)"
        } else {
            "Edit ${engine.getColumnName(c)}${r + 1}"
        }
        
        AlertDialog(
            onDismissRequest = { editingCell = null },
            title = { Text(cellTitle) },
            text = {
                OutlinedTextField(
                    value = textValue,
                    onValueChange = { textValue = it },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 100.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = GreenPrimary,
                        focusedLabelColor = GreenPrimary
                    )
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.updateCell(r, c, textValue)
                        editingCell = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = GreenPrimary)
                ) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { editingCell = null }) { Text("Cancel") }
            }
        )
    }
    
    // Context Menu Dialog
    showMenuForCell?.let { (r, c) ->
        AlertDialog(
            onDismissRequest = { showMenuForCell = null },
            title = { Text("Cell Options: ${engine.getColumnName(c)}${r + 1}") },
            text = {
                Column {
                    val actions = listOf(
                        "Edit Cell" to { editingCell = Pair(r, c) },
                        "Copy Cell" to { viewModel.copyCell(context, r, c) },
                        "Paste into Cell" to { viewModel.pasteCell(context, r, c) },
                        "Delete Cell Content" to { viewModel.deleteCell(r, c) },
                        "Speak Cell Content" to { viewModel.speakCell(r, c) }
                    )
                    actions.forEach { (label, action) ->
                        Text(
                            text = label,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    showMenuForCell = null
                                    action()
                                }
                                .padding(16.dp),
                            color = GreenPrimary,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showMenuForCell = null }) { Text("Close") }
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
