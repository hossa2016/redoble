package com.hossa.redoble

import android.content.Context
import java.util.Locale

/**
 * Idiomas: español (base), inglés y portugués.
 * Los textos se escriben en español en el código y se traducen al mostrarlos,
 * con el diccionario de assets/i18n.tsv (es · en · pt). Las frases con datos
 * usan {} como comodín (por ejemplo «Semana {} · {} min»).
 */
object I18n {
    @Volatile private var loaded = false
    private val exact = HashMap<String, Array<String>>()
    private val patterns = ArrayList<Pair<Regex, Array<String>>>()
    private val upper = HashMap<String, Array<String>>()

    /** "es", "en" o "pt". */
    fun lang(ctx: Context): String {
        val pref = Prefs.lang(ctx)
        if (pref != "auto") return pref
        return when (Locale.getDefault().language) { "en" -> "en"; "pt" -> "pt"; else -> "es" }
    }

    fun locale(ctx: Context): Locale = when (lang(ctx)) {
        "en" -> Locale.US; "pt" -> Locale("pt", "BR"); else -> Locale("es", "CO")
    }

    private fun load(ctx: Context) {
        if (loaded) return
        synchronized(this) {
            if (loaded) return
            try {
                ctx.assets.open("i18n.tsv").bufferedReader(Charsets.UTF_8).useLines { lines ->
                    lines.forEach { line ->
                        if (line.isBlank() || line.startsWith("#")) return@forEach
                        val c = line.split('\t').map { it.replace("\\n", "\n") }
                        if (c.size < 3) return@forEach
                        val tr = arrayOf(c[1], c[2])
                        if (c[0].contains("{}")) {
                            val rx = c[0].split("{}").joinToString("(.+?)") { Regex.escape(it) }
                            patterns.add(Regex("^$rx$", RegexOption.DOT_MATCHES_ALL) to tr)
                        } else exact[c[0]] = tr
                    }
                }
            } catch (e: Exception) { }
            exact.forEach { (k, v) -> upper.putIfAbsent(k.uppercase(), v) }
            // Los patrones más largos primero («Tema {}, elegido» antes que «Tema {}»)
            patterns.sortByDescending { it.first.pattern.length }
            loaded = true
        }
    }

    /** Traduce un texto escrito en español; si no está en el diccionario, queda igual. */
    fun tr(ctx: Context, s: String): String {
        if (s.isEmpty()) return s
        val l = lang(ctx)
        if (l == "es") return s
        load(ctx)
        val i = if (l == "en") 0 else 1
        exact[s]?.let { return it[i] }
        // Mayúsculas (títulos de sección)
        if (s == s.uppercase() && s != s.lowercase()) {
            upper[s]?.let { return it[i].uppercase() }
        }
        for ((rx, t) in patterns) {
            val m = rx.matchEntire(s) ?: continue
            var out = t[i]
            // Los datos de la frase se traducen también si están en el diccionario
            m.groupValues.drop(1).forEach { g -> out = out.replaceFirst("{}", exact[g]?.get(i) ?: g) }
            return out
        }
        return s
    }

    fun tr(ctx: Context, s: CharSequence): CharSequence = if (s is String) tr(ctx, s) else s

    /** Traduce las descripciones para TalkBack de una pantalla entera. */
    fun translateTree(ctx: Context, v: android.view.View) {
        if (lang(ctx) == "es") return
        v.contentDescription?.let { d -> if (d is String) v.contentDescription = tr(ctx, d) }
        if (v is android.view.ViewGroup) for (i in 0 until v.childCount) translateTree(ctx, v.getChildAt(i))
    }

    /** Fecha larga con el formato del idioma ("d 'de' MMMM" → "MMMM d" en inglés). */
    fun dateFormat(ctx: Context, es: String): String = when (lang(ctx)) {
        "en" -> when (es) { "d 'de' MMMM" -> "MMMM d"; "EEEE, d 'de' MMMM" -> "EEEE, MMMM d"; "EEEE · d 'de' MMMM" -> "EEEE · MMMM d"; else -> es }
        else -> es
    }
}
