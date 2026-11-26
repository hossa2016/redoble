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
    fun accent(ctx: Context): Int =
        if (systemAccent(ctx)) ctx.getColor(android.R.color.system_accent1_300)
        else ACCENTS[accentIndex(ctx).coerceIn(0, ACCENTS.size - 1)]

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
    fun setTheme(ctx: Context, v: Int) = p(ctx).edit().putInt("theme", v).putString("rulePause", Rules.rawSig(ctx)).apply()
    /** Regla del Setlist del Día en pausa (firma de la regla que estaba activa al elegir a mano). */
    fun rulePause(ctx: Context) = p(ctx).getString("rulePause", "") ?: ""

    /** completo | musica | minimal | custom */
    /** Apps por fila en el widget: 3 o 4. */
    fun perRow(ctx: Context) = if (p(ctx).getInt("perRow", 3) == 4) 4 else 3
    fun setPerRow(ctx: Context, n: Int) = p(ctx).edit().putInt("perRow", n).apply()
    /** Filas de apps: 0, 1 o 2 (se guarda como antes: 0, 3 o 6). */
    fun appRows(ctx: Context) = appsCount(ctx).let { if (it <= 0) 0 else if (it <= 3) 1 else 2 }
    /** Lugares que se usan con la forma elegida (6 con 3 por fila, 8 con 4). */
    fun usedSlots(ctx: Context) = perRow(ctx) * 2
    fun preset(ctx: Context) = p(ctx).getString("preset", "completo") ?: "completo"
    val ELEMENTS = listOf("dia", "hora", "clima", "manana", "bateria", "player", "fecha")

    // ---- Editor visual (16.0): orden, tamaño, color y letra de cada parte del widget Completo ----
    val BLOCKS = listOf("head", "player", "apps")
    val HEAD_PARTS = listOf("dia", "hora", "fecha", "clima", "bateria")
    private fun order(ctx: Context, key: String, def: List<String>): List<String> {
        val got = (p(ctx).getString(key, null) ?: "").split(",").filter { it in def }.distinct()
        return got + def.filter { it !in got }
    }
    fun blockOrder(ctx: Context) = order(ctx, "ordenBloques", BLOCKS)
    fun headOrder(ctx: Context) = order(ctx, "ordenCabecera", HEAD_PARTS)
    fun setBlockOrder(ctx: Context, l: List<String>) = p(ctx).edit().putString("ordenBloques", l.joinToString(",")).apply()
    fun setHeadOrder(ctx: Context, l: List<String>) = p(ctx).edit().putString("ordenCabecera", l.joinToString(",")).apply()
    /** Tamaño de una parte: 0,7 a 1,5 (1 = el del tema). */
    fun partScale(ctx: Context, part: String) = p(ctx).getFloat("es_$part", 1f).coerceIn(0.7f, 1.5f)
    fun setPartScale(ctx: Context, part: String, v: Float) = p(ctx).edit().putFloat("es_$part", v).apply()
    /** Color de una parte (0 = el del tema). */
    fun partColor(ctx: Context, part: String) = p(ctx).getInt("ec_$part", 0)
    fun setPartColor(ctx: Context, part: String, v: Int) = p(ctx).edit().putInt("ec_$part", v).apply()
    /** Letra de una parte: nombre del Taller de Afiches ("tema" = la del widget). */
    fun partFont(ctx: Context, part: String) = p(ctx).getString("ef_$part", "tema") ?: "tema"
    fun setPartFont(ctx: Context, part: String, v: String) = p(ctx).edit().putString("ef_$part", v).apply()
    val EDIT_PARTS = listOf("dia", "hora", "fecha", "clima", "bateria", "player", "apps")
    /** ¿Hay algo cambiado en el editor visual? */
    fun editorTouched(ctx: Context): Boolean {
        val all = p(ctx).all
        return all.containsKey("ordenBloques") || all.containsKey("ordenCabecera") ||
            EDIT_PARTS.any { all.containsKey("es_$it") || all.containsKey("ec_$it") || all.containsKey("ef_$it") }
    }
    fun resetEditor(ctx: Context) {
        val e = p(ctx).edit().remove("ordenBloques").remove("ordenCabecera")
        EDIT_PARTS.forEach { e.remove("es_$it").remove("ec_$it").remove("ef_$it") }
        e.apply()
    }
    fun el(ctx: Context, key: String) =
        if ((key == "manana" || key == "bateria") && Rules.musicLayout(ctx)) false else p(ctx).getBoolean("el_$key", true)
    /** Lo que eligió el usuario, sin las reglas automáticas (p. ej. audífonos). */
    fun elRaw(ctx: Context, key: String) = p(ctx).getBoolean("el_$key", true)
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
    fun setLiveScene(ctx: Context, id: String) = p(ctx).edit().putString("liveScene", id).putString("rulePause", Rules.rawSig(ctx)).apply()

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
    fun playerLarge(ctx: Context) = Rules.musicLayout(ctx) || p(ctx).getBoolean("playerLarge", false)
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
        // Una plantilla parte de cero: también quita lo hecho en el editor
        e.remove("ordenBloques").remove("ordenCabecera").putBoolean("playerTop", false)
        EDIT_PARTS.forEach { e.remove("es_$it").remove("ec_$it").remove("ef_$it") }
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
    /** Setlist del Día: reglas automáticas. */
    fun ruleRain(ctx: Context) = p(ctx).getBoolean("ruleRain", false)
    fun ruleNight(ctx: Context) = p(ctx).getBoolean("ruleNight", false)
    fun ruleHeadphones(ctx: Context) = p(ctx).getBoolean("ruleHeadphones", false)
    fun setRule(ctx: Context, key: String, v: Boolean) = p(ctx).edit().putBoolean(key, v).apply()
    /** Encore: fechas especiales "MM-dd;texto|…". */
    fun encore(ctx: Context) = p(ctx).getString("encore", "") ?: ""
    fun setEncore(ctx: Context, v: String) = p(ctx).edit().putString("encore", v).apply()
    /** Batería «hasta el 11» al llegar al 100 % cargando. */
    fun amp11(ctx: Context) = true  // «hasta el 11»: un guiño siempre activo
    fun setAmp11(ctx: Context, v: Boolean) = p(ctx).edit().putBoolean("amp11", v).apply()
    /** Días distintos en que se abrió la app (para pedir la reseña una sola vez, al tercer día). */
    fun markDayUsed(ctx: Context): Int {
        val today = java.text.SimpleDateFormat("yyyyMMdd", java.util.Locale.US).format(java.util.Date())
        val set = HashSet(p(ctx).getStringSet("daysUsed", emptySet()) ?: emptySet())
        if (set.add(today)) p(ctx).edit().putStringSet("daysUsed", if (set.size > 5) HashSet(set.sorted().takeLast(5)) else set).apply()
        return set.size
    }
    fun reviewAsked(ctx: Context) = p(ctx).getBoolean("reviewAsked", false)
    fun setReviewAsked(ctx: Context) = p(ctx).edit().putBoolean("reviewAsked", true).apply()
    /** Taller de Afiches: letra propia ("tema" = la del tema). */
    fun workshopFont(ctx: Context) = p(ctx).getString("workshopFont", "tema") ?: "tema"
    fun setWorkshopFont(ctx: Context, v: String) = p(ctx).edit().putString("workshopFont", v).apply()
    /** Piel del reproductor: "normal", "vinilo" o "casete". */
    fun playerSkin(ctx: Context) = p(ctx).getString("playerSkin", "normal") ?: "normal"
    fun setPlayerSkin(ctx: Context, v: String) = p(ctx).edit().putString("playerSkin", v).apply()
    /** Idioma de la app y del widget: "auto" (el del teléfono), "es", "en" o "pt". */
    fun lang(ctx: Context) = p(ctx).getString("lang", "auto") ?: "auto"
    fun setLang(ctx: Context, v: String) = p(ctx).edit().putString("lang", v).apply()
    /** Bitácora de Gira: historial de escucha solo en el teléfono. */
    fun tourLog(ctx: Context) = p(ctx).getBoolean("tourLogOn", false)
    fun setTourLog(ctx: Context, v: Boolean) = p(ctx).edit().putBoolean("tourLogOn", v).apply()
    /** Hora en que «Bajar el Telón» pausa la música (0 = sin temporizador). */
    fun sleepAt(ctx: Context) = p(ctx).getLong("sleepAt", 0L)
    fun setSleepAt(ctx: Context, v: Long) = p(ctx).edit().putLong("sleepAt", v).apply()
    /** Escenario Vivo: con música, el fondo animado pasa a una escena de concierto. */
    fun liveStage(ctx: Context) = p(ctx).getBoolean("liveStage", false)
    fun setLiveStage(ctx: Context, v: Boolean) = p(ctx).edit().putBoolean("liveStage", v).apply()
    fun stageScene(ctx: Context) = p(ctx).getString("stageScene", "escenario") ?: "escenario"
    fun setStageScene(ctx: Context, v: String) = p(ctx).edit().putString("stageScene", v).apply()
    /** El color de la carátula pinta todo el widget mientras suena música. */
    fun coverAll(ctx: Context) = p(ctx).getBoolean("coverAll", false)
    fun setCoverAll(ctx: Context, v: Boolean) = p(ctx).edit().putBoolean("coverAll", v).apply()
    /** Color de acento del sistema (Material You, Android 12+). */
    fun systemAccent(ctx: Context) = p(ctx).getBoolean("systemAccent", false) && android.os.Build.VERSION.SDK_INT >= 31
    fun setSystemAccent(ctx: Context, v: Boolean) = p(ctx).edit().putBoolean("systemAccent", v).apply()
    /** Cabecera: fecha estilo cartel, próxima alarma y próximo evento. */
    fun poster(ctx: Context) = p(ctx).getBoolean("poster", false)
    fun setPoster(ctx: Context, v: Boolean) = p(ctx).edit().putBoolean("poster", v).apply()
    fun showAlarm(ctx: Context) = p(ctx).getBoolean("showAlarm", false)
    fun setShowAlarm(ctx: Context, v: Boolean) = p(ctx).edit().putBoolean("showAlarm", v).apply()
    fun showAgenda(ctx: Context) = p(ctx).getBoolean("showAgenda", false)
    fun setShowAgenda(ctx: Context, v: Boolean) = p(ctx).edit().putBoolean("showAgenda", v).apply()
    /** Temperatura en grados Fahrenheit (si no, Celsius). */
    fun tempF(ctx: Context) = p(ctx).getBoolean("tempF", false)
    fun setTempF(ctx: Context, v: Boolean) = p(ctx).edit().putBoolean("tempF", v).remove("wxAt").apply()
    /** Clima con frases de la marca («Tormenta afuera: noche de distorsión»). */
    /** La voz rock del clima es parte del tema Rock (ya no es un interruptor aparte). */
    fun rockVoice(ctx: Context) = try { Themes.widget(ctx).family == "rock" } catch (e: Exception) { false }
    fun rockVoiceOld(ctx: Context) = p(ctx).getBoolean("rockVoice", false)
    fun setRockVoice(ctx: Context, v: Boolean) = p(ctx).edit().putBoolean("rockVoice", v).apply()
    /** Pronóstico de los próximos días: "día;código;mín;máx" separados por "|". */
    fun forecast(ctx: Context) = p(ctx).getString("wxForecast", "") ?: ""
    fun setForecast(ctx: Context, v: String) = p(ctx).edit().putString("wxForecast", v).apply()
    /** Amanecer y atardecer de hoy ("06:02", "18:11"). */
    fun sunTimes(ctx: Context) = (p(ctx).getString("wxSunrise", "") ?: "") to (p(ctx).getString("wxSunset", "") ?: "")
    fun setSunTimes(ctx: Context, rise: String, set: String) = p(ctx).edit().putString("wxSunrise", rise).putString("wxSunset", set).apply()
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

    /** Sube las apps a los primeros lugares (sin huecos); devuelve true si movió algo. */
    fun compactApps(ctx: Context, group: Int, count: Int): Boolean {
        val items = (0 until count).map { app(ctx, it, group) to label(ctx, it, group) }
        val filled = items.filter { it.first.isNotBlank() }
        val hasGap = items.indexOfFirst { it.first.isBlank() }.let { g -> g >= 0 && items.drop(g).any { it.first.isNotBlank() } }
        if (!hasGap) return false
        for (k in 0 until count) {
            val (pkg, lbl) = filled.getOrNull(k) ?: ("" to "")
            setApp(ctx, k, pkg, group); setLabel(ctx, k, lbl, group)
        }
        return true
    }

    /** Lee el lugar tal cual (sin poner apps por defecto en los lugares nunca usados). */
    fun rawApp(ctx: Context, slot: Int, group: Int = 0): String =
        p(ctx).getString(gk(group, "app$slot"), "") ?: ""

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
    /** Nombre propio de cada widget de Apps (para distinguir una copia de otra). */
    fun awName(ctx: Context, id: Int): String? = p(ctx).getString("aw_${id}_name", null)?.takeIf { it.isNotBlank() }
    fun setAwName(ctx: Context, id: Int, name: String?) = p(ctx).edit().apply {
        if (name.isNullOrBlank()) remove("aw_${id}_name") else putString("aw_${id}_name", name.trim())
    }.apply()
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
        setAwName(ctx, to, awName(ctx, from))
        clearAw(ctx, from)
    }
    fun clearAw(ctx: Context, id: Int) = p(ctx).edit().apply {
        listOf("group", "names", "auto", "cols", "rows", "name").forEach { remove("aw_${id}_$it") }
    }.apply()
}
