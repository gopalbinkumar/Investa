package com.example.investa.data.repository

import androidx.room.withTransaction
import com.example.investa.data.InvestaDatabase
import com.example.investa.data.entity.CashAccountEntity
import kotlinx.coroutines.flow.Flow

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
            } else {
                cashDao.updateBalance(account.id, account.balance + amount, now)
            }
        }
    }

    suspend fun updateCashBalance(account: CashAccountEntity, newBalance: Double) {
        require(newBalance >= 0.0) { "Balance cannot be negative" }
        cashDao.updateCashAccount(
            account.copy(
                balance = newBalance,
                updatedAt = System.currentTimeMillis()
            )
        )
    }

    suspend fun deleteCashAccount(account: CashAccountEntity) {
        require(account.balance == 0.0) { "Cash account must have zero balance" }
        cashDao.deleteCashAccount(account)
    }
}
