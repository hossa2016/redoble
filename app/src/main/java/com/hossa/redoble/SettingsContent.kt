package com.hossa.redoble

import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.text.style.TypefaceSpan
import android.view.Gravity
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.LinearLayout
import java.io.IOException

// Ajustes · Contenido: diseño (elementos), apps del widget y clima.
// Son extensiones de MainActivity para no tener un solo archivo de 1200 líneas.

internal fun MainActivity.weatherPlate(): View {
    val card = ui.plateCard()
    card.addView(ui.sectionTitle("Ubicación del clima"))
    val field = EditText(this).apply {
        setText(Prefs.city(this@weatherPlate))
        hint = "Ciudad, por ejemplo Medellín"
        setHintTextColor(Ui.DIM)
        setTextColor(Ui.HEAD)
        typeface = ui.body
        textSize = 16f
        background = ui.field()
        setSingleLine()
        imeOptions = EditorInfo.IME_ACTION_DONE
        setPadding(ui.dp(14), 0, ui.dp(14), 0)
        if (Build.VERSION.SDK_INT >= 29) {
            textCursorDrawable = GradientDrawable().apply { setColor(ui.accent); setSize(ui.dp(1.5f), ui.dp(20)) }
        }
    }
    // Acción secundaria: el relleno del acento queda para la acción principal de cada pantalla
    val save = ui.secondary("Guardar")
    // Campo a todo el ancho y Guardar debajo: con letra grande nada se corta
    card.addView(field, LinearLayout.LayoutParams(-1, ui.dp(52)))
    card.addView(save, LinearLayout.LayoutParams(-1, ui.dp(48)).apply { topMargin = ui.dp(10) })
    val dot = ui.dot(Ui.DIM)
    val status = ui.bodyText("")
    cityDot = dot; cityStatus = status
    card.addView(LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.TOP
        addView(dot, LinearLayout.LayoutParams(ui.dp(7), ui.dp(7)).apply { topMargin = ui.dp(7); marginEnd = ui.dp(10) })
        addView(status, LinearLayout.LayoutParams(0, -2, 1f))
    }, LinearLayout.LayoutParams(-1, -2).apply { topMargin = ui.dp(12) })
    refreshCityStatus()

    val doSave = {
        val name = field.text.toString().trim()
        if (name.isEmpty()) {
            setStatus(Ui.ALERT, null, "Escribe el nombre de tu ciudad.")
        } else {
            setStatus(Ui.DIM, null, "Buscando $name…")
            io.execute {
                var results: List<Triple<String, Double, Double>> = emptyList()
                var offline = false
                try { WeatherWorker.geoLang = I18n.lang(this); results = WeatherWorker.geocodeAll(name) } catch (e: IOException) { offline = true } catch (e: Exception) { }
                onUi {
                    val use = { r: Triple<String, Double, Double> ->
                        Prefs.setCity(this, r.first, r.second, r.third)
                        field.setText(r.first)
                        setStatus(Ui.DIM, r.first, " Consultando el clima…")
                        WeatherWorker.runNow(this, force = true)
                        WeatherWorker.schedule(this)
                        RockWidget.updateAll(this)
                        onUiDelayed(5000) { refreshCityStatus() }
                    }
                    when {
                        offline -> setStatus(Ui.ALERT, null, "Sin conexión. Inténtalo cuando vuelva la red.")
                        results.isEmpty() -> setStatus(Ui.ALERT, null, "No encontramos esa ciudad. Revisa la ortografía o agrega el departamento.")
                        results.size == 1 -> use(results[0])
                        else -> {
                            // Varias ciudades con ese nombre: se elige la correcta
                            setStatus(Ui.DIM, null, "Elige tu ciudad.")
                            ui.sheet("¿Cuál $name?", items = results.map { it.first }, onItem = { i -> use(results[i]) })
                        }
                    }
                }
            }
        }
    }
    save.setOnClickListener { doSave() }
    field.setOnEditorActionListener { _, action, _ ->
        if (action == EditorInfo.IME_ACTION_DONE) { doSave(); true } else false
    }

    // Pronóstico de los próximos días
    val fc = Prefs.forecast(this).split("|").mapNotNull { d -> d.split(";").takeIf { it.size == 4 } }
    if (fc.isNotEmpty()) {
        card.addView(ui.label("PRÓXIMOS DÍAS"), LinearLayout.LayoutParams(-2, -2).apply { topMargin = ui.dp(16); bottomMargin = ui.dp(6) })
        // Lista vertical: una fila de 44 dp por día (día · estado · mín/máx a la derecha)
        fc.forEach { d ->
            val desc = I18n.tr(this, WeatherWorker.describe(d[1].toIntOrNull() ?: -1))
            card.addView(LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                minimumHeight = ui.dp(44)
                addView(ui.text(d[0], ui.condSemi, 17f, Ui.HEAD), LinearLayout.LayoutParams(ui.dp(64), -2))
                addView(ui.text(desc, ui.cond, 15f, Ui.MUTED).apply { maxLines = 1; ellipsize = android.text.TextUtils.TruncateAt.END },
                    LinearLayout.LayoutParams(0, -2, 1f))
                addView(ui.text("${d[2]}°  ·  ${d[3]}°", ui.cond, 17f, Ui.HEAD))
                contentDescription = "${d[0]}: $desc, ${d[2]}° – ${d[3]}°"
            }, LinearLayout.LayoutParams(-1, -2))
        }
        val (rise, set) = Prefs.sunTimes(this)
        if (rise.isNotBlank()) card.addView(ui.bodyText("Amanecer $rise · Atardecer $set", Ui.MUTED, 14f),
            LinearLayout.LayoutParams(-1, -2).apply { topMargin = ui.dp(10) })
    }
    // Unidad y voz
    card.addView(ui.segmented(listOf(false to "°C Celsius", true to "°F Fahrenheit"), Prefs.tempF(this)) { f ->
        Prefs.setTempF(this, f); WeatherWorker.runNow(this, force = true); onUiDelayed(4000) { build() }; build()
    }, LinearLayout.LayoutParams(-1, -2).apply { topMargin = ui.dp(14) })
    return card
}

internal fun MainActivity.setStatus(dotColor: Int, city: String?, rest: String) {
    cityDot?.background = ui.oval(dotColor)
    val sb = SpannableStringBuilder()
    if (city != null) {
        sb.append("$city.")
        sb.setSpan(ForegroundColorSpan(Ui.HEAD), 0, sb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        if (Build.VERSION.SDK_INT >= 28) {
            sb.setSpan(TypefaceSpan(ui.condMedium), 0, sb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
    }
    sb.append(rest)
    cityStatus?.text = sb
}

internal fun MainActivity.refreshCityStatus() {
    val city = Prefs.city(this)
    val at = Prefs.weatherAt(this)
    when {
        city.isBlank() -> setStatus(Ui.DIM, null, "Escribe tu ciudad para ver el clima en el widget.")
        at == 0L -> setStatus(Ui.DIM, city, " Esperando el primer dato del clima.")
        else -> {
            val min = ((System.currentTimeMillis() - at) / 60000).toInt()
            val ago = when {
                min < 1 -> "hace un momento"
                min < 60 -> "hace $min min"
                else -> "hace ${min / 60} h"
            }
            setStatus(Ui.OK, city, " Actualizado $ago: ${Prefs.weatherNow(this).lowercase()}.")
        }
    }
}

internal fun MainActivity.setlistPlate(): View {
    val card = ui.plateCard()
    // Con widgets de Apps puestos se editan también los grupos 2 y 3, y hay más lugares
    val appsWidget = Widgets.has(this, Widgets.APPS)
    val group = if (appsWidget) appsGroup else 0
    val extraFilled = appsWidget && (8 until Prefs.GROUP_SLOTS).any { Prefs.app(this, it, group).isNotBlank() }
    val showAll = moreSlots || extraFilled
    val used = if (appsWidget) (if (showAll) Prefs.GROUP_SLOTS else 8) else Prefs.usedSlots(this)
    // Sin huecos: las apps suben a los primeros lugares (el número de la fila es su posición real)
    if (Prefs.compactApps(this, group, used)) RockWidget.updateAll(this)
    val filled = (0 until used).count { Prefs.app(this, it, group).isNotBlank() }
    card.addView(ui.sectionTitle("Apps", if (appsWidget) "${Prefs.GROUP_NAMES[group]} · $filled de $used"
        else "$filled de $used · ${Prefs.perRow(this)} por fila"))
    if (appsWidget) {
        card.addView(ui.segmented(Prefs.GROUP_NAMES.indices.map { it to Prefs.GROUP_NAMES[it] }, group) { appsGroup = it; build() },
            LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = ui.dp(8) })
        card.addView(ui.note(if (group == 0) "«Mis apps» también las usa el widget Completo."
            else "Elige este grupo en un widget de Apps con «Cambiar», en Tus widgets."),
            LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = ui.dp(10) })
    }
    card.addView(ui.bodyText("Toca una app para cambiarla, renombrarla o quitarla.", Ui.MUTED, 13f).apply {
        setPadding(0, 0, 0, ui.dp(8))
    })

    // Íconos: silueta o color original de cada app
    card.addView(ui.segmented(listOf(0 to "Silueta", 1 to "Color original"), Prefs.iconMode(this)) { m ->
        Prefs.setIconMode(this, m); RockWidget.updateAll(this); build()
    }, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = ui.dp(10) })
    // Cuántas por fila: 3 (más grandes) o 4 (más apps a la mano)
    card.addView(ui.segmented(listOf(3 to "3 por fila", 4 to "4 por fila"), Prefs.perRow(this)) { n ->
        Prefs.setPerRow(this, n); RockWidget.updateAll(this); build()
    }, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = ui.dp(12) })
    for (i in 0 until used) {
        if (i > 0) card.addView(View(this).apply { setBackgroundColor(Ui.veil(0x0F)) },
            LinearLayout.LayoutParams(-1, ui.dp(1)))
        card.addView(slotRow(i, group), LinearLayout.LayoutParams(-1, ui.dp(56)))
    }
    if (appsWidget && !extraFilled) card.addView(ui.textButton(if (moreSlots) "Mostrar menos lugares" else "Mostrar ${Prefs.GROUP_SLOTS - 8} lugares más").apply {
        setOnClickListener { moreSlots = !moreSlots; build() }
    }, LinearLayout.LayoutParams(-1, ui.dp(48)).apply { topMargin = ui.dp(6) })
    return card
}

internal fun MainActivity.renameSlot(slot: Int, group: Int = 0) {
    val pkg = Prefs.app(this, slot, group)
    val original = Apps.label(this, pkg)
    val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
    val field = EditText(this).apply {
        setText(Prefs.label(this@renameSlot, slot, group) ?: original)
        setSelectAllOnFocus(true)
        setSingleLine()
        filters = arrayOf(android.text.InputFilter.LengthFilter(14))
        // Legible en la ventana clara u oscura
        setTextColor(if (Ui.light) 0xFF1C1A17.toInt() else 0xFFF2ECE4.toInt())
    }
    box.addView(ui.bodyText("Nombre original: $original", Ui.MUTED, 13f))
    box.addView(field)
    ui.sheet("Nombre en el widget", content = box, actions = listOf(
        "Guardar" to {
            val t = field.text.toString().trim()
            Prefs.setLabel(this, slot, if (t == original) null else t, group)
            RockWidget.updateAll(this); Widgets.updateKind(this, Widgets.APPS); build()
        },
        "Usar el original" to {
            Prefs.setLabel(this, slot, null, group); RockWidget.updateAll(this); Widgets.updateKind(this, Widgets.APPS); build()
        }))
    field.requestFocus()
}

internal fun MainActivity.slotRow(i: Int, group: Int = 0): View {
    val pkg = Prefs.app(this, i, group)
    val empty = pkg.isBlank()
    return LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        background = ui.rowBg()
        setPadding(ui.dp(6), 0, ui.dp(6), 0)
        addView(ui.text("%02d".format(i + 1), ui.condMedium, 14f, ui.accent), LinearLayout.LayoutParams(ui.dp(20), -2))
        addView(ui.appHolder(pkg, 38, 19).apply {
            (layoutParams as LinearLayout.LayoutParams).marginStart = ui.dp(10)
        })
        addView(ui.ellipsize(ui.text(if (empty) "Espacio libre" else Prefs.displayName(this@slotRow, i, group), ui.cond, 19f,
            if (empty) Ui.DIM else Ui.HEAD)),
            LinearLayout.LayoutParams(0, -2, 1f).apply { marginStart = ui.dp(14) })
        // Una sola acción por fila: vacía, elegir app; con app, una hoja con las opciones
        if (empty) addView(ui.text("Elegir app", ui.cond, 15f, ui.accent))
        addView(ui.text("›", ui.condLight, 22f, Ui.DIM).apply { setPadding(ui.dp(8), 0, 0, ui.dp(2)) })
        val name = if (empty) "" else Prefs.displayName(this@slotRow, i, group)
        contentDescription = if (empty) "Espacio ${i + 1} libre, elegir app" else "$name, espacio ${i + 1}, ver opciones"
        val pick = {
            AppPicker(this@slotRow, ui, i, group) {
                RockWidget.updateAll(this@slotRow)
                build()
            }.show()
        }
        setOnClickListener {
            if (empty) { pick(); return@setOnClickListener }
            ui.sheet(name, items = listOf("Cambiar app", "Renombrar", "Quitar del widget"), onItem = { which ->
                when (which) {
                    0 -> pick()
                    1 -> renameSlot(i, group)
                    else -> ui.sheet("¿Quitar $name?", "Las apps que siguen suben un lugar para no dejar huecos.",
                        actions = listOf("Quitar" to {
                            // Se corre todo un lugar hacia arriba (con sus nombres propios)
                            // Solo los lugares que usa este grupo (sin widget de Apps, los del Completo)
                            val last = (if (Widgets.has(this@slotRow, Widgets.APPS)) Prefs.GROUP_SLOTS
                                        else Prefs.usedSlots(this@slotRow)) - 1
                            for (k in i until last) {
                                val nextPkg = Prefs.rawApp(this@slotRow, k + 1, group)
                                val nextLabel = Prefs.label(this@slotRow, k + 1, group)
                                Prefs.setApp(this@slotRow, k, nextPkg, group)
                                Prefs.setLabel(this@slotRow, k, nextLabel, group)
                            }
                            Prefs.setApp(this@slotRow, last, "", group)
                            RockWidget.updateAll(this@slotRow)  // también redibuja los widgets de Apps
                            build()
                            toast("$name quitada del widget")
                        }))
                }
            })
        }
    }
}

internal fun MainActivity.designPlate(): View {
    val card = ui.plateCard()
    val preset = Prefs.preset(this)
    val names = mapOf("completo" to "Todo", "musica" to "Música grande", "minimal" to "Mínimo")
    card.addView(ui.sectionTitle("Diseño del Completo", names[preset] ?: "Personalizado"))
    card.addView(ui.segmented(names.entries.map { it.key to it.value }, preset) { k ->
        val touched = Prefs.editorTouched(this)
        Prefs.applyPreset(this, k); RockWidget.updateAll(this); build()
        if (touched) toast("Plantilla aplicada: se quitaron los cambios del editor")
    })
    val pr = Prefs.perRow(this)
    val (short, longDesc) = when (preset) {
        "completo" -> "Hora, clima, batería y hasta ${pr * 2} apps." to "Hora, clima, batería y hasta ${pr * 2} apps. Cuando suena música, el reproductor aparece compacto y queda una fila de $pr apps para que no se apriete."
        "musica" -> "Reproductor grande y hasta $pr apps." to "Reproductor grande siempre a la vista, cabecera compacta y $pr apps."
        "minimal" -> "Día, hora, clima y hasta $pr apps." to "Solo día, hora, clima y $pr apps. Se luce el fondo."
        else -> "Elementos elegidos por ti." to "Elementos elegidos por ti. Se respeta el número de apps aunque suene música."
    }
    card.addView(ui.note(short, longDesc), LinearLayout.LayoutParams(-1, -2).apply { topMargin = ui.dp(10) })

    // Un solo lugar para ajustar cada parte: el editor visual
    card.addView(ui.actionRow("Ajustar cada parte", R.drawable.ic_edit, "Mostrar, ordenar, tamaño, letra y color").apply {
        setOnClickListener { openEditor() }
    }, LinearLayout.LayoutParams(-1, -2).apply { topMargin = ui.dp(8) })
    return card
}

internal fun MainActivity.switchRow(label: String, on: Boolean, onChange: (Boolean) -> Unit): View {
    val row = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        minimumHeight = ui.dp(48)
    }
    row.addView(ui.text(label, ui.cond, 16f, Ui.HEAD), LinearLayout.LayoutParams(0, -2, 1f))
    val sw = androidx.appcompat.widget.SwitchCompat(this).apply {
        isChecked = on
        val states = arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf())
        thumbTintList = android.content.res.ColorStateList(states, intArrayOf(Color.WHITE, if (Ui.light) Color.WHITE else Ui.MUTED))
        trackTintList = android.content.res.ColorStateList(states, intArrayOf(ui.accent, if (Ui.light) Color.parseColor("#857B71") else Color.parseColor("#6E655C")))
        setOnCheckedChangeListener { b, v -> b.tick(); onChange(v) }
    }
    row.addView(sw)
    row.setOnClickListener { sw.toggle() }
    // TalkBack: la fila entera es un interruptor con su nombre y su estado (una sola parada)
    sw.importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
    row.isFocusable = true
    androidx.core.view.ViewCompat.setAccessibilityDelegate(row, object : androidx.core.view.AccessibilityDelegateCompat() {
        override fun onInitializeAccessibilityNodeInfo(host: View, info: androidx.core.view.accessibility.AccessibilityNodeInfoCompat) {
            super.onInitializeAccessibilityNodeInfo(host, info)
            info.className = android.widget.Switch::class.java.name
            info.isCheckable = true
            info.isChecked = sw.isChecked
            info.text = I18n.tr(this@switchRow, label)
        }
    })
    sw.setOnCheckedChangeListener(null)
    sw.setOnCheckedChangeListener { b, v -> b.tick(); onChange(v); row.sendAccessibilityEvent(android.view.accessibility.AccessibilityEvent.TYPE_VIEW_CLICKED) }
    return row
}


/** Cabecera: fecha estilo Cartel de Gira, próxima alarma y próximo evento de la agenda. */
internal fun MainActivity.headerExtrasPlate(): View {
    val card = ui.plateCard()
    card.addView(ui.sectionTitle("Fecha, alarma y agenda"))
    card.addView(switchRow("Fecha estilo cartel", Prefs.poster(this)) { on ->
        Prefs.setPoster(this, on); RockWidget.updateAll(this); build()
    })
    card.addView(ui.note("«${Extras.posterLine(this)}», como un afiche de concierto."))
    card.addView(switchRow("Próxima alarma", Prefs.showAlarm(this)) { on ->
        Prefs.setShowAlarm(this, on); RockWidget.updateAll(this); build()
    }, LinearLayout.LayoutParams(-1, -2).apply { topMargin = ui.dp(6) })
    card.addView(ui.note("Si suena en las próximas 24 horas."))
    card.addView(switchRow("Próximo evento de la agenda", Prefs.showAgenda(this) && Extras.hasCalendar(this)) { on ->
        if (on && !Extras.hasCalendar(this)) calendarPerm.launch(android.Manifest.permission.READ_CALENDAR)
        else { Prefs.setShowAgenda(this, on); RockWidget.updateAll(this); build() }
    }, LinearLayout.LayoutParams(-1, -2).apply { topMargin = ui.dp(6) })
    card.addView(ui.note("Pide permiso para leer tu calendario.", "Solo se lee en el teléfono; nada sale de él."))
    return card
}
