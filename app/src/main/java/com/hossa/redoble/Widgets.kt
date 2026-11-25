package com.hossa.redoble

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.util.TypedValue
import android.view.View
import android.widget.LinearLayout
import android.widget.RemoteViews
import android.widget.ScrollView
import androidx.appcompat.app.AppCompatActivity

// Paquete de widgets: además del Completo (RockWidget) hay tres piezas sueltas que se
// ponen y se estiran por separado. Todas usan el mismo tema, letras y fondo.

/** Hora y clima: día, fecha, hora, clima y batería. Se simplifica si es pequeño. */
class ClockWidget : AppWidgetProvider() {
    override fun onUpdate(ctx: Context, mgr: AppWidgetManager, ids: IntArray) {
        ids.forEach { Widgets.update(ctx, mgr, it, Widgets.CLOCK) }
        // Igual que el Completo: cambio de día, clima y batería aunque esté solo
        try {
            RockWidget.scheduleMidnight(ctx)
            WeatherWorker.schedule(ctx)
            BatteryMonitor.check(ctx)
        } catch (e: Exception) { }
        LiveService.start(ctx)
    }
    override fun onAppWidgetOptionsChanged(ctx: Context, mgr: AppWidgetManager, id: Int, o: Bundle) =
        Widgets.update(ctx, mgr, id, Widgets.CLOCK)
}

/** Apps: tantas como quepan (o columnas y filas a mano). Cada copia tiene su grupo. */
class AppsWidget : AppWidgetProvider() {
    override fun onUpdate(ctx: Context, mgr: AppWidgetManager, ids: IntArray) =
        ids.forEach { Widgets.update(ctx, mgr, it, Widgets.APPS) }
    override fun onAppWidgetOptionsChanged(ctx: Context, mgr: AppWidgetManager, id: Int, o: Bundle) =
        Widgets.update(ctx, mgr, id, Widgets.APPS)
    override fun onDeleted(ctx: Context, ids: IntArray) = ids.forEach { Prefs.clearAw(ctx, it) }
    override fun onRestored(ctx: Context, oldIds: IntArray, newIds: IntArray) =
        oldIds.forEachIndexed { i, old -> newIds.getOrNull(i)?.let { Prefs.moveAw(ctx, old, it) } }
}

/** Reproductor: grande o compacto según el alto; sin música, la última canción. */
class PlayerWidget : AppWidgetProvider() {
    override fun onUpdate(ctx: Context, mgr: AppWidgetManager, ids: IntArray) =
        ids.forEach { Widgets.update(ctx, mgr, it, Widgets.PLAYER) }
    override fun onAppWidgetOptionsChanged(ctx: Context, mgr: AppWidgetManager, id: Int, o: Bundle) =
        Widgets.update(ctx, mgr, id, Widgets.PLAYER)
}

object Widgets {
    const val FULL = 0
    const val CLOCK = 1
    const val PLAYER = 2
    const val APPS = 3

    private fun cls(kind: Int) = when (kind) {
        CLOCK -> ClockWidget::class.java; PLAYER -> PlayerWidget::class.java
        APPS -> AppsWidget::class.java; else -> RockWidget::class.java
    }

    fun ids(ctx: Context, kind: Int): IntArray = try {
        AppWidgetManager.getInstance(ctx).getAppWidgetIds(ComponentName(ctx, cls(kind)))
    } catch (e: Exception) { IntArray(0) }

    fun has(ctx: Context, kind: Int) = ids(ctx, kind).isNotEmpty()

    /** Tamaño del widget en dp (ancho y alto en vertical). */
    fun sizeDp(ctx: Context, mgr: AppWidgetManager, id: Int): Pair<Int, Int> {
        val o = try { mgr.getAppWidgetOptions(id) } catch (e: Exception) { null }
        // Android 12+: lista de tamaños reales (vertical y horizontal). Se toma el ancho
        // más angosto y el alto más alto, que es como se ve con el teléfono vertical.
        if (Build.VERSION.SDK_INT >= 31) {
            val sizes = try {
                @Suppress("DEPRECATION")
                o?.getParcelableArrayList<android.util.SizeF>(AppWidgetManager.OPTION_APPWIDGET_SIZES)
            } catch (e: Exception) { null }
            if (!sizes.isNullOrEmpty()) {
                val w = sizes.minOf { it.width }.toInt(); val h = sizes.maxOf { it.height }.toInt()
                if (w > 0 && h > 0) return Pair(w, h)
            }
        }
        val w = o?.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH) ?: 0
        val h = o?.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT) ?: 0
        return Pair(if (w > 0) w else 320, if (h > 0) h else 160)
    }

    fun update(ctx: Context, mgr: AppWidgetManager, id: Int, kind: Int) {
        try {
            val (w, h) = sizeDp(ctx, mgr, id)
            val wPx = (w * ctx.resources.displayMetrics.density).toInt()
            val v = when (kind) {
                APPS -> appsViews(ctx, id, w, h)
                PLAYER -> playerViews(ctx, w, h)
                else -> RockWidget.buildViews(ctx, wPx, kind = kind, hDp = h)
            }
            mgr.updateAppWidget(id, v)
        } catch (e: Exception) { }
    }

    /** Redibuja las tres piezas sueltas (el Completo lo hace RockWidget). */
    fun updatePieces(ctx: Context) {
        val mgr = AppWidgetManager.getInstance(ctx)
        for (k in intArrayOf(CLOCK, PLAYER, APPS)) for (id in ids(ctx, k)) update(ctx, mgr, id, k)
    }

    fun updateKind(ctx: Context, kind: Int) {
        val mgr = AppWidgetManager.getInstance(ctx)
        for (id in ids(ctx, kind)) update(ctx, mgr, id, kind)
    }

    // ---------------------------------------------------------------- widget Reproductor

    private fun playerLayout(family: String) = when (family) {
        "cristal" -> R.layout.widget_cristal_player; "claro" -> R.layout.widget_claro_player
        "neon" -> R.layout.widget_neon_player; "lujo" -> R.layout.widget_lujo_player
        else -> R.layout.widget_rock_player
    }

    /** Pequeño (bajito), mediano (carátula a la izquierda) o grande (carátula arriba). */
    fun playerViews(ctx: Context, wDp: Int, hDp: Int): RemoteViews {
        val th = Themes.widget(ctx)
        val v = RemoteViews(ctx.packageName, playerLayout(th.family))
        RockWidget.rootBackground(ctx, v, th)
        val wPx = (wDp * ctx.resources.displayMetrics.density).toInt()
        RockWidget.tintCards(ctx, v, th, intArrayOf(R.id.ws, R.id.wm, R.id.wl))
        val fonts = Prefs.themeFonts(ctx)
        when {
            hDp < 120 -> Player.applyStandalone(ctx, v, 0, wPx, 44, fonts, showText = wDp >= 200)
            hDp < 260 || wDp < 200 -> Player.applyStandalone(ctx, v, 1, wPx,
                minOf(hDp - 24, (wDp * (if (wDp < 300) 0.34f else 0.42f)).toInt()).coerceIn(56, 140), fonts)
            // Grande: la carátula deja sitio a título, artista y botones (unos 200 dp)
            else -> Player.applyStandalone(ctx, v, 2, wPx, minOf(wDp - 40, hDp - 210).coerceIn(80, 220), fonts)
        }
        return v
    }

    // ---------------------------------------------------------------- widget de Apps

    private fun appsLayout(family: String) = when (family) {
        "cristal" -> R.layout.widget_cristal_apps; "claro" -> R.layout.widget_claro_apps
        "neon" -> R.layout.widget_neon_apps; "lujo" -> R.layout.widget_lujo_apps
        else -> R.layout.widget_rock_apps
    }

    private val ROWS = intArrayOf(R.id.arow0, R.id.arow1, R.id.arow2, R.id.arow3)
    private val PADS = intArrayOf(
        R.id.apad0, R.id.apad1, R.id.apad2, R.id.apad3, R.id.apad4, R.id.apad5, R.id.apad6, R.id.apad7, R.id.apad8, R.id.apad9,
        R.id.apad10, R.id.apad11, R.id.apad12, R.id.apad13, R.id.apad14, R.id.apad15, R.id.apad16, R.id.apad17, R.id.apad18, R.id.apad19)
    private val HOLDERS = intArrayOf(
        R.id.aholder0, R.id.aholder1, R.id.aholder2, R.id.aholder3, R.id.aholder4, R.id.aholder5, R.id.aholder6, R.id.aholder7, R.id.aholder8, R.id.aholder9,
        R.id.aholder10, R.id.aholder11, R.id.aholder12, R.id.aholder13, R.id.aholder14, R.id.aholder15, R.id.aholder16, R.id.aholder17, R.id.aholder18, R.id.aholder19)
    private val ICONS = intArrayOf(
        R.id.aicon0, R.id.aicon1, R.id.aicon2, R.id.aicon3, R.id.aicon4, R.id.aicon5, R.id.aicon6, R.id.aicon7, R.id.aicon8, R.id.aicon9,
        R.id.aicon10, R.id.aicon11, R.id.aicon12, R.id.aicon13, R.id.aicon14, R.id.aicon15, R.id.aicon16, R.id.aicon17, R.id.aicon18, R.id.aicon19)
    private val LABELS = intArrayOf(
        R.id.alabel0, R.id.alabel1, R.id.alabel2, R.id.alabel3, R.id.alabel4, R.id.alabel5, R.id.alabel6, R.id.alabel7, R.id.alabel8, R.id.alabel9,
        R.id.alabel10, R.id.alabel11, R.id.alabel12, R.id.alabel13, R.id.alabel14, R.id.alabel15, R.id.alabel16, R.id.alabel17, R.id.alabel18, R.id.alabel19)
    private val LABEL_BMPS = intArrayOf(
        R.id.alabel0_bmp, R.id.alabel1_bmp, R.id.alabel2_bmp, R.id.alabel3_bmp, R.id.alabel4_bmp, R.id.alabel5_bmp, R.id.alabel6_bmp, R.id.alabel7_bmp, R.id.alabel8_bmp, R.id.alabel9_bmp,
        R.id.alabel10_bmp, R.id.alabel11_bmp, R.id.alabel12_bmp, R.id.alabel13_bmp, R.id.alabel14_bmp, R.id.alabel15_bmp, R.id.alabel16_bmp, R.id.alabel17_bmp, R.id.alabel18_bmp, R.id.alabel19_bmp)

    /** Medidas del ícono por familia de tema (dp): caja, ícono y alto con nombre. */
    private fun holderDp(family: String) = when (family) { "cristal", "neon" -> 60; "claro", "lujo" -> 48; else -> 34 }

    /** Columnas, filas y si se ven los nombres, según el tamaño (o lo elegido a mano). */
    data class Grid(val cols: Int, val rows: Int, val names: Boolean, val rowH: Int)

    /** Lo elegido para una copia del widget de Apps (o lo que se está probando en su ventana). */
    class AwCfg(val group: Int, val names: String, val auto: Boolean, val cols: Int, val rows: Int)
    fun awCfg(ctx: Context, id: Int) =
        AwCfg(Prefs.awGroup(ctx, id), Prefs.awNames(ctx, id), Prefs.awAuto(ctx, id), Prefs.awCols(ctx, id), Prefs.awRows(ctx, id))

    fun grid(ctx: Context, id: Int, wDp: Int, hDp: Int, family: String, cfg: AwCfg = awCfg(ctx, id)): Grid {
        val hs = holderDp(family)
        val inner = (hDp - 8).coerceAtLeast(40)
        val cols: Int; val rows: Int
        if (cfg.auto) {
            cols = (wDp / 70).coerceIn(1, 5)  // 2 celdas de ancho ya son 2 columnas
            rows = ((inner + 8) / (minOf(hs, 48) + 28)).coerceIn(1, 4)
        } else {
            // A mano, pero sin pasarse de lo que cabe en el widget
            cols = minOf(cfg.cols, ((wDp - 8 + 8) / 48).coerceAtLeast(1))
            rows = minOf(cfg.rows, ((inner + 8) / 44).coerceAtLeast(1))
        }
        val avail = (inner - 8 * (rows - 1)) / rows
        val colW = (wDp - 8 - 8 * (cols - 1)) / cols
        val fits = avail >= minOf(hs, 48) + 26 && colW >= 58
        val names = colW >= 40 && when (cfg.names) { "on" -> true; "off" -> false; else -> fits }
        // Cada casilla con su alto normal (no se estira de más): el resto queda centrado
        val cap = minOf(hs, 48) + (if (names) 44 else 18)
        val rowH = avail.coerceIn(36, maxOf(36, cap))
        return Grid(cols, rows, names, rowH)
    }

    fun appsViews(ctx: Context, id: Int, wDp: Int, hDp: Int, cfg: AwCfg = awCfg(ctx, id)): RemoteViews {
        val th = Themes.widget(ctx)
        val v = RemoteViews(ctx.packageName, appsLayout(th.family))
        RockWidget.rootBackground(ctx, v, th)
        val fonts = Prefs.themeFonts(ctx)
        val g = grid(ctx, id, wDp, hDp, th.family, cfg)
        val group = cfg.group
        val pm = ctx.packageManager
        val dens = ctx.resources.displayMetrics.density
        val S = TextArt.spec(th.family)
        val iconColor = if (th.family == "neon") th.accent(ctx) else th.head

        // Apps del grupo que existen en el teléfono, en orden, hasta llenar la cuadrícula
        val apps = (0 until Prefs.GROUP_SLOTS).mapNotNull { s ->
            val pkg = Prefs.app(ctx, s, group)
            if (pkg.isBlank()) null else pm.getLaunchIntentForPackage(pkg)?.let { Triple(s, pkg, it) }
        }.take(g.cols * g.rows)
        val usedRows = if (apps.isEmpty()) 0 else (apps.size + g.cols - 1) / g.cols

        // Casillas de ancho normal: si hay pocas columnas no se estiran, quedan centradas
        val freeW = wDp - 8
        val cellMax = if (g.names) 100 else maxOf(g.rowH + 12, 64)
        val cellW = minOf((freeW - 8 * (g.cols - 1)) / g.cols, cellMax)
        val side = ((freeW - cellW * g.cols - 8 * (g.cols - 1)) / 2).coerceAtLeast(0)
        val sidePx = (side * dens).toInt()
        for (r in 0 until 4) v.setViewPadding(ROWS[r], sidePx, 0, sidePx, 0)
        // Nombres del mismo tamaño en toda la cuadrícula
        val labelMax = ((cellW * dens).toInt() - (6 * dens).toInt()).coerceAtLeast(1)
        val labelSp = TextArt.commonSize(ctx, apps.map { Prefs.displayName(ctx, it.first, group) }, S.label,
            if (g.cols >= 4) S.lsize * 0.86f else S.lsize, S.lls, th.family == "neon" || th.family == "lujo", S.shadow, maxWidthPx = labelMax)
        // Caja del ícono: más pequeña si la fila es bajita
        val hs = minOf(holderDp(th.family), (g.rowH - if (g.names) 24 else 6).coerceAtLeast(28), (cellW - 8).coerceAtLeast(28))

        for (r in 0 until 4) v.setViewVisibility(ROWS[r], if (r < usedRows) View.VISIBLE else View.GONE)
        for (r in 0 until 4) for (c in 0 until 5) {
            val i = r * 5 + c
            if (r >= usedRows || c >= g.cols) { v.setViewVisibility(PADS[i], View.GONE); continue }
            val a = apps.getOrNull(r * g.cols + c)
            if (a == null) { v.setViewVisibility(PADS[i], View.INVISIBLE); continue }
            val (slot, pkg, launch) = a
            v.setViewVisibility(PADS[i], View.VISIBLE)
            if (Build.VERSION.SDK_INT >= 31) {
                v.setViewLayoutHeight(PADS[i], g.rowH.toFloat(), TypedValue.COMPLEX_UNIT_DIP)
                v.setViewLayoutWidth(HOLDERS[i], hs.toFloat(), TypedValue.COMPLEX_UNIT_DIP)
                v.setViewLayoutHeight(HOLDERS[i], hs.toFloat(), TypedValue.COMPLEX_UNIT_DIP)
                val big = th.family == "claro" || th.family == "lujo" || th.family == "cristal"
                val s0 = if (Prefs.originalIcons(ctx)) (if (big) 34f else 30f) else (if (big) 22f else 18f)
                // El ícono ocupa una buena parte del círculo (antes se veía perdido)
                val isz = if (Prefs.originalIcons(ctx)) hs * 0.78f else maxOf(minOf(s0, hs * 0.75f), hs * 0.56f)
                v.setViewLayoutWidth(ICONS[i], isz, TypedValue.COMPLEX_UNIT_DIP)
                v.setViewLayoutHeight(ICONS[i], isz, TypedValue.COMPLEX_UNIT_DIP)
            }
            Graphics.appIcon(ctx, pkg, mono = iconColor)?.let { v.setImageViewBitmap(ICONS[i], it) }
            val name = Prefs.displayName(ctx, slot, group)
            v.setTextViewText(LABELS[i], name)
            v.setTextColor(LABELS[i], th.head)
            v.setContentDescription(PADS[i], name)
            RockWidget.twin(v, LABELS[i], LABEL_BMPS[i], g.names, fonts, name) {
                TextArt.render(ctx, name, S.label, labelSp, th.head, S.lls,
                    caps = th.family == "neon" || th.family == "lujo", shadow = S.shadow, maxWidthPx = labelMax, translate = false)
            }
            v.setOnClickPendingIntent(PADS[i], PendingIntent.getActivity(ctx, id * 32 + i,
                launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT))
        }
        RockWidget.tintCards(ctx, v, th, PADS + intArrayOf(R.id.aempty), HOLDERS)
        // Sin apps en el grupo: invitación a elegirlas
        v.setViewVisibility(R.id.aempty, if (apps.isEmpty()) View.VISIBLE else View.GONE)
        v.setOnClickPendingIntent(R.id.aempty, PendingIntent.getActivity(ctx, 150,
            Intent(ctx, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT))
        return v
    }

    /** Resumen de una copia del widget de Apps (para la configuración). */
    /** Nombre por defecto según la forma: «Columna 1×3», «Fila 4×1», «Bloque 2×4». */
    fun shapeName(ctx: Context, id: Int): String {
        val (w, h) = sizeDp(ctx, AppWidgetManager.getInstance(ctx), id)
        val g = grid(ctx, id, w, h, Themes.widget(ctx).family)
        val kind = when { g.cols == 1 -> "Columna"; g.rows == 1 -> "Fila"; else -> "Bloque" }
        return I18n.tr(ctx, kind) + " ${g.cols}×${g.rows}"
    }

    fun appsSummary(ctx: Context, id: Int): String {
        val mgr = AppWidgetManager.getInstance(ctx)
        val (w, h) = sizeDp(ctx, mgr, id)
        val g = grid(ctx, id, w, h, Themes.widget(ctx).family)
        fun n(k: Int, one: String, many: String) = "$k " + if (k == 1) one else many
        val shape = n(g.cols, "columna", "columnas") + ", " + n(g.rows, "fila", "filas")
        val size = if (Prefs.awAuto(ctx, id)) "Automático: $shape" else shape
        return "${Prefs.GROUP_NAMES[Prefs.awGroup(ctx, id)]} · $size"
    }
}

/**
 * Ventanita al poner un widget de Apps (y al tocar «Cambiar» en la app): qué grupo de
 * apps muestra esa copia, si lleva nombres y su tamaño.
 */
class AppsWidgetConfig : AppCompatActivity() {
    private var widgetId = AppWidgetManager.INVALID_APPWIDGET_ID

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        widgetId = intent?.extras?.getInt(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
            ?: AppWidgetManager.INVALID_APPWIDGET_ID
        // Si se cierra sin «Listo», el sistema no pone el widget
        setResult(RESULT_CANCELED, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId))
        if (widgetId == AppWidgetManager.INVALID_APPWIDGET_ID) { finish(); return }
        show()
    }

    private var group = 0; private var names = "auto"; private var auto = true; private var cols = 4; private var rows = 2
    private var loaded = false
    private var scroll: ScrollView? = null
    private var isNew = true

    override fun onResume() {
        super.onResume()
        // Al volver de elegir las apps, la vista previa muestra las nuevas
        if (loaded) show()
    }

    private fun show() {
        val keepY = scroll?.scrollY ?: 0
        if (!loaded) {
            isNew = !Prefs.awExists(this, widgetId)
            group = Prefs.awGroup(this, widgetId); names = Prefs.awNames(this, widgetId)
            auto = Prefs.awAuto(this, widgetId); cols = Prefs.awCols(this, widgetId); rows = Prefs.awRows(this, widgetId)
            loaded = true
        }
        val night = (resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) ==
            android.content.res.Configuration.UI_MODE_NIGHT_YES
        Ui.setLight(when (Prefs.uiMode(this)) { "light" -> true; "system" -> !night; else -> false })
        val th = Themes.saved(this)
        val ui = Ui(this, if (th.light) android.graphics.Color.parseColor(if (Ui.light) "#A8443A" else "#E58272") else th.accent(this))
        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(ui.dp(20), ui.dp(28), ui.dp(20), ui.dp(28))
        }
        col.addView(ui.text(if (isNew) "Nuevo widget de apps" else "Widget de apps", ui.condSemi, 26f, Ui.HEAD, 0.01f))
        col.addView(ui.note("Elige qué muestra esta copia."), LinearLayout.LayoutParams(-1, -2).apply { topMargin = ui.dp(4) })

        // Vista previa en vivo con lo que se va eligiendo
        val mgr0 = AppWidgetManager.getInstance(this)
        val (w0, h0) = Widgets.sizeDp(this, mgr0, widgetId)
        val pw = if (w0 > 0) w0 else 320; val ph = if (h0 > 0) h0 else 150
        val maxW = (resources.displayMetrics.widthPixels / resources.displayMetrics.density).toInt() - 40
        val shown = minOf(pw, maxW)
        val frame = android.widget.FrameLayout(this).apply {
            background = ui.rect(android.graphics.Color.parseColor("#141110"), 18f, Ui.veil(0x14), 1f)
            setPadding(ui.dp(6), ui.dp(6), ui.dp(6), ui.dp(6))
        }
        try {
            val rv = Widgets.appsViews(applicationContext, widgetId, shown, ph, Widgets.AwCfg(group, names, auto, cols, rows))
            frame.addView(rv.apply(applicationContext, frame))
        } catch (e: Exception) { }
        col.addView(frame, LinearLayout.LayoutParams(ui.dp(shown + 12), ui.dp(ph + 12)).apply {
            topMargin = ui.dp(16); gravity = android.view.Gravity.CENTER_HORIZONTAL })

        fun label(t: String) = col.addView(ui.text(t, ui.cond, 16f, Ui.HEAD),
            LinearLayout.LayoutParams(-2, -2).apply { topMargin = ui.dp(18); bottomMargin = ui.dp(6) })
        label("Apps")
        col.addView(ui.segmented(Prefs.GROUP_NAMES.indices.map { it to Prefs.GROUP_NAMES[it] }, group) { group = it; show() })
        col.addView(ui.secondary("Elegir las apps de ${Prefs.GROUP_NAMES[group]}").apply {
            setOnClickListener {
                // Se guarda lo elegido y se abre la sección Apps con este grupo
                Prefs.setAw(this@AppsWidgetConfig, widgetId, group, names, auto, cols, rows)
                startActivity(Intent(this@AppsWidgetConfig, MainActivity::class.java)
                    .putExtra("appsGroup", group).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }
        }, LinearLayout.LayoutParams(-1, ui.dp(48)).apply { topMargin = ui.dp(10) })
        label("Nombres")
        col.addView(ui.segmented(listOf("auto" to "Automático", "on" to "Con nombres", "off" to "Sin nombres"), names) { names = it; show() })
        label("Tamaño")
        col.addView(ui.segmented(listOf(true to "Automático", false to "A mano"), auto) { auto = it; show() })
        if (auto) {
            col.addView(ui.note("Pone las apps que quepan según cómo estires el widget."),
                LinearLayout.LayoutParams(-1, -2).apply { topMargin = ui.dp(6) })
        } else {
            label("Columnas")
            col.addView(ui.segmented((1..5).map { it to "$it" }, cols) { cols = it; show() })
            label("Filas")
            col.addView(ui.segmented((1..4).map { it to "$it" }, rows) { rows = it; show() })
        }
        col.addView(ui.primary("Listo").apply {
            setOnClickListener {
                Prefs.setAw(this@AppsWidgetConfig, widgetId, group, names, auto, cols, rows)
                val mgr = AppWidgetManager.getInstance(this@AppsWidgetConfig)
                Widgets.update(this@AppsWidgetConfig, mgr, widgetId, Widgets.APPS)
                setResult(RESULT_OK, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId))
                finish()
            }
        }, LinearLayout.LayoutParams(-1, ui.dp(52)).apply { topMargin = ui.dp(28) })

        val sv = ScrollView(this).apply { setBackgroundColor(Ui.INK); addView(col) }
        scroll = sv
        setContentView(sv)
        sv.post { sv.scrollTo(0, keepY) }
        window.decorView.setBackgroundColor(Ui.INK)
        androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(sv) { view, ins ->
            val b = ins.getInsets(androidx.core.view.WindowInsetsCompat.Type.systemBars())
            view.setPadding(b.left, b.top, b.right, b.bottom); ins
        }
    }
}
