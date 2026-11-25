package com.hossa.redoble

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import androidx.core.content.FileProvider
import androidx.core.content.res.ResourcesCompat
import org.json.JSONObject
import java.io.File
import java.util.Calendar

/**
 * «Bitácora de Gira»: historial de escucha guardado solo en el teléfono.
 * Suma minutos por semana y por año, y cuenta canciones y bandas.
 */
object TourLog {
    private const val FILE = "bitacora"
    private var curKey = ""; private var curArtist = ""; private var since = 0L

    private fun p(ctx: Context) = ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE)
    fun enabled(ctx: Context) = Prefs.tourLog(ctx)

    private fun weekKey(c: Calendar = Calendar.getInstance()) = "w" + c.get(Calendar.YEAR) + "_" + c.get(Calendar.WEEK_OF_YEAR)
    private fun yearKey(c: Calendar = Calendar.getInstance()) = "y" + c.get(Calendar.YEAR)

    /** Lo llama el lector de música: cierra el tramo anterior y abre uno nuevo si suena algo. */
    @Synchronized fun changed(ctx: Context) {
        if (!enabled(ctx)) return
        val now = System.currentTimeMillis()
        val track = try { Player.nowTrack(ctx) } catch (e: Exception) { null }
        val playing = try { Player.nowState(ctx).first } catch (e: Exception) { false }
        val key = if (playing && track != null) track.first + "|" + track.second else ""
        if (key == curKey) return
        // Se cierra lo que sonaba (máximo 15 min por tramo, por si quedó colgado)
        if (curKey.isNotEmpty() && since > 0) {
            val min = ((now - since) / 60_000f).coerceIn(0f, 15f)
            if (min >= 0.5f) add(ctx, curKey, curArtist, min)
        }
        curKey = key; curArtist = track?.second ?: ""; since = if (key.isEmpty()) 0L else now
    }

    private fun add(ctx: Context, key: String, artist: String, min: Float) {
        val ed = p(ctx).edit()
        for (k in listOf(weekKey(), yearKey())) {
            val o = try { JSONObject(p(ctx).getString(k, "{}") ?: "{}") } catch (e: Exception) { JSONObject() }
            o.put("min", o.optDouble("min", 0.0) + min)
            val tracks = o.optJSONObject("tracks") ?: JSONObject()
            tracks.put(key, tracks.optInt(key, 0) + 1)
            // Solo las 60 canciones más tocadas, para que no crezca sin límite
            if (tracks.length() > 80) {
                val keep = tracks.keys().asSequence().sortedByDescending { tracks.optInt(it) }.take(60).toSet()
                tracks.keys().asSequence().toList().filter { it !in keep }.forEach { tracks.remove(it) }
            }
            o.put("tracks", tracks)
            if (artist.isNotBlank()) {
                val arts = o.optJSONObject("artists") ?: JSONObject()
                arts.put(artist, arts.optDouble(artist, 0.0) + min); o.put("artists", arts)
            }
            ed.putString(k, o.toString())
        }
        ed.apply()
    }

    class Summary(val minutes: Int, val topArtist: String, val topTrack: String, val topTracks: List<String>)

    private fun summary(ctx: Context, key: String): Summary {
        val o = try { JSONObject(p(ctx).getString(key, "{}") ?: "{}") } catch (e: Exception) { JSONObject() }
        val tracks = o.optJSONObject("tracks") ?: JSONObject()
        val top = tracks.keys().asSequence().sortedByDescending { tracks.optInt(it) }.take(5)
            .map { it.substringBefore("|") + it.substringAfter("|", "").let { a -> if (a.isBlank()) "" else " · $a" } }.toList()
        val arts = o.optJSONObject("artists") ?: JSONObject()
        val topArtist = arts.keys().asSequence().maxByOrNull { arts.optDouble(it) } ?: ""
        return Summary(o.optDouble("min", 0.0).toInt(), topArtist, top.firstOrNull() ?: "", top)
    }

    fun week(ctx: Context) = summary(ctx, weekKey())
    fun year(ctx: Context) = summary(ctx, yearKey())
    fun weekNumber() = Calendar.getInstance().get(Calendar.WEEK_OF_YEAR)
    fun yearNumber() = Calendar.getInstance().get(Calendar.YEAR)
    fun clear(ctx: Context) { p(ctx).edit().clear().apply() }
}

/** Imágenes para compartir: entrada de concierto y afiches de la bitácora. */
object ShareArt {
    private fun font(ctx: Context, id: Int) = try { ResourcesCompat.getFont(ctx, id) } catch (e: Exception) { null } ?: Typeface.DEFAULT_BOLD

    private fun fit(p: Paint, text: String, maxW: Float, size: Float): Float {
        p.textSize = size
        while (p.measureText(text) > maxW && p.textSize > 18f) p.textSize -= 2f
        return p.textSize
    }

    /** Entrada de concierto con la canción que suena. */
    fun ticket(ctx: Context, title: String, artist: String, art: Bitmap?, accent: Int): Bitmap {
        val w = 1080; val h = 1920
        val b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888); val c = Canvas(b)
        c.drawColor(Color.rgb(16, 13, 12))
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        // Entrada: tarjeta con muescas
        val card = RectF(90f, 260f, w - 90f, h - 260f)
        p.color = Color.rgb(242, 235, 226); c.drawRoundRect(card, 36f, 36f, p)
        p.color = Color.rgb(16, 13, 12)
        c.drawCircle(card.left, 1240f, 46f, p); c.drawCircle(card.right, 1240f, 46f, p)
        // Carátula
        val artR = RectF(card.left + 70f, card.top + 70f, card.right - 70f, card.top + 70f + (card.width() - 140f))
        if (art != null) {
            val path = android.graphics.Path().apply { addRoundRect(artR, 24f, 24f, android.graphics.Path.Direction.CW) }
            c.save(); c.clipPath(path)
            val s = maxOf(artR.width() / art.width, artR.height() / art.height)
            val dw = art.width * s; val dh = art.height * s
            c.drawBitmap(art, null, RectF(artR.centerX() - dw / 2, artR.centerY() - dh / 2, artR.centerX() + dw / 2, artR.centerY() + dh / 2), Paint(Paint.FILTER_BITMAP_FLAG))
            c.restore()
        } else {
            p.shader = LinearGradient(artR.left, artR.top, artR.right, artR.bottom, accent, Color.rgb(30, 24, 22), Shader.TileMode.CLAMP)
            c.drawRoundRect(artR, 24f, 24f, p); p.shader = null
        }
        val text = Paint(Paint.ANTI_ALIAS_FLAG)
        text.typeface = font(ctx, R.font.anton_regular); text.color = Color.rgb(28, 22, 20)
        val maxW = card.width() - 140f
        val t = title.uppercase()
        fit(text, t, maxW, 92f); c.drawText(t, card.left + 70f, artR.bottom + 130f, text)
        text.typeface = font(ctx, R.font.barlow_condensed_medium); text.color = accent
        fit(text, artist.uppercase(), maxW, 60f); c.drawText(artist.uppercase(), card.left + 70f, artR.bottom + 210f, text)
        // Línea punteada (corte de la entrada)
        p.color = Color.rgb(190, 180, 170); p.strokeWidth = 5f; p.pathEffect = android.graphics.DashPathEffect(floatArrayOf(18f, 14f), 0f)
        c.drawLine(card.left + 70f, 1240f, card.right - 70f, 1240f, p); p.pathEffect = null
        val es = java.util.Locale("es", "CO")
        val date = java.text.SimpleDateFormat("EEE d MMM yyyy", es).format(java.util.Date()).replace(".", "").uppercase(es)
        val city = Prefs.city(ctx).substringBefore(",").trim().uppercase(es).ifBlank { "EN VIVO" }
        text.typeface = font(ctx, R.font.barlow_condensed_semibold); text.color = Color.rgb(60, 52, 48)
        text.textSize = 46f
        c.drawText(date, card.left + 70f, 1340f, text)
        c.drawText(city, card.left + 70f, 1405f, text)
        c.drawText("FILA A · ASIENTO 11", card.left + 70f, 1470f, text)
        text.color = accent; text.textSize = 40f
        c.drawText("ADMITE UNO · SOLD OUT", card.left + 70f, 1560f, text)
        text.typeface = font(ctx, R.font.barlow_condensed_light); text.color = Color.rgb(150, 140, 132); text.textSize = 34f
        c.drawText("Prisma Widgets", card.left + 70f, card.bottom - 50f, text)
        return b
    }

    /** Afiche de la bitácora: semana o año. */
    fun poster(ctx: Context, head: String, sub: String, s: TourLog.Summary, accent: Int): Bitmap {
        val w = 1080; val h = 1920
        val b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888); val c = Canvas(b)
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        p.shader = LinearGradient(0f, 0f, 0f, h.toFloat(), Color.rgb(22, 17, 15), Color.rgb(8, 7, 7), Shader.TileMode.CLAMP)
        c.drawRect(0f, 0f, w.toFloat(), h.toFloat(), p); p.shader = null
        p.color = accent; c.drawRect(90f, 200f, 110f, 560f, p)
        val t = Paint(Paint.ANTI_ALIAS_FLAG); t.color = Color.rgb(243, 237, 231)
        t.typeface = font(ctx, R.font.anton_regular)
        fit(t, head, w - 260f, 150f); c.drawText(head, 150f, 340f, t)
        t.typeface = font(ctx, R.font.barlow_condensed_medium); t.color = accent; t.textSize = 60f
        c.drawText(sub, 150f, 430f, t)
        t.color = Color.rgb(200, 190, 182); t.textSize = 54f
        c.drawText("${s.minutes} MINUTOS EN ESCENA", 150f, 530f, t)
        t.typeface = font(ctx, R.font.barlow_condensed_semibold); t.color = Color.rgb(160, 150, 142); t.textSize = 44f
        c.drawText("CABEZA DE CARTEL", 150f, 720f, t)
        t.typeface = font(ctx, R.font.anton_regular); t.color = Color.rgb(243, 237, 231)
        val headliner = s.topArtist.uppercase().ifBlank { "—" }
        fit(t, headliner, w - 240f, 120f); c.drawText(headliner, 150f, 850f, t)
        t.typeface = font(ctx, R.font.barlow_condensed_semibold); t.color = Color.rgb(160, 150, 142); t.textSize = 44f
        c.drawText("SETLIST", 150f, 1020f, t)
        t.typeface = font(ctx, R.font.barlow_condensed_medium); t.color = Color.rgb(230, 222, 214)
        s.topTracks.forEachIndexed { i, tr ->
            val line = "${i + 1}. $tr"
            fit(t, line, w - 260f, 56f); c.drawText(line, 150f, 1110f + i * 90f, t)
        }
        t.typeface = font(ctx, R.font.barlow_condensed_light); t.color = Color.rgb(130, 120, 112); t.textSize = 36f
        c.drawText("Prisma Widgets · Bitácora de Gira", 150f, h - 120f, t)
        return b
    }

    /** Guarda la imagen y abre «Compartir». */
    fun share(ctx: Context, bmp: Bitmap, name: String, chooser: String) {
        val dir = File(ctx.cacheDir, "compartir").apply { mkdirs() }
        val f = File(dir, "$name.png")
        f.outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        val uri = FileProvider.getUriForFile(ctx, ctx.packageName + ".files", f)
        ctx.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
            type = "image/png"; putExtra(Intent.EXTRA_STREAM, uri); addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }, chooser))
    }
}

/** «Bajar el Telón»: temporizador que pausa la música. */
object SleepTimer {
    private fun pi(ctx: Context) = PendingIntent.getBroadcast(ctx, 77, Intent(ctx, SleepReceiver::class.java),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)

    fun start(ctx: Context, minutes: Int) {
        val am = ctx.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val at = System.currentTimeMillis() + minutes * 60_000L
        // Alarma no exacta (no pide permisos especiales); puede variar un par de minutos
        am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi(ctx))
        Prefs.setSleepAt(ctx, at)
    }

    fun cancel(ctx: Context) {
        (ctx.getSystemService(Context.ALARM_SERVICE) as AlarmManager).cancel(pi(ctx))
        Prefs.setSleepAt(ctx, 0L)
    }

    /** Minutos que faltan (0 si no hay temporizador). */
    fun left(ctx: Context): Int {
        val at = Prefs.sleepAt(ctx); if (at <= 0) return 0
        val m = ((at - System.currentTimeMillis()) / 60_000L).toInt() + 1
        return if (m > 0) m else 0
    }
}

class SleepReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        Player.pause(ctx)
        Prefs.setSleepAt(ctx, 0L)
        RockWidget.updateAll(ctx)
    }
}
