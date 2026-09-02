package com.example.investa.ui.settings

import android.content.res.ColorStateList
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.example.investa.data.entity.CurrencyEntity
import com.example.investa.R
import com.example.investa.navigation.AppScreen
import com.example.investa.navigation.ScreenHost
import com.example.investa.utils.formatInputAmount
import com.example.investa.utils.installMoneyInputFormatter
import com.example.investa.utils.parseMoneyInput
import com.example.investa.utils.ThemeManager
import com.example.investa.utils.YahooFinanceApi
import kotlinx.coroutines.launch

internal class SettingsRenderer(private val host: ScreenHost) {
    fun render() {
        val root = host.inflate(R.layout.screen_settings)
        host.attach(root)
        addSettingsRow(root.findViewById(R.id.settings_preferences), R.drawable.ic_lucide_circle_dollar, "Base Currency", "IDR", true)
        addSettingsRow(root.findViewById(R.id.settings_preferences), R.drawable.ic_lucide_circle_dollar, "Exchange Rate", "USD / IDR", true) {
            host.showScreen(AppScreen.EXCHANGE_RATE)
        }
        addSettingsRow(
            root.findViewById(R.id.settings_preferences),
            R.drawable.ic_lucide_moon,
            "Theme",
            ThemeManager.current(host.activity).label,
            true
        ) {
            host.showScreen(AppScreen.THEME)
        }
        addSettingsRow(root.findViewById(R.id.settings_preferences), R.drawable.ic_lucide_languages, "Language", "English", true)
        addSettingsRow(root.findViewById(R.id.settings_data), R.drawable.ic_lucide_download, "Data Backup", "", true)
        addSettingsRow(root.findViewById(R.id.settings_data), R.drawable.ic_lucide_upload, "Data Restore", "", true)
        addSettingsRow(root.findViewById(R.id.settings_about), R.drawable.ic_lucide_info, "About Investa", "", true)
        addSettingsRow(root.findViewById(R.id.settings_about), R.drawable.ic_lucide_info, "Version", "1.0.0", false)
        addSettingsRow(root.findViewById(R.id.settings_about), R.drawable.ic_lucide_message_circle, "Feedback", "", true)
        addSettingsRow(root.findViewById(R.id.settings_about), R.drawable.ic_lucide_star, "Rate Investa", "", true)
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
                ThemeManager.AppTheme.SYSTEM -> R.id.theme_system
            }
        )
        themeGroup.setOnCheckedChangeListener { _, checkedId ->
            val selected = when (checkedId) {
                R.id.theme_light -> ThemeManager.AppTheme.LIGHT
                R.id.theme_system -> ThemeManager.AppTheme.SYSTEM
                else -> ThemeManager.AppTheme.DARK
            }
            selectedTheme = selected
        }
        root.findViewById<TextView>(R.id.theme_save).setOnClickListener {
            ThemeManager.save(host.activity, selectedTheme)
            Toast.makeText(
                host.activity,
                "Theme changed",
                Toast.LENGTH_SHORT
            ).show()
            ThemeManager.apply(host.activity)
        }
    }

    fun renderExchangeRate() {
        val root = host.inflate(R.layout.screen_exchange_rate)
        host.attach(root)
        val exchangeRateInput = root.findViewById<EditText>(R.id.exchange_rate_input)
        val refreshButton = root.findViewById<View>(R.id.exchange_rate_refresh)
        val usd = host.databaseCurrencies.firstOrNull { it.code == "USD" } ?: CurrencyEntity(
            code = "USD", name = "US Dollar", symbol = "$", exchangeRate = 16500.0,
            updatedAt = 0L, isActive = true
        )
        exchangeRateInput.setText(formatInputAmount(usd.exchangeRate, "IDR"))
        installMoneyInputFormatter(exchangeRateInput) { "IDR" }
        refreshButton.setOnClickListener {
            refreshButton.isEnabled = false
            host.activity.lifecycleScope.launch {
                runCatching { YahooFinanceApi.fetchUsdIdrRate() }
                    .onSuccess { latestRate ->
                        val formatted = formatInputAmount(latestRate, "IDR")
                        exchangeRateInput.setText(formatted)
                        exchangeRateInput.setSelection(formatted.length)
                        Toast.makeText(
                            host.activity,
                            "Latest exchange rate loaded",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                    .onFailure { error ->
                        Toast.makeText(
                            host.activity,
                            "Failed to load exchange rate: ${error.message}",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                refreshButton.isEnabled = true
            }
        }
        root.findViewById<View>(R.id.exchange_rate_save).setOnClickListener {
            val exchangeRate = parseMoneyInput(exchangeRateInput.text.toString())
            if (exchangeRate == null || exchangeRate <= 0.0) {
                exchangeRateInput.error = "Enter a valid exchange rate"
                exchangeRateInput.requestFocus()
                return@setOnClickListener
            }
            val updatedUsd = usd.copy(exchangeRate = exchangeRate, updatedAt = System.currentTimeMillis())
            host.currencyViewModel.update(updatedUsd) {
                host.databaseCurrencies = host.databaseCurrencies.filterNot { it.code == updatedUsd.code } + updatedUsd
                Toast.makeText(host.activity, "Exchange rate saved", Toast.LENGTH_SHORT).show()
            }
        }
        root.findViewById<View>(R.id.exchange_rate_back)
            .setOnClickListener { host.showScreen(AppScreen.SETTINGS) }
    }

    private fun addSettingsRow(
        parent: ViewGroup,
        iconRes: Int,
        label: String,
        value: String,
        chevron: Boolean,
        onClick: (() -> Unit)? = null
    ) {
        val row = LayoutInflater.from(host.activity).inflate(R.layout.view_settings_row, parent, false)
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
