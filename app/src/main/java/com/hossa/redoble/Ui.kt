package com.hossa.redoble

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
import android.graphics.drawable.StateListDrawable
import android.text.TextUtils
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.res.ResourcesCompat
import androidx.core.graphics.ColorUtils

/** Paleta, fuentes y piezas reutilizables de la app de ajustes. */
class Ui(val ctx: Context, val accent: Int) {

    companion object {
        /** true: la configuración se ve en modo claro. */
        var light = false; private set
        var INK = 0; private set
        var PLATE_TOP = 0; private set
        var PLATE_BOTTOM = 0; private set
        var PLATE_PRESSED = 0; private set
        var WELL = 0; private set
        var GROOVE = 0; private set
        var HEAD = 0; private set
        var MUTED = 0; private set
        var DIM = 0; private set  // contraste ≥ 4.5:1 sobre las tarjetas
        var OK = 0; private set
        var ALERT = 0; private set

        init { setLight(false) }

        fun setLight(on: Boolean) {
            light = on
            fun c(dark: String, clear: String) = Color.parseColor(if (on) clear else dark)
            INK = c("#0E0B09", "#F3EFE9")
            PLATE_TOP = c("#1C1713", "#FFFFFF")
            PLATE_BOTTOM = c("#141110", "#FBF8F4")
            PLATE_PRESSED = c("#1F1915", "#ECE6DE")
            WELL = c("#0A0807", "#EEE9E2")
            GROOVE = c("#2A231E", "#D9D1C6")
            HEAD = c("#EDE6DC", "#1C1713")
            MUTED = c("#9C9187", "#5B524A")
            DIM = c("#8A7F75", "#6B6158")
            OK = c("#7FB38A", "#2F7A45")
            ALERT = c("#E24B4A", "#C42F2D")
        }

        /** Velo para bordes y separadores: blanco translúcido en oscuro, negro en claro. */
        fun veil(alpha: Int) = if (light) Color.argb((alpha * 1.4f).toInt().coerceAtMost(255), 40, 30, 20) else Color.argb(alpha, 255, 255, 255)
        /** Color de los títulos con transparencia (bordes suaves). */
        fun headA(alpha: Int) = ColorUtils.setAlphaComponent(HEAD, alpha)
    }

    /** Colores claros (plata, ámbar, verde, cian) llevan texto oscuro encima. */
    val silver = ColorUtils.calculateLuminance(accent) > 0.35
    val onAccent = if (silver) Color.parseColor("#14110E") else Color.WHITE
    val accentSoft = ColorUtils.setAlphaComponent(accent, 0x29)
    val accentLine = ColorUtils.setAlphaComponent(accent, 0x8C)
    val accentDark = ColorUtils.blendARGB(accent, Color.BLACK, 0.18f)
    val accentLight = ColorUtils.blendARGB(accent, Color.WHITE, 0.08f)

    private val fonts = HashMap<Int, Typeface>()
    fun font(id: Int): Typeface = fonts.getOrPut(id) {
        ResourcesCompat.getFont(ctx, id) ?: Typeface.DEFAULT
    }

    val anton get() = font(R.font.anton_regular)
    val condLight get() = font(R.font.barlow_condensed_light)
    val cond get() = font(R.font.barlow_condensed_regular)
    val condMedium get() = font(R.font.barlow_condensed_medium)
    val condSemi get() = font(R.font.barlow_condensed_semibold)
    val body get() = font(R.font.barlow_regular)

    fun dp(v: Float): Int = (v * ctx.resources.displayMetrics.density + 0.5f).toInt()
    fun dp(v: Int): Int = dp(v.toFloat())

    // ---------------- Texto ----------------
    fun text(
        s: CharSequence, face: Typeface, sp: Float, color: Int = HEAD, spacing: Float = 0f
    ) = TextView(ctx).apply {
        text = s
        typeface = face
        setTextSize(TypedValue.COMPLEX_UNIT_SP, sp)
        setTextColor(color)
        letterSpacing = spacing
        includeFontPadding = true
    }

    fun bodyText(s: CharSequence, color: Int = MUTED, sp: Float = 14f) =
        text(s, body, sp, color).apply { setLineSpacing(0f, 1.2f) }

    // ---------------- Formas ----------------
    fun rect(color: Int, radiusDp: Float, strokeColor: Int = 0, strokeDp: Float = 0f) = GradientDrawable().apply {
        setColor(color)
        cornerRadius = dp(radiusDp).toFloat()
        if (strokeDp > 0f) setStroke(dp(strokeDp).coerceAtLeast(1), strokeColor)
    }

    fun oval(color: Int, strokeColor: Int = 0, strokeDp: Float = 0f) = GradientDrawable().apply {
        shape = GradientDrawable.OVAL
        setColor(color)
        if (strokeDp > 0f) setStroke(dp(strokeDp).coerceAtLeast(1), strokeColor)
    }

    fun plate(): Drawable = GradientDrawable(
        GradientDrawable.Orientation.TOP_BOTTOM, intArrayOf(PLATE_TOP, PLATE_BOTTOM)
    ).apply {
        cornerRadius = dp(18).toFloat()
        setStroke(dp(1), veil(0x12))
    }

    fun field(): Drawable {
        val normal = rect(WELL, 12f, veil(0x1F), 1f)
        val focus = rect(WELL, 12f, accent, 1.5f)
        return StateListDrawable().apply {
            addState(intArrayOf(android.R.attr.state_focused), focus)
            addState(intArrayOf(), normal)
        }
    }

    fun iconHolder(): Drawable = GradientDrawable().apply {
        shape = GradientDrawable.OVAL
        gradientType = GradientDrawable.RADIAL_GRADIENT
        gradientRadius = dp(19).toFloat()
        setGradientCenter(0.5f, 0.3f)
        colors = intArrayOf(veil(0x12), veil(0x05))
        setStroke(dp(1), veil(0x24))
    }

    fun dashedHolder(): Drawable = GradientDrawable().apply {
        shape = GradientDrawable.OVAL
        setColor(Color.TRANSPARENT)
        setStroke(dp(1.5f), headA(0x47), dp(4).toFloat(), dp(3).toFloat())
    }

    /** Fondo de fila con estado presionado y seleccionado. */
    fun rowBg(selected: Boolean = false): Drawable {
        val pressed = rect(PLATE_PRESSED, 12f)
        val normal = if (selected) rect(accentSoft, 12f, accentLine, 1f) else rect(Color.TRANSPARENT, 12f)
        return StateListDrawable().apply {
            addState(intArrayOf(android.R.attr.state_pressed), pressed)
            addState(intArrayOf(), normal)
        }
    }

    // ---------------- Botones ----------------
    private fun pressDown(v: View) {
        v.setOnTouchListener { view, e ->
            when (e.action) {
                MotionEvent.ACTION_DOWN -> view.translationY = dp(1).toFloat()
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> view.translationY = 0f
            }
            false
        }
    }

    private fun baseButton(label: String) = Button(ctx, null, 0).apply {
        text = label
        isAllCaps = false
        typeface = condSemi
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 17f)
        letterSpacing = 0.02f
        minHeight = dp(48); minimumHeight = dp(48)
        minWidth = 0; minimumWidth = 0
        setPadding(dp(16), 0, dp(16), 0)
        stateListAnimator = null
        gravity = Gravity.CENTER
    }

    fun primary(label: String) = baseButton(label).apply {
        val normalFill = GradientDrawable(
            GradientDrawable.Orientation.TOP_BOTTOM, intArrayOf(accentLight, accentDark)
        ).apply { cornerRadius = dp(12).toFloat() }
        val shine = GradientDrawable().apply {
            setColor(Color.argb(0x40, 255, 255, 255)); cornerRadius = dp(12).toFloat()
        }
        val normal = LayerDrawable(arrayOf(shine, normalFill)).apply {
            setLayerInset(1, 0, dp(1), 0, 0)
        }
        val pressed = rect(accentDark, 12f)
        val disabled = rect(GROOVE, 12f)
        background = StateListDrawable().apply {
            addState(intArrayOf(-android.R.attr.state_enabled), disabled)
            addState(intArrayOf(android.R.attr.state_pressed), pressed)
            addState(intArrayOf(), normal)
        }
        setTextColor(
            ColorStateList(
                arrayOf(intArrayOf(-android.R.attr.state_enabled), intArrayOf()),
                intArrayOf(DIM, onAccent)
            )
        )
        pressDown(this)
    }

    fun secondary(label: String) = baseButton(label).apply {
        background = StateListDrawable().apply {
            addState(intArrayOf(android.R.attr.state_pressed), rect(veil(0x0F), 12f, headA(0x38), 1f))
            addState(intArrayOf(), rect(Color.TRANSPARENT, 12f, headA(0x38), 1f))
        }
        setTextColor(HEAD)
        pressDown(this)
    }

    fun textButton(label: String) = baseButton(label).apply {
        background = null
        typeface = cond
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
        setTextColor(MUTED)
        minHeight = dp(40); minimumHeight = dp(40)
    }

    /**
     * Selector segmentado único de la app (48 dp, el mínimo táctil recomendado).
     * El texto se achica solo si no cabe, también con letra grande del sistema.
     */
    fun <T> segmented(options: List<Pair<T, String>>, selected: T, onPick: (T) -> Unit): LinearLayout {
        // Con 5 o más opciones van en filas de 3 para que ningún texto se corte,
        // aunque el teléfono tenga la letra grande.
        val perRow = if (options.size >= 5) 3 else options.size
        val well = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            background = rect(WELL, 12f, veil(0x12), 1f)
            setPadding(dp(4), dp(4), dp(4), dp(4))
        }
        var row = LinearLayout(ctx)
        options.forEachIndexed { idx, (value, label) ->
            val i = idx % perRow
            if (i == 0) {
                row = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL; isBaselineAligned = false }
                well.addView(row, LinearLayout.LayoutParams(-1, -2).apply { if (idx > 0) topMargin = dp(3) })
            }
            val on = value == selected
            // Mismo tamaño de letra en todas y centrada; el ancho de cada opción se reparte
            // según lo largo de su texto (así "Desvanecido" cabe junto a "Fijo").
            val size = 15f
            val b = (if (on) primary(label) else textButton(label).apply { setTextColor(MUTED) }).apply {
                setTextSize(TypedValue.COMPLEX_UNIT_SP, size)
                maxLines = 1
                includeFontPadding = false
                gravity = Gravity.CENTER
                setPadding(dp(2), 0, dp(2), 0)
                minHeight = dp(48); minimumHeight = dp(48)
                contentDescription = if (on) "$label, seleccionado" else label
                setOnClickListener { if (!on) onPick(value) }
            }
            val weight = if (perRow < options.size) 1f else label.length + 3f
            row.addView(b, LinearLayout.LayoutParams(0, dp(48), weight).apply { if (i > 0) marginStart = dp(3) })
        }
        // La última fila incompleta se rellena para que sus botones midan igual que los de arriba
        val left = (perRow - options.size % perRow) % perRow
        if (perRow < options.size) repeat(left) {
            row.addView(View(ctx), LinearLayout.LayoutParams(0, dp(48), 1f).apply { marginStart = dp(3) })
        }
        return well
    }

    /** Encabezado de grupo (Estilo, Contenido, Reproductor). */
    fun groupHeader(title: String, sub: String): View = LinearLayout(ctx).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(20), dp(14), dp(20), dp(10))
        addView(text(title.uppercase(), condSemi, 13f, accent, 0.16f))
        addView(text(sub, cond, 14f, MUTED).apply { setPadding(0, dp(2), 0, 0) })
    }

    // ---------------- Secciones ----------------
    fun plateCard(): LinearLayout = LinearLayout(ctx).apply {
        orientation = LinearLayout.VERTICAL
        background = plate()
        setPadding(dp(18), dp(18), dp(18), dp(18))
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { setMargins(dp(16), 0, dp(16), dp(14)) }
    }

    fun sectionTitle(title: String, hint: String? = null): LinearLayout = LinearLayout(ctx).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        val bar = View(ctx).apply { background = rect(accent, 2f) }
        addView(bar, LinearLayout.LayoutParams(dp(3), dp(18)))
        addView(
            text(title.uppercase(), anton, 21f, HEAD, 0.03f),
            LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply { marginStart = dp(10) }
        )
        if (hint != null) addView(text(hint, cond, 14f, DIM).apply { tag = "hint" })
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { bottomMargin = dp(14) }
    }

    fun dot(color: Int) = View(ctx).apply { background = oval(color) }

    /** Círculo con el ícono monocromo de la app. */
    fun appHolder(pkg: String?, sizeDp: Int, iconDp: Int, highlight: Boolean = false): FrameLayout =
        FrameLayout(ctx).apply {
            background = if (pkg.isNullOrBlank()) dashedHolder() else if (highlight) {
                (iconHolder() as GradientDrawable).apply { setStroke(dp(1.5f), accent) }
            } else iconHolder()
            val iv = ImageView(ctx)
            if (pkg.isNullOrBlank()) {
                addView(text("+", condLight, 22f, MUTED).apply { gravity = Gravity.CENTER },
                    FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
            } else {
                val color = Prefs.originalIcons(ctx)
                val size = if (color) sizeDp - 4 else iconDp
                Graphics.appIcon(ctx, pkg, dp(iconDp) * 2, dp(size) * 2, HEAD)?.let { iv.setImageBitmap(it) }
                addView(iv, FrameLayout.LayoutParams(dp(size), dp(size), Gravity.CENTER))
            }
            layoutParams = LinearLayout.LayoutParams(dp(sizeDp), dp(sizeDp))
        }

    fun ellipsize(tv: TextView) = tv.apply { maxLines = 1; ellipsize = TextUtils.TruncateAt.END }
}
