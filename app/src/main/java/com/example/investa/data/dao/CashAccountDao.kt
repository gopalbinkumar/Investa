package com.example.investa.data.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.investa.data.entity.CashAccountEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface CashAccountDao {
    @Query("SELECT * FROM cash_accounts ORDER BY currencyCode ASC")
    fun observeAllCashAccounts(): Flow<List<CashAccountEntity>>

    @Query("SELECT * FROM cash_accounts ORDER BY currencyCode ASC")
    suspend fun getAllCashAccounts(): List<CashAccountEntity>

    @Query("SELECT * FROM cash_accounts WHERE currencyCode = :currencyCode LIMIT 1")
    suspend fun getCashAccountByCurrency(currencyCode: String): CashAccountEntity?

    @Query("SELECT balance FROM cash_accounts WHERE currencyCode = :currencyCode LIMIT 1")
    suspend fun getCashBalance(currencyCode: String): Double?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertCashAccount(account: CashAccountEntity): Long

    @Update
    suspend fun updateCashAccount(account: CashAccountEntity)

    @Delete
    suspend fun deleteCashAccount(account: CashAccountEntity)

    @Query("UPDATE cash_accounts SET balance = :balance, updatedAt = :updatedAt WHERE id = :id")
    suspend fun updateBalance(id: Long, balance: Double, updatedAt: Long)
}
