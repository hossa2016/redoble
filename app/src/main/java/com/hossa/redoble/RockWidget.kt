package com.hossa.redoble

import android.app.AlarmManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.BatteryManager
import android.view.View
import android.widget.RemoteViews
import java.util.Calendar

class RockWidget : AppWidgetProvider() {

    override fun onUpdate(ctx: Context, mgr: AppWidgetManager, ids: IntArray) {
        for (id in ids) update(ctx, mgr, id)
        scheduleMidnight(ctx)
        WeatherWorker.schedule(ctx)
    }

    override fun onReceive(ctx: Context, intent: Intent) {
        super.onReceive(ctx, intent)
        when (intent.action) {
            ACTION_REFRESH,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED -> {
                updateAll(ctx)
                scheduleMidnight(ctx)
            }
        }
    }

    companion object {
        const val ACTION_REFRESH = "com.hossa.redoble.REFRESH"

        private val DAYS = arrayOf(
            "DOMINGO", "LUNES", "MARTES", "MIÉRCOLES", "JUEVES", "VIERNES", "SÁBADO"
        )

        private val ROW_IDS = intArrayOf(R.id.app0, R.id.app1, R.id.app2, R.id.app3, R.id.app4, R.id.app5)
        private val ICON_IDS = intArrayOf(R.id.icon0, R.id.icon1, R.id.icon2, R.id.icon3, R.id.icon4, R.id.icon5)
        private val LABEL_IDS = intArrayOf(R.id.label0, R.id.label1, R.id.label2, R.id.label3, R.id.label4, R.id.label5)

        fun updateAll(ctx: Context) {
            val mgr = AppWidgetManager.getInstance(ctx)
            val ids = mgr.getAppWidgetIds(ComponentName(ctx, RockWidget::class.java))
            for (id in ids) update(ctx, mgr, id)
        }

        fun update(ctx: Context, mgr: AppWidgetManager, widgetId: Int) {
            val views = RemoteViews(ctx.packageName, R.layout.widget_rock)
            val accent = Prefs.accent(ctx)

            // Día en vertical
            val day = DAYS[Calendar.getInstance().get(Calendar.DAY_OF_WEEK) - 1]
            views.setImageViewBitmap(R.id.day, Graphics.verticalDay(ctx, day, Prefs.LIGHT))

            // Hora y acento
            views.setTextColor(R.id.clock, accent)
            views.setInt(R.id.divider, "setColorFilter", accent)

            // Clima y batería
            val bm = ctx.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
            val battery = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
            val lines = mutableListOf<String>()
            val weather = Prefs.weather(ctx)
            if (weather.isNotBlank()) lines.add(weather)
            if (battery in 0..100) lines.add("Batería $battery%")
            views.setTextViewText(R.id.info, lines.joinToString("\n"))

            // Tocar la cabecera abre la configuración
            val open = PendingIntent.getActivity(
                ctx, 100, Intent(ctx, MainActivity::class.java),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            views.setOnClickPendingIntent(R.id.header, open)

            // Lista de apps
            val pm = ctx.packageManager
            for (i in 0 until Prefs.SLOTS) {
                val pkg = Prefs.app(ctx, i)
                val launch = if (pkg.isNotBlank()) pm.getLaunchIntentForPackage(pkg) else null
                if (launch == null) {
                    views.setViewVisibility(ROW_IDS[i], View.GONE)
                    continue
                }
                views.setViewVisibility(ROW_IDS[i], View.VISIBLE)
                val label = try {
                    pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
                } catch (e: Exception) {
                    pkg
                }
                views.setTextViewText(LABEL_IDS[i], label)
                Graphics.tintedIcon(ctx, pkg, Prefs.SILVER)?.let {
                    views.setImageViewBitmap(ICON_IDS[i], it)
                }
                launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                val pi = PendingIntent.getActivity(
                    ctx, i, launch,
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
                )
                views.setOnClickPendingIntent(ROW_IDS[i], pi)
            }

            mgr.updateAppWidget(widgetId, views)
        }

        /** Programa una actualización justo después de medianoche para cambiar el día. */
        fun scheduleMidnight(ctx: Context) {
            val am = ctx.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val cal = Calendar.getInstance().apply {
                add(Calendar.DAY_OF_YEAR, 1)
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 5)
                set(Calendar.MILLISECOND, 0)
            }
            val pi = PendingIntent.getBroadcast(
                ctx, 0,
                Intent(ctx, RockWidget::class.java).setAction(ACTION_REFRESH),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            am.setWindow(AlarmManager.RTC, cal.timeInMillis, 60_000L, pi)
        }
    }
}
