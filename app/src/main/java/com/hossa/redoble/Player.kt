package com.hossa.redoble

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
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
import android.os.Build
import android.util.TypedValue
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

    /** ¿Suena algo y de qué color es su carátula? (para Escenario Vivo y el color en todo el widget) */
    fun nowState(ctx: Context): Pair<Boolean, Int?> {
        val c = controller(ctx) ?: return false to null
        val playing = isPlaying(c)
        val md = c.metadata ?: return playing to null
        val art = artFor(ctx, md, download = false)
        return playing to art?.let { coverAccent(it, false) }
    }

    /** ¿Suena algo? (liviano: sin carátula ni colores) */
    fun isPlayingNow(ctx: Context): Boolean = controller(ctx)?.let { isPlaying(it) } ?: false

    /** Título y artista de lo que suena ahora (o lo último que sonó). */
    /** ¿Está sonando algo ahora mismo? (para la Pila inteligente) */
    fun playingNow(ctx: Context): Boolean = try { controller(ctx)?.let { isPlaying(it) } ?: false } catch (e: Exception) { false }

    fun nowTrack(ctx: Context): Pair<String, String>? {
        val c = try { controller(ctx) } catch (e: Exception) { null } ?: return null
        val md = c.metadata ?: return null
        val title = clean(md.getString(MediaMetadata.METADATA_KEY_TITLE) ?: md.description?.title?.toString() ?: "")
        val artist = clean(md.getString(MediaMetadata.METADATA_KEY_ARTIST) ?: md.getString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST)
            ?: md.description?.subtitle?.toString() ?: "")
        return if (title.isBlank()) null else Meta.split(title, artist)
    }

    /** Carátula de lo que suena (sin descargar). */
    fun nowArt(ctx: Context): Bitmap? {
        val c = try { controller(ctx) } catch (e: Exception) { null } ?: return null
        return c.metadata?.let { artFor(ctx, it, download = false) }
    }

    /** Pausa la música (temporizador «Bajar el Telón»). */
    fun pause(ctx: Context) {
        try { controller(ctx)?.transportControls?.pause() } catch (e: Exception) { }
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
    private fun artFor(ctx: Context, md: MediaMetadata, download: Boolean = true): Bitmap? {
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
        val disk = java.io.File(ctx.cacheDir, "art_" + Integer.toHexString(uri.hashCode()) + ".jpg")
        if (disk.exists()) android.graphics.BitmapFactory.decodeFile(disk.path)?.let { artCache.put(uri, it); return it }
        if (download && loading.add(uri)) {
            val app = ctx.applicationContext
            Thread {
                val bmp = try { loadArt(app, uri) } catch (e: Throwable) { null }
                if (bmp != null) {
                    artCache.put(uri, bmp)
                    try { disk.outputStream().use { bmp.compress(Bitmap.CompressFormat.JPEG, 88, it) } } catch (e: Exception) { }
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
        // ⏮ ⏯ ⏭ solo redibujan la tarjeta del reproductor (sin parpadeo de todo el
        // widget). Cerrar sí redibuja todo, porque la tarjeta desaparece.
        RockWidget.requestUpdate(ctx, 350, playerOnly = action != ACTION_CLOSE)
    }

    /** Redibuja los widgets (el tamaño de la cabecera depende del reproductor). */
    fun updateAll(ctx: Context) = RockWidget.updateAll(ctx)

    private fun broadcast(ctx: Context, action: String, code: Int): PendingIntent =
        PendingIntent.getBroadcast(
            ctx, code, RockWidget.internal(ctx, action),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

    /** Llena (u oculta) la tarjeta del reproductor. Devuelve si quedó visible. */
    /**
     * [mode]: 0 = dentro del widget Completo (se oculta sin música); 1 y 2 = el widget
     * Reproductor por separado, grande o compacto (sin música muestra la última canción
     * o «Nada sonando»).
     */
    fun apply(ctx: Context, v: RemoteViews, fonts: Boolean = false, widthPx: Int = 0, preview: Boolean = false, mode: Int = 0,
              sample: Boolean = false, tall: Boolean = false): Boolean {
        v.setViewVisibility(R.id.player, View.GONE)
        v.setViewVisibility(R.id.player_c, View.GONE)
        // En el Completo, sin tarjeta de música su hueco desaparece (las apps suben bajo la hora)
        if (mode == 0) v.setViewVisibility(R.id.middle, View.GONE)
        if (mode < 0 || (mode == 0 && !Prefs.el(ctx, "player"))) return false
        val c = try { controller(ctx) } catch (e: Exception) { null }
        prog = progressOf(c)
        val md = c?.metadata
        val title0 = clean(md?.getString(MediaMetadata.METADATA_KEY_TITLE)
            ?: md?.description?.title?.toString() ?: "")
        if (c == null || md == null || title0.isBlank()) {
            if (mode != 0) idle(ctx, v, fonts, widthPx, mode == 1)
            // Editor visual: sin música se muestra igual, para poder elegirlo y moverlo
            else if (sample) idle(ctx, v, fonts, widthPx, Prefs.playerLarge(ctx))
            return false
        }
        val (title, artist) = Meta.split(title0, clean(md.getString(MediaMetadata.METADATA_KEY_ARTIST)
            ?: md.getString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST)
            ?: md.description?.subtitle?.toString() ?: ""))
        val playing = isPlaying(c)
        val art = artFor(ctx, md, download = !preview)  // la vista previa no descarga nada
        val th = coverTheme(ctx, Themes.widget(ctx), art)
        val accent = th.accent(ctx)

        if (!preview) Prefs.setLastSong(ctx, title, artist, c.packageName)
        val dismissed = if (mode == 0) Prefs.playerDismissed(ctx) else ""
        if (dismissed.isNotEmpty()) {
            if (playing) { if (!preview) Prefs.setPlayerDismissed(ctx, "") }
            else if (dismissed == key(c)) return false
        }

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
            RockWidget.twin(v, tv, bmp, t.isNotBlank(), fonts, t) {
                TextArt.render(ctx, t, font, sp, col, sp2, caps, S.shadow, if (font == S.title) glow else 0, maxW, translate = false)
            }

        if (if (mode == 0) Prefs.playerLarge(ctx) || tall else mode == 1) {
            v.setViewVisibility(R.id.player, View.VISIBLE)
            v.setViewVisibility(R.id.middle, View.VISIBLE)
            // En el widget Reproductor no hay X: el widget se quita desde la pantalla de inicio
            v.setViewVisibility(R.id.player_close, if (mode == 0) View.VISIBLE else View.GONE)
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
            val tw = (W - (140 * d).toInt()).coerceAtLeast((60 * d).toInt())  // los botones van en otra fila: el nombre usa casi todo el ancho
            txt(R.id.player_src, R.id.player_src_bmp, src, S.title, 11f, accent, W / 2, sp2 = 0.12f)
            txt(R.id.player_state, R.id.player_state_bmp, if (playing) " · Sonando" else " · En pausa", S.body, 11f, th.dim, W / 3)
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
            v.setViewVisibility(R.id.middle, View.VISIBLE)
            v.setTextViewText(R.id.pc_title, title)
            v.setTextViewText(R.id.pc_artist, artist)
            v.setViewVisibility(R.id.pc_artist, if (artist.isBlank()) View.GONE else View.VISIBLE)
            val tw = (W - (180 * d).toInt()).coerceAtLeast((60 * d).toInt())
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

    /**
     * Widget Reproductor suelto en tres tamaños: 0 = pequeño (píldora), 1 = mediano
     * (carátula a la izquierda), 2 = grande (carátula arriba). Sin música muestra la
     * última canción o «Nada sonando», y tocarlo abre tu app de música.
     */
    fun applyStandalone(ctx: Context, v: RemoteViews, variant: Int, wPx: Int, artDp: Int, fonts: Boolean, showText: Boolean = true,
                        roomDp: Int = 0) {
        val S = TextArt.spec(Themes.widget(ctx).family)
        val d = ctx.resources.displayMetrics.density
        v.setViewVisibility(R.id.ws, if (variant == 0) View.VISIBLE else View.GONE)
        v.setViewVisibility(R.id.wm, if (variant == 1) View.VISIBLE else View.GONE)
        v.setViewVisibility(R.id.wl, if (variant == 2) View.VISIBLE else View.GONE)

        val c = try { controller(ctx) } catch (e: Exception) { null }
        prog = progressOf(c)
        val md = c?.metadata
        var title = clean(md?.getString(MediaMetadata.METADATA_KEY_TITLE) ?: md?.description?.title?.toString() ?: "")
        val live = c != null && md != null && title.isNotBlank()
        var artist: String
        val playing: Boolean
        var art: Bitmap? = null
        val pkg: String
        val play: PendingIntent?; val next: PendingIntent?; val prev: PendingIntent?; val open: PendingIntent?
        fun launch(p: String) = ctx.packageManager.getLaunchIntentForPackage(p)?.let {
            PendingIntent.getActivity(ctx, 310, it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        }
        if (live) {
            val ta = Meta.split(title, clean(md!!.getString(MediaMetadata.METADATA_KEY_ARTIST)
                ?: md.getString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST) ?: md.description?.subtitle?.toString() ?: ""))
            title = ta.first; artist = ta.second
            playing = isPlaying(c!!)
            art = artFor(ctx, md, download = true)
            pkg = c.packageName
            Prefs.setLastSong(ctx, title, artist, pkg)
            play = broadcast(ctx, ACTION_PLAY_PAUSE, 301); next = broadcast(ctx, ACTION_NEXT, 302)
            prev = broadcast(ctx, ACTION_PREV, 303); open = c.sessionActivity ?: launch(pkg)
        } else {
            val (lt, la, lp) = Prefs.lastSong(ctx)
            val useLast = Prefs.pwIdle(ctx) == "last" && lt.isNotBlank()
            title = if (useLast) lt else "Nada sonando"
            artist = if (useLast) la else "Toca ▶ para abrir tu música"
            playing = false
            pkg = (if (useLast) lp else "").ifBlank { Prefs.musicApp(ctx) }
            val l = launch(pkg)
            play = l; next = l; prev = l; open = l
        }
        val th = coverTheme(ctx, Themes.widget(ctx), art)
        val accent = th.accent(ctx)
        val iconCol = if (th.family == "neon") accent else th.head
        val src = try {
            val pm = ctx.packageManager
            pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString().uppercase()
        } catch (e: Exception) { "MÚSICA" }
        fun txt(tv: Int, bmp: Int, t: String, font: Int, sp: Float, col: Int, maxW: Int, caps: Boolean = false, sp2: Float = 0f) {
            v.setTextViewText(tv, t); v.setTextColor(tv, col)
            RockWidget.twin(v, tv, bmp, t.isNotBlank(), fonts, t) {
                TextArt.render(ctx, t, font, sp, col, sp2, caps, S.shadow, 0, maxW.coerceAtLeast((40 * d).toInt()), translate = false)
            }
        }
        val neonCaps = th.family == "neon"
        fun sizeArt(id: Int) {
            if (Build.VERSION.SDK_INT >= 31) {
                v.setViewLayoutWidth(id, artDp.toFloat(), TypedValue.COMPLEX_UNIT_DIP)
                v.setViewLayoutHeight(id, artDp.toFloat(), TypedValue.COMPLEX_UNIT_DIP)
            }
        }
        when (variant) {
            0 -> {
                val tw = wPx - (128 * d).toInt()
                v.setViewVisibility(R.id.ws_texts, if (showText) View.VISIBLE else View.GONE)
                // Muy angosto (2 celdas): carátula y ⏯; ⏭ solo si cabe
                v.setViewVisibility(R.id.ws_next, if (wPx / d >= 118) View.VISIBLE else View.GONE)
                txt(R.id.ws_title, R.id.ws_title_bmp, title, S.semi, 14f, th.head, tw, neonCaps)
                txt(R.id.ws_artist, R.id.ws_artist_bmp, artist, S.body, 11f, th.dim, tw)
                v.setImageViewBitmap(R.id.ws_art, artBitmap(ctx, art, th, 44, playing))
                v.setImageViewBitmap(R.id.ws_play, playBitmap(ctx, playing, th, 40))
                v.setImageViewBitmap(R.id.ws_next, iconBitmap(ctx, "next", iconCol, 20))
                v.setContentDescription(R.id.ws_play, if (playing) "Pausar" else "Reproducir")
                play?.let { v.setOnClickPendingIntent(R.id.ws_play, it) }
                next?.let { v.setOnClickPendingIntent(R.id.ws_next, it) }
                open?.let { v.setOnClickPendingIntent(R.id.ws_info, it) }
            }
            1 -> {
                val tw = wPx - ((artDp + 14 + 28) * d).toInt()
                sizeArt(R.id.wm_art)
                // Si la columna es angosta no caben los tres botones: se quita ⏮
                // ⏮ es el primero en irse; ⏭ se queda mientras quepa junto a ⏯
                v.setViewVisibility(R.id.wm_prev, if (tw / d >= 160) View.VISIBLE else View.GONE)
                v.setViewVisibility(R.id.wm_next, if (tw / d >= 100) View.VISIBLE else View.GONE)
                val roomyM = tw / d >= 170
                // Alto justo: sin la línea de la app y con ⏯ más pequeño, para que nada se corte
                val fsm = ctx.resources.configuration.fontScale.coerceIn(0.8f, 1.6f)
                val shortM = roomDp > 0 && roomDp < (66 * fsm + 54).toInt()
                txt(R.id.wm_src, R.id.wm_src_bmp, if (shortM) "" else src + if (live && roomyM) (if (playing) " · SONANDO" else " · EN PAUSA") else "",
                    S.title, 11f, accent, tw, sp2 = 0.12f)
                txt(R.id.wm_title, R.id.wm_title_bmp, title, S.semi, 17f, th.head, tw, neonCaps)
                txt(R.id.wm_artist, R.id.wm_artist_bmp, artist, S.body, 13f, th.dim, tw)
                v.setImageViewBitmap(R.id.wm_art, artBitmap(ctx, art, th, artDp, false))
                v.setImageViewBitmap(R.id.wm_prev, iconBitmap(ctx, "prev", iconCol, 22))
                v.setImageViewBitmap(R.id.wm_play, playBitmap(ctx, playing, th, if (shortM) 38 else 46))
                v.setImageViewBitmap(R.id.wm_next, iconBitmap(ctx, "next", iconCol, 22))
                v.setContentDescription(R.id.wm_play, if (playing) "Pausar" else "Reproducir")
                play?.let { v.setOnClickPendingIntent(R.id.wm_play, it) }
                next?.let { v.setOnClickPendingIntent(R.id.wm_next, it) }
                prev?.let { v.setOnClickPendingIntent(R.id.wm_prev, it) }
                open?.let { v.setOnClickPendingIntent(R.id.wm_art, it); v.setOnClickPendingIntent(R.id.wm_info, it) }
            }
            else -> {
                val tw = wPx - (40 * d).toInt()
                sizeArt(R.id.wl_art)
                txt(R.id.wl_title, R.id.wl_title_bmp, title, S.semi, 19f, th.head, tw, neonCaps)
                txt(R.id.wl_artist, R.id.wl_artist_bmp, artist, S.body, 14f, th.dim, tw)
                v.setImageViewBitmap(R.id.wl_art, artBitmap(ctx, art, th, artDp, false))
                v.setImageViewBitmap(R.id.wl_prev, iconBitmap(ctx, "prev", iconCol, 24))
                v.setImageViewBitmap(R.id.wl_play, playBitmap(ctx, playing, th, 56))
                v.setImageViewBitmap(R.id.wl_next, iconBitmap(ctx, "next", iconCol, 24))
                v.setContentDescription(R.id.wl_play, if (playing) "Pausar" else "Reproducir")
                play?.let { v.setOnClickPendingIntent(R.id.wl_play, it) }
                next?.let { v.setOnClickPendingIntent(R.id.wl_next, it) }
                prev?.let { v.setOnClickPendingIntent(R.id.wl_prev, it) }
                open?.let { v.setOnClickPendingIntent(R.id.wl_art, it); v.setOnClickPendingIntent(R.id.wl_info, it) }
            }
        }
    }

    /** El tema con el color de la carátula (si la opción está activa y la carátula tiene color). */
    private fun coverTheme(ctx: Context, th: Theme, art: Bitmap?): Theme {
        if (art == null || !Prefs.coverColor(ctx)) return th
        val c = coverAccent(art, th.light) ?: return th
        return th.withAccent(c)
    }

    /**
     * Color principal de una carátula: el tono con más color (no el gris ni el negro),
     * ajustado para que se lea bien sobre el widget claro u oscuro.
     */
    private val accentCache = android.util.LruCache<Int, Int>(16)
    fun coverAccent(art: Bitmap, lightBg: Boolean): Int? {
        val key = art.hashCode() * 31 + (if (lightBg) 1 else 0)
        accentCache.get(key)?.let { return if (it == 0) null else it }
        val small = try { Bitmap.createScaledBitmap(art, 24, 24, true) } catch (e: Exception) { return null }
        val bins = FloatArray(24); val rs = FloatArray(24); val gs = FloatArray(24); val bs = FloatArray(24)
        val hsl = FloatArray(3); var total = 0f
        for (y in 0 until small.height) for (x in 0 until small.width) {
            val px = small.getPixel(x, y)
            ColorUtils.colorToHSL(px, hsl)
            val l = hsl[2]; val sat = hsl[1]
            total += 1f
            if (l < 0.1f || l > 0.93f || sat < 0.2f) continue
            val w = sat * (1f - kotlin.math.abs(l - 0.5f))
            val b = ((hsl[0] / 15f).toInt()).coerceIn(0, 23)
            bins[b] += w; rs[b] += Color.red(px) * w; gs[b] += Color.green(px) * w; bs[b] += Color.blue(px) * w
        }
        val best = bins.indices.maxByOrNull { bins[it] } ?: return null
        // Carátula casi sin color (blanco y negro): se queda el color del tema
        if (bins[best] < total * 0.04f) { accentCache.put(key, 0); return null }
        val w = bins[best]
        val avg = Color.rgb((rs[best] / w).toInt(), (gs[best] / w).toInt(), (bs[best] / w).toInt())
        ColorUtils.colorToHSL(avg, hsl)
        hsl[1] = hsl[1].coerceIn(0.45f, 0.85f)
        hsl[2] = if (lightBg) 0.40f else 0.62f
        val out = ColorUtils.HSLToColor(hsl)
        accentCache.put(key, out)
        return out
    }

    /** Widget Reproductor sin música: la última canción (para seguir) o «Nada sonando». */
    private fun idle(ctx: Context, v: RemoteViews, fonts: Boolean, widthPx: Int, large: Boolean) {
        val th = Themes.widget(ctx)
        val accent = th.accent(ctx)
        val S = TextArt.spec(th.family)
        val d = ctx.resources.displayMetrics.density
        val W = if (widthPx > 0) widthPx else (360 * d).toInt()
        val (lt, la, lp) = Prefs.lastSong(ctx)
        val useLast = Prefs.pwIdle(ctx) == "last" && lt.isNotBlank()
        val title = if (useLast) lt else "Nada sonando"
        val artist = if (useLast) la else "Toca ▶ para abrir tu música"
        val pkg = (if (useLast) lp else "").ifBlank { Prefs.musicApp(ctx) }
        val launch = ctx.packageManager.getLaunchIntentForPackage(pkg)?.let {
            PendingIntent.getActivity(ctx, 220, it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        }
        val iconCol = if (th.family == "neon") accent else th.head
        fun txt(tv: Int, bmp: Int, t: String, font: Int, sp: Float, col: Int, maxW: Int, caps: Boolean = false, sp2: Float = 0f) =
            RockWidget.twin(v, tv, bmp, t.isNotBlank(), fonts, t) {
                TextArt.render(ctx, t, font, sp, col, sp2, caps, S.shadow, 0, maxW)
            }
        if (large) {
            v.setViewVisibility(R.id.player, View.VISIBLE)
            v.setViewVisibility(R.id.middle, View.VISIBLE)
            v.setViewVisibility(R.id.player_close, View.GONE)
            v.setViewVisibility(R.id.player_eq, View.GONE)
            val src = try {
                val pm = ctx.packageManager
                pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString().uppercase()
            } catch (e: Exception) { "MÚSICA" }
            v.setTextViewText(R.id.player_src, src)
            v.setTextViewText(R.id.player_state, if (useLast) " · Última canción" else "")
            v.setTextViewText(R.id.player_title, title)
            v.setTextViewText(R.id.player_artist, artist)
            v.setViewVisibility(R.id.player_artist, View.VISIBLE)
            val tw = (W - (140 * d).toInt()).coerceAtLeast((60 * d).toInt())  // los botones van en otra fila: el nombre usa casi todo el ancho
            txt(R.id.player_src, R.id.player_src_bmp, src, S.title, 11f, accent, W / 2, sp2 = 0.12f)
            txt(R.id.player_state, R.id.player_state_bmp, if (useLast) " · Última canción" else "", S.body, 11f, th.dim, W / 3)
            txt(R.id.player_title, R.id.player_title_bmp, title, S.semi, 15f, th.head, tw, caps = th.family == "neon")
            txt(R.id.player_artist, R.id.player_artist_bmp, artist, S.body, 12f, th.dim, tw)
            v.setImageViewBitmap(R.id.player_art, artBitmap(ctx, null, th, 56, false))
            v.setImageViewBitmap(R.id.player_prev, iconBitmap(ctx, "prev", th.dim, 24))
            v.setImageViewBitmap(R.id.player_next, iconBitmap(ctx, "next", th.dim, 24))
            v.setImageViewBitmap(R.id.player_play, playBitmap(ctx, false, th, 52))
            v.setContentDescription(R.id.player_play, "Abrir tu música")
            if (launch != null) for (id in intArrayOf(R.id.player_play, R.id.player_info, R.id.player_head, R.id.player_next, R.id.player_prev))
                v.setOnClickPendingIntent(id, launch)
        } else {
            v.setViewVisibility(R.id.player_c, View.VISIBLE)
            v.setViewVisibility(R.id.middle, View.VISIBLE)
            v.setTextViewText(R.id.pc_title, title)
            v.setTextViewText(R.id.pc_artist, artist)
            v.setViewVisibility(R.id.pc_artist, View.VISIBLE)
            val tw = (W - (180 * d).toInt()).coerceAtLeast((60 * d).toInt())
            txt(R.id.pc_title, R.id.pc_title_bmp, title, S.semi, 14f, th.head, tw, caps = th.family == "neon")
            txt(R.id.pc_artist, R.id.pc_artist_bmp, artist, S.body, 12f, th.dim, tw)
            v.setImageViewBitmap(R.id.pc_art, artBitmap(ctx, null, th, 46, false))
            v.setImageViewBitmap(R.id.pc_play, playBitmap(ctx, false, th, 40))
            v.setContentDescription(R.id.pc_play, "Abrir tu música")
            v.setImageViewBitmap(R.id.pc_next, iconBitmap(ctx, "next", iconCol, 22))
            if (launch != null) for (id in intArrayOf(R.id.pc_play, R.id.pc_next, R.id.pc_info)) v.setOnClickPendingIntent(id, launch)
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

    /** Avance de la canción (0..1) o -1 si no se sabe. Lo usan las pieles Vinilo y Casete. */
    @Volatile private var prog = -1f
    private fun progressOf(c: MediaController?): Float {
        val st = c?.playbackState ?: return -1f
        val dur = c.metadata?.getLong(MediaMetadata.METADATA_KEY_DURATION) ?: 0L
        if (dur <= 0) return -1f
        var pos = st.position
        if (st.state == PlaybackState.STATE_PLAYING && st.lastPositionUpdateTime > 0)
            pos += ((android.os.SystemClock.elapsedRealtime() - st.lastPositionUpdateTime) * st.playbackSpeed).toLong()
        return (pos.toFloat() / dur).coerceIn(0f, 1f)
    }

    /** Pieles del reproductor: la carátula como vinilo o casete, con el avance de la canción. */
    private fun skinBitmap(ctx: Context, src: Bitmap, th: Theme, sizeDp: Int, skin: String): Bitmap {
        val accent = th.accent(ctx)
        val s = dp(ctx, sizeDp.toFloat()).toInt()
        val out = Bitmap.createBitmap(s, s, Bitmap.Config.ARGB_8888)
        val c = Canvas(out)
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        val cx = s / 2f; val cy = s / 2f
        if (skin == "vinilo") {
            // Disco negro con surcos, la carátula como etiqueta y el avance como arco
            p.color = 0xFF0E0D0C.toInt(); c.drawCircle(cx, cy, s / 2f - dp(ctx, 2f), p)
            p.style = Paint.Style.STROKE; p.strokeWidth = dp(ctx, 0.6f); p.color = 0x22FFFFFF
            var rr = s / 2f - dp(ctx, 5f); while (rr > s * 0.22f) { c.drawCircle(cx, cy, rr, p); rr -= dp(ctx, 2.2f) }
            p.style = Paint.Style.FILL
            val lr = s * 0.2f
            c.save(); c.clipPath(Path().apply { addCircle(cx, cy, lr, Path.Direction.CW) })
            val sc = maxOf(2 * lr / src.width, 2 * lr / src.height)
            c.drawBitmap(src, null, RectF(cx - src.width * sc / 2, cy - src.height * sc / 2, cx + src.width * sc / 2, cy + src.height * sc / 2), Paint(Paint.FILTER_BITMAP_FLAG))
            c.restore()
            p.color = 0xFF0E0D0C.toInt(); c.drawCircle(cx, cy, dp(ctx, 2f), p)
            if (prog >= 0f) {
                p.style = Paint.Style.STROKE; p.strokeWidth = dp(ctx, 2.2f); p.strokeCap = Paint.Cap.ROUND; p.color = accent
                val inset = dp(ctx, 1.5f)
                c.drawArc(RectF(inset, inset, s - inset, s - inset), -90f, 360f * prog, false, p)
            }
        } else {
            // Casete: carcasa, ventana con dos bobinas (la cinta pasa de una a otra) y la carátula como etiqueta
            val body = RectF(dp(ctx, 1f), s * 0.18f, s - dp(ctx, 1f), s * 0.82f)
            p.color = if (th.light) 0xFF2A2522.toInt() else 0xFF1E1A18.toInt(); c.drawRoundRect(body, dp(ctx, 5f), dp(ctx, 5f), p)
            val label = RectF(body.left + dp(ctx, 4f), body.top + dp(ctx, 4f), body.right - dp(ctx, 4f), body.top + body.height() * 0.42f)
            c.save(); c.clipRect(label)
            val sc = maxOf(label.width() / src.width, label.height() / src.height)
            c.drawBitmap(src, null, RectF(label.centerX() - src.width * sc / 2, label.centerY() - src.height * sc / 2,
                label.centerX() + src.width * sc / 2, label.centerY() + src.height * sc / 2), Paint(Paint.FILTER_BITMAP_FLAG))
            c.restore()
            val win = RectF(body.left + body.width() * 0.2f, label.bottom + dp(ctx, 3f), body.right - body.width() * 0.2f, body.bottom - dp(ctx, 4f))
            p.color = 0xFF0A0908.toInt(); c.drawRoundRect(win, dp(ctx, 3f), dp(ctx, 3f), p)
            val f = if (prog >= 0f) prog else 0.5f
            val maxR = win.height() * 0.46f; val minR = maxR * 0.4f
            val ry = win.centerY()
            p.color = 0xFF5A4A3E.toInt()
            c.drawCircle(win.left + win.width() * 0.25f, ry, minR + (maxR - minR) * (1 - f), p)
            c.drawCircle(win.right - win.width() * 0.25f, ry, minR + (maxR - minR) * f, p)
            p.color = accent
            c.drawCircle(win.left + win.width() * 0.25f, ry, minR * 0.5f, p)
            c.drawCircle(win.right - win.width() * 0.25f, ry, minR * 0.5f, p)
        }
        return out
    }

    private fun artBitmap(ctx: Context, src: Bitmap?, th: Theme, sizeDp: Int, badge: Boolean): Bitmap {
        val skin = Prefs.playerSkin(ctx)
        if (src != null && skin != "normal") return try { skinBitmap(ctx, src, th, sizeDp, skin) } catch (e: Exception) { artBitmap0(ctx, src, th, sizeDp, badge) }
        return artBitmap0(ctx, src, th, sizeDp, badge)
    }

    private fun artBitmap0(ctx: Context, src: Bitmap?, th: Theme, sizeDp: Int, badge: Boolean): Bitmap {
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

    private val audioCb = object : android.media.AudioDeviceCallback() {
        override fun onAudioDevicesAdded(added: Array<out android.media.AudioDeviceInfo>?) { Rules.forget(); Rules.check(this@MediaListener) }
        override fun onAudioDevicesRemoved(removed: Array<out android.media.AudioDeviceInfo>?) { Rules.forget(); Rules.check(this@MediaListener) }
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        try { (getSystemService(AUDIO_SERVICE) as android.media.AudioManager).registerAudioDeviceCallback(audioCb, handler) } catch (e: Exception) { }
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
        try { (getSystemService(AUDIO_SERVICE) as android.media.AudioManager).unregisterAudioDeviceCallback(audioCb) } catch (e: Exception) { }
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
    private fun schedule() {
        RockWidget.requestUpdate(this, 600, playerOnly = true)
        Stage.changed(this)
        TourLog.changed(this)
    }
}
