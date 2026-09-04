package com.example.investa.ui.assets

import android.app.AlertDialog
import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.PopupMenu
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.example.investa.data.entity.AssetEntity
import com.example.investa.data.entity.TransactionEntity
import com.example.investa.R
import com.example.investa.model.Asset
import com.example.investa.navigation.AppScreen
import com.example.investa.navigation.ScreenHost
import com.example.investa.ui.transactions.TransactionHandler
import com.example.investa.utils.currencySymbolFor
import com.example.investa.utils.formatAmount
import com.example.investa.utils.formatInputAmount
import com.example.investa.utils.formatSignedAmount
import com.example.investa.utils.formatTransactionDate
import com.example.investa.utils.formatAssetDate
import com.example.investa.utils.priceUnitSuffix
import com.example.investa.utils.parseMoneyInput
import com.example.investa.utils.showInvestaToast
import com.example.investa.utils.toUiAsset
import com.example.investa.utils.withAmountPrecision
import com.example.investa.utils.YahooFinanceApi
import com.example.investa.utils.localizedCategory
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import kotlinx.coroutines.Job
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

internal class AssetDetailRenderer(
    private val host: ScreenHost,
    private val transactionHandler: TransactionHandler
) {
    private var transactionObservation: Job? = null
    private var observedTransactionAssetId: Long? = null
    private var currentTransactions: List<TransactionEntity> = emptyList()

    fun render() {
        host.contentContainer.removeAllViews()
        val root = host.inflate(R.layout.screen_asset_detail)
        host.attach(root)
        val asset = host.selectedAsset ?: run {
            host.showScreen(AppScreen.ASSETS)
            return
        }
        observeTransactions(asset)
        val currencySymbol = host.databaseCurrencies
            .firstOrNull { it.code == asset.currency }
            ?.symbol
            ?.takeIf { it.isNotBlank() }
            ?: currencySymbolFor(asset.currency)
        val displayAsset = asset.withAmountPrecision(2, currencySymbol).copy(
            addedOn = formatAssetDate(
                host.activity,
                host.databaseAssets.firstOrNull { it.id == asset.id }?.createdAt ?: System.currentTimeMillis()
            )
        )
        root.findViewById<TextView>(R.id.detail_name).text = displayAsset.name
        root.findViewById<TextView>(R.id.detail_symbol).text = displayAsset.symbol
        root.findViewById<TextView>(R.id.detail_value).text = displayAsset.value
        root.findViewById<TextView>(R.id.detail_profit).text = "${displayAsset.profit}  ${displayAsset.profitPercent}"
        root.findViewById<View>(R.id.detail_back).setOnClickListener { host.showScreen(host.detailOrigin) }
        root.findViewById<View>(R.id.detail_buy).setOnClickListener {
            transactionHandler.showTransactionDrawer(displayAsset, isBuy = true)
        }
        root.findViewById<View>(R.id.detail_sell).setOnClickListener {
            transactionHandler.showTransactionDrawer(displayAsset, isBuy = false)
        }
        root.findViewById<View>(R.id.detail_change_current_price).setOnClickListener {
            showCurrentPriceDrawer(displayAsset)
        }
        root.findViewById<View>(R.id.detail_more).setOnClickListener { showAssetOptions(displayAsset) }
        val rows = listOf(
            host.activity.getString(R.string.quantity) to displayAsset.quantity,
            host.activity.getString(R.string.invested_amount) to displayAsset.invested,
            host.activity.getString(R.string.average_price) to displayAsset.averagePrice,
            host.activity.getString(R.string.current_price) to displayAsset.currentPrice,
            host.activity.getString(R.string.category) to localizedCategory(host.activity, displayAsset.category),
            host.activity.getString(R.string.notes) to displayAsset.notes.ifBlank { "-" },
            host.activity.getString(R.string.added_on) to displayAsset.addedOn
        )
        val info = root.findViewById<LinearLayout>(R.id.detail_info_container)
        rows.forEachIndexed { index, (label, value) ->
            val row = info.getChildAt(index)
            row.findViewById<TextView>(R.id.detail_row_label).text = label
            row.findViewById<TextView>(R.id.detail_row_value).text = value
        }
        populateTransactionHistory(root, displayAsset, currentTransactions, currencySymbol)
    }

    fun refreshSelectedAsset(assets: List<AssetEntity>) {
        val selectedId = host.selectedAsset?.id ?: return
        val refreshedEntity = assets.firstOrNull { it.id == selectedId }
        if (refreshedEntity == null) {
            host.selectedAsset = null
            host.showScreen(AppScreen.ASSETS)
        } else {
            host.selectedAsset = refreshedEntity.toUiAsset(host.activity)
            render()
        }
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
        transactionObservation = host.activity.lifecycleScope.launch {
            host.transactionViewModel.observeTransactions(asset.id).collect { transactions ->
                currentTransactions = transactions
                if (host.currentScreen == AppScreen.DETAIL && host.selectedAsset?.id == asset.id) {
                    render()
                }
            }
        }
    }

    private fun populateTransactionHistory(
        root: View,
        asset: Asset,
        transactions: List<TransactionEntity>,
        currencySymbol: String
    ) {
        val historyContainer = root.findViewById<LinearLayout>(R.id.detail_history_container)
        if (transactions.isEmpty()) {
            val empty = LayoutInflater.from(host.activity)
                .inflate(R.layout.view_empty_state, historyContainer, false)
            empty.findViewById<TextView>(R.id.empty_title).text = host.activity.getString(R.string.no_transactions_found)
            empty.findViewById<TextView>(R.id.empty_message).text = host.activity.getString(R.string.add_buy_sell_transaction)
            historyContainer.addView(empty)
            return
        }
        transactions.forEach { transaction ->
            val action = transaction.action.trim().uppercase()
            val actionLabel = host.activity.getString(
                if (action == "BUY") R.string.buy else R.string.sell
            )
            val card = LayoutInflater.from(host.activity)
                .inflate(R.layout.view_transaction_history_card, historyContainer, false)
            card.findViewById<TextView>(R.id.history_type).apply {
                text = actionLabel
                setTextColor(ContextCompat.getColor(
                    host.activity,
                    if (transaction.action.equals("BUY", ignoreCase = true)) {
                        R.color.investa_buy
                    } else {
                        R.color.investa_loss
                    }
                ))
            }
            card.findViewById<TextView>(R.id.history_date).text = formatTransactionDate(host.activity, transaction.date)
            card.findViewById<TextView>(R.id.history_quantity).text =
                com.example.investa.utils.formatTransactionQuantity(transaction.quantity, asset.symbol)
            card.findViewById<TextView>(R.id.history_price_label).text =
                host.activity.getString(R.string.history_price, actionLabel)
            card.findViewById<TextView>(R.id.history_price).text =
                formatAmount(
                    transaction.price,
                    transaction.currency,
                    host.databaseCurrencies.firstOrNull { it.code == transaction.currency }?.symbol
                        ?: currencySymbolFor(transaction.currency),
                    2
                )
            val realizedRow = card.findViewById<View>(R.id.history_realized_row)
            val realizedValue = card.findViewById<TextView>(R.id.history_realized)
            if (transaction.action.equals("SELL", ignoreCase = true)) {
                val realizedPL = transaction.total - transaction.costBasis
                realizedRow.visibility = View.VISIBLE
                realizedValue.text = formatSignedAmount(
                    realizedPL,
                    transaction.currency,
                    host.databaseCurrencies.firstOrNull { it.code == transaction.currency }?.symbol
                        ?: currencySymbolFor(transaction.currency),
                    2
                )
                realizedValue.setTextColor(
                    ContextCompat.getColor(
                        host.activity,
                        if (realizedPL >= 0.0) R.color.investa_mint else R.color.investa_loss
                    )
                )
            } else {
                realizedRow.visibility = View.GONE
            }
            card.setOnClickListener {
                transactionHandler.showTransactionDrawer(asset, transaction.action == "BUY", transaction)
            }
            historyContainer.addView(card)
        }
    }

    private fun showCurrentPriceDrawer(asset: Asset) {
        if (asset.id == 0L) {
            host.activity.showInvestaToast(host.activity.getString(R.string.asset_not_found))
            return
        }
        val dialog = BottomSheetDialog(host.activity)
        val drawer = host.activity.layoutInflater.inflate(R.layout.bottom_sheet_current_price, null)
        dialog.setContentView(drawer)
        val priceInput = drawer.findViewById<android.widget.EditText>(R.id.current_price_input)
        val priceUnit = drawer.findViewById<TextView>(R.id.current_price_unit)
        val saveButton = drawer.findViewById<View>(R.id.current_price_save)
        val refreshButton = drawer.findViewById<View>(R.id.current_price_refresh)
        val refreshIcon = drawer.findViewById<android.widget.ImageView>(R.id.current_price_refresh_icon)
        val refreshProgress = drawer.findViewById<ProgressBar>(R.id.current_price_refresh_progress)
        var priceRequestJob: Job? = null
        refreshButton.visibility = if (asset.category in setOf("Crypto", "ID Stocks", "US Stocks")) {
            View.VISIBLE
        } else {
            View.GONE
        }
        refreshButton.setOnClickListener {
            setRefreshLoading(refreshButton, refreshIcon, refreshProgress, true)
            priceRequestJob = host.activity.lifecycleScope.launch {
                try {
                    val latestPrice = fetchLatestAssetPrice(asset)
                        val formatted = formatInputAmount(latestPrice, asset.currency)
                        priceInput.setText(formatted)
                        priceInput.setSelection(formatted.length)
                        host.activity.showInvestaToast(host.activity.getString(R.string.latest_current_price_loaded))
                } catch (error: CancellationException) {
                    throw error
                } catch (error: Exception) {
                    host.activity.showInvestaToast(
                        host.activity.getString(R.string.failed_current_price, error.message ?: error.javaClass.simpleName)
                    )
                } finally {
                    setRefreshLoading(refreshButton, refreshIcon, refreshProgress, false)
                }
            }
        }
        priceUnit.text = priceUnitSuffix(host.activity, asset.category, asset.symbol)
        priceInput.setText(parseMoneyInput(asset.currentPrice)?.let { formatInputAmount(it, asset.currency) }.orEmpty())
        com.example.investa.utils.installMoneyInputFormatter(priceInput) { asset.currency }
        saveButton.setOnClickListener {
            val currentPrice = parseMoneyInput(priceInput.text.toString())
            if (currentPrice == null || currentPrice <= 0.0) {
                priceInput.error = host.activity.getString(R.string.valid_current_price)
                priceInput.requestFocus()
                return@setOnClickListener
            }
            val entity = host.databaseAssets.firstOrNull { it.id == asset.id }
            if (entity == null) {
                host.activity.showInvestaToast(host.activity.getString(R.string.asset_not_found))
                return@setOnClickListener
            }
            val updatedEntity = entity.copy(currentPrice = currentPrice, updatedAt = System.currentTimeMillis())
            host.assetViewModel.updateAsset(updatedEntity) {
                host.databaseAssets = host.databaseAssets.map { databaseAsset ->
                    if (databaseAsset.id == updatedEntity.id) updatedEntity else databaseAsset
                }
                host.selectedAsset = updatedEntity.toUiAsset(host.activity)
                dialog.dismiss()
                render()
                host.activity.showInvestaToast(host.activity.getString(R.string.current_price_updated))
            }
        }
        dialog.setOnShowListener {
            val bottomSheet = dialog.findViewById<FrameLayout>(com.google.android.material.R.id.design_bottom_sheet)
            bottomSheet?.setBackgroundColor(Color.TRANSPARENT)
            bottomSheet?.let { BottomSheetBehavior.from(it).state = BottomSheetBehavior.STATE_EXPANDED }
        }
        dialog.setOnDismissListener {
            val request = priceRequestJob
            if (request?.isActive == true) {
                request.cancel()
                host.activity.showInvestaToast(
                    host.activity.getString(R.string.current_price_request_cancelled)
                )
            }
            priceRequestJob = null
        }
        dialog.show()
    }

    private suspend fun fetchLatestAssetPrice(asset: Asset): Double {
        val apiSymbol = when (asset.category) {
            "Crypto" -> asset.symbol.trim().uppercase() + "-USD"
            "ID Stocks" -> {
                val symbol = asset.symbol.trim().uppercase()
                if (symbol.endsWith(".JK")) symbol else "$symbol.JK"
            }
            "US Stocks" -> asset.symbol.trim().uppercase()
            else -> error(host.activity.getString(R.string.yahoo_category_unavailable))
        }
        val quote = YahooFinanceApi.fetchQuote(apiSymbol)
        val quoteCurrency = if (asset.category == "ID Stocks") "IDR" else "USD"
        val usdIdrRate = host.exchangeRateFor("USD")
        return when {
            quoteCurrency == asset.currency -> quote
            quoteCurrency == "USD" && asset.currency == "IDR" -> quote * usdIdrRate
            quoteCurrency == "IDR" && asset.currency == "USD" -> quote / usdIdrRate
            else -> quote
        }
    }

    private fun setRefreshLoading(
        button: View,
        icon: android.widget.ImageView,
        progress: ProgressBar,
        loading: Boolean
    ) {
        button.isEnabled = !loading
        icon.visibility = if (loading) View.GONE else View.VISIBLE
        progress.visibility = if (loading) View.VISIBLE else View.GONE
    }

    private fun showAssetOptions(asset: Asset) {
        PopupMenu(host.activity, host.activity.findViewById(R.id.detail_more)).apply {
            menu.add(host.activity.getString(R.string.edit))
            menu.add(host.activity.getString(R.string.delete))
            setOnMenuItemClickListener { item ->
                when (item.title.toString()) {
                    host.activity.getString(R.string.edit) -> { host.showScreen(AppScreen.EDIT); true }
                    host.activity.getString(R.string.delete) -> { confirmDeleteAsset(asset); true }
                    else -> false
                }
            }
        }.show()
    }

    private fun confirmDeleteAsset(asset: Asset) {
        if (asset.id == 0L) {
            host.activity.showInvestaToast(host.activity.getString(R.string.asset_not_found))
            return
        }
        AlertDialog.Builder(host.activity)
            .setTitle(host.activity.getString(R.string.delete_asset))
            .setMessage(host.activity.getString(R.string.delete_asset_message, asset.name))
            .setNegativeButton(host.activity.getString(R.string.cancel), null)
            .setPositiveButton(host.activity.getString(R.string.delete)) { _, _ ->
                val entity = host.databaseAssets.firstOrNull { it.id == asset.id }
                if (entity != null) {
                    host.assetViewModel.deleteAsset(entity) {
                        host.selectedAsset = null
                        host.showScreen(AppScreen.ASSETS)
                        host.activity.showInvestaToast(host.activity.getString(R.string.asset_deleted))
                    }
                }
            }
            .show()
    }
}
