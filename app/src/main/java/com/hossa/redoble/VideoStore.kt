package com.hossa.redoble

import android.content.Context
import android.net.Uri
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors

/**
 * Videos de fondo: 7 escenas vienen dentro de la app y las demás se descargan
 * la primera vez que se eligen (≈1-3 MB cada una) y quedan guardadas en el teléfono.
 * Si no hay internet, el fondo usa la Fogata hasta que la descarga termine.
 */
object VideoStore {
    private val BUNDLED = mapOf(
        "vid_fogata" to R.raw.vid_fogata, "vid_chispas" to R.raw.vid_chispas, "vid_brasas" to R.raw.vid_brasas,
        "vid_luces" to R.raw.vid_luces, "vid_copos" to R.raw.vid_copos, "vid_cafe" to R.raw.vid_cafe,
        "vid_escenario" to R.raw.vid_escenario, "vid_ambar" to R.raw.vid_ambar,
    )
    /** Tamaño aproximado (MB) de cada video que se descarga, para avisar antes. */
    private val SIZE_MB = mapOf("vid_agua" to 1.2, "vid_aurora" to 0.7, "vid_bateria" to 2.0, "vid_brasero" to 0.9,
        "vid_ciudad" to 0.6, "vid_concierto" to 3.0, "vid_cristal" to 1.5, "vid_estallido" to 2.3, "vid_hojas" to 0.4,
        "vid_neon" to 2.1, "vid_nieve" to 1.0, "vid_ondas" to 1.5, "vid_oro" to 1.5, "vid_ventana" to 1.0)
    // Dos copias públicas del mismo archivo: si una falla se prueba la otra
    private val MIRRORS = listOf(
        "https://raw.githubusercontent.com/hossa2016/redoble/main/videos/",
        "https://cdn.jsdelivr.net/gh/hossa2016/redoble@main/videos/",
    )
    private val io = Executors.newSingleThreadExecutor()
    private val running = HashSet<String>()
    private val listeners = mutableListOf<() -> Unit>()

    private fun dir(ctx: Context) = File(ctx.filesDir, "videos").apply { mkdirs() }
    private fun file(ctx: Context, name: String) = File(dir(ctx), "$name.mp4")

    fun has(ctx: Context, name: String) = name in BUNDLED || file(ctx, name).let { it.exists() && it.length() > 10_000 }
    fun ready(ctx: Context, s: VideoScenes.Info) = s.id == "propio" || (has(ctx, s.base) && (s.over == null || has(ctx, s.over)))
    fun sizeMb(s: VideoScenes.Info) = (SIZE_MB[s.base] ?: 0.0) + (s.over?.let { SIZE_MB[it] } ?: 0.0)
    fun downloading(id: String) = synchronized(running) { id in running }

    /** Dirección para reproducir el video, o null si todavía no está en el teléfono. */
    fun uri(ctx: Context, name: String): Uri? = BUNDLED[name]?.let { Uri.parse("android.resource://${ctx.packageName}/$it") }
        ?: file(ctx, name).takeIf { it.exists() && it.length() > 10_000 }?.let { Uri.fromFile(it) }

    /** El fondo animado escucha aquí para cambiar de escena cuando termina una descarga. */
    fun listen(l: () -> Unit) = synchronized(listeners) { listeners += l }
    fun unlisten(l: () -> Unit) = synchronized(listeners) { listeners -= l }

    /**
     * Descarga lo que falte de la escena. [progress] recibe 0..100; [done] true si quedó lista.
     * Ambos se llaman desde el hilo de descarga.
     */
    fun fetch(ctx: Context, s: VideoScenes.Info, progress: ((Int) -> Unit)? = null, done: ((Boolean) -> Unit)? = null) {
        if (ready(ctx, s)) { done?.invoke(true); return }
        val app = ctx.applicationContext
        if (!synchronized(running) { running.add(s.id) }) { done?.let { io.execute { it(ready(app, s)) } }; return }
        io.execute {
            val names = listOfNotNull(s.base, s.over).filter { !has(app, it) }
            var ok = true
            names.forEachIndexed { i, n ->
                if (ok) ok = get(app, n) { p -> progress?.invoke((i * 100 + p) / names.size) }
            }
            synchronized(running) { running.remove(s.id) }
            done?.invoke(ok)
            if (ok) synchronized(listeners) { listeners.toList() }.forEach { try { it() } catch (e: Exception) { } }
        }
    }

    private fun get(ctx: Context, name: String, progress: (Int) -> Unit): Boolean {
        val out = file(ctx, name); val tmp = File(out.path + ".part")
        for (base in MIRRORS) {
            try {
                val c = (URL("$base$name.mp4").openConnection() as HttpURLConnection).apply {
                    connectTimeout = 15_000; readTimeout = 30_000; instanceFollowRedirects = true
                }
                if (c.responseCode != 200) { c.disconnect(); continue }
                val total = c.contentLengthLong
                c.inputStream.use { inp -> tmp.outputStream().use { o ->
                    val buf = ByteArray(32 * 1024); var n: Int; var got = 0L; var last = -1
                    while (inp.read(buf).also { n = it } > 0) {
                        o.write(buf, 0, n); got += n
                        if (total > 0) { val p = (got * 100 / total).toInt(); if (p != last) { last = p; progress(p) } }
                    }
                } }
                c.disconnect()
                if (tmp.length() > 10_000 && tmp.renameTo(out)) return true
            } catch (e: Exception) { }
            tmp.delete()
        }
        return false
    }

    /** Escenas que faltan por bajar (para «Descargar todos»). */
    fun missing(ctx: Context) = VideoScenes.ALL.filter { !ready(ctx, it) }
}
