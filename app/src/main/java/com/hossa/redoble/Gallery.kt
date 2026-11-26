package com.hossa.redoble

import android.graphics.Bitmap
import android.graphics.Color
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout

/**
 * Galería de escenarios: diseños completos (tema, fondo animado, plantilla, color y
 * reproductor) que se aplican con un toque. Se puede deshacer.
 */
object Gallery {
    class Look(val id: String, val name: String, val desc: String, val theme: Int, val scene: String,
               val preset: String, val accent: Int, val skin: String)

    val ALL = listOf(
        Look("garaje", "Garaje", "Rock crudo: batería entre humo, letra marcada y vinilo.", 0, "bateria", "completo", 0, "vinilo"),
        Look("lluvia", "Noche de lluvia", "Neón y lluvia en la ventana, reproductor grande.", 5, "neon", "musica", 7, "normal"),
        Look("fogata", "Fogata", "Tonos ámbar y fuego para la noche.", 2, "fogata", "completo", 3, "casete"),
        Look("estadio", "Estadio", "Reflectores sobre el público y carátula en todo.", 0, "escenario", "musica", 1, "normal"),
        Look("glaciar", "Glaciar", "Frío y limpio: aurora y cristal azul.", 1, "aurora", "minimal", 8, "normal"),
        Look("bosque", "Bosque", "Esmeralda con hojas y gotas.", 3, "hojas", "completo", 5, "normal"),
        Look("gala", "Gala", "Lujo dorado con polvo de oro.", 6, "oro", "minimal", 3, "vinilo"),
        Look("cafe", "Café y lluvia", "Ventana con café humeante, apps a la mano.", 1, "cafe", "completo", 8, "casete"),
    )

    /** Aplica un escenario y devuelve el código para deshacerlo. */
    fun apply(ctx: android.content.Context, l: Look): String {
        val undo = Backup.shareCode(ctx)
        Prefs.applyPreset(ctx, l.preset)
        Prefs.setTheme(ctx, l.theme.coerceIn(0, Themes.ALL.size - 1))
        Prefs.setWallStyle(ctx, "anim"); Prefs.setLiveScene(ctx, l.scene)
        Prefs.setAccentIndex(ctx, l.accent.coerceIn(0, Prefs.ACCENTS.size - 1)); Prefs.setBgAccent(ctx, 0)
        Prefs.setPlayerSkin(ctx, l.skin)
        return undo
    }

    /** Código QR (blanco y negro) de un texto, para compartir el diseño como imagen. */
    fun qr(text: String, size: Int): Bitmap? = try {
        val m = com.google.zxing.qrcode.QRCodeWriter().encode(text, com.google.zxing.BarcodeFormat.QR_CODE, size, size,
            mapOf(com.google.zxing.EncodeHintType.MARGIN to 1, com.google.zxing.EncodeHintType.ERROR_CORRECTION to
                com.google.zxing.qrcode.decoder.ErrorCorrectionLevel.L))
        val px = IntArray(m.width * m.height) { i -> if (m.get(i % m.width, i / m.width)) Color.BLACK else Color.WHITE }
        Bitmap.createBitmap(px, m.width, m.height, Bitmap.Config.ARGB_8888)
    } catch (e: Throwable) { null }
}

/** Escenarios listos: tarjetas con la escena; al tocar, se ven los detalles y se aplica. */
internal fun MainActivity.galleryPlate(): View {
    val card = ui.plateCard()
    card.addView(ui.sectionTitle("Escenarios listos", "Un toque cambia tema, fondo y diseño"))
    val strip = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
    Gallery.ALL.forEach { l ->
        strip.addView(LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            isClickable = true; isFocusable = true
            contentDescription = I18n.tr(this@galleryPlate, l.name) + ". " + I18n.tr(this@galleryPlate, l.desc)
            addView(FrameLayout(this@galleryPlate).apply {
                clipToOutline = true
                background = ui.rect(Ui.WELL, 14f, Ui.veil(0x22), 1f)
                outlineProvider = object : android.view.ViewOutlineProvider() {
                    override fun getOutline(v: View, o: android.graphics.Outline) { o.setRoundRect(0, 0, v.width, v.height, ui.dp(14).toFloat()) }
                }
                VideoScenes.thumb(l.scene)?.let { addView(ImageView(this@galleryPlate).apply {
                    setImageResource(it); scaleType = ImageView.ScaleType.CENTER_CROP }, FrameLayout.LayoutParams(-1, -1)) }
                addView(View(this@galleryPlate).apply { background = ui.oval(Prefs.ACCENTS[l.accent.coerceIn(0, Prefs.ACCENTS.size - 1)]) },
                    FrameLayout.LayoutParams(ui.dp(14), ui.dp(14), Gravity.BOTTOM or Gravity.END).apply { setMargins(0, 0, ui.dp(8), ui.dp(8)) })
            }, LinearLayout.LayoutParams(ui.dp(96), ui.dp(150)))
            addView(ui.text(l.name, ui.cond, 15f, Ui.HEAD).apply { gravity = Gravity.CENTER; ui.fitText(this, 15, 12) },
                LinearLayout.LayoutParams(ui.dp(96), -2).apply { topMargin = ui.dp(6) })
            setOnClickListener {
                ui.sheet(l.name, l.desc, actions = listOf("Aplicar" to {
                    val undo = Gallery.apply(this@galleryPlate, l)
                    RockWidget.updateAll(this@galleryPlate); Player.updateAll(this@galleryPlate)
                    build()
                    ui.sheet("Escenario aplicado", "Si no te convence, vuelve a como estaba.",
                        actions = listOf("Deshacer" to {
                            try { Backup.applyCode(this@galleryPlate, undo) } catch (e: Exception) { }
                            RockWidget.updateAll(this@galleryPlate); build()
                        }), dismissLabel = "Me gusta")
                }))
            }
        }, LinearLayout.LayoutParams(-2, -2).apply { marginEnd = ui.dp(10) })
    }
    card.addView(HorizontalScrollView(this).apply { isHorizontalScrollBarEnabled = false; addView(strip) },
        LinearLayout.LayoutParams(-1, -2).apply { topMargin = ui.dp(8) })
    return card
}
