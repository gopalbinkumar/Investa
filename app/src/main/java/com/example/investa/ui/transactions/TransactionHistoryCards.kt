package com.example.investa.ui.transactions

import android.view.LayoutInflater
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.example.investa.R
import com.example.investa.data.entity.TransactionEntity
import com.example.investa.model.Asset
import com.example.investa.navigation.ScreenHost
import com.example.investa.ui.common.applyElevatedCard
import com.example.investa.ui.common.disableFontPaddingRecursively
import com.example.investa.utils.currencySymbolFor
import com.example.investa.utils.formatAmount
import com.example.investa.utils.formatTransactionDate
import com.example.investa.utils.formatQuantityForCard
import com.example.investa.utils.formatQuantityValue
import com.example.investa.utils.quantityUnitHint

internal fun bindTransactionHistoryCards(
    host: ScreenHost,
    historyContainer: LinearLayout,
    transactions: List<TransactionEntity>,
    assetForTransaction: (TransactionEntity) -> Asset?,
    onTransactionClick: (Asset, TransactionEntity) -> Unit,
    clearExisting: Boolean = true,
    emptyTitleRes: Int = R.string.no_transactions_found,
    emptyMessageRes: Int = R.string.add_buy_sell_transaction
) {
    if (clearExisting) {
        historyContainer.removeAllViews()
    }
    if (transactions.isEmpty() && clearExisting) {
        val empty = LayoutInflater.from(host.activity)
            .inflate(R.layout.view_empty_state, historyContainer, false)
        empty.disableFontPaddingRecursively()
        empty.findViewById<TextView>(R.id.empty_title).text =
            host.activity.getString(emptyTitleRes)
        empty.findViewById<TextView>(R.id.empty_message).text =
            host.activity.getString(emptyMessageRes)
        historyContainer.addView(empty)
        return
    }

    transactions.forEach { transaction ->
        val asset = assetForTransaction(transaction)
        val action = transaction.action.trim().uppercase()
        val actionLabel = host.activity.getString(
            if (action == "BUY") R.string.buy else R.string.sell
        )
        val card = LayoutInflater.from(host.activity)
            .inflate(R.layout.view_transaction_history_card, historyContainer, false)
        card.disableFontPaddingRecursively()
        card.findViewById<TextView>(R.id.history_type).apply {
            text = asset?.symbol
                ?.takeIf { it.isNotBlank() }
                ?.let { "$actionLabel $it" }
                ?: actionLabel
            setTextColor(
                ContextCompat.getColor(
                    host.activity,
                    if (transaction.action.equals("BUY", ignoreCase = true)) {
                        R.color.investa_profit
                    } else {
                        R.color.investa_loss
                    }
                )
            )
        }
        card.findViewById<TextView>(R.id.history_date).text =
            formatTransactionDate(host.activity, transaction.date)
        val quantityUnit = asset?.let {
            quantityUnitHint(host.activity, it.category, it.symbol)
        } ?: "—"
        card.findViewById<TextView>(R.id.history_quantity).text =
            formatQuantityForCard("${formatQuantityValue(transaction.quantity)} $quantityUnit")
        card.findViewById<TextView>(R.id.history_price_label).text =
            host.activity.getString(R.string.history_price, actionLabel)
        card.findViewById<TextView>(R.id.history_price).text = formatAmount(
            transaction.price,
            transaction.currency,
            host.databaseCurrencies.firstOrNull { it.code == transaction.currency }?.symbol
                ?: currencySymbolFor(transaction.currency),
            2
        )

        if (asset != null) {
            card.setOnClickListener { onTransactionClick(asset, transaction) }
        }
        historyContainer.addView(card)
        applyElevatedCard(card)
    }
}
