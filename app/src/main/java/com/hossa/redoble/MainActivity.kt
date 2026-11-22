package com.hossa.redoble

import android.animation.LayoutTransition
import android.app.WallpaperManager
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Intent
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
import android.provider.Settings
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
import android.widget.HorizontalScrollView
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
        if (Prefs.city(this).isNotBlank() && (System.currentTimeMillis() - Prefs.weatherAt(this) > 30 * 60_000L ||
                Prefs.weatherTomorrow(this).contains(" a "))) {
            WeatherWorker.runNow(this)
            WeatherWorker.schedule(this)
            main.postDelayed({ refreshCityStatus() }, 5000)
        }
        build()
    }

    override fun onResume() {
        super.onResume()
        RockWidget.updateAll(this)
        BatteryMonitor.check(this)
        LiveService.start(this)
        build()
    }

    // ==================================================================
    // Construcción de la pantalla
    // ==================================================================
    private fun build() {
        val y = scroll?.scrollY ?: 0
        val th = Themes.current(this)
        ui = Ui(this, if (th.light) Color.parseColor("#D8CFC2") else th.accent(this))

        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, ui.dp(14), 0, ui.dp(40))
        }
        val hasWidget = RockWidget.hasWidgets(this)
        col.addView(topBar())
        if (!hasWidget) col.addView(widgetPlate())
        col.addView(designPlate())
        col.addView(themePlate())
        col.addView(wallpaperPlate())
        if (th.usesUserAccent) col.addView(accentPlate())
        col.addView(weatherPlate())
        col.addView(setlistPlate())
        col.addView(playerPlate())
        if (hasWidget) col.addView(widgetDonePlate())
        col.addView(ui.text("Prisma 8.1", ui.cond, 13f, Ui.DIM).apply { gravity = Gravity.CENTER },
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
        val brand = ui.text("Prisma", ui.anton, 34f, Ui.HEAD, 0.03f).apply {
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
    private var photoSmall: Bitmap? = null

    private fun smallPhoto(): Bitmap? {
        val p = photo ?: return null
        photoSmall?.let { return it }
        val s = 900f / maxOf(p.width, p.height)
        val sm = if (s < 1f) Bitmap.createScaledBitmap(p, (p.width * s).toInt(), (p.height * s).toInt(), true) else p
        photoSmall = sm
        return sm
    }

    private fun renderPreview(iv: ImageView) {
        val sm = smallPhoto() ?: return
        val (w, h) = screenSize()
        val k = 360f / w
        iv.setImageBitmap(
            Graphics.makeWallpaper(sm, (w * k).toInt(), (h * k).toInt(), Prefs.dim(this),
                Prefs.wpMode(this), Prefs.wpPos(this), Prefs.wpZoom(this))
        )
    }

    private val suggested = listOf(
        R.drawable.fondo_bateria to "Batería",
        R.drawable.fondo_neon to "Batería neón",
        R.drawable.fondo_baquetas to "Baquetas",
        R.drawable.fondo_bajo to "Bajo"
    )
    private val thumbs = HashMap<Int, Bitmap>()

    private fun thumb(res: Int): Bitmap? = thumbs[res] ?: try {
        val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeResource(resources, res, o)
        var s = 1
        while (maxOf(o.outWidth, o.outHeight) / (s * 2) >= 360) s *= 2
        BitmapFactory.decodeResource(resources, res, BitmapFactory.Options().apply { inSampleSize = s })
            ?.also { thumbs[res] = it }
    } catch (e: Exception) { null }

    private fun useSuggested(res: Int, name: String) {
        io.execute {
            val bmp = try { BitmapFactory.decodeResource(resources, res, BitmapFactory.Options().apply { inScaled = false }) } catch (e: Exception) { null }
            if (bmp != null) {
                try { photoFile.outputStream().use { bmp.compress(Bitmap.CompressFormat.JPEG, 95, it) } } catch (e: Exception) { }
                getSharedPreferences("redoble", MODE_PRIVATE).edit().putString("photoName", name).apply()
            }
            main.post {
                if (bmp == null) toast("No pudimos abrir ese fondo") else {
                    photo = bmp; photoSmall = null
                    Prefs.setWpPos(this, 50); Prefs.setWpZoom(this, 0)
                    build()
                }
            }
        }
    }

    private fun suggestedRow(): View {
        val current = if (photo != null) getSharedPreferences("redoble", MODE_PRIVATE).getString("photoName", "") else ""
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        suggested.forEachIndexed { i, (res, name) ->
            val on = name == current
            val tile = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER_HORIZONTAL
                setOnClickListener { if (!on) useSuggested(res, name) }
            }
            val img = ImageView(this).apply {
                scaleType = ImageView.ScaleType.CENTER_CROP
                setImageBitmap(thumb(res))
                clipToOutline = true
                outlineProvider = object : ViewOutlineProvider() {
                    override fun getOutline(view: View, outline: Outline) {
                        outline.setRoundRect(0, 0, view.width, view.height, ui.dp(12).toFloat())
                    }
                }
                foreground = ui.rect(Color.TRANSPARENT, 12f, if (on) ui.accent else Color.parseColor("#33EDE6DC"), if (on) 2.5f else 1f)
            }
            tile.addView(img, LinearLayout.LayoutParams(ui.dp(76), ui.dp(120)))
            tile.addView(ui.text(name, ui.cond, 13f, if (on) Ui.HEAD else Ui.MUTED).apply {
                gravity = Gravity.CENTER; maxLines = 1
            }, LinearLayout.LayoutParams(ui.dp(84), -2).apply { topMargin = ui.dp(6) })
            row.addView(tile, LinearLayout.LayoutParams(-2, -2).apply { if (i > 0) marginStart = ui.dp(8) })
        }
        return HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
            addView(row)
        }
    }

    private fun wallpaperPlate(): View {
        val card = ui.plateCard()
        card.addView(ui.sectionTitle("Fondo"))
        card.addView(ui.text("SUGERIDOS", ui.condSemi, 12f, Ui.DIM).apply { letterSpacing = 0.12f },
            LinearLayout.LayoutParams(-2, -2).apply { bottomMargin = ui.dp(8) })
        card.addView(suggestedRow(), LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = ui.dp(18) })

        val (sw, sh) = screenSize()
        val pvW = ui.dp(150)
        val pvH = (pvW * sh.toFloat() / sw).toInt()
        val preview = FrameLayout(this).apply {
            clipToOutline = true
            outlineProvider = object : ViewOutlineProvider() {
                override fun getOutline(view: View, outline: Outline) {
                    outline.setRoundRect(0, 0, view.width, view.height, ui.dp(16).toFloat())
                }
            }
        }
        val iv = ImageView(this).apply { scaleType = ImageView.ScaleType.FIT_XY }
        val hasPhoto = photo != null
        if (hasPhoto) {
            preview.background = ui.rect(Color.BLACK, 16f)
            preview.addView(iv, FrameLayout.LayoutParams(-1, -1))
            preview.foreground = ui.rect(Color.TRANSPARENT, 16f, Color.parseColor("#33EDE6DC"), 1f)
            renderPreview(iv)
        } else {
            preview.background = GradientDrawable().apply {
                setColor(Ui.WELL); cornerRadius = ui.dp(16).toFloat()
                setStroke(ui.dp(1.5f), Color.parseColor("#47EDE6DC"), ui.dp(6).toFloat(), ui.dp(4).toFloat())
            }
            preview.addView(ui.text("Elige un sugerido\no una foto tuya", ui.cond, 16f, Ui.MUTED).apply { gravity = Gravity.CENTER },
                FrameLayout.LayoutParams(-1, -1))
        }
        card.addView(preview, LinearLayout.LayoutParams(pvW, pvH).apply { gravity = Gravity.CENTER_HORIZONTAL })
        if (hasPhoto) {
            val name = getSharedPreferences("redoble", MODE_PRIVATE).getString("photoName", "Tu foto") ?: "Tu foto"
            card.addView(ui.ellipsize(ui.text(name, ui.cond, 14f, Ui.DIM)).apply { gravity = Gravity.CENTER },
                LinearLayout.LayoutParams(-1, -2).apply { topMargin = ui.dp(8) })

            // Modo: recortar / foto completa
            val mode = Prefs.wpMode(this)
            val modes = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                background = ui.rect(Ui.WELL, 12f, Color.parseColor("#12FFFFFF"), 1f)
                setPadding(ui.dp(4), ui.dp(4), ui.dp(4), ui.dp(4))
            }
            listOf("Foto completa" to 1, "Llenar pantalla" to 0).forEachIndexed { idx, (label, m) ->
                val on = m == mode
                val b = (if (on) ui.primary(label) else ui.textButton(label).apply { setTextColor(Ui.MUTED) }).apply {
                    minHeight = ui.dp(40); minimumHeight = ui.dp(40)
                    setOnClickListener {
                        if (m != Prefs.wpMode(this@MainActivity)) {
                            Prefs.setWpMode(this@MainActivity, m); Prefs.setWpPos(this@MainActivity, 50)
                            Prefs.setWpZoom(this@MainActivity, 0); build()
                        }
                    }
                }
                modes.addView(b, LinearLayout.LayoutParams(0, ui.dp(40), 1f).apply { if (idx > 0) marginStart = ui.dp(4) })
            }
            card.addView(modes, LinearLayout.LayoutParams(-1, -2).apply { topMargin = ui.dp(16) })
            card.addView(ui.bodyText(
                if (mode == 1) "Se ve la foto entera; los bordes se funden con el negro."
                else "La foto cubre toda la pantalla; elige qué parte se ve.", Ui.DIM, 13f
            ).apply { setPadding(0, ui.dp(8), 0, 0) })

            card.addView(sliderRow("Posición", Prefs.wpPos(this), 100, "%") { v, done ->
                Prefs.setWpPos(this, v); renderPreview(iv)
            })
            card.addView(sliderRow("Zoom", Prefs.wpZoom(this), 100, "%") { v, done ->
                Prefs.setWpZoom(this, v); renderPreview(iv)
            })
        }
        card.addView(sliderRow("Oscurecer", Prefs.dim(this), 85, "%", showScale = true) { v, done ->
            Prefs.setDim(this, v); if (hasPhoto) renderPreview(iv)
        })

        val pick = ui.secondary("Foto del celular").apply {
            setOnClickListener { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }
        }
        val apply = ui.primary("Aplicar fondo")
        apply.setOnClickListener { applyWallpaper(apply) }
        card.addView(LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            addView(pick, LinearLayout.LayoutParams(0, ui.dp(48), 1f))
            addView(apply, LinearLayout.LayoutParams(0, ui.dp(48), 1f).apply { marginStart = ui.dp(10) })
        }, LinearLayout.LayoutParams(-1, -2).apply { topMargin = ui.dp(16) })
        card.addView(ui.bodyText("Se aplica a la pantalla de inicio y a la de bloqueo, a la resolución exacta de tu pantalla.", Ui.DIM, 13f).apply {
            setPadding(0, ui.dp(10), 0, 0)
        })
        return card
    }

    /** Fila con etiqueta, valor y fader. onChange(valor, soltó) */
    private fun sliderRow(label: String, value: Int, max: Int, unit: String, showScale: Boolean = false,
                          onChange: (Int, Boolean) -> Unit): View {
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(-1, -2).apply { topMargin = ui.dp(14) }
        }
        val valueTv = ui.text("$value $unit", ui.condMedium, 18f, Ui.HEAD)
        box.addView(LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            addView(ui.text(label, ui.cond, 16f, Ui.HEAD), LinearLayout.LayoutParams(0, -2, 1f))
            addView(valueTv)
        })
        val sb = fader(max)
        sb.progress = value
        var last = 0L
        sb.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(s: SeekBar, v: Int, fromUser: Boolean) {
                valueTv.text = "$v $unit"
                val now = System.currentTimeMillis()
                if (fromUser && now - last > 60) { last = now; onChange(v, false) }
            }
            override fun onStartTrackingTouch(s: SeekBar) {}
            override fun onStopTrackingTouch(s: SeekBar) = onChange(s.progress, true)
        })
        box.addView(sb, LinearLayout.LayoutParams(-1, ui.dp(40)))
        if (showScale) box.addView(LinearLayout(this).apply {
            setPadding(ui.dp(9), 0, ui.dp(9), 0)
            addView(ui.text("0", ui.cond, 12f, Ui.DIM), LinearLayout.LayoutParams(0, -2, 1f))
            addView(ui.text("$max", ui.cond, 12f, Ui.DIM))
        })
        return box
    }

    private fun fader(maxValue: Int = 85): SeekBar {
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
            max = maxValue
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
            setPadding(ui.dp(2), 0, ui.dp(2), 0)
        }
        val sel = Prefs.accentIndex(this).coerceIn(0, Prefs.ACCENTS.size - 1)
        for (i in Prefs.ACCENTS.indices) {
            row.addView(knob(i, i == sel), LinearLayout.LayoutParams(ui.dp(88), -2))
        }
        val scroller = android.widget.HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
            overScrollMode = View.OVER_SCROLL_NEVER
            addView(row)
        }
        card.addView(scroller, LinearLayout.LayoutParams(-1, -2))
        card.addView(ui.text("${Prefs.NAMES[sel]} · desliza para ver más", ui.cond, 13f, Ui.DIM).apply {
            gravity = Gravity.CENTER; setPadding(0, ui.dp(8), 0, 0)
        }, LinearLayout.LayoutParams(-1, -2))
        // Llevar el color elegido a la vista
        scroller.post { scroller.scrollTo((ui.dp(88) * sel - scroller.width / 2 + ui.dp(44)).coerceAtLeast(0), 0) }
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
        card.addView(ui.bodyText("Mantén presionada una pista para ponerle un nombre corto.", Ui.DIM, 13f).apply {
            setPadding(0, 0, 0, ui.dp(8))
        })

        // Íconos: silueta (como ahora) o color original de cada app
        val iconMode = Prefs.iconMode(this)
        val iconModes = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            background = ui.rect(Ui.WELL, 12f, Color.parseColor("#12FFFFFF"), 1f)
            setPadding(ui.dp(4), ui.dp(4), ui.dp(4), ui.dp(4))
        }
        listOf("Silueta" to 0, "Color original" to 1).forEachIndexed { idx, (label, m) ->
            val on = m == iconMode
            val b = (if (on) ui.primary(label) else ui.textButton(label).apply { setTextColor(Ui.MUTED) }).apply {
                minHeight = ui.dp(40); minimumHeight = ui.dp(40)
                setOnClickListener {
                    if (m != Prefs.iconMode(this@MainActivity)) {
                        Prefs.setIconMode(this@MainActivity, m)
                        RockWidget.updateAll(this@MainActivity)
                        build()
                    }
                }
            }
            iconModes.addView(b, LinearLayout.LayoutParams(0, ui.dp(40), 1f).apply { if (idx > 0) marginStart = ui.dp(4) })
        }
        card.addView(iconModes, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = ui.dp(12) })
        for (i in 0 until Prefs.SLOTS) {
            if (i > 0) card.addView(View(this).apply { setBackgroundColor(Color.parseColor("#0FFFFFFF")) },
                LinearLayout.LayoutParams(-1, ui.dp(1)))
            card.addView(slotRow(i), LinearLayout.LayoutParams(-1, ui.dp(56)))
        }
        return card
    }

    private fun renameSlot(slot: Int) {
        val pkg = Prefs.app(this, slot)
        val original = Apps.label(this, pkg)
        val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(ui.dp(20), ui.dp(8), ui.dp(20), 0) }
        val field = EditText(this).apply {
            setText(Prefs.label(this@MainActivity, slot) ?: original)
            setSelectAllOnFocus(true)
            setSingleLine()
            filters = arrayOf(android.text.InputFilter.LengthFilter(14))
        }
        box.addView(ui.bodyText("Nombre original: $original", Ui.MUTED, 13f))
        box.addView(field)
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Nombre del pad %02d".format(slot + 1))
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
            addView(ui.ellipsize(ui.text(if (empty) "Pista libre" else Prefs.displayName(this@MainActivity, i), ui.cond, 19f,
                if (empty) Ui.DIM else Ui.HEAD)),
                LinearLayout.LayoutParams(0, -2, 1f).apply { marginStart = ui.dp(14) })
            addView(ui.text(if (empty) "Elegir app" else "Cambiar", ui.cond, 15f, if (empty) ui.accent else Ui.MUTED))
            addView(ui.text("›", ui.condLight, 22f, Ui.DIM).apply { setPadding(ui.dp(8), 0, 0, ui.dp(2)) })
            if (!empty) setOnLongClickListener { renameSlot(i); true }
            setOnClickListener {
                AppPicker(this@MainActivity, ui, i) {
                    RockWidget.updateAll(this@MainActivity)
                    build()
                }.show()
            }
        }
    }

    // ---------------- Widget ----------------
    // ---------------- Diseño: distribuciones y elementos ----------------
    private var customOpen = false

    private fun designPlate(): View {
        val card = ui.plateCard()
        val preset = Prefs.preset(this)
        val names = mapOf("completo" to "Completo", "musica" to "Música", "minimal" to "Minimal")
        card.addView(ui.sectionTitle("Diseño", names[preset] ?: "Personalizado"))
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        names.entries.forEachIndexed { idx, (k, label) ->
            val b = (if (k == preset) ui.primary(label) else ui.secondary(label)).apply {
                textSize = 15f
                setPadding(ui.dp(4), 0, ui.dp(4), 0)
                maxLines = 1
                setOnClickListener {
                    Prefs.applyPreset(this@MainActivity, k)
                    RockWidget.updateAll(this@MainActivity)
                    build()
                }
            }
            row.addView(b, LinearLayout.LayoutParams(0, ui.dp(46), 1f).apply { if (idx > 0) marginStart = ui.dp(8) })
        }
        card.addView(row)
        val desc = when (preset) {
            "completo" -> "Hora, clima, batería y 6 apps. Cuando suena música, el reproductor aparece compacto y quedan 3 apps para que no se apriete."
            "musica" -> "Reproductor grande siempre a la vista, cabecera compacta y 3 apps."
            "minimal" -> "Solo día, hora, clima y 3 apps. Se luce el fondo."
            else -> "Elementos elegidos por ti. Se respeta el número de apps aunque suene música."
        }
        card.addView(ui.bodyText(desc, Ui.MUTED, 14f), LinearLayout.LayoutParams(-1, -2).apply { topMargin = ui.dp(10) })

        val more = ui.textButton(if (customOpen) "Personalizar elementos  ▾" else "Personalizar elementos  ▸").apply {
            setTextColor(Ui.HEAD)
            gravity = Gravity.CENTER_VERTICAL or Gravity.START
            setOnClickListener { customOpen = !customOpen; build() }
        }
        card.addView(more, LinearLayout.LayoutParams(-1, ui.dp(46)).apply { topMargin = ui.dp(8) })

        if (customOpen) {
            val box = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                background = ui.rect(Ui.WELL, 12f, Color.parseColor("#12FFFFFF"), 1f)
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
            val appsRow = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(0, ui.dp(8), 0, ui.dp(4))
            }
            appsRow.addView(ui.text("Apps", ui.cond, 16f, Ui.HEAD), LinearLayout.LayoutParams(0, -2, 1f))
            val n = Prefs.appsCount(this)
            listOf(0 to "Ninguna", 3 to "3", 6 to "6").forEach { (v, label) ->
                val b = (if (v == n) ui.primary(label) else ui.textButton(label).apply { setTextColor(Ui.MUTED) }).apply {
                    minHeight = ui.dp(38); minimumHeight = ui.dp(38)
                    setOnClickListener { Prefs.setAppsCount(this@MainActivity, v); RockWidget.updateAll(this@MainActivity); build() }
                }
                appsRow.addView(b, LinearLayout.LayoutParams(-2, ui.dp(38)).apply { marginStart = ui.dp(6) })
            }
            box.addView(appsRow)
            card.addView(box, LinearLayout.LayoutParams(-1, -2).apply { topMargin = ui.dp(4) })
        }
        return card
    }

    private fun switchRow(label: String, on: Boolean, onChange: (Boolean) -> Unit): View {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            minimumHeight = ui.dp(48)
        }
        row.addView(ui.text(label, ui.cond, 16f, Ui.HEAD), LinearLayout.LayoutParams(0, -2, 1f))
        val sw = androidx.appcompat.widget.SwitchCompat(this).apply {
            isChecked = on
            val states = arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf())
            thumbTintList = android.content.res.ColorStateList(states, intArrayOf(Color.WHITE, Color.parseColor("#9C9187")))
            trackTintList = android.content.res.ColorStateList(states, intArrayOf(ui.accent, Color.parseColor("#3A322B")))
            setOnCheckedChangeListener { _, v -> onChange(v) }
        }
        row.addView(sw)
        row.setOnClickListener { sw.toggle() }
        return row
    }

    // ---------------- Tema ----------------
    private fun themePlate(): View {
        val card = ui.plateCard()
        val cur = Themes.current(this)
        card.addView(ui.sectionTitle("Tema", cur.name))
        val strip = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val tw = ui.dp(84); val thH = ui.dp(130)
        Themes.ALL.forEach { t ->
            val on = t.id == cur.id
            val tile = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER_HORIZONTAL
                setPadding(0, 0, ui.dp(10), 0)
            }
            val img = ImageView(this).apply {
                scaleType = ImageView.ScaleType.CENTER_CROP
                val bmp = if (t.family == "rock") thumb(R.drawable.fondo_bateria) else Themes.wallpaper(t, tw, thH)
                setImageBitmap(bmp)
                background = ui.rect(Color.BLACK, 12f, if (on) ui.accent else Color.parseColor("#24FFFFFF"), if (on) 2f else 1f)
                setPadding(ui.dp(2), ui.dp(2), ui.dp(2), ui.dp(2))
                clipToOutline = true
                outlineProvider = object : ViewOutlineProvider() {
                    override fun getOutline(view: View, outline: Outline) {
                        outline.setRoundRect(0, 0, view.width, view.height, ui.dp(12).toFloat())
                    }
                }
            }
            // Muestra de la hora con los colores del tema
            val frame = FrameLayout(this)
            frame.addView(img, FrameLayout.LayoutParams(tw, thH))
            frame.addView(ui.text("5:43", ui.condLight, 26f, t.head).apply {
                gravity = Gravity.CENTER
            }, FrameLayout.LayoutParams(tw, thH))
            frame.addView(View(this).apply { background = ui.rect(t.accent(this@MainActivity), 3f) },
                FrameLayout.LayoutParams(ui.dp(22), ui.dp(4)).apply {
                    gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL; bottomMargin = ui.dp(14)
                })
            tile.addView(frame)
            tile.addView(ui.ellipsize(ui.text(t.name, ui.cond, 13f, if (on) Ui.HEAD else Ui.MUTED)).apply {
                gravity = Gravity.CENTER
            }, LinearLayout.LayoutParams(tw, -2).apply { topMargin = ui.dp(6) })
            tile.setOnClickListener {
                if (t.id != Prefs.theme(this)) {
                    Prefs.setTheme(this, t.id)
                    RockWidget.updateAll(this)
                    build()
                }
            }
            strip.addView(tile)
        }
        card.addView(HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
            addView(strip)
        })
        card.addView(switchRow("Letras del tema", Prefs.themeFonts(this)) { on ->
            Prefs.setThemeFonts(this, on)
            RockWidget.updateAll(this)
        }, LinearLayout.LayoutParams(-1, -2).apply { topMargin = ui.dp(10) })
        card.addView(ui.bodyText("Encendido: el widget usa las letras exactas de cada tema. Apagado: la letra del sistema.", Ui.MUTED, 13f))
        if (cur.hasOwnWallpaper) {
            card.addView(ui.bodyText("Este tema tiene su propio fondo de pantalla. También puedes usar tu foto en Fondo.", Ui.MUTED, 13f),
                LinearLayout.LayoutParams(-1, -2).apply { topMargin = ui.dp(12) })
            val apply = ui.primary("Poner el fondo del tema")
            apply.setOnClickListener { applyThemeWallpaper(apply, cur) }
            card.addView(apply, LinearLayout.LayoutParams(-1, ui.dp(48)).apply { topMargin = ui.dp(10) })
        }
        return card
    }

    private fun applyThemeWallpaper(btn: Button, t: Theme) {
        val (w, h) = screenSize()
        btn.isEnabled = false
        btn.text = "Aplicando…"
        io.execute {
            val ok = try {
                val wall = Themes.wallpaper(t, w, h)
                WallpaperManager.getInstance(this).setBitmap(
                    wall, android.graphics.Rect(0, 0, w, h), true, WallpaperManager.FLAG_SYSTEM or WallpaperManager.FLAG_LOCK
                )
                true
            } catch (e: Exception) { false }
            main.post {
                btn.isEnabled = true
                btn.text = if (ok) "Fondo aplicado" else "No se pudo aplicar"
                main.postDelayed({ btn.text = "Poner el fondo del tema" }, 2000)
            }
        }
    }

    private fun widgetDonePlate(): View {
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

    private fun playerPlate(): View {
        val card = ui.plateCard()
        card.addView(ui.sectionTitle("Reproductor"))
        card.addView(ui.bodyText("Cuando suena música, aparece encima de la foto con la canción y los botones ⏮ ⏯ ⏭. Sin música se oculta y RockPlay se abre desde su pad."))

        val on = Player.enabled(this)
        card.addView(LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            addView(ui.dot(if (on) Ui.OK else ui.accent), LinearLayout.LayoutParams(ui.dp(7), ui.dp(7)))
            addView(ui.text(
                if (on) "Activo: Prisma puede ver qué está sonando."
                else "Falta el permiso de acceso a notificaciones.",
                ui.cond, 14f, Ui.MUTED
            ).apply { setPadding(ui.dp(8), 0, 0, 0) })
        }, LinearLayout.LayoutParams(-1, -2).apply { topMargin = ui.dp(12) })

        if (!on) {
            card.addView(ui.bodyText("Android pide este permiso para mostrar la música en un widget. Redoble solo lo usa para leer la canción y los controles; no lee tus mensajes.", Ui.DIM, 13f),
                LinearLayout.LayoutParams(-1, -2).apply { topMargin = ui.dp(8) })
        }
        val btn = if (on) ui.textButton("Revisar permiso") else ui.primary("Dar permiso")
        btn.setOnClickListener {
            try {
                startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
            } catch (e: Exception) {
                Toast.makeText(this, "Busca «Acceso a notificaciones» en Ajustes y activa Redoble.", Toast.LENGTH_LONG).show()
            }
        }
        card.addView(btn, LinearLayout.LayoutParams(-1, ui.dp(48)).apply { topMargin = ui.dp(12) })

        // Qué música mostrar
        val only = Prefs.onlyRockPlay(this)
        val modes = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            background = ui.rect(Ui.WELL, 12f, Color.parseColor("#12FFFFFF"), 1f)
            setPadding(ui.dp(4), ui.dp(4), ui.dp(4), ui.dp(4))
        }
        listOf("Solo RockPlay" to true, "Cualquier app" to false).forEachIndexed { idx, (label, m) ->
            val sel = m == only
            val b = (if (sel) ui.primary(label) else ui.textButton(label).apply { setTextColor(Ui.MUTED) }).apply {
                minHeight = ui.dp(40); minimumHeight = ui.dp(40)
                setOnClickListener {
                    if (m != Prefs.onlyRockPlay(this@MainActivity)) {
                        Prefs.setOnlyRockPlay(this@MainActivity, m)
                        Player.updateAll(this@MainActivity)
                        build()
                    }
                }
            }
            modes.addView(b, LinearLayout.LayoutParams(0, ui.dp(40), 1f).apply { if (idx > 0) marginStart = ui.dp(4) })
        }
        card.addView(modes, LinearLayout.LayoutParams(-1, -2).apply { topMargin = ui.dp(12); bottomMargin = ui.dp(4) })
        return card
    }

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
            "Busca Prisma en la lista.",
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
                if (bmp == null) toast("No pudimos abrir esa foto") else {
                    photo = bmp; photoSmall = null
                    Prefs.setWpPos(this, 50); Prefs.setWpZoom(this, 0)
                    build()
                }
            }
        }
    }

    private fun decodeSampled(uri: Uri, reqW: Int, reqH: Int): Bitmap? {
        val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) }
        // Máxima calidad posible: solo se reduce si la foto pasa de ~16 MP
        var sample = 1
        while ((opts.outWidth.toLong() / sample) * (opts.outHeight.toLong() / sample) > 16_000_000L) sample *= 2
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
                val wall = Graphics.makeWallpaper(src, w, h, dim, Prefs.wpMode(this), Prefs.wpPos(this), Prefs.wpZoom(this))
                WallpaperManager.getInstance(this).setBitmap(
                    wall, android.graphics.Rect(0, 0, w, h), true, WallpaperManager.FLAG_SYSTEM or WallpaperManager.FLAG_LOCK
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
