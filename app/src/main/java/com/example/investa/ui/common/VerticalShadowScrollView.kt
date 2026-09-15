package com.example.investa.ui.common

import android.content.Context
import android.graphics.Canvas
import android.util.AttributeSet
import android.view.View
import android.widget.ScrollView

/**
 * Keeps form content inside its vertical viewport while leaving a small horizontal
 * gutter for elevated field shadows.
 */
class VerticalShadowScrollView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : ScrollView(context, attrs, defStyleAttr) {

    private val horizontalShadowGutter = 8 * resources.displayMetrics.density

    init {
        clipChildren = false
        clipToPadding = false
    }

    override fun drawChild(canvas: Canvas, child: View, drawingTime: Long): Boolean {
        val saveCount = canvas.save()
        canvas.clipRect(
            scrollX - horizontalShadowGutter,
            scrollY.toFloat(),
            scrollX + width + horizontalShadowGutter,
            (scrollY + height).toFloat()
        )
        val wasDrawn = super.drawChild(canvas, child, drawingTime)
        canvas.restoreToCount(saveCount)
        return wasDrawn
    }
}
