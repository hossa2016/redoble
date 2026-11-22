package com.hossa.redoble

import android.content.Context
import android.content.Intent
import java.text.Collator
import java.text.Normalizer
import java.util.Locale

data class AppEntry(val label: String, val pkg: String)

object Apps {
    private val labels = java.util.concurrent.ConcurrentHashMap<String, String>()

    fun label(ctx: Context, pkg: String): String = labels.getOrPut(pkg) {
        val pm = ctx.packageManager
        try { pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString() } catch (e: Exception) { pkg }
    }

    fun launchable(ctx: Context): List<AppEntry> {
        val pm = ctx.packageManager
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val collator = Collator.getInstance(Locale("es", "CO")).apply { strength = Collator.PRIMARY }
        return pm.queryIntentActivities(intent, 0)
            .map { AppEntry(it.loadLabel(pm).toString(), it.activityInfo.packageName) }
            .filter { it.pkg != ctx.packageName }
            .distinctBy { it.pkg }
            .sortedWith { a, b -> collator.compare(a.label, b.label) }
    }

    /** Quita tildes y pasa a minúsculas para buscar. */
    fun normalize(s: String): String =
        Normalizer.normalize(s, Normalizer.Form.NFD).replace(Regex("\\p{Mn}+"), "").lowercase(Locale.ROOT)

    fun initial(label: String): String {
        val c = normalize(label).firstOrNull()?.uppercaseChar() ?: '#'
        return if (c in 'A'..'Z') c.toString() else "#"
    }
}
