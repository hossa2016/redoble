package com.hossa.redoble

import android.content.Context
import android.content.Intent
import android.os.PowerManager
import android.widget.LinearLayout

/**
 * Modo confiable: detecta si el widget se congeló (HyperOS/MIUI cierra apps en segundo
 * plano) y guía para que no vuelva a pasar. El widget también se repara solo cada vez
 * que corre la tarea del clima.
 */
object Reliable {
    fun minutesAgo(ctx: Context): Long {
        val t = Prefs.lastDraw(ctx)
        return if (t == 0L) -1 else (System.currentTimeMillis() - t) / 60_000L
    }
    fun stale(ctx: Context) = minutesAgo(ctx) > 30
    fun batteryFree(ctx: Context) = try {
        (ctx.getSystemService(Context.POWER_SERVICE) as PowerManager).isIgnoringBatteryOptimizations(ctx.packageName)
    } catch (e: Exception) { true }
    fun label(ctx: Context): String {
        val m = minutesAgo(ctx)
        return when {
            m < 0 -> I18n.tr(ctx, "Widget activo")
            m < 2 -> I18n.tr(ctx, "Widget al día")
            m < 60 -> I18n.tr(ctx, "Widget al día · hace {} min").replace("{}", m.toString())
            else -> I18n.tr(ctx, "Widget al día · hace {} h").replace("{}", (m / 60).toString())
        }
    }
}

internal fun MainActivity.showReliable() {
    RockWidget.updateAll(this)  // de una vez lo pone al día
    val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
    val bat = Reliable.batteryFree(this)
    fun line(ok: Boolean, text: String) = box.addView(ui.text((if (ok) "✓  " else "✗  ") + I18n.tr(this, text), ui.cond, 16f,
        if (ok) Ui.OK else Ui.ALERT), LinearLayout.LayoutParams(-1, -2).apply { topMargin = ui.dp(8) })
    line(true, "Widget redibujado ahora mismo")
    try {
        val mgr = android.appwidget.AppWidgetManager.getInstance(this)
        mgr.getAppWidgetIds(android.content.ComponentName(this, RockWidget::class.java)).firstOrNull()?.let { id ->
            val (w, h) = Widgets.sizeDp(this, mgr, id)
            box.addView(ui.text(I18n.tr(this, "Tamaño del Completo: {} dp").replace("{}", "$w × $h"), ui.cond, 14f, Ui.DIM),
                LinearLayout.LayoutParams(-1, -2).apply { topMargin = ui.dp(8) })
        }
    } catch (e: Exception) { }
    line(bat, if (bat) "Batería: sin restricciones" else "Batería: con restricciones (puede congelarse)")
    box.addView(ui.note("En Xiaomi también activa «Inicio automático». Si el widget se queda en otra hora, Prisma lo repara solo cada media hora.", sp = 14f),
        LinearLayout.LayoutParams(-1, -2).apply { topMargin = ui.dp(12) })
    ui.sheet("Modo confiable", null, content = box, actions = listOfNotNull(
        ("Batería sin restricciones" to {
            try { startActivity(Intent(android.provider.Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)) } catch (e: Exception) { }
        }).takeIf { !bat },
        "Inicio automático y pasos" to { showKeepAliveSteps() }), dismissLabel = "Listo")
    build()
}
