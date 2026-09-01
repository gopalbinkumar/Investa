package com.example.investa.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.investa.data.entity.CashAccountEntity
import com.example.investa.data.repository.CashRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class CashViewModel(private val repository: CashRepository) : ViewModel() {
    val cashAccounts: StateFlow<List<CashAccountEntity>> = repository.observeAllCashAccounts()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun ensureDefaultAccounts() {
        viewModelScope.launch { repository.ensureDefaultAccounts() }
    }

    fun addCash(
        currencyCode: String,
        amount: Double,
        onSaved: () -> Unit,
        onError: (String) -> Unit = {}
    ) {
        viewModelScope.launch {
            runCatching { repository.addCash(currencyCode, amount) }
                .onSuccess { onSaved() }
                .onFailure { onError(it.message ?: "Cash update failed") }
        }
    }

    fun updateCash(
        account: CashAccountEntity,
        newBalance: Double,
        onSaved: () -> Unit,
        onError: (String) -> Unit = {}
    ) {
        viewModelScope.launch {
            runCatching { repository.updateCashBalance(account, newBalance) }
                .onSuccess { onSaved() }
                .onFailure { onError(it.message ?: "Cash update failed") }
        }
    }

    fun deleteCash(
        account: CashAccountEntity,
        onDeleted: () -> Unit,
        onError: (String) -> Unit = {}
    ) {
        viewModelScope.launch {
            runCatching { repository.deleteCashAccount(account) }
                .onSuccess { onDeleted() }
                .onFailure { onError(it.message ?: "Cash delete failed") }
        }
    }
}

class CashViewModelFactory(
    private val repository: CashRepository
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(CashViewModel::class.java)) {
            return CashViewModel(repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}
