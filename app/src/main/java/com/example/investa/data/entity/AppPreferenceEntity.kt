package com.example.investa.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "app_preferences")
data class AppPreferenceEntity(
    @PrimaryKey
    val id: Int = 1,
    val primaryCurrencyCode: String = "IDR",
    val numberFormatStyle: String = "ID"
)
