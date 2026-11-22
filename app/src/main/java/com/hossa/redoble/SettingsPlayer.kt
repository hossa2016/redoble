package com.hossa.redoble

import android.animation.LayoutTransition
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.Toast

// Ajustes · Reproductor, permisos y agregar el widget.
// Son extensiones de MainActivity para no tener un solo archivo de 1200 líneas.

internal fun MainActivity.widgetDonePlate(): View {
    val card = ui.plateCard()
    card.addView(LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        addView(ui.dot(Ui.OK), LinearLayout.LayoutParams(ui.dp(8), ui.dp(8)))
        addView(ui.text("Widget en pantalla", ui.condMedium, 16f, Ui.HEAD).apply { setPadding(ui.dp(10), 0, 0, 0) },
            LinearLayout.LayoutParams(0, -2, 1f))
        addView(ui.textButton("Agregar otro").apply {
            setOnClickListener { tryPin() }
        })
    })
    return card
}

internal fun MainActivity.playerPlate(): View {
    val card = ui.plateCard()
    card.addView(ui.sectionTitle("Reproductor"))
    card.addView(ui.bodyText("Cuando suena música, el widget muestra la canción con los botones ⏮ ⏯ ⏭. Sin música se oculta."))

    val on = Player.enabled(this)
    card.addView(LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        addView(ui.dot(if (on) Ui.OK else ui.accent), LinearLayout.LayoutParams(ui.dp(7), ui.dp(7)))
        addView(ui.text(
            if (on) "Activo: Prisma Widgets puede ver qué está sonando."
            else "Falta el permiso de acceso a notificaciones.",
            ui.cond, 15f, Ui.MUTED
        ).apply { setPadding(ui.dp(8), 0, 0, 0) })
    }, LinearLayout.LayoutParams(-1, -2).apply { topMargin = ui.dp(12) })

    if (!on) {
        card.addView(ui.bodyText("Android pide este permiso para mostrar la música en un widget. Prisma Widgets solo lo usa para " +
            "leer la canción y los controles; no lee ni guarda tus mensajes.", Ui.MUTED, 13f),
            LinearLayout.LayoutParams(-1, -2).apply { topMargin = ui.dp(8) })
    }
    val btn = if (on) ui.secondary("Revisar permiso") else ui.primary("Dar permiso")
    btn.setOnClickListener {
        try {
            startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
        } catch (e: Exception) {
            Toast.makeText(this, "Busca «Acceso a notificaciones» en Ajustes y activa Prisma Widgets.", Toast.LENGTH_LONG).show()
        }
    }
    card.addView(btn, LinearLayout.LayoutParams(-1, ui.dp(48)).apply { topMargin = ui.dp(12) })

    // Tu app de música preferida
    val fav = Prefs.musicApp(this)
    val favName = if (fav.isBlank()) "Ninguna elegida" else Apps.label(this, fav)
    card.addView(LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        background = ui.rowBg()
        minimumHeight = ui.dp(56)
        setPadding(ui.dp(6), 0, ui.dp(6), 0)
        addView(LinearLayout(this@playerPlate).apply {
            orientation = LinearLayout.VERTICAL
            addView(ui.text("Tu app de música", ui.cond, 15f, Ui.MUTED))
            addView(ui.ellipsize(ui.text(favName, ui.condMedium, 18f, Ui.HEAD)))
        }, LinearLayout.LayoutParams(0, -2, 1f))
        addView(ui.text("Cambiar", ui.cond, 15f, ui.accent))
        addView(ui.text("›", ui.condLight, 22f, Ui.MUTED).apply { setPadding(ui.dp(8), 0, 0, ui.dp(2)) })
        contentDescription = "Tu app de música: $favName. Cambiar"
        setOnClickListener { pickMusicApp() }
    }, LinearLayout.LayoutParams(-1, -2).apply { topMargin = ui.dp(14) })

    // Qué música mostrar
    card.addView(ui.segmented(listOf(true to "Solo tu app", false to "Cualquier app"), Prefs.onlyMusicApp(this)) { m ->
        Prefs.setOnlyMusicApp(this, m); Player.updateAll(this); build()
    }, LinearLayout.LayoutParams(-1, -2).apply { topMargin = ui.dp(12); bottomMargin = ui.dp(4) })

    // Guía de inicio otra vez
    card.addView(ui.textButton("Ver la guía de inicio").apply {
        setOnClickListener { restartOnboarding() }
    }, LinearLayout.LayoutParams(-1, ui.dp(48)).apply { topMargin = ui.dp(8) })
    card.addView(ui.textButton("Política de privacidad").apply {
        setOnClickListener {
            try {
                startActivity(Intent(Intent.ACTION_VIEW, android.net.Uri.parse("https://github.com/hossa2016/redoble/blob/main/PRIVACY.md")))
            } catch (e: Exception) { }
        }
    }, LinearLayout.LayoutParams(-1, ui.dp(48)))
    return card
}

/** Lista de apps instaladas para elegir tu app de música. */
internal fun MainActivity.pickMusicApp() {
    io.execute {
        val apps = Apps.launchable(this)
        onUi {
            androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("Tu app de música")
                .setItems(apps.map { it.label }.toTypedArray()) { _, i ->
                    Prefs.setMusicApp(this, apps[i].pkg)
                    Player.updateAll(this)
                    build()
                }
                .setNegativeButton("Cancelar", null)
                .show()
        }
    }
}

internal fun MainActivity.widgetPlate(): View {
    val card = ui.plateCard()
    card.layoutTransition = LayoutTransition()
    card.addView(ui.sectionTitle("Widget"))
    card.addView(ui.bodyText("Toca Agregar widget. Si tu teléfono no lo permite desde aquí, agrégalo tú en cuatro pasos."))

    val steps = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        background = ui.rect(Ui.WELL, 12f, Color.parseColor("#12FFFFFF"), 1f)
        setPadding(ui.dp(14), ui.dp(14), ui.dp(14), ui.dp(4))
        visibility = View.GONE
    }
    val texts = listOf(
        "Mantén presionado un espacio vacío de la pantalla de inicio.",
        "Toca Widgets.",
        "Busca Prisma Widgets en la lista.",
        "Arrástralo a la pantalla y estíralo hasta ocupar el ancho."
    )
    texts.forEachIndexed { n, t ->
        if (n > 0) steps.addView(View(this).apply { setBackgroundColor(Color.parseColor("#0DFFFFFF")) },
            LinearLayout.LayoutParams(-1, ui.dp(1)))
        steps.addView(LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, ui.dp(9), 0, ui.dp(9))
            addView(ui.text("${n + 1}", ui.condSemi, 14f, ui.accent).apply {
                gravity = Gravity.CENTER
                background = ui.oval(Color.TRANSPARENT, ui.accentLine, 1f)
            }, LinearLayout.LayoutParams(ui.dp(24), ui.dp(24)))
            addView(ui.bodyText(t, Ui.HEAD, 15f), LinearLayout.LayoutParams(0, -2, 1f).apply { marginStart = ui.dp(12) })
        })
    }

    val add = ui.primary("Agregar widget")
    add.setOnClickListener {
        val before = RockWidget.hasWidgets(this)
        val ok = tryPin()
        if (!ok) {
            steps.visibility = View.VISIBLE
        } else {
            onUiDelayed(3000) {
                if (RockWidget.hasWidgets(this) == before) steps.visibility = View.VISIBLE
            }
        }
    }
    card.addView(add, LinearLayout.LayoutParams(-1, ui.dp(48)).apply { topMargin = ui.dp(14); bottomMargin = ui.dp(12) })
    card.addView(steps, LinearLayout.LayoutParams(-1, -2))
    return card
}

internal fun MainActivity.tryPin(): Boolean {
    val mgr = AppWidgetManager.getInstance(this)
    return try {
        mgr.isRequestPinAppWidgetSupported &&
            mgr.requestPinAppWidget(ComponentName(this, RockWidget::class.java), null, null)
    } catch (e: Exception) {
        false
    }
}
