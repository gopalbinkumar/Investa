package com.example.investa.utils

import android.content.Context
import com.example.investa.R

internal object PaletteManager {
    private const val PREFERENCES = "investa_preferences"
    private const val PALETTE_KEY = "palette"

    enum class AppPalette(val value: String) {
        DEFAULT("default"),
        BLUE("blue"),
        GREEN("green"),
        TEAL("teal"),
        PURPLE("purple"),
        ROSE("rose"),
        RED("red")
    }

    fun current(context: Context): AppPalette {
        val value = context
            .getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
            .getString(PALETTE_KEY, AppPalette.DEFAULT.value)
        return AppPalette.entries.firstOrNull { it.value == value } ?: AppPalette.DEFAULT
    }

    fun save(context: Context, palette: AppPalette) {
        context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
            .edit()
            .putString(PALETTE_KEY, palette.value)
            .apply()
    }

    fun themeRes(context: Context): Int = when (current(context)) {
        AppPalette.DEFAULT -> R.style.Theme_Investa_Palette_Default
        AppPalette.BLUE -> R.style.Theme_Investa_Palette_Blue
        AppPalette.GREEN -> R.style.Theme_Investa_Palette_Green
        AppPalette.TEAL -> R.style.Theme_Investa_Palette_Teal
        AppPalette.PURPLE -> R.style.Theme_Investa_Palette_Purple
        AppPalette.ROSE -> R.style.Theme_Investa_Palette_Rose
        AppPalette.RED -> R.style.Theme_Investa_Palette_Red
    }

    fun splashThemeRes(context: Context): Int = when (current(context)) {
        AppPalette.DEFAULT -> R.style.Theme_Investa_Splash_Palette_Default
        AppPalette.BLUE -> R.style.Theme_Investa_Splash_Palette_Blue
        AppPalette.GREEN -> R.style.Theme_Investa_Splash_Palette_Green
        AppPalette.TEAL -> R.style.Theme_Investa_Splash_Palette_Teal
        AppPalette.PURPLE -> R.style.Theme_Investa_Splash_Palette_Purple
        AppPalette.ROSE -> R.style.Theme_Investa_Splash_Palette_Rose
        AppPalette.RED -> R.style.Theme_Investa_Splash_Palette_Red
    }
}
