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

internal fun MainActivity.playerPlate(): View {
    val card = ui.plateCard()
    card.addView(ui.sectionTitle("Reproductor"))
    card.addView(ui.note("Muestra la canción que suena.", "Cuando suena música, el widget muestra la canción con los botones ⏮ ⏯ ⏭. Sin música se oculta."))

    val on = Player.enabled(this)
    card.addView(LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        // Verde si funciona, rojo de aviso si falta el permiso (con acento plata o verde el punto no avisaba)
        addView(ui.dot(if (on) Ui.OK else Ui.ALERT), LinearLayout.LayoutParams(ui.dp(7), ui.dp(7)))
        addView(ui.text(
            if (on) "Activo: puede ver qué suena."
            else "Falta un permiso de Android.",
            ui.cond, 15f, Ui.MUTED
        ).apply { setPadding(ui.dp(8), 0, 0, 0) })
    }, LinearLayout.LayoutParams(-1, -2).apply { topMargin = ui.dp(12) })

    if (!on) {
        card.addView(ui.note("No lee ni guarda tus mensajes.", "Android pide este permiso para mostrar la música en un widget. Prisma Widgets solo lo usa para " +
            "leer la canción y los controles; no lee ni guarda tus mensajes."),
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

    // Dónde va en el widget
    card.addView(ui.text("Posición en el widget", ui.cond, 15f, Ui.MUTED), LinearLayout.LayoutParams(-2, -2).apply { topMargin = ui.dp(14); bottomMargin = ui.dp(6) })
    card.addView(ui.segmented(listOf(false to "Abajo", true to "Debajo del clima"), Prefs.playerTop(this)) { top ->
        Prefs.setPlayerTop(this, top); RockWidget.updateAll(this); build()
    })

    // Qué música mostrar (con su etiqueta, igual que Posición)
    card.addView(ui.text("Qué música muestra", ui.cond, 15f, Ui.MUTED), LinearLayout.LayoutParams(-2, -2).apply { topMargin = ui.dp(14); bottomMargin = ui.dp(6) })
    card.addView(ui.segmented(listOf(true to "Solo tu app", false to "Cualquier app"), Prefs.onlyMusicApp(this)) { m ->
        Prefs.setOnlyMusicApp(this, m); Player.updateAll(this); build()
    }, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = ui.dp(4) })

    // Color: el del tema o el de la carátula de cada canción
    card.addView(ui.text("Color del reproductor", ui.cond, 15f, Ui.MUTED), LinearLayout.LayoutParams(-2, -2).apply { topMargin = ui.dp(14); bottomMargin = ui.dp(6) })
    card.addView(ui.segmented(listOf(true to "Según la carátula", false to "El del tema"), Prefs.coverColor(this)) { on ->
        Prefs.setCoverColor(this, on); Player.updateAll(this); Widgets.updateKind(this, Widgets.PLAYER); build()
    }, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = ui.dp(4) })

    return card
}

/** Lista de apps instaladas para elegir tu app de música. */
internal fun MainActivity.pickMusicApp() {
    io.execute {
        val apps = Apps.launchable(this)
        onUi {
            ui.sheet("Tu app de música", items = apps.map { it.label }, onItem = { i ->
                Prefs.setMusicApp(this, apps[i].pkg)
                Player.updateAll(this)
                build()
            })
        }
    }
}

internal fun MainActivity.widgetPlate(): View {
    val card = ui.plateCard()
    card.layoutTransition = LayoutTransition()
    card.addView(ui.sectionTitle("Widget"))
    card.addView(ui.note("Agrégalo a tu pantalla de inicio.", "Toca Agregar widget. Si tu teléfono no lo permite desde aquí, agrégalo tú en cuatro pasos."))

    val steps = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        background = ui.rect(Ui.WELL, 12f, Ui.veil(0x12), 1f)
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
        if (n > 0) steps.addView(View(this).apply { setBackgroundColor(Ui.veil(0x0D)) },
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

/**
 * Cómo agregar el widget. Muchos teléfonos (Xiaomi/HyperOS, por ejemplo) no dejan que
 * una app lo agregue sola: entonces se explican los pasos para hacerlo a mano.
 */
internal fun MainActivity.showAddWidgetSteps(offerPin: Boolean = false) {
    val msg = "1. Mantén presionado un espacio vacío de la pantalla de inicio.\n" +
        "2. Toca Widgets.\n" +
        "3. Busca Prisma Widgets.\n" +
        "4. Arrástralo a la pantalla y estíralo a lo ancho."
    val pin = offerPin && AppWidgetManager.getInstance(this).isRequestPinAppWidgetSupported
    ui.sheet("Agregar el widget", msg, actions = if (pin) listOf("Probar agregarlo" to {
        if (!tryPin()) toast("Tu teléfono no lo permite desde aquí; usa los pasos.")
        else toast("Si no aparece una ventana para confirmar, usa los pasos.")
    }) else emptyList(), dismissLabel = "Entendido")
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

/** Modo claro u oscuro de esta pantalla de configuración (no cambia el widget). */
internal fun MainActivity.appearancePlate(hasWidget: Boolean): View {
    val card = ui.plateCard()
    card.addView(ui.sectionTitle("Apariencia y ayuda"))
    card.addView(ui.note("Solo cambia esta pantalla, no el widget."))
    card.addView(ui.segmented(listOf("system" to "Sistema", "dark" to "Oscuro", "light" to "Claro"), Prefs.uiMode(this)) {
        Prefs.setUiMode(this, it)
        build()
    }, LinearLayout.LayoutParams(-1, -2).apply { topMargin = ui.dp(14) })

    // Ayuda y datos de la app, en un solo lugar
    card.addView(View(this).apply { setBackgroundColor(Ui.veil(0x0F)) },
        LinearLayout.LayoutParams(-1, ui.dp(1)).apply { topMargin = ui.dp(14); bottomMargin = ui.dp(4) })
    fun link(label: String, action: () -> Unit) = card.addView(ui.textButton(label).apply {
        gravity = Gravity.CENTER_VERTICAL or Gravity.START
        setPadding(ui.dp(4), 0, ui.dp(4), 0)
        setOnClickListener { action() }
    }, LinearLayout.LayoutParams(-1, ui.dp(48)))
    link("Ver la guía de inicio") { restartOnboarding() }
    link("Política de privacidad") {
        try {
            startActivity(Intent(Intent.ACTION_VIEW, android.net.Uri.parse("https://github.com/hossa2016/redoble/blob/main/PRIVACY.md")))
        } catch (e: Exception) { }
    }
    return card
}
