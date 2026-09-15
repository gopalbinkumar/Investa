package com.example.investa.ui.common

import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.res.ResourcesCompat
import com.example.investa.R

/** Applies Investa's text metrics consistently to an entire screen hierarchy. */
internal fun View.disableFontPaddingRecursively() {
    if (this is TextView) {
        includeFontPadding = false
        if (tag == "investa_semibold_text") {
            typeface = ResourcesCompat.getFont(context, R.font.poppins_semibold)
        }
    }
    if (this is ViewGroup) {
        for (index in 0 until childCount) {
            getChildAt(index).disableFontPaddingRecursively()
        }
    }
}
