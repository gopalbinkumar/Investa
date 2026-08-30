package com.example.investa

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "currencies",
    indices = [Index(value = ["code"], unique = true)]
)
data class CurrencyEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val code: String,
    val name: String,
    val symbol: String,
    val exchangeRate: Double,
    val updatedAt: Long,
    val isActive: Boolean
)
