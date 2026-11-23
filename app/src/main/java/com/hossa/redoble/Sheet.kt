package com.hossa.redoble

import android.app.Dialog
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.ScrollView

/**
 * Hoja inferior propia de Prisma (en vez de las ventanas del sistema, que HyperOS pinta
 * a su manera). Sube desde abajo, con el estilo de las tarjetas de la app.
 *
 * - [items]: lista para elegir una opción (se cierra al tocar).
 * - [actions]: botones; el primero es el principal.
 */
fun Ui.sheet(
    title: String?,
    message: CharSequence? = null,
    content: View? = null,
    items: List<String>? = null,
    onItem: ((Int) -> Unit)? = null,
    actions: List<Pair<String, () -> Unit>> = emptyList(),
    dismissLabel: String? = "Cancelar",
): Dialog {
    val d = Dialog(ctx, android.R.style.Theme_DeviceDefault_NoActionBar)
    val col = LinearLayout(ctx).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(22), dp(10), dp(22), dp(18))
    }
    // Asa para indicar que es una hoja
    col.addView(View(ctx).apply { background = rect(Ui.veil(0x40), 3f) },
        LinearLayout.LayoutParams(dp(36), dp(4)).apply { gravity = Gravity.CENTER_HORIZONTAL; bottomMargin = dp(14) })
    if (title != null) col.addView(text(title, condSemi, 21f, Ui.HEAD).apply { fitText(this, 21, 15) },
        LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(8) })
    if (message != null) col.addView(bodyText(message, Ui.MUTED, 16f).apply { setLineSpacing(0f, 1.15f) },
        LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(10) })
    if (content != null) {
        (content.parent as? ViewGroup)?.removeView(content)
        col.addView(content, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(8) })
    }
    items?.forEachIndexed { i, label ->
        col.addView(text(label, cond, 18f, Ui.HEAD).apply {
            gravity = Gravity.CENTER_VERTICAL
            minHeight = dp(52)
            setPadding(dp(12), 0, dp(12), 0)
            background = rowBg()
            isClickable = true
            setOnClickListener {
                performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                d.dismiss(); onItem?.invoke(i)
            }
        }, LinearLayout.LayoutParams(-1, -2))
    }
    actions.forEachIndexed { i, (label, act) ->
        val b = if (i == 0) primary(label) else secondary(label)
        b.setOnClickListener { d.dismiss(); act() }
        col.addView(b, LinearLayout.LayoutParams(-1, dp(48)).apply { topMargin = dp(if (i == 0) 10 else 8) })
    }
    if (dismissLabel != null) {
        val b = textButton(dismissLabel)
        b.setOnClickListener { d.dismiss() }
        col.addView(b, LinearLayout.LayoutParams(-1, dp(48)).apply { topMargin = dp(6) })
    }
    val box = ScrollView(ctx).apply {
        isFillViewport = false
        background = GradientDrawable().apply {
            setColor(Ui.PLATE_TOP)
            val r = dp(24).toFloat()
            cornerRadii = floatArrayOf(r, r, r, r, 0f, 0f, 0f, 0f)
        }
        addView(col)
    }
    d.setContentView(box)
    d.setCanceledOnTouchOutside(true)
    d.window?.apply {
        setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        setGravity(Gravity.BOTTOM)
        setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.WRAP_CONTENT)
        addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
        setDimAmount(0.55f)
        setWindowAnimations(android.R.style.Animation_InputMethod)
        setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
    }
    d.show()
    return d
}

/** Vibración corta al elegir algo (interruptores, segmentos, secciones). */
fun View.tick() {
    performHapticFeedback(
        if (android.os.Build.VERSION.SDK_INT >= 30) HapticFeedbackConstants.CONFIRM else HapticFeedbackConstants.VIRTUAL_KEY)
}
