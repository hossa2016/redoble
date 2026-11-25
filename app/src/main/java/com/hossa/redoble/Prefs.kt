package com.hossa.redoble

import android.content.Context
import android.content.SharedPreferences
import android.graphics.Color

object Prefs {
    /** Apps guardadas (hasta 2 filas de 4). */
    const val SLOTS = 8

    val ACCENTS = intArrayOf(
        Color.parseColor("#E24B4A"), // Rojo
        Color.parseColor("#378ADD"), // Azul
        Color.parseColor("#D3D1C7"), // Plata
        Color.parseColor("#B93A31"), // Rojo rock
        Color.parseColor("#E8A33D"), // Ámbar
        Color.parseColor("#5FD07A"), // Verde vúmetro
        Color.parseColor("#FF6A2B"), // Naranja fuego
        Color.parseColor("#C6F03D"), // Lima neón (antes Morado neón: mismo lugar para no mover los guardados)
        Color.parseColor("#3FD0E0"), // Cian eléctrico
        Color.parseColor("#FF3D7F")  // Magenta punk
    )
    val NAMES = arrayOf("Carmín", "Azul", "Plata", "Rojo rock", "Ámbar", "Verde vúmetro",
        "Naranja fuego", "Lima neón", "Cian eléctrico", "Magenta punk")

    // Apps por defecto (solo se muestran si están instaladas)
    private val DEFAULT_APPS = listOf(
        listOf("com.android.chrome"),
        listOf("com.whatsapp"),
        listOf("com.google.android.youtube"),
        listOf("com.spotify.music", "com.google.android.apps.youtube.music"),
        listOf("com.android.camera", "com.google.android.GoogleCamera"),
        listOf("com.google.android.apps.messaging", "com.android.mms"),
        listOf("com.google.android.gm"),
        listOf("com.google.android.apps.maps")
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

    /** Reproductor: true = solo tu app de música; false = cualquier app de música. */
    fun onlyMusicApp(ctx: Context) = p(ctx).getBoolean("onlyRockPlay", false)
    fun setOnlyMusicApp(ctx: Context, v: Boolean) = p(ctx).edit().putBoolean("onlyRockPlay", v).apply()

    /** Tu app de música preferida (paquete). Por defecto, la de rock si está instalada. */
    fun musicApp(ctx: Context): String = p(ctx).getString("musicApp", null) ?: run {
        val d = Player.DEFAULT_MUSIC_APP
        if (ctx.packageManager.getLaunchIntentForPackage(d) != null) d else ""
    }
    fun setMusicApp(ctx: Context, pkg: String) = p(ctx).edit().putString("musicApp", pkg).apply()

    // ---- Tema y distribución ----
    fun theme(ctx: Context) = p(ctx).getInt("theme", 0)
    fun setTheme(ctx: Context, v: Int) = p(ctx).edit().putInt("theme", v).apply()

    /** completo | musica | minimal | custom */
    /** Apps por fila en el widget: 3 o 4. */
    fun perRow(ctx: Context) = if (p(ctx).getInt("perRow", 3) == 4) 4 else 3
    fun setPerRow(ctx: Context, n: Int) = p(ctx).edit().putInt("perRow", n).apply()
    /** Filas de apps: 0, 1 o 2 (se guarda como antes: 0, 3 o 6). */
    fun appRows(ctx: Context) = appsCount(ctx).let { if (it <= 0) 0 else if (it <= 3) 1 else 2 }
    /** Lugares que se usan con la forma elegida (6 con 3 por fila, 8 con 4). */
    fun usedSlots(ctx: Context) = perRow(ctx) * 2
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

    // Sin violetas ni lilas (no van con la estética de la app); en su lugar pizarra, grafito cálido, plata y arena
    val DARK_TONES = listOf("#0E3B5C", "#0B4F57", "#0F4A33", "#5A1622", "#6B3A1E", "#6E5520", "#24343B", "#2A2A2E")
        .map { android.graphics.Color.parseColor(it) }
    val LIGHT_TONES = listOf("#F2C6D2", "#D6DCE0", "#C6EBD9", "#C7E2F5", "#F7D3BC", "#F3EBD8", "#E4D9CC", "#CFDCC8")
        .map { android.graphics.Color.parseColor(it) }

    /**
     * Actualización en vivo (servicio en primer plano): batería al instante y hora
     * con la letra del tema. Apagado: sin notificación fija; la hora usa el reloj
     * del sistema y la batería se actualiza cada 15 minutos.
     */
    fun liveUpdates(ctx: Context): Boolean {
        val sp = p(ctx)
        // Instalaciones nuevas: apagada (sin servicio en primer plano). Quien ya usaba la app: encendida, como antes.
        if (!sp.contains("liveUpdates")) {
            // Se decide una sola vez y se guarda, para que elegir tema después no lo cambie
            val existing = sp.contains("theme") || sp.contains("preset")
            sp.edit().putBoolean("liveUpdates", existing).apply()
            return existing
        }
        return sp.getBoolean("liveUpdates", false)
    }
    fun setLiveUpdates(ctx: Context, v: Boolean) = p(ctx).edit().putBoolean("liveUpdates", v).apply()

    /** Reproductor justo debajo del clima (true) o abajo, junto a las apps (false). */
    fun playerTop(ctx: Context) = p(ctx).getBoolean("playerTop", false)
    fun setPlayerTop(ctx: Context, v: Boolean) = p(ctx).edit().putBoolean("playerTop", v).apply()

    /** Escena del fondo animado de video (fogata, brasero, chispas, ciudad, cafe, ventana). */
    /** Secciones de la configuración que el usuario dejó abiertas. */
    fun openSections(ctx: Context): Set<String> =
        p(ctx).getStringSet("openSections", null) ?: setOf("tema")   // la primera vez, Tema abierto
    fun setOpenSections(ctx: Context, v: Set<String>) = p(ctx).edit().putStringSet("openSections", HashSet(v)).apply()
    /** Pestaña de la configuración: 0 Estilo, 1 Contenido, 2 Widgets, 3 App. */
    fun settingsTab(ctx: Context): Int = p(ctx).getInt("settingsTab", 0).coerceIn(0, 3)
    fun setSettingsTab(ctx: Context, v: Int) = p(ctx).edit().putInt("settingsTab", v).apply()
    /** Apariencia de la configuración: "system", "dark" o "light". */
    fun uiMode(ctx: Context) = p(ctx).getString("uiMode", "dark") ?: "dark"
    fun setUiMode(ctx: Context, v: String) = p(ctx).edit().putString("uiMode", v).apply()
    fun liveScene(ctx: Context) = p(ctx).getString("liveScene", "fogata") ?: "fogata"
    fun setLiveScene(ctx: Context, id: String) = p(ctx).edit().putString("liveScene", id).apply()

    /** Ya vio la guía de inicio (quien ya tenía el widget puesto no la necesita). */
    fun onboarded(ctx: Context) = p(ctx).getBoolean("onboarded", RockWidget.hasWidgets(ctx))
    fun setOnboarded(ctx: Context, v: Boolean) = p(ctx).edit().putBoolean("onboarded", v).apply()

    /** Si sonaba música en el último redibujo (sobrevive aunque el proceso muera). */
    fun lastPlaying(ctx: Context) = p(ctx).getBoolean("lastPlaying", false)
    fun setLastPlaying(ctx: Context, v: Boolean) { if (lastPlaying(ctx) != v) p(ctx).edit().putBoolean("lastPlaying", v).apply() }

    /** Dibujar los textos con las letras del tema (como imagen). */
    fun themeFonts(ctx: Context) = p(ctx).getBoolean("themeFonts", true)
    fun setThemeFonts(ctx: Context, on: Boolean) = p(ctx).edit().putBoolean("themeFonts", on).apply()
    /**
     * Hora, día y fecha con la letra del tema. Apagado por defecto: así se usa el
     * reloj del sistema, que avanza solo aunque HyperOS cierre la app.
     */
    fun themeClock(ctx: Context) = p(ctx).getBoolean("themeClock", false)
    fun setThemeClock(ctx: Context, on: Boolean) = p(ctx).edit().putBoolean("themeClock", on).apply()
    /** Fondo del protector de pantalla: "wall" (el elegido, oscurecido) o "black". */
    fun dreamBg(ctx: Context) = p(ctx).getString("dreamBg", "wall") ?: "wall"
    fun setDreamBg(ctx: Context, v: String) = p(ctx).edit().putString("dreamBg", v).apply()

    /** El reproductor toma el color de la carátula de cada canción. */
    fun coverColor(ctx: Context) = p(ctx).getBoolean("coverColor", true)
    fun setCoverColor(ctx: Context, on: Boolean) = p(ctx).edit().putBoolean("coverColor", on).apply()
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

    /** Clave de una app según el grupo: 0 = «Mis apps» (las de siempre), 1 y 2 = otros grupos. */
    private fun gk(group: Int, base: String) = if (group == 0) base else "g${group}_$base"

    fun app(ctx: Context, slot: Int, group: Int = 0): String {
        val prefs = p(ctx)
        val key = gk(group, "app$slot")
        if (prefs.contains(key)) return prefs.getString(key, "") ?: ""
        if (group != 0) return ""
        val pm = ctx.packageManager
        val found = DEFAULT_APPS.getOrNull(slot)
            ?.firstOrNull { pm.getLaunchIntentForPackage(it) != null } ?: ""
        prefs.edit().putString(key, found).apply()
        return found
    }

    fun setApp(ctx: Context, slot: Int, pkg: String, group: Int = 0) =
        p(ctx).edit().putString(gk(group, "app$slot"), pkg).remove(gk(group, "label$slot")).apply()

    /** Nombre corto opcional para el acceso; si no hay, se usa el nombre de la app. */
    fun label(ctx: Context, slot: Int, group: Int = 0): String? =
        p(ctx).getString(gk(group, "label$slot"), null)?.takeIf { it.isNotBlank() }
    fun setLabel(ctx: Context, slot: Int, name: String?, group: Int = 0) = p(ctx).edit().apply {
        val k = gk(group, "label$slot")
        if (name.isNullOrBlank()) remove(k) else putString(k, name.trim())
    }.apply()

    fun displayName(ctx: Context, slot: Int, group: Int = 0): String {
        val pkg = app(ctx, slot, group)
        return label(ctx, slot, group) ?: if (pkg.isBlank()) "" else Apps.label(ctx, pkg)
    }

    // ---------------------------------------------------------------- widgets por separado

    /** Lugares de cada grupo de apps (el widget de Apps muestra hasta 4 filas de 5). */
    const val GROUP_SLOTS = 20
    val GROUP_NAMES = listOf("Mis apps", "Grupo 2", "Grupo 3")

    /** Widget Hora y clima: qué datos muestra (la hora siempre). */
    fun cw(ctx: Context, el: String) = p(ctx).getBoolean("cw_$el", true)
    fun setCw(ctx: Context, el: String, on: Boolean) = p(ctx).edit().putBoolean("cw_$el", on).apply()

    /** Widget Reproductor: sin música, "last" (la última canción) o "none" («Nada sonando»). */
    fun pwIdle(ctx: Context) = p(ctx).getString("pwIdle", "last") ?: "last"
    fun setPwIdle(ctx: Context, v: String) = p(ctx).edit().putString("pwIdle", v).apply()
    fun lastSong(ctx: Context): Triple<String, String, String> = p(ctx).let {
        Triple(it.getString("lsTitle", "") ?: "", it.getString("lsArtist", "") ?: "", it.getString("lsPkg", "") ?: "")
    }
    fun setLastSong(ctx: Context, title: String, artist: String, pkg: String) {
        val (t, a, k) = lastSong(ctx)
        if (t != title || a != artist || k != pkg)
            p(ctx).edit().putString("lsTitle", title).putString("lsArtist", artist).putString("lsPkg", pkg).apply()
    }

    /** Cada copia del widget de Apps: grupo, nombres y tamaño (automático o a mano). */
    fun awGroup(ctx: Context, id: Int) = p(ctx).getInt("aw_${id}_group", 0).coerceIn(0, 2)
    fun awNames(ctx: Context, id: Int) = p(ctx).getString("aw_${id}_names", "auto") ?: "auto"
    fun awAuto(ctx: Context, id: Int) = p(ctx).getBoolean("aw_${id}_auto", true)
    fun awCols(ctx: Context, id: Int) = p(ctx).getInt("aw_${id}_cols", 4).coerceIn(1, 5)
    fun awRows(ctx: Context, id: Int) = p(ctx).getInt("aw_${id}_rows", 2).coerceIn(1, 4)
    fun setAw(ctx: Context, id: Int, group: Int, names: String, auto: Boolean, cols: Int, rows: Int) =
        p(ctx).edit().putInt("aw_${id}_group", group).putString("aw_${id}_names", names)
            .putBoolean("aw_${id}_auto", auto).putInt("aw_${id}_cols", cols).putInt("aw_${id}_rows", rows).apply()
    fun awExists(ctx: Context, id: Int) = p(ctx).contains("aw_${id}_group")
    /** Copia de seguridad restaurada: los ajustes pasan del número viejo del widget al nuevo. */
    fun moveAw(ctx: Context, from: Int, to: Int) {
        if (!awExists(ctx, from)) return
        setAw(ctx, to, awGroup(ctx, from), awNames(ctx, from), awAuto(ctx, from), awCols(ctx, from), awRows(ctx, from))
        clearAw(ctx, from)
    }
    fun clearAw(ctx: Context, id: Int) = p(ctx).edit().apply {
        listOf("group", "names", "auto", "cols", "rows").forEach { remove("aw_${id}_$it") }
    }.apply()
}
