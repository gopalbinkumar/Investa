package com.example.investa.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.investa.data.entity.AssetPriceSnapshotEntity
import com.example.investa.data.entity.CashBalanceSnapshotEntity
import com.example.investa.data.entity.CurrencyRateSnapshotEntity

@Dao
interface PerformanceSnapshotDao {
    @Query("SELECT * FROM asset_price_snapshots WHERE assetId IN (:assetIds) AND day BETWEEN :fromDay AND :toDay ORDER BY day ASC")
    suspend fun assetPrices(assetIds: List<Long>, fromDay: Long, toDay: Long): List<AssetPriceSnapshotEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAssetPrices(snapshots: List<AssetPriceSnapshotEntity>)

    @Query("SELECT * FROM currency_rate_snapshots WHERE currencyCode = :currencyCode AND day BETWEEN :fromDay AND :toDay ORDER BY day ASC")
    suspend fun currencyRates(currencyCode: String, fromDay: Long, toDay: Long): List<CurrencyRateSnapshotEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertCurrencyRates(snapshots: List<CurrencyRateSnapshotEntity>)

    @Query("SELECT * FROM cash_balance_snapshots WHERE day BETWEEN :fromDay AND :toDay ORDER BY day ASC")
    suspend fun cashBalances(fromDay: Long, toDay: Long): List<CashBalanceSnapshotEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertCashBalance(snapshot: CashBalanceSnapshotEntity)

    @Query("DELETE FROM asset_price_snapshots")
    suspend fun clearAssetPrices()

    @Query("DELETE FROM currency_rate_snapshots")
    suspend fun clearCurrencyRates()

    @Query("DELETE FROM cash_balance_snapshots")
    suspend fun clearCashBalances()
}
