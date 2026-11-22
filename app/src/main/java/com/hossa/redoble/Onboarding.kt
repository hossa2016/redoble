package com.hossa.redoble

import android.content.Intent
import android.graphics.Color
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.Toast

// Guía de inicio en 3 pasos para quien abre la app por primera vez:
// 1) elegir tema → 2) agregar el widget → 3) permiso para mostrar la música.

internal fun MainActivity.onboardingView(): View {
    val col = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(ui.dp(20), ui.dp(28), ui.dp(20), ui.dp(28))
    }
    val step = onbStep.coerceIn(0, 2)

    // Progreso: 1 · 2 · 3
    col.addView(LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        for (i in 0..2) addView(View(this@onboardingView).apply {
            background = ui.rect(if (i <= step) ui.accent else Ui.GROOVE, 2f)
        }, LinearLayout.LayoutParams(0, ui.dp(4), 1f).apply { if (i > 0) marginStart = ui.dp(6) })
    }, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = ui.dp(24) })

    col.addView(ui.text("Paso ${step + 1} de 3", ui.cond, 15f, Ui.MUTED))
    val (title, body) = when (step) {
        0 -> "Elige tu estilo" to "Siete temas, cada uno con sus propias letras. Puedes cambiarlo cuando quieras."
        1 -> "Agrega el widget" to "Ponlo en tu pantalla de inicio y estíralo a lo ancho para que luzca."
        else -> "Muestra tu música" to "Para ver la canción y los botones ⏮ ⏯ ⏭ en el widget, Android pide el acceso a notificaciones. " +
            "Prisma Widgets solo lo usa para leer la música que suena; no lee ni guarda tus mensajes."
    }
    col.addView(ui.text(title, ui.anton, 30f, Ui.HEAD, 0.02f).apply { setPadding(0, ui.dp(6), 0, ui.dp(10)) })
    col.addView(ui.bodyText(body, Ui.MUTED, 16f))

    val content = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
    when (step) {
        0 -> {
            val cur = Themes.current(this)
            val strip = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            Themes.ALL.forEach { t -> strip.addView(themeTile(t, t.id == cur.id) {
                Prefs.setTheme(this, t.id); RockWidget.updateAll(this); build()
            }) }
            content.addView(HorizontalScrollView(this).apply { isHorizontalScrollBarEnabled = false; addView(strip) })
        }
        1 -> {
            val has = RockWidget.hasWidgets(this)
            val add = if (has) ui.secondary("Widget agregado ✓") else ui.primary("Agregar widget")
            add.setOnClickListener {
                if (!tryPin()) toast("Mantén presionada la pantalla de inicio → Widgets → Prisma Widgets")
            }
            content.addView(add, LinearLayout.LayoutParams(-1, ui.dp(52)))
            content.addView(ui.bodyText("Si tu teléfono no lo permite desde aquí: mantén presionado un espacio vacío de la " +
                "pantalla de inicio, toca Widgets y busca Prisma Widgets.", Ui.DIM, 14f).apply { setPadding(0, ui.dp(12), 0, 0) })
        }
        else -> {
            val on = Player.enabled(this)
            val give = if (on) ui.secondary("Permiso dado ✓") else ui.primary("Dar permiso")
            give.setOnClickListener {
                try { startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) }
                catch (e: Exception) {
                    Toast.makeText(this, "Busca «Acceso a notificaciones» en Ajustes y activa Prisma Widgets.", Toast.LENGTH_LONG).show()
                }
            }
            content.addView(give, LinearLayout.LayoutParams(-1, ui.dp(52)))
            content.addView(ui.bodyText("Puedes hacerlo después desde Ajustes → Reproductor.", Ui.DIM, 14f).apply {
                setPadding(0, ui.dp(12), 0, 0)
            })
        }
    }
    col.addView(content, LinearLayout.LayoutParams(-1, -2).apply { topMargin = ui.dp(24) })

    // Navegación
    val nav = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
    nav.addView(ui.textButton(if (step == 0) "Saltar" else "Atrás").apply {
        setOnClickListener {
            if (step == 0) finishOnboarding() else { onbStep = step - 1; build() }
        }
    }, LinearLayout.LayoutParams(0, ui.dp(52), 1f))
    nav.addView(ui.primary(if (step == 2) "Empezar" else "Siguiente").apply {
        setOnClickListener {
            if (step == 2) finishOnboarding() else { onbStep = step + 1; build() }
        }
    }, LinearLayout.LayoutParams(0, ui.dp(52), 1.4f).apply { marginStart = ui.dp(10) })
    col.addView(nav, LinearLayout.LayoutParams(-1, -2).apply { topMargin = ui.dp(36) })
    return col
}

internal fun MainActivity.finishOnboarding() {
    Prefs.setOnboarded(this, true)
    onbStep = 0
    build()
}

/** Abre la guía de inicio otra vez (desde el pie de los ajustes). */
internal fun MainActivity.restartOnboarding() {
    Prefs.setOnboarded(this, false)
    onbStep = 0
    build()
}

/** Miniatura de un tema con su hora de muestra; la elegida lleva borde y chip "✓". */
internal fun MainActivity.themeTile(t: Theme, on: Boolean, onPick: () -> Unit): View {
    val tw = ui.dp(84); val thH = ui.dp(130)
    val tile = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        gravity = Gravity.CENTER_HORIZONTAL
        setPadding(0, 0, ui.dp(10), 0)
        contentDescription = if (on) "Tema ${t.name}, elegido" else "Tema ${t.name}"
        setOnClickListener { if (!on) onPick() }
    }
    val img = android.widget.ImageView(this).apply {
        scaleType = android.widget.ImageView.ScaleType.CENTER_CROP
        setImageBitmap(if (t.family == "rock") thumb(R.drawable.fondo_bateria) else Themes.wallpaper(t, tw, thH))
        clipToOutline = true
        outlineProvider = object : android.view.ViewOutlineProvider() {
            override fun getOutline(view: View, outline: android.graphics.Outline) {
                outline.setRoundRect(0, 0, view.width, view.height, ui.dp(12).toFloat())
            }
        }
    }
    val frame = android.widget.FrameLayout(this).apply {
        // Borde claro y grueso en el elegido, igual en todos los temas
        foreground = ui.rect(Color.TRANSPARENT, 12f, if (on) Color.WHITE else Color.parseColor("#24FFFFFF"), if (on) 2.5f else 1f)
    }
    frame.addView(img, android.widget.FrameLayout.LayoutParams(tw, thH))
    frame.addView(ui.text("5:43", ui.condLight, 26f, t.head).apply { gravity = Gravity.CENTER },
        android.widget.FrameLayout.LayoutParams(tw, thH))
    frame.addView(View(this).apply { background = ui.rect(t.accent(this@themeTile), 3f) },
        android.widget.FrameLayout.LayoutParams(ui.dp(22), ui.dp(4)).apply {
            gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL; bottomMargin = ui.dp(14)
        })
    if (on) frame.addView(ui.text("✓", ui.condSemi, 13f, Color.parseColor("#14110E")).apply {
        gravity = Gravity.CENTER
        background = ui.oval(Color.WHITE)
    }, android.widget.FrameLayout.LayoutParams(ui.dp(22), ui.dp(22)).apply {
        gravity = Gravity.TOP or Gravity.END; topMargin = ui.dp(6); marginEnd = ui.dp(6)
    })
    tile.addView(frame)
    tile.addView(ui.text(t.name, if (on) ui.condSemi else ui.cond, 14f, if (on) Ui.HEAD else Ui.MUTED).apply {
        gravity = Gravity.CENTER; maxLines = 1
    }, LinearLayout.LayoutParams(tw, -2).apply { topMargin = ui.dp(6) })
    return tile
}
