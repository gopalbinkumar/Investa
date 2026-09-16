package com.example.investa.utils

import android.content.Context
import android.app.UiModeManager
import android.os.Build
import androidx.appcompat.app.AppCompatDelegate

internal object ThemeManager {
    private const val PREFERENCES = "investa_preferences"
    private const val THEME_KEY = "theme"

    private enum class AppTheme(val value: String, val nightMode: Int) {
        DARK("dark", AppCompatDelegate.MODE_NIGHT_YES),
        LIGHT("light", AppCompatDelegate.MODE_NIGHT_NO)
    }

    private fun current(context: Context): AppTheme {
        val value = context
            .getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
            .getString(THEME_KEY, AppTheme.DARK.value)
        return AppTheme.entries.firstOrNull { it.value == value } ?: AppTheme.DARK
    }

    fun isDarkModeEnabled(context: Context): Boolean = current(context) == AppTheme.DARK

    fun apply(context: Context) {
        applyNightMode(context, current(context).nightMode)
    }

    fun update(context: Context, darkMode: Boolean): Boolean {
        val theme = if (darkMode) AppTheme.DARK else AppTheme.LIGHT
        if (current(context) == theme) return false

        save(context, theme)
        applyNightMode(context, theme.nightMode)
        return true
    }

    private fun applyNightMode(context: Context, nightMode: Int) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val mode = if (nightMode == AppCompatDelegate.MODE_NIGHT_YES) {
                UiModeManager.MODE_NIGHT_YES
            } else {
                UiModeManager.MODE_NIGHT_NO
            }
            context.getSystemService(UiModeManager::class.java).setApplicationNightMode(mode)
        } else if (AppCompatDelegate.getDefaultNightMode() != nightMode) {
            AppCompatDelegate.setDefaultNightMode(nightMode)
        }
    }

    private fun save(context: Context, theme: AppTheme) {
        context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
            .edit()
            .putString(THEME_KEY, theme.value)
            // Theme changes recreate the activity immediately. Commit first so the
            // next activity instance cannot restore the previous theme state.
            .commit()
    }

}
