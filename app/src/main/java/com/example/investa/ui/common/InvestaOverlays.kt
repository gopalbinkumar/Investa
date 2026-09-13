package com.example.investa.ui.common

import android.app.Dialog
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.PopupWindow
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.core.content.ContextCompat
import com.example.investa.R

internal data class InvestaPopupOption(
    val label: String,
    val destructive: Boolean = false,
    val onClick: () -> Unit
)

internal fun showInvestaPopup(
    anchor: View,
    options: List<InvestaPopupOption>
): PopupWindow {
    val context = anchor.context
    val density = context.resources.displayMetrics.density
    fun dp(value: Int): Int = (value * density).toInt()

    val container = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        setBackgroundResource(R.drawable.bg_dialog_surface)
        setPadding(0, 0, 0, 0)
    }
    val popup = PopupWindow(
        container,
        dp(180),
        ViewGroup.LayoutParams.WRAP_CONTENT,
        true
    ).apply {
        setBackgroundDrawable(ContextCompat.getDrawable(context, R.drawable.bg_dialog_surface))
        isOutsideTouchable = true
        elevation = dp(8).toFloat()
    }

    options.forEach { option ->
        val item = TextView(context).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(40)
            )
            isClickable = true
            isFocusable = true
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(16), 0, dp(16), 0)
            includeFontPadding = false
            text = option.label
            textSize = 14f
            setTextColor(
                ContextCompat.getColor(
                    context,
                    if (option.destructive) R.color.investa_loss else R.color.investa_text
                )
            )
            foreground = ContextCompat.getDrawable(context, R.drawable.ripple_surface)
        }
        item.setOnClickListener {
            popup.dismiss()
            option.onClick()
        }
        container.addView(item)
    }
    container.disableFontPaddingRecursively()
    popup.showAsDropDown(anchor, anchor.width - dp(180), dp(4))
    return popup
}

internal fun showInvestaConfirmationDialog(
    activity: ComponentActivity,
    title: String,
    message: String,
    onConfirm: () -> Unit
): Dialog {
    val dialog = Dialog(activity)
    val dialogView = activity.layoutInflater
        .inflate(R.layout.dialog_confirm_delete, null, false)
    dialogView.disableFontPaddingRecursively()
    dialogView.findViewById<TextView>(R.id.delete_dialog_title).text = title
    dialogView.findViewById<TextView>(R.id.delete_dialog_message).text = message
    dialogView.findViewById<View>(R.id.delete_dialog_cancel).setOnClickListener {
        dialog.dismiss()
    }
    dialogView.findViewById<View>(R.id.delete_dialog_confirm).setOnClickListener {
        onConfirm()
        dialog.dismiss()
    }
    dialog.setContentView(dialogView)
    dialog.setCanceledOnTouchOutside(true)
    dialog.show()
    dialog.window?.apply {
        val density = activity.resources.displayMetrics.density
        setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        addFlags(android.view.WindowManager.LayoutParams.FLAG_DIM_BEHIND)
        attributes = attributes.apply { dimAmount = 0.45f }
        setLayout(
            (activity.resources.displayMetrics.widthPixels - (48 * density).toInt())
                .coerceAtLeast((240 * density).toInt()),
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
    }
    return dialog
}
