package com.example.investa.ui.transactions

import android.view.View
import com.example.investa.R
import com.example.investa.navigation.AppScreen
import com.example.investa.navigation.ScreenHost
import com.example.investa.utils.toUiAsset

internal class TransactionHistoryRenderer(
    private val host: ScreenHost,
    private val transactionHandler: TransactionHandler
) {
    private var root: View? = null
    private var historyController: TransactionHistoryListController? = null

    fun render() {
        val screenRoot = root ?: host.inflate(R.layout.screen_transaction_history).also { root = it }
        if (screenRoot.parent == null) host.attach(screenRoot)
        screenRoot.findViewById<View>(R.id.transaction_history_back).setOnClickListener {
            host.showScreen(AppScreen.ASSETS)
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
                loadPage = { limit, offset ->
                    host.transactionViewModel.getTransactionHistoryPage(limit, offset)
                },
                isActive = { host.currentScreen == AppScreen.TRANSACTION_HISTORY }
            )
        }
        refresh()
    }

    fun refresh() {
        if (root == null) return
        if (host.currentScreen != AppScreen.TRANSACTION_HISTORY) return
        historyController?.refresh()
    }
}
