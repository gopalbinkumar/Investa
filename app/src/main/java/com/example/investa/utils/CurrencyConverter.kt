package com.example.investa.utils

import com.example.investa.model.Asset

fun convertCurrencyAmount(
    amount: Double,
    fromCurrency: String,
    toCurrency: String,
    usdExchangeRate: Double
): Double {
    if (fromCurrency == toCurrency) return amount
    val safeUsdExchangeRate = usdExchangeRate.coerceAtLeast(1.0)
    val amountInIdr = if (fromCurrency == "USD") amount * safeUsdExchangeRate else amount
    return if (toCurrency == "USD") amountInIdr / safeUsdExchangeRate else amountInIdr
}

fun Asset.toDisplayCurrency(targetCurrency: String, usdExchangeRate: Double): Asset {
    val multiplier = convertCurrencyAmount(1.0, currency, targetCurrency, usdExchangeRate)
    fun convertAmount(value: String): String? = parseMoneyInput(value)?.let {
        formatAmount(it * multiplier, targetCurrency)
    }
    fun convertSignedAmount(value: String): String? = parseMoneyInput(value)?.let { amount ->
        val signedAmount = if (value.trimStart().startsWith("-")) -amount else amount
        formatSignedAmount(signedAmount * multiplier, targetCurrency)
    }
    return copy(
        value = convertAmount(value) ?: value,
        invested = convertAmount(invested) ?: invested,
        profit = convertSignedAmount(profit) ?: profit,
        averagePrice = convertAmount(averagePrice) ?: averagePrice,
        currentPrice = convertAmount(currentPrice) ?: currentPrice,
        currency = targetCurrency
    )
}

fun Asset.toIdrDisplay(usdExchangeRate: Double): Asset =
    toDisplayCurrency("IDR", usdExchangeRate)
