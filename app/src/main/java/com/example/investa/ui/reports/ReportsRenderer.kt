package com.example.investa.ui.reports

import android.view.Gravity
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.example.investa.data.entity.AssetEntity
import com.example.investa.data.entity.TransactionEntity
import com.example.investa.R
import com.example.investa.ui.home.PerformanceChartView
import com.example.investa.navigation.AppScreen
import com.example.investa.navigation.ScreenHost
import com.example.investa.ui.common.addReportSummaryRow
import com.example.investa.ui.common.setReportToggle
import com.example.investa.utils.assetCategories
import com.example.investa.utils.formatAmount
import com.example.investa.utils.formatSignedAmount
import com.example.investa.utils.parseMoneyInput
import com.example.investa.utils.toIdrDisplay
import com.example.investa.utils.toUiAsset
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import kotlin.math.roundToInt
import kotlin.math.roundToLong

internal class ReportsRenderer(private val host: ScreenHost) {
    fun render() {
        host.contentContainer.removeAllViews()
        val root = host.inflate(R.layout.screen_reports)
        host.attach(root)
        root.findViewById<android.widget.ImageView>(R.id.reports_back)
            .setOnClickListener { host.showScreen(AppScreen.HOME) }
        val usdExchangeRate = host.exchangeRateFor("USD")
        val displayAssets = host.databaseAssets.map { asset ->
            asset.toUiAsset().toIdrDisplay(usdExchangeRate)
        }
        val totalValue = displayAssets.sumOf { parseMoneyInput(it.value) ?: 0.0 }
        val totalInvested = displayAssets.sumOf { parseMoneyInput(it.invested) ?: 0.0 }
        val totalProfit = totalValue - totalInvested
        val totalProfitPercentage = if (totalInvested == 0.0) 0.0 else totalProfit * 100.0 / totalInvested
        val displayCurrency = "IDR"
        val realizedPL = host.databaseTransactions
            .asSequence()
            .filter { it.action.trim().equals("SELL", ignoreCase = true) }
            .sumOf { transaction ->
                (transaction.total - transaction.costBasis) * host.exchangeRateFor(transaction.currency)
            }
        root.findViewById<TextView>(R.id.reports_realized_pl).apply {
            text = formatSignedAmount(realizedPL, displayCurrency, 0)
            setTextColor(
                ContextCompat.getColor(
                    host.activity,
                    if (realizedPL >= 0.0) R.color.investa_mint else R.color.investa_loss
                )
            )
        }
        root.findViewById<TextView>(R.id.reports_invested_value).text = formatAmount(totalInvested, displayCurrency, 0)
        root.findViewById<TextView>(R.id.reports_current_value).text = formatAmount(totalValue, displayCurrency, 0)
        root.findViewById<TextView>(R.id.reports_profit_value).apply {
            text = formatSignedAmount(totalProfit, displayCurrency, 0)
            setTextColor(ContextCompat.getColor(host.activity, if (totalProfit >= 0) R.color.investa_mint else R.color.investa_loss))
        }
        root.findViewById<TextView>(R.id.reports_profit_percent).apply {
            text = String.format(Locale.US, "%+.2f%%", totalProfitPercentage)
            setTextColor(ContextCompat.getColor(host.activity, if (totalProfit >= 0) R.color.investa_mint else R.color.investa_loss))
        }
        val performance = performanceSnapshots(host.databaseAssets, host.databaseTransactions, usdExchangeRate)
        root.findViewById<PerformanceChartView>(R.id.performance_chart)
            .setPerformanceData(performance.first, performance.second)
        val monthLabels = root.findViewById<LinearLayout>(R.id.performance_month_labels)
        val monthFormat = SimpleDateFormat("MMM", Locale.ENGLISH)
        val currentMonth = Calendar.getInstance().apply { set(Calendar.DAY_OF_MONTH, 1) }
        repeat(6) { index ->
            val month = currentMonth.clone() as Calendar
            month.add(Calendar.MONTH, index - 5)
            monthLabels.addView(TextView(host.activity).apply {
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                gravity = Gravity.CENTER
                text = monthFormat.format(month.time)
                setTextColor(ContextCompat.getColor(host.activity, R.color.investa_icon_inactive))
                textSize = 11f
            })
        }
        val summary = root.findViewById<LinearLayout>(R.id.category_summary)
        fun renderSummary(byAsset: Boolean) {
            summary.removeAllViews()
            if (byAsset) {
                displayAssets.forEach { asset ->
                    val value = parseMoneyInput(asset.value) ?: 0.0
                    val percentage = if (totalValue == 0.0) 0 else ((value * 100.0) / totalValue).roundToInt()
                    addReportSummaryRow(host.activity, summary, asset.symbol, formatAmount(value, displayCurrency, 0), "$percentage%")
                }
            } else {
                assetCategories.forEach { category ->
                    val value = displayAssets.filter { it.category == category }.sumOf { parseMoneyInput(it.value) ?: 0.0 }
                    val percentage = if (totalValue == 0.0) 0 else ((value * 100.0) / totalValue).roundToInt()
                    addReportSummaryRow(host.activity, summary, category, formatAmount(value, displayCurrency, 0), "$percentage%")
                }
            }
        }
        renderSummary(false)
        val categoryToggle = root.findViewById<TextView>(R.id.toggle_category)
        val assetToggle = root.findViewById<TextView>(R.id.toggle_asset)
        categoryToggle.setOnClickListener {
            setReportToggle(host.activity, categoryToggle, assetToggle, true)
            renderSummary(false)
        }
        assetToggle.setOnClickListener {
            setReportToggle(host.activity, categoryToggle, assetToggle, false)
            renderSummary(true)
        }
    }

    private fun performanceSnapshots(
        assets: List<AssetEntity>,
        transactions: List<TransactionEntity>,
        usdExchangeRate: Double
    ): Pair<List<Long>, List<Long>> {
        val month = Calendar.getInstance().apply {
            set(Calendar.DAY_OF_MONTH, 1)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            add(Calendar.MONTH, -5)
        }
        val investedValues = mutableListOf<Long>()
        val currentValues = mutableListOf<Long>()
        repeat(6) {
            val nextMonth = month.clone() as Calendar
            nextMonth.add(Calendar.MONTH, 1)
            val monthEnd = nextMonth.timeInMillis - 1L
            var invested = 0.0
            var current = 0.0
            assets.forEach { asset ->
                if (asset.createdAt > monthEnd) return@forEach
                var quantity = asset.quantity
                var costBasis = asset.investedAmount
                transactions.asSequence()
                    .filter { it.assetId == asset.id && it.date > monthEnd }
                    .sortedWith(compareByDescending<TransactionEntity> { it.date }.thenByDescending { it.id })
                    .forEach { transaction ->
                        val transactionRate = host.exchangeRateFor(transaction.currency)
                        val assetRate = host.exchangeRateFor(asset.currency)
                        val transactionCost = transaction.total * transactionRate / assetRate
                        if (transaction.action == "BUY") {
                            quantity = (quantity - transaction.quantity).coerceAtLeast(0.0)
                            costBasis = (costBasis - transactionCost).coerceAtLeast(0.0)
                        } else if (transaction.action == "SELL") {
                            val quantityAfterSell = quantity
                            val quantityBeforeSell = quantityAfterSell + transaction.quantity
                            costBasis = if (quantityAfterSell > 0.0) {
                                costBasis * quantityBeforeSell / quantityAfterSell
                            } else {
                                costBasis + transactionCost
                            }
                            quantity = quantityBeforeSell
                        }
                    }
                val assetExchangeRate = if (asset.currency == "USD") usdExchangeRate else 1.0
                invested += costBasis * assetExchangeRate
                current += quantity * asset.currentPrice * assetExchangeRate
            }
            investedValues += invested.roundToLong()
            currentValues += current.roundToLong()
            month.add(Calendar.MONTH, 1)
        }
        return investedValues to currentValues
    }
}
