package com.example.investa.utils

import android.content.Context
import android.util.TypedValue
import androidx.annotation.AttrRes
import androidx.annotation.ColorInt
import androidx.core.content.ContextCompat

@ColorInt
internal fun Context.themeColor(@AttrRes attribute: Int): Int {
    val value = TypedValue()
    if (!theme.resolveAttribute(attribute, value, true)) {
        error("Theme attribute $attribute is not defined")
    }
    return if (value.resourceId != 0) {
        ContextCompat.getColor(this, value.resourceId)
    } else {
        value.data
    }
}
