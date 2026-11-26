package com.hossa.redoble

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import android.widget.RemoteViews

/**
 * Completo alto sin reproductor (no suena nada o lo cerraste con la X): en vez de un hueco,
 * una tarjeta con el próximo evento de tu agenda o, si no hay, el pronóstico de 3 días.
 */
object FullExtra {
    fun apply(ctx: Context, v: RemoteViews, th: Theme, W: Int, hDp: Int, preview: Boolean) {
        // Solo si de verdad sobra sitio (la tarjeta mide unos 100 dp)
        if (hDp <= 0 || RockWidget.fullFree(ctx, hDp) + 76 < 150 && !RockWidget.tallFull(ctx, hDp, (W / ctx.resources.displayMetrics.density).toInt())) return
        val d = ctx.resources.displayMetrics.density
        val w = (W - 36 * d).toInt().coerceAtLeast((160 * d).toInt())
        val event = if (Prefs.showAgenda(ctx) && Extras.hasCalendar(ctx)) Extras.nextTimedEvent(ctx) else ""
        val bmp = if (event.isNotBlank()) eventCard(ctx, th, event, w) else forecastCard(ctx, th, w) ?: return
        v.setImageViewBitmap(R.id.extra_card, bmp)
        v.setContentDescription(R.id.extra_card, if (event.isNotBlank()) I18n.tr(ctx, "Próximo evento") + ": " + event
            else I18n.tr(ctx, "Pronóstico de 3 días"))
        v.setViewVisibility(R.id.extra_card, View.VISIBLE)
        v.setViewVisibility(R.id.middle, View.VISIBLE)
        if (!preview) v.setOnClickPendingIntent(R.id.extra_card, PendingIntent.getActivity(ctx, 360,
            Intent(ctx, MainActivity::class.java).putExtra("tab", 1).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT))
    }

    private fun eventCard(ctx: Context, th: Theme, event: String, w: Int): Bitmap {
        val d = ctx.resources.displayMetrics.density
        val S = TextArt.spec(th.family)
        val pad = (16 * d).toInt()
        val title = event.substringBeforeLast(" · ")
        val time = event.substringAfterLast(" · ", "")
        val eye = TextArt.render(ctx, I18n.tr(ctx, "PRÓXIMO EVENTO"), S.title, 11f, th.accent(ctx), 0.12f, shadow = S.shadow, maxWidthPx = w - 2 * pad)
        val t = TextArt.render(ctx, title, S.semi, 20f, th.head, shadow = S.shadow, maxWidthPx = w - 2 * pad)
        val tm = TextArt.render(ctx, time, S.body, 15f, th.dim, shadow = S.shadow, maxWidthPx = w - 2 * pad)
        val h = pad + eye.height + (6 * d).toInt() + t.height + (2 * d).toInt() + tm.height + pad
        val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        Canvas(out).apply {
            var y = pad.toFloat()
            drawBitmap(eye, pad.toFloat(), y, null); y += eye.height + 6 * d
            drawBitmap(t, pad.toFloat(), y, null); y += t.height + 2 * d
            drawBitmap(tm, pad.toFloat(), y, null)
        }
        return out
    }

    private fun forecastCard(ctx: Context, th: Theme, w: Int): Bitmap? {
        val days = Prefs.forecast(ctx).split("|").mapNotNull { it.split(";").takeIf { p -> p.size == 4 } }.take(3)
        if (days.size < 2) return null
        val d = ctx.resources.displayMetrics.density
        val S = TextArt.spec(th.family)
        val pad = (14 * d).toInt()
        val col = w / days.size
        val iconPx = (30 * d).toInt()
        val parts = days.map { p ->
            Triple(TextArt.render(ctx, I18n.tr(ctx, p[0]), S.semi, 14f, th.head, shadow = S.shadow, maxWidthPx = col - 8),
                TextArt.icon(p[1].toIntOrNull() ?: 0, iconPx, th.head, th.accent(ctx)),
                TextArt.render(ctx, "${p[2]}°–${p[3]}°", S.body, 14f, th.dim, shadow = S.shadow, maxWidthPx = col - 8))
        }
        val h = pad + parts.maxOf { it.first.height } + (6 * d).toInt() + iconPx + (6 * d).toInt() + parts.maxOf { it.third.height } + pad
        val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        Canvas(out).apply {
            parts.forEachIndexed { i, (lab, ic, tmp) ->
                val cx = col * i + col / 2f
                var y = pad.toFloat()
                drawBitmap(lab, cx - lab.width / 2f, y, null); y += lab.height + 6 * d
                drawBitmap(ic, cx - ic.width / 2f, y, null); y += ic.height + 6 * d
                drawBitmap(tmp, cx - tmp.width / 2f, y, null)
            }
        }
        return out
    }
}
