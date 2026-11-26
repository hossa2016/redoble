package com.hossa.redoble

import android.graphics.Color
import android.graphics.Outline
import android.view.Gravity
import android.view.View
import android.view.ViewOutlineProvider
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout

// Vista previa en vivo: el mismo widget real (RemoteViews) dibujado dentro de la
// app, sobre el fondo elegido. Se arma una sola vez y en cada cambio solo se
// actualiza lo de adentro: así el video del fondo animado no se reinicia.

/** Piezas fijas de la vista previa (se reutilizan entre un toque y otro). */
internal class PreviewParts(
    val card: LinearLayout, val frame: FrameLayout, val bg: ImageView, val holder: FrameLayout,
    val wPx: Int, val hPx: Int, val scale: Float, val boxH: Int, val key: String
) {
    var video: VideoPreview? = null
}

internal fun MainActivity.previewPlate(): View {
    // Se rehace solo si cambia el modo claro/oscuro o el color de acento (cambian sus bordes)
    val key = "${Ui.light}-${ui.accent}"
    preview?.takeIf { it.key == key }?.let { updatePreview(); return it.card }
    preview?.let { (it.card.parent as? android.view.ViewGroup)?.removeView(it.card) }

    val card = ui.plateCard()
    val rule = Rules.act(this).takeIf { it.isNotBlank() && Rules.scene(this) != null }
    card.addView(ui.sectionTitle("Vista previa", if (rule != null) I18n.tr(this, "Ahora manda: {}").replace("{}", rule) else "Así se ve en tu pantalla"))

    val (sw, sh) = screenSize()
    val dens = resources.displayMetrics.density
    // Tamaño real aproximado del widget: ancho de pantalla y 4/5 del alto útil
    val wPx = sw - (16 * dens).toInt()
    val hPx = (sh * 0.78f).toInt()
    // Unos 35 % del alto: las pestañas quedan a la vista al abrir (en grande: tocándola)
    val boxW = (sw * 0.36f).toInt()
    val scale = boxW.toFloat() / sw
    val boxH = (sh * scale).toInt()

    val frame = FrameLayout(this).apply {
        clipToOutline = true
        outlineProvider = object : ViewOutlineProvider() {
            override fun getOutline(view: View, outline: Outline) {
                outline.setRoundRect(0, 0, view.width, view.height, ui.dp(16).toFloat())
            }
        }
        foreground = ui.rect(Color.TRANSPARENT, 16f, Ui.headA(0x33), 1f)
        contentDescription = "Vista previa del widget"
    }
    val bg = ImageView(this).apply { importantForAccessibility = android.view.View.IMPORTANT_FOR_ACCESSIBILITY_NO; scaleType = ImageView.ScaleType.CENTER_CROP; setImageBitmap(lastPreviewBg) }
    frame.addView(bg, FrameLayout.LayoutParams(-1, -1))
    // El widget real, a su tamaño, reducido con escala; siempre por encima del fondo
    val holder = FrameLayout(this).apply {
        pivotX = 0f; pivotY = 0f; scaleX = scale; scaleY = scale
        translationZ = 2f
    }
    frame.addView(holder, FrameLayout.LayoutParams(wPx, hPx).apply {
        leftMargin = ((sw - wPx) / 2f * scale).toInt()
        topMargin = (sh * 0.1f * scale).toInt()
    })
    // Capa que evita que los toques lleguen a los botones del widget de muestra
    frame.addView(View(this).apply {
        translationZ = 3f
        contentDescription = "Ver la vista previa a pantalla completa"
        setOnClickListener { openFullPreview() }
    }, FrameLayout.LayoutParams(-1, -1))
    card.addView(frame, LinearLayout.LayoutParams(boxW, boxH).apply { gravity = Gravity.CENTER_HORIZONTAL })
    // Editor visual: solo para el widget Completo (o si aún no hay ninguno)
    if (Widgets.has(this, Widgets.FULL) || !RockWidget.hasWidgets(this))
        card.addView(ui.actionRow("Editar el widget a mano", R.drawable.ic_edit, "Toca cada parte: orden, tamaño, letra y color").apply {
            setOnClickListener { openEditor() }
        }, LinearLayout.LayoutParams(-1, -2).apply { topMargin = ui.dp(14) })

    preview = PreviewParts(card, frame, bg, holder, wPx, hPx, scale, boxH, key)
    updatePreview()
    return card
}

/** Pone el fondo (imagen o video) y vuelve a dibujar el widget de muestra. */
internal fun MainActivity.updatePreview() {
    val p = preview ?: return
    val (sw, _) = screenSize()
    val ruleScene = Rules.scene(this)
    if (Prefs.wallStyle(this) == "anim" || ruleScene != null) {
        // Fondo animado: la escena viva detrás del widget (la de la regla activa, si hay); si es la misma, sigue corriendo
        val id = ruleScene ?: Prefs.liveScene(this)
        if (p.video?.scene?.id != id) {
            p.video?.let { p.frame.removeView(it) }
            val v = VideoPreview(this).apply { scene = VideoScenes.find(id) }
            p.frame.addView(v, 1, FrameLayout.LayoutParams(-1, -1))
            p.video = v
        }
        p.bg.visibility = View.GONE
    } else {
        p.video?.let { p.frame.removeView(it) }
        p.video = null
        p.bg.visibility = View.VISIBLE
        io.execute {
            val th = Themes.current(this)
            val small = try {
                val w = (sw * p.scale).toInt().coerceAtLeast(1); val h = p.boxH.coerceAtLeast(1)
                val style = Prefs.wallStyle(this)
                when {
                    style == "photo" && photo != null -> smallPhoto()?.let {
                        Graphics.makeWallpaper(it, w, h, Prefs.dim(this), Prefs.wpMode(this), Prefs.wpPos(this), Prefs.wpZoom(this))
                    }
                    th.family == "rock" && style != "fade" && style != "solid" -> thumb(R.drawable.fondo_bateria)
                    else -> Themes.styledWallpaper(this, th, w, h)
                }
            } catch (e: Throwable) { null }
            onUi { if (small != null) { lastPreviewBg = small; p.bg.setImageBitmap(small) } }
        }
    }
    // El widget se arma fuera del hilo principal; mientras tanto se ve el anterior
    val appCtx = applicationContext
    // Se muestra el widget que de verdad está puesto: el Completo si está (o si aún no hay
    // ninguno); si no, Hora y clima, Reproductor o Apps, en ese orden.
    val dens = resources.displayMetrics.density
    val kind = when {
        Widgets.has(this, Widgets.FULL) || !RockWidget.hasWidgets(this) -> Widgets.FULL
        Widgets.has(this, Widgets.CLOCK) -> Widgets.CLOCK
        Widgets.has(this, Widgets.PLAYER) -> Widgets.PLAYER
        else -> Widgets.APPS
    }
    // Widgets sueltos: al tamaño real del primero que esté puesto (como en la pantalla de inicio)
    val firstId = if (kind == Widgets.FULL) null else Widgets.ids(this, kind).firstOrNull()
    val real = firstId?.let { try { Widgets.sizeDp(this, android.appwidget.AppWidgetManager.getInstance(this), it) } catch (e: Exception) { null } }
    val fallbackH = when (kind) { Widgets.CLOCK -> 230; Widgets.PLAYER -> 200; Widgets.APPS -> 300; else -> 0 }
    val wDp = real?.first?.takeIf { it > 0 } ?: (p.wPx / dens).toInt()
    val hDp = real?.second?.takeIf { it > 0 } ?: fallbackH
    val wPx = if (kind == Widgets.FULL) p.wPx else (wDp * dens).toInt().coerceAtMost(p.wPx)
    val childH = if (kind == Widgets.FULL) p.hPx else (hDp * dens).toInt()
    val appsId = if (kind == Widgets.APPS) firstId else null
    io.execute {
        var err: Throwable? = null
        val rv = try {
            when {
                kind == Widgets.APPS && appsId != null -> Widgets.appsViews(appCtx, appsId, wDp, hDp)
                // El Reproductor suelto tiene sus propias plantillas (las mismas del widget real)
                kind == Widgets.PLAYER -> Widgets.playerViews(appCtx, wDp, hDp)
                kind == Widgets.CLOCK -> RockWidget.buildViews(appCtx, wPx, preview = true, kind = kind, hDp = hDp)
                else -> RockWidget.buildViews(appCtx, p.wPx, preview = true)
            }
        } catch (e: Throwable) { err = e; null }
        onUi {
            try {
                rv?.let {
                    // Con el contexto de la app (no el de la pantalla de ajustes): así se usan las
                    // vistas normales de Android, como en la pantalla de inicio. Las de AppCompat
                    // rechazan setImageBitmap desde RemoteViews y el widget no se dibujaba.
                    val fresh = it.apply(applicationContext, p.holder)
                    systemFonts(fresh)
                    p.holder.removeAllViews()
                    p.holder.addView(fresh, FrameLayout.LayoutParams(wPx, childH))
                    fresh.post {
                        snapThumb()
                        // Diagnóstico: si el widget quedó sin tamaño, se avisa en la vista previa
                        if (fresh.width == 0 || fresh.height == 0) showPreviewError(p, "Widget sin tamaño (${fresh.width}x${fresh.height})")
                        else showPreviewError(p, null)
                    }
                }
            } catch (e: Throwable) { err = e }
            err?.let { showPreviewError(p, it.javaClass.simpleName + ": " + (it.message ?: "")) }
        }
    }
}

/** Muestra (o quita) un aviso dentro de la vista previa si el widget no se pudo dibujar. */
internal fun MainActivity.showPreviewError(p: PreviewParts, msg: String?) {
    val old = p.frame.findViewWithTag<View>("pvErr")
    if (msg == null) { old?.let { p.frame.removeView(it) }; return }
    // Frase amable para todos; el detalle técnico solo en las versiones de prueba
    val text = "La vista previa no se pudo mostrar. Tu widget en la pantalla de inicio no se ve afectado." +
        (if (BuildConfig.DEBUG) "\n$msg" else "")
    if (old != null) return
    p.frame.addView(android.widget.LinearLayout(this).apply {
        tag = "pvErr"; translationZ = 4f; orientation = android.widget.LinearLayout.VERTICAL
        gravity = Gravity.CENTER_HORIZONTAL
        setBackgroundColor(Color.argb(0xCC, 0, 0, 0)); setPadding(ui.dp(12), ui.dp(10), ui.dp(12), ui.dp(6))
        addView(ui.text(text, ui.body, 13f, Color.WHITE).apply { gravity = Gravity.CENTER })
        addView(ui.textButton("Reintentar").apply {
            setOnClickListener { showPreviewError(p, null); updatePreview() }
        }, android.widget.LinearLayout.LayoutParams(-2, ui.dp(44)))
    }, FrameLayout.LayoutParams(-1, -2, Gravity.TOP))
}

/**
 * La pantalla de inicio (HyperOS/MIUI) dibuja la hora y la fecha del sistema con su propia
 * letra, no con la del diseño. La vista previa hace lo mismo para no prometer otra cosa;
 * lo dibujado con la letra del tema (imágenes) no cambia.
 */
internal fun systemFonts(v: android.view.View) {
    if (v is android.widget.TextView) {
        val bold = v.typeface?.isBold == true
        v.typeface = android.graphics.Typeface.create("sans-serif", if (bold) android.graphics.Typeface.BOLD else android.graphics.Typeface.NORMAL)
    }
    if (v is android.view.ViewGroup) for (i in 0 until v.childCount) systemFonts(v.getChildAt(i))
}
