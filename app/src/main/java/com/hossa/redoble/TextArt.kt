package com.hossa.redoble

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Typeface
import android.text.TextPaint
import android.text.TextUtils
import androidx.core.content.res.ResourcesCompat
import java.util.Locale
import kotlin.math.ceil

/**
 * Dibuja textos como imagen con la letra de cada tema.
 * HyperOS ignora las fuentes propias dentro de los widgets, así que la única
 * forma de que se vean las letras del diseño es entregarlas ya dibujadas.
 */
object TextArt {

    /** Letras y medidas de cada familia de tema (las mismas del layout). */
    class Spec(
        val time: Int, val colon: Int, val body: Int, val med: Int, val semi: Int, val title: Int, val label: Int,
        val tsize: Float, val dsize: Float, val wsize: Float, val tomsize: Float, val lsize: Float,
        val tls: Float, val lls: Float, val tspace: Float,
        val caps: Boolean, val shadow: Boolean, val glow: Int = 0,
    )

    private val SPECS = mapOf(
        "rock" to Spec(R.font.barlow_condensed_extralight, R.font.barlow_condensed_light, R.font.barlow_condensed_regular,
            R.font.barlow_condensed_medium, R.font.barlow_condensed_semibold, R.font.anton_regular, R.font.barlow_condensed_regular,
            64f, 17f, 16f, 14f, 13f, 0.06f, 0f, 0f, caps = false, shadow = true),
        "cristal" to Spec(R.font.jakarta_light, R.font.jakarta_light, R.font.jakarta_regular, R.font.jakarta_semibold,
            R.font.jakarta_bold, R.font.jakarta_bold, R.font.jakarta_semibold,
            76f, 15f, 15f, 13f, 12f, 0.04f, 0f, -0.03f, caps = false, shadow = false),
        "claro" to Spec(R.font.intertight_extralight, R.font.intertight_extralight, R.font.inter_regular, R.font.inter_medium,
            R.font.inter_semibold, R.font.inter_semibold, R.font.inter_medium,
            76f, 14f, 15f, 13f, 12f, 0.2f, 0f, -0.04f, caps = false, shadow = false),
        "neon" to Spec(R.font.orbitron_medium, R.font.orbitron_medium, R.font.rajdhani_medium, R.font.rajdhani_semibold,
            R.font.rajdhani_semibold, R.font.orbitron_bold, R.font.rajdhani_semibold,
            54f, 15f, 16f, 14f, 12f, 0.25f, 0.1f, 0f, caps = true, shadow = false, glow = Color.parseColor("#3DF2FF")),
        "lujo" to Spec(R.font.cormorant_light, R.font.cormorant_light, R.font.cormorant_italic, R.font.montserrat_medium,
            R.font.cormorant_medium, R.font.montserrat_medium, R.font.montserrat_medium,
            80f, 12f, 18f, 11f, 10f, 0.22f, 0.12f, 0f, caps = true, shadow = false),
    )

    fun spec(family: String): Spec = SPECS[family] ?: SPECS.getValue("rock")

    private val faces = HashMap<Int, Typeface>()
    private fun face(ctx: Context, id: Int): Typeface = faces.getOrPut(id) {
        try { ResourcesCompat.getFont(ctx, id) } catch (e: Exception) { null } ?: Typeface.DEFAULT
    }

    private val es = Locale("es", "CO")

    /** Un texto en una sola línea, recortado con "…" si pasa de [maxWidthPx]. */
    fun render(
        ctx: Context, text: String, font: Int, sp: Float, color: Int,
        spacing: Float = 0f, caps: Boolean = false, shadow: Boolean = false, glow: Int = 0,
        maxWidthPx: Int = 0,
    ): Bitmap {
        var t: CharSequence = if (caps) text.uppercase(es) else text
        val pad = pad(ctx, shadow, glow)
        var p = paint(ctx, font, sp, color, spacing, shadow, glow)
        // Si no cabe, primero se achica (hasta 72 %); solo después se corta con "…"
        if (maxWidthPx > 0) {
            var size = sp
            while (p.measureText(t.toString()) + pad * 2 > maxWidthPx && size > sp * 0.72f) {
                size -= 0.5f
                p = paint(ctx, font, size, color, spacing, shadow, glow)
            }
        }
        if (maxWidthPx > 0 && p.measureText(t.toString()) + pad * 2 > maxWidthPx) {
            t = TextUtils.ellipsize(t, p, (maxWidthPx - pad * 2).toFloat().coerceAtLeast(1f), TextUtils.TruncateAt.END)
        }
        val fm = p.fontMetrics
        val w = ceil(p.measureText(t.toString())).toInt() + pad * 2
        val h = ceil(fm.descent - fm.ascent).toInt() + pad * 2
        val bmp = Bitmap.createBitmap(w.coerceAtLeast(1), h.coerceAtLeast(1), Bitmap.Config.ARGB_8888)
        Canvas(bmp).drawText(t.toString(), pad.toFloat(), pad - fm.ascent, p)
        return bmp
    }

    /** La hora "8:15" con los dos puntos en otro color (y otra letra si aplica). */
    fun clock(ctx: Context, hh: String, mm: String, s: Spec, sp: Float, color: Int, colonColor: Int): Bitmap {
        val main = paint(ctx, s.time, sp, color, s.tspace, s.shadow, s.glow)
        val col = paint(ctx, s.colon, sp, colonColor, 0f, s.shadow, s.glow)
        val pad = pad(ctx, s.shadow, s.glow)
        val wH = main.measureText(hh); val wC = col.measureText(":"); val wM = main.measureText(mm)
        val fm = main.fontMetrics
        val w = ceil(wH + wC + wM).toInt() + pad * 2
        val h = ceil(fm.descent - fm.ascent).toInt() + pad * 2
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val base = pad - fm.ascent
        c.drawText(hh, pad.toFloat(), base, main)
        c.drawText(":", pad + wH, base, col)
        c.drawText(mm, pad + wH + wC, base, main)
        return bmp
    }

    /**
     * Clima con ícono: Cristal = ícono + temperatura grande + descripción al lado;
     * Claro = ícono pequeño + texto en una línea (con "Mañana" incluido).
     */
    fun weather(ctx: Context, code: Int, temp: Int, desc: String, extra: String, s: Spec,
                big: Boolean, color: Int, dim: Int, accent: Int, maxWidthPx: Int): Bitmap {
        val d = ctx.resources.displayMetrics.density
        val iconPx = ((if (big) 30f else if (extra.isNotBlank()) 24f else 18f) * d).toInt()
        val icon = icon(code, iconPx, color, accent)
        val gap = (if (big) 10f else 7f) * d
        val parts = ArrayList<Bitmap>()
        if (big) {
            parts += render(ctx, "$temp°", s.time, 34f, color, -0.02f)
            val sub = if (extra.isBlank()) desc else "$desc\n$extra"
            parts += twoLines(ctx, sub, s.med, s.body, 13f, 11f, color, dim,
                (maxWidthPx - iconPx - gap * 2 - parts[0].width).toInt().coerceAtLeast(40))
        } else {
            val avail = (maxWidthPx - iconPx - gap).toInt().coerceAtLeast(40)
            val one = if (extra.isBlank()) "$desc, $temp°" else "$desc, $temp° · ${extra.replace("Mañana:", "Mañana")}"
            val fits = paint(ctx, s.med, s.wsize, color, 0f, false, 0).measureText(one) + pad(ctx, false, 0) * 2 <= avail
            parts += if (extra.isBlank() || fits) render(ctx, one, s.med, s.wsize, color, maxWidthPx = avail)
                else twoLines(ctx, "$desc, $temp°\n$extra", s.med, s.body, s.wsize, s.tomsize, color, dim, avail)
        }
        val w = (iconPx + gap * parts.size + parts.sumOf { it.width }).toInt()
        val h = maxOf(iconPx, parts.maxOf { it.height })
        val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(out)
        c.drawBitmap(icon, 0f, (h - iconPx) / 2f, null)
        var x = iconPx + gap
        for (b in parts) { c.drawBitmap(b, x, (h - b.height) / 2f, null); x += b.width + gap }
        return out
    }

    private fun twoLines(ctx: Context, text: String, f1: Int, f2: Int, s1: Float, s2: Float, c1: Int, c2: Int, maxW: Int): Bitmap {
        val lines = text.split("\n")
        val a = render(ctx, lines[0], f1, s1, c1, maxWidthPx = maxW)
        if (lines.size < 2) return a
        val b = render(ctx, lines[1], f2, s2, c2, maxWidthPx = maxW)
        val out = Bitmap.createBitmap(maxOf(a.width, b.width), a.height + b.height, Bitmap.Config.ARGB_8888)
        Canvas(out).apply { drawBitmap(a, 0f, 0f, null); drawBitmap(b, 0f, a.height.toFloat(), null) }
        return out
    }

    /** Íconos de clima dibujados (sol, nube, lluvia, tormenta, niebla). */
    fun icon(code: Int, px: Int, color: Int, accent: Int): Bitmap {
        val b = Bitmap.createBitmap(px, px, Bitmap.Config.ARGB_8888)
        val c = Canvas(b)
        val p = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            style = android.graphics.Paint.Style.STROKE; strokeWidth = px / 14f; strokeCap = android.graphics.Paint.Cap.ROUND
            this.color = color
        }
        val f = px.toFloat()
        fun sun(cx: Float, cy: Float, r: Float) {
            p.color = accent
            c.drawCircle(cx, cy, r, p)
            for (i in 0 until 8) {
                val a = Math.toRadians(i * 45.0)
                c.drawLine(cx + (r * 1.45f * Math.cos(a)).toFloat(), cy + (r * 1.45f * Math.sin(a)).toFloat(),
                    cx + (r * 1.85f * Math.cos(a)).toFloat(), cy + (r * 1.85f * Math.sin(a)).toFloat(), p)
            }
            p.color = color
        }
        fun cloud(y: Float) {
            val path = android.graphics.Path().apply {
                moveTo(f * 0.22f, y)
                lineTo(f * 0.78f, y)
                arcTo(android.graphics.RectF(f * 0.62f, y - f * 0.30f, f * 0.94f, y), 90f, -180f)
                arcTo(android.graphics.RectF(f * 0.32f, y - f * 0.46f, f * 0.74f, y - f * 0.06f), -10f, -150f)
                arcTo(android.graphics.RectF(f * 0.06f, y - f * 0.28f, f * 0.38f, y), -90f, -180f)
                close()
            }
            c.drawPath(path, p)
        }
        when (code) {
            0 -> sun(f / 2, f / 2, f * 0.2f)
            1, 2 -> { sun(f * 0.34f, f * 0.32f, f * 0.13f); cloud(f * 0.82f) }
            45, 48 -> for (i in 0..2) c.drawLine(f * 0.15f, f * (0.35f + i * 0.17f), f * 0.85f, f * (0.35f + i * 0.17f), p)
            in 51..67, in 80..82 -> {
                cloud(f * 0.62f)
                p.color = accent
                for (i in 0..2) c.drawLine(f * (0.32f + i * 0.18f), f * 0.74f, f * (0.27f + i * 0.18f), f * 0.92f, p)
            }
            in 95..99 -> {
                cloud(f * 0.62f)
                p.color = accent; p.style = android.graphics.Paint.Style.FILL
                c.drawPath(android.graphics.Path().apply {
                    moveTo(f * 0.54f, f * 0.64f); lineTo(f * 0.40f, f * 0.82f); lineTo(f * 0.50f, f * 0.82f)
                    lineTo(f * 0.44f, f * 0.98f); lineTo(f * 0.62f, f * 0.76f); lineTo(f * 0.52f, f * 0.76f); close()
                }, p)
            }
            else -> cloud(f * 0.75f)
        }
        return b
    }

    private fun paint(ctx: Context, font: Int, sp: Float, color: Int, spacing: Float, shadow: Boolean, glow: Int) =
        TextPaint(TextPaint.ANTI_ALIAS_FLAG or TextPaint.SUBPIXEL_TEXT_FLAG).apply {
            typeface = face(ctx, font)
            textSize = sp * ctx.resources.displayMetrics.scaledDensity
            this.color = color
            letterSpacing = spacing
            if (font == R.font.cormorant_light || font == R.font.cormorant_medium || font == R.font.cormorant_italic)
                fontFeatureSettings = "'lnum'"
            val d = ctx.resources.displayMetrics.density
            when {
                glow != 0 -> setShadowLayer(14f * d, 0f, 0f, glow)
                shadow -> setShadowLayer(6f * d, 0f, 2f * d, Color.parseColor("#CC000000"))
            }
        }

    private fun pad(ctx: Context, shadow: Boolean, glow: Int): Int {
        val d = ctx.resources.displayMetrics.density
        return ((if (glow != 0) 16f else if (shadow) 7f else 2f) * d).toInt()
    }
}
