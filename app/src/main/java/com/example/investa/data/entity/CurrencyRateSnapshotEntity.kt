package com.example.investa.data.entity

import androidx.room.Entity
import androidx.room.Index

@Entity(
    tableName = "currency_rate_snapshots",
    primaryKeys = ["currencyCode", "day"],
    indices = [Index(value = ["currencyCode", "day"])]
)
data class CurrencyRateSnapshotEntity(
    val currencyCode: String,
    val day: Long,
    val rateToIdr: Double,
    val updatedAt: Long
)
