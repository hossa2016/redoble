package com.hossa.redoble

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import androidx.core.graphics.ColorUtils

/** Un tema visual del widget. Los tres "Cristal" comparten layout. */
class Theme(
    val id: Int,
    val name: String,
    val family: String,          // rock | cristal | claro | neon | lujo
    val layout: Int,
    val head: Int,               // texto principal
    val dim: Int,                // texto secundario
    private val fixedAccent: Int?, // null = usa el color de acento elegido (rock)
    val accent2: Int,            // segundo color (degradados)
    val bg: Int,                 // color base del fondo de pantalla
    val orbs: IntArray = intArrayOf(),
    val light: Boolean = false,
) {
    fun accent(ctx: Context): Int = fixedAccent ?: Prefs.accent(ctx)
    val usesUserAccent get() = fixedAccent == null
    val hasOwnWallpaper get() = family != "rock"

    /** Mismo tema con otros colores de texto (para fondos claros u oscuros). */
    fun withText(head: Int, dim: Int, light: Boolean) =
        Theme(id, name, family, layout, head, dim, fixedAccent, accent2, bg, orbs, light)
}

object Themes {
    private fun c(s: String) = Color.parseColor(s)

    val ALL = listOf(
        Theme(0, "Rock oscuro", "rock", R.layout.widget_rock, c("#EDE6DC"), c("#B5AA9F"), null, c("#8E2A22"), c("#0A0807")),
        Theme(1, "Cristal glaciar", "cristal", R.layout.widget_cristal, c("#FFFFFF"), c("#B4C3CC"), c("#4FD8F0"), c("#2F8CFF"), c("#030305"),
            intArrayOf(c("#0E3B5C"), c("#0B5563"), c("#123A5A"))),
        Theme(2, "Cristal ámbar", "cristal", R.layout.widget_cristal, c("#FFFFFF"), c("#CDBFAE"), c("#F2B66B"), c("#D9783A"), c("#030305"),
            intArrayOf(c("#5A3510"), c("#6B2E12"), c("#3D2A14"))),
        Theme(3, "Cristal esmeralda", "cristal", R.layout.widget_cristal, c("#FFFFFF"), c("#B5CCC2"), c("#6FF0C0"), c("#2FBF8A"), c("#030305"),
            intArrayOf(c("#0E4A35"), c("#0D3D3F"), c("#1F4A22"))),
        Theme(4, "Claro", "claro", R.layout.widget_claro, c("#1C1A17"), c("#6B655C"), c("#1C1A17"), c("#3A362F"), c("#F3EFE8"), light = true),
        Theme(5, "Neón", "neon", R.layout.widget_neon, c("#EFFFFF"), c("#A3A5CC"), c("#3DF2FF"), c("#FF3DCB"), c("#07060D")),
        Theme(6, "Lujo dorado", "lujo", R.layout.widget_lujo, c("#EBD9AE"), c("#A8987A"), c("#D4B06A"), c("#A98545"), c("#0E0D0B")),
    )

    fun current(ctx: Context): Theme = ALL.getOrNull(Prefs.theme(ctx)) ?: ALL[0]

    fun isLight(c: Int) = ColorUtils.calculateLuminance(c) > 0.45

    /** Color por defecto de los fondos: uno oscuro sacado del tema. */
    fun defaultBg(t: Theme): Int = if (t.light) Color.parseColor("#E8E1D6")
        else t.orbs.firstOrNull() ?: ColorUtils.blendARGB(t.accent2, Color.BLACK, 0.55f)

    fun wallColor(ctx: Context, t: Theme) = Prefs.wallColor(ctx).takeIf { it != 0 } ?: defaultBg(t)
    fun widgetColor(ctx: Context, t: Theme) = Prefs.widgetColor(ctx).takeIf { it != 0 } ?: defaultBg(t)

    /**
     * Tema que usa el widget: si el fondo elegido es claro, letras oscuras;
     * si es oscuro, letras claras. Así cualquier color se lee bien.
     */
    fun widget(ctx: Context): Theme {
        val t = current(ctx)
        val bg = when {
            Prefs.widgetStyle(ctx) != "orig" -> widgetColor(ctx, t)
            Prefs.wallStyle(ctx) != "orig" -> wallColor(ctx, t)
            else -> return t
        }
        val light = isLight(bg)
        if (light == t.light) return t
        return if (light) t.withText(c("#1C1A17"), c("#5E584F"), true)
        else t.withText(c("#F5F2EC"), c("#B9B2A7"), false)
    }

    /** Fondo de pantalla según el estilo elegido (como está, desvanecido o fijo). */
    fun styledWallpaper(ctx: Context, t: Theme, w: Int, h: Int): Bitmap {
        val style = Prefs.wallStyle(ctx)
        if (style == "orig") return wallpaper(t, w, h)
        val col = wallColor(ctx, t)
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val cv = Canvas(bmp)
        if (style == "solid") { cv.drawColor(col); return bmp }
        val end = if (isLight(col)) Color.parseColor("#FBF8F3") else Color.parseColor("#050505")
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        p.shader = LinearGradient(0f, 0f, 0f, h.toFloat(),
            intArrayOf(col, ColorUtils.blendARGB(col, end, 0.45f), end), floatArrayOf(0f, 0.45f, 1f), Shader.TileMode.CLAMP)
        cv.drawRect(0f, 0f, w.toFloat(), h.toFloat(), p)
        return bmp
    }

    /** Fondo de pantalla propio del tema (para los que no usan foto). */
    fun wallpaper(t: Theme, w: Int, h: Int): Bitmap {
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val cv = Canvas(bmp)
        cv.drawColor(t.bg)
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        fun orb(cx: Float, cy: Float, r: Float, color: Int, alpha: Float) {
            p.shader = RadialGradient(cx, cy, r,
                intArrayOf(ColorUtils.setAlphaComponent(color, (255 * alpha).toInt()), ColorUtils.setAlphaComponent(color, 0)),
                floatArrayOf(0f, 1f), Shader.TileMode.CLAMP)
            cv.drawCircle(cx, cy, r, p)
        }
        when (t.family) {
            "cristal" -> {
                orb(w * 0.05f, h * 0.22f, w * 0.85f, t.orbs[0], 0.55f)
                orb(w * 0.95f, h * 0.50f, w * 0.80f, t.orbs[1], 0.45f)
                orb(w * 0.35f, h * 0.95f, w * 0.85f, t.orbs[2], 0.50f)
            }
            "claro" -> {
                orb(w * 0.85f, 0f, w * 1.1f, Color.WHITE, 0.9f)
                orb(w * 0.1f, h * 1.0f, w * 0.9f, Color.parseColor("#E6DED2"), 0.8f)
            }
            "neon" -> {
                val step = w / 15f
                p.shader = null
                p.strokeWidth = maxOf(1f, w / 1080f * 2f)
                p.color = ColorUtils.setAlphaComponent(t.accent2, 22)
                var y = 0f; while (y < h) { cv.drawLine(0f, y, w.toFloat(), y, p); y += step }
                p.color = ColorUtils.setAlphaComponent(Color.parseColor("#3DF2FF"), 18)
                var x = 0f; while (x < w) { cv.drawLine(x, 0f, x, h.toFloat(), p); x += step }
                orb(w * 0.1f, h * 1.02f, w * 0.9f, t.accent2, 0.35f)
                orb(w * 0.95f, h * 0.15f, w * 0.6f, Color.parseColor("#3DF2FF"), 0.15f)
            }
            "lujo" -> {
                orb(w * 0.5f, h * 0.22f, w * 1.0f, Color.parseColor("#2A241A"), 1f)
                p.shader = LinearGradient(0f, h * 0.7f, 0f, h.toFloat(),
                    intArrayOf(0, Color.parseColor("#66000000")), null, Shader.TileMode.CLAMP)
                cv.drawRect(0f, 0f, w.toFloat(), h.toFloat(), p)
                // Marco dorado fino con esquinas en diamante
                p.shader = null
                p.style = Paint.Style.STROKE
                p.strokeWidth = maxOf(1.5f, w / 720f)
                p.color = ColorUtils.setAlphaComponent(Color.parseColor("#D4B06A"), 120)
                val m = w * 0.04f; val top = h * 0.045f; val bot = h - h * 0.035f
                cv.drawRect(m, top, w - m, bot, p)
                p.color = ColorUtils.setAlphaComponent(Color.parseColor("#D4B06A"), 50)
                val m2 = m + w * 0.012f
                cv.drawRect(m2, top + w * 0.012f, w - m2, bot - w * 0.012f, p)
                p.style = Paint.Style.FILL
                p.color = Color.parseColor("#D4B06A")
                val dsz = w * 0.012f
                for ((cx, cy) in listOf(m to top, w - m to top, m to bot, w - m to bot)) {
                    val path = android.graphics.Path().apply {
                        moveTo(cx, cy - dsz); lineTo(cx + dsz, cy); lineTo(cx, cy + dsz); lineTo(cx - dsz, cy); close()
                    }
                    cv.drawPath(path, p)
                }
            }
        }
        return bmp
    }
}
