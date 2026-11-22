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
        private val LABEL_BMPS = intArrayOf(R.id.label0_bmp, R.id.label1_bmp, R.id.label2_bmp, R.id.label3_bmp, R.id.label4_bmp, R.id.label5_bmp)
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

        private val ES = java.util.Locale("es", "CO")
        @Volatile private var lastPlaying = false

        /**
         * Muestra un texto como TextView (letra del sistema) o como imagen con la
         * letra del tema, según el ajuste "Letras del tema".
         */
        internal fun twin(v: RemoteViews, tv: Int, bmp: Int, visible: Boolean, fonts: Boolean, draw: () -> android.graphics.Bitmap) {
            if (!visible) { v.setViewVisibility(tv, View.GONE); v.setViewVisibility(bmp, View.GONE); return }
            if (fonts) {
                v.setViewVisibility(tv, View.GONE)
                v.setViewVisibility(bmp, View.VISIBLE)
                v.setImageViewBitmap(bmp, draw())
            } else {
                v.setViewVisibility(tv, View.VISIBLE)
                v.setViewVisibility(bmp, View.GONE)
            }
        }

        private fun widthPx(ctx: Context, mgr: AppWidgetManager, id: Int): Int {
            val dp = try { mgr.getAppWidgetOptions(id).getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH) } catch (e: Exception) { 0 }
            return ((if (dp > 0) dp else 360) * ctx.resources.displayMetrics.density).toInt()
        }

        private fun clockBitmap(ctx: Context, th: Theme, playing: Boolean): android.graphics.Bitmap {
            val s = TextArt.spec(th.family)
            val is24 = android.text.format.DateFormat.is24HourFormat(ctx)
            val hp = if (th.family == "neon") (if (is24) "HH" else "hh") else (if (is24) "H" else "h")
            val now = java.util.Date()
            val hh = java.text.SimpleDateFormat(hp, ES).format(now)
            val mm = java.text.SimpleDateFormat("mm", ES).format(now)
            val colon = when (th.family) { "claro" -> th.head; "neon" -> th.accent2; else -> th.accent(ctx) }
            return TextArt.clock(ctx, hh, mm, s, timeSize(th.family) * (if (playing) 0.875f else 1f), th.head, colon)
        }

        /** Cada minuto (desde el servicio en vivo): solo la hora dibujada. */
        fun updateClock(ctx: Context) {
            if (!Prefs.themeFonts(ctx) || !Prefs.el(ctx, "hora")) return
            val mgr = AppWidgetManager.getInstance(ctx)
            val th = Themes.current(ctx)
            for (id in mgr.getAppWidgetIds(ComponentName(ctx, RockWidget::class.java))) {
                try {
                    val v = RemoteViews(ctx.packageName, th.layout)
                    v.setImageViewBitmap(R.id.clock_bmp, clockBitmap(ctx, th, lastPlaying))
                    mgr.partiallyUpdateAppWidget(id, v)
                } catch (e: Exception) { }
            }
        }

        /** Tamaño base de la hora por familia de tema (sp). */
        private fun timeSize(family: String) = when (family) {
            "claro" -> 76f; "cristal" -> 76f; "neon" -> 54f; "lujo" -> 80f; else -> 64f
        }

        fun update(ctx: Context, mgr: AppWidgetManager, widgetId: Int) {
            val th = Themes.current(ctx)
            val v = RemoteViews(ctx.packageName, th.layout)
            val accent = th.accent(ctx)
            val rock = th.family == "rock"

            val fonts = Prefs.themeFonts(ctx)
            val S = TextArt.spec(th.family)
            val W = widthPx(ctx, mgr, widgetId)
            val dens = ctx.resources.displayMetrics.density
            val colW = W - ((if (rock) 96 else 24) * dens).toInt()

            // Reproductor primero: de él depende cuánto espacio queda.
            val playing = Player.apply(ctx, v, fonts, W)
            lastPlaying = playing

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
            val dayColor = when (th.family) { "claro", "cristal" -> th.head; "neon" -> th.accent2; else -> accent }
            if (!rock) v.setTextColor(R.id.day_txt, dayColor)
            val now = java.util.Date()
            val dayFmt = when (th.family) {
                "cristal" -> "EEEE, d 'de' MMMM"; "neon" -> "EEEE '//' dd.MM"; "lujo" -> "EEEE · d 'de' MMMM"; else -> "EEEE"
            }
            twin(v, R.id.day_txt, R.id.day_txt_bmp, !rock && dia, fonts) {
                TextArt.render(ctx, java.text.SimpleDateFormat(dayFmt, ES).format(now), S.title, 13f, dayColor,
                    S.tls, caps = true, glow = if (th.family == "neon") th.accent2 else 0, maxWidthPx = colW)
            }
            // Cristal, Neón y Lujo llevan la fecha en la misma línea del día.
            val dateInDay = th.family == "cristal" || th.family == "neon" || th.family == "lujo"
            twin(v, R.id.date, R.id.date_bmp, dia && !dateInDay, fonts) {
                TextArt.render(ctx, java.text.SimpleDateFormat("d 'de' MMMM", ES).format(now),
                    if (th.family == "lujo") S.med else S.body, S.dsize, if (th.family == "lujo") th.dim else th.head,
                    S.lls, caps = S.caps, shadow = S.shadow, maxWidthPx = colW)
            }

            // Hora (más pequeña cuando suena música)
            v.setViewVisibility(R.id.clock, vis(hora && !fonts))
            v.setViewVisibility(R.id.clock_bmp, vis(hora && fonts))
            if (hora && fonts) v.setImageViewBitmap(R.id.clock_bmp, clockBitmap(ctx, th, playing))
            val ts = timeSize(th.family) * (if (playing) 0.875f else 1f)
            for (id in intArrayOf(R.id.hh, R.id.colon, R.id.mm)) v.setTextViewTextSize(id, TypedValue.COMPLEX_UNIT_SP, ts)
            v.setTextColor(R.id.colon, when (th.family) { "claro" -> th.head; "neon" -> th.accent2; else -> accent })

            // Clima
            val tomorrow = Prefs.weatherTomorrow(ctx)
            v.setTextViewText(R.id.wx_now, now0(ctx, null))
            v.setTextViewText(R.id.wx_tomorrow, tomorrow)
            val nowTxt = now0(ctx, now)
            val wxIcon = fonts && clima && Prefs.weatherCode(ctx) >= 0 && (th.family == "cristal" || th.family == "claro")
            val tomInline = wxIcon && manana && tomorrow.isNotBlank()
            twin(v, R.id.wx_now, R.id.wx_now_bmp, clima, fonts) {
                if (wxIcon) TextArt.weather(ctx, Prefs.weatherCode(ctx), Prefs.weatherTemp(ctx), Prefs.weatherDesc(ctx),
                    if (tomInline) tomorrow else "", S, th.family == "cristal", th.head, th.dim, accent, colW)
                else TextArt.render(ctx, nowTxt, if (th.family == "lujo") S.body else S.med, S.wsize, th.head,
                    caps = th.family == "neon", shadow = S.shadow, maxWidthPx = colW)
            }
            twin(v, R.id.wx_tomorrow, R.id.wx_tomorrow_bmp, manana && tomorrow.isNotBlank() && !tomInline, fonts) {
                TextArt.render(ctx, tomorrow, if (th.family == "lujo") S.med else S.body, S.tomsize, th.dim,
                    S.lls, caps = th.family == "neon" || th.family == "lujo", shadow = S.shadow, maxWidthPx = colW)
            }
            v.setViewVisibility(R.id.batt_row, vis(bateria))
            v.setViewVisibility(R.id.status_row, vis(clima))
            v.setViewVisibility(R.id.hairline, vis((dia || hora) && (clima || bateria || manana)))
            v.setViewVisibility(R.id.header, vis(dia || hora || clima || manana || bateria))
            if (bateria) applyBattery(ctx, v, fonts)

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
                val name = Prefs.displayName(ctx, slot)
                v.setTextViewText(LABELS[i], name)
                twin(v, LABELS[i], LABEL_BMPS[i], true, fonts) {
                    TextArt.render(ctx, name, S.label, S.lsize, th.head, S.lls,
                        caps = th.family == "neon" || th.family == "lujo", shadow = S.shadow,
                        maxWidthPx = W / 3 - (10 * dens).toInt())
                }
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
            twin(v, R.id.setlist_title, R.id.setlist_title_bmp, true, fonts) {
                TextArt.render(ctx, if (rock) "SETLIST" else "APPS", S.title, if (rock) 15f else 12f, th.head, S.tls, shadow = S.shadow)
            }
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
        private fun now0(ctx: Context, d: java.util.Date?): String =
            Prefs.weatherNow(ctx).ifBlank { if (Prefs.city(ctx).isBlank()) "Elige tu ciudad" else "Cargando clima…" }

        fun applyBattery(ctx: Context, v: RemoteViews, fonts: Boolean = Prefs.themeFonts(ctx)) {
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
            val bt = if (th.family == "lujo") "BATERÍA $level %" else "$level %"
            val bc = if (!charging && level <= 15) Graphics.ALERT else th.head
            v.setTextViewText(R.id.batt_txt, bt)
            v.setTextColor(R.id.batt_txt, bc)
            val S = TextArt.spec(th.family)
            twin(v, R.id.batt_txt, R.id.batt_txt_bmp, true, fonts) {
                TextArt.render(ctx, bt, S.med, maxOf(S.wsize - 2f, 11f), bc, S.lls, shadow = S.shadow)
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
