package com.hossa.redoble

import android.appwidget.AppWidgetManager
import android.content.Intent
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout

// Ajustes de las piezas sueltas. Cada sección aparece solo si ese widget está puesto.

/** Widget Hora y clima: qué datos muestra. */
internal fun MainActivity.clockWidgetPlate(): View {
    val card = ui.plateCard()
    card.addView(ui.sectionTitle("Widget Hora y clima"))
    card.addView(ui.note("La hora siempre se ve. Si el widget es pequeño, se quitan datos solos.",
        "Bajito: solo la hora. Mediano: hora, fecha y clima. Alto: todo lo que elijas aquí."))
    listOf("dia" to "Día y fecha", "clima" to "Clima actual", "manana" to "Pronóstico de mañana", "bateria" to "Batería").forEach { (k, label) ->
        card.addView(switchRow(label, Prefs.cw(this, k)) { on ->
            Prefs.setCw(this, k, on); Widgets.updateKind(this, Widgets.CLOCK); build()
        })
    }
    return card
}

/** Widgets de Apps: cada copia con su grupo y tamaño, y un botón para cambiarla. */
internal fun MainActivity.appsWidgetsPlate(): View {
    val card = ui.plateCard()
    val ids = Widgets.ids(this, Widgets.APPS)
    card.addView(ui.sectionTitle("Widgets de Apps"))
    card.addView(ui.note("Toca uno para ponerle nombre o cambiar su grupo y tamaño."))
    val baseNames = ids.map { Prefs.awName(this, it) ?: Widgets.shapeName(this, it) }
    val shownNames = baseNames.mapIndexed { n, b ->
        if (baseNames.count { it == b } > 1) "$b · ${baseNames.take(n + 1).count { it == b }}" else b
    }
    ids.forEachIndexed { n, id ->
        if (n > 0) card.addView(View(this).apply { setBackgroundColor(Ui.veil(0x0F)) }, LinearLayout.LayoutParams(-1, ui.dp(1)))
        card.addView(LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = ui.rowBg()
            setPadding(ui.dp(6), 0, ui.dp(6), 0)
            addView(LinearLayout(this@appsWidgetsPlate).apply {
                orientation = LinearLayout.VERTICAL
                addView(ui.text(shownNames[n], ui.cond, 18f, Ui.HEAD))
                // Hasta dos líneas: el resumen ya no se corta con «…»
                addView(ui.text(Widgets.appsSummary(this@appsWidgetsPlate, id), ui.cond, 14f, Ui.DIM).apply { maxLines = 2 })
                // Sus primeras apps, para distinguir una copia de otra
                val g = Prefs.awGroup(this@appsWidgetsPlate, id)
                val pkgs = (0 until Prefs.GROUP_SLOTS).map { Prefs.app(this@appsWidgetsPlate, it, g) }.filter { it.isNotBlank() }.take(5)
                if (pkgs.isNotEmpty()) addView(LinearLayout(this@appsWidgetsPlate).apply {
                    orientation = LinearLayout.HORIZONTAL
                    pkgs.forEachIndexed { k, p ->
                        addView(ui.appHolder(p, 26, 14).apply {
                            (layoutParams as LinearLayout.LayoutParams).apply { if (k > 0) marginStart = ui.dp(4) }
                        })
                    }
                }, LinearLayout.LayoutParams(-2, -2).apply { topMargin = ui.dp(6) })
            }, LinearLayout.LayoutParams(0, -2, 1f))
            addView(ui.text("›", ui.condLight, 22f, Ui.DIM).apply { setPadding(ui.dp(8), 0, 0, ui.dp(2)) })
            val title = Prefs.awName(this@appsWidgetsPlate, id) ?: Widgets.shapeName(this@appsWidgetsPlate, id)
            contentDescription = "$title, ver opciones"
            setOnClickListener {
                ui.sheet(title, items = listOf("Cambiar grupo o tamaño", "Ponerle nombre"), onItem = { which ->
                    if (which == 0) startActivity(Intent(this@appsWidgetsPlate, AppsWidgetConfig::class.java)
                        .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id))
                    else renameAppsWidget(id, title)
                })
            }
        }.apply { minimumHeight = ui.dp(64); setPadding(ui.dp(6), ui.dp(10), ui.dp(6), ui.dp(10)) }, LinearLayout.LayoutParams(-1, -2))
    }
    return card
}

/** Widget Reproductor: qué muestra cuando no suena nada. */
internal fun MainActivity.playerWidgetPlate(): View {
    val card = ui.plateCard()
    card.addView(ui.sectionTitle("Widget reproductor"))
    card.addView(ui.note("Tres tamaños: estíralo y cambia solo.",
        "Bajito: carátula y botones, como una píldora. Mediano: carátula a la izquierda con título y botones. " +
        "Grande (casi cuadrado): carátula arriba, título y botones abajo."))
    card.addView(ui.text("Sin música, mostrar", ui.cond, 16f, Ui.HEAD),
        LinearLayout.LayoutParams(-2, -2).apply { topMargin = ui.dp(12); bottomMargin = ui.dp(6) })
    card.addView(ui.segmented(listOf("last" to "Última canción", "none" to "Nada sonando"), Prefs.pwIdle(this)) {
        Prefs.setPwIdle(this, it); Widgets.updateKind(this, Widgets.PLAYER); build()
    })
    return card
}

/** Los 4 widgets del paquete: cuáles tienes puestos y cómo agregar los demás. */
internal fun MainActivity.packPlate(): View {
    val card = ui.plateCard()
    val list = listOf(
        Widgets.has(this, Widgets.FULL) to ("Completo" to "Todo junto: hora, clima, música y apps"),
        Widgets.has(this, Widgets.CLOCK) to ("Hora y clima" to "Día, fecha, hora, clima y batería"),
        Widgets.has(this, Widgets.APPS) to ("Apps" to "Tus apps, se acomodan al tamaño"),
        Widgets.has(this, Widgets.PLAYER) to ("Reproductor" to "La canción con sus botones"),
        MiniWidget.placed(this) to ("Mini 1×1" to "Día, fecha y batería en un cuadrito"),
        WeekWidget.placed(this) to ("Mi semana" to "Cuánto escuchaste y tu artista de la semana"),
        SmartWidget.placed(this) to ("Pila inteligente" to "Música, tu próximo evento o la hora, según el momento"),
        AgendaWidget.placed(this) to ("Agenda" to "Tus eventos de hoy y mañana"))
    val providers = listOf(RockWidget::class.java, ClockWidget::class.java, AppsWidget::class.java, PlayerWidget::class.java,
        MiniWidget::class.java, WeekWidget::class.java, SmartWidget::class.java, AgendaWidget::class.java)
    val placed = list.count { it.first }
    card.addView(ui.sectionTitle("Tus widgets"))
    card.addView(ui.note("Ponlos juntos o por separado; todos usan tu tema."))
    list.forEach { (on, nd) ->
        card.addView(LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(ui.dp(4), ui.dp(8), ui.dp(4), ui.dp(8))
            addView(View(this@packPlate).apply { background = ui.oval(if (on) Ui.OK else Ui.GROOVE) },
                LinearLayout.LayoutParams(ui.dp(9), ui.dp(9)).apply { marginEnd = ui.dp(12) })
            addView(LinearLayout(this@packPlate).apply {
                orientation = LinearLayout.VERTICAL
                addView(ui.text(nd.first, ui.condSemi, 17f, Ui.HEAD))
                addView(ui.text(nd.second, ui.cond, 14f, Ui.DIM))
            }, LinearLayout.LayoutParams(0, -2, 1f))
            // Agregar cualquiera desde aquí (antes solo el Completo); los Apps se pueden poner varias veces
            val kindIdx = list.indexOfFirst { it.second == nd }
            if (on) addView(ui.text("En pantalla", ui.cond, 14f, Ui.OK),
                LinearLayout.LayoutParams(-2, -2).apply { marginStart = ui.dp(12) })
            if (!on || kindIdx == 2) addView(ui.textButton(if (on) "+ Otro" else "Agregar").apply {
                contentDescription = I18n.tr(this@packPlate, "Agregar") + " " + I18n.tr(this@packPlate, nd.first)
                setOnClickListener {
                    val ok = try {
                        val mgr = android.appwidget.AppWidgetManager.getInstance(this@packPlate)
                        mgr.isRequestPinAppWidgetSupported &&
                            mgr.requestPinAppWidget(android.content.ComponentName(this@packPlate, providers[kindIdx]), null, null)
                    } catch (e: Exception) { false }
                    if (!ok) showAddWidgetSteps()
                }
            }, LinearLayout.LayoutParams(-2, ui.dp(48)).apply { marginStart = ui.dp(8) })
        }, LinearLayout.LayoutParams(-1, -2))
    }
    if (placed < list.size) card.addView(ui.secondary("Cómo agregar un widget").apply {
        setOnClickListener { showAddWidgetSteps() }
    }, LinearLayout.LayoutParams(-1, ui.dp(48)).apply { topMargin = ui.dp(10) })
    return card
}

/**
 * Protector de pantalla mientras carga: lo más cercano a la pantalla siempre activa
 * (HyperOS no deja que otras apps pongan cosas en la suya).
 */
internal fun MainActivity.dreamPlate(): View {
    val card = ui.plateCard()
    card.addView(ui.sectionTitle("Pantalla al cargar"))
    card.addView(ui.note("Hora, clima y música mientras el teléfono carga.",
        "Es un protector de pantalla: aparece cuando el teléfono está cargando y se apaga la pantalla (o al ponerlo en su base). " +
        "Muestra tu widget Hora y clima y, si suena música, el reproductor con sus botones. Usa poco brillo y se mueve un poco " +
        "cada minuto para no marcar la pantalla. La pantalla siempre activa de HyperOS no permite widgets de otras apps; esto es lo más parecido."))
    card.addView(ui.text("Fondo", ui.cond, 16f, Ui.HEAD),
        LinearLayout.LayoutParams(-2, -2).apply { topMargin = ui.dp(12); bottomMargin = ui.dp(6) })
    card.addView(ui.segmented(listOf("wall" to "Tu fondo, oscurecido", "black" to "Negro"), Prefs.dreamBg(this)) {
        Prefs.setDreamBg(this, it); build()
    })
    val open = ui.primary("Activarlo en el teléfono")
    open.setOnClickListener {
        val ok = try { startActivity(Intent(android.provider.Settings.ACTION_DREAM_SETTINGS)); true } catch (e: Exception) { false }
        if (!ok) ui.sheet("Activar el protector de pantalla",
            "Tu teléfono no abrió el ajuste directamente. Búscalo así:\n\n" +
            "1. Abre Ajustes y busca «Protector de pantalla» (o «Salvapantallas»).\n" +
            "2. Elige Prisma Widgets.\n" +
            "3. En «Cuándo empezar», elige «Mientras se carga».\n\n" +
            "Si tu versión de HyperOS no tiene esa opción, el teléfono no admite protectores de pantalla.",
            dismissLabel = "Entendido")
    }
    card.addView(open, LinearLayout.LayoutParams(-1, ui.dp(48)).apply { topMargin = ui.dp(14) })
    return card
}


/** Nombre propio para un widget de Apps (solo se ve en estos ajustes). */
internal fun MainActivity.renameAppsWidget(id: Int, current: String) {
    val field = android.widget.EditText(this).apply {
        setText(Prefs.awName(this@renameAppsWidget, id) ?: "")
        hint = "Por ejemplo: Inicio, Trabajo, Música"
        setSingleLine()
        filters = arrayOf(android.text.InputFilter.LengthFilter(20))
        setTextColor(if (Ui.light) 0xFF1C1A17.toInt() else 0xFFF2ECE4.toInt())
        setHintTextColor(Ui.DIM)
    }
    ui.sheet("Nombre de $current", content = field, actions = listOf(
        "Guardar" to { Prefs.setAwName(this, id, field.text.toString()); build() },
        "Quitar el nombre" to { Prefs.setAwName(this, id, null); build() }))
    field.requestFocus()
}
