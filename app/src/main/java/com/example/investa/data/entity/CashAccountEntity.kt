package com.example.investa.data.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "cash_accounts",
    indices = [Index(value = ["currencyCode"], unique = true)]
)
data class CashAccountEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val currencyCode: String,
    val balance: Double,
    val createdAt: Long,
    val updatedAt: Long
)
