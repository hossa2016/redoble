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
    card.addView(ui.sectionTitle("Vista previa", "Así se ve"))

    val (sw, sh) = screenSize()
    val dens = resources.displayMetrics.density
    // Tamaño real aproximado del widget: ancho de pantalla y 4/5 del alto útil
    val wPx = sw - (16 * dens).toInt()
    val hPx = (sh * 0.78f).toInt()
    val boxW = ui.dp(140)
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
    val bg = ImageView(this).apply { scaleType = ImageView.ScaleType.CENTER_CROP; setImageBitmap(lastPreviewBg) }
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
    frame.addView(View(this).apply { isClickable = true; translationZ = 3f }, FrameLayout.LayoutParams(-1, -1))
    card.addView(frame, LinearLayout.LayoutParams(boxW, boxH).apply { gravity = Gravity.CENTER_HORIZONTAL })

    preview = PreviewParts(card, frame, bg, holder, wPx, hPx, scale, boxH, key)
    updatePreview()
    return card
}

/** Pone el fondo (imagen o video) y vuelve a dibujar el widget de muestra. */
internal fun MainActivity.updatePreview() {
    val p = preview ?: return
    val (sw, _) = screenSize()
    if (Prefs.wallStyle(this) == "anim") {
        // Fondo animado: la escena viva detrás del widget; si es la misma, sigue corriendo
        val id = Prefs.liveScene(this)
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
    io.execute {
        var err: Throwable? = null
        val rv = try { RockWidget.buildViews(appCtx, p.wPx, preview = true) } catch (e: Throwable) { err = e; null }
        onUi {
            try {
                rv?.let {
                    // Con el contexto de la app (no el de la pantalla de ajustes): así se usan las
                    // vistas normales de Android, como en la pantalla de inicio. Las de AppCompat
                    // rechazan setImageBitmap desde RemoteViews y el widget no se dibujaba.
                    val fresh = it.apply(applicationContext, p.holder)
                    p.holder.removeAllViews()
                    p.holder.addView(fresh, FrameLayout.LayoutParams(p.wPx, p.hPx))
                    fresh.post {
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
    if (old != null) { (old as android.widget.TextView).text = "No se pudo dibujar el widget:\n$msg"; return }
    p.frame.addView(ui.text("No se pudo dibujar el widget:\n$msg", ui.body, 10f, Color.WHITE).apply {
        tag = "pvErr"; translationZ = 4f
        setBackgroundColor(Color.argb(0xCC, 0, 0, 0)); setPadding(ui.dp(6), ui.dp(6), ui.dp(6), ui.dp(6))
    }, FrameLayout.LayoutParams(-1, -2, Gravity.TOP))
}

/**
 * Al elegir un fondo: se aplica y la pantalla sube con suavidad hasta la vista
 * previa, para ver enseguida cómo queda.
 */
internal fun MainActivity.buildAndShowPreview() {
    build()
    val sv = scroll ?: return
    val card = preview?.card ?: return
    sv.postDelayed({
        val top = (card.top - ui.dp(8)).coerceAtLeast(0)
        if (sv.scrollY > top) sv.smoothScrollTo(0, top)
    }, 120)
}
