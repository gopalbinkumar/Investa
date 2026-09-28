package com.example.investa.data.backup

import com.example.investa.data.entity.AssetEntity
import com.example.investa.data.entity.CashAccountEntity
import com.example.investa.data.entity.CurrencyEntity
import com.example.investa.data.entity.TransactionEntity
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

internal object BackupJsonExporter {
    private const val SCHEMA_VERSION = 1

    fun create(
        assets: List<AssetEntity>,
        transactions: List<TransactionEntity>,
        cashAccounts: List<CashAccountEntity>,
        currencies: List<CurrencyEntity>,
        primaryCurrency: String,
        numberFormatStyle: String
    ): String = JSONObject().apply {
        put("schemaVersion", SCHEMA_VERSION)
        put("exportedAt", System.currentTimeMillis())
        put("assets", assets.toJsonArray { asset -> JSONObject().apply {
            put("id", asset.id); put("name", asset.name); put("symbol", asset.symbol)
            put("category", asset.category); put("quantity", asset.quantity)
            put("investedAmount", asset.investedAmount); put("averagePrice", asset.averagePrice)
            put("currentPrice", asset.currentPrice); put("currency", asset.currency)
            put("notes", asset.notes); put("createdAt", asset.createdAt); put("updatedAt", asset.updatedAt)
        } })
        put("transactions", transactions.toJsonArray { transaction -> JSONObject().apply {
            put("id", transaction.id); put("assetId", transaction.assetId); put("action", transaction.action)
            put("date", transaction.date); put("quantity", transaction.quantity); put("price", transaction.price)
            put("fee", transaction.fee); put("total", transaction.total); put("costBasis", transaction.costBasis)
            put("currency", transaction.currency); put("exchangeRateToIdr", transaction.exchangeRateToIdr); put("notes", transaction.notes)
            put("createdAt", transaction.createdAt); put("updatedAt", transaction.updatedAt)
        } })
        put("cashAccounts", cashAccounts.toJsonArray { cash -> JSONObject().apply {
            put("id", cash.id); put("currencyCode", cash.currencyCode); put("balance", cash.balance)
            put("createdAt", cash.createdAt); put("updatedAt", cash.updatedAt)
        } })
        put("currencies", currencies.toJsonArray { currency -> JSONObject().apply {
            put("id", currency.id); put("code", currency.code); put("name", currency.name)
            put("symbol", currency.symbol); put("exchangeRate", currency.exchangeRate)
            put("updatedAt", currency.updatedAt); put("isActive", currency.isActive)
        } })
        put("preferences", JSONObject().apply {
            put("primaryCurrency", primaryCurrency); put("numberFormatStyle", numberFormatStyle)
        })
    }.toString(2)

    fun suggestedFileName(now: Long = System.currentTimeMillis()): String {
        val formatter = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }
        return "investa_backup_${formatter.format(Date(now))}.investa.json"
    }

    private fun <T> List<T>.toJsonArray(transform: (T) -> JSONObject): JSONArray = JSONArray().also { array ->
        forEach { item -> array.put(transform(item)) }
    }
}
