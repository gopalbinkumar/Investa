package com.example.investa

import kotlinx.coroutines.flow.Flow

class CurrencyRepository(private val currencyDao: CurrencyDao) {
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

    suspend fun update(currency: CurrencyEntity) = currencyDao.upsert(currency)
}
