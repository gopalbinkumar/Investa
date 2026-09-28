package com.example.investa.data.entity

import androidx.room.Entity
import androidx.room.Index

@Entity(
    tableName = "cash_balance_snapshots",
    primaryKeys = ["currencyCode", "day"],
    indices = [Index(value = ["currencyCode", "day"])]
)
data class CashBalanceSnapshotEntity(
    val currencyCode: String,
    val day: Long,
    val balance: Double,
    val updatedAt: Long
)
