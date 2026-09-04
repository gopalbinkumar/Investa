package com.example.investa.utils

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager

internal object IconThemeManager {
    private const val PREFERENCES = "investa_preferences"
    private const val ICON_THEME_KEY = "icon_theme"
    private const val DARK_ALIAS = "com.example.investa.DarkIconAlias"
    private const val LIGHT_ALIAS = "com.example.investa.LightIconAlias"

    enum class IconTheme(val value: String) {
        DARK("dark"),
        LIGHT("light")
    }

    fun current(context: Context): IconTheme {
        val value = context
            .getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
            .getString(ICON_THEME_KEY, IconTheme.DARK.value)
        return IconTheme.entries.firstOrNull { it.value == value } ?: IconTheme.DARK
    }

    fun save(context: Context, iconTheme: IconTheme) {
        context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
            .edit()
            .putString(ICON_THEME_KEY, iconTheme.value)
            .apply()
    }

    fun apply(context: Context) {
        val packageManager = context.packageManager
        val darkComponent = ComponentName(context.packageName, DARK_ALIAS)
        val lightComponent = ComponentName(context.packageName, LIGHT_ALIAS)
        val activeComponent = when (current(context)) {
            IconTheme.DARK -> darkComponent
            IconTheme.LIGHT -> lightComponent
        }
        val inactiveComponent = if (activeComponent == darkComponent) {
            lightComponent
        } else {
            darkComponent
        }

        packageManager.setComponentEnabledSetting(
            activeComponent,
            PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
            PackageManager.DONT_KILL_APP
        )
        packageManager.setComponentEnabledSetting(
            inactiveComponent,
            PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
            PackageManager.DONT_KILL_APP
        )
    }
}
