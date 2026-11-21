package com.hossa.redoble

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.Rect
import android.graphics.Typeface
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.Drawable
import android.os.Build

object Graphics {

    /** Dibuja el nombre del día en vertical (de abajo hacia arriba). */
    fun verticalDay(ctx: Context, text: String, color: Int): Bitmap {
        val d = ctx.resources.displayMetrics.density
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = color
            textSize = 58f * d
            typeface = Typeface.create("sans-serif-condensed", Typeface.BOLD)
            letterSpacing = 0.04f
        }
        val bounds = Rect()
        paint.getTextBounds(text, 0, text.length, bounds)
        val textW = paint.measureText(text).toInt() + (4 * d).toInt()
        val fm = paint.fontMetricsInt
        val textH = fm.descent - fm.ascent
        val bmp = Bitmap.createBitmap(textH, textW, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        c.rotate(-90f)
        c.translate(-textW.toFloat(), 0f)
        c.drawText(text, 0f, -fm.ascent.toFloat(), paint)
        return bmp
    }

    /** Ícono de la app convertido en silueta de un solo color. */
    fun tintedIcon(ctx: Context, pkg: String, color: Int): Bitmap? {
        val size = (28 * ctx.resources.displayMetrics.density).toInt().coerceAtLeast(24) * 2
        val icon: Drawable = try {
            ctx.packageManager.getApplicationIcon(pkg)
        } catch (e: Exception) {
            return null
        }

        var src: Drawable = icon
        var isLayer = false
        if (icon is AdaptiveIconDrawable) {
            val mono = if (Build.VERSION.SDK_INT >= 33) icon.monochrome else null
            if (mono != null) {
                src = mono; isLayer = true
            } else if (icon.foreground != null) {
                src = icon.foreground; isLayer = true
            }
        }

        val raw = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val c = Canvas(raw)
        if (isLayer) {
            // Las capas adaptativas miden 108dp con 72dp visibles: se amplían para llenar.
            val extra = (size * 0.25f).toInt()
            src.setBounds(-extra, -extra, size + extra, size + extra)
        } else {
            src.setBounds(0, 0, size, size)
        }
        src.draw(c)

        val out = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            colorFilter = PorterDuffColorFilter(color, PorterDuff.Mode.SRC_IN)
        }
        Canvas(out).drawBitmap(raw, 0f, 0f, paint)
        raw.recycle()
        return out
    }

    /** Recorta la foto al tamaño de la pantalla y la oscurece. */
    fun makeWallpaper(photo: Bitmap, width: Int, height: Int, dimPercent: Int): Bitmap {
        val out = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val c = Canvas(out)
        val scale = maxOf(width / photo.width.toFloat(), height / photo.height.toFloat())
        val w = photo.width * scale
        val h = photo.height * scale
        val left = (width - w) / 2f
        val top = (height - h) / 2f
        val dst = android.graphics.RectF(left, top, left + w, top + h)
        c.drawBitmap(photo, null, dst, Paint(Paint.FILTER_BITMAP_FLAG))
        val alpha = (dimPercent.coerceIn(0, 100) * 255 / 100)
        c.drawColor(Color.argb(alpha, 0, 0, 0))
        return out
    }
}
