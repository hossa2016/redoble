package com.hossa.redoble

import android.content.Context
import android.graphics.Color
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** El editor visual: lo guardado debe verse en el widget armado (y al volver a armarlo). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class EditorTest {
    private val ctx: Context = ApplicationProvider.getApplicationContext()

    private fun build(): View {
        val rv = RockWidget.buildViews(ctx, 1000, preview = true)
        return rv.apply(ctx, FrameLayout(ctx))
    }
    private fun sp(v: View, id: Int) = (v.findViewById<TextView>(id)).textSize / ctx.resources.displayMetrics.scaledDensity

    private fun colOrder(v: View): List<String> {
        val col = v.findViewById<ViewGroup>(R.id.col)
        val names = mapOf(R.id.day_txt to "dia", R.id.date_row to "dia", R.id.clock to "hora", R.id.date to "fecha",
            R.id.status_row to "clima", R.id.batt_row to "bateria")
        return (0 until col.childCount).mapNotNull { i ->
            val c = col.getChildAt(i)
            names.entries.firstOrNull { c.findViewById<View>(it.key) != null }?.value
        }
    }

    @Test fun piecesAndEdits() {
        Prefs.resetEditor(ctx)
        val v0 = build()
        val stack = v0.findViewById<ViewGroup>(R.id.stack)
        assertEquals("bloques en la pila", 3, stack.childCount)
        assertEquals(listOf("dia", "hora", "fecha", "clima", "bateria"), colOrder(v0))
        val base = sp(v0, R.id.hh)

        Prefs.setPartScale(ctx, "hora", 1.5f)
        Prefs.setPartColor(ctx, "hora", Color.RED)
        Prefs.setHeadOrder(ctx, listOf("clima", "hora", "dia", "fecha", "bateria"))
        Prefs.setBlockOrder(ctx, listOf("apps", "head", "player"))
        // Como en el teléfono: se lee de nuevo desde las preferencias
        val v1 = build()
        assertEquals("tamaño de la hora", base * 1.5f, sp(v1, R.id.hh), 0.6f)
        assertEquals("color de la hora", Color.RED, v1.findViewById<TextView>(R.id.hh).currentTextColor)
        assertEquals(listOf("clima", "hora", "dia", "fecha", "bateria"), colOrder(v1))
        val st1 = v1.findViewById<ViewGroup>(R.id.stack)
        assertTrue("apps primero", st1.getChildAt(0).findViewById<View>(R.id.setlist) != null)

        // Re-aplicar sobre la vista vieja (lo que hace la pantalla de inicio con el mismo diseño)
        val rv = RockWidget.buildViews(ctx, 1000, preview = true)
        rv.reapply(ctx, v0)
        assertEquals("reapply tamaño", base * 1.5f, sp(v0, R.id.hh), 0.6f)
        assertEquals("reapply orden", listOf("clima", "hora", "dia", "fecha", "bateria"), colOrder(v0))

        // Guardado de verdad en disco (otra instancia de preferencias lo ve)
        val raw = ctx.getSharedPreferences("redoble", Context.MODE_PRIVATE)
        assertEquals(1.5f, raw.getFloat("es_hora", 1f), 0.001f)
        Prefs.resetEditor(ctx)
    }
}
