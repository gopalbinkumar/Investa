package com.example.investa.viewmodel

import com.example.investa.data.entity.TransactionEntity
import com.example.investa.data.repository.TransactionRepository

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

class TransactionViewModel(private val repository: TransactionRepository) : ViewModel() {
    fun observeTransactions(assetId: Long): Flow<List<TransactionEntity>> =
        repository.observeTransactions(assetId)

    fun observeAllTransactions(): Flow<List<TransactionEntity>> =
        repository.observeAllTransactions()

    suspend fun getAllTransactions(): List<TransactionEntity> =
        repository.getAllTransactions()

    suspend fun getTransactionHistoryPage(limit: Int, offset: Int): List<TransactionEntity> =
        repository.getTransactionHistoryPage(limit, offset)

    suspend fun getTransactionHistoryPageForAsset(
        assetId: Long,
        limit: Int,
        offset: Int
    ): List<TransactionEntity> =
        repository.getTransactionHistoryPageForAsset(assetId, limit, offset)

    fun addTransaction(
        transaction: TransactionEntity,
        onSaved: () -> Unit,
        onError: (String) -> Unit = {}
    ) {
        viewModelScope.launch {
            runCatching { repository.addTransaction(transaction) }
                .onSuccess { onSaved() }
                .onFailure { onError(it.message ?: "Transaction failed") }
        }
    }

    fun updateTransaction(
        transaction: TransactionEntity,
        onSaved: () -> Unit,
        onError: (String) -> Unit = {}
    ) {
        viewModelScope.launch {
            runCatching { repository.updateTransaction(transaction) }
                .onSuccess { onSaved() }
                .onFailure { onError(it.message ?: "Transaction update failed") }
        }
    }

    fun deleteTransaction(
        transaction: TransactionEntity,
        onDeleted: () -> Unit,
        onError: (String) -> Unit = {}
    ) {
        viewModelScope.launch {
            runCatching { repository.deleteTransaction(transaction) }
                .onSuccess { onDeleted() }
                .onFailure { onError(it.message ?: "Transaction delete failed") }
        }
    }
}

class TransactionViewModelFactory(
    private val repository: TransactionRepository
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(TransactionViewModel::class.java)) {
            return TransactionViewModel(repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}
