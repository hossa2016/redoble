package com.hossa.redoble

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Shader
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowInsetsControllerCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.activity.enableEdgeToEdge
import java.io.File
import java.util.concurrent.Executors

class MainActivity : AppCompatActivity() {

    internal val io = Executors.newSingleThreadExecutor()
    internal val main = Handler(Looper.getMainLooper())

    internal lateinit var ui: Ui
    internal var scroll: ScrollView? = null
    internal var photo: Bitmap? = null
    @Volatile internal var photoSmall: Bitmap? = null
    internal var cityStatus: TextView? = null
    internal var cityDot: View? = null
    internal var customOpen = false
    internal var photoOpen = false
    internal var onbStep = 0
    internal val thumbs = java.util.concurrent.ConcurrentHashMap<Int, Bitmap>()
    /** Miniaturas de los temas (se dibujan una sola vez, no en cada toque). */
    internal val themeThumbs = HashMap<Int, Bitmap>()
    internal val openSections: MutableSet<String> by lazy { Prefs.openSections(this).take(1).toMutableSet() }
    /** Para cerrar las demás secciones cuando se abre una (solo una abierta a la vez). */
    internal val folds = HashMap<String, (Boolean) -> Unit>()
    /** Vista previa fija (no se rehace en cada toque) y la columna de la pantalla. */
    internal var preview: PreviewParts? = null
    internal var mainCol: LinearLayout? = null
    /** Grupo de apps que se está editando (con widgets de Apps puestos) y si se ven los 20 lugares. */
    internal var appsGroup = 0
    internal var moreSlots = false
    internal var lastPreviewBg: Bitmap? = null

    internal val suggested = listOf(
        R.drawable.fondo_bateria to "Batería",
        R.drawable.fondo_neon to "Batería neón",
        R.drawable.fondo_baquetas to "Baquetas",
        R.drawable.fondo_bajo to "Bajo"
    )

    internal val photoFile get() = File(filesDir, "fondo.jpg")

    internal val picker = registerForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) loadPhoto(uri)
    }

    /** Android 13+: permiso para que se vea la notificación del widget en vivo. */
    internal val notifPerm = registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    internal fun askNotifications() {
        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            notifPerm.launch(android.Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()  // Android 15+ lo exige; los márgenes se ajustan en build()
        super.onCreate(savedInstanceState)
        // La foto se lee fuera del hilo principal para no trabar la apertura
        // reducida a ~4 MP por si una versión anterior guardó una foto más grande.
        if (photoFile.exists()) io.execute {
            val bmp = try {
                val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeFile(photoFile.path, o)
                var s = 1
                while ((o.outWidth.toLong() / s) * (o.outHeight.toLong() / s) > 4_000_000L) s *= 2
                BitmapFactory.decodeFile(photoFile.path, BitmapFactory.Options().apply { inSampleSize = s })
            } catch (e: Throwable) { null }
            onUi {
                photo = bmp
                // Solo se reconstruye si la foto se ve en pantalla (evita construir dos veces)
                if (Prefs.onboarded(this) && Prefs.wallStyle(this) == "photo") build()
            }
        }
        if (Prefs.city(this).isNotBlank() && (System.currentTimeMillis() - Prefs.weatherAt(this) > 30 * 60_000L ||
                Prefs.weatherTomorrow(this).contains(" a "))) {
            WeatherWorker.runNow(this)
            WeatherWorker.schedule(this)
            onUiDelayed(5000) { refreshCityStatus() }
        }
        if (Prefs.onboarded(this) && Prefs.liveUpdates(this)) askNotifications()
        // build() se hace en onResume (antes se construía dos veces al abrir)
    }

    override fun onDestroy() {
        main.removeCallbacksAndMessages(null)
        io.shutdownNow()
        super.onDestroy()
    }

    internal fun alive() = !isFinishing && !isDestroyed

    /** Vuelve al hilo principal solo si la pantalla sigue abierta. */
    internal fun onUi(block: () -> Unit) { main.post { if (alive()) block() } }
    internal fun onUiDelayed(ms: Long, block: () -> Unit) { main.postDelayed({ if (alive()) block() }, ms) }

    /** Al salir de la app se suelta el video de la vista previa (no gasta batería en segundo plano). */
    override fun onStop() {
        preview?.let { p -> p.video?.let { p.frame.removeView(it) }; p.video = null }
        super.onStop()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
    }

    override fun onResume() {
        super.onResume()
        // Desde la ventana del widget de Apps: abrir Apps con ese grupo
        intent?.getIntExtra("appsGroup", -1)?.takeIf { it >= 0 }?.let { g ->
            appsGroup = g
            openSections.clear(); openSections.add("apps"); Prefs.setOpenSections(this, openSections)
            intent.removeExtra("appsGroup")
        }
        // El widget se redibuja fuera del hilo principal para que la app abra fluida
        val app = applicationContext
        io.execute { try { RockWidget.updateAll(app) } catch (e: Exception) { } }
        BatteryMonitor.check(this)
        LiveService.start(this)
        build()
    }

    // ==================================================================
    // Construcción de la pantalla: tres bloques (Estilo, Contenido, Reproductor)
    // ==================================================================
    internal fun build() {
        val th = Themes.current(this)
        applyUiMode()
        // Temas sin color propio (Claro): el acento de la marca, para que la app no quede apagada
        val base = if (th.light) Color.parseColor(if (Ui.light) "#A8443A" else "#E58272") else th.accent(this)
        // En modo claro los acentos muy claros (plata, ámbar, cian) se oscurecen para que se lean
        val accent = if (Ui.light && androidx.core.graphics.ColorUtils.calculateLuminance(base) > 0.4)
            androidx.core.graphics.ColorUtils.blendARGB(base, Color.BLACK, 0.35f) else base
        ui = Ui(this, accent)
        if (!Prefs.onboarded(this)) { setScreen(onboardingView(), keepScroll = false); return }

        // La columna y la vista previa se conservan; se rehace solo lo demás
        val col = mainCol ?: LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, ui.dp(14), 0, ui.dp(40))
        }.also { mainCol = it }
        val hasWidget = RockWidget.hasWidgets(this)
        val pv = previewPlate()
        for (i in col.childCount - 1 downTo 0) if (col.getChildAt(i) !== pv) col.removeViewAt(i)
        if (pv.parent == null) col.addView(pv)
        col.addView(topBar(), 0)
        if (!hasWidget) col.addView(widgetPlate(), 1)

        // Pestañas: la pantalla se parte en cuatro para no recorrer 15 secciones seguidas.
        // Si hay una sección abierta (por ejemplo, desde la ventana de un widget), manda su pestaña.
        var tab = Prefs.settingsTab(this)
        openSections.firstOrNull()?.let { TAB_OF[it] }?.let { if (it != tab) { tab = it; Prefs.setSettingsTab(this, it) } }
        val tabs = ui.segmented(listOf(0 to "Estilo", 1 to "Contenido", 2 to "Widgets", 3 to "App"), tab) { t ->
            Prefs.setSettingsTab(this, t)
            openSections.clear(); Prefs.setOpenSections(this, openSections)
            build()
            // Que las pestañas queden a la vista al cambiar
            scroll?.let { sv -> sv.post { tabsView?.let { tv -> if (sv.scrollY > tv.top) sv.smoothScrollTo(0, (tv.top - ui.dp(8)).coerceAtLeast(0)) } } }
        }.apply { contentDescription = "Secciones de ajustes" }
        tabsView = tabs
        col.addView(tabs, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
            setMargins(ui.dp(16), 0, ui.dp(16), ui.dp(14))
        })

        val clockW = Widgets.has(this, Widgets.CLOCK); val appsW = Widgets.has(this, Widgets.APPS)
        val playerW = Widgets.has(this, Widgets.PLAYER)
        when (tab) {
            0 -> {
                val ws = Prefs.wallStyle(this)
                val wallName = when (ws) {
                    "fade" -> "Desvanecido"; "solid" -> "Fijo"; "photo" -> "Foto"
                    "anim" -> VideoScenes.find(Prefs.liveScene(this)).name; else -> "Del tema"
                }
                val widgetBgName = when (Prefs.widgetStyle(this)) { "fade" -> "Desvanecido"; "solid" -> "Fijo"; else -> "Sin fondo" }
                col.addView(fold(themePlate(), "tema"))
                col.addView(fold(wallpaperPlate(), "fondo", wallName))
                col.addView(fold(widgetBgPlate(), "fondoWidget", widgetBgName))
                if (th.usesUserAccent) col.addView(fold(accentPlate(), "acento",
                    Prefs.NAMES[Prefs.accentIndex(this).coerceIn(0, Prefs.NAMES.size - 1)]))
            }
            1 -> {
                // Lo que muestran los widgets (Diseño es del widget Completo)
                if (Widgets.has(this, Widgets.FULL) || !hasWidget) col.addView(fold(designPlate(), "diseno"))
                col.addView(fold(setlistPlate(), "apps"))
                col.addView(fold(weatherPlate(), "clima", Prefs.city(this).substringBefore(",").ifBlank { "Agregar ciudad ›" }))
                col.addView(fold(playerPlate(), "reproductor", if (Player.enabled(this)) "Activo" else "Falta dar permiso ›"))
            }
            2 -> {
                // Cuáles tienes y las opciones propias de cada widget suelto
                col.addView(fold(packPlate(), "w_pack", "${intArrayOf(Widgets.FULL, Widgets.CLOCK, Widgets.APPS, Widgets.PLAYER).count { Widgets.has(this, it) }} de 4 en pantalla"))
                if (clockW) col.addView(fold(clockWidgetPlate(), "w_clock"))
                if (appsW) col.addView(fold(appsWidgetsPlate(), "w_apps", Widgets.ids(this, Widgets.APPS).size.let { if (it == 1) "1 widget" else "$it widgets" }))
                if (playerW) col.addView(fold(playerWidgetPlate(), "w_player", if (Prefs.pwIdle(this) == "last") "Sin música: última canción" else "Sin música: nada sonando"))
                col.addView(fold(dreamPlate(), "dream", if (Prefs.dreamBg(this) == "black") "Fondo negro" else "Con tu fondo"))
            }
            else -> col.addView(fold(appearancePlate(hasWidget), "apariencia",
                when (Prefs.uiMode(this)) { "system" -> "Sistema"; "light" -> "Claro"; else -> "Oscuro" }))
        }

        col.addView(ui.text("Prisma Widgets " + BuildConfig.VERSION_NAME, ui.cond, 13f, Ui.DIM).apply { gravity = Gravity.CENTER },
            LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                topMargin = ui.dp(6)
            })
        setScreen(col, keepScroll = true)
        previewPill()
    }

    /**
     * Botón flotante «Vista previa»: aparece cuando la vista previa sale de la pantalla,
     * para ver el resultado sin volver arriba.
     */
    internal fun previewPill() {
        val host = findViewById<android.view.ViewGroup>(android.R.id.content) ?: return
        val sv = scroll ?: return
        var pill = host.findViewWithTag<TextView>("pvPill")
        if (pill == null) {
            pill = ui.text("Vista previa", ui.condSemi, 15f, Ui.HEAD).apply {
                tag = "pvPill"
                gravity = Gravity.CENTER
                minHeight = ui.dp(44); minimumHeight = ui.dp(44)
                background = ui.rect(Ui.PLATE_TOP, 22f, ui.accentLine, 1.5f)
                setPadding(ui.dp(18), 0, ui.dp(18), 0)
                elevation = ui.dp(6).toFloat()
                contentDescription = "Ver la vista previa del widget"
                visibility = View.GONE
                setOnClickListener { visibility = View.GONE; openFullPreview() }
            }
            val top = ViewCompat.getRootWindowInsets(host)?.getInsets(WindowInsetsCompat.Type.systemBars())?.top ?: 0
            host.addView(pill, android.widget.FrameLayout.LayoutParams(-2, -2, Gravity.TOP or Gravity.END).apply {
                topMargin = top + ui.dp(10); marginEnd = ui.dp(16)
            })
        } else {
            // Colores al día si cambió el modo claro/oscuro o el acento
            pill.setTextColor(Ui.HEAD)
            pill.background = ui.rect(Ui.PLATE_TOP, 22f, ui.accentLine, 1.5f)
        }
        val p = pill
        fun update() {
            val card = preview?.card
            val hidden = card != null && card.parent != null && sv.scrollY > card.bottom - ui.dp(40)
            val want = if (hidden && !fullPreviewOpen) View.VISIBLE else View.GONE
            if (p.visibility != want) {
                if (want == View.VISIBLE) { p.alpha = 0f; p.visibility = View.VISIBLE; p.animate().alpha(1f).setDuration(150).start() }
                else p.visibility = View.GONE
            }
        }
        sv.setOnScrollChangeListener { _, _, _, _, _ -> update() }
        sv.post { update() }
    }

    /** Aplica el modo claro u oscuro de la configuración (y los íconos de la barra de estado). */
    internal fun applyUiMode() {
        val night = (resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) ==
            android.content.res.Configuration.UI_MODE_NIGHT_YES
        val light = when (Prefs.uiMode(this)) { "light" -> true; "system" -> !night; else -> false }
        Ui.setLight(light)
        window.decorView.setBackgroundColor(Ui.INK)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            isAppearanceLightStatusBars = light
            isAppearanceLightNavigationBars = light
        }
    }

    /** Pone la pantalla dentro de un scroll que respeta barras del sistema y teclado. */
    internal fun setScreen(content: View, keepScroll: Boolean) {
        val y = if (keepScroll) scroll?.scrollY ?: 0 else 0
        // Si ya hay pantalla, solo se cambia su contenido (sin reemplazar la ventana
        // ni saltar el scroll): así tocar un botón no produce destellos.
        scroll?.let { sv ->
            // Misma pantalla: ya está puesta, no se toca (sin saltos ni parpadeos)
            if (sv.parent != null && sv.getChildAt(0) === content) { sv.setBackgroundColor(Ui.INK); return }
            if (sv.parent != null) {
                sv.setBackgroundColor(Ui.INK)
                sv.removeAllViews()
                sv.addView(content)
                // El scroll se ubica justo antes de dibujar, cuando el contenido nuevo ya
                // tiene su alto (si se hace antes, Android lo lleva al principio).
                val target = if (keepScroll) y else 0
                sv.viewTreeObserver.addOnPreDrawListener(object : android.view.ViewTreeObserver.OnPreDrawListener {
                    override fun onPreDraw(): Boolean {
                        sv.viewTreeObserver.removeOnPreDrawListener(this)
                        sv.scrollTo(0, target)
                        return true
                    }
                })
                return
            }
        }
        val sv = ScrollView(this).apply {
            setBackgroundColor(Ui.INK)
            isFillViewport = true
            clipToPadding = true   // el contenido no pasa por detrás de la barra de estado
            addView(content)
        }
        ViewCompat.setOnApplyWindowInsetsListener(sv) { v, insets ->
            val b = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.ime() or WindowInsetsCompat.Type.displayCutout())
            v.setPadding(b.left, b.top, b.right, b.bottom)
            insets
        }
        scroll = sv
        setContentView(sv)
        sv.post { sv.scrollTo(0, y) }
    }

    internal fun topBar(): View {
        val brand = ui.text("Prisma", ui.anton, 34f, Ui.HEAD, 0.03f).apply {
            setShadowLayer(ui.dp(3).toFloat(), 0f, ui.dp(2).toFloat(), Color.parseColor(if (Ui.light) "#26000000" else "#E6000000"))
        }
        brand.post {
            val w = brand.paint.measureText(brand.text.toString())
            // En modo claro, metal oscuro (acero pavonado) para que no se pierda sobre el fondo claro
            val colors = if (Ui.light) intArrayOf(
                Color.parseColor("#3A342E"), Color.parseColor("#7A7168"), Color.parseColor("#5E564E"),
                Color.parseColor("#2A2521"), Color.parseColor("#14110E"), Color.parseColor("#7A5532"),
                Color.parseColor("#4A433C"), Color.parseColor("#8A8178"), Color.parseColor("#2A2521")
            ) else Graphics.METAL_COLORS
            brand.paint.shader = LinearGradient(0f, 0f, w, 0f, colors, Graphics.METAL_STOPS, Shader.TileMode.CLAMP)
            brand.invalidate()
        }
        val left = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(LinearLayout(this@MainActivity).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.BOTTOM
                addView(brand)
                addView(ui.text("WIDGETS", ui.condSemi, 14f, Ui.MUTED, 0.2f).apply { setPadding(ui.dp(8), 0, 0, ui.dp(7)) })
            })
            addView(ui.text("Ajustes del widget", ui.cond, 15f, Ui.MUTED).apply { setPadding(0, ui.dp(4), 0, 0) })
        }
        val active = RockWidget.hasWidgets(this)
        val status = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            addView(ui.dot(if (active) Ui.OK else Ui.DIM), LinearLayout.LayoutParams(ui.dp(7), ui.dp(7)))
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

    /** Aviso propio (píldora abajo que se desvanece), con el estilo de la app. */
    internal fun toast(msg: String) {
        val host = findViewById<android.view.ViewGroup>(android.R.id.content)
        if (host == null || fullPreviewOpen) { Toast.makeText(this, msg, Toast.LENGTH_SHORT).show(); return }
        host.findViewWithTag<View>("pill")?.let { host.removeView(it) }
        val pill = ui.text(msg, ui.condMedium, 16f, Ui.HEAD).apply {
            tag = "pill"
            gravity = Gravity.CENTER
            background = ui.rect(Ui.PLATE_TOP, 24f, Ui.veil(0x24), 1f)
            setPadding(ui.dp(20), ui.dp(12), ui.dp(20), ui.dp(12))
            elevation = ui.dp(8).toFloat()
            alpha = 0f; translationY = ui.dp(16).toFloat()
        }
        val bottom = ViewCompat.getRootWindowInsets(host)?.getInsets(WindowInsetsCompat.Type.systemBars())?.bottom ?: 0
        host.addView(pill, android.widget.FrameLayout.LayoutParams(-2, -2,
            Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL).apply { bottomMargin = bottom + ui.dp(24); marginStart = ui.dp(24); marginEnd = ui.dp(24) })
        pill.animate().alpha(1f).translationY(0f).setDuration(180).start()
        pill.postDelayed({
            pill.animate().alpha(0f).setDuration(250).withEndAction { host.removeView(pill) }.start()
        }, 2500)
    }
    internal var fullPreviewOpen = false
    internal var tabsView: View? = null

    companion object {
        /** En qué pestaña vive cada sección. */
        val TAB_OF = mapOf(
            "tema" to 0, "fondo" to 0, "fondoWidget" to 0, "acento" to 0,
            "diseno" to 1, "apps" to 1, "clima" to 1, "reproductor" to 1,
            "w_pack" to 2, "w_clock" to 2, "w_apps" to 2, "w_player" to 2, "dream" to 2,
            "apariencia" to 3)
    }
    internal var permCelebrated = false
}
