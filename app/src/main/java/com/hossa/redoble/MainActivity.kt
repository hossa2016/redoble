package com.hossa.redoble

import android.app.WallpaperManager
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.PorterDuff
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.WindowManager
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.RadioGroup
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import java.util.concurrent.Executors

class MainActivity : AppCompatActivity() {

    private val io = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())

    private lateinit var preview: ImageView
    private lateinit var dimLabel: TextView
    private lateinit var slots: LinearLayout
    private var photo: Bitmap? = null

    private val picker = registerForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) loadPhoto(uri)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        preview = findViewById(R.id.preview)
        dimLabel = findViewById(R.id.dimLabel)
        slots = findViewById(R.id.slots)

        // ---- Fondo ----
        findViewById<Button>(R.id.btnPick).setOnClickListener {
            picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
        }
        val dim = findViewById<SeekBar>(R.id.dim)
        dim.progress = Prefs.dim(this)
        applyDimPreview(dim.progress)
        dim.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar, value: Int, fromUser: Boolean) {
                applyDimPreview(value)
            }
            override fun onStartTrackingTouch(sb: SeekBar) {}
            override fun onStopTrackingTouch(sb: SeekBar) = Prefs.setDim(this@MainActivity, sb.progress)
        })
        findViewById<Button>(R.id.btnApply).setOnClickListener { applyWallpaper() }

        // ---- Color ----
        val colors = findViewById<RadioGroup>(R.id.colors)
        val radioIds = intArrayOf(R.id.cRed, R.id.cBlue, R.id.cSilver)
        colors.check(radioIds[Prefs.accentIndex(this).coerceIn(0, 2)])
        colors.setOnCheckedChangeListener { _, checkedId ->
            Prefs.setAccentIndex(this, radioIds.indexOf(checkedId).coerceAtLeast(0))
            RockWidget.updateAll(this)
        }

        // ---- Clima ----
        val city = findViewById<EditText>(R.id.city)
        val cityStatus = findViewById<TextView>(R.id.cityStatus)
        city.setText(Prefs.city(this))
        if (Prefs.city(this).isNotBlank()) cityStatus.text = "Clima de ${Prefs.city(this)}"
        findViewById<Button>(R.id.btnCity).setOnClickListener {
            val name = city.text.toString()
            if (name.isBlank()) {
                cityStatus.text = "Escribe el nombre de tu ciudad"
                return@setOnClickListener
            }
            cityStatus.text = "Buscando…"
            io.execute {
                val result = try { WeatherWorker.geocode(name) } catch (e: Exception) { null }
                main.post {
                    if (result == null) {
                        cityStatus.text = "No encontré esa ciudad. Revisa el nombre o tu conexión."
                    } else {
                        Prefs.setCity(this, result.first, result.second, result.third)
                        city.setText(result.first)
                        cityStatus.text = "Clima de ${result.first}"
                        WeatherWorker.runNow(this)
                        WeatherWorker.schedule(this)
                    }
                }
            }
        }

        // ---- Apps ----
        buildSlots()

        // ---- Widget ----
        findViewById<Button>(R.id.btnWidget).setOnClickListener { pinWidget() }
    }

    override fun onResume() {
        super.onResume()
        RockWidget.updateAll(this)
    }

    // ---------- Fondo ----------

    private fun applyDimPreview(value: Int) {
        dimLabel.text = "Oscurecer: $value%"
        val alpha = value * 255 / 100
        preview.setColorFilter(Color.argb(alpha, 0, 0, 0), PorterDuff.Mode.SRC_ATOP)
    }

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
            main.post {
                if (bmp == null) {
                    toast("No pude abrir esa foto")
                } else {
                    photo = bmp
                    preview.setImageBitmap(bmp)
                }
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

    private fun applyWallpaper() {
        val src = photo
        if (src == null) {
            toast("Primero elige una foto")
            return
        }
        val (w, h) = screenSize()
        val dim = Prefs.dim(this)
        toast("Aplicando fondo…")
        io.execute {
            val ok = try {
                val wall = Graphics.makeWallpaper(src, w, h, dim)
                WallpaperManager.getInstance(this).setBitmap(
                    wall, null, true,
                    WallpaperManager.FLAG_SYSTEM or WallpaperManager.FLAG_LOCK
                )
                true
            } catch (e: Exception) {
                false
            }
            main.post { toast(if (ok) "Fondo aplicado" else "No se pudo aplicar el fondo") }
        }
    }

    // ---------- Apps ----------

    private fun buildSlots() {
        slots.removeAllViews()
        val pm = packageManager
        for (i in 0 until Prefs.SLOTS) {
            val pkg = Prefs.app(this, i)
            val label = if (pkg.isBlank()) "(vacío)" else try {
                pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
            } catch (e: Exception) {
                "(vacío)"
            }
            val btn = Button(this, null, 0, R.style.RockButton).apply {
                text = "${i + 1}.  $label"
                gravity = Gravity.START or Gravity.CENTER_VERTICAL
                setPadding((16 * resources.displayMetrics.density).toInt(), 0, 0, 0)
                setOnClickListener { chooseApp(i) }
            }
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                (52 * resources.displayMetrics.density).toInt()
            ).apply { topMargin = (10 * resources.displayMetrics.density).toInt() }
            slots.addView(btn, lp)
        }
    }

    private fun chooseApp(slot: Int) {
        val pm = packageManager
        val launcher = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val apps = pm.queryIntentActivities(launcher, 0)
            .map { Pair(it.loadLabel(pm).toString(), it.activityInfo.packageName) }
            .filter { it.second != packageName }
            .distinctBy { it.second }
            .sortedBy { it.first.lowercase() }
        val names = arrayOf("(vacío)") + apps.map { it.first }.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle("Espacio ${slot + 1}")
            .setItems(names) { _, which ->
                Prefs.setApp(this, slot, if (which == 0) "" else apps[which - 1].second)
                buildSlots()
                RockWidget.updateAll(this)
            }
            .show()
    }

    // ---------- Widget ----------

    private fun pinWidget() {
        val mgr = AppWidgetManager.getInstance(this)
        val provider = ComponentName(this, RockWidget::class.java)
        val ok = try {
            mgr.isRequestPinAppWidgetSupported && mgr.requestPinAppWidget(provider, null, null)
        } catch (e: Exception) {
            false
        }
        if (!ok) {
            AlertDialog.Builder(this)
                .setTitle("Agregar el widget")
                .setMessage(
                    "Mantén presionado un espacio vacío de la pantalla de inicio, toca Widgets, " +
                        "busca Redoble y arrástralo a la pantalla."
                )
                .setPositiveButton("Entendido", null)
                .show()
        }
    }

    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
}
