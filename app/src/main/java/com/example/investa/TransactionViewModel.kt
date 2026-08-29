package com.example.investa

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

    fun addTransaction(transaction: TransactionEntity, onSaved: () -> Unit) {
        viewModelScope.launch {
            repository.addTransaction(transaction)
            onSaved()
        }
    }

    fun updateTransaction(transaction: TransactionEntity, onSaved: () -> Unit) {
        viewModelScope.launch {
            repository.updateTransaction(transaction)
            onSaved()
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
