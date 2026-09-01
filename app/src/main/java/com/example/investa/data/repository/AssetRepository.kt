package com.example.investa.data.repository

import com.example.investa.data.dao.AssetDao
import com.example.investa.data.entity.AssetEntity

import kotlinx.coroutines.flow.Flow

class AssetRepository(private val assetDao: AssetDao) {
    fun observeAssets(): Flow<List<AssetEntity>> = assetDao.observeAssets()

    suspend fun addAsset(asset: AssetEntity): Long = assetDao.insert(asset)

    suspend fun updateAsset(asset: AssetEntity) {
        assetDao.update(asset)
    }

    suspend fun deleteAsset(asset: AssetEntity) {
        assetDao.delete(asset)
    }
}
