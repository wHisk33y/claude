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
import android.graphics.Typeface
import kotlin.math.max
import kotlin.math.sin

/** Shared soft-3D painting helpers. */
open class Painter {
    protected val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    protected val line = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    protected val path = Path()
    protected val path2 = Path()
    protected val rect = RectF()
    protected val rect2 = RectF()

    protected fun shader(s: Shader) {
        fill.color = Color.WHITE
        fill.shader = s
    }

    protected fun radial(x: Float, y: Float, r: Float, colors: IntArray, stops: FloatArray? = null) =
        RadialGradient(x, y, max(r, 0.5f), colors, stops, Shader.TileMode.CLAMP)

    protected fun vgrad(c: Canvas, l: Float, t: Float, r: Float, b: Float, top: Int, bottom: Int) {
        shader(LinearGradient(0f, t, 0f, b, top, bottom, Shader.TileMode.CLAMP))
        c.drawRect(l, t, r, b, fill)
        fill.shader = null
    }

    protected fun shaded(c: Canvas, p: Path, light: Int, base: Int, dark: Int, outline: Boolean = true, outlineW: Float = 2f) {
        p.computeBounds(rect2, false)
        val w = rect2.width()
        val h = rect2.height()
        shader(radial(rect2.left + w * 0.32f, rect2.top + h * 0.25f, max(w, h) * 0.95f, intArrayOf(light, base, dark), floatArrayOf(0f, 0.5f, 1f)))
        c.drawPath(p, fill)
        fill.shader = null
        if (outline) {
            line.shader = null
            line.color = Colors.alpha(Colors.darken(dark, 0.3f), 0.4f)
            line.strokeWidth = outlineW
            c.drawPath(p, line)
        }
    }

    /** A vertical-gradient rounded box with a soft drop shadow and a top highlight. */
    protected fun box(c: Canvas, l: Float, t: Float, r: Float, b: Float, rad: Float, top: Int, bottom: Int, shadow: Float = 0f) {
        if (shadow > 0f) {
            fill.shader = null
            fill.color = Colors.alpha(0xFF2A1740.toInt(), 0.18f)
            c.drawRoundRect(l + shadow * 0.3f, t + shadow, r + shadow * 0.3f, b + shadow, rad, rad, fill)
        }
        shader(LinearGradient(0f, t, 0f, b, top, bottom, Shader.TileMode.CLAMP))
        c.drawRoundRect(l, t, r, b, rad, rad, fill)
        fill.shader = null
        line.shader = null
        line.color = Colors.alpha(Color.WHITE, 0.45f)
        line.strokeWidth = max(1f, (b - t) * 0.04f).coerceAtMost(4f)
        c.drawLine(l + rad * 0.6f, t + line.strokeWidth, r - rad * 0.6f, t + line.strokeWidth, line)
    }

    protected fun ellipseShadow(c: Canvas, x: Float, y: Float, rx: Float, ry: Float, a: Float = 0.3f) {
        c.save()
        c.scale(1f, ry / max(rx, 0.5f), x, y)
        shader(radial(x, y, rx, intArrayOf(Colors.alpha(0xFF2A1740.toInt(), a), Colors.alpha(0xFF2A1740.toInt(), a * 0.45f), Colors.alpha(0xFF2A1740.toInt(), 0f)), floatArrayOf(0f, 0.55f, 1f)))
        c.drawCircle(x, y, rx, fill)
        fill.shader = null
        c.restore()
    }
}

class PropRenderer : Painter() {
    private val emoji = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER }
    private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        typeface = Typeface.DEFAULT_BOLD
    }
    private val clear = Paint(Paint.ANTI_ALIAS_FLAG).apply { xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR) }

    var typeface: Typeface
        get() = text.typeface
        set(value) {
            text.typeface = value
        }

    // ------------------------------------------------------------------ bowl & food

    fun bowlBack(c: Canvas, x: Float, groundY: Float, s: Float) {
        val w = s * 0.46f
        val depth = s * 0.14f
        val rimY = groundY - depth
        val rh = s * 0.075f
        ellipseShadow(c, x, groundY, w * 0.7f, s * 0.05f, 0.35f)
        path.reset()
        path.addOval(x - w / 2f, rimY - rh, x + w / 2f, rimY + rh, Path.Direction.CW)
        shaded(c, path, 0xFFFFE3EE.toInt(), 0xFFFF9EC4.toInt(), 0xFFD9588E.toInt(), outlineW = s * 0.008f)
        path.reset()
        path.addOval(x - w * 0.42f, rimY - rh * 0.72f, x + w * 0.42f, rimY + rh * 0.72f, Path.Direction.CW)
        shader(LinearGradient(0f, rimY - rh, 0f, rimY + rh, 0xFF8A2F5C.toInt(), 0xFFD9588E.toInt(), Shader.TileMode.CLAMP))
        c.drawPath(path, fill)
        fill.shader = null
    }

    fun food(c: Canvas, x: Float, groundY: Float, s: Float, item: String, bites: Int, pop: Float) {
        val depth = s * 0.14f
        val size = s * 0.3f * DirectorEase.back(pop.coerceIn(0f, 1f))
        if (size < 1f) return
        val y = groundY - depth - s * 0.02f
        c.saveLayer(x - size, y - size * 1.2f, x + size, y + size * 0.4f, null)
        emoji.textSize = size
        c.drawText(item, x, y, emoji)
        val bitePos = floatArrayOf(0.34f, -0.78f, -0.3f, -0.85f, 0.42f, -0.35f, -0.4f, -0.4f)
        for (i in 0 until bites.coerceAtMost(4)) {
            c.drawCircle(x + bitePos[i * 2] * size, y + bitePos[i * 2 + 1] * size, size * 0.24f, clear)
        }
        c.restore()
    }

    fun bowlFront(c: Canvas, x: Float, groundY: Float, s: Float) {
        val w = s * 0.46f
        val depth = s * 0.14f
        val rimY = groundY - depth
        val rh = s * 0.075f
        path.reset()
        path.moveTo(x - w / 2f, rimY)
        path.cubicTo(x - w / 2f, rimY + depth * 0.7f, x - w * 0.34f, groundY, x - w * 0.24f, groundY)
        path.lineTo(x + w * 0.24f, groundY)
        path.cubicTo(x + w * 0.34f, groundY, x + w / 2f, rimY + depth * 0.7f, x + w / 2f, rimY)
        rect.set(x - w / 2f, rimY - rh, x + w / 2f, rimY + rh)
        path.arcTo(rect, 0f, 180f)
        path.close()
        shaded(c, path, 0xFFFFE8F1.toInt(), 0xFFFF8DB9.toInt(), 0xFFC7457C.toInt(), outlineW = s * 0.008f)
        // paw print
        fill.color = Colors.alpha(Color.WHITE, 0.75f)
        val py = rimY + depth * 0.62f
        c.drawOval(x - s * 0.035f, py - s * 0.02f, x + s * 0.035f, py + s * 0.03f, fill)
        for (i in -1..1) c.drawCircle(x + i * s * 0.03f, py - s * 0.04f - (if (i == 0) s * 0.008f else 0f), s * 0.012f, fill)
        line.color = Colors.alpha(Color.WHITE, 0.7f)
        line.strokeWidth = s * 0.01f
        rect.set(x - w * 0.46f, rimY - rh * 0.8f, x + w * 0.46f, rimY + rh * 0.95f)
        c.drawArc(rect, 20f, 140f, false, line)
    }

    // ------------------------------------------------------------------ bed / nest

    fun bedBack(c: Canvas, x: Float, groundY: Float, s: Float, cozy: Boolean) {
        val w = s * 1.36f
        val l = x - w / 2f
        val r = x + w / 2f
        ellipseShadow(c, x, groundY, w * 0.62f, s * 0.07f, 0.3f)
        if (cozy) {
            // headboard with heart
            box(c, l - s * 0.05f, groundY - s * 0.7f, l + s * 0.09f, groundY, s * 0.05f, 0xFFE9B384.toInt(), 0xFFB9774A.toInt())
            fill.color = 0xFFB9774A.toInt()
            c.drawCircle(l + s * 0.02f, groundY - s * 0.7f, s * 0.075f, fill)
            shader(radial(l, groundY - s * 0.72f, s * 0.08f, intArrayOf(0xFFF5C898.toInt(), 0xFFB9774A.toInt())))
            c.drawCircle(l + s * 0.02f, groundY - s * 0.72f, s * 0.07f, fill)
            fill.shader = null
            // frame & legs
            box(c, l, groundY - s * 0.19f, r, groundY - s * 0.06f, s * 0.03f, 0xFFE0A574.toInt(), 0xFFA96A3F.toInt())
            box(c, l + s * 0.02f, groundY - s * 0.07f, l + s * 0.08f, groundY, s * 0.015f, 0xFFB9774A.toInt(), 0xFF8A5530.toInt())
            box(c, r - s * 0.08f, groundY - s * 0.07f, r - s * 0.02f, groundY, s * 0.015f, 0xFFB9774A.toInt(), 0xFF8A5530.toInt())
            // mattress
            box(c, l + s * 0.02f, groundY - s * 0.3f, r - s * 0.02f, groundY - s * 0.16f, s * 0.06f, 0xFFFFFFFF.toInt(), 0xFFE3E4F4.toInt())
            // pillow
            path.reset()
            path.addRoundRect(l + s * 0.06f, groundY - s * 0.42f, l + s * 0.42f, groundY - s * 0.27f, s * 0.07f, s * 0.07f, Path.Direction.CW)
            shaded(c, path, Color.WHITE, 0xFFF4F1FF.toInt(), 0xFFC9C3E8.toInt(), outlineW = s * 0.006f)
        } else {
            // round nest cushion
            path.reset()
            path.addOval(x - w * 0.46f, groundY - s * 0.2f, x + w * 0.46f, groundY + s * 0.02f, Path.Direction.CW)
            shaded(c, path, 0xFFFFE1C2.toInt(), 0xFFE9A86F.toInt(), 0xFFA8683A.toInt(), outlineW = s * 0.008f)
            path.reset()
            path.addOval(x - w * 0.36f, groundY - s * 0.17f, x + w * 0.36f, groundY - s * 0.04f, Path.Direction.CW)
            shader(LinearGradient(0f, groundY - s * 0.17f, 0f, groundY - s * 0.04f, 0xFFB7794A.toInt(), 0xFFF0C9A2.toInt(), Shader.TileMode.CLAMP))
            c.drawPath(path, fill)
            fill.shader = null
        }
    }

    fun bedFront(c: Canvas, x: Float, groundY: Float, s: Float, blanket: Float, cozy: Boolean, t: Float) {
        val w = s * 1.36f
        val l = x - w / 2f
        val r = x + w / 2f
        if (blanket > 0.01f) {
            val top = groundY - s * 0.3f - s * 0.13f
            val left = r - (r - (x - s * 0.36f)) * blanket
            path.reset()
            path.moveTo(left, top + s * 0.04f)
            path.quadTo(left + (r - left) * 0.25f, top - s * 0.03f + sin(t * 1.6f) * s * 0.01f, left + (r - left) * 0.5f, top + s * 0.02f)
            path.quadTo(left + (r - left) * 0.75f, top + s * 0.06f, r + s * 0.02f, top + s * 0.02f)
            path.lineTo(r + s * 0.02f, groundY - s * 0.12f)
            path.quadTo(left + (r - left) * 0.5f, groundY - s * 0.06f, left - s * 0.02f, groundY - s * 0.12f)
            path.close()
            shaded(c, path, 0xFFB8C8FF.toInt(), 0xFF8C9CF4.toInt(), 0xFF5463C7.toInt(), outlineW = s * 0.008f)
            c.save()
            c.clipPath(path)
            fill.color = Colors.alpha(Color.WHITE, 0.55f)
            var sx = left + s * 0.06f
            var row = 0
            while (sx < r) {
                var sy = top + s * 0.08f + (row % 2) * s * 0.05f
                while (sy < groundY - s * 0.1f) {
                    Shapes.star(path2, sx, sy, s * 0.028f, s * 0.013f)
                    c.drawPath(path2, fill)
                    sy += s * 0.1f
                }
                sx += s * 0.09f
                row++
            }
            // fold
            line.color = Colors.alpha(Color.WHITE, 0.5f)
            line.strokeWidth = s * 0.012f
            c.drawLine(left + s * 0.02f, top + s * 0.07f, r, top + s * 0.07f, line)
            c.restore()
        }
        if (cozy) {
            box(c, r - s * 0.08f, groundY - s * 0.42f, r + s * 0.05f, groundY, s * 0.04f, 0xFFE9B384.toInt(), 0xFFB9774A.toInt())
        }
    }

    // ------------------------------------------------------------------ bath tub

    fun tubBack(c: Canvas, x: Float, groundY: Float, s: Float, rise: Float, t: Float) {
        if (rise <= 0.01f) return
        c.save()
        c.scale(rise, rise, x, groundY)
        val w = s * 1.3f
        val top = groundY - s * 0.46f
        val rh = s * 0.1f
        ellipseShadow(c, x, groundY, w * 0.62f, s * 0.07f, 0.35f)
        path.reset()
        path.addOval(x - w / 2f, top - rh, x + w / 2f, top + rh, Path.Direction.CW)
        shaded(c, path, Color.WHITE, 0xFFF4F6FB.toInt(), 0xFFC3C9D8.toInt(), outlineW = s * 0.008f)
        path.reset()
        path.addOval(x - w * 0.44f, top - rh * 0.7f, x + w * 0.44f, top + rh * 0.7f, Path.Direction.CW)
        shader(LinearGradient(0f, top - rh, 0f, top + rh, 0xFF5FB7F2.toInt(), 0xFFB9E6FF.toInt(), Shader.TileMode.CLAMP))
        c.drawPath(path, fill)
        fill.shader = null
        // ripple
        line.color = Colors.alpha(Color.WHITE, 0.6f)
        line.strokeWidth = s * 0.008f
        rect.set(x - w * 0.3f + sin(t * 2f) * s * 0.02f, top - rh * 0.35f, x + w * 0.1f, top + rh * 0.25f)
        c.drawArc(rect, 200f, 120f, false, line)
        duck(c, x + w * 0.3f, top + sin(t * 2.4f) * s * 0.012f, s * 0.2f, t)
        c.restore()
    }

    fun tubFront(c: Canvas, x: Float, groundY: Float, s: Float, rise: Float, foam: Float, t: Float) {
        if (rise <= 0.01f) return
        c.save()
        c.scale(rise, rise, x, groundY)
        val w = s * 1.3f
        val top = groundY - s * 0.46f
        val rh = s * 0.1f
        // clawfeet
        for (sgn in intArrayOf(-1, 1)) {
            path.reset()
            path.addOval(x + sgn * w * 0.36f - s * 0.06f, groundY - s * 0.08f, x + sgn * w * 0.36f + s * 0.06f, groundY + s * 0.01f, Path.Direction.CW)
            shaded(c, path, 0xFFFFF1B8.toInt(), 0xFFFFC83D.toInt(), 0xFFB57B00.toInt(), outlineW = s * 0.006f)
        }
        path.reset()
        path.moveTo(x - w / 2f, top)
        path.cubicTo(x - w / 2f, top + s * 0.3f, x - w * 0.38f, groundY - s * 0.05f, x - w * 0.26f, groundY - s * 0.05f)
        path.lineTo(x + w * 0.26f, groundY - s * 0.05f)
        path.cubicTo(x + w * 0.38f, groundY - s * 0.05f, x + w / 2f, top + s * 0.3f, x + w / 2f, top)
        rect.set(x - w / 2f, top - rh, x + w / 2f, top + rh)
        path.arcTo(rect, 0f, 180f)
        path.close()
        shaded(c, path, Color.WHITE, 0xFFF7F8FC.toInt(), 0xFFB9C0D2.toInt(), outlineW = s * 0.008f)
        // gold rim & shine
        line.color = 0xFFFFC83D.toInt()
        line.strokeWidth = s * 0.018f
        c.drawArc(rect, 5f, 170f, false, line)
        fill.color = Colors.alpha(Color.WHITE, 0.8f)
        c.drawRoundRect(x - w * 0.36f, top + s * 0.1f, x - w * 0.3f, top + s * 0.32f, s * 0.03f, s * 0.03f, fill)
        // foam along the rim
        if (foam > 0.01f) {
            val n = 9
            for (i in 0 until n) {
                val fx = x - w * 0.4f + i * (w * 0.8f / (n - 1))
                val fy = top + rh * 0.3f - (i % 2) * s * 0.03f + sin(t * 3f + i) * s * 0.008f
                val r = s * (0.055f + (i % 3) * 0.015f) * foam
                shader(radial(fx - r * 0.3f, fy - r * 0.35f, r * 1.3f, intArrayOf(Color.WHITE, 0xFFF6FBFF.toInt(), 0xFFC6E3F7.toInt()), floatArrayOf(0f, 0.6f, 1f)))
                c.drawCircle(fx, fy, r, fill)
                fill.shader = null
            }
        }
        c.restore()
    }

    fun duck(c: Canvas, x: Float, y: Float, s: Float, t: Float) {
        c.save()
        c.rotate(sin(t * 2f) * 6f, x, y)
        path.reset()
        path.addOval(x - s * 0.5f, y - s * 0.28f, x + s * 0.45f, y + s * 0.12f, Path.Direction.CW)
        shaded(c, path, 0xFFFFF6B0.toInt(), 0xFFFFD43B.toInt(), 0xFFD59A00.toInt(), outlineW = s * 0.03f)
        path.reset()
        path.addCircle(x + s * 0.18f, y - s * 0.42f, s * 0.22f, Path.Direction.CW)
        shaded(c, path, 0xFFFFF6B0.toInt(), 0xFFFFD43B.toInt(), 0xFFD59A00.toInt(), outlineW = s * 0.03f)
        path.reset()
        path.addOval(x + s * 0.34f, y - s * 0.42f, x + s * 0.58f, y - s * 0.3f, Path.Direction.CW)
        shaded(c, path, 0xFFFFC08A.toInt(), 0xFFFF8A3D.toInt(), 0xFFC45A10.toInt(), outline = false)
        fill.color = 0xFF2A1F3D.toInt()
        c.drawCircle(x + s * 0.24f, y - s * 0.47f, s * 0.045f, fill)
        fill.color = Color.WHITE
        c.drawCircle(x + s * 0.255f, y - s * 0.485f, s * 0.015f, fill)
        c.restore()
    }

    // ------------------------------------------------------------------ ball, pill

    fun ball(c: Canvas, x: Float, groundY: Float, height: Float, r: Float, rot: Float, alpha: Float) {
        if (alpha <= 0.01f) return
        val y = groundY - r - height
        val shadowA = (0.35f * alpha / (1f + height / (r * 3f))).coerceIn(0f, 0.35f)
        ellipseShadow(c, x, groundY, r * (1.1f - (height / (r * 12f)).coerceAtMost(0.5f)), r * 0.25f, shadowA)
        c.saveLayerAlpha(x - r * 1.1f, y - r * 1.1f, x + r * 1.1f, y + r * 1.1f, (alpha * 255).toInt())
        val colors = intArrayOf(0xFFFF5E6C.toInt(), 0xFFFFFFFF.toInt(), 0xFF4DA8FF.toInt(), 0xFFFFFFFF.toInt(), 0xFFFFD43B.toInt(), 0xFFFFFFFF.toInt())
        rect.set(x - r, y - r, x + r, y + r)
        fill.shader = null
        for (i in 0 until 6) {
            fill.color = colors[i]
            c.drawArc(rect, rot + i * 60f, 60f, true, fill)
        }
        shader(radial(x - r * 0.35f, y - r * 0.4f, r * 1.6f, intArrayOf(Colors.alpha(Color.WHITE, 0.35f), Colors.alpha(Color.WHITE, 0f), Colors.alpha(0xFF1A1030.toInt(), 0.35f)), floatArrayOf(0f, 0.5f, 1f)))
        c.drawCircle(x, y, r, fill)
        fill.shader = null
        fill.color = Colors.alpha(Color.WHITE, 0.85f)
        c.drawOval(x - r * 0.55f, y - r * 0.62f, x - r * 0.15f, y - r * 0.38f, fill)
        c.restore()
    }

    fun floatingItem(c: Canvas, x: Float, y: Float, s: Float, item: String, t: Float) {
        shader(radial(x, y, s * 0.9f, intArrayOf(Colors.alpha(0xFFFFF3B0.toInt(), 0.7f), Colors.alpha(0xFFFFF3B0.toInt(), 0f))))
        c.drawCircle(x, y, s * 0.9f, fill)
        fill.shader = null
        emoji.textSize = s
        c.save()
        c.rotate(sin(t * 6f) * 12f, x, y)
        c.drawText(item, x, y + s * 0.35f, emoji)
        c.restore()
    }

    // ------------------------------------------------------------------ poop

    fun poop(c: Canvas, x: Float, bottom: Float, r: Float, t: Float) {
        val wob = sin(t * 2f) * r * 0.04f
        ellipseShadow(c, x, bottom, r * 1.3f, r * 0.25f, 0.3f)
        val light = 0xFFC9926A.toInt()
        val base = 0xFF8D5A3B.toInt()
        val dark = 0xFF5A3522.toInt()
        path.reset()
        path.addOval(x - r * 1.1f, bottom - r * 0.72f, x + r * 1.1f, bottom, Path.Direction.CW)
        shaded(c, path, light, base, dark, outlineW = r * 0.05f)
        path.reset()
        path.addOval(x - r * 0.8f + wob, bottom - r * 1.28f, x + r * 0.8f + wob, bottom - r * 0.45f, Path.Direction.CW)
        shaded(c, path, light, base, dark, outlineW = r * 0.05f)
        path.reset()
        path.moveTo(x - r * 0.45f + wob * 2f, bottom - r * 1.08f)
        path.quadTo(x - r * 0.3f, bottom - r * 1.95f, x + r * 0.2f + wob * 2f, bottom - r * 2.05f)
        path.quadTo(x + r * 0.05f, bottom - r * 1.7f, x + r * 0.45f + wob * 2f, bottom - r * 1.08f)
        path.close()
        shaded(c, path, light, base, dark, outlineW = r * 0.05f)
        for (sgn in intArrayOf(-1, 1)) {
            fill.color = Color.WHITE
            c.drawCircle(x + sgn * r * 0.3f, bottom - r * 0.86f, r * 0.2f, fill)
            fill.color = 0xFF2E2440.toInt()
            c.drawCircle(x + sgn * r * 0.3f + r * 0.04f, bottom - r * 0.84f, r * 0.1f, fill)
            fill.color = Color.WHITE
            c.drawCircle(x + sgn * r * 0.3f + r * 0.07f, bottom - r * 0.88f, r * 0.035f, fill)
        }
    }

    fun stink(c: Canvas, x: Float, bottom: Float, r: Float, t: Float, seed: Int) {
        line.shader = null
        line.color = Colors.alpha(0xFF9FB58A.toInt(), 0.7f)
        line.strokeWidth = max(1f, r * 0.12f)
        for (k in -1..1 step 2) {
            path.reset()
            val phase = (t * 0.8f + seed * 0.3f + k * 0.25f) % 1f
            val x0 = x + k * r * 0.5f
            val y0 = bottom - r * 2.1f - phase * r * 0.8f
            path.moveTo(x0, y0)
            path.quadTo(x0 + r * 0.25f, y0 - r * 0.35f, x0, y0 - r * 0.7f)
            path.quadTo(x0 - r * 0.25f, y0 - r * 1.05f, x0, y0 - r * 1.4f)
            line.alpha = ((1f - phase) * 180).toInt()
            c.drawPath(path, line)
        }
        val fa = t * 5f + seed
        fill.color = 0xFF2B2B2B.toInt()
        val fx = x + kotlin.math.cos(fa) * r * 1.4f
        val fy = bottom - r * 2.4f + sin(fa * 1.7f) * r * 0.5f
        c.drawCircle(fx, fy, max(1f, r * 0.12f), fill)
        fill.color = Colors.alpha(Color.WHITE, 0.7f)
        c.drawOval(fx - r * 0.2f, fy - r * 0.18f, fx, fy - r * 0.05f, fill)
    }

    // ------------------------------------------------------------------ speech bubble

    fun speech(c: Canvas, msg: String, ax: Float, ay: Float, sceneW: Float, size: Float, alpha: Float) {
        if (alpha < 0.02f) return
        text.textSize = size
        val tw = text.measureText(msg)
        val padX = size * 0.8f
        val padY = size * 0.55f
        val bw = tw + padX * 2f
        val bh = size + padY * 2f
        val tail = size * 0.6f
        val cx = ax.coerceIn(bw / 2f + size * 0.4f, sceneW - bw / 2f - size * 0.4f)
        val bottom = ay - tail
        val top = bottom - bh
        val scale = 0.75f + 0.25f * alpha
        c.save()
        c.scale(scale, scale, ax, ay)
        val a = alpha.coerceIn(0f, 1f)
        fill.shader = null
        fill.color = Colors.alpha(0xFF2A1740.toInt(), 0.18f * a)
        c.drawRoundRect(cx - bw / 2f + size * 0.12f, top + size * 0.18f, cx + bw / 2f + size * 0.12f, bottom + size * 0.18f, bh / 2f, bh / 2f, fill)
        path.reset()
        path.addRoundRect(cx - bw / 2f, top, cx + bw / 2f, bottom, bh / 2f, bh / 2f, Path.Direction.CW)
        path2.reset()
        val tx = ax.coerceIn(cx - bw / 2f + bh / 2f, cx + bw / 2f - bh / 2f)
        path2.moveTo(tx - tail * 0.6f, bottom - 2f)
        path2.quadTo(tx - tail * 0.1f, bottom + tail * 0.5f, ax, ay)
        path2.quadTo(tx + tail * 0.2f, bottom + tail * 0.3f, tx + tail * 0.6f, bottom - 2f)
        path2.close()
        path.op(path2, Path.Op.UNION)
        shader(LinearGradient(0f, top, 0f, bottom, Colors.alpha(Color.WHITE, a), Colors.alpha(0xFFF3EEFF.toInt(), a), Shader.TileMode.CLAMP))
        c.drawPath(path, fill)
        fill.shader = null
        line.color = Colors.alpha(0xFFD9CCEF.toInt(), a)
        line.strokeWidth = size * 0.08f
        c.drawPath(path, line)
        text.color = Colors.alpha(0xFF3A2A45.toInt(), a)
        c.drawText(msg, cx, bottom - padY - size * 0.18f, text)
        c.restore()
    }
}

/** Tiny easing helpers shared by renderers. */
object DirectorEase {
    fun back(p: Float): Float = PetDirector.easeOutBack(p)
}
