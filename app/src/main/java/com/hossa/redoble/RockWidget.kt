package com.hossa.redoble

import android.app.AlarmManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Build
import android.util.TypedValue
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
        LiveService.start(ctx)
    }

    override fun onReceive(ctx: Context, intent: Intent) {
        super.onReceive(ctx, intent)
        when (intent.action) {
            BatteryMonitor.ACTION_TICK -> {
                BatteryMonitor.refresh(ctx)
                BatteryMonitor.check(ctx)
            }
            Player.ACTION_PLAY_PAUSE, Player.ACTION_NEXT, Player.ACTION_PREV, Player.ACTION_CLOSE ->
                Player.handle(ctx, intent.action)
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

        private fun vis(b: Boolean) = if (b) View.VISIBLE else View.GONE

        /** Tamaño base de la hora por familia de tema (sp). */
        private fun timeSize(family: String) = when (family) {
            "claro" -> 76f; "cristal" -> 76f; "neon" -> 54f; "lujo" -> 80f; else -> 64f
        }

        fun update(ctx: Context, mgr: AppWidgetManager, widgetId: Int) {
            val th = Themes.current(ctx)
            val v = RemoteViews(ctx.packageName, th.layout)
            val accent = th.accent(ctx)
            val rock = th.family == "rock"

            // Reproductor primero: de él depende cuánto espacio queda.
            val playing = Player.apply(ctx, v)

            val dia = Prefs.el(ctx, "dia"); val hora = Prefs.el(ctx, "hora")
            val clima = Prefs.el(ctx, "clima"); val manana = Prefs.el(ctx, "manana")
            val bateria = Prefs.el(ctx, "bateria")

            // Día
            val day = DAYS[Calendar.getInstance().get(Calendar.DAY_OF_WEEK) - 1]
            if (rock && dia) {
                val h = if (playing) 150 else 176
                v.setImageViewBitmap(R.id.day_img, Graphics.dayBitmap(ctx, day, accent, h))
                if (Build.VERSION.SDK_INT >= 31) v.setViewLayoutHeight(R.id.day_img, h.toFloat(), TypedValue.COMPLEX_UNIT_DIP)
            }
            v.setViewVisibility(R.id.day_img, vis(rock && dia))
            v.setViewVisibility(R.id.day_txt, vis(!rock && dia))
            if (!rock) v.setTextColor(R.id.day_txt, when (th.family) {
                "claro", "cristal" -> th.head; "neon" -> th.accent2; else -> accent
            })
            // Cristal, Neón y Lujo llevan la fecha en la misma línea del día.
            val dateInDay = th.family == "cristal" || th.family == "neon" || th.family == "lujo"
            v.setViewVisibility(R.id.date, vis(dia && !dateInDay))

            // Hora (más pequeña cuando suena música)
            v.setViewVisibility(R.id.clock, vis(hora))
            val ts = timeSize(th.family) * (if (playing) 0.875f else 1f)
            for (id in intArrayOf(R.id.hh, R.id.colon, R.id.mm)) v.setTextViewTextSize(id, TypedValue.COMPLEX_UNIT_SP, ts)
            v.setTextColor(R.id.colon, when (th.family) { "claro" -> th.head; "neon" -> th.accent2; else -> accent })

            // Clima
            val now = Prefs.weatherNow(ctx)
            val tomorrow = Prefs.weatherTomorrow(ctx)
            v.setTextViewText(R.id.wx_now, now.ifBlank { if (Prefs.city(ctx).isBlank()) "Elige tu ciudad" else "Cargando clima…" })
            v.setTextViewText(R.id.wx_tomorrow, tomorrow)
            v.setViewVisibility(R.id.wx_now, vis(clima))
            v.setViewVisibility(R.id.wx_tomorrow, vis(manana && tomorrow.isNotBlank()))
            v.setViewVisibility(R.id.batt_row, vis(bateria))
            v.setViewVisibility(R.id.status_row, vis(clima))
            v.setViewVisibility(R.id.hairline, vis((dia || hora) && (clima || bateria || manana)))
            v.setViewVisibility(R.id.header, vis(dia || hora || clima || manana || bateria))
            if (bateria) applyBattery(ctx, v)

            // Abrir ajustes desde la cabecera y la lista vacía
            val open = PendingIntent.getActivity(
                ctx, 100, Intent(ctx, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            v.setOnClickPendingIntent(R.id.header, open)
            v.setOnClickPendingIntent(R.id.day_img, open)
            v.setOnClickPendingIntent(R.id.empty, open)

            // Apps: cuántas mostrar según la distribución (con música, solo una fila)
            val wanted = Prefs.appsCount(ctx)
            // En "Completo", con música se deja una sola fila para dar espacio al
            // reproductor. Si el usuario eligió el número a mano, se respeta.
            val auto = Prefs.preset(ctx) == "completo"
            val maxApps = if (auto && playing && wanted > 3) 3 else wanted
            val pm = ctx.packageManager
            val showNums = rock
            val numColor = ColorUtils.setAlphaComponent(if (rock) accent else th.accent2, if (rock) 0x99 else 0xCC)
            val iconColor = when (th.family) { "neon" -> accent; "lujo" -> th.head; else -> th.head }
            val filled = (0 until Prefs.SLOTS)
                .filter { val a = Prefs.app(ctx, it); a.isNotBlank() && pm.getLaunchIntentForPackage(a) != null }
                .take(maxApps)
            for (i in 0 until Prefs.SLOTS) {
                val slot = filled.getOrNull(i)
                val pkg = slot?.let { Prefs.app(ctx, it) }
                if (slot == null || pkg == null) {
                    v.setViewVisibility(PADS[i], View.INVISIBLE)
                    continue
                }
                v.setViewVisibility(PADS[i], View.VISIBLE)
                v.setTextViewText(NUMS[i], String.format("%02d", i + 1))
                v.setTextColor(NUMS[i], numColor)
                v.setViewVisibility(NUMS[i], vis(showNums))
                v.setTextViewText(LABELS[i], Prefs.displayName(ctx, slot))
                Graphics.appIcon(ctx, pkg, mono = iconColor)?.let { v.setImageViewBitmap(ICONS[i], it) }
                if (Build.VERSION.SDK_INT >= 31) {
                    val big = th.family == "claro" || th.family == "lujo" || th.family == "cristal"
                    val s = if (Prefs.originalIcons(ctx)) (if (big) 34f else 30f) else (if (big) 22f else 18f)
                    v.setViewLayoutWidth(ICONS[i], s, TypedValue.COMPLEX_UNIT_DIP)
                    v.setViewLayoutHeight(ICONS[i], s, TypedValue.COMPLEX_UNIT_DIP)
                }
                val launch = pm.getLaunchIntentForPackage(pkg)!!.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                v.setOnClickPendingIntent(
                    PADS[i],
                    PendingIntent.getActivity(ctx, i, launch, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
                )
            }
            v.setViewVisibility(R.id.setlist, vis(wanted > 0))
            v.setViewVisibility(R.id.row2, vis(filled.size > 3))
            v.setViewVisibility(R.id.row1, vis(filled.isNotEmpty()))
            v.setViewVisibility(R.id.empty, vis(wanted > 0 && filled.isEmpty()))
            v.setViewVisibility(R.id.setlist_head, vis(filled.size > 3))
            val word = if (rock) (if (filled.size == 1) "pista" else "pistas") else (if (filled.size == 1) "app" else "apps")
            v.setTextViewText(R.id.count, "${filled.size} $word")

            // Si hay ciudad pero no hay dato del clima, pedirlo ya
            if (Prefs.city(ctx).isNotBlank() &&
                System.currentTimeMillis() - Prefs.weatherAt(ctx) > 45 * 60_000L) {
                WeatherWorker.runNow(ctx)
            }

            mgr.updateAppWidget(widgetId, v)
        }

        /** Medidor LED y texto de batería (también se usa en actualizaciones parciales). */
        fun applyBattery(ctx: Context, v: RemoteViews) {
            val th = Themes.current(ctx)
            val accent = th.accent(ctx)
            val level = BatteryMonitor.level(ctx)
            val charging = BatteryMonitor.isCharging(ctx)
            val off = if (th.light) Color.parseColor("#1F1C1A17") else Color.parseColor("#2A231E")
            when (th.family) {
                // Claro: barra fina; Lujo: solo texto ("BATERÍA 70 %").
                "claro" -> v.setImageViewBitmap(R.id.leds_img, Graphics.barBitmap(ctx, level, th.head, off))
                "lujo" -> v.setViewVisibility(R.id.leds_img, View.GONE)
                else -> v.setImageViewBitmap(R.id.leds_img, Graphics.ledBitmap(ctx, level, accent, charging, off))
            }
            if (charging) {
                v.setViewVisibility(R.id.batt_bolt, View.VISIBLE)
                v.setImageViewBitmap(R.id.batt_bolt, Graphics.boltBitmap(ctx, accent))
            } else {
                v.setViewVisibility(R.id.batt_bolt, View.GONE)
            }
            v.setTextViewText(R.id.batt_txt, if (th.family == "lujo") "BATERÍA $level %" else "$level %")
            v.setTextColor(R.id.batt_txt, if (!charging && level <= 15) Graphics.ALERT else th.head)
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
