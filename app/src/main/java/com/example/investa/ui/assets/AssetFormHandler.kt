package com.example.investa.ui.assets

import android.text.Editable
import android.text.InputFilter
import android.text.TextWatcher
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.ScrollView
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.Spinner
import android.widget.TextView
import androidx.lifecycle.lifecycleScope
import com.example.investa.data.entity.AssetEntity
import com.example.investa.R
import com.example.investa.model.Asset
import com.example.investa.navigation.AppScreen
import com.example.investa.navigation.ScreenHost
import com.example.investa.utils.SELECT_CATEGORY
import com.example.investa.utils.assetCategories
import com.example.investa.utils.formatEditableAmount
import com.example.investa.utils.formatInputAmount
import com.example.investa.utils.installDecimalInputFormatter
import com.example.investa.utils.installMoneyInputFormatter
import com.example.investa.utils.enableImeScrolling
import com.example.investa.utils.localizedCategory
import com.example.investa.utils.parseMoneyInput
import com.example.investa.utils.showInvestaToast
import com.example.investa.utils.hideInvestaKeyboard
import com.example.investa.utils.parseTransactionQuantity
import com.example.investa.utils.priceUnitSuffix
import com.example.investa.utils.quantityUnitHint
import com.example.investa.utils.toUiAsset
import com.example.investa.utils.YahooFinanceApi
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import java.util.Locale

internal class AssetFormHandler(private val host: ScreenHost) {
    fun render(asset: Asset?) {
        val root = host.inflate(R.layout.screen_asset_form)
        host.attach(root)
        root.findViewById<ScrollView>(R.id.asset_form_scroll).enableImeScrolling()
        val nameInput = root.findViewById<EditText>(R.id.form_name)
        val symbolInput = root.findViewById<EditText>(R.id.form_symbol)
        val quantityInput = root.findViewById<EditText>(R.id.form_quantity)
        val quantityUnitHintView = root.findViewById<TextView>(R.id.form_quantity_unit_hint)
        val investedInput = root.findViewById<EditText>(R.id.form_invested)
        val averagePriceInput = root.findViewById<EditText>(R.id.form_average_price)
        val averagePriceUnit = root.findViewById<TextView>(R.id.form_average_price_unit)
        val currentPriceInput = root.findViewById<EditText>(R.id.form_current_price)
        val currentPriceUnit = root.findViewById<TextView>(R.id.form_current_price_unit)
        val notesInput = root.findViewById<EditText>(R.id.form_notes)
        val categorySpinner = root.findViewById<Spinner>(R.id.form_category)
        val currencySpinner = root.findViewById<Spinner>(R.id.form_currency)
        val assetFieldsContainer = root.findViewById<View>(R.id.asset_fields_container)
        val refreshButton = root.findViewById<View>(R.id.form_refresh)
        val refreshIcon = root.findViewById<ImageView>(R.id.form_refresh_icon)
        val refreshProgress = root.findViewById<ProgressBar>(R.id.form_refresh_progress)
        symbolInput.filters = arrayOf(InputFilter.AllCaps())
        root.findViewById<TextView>(R.id.form_title).text = host.activity.getString(
            if (asset == null) R.string.add_asset else R.string.edit_asset
        )
        root.findViewById<TextView>(R.id.form_save).text = host.activity.getString(
            if (asset == null) R.string.save_asset else R.string.save_changes
        )
        root.findViewById<View>(R.id.form_back).setOnClickListener {
            host.showScreen(if (asset == null) AppScreen.ASSETS else AppScreen.DETAIL)
        }
        root.findViewById<View>(R.id.form_save).setOnClickListener {
            val name = nameInput.text.toString().trim()
            val symbol = symbolInput.text.toString().trim().uppercase(Locale.ROOT)
            val category = canonicalCategoryAt(categorySpinner.selectedItemPosition)
            val quantity = parseTransactionQuantity(quantityInput.text.toString())
            val averagePrice = parseMoneyInput(averagePriceInput.text.toString())
            val currentPrice = parseMoneyInput(currentPriceInput.text.toString())
            val investedAmount = if (quantity != null && averagePrice != null) quantity * averagePrice else null
            val currency = currencySpinner.selectedItem?.toString().orEmpty()
            val now = System.currentTimeMillis()
            when {
                name.isEmpty() -> nameInput.apply { error = host.activity.getString(R.string.asset_name_required); requestFocus() }
                symbol.isEmpty() -> symbolInput.apply { error = host.activity.getString(R.string.symbol_required); requestFocus() }
                category == SELECT_CATEGORY || category.isEmpty() ->
                    host.activity.showInvestaToast(host.activity.getString(R.string.select_category_error))
                quantity == null || quantity < 0.0 -> quantityInput.apply {
                    error = host.activity.getString(R.string.valid_quantity); requestFocus()
                }
                investedAmount == null || investedAmount < 0.0 -> investedInput.apply {
                    error = host.activity.getString(R.string.valid_invested_amount); requestFocus()
                }
                averagePrice == null || averagePrice < 0.0 -> averagePriceInput.apply {
                    error = host.activity.getString(R.string.valid_average_price); requestFocus()
                }
                currentPrice == null || currentPrice < 0.0 -> currentPriceInput.apply {
                    error = host.activity.getString(R.string.valid_current_price); requestFocus()
                }
                else -> {
                    val existingEntity = asset?.id?.let { id -> host.databaseAssets.firstOrNull { it.id == id } }
                    val updatedEntity = AssetEntity(
                        id = asset?.id ?: 0L,
                        name = name,
                        symbol = symbol,
                        category = category,
                        quantity = quantity,
                        investedAmount = investedAmount,
                        averagePrice = averagePrice,
                        currentPrice = currentPrice,
                        currency = currency,
                        notes = notesInput.text.toString().trim(),
                        createdAt = existingEntity?.createdAt ?: now,
                        updatedAt = now
                    )
                    if (asset == null) {
                        host.assetViewModel.addAsset(updatedEntity) { assetId ->
                            val savedEntity = updatedEntity.copy(id = assetId)
                            host.databaseAssets = host.databaseAssets + savedEntity
                            host.selectedAsset = savedEntity.toUiAsset(host.activity)
                            host.showScreen(AppScreen.DETAIL)
                            host.activity.showInvestaToast(host.activity.getString(R.string.asset_added))
                        }
                    } else {
                        host.assetViewModel.updateAsset(updatedEntity) {
                            host.databaseAssets = host.databaseAssets.map { entity ->
                                if (entity.id == updatedEntity.id) updatedEntity else entity
                            }
                            host.selectedAsset = updatedEntity.toUiAsset(host.activity)
                            host.showScreen(AppScreen.DETAIL)
                            host.activity.showInvestaToast(host.activity.getString(R.string.asset_updated))
                        }
                    }
                }
            }
        }

        nameInput.setText(asset?.name.orEmpty())
        symbolInput.setText(asset?.symbol.orEmpty())
        quantityInput.setText(formatEditableAmount(asset?.quantity?.substringBeforeLast(" ").orEmpty(), "IDR"))
        investedInput.setText("")
        averagePriceInput.setText(asset?.let {
            parseMoneyInput(it.averagePrice)?.let { value -> formatInputAmount(value, it.currency) }
        }.orEmpty())
        currentPriceInput.setText(asset?.let {
            parseMoneyInput(it.currentPrice)?.let { value -> formatInputAmount(value, it.currency) }
        }.orEmpty())
        notesInput.setText(asset?.notes.orEmpty())
        setupCategorySpinner(categorySpinner, asset?.category ?: SELECT_CATEGORY)
        setupSpinner(currencySpinner, listOf("IDR", "USD"), asset?.currency ?: "IDR")
        refreshButton.visibility = View.GONE
        investedInput.apply {
            isFocusable = false
            isFocusableInTouchMode = false
            isCursorVisible = false
            isLongClickable = false
            setOnClickListener(null)
        }
        val moneyInputs = listOf(averagePriceInput, currentPriceInput)
        fun selectedCurrency(): String = currencySpinner.selectedItem?.toString() ?: "IDR"
        fun updateInvestedAmount() {
            val quantity = parseTransactionQuantity(quantityInput.text.toString())
            val averagePrice = parseMoneyInput(averagePriceInput.text.toString())
            val investedAmount = if (quantity != null && averagePrice != null) quantity * averagePrice else null
            investedInput.setText(investedAmount?.takeIf { it > 0.0 }?.let {
                formatInputAmount(it, selectedCurrency())
            } ?: "")
        }
        moneyInputs.forEach { input ->
            installMoneyInputFormatter(input) { selectedCurrency() }
            reformatMoneyInput(input, selectedCurrency())
        }
        installDecimalInputFormatter(quantityInput)
        currencySpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                moneyInputs.forEach { input -> reformatMoneyInput(input, selectedCurrency()) }
                updateInvestedAmount()
            }
            override fun onNothingSelected(parent: AdapterView<*>?) = Unit
        }
        listOf(quantityInput, averagePriceInput).forEach { input ->
            input.addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
                override fun afterTextChanged(s: Editable?) = updateInvestedAmount()
            })
        }
        updateInvestedAmount()

        fun selectedCategory(): String = canonicalCategoryAt(categorySpinner.selectedItemPosition)
        fun updateQuantityUnitHint() {
            val category = selectedCategory()
            quantityUnitHintView.text = quantityUnitHint(host.activity, category, symbolInput.text.toString())
            val unitSuffix = priceUnitSuffix(host.activity, category, symbolInput.text.toString())
            averagePriceUnit.text = unitSuffix
            currentPriceUnit.text = unitSuffix
            quantityUnitHintView.visibility = View.VISIBLE
            val hasAssetIdentity = nameInput.text.toString().trim().isNotEmpty() && symbolInput.text.toString().trim().isNotEmpty()
            assetFieldsContainer.visibility = if (category != SELECT_CATEGORY && hasAssetIdentity) View.VISIBLE else View.GONE
            val canLoadAssetData = category in setOf("Crypto", "ID Stocks", "US Stocks") &&
                symbolInput.text.toString().trim().isNotEmpty()
            refreshButton.visibility = if (canLoadAssetData) View.VISIBLE else View.GONE
        }
        categorySpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) = updateQuantityUnitHint()
            override fun onNothingSelected(parent: AdapterView<*>?) = updateQuantityUnitHint()
        }
        symbolInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = updateQuantityUnitHint()
            override fun afterTextChanged(s: Editable?) = Unit
        })
        nameInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = updateQuantityUnitHint()
            override fun afterTextChanged(s: Editable?) = Unit
        })

        refreshButton.setOnClickListener {
            val category = selectedCategory()
            val symbol = symbolInput.text.toString().trim().uppercase(Locale.ROOT)
            if (category !in setOf("Crypto", "ID Stocks", "US Stocks") || symbol.isBlank()) {
                return@setOnClickListener
            }
            refreshButton.isEnabled = false
            refreshIcon.visibility = View.GONE
            refreshProgress.visibility = View.VISIBLE
            host.activity.lifecycleScope.launch {
                try {
                    val quote = fetchLatestAssetQuote(category, symbol, selectedCurrency())
                    val fetchedName = if (category == "ID Stocks") {
                        quote.shortName ?: quote.longName ?: quote.name
                    } else {
                        quote.name
                    }
                    fetchedName?.let { nameInput.setText(it) }
                    val formattedPrice = formatInputAmount(quote.price, selectedCurrency())
                    currentPriceInput.setText(formattedPrice)
                    currentPriceInput.setSelection(formattedPrice.length)
                    updateQuantityUnitHint()
                    currentPriceInput.hideInvestaKeyboard()
                    host.activity.showInvestaToast(
                        host.activity.getString(R.string.latest_asset_data_loaded)
                    )
                } catch (error: CancellationException) {
                    throw error
                } catch (error: Exception) {
                    host.activity.showInvestaToast(
                        host.activity.getString(
                            R.string.failed_asset_data,
                            error.message ?: error.javaClass.simpleName
                        )
                    )
                } finally {
                    refreshButton.isEnabled = true
                    refreshIcon.visibility = View.VISIBLE
                    refreshProgress.visibility = View.GONE
                }
            }
        }
        updateQuantityUnitHint()
    }

    private suspend fun fetchLatestAssetQuote(
        category: String,
        symbol: String,
        assetCurrency: String
    ): YahooFinanceApi.QuoteDetails {
        val apiSymbol = when (category) {
            "Crypto" -> "$symbol-USD"
            "ID Stocks" -> if (symbol.endsWith(".JK")) symbol else "$symbol.JK"
            "US Stocks" -> symbol
            else -> error(host.activity.getString(R.string.yahoo_category_unavailable))
        }
        val quote = YahooFinanceApi.fetchQuoteDetails(apiSymbol)
        val quoteCurrency = if (category == "ID Stocks") "IDR" else "USD"
        val usdIdrRate = host.exchangeRateFor("USD")
        val convertedPrice = when {
            quoteCurrency == assetCurrency -> quote.price
            quoteCurrency == "USD" && assetCurrency == "IDR" -> quote.price * usdIdrRate
            quoteCurrency == "IDR" && assetCurrency == "USD" -> quote.price / usdIdrRate
            else -> quote.price
        }
        return quote.copy(price = convertedPrice)
    }

    private fun setupSpinner(spinner: Spinner, values: List<String>, selected: String) {
        val adapter = ArrayAdapter(host.activity, R.layout.spinner_item, values).also {
            it.setDropDownViewResource(R.layout.spinner_dropdown_item)
        }
        spinner.adapter = adapter
        spinner.setSelection(values.indexOf(selected).coerceAtLeast(0))
    }

    private fun setupCategorySpinner(spinner: Spinner, selected: String) {
        val values = listOf(SELECT_CATEGORY) + assetCategories
        val displayValues = values.map { localizedCategory(host.activity, it) }
        val adapter = ArrayAdapter(host.activity, R.layout.spinner_item, displayValues).also {
            it.setDropDownViewResource(R.layout.spinner_dropdown_item)
        }
        spinner.adapter = adapter
        spinner.setSelection(values.indexOf(selected).coerceAtLeast(0))
    }

    private fun canonicalCategoryAt(position: Int): String =
        (listOf(SELECT_CATEGORY) + assetCategories).getOrElse(position) { SELECT_CATEGORY }

    private fun reformatMoneyInput(input: EditText, currency: String) {
        val amount = parseMoneyInput(input.text.toString()) ?: return
        val formatted = formatInputAmount(amount, currency)
        if (input.text.toString() != formatted) {
            input.setText(formatted)
            input.setSelection(formatted.length)
        }
    }
}
