package com.example.investa.data.backup

import androidx.room.withTransaction
import com.example.investa.data.InvestaDatabase
import com.example.investa.data.entity.AppPreferenceEntity
import com.example.investa.data.entity.AssetEntity
import com.example.investa.data.entity.CashAccountEntity
import com.example.investa.data.entity.CurrencyEntity
import com.example.investa.data.entity.TransactionEntity
import org.json.JSONArray
import org.json.JSONObject

internal data class BackupData(
    val assets: List<AssetEntity>, val transactions: List<TransactionEntity>,
    val cashAccounts: List<CashAccountEntity>, val currencies: List<CurrencyEntity>,
    val preferences: AppPreferenceEntity
)

internal object BackupJsonRestorer {
    fun parse(text: String): BackupData {
        val root = JSONObject(text)
        require(root.getInt("schemaVersion") == 1) { "Unsupported backup version" }
        val assets = root.getJSONArray("assets").map { o -> AssetEntity(o.getLong("id"), o.getString("name"), o.getString("symbol"), o.getString("category"), o.getDouble("quantity"), o.getDouble("investedAmount"), o.getDouble("averagePrice"), o.getDouble("currentPrice"), o.getString("currency"), o.getString("notes"), o.getLong("createdAt"), o.getLong("updatedAt")) }
        val assetIds = assets.map { it.id }.toSet()
        val transactions = root.getJSONArray("transactions").map { o -> TransactionEntity(o.getLong("id"), o.getLong("assetId"), o.getString("action"), o.getLong("date"), o.getDouble("quantity"), o.getDouble("price"), o.getDouble("fee"), o.getDouble("total"), o.getDouble("costBasis"), o.getString("currency"), o.optDouble("exchangeRateToIdr", if (o.getString("currency") == "USD") 16_500.0 else 1.0), o.getString("notes"), o.getLong("createdAt"), o.getLong("updatedAt")) }
        require(transactions.all { it.assetId in assetIds }) { "Transaction references a missing asset" }
        val cash = root.getJSONArray("cashAccounts").map { o -> CashAccountEntity(o.getLong("id"), o.getString("currencyCode"), o.getDouble("balance"), o.getLong("createdAt"), o.getLong("updatedAt")) }
        val currencies = root.getJSONArray("currencies").map { o -> CurrencyEntity(o.getLong("id"), o.getString("code"), o.getString("name"), o.getString("symbol"), o.getDouble("exchangeRate"), o.getLong("updatedAt"), o.getBoolean("isActive")) }
        val preferences = root.getJSONObject("preferences")
        return BackupData(assets, transactions, cash, currencies, AppPreferenceEntity(primaryCurrencyCode = preferences.getString("primaryCurrency"), numberFormatStyle = preferences.getString("numberFormatStyle")))
    }

    suspend fun restore(database: InvestaDatabase, backup: BackupData) = database.withTransaction {
        database.performanceSnapshotDao().clearAssetPrices()
        database.performanceSnapshotDao().clearCurrencyRates()
        database.performanceSnapshotDao().clearCashBalances()
        database.transactionDao().clearAll()
        database.assetDao().clearAll()
        database.cashAccountDao().clearAll()
        database.currencyDao().clearAll()
        if (backup.assets.isNotEmpty()) database.assetDao().insertAllForBackup(backup.assets)
        if (backup.transactions.isNotEmpty()) database.transactionDao().insertAllForBackup(backup.transactions)
        if (backup.cashAccounts.isNotEmpty()) database.cashAccountDao().insertAllForBackup(backup.cashAccounts)
        if (backup.currencies.isNotEmpty()) database.currencyDao().insertAllForBackup(backup.currencies)
        database.appPreferenceDao().upsert(backup.preferences)
    }

    private fun <T> JSONArray.map(transform: (JSONObject) -> T): List<T> = List(length()) { index -> transform(getJSONObject(index)) }
}
