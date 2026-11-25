package com.hossa.redoble

import android.content.Context
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.SurfaceTexture
import android.graphics.drawable.GradientDrawable
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.media.MediaPlayer
import android.opengl.EGL14
import android.opengl.EGLConfig
import android.opengl.EGLContext
import android.opengl.EGLDisplay
import android.opengl.EGLSurface
import android.opengl.GLES11Ext
import android.opengl.GLES20
import android.os.Handler
import android.os.HandlerThread
import android.os.SystemClock
import android.service.wallpaper.WallpaperService
import android.view.MotionEvent
import android.view.Surface
import android.view.SurfaceHolder
import android.view.TextureView
import android.view.View
import android.widget.FrameLayout
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import kotlin.math.sin

/** Escenas de video real. [over]: capa delantera opcional (se mezcla en modo "pantalla"). */
object VideoScenes {
    class Info(val id: String, val name: String, val desc: String, val base: Int, val over: Int? = null, val fire: Boolean = false)
    val ALL = listOf(
        Info("fogata", "Fogata", "Fogata real con chispas por delante y la luz del fuego", R.raw.vid_fogata, R.raw.vid_chispas, true),
        Info("brasero", "Brasero", "Fuego intenso con chispas al frente y resplandor cálido", R.raw.vid_brasero, R.raw.vid_chispas, true),
        Info("chispas", "Chispas", "Brasas volando en la oscuridad", R.raw.vid_chispas, null, true),
        Info("ciudad", "Lluvia y ciudad", "Gotas en el vidrio y luces de ciudad desenfocadas", R.raw.vid_ciudad),
        Info("cafe", "Lluvia y café", "Ventana con lluvia, neón y el vapor de un café", R.raw.vid_cafe),
        Info("ventana", "Ventana gris", "Lluvia fuerte resbalando por el vidrio", R.raw.vid_ventana),
        Info("bateria", "Batería", "Tambores entre humo y luz de fuego, con chispas", R.raw.vid_bateria, R.raw.vid_chispas, true),
        Info("concierto", "Concierto", "Luces rojas y lluvia de papelitos sobre el público", R.raw.vid_concierto),
        Info("neon", "Neón y lluvia", "Gotas en el vidrio con reflejos de neón y luces flotando", R.raw.vid_neon, R.raw.vid_luces),
        Info("cristal", "Cristal líquido", "Vidrio iridiscente que fluye con reflejos de arcoíris", R.raw.vid_cristal),
        Info("oro", "Polvo de oro", "Partículas doradas que suben sobre negro", R.raw.vid_oro),
        Info("luces", "Luces flotantes", "Puntos de luz azul y ámbar flotando en la oscuridad", R.raw.vid_luces),
        Info("aurora", "Aurora boreal", "Aurora verde y magenta sobre los pinos, con copos al frente", R.raw.vid_aurora, R.raw.vid_copos),
        Info("nieve", "Nieve", "Copos cayendo sobre un claro del bosque", R.raw.vid_nieve),
        Info("hojas", "Hojas y gotas", "Hojas verdes con gotas de lluvia", R.raw.vid_hojas),
        Info("estallido", "Estallido", "Cristal líquido que estalla y cambia de color", R.raw.vid_estallido),
        Info("ambar", "Vidrio ámbar", "Vidrio quebrado con luces cálidas y brasas al frente", R.raw.vid_ambar, R.raw.vid_brasas, true),
        Info("escenario", "Escenario", "Reflectores sobre el público en un concierto de noche", R.raw.vid_escenario),
        Info("ondas", "Ondas de luz", "Ondas de luz turquesa con polvo brillante al frente", R.raw.vid_ondas, R.raw.vid_copos),
        Info("agua", "Agua", "Superficie del mar moviéndose en calma", R.raw.vid_agua),
        Info("brasas", "Brasas", "Chispas naranjas saltando en la oscuridad", R.raw.vid_brasas, null, true),
    )
    fun find(id: String) = ALL.firstOrNull { it.id == id } ?: ALL[0]
    /** Miniatura (un cuadro del video, con su capa delantera) para elegir la escena. */
    private val THUMBS = mapOf(
        "fogata" to R.drawable.thumb_fogata,
        "brasero" to R.drawable.thumb_brasero,
        "chispas" to R.drawable.thumb_chispas,
        "ciudad" to R.drawable.thumb_ciudad,
        "cafe" to R.drawable.thumb_cafe,
        "ventana" to R.drawable.thumb_ventana,
        "bateria" to R.drawable.thumb_bateria,
        "concierto" to R.drawable.thumb_concierto,
        "neon" to R.drawable.thumb_neon,
        "cristal" to R.drawable.thumb_cristal,
        "oro" to R.drawable.thumb_oro,
        "luces" to R.drawable.thumb_luces,
        "aurora" to R.drawable.thumb_aurora,
        "nieve" to R.drawable.thumb_nieve,
        "hojas" to R.drawable.thumb_hojas,
        "estallido" to R.drawable.thumb_estallido,
        "ambar" to R.drawable.thumb_ambar,
        "brasas" to R.drawable.thumb_brasas,
        "escenario" to R.drawable.thumb_escenario,
        "ondas" to R.drawable.thumb_ondas,
        "agua" to R.drawable.thumb_agua
    )
    fun thumb(id: String): Int? = THUMBS[id]
}

/**
 * Lee la gravedad del teléfono y la entrega suavizada:
 *  - gx, gy: hacia dónde cae la gravedad en la pantalla (-1..1).
 *  - tx, ty: inclinación respecto a como se sostiene normalmente (para la profundidad).
 */
class Tilt(ctx: Context) : SensorEventListener {
    private val sm = ctx.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val sensor = sm.getDefaultSensor(Sensor.TYPE_GRAVITY) ?: sm.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    private var rawX = 0f; private var rawY = .65f
    var gx = 0f; var gy = .65f; var tx = 0f; var ty = 0f

    // Ritmo de interfaz (no de juego): suficiente para el movimiento suave y gasta menos
    fun start() { sensor?.let { sm.registerListener(this, it, SensorManager.SENSOR_DELAY_UI) } }
    fun stop() { sm.unregisterListener(this) }
    override fun onSensorChanged(e: SensorEvent) {
        rawX = (-e.values[0] / 9.81f).coerceIn(-1f, 1f); rawY = (e.values[1] / 9.81f).coerceIn(-1f, 1f)
    }
    override fun onAccuracyChanged(s: Sensor?, a: Int) {}
    fun smooth(targetX: Float = rawX, targetY: Float = rawY) {
        gx += (targetX - gx) * .08f; gy += (targetY - gy) * .08f
        tx = (gx * 1.6f).coerceIn(-1f, 1f); ty = ((gy - .65f) * 2.2f).coerceIn(-1f, 1f)
    }
}

/**
 * Dibuja una escena de video con la GPU: la capa de fondo se desplaza poco, la
 * delantera (chispas) se desplaza más, y la luz del fuego parpadea. Todo corre en
 * su propio hilo para no trabar el inicio.
 */
private class VideoRenderer(private val ctx: Context, private val scene: VideoScenes.Info) {
    private var dpy: EGLDisplay = EGL14.EGL_NO_DISPLAY
    private var eglCtx: EGLContext = EGL14.EGL_NO_CONTEXT
    private var eglSurf: EGLSurface = EGL14.EGL_NO_SURFACE
    private var prog = 0; private var w = 1; private var h = 1
    // ExoPlayer (Media3): repite el video sin el salto que hacía MediaPlayer al reiniciar.
    // Todo se llama desde el hilo del dibujo, que es el hilo del reproductor.
    private class Layer(val tex: Int, val st: SurfaceTexture, val player: androidx.media3.exoplayer.ExoPlayer, val surface: Surface) { val m = FloatArray(16) }
    private val layers = ArrayList<Layer>()
    private val quad: FloatBuffer = ByteBuffer.allocateDirect(32).order(ByteOrder.nativeOrder()).asFloatBuffer().apply {
        put(floatArrayOf(-1f, -1f, 1f, -1f, -1f, 1f, 1f, 1f)); position(0) }

    private val vs = "attribute vec2 a;varying vec2 v;void main(){v=a*.5+.5;gl_Position=vec4(a,0.,1.);}"
    private val fs = """#extension GL_OES_EGL_image_external : require
        precision mediump float;varying vec2 v;uniform samplerExternalOES tex;uniform mat4 st;
        uniform vec2 sc;uniform vec2 off;uniform float rot;uniform float vig;uniform vec4 glow;uniform vec4 tint;
        void main(){vec2 p=v-.5;float c=cos(rot),s=sin(rot);p=vec2(c*p.x-s*p.y,s*p.x+c*p.y);
         p=p*sc+.5+off;vec2 tc=(st*vec4(p,0.,1.)).xy;vec3 col=texture2D(tex,tc).rgb;
         if(glow.w>0.){float d=length((v-glow.xy)*vec2(1.,.6));col+=vec3(1.,.47,.16)*glow.z*smoothstep(.75,0.,d);}
         if(tint.w>0.){float l=dot(col,vec3(.299,.587,.114));col=mix(col,tint.rgb*l*1.8,tint.w);}
         if(vig>0.){float d=length((v-vec2(.5,.45))*vec2(1.,.8));col*=mix(1.,smoothstep(1.05,.35,d),vig);}
         gl_FragColor=vec4(col,1.);}"""

    fun init(surface: Surface) {
        dpy = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
        val ver = IntArray(2); EGL14.eglInitialize(dpy, ver, 0, ver, 1)
        val attrs = intArrayOf(EGL14.EGL_RED_SIZE, 8, EGL14.EGL_GREEN_SIZE, 8, EGL14.EGL_BLUE_SIZE, 8,
            EGL14.EGL_RENDERABLE_TYPE, EGL14.EGL_OPENGL_ES2_BIT, EGL14.EGL_NONE)
        val cfgs = arrayOfNulls<EGLConfig>(1); val n = IntArray(1)
        EGL14.eglChooseConfig(dpy, attrs, 0, cfgs, 0, 1, n, 0)
        eglCtx = EGL14.eglCreateContext(dpy, cfgs[0], EGL14.EGL_NO_CONTEXT, intArrayOf(EGL14.EGL_CONTEXT_CLIENT_VERSION, 2, EGL14.EGL_NONE), 0)
        eglSurf = EGL14.eglCreateWindowSurface(dpy, cfgs[0], surface, intArrayOf(EGL14.EGL_NONE), 0)
        EGL14.eglMakeCurrent(dpy, eglSurf, eglSurf, eglCtx)
        prog = link(vs, fs)
        addLayer(scene.base); scene.over?.let { addLayer(it) }
    }

    private fun addLayer(res: Int) {
        val t = IntArray(1); GLES20.glGenTextures(1, t, 0)
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, t[0])
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)
        val st = SurfaceTexture(t[0]); val surf = Surface(st)
        st.setOnFrameAvailableListener { fresh = true }
        val looper = android.os.Looper.myLooper() ?: android.os.Looper.getMainLooper()
        val mp = try {
            androidx.media3.exoplayer.ExoPlayer.Builder(ctx).setLooper(looper).build().apply {
                setVideoSurface(surf)
                volume = 0f
                repeatMode = androidx.media3.common.Player.REPEAT_MODE_ONE
                setMediaItem(androidx.media3.common.MediaItem.fromUri(
                    android.net.Uri.parse("android.resource://${ctx.packageName}/$res")))
                prepare()
            }
        } catch (e: Exception) { return }
        layers.add(Layer(t[0], st, mp, surf))
    }

    fun size(width: Int, height: Int) { w = width; h = height; fresh = true }
    /** Tinte del color de la carátula (Escenario Vivo); 0 = sin tinte. */
    private var tint = floatArrayOf(1f, 1f, 1f, 0f)
    fun setTint(color: Int?, strength: Float) {
        tint = if (color == null || strength <= 0f) floatArrayOf(1f, 1f, 1f, 0f)
        else floatArrayOf(android.graphics.Color.red(color) / 255f, android.graphics.Color.green(color) / 255f,
            android.graphics.Color.blue(color) / 255f, strength)
        fresh = true
    }

    /** Hay un cuadro nuevo del video (si no, y el teléfono no se movió, no se redibuja). */
    @Volatile var fresh = true
    private var lastTx = 9f; private var lastTy = 9f
    fun play() { layers.forEach { try { it.player.play() } catch (e: Exception) { } } }
    fun pause() { layers.forEach { try { it.player.pause() } catch (e: Exception) { } } }

    fun draw(tx: Float, ty: Float, t: Float, force: Boolean = false) {
        if (prog == 0) return
        // Sin cuadro nuevo ni movimiento (y sin fuego que parpadee): no se gasta GPU
        val moved = kotlin.math.abs(tx - lastTx) > 0.002f || kotlin.math.abs(ty - lastTy) > 0.002f
        if (!force && !fresh && !moved && !scene.fire) return
        fresh = false; lastTx = tx; lastTy = ty
        GLES20.glViewport(0, 0, w, h); GLES20.glClearColor(0f, 0f, 0f, 1f); GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)
        GLES20.glUseProgram(prog)
        val a = GLES20.glGetAttribLocation(prog, "a"); GLES20.glEnableVertexAttribArray(a)
        GLES20.glVertexAttribPointer(a, 2, GLES20.GL_FLOAT, false, 0, quad)
        layers.forEachIndexed { i, l ->
            try { l.st.updateTexImage(); l.st.getTransformMatrix(l.m) } catch (e: Exception) { }
            val vs = l.player.videoSize
            val vw = vs.width.coerceAtLeast(1); val vh = vs.height.coerceAtLeast(1)
            val va = vw.toFloat() / vh; val sa = w.toFloat() / h
            // "cover": llena la pantalla recortando lo que sobra, con un margen para moverse
            val zoom = if (i == 0) 1.12f else 1.2f
            var sx = 1f; var sy = 1f; if (sa < va) sx = sa / va else sy = va / sa
            sx /= zoom; sy /= zoom
            val depth = if (i == 0) 1f else 2.4f
            val mx = (1 - sx) / 2; val my = (1 - sy) / 2
            val ox = (-tx * .035f * depth).coerceIn(-mx, mx); val oy = (ty * .03f * depth).coerceIn(-my, my)
            GLES20.glActiveTexture(GLES20.GL_TEXTURE0); GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, l.tex)
            GLES20.glUniform1i(GLES20.glGetUniformLocation(prog, "tex"), 0)
            GLES20.glUniformMatrix4fv(GLES20.glGetUniformLocation(prog, "st"), 1, false, l.m, 0)
            GLES20.glUniform2f(GLES20.glGetUniformLocation(prog, "sc"), sx, sy)
            GLES20.glUniform2f(GLES20.glGetUniformLocation(prog, "off"), ox, oy)
            GLES20.glUniform1f(GLES20.glGetUniformLocation(prog, "rot"), if (i == 0) tx * .01f else 0f)
            GLES20.glUniform1f(GLES20.glGetUniformLocation(prog, "vig"), if (i == 0) .9f else 0f)
            GLES20.glUniform4f(GLES20.glGetUniformLocation(prog, "tint"), tint[0], tint[1], tint[2], if (i == 0) tint[3] else 0f)
            val flick = .55f + .18f * sin(t * 9.3f) + .12f * sin(t * 15.7f + 1) + .08f * sin(t * 23.1f + 2)
            GLES20.glUniform4f(GLES20.glGetUniformLocation(prog, "glow"), .5f - tx * .08f, .08f, .22f * flick, if (scene.fire && i == 0) 1f else 0f)
            if (i == 0) GLES20.glDisable(GLES20.GL_BLEND)
            else { GLES20.glEnable(GLES20.GL_BLEND); GLES20.glBlendFunc(GLES20.GL_ONE, GLES20.GL_ONE_MINUS_SRC_COLOR) } // modo "pantalla"
            GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
        }
        EGL14.eglSwapBuffers(dpy, eglSurf)
    }

    fun release() {
        layers.forEach { try { it.player.release(); it.surface.release(); it.st.release() } catch (e: Exception) { } }
        layers.clear()
        if (dpy != EGL14.EGL_NO_DISPLAY) {
            EGL14.eglMakeCurrent(dpy, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT)
            EGL14.eglDestroySurface(dpy, eglSurf); EGL14.eglDestroyContext(dpy, eglCtx); EGL14.eglTerminate(dpy)
        }
        dpy = EGL14.EGL_NO_DISPLAY; prog = 0
    }

    private fun link(v: String, f: String): Int {
        fun sh(type: Int, src: String) = GLES20.glCreateShader(type).also { GLES20.glShaderSource(it, src); GLES20.glCompileShader(it) }
        return GLES20.glCreateProgram().also { GLES20.glAttachShader(it, sh(GLES20.GL_VERTEX_SHADER, v)); GLES20.glAttachShader(it, sh(GLES20.GL_FRAGMENT_SHADER, f)); GLES20.glLinkProgram(it) }
    }
}

/** El fondo de pantalla animado de Prisma Widgets (video real con profundidad). */
class PrismaWallpaper : WallpaperService() {
    override fun onCreateEngine(): Engine = VideoEngine()

    inner class VideoEngine : Engine() {
        private val thread = HandlerThread("prisma-fondo").apply { start() }
        private val gl = Handler(thread.looper)
        private val tilt = Tilt(this@PrismaWallpaper)
        private var renderer: VideoRenderer? = null
        private var sceneId = ""
        private var visible = false; private var w = 0; private var h = 0
        private val t0 = SystemClock.uptimeMillis()
        private val tick = object : Runnable { override fun run() {
            tilt.smooth()
            try { renderer?.draw(tilt.tx, tilt.ty, (SystemClock.uptimeMillis() - t0) / 1000f) } catch (e: Exception) { }
            if (visible) gl.postDelayed(this, 33) } }

        /** Escena que toca ahora: la elegida o, con Escenario Vivo y música sonando, la de concierto. */
        private fun wanted(): String {
            val c = this@PrismaWallpaper
            return if (Prefs.liveStage(c) && Stage.playing) Prefs.stageScene(c) else Prefs.liveScene(c)
        }
        private fun applyTint() {
            val c = this@PrismaWallpaper
            renderer?.setTint(if (Prefs.liveStage(c) && Stage.playing) Stage.accent else null, 0.35f)
        }
        private val onStage: () -> Unit = {
            gl.post {
                if (wanted() != sceneId && surfaceHolder.surface.isValid) {
                    build(surfaceHolder)
                    if (android.os.Build.VERSION.SDK_INT >= 27) notifyColorsChanged()
                } else applyTint()
                if (visible) try { renderer?.draw(tilt.tx, tilt.ty, 0f, force = true) } catch (e: Exception) { }
            }
        }

        override fun onCreate(surfaceHolder: SurfaceHolder) { super.onCreate(surfaceHolder); Stage.listen(onStage) }

        private fun build(holder: SurfaceHolder) {
            renderer?.release(); renderer = null
            sceneId = wanted()
            val r = VideoRenderer(this@PrismaWallpaper, VideoScenes.find(sceneId))
            try { r.init(holder.surface); r.size(w, h); renderer = r; applyTint(); if (visible) r.play() } catch (e: Exception) { r.release() }
        }

        override fun onSurfaceCreated(holder: SurfaceHolder) { super.onSurfaceCreated(holder); gl.post { build(holder) } }
        override fun onSurfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
            super.onSurfaceChanged(holder, format, width, height); w = width; h = height; gl.post { renderer?.size(width, height) }
        }
        /** Ahorro de batería o batería baja: el fondo se queda quieto (un cuadro fijo). */
        private fun saving(): Boolean {
            val pm = getSystemService(android.os.PowerManager::class.java)
            return (pm?.isPowerSaveMode == true) || BatteryMonitor.level(this@PrismaWallpaper) in 0..14
        }

        @androidx.annotation.RequiresApi(27)
        override fun onComputeColors(): android.app.WallpaperColors? {
            // Colores de la escena para que HyperOS adapte íconos y barra de estado
            val res = VideoScenes.thumb(sceneId.ifBlank { Prefs.liveScene(this@PrismaWallpaper) }) ?: return null
            return try {
                val bmp = android.graphics.BitmapFactory.decodeResource(resources, res) ?: return null
                android.app.WallpaperColors.fromBitmap(bmp)
            } catch (e: Exception) { null }
        }

        override fun onVisibilityChanged(v: Boolean) {
            visible = v
            if (v) {
                val still = saving()
                if (!still) tilt.start()
                gl.post {
                    // Si se eligió otra escena en los ajustes, cambiarla al volver
                    if (wanted() != sceneId && surfaceHolder.surface.isValid) {
                        build(surfaceHolder)
                        if (android.os.Build.VERSION.SDK_INT >= 27) notifyColorsChanged()
                    }
                    if (still) {
                        // Un instante de video para tener un cuadro, y se congela
                        renderer?.play()
                        gl.postDelayed({ try { renderer?.draw(0f, 0f, 0f, force = true) } catch (e: Exception) { }; renderer?.pause() }, 400)
                    } else renderer?.play()
                }
                gl.removeCallbacks(tick)
                if (!still) gl.post(tick)
            } else {
                tilt.stop(); gl.removeCallbacks(tick); gl.post { renderer?.pause() }   // sin pantalla visible no gasta nada
            }
        }
        override fun onSurfaceDestroyed(holder: SurfaceHolder) {
            visible = false; tilt.stop(); gl.removeCallbacks(tick)
            val latch = java.util.concurrent.CountDownLatch(1)
            gl.post { renderer?.release(); renderer = null; latch.countDown() }
            try { latch.await(1, java.util.concurrent.TimeUnit.SECONDS) } catch (e: Exception) { }
            super.onSurfaceDestroyed(holder)
        }
        override fun onDestroy() { Stage.unlisten(onStage); gl.post { renderer?.release(); renderer = null; thread.quitSafely() }; super.onDestroy() }
    }
}

/**
 * Vista previa para los ajustes: el video de la escena con el mismo movimiento al
 * inclinar (o deslizando el dedo encima) y la luz del fuego.
 */
class VideoPreview(ctx: Context) : FrameLayout(ctx), TextureView.SurfaceTextureListener {
    private val tv = TextureView(ctx)
    private val light = View(ctx)
    private var mp: MediaPlayer? = null
    private val tilt = Tilt(ctx)
    private var dragX: Float? = null; private var dragY: Float? = null
    private val t0 = SystemClock.uptimeMillis()
    var scene: VideoScenes.Info = VideoScenes.ALL[0]
        set(v) { field = v; light.visibility = if (v.fire) View.VISIBLE else View.GONE; restart() }

    init {
        addView(tv, LayoutParams(-1, -1))
        light.background = GradientDrawable().apply {
            gradientType = GradientDrawable.RADIAL_GRADIENT; gradientRadius = 400f; setGradientCenter(.5f, .95f)
            colors = intArrayOf(Color.argb(90, 255, 120, 40), Color.TRANSPARENT)
        }
        addView(light, LayoutParams(-1, -1))
        tv.surfaceTextureListener = this
    }

    private fun restart() {
        mp?.release(); mp = null
        val st = tv.surfaceTexture ?: return
        val p = MediaPlayer.create(context, scene.base) ?: return
        p.setSurface(Surface(st)); p.isLooping = true; p.setVolume(0f, 0f); p.start(); mp = p
    }

    private var ticking = false
    override fun onSurfaceTextureAvailable(st: SurfaceTexture, w: Int, h: Int) { restart(); if (!ticking) { ticking = true; tick() } }
    override fun onSurfaceTextureSizeChanged(st: SurfaceTexture, w: Int, h: Int) {}
    override fun onSurfaceTextureDestroyed(st: SurfaceTexture): Boolean { mp?.release(); mp = null; return true }
    override fun onSurfaceTextureUpdated(st: SurfaceTexture) {}
    override fun onAttachedToWindow() { super.onAttachedToWindow(); tilt.start() }
    override fun onDetachedFromWindow() { tilt.stop(); mp?.release(); mp = null; super.onDetachedFromWindow() }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> {
                parent?.requestDisallowInterceptTouchEvent(true)
                dragX = ((e.x / width) - .5f) * 2; dragY = ((e.y / height) - .5f) * 2 + .65f
            }
            else -> { dragX = null; dragY = null }
        }
        return true
    }

    private fun tick() {
        if (!isAttachedToWindow) { ticking = false; return }
        val dx = dragX; val dy = dragY
        if (dx != null && dy != null) tilt.smooth(dx, dy) else tilt.smooth()
        val p = mp
        if (p != null && width > 0) {
            val va = p.videoWidth.coerceAtLeast(1).toFloat() / p.videoHeight.coerceAtLeast(1); val sa = width.toFloat() / height
            var sx = 1.12f; var sy = 1.12f; if (sa < va) sx *= va / sa else sy *= sa / va
            val m = Matrix(); m.setScale(sx, sy, width / 2f, height / 2f)
            val mx = (sx - 1) * width / 2; val my = (sy - 1) * height / 2
            m.postTranslate((-tilt.tx * width * .035f).coerceIn(-mx, mx), (tilt.ty * height * .03f).coerceIn(-my, my))
            tv.setTransform(m)
        }
        val t = (SystemClock.uptimeMillis() - t0) / 1000f
        light.alpha = .55f + .18f * sin(t * 9.3f) + .12f * sin(t * 15.7f + 1) + .08f * sin(t * 23.1f + 2)
        postOnAnimation { tick() }
    }
}

/**
 * El fondo animado a pantalla completa dentro de la app, dibujado con el mismo motor
 * que el fondo real (capas, luz del fuego e inclinación): lo que se ve es lo que queda.
 */
class VideoSurfaceView(ctx: Context) : android.view.SurfaceView(ctx), SurfaceHolder.Callback {
    private val thread = HandlerThread("prisma-vista").apply { start() }
    private val gl = Handler(thread.looper)
    private val tilt = Tilt(ctx)
    private var renderer: VideoRenderer? = null
    @Volatile private var surfaceOk = false
    @Volatile private var running = false
    @Volatile private var w = 0
    @Volatile private var h = 0
    private val t0 = SystemClock.uptimeMillis()

    var scene: VideoScenes.Info = VideoScenes.ALL[0]
        set(v) {
            if (field.id == v.id && renderer != null) return
            field = v
            if (surfaceOk) gl.post { rebuild() }
        }

    private val tick = object : Runnable {
        override fun run() {
            tilt.smooth()
            try { renderer?.draw(tilt.tx, tilt.ty, (SystemClock.uptimeMillis() - t0) / 1000f) } catch (e: Exception) { }
            if (running) gl.postDelayed(this, 33)
        }
    }

    init { holder.addCallback(this) }

    private fun rebuild() {
        renderer?.release(); renderer = null
        if (!surfaceOk) return
        val r = VideoRenderer(context, scene)
        try { r.init(holder.surface); r.size(w, h); renderer = r; if (running) r.play() } catch (e: Exception) { r.release() }
    }

    override fun surfaceCreated(sh: SurfaceHolder) { surfaceOk = true; gl.post { rebuild() }; start() }
    override fun surfaceChanged(sh: SurfaceHolder, format: Int, width: Int, height: Int) {
        w = width; h = height; gl.post { renderer?.size(width, height) }
    }
    override fun surfaceDestroyed(sh: SurfaceHolder) {
        surfaceOk = false; stop()
        val latch = java.util.concurrent.CountDownLatch(1)
        gl.post { renderer?.release(); renderer = null; latch.countDown() }
        try { latch.await(1, java.util.concurrent.TimeUnit.SECONDS) } catch (e: Exception) { }
    }

    private fun start() {
        if (running) return
        running = true; tilt.start()
        gl.post { renderer?.play() }
        gl.removeCallbacks(tick); gl.post(tick)
    }

    private fun stop() {
        running = false; tilt.stop(); gl.removeCallbacks(tick)
        gl.post { renderer?.pause() }
    }

    override fun onDetachedFromWindow() {
        stop()
        gl.post { renderer?.release(); renderer = null; thread.quitSafely() }
        super.onDetachedFromWindow()
    }
}
