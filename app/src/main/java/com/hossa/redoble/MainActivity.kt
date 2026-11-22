package com.hossa.redoble

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
        WindowInsetsControllerCompat(window, window.decorView).isAppearanceLightStatusBars = false
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

    override fun onResume() {
        super.onResume()
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
        ui = Ui(this, if (th.light) Color.parseColor("#D8CFC2") else th.accent(this))
        if (!Prefs.onboarded(this)) { setScreen(onboardingView(), keepScroll = false); return }

        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, ui.dp(14), 0, ui.dp(40))
        }
        val hasWidget = RockWidget.hasWidgets(this)
        col.addView(topBar())
        if (!hasWidget) col.addView(widgetPlate())
        col.addView(previewPlate())

        col.addView(ui.groupHeader("Estilo", "Cómo se ve tu widget"))
        col.addView(themePlate())
        col.addView(wallpaperPlate())
        col.addView(widgetBgPlate())
        if (th.usesUserAccent) col.addView(accentPlate())

        col.addView(ui.groupHeader("Contenido", "Qué muestra"))
        col.addView(designPlate())
        col.addView(setlistPlate())
        col.addView(weatherPlate())

        col.addView(ui.groupHeader("Reproductor y permisos", "Tu música en el widget"))
        col.addView(playerPlate())
        if (hasWidget) col.addView(widgetDonePlate())

        col.addView(ui.text("Prisma Widgets " + BuildConfig.VERSION_NAME, ui.cond, 13f, Ui.DIM).apply { gravity = Gravity.CENTER },
            LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                topMargin = ui.dp(6)
            })
        setScreen(col, keepScroll = true)
    }

    /** Pone la pantalla dentro de un scroll que respeta barras del sistema y teclado. */
    internal fun setScreen(content: View, keepScroll: Boolean) {
        val y = if (keepScroll) scroll?.scrollY ?: 0 else 0
        val sv = ScrollView(this).apply {
            setBackgroundColor(Ui.INK)
            isFillViewport = true
            clipToPadding = false
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
            setShadowLayer(ui.dp(3).toFloat(), 0f, ui.dp(2).toFloat(), Color.parseColor("#E6000000"))
        }
        brand.post {
            val w = brand.paint.measureText(brand.text.toString())
            brand.paint.shader = LinearGradient(0f, 0f, w, 0f, Graphics.METAL_COLORS, Graphics.METAL_STOPS, Shader.TileMode.CLAMP)
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

    internal fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
}
