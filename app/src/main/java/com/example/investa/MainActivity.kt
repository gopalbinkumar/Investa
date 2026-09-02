package com.example.investa

import android.content.res.ColorStateList
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.ComponentActivity
import androidx.appcompat.app.AppCompatActivity
import androidx.activity.viewModels
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.example.investa.data.InvestaDatabase
import com.example.investa.data.entity.AssetEntity
import com.example.investa.data.entity.CashAccountEntity
import com.example.investa.data.entity.CurrencyEntity
import com.example.investa.data.entity.TransactionEntity
import com.example.investa.data.repository.AssetRepository
import com.example.investa.data.repository.CashRepository
import com.example.investa.data.repository.CurrencyRepository
import com.example.investa.data.repository.TransactionRepository
import com.example.investa.model.Asset
import com.example.investa.navigation.AppNavigator
import com.example.investa.navigation.AppScreen
import com.example.investa.navigation.ScreenHost
import com.example.investa.ui.assets.AssetDetailRenderer
import com.example.investa.ui.assets.AssetFormHandler
import com.example.investa.ui.assets.AssetsRenderer
import com.example.investa.ui.cash.CashRenderer
import com.example.investa.ui.home.HomeRenderer
import com.example.investa.ui.reports.ReportsRenderer
import com.example.investa.ui.settings.SettingsRenderer
import com.example.investa.ui.transactions.TransactionHandler
import com.example.investa.utils.ThemeManager
import com.example.investa.utils.toUiAsset
import com.example.investa.viewmodel.AssetViewModel
import com.example.investa.viewmodel.AssetViewModelFactory
import com.example.investa.viewmodel.CashViewModel
import com.example.investa.viewmodel.CashViewModelFactory
import com.example.investa.viewmodel.CurrencyViewModel
import com.example.investa.viewmodel.CurrencyViewModelFactory
import com.example.investa.viewmodel.TransactionViewModel
import com.example.investa.viewmodel.TransactionViewModelFactory
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity(), ScreenHost {
    companion object {
        private const val SCREEN_STATE_KEY = "investa_current_screen"
    }

    override lateinit var contentContainer: ViewGroup
    override lateinit var bottomNavigation: View
    override var currentScreen = AppScreen.HOME
    override var selectedCategory = "All"
    override var assetsRoot: View? = null
    override var databaseAssets: List<AssetEntity> = emptyList()
    override var databaseTransactions: List<TransactionEntity> = emptyList()
    override var databaseCashAccounts: List<CashAccountEntity> = emptyList()
    override var databaseCurrencies: List<CurrencyEntity> = emptyList()
    override var selectedAsset: Asset? = null
    override val activity: ComponentActivity get() = this

    override val assetViewModel: AssetViewModel by viewModels {
        AssetViewModelFactory(AssetRepository(InvestaDatabase.getInstance(applicationContext).assetDao()))
    }
    override val transactionViewModel: TransactionViewModel by viewModels {
        TransactionViewModelFactory(TransactionRepository(InvestaDatabase.getInstance(applicationContext)))
    }
    override val currencyViewModel: CurrencyViewModel by viewModels {
        CurrencyViewModelFactory(CurrencyRepository(InvestaDatabase.getInstance(applicationContext).currencyDao()))
    }
    override val cashViewModel: CashViewModel by viewModels {
        CashViewModelFactory(CashRepository(InvestaDatabase.getInstance(applicationContext)))
    }

    private lateinit var navigator: AppNavigator
    private lateinit var homeRenderer: HomeRenderer
    private lateinit var assetsRenderer: AssetsRenderer
    private lateinit var cashRenderer: CashRenderer
    private lateinit var transactionHandler: TransactionHandler
    private lateinit var assetDetailRenderer: AssetDetailRenderer
    private lateinit var assetFormHandler: AssetFormHandler
    private lateinit var reportsRenderer: ReportsRenderer
    private lateinit var settingsRenderer: SettingsRenderer

    override fun onCreate(savedInstanceState: Bundle?) {
        ThemeManager.apply(this)
        super.onCreate(savedInstanceState)
        window.statusBarColor = ContextCompat.getColor(this, R.color.investa_background)
        window.navigationBarColor = ContextCompat.getColor(this, R.color.investa_background)
        setContentView(R.layout.activity_main)
        findViewById<com.google.android.material.floatingactionbutton.FloatingActionButton>(R.id.fab)
            .imageTintList = ColorStateList.valueOf(
                ContextCompat.getColor(this, R.color.investa_background)
            )
        contentContainer = findViewById(R.id.content_container)
        bottomNavigation = findViewById(R.id.bottom_navigation)

        navigator = AppNavigator(this) { screen -> renderScreen(screen) }
        homeRenderer = HomeRenderer(this)
        assetsRenderer = AssetsRenderer(this)
        cashRenderer = CashRenderer(this)
        transactionHandler = TransactionHandler(this)
        assetDetailRenderer = AssetDetailRenderer(this, transactionHandler)
        assetFormHandler = AssetFormHandler(this)
        reportsRenderer = ReportsRenderer(this)
        settingsRenderer = SettingsRenderer(this)
        navigator.setupBottomNavigation()
        currencyViewModel.ensureDefaults()
        cashViewModel.ensureDefaultAccounts()

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                assetViewModel.assets.collect { assets ->
                    databaseAssets = assets
                    when (currentScreen) {
                        AppScreen.HOME -> homeRenderer.render()
                        AppScreen.ASSETS -> assetsRenderer.render()
                        AppScreen.DETAIL -> assetDetailRenderer.refreshSelectedAsset(assets)
                        AppScreen.REPORTS -> reportsRenderer.render()
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
                        AppScreen.HOME -> homeRenderer.render()
                        AppScreen.REPORTS -> reportsRenderer.render()
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
                        AppScreen.HOME -> homeRenderer.render()
                        AppScreen.ASSETS -> assetsRenderer.render()
                        AppScreen.REPORTS -> reportsRenderer.render()
                        AppScreen.CASH -> cashRenderer.render()
                        else -> Unit
                    }
                }
            }
        }
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                cashViewModel.cashAccounts.collect { accounts ->
                    databaseCashAccounts = accounts
                    when (currentScreen) {
                        AppScreen.HOME -> homeRenderer.render()
                        AppScreen.CASH -> cashRenderer.render()
                        else -> Unit
                    }
                }
            }
        }
        val restoredScreen = savedInstanceState
            ?.getString(SCREEN_STATE_KEY)
            ?.let { screenName -> runCatching { AppScreen.valueOf(screenName) }.getOrNull() }
        showScreen(restoredScreen ?: AppScreen.HOME)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putString(SCREEN_STATE_KEY, currentScreen.name)
        super.onSaveInstanceState(outState)
    }

    override fun onBackPressed() {
        if (!navigator.handleBack()) super.onBackPressed()
    }

    override fun showScreen(screen: AppScreen) = navigator.showScreen(screen)

    private fun renderScreen(screen: AppScreen) {
        when (screen) {
            AppScreen.HOME -> homeRenderer.render()
            AppScreen.ASSETS -> assetsRenderer.render()
            AppScreen.CASH -> cashRenderer.render()
            AppScreen.REPORTS -> reportsRenderer.render()
            AppScreen.SETTINGS -> settingsRenderer.render()
            AppScreen.THEME -> settingsRenderer.renderTheme()
            AppScreen.EXCHANGE_RATE -> settingsRenderer.renderExchangeRate()
            AppScreen.DETAIL -> assetDetailRenderer.render()
            AppScreen.ADD -> assetFormHandler.render(null)
            AppScreen.EDIT -> {
                val entity = databaseAssets.firstOrNull { it.id == selectedAsset?.id }
                if (entity == null) {
                    selectedAsset = null
                    showScreen(AppScreen.ASSETS)
                    return
                }
                assetFormHandler.render(entity.toUiAsset())
            }
        }
    }

    override fun inflate(layout: Int): View =
        LayoutInflater.from(this).inflate(layout, contentContainer, false)

    override fun attach(view: View) {
        contentContainer.addView(
            view,
            ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        )
    }

    override fun exchangeRateFor(currency: String): Double {
        if (currency != "USD") return 1.0
        return databaseCurrencies.firstOrNull { it.code == "USD" }?.exchangeRate ?: 16500.0
    }

    override fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    override fun refreshTransactions() {
        lifecycleScope.launch {
            databaseTransactions = transactionViewModel.getAllTransactions()
            when (currentScreen) {
                AppScreen.HOME -> homeRenderer.render()
                AppScreen.REPORTS -> reportsRenderer.render()
                else -> Unit
            }
        }
    }
}
