package com.hossa.redoble

import android.content.Context
import android.content.SharedPreferences
import android.graphics.Color

object Prefs {
    const val SLOTS = 6

    val ACCENTS = intArrayOf(
        Color.parseColor("#E24B4A"), // Rojo
        Color.parseColor("#378ADD"), // Azul
        Color.parseColor("#D3D1C7")  // Plata
    )
    val SILVER = Color.parseColor("#D3D1C7")
    val LIGHT = Color.parseColor("#F1EFE8")

    // Apps por defecto (solo se muestran si están instaladas)
    private val DEFAULT_APPS = listOf(
        listOf("com.android.chrome"),
        listOf("com.whatsapp"),
        listOf("com.google.android.youtube"),
        listOf("com.spotify.music", "com.google.android.apps.youtube.music"),
        listOf("com.android.camera", "com.google.android.GoogleCamera"),
        listOf("com.google.android.apps.messaging", "com.android.mms")
    )

    private fun p(ctx: Context): SharedPreferences =
        ctx.getSharedPreferences("redoble", Context.MODE_PRIVATE)

    fun accentIndex(ctx: Context) = p(ctx).getInt("accent", 0)
    fun setAccentIndex(ctx: Context, i: Int) = p(ctx).edit().putInt("accent", i).apply()
    fun accent(ctx: Context) = ACCENTS[accentIndex(ctx).coerceIn(0, ACCENTS.size - 1)]

    fun dim(ctx: Context) = p(ctx).getInt("dim", 40)
    fun setDim(ctx: Context, v: Int) = p(ctx).edit().putInt("dim", v).apply()

    fun city(ctx: Context) = p(ctx).getString("city", "") ?: ""
    fun lat(ctx: Context) = p(ctx).getFloat("lat", Float.NaN)
    fun lon(ctx: Context) = p(ctx).getFloat("lon", Float.NaN)
    fun setCity(ctx: Context, name: String, lat: Double, lon: Double) =
        p(ctx).edit().putString("city", name).putFloat("lat", lat.toFloat())
            .putFloat("lon", lon.toFloat()).remove("weather").apply()

    fun weather(ctx: Context) = p(ctx).getString("weather", "") ?: ""
    fun setWeather(ctx: Context, text: String) = p(ctx).edit().putString("weather", text).apply()

    fun app(ctx: Context, slot: Int): String {
        val prefs = p(ctx)
        val key = "app$slot"
        if (prefs.contains(key)) return prefs.getString(key, "") ?: ""
        val pm = ctx.packageManager
        val found = DEFAULT_APPS.getOrNull(slot)
            ?.firstOrNull { pm.getLaunchIntentForPackage(it) != null } ?: ""
        prefs.edit().putString(key, found).apply()
        return found
    }

    fun setApp(ctx: Context, slot: Int, pkg: String) =
        p(ctx).edit().putString("app$slot", pkg).apply()
}
