package com.example.investa.ui.transactions

import android.app.AlertDialog
import android.app.DatePickerDialog
import android.graphics.Color
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.PopupMenu
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.example.investa.data.entity.TransactionEntity
import com.example.investa.R
import com.example.investa.model.Asset
import com.example.investa.navigation.ScreenHost
import com.example.investa.utils.calculateTransactionTotal
import com.example.investa.utils.formatAmount
import com.example.investa.utils.formatEditableAmount
import com.example.investa.utils.formatInputAmount
import com.example.investa.utils.formatTransactionDate
import com.example.investa.utils.installDecimalInputFormatter
import com.example.investa.utils.installMoneyInputFormatter
import com.example.investa.utils.parseMoneyInput
import com.example.investa.utils.showInvestaToast
import com.example.investa.utils.parseTransactionDate
import com.example.investa.utils.parseTransactionQuantity
import com.example.investa.utils.priceUnitSuffix
import com.example.investa.utils.quantityUnitHint
import com.example.investa.utils.LanguageManager
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

internal class TransactionHandler(private val host: ScreenHost) {
    fun showTransactionDrawer(
        asset: Asset,
        isBuy: Boolean,
        transaction: TransactionEntity? = null
    ) {
        val dialog = BottomSheetDialog(host.activity)
        val drawer = host.activity.layoutInflater.inflate(R.layout.bottom_sheet_transaction, null)
        dialog.setContentView(drawer)
        val title = drawer.findViewById<TextView>(R.id.transaction_title)
        val moreButton = drawer.findViewById<View>(R.id.transaction_more)
        val dateInput = drawer.findViewById<EditText>(R.id.transaction_date)
        val quantityInput = drawer.findViewById<EditText>(R.id.transaction_quantity)
        val quantityUnit = drawer.findViewById<TextView>(R.id.transaction_quantity_unit)
        val priceInput = drawer.findViewById<EditText>(R.id.transaction_price)
        val priceUnit = drawer.findViewById<TextView>(R.id.transaction_price_unit)
        val feeInput = drawer.findViewById<EditText>(R.id.transaction_fee)
        val feeToggle = drawer.findViewById<android.widget.CheckBox>(R.id.transaction_fee_enabled)
        val notesInput = drawer.findViewById<EditText>(R.id.transaction_notes)
        val total = drawer.findViewById<TextView>(R.id.transaction_total)
        val saveButton = drawer.findViewById<View>(R.id.transaction_save)
        val dateFormat = SimpleDateFormat("dd MMMM yyyy", LanguageManager.locale(host.activity))

        drawer.findViewById<TextView>(R.id.transaction_price_label).text =
            host.activity.getString(if (isBuy) R.string.buy_price else R.string.sell_price)
        quantityUnit.text = quantityUnitHint(host.activity, asset.category, asset.symbol)
        priceUnit.text = priceUnitSuffix(host.activity, asset.category, asset.symbol)
        dateInput.setText(
            transaction?.let { formatTransactionDate(host.activity, it.date) }
                ?: dateFormat.format(Date())
        )
        fun selectedCurrency(): String = asset.currency
        quantityInput.setText(
            transaction?.let { formatEditableAmount(formatQuantityValue(it.quantity), "IDR") } ?: ""
        )
        priceInput.setText(transaction?.let { formatInputAmount(it.price, selectedCurrency()) } ?: "")
        feeInput.setText(
            transaction?.fee?.takeIf { it > 0.0 }?.let { formatInputAmount(it, selectedCurrency()) } ?: ""
        )
        notesInput.setText(transaction?.notes.orEmpty())
        feeToggle.isChecked = transaction?.fee?.let { it > 0.0 } ?: false
        installMoneyInputFormatter(priceInput) { selectedCurrency() }
        installMoneyInputFormatter(feeInput) { selectedCurrency() }
        installDecimalInputFormatter(quantityInput)

        fun showDatePicker() {
            val selectedDate = Calendar.getInstance().apply {
                timeInMillis = parseTransactionDate(host.activity, dateInput.text.toString())
            }
            DatePickerDialog(
                host.activity,
                { _, year, month, dayOfMonth ->
                    selectedDate.set(year, month, dayOfMonth)
                    dateInput.setText(dateFormat.format(selectedDate.time))
                },
                selectedDate.get(Calendar.YEAR),
                selectedDate.get(Calendar.MONTH),
                selectedDate.get(Calendar.DAY_OF_MONTH)
            ).show()
        }
        dateInput.setOnClickListener { showDatePicker() }

        fun updateTotal() {
            val quantity = parseTransactionQuantity(quantityInput.text.toString())
            val price = parseMoneyInput(priceInput.text.toString())
            val fee = if (feeToggle.isChecked) parseMoneyInput(feeInput.text.toString()) ?: 0.0 else 0.0
            val currency = selectedCurrency()
            total.text = if (quantity != null && price != null) {
                formatAmount(calculateTransactionTotal(isBuy, quantity, price, fee), currency)
            } else "—"
        }
        feeToggle.setOnCheckedChangeListener { _, enabled ->
            feeInput.isEnabled = enabled
            feeInput.alpha = if (enabled) 1f else 0.45f
            updateTotal()
        }
        feeInput.isEnabled = feeToggle.isChecked
        feeInput.alpha = if (feeToggle.isChecked) 1f else 0.45f
        listOf(quantityInput, priceInput, feeInput).forEach { input ->
            input.addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = updateTotal()
                override fun afterTextChanged(s: Editable?) = Unit
            })
        }
        updateTotal()

        fun updateTransactionFieldAppearance(editable: Boolean) {
            val background = ContextCompat.getDrawable(
                host.activity,
                if (editable) R.drawable.bg_input else R.drawable.bg_input_readonly
            )
            val foreground = if (editable) {
                ContextCompat.getDrawable(host.activity, R.drawable.ripple_surface)
            } else {
                null
            }
            listOf(feeInput, notesInput).forEach { input ->
                input.background = background?.constantState?.newDrawable()
                input.foreground = foreground?.constantState?.newDrawable()
            }
            listOf(quantityInput, priceInput).forEach { input ->
                val container = input.parent as? View
                container?.background = background?.constantState?.newDrawable()
                container?.foreground = foreground?.constantState?.newDrawable()
            }
        }

        saveButton.setOnClickListener {
            if (transaction == null && asset.id == 0L) {
                host.activity.showInvestaToast(host.activity.getString(R.string.save_asset_first))
                return@setOnClickListener
            }
            val quantity = parseTransactionQuantity(quantityInput.text.toString())
            val price = parseMoneyInput(priceInput.text.toString())
            val fee = if (feeToggle.isChecked) parseMoneyInput(feeInput.text.toString()) ?: 0.0 else 0.0
            val currency = selectedCurrency()
            val now = System.currentTimeMillis()
            when {
                quantity == null || quantity <= 0.0 -> quantityInput.apply {
                    error = host.activity.getString(R.string.valid_quantity); requestFocus()
                }
                price == null || price <= 0.0 -> priceInput.apply {
                    error = host.activity.getString(R.string.valid_price); requestFocus()
                }
                fee < 0.0 -> feeInput.apply {
                    error = host.activity.getString(R.string.valid_fee); requestFocus()
                }
                else -> {
                    val updatedTransaction = transaction?.copy(
                        date = parseTransactionDate(host.activity, dateInput.text.toString()),
                        quantity = quantity,
                        price = price,
                        fee = fee,
                        total = calculateTransactionTotal(isBuy, quantity, price, fee),
                        currency = currency,
                        notes = notesInput.text.toString().trim(),
                        updatedAt = now
                    )
                    if (updatedTransaction != null) {
                        host.transactionViewModel.updateTransaction(
                            transaction = updatedTransaction,
                            onSaved = {
                                host.refreshTransactions()
                                dialog.dismiss()
                                host.activity.showInvestaToast(host.activity.getString(R.string.transaction_updated))
                            },
                            onError = { message ->
                                host.activity.showInvestaToast(message)
                            }
                        )
                    } else {
                        host.transactionViewModel.addTransaction(
                            TransactionEntity(
                                assetId = asset.id,
                                action = if (isBuy) "BUY" else "SELL",
                                date = parseTransactionDate(host.activity, dateInput.text.toString()),
                                quantity = quantity,
                                price = price,
                                fee = fee,
                                total = calculateTransactionTotal(isBuy, quantity, price, fee),
                                currency = currency,
                                notes = notesInput.text.toString().trim(),
                                createdAt = now,
                                updatedAt = now
                            )
                        ,
                            onSaved = {
                                host.refreshTransactions()
                                dialog.dismiss()
                                host.activity.showInvestaToast(host.activity.getString(R.string.transaction_added))
                            },
                            onError = { message ->
                                host.activity.showInvestaToast(message)
                            }
                        )
                    }
                }
            }
        }

        if (transaction != null) {
            title.text = host.activity.getString(R.string.transaction_detail)
            moreButton.visibility = View.VISIBLE
            moreButton.setOnClickListener { showTransactionOptions(moreButton, transaction, dialog) }
            saveButton.visibility = View.GONE
            updateTransactionFieldAppearance(editable = false)
            val inputs = listOf(quantityInput, priceInput, feeInput, notesInput)
            inputs.forEach { input ->
                input.isFocusable = false
                input.isFocusableInTouchMode = false
                input.setOnClickListener {
                    updateTransactionFieldAppearance(editable = true)
                    inputs.forEach { editableInput ->
                        editableInput.isFocusable = true
                        editableInput.isFocusableInTouchMode = true
                    }
                    feeToggle.isClickable = true
                    feeToggle.isFocusable = true
                    saveButton.visibility = View.VISIBLE
                    input.requestFocus()
                }
            }
            dateInput.setOnClickListener {
                updateTransactionFieldAppearance(editable = true)
                inputs.forEach { editableInput ->
                    editableInput.isFocusable = true
                    editableInput.isFocusableInTouchMode = true
                }
                feeToggle.isClickable = true
                feeToggle.isFocusable = true
                saveButton.visibility = View.VISIBLE
                showDatePicker()
            }
            feeToggle.isClickable = false
            feeToggle.isFocusable = false
        }
        dialog.setOnShowListener {
            val bottomSheet = dialog.findViewById<FrameLayout>(com.google.android.material.R.id.design_bottom_sheet)
            bottomSheet?.setBackgroundColor(Color.TRANSPARENT)
            bottomSheet?.let { sheet ->
                val baseBottomPadding = drawer.paddingBottom
                ViewCompat.setOnApplyWindowInsetsListener(sheet) { _, insets ->
                    val navigationInset = if (android.os.Build.VERSION.SDK_INT >= 35) {
                        insets.getInsets(WindowInsetsCompat.Type.navigationBars()).bottom
                    } else {
                        0
                    }
                    drawer.setPadding(
                        drawer.paddingLeft,
                        drawer.paddingTop,
                        drawer.paddingRight,
                        baseBottomPadding + navigationInset
                    )
                    insets
                }
                ViewCompat.requestApplyInsets(sheet)
                val behavior = BottomSheetBehavior.from(sheet).apply {
                    // Open the drawer fully from the start; do not leave it in the
                    // half-expanded/collapsed position where the Save button is hidden.
                    skipCollapsed = true
                    isFitToContents = true
                    state = BottomSheetBehavior.STATE_EXPANDED
                }
                // Re-apply after measurement because Android can restore the initial
                // bottom-sheet state while the content is being laid out.
                sheet.post { behavior.state = BottomSheetBehavior.STATE_EXPANDED }
            }
        }
        dialog.show()
    }

    private fun showTransactionOptions(
        anchor: View,
        transaction: TransactionEntity,
        dialog: BottomSheetDialog
    ) {
        PopupMenu(host.activity, anchor).apply {
            menu.add(R.string.delete)
            setOnMenuItemClickListener { item ->
                if (item.itemId == 0 || item.title.toString() == host.activity.getString(R.string.delete)) {
                    confirmDeleteTransaction(transaction, dialog)
                    true
                } else {
                    false
                }
            }
        }.show()
    }

    private fun confirmDeleteTransaction(transaction: TransactionEntity, dialog: BottomSheetDialog) {
        AlertDialog.Builder(host.activity)
            .setTitle(host.activity.getString(R.string.delete_transaction))
            .setMessage(host.activity.getString(R.string.delete_transaction_message))
            .setNegativeButton(host.activity.getString(R.string.cancel), null)
            .setPositiveButton(host.activity.getString(R.string.delete)) { _, _ ->
                host.transactionViewModel.deleteTransaction(
                    transaction = transaction,
                    onDeleted = {
                        host.refreshTransactions()
                        dialog.dismiss()
                        host.activity.showInvestaToast(host.activity.getString(R.string.transaction_deleted))
                    },
                    onError = { message ->
                        host.activity.showInvestaToast(message)
                    }
                )
            }
            .show()
    }

    private fun formatQuantityValue(quantity: Double): String =
        java.math.BigDecimal.valueOf(quantity).stripTrailingZeros().toPlainString()
}
