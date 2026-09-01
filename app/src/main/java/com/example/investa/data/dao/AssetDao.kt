package com.example.investa.data.dao

import com.example.investa.data.entity.AssetEntity

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface AssetDao {
    @Query("SELECT * FROM assets ORDER BY createdAt DESC")
    fun observeAssets(): Flow<List<AssetEntity>>

    @Query("SELECT * FROM assets WHERE id = :id LIMIT 1")
    suspend fun findById(id: Long): AssetEntity?

    @Insert
    suspend fun insert(asset: AssetEntity): Long

    @Update
    suspend fun update(asset: AssetEntity)

    @Delete
    suspend fun delete(asset: AssetEntity)
}
