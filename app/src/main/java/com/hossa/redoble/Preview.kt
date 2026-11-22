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
// app, sobre el fondo elegido. Cambia al instante al tocar cada opción.

internal fun MainActivity.previewPlate(): View {
    val card = ui.plateCard()
    card.addView(ui.sectionTitle("Vista previa", "Así se ve"))

    val (sw, sh) = screenSize()
    val dens = resources.displayMetrics.density
    // Tamaño real aproximado del widget: ancho de pantalla y 4/5 del alto útil
    val wPx = sw - (16 * dens).toInt()
    val hPx = (sh * 0.78f).toInt()
    val boxW = ui.dp(180)
    val scale = boxW.toFloat() / sw
    val boxH = (sh * scale).toInt()

    val frame = FrameLayout(this).apply {
        clipToOutline = true
        outlineProvider = object : ViewOutlineProvider() {
            override fun getOutline(view: View, outline: Outline) {
                outline.setRoundRect(0, 0, view.width, view.height, ui.dp(18).toFloat())
            }
        }
        foreground = ui.rect(Color.TRANSPARENT, 18f, Color.parseColor("#33EDE6DC"), 1f)
        contentDescription = "Vista previa del widget"
    }
    // Fondo: el que tendrá la pantalla con lo elegido
    val bg = ImageView(this).apply { scaleType = ImageView.ScaleType.CENTER_CROP }
    frame.addView(bg, FrameLayout.LayoutParams(-1, -1))
    io.execute {
        val th = Themes.current(this)
        val small = try {
            val w = (sw * scale).toInt().coerceAtLeast(1); val h = boxH.coerceAtLeast(1)
            val style = Prefs.wallStyle(this)
            when {
                style == "photo" && photo != null -> smallPhoto()?.let {
                    Graphics.makeWallpaper(it, w, h, Prefs.dim(this), Prefs.wpMode(this), Prefs.wpPos(this), Prefs.wpZoom(this))
                }
                th.family == "rock" && style != "fade" && style != "solid" -> thumb(R.drawable.fondo_bateria)
                else -> Themes.styledWallpaper(this, th, w, h)
            }
        } catch (e: Throwable) { null }
        onUi { bg.setImageBitmap(small) }
    }

    // El widget real, a su tamaño, reducido con escala
    try {
        val rv = RockWidget.buildViews(this, wPx, preview = true)
        val holder = FrameLayout(this)
        val real = rv.apply(this, holder)
        holder.addView(real, FrameLayout.LayoutParams(wPx, hPx))
        holder.pivotX = 0f; holder.pivotY = 0f
        holder.scaleX = scale; holder.scaleY = scale
        frame.addView(holder, FrameLayout.LayoutParams(wPx, hPx).apply {
            leftMargin = ((sw - wPx) / 2f * scale).toInt()
            topMargin = (sh * 0.1f * scale).toInt()
        })
    } catch (e: Exception) { }
    // Capa que evita que los toques lleguen a los botones del widget de muestra
    frame.addView(View(this).apply { isClickable = true }, FrameLayout.LayoutParams(-1, -1))

    card.addView(frame, LinearLayout.LayoutParams(boxW, boxH).apply { gravity = Gravity.CENTER_HORIZONTAL })
    card.addView(ui.bodyText("Se actualiza al cambiar el tema, los fondos o lo que muestra.", Ui.MUTED, 13f).apply {
        gravity = Gravity.CENTER
    }, LinearLayout.LayoutParams(-1, -2).apply { topMargin = ui.dp(10) })
    return card
}
