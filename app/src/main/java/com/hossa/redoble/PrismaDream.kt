package com.hossa.redoble

import android.graphics.Color
import android.os.Handler
import android.os.Looper
import android.service.dreams.DreamService
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout

/**
 * Protector de pantalla de Prisma (Android lo muestra mientras el teléfono carga o
 * está en su base). Es lo más cercano a la pantalla siempre activa que una app puede
 * ofrecer: hora, clima y reproductor de Prisma sobre el fondo elegido, con poco brillo.
 * Se mueve un poco cada minuto para no marcar la pantalla.
 */
class PrismaDream : DreamService() {
    private val main = Handler(Looper.getMainLooper())
    private var clockHost: FrameLayout? = null
    private var playerHost: FrameLayout? = null
    private var column: LinearLayout? = null
    private var video: VideoSurfaceView? = null

    private val refreshClock = object : Runnable {
        override fun run() {
            drawClock(); nudge()
            // Al empezar el minuto siguiente
            main.postDelayed(this, 60_000L - System.currentTimeMillis() % 60_000L + 200)
        }
    }
    private val refreshPlayer = object : Runnable {
        override fun run() { drawPlayer(); main.postDelayed(this, 4_000) }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        isInteractive = true      // los botones del reproductor funcionan
        isFullscreen = true
        isScreenBright = false    // brillo bajo, como la pantalla siempre activa

        val dm = resources.displayMetrics
        val root = FrameLayout(this).apply { setBackgroundColor(Color.BLACK) }

        // Fondo: el animado elegido, la imagen del tema o negro puro
        when (Prefs.dreamBg(this)) {
            "black" -> {}
            else -> if (Prefs.wallStyle(this) == "anim") {
                video = VideoSurfaceView(this).apply { scene = VideoScenes.find(Prefs.liveScene(this@PrismaDream)) }
                root.addView(video, FrameLayout.LayoutParams(-1, -1))
            } else {
                val bmp = try {
                    Themes.styledWallpaper(this, Themes.current(this), dm.widthPixels / 2, dm.heightPixels / 2)
                } catch (e: Throwable) { null }
                if (bmp != null) root.addView(ImageView(this).apply { importantForAccessibility = android.view.View.IMPORTANT_FOR_ACCESSIBILITY_NO;
                    scaleType = ImageView.ScaleType.CENTER_CROP; setImageBitmap(bmp)
                }, FrameLayout.LayoutParams(-1, -1))
            }
        }
        // Velo oscuro: menos brillo y menos gasto
        if (Prefs.dreamBg(this) != "black")
            root.addView(View(this).apply { setBackgroundColor(Color.argb(0x8C, 0, 0, 0)) }, FrameLayout.LayoutParams(-1, -1))

        val wDp = minOf((dm.widthPixels / dm.density).toInt() - 48, 420)
        column = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
        }
        clockHost = FrameLayout(this)
        playerHost = FrameLayout(this)
        column!!.addView(clockHost, LinearLayout.LayoutParams(dp(wDp), dp(230)))
        column!!.addView(playerHost, LinearLayout.LayoutParams(dp(wDp), dp(112)).apply { topMargin = dp(18) })
        root.addView(column, FrameLayout.LayoutParams(-2, -2, Gravity.CENTER))
        setContentView(root)
    }

    override fun onDreamingStarted() {
        super.onDreamingStarted()
        main.post(refreshClock); main.post(refreshPlayer)
    }

    override fun onDreamingStopped() {
        main.removeCallbacksAndMessages(null)
        super.onDreamingStopped()
    }

    override fun onDetachedFromWindow() {
        main.removeCallbacksAndMessages(null)
        super.onDetachedFromWindow()
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private fun drawClock() {
        val host = clockHost ?: return
        try {
            val w = host.layoutParams.width
            val rv = RockWidget.buildViews(applicationContext, w, kind = Widgets.CLOCK, hDp = 230)
            host.removeAllViews()
            host.addView(rv.apply(applicationContext, host))
        } catch (e: Exception) { }
    }

    private fun drawPlayer() {
        val host = playerHost ?: return
        // Sin música no se muestra (pantalla más limpia)
        val playing = Prefs.lastPlaying(this)
        host.visibility = if (playing) View.VISIBLE else View.GONE
        if (!playing) return
        try {
            val wDp = (host.layoutParams.width / resources.displayMetrics.density).toInt()
            val rv = Widgets.playerViews(applicationContext, wDp, 112)
            host.removeAllViews()
            host.addView(rv.apply(applicationContext, host))
        } catch (e: Exception) { }
    }

    /** Pequeño desplazamiento cada minuto para no dejar marcas en la pantalla. */
    private fun nudge() {
        val c = column ?: return
        val r = java.util.Random()
        c.animate().translationX((r.nextInt(41) - 20) * resources.displayMetrics.density)
            .translationY((r.nextInt(81) - 40) * resources.displayMetrics.density).setDuration(1200).start()
    }
}
