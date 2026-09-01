package com.example.investa.utils

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

internal object YahooFinanceApi {
    private const val BASE_URL = "https://query1.finance.yahoo.com/v8/finance/chart/"

    suspend fun fetchUsdIdrRate(): Double = fetchQuote("IDR=X")

    suspend fun fetchQuote(apiSymbol: String): Double = withContext(Dispatchers.IO) {
        val connection = (URL("$BASE_URL$apiSymbol?range=1d&interval=1d").openConnection() as HttpURLConnection)
            .apply {
                requestMethod = "GET"
                connectTimeout = 10_000
                readTimeout = 10_000
                setRequestProperty("Accept", "application/json")
                setRequestProperty("User-Agent", "Mozilla/5.0")
            }
        try {
            if (connection.responseCode !in 200..299) {
                error("HTTP ${connection.responseCode}")
            }
            val response = connection.inputStream.bufferedReader().use { it.readText() }
            parseQuote(response)
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
        val metaPrice = result.optJSONObject("meta")
            ?.optDouble("regularMarketPrice", Double.NaN)
            ?: Double.NaN
        if (metaPrice.isValidQuote()) return metaPrice

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
