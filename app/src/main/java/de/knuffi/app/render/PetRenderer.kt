package de.knuffi.app.render

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PointF
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.PorterDuffXfermode
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import de.knuffi.core.Form
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/**
 * Draws the pet in a soft, plastic 3D look with the plain Android canvas so the same art
 * can be used in Compose, the live wallpaper, the floating overlay, the widget and notifications.
 */
class PetRenderer {
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val line = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val emoji = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        typeface = Typeface.DEFAULT
    }
    private val body = Path()
    private val path = Path()
    private val path2 = Path()
    private val path3 = Path()
    private val tmp = Path()
    private val rect = RectF()
    private val layerPaint = Paint()
    private val tint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val srcAtop = PorterDuffXfermode(PorterDuff.Mode.SRC_ATOP)
    private var u = 1f

    /** Bounds of the most recently drawn pet in canvas coordinates. */
    val bounds = RectF()

    /** Top of the head of the most recently drawn pet (for speech bubbles). */
    val head = PointF()

    // ------------------------------------------------------------------ shared helpers

    private fun shaderFill(s: Shader) {
        fill.color = Color.WHITE
        fill.shader = s
    }

    private fun radial(cx: Float, cy: Float, r: Float, colors: IntArray, stops: FloatArray? = null): RadialGradient =
        RadialGradient(cx, cy, max(r, 0.5f), colors, stops, Shader.TileMode.CLAMP)

    /** Fills a path with a soft light-from-top-left gradient and an optional thin outline. */
    private fun shaded(c: Canvas, p: Path, light: Int, base: Int, dark: Int, outline: Boolean = true) {
        p.computeBounds(rect, false)
        val w = rect.width()
        val h = rect.height()
        shaderFill(
            radial(
                rect.left + w * 0.34f, rect.top + h * 0.28f, max(w, h) * 0.95f,
                intArrayOf(light, base, dark), floatArrayOf(0f, 0.5f, 1f),
            ),
        )
        c.drawPath(p, fill)
        fill.shader = null
        if (outline) outline(c, p, dark)
    }

    private fun outline(c: Canvas, p: Path, dark: Int) {
        line.shader = null
        line.color = Colors.alpha(Colors.darken(dark, 0.3f), 0.45f)
        line.strokeWidth = u * 0.016f
        c.drawPath(p, line)
    }

    private fun gloss(c: Canvas, x: Float, y: Float, rx: Float, ry: Float, a: Float = 0.8f, rot: Float = -25f) {
        c.save()
        c.rotate(rot, x, y)
        c.scale(1f, ry / max(rx, 0.1f), x, y)
        shaderFill(radial(x, y, rx, intArrayOf(Colors.alpha(Color.WHITE, a), Colors.alpha(Color.WHITE, 0f))))
        c.drawCircle(x, y, rx, fill)
        fill.shader = null
        c.restore()
    }

    private fun stroke(c: Canvas, p: Path, color: Int, width: Float) {
        line.shader = null
        line.color = color
        line.strokeWidth = max(0.8f, width)
        c.drawPath(p, line)
    }

    private fun ovalPath(p: Path, l: Float, t: Float, r: Float, b: Float): Path {
        p.reset()
        p.addOval(l, t, r, b, Path.Direction.CW)
        return p
    }

    private fun buildBody(p: Path, bw: Float, bh: Float, bottom: Float, ghost: Boolean, t: Float) {
        p.reset()
        val top = bottom - 2f * bh
        val cy = bottom - bh
        p.moveTo(0f, top)
        p.cubicTo(bw * 0.62f, top, bw, cy - bh * 0.45f, bw, cy + bh * 0.15f)
        if (!ghost) {
            p.cubicTo(bw, cy + bh * 0.72f, bw * 0.6f, bottom, 0f, bottom)
            p.cubicTo(-bw * 0.6f, bottom, -bw, cy + bh * 0.72f, -bw, cy + bh * 0.15f)
        } else {
            p.lineTo(bw, bottom - bh * 0.12f)
            val waves = 4
            val ww = 2f * bw / waves
            for (i in 0 until waves) {
                val x0 = bw - i * ww
                val wob = sin(t * 3f + i) * bh * 0.07f
                p.quadTo(x0 - ww * 0.5f, bottom + bh * 0.16f + wob, x0 - ww, bottom - bh * 0.12f)
            }
            p.lineTo(-bw, cy + bh * 0.15f)
        }
        p.cubicTo(-bw, cy - bh * 0.45f, -bw * 0.62f, top, 0f, top)
        p.close()
    }

    // ------------------------------------------------------------------ public API

    fun drawShadow(c: Canvas, cx: Float, groundY: Float, size: Float, pose: PetPose, look: PetLook?) {
        val u = size / 2f
        val fl = look?.formLook
        val ghost = fl?.ghost == true
        val shrink = (1f - pose.lift * 0.9f).coerceIn(0.35f, 1f) * if (ghost) 0.75f else 1f
        val w = u * 0.78f * (fl?.width ?: 0.8f) * shrink * (1f + 0.25f * pose.lying)
        val h = u * 0.12f * shrink
        c.save()
        c.scale(1f, h / w, cx, groundY)
        shaderFill(
            radial(
                cx, groundY, w,
                intArrayOf(Colors.alpha(0xFF2A1740.toInt(), 0.34f * shrink), Colors.alpha(0xFF2A1740.toInt(), 0.16f * shrink), Colors.alpha(0xFF2A1740.toInt(), 0f)),
                floatArrayOf(0f, 0.55f, 1f),
            ),
        )
        c.drawCircle(cx, groundY, w, fill)
        fill.shader = null
        c.restore()
    }

    fun draw(c: Canvas, cx: Float, groundY: Float, size: Float, look: PetLook, pose: PetPose, silhouette: Int? = null) {
        u = size / 2f
        if (look.form == Form.EGG) {
            drawEgg(c, cx, groundY, look, pose, silhouette)
            return
        }
        val fl = look.formLook
        val col = fl.colors
        val lying = pose.lying.coerceIn(0f, 1f)
        val bw = u * 0.70f * fl.width * (1f + 0.18f * lying)
        val bh = u * 0.60f * fl.height * (1f - 0.3f * lying)
        val footH = if (fl.feet) u * 0.12f * (1f - lying) else 0f
        val ghostBob = if (fl.ghost) u * 0.1f + sin(pose.t * 2f) * u * 0.04f else 0f
        val bottom = -footH * 0.5f - ghostBob
        val top = bottom - 2f * bh
        val cy = bottom - bh
        val turn = pose.turn.coerceIn(-1f, 1f)
        val fx = turn * bw * 0.22f
        val baseY = groundY - pose.lift * u

        // bounds in canvas space (approximate, ignores rotation)
        val sx = pose.scaleX
        val sy = pose.scaleY
        bounds.set(cx - bw * 1.15f * sx, baseY + top * sy - u * 0.1f, cx + bw * 1.15f * sx, baseY + u * 0.02f)
        head.set(cx + fx * sx, baseY + (top - earHeight(fl)) * sy)

        val useLayer = silhouette != null || pose.sickTint > 0.01f
        val save = if (useLayer) {
            layerPaint.colorFilter = silhouette?.let { PorterDuffColorFilter(it, PorterDuff.Mode.SRC_IN) }
            c.saveLayer(null, layerPaint)
        } else {
            c.save()
        }
        c.translate(cx, baseY)
        c.rotate(pose.tilt)
        c.scale(sx, sy)

        if (look.level >= 25 && silhouette == null) drawAura(c, bw, bh, cy, pose.t, col.accent)
        drawBack(c, fl, look, pose, bw, bh, top, cy, fx)
        if (fl.feet && lying < 0.6f) drawFeet(c, col, pose, bw, footH)
        drawBody(c, fl, look, pose, bw, bh, bottom, top, cy, fx)
        drawFace(c, fl, pose, bw, bh, cy, fx)
        drawFront(c, fl, look, pose, bw, bh, top, cy, fx)
        drawAccessories(c, look, pose, bw, bh, top, cy, fx)
        if (lying < 0.5f) drawArms(c, col, pose, bw, bh, cy, fx)
        if (pose.foam > 0.01f) drawFoam(c, pose, bw, top, fx)
        if (fl.sparkles && silhouette == null) drawSparkles(c, bw, bh, cy, pose.t)

        if (pose.sickTint > 0.01f && silhouette == null) {
            tint.shader = null
            tint.color = Colors.alpha(0xFF74C476.toInt(), 0.3f * pose.sickTint)
            tint.xfermode = srcAtop
            c.drawRect(-bw * 3f, top - u, bw * 3f, u, tint)
            tint.xfermode = null
        }
        if (pose.sweat > 0.01f) {
            val bob = sin(pose.t * 3f) * u * 0.03f
            Shapes.drop(path, fx + bw * 0.72f, top + bh * 0.45f + bob, u * 0.055f)
            shaderFill(radial(fx + bw * 0.7f, top + bh * 0.4f + bob, u * 0.09f, intArrayOf(0xFFE8F7FF.toInt(), 0xFF7CC8FF.toInt())))
            c.drawPath(path, fill)
            fill.shader = null
        }
        if (pose.glow > 0.01f && silhouette == null) {
            tint.color = Colors.alpha(Color.WHITE, pose.glow)
            tint.xfermode = srcAtop
            c.drawRect(-bw * 3f, top - u, bw * 3f, u, tint)
            tint.xfermode = null
        }
        c.restoreToCount(save)
    }

    private fun earHeight(fl: FormLook): Float = when {
        fl.ears == Ears.BUNNY -> u * 0.6f
        fl.antenna != Antenna.NONE -> u * 0.35f
        fl.horns || fl.ears == Ears.CAT -> u * 0.25f
        else -> 0f
    }

    // ------------------------------------------------------------------ body

    private fun drawBody(c: Canvas, fl: FormLook, look: PetLook, pose: PetPose, bw: Float, bh: Float, bottom: Float, top: Float, cy: Float, fx: Float) {
        val col = fl.colors
        buildBody(body, bw, bh, bottom, fl.ghost, pose.t)

        // Base: light from the top left, turning slightly with the head.
        val lx = -bw * 0.38f + fx * 0.3f
        val ly = top + bh * 0.42f
        shaderFill(radial(lx, ly, max(bw, bh) * 2.15f, intArrayOf(col.light, col.body, col.shade), floatArrayOf(0f, 0.42f, 1f)))
        c.drawPath(body, fill)
        fill.shader = null

        c.save()
        c.clipPath(body)
        // Soft belly patch
        val bellyW = bw * if (fl.bigBelly) 0.72f else 0.58f
        val bellyH = bh * if (fl.bigBelly) 0.68f else 0.52f
        val bx = fx * 0.55f
        val by = bottom - bellyH * 0.8f
        c.save()
        c.scale(1f, bellyH / bellyW, bx, by)
        shaderFill(radial(bx, by, bellyW, intArrayOf(col.belly, Colors.alpha(col.belly, 0.95f), Colors.alpha(col.belly, 0f)), floatArrayOf(0f, 0.72f, 1f)))
        c.drawCircle(bx, by, bellyW, fill)
        fill.shader = null
        c.restore()
        // Dirt smudges
        if (look.dirty) {
            fill.color = Colors.alpha(0xFF8D6A4A.toInt(), 0.22f)
            c.drawOval(-bw * 0.7f, cy + bh * 0.25f, -bw * 0.38f, cy + bh * 0.48f, fill)
            c.drawOval(bw * 0.35f, cy - bh * 0.35f, bw * 0.6f, cy - bh * 0.18f, fill)
            c.drawOval(bw * 0.1f, cy + bh * 0.55f, bw * 0.34f, cy + bh * 0.72f, fill)
        }
        // Ambient occlusion towards the floor
        shaderFill(
            LinearGradient(
                0f, bottom - bh * 0.62f, 0f, bottom,
                Colors.alpha(col.shade, 0f), Colors.alpha(Colors.darken(col.shade, 0.25f), 0.55f), Shader.TileMode.CLAMP,
            ),
        )
        c.drawRect(-bw * 1.5f, bottom - bh * 0.65f, bw * 1.5f, bottom + bh, fill)
        fill.shader = null
        // Bounce light: bright crescent on the lower right edge
        path2.set(body)
        tmp.set(body)
        tmp.offset(-bw * 0.07f, -bh * 0.06f)
        if (path2.op(tmp, Path.Op.DIFFERENCE)) {
            fill.color = Colors.alpha(col.light, 0.4f)
            c.drawPath(path2, fill)
        }
        c.restore()

        // Specular highlights
        gloss(c, lx, top + bh * 0.5f, bw * 0.42f, bh * 0.26f, 0.75f)
        fill.color = Colors.alpha(Color.WHITE, 0.92f)
        c.save()
        c.rotate(-28f, lx - bw * 0.05f, top + bh * 0.36f)
        c.drawOval(lx - bw * 0.17f, top + bh * 0.3f, lx + bw * 0.03f, top + bh * 0.42f, fill)
        c.restore()
        fill.color = Colors.alpha(Color.WHITE, 0.6f)
        c.drawCircle(lx + bw * 0.12f, top + bh * 0.26f, bw * 0.035f, fill)

        outline(c, body, col.shade)
    }

    private fun drawAura(c: Canvas, bw: Float, bh: Float, cy: Float, t: Float, color: Int) {
        val r = bw * 2.2f * (1f + 0.05f * sin(t * 2f))
        shaderFill(radial(0f, cy, r, intArrayOf(Colors.alpha(color, 0.45f), Colors.alpha(color, 0.12f), Colors.alpha(color, 0f)), floatArrayOf(0f, 0.6f, 1f)))
        c.drawCircle(0f, cy, r, fill)
        fill.shader = null
    }

    private fun drawSparkles(c: Canvas, bw: Float, bh: Float, cy: Float, t: Float) {
        val pts = floatArrayOf(-1.35f, -0.9f, 1.3f, -0.6f, -1.15f, 0.5f, 1.4f, 0.35f)
        for (i in 0 until 4) {
            val a = (sin(t * 2.2f + i * 1.7f) + 1f) / 2f
            val x = pts[i * 2] * bw
            val y = cy + pts[i * 2 + 1] * bh
            val r = u * (0.05f + 0.04f * a)
            shaderFill(radial(x, y, r * 2.2f, intArrayOf(Colors.alpha(0xFFFFF3B0.toInt(), 0.6f * a), Colors.alpha(0xFFFFF3B0.toInt(), 0f))))
            c.drawCircle(x, y, r * 2.2f, fill)
            fill.shader = null
            fill.color = Colors.alpha(0xFFFFF7D6.toInt(), 0.4f + 0.6f * a)
            Shapes.sparkle(path3, x, y, r)
            c.drawPath(path3, fill)
        }
    }

    // ------------------------------------------------------------------ limbs

    private fun drawFeet(c: Canvas, col: PetColors, pose: PetPose, bw: Float, footH: Float) {
        val fw = u * 0.2f
        val base = Colors.lerp(col.body, col.shade, 0.3f)
        for (s in intArrayOf(-1, 1)) {
            val lift = if (s < 0) pose.footL else pose.footR
            val stride = if (s < 0) pose.strideL else pose.strideR
            val x = s * bw * 0.42f + stride * u * 0.1f
            val y = -footH * 0.5f - lift * u * 0.1f
            ovalPath(path, x - fw * 0.5f, y - footH * 0.55f, x + fw * 0.5f, y + footH * 0.5f)
            shaded(c, path, Colors.lighten(base, 0.3f), base, Colors.darken(col.shade, 0.1f))
        }
    }

    private fun drawArms(c: Canvas, col: PetColors, pose: PetPose, bw: Float, bh: Float, cy: Float, fx: Float) {
        val len = u * 0.26f
        val th = u * 0.13f
        for (s in intArrayOf(-1, 1)) {
            val raise = if (s < 0) pose.armL else pose.armR
            val wave = if (s < 0) pose.armWaveL else pose.armWaveR
            val px = s * bw * 0.9f + fx * 0.2f
            val py = cy + bh * 0.22f
            // rest points outward-down, raise lifts the arm up over the head, hold brings hands to the front
            var angle = 28f + raise * 125f + wave
            angle = angle * (1f - pose.hold) + (-40f) * pose.hold
            c.save()
            c.translate(px, py)
            c.rotate(-s * angle)
            ovalPath(path, -th * 0.5f, -th * 0.35f, th * 0.5f, len)
            shaded(c, path, col.light, col.body, col.shade)
            c.restore()
        }
    }

    // ------------------------------------------------------------------ back parts

    private fun drawBack(c: Canvas, fl: FormLook, look: PetLook, pose: PetPose, bw: Float, bh: Float, top: Float, cy: Float, fx: Float) {
        val col = fl.colors
        val t = pose.t
        val lag = pose.earLag

        if (fl.tail == Tail.DRAGON) {
            val side = if (pose.turn > 0.05f) -1f else 1f
            val wag = sin(t * 3f) * bh * 0.14f
            path.reset()
            path.moveTo(side * bw * 0.72f, cy + bh * 0.3f)
            path.quadTo(side * bw * 1.3f, cy + bh * 0.45f, side * bw * 1.38f, cy - bh * 0.1f + wag)
            path.quadTo(side * bw * 1.15f, cy + bh * 0.75f, side * bw * 0.6f, cy + bh * 0.8f)
            path.close()
            shaded(c, path, col.light, col.body, col.shade)
            val tx = side * bw * 1.38f
            val ty = cy - bh * 0.1f + wag
            path.reset()
            path.moveTo(tx, ty - u * 0.17f)
            path.quadTo(tx + u * 0.13f, ty - u * 0.02f, tx, ty + u * 0.07f)
            path.quadTo(tx - u * 0.13f, ty - u * 0.02f, tx, ty - u * 0.17f)
            path.close()
            shaded(c, path, Colors.lighten(col.accent, 0.4f), col.accent, Colors.darken(col.accent, 0.3f))
        }

        val flying = pose.lift > 0.05f
        val flap = sin(t * (if (flying) 16f else 3f)) * (if (flying) 1f else 0.35f)
        when (fl.wings) {
            Wings.NONE -> Unit
            Wings.SMALL -> for (s in intArrayOf(-1, 1)) {
                c.save()
                c.translate(s * bw * 0.86f, cy - bh * 0.1f)
                c.rotate(s * (-16f + flap * 20f))
                val w = u * 0.44f
                ovalPath(path, if (s > 0) 0f else -w, -u * 0.13f, if (s > 0) w else 0f, u * 0.1f)
                shaded(c, path, Color.WHITE, col.accent2, Colors.darken(col.accent2, 0.18f))
                ovalPath(path, if (s > 0) 0f else -w * 0.7f, 0f, if (s > 0) w * 0.7f else 0f, u * 0.17f)
                shaded(c, path, Color.WHITE, col.accent2, Colors.darken(col.accent2, 0.18f))
                c.restore()
            }
            Wings.DRAGON -> for (s in intArrayOf(-1, 1)) {
                val sf = s.toFloat()
                c.save()
                c.translate(sf * bw * 0.72f, cy - bh * 0.25f)
                c.rotate(sf * (flap * 16f - 6f))
                path.reset()
                path.moveTo(0f, 0f)
                path.quadTo(sf * u * 0.2f, -u * 0.5f, sf * u * 0.4f, -u * 0.58f)
                path.quadTo(sf * u * 0.66f, -u * 0.6f, sf * u * 0.86f, -u * 0.48f)
                path.quadTo(sf * u * 0.74f, -u * 0.26f, sf * u * 0.88f, -u * 0.05f)
                path.quadTo(sf * u * 0.66f, -u * 0.1f, sf * u * 0.56f, u * 0.1f)
                path.quadTo(sf * u * 0.42f, -u * 0.02f, sf * u * 0.28f, u * 0.15f)
                path.quadTo(sf * u * 0.14f, u * 0.02f, 0f, u * 0.13f)
                path.close()
                shaded(c, path, Colors.lighten(col.accent2, 0.35f), col.accent2, Colors.darken(col.accent2, 0.3f))
                path3.reset()
                path3.moveTo(sf * u * 0.4f, -u * 0.56f)
                path3.lineTo(sf * u * 0.56f, u * 0.07f)
                path3.moveTo(sf * u * 0.4f, -u * 0.56f)
                path3.lineTo(sf * u * 0.28f, u * 0.12f)
                stroke(c, path3, Colors.alpha(Colors.darken(col.accent2, 0.35f), 0.7f), u * 0.025f)
                c.restore()
            }
            Wings.ANGEL -> for (s in intArrayOf(-1, 1)) {
                val sf = s.toFloat()
                c.save()
                c.translate(sf * bw * 0.8f, cy - bh * 0.2f)
                c.rotate(sf * flap * 10f)
                val lens = floatArrayOf(0.54f, 0.45f, 0.35f)
                val angles = floatArrayOf(-38f, -12f, 14f)
                for (i in 0..2) {
                    c.save()
                    c.rotate(sf * angles[i])
                    val l = u * lens[i]
                    ovalPath(path, if (s > 0) 0f else -l, -u * 0.085f, if (s > 0) l else 0f, u * 0.085f)
                    shaded(c, path, Color.WHITE, 0xFFF4F1FF.toInt(), 0xFFC9C3EE.toInt())
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
                    c.translate(s * bw * 0.34f + fx * 0.4f, top + bh * 0.22f)
                    val droop = if (look.sleeping || look.mood == de.knuffi.core.Mood.SAD) 62f else 12f
                    c.rotate(s * (droop + sin(t * 2f + s) * 4f) + lag * 0.8f)
                    ovalPath(path, -earW, -earH * 2f, earW, 0f)
                    shaded(c, path, col.light, col.body, col.shade)
                    ovalPath(path2, -earW * 0.48f, -earH * 1.78f, earW * 0.48f, -earH * 0.3f)
                    shaded(c, path2, Colors.lighten(col.accent, 0.35f), col.accent, Colors.darken(col.accent, 0.15f), outline = false)
                    c.restore()
                }
            }
            Ears.CAT -> for (s in intArrayOf(-1, 1)) {
                val sf = s.toFloat()
                c.save()
                c.rotate(lag * 0.5f, sf * bw * 0.55f + fx * 0.4f, top + bh * 0.3f)
                path.reset()
                path.moveTo(sf * bw * 0.14f + fx * 0.4f, top + bh * 0.1f)
                path.quadTo(sf * bw * 0.46f + fx * 0.4f, top - bh * 0.62f, sf * bw * 0.7f + fx * 0.4f, top - bh * 0.36f)
                path.quadTo(sf * bw * 0.86f + fx * 0.4f, top - bh * 0.12f, sf * bw * 0.9f + fx * 0.4f, top + bh * 0.45f)
                path.close()
                shaded(c, path, col.light, col.body, col.shade)
                path.reset()
                path.moveTo(sf * bw * 0.34f + fx * 0.4f, top + bh * 0.05f)
                path.quadTo(sf * bw * 0.52f + fx * 0.4f, top - bh * 0.35f, sf * bw * 0.66f + fx * 0.4f, top - bh * 0.2f)
                path.quadTo(sf * bw * 0.76f + fx * 0.4f, top, sf * bw * 0.76f + fx * 0.4f, top + bh * 0.3f)
                path.close()
                shaded(c, path, Colors.lighten(col.accent, 0.3f), col.accent, Colors.darken(col.accent, 0.2f), outline = false)
                c.restore()
            }
            Ears.ROUND -> for (s in intArrayOf(-1, 1)) {
                val r = u * 0.17f
                val x = s * bw * 0.6f + fx * 0.35f
                val y = top + bh * 0.24f
                ovalPath(path, x - r, y - r, x + r, y + r)
                shaded(c, path, col.light, col.body, col.shade)
                ovalPath(path2, x - r * 0.55f, y - r * 0.55f, x + r * 0.55f, y + r * 0.55f)
                shaded(c, path2, Colors.lighten(col.accent, 0.35f), col.accent, Colors.darken(col.accent, 0.2f), outline = false)
            }
        }

        if (fl.spikes) {
            for (i in -2..2) {
                val x = i * bw * 0.3f + fx * 0.3f
                val nx = i * 0.3f
                val y = top + bh * 2f * 0.26f * nx * nx + bh * 0.08f
                Shapes.softTriangle(path, x, y + bh * 0.1f, bw * 0.15f, bh * 0.42f, lean = i * bw * 0.08f)
                shaded(c, path, Colors.lighten(col.accent, 0.35f), col.accent, Colors.darken(col.accent, 0.3f))
            }
        }

        if (fl.horns) {
            for (s in intArrayOf(-1, 1)) {
                val sf = s.toFloat()
                path.reset()
                path.moveTo(sf * bw * 0.16f + fx * 0.4f, top + bh * 0.12f)
                path.quadTo(sf * bw * 0.3f + fx * 0.4f, top - bh * 0.35f, sf * bw * 0.64f + fx * 0.4f, top - bh * 0.52f)
                path.quadTo(sf * bw * 0.5f + fx * 0.4f, top - bh * 0.1f, sf * bw * 0.48f + fx * 0.4f, top + bh * 0.25f)
                path.close()
                shaded(c, path, Colors.lighten(col.accent, 0.45f), col.accent, Colors.darken(col.accent, 0.3f))
            }
        }

        when (fl.antenna) {
            Antenna.NONE -> Unit
            Antenna.SPROUT -> {
                val baseX = fx * 0.4f
                c.save()
                c.rotate(sin(t * 1.6f) * 8f + lag, baseX, top)
                path.reset()
                path.moveTo(baseX, top + bh * 0.06f)
                path.quadTo(baseX + u * 0.02f, top - u * 0.14f, baseX + u * 0.05f, top - u * 0.22f)
                stroke(c, path, Colors.darken(col.accent, 0.25f), u * 0.045f)
                for (s in intArrayOf(-1, 1)) {
                    c.save()
                    c.translate(baseX + u * 0.05f, top - u * 0.22f)
                    c.rotate(s * 38f)
                    ovalPath(path, if (s > 0) 0f else -u * 0.17f, -u * 0.055f, if (s > 0) u * 0.17f else 0f, u * 0.055f)
                    shaded(c, path, Colors.lighten(col.accent, 0.45f), col.accent, Colors.darken(col.accent, 0.25f))
                    c.restore()
                }
                c.restore()
            }
            Antenna.GLOW, Antenna.STAR -> {
                val baseX = fx * 0.4f
                val sway = sin(t * 1.3f) * u * 0.04f + lag * u * 0.004f
                val tipX = baseX + sway
                val tipY = top - u * 0.34f
                path.reset()
                path.moveTo(baseX, top + bh * 0.06f)
                path.quadTo(baseX - u * 0.05f, top - u * 0.18f, tipX, tipY)
                stroke(c, path, Colors.darken(col.body, 0.28f), u * 0.04f)
                val pulse = (sin(t * 2.5f) + 1f) / 2f
                val gr = u * (0.28f + 0.07f * pulse)
                shaderFill(radial(tipX, tipY, gr, intArrayOf(Colors.alpha(col.accent, 0.75f), Colors.alpha(col.accent, 0.25f), Colors.alpha(col.accent, 0f)), floatArrayOf(0f, 0.4f, 1f)))
                c.drawCircle(tipX, tipY, gr, fill)
                fill.shader = null
                if (fl.antenna == Antenna.GLOW) {
                    ovalPath(path, tipX - u * 0.1f, tipY - u * 0.1f, tipX + u * 0.1f, tipY + u * 0.1f)
                    shaded(c, path, Color.WHITE, Colors.lerp(col.accent, Color.WHITE, 0.3f * pulse), Colors.darken(col.accent, 0.15f), outline = false)
                } else {
                    Shapes.softStar(path, tipX, tipY, u * 0.16f, u * 0.08f, 5, t * 30f)
                    shaded(c, path, Color.WHITE, col.accent, Colors.darken(col.accent, 0.25f), outline = false)
                }
            }
        }
    }

    // ------------------------------------------------------------------ front parts

    private fun drawFront(c: Canvas, fl: FormLook, look: PetLook, pose: PetPose, bw: Float, bh: Float, top: Float, cy: Float, fx: Float) {
        val col = fl.colors
        if (fl.headband) {
            c.save()
            c.clipPath(body)
            shaderFill(LinearGradient(0f, top + bh * 0.28f, 0f, top + bh * 0.52f, Colors.lighten(col.accent, 0.3f), Colors.darken(col.accent, 0.2f), Shader.TileMode.CLAMP))
            c.drawRect(-bw * 2f, top + bh * 0.28f, bw * 2f, top + bh * 0.5f, fill)
            fill.shader = null
            fill.color = Colors.alpha(Color.WHITE, 0.55f)
            c.drawRect(-bw * 2f, top + bh * 0.34f, bw * 2f, top + bh * 0.37f, fill)
            c.restore()
            val side = if (pose.turn > 0.05f) -1f else 1f
            path.reset()
            path.moveTo(side * bw * 0.9f, top + bh * 0.38f)
            path.quadTo(side * bw * 1.2f, top + bh * 0.1f, side * bw * 1.3f, top + bh * 0.2f + sin(pose.t * 6f) * bh * 0.05f)
            path.quadTo(side * bw * 1.2f, top + bh * 0.45f, side * bw * 0.92f, top + bh * 0.5f)
            path.close()
            shaded(c, path, Colors.lighten(col.accent, 0.3f), col.accent, Colors.darken(col.accent, 0.25f))
        }
        if (fl.crown && look.hat == null) drawCrown(c, top + bh * 0.1f, bw * 0.42f, u * 0.3f, fx)
        if (pose.nightCap > 0.01f && look.hat == null) drawNightCap(c, top, bw, bh, fx, pose)
    }

    private fun drawCrown(c: Canvas, base: Float, w: Float, h: Float, fx: Float) {
        val x0 = fx * 0.4f
        path.reset()
        path.moveTo(x0 - w, base)
        path.lineTo(x0 - w, base - h * 0.6f)
        path.lineTo(x0 - w * 0.5f, base - h * 0.2f)
        path.lineTo(x0, base - h)
        path.lineTo(x0 + w * 0.5f, base - h * 0.2f)
        path.lineTo(x0 + w, base - h * 0.6f)
        path.lineTo(x0 + w, base)
        path.close()
        shaded(c, path, 0xFFFFF4C2.toInt(), 0xFFFFC83D.toInt(), 0xFFD48A00.toInt())
        gem(c, x0, base - h * 0.35f, h * 0.13f, 0xFFFF5C7A.toInt())
        gem(c, x0 - w * 0.62f, base - h * 0.22f, h * 0.09f, 0xFF4D8DFF.toInt())
        gem(c, x0 + w * 0.62f, base - h * 0.22f, h * 0.09f, 0xFF4D8DFF.toInt())
        gem(c, x0, base - h, h * 0.1f, 0xFFFFF3C4.toInt())
    }

    private fun gem(c: Canvas, x: Float, y: Float, r: Float, color: Int) {
        ovalPath(path2, x - r, y - r, x + r, y + r)
        shaded(c, path2, Colors.lighten(color, 0.6f), color, Colors.darken(color, 0.35f), outline = false)
    }

    private fun drawNightCap(c: Canvas, top: Float, bw: Float, bh: Float, fx: Float, pose: PetPose) {
        val a = pose.nightCap
        c.save()
        c.translate(fx * 0.3f + bw * 0.1f, top + bh * 0.22f)
        c.rotate(18f + sin(pose.t) * 3f)
        c.scale(a, a)
        path.reset()
        path.moveTo(-bw * 0.6f, 0f)
        path.quadTo(-bw * 0.2f, -u * 0.55f, bw * 0.75f, -u * 0.62f)
        path.quadTo(bw * 0.2f, -u * 0.25f, bw * 0.6f, 0f)
        path.close()
        shaded(c, path, 0xFFB9C6FF.toInt(), 0xFF7C8CF0.toInt(), 0xFF4C58B8.toInt())
        ovalPath(path, -bw * 0.66f, -u * 0.08f, bw * 0.66f, u * 0.08f)
        shaded(c, path, Color.WHITE, 0xFFF1F3FF.toInt(), 0xFFC7CCE8.toInt())
        ovalPath(path, bw * 0.75f - u * 0.08f, -u * 0.7f, bw * 0.75f + u * 0.08f, -u * 0.54f)
        shaded(c, path, Color.WHITE, 0xFFF1F3FF.toInt(), 0xFFC7CCE8.toInt())
        c.restore()
    }

    private fun drawFoam(c: Canvas, pose: PetPose, bw: Float, top: Float, fx: Float) {
        val n = (pose.foam * 9).toInt().coerceIn(1, 9)
        val offs = floatArrayOf(0f, -0.4f, 0.4f, -0.2f, 0.25f, -0.62f, 0.6f, 0f, -0.1f)
        val ups = floatArrayOf(0.02f, 0.1f, 0.1f, 0.2f, 0.22f, 0.18f, 0.2f, 0.32f, 0.12f)
        for (i in 0 until n) {
            val x = fx * 0.4f + offs[i] * bw + sin(pose.t * 2f + i) * u * 0.02f
            val y = top + u * 0.05f - ups[i] * u
            val r = u * (0.12f + (i % 3) * 0.03f) * pose.foam.coerceIn(0.4f, 1f)
            shaderFill(radial(x - r * 0.3f, y - r * 0.35f, r * 1.3f, intArrayOf(Color.WHITE, 0xFFF6FBFF.toInt(), 0xFFC6E3F7.toInt()), floatArrayOf(0f, 0.6f, 1f)))
            c.drawCircle(x, y, r, fill)
            fill.shader = null
            fill.color = Colors.alpha(Color.WHITE, 0.95f)
            c.drawCircle(x - r * 0.35f, y - r * 0.35f, r * 0.18f, fill)
        }
    }

    // ------------------------------------------------------------------ face

    private fun drawFace(c: Canvas, fl: FormLook, pose: PetPose, bw: Float, bh: Float, cy: Float, fx: Float) {
        val col = fl.colors
        val turn = pose.turn
        val re = u * 0.135f * (0.92f + 0.08f * fl.width)
        val ex = bw * 0.36f * (1f - 0.1f * abs(turn))
        val ey = cy - bh * 0.1f
        val lx = pose.lookX.coerceIn(-1f, 1f) * re * 0.22f
        val ly = pose.lookY.coerceIn(-1f, 1f) * re * 0.18f

        // Cheeks
        val blush = pose.blush.coerceIn(0f, 1f)
        if (blush > 0.02f) {
            for (s in intArrayOf(-1, 1)) {
                val x = fx + s * bw * 0.6f * (1f - 0.1f * abs(turn))
                val y = cy + bh * 0.14f
                val r = re * 1.05f
                c.save()
                c.scale(1f, 0.6f, x, y)
                shaderFill(radial(x, y, r, intArrayOf(Colors.alpha(col.cheek, 0.85f * blush), Colors.alpha(col.cheek, 0f))))
                c.drawCircle(x, y, r, fill)
                fill.shader = null
                c.restore()
            }
        }

        for (s in intArrayOf(-1, 1)) {
            val near = if (turn * s > 0) 1f + 0.06f * abs(turn) else 1f - 0.1f * abs(turn)
            val x = fx + s * ex
            drawEye(c, fl, pose, x, ey, re * near, lx, ly, s)
        }

        // Brows
        if (abs(pose.brows) > 0.05f && pose.eyes != EyeShape.CLOSED) {
            for (s in intArrayOf(-1, 1)) {
                val sf = s.toFloat()
                val x = fx + sf * ex
                path.reset()
                if (pose.brows < 0f) {
                    path.moveTo(x + sf * re * 0.75f, ey - re * 1.15f)
                    path.lineTo(x - sf * re * 0.55f, ey - re * 1.55f)
                } else {
                    path.moveTo(x + sf * re * 0.85f, ey - re * 1.55f)
                    path.lineTo(x - sf * re * 0.6f, ey - re * 1.08f)
                }
                stroke(c, path, col.eye, re * 0.22f)
            }
        }

        // Tear
        if (pose.tear > 0.01f) {
            val tt = (pose.t % 2.2f) / 2.2f
            val x = fx - ex - re * 0.2f
            val y = ey + re * 1.1f + tt * re * 2.4f
            Shapes.drop(path, x, y, re * 0.3f)
            shaderFill(radial(x - re * 0.1f, y - re * 0.2f, re * 0.5f, intArrayOf(Colors.alpha(Color.WHITE, 1f - tt), Colors.alpha(0xFF7CC8FF.toInt(), 1f - tt))))
            c.drawPath(path, fill)
            fill.shader = null
        }

        drawMouth(c, col, pose, fx, cy + bh * 0.26f, u * 0.075f)
    }

    private fun drawEye(c: Canvas, fl: FormLook, pose: PetPose, x: Float, y: Float, re: Float, lx: Float, ly: Float, side: Int) {
        val col = fl.colors
        var shape = pose.eyes
        if (fl.eyes == EyeStyle.GLOW && (shape == EyeShape.OPEN || shape == EyeShape.SQUINT || shape == EyeShape.SLEEPY)) {
            drawGlowEye(c, col, x, y, re, pose.eyeOpen)
            return
        }
        val open = when (shape) {
            EyeShape.SQUINT -> 0.5f * pose.eyeOpen
            EyeShape.SLEEPY -> 0.35f * pose.eyeOpen
            else -> pose.eyeOpen
        }
        if ((shape == EyeShape.OPEN || shape == EyeShape.SQUINT || shape == EyeShape.SLEEPY || shape == EyeShape.WIDE) && open < 0.18f) {
            shape = EyeShape.CLOSED
        }
        when (shape) {
            EyeShape.OPEN, EyeShape.SQUINT, EyeShape.SLEEPY -> {
                val w = re * 0.8f
                val h = re * 1.02f * open
                ovalPath(path, x - w, y - h, x + w, y + h)
                shaderFill(LinearGradient(0f, y - h, 0f, y + h, col.eye, Colors.lerp(col.eye, col.iris, 0.45f), Shader.TileMode.CLAMP))
                c.drawPath(path, fill)
                fill.shader = null
                c.save()
                c.clipPath(path)
                shaderFill(
                    radial(
                        x + lx, y + h * 0.5f + ly, w * 1.05f,
                        intArrayOf(Colors.lighten(col.iris, 0.35f), col.iris, Colors.alpha(col.iris, 0f)), floatArrayOf(0f, 0.45f, 1f),
                    ),
                )
                c.drawCircle(x + lx, y + h * 0.5f + ly, w * 1.05f, fill)
                fill.shader = null
                c.restore()
                // highlights
                fill.color = Color.WHITE
                val hx = x + w * 0.3f + lx * 0.6f
                val hy = y - h * 0.4f + ly * 0.5f
                c.drawOval(hx - w * 0.36f, hy - w * 0.32f * max(open, 0.5f), hx + w * 0.36f, hy + w * 0.32f * max(open, 0.5f), fill)
                fill.color = Colors.alpha(Color.WHITE, 0.9f)
                c.drawCircle(x - w * 0.32f + lx * 0.3f, y + h * 0.45f, w * 0.14f, fill)
                path2.reset()
                path2.addArc(x - w * 0.62f, y - h * 0.62f, x + w * 0.62f, y + h * 0.78f, 30f, 120f)
                stroke(c, path2, Colors.alpha(Color.WHITE, 0.35f), re * 0.08f)
                if (shape != EyeShape.OPEN) {
                    // heavy lid line
                    path2.reset()
                    path2.moveTo(x - w * 1.05f, y - h * 0.95f)
                    path2.quadTo(x, y - h * 1.25f, x + w * 1.05f, y - h * 0.95f)
                    stroke(c, path2, col.eye, re * 0.2f)
                }
            }
            EyeShape.WIDE -> {
                val w = re * 0.85f
                val h = re * 1.08f
                ovalPath(path, x - w, y - h, x + w, y + h)
                shaded(c, path, Color.WHITE, 0xFFF7F4FF.toInt(), 0xFFCEC6E4.toInt())
                val r = w * 0.48f
                ovalPath(path2, x - r + lx, y - r * 1.1f + ly, x + r + lx, y + r * 1.1f + ly)
                shaderFill(radial(x + lx, y + ly + r * 0.4f, r * 1.3f, intArrayOf(col.iris, col.eye)))
                c.drawPath(path2, fill)
                fill.shader = null
                fill.color = Color.WHITE
                c.drawCircle(x + lx + r * 0.35f, y + ly - r * 0.4f, r * 0.32f, fill)
            }
            EyeShape.CLOSED -> {
                path.reset()
                path.moveTo(x - re * 0.7f, y)
                path.quadTo(x, y + re * 0.75f, x + re * 0.7f, y)
                stroke(c, path, col.eye, re * 0.26f)
                if (pose.lying > 0.5f) {
                    // lashes
                    path.reset()
                    path.moveTo(x + side * re * 0.62f, y + re * 0.12f)
                    path.lineTo(x + side * re * 0.9f, y + re * 0.02f)
                    stroke(c, path, col.eye, re * 0.16f)
                }
            }
            EyeShape.HAPPY -> {
                path.reset()
                path.moveTo(x - re * 0.72f, y + re * 0.32f)
                path.quadTo(x, y - re * 0.9f, x + re * 0.72f, y + re * 0.32f)
                stroke(c, path, col.eye, re * 0.3f)
            }
            EyeShape.HEART -> {
                Shapes.heart(path, x, y + re * 0.05f, re * 1.3f)
                shaded(c, path, 0xFFFFB3CF.toInt(), 0xFFFF4F8B.toInt(), 0xFFC21F5A.toInt(), outline = false)
                fill.color = Colors.alpha(Color.WHITE, 0.85f)
                c.drawCircle(x - re * 0.38f, y - re * 0.32f, re * 0.2f, fill)
            }
            EyeShape.STAR -> {
                Shapes.softStar(path, x, y, re * 1.05f, re * 0.5f, 5, 0f)
                shaded(c, path, 0xFFFFF6C8.toInt(), 0xFFFFC83D.toInt(), 0xFFE08A00.toInt(), outline = false)
                fill.color = Colors.alpha(Color.WHITE, 0.9f)
                c.drawCircle(x - re * 0.25f, y - re * 0.25f, re * 0.16f, fill)
            }
            EyeShape.DIZZY -> {
                Shapes.spiral(path, x, y, re * 0.8f, 2.2f, pose.t * 8f * side)
                stroke(c, path, col.eye, re * 0.16f)
            }
        }
    }

    private fun drawGlowEye(c: Canvas, col: PetColors, x: Float, y: Float, re: Float, open: Float) {
        val h = re * max(open, 0.12f)
        shaderFill(radial(x, y, re * 2.2f, intArrayOf(Colors.alpha(col.eye, 0.55f), Colors.alpha(col.eye, 0f))))
        c.drawCircle(x, y, re * 2.2f, fill)
        fill.shader = null
        ovalPath(path, x - re * 0.7f, y - h, x + re * 0.7f, y + h)
        shaded(c, path, Color.WHITE, col.eye, Colors.darken(col.eye, 0.2f), outline = false)
    }

    private fun drawMouth(c: Canvas, col: PetColors, pose: PetPose, x: Float, y: Float, m: Float) {
        val dark = 0xFF6E2440.toInt()
        val deep = 0xFF3E0F22.toInt()
        val tongue = 0xFFFF8FA3.toInt()
        var shape = pose.mouth
        if (shape == MouthShape.CHEW) shape = if (pose.mouthOpen > 0.5f) MouthShape.O else MouthShape.CAT
        when (shape) {
            MouthShape.CAT -> {
                path.reset()
                path.moveTo(x - m, y)
                path.quadTo(x - m * 0.5f, y + m * 0.95f, x, y)
                path.quadTo(x + m * 0.5f, y + m * 0.95f, x + m, y)
                stroke(c, path, col.eye, m * 0.4f)
            }
            MouthShape.SMILE -> {
                path.reset()
                path.moveTo(x - m * 1.1f, y)
                path.quadTo(x, y + m * 1.1f, x + m * 1.1f, y)
                stroke(c, path, col.eye, m * 0.4f)
            }
            MouthShape.OPEN, MouthShape.GRIN -> {
                val wide = if (shape == MouthShape.GRIN) 1.25f else 1f
                path.reset()
                path.moveTo(x - m * 1.35f * wide, y - m * 0.15f)
                path.cubicTo(x - m * 1.2f * wide, y + m * 1.9f, x + m * 1.2f * wide, y + m * 1.9f, x + m * 1.35f * wide, y - m * 0.15f)
                path.close()
                shaderFill(LinearGradient(0f, y, 0f, y + m * 1.6f, deep, dark, Shader.TileMode.CLAMP))
                c.drawPath(path, fill)
                fill.shader = null
                c.save()
                c.clipPath(path)
                ovalPath(path2, x - m * 0.85f, y + m * 0.8f, x + m * 0.85f, y + m * 1.9f)
                shaded(c, path2, 0xFFFFC2CD.toInt(), tongue, 0xFFE0607A.toInt(), outline = false)
                if (shape == MouthShape.GRIN) {
                    fill.color = Color.WHITE
                    c.drawRect(x - m * 1.4f, y - m * 0.3f, x + m * 1.4f, y + m * 0.28f, fill)
                }
                c.restore()
            }
            MouthShape.O, MouthShape.YAWN -> {
                val big = if (shape == MouthShape.YAWN) 1f else 0.45f + 0.35f * pose.mouthOpen
                val w = m * 0.85f * big
                val h = m * 1.3f * big
                ovalPath(path, x - w, y - h * 0.4f, x + w, y + h)
                shaderFill(LinearGradient(0f, y - h * 0.4f, 0f, y + h, deep, dark, Shader.TileMode.CLAMP))
                c.drawPath(path, fill)
                fill.shader = null
                if (shape == MouthShape.YAWN) {
                    c.save()
                    c.clipPath(path)
                    ovalPath(path2, x - w * 0.7f, y + h * 0.35f, x + w * 0.7f, y + h * 1.2f)
                    shaded(c, path2, 0xFFFFC2CD.toInt(), tongue, 0xFFE0607A.toInt(), outline = false)
                    c.restore()
                }
            }
            MouthShape.FROWN -> {
                path.reset()
                path.moveTo(x - m, y + m * 0.6f)
                path.quadTo(x, y - m * 0.5f, x + m, y + m * 0.6f)
                stroke(c, path, col.eye, m * 0.4f)
            }
            MouthShape.WAVY -> {
                path.reset()
                path.moveTo(x - m * 1.2f, y)
                path.quadTo(x - m * 0.6f, y - m * 0.55f, x, y)
                path.quadTo(x + m * 0.6f, y + m * 0.55f, x + m * 1.2f, y)
                stroke(c, path, col.eye, m * 0.36f)
            }
            MouthShape.TONGUE -> {
                ovalPath(path2, x + m * 0.05f, y + m * 0.1f, x + m * 0.85f, y + m * 1.2f)
                shaded(c, path2, 0xFFFFC2CD.toInt(), tongue, 0xFFE0607A.toInt(), outline = false)
                path.reset()
                path.moveTo(x - m * 1.1f, y)
                path.quadTo(x, y + m * 1.0f, x + m * 1.1f, y)
                stroke(c, path, col.eye, m * 0.4f)
            }
            MouthShape.FLAT -> {
                path.reset()
                path.moveTo(x - m * 0.8f, y + m * 0.2f)
                path.lineTo(x + m * 0.8f, y + m * 0.2f)
                stroke(c, path, col.eye, m * 0.38f)
            }
            MouthShape.CHEW -> Unit
        }
    }

    // ------------------------------------------------------------------ accessories

    private fun drawAccessories(c: Canvas, look: PetLook, pose: PetPose, bw: Float, bh: Float, top: Float, cy: Float, fx: Float) {
        val re = u * 0.135f
        val ex = bw * 0.36f * (1f - 0.1f * abs(pose.turn))
        val ey = cy - bh * 0.1f
        val t = pose.t
        val neckY = cy + bh * 0.55f

        when (look.neck) {
            "neck_bowtie" -> {
                val x = fx * 0.6f
                for (s in intArrayOf(-1, 1)) {
                    val sf = s.toFloat()
                    path.reset()
                    path.moveTo(x, neckY)
                    path.quadTo(x + sf * u * 0.12f, neckY - u * 0.16f, x + sf * u * 0.23f, neckY - u * 0.1f)
                    path.lineTo(x + sf * u * 0.23f, neckY + u * 0.1f)
                    path.quadTo(x + sf * u * 0.12f, neckY + u * 0.16f, x, neckY)
                    path.close()
                    shaded(c, path, 0xFFFF9AAA.toInt(), 0xFFE0475F.toInt(), 0xFF9E2238.toInt())
                }
                gem(c, x, neckY, u * 0.055f, 0xFFB8324A.toInt())
            }
            "neck_scarf" -> {
                c.save()
                c.clipPath(body)
                shaderFill(LinearGradient(0f, neckY - bh * 0.18f, 0f, neckY + bh * 0.1f, 0xFFFF8A8A.toInt(), 0xFFD63B4B.toInt(), Shader.TileMode.CLAMP))
                c.drawRect(-bw * 2f, neckY - bh * 0.18f, bw * 2f, neckY + bh * 0.1f, fill)
                fill.shader = null
                fill.color = Colors.alpha(Color.WHITE, 0.55f)
                var sx = -bw
                while (sx < bw) {
                    c.drawRect(sx, neckY - bh * 0.18f, sx + bw * 0.1f, neckY + bh * 0.1f, fill)
                    sx += bw * 0.3f
                }
                c.restore()
                c.save()
                c.rotate(-8f + sin(t * 2f) * 5f, fx + bw * 0.35f, neckY)
                path.reset()
                path.addRoundRect(fx + bw * 0.26f, neckY - bh * 0.05f, fx + bw * 0.5f, neckY + bh * 0.6f, u * 0.05f, u * 0.05f, Path.Direction.CW)
                shaded(c, path, 0xFFFF9C9C.toInt(), 0xFFF0505F.toInt(), 0xFFB8323F.toInt())
                c.restore()
            }
            "neck_bell" -> {
                c.save()
                c.clipPath(body)
                shaderFill(LinearGradient(0f, neckY - bh * 0.1f, 0f, neckY, 0xFFFF7A8E.toInt(), 0xFFC0304A.toInt(), Shader.TileMode.CLAMP))
                c.drawRect(-bw * 2f, neckY - bh * 0.1f, bw * 2f, neckY, fill)
                fill.shader = null
                c.restore()
                val swing = sin(t * 4f) * u * 0.015f
                val bx = fx * 0.6f + swing
                val by = neckY + u * 0.07f
                ovalPath(path, bx - u * 0.09f, by - u * 0.09f, bx + u * 0.09f, by + u * 0.09f)
                shaded(c, path, 0xFFFFF4C2.toInt(), 0xFFFFC83D.toInt(), 0xFFC08000.toInt())
                path.reset()
                path.moveTo(bx - u * 0.045f, by + u * 0.03f)
                path.lineTo(bx + u * 0.045f, by + u * 0.03f)
                stroke(c, path, 0xFF8A5A00.toInt(), u * 0.02f)
            }
        }

        when (look.face) {
            "face_round" -> {
                for (s in intArrayOf(-1, 1)) {
                    val x = fx + s * ex
                    ovalPath(path, x - re * 1.45f, ey - re * 1.45f, x + re * 1.45f, ey + re * 1.45f)
                    fill.color = Colors.alpha(0xFFE6F4FF.toInt(), 0.22f)
                    c.drawPath(path, fill)
                    stroke(c, path, 0xFF3A3150.toInt(), u * 0.035f)
                    path2.reset()
                    path2.addArc(x - re * 1.1f, ey - re * 1.1f, x + re * 1.1f, ey + re * 1.1f, 200f, 60f)
                    stroke(c, path2, Colors.alpha(Color.WHITE, 0.8f), u * 0.02f)
                }
                path.reset()
                path.moveTo(fx - ex + re * 1.45f, ey)
                path.quadTo(fx, ey - re * 0.5f, fx + ex - re * 1.45f, ey)
                stroke(c, path, 0xFF3A3150.toInt(), u * 0.03f)
            }
            "face_sun" -> {
                for (s in intArrayOf(-1, 1)) {
                    val x = fx + s * ex
                    path.reset()
                    path.addRoundRect(x - re * 1.45f, ey - re * 0.95f, x + re * 1.45f, ey + re * 1.05f, re * 0.55f, re * 0.55f, Path.Direction.CW)
                    shaderFill(LinearGradient(0f, ey - re, 0f, ey + re, 0xFF4A4468.toInt(), 0xFF141022.toInt(), Shader.TileMode.CLAMP))
                    c.drawPath(path, fill)
                    fill.shader = null
                    path2.reset()
                    path2.moveTo(x - re * 0.9f, ey - re * 0.2f)
                    path2.lineTo(x - re * 0.3f, ey - re * 0.65f)
                    stroke(c, path2, Colors.alpha(Color.WHITE, 0.7f), re * 0.22f)
                }
                path.reset()
                path.moveTo(fx - ex + re * 1.4f, ey - re * 0.4f)
                path.lineTo(fx + ex - re * 1.4f, ey - re * 0.4f)
                stroke(c, path, 0xFF1F1B2E.toInt(), u * 0.04f)
            }
            "face_heart" -> {
                for (s in intArrayOf(-1, 1)) {
                    Shapes.heart(path, fx + s * ex, ey + re * 0.1f, re * 1.9f)
                    shaderFill(radial(fx + s * ex - re * 0.5f, ey - re * 0.4f, re * 2.2f, intArrayOf(Colors.alpha(0xFFFFB8D6.toInt(), 0.9f), Colors.alpha(0xFFFF4F8B.toInt(), 0.85f))))
                    c.drawPath(path, fill)
                    fill.shader = null
                    outline(c, path, 0xFFC21F5A.toInt())
                    fill.color = Colors.alpha(Color.WHITE, 0.8f)
                    c.drawCircle(fx + s * ex - re * 0.6f, ey - re * 0.45f, re * 0.25f, fill)
                }
                path.reset()
                path.moveTo(fx - ex + re * 1.2f, ey - re * 0.3f)
                path.lineTo(fx + ex - re * 1.2f, ey - re * 0.3f)
                stroke(c, path, 0xFFD13A78.toInt(), u * 0.035f)
            }
        }

        val hx = fx * 0.45f
        when (look.hat) {
            "hat_party" -> {
                c.save()
                c.translate(hx + bw * 0.1f, top + bh * 0.12f)
                c.rotate(12f + pose.earLag * 0.3f)
                val hw = u * 0.22f
                val hh = u * 0.54f
                path.reset()
                path.moveTo(-hw, 0f)
                path.quadTo(0f, u * 0.06f, hw, 0f)
                path.lineTo(0f, -hh)
                path.close()
                shaded(c, path, 0xFFB7ADFF.toInt(), 0xFF7C6CFF.toInt(), 0xFF4A3BC4.toInt())
                c.save()
                c.clipPath(path)
                fill.color = 0xFFFFD166.toInt()
                c.drawRect(-hw, -hh * 0.36f, hw, -hh * 0.23f, fill)
                c.drawRect(-hw, -hh * 0.72f, hw, -hh * 0.6f, fill)
                c.restore()
                ovalPath(path, -u * 0.08f, -hh - u * 0.08f, u * 0.08f, -hh + u * 0.08f)
                shaded(c, path, 0xFFFFC2D6.toInt(), 0xFFFF6B9A.toInt(), 0xFFC23A66.toInt())
                c.restore()
            }
            "hat_bow" -> {
                c.save()
                c.translate(hx + bw * 0.42f, top + bh * 0.28f)
                c.rotate(-15f)
                for (s in intArrayOf(-1, 1)) {
                    val sf = s.toFloat()
                    path.reset()
                    path.moveTo(0f, 0f)
                    path.quadTo(sf * u * 0.12f, -u * 0.2f, sf * u * 0.24f, -u * 0.11f)
                    path.quadTo(sf * u * 0.28f, 0f, sf * u * 0.24f, u * 0.11f)
                    path.quadTo(sf * u * 0.12f, u * 0.18f, 0f, 0f)
                    path.close()
                    shaded(c, path, 0xFFFFB3D1.toInt(), 0xFFFF5C9A.toInt(), 0xFFC22F6B.toInt())
                }
                gem(c, 0f, 0f, u * 0.065f, 0xFFE0407E.toInt())
                c.restore()
            }
            "hat_flower" -> {
                val fxp = hx - bw * 0.45f
                val fyp = top + bh * 0.28f
                c.save()
                c.rotate(t * 12f, fxp, fyp)
                for (i in 0 until 5) {
                    val a = i * (Math.PI * 2 / 5).toFloat()
                    val px = fxp + cos(a) * u * 0.09f
                    val py = fyp + sin(a) * u * 0.09f
                    ovalPath(path, px - u * 0.08f, py - u * 0.08f, px + u * 0.08f, py + u * 0.08f)
                    shaded(c, path, Color.WHITE, 0xFFFFB3D9.toInt(), 0xFFE07AAE.toInt())
                }
                c.restore()
                gem(c, fxp, fyp, u * 0.065f, 0xFFFFD84D.toInt())
            }
            "hat_cap" -> {
                val base = top + bh * 0.16f
                rect.set(hx - bw * 0.72f, base - u * 0.36f, hx + bw * 0.72f, base + u * 0.36f)
                path.reset()
                path.arcTo(rect, 180f, 180f)
                path.close()
                shaded(c, path, 0xFF9CC2FF.toInt(), 0xFF4D8DFF.toInt(), 0xFF2A5BC0.toInt())
                val dir = if (pose.turn < -0.05f) -1f else 1f
                ovalPath(path, hx + dir * bw * 0.1f - (if (dir > 0) 0f else bw * 1.1f), base - u * 0.05f, hx + dir * bw * 0.1f + (if (dir > 0) bw * 1.1f else 0f), base + u * 0.08f)
                shaded(c, path, 0xFF6E9EF0.toInt(), 0xFF2F63C9.toInt(), 0xFF1D3F8A.toInt())
                gem(c, hx, base - u * 0.36f, u * 0.045f, 0xFF2F63C9.toInt())
            }
            "hat_tophat" -> {
                c.save()
                c.rotate(6f, hx, top)
                val base = top + bh * 0.1f
                path.reset()
                path.addRoundRect(hx - bw * 0.42f, base - u * 0.58f, hx + bw * 0.42f, base, u * 0.04f, u * 0.04f, Path.Direction.CW)
                shaded(c, path, 0xFF6A6488.toInt(), 0xFF2E2A3A.toInt(), 0xFF141119.toInt())
                shaderFill(LinearGradient(0f, base - u * 0.17f, 0f, base - u * 0.06f, 0xFFFF7A8E.toInt(), 0xFFB82B45.toInt(), Shader.TileMode.CLAMP))
                c.drawRect(hx - bw * 0.42f, base - u * 0.17f, hx + bw * 0.42f, base - u * 0.06f, fill)
                fill.shader = null
                ovalPath(path, hx - bw * 0.74f, base - u * 0.07f, hx + bw * 0.74f, base + u * 0.08f)
                shaded(c, path, 0xFF5A5470.toInt(), 0xFF2E2A3A.toInt(), 0xFF141119.toInt())
                c.restore()
            }
            "hat_wizard" -> {
                val base = top + bh * 0.14f
                path.reset()
                path.moveTo(hx - bw * 0.56f, base)
                path.quadTo(hx - bw * 0.1f, base - u * 0.5f, hx + bw * 0.44f + sin(t * 1.5f) * u * 0.03f, base - u * 0.88f)
                path.quadTo(hx + bw * 0.2f, base - u * 0.4f, hx + bw * 0.56f, base)
                path.close()
                shaded(c, path, 0xFF9C8FFF.toInt(), 0xFF5B4BD6.toInt(), 0xFF30248F.toInt())
                Shapes.softStar(path3, hx - bw * 0.05f, base - u * 0.26f, u * 0.08f, u * 0.035f)
                shaded(c, path3, Color.WHITE, 0xFFFFD95A.toInt(), 0xFFD9A000.toInt(), outline = false)
                Shapes.softStar(path3, hx + bw * 0.2f, base - u * 0.52f, u * 0.055f, u * 0.025f)
                shaded(c, path3, Color.WHITE, 0xFFFFD95A.toInt(), 0xFFD9A000.toInt(), outline = false)
                ovalPath(path, hx - bw * 0.82f, base - u * 0.07f, hx + bw * 0.82f, base + u * 0.08f)
                shaded(c, path, 0xFF7E70F0.toInt(), 0xFF4636B8.toInt(), 0xFF261A7A.toInt())
            }
            "hat_crown" -> drawCrown(c, top + bh * 0.1f, bw * 0.46f, u * 0.34f, fx)
        }

        // Held item (e.g. food) in the arms
        val item = pose.holding
        if (item != null && pose.hold > 0.3f) {
            emoji.textSize = u * 0.36f
            emoji.alpha = 255
            c.drawText(item, fx * 0.5f, cy + bh * 0.62f, emoji)
        }
    }

    // ------------------------------------------------------------------ egg

    private fun drawEgg(c: Canvas, cx: Float, groundY: Float, look: PetLook, pose: PetPose, silhouette: Int?) {
        val col = look.formLook.colors
        val ew = u * 0.6f * (1f - 0.02f * pose.breath)
        val eh = u * 0.78f * (1f + 0.02f * pose.breath)
        bounds.set(cx - ew * 1.4f, groundY - eh * 2.3f, cx + ew * 1.4f, groundY)
        head.set(cx, groundY - eh * 2f)
        val save = if (silhouette != null) {
            layerPaint.colorFilter = PorterDuffColorFilter(silhouette, PorterDuff.Mode.SRC_IN)
            c.saveLayer(null, layerPaint)
        } else c.save()
        c.translate(cx, groundY)
        c.rotate(pose.eggWobble)

        path.reset()
        path.moveTo(0f, -2f * eh)
        path.cubicTo(ew * 0.75f, -2f * eh, ew, -eh * 1.1f, ew, -eh * 0.7f)
        path.cubicTo(ew, -eh * 0.25f, ew * 0.6f, 0f, 0f, 0f)
        path.cubicTo(-ew * 0.6f, 0f, -ew, -eh * 0.25f, -ew, -eh * 0.7f)
        path.cubicTo(-ew, -eh * 1.1f, -ew * 0.75f, -2f * eh, 0f, -2f * eh)
        path.close()

        val taps = look.hatchTaps
        if (taps >= 3 && silhouette == null) {
            val pulse = (sin(pose.t * 6f) + 1f) / 2f
            val r = eh * (1.55f + 0.12f * pulse)
            shaderFill(radial(0f, -eh, r, intArrayOf(Colors.alpha(0xFFFFE680.toInt(), 0.6f), Colors.alpha(0xFFFFE680.toInt(), 0f))))
            c.drawCircle(0f, -eh, r, fill)
            fill.shader = null
        }

        shaderFill(radial(-ew * 0.35f, -eh * 1.45f, eh * 2.1f, intArrayOf(col.light, col.body, col.shade), floatArrayOf(0f, 0.45f, 1f)))
        c.drawPath(path, fill)
        fill.shader = null
        c.save()
        c.clipPath(path)
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
        shaded(c, path2, Colors.lighten(col.accent2, 0.4f), col.accent2, Colors.darken(col.accent2, 0.25f), outline = false)
        val spots = floatArrayOf(-0.35f, -1.5f, 0.16f, 0.4f, -1.3f, 0.11f, 0.1f, -0.35f, 0.14f, -0.6f, -0.45f, 0.09f)
        for (i in 0 until 4) {
            val sx = spots[i * 3] * ew
            val sy = spots[i * 3 + 1] * eh
            val sr = spots[i * 3 + 2] * ew
            ovalPath(path3, sx - sr, sy - sr, sx + sr, sy + sr)
            shaded(c, path3, Colors.lighten(col.accent, 0.4f), col.accent, Colors.darken(col.accent, 0.2f), outline = false)
        }
        // ambient occlusion
        shaderFill(LinearGradient(0f, -eh * 0.6f, 0f, 0f, Colors.alpha(col.shade, 0f), Colors.alpha(Colors.darken(col.shade, 0.2f), 0.5f), Shader.TileMode.CLAMP))
        c.drawRect(-ew * 1.5f, -eh * 0.6f, ew * 1.5f, eh * 0.1f, fill)
        fill.shader = null
        c.restore()
        gloss(c, -ew * 0.4f, -eh * 1.45f, ew * 0.32f, eh * 0.22f, 0.85f)
        outline(c, path, col.shade)

        if (taps > 0) {
            path2.reset()
            val n = taps.coerceAtMost(4)
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
            stroke(c, path2, 0xFF6B4E3D.toInt(), u * 0.038f)
            c.save()
            c.translate(u * 0.012f, u * 0.012f)
            stroke(c, path2, Colors.alpha(Color.WHITE, 0.6f), u * 0.015f)
            c.restore()
        }
        c.restoreToCount(save)
    }

    /** Convenience for static renders. */
    fun drawStandalone(c: Canvas, w: Float, h: Float, look: PetLook, pose: PetPose, sizeFactor: Float = 0.72f, groundFactor: Float = 0.93f, shadow: Boolean = true, silhouette: Int? = null) {
        val s = min(w, h) * sizeFactor
        if (shadow) drawShadow(c, w / 2f, h * groundFactor, s, pose, look)
        draw(c, w / 2f, h * groundFactor, s, look, pose, silhouette)
    }
}
