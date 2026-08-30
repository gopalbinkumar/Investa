package com.example.investa

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class CurrencyViewModel(private val repository: CurrencyRepository) : ViewModel() {
    val currencies: StateFlow<List<CurrencyEntity>> = repository.observeActiveCurrencies()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun ensureDefaults() {
        viewModelScope.launch { repository.ensureDefaults() }
    }

    fun update(currency: CurrencyEntity, onSaved: () -> Unit) {
        viewModelScope.launch {
            repository.update(currency)
            onSaved()
        }
    }
}

class CurrencyViewModelFactory(
    private val repository: CurrencyRepository
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(CurrencyViewModel::class.java)) {
            return CurrencyViewModel(repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}
