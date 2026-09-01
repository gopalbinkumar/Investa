package com.example.investa.data.dao

import com.example.investa.data.entity.TransactionEntity

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface TransactionDao {
    @Query("SELECT * FROM transactions WHERE assetId = :assetId ORDER BY date DESC, id DESC")
    fun observeTransactions(assetId: Long): Flow<List<TransactionEntity>>

    @Query("SELECT * FROM transactions ORDER BY date ASC, id ASC")
    fun observeAllTransactions(): Flow<List<TransactionEntity>>

    @Query("SELECT * FROM transactions ORDER BY date ASC, id ASC")
    suspend fun getAllTransactions(): List<TransactionEntity>

    @Query("SELECT COALESCE(SUM(total - costBasis), 0.0) FROM transactions WHERE UPPER(action) = 'SELL' AND currency = :currencyCode")
    fun observeTotalRealizedPL(currencyCode: String): Flow<Double>

    @Query("SELECT COALESCE(SUM(total - costBasis), 0.0) FROM transactions WHERE UPPER(action) = 'SELL' AND currency = :currencyCode")
    suspend fun getTotalRealizedPL(currencyCode: String): Double

    @Query("SELECT * FROM transactions WHERE id = :id LIMIT 1")
    suspend fun findById(id: Long): TransactionEntity?

    @Insert
    suspend fun insert(transaction: TransactionEntity): Long

    @Update
    suspend fun update(transaction: TransactionEntity)

    @Delete
    suspend fun delete(transaction: TransactionEntity)
}
