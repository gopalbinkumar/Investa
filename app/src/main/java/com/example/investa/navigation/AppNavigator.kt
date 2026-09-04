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

internal interface ScreenHost {
    val activity: ComponentActivity
    val contentContainer: ViewGroup
    val bottomNavigation: View
    var currentScreen: AppScreen
    var detailOrigin: AppScreen
    var selectedCategory: String
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
    private var hasRenderedInitialScreen = false
    private var reportScrollYBeforeDetail = 0

    fun setupBottomNavigation() {
        host.activity.findViewById<View>(R.id.nav_home)
            .setOnClickListener { showScreen(AppScreen.HOME) }
        host.activity.findViewById<View>(R.id.nav_assets)
            .setOnClickListener { showScreen(AppScreen.ASSETS) }
        host.activity.findViewById<View>(R.id.nav_cash)
            .setOnClickListener { showScreen(AppScreen.CASH) }
        host.activity.findViewById<View>(R.id.fab)
            .setOnClickListener { showScreen(AppScreen.ADD) }
        host.activity.findViewById<View>(R.id.nav_reports)
            .setOnClickListener { showScreen(AppScreen.REPORTS) }
        host.activity.findViewById<View>(R.id.nav_settings)
            .setOnClickListener { showScreen(AppScreen.SETTINGS) }
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
        host.currentScreen = screen
        val isMainScreen = screen in listOf(
            AppScreen.HOME,
            AppScreen.ASSETS,
            AppScreen.CASH,
            AppScreen.REPORTS,
            AppScreen.SETTINGS
        )
        host.bottomNavigation.visibility = if (isMainScreen) View.VISIBLE else View.GONE
        host.activity.findViewById<View>(R.id.fab).visibility =
            if (screen == AppScreen.ASSETS) View.VISIBLE else View.GONE
        val contentParams = host.contentContainer.layoutParams as ViewGroup.MarginLayoutParams
        contentParams.bottomMargin = if (isMainScreen) host.dp(56) else 0
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
        AppScreen.THEME -> { showScreen(AppScreen.SETTINGS); true }
        AppScreen.LANGUAGE -> { showScreen(AppScreen.SETTINGS); true }
        AppScreen.EXCHANGE_RATE -> { showScreen(AppScreen.SETTINGS); true }
        AppScreen.DETAIL -> { showScreen(host.detailOrigin); true }
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
        AppScreen.THEME -> 5
        AppScreen.LANGUAGE -> 6
        AppScreen.EXCHANGE_RATE -> 7
        AppScreen.DETAIL -> 8
        AppScreen.ADD -> 9
        AppScreen.EDIT -> 10
    }

    private fun updateSelectedNavigation(screen: AppScreen) {
        val ids = listOf(R.id.nav_home, R.id.nav_assets, R.id.nav_cash, R.id.nav_reports, R.id.nav_settings)
        val selectedId = when (screen) {
            AppScreen.HOME -> R.id.nav_home
            AppScreen.ASSETS -> R.id.nav_assets
            AppScreen.CASH -> R.id.nav_cash
            AppScreen.REPORTS -> R.id.nav_reports
            else -> R.id.nav_settings
        }
        ids.forEach { id -> host.activity.findViewById<View>(id).isSelected = id == selectedId }
    }
}
