package com.example.investa

import android.app.Activity
import android.content.res.ColorStateList
import android.os.Bundle
import android.text.Editable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.Spinner
import android.widget.TextView
import androidx.core.content.ContextCompat

private enum class AppScreen { HOME, ASSETS, REPORTS, SETTINGS, DETAIL, ADD, EDIT }

private data class Asset(
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
    val addedOn: String
)

private val dummyAssets = listOf(
    Asset(
        "Bitcoin",
        "BTC",
        "Crypto",
        "0.045 BTC",
        "Rp 54.000.000",
        "Rp 45.000.000",
        "+Rp 9.000.000",
        "+20.00%",
        "Rp 1.000.000.000",
        "Rp 1.200.000.000",
        "Long term investment",
        "12 Jul 2024"
    ),
    Asset(
        "Ethereum",
        "ETH",
        "Crypto",
        "0.5 ETH",
        "Rp 22.500.000",
        "Rp 19.500.000",
        "+Rp 3.000.000",
        "+15.38%",
        "Rp 39.000.000",
        "Rp 45.000.000",
        "Core crypto holding",
        "20 Aug 2024"
    ),
    Asset(
        "Solana",
        "SOL",
        "Crypto",
        "5 SOL",
        "Rp 8.750.000",
        "Rp 7.500.000",
        "+Rp 1.250.000",
        "+16.67%",
        "Rp 1.500.000",
        "Rp 1.750.000",
        "Growth allocation",
        "04 Sep 2024"
    ),
    Asset(
        "BBRI",
        "BBRI",
        "ID Stocks",
        "100 lot",
        "Rp 6.800.000",
        "Rp 6.000.000",
        "+Rp 800.000",
        "+13.33%",
        "Rp 6.000",
        "Rp 6.800",
        "Dividend watchlist",
        "15 Jan 2024"
    ),
    Asset(
        "SPY",
        "SPY",
        "US Stocks",
        "0.1 unit",
        "Rp 5.600.000",
        "Rp 5.000.000",
        "+Rp 600.000",
        "+12.00%",
        "Rp 50.000.000",
        "Rp 56.000.000",
        "Global index exposure",
        "18 Mar 2024"
    ),
    Asset(
        "Gold",
        "XAU",
        "Gold",
        "0.5 gr",
        "Rp 4.250.000",
        "Rp 3.950.000",
        "+Rp 300.000",
        "+7.59%",
        "Rp 7.900.000",
        "Rp 8.500.000",
        "Portfolio hedge",
        "01 Feb 2024"
    )
)

private val assetCategories =
    listOf("Crypto", "ID Stocks", "US Stocks", "Bonds", "Mutual Fund", "Gold")
private val allocation = listOf(
    "Crypto" to 55,
    "ID Stocks" to 18,
    "US Stocks" to 10,
    "Bonds" to 6,
    "Mutual Fund" to 6,
    "Gold" to 5
)

class MainActivity : Activity() {
    private lateinit var contentContainer: ViewGroup
    private lateinit var bottomNavigation: View
    private var currentScreen = AppScreen.HOME
    private var selectedAssetIndex = 0
    private var selectedCategory = "All"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = ContextCompat.getColor(this, R.color.investa_background)
        window.navigationBarColor = ContextCompat.getColor(this, R.color.investa_background)
        setContentView(R.layout.activity_main)
        contentContainer = findViewById(R.id.content_container)
        bottomNavigation = findViewById(R.id.bottom_navigation)
        setupBottomNavigation()
        showScreen(AppScreen.HOME)
    }

    private fun setupBottomNavigation() {
        findViewById<View>(R.id.nav_home).setOnClickListener { showScreen(AppScreen.HOME) }
        findViewById<View>(R.id.nav_assets).setOnClickListener { showScreen(AppScreen.ASSETS) }
        findViewById<View>(R.id.fab).setOnClickListener { showScreen(AppScreen.ADD) }
        findViewById<View>(R.id.nav_reports).setOnClickListener { showScreen(AppScreen.REPORTS) }
        findViewById<View>(R.id.nav_settings).setOnClickListener { showScreen(AppScreen.SETTINGS) }
    }

    private fun showScreen(screen: AppScreen) {
        currentScreen = screen
        val isMainScreen = screen in listOf(
            AppScreen.HOME,
            AppScreen.ASSETS,
            AppScreen.REPORTS,
            AppScreen.SETTINGS
        )
        bottomNavigation.visibility = if (isMainScreen) View.VISIBLE else View.GONE
        findViewById<View>(R.id.fab).visibility = if (isMainScreen) View.VISIBLE else View.GONE
        val contentParams = contentContainer.layoutParams as ViewGroup.MarginLayoutParams
        contentParams.bottomMargin = if (isMainScreen) dp(64) else 0
        contentContainer.layoutParams = contentParams
        contentContainer.removeAllViews()
        when (screen) {
            AppScreen.HOME -> renderHome()
            AppScreen.ASSETS -> renderAssets()
            AppScreen.REPORTS -> renderReports()
            AppScreen.SETTINGS -> renderSettings()
            AppScreen.DETAIL -> renderDetail()
            AppScreen.ADD -> renderForm(null)
            AppScreen.EDIT -> renderForm(dummyAssets[selectedAssetIndex])
        }
        if (isMainScreen) updateSelectedNavigation(screen)
    }

    private fun inflate(layout: Int): View =
        LayoutInflater.from(this).inflate(layout, contentContainer, false)

    private fun attach(view: View) {
        contentContainer.addView(
            view,
            ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        )
    }

    private fun renderHome() {
        val root = inflate(R.layout.screen_home)
        attach(root)
        val invested = root.findViewById<View>(R.id.summary_invested)
        invested.findViewById<TextView>(R.id.summary_title).text = "Total Invested"
        invested.findViewById<TextView>(R.id.summary_value).text = "Rp 106.900.000"
        invested.findViewById<TextView>(R.id.summary_percent).visibility = View.GONE
        val profit = root.findViewById<View>(R.id.summary_profit)
        profit.findViewById<TextView>(R.id.summary_title).text = "Total Profit"
        profit.findViewById<TextView>(R.id.summary_value).text = "Rp 18.750.000"
        profit.findViewById<TextView>(R.id.summary_value)
            .setTextColor(ContextCompat.getColor(this, R.color.investa_mint))
        profit.findViewById<TextView>(R.id.summary_percent).text = "+17.56%"
        bindLegendRows(root.findViewById(R.id.legend_container))
        root.findViewById<TextView>(R.id.see_all_assets)
            .setOnClickListener { showScreen(AppScreen.ASSETS) }
        val topAssets = root.findViewById<LinearLayout>(R.id.top_assets_container)
        listOf(0, 1, 2).forEach { index ->
            addAssetRow(
                topAssets,
                dummyAssets[index],
                true
            ) { selectedAssetIndex = index; showScreen(AppScreen.DETAIL) }
        }
    }

    private fun renderAssets() {
        val root = inflate(R.layout.screen_assets)
        attach(root)
        val categoryContainer = root.findViewById<LinearLayout>(R.id.category_container)
        (listOf("All") + assetCategories).forEach { category ->
            val chip = TextView(this).apply {
                text = category
                textSize = 12f
                setPadding(dp(17), dp(9), dp(17), dp(9))
                setOnClickListener { selectedCategory = category; renderAssets() }
            }
            chip.background = ContextCompat.getDrawable(
                this,
                if (category == selectedCategory) R.drawable.bg_chip_selected else R.drawable.bg_chip
            )
            chip.setTextColor(
                ContextCompat.getColor(
                    this,
                    if (category == selectedCategory) R.color.investa_background else R.color.investa_text_secondary
                )
            )
            val params = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            params.marginEnd = dp(8)
            categoryContainer.addView(chip, params)
        }
        populateAssetList(root)
    }

    private fun populateAssetList(root: View) {
        val list = root.findViewById<LinearLayout>(R.id.asset_list_container)
        list.removeAllViews()
        dummyAssets.filter { asset -> selectedCategory == "All" || asset.category == selectedCategory }
            .forEach { asset ->
                val index = dummyAssets.indexOf(asset)
                addAssetRow(list, asset, false) {
                    selectedAssetIndex = index; showScreen(AppScreen.DETAIL)
                }
            }
        if (list.childCount == 0) {
            val empty = LayoutInflater.from(this).inflate(R.layout.view_empty_state, list, false)
            list.addView(empty)
        }
    }

    private fun renderDetail() {
        val root = inflate(R.layout.screen_asset_detail)
        attach(root)
        val asset = dummyAssets[selectedAssetIndex]
        root.findViewById<TextView>(R.id.detail_name).text = asset.name
        root.findViewById<TextView>(R.id.detail_symbol).text = asset.symbol
        root.findViewById<TextView>(R.id.detail_value).text = asset.value
        root.findViewById<TextView>(R.id.detail_profit).text =
            "${asset.profit}  ${asset.profitPercent}"
        root.findViewById<View>(R.id.detail_back)
            .setOnClickListener { showScreen(AppScreen.ASSETS) }
        root.findViewById<View>(R.id.detail_edit).setOnClickListener { showScreen(AppScreen.EDIT) }
        root.findViewById<View>(R.id.detail_delete).setOnClickListener { }
        val rows = listOf(
            "Quantity" to asset.quantity,
            "Invested Amount" to asset.invested,
            "Average Price" to asset.averagePrice,
            "Current Price" to asset.currentPrice,
            "Category" to asset.category,
            "Notes" to asset.notes,
            "Added On" to asset.addedOn
        )
        val info = root.findViewById<LinearLayout>(R.id.detail_info_container)
        rows.forEachIndexed { index, (label, value) ->
            val row = info.getChildAt(index)
            row.findViewById<TextView>(R.id.detail_row_label).text = label
            row.findViewById<TextView>(R.id.detail_row_value).text = value
        }
    }

    private fun renderForm(asset: Asset?) {
        val root = inflate(R.layout.screen_asset_form)
        attach(root)
        root.findViewById<TextView>(R.id.form_title).text =
            if (asset == null) "Add Asset" else "Edit Asset"
        root.findViewById<TextView>(R.id.form_save).text =
            if (asset == null) "Save Asset" else "Save Changes"
        root.findViewById<View>(R.id.form_back)
            .setOnClickListener { showScreen(if (asset == null) AppScreen.HOME else AppScreen.DETAIL) }
        root.findViewById<View>(R.id.form_save)
            .setOnClickListener { showScreen(if (asset == null) AppScreen.HOME else AppScreen.DETAIL) }
        root.findViewById<EditText>(R.id.form_name).setText(asset?.name.orEmpty())
        root.findViewById<EditText>(R.id.form_symbol).setText(asset?.symbol.orEmpty())
        root.findViewById<EditText>(R.id.form_quantity)
            .setText(asset?.quantity?.substringBeforeLast(" ").orEmpty())
        root.findViewById<EditText>(R.id.form_invested)
            .setText(asset?.invested?.removePrefix("Rp ")?.replace(".", "").orEmpty())
        root.findViewById<EditText>(R.id.form_current_price)
            .setText(asset?.currentPrice?.removePrefix("Rp ")?.replace(".", "").orEmpty())
        root.findViewById<EditText>(R.id.form_notes).setText(asset?.notes.orEmpty())
        setupSpinner(
            root.findViewById(R.id.form_category),
            assetCategories,
            asset?.category ?: "Crypto"
        )
        setupSpinner(root.findViewById(R.id.form_currency), listOf("IDR", "USD"), "IDR")
    }

    private fun renderReports() {
        val root = inflate(R.layout.screen_reports)
        attach(root)
        root.findViewById<ImageView>(R.id.reports_back)
            .setOnClickListener { showScreen(AppScreen.HOME) }
        val legend = root.findViewById<ViewGroup>(R.id.report_legend_container)
        bindLegendRows(legend)
        val summary = root.findViewById<LinearLayout>(R.id.category_summary)
        val amounts =
            listOf("69.107.500", "22.617.000", "12.558.000", "7.539.000", "7.539.000", "6.290.000")
        allocation.forEachIndexed { index, (label, percent) ->
            val row = LayoutInflater.from(this)
                .inflate(R.layout.view_category_summary_row, summary, false)
            row.findViewById<TextView>(R.id.summary_category).text = label
            row.findViewById<TextView>(R.id.summary_amount).text = "Rp ${amounts[index]}"
            row.findViewById<TextView>(R.id.summary_percentage).text = "${percent}%"
            tint(row.findViewById(R.id.summary_dot), chartColor(index))
            summary.addView(row)
        }
        val categoryToggle = root.findViewById<TextView>(R.id.toggle_category)
        val assetToggle = root.findViewById<TextView>(R.id.toggle_asset)
        categoryToggle.setOnClickListener { setReportToggle(categoryToggle, assetToggle, true) }
        assetToggle.setOnClickListener { setReportToggle(categoryToggle, assetToggle, false) }
    }

    private fun renderSettings() {
        val root = inflate(R.layout.screen_settings)
        attach(root)
        addSettingsRow(
            root.findViewById(R.id.settings_preferences),
            R.drawable.ic_lucide_circle_dollar,
            "Base Currency",
            "IDR",
            true
        )
        addSettingsRow(
            root.findViewById(R.id.settings_preferences),
            R.drawable.ic_lucide_moon,
            "Theme",
            "Dark",
            true
        )
        addSettingsRow(
            root.findViewById(R.id.settings_preferences),
            R.drawable.ic_lucide_languages,
            "Language",
            "English",
            true
        )
        addSettingsRow(
            root.findViewById(R.id.settings_data),
            R.drawable.ic_lucide_download,
            "Data Backup",
            "",
            true
        )
        addSettingsRow(
            root.findViewById(R.id.settings_data),
            R.drawable.ic_lucide_upload,
            "Data Restore",
            "",
            true
        )
        addSettingsRow(
            root.findViewById(R.id.settings_about),
            R.drawable.ic_lucide_info,
            "About Investa",
            "",
            true
        )
        addSettingsRow(
            root.findViewById(R.id.settings_about),
            R.drawable.ic_lucide_info,
            "Version",
            "1.0.0",
            false
        )
        addSettingsRow(
            root.findViewById(R.id.settings_about),
            R.drawable.ic_lucide_message_circle,
            "Feedback",
            "",
            true
        )
        addSettingsRow(
            root.findViewById(R.id.settings_about),
            R.drawable.ic_lucide_star,
            "Rate Investa",
            "",
            true
        )
    }

    private fun addSettingsRow(
        parent: ViewGroup,
        iconRes: Int,
        label: String,
        value: String,
        chevron: Boolean
    ) {
        val row = LayoutInflater.from(this).inflate(R.layout.view_settings_row, parent, false)
        row.findViewById<ImageView>(R.id.settings_icon).apply {
            setImageResource(iconRes)
            imageTintList = ColorStateList.valueOf(
                ContextCompat.getColor(
                    this@MainActivity,
                    R.color.investa_icon_primary
                )
            )
        }
        row.findViewById<TextView>(R.id.settings_label).text = label
        row.findViewById<TextView>(R.id.settings_value).text = value
        row.findViewById<ImageView>(R.id.settings_chevron).visibility =
            if (chevron) View.VISIBLE else View.GONE
        parent.addView(row)
    }

    private fun addAssetRow(
        parent: ViewGroup,
        asset: Asset,
        compact: Boolean,
        onClick: () -> Unit
    ) {
        val row = LayoutInflater.from(this).inflate(R.layout.asset_item, parent, false)
        bindAsset(row, asset, compact)
        row.setOnClickListener { onClick() }
        parent.addView(row)
    }

    private fun bindAsset(row: View, asset: Asset, compact: Boolean) {
        row.findViewById<TextView>(R.id.asset_name).text = asset.name
        row.findViewById<TextView>(R.id.asset_symbol).text = asset.symbol
        row.findViewById<TextView>(R.id.asset_category).text = asset.category
        row.findViewById<TextView>(R.id.asset_quantity).text = asset.quantity
        row.findViewById<TextView>(R.id.asset_value).text = asset.value
        row.findViewById<TextView>(R.id.asset_profit).text = asset.profitPercent
        if (compact) {
            row.findViewById<TextView>(R.id.asset_category).visibility =
                View.GONE; row.findViewById<TextView>(R.id.asset_quantity).visibility = View.GONE
        }
    }

    private fun bindLegendRows(legend: ViewGroup) {
        val rows = (0 until legend.childCount).map { legend.getChildAt(it) }
        rows.forEachIndexed { index, row ->
            if (index < allocation.size) {
                val (label, percent) = allocation[index]
                row.findViewById<TextView>(R.id.legend_label).text = label
                row.findViewById<TextView>(R.id.legend_percent).text = "${percent}%"
                tint(row.findViewById(R.id.legend_dot), chartColor(index))
            }
        }
    }

    private fun setupSpinner(spinner: Spinner, values: List<String>, selected: String) {
        val adapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_item,
            values
        ).also { it.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item) }
        spinner.adapter = adapter
        spinner.setSelection(values.indexOf(selected).coerceAtLeast(0))
    }

    private fun setReportToggle(category: TextView, asset: TextView, categorySelected: Boolean) {
        if (categorySelected) category.setBackgroundResource(R.drawable.bg_primary_button) else category.background =
            null
        if (categorySelected) asset.background =
            null else asset.setBackgroundResource(R.drawable.bg_primary_button)
        category.setTextColor(
            ContextCompat.getColor(
                this,
                if (categorySelected) R.color.investa_background else R.color.investa_text_secondary
            )
        )
        asset.setTextColor(
            ContextCompat.getColor(
                this,
                if (categorySelected) R.color.investa_text_secondary else R.color.investa_background
            )
        )
    }

    private fun updateSelectedNavigation(screen: AppScreen) {
        val ids = listOf(R.id.nav_home, R.id.nav_assets, R.id.nav_reports, R.id.nav_settings)
        val selectedId = when (screen) {
            AppScreen.HOME -> R.id.nav_home; AppScreen.ASSETS -> R.id.nav_assets; AppScreen.REPORTS -> R.id.nav_reports; else -> R.id.nav_settings
        }
        ids.forEach { id ->
            val item = findViewById<LinearLayout>(id)
            val isSelected = id == selectedId
            item.background = null
            val color = ContextCompat.getColor(
                this,
                if (isSelected) R.color.investa_mint else R.color.investa_icon_inactive
            )
            for (childIndex in 0 until item.childCount) {
                when (val child = item.getChildAt(childIndex)) {
                    is ImageView -> child.imageTintList = ColorStateList.valueOf(color)
                    is TextView -> child.setTextColor(color)
                }
            }
        }
    }

    private fun tint(view: View, color: Int) {
        view.backgroundTintList = ColorStateList.valueOf(color)
    }

    private fun chartColor(index: Int) = intArrayOf(
        0xFFFF9F43.toInt(),
        0xFFFF6B6B.toInt(),
        0xFFFFD166.toInt(),
        0xFF4D96FF.toInt(),
        0xFFA66CFF.toInt(),
        0xFF00C2A8.toInt()
    )[index]

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
}
