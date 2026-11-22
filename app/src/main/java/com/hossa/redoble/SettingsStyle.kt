package com.hossa.redoble

import android.content.ComponentName
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Outline
import android.graphics.drawable.ClipDrawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
import android.net.Uri
import android.os.Build
import android.provider.OpenableColumns
import android.view.Gravity
import android.view.View
import android.view.ViewOutlineProvider
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.SeekBar
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts

// Ajustes · Estilo: tema, fondos (de pantalla y del widget), foto y acento.
// Son extensiones de MainActivity para no tener un solo archivo de 1200 líneas.

internal fun MainActivity.smallPhoto(): Bitmap? {
    val p = photo ?: return null
    photoSmall?.let { return it }
    val s = 900f / maxOf(p.width, p.height)
    val sm = if (s < 1f) Bitmap.createScaledBitmap(p, (p.width * s).toInt(), (p.height * s).toInt(), true) else p
    photoSmall = sm
    return sm
}

internal fun MainActivity.renderPreview(iv: ImageView) {
    val sm = smallPhoto() ?: return
    val (w, h) = screenSize()
    val k = 360f / w
    iv.setImageBitmap(
        Graphics.makeWallpaper(sm, (w * k).toInt(), (h * k).toInt(), Prefs.dim(this),
            Prefs.wpMode(this), Prefs.wpPos(this), Prefs.wpZoom(this))
    )
}

internal fun MainActivity.thumb(res: Int): Bitmap? = thumbs[res] ?: try {
    val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeResource(resources, res, o)
    var s = 1
    while (maxOf(o.outWidth, o.outHeight) / (s * 2) >= 360) s *= 2
    BitmapFactory.decodeResource(resources, res, BitmapFactory.Options().apply { inSampleSize = s })
        ?.also { thumbs[res] = it }
} catch (e: Exception) { null }

internal fun MainActivity.useSuggested(res: Int, name: String) {
    io.execute {
        val bmp = try { BitmapFactory.decodeResource(resources, res, BitmapFactory.Options().apply { inScaled = false }) } catch (e: Throwable) { null }
        if (bmp != null) {
            try { photoFile.outputStream().use { bmp.compress(Bitmap.CompressFormat.JPEG, 95, it) } } catch (e: Exception) { }
            getSharedPreferences("redoble", android.content.Context.MODE_PRIVATE).edit().putString("photoName", name).apply()
        }
        onUi {
            if (bmp == null) toast("No pudimos abrir ese fondo") else {
                photo = bmp; photoSmall = null
                Prefs.setWpPos(this, 50); Prefs.setWpZoom(this, 0)
                build()
            }
        }
    }
}

internal fun MainActivity.suggestedRow(): View {
    val current = if (photo != null) getSharedPreferences("redoble", android.content.Context.MODE_PRIVATE).getString("photoName", "") else ""
    val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
    suggested.forEachIndexed { i, (res, name) ->
        val on = name == current
        val tile = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setOnClickListener { if (!on) useSuggested(res, name) }
        }
        val img = ImageView(this).apply {
            scaleType = ImageView.ScaleType.CENTER_CROP
            setImageBitmap(thumb(res))
            clipToOutline = true
            outlineProvider = object : ViewOutlineProvider() {
                override fun getOutline(view: View, outline: Outline) {
                    outline.setRoundRect(0, 0, view.width, view.height, ui.dp(12).toFloat())
                }
            }
            foreground = ui.rect(Color.TRANSPARENT, 12f, if (on) ui.accent else Ui.headA(0x33), if (on) 2.5f else 1f)
        }
        tile.addView(img, LinearLayout.LayoutParams(ui.dp(76), ui.dp(120)))
        tile.addView(ui.text(name, ui.cond, 13f, if (on) Ui.HEAD else Ui.MUTED).apply {
            gravity = Gravity.CENTER; maxLines = 1
        }, LinearLayout.LayoutParams(ui.dp(84), -2).apply { topMargin = ui.dp(6) })
        row.addView(tile, LinearLayout.LayoutParams(-2, -2).apply { if (i > 0) marginStart = ui.dp(8) })
    }
    return HorizontalScrollView(this).apply {
        isHorizontalScrollBarEnabled = false
        addView(row)
    }
}

/**
 * Fondo de pantalla, todo en un solo lugar: el del tema, un color desvanecido,
 * un color fijo o una foto. Los ajustes finos de la foto van plegados.
 */
internal fun MainActivity.wallpaperPlate(): View {
    val card = ui.plateCard()
    val th = Themes.current(this)
    card.addView(ui.sectionTitle("Fondo de pantalla"))
    val ws = Prefs.wallStyle(this)
    card.addView(ui.segmented(listOf("orig" to "Tema", "fade" to "Desvanecido", "solid" to "Fijo", "photo" to "Foto", "anim" to "Animado"), ws) {
        Prefs.setWallStyle(this, it); RockWidget.updateAll(this); build()
    })

    if (ws == "fade" || ws == "solid") {
        card.addView(colorSwatches(Prefs.wallColor(this)) { Prefs.setWallColor(this, it); RockWidget.updateAll(this); build() })
    }
    if (ws == "anim") {
        animSection(card)
    } else if (ws != "photo") {
        // Mismo nombre de acción en todos los fondos: "Ver y aplicar" lleva a la pantalla completa, donde se aplica
        val apply = ui.primary("Ver y aplicar")
        apply.setOnClickListener { openFullPreview() }
        card.addView(apply, LinearLayout.LayoutParams(-1, ui.dp(48)).apply { topMargin = ui.dp(14) })
    } else {
        photoSection(card)
    }
    // Los fondos fijos preguntan dónde ponerlos; el animado lo confirma Android
    if (ws != "anim") card.addView(ui.note("Al aplicar eliges inicio, bloqueo o ambas."),
        LinearLayout.LayoutParams(-1, -2).apply { topMargin = ui.dp(10) })
    return card
}

/** Fondo animado: grilla de miniaturas de cada escena y botón para ponerlo. */
internal fun MainActivity.animSection(card: LinearLayout) {
    val info = VideoScenes.find(Prefs.liveScene(this))
    // Ancho real de la ventana (sirve también en pantalla dividida)
    val sw = resources.displayMetrics.widthPixels
    // Ancho útil de la tarjeta: pantalla menos márgenes (16) y relleno (18) a cada lado
    val gap = ui.dp(8)
    val tileW = (sw - ui.dp(16 + 18) * 2 - gap * 2) / 3
    val tileH = (tileW * 1.45f).toInt()
    val grid = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
    VideoScenes.ALL.chunked(3).forEach { trio ->
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; isBaselineAligned = false }
        trio.forEachIndexed { i, s -> row.addView(sceneTile(s, s.id == info.id, tileW, tileH),
            LinearLayout.LayoutParams(tileW, -2).apply { if (i > 0) marginStart = gap }) }
        grid.addView(row, LinearLayout.LayoutParams(-1, -2).apply { topMargin = ui.dp(10) })
    }
    card.addView(grid, LinearLayout.LayoutParams(-1, -2).apply { topMargin = ui.dp(6) })
    val apply = ui.primary("Ver y aplicar")
    apply.setOnClickListener { openFullPreview() }
    card.addView(apply, LinearLayout.LayoutParams(-1, ui.dp(48)).apply { topMargin = ui.dp(14) })
    card.addView(ui.note("Toca uno para verlo; mantenlo presionado para elegirlo.",
        "Al tocar uno lo ves a pantalla completa, donde puedes deslizar para ver los demás y aplicarlo. " +
        "Se mueve solo con la pantalla encendida y en el inicio; al abrir una app o bloquear el teléfono se pausa para no gastar batería."),
        LinearLayout.LayoutParams(-1, -2).apply { topMargin = ui.dp(8) })
}

/** Miniatura de una escena: imagen, nombre debajo y borde del acento si es la elegida. */
internal fun MainActivity.sceneTile(s: VideoScenes.Info, on: Boolean, w: Int, h: Int): View {
    val img = android.widget.ImageView(this).apply {
        scaleType = android.widget.ImageView.ScaleType.CENTER_CROP
        VideoScenes.thumb(s.id)?.let { setImageResource(it) }
        clipToOutline = true
        outlineProvider = object : ViewOutlineProvider() {
            override fun getOutline(view: View, outline: Outline) { outline.setRoundRect(0, 0, view.width, view.height, ui.dp(12).toFloat()) }
        }
    }
    val frame = FrameLayout(this).apply {
        addView(img, FrameLayout.LayoutParams(-1, -1))
        foreground = ui.rect(Color.TRANSPARENT, 12f, if (on) ui.accent else Ui.headA(0x24), if (on) 2.5f else 1f)
        if (on) addView(ui.text("✓", ui.condSemi, 13f, ui.onAccent).apply {
            gravity = Gravity.CENTER
            background = ui.oval(ui.accent)
        }, FrameLayout.LayoutParams(ui.dp(22), ui.dp(22), Gravity.TOP or Gravity.END).apply { topMargin = ui.dp(6); marginEnd = ui.dp(6) })
    }
    return LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        addView(frame, LinearLayout.LayoutParams(w, h))
        addView(ui.text(s.name, if (on) ui.condSemi else ui.cond, 13f, if (on) Ui.HEAD else Ui.MUTED).apply {
            gravity = Gravity.CENTER
            ui.fitText(this, 13)
            setPadding(0, ui.dp(4), 0, 0)
        }, LinearLayout.LayoutParams(-1, ui.dp(26)))
        isClickable = true
        foreground = ui.rowBg()
        contentDescription = "${s.name}: ${s.desc}" + if (on) ", elegido" else ""
        // Tocar: verla a pantalla completa (ahí se aplica). Mantener presionado: elegirla directo.
        setOnClickListener { openFullPreview(VideoScenes.ALL.indexOf(s)) }
        setOnLongClickListener {
            if (!on) { Prefs.setLiveScene(this@sceneTile, s.id); RockWidget.updateAll(this@sceneTile); build() }
            true
        }
    }
}

/** Fotos sugeridas o del celular, con vista previa; posición, zoom y oscurecer plegados. */
internal fun MainActivity.photoSection(card: LinearLayout) {
    card.addView(ui.label("Sugeridos"),
        LinearLayout.LayoutParams(-2, -2).apply { topMargin = ui.dp(16); bottomMargin = ui.dp(8) })
    card.addView(suggestedRow(), LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = ui.dp(16) })

    val (sw, sh) = screenSize()
    val pvW = ui.dp(150)
    val pvH = (pvW * sh.toFloat() / sw).toInt()
    val preview = FrameLayout(this).apply {
        clipToOutline = true
        outlineProvider = object : ViewOutlineProvider() {
            override fun getOutline(view: View, outline: Outline) {
                outline.setRoundRect(0, 0, view.width, view.height, ui.dp(16).toFloat())
            }
        }
    }
    val iv = ImageView(this).apply { scaleType = ImageView.ScaleType.FIT_XY }
    val hasPhoto = photo != null
    if (hasPhoto) {
        preview.background = ui.rect(Color.BLACK, 16f)
        preview.addView(iv, FrameLayout.LayoutParams(-1, -1))
        preview.foreground = ui.rect(Color.TRANSPARENT, 16f, Ui.headA(0x33), 1f)
        renderPreview(iv)
    } else {
        preview.background = GradientDrawable().apply {
            setColor(Ui.WELL); cornerRadius = ui.dp(16).toFloat()
            setStroke(ui.dp(1.5f), Ui.headA(0x47), ui.dp(6).toFloat(), ui.dp(4).toFloat())
        }
        preview.addView(ui.text("Elige un sugerido\no una foto tuya", ui.cond, 16f, Ui.MUTED).apply { gravity = Gravity.CENTER },
            FrameLayout.LayoutParams(-1, -1))
    }
    card.addView(preview, LinearLayout.LayoutParams(pvW, pvH).apply { gravity = Gravity.CENTER_HORIZONTAL })
    if (hasPhoto) {
        val name = getSharedPreferences("redoble", android.content.Context.MODE_PRIVATE).getString("photoName", "Tu foto") ?: "Tu foto"
        card.addView(ui.ellipsize(ui.text(name, ui.cond, 14f, Ui.MUTED)).apply { gravity = Gravity.CENTER },
            LinearLayout.LayoutParams(-1, -2).apply { topMargin = ui.dp(8) })

        // Ajustes finos, plegados
        val more = ui.expander("Ajustar foto", photoOpen) { photoOpen = !photoOpen; build() }
        card.addView(more, LinearLayout.LayoutParams(-1, -2).apply { topMargin = ui.dp(8) })
        if (photoOpen) {
            val mode = Prefs.wpMode(this)
            card.addView(ui.segmented(listOf(1 to "Foto completa", 0 to "Llenar pantalla"), mode) { m ->
                Prefs.setWpMode(this, m); Prefs.setWpPos(this, 50); Prefs.setWpZoom(this, 0); build()
            })
            card.addView(ui.note(
                if (mode == 1) "Se ve la foto entera." else "La foto cubre la pantalla.",
                if (mode == 1) "Se ve la foto entera; los bordes se funden con el negro."
                else "La foto cubre toda la pantalla; con Posición eliges qué parte se ve."
            ), LinearLayout.LayoutParams(-1, -2).apply { topMargin = ui.dp(8) })
            card.addView(sliderRow("Posición", Prefs.wpPos(this), 100, "%") { v, _ ->
                Prefs.setWpPos(this, v); renderPreview(iv)
            })
            card.addView(sliderRow("Zoom", Prefs.wpZoom(this), 100, "%") { v, _ ->
                Prefs.setWpZoom(this, v); renderPreview(iv)
            })
            card.addView(sliderRow("Oscurecer", Prefs.dim(this), 85, "%", showScale = true) { v, _ ->
                Prefs.setDim(this, v); renderPreview(iv)
            })
        }
    }

    val pick = ui.secondary("Foto del celular").apply {
        setOnClickListener { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }
    }
    val apply = ui.primary("Ver y aplicar")
    apply.setOnClickListener { openFullPreview() }
    card.addView(LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        addView(pick, LinearLayout.LayoutParams(0, ui.dp(48), 1f))
        addView(apply, LinearLayout.LayoutParams(0, ui.dp(48), 1f).apply { marginStart = ui.dp(10) })
    }, LinearLayout.LayoutParams(-1, -2).apply { topMargin = ui.dp(16) })
}

/** Fila con etiqueta, valor y fader. onChange(valor, soltó) */
internal fun MainActivity.sliderRow(label: String, value: Int, max: Int, unit: String, showScale: Boolean = false,
                      onChange: (Int, Boolean) -> Unit): View {
    val box = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        layoutParams = LinearLayout.LayoutParams(-1, -2).apply { topMargin = ui.dp(14) }
    }
    val valueTv = ui.text("$value $unit", ui.condMedium, 18f, Ui.HEAD)
    box.addView(LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        addView(ui.text(label, ui.cond, 16f, Ui.HEAD), LinearLayout.LayoutParams(0, -2, 1f))
        addView(valueTv)
    })
    val sb = fader(max)
    sb.progress = value
    var last = 0L
    sb.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
        override fun onProgressChanged(s: SeekBar, v: Int, fromUser: Boolean) {
            valueTv.text = "$v $unit"
            val now = System.currentTimeMillis()
            if (fromUser && now - last > 60) { last = now; onChange(v, false) }
        }
        override fun onStartTrackingTouch(s: SeekBar) {}
        override fun onStopTrackingTouch(s: SeekBar) = onChange(s.progress, true)
    })
    box.addView(sb, LinearLayout.LayoutParams(-1, ui.dp(40)))
    if (showScale) box.addView(LinearLayout(this).apply {
        setPadding(ui.dp(9), 0, ui.dp(9), 0)
        addView(ui.text("0", ui.cond, 12f, Ui.DIM), LinearLayout.LayoutParams(0, -2, 1f))
        addView(ui.text("$max", ui.cond, 12f, Ui.DIM))
    })
    return box
}

internal fun MainActivity.fader(maxValue: Int = 85): SeekBar {
    val track = ui.rect(Ui.WELL, 3f, Ui.veil(0x0F), 1f)
    val fill = ClipDrawable(ui.rect(ui.accent, 3f), Gravity.START, ClipDrawable.HORIZONTAL)
    val progress = LayerDrawable(arrayOf(track, fill)).apply {
        setId(0, android.R.id.background)
        setId(1, android.R.id.progress)
    }

    val w = ui.dp(22); val h = ui.dp(34)
    val shadow = GradientDrawable().apply {
        setColor(Color.parseColor("#CC000000")); cornerRadius = ui.dp(5).toFloat(); setSize(w, h)
    }
    val cap = GradientDrawable(
        GradientDrawable.Orientation.LEFT_RIGHT,
        intArrayOf(Color.parseColor("#3A352F"), Color.parseColor("#F3EFE7"), Color.parseColor("#3A352F"))
    ).apply {
        cornerRadius = ui.dp(5).toFloat(); setStroke(ui.dp(1), Color.parseColor("#80000000")); setSize(w, h)
    }
    val line = GradientDrawable().apply { setColor(ui.accent); setSize(ui.dp(2), ui.dp(14)) }
    val thumb = LayerDrawable(arrayOf(shadow, cap, line)).apply {
        setLayerInset(0, ui.dp(1), ui.dp(3), ui.dp(1), 0)
        setLayerInset(1, 0, 0, 0, ui.dp(3))
        setLayerInset(2, (w - ui.dp(2)) / 2, (h - ui.dp(14)) / 2, (w - ui.dp(2)) / 2, (h - ui.dp(14)) / 2 + ui.dp(3))
    }

    return SeekBar(this).apply {
        max = maxValue
        progressDrawable = progress
        this.thumb = thumb
        splitTrack = false
        thumbOffset = ui.dp(11)
        setPadding(ui.dp(11), 0, ui.dp(11), 0)
        if (Build.VERSION.SDK_INT >= 29) { minHeight = ui.dp(6); maxHeight = ui.dp(6) }
        background = null
    }
}

internal fun MainActivity.accentPlate(): View {
    val card = ui.plateCard()
    card.addView(ui.sectionTitle("Color de acento"))
    val row = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        setPadding(ui.dp(2), 0, ui.dp(2), 0)
    }
    val sel = Prefs.accentIndex(this).coerceIn(0, Prefs.ACCENTS.size - 1)
    for (i in Prefs.ACCENTS.indices) {
        row.addView(knob(i, i == sel), LinearLayout.LayoutParams(ui.dp(88), -2))
    }
    val scroller = android.widget.HorizontalScrollView(this).apply {
        isHorizontalScrollBarEnabled = false
        overScrollMode = View.OVER_SCROLL_NEVER
        addView(row)
    }
    card.addView(scroller, LinearLayout.LayoutParams(-1, -2))
    // El nombre del color ya se ve bajo el título de la sección: aquí solo la pista
    card.addView(ui.text("Desliza para ver más colores", ui.cond, 13f, Ui.DIM).apply {
        gravity = Gravity.CENTER; setPadding(0, ui.dp(8), 0, 0)
    }, LinearLayout.LayoutParams(-1, -2))
    // Llevar el color elegido a la vista
    scroller.post { scroller.scrollTo((ui.dp(88) * sel - scroller.width / 2 + ui.dp(44)).coerceAtLeast(0), 0) }
    return card
}

internal fun MainActivity.knob(i: Int, selected: Boolean): View {
    val color = Prefs.ACCENTS[i]
    val outer = FrameLayout(this)
    if (selected) {
        outer.addView(View(this).apply {
            background = ui.oval(Color.TRANSPARENT, (color and 0x00FFFFFF) or 0x1F000000, 6f)
        }, FrameLayout.LayoutParams(ui.dp(72), ui.dp(72), Gravity.CENTER))
        outer.addView(View(this).apply {
            background = ui.oval(Color.TRANSPARENT, color, 1.5f)
        }, FrameLayout.LayoutParams(ui.dp(64), ui.dp(64), Gravity.CENTER))
    }
    val cap = FrameLayout(this).apply {
        background = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            gradientType = GradientDrawable.RADIAL_GRADIENT
            gradientRadius = ui.dp(34).toFloat()
            setGradientCenter(0.5f, 0.35f)
            colors = intArrayOf(Color.parseColor("#3A342E"), Color.parseColor("#141110"))
            setStroke(ui.dp(1), Color.argb(0x1A, 255, 255, 255))
        }
        elevation = ui.dp(4).toFloat()
        outlineProvider = ViewOutlineProvider.BACKGROUND
        val gem = FrameLayout(this@knob).apply {
            background = ui.oval(color)
            addView(View(this@knob).apply {
                background = ui.oval(Color.argb(0x4D, 255, 255, 255)); alpha = 0.5f
            }, FrameLayout.LayoutParams(ui.dp(20), ui.dp(10), Gravity.TOP or Gravity.CENTER_HORIZONTAL).apply {
                topMargin = ui.dp(3)
            })
        }
        addView(gem, FrameLayout.LayoutParams(ui.dp(30), ui.dp(30), Gravity.CENTER))
    }
    outer.addView(cap, FrameLayout.LayoutParams(ui.dp(56), ui.dp(56), Gravity.CENTER))

    return LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        gravity = Gravity.CENTER_HORIZONTAL
        addView(outer, LinearLayout.LayoutParams(ui.dp(76), ui.dp(76)))
        addView(ui.text(Prefs.NAMES[i], if (selected) ui.condSemi else ui.cond, 15f, if (selected) Ui.HEAD else Ui.MUTED).apply {
            gravity = Gravity.CENTER
            ui.fitText(this, 15)
            setPadding(ui.dp(2), ui.dp(4), ui.dp(2), 0)
        }, LinearLayout.LayoutParams(-1, ui.dp(26)))
        isClickable = true
        contentDescription = "Color ${Prefs.NAMES[i]}" + if (selected) ", elegido" else ""
        setOnClickListener {
            animate().scaleX(0.96f).scaleY(0.96f).setDuration(80).withEndAction {
                scaleX = 1f; scaleY = 1f
                Prefs.setAccentIndex(this@knob, i)
                RockWidget.updateAll(this@knob)
                build()
            }.start()
        }
    }
}

internal fun MainActivity.themePlate(): View {
    val card = ui.plateCard()
    val cur = Themes.current(this)
    card.addView(ui.sectionTitle("Tema", cur.name))
    val strip = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
    Themes.ALL.forEach { t ->
        strip.addView(themeTile(t, t.id == cur.id) {
            Prefs.setTheme(this, t.id)
            RockWidget.updateAll(this)
            build()
        })
    }
    card.addView(HorizontalScrollView(this).apply {
        isHorizontalScrollBarEnabled = false
        addView(strip)
    })
    // Letras: primero el interruptor general y, justo debajo, la hora (que depende de él)
    val fonts = Prefs.themeFonts(this)
    val live = Prefs.liveUpdates(this)
    card.addView(switchRow("Letras del tema", fonts) { on ->
        Prefs.setThemeFonts(this, on)
        RockWidget.updateAll(this)
        build()
    }, LinearLayout.LayoutParams(-1, -2).apply { topMargin = ui.dp(10) })
    card.addView(ui.note("Clima, batería, música y apps con la letra de cada tema.",
        "La hora, el día y la fecha usan el reloj del sistema, que nunca se atrasa. " +
        "Si quieres que también lleven la letra del tema, activa «Hora con la letra del tema»."))
    if (fonts) {
        // Solo cuenta como encendida si la actualización en vivo también lo está (es quien la avanza)
        card.addView(switchRow("Hora con la letra del tema", Prefs.themeClock(this) && live) { on ->
            Prefs.setThemeClock(this, on)
            // Necesita la actualización en vivo: se enciende con ella en un solo toque
            if (on && !live) { Prefs.setLiveUpdates(this, true); askNotifications(); LiveService.start(this) }
            RockWidget.updateAll(this)
            build()
            if (on) showKeepAliveSteps()
        }, LinearLayout.LayoutParams(-1, -2).apply { topMargin = ui.dp(6) })
        card.addView(ui.note("Usa la actualización en vivo. Si la hora se queda quieta, apágalo.",
            "Con esto Prisma Widgets dibuja la hora, el día y la fecha cada minuto, y para eso enciende la " +
            "actualización en vivo. Si HyperOS cierra la app para ahorrar batería, la hora se congela. " +
            "Apagado, se usa el reloj del sistema, que nunca se atrasa."))
    }
    card.addView(View(this).apply { setBackgroundColor(Ui.veil(0x0F)) },
        LinearLayout.LayoutParams(-1, ui.dp(1)).apply { topMargin = ui.dp(10); bottomMargin = ui.dp(4) })
    card.addView(switchRow("Actualización en vivo", live) { on ->
        Prefs.setLiveUpdates(this, on)
        // Android exige una notificación fija para esto; el permiso se pide solo aquí, ya explicado
        if (on) { askNotifications(); LiveService.start(this) } else LiveService.stop(this)
        RockWidget.updateAll(this)
        build()
    })
    card.addView(ui.note("Batería al instante (con una notificación fija).",
        "Mantiene la batería al día y permite la hora con la letra del tema. Android exige mostrar una " +
        "notificación fija mientras está activa; al encenderla te pedirá el permiso de notificaciones."))
    return card
}

/**
 * HyperOS/MIUI cierra las apps en segundo plano. Para que la hora con la letra
 * del tema no se congele hay que darle Inicio automático y batería sin restricciones.
 */
internal fun MainActivity.showKeepAliveSteps() {
    val msg = "Para que HyperOS no cierre Prisma Widgets y la hora no se congele:\n\n" +
        "1. Activa Inicio automático.\n" +
        "2. En Batería (Ahorro de batería) elige Sin restricciones.\n" +
        "3. En Recientes, baja la tarjeta de Prisma Widgets para bloquearla (candado).\n\n" +
        "Si aun así se queda quieta, apaga «Hora con la letra del tema»."
    android.app.AlertDialog.Builder(this,
        if (Ui.light) android.R.style.Theme_DeviceDefault_Light_Dialog_Alert else android.R.style.Theme_DeviceDefault_Dialog_Alert)
        .setTitle("Mantener la hora al día")
        .setMessage(msg)
        .setPositiveButton("Inicio automático") { _, _ -> openAutostart() }
        .setNeutralButton("Batería") { _, _ -> openAppInfo() }
        .setNegativeButton("Luego", null)
        .show()
}

private fun MainActivity.openAppInfo() {
    try {
        startActivity(Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            android.net.Uri.fromParts("package", packageName, null)))
    } catch (e: Exception) { }
}

/** Pantalla de Inicio automático de Xiaomi; en otros teléfonos, la información de la app. */
private fun MainActivity.openAutostart() {
    try {
        startActivity(Intent().setComponent(android.content.ComponentName(
            "com.miui.securitycenter", "com.miui.permcenter.autostart.AutoStartManagementActivity")))
    } catch (e: Exception) { openAppInfo() }
}

/** Paleta de colores: oscuros, claros, personalizado y "del tema". */
internal fun MainActivity.colorSwatches(cur: Int, onPick: (Int) -> Unit): View {
    val th = Themes.current(this)
    val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
    fun line(title: String, colors: List<Int>) {
        box.addView(ui.label(title), LinearLayout.LayoutParams(-2, -2).apply { topMargin = ui.dp(12) })
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        // Cada color es un círculo de verdad (antes se estiraba en óvalo) dentro de una
        // celda alta de 44 dp, más fácil de tocar. En modo claro el aro va oscuro para que se vea.
        val ring = if (Ui.light) Ui.HEAD else Color.WHITE
        colors.forEachIndexed { i, c ->
            val on = c == cur
            row.addView(FrameLayout(this).apply {
                addView(View(this@colorSwatches).apply {
                    background = ui.oval(c, if (on) ring else Ui.veil(0x33), if (on) 3f else 1f)
                }, FrameLayout.LayoutParams(ui.dp(30), ui.dp(30), Gravity.CENTER))
                background = ui.rowBg()
                contentDescription = "$title, color ${i + 1}" + if (on) ", elegido" else ""
                setOnClickListener { onPick(c) }
            }, LinearLayout.LayoutParams(0, ui.dp(44), 1f))
        }
        box.addView(row, LinearLayout.LayoutParams(-1, ui.dp(44)).apply { topMargin = ui.dp(2) })
    }
    line("Oscuros", Prefs.DARK_TONES)
    line("Claros", Prefs.LIGHT_TONES)
    val custom = cur != 0 && cur !in Prefs.DARK_TONES && cur !in Prefs.LIGHT_TONES
    val bottom = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
    bottom.addView(ui.secondary(if (custom) "Personalizado ✓" else "Personalizado").apply {
        setOnClickListener { customColor(if (cur != 0) cur else Themes.defaultBg(th), onPick) }
    }, LinearLayout.LayoutParams(0, ui.dp(48), 1f))
    bottom.addView(ui.secondary(if (cur == 0) "Del tema ✓" else "Del tema").apply {
        setOnClickListener { onPick(0) }
    }, LinearLayout.LayoutParams(0, ui.dp(48), 1f).apply { marginStart = ui.dp(8) })
    box.addView(bottom, LinearLayout.LayoutParams(-1, -2).apply { topMargin = ui.dp(12) })
    return box
}

/** Fondo del widget: una capa detrás del reloj y el clima (normal, desvanecida o fija). */
internal fun MainActivity.widgetBgPlate(): View {
    val card = ui.plateCard()
    card.addView(ui.sectionTitle("Fondo del widget"))
    val gs = Prefs.widgetStyle(this)
    card.addView(ui.segmented(listOf("orig" to "Normal", "fade" to "Desvanecido", "solid" to "Fijo"), gs) {
        Prefs.setWidgetStyle(this, it); RockWidget.updateAll(this); build()
    })
    if (gs != "orig") card.addView(colorSwatches(Prefs.widgetColor(this)) {
        Prefs.setWidgetColor(this, it); RockWidget.updateAll(this); build()
    })
    card.addView(ui.note("Las letras se ajustan solas al color.", "Con colores claros, las letras del widget pasan a oscuras solas."),
        LinearLayout.LayoutParams(-1, -2).apply { topMargin = ui.dp(10) })
    return card
}

/** Selector de color libre: tono y brillo, con muestra. */
internal fun MainActivity.customColor(start: Int, onPick: (Int) -> Unit) {
    val hsv = FloatArray(3); Color.colorToHSV(start, hsv)
    val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(ui.dp(24), ui.dp(16), ui.dp(24), 0) }
    val preview = View(this)
    fun col() = Color.HSVToColor(hsv)
    fun refresh() { preview.background = ui.rect(col(), 14f) }
    box.addView(preview, LinearLayout.LayoutParams(-1, ui.dp(64)))
    fun bar(label: String, max: Int, value: Int, set: (Int) -> Unit) {
        box.addView(ui.text(label, ui.cond, 14f, Ui.DIM), LinearLayout.LayoutParams(-2, -2).apply { topMargin = ui.dp(14) })
        box.addView(android.widget.SeekBar(this).apply {
            this.max = max; progress = value
            setOnSeekBarChangeListener(object : android.widget.SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(s: android.widget.SeekBar, p: Int, u: Boolean) { set(p); refresh() }
                override fun onStartTrackingTouch(s: android.widget.SeekBar) {}
                override fun onStopTrackingTouch(s: android.widget.SeekBar) {}
            })
        })
    }
    // Brillo: de oscuro (0) a claro (100) jugando con valor y saturación
    val light0 = if (hsv[2] < 0.95f) (hsv[2] * 50).toInt() else (50 + (1 - hsv[1]) * 50).toInt()
    fun setLight(p: Int) {
        if (p <= 50) { hsv[2] = (p / 50f).coerceAtLeast(0.08f); hsv[1] = maxOf(hsv[1], 0.35f) }
        else { hsv[2] = 1f; hsv[1] = (1 - (p - 50) / 50f * 0.8f) }
    }
    if (hsv[1] < 0.15f) hsv[1] = 0.5f
    bar("Tono", 360, hsv[0].toInt()) { hsv[0] = it.toFloat() }
    bar("Brillo", 100, light0) { setLight(it) }
    refresh()
    // Mismo estilo de ventana que las demás: clara u oscura según la pantalla de ajustes
    android.app.AlertDialog.Builder(this,
        if (Ui.light) android.R.style.Theme_DeviceDefault_Light_Dialog_Alert else android.R.style.Theme_DeviceDefault_Dialog_Alert)
        .setTitle("Color personalizado")
        .setView(box)
        .setPositiveButton("Usar") { _, _ -> onPick(col() or 0xFF000000.toInt()) }
        .setNegativeButton("Cancelar", null)
        .show()
}

internal fun MainActivity.screenSize(): Pair<Int, Int> {
    return if (Build.VERSION.SDK_INT >= 30) {
        val b = getSystemService(WindowManager::class.java).maximumWindowMetrics.bounds
        Pair(b.width(), b.height())
    } else {
        val dm = android.util.DisplayMetrics()
        @Suppress("DEPRECATION")
        windowManager.defaultDisplay.getRealMetrics(dm)
        Pair(dm.widthPixels, dm.heightPixels)
    }
}

internal fun MainActivity.loadPhoto(uri: Uri) {
    val (w, h) = screenSize()
    io.execute {
        val bmp = try { decodeSampled(uri, w, h) } catch (e: Throwable) { null }  // incluye falta de memoria
        var name = "Tu foto"
        try {
            contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
                if (c.moveToFirst()) name = c.getString(0) ?: name
            }
        } catch (e: Exception) { }
        if (bmp != null) {
            try { photoFile.outputStream().use { bmp.compress(Bitmap.CompressFormat.JPEG, 92, it) } } catch (e: Exception) { }
            getSharedPreferences("redoble", android.content.Context.MODE_PRIVATE).edit().putString("photoName", name).apply()
        }
        onUi {
            if (bmp == null) toast("No pudimos abrir esa foto") else {
                photo = bmp; photoSmall = null
                Prefs.setWpPos(this, 50); Prefs.setWpZoom(this, 0)
                build()
            }
        }
    }
}

internal fun MainActivity.decodeSampled(uri: Uri, reqW: Int, reqH: Int): Bitmap? {
    val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) }
    // Se reduce hasta ~4 MP (sobra para la pantalla y evita quedarse sin memoria)
    var sample = 1
    while ((opts.outWidth.toLong() / sample) * (opts.outHeight.toLong() / sample) > 4_000_000L) sample *= 2
    val real = BitmapFactory.Options().apply { inSampleSize = sample }
    return contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, real) }
}
