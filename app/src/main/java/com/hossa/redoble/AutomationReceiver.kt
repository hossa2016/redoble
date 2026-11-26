package com.hossa.redoble

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * Atajos para Tasker, Automate o MacroDroid (intención de difusión):
 *  · com.hossa.redoble.SCENE  extra "scene" = fogata, cafe, concierto…  (fondo animado)
 *  · com.hossa.redoble.THEME  extra "theme" = 0…n o el nombre del tema
 *  · com.hossa.redoble.TEMPLATE extra "template" = todo, musica, minimo
 *  · com.hossa.redoble.REFRESH  (redibuja todos los widgets)
 * Solo cambia el aspecto: no lee ni envía datos.
 */
class AutomationReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, i: Intent) {
        try {
            when (i.action) {
                ACTION_SCENE -> i.getStringExtra("scene")?.let { id ->
                    if (VideoScenes.ALL.any { it.id == id }) { Prefs.setLiveScene(ctx, id); Prefs.setWallStyle(ctx, "anim") }
                }
                ACTION_THEME -> {
                    val raw = i.getStringExtra("theme") ?: i.getIntExtra("theme", -1).takeIf { it >= 0 }?.toString()
                    val idx = raw?.toIntOrNull() ?: Themes.ALL.indexOfFirst { I18n.tr(ctx, it.name).equals(raw, true) || it.name.equals(raw, true) }
                    if (idx in Themes.ALL.indices) Prefs.setTheme(ctx, idx)
                }
                ACTION_TEMPLATE -> i.getStringExtra("template")?.let { Prefs.applyPreset(ctx, it) }
                ACTION_REFRESH -> {}
                else -> return
            }
            RockWidget.updateAll(ctx)
        } catch (e: Exception) { ErrorLog.soft(ctx, e, "automation") }
    }

    companion object {
        const val ACTION_SCENE = "com.hossa.redoble.SCENE"
        const val ACTION_THEME = "com.hossa.redoble.THEME"
        const val ACTION_TEMPLATE = "com.hossa.redoble.TEMPLATE"
        const val ACTION_REFRESH = "com.hossa.redoble.REFRESH"
    }
}
