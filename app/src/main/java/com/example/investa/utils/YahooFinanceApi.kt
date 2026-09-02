package com.example.investa.utils

import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

internal object YahooFinanceApi {
    private val BASE_URLS = listOf(
        "https://query1.finance.yahoo.com/v8/finance/chart/",
        "https://query2.finance.yahoo.com/v8/finance/chart/"
    )

    suspend fun fetchUsdIdrRate(): Double = fetchQuote("IDR=X")

    suspend fun fetchQuote(apiSymbol: String): Double = withContext(Dispatchers.IO) {
        val normalizedSymbol = apiSymbol.trim().uppercase()
        require(normalizedSymbol.isNotBlank()) { "Yahoo Finance symbol is empty" }
        val encodedSymbol = Uri.encode(normalizedSymbol)
        var lastError: Throwable? = null

        for (baseUrl in BASE_URLS) {
            try {
                return@withContext fetchFromEndpoint("$baseUrl$encodedSymbol?range=1d&interval=5m&includePrePost=true&events=div%2Csplits&lang=en-US&region=US")
            } catch (error: Throwable) {
                lastError = error
            }
        }
        error("Yahoo Finance unavailable: ${lastError?.message ?: "unknown network error"}")
    }

    private fun fetchFromEndpoint(endpoint: String): Double {
        val connection = (URL(endpoint).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 15_000
            readTimeout = 15_000
            useCaches = false
            instanceFollowRedirects = true
            setRequestProperty("Accept", "application/json")
            setRequestProperty("Accept-Language", "en-US,en;q=0.9")
            setRequestProperty("Cache-Control", "no-cache")
            setRequestProperty("User-Agent", "Mozilla/5.0 (Android; Mobile) AppleWebKit/537.36 Chrome/120 Safari/537.36")
        }
        try {
            val responseCode = connection.responseCode
            if (responseCode !in 200..299) {
                error("HTTP $responseCode")
            }
            val response = connection.inputStream.bufferedReader().use { it.readText() }
            return parseQuote(response)
        } finally {
            connection.disconnect()
        }
    }

    private fun parseQuote(response: String): Double {
        val chart = JSONObject(response).optJSONObject("chart")
            ?: error("Invalid Yahoo Finance response")
        chart.optJSONObject("error")?.let { errorObject ->
            if (!errorObject.isNull("description")) {
                error(errorObject.optString("description"))
            }
        }
        val result = chart.optJSONArray("result")?.optJSONObject(0)
            ?: error("Yahoo Finance quote unavailable")
        val meta = result.optJSONObject("meta")
        listOf(
            "regularMarketPrice",
            "postMarketPrice",
            "preMarketPrice",
            "previousClose",
            "chartPreviousClose"
        ).forEach { key ->
            val metaPrice = meta?.optDouble(key, Double.NaN) ?: Double.NaN
            if (metaPrice.isValidQuote()) return metaPrice
        }

        val closes = result.optJSONObject("indicators")
            ?.optJSONArray("quote")
            ?.optJSONObject(0)
            ?.optJSONArray("close")
            ?: error("Yahoo Finance price unavailable")
        for (index in closes.length() - 1 downTo 0) {
            val close = closes.optDouble(index, Double.NaN)
            if (close.isValidQuote()) return close
        }
        error("Yahoo Finance price unavailable")
    }

    private fun Double.isValidQuote(): Boolean = isFinite() && this > 0.0
}
