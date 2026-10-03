package com.speaksheet.ui.screens

import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.TableChart
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.speaksheet.data.RecentFile
import com.speaksheet.viewmodel.MainViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: MainViewModel,
    onNavigateToSpreadsheet: () -> Unit,
    onNavigateToSettings: () -> Unit
) {
    val recentFiles by viewModel.recentFiles.collectAsStateWithLifecycle()
    val settings by viewModel.appSettings.collectAsStateWithLifecycle()
    val themeColor = Color(settings.themeColor)
    var searchQuery by remember { mutableStateOf("") }
    val filteredFiles = remember(recentFiles, searchQuery) {
        recentFiles.filter { 
            it.name.contains(searchQuery, ignoreCase = true) 
        }
    }
    
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
        onResult = { uri: Uri? ->
            uri?.let {
                try {
                    context.contentResolver.takePersistableUriPermission(
                        it,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                    )
                } catch (_: Throwable) {}
                coroutineScope.launch {
                    val finalName = withContext(Dispatchers.IO) {
                        var name: String? = null
                        if (it.scheme == "content") {
                            try {
                                context.contentResolver.query(it, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                                    val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                                    if (idx != -1 && cursor.moveToFirst()) {
                                        name = cursor.getString(idx)
                                    }
                                }
                            } catch (_: Exception) {}
                        }
                        if (name.isNullOrBlank()) {
                            name = it.lastPathSegment?.substringAfterLast('/')
                        }
                        name?.takeIf { n -> n.isNotBlank() } ?: "Imported File.xlsx"
                    }
                    viewModel.openFile(it, finalName)
                    onNavigateToSpreadsheet()
                }
            }
        }
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = { 
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Filled.TableChart, 
                            contentDescription = null, 
                            tint = themeColor,
                            modifier = Modifier.size(28.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("SpeakSheet", fontWeight = FontWeight.Bold)
                    }
                },
                actions = {
                    IconButton(onClick = onNavigateToSettings) {
                        Icon(Icons.Filled.Settings, contentDescription = "Settings")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = themeColor
                )
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = {
                    viewModel.openNewSpreadsheet()
                    onNavigateToSpreadsheet()
                },
                icon = {
                    Icon(Icons.Filled.Add, contentDescription = null)
                },
                text = {
                    Text("New Sheet", fontWeight = FontWeight.SemiBold)
                },
                containerColor = themeColor,
                contentColor = Color.White,
                modifier = Modifier
                    .padding(16.dp)
                    .testTag("fab_new_spreadsheet")
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            contentPadding = PaddingValues(bottom = 80.dp)
        ) {
            item {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    label = { Text("Search Recent Files") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp, bottom = 16.dp)
                        .testTag("search_files_input"),
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = themeColor,
                        focusedLabelColor = themeColor
                    )
                )
            }
            
            // Templates Section
            item {
                Text(
                    text = "Templates",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 20.dp)
                ) {
                    item {
                        Card(
                            onClick = {
                                viewModel.openNewSpreadsheet()
                                onNavigateToSpreadsheet()
                            },
                            modifier = Modifier
                                .width(160.dp)
                                .testTag("template_blank_spreadsheet"),
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
                        ) {
                            Column(
                                modifier = Modifier.padding(14.dp),
                                horizontalAlignment = Alignment.Start
                            ) {
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = themeColor.copy(alpha = 0.2f),
                                    modifier = Modifier.size(36.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(Icons.Default.Add, contentDescription = null, tint = themeColor, modifier = Modifier.size(20.dp))
                                    }
                                }
                                Spacer(modifier = Modifier.height(10.dp))
                                Text("Blank Sheet", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                                Spacer(modifier = Modifier.height(2.dp))
                                Text("Empty grid", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }

                    item {
                        Card(
                            onClick = {
                                viewModel.openSampleSortFilterReport()
                                onNavigateToSpreadsheet()
                            },
                            modifier = Modifier
                                .width(200.dp)
                                .testTag("template_sort_filter_demo"),
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
                        ) {
                            Column(
                                modifier = Modifier.padding(14.dp),
                                horizontalAlignment = Alignment.Start
                            ) {
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = themeColor.copy(alpha = 0.2f),
                                    modifier = Modifier.size(36.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(Icons.Default.TableChart, contentDescription = null, tint = themeColor, modifier = Modifier.size(20.dp))
                                    }
                                }
                                Spacer(modifier = Modifier.height(10.dp))
                                Text("SORT & FILTER Demo", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                                Spacer(modifier = Modifier.height(2.dp))
                                Text("Sample with =SORT(FILTER(...)) formula", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }

                    item {
                        Card(
                            onClick = {
                                viewModel.openSampleSectionedReport()
                                onNavigateToSpreadsheet()
                            },
                            modifier = Modifier
                                .width(200.dp)
                                .testTag("template_sectioned_report"),
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
                        ) {
                            Column(
                                modifier = Modifier.padding(14.dp),
                                horizontalAlignment = Alignment.Start
                            ) {
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = themeColor.copy(alpha = 0.2f),
                                    modifier = Modifier.size(36.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(Icons.Default.TableChart, contentDescription = null, tint = themeColor, modifier = Modifier.size(20.dp))
                                    }
                                }
                                Spacer(modifier = Modifier.height(10.dp))
                                Text("Sectioned Report", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                                Spacer(modifier = Modifier.height(2.dp))
                                Text("Sample with banners & subtotals", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }

            // Recent Files Header & Action buttons
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Recent Files",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilledTonalButton(
                            onClick = { 
                                filePickerLauncher.launch(arrayOf(
                                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", 
                                    "application/vnd.ms-excel",
                                    "application/vnd.ms-excel.sheet.macroEnabled.12",
                                    "application/vnd.ms-excel.sheet.binary.macroEnabled.12",
                                    "application/vnd.oasis.opendocument.spreadsheet",
                                    "text/csv", 
                                    "text/comma-separated-values",
                                    "text/tab-separated-values",
                                    "text/tsv"
                                )) 
                            },
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                            modifier = Modifier.testTag("import_file_button")
                        ) {
                            Icon(Icons.Default.FileOpen, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Import", fontSize = 13.sp)
                        }

                        Button(
                            onClick = {
                                viewModel.openNewSpreadsheet()
                                onNavigateToSpreadsheet()
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = themeColor),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                            modifier = Modifier.testTag("new_sheet_header_button")
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("New Sheet", fontSize = 13.sp)
                        }
                    }
                }
            }
            
            if (filteredFiles.isEmpty()) {
                item {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "No recent files found",
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Create a blank sheet or import a file from your device to get started!",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Button(
                                onClick = {
                                    viewModel.openNewSpreadsheet()
                                    onNavigateToSpreadsheet()
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = themeColor),
                                modifier = Modifier.testTag("empty_state_create_new_button")
                            ) {
                                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Create Blank Spreadsheet")
                            }
                        }
                    }
                }
            } else {
                items(filteredFiles, key = { it.uri }) { file ->
                    RecentFileCard(
                        file = file,
                        themeColor = themeColor,
                        onClick = {
                            viewModel.openFile(Uri.parse(file.uri), file.name)
                            onNavigateToSpreadsheet()
                        },
                        onDelete = {
                            viewModel.deleteRecentFile(file)
                        }
                    )
                }
            }
        }
    }
}

private val recentFileDateFormat = SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault())

@Composable
fun RecentFileCard(
    file: RecentFile, 
    themeColor: Color,
    onClick: () -> Unit,
    onDelete: (() -> Unit)? = null
) {
    val dateStr = remember(file.lastModified) {
        recentFileDateFormat.format(Date(file.lastModified))
    }
    
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
            .clickable { onClick() }
            .testTag("recent_file_${file.name.replace(" ", "_")}"),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = themeColor.copy(alpha = 0.15f),
                modifier = Modifier.size(44.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Filled.TableChart,
                        contentDescription = null,
                        tint = themeColor,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = file.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "Last opened: $dateStr",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
            }
            if (onDelete != null) {
                IconButton(
                    onClick = onDelete,
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("delete_recent_${file.name.replace(" ", "_")}")
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Remove file",
                        tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                        modifier = Modifier.size(18.dp)
                    )
                }
            } else {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                    contentDescription = "Open file",
                    tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}
