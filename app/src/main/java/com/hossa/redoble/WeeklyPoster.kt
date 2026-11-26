package com.hossa.redoble

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.app.NotificationCompat
import java.util.Calendar

/** Cada domingo desde las 6 p. m.: aviso de que el afiche de la semana está listo para compartir. */
object WeeklyPoster {
    private const val CHANNEL = "afiche"

    fun maybeNotify(ctx: Context) {
        if (!Prefs.tourLog(ctx) || !Prefs.weeklyPoster(ctx)) return
        val c = Calendar.getInstance()
        if (c.get(Calendar.DAY_OF_WEEK) != Calendar.SUNDAY || c.get(Calendar.HOUR_OF_DAY) < 18) return
        val key = "${c.get(Calendar.YEAR)}-${TourLog.weekNumber()}"
        if (Prefs.posterSent(ctx) == key) return
        val wk = TourLog.week(ctx)
        if (wk.minutes < 10) return
        if (android.os.Build.VERSION.SDK_INT >= 33 &&
            ctx.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
        val nm = ctx.getSystemService(NotificationManager::class.java)
        if (nm.getNotificationChannel(CHANNEL) == null)
            nm.createNotificationChannel(NotificationChannel(CHANNEL, I18n.tr(ctx, "Afiche de la semana"), NotificationManager.IMPORTANCE_LOW))
        val open = PendingIntent.getActivity(ctx, 340, Intent(ctx, MainActivity::class.java).putExtra("poster", true)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        nm.notify(340, NotificationCompat.Builder(ctx, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat)
            .setContentTitle(I18n.tr(ctx, "Tu afiche de la semana está listo"))
            .setContentText(I18n.tr(ctx, "{} min de música. Toca para verlo y compartirlo.").replace("{}", wk.minutes.toString()))
            .setAutoCancel(true).setContentIntent(open).build())
        Prefs.setPosterSent(ctx, key)
    }
}
