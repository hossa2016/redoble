package com.hossa.redoble

import android.content.Context
import android.graphics.Canvas
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.service.wallpaper.WallpaperService
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.SurfaceHolder
import android.view.View

/**
 * Lee la gravedad del teléfono y la entrega suavizada:
 *  - gx, gy: hacia dónde cae la gravedad en la pantalla (-1..1).
 *  - tx, ty: inclinación respecto a como se sostiene normalmente (para la profundidad).
 */
class Tilt(ctx: Context) : SensorEventListener {
    private val sm = ctx.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val sensor = sm.getDefaultSensor(Sensor.TYPE_GRAVITY) ?: sm.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    private var rawX = 0f; private var rawY = .7f
    var gx = 0f; var gy = .7f; var tx = 0f; var ty = 0f
    val available get() = sensor != null

    fun start() { sensor?.let { sm.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME) } }
    fun stop() { sm.unregisterListener(this) }

    override fun onSensorChanged(e: SensorEvent) {
        rawX = (-e.values[0] / 9.81f).coerceIn(-1f, 1f)
        rawY = (e.values[1] / 9.81f).coerceIn(-1f, 1f)
    }
    override fun onAccuracyChanged(s: Sensor?, a: Int) {}

    /** Suaviza hacia la lectura (o hacia un valor dado, para la vista previa). */
    fun smooth(targetX: Float = rawX, targetY: Float = rawY) {
        gx += (targetX - gx) * .12f; gy += (targetY - gy) * .12f
        tx = (gx * 1.6f).coerceIn(-1f, 1f); ty = ((gy - .65f) * 2.2f).coerceIn(-1f, 1f)
    }
}

/** El fondo de pantalla animado de Prisma Widgets. */
class PrismaWallpaper : WallpaperService() {
    override fun onCreateEngine(): Engine = SceneEngine()

    inner class SceneEngine : Engine() {
        private val handler = Handler(Looper.getMainLooper())
        private var scene: LiveScene = LiveScenes.find(Prefs.liveScene(this@PrismaWallpaper)).make()
        private var sceneId = Prefs.liveScene(this@PrismaWallpaper)
        private val tilt = Tilt(this@PrismaWallpaper)
        private var visible = false
        private var w = 0; private var h = 0
        private val t0 = SystemClock.uptimeMillis(); private var last = t0
        private val tick = object : Runnable { override fun run() { frame(); if (visible) handler.postDelayed(this, 33) } }

        override fun onVisibilityChanged(v: Boolean) {
            visible = v
            if (v) {
                // Si se eligió otra escena en los ajustes, cambiarla al volver
                val id = Prefs.liveScene(this@PrismaWallpaper)
                if (id != sceneId) { sceneId = id; scene = LiveScenes.find(id).make(); if (w > 0) scene.resize(w, h, resources.displayMetrics.density) }
                tilt.start(); last = SystemClock.uptimeMillis(); handler.post(tick)
            } else { tilt.stop(); handler.removeCallbacks(tick) }   // sin pantalla visible no gasta nada
        }

        override fun onSurfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
            super.onSurfaceChanged(holder, format, width, height)
            w = width; h = height; scene.resize(width, height, resources.displayMetrics.density); frame()
        }

        override fun onSurfaceDestroyed(holder: SurfaceHolder) { visible = false; handler.removeCallbacks(tick); tilt.stop(); super.onSurfaceDestroyed(holder) }
        override fun onDestroy() { handler.removeCallbacks(tick); tilt.stop(); super.onDestroy() }

        private fun frame() {
            val now = SystemClock.uptimeMillis(); val dt = ((now - last) / 1000f).coerceIn(0f, .05f); last = now
            tilt.smooth()
            var c: Canvas? = null
            try {
                c = surfaceHolder.lockHardwareCanvas()
                if (c != null) scene.draw(c, tilt.tx, tilt.ty, tilt.gx, tilt.gy, (now - t0) / 1000f, dt)
            } catch (e: Exception) {
            } finally { try { if (c != null) surfaceHolder.unlockCanvasAndPost(c) } catch (e: Exception) { } }
        }
    }
}

/**
 * Vista previa animada para los ajustes: usa el sensor si existe y, además,
 * se puede inclinar deslizando el dedo encima.
 */
class SceneView @JvmOverloads constructor(ctx: Context, attrs: AttributeSet? = null) : View(ctx, attrs) {
    var sceneId: String = "metal"
        set(v) { field = v; scene = LiveScenes.find(v).make(); if (width > 0) scene.resize(width, height, resources.displayMetrics.density) }
    private var scene: LiveScene = LiveScenes.find(sceneId).make()
    private val tilt = Tilt(ctx)
    private var dragX: Float? = null; private var dragY: Float? = null
    private val t0 = SystemClock.uptimeMillis(); private var last = t0

    override fun onSizeChanged(w: Int, h: Int, ow: Int, oh: Int) { scene.resize(w, h, resources.displayMetrics.density) }
    override fun onAttachedToWindow() { super.onAttachedToWindow(); tilt.start() }
    override fun onDetachedFromWindow() { tilt.stop(); super.onDetachedFromWindow() }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> {
                parent?.requestDisallowInterceptTouchEvent(true)
                dragX = ((e.x / width) - .5f) * 2; dragY = ((e.y / height) - .5f) * 2 + .3f
            }
            else -> { dragX = null; dragY = null }
        }
        return true
    }

    override fun onDraw(c: Canvas) {
        val now = SystemClock.uptimeMillis(); val dt = ((now - last) / 1000f).coerceIn(0f, .05f); last = now
        val dx = dragX; val dy = dragY
        if (dx != null && dy != null) tilt.smooth(dx, dy) else tilt.smooth()
        try { scene.draw(c, tilt.tx, tilt.ty, tilt.gx, tilt.gy, (now - t0) / 1000f, dt) } catch (e: Exception) { }
        postInvalidateOnAnimation()
    }
}
