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

    /** Reproductor: true = solo RockPlay IA; false = cualquier app de música. */
    fun onlyRockPlay(ctx: Context) = p(ctx).getBoolean("onlyRockPlay", true)
    fun setOnlyRockPlay(ctx: Context, v: Boolean) = p(ctx).edit().putBoolean("onlyRockPlay", v).apply()

    // ---- Tema y distribución ----
    fun theme(ctx: Context) = p(ctx).getInt("theme", 0)
    fun setTheme(ctx: Context, v: Int) = p(ctx).edit().putInt("theme", v).apply()

    /** completo | musica | minimal | custom */
    fun preset(ctx: Context) = p(ctx).getString("preset", "completo") ?: "completo"
    val ELEMENTS = listOf("dia", "hora", "clima", "manana", "bateria", "player")
    fun el(ctx: Context, key: String) = p(ctx).getBoolean("el_$key", true)
    fun setEl(ctx: Context, key: String, v: Boolean) =
        p(ctx).edit().putBoolean("el_$key", v).putString("preset", "custom").apply()
    fun appsCount(ctx: Context) = p(ctx).getInt("appsCount", 6)
    fun setAppsCount(ctx: Context, n: Int) =
        p(ctx).edit().putInt("appsCount", n).putString("preset", "custom").apply()
    // ---- Colores de fondo ----
    /** Estilo: "orig" (como está), "fade" (desvanecido) o "solid" (fijo). */
    fun wallStyle(ctx: Context) = p(ctx).getString("wallStyle", "orig") ?: "orig"
    fun setWallStyle(ctx: Context, s: String) = p(ctx).edit().putString("wallStyle", s).apply()
    fun wallColor(ctx: Context) = p(ctx).getInt("wallColor", 0)
    fun setWallColor(ctx: Context, c: Int) = p(ctx).edit().putInt("wallColor", c).apply()
    fun widgetStyle(ctx: Context) = p(ctx).getString("widgetStyle", "orig") ?: "orig"
    fun setWidgetStyle(ctx: Context, s: String) = p(ctx).edit().putString("widgetStyle", s).apply()
    fun widgetColor(ctx: Context) = p(ctx).getInt("widgetColor", 0)
    fun setWidgetColor(ctx: Context, c: Int) = p(ctx).edit().putInt("widgetColor", c).apply()

    val DARK_TONES = listOf("#0E3B5C", "#0B4F57", "#0F4A33", "#5A1622", "#6B3A1E", "#6E5520", "#3A2163", "#2A2A2E")
        .map { android.graphics.Color.parseColor(it) }
    val LIGHT_TONES = listOf("#F2C6D2", "#D9CCF0", "#C6EBD9", "#C7E2F5", "#F7D3BC", "#F3EBD8", "#E6D3EC", "#CFDCC8")
        .map { android.graphics.Color.parseColor(it) }

    /** Dibujar los textos con las letras del tema (como imagen). */
    fun themeFonts(ctx: Context) = p(ctx).getBoolean("themeFonts", true)
    fun setThemeFonts(ctx: Context, on: Boolean) = p(ctx).edit().putBoolean("themeFonts", on).apply()
    fun playerLarge(ctx: Context) = p(ctx).getBoolean("playerLarge", false)
    fun setPlayerLarge(ctx: Context, v: Boolean) =
        p(ctx).edit().putBoolean("playerLarge", v).putString("preset", "custom").apply()

    fun applyPreset(ctx: Context, name: String) {
        val off = when (name) {
            "musica" -> setOf("manana", "bateria")
            "minimal" -> setOf("manana", "bateria", "player")
            else -> emptySet()
        }
        val e = p(ctx).edit().putString("preset", name)
        ELEMENTS.forEach { e.putBoolean("el_$it", it !in off) }
        e.putInt("appsCount", if (name == "completo") 6 else 3)
        e.putBoolean("playerLarge", name == "musica")
        e.apply()
    }

    fun playerDismissed(ctx: Context) = p(ctx).getString("playerDismissed", "") ?: ""
    fun setPlayerDismissed(ctx: Context, v: String) = p(ctx).edit().putString("playerDismissed", v).apply()

    fun city(ctx: Context) = p(ctx).getString("city", "") ?: ""
    fun lat(ctx: Context) = p(ctx).getFloat("lat", Float.NaN)
    fun lon(ctx: Context) = p(ctx).getFloat("lon", Float.NaN)
    fun setCity(ctx: Context, name: String, lat: Double, lon: Double) =
        p(ctx).edit().putString("city", name).putFloat("lat", lat.toFloat())
            .putFloat("lon", lon.toFloat()).remove("wxNow").remove("wxTomorrow").remove("wxAt").apply()

    fun weatherNow(ctx: Context) = p(ctx).getString("wxNow", "") ?: ""
    fun weatherTomorrow(ctx: Context) = p(ctx).getString("wxTomorrow", "") ?: ""
    fun weatherCode(ctx: Context) = p(ctx).getInt("wxCode", -1)
    fun weatherTemp(ctx: Context) = p(ctx).getInt("wxTemp", 0)
    fun weatherDesc(ctx: Context) = p(ctx).getString("wxDesc", "") ?: ""
    fun setWeatherExtra(ctx: Context, code: Int, temp: Int, desc: String) = p(ctx).edit()
        .putInt("wxCode", code).putInt("wxTemp", temp).putString("wxDesc", desc).apply()
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
