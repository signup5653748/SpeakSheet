package com.example.ui.screens

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
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ui.theme.GreenPrimary
import com.example.viewmodel.MainViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

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

    // Ensure layout cache is updated for unscaled density and largeTouchMode only (never on zoom)
    LaunchedEffect(refreshTrigger, density, settings.largeTouchMode) {
        engine.updateLayoutIfNeeded(density, settings.largeTouchMode)
    }

    fun clampPan(offset: Offset, zoom: Float): Offset {
        val viewW = if (viewportSize.width > 0) viewportSize.width.toFloat() else 1000f
        val viewH = if (viewportSize.height > 0) viewportSize.height.toFloat() else 1500f
        val headerW = if (settings.showRowNumbers) 44f * density else 0f
        val headerH = 32f * density
        val contentW = (engine.totalWidthPx + headerW) * zoom
        val contentH = (engine.totalHeightPx + headerH) * zoom
        val margin = 48f * density

        val minX: Float
        val maxX: Float
        if (contentW > viewW) {
            minX = viewW - contentW - margin
            maxX = margin
        } else {
            minX = -margin
            maxX = (viewW - contentW) + margin
        }

        val minY: Float
        val maxY: Float
        if (contentH > viewH) {
            minY = viewH - contentH - margin
            maxY = margin
        } else {
            minY = -margin
            maxY = (viewH - contentH) + margin
        }

        return Offset(
            x = offset.x.coerceIn(minOf(minX, maxX), maxOf(minX, maxX)),
            y = offset.y.coerceIn(minOf(minY, maxY), maxOf(minY, maxY))
        )
    }

    fun applyZoom(
        newZoom: Float,
        pivot: Offset = Offset(
            if (viewportSize.width > 0) viewportSize.width / 2f else 500f,
            if (viewportSize.height > 0) viewportSize.height / 2f else 750f
        )
    ) {
        val clampedZoom = newZoom.coerceIn(0.7f, 3.0f)
        if (userZoom == clampedZoom) return
        val oldZoom = userZoom
        userZoom = clampedZoom

        val curPan = animPanOffset.value
        val targetPan = pivot - (pivot - curPan) * (clampedZoom / oldZoom)
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
        val cellLeftUnscaled = headerW + engine.getColOffsetPx(newC)
        val cellRightUnscaled = cellLeftUnscaled + engine.getColWidthPx(newC)
        val cellTopUnscaled = headerH + engine.getRowOffsetPx(newR)
        val cellBottomUnscaled = cellTopUnscaled + engine.getRowHeightPx(newR)

        val viewW = if (viewportSize.width > 0) viewportSize.width.toFloat() else 1000f
        val viewH = if (viewportSize.height > 0) viewportSize.height.toFloat() else 1500f
        
        val curPan = animPanOffset.value
        val cellScreenLeft = cellLeftUnscaled * userZoom + curPan.x
        val cellScreenRight = cellRightUnscaled * userZoom + curPan.x
        val cellScreenTop = cellTopUnscaled * userZoom + curPan.y
        val cellScreenBottom = cellBottomUnscaled * userZoom + curPan.y

        // 40dp margin so cell lands comfortably in view without edge clipping
        val margin = 40f * density
        var targetPanX = curPan.x
        var targetPanY = curPan.y
        
        if (cellScreenLeft < margin) {
            targetPanX = margin - cellLeftUnscaled * userZoom
        } else if (cellScreenRight > viewW - margin) {
            targetPanX = viewW - margin - cellRightUnscaled * userZoom
        }
        
        if (cellScreenTop < margin) {
            targetPanY = margin - cellTopUnscaled * userZoom
        } else if (cellScreenBottom > viewH - margin) {
            targetPanY = viewH - margin - cellBottomUnscaled * userZoom
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
                                    applyZoom(1.0f)
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
                                    applyZoom((userZoom + 0.20f).coerceIn(0.7f, 3.0f))
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
                                    applyZoom((userZoom - 0.20f).coerceIn(0.7f, 3.0f))
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
                                                applyZoom(preset)
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
                    .pointerInput(showRowNumbers, density) {
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

                                    val oldZoom = userZoom
                                    val newZoom = (oldZoom * zoomFactor).coerceIn(0.7f, 3.0f)
                                    userZoom = newZoom

                                    val curPan = animPanOffset.value
                                    val targetPan = centroid - (centroid - curPan) * (newZoom / oldZoom) + panDelta
                                    val clampedPan = clampPan(targetPan, newZoom)

                                    panChannel.trySend(clampedPan)
                                    event.changes.forEach { it.consume() }
                                } else if (pressedPointers.size == 1) {
                                    val change = pressedPointers[0]
                                    val dist = (change.position - startPos).getDistance()

                                    if (dist > touchSlop) {
                                        hasMoved = true
                                        scrollJob?.cancel()
                                        val drag = change.position - lastPos
                                        val targetPan = animPanOffset.value + drag
                                        val clampedPan = clampPan(targetPan, userZoom)
                                        panChannel.trySend(clampedPan)
                                        change.consume()
                                    } else if (!hasMoved && !isMultiTouch && !longPressTriggered) {
                                        val elapsed = System.currentTimeMillis() - downTime
                                        if (elapsed >= viewConfiguration.longPressTimeoutMillis) {
                                            longPressTriggered = true
                                            val curPan = animPanOffset.value
                                            val unscaledX = (startPos.x - curPan.x) / userZoom
                                            val unscaledY = (startPos.y - curPan.y) / userZoom
                                            val headerW = if (showRowNumbers) 44f * density else 0f
                                            val headerH = 32f * density
                                            if (unscaledX >= headerW && unscaledY > headerH) {
                                                val gridX = unscaledX - headerW
                                                val gridY = unscaledY - headerH
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

                                        val curPan = animPanOffset.value
                                        val unscaledX = (startPos.x - curPan.x) / userZoom
                                        val unscaledY = (startPos.y - curPan.y) / userZoom
                                        val headerW = if (showRowNumbers) 44f * density else 0f
                                        val headerH = 32f * density

                                        if (unscaledX >= headerW && unscaledY > headerH) {
                                            val gridX = unscaledX - headerW
                                            val gridY = unscaledY - headerH
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
                                        } else if (unscaledY <= headerH && unscaledX >= headerW) {
                                            // Tapped column header
                                            val c = engine.getColAt(unscaledX - headerW).coerceIn(0, engine.maxCol - 1)
                                            selectedCell = Pair(0, c)
                                            viewModel.speakCell(0, c)
                                            triggerHaptic()
                                        } else if (unscaledX < headerW && unscaledY > headerH) {
                                            // Tapped row header
                                            val r = engine.getRowAt(unscaledY - headerH).coerceIn(0, engine.maxRow - 1)
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

                withTransform({
                    translate(left = panOffset.x, top = panOffset.y)
                    scale(scaleX = userZoom, scaleY = userZoom, pivot = Offset.Zero)
                }) {
                    val visibleLeftUnscaled = -panOffset.x / userZoom
                    val visibleTopUnscaled = -panOffset.y / userZoom
                    val visibleRightUnscaled = (size.width - panOffset.x) / userZoom
                    val visibleBottomUnscaled = (size.height - panOffset.y) / userZoom

                    val startRow = engine.getRowAt(visibleTopUnscaled - headerH).coerceIn(0, engine.maxRow - 1)
                    val startCol = engine.getColAt(visibleLeftUnscaled - headerW).coerceIn(0, engine.maxCol - 1)

                    // --- 1. Draw Grid Lines & Cell Backgrounds & Content ---
                    var r = startRow
                    while (r < engine.maxRow) {
                        val rowTop = headerH + engine.getRowOffsetPx(r)
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
                            val colLeft = headerW + engine.getColOffsetPx(c)
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

                    // --- 2. Top Column Headers ---
                    val totalGridW = engine.totalWidthPx
                    drawRect(
                        color = headerBg,
                        topLeft = Offset(headerW, 0f),
                        size = Size(totalGridW, headerH)
                    )
                    var hc = startCol
                    while (hc < engine.maxCol) {
                        val colLeft = headerW + engine.getColOffsetPx(hc)
                        val colWidth = engine.getColWidthPx(hc)
                        val colRight = colLeft + colWidth

                        if (colRight < visibleLeftUnscaled) {
                            hc++
                            continue
                        }
                        if (colLeft > visibleRightUnscaled) {
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

                        val textLayout = textMeasurer.measure(
                            text = displayLabel,
                            style = effectiveHeaderStyle,
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

                    // --- 3. Left Row Headers (if enabled) ---
                    if (showRowNumbers && headerW > 0f) {
                        val totalGridH = engine.totalHeightPx
                        drawRect(
                            color = headerBg,
                            topLeft = Offset(0f, headerH),
                            size = Size(headerW, totalGridH)
                        )
                        var hr = startRow
                        while (hr < engine.maxRow) {
                            val rowTop = headerH + engine.getRowOffsetPx(hr)
                            val rowHeight = engine.getRowHeightPx(hr)
                            val rowBottom = rowTop + rowHeight

                            if (rowBottom < visibleTopUnscaled) {
                                hr++
                                continue
                            }
                            if (rowTop > visibleBottomUnscaled) {
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
