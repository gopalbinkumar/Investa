package com.example.investa

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
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val diameter = minOf(width, height).toFloat() - 24f
        val left = (width - diameter) / 2f
        val top = (height - diameter) / 2f
        bounds.set(left, top, left + diameter, top + diameter)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 28f
        var start = -90f
        floatArrayOf(55f, 18f, 10f, 6f, 6f, 5f).forEachIndexed { index, percent ->
            paint.color = chartColors[index]
            canvas.drawArc(bounds, start, percent * 3.6f - 2f, false, paint)
            start += percent * 3.6f
        }
    }
}

class PerformanceChartView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) : View(context, attrs) {
    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF408A71.toInt(); style = Paint.Style.STROKE; strokeWidth = 5f; strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND }
    private val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFB0E4CC.toInt(); style = Paint.Style.FILL }
    private val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x7331564A; style = Paint.Style.STROKE; strokeWidth = 1f }
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val values = floatArrayOf(.27f, .34f, .31f, .48f, .46f, .66f, .82f)
        for (row in 0..2) { val y = height * row / 2f; canvas.drawLine(0f, y, width.toFloat(), y, gridPaint) }
        val path = Path()
        values.forEachIndexed { index, value -> val x = index * width.toFloat() / (values.size - 1); val y = height * (1f - value); if (index == 0) path.moveTo(x, y) else path.lineTo(x, y); canvas.drawCircle(x, y, 4.5f, dotPaint) }
        canvas.drawPath(path, linePaint)
    }
}
