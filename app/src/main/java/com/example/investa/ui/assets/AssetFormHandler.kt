package com.example.investa.ui.assets

import android.text.Editable
import android.text.InputFilter
import android.text.TextWatcher
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.Spinner
import android.widget.Toast
import android.widget.TextView
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
import com.example.investa.utils.parseMoneyInput
import com.example.investa.utils.parseTransactionQuantity
import com.example.investa.utils.priceUnitSuffix
import com.example.investa.utils.quantityUnitHint
import com.example.investa.utils.toUiAsset
import java.util.Locale

internal class AssetFormHandler(private val host: ScreenHost) {
    fun render(asset: Asset?) {
        val root = host.inflate(R.layout.screen_asset_form)
        host.attach(root)
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
        symbolInput.filters = arrayOf(InputFilter.AllCaps())
        root.findViewById<TextView>(R.id.form_title).text = if (asset == null) "Add Asset" else "Edit Asset"
        root.findViewById<TextView>(R.id.form_save).text = if (asset == null) "Save Asset" else "Save Changes"
        root.findViewById<View>(R.id.form_back).setOnClickListener {
            host.showScreen(if (asset == null) AppScreen.ASSETS else AppScreen.DETAIL)
        }
        root.findViewById<View>(R.id.form_save).setOnClickListener {
            val name = nameInput.text.toString().trim()
            val symbol = symbolInput.text.toString().trim().uppercase(Locale.ROOT)
            val category = categorySpinner.selectedItem?.toString().orEmpty()
            val quantity = parseTransactionQuantity(quantityInput.text.toString())
            val averagePrice = parseMoneyInput(averagePriceInput.text.toString())
            val currentPrice = parseMoneyInput(currentPriceInput.text.toString())
            val investedAmount = if (quantity != null && averagePrice != null) quantity * averagePrice else null
            val currency = currencySpinner.selectedItem?.toString().orEmpty()
            val now = System.currentTimeMillis()
            when {
                name.isEmpty() -> nameInput.apply { error = "Asset name is required"; requestFocus() }
                symbol.isEmpty() -> symbolInput.apply { error = "Symbol is required"; requestFocus() }
                category == SELECT_CATEGORY || category.isEmpty() ->
                    Toast.makeText(host.activity, "Select a category", Toast.LENGTH_SHORT).show()
                quantity == null || quantity <= 0.0 -> quantityInput.apply {
                    error = "Enter a valid quantity"; requestFocus()
                }
                investedAmount == null || investedAmount <= 0.0 -> investedInput.apply {
                    error = "Enter a valid invested amount"; requestFocus()
                }
                averagePrice == null || averagePrice <= 0.0 -> averagePriceInput.apply {
                    error = "Enter a valid average price"; requestFocus()
                }
                currentPrice == null || currentPrice <= 0.0 -> currentPriceInput.apply {
                    error = "Enter a valid current price"; requestFocus()
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
                            host.selectedAsset = savedEntity.toUiAsset()
                            host.showScreen(AppScreen.DETAIL)
                            Toast.makeText(host.activity, "Asset added", Toast.LENGTH_SHORT).show()
                        }
                    } else {
                        host.assetViewModel.updateAsset(updatedEntity) {
                            host.databaseAssets = host.databaseAssets.map { entity ->
                                if (entity.id == updatedEntity.id) updatedEntity else entity
                            }
                            host.selectedAsset = updatedEntity.toUiAsset()
                            host.showScreen(AppScreen.DETAIL)
                            Toast.makeText(host.activity, "Asset updated", Toast.LENGTH_SHORT).show()
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
        setupSpinner(categorySpinner, listOf(SELECT_CATEGORY) + assetCategories, asset?.category ?: SELECT_CATEGORY)
        setupSpinner(currencySpinner, listOf("IDR", "USD"), asset?.currency ?: "IDR")
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

        fun updateQuantityUnitHint() {
            quantityUnitHintView.text = quantityUnitHint(categorySpinner.selectedItem?.toString().orEmpty(), symbolInput.text.toString())
            val unitSuffix = priceUnitSuffix(categorySpinner.selectedItem?.toString().orEmpty(), symbolInput.text.toString())
            averagePriceUnit.text = unitSuffix
            currentPriceUnit.text = unitSuffix
            quantityUnitHintView.visibility = View.VISIBLE
            val hasAssetIdentity = nameInput.text.toString().trim().isNotEmpty() && symbolInput.text.toString().trim().isNotEmpty()
            assetFieldsContainer.visibility = if (categorySpinner.selectedItem?.toString() != SELECT_CATEGORY && hasAssetIdentity) View.VISIBLE else View.GONE
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
        updateQuantityUnitHint()
    }

    private fun setupSpinner(spinner: Spinner, values: List<String>, selected: String) {
        val adapter = ArrayAdapter(host.activity, R.layout.spinner_item, values).also {
            it.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        }
        spinner.adapter = adapter
        spinner.setSelection(values.indexOf(selected).coerceAtLeast(0))
    }

    private fun reformatMoneyInput(input: EditText, currency: String) {
        val amount = parseMoneyInput(input.text.toString()) ?: return
        val formatted = formatInputAmount(amount, currency)
        if (input.text.toString() != formatted) {
            input.setText(formatted)
            input.setSelection(formatted.length)
        }
    }
}
