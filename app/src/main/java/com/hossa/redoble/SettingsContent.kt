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
    card.addView(ui.sectionTitle("Clima"))
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
    val save = ui.primary("Guardar")
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
                var result: Triple<String, Double, Double>? = null
                var offline = false
                try { result = WeatherWorker.geocode(name) } catch (e: IOException) { offline = true } catch (e: Exception) { }
                onUi {
                    val r = result
                    when {
                        offline -> setStatus(Ui.ALERT, null, "Sin conexión. Inténtalo cuando vuelva la red.")
                        r == null -> setStatus(Ui.ALERT, null, "No encontramos esa ciudad. Revisa la ortografía o agrega el departamento.")
                        else -> {
                            Prefs.setCity(this, r.first, r.second, r.third)
                            field.setText(r.first)
                            setStatus(Ui.DIM, r.first, " Consultando el clima…")
                            WeatherWorker.runNow(this, force = true)
                            WeatherWorker.schedule(this)
                            RockWidget.updateAll(this)
                            onUiDelayed(5000) { refreshCityStatus() }
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
    val used = Prefs.usedSlots(this)
    val filled = (0 until used).count { Prefs.app(this, it).isNotBlank() }
    card.addView(ui.sectionTitle("Apps", "$filled de $used · ${Prefs.perRow(this)} por fila"))
    card.addView(ui.bodyText("Mantén presionada una app para renombrarla.", Ui.MUTED, 13f).apply {
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
        card.addView(slotRow(i), LinearLayout.LayoutParams(-1, ui.dp(56)))
    }
    return card
}

internal fun MainActivity.renameSlot(slot: Int) {
    val pkg = Prefs.app(this, slot)
    val original = Apps.label(this, pkg)
    val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(ui.dp(20), ui.dp(8), ui.dp(20), 0) }
    val field = EditText(this).apply {
        setText(Prefs.label(this@renameSlot, slot) ?: original)
        setSelectAllOnFocus(true)
        setSingleLine()
        filters = arrayOf(android.text.InputFilter.LengthFilter(14))
    }
    box.addView(ui.bodyText("Nombre original: $original", Ui.MUTED, 13f))
    box.addView(field)
    androidx.appcompat.app.AlertDialog.Builder(this)
        .setTitle("Nombre del acceso %d".format(slot + 1))
        .setView(box)
        .setPositiveButton("Guardar") { _, _ ->
            val t = field.text.toString().trim()
            Prefs.setLabel(this, slot, if (t == original) null else t)
            RockWidget.updateAll(this); build()
        }
        .setNeutralButton("Original") { _, _ ->
            Prefs.setLabel(this, slot, null); RockWidget.updateAll(this); build()
        }
        .setNegativeButton("Cancelar", null)
        .show()
    field.requestFocus()
}

internal fun MainActivity.slotRow(i: Int): View {
    val pkg = Prefs.app(this, i)
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
        addView(ui.ellipsize(ui.text(if (empty) "Espacio libre" else Prefs.displayName(this@slotRow, i), ui.cond, 19f,
            if (empty) Ui.DIM else Ui.HEAD)),
            LinearLayout.LayoutParams(0, -2, 1f).apply { marginStart = ui.dp(14) })
        addView(ui.text(if (empty) "Elegir app" else "Cambiar", ui.cond, 15f, if (empty) ui.accent else Ui.MUTED))
        addView(ui.text("›", ui.condLight, 22f, Ui.DIM).apply { setPadding(ui.dp(8), 0, 0, ui.dp(2)) })
        if (!empty) setOnLongClickListener { renameSlot(i); true }
        setOnClickListener {
            AppPicker(this@slotRow, ui, i) {
                RockWidget.updateAll(this@slotRow)
                build()
            }.show()
        }
    }
}

internal fun MainActivity.designPlate(): View {
    val card = ui.plateCard()
    val preset = Prefs.preset(this)
    val names = mapOf("completo" to "Completo", "musica" to "Música", "minimal" to "Minimal")
    card.addView(ui.sectionTitle("Diseño", names[preset] ?: "Personalizado"))
    card.addView(ui.segmented(names.entries.map { it.key to it.value }, preset) { k ->
        Prefs.applyPreset(this, k); RockWidget.updateAll(this); build()
    })
    val pr = Prefs.perRow(this)
    val (short, longDesc) = when (preset) {
        "completo" -> "Hora, clima, batería y ${pr * 2} apps." to "Hora, clima, batería y ${pr * 2} apps. Cuando suena música, el reproductor aparece compacto y queda una fila de $pr apps para que no se apriete."
        "musica" -> "Reproductor grande y $pr apps." to "Reproductor grande siempre a la vista, cabecera compacta y $pr apps."
        "minimal" -> "Día, hora, clima y $pr apps." to "Solo día, hora, clima y $pr apps. Se luce el fondo."
        else -> "Elementos elegidos por ti." to "Elementos elegidos por ti. Se respeta el número de apps aunque suene música."
    }
    card.addView(ui.note(short, longDesc), LinearLayout.LayoutParams(-1, -2).apply { topMargin = ui.dp(10) })

    val more = ui.expander("Personalizar elementos", customOpen) { customOpen = !customOpen; build() }
    card.addView(more, LinearLayout.LayoutParams(-1, -2).apply { topMargin = ui.dp(8) })

    if (customOpen) {
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = ui.rect(Ui.WELL, 12f, Ui.veil(0x12), 1f)
            setPadding(ui.dp(14), ui.dp(4), ui.dp(10), ui.dp(8))
        }
        val labels = listOf("dia" to "Día y fecha", "hora" to "Hora", "clima" to "Clima actual",
            "manana" to "Pronóstico de mañana", "bateria" to "Batería", "player" to "Reproductor (cuando suena música)")
        labels.forEach { (k, label) ->
            box.addView(switchRow(label, Prefs.el(this, k)) { on ->
                Prefs.setEl(this, k, on); RockWidget.updateAll(this); build()
            })
        }
        box.addView(switchRow("Reproductor grande", Prefs.playerLarge(this)) { on ->
            Prefs.setPlayerLarge(this, on); RockWidget.updateAll(this); build()
        })
        // Apps: 0 / 3 / 6
        box.addView(ui.text("Apps", ui.cond, 16f, Ui.HEAD), LinearLayout.LayoutParams(-2, -2).apply { topMargin = ui.dp(8); bottomMargin = ui.dp(6) })
        box.addView(ui.segmented(listOf(0 to "Ninguna", 3 to "1 fila", 6 to "2 filas"), Prefs.appRows(this) * 3) { v ->
            Prefs.setAppsCount(this, v); RockWidget.updateAll(this); build()
        }, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = ui.dp(4) })
        card.addView(box, LinearLayout.LayoutParams(-1, -2).apply { topMargin = ui.dp(4) })
    }
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
        trackTintList = android.content.res.ColorStateList(states, intArrayOf(ui.accent, if (Ui.light) Color.parseColor("#B9AFA3") else Color.parseColor("#3A322B")))
        setOnCheckedChangeListener { _, v -> onChange(v) }
    }
    row.addView(sw)
    row.setOnClickListener { sw.toggle() }
    return row
}
