package com.SpeakSheet

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.SpeakSheet.ui.screens.HomeScreen
import com.SpeakSheet.ui.screens.SettingsScreen
import com.SpeakSheet.ui.screens.SpreadsheetScreen
import com.SpeakSheet.ui.theme.BackgroundDark
import com.SpeakSheet.ui.theme.SpeakSheetTheme
import com.SpeakSheet.viewmodel.MainViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        enableEdgeToEdge()
        setContent {
            SpeakSheetTheme {
                val navController = rememberNavController()
                val viewModel: MainViewModel = viewModel()
                
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
}
