package com.example.investa.ui.settings

import android.content.res.ColorStateList
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.example.investa.data.entity.CurrencyEntity
import com.example.investa.R
import com.example.investa.navigation.AppScreen
import com.example.investa.navigation.ScreenHost
import com.example.investa.ui.common.disableFontPaddingRecursively
import com.example.investa.ui.common.setLoadingState
import com.example.investa.utils.formatInputAmount
import com.example.investa.utils.installMoneyInputFormatter
import com.example.investa.utils.parseMoneyInput
import com.example.investa.utils.showInvestaToast
import com.example.investa.utils.hideInvestaKeyboard
import com.example.investa.utils.ThemeManager
import com.example.investa.utils.LanguageManager
import com.example.investa.utils.IconThemeManager
import com.example.investa.utils.YahooFinanceApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job

internal class SettingsRenderer(private val host: ScreenHost) {
    fun render() {
        val root = host.inflate(R.layout.screen_settings)
        host.attach(root)
        addSettingsRow(root.findViewById(R.id.settings_preferences), R.drawable.ic_lucide_circle_dollar, host.activity.getString(R.string.exchange_rate_setting), "USD / IDR", true) {
            host.showScreen(AppScreen.EXCHANGE_RATE)
        }
        addSettingsRow(
            root.findViewById(R.id.settings_preferences),
            R.drawable.ic_lucide_moon,
            host.activity.getString(R.string.theme),
            themeSummary(),
            true
        ) {
            host.showScreen(AppScreen.THEME)
        }
        addSettingsRow(
            root.findViewById(R.id.settings_preferences),
            R.drawable.ic_lucide_languages, host.activity.getString(R.string.language), LanguageManager.label(host.activity), true) {
            host.showScreen(AppScreen.LANGUAGE)
        }
        addSettingsRow(root.findViewById(R.id.settings_data), R.drawable.ic_lucide_upload, host.activity.getString(R.string.data_backup), "", true)
        addSettingsRow(root.findViewById(R.id.settings_data), R.drawable.ic_lucide_download, host.activity.getString(R.string.data_restore), "", true)
        addSettingsRow(root.findViewById(R.id.settings_about), R.drawable.ic_lucide_info, host.activity.getString(R.string.about_investa), "", true)
        addSettingsRow(root.findViewById(R.id.settings_about), R.drawable.ic_lucide_info, host.activity.getString(R.string.version), "1.0.0", false)
        addSettingsRow(root.findViewById(R.id.settings_about), R.drawable.ic_lucide_message_circle, host.activity.getString(R.string.feedback), "", true)
        addSettingsRow(root.findViewById(R.id.settings_about), R.drawable.ic_lucide_star, host.activity.getString(R.string.rate_investa), "", true)
    }

    fun renderTheme() {
        val root = host.inflate(R.layout.screen_theme)
        host.attach(root)
        root.findViewById<View>(R.id.theme_back)
            .setOnClickListener { host.showScreen(AppScreen.SETTINGS) }

        val currentTheme = ThemeManager.current(host.activity)
        var selectedTheme = currentTheme
        val themeGroup = root.findViewById<android.widget.RadioGroup>(R.id.theme_radio_group)
        themeGroup.check(
            when (currentTheme) {
                ThemeManager.AppTheme.DARK -> R.id.theme_dark
                ThemeManager.AppTheme.LIGHT -> R.id.theme_light
            }
        )
        themeGroup.setOnCheckedChangeListener { _, checkedId ->
            val selected = when (checkedId) {
                R.id.theme_light -> ThemeManager.AppTheme.LIGHT
                else -> ThemeManager.AppTheme.DARK
            }
            selectedTheme = selected
        }

        val currentIconTheme = IconThemeManager.current(host.activity)
        var selectedIconTheme = currentIconTheme
        val iconThemeGroup = root.findViewById<android.widget.RadioGroup>(R.id.icon_theme_radio_group)
        iconThemeGroup.check(
            when (currentIconTheme) {
                IconThemeManager.IconTheme.LIGHT -> R.id.icon_theme_light
                IconThemeManager.IconTheme.DARK -> R.id.icon_theme_dark
            }
        )
        iconThemeGroup.setOnCheckedChangeListener { _, checkedId ->
            selectedIconTheme = when (checkedId) {
                R.id.icon_theme_light -> IconThemeManager.IconTheme.LIGHT
                else -> IconThemeManager.IconTheme.DARK
            }
        }
        root.findViewById<TextView>(R.id.theme_save).setOnClickListener {
            val appThemeChanged = ThemeManager.current(host.activity) != selectedTheme
            val iconThemeChanged = IconThemeManager.current(host.activity) != selectedIconTheme

            if (appThemeChanged) {
                ThemeManager.save(host.activity, selectedTheme)
            }
            if (iconThemeChanged) {
                IconThemeManager.save(host.activity, selectedIconTheme)
                IconThemeManager.apply(host.activity)
                host.activity.finishAffinity()
                host.activity.finishAndRemoveTask()
                return@setOnClickListener
            }
            host.activity.showInvestaToast(host.activity.getString(R.string.theme_changed))
            if (appThemeChanged) {
                ThemeManager.apply(host.activity)
            }
        }
    }

    fun renderLanguage() {
        val root = host.inflate(R.layout.screen_language)
        host.attach(root)
        root.findViewById<View>(R.id.language_back)
            .setOnClickListener { host.showScreen(AppScreen.SETTINGS) }

        val currentLanguage = LanguageManager.current(host.activity)
        var selectedLanguage = currentLanguage
        val languageGroup = root.findViewById<android.widget.RadioGroup>(R.id.language_radio_group)
        languageGroup.check(
            when (currentLanguage) {
                LanguageManager.AppLanguage.ENGLISH -> R.id.language_english
                LanguageManager.AppLanguage.INDONESIAN -> R.id.language_indonesian
            }
        )
        languageGroup.setOnCheckedChangeListener { _, checkedId ->
            selectedLanguage = when (checkedId) {
                R.id.language_english -> LanguageManager.AppLanguage.ENGLISH
                else -> LanguageManager.AppLanguage.INDONESIAN
            }
        }
        root.findViewById<TextView>(R.id.language_save).setOnClickListener {
            val languageChanged = currentLanguage != selectedLanguage
            LanguageManager.save(host.activity, selectedLanguage)
            if (languageChanged) {
                LanguageManager.markLanguageChangedToast(host.activity)
            }
            LanguageManager.apply(host.activity)
            if (!languageChanged) {
                host.activity.showInvestaToast(host.activity.getString(R.string.language_changed))
            }
        }
    }

    fun renderExchangeRate() {
        val root = host.inflate(R.layout.screen_exchange_rate)
        host.attach(root)
        val exchangeRateInput = root.findViewById<EditText>(R.id.exchange_rate_input)
        val refreshButton = root.findViewById<View>(R.id.exchange_rate_refresh)
        val refreshIcon = root.findViewById<ImageView>(R.id.exchange_rate_refresh_icon)
        val refreshProgress = root.findViewById<ProgressBar>(R.id.exchange_rate_refresh_progress)
        var exchangeRateRequestJob: Job? = null
        val usd = host.databaseCurrencies.firstOrNull { it.code == "USD" } ?: CurrencyEntity(
            code = "USD", name = host.activity.getString(R.string.us_dollar), symbol = "$", exchangeRate = 16500.0,
            updatedAt = 0L, isActive = true
        )
        exchangeRateInput.setText(formatInputAmount(usd.exchangeRate, "IDR"))
        installMoneyInputFormatter(exchangeRateInput) { "IDR" }
        refreshButton.setOnClickListener {
            setLoadingState(refreshButton, refreshIcon, refreshProgress, true)
            exchangeRateRequestJob = host.activity.lifecycleScope.launch {
                try {
                    val latestRate = YahooFinanceApi.fetchUsdIdrRate()
                        val formatted = formatInputAmount(latestRate, "IDR")
                        exchangeRateInput.setText(formatted)
                        exchangeRateInput.setSelection(formatted.length)
                        exchangeRateInput.hideInvestaKeyboard()
                        host.activity.showInvestaToast(host.activity.getString(R.string.latest_exchange_rate_loaded))
                } catch (error: CancellationException) {
                    throw error
                } catch (error: Exception) {
                    host.activity.showInvestaToast(
                        host.activity.getString(R.string.failed_exchange_rate, error.message ?: error.javaClass.simpleName)
                    )
                } finally {
                    setLoadingState(refreshButton, refreshIcon, refreshProgress, false)
                }
            }
        }
        root.findViewById<View>(R.id.exchange_rate_save).setOnClickListener {
            val exchangeRate = parseMoneyInput(exchangeRateInput.text.toString())
            if (exchangeRate == null || exchangeRate <= 0.0) {
                exchangeRateInput.error = host.activity.getString(R.string.valid_exchange_rate)
                exchangeRateInput.requestFocus()
                return@setOnClickListener
            }
            val updatedUsd = usd.copy(exchangeRate = exchangeRate, updatedAt = System.currentTimeMillis())
            host.currencyViewModel.update(updatedUsd) {
                host.databaseCurrencies = host.databaseCurrencies.filterNot { it.code == updatedUsd.code } + updatedUsd
                host.activity.showInvestaToast(host.activity.getString(R.string.exchange_rate_saved))
            }
        }
        root.findViewById<View>(R.id.exchange_rate_back)
            .setOnClickListener { host.showScreen(AppScreen.SETTINGS) }
        root.addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
            override fun onViewAttachedToWindow(view: View) = Unit

            override fun onViewDetachedFromWindow(view: View) {
                val request = exchangeRateRequestJob
                if (request?.isActive == true) {
                    request.cancel()
                    host.activity.showInvestaToast(
                        host.activity.getString(R.string.exchange_rate_request_cancelled)
                    )
                }
                exchangeRateRequestJob = null
            }
        })
    }

    private fun themeLabel(theme: ThemeManager.AppTheme): String = when (theme) {
        ThemeManager.AppTheme.DARK -> host.activity.getString(R.string.theme_dark)
        ThemeManager.AppTheme.LIGHT -> host.activity.getString(R.string.theme_light)
    }

    private fun themeSummary(): String = themeLabel(ThemeManager.current(host.activity))

    private fun addSettingsRow(
        parent: ViewGroup,
        iconRes: Int,
        label: String,
        value: String,
        chevron: Boolean,
        onClick: (() -> Unit)? = null
    ) {
        val row = LayoutInflater.from(host.activity).inflate(R.layout.view_settings_row, parent, false)
        row.disableFontPaddingRecursively()
        row.findViewById<ImageView>(R.id.settings_icon).apply {
            setImageResource(iconRes)
            imageTintList = ColorStateList.valueOf(
                ContextCompat.getColor(host.activity, R.color.investa_icon_primary)
            )
        }
        row.findViewById<TextView>(R.id.settings_label).text = label
        row.findViewById<TextView>(R.id.settings_value).text = value
        row.findViewById<ImageView>(R.id.settings_chevron).visibility = if (chevron) View.VISIBLE else View.GONE
        row.setOnClickListener { onClick?.invoke() }
        row.isClickable = onClick != null
        parent.addView(row)
    }
}
