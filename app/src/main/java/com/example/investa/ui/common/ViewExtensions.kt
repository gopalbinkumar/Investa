package com.example.investa.ui.common

import android.view.View
import android.view.ViewGroup
import android.widget.TextView

/** Applies Investa's text metrics consistently to an entire screen hierarchy. */
internal fun View.disableFontPaddingRecursively() {
    if (this is TextView) {
        includeFontPadding = false
    }
    if (this is ViewGroup) {
        for (index in 0 until childCount) {
            getChildAt(index).disableFontPaddingRecursively()
        }
    }
}
