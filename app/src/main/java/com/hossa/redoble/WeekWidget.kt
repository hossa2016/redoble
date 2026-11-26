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
import android.widget.RemoteViews
import androidx.core.content.res.ResourcesCompat

/**
 * «Mi semana»: cuánto escuchaste esta semana, tu artista y tu canción más tocada
 * (del historial de escucha, que solo vive en el teléfono). Se redibuja cada hora.
 */
class WeekWidget : AppWidgetProvider() {
    override fun onUpdate(ctx: Context, mgr: AppWidgetManager, ids: IntArray) {
        ids.forEach { try { update(ctx, mgr, it) } catch (e: Exception) { ErrorLog.soft(ctx, e, "week") } }
    }

    override fun onAppWidgetOptionsChanged(ctx: Context, mgr: AppWidgetManager, id: Int, o: android.os.Bundle) {
        try { update(ctx, mgr, id) } catch (e: Exception) { }
    }

    companion object {
        fun placed(ctx: Context) = AppWidgetManager.getInstance(ctx).getAppWidgetIds(ComponentName(ctx, WeekWidget::class.java)).isNotEmpty()

        fun updateAll(ctx: Context) {
            val mgr = AppWidgetManager.getInstance(ctx)
            for (id in mgr.getAppWidgetIds(ComponentName(ctx, WeekWidget::class.java))) {
                try { update(ctx, mgr, id) } catch (e: Exception) { }
            }
        }

        fun update(ctx: Context, mgr: AppWidgetManager, id: Int) {
            val v = RemoteViews(ctx.packageName, R.layout.widget_week)
            val dens = ctx.resources.displayMetrics.density
            val o = mgr.getAppWidgetOptions(id)
            val wDp = o.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 250).coerceIn(110, 600)
            val hDp = o.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, 120).coerceIn(60, 400)
            v.setImageViewBitmap(R.id.week_img, draw(ctx, (wDp * dens).toInt(), (hDp * dens).toInt()))
            v.setContentDescription(R.id.week_img, describe(ctx))
            // Tocar abre la pestaña Música (con los afiches para compartir)
            val open = PendingIntent.getActivity(ctx, 310, Intent(ctx, MainActivity::class.java)
                .putExtra("tab", 2).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
            v.setOnClickPendingIntent(R.id.week_root, open)
            mgr.updateAppWidget(id, v)
        }

        private fun mins(ctx: Context, m: Int) =
            if (m >= 60) "${m / 60} h ${m % 60} min" else "$m min"

        private fun describe(ctx: Context): String {
            if (!TourLog.enabled(ctx)) return I18n.tr(ctx, "Enciende el historial de escucha en Música")
            val s = TourLog.week(ctx)
            return I18n.tr(ctx, "Semana") + " ${TourLog.weekNumber()}: " + mins(ctx, s.minutes) +
                (if (s.topArtist.isNotBlank()) ", " + s.topArtist else "")
        }

        private fun draw(ctx: Context, w: Int, h: Int): Bitmap {
            val th = Themes.widget(ctx)
            val accent = th.accent(ctx)
            val spec = TextArt.spec(th.family)
            val out = Bitmap.createBitmap(w.coerceAtLeast(120), h.coerceAtLeast(80), Bitmap.Config.ARGB_8888)
            val c = Canvas(out)
            val W = out.width.toFloat(); val H = out.height.toFloat()
            val p = Paint(Paint.ANTI_ALIAS_FLAG)
            p.color = Themes.widgetColor(ctx, th); p.alpha = 0xE6
            val r = minOf(W, H) * 0.12f
            c.drawRoundRect(RectF(0f, 0f, W, H), r, r, p)
            fun font(id: Int) = try { ResourcesCompat.getFont(ctx, id) } catch (e: Exception) { null }
            val pad = minOf(W, H) * 0.11f
            val t = Paint(Paint.ANTI_ALIAS_FLAG)
            fun fit(text: String, size: Float, maxW: Float): Float {
                t.textSize = size; var s = size
                while (s > 8f && t.measureText(text) > maxW) { s -= 1f; t.textSize = s }
                return s
            }
            val loc = I18n.locale(ctx)
            val head = (I18n.tr(ctx, "MI SEMANA") + " · " + TourLog.weekNumber()).uppercase(loc)
            t.typeface = font(spec.title); t.color = accent; t.letterSpacing = 0.14f
            fit(head, H * 0.13f, W - pad * 2)
            c.drawText(head, pad, pad + t.textSize, t)
            var y = pad + t.textSize
            if (!TourLog.enabled(ctx)) {
                t.typeface = font(spec.body); t.color = th.head; t.letterSpacing = 0f
                val msg = I18n.tr(ctx, "Toca para encender el historial de escucha")
                fit(msg, H * 0.12f, W - pad * 2); y += t.textSize * 1.8f
                c.drawText(msg, pad, y, t)
                return out
            }
            val s = TourLog.week(ctx)
            t.typeface = font(spec.time); t.color = th.head; t.letterSpacing = 0f
            val big = mins(ctx, s.minutes)
            fit(big, H * 0.30f, W - pad * 2); y += t.textSize * 1.15f
            c.drawText(big, pad, y, t)
            t.typeface = font(spec.med); t.color = th.dim
            val l1 = if (s.topArtist.isNotBlank()) I18n.tr(ctx, "Más escuchado: {}").replace("{}", s.topArtist) else I18n.tr(ctx, "Aún sin música esta semana")
            fit(l1, H * 0.12f, W - pad * 2); y += t.textSize * 1.6f
            if (y < H - pad * 0.3f) c.drawText(l1, pad, y, t)
            if (s.topTrack.isNotBlank()) {
                val l2 = "♪ " + s.topTrack
                fit(l2, H * 0.11f, W - pad * 2); y += t.textSize * 1.45f
                if (y < H - pad * 0.3f) c.drawText(l2, pad, y, t)
            }
            return out
        }
    }
}
