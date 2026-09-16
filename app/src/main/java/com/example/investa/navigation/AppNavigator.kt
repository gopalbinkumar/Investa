package com.example.investa.navigation

import android.view.View
import android.view.ViewGroup
import android.widget.ScrollView
import androidx.activity.ComponentActivity
import com.example.investa.data.entity.AssetEntity
import com.example.investa.data.entity.CashAccountEntity
import com.example.investa.data.entity.CurrencyEntity
import com.example.investa.data.entity.TransactionEntity
import com.example.investa.viewmodel.AssetViewModel
import com.example.investa.viewmodel.CashViewModel
import com.example.investa.viewmodel.CurrencyViewModel
import com.example.investa.viewmodel.TransactionViewModel
import com.example.investa.R
import com.example.investa.model.Asset
import com.example.investa.ui.common.disableFontPaddingRecursively

internal interface ScreenHost {
    val activity: ComponentActivity
    val contentContainer: ViewGroup
    val bottomNavigation: View
    var systemNavigationInset: Int
    var currentScreen: AppScreen
    var detailOrigin: AppScreen
    var selectedCategory: String
    var primaryCurrency: String
    var numberFormatStyle: com.example.investa.utils.NumberFormatStyle
    var assetsRoot: View?
    var databaseAssets: List<AssetEntity>
    var databaseTransactions: List<TransactionEntity>
    var databaseCashAccounts: List<CashAccountEntity>
    var databaseCurrencies: List<CurrencyEntity>
    var selectedAsset: Asset?
    val assetViewModel: AssetViewModel
    val transactionViewModel: TransactionViewModel
    val cashViewModel: CashViewModel
    val currencyViewModel: CurrencyViewModel
    val appPreferenceViewModel: com.example.investa.viewmodel.AppPreferenceViewModel

    fun showScreen(screen: AppScreen)
    fun inflate(layout: Int): View
    fun attach(view: View)
    fun exchangeRateFor(currency: String): Double
    fun dp(value: Int): Int
    fun refreshTransactions()
}

internal class AppNavigator(
    private val host: ScreenHost,
    private val renderScreen: (AppScreen) -> Unit
) {
    private val navigationItems = listOf(
        R.id.nav_home to AppScreen.HOME,
        R.id.nav_assets to AppScreen.ASSETS,
        R.id.nav_cash to AppScreen.CASH,
        R.id.nav_reports to AppScreen.REPORTS,
        R.id.nav_settings to AppScreen.SETTINGS
    )
    private var hasRenderedInitialScreen = false
    private var reportScrollYBeforeDetail = 0

    fun setupBottomNavigation() {
        navigationItems.forEach { (viewId, screen) ->
            host.activity.findViewById<View>(viewId)
                .setOnClickListener { showScreen(screen) }
        }
    }

    fun showScreen(screen: AppScreen) {
        if (screen == host.currentScreen && hasRenderedInitialScreen) return
        val isInitialScreen = !hasRenderedInitialScreen
        val previousScreen = host.currentScreen
        if (screen == AppScreen.DETAIL && previousScreen in setOf(AppScreen.ASSETS, AppScreen.REPORTS)) {
            host.detailOrigin = previousScreen
            if (previousScreen == AppScreen.REPORTS) {
                reportScrollYBeforeDetail =
                    host.contentContainer.findViewById<ScrollView>(R.id.reports_scroll)?.scrollY ?: 0
            }
        }
        val shouldRestoreReportScroll =
            screen == AppScreen.REPORTS &&
                previousScreen == AppScreen.DETAIL &&
                host.detailOrigin == AppScreen.REPORTS
        val reportScrollY = if (shouldRestoreReportScroll) reportScrollYBeforeDetail else 0
        val transition = when {
            screen == host.currentScreen -> ScreenTransition.FORWARD
            screenOrder(screen) >= screenOrder(host.currentScreen) -> ScreenTransition.FORWARD
            else -> ScreenTransition.BACKWARD
        }
        if (previousScreen == AppScreen.REPORTS && screen != AppScreen.REPORTS) {
            host.databaseTransactions = emptyList()
        }
        host.currentScreen = screen
        val isMainScreen = screen.isMainScreen
        host.bottomNavigation.visibility = if (isMainScreen) View.VISIBLE else View.GONE
        val contentParams = host.contentContainer.layoutParams as ViewGroup.MarginLayoutParams
        contentParams.bottomMargin = when {
            isMainScreen -> host.dp(56) + host.systemNavigationInset
            else -> host.systemNavigationInset
        }
        host.contentContainer.layoutParams = contentParams
        host.contentContainer.animate().cancel()
        if (isInitialScreen) {
            host.contentContainer.translationX = 0f
            host.contentContainer.alpha = 1f
        } else {
            host.contentContainer.translationX = if (transition == ScreenTransition.FORWARD) {
                host.activity.resources.displayMetrics.widthPixels.toFloat() * 0.18f
            } else {
                -host.activity.resources.displayMetrics.widthPixels.toFloat() * 0.18f
            }
            host.contentContainer.alpha = 0.85f
        }
        host.contentContainer.removeAllViews()
        renderScreen(screen)
        host.contentContainer.disableFontPaddingRecursively()
        if (shouldRestoreReportScroll) {
            host.contentContainer.findViewById<ScrollView>(R.id.reports_scroll)?.let { reportsScroll ->
                reportsScroll.post {
                    reportsScroll.scrollTo(0, reportScrollY)
                }
            }
        }
        if (!isInitialScreen) {
            host.contentContainer.animate()
                .translationX(0f)
                .alpha(1f)
                .setDuration(220L)
                .setInterpolator(android.view.animation.DecelerateInterpolator())
                .start()
        }
        hasRenderedInitialScreen = true
        if (isMainScreen) updateSelectedNavigation(screen)
    }

    fun handleBack(): Boolean = when (host.currentScreen) {
        AppScreen.LANGUAGE -> { showScreen(AppScreen.SETTINGS); true }
        AppScreen.EXCHANGE_RATE -> { showScreen(AppScreen.SETTINGS); true }
        AppScreen.PRIMARY_CURRENCY -> { showScreen(AppScreen.SETTINGS); true }
        AppScreen.NUMBER_FORMAT -> { showScreen(AppScreen.SETTINGS); true }
        AppScreen.ABOUT -> { showScreen(AppScreen.SETTINGS); true }
        AppScreen.DETAIL -> { showScreen(host.detailOrigin); true }
        AppScreen.TRANSACTION_HISTORY -> { showScreen(AppScreen.ASSETS); true }
        AppScreen.ADD -> { showScreen(AppScreen.ASSETS); true }
        AppScreen.EDIT -> { showScreen(AppScreen.DETAIL); true }
        AppScreen.ASSETS,
        AppScreen.CASH,
        AppScreen.REPORTS,
        AppScreen.SETTINGS -> { showScreen(AppScreen.HOME); true }
        else -> false
    }

    private fun screenOrder(screen: AppScreen): Int = when (screen) {
        AppScreen.HOME -> 0
        AppScreen.ASSETS -> 1
        AppScreen.CASH -> 2
        AppScreen.REPORTS -> 3
        AppScreen.SETTINGS -> 4
        AppScreen.LANGUAGE -> 5
        AppScreen.EXCHANGE_RATE -> 6
        AppScreen.PRIMARY_CURRENCY -> 7
        AppScreen.NUMBER_FORMAT -> 8
        AppScreen.ABOUT -> 9
        AppScreen.DETAIL -> 11
        AppScreen.TRANSACTION_HISTORY -> 12
        AppScreen.ADD -> 13
        AppScreen.EDIT -> 14
    }

    private fun updateSelectedNavigation(screen: AppScreen) {
        navigationItems.forEach { (viewId, itemScreen) ->
            host.activity.findViewById<View>(viewId).isSelected = itemScreen == screen
        }
    }
}
