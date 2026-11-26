package com.hossa.redoble

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.os.Bundle
import android.widget.RemoteViews
import androidx.core.content.res.ResourcesCompat

/**
 * Pila inteligente: un solo lugar que muestra lo que importa ahora.
 *  · Si suena música → el reproductor
 *  · Si tienes un evento en las próximas 3 horas → el evento
 *  · Si no → la hora y el clima
 */
class SmartWidget : AppWidgetProvider() {
    override fun onUpdate(ctx: Context, mgr: AppWidgetManager, ids: IntArray) {
        ids.forEach { try { update(ctx, mgr, it) } catch (e: Exception) { ErrorLog.soft(ctx, e, "smart") } }
    }
    override fun onAppWidgetOptionsChanged(ctx: Context, mgr: AppWidgetManager, id: Int, o: Bundle) {
        try { update(ctx, mgr, id) } catch (e: Exception) { }
    }

    companion object {
        fun placed(ctx: Context) = AppWidgetManager.getInstance(ctx).getAppWidgetIds(ComponentName(ctx, SmartWidget::class.java)).isNotEmpty()

        fun updateAll(ctx: Context) {
            val mgr = AppWidgetManager.getInstance(ctx)
            for (id in mgr.getAppWidgetIds(ComponentName(ctx, SmartWidget::class.java))) {
                try { update(ctx, mgr, id) } catch (e: Exception) { }
            }
        }

        /** Qué mostrar ahora: "music", "event" o "clock". */
        fun mode(ctx: Context): String = when {
            Player.nowTrack(ctx) != null && Player.playingNow(ctx) -> "music"
            Extras.minutesToNextEvent(ctx) in 0..180 -> "event"
            else -> "clock"
        }

        fun update(ctx: Context, mgr: AppWidgetManager, id: Int) {
            val (w, h) = Widgets.sizeDp(ctx, mgr, id)
            val dens = ctx.resources.displayMetrics.density
            val v = when (mode(ctx)) {
                "music" -> Widgets.playerViews(ctx, w, h)
                "event" -> eventViews(ctx, (w * dens).toInt(), (h * dens).toInt())
                else -> RockWidget.buildViews(ctx, (w * dens).toInt(), kind = Widgets.CLOCK, hDp = h)
            }
            mgr.updateAppWidget(id, v)
        }

        private fun eventViews(ctx: Context, w: Int, h: Int): RemoteViews {
            val v = RemoteViews(ctx.packageName, R.layout.widget_week)
            val txt = Extras.nextEvent(ctx)
            v.setImageViewBitmap(R.id.week_img, draw(ctx, w, h, txt))
            v.setContentDescription(R.id.week_img, I18n.tr(ctx, "Próximo evento") + ": " + txt)
            val open = PendingIntent.getActivity(ctx, 320, Intent(Intent.ACTION_VIEW,
                android.provider.CalendarContract.CONTENT_URI.buildUpon().appendPath("time")
                    .appendPath(System.currentTimeMillis().toString()).build()).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
            v.setOnClickPendingIntent(R.id.week_root, open)
            return v
        }

        private fun draw(ctx: Context, w: Int, h: Int, event: String): Bitmap {
            val th = Themes.widget(ctx)
            val spec = TextArt.spec(th.family)
            val out = Bitmap.createBitmap(w.coerceAtLeast(120), h.coerceAtLeast(80), Bitmap.Config.ARGB_8888)
            val c = Canvas(out); val W = out.width.toFloat(); val H = out.height.toFloat()
            val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Themes.widgetColor(ctx, th); alpha = 0xE6 }
            val r = minOf(W, H) * 0.12f
            c.drawRoundRect(RectF(0f, 0f, W, H), r, r, p)
            fun font(id: Int) = try { ResourcesCompat.getFont(ctx, id) } catch (e: Exception) { null }
            val pad = minOf(W, H) * 0.11f
            val t = Paint(Paint.ANTI_ALIAS_FLAG)
            fun fit(s: String, size: Float) { t.textSize = size; while (t.textSize > 8f && t.measureText(s) > W - pad * 2) t.textSize -= 1f }
            val head = I18n.tr(ctx, "PRÓXIMO EVENTO")
            t.typeface = font(spec.title); t.color = th.accent(ctx); t.letterSpacing = 0.14f
            fit(head, H * 0.13f); var y = pad + t.textSize
            c.drawText(head, pad, y, t)
            val title = event.substringBefore(" · ")
            val time = event.substringAfter(" · ", "")
            t.typeface = font(spec.time); t.color = th.head; t.letterSpacing = 0f
            fit(time, H * 0.30f); y += t.textSize * 1.15f
            c.drawText(time, pad, y, t)
            t.typeface = font(spec.med); t.color = th.dim
            fit(title, H * 0.14f); y += t.textSize * 1.5f
            if (y < H - pad * 0.3f) c.drawText(title, pad, y, t)
            return out
        }
    }
}
