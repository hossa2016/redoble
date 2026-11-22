package com.hossa.redoble

import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

/**
 * Escenas del fondo animado. Cada una dibuja un cuadro con:
 *  - tx, ty: inclinación suavizada (-1..1, 0 = como se sostiene normalmente) para el efecto de profundidad.
 *  - gx, gy: hacia dónde "cae" la gravedad en la pantalla (-1..1), para líquido, arena, lluvia y fuego.
 *  - t: segundos desde que empezó; dt: segundos desde el cuadro anterior.
 */
abstract class LiveScene {
    var w = 1f; var h = 1f; var dens = 1f
    open fun resize(width: Int, height: Int, density: Float) { w = width.toFloat(); h = height.toFloat(); dens = density }
    abstract fun draw(c: Canvas, tx: Float, ty: Float, gx: Float, gy: Float, t: Float, dt: Float)

    protected val p = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    protected val add = PorterDuffXfermode(PorterDuff.Mode.ADD)
    protected val screen = PorterDuffXfermode(PorterDuff.Mode.SCREEN)
    protected fun c(a: Int, r: Int, g: Int, b: Int) = Color.argb(a.coerceIn(0, 255), r, g, b)

    /** Círculo de luz suave (sin desenfoque costoso: degradado radial). */
    protected fun glow(cv: Canvas, x: Float, y: Float, r: Float, color: Int, alpha: Float, mode: PorterDuffXfermode? = null) {
        if (r <= 0f) return
        val a = (alpha * 255).roundToInt().coerceIn(0, 255)
        val r0 = Color.red(color); val g0 = Color.green(color); val b0 = Color.blue(color)
        p.reset(); p.isAntiAlias = true; p.xfermode = mode
        p.shader = RadialGradient(x, y, r, intArrayOf(c(a, r0, g0, b0), c((a * .45f).toInt(), r0, g0, b0), c(0, r0, g0, b0)),
            floatArrayOf(0f, .45f, 1f), Shader.TileMode.CLAMP)
        cv.drawCircle(x, y, r, p)
        p.shader = null; p.xfermode = null
    }

    /** Grano fino y viñeta: el acabado "fotográfico" de los fondos premium. */
    protected fun finish(cv: Canvas, dark: Boolean = true) {
        p.reset(); p.shader = Grain.shader; p.alpha = if (dark) 255 else 160
        cv.drawRect(0f, 0f, w, h, p); p.shader = null; p.alpha = 255
        p.shader = RadialGradient(w / 2, h * .45f, h * .8f, intArrayOf(0, 0, if (dark) c(140, 0, 0, 0) else c(46, 80, 60, 40)),
            floatArrayOf(0f, .35f, 1f), Shader.TileMode.CLAMP)
        cv.drawRect(0f, 0f, w, h, p); p.shader = null
    }

    /** Partículas con profundidad: las cercanas son más grandes y se mueven más. */
    protected class Mote(var u: Float, var v: Float, val z: Float, val s: Float, val ph: Float)
    protected val motes = List(60) { Mote(Random.nextFloat(), Random.nextFloat(), .2f + Random.nextFloat() * .8f, .5f + Random.nextFloat() * .5f, Random.nextFloat() * 6.3f) }
    protected fun dust(cv: Canvas, tx: Float, ty: Float, t: Float, color: Int, maxR: Float, top: Float = 0f, bottom: Float = 1f) {
        for (m in motes) {
            var yy = (m.v - t * .012f * m.z) % 1f; if (yy < 0) yy += 1f
            val x = m.u * w + tx * 60 * (w / 282f) * m.z + sin(t * .4f + m.ph) * 6 * (w / 282f)
            val y = h * top + yy * h * (bottom - top) + ty * 40 * (w / 282f) * m.z
            val a = (.25f + .5f * m.z) * (.6f + .4f * sin(t * 1.3f + m.ph))
            glow(cv, x, y, maxR * (w / 282f) * m.z * m.s * 2.4f, color, a, add)
        }
    }

    protected fun lin(x0: Float, y0: Float, x1: Float, y1: Float, cols: IntArray, pos: FloatArray? = null) =
        LinearGradient(x0, y0, x1, y1, cols, pos, Shader.TileMode.CLAMP)
}

/** Textura de grano compartida (se crea una vez). */
object Grain {
    val shader: BitmapShader by lazy {
        val n = 160
        val px = IntArray(n * n) { val v = Random.nextInt(256); Color.argb(12, v, v, v) }
        val b = Bitmap.createBitmap(px, n, n, Bitmap.Config.ARGB_8888)
        BitmapShader(b, Shader.TileMode.REPEAT, Shader.TileMode.REPEAT)
    }
}

object LiveScenes {
    class Info(val id: String, val name: String, val desc: String, val make: () -> LiveScene)
    val ALL = listOf(
        Info("metal", "Metal líquido", "Gotas de cromo que escurren hacia donde inclinas") { MetalScene() },
        Info("arena", "Arena de luz", "Granos dorados que caen y se amontonan") { SandScene() },
        Info("prisma", "Luz de prisma", "Manchas de arcoíris que se separan al inclinar") { PrismScene() },
        Info("lluvia", "Lluvia", "Gotas que resbalan por el vidrio y luces de ciudad") { RainScene() },
        Info("fuego", "Fuego", "Llamas y chispas que siempre suben") { FireScene() },
        Info("aurora", "Aurora", "Cortinas de aurora boreal que ondulan") { AuroraScene() },
        Info("rock", "Rock", "Luces de escenario, humo y platillos con brillo") { RockScene() },
        Info("cristal", "Cristal", "Luces de vidrio flotando a distintas profundidades") { CrystalScene() },
        Info("neon", "Neón", "Cuadrícula que avanza, montañas y sol de neón") { NeonScene() },
        Info("lujo", "Lujo", "Oro grabado con brillo líquido y polvo dorado") { LuxScene() },
    )
    fun find(id: String) = ALL.firstOrNull { it.id == id } ?: ALL[0]
}

// ======================================================================
// Metal líquido: metabolas con gravedad, sombreadas como cromo
// ======================================================================
class MetalScene : LiveScene() {
    private val mw = 120; private val mh = 260
    private val buf = IntArray(mw * mh); private val field = FloatArray(mw * mh)
    private val bmp = Bitmap.createBitmap(mw, mh, Bitmap.Config.ARGB_8888)
    private class B(var x: Float, var y: Float, var vx: Float = 0f, var vy: Float = 0f, val r: Float)
    private val bs = List(11) { B(20 + Random.nextFloat() * (mw - 40), 30 + Random.nextFloat() * (mh - 60), r = 7 + Random.nextFloat() * 7) }
    private fun hgt(v: Float) = sqrt(((v - .9f) / 3f).coerceIn(0f, 1f))

    override fun draw(c: Canvas, tx: Float, ty: Float, gx: Float, gy: Float, t: Float, dt: Float) {
        for (b in bs) {
            b.vx += (gx * 9 + sin(t / 1.7f + b.r) * 1.5f) * dt; b.vy += (gy * 9 + cos(t / 2.1f + b.r) * 1.5f) * dt
            for (q in bs) { if (q === b) continue
                val dx = q.x - b.x; val dy = q.y - b.y; val d = hypot(dx, dy).coerceAtLeast(1f); val m = (b.r + q.r) * 1.05f
                if (d < m) { val f = (m - d) * 3.2f; b.vx -= dx / d * f * dt; b.vy -= dy / d * f * dt } }
            b.vx *= .97f; b.vy *= .97f; b.x += b.vx * dt * 14; b.y += b.vy * dt * 14
            val k = b.r * .9f
            if (b.x < k) { b.x = k; b.vx *= -.3f }; if (b.x > mw - k) { b.x = mw - k; b.vx *= -.3f }
            if (b.y < k) { b.y = k; b.vy *= -.3f }; if (b.y > mh - k) { b.y = mh - k; b.vy *= -.3f }
        }
        for (yy in 0 until mh) for (xx in 0 until mw) { var f = 0f
            for (b in bs) { val dx = xx - b.x; val dy = yy - b.y; f += b.r * b.r / (dx * dx + dy * dy + 1) }
            field[yy * mw + xx] = f }
        val lx = -.4f - tx * .6f; val ly = -.7f - ty * .4f; val lz = .8f; val ln = sqrt(lx * lx + ly * ly + lz * lz)
        for (yy in 0 until mh) for (xx in 0 until mw) {
            val k = yy * mw + xx; val f = field[k]; val a = ((f - .9f) / .12f).coerceIn(0f, 1f)
            if (a > 0f) {
                val hx = hgt(field[k + if (xx < mw - 1) 1 else 0]) - hgt(field[k - if (xx > 0) 1 else 0])
                val hy = hgt(field[k + if (yy < mh - 1) mw else 0]) - hgt(field[k - if (yy > 0) mw else 0])
                var nx = -hx * 3; var ny = -hy * 3; var nz = 1f; val nl = sqrt(nx * nx + ny * ny + nz * nz); nx /= nl; ny /= nl; nz /= nl
                val dif = max(0f, (nx * -lx + ny * -ly + nz * lz) / ln); val rz = 2 * dif * nz - lz / ln
                val spec = max(0f, rz).pow(40)
                val env = if (ny < -.05f) 170 + 80 * min(1f, -ny * 2) else if (ny > .05f) 30f else 95f
                val stripe = max(0f, sin(nx * 6 + tx * 3)) * 22
                val v = env * (.55f + .45f * dif) + stripe
                val r = min(255f, v * .9f + spec * 255); val g = min(255f, v * .95f + spec * 255); val bl = min(255f, v * 1.08f + spec * 255)
                buf[k] = Color.rgb((8 + (r - 8) * a).toInt(), (9 + (g - 9) * a).toInt(), (12 + (bl - 12) * a).toInt())
            } else { val g = max(0f, f - .5f) * 70; buf[k] = Color.rgb((8 + g * .35f).toInt(), (9 + g * .4f).toInt(), (13 + g * .55f).toInt()) }
        }
        bmp.setPixels(buf, 0, mw, 0, 0, mw, mh)
        p.reset(); p.isFilterBitmap = true
        c.drawBitmap(bmp, null, RectF(0f, 0f, w, h), p)
        finish(c)
    }
}

// ======================================================================
// Arena de luz: autómata de arena con gravedad en 8 direcciones
// ======================================================================
class SandScene : LiveScene() {
    private val aw = 96; private val ah = 208
    private val g = ByteArray(aw * ah); private val px = IntArray(aw * ah)
    private val bmp = Bitmap.createBitmap(aw, ah, Bitmap.Config.ARGB_8888)
    private val cols = intArrayOf(Color.rgb(7, 5, 10), Color.rgb(246, 213, 138), Color.rgb(227, 178, 90), Color.rgb(255, 240, 196))
    private var acc = 0f
    init {
        for (x in 0 until aw) { val top = ah - (ah * .30f * (1 - abs(x.toFloat() / aw - .5f) * 1.3f)).roundToInt()
            for (y in max(0, top) until ah) if (Random.nextFloat() < .9f) g[y * aw + x] = (1 + Random.nextInt(3)).toByte() }
    }
    private fun step(gx: Float, gy: Float) {
        val sx = if (abs(gx) > .35f) (if (gx > 0) 1 else -1) else 0
        val sy = if (abs(gy) > .35f) (if (gy > 0) 1 else -1) else if (sx != 0) 0 else 1
        val ys = if (sy > 0) (ah - 1 downTo 0) else (0 until ah)
        val xs = if (sx > 0) (aw - 1 downTo 0) else (0 until aw)
        fun mv(i: Int, x: Int, y: Int, dx: Int, dy: Int): Boolean {
            val X = x + dx; val Y = y + dy
            if (X < 0 || Y < 0 || X >= aw || Y >= ah) return false
            val j = Y * aw + X; if (g[j].toInt() != 0) return false
            g[j] = g[i]; g[i] = 0; return true
        }
        for (y in ys) for (x in xs) { val i = y * aw + x; if (g[i].toInt() == 0) continue
            if (mv(i, x, y, sx, sy)) continue
            val r = if (Random.nextBoolean()) 1 else -1
            if (sy != 0) { if (!mv(i, x, y, sx + r, sy)) mv(i, x, y, sx - r, sy) } else { if (!mv(i, x, y, sx, r)) mv(i, x, y, sx, -r) } }
    }
    override fun draw(c: Canvas, tx: Float, ty: Float, gx: Float, gy: Float, t: Float, dt: Float) {
        acc += dt; while (acc > .03f) { step(gx, gy); acc -= .03f }
        for (i in px.indices) px[i] = cols[g[i].toInt()]
        bmp.setPixels(px, 0, aw, 0, 0, aw, ah)
        p.reset(); p.isFilterBitmap = false
        c.drawBitmap(bmp, null, RectF(0f, 0f, w, h), p)
        glow(c, w / 2, h * .9f, w, Color.rgb(255, 190, 90), .25f, add)
        finish(c)
    }
}

// ======================================================================
// Luz de prisma: manchas de arcoíris (dispersión) y líneas de cáustica
// ======================================================================
class PrismScene : LiveScene() {
    private val path = Path()
    override fun draw(c: Canvas, tx: Float, ty: Float, gx: Float, gy: Float, t: Float, dt: Float) {
        p.reset(); p.shader = lin(0f, 0f, 0f, h, intArrayOf(Color.rgb(13, 12, 16), Color.rgb(6, 5, 8))); c.drawRect(0f, 0f, w, h, p); p.shader = null
        val s = (w / 282f) / 2.4f
        for (k in 0 until 7) {
            val bx = w * (.15f + ((k * .37f) % 1f) * .7f); val by = h * (.25f + ((k * .61f) % 1f) * .55f); val ph = k * 1.7f
            for (col in 0 until 6) {
                val hue = col * 55f; val sep = (col - 2.5f) * (4 + abs(tx) * 9) * s * 2.4f
                val x = bx + tx * 40 * s * 2.4f * (1 + k * .1f) + sin(t * .4f + ph) * 14 * s * 2.4f + sep * cos(.6f + tx)
                val y = by + ty * 28 * s * 2.4f + cos(t * .33f + ph) * 12 * s * 2.4f + sep * sin(.6f + tx)
                val rx = (22 + k % 3 * 8) * s * 3.4f; val ry = (7 + k % 2 * 4) * s * 3.4f
                c.save(); c.translate(x, y); c.rotate(((.5f + tx * .4f + sin(t * .2f + k) * .2f) * 180 / PI).toFloat()); c.scale(1f, ry / rx)
                glow(c, 0f, 0f, rx, Color.HSVToColor(floatArrayOf(hue, 1f, 1f)), .30f, add)
                c.restore()
            }
        }
        p.reset(); p.isAntiAlias = true; p.style = Paint.Style.STROKE; p.strokeWidth = 1.2f * dens; p.xfermode = add
        for (col in 0 until 3) { p.color = Color.HSVToColor(46, floatArrayOf(intArrayOf(190, 300, 45)[col].toFloat(), .7f, 1f))
            for (r in 0 until 9) { path.reset(); var k = 0f
                while (k <= w) { val y = h * (.1f + r * .1f) + sin(k * .035f / s / 2.4f + t * .8f + r + col * .4f + tx * 2) * 10 * s * 2.4f +
                        sin(k * .09f / s / 2.4f - t * .5f) * 4 * s * 2.4f + ty * 15 * s * 2.4f
                    if (k == 0f) path.moveTo(k, y) else path.lineTo(k, y); k += 6 * s * 2.4f }
                c.drawPath(path, p) } }
        p.xfermode = null; p.style = Paint.Style.FILL
        finish(c)
    }
}

// ======================================================================
// Lluvia sobre vidrio
// ======================================================================
class RainScene : LiveScene() {
    private class Bk(val u: Float, val v: Float, val r: Float, val hue: Float, val ph: Float)
    private class St(var u: Float, var v: Float, val l: Float, val s: Float)
    private class Dr(var x: Float, var y: Float, val r: Float, var v: Float = 0f, val trail: ArrayDeque<Pair<Float, Float>> = ArrayDeque())
    private val bk = List(26) { Bk(Random.nextFloat(), Random.nextFloat() * .9f, 10 + Random.nextFloat() * 30, floatArrayOf(30f, 45f, 200f, 330f, 15f)[Random.nextInt(5)], Random.nextFloat() * 6) }
    private val st = List(140) { St(Random.nextFloat(), Random.nextFloat(), 8 + Random.nextFloat() * 18, .9f + Random.nextFloat() * .8f) }
    private val dr = List(55) { Dr(Random.nextFloat(), Random.nextFloat(), 1.5f + Random.nextFloat() * 4.5f) }
    private val path = Path()
    override fun draw(c: Canvas, tx: Float, ty: Float, gx: Float, gy: Float, t: Float, dt: Float) {
        val s = (w / 282f)
        p.reset(); p.shader = lin(0f, 0f, 0f, h, intArrayOf(Color.rgb(6, 8, 15), Color.rgb(12, 10, 18))); c.drawRect(0f, 0f, w, h, p); p.shader = null
        for (b in bk) glow(c, b.u * w + tx * 12 * s, b.v * h + ty * 8 * s, b.r * s * 1.4f, Color.HSVToColor(floatArrayOf(b.hue, .8f, 1f)), .26f + .08f * sin(t + b.ph), add)
        val ang = gx * .45f
        p.reset(); p.isAntiAlias = true; p.color = Color.argb(70, 180, 200, 230); p.strokeWidth = 1f * dens
        for (r in st) { r.v += dt * 1.3f * r.s; if (r.v > 1.1f) { r.v = -.1f; r.u = Random.nextFloat() }
            val x = ((r.u * w + r.v * h * sin(ang)) % w + w) % w; val y = r.v * h
            c.drawLine(x, y, x - sin(ang) * r.l * s, y - cos(ang) * r.l * s, p) }
        for (d in dr) {
            if (d.r > 3.6f) { d.v += dt * (.05f + max(0f, gy) * .08f) * d.r; d.v *= .96f; d.y += d.v * dt; d.x += gx * dt * .03f * d.r
                if (Random.nextFloat() < .3f) d.trail.addLast(d.x to d.y); if (d.trail.size > 14) d.trail.removeFirst() }
            if (d.y > 1.05f || d.y < -.05f || d.x < -.05f || d.x > 1.05f) { d.x = Random.nextFloat(); d.y = Random.nextFloat() * .3f; d.v = 0f; d.trail.clear() }
            if (d.trail.size > 1) { path.reset(); d.trail.forEachIndexed { k, (a, b) -> if (k == 0) path.moveTo(a * w, b * h) else path.lineTo(a * w, b * h) }
                path.lineTo(d.x * w, d.y * h)
                p.reset(); p.isAntiAlias = true; p.style = Paint.Style.STROKE; p.strokeCap = Paint.Cap.ROUND
                p.color = Color.argb(36, 200, 215, 240); p.strokeWidth = d.r * .55f * s; c.drawPath(path, p) }
            val x = d.x * w; val y = d.y * h; val r = d.r * s
            p.reset(); p.isAntiAlias = true
            p.shader = RadialGradient(x - r * .3f, y - r * .4f, r, intArrayOf(Color.argb(190, 255, 255, 255), Color.argb(64, 160, 180, 215), Color.argb(140, 20, 25, 40)),
                floatArrayOf(0f, .5f, 1f), Shader.TileMode.CLAMP)
            c.drawCircle(x, y, r, p); p.shader = null
        }
        finish(c)
    }
}

// ======================================================================
// Fuego: algoritmo clásico de llamas a baja resolución + chispas
// ======================================================================
class FireScene : LiveScene() {
    private val fw = 80; private val fh = 150
    private val f = IntArray(fw * fh); private val px = IntArray(fw * fh)
    private val bmp = Bitmap.createBitmap(fw, fh, Bitmap.Config.ARGB_8888)
    private val pal = IntArray(37) { i -> val k = i / 36f
        Color.argb(if (i == 0) 0 else min(255, i * 12), min(255f, k * 2.4f * 255).toInt(), (max(0f, k * 2.6f - .9f) * 200).coerceAtMost(255f).toInt(),
            (max(0f, k * 3 - 2.3f) * 255).coerceAtMost(255f).toInt()) }
    private class Em(var x: Float, var y: Float, val v: Float, val ph: Float)
    private val em = List(45) { Em(Random.nextFloat(), 1 + Random.nextFloat(), .1f + Random.nextFloat() * .2f, Random.nextFloat() * 6) }
    private var acc = 0f
    private fun step(wind: Float) {
        for (x in 0 until fw) f[(fh - 1) * fw + x] = max(0, (36 - (abs(x.toFloat() / fw - .5f) * 2).pow(2) * 26 - Random.nextFloat() * 5).roundToInt())
        for (y in 1 until fh) for (x in 0 until fw) { val src = y * fw + x; val v = f[src]
            if (v == 0) { f[src - fw] = 0; continue }
            val r = (Random.nextFloat() * 3.2f).toInt(); val dx = (r - 1 + wind * Random.nextFloat()).roundToInt()
            val dst = src - fw + dx; if (dst in 0 until fw * fh) f[dst] = max(0, v - (r and 1)) }
    }
    override fun draw(c: Canvas, tx: Float, ty: Float, gx: Float, gy: Float, t: Float, dt: Float) {
        acc += dt; while (acc > .033f) { step(-gx * 1.6f); acc -= .033f }
        for (i in px.indices) px[i] = pal[f[i].coerceIn(0, 36)]
        bmp.setPixels(px, 0, fw, 0, 0, fw, fh)
        c.drawColor(Color.rgb(7, 3, 2))
        glow(c, w / 2, h, h * .8f, Color.rgb(255, 110, 30), .35f, add)
        p.reset(); p.isFilterBitmap = true; p.xfermode = add
        c.drawBitmap(bmp, null, RectF(0f, h * .32f, w, h), p); p.xfermode = null
        val s = (w / 282f)
        for (e in em) { e.y -= e.v * dt; e.x += (-gx * .06f + sin(t * 2 + e.ph) * .02f) * dt * 3
            if (e.y < -.05f) { e.y = 1f; e.x = .2f + Random.nextFloat() * .6f }
            glow(c, e.x * w, e.y * h, 4 * s, Color.rgb(255, 200, 120), (e.y * 1.4f).coerceIn(0f, 1f), add) }
        finish(c)
    }
}

// ======================================================================
// Aurora boreal
// ======================================================================
class AuroraScene : LiveScene() {
    private val path = Path()
    override fun draw(c: Canvas, tx: Float, ty: Float, gx: Float, gy: Float, t: Float, dt: Float) {
        val s = (w / 282f)
        p.reset(); p.shader = lin(0f, 0f, 0f, h, intArrayOf(Color.rgb(2, 6, 17), Color.rgb(6, 26, 26))); c.drawRect(0f, 0f, w, h, p); p.shader = null
        for (i in 0 until 50) { p.color = Color.argb((255 * (.3f + .3f * sin(t * 1.5f + i))).toInt().coerceIn(0, 255), 255, 255, 255)
            c.drawCircle((i * 71 * s) % w, (i * 37 * s) % (h * .6f), .7f * dens, p) }
        val bands = listOf(Triple(Color.rgb(60, 255, 170), .22f, 0f), Triple(Color.rgb(120, 90, 255), .36f, 2f), Triple(Color.rgb(40, 200, 255), .30f, 4f))
        p.reset(); p.xfermode = add
        for ((col, yb, ph) in bands) { var k = 0f; val step = 4 * s
            while (k < w) { val kk = k / s
                val y0 = h * yb + sin(kk * .018f + t * .6f + ph) * 30 * s + sin(kk * .05f + t * .3f) * 10 * s + ty * 30 * s
                val hh = h * .28f + sin(kk * .03f + t + ph) * 40 * s
                val r = Color.red(col); val g = Color.green(col); val b = Color.blue(col)
                p.shader = lin(0f, y0, 0f, y0 + hh, intArrayOf(Color.argb(0, r, g, b), Color.argb(70, r, g, b), Color.argb(0, r, g, b)), floatArrayOf(0f, .25f, 1f))
                c.drawRect(k + tx * 40 * s, y0, k + tx * 40 * s + step * 1.6f, y0 + hh, p); k += step } }
        p.shader = null; p.xfermode = null
        p.color = Color.rgb(2, 7, 10); path.reset(); path.moveTo(0f, h)
        for (k in 0..10) path.lineTo(k / 10f * w + tx * 10 * s, h * .86f - abs(sin(k * 1.3f)) * h * .06f)
        path.lineTo(w, h); path.close(); c.drawPath(path, p)
        finish(c)
    }
}

// ======================================================================
// Rock premium: haces de escenario, humo, partículas, batería en capas
// ======================================================================
class RockScene : LiveScene() {
    private val path = Path()
    override fun draw(c: Canvas, tx: Float, ty: Float, gx: Float, gy: Float, t: Float, dt: Float) {
        val s = (w / 282f)
        c.drawColor(Color.rgb(3, 2, 2))
        val beams = listOf(Triple(.28f, -.12f, Color.rgb(255, 170, 120)), Triple(.72f, .12f, Color.rgb(255, 235, 210)), Triple(.5f, 0f, Color.rgb(200, 60, 50)))
        p.reset(); p.isAntiAlias = true; p.xfermode = add
        beams.forEachIndexed { i, (u, sw, col) ->
            val top = w * u + tx * 12 * s; val ang = sw + sin(t * .35f + i * 2) * .06f + tx * .05f; val len = h * 1.05f
            val ex = top + sin(ang) * len
            for (layer in 0 until 3) { // tres capas = borde suave sin desenfoque
                val spread = w * (.2f + layer * .08f); val a = 34 - layer * 9
                p.shader = lin(top, 0f, ex, len, intArrayOf(Color.argb(a, Color.red(col), Color.green(col), Color.blue(col)), Color.argb(0, Color.red(col), Color.green(col), Color.blue(col))))
                path.reset(); path.moveTo(top - 6 * s, 0f); path.lineTo(top + 6 * s, 0f); path.lineTo(ex + spread, len); path.lineTo(ex - spread, len); path.close()
                c.drawPath(path, p) } }
        p.shader = null; p.xfermode = null
        dust(c, tx, ty, t, Color.rgb(255, 225, 200), 1.8f, 0f, .85f)
        // bombo y toms (capa trasera)
        c.save(); c.translate(tx * 12 * s, ty * 7 * s)
        p.reset(); p.isAntiAlias = true
        p.shader = RadialGradient(w * .45f, h * .7f, w * .34f, intArrayOf(Color.rgb(42, 37, 34), Color.rgb(13, 11, 10)), null, Shader.TileMode.CLAMP)
        c.drawCircle(w * .5f, h * .79f, w * .31f, p); p.shader = null
        p.style = Paint.Style.STROKE; p.strokeWidth = 2 * dens; p.color = Color.argb(64, 255, 220, 200)
        val r = w * .31f; c.drawArc(RectF(w * .5f - r, h * .79f - r, w * .5f + r, h * .79f + r), ((3.6f + tx * .3f) * 180 / PI).toFloat(), (1.8f * 180 / PI).toFloat(), false, p)
        p.style = Paint.Style.FILL; p.color = Color.rgb(22, 18, 15)
        c.drawRect(w * .27f, h * .6f, w * .44f, h * .67f, p); c.drawRect(w * .56f, h * .6f, w * .73f, h * .67f, p)
        c.restore()
        // platillos y soportes (capa delantera: se mueven más)
        c.save(); c.translate(tx * 32 * s, ty * 19 * s)
        p.reset(); p.isAntiAlias = true; p.color = Color.rgb(58, 51, 46); p.strokeWidth = 2 * dens
        c.drawLine(w * .13f, h * .95f, w * .13f, h * .5f, p); c.drawLine(w * .87f, h * .95f, w * .87f, h * .45f, p)
        fun cym(a: Float, b: Float, rr: Float, tilt: Float) {
            val k = (.5f + tx * .45f).coerceIn(.02f, .98f)
            p.shader = lin(a - rr, b, a + rr, b, intArrayOf(Color.rgb(61, 47, 22), Color.rgb(241, 211, 140), Color.rgb(61, 47, 22)), floatArrayOf(0f, k, 1f))
            c.save(); c.rotate((tilt * 180 / PI).toFloat(), a, b); c.drawOval(RectF(a - rr, b - rr * .15f, a + rr, b + rr * .15f), p); c.restore(); p.shader = null }
        cym(w * .13f, h * .5f, w * .21f, -.12f); cym(w * .87f, h * .45f, w * .24f, .1f)
        c.restore()
        finish(c)
    }
}

// ======================================================================
// Cristal premium: luces de vidrio a distintas profundidades
// ======================================================================
class CrystalScene : LiveScene() {
    override fun draw(c: Canvas, tx: Float, ty: Float, gx: Float, gy: Float, t: Float, dt: Float) {
        val s = (w / 282f)
        p.reset(); p.shader = lin(0f, 0f, 0f, h, intArrayOf(Color.rgb(4, 7, 13), Color.rgb(1, 2, 3))); c.drawRect(0f, 0f, w, h, p); p.shader = null
        val orbs = listOf(floatArrayOf(.15f, .22f, .85f, .85f, 22f), floatArrayOf(.95f, .48f, .75f, .7f, 40f), floatArrayOf(.3f, .9f, .8f, .75f, 62f), floatArrayOf(.7f, .08f, .5f, .35f, 85f))
        val cols = intArrayOf(Color.rgb(30, 140, 210), Color.rgb(20, 170, 170), Color.rgb(60, 90, 220), Color.rgb(120, 220, 255))
        orbs.forEachIndexed { i, o -> glow(c, w * o[0] + tx * o[4] * s + sin(t * .25f + i) * 12 * s, h * o[1] + ty * o[4] * .7f * s + cos(t * .2f + i) * 10 * s,
            w * o[2], cols[i], o[3], screen) }
        dust(c, tx, ty, t, Color.rgb(170, 230, 255), 3.2f)
        val k = (.5f + tx * .4f).coerceIn(.13f, .87f)
        p.reset(); p.xfermode = screen
        p.shader = lin(0f, 0f, w, h, intArrayOf(0, Color.argb(26, 255, 255, 255), 0), floatArrayOf(k - .12f, k, k + .12f))
        c.drawRect(0f, 0f, w, h, p); p.shader = null; p.xfermode = null
        finish(c)
    }
}

// ======================================================================
// Neón premium: cielo, montañas, sol y cuadrícula que avanza
// ======================================================================
class NeonScene : LiveScene() {
    private val path = Path()
    override fun draw(c: Canvas, tx: Float, ty: Float, gx: Float, gy: Float, t: Float, dt: Float) {
        val s = (w / 282f)
        val hz = h * .6f + ty * 22 * s; val vx = w / 2 - tx * 70 * s
        p.reset(); p.shader = lin(0f, 0f, 0f, hz, intArrayOf(Color.rgb(5, 3, 12), Color.rgb(26, 6, 40))); c.drawRect(0f, 0f, w, hz, p); p.shader = null
        p.color = Color.rgb(4, 3, 10); c.drawRect(0f, hz, w, h, p)
        for (i in 0 until 40) { p.color = Color.argb((255 * (.2f + .3f * sin(t * 2 + i))).toInt().coerceIn(0, 255), 220, 230, 255)
            c.drawCircle((i * 97 * s) % w + tx * 8 * s, (i * 53 * s) % max(1f, hz * .8f), .6f * dens, p) }
        listOf(Triple(.5f, 18f, Color.rgb(18, 10, 36)), Triple(.9f, 36f, Color.rgb(11, 6, 24))).forEachIndexed { j, (amp, d, col) ->
            p.color = col; path.reset(); path.moveTo(0f, hz)
            for (i in 0..12) path.lineTo(i / 12f * w + tx * d * s, hz - (sin(i * 1.7f + j * 2) * .5f + .6f) * amp * 60 * s)
            path.lineTo(w, hz); path.close(); c.drawPath(path, p) }
        glow(c, vx, hz, w * .75f, Color.rgb(255, 61, 203), .45f, add)
        glow(c, vx, hz, w * .25f, Color.rgb(255, 140, 230), .4f, add)
        p.reset(); p.isAntiAlias = true; p.style = Paint.Style.STROKE; p.xfermode = add
        fun neonLine(x0: Float, y0: Float, x1: Float, y1: Float, col: Int, a: Float) {
            p.color = Color.argb((60 * a).toInt(), Color.red(col), Color.green(col), Color.blue(col)); p.strokeWidth = 5 * dens; c.drawLine(x0, y0, x1, y1, p)
            p.color = Color.argb((210 * a).toInt(), Color.red(col), Color.green(col), Color.blue(col)); p.strokeWidth = 1.1f * dens; c.drawLine(x0, y0, x1, y1, p) }
        val cyan = Color.rgb(61, 242, 255)
        for (i in -12..12) neonLine(vx, hz, w / 2 + i * w * .2f + tx * 90 * s, h + 60, cyan, 1f)
        val off = (t * .35f) % 1f
        for (k in 0 until 14) { val f = (k + off) / 14f; val y = hz + f.pow(2.3f) * (h - hz + 60); neonLine(0f, y, w, y, cyan, min(1f, f * 1.6f)) }
        neonLine(0f, hz, w, hz, Color.rgb(255, 61, 203), 1f)
        p.xfermode = null; p.style = Paint.Style.FILL
        finish(c)
    }
}

// ======================================================================
// Lujo premium: abanicos art déco en oro con brillo líquido
// ======================================================================
class LuxScene : LiveScene() {
    override fun draw(c: Canvas, tx: Float, ty: Float, gx: Float, gy: Float, t: Float, dt: Float) {
        val s = (w / 282f)
        p.reset(); p.shader = RadialGradient(w / 2, h * .32f, w * 1.2f, intArrayOf(Color.rgb(36, 30, 21), Color.rgb(8, 7, 6)), null, Shader.TileMode.CLAMP)
        c.drawRect(0f, 0f, w, h, p); p.shader = null
        val k = (.5f + tx * .35f + ty * .2f).coerceIn(.18f, .82f)
        val sheen = lin(0f, 0f, w, h, intArrayOf(Color.argb(46, 120, 95, 50), Color.argb(56, 140, 110, 60), Color.argb(217, 255, 230, 170), Color.argb(56, 140, 110, 60), Color.argb(46, 120, 95, 50)),
            floatArrayOf(0f, k - .16f, k, k + .16f, 1f))
        c.save(); c.translate(tx * 10 * s, ty * 8 * s)
        p.reset(); p.isAntiAlias = true; p.style = Paint.Style.STROKE; p.strokeWidth = .8f * dens; p.shader = sheen
        val R = 46 * s; var row = -1
        while (row < h / R * 1.3f + 2) { var col = -1
            while (col < w / R + 2) { val cx = col * R + if (row % 2 != 0) R / 2 else 0f; val cy = row * R * .62f
                for (a in 1..3) { val rr = R * .18f * a; c.drawArc(RectF(cx - rr, cy - rr, cx + rr, cy + rr), 180f, 180f, false, p) }
                col++ }
            row++ }
        c.restore()
        p.reset(); p.shader = lin(0f, 0f, 0f, h, intArrayOf(Color.argb(38, 8, 7, 6), Color.argb(178, 8, 7, 6), Color.argb(90, 8, 7, 6)), floatArrayOf(0f, .5f, 1f))
        c.drawRect(0f, 0f, w, h, p); p.shader = null
        dust(c, tx, ty, t, Color.rgb(255, 215, 140), 2.4f)
        val m = 12 * dens
        p.reset(); p.isAntiAlias = true; p.style = Paint.Style.STROKE; p.shader = sheen; p.strokeWidth = 2 * dens
        c.drawRect(m, m, w - m, h - m, p); p.strokeWidth = 1 * dens; p.alpha = 128; c.drawRect(m + 6 * dens, m + 6 * dens, w - m - 6 * dens, h - m - 6 * dens, p); p.alpha = 255
        p.style = Paint.Style.FILL; val d = 6 * dens; val path = Path()
        for ((a, b) in listOf(m to m, w - m to m, m to h - m, w - m to h - m)) { path.reset(); path.moveTo(a, b - d); path.lineTo(a + d, b); path.lineTo(a, b + d); path.lineTo(a - d, b); path.close(); c.drawPath(path, p) }
        p.shader = null
        finish(c)
    }
}
