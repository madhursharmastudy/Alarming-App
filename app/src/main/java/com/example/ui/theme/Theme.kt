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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

/**
 * Dark theme: background #0B0B0F, main color gold #D4AF37.
 * Uses only these two colors for all text, icons, buttons, borders, cards, and progress bars.
 * Lower alpha (10% to 40%) used for soft surfaces, disabled states, and dividers.
 * Text on gold buttons uses background color.
 */
private val AurumDarkColorScheme = darkColorScheme(
    primary = AurumDarkGold,
    onPrimary = AurumDarkBackground,
    primaryContainer = AurumDarkGold.copy(alpha = 0.22f),
    onPrimaryContainer = AurumDarkGold,
    secondary = AurumDarkGold,
    onSecondary = AurumDarkBackground,
    secondaryContainer = AurumDarkGold.copy(alpha = 0.15f),
    onSecondaryContainer = AurumDarkGold,
    background = AurumDarkBackground,
    onBackground = AurumDarkGold,
    surface = AurumDarkBackground,
    onSurface = AurumDarkGold,
    surfaceVariant = AurumDarkGold.copy(alpha = 0.12f),
    onSurfaceVariant = AurumDarkGold.copy(alpha = 0.80f),
    outline = AurumDarkGold.copy(alpha = 0.35f),
    outlineVariant = AurumDarkGold.copy(alpha = 0.20f),
    error = AurumDarkGold,
    onError = AurumDarkBackground,
    errorContainer = AurumDarkGold.copy(alpha = 0.25f),
    onErrorContainer = AurumDarkGold
)

/**
 * Light theme: background #F7F3E8, main color deep gold #8A6D12.
 * Text on deep gold buttons uses background color.
 */
private val AurumLightColorScheme = lightColorScheme(
    primary = AurumLightDeepGold,
    onPrimary = AurumLightBackground,
    primaryContainer = AurumLightDeepGold.copy(alpha = 0.20f),
    onPrimaryContainer = AurumLightDeepGold,
    secondary = AurumLightDeepGold,
    onSecondary = AurumLightBackground,
    secondaryContainer = AurumLightDeepGold.copy(alpha = 0.14f),
    onSecondaryContainer = AurumLightDeepGold,
    background = AurumLightBackground,
    onBackground = AurumLightDeepGold,
    surface = AurumLightBackground,
    onSurface = AurumLightDeepGold,
    surfaceVariant = AurumLightDeepGold.copy(alpha = 0.10f),
    onSurfaceVariant = AurumLightDeepGold.copy(alpha = 0.80f),
    outline = AurumLightDeepGold.copy(alpha = 0.35f),
    outlineVariant = AurumLightDeepGold.copy(alpha = 0.18f),
    error = AurumLightDeepGold,
    onError = AurumLightBackground,
    errorContainer = AurumLightDeepGold.copy(alpha = 0.22f),
    onErrorContainer = AurumLightDeepGold
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
                // Light icons on dark theme (appearanceLight = false)
                // Dark icons on light theme (appearanceLight = true)
                insetsController.isAppearanceLightStatusBars = !useDark
                insetsController.isAppearanceLightNavigationBars = !useDark
            }
        }
    }

    MaterialTheme(colorScheme = colorScheme, typography = Typography, content = content)
}
