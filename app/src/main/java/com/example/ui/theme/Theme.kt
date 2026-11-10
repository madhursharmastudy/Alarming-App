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

private val AurumDarkColorScheme = darkColorScheme(
    primary = AurumDarkAccent,
    onPrimary = Color(0xFF00213F),
    primaryContainer = Color(0xFF16406B),
    onPrimaryContainer = Color(0xFFD2E6FF),
    secondary = AurumDarkSecondary,
    onSecondary = Color(0xFF04222B),
    secondaryContainer = Color(0xFF1A4752),
    onSecondaryContainer = Color(0xFFCDEFF5),
    tertiary = AurumDarkTertiary,
    onTertiary = Color(0xFF3A2200),
    background = AurumDarkBackground,
    onBackground = AurumDarkText,
    surface = AurumDarkSurface,
    onSurface = AurumDarkText,
    surfaceVariant = AurumDarkSurfaceHigh,
    onSurfaceVariant = AurumDarkTextMuted,
    surfaceTint = AurumDarkAccent,
    surfaceContainerLowest = AurumDarkBackground,
    surfaceContainerLow = AurumDarkSurface,
    surfaceContainer = AurumDarkSurface,
    surfaceContainerHigh = AurumDarkSurfaceHigh,
    surfaceContainerHighest = AurumDarkSurfaceHigh,
    outline = AurumDarkOutline,
    outlineVariant = Color(0xFF2B4261),
    error = AurumDarkError,
    onError = Color(0xFF3B0A07),
    errorContainer = Color(0xFF5C1F1B),
    onErrorContainer = Color(0xFFFFDAD6)
)

private val AurumLightColorScheme = lightColorScheme(
    primary = AurumLightAccent,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD2E6FF),
    onPrimaryContainer = Color(0xFF00213F),
    secondary = AurumLightSecondary,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFCDEFF5),
    onSecondaryContainer = Color(0xFF04222B),
    tertiary = AurumLightTertiary,
    onTertiary = Color.White,
    background = AurumLightBackground,
    onBackground = AurumLightText,
    surface = AurumLightSurface,
    onSurface = AurumLightText,
    surfaceVariant = AurumLightSurfaceHigh,
    onSurfaceVariant = AurumLightTextMuted,
    surfaceTint = AurumLightAccent,
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = AurumLightBackground,
    surfaceContainer = AurumLightSurface,
    surfaceContainerHigh = AurumLightSurfaceHigh,
    surfaceContainerHighest = AurumLightSurfaceHigh,
    outline = AurumLightOutline,
    outlineVariant = Color(0xFFC0D3E6),
    error = AurumLightError,
    onError = Color.White,
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002)
)

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    forceDark: Boolean = false,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    LaunchedEffect(Unit) {
        ThemePreferences.init(context)
    }

    val mode by ThemePreferences.themeModeFlow.collectAsState()
    val systemInDark = isSystemInDarkTheme()
    val useDark = forceDark || when (mode) {
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
