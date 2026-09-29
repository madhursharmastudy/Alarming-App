package com.example.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

/**
 * Dark theme:
 * - background #0D0D0D (near-black)
 * - main/accent color #E53935 (bright red)
 * - text on accent color uses #0D0D0D
 */
private val AurumDarkColorScheme = darkColorScheme(
    primary = AurumDarkAccent,
    onPrimary = AurumDarkBackground,
    primaryContainer = AurumDarkAccent.copy(alpha = 0.20f),
    onPrimaryContainer = AurumDarkAccent,
    secondary = AurumDarkAccent,
    onSecondary = AurumDarkBackground,
    secondaryContainer = AurumDarkAccent.copy(alpha = 0.14f),
    onSecondaryContainer = AurumDarkAccent,
    background = AurumDarkBackground,
    onBackground = AurumDarkAccent,
    surface = AurumDarkBackground,
    onSurface = AurumDarkAccent,
    surfaceVariant = AurumDarkAccent.copy(alpha = 0.12f),
    onSurfaceVariant = AurumDarkAccent.copy(alpha = 0.85f),
    outline = AurumDarkAccent.copy(alpha = 0.35f),
    outlineVariant = AurumDarkAccent.copy(alpha = 0.20f),
    error = AurumDarkAccent,
    onError = AurumDarkBackground,
    errorContainer = AurumDarkAccent.copy(alpha = 0.20f),
    onErrorContainer = AurumDarkAccent
)

/**
 * Light theme:
 * - background #FAFAFA (near-white)
 * - main/accent color #C62828 (dark red)
 * - text on accent color uses #FFFFFF
 */
private val AurumLightColorScheme = lightColorScheme(
    primary = AurumLightAccent,
    onPrimary = Color.White,
    primaryContainer = AurumLightAccent.copy(alpha = 0.18f),
    onPrimaryContainer = AurumLightAccent,
    secondary = AurumLightAccent,
    onSecondary = Color.White,
    secondaryContainer = AurumLightAccent.copy(alpha = 0.12f),
    onSecondaryContainer = AurumLightAccent,
    background = AurumLightBackground,
    onBackground = AurumLightAccent,
    surface = AurumLightBackground,
    onSurface = AurumLightAccent,
    surfaceVariant = AurumLightAccent.copy(alpha = 0.10f),
    onSurfaceVariant = AurumLightAccent.copy(alpha = 0.85f),
    outline = AurumLightAccent.copy(alpha = 0.35f),
    outlineVariant = AurumLightAccent.copy(alpha = 0.18f),
    error = AurumLightAccent,
    onError = Color.White,
    errorContainer = AurumLightAccent.copy(alpha = 0.20f),
    onErrorContainer = AurumLightAccent
)

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    LaunchedEffect(Unit) {
        ThemePreferences.init(context)
    }

    val mode by ThemePreferences.themeModeFlow.collectAsState()
    val systemInDark = isSystemInDarkTheme()
    val useDark = when (mode) {
        AppThemeMode.SYSTEM -> systemInDark
        AppThemeMode.DARK -> true
        AppThemeMode.LIGHT -> false
    }

    val colorScheme = if (useDark) AurumDarkColorScheme else AurumLightColorScheme

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window
            if (window != null) {
                val insetsController = WindowCompat.getInsetsController(window, view)
                // Light icons on dark theme, dark icons on light theme
                insetsController.isAppearanceLightStatusBars = !useDark
                insetsController.isAppearanceLightNavigationBars = !useDark
            }
        }
    }

    MaterialTheme(colorScheme = colorScheme, typography = Typography, content = content)
}
