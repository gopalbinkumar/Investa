package com.example.investa.data.repository

import com.example.investa.data.InvestaDatabase
import com.example.investa.data.entity.AssetEntity
import com.example.investa.data.entity.AssetPriceSnapshotEntity

import kotlinx.coroutines.flow.Flow

class AssetRepository(private val database: InvestaDatabase) {
    private val assetDao = database.assetDao()
    fun observeAssets(): Flow<List<AssetEntity>> = assetDao.observeAssets()

    suspend fun addAsset(asset: AssetEntity): Long {
        val id = assetDao.insert(asset)
        savePriceSnapshot(asset.copy(id = id))
        return id
    }

    suspend fun updateAsset(asset: AssetEntity) {
        assetDao.update(asset)
        savePriceSnapshot(asset)
    }

    suspend fun deleteAsset(asset: AssetEntity) {
        assetDao.delete(asset)
    }

    private suspend fun savePriceSnapshot(asset: AssetEntity) {
        val now = System.currentTimeMillis()
        database.performanceSnapshotDao().upsertAssetPrices(
            listOf(AssetPriceSnapshotEntity(asset.id, startOfDay(now), asset.currentPrice, now))
        )
    }

    private fun startOfDay(time: Long): Long = java.util.Calendar.getInstance().apply {
        timeInMillis = time
        set(java.util.Calendar.HOUR_OF_DAY, 0); set(java.util.Calendar.MINUTE, 0)
        set(java.util.Calendar.SECOND, 0); set(java.util.Calendar.MILLISECOND, 0)
    }.timeInMillis
}
