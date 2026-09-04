package com.example.investa.utils

import android.content.Context
import com.example.investa.R

const val SELECT_CATEGORY = "Select Category"

val assetCategories = listOf("Crypto", "ID Stocks", "US Stocks", "Bonds", "Mutual Fund", "Gold")

fun currencySymbolFor(currency: String): String = when (currency) {
    "USD" -> "$"
    else -> "Rp"
}

fun localizedCategory(context: Context, category: String): String = when (category) {
    "All" -> context.getString(R.string.category_all)
    SELECT_CATEGORY -> context.getString(R.string.select_category)
    "Crypto" -> context.getString(R.string.category_crypto)
    "ID Stocks" -> context.getString(R.string.category_id_stocks)
    "US Stocks" -> context.getString(R.string.category_us_stocks)
    "Bonds" -> context.getString(R.string.category_bonds)
    "Mutual Fund" -> context.getString(R.string.category_mutual_fund)
    "Gold" -> context.getString(R.string.category_gold)
    "Cash" -> context.getString(R.string.category_cash)
    "Others" -> context.getString(R.string.category_others)
    else -> category
}

fun localizedCurrencyName(context: Context, currency: com.example.investa.data.entity.CurrencyEntity): String =
    when (currency.code) {
        "USD" -> context.getString(R.string.us_dollar)
        "IDR" -> context.getString(R.string.indonesian_rupiah)
        else -> currency.name
    }

fun quantityUnitHint(context: Context, category: String, symbol: String): String = when (category) {
    "Crypto" -> symbol.trim().ifEmpty { "[symbol]" }
    "ID Stocks", "US Stocks" -> context.getString(R.string.unit_shares)
    "Mutual Fund", "Bonds" -> context.getString(R.string.unit_units)
    "Gold" -> context.getString(R.string.unit_grams)
    else -> ""
}

fun priceUnitSuffix(context: Context, category: String, symbol: String): String = when (category) {
    "Crypto" -> context.getString(R.string.per_symbol, symbol.trim().ifEmpty { "[symbol]" })
    "ID Stocks", "US Stocks" -> context.getString(R.string.per_share)
    "Mutual Fund", "Bonds" -> context.getString(R.string.per_unit)
    "Gold" -> context.getString(R.string.per_gram)
    else -> ""
}
