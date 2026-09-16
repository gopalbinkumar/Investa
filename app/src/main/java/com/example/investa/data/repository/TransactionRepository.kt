package com.example.investa.data.repository

import androidx.room.withTransaction
import com.example.investa.data.InvestaDatabase
import com.example.investa.data.entity.AssetEntity
import com.example.investa.data.entity.TransactionEntity
import kotlinx.coroutines.flow.Flow

class TransactionRepository(private val database: InvestaDatabase) {
    private val assetDao = database.assetDao()
    private val transactionDao = database.transactionDao()
    private val cashDao = database.cashAccountDao()
    private val currencyDao = database.currencyDao()

    fun observeTransactions(assetId: Long): Flow<List<TransactionEntity>> =
        transactionDao.observeTransactions(assetId)

    fun observeAllTransactions(): Flow<List<TransactionEntity>> =
        transactionDao.observeAllTransactions()

    suspend fun getAllTransactions(): List<TransactionEntity> =
        transactionDao.getAllTransactions()

    suspend fun getTransactionById(id: Long): TransactionEntity? =
        transactionDao.findById(id)

    suspend fun getTransactionHistoryPage(limit: Int, offset: Int): List<TransactionEntity> =
        transactionDao.getTransactionHistoryPage(limit, offset)

    suspend fun getTransactionHistoryPageMatchingAssets(
        searchQuery: String,
        limit: Int,
        offset: Int
    ): List<TransactionEntity> = transactionDao.getTransactionHistoryPageMatchingAssets(
        searchQuery,
        limit,
        offset
    )

    suspend fun getTransactionHistoryPageForAsset(
        assetId: Long,
        limit: Int,
        offset: Int
    ): List<TransactionEntity> =
        transactionDao.getTransactionHistoryPageForAsset(assetId, limit, offset)

    fun observeTotalRealizedPL(currencyCode: String): Flow<Double> =
        transactionDao.observeTotalRealizedPL(currencyCode)

    suspend fun getTotalRealizedPL(currencyCode: String): Double =
        transactionDao.getTotalRealizedPL(currencyCode)

    suspend fun addTransaction(transaction: TransactionEntity) {
        database.withTransaction {
            val asset = assetDao.findById(transaction.assetId)
                ?: error("Asset not found")
            val preparedTransaction = applyCashAndAssetTransaction(asset, transaction)
            transactionDao.insert(preparedTransaction)
        }
    }

    suspend fun updateTransaction(transaction: TransactionEntity) {
        database.withTransaction {
            val oldTransaction = transactionDao.findById(transaction.id)
                ?: error("Transaction not found")
            val asset = assetDao.findById(transaction.assetId)
                ?: error("Asset not found")
            reverseCashAndAssetTransaction(asset, oldTransaction)
            val revertedAsset = assetDao.findById(asset.id)
                ?: error("Asset not found")
            val preparedTransaction = applyCashAndAssetTransaction(revertedAsset, transaction)
            transactionDao.update(preparedTransaction)
        }
    }

    suspend fun deleteTransaction(transaction: TransactionEntity) {
        database.withTransaction {
            val existingTransaction = transactionDao.findById(transaction.id)
                ?: return@withTransaction
            val asset = assetDao.findById(existingTransaction.assetId)
                ?: error("Asset not found")
            reverseCashAndAssetTransaction(asset, existingTransaction)
            transactionDao.delete(existingTransaction)
        }
    }

    private suspend fun applyCashAndAssetTransaction(
        asset: AssetEntity,
        transaction: TransactionEntity
    ): TransactionEntity {
        validateTransaction(transaction, asset)
        val cash = cashDao.getCashAccountByCurrency(transaction.currency)
            ?: error("Cash account ${transaction.currency} not found")
        if (transaction.action == "BUY" && cash.balance < transaction.total) {
            error("Insufficient cash balance")
        }

        val totalInAssetCurrency = convertAmount(
            transaction.total,
            currencyRate(transaction.currency),
            currencyRate(asset.currency)
        )
        val averagePriceBeforeSell = currentAveragePrice(asset)
        val costBasis = if (transaction.action == "SELL") {
            convertAmount(
                transaction.quantity * averagePriceBeforeSell,
                currencyRate(asset.currency),
                currencyRate(transaction.currency)
            )
        } else {
            0.0
        }
        val updatedAsset = when (transaction.action) {
            "BUY" -> {
                val quantity = asset.quantity + transaction.quantity
                val invested = asset.investedAmount + totalInAssetCurrency
                asset.copy(
                    quantity = quantity,
                    investedAmount = invested,
                    averagePrice = if (quantity > 0.0) invested / quantity else 0.0,
                    updatedAt = System.currentTimeMillis()
                )
            }
            "SELL" -> {
                val averagePrice = currentAveragePrice(asset)
                val quantity = asset.quantity - transaction.quantity
                val invested = (asset.investedAmount - averagePrice * transaction.quantity)
                    .coerceAtLeast(0.0)
                asset.copy(
                    quantity = quantity.coerceAtLeast(0.0),
                    investedAmount = invested,
                    averagePrice = if (quantity > 0.0) invested / quantity else 0.0,
                    updatedAt = System.currentTimeMillis()
                )
            }
            else -> error("Invalid transaction action")
        }
        val cashBalance = if (transaction.action == "BUY") {
            cash.balance - transaction.total
        } else {
            cash.balance + transaction.total
        }
        if (cashBalance < 0.0) error("Cash balance cannot be negative")
        cashDao.updateBalance(cash.id, cashBalance, System.currentTimeMillis())
        assetDao.update(updatedAsset)
        return transaction.copy(costBasis = costBasis)
    }

    private suspend fun reverseCashAndAssetTransaction(
        asset: AssetEntity,
        transaction: TransactionEntity
    ) {
        val cash = cashDao.getCashAccountByCurrency(transaction.currency)
            ?: error("Cash account ${transaction.currency} not found")
        val totalInAssetCurrency = convertAmount(
            transaction.total,
            currencyRate(transaction.currency),
            currencyRate(asset.currency)
        )
        val averagePrice = currentAveragePrice(asset)
        val costBasisInAssetCurrency = if (transaction.action == "SELL" && transaction.costBasis > 0.0) {
            convertAmount(
                transaction.costBasis,
                currencyRate(transaction.currency),
                currencyRate(asset.currency)
            )
        } else {
            averagePrice * transaction.quantity
        }
        val updatedAsset = when (transaction.action) {
            "BUY" -> asset.copy(
                quantity = (asset.quantity - transaction.quantity).coerceAtLeast(0.0),
                investedAmount = (asset.investedAmount - totalInAssetCurrency).coerceAtLeast(0.0),
                updatedAt = System.currentTimeMillis()
            )
            "SELL" -> asset.copy(
                quantity = asset.quantity + transaction.quantity,
                investedAmount = asset.investedAmount + costBasisInAssetCurrency,
                updatedAt = System.currentTimeMillis()
            )
            else -> error("Invalid transaction action")
        }
        val cashBalance = if (transaction.action == "BUY") {
            cash.balance + transaction.total
        } else {
            cash.balance - transaction.total
        }
        if (cashBalance < 0.0) error("Cash balance cannot be reversed")
        val updatedAverage = if (updatedAsset.quantity > 0.0) {
            updatedAsset.investedAmount / updatedAsset.quantity
        } else {
            0.0
        }
        cashDao.updateBalance(cash.id, cashBalance, System.currentTimeMillis())
        assetDao.update(updatedAsset.copy(averagePrice = updatedAverage))
    }

    private suspend fun validateTransaction(transaction: TransactionEntity, asset: AssetEntity) {
        if (transaction.action != "BUY" && transaction.action != "SELL") {
            error("Invalid transaction action")
        }
        if (transaction.quantity <= 0.0) error("Quantity must be greater than zero")
        if (transaction.price <= 0.0) error("Price must be greater than zero")
        if (transaction.fee < 0.0) error("Fee cannot be negative")
        if (transaction.total < 0.0) error("Transaction total cannot be negative")
        if (transaction.action == "SELL" && transaction.quantity > asset.quantity) {
            error("Insufficient asset quantity")
        }
        currencyRate(transaction.currency)
    }

    private suspend fun currencyRate(code: String): Double =
        currencyDao.findByCode(code)?.exchangeRate?.takeIf { it > 0.0 }
            ?: error("Currency $code not found")

    private fun currentAveragePrice(asset: AssetEntity): Double =
        if (asset.averagePrice > 0.0) asset.averagePrice
        else if (asset.quantity > 0.0) asset.investedAmount / asset.quantity
        else 0.0

    private fun convertAmount(amount: Double, fromIdrRate: Double, toIdrRate: Double): Double =
        amount * fromIdrRate / toIdrRate
}
