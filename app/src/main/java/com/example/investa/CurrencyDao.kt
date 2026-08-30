package com.example.investa

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface CurrencyDao {
    @Query("SELECT * FROM currencies WHERE isActive = 1 ORDER BY code ASC")
    fun observeActiveCurrencies(): Flow<List<CurrencyEntity>>

    @Query("SELECT * FROM currencies WHERE code = :code LIMIT 1")
    suspend fun findByCode(code: String): CurrencyEntity?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(currency: CurrencyEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(currency: CurrencyEntity)
}
