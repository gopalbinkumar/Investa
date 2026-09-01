package com.example.investa.data.entity

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
    val investedAmount: Double,
    val averagePrice: Double,
    val currentPrice: Double,
    val currency: String,
    val notes: String,
    val createdAt: Long,
    val updatedAt: Long
)
