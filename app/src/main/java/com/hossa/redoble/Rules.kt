package com.hossa.redoble

import android.content.Context
import android.media.AudioDeviceInfo
import android.media.AudioManager
import java.util.Calendar

/**
 * «Setlist del Día»: reglas que cambian el escenario solas, sin tocar los ajustes
 * guardados (al dejar de cumplirse, todo vuelve a lo que elegiste).
 *  · Lluvia → escena «Lluvia y café»
 *  · Noche (8 p. m. a 5 a. m.) → tema Ámbar y escena Fogata
 *  · Audífonos → diseño Música grande
 *  · Fechas especiales (Encore) → escena Concierto y el aviso del día
 */
object Rules {
    fun isNight(): Boolean { val h = Calendar.getInstance().get(Calendar.HOUR_OF_DAY); return h >= 20 || h < 5 }

    fun isRain(ctx: Context): Boolean {
        val c = Prefs.weatherCode(ctx)
        return c in 51..67 || c in 80..82 || c in 95..99
    }

    @Volatile private var hpAt = 0L
    @Volatile private var hpVal = false
    fun headphones(ctx: Context): Boolean {
        val now = android.os.SystemClock.elapsedRealtime()
        if (now - hpAt < 5000) return hpVal
        hpVal = headphones0(ctx); hpAt = now
        return hpVal
    }
    fun forget() { hpAt = 0L }
    private fun headphones0(ctx: Context): Boolean {
        val am = ctx.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return false
        return try {
            am.getDevices(AudioManager.GET_DEVICES_OUTPUTS).any {
                it.type == AudioDeviceInfo.TYPE_WIRED_HEADPHONES || it.type == AudioDeviceInfo.TYPE_WIRED_HEADSET ||
                    it.type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP || it.type == AudioDeviceInfo.TYPE_USB_HEADSET ||
                    (android.os.Build.VERSION.SDK_INT >= 31 && it.type == AudioDeviceInfo.TYPE_BLE_HEADSET)
            }
        } catch (e: Exception) { false }
    }

    /** Escena que pide una regla (o null). */
    fun scene(ctx: Context): String? = when {
        Encore.today(ctx) != null -> "concierto"
        Prefs.ruleRain(ctx) && isRain(ctx) -> "cafe"
        Prefs.ruleNight(ctx) && isNight() -> "fogata"
        else -> null
    }

    /** Tema que pide una regla (o null). */
    fun theme(ctx: Context): Int? = if (Prefs.ruleNight(ctx) && isNight()) 2 else null

    /** Audífonos: diseño Música grande sin tocar los ajustes guardados. */
    fun musicLayout(ctx: Context) = Prefs.ruleHeadphones(ctx) && headphones(ctx)

    /** Nombre del «acto» activo para mostrarlo en la app ("" si ninguna regla actúa). */
    fun act(ctx: Context): String {
        val parts = mutableListOf<String>()
        Encore.today(ctx)?.let { parts += it }
        if (Prefs.ruleNight(ctx) && isNight()) parts += I18n.tr(ctx, "Noche")
        if (Prefs.ruleRain(ctx) && isRain(ctx)) parts += I18n.tr(ctx, "Lluvia")
        if (musicLayout(ctx)) parts += I18n.tr(ctx, "Audífonos")
        return parts.joinToString(" · ")
    }

    @Volatile private var lastSig = ""
    /** Al encender la pantalla: si cambió alguna regla, se redibuja todo. */
    fun check(ctx: Context) {
        val sig = "${scene(ctx)}|${theme(ctx)}|${musicLayout(ctx)}"
        if (sig != lastSig) { val first = lastSig.isEmpty(); lastSig = sig; if (!first) RockWidget.updateAll(ctx) }
    }
}

/** «Encore»: fechas que importan (primer concierto, un festival…). */
object Encore {
    /** Lista guardada: "MM-dd;texto" separados por "|". */
    fun all(ctx: Context): List<Pair<String, String>> = Prefs.encore(ctx).split("|").mapNotNull {
        val c = it.split(";", limit = 2); if (c.size == 2 && c[0].length == 5) c[0] to c[1] else null
    }
    fun add(ctx: Context, md: String, text: String) =
        Prefs.setEncore(ctx, (all(ctx) + (md to text.replace("|", " ").replace(";", " "))).joinToString("|") { "${it.first};${it.second}" })
    fun remove(ctx: Context, i: Int) =
        Prefs.setEncore(ctx, all(ctx).filterIndexed { k, _ -> k != i }.joinToString("|") { "${it.first};${it.second}" })
    fun today(ctx: Context): String? {
        val md = java.text.SimpleDateFormat("MM-dd", java.util.Locale.US).format(java.util.Date())
        return all(ctx).firstOrNull { it.first == md }?.second
    }
}
