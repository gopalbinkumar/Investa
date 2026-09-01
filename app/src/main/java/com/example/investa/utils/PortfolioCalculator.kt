package com.example.investa.utils

import com.example.investa.data.entity.AssetEntity
import com.example.investa.data.entity.TransactionEntity
import com.example.investa.model.Asset
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

fun AssetEntity.toUiAsset(): Asset {
    val currentValueAmount = quantity * currentPrice
    val profitAmount = quantity * (currentPrice - averagePrice)
    val profitPercentage = if (averagePrice == 0.0) 0.0 else {
        (currentPrice - averagePrice) * 100.0 / averagePrice
    }
    val quantityUnit = when (category) {
        "Crypto" -> symbol
        "ID Stocks", "US Stocks" -> "share(s)"
        "Mutual Fund", "Bonds" -> "Unit(s)"
        "Gold" -> "gr"
        else -> "unit(s)"
    }
    val quantityText = "${formatQuantityValue(quantity)} $quantityUnit"
    return Asset(
        name = name,
        symbol = symbol,
        category = category,
        quantity = quantityText,
        value = formatAmount(currentValueAmount, currency),
        invested = formatAmount(investedAmount, currency),
        profit = formatSignedAmount(profitAmount, currency),
        profitPercent = String.format(Locale.US, "%+.2f%%", profitPercentage),
        averagePrice = formatAmount(averagePrice, currency),
        currentPrice = formatAmount(currentPrice, currency),
        notes = notes,
        addedOn = SimpleDateFormat("dd MMM yyyy", Locale.ENGLISH).format(Date(createdAt)),
        id = id,
        currency = currency
    )
}

fun Asset.withTransactionHistory(
    transactions: List<TransactionEntity>,
    maxFractionDigits: Int = 8,
    currencySymbol: String? = null
): Asset {
    if (transactions.isEmpty()) return this
    val displayCurrencySymbol = currencySymbol ?: currencySymbolFor(currency)
    var holdingQuantity = parseTransactionQuantity(quantity) ?: 0.0
    var costBasis = parseMoneyInput(invested) ?: 0.0
    val fallbackAveragePrice = parseMoneyInput(averagePrice) ?: 0.0
    if (costBasis == 0.0 && fallbackAveragePrice > 0.0) {
        costBasis = holdingQuantity * fallbackAveragePrice
    }
    transactions.sortedWith(compareBy<TransactionEntity> { it.date }.thenBy { it.id })
        .forEach { transaction ->
            if (transaction.action == "BUY") {
                holdingQuantity += transaction.quantity
                costBasis += transaction.quantity * transaction.price + transaction.fee
            } else if (transaction.action == "SELL") {
                val averageCost = if (holdingQuantity > 0.0) costBasis / holdingQuantity else 0.0
                holdingQuantity = (holdingQuantity - transaction.quantity).coerceAtLeast(0.0)
                costBasis = (costBasis - averageCost * transaction.quantity).coerceAtLeast(0.0)
            }
        }
    val currentPriceAmount = parseMoneyInput(currentPrice) ?: 0.0
    val currentValueAmount = holdingQuantity * currentPriceAmount
    val profitAmount = currentValueAmount - costBasis
    val profitPercentage = if (costBasis == 0.0) 0.0 else profitAmount * 100.0 / costBasis
    val unit = quantity.substringAfter(" ", symbol)
    return copy(
        quantity = formatQuantityWithUnit(holdingQuantity, unit),
        value = formatAmount(currentValueAmount, currency, displayCurrencySymbol, maxFractionDigits),
        invested = formatAmount(costBasis, currency, displayCurrencySymbol, maxFractionDigits),
        profit = formatSignedAmount(profitAmount, currency, displayCurrencySymbol, maxFractionDigits),
        profitPercent = String.format(Locale.US, "%+.2f%%", profitPercentage),
        averagePrice = formatAmount(
            if (holdingQuantity > 0.0) costBasis / holdingQuantity else 0.0,
            currency,
            displayCurrencySymbol,
            maxFractionDigits
        )
    )
}

fun Asset.withCalculatedCurrentValue(): Asset {
    val investedAmount = parseMoneyInput(invested)
    val averagePriceAmount = parseMoneyInput(averagePrice)
    val currentPriceAmount = parseMoneyInput(currentPrice)
    if (investedAmount == null || averagePriceAmount == null || averagePriceAmount <= 0.0 ||
        currentPriceAmount == null
    ) return this
    val currentValueAmount = investedAmount * currentPriceAmount / averagePriceAmount
    val profitAmount = currentValueAmount - investedAmount
    val profitPercentage = if (investedAmount == 0.0) 0.0 else profitAmount * 100.0 / investedAmount
    return copy(
        value = formatAmount(currentValueAmount, currency),
        profit = formatSignedAmount(profitAmount, currency),
        profitPercent = String.format(Locale.US, "%+.2f%%", profitPercentage)
    )
}

fun Asset.withAmountPrecision(maxFractionDigits: Int, currencySymbol: String? = null): Asset {
    val displayCurrencySymbol = currencySymbol ?: currencySymbolFor(currency)
    val signedProfit = parseMoneyInput(profit)?.let { amount ->
        if (profit.trimStart().startsWith("-")) -amount else amount
    }
    return copy(
        value = parseMoneyInput(value)?.let {
            formatAmount(it, currency, displayCurrencySymbol, maxFractionDigits)
        } ?: value,
        invested = parseMoneyInput(invested)?.let {
            formatAmount(it, currency, displayCurrencySymbol, maxFractionDigits)
        } ?: invested,
        profit = signedProfit?.let {
            formatSignedAmount(it, currency, displayCurrencySymbol, maxFractionDigits)
        } ?: profit,
        averagePrice = parseMoneyInput(averagePrice)?.let {
            formatAmount(it, currency, displayCurrencySymbol, maxFractionDigits)
        } ?: averagePrice,
        currentPrice = parseMoneyInput(currentPrice)?.let {
            formatAmount(it, currency, displayCurrencySymbol, maxFractionDigits)
        } ?: currentPrice
    )
}
