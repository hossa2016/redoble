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

    /** Al estirarlo o achicarlo se vuelve a armar a su nuevo tamaño (ancho y alto). */
    override fun onAppWidgetOptionsChanged(ctx: Context, mgr: AppWidgetManager, id: Int, o: android.os.Bundle) {
        try { update(ctx, mgr, id) } catch (e: Exception) { }
    }

    override fun onUpdate(ctx: Context, mgr: AppWidgetManager, ids: IntArray) {
        for (id in ids) {
            try { update(ctx, mgr, id) } catch (e: Exception) { }
        }
        try {
            scheduleMidnight(ctx)
            WeatherWorker.schedule(ctx)
            BatteryMonitor.check(ctx)
        } catch (e: Exception) { }
        LiveService.start(ctx)
    }

    /**
     * Este receptor es público (Android lo exige para los widgets), así que solo
     * atiende avisos del sistema. Los botones y alarmas propios van a [ActionReceiver].
     */
    override fun onReceive(ctx: Context, intent: Intent) {
        super.onReceive(ctx, intent)
        when (intent.action) {
            Intent.ACTION_TIME_CHANGED, Intent.ACTION_TIMEZONE_CHANGED -> {
                updateAll(ctx)
                scheduleMidnight(ctx)
            }
        }
    }

    companion object {
        const val ACTION_REFRESH = "com.hossa.redoble.REFRESH"
        const val ACTION_CLOCK_CHECK = "com.hossa.redoble.CLOCK_CHECK"

        /** Intención hacia el receptor privado (no exportado) de la app. */
        fun internal(ctx: Context, action: String): Intent =
            Intent(ctx, ActionReceiver::class.java).setAction(action)

        val DAYS = arrayOf("Domingo", "Lunes", "Martes", "Miércoles", "Jueves", "Viernes", "Sábado")

        // Lugares en el orden en que se ven: fila 1 = 0,1,2,(6) · fila 2 = 3,4,5,(7).
        // Con 3 por fila, los lugares 6 y 7 se ocultan y los demás se reparten el ancho.
        private val ORDER3 = intArrayOf(0, 1, 2, 3, 4, 5)
        private val ORDER4 = intArrayOf(0, 1, 2, 6, 3, 4, 5, 7)
        private val PADS = intArrayOf(R.id.pad0, R.id.pad1, R.id.pad2, R.id.pad3, R.id.pad4, R.id.pad5, R.id.pad6, R.id.pad7)
        private val NUMS = intArrayOf(R.id.num0, R.id.num1, R.id.num2, R.id.num3, R.id.num4, R.id.num5, R.id.num6, R.id.num7)
        private val ICONS = intArrayOf(R.id.icon0, R.id.icon1, R.id.icon2, R.id.icon3, R.id.icon4, R.id.icon5, R.id.icon6, R.id.icon7)
        private val LABEL_BMPS = intArrayOf(R.id.label0_bmp, R.id.label1_bmp, R.id.label2_bmp, R.id.label3_bmp, R.id.label4_bmp, R.id.label5_bmp, R.id.label6_bmp, R.id.label7_bmp)
        private val LABELS = intArrayOf(R.id.label0, R.id.label1, R.id.label2, R.id.label3, R.id.label4, R.id.label5, R.id.label6, R.id.label7)

        /** ¿Hay algún widget de Prisma puesto (el Completo o una de las piezas)? */
        fun hasWidgets(ctx: Context): Boolean =
            intArrayOf(Widgets.FULL, Widgets.CLOCK, Widgets.APPS, Widgets.PLAYER).any { Widgets.has(ctx, it) } ||
                listOf(MiniWidget::class.java, WeekWidget::class.java, SmartWidget::class.java, AgendaWidget::class.java).any {
                    try { AppWidgetManager.getInstance(ctx).getAppWidgetIds(ComponentName(ctx, it)).isNotEmpty() } catch (e: Exception) { false }
                }

        /** Alto real (dp) del primer widget Completo, para que las vistas previas sean fieles. */
        fun fullHeightDp(ctx: Context): Int = try {
            val mgr = AppWidgetManager.getInstance(ctx)
            mgr.getAppWidgetIds(ComponentName(ctx, RockWidget::class.java)).firstOrNull()?.let { Widgets.sizeDp(ctx, mgr, it).second } ?: 0
        } catch (e: Exception) { 0 }

        /** Redibuja todo. Sincronizado: lo llaman el hilo principal y WorkManager. */
        @Synchronized
        fun updateAll(ctx: Context) {
            val mgr = AppWidgetManager.getInstance(ctx)
            var ok = false; var any = false
            for (id in mgr.getAppWidgetIds(ComponentName(ctx, RockWidget::class.java))) {
                any = true
                try { update(ctx, mgr, id); ok = true } catch (e: Exception) { }
            }
            Widgets.updatePieces(ctx)
            MiniWidget.updateAll(ctx)
            WeekWidget.updateAll(ctx)
            SmartWidget.updateAll(ctx)
            AgendaWidget.updateAll(ctx)
            if (ok || !any) Prefs.setLastDraw(ctx)
        }

        private val mainH = android.os.Handler(android.os.Looper.getMainLooper())
        @Volatile private var appCtx: Context? = null
        @Volatile private var pendingWhileOff = false
        @Volatile private var pendingFull = false
        private val debounced = Runnable {
            val c = appCtx ?: return@Runnable
            if (pendingFull) { pendingFull = false; updateAll(c) } else updatePlayer(c)
        }

        private fun screenOn(ctx: Context) = try {
            (ctx.getSystemService(Context.POWER_SERVICE) as android.os.PowerManager).isInteractive
        } catch (e: Exception) { true }

        /**
         * Pide un redibujo agrupando cambios seguidos (reproductor). Con la pantalla
         * apagada no redibuja: lo deja pendiente para cuando se encienda (si el
         * servicio en vivo está activo para avisar).
         */
        fun requestUpdate(ctx: Context, delayMs: Long = 600, playerOnly: Boolean = false) {
            val app = ctx.applicationContext
            if (!screenOn(app) && LiveService.running) { pendingWhileOff = true; return }
            if (!playerOnly) pendingFull = true
            appCtx = app
            mainH.removeCallbacks(debounced)
            mainH.postDelayed(debounced, delayMs)
        }

        /** Al encender la pantalla: hora al día y lo que quedó pendiente. */
        fun onScreenOn(ctx: Context) {
            Rules.check(ctx)
            if (pendingWhileOff) { pendingWhileOff = false; updateAll(ctx) } else updateClock(ctx)
        }

        private fun vis(b: Boolean) = if (b) View.VISIBLE else View.GONE

        private val ES = java.util.Locale("es", "CO")

        /**
         * Muestra un texto como TextView (letra del sistema) o como imagen con la
         * letra del tema, según el ajuste "Letras del tema".
         */
        internal fun twin(v: RemoteViews, tv: Int, bmp: Int, visible: Boolean, fonts: Boolean, desc: String? = null,
                          draw: () -> android.graphics.Bitmap) {
            if (!visible) { v.setViewVisibility(tv, View.GONE); v.setViewVisibility(bmp, View.GONE); return }
            if (fonts) {
                v.setViewVisibility(tv, View.GONE)
                v.setViewVisibility(bmp, View.VISIBLE)
                v.setImageViewBitmap(bmp, draw())
                // TalkBack lee el texto aunque esté dibujado como imagen
                if (desc != null) v.setContentDescription(bmp, desc)
            } else {
                v.setViewVisibility(tv, View.VISIBLE)
                v.setViewVisibility(bmp, View.GONE)
            }
        }

        internal fun widthPx(ctx: Context, mgr: AppWidgetManager, id: Int): Int {
            // El ancho real del widget (antes se medía siempre como si ocupara toda la pantalla)
            val dp = Widgets.sizeDp(ctx, mgr, id).first
            val dm = ctx.resources.displayMetrics
            val screen = minOf(dm.widthPixels, dm.heightPixels) - (16 * dm.density).toInt()
            return if (dp > 0) minOf((dp * dm.density).toInt(), screen) else screen
        }

        private fun clockBitmap(ctx: Context, th: Theme, playing: Boolean, size: Float = 0f, full: Boolean = false): android.graphics.Bitmap {
            val font = if (full) Prefs.partFont(ctx, "hora") else "tema"
            val s = if (font == "tema") TextArt.spec(th.family) else TextArt.specFor(th.family, font)
            val k = if (full) Prefs.partScale(ctx, "hora") else 1f
            val ink = (if (full) Prefs.partColor(ctx, "hora") else 0).takeIf { it != 0 } ?: th.head
            val is24 = android.text.format.DateFormat.is24HourFormat(ctx)
            val hp = if (th.family == "neon") (if (is24) "HH" else "hh") else (if (is24) "H" else "h")
            val now = java.util.Date()
            val hh = java.text.SimpleDateFormat(hp, ES).format(now)
            val mm = java.text.SimpleDateFormat("mm", ES).format(now)
            val colon = when (th.family) { "claro" -> th.head; "neon" -> th.accent2; else -> th.accent(ctx) }
            val sz = (if (size > 0f) size else timeSize(th.family) * (if (playing) 0.875f else 1f)) * k
            return TextArt.clock(ctx, hh, mm, s, sz, ink, colon)
        }

        /**
         * Hora del widget Hora y clima: a su tamaño normal, pero más pequeña si el widget
         * es angosto o muy bajito, para que nunca se corte.
         */
        internal fun soloTimeSize(ctx: Context, family: String, wDp: Int, hDp: Int): Float {
            val fs = ctx.resources.configuration.fontScale.coerceIn(0.8f, 1.6f)
            val base = timeSize(family)
            val colW = wDp - (if (family == "rock" && wDp >= 230) 96 else 24)
            val byW = colW / (2.4f * fs)
            val byH = if (hDp in 1..109) (hDp - 16) / (1.05f * fs) else base
            return minOf(base, byW, byH).coerceAtLeast(24f)
        }

        /**
         * La hora dibujada con la letra del tema solo se usa mientras el servicio en
         * vivo está activo (es quien la avanza cada minuto). Si no, se usa el reloj
         * del sistema, que avanza solo: así la hora nunca se queda congelada.
         */
        private fun bitmapClock(ctx: Context) =
            Prefs.themeFonts(ctx) && Prefs.themeClock(ctx) && Prefs.liveUpdates(ctx) && LiveService.running

        /** Día y fecha como imagen solo si la hora también va con la letra del tema. */
        private fun bitmapDate(ctx: Context) = Prefs.themeFonts(ctx) && Prefs.themeClock(ctx) && Prefs.liveUpdates(ctx)

        /** Cada minuto (desde el servicio en vivo): solo la hora dibujada. */
        @Synchronized
        fun updateClock(ctx: Context) {
            if (!bitmapClock(ctx)) return
            // La alarma de respaldo se vuelve a armar siempre, aunque la pantalla esté apagada
            armClockCheck(ctx)
            // La Pila inteligente con la hora de la letra del tema también va al minuto
            try { if (screenOn(ctx) && SmartWidget.placed(ctx) && SmartWidget.mode(ctx) == "clock") SmartWidget.updateAll(ctx) } catch (e: Exception) { }
            if (!(Prefs.el(ctx, "hora") || Widgets.has(ctx, Widgets.CLOCK)) || !screenOn(ctx)) return
            Prefs.setLastDraw(ctx)
            val mgr = AppWidgetManager.getInstance(ctx)
            val th = Themes.widget(ctx)
            val playing = Prefs.lastPlaying(ctx)
            for (id in mgr.getAppWidgetIds(ComponentName(ctx, RockWidget::class.java))) {
                try {
                    val v = RemoteViews(ctx.packageName, th.layout)
                    v.setImageViewBitmap(R.id.clock_bmp, clockBitmap(ctx, th, playing, full = true))
                    mgr.partiallyUpdateAppWidget(id, v)
                } catch (e: Exception) { }
            }
            // Widget Hora y clima: la hora al tamaño que cabe en cada copia
            for (id in Widgets.ids(ctx, Widgets.CLOCK)) {
                try {
                    val v = RemoteViews(ctx.packageName, th.layout)
                    val (w, h) = Widgets.sizeDp(ctx, mgr, id)
                    v.setImageViewBitmap(R.id.clock_bmp, clockBitmap(ctx, th, false, soloTimeSize(ctx, th.family, w, h)))
                    mgr.partiallyUpdateAppWidget(id, v)
                } catch (e: Exception) { }
            }
        }

        /**
         * Respaldo: una alarma (no exacta, no despierta el teléfono) un par de minutos
         * adelante. Cada minuto se corre; si el servicio muere y deja de correrla,
         * salta y cambia al reloj del sistema.
         */
        fun armClockCheck(ctx: Context) {
            try {
                val am = ctx.getSystemService(Context.ALARM_SERVICE) as AlarmManager
                val pi = PendingIntent.getBroadcast(ctx, 9, internal(ctx, ACTION_CLOCK_CHECK),
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
                am.setWindow(AlarmManager.RTC, System.currentTimeMillis() + 150_000L, 60_000L, pi)
            } catch (e: Exception) { }
        }

        /** Lo atiende el receptor privado cuando salta la alarma de respaldo. */
        internal fun onClockCheck(ctx: Context) {
            if (LiveService.running) updateClock(ctx)
            else { updateAll(ctx); LiveService.start(ctx) }
        }

        /** Tamaño base de la hora por familia de tema (sp). */
        private fun timeSize(family: String) = when (family) {
            "claro" -> 76f; "cristal" -> 76f; "neon" -> 54f; "lujo" -> 80f; else -> 64f
        }

        fun update(ctx: Context, mgr: AppWidgetManager, widgetId: Int) {
            mgr.updateAppWidget(widgetId, buildViews(ctx, widthPx(ctx, mgr, widgetId), hDp = Widgets.sizeDp(ctx, mgr, widgetId).second))
            Prefs.setLastDraw(ctx)
        }

        /**
         * Arma el widget completo. Lo usan el widget real y la vista previa de los
         * ajustes ([preview] = true: sin efectos secundarios como pedir el clima).
         * Está dividido por secciones: cabecera, clima/batería y apps.
         */
        fun buildViews(ctx: Context, W: Int, preview: Boolean = false, kind: Int = Widgets.FULL, hDp: Int = 0,
                       sample: Boolean = false): RemoteViews {
            // El widget Reproductor suelto usa la variante con el reproductor arriba
            val th = Themes.widget(ctx).let { if (kind == Widgets.PLAYER) Themes.up(it) else it }
            val v = RemoteViews(ctx.packageName, th.layout)
            compose(ctx, v, th, kind)
            applyBackground(ctx, v, th)
            val fonts = Prefs.themeFonts(ctx)

            when (kind) {
                Widgets.CLOCK -> {
                    // Solo la cabecera (hora, fecha, clima, batería)
                    Player.apply(ctx, v, fonts, W, preview = true, mode = -1)
                    v.setViewVisibility(R.id.middle, View.GONE)
                    v.setViewVisibility(R.id.setlist, View.GONE)
                    headerSection(ctx, v, th, W, false, fonts, preview, kind, hDp)
                }
                Widgets.PLAYER -> {
                    // Solo el reproductor: grande si el widget es alto, compacto si es bajito
                    v.setViewVisibility(R.id.top_space, View.GONE)
                    v.setViewVisibility(R.id.header, View.GONE)
                    v.setViewVisibility(R.id.setlist, View.GONE)
                    // El grande mide unos 185 dp: solo si cabe entero; si no, el compacto
                    Player.apply(ctx, v, fonts, W, preview, mode = if (hDp >= 190) 1 else 2)
                    if (Build.VERSION.SDK_INT >= 31) for (pid in intArrayOf(R.id.player, R.id.player_c))
                        v.setViewLayoutMargin(pid, RemoteViews.MARGIN_TOP, 0f, TypedValue.COMPLEX_UNIT_DIP)
                }
                else -> {
                    // Reproductor primero: de él depende cuánto espacio queda.
                    val playing = Player.apply(ctx, v, fonts, W, preview, sample = sample, tall = tallFull(ctx, hDp, (W / ctx.resources.displayMetrics.density).toInt()))
                    if (!preview) Prefs.setLastPlaying(ctx, playing)
                    headerSection(ctx, v, th, W, playing, fonts, preview, hDp = hDp)
                    appsSection(ctx, v, th, W, playing, fonts, hDp)
                }
            }

            // Si hay ciudad pero el clima es viejo o falta el tipo de clima, pedirlo ya
            if (!preview && Prefs.city(ctx).isNotBlank() && (Prefs.weatherCode(ctx) < 0 ||
                System.currentTimeMillis() - Prefs.weatherAt(ctx) > 45 * 60_000L)) {
                WeatherWorker.runNow(ctx)
            }
            return v
        }

        /**
         * Arma el widget con sus piezas en el orden elegido en el editor visual
         * (solo el Completo; los sueltos van en el orden de siempre).
         */
        private fun compose(ctx: Context, v: RemoteViews, th: Theme, kind: Int) {
            val fam = th.family; val pkg = ctx.packageName
            val full = kind == Widgets.FULL
            val blocks = if (full) Prefs.blockOrder(ctx) else Prefs.BLOCKS
            val heads = if (full) Prefs.headOrder(ctx) else Prefs.HEAD_PARTS
            val top = kind == Widgets.PLAYER || Themes.isUp(th) || (full && blocks.first() == "player")
            v.removeAllViews(R.id.stack)
            for (b in blocks) {
                val piece = when (b) {
                    "head" -> RemoteViews(pkg, WidgetPieces.of(fam, "header")).also { h ->
                        for (p in heads) WidgetPieces.of(fam, p).takeIf { it != 0 }?.let { h.addView(R.id.col, RemoteViews(pkg, it)) }
                    }
                    "player" -> RemoteViews(pkg, WidgetPieces.of(fam, if (top) "middle_top" else "middle"))
                    else -> RemoteViews(pkg, WidgetPieces.of(fam, "setlist"))
                }
                v.addView(R.id.stack, piece)
            }
        }

        /**
         * Cambió algo del reproductor. Si el estado (sonando o no) es el mismo,
         * solo se redibuja la tarjeta del reproductor; si cambió, todo (la hora y
         * las apps se acomodan según haya música).
         */
        @Synchronized
        fun updatePlayer(ctx: Context) {
            Widgets.updateKind(ctx, Widgets.PLAYER)
            SmartWidget.updateAll(ctx)
            val mgr = AppWidgetManager.getInstance(ctx)
            val ids = mgr.getAppWidgetIds(ComponentName(ctx, RockWidget::class.java))
            if (ids.isEmpty()) return
            val th = Themes.widget(ctx)
            for (id in ids) {
                try {
                    val v = RemoteViews(ctx.packageName, th.layout)
                    val playing = Player.apply(ctx, v, Prefs.themeFonts(ctx), widthPx(ctx, mgr, id),
                        tall = Widgets.sizeDp(ctx, mgr, id).let { (w, h) -> tallFull(ctx, h, w) })
                    if (playing != Prefs.lastPlaying(ctx)) { updateAll(ctx); return }
                    mgr.partiallyUpdateAppWidget(id, v)
                } catch (e: Exception) { }
            }
        }

        /**
         * Cuánto alto (dp) le sobra al Completo con el reproductor compacto: se calcula con lo que
         * de verdad lleva (letra del teléfono, filas de apps, margen del panel), no con un número fijo.
         */
        internal fun fullFree(ctx: Context, hDp: Int): Int {
            if (hDp <= 0) return 0
            val fs = ctx.resources.configuration.fontScale.coerceIn(0.8f, 1.6f)
            val th = Themes.widget(ctx)
            val head = ((24 + timeSize(th.family) * 1.2f + 20 + 56 + (if (Prefs.el(ctx, "bateria")) 28 else 0) + 36) * fs).toInt()
            val player = if (Prefs.el(ctx, "player")) 76 else 0
            val n = (0 until 8).count { Prefs.app(ctx, it, 0).isNotBlank() }
            val rows = if (n == 0) 0 else minOf((n + Prefs.perRow(ctx) - 1) / Prefs.perRow(ctx), if (hDp in 1..329) 1 else 2)
            val apps = if (rows == 0) 0 else rows * 88 + 24
            val pad = 12 + 2 * rootPadDp(ctx, hDp)
            return hDp - head - player - apps - pad
        }

        /** Sobra sitio para el reproductor grande (unos 110 dp más que el compacto). */
        internal fun tallFull(ctx: Context, hDp: Int, wDp: Int = 0): Boolean =
            fullFree(ctx, hDp) >= 115 ||
                // Algunos lanzadores (HyperOS) informan un alto menor al real: por la forma (mucho más alto
                // que ancho) también se sabe que sobra espacio
                (wDp > 0 && hDp >= 560 && hDp >= wDp * 1.7f)

        /** Día, fecha, hora, clima y batería. */
        private fun headerSection(ctx: Context, v: RemoteViews, th: Theme, W: Int, playing: Boolean, fonts: Boolean, preview: Boolean,
                                  kind: Int = Widgets.FULL, hDp: Int = 0) {
            val accent = th.accent(ctx)
            val rock = th.family == "rock"
            val S = TextArt.spec(th.family)
            val dens = ctx.resources.displayMetrics.density
            // En el widget Hora y clima: lo elegido para él, y menos datos si es pequeño
            val solo = kind == Widgets.CLOCK
            // Editor visual: tamaño, color y letra de cada parte (solo en el Completo)
            val ed = kind == Widgets.FULL
            fun sc(p: String) = if (ed) Prefs.partScale(ctx, p) else 1f
            fun colr(p: String, def: Int) = (if (ed) Prefs.partColor(ctx, p) else 0).takeIf { it != 0 } ?: def
            fun own(p: String) = if (ed) Prefs.partColor(ctx, p) else 0
            fun fnt(p: String) = if (ed) Prefs.partFont(ctx, p) else "tema"
            fun sp(p: String) = if (fnt(p) == "tema") S else TextArt.specFor(th.family, fnt(p))
            fun custom(p: String) = fnt(p) != "tema"
            // Rock angosto: sin el día vertical (ocupaba media pantalla); el día va en texto
            val bigDay = rock && !(solo && W / dens < 230)
            val colW = W - ((if (bigDay) 96 else 24) * dens).toInt()

            val wDp = (W / dens).toInt()
            val fs = ctx.resources.configuration.fontScale.coerceIn(0.8f, 1.6f)
            val dateInDay0 = th.family == "cristal" || th.family == "neon" || th.family == "lujo"
            var dia = if (solo) Prefs.cw(ctx, "dia") else Prefs.el(ctx, "dia")
            val hora = if (solo) true else Prefs.el(ctx, "hora")
            // Clima viejo (más de 6 horas sin actualizar): mejor no mostrarlo que mostrar algo falso
            val wxAt = Prefs.weatherAt(ctx)
            val stale = wxAt > 0 && System.currentTimeMillis() - wxAt > 6 * 3_600_000L
            var clima = (if (solo) Prefs.cw(ctx, "clima") else Prefs.el(ctx, "clima")) && !stale
            // Próxima alarma y evento de la agenda van en la línea de «Mañana»
            val extra = listOf(Encore.today(ctx)?.let { I18n.tr(ctx, "Hoy") + ": " + it } ?: "",
                if (Prefs.showAlarm(ctx)) Extras.nextAlarm(ctx) else "",
                if (Prefs.showAgenda(ctx)) Extras.nextEvent(ctx) else "").filter { it.isNotBlank() }.joinToString(" · ")
            // Completo bajito (menos de 300 dp): sin la línea de mañana, para que todo quepa
            var manana = (if (solo) Prefs.cw(ctx, "manana") && wDp >= 230 else Prefs.el(ctx, "manana") && !(hDp in 1..299)) || extra.isNotBlank()
            val poster = Prefs.poster(ctx)
            var bateria = if (solo) Prefs.cw(ctx, "bateria") else Prefs.el(ctx, "bateria")
            // Widget Hora y clima: según el alto se quitan líneas enteras (nunca se cortan).
            // Orden en que se quitan: batería, mañana, clima, día y fecha.
            var timeRoom = 0
            if (solo && hDp > 0) {
                fun need(): Int {
                    var n = 0
                    if (dia) n += if (bigDay) 0 else if (dateInDay0) 22 else 44
                    if (clima) n += 36
                    if (manana) n += 20
                    if (bateria) n += 24
                    return (n * fs).toInt()
                }
                val avail = hDp - 20
                val minTime = (40 * fs).toInt()
                while (avail - need() < minTime) {
                    when {
                        bateria -> bateria = false
                        manana -> manana = false
                        clima -> clima = false
                        dia -> dia = false
                        else -> break
                    }
                }
                timeRoom = avail - need()
            }
            val narrow = solo && wDp < 260

            // Día
            val day = I18n.tr(ctx, DAYS[Calendar.getInstance().get(Calendar.DAY_OF_WEEK) - 1])
            if (bigDay && dia) {
                // En el widget Hora y clima, el día de Rock no pasa del alto del widget
                val h = ((if (solo) (hDp - 28).coerceIn(80, 176) else if (playing) 150 else 176) * sc("dia")).toInt().coerceIn(60, 240)
                v.setImageViewBitmap(R.id.day_img, Graphics.dayBitmap(ctx, day, colr("dia", accent), h))
                if (Build.VERSION.SDK_INT >= 31) v.setViewLayoutHeight(R.id.day_img, h.toFloat(), TypedValue.COMPLEX_UNIT_DIP)
            }
            v.setViewVisibility(R.id.day_img, vis(bigDay && dia))
            val dayColor = colr("dia", when (th.family) { "claro", "cristal" -> th.head; "neon" -> th.accent2; else -> accent })
            if (!bigDay) v.setTextColor(R.id.day_txt, dayColor)
            if (sc("dia") != 1f) v.setTextViewTextSize(R.id.day_txt, TypedValue.COMPLEX_UNIT_SP, 13f * sc("dia"))
            val now = java.util.Date()
            val longDay = when (th.family) {
                "cristal" -> "EEEE, d 'de' MMMM"; "neon" -> "EEEE '//' dd.MM"; "lujo" -> "EEEE · d 'de' MMMM"; else -> "EEEE"
            }
            val dayFits = TextArt.commonSize(ctx, listOf(java.text.SimpleDateFormat(I18n.dateFormat(ctx, longDay), I18n.locale(ctx))
                .format(java.util.Date()).replace(".", "").uppercase()), S.title, 13f * sc("dia"), S.tls, true, S.shadow,
                maxWidthPx = ((colW - 8 * dens) / fs).toInt()) >= 13f * sc("dia") * 0.97f
            val dayFmt = if (narrow || !dayFits) when (th.family) {
                "cristal" -> "EEE d MMM"; "neon" -> "EEE '//' dd.MM"; "lujo" -> "EEE · d MMM"; else -> "EEEE"
            } else when (th.family) {
                "cristal" -> "EEEE, d 'de' MMMM"; "neon" -> "EEEE '//' dd.MM"; "lujo" -> "EEEE · d 'de' MMMM"; else -> "EEEE"
            }
            // El día del sistema (TextClock) usa el mismo formato: antes quedaba «MIÉRCOLES //» cortado
            I18n.dateFormat(ctx, dayFmt).let { df ->
                v.setCharSequence(R.id.day_txt, "setFormat12Hour", df); v.setCharSequence(R.id.day_txt, "setFormat24Hour", df)
            }
            // Fecha corta ("22 nov") cuando el widget es angosto: nunca "22 de novie…"
            val dateFmt = I18n.dateFormat(ctx, if (narrow) "d MMM" else "d 'de' MMMM")
            val loc = I18n.locale(ctx)
            fun fmt(f: String) = java.text.SimpleDateFormat(I18n.dateFormat(ctx, f), loc).format(now).replace(".", "")
            v.setCharSequence(R.id.date, "setFormat12Hour", dateFmt)
            v.setCharSequence(R.id.date, "setFormat24Hour", dateFmt)
            // Cartel de Gira: la fecha se dibuja como afiche (necesita la imagen, no el reloj del sistema)
            val otherLang = I18n.lang(ctx) != (java.util.Locale.getDefault().language.takeIf { it == "en" || it == "pt" } ?: "es")
            val dateFonts = (fonts && bitmapDate(ctx)) || poster || otherLang
            twin(v, R.id.day_txt, R.id.day_txt_bmp, !bigDay && dia, dateFonts || custom("dia")) {
                TextArt.render(ctx, fmt(dayFmt), sp("dia").title, 13f * sc("dia"), dayColor,
                    S.tls, caps = true, glow = if (th.family == "neon") th.accent2 else 0, maxWidthPx = colW)
            }
            v.setContentDescription(R.id.day_txt_bmp, fmt(dayFmt))
            v.setContentDescription(R.id.date_bmp, if (poster) Extras.posterLine(ctx) else fmt(dateFmt))
            // Cristal, Neón y Lujo llevan la fecha en la misma línea del día.
            val dateInDay = dateInDay0 && !poster
            val fecha = !ed || Prefs.el(ctx, "fecha")
            val fs2 = sp("fecha")
            if (own("fecha") != 0) v.setTextColor(R.id.date, own("fecha"))
            if (sc("fecha") != 1f) v.setTextViewTextSize(R.id.date, TypedValue.COMPLEX_UNIT_SP, S.dsize * sc("fecha"))
            twin(v, R.id.date, R.id.date_bmp, dia && fecha && !dateInDay, dateFonts || custom("fecha")) {
                if (poster) TextArt.render(ctx, Extras.posterLine(ctx), fs2.title, S.dsize * sc("fecha"), colr("fecha", accent),
                    0.12f, caps = true, shadow = S.shadow, maxWidthPx = colW, translate = false)
                else TextArt.render(ctx, fmt(dateFmt),
                    if (th.family == "lujo") fs2.med else fs2.body, S.dsize * sc("fecha"),
                    colr("fecha", if (th.family == "lujo") th.dim else th.head),
                    S.lls, caps = S.caps, shadow = S.shadow, maxWidthPx = colW)
            }

            // Hora (más pequeña cuando suena música; en el widget suelto, a lo que quepa)
            val soloTs = if (solo) minOf(soloTimeSize(ctx, th.family, wDp, 0),
                if (timeRoom > 0) timeRoom / (1.1f * fs) else 999f).coerceAtLeast(24f) else 0f
            val bmpClock = bitmapClock(ctx)
            v.setViewVisibility(R.id.clock, vis(hora && !bmpClock))
            v.setViewVisibility(R.id.clock_bmp, vis(hora && bmpClock))
            if (hora && bmpClock) {
                v.setImageViewBitmap(R.id.clock_bmp, clockBitmap(ctx, th, playing, soloTs, full = ed))
                if (!preview) armClockCheck(ctx)
            }
            // Completo muy alto: la hora crece un poco para llenar el espacio que sobra
            val grow = if (kind == Widgets.FULL && hDp > 0) {
                val tall = tallFull(ctx, hDp, wDp)
                val left = maxOf(fullFree(ctx, hDp), if (tall) 160 else 0) - (if (tall && playing) 110 else 0)
                (1f + left / 300f).coerceIn(1f, if (playing) 1.3f else 1.4f)
            } else 1f
            val ts = (if (solo) soloTs else timeSize(th.family) * (if (playing) 0.875f else 1f) * grow) * sc("hora")
            if (own("hora") != 0) for (id in intArrayOf(R.id.hh, R.id.mm)) v.setTextColor(id, own("hora"))
            // Fecha más pequeña si el widget suelto es angosto
            if (solo && wDp < 200) v.setTextViewTextSize(R.id.date, TypedValue.COMPLEX_UNIT_SP, S.dsize * 0.9f)
            for (id in intArrayOf(R.id.hh, R.id.colon, R.id.mm)) v.setTextViewTextSize(id, TypedValue.COMPLEX_UNIT_SP, ts)
            v.setTextColor(R.id.colon, when (th.family) { "claro" -> th.head; "neon" -> th.accent2; else -> accent })

            // Clima
            val tomorrow = listOf(if (if (solo) Prefs.cw(ctx, "manana") else Prefs.el(ctx, "manana")) Prefs.weatherTomorrow(ctx) else "", extra)
                .filter { it.isNotBlank() }.joinToString(" · ")
            v.setTextViewText(R.id.wx_tomorrow, tomorrow)
            // «Mañana…» se leía muy poco sobre fondos claros o sin fondo: más cerca del color principal
            val bare = Prefs.widgetStyle(ctx) != "fade" && Prefs.widgetStyle(ctx) != "solid"
            val tomInk = ColorUtils.blendARGB(th.dim, th.head, 0.9f)
            v.setTextColor(R.id.wx_tomorrow, tomInk)
            val nowFull = now0(ctx)
            val ws = sp("clima"); val wk = sc("clima"); val wInk = colr("clima", th.head)
            val fitsFull = TextArt.commonSize(ctx, listOf(nowFull), if (th.family == "lujo") ws.body else ws.med, S.wsize * wk,
                caps = th.family == "neon", shadow = S.shadow, maxWidthPx = colW) >= S.wsize * wk * 0.85f
            if (own("clima") != 0) v.setTextColor(R.id.wx_now, own("clima"))
            if (wk != 1f) {
                v.setTextViewTextSize(R.id.wx_now, TypedValue.COMPLEX_UNIT_SP, S.wsize * wk)
                v.setTextViewTextSize(R.id.wx_tomorrow, TypedValue.COMPLEX_UNIT_SP, S.tomsize * wk)
            }
            // Si no cabe completo: solo la temperatura (nunca "Nie…")
            val nowTxt = if (fitsFull || Prefs.weatherNow(ctx).isBlank()) nowFull else "${Prefs.weatherTemp(ctx)}°"
            v.setTextViewText(R.id.wx_now, nowTxt)
            v.setContentDescription(R.id.wx_now_bmp, nowFull)
            val wxIcon = fonts && clima && Prefs.weatherCode(ctx) >= 0 && (th.family == "cristal" || th.family == "claro")
            val tomInline = wxIcon && manana && tomorrow.isNotBlank()
            twin(v, R.id.wx_now, R.id.wx_now_bmp, clima, fonts || custom("clima")) {
                if (wxIcon) TextArt.scaled(TextArt.weather(ctx, Prefs.weatherCode(ctx), Prefs.weatherTemp(ctx), Prefs.weatherDesc(ctx),
                    if (tomInline) tomorrow else "", ws, th.family == "cristal", wInk, th.dim, accent, (colW / wk).toInt()), wk)
                else TextArt.render(ctx, nowTxt, if (th.family == "lujo") ws.body else ws.med, S.wsize * wk, wInk,
                    caps = th.family == "neon", shadow = S.shadow, maxWidthPx = colW)
            }
            twin(v, R.id.wx_tomorrow, R.id.wx_tomorrow_bmp, manana && tomorrow.isNotBlank() && !tomInline, fonts || custom("clima")) {
                TextArt.render(ctx, tomorrow, if (th.family == "lujo") ws.med else ws.body, S.tomsize * wk, tomInk,
                    S.lls, caps = th.family == "neon" || th.family == "lujo", shadow = true, maxWidthPx = colW)
            }
            v.setViewVisibility(R.id.batt_row, vis(bateria))
            v.setViewVisibility(R.id.status_row, vis(clima))
            // La rayita va entre la hora y el clima: si el clima quedó arriba, sobra
            val heads = if (ed) Prefs.headOrder(ctx) else Prefs.HEAD_PARTS
            v.setViewVisibility(R.id.hairline, vis((dia || hora) && (clima || bateria || manana) &&
                heads.indexOf("clima") > heads.indexOf("hora")))
            v.setViewVisibility(R.id.header, vis(dia || hora || clima || manana || bateria))
            if (bateria) applyBattery(ctx, v, fonts)
            if (!preview) {
                // Abrir ajustes desde la cabecera y la lista vacía
                val open = PendingIntent.getActivity(
                    ctx, 100, Intent(ctx, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
                )
                v.setOnClickPendingIntent(R.id.header, open)
                v.setOnClickPendingIntent(R.id.day_img, open)
                v.setOnClickPendingIntent(R.id.empty, open)
            }
        }

        /** Accesos a apps (una o dos filas según la distribución y la música). */
        private fun appsSection(ctx: Context, v: RemoteViews, th: Theme, W: Int, playing: Boolean, fonts: Boolean, hDp: Int = 0) {
            val accent = th.accent(ctx)
            val rock = th.family == "rock"
            val S = TextArt.spec(th.family)
            val dens = ctx.resources.displayMetrics.density


            // Apps: cuántas mostrar según la distribución (con música, solo una fila)
            val perRow = Prefs.perRow(ctx)
            val order = if (perRow == 4) ORDER4 else ORDER3
            val wanted = Prefs.appRows(ctx) * perRow
            // En "Completo", con música se deja una sola fila para dar espacio al
            // reproductor. Si el usuario eligió el número a mano, se respeta.
            // Con el reproductor grande tampoco caben dos filas: se cortaban sus botones.
            val auto = Prefs.preset(ctx) == "completo" || Prefs.playerLarge(ctx)
            // Con música o en un Completo bajito (menos de 330 dp), una sola fila
            val maxApps = if ((auto && playing || hDp in 1..329) && wanted > perRow) perRow else wanted
            val pm = ctx.packageManager
            val showNums = false  // los números 01/02/03 se leían poco y no aportaban
            val numColor = ColorUtils.setAlphaComponent(if (rock) accent else th.accent2, if (rock) 0x99 else 0xCC)
            val ak = Prefs.partScale(ctx, "apps")
            val aInk = Prefs.partColor(ctx, "apps")
            val aFont = Prefs.partFont(ctx, "apps")
            val aS = if (aFont == "tema") S else TextArt.specFor(th.family, aFont)
            val iconColor = aInk.takeIf { it != 0 } ?: when (th.family) { "neon" -> accent; "lujo" -> th.head; else -> th.head }
            // Una sola consulta por app (antes se hacían dos)
            val launches = HashMap<Int, Intent>()
            for (s in 0 until Prefs.usedSlots(ctx)) {
                val a = Prefs.app(ctx, s)
                if (a.isNotBlank()) pm.getLaunchIntentForPackage(a)?.let { launches[s] = it }
            }
            val filled = (0 until Prefs.usedSlots(ctx)).filter { it in launches }.take(maxApps)
            // Con 3 por fila, el cuarto lugar de cada fila desaparece (no ocupa espacio)
            if (perRow == 3) { v.setViewVisibility(PADS[6], View.GONE); v.setViewVisibility(PADS[7], View.GONE) }
            // Todos los nombres del mismo tamaño: si uno largo tiene que achicarse, los demás también
            val labelMax = W / perRow - (10 * dens).toInt()
            val labelCaps = th.family == "neon" || th.family == "lujo"
            val labelSp = TextArt.commonSize(ctx, filled.map { Prefs.displayName(ctx, it) }, aS.label,
                (if (perRow == 4) S.lsize * 0.86f else S.lsize) * ak, S.lls, labelCaps, S.shadow, maxWidthPx = labelMax)
            for (pos in order.indices) {
                val i = order[pos]
                val slot = filled.getOrNull(pos)
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
                if (aInk != 0) v.setTextColor(LABELS[i], aInk)
                if (ak != 1f) v.setTextViewTextSize(LABELS[i], TypedValue.COMPLEX_UNIT_SP, labelSp)
                twin(v, LABELS[i], LABEL_BMPS[i], true, fonts || aFont != "tema", name) {
                    // Con 4 por fila, letra un poco menor para todas (así se ven parejas)
                    TextArt.render(ctx, name, aS.label, labelSp, aInk.takeIf { it != 0 } ?: th.head, S.lls,
                        caps = labelCaps, shadow = S.shadow, maxWidthPx = labelMax, translate = false)
                }
                Graphics.appIcon(ctx, pkg, mono = iconColor)?.let { v.setImageViewBitmap(ICONS[i], it) }
                if (Build.VERSION.SDK_INT >= 31) {
                    val big = th.family == "claro" || th.family == "lujo" || th.family == "cristal"
                    val s0 = if (Prefs.originalIcons(ctx)) (if (big) 36f else 32f) else (if (big) 30f else 28f)
                    val s = (if (perRow == 4) s0 * 0.9f else s0) * ak
                    v.setViewLayoutWidth(ICONS[i], s, TypedValue.COMPLEX_UNIT_DIP)
                    v.setViewLayoutHeight(ICONS[i], s, TypedValue.COMPLEX_UNIT_DIP)
                }
                val launch = (launches[slot] ?: continue).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                v.setOnClickPendingIntent(
                    PADS[i],
                    PendingIntent.getActivity(ctx, i, launch, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
                )
            }
            v.setViewVisibility(R.id.setlist, vis(wanted > 0))
            v.setViewVisibility(R.id.row2, vis(filled.size > perRow))
            v.setViewVisibility(R.id.row1, vis(filled.isNotEmpty()))
            v.setViewVisibility(R.id.empty, vis(wanted > 0 && filled.isEmpty()))
            v.setViewVisibility(R.id.setlist_head, vis(filled.size > perRow))
            twin(v, R.id.setlist_title, R.id.setlist_title_bmp, true, fonts) {
                TextArt.render(ctx, if (rock) "SETLIST" else "APPS", S.title, if (rock) 15f else 12f, th.head, S.tls, shadow = S.shadow)
            }
            val word = if (filled.size == 1) "app" else "apps"
            v.setTextViewText(R.id.count, "${filled.size} " + I18n.tr(ctx, word))
        }

        /** Fondo del widget (como está, desvanecido o fijo) y colores de texto acordes. */
        private fun applyBackground(ctx: Context, v: RemoteViews, th: Theme) {
            rootBackground(ctx, v, th)
            textColors(ctx, v, th)
            tintCards(ctx, v, th, PADS + intArrayOf(R.id.player, R.id.player_c, R.id.empty))
        }

        /** Solo el fondo (lo usan también las piezas sueltas, como el widget de Apps). */
        /** Margen interior del panel: 16 dp, menos en widgets angostos (si no, los íconos no caben). */
        internal fun rootPadDp(ctx: Context, sizeDp: Int): Int = when {
            Prefs.widgetStyle(ctx) != "fade" && Prefs.widgetStyle(ctx) != "solid" -> 0
            sizeDp in 1..139 -> 6
            sizeDp in 140..219 -> 10
            else -> 16
        }

        internal fun rootBackground(ctx: Context, v: RemoteViews, th: Theme, sizeDp: Int = 0) {
            when (Prefs.widgetStyle(ctx)) {
                "fade", "solid" -> {
                    val col = Themes.widgetColor(ctx, th)
                    if (android.os.Build.VERSION.SDK_INT >= 31) {
                        v.setInt(R.id.root, "setBackgroundResource",
                            if (Prefs.widgetStyle(ctx) == "fade") R.drawable.wbg_fade else R.drawable.wbg_solid)
                        v.setColorStateList(R.id.root, "setBackgroundTintList", android.content.res.ColorStateList.valueOf(col))
                    } else v.setInt(R.id.root, "setBackgroundColor", col)
                    // Margen interior: el contenido no queda pegado al borde del fondo
                    val pad = (rootPadDp(ctx, sizeDp) * ctx.resources.displayMetrics.density).toInt()
                    v.setViewPadding(R.id.root, pad, pad, pad, pad)
                }
                else -> v.setInt(R.id.root, "setBackgroundResource", 0)
            }
        }

        /**
         * Si el texto cambió a oscuro (fondo claro) o a claro (fondo oscuro), las tarjetas
         * también: así nunca queda texto oscuro sobre una tarjeta casi negra, ni al revés.
         */
        internal fun tintCards(ctx: Context, v: RemoteViews, th: Theme, cards: IntArray, holders: IntArray = IntArray(0)) {
            if (Build.VERSION.SDK_INT < 31) return
            if (th.light == Themes.current(ctx).light) return
            val card = android.content.res.ColorStateList.valueOf(
                Color.parseColor(if (th.light) "#CCFFFFFF" else "#B8141110"))
            val hold = android.content.res.ColorStateList.valueOf(
                Color.parseColor(if (th.light) "#1A000000" else "#2EFFFFFF"))
            for (id in cards) v.setColorStateList(id, "setBackgroundTintList", card)
            for (id in holders) v.setColorStateList(id, "setBackgroundTintList", hold)
        }

        private fun textColors(ctx: Context, v: RemoteViews, th: Theme) {
            // Si el texto cambió (fondo claro/oscuro), también la letra del sistema
            if (th.head != Themes.current(ctx).head) {
                for (id in intArrayOf(R.id.hh, R.id.mm, R.id.wx_now, R.id.batt_txt, R.id.pc_title, R.id.player_title,
                        R.id.setlist_title, R.id.day_txt, R.id.empty_txt) + LABELS) v.setTextColor(id, th.head)
                for (id in intArrayOf(R.id.date, R.id.wx_tomorrow, R.id.pc_artist, R.id.player_artist, R.id.count)) v.setTextColor(id, th.dim)
            }
        }

        private fun now0(ctx: Context): String =
            Prefs.weatherNow(ctx).ifBlank { if (Prefs.city(ctx).isBlank()) "Elige tu ciudad" else "Cargando clima…" }

        /** Medidor LED y texto de batería (también se usa en actualizaciones parciales). */
        fun applyBattery(ctx: Context, v: RemoteViews, fonts: Boolean = Prefs.themeFonts(ctx)) {
            val th = Themes.widget(ctx)
            val accent = th.accent(ctx)
            val level = BatteryMonitor.level(ctx)
            val charging = BatteryMonitor.isCharging(ctx)
            val off = if (th.light) Color.parseColor("#381C1A17") else Color.parseColor("#2A231E")
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
            val bt0 = if (th.family == "lujo") I18n.tr(ctx, "BATERÍA") + " $level %" else "$level %"
            // «Este va hasta el 11»: guiño al llegar al 100 % cargando
            val bt = if (Prefs.amp11(ctx) && charging && level >= 100) "$bt0 · " + I18n.tr(ctx, "¡hasta el 11!") else bt0
            val bc = if (!charging && level <= 15) Graphics.alert(ctx) else th.head
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
                ctx, 0, internal(ctx, ACTION_REFRESH),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            am.setWindow(AlarmManager.RTC, cal.timeInMillis, 60_000L, pi)
            // Setlist del Día: redibujo a las 8 p. m. y a las 5 a. m. (empieza y termina la noche)
            if (!Prefs.ruleNight(ctx)) for (code in 1..2) am.cancel(PendingIntent.getBroadcast(ctx, 600 + code,
                internal(ctx, ACTION_REFRESH), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT))
            if (Prefs.ruleNight(ctx)) for ((code, hour) in listOf(1 to 20, 2 to 5)) {
                val t = Calendar.getInstance().apply {
                    set(Calendar.HOUR_OF_DAY, hour); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 5); set(Calendar.MILLISECOND, 0)
                    if (timeInMillis <= System.currentTimeMillis()) add(Calendar.DAY_OF_YEAR, 1)
                }
                am.setWindow(AlarmManager.RTC, t.timeInMillis, 120_000L, PendingIntent.getBroadcast(ctx, 600 + code,
                    internal(ctx, ACTION_REFRESH), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT))
            }
        }
    }
}

/**
 * Receptor privado (no exportado): botones del reproductor, refresco de medianoche
 * y alarma de respaldo del reloj. Otras apps no pueden enviarle órdenes.
 */
class ActionReceiver : android.content.BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        try {
            when (intent.action) {
                Player.ACTION_PLAY_PAUSE, Player.ACTION_NEXT, Player.ACTION_PREV, Player.ACTION_CLOSE ->
                    Player.handle(ctx, intent.action)
                RockWidget.ACTION_REFRESH -> {
                    RockWidget.updateAll(ctx)
                    RockWidget.scheduleMidnight(ctx)
                }
                RockWidget.ACTION_CLOCK_CHECK -> RockWidget.onClockCheck(ctx)
            }
        } catch (e: Exception) { }
    }
}
