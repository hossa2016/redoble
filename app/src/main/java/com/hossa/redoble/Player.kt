package com.hossa.redoble

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.Handler
import android.os.Looper
import android.service.notification.NotificationListenerService
import android.view.View
import android.widget.RemoteViews
import androidx.core.app.NotificationManagerCompat
import androidx.core.graphics.ColorUtils

/**
 * Reproductor del widget: lee la sesión multimedia activa (tu app de música
 * preferida u otra) y la muestra en el widget, con ⏮ ⏯ ⏭.
 */
object Player {
    /** App de música preferida por defecto (si está instalada). */
    const val DEFAULT_MUSIC_APP = "com.rockplayia.rockplay"

    const val ACTION_PLAY_PAUSE = "com.hossa.redoble.PLAYER_PLAY_PAUSE"
    const val ACTION_NEXT = "com.hossa.redoble.PLAYER_NEXT"
    const val ACTION_PREV = "com.hossa.redoble.PLAYER_PREV"
    const val ACTION_CLOSE = "com.hossa.redoble.PLAYER_CLOSE"

    private fun key(c: MediaController): String =
        c.packageName + "|" + (c.metadata?.getString(MediaMetadata.METADATA_KEY_TITLE) ?: "")

    private val main = Handler(Looper.getMainLooper())

    /** ¿El usuario dio el permiso de acceso a notificaciones? */
    fun enabled(ctx: Context): Boolean =
        NotificationManagerCompat.getEnabledListenerPackages(ctx).contains(ctx.packageName)

    private fun isActive(c: MediaController): Boolean {
        val s = c.playbackState?.state ?: PlaybackState.STATE_NONE
        return c.metadata != null && s != PlaybackState.STATE_NONE &&
            s != PlaybackState.STATE_STOPPED && s != PlaybackState.STATE_ERROR
    }

    fun isPlaying(c: MediaController): Boolean {
        val s = c.playbackState?.state ?: return false
        return s == PlaybackState.STATE_PLAYING || s == PlaybackState.STATE_BUFFERING ||
            s == PlaybackState.STATE_CONNECTING
    }

    /** La sesión a mostrar: primero tu app preferida sonando, luego cualquiera sonando, luego en pausa. */
    fun controller(ctx: Context): MediaController? {
        if (!enabled(ctx)) return null
        val msm = ctx.getSystemService(Context.MEDIA_SESSION_SERVICE) as MediaSessionManager
        val all = try {
            msm.getActiveSessions(ComponentName(ctx, MediaListener::class.java))
        } catch (e: SecurityException) {
            return null
        }
        val fav = Prefs.musicApp(ctx)
        var pool = all.filter { isActive(it) }
        if (Prefs.onlyMusicApp(ctx) && fav.isNotBlank()) pool = pool.filter { it.packageName == fav }
        return pool.firstOrNull { it.packageName == fav && isPlaying(it) }
            ?: pool.firstOrNull { isPlaying(it) }
            ?: pool.firstOrNull { it.packageName == fav }
            ?: pool.firstOrNull()
    }

    /** Títulos de YouTube vienen con códigos HTML (I&#39;m → I'm). */
    private fun clean(s: String): String =
        if (s.contains('&')) android.text.Html.fromHtml(s, android.text.Html.FROM_HTML_MODE_LEGACY).toString().trim() else s.trim()

    // ---------------------------------------------------------------- carátula
    private val artCache = android.util.LruCache<String, Bitmap>(12)
    private val loading = java.util.Collections.synchronizedSet(HashSet<String>())

    /**
     * Carátula: primero la imagen que trae la sesión; si solo trae la dirección
     * (como RockPlay), se descarga en segundo plano y se redibuja el widget.
     */
    private fun artFor(ctx: Context, md: MediaMetadata): Bitmap? {
        md.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART)?.let { return it }
        md.getBitmap(MediaMetadata.METADATA_KEY_ART)?.let { return it }
        md.getBitmap(MediaMetadata.METADATA_KEY_DISPLAY_ICON)?.let { return it }
        md.description?.iconBitmap?.let { return it }
        val uri = listOf(
            md.getString(MediaMetadata.METADATA_KEY_ALBUM_ART_URI),
            md.getString(MediaMetadata.METADATA_KEY_ART_URI),
            md.getString(MediaMetadata.METADATA_KEY_DISPLAY_ICON_URI),
            md.description?.iconUri?.toString()
        ).firstOrNull { !it.isNullOrBlank() } ?: run {
            // RockPlay: el id de la canción es el del video de YouTube.
            val id = md.getString(MediaMetadata.METADATA_KEY_MEDIA_ID) ?: ""
            if (Regex("^[A-Za-z0-9_-]{11}$").matches(id)) "https://i.ytimg.com/vi/$id/hqdefault.jpg" else null
        } ?: return null
        artCache.get(uri)?.let { return it }
        if (loading.add(uri)) {
            val app = ctx.applicationContext
            Thread {
                val bmp = try { loadArt(app, uri) } catch (e: Throwable) { null }
                if (bmp != null) {
                    artCache.put(uri, bmp)
                    RockWidget.requestUpdate(app, 100, playerOnly = true)
                }
                loading.remove(uri)
            }.start()
        }
        return null
    }

    private fun loadArt(ctx: Context, uri: String): Bitmap? {
        val bytes: ByteArray = if (uri.startsWith("http://") || uri.startsWith("https://")) {
            val con = java.net.URL(uri).openConnection() as java.net.HttpURLConnection
            con.connectTimeout = 8000; con.readTimeout = 8000
            con.instanceFollowRedirects = true
            try { con.inputStream.use { it.readBytes() } } finally { con.disconnect() }
        } else {
            ctx.contentResolver.openInputStream(android.net.Uri.parse(uri))?.use { it.readBytes() } ?: return null
        }
        val o = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
        android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size, o)
        var sample = 1
        while (maxOf(o.outWidth, o.outHeight) / (sample * 2) >= 256) sample *= 2
        return android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size,
            android.graphics.BitmapFactory.Options().apply { inSampleSize = sample })
    }

    /** Botones del widget. */
    fun handle(ctx: Context, action: String?) {
        val c = controller(ctx) ?: return
        val t = c.transportControls
        when (action) {
            ACTION_PLAY_PAUSE -> if (isPlaying(c)) t.pause() else t.play()
            ACTION_NEXT -> t.skipToNext()
            ACTION_PREV -> t.skipToPrevious()
            ACTION_CLOSE -> {
                // Detiene la música y oculta la tarjeta hasta que vuelva a sonar algo.
                Prefs.setPlayerDismissed(ctx, key(c))
                try { t.stop() } catch (e: Exception) { t.pause() }
            }
            else -> return
        }
        RockWidget.requestUpdate(ctx, 350)
    }

    /** Redibuja los widgets (el tamaño de la cabecera depende del reproductor). */
    fun updateAll(ctx: Context) = RockWidget.updateAll(ctx)

    private fun broadcast(ctx: Context, action: String, code: Int): PendingIntent =
        PendingIntent.getBroadcast(
            ctx, code, RockWidget.internal(ctx, action),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

    /** Llena (u oculta) la tarjeta del reproductor. Devuelve si quedó visible. */
    fun apply(ctx: Context, v: RemoteViews, fonts: Boolean = false, widthPx: Int = 0): Boolean {
        v.setViewVisibility(R.id.player, View.GONE)
        v.setViewVisibility(R.id.player_c, View.GONE)
        if (!Prefs.el(ctx, "player")) return false
        val c = try { controller(ctx) } catch (e: Exception) { null }
        val md = c?.metadata
        val title = clean(md?.getString(MediaMetadata.METADATA_KEY_TITLE)
            ?: md?.description?.title?.toString() ?: "")
        if (c == null || md == null || title.isBlank()) return false
        val artist = clean(md.getString(MediaMetadata.METADATA_KEY_ARTIST)
            ?: md.getString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST)
            ?: md.description?.subtitle?.toString() ?: "")
        val playing = isPlaying(c)
        val th = Themes.widget(ctx)
        val accent = th.accent(ctx)

        val dismissed = Prefs.playerDismissed(ctx)
        if (dismissed.isNotEmpty()) {
            if (playing) Prefs.setPlayerDismissed(ctx, "")
            else if (dismissed == key(c)) return false
        }

        val art = artFor(ctx, md)
        val open = c.sessionActivity ?: ctx.packageManager.getLaunchIntentForPackage(c.packageName)?.let {
            PendingIntent.getActivity(
                ctx, 204, it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
        }
        val iconCol = if (th.family == "neon") accent else th.head
        val S = TextArt.spec(th.family)
        val d = ctx.resources.displayMetrics.density
        val W = if (widthPx > 0) widthPx else (360 * d).toInt()
        val glow = if (th.family == "neon") accent else 0
        fun txt(tv: Int, bmp: Int, t: String, font: Int, sp: Float, col: Int, maxW: Int, caps: Boolean = false, sp2: Float = 0f) =
            RockWidget.twin(v, tv, bmp, t.isNotBlank(), fonts) {
                TextArt.render(ctx, t, font, sp, col, sp2, caps, S.shadow, if (font == S.title) glow else 0, maxW)
            }

        if (Prefs.playerLarge(ctx)) {
            v.setViewVisibility(R.id.player, View.VISIBLE)
            val src = try {
                val pm = ctx.packageManager
                pm.getApplicationLabel(pm.getApplicationInfo(c.packageName, 0)).toString().uppercase()
            } catch (e: Exception) { "MÚSICA" }
            v.setTextViewText(R.id.player_src, src)
            v.setTextViewText(R.id.player_state, if (playing) " · Sonando" else " · En pausa")
            v.setViewVisibility(R.id.player_eq, if (playing) View.VISIBLE else View.GONE)
            v.setImageViewBitmap(R.id.player_eq, eqBitmap(ctx, accent))
            v.setTextViewText(R.id.player_title, title)
            v.setTextViewText(R.id.player_artist, artist)
            v.setViewVisibility(R.id.player_artist, if (artist.isBlank()) View.GONE else View.VISIBLE)
            val tw = W - (230 * d).toInt()
            txt(R.id.player_src, R.id.player_src_bmp, src, S.title, 10f, accent, W / 2, sp2 = 0.12f)
            txt(R.id.player_state, R.id.player_state_bmp, if (playing) " · Sonando" else " · En pausa", S.body, 10f, th.dim, W / 3)
            txt(R.id.player_title, R.id.player_title_bmp, title, S.semi, 15f, th.head, tw, caps = th.family == "neon")
            txt(R.id.player_artist, R.id.player_artist_bmp, artist, S.body, 12f, th.dim, tw)
            v.setImageViewBitmap(R.id.player_art, artBitmap(ctx, art, th, 56, false))
            v.setImageViewBitmap(R.id.player_close, iconBitmap(ctx, "close", th.dim, 16))
            v.setOnClickPendingIntent(R.id.player_close, broadcast(ctx, ACTION_CLOSE, 205))
            v.setImageViewBitmap(R.id.player_prev, iconBitmap(ctx, "prev", iconCol, 24))
            v.setImageViewBitmap(R.id.player_next, iconBitmap(ctx, "next", iconCol, 24))
            v.setImageViewBitmap(R.id.player_play, playBitmap(ctx, playing, th, 52))
            v.setContentDescription(R.id.player_play, if (playing) "Pausar" else "Reproducir")
            v.setOnClickPendingIntent(R.id.player_play, broadcast(ctx, ACTION_PLAY_PAUSE, 201))
            v.setOnClickPendingIntent(R.id.player_next, broadcast(ctx, ACTION_NEXT, 202))
            v.setOnClickPendingIntent(R.id.player_prev, broadcast(ctx, ACTION_PREV, 203))
            if (open != null) {
                v.setOnClickPendingIntent(R.id.player_info, open)
                v.setOnClickPendingIntent(R.id.player_head, open)
            }
        } else {
            v.setViewVisibility(R.id.player_c, View.VISIBLE)
            v.setTextViewText(R.id.pc_title, title)
            v.setTextViewText(R.id.pc_artist, artist)
            v.setViewVisibility(R.id.pc_artist, if (artist.isBlank()) View.GONE else View.VISIBLE)
            val tw = W - (180 * d).toInt()
            txt(R.id.pc_title, R.id.pc_title_bmp, title, S.semi, 14f, th.head, tw, caps = th.family == "neon")
            txt(R.id.pc_artist, R.id.pc_artist_bmp, artist, S.body, 12f, th.dim, tw)
            v.setImageViewBitmap(R.id.pc_art, artBitmap(ctx, art, th, 46, playing))
            v.setImageViewBitmap(R.id.pc_play, playBitmap(ctx, playing, th, 40))
            v.setContentDescription(R.id.pc_play, if (playing) "Pausar" else "Reproducir")
            v.setImageViewBitmap(R.id.pc_next, iconBitmap(ctx, "next", iconCol, 22))
            v.setOnClickPendingIntent(R.id.pc_play, broadcast(ctx, ACTION_PLAY_PAUSE, 211))
            v.setOnClickPendingIntent(R.id.pc_next, broadcast(ctx, ACTION_NEXT, 212))
            if (open != null) v.setOnClickPendingIntent(R.id.pc_info, open)
        }
        return true
    }

    // ---------------------------------------------------------------- dibujo

    private fun dp(ctx: Context, v: Float) = v * ctx.resources.displayMetrics.density

    private fun eqBitmap(ctx: Context, accent: Int): Bitmap {
        val w = dp(ctx, 16f).toInt(); val h = dp(ctx, 14f).toInt()
        val b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(b)
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = accent }
        val bars = floatArrayOf(0.62f, 1f, 0.5f, 0.8f)
        val bw = dp(ctx, 3f); val gap = (w - bw * 4) / 3f
        bars.forEachIndexed { i, f ->
            val x = i * (bw + gap)
            c.drawRoundRect(RectF(x, h * (1 - f), x + bw, h.toFloat()), dp(ctx, 1f), dp(ctx, 1f), p)
        }
        return b
    }

    private fun artBitmap(ctx: Context, src: Bitmap?, th: Theme, sizeDp: Int, badge: Boolean): Bitmap {
        val accent = th.accent(ctx)
        val s = dp(ctx, sizeDp.toFloat()).toInt()
        val r = dp(ctx, if (th.family == "neon") 4f else sizeDp / 6f)
        val out = Bitmap.createBitmap(s, s, Bitmap.Config.ARGB_8888)
        val c = Canvas(out)
        val rect = RectF(0f, 0f, s.toFloat(), s.toFloat())
        val clip = Path().apply { addRoundRect(rect, r, r, Path.Direction.CW) }
        c.save(); c.clipPath(clip)
        if (src != null) {
            val scale = maxOf(s.toFloat() / src.width, s.toFloat() / src.height)
            val w = src.width * scale; val h = src.height * scale
            c.drawBitmap(src, null, RectF((s - w) / 2, (s - h) / 2, (s + w) / 2, (s + h) / 2),
                Paint(Paint.FILTER_BITMAP_FLAG))
        } else {
            val base = if (th.light) 0xFFE9E3DA.toInt() else 0xFF140C0A.toInt()
            val bg = Paint().apply {
                shader = LinearGradient(0f, 0f, s.toFloat(), s.toFloat(),
                    ColorUtils.blendARGB(accent, base, 0.55f), base, Shader.TileMode.CLAMP)
            }
            c.drawRect(rect, bg)
            val note = iconBitmap(ctx, "note", ColorUtils.setAlphaComponent(th.head, 0xBF), sizeDp / 2)
            c.drawBitmap(note, (s - note.width) / 2f, (s - note.height) / 2f, null)
        }
        c.restore()
        val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE; strokeWidth = dp(ctx, 1f)
            color = if (th.light) 0x141C1A17 else 0x1AFFFFFF
        }
        c.drawRoundRect(RectF(0.5f, 0.5f, s - 0.5f, s - 0.5f), r, r, stroke)
        if (badge) {
            // Pequeña insignia de ecualizador en la esquina: "sonando".
            val bs = dp(ctx, 16f)
            val cx = s - bs / 2 - dp(ctx, 1f); val cy = s - bs / 2 - dp(ctx, 1f)
            c.drawCircle(cx, cy, bs / 2, Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = if (th.light) 0xFFFFFFFF.toInt() else 0xFF120F0C.toInt()
            })
            val eq = eqBitmap(ctx, accent)
            val k = 0.62f
            c.save(); c.translate(cx - eq.width * k / 2, cy - eq.height * k / 2); c.scale(k, k)
            c.drawBitmap(eq, 0f, 0f, null); c.restore()
        }
        return out
    }

    private fun playBitmap(ctx: Context, playing: Boolean, th: Theme, sizeDp: Int): Bitmap {
        val s = dp(ctx, sizeDp.toFloat()).toInt()
        val b = Bitmap.createBitmap(s, s, Bitmap.Config.ARGB_8888)
        val c = Canvas(b)
        val accent = th.accent(ctx)
        val half = s / 2f
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        var iconColor = 0xFFFFFFFF.toInt()
        when (th.family) {
            "cristal" -> p.shader = LinearGradient(0f, 0f, s.toFloat(), s.toFloat(), accent, th.accent2, Shader.TileMode.CLAMP)
            "lujo" -> { p.shader = LinearGradient(0f, 0f, 0f, s.toFloat(), 0xFFE3C47E.toInt(), 0xFFA98545.toInt(), Shader.TileMode.CLAMP); iconColor = 0xFF1A160F.toInt() }
            "neon" -> { p.color = 0x1F3DF2FF; iconColor = accent }
            "claro" -> p.color = th.head
            else -> p.color = accent
        }
        c.drawCircle(half, half, half - dp(ctx, 1f), p)
        if (th.family == "neon") {
            c.drawCircle(half, half, half - dp(ctx, 1.5f), Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE; strokeWidth = dp(ctx, 1.5f); color = accent
            })
        }
        val icon = iconBitmap(ctx, if (playing) "pause" else "play", iconColor, (sizeDp * if (playing) 0.42f else 0.46f).toInt())
        c.drawBitmap(icon, (s - icon.width) / 2f, (s - icon.height) / 2f, null)
        return b
    }

    /** Íconos en una cuadrícula de 24×24. */
    private fun iconBitmap(ctx: Context, kind: String, color: Int, sizeDp: Int): Bitmap {
        val s = dp(ctx, sizeDp.toFloat()).toInt()
        val b = Bitmap.createBitmap(s, s, Bitmap.Config.ARGB_8888)
        val c = Canvas(b)
        val k = s / 24f
        c.scale(k, k)
        val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color }
        when (kind) {
            "play" -> c.drawPath(Path().apply { moveTo(8f, 5f); lineTo(20f, 12f); lineTo(8f, 19f); close() }, fill)
            "pause" -> {
                c.drawRoundRect(RectF(6f, 5f, 10f, 19f), 1f, 1f, fill)
                c.drawRoundRect(RectF(14f, 5f, 18f, 19f), 1f, 1f, fill)
            }
            "next" -> {
                c.drawPath(Path().apply { moveTo(5f, 5f); lineTo(16f, 12f); lineTo(5f, 19f); close() }, fill)
                c.drawRect(16.5f, 5f, 19f, 19f, fill)
            }
            "prev" -> {
                c.drawPath(Path().apply { moveTo(19f, 5f); lineTo(8f, 12f); lineTo(19f, 19f); close() }, fill)
                c.drawRect(5f, 5f, 7.5f, 19f, fill)
            }
            "close" -> {
                val st = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    this.color = color; style = Paint.Style.STROKE; strokeWidth = 2.6f
                    strokeCap = Paint.Cap.ROUND
                }
                c.drawLine(5f, 5f, 19f, 19f, st)
                c.drawLine(19f, 5f, 5f, 19f, st)
            }
            "note" -> {
                val st = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    this.color = color; style = Paint.Style.STROKE; strokeWidth = 1.8f
                    strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND
                }
                c.drawPath(Path().apply { moveTo(9f, 18f); lineTo(9f, 5f); lineTo(19f, 3f); lineTo(19f, 16f) }, st)
                c.drawCircle(6f, 18f, 3f, st)
                c.drawCircle(16f, 16f, 3f, st)
            }
        }
        return b
    }
}

/**
 * Servicio de "acceso a notificaciones": solo sirve para que Android deje
 * ver la música que suena y avisar al widget cuando cambia. No lee mensajes.
 */
class MediaListener : NotificationListenerService() {
    private val handler = Handler(Looper.getMainLooper())
    private val attached = mutableListOf<Pair<MediaController, MediaController.Callback>>()
    private var msm: MediaSessionManager? = null


    private val sessions = MediaSessionManager.OnActiveSessionsChangedListener { list ->
        attach(list); schedule()
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        val m = getSystemService(MEDIA_SESSION_SERVICE) as MediaSessionManager
        msm = m
        val me = ComponentName(this, MediaListener::class.java)
        try {
            m.addOnActiveSessionsChangedListener(sessions, me, handler)
            attach(m.getActiveSessions(me))
        } catch (e: SecurityException) { }
        schedule()
    }

    override fun onListenerDisconnected() {
        try { msm?.removeOnActiveSessionsChangedListener(sessions) } catch (e: Exception) { }
        detach()
        schedule()
        super.onListenerDisconnected()
    }

    private fun attach(list: List<MediaController>?) {
        detach()
        list?.forEach { c ->
            val cb = object : MediaController.Callback() {
                override fun onPlaybackStateChanged(state: PlaybackState?) = schedule()
                override fun onMetadataChanged(metadata: MediaMetadata?) = schedule()
                override fun onSessionDestroyed() = schedule()
            }
            try { c.registerCallback(cb, handler); attached.add(c to cb) } catch (e: Exception) { }
        }
    }

    private fun detach() {
        attached.forEach { (c, cb) -> try { c.unregisterCallback(cb) } catch (e: Exception) { } }
        attached.clear()
    }

    /** Agrupa cambios seguidos y no redibuja con la pantalla apagada. */
    private fun schedule() = RockWidget.requestUpdate(this, 600, playerOnly = true)
}
