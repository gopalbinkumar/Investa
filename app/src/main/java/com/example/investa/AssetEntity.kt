package com.example.investa

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "assets")
data class AssetEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val symbol: String,
    val category: String,
    val quantity: Double,
    val investedAmount: Long,
    val averagePrice: Long,
    val currentPrice: Long,
    val currency: String,
    val notes: String,
    val createdAt: Long,
    val updatedAt: Long
)
