package de.knuffi.app.render

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.PorterDuffXfermode
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import de.knuffi.core.Form
import de.knuffi.core.Mood
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.sin

enum class Expression { AUTO, EXCITED, LOVE, EATING, REFUSE, SURPRISED }

/** Animation values for a single rendered frame of the pet. */
class PetFrame {
    var t = 0f
    var breath = 0f
    var hop = 0f
    var squish = 0f
    var blink = 0f
    var lookX = 0f
    var lookY = 0f
    var mouthOpen = 0f
    var expression = Expression.AUTO
    var tilt = 0f
    var facing = 1f
    var wingFlap = 0f
    var eggWobble = 0f

    fun reset(): PetFrame {
        t = 0f; breath = 0f; hop = 0f; squish = 0f; blink = 0f; lookX = 0f; lookY = 0f
        mouthOpen = 0f; expression = Expression.AUTO; tilt = 0f; facing = 1f; wingFlap = 0f; eggWobble = 0f
        return this
    }
}

private enum class EyeKind { OPEN, CLOSED_SMILE, CLOSED_LINE, HAPPY_ARC, HEART, HALF, GLOW }
private enum class MouthKind { CAT, BIG_SMILE, FROWN, CHOMP, SMALL_O, FLAT, WAVY }

/**
 * Draws the pet with the plain Android canvas so the exact same art can be used in
 * Compose, in the home screen widget and in notification icons.
 */
class PetRenderer {
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val line = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val path = Path()
    private val path2 = Path()
    private val path3 = Path()
    private val rect = RectF()
    private val layerPaint = Paint()
    private val tintPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val srcAtop = PorterDuffXfermode(PorterDuff.Mode.SRC_ATOP)

    private var mode = RenderMode.SMOOTH
    private var outlineW = 2f
    private var u = 1f

    /** Bounds of the most recently drawn pet in canvas coordinates. */
    val bounds = RectF()

    fun drawShadow(c: Canvas, cx: Float, groundY: Float, size: Float, f: PetFrame, look: PetLook?, mode: RenderMode) {
        val u = size / 2f
        val ghost = look?.formLook?.ghost == true
        val shrink = 1f - (f.hop * 0.35f) - if (ghost) 0.25f else 0f
        val w = u * 0.62f * (look?.formLook?.width ?: 0.8f) * shrink
        fill.shader = null
        fill.isAntiAlias = mode != RenderMode.PIXEL
        fill.color = Color.argb(if (mode == RenderMode.FLAT) 34 else 46, 40, 20, 60)
        c.drawOval(cx - w, groundY - u * 0.07f, cx + w, groundY + u * 0.07f, fill)
    }

    fun draw(
        c: Canvas,
        cx: Float,
        groundY: Float,
        size: Float,
        look: PetLook,
        f: PetFrame,
        mode: RenderMode,
        silhouette: Int? = null,
    ) {
        this.mode = mode
        val aa = mode != RenderMode.PIXEL
        fill.isAntiAlias = aa
        line.isAntiAlias = aa
        tintPaint.isAntiAlias = aa
        u = size / 2f
        outlineW = when (mode) {
            RenderMode.SMOOTH -> u * 0.035f
            RenderMode.FLAT -> u * 0.04f
            RenderMode.PIXEL -> 0f
        }

        if (look.form == Form.EGG) {
            drawEgg(c, cx, groundY, look, f, silhouette)
            return
        }

        val fl = look.formLook
        var sx = 1f - 0.015f * f.breath + 0.16f * f.squish
        var sy = 1f + 0.03f * f.breath - 0.16f * f.squish
        if (look.sleeping) {
            sx *= 1.06f
            sy *= 0.9f
        } else if (look.mood == Mood.SAD) {
            sy *= 0.96f
        }
        val bw = u * 0.72f * fl.width * sx
        val bh = u * 0.62f * fl.height * sy
        val lift = f.hop * u * 0.55f + if (fl.ghost) u * 0.16f + sin(f.t * 2f) * u * 0.05f else 0f
        val baseY = groundY - lift

        bounds.set(cx - bw * 1.25f, baseY - bh * 2.5f, cx + bw * 1.25f, baseY + u * 0.05f)

        val useLayer = silhouette != null || look.sick
        val saveCount = if (useLayer) {
            layerPaint.colorFilter = silhouette?.let { PorterDuffColorFilter(it, PorterDuff.Mode.SRC_IN) }
            c.saveLayer(null, layerPaint)
        } else {
            c.save()
        }
        c.translate(cx, baseY)
        c.rotate(f.tilt)
        c.scale(f.facing, 1f)

        if (look.level >= 25 && silhouette == null) drawAura(c, bw, bh, f.t, fl.colors.accent)
        drawBack(c, fl, look, f, bw, bh)
        drawBody(c, fl, bw, bh, f.t)
        drawFront(c, fl, look, bw, bh)
        drawFace(c, fl, look, f, bw, bh)
        drawAccessories(c, look, f, bw, bh)
        if (fl.sparkles && silhouette == null) drawSparkles(c, bw, bh, f.t)

        if (look.sick && silhouette == null) {
            tintPaint.shader = null
            tintPaint.color = Colors.alpha(0xFF7CC47F.toInt(), 0.3f)
            tintPaint.xfermode = srcAtop
            c.drawRect(-bw * 3f, -bh * 4f, bw * 3f, bh, tintPaint)
            tintPaint.xfermode = null
            val bob = sin(f.t * 3f) * u * 0.03f
            Shapes.drop(path, bw * 0.78f, -bh * 1.72f + bob, u * 0.06f)
            shape(c, path, 0xFF8FD3FF.toInt())
        }
        c.restoreToCount(saveCount)
    }

    // ------------------------------------------------------------------ helpers

    private fun shape(c: Canvas, p: Path, color: Int, outline: Boolean = true) {
        fill.shader = null
        fill.color = color
        c.drawPath(p, fill)
        if (outline) outline(c, p, color)
    }

    private fun outline(c: Canvas, p: Path, base: Int) {
        if (mode == RenderMode.PIXEL || outlineW <= 0f) return
        line.shader = null
        line.color = if (mode == RenderMode.FLAT) FLAT_OUTLINE else Colors.darken(base, 0.3f)
        line.strokeWidth = outlineW
        c.drawPath(p, line)
    }

    private fun oval(c: Canvas, l: Float, t: Float, r: Float, b: Float, color: Int, outline: Boolean = true) {
        path2.reset()
        path2.addOval(l, t, r, b, Path.Direction.CW)
        shape(c, path2, color, outline)
    }

    private fun circle(c: Canvas, x: Float, y: Float, r: Float, color: Int, outline: Boolean = true) =
        oval(c, x - r, y - r, x + r, y + r, color, outline)

    private fun stroke(c: Canvas, p: Path, color: Int, width: Float) {
        line.shader = null
        line.color = color
        line.strokeWidth = max(if (mode == RenderMode.PIXEL) 1f else 0.5f, width)
        c.drawPath(p, line)
    }

    private fun bodyPath(p: Path, bw: Float, bh: Float, ghost: Boolean, t: Float) {
        p.reset()
        val top = -2f * bh
        val cy = -bh
        p.moveTo(0f, top)
        p.cubicTo(bw * 0.62f, top, bw, cy - bh * 0.45f, bw, cy + bh * 0.15f)
        if (!ghost) {
            p.cubicTo(bw, cy + bh * 0.72f, bw * 0.6f, 0f, 0f, 0f)
            p.cubicTo(-bw * 0.6f, 0f, -bw, cy + bh * 0.72f, -bw, cy + bh * 0.15f)
        } else {
            p.lineTo(bw, -bh * 0.1f)
            val waves = 4
            val ww = 2f * bw / waves
            for (i in 0 until waves) {
                val x0 = bw - i * ww
                val wobble = sin(t * 3f + i) * bh * 0.06f
                p.quadTo(x0 - ww * 0.5f, bh * 0.14f + wobble, x0 - ww, -bh * 0.1f)
            }
            p.lineTo(-bw, cy + bh * 0.15f)
        }
        p.cubicTo(-bw, cy - bh * 0.45f, -bw * 0.62f, top, 0f, top)
        p.close()
    }

    // ------------------------------------------------------------------ body

    private fun drawBody(c: Canvas, fl: FormLook, bw: Float, bh: Float, t: Float) {
        val col = fl.colors
        bodyPath(path, bw, bh, fl.ghost, t)
        when (mode) {
            RenderMode.SMOOTH -> {
                fill.shader = LinearGradient(
                    0f, -2f * bh, 0f, 0f,
                    intArrayOf(Colors.lighten(col.body, 0.22f), col.body, col.shade),
                    floatArrayOf(0f, 0.55f, 1f),
                    Shader.TileMode.CLAMP,
                )
                c.drawPath(path, fill)
                fill.shader = null
            }
            RenderMode.FLAT -> {
                fill.shader = null
                fill.color = col.body
                c.drawPath(path, fill)
            }
            RenderMode.PIXEL -> {
                fill.shader = null
                fill.color = col.body
                c.drawPath(path, fill)
                c.save()
                c.clipPath(path)
                fill.color = col.shade
                c.drawRect(-bw * 2f, -bh * 0.38f, bw * 2f, bh, fill)
                c.restore()
            }
        }

        // Belly
        val bellyW = bw * if (fl.bigBelly) 0.7f else 0.56f
        val bellyH = bh * if (fl.bigBelly) 0.64f else 0.5f
        c.save()
        c.clipPath(path)
        fill.shader = null
        fill.color = if (mode == RenderMode.SMOOTH) Colors.alpha(col.belly, 0.9f) else col.belly
        c.drawOval(-bellyW, -bellyH * 1.9f, bellyW, bellyH * 0.1f, fill)
        c.restore()

        if (mode == RenderMode.SMOOTH) {
            fill.color = Colors.alpha(Color.WHITE, 0.55f)
            c.save()
            c.rotate(-24f, -bw * 0.45f, -bh * 1.55f)
            c.drawOval(-bw * 0.65f, -bh * 1.65f, -bw * 0.25f, -bh * 1.45f, fill)
            c.restore()
            fill.color = Colors.alpha(Color.WHITE, 0.4f)
            c.drawCircle(-bw * 0.2f, -bh * 1.72f, bw * 0.05f, fill)
        } else if (mode == RenderMode.PIXEL) {
            fill.color = Colors.lighten(col.body, 0.55f)
            c.drawRect(-bw * 0.55f, -bh * 1.62f, -bw * 0.55f + max(1f, bw * 0.2f), -bh * 1.62f + max(1f, bh * 0.1f), fill)
        }
        outline(c, path, col.shade)
    }

    private fun drawAura(c: Canvas, bw: Float, bh: Float, t: Float, color: Int) {
        val r = bw * 2.1f * (1f + 0.04f * sin(t * 2f))
        if (mode == RenderMode.SMOOTH) {
            fill.shader = RadialGradient(0f, -bh, r, Colors.alpha(color, 0.45f), Colors.alpha(color, 0f), Shader.TileMode.CLAMP)
            c.drawCircle(0f, -bh, r, fill)
            fill.shader = null
        } else {
            for (i in 0 until 8) {
                val a = t * 0.6f + i * (Math.PI.toFloat() / 4f)
                val x = kotlin.math.cos(a) * bw * 1.5f
                val y = -bh + sin(a) * bh * 1.4f
                Shapes.sparkle(path3, x, y, u * 0.06f)
                fill.color = Colors.alpha(color, 0.8f)
                c.drawPath(path3, fill)
            }
        }
    }

    private fun drawSparkles(c: Canvas, bw: Float, bh: Float, t: Float) {
        fill.shader = null
        val pts = floatArrayOf(-1.3f, -1.9f, 1.25f, -1.6f, -1.1f, -0.4f, 1.35f, -0.6f)
        for (i in 0 until 4) {
            val a = (sin(t * 2.2f + i * 1.7f) + 1f) / 2f
            fill.color = Colors.alpha(0xFFFFE680.toInt(), 0.35f + 0.65f * a)
            Shapes.sparkle(path3, pts[i * 2] * bw, pts[i * 2 + 1] * bh, u * (0.05f + 0.04f * a))
            c.drawPath(path3, fill)
        }
    }

    // ------------------------------------------------------------------ back parts

    private fun drawBack(c: Canvas, fl: FormLook, look: PetLook, f: PetFrame, bw: Float, bh: Float) {
        val col = fl.colors
        val t = f.t

        if (fl.tail == Tail.DRAGON) {
            val wag = sin(t * 3f) * bh * 0.12f
            path.reset()
            path.moveTo(bw * 0.7f, -bh * 0.6f)
            path.quadTo(bw * 1.25f, -bh * 0.5f, bw * 1.32f, -bh * 1.0f + wag)
            path.quadTo(bw * 1.12f, -bh * 0.2f, bw * 0.62f, -bh * 0.15f)
            path.close()
            shape(c, path, col.body)
            val tx = bw * 1.32f
            val ty = -bh * 1.0f + wag
            path.reset()
            path.moveTo(tx, ty - u * 0.16f)
            path.lineTo(tx + u * 0.12f, ty + u * 0.02f)
            path.lineTo(tx, ty + u * 0.06f)
            path.lineTo(tx - u * 0.12f, ty + u * 0.02f)
            path.close()
            shape(c, path, col.accent)
        }

        val flap = sin(t * (if (f.hop > 0.01f) 16f else 3f)) * (if (f.hop > 0.01f) 1f else 0.4f) + f.wingFlap
        when (fl.wings) {
            Wings.NONE -> Unit
            Wings.SMALL -> for (s in intArrayOf(-1, 1)) {
                c.save()
                c.translate(s * bw * 0.85f, -bh * 1.15f)
                c.rotate(s * (-18f + flap * 18f))
                oval(c, if (s > 0) 0f else -u * 0.42f, -u * 0.12f, if (s > 0) u * 0.42f else 0f, u * 0.1f, col.accent2)
                oval(c, if (s > 0) 0f else -u * 0.3f, u * 0.0f, if (s > 0) u * 0.3f else 0f, u * 0.17f, col.accent2)
                c.restore()
            }
            Wings.DRAGON -> for (s in intArrayOf(-1, 1)) {
                val sf = s.toFloat()
                c.save()
                c.translate(sf * bw * 0.7f, -bh * 1.25f)
                c.rotate(sf * (flap * 14f - 6f))
                path.reset()
                path.moveTo(0f, 0f)
                path.lineTo(sf * u * 0.35f, -u * 0.55f)
                path.lineTo(sf * u * 0.82f, -u * 0.5f)
                path.quadTo(sf * u * 0.72f, -u * 0.26f, sf * u * 0.84f, -u * 0.06f)
                path.quadTo(sf * u * 0.64f, -u * 0.1f, sf * u * 0.54f, u * 0.1f)
                path.quadTo(sf * u * 0.42f, -u * 0.02f, sf * u * 0.26f, u * 0.14f)
                path.quadTo(sf * u * 0.14f, u * 0.02f, 0f, u * 0.12f)
                path.close()
                shape(c, path, col.accent2)
                path3.reset()
                path3.moveTo(sf * u * 0.35f, -u * 0.52f)
                path3.lineTo(sf * u * 0.54f, u * 0.06f)
                path3.moveTo(sf * u * 0.35f, -u * 0.52f)
                path3.lineTo(sf * u * 0.26f, u * 0.1f)
                stroke(c, path3, Colors.darken(col.accent2, 0.25f), u * 0.025f)
                c.restore()
            }
            Wings.ANGEL -> for (s in intArrayOf(-1, 1)) {
                val sf = s.toFloat()
                c.save()
                c.translate(sf * bw * 0.78f, -bh * 1.2f)
                c.rotate(sf * (flap * 10f))
                val lens = floatArrayOf(0.52f, 0.44f, 0.34f)
                val angles = floatArrayOf(-38f, -12f, 14f)
                for (i in 0..2) {
                    c.save()
                    c.rotate(sf * angles[i])
                    val l = u * lens[i]
                    oval(c, if (s > 0) 0f else -l, -u * 0.08f, if (s > 0) l else 0f, u * 0.08f, col.accent2)
                    c.restore()
                }
                c.restore()
            }
        }

        when (fl.ears) {
            Ears.NONE -> Unit
            Ears.BUNNY -> {
                val earW = u * 0.19f
                val earH = u * 0.34f
                for (s in intArrayOf(-1, 1)) {
                    c.save()
                    c.translate(s * bw * 0.36f, -2f * bh + bh * 0.2f)
                    val droop = if (look.sleeping || look.mood == Mood.SAD) 58f else 12f
                    c.rotate(s * (droop + sin(t * 2f + s) * 5f))
                    oval(c, -earW, -earH * 2f, earW, 0f, col.body)
                    fill.color = col.accent
                    c.drawOval(-earW * 0.5f, -earH * 1.75f, earW * 0.5f, -earH * 0.3f, fill)
                    c.restore()
                }
            }
            Ears.CAT -> for (s in intArrayOf(-1, 1)) {
                val sf = s.toFloat()
                path.reset()
                path.moveTo(sf * bw * 0.16f, -bh * 1.94f)
                path.lineTo(sf * bw * 0.64f, -bh * 2.45f)
                path.lineTo(sf * bw * 0.9f, -bh * 1.5f)
                path.close()
                shape(c, path, col.body)
                path.reset()
                path.moveTo(sf * bw * 0.34f, -bh * 1.9f)
                path.lineTo(sf * bw * 0.62f, -bh * 2.25f)
                path.lineTo(sf * bw * 0.76f, -bh * 1.66f)
                path.close()
                shape(c, path, col.accent, outline = false)
            }
            Ears.ROUND -> for (s in intArrayOf(-1, 1)) {
                val r = u * 0.17f
                circle(c, s * bw * 0.62f, -bh * 1.8f, r, col.body)
                fill.color = col.accent
                c.drawCircle(s * bw * 0.62f, -bh * 1.8f, r * 0.55f, fill)
            }
        }

        if (fl.spikes) {
            for (i in -2..2) {
                val x = i * bw * 0.3f
                val nx = x / bw
                val y = -2f * bh * (1f - 0.26f * nx * nx)
                path.reset()
                path.moveTo(x - bw * 0.15f, y + bh * 0.12f)
                path.lineTo(x + i * bw * 0.08f, y - bh * 0.34f)
                path.lineTo(x + bw * 0.15f, y + bh * 0.12f)
                path.close()
                shape(c, path, col.accent)
            }
        }

        if (fl.horns) {
            for (s in intArrayOf(-1, 1)) {
                val sf = s.toFloat()
                path.reset()
                path.moveTo(sf * bw * 0.16f, -bh * 1.9f)
                path.quadTo(sf * bw * 0.32f, -bh * 2.35f, sf * bw * 0.62f, -bh * 2.5f)
                path.quadTo(sf * bw * 0.48f, -bh * 2.1f, sf * bw * 0.46f, -bh * 1.78f)
                path.close()
                shape(c, path, col.accent)
            }
        }

        when (fl.antenna) {
            Antenna.NONE -> Unit
            Antenna.SPROUT -> {
                c.save()
                c.rotate(sin(t * 1.6f) * 8f, 0f, -2f * bh)
                path.reset()
                path.moveTo(0f, -2f * bh + bh * 0.05f)
                path.quadTo(u * 0.02f, -2f * bh - u * 0.14f, u * 0.05f, -2f * bh - u * 0.22f)
                stroke(c, path, Colors.darken(col.accent, 0.2f), u * 0.045f)
                for (s in intArrayOf(-1, 1)) {
                    c.save()
                    c.translate(u * 0.05f, -2f * bh - u * 0.22f)
                    c.rotate(s * 38f)
                    oval(c, if (s > 0) 0f else -u * 0.16f, -u * 0.05f, if (s > 0) u * 0.16f else 0f, u * 0.05f, col.accent)
                    c.restore()
                }
                c.restore()
            }
            Antenna.GLOW, Antenna.STAR -> {
                val sway = sin(t * 1.3f) * u * 0.04f
                val topX = sway
                val topY = -2f * bh - u * 0.34f
                path.reset()
                path.moveTo(0f, -2f * bh + bh * 0.05f)
                path.quadTo(-u * 0.05f, -2f * bh - u * 0.18f, topX, topY)
                stroke(c, path, Colors.darken(col.body, 0.25f), u * 0.04f)
                val pulse = (sin(t * 2.5f) + 1f) / 2f
                if (mode == RenderMode.SMOOTH) {
                    val gr = u * (0.26f + 0.06f * pulse)
                    fill.shader = RadialGradient(topX, topY, gr, Colors.alpha(col.accent, 0.7f), Colors.alpha(col.accent, 0f), Shader.TileMode.CLAMP)
                    c.drawCircle(topX, topY, gr, fill)
                    fill.shader = null
                }
                if (fl.antenna == Antenna.GLOW) {
                    circle(c, topX, topY, u * 0.1f, Colors.lerp(col.accent, Color.WHITE, 0.3f * pulse))
                } else {
                    Shapes.star(path, topX, topY, u * 0.15f, u * 0.07f, 5, t * 30f)
                    shape(c, path, col.accent)
                }
            }
        }
    }

    // ------------------------------------------------------------------ front parts

    private fun drawFront(c: Canvas, fl: FormLook, look: PetLook, bw: Float, bh: Float) {
        val col = fl.colors
        if (fl.headband) {
            c.save()
            c.clipPath(path.apply { bodyPath(this, bw, bh, fl.ghost, 0f) })
            fill.shader = null
            fill.color = col.accent
            c.drawRect(-bw * 2f, -bh * 1.72f, bw * 2f, -bh * 1.52f, fill)
            fill.color = Colors.lighten(col.accent, 0.5f)
            c.drawRect(-bw * 2f, -bh * 1.66f, bw * 2f, -bh * 1.61f, fill)
            c.restore()
            path.reset()
            path.moveTo(bw * 0.9f, -bh * 1.62f)
            path.lineTo(bw * 1.25f, -bh * 1.82f)
            path.lineTo(bw * 1.18f, -bh * 1.5f)
            path.close()
            shape(c, path, col.accent)
        }
        if (fl.crown && look.hat == null) drawCrown(c, bh, bw * 0.42f, u * 0.3f)
    }

    private fun drawCrown(c: Canvas, bh: Float, w: Float, h: Float) {
        val base = -1.9f * bh
        path.reset()
        path.moveTo(-w, base)
        path.lineTo(-w, base - h * 0.6f)
        path.lineTo(-w * 0.5f, base - h * 0.2f)
        path.lineTo(0f, base - h)
        path.lineTo(w * 0.5f, base - h * 0.2f)
        path.lineTo(w, base - h * 0.6f)
        path.lineTo(w, base)
        path.close()
        if (mode == RenderMode.SMOOTH) {
            fill.shader = LinearGradient(0f, base - h, 0f, base, 0xFFFFE082.toInt(), 0xFFFFA000.toInt(), Shader.TileMode.CLAMP)
            c.drawPath(path, fill)
            fill.shader = null
            outline(c, path, 0xFFFFB300.toInt())
        } else {
            shape(c, path, 0xFFFFC83D.toInt())
        }
        circle(c, 0f, base - h * 0.35f, h * 0.13f, 0xFFE0475F.toInt(), outline = false)
        circle(c, -w * 0.62f, base - h * 0.22f, h * 0.08f, 0xFF4D8DFF.toInt(), outline = false)
        circle(c, w * 0.62f, base - h * 0.22f, h * 0.08f, 0xFF4D8DFF.toInt(), outline = false)
        circle(c, 0f, base - h, h * 0.09f, 0xFFFFF3C4.toInt(), outline = false)
    }

    // ------------------------------------------------------------------ face

    private fun drawFace(c: Canvas, fl: FormLook, look: PetLook, f: PetFrame, bw: Float, bh: Float) {
        val col = fl.colors
        val re = u * 0.125f * (0.9f + 0.1f * fl.width)
        val ex = bw * 0.37f
        val ey = -bh * 1.12f
        val lx = f.lookX * f.facing * re * 0.28f
        val ly = f.lookY * re * 0.22f
        val mood = look.mood
        val expr = f.expression

        val eyes = when {
            look.sleeping -> EyeKind.CLOSED_SMILE
            expr == Expression.LOVE -> EyeKind.HEART
            expr == Expression.EXCITED -> EyeKind.HAPPY_ARC
            expr == Expression.EATING -> EyeKind.HAPPY_ARC
            f.blink > 0.55f -> EyeKind.CLOSED_LINE
            fl.eyes == EyeStyle.GLOW -> EyeKind.GLOW
            mood == Mood.SICK -> EyeKind.HALF
            fl.eyes == EyeStyle.GRUMPY -> EyeKind.HALF
            else -> EyeKind.OPEN
        }
        val mouth = when {
            look.sleeping -> MouthKind.SMALL_O
            expr == Expression.EATING -> MouthKind.CHOMP
            expr == Expression.EXCITED || expr == Expression.LOVE -> MouthKind.BIG_SMILE
            expr == Expression.REFUSE -> MouthKind.FLAT
            expr == Expression.SURPRISED -> MouthKind.SMALL_O
            mood == Mood.SICK -> MouthKind.WAVY
            mood == Mood.SAD -> MouthKind.FROWN
            mood == Mood.HAPPY -> MouthKind.BIG_SMILE
            fl.eyes == EyeStyle.GRUMPY -> MouthKind.FLAT
            else -> MouthKind.CAT
        }

        // Cheeks
        val cheekA = when {
            expr == Expression.LOVE || expr == Expression.EXCITED -> 0.85f
            mode == RenderMode.FLAT -> 0.35f
            else -> 0.55f
        }
        fill.shader = null
        for (s in intArrayOf(-1, 1)) {
            val cxk = s * bw * 0.63f
            val cyk = -bh * 0.9f
            if (mode == RenderMode.PIXEL) {
                fill.color = col.cheek
                c.drawRect(cxk - re * 0.7f, cyk - re * 0.25f, cxk + re * 0.7f, cyk + re * 0.25f, fill)
            } else {
                fill.color = Colors.alpha(col.cheek, cheekA)
                c.drawOval(cxk - re * 0.95f, cyk - re * 0.5f, cxk + re * 0.95f, cyk + re * 0.5f, fill)
            }
        }

        for (s in intArrayOf(-1, 1)) {
            val x = s * ex
            drawEye(c, eyes, x, ey, re, lx, ly, f.blink, col)
        }

        // Brows
        if (mood == Mood.SAD && !look.sleeping && expr == Expression.AUTO) {
            for (s in intArrayOf(-1, 1)) {
                val sf = s.toFloat()
                path.reset()
                path.moveTo(sf * (ex + re * 0.8f), ey - re * 1.15f)
                path.lineTo(sf * (ex - re * 0.6f), ey - re * 1.6f)
                stroke(c, path, col.eye, re * 0.22f)
            }
            val tearT = (f.t % 2.2f) / 2.2f
            fill.color = Colors.alpha(0xFF8FD3FF.toInt(), 1f - tearT)
            Shapes.drop(path3, -ex - re * 0.2f, ey + re * 1.2f + tearT * re * 2.2f, re * 0.32f)
            c.drawPath(path3, fill)
        } else if (fl.eyes == EyeStyle.GRUMPY && !look.sleeping && expr == Expression.AUTO) {
            for (s in intArrayOf(-1, 1)) {
                val sf = s.toFloat()
                path.reset()
                path.moveTo(sf * (ex + re * 0.9f), ey - re * 1.55f)
                path.lineTo(sf * (ex - re * 0.7f), ey - re * 1.05f)
                stroke(c, path, col.eye, re * 0.24f)
            }
        }

        drawMouth(c, mouth, 0f, -bh * 0.78f, u * 0.075f, f, col)
    }

    private fun drawEye(c: Canvas, kind: EyeKind, x: Float, y: Float, re: Float, lx: Float, ly: Float, blink: Float, col: PetColors) {
        when (kind) {
            EyeKind.OPEN -> drawOpenEye(c, x, y, re, lx, ly, blink, col)
            EyeKind.HALF -> {
                c.save()
                c.clipRect(x - re * 1.5f, y - re * 0.1f, x + re * 1.5f, y + re * 1.5f)
                drawOpenEye(c, x, y, re, lx, ly, 0f, col)
                c.restore()
                path.reset()
                path.moveTo(x - re * 0.85f, y - re * 0.05f)
                path.lineTo(x + re * 0.85f, y - re * 0.05f)
                stroke(c, path, col.eye, re * 0.26f)
            }
            EyeKind.CLOSED_SMILE -> {
                path.reset()
                path.moveTo(x - re * 0.7f, y)
                path.quadTo(x, y + re * 0.75f, x + re * 0.7f, y)
                stroke(c, path, col.eye, re * 0.28f)
            }
            EyeKind.CLOSED_LINE -> {
                path.reset()
                path.moveTo(x - re * 0.7f, y + re * 0.1f)
                path.quadTo(x, y + re * 0.3f, x + re * 0.7f, y + re * 0.1f)
                stroke(c, path, col.eye, re * 0.26f)
            }
            EyeKind.HAPPY_ARC -> {
                path.reset()
                path.moveTo(x - re * 0.72f, y + re * 0.3f)
                path.quadTo(x, y - re * 0.85f, x + re * 0.72f, y + re * 0.3f)
                stroke(c, path, col.eye, re * 0.3f)
            }
            EyeKind.HEART -> {
                Shapes.heart(path, x, y, re * 1.25f)
                shape(c, path, 0xFFFF4F8B.toInt(), outline = false)
                if (mode != RenderMode.PIXEL) {
                    fill.color = Colors.alpha(Color.WHITE, 0.7f)
                    c.drawCircle(x - re * 0.35f, y - re * 0.35f, re * 0.2f, fill)
                }
            }
            EyeKind.GLOW -> {
                val h = re * 1.0f * (1f - blink)
                if (mode == RenderMode.SMOOTH) {
                    fill.shader = RadialGradient(x, y, re * 2f, Colors.alpha(col.eye, 0.5f), Colors.alpha(col.eye, 0f), Shader.TileMode.CLAMP)
                    c.drawCircle(x, y, re * 2f, fill)
                    fill.shader = null
                }
                fill.color = col.eye
                c.drawOval(x - re * 0.7f, y - h, x + re * 0.7f, y + h, fill)
            }
        }
    }

    private fun drawOpenEye(c: Canvas, x: Float, y: Float, re: Float, lx: Float, ly: Float, blink: Float, col: PetColors) {
        val open = 1f - blink.coerceIn(0f, 0.55f) / 0.55f * 0.8f
        val w = re * 0.82f
        val h = re * 1.02f * open
        fill.shader = null
        when (mode) {
            RenderMode.SMOOTH -> {
                fill.shader = LinearGradient(0f, y - h, 0f, y + h, col.eye, Colors.lerp(col.eye, 0xFF8A7BD1.toInt(), 0.55f), Shader.TileMode.CLAMP)
                c.drawOval(x - w, y - h, x + w, y + h, fill)
                fill.shader = null
                fill.color = Color.WHITE
                c.drawCircle(x + re * 0.28f + lx, y - re * 0.38f * open + ly, re * 0.34f * open, fill)
                c.drawCircle(x - re * 0.25f + lx * 0.5f, y + re * 0.42f * open + ly * 0.5f, re * 0.15f * open, fill)
            }
            RenderMode.FLAT -> {
                fill.color = col.eye
                c.drawOval(x - w * 0.85f, y - h * 0.9f, x + w * 0.85f, y + h * 0.9f, fill)
                fill.color = Color.WHITE
                c.drawCircle(x + re * 0.22f + lx, y - re * 0.3f * open + ly, re * 0.24f * open, fill)
            }
            RenderMode.PIXEL -> {
                fill.color = col.eye
                c.drawRect(x - w * 0.75f, y - h, x + w * 0.75f, y + h, fill)
                fill.color = Color.WHITE
                val px = max(1f, re * 0.4f)
                c.drawRect(x + lx, y - h * 0.8f + ly, x + lx + px, y - h * 0.8f + ly + px, fill)
            }
        }
    }

    private fun drawMouth(c: Canvas, kind: MouthKind, x: Float, y: Float, m: Float, f: PetFrame, col: PetColors) {
        val dark = 0xFF7A2E45.toInt()
        when (kind) {
            MouthKind.CAT -> {
                path.reset()
                path.moveTo(x - m, y)
                path.quadTo(x - m * 0.5f, y + m * 0.95f, x, y)
                path.quadTo(x + m * 0.5f, y + m * 0.95f, x + m, y)
                stroke(c, path, col.eye, m * 0.42f)
            }
            MouthKind.BIG_SMILE -> {
                path.reset()
                path.moveTo(x - m * 1.5f, y - m * 0.2f)
                path.quadTo(x, y + m * 3.2f, x + m * 1.5f, y - m * 0.2f)
                path.close()
                fill.shader = null
                fill.color = dark
                c.drawPath(path, fill)
                c.save()
                c.clipPath(path)
                fill.color = 0xFFFF8FA3.toInt()
                c.drawCircle(x, y + m * 1.8f, m * 0.95f, fill)
                c.restore()
            }
            MouthKind.FROWN -> {
                path.reset()
                path.moveTo(x - m, y + m * 0.6f)
                path.quadTo(x, y - m * 0.5f, x + m, y + m * 0.6f)
                stroke(c, path, col.eye, m * 0.42f)
            }
            MouthKind.CHOMP -> {
                val h = m * (0.3f + 1.6f * f.mouthOpen)
                fill.shader = null
                fill.color = dark
                c.drawOval(x - m * 0.9f, y - h * 0.5f, x + m * 0.9f, y + h, fill)
            }
            MouthKind.SMALL_O -> {
                val s = 1f + 0.15f * f.breath
                fill.shader = null
                fill.color = dark
                c.drawOval(x - m * 0.4f * s, y - m * 0.2f, x + m * 0.4f * s, y + m * 0.6f * s, fill)
            }
            MouthKind.FLAT -> {
                path.reset()
                path.moveTo(x - m * 0.8f, y + m * 0.2f)
                path.lineTo(x + m * 0.8f, y + m * 0.2f)
                stroke(c, path, col.eye, m * 0.4f)
            }
            MouthKind.WAVY -> {
                path.reset()
                path.moveTo(x - m * 1.2f, y)
                path.quadTo(x - m * 0.6f, y - m * 0.55f, x, y)
                path.quadTo(x + m * 0.6f, y + m * 0.55f, x + m * 1.2f, y)
                stroke(c, path, col.eye, m * 0.38f)
            }
        }
    }

    // ------------------------------------------------------------------ accessories

    private fun drawAccessories(c: Canvas, look: PetLook, f: PetFrame, bw: Float, bh: Float) {
        val re = u * 0.125f
        val ex = bw * 0.37f
        val ey = -bh * 1.12f
        val t = f.t

        when (look.neck) {
            "neck_bowtie" -> {
                val y = -bh * 0.5f
                for (s in intArrayOf(-1, 1)) {
                    val sf = s.toFloat()
                    path.reset()
                    path.moveTo(0f, y)
                    path.lineTo(sf * u * 0.21f, y - u * 0.11f)
                    path.lineTo(sf * u * 0.21f, y + u * 0.11f)
                    path.close()
                    shape(c, path, 0xFFE0475F.toInt())
                }
                circle(c, 0f, y, u * 0.05f, 0xFFB8324A.toInt())
            }
            "neck_scarf" -> {
                c.save()
                c.clipPath(path3.apply { bodyPath(this, bw, bh, look.formLook.ghost, t) })
                fill.shader = null
                fill.color = 0xFFFF6B6B.toInt()
                c.drawRect(-bw * 2f, -bh * 0.66f, bw * 2f, -bh * 0.4f, fill)
                fill.color = Colors.alpha(Color.WHITE, 0.55f)
                var sx = -bw
                while (sx < bw) {
                    c.drawRect(sx, -bh * 0.66f, sx + bw * 0.1f, -bh * 0.4f, fill)
                    sx += bw * 0.3f
                }
                c.restore()
                c.save()
                c.rotate(-8f + sin(t * 2f) * 4f, bw * 0.35f, -bh * 0.5f)
                path.reset()
                path.addRoundRect(bw * 0.28f, -bh * 0.55f, bw * 0.5f, -bh * 0.05f, u * 0.04f, u * 0.04f, Path.Direction.CW)
                shape(c, path, 0xFFFF6B6B.toInt())
                c.restore()
            }
            "neck_bell" -> {
                c.save()
                c.clipPath(path3.apply { bodyPath(this, bw, bh, look.formLook.ghost, t) })
                fill.shader = null
                fill.color = 0xFFE0475F.toInt()
                c.drawRect(-bw * 2f, -bh * 0.6f, bw * 2f, -bh * 0.5f, fill)
                c.restore()
                val swing = sin(t * 4f) * u * 0.015f
                val by = -bh * 0.42f
                circle(c, swing, by, u * 0.085f, 0xFFFFC83D.toInt())
                path.reset()
                path.moveTo(swing - u * 0.04f, by + u * 0.03f)
                path.lineTo(swing + u * 0.04f, by + u * 0.03f)
                stroke(c, path, 0xFF9A6A00.toInt(), u * 0.02f)
                if (mode != RenderMode.PIXEL) {
                    fill.color = Colors.alpha(Color.WHITE, 0.7f)
                    c.drawCircle(swing - u * 0.03f, by - u * 0.03f, u * 0.02f, fill)
                }
            }
        }

        when (look.face) {
            "face_round" -> {
                for (s in intArrayOf(-1, 1)) {
                    path.reset()
                    path.addCircle(s * ex, ey, re * 1.45f, Path.Direction.CW)
                    fill.shader = null
                    fill.color = Colors.alpha(Color.WHITE, 0.18f)
                    c.drawPath(path, fill)
                    stroke(c, path, 0xFF3A3150.toInt(), u * 0.035f)
                }
                path.reset()
                path.moveTo(-ex + re * 1.45f, ey)
                path.quadTo(0f, ey - re * 0.5f, ex - re * 1.45f, ey)
                stroke(c, path, 0xFF3A3150.toInt(), u * 0.03f)
            }
            "face_sun" -> {
                for (s in intArrayOf(-1, 1)) {
                    val x = s * ex
                    path.reset()
                    path.addRoundRect(x - re * 1.45f, ey - re * 0.95f, x + re * 1.45f, ey + re * 1.05f, re * 0.55f, re * 0.55f, Path.Direction.CW)
                    shape(c, path, 0xFF1F1B2E.toInt())
                    if (mode != RenderMode.PIXEL) {
                        path.reset()
                        path.moveTo(x - re * 0.8f, ey - re * 0.3f)
                        path.lineTo(x - re * 0.2f, ey - re * 0.6f)
                        stroke(c, path, Colors.alpha(Color.WHITE, 0.6f), re * 0.2f)
                    } else {
                        fill.color = Color.WHITE
                        c.drawRect(x - re * 0.9f, ey - re * 0.6f, x - re * 0.9f + max(1f, re * 0.4f), ey - re * 0.6f + max(1f, re * 0.4f), fill)
                    }
                }
                path.reset()
                path.moveTo(-ex + re * 1.4f, ey - re * 0.4f)
                path.lineTo(ex - re * 1.4f, ey - re * 0.4f)
                stroke(c, path, 0xFF1F1B2E.toInt(), u * 0.04f)
            }
            "face_heart" -> {
                for (s in intArrayOf(-1, 1)) {
                    Shapes.heart(path, s * ex, ey + re * 0.1f, re * 1.9f)
                    fill.shader = null
                    fill.color = Colors.alpha(0xFFFF5C9A.toInt(), if (mode == RenderMode.PIXEL) 1f else 0.82f)
                    c.drawPath(path, fill)
                    outline(c, path, 0xFFFF5C9A.toInt())
                }
                path.reset()
                path.moveTo(-ex + re * 1.2f, ey - re * 0.3f)
                path.lineTo(ex - re * 1.2f, ey - re * 0.3f)
                stroke(c, path, 0xFFD13A78.toInt(), u * 0.035f)
            }
        }

        val top = -2f * bh
        when (look.hat) {
            "hat_party" -> {
                c.save()
                c.translate(bw * 0.12f, top + bh * 0.1f)
                c.rotate(12f)
                val hw = u * 0.21f
                val hh = u * 0.52f
                path.reset()
                path.moveTo(-hw, 0f)
                path.lineTo(hw, 0f)
                path.lineTo(0f, -hh)
                path.close()
                shape(c, path, 0xFF7C6CFF.toInt())
                c.save()
                c.clipPath(path)
                fill.color = 0xFFFFD166.toInt()
                c.drawRect(-hw, -hh * 0.35f, hw, -hh * 0.22f, fill)
                c.drawRect(-hw, -hh * 0.7f, hw, -hh * 0.58f, fill)
                c.restore()
                circle(c, 0f, -hh, u * 0.075f, 0xFFFF6B9A.toInt())
                c.restore()
            }
            "hat_bow" -> {
                c.save()
                c.translate(bw * 0.45f, top + bh * 0.25f)
                c.rotate(-15f)
                for (s in intArrayOf(-1, 1)) {
                    val sf = s.toFloat()
                    path.reset()
                    path.moveTo(0f, 0f)
                    path.quadTo(sf * u * 0.12f, -u * 0.18f, sf * u * 0.22f, -u * 0.1f)
                    path.lineTo(sf * u * 0.22f, u * 0.1f)
                    path.quadTo(sf * u * 0.12f, u * 0.16f, 0f, 0f)
                    path.close()
                    shape(c, path, 0xFFFF5C9A.toInt())
                }
                circle(c, 0f, 0f, u * 0.06f, 0xFFE0407E.toInt())
                c.restore()
            }
            "hat_flower" -> {
                val fx = -bw * 0.45f
                val fy = top + bh * 0.25f
                c.save()
                c.rotate(t * 12f, fx, fy)
                for (i in 0 until 5) {
                    val a = i * (Math.PI * 2 / 5).toFloat()
                    circle(c, fx + kotlin.math.cos(a) * u * 0.085f, fy + sin(a) * u * 0.085f, u * 0.075f, 0xFFFFB3D9.toInt())
                }
                c.restore()
                circle(c, fx, fy, u * 0.06f, 0xFFFFD84D.toInt())
            }
            "hat_cap" -> {
                val base = top + bh * 0.14f
                rect.set(-bw * 0.72f, base - u * 0.34f, bw * 0.72f, base + u * 0.34f)
                path.reset()
                path.arcTo(rect, 180f, 180f)
                path.close()
                shape(c, path, 0xFF4D8DFF.toInt())
                oval(c, bw * 0.15f, base - u * 0.05f, bw * 1.25f, base + u * 0.07f, 0xFF2F63C9.toInt())
                circle(c, 0f, base - u * 0.34f, u * 0.04f, 0xFF2F63C9.toInt())
            }
            "hat_tophat" -> {
                c.save()
                c.rotate(6f, 0f, top)
                val base = top + bh * 0.08f
                path.reset()
                path.addRect(-bw * 0.42f, base - u * 0.56f, bw * 0.42f, base, Path.Direction.CW)
                shape(c, path, 0xFF2E2A3A.toInt())
                fill.color = 0xFFE0475F.toInt()
                c.drawRect(-bw * 0.42f, base - u * 0.16f, bw * 0.42f, base - u * 0.06f, fill)
                oval(c, -bw * 0.72f, base - u * 0.06f, bw * 0.72f, base + u * 0.07f, 0xFF2E2A3A.toInt())
                c.restore()
            }
            "hat_wizard" -> {
                val base = top + bh * 0.12f
                path.reset()
                path.moveTo(-bw * 0.55f, base)
                path.quadTo(-bw * 0.1f, base - u * 0.5f, bw * 0.42f + sin(t * 1.5f) * u * 0.03f, base - u * 0.85f)
                path.quadTo(bw * 0.2f, base - u * 0.4f, bw * 0.55f, base)
                path.close()
                shape(c, path, 0xFF5B4BD6.toInt())
                Shapes.star(path3, -bw * 0.05f, base - u * 0.25f, u * 0.07f, u * 0.03f)
                fill.color = 0xFFFFD95A.toInt()
                c.drawPath(path3, fill)
                Shapes.star(path3, bw * 0.2f, base - u * 0.5f, u * 0.05f, u * 0.022f)
                c.drawPath(path3, fill)
                oval(c, -bw * 0.8f, base - u * 0.06f, bw * 0.8f, base + u * 0.07f, 0xFF4636B8.toInt())
            }
            "hat_crown" -> drawCrown(c, bh, bw * 0.46f, u * 0.34f)
        }
    }

    // ------------------------------------------------------------------ egg

    private fun drawEgg(c: Canvas, cx: Float, groundY: Float, look: PetLook, f: PetFrame, silhouette: Int?) {
        val col = look.formLook.colors
        val ew = u * 0.6f * (1f - 0.02f * f.breath)
        val eh = u * 0.78f * (1f + 0.02f * f.breath)
        bounds.set(cx - ew * 1.4f, groundY - eh * 2.3f, cx + ew * 1.4f, groundY)
        val saveCount = if (silhouette != null) {
            layerPaint.colorFilter = PorterDuffColorFilter(silhouette, PorterDuff.Mode.SRC_IN)
            c.saveLayer(null, layerPaint)
        } else c.save()
        c.translate(cx, groundY)
        c.rotate(f.eggWobble)

        path.reset()
        path.moveTo(0f, -2f * eh)
        path.cubicTo(ew * 0.75f, -2f * eh, ew, -eh * 1.1f, ew, -eh * 0.7f)
        path.cubicTo(ew, -eh * 0.25f, ew * 0.6f, 0f, 0f, 0f)
        path.cubicTo(-ew * 0.6f, 0f, -ew, -eh * 0.25f, -ew, -eh * 0.7f)
        path.cubicTo(-ew, -eh * 1.1f, -ew * 0.75f, -2f * eh, 0f, -2f * eh)
        path.close()

        val taps = look.hatchTaps
        if (taps >= 3 && mode == RenderMode.SMOOTH && silhouette == null) {
            val pulse = (sin(f.t * 6f) + 1f) / 2f
            val r = eh * (1.5f + 0.1f * pulse)
            fill.shader = RadialGradient(0f, -eh, r, Colors.alpha(0xFFFFE680.toInt(), 0.55f), Colors.alpha(0xFFFFE680.toInt(), 0f), Shader.TileMode.CLAMP)
            c.drawCircle(0f, -eh, r, fill)
            fill.shader = null
        }

        if (mode == RenderMode.SMOOTH) {
            fill.shader = LinearGradient(0f, -2f * eh, 0f, 0f, Colors.lighten(col.body, 0.3f), col.shade, Shader.TileMode.CLAMP)
            c.drawPath(path, fill)
            fill.shader = null
        } else {
            fill.shader = null
            fill.color = col.body
            c.drawPath(path, fill)
        }
        c.save()
        c.clipPath(path)
        fill.shader = null
        // zig-zag band
        path2.reset()
        val bandY = -eh * 0.95f
        val zw = ew * 0.25f
        path2.moveTo(-ew * 1.2f, bandY)
        var x = -ew * 1.2f
        var up = true
        while (x < ew * 1.2f) {
            x += zw
            path2.lineTo(x, bandY + if (up) -eh * 0.12f else eh * 0.12f)
            up = !up
        }
        path2.lineTo(ew * 1.2f, bandY + eh * 0.25f)
        path2.lineTo(-ew * 1.2f, bandY + eh * 0.25f)
        path2.close()
        fill.color = col.accent2
        c.drawPath(path2, fill)
        fill.color = col.accent
        c.drawCircle(-ew * 0.35f, -eh * 1.5f, ew * 0.16f, fill)
        c.drawCircle(ew * 0.4f, -eh * 1.3f, ew * 0.11f, fill)
        c.drawCircle(ew * 0.1f, -eh * 0.35f, ew * 0.14f, fill)
        c.drawCircle(-ew * 0.6f, -eh * 0.45f, ew * 0.09f, fill)
        c.restore()
        if (mode == RenderMode.SMOOTH) {
            fill.color = Colors.alpha(Color.WHITE, 0.6f)
            c.drawOval(-ew * 0.6f, -eh * 1.7f, -ew * 0.3f, -eh * 1.3f, fill)
        }
        outline(c, path, col.shade)

        if (taps > 0) {
            path2.reset()
            val crackColor = 0xFF6B4E3D.toInt()
            val n = taps.coerceAtMost(4)
            // Each tap adds another crack segment across the upper shell.
            val pts = arrayOf(
                floatArrayOf(-0.9f, -1.25f, -0.55f, -1.4f, -0.35f, -1.2f, -0.1f, -1.42f),
                floatArrayOf(-0.1f, -1.42f, 0.15f, -1.22f, 0.4f, -1.45f),
                floatArrayOf(0.4f, -1.45f, 0.6f, -1.25f, 0.9f, -1.35f),
                floatArrayOf(-0.35f, -1.2f, -0.3f, -0.95f, -0.05f, -1.0f, 0.15f, -1.22f),
            )
            for (i in 0 until n) {
                val p = pts[i]
                path2.moveTo(p[0] * ew, p[1] * eh)
                var k = 2
                while (k < p.size) {
                    path2.lineTo(p[k] * ew, p[k + 1] * eh)
                    k += 2
                }
            }
            stroke(c, path2, crackColor, u * 0.035f)
        }
        c.restoreToCount(saveCount)
    }

    companion object {
        val FLAT_OUTLINE: Int = Colors.alpha(0xFF3A3150.toInt(), 0.9f)
    }
}

/** Computes idle motion for the pet. */
object PetMotion {
    fun idle(f: PetFrame, look: PetLook, t: Float) {
        f.t = t
        f.breath = sin(t * 2f * Math.PI.toFloat() / 3.2f)
        f.hop = 0f
        f.squish = 0f
        f.tilt = 0f
        f.mouthOpen = 0f
        f.wingFlap = 0f
        val bp = t % 4.1f
        f.blink = if (bp < 0.14f) 1f - abs(bp - 0.07f) / 0.07f else 0f
        if (look.sleeping) {
            f.blink = 0f
            f.breath = sin(t * 2f * Math.PI.toFloat() / 4.2f) * 1.6f
            return
        }
        when (look.mood) {
            Mood.HAPPY -> {
                val phase = t % 2.4f
                if (phase < 0.45f) {
                    f.hop = sin(phase / 0.45f * Math.PI.toFloat()) * 0.55f
                } else if (phase < 0.62f) {
                    f.squish = sin((phase - 0.45f) / 0.17f * Math.PI.toFloat()) * 0.45f
                }
            }
            Mood.OKAY -> f.tilt = sin(t * 0.9f) * 3f
            Mood.SAD, Mood.SICK -> f.tilt = sin(t * 0.5f) * 2f
            else -> Unit
        }
        f.eggWobble = if (look.form == Form.EGG) sin(t * 1.3f) * 2.5f else 0f
    }
}
