package com.example.investa.utils

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.widget.Toast

private const val INVESTA_TOAST_DURATION_MS = 1000L

fun Context.showInvestaToast(message: CharSequence) {
    val toast = Toast.makeText(this, message, Toast.LENGTH_SHORT)
    toast.show()
    Handler(Looper.getMainLooper()).postDelayed({ toast.cancel() }, INVESTA_TOAST_DURATION_MS)
}
