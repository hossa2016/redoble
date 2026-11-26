package com.hossa.redoble

import android.Manifest
import android.app.AlarmManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.CalendarContract
import java.text.SimpleDateFormat
import java.util.Locale

/** Datos extra de la cabecera: próxima alarma y próximo evento de la agenda. */
object Extras {

    /** "Alarma 6:30" (o "Alarma mañana 6:30"); vacío si no hay alarma en las próximas 24 h. */
    fun nextAlarm(ctx: Context): String {
        val am = ctx.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return ""
        val t = am.nextAlarmClock?.triggerTime ?: return ""
        val now = System.currentTimeMillis()
        if (t - now > 24 * 3_600_000L || t < now) return ""
        return I18n.tr(ctx, "Alarma") + " " + time(ctx, t)
    }

    fun hasCalendar(ctx: Context) =
        ctx.checkSelfPermission(Manifest.permission.READ_CALENDAR) == PackageManager.PERMISSION_GRANTED

    /** Próximo evento de hoy o mañana: "Reunión 3:00 p. m."; vacío si no hay permiso o eventos. */
    /** Minutos hasta el próximo evento que no es de todo el día (-1 si no hay en 36 h). */
    fun minutesToNextEvent(ctx: Context): Int {
        if (!hasCalendar(ctx)) return -1
        return try {
            val now = System.currentTimeMillis()
            val uri = CalendarContract.Instances.CONTENT_URI.buildUpon().let {
                android.content.ContentUris.appendId(it, now); android.content.ContentUris.appendId(it, now + 36 * 3_600_000L); it.build() }
            ctx.contentResolver.query(uri, arrayOf(CalendarContract.Instances.BEGIN, CalendarContract.Instances.ALL_DAY),
                "${CalendarContract.Instances.BEGIN} >= ?", arrayOf(now.toString()), "${CalendarContract.Instances.BEGIN} ASC")?.use { c ->
                while (c.moveToNext()) if (c.getInt(1) == 0) return ((c.getLong(0) - now) / 60_000L).toInt()
                -1
            } ?: -1
        } catch (e: Exception) { -1 }
    }

    /** Títulos de los eventos de hoy (para la regla «Concierto hoy»). */
    fun todayTitles(ctx: Context): List<String> {
        if (!hasCalendar(ctx)) return emptyList()
        val cal = java.util.Calendar.getInstance().apply { set(java.util.Calendar.HOUR_OF_DAY, 0); set(java.util.Calendar.MINUTE, 0) }
        val from = cal.timeInMillis; val to = from + 24 * 3_600_000L
        val uri = CalendarContract.Instances.CONTENT_URI.buildUpon().let {
            android.content.ContentUris.appendId(it, from); android.content.ContentUris.appendId(it, to); it.build()
        }
        val out = mutableListOf<String>()
        ctx.contentResolver.query(uri, arrayOf(CalendarContract.Instances.TITLE), null, null, null)?.use { c ->
            while (c.moveToNext()) c.getString(0)?.let { out += it }
        }
        return out
    }

    fun nextEvent(ctx: Context): String {
        if (!hasCalendar(ctx)) return ""
        return try {
            val now = System.currentTimeMillis()
            val uri = CalendarContract.Instances.CONTENT_URI.buildUpon().let {
                android.content.ContentUris.appendId(it, now); android.content.ContentUris.appendId(it, now + 36 * 3_600_000L); it.build()
            }
            ctx.contentResolver.query(uri, arrayOf(CalendarContract.Instances.TITLE, CalendarContract.Instances.BEGIN,
                CalendarContract.Instances.ALL_DAY), "${CalendarContract.Instances.END} >= ?", arrayOf(now.toString()),
                "${CalendarContract.Instances.BEGIN} ASC")?.use { c ->
                while (c.moveToNext()) {
                    val title = c.getString(0)?.trim().orEmpty()
                    if (title.isEmpty()) continue
                    val begin = c.getLong(1); val allDay = c.getInt(2) == 1
                    val tomorrow = !sameDay(begin, now)
                    val whenTxt = when {
                        allDay && tomorrow -> I18n.tr(ctx, "mañana")
                        allDay -> I18n.tr(ctx, "hoy")
                        tomorrow -> I18n.tr(ctx, "mañana") + " " + time(ctx, begin)
                        else -> time(ctx, begin)
                    }
                    return "${title.take(22)} · $whenTxt"
                }
                ""
            } ?: ""
        } catch (e: Exception) { "" }
    }

    private fun sameDay(a: Long, b: Long): Boolean {
        val f = SimpleDateFormat("yyyyMMdd", Locale.US); return f.format(java.util.Date(a)) == f.format(java.util.Date(b))
    }

    private fun time(ctx: Context, t: Long): String {
        val h24 = android.text.format.DateFormat.is24HourFormat(ctx)
        return SimpleDateFormat(if (h24) "H:mm" else "h:mm a", I18n.locale(ctx)).format(java.util.Date(t)).replace(".", "")
    }

    /** Línea de fecha estilo «Cartel de Gira»: "MAR 24 NOV · ENVIGADO". */
    fun posterLine(ctx: Context): String {
        val loc = I18n.locale(ctx)
        val d = SimpleDateFormat("EEE d MMM", loc).format(java.util.Date()).replace(".", "").uppercase(loc)
        val city = Prefs.city(ctx).substringBefore(",").trim().uppercase(loc)
        return if (city.isBlank()) d else "$d · $city"
    }
}

/** La alarma del sistema cambió: se redibuja la cabecera. */
class AlarmChangeReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) { RockWidget.updateAll(ctx) }
}
