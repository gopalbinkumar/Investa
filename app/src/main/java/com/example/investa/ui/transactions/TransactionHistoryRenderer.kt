package com.example.investa.ui.transactions

import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import androidx.core.widget.doAfterTextChanged
import com.example.investa.R
import com.example.investa.navigation.AppScreen
import com.example.investa.navigation.ScreenHost
import com.example.investa.utils.hideInvestaKeyboard
import com.example.investa.utils.toUiAsset

internal class TransactionHistoryRenderer(
    private val host: ScreenHost,
    private val transactionHandler: TransactionHandler
) {
    private var root: View? = null
    private var historyController: TransactionHistoryListController? = null

    fun invalidateThemeCache() {
        historyController?.cancel()
        historyController = null
        root = null
    }

    fun render() {
        val screenRoot = root ?: host.inflate(R.layout.screen_transaction_history).also { root = it }
        if (screenRoot.parent == null) host.attach(screenRoot)
        screenRoot.findViewById<View>(R.id.transaction_history_back).setOnClickListener {
            host.showScreen(AppScreen.ASSETS)
        }
        val searchInput = screenRoot.findViewById<EditText>(R.id.transaction_history_search)
        val clearSearch = screenRoot.findViewById<View>(R.id.transaction_history_search_clear)
        clearSearch.visibility = if (searchInput.text.isNullOrEmpty()) View.GONE else View.VISIBLE
        clearSearch.setOnClickListener { searchInput.text?.clear() }
        searchInput.setOnEditorActionListener { view, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                view.hideInvestaKeyboard()
                true
            } else {
                false
            }
        }
        if (historyController == null) {
            historyController = TransactionHistoryListController(
                host = host,
                scrollView = screenRoot.findViewById(R.id.transaction_history_scroll),
                historyContainer = screenRoot.findViewById(R.id.transaction_history_container),
                assetForTransaction = { transaction ->
                    host.databaseAssets
                        .firstOrNull { it.id == transaction.assetId }
                        ?.toUiAsset(host.activity)
                },
                onTransactionClick = { asset, transaction ->
                    transactionHandler.showTransactionDrawer(
                        asset,
                        transaction.action.equals("BUY", ignoreCase = true),
                        transaction
                    )
                },
                loadPage = { query, limit, offset ->
                    host.transactionViewModel.getTransactionHistoryPageMatchingAssets(
                        query,
                        limit,
                        offset
                    )
                },
                isActive = { host.currentScreen == AppScreen.TRANSACTION_HISTORY }
            )
        }
        searchInput.doAfterTextChanged { query ->
            clearSearch.visibility = if (query.isNullOrEmpty()) View.GONE else View.VISIBLE
            historyController?.setSearchQuery(query?.toString().orEmpty())
        }
        val initialQuery = searchInput.text?.toString().orEmpty()
        if (initialQuery.isBlank()) {
            refresh()
        } else {
            historyController?.setSearchQuery(initialQuery)
        }
    }

    fun refresh() {
        if (root == null) return
        if (host.currentScreen != AppScreen.TRANSACTION_HISTORY) return
        historyController?.refresh()
    }
}
