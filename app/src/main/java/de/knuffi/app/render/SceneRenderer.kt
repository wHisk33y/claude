package de.knuffi.app.render

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import kotlin.math.PI
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.random.Random

/** Colours used for the minimal style (taken from the Material colour scheme). */
class FlatColors(
    val wall: Int,
    val floor: Int,
    val accent1: Int,
    val accent2: Int,
    val accent3: Int,
    val outline: Int,
)

class SceneState {
    var look: PetLook? = null
    var room: String = "room_cozy"
    var hour: Float = 12f
    var lightsOff: Boolean = false
    var poops: Int = 0
    var petX: Float = 0.5f
    var flat: FlatColors? = null
}

class SceneRenderer {
    val pet = PetRenderer()
    val petBounds = RectF()

    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val line = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val path = Path()
    private val rect = RectF()
    private var mode = RenderMode.SMOOTH

    private val starField: FloatArray = Random(7).let { r -> FloatArray(120) { r.nextFloat() } }

    fun groundY(h: Float) = h * 0.87f
    fun floorTop(h: Float) = h * 0.72f
    fun petSize(w: Float, h: Float) = min(h * 0.44f, w * 0.52f)

    private fun setMode(m: RenderMode) {
        mode = m
        val aa = m != RenderMode.PIXEL
        fill.isAntiAlias = aa
        line.isAntiAlias = aa
    }

    // ------------------------------------------------------------------ public API

    fun drawBackground(c: Canvas, w: Float, h: Float, s: SceneState, f: PetFrame, t: Float, m: RenderMode) {
        setMode(m)
        when (s.room) {
            "room_forest" -> drawForest(c, w, h, s, t)
            "room_ocean" -> drawOcean(c, w, h, s, t)
            "room_space" -> drawSpace(c, w, h, t)
            "room_candy" -> drawCandy(c, w, h, s, t)
            else -> drawCozy(c, w, h, s, t)
        }
        val look = s.look ?: return
        pet.drawShadow(c, s.petX * w, groundY(h), petSize(w, h), f, look, m)
    }

    fun drawSprites(c: Canvas, w: Float, h: Float, s: SceneState, f: PetFrame, t: Float, m: RenderMode) {
        setMode(m)
        val poopX = floatArrayOf(0.17f, 0.84f, 0.29f)
        for (i in 0 until s.poops.coerceAtMost(3)) {
            drawPoop(c, poopX[i] * w, h * 0.93f, h * 0.055f, t + i)
        }
        val look = s.look ?: return
        pet.draw(c, s.petX * w, groundY(h), petSize(w, h), look, f, m)
        petBounds.set(pet.bounds)
    }

    fun drawOverlay(c: Canvas, w: Float, h: Float, s: SceneState, t: Float, m: RenderMode, particles: ParticleSystem?) {
        setMode(m)
        val dark = darkness(s.hour)
        val overlay = when {
            s.lightsOff -> 0.5f
            s.room == "room_space" -> 0f
            s.room == "room_cozy" -> dark * 0.1f
            else -> dark * 0.3f
        }
        if (overlay > 0.01f) {
            fill.shader = null
            fill.color = Colors.alpha(0xFF10123A.toInt(), overlay)
            c.drawRect(0f, 0f, w, h, fill)
        }
        // Stink lines and flies above poops
        val poopX = floatArrayOf(0.17f, 0.84f, 0.29f)
        for (i in 0 until s.poops.coerceAtMost(3)) {
            val px = poopX[i] * w
            val py = h * 0.93f
            val r = h * 0.055f
            line.shader = null
            line.color = Colors.alpha(0xFF8FA37A.toInt(), 0.7f)
            line.strokeWidth = max(1f, r * 0.12f)
            for (k in -1..1 step 2) {
                path.reset()
                val phase = (t * 0.8f + i * 0.3f + k * 0.25f) % 1f
                val x0 = px + k * r * 0.5f
                val y0 = py - r * 1.6f - phase * r * 0.8f
                path.moveTo(x0, y0)
                path.quadTo(x0 + r * 0.25f, y0 - r * 0.35f, x0, y0 - r * 0.7f)
                path.quadTo(x0 - r * 0.25f, y0 - r * 1.05f, x0, y0 - r * 1.4f)
                c.drawPath(path, line)
            }
            fill.color = 0xFF2B2B2B.toInt()
            val fa = t * 5f + i
            c.drawCircle(px + kotlin.math.cos(fa) * r * 1.3f, py - r * 2.2f + sin(fa * 1.7f) * r * 0.5f, max(1f, r * 0.12f), fill)
        }
        particles?.draw(c, w, h, m)
    }

    // ------------------------------------------------------------------ sky & helpers

    private fun vGradient(c: Canvas, l: Float, t: Float, r: Float, b: Float, top: Int, bottom: Int) {
        fill.shader = null
        if (mode == RenderMode.PIXEL) {
            val bands = 6
            val bh = (b - t) / bands
            for (i in 0 until bands) {
                fill.color = Colors.lerp(top, bottom, i / (bands - 1f))
                c.drawRect(l, t + i * bh, r, t + (i + 1) * bh + 1f, fill)
            }
        } else {
            fill.color = Color.WHITE
            fill.shader = LinearGradient(0f, t, 0f, b, top, bottom, Shader.TileMode.CLAMP)
            c.drawRect(l, t, r, b, fill)
            fill.shader = null
        }
    }

    private fun solid(c: Canvas, l: Float, t: Float, r: Float, b: Float, color: Int) {
        fill.shader = null
        fill.color = color
        c.drawRect(l, t, r, b, fill)
    }

    private fun sky(hour: Float): Pair<Int, Int> {
        val hs = HOURS
        var i = 0
        while (i < hs.size - 2 && hour > hs[i + 1]) i++
        val f = ((hour - hs[i]) / (hs[i + 1] - hs[i])).coerceIn(0f, 1f)
        return Colors.lerp(SKY_TOP[i], SKY_TOP[i + 1], f) to Colors.lerp(SKY_BOTTOM[i], SKY_BOTTOM[i + 1], f)
    }

    fun darkness(hour: Float): Float {
        val hs = HOURS
        var i = 0
        while (i < hs.size - 2 && hour > hs[i + 1]) i++
        val f = ((hour - hs[i]) / (hs[i + 1] - hs[i])).coerceIn(0f, 1f)
        return DARK[i] + (DARK[i + 1] - DARK[i]) * f
    }

    private fun drawSky(c: Canvas, l: Float, t: Float, r: Float, b: Float, hour: Float, time: Float) {
        val (top, bottom) = sky(hour)
        vGradient(c, l, t, r, b, top, bottom)
        val w = r - l
        val h = b - t
        val dark = darkness(hour)
        if (dark > 0.25f) {
            for (i in 0 until 18) {
                val sx = l + starField[i * 2] * w
                val sy = t + starField[i * 2 + 1] * h * 0.75f
                val tw = (sin(time * 2f + i * 1.3f) + 1f) / 2f
                fill.color = Colors.alpha(Color.WHITE, dark * (0.35f + 0.65f * tw))
                val sr = max(1f, min(w, h) * 0.012f * (0.6f + tw * 0.6f))
                if (mode == RenderMode.PIXEL) c.drawRect(sx, sy, sx + sr, sy + sr, fill) else c.drawCircle(sx, sy, sr, fill)
            }
        }
        val rr = min(w, h) * 0.11f
        if (hour in 6f..20f) {
            val p = (hour - 6f) / 14f
            val x = l + w * (0.12f + 0.76f * p)
            val y = b - h * (0.18f + 0.6f * sin(p * PI.toFloat()))
            if (mode == RenderMode.SMOOTH) {
                fill.color = Color.WHITE
                fill.shader = RadialGradient(x, y, rr * 2.6f, Colors.alpha(0xFFFFF3B0.toInt(), 0.8f), Colors.alpha(0xFFFFF3B0.toInt(), 0f), Shader.TileMode.CLAMP)
                c.drawCircle(x, y, rr * 2.6f, fill)
                fill.shader = null
            }
            fill.color = if (hour < 8f || hour > 18f) 0xFFFFB067.toInt() else 0xFFFFE066.toInt()
            c.drawCircle(x, y, rr, fill)
        } else {
            val nh = if (hour >= 20f) hour - 20f else hour + 4f
            val p = nh / 10f
            val x = l + w * (0.15f + 0.7f * p)
            val y = b - h * (0.25f + 0.5f * sin(p * PI.toFloat()))
            if (mode == RenderMode.SMOOTH) {
                fill.color = Color.WHITE
                fill.shader = RadialGradient(x, y, rr * 2.4f, Colors.alpha(0xFFE8ECFF.toInt(), 0.45f), Colors.alpha(0xFFE8ECFF.toInt(), 0f), Shader.TileMode.CLAMP)
                c.drawCircle(x, y, rr * 2.4f, fill)
                fill.shader = null
            }
            fill.color = 0xFFF4F1DA.toInt()
            c.drawCircle(x, y, rr, fill)
            fill.color = Colors.lerp(top, bottom, (y - t) / h)
            c.drawCircle(x + rr * 0.45f, y - rr * 0.25f, rr * 0.85f, fill)
        }
        if (dark < 0.6f) {
            for (k in 0 until 2) {
                val cx = l + (((time * 0.012f + k * 0.55f) % 1.4f) - 0.2f) * w
                val cy = t + h * (0.22f + k * 0.2f)
                drawCloud(c, cx, cy, w * 0.13f, Colors.alpha(Color.WHITE, 0.9f - dark))
            }
        }
    }

    private fun drawCloud(c: Canvas, x: Float, y: Float, s: Float, color: Int) {
        fill.shader = null
        fill.color = color
        c.drawCircle(x, y, s * 0.5f, fill)
        c.drawCircle(x + s * 0.5f, y + s * 0.1f, s * 0.4f, fill)
        c.drawCircle(x - s * 0.5f, y + s * 0.12f, s * 0.36f, fill)
        c.drawRect(x - s * 0.5f, y + s * 0.1f, x + s * 0.5f, y + s * 0.5f, fill)
    }

    private fun roundRect(c: Canvas, l: Float, t: Float, r: Float, b: Float, rad: Float, color: Int) {
        fill.shader = null
        fill.color = color
        if (mode == RenderMode.PIXEL) c.drawRect(l, t, r, b, fill) else {
            rect.set(l, t, r, b)
            c.drawRoundRect(rect, rad, rad, fill)
        }
    }

    // ------------------------------------------------------------------ rooms

    private fun drawCozy(c: Canvas, w: Float, h: Float, s: SceneState, t: Float) {
        val floorTop = floorTop(h)
        val flat = s.flat
        if (mode == RenderMode.FLAT && flat != null) {
            solid(c, 0f, 0f, w, floorTop, flat.wall)
            solid(c, 0f, floorTop, w, h, flat.floor)
        } else {
            vGradient(c, 0f, 0f, w, floorTop, 0xFFFFEBDD.toInt(), 0xFFFFD9C7.toInt())
            // wallpaper dots
            fill.shader = null
            fill.color = Colors.alpha(Color.WHITE, 0.5f)
            val step = w / 9f
            var row = 0
            var y = step * 0.5f
            while (y < floorTop - step * 0.3f) {
                var x = if (row % 2 == 0) step * 0.5f else step
                while (x < w) {
                    val r = max(1f, step * 0.07f)
                    if (mode == RenderMode.PIXEL) c.drawRect(x, y, x + r * 1.5f, y + r * 1.5f, fill) else c.drawCircle(x, y, r, fill)
                    x += step
                }
                y += step * 0.8f
                row++
            }
            // floor planks
            vGradient(c, 0f, floorTop, w, h, 0xFFE3AE82.toInt(), 0xFFC98F63.toInt())
            fill.color = Colors.alpha(0xFF9C6B45.toInt(), 0.45f)
            val plank = (h - floorTop) / 4f
            for (i in 1..3) c.drawRect(0f, floorTop + i * plank, w, floorTop + i * plank + max(1f, h * 0.004f), fill)
            for (i in 0..6) {
                val px = (i * 0.17f + (i % 2) * 0.08f) * w
                val row2 = i % 4
                c.drawRect(px, floorTop + row2 * plank, px + max(1f, w * 0.004f), floorTop + (row2 + 1) * plank, fill)
            }
            fill.color = 0xFFF4C7AB.toInt()
            c.drawRect(0f, floorTop - h * 0.025f, w, floorTop, fill)
        }

        // rug
        val rugColor = if (mode == RenderMode.FLAT && flat != null) flat.accent1 else 0xFFFFA8C8.toInt()
        fill.shader = null
        fill.color = rugColor
        c.drawOval(w * 0.24f, h * 0.83f, w * 0.76f, h * 0.93f, fill)
        fill.color = Colors.lighten(rugColor, 0.35f)
        c.drawOval(w * 0.3f, h * 0.845f, w * 0.7f, h * 0.915f, fill)

        // window
        val wl = w * 0.07f
        val wt = h * 0.1f
        val wr = w * 0.38f
        val wb = h * 0.44f
        val frame = if (mode == RenderMode.FLAT && flat != null) Colors.lighten(flat.accent3, 0.85f) else Color.WHITE
        roundRect(c, wl - w * 0.018f, wt - w * 0.018f, wr + w * 0.018f, wb + w * 0.018f, w * 0.02f, frame)
        c.save()
        c.clipRect(wl, wt, wr, wb)
        drawSky(c, wl, wt, wr, wb, s.hour, t)
        c.restore()
        fill.color = frame
        c.drawRect((wl + wr) / 2f - w * 0.007f, wt, (wl + wr) / 2f + w * 0.007f, wb, fill)
        c.drawRect(wl, (wt + wb) / 2f - w * 0.007f, wr, (wt + wb) / 2f + w * 0.007f, fill)
        // curtains
        if (mode != RenderMode.FLAT) {
            val curtain = 0xFFFF8FB8.toInt()
            for (side in 0..1) {
                val x0 = if (side == 0) wl - w * 0.03f else wr + w * 0.03f
                val dir = if (side == 0) 1f else -1f
                path.reset()
                path.moveTo(x0, wt - h * 0.03f)
                path.lineTo(x0 + dir * w * 0.075f, wt - h * 0.03f)
                path.quadTo(x0 + dir * w * 0.02f, (wt + wb) / 2f, x0 + dir * w * 0.05f, wb + h * 0.04f)
                path.lineTo(x0, wb + h * 0.04f)
                path.close()
                fill.color = curtain
                c.drawPath(path, fill)
            }
            fill.color = 0xFFE07AA0.toInt()
            c.drawRect(wl - w * 0.05f, wt - h * 0.045f, wr + w * 0.05f, wt - h * 0.025f, fill)
        }

        // picture frame
        val pl = w * 0.66f
        val pt = h * 0.13f
        val pr = w * 0.86f
        val pb = h * 0.33f
        roundRect(c, pl, pt, pr, pb, w * 0.01f, if (mode == RenderMode.FLAT && flat != null) flat.accent3 else 0xFFFFC857.toInt())
        roundRect(c, pl + w * 0.015f, pt + w * 0.015f, pr - w * 0.015f, pb - w * 0.015f, w * 0.005f, 0xFFFFF7EC.toInt())
        Shapes.heart(path, (pl + pr) / 2f, (pt + pb) / 2f + h * 0.01f, (pb - pt) * 0.28f * (1f + 0.06f * sin(t * 3f)))
        fill.color = if (mode == RenderMode.FLAT && flat != null) flat.accent3 else 0xFFFF6B9A.toInt()
        c.drawPath(path, fill)

        // plant
        val potX = w * 0.1f
        val potTop = floorTop - h * 0.02f
        path.reset()
        path.moveTo(potX - w * 0.05f, potTop)
        path.lineTo(potX + w * 0.05f, potTop)
        path.lineTo(potX + w * 0.038f, potTop + h * 0.1f)
        path.lineTo(potX - w * 0.038f, potTop + h * 0.1f)
        path.close()
        fill.color = if (mode == RenderMode.FLAT && flat != null) flat.accent2 else 0xFFE58A5E.toInt()
        c.drawPath(path, fill)
        val leaf = if (mode == RenderMode.FLAT && flat != null) Colors.darken(flat.accent2, 0.35f) else 0xFF5DBB7A.toInt()
        for (i in 0 until 5) {
            val a = -90f + (i - 2) * 28f + sin(t * 1.2f + i) * 4f
            c.save()
            c.rotate(a, potX, potTop)
            fill.color = if (i % 2 == 0) leaf else Colors.lighten(leaf, 0.2f)
            c.drawOval(potX, potTop - h * 0.025f, potX + h * 0.16f, potTop + h * 0.025f, fill)
            c.restore()
        }

        // lamp
        val lx = w * 0.9f
        fill.color = 0xFF8A6A5A.toInt()
        c.drawRect(lx - w * 0.006f, h * 0.36f, lx + w * 0.006f, floorTop + h * 0.08f, fill)
        c.drawOval(lx - w * 0.04f, floorTop + h * 0.07f, lx + w * 0.04f, floorTop + h * 0.095f, fill)
        if (!s.lightsOff && mode == RenderMode.SMOOTH) {
            fill.color = Color.WHITE
            fill.shader = RadialGradient(lx, h * 0.36f, h * 0.25f, Colors.alpha(0xFFFFE8A3.toInt(), 0.5f), Colors.alpha(0xFFFFE8A3.toInt(), 0f), Shader.TileMode.CLAMP)
            c.drawCircle(lx, h * 0.36f, h * 0.25f, fill)
            fill.shader = null
        }
        path.reset()
        path.moveTo(lx - w * 0.035f, h * 0.28f)
        path.lineTo(lx + w * 0.035f, h * 0.28f)
        path.lineTo(lx + w * 0.06f, h * 0.37f)
        path.lineTo(lx - w * 0.06f, h * 0.37f)
        path.close()
        fill.color = if (s.lightsOff) 0xFFB9A89A.toInt() else if (mode == RenderMode.FLAT && flat != null) flat.accent1 else 0xFFFFE08A.toInt()
        c.drawPath(path, fill)
    }

    private fun drawForest(c: Canvas, w: Float, h: Float, s: SceneState, t: Float) {
        val floorTop = floorTop(h)
        drawSky(c, 0f, 0f, w, floorTop, s.hour, t)
        val dark = darkness(s.hour)
        val hill = Colors.lerp(0xFF7CC68D.toInt(), 0xFF24424A.toInt(), dark * 0.7f)
        path.reset()
        path.moveTo(0f, floorTop)
        var x = 0f
        while (x <= w) {
            path.lineTo(x, floorTop - h * 0.14f - sin(x / w * 7f) * h * 0.05f)
            x += w / 24f
        }
        path.lineTo(w, floorTop)
        path.close()
        fill.shader = null
        fill.color = hill
        c.drawPath(path, fill)

        val trees = floatArrayOf(0.07f, 0.24f, 0.78f, 0.94f)
        for ((i, tx) in trees.withIndex()) {
            val px = tx * w
            val base = floorTop + h * 0.02f
            val th = h * (0.32f + (i % 2) * 0.08f)
            fill.color = Colors.lerp(0xFF8B5E3C.toInt(), 0xFF3A2A2A.toInt(), dark * 0.6f)
            c.drawRect(px - w * 0.018f, base - th * 0.55f, px + w * 0.018f, base, fill)
            val sway = sin(t * 0.9f + i) * w * 0.006f
            val canopy = Colors.lerp(if (i % 2 == 0) 0xFF4DB36A.toInt() else 0xFF3E9C5A.toInt(), 0xFF1D3A36.toInt(), dark * 0.6f)
            fill.color = canopy
            c.drawCircle(px + sway, base - th * 0.8f, th * 0.26f, fill)
            c.drawCircle(px - th * 0.18f + sway, base - th * 0.6f, th * 0.2f, fill)
            c.drawCircle(px + th * 0.18f + sway, base - th * 0.62f, th * 0.21f, fill)
            fill.color = Colors.lighten(canopy, 0.15f)
            c.drawCircle(px - th * 0.07f + sway, base - th * 0.88f, th * 0.1f, fill)
        }

        vGradient(c, 0f, floorTop, w, h, Colors.lerp(0xFF8EDB7E.toInt(), 0xFF2F5A45.toInt(), dark * 0.6f), Colors.lerp(0xFF5FB463.toInt(), 0xFF1E3D30.toInt(), dark * 0.6f))
        if (mode != RenderMode.FLAT) {
            line.shader = null
            line.color = Colors.alpha(0xFF3F8F4A.toInt(), 0.7f)
            line.strokeWidth = max(1f, w * 0.004f)
            for (i in 0 until 26) {
                val gx = (i / 26f + starField[i] * 0.03f) * w
                val gy = floorTop + starField[i + 30] * (h - floorTop)
                c.drawLine(gx, gy, gx + sin(t + i) * w * 0.004f, gy - h * 0.025f, line)
            }
        }
        // mushrooms
        for ((i, mx) in floatArrayOf(0.14f, 0.86f).withIndex()) {
            val px = mx * w
            val py = floorTop + h * (0.12f + i * 0.03f)
            val r = h * 0.045f
            fill.color = 0xFFFFF1E0.toInt()
            c.drawRect(px - r * 0.3f, py - r * 0.6f, px + r * 0.3f, py + r * 0.4f, fill)
            rect.set(px - r, py - r * 1.3f, px + r, py - r * 0.1f)
            path.reset()
            path.arcTo(rect, 180f, 180f)
            path.close()
            fill.color = 0xFFE8505B.toInt()
            c.drawPath(path, fill)
            fill.color = Color.WHITE
            c.drawCircle(px - r * 0.4f, py - r * 0.85f, max(1f, r * 0.16f), fill)
            c.drawCircle(px + r * 0.35f, py - r * 0.95f, max(1f, r * 0.12f), fill)
        }
        // fireflies
        if (dark > 0.3f) {
            for (i in 0 until 8) {
                val fx = (0.1f + 0.8f * starField[50 + i] + sin(t * 0.5f + i) * 0.05f) * w
                val fy = (0.35f + 0.4f * starField[60 + i] + sin(t * 0.8f + i * 2f) * 0.04f) * h
                val glow = (sin(t * 3f + i) + 1f) / 2f
                if (mode == RenderMode.SMOOTH) {
                    fill.color = Color.WHITE
                    fill.shader = RadialGradient(fx, fy, h * 0.03f, Colors.alpha(0xFFFFF59D.toInt(), 0.7f * glow * dark), Colors.alpha(0xFFFFF59D.toInt(), 0f), Shader.TileMode.CLAMP)
                    c.drawCircle(fx, fy, h * 0.03f, fill)
                    fill.shader = null
                }
                fill.color = Colors.alpha(0xFFFFF59D.toInt(), (0.4f + 0.6f * glow) * dark)
                c.drawCircle(fx, fy, max(1f, h * 0.006f), fill)
            }
        }
    }

    private fun drawOcean(c: Canvas, w: Float, h: Float, s: SceneState, t: Float) {
        val floorTop = floorTop(h)
        val dark = darkness(s.hour) * 0.6f
        vGradient(c, 0f, 0f, w, h, Colors.lerp(0xFF7FDBFF.toInt(), 0xFF123A6B.toInt(), dark), Colors.lerp(0xFF1C74B5.toInt(), 0xFF071A3A.toInt(), dark))
        if (mode != RenderMode.PIXEL) {
            fill.shader = null
            for (i in 0 until 3) {
                val x0 = w * (0.2f + i * 0.3f) + sin(t * 0.4f + i) * w * 0.05f
                path.reset()
                path.moveTo(x0 - w * 0.04f, 0f)
                path.lineTo(x0 + w * 0.04f, 0f)
                path.lineTo(x0 + w * 0.14f, floorTop)
                path.lineTo(x0 - w * 0.02f, floorTop)
                path.close()
                fill.color = Colors.alpha(Color.WHITE, 0.08f)
                c.drawPath(path, fill)
            }
        }
        // sand
        vGradient(c, 0f, floorTop, w, h, 0xFFF6DDA8.toInt(), 0xFFE2BE7E.toInt())
        path.reset()
        path.moveTo(0f, floorTop)
        var x = 0f
        while (x <= w) {
            path.lineTo(x, floorTop - sin(x / w * 9f) * h * 0.012f)
            x += w / 20f
        }
        path.lineTo(w, floorTop + h * 0.02f)
        path.lineTo(0f, floorTop + h * 0.02f)
        path.close()
        fill.color = 0xFFF6DDA8.toInt()
        c.drawPath(path, fill)
        // seaweed
        val weeds = floatArrayOf(0.06f, 0.13f, 0.82f, 0.9f, 0.96f)
        for ((i, wx) in weeds.withIndex()) {
            val bx = wx * w
            val hh = h * (0.28f + (i % 3) * 0.07f)
            path.reset()
            path.moveTo(bx, floorTop + h * 0.02f)
            val segs = 5
            for (k in 1..segs) {
                val yy = floorTop + h * 0.02f - hh * k / segs
                val xx = bx + sin(t * 1.3f + i + k * 0.8f) * w * 0.018f * k / segs * 2f
                path.lineTo(xx, yy)
            }
            line.shader = null
            line.color = if (i % 2 == 0) 0xFF3FAF6E.toInt() else 0xFF2E8F5A.toInt()
            line.strokeWidth = max(1f, w * 0.014f)
            c.drawPath(path, line)
        }
        // starfish & shell
        Shapes.star(path, w * 0.72f, floorTop + h * 0.16f, h * 0.035f, h * 0.016f, 5, 12f)
        fill.color = 0xFFFF8A65.toInt()
        c.drawPath(path, fill)
        fill.color = 0xFFFFD1DC.toInt()
        c.drawOval(w * 0.22f, floorTop + h * 0.15f, w * 0.27f, floorTop + h * 0.19f, fill)
        // fish
        for (k in 0 until 2) {
            val speed = 0.04f + k * 0.02f
            val p = ((t * speed + k * 0.47f) % 1.3f) - 0.15f
            val dir = if (k == 0) 1f else -1f
            val fx = if (dir > 0) p * w else (1f - p) * w
            val fy = h * (0.22f + k * 0.18f) + sin(t * 2f + k) * h * 0.015f
            val fs = h * 0.035f
            fill.color = if (k == 0) 0xFFFFA24D.toInt() else 0xFFFFE066.toInt()
            c.drawOval(fx - fs, fy - fs * 0.6f, fx + fs, fy + fs * 0.6f, fill)
            path.reset()
            path.moveTo(fx - dir * fs * 0.8f, fy)
            path.lineTo(fx - dir * fs * 1.6f, fy - fs * 0.6f)
            path.lineTo(fx - dir * fs * 1.6f, fy + fs * 0.6f)
            path.close()
            c.drawPath(path, fill)
            fill.color = 0xFF1F1B2E.toInt()
            c.drawCircle(fx + dir * fs * 0.5f, fy - fs * 0.1f, max(1f, fs * 0.15f), fill)
        }
        // bubbles
        line.color = Colors.alpha(Color.WHITE, 0.6f)
        line.strokeWidth = max(1f, h * 0.003f)
        for (i in 0 until 9) {
            val base = weeds[i % weeds.size] * w
            val p = (t * 0.06f + i * 0.137f) % 1f
            val bx = base + sin(t * 2f + i) * w * 0.01f
            val by = floorTop - p * floorTop
            val br = max(1.5f, h * (0.008f + (i % 3) * 0.004f))
            c.drawCircle(bx, by, br, line)
        }
    }

    private fun drawSpace(c: Canvas, w: Float, h: Float, t: Float) {
        val floorTop = floorTop(h)
        vGradient(c, 0f, 0f, w, h, 0xFF0B0D2E.toInt(), 0xFF2E1D5E.toInt())
        for (i in 0 until 40) {
            val sx = starField[i * 2] * w
            val sy = starField[i * 2 + 1] * floorTop
            val tw = (sin(t * (1.5f + (i % 5) * 0.3f) + i) + 1f) / 2f
            fill.shader = null
            fill.color = Colors.alpha(Color.WHITE, 0.3f + 0.7f * tw)
            val r = max(1f, h * 0.0035f * (1f + tw))
            if (mode == RenderMode.PIXEL) c.drawRect(sx, sy, sx + r, sy + r, fill) else c.drawCircle(sx, sy, r, fill)
        }
        // planet
        val px = w * 0.78f
        val py = h * 0.22f
        val pr = h * 0.09f
        if (mode == RenderMode.SMOOTH) {
            fill.color = Color.WHITE
            fill.shader = LinearGradient(px - pr, py - pr, px + pr, py + pr, 0xFFFFB38A.toInt(), 0xFFE0607E.toInt(), Shader.TileMode.CLAMP)
            c.drawCircle(px, py, pr, fill)
            fill.shader = null
        } else {
            fill.color = 0xFFFF9E7A.toInt()
            c.drawCircle(px, py, pr, fill)
        }
        c.save()
        c.rotate(-18f, px, py)
        line.shader = null
        line.color = 0xFFFFD29A.toInt()
        line.strokeWidth = max(1f, h * 0.012f)
        rect.set(px - pr * 1.7f, py - pr * 0.35f, px + pr * 1.7f, py + pr * 0.35f)
        c.drawArc(rect, 0f, 180f, false, line)
        c.restore()
        fill.color = 0xFFD8D4F0.toInt()
        c.drawCircle(w * 0.18f, h * 0.16f, h * 0.035f, fill)
        // shooting star
        val cycle = t % 7f
        if (cycle < 0.9f) {
            val p = cycle / 0.9f
            val sx = w * (0.15f + 0.6f * p)
            val sy = h * (0.08f + 0.25f * p)
            line.color = Colors.alpha(Color.WHITE, 1f - p)
            line.strokeWidth = max(1f, h * 0.005f)
            c.drawLine(sx, sy, sx - w * 0.08f, sy - h * 0.035f, line)
        }
        // moon floor
        vGradient(c, 0f, floorTop, w, h, 0xFF9C99BC.toInt(), 0xFF6D6A8E.toInt())
        fill.color = 0xFF7F7CA1.toInt()
        c.drawOval(w * 0.08f, floorTop + h * 0.06f, w * 0.2f, floorTop + h * 0.1f, fill)
        c.drawOval(w * 0.8f, floorTop + h * 0.12f, w * 0.94f, floorTop + h * 0.17f, fill)
        c.drawOval(w * 0.6f, floorTop + h * 0.03f, w * 0.66f, floorTop + h * 0.05f, fill)
    }

    private fun drawCandy(c: Canvas, w: Float, h: Float, s: SceneState, t: Float) {
        val floorTop = floorTop(h)
        val dark = darkness(s.hour) * 0.5f
        vGradient(c, 0f, 0f, w, floorTop, Colors.lerp(0xFFFFC4E1.toInt(), 0xFF5B3D7A.toInt(), dark), Colors.lerp(0xFFFFEEF6.toInt(), 0xFF8F5E9E.toInt(), dark))
        drawCloud(c, w * 0.25f + sin(t * 0.3f) * w * 0.02f, h * 0.16f, w * 0.18f, 0xFFFFD6EC.toInt())
        drawCloud(c, w * 0.72f + sin(t * 0.25f + 1f) * w * 0.02f, h * 0.1f, w * 0.15f, 0xFFD6ECFF.toInt())
        // lollipops
        val pops = floatArrayOf(0.1f, 0.88f)
        val popColors = intArrayOf(0xFFFF6FA5.toInt(), 0xFF7FC8FF.toInt())
        for ((i, lx) in pops.withIndex()) {
            val px = lx * w
            val top = h * (0.3f + i * 0.06f)
            fill.shader = null
            fill.color = Color.WHITE
            c.drawRect(px - w * 0.008f, top, px + w * 0.008f, floorTop + h * 0.03f, fill)
            val r = h * 0.075f
            fill.color = popColors[i]
            c.drawCircle(px, top, r, fill)
            line.shader = null
            line.color = Colors.alpha(Color.WHITE, 0.9f)
            line.strokeWidth = max(1f, r * 0.18f)
            c.save()
            c.rotate(t * 40f * (if (i == 0) 1 else -1), px, top)
            path.reset()
            var a = 0f
            path.moveTo(px, top)
            while (a < 3.2f * PI.toFloat()) {
                val rr = r * a / (3.2f * PI.toFloat()) * 0.9f
                path.lineTo(px + kotlin.math.cos(a) * rr, top + sin(a) * rr)
                a += 0.3f
            }
            c.drawPath(path, line)
            c.restore()
        }
        // candy cane
        val cx = w * 0.72f
        path.reset()
        path.moveTo(cx, floorTop + h * 0.03f)
        path.lineTo(cx, h * 0.46f)
        path.quadTo(cx, h * 0.38f, cx + w * 0.05f, h * 0.38f)
        path.quadTo(cx + w * 0.1f, h * 0.38f, cx + w * 0.1f, h * 0.45f)
        line.shader = null
        line.pathEffect = null
        line.color = Color.WHITE
        line.strokeWidth = max(2f, w * 0.022f)
        c.drawPath(path, line)
        line.color = 0xFFE8505B.toInt()
        line.pathEffect = DashPathEffect(floatArrayOf(w * 0.02f, w * 0.02f), 0f)
        c.drawPath(path, line)
        line.pathEffect = null
        // checkerboard floor
        val cols = 10
        val rows = 3
        val tw = w / cols
        val th = (h - floorTop) / rows
        for (r in 0 until rows) for (k in 0 until cols) {
            fill.color = if ((r + k) % 2 == 0) Colors.lerp(0xFFFF9EC8.toInt(), 0xFF6A4A7A.toInt(), dark) else Colors.lerp(0xFFFFF4FA.toInt(), 0xFF9C84A8.toInt(), dark)
            c.drawRect(k * tw, floorTop + r * th, (k + 1) * tw + 1f, floorTop + (r + 1) * th + 1f, fill)
        }
        val sprinkle = intArrayOf(0xFFFFD166.toInt(), 0xFF6EC1FF.toInt(), 0xFF7DD9B0.toInt(), 0xFFB39DFF.toInt())
        for (i in 0 until 14) {
            val sx = starField[80 + i] * w
            val sy = floorTop + starField[100 + (i % 20)] * (h - floorTop)
            fill.color = sprinkle[i % sprinkle.size]
            c.save()
            c.rotate(i * 47f, sx, sy)
            c.drawRect(sx - h * 0.008f, sy - h * 0.003f, sx + h * 0.008f, sy + h * 0.003f, fill)
            c.restore()
        }
    }

    private fun drawPoop(c: Canvas, x: Float, bottom: Float, r: Float, t: Float) {
        val base = 0xFF8D5A3B.toInt()
        val light = 0xFFB07A55.toInt()
        val wob = sin(t * 2f) * r * 0.04f
        fill.shader = null
        fill.color = base
        c.drawOval(x - r * 1.1f, bottom - r * 0.7f, x + r * 1.1f, bottom, fill)
        c.drawOval(x - r * 0.8f + wob, bottom - r * 1.25f, x + r * 0.8f + wob, bottom - r * 0.45f, fill)
        c.drawOval(x - r * 0.45f + wob * 2f, bottom - r * 1.75f, x + r * 0.45f + wob * 2f, bottom - r * 1.05f, fill)
        path.reset()
        path.moveTo(x - r * 0.1f + wob * 2f, bottom - r * 1.65f)
        path.quadTo(x + r * 0.1f, bottom - r * 2.2f, x + r * 0.35f + wob * 2f, bottom - r * 2.05f)
        path.quadTo(x + r * 0.25f, bottom - r * 1.75f, x + r * 0.2f, bottom - r * 1.6f)
        path.close()
        c.drawPath(path, fill)
        if (mode != RenderMode.PIXEL) {
            fill.color = Colors.alpha(light, 0.8f)
            c.drawOval(x - r * 0.6f, bottom - r * 0.6f, x - r * 0.1f, bottom - r * 0.35f, fill)
        }
        fill.color = Color.WHITE
        c.drawCircle(x - r * 0.28f, bottom - r * 0.85f, max(1f, r * 0.17f), fill)
        c.drawCircle(x + r * 0.28f, bottom - r * 0.85f, max(1f, r * 0.17f), fill)
        fill.color = 0xFF2E2440.toInt()
        c.drawCircle(x - r * 0.25f, bottom - r * 0.83f, max(1f, r * 0.08f), fill)
        c.drawCircle(x + r * 0.31f, bottom - r * 0.83f, max(1f, r * 0.08f), fill)
    }

    companion object {
        private val HOURS = floatArrayOf(0f, 5f, 6.5f, 8f, 17f, 19f, 20.5f, 22f, 24f)
        private val SKY_TOP = intArrayOf(
            0xFF0B1030.toInt(), 0xFF1B1F4F.toInt(), 0xFF6A5ACD.toInt(), 0xFF6EC1FF.toInt(), 0xFF6EC1FF.toInt(),
            0xFF7B6CC4.toInt(), 0xFF3A2F7A.toInt(), 0xFF0B1030.toInt(), 0xFF0B1030.toInt(),
        )
        private val SKY_BOTTOM = intArrayOf(
            0xFF2A2F6B.toInt(), 0xFF4A3F7F.toInt(), 0xFFFFB199.toInt(), 0xFFD2F0FF.toInt(), 0xFFD2F0FF.toInt(),
            0xFFFFA07A.toInt(), 0xFFE0708A.toInt(), 0xFF2A2F6B.toInt(), 0xFF2A2F6B.toInt(),
        )
        private val DARK = floatArrayOf(1f, 1f, 0.45f, 0f, 0f, 0.25f, 0.7f, 1f, 1f)
    }
}

/**
 * Low resolution drawing surface for the pixel art style. Everything is drawn into small
 * bitmaps, sprites get a crisp 1px outline and the result is scaled up without filtering.
 */
class PixelLayer(private val targetWidth: Int = 128) {
    var scale = 1f
        private set
    private var bg: Bitmap? = null
    private var sprite: Bitmap? = null
    val bgCanvas = Canvas()
    val spriteCanvas = Canvas()
    private var px = IntArray(0)
    private var out = IntArray(0)
    private val presentPaint = Paint().apply {
        isFilterBitmap = false
        isAntiAlias = false
        isDither = false
    }
    private val dst = RectF()

    val width: Int get() = bg?.width ?: 1
    val height: Int get() = bg?.height ?: 1

    fun begin(w: Float, h: Float) {
        val ps = max(1, (w / targetWidth).roundToInt())
        val lw = max(1, ceil(w / ps).toInt())
        val lh = max(1, ceil(h / ps).toInt())
        val current = bg
        if (current == null || current.width != lw || current.height != lh) {
            current?.recycle()
            sprite?.recycle()
            val b = Bitmap.createBitmap(lw, lh, Bitmap.Config.ARGB_8888)
            val s = Bitmap.createBitmap(lw, lh, Bitmap.Config.ARGB_8888)
            bg = b
            sprite = s
            bgCanvas.setBitmap(b)
            spriteCanvas.setBitmap(s)
            px = IntArray(lw * lh)
            out = IntArray(lw * lh)
        }
        scale = ps.toFloat()
        bg?.eraseColor(Color.TRANSPARENT)
        sprite?.eraseColor(Color.TRANSPARENT)
    }

    /** Adds a one pixel outline around everything drawn on the sprite layer. */
    fun outlineSprites(color: Int) {
        val s = sprite ?: return
        val w = s.width
        val h = s.height
        s.getPixels(px, 0, w, 0, 0, w, h)
        System.arraycopy(px, 0, out, 0, px.size)
        for (y in 0 until h) {
            val row = y * w
            for (x in 0 until w) {
                val i = row + x
                if ((px[i] ushr 24) != 0) continue
                val solid = (x > 0 && (px[i - 1] ushr 24) > 100) ||
                    (x < w - 1 && (px[i + 1] ushr 24) > 100) ||
                    (y > 0 && (px[i - w] ushr 24) > 100) ||
                    (y < h - 1 && (px[i + w] ushr 24) > 100)
                if (solid) out[i] = color
            }
        }
        s.setPixels(out, 0, w, 0, 0, w, h)
    }

    fun compositeSprites() {
        val s = sprite ?: return
        bgCanvas.drawBitmap(s, 0f, 0f, null)
    }

    fun present(c: Canvas, w: Float, h: Float) {
        val b = bg ?: return
        c.save()
        c.clipRect(0f, 0f, w, h)
        dst.set(0f, 0f, b.width * scale, b.height * scale)
        c.drawBitmap(b, null, dst, presentPaint)
        c.restore()
    }

    /** Draws only the sprite layer (used for pet portraits). */
    fun presentSprites(c: Canvas, w: Float, h: Float) {
        val s = sprite ?: return
        c.save()
        c.clipRect(0f, 0f, w, h)
        dst.set(0f, 0f, s.width * scale, s.height * scale)
        c.drawBitmap(s, null, dst, presentPaint)
        c.restore()
    }

    companion object {
        const val OUTLINE = 0xFF241B35.toInt()
    }
}
