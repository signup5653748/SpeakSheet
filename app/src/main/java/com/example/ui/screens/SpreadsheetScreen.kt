package com.example.ui.screens

import android.os.VibrationEffect
import android.os.Vibrator
import android.content.Context
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.InteractionMode
import com.example.ui.theme.GreenPrimary
import com.example.ui.theme.HighlightColor
import com.example.viewmodel.MainViewModel

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
    
    var selectedCell by remember { mutableStateOf<Pair<Int, Int>?>(Pair(0, 0)) }
    var editingCell by remember { mutableStateOf<Pair<Int, Int>?>(null) }
    var showMenuForCell by remember { mutableStateOf<Pair<Int, Int>?>(null) }
    
    val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
    
    var scrollX by remember { mutableStateOf(0f) }
    var scrollY by remember { mutableStateOf(0f) }
    var scale by remember(settings.largeTouchMode) { mutableStateOf(if (settings.largeTouchMode) 1.5f else 1f) }

    val textMeasurer = rememberTextMeasurer()
    val textStyle = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onBackground)
    val headerStyle = MaterialTheme.typography.labelMedium.copy(color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold)

    val gridColor = if (settings.highContrastGrid) Color.White else Color.DarkGray
    val headerBg = MaterialTheme.colorScheme.surface
    val selectionBg = HighlightColor
    val selectionBorder = GreenPrimary

    // Update layout cache
    LaunchedEffect(refreshTrigger, density) {
        engine.updateLayoutIfNeeded(density)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(fileName) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { padding ->
        Box(modifier = Modifier.padding(padding).fillMaxSize()) {
            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        detectTransformGestures { centroid, pan, zoom, _ ->
                            val oldScale = scale
                            scale = (scale * zoom).coerceIn(0.5f, 3f)
                            val factor = scale / oldScale
                            
                            scrollX = (scrollX + centroid.x) * factor - centroid.x - pan.x
                            scrollY = (scrollY + centroid.y) * factor - centroid.y - pan.y
                            
                            scrollX = scrollX.coerceAtLeast(0f)
                            scrollY = scrollY.coerceAtLeast(0f)
                        }
                    }
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onTap = { offset ->
                                val headerW = 40.dp.toPx() * scale
                                val headerH = 30.dp.toPx() * scale
                                
                                if (offset.x > headerW && offset.y > headerH) {
                                    val gridX = (offset.x - headerW + scrollX) / scale
                                    val gridY = (offset.y - headerH + scrollY) / scale
                                    
                                    val r = engine.getRowAt(gridY)
                                    val c = engine.getColAt(gridX)
                                    
                                    selectedCell = Pair(r, c)
                                    viewModel.speakCell(r, c)
                                    
                                    if (settings.vibrateOnSelect) {
                                        vibrator.vibrate(VibrationEffect.createOneShot(20, VibrationEffect.DEFAULT_AMPLITUDE))
                                    }
                                }
                            },
                            onDoubleTap = { offset ->
                                val headerW = 40.dp.toPx() * scale
                                val headerH = 30.dp.toPx() * scale
                                if (offset.x > headerW && offset.y > headerH) {
                                    selectedCell?.let { (r, c) ->
                                        if (settings.interactionMode == InteractionMode.SINGLE_TAP_SPEAK_DOUBLE_TAP_EDIT) {
                                            editingCell = Pair(r, c)
                                        } else if (settings.interactionMode == InteractionMode.SINGLE_TAP_SPEAK_DOUBLE_TAP_MENU) {
                                            showMenuForCell = Pair(r, c)
                                        }
                                    }
                                }
                            },
                            onLongPress = { offset ->
                                val headerW = 40.dp.toPx() * scale
                                val headerH = 30.dp.toPx() * scale
                                if (offset.x > headerW && offset.y > headerH) {
                                    selectedCell?.let { (r, c) ->
                                        if (settings.interactionMode == InteractionMode.SINGLE_TAP_SPEAK_LONG_PRESS_MENU) {
                                            showMenuForCell = Pair(r, c)
                                        }
                                    }
                                }
                            }
                        )
                    }
            ) {
                // Ensure layout is ready
                engine.updateLayoutIfNeeded(density)
                val t = refreshTrigger // observe trigger
                
                val canvasW = size.width
                val canvasH = size.height
                
                val headerW = 40.dp.toPx() * scale
                val headerH = 30.dp.toPx() * scale
                val pad = 4.dp.toPx() * scale
                
                val startRow = engine.getRowAt(scrollY / scale)
                val startCol = engine.getColAt(scrollX / scale)
                
                // --- 1. Draw Grid Lines & Cell Backgrounds ---
                var currentY = headerH
                var r = startRow
                while (currentY < canvasH && r < engine.maxRow) {
                    val rowHeight = engine.getRowHeight(r) * density * scale
                    
                    var currentX = headerW
                    var c = startCol
                    while (currentX < canvasW && c < engine.maxCol) {
                        val colWidth = engine.getColWidth(c) * density * scale
                        
                        // Draw Selection Background
                        if (selectedCell?.first == r && selectedCell?.second == c) {
                            drawRect(
                                color = selectionBg,
                                topLeft = Offset(currentX, currentY),
                                size = Size(colWidth, rowHeight)
                            )
                        }
                        
                        // Draw Cell Borders
                        drawRect(
                            color = gridColor,
                            topLeft = Offset(currentX, currentY),
                            size = Size(colWidth, rowHeight),
                            style = Stroke(width = 1f)
                        )
                        
                        currentX += colWidth
                        c++
                    }
                    currentY += rowHeight
                    r++
                }

                // --- 2. Draw Cell Content (with exact Excel overflow/clipping) ---
                currentY = headerH
                r = startRow
                while (currentY < canvasH && r < engine.maxRow) {
                    val rowHeight = engine.getRowHeight(r) * density * scale
                    var currentX = headerW
                    var c = startCol
                    while (currentX < canvasW && c < engine.maxCol) {
                        val colWidth = engine.getColWidth(c) * density * scale
                        val text = engine.getCellValue(r, c)
                        
                        if (text.isNotEmpty()) {
                            val isRightAligned = engine.isRightAligned(r, c)
                            val textLayout = textMeasurer.measure(text, style = textStyle, maxLines = 1)
                            val textWidth = textLayout.size.width * scale
                            
                            var clipLeft = currentX
                            var clipRight = currentX + colWidth
                            
                            // Excel text overflow logic
                            if (textWidth > colWidth - pad * 2) {
                                if (!isRightAligned) {
                                    // Left aligned: check right cells
                                    var nextC = c + 1
                                    var extraW = 0f
                                    while (nextC < engine.maxCol && engine.getCellValue(r, nextC).isEmpty()) {
                                        extraW += engine.getColWidth(nextC) * density * scale
                                        nextC++
                                    }
                                    clipRight += extraW
                                } else {
                                    // Right aligned: check left cells
                                    var prevC = c - 1
                                    var extraW = 0f
                                    while (prevC >= 0 && engine.getCellValue(r, prevC).isEmpty()) {
                                        extraW += engine.getColWidth(prevC) * density * scale
                                        prevC--
                                    }
                                    clipLeft -= extraW
                                }
                            }
                            
                            clipRect(left = clipLeft, top = currentY, right = clipRight, bottom = currentY + rowHeight) {
                                val textOffsetX = if (isRightAligned) {
                                    currentX + colWidth - textWidth - pad
                                } else {
                                    currentX + pad
                                }
                                val textOffsetY = currentY + (rowHeight - textLayout.size.height) / 2
                                drawText(textLayout, topLeft = Offset(textOffsetX, textOffsetY))
                            }
                        }
                        
                        currentX += colWidth
                        c++
                    }
                    currentY += rowHeight
                    r++
                }
                
                // --- 3. Draw Selected Cell Border ---
                selectedCell?.let { (sr, sc) ->
                    if (sr >= startRow && sc >= startCol) {
                        val sy = headerH + (engine.getRowOffset(sr) * density * scale) - scrollY
                        val sx = headerW + (engine.getColOffset(sc) * density * scale) - scrollX
                        val sh = engine.getRowHeight(sr) * density * scale
                        val sw = engine.getColWidth(sc) * density * scale
                        
                        if (sy < canvasH && sx < canvasW) {
                            drawRect(
                                color = selectionBorder,
                                topLeft = Offset(sx, sy),
                                size = Size(sw, sh),
                                style = Stroke(width = 2.dp.toPx())
                            )
                            // Handle
                            drawRect(
                                color = selectionBorder,
                                topLeft = Offset(sx + sw - 4.dp.toPx(), sy + sh - 4.dp.toPx()),
                                size = Size(8.dp.toPx(), 8.dp.toPx())
                            )
                        }
                    }
                }

                // --- 4. Draw Row & Column Headers (Frozen) ---
                
                // Column Headers (Top)
                drawRect(color = headerBg, topLeft = Offset(headerW, 0f), size = Size(canvasW - headerW, headerH))
                drawLine(color = gridColor, start = Offset(headerW, headerH), end = Offset(canvasW, headerH), strokeWidth = 1f)
                
                var currentX = headerW
                var c = startCol
                while (currentX < canvasW && c < engine.maxCol) {
                    val colWidth = engine.getColWidth(c) * density * scale
                    val name = engine.getColumnName(c)
                    val tl = textMeasurer.measure(name, style = headerStyle)
                    drawText(tl, topLeft = Offset(currentX + (colWidth - tl.size.width) / 2, (headerH - tl.size.height) / 2))
                    drawLine(color = gridColor, start = Offset(currentX + colWidth, 0f), end = Offset(currentX + colWidth, headerH), strokeWidth = 1f)
                    currentX += colWidth
                    c++
                }
                
                // Row Headers (Left)
                drawRect(color = headerBg, topLeft = Offset(0f, headerH), size = Size(headerW, canvasH - headerH))
                drawLine(color = gridColor, start = Offset(headerW, headerH), end = Offset(headerW, canvasH), strokeWidth = 1f)
                
                currentY = headerH
                r = startRow
                while (currentY < canvasH && r < engine.maxRow) {
                    val rowHeight = engine.getRowHeight(r) * density * scale
                    val name = (r + 1).toString()
                    val tl = textMeasurer.measure(name, style = headerStyle)
                    drawText(tl, topLeft = Offset((headerW - tl.size.width) / 2, currentY + (rowHeight - tl.size.height) / 2))
                    drawLine(color = gridColor, start = Offset(0f, currentY + rowHeight), end = Offset(headerW, currentY + rowHeight), strokeWidth = 1f)
                    currentY += rowHeight
                    r++
                }
                
                // Top-Left Corner
                drawRect(color = headerBg, topLeft = Offset(0f, 0f), size = Size(headerW, headerH))
                drawLine(color = gridColor, start = Offset(headerW, 0f), end = Offset(headerW, headerH), strokeWidth = 1f)
                drawLine(color = gridColor, start = Offset(0f, headerH), end = Offset(headerW, headerH), strokeWidth = 1f)
                
                // --- 5. Draw Scrollbars ---
                val scrollbarColor = Color.Gray.copy(alpha = 0.5f)
                val totalH = if (engine.maxRow > 0) engine.getRowOffset(engine.maxRow - 1) * density * scale else canvasH
                val totalW = if (engine.maxCol > 0) engine.getColOffset(engine.maxCol - 1) * density * scale else canvasW
                
                if (totalH > canvasH) {
                    val barH = maxOf((canvasH / totalH) * canvasH, 20.dp.toPx())
                    val barY = (scrollY / (totalH - canvasH + headerH)).coerceIn(0f, 1f) * (canvasH - barH - headerH) + headerH
                    drawRect(
                        color = scrollbarColor,
                        topLeft = Offset(canvasW - 4.dp.toPx(), barY),
                        size = Size(4.dp.toPx(), barH)
                    )
                }
                
                if (totalW > canvasW) {
                    val barW = maxOf((canvasW / totalW) * canvasW, 20.dp.toPx())
                    val barX = (scrollX / (totalW - canvasW + headerW)).coerceIn(0f, 1f) * (canvasW - barW - headerW) + headerW
                    drawRect(
                        color = scrollbarColor,
                        topLeft = Offset(barX, canvasH - 4.dp.toPx()),
                        size = Size(barW, 4.dp.toPx())
                    )
                }
            }
        }
    }
    
    // Edit Dialog
    editingCell?.let { (r, c) ->
        val formulaOrValue = engine.getCellFormulaOrValue(r, c)
        var textValue by remember { mutableStateOf(formulaOrValue) }
        
        AlertDialog(
            onDismissRequest = { editingCell = null },
            title = { Text("Edit ${engine.getColumnName(c)}${r + 1}") },
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
            title = { Text("Menu: ${engine.getColumnName(c)}${r + 1}") },
            text = {
                Column {
                    val items = listOf("Edit", "Copy", "Cut", "Paste", "Delete", "Insert Row", "Insert Column", "Format Cell")
                    items.forEach { item ->
                        Text(
                            text = item,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    if (item == "Edit") {
                                        editingCell = Pair(r, c)
                                    } else {
                                        viewModel.ttsManager.speak("$item not implemented yet")
                                    }
                                    showMenuForCell = null
                                }
                                .padding(16.dp),
                            color = GreenPrimary
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
