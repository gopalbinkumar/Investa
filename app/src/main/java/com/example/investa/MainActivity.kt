package com.example.investa

import android.content.res.Configuration
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.activity.viewModels
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
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
import com.example.investa.data.repository.AppPreferenceRepository
import com.example.investa.model.Asset
import com.example.investa.navigation.AppNavigator
import com.example.investa.navigation.AppScreen
import com.example.investa.navigation.ScreenHost
import com.example.investa.navigation.isMainScreen
import com.example.investa.ui.assets.AssetDetailRenderer
import com.example.investa.ui.assets.AssetFormHandler
import com.example.investa.ui.assets.AssetsRenderer
import com.example.investa.ui.cash.CashRenderer
import com.example.investa.ui.common.disableFontPaddingRecursively
import com.example.investa.ui.common.applyElevatedCards
import com.example.investa.ui.home.HomeRenderer
import com.example.investa.ui.reports.ReportsRenderer
import com.example.investa.ui.settings.SettingsRenderer
import com.example.investa.ui.transactions.TransactionHandler
import com.example.investa.ui.transactions.TransactionHistoryRenderer
import com.example.investa.utils.LanguageManager
import com.example.investa.utils.showInvestaToast
import com.example.investa.utils.toUiAsset
import com.example.investa.utils.NumberFormatStyle
import com.example.investa.utils.NumberFormatStyleManager
import com.example.investa.viewmodel.AssetViewModel
import com.example.investa.viewmodel.AssetViewModelFactory
import com.example.investa.viewmodel.CashViewModel
import com.example.investa.viewmodel.CashViewModelFactory
import com.example.investa.viewmodel.CurrencyViewModel
import com.example.investa.viewmodel.CurrencyViewModelFactory
import com.example.investa.viewmodel.TransactionViewModel
import com.example.investa.viewmodel.TransactionViewModelFactory
import com.example.investa.viewmodel.AppPreferenceViewModel
import com.example.investa.viewmodel.AppPreferenceViewModelFactory
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity(), ScreenHost {
    companion object {
        private const val SCREEN_STATE_KEY = "investa_current_screen"
    }

    override lateinit var contentContainer: ViewGroup
    override lateinit var bottomNavigation: View
    override var systemNavigationInset = 0
    override var currentScreen = AppScreen.HOME
    override var detailOrigin = AppScreen.ASSETS
    override var selectedCategory = "All"
    override var primaryCurrency = "IDR"
    override var numberFormatStyle = NumberFormatStyle.INDONESIAN
    override var assetsRoot: View? = null
    override var databaseAssets: List<AssetEntity> = emptyList()
    override var databaseTransactions: List<TransactionEntity> = emptyList()
    override var databaseCashAccounts: List<CashAccountEntity> = emptyList()
    override var databaseCurrencies: List<CurrencyEntity> = emptyList()
    override var selectedAsset: Asset? = null
    override val activity: ComponentActivity get() = this

    private val database: InvestaDatabase by lazy {
        InvestaDatabase.getInstance(applicationContext)
    }

    override val assetViewModel: AssetViewModel by viewModels {
        AssetViewModelFactory(AssetRepository(database.assetDao()))
    }
    override val transactionViewModel: TransactionViewModel by viewModels {
        TransactionViewModelFactory(TransactionRepository(database))
    }
    override val currencyViewModel: CurrencyViewModel by viewModels {
        CurrencyViewModelFactory(CurrencyRepository(database.currencyDao()))
    }
    override val appPreferenceViewModel: AppPreferenceViewModel by viewModels {
        AppPreferenceViewModelFactory(AppPreferenceRepository(database.appPreferenceDao()))
    }
    override val cashViewModel: CashViewModel by viewModels {
        CashViewModelFactory(CashRepository(database))
    }

    private lateinit var navigator: AppNavigator
    private lateinit var homeRenderer: HomeRenderer
    private lateinit var assetsRenderer: AssetsRenderer
    private lateinit var cashRenderer: CashRenderer
    private lateinit var transactionHandler: TransactionHandler
    private lateinit var transactionHistoryRenderer: TransactionHistoryRenderer
    private lateinit var assetDetailRenderer: AssetDetailRenderer
    private lateinit var assetFormHandler: AssetFormHandler
    private lateinit var reportsRenderer: ReportsRenderer
    private lateinit var settingsRenderer: SettingsRenderer

    override fun onCreate(savedInstanceState: Bundle?) {
        LanguageManager.apply(this)
        super.onCreate(savedInstanceState)
        applySystemBarTheme(resources.configuration)
        setContentView(R.layout.activity_main)
        // Apply the text metric globally to the static activity layout as well.
        // Dynamically rendered screens are covered again from attach() and the
        // existing recursive calls in their renderers.
        findViewById<View>(android.R.id.content).disableFontPaddingRecursively()
        contentContainer = findViewById(R.id.content_container)
        bottomNavigation = findViewById(R.id.bottom_navigation)
        setupSystemBarInsets()

        navigator = AppNavigator(this) { screen -> renderScreen(screen) }
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (!navigator.handleBack()) {
                    isEnabled = false
                    onBackPressedDispatcher.onBackPressed()
                    isEnabled = true
                }
            }
        })
        homeRenderer = HomeRenderer(this)
        assetsRenderer = AssetsRenderer(this)
        cashRenderer = CashRenderer(this)
        transactionHandler = TransactionHandler(this)
        transactionHistoryRenderer = TransactionHistoryRenderer(this, transactionHandler)
        assetDetailRenderer = AssetDetailRenderer(this, transactionHandler)
        assetFormHandler = AssetFormHandler(this)
        reportsRenderer = ReportsRenderer(this)
        settingsRenderer = SettingsRenderer(this)
        navigator.setupBottomNavigation()
        currencyViewModel.ensureDefaults()
        cashViewModel.ensureDefaultAccounts()
        appPreferenceViewModel.ensureDefaults()

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                assetViewModel.assets.collect { assets ->
                    databaseAssets = assets
                    when (currentScreen) {
                        AppScreen.HOME -> homeRenderer.render()
                        AppScreen.ASSETS -> assetsRenderer.render()
                        AppScreen.DETAIL -> assetDetailRenderer.refreshSelectedAsset(assets)
                        AppScreen.TRANSACTION_HISTORY -> transactionHistoryRenderer.refresh()
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
                        AppScreen.TRANSACTION_HISTORY -> transactionHistoryRenderer.refresh()
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
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                appPreferenceViewModel.primaryCurrency.collect { currency ->
                    primaryCurrency = currency
                    when (currentScreen) {
                        AppScreen.HOME -> homeRenderer.render()
                        AppScreen.CASH -> cashRenderer.render()
                        AppScreen.REPORTS -> reportsRenderer.render()
                        AppScreen.TRANSACTION_HISTORY -> transactionHistoryRenderer.refresh()
                        AppScreen.SETTINGS -> settingsRenderer.render()
                        else -> Unit
                    }
                }
            }
        }
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                appPreferenceViewModel.numberFormatStyle.collect { styleId ->
                    numberFormatStyle = NumberFormatStyle.fromId(styleId)
                    NumberFormatStyleManager.apply(numberFormatStyle)
                    when (currentScreen) {
                        AppScreen.HOME -> homeRenderer.render()
                        AppScreen.ASSETS -> assetsRenderer.render()
                        AppScreen.CASH -> cashRenderer.render()
                        AppScreen.REPORTS -> reportsRenderer.render()
                        AppScreen.SETTINGS -> settingsRenderer.render()
                        AppScreen.DETAIL -> assetDetailRenderer.render()
                        else -> Unit
                    }
                }
            }
        }
        val restoredScreen = savedInstanceState
            ?.getString(SCREEN_STATE_KEY)
            ?.let { screenName -> runCatching { AppScreen.valueOf(screenName) }.getOrNull() }
        showScreen(restoredScreen ?: AppScreen.HOME)
        if (LanguageManager.consumeLanguageChangedToast(this)) {
            showInvestaToast(getString(R.string.language_changed))
        }
    }

    private fun setupSystemBarInsets() {
        val root = findViewById<View>(android.R.id.content)
        ViewCompat.setOnApplyWindowInsetsListener(root) { _, insets ->
            val isEdgeToEdgeDevice = android.os.Build.VERSION.SDK_INT >= 35
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            systemNavigationInset = if (isEdgeToEdgeDevice) {
                systemBars.bottom
            } else {
                0
            }

            (bottomNavigation.layoutParams as? ViewGroup.MarginLayoutParams)?.let { params ->
                params.bottomMargin = systemNavigationInset
                bottomNavigation.layoutParams = params
            }

            (contentContainer.layoutParams as? ViewGroup.MarginLayoutParams)?.let { params ->
                params.topMargin = if (isEdgeToEdgeDevice) {
                    (systemBars.top - dp(18)).coerceAtLeast(0)
                } else {
                    0
                }
                params.bottomMargin = when {
                    currentScreen.isMainScreen -> dp(56) + systemNavigationInset
                    else -> systemNavigationInset
                }
                contentContainer.layoutParams = params
            }
            insets
        }
        ViewCompat.requestApplyInsets(root)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putString(SCREEN_STATE_KEY, currentScreen.name)
        super.onSaveInstanceState(outState)
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)

        applySystemBarTheme(newConfig)
        (contentContainer.parent as? View)?.setBackgroundColor(
            ContextCompat.getColor(this, R.color.investa_background)
        )
        bottomNavigation.background = ContextCompat.getDrawable(this, R.drawable.bg_bottom_navigation)
        refreshNavigationIconColors(bottomNavigation)

        contentContainer.animate().cancel()
        contentContainer.translationX = 0f
        contentContainer.alpha = 1f
        contentContainer.removeAllViews()
        homeRenderer.invalidateThemeCache()
        assetsRenderer.invalidateThemeCache()
        reportsRenderer.invalidateThemeCache()
        transactionHistoryRenderer.invalidateThemeCache()
        renderScreen(currentScreen)
        if (LanguageManager.consumeLanguageChangedToast(this)) {
            showInvestaToast(getString(R.string.language_changed))
        }
    }

    private fun applySystemBarTheme(configuration: Configuration) {
        val backgroundColor = ContextCompat.getColor(this, R.color.investa_background)
        window.statusBarColor = backgroundColor
        window.navigationBarColor = backgroundColor

        val isDarkTheme = configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK ==
            Configuration.UI_MODE_NIGHT_YES
        WindowInsetsControllerCompat(window, window.decorView).apply {
            isAppearanceLightStatusBars = !isDarkTheme
            isAppearanceLightNavigationBars = !isDarkTheme
        }
    }

    private fun refreshNavigationIconColors(view: View) {
        if (view is ImageView) {
            view.imageTintList = ContextCompat.getColorStateList(this, R.color.nav_icon_tint)
            view.foreground = ContextCompat.getDrawable(this, R.drawable.ripple_icon)
        }
        if (view is ViewGroup) {
            for (index in 0 until view.childCount) {
                refreshNavigationIconColors(view.getChildAt(index))
            }
        }
    }

    override fun showScreen(screen: AppScreen) = navigator.showScreen(screen)

    private fun renderScreen(screen: AppScreen) {
        when (screen) {
            AppScreen.HOME -> homeRenderer.render()
            AppScreen.ASSETS -> assetsRenderer.render()
            AppScreen.CASH -> cashRenderer.render()
            AppScreen.REPORTS -> {
                reportsRenderer.render()
                // Rendering the chart again while the screen-slide animation is running can
                // interrupt its frames. Load the complete report data immediately after the
                // navigator's 220 ms transition has settled instead.
                contentContainer.postDelayed({
                    if (currentScreen == AppScreen.REPORTS) {
                        refreshTransactions()
                    }
                }, 240L)
            }
            AppScreen.SETTINGS -> settingsRenderer.render()
            AppScreen.LANGUAGE -> settingsRenderer.renderLanguage()
            AppScreen.EXCHANGE_RATE -> settingsRenderer.renderExchangeRate()
            AppScreen.PRIMARY_CURRENCY -> settingsRenderer.renderPrimaryCurrency()
            AppScreen.NUMBER_FORMAT -> settingsRenderer.renderNumberFormat()
            AppScreen.ABOUT -> settingsRenderer.renderAbout()
            AppScreen.DETAIL -> assetDetailRenderer.render()
            AppScreen.TRANSACTION_HISTORY -> transactionHistoryRenderer.render()
            AppScreen.ADD -> assetFormHandler.render(null)
            AppScreen.EDIT -> {
                val entity = databaseAssets.firstOrNull { it.id == selectedAsset?.id }
                if (entity == null) {
                    selectedAsset = null
                    showScreen(AppScreen.ASSETS)
                    return
                }
                assetFormHandler.render(entity.toUiAsset(this))
            }
        }
    }

    override fun inflate(layout: Int): View =
        LayoutInflater.from(this).inflate(layout, contentContainer, false)

    override fun attach(view: View) {
        view.disableFontPaddingRecursively()
        contentContainer.addView(
            view,
            ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        )
        applyElevatedCards(view)
    }

    override fun exchangeRateFor(currency: String): Double {
        if (currency != "USD") return 1.0
        return databaseCurrencies.firstOrNull { it.code == "USD" }?.exchangeRate ?: 16500.0
    }

    override fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    override fun refreshTransactions() {
        lifecycleScope.launch {
            when (currentScreen) {
                AppScreen.TRANSACTION_HISTORY -> transactionHistoryRenderer.refresh()
                AppScreen.REPORTS -> {
                    databaseTransactions = transactionViewModel.getAllTransactions()
                    reportsRenderer.render()
                }
                else -> Unit
            }
        }
    }
}
