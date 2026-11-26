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
    // El botón solo aparece cuando falta el permiso (con permiso basta el punto verde)
    if (!on) {
        val btn = ui.primary("Dar permiso")
        btn.setOnClickListener { askNotificationAccess(ui) }
        card.addView(btn, LinearLayout.LayoutParams(-1, ui.dp(48)).apply { topMargin = ui.dp(12) })
    }

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


    // Qué música mostrar (con su etiqueta, igual que Posición)
    card.addView(ui.text("Qué música muestra", ui.cond, 15f, Ui.MUTED), LinearLayout.LayoutParams(-2, -2).apply { topMargin = ui.dp(14); bottomMargin = ui.dp(6) })
    card.addView(ui.segmented(listOf(true to "Solo tu app", false to "Cualquier app"), Prefs.onlyMusicApp(this)) { m ->
        Prefs.setOnlyMusicApp(this, m); Player.updateAll(this); build()
    }, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = ui.dp(4) })

    // Color: el del tema o el de la carátula de cada canción
    // Un solo ajuste de color (antes eran dos que se pisaban)
    card.addView(ui.text("Color", ui.cond, 15f, Ui.MUTED), LinearLayout.LayoutParams(-2, -2).apply { topMargin = ui.dp(14); bottomMargin = ui.dp(6) })
    val colorMode = when { !Prefs.coverColor(this) -> 0; Prefs.coverAll(this) -> 2; else -> 1 }
    card.addView(ui.segmented(listOf(0 to "Del tema", 1 to "Carátula", 2 to "Carátula en todo"), colorMode) { m ->
        Prefs.setCoverColor(this, m > 0); Prefs.setCoverAll(this, m == 2)
        Stage.changed(this); RockWidget.updateAll(this); Player.updateAll(this); Widgets.updateKind(this, Widgets.PLAYER); build()
    }, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = ui.dp(4) })
    card.addView(ui.note("«Carátula en todo»: mientras suena, la hora y el clima también toman el color del disco.", sp = 14f))
    // Piel del reproductor
    card.addView(ui.text("Piel del reproductor", ui.cond, 15f, Ui.MUTED), LinearLayout.LayoutParams(-2, -2).apply { topMargin = ui.dp(14); bottomMargin = ui.dp(6) })
    card.addView(ui.segmented(listOf("normal" to "Carátula", "vinilo" to "Vinilo", "casete" to "Casete"), Prefs.playerSkin(this)) { s ->
        Prefs.setPlayerSkin(this, s); Player.updateAll(this); Widgets.updateKind(this, Widgets.PLAYER); build()
    })
    card.addView(ui.note("Vinilo y Casete muestran cuánto va de la canción (se actualiza cada minuto)."))

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
    fun link(label: String, icon: Int = R.drawable.ic_open, action: () -> Unit) = card.addView(ui.actionRow(label, icon).apply {
        setOnClickListener { action() }
    }, LinearLayout.LayoutParams(-1, -2).apply { topMargin = ui.dp(4) })
    link("Guardar copia de ajustes", R.drawable.ic_download) {
        exportDoc.launch("prisma-ajustes-" + java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).format(java.util.Date()) + ".json")
    }
    link("Restaurar una copia", R.drawable.ic_upload) { importDoc.launch(arrayOf("application/json", "text/plain", "application/octet-stream")) }
    link("Usar un diseño compartido", R.drawable.ic_key) { askSetupCode() }
    if (ErrorLog.has(this)) link("Enviar informe de errores", R.drawable.ic_info) {
        ui.sheet("Informe de errores", "Prisma guardó en tu teléfono el detalle de algunos cierres. No incluye datos personales. ¿Quieres compartirlo?",
            actions = listOf("Compartir" to { ErrorLog.share(this) }, "Borrar el informe" to { ErrorLog.clear(this); build() }))
    }
    // De la misma familia de apps (antes aparecía dentro de Comparte, como un anuncio)
    packageManager.getLaunchIntentForPackage(Player.DEFAULT_MUSIC_APP)?.let { rp ->
        link("Descubrir bandas en RockPlay IA") {
            val deep = Intent(Intent.ACTION_VIEW, Uri.parse("rockplay://descubrir")).setPackage(Player.DEFAULT_MUSIC_APP)
            try { startActivity(deep) } catch (e: Exception) { startActivity(rp) }
        }
    }
    link("Atajos para Tasker y Automate", R.drawable.ic_key) {
        val on = Prefs.automation(this)
        ui.sheet("Atajos para Tasker y Automate",
            I18n.tr(this, if (on) "Los atajos están permitidos." else "Los atajos están apagados: otras apps no pueden cambiar tu widget.") + "\n\n" +
            I18n.tr(this, "Envía una intención de difusión (broadcast) con estas acciones:") + "\n\n" +
            "· com.hossa.redoble.SCENE  ‹scene› = fogata, cafe, concierto…\n" +
            "· com.hossa.redoble.THEME  ‹theme› = 0…6\n" +
            "· com.hossa.redoble.TEMPLATE  ‹template› = completo, musica, minimal\n" +
            "· com.hossa.redoble.REFRESH\n\n" +
            I18n.tr(this, "Solo cambian el aspecto; no leen ni envían datos."),
            actions = listOf((if (on) "Apagar atajos" else "Permitir atajos") to {
                Prefs.setAutomation(this, !on)
                toast(I18n.tr(this, if (on) "Atajos apagados" else "Atajos permitidos"))
            }), dismissLabel = "Cerrar")
    }
    link("Ver la guía de inicio", R.drawable.ic_help) { restartOnboarding() }
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
    ui.sheet("Usar un diseño compartido", "Cambia tema, fondo, colores y diseño. Tu ciudad y tus fotos no se tocan.", content = field,
        actions = listOf("Aplicar" to {
            val n = try { Backup.applyCode(this, Regex("PRISMA:[A-Za-z0-9_\\-=]+").find(field.text.toString())?.value ?: field.text.toString()) }
                catch (e: Exception) { -1 }
            if (n < 0) toast("Ese código no es válido") else afterRestore(n)
        }))
}


/** Extras de la música: fondo que sigue la música, temporizador de sueño e historial. */
internal fun MainActivity.musicExtrasPlate(): View {
    val card = ui.plateCard()
    card.addView(ui.sectionTitle("Extras de música"))
    // Fondo que sigue la música (Escenario Vivo)
    card.addView(switchRow("Fondo que sigue la música", Prefs.liveStage(this)) { on ->
        Prefs.setLiveStage(this, on); Stage.changed(this)
        if (on && Prefs.wallStyle(this) != "anim") toast("Funciona con el fondo «Animado»")
        build()
    })
    card.addView(ui.note("Con música, el fondo pasa a modo concierto.",
        "Con música, el fondo animado pasa a una escena de concierto teñida con el color de la carátula. Al pausar, vuelve a tu escena."))
    if (Prefs.liveStage(this)) card.addView(ui.segmented(listOf("escenario" to "Escenario", "concierto" to "Concierto", "bateria" to "Batería"),
        Prefs.stageScene(this)) { s -> Prefs.setStageScene(this, s); Stage.changed(this); build() },
        LinearLayout.LayoutParams(-1, -2).apply { topMargin = ui.dp(8) })

    // Temporizador de sueño (Bajar el Telón)
    card.addView(ui.text("Temporizador de sueño", ui.cond, 17f, Ui.HEAD), LinearLayout.LayoutParams(-2, -2).apply { topMargin = ui.dp(16) })
    val left = SleepTimer.left(this)
    card.addView(ui.note(if (left > 0) I18n.tr(this, "La música se pausa en unos {} min.").replace("{}", left.toString()) else "Pausa la música para dormir."))
    card.addView(ui.segmented(listOf(15 to "15 min", 30 to "30 min", 45 to "45 min", 0 to "Apagado"), if (left > 0) -1 else 0) { m ->
        if (m == 0) { SleepTimer.cancel(this); toast("Temporizador apagado") }
        else if (!Player.enabled(this)) toast("Primero da el acceso a notificaciones (en Música)")
        else { SleepTimer.start(this, m); toast(I18n.tr(this, "La música se pausa en {} min").replace("{}", m.toString())) }
        build()
    }, LinearLayout.LayoutParams(-1, -2).apply { topMargin = ui.dp(6) })

    // Historial de escucha (Bitácora de Gira)
    card.addView(switchRow("Historial de escucha", Prefs.tourLog(this)) { on ->
        Prefs.setTourLog(this, on); if (on) TourLog.changed(this); build()
    }, LinearLayout.LayoutParams(-1, -2).apply { topMargin = ui.dp(16) })
    card.addView(ui.note("Se guarda solo en tu teléfono.",
        "Guarda en tu teléfono qué escuchas, para armar tu setlist de la semana y el afiche del año. Nada sale del teléfono."))
    if (Prefs.tourLog(this)) {
        val wk = TourLog.week(this)
        // Tarjeta de estadística pequeña (no compite con los títulos)
        card.addView(LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = ui.rect(Ui.veil(0x0A), 12f)
            setPadding(ui.dp(14), ui.dp(10), ui.dp(14), ui.dp(10))
            if (wk.minutes > 0) {
                addView(ui.caption(I18n.tr(this@musicExtrasPlate, "SEMANA {}").replace("{}", TourLog.weekNumber().toString())))
                addView(ui.text("${wk.minutes} min", ui.condSemi, 22f, Ui.HEAD))
                val top = wk.topArtist.ifBlank { wk.topTrack }.replace("\"", "").replace("“", "").replace("”", "").trim()
                if (top.isNotBlank()) addView(ui.text(I18n.tr(this@musicExtrasPlate, " · Más tocada: {}").replace("{}", top).removePrefix(" · "),
                    ui.cond, 15f, Ui.MUTED).apply { maxLines = 1; ellipsize = android.text.TextUtils.TruncateAt.END })
            } else addView(ui.text("Aún no hay escucha esta semana.", ui.cond, 15f, Ui.MUTED))
        }, LinearLayout.LayoutParams(-1, -2).apply { topMargin = ui.dp(10) })
    }
    return card
}

/** Comparte y descubre: lo que no es un ajuste (entrada, afiches, RockPlay IA). */
internal fun MainActivity.sharePlate(): View {
    val card = ui.plateCard()
    card.addView(ui.sectionTitle("Comparte y descubre"))
    fun row(label: String, icon: Int, sub: String?, action: () -> Unit) =
        card.addView(ui.actionRow(label, icon, sub).apply { setOnClickListener { action() } },
            LinearLayout.LayoutParams(-1, -2).apply { topMargin = ui.dp(6) })
    row("Entrada de concierto", R.drawable.ic_ticket, "Con la canción que suena") {
        val tr = Player.nowTrack(this)
        if (tr == null) toast("Pon a sonar una canción primero")
        else io.execute {
            val bmp = ShareArt.ticket(this, tr.first, tr.second, Player.nowArt(this), Stage.accent ?: ui.accent)
            val f = ShareArt.save(this, bmp, "entrada")
            onUi { ShareArt.share(this, f, "Compartir entrada") }
        }
    }
    if (Prefs.tourLog(this)) {
        row("Afiche de la semana", R.drawable.ic_image, "Lo que más escuchaste esta semana") {
            io.execute { val f = ShareArt.save(this, ShareArt.poster(this, "SEMANA ${TourLog.weekNumber()}", "SETLIST DE LA SEMANA",
                TourLog.week(this), ui.accent), "afiche"); onUi { ShareArt.share(this, f, "Afiche de la semana") } }
        }
        row(I18n.tr(this, "Afiche de la Gira {}").replace("{}", TourLog.yearNumber().toString()), R.drawable.ic_image, "Tu año en escena") {
            io.execute { val f = ShareArt.save(this, ShareArt.poster(this, "GIRA ${TourLog.yearNumber()}", "TU AÑO EN ESCENA",
                TourLog.year(this), ui.accent), "afiche"); onUi { ShareArt.share(this, f, "Afiche de la Gira") } }
        }
    }
    row("Compartir mi diseño", R.drawable.ic_share, "Código y QR con tu tema y colores") {
        val code = Backup.shareCode(this)
        val text = I18n.tr(this@sharePlate, "Mi escenario en Prisma Widgets. Para usarlo: Prisma › Más › Usar un diseño compartido, y pega este código:\n\n{}").replace("{}", code)
        io.execute {
            // Imagen con el QR (se escanea con la cámara y se pega el texto en Prisma)
            val f = Gallery.qr(code, 900)?.let { q ->
                val out = android.graphics.Bitmap.createBitmap(1000, 1180, android.graphics.Bitmap.Config.ARGB_8888)
                val c = android.graphics.Canvas(out); c.drawColor(android.graphics.Color.WHITE)
                c.drawBitmap(q, 50f, 50f, null)
                val p = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                    color = android.graphics.Color.BLACK; textSize = 56f; textAlign = android.graphics.Paint.Align.CENTER; typeface = ui.condSemi }
                c.drawText("PRISMA WIDGETS", 500f, 1050f, p)
                p.textSize = 34f; p.typeface = ui.cond
                c.drawText(I18n.tr(this@sharePlate, "Escanéalo y pégalo en Más › Usar un diseño compartido"), 500f, 1110f, p)
                ShareArt.save(this@sharePlate, out, "diseno_qr")
            }
            onUi {
                startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
                    if (f != null) {
                        type = "image/png"
                        putExtra(Intent.EXTRA_STREAM, androidx.core.content.FileProvider.getUriForFile(this@sharePlate, "$packageName.files", f))
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    } else type = "text/plain"
                    putExtra(Intent.EXTRA_TEXT, text)
                }, I18n.tr(this@sharePlate, "Compartir mi diseño")))
            }
        }
    }
    return card
}

/** Rendimiento: hora con la letra del tema y actualización en vivo. */
internal fun MainActivity.performancePlate(): View {
    val card = ui.plateCard()
    card.addView(ui.sectionTitle("Rendimiento"))
    val live = Prefs.liveUpdates(this)
    card.addView(switchRow("Actualización en vivo", live) { on ->
        Prefs.setLiveUpdates(this, on)
        if (on) { askNotifications(); LiveService.start(this) } else LiveService.stop(this)
        RockWidget.updateAll(this); build()
    })
    card.addView(ui.note("Batería al instante (con una notificación fija).",
        "Mantiene la batería al día y permite la hora con la letra del tema. Android exige mostrar una " +
        "notificación fija mientras está activa; al encenderla te pedirá el permiso de notificaciones."))
    card.addView(ui.actionRow("Modo confiable", R.drawable.ic_help, Reliable.label(this)).apply { setOnClickListener { showReliable() } },
        LinearLayout.LayoutParams(-1, -2).apply { topMargin = ui.dp(10) })
    card.addView(ui.note("La hora y la fecha con la letra del tema se encienden en Estilo › Tema.", sp = 14f),
        LinearLayout.LayoutParams(-1, -2).apply { topMargin = ui.dp(8) })
    return card
}

