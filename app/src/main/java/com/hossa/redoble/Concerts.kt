package com.hossa.redoble

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.view.View
import android.widget.LinearLayout
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * Conciertos cerca: tus artistas más escuchados y dónde buscar sus fechas en tu ciudad.
 * Con una clave de Ticketmaster (secreto TICKETMASTER_KEY al compilar) también muestra
 * fechas reales en los países donde Ticketmaster vende (EE. UU., México, Europa, etc.).
 * Sin clave, cada artista abre una búsqueda web de sus conciertos en tu ciudad.
 */
object Concerts {
    class Show(val name: String, val date: String, val place: String, val url: String)

    val hasKey get() = BuildConfig.TM_KEY.isNotBlank()

    /** Hasta 6 artistas: el que suena, los del año y los de la semana, sin repetir. */
    fun artists(ctx: Context): List<String> {
        val out = LinkedHashSet<String>()
        Player.nowTrack(ctx)?.second?.let { clean(it)?.let(out::add) }
        if (Prefs.tourLog(ctx)) (TourLog.topArtists(ctx, true) + TourLog.topArtists(ctx, false)).forEach { a -> clean(a)?.let(out::add) }
        return out.take(6)
    }

    private fun clean(a: String): String? = a.substringBefore(" - Topic").substringBefore(",").substringBefore(" feat")
        .replace("\"", "").trim().takeIf { it.length in 2..60 }

    fun searchUri(ctx: Context, artist: String): Uri {
        val city = Prefs.city(ctx).substringBefore(",").trim()
        val q = listOf(artist, I18n.tr(ctx, "concierto"), city).filter { it.isNotBlank() }.joinToString(" ")
        return Uri.parse("https://www.google.com/search?q=" + URLEncoder.encode(q, "UTF-8"))
    }

    /** Fechas reales cerca (radio 300 km) con Ticketmaster; vacío si no hay clave, ciudad o red. */
    fun nearby(ctx: Context, artist: String): List<Show> {
        if (!hasKey) return emptyList()
        val lat = Prefs.lat(ctx); val lon = Prefs.lon(ctx)
        if (lat.isNaN() || lon.isNaN()) return emptyList()
        return try {
            val u = "https://app.ticketmaster.com/discovery/v2/events.json?classificationName=music&size=3&sort=date,asc" +
                "&radius=300&unit=km&latlong=$lat,$lon&keyword=" + URLEncoder.encode(artist, "UTF-8") + "&apikey=" + BuildConfig.TM_KEY
            val c = (URL(u).openConnection() as HttpURLConnection).apply { connectTimeout = 10_000; readTimeout = 15_000 }
            if (c.responseCode != 200) { c.disconnect(); return emptyList() }
            val o = JSONObject(c.inputStream.bufferedReader().use { it.readText() }); c.disconnect()
            val evs = o.optJSONObject("_embedded")?.optJSONArray("events") ?: return emptyList()
            (0 until evs.length()).map { i ->
                val e = evs.getJSONObject(i)
                val venue = e.optJSONObject("_embedded")?.optJSONArray("venues")?.optJSONObject(0)
                Show(e.optString("name"), e.optJSONObject("dates")?.optJSONObject("start")?.optString("localDate").orEmpty(),
                    listOfNotNull(venue?.optString("name"), venue?.optJSONObject("city")?.optString("name")).filter { it.isNotBlank() }.joinToString(", "),
                    e.optString("url"))
            }
        } catch (e: Exception) { emptyList() }
    }
}

/** Tarjeta «Conciertos cerca» en la pestaña Música. */
internal fun MainActivity.concertsPlate(): View {
    val card = ui.plateCard()
    card.addView(ui.sectionTitle("Conciertos cerca"))
    val city = Prefs.city(this).substringBefore(",").trim()
    val arts = Concerts.artists(this)
    card.addView(ui.note(when {
        arts.isEmpty() -> "Activa el historial de escucha (arriba) o pon música: aquí salen tus artistas."
        city.isBlank() -> "Escribe tu ciudad en Clima para buscar cerca de ti."
        else -> I18n.tr(this, "Tus artistas más escuchados. Toca uno para ver sus fechas en {}.").replace("{}", city)
    }, sp = 14f), LinearLayout.LayoutParams(-1, -2).apply { topMargin = ui.dp(4) })
    arts.forEach { a ->
        val row = ui.actionRow(a, R.drawable.ic_ticket, I18n.tr(this, "Buscar fechas"))
        row.contentDescription = I18n.tr(this, "Buscar conciertos de {}").replace("{}", a)
        row.setOnClickListener { try { startActivity(Intent(Intent.ACTION_VIEW, Concerts.searchUri(this, a))) } catch (e: Exception) { } }
        card.addView(row, LinearLayout.LayoutParams(-1, -2).apply { topMargin = ui.dp(6) })
        if (Concerts.hasKey) {
            val holder = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
            card.addView(holder, LinearLayout.LayoutParams(-1, -2))
            io.execute {
                val shows = Concerts.nearby(this, a)
                onUi { shows.forEach { s ->
                    holder.addView(ui.actionRow("${s.date} · ${s.place}", R.drawable.ic_ticket, s.name).apply {
                        setOnClickListener { try { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(s.url))) } catch (e: Exception) { } }
                    }, LinearLayout.LayoutParams(-1, -2).apply { topMargin = ui.dp(4); marginStart = ui.dp(16) })
                } }
            }
        }
    }
    return card
}
