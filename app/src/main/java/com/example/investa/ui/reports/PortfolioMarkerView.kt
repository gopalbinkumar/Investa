package com.example.investa.ui.reports

import android.content.Context
import android.widget.TextView
import com.example.investa.R
import com.example.investa.ui.common.applyElevatedCards
import com.example.investa.ui.common.disableFontPaddingRecursively
import com.example.investa.data.repository.PortfolioPerformancePoint
import com.example.investa.utils.formatAmount
import com.github.mikephil.charting.components.MarkerView
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.highlight.Highlight
import com.github.mikephil.charting.utils.MPPointF
import kotlin.math.roundToInt

internal class PortfolioMarkerView(
    context: Context,
    private val labels: List<String>,
    private val points: List<PortfolioPerformancePoint>,
    private val currency: String
) : MarkerView(context, R.layout.view_portfolio_marker) {

    init {
        disableFontPaddingRecursively()
        applyElevatedCards(this)
    }

    private var chartWidth = 0f
    private var chartHeight = 0f

    private val dateView = findViewById<TextView>(R.id.marker_date)
    private val currentView = findViewById<TextView>(R.id.marker_current)
    private val investedView = findViewById<TextView>(R.id.marker_invested)

    override fun refreshContent(e: Entry?, highlight: Highlight?) {
        if (labels.isEmpty()) return
        val index = e?.x?.roundToInt()?.coerceIn(0, labels.lastIndex) ?: return
        dateView.text = labels[index]
        points.getOrNull(index)?.let { point ->
            currentView.text = context.getString(R.string.current_val_marker, formatAmount(point.current, currency, 0))
            investedView.text = context.getString(R.string.invested_marker, formatAmount(point.invested, currency, 0))
        }
        super.refreshContent(e, highlight)
    }

    fun updateChartBounds(width: Int, height: Int) {
        chartWidth = width.toFloat()
        chartHeight = height.toFloat()
    }

    override fun getOffset(): MPPointF = MPPointF(-width / 2f, -height.toFloat() - 12f)

    override fun getOffsetForDrawingAtPoint(posX: Float, posY: Float): MPPointF {
        val offset = getOffset()
        val markerWidth = width.toFloat()
        val markerHeight = height.toFloat()
        val availableWidth = chartWidth.takeIf { it > 0f } ?: return offset
        val availableHeight = chartHeight.takeIf { it > 0f } ?: return offset

        var offsetX = offset.x
        var offsetY = offset.y
        if (posX + offsetX < 0f) {
            offsetX = -posX
        } else if (posX + offsetX + markerWidth > availableWidth) {
            offsetX = availableWidth - posX - markerWidth
        }
        if (posY + offsetY < 0f) {
            offsetY = -posY
        } else if (posY + offsetY + markerHeight > availableHeight) {
            offsetY = availableHeight - posY - markerHeight
        }
        return MPPointF(offsetX, offsetY)
    }
}
