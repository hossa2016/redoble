package com.hossa.redoble

import android.app.AlarmManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.view.View
import android.widget.RemoteViews
import androidx.core.graphics.ColorUtils
import java.util.Calendar

class RockWidget : AppWidgetProvider() {

    override fun onUpdate(ctx: Context, mgr: AppWidgetManager, ids: IntArray) {
        for (id in ids) update(ctx, mgr, id)
        scheduleMidnight(ctx)
        WeatherWorker.schedule(ctx)
        BatteryMonitor.check(ctx)
    }

    override fun onReceive(ctx: Context, intent: Intent) {
        super.onReceive(ctx, intent)
        when (intent.action) {
            BatteryMonitor.ACTION_TICK -> {
                BatteryMonitor.refresh(ctx)
                BatteryMonitor.check(ctx)
            }
            ACTION_REFRESH, Intent.ACTION_TIME_CHANGED, Intent.ACTION_TIMEZONE_CHANGED -> {
                updateAll(ctx)
                scheduleMidnight(ctx)
            }
        }
    }

    companion object {
        const val ACTION_REFRESH = "com.hossa.redoble.REFRESH"

        val DAYS = arrayOf("Domingo", "Lunes", "Martes", "Miércoles", "Jueves", "Viernes", "Sábado")

        private val PADS = intArrayOf(R.id.pad0, R.id.pad1, R.id.pad2, R.id.pad3, R.id.pad4, R.id.pad5)
        private val NUMS = intArrayOf(R.id.num0, R.id.num1, R.id.num2, R.id.num3, R.id.num4, R.id.num5)
        private val ICONS = intArrayOf(R.id.icon0, R.id.icon1, R.id.icon2, R.id.icon3, R.id.icon4, R.id.icon5)
        private val LABELS = intArrayOf(R.id.label0, R.id.label1, R.id.label2, R.id.label3, R.id.label4, R.id.label5)

        fun hasWidgets(ctx: Context): Boolean =
            AppWidgetManager.getInstance(ctx)
                .getAppWidgetIds(ComponentName(ctx, RockWidget::class.java)).isNotEmpty()

        fun updateAll(ctx: Context) {
            val mgr = AppWidgetManager.getInstance(ctx)
            for (id in mgr.getAppWidgetIds(ComponentName(ctx, RockWidget::class.java))) {
                try { update(ctx, mgr, id) } catch (e: Exception) { }
            }
        }

        fun update(ctx: Context, mgr: AppWidgetManager, widgetId: Int) {
            val v = RemoteViews(ctx.packageName, R.layout.widget_rock)
            val accent = Prefs.accent(ctx)

            val day = DAYS[Calendar.getInstance().get(Calendar.DAY_OF_WEEK) - 1]
            v.setImageViewBitmap(R.id.day_img, Graphics.dayBitmap(ctx, day, accent))

            v.setTextColor(R.id.colon, accent)

            // Clima
            val now = Prefs.weatherNow(ctx)
            val tomorrow = Prefs.weatherTomorrow(ctx)
            v.setTextViewText(R.id.wx_now, now.ifBlank { if (Prefs.city(ctx).isBlank()) "Elige tu ciudad" else "Cargando clima…" })
            v.setTextViewText(R.id.wx_tomorrow, tomorrow)
            v.setViewVisibility(R.id.wx_tomorrow, if (tomorrow.isBlank()) View.GONE else View.VISIBLE)

            applyBattery(ctx, v)

            // Abrir ajustes desde la cabecera, el día y la lista vacía
            val open = PendingIntent.getActivity(
                ctx, 100, Intent(ctx, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            v.setOnClickPendingIntent(R.id.header, open)
            v.setOnClickPendingIntent(R.id.day_img, open)
            v.setOnClickPendingIntent(R.id.empty, open)

            // Pads: se llenan en orden con las apps elegidas
            val pm = ctx.packageManager
            val numColor = ColorUtils.setAlphaComponent(accent, 0xD9)
            val filled = (0 until Prefs.SLOTS).map { Prefs.app(ctx, it) }
                .filter { it.isNotBlank() && pm.getLaunchIntentForPackage(it) != null }
            for (i in 0 until Prefs.SLOTS) {
                val pkg = filled.getOrNull(i)
                if (pkg == null) {
                    v.setViewVisibility(PADS[i], View.INVISIBLE)
                    continue
                }
                v.setViewVisibility(PADS[i], View.VISIBLE)
                v.setTextViewText(NUMS[i], String.format("%02d", i + 1))
                v.setTextColor(NUMS[i], numColor)
                v.setTextViewText(LABELS[i], Apps.label(ctx, pkg))
                Graphics.monoIcon(ctx, pkg)?.let { v.setImageViewBitmap(ICONS[i], it) }
                val launch = pm.getLaunchIntentForPackage(pkg)!!.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                v.setOnClickPendingIntent(
                    PADS[i],
                    PendingIntent.getActivity(ctx, i, launch, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
                )
            }
            v.setViewVisibility(R.id.row2, if (filled.size > 3) View.VISIBLE else View.GONE)
            v.setViewVisibility(R.id.row1, if (filled.isEmpty()) View.GONE else View.VISIBLE)
            v.setViewVisibility(R.id.empty, if (filled.isEmpty()) View.VISIBLE else View.GONE)
            v.setTextViewText(R.id.count, if (filled.size == 1) "1 pista" else "${filled.size} pistas")

            // Si hay ciudad pero no hay dato del clima, pedirlo ya
            if (Prefs.city(ctx).isNotBlank() &&
                System.currentTimeMillis() - Prefs.weatherAt(ctx) > 45 * 60_000L) {
                WeatherWorker.runNow(ctx)
            }

            mgr.updateAppWidget(widgetId, v)
        }

        /** Medidor LED y texto de batería (también se usa en actualizaciones parciales). */
        fun applyBattery(ctx: Context, v: RemoteViews) {
            val accent = Prefs.accent(ctx)
            val level = BatteryMonitor.level(ctx)
            val charging = BatteryMonitor.isCharging(ctx)
            v.setImageViewBitmap(R.id.leds_img, Graphics.ledBitmap(ctx, level, accent, charging))
            when {
                charging -> {
                    v.setTextViewText(R.id.batt_txt, "$level % · cargando")
                    v.setTextColor(R.id.batt_txt, Graphics.HEAD)
                }
                level <= 15 -> {
                    v.setTextViewText(R.id.batt_txt, "Batería baja, $level %")
                    v.setTextColor(R.id.batt_txt, Graphics.ALERT)
                }
                else -> {
                    v.setTextViewText(R.id.batt_txt, "$level %")
                    v.setTextColor(R.id.batt_txt, Graphics.HEAD)
                }
            }
        }

        fun scheduleMidnight(ctx: Context) {
            val am = ctx.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val cal = Calendar.getInstance().apply {
                add(Calendar.DAY_OF_YEAR, 1)
                set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 5); set(Calendar.MILLISECOND, 0)
            }
            val pi = PendingIntent.getBroadcast(
                ctx, 0, Intent(ctx, RockWidget::class.java).setAction(ACTION_REFRESH),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            am.setWindow(AlarmManager.RTC, cal.timeInMillis, 60_000L, pi)
        }
    }
}
