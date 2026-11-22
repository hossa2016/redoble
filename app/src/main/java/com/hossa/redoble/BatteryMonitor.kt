package com.hossa.redoble

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
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
 * - Con el servicio en vivo activo: al instante (escucha los cambios de batería).
 * - Sin él: cada 15 minutos (WorkManager) y al conectar el cargador.
 * Sin alarmas exactas: Google Play no las permite para este tipo de app.
 */
object BatteryMonitor {
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

    /** Programa la revisión periódica y, si no está cargando, el aviso de cargador. */
    fun check(ctx: Context) {
        schedulePeriodic(ctx)
        if (!isCharging(ctx)) watchCharger(ctx)
    }

    /** Actualiza solo el medidor y el texto de batería en todos los widgets. */
    fun refresh(ctx: Context) {
        val mgr = AppWidgetManager.getInstance(ctx)
        val ids = mgr.getAppWidgetIds(ComponentName(ctx, RockWidget::class.java))
        if (ids.isEmpty()) return
        val v = RemoteViews(ctx.packageName, Themes.widget(ctx).layout)
        RockWidget.applyBattery(ctx, v)
        for (id in ids) mgr.partiallyUpdateAppWidget(id, v)
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

/** Cada 15 minutos, con o sin cargador: batería al día e intento de reanudar el servicio. */
open class BatteryWorker(ctx: Context, params: WorkerParameters) : Worker(ctx, params) {
    override fun doWork(): Result {
        try {
            BatteryMonitor.refresh(applicationContext)
            BatteryMonitor.check(applicationContext)
        } catch (e: Exception) { }
        LiveService.start(applicationContext)
        return Result.success()
    }
}

/** Se ejecuta al conectar el cargador (mismo trabajo; se conserva el nombre por los trabajos ya programados). */
class ChargerWorker(ctx: Context, params: WorkerParameters) : BatteryWorker(ctx, params)
