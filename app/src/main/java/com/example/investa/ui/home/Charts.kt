package com.example.investa.ui.home

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View

private val chartColors = intArrayOf(
    0xFFFF9F43.toInt(),
    0xFFFF6B6B.toInt(),
    0xFFFFD166.toInt(),
    0xFF4D96FF.toInt(),
    0xFFA66CFF.toInt(),
    0xFF00C2A8.toInt()
)

class DonutChartView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) : View(context, attrs) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val bounds = RectF()
    private var allocation = FloatArray(0)

    fun setAllocationPercentages(percentages: List<Float>) {
        allocation = percentages.toFloatArray()
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val diameter = minOf(width, height).toFloat() - 24f
        val left = (width - diameter) / 2f
        val top = (height - diameter) / 2f
        bounds.set(left, top, left + diameter, top + diameter)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 28f
        var start = -90f
        allocation.forEachIndexed { index, percent ->
            if (percent > 0f) {
                paint.color = chartColors[index % chartColors.size]
                canvas.drawArc(bounds, start, (percent * 3.6f - 2f).coerceAtLeast(0f), false, paint)
                start += percent * 3.6f
            }
        }
    }
}

class PerformanceChartView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) : View(context, attrs) {
    private val investedLinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFFFD166.toInt(); style = Paint.Style.STROKE; strokeWidth = 5f; strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND }
    private val currentLinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF40A2D8.toInt(); style = Paint.Style.STROKE; strokeWidth = 5f; strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND }
    private val investedDotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFFFD166.toInt(); style = Paint.Style.FILL }
    private val currentDotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF40A2D8.toInt(); style = Paint.Style.FILL }
    private val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x7331564A; style = Paint.Style.STROKE; strokeWidth = 1f }
    private var investedValues = emptyList<Long>()
    private var currentValues = emptyList<Long>()

    fun setPerformanceData(invested: List<Long>, current: List<Long>) {
        investedValues = invested
        currentValues = current
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        for (row in 0..2) { val y = height * row / 2f; canvas.drawLine(0f, y, width.toFloat(), y, gridPaint) }
        val values = investedValues + currentValues
        if (values.isEmpty()) return
        val minValue = values.minOrNull() ?: 0L
        val maxValue = values.maxOrNull() ?: 0L
        val range = (maxValue - minValue).toFloat()

        fun drawSeries(series: List<Long>, linePaint: Paint, dotPaint: Paint) {
            if (series.isEmpty()) return
            val path = Path()
            series.forEachIndexed { index, value ->
                val x = if (series.size == 1) width / 2f else {
                    index * width.toFloat() / (series.size - 1)
                }
                val normalized = if (range == 0f) 0.5f else {
                    (value - minValue) / range
                }
                val y = height * (1f - normalized)
                if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
                canvas.drawCircle(x, y, 4.5f, dotPaint)
            }
            canvas.drawPath(path, linePaint)
        }

        drawSeries(investedValues, investedLinePaint, investedDotPaint)
        drawSeries(currentValues, currentLinePaint, currentDotPaint)
    }
}
