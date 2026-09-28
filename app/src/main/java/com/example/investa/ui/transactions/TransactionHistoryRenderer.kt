package com.example.investa.ui.transactions

import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
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
    companion object {
        private const val KEYBOARD_DISMISS_DELAY_MS = 220L
        // Let the page transition finish before inflating the first batch of
        // transaction cards on the main thread.
        private const val HISTORY_LOAD_DELAY_MS = 260L
    }

    private var root: View? = null
    private var historyController: TransactionHistoryListController? = null
    private var isOpeningTransactionDetail = false
    private var initialLoadPending = false
    private var initialLoadRequestId = 0
    private var isSearchListenerInstalled = false

    fun invalidateThemeCache() {
        cancelPendingInitialLoad()
        historyController?.cancel()
        historyController = null
        root = null
        isSearchListenerInstalled = false
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
                    if (!isOpeningTransactionDetail) {
                        isOpeningTransactionDetail = true
                        val openTransactionDetail = {
                            isOpeningTransactionDetail = false
                            if (host.currentScreen == AppScreen.TRANSACTION_HISTORY) {
                                transactionHandler.showTransactionDrawer(
                                    asset,
                                    transaction.action.equals("BUY", ignoreCase = true),
                                    transaction
                                )
                            }
                        }
                        val isKeyboardVisible = ViewCompat.getRootWindowInsets(searchInput)
                            ?.isVisible(WindowInsetsCompat.Type.ime()) == true
                        if (isKeyboardVisible) {
                            searchInput.hideInvestaKeyboard()
                            ViewCompat.getWindowInsetsController(searchInput)
                                ?.hide(WindowInsetsCompat.Type.ime())
                            screenRoot.postDelayed(openTransactionDetail, KEYBOARD_DISMISS_DELAY_MS)
                        } else {
                            openTransactionDetail()
                        }
                    }
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
        if (!isSearchListenerInstalled) {
            searchInput.doAfterTextChanged { query ->
                clearSearch.visibility = if (query.isNullOrEmpty()) View.GONE else View.VISIBLE
                cancelPendingInitialLoad()
                historyController?.setSearchQuery(query?.toString().orEmpty())
            }
            isSearchListenerInstalled = true
        }
        val initialQuery = searchInput.text?.toString().orEmpty()
        if (initialQuery.isBlank() && historyController?.hasLoadedContent() != true) {
            scheduleInitialLoad(screenRoot)
        } else {
            cancelPendingInitialLoad()
            historyController?.setSearchQuery(initialQuery)
        }
    }

    fun refresh() {
        if (root == null) return
        if (host.currentScreen != AppScreen.TRANSACTION_HISTORY) return
        if (initialLoadPending) return
        historyController?.refresh()
    }

    private fun scheduleInitialLoad(screenRoot: View) {
        if (initialLoadPending) return
        initialLoadPending = true
        val requestId = ++initialLoadRequestId
        screenRoot.postDelayed({
            if (requestId != initialLoadRequestId) return@postDelayed
            initialLoadPending = false
            if (screenRoot.parent != null && host.currentScreen == AppScreen.TRANSACTION_HISTORY) {
                historyController?.refresh()
            }
        }, HISTORY_LOAD_DELAY_MS)
    }

    private fun cancelPendingInitialLoad() {
        initialLoadPending = false
        initialLoadRequestId++
    }
}
