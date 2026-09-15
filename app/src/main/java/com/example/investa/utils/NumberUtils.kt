package com.example.investa.utils

import android.text.Editable
import android.text.TextWatcher
import android.text.method.DigitsKeyListener
import android.widget.EditText
import java.math.BigDecimal
import java.text.NumberFormat
import kotlin.math.abs

fun parseTransactionQuantity(value: String): Double? = parseMoneyInput(value.substringBefore(" "))

fun parseMoneyInput(value: String): Double? {
    val source = value.trim()
    val currency = if (source.contains('$') || source.startsWith("USD", ignoreCase = true)) {
        "USD"
    } else {
        "IDR"
    }
    val parts = splitEditableAmount(source, currency) ?: return null
    val normalized = parts.first + (parts.second?.let { ".${it}" } ?: "")
    return normalized.toDoubleOrNull()
}

fun formatEditableAmount(
    value: String,
    currency: String,
    decimalMode: Boolean? = null
): String {
    val parts = splitEditableAmount(value, currency, decimalMode) ?: return ""
    val integerFormatter = NumberFormat.getIntegerInstance(NumberFormatStyleManager.current.locale)
    val integer = integerFormatter.format(BigDecimal(parts.first))
    return integer + (parts.second?.let { "${NumberFormatStyleManager.current.decimalSeparator}$it" } ?: "")
}

fun installMoneyInputFormatter(input: EditText, currencyProvider: () -> String) =
    installNumericInputFormatter(input, currencyProvider, true)

fun installDecimalInputFormatter(input: EditText) =
    installNumericInputFormatter(input, { "IDR" }, false)

private fun installNumericInputFormatter(
    input: EditText,
    currencyProvider: () -> String,
    includeCurrencyPrefix: Boolean
) {
    input.keyListener = DigitsKeyListener.getInstance("0123456789.,")
    var isFormatting = false
    var decimalMode = input.text.toString().contains(
        NumberFormatStyleManager.current.decimalSeparator
    )
    input.addTextChangedListener(object : TextWatcher {
        override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit

        override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
            if (isFormatting) return
            val insertedText = s?.toString()
                ?.substring(start, (start + count).coerceAtMost(s.length))
                .orEmpty()
            val insertedDecimalSeparator = insertedText.contains(
                NumberFormatStyleManager.current.decimalSeparator
            )
            if (insertedDecimalSeparator) decimalMode = true
            else if (s != null && !s.toString().contains(
                    NumberFormatStyleManager.current.decimalSeparator
                )
            ) {
                decimalMode = false
            }
            if (s?.none(Char::isDigit) != false) decimalMode = false
        }

        override fun afterTextChanged(editable: Editable?) {
            if (isFormatting) return
            val source = editable?.toString().orEmpty()
            if (source.trimEnd().endsWith(NumberFormatStyleManager.current.decimalSeparator)) {
                decimalMode = true
            }
            val formattedBody = formatEditableAmount(source, currencyProvider(), decimalMode)
            val formattedWithSeparator = if (includeCurrencyPrefix && formattedBody.isNotEmpty()) {
                if (currencyProvider() == "IDR") "Rp$formattedBody" else "\$$formattedBody"
            } else {
                formattedBody
            }
            if (source != formattedWithSeparator) {
                isFormatting = true
                input.setText(formattedWithSeparator)
                input.setSelection(formattedWithSeparator.length)
                isFormatting = false
            }
        }
    })
}

fun reformatMoneyInput(input: EditText, currency: String) {
    val amount = parseMoneyInput(input.text.toString()) ?: return
    val formatted = formatInputAmount(amount, currency)
    if (input.text.toString() != formatted) {
        input.setText(formatted)
        input.setSelection(formatted.length)
    }
}

fun formatInputAmount(amount: Double, currency: String): String {
    val formatter = NumberFormat.getNumberInstance(NumberFormatStyleManager.current.locale).apply {
        minimumFractionDigits = 0
        maximumFractionDigits = 8
    }
    val formatted = formatter.format(amount)
    return "${currencySymbolFor(currency)}$formatted"
}

fun formatQuantityValue(quantity: Double): String {
    val formatter = NumberFormat.getNumberInstance(NumberFormatStyleManager.current.locale).apply {
        minimumFractionDigits = 0
        maximumFractionDigits = 8
    }
    return formatter.format(quantity)
}

fun formatCompactQuantityValue(quantity: Double): String {
    val plain = BigDecimal.valueOf(quantity)
        .stripTrailingZeros()
        .toPlainString()
    val sign = if (plain.startsWith("-")) "-" else ""
    val unsigned = plain.removePrefix("-")
    val decimalIndex = unsigned.indexOf('.')
    if (decimalIndex < 0) {
        val formattedInteger = NumberFormat.getIntegerInstance(
            NumberFormatStyleManager.current.locale
        ).format(BigDecimal(unsigned))
        return sign + formattedInteger
    }

    val integerPart = unsigned.substring(0, decimalIndex)
    val fraction = unsigned.substring(decimalIndex + 1)
    val firstNonZero = fraction.indexOfFirst { it != '0' }
    if (firstNonZero < 0) return sign + integerPart

    val decimalsToKeep = if (integerPart == "0") {
        // Keep three meaningful digits after any leading fractional zeroes.
        firstNonZero + 3
    } else {
        3
    }
    val compactFraction = fraction
        .take(decimalsToKeep)
        .trimEnd('0')
    val formattedIntegerPart = NumberFormat.getIntegerInstance(
        NumberFormatStyleManager.current.locale
    ).format(BigDecimal(integerPart))
    return sign + formattedIntegerPart +
        if (compactFraction.isEmpty()) "" else "${NumberFormatStyleManager.current.decimalSeparator}$compactFraction"
}

fun formatQuantityForCard(quantityWithUnit: String): String {
    val numericPart = quantityWithUnit.substringBefore(' ')
    val amount = parseTransactionQuantity(numericPart)
        ?: return quantityWithUnit.replace('.', NumberFormatStyleManager.current.decimalSeparator)
    val unit = quantityWithUnit.substringAfter(' ', "")
    val formatted = formatCompactQuantityValue(amount)
    val displayUnit = if (unit.endsWith("(s)")) {
        val singularUnit = unit.removeSuffix("(s)")
        if (amount != 1.0) "${singularUnit}s" else singularUnit
    } else {
        unit
    }
    return if (displayUnit.isBlank()) formatted else "$formatted $displayUnit"
}

fun formatQuantityWithUnit(quantity: Double, unit: String): String =
    "${formatQuantityValue(quantity)} $unit"

fun formatTransactionQuantity(quantity: Double, symbol: String): String =
    formatQuantityWithUnit(quantity, symbol)

fun calculateTransactionTotal(isBuy: Boolean, quantity: Double, price: Double, fee: Double): Double {
    val gross = quantity * price
    return if (isBuy) gross + fee else (gross - fee).coerceAtLeast(0.0)
}

fun formatAmount(amount: Double, currency: String, maxFractionDigits: Int = 8): String =
    formatAmount(amount, currency, currencySymbolFor(currency), maxFractionDigits)

fun formatAmount(
    amount: Double,
    currency: String,
    currencySymbol: String,
    maxFractionDigits: Int = 8
): String {
    val isUsd = currency.equals("USD", ignoreCase = true)
    val absoluteAmount = abs(amount)
    val effectiveMaxFractionDigits = when {
        !isUsd -> maxFractionDigits
        absoluteAmount > 0.0 && absoluteAmount < 1.0 -> 3
        else -> 2
    }
    val formatter = NumberFormat.getNumberInstance(NumberFormatStyleManager.current.locale).apply {
        minimumFractionDigits = 0
        maximumFractionDigits = effectiveMaxFractionDigits
    }
    val prefix = currencySymbol.ifBlank { currencySymbolFor(currency) }
    return "$prefix${formatter.format(amount)}"
}

fun formatSignedAmount(amount: Double, currency: String, maxFractionDigits: Int = 8): String =
    formatSignedAmount(amount, currency, currencySymbolFor(currency), maxFractionDigits)

fun formatSignedAmount(
    amount: Double,
    currency: String,
    currencySymbol: String,
    maxFractionDigits: Int = 8
): String {
    val sign = when {
        amount > 0 -> "+"
        amount < 0 -> "-"
        else -> ""
    }
    return sign + formatAmount(abs(amount), currency, currencySymbol, maxFractionDigits)
}

private fun splitEditableAmount(
    value: String,
    @Suppress("UNUSED_PARAMETER") currency: String,
    decimalMode: Boolean? = null
): Pair<String, String?>? {
    val numeric = value.filter { it.isDigit() || it == '.' || it == ',' }
    if (numeric.isEmpty()) return null

    val primarySeparator = NumberFormatStyleManager.current.decimalSeparator
    val alternateSeparator = if (primarySeparator == ',') '.' else ','
    val primaryIndex = numeric.lastIndexOf(primarySeparator)
    val alternateIndex = numeric.lastIndexOf(alternateSeparator)
    val decimalIndex = if (decimalMode != null) {
        if (!decimalMode) {
            -1
        } else if (primaryIndex >= 0) {
            primaryIndex
        } else if (
            alternateIndex >= 0 &&
            (numeric.startsWith("0$alternateSeparator") ||
                numeric.endsWith(alternateSeparator) ||
                numeric.substringAfterLast(alternateSeparator).length != 3)
        ) {
            alternateIndex
        } else {
            -1
        }
    } else {
        when {
            primaryIndex >= 0 && alternateIndex >= 0 -> maxOf(primaryIndex, alternateIndex)
            primaryIndex >= 0 -> primaryIndex
            alternateIndex >= 0 && numeric.startsWith("0$alternateSeparator") -> alternateIndex
            alternateIndex >= 0 && numeric.count { it == alternateSeparator } == 1 &&
                numeric.substringAfterLast(alternateSeparator).length != 3 -> alternateIndex
            else -> -1
        }
    }

    return if (decimalIndex >= 0) {
        numeric.substring(0, decimalIndex).filter(Char::isDigit).ifEmpty { "0" } to
            numeric.substring(decimalIndex + 1).filter(Char::isDigit)
    } else {
        numeric.filter(Char::isDigit) to null
    }
}
