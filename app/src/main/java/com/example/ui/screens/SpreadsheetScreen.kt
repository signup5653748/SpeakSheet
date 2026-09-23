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
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.input.pointer.PointerEventPass
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
    var lastTapTimestamp by remember { mutableLongStateOf(0L) }
    var lastTapPosition by remember { mutableStateOf(Offset.Zero) }
    
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
    LaunchedEffect(refreshTrigger, density, userZoom, settings.largeTouchMode) {
        engine.updateLayoutIfNeeded(density, userZoom, settings.largeTouchMode)
    }

    fun applyZoomChange(
        oldZoom: Float,
        newZoom: Float,
        pivotX: Float = (viewportSize.width / 2f).coerceAtLeast(0f),
        pivotY: Float = (viewportSize.height / 2f).coerceAtLeast(0f)
    ) {
        val clampedZoom = newZoom.coerceIn(0.7f, 3.0f)
        if (oldZoom == clampedZoom) return
        val zoomRatio = clampedZoom / oldZoom
        userZoom = clampedZoom
        
        val headerW = if (settings.showRowNumbers) 44f * density * oldZoom else 0f
        val headerH = 32f * density * oldZoom
        
        val curX = animScrollX.value
        val curY = animScrollY.value
        val targetX = ((curX + pivotX - headerW) * zoomRatio - (pivotX - headerW * zoomRatio)).coerceAtLeast(0f)
        val targetY = ((curY + pivotY - headerH) * zoomRatio - (pivotY - headerH * zoomRatio)).coerceAtLeast(0f)
        
        scrollJob?.cancel()
        coroutineScope.launch {
            animScrollX.snapTo(targetX)
            animScrollY.snapTo(targetY)
        }
    }

    // Smooth navigation with clear landing feedback
    fun moveSelection(deltaRow: Int, deltaCol: Int) {
        val current = selectedCell ?: Pair(0, 0)
        val newR = (current.first + deltaRow).coerceIn(0, engine.maxRow - 1)
        val newC = (current.second + deltaCol).coerceIn(0, engine.maxCol - 1)
        selectedCell = Pair(newR, newC)
        
        val headerW = if (settings.showRowNumbers) 44f * density * userZoom else 0f
        val headerH = 32f * density * userZoom
        val cellLeft = engine.getColOffsetPx(newC)
        val cellRight = cellLeft + engine.getColWidthPx(newC)
        val cellTop = engine.getRowOffsetPx(newR)
        val cellBottom = cellTop + engine.getRowHeightPx(newR)

        val canvasW = if (viewportSize.width > 0) viewportSize.width.toFloat() else 1000f
        val canvasH = if (viewportSize.height > 0) viewportSize.height.toFloat() else 1500f
        val visibleW = (canvasW - headerW).coerceAtLeast(100f)
        val visibleH = (canvasH - headerH).coerceAtLeast(100f)
        
        // 36dp margin so cell lands comfortably in view without edge clipping
        val margin = 36f * density * userZoom
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
                                .clickable { applyZoomChange(userZoom, 1.0f) }
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
                            // Zoom options under three-dot menu
                            DropdownMenuItem(
                                text = {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text("Reset Zoom (100%)", fontWeight = FontWeight.SemiBold)
                                        Text("${(userZoom * 100).roundToInt()}%", color = GreenPrimary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                    }
                                },
                                leadingIcon = {
                                    Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(20.dp), tint = GreenPrimary)
                                },
                                onClick = {
                                    applyZoomChange(userZoom, 1.0f)
                                    showOptionsMenu = false
                                },
                                modifier = Modifier.testTag("menu_zoom_reset")
                            )

                            DropdownMenuItem(
                                text = {
                                    Text("Zoom In (+20%)", fontWeight = FontWeight.Medium)
                                },
                                leadingIcon = {
                                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(20.dp))
                                },
                                onClick = {
                                    applyZoomChange(userZoom, (userZoom + 0.20f).coerceIn(0.7f, 3.0f))
                                    showOptionsMenu = false
                                },
                                modifier = Modifier.testTag("menu_zoom_in")
                            )

                            DropdownMenuItem(
                                text = {
                                    Text("Zoom Out (-20%)", fontWeight = FontWeight.Medium)
                                },
                                leadingIcon = {
                                    Icon(Icons.Default.Remove, contentDescription = null, modifier = Modifier.size(20.dp))
                                },
                                onClick = {
                                    applyZoomChange(userZoom, (userZoom - 0.20f).coerceIn(0.7f, 3.0f))
                                    showOptionsMenu = false
                                },
                                modifier = Modifier.testTag("menu_zoom_out")
                            )

                            // Preset zoom levels
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 6.dp),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                listOf(0.8f, 1.0f, 1.25f, 1.5f, 2.0f).forEach { preset ->
                                    val isCurrent = (userZoom * 100).roundToInt() == (preset * 100).roundToInt()
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = if (isCurrent) GreenPrimary else MaterialTheme.colorScheme.surfaceVariant,
                                        modifier = Modifier
                                            .weight(1f)
                                            .clickable {
                                                applyZoomChange(userZoom, preset)
                                                showOptionsMenu = false
                                            }
                                    ) {
                                        Text(
                                            text = "${(preset * 100).roundToInt()}%",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = if (isCurrent) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.padding(vertical = 6.dp),
                                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                        )
                                    }
                                }
                            }

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
                    .pointerInput(showRowNumbers, density, userZoom) {
                        awaitEachGesture {
                            val down = awaitFirstDown(requireUnconsumed = false)
                            val downTime = System.currentTimeMillis()
                            val startPos = down.position
                            var isMultiTouch = false
                            var hasMoved = false
                            val touchSlop = viewConfiguration.touchSlop
                            var lastPos = startPos
                            var longPressTriggered = false

                            while (true) {
                                val event = awaitPointerEvent(PointerEventPass.Main)
                                val pressedPointers = event.changes.filter { it.pressed }

                                if (pressedPointers.size >= 2) {
                                    isMultiTouch = true
                                    scrollJob?.cancel()

                                    val zoomFactor = event.calculateZoom()
                                    val panDelta = event.calculatePan()
                                    val centroid = event.calculateCentroid()

                                    if (zoomFactor != 1.0f) {
                                        applyZoomChange(userZoom, userZoom * zoomFactor, centroid.x, centroid.y)
                                    }
                                    if (panDelta != Offset.Zero) {
                                        coroutineScope.launch {
                                            val newX = (animScrollX.value - panDelta.x).coerceAtLeast(0f)
                                            val newY = (animScrollY.value - panDelta.y).coerceAtLeast(0f)
                                            animScrollX.snapTo(newX)
                                            animScrollY.snapTo(newY)
                                        }
                                    }
                                    event.changes.forEach { it.consume() }
                                } else if (pressedPointers.size == 1) {
                                    val change = pressedPointers[0]
                                    val dist = (change.position - startPos).getDistance()

                                    if (dist > touchSlop) {
                                        hasMoved = true
                                        scrollJob?.cancel()
                                        val drag = change.position - lastPos
                                        coroutineScope.launch {
                                            val newX = (animScrollX.value - drag.x).coerceAtLeast(0f)
                                            val newY = (animScrollY.value - drag.y).coerceAtLeast(0f)
                                            animScrollX.snapTo(newX)
                                            animScrollY.snapTo(newY)
                                        }
                                        change.consume()
                                    } else if (!hasMoved && !isMultiTouch && !longPressTriggered) {
                                        val elapsed = System.currentTimeMillis() - downTime
                                        if (elapsed >= viewConfiguration.longPressTimeoutMillis) {
                                            longPressTriggered = true
                                            val headerW = if (showRowNumbers) 44f * density * userZoom else 0f
                                            val headerH = 32f * density * userZoom
                                            if (startPos.x >= headerW && startPos.y > headerH) {
                                                val gridX = startPos.x - headerW + animScrollX.value
                                                val gridY = startPos.y - headerH + animScrollY.value
                                                val r = engine.getRowAt(gridY).coerceIn(0, engine.maxRow - 1)
                                                val c = engine.getColAt(gridX).coerceIn(0, engine.maxCol - 1)
                                                selectedCell = Pair(r, c)
                                                showMenuForCell = Pair(r, c)
                                                triggerHaptic()
                                            }
                                            change.consume()
                                        }
                                    }
                                    lastPos = change.position
                                } else {
                                    // Finger released / pointer up
                                    if (!isMultiTouch && !hasMoved && !longPressTriggered) {
                                        val now = System.currentTimeMillis()
                                        val isDoubleTap = (now - lastTapTimestamp < 320) &&
                                                ((startPos - lastTapPosition).getDistance() < 24 * density)

                                        val headerW = if (showRowNumbers) 44f * density * userZoom else 0f
                                        val headerH = 32f * density * userZoom
                                        if (startPos.x >= headerW && startPos.y > headerH) {
                                            val gridX = startPos.x - headerW + animScrollX.value
                                            val gridY = startPos.y - headerH + animScrollY.value
                                            val r = engine.getRowAt(gridY).coerceIn(0, engine.maxRow - 1)
                                            val c = engine.getColAt(gridX).coerceIn(0, engine.maxCol - 1)

                                            if (isDoubleTap) {
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
                                        }
                                    }
                                    break
                                }
                            }
                        }
                    }
            ) {
                // Ensure layout cache is populated with density, zoom, and largeTouch
                engine.updateLayoutIfNeeded(density, userZoom, settings.largeTouchMode)
                val t = refreshTrigger // observe trigger

                val headerW = if (showRowNumbers) 44f * density * userZoom else 0f
                val headerH = 32f * density * userZoom
                val pad = 5f * density * userZoom

                val curScrollX = animScrollX.value
                val curScrollY = animScrollY.value

                val startRow = engine.getRowAt(curScrollY).coerceIn(0, engine.maxRow - 1)
                val startCol = engine.getColAt(curScrollX).coerceIn(0, engine.maxCol - 1)

                val effectiveFontSize = (13 * userZoom).sp
                val effectiveHeaderFontSize = (11 * userZoom).sp

                // --- 1. Draw Grid Lines & Cell Backgrounds & Content ---
                clipRect(headerW, headerH, size.width, size.height) {
                    var r = startRow
                    while (r < engine.maxRow) {
                        val rowTop = headerH + engine.getRowOffsetPx(r) - curScrollY
                        val rowHeight = engine.getRowHeightPx(r)
                        val rowBottom = rowTop + rowHeight

                        if (rowBottom < headerH) {
                            r++
                            continue
                        }
                        if (rowTop > size.height) {
                            break
                        }

                        var c = startCol
                        while (c < engine.maxCol) {
                            val colLeft = headerW + engine.getColOffsetPx(c) - curScrollX
                            val colWidth = engine.getColWidthPx(c)
                            val colRight = colLeft + colWidth

                            if (colRight < headerW) {
                                c++
                                continue
                            }
                            if (colLeft > size.width) {
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
                                    val effectiveStyle = if (isHeaderRow) {
                                        textStyle.copy(fontWeight = FontWeight.Bold, color = GreenPrimary, fontSize = effectiveFontSize)
                                    } else {
                                        textStyle.copy(fontSize = effectiveFontSize)
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

                // --- 2. Top Column Headers ---
                clipRect(headerW, 0f, size.width, headerH) {
                    drawRect(
                        color = headerBg,
                        topLeft = Offset(headerW, 0f),
                        size = Size(size.width - headerW, headerH)
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
                        val headerColor = if (isColSelected) GreenPrimary else headerStyle.color

                        drawRect(
                            color = gridColor,
                            topLeft = Offset(colLeft, 0f),
                            size = Size(colWidth, headerH),
                            style = Stroke(width = 1f * density)
                        )

                        val textLayout = textMeasurer.measure(
                            text = displayLabel,
                            style = headerStyle.copy(color = headerColor, fontSize = effectiveHeaderFontSize),
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
                    clipRect(0f, headerH, headerW, size.height) {
                        drawRect(
                            color = headerBg,
                            topLeft = Offset(0f, headerH),
                            size = Size(headerW, size.height - headerH)
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

                            val textLayout = textMeasurer.measure(
                                text = rowName,
                                style = headerStyle.copy(color = headerColor, fontSize = effectiveHeaderFontSize)
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
}
