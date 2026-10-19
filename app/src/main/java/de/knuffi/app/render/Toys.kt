package de.knuffi.app.render

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Shader
import de.knuffi.core.Settings
import de.knuffi.core.ToyTuning
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

/** Slider values of the screen toys (0 = off, 4 = very often). */
class ToyConfig {
    var ladder = 2
    var planes = 2
    var footprints = 2
    var paint = 1

    fun from(s: Settings) {
        ladder = s.toyLadder
        planes = s.toyPlanes
        footprints = s.toyFootprints
        paint = s.toyPaint
    }
}

/** A footprint on the floor. x is normalised, z is the depth (0 front, 1 back). */
class Footprint(val x: Float, val z: Float, val side: Int, val born: Float, val dir: Float, val cat: Boolean, val dirty: Boolean)

/**
 * A paint splash. x is normalised, lift is the height above the floor in pet sizes (0 = on the floor),
 * size in pet sizes. [amount] goes from 1 (fresh) to 0 (wiped away).
 */
class Splash(val x: Float, val lift: Float, val size: Float, val color: Int, val seed: Int, val born: Float) {
    var amount = 1f
    var pop = 0f

    // Screen position of the last frame, for touch handling.
    var sx = -1f
    var sy = -1f
    var sr = 0f
    val onFloor: Boolean get() = lift <= 0.05f
}

/** A paper plane. x is normalised, h is the height above the floor in pet sizes. */
class Plane(var x: Float, var h: Float, var vx: Float, var vh: Float, val born: Float) {
    var landed = false
    var landedAt = 0f
    var alpha = 1f
    var caught = false
    var sx = -1f
    var sy = -1f
    var sr = 0f
    val angle: Float get() = if (landed) 0f else atan2(-vh, abs(vx)).toDegrees()
    private fun Float.toDegrees() = this * 180f / PI.toFloat()
}

/** Shared state of everything the pet leaves behind on the screen. */
class Toys {
    val footprints = ArrayList<Footprint>()
    val splashes = ArrayList<Splash>()
    var plane: Plane? = null

    // Ladder: x normalised, extend 0..1 (folded..standing)
    var ladderX = 0.3f
    var ladderExtend = 0f
    /** Ladder height in pet sizes (taller on the home screen). */
    var ladderHeight = 1.55f

    private var lastStep = 0f

    fun addFootprint(x: Float, z: Float, side: Int, t: Float, dir: Float, cat: Boolean, dirty: Boolean, cfg: ToyConfig) {
        if (cfg.footprints <= 0) return
        if (t - lastStep < 0.12f) return
        lastStep = t
        footprints += Footprint(x, z, side, t, dir, cat, dirty)
        while (footprints.size > 90) footprints.removeAt(0)
    }

    fun update(t: Float, dt: Float, cfg: ToyConfig) {
        val life = ToyTuning.footprintSeconds(cfg.footprints)
        footprints.removeAll { t - it.born > life }
        val it = splashes.iterator()
        while (it.hasNext()) {
            val s = it.next()
            s.pop = min(1f, s.pop + dt * 5f)
            if (s.amount <= 0f) it.remove()
        }
        if (cfg.paint <= 0) splashes.clear()
        plane?.let { p ->
            if (!p.landed) {
                p.vh -= 1.1f * dt
                // A paper plane glides: it can't fall faster than a gentle sink.
                if (p.vh < -0.42f) p.vh = -0.42f + (p.vh + 0.42f) * 0.2f
                p.x += p.vx * dt
                p.h += p.vh * dt + sin((t - p.born) * 5f) * 0.004f
                if (p.x < 0.04f || p.x > 0.96f) {
                    p.vx = -p.vx * 0.8f
                    p.x = p.x.coerceIn(0.04f, 0.96f)
                }
                if (p.h <= 0f) {
                    p.h = 0f
                    p.landed = true
                    p.landedAt = t
                }
            } else if (t - p.landedAt > 10f) {
                p.alpha -= dt
                if (p.alpha <= 0f) plane = null
            }
            if (p.caught) {
                p.alpha -= dt * 4f
                if (p.alpha <= 0f) plane = null
            }
        }
    }

    fun throwPlane(x: Float, dir: Float, t: Float, rnd: Random) {
        plane = Plane(x + dir * 0.06f, 0.75f, dir * (0.3f + rnd.nextFloat() * 0.15f), 1.25f + rnd.nextFloat() * 0.35f, t)
    }

    fun addSplash(x: Float, lift: Float, t: Float, rnd: Random, cfg: ToyConfig): Boolean {
        if (splashes.size >= ToyTuning.maxSplashes(cfg.paint)) return false
        val color = SPLASH_COLORS[rnd.nextInt(SPLASH_COLORS.size)]
        splashes += Splash(x.coerceIn(0.05f, 0.95f), lift, 0.16f + rnd.nextFloat() * 0.1f, color, rnd.nextInt(1000), t)
        return true
    }

    /** Wipes splashes under the finger. Returns how many were wiped away completely. */
    fun wipe(px: Float, py: Float, strength: Float): Int {
        var done = 0
        for (s in splashes) {
            if (s.sr <= 0f || s.amount <= 0f) continue
            if (hypot(px - s.sx, py - s.sy) < s.sr * 1.25f) {
                s.amount -= strength
                if (s.amount <= 0f) done++
            }
        }
        return done
    }

    fun hitPlane(px: Float, py: Float): Boolean {
        val p = plane ?: return false
        if (p.caught || p.sr <= 0f) return false
        if (hypot(px - p.sx, py - p.sy) < p.sr * 1.8f) {
            p.caught = true
            return true
        }
        return false
    }

    fun hasMess(): Boolean = splashes.isNotEmpty()

    companion object {
        val SPLASH_COLORS = intArrayOf(
            0xFFFF5C7A.toInt(), 0xFF4DA8FF.toInt(), 0xFFFFC83D.toInt(), 0xFF5ED17A.toInt(), 0xFFB07CFF.toInt(), 0xFFFF8A3D.toInt(),
        )
    }
}

/** Draws ladder, paper plane, splashes and footprints in the soft 3D style. */
class ToyRenderer : Painter() {

    fun ladder(c: Canvas, x: Float, groundY: Float, height: Float, width: Float, extend: Float) {
        if (extend <= 0.01f) return
        val h = height * extend
        val top = groundY - h
        ellipseShadow(c, x, groundY, width * 0.9f, width * 0.15f, 0.25f)
        val rail = width * 0.11f
        for (s in intArrayOf(-1, 1)) {
            val rx = x + s * width * 0.5f
            rect.set(rx - rail / 2f, top, rx + rail / 2f, groundY)
            shader(LinearGradient(rx - rail, 0f, rx + rail, 0f, intArrayOf(0xFFE8B880.toInt(), 0xFFB07A44.toInt(), 0xFF7A4E26.toInt()), null, Shader.TileMode.CLAMP))
            c.drawRoundRect(rect, rail / 2f, rail / 2f, fill)
            fill.shader = null
        }
        val rungs = (height / (width * 0.42f)).toInt().coerceIn(7, 40)
        for (i in 1..rungs) {
            val y = groundY - height * i / (rungs + 0.6f)
            if (y < top) break
            rect.set(x - width * 0.5f, y - rail * 0.35f, x + width * 0.5f, y + rail * 0.35f)
            shader(LinearGradient(0f, rect.top, 0f, rect.bottom, 0xFFD9A066.toInt(), 0xFF8A5A2E.toInt(), Shader.TileMode.CLAMP))
            c.drawRoundRect(rect, rail * 0.3f, rail * 0.3f, fill)
            fill.shader = null
        }
        line.shader = null
        line.color = Colors.alpha(Color.WHITE, 0.35f)
        line.strokeWidth = rail * 0.25f
        c.drawLine(x - width * 0.5f - rail * 0.15f, top + rail, x - width * 0.5f - rail * 0.15f, groundY - rail, line)
    }

    /** A sheet of paper while it is being folded. */
    fun paper(c: Canvas, x: Float, y: Float, size: Float, rot: Float) {
        c.save()
        c.translate(x, y)
        c.rotate(rot * 0.2f)
        c.scale(1f, 0.55f + 0.45f * cos(rot * PI.toFloat() / 180f).let { abs(it) })
        rect.set(-size, -size * 0.75f, size, size * 0.75f)
        shader(LinearGradient(0f, -size, 0f, size, Color.WHITE, 0xFFE4EAF6.toInt(), Shader.TileMode.CLAMP))
        c.drawRect(rect, fill)
        fill.shader = null
        line.shader = null
        line.color = 0x55465080
        line.strokeWidth = max(1f, size * 0.05f)
        c.drawRect(rect, line)
        c.drawLine(0f, -size * 0.75f, 0f, size * 0.75f, line)
        c.restore()
    }

    fun plane(c: Canvas, x: Float, y: Float, size: Float, angle: Float, dir: Float, alpha: Float) {
        if (alpha <= 0.01f) return
        c.save()
        c.translate(x, y)
        c.scale(if (dir < 0) -1f else 1f, 1f)
        c.rotate(angle)
        val a = alpha.coerceIn(0f, 1f)
        // lower wing (shadow side)
        path.reset()
        path.moveTo(size, 0f)
        path.lineTo(-size * 0.85f, size * 0.32f)
        path.lineTo(-size * 0.55f, size * 0.02f)
        path.close()
        fill.shader = null
        fill.color = Colors.alpha(0xFFC9D2E8.toInt(), a)
        c.drawPath(path, fill)
        // keel
        path.reset()
        path.moveTo(size, 0f)
        path.lineTo(-size * 0.55f, size * 0.02f)
        path.lineTo(-size * 0.62f, size * 0.2f)
        path.close()
        fill.color = Colors.alpha(0xFF9AA6C4.toInt(), a)
        c.drawPath(path, fill)
        // upper wing
        path.reset()
        path.moveTo(size, 0f)
        path.lineTo(-size, -size * 0.42f)
        path.lineTo(-size * 0.55f, size * 0.02f)
        path.close()
        shader(LinearGradient(0f, -size * 0.4f, 0f, size * 0.05f, Colors.alpha(Color.WHITE, a), Colors.alpha(0xFFE8EEFA.toInt(), a), Shader.TileMode.CLAMP))
        c.drawPath(path, fill)
        fill.shader = null
        line.shader = null
        line.color = Colors.alpha(0xFF6A7494.toInt(), 0.6f * a)
        line.strokeWidth = max(1f, size * 0.035f)
        c.drawLine(size, 0f, -size * 0.55f, size * 0.02f, line)
        line.color = Colors.alpha(0xFF6A7494.toInt(), 0.35f * a)
        c.drawPath(path, line)
        c.restore()
    }

    fun splash(c: Canvas, s: Splash, x: Float, y: Float, r0: Float) {
        val k = s.amount.coerceIn(0f, 1f)
        val r = r0 * (0.55f + 0.45f * k) * PropEase.backOut(s.pop)
        if (r <= 0.5f) return
        val rnd = Random(s.seed)
        c.save()
        if (s.onFloor) c.scale(1f, 0.42f, x, y)
        path.reset()
        val n = 9
        for (i in 0..n) {
            val a = i / n.toFloat() * 2f * PI.toFloat()
            val rr = r * (0.78f + 0.32f * rnd.nextFloat())
            val px = x + cos(a) * rr
            val py = y + sin(a) * rr
            if (i == 0) path.moveTo(px, py) else {
                val am = (i - 0.5f) / n * 2f * PI.toFloat()
                path.quadTo(x + cos(am) * rr * 1.18f, y + sin(am) * rr * 1.18f, px, py)
            }
        }
        path.close()
        val col = Colors.alpha(s.color, 0.92f * (0.35f + 0.65f * k))
        shader(radial(x - r * 0.3f, y - r * 0.35f, r * 1.4f, intArrayOf(Colors.lighten(col, 0.35f), col, Colors.darken(col, 0.2f)), floatArrayOf(0f, 0.55f, 1f)))
        c.drawPath(path, fill)
        fill.shader = null
        fill.color = col
        for (i in 0 until 5) {
            val a = rnd.nextFloat() * 2f * PI.toFloat()
            val d = r * (1.2f + rnd.nextFloat() * 0.6f)
            c.drawCircle(x + cos(a) * d, y + sin(a) * d, r * (0.08f + 0.1f * rnd.nextFloat()), fill)
        }
        if (!s.onFloor) {
            // a drip running down
            val dl = r * (0.6f + rnd.nextFloat()) * (0.4f + 0.6f * k)
            val dx = x + (rnd.nextFloat() - 0.5f) * r * 0.8f
            rect.set(dx - r * 0.09f, y, dx + r * 0.09f, y + r * 0.7f + dl)
            c.drawRoundRect(rect, r * 0.09f, r * 0.09f, fill)
            c.drawCircle(dx, y + r * 0.7f + dl, r * 0.13f, fill)
        }
        fill.color = Colors.alpha(Color.WHITE, 0.55f * k)
        c.drawOval(x - r * 0.5f, y - r * 0.55f, x - r * 0.12f, y - r * 0.32f, fill)
        c.restore()
    }

    fun footprint(c: Canvas, f: Footprint, x: Float, y: Float, size: Float, alpha: Float) {
        if (alpha <= 0.01f) return
        val col = if (f.dirty) 0xFF6E4A2E.toInt() else 0xFF8A6A54.toInt()
        c.save()
        c.translate(x, y)
        c.scale(1f, 0.5f)
        c.rotate(f.dir * 90f + f.side * 6f)
        fill.shader = null
        fill.color = Colors.alpha(col, alpha * (if (f.dirty) 0.5f else 0.32f))
        if (f.cat) {
            c.drawOval(-size * 0.5f, -size * 0.35f, size * 0.5f, size * 0.45f, fill)
            for (k in 0 until 4) {
                val a = (-0.62f + k * 0.41f) * PI.toFloat() / 2f - PI.toFloat() / 2f
                c.drawCircle(cos(a) * size * 0.72f, sin(a) * size * 0.72f + size * 0.05f, size * 0.2f, fill)
            }
        } else {
            c.drawOval(-size * 0.42f, -size * 0.7f, size * 0.42f, size * 0.7f, fill)
            for (k in -1..1) c.drawCircle(k * size * 0.28f, -size * 0.95f, size * 0.16f, fill)
        }
        c.restore()
    }
}

/** Small easing helpers for the toys. */
object PropEase {
    fun backOut(p: Float): Float {
        val q = p.coerceIn(0f, 1f) - 1f
        return 1f + 2.70158f * q * q * q + 1.70158f * q * q
    }
}
