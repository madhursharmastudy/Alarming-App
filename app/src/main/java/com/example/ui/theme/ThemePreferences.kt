package com.example.ui.theme

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class AppThemeMode(val displayName: String) {
    SYSTEM("System Default"),
    DARK("Dark (Gold & Midnight)"),
    LIGHT("Light (Deep Gold & Cream)")
}

object ThemePreferences {
    private const val PREFS_NAME = "aurum_theme_prefs"
    private const val KEY_THEME_MODE = "key_theme_mode"

    private val _themeModeFlow = MutableStateFlow(AppThemeMode.SYSTEM)
    val themeModeFlow = _themeModeFlow.asStateFlow()

    private var isInitialized = false

    fun init(context: Context) {
        if (!isInitialized) {
            val prefs = getPrefs(context)
            val savedName = prefs.getString(KEY_THEME_MODE, AppThemeMode.SYSTEM.name) ?: AppThemeMode.SYSTEM.name
            _themeModeFlow.value = try {
                AppThemeMode.valueOf(savedName)
            } catch (e: Exception) {
                AppThemeMode.SYSTEM
            }
            isInitialized = true
        }
    }

    fun getThemeMode(context: Context): AppThemeMode {
        init(context)
        return _themeModeFlow.value
    }

    fun setThemeMode(context: Context, mode: AppThemeMode) {
        init(context)
        _themeModeFlow.value = mode
        getPrefs(context).edit().putString(KEY_THEME_MODE, mode.name).apply()
    }

    private fun getPrefs(context: Context): SharedPreferences {
        return context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }
}
