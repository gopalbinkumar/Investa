package com.example.investa.ui.cash

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Spinner
import android.widget.TextView
import android.widget.ScrollView
import com.example.investa.R
import com.example.investa.data.entity.CashAccountEntity
import com.example.investa.data.entity.CurrencyEntity
import com.example.investa.navigation.ScreenHost
import com.example.investa.ui.common.applyElevatedCard
import com.example.investa.ui.common.disableFontPaddingRecursively
import com.example.investa.ui.common.applyElevatedCards
import com.example.investa.utils.formatAmount
import com.example.investa.utils.formatInputAmount
import com.example.investa.utils.installMoneyInputFormatter
import com.example.investa.utils.parseMoneyInput
import com.example.investa.utils.showInvestaToast
import com.example.investa.utils.enableImeScrolling
import com.example.investa.utils.localizedCurrencyName
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog

internal class CashRenderer(private val host: ScreenHost) {
    fun render() {
        host.contentContainer.removeAllViews()
        val root = host.inflate(R.layout.screen_cash)
        host.attach(root)
        val currencies = availableCurrencies()
        val totalCash = host.databaseCashAccounts.sumOf { account ->
            account.balance * exchangeRate(account.currencyCode)
        }
        root.findViewById<TextView>(R.id.cash_total_value).text =
            formatAmount(totalCash, "IDR", "Rp", 0)
        root.findViewById<TextView>(R.id.cash_total_usd_value).text =
            formatAmount(totalCash / host.exchangeRateFor("USD").coerceAtLeast(1.0), "USD", "\$", 2)

        val accountsContainer = root.findViewById<LinearLayout>(R.id.cash_accounts_container)
        host.databaseCashAccounts
            .sortedBy { it.currencyCode }
            .forEach { account ->
                val currency = currencies.firstOrNull { it.code == account.currencyCode }
                    ?: fallbackCurrency(account.currencyCode)
                val row = LayoutInflater.from(host.activity)
                    .inflate(R.layout.view_cash_account, accountsContainer, false)
                row.disableFontPaddingRecursively()
                row.findViewById<TextView>(R.id.cash_account_code).text = currency.code
                row.findViewById<TextView>(R.id.cash_account_name).text = localizedCurrencyName(host.activity, currency)
                row.findViewById<TextView>(R.id.cash_account_balance).text =
                    formatAmount(account.balance, currency.code, currency.symbol, 2)
                row.findViewById<TextView>(R.id.cash_account_idr_value).apply {
                    if (currency.code == "IDR") {
                        visibility = View.GONE
                    } else {
                        text = host.activity.getString(
                            R.string.approx_amount,
                            formatAmount(account.balance * exchangeRate(currency.code), "IDR", "Rp", 0)
                        )
                    }
                }
                row.setOnClickListener { showCashDrawer(account) }
                accountsContainer.addView(row)
                applyElevatedCard(row)
            }
        root.findViewById<View>(R.id.cash_add).setOnClickListener { showCashDrawer(null) }
    }

    private fun showCashDrawer(account: CashAccountEntity?) {
        val dialog = BottomSheetDialog(host.activity)
        val drawer = host.activity.layoutInflater.inflate(R.layout.bottom_sheet_cash, null)
        drawer.disableFontPaddingRecursively()
        applyElevatedCards(drawer)
        drawer.findViewById<ScrollView>(R.id.cash_content_scroll).enableImeScrolling()
        dialog.setContentView(drawer)
        val title = drawer.findViewById<TextView>(R.id.cash_drawer_title)
        val currencySpinner = drawer.findViewById<Spinner>(R.id.cash_currency)
        val amountInput = drawer.findViewById<EditText>(R.id.cash_amount)
        val saveButton = drawer.findViewById<TextView>(R.id.cash_save)
        val currencies = availableCurrencies()
        val currencyCodes = currencies.map { it.code }
        val initialCurrency = account?.currencyCode ?: "IDR"
        currencySpinner.adapter = ArrayAdapter(
            host.activity,
            R.layout.spinner_item,
            currencyCodes
        ).also { it.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item) }
        currencySpinner.setSelection(currencyCodes.indexOf(initialCurrency).coerceAtLeast(0))
        currencySpinner.isEnabled = account == null
        title.text = host.activity.getString(if (account == null) R.string.add_cash else R.string.edit_cash)
        saveButton.text = host.activity.getString(if (account == null) R.string.save_cash else R.string.save_changes)

        fun selectedCurrency(): String = currencySpinner.selectedItem?.toString() ?: "IDR"
        amountInput.setText(
            account?.let { formatInputAmount(it.balance, it.currencyCode) }.orEmpty()
        )
        installMoneyInputFormatter(amountInput) { selectedCurrency() }
        currencySpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                val amount = parseMoneyInput(amountInput.text.toString()) ?: return
                val formatted = formatInputAmount(amount, selectedCurrency())
                if (amountInput.text.toString() != formatted) {
                    amountInput.setText(formatted)
                    amountInput.setSelection(formatted.length)
                }
            }

            override fun onNothingSelected(parent: AdapterView<*>?) = Unit
        }
        saveButton.setOnClickListener {
            val amount = parseMoneyInput(amountInput.text.toString())
            when {
                amount == null || amount < 0.0 -> {
                    amountInput.error = host.activity.getString(R.string.valid_amount)
                    amountInput.requestFocus()
                }
                account == null -> host.cashViewModel.addCash(
                    selectedCurrency(),
                    amount,
                    onSaved = {
                        dialog.dismiss()
                        host.activity.showInvestaToast(host.activity.getString(R.string.cash_added))
                    },
                    onError = { message -> showError(message) }
                )
                else -> host.cashViewModel.updateCash(
                    account,
                    amount,
                    onSaved = {
                        dialog.dismiss()
                        host.activity.showInvestaToast(host.activity.getString(R.string.cash_updated))
                    },
                    onError = { message -> showError(message) }
                )
            }
        }
        dialog.setOnShowListener {
            val bottomSheet = dialog.findViewById<android.widget.FrameLayout>(
                com.google.android.material.R.id.design_bottom_sheet
            )
            bottomSheet?.setBackgroundColor(Color.TRANSPARENT)
            bottomSheet?.let {
                BottomSheetBehavior.from(it).apply {
                    isDraggable = false
                    state = BottomSheetBehavior.STATE_EXPANDED
                }
            }
        }
        dialog.show()
    }

    private fun availableCurrencies(): List<CurrencyEntity> {
        val currencies = host.databaseCurrencies.filter { it.isActive }
        return if (currencies.isNotEmpty()) currencies else listOf(
            fallbackCurrency("IDR"),
            fallbackCurrency("USD")
        )
    }

    private fun fallbackCurrency(code: String): CurrencyEntity = CurrencyEntity(
        code = code,
        name = if (code == "USD") host.activity.getString(R.string.us_dollar) else host.activity.getString(R.string.indonesian_rupiah),
        symbol = if (code == "USD") "$" else "Rp",
        exchangeRate = if (code == "USD") 16500.0 else 1.0,
        updatedAt = 0L,
        isActive = true
    )

    private fun exchangeRate(code: String): Double =
        host.databaseCurrencies.firstOrNull { it.code == code }?.exchangeRate
            ?.takeIf { it > 0.0 }
            ?: if (code == "USD") 16500.0 else 1.0

    private fun showError(message: String) {
        host.activity.showInvestaToast(message)
    }
}
