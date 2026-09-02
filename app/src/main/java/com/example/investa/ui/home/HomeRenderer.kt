package com.example.investa.ui.home

import android.graphics.Color
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.example.investa.R
import com.example.investa.navigation.ScreenHost
import com.example.investa.ui.common.addAssetRow
import com.example.investa.ui.common.bindLegendRows
import com.example.investa.utils.assetCategories
import com.example.investa.utils.formatAmount
import com.example.investa.utils.formatSignedAmount
import com.example.investa.utils.parseMoneyInput
import com.example.investa.utils.toIdrDisplay
import com.example.investa.utils.toUiAsset
import com.example.investa.utils.withCalculatedCurrentValue
import com.github.mikephil.charting.charts.PieChart
import com.github.mikephil.charting.data.PieData
import com.github.mikephil.charting.data.PieDataSet
import com.github.mikephil.charting.data.PieEntry
import java.util.Locale
import kotlin.math.roundToInt

internal class HomeRenderer(private val host: ScreenHost) {
    fun render() {
        host.contentContainer.removeAllViews()
        val root = host.inflate(R.layout.screen_home)
        host.attach(root)
        val usdExchangeRate = host.exchangeRateFor("USD")
        val displayAssets = host.databaseAssets.map { asset ->
            asset.toUiAsset().toIdrDisplay(usdExchangeRate)
        }
        val investmentValue = displayAssets.sumOf { parseMoneyInput(it.value) ?: 0.0 }
        val cashValue = host.databaseCashAccounts.sumOf { account ->
            account.balance * host.exchangeRateFor(account.currencyCode)
        }
        val totalValue = investmentValue + cashValue
        val totalInvested = displayAssets.sumOf { parseMoneyInput(it.invested) ?: 0.0 }
        val totalProfit = investmentValue - totalInvested
        val totalProfitPercentage = if (totalInvested == 0.0) 0.0 else totalProfit * 100.0 / totalInvested
        val displayCurrency = "IDR"

        root.findViewById<TextView>(R.id.portfolio_value).text = formatAmount(totalValue, displayCurrency, 0)
        root.findViewById<TextView>(R.id.portfolio_profit).apply {
            text = formatSignedAmount(totalProfit, displayCurrency, 0)
            setTextColor(ContextCompat.getColor(
                host.activity,
                if (totalProfit >= 0) R.color.investa_mint else R.color.investa_loss
            ))
        }
        root.findViewById<TextView>(R.id.portfolio_profit_percent).apply {
            text = String.format(Locale.US, "%+.2f%%", totalProfitPercentage)
            setTextColor(ContextCompat.getColor(
                host.activity,
                if (totalProfit >= 0) R.color.investa_mint else R.color.investa_loss
            ))
        }

        val invested = root.findViewById<View>(R.id.summary_invested)
        invested.findViewById<TextView>(R.id.summary_title).text = "Total Invested"
        invested.findViewById<TextView>(R.id.summary_value).text = formatAmount(totalInvested, displayCurrency, 0)
        invested.findViewById<TextView>(R.id.summary_percent).apply {
            text = "— last month"
            visibility = View.VISIBLE
        }
        val profit = root.findViewById<View>(R.id.summary_profit)
        profit.findViewById<TextView>(R.id.summary_title).text = "Total Profit"
        profit.findViewById<TextView>(R.id.summary_value).apply {
            text = formatSignedAmount(totalProfit, displayCurrency, 0)
            setTextColor(ContextCompat.getColor(
                host.activity,
                if (totalProfit >= 0) R.color.investa_mint else R.color.investa_loss
            ))
        }
        profit.findViewById<TextView>(R.id.summary_percent).apply {
            text = String.format(Locale.US, "%+.2f%%", totalProfitPercentage)
            setTextColor(ContextCompat.getColor(
                host.activity,
                if (totalProfit >= 0) R.color.investa_mint else R.color.investa_loss
            ))
        }

        val categoryValues = assetCategories.map { category ->
            displayAssets.filter { it.category == category }.sumOf { parseMoneyInput(it.value) ?: 0.0 }
        }
        val categoryPercentages = categoryValues.map { value ->
            if (investmentValue == 0.0) 0f else (value * 100.0 / investmentValue).toFloat()
        }
        bindLegendRows(
            root.findViewById(R.id.legend_container),
            assetCategories.zip(categoryPercentages.map { it.roundToInt() })
        )
        setupAllocationChart(
            root.findViewById(R.id.portfolio_allocation_chart),
            assetCategories.zip(categoryPercentages)
        )
        root.findViewById<TextView>(R.id.see_all_assets)
            .setOnClickListener { host.showScreen(com.example.investa.navigation.AppScreen.ASSETS) }

        val topAssets = root.findViewById<LinearLayout>(R.id.top_assets_container)
        displayAssets
            .map { it.withCalculatedCurrentValue() }
            .sortedByDescending { parseMoneyInput(it.value) ?: 0.0 }
            .take(3)
            .forEach { topAsset ->
                addAssetRow(
                    host.activity,
                    topAssets,
                    topAsset.copy(
                        value = parseMoneyInput(topAsset.value)?.let {
                            formatAmount(it, topAsset.currency, 0)
                        } ?: topAsset.value
                    ),
                    true,
                    topAsset.profitPercent
                )
            }
    }

    private fun setupAllocationChart(
        chart: PieChart,
        allocation: List<Pair<String, Float>>
    ) {
        val entries = allocation
            .filter { it.second > 0f }
            .map { (category, percentage) -> PieEntry(percentage, category) }

        chart.apply {
            clear()
            if (entries.isEmpty()) return@apply

            val dataSet = PieDataSet(entries, "").apply {
                colors = listOf(
                    Color.rgb(255, 159, 67),  // Orange
                    Color.rgb(255, 107, 107), // Soft red
                    Color.rgb(255, 209, 102), // Yellow
                    Color.rgb(77, 150, 255),  // Blue
                    Color.rgb(166, 108, 255), // Purple
                    Color.rgb(0, 194, 168)     // Teal
                )
                setDrawValues(false)
                setDrawIcons(false)
                sliceSpace = 2f
                selectionShift = 0f
            }

            data = PieData(dataSet)
            description.isEnabled = false
            legend.isEnabled = false
            setDrawEntryLabels(false)
            setUsePercentValues(false)
            setRotationEnabled(false)
            isHighlightPerTapEnabled = false
            setTouchEnabled(false)
            setHoleRadius(58f)
            setTransparentCircleRadius(58f)
            setTransparentCircleAlpha(0)
            setHoleColor(Color.TRANSPARENT)
            setExtraOffsets(0f, 0f, 0f, 0f)
            invalidate()
        }
    }
}
