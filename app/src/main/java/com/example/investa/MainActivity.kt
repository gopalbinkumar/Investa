package com.example.investa

import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Bundle
import android.app.DatePickerDialog
import android.app.AlertDialog
import android.text.Editable
import android.text.InputFilter
import android.text.TextWatcher
import android.text.method.DigitsKeyListener
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.PopupMenu
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import android.view.animation.DecelerateInterpolator
import androidx.activity.ComponentActivity
import androidx.activity.viewModels
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.math.BigDecimal
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.roundToLong

private enum class AppScreen { HOME, ASSETS, REPORTS, SETTINGS, EXCHANGE_RATE, DETAIL, ADD, EDIT }
private enum class ScreenTransition { FORWARD, BACKWARD }
private const val SELECT_CATEGORY = "Select Category"

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
    val addedOn: String,
    val id: Long = 0,
    val currency: String = "IDR"
)

private val assetCategories =
    listOf("Crypto", "ID Stocks", "US Stocks", "Bonds", "Mutual Fund", "Gold")

class MainActivity : ComponentActivity() {
    private val assetViewModel: AssetViewModel by viewModels {
        AssetViewModelFactory(
            AssetRepository(
                InvestaDatabase.getInstance(applicationContext).assetDao()
            )
        )
    }
    private val transactionViewModel: TransactionViewModel by viewModels {
        TransactionViewModelFactory(
            TransactionRepository(
                InvestaDatabase.getInstance(applicationContext).transactionDao()
            )
        )
    }
    private val currencyViewModel: CurrencyViewModel by viewModels {
        CurrencyViewModelFactory(
            CurrencyRepository(
                InvestaDatabase.getInstance(applicationContext).currencyDao()
            )
        )
    }
    private lateinit var contentContainer: ViewGroup
    private lateinit var bottomNavigation: View
    private var currentScreen = AppScreen.HOME
    private var selectedCategory = "All"
    private var assetsRoot: View? = null
    private var databaseAssets: List<AssetEntity> = emptyList()
    private var databaseTransactions: List<TransactionEntity> = emptyList()
    private var databaseCurrencies: List<CurrencyEntity> = emptyList()
    private var selectedAsset: Asset? = null
    private var hasRenderedInitialScreen = false
    private var transactionObservation: Job? = null
    private var observedTransactionAssetId: Long? = null
    private var currentTransactions: List<TransactionEntity> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = ContextCompat.getColor(this, R.color.investa_background)
        window.navigationBarColor = ContextCompat.getColor(this, R.color.investa_background)
        setContentView(R.layout.activity_main)
        contentContainer = findViewById(R.id.content_container)
        bottomNavigation = findViewById(R.id.bottom_navigation)
        setupBottomNavigation()
        currencyViewModel.ensureDefaults()
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                assetViewModel.assets.collect { assets ->
                    databaseAssets = assets
                    when (currentScreen) {
                        AppScreen.HOME -> renderHome()
                        AppScreen.ASSETS -> renderAssets()
                        AppScreen.DETAIL -> refreshSelectedAsset(assets)
                        AppScreen.REPORTS -> renderReports()
                        else -> Unit
                    }
                }
            }
        }
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                transactionViewModel.observeAllTransactions().collect { transactions ->
                    databaseTransactions = transactions
                    when (currentScreen) {
                        AppScreen.HOME -> renderHome()
                        AppScreen.REPORTS -> renderReports()
                        else -> Unit
                    }
                }
            }
        }
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                currencyViewModel.currencies.collect { currencies ->
                    databaseCurrencies = currencies
                    when (currentScreen) {
                        AppScreen.HOME -> renderHome()
                        AppScreen.ASSETS -> renderAssets()
                        AppScreen.REPORTS -> renderReports()
                        else -> Unit
                    }
                }
            }
        }
        showScreen(AppScreen.HOME)
    }

    override fun onBackPressed() {
        when (currentScreen) {
            AppScreen.EXCHANGE_RATE -> showScreen(AppScreen.SETTINGS)
            AppScreen.DETAIL -> showScreen(AppScreen.ASSETS)
            AppScreen.ADD -> showScreen(AppScreen.ASSETS)
            AppScreen.EDIT -> showScreen(AppScreen.DETAIL)
            else -> super.onBackPressed()
        }
    }

    private fun setupBottomNavigation() {
        findViewById<View>(R.id.nav_home).setOnClickListener { showScreen(AppScreen.HOME) }
        findViewById<View>(R.id.nav_assets).setOnClickListener { showScreen(AppScreen.ASSETS) }
        findViewById<View>(R.id.fab).setOnClickListener { showScreen(AppScreen.ADD) }
        findViewById<View>(R.id.nav_reports).setOnClickListener { showScreen(AppScreen.REPORTS) }
        findViewById<View>(R.id.nav_settings).setOnClickListener { showScreen(AppScreen.SETTINGS) }
    }

    private fun showScreen(screen: AppScreen) {
        if (screen == currentScreen && hasRenderedInitialScreen) return
        val isInitialScreen = !hasRenderedInitialScreen
        val transition = when {
            screen == currentScreen -> ScreenTransition.FORWARD
            screenOrder(screen) >= screenOrder(currentScreen) -> ScreenTransition.FORWARD
            else -> ScreenTransition.BACKWARD
        }
        currentScreen = screen
        val isMainScreen = screen in listOf(
            AppScreen.HOME,
            AppScreen.ASSETS,
            AppScreen.REPORTS,
            AppScreen.SETTINGS
        )
        bottomNavigation.visibility = if (isMainScreen) View.VISIBLE else View.GONE
        findViewById<View>(R.id.fab).visibility =
            if (screen == AppScreen.ASSETS) View.VISIBLE else View.GONE
        val contentParams = contentContainer.layoutParams as ViewGroup.MarginLayoutParams
        contentParams.bottomMargin = if (isMainScreen) dp(56) else 0
        contentContainer.layoutParams = contentParams
        contentContainer.animate().cancel()
        if (isInitialScreen) {
            contentContainer.translationX = 0f
            contentContainer.alpha = 1f
        } else {
            contentContainer.translationX = if (transition == ScreenTransition.FORWARD) {
                resources.displayMetrics.widthPixels.toFloat() * 0.18f
            } else {
                -resources.displayMetrics.widthPixels.toFloat() * 0.18f
            }
            contentContainer.alpha = 0.85f
        }
        contentContainer.removeAllViews()
        when (screen) {
            AppScreen.HOME -> renderHome()
            AppScreen.ASSETS -> renderAssets()
            AppScreen.REPORTS -> renderReports()
            AppScreen.SETTINGS -> renderSettings()
            AppScreen.EXCHANGE_RATE -> renderExchangeRate()
            AppScreen.DETAIL -> renderDetail()
            AppScreen.ADD -> renderForm(null)
            AppScreen.EDIT -> {
                val entity = databaseAssets.firstOrNull { it.id == selectedAsset?.id }
                if (entity == null) {
                    selectedAsset = null
                    showScreen(AppScreen.ASSETS)
                    return
                }
                renderForm(entity.toUiAsset())
            }
        }
        if (!isInitialScreen) {
            contentContainer.animate()
                .translationX(0f)
                .alpha(1f)
                .setDuration(220L)
                .setInterpolator(DecelerateInterpolator())
                .start()
        }
        hasRenderedInitialScreen = true
        if (isMainScreen) updateSelectedNavigation(screen)
    }

    private fun screenOrder(screen: AppScreen): Int = when (screen) {
        AppScreen.HOME -> 0
        AppScreen.ASSETS -> 1
        AppScreen.REPORTS -> 2
        AppScreen.SETTINGS -> 3
        AppScreen.EXCHANGE_RATE -> 4
        AppScreen.DETAIL -> 5
        AppScreen.ADD -> 6
        AppScreen.EDIT -> 7
    }

    private fun updateSelectedNavigation(screen: AppScreen) {
        val ids = listOf(R.id.nav_home, R.id.nav_assets, R.id.nav_reports, R.id.nav_settings)
        val selectedId = when (screen) {
            AppScreen.HOME -> R.id.nav_home
            AppScreen.ASSETS -> R.id.nav_assets
            AppScreen.REPORTS -> R.id.nav_reports
            else -> R.id.nav_settings
        }
        ids.forEach { id ->
            findViewById<View>(id).isSelected = id == selectedId
        }
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

    private fun exchangeRateFor(currency: String): Double {
        if (currency != "USD") return 1.0
        return databaseCurrencies.firstOrNull { it.code == "USD" }?.exchangeRate ?: 16500.0
    }

    private fun renderHome() {
        contentContainer.removeAllViews()
        val root = inflate(R.layout.screen_home)
        attach(root)
        val usdExchangeRate = exchangeRateFor("USD")
        val displayAssets = databaseAssets.map { asset ->
            asset.toUiAsset().withTransactionHistory(
                databaseTransactions.filter { it.assetId == asset.id }
            ).toIdrDisplay(usdExchangeRate)
        }
        val totalValue = displayAssets.sumOf { parseMoneyInput(it.value) ?: 0.0 }
        val totalInvested = displayAssets.sumOf { parseMoneyInput(it.invested) ?: 0.0 }
        val totalProfit = totalValue - totalInvested
        val totalProfitPercentage = if (totalInvested == 0.0) 0.0 else {
            totalProfit * 100.0 / totalInvested
        }
        val displayCurrency = "IDR"

        root.findViewById<TextView>(R.id.portfolio_value).text =
            formatAmount(totalValue, displayCurrency, 0)
        root.findViewById<TextView>(R.id.portfolio_profit).apply {
            text = formatSignedAmount(totalProfit, displayCurrency, 0)
            setTextColor(
                ContextCompat.getColor(
                    this@MainActivity,
                    if (totalProfit >= 0) R.color.investa_mint else R.color.investa_loss
                )
            )
        }
        root.findViewById<TextView>(R.id.portfolio_profit_percent).apply {
            text = String.format(Locale.US, "%+.2f%%", totalProfitPercentage)
            setTextColor(
                ContextCompat.getColor(
                    this@MainActivity,
                    if (totalProfit >= 0) R.color.investa_mint else R.color.investa_loss
                )
            )
        }

        val invested = root.findViewById<View>(R.id.summary_invested)
        invested.findViewById<TextView>(R.id.summary_title).text = "Total Invested"
        invested.findViewById<TextView>(R.id.summary_value).text =
            formatAmount(totalInvested, displayCurrency, 0)
        invested.findViewById<TextView>(R.id.summary_percent).apply {
            text = "— last month"
            visibility = View.VISIBLE
        }
        val profit = root.findViewById<View>(R.id.summary_profit)
        profit.findViewById<TextView>(R.id.summary_title).text = "Total Profit"
        profit.findViewById<TextView>(R.id.summary_value).apply {
            text = formatSignedAmount(totalProfit, displayCurrency, 0)
            setTextColor(
                ContextCompat.getColor(
                    this@MainActivity,
                    if (totalProfit >= 0) R.color.investa_mint else R.color.investa_loss
                )
            )
        }
        profit.findViewById<TextView>(R.id.summary_percent).apply {
            text = String.format(Locale.US, "%+.2f%%", totalProfitPercentage)
            setTextColor(
                ContextCompat.getColor(
                    this@MainActivity,
                    if (totalProfit >= 0) R.color.investa_mint else R.color.investa_loss
                )
            )
        }

        val categoryValues = assetCategories.map { category ->
            displayAssets
                .filter { it.category == category }
                .sumOf { parseMoneyInput(it.value) ?: 0.0 }
        }
        val categoryPercentages = categoryValues.map { value ->
            if (totalValue == 0.0) 0f else (value * 100.0 / totalValue).toFloat()
        }
        bindLegendRows(
            root.findViewById(R.id.legend_container),
            assetCategories.zip(categoryPercentages.map { it.roundToInt() })
        )
        root.findViewById<DonutChartView>(R.id.portfolio_allocation_chart)
            .setAllocationPercentages(categoryPercentages)
        root.findViewById<TextView>(R.id.see_all_assets)
            .setOnClickListener { showScreen(AppScreen.ASSETS) }
        val topAssets = root.findViewById<LinearLayout>(R.id.top_assets_container)
        displayAssets
            .map { it.withCalculatedCurrentValue() }
            .sortedByDescending { parseMoneyInput(it.value) ?: 0.0 }
            .take(3)
            .forEach { topAsset ->
            addAssetRow(
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

    private fun renderAssets() {
        val root = assetsRoot ?: inflate(R.layout.screen_assets).also { assetsRoot = it }
        if (root.parent == null) attach(root)
        val categoryContainer = root.findViewById<LinearLayout>(R.id.category_container)
        if (categoryContainer.childCount == 0) {
            (listOf("All") + assetCategories).forEach { category ->
                val chip = TextView(this).apply {
                    text = category
                    textSize = 12f
                    setPadding(dp(17), dp(9), dp(17), dp(9))
                    setOnClickListener {
                        selectedCategory = category
                        updateCategorySelection(categoryContainer)
                        populateAssetList(root)
                    }
                }
                val params = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
                params.marginEnd = dp(8)
                categoryContainer.addView(chip, params)
            }
        }
        updateCategorySelection(categoryContainer)
        populateAssetList(root)
    }

    private fun updateCategorySelection(categoryContainer: ViewGroup) {
        for (index in 0 until categoryContainer.childCount) {
            val chip = categoryContainer.getChildAt(index) as TextView
            val isSelected = chip.text.toString() == selectedCategory
            chip.background = ContextCompat.getDrawable(
                this,
                if (isSelected) R.drawable.bg_chip_selected else R.drawable.bg_chip
            )
            chip.setTextColor(
                ContextCompat.getColor(
                    this,
                    if (isSelected) R.color.investa_background else R.color.investa_text_secondary
                )
            )
        }
    }

    private fun populateAssetList(root: View) {
        val list = root.findViewById<LinearLayout>(R.id.asset_list_container)
        list.removeAllViews()
        val usdExchangeRate = exchangeRateFor("USD")
        val filteredAssets = databaseAssets
            .filter { asset -> selectedCategory == "All" || asset.category == selectedCategory }
            .sortedBy { it.symbol.trim().uppercase(Locale.ROOT) }
        root.findViewById<TextView>(R.id.asset_count).text = "${filteredAssets.size} Assets"
        filteredAssets.forEach { entity ->
                val nativeAsset = entity.toUiAsset()
                addAssetRow(list, nativeAsset.toIdrDisplay(usdExchangeRate), false) {
                    selectedAsset = nativeAsset
                    showScreen(AppScreen.DETAIL)
                }
            }
        if (list.childCount == 0) {
            val empty = LayoutInflater.from(this).inflate(R.layout.view_empty_state, list, false)
            list.addView(empty)
        }
    }

    private fun renderDetail() {
        contentContainer.removeAllViews()
        val root = inflate(R.layout.screen_asset_detail)
        attach(root)
        val asset = selectedAsset ?: run {
            showScreen(AppScreen.ASSETS)
            return
        }
        observeTransactions(asset)
        val currencySymbol = databaseCurrencies
            .firstOrNull { it.code == asset.currency }
            ?.symbol
            ?.takeIf { it.isNotBlank() }
            ?: currencySymbolFor(asset.currency)
        val displayAsset = asset
            .withTransactionHistory(currentTransactions, 2, currencySymbol)
            .withAmountPrecision(2, currencySymbol)
        root.findViewById<TextView>(R.id.detail_name).text = displayAsset.name
        root.findViewById<TextView>(R.id.detail_symbol).text = displayAsset.symbol
        root.findViewById<TextView>(R.id.detail_value).text = displayAsset.value
        root.findViewById<TextView>(R.id.detail_profit).text =
            "${displayAsset.profit}  ${displayAsset.profitPercent}"
        root.findViewById<View>(R.id.detail_back)
            .setOnClickListener { showScreen(AppScreen.ASSETS) }
        root.findViewById<View>(R.id.detail_buy)
            .setOnClickListener { showTransactionDrawer(displayAsset, isBuy = true) }
        root.findViewById<View>(R.id.detail_sell)
            .setOnClickListener { showTransactionDrawer(displayAsset, isBuy = false) }
        root.findViewById<View>(R.id.detail_change_current_price)
            .setOnClickListener { showCurrentPriceDrawer(displayAsset) }
        root.findViewById<View>(R.id.detail_more).setOnClickListener {
            showAssetOptions(displayAsset)
        }
        val rows = listOf(
            "Quantity" to displayAsset.quantity,
            "Invested Amount" to displayAsset.invested,
            "Average Price" to displayAsset.averagePrice,
            "Current Price" to displayAsset.currentPrice,
            "Category" to displayAsset.category,
            "Notes" to displayAsset.notes.ifBlank { "-" },
            "Added On" to displayAsset.addedOn
        )
        val info = root.findViewById<LinearLayout>(R.id.detail_info_container)
        rows.forEachIndexed { index, (label, value) ->
            val row = info.getChildAt(index)
            row.findViewById<TextView>(R.id.detail_row_label).text = label
            row.findViewById<TextView>(R.id.detail_row_value).text = value
        }
        populateTransactionHistory(root, displayAsset, currentTransactions, currencySymbol)
    }

    private fun refreshSelectedAsset(assets: List<AssetEntity>) {
        val selectedId = selectedAsset?.id ?: return
        val refreshedEntity = assets.firstOrNull { it.id == selectedId }
        if (refreshedEntity == null) {
            selectedAsset = null
            showScreen(AppScreen.ASSETS)
        } else {
            selectedAsset = refreshedEntity.toUiAsset()
            renderDetail()
        }
    }

    private fun showAssetOptions(asset: Asset) {
        val anchor = findViewById<View>(R.id.detail_more)
        PopupMenu(this, anchor).apply {
            menu.add("Edit")
            menu.add("Delete")
            setOnMenuItemClickListener { item ->
                when (item.title.toString()) {
                    "Edit" -> {
                        showScreen(AppScreen.EDIT)
                        true
                    }
                    "Delete" -> {
                        confirmDeleteAsset(asset)
                        true
                    }
                    else -> false
                }
            }
        }.show()
    }

    private fun confirmDeleteAsset(asset: Asset) {
        if (asset.id == 0L) {
            Toast.makeText(this, "Asset not found", Toast.LENGTH_SHORT).show()
            return
        }
        AlertDialog.Builder(this)
            .setTitle("Delete Asset")
            .setMessage("Delete ${asset.name} from assets?")
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Delete") { _, _ ->
                val entity = databaseAssets.firstOrNull { it.id == asset.id }
                if (entity != null) {
                    assetViewModel.deleteAsset(entity) {
                        selectedAsset = null
                        showScreen(AppScreen.ASSETS)
                    }
                }
            }
            .show()
    }

    private fun observeTransactions(asset: Asset) {
        if (asset.id == 0L) {
            transactionObservation?.cancel()
            transactionObservation = null
            observedTransactionAssetId = null
            currentTransactions = emptyList()
            return
        }
        if (observedTransactionAssetId == asset.id) return

        transactionObservation?.cancel()
        currentTransactions = emptyList()
        observedTransactionAssetId = asset.id
        transactionObservation = lifecycleScope.launch {
            transactionViewModel.observeTransactions(asset.id).collect { transactions ->
                currentTransactions = transactions
                if (currentScreen == AppScreen.DETAIL && selectedAsset?.id == asset.id) {
                    renderDetail()
                }
            }
        }
    }

    private fun populateTransactionHistory(
        root: View,
        asset: Asset,
        transactions: List<TransactionEntity>,
        currencySymbol: String = currencySymbolFor(asset.currency)
    ) {
        val historyContainer = root.findViewById<LinearLayout>(R.id.detail_history_container)
        if (transactions.isEmpty()) {
            val empty = LayoutInflater.from(this)
                .inflate(R.layout.view_empty_state, historyContainer, false)
            empty.findViewById<TextView>(R.id.empty_title).text = "No transactions found"
            empty.findViewById<TextView>(R.id.empty_message).text =
                "Add a buy or sell transaction"
            historyContainer.addView(empty)
            return
        }
        transactions.forEach { transaction ->
            val action = transaction.action.lowercase().replaceFirstChar { it.uppercase() }
            val card = LayoutInflater.from(this)
                .inflate(R.layout.view_transaction_history_card, historyContainer, false)
            card.findViewById<TextView>(R.id.history_type).apply {
                text = action
                setTextColor(
                    ContextCompat.getColor(
                        this@MainActivity,
                        if (transaction.action == "BUY") R.color.investa_buy else R.color.investa_loss
                    )
                )
            }
            card.findViewById<TextView>(R.id.history_date).text = formatTransactionDate(transaction.date)
            card.findViewById<TextView>(R.id.history_quantity).text =
                formatTransactionQuantity(transaction.quantity, asset.symbol)
            card.findViewById<TextView>(R.id.history_price_label).text =
                "$action Price"
            card.findViewById<TextView>(R.id.history_price).text =
                formatAmount(transaction.price, asset.currency, currencySymbol, 2)
            card.setOnClickListener {
                showTransactionDrawer(asset, transaction.action == "BUY", transaction)
            }
            historyContainer.addView(card)
        }
    }

    private fun showCurrentPriceDrawer(asset: Asset) {
        if (asset.id == 0L) {
            Toast.makeText(this, "Asset not found", Toast.LENGTH_SHORT).show()
            return
        }

        val dialog = BottomSheetDialog(this)
        val drawer = layoutInflater.inflate(R.layout.bottom_sheet_current_price, null)
        dialog.setContentView(drawer)
        val priceInput = drawer.findViewById<EditText>(R.id.current_price_input)
        val priceUnit = drawer.findViewById<TextView>(R.id.current_price_unit)
        val saveButton = drawer.findViewById<View>(R.id.current_price_save)

        priceUnit.text = priceUnitSuffix(asset.category, asset.symbol)
        priceInput.setText(
            parseMoneyInput(asset.currentPrice)?.let {
                formatInputAmount(it, asset.currency)
            }.orEmpty()
        )
        installMoneyInputFormatter(priceInput) { asset.currency }

        saveButton.setOnClickListener {
            val currentPrice = parseMoneyInput(priceInput.text.toString())
            if (currentPrice == null || currentPrice <= 0.0) {
                priceInput.error = "Enter a valid current price"
                priceInput.requestFocus()
                return@setOnClickListener
            }

            val entity = databaseAssets.firstOrNull { it.id == asset.id }
            if (entity == null) {
                Toast.makeText(this, "Asset not found", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val updatedEntity = entity.copy(
                currentPrice = currentPrice,
                updatedAt = System.currentTimeMillis()
            )
            assetViewModel.updateAsset(updatedEntity) {
                databaseAssets = databaseAssets.map { databaseAsset ->
                    if (databaseAsset.id == updatedEntity.id) updatedEntity else databaseAsset
                }
                selectedAsset = updatedEntity.toUiAsset()
                dialog.dismiss()
                renderDetail()
            }
        }

        dialog.setOnShowListener {
            val bottomSheet = dialog.findViewById<FrameLayout>(
                com.google.android.material.R.id.design_bottom_sheet
            )
            bottomSheet?.setBackgroundColor(Color.TRANSPARENT)
            bottomSheet?.let {
                BottomSheetBehavior.from(it).state = BottomSheetBehavior.STATE_EXPANDED
            }
        }
        dialog.show()
    }

    private fun showTransactionDrawer(
        asset: Asset,
        isBuy: Boolean,
        transaction: TransactionEntity? = null
    ) {
        val dialog = BottomSheetDialog(this)
        val drawer = layoutInflater.inflate(R.layout.bottom_sheet_transaction, null)
        dialog.setContentView(drawer)
        val title = drawer.findViewById<TextView>(R.id.transaction_title)
        val dateInput = drawer.findViewById<EditText>(R.id.transaction_date)
        val quantityInput = drawer.findViewById<EditText>(R.id.transaction_quantity)
        val quantityUnit = drawer.findViewById<TextView>(R.id.transaction_quantity_unit)
        val priceInput = drawer.findViewById<EditText>(R.id.transaction_price)
        val priceUnit = drawer.findViewById<TextView>(R.id.transaction_price_unit)
        val feeInput = drawer.findViewById<EditText>(R.id.transaction_fee)
        val feeToggle = drawer.findViewById<android.widget.CheckBox>(R.id.transaction_fee_enabled)
        val notesInput = drawer.findViewById<EditText>(R.id.transaction_notes)
        val total = drawer.findViewById<TextView>(R.id.transaction_total)
        val saveButton = drawer.findViewById<View>(R.id.transaction_save)
        val dateFormat = SimpleDateFormat("dd MMMM yyyy", Locale.ENGLISH)

        drawer.findViewById<TextView>(R.id.transaction_price_label).text =
            if (isBuy) "Buy Price" else "Sell Price"
        quantityUnit.text = quantityUnitHint(asset.category, asset.symbol)
        priceUnit.text = priceUnitSuffix(asset.category, asset.symbol)

        dateInput.setText(
            transaction?.let { formatTransactionDate(it.date) }
                ?: SimpleDateFormat("dd MMMM yyyy", Locale.ENGLISH).format(Date())
        )
        quantityInput.setText(
            transaction?.let { formatEditableAmount(formatQuantityValue(it.quantity), "IDR") }
                ?: ""
        )
        priceInput.setText(
            transaction?.let { formatInputAmount(it.price, asset.currency) } ?: ""
        )
        feeInput.setText(
            transaction?.fee?.takeIf { it > 0.0 }?.let { formatInputAmount(it, asset.currency) }
                ?: ""
        )
        notesInput.setText(transaction?.notes.orEmpty())
        feeToggle.isChecked = transaction?.fee?.let { it > 0.0 } ?: false

        installMoneyInputFormatter(priceInput) { asset.currency }
        installMoneyInputFormatter(feeInput) { asset.currency }
        installDecimalInputFormatter(quantityInput)

        fun showDatePicker() {
            val selectedDate = Calendar.getInstance().apply {
                timeInMillis = parseTransactionDate(dateInput.text.toString())
            }
            DatePickerDialog(
                this,
                { _, year, month, dayOfMonth ->
                    selectedDate.set(year, month, dayOfMonth)
                    dateInput.setText(dateFormat.format(selectedDate.time))
                },
                selectedDate.get(Calendar.YEAR),
                selectedDate.get(Calendar.MONTH),
                selectedDate.get(Calendar.DAY_OF_MONTH)
            ).show()
        }

        dateInput.setOnClickListener { showDatePicker() }

        fun updateTotal() {
            val quantity = parseTransactionQuantity(quantityInput.text.toString())
            val price = parseMoneyInput(priceInput.text.toString())
            val fee = if (feeToggle.isChecked) {
                parseMoneyInput(feeInput.text.toString()) ?: 0.0
            } else {
                0.0
            }
            total.text = if (quantity != null && price != null) {
                formatAmount(calculateTransactionTotal(isBuy, quantity, price, fee), asset.currency)
            } else {
                "—"
            }
        }

        feeToggle.setOnCheckedChangeListener { _, enabled ->
            feeInput.isEnabled = enabled
            feeInput.alpha = if (enabled) 1f else 0.45f
            updateTotal()
        }
        feeInput.isEnabled = feeToggle.isChecked
        feeInput.alpha = if (feeToggle.isChecked) 1f else 0.45f
        listOf(quantityInput, priceInput, feeInput).forEach { input ->
            input.addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(
                    s: CharSequence?,
                    start: Int,
                    count: Int,
                    after: Int
                ) = Unit

                override fun onTextChanged(
                    s: CharSequence?,
                    start: Int,
                    before: Int,
                    count: Int
                ) = updateTotal()

                override fun afterTextChanged(s: Editable?) = Unit
            })
        }
        updateTotal()

        saveButton.setOnClickListener {
            if (transaction == null && asset.id == 0L) {
                Toast.makeText(this, "Save the asset before adding a transaction", Toast.LENGTH_SHORT)
                    .show()
                return@setOnClickListener
            }

            val quantity = parseTransactionQuantity(quantityInput.text.toString())
            val price = parseMoneyInput(priceInput.text.toString())
            val fee = if (feeToggle.isChecked) {
                parseMoneyInput(feeInput.text.toString()) ?: 0.0
            } else {
                0.0
            }
            val now = System.currentTimeMillis()
            when {
                quantity == null || quantity <= 0.0 -> quantityInput.apply {
                    error = "Enter a valid quantity"
                    requestFocus()
                }
                price == null || price <= 0.0 -> priceInput.apply {
                    error = "Enter a valid price"
                    requestFocus()
                }
                fee < 0.0 -> feeInput.apply {
                    error = "Enter a valid fee"
                    requestFocus()
                }
                else -> {
                    val updatedTransaction = transaction?.copy(
                        date = parseTransactionDate(dateInput.text.toString()),
                        quantity = quantity,
                        price = price,
                        fee = fee,
                        total = calculateTransactionTotal(isBuy, quantity, price, fee),
                        notes = notesInput.text.toString().trim(),
                        updatedAt = now
                    )
                    if (updatedTransaction != null) {
                        transactionViewModel.updateTransaction(updatedTransaction) { dialog.dismiss() }
                    } else {
                        transactionViewModel.addTransaction(
                            TransactionEntity(
                                assetId = asset.id,
                                action = if (isBuy) "BUY" else "SELL",
                                date = parseTransactionDate(dateInput.text.toString()),
                                quantity = quantity,
                                price = price,
                                fee = fee,
                                total = calculateTransactionTotal(isBuy, quantity, price, fee),
                                notes = notesInput.text.toString().trim(),
                                createdAt = now,
                                updatedAt = now
                            )
                        ) { dialog.dismiss() }
                    }
                }
            }
        }

        if (transaction != null) {
            title.text = "Transaction Detail"
            saveButton.visibility = View.GONE
            val inputs = listOf(quantityInput, priceInput, feeInput, notesInput)
            inputs.forEach { input ->
                input.isFocusable = false
                input.isFocusableInTouchMode = false
                input.setOnClickListener {
                    inputs.forEach { editableInput ->
                        editableInput.isFocusable = true
                        editableInput.isFocusableInTouchMode = true
                    }
                    feeToggle.isClickable = true
                    feeToggle.isFocusable = true
                    saveButton.visibility = View.VISIBLE
                    input.requestFocus()
                }
            }
            dateInput.setOnClickListener {
                inputs.forEach { editableInput ->
                    editableInput.isFocusable = true
                    editableInput.isFocusableInTouchMode = true
                }
                feeToggle.isClickable = true
                feeToggle.isFocusable = true
                saveButton.visibility = View.VISIBLE
                showDatePicker()
            }
            feeToggle.isClickable = false
            feeToggle.isFocusable = false
        }

        dialog.setOnShowListener {
            val bottomSheet = dialog.findViewById<FrameLayout>(
                com.google.android.material.R.id.design_bottom_sheet
            )
            bottomSheet?.setBackgroundColor(Color.TRANSPARENT)
            bottomSheet?.let {
                BottomSheetBehavior.from(it).state = BottomSheetBehavior.STATE_EXPANDED
            }
        }
        dialog.show()
    }

    private fun renderForm(asset: Asset?) {
        val root = inflate(R.layout.screen_asset_form)
        attach(root)
        val nameInput = root.findViewById<EditText>(R.id.form_name)
        val symbolInput = root.findViewById<EditText>(R.id.form_symbol)
        val quantityInput = root.findViewById<EditText>(R.id.form_quantity)
        val quantityUnitHintView = root.findViewById<TextView>(R.id.form_quantity_unit_hint)
        val investedInput = root.findViewById<EditText>(R.id.form_invested)
        val averagePriceInput = root.findViewById<EditText>(R.id.form_average_price)
        val averagePriceUnit = root.findViewById<TextView>(R.id.form_average_price_unit)
        val currentPriceInput = root.findViewById<EditText>(R.id.form_current_price)
        val currentPriceUnit = root.findViewById<TextView>(R.id.form_current_price_unit)
        val notesInput = root.findViewById<EditText>(R.id.form_notes)
        val categorySpinner = root.findViewById<Spinner>(R.id.form_category)
        val currencySpinner = root.findViewById<Spinner>(R.id.form_currency)
        val assetFieldsContainer = root.findViewById<View>(R.id.asset_fields_container)
        symbolInput.filters = arrayOf(InputFilter.AllCaps())
        root.findViewById<TextView>(R.id.form_title).text =
            if (asset == null) "Add Asset" else "Edit Asset"
        root.findViewById<TextView>(R.id.form_save).text =
            if (asset == null) "Save Asset" else "Save Changes"
        root.findViewById<View>(R.id.form_back)
            .setOnClickListener { showScreen(if (asset == null) AppScreen.ASSETS else AppScreen.DETAIL) }
        root.findViewById<View>(R.id.form_save).setOnClickListener {
            val name = nameInput.text.toString().trim()
            val symbol = symbolInput.text.toString().trim().uppercase(Locale.ROOT)
            val category = categorySpinner.selectedItem?.toString().orEmpty()
            val quantity = parseTransactionQuantity(quantityInput.text.toString())
            val averagePrice = parseMoneyInput(averagePriceInput.text.toString())
            val currentPrice = parseMoneyInput(currentPriceInput.text.toString())
            val investedAmount = if (quantity != null && averagePrice != null) {
                quantity * averagePrice
            } else {
                null
            }
            val currency = currencySpinner.selectedItem?.toString().orEmpty()
            val now = System.currentTimeMillis()

            when {
                name.isEmpty() -> nameInput.apply {
                    error = "Asset name is required"
                    requestFocus()
                }
                symbol.isEmpty() -> symbolInput.apply {
                    error = "Symbol is required"
                    requestFocus()
                }
                category == SELECT_CATEGORY || category.isEmpty() ->
                    Toast.makeText(this, "Select a category", Toast.LENGTH_SHORT).show()
                quantity == null || quantity <= 0.0 -> quantityInput.apply {
                    error = "Enter a valid quantity"
                    requestFocus()
                }
                investedAmount == null || investedAmount <= 0.0 -> investedInput.apply {
                    error = "Enter a valid invested amount"
                    requestFocus()
                }
                averagePrice == null || averagePrice <= 0.0 -> averagePriceInput.apply {
                    error = "Enter a valid average price"
                    requestFocus()
                }
                currentPrice == null || currentPrice <= 0.0 -> currentPriceInput.apply {
                    error = "Enter a valid current price"
                    requestFocus()
                }
                else -> {
                    val existingEntity = asset?.id?.let { id ->
                        databaseAssets.firstOrNull { it.id == id }
                    }
                    val updatedEntity = AssetEntity(
                        id = asset?.id ?: 0L,
                        name = name,
                        symbol = symbol,
                        category = category,
                        quantity = quantity,
                        investedAmount = investedAmount,
                        averagePrice = averagePrice,
                        currentPrice = currentPrice,
                        currency = currency,
                        notes = notesInput.text.toString().trim(),
                        createdAt = existingEntity?.createdAt ?: now,
                        updatedAt = now
                    )
                    if (asset == null) {
                        assetViewModel.addAsset(updatedEntity) { assetId ->
                            val savedEntity = updatedEntity.copy(id = assetId)
                            databaseAssets = databaseAssets + savedEntity
                            selectedAsset = savedEntity.toUiAsset()
                            showScreen(AppScreen.DETAIL)
                        }
                    } else {
                        assetViewModel.updateAsset(updatedEntity) {
                            databaseAssets = databaseAssets.map { entity ->
                                if (entity.id == updatedEntity.id) updatedEntity else entity
                            }
                            selectedAsset = updatedEntity.toUiAsset()
                            showScreen(AppScreen.DETAIL)
                        }
                    }
                }
            }
        }
        nameInput.setText(asset?.name.orEmpty())
        symbolInput.setText(asset?.symbol.orEmpty())
        quantityInput.setText(
            formatEditableAmount(
                asset?.quantity?.substringBeforeLast(" ").orEmpty(),
                "IDR"
            )
        )
        investedInput.setText("")
        averagePriceInput.setText(
            asset?.let {
                parseMoneyInput(it.averagePrice)?.let { value ->
                    formatInputAmount(value, it.currency)
                }
            }.orEmpty()
        )
        currentPriceInput.setText(
            asset?.let {
                parseMoneyInput(it.currentPrice)?.let { value ->
                    formatInputAmount(value, it.currency)
                }
            }.orEmpty()
        )
        notesInput.setText(asset?.notes.orEmpty())
        setupSpinner(
            categorySpinner,
            listOf(SELECT_CATEGORY) + assetCategories,
            asset?.category ?: SELECT_CATEGORY
        )
        setupSpinner(currencySpinner, listOf("IDR", "USD"), asset?.currency ?: "IDR")

        investedInput.apply {
            isFocusable = false
            isFocusableInTouchMode = false
            isCursorVisible = false
            isLongClickable = false
            setOnClickListener(null)
        }
        val moneyInputs = listOf(averagePriceInput, currentPriceInput)
        fun selectedCurrency(): String = currencySpinner.selectedItem?.toString() ?: "IDR"
        fun updateInvestedAmount() {
            val quantity = parseTransactionQuantity(quantityInput.text.toString())
            val averagePrice = parseMoneyInput(averagePriceInput.text.toString())
            val investedAmount = if (quantity != null && averagePrice != null) {
                quantity * averagePrice
            } else {
                null
            }
            investedInput.setText(
                investedAmount?.takeIf { it > 0.0 }?.let {
                    formatInputAmount(it, selectedCurrency())
                } ?: ""
            )
        }
        moneyInputs.forEach { input ->
            installMoneyInputFormatter(input) { selectedCurrency() }
            reformatMoneyInput(input, selectedCurrency())
        }
        installDecimalInputFormatter(quantityInput)
        currencySpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(
                parent: AdapterView<*>?,
                view: View?,
                position: Int,
                id: Long
            ) {
                moneyInputs.forEach { input ->
                    reformatMoneyInput(input, selectedCurrency())
                }
                updateInvestedAmount()
            }

            override fun onNothingSelected(parent: AdapterView<*>?) = Unit
        }
        listOf(quantityInput, averagePriceInput).forEach { input ->
            input.addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(
                    s: CharSequence?,
                    start: Int,
                    count: Int,
                    after: Int
                ) = Unit

                override fun onTextChanged(
                    s: CharSequence?,
                    start: Int,
                    before: Int,
                    count: Int
                ) = Unit

                override fun afterTextChanged(s: Editable?) = updateInvestedAmount()
            })
        }
        updateInvestedAmount()

        fun updateQuantityUnitHint() {
            quantityUnitHintView.text = quantityUnitHint(
                categorySpinner.selectedItem?.toString().orEmpty(),
                symbolInput.text.toString()
            )
            val unitSuffix = priceUnitSuffix(
                categorySpinner.selectedItem?.toString().orEmpty(),
                symbolInput.text.toString()
            )
            averagePriceUnit.text = unitSuffix
            currentPriceUnit.text = unitSuffix
            quantityUnitHintView.visibility = View.VISIBLE
            val hasAssetIdentity = nameInput.text.toString().trim().isNotEmpty() &&
                symbolInput.text.toString().trim().isNotEmpty()
            assetFieldsContainer.visibility =
                if (categorySpinner.selectedItem?.toString() != SELECT_CATEGORY && hasAssetIdentity) {
                    View.VISIBLE
                } else View.GONE
        }

        categorySpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(
                parent: AdapterView<*>?,
                view: View?,
                position: Int,
                id: Long
            ) = updateQuantityUnitHint()

            override fun onNothingSelected(parent: AdapterView<*>?) = updateQuantityUnitHint()
        }
        symbolInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) =
                updateQuantityUnitHint()

            override fun afterTextChanged(s: Editable?) = Unit
        })
        nameInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) =
                updateQuantityUnitHint()

            override fun afterTextChanged(s: Editable?) = Unit
        })
        updateQuantityUnitHint()
    }

    private fun renderReports() {
        contentContainer.removeAllViews()
        val root = inflate(R.layout.screen_reports)
        attach(root)
        root.findViewById<ImageView>(R.id.reports_back)
            .setOnClickListener { showScreen(AppScreen.HOME) }

        val usdExchangeRate = exchangeRateFor("USD")
        val displayAssets = databaseAssets.map { asset ->
            asset.toUiAsset().withTransactionHistory(
                databaseTransactions.filter { it.assetId == asset.id }
            ).toIdrDisplay(usdExchangeRate)
        }
        val totalValue = displayAssets.sumOf { parseMoneyInput(it.value) ?: 0.0 }
        val totalInvested = displayAssets.sumOf { parseMoneyInput(it.invested) ?: 0.0 }
        val totalProfit = totalValue - totalInvested
        val totalProfitPercentage = if (totalInvested == 0.0) 0.0 else {
            totalProfit * 100.0 / totalInvested
        }
        val displayCurrency = "IDR"

        root.findViewById<TextView>(R.id.reports_invested_value).text =
            formatAmount(totalInvested, displayCurrency, 0)
        root.findViewById<TextView>(R.id.reports_current_value).text =
            formatAmount(totalValue, displayCurrency, 0)
        root.findViewById<TextView>(R.id.reports_profit_value).apply {
            text = formatSignedAmount(totalProfit, displayCurrency, 0)
            setTextColor(
                ContextCompat.getColor(
                    this@MainActivity,
                    if (totalProfit >= 0) R.color.investa_mint else R.color.investa_loss
                )
            )
        }
        root.findViewById<TextView>(R.id.reports_profit_percent).apply {
            text = String.format(Locale.US, "%+.2f%%", totalProfitPercentage)
            setTextColor(
                ContextCompat.getColor(
                    this@MainActivity,
                    if (totalProfit >= 0) R.color.investa_mint else R.color.investa_loss
                )
            )
        }
        val performance = performanceSnapshots(
            databaseAssets,
            databaseTransactions,
            usdExchangeRate
        )
        root.findViewById<PerformanceChartView>(R.id.performance_chart)
            .setPerformanceData(performance.first, performance.second)

        val monthLabels = root.findViewById<LinearLayout>(R.id.performance_month_labels)
        val monthFormat = SimpleDateFormat("MMM", Locale.ENGLISH)
        val currentMonth = Calendar.getInstance()
        currentMonth.set(Calendar.DAY_OF_MONTH, 1)
        repeat(6) { index ->
            val month = currentMonth.clone() as Calendar
            month.add(Calendar.MONTH, index - 5)
            monthLabels.addView(
                TextView(this).apply {
                    layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                    gravity = android.view.Gravity.CENTER
                    text = monthFormat.format(month.time)
                    setTextColor(ContextCompat.getColor(
                        this@MainActivity,
                        R.color.investa_icon_inactive
                    ))
                    textSize = 11f
                }
            )
        }
        val summary = root.findViewById<LinearLayout>(R.id.category_summary)

        fun renderSummary(byAsset: Boolean) {
            summary.removeAllViews()
            if (byAsset) {
                displayAssets.forEach { asset ->
                    val value = parseMoneyInput(asset.value) ?: 0.0
                    val percentage = if (totalValue == 0.0) 0 else {
                        ((value * 100.0) / totalValue).roundToInt()
                    }
                    addReportSummaryRow(
                        summary,
                        asset.symbol,
                        formatAmount(value, displayCurrency, 0),
                        "$percentage%"
                    )
                }
            } else {
                assetCategories.forEach { category ->
                    val value = displayAssets
                        .filter { it.category == category }
                        .sumOf { parseMoneyInput(it.value) ?: 0.0 }
                    val percentage = if (totalValue == 0.0) 0 else {
                        ((value * 100.0) / totalValue).roundToInt()
                    }
                    addReportSummaryRow(
                        summary,
                        category,
                        formatAmount(value, displayCurrency, 0),
                        "$percentage%"
                    )
                }
            }
        }

        renderSummary(false)
        val categoryToggle = root.findViewById<TextView>(R.id.toggle_category)
        val assetToggle = root.findViewById<TextView>(R.id.toggle_asset)
        categoryToggle.setOnClickListener {
            setReportToggle(categoryToggle, assetToggle, true)
            renderSummary(false)
        }
        assetToggle.setOnClickListener {
            setReportToggle(categoryToggle, assetToggle, false)
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
                var costBasis = asset.investedAmount.toDouble()
                transactions
                    .asSequence()
                    .filter {
                        it.assetId == asset.id &&
                            it.date >= asset.createdAt &&
                            it.date <= monthEnd
                    }
                    .sortedWith(compareBy<TransactionEntity> { it.date }.thenBy { it.id })
                    .forEach { transaction ->
                        if (transaction.action == "BUY") {
                            quantity += transaction.quantity
                            costBasis += transaction.quantity * transaction.price + transaction.fee
                        } else if (transaction.action == "SELL") {
                            val averageCost = if (quantity > 0.0) costBasis / quantity else 0.0
                            quantity = (quantity - transaction.quantity).coerceAtLeast(0.0)
                            costBasis =
                                (costBasis - averageCost * transaction.quantity).coerceAtLeast(0.0)
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

    private fun addReportSummaryRow(
        summary: ViewGroup,
        label: String,
        amount: String,
        percentage: String
    ) {
        val row = LayoutInflater.from(this)
            .inflate(R.layout.view_category_summary_row, summary, false)
        row.findViewById<TextView>(R.id.summary_category).text = label
        row.findViewById<TextView>(R.id.summary_amount).text = amount
        row.findViewById<TextView>(R.id.summary_percentage).text = percentage
        summary.addView(row)
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
            R.drawable.ic_lucide_circle_dollar,
            "Exchange Rate",
            "USD / IDR",
            true
        ) { showScreen(AppScreen.EXCHANGE_RATE) }
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

    private fun renderExchangeRate() {
        val root = inflate(R.layout.screen_exchange_rate)
        attach(root)
        val exchangeRateInput = root.findViewById<EditText>(R.id.exchange_rate_input)
        val usd = databaseCurrencies.firstOrNull { it.code == "USD" }
            ?: CurrencyEntity(
                code = "USD",
                name = "US Dollar",
                symbol = "$",
                exchangeRate = 16500.0,
                updatedAt = 0L,
                isActive = true
            )
        exchangeRateInput.setText(formatInputAmount(usd.exchangeRate, "IDR"))
        exchangeRateInput.also { input ->
            installMoneyInputFormatter(input) { "IDR" }
        }
        root.findViewById<View>(R.id.exchange_rate_save).setOnClickListener {
            val exchangeRate = parseMoneyInput(exchangeRateInput.text.toString())
            if (exchangeRate == null || exchangeRate <= 0.0) {
                exchangeRateInput.error = "Enter a valid exchange rate"
                exchangeRateInput.requestFocus()
                return@setOnClickListener
            }
            val updatedUsd = usd.copy(
                exchangeRate = exchangeRate,
                updatedAt = System.currentTimeMillis()
            )
            currencyViewModel.update(updatedUsd) {
                databaseCurrencies = databaseCurrencies
                    .filterNot { it.code == updatedUsd.code } + updatedUsd
                Toast.makeText(this, "Exchange rate saved", Toast.LENGTH_SHORT).show()
            }
        }
        root.findViewById<View>(R.id.exchange_rate_back)
            .setOnClickListener { showScreen(AppScreen.SETTINGS) }
    }

    private fun addSettingsRow(
        parent: ViewGroup,
        iconRes: Int,
        label: String,
        value: String,
        chevron: Boolean,
        onClick: (() -> Unit)? = null
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
        row.setOnClickListener { onClick?.invoke() }
        row.isClickable = onClick != null
        parent.addView(row)
    }

    private fun addAssetRow(
        parent: ViewGroup,
        asset: Asset,
        compact: Boolean,
        percentage: String? = null,
        onClick: (() -> Unit)? = null
    ) {
        val row = LayoutInflater.from(this).inflate(R.layout.asset_item, parent, false)
        bindAsset(row, asset, compact, percentage)
        if (onClick != null) {
            row.setOnClickListener { onClick() }
        } else {
            row.setOnClickListener(null)
        }
        row.isClickable = onClick != null
        parent.addView(row)
    }

    private fun bindAsset(row: View, asset: Asset, compact: Boolean, percentage: String? = null) {
        row.findViewById<TextView>(R.id.asset_name).text = asset.name
        row.findViewById<TextView>(R.id.asset_symbol).text = asset.symbol
        row.findViewById<TextView>(R.id.asset_category).text = asset.category
        row.findViewById<TextView>(R.id.asset_quantity).text = asset.quantity
        row.findViewById<TextView>(R.id.asset_value).text = asset.value
        row.findViewById<TextView>(R.id.asset_profit).text = percentage ?: asset.profitPercent
        row.findViewById<View>(R.id.asset_summary_container).visibility =
            if (compact) View.VISIBLE else View.GONE
        row.findViewById<ImageView>(R.id.asset_chevron).visibility =
            if (compact) View.GONE else View.VISIBLE
        if (compact) {
            row.findViewById<TextView>(R.id.asset_category).visibility =
                View.GONE; row.findViewById<TextView>(R.id.asset_quantity).visibility = View.GONE
        }
    }

    private fun bindLegendRows(
        legend: ViewGroup,
        values: List<Pair<String, Int>>
    ) {
        val rows = (0 until legend.childCount).map { legend.getChildAt(it) }
        rows.forEachIndexed { index, row ->
            if (index < values.size) {
                val (label, percent) = values[index]
                row.findViewById<TextView>(R.id.legend_label).text = label
                row.findViewById<TextView>(R.id.legend_percent).text = "${percent}%"
                tint(row.findViewById(R.id.legend_dot), chartColor(index))
            }
        }
    }

    private fun setupSpinner(spinner: Spinner, values: List<String>, selected: String) {
        val adapter = ArrayAdapter(
            this,
            R.layout.spinner_item,
            values
        ).also { it.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item) }
        spinner.adapter = adapter
        spinner.setSelection(values.indexOf(selected).coerceAtLeast(0))
    }

    private fun quantityUnitHint(category: String, symbol: String): String = when (category) {
        "Crypto" -> symbol.trim().ifEmpty { "[symbol]" }
        "ID Stocks", "US Stocks" -> "share(s)"
        "Mutual Fund", "Bonds" -> "Unit(s)"
        "Gold" -> "gr"
        else -> ""
    }

    private fun priceUnitSuffix(category: String, symbol: String): String = when (category) {
        "Crypto" -> "per ${symbol.trim().ifEmpty { "[symbol]" }}"
        "ID Stocks", "US Stocks" -> "per share"
        "Mutual Fund", "Bonds" -> "per unit"
        "Gold" -> "per gram"
        else -> ""
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

private fun AssetEntity.toUiAsset(): Asset {
    val currentValueAmount = quantity * currentPrice
    val profitAmount = quantity * (currentPrice - averagePrice)
    val profitPercentage = if (averagePrice == 0.0) 0.0 else {
        (currentPrice - averagePrice) * 100.0 / averagePrice
    }
    val currentValue = formatAmount(currentValueAmount, currency)
    val investedValue = formatAmount(investedAmount, currency)
    val quantityText = "${BigDecimal.valueOf(quantity).stripTrailingZeros().toPlainString()} " +
        when (category) {
            "Crypto" -> symbol
            "ID Stocks", "US Stocks" -> "share(s)"
            "Mutual Fund", "Bonds" -> "Unit(s)"
            "Gold" -> "gr"
            else -> "unit(s)"
        }
    return Asset(
        name = name,
        symbol = symbol,
        category = category,
        quantity = quantityText,
        value = currentValue,
        invested = investedValue,
        profit = formatSignedAmount(profitAmount, currency),
        profitPercent = String.format(Locale.US, "%+.2f%%", profitPercentage),
        averagePrice = formatAmount(averagePrice, currency),
        currentPrice = formatAmount(currentPrice, currency),
        notes = notes,
        addedOn = SimpleDateFormat("dd MMM yyyy", Locale.ENGLISH).format(Date(createdAt)),
        id = id,
        currency = currency
    )
}

private fun Asset.toIdrDisplay(usdExchangeRate: Double): Asset {
    val multiplier = if (currency == "USD") usdExchangeRate else 1.0
    fun convertAmount(value: String): String? = parseMoneyInput(value)?.let {
        formatAmount(it * multiplier, "IDR")
    }
    fun convertSignedAmount(value: String): String? = parseMoneyInput(value)?.let { amount ->
        val signedAmount = if (value.trimStart().startsWith("-")) -amount else amount
        formatSignedAmount(signedAmount * multiplier, "IDR")
    }
    return copy(
        value = convertAmount(value) ?: value,
        invested = convertAmount(invested) ?: invested,
        profit = convertSignedAmount(profit) ?: profit,
        averagePrice = convertAmount(averagePrice) ?: averagePrice,
        currentPrice = convertAmount(currentPrice) ?: currentPrice,
        currency = "IDR"
    )
}

private fun Asset.withTransactionHistory(
    transactions: List<TransactionEntity>,
    maxFractionDigits: Int = 8,
    currencySymbol: String? = null
): Asset {
    if (transactions.isEmpty()) return this

    val displayCurrencySymbol = currencySymbol ?: currencySymbolFor(currency)

    var holdingQuantity = parseTransactionQuantity(quantity) ?: 0.0
    var costBasis = parseMoneyInput(invested) ?: 0.0
    val fallbackAveragePrice = parseMoneyInput(averagePrice) ?: 0.0
    if (costBasis == 0.0 && fallbackAveragePrice > 0.0) {
        costBasis = holdingQuantity * fallbackAveragePrice
    }

    transactions.sortedWith(compareBy<TransactionEntity> { it.date }.thenBy { it.id })
        .forEach { transaction ->
            if (transaction.action == "BUY") {
                holdingQuantity += transaction.quantity
                costBasis += transaction.quantity * transaction.price + transaction.fee
            } else if (transaction.action == "SELL") {
                val averageCost = if (holdingQuantity > 0.0) {
                    costBasis / holdingQuantity
                } else {
                    0.0
                }
                holdingQuantity = (holdingQuantity - transaction.quantity).coerceAtLeast(0.0)
                costBasis = (costBasis - averageCost * transaction.quantity).coerceAtLeast(0.0)
            }
        }

    val currentPriceAmount = parseMoneyInput(currentPrice) ?: 0.0
    val currentValueAmount = holdingQuantity * currentPriceAmount
    val costBasisAmount = costBasis
    val profitAmount = currentValueAmount - costBasisAmount
    val profitPercentage = if (costBasisAmount == 0.0) 0.0 else {
        profitAmount * 100.0 / costBasisAmount
    }
    val unit = quantity.substringAfter(" ", symbol)

    return copy(
        quantity = formatQuantityWithUnit(holdingQuantity, unit),
        value = formatAmount(currentValueAmount, currency, displayCurrencySymbol, maxFractionDigits),
        invested = formatAmount(costBasisAmount, currency, displayCurrencySymbol, maxFractionDigits),
        profit = formatSignedAmount(profitAmount, currency, displayCurrencySymbol, maxFractionDigits),
        profitPercent = String.format(Locale.US, "%+.2f%%", profitPercentage),
        averagePrice = formatAmount(
            if (holdingQuantity > 0.0) costBasis / holdingQuantity else 0.0,
            currency,
            displayCurrencySymbol,
            maxFractionDigits
        )
    )
}

private fun Asset.withCalculatedCurrentValue(): Asset {
    val investedAmount = parseMoneyInput(invested)
    val averagePriceAmount = parseMoneyInput(averagePrice)
    val currentPriceAmount = parseMoneyInput(currentPrice)
    if (investedAmount == null || averagePriceAmount == null || averagePriceAmount <= 0.0 ||
        currentPriceAmount == null
    ) {
        return this
    }

    val currentValueAmount =
        investedAmount * currentPriceAmount / averagePriceAmount
    val profitAmount = currentValueAmount - investedAmount
    val profitPercentage = if (investedAmount == 0.0) 0.0 else {
        profitAmount * 100.0 / investedAmount
    }
    return copy(
        value = formatAmount(currentValueAmount, currency),
        profit = formatSignedAmount(profitAmount, currency),
        profitPercent = String.format(Locale.US, "%+.2f%%", profitPercentage)
    )
}

private fun parseTransactionQuantity(value: String): Double? =
    parseMoneyInput(value.substringBefore(" "))

private fun parseMoneyInput(value: String): Double? {
    val source = value.trim()
    val currency = if (source.contains('$') || source.startsWith("USD", ignoreCase = true)) {
        "USD"
    } else {
        "IDR"
    }
    val parts = splitEditableAmount(source, currency) ?: return null
    val normalized = parts.first + (parts.second?.let { ".${it}" } ?: "")
    return normalized.toDoubleOrNull()
}

private fun Asset.withAmountPrecision(
    maxFractionDigits: Int,
    currencySymbol: String? = null
): Asset {
    val displayCurrencySymbol = currencySymbol ?: currencySymbolFor(currency)
    val signedProfit = parseMoneyInput(profit)?.let { amount ->
        if (profit.trimStart().startsWith("-")) -amount else amount
    }
    return copy(
        value = parseMoneyInput(value)?.let {
            formatAmount(it, currency, displayCurrencySymbol, maxFractionDigits)
        } ?: value,
        invested = parseMoneyInput(invested)?.let {
            formatAmount(it, currency, displayCurrencySymbol, maxFractionDigits)
        } ?: invested,
        profit = signedProfit?.let {
            formatSignedAmount(it, currency, displayCurrencySymbol, maxFractionDigits)
        } ?: profit,
        averagePrice = parseMoneyInput(averagePrice)?.let {
            formatAmount(it, currency, displayCurrencySymbol, maxFractionDigits)
        } ?: averagePrice,
        currentPrice = parseMoneyInput(currentPrice)?.let {
            formatAmount(it, currency, displayCurrencySymbol, maxFractionDigits)
        } ?: currentPrice
    )
}

private fun splitEditableAmount(
    value: String,
    currency: String,
    decimalMode: Boolean? = null
): Pair<String, String?>? {
    val numeric = value.filter { it.isDigit() || it == '.' || it == ',' }
    if (numeric.isEmpty()) return null

    val primarySeparator = ','
    val alternateSeparator = '.'
    val primaryIndex = numeric.lastIndexOf(primarySeparator)
    val alternateIndex = numeric.lastIndexOf(alternateSeparator)
    val decimalIndex = if (decimalMode != null) {
        if (!decimalMode) {
            -1
        } else if (primaryIndex >= 0) {
            primaryIndex
        } else if (
            alternateIndex >= 0 &&
            (numeric.startsWith("0$alternateSeparator") ||
                numeric.endsWith(alternateSeparator) ||
                numeric.substringAfterLast(alternateSeparator).length != 3)
        ) {
            alternateIndex
        } else {
            -1
        }
    } else {
        when {
            primaryIndex >= 0 && alternateIndex >= 0 -> maxOf(primaryIndex, alternateIndex)
            primaryIndex >= 0 -> primaryIndex
            alternateIndex >= 0 && numeric.startsWith("0$alternateSeparator") -> alternateIndex
            alternateIndex >= 0 && numeric.count { it == alternateSeparator } == 1 &&
                numeric.substringAfterLast(alternateSeparator).length != 3 -> alternateIndex
            else -> -1
        }
    }

    return if (decimalIndex >= 0) {
        numeric.substring(0, decimalIndex).filter(Char::isDigit).ifEmpty { "0" } to
            numeric.substring(decimalIndex + 1).filter(Char::isDigit)
    } else {
        numeric.filter(Char::isDigit) to null
    }
}

private fun formatEditableAmount(
    value: String,
    currency: String,
    decimalMode: Boolean? = null
): String {
    val parts = splitEditableAmount(value, currency, decimalMode) ?: return ""
    val integerFormatter = NumberFormat.getIntegerInstance(Locale.GERMANY)
    val integer = integerFormatter.format(BigDecimal(parts.first))
    return integer + (parts.second?.let { ",$it" } ?: "")
}

private fun installMoneyInputFormatter(
    input: EditText,
    currencyProvider: () -> String
) = installNumericInputFormatter(input, currencyProvider, true)

private fun installDecimalInputFormatter(input: EditText) =
    installNumericInputFormatter(input, { "IDR" }, false)

private fun installNumericInputFormatter(
    input: EditText,
    currencyProvider: () -> String,
    includeCurrencyPrefix: Boolean
) {
    input.keyListener = DigitsKeyListener.getInstance("0123456789.,")
    var isFormatting = false
    var decimalMode = input.text.toString().let { current ->
        current.contains(',')
    }
    input.addTextChangedListener(object : TextWatcher {
        override fun beforeTextChanged(
            s: CharSequence?,
            start: Int,
            count: Int,
            after: Int
        ) = Unit

        override fun onTextChanged(
            s: CharSequence?,
            start: Int,
            before: Int,
            count: Int
        ) {
            if (isFormatting) return
            val insertedText = s?.toString()?.substring(start, (start + count).coerceAtMost(s.length))
                .orEmpty()
            val insertedDecimalSeparator = insertedText.contains('.') || insertedText.contains(',')
            if (insertedDecimalSeparator) decimalMode = true
            else if (s != null && !s.toString().contains(',')) decimalMode = false
            if (s?.none(Char::isDigit) != false) decimalMode = false
        }

        override fun afterTextChanged(editable: Editable?) {
            if (isFormatting) return
            val source = editable?.toString().orEmpty()
            if (source.trimEnd().endsWith('.') || source.trimEnd().endsWith(',')) {
                decimalMode = true
            }
            val formattedBody = formatEditableAmount(
                source,
                currencyProvider(),
                decimalMode
            )
            val formattedWithSeparator = if (includeCurrencyPrefix && formattedBody.isNotEmpty()) {
                if (currencyProvider() == "IDR") "Rp $formattedBody" else "$ $formattedBody"
            } else {
                formattedBody
            }
            if (source != formattedWithSeparator) {
                isFormatting = true
                input.setText(formattedWithSeparator)
                input.setSelection(formattedWithSeparator.length)
                isFormatting = false
            }
        }
    })
}

private fun reformatMoneyInput(input: EditText, currency: String) {
    val amount = parseMoneyInput(input.text.toString()) ?: return
    val formatted = formatInputAmount(amount, currency)
    if (input.text.toString() != formatted) {
        input.setText(formatted)
        input.setSelection(formatted.length)
    }
}

private fun formatInputAmount(amount: Double, currency: String): String {
    val locale = Locale.GERMANY
    val formatter = NumberFormat.getNumberInstance(locale).apply {
        minimumFractionDigits = 0
        maximumFractionDigits = 8
    }
    val formatted = formatter.format(amount)
    return if (currency == "IDR") "Rp $formatted" else "\$ $formatted"
}

private fun parseTransactionDate(value: String): Long = runCatching {
    SimpleDateFormat("dd MMMM yyyy", Locale.ENGLISH).apply {
        isLenient = false
    }.parse(value)?.time ?: System.currentTimeMillis()
}.getOrDefault(System.currentTimeMillis())

private fun formatTransactionDate(value: Long): String =
    SimpleDateFormat("dd MMMM yyyy", Locale.ENGLISH).format(Date(value))

private fun formatTransactionQuantity(quantity: Double, symbol: String): String =
    formatQuantityWithUnit(quantity, symbol)

private fun formatQuantityValue(quantity: Double): String =
    BigDecimal.valueOf(quantity).stripTrailingZeros().toPlainString()

private fun formatQuantityWithUnit(quantity: Double, unit: String): String =
    "${formatQuantityValue(quantity)} $unit"

private fun calculateTransactionTotal(
    isBuy: Boolean,
    quantity: Double,
    price: Double,
    fee: Double
): Double {
    val gross = quantity * price
    return if (isBuy) gross + fee else (gross - fee).coerceAtLeast(0.0)
}

private fun formatAmount(
    amount: Double,
    currency: String,
    maxFractionDigits: Int = 8
): String = formatAmount(amount, currency, currencySymbolFor(currency), maxFractionDigits)

private fun formatAmount(
    amount: Double,
    currency: String,
    currencySymbol: String,
    maxFractionDigits: Int = 8
): String {
    val locale = Locale.GERMANY
    val formatter = NumberFormat.getNumberInstance(locale).apply {
        minimumFractionDigits = 0
        maximumFractionDigits = maxFractionDigits
    }
    val formatted = formatter.format(amount)
    val prefix = currencySymbol.ifBlank { currencySymbolFor(currency) }
    return "$prefix $formatted"
}

private fun formatSignedAmount(
    amount: Double,
    currency: String,
    maxFractionDigits: Int = 8
): String = formatSignedAmount(amount, currency, currencySymbolFor(currency), maxFractionDigits)

private fun formatSignedAmount(
    amount: Double,
    currency: String,
    currencySymbol: String,
    maxFractionDigits: Int = 8
): String {
    val sign = when {
        amount > 0 -> "+"
        amount < 0 -> "-"
        else -> ""
    }
    return sign + formatAmount(abs(amount), currency, currencySymbol, maxFractionDigits)
}

private fun currencySymbolFor(currency: String): String = when (currency) {
    "USD" -> "$"
    else -> "Rp"
}
