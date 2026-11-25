package com.hossa.redoble

import android.content.Context
import android.os.Handler
import android.os.Looper
import java.util.concurrent.CopyOnWriteArraySet

/**
 * «Escenario Vivo»: estado de la música para el fondo animado y el color del widget.
 * Los cambios se confirman tras unos segundos estables, para no saltar de escena
 * con cada pausa corta o cambio de canción.
 */
object Stage {
    @Volatile var playing = false; private set
    /** Color principal de la carátula que suena (null si no hay o no tiene color). */
    @Volatile var accent: Int? = null; private set

    private val main = Handler(Looper.getMainLooper())
    private val listeners = CopyOnWriteArraySet<() -> Unit>()
    private var pending: Runnable? = null

    fun listen(l: () -> Unit) { listeners.add(l) }
    fun unlisten(l: () -> Unit) { listeners.remove(l) }

    /** Lo llama el lector de música cada vez que cambia algo. */
    fun changed(ctx: Context) {
        val app = ctx.applicationContext
        pending?.let { main.removeCallbacks(it) }
        val r = Runnable {
            val (p, a) = try { Player.nowState(app) } catch (e: Exception) { false to null }
            if (p == playing && a == accent) return@Runnable
            val colorChanged = a != accent || p != playing
            playing = p; accent = a
            listeners.forEach { try { it() } catch (e: Exception) { } }
            // Color de la carátula en todo el widget: se redibuja entero
            if (colorChanged && Prefs.coverAll(app)) RockWidget.updateAll(app)
        }
        pending = r
        main.postDelayed(r, 2500)
    }
}
