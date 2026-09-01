package com.example.investa.data.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "transactions",
    foreignKeys = [
        ForeignKey(
            entity = AssetEntity::class,
            parentColumns = ["id"],
            childColumns = ["assetId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["assetId"])]
)
data class TransactionEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val assetId: Long,
    val action: String,
    val date: Long,
    val quantity: Double,
    val price: Double,
    val fee: Double,
    val total: Double,
    val costBasis: Double = 0.0,
    val currency: String,
    val notes: String,
    val createdAt: Long,
    val updatedAt: Long
)
