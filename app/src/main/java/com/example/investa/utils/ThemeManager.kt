package com.example.investa.utils

import android.content.Context
import androidx.appcompat.app.AppCompatDelegate

internal object ThemeManager {
    private const val PREFERENCES = "investa_preferences"
    private const val THEME_KEY = "theme"

    enum class AppTheme(val value: String, val label: String, val nightMode: Int) {
        DARK("dark", "Dark", AppCompatDelegate.MODE_NIGHT_YES),
        LIGHT("light", "Light", AppCompatDelegate.MODE_NIGHT_NO),
        SYSTEM("system", "System", AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
    }

    fun current(context: Context): AppTheme {
        val value = context
            .getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
            .getString(THEME_KEY, AppTheme.DARK.value)
        return AppTheme.entries.firstOrNull { it.value == value } ?: AppTheme.DARK
    }

    fun apply(context: Context) {
        AppCompatDelegate.setDefaultNightMode(current(context).nightMode)
    }

    fun save(context: Context, theme: AppTheme) {
        context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
            .edit()
            .putString(THEME_KEY, theme.value)
            .apply()
    }

    fun select(context: Context, theme: AppTheme) {
        if (current(context) == theme) return
        save(context, theme)
        AppCompatDelegate.setDefaultNightMode(theme.nightMode)
    }
}
