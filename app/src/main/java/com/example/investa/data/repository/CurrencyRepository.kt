package com.example.investa.data.repository

import com.example.investa.data.InvestaDatabase
import com.example.investa.data.entity.CurrencyEntity
import com.example.investa.data.entity.CurrencyRateSnapshotEntity

import kotlinx.coroutines.flow.Flow

class CurrencyRepository(private val database: InvestaDatabase) {
    private val currencyDao = database.currencyDao()
    fun observeActiveCurrencies(): Flow<List<CurrencyEntity>> =
        currencyDao.observeActiveCurrencies()

    suspend fun ensureDefaults() {
        val now = System.currentTimeMillis()
        currencyDao.insert(
            CurrencyEntity(
                code = "IDR",
                name = "Indonesian Rupiah",
                symbol = "Rp",
                exchangeRate = 1.0,
                updatedAt = now,
                isActive = true
            )
        )
        currencyDao.insert(
            CurrencyEntity(
                code = "USD",
                name = "US Dollar",
                symbol = "$",
                exchangeRate = 16500.0,
                updatedAt = now,
                isActive = true
            )
        )
    }

    suspend fun update(currency: CurrencyEntity) {
        currencyDao.upsert(currency)
        if (currency.code == "USD") {
            val now = System.currentTimeMillis()
            database.performanceSnapshotDao().upsertCurrencyRates(
                listOf(CurrencyRateSnapshotEntity("USD", startOfDay(now), currency.exchangeRate, now))
            )
        }
    }

    private fun startOfDay(time: Long): Long = java.util.Calendar.getInstance().apply {
        timeInMillis = time
        set(java.util.Calendar.HOUR_OF_DAY, 0); set(java.util.Calendar.MINUTE, 0)
        set(java.util.Calendar.SECOND, 0); set(java.util.Calendar.MILLISECOND, 0)
    }.timeInMillis
}
