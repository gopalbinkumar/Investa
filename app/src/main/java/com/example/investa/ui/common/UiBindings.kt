package com.example.investa.ui.common

import android.content.Context
import android.content.res.ColorStateList
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.example.investa.R
import com.example.investa.model.Asset
import com.example.investa.utils.localizedCategory
import com.example.investa.utils.formatQuantityForCard

internal fun addAssetRow(
    context: Context,
    parent: ViewGroup,
    asset: Asset,
    compact: Boolean,
    percentage: String? = null,
    onClick: (() -> Unit)? = null
) {
    val row = LayoutInflater.from(context).inflate(R.layout.asset_item, parent, false)
    bindAsset(row, asset, compact, percentage)
    if (onClick != null) row.setOnClickListener { onClick() } else row.setOnClickListener(null)
    row.isClickable = onClick != null
    parent.addView(row)
    applyElevatedCard(row)
}

internal fun bindAsset(
    row: View,
    asset: Asset,
    compact: Boolean,
    percentage: String? = null,
    showCategory: Boolean = true
) {
    // RecyclerView/ViewPager2 rows are inflated after the screen root, so apply
    // the global text metric rule here as well.
    row.disableFontPaddingRecursively()
    row.findViewById<TextView>(R.id.asset_name).text = asset.name
    row.findViewById<TextView>(R.id.asset_symbol).text = asset.symbol
    val cardQuantity = formatQuantityForCard(asset.quantity)
    val categoryView = row.findViewById<TextView>(R.id.asset_category)
    categoryView.text =
        if (showCategory) {
            "${localizedCategory(row.context, asset.category)} · $cardQuantity"
        } else {
            cardQuantity
        }
    row.findViewById<TextView>(R.id.asset_value).text = asset.value
    row.findViewById<TextView>(R.id.asset_profit).apply {
        val displayedPercentage = percentage ?: asset.profitPercent
        text = displayedPercentage
        if (compact) {
            setTextColor(ContextCompat.getColor(
                row.context,
                if (displayedPercentage.trimStart().startsWith("-")) {
                    R.color.investa_loss
                } else {
                    R.color.investa_profit
                }
            ))
        }
    }
    row.findViewById<View>(R.id.asset_summary_container).visibility =
        if (compact) View.VISIBLE else View.GONE
    row.findViewById<ImageView>(R.id.asset_chevron).visibility =
        if (compact) View.GONE else View.VISIBLE
    categoryView.visibility =
        if (compact) View.GONE else View.VISIBLE
}

internal fun bindLegendRows(legend: ViewGroup, values: List<Pair<String, Int>>) {
    val rows = (0 until legend.childCount).map { legend.getChildAt(it) }
    rows.forEachIndexed { index, row ->
        if (index < values.size) {
            val (label, percent) = values[index]
            row.findViewById<TextView>(R.id.legend_label).text = label
            row.findViewById<TextView>(R.id.legend_percent).text = "${percent}%"
            tint(row.findViewById(R.id.legend_dot), chartColor(index))
            (row.layoutParams as? LinearLayout.LayoutParams)?.let { params ->
                params.bottomMargin = if (index == values.lastIndex) 0 else row.context.resources
                    .getDimensionPixelSize(R.dimen.allocation_legend_row_spacing)
                row.layoutParams = params
            }
        }
    }
}

internal fun addReportSummaryRow(
    context: Context,
    summary: ViewGroup,
    label: String,
    amount: String,
    percentage: String,
    onClick: (() -> Unit)? = null
) {
    val row = LayoutInflater.from(context)
        .inflate(R.layout.view_category_summary_row, summary, false)
    row.findViewById<TextView>(R.id.summary_category).text = label
    row.findViewById<TextView>(R.id.summary_amount).text = amount
    row.findViewById<TextView>(R.id.summary_percentage).text = percentage
    row.isClickable = onClick != null
    row.isFocusable = onClick != null
    row.setOnClickListener { onClick?.invoke() }
    summary.addView(row)
}

internal fun setReportToggle(
    context: Context,
    category: TextView,
    asset: TextView,
    categorySelected: Boolean
) {
    if (categorySelected) category.setBackgroundResource(R.drawable.bg_primary_button) else category.background = null
    if (categorySelected) asset.background = null else asset.setBackgroundResource(R.drawable.bg_primary_button)
    category.setTextColor(
        ContextCompat.getColor(
            context,
            if (categorySelected) R.color.investa_background else R.color.investa_text_secondary
        )
    )
    asset.setTextColor(
        ContextCompat.getColor(
            context,
            if (categorySelected) R.color.investa_text_secondary else R.color.investa_background
        )
    )
}

internal fun setLoadingState(
    button: View,
    icon: ImageView,
    progress: ProgressBar,
    loading: Boolean
) {
    button.isEnabled = !loading
    icon.visibility = if (loading) View.GONE else View.VISIBLE
    progress.visibility = if (loading) View.VISIBLE else View.GONE
}

internal fun tint(view: View, color: Int) {
    view.backgroundTintList = ColorStateList.valueOf(color)
}

internal fun chartColor(index: Int) = intArrayOf(
    0xFFFF6B6B.toInt(), // Coral / Red
    0xFFFFB547.toInt(), // Amber
    0xFF4DD0B5.toInt(), // Mint / Green
    0xFFF15BB5.toInt(), // Pink
    0xFF4D96FF.toInt(), // Blue
    0xFF9B6DFF.toInt() // Purple
)[index % 6]
