package com.hossa.redoble

import android.app.Dialog
import android.graphics.Color
import android.graphics.Rect
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import androidx.core.graphics.ColorUtils
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat

/**
 * Editor visual (16.0): tocas una parte del widget Completo y abajo salen solo sus
 * opciones (orden, tamaño, letra, color, ocultar). Cada cambio se guarda y se ve al instante.
 */
private var editorOpen = false

internal fun MainActivity.openEditor() {
    if (editorOpen) return
    editorOpen = true
    val (sw, sh) = screenSize()
    val d = Dialog(this, android.R.style.Theme_DeviceDefault_NoActionBar)
    val app = applicationContext
    val names = linkedMapOf("dia" to "Día y fecha", "hora" to "Hora", "fecha" to "Fecha", "clima" to "Clima",
        "bateria" to "Batería", "player" to "Reproductor", "apps" to "Apps")
    // Vistas del widget que forman cada parte (para saber dónde tocar)
    val viewsOf = mapOf(
        "dia" to intArrayOf(R.id.day_img, R.id.day_txt, R.id.day_txt_bmp, R.id.date_row),
        "hora" to intArrayOf(R.id.clock, R.id.clock_bmp),
        "fecha" to intArrayOf(R.id.date, R.id.date_bmp),
        "clima" to intArrayOf(R.id.status_row, R.id.wx_tomorrow, R.id.wx_tomorrow_bmp),
        "bateria" to intArrayOf(R.id.batt_row),
        "player" to intArrayOf(R.id.player, R.id.player_c),
        "apps" to intArrayOf(R.id.setlist))
    var sel: String? = null

    val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(Ui.INK) }

    // ---- Barra de arriba ----
    val bar = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
        setPadding(ui.dp(8), ui.dp(4), ui.dp(12), ui.dp(4))
    }
    val back = ImageView(this).apply {
        setImageResource(R.drawable.ic_back); imageTintList = android.content.res.ColorStateList.valueOf(Ui.HEAD)
        scaleType = ImageView.ScaleType.CENTER; contentDescription = I18n.tr(this@openEditor, "Cerrar")
        background = ui.rect(Color.TRANSPARENT, 24f)
        isClickable = true; isFocusable = true
        setOnClickListener { d.dismiss() }
    }
    bar.addView(back, LinearLayout.LayoutParams(ui.dp(48), ui.dp(48)))
    bar.addView(ui.text("Editar widget", ui.condSemi, 22f), LinearLayout.LayoutParams(0, -2, 1f).apply { marginStart = ui.dp(4) })
    val reset = ui.secondary("Restablecer")
    bar.addView(reset)
    root.addView(bar, LinearLayout.LayoutParams(-1, -2))

    // ---- El widget sobre el fondo, a escala ----
    val wPx = sw - ui.dp(16)
    val hPx = (sh * 0.78f).toInt()
    val stageH = (sh * 0.46f).toInt()
    val stageW = sw - ui.dp(24)
    val scale = minOf(stageW.toFloat() / wPx, stageH.toFloat() / hPx)
    val stage = FrameLayout(this).apply {
        clipToOutline = true
        outlineProvider = object : android.view.ViewOutlineProvider() {
            override fun getOutline(view: View, outline: android.graphics.Outline) {
                outline.setRoundRect(0, 0, view.width, view.height, ui.dp(18).toFloat())
            }
        }
    }
    root.addView(stage, LinearLayout.LayoutParams((wPx * scale).toInt(), (hPx * scale).toInt()).apply {
        gravity = Gravity.CENTER_HORIZONTAL; topMargin = ui.dp(4)
    })
    val bg = ImageView(this).apply { scaleType = ImageView.ScaleType.CENTER_CROP }
    stage.addView(bg, FrameLayout.LayoutParams(-1, -1))
    io.execute {
        val bmp = try { stillWallpaper(sw / 2, sh / 2) } catch (e: Throwable) { null }
        onUi { bmp?.let { bg.setImageBitmap(it) } }
    }
    // Todo lo de adentro mide como el widget real y se reduce con escala
    val board = FrameLayout(this).apply { pivotX = 0f; pivotY = 0f; scaleX = scale; scaleY = scale }
    stage.addView(board, FrameLayout.LayoutParams(wPx, hPx))
    val holder = FrameLayout(this)
    board.addView(holder, FrameLayout.LayoutParams(wPx, hPx))
    val overlay = FrameLayout(this)
    board.addView(overlay, FrameLayout.LayoutParams(wPx, hPx))

    // ---- Panel de opciones ----
    val panelScroll = ScrollView(this).apply { isFillViewport = true }
    val panel = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(ui.dp(20), ui.dp(16), ui.dp(20), ui.dp(20))
        background = ui.rect(Ui.PLATE_TOP, 22f, Ui.headA(0x22), 1f)
    }
    panelScroll.addView(panel, FrameLayout.LayoutParams(-1, -2))
    root.addView(panelScroll, LinearLayout.LayoutParams(-1, 0, 1f).apply { topMargin = ui.dp(12) })

    lateinit var refresh: () -> Unit
    lateinit var fillPanel: () -> Unit
    var widgetRoot: ViewGroup? = null

    fun visibleParts(): List<String> {
        val fam = Themes.widget(this).family
        return names.keys.filter { p ->
            val els = p == "apps" || Prefs.el(this, p)
            val piece = p !in Prefs.HEAD_PARTS || WidgetPieces.of(fam, p) != 0
            els && piece
        }
    }

    fun drawOverlay() {
        overlay.removeAllViews()
        val wr = widgetRoot ?: return
        val minH = (ui.dp(44) / scale).toInt()
        val stroke = 1.5f / scale
        val fam = Themes.widget(this).family
        for ((p, ids) in viewsOf) {
            if (p in Prefs.HEAD_PARTS && WidgetPieces.of(fam, p) == 0 && p != "dia") continue
            var box: Rect? = null
            for (id in ids) {
                val v = wr.findViewById<View>(id) ?: continue
                if (!v.isShown || v.width == 0 || v.height == 0) continue
                val r = Rect(0, 0, v.width, v.height)
                try { wr.offsetDescendantRectToMyCoords(v, r) } catch (e: Exception) { continue }
                box = box?.apply { union(r) } ?: r
            }
            val b = box ?: continue
            if (b.height() < minH) { val c = b.centerY(); b.top = c - minH / 2; b.bottom = c + minH / 2 }
            val on = sel == p
            val pad = (6 / scale).toInt()
            overlay.addView(View(this).apply {
                background = if (on) ui.rect(ColorUtils.setAlphaComponent(ui.accent, 0x22), 12f / scale, ui.accent, 3f / scale)
                    else android.graphics.drawable.GradientDrawable().apply {
                        cornerRadius = ui.dp(12f / scale).toFloat()
                        setStroke(ui.dp(stroke), Color.argb(0x88, 255, 255, 255), ui.dp(6f / scale).toFloat(), ui.dp(5f / scale).toFloat())
                    }
                contentDescription = I18n.tr(this@openEditor, "Editar") + " " + I18n.tr(this@openEditor, names.getValue(p))
                setOnClickListener { sel = p; drawOverlay(); fillPanel() }
            }, FrameLayout.LayoutParams(b.width() + pad * 2, b.height() + pad * 2).apply {
                leftMargin = b.left - pad; topMargin = b.top - pad
            })
        }
    }

    fun changed() {
        RockWidget.updateAll(app)
        refresh()
    }

    refresh = {
        io.execute {
            val rv = try { RockWidget.buildViews(app, wPx, preview = true) } catch (e: Throwable) { null }
            onUi {
                try {
                    rv?.let {
                        val v = it.apply(app, holder)
                        holder.removeAllViews(); holder.addView(v, FrameLayout.LayoutParams(wPx, hPx))
                        widgetRoot = v as? ViewGroup
                        v.post { drawOverlay() }
                    }
                } catch (e: Throwable) { }
            }
        }
    }

    // Subir o bajar una parte. Las de la cabecera se mueven entre ellas; si ya está
    // en el borde, se mueve la cabecera entera (hora, clima y batería van juntos).
    fun move(p: String, up: Boolean) {
        val fam = Themes.widget(this).family
        if (p in Prefs.HEAD_PARTS) {
            val all = Prefs.headOrder(this).toMutableList()
            val vis = all.filter { WidgetPieces.of(fam, it) != 0 && Prefs.el(this, it) }
            val i = vis.indexOf(p)
            val j = if (up) i - 1 else i + 1
            if (i >= 0 && j in vis.indices) {
                val a = all.indexOf(p); val b = all.indexOf(vis[j])
                all[a] = vis[j]; all[b] = p
                Prefs.setHeadOrder(this, all)
                changed(); return
            }
        }
        val block = when (p) { "player" -> "player"; "apps" -> "apps"; else -> "head" }
        val bl = Prefs.blockOrder(this).toMutableList()
        val i = bl.indexOf(block); val j = if (up) i - 1 else i + 1
        if (j !in bl.indices) { toast(if (up) "Ya está arriba del todo" else "Ya está abajo del todo"); return }
        bl[i] = bl[j]; bl[j] = block
        Prefs.setBlockOrder(this, bl)
        changed()
    }

    fun label(s: String) = ui.text(s, ui.cond, 15f, Ui.DIM).apply { minWidth = ui.dp(64) }
    fun row(vararg views: View) = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
        views.forEach { addView(it) }
    }
    fun rowLp(top: Int = 14) = LinearLayout.LayoutParams(-1, -2).apply { topMargin = ui.dp(top) }

    fillPanel = {
        panel.removeAllViews()
        val p = sel
        if (p == null) {
            panel.addView(ui.text("Toca una parte del widget", ui.condSemi, 21f))
            panel.addView(ui.note("Cambia su orden, tamaño, letra y color, u ocúltala. Lo ves al instante arriba."), rowLp(6))
            val hidden = names.keys.filter { it != "apps" && !Prefs.el(this, it) &&
                (it !in Prefs.HEAD_PARTS || WidgetPieces.of(Themes.widget(this).family, it) != 0) }
            if (hidden.isNotEmpty()) {
                panel.addView(ui.label("OCULTAS · TOCA PARA VOLVER A PONERLAS"), rowLp(18))
                val chips = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
                hidden.forEach { h ->
                    chips.addView(ui.secondary("+ " + I18n.tr(this, names.getValue(h))).apply {
                        setOnClickListener { Prefs.setEl(this@openEditor, h, true); fillPanel(); changed() }
                    }, LinearLayout.LayoutParams(-2, -2).apply { marginEnd = ui.dp(8) })
                }
                panel.addView(HorizontalScrollView(this).apply { isHorizontalScrollBarEnabled = false; addView(chips) }, rowLp(8))
            }
            // Salida clara, además de la flecha y el gesto de volver
            panel.addView(ui.primary("Listo").apply { setOnClickListener { d.dismiss() } },
                LinearLayout.LayoutParams(-1, ui.dp(52)).apply { topMargin = ui.dp(24) })
        } else {
            val title = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
            title.addView(ui.text(names.getValue(p), ui.condSemi, 22f), LinearLayout.LayoutParams(0, -2, 1f))
            if (p != "apps") title.addView(ui.textButton("Ocultar").apply {
                setTextColor(Ui.ALERT)
                setOnClickListener { Prefs.setEl(this@openEditor, p, false); sel = null; fillPanel(); changed() }
            })
            panel.addView(title)

            // Orden
            panel.addView(row(label("Orden"),
                ui.secondary("↑ " + I18n.tr(this, "Subir")).apply { setOnClickListener { move(p, true) } },
                View(this).apply { layoutParams = LinearLayout.LayoutParams(ui.dp(8), 1) },
                ui.secondary("↓ " + I18n.tr(this, "Bajar")).apply { setOnClickListener { move(p, false) } }), rowLp())

            // Tamaño
            when (p) {
                "bateria" -> {}
                "player" -> panel.addView(row(label("Tamaño"), ui.segmented(listOf(false to "Compacto", true to "Grande"),
                    Prefs.playerLarge(this)) { big -> Prefs.setPlayerLarge(this, big); changed() }.apply {
                        layoutParams = LinearLayout.LayoutParams(0, -2, 1f) }), rowLp())
                else -> {
                    val k = Prefs.partScale(this, p)
                    val pct = ui.text("${(k * 100).toInt()} %", ui.condSemi, 18f).apply { gravity = Gravity.CENTER; minWidth = ui.dp(72) }
                    fun step(d: Float) {
                        val n = ((Prefs.partScale(this, p) + d) * 10).let { Math.round(it) / 10f }.coerceIn(0.7f, 1.5f)
                        Prefs.setPartScale(this, p, n); pct.text = "${(n * 100).toInt()} %"; changed()
                    }
                    panel.addView(row(label("Tamaño"),
                        ui.secondary("−").apply { contentDescription = I18n.tr(this@openEditor, "Más pequeño"); setOnClickListener { step(-0.1f) } },
                        pct,
                        ui.secondary("+").apply { contentDescription = I18n.tr(this@openEditor, "Más grande"); setOnClickListener { step(0.1f) } }), rowLp())
                }
            }

            // Letra
            if (p != "player" && p != "bateria") {
                val cur = Prefs.partFont(this, p)
                val strip = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
                TextArt.WORKSHOP_FONTS.forEach { (name, res) ->
                    strip.addView(ui.option(if (name == "tema") "Del tema" else name, cur == name).apply {
                        if (res != 0) typeface = ui.font(res)
                        setOnClickListener {
                            Prefs.setPartFont(this@openEditor, p, name)
                            // Para verse, la letra necesita «Letras del tema»
                            if (name != "tema" && !Prefs.themeFonts(this@openEditor)) Prefs.setThemeFonts(this@openEditor, true)
                            fillPanel(); changed()
                        }
                    }, LinearLayout.LayoutParams(-2, -2).apply { marginEnd = ui.dp(6) })
                }
                panel.addView(label("Letra"), rowLp())
                panel.addView(HorizontalScrollView(this).apply { isHorizontalScrollBarEnabled = false; addView(strip) }, rowLp(6))
                if (p == "hora" && cur != "tema" && !(Prefs.themeClock(this) && Prefs.liveUpdates(this)))
                    panel.addView(ui.note("La hora se ve con esta letra cuando están encendidas «Hora con la letra del tema» y «Actualización en vivo» (en Más › Rendimiento).", sp = 14f), rowLp(6))
            }

            // Color
            if (p != "player" && p != "bateria") {
                val cur = Prefs.partColor(this, p)
                val strip = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
                val colors = listOf(0 to "Del tema", Color.parseColor("#EDE6DC") to "Hueso") +
                    Prefs.ACCENTS.toList().mapIndexed { i, c -> c to Prefs.NAMES.getOrElse(i) { "" } }
                colors.forEach { (c, n) ->
                    val on = cur == c
                    strip.addView(FrameLayout(this).apply {
                        contentDescription = I18n.tr(this@openEditor, n)
                        background = if (c == 0) ui.oval(Color.TRANSPARENT, if (on) ui.accent else Ui.headA(0x88), if (on) 3f else 1.5f)
                            else ui.oval(c, if (on) Ui.HEAD else Color.TRANSPARENT, 3f)
                        if (c == 0) addView(ui.text("T", ui.condSemi, 15f, Ui.HEAD).apply { gravity = Gravity.CENTER },
                            FrameLayout.LayoutParams(-1, -1))
                        setOnClickListener { Prefs.setPartColor(this@openEditor, p, c); fillPanel(); changed() }
                    }, LinearLayout.LayoutParams(ui.dp(44), ui.dp(44)).apply { marginEnd = ui.dp(8) })
                }
                panel.addView(label("Color"), rowLp())
                panel.addView(HorizontalScrollView(this).apply { isHorizontalScrollBarEnabled = false; addView(strip) }, rowLp(6))
            }

            panel.addView(ui.primary("Listo").apply { setOnClickListener { sel = null; drawOverlay(); fillPanel() } },
                LinearLayout.LayoutParams(-1, ui.dp(52)).apply { topMargin = ui.dp(18) })
        }
    }

    reset.setOnClickListener {
        Prefs.resetEditor(this); sel = null; fillPanel(); changed()
        toast("Widget como venía")
    }

    // Bordes del sistema (barra de estado y de navegación)
    ViewCompat.setOnApplyWindowInsetsListener(root) { v, ins ->
        val b = ins.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
        v.setPadding(b.left, b.top, b.right, b.bottom); ins
    }
    d.setContentView(root)
    d.window?.let { w ->
        w.setLayout(-1, -1)
        WindowCompat.setDecorFitsSystemWindows(w, false)
        @Suppress("DEPRECATION")
        w.statusBarColor = Color.TRANSPARENT
        @Suppress("DEPRECATION")
        w.navigationBarColor = Ui.INK
    }
    // Volver siempre cierra (también con el gesto de Android 13+ y en MIUI)
    d.setCancelable(true)
    d.setOnKeyListener { _, code, e ->
        if (code == android.view.KeyEvent.KEYCODE_BACK) { if (e.action == android.view.KeyEvent.ACTION_UP) d.dismiss(); true } else false
    }
    if (android.os.Build.VERSION.SDK_INT >= 33) d.onBackInvokedDispatcher.registerOnBackInvokedCallback(
        android.window.OnBackInvokedDispatcher.PRIORITY_OVERLAY) { d.dismiss() }
    d.setOnDismissListener {
        editorOpen = false
        try { build() } catch (e: Throwable) { ErrorLog.write(this, e, "editor") }
    }
    fillPanel()
    refresh()
    d.show()
}
