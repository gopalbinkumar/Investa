package com.example.investa.ui.home

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.MarginPageTransformer
import androidx.viewpager2.widget.ViewPager2
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
    private var homeRoot: View? = null
    private var allocationAdapter: AllocationPagerAdapter? = null

    fun render() {
        val root = homeRoot ?: host.inflate(R.layout.screen_home).also { homeRoot = it }
        if (root.parent == null) host.attach(root)
        val usdExchangeRate = host.exchangeRateFor("USD")
        val displayAssets = host.databaseAssets.map { asset ->
            asset.toUiAsset(host.activity).toIdrDisplay(usdExchangeRate)
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
        invested.findViewById<TextView>(R.id.summary_title).text = host.activity.getString(R.string.total_invested)
        invested.findViewById<TextView>(R.id.summary_value).text = formatAmount(totalInvested, displayCurrency, 0)
        invested.findViewById<TextView>(R.id.summary_percent).apply {
            text = host.activity.getString(R.string.last_month)
            visibility = View.VISIBLE
        }
        val profit = root.findViewById<View>(R.id.summary_profit)
        profit.findViewById<TextView>(R.id.summary_title).text = host.activity.getString(R.string.total_profit)
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
        val investedCategoryValues = assetCategories.map { category ->
            displayAssets
                .filter { it.category == category }
                .sumOf { parseMoneyInput(it.invested) ?: 0.0 }
        }
        val investedCategoryPercentages = investedCategoryValues.map { value ->
            if (totalInvested == 0.0) 0f else (value * 100.0 / totalInvested).toFloat()
        }
        val allocationPages = listOf(
            AllocationPage(
                title = host.activity.getString(R.string.portfolio_allocation),
                allocation = assetCategories.zip(categoryPercentages)
            ),
            AllocationPage(
                title = host.activity.getString(R.string.invested_allocation),
                allocation = assetCategories.zip(investedCategoryPercentages)
            )
        )
        val allocationTitle = root.findViewById<TextView>(R.id.allocation_title)
        val allocationPager = root.findViewById<ViewPager2>(R.id.allocation_pager)
        val existingAdapter = allocationAdapter
        if (existingAdapter == null) {
            allocationAdapter = AllocationPagerAdapter(allocationPages, ::setupAllocationChart)
            allocationPager.adapter = allocationAdapter
            allocationPager.setPageTransformer(MarginPageTransformer(host.dp(12)))
            allocationPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
                override fun onPageSelected(position: Int) {
                    updateAllocationPage(root, allocationTitle, allocationAdapter?.pages.orEmpty(), position)
                }
            })
        } else {
            existingAdapter.updatePages(allocationPages)
        }
        val selectedAllocationPage = allocationPager.currentItem.coerceIn(0, allocationPages.lastIndex)
        updateAllocationPage(root, allocationTitle, allocationPages, selectedAllocationPage)
        root.findViewById<TextView>(R.id.see_all_assets)
            .setOnClickListener { host.showScreen(com.example.investa.navigation.AppScreen.ASSETS) }

        val topAssets = root.findViewById<LinearLayout>(R.id.top_assets_container)
        topAssets.removeAllViews()
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

    private fun updateAllocationPage(
        root: View,
        title: TextView,
        pages: List<AllocationPage>,
        position: Int
    ) {
        val selectedPosition = position.coerceIn(pages.indices)
        title.text = pages[selectedPosition].title
        root.findViewById<View>(R.id.allocation_dot_portfolio).setBackgroundResource(
            if (selectedPosition == 0) R.drawable.bg_allocation_dot_active
            else R.drawable.bg_allocation_dot_inactive
        )
        root.findViewById<View>(R.id.allocation_dot_invested).setBackgroundResource(
            if (selectedPosition == 1) R.drawable.bg_allocation_dot_active
            else R.drawable.bg_allocation_dot_inactive
        )
    }

    private data class AllocationPage(
        val title: String,
        val allocation: List<Pair<String, Float>>
    )

    private class AllocationPagerAdapter(
        var pages: List<AllocationPage>,
        private val setupChart: (PieChart, List<Pair<String, Float>>) -> Unit
    ) : RecyclerView.Adapter<AllocationPagerAdapter.PageViewHolder>() {
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PageViewHolder {
            val page = LayoutInflater.from(parent.context)
                .inflate(R.layout.view_allocation_page, parent, false)
            return PageViewHolder(page)
        }

        override fun onBindViewHolder(holder: PageViewHolder, position: Int) {
            val allocationPage = pages[position]
            setupChart(holder.chart, allocationPage.allocation)
            bindLegendRows(
                holder.legend,
                allocationPage.allocation.map { (category, percentage) ->
                    category to percentage.roundToInt()
                }
            )
        }

        override fun getItemCount(): Int = pages.size

        fun updatePages(updatedPages: List<AllocationPage>) {
            if (pages == updatedPages) return
            pages = updatedPages
            notifyDataSetChanged()
        }

        class PageViewHolder(page: View) : RecyclerView.ViewHolder(page) {
            val chart: PieChart = page.findViewById(R.id.allocation_chart)
            val legend: ViewGroup = page.findViewById(R.id.allocation_legend)
        }
    }
}
