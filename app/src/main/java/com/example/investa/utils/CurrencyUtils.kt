package com.example.investa.utils

const val SELECT_CATEGORY = "Select Category"

val assetCategories = listOf("Crypto", "ID Stocks", "US Stocks", "Bonds", "Mutual Fund", "Gold")

fun quantityUnitHint(category: String, symbol: String): String = when (category) {
    "Crypto" -> symbol.trim().ifEmpty { "[symbol]" }
    "ID Stocks", "US Stocks" -> "share(s)"
    "Mutual Fund", "Bonds" -> "Unit(s)"
    "Gold" -> "gr"
    else -> ""
}

fun priceUnitSuffix(category: String, symbol: String): String = when (category) {
    "Crypto" -> "per ${symbol.trim().ifEmpty { "[symbol]" }}"
    "ID Stocks", "US Stocks" -> "per share"
    "Mutual Fund", "Bonds" -> "per unit"
    "Gold" -> "per gram"
    else -> ""
}

fun currencySymbolFor(currency: String): String = when (currency) {
    "USD" -> "$"
    else -> "Rp"
}
