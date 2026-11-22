package com.hossa.redoble

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.os.BatteryManager
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat

/**
 * Servicio ligero que escucha la batería en tiempo real para que el widget
 * muestre al instante cuando se conecta o desconecta el cargador.
 */
class LiveService : Service() {

    private var lastLevel = -1
    private var lastPlugged = -1

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context, intent: Intent) {
            when (intent.action) {
                Intent.ACTION_BATTERY_CHANGED -> {
                    val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
                    val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, 100).coerceAtLeast(1)
                    val pct = if (level >= 0) level * 100 / scale else -1
                    val plugged = if (intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) != 0) 1 else 0
                    if (pct != lastLevel || plugged != lastPlugged) {
                        lastLevel = pct
                        lastPlugged = plugged
                        BatteryMonitor.refresh(ctx)
                    }
                }
                Intent.ACTION_TIME_TICK -> RockWidget.updateClock(ctx)
                Intent.ACTION_TIME_CHANGED, Intent.ACTION_TIMEZONE_CHANGED -> RockWidget.updateAll(ctx)
                Intent.ACTION_SCREEN_ON, Intent.ACTION_USER_PRESENT -> {
                    BatteryMonitor.refresh(ctx)
                    RockWidget.onScreenOn(ctx)
                }
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        val type = if (Build.VERSION.SDK_INT >= 34) ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE else 0
        try {
            ServiceCompat.startForeground(this, NOTIF_ID, notification(), type)
        } catch (e: Exception) {
            stopSelf()
            return
        }
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_BATTERY_CHANGED)
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_USER_PRESENT)
            addAction(Intent.ACTION_TIME_TICK)
            addAction(Intent.ACTION_TIME_CHANGED)
            addAction(Intent.ACTION_TIMEZONE_CHANGED)
        }
        ContextCompat.registerReceiver(this, receiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
        running = true
        // Ya hay quien avance la hora: pasar al reloj con la letra del tema
        RockWidget.updateAll(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY

    override fun onDestroy() {
        try { unregisterReceiver(receiver) } catch (e: Exception) { }
        running = false
        // Sin servicio, volver al reloj del sistema (que avanza solo)
        try { RockWidget.updateAll(applicationContext) } catch (e: Exception) { }
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun notification(): Notification {
        val nm = getSystemService(NotificationManager::class.java)
        if (nm.getNotificationChannel(CHANNEL) == null) {
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL, "Widget en vivo", NotificationManager.IMPORTANCE_MIN).apply {
                    description = "Mantiene la batería del widget al día"
                    setShowBadge(false)
                }
            )
        }
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        return NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat)
            .setContentTitle("Prisma")
            .setContentText("Widget en vivo")
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setOngoing(true)
            .setSilent(true)
            .setShowWhen(false)
            .setContentIntent(open)
            .build()
    }

    companion object {
        private const val CHANNEL = "en_vivo"
        private const val NOTIF_ID = 42

        /** Si el servicio está vivo en este proceso. */
        @Volatile var running = false
            private set

        /** Arranca el servicio si se puede (desde segundo plano Android puede negarlo). */
        fun start(ctx: Context) {
            try {
                ContextCompat.startForegroundService(ctx, Intent(ctx, LiveService::class.java))
            } catch (e: Exception) { }
        }
    }
}

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        LiveService.start(ctx)
        RockWidget.updateAll(ctx)
        BatteryMonitor.check(ctx)
    }
}
