package com.example.ui.screens

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.GreenPrimary
import com.example.viewmodel.MainViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SpreadsheetScreen(
    viewModel: MainViewModel,
    onNavigateBack: () -> Unit
) {
    val fileName by viewModel.currentFileName.collectAsState()
    val settings by viewModel.appSettings.collectAsState()
    val refreshTrigger by viewModel.gridRefreshTrigger.collectAsState()
    val engine = viewModel.spreadsheetEngine
    val context = LocalContext.current
    val density = LocalDensity.current.density
    val coroutineScope = rememberCoroutineScope()
    
    var selectedCell by remember { mutableStateOf<Pair<Int, Int>?>(Pair(0, 0)) }
    var editingCell by remember { mutableStateOf<Pair<Int, Int>?>(null) }
    var showMenuForCell by remember { mutableStateOf<Pair<Int, Int>?>(null) }
    var showOptionsMenu by remember { mutableStateOf(false) }

    // Scroll state with smooth animation support
    val animScrollX = remember { Animatable(0f) }
    val animScrollY = remember { Animatable(0f) }
    var scrollJob by remember { mutableStateOf<Job?>(null) }

    // Pure image-like two-finger zoom
    var userZoom by remember { mutableFloatStateOf(1.0f) }
    var viewportSize by remember { mutableStateOf(IntSize.Zero) }
    
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

    val textMeasurer = rememberTextMeasurer()
    val textStyle = MaterialTheme.typography.bodyMedium.copy(
        color = MaterialTheme.colorScheme.onBackground,
        fontSize = 13.sp
    )
    val headerStyle = MaterialTheme.typography.labelMedium.copy(
        color = MaterialTheme.colorScheme.onSurface,
        fontWeight = FontWeight.Bold,
        fontSize = 11.sp
    )

    val gridColor = if (settings.highContrastGrid) Color(0xFF888888) else Color(0xFF444444)
    val headerBg = MaterialTheme.colorScheme.surface
    val highlightFill = GreenPrimary.copy(alpha = 0.22f)

    // Ensure layout cache is updated
    LaunchedEffect(refreshTrigger, density) {
        engine.updateLayoutIfNeeded(density)
    }

    // Smooth navigation with clear landing feedback
    fun moveSelection(deltaRow: Int, deltaCol: Int) {
        val current = selectedCell ?: Pair(0, 0)
        val newR = (current.first + deltaRow).coerceIn(0, engine.maxRow - 1)
        val newC = (current.second + deltaCol).coerceIn(0, engine.maxCol - 1)
        selectedCell = Pair(newR, newC)
        
        val headerW = if (settings.showRowNumbers) 44f * density else 0f
        val headerH = 32f * density
        val cellLeft = engine.getColOffsetPx(newC)
        val cellRight = cellLeft + engine.getColWidthPx(newC)
        val cellTop = engine.getRowOffsetPx(newR)
        val cellBottom = cellTop + engine.getRowHeightPx(newR)

        val canvasW = if (viewportSize.width > 0) viewportSize.width.toFloat() / userZoom else 1000f
        val canvasH = if (viewportSize.height > 0) viewportSize.height.toFloat() / userZoom else 1500f
        val visibleW = (canvasW - headerW).coerceAtLeast(100f)
        val visibleH = (canvasH - headerH).coerceAtLeast(100f)
        
        // 40dp margin so cell lands comfortably in view without edge clipping
        val margin = 40f * density
        var targetX = animScrollX.value
        var targetY = animScrollY.value
        
        if (cellLeft < targetX + margin) {
            targetX = (cellLeft - margin).coerceAtLeast(0f)
        } else if (cellRight > targetX + visibleW - margin) {
            targetX = (cellRight - visibleW + margin).coerceAtLeast(0f)
        }
        
        if (cellTop < targetY + margin) {
            targetY = (cellTop - margin).coerceAtLeast(0f)
        } else if (cellBottom > targetY + visibleH - margin) {
            targetY = (cellBottom - visibleH + margin).coerceAtLeast(0f)
        }
        
        scrollJob?.cancel()
        scrollJob = coroutineScope.launch {
            launch {
                animScrollX.animateTo(
                    targetX,
                    animationSpec = tween(durationMillis = 180, easing = FastOutSlowInEasing)
                )
            }
            launch {
                animScrollY.animateTo(
                    targetY,
                    animationSpec = tween(durationMillis = 180, easing = FastOutSlowInEasing)
                )
            }
        }
        
        viewModel.speakCell(newR, newC)
        triggerHaptic()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { 
                    Column {
                        Text(fileName, maxLines = 1, overflow = TextOverflow.Ellipsis)
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
                    // Quick Reset Zoom chip if zoomed
                    if (userZoom != 1.0f) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = GreenPrimary.copy(alpha = 0.15f),
                            modifier = Modifier
                                .padding(end = 4.dp)
                                .clickable { userZoom = 1.0f }
                        ) {
                            Text(
                                text = "Reset 100%",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = GreenPrimary,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
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
                            // Zoom options
                            DropdownMenuItem(
                                text = {
                                    Text("Reset Zoom to 100%", fontWeight = FontWeight.Medium)
                                },
                                onClick = {
                                    userZoom = 1.0f
                                    showOptionsMenu = false
                                }
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
                            modifier = Modifier.weight(1f).height(44.dp).testTag("nav_left")
                        ) {
                            Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "Left", modifier = Modifier.size(22.dp))
                            Spacer(Modifier.width(2.dp))
                            Text("Left", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                        }

                        FilledTonalButton(
                            onClick = { moveSelection(deltaRow = -1, deltaCol = 0) },
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp),
                            modifier = Modifier.weight(1f).height(44.dp).testTag("nav_up")
                        ) {
                            Icon(Icons.Default.KeyboardArrowUp, contentDescription = "Up", modifier = Modifier.size(22.dp))
                            Spacer(Modifier.width(2.dp))
                            Text("Up", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                        }

                        FilledTonalButton(
                            onClick = { moveSelection(deltaRow = 1, deltaCol = 0) },
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp),
                            modifier = Modifier.weight(1f).height(44.dp).testTag("nav_down")
                        ) {
                            Icon(Icons.Default.KeyboardArrowDown, contentDescription = "Down", modifier = Modifier.size(22.dp))
                            Spacer(Modifier.width(2.dp))
                            Text("Down", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                        }

                        FilledTonalButton(
                            onClick = { moveSelection(deltaRow = 0, deltaCol = 1) },
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp),
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
                    // Tap gestures mapped cleanly to zoomed space
                    .pointerInput(userZoom, showRowNumbers) {
                        detectTapGestures(
                            onTap = { offset ->
                                val virtualX = offset.x / userZoom
                                val virtualY = offset.y / userZoom
                                val headerW = if (showRowNumbers) 44f * density else 0f
                                val headerH = 32f * density
                                
                                if (virtualX >= headerW && virtualY > headerH) {
                                    val gridX = virtualX - headerW + animScrollX.value
                                    val gridY = virtualY - headerH + animScrollY.value
                                    
                                    val r = engine.getRowAt(gridY).coerceIn(0, engine.maxRow - 1)
                                    val c = engine.getColAt(gridX).coerceIn(0, engine.maxCol - 1)
                                    
                                    selectedCell = Pair(r, c)
                                    viewModel.speakCell(r, c)
                                    triggerHaptic()
                                }
                            },
                            onDoubleTap = { offset ->
                                val virtualX = offset.x / userZoom
                                val virtualY = offset.y / userZoom
                                val headerW = if (showRowNumbers) 44f * density else 0f
                                val headerH = 32f * density
                                if (virtualX >= headerW && virtualY > headerH) {
                                    val gridX = virtualX - headerW + animScrollX.value
                                    val gridY = virtualY - headerH + animScrollY.value
                                    val r = engine.getRowAt(gridY).coerceIn(0, engine.maxRow - 1)
                                    val c = engine.getColAt(gridX).coerceIn(0, engine.maxCol - 1)
                                    selectedCell = Pair(r, c)
                                    editingCell = Pair(r, c)
                                }
                            },
                            onLongPress = { offset ->
                                val virtualX = offset.x / userZoom
                                val virtualY = offset.y / userZoom
                                val headerW = if (showRowNumbers) 44f * density else 0f
                                val headerH = 32f * density
                                if (virtualX >= headerW && virtualY > headerH) {
                                    val gridX = virtualX - headerW + animScrollX.value
                                    val gridY = virtualY - headerH + animScrollY.value
                                    val r = engine.getRowAt(gridY).coerceIn(0, engine.maxRow - 1)
                                    val c = engine.getColAt(gridX).coerceIn(0, engine.maxCol - 1)
                                    selectedCell = Pair(r, c)
                                    showMenuForCell = Pair(r, c)
                                }
                            }
                        )
                    }
                    // Pure image-like two-finger pinch-to-zoom and pan
                    .pointerInput(Unit) {
                        detectTransformGestures { centroid, pan, zoom, _ ->
                            scrollJob?.cancel()
                            if (zoom != 1.0f) {
                                val oldZoom = userZoom
                                val newZoom = (userZoom * zoom).coerceIn(0.7f, 3.0f)
                                val zoomRatio = newZoom / oldZoom
                                userZoom = newZoom
                                
                                val targetX = ((animScrollX.value + centroid.x / oldZoom) * zoomRatio - centroid.x / newZoom).coerceAtLeast(0f)
                                val targetY = ((animScrollY.value + centroid.y / oldZoom) * zoomRatio - centroid.y / newZoom).coerceAtLeast(0f)
                                coroutineScope.launch {
                                    animScrollX.snapTo(targetX)
                                    animScrollY.snapTo(targetY)
                                }
                            }
                            if (pan != Offset.Zero) {
                                coroutineScope.launch {
                                    val newX = (animScrollX.value - pan.x / userZoom).coerceAtLeast(0f)
                                    val newY = (animScrollY.value - pan.y / userZoom).coerceAtLeast(0f)
                                    animScrollX.snapTo(newX)
                                    animScrollY.snapTo(newY)
                                }
                            }
                        }
                    }
            ) {
                // Ensure layout cache is populated
                engine.updateLayoutIfNeeded(density)
                val t = refreshTrigger // observe trigger
                
                // Pure graphics scaling: exactly like zooming an image!
                scale(userZoom, userZoom, pivot = Offset.Zero) {
                    val virtualW = size.width / userZoom
                    val virtualH = size.height / userZoom
                    
                    val headerW = if (showRowNumbers) 44f * density else 0f
                    val headerH = 32f * density
                    val pad = 5f * density
                    
                    val curScrollX = animScrollX.value
                    val curScrollY = animScrollY.value
                    
                    val startRow = engine.getRowAt(curScrollY).coerceIn(0, engine.maxRow - 1)
                    val startCol = engine.getColAt(curScrollX).coerceIn(0, engine.maxCol - 1)
                    
                    // --- 1. Draw Grid Lines & Cell Backgrounds & Content ---
                    clipRect(headerW, headerH, virtualW, virtualH) {
                        var r = startRow
                        while (r < engine.maxRow) {
                            val rowTop = headerH + engine.getRowOffsetPx(r) - curScrollY
                            val rowHeight = engine.getRowHeightPx(r)
                            val rowBottom = rowTop + rowHeight
                            
                            // If above visible window, skip
                            if (rowBottom < headerH) {
                                r++
                                continue
                            }
                            // If below visible window, stop rendering rows!
                            if (rowTop > virtualH) {
                                break
                            }
                            
                            var c = startCol
                            while (c < engine.maxCol) {
                                val colLeft = headerW + engine.getColOffsetPx(c) - curScrollX
                                val colWidth = engine.getColWidthPx(c)
                                val colRight = colLeft + colWidth
                                
                                // If left of visible window, skip
                                if (colRight < headerW) {
                                    c++
                                    continue
                                }
                                // If right of visible window, stop rendering columns for this row!
                                if (colLeft > virtualW) {
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
                                    style = Stroke(width = 1f)
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
                                        val effectiveStyle = if (isHeaderRow) {
                                            textStyle.copy(fontWeight = FontWeight.Bold, color = GreenPrimary)
                                        } else {
                                            textStyle
                                        }
                                        val textLayout = textMeasurer.measure(
                                            text = text,
                                            style = effectiveStyle,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
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
                                    // Crisp inner white outline
                                    drawRect(
                                        color = Color.White,
                                        topLeft = Offset(colLeft + 1.5f * density, rowTop + 1.5f * density),
                                        size = Size(colWidth - 3f * density, rowHeight - 3f * density),
                                        style = Stroke(width = 1.5f * density)
                                    )
                                    // Vibrant green outer outline
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
                    
                    // --- 2. Top Column Headers ---
                    clipRect(headerW, 0f, virtualW, headerH) {
                        drawRect(
                            color = headerBg,
                            topLeft = Offset(headerW, 0f),
                            size = Size(virtualW - headerW, headerH)
                        )
                        var hc = startCol
                        while (hc < engine.maxCol) {
                            val colLeft = headerW + engine.getColOffsetPx(hc) - curScrollX
                            val colWidth = engine.getColWidthPx(hc)
                            val colRight = colLeft + colWidth
                            
                            if (colRight < headerW) {
                                hc++
                                continue
                            }
                            if (colLeft > virtualW) {
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
                            val headerColor = if (isColSelected) GreenPrimary else headerStyle.color
                            
                            drawRect(
                                color = gridColor,
                                topLeft = Offset(colLeft, 0f),
                                size = Size(colWidth, headerH),
                                style = Stroke(width = 1f)
                            )
                            
                            val textLayout = textMeasurer.measure(
                                text = displayLabel,
                                style = headerStyle.copy(color = headerColor),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            drawText(
                                textLayoutResult = textLayout,
                                topLeft = Offset(
                                    colLeft + (colWidth - textLayout.size.width) / 2,
                                    (headerH - textLayout.size.height) / 2
                                )
                            )
                            
                            hc++
                        }
                    }
                    
                    // --- 3. Left Row Headers (if enabled) ---
                    if (showRowNumbers && headerW > 0f) {
                        clipRect(0f, headerH, headerW, virtualH) {
                            drawRect(
                                color = headerBg,
                                topLeft = Offset(0f, headerH),
                                size = Size(headerW, virtualH - headerH)
                            )
                            var hr = startRow
                            while (hr < engine.maxRow) {
                                val rowTop = headerH + engine.getRowOffsetPx(hr) - curScrollY
                                val rowHeight = engine.getRowHeightPx(hr)
                                val rowBottom = rowTop + rowHeight
                                
                                if (rowBottom < headerH) {
                                    hr++
                                    continue
                                }
                                if (rowTop > virtualH) {
                                    break
                                }
                                
                                val rowName = "${hr + 1}"
                                val isRowSelected = selectedCell?.first == hr
                                val headerColor = if (isRowSelected) GreenPrimary else headerStyle.color
                                
                                drawRect(
                                    color = gridColor,
                                    topLeft = Offset(0f, rowTop),
                                    size = Size(headerW, rowHeight),
                                    style = Stroke(width = 1f)
                                )
                                
                                val textLayout = textMeasurer.measure(
                                    text = rowName,
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
                        }
                        
                        // Top-Left Corner Box
                        drawRect(
                            color = headerBg,
                            topLeft = Offset(0f, 0f),
                            size = Size(headerW, headerH)
                        )
                        drawRect(
                            color = gridColor,
                            topLeft = Offset(0f, 0f),
                            size = Size(headerW, headerH),
                            style = Stroke(width = 1f)
                        )
                    }
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
}
