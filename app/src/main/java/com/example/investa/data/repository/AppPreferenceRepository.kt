package com.example.investa.data.repository

import com.example.investa.data.dao.AppPreferenceDao
import com.example.investa.data.entity.AppPreferenceEntity
import kotlinx.coroutines.flow.Flow

class AppPreferenceRepository(private val dao: AppPreferenceDao) {
    fun observePreferences(): Flow<AppPreferenceEntity?> = dao.observePreferences()

    suspend fun ensureDefaults() {
        dao.insertDefaults(AppPreferenceEntity())
    }

    suspend fun savePrimaryCurrency(currencyCode: String) {
        val current = dao.getPreferences() ?: AppPreferenceEntity()
        dao.upsert(current.copy(primaryCurrencyCode = currencyCode))
    }

    suspend fun saveNumberFormatStyle(style: String) {
        val current = dao.getPreferences() ?: AppPreferenceEntity()
        dao.upsert(current.copy(numberFormatStyle = style))
    }
}
