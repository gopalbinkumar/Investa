package com.example.investa.utils

import java.util.Locale

enum class NumberFormatStyle(
    val id: String,
    val locale: Locale,
    val decimalSeparator: Char,
    val example: String
) {
    INDONESIAN("ID", Locale.GERMANY, ',', "1.234.567,89"),
    ENGLISH("US", Locale.US, '.', "1,234,567.89");

    companion object {
        fun fromId(id: String?): NumberFormatStyle =
            entries.firstOrNull { it.id == id } ?: INDONESIAN
    }
}

object NumberFormatStyleManager {
    @Volatile
    var current: NumberFormatStyle = NumberFormatStyle.INDONESIAN
        private set

    fun apply(style: NumberFormatStyle) {
        current = style
    }
}
