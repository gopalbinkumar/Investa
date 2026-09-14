package com.example.investa.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.investa.data.entity.AppPreferenceEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface AppPreferenceDao {
    @Query("SELECT * FROM app_preferences WHERE id = 1 LIMIT 1")
    fun observePreferences(): Flow<AppPreferenceEntity?>

    @Query("SELECT * FROM app_preferences WHERE id = 1 LIMIT 1")
    suspend fun getPreferences(): AppPreferenceEntity?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertDefaults(preferences: AppPreferenceEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(preferences: AppPreferenceEntity)
}
