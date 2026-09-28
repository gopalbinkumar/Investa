package com.example.investa.data.repository

import com.example.investa.data.InvestaDatabase
import com.example.investa.data.entity.AssetEntity
import com.example.investa.data.entity.AssetPriceSnapshotEntity
import com.example.investa.data.entity.CurrencyRateSnapshotEntity
import com.example.investa.data.entity.TransactionEntity
import java.util.Calendar
import kotlin.math.max

enum class PerformanceRange(val months: Int?) {
    ONE_MONTH(1), THREE_MONTHS(3), SIX_MONTHS(6), ONE_YEAR(12), ALL(null)
}

data class PortfolioPerformancePoint(
    val day: Long,
    val invested: Double,
    val current: Double
)

class PortfolioPerformanceRepository(private val database: InvestaDatabase) {
    private val snapshots = database.performanceSnapshotDao()

    suspend fun load(range: PerformanceRange, displayCurrency: String): List<PortfolioPerformancePoint> {
        val assets = database.assetDao().getAllAssets()
        val transactions = database.transactionDao().getAllTransactions()
        val cashAccounts = database.cashAccountDao().getAllCashAccounts()
        val currencies = database.currencyDao().getActiveCurrencies()
        if (assets.isEmpty() && cashAccounts.none { it.balance != 0.0 }) return emptyList()

        val today = startOfDay(System.currentTimeMillis())
        val firstActivity = (assets.map { it.createdAt } + transactions.map { it.date } + cashAccounts.map { it.createdAt })
            .minOrNull()?.let(::startOfDay) ?: today
        val fromDay = when (range.months) {
            null -> firstActivity
            else -> Calendar.getInstance().apply {
                timeInMillis = today
                add(Calendar.MONTH, -range.months)
            }.timeInMillis
        }.coerceAtLeast(firstActivity)

        val currentUsdRate = currencies.firstOrNull { it.code == "USD" }?.exchangeRate ?: 16_500.0
        ensureLocalSnapshots(assets, transactions, currencies)
        val usdRates = snapshots.currencyRates("USD", fromDay, today)
            .associate { it.day to it.rateToIdr }
        val assetPrices = snapshots.assetPrices(assets.map { it.id }, fromDay, today)
            .groupBy { it.assetId }
            .mapValues { (_, values) -> values.sortedBy { it.day } }
        val cashSnapshots = snapshots.cashBalances(fromDay, today)
            .associateBy { "${it.currencyCode}:${it.day}" }

        return generateSequence(fromDay) { day -> (day + DAY_MILLIS).takeIf { it <= today } }
            .map { day ->
                var invested = 0.0
                var current = 0.0
                assets.forEach { asset ->
                    if (firstAssetActivity(asset, transactions) > day) return@forEach
                    val holding = holdingAt(asset, transactions, day, usdRates, currentUsdRate)
                    val price = assetPrices[asset.id].lastPriceAt(day) ?: asset.currentPrice
                    val rate = rateOn(asset.currency, day, usdRates, currentUsdRate)
                    invested += holding.costBasis * rate
                    current += holding.quantity * price * rate
                }
                cashAccounts.forEach { account ->
                    if (account.createdAt > day) return@forEach
                    val stored = cashSnapshots["${account.currencyCode}:$day"]?.balance
                    val balance = stored ?: cashAt(account.currencyCode, account.balance, transactions, day)
                    val cashValue = balance * rateOn(account.currencyCode, day, usdRates, currentUsdRate)
                    invested += cashValue
                    current += cashValue
                }
                val displayRate = rateOn(displayCurrency, day, usdRates, currentUsdRate)
                PortfolioPerformancePoint(day, invested / displayRate, current / displayRate)
            }
            .toList()
    }

    private suspend fun ensureLocalSnapshots(
        assets: List<AssetEntity>,
        transactions: List<TransactionEntity>,
        currencies: List<com.example.investa.data.entity.CurrencyEntity>
    ) {
        val now = System.currentTimeMillis()
        val assetById = assets.associateBy { it.id }
        val fromTransactions = transactions.mapNotNull { transaction ->
            val asset = assetById[transaction.assetId] ?: return@mapNotNull null
            val price = when {
                transaction.currency == asset.currency -> transaction.price
                transaction.currency == "USD" && asset.currency == "IDR" -> transaction.price * transaction.exchangeRateToIdr
                transaction.currency == "IDR" && asset.currency == "USD" -> transaction.price / transaction.exchangeRateToIdr
                else -> transaction.price
            }
            AssetPriceSnapshotEntity(asset.id, startOfDay(transaction.date), price, now)
        }
        snapshots.upsertAssetPrices(fromTransactions + assets.map { asset ->
            AssetPriceSnapshotEntity(asset.id, startOfDay(now), asset.currentPrice, now)
        })
        val rateSnapshots = transactions.asSequence()
            .filter { it.currency == "USD" && it.exchangeRateToIdr > 0.0 }
            .map { transaction -> CurrencyRateSnapshotEntity("USD", startOfDay(transaction.date), transaction.exchangeRateToIdr, now) }
            .toList() + currencies.filter { it.code == "USD" }.map { currency ->
            CurrencyRateSnapshotEntity("USD", startOfDay(now), currency.exchangeRate, now)
        }
        if (rateSnapshots.isNotEmpty()) snapshots.upsertCurrencyRates(rateSnapshots)
    }

    private fun rateOn(currency: String, day: Long, usdRates: Map<Long, Double>, fallbackUsdRate: Double): Double = when (currency) {
        "USD" -> usdRates.filterKeys { it <= day }.maxByOrNull { it.key }?.value ?: fallbackUsdRate
        else -> 1.0
    }

    private fun firstAssetActivity(asset: AssetEntity, transactions: List<TransactionEntity>): Long =
        minOf(asset.createdAt, transactions.filter { it.assetId == asset.id }.minOfOrNull { it.date } ?: asset.createdAt)

    private fun holdingAt(
        asset: AssetEntity,
        transactions: List<TransactionEntity>,
        day: Long,
        usdRates: Map<Long, Double>,
        currentUsdRate: Double
    ): Holding {
        var quantity = asset.quantity
        var costBasis = asset.investedAmount
        transactions.asSequence().filter { it.assetId == asset.id && it.date > day }
            .sortedWith(compareByDescending<TransactionEntity> { it.date }.thenByDescending { it.id })
            .forEach { transaction ->
                val transactionCost = transaction.total *
                    rateOn(transaction.currency, day, usdRates, currentUsdRate) /
                    rateOn(asset.currency, day, usdRates, currentUsdRate)
                if (transaction.action.equals("BUY", true)) {
                    quantity -= transaction.quantity
                    costBasis -= transactionCost
                } else if (transaction.action.equals("SELL", true)) {
                    quantity += transaction.quantity
                    costBasis += if (transaction.costBasis > 0) transaction.costBasis else max(0.0, costBasis / quantity) * transaction.quantity
                }
            }
        return Holding(quantity.coerceAtLeast(0.0), costBasis.coerceAtLeast(0.0))
    }

    private fun cashAt(currency: String, currentBalance: Double, transactions: List<TransactionEntity>, day: Long): Double =
        transactions.asSequence().filter { it.currency == currency && it.date > day }.fold(currentBalance) { balance, transaction ->
            if (transaction.action.equals("BUY", true)) balance + transaction.total else balance - transaction.total
        }.coerceAtLeast(0.0)

    private fun List<AssetPriceSnapshotEntity>?.lastPriceAt(day: Long): Double? =
        this?.asSequence()?.filter { it.day <= day }?.lastOrNull()?.price

    private data class Holding(val quantity: Double, val costBasis: Double)

    private fun startOfDay(time: Long): Long = Calendar.getInstance().apply {
        timeInMillis = time
        set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
    }.timeInMillis

    private companion object { const val DAY_MILLIS = 86_400_000L }
}
