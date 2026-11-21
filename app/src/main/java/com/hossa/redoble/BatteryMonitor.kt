package com.hossa.redoble

import android.app.AlarmManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Build
import android.os.SystemClock
import android.widget.RemoteViews
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerParameters
import java.util.concurrent.TimeUnit

/**
 * Batería del widget:
 * - Cargando: se actualiza cada minuto (alarma exacta).
 * - Sin cargador: cada 15 minutos (WorkManager) y al instante al desconectar.
 * - Al conectar el cargador lo detecta un trabajo con restricción "cargando".
 */
object BatteryMonitor {
    const val ACTION_TICK = "com.hossa.redoble.BATTERY_TICK"
    private const val TICK_MS = 60_000L

    /** Conectado a corriente (más fiable en Xiaomi que BatteryManager.isCharging). */
    fun isCharging(ctx: Context): Boolean {
        try {
            val i = ctx.applicationContext.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            if (i != null) {
                val plugged = i.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0)
                val status = i.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
                if (plugged != 0 || status == BatteryManager.BATTERY_STATUS_CHARGING) return true
            }
        } catch (e: Exception) { }
        val bm = ctx.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
        return bm.isCharging
    }

    fun level(ctx: Context): Int {
        val bm = ctx.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
        return bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY).coerceIn(0, 100)
    }

    /** Decide qué vigilar según si está cargando o no. */
    fun check(ctx: Context) {
        schedulePeriodic(ctx)
        if (isCharging(ctx)) {
            scheduleTick(ctx)
        } else {
            cancelTick(ctx)
            watchCharger(ctx)
        }
    }

    /** Actualiza solo el medidor y el texto de batería en todos los widgets. */
    fun refresh(ctx: Context) {
        val mgr = AppWidgetManager.getInstance(ctx)
        val ids = mgr.getAppWidgetIds(ComponentName(ctx, RockWidget::class.java))
        if (ids.isEmpty()) return
        val v = RemoteViews(ctx.packageName, R.layout.widget_rock)
        RockWidget.applyBattery(ctx, v)
        for (id in ids) mgr.partiallyUpdateAppWidget(id, v)
    }

    private fun tickIntent(ctx: Context) = PendingIntent.getBroadcast(
        ctx, 7, Intent(ctx, RockWidget::class.java).setAction(ACTION_TICK),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
    )

    private fun scheduleTick(ctx: Context) {
        val am = ctx.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val at = SystemClock.elapsedRealtime() + TICK_MS
        val pi = tickIntent(ctx)
        val exactOk = Build.VERSION.SDK_INT < 31 || am.canScheduleExactAlarms()
        try {
            if (exactOk) am.setExactAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME, at, pi)
            else am.setAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME, at, pi)
        } catch (e: SecurityException) {
            am.setAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME, at, pi)
        }
    }

    private fun cancelTick(ctx: Context) {
        val am = ctx.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        am.cancel(tickIntent(ctx))
    }

    private fun watchCharger(ctx: Context) {
        val req = OneTimeWorkRequestBuilder<ChargerWorker>()
            .setConstraints(Constraints.Builder().setRequiresCharging(true).build())
            .build()
        WorkManager.getInstance(ctx).enqueueUniqueWork("cargador", ExistingWorkPolicy.KEEP, req)
    }

    private fun schedulePeriodic(ctx: Context) {
        val req = PeriodicWorkRequestBuilder<BatteryWorker>(15, TimeUnit.MINUTES).build()
        WorkManager.getInstance(ctx).enqueueUniquePeriodicWork("bateria", ExistingPeriodicWorkPolicy.KEEP, req)
    }
}

/** Se ejecuta cuando se conecta el cargador. */
class ChargerWorker(ctx: Context, params: WorkerParameters) : Worker(ctx, params) {
    override fun doWork(): Result {
        BatteryMonitor.refresh(applicationContext)
        BatteryMonitor.check(applicationContext)
        LiveService.start(applicationContext)
        return Result.success()
    }
}

/** Cada 15 minutos, con o sin cargador. */
class BatteryWorker(ctx: Context, params: WorkerParameters) : Worker(ctx, params) {
    override fun doWork(): Result {
        BatteryMonitor.refresh(applicationContext)
        BatteryMonitor.check(applicationContext)
        LiveService.start(applicationContext)
        return Result.success()
    }
}
