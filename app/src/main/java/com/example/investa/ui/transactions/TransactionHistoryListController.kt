package com.example.investa.ui.transactions

import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import com.example.investa.R
import com.example.investa.data.entity.TransactionEntity
import com.example.investa.model.Asset
import com.example.investa.navigation.ScreenHost
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

internal class TransactionHistoryListController(
    private val host: ScreenHost,
    private val scrollView: ScrollView,
    private val historyContainer: LinearLayout,
    private val assetForTransaction: (TransactionEntity) -> Asset?,
    private val onTransactionClick: (Asset, TransactionEntity) -> Unit,
    private val loadPage: suspend (searchQuery: String, limit: Int, offset: Int) -> List<TransactionEntity>,
    private val isActive: () -> Boolean
) {
    companion object {
        private const val PAGE_SIZE = 10
        private const val LOAD_MORE_THRESHOLD_DP = 96
        private const val SEARCH_DEBOUNCE_MS = 250L
    }

    private var isLoading = false
    private var hasMorePages = true
    private var loadedCount = 0
    private var loadJob: Job? = null
    private var pendingSearchJob: Job? = null
    private var searchQuery = ""
    private var requestVersion = 0
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
        pendingSearchJob?.cancel()
        pendingSearchJob = null
        requestVersion++
        loadJob?.cancel()
        isLoading = false
        hideLoadingIndicator()
        loadedCount = 0
        hasMorePages = true
        loadPage(clearExisting = true)
    }

    fun setSearchQuery(query: String) {
        val normalizedQuery = query.trim()
        if (normalizedQuery == searchQuery) return

        searchQuery = normalizedQuery
        requestVersion++
        val searchVersion = requestVersion
        pendingSearchJob?.cancel()
        pendingSearchJob = null
        loadJob?.cancel()
        loadJob = null
        isLoading = false
        loadedCount = 0
        hasMorePages = true
        hideLoadingIndicator()
        historyContainer.removeAllViews()
        scrollView.scrollTo(0, 0)

        if (normalizedQuery.isEmpty()) {
            loadPage(clearExisting = true)
            return
        }

        isLoading = true
        showLoadingIndicator()
        pendingSearchJob = host.activity.lifecycleScope.launch {
            delay(SEARCH_DEBOUNCE_MS)
            if (searchVersion != requestVersion || !isActive()) return@launch
            hideLoadingIndicator()
            isLoading = false
            loadPage(clearExisting = true)
        }
    }

    fun cancel() {
        requestVersion++
        pendingSearchJob?.cancel()
        pendingSearchJob = null
        loadJob?.cancel()
        loadJob = null
        isLoading = false
        hideLoadingIndicator()
    }

    private fun loadNextPage() = loadPage(clearExisting = false)

    private fun loadPage(clearExisting: Boolean) {
        if (isLoading || !hasMorePages) return
        val pageVersion = requestVersion
        val pageQuery = searchQuery
        isLoading = true
        if (clearExisting) historyContainer.removeAllViews()
        showLoadingIndicator()
        loadJob = host.activity.lifecycleScope.launch {
            try {
                val rows = loadPage(pageQuery, PAGE_SIZE + 1, loadedCount)
                if (pageVersion != requestVersion || !isActive()) return@launch

                val page = rows.take(PAGE_SIZE)
                hasMorePages = rows.size > PAGE_SIZE
                hideLoadingIndicator()
                if (page.isNotEmpty() || clearExisting) {
                    bindTransactionHistoryCards(
                        host = host,
                        historyContainer = historyContainer,
                        transactions = page,
                        assetForTransaction = assetForTransaction,
                        onTransactionClick = onTransactionClick,
                        clearExisting = clearExisting,
                        emptyTitleRes = if (pageQuery.isBlank()) {
                            R.string.no_transactions_found
                        } else {
                            R.string.no_matching_transactions
                        },
                        emptyMessageRes = if (pageQuery.isBlank()) {
                            R.string.add_buy_sell_transaction
                        } else {
                            R.string.try_another_asset_search
                        }
                    )
                }
                loadedCount += page.size
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                if (pageVersion == requestVersion && isActive()) {
                    hideLoadingIndicator()
                }
            } finally {
                if (pageVersion == requestVersion) {
                    isLoading = false
                }
            }
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
