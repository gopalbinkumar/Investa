package com.example.investa.utils

import android.view.ViewGroup
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.FrameLayout
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.widget.NestedScrollView

internal fun View.enableImeScrolling() {
    val basePaddingBottom = paddingBottom
    ViewCompat.setOnApplyWindowInsetsListener(this) { view, insets ->
        val imeBottom = insets.getInsets(WindowInsetsCompat.Type.ime()).bottom
        val navigationBottom = insets.getInsets(WindowInsetsCompat.Type.navigationBars()).bottom
        val extraImePadding = (imeBottom - navigationBottom).coerceAtLeast(0)
        view.setPadding(
            view.paddingLeft,
            view.paddingTop,
            view.paddingRight,
            basePaddingBottom + extraImePadding
        )
        insets
    }
    ViewCompat.requestApplyInsets(this)
}

internal fun View.wrapInImeScrollView(): NestedScrollView =
    NestedScrollView(context).apply {
        layoutParams = ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
        isVerticalScrollBarEnabled = false
        isHorizontalScrollBarEnabled = false
        isFillViewport = false
        clipToPadding = false
        overScrollMode = View.OVER_SCROLL_NEVER
        addView(
            this@wrapInImeScrollView,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        )
        enableImeScrolling()
    }

fun View.hideInvestaKeyboard() {
    val inputMethodManager = context.getSystemService(InputMethodManager::class.java)
    clearFocus()
    post {
        inputMethodManager?.hideSoftInputFromWindow(windowToken, InputMethodManager.HIDE_NOT_ALWAYS)
        inputMethodManager?.hideSoftInputFromWindow(rootView.windowToken, InputMethodManager.HIDE_NOT_ALWAYS)
    }
}
