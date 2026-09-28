package com.example.investa.utils

import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.Calendar

internal object YahooFinanceApi {
    data class QuoteDetails(
        val price: Double,
        val name: String?,
        val shortName: String? = null,
        val longName: String? = null
    )

    data class HistoricalQuote(val day: Long, val close: Double)

    private val BASE_URLS = listOf(
        "https://query1.finance.yahoo.com/v8/finance/chart/",
        "https://query2.finance.yahoo.com/v8/finance/chart/"
    )

    suspend fun fetchUsdIdrRate(): Double = fetchQuote("IDR=X")

    suspend fun fetchQuote(apiSymbol: String): Double = fetchQuoteDetails(apiSymbol).price

    suspend fun fetchQuoteDetails(apiSymbol: String): QuoteDetails = withContext(Dispatchers.IO) {
        val normalizedSymbol = apiSymbol.trim().uppercase()
        require(normalizedSymbol.isNotBlank()) { "Yahoo Finance symbol is empty" }
        val encodedSymbol = Uri.encode(normalizedSymbol)
        var lastError: Throwable? = null

        for (baseUrl in BASE_URLS) {
            try {
                return@withContext fetchFromEndpoint("$baseUrl$encodedSymbol?range=1d&interval=5m&includePrePost=true&events=div%2Csplits&lang=en-US&region=US")
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                lastError = error
            }
        }
        error("Yahoo Finance unavailable: ${lastError?.message ?: "unknown network error"}")
    }

    suspend fun fetchDailyHistory(apiSymbol: String, fromDay: Long): List<HistoricalQuote> =
        withContext(Dispatchers.IO) {
            val normalizedSymbol = apiSymbol.trim().uppercase()
            require(normalizedSymbol.isNotBlank()) { "Yahoo Finance symbol is empty" }
            val encodedSymbol = Uri.encode(normalizedSymbol)
            val nowSeconds = System.currentTimeMillis() / 1_000L
            val fromSeconds = fromDay / 1_000L
            var lastError: Throwable? = null
            for (baseUrl in BASE_URLS) {
                try {
                    val response = fetchRaw(
                        "$baseUrl$encodedSymbol?period1=$fromSeconds&period2=$nowSeconds&interval=1d&events=history"
                    )
                    return@withContext parseDailyHistory(response)
                } catch (error: CancellationException) {
                    throw error
                } catch (error: Throwable) {
                    lastError = error
                }
            }
            error("Yahoo Finance unavailable: ${lastError?.message ?: "unknown network error"}")
        }

    private suspend fun fetchFromEndpoint(endpoint: String): QuoteDetails = withContext(Dispatchers.IO) {
        return@withContext parseQuote(fetchRaw(endpoint))
    }

    private suspend fun fetchRaw(endpoint: String): String = withContext(Dispatchers.IO) {
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
        val cancellationHandle = coroutineContext[Job]?.invokeOnCompletion {
            connection.disconnect()
        }
        try {
            val responseCode = connection.responseCode
            if (responseCode !in 200..299) {
                error("HTTP $responseCode")
            }
            return@withContext connection.inputStream.bufferedReader().use { it.readText() }
        } finally {
            cancellationHandle?.dispose()
            connection.disconnect()
        }
    }

    private fun parseQuote(response: String): QuoteDetails {
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
        val shortName = meta?.optString("shortName")?.takeIf { it.isNotBlank() }
        val longName = meta?.optString("longName")?.takeIf { it.isNotBlank() }
        val displayName = meta?.optString("displayName")?.takeIf { it.isNotBlank() }
        val name = longName ?: shortName ?: displayName
        listOf(
            "regularMarketPrice",
            "postMarketPrice",
            "preMarketPrice",
            "previousClose",
            "chartPreviousClose"
        ).forEach { key ->
            val metaPrice = meta?.optDouble(key, Double.NaN) ?: Double.NaN
            if (metaPrice.isValidQuote()) return QuoteDetails(metaPrice, name, shortName, longName)
        }

        val closes = result.optJSONObject("indicators")
            ?.optJSONArray("quote")
            ?.optJSONObject(0)
            ?.optJSONArray("close")
            ?: error("Yahoo Finance price unavailable")
        for (index in closes.length() - 1 downTo 0) {
            val close = closes.optDouble(index, Double.NaN)
            if (close.isValidQuote()) return QuoteDetails(close, name, shortName, longName)
        }
        error("Yahoo Finance price unavailable")
    }

    private fun parseDailyHistory(response: String): List<HistoricalQuote> {
        val result = JSONObject(response).optJSONObject("chart")
            ?.optJSONArray("result")?.optJSONObject(0)
            ?: error("Yahoo Finance history unavailable")
        val timestamps = result.optJSONArray("timestamp") ?: return emptyList()
        val closes = result.optJSONObject("indicators")
            ?.optJSONArray("quote")?.optJSONObject(0)
            ?.optJSONArray("close") ?: return emptyList()
        val calendar = Calendar.getInstance()
        return buildList {
            for (index in 0 until minOf(timestamps.length(), closes.length())) {
                val close = closes.optDouble(index, Double.NaN)
                if (!close.isValidQuote()) continue
                calendar.timeInMillis = timestamps.optLong(index) * 1_000L
                calendar.set(Calendar.HOUR_OF_DAY, 0)
                calendar.set(Calendar.MINUTE, 0)
                calendar.set(Calendar.SECOND, 0)
                calendar.set(Calendar.MILLISECOND, 0)
                add(HistoricalQuote(calendar.timeInMillis, close))
            }
        }
    }

    private fun Double.isValidQuote(): Boolean = isFinite() && this > 0.0
}
