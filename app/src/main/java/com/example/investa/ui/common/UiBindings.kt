package com.example.investa.ui.common

import android.content.Context
import android.content.res.ColorStateList
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.example.investa.R
import com.example.investa.model.Asset
import com.example.investa.utils.localizedCategory

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
}

internal fun bindAsset(row: View, asset: Asset, compact: Boolean, percentage: String? = null) {
    row.findViewById<TextView>(R.id.asset_name).text = asset.name
    row.findViewById<TextView>(R.id.asset_symbol).text = asset.symbol
    row.findViewById<TextView>(R.id.asset_category).text = localizedCategory(row.context, asset.category)
    row.findViewById<TextView>(R.id.asset_quantity).text = asset.quantity
    row.findViewById<TextView>(R.id.asset_value).text = asset.value
    row.findViewById<TextView>(R.id.asset_profit).text = percentage ?: asset.profitPercent
    row.findViewById<View>(R.id.asset_summary_container).visibility =
        if (compact) View.VISIBLE else View.GONE
    row.findViewById<ImageView>(R.id.asset_chevron).visibility =
        if (compact) View.GONE else View.VISIBLE
    if (compact) {
        row.findViewById<TextView>(R.id.asset_category).visibility = View.GONE
        row.findViewById<TextView>(R.id.asset_quantity).visibility = View.GONE
    }
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
    category.setTextColor(ContextCompat.getColor(
        context,
        if (categorySelected) R.color.investa_background else R.color.investa_text_secondary
    ))
    asset.setTextColor(ContextCompat.getColor(
        context,
        if (categorySelected) R.color.investa_text_secondary else R.color.investa_background
    ))
}

internal fun tint(view: View, color: Int) {
    view.backgroundTintList = ColorStateList.valueOf(color)
}

private fun chartColor(index: Int) = intArrayOf(
    0xFFFF9F43.toInt(), // Orange
    0xFFFF6B6B.toInt(), // Soft red
    0xFFFFD166.toInt(), // Yellow
    0xFF4D96FF.toInt(), // Blue
    0xFFA66CFF.toInt(), // Purple
    0xFF00C2A8.toInt()  // Teal
)[index % 6]
