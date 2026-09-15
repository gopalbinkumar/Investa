package com.example.investa.ui.reports

import android.graphics.Color
import android.view.MotionEvent
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat
import com.example.investa.R
import com.example.investa.data.entity.AssetEntity
import com.example.investa.data.entity.TransactionEntity
import com.example.investa.model.Asset
import com.example.investa.navigation.AppScreen
import com.example.investa.navigation.ScreenHost
import com.example.investa.ui.common.addReportSummaryRow
import com.example.investa.ui.common.applyElevatedCard
import com.example.investa.ui.common.setReportToggle
import com.example.investa.utils.assetCategories
import com.example.investa.utils.localizedCategory
import com.example.investa.utils.formatAmount
import com.example.investa.utils.formatSignedAmount
import com.example.investa.utils.parseMoneyInput
import com.example.investa.utils.toDisplayCurrency
import com.example.investa.utils.convertCurrencyAmount
import com.example.investa.utils.toUiAsset
import com.example.investa.utils.LanguageManager
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import com.github.mikephil.charting.formatter.ValueFormatter
import com.github.mikephil.charting.components.YAxis
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.roundToInt
import kotlin.math.roundToLong

internal class ReportsRenderer(private val host: ScreenHost) {
    private var summaryByAsset = false
    private var reportsRoot: android.view.View? = null

    fun render() {
        val root = reportsRoot ?: host.inflate(R.layout.screen_reports).also { reportsRoot = it }
        val categoryToggle = root.findViewById<TextView>(R.id.toggle_category)
        val assetToggle = root.findViewById<TextView>(R.id.toggle_asset)
        setReportToggle(host.activity, categoryToggle, assetToggle, !summaryByAsset)
        if (root.parent == null) host.attach(root)
        root.findViewById<android.widget.ImageView>(R.id.reports_back)
            .setOnClickListener { host.showScreen(AppScreen.HOME) }
        val usdExchangeRate = host.exchangeRateFor("USD")
        val displayCurrency = host.primaryCurrency
        val displayAssets = host.databaseAssets.map { asset ->
            asset.toUiAsset(host.activity).toDisplayCurrency(displayCurrency, usdExchangeRate)
        }
        val nativeAssets = host.databaseAssets.map { asset ->
            asset.toUiAsset(host.activity)
        }
        val totalValue = displayAssets.sumOf { parseMoneyInput(it.value) ?: 0.0 }
        val totalInvested = displayAssets.sumOf { parseMoneyInput(it.invested) ?: 0.0 }
        val totalProfit = totalValue - totalInvested
        val totalProfitPercentage = if (totalInvested == 0.0) 0.0 else totalProfit * 100.0 / totalInvested
        val realizedPL = host.databaseTransactions
            .asSequence()
            .filter { it.action.trim().equals("SELL", ignoreCase = true) }
            .sumOf { transaction ->
                convertCurrencyAmount(
                    transaction.total - transaction.costBasis,
                    transaction.currency,
                    displayCurrency,
                    usdExchangeRate
                )
            }
        root.findViewById<TextView>(R.id.reports_realized_pl).apply {
            text = formatSignedAmount(realizedPL, displayCurrency, 0)
            setTextColor(
                ContextCompat.getColor(
                    host.activity,
                    if (realizedPL >= 0.0) R.color.investa_profit else R.color.investa_loss
                )
            )
        }
        root.findViewById<TextView>(R.id.reports_invested_value).text = formatAmount(totalInvested, displayCurrency, 0)
        root.findViewById<TextView>(R.id.reports_current_value).text = formatAmount(totalValue, displayCurrency, 0)
        root.findViewById<TextView>(R.id.reports_profit_value).apply {
            text = formatSignedAmount(totalProfit, displayCurrency, 0)
            setTextColor(ContextCompat.getColor(host.activity, if (totalProfit >= 0) R.color.investa_profit else R.color.investa_loss))
        }
        root.findViewById<TextView>(R.id.reports_profit_percent).apply {
            text = String.format(Locale.US, "%+.2f%%", totalProfitPercentage)
            setTextColor(ContextCompat.getColor(host.activity, if (totalProfit >= 0) R.color.investa_profit else R.color.investa_loss))
        }
        val performance = dailyPerformanceSnapshots(
            assets = host.databaseAssets,
            transactions = host.databaseTransactions,
            usdExchangeRate = usdExchangeRate,
            displayCurrency = displayCurrency
        )
        setupPerformanceChart(
            root.findViewById(R.id.performance_chart),
            performance,
            root.findViewById(R.id.reports_invested_value),
            root.findViewById(R.id.reports_current_value)
        )
        val summary = root.findViewById<LinearLayout>(R.id.category_summary)
        fun renderSummary(byAsset: Boolean) {
            summary.removeAllViews()
            if (byAsset) {
                summary.background = null
                summary.setPadding(0, 0, 0, 0)
                val groupedAssets = nativeAssets.groupBy { it.category }
                val categories = assetCategories + groupedAssets.keys
                    .filterNot { it in assetCategories }
                    .sortedBy { it.lowercase(Locale.ENGLISH) }
                categories.forEach { category ->
                    val categoryAssets = groupedAssets[category]
                        ?.sortedWith(
                            compareBy<Asset> { it.name.lowercase(Locale.ENGLISH) }
                                .thenBy { it.symbol.lowercase(Locale.ENGLISH) }
                        )
                        .orEmpty()
                    if (categoryAssets.isEmpty()) return@forEach

                    val categoryCard = LinearLayout(host.activity).apply {
                        orientation = LinearLayout.VERTICAL
                        setBackgroundResource(R.drawable.bg_surface)
                        setPadding(0, host.dp(16), 0, host.dp(16))
                        layoutParams = LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.MATCH_PARENT,
                            LinearLayout.LayoutParams.WRAP_CONTENT
                        ).apply {
                            topMargin = if (summary.childCount == 0) 0 else host.dp(10)
                        }
                    }
                    categoryCard.addView(TextView(host.activity).apply {
                        text = localizedCategory(host.activity, category)
                        includeFontPadding = false
                        setTextColor(ContextCompat.getColor(host.activity, R.color.investa_text_secondary))
                        textSize = 12f
                        typeface = ResourcesCompat.getFont(host.activity, R.font.poppins_semibold)
                        setPadding(host.dp(16), 0, host.dp(16), 0)
                        layoutParams = LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.MATCH_PARENT,
                            LinearLayout.LayoutParams.WRAP_CONTENT
                        )
                    })
                    categoryAssets.forEach { asset ->
                        val nativeValue = parseMoneyInput(asset.value) ?: 0.0
                        val displayValue = convertCurrencyAmount(
                            nativeValue,
                            asset.currency,
                            displayCurrency,
                            usdExchangeRate
                        )
                        val percentage = if (totalValue == 0.0) 0 else {
                            ((displayValue * 100.0) / totalValue).roundToInt()
                        }
                        addReportSummaryRow(
                            host.activity,
                            categoryCard,
                            asset.symbol,
                            formatAmount(nativeValue, asset.currency, 0),
                            "$percentage%",
                            onClick = {
                                host.selectedAsset = host.databaseAssets
                                    .firstOrNull { it.id == asset.id }
                                    ?.toUiAsset(host.activity)
                                    ?: asset
                                host.showScreen(AppScreen.DETAIL)
                            }
                        )
                    }
                    summary.addView(categoryCard)
                    applyElevatedCard(categoryCard)
                }
            } else {
                applyElevatedCard(summary)
                summary.setPadding(0, host.dp(16), 0, host.dp(16))
                assetCategories.forEach { category ->
                    val value = displayAssets.filter { it.category == category }.sumOf { parseMoneyInput(it.value) ?: 0.0 }
                    val percentage = if (totalValue == 0.0) 0 else ((value * 100.0) / totalValue).roundToInt()
                    addReportSummaryRow(
                        host.activity,
                        summary,
                        localizedCategory(host.activity, category),
                        formatAmount(value, displayCurrency, 0),
                        "$percentage%",
                        onClick = {
                            host.selectedCategory = category
                            host.showScreen(AppScreen.ASSETS)
                        }
                    )
                }
            }
        }
        renderSummary(summaryByAsset)
        categoryToggle.setOnClickListener {
            summaryByAsset = false
            setReportToggle(host.activity, categoryToggle, assetToggle, true)
            renderSummary(summaryByAsset)
        }
        assetToggle.setOnClickListener {
            summaryByAsset = true
            setReportToggle(host.activity, categoryToggle, assetToggle, false)
            renderSummary(summaryByAsset)
        }
    }

    private fun setupPerformanceChart(
        chart: LineChart,
        performance: List<DailyPerformance>,
        investedSummary: TextView,
        currentSummary: TextView
    ) {
        val investedValues = performance.map { it.invested }
        val currentValues = performance.map { it.current }
        val dateLabels = performance.map { it.dateLabel }

        fun updateSummary(index: Int) {
            val selectedIndex = index.coerceIn(0, (performance.size - 1).coerceAtLeast(0))
            val selected = performance.getOrNull(selectedIndex) ?: return
            investedSummary.text = formatAmount(selected.invested.toDouble(), host.primaryCurrency, 0)
            currentSummary.text = formatAmount(selected.current.toDouble(), host.primaryCurrency, 0)
        }

        updateSummary(performance.lastIndex)

        fun dataSet(values: List<Long>, label: String, color: Int): LineDataSet {
            val entries = values.mapIndexed { index, value ->
                Entry(index.toFloat(), value.toFloat())
            }
            return LineDataSet(entries, label).apply {
                axisDependency = YAxis.AxisDependency.RIGHT
                this.color = color
                lineWidth = 2.5f
                mode = LineDataSet.Mode.CUBIC_BEZIER
                setDrawValues(false)
                setDrawCircles(false)
                setCircleColor(color)
                circleRadius = 3.5f
                circleHoleRadius = 1.5f
                setDrawHighlightIndicators(true)
                setDrawVerticalHighlightIndicator(true)
                setDrawHorizontalHighlightIndicator(false)
                isHighlightEnabled = true
                highLightColor = Color.WHITE
                highlightLineWidth = 1f
                enableDashedHighlightLine(6f, 4f, 0f)
            }
        }

        val allValues = (investedValues + currentValues).map { it.toFloat() }
        val axisRange = performanceAxisRange(allValues)

        chart.apply {
            setBackgroundColor(Color.TRANSPARENT)
            description.isEnabled = false
            legend.isEnabled = false
            axisLeft.isEnabled = false
            axisRight.isEnabled = true
            axisRight.setDrawAxisLine(false)
            axisRight.setDrawGridLines(true)
            axisRight.enableGridDashedLine(8f, 5f, 0f)
            axisRight.gridColor = Color.argb(80, 64, 162, 216)
            axisRight.textColor = ContextCompat.getColor(host.activity, R.color.investa_text_secondary)
            axisRight.textSize = 9f
            axisRight.axisMinimum = axisRange.minimum
            axisRight.axisMaximum = axisRange.maximum
            axisRight.setLabelCount(4, false)
            axisRight.valueFormatter = object : ValueFormatter() {
                override fun getFormattedValue(value: Float): String = formatAxisValue(value)
            }
            xAxis.isEnabled = true
            xAxis.position = com.github.mikephil.charting.components.XAxis.XAxisPosition.BOTTOM
            xAxis.setDrawAxisLine(false)
            xAxis.setDrawGridLines(false)
            xAxis.textColor = ContextCompat.getColor(host.activity, R.color.investa_text_secondary)
            xAxis.textSize = 9f
            xAxis.granularity = 1f
            xAxis.setLabelCount(3, true)
            xAxis.valueFormatter = object : ValueFormatter() {
                override fun getFormattedValue(value: Float): String {
                    val index = value.roundToInt().coerceIn(0, performance.lastIndex)
                    return performance.getOrNull(index)?.monthLabel.orEmpty()
                }
            }
            setTouchEnabled(true)
            setDragEnabled(false)
            setScaleEnabled(false)
            setScaleXEnabled(false)
            setScaleYEnabled(false)
            setPinchZoom(false)
            setHighlightPerDragEnabled(true)
            setHighlightPerTapEnabled(true)
            isDoubleTapToZoomEnabled = false
            setDrawMarkers(true)
            val portfolioMarker = PortfolioMarkerView(
                host.activity,
                dateLabels
            )
            marker = portfolioMarker
            addOnLayoutChangeListener { _, left, top, right, bottom, _, _, _, _ ->
                portfolioMarker.updateChartBounds(right - left, bottom - top)
            }
            post { portfolioMarker.updateChartBounds(width, height) }
            setOnTouchListener { view, event ->
                when (event.actionMasked) {
                    MotionEvent.ACTION_DOWN,
                    MotionEvent.ACTION_MOVE -> {
                        view.parent?.requestDisallowInterceptTouchEvent(true)
                        getHighlightByTouchPoint(event.x, event.y)?.let { highlight ->
                            updateSummary(highlight.x.roundToInt())
                            highlightValue(highlight, true)
                        }
                        true
                    }
                    MotionEvent.ACTION_UP,
                    MotionEvent.ACTION_CANCEL -> {
                        view.parent?.requestDisallowInterceptTouchEvent(false)
                        updateSummary(performance.lastIndex)
                        highlightValue(null, true)
                        invalidate()
                        true
                    }
                    else -> true
                }
            }
            extraLeftOffset = 4f
            extraRightOffset = 4f
            extraTopOffset = 8f
            extraBottomOffset = 8f
            data = LineData(
                dataSet(investedValues, host.activity.getString(R.string.total_invested), Color.rgb(255, 209, 102)),
                dataSet(currentValues, host.activity.getString(R.string.current_value), Color.rgb(64, 162, 216))
            )
            invalidate()
        }
    }

    private fun dailyPerformanceSnapshots(
        assets: List<AssetEntity>,
        transactions: List<TransactionEntity>,
        usdExchangeRate: Double,
        displayCurrency: String
    ): List<DailyPerformance> {
        val calendar = Calendar.getInstance().apply {
            set(Calendar.DAY_OF_MONTH, 1)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            add(Calendar.MONTH, -2)
        }
        val today = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val dateFormat = SimpleDateFormat("d MMM yyyy", LanguageManager.locale(host.activity))
        val monthFormat = SimpleDateFormat("MMM", LanguageManager.locale(host.activity))
        val performance = mutableListOf<DailyPerformance>()

        while (!calendar.after(today)) {
            val dayEnd = (calendar.clone() as Calendar).apply {
                set(Calendar.HOUR_OF_DAY, 23)
                set(Calendar.MINUTE, 59)
                set(Calendar.SECOND, 59)
                set(Calendar.MILLISECOND, 999)
            }.timeInMillis
            var invested = 0.0
            var current = 0.0

            assets.forEach { asset ->
                if (asset.createdAt > dayEnd) return@forEach

                var quantity = asset.quantity
                var costBasis = asset.investedAmount
                transactions.asSequence()
                    .filter { it.assetId == asset.id && it.date > dayEnd }
                    .sortedWith(compareByDescending<TransactionEntity> { it.date }.thenByDescending { it.id })
                    .forEach { transaction ->
                        val transactionAction = transaction.action.trim().uppercase(Locale.US)
                        val transactionRate = host.exchangeRateFor(transaction.currency)
                        val assetRate = host.exchangeRateFor(asset.currency)
                        val transactionCost = transaction.total * transactionRate / assetRate
                        when (transactionAction) {
                            "BUY" -> {
                                quantity -= transaction.quantity
                                costBasis -= transactionCost
                            }
                            "SELL" -> {
                                val sellCostBasis = if (transaction.costBasis > 0.0) {
                                    transaction.costBasis * transactionRate / assetRate
                                } else {
                                    val averagePrice = if (quantity > 0.0) costBasis / quantity else 0.0
                                    averagePrice * transaction.quantity
                                }
                                quantity += transaction.quantity
                                costBasis += sellCostBasis
                            }
                        }
                    }

                val assetExchangeRate = if (asset.currency == "USD") usdExchangeRate else 1.0
                val historicalQuantity = quantity.coerceAtLeast(0.0)
                val historicalCostBasis = costBasis.coerceAtLeast(0.0)
                invested += historicalCostBasis * assetExchangeRate
                current += historicalQuantity * asset.currentPrice * assetExchangeRate
            }

            performance += DailyPerformance(
                dateLabel = dateFormat.format(calendar.time),
                monthLabel = monthFormat.format(calendar.time),
                invested = convertCurrencyAmount(invested, "IDR", displayCurrency, usdExchangeRate).roundToLong(),
                current = convertCurrencyAmount(current, "IDR", displayCurrency, usdExchangeRate).roundToLong()
            )
            calendar.add(Calendar.DAY_OF_MONTH, 1)
        }
        return performance
    }

    private fun performanceAxisRange(values: List<Float>): AxisRange {
        val minimumValue = values.minOrNull()?.toDouble() ?: 0.0
        val maximumValue = values.maxOrNull()?.toDouble() ?: 0.0
        val scale = when {
            maximumValue >= 1_000_000_000.0 -> 1_000_000_000.0
            maximumValue >= 1_000_000.0 -> 1_000_000.0
            maximumValue >= 1_000.0 -> 1_000.0
            else -> 1.0
        }
        val step = scale * 2.5
        var minimum = floor(minimumValue / step) * step
        var maximum = ceil(maximumValue / step) * step

        if (maximum <= minimum) {
            if (minimum == 0.0) {
                maximum = step
            } else {
                minimum = (minimum - step).coerceAtLeast(0.0)
                maximum += step
            }
        }
        return AxisRange(minimum.toFloat(), maximum.toFloat())
    }

    private fun formatAxisValue(value: Float): String {
        val amount = value.toDouble()
        return when {
            amount >= 1_000_000_000.0 -> "${(amount / 1_000_000_000.0).formatCompact()}B"
            amount >= 1_000_000.0 -> "${(amount / 1_000_000.0).formatCompact()}M"
            amount >= 1_000.0 -> "${(amount / 1_000.0).formatCompact()}K"
            else -> amount.roundToLong().toString()
        }
    }

    private fun Double.formatCompact(): String = String.format(Locale.US, "%.1f", this)

    private data class DailyPerformance(
        val dateLabel: String,
        val monthLabel: String,
        val invested: Long,
        val current: Long
    )

    private data class AxisRange(
        val minimum: Float,
        val maximum: Float
    )

}
