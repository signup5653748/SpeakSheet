package com.speaksheet.ui.theme

import android.app.Activity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

val LocalThemeAccentColor = staticCompositionLocalOf { GreenPrimary }
val LocalSelectedCellColor = staticCompositionLocalOf { GreenPrimary }

@Composable
fun SpeakSheetTheme(
    themeColor: Color = GreenPrimary,
    selectedCellColor: Color = GreenPrimary,
    content: @Composable () -> Unit
) {
    val colorScheme = darkColorScheme(
        primary = themeColor,
        secondary = themeColor,
        tertiary = themeColor,
        background = BackgroundDark,
        surface = SurfaceDark,
        onPrimary = TextPrimary,
        onSecondary = TextPrimary,
        onTertiary = TextPrimary,
        onBackground = TextPrimary,
        onSurface = TextPrimary,
        primaryContainer = themeColor.copy(alpha = 0.2f),
        onPrimaryContainer = themeColor,
        surfaceVariant = SurfaceDark
    )
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window
            window?.let {
                WindowCompat.getInsetsController(it, view).isAppearanceLightStatusBars = false
            }
        }
    }

    CompositionLocalProvider(
        LocalThemeAccentColor provides themeColor,
        LocalSelectedCellColor provides selectedCellColor
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = Typography,
            content = content
        )
    }
}
