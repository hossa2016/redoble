package com.hossa.redoble

import android.app.Dialog
import android.app.WallpaperManager
import android.content.ComponentName
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.os.Build
import android.view.GestureDetector
import android.view.Gravity
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import androidx.core.graphics.ColorUtils
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import kotlin.math.abs

// Vista previa a pantalla completa: el fondo a tamaño real (los animados con el mismo
// motor del fondo de verdad, con capas e inclinación) y el widget real encima.
//  - Fondos animados: deslizar a los lados pasa al anterior o al siguiente.
//  - Foto: pellizcar acerca y arrastrar encuadra.
//  - Deslizar hacia abajo, la flecha o el botón atrás cierran sin cambiar nada.
//  - "Aplicar" es lo único que cambia el fondo.

/** Abre el fondo actual o, con [animIndex], el fondo animado de esa posición. */
internal fun MainActivity.openFullPreview(animIndex: Int? = null) {
    val style = Prefs.wallStyle(this)
    val anim = animIndex != null || style == "anim"
    val photoMode = !anim && style == "photo"
    if (photoMode && photo == null) { toast("Primero elige una foto"); return }
    val scenes = VideoScenes.ALL
    var index = animIndex ?: scenes.indexOfFirst { it.id == Prefs.liveScene(this) }.coerceAtLeast(0)
    val (sw, sh) = screenSize()

    val d = Dialog(this, android.R.style.Theme_DeviceDefault_NoActionBar)
    fullPreviewOpen = true
    val root = FrameLayout(this).apply { setBackgroundColor(Color.BLACK) }

    // ---- Fondo ----
    val video = if (anim) VideoSurfaceView(this).apply { scene = scenes[index] } else null
    val still = if (anim) null else ImageView(this).apply { importantForAccessibility = android.view.View.IMPORTANT_FOR_ACCESSIBILITY_NO; scaleType = ImageView.ScaleType.CENTER_CROP }
    root.addView(video ?: still!!, FrameLayout.LayoutParams(-1, -1))

    var rendering = false; var pending = false
    fun renderStill(scale: Float) {
        if (still == null) return
        if (rendering) { pending = true; return }
        rendering = true
        val w = (sw * scale).toInt().coerceAtLeast(1); val h = (sh * scale).toInt().coerceAtLeast(1)
        io.execute {
            val bmp: Bitmap? = try { stillWallpaper(w, h) } catch (e: Throwable) { null }
            onUi {
                bmp?.let { still.setImageBitmap(it) }
                rendering = false
                if (pending) { pending = false; renderStill(0.5f) }
            }
        }
    }
    renderStill(1f)

    // ---- El widget real encima, a su tamaño y en su lugar ----
    val wPx = sw - ui.dp(16)
    val hPx = (sh * 0.78f).toInt()
    val holder = FrameLayout(this)
    root.addView(holder, FrameLayout.LayoutParams(wPx, hPx).apply {
        leftMargin = (sw - wPx) / 2; topMargin = (sh * 0.1f).toInt()
    })
    fun refreshWidget() {
        val app = applicationContext
        io.execute {
            val rv = try { RockWidget.buildViews(app, wPx, preview = true) } catch (e: Throwable) { null }
            onUi {
                try {
                    rv?.let {
                        val v = it.apply(app, holder)
                        systemFonts(v)
                        holder.removeAllViews(); holder.addView(v, FrameLayout.LayoutParams(wPx, hPx))
                    }
                } catch (e: Throwable) { }
            }
        }
    }
    refreshWidget()

    // ---- Capa de gestos (encima del widget, así sus botones no se activan) ----
    val gestures = View(this).apply { contentDescription = "Vista previa a pantalla completa" }
    root.addView(gestures, FrameLayout.LayoutParams(-1, -1))

    // ---- Barra de arriba ----
    val white = Color.WHITE
    fun iconButton(res: Int, desc: String) = ImageView(this).apply {
        setImageResource(res); imageTintList = android.content.res.ColorStateList.valueOf(white)
        scaleType = ImageView.ScaleType.CENTER
        background = ui.oval(Color.argb(0x66, 0, 0, 0))
        contentDescription = desc
    }
    val top = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setPadding(ui.dp(12), ui.dp(8), ui.dp(12), ui.dp(8))
    }
    val back = iconButton(R.drawable.ic_back, "Volver")
    top.addView(back, LinearLayout.LayoutParams(ui.dp(48), ui.dp(48)))
    top.addView(View(this), LinearLayout.LayoutParams(0, 1, 1f))
    val counter = ui.text("", ui.condMedium, 15f, white).apply {
        setPadding(ui.dp(12), ui.dp(4), ui.dp(12), ui.dp(4))
        background = ui.rect(Color.argb(0x66, 0, 0, 0), 14f)
        visibility = if (anim) View.VISIBLE else View.GONE
    }
    top.addView(counter, LinearLayout.LayoutParams(-2, -2).apply { marginEnd = ui.dp(10) })
    val eye = iconButton(R.drawable.ic_eye, "Ocultar widget")
    top.addView(eye, LinearLayout.LayoutParams(ui.dp(48), ui.dp(48)))
    root.addView(top, FrameLayout.LayoutParams(-1, -2, Gravity.TOP))

    // ---- Panel de abajo ----
    val panel = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        background = ui.rect(ColorUtils.setAlphaComponent(Ui.PLATE_TOP, 0xF0), 22f, Ui.veil(0x14), 1f)
        setPadding(ui.dp(18), ui.dp(16), ui.dp(18), ui.dp(16))
        isClickable = true
    }
    val panelLp = FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM).apply {
        leftMargin = ui.dp(12); rightMargin = ui.dp(12); bottomMargin = ui.dp(12)
    }
    root.addView(panel, panelLp)

    val wm = WallpaperManager.getInstance(this)
    val ourLive = wm.wallpaperInfo?.packageName == packageName

    fun close() { d.dismiss() }

    fun applyAnim() {
        val s = scenes[index]
        Prefs.setLiveScene(this, s.id); Prefs.setWallStyle(this, "anim")
        RockWidget.updateAll(this)
        close()
        if (ourLive) toast("Fondo aplicado: ${s.name}") else launchLiveWallpaper()
    }

    fun applyStill() {
        val names = arrayOf("Pantalla de inicio", "Pantalla de bloqueo", "Ambas")
        val flags = intArrayOf(WallpaperManager.FLAG_SYSTEM, WallpaperManager.FLAG_LOCK,
            WallpaperManager.FLAG_SYSTEM or WallpaperManager.FLAG_LOCK)
        ui.sheet("Aplicar en", items = names.toList(), onItem = { i ->
                toast("Aplicando…")
                io.execute {
                    val ok = try {
                        val wall = stillWallpaper(sw, sh) ?: throw IllegalStateException()
                        wm.setBitmap(wall, android.graphics.Rect(0, 0, sw, sh), true, flags[i]); true
                    } catch (e: Throwable) { false }
                    onUi { toast(if (ok) "Fondo aplicado" else "No se pudo aplicar"); if (ok) close() }
                }
            })
    }

    // Contenido del panel: datos del fondo, o los ajustes del fondo del widget
    var styleOpen = false
    lateinit var fill: () -> Unit
    fill = {
        panel.removeAllViews()
        if (styleOpen) {
            panel.addView(ui.text("FONDO DEL WIDGET", ui.anton, 19f, Ui.HEAD, 0.03f))
            val gs = Prefs.widgetStyle(this)
            panel.addView(ui.segmented(listOf("orig" to "Sin fondo", "fade" to "Desvanecido", "solid" to "Fijo"), gs) {
                Prefs.setWidgetStyle(this, it); RockWidget.updateAll(this); refreshWidget(); fill()
            }, LinearLayout.LayoutParams(-1, -2).apply { topMargin = ui.dp(12) })
            if (gs != "orig") panel.addView(colorSwatches(Prefs.widgetColor(this)) {
                Prefs.setWidgetColor(this, it); RockWidget.updateAll(this); refreshWidget(); fill()
            })
            panel.addView(ui.primary("Listo").apply { setOnClickListener { styleOpen = false; fill() } },
                LinearLayout.LayoutParams(-1, ui.dp(48)).apply { topMargin = ui.dp(14) })
        } else {
            val (name, desc) = if (anim) scenes[index].name to scenes[index].desc else when (style) {
                "fade" -> "Desvanecido" to "Color que se funde hacia abajo."
                "solid" -> "Fijo" to "Un solo color de fondo."
                "photo" -> "Tu foto" to "Pellizca para acercar y arrastra para encuadrar."
                else -> "Del tema" to "El fondo propio de ${Themes.saved(this).name}."
            }
            panel.addView(ui.text(name, ui.anton, 24f, Ui.HEAD, 0.02f).apply { isSingleLine = true; ui.fitText(this, 24, 16) },
                LinearLayout.LayoutParams(-1, ui.dp(40)))
            panel.addView(ui.note(desc), LinearLayout.LayoutParams(-1, -2).apply { topMargin = ui.dp(2) })
            if (anim && !ourLive) panel.addView(ui.text("Al aplicar se abrirá la confirmación de Android.", ui.cond, 13f, Ui.DIM),
                LinearLayout.LayoutParams(-1, -2).apply { topMargin = ui.dp(6) })
            panel.addView(LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                isBaselineAligned = false
                addView(ui.secondary("Fondo del widget").apply {
                    ui.fitText(this, 17, 12)
                    setOnClickListener { styleOpen = true; fill() }
                }, LinearLayout.LayoutParams(0, ui.dp(48), 1f))
                addView(ui.primary("Aplicar").apply {
                    setOnClickListener { if (anim) applyAnim() else applyStill() }
                }, LinearLayout.LayoutParams(0, ui.dp(48), 1f).apply { marginStart = ui.dp(10) })
            }, LinearLayout.LayoutParams(-1, -2).apply { topMargin = ui.dp(14) })
        }
    }
    fun show() {
        if (anim) {
            video?.scene = scenes[index]
            counter.text = "${index + 1}/${scenes.size}"
        }
        fill()
    }
    show()

    // ---- Botones ----
    back.setOnClickListener { close() }
    var widgetShown = true
    eye.setOnClickListener {
        widgetShown = !widgetShown
        holder.animate().alpha(if (widgetShown) 1f else 0f).setDuration(180).start()
        eye.setImageResource(if (widgetShown) R.drawable.ic_eye else R.drawable.ic_eye_off)
        eye.contentDescription = if (widgetShown) "Ocultar widget" else "Mostrar widget"
    }

    // ---- Gestos ----
    var chrome = true
    // Posición y zoom de la foto con decimales (cada movimiento es muy pequeño)
    var posF = Prefs.wpPos(this).toFloat(); var zoomF = Prefs.wpZoom(this).toFloat()
    val swipe = ui.dp(60).toFloat()
    val gd = GestureDetector(this, object : GestureDetector.SimpleOnGestureListener() {
        override fun onDown(e: MotionEvent) = true
        override fun onSingleTapUp(e: MotionEvent): Boolean {
            // Un toque oculta o muestra los controles para ver el fondo limpio
            chrome = !chrome
            listOf(top, panel).forEach { it.animate().alpha(if (chrome) 1f else 0f).setDuration(180).start(); it.isEnabled = chrome }
            panel.visibility = if (chrome) View.VISIBLE else View.INVISIBLE
            top.visibility = if (chrome) View.VISIBLE else View.INVISIBLE
            return true
        }
        override fun onScroll(e1: MotionEvent?, e2: MotionEvent, dx: Float, dy: Float): Boolean {
            if (!photoMode) return false
            // Arrastrar mueve la parte de la foto que se ve
            val p = photo ?: return false
            val wider = p.width.toFloat() / p.height > sw.toFloat() / sh
            val delta = if (Prefs.wpMode(this@openFullPreview) == 0 && wider) dx / sw else dy / sh
            posF = (posF + delta * 100f).coerceIn(0f, 100f)
            val pos = posF.toInt()
            if (pos != Prefs.wpPos(this@openFullPreview)) { Prefs.setWpPos(this@openFullPreview, pos); renderStill(0.5f) }
            return true
        }
        override fun onFling(e1: MotionEvent?, e2: MotionEvent, vx: Float, vy: Float): Boolean {
            val a = e1 ?: return false
            val dx = e2.x - a.x; val dy = e2.y - a.y
            if (anim && abs(dx) > abs(dy) && abs(dx) > swipe) {
                index = (index + (if (dx < 0) 1 else -1) + scenes.size) % scenes.size
                show(); return true
            }
            if (!photoMode && dy > swipe * 1.5f && abs(dy) > abs(dx)) { close(); return true }
            return false
        }
    })
    val sgd = ScaleGestureDetector(this, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
        override fun onScale(det: ScaleGestureDetector): Boolean {
            zoomF = (zoomF + (det.scaleFactor - 1f) * 120f).coerceIn(0f, 100f)
            val z = zoomF.toInt()
            if (z != Prefs.wpZoom(this@openFullPreview)) { Prefs.setWpZoom(this@openFullPreview, z); renderStill(0.5f) }
            return true
        }
    })
    gestures.setOnTouchListener { _, e ->
        if (photoMode) sgd.onTouchEvent(e)
        if (!(photoMode && sgd.isInProgress)) gd.onTouchEvent(e)
        if (photoMode && (e.actionMasked == MotionEvent.ACTION_UP || e.actionMasked == MotionEvent.ACTION_CANCEL)) renderStill(1f)
        true
    }

    // ---- Ventana a pantalla completa, por detrás de las barras del sistema ----
    ViewCompat.setOnApplyWindowInsetsListener(root) { _, ins ->
        val b = ins.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
        top.setPadding(ui.dp(12) + b.left, ui.dp(8) + b.top, ui.dp(12) + b.right, ui.dp(8))
        panelLp.bottomMargin = ui.dp(12) + b.bottom
        panel.layoutParams = panelLp
        ins
    }
    d.setContentView(root)
    d.window?.let { w ->
        w.setLayout(-1, -1)
        WindowCompat.setDecorFitsSystemWindows(w, false)
        @Suppress("DEPRECATION")
        w.statusBarColor = Color.TRANSPARENT
        @Suppress("DEPRECATION")
        w.navigationBarColor = Color.TRANSPARENT
        if (Build.VERSION.SDK_INT >= 28) w.attributes = w.attributes.apply {
            layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }
        WindowInsetsControllerCompat(w, w.decorView).isAppearanceLightStatusBars = false
    }
    d.setOnDismissListener { fullPreviewOpen = false; build() }
    // Entrada: crece desde un poco más pequeño, como si se abriera la miniatura
    root.alpha = 0f; root.scaleX = 0.92f; root.scaleY = 0.92f
    d.show()
    root.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(220).start()
}

/** El fondo estático elegido (del tema, desvanecido, fijo o foto) al tamaño pedido. */
internal fun MainActivity.stillWallpaper(w: Int, h: Int): Bitmap? {
    val th = Themes.current(this)
    val style = Prefs.wallStyle(this)
    return when {
        style == "photo" -> photo?.let {
            Graphics.makeWallpaper(it, w, h, Prefs.dim(this), Prefs.wpMode(this), Prefs.wpPos(this), Prefs.wpZoom(this))
        }
        th.family == "rock" && style == "orig" -> {
            val src = android.graphics.BitmapFactory.decodeResource(resources, R.drawable.fondo_bateria)
            Graphics.makeWallpaper(src, w, h, Prefs.dim(this), Prefs.wpMode(this), Prefs.wpPos(this), Prefs.wpZoom(this))
        }
        else -> Themes.styledWallpaper(this, th, w, h)
    }
}

/** Abre la pantalla de Android para poner el fondo animado de Prisma Widgets. */
internal fun MainActivity.launchLiveWallpaper() {
    val intent = Intent(WallpaperManager.ACTION_CHANGE_LIVE_WALLPAPER).putExtra(
        WallpaperManager.EXTRA_LIVE_WALLPAPER_COMPONENT, ComponentName(this, PrismaWallpaper::class.java))
    try { startActivity(intent) } catch (e: Exception) {
        try { startActivity(Intent(WallpaperManager.ACTION_LIVE_WALLPAPER_CHOOSER)) } catch (e2: Exception) { toast("Tu teléfono no permite fondos animados") }
    }
}
