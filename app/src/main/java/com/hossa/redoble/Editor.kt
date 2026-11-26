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
    // Capa de arriba para los avisos del editor (el aviso normal quedaba debajo de esta pantalla)
    val shell = FrameLayout(this).apply { addView(root, FrameLayout.LayoutParams(-1, -1)) }
    fun say(msg0: String) {
        val msg = I18n.tr(this, msg0)
        shell.findViewWithTag<View>("edPill")?.let { shell.removeView(it) }
        val pill = ui.text(msg, ui.condMedium, 16f, Ui.HEAD).apply {
            tag = "edPill"; gravity = Gravity.CENTER
            background = ui.rect(Ui.PLATE_TOP, 24f, ui.accent, 1.5f)
            setPadding(ui.dp(20), ui.dp(12), ui.dp(20), ui.dp(12))
            elevation = ui.dp(8).toFloat(); alpha = 0f
        }
        shell.addView(pill, FrameLayout.LayoutParams(-2, -2, Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL).apply {
            bottomMargin = root.paddingBottom + ui.dp(28); marginStart = ui.dp(24); marginEnd = ui.dp(24)
        })
        pill.animate().alpha(1f).setDuration(160).start()
        pill.postDelayed({ pill.animate().alpha(0f).setDuration(250).withEndAction { shell.removeView(pill) }.start() }, 2600)
        root.announceForAccessibility(msg)
    }

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
    // Arriba a la derecha, donde se busca «Guardar»: Listo (los cambios ya quedaron guardados)
    bar.addView(ui.primary("Listo").apply { setOnClickListener { d.dismiss() } })
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
    val bg = ImageView(this).apply { importantForAccessibility = android.view.View.IMPORTANT_FOR_ACCESSIBILITY_NO; scaleType = ImageView.ScaleType.CENTER_CROP }
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
        clipToPadding = false
    }
    panelScroll.addView(panel, FrameLayout.LayoutParams(-1, -2))
    root.addView(panelScroll, LinearLayout.LayoutParams(-1, 0, 1f).apply { topMargin = ui.dp(12) })

    lateinit var refresh: () -> Unit
    lateinit var fillPanel: () -> Unit
    var widgetRoot: ViewGroup? = null

    // Partes que de verdad se ven ahora en el widget (para el panel)
    var shown = listOf<String>()

    lateinit var canMove: (String, Boolean) -> Boolean
    lateinit var doMove: (String, Boolean) -> Unit
    fun drawOverlay() {
        overlay.removeAllViews()
        val wr = widgetRoot ?: return
        val minH = (ui.dp(48) / scale).toInt()
        val minW = (ui.dp(48) / scale).toInt()
        val pad = (ui.dp(5) / scale).toInt()
        val fam = Themes.widget(this).family
        val boxes = LinkedHashMap<String, Rect>()
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
            b.inset(-pad, -pad)
            if (b.height() < minH) { val c = b.centerY(); b.top = c - minH / 2; b.bottom = c + minH / 2 }
            if (b.width() < minW) { val c = b.centerX(); b.left = c - minW / 2; b.right = c + minW / 2 }
            boxes[p] = b
        }
        // Si dos se pisan (uno encima del otro), se reparten el espacio por la mitad
        val list = boxes.entries.sortedBy { it.value.centerY() }
        for (i in list.indices) for (j in i + 1 until list.size) {
            val a = list[i].value; val c = list[j].value
            val sideBySide = a.right <= c.left || c.right <= a.left
            if (!sideBySide && a.bottom > c.top) {
                val cut = ((a.bottom + c.top) / 2).coerceIn(a.top + 1, (c.bottom - 1).coerceAtLeast(a.top + 1))
                a.bottom = cut; c.top = cut
            }
        }
        // Siempre dentro del widget. El borde se dibuja con lo repartido (ya no se cruzan);
        // solo el área que se toca crece hasta 48 dp, y las chicas quedan encima.
        boxes.values.forEach { it.intersect(0, 0, wPx, hPx) }
        shown = boxes.keys.toList()
        for ((p, b) in boxes.entries.sortedByDescending { it.value.width().toLong() * it.value.height() }.map { it.key to it.value }) {
            val on = sel == p
            val real = Rect(b)
            if (b.height() < minH) { val c = b.centerY(); b.top = (c - minH / 2).coerceAtLeast(0); b.bottom = (c + minH / 2).coerceAtMost(hPx) }
            val face = if (on) ui.rect(ColorUtils.setAlphaComponent(ui.accent, 0x22), 12f / scale, ui.accent, 3f / scale)
                else android.graphics.drawable.GradientDrawable().apply {
                    cornerRadius = ui.dp(12f / scale).toFloat()
                    setStroke(ui.dp(1.5f / scale), Color.argb(0x88, 255, 255, 255), ui.dp(6f / scale).toFloat(), ui.dp(5f / scale).toFloat())
                }
            overlay.addView(View(this).apply {
                background = android.graphics.drawable.InsetDrawable(face, real.left - b.left, real.top - b.top, b.right - real.right, b.bottom - real.bottom)
                contentDescription = I18n.tr(this@openEditor, "Editar") + " " + I18n.tr(this@openEditor, names.getValue(p))
                setOnClickListener { sel = p; drawOverlay(); fillPanel() }
                // Mantén presionado y desliza arriba o abajo para cambiar el orden
                var dragging = false; var y0 = 0f
                setOnLongClickListener {
                    dragging = true; sel = p; fillPanel()
                    performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS)
                    alpha = 0.7f; elevation = 8f
                    say("Desliza para moverlo"); true
                }
                setOnTouchListener { v, e ->
                    when (e.actionMasked) {
                        android.view.MotionEvent.ACTION_DOWN -> { y0 = e.rawY; false }
                        android.view.MotionEvent.ACTION_MOVE -> if (dragging) { v.translationY = (e.rawY - y0) / scale; true } else false
                        android.view.MotionEvent.ACTION_UP, android.view.MotionEvent.ACTION_CANCEL -> {
                            if (!dragging) false else {
                                dragging = false; v.alpha = 1f; v.translationY = 0f
                                val dy = (e.rawY - y0) / scale
                                val steps = Math.round(dy / maxOf(real.height(), minH).toFloat())
                                if (steps != 0) repeat(kotlin.math.abs(steps)) { if (canMove(p, steps < 0)) doMove(p, steps < 0) }
                                else drawOverlay()
                                true
                            }
                        }
                        else -> false
                    }
                }
            }, FrameLayout.LayoutParams(b.width(), b.height()).apply { leftMargin = b.left; topMargin = b.top })
            // Nombre de la parte elegida, pegado a su recuadro
            if (on) overlay.addView(ui.text(names.getValue(p), ui.condSemi, 13f / scale, ui.onAccent).apply {
                background = ui.rect(ui.accent, 8f / scale)
                setPadding(ui.dp(8f / scale), ui.dp(2f / scale), ui.dp(8f / scale), ui.dp(2f / scale))
                importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            }, FrameLayout.LayoutParams(-2, -2).apply {
                leftMargin = real.left + ui.dp(6f / scale)
                topMargin = (real.top - ui.dp(22f / scale)).coerceAtLeast(0)
            })
        }
    }

    fun changed() {
        RockWidget.updateAll(app)
        refresh()
    }

    refresh = {
        io.execute {
            val rv = try { RockWidget.buildViews(app, wPx, preview = true, sample = true) } catch (e: Throwable) { null }
            onUi {
                try {
                    rv?.let {
                        val v = it.apply(app, holder)
                        systemFonts(v)
                        holder.removeAllViews(); holder.addView(v, FrameLayout.LayoutParams(wPx, hPx))
                        widgetRoot = v as? ViewGroup
                        v.post { drawOverlay(); if (sel == null) fillPanel() }
                    }
                } catch (e: Throwable) { }
            }
        }
    }

    // Bloques que se ven (los ocultos no cuentan al mover: si no, «no pasaba nada»)
    fun blockVisible(b: String) = when (b) {
        "player" -> Prefs.el(this, "player")
        "apps" -> Prefs.appRows(this) > 0
        else -> Prefs.HEAD_PARTS.any { Prefs.el(this, it) }
    }
    fun blockOf(p: String) = when (p) { "player" -> "player"; "apps" -> "apps"; else -> "head" }
    fun headVisible(): List<String> {
        val fam = Themes.widget(this).family
        return Prefs.headOrder(this).filter { WidgetPieces.of(fam, it) != 0 && Prefs.el(this, it) && (shown.isEmpty() || it in shown) }
    }
    /** Hacia dónde puede ir: null = no se mueve; "parte" = entre las de la cabecera; "bloque" = el bloque entero. */
    fun moveKind(p: String, up: Boolean): String? {
        if (p in Prefs.HEAD_PARTS) {
            val vis = headVisible(); val i = vis.indexOf(p); val j = if (up) i - 1 else i + 1
            if (i >= 0 && j in vis.indices) return "parte"
        }
        val bl = Prefs.blockOrder(this).filter { blockVisible(it) || it == blockOf(p) }
        val i = bl.indexOf(blockOf(p)); val j = if (up) i - 1 else i + 1
        return if (j in bl.indices) "bloque" else null
    }

    var focusArrow: Boolean? = null
    fun move(p: String, up: Boolean) {
        when (moveKind(p, up)) {
            null -> { say(if (up) "Ya está arriba del todo" else "Ya está abajo del todo"); return }
            "parte" -> {
                val all = Prefs.headOrder(this).toMutableList()
                val vis = headVisible(); val i = vis.indexOf(p); val other = vis[if (up) i - 1 else i + 1]
                val a = all.indexOf(p); val b = all.indexOf(other)
                all[a] = other; all[b] = p
                Prefs.setHeadOrder(this, all)
                val pos = headVisible().indexOf(p)
                say(I18n.tr(this, names.getValue(p)) + ": " + I18n.tr(this, "puesto {} de {}")
                    .replaceFirst("{}", "${pos + 1}").replaceFirst("{}", "${headVisible().size}"))
            }
            else -> {
                val block = blockOf(p)
                val full = Prefs.blockOrder(this).toMutableList()
                val vis = full.filter { blockVisible(it) || it == block }
                val i = vis.indexOf(block); val target = vis[if (up) i - 1 else i + 1]
                // Se intercambia con el bloque visible de al lado (saltando los ocultos)
                val a = full.indexOf(block); val b = full.indexOf(target)
                full[a] = target; full[b] = block
                Prefs.setBlockOrder(this, full)
                val bv = Prefs.blockOrder(this).filter { blockVisible(it) }
                if (block == "head") say("Se movió toda la cabecera: día, fecha, hora, clima y batería van juntos")
                else say(I18n.tr(this, names.getValue(p)) + ": " + I18n.tr(this, "bloque {} de {}")
                    .replaceFirst("{}", "${bv.indexOf(block) + 1}").replaceFirst("{}", "${bv.size}"))
            }
        }
        focusArrow = up
        changed(); fillPanel()
    }

    canMove = { p, up -> moveKind(p, up) != null }
    doMove = { p, up -> move(p, up) }

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
            panel.addView(ui.note("Cambia su orden, tamaño, letra y color, u ocúltala. Cada cambio se guarda solo, al instante."), rowLp(6))
            val fam = Themes.widget(this).family
            fun exists(k: String) = k !in Prefs.HEAD_PARTS || k == "dia" || WidgetPieces.of(fam, k) != 0
            fun chipRow(items: List<Pair<String, () -> Unit>>, dashed: Boolean): View {
                val chips = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
                items.forEach { (t, act) ->
                    chips.addView(ui.secondary(t).apply {
                        if (dashed) background = android.graphics.drawable.GradientDrawable().apply {
                            cornerRadius = ui.dp(12).toFloat()
                            setStroke(ui.dp(1.5f), Ui.headA(0x88), ui.dp(6).toFloat(), ui.dp(4).toFloat())
                        }
                        setOnClickListener { act() }
                    }, LinearLayout.LayoutParams(-2, -2).apply { marginEnd = ui.dp(8) })
                }
                return HorizontalScrollView(this).apply {
                    isHorizontalScrollBarEnabled = false; clipToPadding = false
                    setPadding(ui.dp(20), 0, ui.dp(20), 0); addView(chips)
                }
            }
            // Partes: otra forma de elegir (y la que usa TalkBack sin tener que buscar en el dibujo)
            if (shown.isNotEmpty()) {
                panel.addView(ui.label("PARTES"), rowLp(18))
                panel.addView(chipRow(shown.map { k -> I18n.tr(this, names.getValue(k)) to { sel = k; drawOverlay(); fillPanel() } }, false),
                    rowLp(8).apply { marginStart = -ui.dp(20); marginEnd = -ui.dp(20) })
            }
            val hidden = names.keys.filter { it != "apps" && exists(it) && !Prefs.elRaw(this, it) }
            if (hidden.isNotEmpty()) {
                panel.addView(ui.label("OCULTAS · TOCA PARA VOLVER A PONERLAS"), rowLp(18))
                panel.addView(chipRow(hidden.map { h -> ("+ " + I18n.tr(this, names.getValue(h))) to {
                    Prefs.setEl(this@openEditor, h, true); fillPanel(); changed() } }, true),
                    rowLp(8).apply { marginStart = -ui.dp(20); marginEnd = -ui.dp(20) })
            }
            // Por qué falta algo que está encendido
            val why = mutableListOf<String>()
            if (Prefs.elRaw(this, "bateria") && Rules.musicLayout(this))
                why += "La batería no se ve mientras tienes audífonos (regla «Con audífonos» en Estilo › Cambio automático)."
            if (Prefs.el(this, "clima") && widgetRoot != null && "clima" !in shown)
                why += if (Prefs.city(this).isBlank()) "El clima no se ve porque falta tu ciudad (Widget › Clima)."
                       else "El clima no se ve porque el dato es viejo. Se actualiza solo en unos minutos."
            if (Prefs.appRows(this) == 0) why += "Las apps no se ven porque elegiste 0 apps (Widget › Apps)."
            why.forEach { panel.addView(ui.note(it, sp = 14f), rowLp(10)) }
            // Salida clara, además de la flecha y el gesto de volver
            panel.addView(ui.primary("Listo").apply { setOnClickListener { d.dismiss() } },
                LinearLayout.LayoutParams(-1, ui.dp(52)).apply { topMargin = ui.dp(24) })
            // Volver al diseño original: abajo, discreto y con confirmación
            panel.addView(ui.textButton("Volver al diseño original").apply {
                setTextColor(Ui.ALERT)
                setOnClickListener {
                    android.app.AlertDialog.Builder(this@openEditor)
                        .setMessage(I18n.tr(this@openEditor, "¿Deshacer todos los cambios del editor (orden, tamaños, letras y colores)?"))
                        .setPositiveButton(I18n.tr(this@openEditor, "Deshacer")) { _, _ ->
                            Prefs.resetEditor(this@openEditor); sel = null; fillPanel(); changed()
                        }
                        .setNegativeButton(I18n.tr(this@openEditor, "Cancelar"), null).show()
                }
            }, LinearLayout.LayoutParams(-1, -2).apply { topMargin = ui.dp(8) })
        } else {
            val title = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
            title.addView(ui.text(names.getValue(p), ui.condSemi, 22f), LinearLayout.LayoutParams(0, -2, 1f))
            if (p != "apps") title.addView(ui.textButton("Ocultar").apply {
                setTextColor(Ui.ALERT)
                setOnClickListener { Prefs.setEl(this@openEditor, p, false); sel = null; fillPanel(); changed() }
            })
            panel.addView(title)
            if (p == "player" && !Prefs.lastPlaying(this))
                panel.addView(ui.note("Sin música suena nada: lo de arriba es un ejemplo de cómo se verá.", sp = 13f), rowLp(2))

            // Orden: flechas apagadas en los extremos y un mapa de los tres bloques
            fun arrow(up: Boolean) = ui.secondary((if (up) "↑ " else "↓ ") + I18n.tr(this, if (up) "Subir" else "Bajar")).apply {
                val k = moveKind(p, up)
                isEnabled = k != null; alpha = if (k != null) 1f else 0.4f
                contentDescription = I18n.tr(this@openEditor, if (up) "Subir" else "Bajar") + " " + I18n.tr(this@openEditor, names.getValue(p)) +
                    (if (k == "bloque" && p in Prefs.HEAD_PARTS) ", " + I18n.tr(this@openEditor, "mueve toda la cabecera") else "") +
                    (if (k == null) ", " + I18n.tr(this@openEditor, "no disponible") else "")
                setOnClickListener { move(p, up) }
                // TalkBack sigue en la misma flecha después de rehacer el panel
                if (focusArrow == up) {
                    focusArrow = null
                    post { performAccessibilityAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_ACCESSIBILITY_FOCUS, null) }
                }
            }
            panel.addView(row(label("Orden"), arrow(true),
                View(this).apply { layoutParams = LinearLayout.LayoutParams(ui.dp(8), 1) }, arrow(false)), rowLp())
            val map = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
            val blockNames = mapOf("head" to "Cabecera", "player" to "Reproductor", "apps" to "Apps")
            Prefs.blockOrder(this).filter { blockVisible(it) || it == blockOf(p) }.forEachIndexed { i, b ->
                if (i > 0) map.addView(ui.text("›", ui.cond, 15f, Ui.DIM).apply { setPadding(ui.dp(6), 0, ui.dp(6), 0) })
                val here = b == blockOf(p)
                map.addView(ui.text(blockNames.getValue(b), if (here) ui.condSemi else ui.cond, 14f, if (here) Ui.HEAD else Ui.DIM).apply {
                    background = if (here) ui.rect(ColorUtils.setAlphaComponent(ui.accent, 0x33), 8f, ui.accent, 1f) else null
                    setPadding(ui.dp(8), ui.dp(4), ui.dp(8), ui.dp(4))
                })
            }
            panel.addView(row(label("Bloques"), HorizontalScrollView(this).apply { isHorizontalScrollBarEnabled = false; addView(map) }), rowLp(8))
            if (p in Prefs.HEAD_PARTS) panel.addView(ui.note("Día, hora, fecha, clima y batería se ordenan entre ellos. Al pasar el borde, se mueve toda la cabecera.", sp = 13f), rowLp(6))

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
                    panel.addView(android.widget.SeekBar(this).apply {
                        max = 16; progress = Math.round((k - 0.7f) / 0.05f)
                        contentDescription = I18n.tr(this@openEditor, "Tamaño")
                        progressTintList = android.content.res.ColorStateList.valueOf(ui.accent)
                        thumbTintList = android.content.res.ColorStateList.valueOf(ui.accent)
                        minimumHeight = ui.dp(48)
                        setOnSeekBarChangeListener(object : android.widget.SeekBar.OnSeekBarChangeListener {
                            override fun onProgressChanged(sb: android.widget.SeekBar, v: Int, fromUser: Boolean) {
                                pct.text = "${70 + v * 5} %"
                                sb.stateDescription = "${70 + v * 5} %"
                            }
                            override fun onStartTrackingTouch(sb: android.widget.SeekBar) {}
                            override fun onStopTrackingTouch(sb: android.widget.SeekBar) {
                                Prefs.setPartScale(this@openEditor, p, 0.7f + sb.progress * 0.05f); changed()
                            }
                        })
                    }, LinearLayout.LayoutParams(-1, ui.dp(48)).apply { topMargin = ui.dp(4) })
                }
            }

            // Opciones propias de cada parte (antes repartidas en otras secciones)
            when (p) {
                "clima" -> panel.addView(switchRow("Pronóstico de mañana", Prefs.el(this, "manana")) { on ->
                    Prefs.setEl(this, "manana", on); changed() }, rowLp())
                "apps" -> panel.addView(row(label("Cantidad"), ui.segmented(listOf(0 to "Ninguna", 3 to "1 fila", 6 to "2 filas"),
                    Prefs.appRows(this) * 3) { v -> Prefs.setAppsCount(this, v); changed(); if (v == 0) { sel = null; fillPanel() } }.apply {
                        layoutParams = LinearLayout.LayoutParams(0, -2, 1f) }), rowLp())
                "player" -> panel.addView(row(label("Pegado a"), ui.segmented(listOf(true to "Cabecera", false to "Apps"),
                    Prefs.playerTop(this)) { top -> Prefs.setPlayerTop(this, top); changed() }.apply {
                        layoutParams = LinearLayout.LayoutParams(0, -2, 1f) }), rowLp())
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
                    panel.addView(ui.note("La hora se ve con esta letra cuando está encendida «Hora y fecha con la letra del tema» (en Estilo › Tema).", sp = 14f), rowLp(6))
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

            panel.addView(ui.secondary("Elegir otra parte").apply { setOnClickListener { sel = null; drawOverlay(); fillPanel() } },
                LinearLayout.LayoutParams(-1, ui.dp(52)).apply { topMargin = ui.dp(18) })
        }
    }


    // Bordes del sistema (barra de estado y de navegación)
    ViewCompat.setOnApplyWindowInsetsListener(root) { v, ins ->
        val b = ins.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
        v.setPadding(b.left, b.top, b.right, b.bottom); ins
    }
    d.setContentView(shell)
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
