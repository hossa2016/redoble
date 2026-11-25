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
    card.addView(ui.sectionTitle("Música"))
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
            toast("Busca «Acceso a notificaciones» en Ajustes y activa Prisma Widgets.")
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
    card.addView(switchRow("Color de la carátula en todo el widget", Prefs.coverAll(this)) { on ->
        Prefs.setCoverAll(this, on); Stage.changed(this); RockWidget.updateAll(this)
    }, LinearLayout.LayoutParams(-1, -2).apply { topMargin = ui.dp(6) })
    card.addView(ui.note("Mientras suena, la hora, el clima y los detalles toman el color del disco."))

    // Escenario Vivo
    card.addView(switchRow("Escenario Vivo", Prefs.liveStage(this)) { on ->
        Prefs.setLiveStage(this, on); Stage.changed(this)
        if (on && !Prefs.wallStyle(this).let { it == "anim" }) toast("Funciona con el fondo «Animado»")
    }, LinearLayout.LayoutParams(-1, -2).apply { topMargin = ui.dp(12) })
    card.addView(ui.note("Con música, el fondo animado pasa a una escena de concierto teñida con el color de la carátula. Al pausar, vuelve a tu escena."))
    if (Prefs.liveStage(this)) card.addView(ui.segmented(listOf("escenario" to "Escenario", "concierto" to "Concierto", "bateria" to "Batería"),
        Prefs.stageScene(this)) { s -> Prefs.setStageScene(this, s); Stage.changed(this); build() },
        LinearLayout.LayoutParams(-1, -2).apply { topMargin = ui.dp(8) })

    // Entrada de concierto
    card.addView(ui.textButton("Compartir entrada de concierto").apply {
        contentDescription = "Crear una entrada de concierto con la canción que suena y compartirla"
        setOnClickListener {
            val tr = Player.nowTrack(this@playerPlate)
            if (tr == null) { toast("Pon a sonar una canción primero"); return@setOnClickListener }
            io.execute {
                val bmp = ShareArt.ticket(this@playerPlate, tr.first, tr.second, Player.nowArt(this@playerPlate),
                    Stage.accent ?: ui.accent)
                onUi { ShareArt.share(this@playerPlate, bmp, "entrada", "Compartir entrada") }
            }
        }
    }, LinearLayout.LayoutParams(-1, ui.dp(48)).apply { topMargin = ui.dp(12) })

    // Bajar el Telón
    card.addView(ui.text("Bajar el Telón", ui.cond, 15f, Ui.MUTED), LinearLayout.LayoutParams(-2, -2).apply { topMargin = ui.dp(14); bottomMargin = ui.dp(2) })
    val left = SleepTimer.left(this)
    card.addView(ui.note(if (left > 0) "La música se pausa en unos $left min." else "Pausa la música después de un rato, para dormir."))
    card.addView(ui.segmented(listOf(15 to "15 min", 30 to "30 min", 45 to "45 min", 0 to "Apagado"), if (left > 0) -1 else 0) { m ->
        if (m == 0) { SleepTimer.cancel(this); toast("Temporizador apagado") }
        else { SleepTimer.start(this, m); toast("La música se pausa en $m min") }
        build()
    }, LinearLayout.LayoutParams(-1, -2).apply { topMargin = ui.dp(6) })

    // Bitácora de Gira
    card.addView(switchRow("Bitácora de Gira", Prefs.tourLog(this)) { on ->
        Prefs.setTourLog(this, on); if (on) TourLog.changed(this); build()
    }, LinearLayout.LayoutParams(-1, -2).apply { topMargin = ui.dp(12) })
    card.addView(ui.note("Guarda en tu teléfono qué escuchas, para armar tu setlist de la semana y el afiche del año. Nada sale del teléfono."))
    if (Prefs.tourLog(this)) {
        val wk = TourLog.week(this)
        card.addView(ui.bodyText(if (wk.minutes > 0) "Semana ${TourLog.weekNumber()} · ${wk.minutes} min" +
            (if (wk.topArtist.isNotBlank()) " · Más tocada: ${wk.topArtist}" else "") else "Aún no hay escucha esta semana.", Ui.HEAD, 15f),
            LinearLayout.LayoutParams(-1, -2).apply { topMargin = ui.dp(8) })
        fun posterBtn(label: String, make: () -> android.graphics.Bitmap) = card.addView(ui.textButton(label).apply {
            setOnClickListener { io.execute { val b = make(); onUi { ShareArt.share(this@playerPlate, b, "afiche", label) } } }
        }, LinearLayout.LayoutParams(-1, ui.dp(48)))
        posterBtn("Afiche de la semana") {
            ShareArt.poster(this, "SEMANA ${TourLog.weekNumber()}", "SETLIST DE LA SEMANA", TourLog.week(this), ui.accent)
        }
        posterBtn("Afiche de la Gira ${TourLog.yearNumber()}") {
            ShareArt.poster(this, "GIRA ${TourLog.yearNumber()}", "TU AÑO EN ESCENA", TourLog.year(this), ui.accent)
        }
    }

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

    // Idioma de la app y del widget
    card.addView(ui.text("Idioma", ui.cond, 15f, Ui.MUTED), LinearLayout.LayoutParams(-2, -2).apply { topMargin = ui.dp(16); bottomMargin = ui.dp(6) })
    card.addView(ui.segmented(listOf("auto" to "Automático", "es" to "Español", "en" to "English", "pt" to "Português"), Prefs.lang(this)) { l ->
        Prefs.setLang(this, l)
        WeatherWorker.runNow(this, force = true)
        RockWidget.updateAll(this); Widgets.updateKind(this, Widgets.APPS)
        recreate()
    })

    // Ayuda y datos de la app, en un solo lugar
    card.addView(View(this).apply { setBackgroundColor(Ui.veil(0x0F)) },
        LinearLayout.LayoutParams(-1, ui.dp(1)).apply { topMargin = ui.dp(14); bottomMargin = ui.dp(4) })
    fun link(label: String, action: () -> Unit) = card.addView(ui.textButton(label).apply {
        gravity = Gravity.CENTER_VERTICAL or Gravity.START
        setPadding(ui.dp(4), 0, ui.dp(4), 0)
        setOnClickListener { action() }
    }, LinearLayout.LayoutParams(-1, ui.dp(48)))
    link("Guardar una copia de mis ajustes") {
        exportDoc.launch("prisma-ajustes-" + java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).format(java.util.Date()) + ".json")
    }
    link("Restaurar una copia") { importDoc.launch(arrayOf("application/json", "text/plain", "application/octet-stream")) }
    link("Compartir mi setup") {
        val code = Backup.shareCode(this)
        startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, "Mi escenario en Prisma Widgets. Para usarlo: Prisma › App › Usar un setup compartido, y pega este código:\n\n$code")
        }, "Compartir mi setup"))
    }
    link("Usar un setup compartido") { askSetupCode() }
    if (ErrorLog.has(this)) link("Enviar informe de errores") {
        ui.sheet("Informe de errores", "Prisma guardó en tu teléfono el detalle de algunos cierres. No incluye datos personales. ¿Quieres compartirlo?",
            actions = listOf("Compartir" to { ErrorLog.share(this) }, "Borrar el informe" to { ErrorLog.clear(this); build() }))
    }
    link("Ver la guía de inicio") { restartOnboarding() }
    link("Política de privacidad") {
        try {
            startActivity(Intent(Intent.ACTION_VIEW, android.net.Uri.parse("https://github.com/hossa2016/redoble/blob/main/PRIVACY.md")))
        } catch (e: Exception) { }
    }
    return card
}


/** Pegar el código de un setup compartido (sin datos personales). */
internal fun MainActivity.askSetupCode() {
    val field = android.widget.EditText(this).apply {
        hint = "PRISMA:…"
        maxLines = 4
        setTextColor(if (Ui.light) 0xFF1C1A17.toInt() else 0xFFF2ECE4.toInt())
        setHintTextColor(Ui.DIM)
        // Si hay un código copiado, se pega solo
        val clip = (getSystemService(android.content.Context.CLIPBOARD_SERVICE) as? android.content.ClipboardManager)
            ?.primaryClip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.text?.toString()
        clip?.let { c -> Regex("PRISMA:[A-Za-z0-9_\\-=]+").find(c)?.value?.let { setText(it) } }
    }
    ui.sheet("Usar un setup compartido", "Cambia tema, fondo, colores y diseño. Tu ciudad y tus fotos no se tocan.", content = field,
        actions = listOf("Aplicar" to {
            val n = try { Backup.applyCode(this, Regex("PRISMA:[A-Za-z0-9_\\-=]+").find(field.text.toString())?.value ?: field.text.toString()) }
                catch (e: Exception) { -1 }
            if (n < 0) toast("Ese código no es válido") else afterRestore(n)
        }))
}
