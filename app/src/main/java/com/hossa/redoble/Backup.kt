package com.hossa.redoble

import android.app.Application
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Base64
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** Aplicación: guarda un registro local de los cierres inesperados (no se envía a ningún servidor). */
class PrismaApp : Application() {
    override fun onCreate() {
        super.onCreate()
        val prev = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { t, e ->
            try { ErrorLog.write(this, e) } catch (x: Throwable) { }
            prev?.uncaughtException(t, e)
        }
    }
}

/** Registro de errores en el teléfono; el usuario decide si lo comparte. */
object ErrorLog {
    private const val MAX = 40_000
    fun file(ctx: Context) = File(ctx.filesDir, "errores.txt")

    fun write(ctx: Context, e: Throwable, note: String = "cierre") {
        val f = file(ctx)
        val sw = java.io.StringWriter(); e.printStackTrace(java.io.PrintWriter(sw))
        val head = "=== ${java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.US).format(java.util.Date())} · $note · " +
            "Prisma ${BuildConfig.VERSION_NAME} · Android ${android.os.Build.VERSION.RELEASE} · ${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}\n"
        val old = if (f.exists()) f.readText() else ""
        f.writeText((old + head + sw.toString().take(6000) + "\n").takeLast(MAX))
    }

    /** Para los errores que la app atrapa y sigue funcionando. */
    fun soft(ctx: Context, e: Throwable, where: String) { try { write(ctx, e, where) } catch (x: Throwable) { } }

    fun has(ctx: Context) = file(ctx).let { it.exists() && it.length() > 0 }
    fun clear(ctx: Context) { file(ctx).delete() }

    /** Abre «Compartir» con el texto del registro. */
    fun share(ctx: Context) {
        val text = file(ctx).takeIf { it.exists() }?.readText() ?: return
        ctx.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, "Prisma Widgets · informe de errores")
            putExtra(Intent.EXTRA_TEXT, text)
        }, "Enviar informe de errores").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}

/**
 * Copia de los ajustes en un archivo JSON (con la foto del fondo), para cambiar de
 * teléfono o volver a una configuración. También sirve para compartir un «setup»
 * sin datos personales ([share] = true: sin ciudad, coordenadas, foto ni nombres).
 */
object Backup {
    private const val PREFS = "redoble"
    /** Claves personales que no se comparten con otras personas. */
    private val PERSONAL = setOf("city", "lat", "lon", "wxNow", "wxTomorrow", "wxAt", "wxCode", "wxTemp", "wxDesc",
        "wxForecast", "wxSunrise", "wxSunset", "playerDismissed", "tourLog", "encore", "lastTitle", "lastArtist")

    fun toJson(ctx: Context, share: Boolean = false): JSONObject {
        val all = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).all
        val prefs = JSONObject()
        for ((k, v) in all) {
            if (share && (k in PERSONAL || k.startsWith("label") || k.contains("_label") || k.endsWith("_name"))) continue
            val o = JSONObject()
            when (v) {
                is Boolean -> o.put("t", "b").put("v", v)
                is Int -> o.put("t", "i").put("v", v)
                is Long -> o.put("t", "l").put("v", v)
                is Float -> o.put("t", "f").put("v", v.toDouble())
                is String -> o.put("t", "s").put("v", v)
                is Set<*> -> o.put("t", "set").put("v", JSONArray(v.map { it.toString() }))
                else -> continue
            }
            prefs.put(k, o)
        }
        val root = JSONObject().put("app", "prisma-widgets").put("version", 1)
            .put("appVersion", BuildConfig.VERSION_NAME).put("prefs", prefs)
        if (!share) {
            val photo = File(ctx.filesDir, "fondo.jpg")
            if (photo.exists() && photo.length() < 8_000_000) root.put("photo", Base64.encodeToString(photo.readBytes(), Base64.NO_WRAP))
        }
        return root
    }

    fun export(ctx: Context, uri: Uri) {
        ctx.contentResolver.openOutputStream(uri)?.use { it.write(toJson(ctx).toString().toByteArray()) }
            ?: throw java.io.IOException("No se pudo escribir")
    }

    /** Aplica una copia; devuelve cuántos ajustes se restauraron. */
    fun apply(ctx: Context, root: JSONObject): Int {
        if (root.optString("app") != "prisma-widgets") throw IllegalArgumentException("No es una copia de Prisma")
        val prefs = root.getJSONObject("prefs")
        val ed = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
        var n = 0
        for (k in prefs.keys()) {
            val o = prefs.getJSONObject(k)
            when (o.optString("t")) {
                "b" -> ed.putBoolean(k, o.getBoolean("v"))
                "i" -> ed.putInt(k, o.getInt("v"))
                "l" -> ed.putLong(k, o.getLong("v"))
                "f" -> ed.putFloat(k, o.getDouble("v").toFloat())
                "s" -> ed.putString(k, o.getString("v"))
                "set" -> { val a = o.getJSONArray("v"); ed.putStringSet(k, (0 until a.length()).map { a.getString(it) }.toSet()) }
                else -> continue
            }
            n++
        }
        ed.apply()
        root.optString("photo").takeIf { it.isNotBlank() }?.let {
            File(ctx.filesDir, "fondo.jpg").writeBytes(Base64.decode(it, Base64.NO_WRAP))
        }
        return n
    }

    fun import(ctx: Context, uri: Uri): Int {
        val text = ctx.contentResolver.openInputStream(uri)?.use { it.readBytes().toString(Charsets.UTF_8) }
            ?: throw java.io.IOException("No se pudo leer")
        return apply(ctx, JSONObject(text))
    }

    /** Código corto para compartir un setup (sin datos personales): texto en Base64 comprimido. */
    fun shareCode(ctx: Context): String {
        val bytes = toJson(ctx, share = true).toString().toByteArray()
        val bos = java.io.ByteArrayOutputStream()
        java.util.zip.GZIPOutputStream(bos).use { it.write(bytes) }
        return "PRISMA:" + Base64.encodeToString(bos.toByteArray(), Base64.NO_WRAP or Base64.URL_SAFE)
    }

    fun applyCode(ctx: Context, code: String): Int {
        val raw = code.trim().removePrefix("PRISMA:")
        val gz = Base64.decode(raw, Base64.NO_WRAP or Base64.URL_SAFE)
        val text = java.util.zip.GZIPInputStream(gz.inputStream()).use { it.readBytes().toString(Charsets.UTF_8) }
        return apply(ctx, JSONObject(text))
    }
}
