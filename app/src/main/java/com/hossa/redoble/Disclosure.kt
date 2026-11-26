package com.hossa.redoble

import android.app.Activity
import android.content.Intent
import android.provider.Settings
import android.widget.Toast

/** Aviso claro antes de mandar a «Acceso a notificaciones» (lo pide Google Play). */
fun Activity.askNotificationAccess(ui: Ui) {
    ui.sheet("Antes de dar el permiso",
        I18n.tr(this, "Android lo llama «Acceso a notificaciones», pero Prisma Widgets solo lo usa para ver la canción que suena: título, artista, carátula y los botones ⏮ ⏯ ⏭.") + "\n\n" +
        I18n.tr(this, "No lee, no guarda y no envía tus notificaciones ni tus mensajes. Puedes quitar el permiso cuando quieras."),
        actions = listOf("Continuar" to {
            try { startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) }
            catch (e: Exception) {
                Toast.makeText(this, I18n.tr(this, "Busca «Acceso a notificaciones» en Ajustes y activa Prisma Widgets."), Toast.LENGTH_LONG).show()
            }
        }), dismissLabel = "Ahora no")
}
