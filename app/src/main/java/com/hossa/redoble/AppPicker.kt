package com.hossa.redoble

import android.app.Dialog
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.text.Editable
import android.text.SpannableString
import android.text.Spanned
import android.text.TextWatcher
import android.text.style.ForegroundColorSpan
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.view.animation.DecelerateInterpolator
import android.widget.BaseAdapter
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

/** Hoja inferior para elegir la app de una pista. */
class AppPicker(
    private val activity: AppCompatActivity,
    private val ui: Ui,
    private val slot: Int,
    private val onDone: () -> Unit
) {
    private sealed class Item {
        data class Header(val letter: String) : Item()
        data class App(val entry: AppEntry) : Item()
    }

    private val all = Apps.launchable(activity)
    private val current = Prefs.app(activity, slot)
    private val usedIn: Map<String, Int> = (0 until Prefs.SLOTS)
        .filter { it != slot }
        .associateBy { Prefs.app(activity, it) }
        .filterKeys { it.isNotBlank() }

    private var items: List<Item> = build("")
    private lateinit var dialog: Dialog

    private fun build(query: String): List<Item> {
        val q = Apps.normalize(query.trim())
        val list = if (q.isEmpty()) all else all.filter { Apps.normalize(it.label).contains(q) }
        val out = ArrayList<Item>()
        var last = ""
        for (a in list) {
            val l = Apps.initial(a.label)
            if (l != last) { out.add(Item.Header(l)); last = l }
            out.add(Item.App(a))
        }
        return out
    }

    fun show() {
        val ctx = activity
        dialog = Dialog(ctx, R.style.Theme_Redoble_Sheet)

        val sheet = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                intArrayOf(Ui.PLATE_TOP, Ui.INK, Ui.INK)
            ).apply {
                val r = ui.dp(24).toFloat()
                cornerRadii = floatArrayOf(r, r, r, r, 0f, 0f, 0f, 0f)
                setStroke(ui.dp(1), Ui.veil(0x1F))
            }
        }

        // Agarre
        sheet.addView(View(ctx).apply { background = ui.rect(Ui.headA(0x40), 2f) },
            LinearLayout.LayoutParams(ui.dp(40), ui.dp(4)).apply {
                gravity = Gravity.CENTER_HORIZONTAL; topMargin = ui.dp(10); bottomMargin = ui.dp(16)
            })

        // Título
        val num = String.format("%02d", slot + 1)
        val title = SpannableString("APP $num").apply {
            setSpan(ForegroundColorSpan(ui.accent), 4, 4 + num.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        val head = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(ui.dp(20), 0, ui.dp(8), 0)
            addView(ui.text(title, ui.anton, 26f, Ui.HEAD, 0.03f),
                LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            addView(ui.textButton("Cerrar").apply { setOnClickListener { dialog.dismiss() } })
        }
        sheet.addView(head)
        sheet.addView(ui.bodyText("Elige la app que se abrirá desde este acceso.").apply {
            setPadding(ui.dp(20), ui.dp(6), ui.dp(20), ui.dp(14))
        })

        // Buscador
        val search = EditText(ctx).apply {
            hint = "Buscar app"
            setHintTextColor(Ui.DIM)
            setTextColor(Ui.HEAD)
            typeface = ui.body
            textSize = 16f
            background = ui.field()
            setSingleLine()
            setPadding(ui.dp(16), 0, ui.dp(14), 0)
            setCompoundDrawablesRelativeWithIntrinsicBounds(
                android.R.drawable.ic_menu_search, 0, 0, 0
            )
            compoundDrawablePadding = ui.dp(10)
            compoundDrawablesRelative[0]?.setTint(Ui.DIM)
        }
        sheet.addView(search, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, ui.dp(48)).apply {
            marginStart = ui.dp(20); marginEnd = ui.dp(20); bottomMargin = ui.dp(8)
        })

        // Lista
        val adapter = Adapter()
        val list = ListView(ctx).apply {
            divider = null
            selector = ColorDrawable(Color.TRANSPARENT)
            cacheColorHint = Color.TRANSPARENT
            setPadding(ui.dp(12), 0, ui.dp(12), 0)
            clipToPadding = false
            this.adapter = adapter
            setOnItemClickListener { _, _, pos, _ ->
                val it = items[pos]
                if (it is Item.App) choose(it.entry.pkg)
            }
        }
        sheet.addView(list, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))

        search.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun afterTextChanged(s: Editable?) {
                items = build(s?.toString() ?: "")
                adapter.notifyDataSetChanged()
            }
        })

        // Pie
        sheet.addView(ui.secondary("Dejar espacio libre").apply { setOnClickListener { choose("") } },
            LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, ui.dp(48)).apply {
                setMargins(ui.dp(20), ui.dp(12), ui.dp(20), ui.dp(20))
            })

        dialog.setContentView(sheet)
        // Android 15+: la hoja llega hasta abajo; se deja espacio para la barra de navegación y el teclado
        androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(sheet) { v, insets ->
            val b = insets.getInsets(androidx.core.view.WindowInsetsCompat.Type.navigationBars() or
                androidx.core.view.WindowInsetsCompat.Type.ime())
            v.setPadding(v.paddingLeft, v.paddingTop, v.paddingRight, b.bottom)
            insets
        }
        val screenH = ctx.resources.displayMetrics.heightPixels
        val h = minOf(ui.dp(640), (screenH * 0.8f).toInt())
        dialog.window?.apply {
            setLayout(WindowManager.LayoutParams.MATCH_PARENT, h)
            setGravity(Gravity.BOTTOM)
            setDimAmount(0.72f)
            setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        }
        dialog.setOnShowListener {
            sheet.translationY = h.toFloat()
            sheet.animate().translationY(0f).setDuration(220).setInterpolator(DecelerateInterpolator()).start()
        }
        dialog.show()
    }

    private fun choose(pkg: String) {
        if (pkg.isNotBlank()) usedIn[pkg]?.let { Prefs.setApp(activity, it, "") }
        Prefs.setApp(activity, slot, pkg)
        dialog.dismiss()
        onDone()
    }

    private inner class Adapter : BaseAdapter() {
        override fun getCount() = items.size
        override fun getItem(p: Int) = items[p]
        override fun getItemId(p: Int) = p.toLong()
        override fun getViewTypeCount() = 2
        override fun getItemViewType(p: Int) = if (items[p] is Item.Header) 0 else 1
        override fun isEnabled(p: Int) = items[p] is Item.App

        override fun getView(p: Int, convertView: View?, parent: ViewGroup): View {
            return when (val it = items[p]) {
                is Item.Header -> ui.text(it.letter, ui.anton, 14f, Ui.DIM, 0.06f).apply {
                    setPadding(ui.dp(8), ui.dp(14), ui.dp(8), ui.dp(4))
                }
                is Item.App -> appRow(it.entry)
            }
        }
    }

    private fun appRow(a: AppEntry): View {
        val ctx = activity
        val selected = a.pkg == current
        return LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = ui.rowBg(selected)
            setPadding(ui.dp(10), 0, ui.dp(10), 0)
            minimumHeight = ui.dp(56)
            layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ui.dp(56))
            addView(ui.appHolder(a.pkg, 38, 19, selected))
            addView(ui.ellipsize(ui.text(a.label, ui.cond, 19f, Ui.HEAD)),
                LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply { marginStart = ui.dp(14) })
            usedIn[a.pkg]?.let { other ->
                addView(ui.text("En el acceso %d".format(other + 1), ui.cond, 14f, Ui.DIM).apply {
                    setPadding(ui.dp(8), 0, 0, 0)
                })
            }
            if (selected) {
                val check = FrameLayout(ctx).apply {
                    background = ui.oval(ui.accent)
                    addView(ui.text("✓", ui.condSemi, 14f, ui.onAccent).apply { gravity = Gravity.CENTER },
                        FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
                }
                addView(check, LinearLayout.LayoutParams(ui.dp(22), ui.dp(22)).apply { marginStart = ui.dp(10) })
            }
        }
    }
}
