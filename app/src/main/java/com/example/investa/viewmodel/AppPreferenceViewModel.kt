package com.example.investa.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.investa.data.repository.AppPreferenceRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class AppPreferenceViewModel(private val repository: AppPreferenceRepository) : ViewModel() {
    val primaryCurrency: StateFlow<String> = repository.observePreferences()
        .map { it?.primaryCurrencyCode?.takeIf { code -> code == "IDR" || code == "USD" } ?: "IDR" }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), "IDR")

    val numberFormatStyle: StateFlow<String> = repository.observePreferences()
        .map { it?.numberFormatStyle ?: "ID" }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), "ID")

    fun ensureDefaults() {
        viewModelScope.launch { repository.ensureDefaults() }
    }

    fun savePrimaryCurrency(currencyCode: String) {
        viewModelScope.launch { repository.savePrimaryCurrency(currencyCode) }
    }

    fun saveNumberFormatStyle(style: String) {
        viewModelScope.launch { repository.saveNumberFormatStyle(style) }
    }
}

class AppPreferenceViewModelFactory(
    private val repository: AppPreferenceRepository
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(AppPreferenceViewModel::class.java)) {
            return AppPreferenceViewModel(repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}
