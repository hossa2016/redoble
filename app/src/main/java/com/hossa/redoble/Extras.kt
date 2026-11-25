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
    private val ES = Locale("es", "CO")

    /** "Alarma 6:30" (o "Alarma mañana 6:30"); vacío si no hay alarma en las próximas 24 h. */
    fun nextAlarm(ctx: Context): String {
        val am = ctx.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return ""
        val t = am.nextAlarmClock?.triggerTime ?: return ""
        val now = System.currentTimeMillis()
        if (t - now > 24 * 3_600_000L || t < now) return ""
        return "Alarma " + time(ctx, t)
    }

    fun hasCalendar(ctx: Context) =
        ctx.checkSelfPermission(Manifest.permission.READ_CALENDAR) == PackageManager.PERMISSION_GRANTED

    /** Próximo evento de hoy o mañana: "Reunión 3:00 p. m."; vacío si no hay permiso o eventos. */
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
                        allDay && tomorrow -> "mañana"
                        allDay -> "hoy"
                        tomorrow -> "mañana " + time(ctx, begin)
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
        return SimpleDateFormat(if (h24) "H:mm" else "h:mm a", ES).format(java.util.Date(t)).replace(".", "")
    }

    /** Línea de fecha estilo «Cartel de Gira»: "MAR 24 NOV · ENVIGADO". */
    fun posterLine(ctx: Context): String {
        val d = SimpleDateFormat("EEE d MMM", ES).format(java.util.Date()).replace(".", "").uppercase(ES)
        val city = Prefs.city(ctx).substringBefore(",").trim().uppercase(ES)
        return if (city.isBlank()) d else "$d · $city"
    }
}

/** La alarma del sistema cambió: se redibuja la cabecera. */
class AlarmChangeReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) { RockWidget.updateAll(ctx) }
}
