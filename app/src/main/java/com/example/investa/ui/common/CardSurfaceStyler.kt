package com.example.investa.ui.common

import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.LayerDrawable
import android.view.View
import android.view.ViewGroup
import android.widget.HorizontalScrollView
import android.widget.ScrollView
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.widget.NestedScrollView
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2
import com.example.investa.R
import com.google.android.material.shape.MaterialShapeDrawable
import com.google.android.material.shape.ShapeAppearanceModel

internal const val ELEVATED_CARD_TAG = "investa_elevated_card"
internal const val INPUT_FIELD_TAG = "investa_input_field"

internal fun applyElevatedCard(
    view: View,
    shadowRadiusDp: Int = 5,
    cornerRadiusDp: Int = 20
) {
    val density = view.resources.displayMetrics.density
    val shadowRadius = (shadowRadiusDp * density).toInt()
    val shape = ShapeAppearanceModel.builder()
        .setAllCornerSizes(cornerRadiusDp * density)
        .build()
    val isPortfolioCard = view.id == R.id.portfolio_card
    val shadowBackground = MaterialShapeDrawable(shape).apply {
        fillColor = ColorStateList.valueOf(
            if (isPortfolioCard) Color.TRANSPARENT else ContextCompat.getColor(
                view.context,
                R.color.investa_card_surface
            )
        )
        shadowCompatibilityMode = MaterialShapeDrawable.SHADOW_COMPAT_MODE_ALWAYS
        elevation = shadowRadius.toFloat()
        setShadowRadius(shadowRadius)
        setShadowVerticalOffset(0)
            setShadowColor(ContextCompat.getColor(view.context, R.color.investa_card_shadow))
    }

    view.background = if (isPortfolioCard) {
        val portfolioSurface = ContextCompat.getDrawable(
            view.context,
            R.drawable.bg_portfolio_card
        )?.mutate()
        if (portfolioSurface == null) {
            shadowBackground
        } else {
            LayerDrawable(arrayOf(shadowBackground, portfolioSurface))
        }
    } else {
        shadowBackground
    }
    view.clipToOutline = false
    ViewCompat.setElevation(view, 0f)
    allowShadowInsideScrollViewport(view)
}

private fun allowShadowInsideScrollViewport(view: View) {
    var ancestor = view.parent
    while (ancestor is ViewGroup) {
        if (
            ancestor is ScrollView ||
            ancestor is HorizontalScrollView ||
            ancestor is NestedScrollView ||
            ancestor is RecyclerView ||
            ancestor is ViewPager2
        ) {
            break
        }
        ancestor.clipChildren = false
        ancestor.clipToPadding = false
        ancestor = ancestor.parent
    }
}

internal fun applyElevatedCards(root: View) {
    when (root.tag) {
        ELEVATED_CARD_TAG -> applyElevatedCard(root)
        INPUT_FIELD_TAG -> {
            val paddingLeft = root.paddingLeft
            val paddingTop = root.paddingTop
            val paddingRight = root.paddingRight
            val paddingBottom = root.paddingBottom
            applyElevatedCard(root)
            root.setPadding(paddingLeft, paddingTop, paddingRight, paddingBottom)
        }
    }
    if (root is ViewGroup) {
        for (index in 0 until root.childCount) {
            applyElevatedCards(root.getChildAt(index))
        }
    }
}
