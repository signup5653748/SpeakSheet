package com.speaksheet

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.OpenableColumns
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.speaksheet.ui.screens.HomeScreen
import com.speaksheet.ui.screens.SettingsScreen
import com.speaksheet.ui.screens.SpreadsheetScreen
import com.speaksheet.ui.theme.SpeakSheetTheme
import com.speaksheet.viewmodel.MainViewModel

class MainActivity : ComponentActivity() {

    private val pendingFileUri = mutableStateOf<Uri?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        handleIntent(intent)

        enableEdgeToEdge()
        setContent {
            SpeakSheetTheme {
                val navController = rememberNavController()
                val viewModel: MainViewModel = viewModel()

                val pendingUri by pendingFileUri
                LaunchedEffect(pendingUri) {
                    val uri = pendingUri ?: return@LaunchedEffect
                    try {
                        val flags = intent?.flags ?: 0
                        if ((flags and Intent.FLAG_GRANT_READ_URI_PERMISSION) != 0 && uri.scheme == "content") {
                            try {
                                contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            } catch (_: SecurityException) {
                                // Persistable permission not grantable or not needed
                            }
                        }
                    } catch (_: Exception) {}

                    val fileName = resolveFileName(this@MainActivity, uri)
                    viewModel.openFile(uri, fileName)
                    navController.navigate("spreadsheet") {
                        popUpTo("home") { inclusive = false }
                        launchSingleTop = true
                    }
                    pendingFileUri.value = null
                }
                
                NavHost(navController = navController, startDestination = "home") {
                    composable("home") {
                        HomeScreen(
                            viewModel = viewModel,
                            onNavigateToSpreadsheet = { navController.navigate("spreadsheet") },
                            onNavigateToSettings = { navController.navigate("settings") }
                        )
                    }
                    composable("spreadsheet") {
                        SpreadsheetScreen(
                            viewModel = viewModel,
                            onNavigateBack = { navController.popBackStack() }
                        )
                    }
                    composable("settings") {
                        SettingsScreen(
                            viewModel = viewModel,
                            onNavigateBack = { navController.popBackStack() }
                        )
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        if (intent == null) return
        val uri: Uri? = when (intent.action) {
            Intent.ACTION_VIEW, Intent.ACTION_EDIT -> intent.data
            Intent.ACTION_SEND -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    intent.getParcelableExtra(Intent.EXTRA_STREAM)
                }
            }
            else -> intent.data
        }
        if (uri != null) {
            pendingFileUri.value = uri
        }
    }

    private fun resolveFileName(context: Context, uri: Uri): String {
        var name: String? = null
        if (uri.scheme == "content") {
            try {
                context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                    val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (idx != -1 && cursor.moveToFirst()) {
                        name = cursor.getString(idx)
                    }
                }
            } catch (_: Exception) {}
        }
        if (name.isNullOrBlank()) {
            name = uri.lastPathSegment?.substringAfterLast('/')
        }
        return name?.takeIf { it.isNotBlank() } ?: "Spreadsheet.xlsx"
    }
}

