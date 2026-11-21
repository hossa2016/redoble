package com.hossa.redoble

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.PorterDuffXfermode
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.Drawable
import android.os.Build
import androidx.core.content.res.ResourcesCompat
import androidx.core.graphics.ColorUtils
import java.util.Locale

object Graphics {

    val HEAD = Color.parseColor("#EDE6DC")
    val MUTED = Color.parseColor("#9C9187")
    val DIM = Color.parseColor("#645A52")
    val INK = Color.parseColor("#0E0B09")
    val ALERT = Color.parseColor("#E24B4A")

    /** Gradiente de metal pulido (con reflejo de madera). */
    val METAL_COLORS = intArrayOf(
        Color.parseColor("#5E5A54"), Color.parseColor("#CFCAC0"), Color.parseColor("#FBFAF6"),
        Color.parseColor("#8F8A81"), Color.parseColor("#34302C"), Color.parseColor("#A47C55"),
        Color.parseColor("#D9D3C8"), Color.parseColor("#FFFFFF"), Color.parseColor("#6F6961")
    )
    val METAL_STOPS = floatArrayOf(0f, .14f, .24f, .40f, .52f, .60f, .72f, .80f, 1f)

    private var anton: Typeface? = null
    fun anton(ctx: Context): Typeface {
        anton?.let { return it }
        val t = ResourcesCompat.getFont(ctx, R.font.anton_regular) ?: Typeface.DEFAULT_BOLD
        anton = t
        return t
    }

    private fun dp(ctx: Context, v: Float) = v * ctx.resources.displayMetrics.density

    // ------------------------------------------------------------------
    // Día vertical con metal pulido + varilla de tensión
    // ------------------------------------------------------------------
    fun dayBitmap(ctx: Context, dayName: String, accent: Int, heightDp: Int): Bitmap {
        var scale = 1f
        val dens = ctx.resources.displayMetrics.density
        // Mantener el bitmap en un tamaño razonable para RemoteViews.
        if (106 * dens * heightDp * dens * 4 > 2_400_000) scale = 0.75f
        val d = { v: Float -> v * dens * scale }

        val w = d(106f).toInt()
        val h = d(heightDp.toFloat()).toInt()
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)

        val text = dayName.uppercase(Locale("es", "CO"))
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = anton(ctx)
            letterSpacing = 0.03f
        }
        // Auto-ajuste: de 104dp hacia abajo hasta que quepa a lo alto (mínimo 64dp).
        var size = d(104f)
        paint.textSize = size
        val maxLen = h - d(16f)
        while (paint.measureText(text) > maxLen && size > d(64f)) {
            size -= d(2f)
            paint.textSize = size
        }
        // Si aún no cabe (widget muy bajo), forzar.
        while (paint.measureText(text) > maxLen && size > d(20f)) {
            size -= d(2f)
            paint.textSize = size
        }
        val textLen = paint.measureText(text)
        val baselineX = d(94f)          // la línea base queda a la derecha; las letras crecen a la izquierda
        val startY = d(6f) + textLen    // el final de la palabra queda arriba con 6dp de margen

        fun drawWord(p: Paint, dxScreen: Float = 0f, dyScreen: Float = 0f) {
            c.save()
            c.translate(baselineX + dxScreen, startY + dyScreen)
            c.rotate(-90f)
            c.drawText(text, 0f, 0f, p)
            c.restore()
        }

        // Pasada 1: sombra
        val shadow = Paint(paint).apply {
            color = Color.parseColor("#D9000000")
            setShadowLayer(d(5f), d(3f), 0f, Color.parseColor("#D9000000"))
        }
        // En el lienzo rotado, "abajo" en pantalla es +x antes de rotar; usamos desplazamiento en pantalla.
        shadow.clearShadowLayer()
        shadow.maskFilter = BlurMaskFilter(d(5f), BlurMaskFilter.Blur.NORMAL)
        drawWord(shadow, 0f, d(3f))

        // Pasada 2: relleno metálico (gradiente a lo ancho de las letras, en X de pantalla)
        val layer = c.saveLayer(0f, 0f, w.toFloat(), h.toFloat(), null)
        drawWord(Paint(paint).apply { color = Color.WHITE })
        val metal = Paint().apply {
            shader = LinearGradient(0f, 0f, w.toFloat(), 0f, METAL_COLORS, METAL_STOPS, Shader.TileMode.CLAMP)
            xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_IN)
        }
        c.drawRect(0f, 0f, w.toFloat(), h.toFloat(), metal)
        c.restoreToCount(layer)

        // Pasada 3: bisel
        drawWord(Paint(paint).apply {
            style = Paint.Style.STROKE; strokeWidth = d(0.75f); color = Color.parseColor("#66000000")
        })
        // Pasada 4: brillo
        drawWord(Paint(paint).apply {
            style = Paint.Style.STROKE; strokeWidth = d(0.5f); color = Color.parseColor("#33FFFFFF")
        }, 0f, -d(0.5f))

        // Varilla de tensión en x = 104dp
        val rodX = d(103f)
        val top = d(6f)
        val bottom = h - d(6f)
        val glow = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = ColorUtils.setAlphaComponent(accent, 0x8C)
            strokeWidth = d(2f)
            maskFilter = BlurMaskFilter(d(6f), BlurMaskFilter.Blur.NORMAL)
        }
        c.drawLine(rodX, top + (bottom - top) * 0.12f, rodX, top + (bottom - top) * 0.88f, glow)
        val rod = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            strokeWidth = d(2f)
            shader = LinearGradient(
                0f, top, 0f, bottom,
                intArrayOf(Color.TRANSPARENT, accent, accent, Color.TRANSPARENT),
                floatArrayOf(0f, .12f, .88f, 1f), Shader.TileMode.CLAMP
            )
        }
        c.drawLine(rodX, top, rodX, bottom, rod)
        for (f in floatArrayOf(0.10f, 0.90f)) {
            val cy = top + (bottom - top) * f
            val r = d(4f)
            c.drawCircle(rodX, cy + d(1f), r, Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.BLACK; maskFilter = BlurMaskFilter(d(1f), BlurMaskFilter.Blur.NORMAL)
            })
            c.drawCircle(rodX, cy, r, Paint(Paint.ANTI_ALIAS_FLAG).apply {
                shader = RadialGradient(
                    rodX - r * 0.3f, cy - r * 0.4f, r * 1.4f,
                    intArrayOf(Color.WHITE, Color.parseColor("#8A857D"), Color.parseColor("#2C2925")),
                    floatArrayOf(0f, .55f, 1f), Shader.TileMode.CLAMP
                )
            })
        }
        return bmp
    }

    // ------------------------------------------------------------------
    // Vúmetro LED de 12 segmentos
    // ------------------------------------------------------------------
    fun ledBitmap(ctx: Context, level: Int, accent: Int): Bitmap {
        val w = dp(ctx, 113f).toInt()
        val h = dp(ctx, 19f).toInt()
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val on = Math.round(level.coerceIn(0, 100) / 100f * 12)
        val color = if (level <= 15) ALERT else accent
        val segW = dp(ctx, 6f)
        val segH = dp(ctx, 11f)
        val gap = dp(ctx, 3f)
        val r = dp(ctx, 1.5f)
        val x0 = dp(ctx, 4f)
        val y0 = dp(ctx, 4f)
        val glow = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = ColorUtils.setAlphaComponent(color, 0x8C)
            maskFilter = BlurMaskFilter(dp(ctx, 3f), BlurMaskFilter.Blur.NORMAL)
        }
        val fillOn = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color }
        val fillOff = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = Color.parseColor("#2A231E") }
        val topOn = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = Color.parseColor("#59FFFFFF") }
        val topOff = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = Color.parseColor("#0AFFFFFF") }
        for (i in 0 until 12) {
            val x = x0 + i * (segW + gap)
            val rect = RectF(x, y0, x + segW, y0 + segH)
            if (i < on) {
                c.drawRoundRect(rect, r, r, glow)
                c.drawRoundRect(rect, r, r, fillOn)
                c.drawRect(x + r, y0, x + segW - r, y0 + dp(ctx, 1f), topOn)
            } else {
                c.drawRoundRect(rect, r, r, fillOff)
                c.drawRect(x + r, y0, x + segW - r, y0 + dp(ctx, 1f), topOff)
            }
        }
        return bmp
    }

    // ------------------------------------------------------------------
    // Ícono monocromo
    // ------------------------------------------------------------------
    private val iconCache = HashMap<String, Bitmap>()

    fun monoIcon(ctx: Context, pkg: String, color: Int = HEAD, sizePx: Int = 48): Bitmap? {
        val key = "$pkg@$color@$sizePx"
        iconCache[key]?.let { return it }
        val pm = ctx.packageManager
        val icon: Drawable = try { pm.getApplicationIcon(pkg) } catch (e: Exception) { return null }

        val out = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        val tint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
            colorFilter = PorterDuffColorFilter(color, PorterDuff.Mode.SRC_IN)
        }

        val mono = if (Build.VERSION.SDK_INT >= 33 && icon is AdaptiveIconDrawable) icon.monochrome else null
        val raw = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        val rc = Canvas(raw)
        if (mono != null) {
            val extra = (sizePx * 0.25f).toInt()
            mono.setBounds(-extra, -extra, sizePx + extra, sizePx + extra)
            mono.draw(rc)
            Canvas(out).drawBitmap(raw, 0f, 0f, tint)
        } else {
            icon.setBounds(0, 0, sizePx, sizePx)
            icon.draw(rc)
            // Luminancia -> alfa: lo claro queda visible, lo oscuro y el fondo desaparecen.
            val px = IntArray(sizePx * sizePx)
            raw.getPixels(px, 0, sizePx, 0, 0, sizePx, sizePx)
            // Color del fondo de la placa (muestra cerca del borde, dentro de la forma)
            val edge = px[(sizePx / 2) * sizePx + (sizePx * 0.12f).toInt()]
            val edgeL = lum(edge)
            val invert = edgeL > 0.6f   // placa clara: lo oscuro es el dibujo
            var visible = 0
            for (i in px.indices) {
                val a = Color.alpha(px[i]) / 255f
                if (a < 0.05f) { px[i] = 0; continue }
                val l = lum(px[i])
                val diff = kotlin.math.abs(l - edgeL) + colorDist(px[i], edge)
                var alpha = ((diff - 0.12f) * 2.6f).coerceIn(0f, 1f)
                if (!invert && l < 0.25f) alpha *= 0.4f
                alpha *= a
                if (alpha > 0.3f) visible++
                px[i] = Color.argb((alpha * 255).toInt(), 255, 255, 255)
            }
            if (visible < px.size * 0.08f) {
                // Muy poco visible: usar la inicial
                val label = try { pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString() } catch (e: Exception) { "?" }
                val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    typeface = anton(ctx); textSize = sizePx * 0.62f; this.color = color
                    textAlign = Paint.Align.CENTER
                }
                val fm = p.fontMetrics
                Canvas(out).drawText(label.take(1).uppercase(), sizePx / 2f, sizePx / 2f - (fm.ascent + fm.descent) / 2, p)
            } else {
                raw.setPixels(px, 0, sizePx, 0, 0, sizePx, sizePx)
                Canvas(out).drawBitmap(raw, 0f, 0f, tint)
            }
        }
        raw.recycle()
        iconCache[key] = out
        return out
    }

    private fun lum(c: Int) =
        (0.2126f * Color.red(c) + 0.7152f * Color.green(c) + 0.0722f * Color.blue(c)) / 255f

    private fun colorDist(a: Int, b: Int): Float {
        val dr = (Color.red(a) - Color.red(b)) / 255f
        val dg = (Color.green(a) - Color.green(b)) / 255f
        val db = (Color.blue(a) - Color.blue(b)) / 255f
        return (kotlin.math.sqrt(dr * dr + dg * dg + db * db) * 0.5f)
    }

    // ------------------------------------------------------------------
    // Fondo de pantalla
    // ------------------------------------------------------------------
    fun makeWallpaper(photo: Bitmap, width: Int, height: Int, dimPercent: Int): Bitmap {
        val out = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val c = Canvas(out)
        val scale = maxOf(width / photo.width.toFloat(), height / photo.height.toFloat())
        val w = photo.width * scale
        val h = photo.height * scale
        val left = (width - w) / 2f
        val top = (height - h) / 2f
        c.drawBitmap(photo, null, RectF(left, top, left + w, top + h), Paint(Paint.FILTER_BITMAP_FLAG))
        c.drawColor(Color.argb(dimPercent.coerceIn(0, 100) * 255 / 100, 0, 0, 0))
        return out
    }

    @Suppress("unused")
    private val gray = ColorMatrixColorFilter(ColorMatrix().apply { setSaturation(0f) })
}
