package com.example.investa.model

data class Asset(
    val name: String,
    val symbol: String,
    val category: String,
    val quantity: String,
    val value: String,
    val invested: String,
    val profit: String,
    val profitPercent: String,
    val averagePrice: String,
    val currentPrice: String,
    val notes: String,
    val addedOn: String,
    val id: Long = 0,
    val currency: String = "IDR"
)
