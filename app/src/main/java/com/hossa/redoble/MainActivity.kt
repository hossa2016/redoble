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

    /** Reseña dentro de la app: una sola vez, al tercer día de uso, nunca durante la guía. */
    private fun maybeAskReview() {
        if (!Prefs.onboarded(this) || Prefs.reviewAsked(this)) return
        if (Prefs.markDayUsed(this) < 3) return
        Prefs.setReviewAsked(this)
        try {
            val rm = com.google.android.play.core.review.ReviewManagerFactory.create(this)
            rm.requestReviewFlow().addOnCompleteListener { t ->
                if (t.isSuccessful) try { rm.launchReviewFlow(this, t.result) } catch (e: Exception) { }
            }
        } catch (e: Exception) { }
    }

    /** Copia de los ajustes: guardar y restaurar un archivo elegido por el usuario. */
    internal val exportDoc = registerForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) io.execute {
            val ok = try { Backup.export(this, uri); true } catch (e: Exception) { ErrorLog.soft(this, e, "exportar"); false }
            onUi { toast(if (ok) "Copia guardada" else "No se pudo guardar la copia") }
        }
    }
    internal val importDoc = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) io.execute {
            val n = try { Backup.import(this, uri) } catch (e: Exception) { ErrorLog.soft(this, e, "restaurar"); -1 }
            onUi { afterRestore(n) }
        }
    }

    /** Tras restaurar una copia o un setup: se redibuja todo con los ajustes nuevos. */
    internal fun afterRestore(n: Int) {
        if (n < 0) { toast("Ese archivo no es una copia de Prisma"); return }
        RockWidget.updateAll(this)
        Widgets.updateKind(this, Widgets.APPS)
        toast("Ajustes restaurados")
        preview = null; mainCol = null; photo = null
        recreate()
    }

    /** Video propio para el fondo animado (selector del sistema: no pide permisos). */
    internal val pickVideo = registerForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri == null) return@registerForActivityResult
        toast("Preparando tu video…")
        io.execute {
            val tmp = java.io.File(filesDir, "video_propio.tmp")
            val ok = try {
                var total = 0L
                contentResolver.openInputStream(uri)?.use { input ->
                    tmp.outputStream().use { out ->
                        val buf = ByteArray(256 * 1024)
                        while (true) {
                            val r = input.read(buf); if (r < 0) break
                            total += r; if (total > 80L * 1024 * 1024) throw java.io.IOException("muy grande")
                            out.write(buf, 0, r)
                        }
                    }
                } ?: throw java.io.IOException("sin video")
                tmp.renameTo(java.io.File(filesDir, "video_propio.mp4"))
            } catch (e: Exception) { tmp.delete(); ErrorLog.soft(this, e, "video propio"); false }
            onUi {
                if (!ok) { toast("Ese video es muy grande o no se pudo abrir (máximo 80 MB)"); return@onUi }
                Prefs.setLiveScene(this, "propio"); Prefs.setWallStyle(this, "anim")
                RockWidget.updateAll(this)
                val ours = android.app.WallpaperManager.getInstance(this).wallpaperInfo?.packageName == packageName
                if (ours) toast("Fondo aplicado: Mi video") else launchLiveWallpaper()
                build()
            }
        }
    }

    /** Permiso opcional de la agenda (solo al activar «Próximo evento»). */
    internal val calendarPerm = registerForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        Prefs.setShowAgenda(this, ok)
        if (!ok) toast("Sin permiso de la agenda no se puede mostrar el próximo evento")
        RockWidget.updateAll(this); build()
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
        maybeAskReview()
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
        val th = Themes.saved(this)
        applyUiMode()
        // Temas sin color propio (Claro): el acento de la marca, para que la app no quede apagada
        val base = if (th.light) Color.parseColor(if (Ui.light) "#A8443A" else "#E58272") else th.accent(this)
        // En modo claro los acentos muy claros (plata, ámbar, cian) se oscurecen para que se lean
        val accent = if (Ui.light && androidx.core.graphics.ColorUtils.calculateLuminance(base) > 0.4)
            androidx.core.graphics.ColorUtils.blendARGB(base, Color.BLACK, 0.35f) else base
        ui = Ui(this, accent)
        if (!Prefs.onboarded(this)) {
            findViewById<android.view.ViewGroup>(android.R.id.content)?.findViewWithTag<View>("pvPill")?.visibility = View.GONE
            setScreen(onboardingView(), keepScroll = false); return
        }

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
        val tabs = ui.segmented(TAB_NAMES, tab) { t -> selectTab(t) }
        // TalkBack: cada opción se anuncia como «pestaña» (sin parada extra en el contenedor)
        fun markTabs(v: View) {
            if (v is android.widget.Button) {
                ViewCompat.setAccessibilityDelegate(v, object : androidx.core.view.AccessibilityDelegateCompat() {
                    override fun onInitializeAccessibilityNodeInfo(host: View, info: androidx.core.view.accessibility.AccessibilityNodeInfoCompat) {
                        super.onInitializeAccessibilityNodeInfo(host, info)
                        info.roleDescription = "pestaña"
                    }
                })
            } else if (v is android.view.ViewGroup) for (k in 0 until v.childCount) markTabs(v.getChildAt(k))
        }
        markTabs(tabs)
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
                col.addView(fold(rulesPlate(), "reglas", Rules.act(this).ifBlank {
                    if (Prefs.ruleRain(this) || Prefs.ruleNight(this) || Prefs.ruleHeadphones(this)) "Esperando su momento" else "Apagado" }))
                if (th.usesUserAccent) col.addView(fold(accentPlate(), "acento",
                    Prefs.NAMES[Prefs.accentIndex(this).coerceIn(0, Prefs.NAMES.size - 1)]))
            }
            1 -> {
                // El widget: qué muestra y cómo están los widgets sueltos
                if (Widgets.has(this, Widgets.FULL) || !hasWidget) col.addView(fold(designPlate(), "diseno"))
                col.addView(fold(setlistPlate(), "apps"))
                col.addView(fold(headerExtrasPlate(), "cabecera", listOfNotNull(
                    "Cartel".takeIf { Prefs.poster(this) }, "Alarma".takeIf { Prefs.showAlarm(this) },
                    "Agenda".takeIf { Prefs.showAgenda(this) }).joinToString(" · ").ifBlank { "Fecha normal" }))
                col.addView(fold(weatherPlate(), "clima", Prefs.city(this).substringBefore(",").ifBlank { "Agregar ciudad ›" }))
                val total = listOf(Widgets.has(this, Widgets.FULL), clockW, appsW, playerW, MiniWidget.placed(this)).count { it }
                col.addView(fold(packPlate(), "w_pack", I18n.tr(this, "{} de 5 en pantalla").replace("{}", total.toString())))
                if (clockW) col.addView(fold(clockWidgetPlate(), "w_clock", listOfNotNull(
                    "Día".takeIf { Prefs.cw(this, "dia") }, "Clima".takeIf { Prefs.cw(this, "clima") },
                    "Mañana".takeIf { Prefs.cw(this, "manana") }, "Batería".takeIf { Prefs.cw(this, "bateria") }).joinToString(" · ")))
                if (appsW) col.addView(fold(appsWidgetsPlate(), "w_apps", Widgets.ids(this, Widgets.APPS).size.let { if (it == 1) "1 widget" else "$it widgets" }))
                if (playerW) col.addView(fold(playerWidgetPlate(), "w_player", if (Prefs.pwIdle(this) == "last") "Sin música: última canción" else "Sin música: nada sonando"))
            }
            2 -> {
                col.addView(fold(playerPlate(), "reproductor", if (Player.enabled(this)) "Activo" else "Falta dar permiso ›"))
                col.addView(fold(musicExtrasPlate(), "musica_extras", listOfNotNull(
                    "Escenario Vivo".takeIf { Prefs.liveStage(this) }, "Temporizador".takeIf { SleepTimer.left(this) > 0 },
                    "Historial".takeIf { Prefs.tourLog(this) }).joinToString(" · ").ifBlank { "Apagado" }))
                col.addView(fold(sharePlate(), "comparte", "Entrada, afiches y setup"))
            }
            else -> {
                col.addView(fold(appearancePlate(hasWidget), "apariencia",
                    when (Prefs.uiMode(this)) { "system" -> "Sistema"; "light" -> "Claro"; else -> "Oscuro" }))
                col.addView(fold(performancePlate(), "rendimiento", if (Prefs.liveUpdates(this)) "Actualización en vivo" else "Normal"))
                col.addView(fold(dreamPlate(), "dream", if (Prefs.dreamBg(this) == "black") "Fondo negro" else "Con tu fondo"))
                col.addView(fold(funPlate(), "divertidos", listOfNotNull("Voz rock".takeIf { Prefs.rockVoice(this) },
                    "Hasta el 11".takeIf { Prefs.amp11(this) }).joinToString(" · ").ifBlank { "Apagado" }))
            }
        }

        col.addView(ui.text("Prisma Widgets " + BuildConfig.VERSION_NAME, ui.cond, 13f, Ui.DIM).apply { gravity = Gravity.CENTER },
            LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                topMargin = ui.dp(6)
            })
        setScreen(col, keepScroll = true)
        previewPill()
    }

    /** Cambia de pestaña y deja las pestañas a la vista. */
    internal fun selectTab(t: Int) {
        Prefs.setSettingsTab(this, t)
        openSections.clear(); Prefs.setOpenSections(this, openSections)
        build()
        scroll?.let { sv -> sv.post { tabsView?.let { tv -> if (sv.scrollY > tv.top) sv.smoothScrollTo(0, (tv.top - ui.dp(8)).coerceAtLeast(0)) } } }
    }

    /**
     * Barra fija arriba al bajar: miniatura del widget (tócala para verlo en grande)
     * y las pestañas. Reemplaza al botón flotante, que tapaba los títulos.
     */
    /** Foto actual de la vista previa para la miniatura de la barra fija. */
    internal fun snapThumb(thumb: android.widget.ImageView? = null) {
        val t = thumb ?: findViewById<android.view.ViewGroup>(android.R.id.content)?.findViewWithTag<android.widget.ImageView>("pvThumb") ?: return
        val fr = preview?.frame ?: return
        if (fr.width <= 0) return
        try {
            val bmp = android.graphics.Bitmap.createBitmap(fr.width, fr.height, android.graphics.Bitmap.Config.ARGB_8888)
            val c = android.graphics.Canvas(bmp)
            // El video no se copia con draw(): se pinta su cuadro actual primero
            preview?.video?.frame()?.let { c.drawBitmap(it, null, android.graphics.Rect(0, 0, fr.width, fr.height), null) }
            fr.draw(c); t.setImageBitmap(bmp)
        } catch (e: Exception) { }
    }

    internal fun previewPill() {
        val host = findViewById<android.view.ViewGroup>(android.R.id.content) ?: return
        val sv = scroll ?: return
        host.findViewWithTag<View>("pvPill")?.let { host.removeView(it) }
        val thumb = android.widget.ImageView(this).apply {
            scaleType = android.widget.ImageView.ScaleType.CENTER_CROP
            background = ui.rect(Ui.PLATE_TOP, 10f, Ui.headA(0x33), 1f)
            clipToOutline = true
            outlineProvider = object : android.view.ViewOutlineProvider() {
                override fun getOutline(view: View, outline: android.graphics.Outline) {
                    outline.setRoundRect(0, 0, view.width, view.height, ui.dp(10).toFloat())
                }
            }
            contentDescription = "Ver la vista previa del widget"
            setOnClickListener { openFullPreview() }
        }
        val bar = LinearLayout(this).apply {
            tag = "pvPill"
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setBackgroundColor(Ui.INK)
            elevation = ui.dp(4).toFloat()
            setPadding(ui.dp(12), ui.dp(6), ui.dp(12), ui.dp(6))
            visibility = View.GONE
            addView(thumb, LinearLayout.LayoutParams(ui.dp(48), ui.dp(68)).apply { marginEnd = ui.dp(10) })
            addView(ui.segmented(TAB_NAMES, Prefs.settingsTab(this@MainActivity)) { t -> selectTab(t) },
                LinearLayout.LayoutParams(0, -2, 1f))
        }
        host.addView(bar, android.widget.FrameLayout.LayoutParams(-1, -2, Gravity.TOP))
        ViewCompat.setOnApplyWindowInsetsListener(bar) { v, ins ->
            val b = ins.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
            v.setPadding(ui.dp(12) + b.left, ui.dp(6) + b.top, ui.dp(12) + b.right, ui.dp(6)); ins
        }
        ViewCompat.requestApplyInsets(bar)
        fun update() {
            val tv = tabsView
            val onMain = Prefs.onboarded(this) && sv.getChildAt(0) === mainCol
            val want = if (onMain && !fullPreviewOpen && tv != null && tv.parent != null && sv.scrollY > tv.top) View.VISIBLE else View.GONE
            if (bar.visibility != want) {
                if (want == View.VISIBLE) {
                    // Foto del momento de la vista previa para la miniatura
                    snapThumb(thumb); bar.postDelayed({ snapThumb(thumb) }, 400)
                    bar.alpha = 0f; bar.visibility = View.VISIBLE; bar.animate().alpha(1f).setDuration(150).start()
                } else bar.visibility = View.GONE
            }
        }
        thumb.tag = "pvThumb"
        // Mientras se ve, la miniatura se renueva (la hora no se queda atrás)
        val tick = object : Runnable {
            override fun run() {
                if (!bar.isAttachedToWindow) return
                if (bar.visibility == View.VISIBLE) snapThumb(thumb)
                // Justo al cambiar el minuto (la hora de la miniatura no se queda atrás)
                bar.postDelayed(this, 60_500L - System.currentTimeMillis() % 60_000L)
            }
        }
        bar.postDelayed(tick, 60_500L - System.currentTimeMillis() % 60_000L)
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
        I18n.translateTree(this, content)
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
            // Estado en un chip pequeño bajo el logo (secundario, no compite con la marca)
            val active = RockWidget.hasWidgets(this@MainActivity)
            addView(LinearLayout(this@MainActivity).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                background = ui.rect(Ui.veil(0x0C), 12f, Ui.veil(0x1A), 1f)
                setPadding(ui.dp(10), ui.dp(3), ui.dp(12), ui.dp(3))
                addView(ui.dot(if (active) Ui.OK else Ui.DIM), LinearLayout.LayoutParams(ui.dp(7), ui.dp(7)))
                addView(ui.text(if (active) "Widget activo" else "Widget sin agregar", ui.cond, 14f, Ui.MUTED).apply {
                    setPadding(ui.dp(6), 0, 0, 0)
                })
            }, LinearLayout.LayoutParams(-2, -2).apply { topMargin = ui.dp(6) })
        }
        return LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(ui.dp(20), ui.dp(14), ui.dp(20), ui.dp(18))
            addView(left, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        }
    }

    /** Aviso propio (píldora abajo que se desvanece), con el estilo de la app. */
    internal fun toast(msg0: String) {
        val msg = I18n.tr(this, msg0)
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
        val TAB_NAMES = listOf(0 to "Estilo", 1 to "Widget", 2 to "Música", 3 to "Más")
        val TAB_OF = mapOf(
            "tema" to 0, "fondo" to 0, "fondoWidget" to 0, "reglas" to 0, "acento" to 0,
            "diseno" to 1, "apps" to 1, "cabecera" to 1, "clima" to 1,
            "w_pack" to 1, "w_clock" to 1, "w_apps" to 1, "w_player" to 1,
            "reproductor" to 2, "musica_extras" to 2, "comparte" to 2,
            "apariencia" to 3, "rendimiento" to 3, "dream" to 3, "divertidos" to 3)
    }
    internal var permCelebrated = false
}
