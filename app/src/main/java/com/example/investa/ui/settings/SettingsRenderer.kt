package com.example.investa.ui.settings

import android.content.res.ColorStateList
import android.content.Intent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import com.google.android.material.switchmaterial.SwitchMaterial
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
import com.example.investa.utils.YahooFinanceApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job

internal class SettingsRenderer(private val host: ScreenHost) {
    companion object {
        private const val THEME_SWITCH_ANIMATION_DELAY_MS = 220L
    }

    fun render() {
        val root = host.inflate(R.layout.screen_settings)
        host.attach(root)
        addSettingsRow(
            root.findViewById(R.id.settings_preferences),
            R.drawable.ic_lucide_circle_dollar,
            host.activity.getString(R.string.primary_currency),
            "",
            true,
            onClick = { host.showScreen(AppScreen.PRIMARY_CURRENCY) }
        )
        addSettingsRow(
            root.findViewById(R.id.settings_preferences),
            R.drawable.ic_lucide_decimals_arrow_right,
            host.activity.getString(R.string.number_format),
            "",
            true,
            onClick = { host.showScreen(AppScreen.NUMBER_FORMAT) }
        )
        addSettingsRow(
            root.findViewById(R.id.settings_preferences),
            R.drawable.ic_lucide_chart_no_axes_combined,
            host.activity.getString(R.string.exchange_rate_setting),
            "",
            true,
            onClick = { host.showScreen(AppScreen.EXCHANGE_RATE) }
        )
        addSettingsRow(
            root.findViewById(R.id.settings_preferences),
            R.drawable.ic_lucide_languages,
            host.activity.getString(R.string.language),
            "",
            true,
            onClick = { host.showScreen(AppScreen.LANGUAGE) }
        )
        addSettingsRow(
            root.findViewById(R.id.settings_preferences),
            R.drawable.ic_lucide_moon,
            host.activity.getString(R.string.dark_theme),
            "",
            false,
            switchVisible = true,
            switchChecked = ThemeManager.isDarkModeEnabled(host.activity),
            onSwitchChanged = { isDark ->
                if (ThemeManager.update(host.activity, isDark)) {
                    host.activity.showInvestaToast(host.activity.getString(R.string.theme_changed))
                }
            }
        )
        addSettingsRow(root.findViewById(R.id.settings_data), R.drawable.ic_lucide_upload, host.activity.getString(R.string.data_backup), "", true)
        addSettingsRow(root.findViewById(R.id.settings_data), R.drawable.ic_lucide_download, host.activity.getString(R.string.data_restore), "", true)
        addSettingsRow(
            root.findViewById(R.id.settings_about),
            R.drawable.ic_lucide_info,
            host.activity.getString(R.string.about_investa),
            "",
            true,
            onClick = { host.showScreen(AppScreen.ABOUT) }
        )
        addSettingsRow(root.findViewById(R.id.settings_about), R.drawable.ic_lucide_message_circle, host.activity.getString(R.string.feedback), "", true)
        addSettingsRow(root.findViewById(R.id.settings_about), R.drawable.ic_lucide_star, host.activity.getString(R.string.rate_investa), "", true)
        addSettingsRow(root.findViewById(R.id.settings_about), R.drawable.ic_lucide_info, host.activity.getString(R.string.version), "1.0.0", false)
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

    fun renderPrimaryCurrency() {
        val root = host.inflate(R.layout.screen_primary_currency)
        host.attach(root)
        root.findViewById<View>(R.id.primary_currency_back)
            .setOnClickListener { host.showScreen(AppScreen.SETTINGS) }

        var selectedCurrency = host.primaryCurrency
        val currencyGroup = root.findViewById<android.widget.RadioGroup>(R.id.primary_currency_radio_group)
        currencyGroup.check(
            if (selectedCurrency == "USD") R.id.primary_currency_usd
            else R.id.primary_currency_idr
        )
        currencyGroup.setOnCheckedChangeListener { _, checkedId ->
            selectedCurrency = if (checkedId == R.id.primary_currency_usd) "USD" else "IDR"
        }
        root.findViewById<TextView>(R.id.primary_currency_save).setOnClickListener {
            host.appPreferenceViewModel.savePrimaryCurrency(selectedCurrency)
            host.activity.showInvestaToast(host.activity.getString(R.string.primary_currency_changed))
            host.showScreen(AppScreen.SETTINGS)
        }
    }

    fun renderNumberFormat() {
        val root = host.inflate(R.layout.screen_number_format)
        host.attach(root)
        root.findViewById<View>(R.id.number_format_back)
            .setOnClickListener { host.showScreen(AppScreen.SETTINGS) }

        var selectedStyle = host.numberFormatStyle
        val formatGroup = root.findViewById<android.widget.RadioGroup>(R.id.number_format_radio_group)
        formatGroup.check(
            if (selectedStyle == com.example.investa.utils.NumberFormatStyle.ENGLISH) {
                R.id.number_format_english
            } else {
                R.id.number_format_indonesian
            }
        )
        formatGroup.setOnCheckedChangeListener { _, checkedId ->
            selectedStyle = if (checkedId == R.id.number_format_english) {
                com.example.investa.utils.NumberFormatStyle.ENGLISH
            } else {
                com.example.investa.utils.NumberFormatStyle.INDONESIAN
            }
        }
        root.findViewById<TextView>(R.id.number_format_save).setOnClickListener {
            host.appPreferenceViewModel.saveNumberFormatStyle(selectedStyle.id)
            host.activity.showInvestaToast(host.activity.getString(R.string.number_format_changed))
            host.showScreen(AppScreen.SETTINGS)
        }
    }

    fun renderAbout() {
        val root = host.inflate(R.layout.screen_about)
        host.attach(root)
        root.findViewById<View>(R.id.about_back)
            .setOnClickListener { host.showScreen(AppScreen.SETTINGS) }
        root.findViewById<View>(R.id.about_feedback).setOnClickListener {
            val feedbackIntent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_SUBJECT, host.activity.getString(R.string.feedback_subject))
            }
            host.activity.startActivity(Intent.createChooser(feedbackIntent, null))
        }
        root.findViewById<View>(R.id.about_privacy).setOnClickListener {
            host.activity.showInvestaToast(host.activity.getString(R.string.privacy_policy_message))
        }
    }

    private fun addSettingsRow(
        parent: ViewGroup,
        iconRes: Int,
        label: String,
        value: String,
        chevron: Boolean,
        switchVisible: Boolean = false,
        switchChecked: Boolean = false,
        onSwitchChanged: ((Boolean) -> Unit)? = null,
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
        val themeSwitch = row.findViewById<SwitchMaterial>(R.id.settings_switch)
        themeSwitch.visibility = if (switchVisible) View.VISIBLE else View.GONE
        // Theme changes recreate the activity. Restoring this view's old checked
        // state would overwrite the value just loaded from theme preferences.
        themeSwitch.isSaveEnabled = false
        themeSwitch.isChecked = switchChecked
        if (switchVisible) {
            // Handle only a direct click on the switch. A checked-change listener
            // can also run for programmatic state restoration during recreation.
            themeSwitch.setOnCheckedChangeListener(null)
            themeSwitch.setOnClickListener {
                val requestedDarkMode = themeSwitch.isChecked
                themeSwitch.isClickable = false
                themeSwitch.postDelayed({
                    onSwitchChanged?.invoke(requestedDarkMode)
                    themeSwitch.isClickable = true
                }, THEME_SWITCH_ANIMATION_DELAY_MS)
            }
            themeSwitch.isClickable = true
            themeSwitch.isFocusable = true

            // This row must never participate in the gesture. Only the switch is
            // interactive, and removing the foreground also removes pressed/ripple.
            row.foreground = null
            row.setOnClickListener(null)
            row.isClickable = false
            row.isFocusable = false
        } else if (onClick != null) {
            row.setOnClickListener { onClick.invoke() }
            row.isClickable = true
            row.isFocusable = true
        } else {
            row.setOnClickListener(null)
            row.isClickable = false
            row.isFocusable = false
        }
        parent.addView(row)
    }
}
