package com.hossa.redoble

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerParameters
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlin.math.roundToInt

class WeatherWorker(ctx: Context, params: WorkerParameters) : Worker(ctx, params) {

    override fun doWork(): Result {
        val ctx = applicationContext
        val lat = Prefs.lat(ctx)
        val lon = Prefs.lon(ctx)
        if (lat.isNaN() || lon.isNaN()) return Result.success()
        return try {
            val url = String.format(
                Locale.US,
                "https://api.open-meteo.com/v1/forecast?latitude=%.4f&longitude=%.4f" +
                    "&current=temperature_2m,weather_code&daily=weather_code,temperature_2m_max,temperature_2m_min&forecast_days=2&timezone=auto",
                lat, lon
            )
            val json = JSONObject(get(url))
            val current = json.getJSONObject("current")
            val temp = current.getDouble("temperature_2m").roundToInt()
            val now = describe(current.getInt("weather_code"))
            val daily = json.getJSONObject("daily")
            val codes = daily.getJSONArray("weather_code")
            var tomorrow = ""
            if (codes.length() > 1) {
                val mn = daily.getJSONArray("temperature_2m_min").getDouble(1).roundToInt()
                val mx = daily.getJSONArray("temperature_2m_max").getDouble(1).roundToInt()
                tomorrow = "Mañana: ${describe(codes.getInt(1)).lowercase()} · $mn°–$mx°"
            }
            Prefs.setWeather(ctx, "$now, $temp°", tomorrow)
            Prefs.setWeatherExtra(ctx, current.getInt("weather_code"), temp, now)
            RockWidget.updateAll(ctx)
            Result.success()
        } catch (e: Exception) {
            Result.retry()
        }
    }

    companion object {
        private val NET = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()

        fun schedule(ctx: Context) {
            val req = PeriodicWorkRequestBuilder<WeatherWorker>(1, TimeUnit.HOURS)
                .setConstraints(NET).build()
            WorkManager.getInstance(ctx)
                .enqueueUniquePeriodicWork("clima", ExistingPeriodicWorkPolicy.KEEP, req)
        }

        @Volatile private var lastAsk = 0L

        /**
         * Pide el clima ya. REPLACE: si había un reintento esperando (por ejemplo de
         * la ciudad anterior), se reemplaza. Los pedidos automáticos del widget se
         * espacian 2 minutos para no cancelarse entre sí.
         */
        fun runNow(ctx: Context, force: Boolean = false) {
            val now = System.currentTimeMillis()
            if (!force && now - lastAsk < 120_000L) return
            lastAsk = now
            val req = OneTimeWorkRequestBuilder<WeatherWorker>().setConstraints(NET).build()
            WorkManager.getInstance(ctx)
                .enqueueUniqueWork("clima-ahora", androidx.work.ExistingWorkPolicy.REPLACE, req)
        }

        fun get(url: String): String {
            val conn = URL(url).openConnection() as HttpURLConnection
            conn.connectTimeout = 10_000
            conn.readTimeout = 10_000
            try {
                return conn.inputStream.bufferedReader().use { it.readText() }
            } finally {
                conn.disconnect()
            }
        }

        /** Busca la ciudad y devuelve (nombre, lat, lon) o null. Llamar fuera del hilo principal. */
        fun geocode(name: String): Triple<String, Double, Double>? {
            val q = URLEncoder.encode(name.trim(), "UTF-8")
            val json = JSONObject(
                get("https://geocoding-api.open-meteo.com/v1/search?name=$q&count=1&language=es")
            )
            val results = json.optJSONArray("results") ?: return null
            if (results.length() == 0) return null
            val r = results.getJSONObject(0)
            val label = listOfNotNull(r.optString("name").ifBlank { null }, r.optString("country").ifBlank { null })
                .joinToString(", ")
            return Triple(label, r.getDouble("latitude"), r.getDouble("longitude"))
        }

        fun describe(code: Int): String = when (code) {
            0 -> "Despejado"
            1, 2 -> "Algo nublado"
            3 -> "Nublado"
            45, 48 -> "Niebla"
            in 51..57 -> "Llovizna"
            in 61..67 -> "Lluvia"
            in 71..77 -> "Nieve"
            in 80..82 -> "Chubascos"
            in 85..86 -> "Nieve"
            in 95..99 -> "Tormenta"
            else -> "Variable"
        }
    }
}
