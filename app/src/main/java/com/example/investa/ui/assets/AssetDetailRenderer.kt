package com.example.investa.ui.assets

import android.graphics.Color
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.example.investa.data.entity.AssetEntity
import com.example.investa.R
import com.example.investa.model.Asset
import com.example.investa.navigation.AppScreen
import com.example.investa.navigation.ScreenHost
import com.example.investa.ui.transactions.TransactionHandler
import com.example.investa.ui.transactions.TransactionHistoryListController
import com.example.investa.ui.common.InvestaPopupOption
import com.example.investa.ui.common.disableFontPaddingRecursively
import com.example.investa.ui.common.applyElevatedCards
import com.example.investa.ui.common.showInvestaConfirmationDialog
import com.example.investa.ui.common.showInvestaPopup
import com.example.investa.ui.common.setLoadingState
import com.example.investa.utils.currencySymbolFor
import com.example.investa.utils.formatAmount
import com.example.investa.utils.formatInputAmount
import com.example.investa.utils.formatQuantityForCard
import com.example.investa.utils.formatSignedAmount
import com.example.investa.utils.priceUnitSuffix
import com.example.investa.utils.parseMoneyInput
import com.example.investa.utils.showInvestaToast
import com.example.investa.utils.hideInvestaKeyboard
import com.example.investa.utils.toUiAsset
import com.example.investa.utils.withAmountPrecision
import com.example.investa.utils.enableImeScrolling
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
    companion object {
        // Let the detail screen finish its navigation animation before inflating
        // transaction cards on the main thread.
        private const val HISTORY_LOAD_DELAY_MS = 260L
    }

    private var historyController: TransactionHistoryListController? = null

    fun render() {
        host.contentContainer.removeAllViews()
        val root = host.inflate(R.layout.screen_asset_detail)
        host.attach(root)
        val asset = host.selectedAsset ?: run {
            host.showScreen(AppScreen.ASSETS)
            return
        }
        val currencySymbol = host.databaseCurrencies
            .firstOrNull { it.code == asset.currency }
            ?.symbol
            ?.takeIf { it.isNotBlank() }
            ?: currencySymbolFor(asset.currency)
        val displayAsset = asset.withAmountPrecision(2, currencySymbol)
        root.findViewById<TextView>(R.id.detail_name).text = displayAsset.name
        root.findViewById<TextView>(R.id.detail_symbol).text = displayAsset.symbol
        root.findViewById<TextView>(R.id.detail_value).text = displayAsset.value
        root.findViewById<TextView>(R.id.detail_profit).apply {
            text = "${displayAsset.profit}  ${displayAsset.profitPercent}"
            setTextColor(ContextCompat.getColor(
                host.activity,
                if (displayAsset.profit.trimStart().startsWith("-")) {
                    R.color.investa_loss
                } else {
                    R.color.investa_profit
                }
            ))
        }
        root.findViewById<View>(R.id.detail_back).setOnClickListener { host.showScreen(host.detailOrigin) }
        root.findViewById<View>(R.id.detail_buy).setOnClickListener {
            transactionHandler.showTransactionDrawer(asset, isBuy = true)
        }
        root.findViewById<View>(R.id.detail_sell).setOnClickListener {
            val currentQuantity = host.databaseAssets
                .firstOrNull { it.id == asset.id }
                ?.quantity
                ?: 0.0
            if (currentQuantity <= 0.0) {
                host.activity.showInvestaToast(
                    host.activity.getString(R.string.insufficient_asset_quantity)
                )
            } else {
                transactionHandler.showTransactionDrawer(asset, isBuy = false)
            }
        }
        root.findViewById<View>(R.id.detail_change_current_price).setOnClickListener {
            showCurrentPriceDrawer(asset)
        }
        root.findViewById<View>(R.id.detail_more).setOnClickListener { showAssetOptions(asset) }
        val rows = listOf(
            host.activity.getString(R.string.quantity) to formatQuantityForCard(displayAsset.quantity),
            host.activity.getString(R.string.invested_amount) to displayAsset.invested,
            host.activity.getString(R.string.average_price) to displayAsset.averagePrice,
            host.activity.getString(R.string.current_price) to displayAsset.currentPrice,
            host.activity.getString(R.string.category) to localizedCategory(host.activity, displayAsset.category),
            host.activity.getString(R.string.notes) to displayAsset.notes.ifBlank { "-" }
        )
        val info = root.findViewById<LinearLayout>(R.id.detail_info_container)
        rows.forEachIndexed { index, (label, value) ->
            val row = info.getChildAt(index)
            row.findViewById<TextView>(R.id.detail_row_label).text = label
            row.findViewById<TextView>(R.id.detail_row_value).text = value
        }
        historyController?.cancel()
        historyController = TransactionHistoryListController(
            host = host,
            scrollView = root.findViewById(R.id.detail_scroll),
            historyContainer = root.findViewById(R.id.detail_history_container),
            assetForTransaction = { asset },
            onTransactionClick = { clickedAsset, transaction ->
                transactionHandler.showTransactionDrawer(
                    clickedAsset,
                    transaction.action.equals("BUY", ignoreCase = true),
                    transaction
                )
            },
            loadPage = { _, limit, offset ->
                host.transactionViewModel.getTransactionHistoryPageForAsset(
                    asset.id,
                    limit,
                    offset
                )
            },
            isActive = {
                host.currentScreen == AppScreen.DETAIL && host.selectedAsset?.id == asset.id
            }
        )
        val renderedRoot = root
        val renderedAssetId = asset.id
        renderedRoot.postDelayed({
            if (
                renderedRoot.parent != null &&
                host.currentScreen == AppScreen.DETAIL &&
                host.selectedAsset?.id == renderedAssetId
            ) {
                historyController?.refresh()
            }
        }, HISTORY_LOAD_DELAY_MS)
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

    private fun showCurrentPriceDrawer(asset: Asset) {
        if (asset.id == 0L) {
            host.activity.showInvestaToast(host.activity.getString(R.string.asset_not_found))
            return
        }
        val dialog = BottomSheetDialog(host.activity)
        val drawer = host.activity.layoutInflater.inflate(R.layout.bottom_sheet_current_price, null)
        drawer.disableFontPaddingRecursively()
        applyElevatedCards(drawer)
        drawer.findViewById<ScrollView>(R.id.current_price_content_scroll).enableImeScrolling()
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
            setLoadingState(refreshButton, refreshIcon, refreshProgress, true)
            priceRequestJob = host.activity.lifecycleScope.launch {
                try {
                    val latestPrice = fetchLatestAssetPrice(asset)
                        val formatted = formatInputAmount(latestPrice, asset.currency)
                        priceInput.setText(formatted)
                        priceInput.setSelection(formatted.length)
                        priceInput.hideInvestaKeyboard()
                        host.activity.showInvestaToast(host.activity.getString(R.string.latest_current_price_loaded))
                } catch (error: CancellationException) {
                    throw error
                } catch (error: Exception) {
                    host.activity.showInvestaToast(
                        host.activity.getString(R.string.failed_current_price, error.message ?: error.javaClass.simpleName)
                    )
                } finally {
                    setLoadingState(refreshButton, refreshIcon, refreshProgress, false)
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
            bottomSheet?.let { sheet ->
                sheet.clipChildren = true
                sheet.clipToPadding = true
                BottomSheetBehavior.from(sheet).apply {
                    isDraggable = false
                    state = BottomSheetBehavior.STATE_EXPANDED
                }
            }
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

    private fun showAssetOptions(asset: Asset) {
        val anchor = host.activity.findViewById<View>(R.id.detail_more)
        showInvestaPopup(
            anchor,
            listOf(
                InvestaPopupOption(host.activity.getString(R.string.edit)) {
                    host.showScreen(AppScreen.EDIT)
                },
                InvestaPopupOption(
                    host.activity.getString(R.string.delete),
                    destructive = true
                ) {
                    confirmDeleteAsset(asset)
                }
            )
        )
    }

    private fun confirmDeleteAsset(asset: Asset) {
        if (asset.id == 0L) {
            host.activity.showInvestaToast(host.activity.getString(R.string.asset_not_found))
            return
        }
        showInvestaConfirmationDialog(
            activity = host.activity,
            title = host.activity.getString(R.string.delete_asset),
            message = host.activity.getString(R.string.delete_asset_message, asset.name)
        ) {
            val entity = host.databaseAssets.firstOrNull { it.id == asset.id }
            if (entity != null) {
                host.assetViewModel.deleteAsset(entity) {
                    host.selectedAsset = null
                    host.showScreen(AppScreen.ASSETS)
                    host.activity.showInvestaToast(host.activity.getString(R.string.asset_deleted))
                }
            }
        }
    }
}
