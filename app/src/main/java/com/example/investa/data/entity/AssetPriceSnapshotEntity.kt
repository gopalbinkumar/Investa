package com.example.investa.data.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

@Entity(
    tableName = "asset_price_snapshots",
    primaryKeys = ["assetId", "day"],
    foreignKeys = [
        ForeignKey(
            entity = AssetEntity::class,
            parentColumns = ["id"],
            childColumns = ["assetId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["assetId", "day"])]
)
data class AssetPriceSnapshotEntity(
    val assetId: Long,
    val day: Long,
    val price: Double,
    val updatedAt: Long
)
