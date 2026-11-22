package com.hossa.redoble

import android.animation.LayoutTransition
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout

// Secciones plegables: tocar el título abre o cierra la tarjeta.
// Cerradas muestran solo el título y un resumen (por ejemplo, el tema elegido).

internal fun MainActivity.fold(card: View, key: String, summary: String? = null): View {
    val c = card as? LinearLayout ?: return card
    val title = c.getChildAt(0) as? LinearLayout ?: return card
    if (summary != null && title.findViewWithTag<View>("hint") == null) {
        title.addView(ui.text(summary, ui.cond, 14f, Ui.DIM).apply { tag = "hint"; maxLines = 1 })
    }
    val chev = ui.text("›", ui.condLight, 30f, Ui.MUTED).apply {
        gravity = Gravity.CENTER
        includeFontPadding = false
    }
    title.addView(chev, LinearLayout.LayoutParams(ui.dp(32), ui.dp(32)).apply { marginStart = ui.dp(6) })
    title.minimumHeight = ui.dp(48)
    title.isClickable = true
    title.background = ui.rowBg()

    // Lo que ya estaba oculto dentro de la sección sigue oculto al abrirla
    val wasHidden = HashSet<View>()
    for (i in 1 until c.childCount) c.getChildAt(i).let { if (it.visibility != View.VISIBLE) wasHidden.add(it) }

    fun show(open: Boolean, animate: Boolean) {
        for (i in 1 until c.childCount) {
            val v = c.getChildAt(i)
            if (open) {
                v.visibility = if (v in wasHidden) View.GONE else View.VISIBLE
            } else {
                if (v.visibility == View.VISIBLE) wasHidden.remove(v) else wasHidden.add(v)
                v.visibility = View.GONE
            }
        }
        (title.layoutParams as? LinearLayout.LayoutParams)?.let {
            it.bottomMargin = if (open) ui.dp(14) else 0
            title.layoutParams = it
        }
        val angle = if (open) -90f else 90f
        if (animate) chev.animate().rotation(angle).setDuration(180).start() else chev.rotation = angle
        val name = (title.getChildAt(1) as? android.widget.TextView)?.text ?: ""
        title.contentDescription = "$name, " + if (open) "abierto, toca para cerrar" else "cerrado, toca para abrir"
    }

    var open = key in openSections
    if (!open) show(false, false) else show(true, false)
    c.layoutTransition = LayoutTransition().apply { setDuration(180) }
    title.setOnClickListener {
        open = !open
        if (open) openSections.add(key) else openSections.remove(key)
        Prefs.setOpenSections(this, openSections)
        show(open, true)
    }
    return c
}
