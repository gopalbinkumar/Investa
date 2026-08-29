package com.example.investa

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class AssetViewModel(private val repository: AssetRepository) : ViewModel() {
    val assets: StateFlow<List<AssetEntity>> = repository.observeAssets()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun addAsset(asset: AssetEntity, onSaved: () -> Unit) {
        viewModelScope.launch {
            repository.addAsset(asset)
            onSaved()
        }
    }

    fun updateAsset(asset: AssetEntity, onSaved: () -> Unit) {
        viewModelScope.launch {
            repository.updateAsset(asset)
            onSaved()
        }
    }

    fun deleteAsset(asset: AssetEntity, onDeleted: () -> Unit) {
        viewModelScope.launch {
            repository.deleteAsset(asset)
            onDeleted()
        }
    }
}

class AssetViewModelFactory(
    private val repository: AssetRepository
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(AssetViewModel::class.java)) {
            return AssetViewModel(repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}
