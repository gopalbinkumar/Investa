package com.example.investa.utils

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

fun parseTransactionDate(value: String): Long = runCatching {
    SimpleDateFormat("dd MMMM yyyy", Locale.ENGLISH).apply { isLenient = false }
        .parse(value)?.time ?: System.currentTimeMillis()
}.getOrDefault(System.currentTimeMillis())

fun formatTransactionDate(value: Long): String =
    SimpleDateFormat("dd MMMM yyyy", Locale.ENGLISH).format(Date(value))
