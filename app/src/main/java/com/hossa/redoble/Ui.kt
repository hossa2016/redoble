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
            INK = c("#0E0B09", "#ECE6DE")
            PLATE_TOP = c("#1C1713", "#FFFFFF")
            PLATE_BOTTOM = c("#141110", "#FBF8F4")
            PLATE_PRESSED = c("#1F1915", "#E4DDD3")
            WELL = c("#0A0807", "#EEE9E2")
            GROOVE = c("#2A231E", "#D9D1C6")
            HEAD = c("#EDE6DC", "#1C1713")
            MUTED = c("#9C9187", "#5B524A")
            DIM = c("#958A80", "#655B52")
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
    /**
     * Texto sobre el color de acento: el que más contraste da (blanco o casi negro).
     * Si ni así llega a 4,5:1 (WCAG AA), el relleno del botón se oscurece o aclara un poco.
     */
    val onAccent: Int
    private val btnBase: Int
    init {
        val dark = Color.parseColor("#14110E")
        // Colores medios (carmín, azul, magenta, rojo): letra blanca y relleno algo más oscuro,
        // así no pierden su tono. Solo los claros (plata, ámbar, verde, lima, cian, naranja) van con letra oscura.
        val text = if (ColorUtils.calculateContrast(Color.WHITE, accent) >= 3.0) Color.WHITE else dark
        var base = accent
        var k = 0
        // Se mide contra la parte más clara (arriba) del degradado del botón
        while (k < 14 && ColorUtils.calculateContrast(text,
                if (text == Color.WHITE) ColorUtils.blendARGB(base, Color.WHITE, 0.08f) else ColorUtils.blendARGB(base, Color.BLACK, 0.18f)) < 4.6) {
            base = ColorUtils.blendARGB(base, if (text == Color.WHITE) Color.BLACK else Color.WHITE, 0.06f); k++
        }
        onAccent = text; btnBase = base
    }
    val accentSoft = ColorUtils.setAlphaComponent(accent, 0x29)
    val accentLine = ColorUtils.setAlphaComponent(accent, 0x8C)
    val accentDark = ColorUtils.blendARGB(btnBase, Color.BLACK, 0.18f)
    val accentLight = ColorUtils.blendARGB(btnBase, Color.WHITE, 0.08f)

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
        text = I18n.tr(ctx, s)
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
        text = I18n.tr(ctx, label)
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
        minHeight = dp(48); minimumHeight = dp(48)
    }

    /**
     * Opción de un selector. La elegida va en un tono suave del acento con borde
     * (el rojo relleno queda solo para las acciones principales).
     */
    fun option(label: String, on: Boolean) = baseButton(label).apply {
        val pressed = rect(veil(0x14), 10f)
        val normal = if (on) rect(ColorUtils.setAlphaComponent(accent, if (light) 0x26 else 0x33), 10f, accent, 1.5f)
                     else rect(Color.TRANSPARENT, 10f)
        background = StateListDrawable().apply {
            addState(intArrayOf(android.R.attr.state_pressed), pressed)
            addState(intArrayOf(), normal)
        }
        typeface = if (on) condSemi else cond
        setTextColor(if (on) HEAD else MUTED)
        pressDown(this)
    }

    /** El texto se achica (hasta 12 sp) solo si no cabe; si cabe, queda a su tamaño. */
    fun fitText(tv: TextView, maxSp: Int, minSp: Int = 12) {
        tv.maxLines = 1
        androidx.core.widget.TextViewCompat.setAutoSizeTextTypeUniformWithConfiguration(
            tv, minSp, maxSp, 1, TypedValue.COMPLEX_UNIT_SP)
    }

    /**
     * Descripción de una línea; si hay más que contar, un botón ⓘ la abre en una ventana.
     */
    /**
     * Sistema de texto (tokens): Título 20 condSemi · Etiqueta 17 cond HEAD ·
     * Ayuda 15 cond MUTED (una o dos líneas; lo largo va al ⓘ) · Pie 12 DIM.
     * Espacios: 4 / 8 / 12 / 16 / 24 dp.
     */
    fun caption(s: String) = text(s, cond, 12f, DIM)

    /**
     * Acción dentro de una sección: fila de 56 dp con ícono, texto y flecha
     * (nunca texto gris suelto, que parece un párrafo).
     */
    fun actionRow(label: String, icon: Int, sub: String? = null): LinearLayout = LinearLayout(ctx).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        background = rowBg()
        minimumHeight = dp(56)
        setPadding(dp(10), dp(6), dp(6), dp(6))
        isClickable = true; isFocusable = true
        addView(FrameLayout(ctx).apply {
            background = rect(ColorUtils.setAlphaComponent(accent, if (light) 0x22 else 0x2E), 12f)
            addView(ImageView(ctx).apply {
                setImageResource(icon); imageTintList = ColorStateList.valueOf(accent)
                scaleType = ImageView.ScaleType.CENTER_INSIDE
            }, FrameLayout.LayoutParams(dp(22), dp(22), Gravity.CENTER))
        }, LinearLayout.LayoutParams(dp(36), dp(36)))
        addView(LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), 0, dp(8), 0)
            addView(text(label, cond, 17f, HEAD))
            if (sub != null) addView(text(sub, cond, 13f, DIM))
        }, LinearLayout.LayoutParams(0, -2, 1f))
        addView(ImageView(ctx).apply {
            setImageResource(R.drawable.ic_chevron); imageTintList = ColorStateList.valueOf(DIM)
        }, LinearLayout.LayoutParams(dp(22), dp(22)))
        contentDescription = label
    }

    fun note(short: String, more: String? = null, color: Int = MUTED, sp: Float = 15f): View {
        // Ayuda en la misma familia que las etiquetas (condensada), una sola voz tipográfica
        val t = text(short, cond, sp, color).apply { setLineSpacing(0f, 1.08f) }
        if (more == null) return t
        return LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            addView(t, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            addView(ImageView(ctx).apply {
                setImageResource(R.drawable.ic_info)
                imageTintList = ColorStateList.valueOf(MUTED)
                scaleType = ImageView.ScaleType.CENTER
                background = rowBg()
                contentDescription = "Más información: $short"
                setOnClickListener {
                    sheet(null, more, dismissLabel = "Entendido")
                }
            }, LinearLayout.LayoutParams(dp(48), dp(48)))
        }
    }

    /**
     * Selector segmentado único de la app (48 dp, el mínimo táctil recomendado).
     * El texto se achica solo si no cabe, también con letra grande del sistema.
     */
    fun <T> segmented(options: List<Pair<T, String>>, selected: T, onPick: (T) -> Unit): LinearLayout {
        // Con 5 o más opciones van en dos filas (cada una ocupa todo el ancho) para que ningún texto se corte,
        // aunque el teléfono tenga la letra grande.
        val perRow = if (options.size >= 5) (options.size + 1) / 2 else options.size
        val well = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            background = rect(WELL, 12f, veil(0x12), 1f)
            setPadding(dp(4), dp(4), dp(4), dp(4))
        }
        var row = LinearLayout(ctx)
        var current: TextView? = null
        var picking = false
        options.forEachIndexed { idx, (value, label) ->
            val i = idx % perRow
            if (i == 0) {
                row = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL; isBaselineAligned = false }
                well.addView(row, LinearLayout.LayoutParams(-1, -2).apply { if (idx > 0) topMargin = dp(3) })
            }
            val on = value == selected
            // Mismo tamaño de letra en todas y centrada; el ancho de cada opción se reparte
            // según lo largo de su texto (así "Desvanecido" cabe junto a "Fijo").
            val b = option(label, on).apply {
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
                fitText(this, 15)
                includeFontPadding = false
                gravity = Gravity.CENTER
                setPadding(dp(2), 0, dp(2), 0)
                minHeight = dp(48); minimumHeight = dp(48)
                contentDescription = if (on) "$label, seleccionado" else label
                if (on) current = this
                setOnClickListener {
                    if (on || picking) return@setOnClickListener
                    picking = true; tick()
                    // Transición: la marca pasa de la opción anterior a esta y luego se aplica
                    val sel = rect(ColorUtils.setAlphaComponent(accent, if (light) 0x26 else 0x33), 10f, accent, 1.5f)
                    val none = rect(Color.TRANSPARENT, 10f)
                    background = android.graphics.drawable.TransitionDrawable(arrayOf(none, sel)).also { it.startTransition(160) }
                    setTextColor(HEAD)
                    current?.let { prev ->
                        prev.background = android.graphics.drawable.TransitionDrawable(arrayOf(sel, none)).also { it.startTransition(160) }
                        prev.setTextColor(MUTED)
                    }
                    postDelayed({ onPick(value) }, 170)
                }
            }
            val weight = label.length + 3f
            row.addView(b, LinearLayout.LayoutParams(0, dp(48), weight).apply { if (i > 0) marginStart = dp(3) })
        }
        return well
    }

    /**
     * Fila "Ajustar foto" / "Personalizar elementos" con la misma flecha dibujada de las
     * secciones (abajo cerrada, arriba abierta), en vez de ▸ ▾ de texto.
     */
    fun expander(label: String, open: Boolean, onClick: () -> Unit): View = LinearLayout(ctx).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        minimumHeight = dp(48)
        setPadding(dp(4), 0, 0, 0)
        background = rowBg()
        isClickable = true
        contentDescription = "$label, " + if (open) "abierto, toca para cerrar" else "cerrado, toca para abrir"
        addView(text(label, cond, 16f, HEAD), LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        addView(ImageView(ctx).apply {
            setImageResource(R.drawable.ic_chevron)
            imageTintList = ColorStateList.valueOf(MUTED)
            scaleType = ImageView.ScaleType.CENTER
            rotation = if (open) -90f else 90f
        }, LinearLayout.LayoutParams(dp(40), dp(40)))
        setOnClickListener { onClick() }
    }

    /** Etiqueta pequeña dentro de una tarjeta (Sugeridos, Oscuros, Claros…), en minúsculas y del mismo estilo. */
    fun label(s: String) = text(s, condSemi, 13f, MUTED, 0.06f)

    /** Encabezado de grupo (Estilo, Contenido, Reproductor). */
    fun groupHeader(title: String): View = LinearLayout(ctx).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(20), dp(16), dp(20), dp(10))
        addView(text(title, condSemi, 15f, accent, 0.04f))
        isFocusable = false
        accessibilityHeading()
    }

    private fun View.accessibilityHeading() {
        if (android.os.Build.VERSION.SDK_INT >= 28) isAccessibilityHeading = true
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
        // Título a todo el ancho y el resumen (lo elegido) debajo, en pequeño:
        // así el título nunca se corta, aunque la letra del teléfono sea grande
        val col = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL; tag = "titleCol" }
        col.addView(text(title, condSemi, 21f, HEAD, 0.01f).apply {
            tag = "titleText"
            isSingleLine = true
            fitText(this, 21, 15)
        }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(34)))
        if (hint != null) col.addView(hintText(hint))
        addView(col, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply { marginStart = dp(10) })
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { bottomMargin = dp(14) }
    }

    /** Resumen bajo el título (por ejemplo "Rock" o "Animado: Fogata"). */
    fun hintText(s: String) = text(s, cond, 14f, DIM).apply {
        tag = "hint"; maxLines = 1; ellipsize = TextUtils.TruncateAt.END
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
