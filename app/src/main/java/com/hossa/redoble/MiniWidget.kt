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

/** Widget 1×1: día, número del día y batería, con la letra y el color del tema. */
class MiniWidget : AppWidgetProvider() {
    override fun onUpdate(ctx: Context, mgr: AppWidgetManager, ids: IntArray) {
        ids.forEach { try { update(ctx, mgr, it) } catch (e: Exception) { ErrorLog.soft(ctx, e, "mini") } }
        try { RockWidget.scheduleMidnight(ctx); BatteryMonitor.check(ctx) } catch (e: Exception) { }
    }

    companion object {
        fun placed(ctx: Context) = AppWidgetManager.getInstance(ctx).getAppWidgetIds(ComponentName(ctx, MiniWidget::class.java)).isNotEmpty()

        fun updateAll(ctx: Context) {
            val mgr = AppWidgetManager.getInstance(ctx)
            for (id in mgr.getAppWidgetIds(ComponentName(ctx, MiniWidget::class.java))) {
                try { update(ctx, mgr, id) } catch (e: Exception) { }
            }
        }

        fun update(ctx: Context, mgr: AppWidgetManager, id: Int) {
            val v = RemoteViews(ctx.packageName, R.layout.widget_mini)
            val dens = ctx.resources.displayMetrics.density
            val opts = mgr.getAppWidgetOptions(id)
            val wDp = opts.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 70).coerceIn(40, 200)
            val hDp = opts.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, 70).coerceIn(40, 200)
            val side = (minOf(wDp, hDp) * dens).toInt().coerceAtLeast(80)
            v.setImageViewBitmap(R.id.mini_img, draw(ctx, side))
            val loc = I18n.locale(ctx)
            v.setContentDescription(R.id.mini_img, java.text.SimpleDateFormat("EEEE d", loc).format(java.util.Date()) +
                ", " + I18n.tr(ctx, "Batería") + " " + BatteryMonitor.level(ctx) + " %")
            val open = PendingIntent.getActivity(ctx, 300, Intent(ctx, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
            v.setOnClickPendingIntent(R.id.mini_root, open)
            mgr.updateAppWidget(id, v)
        }

        private fun draw(ctx: Context, s: Int): Bitmap {
            val th = Themes.widget(ctx)
            val accent = th.accent(ctx)
            val spec = TextArt.spec(th.family)
            val out = Bitmap.createBitmap(s, s, Bitmap.Config.ARGB_8888)
            val c = Canvas(out)
            val p = Paint(Paint.ANTI_ALIAS_FLAG)
            // Tarjeta del color del widget (para leerse sobre cualquier fondo)
            p.color = Themes.widgetColor(ctx, th); p.alpha = 0xE6
            c.drawRoundRect(RectF(0f, 0f, s.toFloat(), s.toFloat()), s * 0.22f, s * 0.22f, p)
            val loc = I18n.locale(ctx)
            val now = java.util.Date()
            fun font(id: Int) = try { ResourcesCompat.getFont(ctx, id) } catch (e: Exception) { null }
            val t = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER }
            t.typeface = font(spec.title); t.color = accent; t.textSize = s * 0.16f; t.letterSpacing = 0.12f
            c.drawText(java.text.SimpleDateFormat("EEE", loc).format(now).replace(".", "").uppercase(loc), s / 2f, s * 0.27f, t)
            t.typeface = font(spec.time); t.color = th.head; t.textSize = s * 0.42f; t.letterSpacing = 0f
            c.drawText(java.text.SimpleDateFormat("d", loc).format(now), s / 2f, s * 0.66f, t)
            val lvl = BatteryMonitor.level(ctx)
            t.typeface = font(spec.med); t.color = th.dim; t.textSize = s * 0.14f
            c.drawText("$lvl %" + if (BatteryMonitor.isCharging(ctx)) " ⚡" else "", s / 2f, s * 0.86f, t)
            return out
        }
    }
}
