package com.example.investa

import androidx.room.Dao
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

    @Insert
    suspend fun insert(transaction: TransactionEntity): Long

    @Update
    suspend fun update(transaction: TransactionEntity)
}
