package com.example.investa.ui.transactions

import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import com.example.investa.data.entity.TransactionEntity
import com.example.investa.model.Asset
import com.example.investa.navigation.ScreenHost
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

internal class TransactionHistoryListController(
    private val host: ScreenHost,
    private val scrollView: ScrollView,
    private val historyContainer: LinearLayout,
    private val assetForTransaction: (TransactionEntity) -> Asset?,
    private val onTransactionClick: (Asset, TransactionEntity) -> Unit,
    private val loadPage: suspend (limit: Int, offset: Int) -> List<TransactionEntity>,
    private val isActive: () -> Boolean
) {
    companion object {
        private const val PAGE_SIZE = 10
        private const val LOAD_MORE_THRESHOLD_DP = 96
    }

    private var isLoading = false
    private var hasMorePages = true
    private var loadedCount = 0
    private var loadJob: Job? = null
    private val loadingIndicator = ProgressBar(host.activity).apply {
        isIndeterminate = true
        layoutParams = LinearLayout.LayoutParams(host.dp(28), host.dp(28)).apply {
            gravity = android.view.Gravity.CENTER_HORIZONTAL
            topMargin = host.dp(14)
            bottomMargin = host.dp(14)
        }
    }

    init {
        scrollView.setOnScrollChangeListener { _, _, scrollY, _, _ ->
            val contentBottom = historyContainer.bottom
            val viewportBottom = scrollY + scrollView.height
            val threshold = host.dp(LOAD_MORE_THRESHOLD_DP)
            if (
                !isLoading &&
                viewportBottom >= contentBottom - threshold &&
                hasMorePages
            ) {
                loadNextPage()
            }
        }
    }

    fun refresh() {
        loadJob?.cancel()
        isLoading = false
        hideLoadingIndicator()
        loadedCount = 0
        hasMorePages = true
        loadPage(clearExisting = true)
    }

    fun cancel() {
        loadJob?.cancel()
        loadJob = null
        isLoading = false
        hideLoadingIndicator()
    }

    private fun loadNextPage() = loadPage(clearExisting = false)

    private fun loadPage(clearExisting: Boolean) {
        if (isLoading || !hasMorePages) return
        isLoading = true
        if (clearExisting) historyContainer.removeAllViews()
        showLoadingIndicator()
        loadJob = host.activity.lifecycleScope.launch {
            runCatching { loadPage(PAGE_SIZE + 1, loadedCount) }
                .onSuccess { rows ->
                    if (!isActive()) return@onSuccess
                    val page = rows.take(PAGE_SIZE)
                    hasMorePages = rows.size > PAGE_SIZE
                    hideLoadingIndicator()
                    if (page.isNotEmpty() || clearExisting) {
                        if (clearExisting) {
                            bindTransactionHistoryCards(
                                host = host,
                                historyContainer = historyContainer,
                                transactions = page,
                                assetForTransaction = assetForTransaction,
                                onTransactionClick = onTransactionClick
                            )
                        } else if (page.isNotEmpty()) {
                            bindTransactionHistoryCards(
                                host = host,
                                historyContainer = historyContainer,
                                transactions = page,
                                assetForTransaction = assetForTransaction,
                                onTransactionClick = onTransactionClick,
                                clearExisting = false
                            )
                        }
                    }
                    loadedCount += page.size
                }
                .onFailure {
                    if (isActive()) hideLoadingIndicator()
                }
            isLoading = false
        }
    }

    private fun showLoadingIndicator() {
        if (loadingIndicator.parent == null) {
            historyContainer.addView(loadingIndicator)
        }
    }

    private fun hideLoadingIndicator() {
        if (loadingIndicator.parent === historyContainer) {
            historyContainer.removeView(loadingIndicator)
        }
    }
}
