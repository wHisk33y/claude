package de.knuffi.app.render

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
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.random.Random

enum class Weather { CLEAR, CLOUDY, RAIN, SNOW }
enum class LayoutKind { HOME, WALLPAPER, BOX }

/** Where everything sits in the scene for a given size. */
class SceneLayout {
    var w = 1f
    var h = 1f
    var kind = LayoutKind.HOME
    var floorY = 0f
    var groundY = 0f
    var backGroundY = 0f
    var petSize = 1f
    val window = RectF()
    var vpX = 0f
    var vpY = 0f
    var bowlX = 0.16f
    var bedX = 0.78f
    var minX = 0.24f
    var maxX = 0.76f

    fun compute(w: Float, h: Float, kind: LayoutKind) {
        this.w = w
        this.h = h
        this.kind = kind
        when (kind) {
            LayoutKind.HOME -> {
                floorY = h * 0.595f
                groundY = h * 0.775f
                petSize = min(w * 0.52f, h * 0.27f)
                window.set(w * 0.07f, h * 0.245f, w * 0.43f, h * 0.46f)
            }
            LayoutKind.WALLPAPER -> {
                floorY = h * 0.6f
                groundY = h * 0.76f
                petSize = min(w * 0.4f, h * 0.2f)
                window.set(w * 0.08f, h * 0.2f, w * 0.44f, h * 0.42f)
            }
            LayoutKind.BOX -> {
                floorY = h * 0.6f
                groundY = h * 0.92f
                petSize = min(h * 0.5f, w * 0.34f)
                window.set(w * 0.06f, h * 0.09f, w * 0.3f, h * 0.48f)
            }
        }
        backGroundY = floorY + (groundY - floorY) * 0.38f
        vpX = w / 2f
        vpY = floorY - (h - floorY) * 1.4f
        bowlX = if (kind == LayoutKind.BOX) 0.12f else 0.15f
        bedX = if (kind == LayoutKind.BOX) 0.85f else 0.78f
        minX = if (kind == LayoutKind.BOX) 0.3f else 0.26f
        maxX = if (kind == LayoutKind.BOX) 0.7f else 0.74f
    }

    val propSize: Float get() = petSize * 0.78f
}

class RoomRenderer : Painter() {
    private val starField: FloatArray = Random(7).let { r -> FloatArray(300) { r.nextFloat() } }
    private val clear = Paint(Paint.ANTI_ALIAS_FLAG).apply { xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR) }
    private val path3 = Path()
    private val furniture = FurnitureRenderer()
    private var vignette: RadialGradient? = null
    private var vignetteKey = 0f

    private fun lampX(L: SceneLayout) = L.w * 0.53f
    private fun lampY(L: SceneLayout) = L.window.top + L.window.height() * 0.2f

    fun isCozy(room: String) = room == "room_cozy" || room !in setOf("room_forest", "room_ocean", "room_space", "room_candy")

    // ------------------------------------------------------------------ time of day

    fun darkness(hour: Float): Float = keyframe(hour, DARK)

    private fun keyframe(hour: Float, values: FloatArray): Float {
        var i = 0
        while (i < HOURS.size - 2 && hour > HOURS[i + 1]) i++
        val f = ((hour - HOURS[i]) / (HOURS[i + 1] - HOURS[i])).coerceIn(0f, 1f)
        return values[i] + (values[i + 1] - values[i]) * f
    }

    private fun skyColors(hour: Float, weather: Weather): Pair<Int, Int> {
        var i = 0
        while (i < HOURS.size - 2 && hour > HOURS[i + 1]) i++
        val f = ((hour - HOURS[i]) / (HOURS[i + 1] - HOURS[i])).coerceIn(0f, 1f)
        var top = Colors.lerp(SKY_TOP[i], SKY_TOP[i + 1], f)
        var bottom = Colors.lerp(SKY_BOTTOM[i], SKY_BOTTOM[i + 1], f)
        if (weather == Weather.RAIN || weather == Weather.CLOUDY) {
            val k = if (weather == Weather.RAIN) 0.45f else 0.25f
            top = Colors.lerp(top, 0xFF8E97AD.toInt(), k * (1f - darkness(hour) * 0.6f))
            bottom = Colors.lerp(bottom, 0xFFB9C0D0.toInt(), k * (1f - darkness(hour) * 0.6f))
        }
        return top to bottom
    }

    // ------------------------------------------------------------------ sky

    fun drawSky(c: Canvas, l: Float, t: Float, r: Float, b: Float, hour: Float, time: Float, weather: Weather) {
        val (top, bottom) = skyColors(hour, weather)
        vgrad(c, l, t, r, b, top, bottom)
        val w = r - l
        val h = b - t
        val dark = darkness(hour)
        val s = min(w, h)
        if (dark > 0.25f && weather != Weather.RAIN) {
            for (i in 0 until 34) {
                val sx = l + starField[i * 2] * w
                val sy = t + starField[i * 2 + 1] * h * 0.8f
                val tw = (sin(time * (1.5f + (i % 4) * 0.4f) + i * 1.3f) + 1f) / 2f
                val a = dark * (0.3f + 0.7f * tw)
                val sr = s * 0.006f * (0.6f + tw * 0.8f)
                fill.shader = null
                fill.color = Colors.alpha(Color.WHITE, a)
                c.drawCircle(sx, sy, sr, fill)
                if (i % 7 == 0) {
                    Shapes.sparkle(path, sx, sy, sr * 4f)
                    fill.color = Colors.alpha(0xFFE8EEFF.toInt(), a * 0.8f)
                    c.drawPath(path, fill)
                }
            }
        }
        val rr = s * 0.1f
        if (hour in 6f..20f) {
            val p = (hour - 6f) / 14f
            val x = l + w * (0.12f + 0.76f * p)
            val y = b - h * (0.2f + 0.58f * sin(p * PI.toFloat()))
            val dim = if (weather == Weather.RAIN) 0.35f else if (weather == Weather.CLOUDY) 0.7f else 1f
            shader(radial(x, y, rr * 3.2f, intArrayOf(Colors.alpha(0xFFFFF3B0.toInt(), 0.75f * dim), Colors.alpha(0xFFFFF3B0.toInt(), 0.2f * dim), Colors.alpha(0xFFFFF3B0.toInt(), 0f)), floatArrayOf(0f, 0.4f, 1f)))
            c.drawCircle(x, y, rr * 3.2f, fill)
            fill.shader = null
            c.save()
            c.rotate(time * 8f, x, y)
            fill.color = Colors.alpha(0xFFFFF1A8.toInt(), 0.35f * dim)
            for (k in 0 until 10) {
                c.rotate(36f, x, y)
                path.reset()
                path.moveTo(x - rr * 0.12f, y - rr * 1.3f)
                path.lineTo(x, y - rr * 2.1f)
                path.lineTo(x + rr * 0.12f, y - rr * 1.3f)
                path.close()
                c.drawPath(path, fill)
            }
            c.restore()
            val warm = hour < 8f || hour > 18f
            shader(radial(x - rr * 0.3f, y - rr * 0.3f, rr * 1.3f, intArrayOf(0xFFFFFDE8.toInt(), if (warm) 0xFFFFB067.toInt() else 0xFFFFD84A.toInt(), if (warm) 0xFFFF8A3D.toInt() else 0xFFFFB300.toInt()), floatArrayOf(0f, 0.6f, 1f)))
            c.drawCircle(x, y, rr, fill)
            fill.shader = null
        } else {
            val nh = if (hour >= 20f) hour - 20f else hour + 4f
            val p = nh / 10f
            val x = l + w * (0.15f + 0.7f * p)
            val y = b - h * (0.28f + 0.5f * sin(p * PI.toFloat()))
            shader(radial(x, y, rr * 2.6f, intArrayOf(Colors.alpha(0xFFE8ECFF.toInt(), 0.5f), Colors.alpha(0xFFE8ECFF.toInt(), 0f))))
            c.drawCircle(x, y, rr * 2.6f, fill)
            fill.shader = null
            shader(radial(x - rr * 0.35f, y - rr * 0.35f, rr * 1.4f, intArrayOf(0xFFFFFFF4.toInt(), 0xFFF1EDD3.toInt(), 0xFFC9C3A6.toInt()), floatArrayOf(0f, 0.6f, 1f)))
            c.drawCircle(x, y, rr, fill)
            fill.shader = null
            fill.color = Colors.alpha(0xFFB9B294.toInt(), 0.45f)
            c.drawCircle(x + rr * 0.3f, y - rr * 0.2f, rr * 0.2f, fill)
            c.drawCircle(x - rr * 0.25f, y + rr * 0.35f, rr * 0.14f, fill)
            c.drawCircle(x + rr * 0.1f, y + rr * 0.45f, rr * 0.09f, fill)
        }
        val clouds = when (weather) {
            Weather.CLEAR -> 2
            Weather.CLOUDY -> 4
            Weather.RAIN -> 5
            Weather.SNOW -> 4
        }
        val cloudDark = if (weather == Weather.RAIN) 0.35f else 0f
        for (k in 0 until clouds) {
            val speed = 0.01f + (k % 3) * 0.004f
            val cx = l + ((((time * speed + k * 0.37f) % 1.5f) + 1.5f) % 1.5f - 0.25f) * w
            val cy = t + h * (0.18f + (k % 3) * 0.16f)
            cloud(c, cx, cy, w * (0.16f + (k % 2) * 0.05f), dark, cloudDark)
        }
        if (weather == Weather.RAIN) {
            line.shader = null
            line.color = Colors.alpha(0xFFDDEBFF.toInt(), 0.55f)
            line.strokeWidth = max(1f, s * 0.006f)
            for (i in 0 until 46) {
                val rx = l + starField[100 + i] * w
                val fall = ((time * (1.1f + starField[150 + i] * 0.5f) + starField[200 + i]) % 1f)
                val ry = t + fall * h
                c.drawLine(rx, ry, rx - s * 0.02f, ry + s * 0.06f, line)
            }
        } else if (weather == Weather.SNOW) {
            fill.shader = null
            fill.color = Colors.alpha(Color.WHITE, 0.9f)
            for (i in 0 until 46) {
                val fall = ((time * (0.08f + starField[150 + i] * 0.06f) + starField[200 + i]) % 1f)
                val rx = l + (starField[100 + i] + sin(time * 0.8f + i) * 0.03f) * w
                val ry = t + fall * h
                c.drawCircle(rx, ry, s * (0.006f + starField[250 + (i % 50)] * 0.008f), fill)
            }
        }
    }

    private fun cloud(c: Canvas, x: Float, y: Float, s: Float, dark: Float, rainy: Float) {
        Shapes.cloud(path3, x, y, s)
        val top = Colors.lerp(Colors.lerp(Color.WHITE, 0xFF8E94B8.toInt(), dark * 0.6f), 0xFF9AA3B8.toInt(), rainy)
        val bottom = Colors.lerp(Colors.lerp(0xFFDDE6F5.toInt(), 0xFF5A6090.toInt(), dark * 0.6f), 0xFF6F7890.toInt(), rainy)
        shader(LinearGradient(0f, y - s * 0.45f, 0f, y + s * 0.42f, Colors.alpha(top, 0.95f), Colors.alpha(bottom, 0.95f), Shader.TileMode.CLAMP))
        c.drawPath(path3, fill)
        fill.shader = null
        fill.color = Colors.alpha(Color.WHITE, 0.5f * (1f - dark))
        c.drawCircle(x - s * 0.08f, y - s * 0.18f, s * 0.14f, fill)
    }

    // ------------------------------------------------------------------ layers

    /** Things drawn before the cached static layer (sky seen through transparent parts). */
    fun drawBehind(c: Canvas, L: SceneLayout, room: String, hour: Float, t: Float, weather: Weather) {
        val w = L.w
        val h = L.h
        when (room) {
            "room_forest", "room_candy" -> {
                if (room == "room_candy") {
                    val dark = darkness(hour)
                    vgrad(c, 0f, 0f, w, L.floorY + 2f, Colors.lerp(0xFFFFC2E2.toInt(), 0xFF4A3470.toInt(), dark * 0.8f), Colors.lerp(0xFFFFEFF7.toInt(), 0xFF8C5FA0.toInt(), dark * 0.8f))
                    for (k in 0 until 3) {
                        val cx = ((((t * 0.012f + k * 0.4f) % 1.4f) + 1.4f) % 1.4f - 0.2f) * w
                        Shapes.cloud(path3, cx, h * (0.12f + k * 0.08f), w * (0.16f + k * 0.02f))
                        shader(LinearGradient(0f, h * 0.05f, 0f, h * 0.35f, if (k % 2 == 0) 0xFFFFE0F0.toInt() else 0xFFE0F0FF.toInt(), if (k % 2 == 0) 0xFFFFB3D6.toInt() else 0xFFB3D6FF.toInt(), Shader.TileMode.CLAMP))
                        c.drawPath(path3, fill)
                        fill.shader = null
                    }
                } else {
                    drawSky(c, 0f, 0f, w, L.floorY + 2f, hour, t, weather)
                }
            }
            "room_ocean" -> {
                val dark = darkness(hour) * 0.7f
                vgrad(c, 0f, 0f, w, h, Colors.lerp(0xFF7FE0FF.toInt(), 0xFF0E2F5E.toInt(), dark), Colors.lerp(0xFF1466A8.toInt(), 0xFF061634.toInt(), dark))
            }
            "room_space" -> {
                vgrad(c, 0f, 0f, w, h, 0xFF0A0B2A.toInt(), 0xFF2A1A56.toInt())
                shader(radial(w * 0.25f, h * 0.25f, w * 0.6f, intArrayOf(Colors.alpha(0xFFB04FD6.toInt(), 0.35f), Colors.alpha(0xFFB04FD6.toInt(), 0f))))
                c.drawCircle(w * 0.25f, h * 0.25f, w * 0.6f, fill)
                shader(radial(w * 0.8f, h * 0.45f, w * 0.5f, intArrayOf(Colors.alpha(0xFF3FA9F5.toInt(), 0.28f), Colors.alpha(0xFF3FA9F5.toInt(), 0f))))
                c.drawCircle(w * 0.8f, h * 0.45f, w * 0.5f, fill)
                fill.shader = null
                for (i in 0 until 70) {
                    val sx = starField[i * 2] * w
                    val sy = starField[i * 2 + 1] * L.floorY
                    val tw = (sin(t * (1.2f + (i % 5) * 0.35f) + i) + 1f) / 2f
                    fill.color = Colors.alpha(Color.WHITE, 0.25f + 0.75f * tw)
                    c.drawCircle(sx, sy, w * 0.0025f * (1f + tw), fill)
                    if (i % 11 == 0) {
                        Shapes.sparkle(path, sx, sy, w * 0.012f * (0.6f + tw))
                        c.drawPath(path, fill)
                    }
                }
                val cycle = t % 7f
                if (cycle < 0.9f) {
                    val p = cycle / 0.9f
                    val sx = w * (0.1f + 0.6f * p)
                    val sy = h * (0.06f + 0.2f * p)
                    line.shader = LinearGradient(sx, sy, sx - w * 0.12f, sy - h * 0.04f, Colors.alpha(Color.WHITE, 1f - p), Colors.alpha(Color.WHITE, 0f), Shader.TileMode.CLAMP)
                    line.strokeWidth = w * 0.006f
                    c.drawLine(sx, sy, sx - w * 0.12f, sy - h * 0.04f, line)
                    line.shader = null
                }
            }
            else -> {
                val win = L.window
                drawSky(c, win.left, win.top, win.right, win.bottom, hour, t, weather)
            }
        }
    }

    /** Cached static layer (transparent where the sky shows through). */
    fun drawStatic(c: Canvas, L: SceneLayout, room: String, f: Furnishing = Furnishing()) {
        when (room) {
            "room_forest" -> staticForest(c, L)
            "room_ocean" -> staticOcean(c, L)
            "room_space" -> staticSpace(c, L)
            "room_candy" -> staticCandy(c, L)
            else -> staticCozy(c, L, f)
        }
    }

    /** Animated parts that sit in front of the static layer but behind the pet. */
    fun drawAnimated(c: Canvas, L: SceneLayout, room: String, hour: Float, t: Float, f: Furnishing = Furnishing()) {
        if (isCozy(room)) f.event?.let { furniture.garland(c, L, it, t) }
        val w = L.w
        val h = L.h
        val dark = darkness(hour)
        when (room) {
            "room_forest" -> {
                if (dark > 0.3f) {
                    for (i in 0 until 12) {
                        val fx = (0.08f + 0.84f * starField[50 + i] + sin(t * 0.5f + i) * 0.05f) * w
                        val fy = L.floorY - h * 0.2f + (starField[70 + i] * 0.3f + sin(t * 0.8f + i * 2f) * 0.03f) * h
                        val glow = (sin(t * 3f + i) + 1f) / 2f
                        shader(radial(fx, fy, w * 0.035f, intArrayOf(Colors.alpha(0xFFFFF59D.toInt(), 0.7f * glow * dark), Colors.alpha(0xFFFFF59D.toInt(), 0f))))
                        c.drawCircle(fx, fy, w * 0.035f, fill)
                        fill.shader = null
                        fill.color = Colors.alpha(0xFFFFFDE0.toInt(), (0.5f + 0.5f * glow) * dark)
                        c.drawCircle(fx, fy, w * 0.006f, fill)
                    }
                } else {
                    // soft light rays through the trees
                    for (k in 0 until 3) {
                        val x0 = w * (0.2f + k * 0.28f) + sin(t * 0.3f + k) * w * 0.03f
                        path.reset()
                        path.moveTo(x0 - w * 0.03f, 0f)
                        path.lineTo(x0 + w * 0.03f, 0f)
                        path.lineTo(x0 + w * 0.16f, L.groundY)
                        path.lineTo(x0 - w * 0.02f, L.groundY)
                        path.close()
                        shader(LinearGradient(0f, 0f, 0f, L.groundY, Colors.alpha(0xFFFFF6D0.toInt(), 0.22f * (1f - dark)), Colors.alpha(0xFFFFF6D0.toInt(), 0f), Shader.TileMode.CLAMP))
                        c.drawPath(path, fill)
                        fill.shader = null
                    }
                }
            }
            "room_ocean" -> {
                // caustics & rays
                for (k in 0 until 4) {
                    val x0 = w * (0.12f + k * 0.26f) + sin(t * 0.4f + k) * w * 0.05f
                    path.reset()
                    path.moveTo(x0 - w * 0.04f, 0f)
                    path.lineTo(x0 + w * 0.04f, 0f)
                    path.lineTo(x0 + w * 0.18f, L.groundY)
                    path.lineTo(x0 - w * 0.03f, L.groundY)
                    path.close()
                    shader(LinearGradient(0f, 0f, 0f, L.groundY, Colors.alpha(Color.WHITE, 0.16f), Colors.alpha(Color.WHITE, 0f), Shader.TileMode.CLAMP))
                    c.drawPath(path, fill)
                    fill.shader = null
                }
                line.shader = null
                line.color = Colors.alpha(Color.WHITE, 0.16f)
                line.strokeWidth = w * 0.004f
                for (k in 0 until 6) {
                    path.reset()
                    val y0 = L.floorY + (h - L.floorY) * (0.1f + k * 0.14f)
                    var x = 0f
                    path.moveTo(0f, y0)
                    while (x < w) {
                        x += w / 12f
                        path.lineTo(x, y0 + sin(x / w * 18f + t * 1.5f + k) * h * 0.006f)
                    }
                    c.drawPath(path, line)
                }
                // seaweed
                val weeds = floatArrayOf(0.05f, 0.12f, 0.88f, 0.95f)
                for ((i, wx) in weeds.withIndex()) {
                    val bx = wx * w
                    val hh = h * (0.22f + (i % 2) * 0.06f)
                    val baseY = L.floorY + (h - L.floorY) * 0.25f
                    val segs = 8
                    var px = bx
                    var py = baseY
                    for (k in 1..segs) {
                        val yy = baseY - hh * k / segs
                        val xx = bx + sin(t * 1.3f + i + k * 0.7f) * w * 0.02f * k / segs * 2f
                        line.color = Colors.lerp(0xFF3FBF72.toInt(), 0xFF1E7A4A.toInt(), k / segs.toFloat())
                        line.strokeWidth = w * 0.028f * (1f - k / (segs + 3f))
                        c.drawLine(px, py, xx, yy, line)
                        px = xx
                        py = yy
                    }
                }
                // fish
                for (k in 0 until 3) {
                    val speed = 0.035f + k * 0.015f
                    val p = ((t * speed + k * 0.43f) % 1.3f) - 0.15f
                    val dir = if (k % 2 == 0) 1f else -1f
                    val fxp = if (dir > 0) p * w else (1f - p) * w
                    val fyp = h * (0.2f + k * 0.12f) + sin(t * 2f + k) * h * 0.012f
                    fish(c, fxp, fyp, h * 0.028f, dir, t, if (k == 1) 0xFFFFD43B.toInt() else if (k == 2) 0xFFFF7EB6.toInt() else 0xFFFF9E4D.toInt())
                }
                // bubbles
                for (i in 0 until 12) {
                    val base = weeds[i % weeds.size] * w
                    val p = (t * 0.07f + i * 0.13f) % 1f
                    val bx = base + sin(t * 2f + i) * w * 0.015f
                    val by = L.floorY + (h - L.floorY) * 0.2f - p * (L.floorY + h * 0.1f)
                    val br = h * (0.006f + (i % 3) * 0.003f)
                    shader(radial(bx, by, br, intArrayOf(Colors.alpha(Color.WHITE, 0.05f), Colors.alpha(Color.WHITE, 0.55f)), floatArrayOf(0.6f, 1f)))
                    c.drawCircle(bx, by, br, fill)
                    fill.shader = null
                    fill.color = Colors.alpha(Color.WHITE, 0.8f)
                    c.drawCircle(bx - br * 0.35f, by - br * 0.35f, br * 0.25f, fill)
                }
            }
            "room_candy" -> {
                fill.shader = null
                for (i in 0 until 10) {
                    val gx = starField[120 + i] * w
                    val gy = L.floorY + starField[140 + i] * (h - L.floorY) * 0.6f
                    val tw = (sin(t * 3f + i * 1.7f) + 1f) / 2f
                    Shapes.sparkle(path, gx, gy, w * 0.012f * tw)
                    fill.color = Colors.alpha(Color.WHITE, 0.9f * tw)
                    c.drawPath(path, fill)
                }
            }
            else -> Unit
        }
    }

    private fun fish(c: Canvas, x: Float, y: Float, s: Float, dir: Float, t: Float, color: Int) {
        c.save()
        c.scale(dir, 1f, x, y)
        val wag = sin(t * 10f) * s * 0.15f
        path.reset()
        path.moveTo(x - s * 0.7f, y)
        path.lineTo(x - s * 1.5f, y - s * 0.6f + wag)
        path.lineTo(x - s * 1.4f, y + s * 0.6f + wag)
        path.close()
        shaded(c, path, Colors.lighten(color, 0.4f), color, Colors.darken(color, 0.3f), outline = false)
        path.reset()
        path.addOval(x - s, y - s * 0.62f, x + s, y + s * 0.62f, Path.Direction.CW)
        shaded(c, path, Colors.lighten(color, 0.5f), color, Colors.darken(color, 0.3f), outline = false)
        fill.color = Color.WHITE
        c.drawCircle(x + s * 0.5f, y - s * 0.12f, s * 0.2f, fill)
        fill.color = 0xFF1F1B2E.toInt()
        c.drawCircle(x + s * 0.56f, y - s * 0.12f, s * 0.11f, fill)
        c.restore()
    }

    /** Light, time of day and vignette on top of everything (except particles & UI). */
    fun drawLighting(c: Canvas, L: SceneLayout, room: String, hour: Float, t: Float, lightsOff: Boolean, darkUi: Boolean, f: Furnishing = Furnishing()) {
        val w = L.w
        val h = L.h
        val dark = darkness(hour)
        val cozy = isCozy(room)
        if (cozy) {
            val win = L.window
            val shaftA = if (lightsOff) 0.1f else 0.24f * (1f - dark) + 0.08f * dark
            if (shaftA > 0.01f) {
                val warm = when {
                    dark > 0.6f -> 0xFFB9C8FF.toInt()
                    hour > 17f || hour < 8f -> 0xFFFFC98A.toInt()
                    else -> 0xFFFFF4D6.toInt()
                }
                val shift = w * 0.22f
                path.reset()
                path.moveTo(win.left, win.top)
                path.lineTo(win.right, win.top)
                path.lineTo(win.right + shift, L.groundY + h * 0.03f)
                path.lineTo(win.left + shift * 0.6f, L.groundY + h * 0.05f)
                path.close()
                shader(LinearGradient(0f, win.top, 0f, L.groundY, Colors.alpha(warm, shaftA), Colors.alpha(warm, 0f), Shader.TileMode.CLAMP))
                c.drawPath(path, fill)
                fill.shader = null
                if (dark < 0.5f && !lightsOff) {
                    for (i in 0 until 14) {
                        val px = win.left + (starField[i + 10] + sin(t * 0.2f + i) * 0.05f) * (win.width() + shift * 0.7f)
                        val py = win.bottom + ((starField[i + 30] + t * 0.015f * (1 + i % 3)) % 1f) * (L.groundY - win.bottom)
                        val a = (sin(t * 1.5f + i) + 1f) / 2f
                        fill.color = Colors.alpha(Color.WHITE, 0.55f * a * (1f - dark))
                        c.drawCircle(px, py, w * 0.0035f, fill)
                    }
                }
            }
        }
        val overlay = when {
            lightsOff -> 0.62f
            room == "room_space" -> 0.05f
            cozy -> dark * 0.4f
            else -> dark * 0.38f
        } + if (darkUi) 0.06f else 0f
        if (overlay > 0.01f) {
            fill.shader = null
            fill.color = Colors.alpha(0xFF141035.toInt(), overlay)
            c.drawRect(0f, 0f, w, h, fill)
        }
        if (cozy && !lightsOff && dark > 0.15f) {
            // warm glow from the lamp
            val lx = lampX(L)
            val ly = lampY(L) + if (f.lamp == "lamp_lava") L.h * 0.02f else 0f
            val gr = w * 0.55f
            val glow = furniture.lampGlow(f.lamp)
            val pulse = if (f.lamp == "lamp_lava") 0.85f + 0.15f * sin(t * 1.3f) else 1f
            shader(radial(lx, ly, gr, intArrayOf(Colors.alpha(glow, 0.42f * dark * pulse), Colors.alpha(glow, 0.12f * dark), Colors.alpha(glow, 0f)), floatArrayOf(0f, 0.45f, 1f)))
            c.drawCircle(lx, ly, gr, fill)
            fill.shader = null
            c.save()
            c.scale(1f, 0.25f, w * 0.5f, L.groundY)
            shader(radial(w * 0.5f, L.groundY, w * 0.5f, intArrayOf(Colors.alpha(0xFFFFD18A.toInt(), 0.22f * dark), Colors.alpha(0xFFFFD18A.toInt(), 0f))))
            c.drawCircle(w * 0.5f, L.groundY, w * 0.5f, fill)
            fill.shader = null
            c.restore()
        }
        if (room == "room_forest" && dark > 0.4f) {
            // moonlight tint
            fill.color = Colors.alpha(0xFF3E5BB8.toInt(), 0.08f * dark)
            c.drawRect(0f, 0f, w, h, fill)
        }
        // vignette
        val key = w * 10000f + h
        if (vignette == null || vignetteKey != key) {
            vignetteKey = key
            vignette = RadialGradient(
                w / 2f, h * 0.55f, max(w, h) * 0.75f,
                intArrayOf(Colors.alpha(0xFF1A1030.toInt(), 0f), Colors.alpha(0xFF1A1030.toInt(), 0f), Colors.alpha(0xFF1A1030.toInt(), 0.32f)),
                floatArrayOf(0f, 0.55f, 1f), Shader.TileMode.CLAMP,
            )
        }
        fill.color = Color.WHITE
        fill.shader = vignette
        c.drawRect(0f, 0f, w, h, fill)
        fill.shader = null
    }

    // ------------------------------------------------------------------ cozy room

    private fun staticCozy(c: Canvas, L: SceneLayout, f: Furnishing) {
        val w = L.w
        val h = L.h
        val fy = L.floorY
        val win = L.window
        val wt = fy - (fy - win.bottom) * 0.55f
        val style = furniture.wallStyle(f.wall)
        furniture.wall(c, L, f.wall, wt)

        // floor with perspective planks
        vgrad(c, 0f, fy, w, h, 0xFFEDBA8C.toInt(), 0xFFC4865A.toInt())
        c.save()
        c.clipRect(0f, fy, w, h)
        line.shader = null
        line.color = Colors.alpha(0xFF8A5530.toInt(), 0.35f)
        line.strokeWidth = max(1f, w * 0.003f)
        for (i in -10..10) {
            val bx = L.vpX + i * w * 0.14f
            c.drawLine(bx, h, L.vpX + (bx - L.vpX) * ((fy - L.vpY) / (h - L.vpY)), fy, line)
        }
        for (k in 1..7) {
            val y = fy + (h - fy) * (k / 7f).pow(1.6f)
            val nextY = fy + (h - fy) * ((k + 1) / 7f).pow(1.6f)
            val off = if (k % 2 == 0) 0.07f else 0f
            var jx = off * w
            while (jx < w) {
                c.drawLine(jx, y, jx, min(nextY, h), line)
                jx += w * 0.28f
            }
        }
        shader(LinearGradient(0f, fy, 0f, fy + (h - fy) * 0.5f, Colors.alpha(Color.WHITE, 0f), Colors.alpha(Color.WHITE, 0.14f), Shader.TileMode.MIRROR))
        c.drawRect(0f, fy, w, fy + (h - fy) * 0.9f, fill)
        shader(LinearGradient(0f, fy, 0f, fy + h * 0.03f, Colors.alpha(0xFF5A2E18.toInt(), 0.35f), Colors.alpha(0xFF5A2E18.toInt(), 0f), Shader.TileMode.CLAMP))
        c.drawRect(0f, fy, w, fy + h * 0.03f, fill)
        fill.shader = null
        c.restore()
        // baseboard
        box(c, 0f, fy - h * 0.018f, w, fy + h * 0.002f, 0f, 0xFFFFFFFF.toInt(), 0xFFEDE3EC.toInt())

        furniture.rug(c, L, f.rug)

        // window frame with a transparent hole for the sky
        val fw = w * 0.022f
        box(c, win.left - fw, win.top - fw, win.right + fw, win.bottom + fw, w * 0.02f, 0xFFFFFFFF.toInt(), 0xFFE8DDE6.toInt(), shadow = h * 0.008f)
        c.drawRoundRect(win.left, win.top, win.right, win.bottom, w * 0.01f, w * 0.01f, clear)
        fill.shader = null
        // inner bevel
        line.color = Colors.alpha(0xFFB9A4B5.toInt(), 0.6f)
        line.strokeWidth = w * 0.005f
        c.drawRoundRect(win.left, win.top, win.right, win.bottom, w * 0.01f, w * 0.01f, line)
        // muntins
        val mx = (win.left + win.right) / 2f
        val my = (win.top + win.bottom) / 2f
        box(c, mx - w * 0.008f, win.top, mx + w * 0.008f, win.bottom, 0f, 0xFFFFFFFF.toInt(), 0xFFE8DDE6.toInt())
        box(c, win.left, my - w * 0.008f, win.right, my + w * 0.008f, 0f, 0xFFFFFFFF.toInt(), 0xFFE8DDE6.toInt())
        // glass glare
        c.save()
        c.clipRect(win)
        fill.color = Colors.alpha(Color.WHITE, 0.16f)
        path.reset()
        path.moveTo(win.left + win.width() * 0.1f, win.bottom)
        path.lineTo(win.left + win.width() * 0.35f, win.top)
        path.lineTo(win.left + win.width() * 0.47f, win.top)
        path.lineTo(win.left + win.width() * 0.22f, win.bottom)
        path.close()
        c.drawPath(path, fill)
        c.restore()
        // sill with a little cactus
        box(c, win.left - fw * 1.8f, win.bottom + fw * 0.6f, win.right + fw * 1.8f, win.bottom + fw * 1.9f, fw * 0.4f, 0xFFFFFFFF.toInt(), 0xFFDCCFDA.toInt(), shadow = h * 0.006f)
        val cx = win.right - win.width() * 0.2f
        val cy = win.bottom + fw * 0.6f
        path.reset()
        path.addRoundRect(cx - w * 0.028f, cy - h * 0.035f, cx + w * 0.028f, cy + 1f, w * 0.01f, w * 0.01f, Path.Direction.CW)
        shaded(c, path, 0xFFFFB38A.toInt(), 0xFFE5773F.toInt(), 0xFFA84E22.toInt(), outlineW = w * 0.002f)
        path.reset()
        path.addRoundRect(cx - w * 0.016f, cy - h * 0.085f, cx + w * 0.016f, cy - h * 0.03f, w * 0.016f, w * 0.016f, Path.Direction.CW)
        shaded(c, path, 0xFF9BE3A8.toInt(), 0xFF4FB36A.toInt(), 0xFF2B7A40.toInt(), outlineW = w * 0.002f)
        fill.color = 0xFFFF7EB6.toInt()
        c.drawCircle(cx, cy - h * 0.088f, w * 0.008f, fill)
        f.event?.let { furniture.sillItem(c, it, win.left + win.width() * 0.3f, cy, h * 0.05f) }
        // curtains
        curtain(c, win.left - fw * 3.5f, win.left + win.width() * 0.12f, win.top - fw * 2f, win.bottom + fw * 3f, true, w, style)
        curtain(c, win.right - win.width() * 0.12f, win.right + fw * 3.5f, win.top - fw * 2f, win.bottom + fw * 3f, false, w, style)
        box(c, win.left - fw * 5f, win.top - fw * 2.8f, win.right + fw * 5f, win.top - fw * 1.8f, fw, 0xFFD8A657.toInt(), 0xFF9C6A2A.toInt(), shadow = h * 0.004f)
        for (ex in floatArrayOf(win.left - fw * 5f, win.right + fw * 5f)) {
            path.reset()
            path.addCircle(ex, win.top - fw * 2.3f, fw * 1.1f, Path.Direction.CW)
            shaded(c, path, 0xFFFFF1B8.toInt(), 0xFFFFC83D.toInt(), 0xFFB57B00.toInt(), outlineW = w * 0.002f)
        }

        // picture frame
        val pl = w * 0.6f
        val pr = w * 0.86f
        val pt = win.top
        val pb = win.top + (win.height()) * 0.55f
        box(c, pl, pt, pr, pb, w * 0.01f, 0xFFFFE08A.toInt(), 0xFFC98C1B.toInt(), shadow = h * 0.008f)
        val pad = w * 0.018f
        c.save()
        c.clipRect(pl + pad, pt + pad, pr - pad, pb - pad)
        furniture.picture(c, f.picture, pl + pad, pt + pad, pr - pad, pb - pad)
        c.restore()
        line.color = Colors.alpha(0xFF8A5A10.toInt(), 0.4f)
        line.strokeWidth = w * 0.003f
        c.drawRect(pl + pad, pt + pad, pr - pad, pb - pad, line)

        // lamp (glow is drawn dynamically)
        furniture.lamp(c, L, f.lamp, lampX(L), lampY(L))

        // shelf with books
        val sl = w * 0.6f
        val sr = w * 0.9f
        val st = pb + (wt - pb) * 0.55f
        box(c, sl, st, sr, st + h * 0.012f, h * 0.003f, 0xFFE9B384.toInt(), 0xFFB9774A.toInt(), shadow = h * 0.008f)
        val bookColors = intArrayOf(0xFFFF7EB6.toInt(), 0xFF7C6CFF.toInt(), 0xFF2EC4A0.toInt(), 0xFFFFC83D.toInt(), 0xFF4DA8FF.toInt())
        var bx = sl + w * 0.015f
        for (i in 0 until 5) {
            val bw = w * (0.022f + (i % 2) * 0.008f)
            val bh = h * (0.045f + (i % 3) * 0.01f)
            box(c, bx, st - bh, bx + bw, st, w * 0.003f, Colors.lighten(bookColors[i], 0.2f), Colors.darken(bookColors[i], 0.15f))
            fill.color = Colors.alpha(Color.WHITE, 0.6f)
            c.drawRect(bx + bw * 0.2f, st - bh * 0.7f, bx + bw * 0.8f, st - bh * 0.62f, fill)
            bx += bw + w * 0.004f
        }
        // little trophy
        val tx = sr - w * 0.05f
        path.reset()
        path.moveTo(tx - w * 0.025f, st - h * 0.045f)
        path.quadTo(tx, st - h * 0.005f, tx + w * 0.025f, st - h * 0.045f)
        path.close()
        shaded(c, path, 0xFFFFF4C2.toInt(), 0xFFFFC83D.toInt(), 0xFFC08000.toInt(), outlineW = w * 0.002f)
        box(c, tx - w * 0.012f, st - h * 0.012f, tx + w * 0.012f, st, w * 0.003f, 0xFFFFD86A.toInt(), 0xFFC08000.toInt())

        // plant in the corner
        furniture.plant(c, L, f.plant)
    }

    private fun curtain(c: Canvas, l: Float, r: Float, t: Float, b: Float, left: Boolean, w: Float, style: WallStyle) {
        val tieY = t + (b - t) * 0.62f
        path.reset()
        if (left) {
            path.moveTo(l, t)
            path.lineTo(r, t)
            path.quadTo(r - (r - l) * 0.1f, tieY - (b - t) * 0.1f, l + (r - l) * 0.55f, tieY)
            path.quadTo(l + (r - l) * 0.75f, b - (b - t) * 0.05f, l + (r - l) * 0.6f, b)
            path.lineTo(l, b)
        } else {
            path.moveTo(r, t)
            path.lineTo(l, t)
            path.quadTo(l + (r - l) * 0.1f, tieY - (b - t) * 0.1f, r - (r - l) * 0.55f, tieY)
            path.quadTo(r - (r - l) * 0.75f, b - (b - t) * 0.05f, r - (r - l) * 0.6f, b)
            path.lineTo(r, b)
        }
        path.close()
        shader(LinearGradient(l, 0f, r, 0f, style.curtain, null, Shader.TileMode.MIRROR))
        c.drawPath(path, fill)
        fill.shader = null
        line.color = Colors.alpha(style.curtainLine, 0.35f)
        line.strokeWidth = w * 0.003f
        c.drawPath(path, line)
        // sash
        val sx = if (left) l + (r - l) * 0.55f else r - (r - l) * 0.55f
        path.reset()
        path.addRoundRect(sx - w * 0.03f, tieY - w * 0.012f, sx + w * 0.03f, tieY + w * 0.012f, w * 0.01f, w * 0.01f, Path.Direction.CW)
        shaded(c, path, 0xFFFFF1B8.toInt(), 0xFFFFC83D.toInt(), 0xFFB57B00.toInt(), outlineW = w * 0.002f)
    }

    // ------------------------------------------------------------------ forest

    private fun staticForest(c: Canvas, L: SceneLayout) {
        val w = L.w
        val h = L.h
        val fy = L.floorY
        // far mountains with haze
        path.reset()
        path.moveTo(0f, fy)
        path.lineTo(0f, fy - h * 0.16f)
        path.lineTo(w * 0.18f, fy - h * 0.3f)
        path.lineTo(w * 0.36f, fy - h * 0.18f)
        path.lineTo(w * 0.55f, fy - h * 0.34f)
        path.lineTo(w * 0.78f, fy - h * 0.2f)
        path.lineTo(w, fy - h * 0.28f)
        path.lineTo(w, fy)
        path.close()
        shader(LinearGradient(0f, fy - h * 0.34f, 0f, fy, 0xFF8FA8D8.toInt(), 0xFFC8DDF0.toInt(), Shader.TileMode.CLAMP))
        c.drawPath(path, fill)
        fill.shader = null
        // snow caps
        fill.color = Colors.alpha(Color.WHITE, 0.8f)
        for ((px, py) in listOf(w * 0.18f to fy - h * 0.3f, w * 0.55f to fy - h * 0.34f)) {
            path.reset()
            path.moveTo(px, py)
            path.lineTo(px - w * 0.05f, py + h * 0.04f)
            path.lineTo(px + w * 0.05f, py + h * 0.04f)
            path.close()
            c.drawPath(path, fill)
        }
        // rolling hills
        path.reset()
        path.moveTo(0f, fy)
        var x = 0f
        while (x <= w) {
            path.lineTo(x, fy - h * 0.1f - sin(x / w * 6f) * h * 0.035f)
            x += w / 30f
        }
        path.lineTo(w, fy)
        path.close()
        shader(LinearGradient(0f, fy - h * 0.15f, 0f, fy, 0xFF7ACB8A.toInt(), 0xFF5BAF6E.toInt(), Shader.TileMode.CLAMP))
        c.drawPath(path, fill)
        fill.shader = null
        // tree line
        val trees = floatArrayOf(0.04f, 0.17f, 0.3f, 0.7f, 0.84f, 0.96f)
        for ((i, tx) in trees.withIndex()) {
            val px = tx * w
            val base = fy + h * 0.01f
            val th = h * (0.26f + (i % 3) * 0.05f)
            box(c, px - w * 0.02f, base - th * 0.5f, px + w * 0.02f, base, w * 0.01f, 0xFFA87550.toInt(), 0xFF6E4527.toInt())
            val cc = if (i % 2 == 0) 0xFF4FB06A.toInt() else 0xFF3F9A5A.toInt()
            for ((ox, oy, r) in listOf(Triple(0f, -0.82f, 0.28f), Triple(-0.2f, -0.62f, 0.22f), Triple(0.2f, -0.64f, 0.23f), Triple(0f, -0.55f, 0.2f))) {
                path.reset()
                path.addCircle(px + ox * th, base + oy * th, r * th, Path.Direction.CW)
                shaded(c, path, Colors.lighten(cc, 0.3f), cc, Colors.darken(cc, 0.35f), outline = false)
            }
        }
        // meadow floor
        vgrad(c, 0f, fy, w, h, 0xFF9BDB86.toInt(), 0xFF5FAE5C.toInt())
        c.save()
        c.clipRect(0f, fy, w, h)
        fill.color = Colors.alpha(Color.WHITE, 0.08f)
        for (i in -6..6 step 2) {
            val bx = L.vpX + i * w * 0.16f
            path.reset()
            path.moveTo(L.vpX + (bx - L.vpX) * ((fy - L.vpY) / (h - L.vpY)), fy)
            path.lineTo(bx, h)
            path.lineTo(bx + w * 0.16f, h)
            path.lineTo(L.vpX + (bx + w * 0.16f - L.vpX) * ((fy - L.vpY) / (h - L.vpY)), fy)
            path.close()
            c.drawPath(path, fill)
        }
        c.restore()
        // grass tufts & flowers
        line.shader = null
        for (i in 0 until 40) {
            val gx = starField[i] * w
            val gy = fy + starField[i + 40] * (h - fy)
            val gs = h * 0.012f * (0.6f + (gy - fy) / (h - fy))
            line.color = Colors.alpha(0xFF2F7A3A.toInt(), 0.7f)
            line.strokeWidth = max(1f, gs * 0.25f)
            c.drawLine(gx, gy, gx - gs * 0.3f, gy - gs * 1.2f, line)
            c.drawLine(gx, gy, gx + gs * 0.1f, gy - gs * 1.5f, line)
            c.drawLine(gx, gy, gx + gs * 0.4f, gy - gs * 1.1f, line)
            if (i % 5 == 0) {
                val fc = intArrayOf(0xFFFFFFFF.toInt(), 0xFFFFD84A.toInt(), 0xFFFF8FB8.toInt())[i % 3]
                fill.color = fc
                for (k in 0 until 5) {
                    val a = k * 2f * PI.toFloat() / 5f
                    c.drawCircle(gx + gs * 0.9f + cos(a) * gs * 0.35f, gy - gs * 1.6f + sin(a) * gs * 0.35f, gs * 0.25f, fill)
                }
                fill.color = 0xFFFFC83D.toInt()
                c.drawCircle(gx + gs * 0.9f, gy - gs * 1.6f, gs * 0.2f, fill)
            }
        }
        // mushrooms
        for ((i, mx) in floatArrayOf(0.1f, 0.92f).withIndex()) {
            mushroom(c, mx * w, fy + (h - fy) * (0.3f + i * 0.1f), h * 0.045f)
        }
    }

    private fun mushroom(c: Canvas, x: Float, y: Float, r: Float) {
        ellipseShadow(c, x, y, r * 1.1f, r * 0.25f, 0.3f)
        path.reset()
        path.addRoundRect(x - r * 0.35f, y - r * 0.8f, x + r * 0.35f, y, r * 0.2f, r * 0.2f, Path.Direction.CW)
        shaded(c, path, Color.WHITE, 0xFFFFF1E0.toInt(), 0xFFD8C2A8.toInt(), outlineW = r * 0.04f)
        rect.set(x - r, y - r * 1.45f, x + r, y - r * 0.2f)
        path.reset()
        path.arcTo(rect, 180f, 180f)
        path.close()
        shaded(c, path, 0xFFFF9A9E.toInt(), 0xFFE8505B.toInt(), 0xFFA6222E.toInt(), outlineW = r * 0.04f)
        fill.color = Colors.alpha(Color.WHITE, 0.95f)
        c.drawCircle(x - r * 0.4f, y - r * 0.95f, r * 0.14f, fill)
        c.drawCircle(x + r * 0.35f, y - r * 1.05f, r * 0.11f, fill)
        c.drawCircle(x, y - r * 1.25f, r * 0.09f, fill)
    }

    // ------------------------------------------------------------------ ocean

    private fun staticOcean(c: Canvas, L: SceneLayout) {
        val w = L.w
        val h = L.h
        val fy = L.floorY
        // distant rocks
        path.reset()
        path.moveTo(0f, fy + h * 0.02f)
        path.quadTo(w * 0.1f, fy - h * 0.12f, w * 0.25f, fy - h * 0.04f)
        path.quadTo(w * 0.35f, fy - h * 0.1f, w * 0.42f, fy + h * 0.02f)
        path.close()
        shader(LinearGradient(0f, fy - h * 0.12f, 0f, fy, 0xFF3A6E9E.toInt(), 0xFF2A5580.toInt(), Shader.TileMode.CLAMP))
        c.drawPath(path, fill)
        path.reset()
        path.moveTo(w * 0.62f, fy + h * 0.02f)
        path.quadTo(w * 0.75f, fy - h * 0.16f, w * 0.88f, fy - h * 0.06f)
        path.quadTo(w * 0.95f, fy - h * 0.12f, w, fy - h * 0.05f)
        path.lineTo(w, fy + h * 0.02f)
        path.close()
        c.drawPath(path, fill)
        fill.shader = null
        // sand with ripples
        path.reset()
        path.moveTo(0f, fy)
        var x = 0f
        while (x <= w) {
            path.lineTo(x, fy - sin(x / w * 9f) * h * 0.01f)
            x += w / 24f
        }
        path.lineTo(w, h)
        path.lineTo(0f, h)
        path.close()
        shader(LinearGradient(0f, fy, 0f, h, 0xFFF8E2B0.toInt(), 0xFFDDB679.toInt(), Shader.TileMode.CLAMP))
        c.drawPath(path, fill)
        fill.shader = null
        line.shader = null
        line.color = Colors.alpha(0xFFB88E54.toInt(), 0.35f)
        line.strokeWidth = w * 0.003f
        for (k in 1..6) {
            val y = fy + (h - fy) * (k / 6f).pow(1.5f)
            path.reset()
            path.moveTo(0f, y)
            var xx = 0f
            while (xx < w) {
                xx += w / 10f
                path.quadTo(xx - w / 20f, y - h * 0.006f, xx, y)
            }
            c.drawPath(path, line)
        }
        // corals
        coral(c, w * 0.08f, fy + (h - fy) * 0.18f, h * 0.12f, 0xFFFF7EB6.toInt())
        coral(c, w * 0.93f, fy + (h - fy) * 0.12f, h * 0.1f, 0xFFFF9E4D.toInt())
        coral(c, w * 0.85f, fy + (h - fy) * 0.3f, h * 0.07f, 0xFFB39DFF.toInt())
        // shells & starfish
        Shapes.softStar(path, w * 0.7f, fy + (h - fy) * 0.55f, h * 0.035f, h * 0.016f, 5, 12f)
        shaded(c, path, 0xFFFFC2A0.toInt(), 0xFFFF8A65.toInt(), 0xFFC45A34.toInt(), outlineW = w * 0.002f)
        path.reset()
        path.addOval(w * 0.2f, fy + (h - fy) * 0.5f, w * 0.26f, fy + (h - fy) * 0.5f + h * 0.03f, Path.Direction.CW)
        shaded(c, path, 0xFFFFF0F5.toInt(), 0xFFFFC7DC.toInt(), 0xFFD88AA8.toInt(), outlineW = w * 0.002f)
        // treasure chest
        val cx = w * 0.12f
        val cy = fy + (h - fy) * 0.55f
        val cs = h * 0.05f
        ellipseShadow(c, cx, cy, cs * 1.4f, cs * 0.3f, 0.3f)
        box(c, cx - cs, cy - cs * 0.9f, cx + cs, cy, cs * 0.1f, 0xFFB8743F.toInt(), 0xFF7A4520.toInt())
        rect.set(cx - cs, cy - cs * 1.5f, cx + cs, cy - cs * 0.3f)
        path.reset()
        path.arcTo(rect, 180f, 180f)
        path.close()
        shaded(c, path, 0xFFD89060.toInt(), 0xFFA8622F.toInt(), 0xFF6A3A18.toInt(), outlineW = w * 0.002f)
        box(c, cx - cs * 0.15f, cy - cs, cx + cs * 0.15f, cy - cs * 0.6f, cs * 0.05f, 0xFFFFE08A.toInt(), 0xFFC98C1B.toInt())
        shader(radial(cx, cy - cs * 0.95f, cs * 1.2f, intArrayOf(Colors.alpha(0xFFFFE680.toInt(), 0.5f), Colors.alpha(0xFFFFE680.toInt(), 0f))))
        c.drawCircle(cx, cy - cs * 0.95f, cs * 1.2f, fill)
        fill.shader = null
    }

    private fun coral(c: Canvas, x: Float, y: Float, s: Float, color: Int) {
        line.shader = null
        line.strokeCap = Paint.Cap.ROUND
        val branches = listOf(
            floatArrayOf(0f, 0f, 0f, -1f),
            floatArrayOf(0f, -0.4f, -0.45f, -0.85f),
            floatArrayOf(0f, -0.55f, 0.4f, -0.95f),
            floatArrayOf(-0.45f, -0.85f, -0.55f, -1.1f),
            floatArrayOf(0.4f, -0.95f, 0.5f, -1.2f),
        )
        for ((i, b) in branches.withIndex()) {
            line.strokeWidth = s * (0.2f - i * 0.02f)
            line.color = Colors.darken(color, 0.2f)
            c.drawLine(x + b[0] * s, y + b[1] * s, x + b[2] * s, y + b[3] * s, line)
            line.strokeWidth *= 0.6f
            line.color = Colors.lighten(color, 0.25f)
            c.drawLine(x + b[0] * s - s * 0.02f, y + b[1] * s, x + b[2] * s - s * 0.02f, y + b[3] * s, line)
        }
    }

    // ------------------------------------------------------------------ space

    private fun staticSpace(c: Canvas, L: SceneLayout) {
        val w = L.w
        val h = L.h
        val fy = L.floorY
        // planet with ring
        val px = w * 0.76f
        val py = h * 0.3f
        val pr = min(w, h) * 0.14f
        rect.set(px - pr * 1.9f, py - pr * 0.42f, px + pr * 1.9f, py + pr * 0.42f)
        line.shader = null
        line.color = 0xFFFFD29A.toInt()
        line.strokeWidth = pr * 0.14f
        c.save()
        c.rotate(-16f, px, py)
        c.drawArc(rect, 180f, 180f, false, line)
        c.restore()
        path.reset()
        path.addCircle(px, py, pr, Path.Direction.CW)
        shaded(c, path, 0xFFFFD2B8.toInt(), 0xFFFF8E7A.toInt(), 0xFF8A2E5A.toInt(), outline = false)
        c.save()
        c.clipPath(path)
        fill.color = Colors.alpha(0xFFFFF0E0.toInt(), 0.25f)
        c.drawRect(px - pr, py - pr * 0.35f, px + pr, py - pr * 0.2f, fill)
        c.drawRect(px - pr, py + pr * 0.15f, px + pr, py + pr * 0.32f, fill)
        c.restore()
        c.save()
        c.rotate(-16f, px, py)
        line.color = 0xFFFFE2B8.toInt()
        c.drawArc(rect, 0f, 180f, false, line)
        c.restore()
        // small moon
        path.reset()
        path.addCircle(w * 0.2f, h * 0.18f, min(w, h) * 0.045f, Path.Direction.CW)
        shaded(c, path, 0xFFF4F1FF.toInt(), 0xFFC9C3E8.toInt(), 0xFF6E6894.toInt(), outline = false)
        // moon surface floor
        path.reset()
        path.moveTo(0f, fy + h * 0.02f)
        path.quadTo(w * 0.25f, fy - h * 0.03f, w * 0.5f, fy)
        path.quadTo(w * 0.75f, fy + h * 0.03f, w, fy - h * 0.01f)
        path.lineTo(w, h)
        path.lineTo(0f, h)
        path.close()
        shader(LinearGradient(0f, fy, 0f, h, 0xFFB4B1D2.toInt(), 0xFF6D6A90.toInt(), Shader.TileMode.CLAMP))
        c.drawPath(path, fill)
        fill.shader = null
        for ((cx, cy, r) in listOf(Triple(0.12f, 0.25f, 0.06f), Triple(0.84f, 0.4f, 0.08f), Triple(0.6f, 0.12f, 0.035f), Triple(0.35f, 0.62f, 0.05f), Triple(0.92f, 0.8f, 0.04f))) {
            val x = cx * w
            val y = fy + cy * (h - fy)
            val rx = r * w
            val ry = rx * 0.35f
            fill.color = Colors.alpha(0xFF4E4A70.toInt(), 0.55f)
            c.drawOval(x - rx, y - ry, x + rx, y + ry, fill)
            fill.color = Colors.alpha(Color.WHITE, 0.25f)
            c.drawOval(x - rx, y + ry * 0.2f, x + rx, y + ry * 1.2f, fill)
            fill.color = 0xFF8F8CB0.toInt()
            c.drawOval(x - rx * 0.85f, y - ry * 0.6f, x + rx * 0.85f, y + ry * 0.7f, fill)
        }
        // flag
        val fx = w * 0.08f
        val fb = fy + (h - fy) * 0.1f
        box(c, fx - w * 0.004f, fb - h * 0.12f, fx + w * 0.004f, fb, 0f, 0xFFE8E8F0.toInt(), 0xFF9A98B0.toInt())
        path.reset()
        path.moveTo(fx, fb - h * 0.12f)
        path.lineTo(fx + w * 0.08f, fb - h * 0.1f)
        path.lineTo(fx, fb - h * 0.075f)
        path.close()
        shaded(c, path, 0xFFFFB3D1.toInt(), 0xFFFF5C9A.toInt(), 0xFFC22F6B.toInt(), outline = false)
    }

    // ------------------------------------------------------------------ candy land

    private fun staticCandy(c: Canvas, L: SceneLayout) {
        val w = L.w
        val h = L.h
        val fy = L.floorY
        // candy hills with frosting
        for ((i, col) in listOf(0xFFB9F2DC.toInt(), 0xFFFFC7DC.toInt()).withIndex()) {
            path.reset()
            path.moveTo(0f, fy)
            var x = 0f
            val base = fy - h * (0.12f - i * 0.05f)
            while (x <= w) {
                path.lineTo(x, base - sin(x / w * (5f + i * 2f) + i) * h * 0.04f)
                x += w / 30f
            }
            path.lineTo(w, fy)
            path.close()
            shader(LinearGradient(0f, base - h * 0.05f, 0f, fy, Colors.lighten(col, 0.3f), col, Shader.TileMode.CLAMP))
            c.drawPath(path, fill)
            fill.shader = null
        }
        // lollipop trees
        for ((i, lx) in floatArrayOf(0.1f, 0.9f, 0.3f).withIndex()) {
            val px = lx * w
            val top = fy - h * (0.28f - i * 0.06f)
            val r = h * (0.07f - i * 0.012f)
            box(c, px - w * 0.008f, top, px + w * 0.008f, fy + h * 0.01f, w * 0.004f, Color.WHITE, 0xFFDAD6E8.toInt())
            val col = intArrayOf(0xFFFF6FA5.toInt(), 0xFF7FC8FF.toInt(), 0xFFFFD84A.toInt())[i]
            path.reset()
            path.addCircle(px, top, r, Path.Direction.CW)
            shaded(c, path, Colors.lighten(col, 0.5f), col, Colors.darken(col, 0.3f), outlineW = w * 0.002f)
            Shapes.spiral(path, px, top, r * 0.92f, 2.6f, i.toFloat())
            line.shader = null
            line.color = Colors.alpha(Color.WHITE, 0.85f)
            line.strokeWidth = r * 0.16f
            c.drawPath(path, line)
            fill.color = Colors.alpha(Color.WHITE, 0.6f)
            c.drawOval(px - r * 0.6f, top - r * 0.7f, px - r * 0.2f, top - r * 0.45f, fill)
        }
        // candy cane
        val cx = w * 0.72f
        path.reset()
        path.moveTo(cx, fy + h * 0.01f)
        path.lineTo(cx, fy - h * 0.18f)
        path.quadTo(cx, fy - h * 0.25f, cx + w * 0.05f, fy - h * 0.25f)
        path.quadTo(cx + w * 0.1f, fy - h * 0.25f, cx + w * 0.1f, fy - h * 0.19f)
        line.color = Color.WHITE
        line.strokeWidth = w * 0.03f
        c.drawPath(path, line)
        line.color = 0xFFE8505B.toInt()
        line.pathEffect = android.graphics.DashPathEffect(floatArrayOf(w * 0.025f, w * 0.025f), 0f)
        c.drawPath(path, line)
        line.pathEffect = null
        // gumdrops
        for ((i, gx) in floatArrayOf(0.2f, 0.52f, 0.62f).withIndex()) {
            val col = intArrayOf(0xFF7DD9B0.toInt(), 0xFFB39DFF.toInt(), 0xFFFF9E4D.toInt())[i]
            val x = gx * w
            val y = fy + h * 0.005f
            rect.set(x - h * 0.03f, y - h * 0.05f, x + h * 0.03f, y + h * 0.015f)
            path.reset()
            path.arcTo(rect, 180f, 180f)
            path.close()
            shaded(c, path, Colors.lighten(col, 0.5f), col, Colors.darken(col, 0.3f), outlineW = w * 0.002f)
        }
        // glossy checker floor in perspective
        vgrad(c, 0f, fy, w, h, 0xFFFFF2F8.toInt(), 0xFFFFE0EE.toInt())
        c.save()
        c.clipRect(0f, fy, w, h)
        val rows = 7
        val cols = 12
        for (rI in 0 until rows) {
            val y0 = fy + (h - fy) * (rI / rows.toFloat()).pow(1.5f)
            val y1 = fy + (h - fy) * ((rI + 1) / rows.toFloat()).pow(1.5f)
            for (k in -cols..cols) {
                if ((rI + k) % 2 != 0) continue
                val bx0 = L.vpX + k * w * 0.12f
                val bx1 = L.vpX + (k + 1) * w * 0.12f
                fun px(bx: Float, y: Float) = L.vpX + (bx - L.vpX) * ((y - L.vpY) / (h - L.vpY))
                path.reset()
                path.moveTo(px(bx0, y0), y0)
                path.lineTo(px(bx1, y0), y0)
                path.lineTo(px(bx1, y1), y1)
                path.lineTo(px(bx0, y1), y1)
                path.close()
                fill.color = 0xFFFF9EC8.toInt()
                c.drawPath(path, fill)
            }
        }
        shader(LinearGradient(0f, fy, 0f, h, Colors.alpha(Color.WHITE, 0.3f), Colors.alpha(Color.WHITE, 0f), Shader.TileMode.CLAMP))
        c.drawRect(0f, fy, w, h, fill)
        fill.shader = null
        c.restore()
        // sprinkles
        val sprinkle = intArrayOf(0xFFFFD166.toInt(), 0xFF6EC1FF.toInt(), 0xFF7DD9B0.toInt(), 0xFFB39DFF.toInt())
        for (i in 0 until 20) {
            val sx = starField[80 + i] * w
            val sy = fy + starField[100 + (i % 20)] * (h - fy)
            fill.color = sprinkle[i % sprinkle.size]
            c.save()
            c.rotate(i * 47f, sx, sy)
            c.drawRoundRect(sx - h * 0.01f, sy - h * 0.003f, sx + h * 0.01f, sy + h * 0.003f, h * 0.003f, h * 0.003f, fill)
            c.restore()
        }
    }

    companion object {
        private val HOURS = floatArrayOf(0f, 5f, 6.5f, 8f, 17f, 19f, 20.5f, 22f, 24f)
        private val SKY_TOP = intArrayOf(
            0xFF0B1030.toInt(), 0xFF1B1F4F.toInt(), 0xFF6A5ACD.toInt(), 0xFF5DB8FF.toInt(), 0xFF5DB8FF.toInt(),
            0xFF7B6CC4.toInt(), 0xFF3A2F7A.toInt(), 0xFF0B1030.toInt(), 0xFF0B1030.toInt(),
        )
        private val SKY_BOTTOM = intArrayOf(
            0xFF2A2F6B.toInt(), 0xFF4A3F7F.toInt(), 0xFFFFB199.toInt(), 0xFFCFEEFF.toInt(), 0xFFCFEEFF.toInt(),
            0xFFFFA07A.toInt(), 0xFFE0708A.toInt(), 0xFF2A2F6B.toInt(), 0xFF2A2F6B.toInt(),
        )
        private val DARK = floatArrayOf(1f, 1f, 0.45f, 0f, 0f, 0.25f, 0.7f, 1f, 1f)

        fun weatherFor(epochDay: Long, month: Int): Weather {
            val v = ((epochDay * 2654435761L) ushr 8) % 100
            return when {
                (month == 12 || month <= 2) && v < 30 -> Weather.SNOW
                v < 18 -> Weather.RAIN
                v < 45 -> Weather.CLOUDY
                else -> Weather.CLEAR
            }
        }
    }
}
