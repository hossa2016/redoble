package com.hossa.redoble

import android.animation.LayoutTransition
import android.app.WallpaperManager
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Outline
import android.graphics.Shader
import android.graphics.drawable.ClipDrawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.OpenableColumns
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.text.style.TypefaceSpan
import android.view.Gravity
import android.view.View
import android.view.ViewOutlineProvider
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowInsetsControllerCompat
import java.io.File
import java.io.IOException
import java.util.concurrent.Executors

class MainActivity : AppCompatActivity() {

    private val io = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())

    private lateinit var ui: Ui
    private var scroll: ScrollView? = null
    private var photo: Bitmap? = null
    private var cityStatus: TextView? = null
    private var cityDot: View? = null
    private var pinRequestedAt = 0L

    private val photoFile get() = File(filesDir, "fondo.jpg")

    private val picker = registerForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) loadPhoto(uri)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowInsetsControllerCompat(window, window.decorView).isAppearanceLightStatusBars = false
        if (photoFile.exists()) photo = BitmapFactory.decodeFile(photoFile.path)
        build()
    }

    override fun onResume() {
        super.onResume()
        RockWidget.updateAll(this)
        build()
    }

    // ==================================================================
    // Construcción de la pantalla
    // ==================================================================
    private fun build() {
        val y = scroll?.scrollY ?: 0
        ui = Ui(this, Prefs.accent(this))

        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, ui.dp(14), 0, ui.dp(40))
        }
        col.addView(topBar())
        col.addView(wallpaperPlate())
        col.addView(accentPlate())
        col.addView(weatherPlate())
        col.addView(setlistPlate())
        col.addView(widgetPlate())
        col.addView(ui.text("Redoble 2.0", ui.cond, 13f, Ui.DIM).apply { gravity = Gravity.CENTER },
            LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                topMargin = ui.dp(6)
            })

        val sv = ScrollView(this).apply {
            setBackgroundColor(Ui.INK)
            isFillViewport = true
            addView(col)
            fitsSystemWindows = true
        }
        scroll = sv
        setContentView(sv)
        sv.post { sv.scrollTo(0, y) }
    }

    private fun topBar(): View {
        val brand = ui.text("Redoble", ui.anton, 34f, Ui.HEAD, 0.03f).apply {
            setShadowLayer(ui.dp(3).toFloat(), 0f, ui.dp(2).toFloat(), Color.parseColor("#E6000000"))
        }
        brand.post {
            val w = brand.paint.measureText(brand.text.toString())
            brand.paint.shader = LinearGradient(0f, 0f, w, 0f, Graphics.METAL_COLORS, Graphics.METAL_STOPS, Shader.TileMode.CLAMP)
            brand.invalidate()
        }
        val left = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(brand)
            addView(ui.text("Ajustes del widget", ui.cond, 15f, Ui.MUTED).apply { setPadding(0, ui.dp(4), 0, 0) })
        }
        val active = RockWidget.hasWidgets(this)
        val status = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            addView(ui.dot(if (active) ui.accent else Ui.DIM), LinearLayout.LayoutParams(ui.dp(7), ui.dp(7)))
            addView(ui.text(if (active) "Widget activo" else "Widget sin agregar", ui.cond, 14f, Ui.MUTED).apply {
                setPadding(ui.dp(8), 0, 0, 0)
            })
        }
        return LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(ui.dp(20), ui.dp(14), ui.dp(20), ui.dp(18))
            addView(left, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            addView(status)
        }
    }

    // ---------------- Fondo ----------------
    private fun wallpaperPlate(): View {
        val card = ui.plateCard()
        card.addView(ui.sectionTitle("Fondo"))

        val dim = Prefs.dim(this)
        val preview = FrameLayout(this).apply {
            clipToOutline = true
            outlineProvider = object : ViewOutlineProvider() {
                override fun getOutline(view: View, outline: Outline) {
                    outline.setRoundRect(0, 0, view.width, view.height, ui.dp(12).toFloat())
                }
            }
        }
        val overlay = View(this).apply { setBackgroundColor(Color.BLACK); alpha = dim / 100f }
        val p = photo
        if (p != null) {
            preview.background = ui.rect(Ui.WELL, 12f)
            preview.addView(ImageView(this).apply {
                scaleType = ImageView.ScaleType.CENTER_CROP
                setImageBitmap(p)
            }, FrameLayout.LayoutParams(-1, -1))
            preview.addView(overlay, FrameLayout.LayoutParams(-1, -1))
            val name = getSharedPreferences("redoble", MODE_PRIVATE).getString("photoName", "Tu foto") ?: "Tu foto"
            preview.addView(ui.ellipsize(ui.text(name, ui.cond, 14f, Ui.HEAD)).apply {
                setShadowLayer(ui.dp(3).toFloat(), 0f, 0f, Color.BLACK)
            }, FrameLayout.LayoutParams(-2, -2, Gravity.BOTTOM or Gravity.START).apply {
                setMargins(ui.dp(12), 0, ui.dp(12), ui.dp(10))
            })
        } else {
            preview.background = GradientDrawable().apply {
                setColor(Ui.WELL); cornerRadius = ui.dp(12).toFloat()
                setStroke(ui.dp(1.5f), Color.parseColor("#47EDE6DC"), ui.dp(6).toFloat(), ui.dp(4).toFloat())
            }
            preview.addView(ui.text("Elige una foto de tu galería", ui.cond, 16f, Ui.MUTED).apply { gravity = Gravity.CENTER },
                FrameLayout.LayoutParams(-1, -1))
        }
        card.addView(preview, LinearLayout.LayoutParams(-1, ui.dp(150)))

        // Fader
        val value = ui.text("$dim %", ui.condMedium, 18f, Ui.HEAD)
        card.addView(LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            addView(ui.text("Oscurecer", ui.cond, 16f, Ui.HEAD), LinearLayout.LayoutParams(0, -2, 1f))
            addView(value)
        }, LinearLayout.LayoutParams(-1, -2).apply { topMargin = ui.dp(18) })

        val fader = fader()
        fader.progress = dim
        fader.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar, v: Int, fromUser: Boolean) {
                value.text = "$v %"
                overlay.alpha = v / 100f
            }
            override fun onStartTrackingTouch(sb: SeekBar) {}
            override fun onStopTrackingTouch(sb: SeekBar) = Prefs.setDim(this@MainActivity, sb.progress)
        })
        card.addView(fader, LinearLayout.LayoutParams(-1, ui.dp(40)))
        card.addView(LinearLayout(this).apply {
            setPadding(ui.dp(9), 0, ui.dp(9), 0)
            addView(ui.text("0", ui.cond, 12f, Ui.DIM), LinearLayout.LayoutParams(0, -2, 1f))
            addView(ui.text("85", ui.cond, 12f, Ui.DIM))
        })

        // Botones
        val pick = ui.secondary(if (photo == null) "Elegir foto" else "Cambiar foto").apply {
            setOnClickListener { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }
        }
        val apply = ui.primary("Aplicar fondo")
        apply.setOnClickListener { applyWallpaper(apply) }
        card.addView(LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            addView(pick, LinearLayout.LayoutParams(0, ui.dp(48), 1f))
            addView(apply, LinearLayout.LayoutParams(0, ui.dp(48), 1f).apply { marginStart = ui.dp(10) })
        }, LinearLayout.LayoutParams(-1, -2).apply { topMargin = ui.dp(14) })
        card.addView(ui.bodyText("Se aplica a la pantalla de inicio y a la de bloqueo.", Ui.DIM, 13f).apply {
            setPadding(0, ui.dp(10), 0, 0)
        })
        return card
    }

    private fun fader(): SeekBar {
        val track = ui.rect(Color.parseColor("#070605"), 3f, Color.parseColor("#0FFFFFFF"), 1f)
        val fill = ClipDrawable(ui.rect(ui.accent, 3f), Gravity.START, ClipDrawable.HORIZONTAL)
        val progress = LayerDrawable(arrayOf(track, fill)).apply {
            setId(0, android.R.id.background)
            setId(1, android.R.id.progress)
        }

        val w = ui.dp(22); val h = ui.dp(34)
        val shadow = GradientDrawable().apply {
            setColor(Color.parseColor("#CC000000")); cornerRadius = ui.dp(5).toFloat(); setSize(w, h)
        }
        val cap = GradientDrawable(
            GradientDrawable.Orientation.LEFT_RIGHT,
            intArrayOf(Color.parseColor("#3A352F"), Color.parseColor("#F3EFE7"), Color.parseColor("#3A352F"))
        ).apply {
            cornerRadius = ui.dp(5).toFloat(); setStroke(ui.dp(1), Color.parseColor("#80000000")); setSize(w, h)
        }
        val line = GradientDrawable().apply { setColor(ui.accent); setSize(ui.dp(2), ui.dp(14)) }
        val thumb = LayerDrawable(arrayOf(shadow, cap, line)).apply {
            setLayerInset(0, ui.dp(1), ui.dp(3), ui.dp(1), 0)
            setLayerInset(1, 0, 0, 0, ui.dp(3))
            setLayerInset(2, (w - ui.dp(2)) / 2, (h - ui.dp(14)) / 2, (w - ui.dp(2)) / 2, (h - ui.dp(14)) / 2 + ui.dp(3))
        }

        return SeekBar(this).apply {
            max = 85
            progressDrawable = progress
            this.thumb = thumb
            splitTrack = false
            thumbOffset = ui.dp(11)
            setPadding(ui.dp(11), 0, ui.dp(11), 0)
            if (Build.VERSION.SDK_INT >= 29) { minHeight = ui.dp(6); maxHeight = ui.dp(6) }
            background = null
        }
    }

    // ---------------- Color ----------------
    private fun accentPlate(): View {
        val card = ui.plateCard()
        card.addView(ui.sectionTitle("Color de acento"))
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(ui.dp(6), 0, ui.dp(6), 0)
        }
        val sel = Prefs.accentIndex(this)
        for (i in Prefs.ACCENTS.indices) {
            row.addView(knob(i, i == sel), LinearLayout.LayoutParams(0, -2, 1f))
        }
        card.addView(row)
        return card
    }

    private fun knob(i: Int, selected: Boolean): View {
        val color = Prefs.ACCENTS[i]
        val outer = FrameLayout(this)
        if (selected) {
            outer.addView(View(this).apply {
                background = ui.oval(Color.TRANSPARENT, (color and 0x00FFFFFF) or 0x1F000000, 6f)
            }, FrameLayout.LayoutParams(ui.dp(72), ui.dp(72), Gravity.CENTER))
            outer.addView(View(this).apply {
                background = ui.oval(Color.TRANSPARENT, color, 1.5f)
            }, FrameLayout.LayoutParams(ui.dp(64), ui.dp(64), Gravity.CENTER))
        }
        val cap = FrameLayout(this).apply {
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                gradientType = GradientDrawable.RADIAL_GRADIENT
                gradientRadius = ui.dp(34).toFloat()
                setGradientCenter(0.5f, 0.35f)
                colors = intArrayOf(Color.parseColor("#3A342E"), Color.parseColor("#141110"))
                setStroke(ui.dp(1), Color.parseColor("#1AFFFFFF"))
            }
            elevation = ui.dp(4).toFloat()
            outlineProvider = ViewOutlineProvider.BACKGROUND
            val gem = FrameLayout(this@MainActivity).apply {
                background = ui.oval(color)
                addView(View(this@MainActivity).apply {
                    background = ui.oval(Color.parseColor("#4DFFFFFF")); alpha = 0.5f
                }, FrameLayout.LayoutParams(ui.dp(20), ui.dp(10), Gravity.TOP or Gravity.CENTER_HORIZONTAL).apply {
                    topMargin = ui.dp(3)
                })
            }
            addView(gem, FrameLayout.LayoutParams(ui.dp(30), ui.dp(30), Gravity.CENTER))
        }
        outer.addView(cap, FrameLayout.LayoutParams(ui.dp(56), ui.dp(56), Gravity.CENTER))

        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            addView(outer, LinearLayout.LayoutParams(ui.dp(76), ui.dp(76)))
            addView(ui.text(Prefs.NAMES[i], ui.cond, 16f, if (selected) Ui.HEAD else Ui.MUTED).apply {
                setPadding(0, ui.dp(4), 0, 0)
            })
            isClickable = true
            setOnClickListener {
                animate().scaleX(0.96f).scaleY(0.96f).setDuration(80).withEndAction {
                    scaleX = 1f; scaleY = 1f
                    Prefs.setAccentIndex(this@MainActivity, i)
                    RockWidget.updateAll(this@MainActivity)
                    build()
                }.start()
            }
        }
    }

    // ---------------- Clima ----------------
    private fun weatherPlate(): View {
        val card = ui.plateCard()
        card.addView(ui.sectionTitle("Clima"))
        val field = EditText(this).apply {
            setText(Prefs.city(this@MainActivity))
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
        card.addView(LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            addView(field, LinearLayout.LayoutParams(0, ui.dp(48), 1f))
            addView(save, LinearLayout.LayoutParams(ui.dp(96), ui.dp(48)).apply { marginStart = ui.dp(10) })
        })
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
                    main.post {
                        val r = result
                        when {
                            offline -> setStatus(Ui.ALERT, null, "Sin conexión. Inténtalo cuando vuelva la red.")
                            r == null -> setStatus(Ui.ALERT, null, "No encontramos esa ciudad. Revisa la ortografía o agrega el departamento.")
                            else -> {
                                Prefs.setCity(this, r.first, r.second, r.third)
                                field.setText(r.first)
                                setStatus(Ui.DIM, r.first, " Consultando el clima…")
                                WeatherWorker.runNow(this)
                                WeatherWorker.schedule(this)
                                RockWidget.updateAll(this)
                                main.postDelayed({ refreshCityStatus() }, 5000)
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

    private fun setStatus(dotColor: Int, city: String?, rest: String) {
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

    private fun refreshCityStatus() {
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

    // ---------------- Setlist ----------------
    private fun setlistPlate(): View {
        val card = ui.plateCard()
        val filled = (0 until Prefs.SLOTS).count { Prefs.app(this, it).isNotBlank() }
        card.addView(ui.sectionTitle("Setlist", "$filled de ${Prefs.SLOTS} pistas"))
        for (i in 0 until Prefs.SLOTS) {
            if (i > 0) card.addView(View(this).apply { setBackgroundColor(Color.parseColor("#0FFFFFFF")) },
                LinearLayout.LayoutParams(-1, ui.dp(1)))
            card.addView(slotRow(i), LinearLayout.LayoutParams(-1, ui.dp(56)))
        }
        return card
    }

    private fun slotRow(i: Int): View {
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
            addView(ui.ellipsize(ui.text(if (empty) "Pista libre" else Apps.label(this@MainActivity, pkg), ui.cond, 19f,
                if (empty) Ui.DIM else Ui.HEAD)),
                LinearLayout.LayoutParams(0, -2, 1f).apply { marginStart = ui.dp(14) })
            addView(ui.text(if (empty) "Elegir app" else "Cambiar", ui.cond, 15f, if (empty) ui.accent else Ui.MUTED))
            addView(ui.text("›", ui.condLight, 22f, Ui.DIM).apply { setPadding(ui.dp(8), 0, 0, ui.dp(2)) })
            setOnClickListener {
                AppPicker(this@MainActivity, ui, i) {
                    RockWidget.updateAll(this@MainActivity)
                    build()
                }.show()
            }
        }
    }

    // ---------------- Widget ----------------
    private fun widgetPlate(): View {
        val card = ui.plateCard()
        card.layoutTransition = LayoutTransition()
        card.addView(ui.sectionTitle("Widget"))
        card.addView(ui.bodyText("HyperOS no deja que una app ponga widgets por su cuenta. Agrégalo tú en cuatro pasos."))

        val steps = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = ui.rect(Ui.WELL, 12f, Color.parseColor("#12FFFFFF"), 1f)
            setPadding(ui.dp(14), ui.dp(14), ui.dp(14), ui.dp(4))
            visibility = View.GONE
        }
        val texts = listOf(
            "Mantén presionado un espacio vacío de la pantalla de inicio.",
            "Toca Widgets.",
            "Busca Redoble en la lista.",
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
                pinRequestedAt = System.currentTimeMillis()
                main.postDelayed({
                    if (RockWidget.hasWidgets(this) == before) steps.visibility = View.VISIBLE
                }, 3000)
            }
        }
        card.addView(add, LinearLayout.LayoutParams(-1, ui.dp(48)).apply { topMargin = ui.dp(14); bottomMargin = ui.dp(12) })
        card.addView(steps, LinearLayout.LayoutParams(-1, -2))
        return card
    }

    private fun tryPin(): Boolean {
        val mgr = AppWidgetManager.getInstance(this)
        return try {
            mgr.isRequestPinAppWidgetSupported &&
                mgr.requestPinAppWidget(ComponentName(this, RockWidget::class.java), null, null)
        } catch (e: Exception) {
            false
        }
    }

    // ==================================================================
    // Foto y fondo de pantalla
    // ==================================================================
    private fun screenSize(): Pair<Int, Int> {
        return if (Build.VERSION.SDK_INT >= 30) {
            val b = getSystemService(WindowManager::class.java).maximumWindowMetrics.bounds
            Pair(b.width(), b.height())
        } else {
            val dm = android.util.DisplayMetrics()
            @Suppress("DEPRECATION")
            windowManager.defaultDisplay.getRealMetrics(dm)
            Pair(dm.widthPixels, dm.heightPixels)
        }
    }

    private fun loadPhoto(uri: Uri) {
        val (w, h) = screenSize()
        io.execute {
            val bmp = try { decodeSampled(uri, w, h) } catch (e: Exception) { null }
            var name = "Tu foto"
            try {
                contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
                    if (c.moveToFirst()) name = c.getString(0) ?: name
                }
            } catch (e: Exception) { }
            if (bmp != null) {
                try { photoFile.outputStream().use { bmp.compress(Bitmap.CompressFormat.JPEG, 92, it) } } catch (e: Exception) { }
                getSharedPreferences("redoble", MODE_PRIVATE).edit().putString("photoName", name).apply()
            }
            main.post {
                if (bmp == null) toast("No pudimos abrir esa foto") else { photo = bmp; build() }
            }
        }
    }

    private fun decodeSampled(uri: Uri, reqW: Int, reqH: Int): Bitmap? {
        val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) }
        var sample = 1
        while (opts.outWidth / (sample * 2) >= reqW && opts.outHeight / (sample * 2) >= reqH) sample *= 2
        val real = BitmapFactory.Options().apply { inSampleSize = sample }
        return contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, real) }
    }

    private fun applyWallpaper(btn: Button) {
        val src = photo
        if (src == null) {
            toast("Primero elige una foto")
            return
        }
        val (w, h) = screenSize()
        val dim = Prefs.dim(this)
        btn.isEnabled = false
        btn.text = "Aplicando…"
        io.execute {
            val ok = try {
                val wall = Graphics.makeWallpaper(src, w, h, dim)
                WallpaperManager.getInstance(this).setBitmap(
                    wall, null, true, WallpaperManager.FLAG_SYSTEM or WallpaperManager.FLAG_LOCK
                )
                true
            } catch (e: Exception) {
                false
            }
            main.post {
                btn.isEnabled = true
                btn.text = if (ok) "Fondo aplicado" else "No se pudo aplicar"
                main.postDelayed({ btn.text = "Aplicar fondo" }, 2000)
            }
        }
    }

    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
}
