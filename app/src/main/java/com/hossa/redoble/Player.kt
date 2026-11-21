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
 * Reproductor del widget: lee la sesión multimedia activa (RockPlay IA u
 * otra app) y la muestra encima de la foto, con ⏮ ⏯ ⏭.
 */
object Player {
    const val ROCKPLAY = "com.rockplayia.rockplay"

    const val ACTION_PLAY_PAUSE = "com.hossa.redoble.PLAYER_PLAY_PAUSE"
    const val ACTION_NEXT = "com.hossa.redoble.PLAYER_NEXT"
    const val ACTION_PREV = "com.hossa.redoble.PLAYER_PREV"

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

    /** La sesión a mostrar: primero RockPlay sonando, luego cualquiera sonando, luego en pausa. */
    fun controller(ctx: Context): MediaController? {
        if (!enabled(ctx)) return null
        val msm = ctx.getSystemService(Context.MEDIA_SESSION_SERVICE) as MediaSessionManager
        val all = try {
            msm.getActiveSessions(ComponentName(ctx, MediaListener::class.java))
        } catch (e: SecurityException) {
            return null
        }
        var pool = all.filter { isActive(it) }
        if (Prefs.onlyRockPlay(ctx)) pool = pool.filter { it.packageName == ROCKPLAY }
        return pool.firstOrNull { it.packageName == ROCKPLAY && isPlaying(it) }
            ?: pool.firstOrNull { isPlaying(it) }
            ?: pool.firstOrNull { it.packageName == ROCKPLAY }
            ?: pool.firstOrNull()
    }

    /** Botones del widget. */
    fun handle(ctx: Context, action: String?) {
        val c = controller(ctx) ?: return
        val t = c.transportControls
        when (action) {
            ACTION_PLAY_PAUSE -> if (isPlaying(c)) t.pause() else t.play()
            ACTION_NEXT -> t.skipToNext()
            ACTION_PREV -> t.skipToPrevious()
            else -> return
        }
        val app = ctx.applicationContext
        main.postDelayed({ updateAll(app) }, 350)
    }

    /** Actualiza solo la parte del reproductor en todos los widgets. */
    fun updateAll(ctx: Context) {
        val mgr = AppWidgetManager.getInstance(ctx)
        for (id in mgr.getAppWidgetIds(ComponentName(ctx, RockWidget::class.java))) {
            try {
                val v = RemoteViews(ctx.packageName, R.layout.widget_rock)
                apply(ctx, v)
                mgr.partiallyUpdateAppWidget(id, v)
            } catch (e: Exception) { }
        }
    }

    private fun broadcast(ctx: Context, action: String, code: Int): PendingIntent =
        PendingIntent.getBroadcast(
            ctx, code, Intent(ctx, RockWidget::class.java).setAction(action),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

    /** Llena (u oculta) la tarjeta del reproductor. */
    fun apply(ctx: Context, v: RemoteViews) {
        val c = try { controller(ctx) } catch (e: Exception) { null }
        val md = c?.metadata
        val title = md?.getString(MediaMetadata.METADATA_KEY_TITLE)
            ?: md?.description?.title?.toString() ?: ""
        if (c == null || md == null || title.isBlank()) {
            v.setViewVisibility(R.id.player, View.GONE)
            return
        }
        val artist = md.getString(MediaMetadata.METADATA_KEY_ARTIST)
            ?: md.getString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST)
            ?: md.description?.subtitle?.toString() ?: ""
        val playing = isPlaying(c)
        val accent = Prefs.accent(ctx)

        v.setViewVisibility(R.id.player, View.VISIBLE)
        val src = if (c.packageName == ROCKPLAY) "ROCKPLAY" else try {
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

        val art = md.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART)
            ?: md.getBitmap(MediaMetadata.METADATA_KEY_ART)
            ?: md.getBitmap(MediaMetadata.METADATA_KEY_DISPLAY_ICON)
            ?: md.description?.iconBitmap
        v.setImageViewBitmap(R.id.player_art, artBitmap(ctx, art, accent))

        v.setImageViewBitmap(R.id.player_prev, iconBitmap(ctx, "prev", Graphics.HEAD, 24))
        v.setImageViewBitmap(R.id.player_next, iconBitmap(ctx, "next", Graphics.HEAD, 24))
        v.setImageViewBitmap(R.id.player_play, playBitmap(ctx, playing, accent))
        v.setContentDescription(R.id.player_play, if (playing) "Pausar" else "Reproducir")

        v.setOnClickPendingIntent(R.id.player_play, broadcast(ctx, ACTION_PLAY_PAUSE, 201))
        v.setOnClickPendingIntent(R.id.player_next, broadcast(ctx, ACTION_NEXT, 202))
        v.setOnClickPendingIntent(R.id.player_prev, broadcast(ctx, ACTION_PREV, 203))

        // Tocar la carátula o el nombre abre la app que está sonando.
        val open = c.sessionActivity ?: ctx.packageManager.getLaunchIntentForPackage(c.packageName)?.let {
            PendingIntent.getActivity(
                ctx, 204, it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
        }
        if (open != null) {
            v.setOnClickPendingIntent(R.id.player_info, open)
            v.setOnClickPendingIntent(R.id.player_head, open)
        }
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

    private fun artBitmap(ctx: Context, src: Bitmap?, accent: Int): Bitmap {
        val s = dp(ctx, 56f).toInt()
        val r = dp(ctx, 9f)
        val out = Bitmap.createBitmap(s, s, Bitmap.Config.ARGB_8888)
        val c = Canvas(out)
        val rect = RectF(0f, 0f, s.toFloat(), s.toFloat())
        val clip = Path().apply { addRoundRect(rect, r, r, Path.Direction.CW) }
        c.save(); c.clipPath(clip)
        if (src != null) {
            // Recorte centrado (centerCrop).
            val scale = maxOf(s.toFloat() / src.width, s.toFloat() / src.height)
            val w = src.width * scale; val h = src.height * scale
            c.drawBitmap(src, null, RectF((s - w) / 2, (s - h) / 2, (s + w) / 2, (s + h) / 2),
                Paint(Paint.FILTER_BITMAP_FLAG))
        } else {
            val bg = Paint().apply {
                shader = LinearGradient(0f, 0f, s.toFloat(), s.toFloat(),
                    ColorUtils.blendARGB(accent, 0xFF000000.toInt(), 0.55f), 0xFF140C0A.toInt(), Shader.TileMode.CLAMP)
            }
            c.drawRect(rect, bg)
            val note = iconBitmap(ctx, "note", 0xBFEDE6DC.toInt(), 26)
            c.drawBitmap(note, (s - note.width) / 2f, (s - note.height) / 2f, null)
        }
        c.restore()
        val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE; strokeWidth = dp(ctx, 1f); color = 0x1AFFFFFF
        }
        c.drawRoundRect(RectF(0.5f, 0.5f, s - 0.5f, s - 0.5f), r, r, stroke)
        return out
    }

    private fun playBitmap(ctx: Context, playing: Boolean, accent: Int): Bitmap {
        val s = dp(ctx, 52f).toInt()
        val b = Bitmap.createBitmap(s, s, Bitmap.Config.ARGB_8888)
        val c = Canvas(b)
        c.drawCircle(s / 2f, s / 2f, s / 2f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = accent })
        val icon = iconBitmap(ctx, if (playing) "pause" else "play", 0xFFFFFFFF.toInt(), if (playing) 22 else 24)
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
    private val refresh = Runnable { Player.updateAll(this) }

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

    private fun schedule() {
        handler.removeCallbacks(refresh)
        handler.postDelayed(refresh, 250)
    }
}
