package com.hossa.redoble

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * Atajos para Tasker, Automate o MacroDroid (intención de difusión):
 *  · com.hossa.redoble.SCENE  extra "scene" = fogata, cafe, concierto…  (fondo animado)
 *  · com.hossa.redoble.THEME  extra "theme" = 0…n o el nombre del tema
 *  · com.hossa.redoble.TEMPLATE extra "template" = completo, musica, minimal
 *  · com.hossa.redoble.REFRESH  (redibuja todos los widgets)
 * Solo funciona si la persona los permitió en Más; cambia el aspecto, no lee ni envía datos.
 * Máximo una orden cada 3 s y el trabajo se hace fuera del hilo principal.
 */
class AutomationReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, i: Intent) {
        if (!Prefs.automation(ctx)) return
        val action = i.action ?: return
        if (action !in setOf(ACTION_SCENE, ACTION_THEME, ACTION_TEMPLATE, ACTION_REFRESH)) return
        val now = android.os.SystemClock.elapsedRealtime()
        if (now - last < 3_000L) return
        last = now
        val scene = i.getStringExtra("scene")
        val theme = i.getStringExtra("theme") ?: i.getIntExtra("theme", -1).takeIf { it >= 0 }?.toString()
        val template = i.getStringExtra("template")
        val pending = goAsync()
        Thread {
            try {
                when (action) {
                    ACTION_SCENE -> if (VideoScenes.ALL.any { it.id == scene }) { Prefs.setLiveScene(ctx, scene!!); Prefs.setWallStyle(ctx, "anim") }
                    ACTION_THEME -> {
                        val idx = theme?.toIntOrNull() ?: Themes.ALL.indexOfFirst { I18n.tr(ctx, it.name).equals(theme, true) || it.name.equals(theme, true) }
                        if (idx in Themes.ALL.indices) Prefs.setTheme(ctx, idx)
                    }
                    ACTION_TEMPLATE -> if (template in TEMPLATES) Prefs.applyPreset(ctx, template!!)
                }
                RockWidget.updateAll(ctx)
            } catch (e: Exception) { ErrorLog.soft(ctx, e, "automation") }
            finally { pending.finish() }
        }.start()
    }

    companion object {
        @Volatile private var last = 0L
        private val TEMPLATES = setOf("completo", "musica", "minimal")
        const val ACTION_SCENE = "com.hossa.redoble.SCENE"
        const val ACTION_THEME = "com.hossa.redoble.THEME"
        const val ACTION_TEMPLATE = "com.hossa.redoble.TEMPLATE"
        const val ACTION_REFRESH = "com.hossa.redoble.REFRESH"
    }
}
