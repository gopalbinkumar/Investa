package com.example.investa.ui.reports

import android.graphics.Color
import android.view.MotionEvent
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat
import androidx.lifecycle.lifecycleScope
import com.example.investa.R
import com.example.investa.data.repository.PerformanceRange
import com.example.investa.data.repository.PortfolioPerformancePoint
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
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.roundToInt
import kotlin.math.roundToLong

internal class ReportsRenderer(private val host: ScreenHost) {
    private var summaryByAsset = false
    private var reportsRoot: android.view.View? = null
    private var performanceRange = PerformanceRange.THREE_MONTHS
    private var performanceJob: Job? = null
    private var performanceRequestId = 0

    fun invalidateThemeCache() {
        performanceJob?.cancel()
        performanceJob = null
        reportsRoot = null
    }

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
        val totalAssetValue = displayAssets.sumOf { parseMoneyInput(it.value) ?: 0.0 }
        val totalCashValue = host.databaseCashAccounts.sumOf { account ->
            convertCurrencyAmount(account.balance, account.currencyCode, displayCurrency, usdExchangeRate)
        }
        val totalValue = totalAssetValue + totalCashValue
        val totalInvested = displayAssets.sumOf { parseMoneyInput(it.invested) ?: 0.0 } + totalCashValue
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
            text = String.format(Locale.US, "(%+.2f%%)", totalProfitPercentage)
            setTextColor(ContextCompat.getColor(host.activity, if (totalProfit >= 0) R.color.investa_profit else R.color.investa_loss))
        }
        setupPerformanceRangeControls(root)
        loadPerformance(root)
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

    private fun setupPerformanceRangeControls(root: android.view.View) {
        val ranges = listOf(
            R.id.performance_range_1m to PerformanceRange.ONE_MONTH,
            R.id.performance_range_3m to PerformanceRange.THREE_MONTHS,
            R.id.performance_range_6m to PerformanceRange.SIX_MONTHS,
            R.id.performance_range_1y to PerformanceRange.ONE_YEAR,
            R.id.performance_range_all to PerformanceRange.ALL
        )
        ranges.forEach { (id, range) ->
            val button = root.findViewById<TextView>(id)
            val selected = range == performanceRange
            button.setBackgroundResource(if (selected) R.drawable.bg_chip_selected else R.drawable.bg_chip)
            button.setTextColor(ContextCompat.getColor(host.activity, if (selected) R.color.investa_on_primary else R.color.investa_text_secondary))
            button.setOnClickListener {
                if (performanceRange != range) {
                    performanceRange = range
                    setupPerformanceRangeControls(root)
                    loadPerformance(root)
                }
            }
        }
    }

    private fun loadPerformance(root: android.view.View) {
        val chart = root.findViewById<LineChart>(R.id.performance_chart)
        val empty = root.findViewById<TextView>(R.id.performance_chart_empty)
        val requestId = ++performanceRequestId
        performanceJob?.cancel()
        chart.visibility = android.view.View.INVISIBLE
        empty.visibility = android.view.View.VISIBLE
        empty.text = host.activity.getString(R.string.loading_portfolio_performance)
        val displayCurrency = host.primaryCurrency
        performanceJob = host.activity.lifecycleScope.launch {
            val points = runCatching {
                withContext(Dispatchers.IO) {
                    host.portfolioPerformanceRepository.load(performanceRange, displayCurrency)
                }
            }.getOrDefault(emptyList())
            if (requestId != performanceRequestId || root.parent == null || host.currentScreen != AppScreen.REPORTS) return@launch
            if (points.isEmpty()) {
                empty.text = host.activity.getString(R.string.portfolio_performance_empty)
                return@launch
            }
            val dateFormat = SimpleDateFormat("d MMM yyyy", LanguageManager.locale(host.activity))
            val monthFormat = SimpleDateFormat("MMM", LanguageManager.locale(host.activity))
            val performance = points.map { point ->
                DailyPerformance(
                    dateLabel = dateFormat.format(point.day),
                    monthLabel = monthFormat.format(point.day),
                    invested = point.invested.roundToLong(),
                    current = point.current.roundToLong()
                )
            }
            empty.visibility = android.view.View.GONE
            chart.visibility = android.view.View.VISIBLE
            setupPerformanceChart(
                chart,
                performance,
                root.findViewById(R.id.reports_invested_value),
                root.findViewById(R.id.reports_current_value)
            )
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
                dateLabels,
                performance.map { PortfolioPerformancePoint(0L, it.invested.toDouble(), it.current.toDouble()) },
                host.primaryCurrency
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
