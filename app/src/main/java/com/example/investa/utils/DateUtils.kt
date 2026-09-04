package com.example.investa.utils

import android.content.Context
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

fun parseTransactionDate(value: String): Long = runCatching {
    SimpleDateFormat("dd MMMM yyyy", Locale.ENGLISH).apply { isLenient = false }
        .parse(value)?.time ?: System.currentTimeMillis()
}.getOrDefault(System.currentTimeMillis())

fun formatTransactionDate(value: Long): String =
    SimpleDateFormat("dd MMMM yyyy", Locale.ENGLISH).format(Date(value))

fun parseTransactionDate(context: Context, value: String): Long {
    val locales = listOf(LanguageManager.locale(context), Locale.ENGLISH, Locale("id")).distinct()
    return locales.asSequence().mapNotNull { locale ->
        runCatching {
            SimpleDateFormat("dd MMMM yyyy", locale).apply { isLenient = false }
                .parse(value)?.time
        }.getOrNull()
    }.firstOrNull() ?: System.currentTimeMillis()
}

fun formatTransactionDate(context: Context, value: Long): String =
    SimpleDateFormat("dd MMMM yyyy", LanguageManager.locale(context)).format(Date(value))

fun formatAssetDate(context: Context, value: Long): String =
    SimpleDateFormat("dd MMM yyyy", LanguageManager.locale(context)).format(Date(value))
