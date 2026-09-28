package com.example.investa.data.repository

import androidx.room.withTransaction
import com.example.investa.data.InvestaDatabase
import com.example.investa.data.entity.CashAccountEntity
import com.example.investa.data.entity.CashBalanceSnapshotEntity
import kotlinx.coroutines.flow.Flow
import java.util.Calendar

class CashRepository(private val database: InvestaDatabase) {
    private val cashDao = database.cashAccountDao()

    fun observeAllCashAccounts(): Flow<List<CashAccountEntity>> =
        cashDao.observeAllCashAccounts()

    suspend fun ensureDefaultAccounts() {
        database.withTransaction {
            val now = System.currentTimeMillis()
            listOf("IDR", "USD").forEach { currencyCode ->
                if (cashDao.getCashAccountByCurrency(currencyCode) == null) {
                    cashDao.insertCashAccount(
                        CashAccountEntity(
                            currencyCode = currencyCode,
                            balance = 0.0,
                            createdAt = now,
                            updatedAt = now
                        )
                    )
                    database.performanceSnapshotDao().upsertCashBalance(
                        CashBalanceSnapshotEntity(currencyCode, startOfDay(now), 0.0, now)
                    )
                }
            }
        }
    }

    suspend fun addCash(currencyCode: String, amount: Double) {
        require(amount > 0.0) { "Amount must be greater than zero" }
        database.withTransaction {
            val now = System.currentTimeMillis()
            val account = cashDao.getCashAccountByCurrency(currencyCode)
            if (account == null) {
                cashDao.insertCashAccount(
                    CashAccountEntity(
                        currencyCode = currencyCode,
                        balance = amount,
                        createdAt = now,
                        updatedAt = now
                    )
                )
                database.performanceSnapshotDao().upsertCashBalance(
                    CashBalanceSnapshotEntity(currencyCode, startOfDay(now), amount, now)
                )
            } else {
                val balance = account.balance + amount
                cashDao.updateBalance(account.id, balance, now)
                database.performanceSnapshotDao().upsertCashBalance(
                    CashBalanceSnapshotEntity(currencyCode, startOfDay(now), balance, now)
                )
            }
        }
    }

    suspend fun updateCashBalance(account: CashAccountEntity, newBalance: Double) {
        require(newBalance >= 0.0) { "Balance cannot be negative" }
        val now = System.currentTimeMillis()
        cashDao.updateCashAccount(
            account.copy(
                balance = newBalance,
                updatedAt = now
            )
        )
        database.performanceSnapshotDao().upsertCashBalance(
            CashBalanceSnapshotEntity(account.currencyCode, startOfDay(now), newBalance, now)
        )
    }

    suspend fun deleteCashAccount(account: CashAccountEntity) {
        require(account.balance == 0.0) { "Cash account must have zero balance" }
        cashDao.deleteCashAccount(account)
    }

    private fun startOfDay(time: Long): Long = Calendar.getInstance().apply {
        timeInMillis = time
        set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
    }.timeInMillis
}
