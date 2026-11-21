package com.hossa.redoble

import android.content.Context
import android.content.SharedPreferences
import android.graphics.Color

object Prefs {
    const val SLOTS = 6

    val ACCENTS = intArrayOf(
        Color.parseColor("#E24B4A"), // Rojo
        Color.parseColor("#378ADD"), // Azul
        Color.parseColor("#D3D1C7"), // Plata
        Color.parseColor("#B93A31"), // Rojo RockPlay
        Color.parseColor("#E8A33D"), // Ámbar
        Color.parseColor("#5FD07A"), // Verde vúmetro
        Color.parseColor("#FF6A2B"), // Naranja fuego
        Color.parseColor("#9B6BFF"), // Morado neón
        Color.parseColor("#3FD0E0"), // Cian eléctrico
        Color.parseColor("#FF3D7F")  // Magenta punk
    )
    val NAMES = arrayOf("Rojo", "Azul", "Plata", "Rojo RockPlay", "Ámbar", "Verde vúmetro",
        "Naranja fuego", "Morado neón", "Cian eléctrico", "Magenta punk")

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

    /** 0 = silueta (como ahora, en un solo tono), 1 = color original de cada app */
    fun iconMode(ctx: Context) = p(ctx).getInt("iconMode", 0)
    fun setIconMode(ctx: Context, v: Int) = p(ctx).edit().putInt("iconMode", v).apply()
    fun originalIcons(ctx: Context) = iconMode(ctx) == 1

    fun dim(ctx: Context) = p(ctx).getInt("dim", 40)
    fun setDim(ctx: Context, v: Int) = p(ctx).edit().putInt("dim", v).apply()

    /** 0 = recortar (llenar la pantalla), 1 = foto completa */
    fun wpMode(ctx: Context) = p(ctx).getInt("wpMode", 1)
    fun setWpMode(ctx: Context, v: Int) = p(ctx).edit().putInt("wpMode", v).apply()
    fun wpPos(ctx: Context) = p(ctx).getInt("wpPos", 50)
    fun setWpPos(ctx: Context, v: Int) = p(ctx).edit().putInt("wpPos", v).apply()
    fun wpZoom(ctx: Context) = p(ctx).getInt("wpZoom", 0)
    fun setWpZoom(ctx: Context, v: Int) = p(ctx).edit().putInt("wpZoom", v).apply()

    fun city(ctx: Context) = p(ctx).getString("city", "") ?: ""
    fun lat(ctx: Context) = p(ctx).getFloat("lat", Float.NaN)
    fun lon(ctx: Context) = p(ctx).getFloat("lon", Float.NaN)
    fun setCity(ctx: Context, name: String, lat: Double, lon: Double) =
        p(ctx).edit().putString("city", name).putFloat("lat", lat.toFloat())
            .putFloat("lon", lon.toFloat()).remove("wxNow").remove("wxTomorrow").remove("wxAt").apply()

    fun weatherNow(ctx: Context) = p(ctx).getString("wxNow", "") ?: ""
    fun weatherTomorrow(ctx: Context) = p(ctx).getString("wxTomorrow", "") ?: ""
    fun weatherAt(ctx: Context) = p(ctx).getLong("wxAt", 0L)
    fun setWeather(ctx: Context, now: String, tomorrow: String) = p(ctx).edit()
        .putString("wxNow", now).putString("wxTomorrow", tomorrow)
        .putLong("wxAt", System.currentTimeMillis()).apply()

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
        p(ctx).edit().putString("app$slot", pkg).remove("label$slot").apply()

    /** Nombre corto opcional para el pad; si no hay, se usa el nombre de la app. */
    fun label(ctx: Context, slot: Int): String? = p(ctx).getString("label$slot", null)?.takeIf { it.isNotBlank() }
    fun setLabel(ctx: Context, slot: Int, name: String?) =
        p(ctx).edit().apply { if (name.isNullOrBlank()) remove("label$slot") else putString("label$slot", name.trim()) }.apply()

    fun displayName(ctx: Context, slot: Int): String {
        val pkg = app(ctx, slot)
        return label(ctx, slot) ?: if (pkg.isBlank()) "" else Apps.label(ctx, pkg)
    }
}
