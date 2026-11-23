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
    card.addView(ui.sectionTitle("Hora y clima"))
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
    card.addView(ui.note("Se acomodan solos al tamaño. Toca uno para cambiar su grupo o su tamaño."))
    ids.forEachIndexed { n, id ->
        if (n > 0) card.addView(View(this).apply { setBackgroundColor(Ui.veil(0x0F)) }, LinearLayout.LayoutParams(-1, ui.dp(1)))
        card.addView(LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = ui.rowBg()
            setPadding(ui.dp(6), 0, ui.dp(6), 0)
            addView(LinearLayout(this@appsWidgetsPlate).apply {
                orientation = LinearLayout.VERTICAL
                addView(ui.text(if (ids.size == 1) "Tu widget de Apps" else "Widget ${n + 1}", ui.cond, 18f, Ui.HEAD))
                addView(ui.ellipsize(ui.text(Widgets.appsSummary(this@appsWidgetsPlate, id), ui.cond, 14f, Ui.DIM)))
            }, LinearLayout.LayoutParams(0, -2, 1f))
            addView(ui.text("Cambiar", ui.cond, 15f, ui.accent))
            addView(ui.text("›", ui.condLight, 22f, Ui.DIM).apply { setPadding(ui.dp(8), 0, 0, ui.dp(2)) })
            contentDescription = "Cambiar widget ${n + 1}"
            setOnClickListener {
                startActivity(Intent(this@appsWidgetsPlate, AppsWidgetConfig::class.java)
                    .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id))
            }
        }, LinearLayout.LayoutParams(-1, ui.dp(64)))
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
        Widgets.FULL to ("Completo" to "Todo junto: hora, clima, música y apps"),
        Widgets.CLOCK to ("Hora y clima" to "Día, fecha, hora, clima y batería"),
        Widgets.APPS to ("Apps" to "Tus apps, se acomodan al tamaño"),
        Widgets.PLAYER to ("Reproductor" to "La canción con sus botones"))
    val placed = list.count { Widgets.has(this, it.first) }
    card.addView(ui.sectionTitle("Paquete de widgets"))
    card.addView(ui.note("Ponlos juntos o por separado; todos usan tu tema."))
    list.forEach { (kind, nd) ->
        val on = Widgets.has(this, kind)
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
            addView(ui.text(if (on) "Puesto" else "", ui.cond, 14f, Ui.OK))
        }, LinearLayout.LayoutParams(-1, -2))
    }
    if (placed < list.size) card.addView(ui.secondary("Cómo agregar un widget").apply {
        setOnClickListener { showAddWidgetSteps() }
    }, LinearLayout.LayoutParams(-1, ui.dp(48)).apply { topMargin = ui.dp(10) })
    return card
}
