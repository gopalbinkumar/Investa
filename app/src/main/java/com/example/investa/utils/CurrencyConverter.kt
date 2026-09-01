package com.example.investa.utils

import com.example.investa.model.Asset

fun Asset.toIdrDisplay(usdExchangeRate: Double): Asset {
    val multiplier = if (currency == "USD") usdExchangeRate else 1.0
    fun convertAmount(value: String): String? = parseMoneyInput(value)?.let {
        formatAmount(it * multiplier, "IDR")
    }
    fun convertSignedAmount(value: String): String? = parseMoneyInput(value)?.let { amount ->
        val signedAmount = if (value.trimStart().startsWith("-")) -amount else amount
        formatSignedAmount(signedAmount * multiplier, "IDR")
    }
    return copy(
        value = convertAmount(value) ?: value,
        invested = convertAmount(invested) ?: invested,
        profit = convertSignedAmount(profit) ?: profit,
        averagePrice = convertAmount(averagePrice) ?: averagePrice,
        currentPrice = convertAmount(currentPrice) ?: currentPrice,
        currency = "IDR"
    )
}
