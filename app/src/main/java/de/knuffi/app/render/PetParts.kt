package de.knuffi.app.render

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Path
import android.graphics.Shader
import de.knuffi.core.EggLine
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

// Extra body parts for the many creature forms. They extend PetRenderer and share its paints.

private const val TAU = (PI * 2).toFloat()

/** Stable pseudo random numbers for decoration placement. */
private fun hash(i: Int, salt: Int = 0): Float {
    val x = sin((i * 12.9898f + salt * 78.233f)) * 43758.547f
    return x - kotlin.math.floor(x)
}

internal fun PetRenderer.buildShapedBody(p: Path, fl: FormLook, bw: Float, bh: Float, bottom: Float, t: Float): Boolean {
    val top = bottom - 2f * bh
    val cy = bottom - bh
    when (fl.shape) {
        Shape.SQUARE -> {
            val r = bw * 0.42f
            p.reset()
            p.moveTo(-bw + r, top)
            p.lineTo(bw - r, top)
            p.cubicTo(bw - r * 0.3f, top, bw, top + r * 0.3f, bw, top + r)
            p.lineTo(bw, bottom - r)
            p.cubicTo(bw, bottom - r * 0.3f, bw - r * 0.3f, bottom, bw - r, bottom)
            p.lineTo(-bw + r, bottom)
            p.cubicTo(-bw + r * 0.3f, bottom, -bw, bottom - r * 0.3f, -bw, bottom - r)
            p.lineTo(-bw, top + r)
            p.cubicTo(-bw, top + r * 0.3f, -bw + r * 0.3f, top, -bw + r, top)
            p.close()
            return true
        }
        Shape.DROP -> {
            val tipY = top - bh * 0.28f + sin(t * 2.4f) * bh * 0.03f
            val sway = sin(t * 1.7f) * bw * 0.06f
            p.reset()
            p.moveTo(sway, tipY)
            p.cubicTo(bw * 0.35f + sway, top + bh * 0.1f, bw, cy - bh * 0.2f, bw, cy + bh * 0.2f)
            p.cubicTo(bw, cy + bh * 0.75f, bw * 0.6f, bottom, 0f, bottom)
            p.cubicTo(-bw * 0.6f, bottom, -bw, cy + bh * 0.75f, -bw, cy + bh * 0.2f)
            p.cubicTo(-bw, cy - bh * 0.2f, -bw * 0.35f + sway, top + bh * 0.1f, sway, tipY)
            p.close()
            return true
        }
        Shape.ROUND -> return false
    }
}

// ---------------------------------------------------------------------------------- aura

internal fun PetRenderer.drawAuraFx(c: Canvas, fl: FormLook, bw: Float, bh: Float, cy: Float, t: Float) {
    val col = fl.colors
    val n = 6
    for (i in 0 until n) {
        val ph = (t * 0.25f + i / n.toFloat()) % 1f
        val ang = i * TAU / n + t * 0.35f
        val rx = bw * 1.45f
        val ry = bh * 1.25f
        when (fl.aura) {
            Aura.NONE -> return
            Aura.FLAMES -> {
                val x = cos(ang) * rx
                val y = cy + sin(ang) * ry
                val s = u * (0.1f + 0.04f * sin(t * 6f + i))
                flame(c, x, y, s, col.accent2, col.accent, 0.85f)
            }
            Aura.BUBBLES -> {
                val x = (hash(i) - 0.5f) * bw * 2.8f + sin(t * 1.5f + i) * u * 0.05f
                val y = cy + bh * 1.2f - ph * bh * 3f
                val r = u * (0.04f + 0.04f * hash(i, 3))
                bubble(c, x, y, r, 1f - ph)
            }
            Aura.SNOW -> {
                val x = (hash(i) - 0.5f) * bw * 3f + sin(t + i) * u * 0.08f
                val y = cy - bh * 1.6f + ph * bh * 3.2f
                val a = if (ph < 0.15f) ph / 0.15f else if (ph > 0.8f) (1f - ph) / 0.2f else 1f
                snowflake(c, x, y, u * 0.06f, Colors.alpha(Color.WHITE, 0.9f * a), t + i)
            }
            Aura.HEARTS -> {
                val x = (hash(i) - 0.5f) * bw * 2.8f
                val y = cy + bh * 0.4f - ph * bh * 2.4f
                Shapes.heart(path3, x, y, u * 0.1f)
                fill.shader = null
                fill.color = Colors.alpha(col.accent2, 0.85f * (1f - ph))
                c.drawPath(path3, fill)
            }
            Aura.LEAVES -> {
                val x = (hash(i) - 0.5f) * bw * 3f + sin(t * 1.3f + i * 2f) * u * 0.1f
                val y = cy - bh * 1.4f + ph * bh * 3f
                c.save()
                c.rotate(t * 60f + i * 40f, x, y)
                leaf(c, x, y, u * 0.08f, u * 0.045f, Colors.alpha(col.accent, 0.9f * (1f - abs(ph - 0.5f) * 1.6f).coerceIn(0f, 1f)))
                c.restore()
            }
            Aura.STARS -> {
                val x = cos(ang) * rx
                val y = cy + sin(ang) * ry
                val tw = (sin(t * 3f + i * 1.3f) + 1f) / 2f
                Shapes.softStar(path3, x, y, u * (0.05f + 0.04f * tw), u * 0.025f, 4, t * 40f)
                fill.shader = null
                fill.color = Colors.alpha(Colors.lighten(col.accent, 0.4f), 0.5f + 0.5f * tw)
                c.drawPath(path3, fill)
            }
            Aura.SMOKE -> {
                val x = (hash(i) - 0.5f) * bw * 2.2f
                val y = cy - bh * 0.4f - ph * bh * 2f
                val r = u * (0.08f + 0.12f * ph)
                shaderFill(radial(x, y, r, intArrayOf(Colors.alpha(0xFF9A93A8.toInt(), 0.45f * (1f - ph)), Colors.alpha(0xFF9A93A8.toInt(), 0f))))
                c.drawCircle(x, y, r, fill)
                fill.shader = null
            }
            Aura.BOLTS -> {
                if (sin(t * 7f + i * 2.1f) > 0.6f) {
                    val x = cos(ang) * rx
                    val y = cy + sin(ang) * ry
                    bolt(c, x, y, u * 0.12f, col.accent)
                }
            }
        }
    }
}

internal fun PetRenderer.flame(c: Canvas, x: Float, y: Float, s: Float, outer: Int, inner: Int, alpha: Float = 1f) {
    path3.reset()
    path3.moveTo(x, y - s * 1.6f)
    path3.cubicTo(x + s * 0.9f, y - s * 0.6f, x + s, y + s * 0.5f, x, y + s)
    path3.cubicTo(x - s, y + s * 0.5f, x - s * 0.9f, y - s * 0.6f, x, y - s * 1.6f)
    path3.close()
    shaderFill(LinearGradient(0f, y - s * 1.6f, 0f, y + s, Colors.alpha(inner, alpha), Colors.alpha(outer, alpha), Shader.TileMode.CLAMP))
    c.drawPath(path3, fill)
    fill.shader = null
    fill.color = Colors.alpha(0xFFFFF6B0.toInt(), 0.9f * alpha)
    c.drawOval(x - s * 0.35f, y - s * 0.2f, x + s * 0.35f, y + s * 0.75f, fill)
}

internal fun PetRenderer.bubble(c: Canvas, x: Float, y: Float, r: Float, alpha: Float) {
    val a = alpha.coerceIn(0f, 1f)
    shaderFill(radial(x - r * 0.3f, y - r * 0.3f, r * 1.3f, intArrayOf(Colors.alpha(Color.WHITE, 0.5f * a), Colors.alpha(0xFFBFE8FF.toInt(), 0.25f * a), Colors.alpha(0xFF7CC8FF.toInt(), 0.45f * a)), floatArrayOf(0f, 0.6f, 1f)))
    c.drawCircle(x, y, r, fill)
    fill.shader = null
    fill.color = Colors.alpha(Color.WHITE, 0.9f * a)
    c.drawCircle(x - r * 0.35f, y - r * 0.35f, r * 0.22f, fill)
}

internal fun PetRenderer.snowflake(c: Canvas, x: Float, y: Float, r: Float, color: Int, rot: Float) {
    path3.reset()
    for (k in 0 until 3) {
        val a = rot + k * (PI / 3).toFloat()
        path3.moveTo(x - cos(a) * r, y - sin(a) * r)
        path3.lineTo(x + cos(a) * r, y + sin(a) * r)
    }
    stroke(c, path3, color, r * 0.28f)
}

internal fun PetRenderer.leaf(c: Canvas, x: Float, y: Float, l: Float, w: Float, color: Int) {
    path3.reset()
    path3.moveTo(x - l, y)
    path3.quadTo(x, y - w * 2f, x + l, y)
    path3.quadTo(x, y + w * 2f, x - l, y)
    path3.close()
    shaded(c, path3, Colors.lighten(color, 0.4f), color, Colors.darken(color, 0.3f), outline = false)
    path3.reset()
    path3.moveTo(x - l * 0.8f, y)
    path3.lineTo(x + l * 0.8f, y)
    stroke(c, path3, Colors.alpha(Colors.darken(color, 0.35f), Color.alpha(color) / 255f * 0.6f), w * 0.25f)
}

internal fun PetRenderer.bolt(c: Canvas, x: Float, y: Float, s: Float, color: Int) {
    path3.reset()
    path3.moveTo(x + s * 0.2f, y - s)
    path3.lineTo(x - s * 0.45f, y + s * 0.1f)
    path3.lineTo(x - s * 0.02f, y + s * 0.1f)
    path3.lineTo(x - s * 0.25f, y + s)
    path3.lineTo(x + s * 0.5f, y - s * 0.2f)
    path3.lineTo(x + s * 0.05f, y - s * 0.2f)
    path3.close()
    shaded(c, path3, Colors.lighten(color, 0.5f), color, Colors.darken(color, 0.3f))
}

// ---------------------------------------------------------------------------------- tails

internal fun PetRenderer.drawTailPart(c: Canvas, fl: FormLook, pose: PetPose, bw: Float, bh: Float, cy: Float) {
    val col = fl.colors
    val t = pose.t
    val side = if (pose.turn > 0.05f) -1f else 1f
    val bx = side * bw * 0.72f
    val by = cy + bh * 0.4f
    val wag = sin(t * 3f)
    when (fl.tail) {
        Tail.NONE, Tail.DRAGON -> Unit
        Tail.FLUFFY -> {
            c.save()
            c.rotate(side * (25f + wag * 8f), bx, by)
            ovalPath(path, bx - u * 0.2f + side * u * 0.12f, by - u * 0.62f, bx + u * 0.2f + side * u * 0.12f, by + u * 0.08f)
            val main = Colors.lerp(col.body, col.accent2, 0.35f)
            shaded(c, path, Colors.lighten(main, 0.5f), main, Colors.darken(main, 0.25f))
            ovalPath(path2, bx - u * 0.13f + side * u * 0.14f, by - u * 0.64f, bx + u * 0.13f + side * u * 0.14f, by - u * 0.36f)
            shaded(c, path2, Color.WHITE, Colors.lighten(main, 0.6f), main, outline = false)
            c.restore()
        }
        Tail.FISH -> {
            val tx = bx + side * u * 0.28f
            val ty = by - u * 0.05f + wag * u * 0.04f
            path.reset()
            path.moveTo(bx, by + u * 0.05f)
            path.quadTo(tx, ty, tx + side * u * 0.2f, ty - u * 0.28f)
            path.quadTo(tx + side * u * 0.05f, ty, tx + side * u * 0.24f, ty + u * 0.22f)
            path.quadTo(tx - side * u * 0.02f, ty + u * 0.12f, bx, by + u * 0.2f)
            path.close()
            shaded(c, path, Colors.lighten(col.accent2, 0.45f), col.accent2, Colors.darken(col.accent2, 0.3f))
            path3.reset()
            path3.moveTo(tx - side * u * 0.02f, ty)
            path3.lineTo(tx + side * u * 0.16f, ty - u * 0.18f)
            path3.moveTo(tx - side * u * 0.02f, ty + u * 0.02f)
            path3.lineTo(tx + side * u * 0.18f, ty + u * 0.14f)
            stroke(c, path3, Colors.alpha(Color.WHITE, 0.5f), u * 0.018f)
        }
        Tail.LEAF -> {
            c.save()
            c.rotate(side * (-35f + wag * 6f), bx, by)
            leaf(c, bx + side * u * 0.28f, by, u * 0.3f, u * 0.12f, col.accent)
            c.restore()
        }
        Tail.BOLT -> {
            c.save()
            c.rotate(side * (20f + wag * 5f), bx, by)
            bolt(c, bx + side * u * 0.2f, by - u * 0.15f, u * 0.3f, col.accent)
            c.restore()
        }
        Tail.CAT, Tail.DINO -> {
            val dino = fl.tail == Tail.DINO
            path.reset()
            if (dino) {
                path.moveTo(bx - side * u * 0.05f, by - u * 0.14f)
                path.quadTo(bx + side * u * 0.4f, by - u * 0.05f + wag * u * 0.05f, bx + side * u * 0.62f, by - u * 0.12f + wag * u * 0.08f)
                path.quadTo(bx + side * u * 0.4f, by + u * 0.2f, bx - side * u * 0.05f, by + u * 0.2f)
                path.close()
                shaded(c, path, col.light, col.body, col.shade)
            } else {
                path.reset()
                path.moveTo(bx, by)
                path.cubicTo(bx + side * u * 0.35f, by + u * 0.05f, bx + side * u * 0.3f, by - u * 0.45f, bx + side * u * (0.5f + 0.06f * wag), by - u * 0.55f)
                line.shader = null
                line.color = Colors.darken(col.body, 0.12f)
                line.strokeWidth = u * 0.1f
                c.drawPath(path, line)
                line.color = Colors.alpha(col.light, 0.6f)
                line.strokeWidth = u * 0.03f
                c.save()
                c.translate(-u * 0.015f, -u * 0.015f)
                c.drawPath(path, line)
                c.restore()
            }
        }
        Tail.RAINBOW -> {
            val colors = intArrayOf(0xFFFF7A9A.toInt(), 0xFFFFC46A.toInt(), 0xFFFFF07A.toInt(), 0xFF8AE8A0.toInt(), 0xFF8AC8FF.toInt(), 0xFFB99AFF.toInt())
            for ((i, k) in colors.withIndex()) {
                path.reset()
                val o = (i - 2.5f) * u * 0.035f
                path.moveTo(bx, by + o)
                path.cubicTo(bx + side * u * 0.35f, by + o + u * 0.1f, bx + side * u * 0.45f, by - u * 0.35f + o, bx + side * u * (0.35f + 0.05f * wag), by - u * 0.55f + o)
                line.shader = null
                line.color = k
                line.strokeWidth = u * 0.045f
                c.drawPath(path, line)
            }
        }
        Tail.FLAME -> {
            c.save()
            c.rotate(side * (40f + wag * 10f), bx, by)
            flame(c, bx + side * u * 0.25f, by - u * 0.1f, u * 0.18f, col.accent2, col.accent)
            c.restore()
        }
        Tail.COMET -> {
            for (i in 0 until 5) {
                val k = i / 5f
                val x = bx + side * u * (0.15f + k * 0.55f)
                val y = by - u * 0.1f - k * u * 0.2f + sin(t * 3f + i) * u * 0.02f
                val r = u * (0.1f - k * 0.015f)
                shaderFill(radial(x, y, r * 2f, intArrayOf(Colors.alpha(col.accent, 0.7f * (1f - k)), Colors.alpha(col.accent, 0f))))
                c.drawCircle(x, y, r * 2f, fill)
                fill.shader = null
            }
            Shapes.softStar(path3, bx + side * u * 0.15f, by - u * 0.1f, u * 0.12f, u * 0.06f, 5, t * 30f)
            shaded(c, path3, Color.WHITE, col.accent, Colors.darken(col.accent, 0.25f), outline = false)
        }
    }
}

// ---------------------------------------------------------------------------------- wings

internal fun PetRenderer.drawWingsPart(c: Canvas, fl: FormLook, pose: PetPose, bw: Float, bh: Float, cy: Float, flap: Float) {
    val col = fl.colors
    when (fl.wings) {
        Wings.FAIRY -> for (s in intArrayOf(-1, 1)) {
            val sf = s.toFloat()
            c.save()
            c.translate(sf * bw * 0.7f, cy - bh * 0.25f)
            c.rotate(sf * (flap * 18f - 8f))
            for (k in 0..1) {
                val big = k == 0
                val w = u * if (big) 0.55f else 0.4f
                val h = u * if (big) 0.36f else 0.26f
                c.save()
                c.rotate(sf * if (big) -28f else 22f)
                ovalPath(path, if (s > 0) 0f else -w, -h, if (s > 0) w else 0f, h)
                shaderFill(radial(sf * w * 0.3f, -h * 0.3f, w * 1.1f, intArrayOf(Colors.alpha(Color.WHITE, 0.8f), Colors.alpha(col.accent2, 0.55f), Colors.alpha(col.accent, 0.5f)), floatArrayOf(0f, 0.55f, 1f)))
                c.drawPath(path, fill)
                fill.shader = null
                stroke(c, path, Colors.alpha(Colors.darken(col.accent, 0.2f), 0.6f), u * 0.018f)
                fill.color = Colors.alpha(Color.WHITE, 0.8f)
                c.drawCircle(sf * w * 0.55f, -h * 0.2f, u * 0.025f, fill)
                c.restore()
            }
            c.restore()
        }
        Wings.BAT -> for (s in intArrayOf(-1, 1)) {
            val sf = s.toFloat()
            c.save()
            c.translate(sf * bw * 0.72f, cy - bh * 0.25f)
            c.rotate(sf * (flap * 18f - 6f))
            path.reset()
            path.moveTo(0f, 0f)
            path.quadTo(sf * u * 0.3f, -u * 0.55f, sf * u * 0.85f, -u * 0.42f)
            path.quadTo(sf * u * 0.75f, -u * 0.16f, sf * u * 0.78f, 0f)
            path.quadTo(sf * u * 0.62f, -u * 0.08f, sf * u * 0.52f, u * 0.08f)
            path.quadTo(sf * u * 0.4f, -u * 0.02f, sf * u * 0.28f, u * 0.12f)
            path.quadTo(sf * u * 0.16f, u * 0.02f, 0f, u * 0.12f)
            path.close()
            shaded(c, path, Colors.lighten(col.accent2, 0.3f), col.accent2, Colors.darken(col.accent2, 0.35f))
            c.restore()
        }
        Wings.JET -> {
            val y = cy - bh * 0.35f
            for (s in intArrayOf(-1, 1)) {
                val x = s * bw * 0.72f
                path.reset()
                path.addRoundRect(x - u * 0.12f, y - u * 0.3f, x + u * 0.12f, y + u * 0.3f, u * 0.1f, u * 0.1f, Path.Direction.CW)
                shaded(c, path, Colors.lighten(col.accent2, 0.5f), col.accent2, Colors.darken(col.accent2, 0.35f))
                fill.color = col.accent
                c.drawRect(x - u * 0.12f, y - u * 0.08f, x + u * 0.12f, y - u * 0.02f, fill)
                val fl2 = 0.8f + 0.2f * sin(pose.t * 30f + s)
                flame(c, x, y + u * 0.42f, u * 0.1f * fl2, 0xFFFF5A36.toInt(), 0xFFFFE680.toInt())
            }
        }
        Wings.BEE -> for (s in intArrayOf(-1, 1)) {
            val sf = s.toFloat()
            c.save()
            c.translate(sf * bw * 0.35f, cy - bh * 0.85f)
            c.rotate(sf * (-30f + flap * 25f))
            for (k in 0..1) {
                val w = u * if (k == 0) 0.3f else 0.22f
                c.save()
                c.rotate(sf * k * 30f)
                ovalPath(path, if (s > 0) 0f else -w, -u * 0.12f, if (s > 0) w else 0f, u * 0.12f)
                fill.shader = null
                fill.color = Colors.alpha(col.accent2, 0.7f)
                c.drawPath(path, fill)
                stroke(c, path, Colors.alpha(0xFF7A8AA8.toInt(), 0.6f), u * 0.015f)
                c.restore()
            }
            c.restore()
        }
        Wings.FEATHER -> for (s in intArrayOf(-1, 1)) {
            val sf = s.toFloat()
            c.save()
            c.translate(sf * bw * 0.8f, cy - bh * 0.2f)
            c.rotate(sf * flap * 12f)
            val lens = floatArrayOf(0.62f, 0.52f, 0.42f, 0.3f)
            val angles = floatArrayOf(-42f, -20f, 2f, 22f)
            for (i in lens.indices) {
                c.save()
                c.rotate(sf * angles[i])
                val l = u * lens[i]
                ovalPath(path, if (s > 0) 0f else -l, -u * 0.08f, if (s > 0) l else 0f, u * 0.08f)
                val k = Colors.lerp(col.accent, col.accent2, i / 3f)
                shaded(c, path, Colors.lighten(k, 0.5f), k, Colors.darken(k, 0.25f))
                c.restore()
            }
            c.restore()
        }
        else -> Unit
    }
}

// ---------------------------------------------------------------------------------- ears

internal fun PetRenderer.drawEarsPart(c: Canvas, fl: FormLook, pose: PetPose, bw: Float, bh: Float, top: Float, cy: Float, fx: Float) {
    val col = fl.colors
    val lag = pose.earLag
    val t = pose.t
    when (fl.ears) {
        Ears.FOX, Ears.HORSE, Ears.BAT -> for (s in intArrayOf(-1, 1)) {
            val sf = s.toFloat()
            val (spread, height, width) = when (fl.ears) {
                Ears.FOX -> Triple(0.5f, 0.75f, 0.3f)
                Ears.BAT -> Triple(0.55f, 0.85f, 0.4f)
                else -> Triple(0.42f, 0.6f, 0.22f)
            }
            val x0 = sf * bw * spread + fx * 0.4f
            c.save()
            c.rotate(lag * 0.5f + sf * (if (fl.ears == Ears.BAT) 14f else 6f), x0, top + bh * 0.3f)
            path.reset()
            path.moveTo(x0 - sf * bw * width * 0.9f, top + bh * 0.3f)
            path.quadTo(x0 - sf * bw * width * 0.4f, top - bh * height * 0.6f, x0 + sf * bw * 0.05f, top - bh * height)
            path.quadTo(x0 + sf * bw * width * 0.8f, top - bh * height * 0.3f, x0 + sf * bw * width * 0.9f, top + bh * 0.35f)
            path.close()
            shaded(c, path, col.light, col.body, col.shade)
            path2.reset()
            path2.moveTo(x0 - sf * bw * width * 0.45f, top + bh * 0.2f)
            path2.quadTo(x0 - sf * bw * width * 0.15f, top - bh * height * 0.4f, x0 + sf * bw * 0.04f, top - bh * height * 0.72f)
            path2.quadTo(x0 + sf * bw * width * 0.5f, top - bh * height * 0.2f, x0 + sf * bw * width * 0.5f, top + bh * 0.22f)
            path2.close()
            val inner = if (fl.ears == Ears.FOX) col.belly else col.accent
            shaded(c, path2, Colors.lighten(inner, 0.3f), inner, Colors.darken(inner, 0.15f), outline = false)
            if (fl.ears == Ears.FOX) {
                // dark tips
                c.save()
                c.clipPath(path)
                fill.shader = null
                fill.color = Colors.darken(col.body, 0.45f)
                c.drawCircle(x0 + sf * bw * 0.05f, top - bh * height, bw * 0.16f, fill)
                c.restore()
            }
            c.restore()
        }
        Ears.FIN -> for (s in intArrayOf(-1, 1)) {
            val sf = s.toFloat()
            val x0 = sf * bw * 0.86f + fx * 0.2f
            val y0 = cy - bh * 0.25f
            c.save()
            c.rotate(sf * (sin(t * 3f + s) * 8f) + lag * 0.3f, x0, y0)
            path.reset()
            path.moveTo(x0, y0 - u * 0.12f)
            path.quadTo(x0 + sf * u * 0.35f, y0 - u * 0.3f, x0 + sf * u * 0.42f, y0 - u * 0.02f)
            path.quadTo(x0 + sf * u * 0.3f, y0 + u * 0.08f, x0 + sf * u * 0.38f, y0 + u * 0.2f)
            path.quadTo(x0 + sf * u * 0.12f, y0 + u * 0.16f, x0, y0 + u * 0.1f)
            path.close()
            shaded(c, path, Colors.lighten(col.accent2, 0.45f), col.accent2, Colors.darken(col.accent2, 0.3f))
            path3.reset()
            for (k in 0..2) {
                path3.moveTo(x0 + sf * u * 0.04f, y0 - u * 0.02f + k * u * 0.05f)
                path3.lineTo(x0 + sf * u * 0.32f, y0 - u * 0.12f + k * u * 0.12f)
            }
            stroke(c, path3, Colors.alpha(Color.WHITE, 0.45f), u * 0.016f)
            c.restore()
        }
        Ears.LEAF -> for (s in intArrayOf(-1, 1)) {
            val sf = s.toFloat()
            val x0 = sf * bw * 0.62f + fx * 0.4f
            val y0 = top + bh * 0.2f
            c.save()
            c.rotate(sf * (-40f + sin(t * 2f + s) * 6f) + lag * 0.6f, x0, y0)
            leaf(c, x0 + sf * u * 0.22f, y0, u * 0.24f, u * 0.1f, col.accent)
            c.restore()
        }
        Ears.WRAPPER -> for (s in intArrayOf(-1, 1)) {
            val sf = s.toFloat()
            val x0 = sf * bw * 0.95f
            val y0 = cy + bh * 0.05f
            c.save()
            c.rotate(sf * sin(t * 2.5f) * 6f, x0, y0)
            path.reset()
            path.moveTo(x0, y0)
            path.lineTo(x0 + sf * u * 0.34f, y0 - u * 0.22f)
            path.quadTo(x0 + sf * u * 0.28f, y0, x0 + sf * u * 0.34f, y0 + u * 0.22f)
            path.close()
            shaded(c, path, Colors.lighten(col.accent2, 0.5f), col.accent2, Colors.darken(col.accent2, 0.25f))
            c.save()
            c.clipPath(path)
            fill.shader = null
            fill.color = Colors.alpha(Color.WHITE, 0.6f)
            for (k in 0..2) c.drawRect(x0 + sf * u * (0.06f + k * 0.1f) - u * 0.02f, y0 - u * 0.3f, x0 + sf * u * (0.06f + k * 0.1f) + u * 0.02f, y0 + u * 0.3f, fill)
            c.restore()
            c.restore()
        }
        else -> Unit
    }
}

// ---------------------------------------------------------------------------------- back parts

internal fun PetRenderer.drawBackPart(c: Canvas, fl: FormLook, pose: PetPose, bw: Float, bh: Float, top: Float, cy: Float, fx: Float) {
    val col = fl.colors
    val t = pose.t
    when (fl.back) {
        Back.NONE -> Unit
        Back.PLATES -> for (i in -2..2) {
            val nx = i * 0.3f
            val x = i * bw * 0.34f + fx * 0.3f
            val y = top + bh * 2f * 0.24f * nx * nx + bh * 0.12f
            val h = bh * (0.48f - abs(i) * 0.08f)
            path.reset()
            path.moveTo(x - bw * 0.16f, y + bh * 0.1f)
            path.lineTo(x - bw * 0.12f, y - h * 0.5f)
            path.quadTo(x, y - h * 1.1f, x + bw * 0.12f, y - h * 0.5f)
            path.lineTo(x + bw * 0.16f, y + bh * 0.1f)
            path.close()
            shaded(c, path, Colors.lighten(col.accent, 0.4f), col.accent, Colors.darken(col.accent, 0.3f))
        }
        Back.SHELL -> {
            ovalPath(path, -bw * 1.12f, top - bh * 0.15f, bw * 1.12f, cy + bh * 0.4f)
            shaded(c, path, Colors.lighten(col.accent, 0.35f), col.accent, Colors.darken(col.accent, 0.35f))
            path3.reset()
            for (k in -1..1) {
                path3.moveTo(k * bw * 0.45f, top - bh * 0.1f)
                path3.quadTo(k * bw * 0.6f, cy - bh * 0.3f, k * bw * 0.7f, cy + bh * 0.35f)
            }
            stroke(c, path3, Colors.alpha(Colors.darken(col.accent, 0.4f), 0.6f), u * 0.025f)
        }
        Back.MANE -> {
            val n = 6
            val side = if (pose.turn > 0.05f) -1f else 1f
            for (i in 0 until n) {
                val k = i / (n - 1f)
                val x = side * (-bw * 0.15f + k * bw * 0.95f) + fx * 0.3f
                val y = top + bh * (0.05f + k * k * 0.9f) + sin(t * 2f + i) * u * 0.015f
                val r = u * (0.2f - k * 0.05f)
                val k2 = Colors.lerp(col.accent, col.accent2, k)
                ovalPath(path, x - r, y - r, x + r, y + r)
                shaded(c, path, Colors.lighten(k2, 0.45f), k2, Colors.darken(k2, 0.2f))
            }
        }
        Back.FRILL -> {
            val r = bw * 1.25f
            val y = top + bh * 0.55f
            path.reset()
            val n = 9
            for (i in 0..n) {
                val a = PI.toFloat() + i * PI.toFloat() / n
                val rr = if (i % 2 == 0) r else r * 0.88f
                val x = fx * 0.3f + cos(a) * rr
                val yy = y + sin(a) * rr * 0.8f
                if (i == 0) path.moveTo(x, yy) else path.lineTo(x, yy)
            }
            path.close()
            shaded(c, path, Colors.lighten(col.accent2, 0.35f), col.accent2, Colors.darken(col.accent2, 0.3f))
            ovalPath(path2, fx * 0.3f - r * 0.2f, y - r * 0.65f, fx * 0.3f + r * 0.2f, y - r * 0.4f)
            fill.shader = null
            fill.color = Colors.alpha(Color.WHITE, 0.25f)
            c.drawPath(path2, fill)
        }
        Back.CAPE -> {
            val sway = sin(t * 2f) * bw * 0.06f
            path.reset()
            path.moveTo(-bw * 0.7f, cy - bh * 0.35f)
            path.lineTo(bw * 0.7f, cy - bh * 0.35f)
            path.quadTo(bw * 1.25f + sway, cy + bh * 0.4f, bw * 1.15f + sway, cy + bh * 1.05f)
            path.quadTo(sway, cy + bh * 0.9f, -bw * 1.15f + sway, cy + bh * 1.05f)
            path.quadTo(-bw * 1.25f + sway, cy + bh * 0.4f, -bw * 0.7f, cy - bh * 0.35f)
            path.close()
            shaded(c, path, Colors.lighten(col.accent2, 0.25f), col.accent2, Colors.darken(col.accent2, 0.4f))
            c.save()
            c.clipPath(path)
            fill.shader = null
            fill.color = Colors.alpha(col.accent, 0.9f)
            c.drawRect(-bw * 2f, cy + bh * 0.1f, bw * 2f, cy + bh * 1.2f, fill)
            c.restore()
        }
    }
}

internal fun PetRenderer.drawAntlers(c: Canvas, fl: FormLook, pose: PetPose, bw: Float, bh: Float, top: Float, fx: Float) {
    val col = fl.colors
    val base = if (fl.sparkles) Colors.lerp(0xFFB08457.toInt(), col.accent, 0.55f) else 0xFFB08457.toInt()
    for (s in intArrayOf(-1, 1)) {
        val sf = s.toFloat()
        val x0 = sf * bw * 0.3f + fx * 0.4f
        val y0 = top + bh * 0.12f
        path.reset()
        path.moveTo(x0, y0)
        path.quadTo(x0 + sf * u * 0.05f, y0 - u * 0.3f, x0 + sf * u * 0.18f, y0 - u * 0.5f)
        path.moveTo(x0 + sf * u * 0.04f, y0 - u * 0.2f)
        path.quadTo(x0 + sf * u * 0.22f, y0 - u * 0.25f, x0 + sf * u * 0.34f, y0 - u * 0.36f)
        path.moveTo(x0 + sf * u * 0.1f, y0 - u * 0.38f)
        path.quadTo(x0 + sf * u * 0.02f, y0 - u * 0.5f, x0 - sf * u * 0.02f, y0 - u * 0.62f)
        line.shader = null
        line.color = Colors.darken(base, 0.25f)
        line.strokeWidth = u * 0.075f
        c.drawPath(path, line)
        line.color = base
        line.strokeWidth = u * 0.05f
        c.drawPath(path, line)
        if (!fl.sparkles) {
            leaf(c, x0 + sf * u * 0.34f, y0 - u * 0.38f, u * 0.08f, u * 0.035f, col.accent)
        } else {
            val tw = (sin(pose.t * 3f + s) + 1f) / 2f
            Shapes.softStar(path3, x0 + sf * u * 0.18f, y0 - u * 0.52f, u * (0.05f + 0.02f * tw), u * 0.025f, 5, 0f)
            shaded(c, path3, Color.WHITE, col.accent, Colors.darken(col.accent, 0.2f), outline = false)
        }
    }
}

// ---------------------------------------------------------------------------------- tops

internal fun PetRenderer.drawTopPart(c: Canvas, fl: FormLook, pose: PetPose, bw: Float, bh: Float, top: Float, fx: Float) {
    val col = fl.colors
    val t = pose.t
    val lag = pose.earLag
    val x0 = fx * 0.4f
    val y0 = top + bh * 0.06f
    when (fl.antenna) {
        Antenna.UNICORN -> {
            c.save()
            c.rotate(10f + lag * 0.2f, x0, y0)
            path.reset()
            path.moveTo(x0 - u * 0.09f, y0 + u * 0.02f)
            path.lineTo(x0, y0 - u * 0.5f)
            path.lineTo(x0 + u * 0.09f, y0 + u * 0.02f)
            path.close()
            shaded(c, path, Color.WHITE, col.accent, Colors.darken(col.accent, 0.25f))
            c.save()
            c.clipPath(path)
            path3.reset()
            for (k in 0..3) {
                val y = y0 - u * (0.06f + k * 0.11f)
                path3.moveTo(x0 - u * 0.12f, y)
                path3.lineTo(x0 + u * 0.12f, y - u * 0.06f)
            }
            stroke(c, path3, Colors.alpha(Color.WHITE, 0.7f), u * 0.02f)
            c.restore()
            c.restore()
        }
        Antenna.FLAME -> {
            val s = u * (0.16f + 0.02f * sin(t * 8f))
            c.save()
            c.rotate(sin(t * 5f) * 6f + lag * 0.4f, x0, y0)
            flame(c, x0, y0 - s * 0.7f, s, col.accent, 0xFFFFE680.toInt())
            c.restore()
        }
        Antenna.FLOWER -> {
            val fy = y0 - u * 0.08f
            c.save()
            c.rotate(sin(t) * 6f + lag * 0.4f, x0, y0)
            for (i in 0 until 6) {
                val a = i * TAU / 6 + t * 0.2f
                val px = x0 + cos(a) * u * 0.11f
                val py = fy + sin(a) * u * 0.11f
                ovalPath(path, px - u * 0.085f, py - u * 0.085f, px + u * 0.085f, py + u * 0.085f)
                shaded(c, path, Colors.lighten(col.accent, 0.5f), col.accent, Colors.darken(col.accent, 0.2f))
            }
            gem(c, x0, fy, u * 0.07f, 0xFFFFD84D.toInt())
            c.restore()
        }
        Antenna.CRYSTAL -> for (k in -1..1) {
            val h = u * if (k == 0) 0.36f else 0.24f
            val x = x0 + k * u * 0.12f
            c.save()
            c.rotate(k * 22f, x, y0)
            path.reset()
            path.moveTo(x - u * 0.05f, y0 + u * 0.02f)
            path.lineTo(x - u * 0.05f, y0 - h * 0.7f)
            path.lineTo(x, y0 - h)
            path.lineTo(x + u * 0.05f, y0 - h * 0.7f)
            path.lineTo(x + u * 0.05f, y0 + u * 0.02f)
            path.close()
            fill.shader = null
            shaded(c, path, Color.WHITE, Colors.alpha(col.accent, 0.95f), Colors.darken(col.accent, 0.25f))
            c.restore()
        }
        Antenna.SWIRL -> {
            path.reset()
            path.moveTo(x0, y0 + u * 0.02f)
            path.lineTo(x0 + u * 0.02f, y0 - u * 0.2f)
            stroke(c, path, Color.WHITE, u * 0.045f)
            val cx = x0 + u * 0.02f
            val cyy = y0 - u * 0.33f
            ovalPath(path, cx - u * 0.15f, cyy - u * 0.15f, cx + u * 0.15f, cyy + u * 0.15f)
            shaded(c, path, Colors.lighten(col.accent, 0.5f), col.accent, Colors.darken(col.accent, 0.25f))
            Shapes.spiral(path3, cx, cyy, u * 0.13f, 2.3f, t * 20f)
            stroke(c, path3, Colors.alpha(Color.WHITE, 0.85f), u * 0.03f)
        }
        Antenna.BOLT -> {
            val sway = sin(t * 2f) * u * 0.03f + lag * u * 0.004f
            path.reset()
            path.moveTo(x0, y0 + u * 0.03f)
            path.lineTo(x0 + sway, y0 - u * 0.26f)
            stroke(c, path, Colors.darken(col.body, 0.35f), u * 0.035f)
            val pulse = (sin(t * 4f) + 1f) / 2f
            shaderFill(radial(x0 + sway, y0 - u * 0.3f, u * 0.2f, intArrayOf(Colors.alpha(col.accent, 0.6f * pulse), Colors.alpha(col.accent, 0f))))
            c.drawCircle(x0 + sway, y0 - u * 0.3f, u * 0.2f, fill)
            fill.shader = null
            ovalPath(path, x0 + sway - u * 0.075f, y0 - u * 0.375f, x0 + sway + u * 0.075f, y0 - u * 0.225f)
            shaded(c, path, Color.WHITE, col.accent, Colors.darken(col.accent, 0.25f))
        }
        Antenna.STEM -> {
            path.reset()
            path.moveTo(x0 - u * 0.04f, y0 + u * 0.04f)
            path.quadTo(x0 - u * 0.02f, y0 - u * 0.12f, x0 + u * 0.08f, y0 - u * 0.2f)
            path.lineTo(x0 + u * 0.11f, y0 - u * 0.15f)
            path.quadTo(x0 + u * 0.04f, y0 - u * 0.08f, x0 + u * 0.05f, y0 + u * 0.04f)
            path.close()
            shaded(c, path, 0xFFA08050.toInt(), 0xFF7A5A30.toInt(), 0xFF4A3418.toInt())
            leaf(c, x0 - u * 0.14f, y0 - u * 0.06f, u * 0.12f, u * 0.05f, col.accent)
        }
        Antenna.CREST -> for (k in 0..2) {
            val x = x0 + (k - 1) * u * 0.1f
            val h = u * if (k == 1) 0.3f else 0.22f
            c.save()
            c.rotate((k - 1) * 18f + sin(t * 2f + k) * 4f + lag * 0.3f, x, y0)
            path.reset()
            path.moveTo(x - u * 0.06f, y0 + u * 0.04f)
            path.quadTo(x - u * 0.04f, y0 - h * 0.7f, x + u * 0.03f, y0 - h)
            path.quadTo(x + u * 0.06f, y0 - h * 0.5f, x + u * 0.06f, y0 + u * 0.04f)
            path.close()
            shaded(c, path, Colors.lighten(col.accent, 0.4f), col.accent, Colors.darken(col.accent, 0.3f))
            c.restore()
        }
        Antenna.DROPLET -> for (k in 0..2) {
            val ph = (t * 0.8f + k / 3f) % 1f
            val x = x0 + (k - 1) * u * 0.1f * ph * 2f
            val y = y0 - u * 0.1f - ph * u * 0.45f
            Shapes.drop(path3, x, y, u * 0.07f * (1f - ph * 0.4f))
            fill.shader = null
            fill.color = Colors.alpha(col.accent, 0.9f * (1f - ph))
            c.drawPath(path3, fill)
        }
        Antenna.CLOVER -> {
            path.reset()
            path.moveTo(x0, y0 + u * 0.03f)
            path.quadTo(x0 + u * 0.03f, y0 - u * 0.1f, x0, y0 - u * 0.16f)
            stroke(c, path, Colors.darken(col.accent, 0.3f), u * 0.035f)
            c.save()
            c.rotate(sin(t * 1.5f) * 8f + lag, x0, y0)
            for (k in 0..2) {
                val a = -PI.toFloat() / 2 + (k - 1) * 1.2f
                val px = x0 + cos(a) * u * 0.1f
                val py = y0 - u * 0.2f + sin(a) * u * 0.1f
                Shapes.heart(path3, px, py, u * 0.13f)
                shaded(c, path3, Colors.lighten(col.accent, 0.45f), col.accent, Colors.darken(col.accent, 0.3f))
            }
            c.restore()
        }
        Antenna.MOON -> {
            val mx = x0 + sin(t * 1.2f) * u * 0.03f
            val my = y0 - u * 0.3f
            ovalPath(path, mx - u * 0.15f, my - u * 0.15f, mx + u * 0.15f, my + u * 0.15f)
            ovalPath(path2, mx - u * 0.07f, my - u * 0.19f, mx + u * 0.21f, my + u * 0.09f)
            path.op(path2, Path.Op.DIFFERENCE)
            shaderFill(radial(mx, my, u * 0.35f, intArrayOf(Colors.alpha(col.accent, 0.5f), Colors.alpha(col.accent, 0f))))
            c.drawCircle(mx, my, u * 0.35f, fill)
            fill.shader = null
            shaded(c, path, Color.WHITE, col.accent, Colors.darken(col.accent, 0.2f))
        }
        Antenna.PEARL -> {
            val px = x0 + bw * 0.4f
            val py = y0 + bh * 0.1f
            path.reset()
            path.moveTo(px, py + u * 0.08f)
            for (k in 0..4) {
                val a = PI.toFloat() + k * PI.toFloat() / 4
                path.lineTo(px + cos(a) * u * 0.14f, py + sin(a) * u * 0.14f)
            }
            path.close()
            shaded(c, path, Color.WHITE, col.accent2, Colors.darken(col.accent2, 0.2f))
            gem(c, px, py + u * 0.02f, u * 0.05f, 0xFFF4F0FF.toInt())
        }
        Antenna.BUD -> {
            path.reset()
            path.moveTo(x0, y0 + u * 0.02f)
            path.lineTo(x0, y0 - u * 0.12f)
            stroke(c, path, Colors.darken(col.accent2, 0.2f), u * 0.035f)
            for (s in intArrayOf(-1, 1)) leaf(c, x0 + s * u * 0.09f, y0 - u * 0.06f, u * 0.08f, u * 0.035f, col.accent2)
            c.save()
            c.rotate(sin(t * 1.5f) * 6f + lag, x0, y0)
            Shapes.drop(path3, x0, y0 - u * 0.24f, u * 0.13f)
            shaded(c, path3, Colors.lighten(col.accent, 0.5f), col.accent, Colors.darken(col.accent, 0.25f))
            c.restore()
        }
        Antenna.ANTENNAE -> for (s in intArrayOf(-1, 1)) {
            val sf = s.toFloat()
            val bx = x0 + sf * u * 0.1f
            val tx = bx + sf * u * 0.16f + sin(t * 2f + s) * u * 0.02f
            val ty = y0 - u * 0.36f
            path.reset()
            path.moveTo(bx, y0 + u * 0.03f)
            path.quadTo(bx + sf * u * 0.02f, y0 - u * 0.2f, tx, ty)
            stroke(c, path, Colors.darken(col.accent, 0.1f), u * 0.03f)
            ovalPath(path, tx - u * 0.05f, ty - u * 0.05f, tx + u * 0.05f, ty + u * 0.05f)
            shaded(c, path, Colors.lighten(col.accent, 0.5f), col.accent, Colors.darken(col.accent, 0.2f))
        }
        Antenna.RAYS -> {
            val cyy = y0 + bh * 0.6f
            val r1 = bw * 1.05f
            val r2 = bw * 1.35f
            val n = 12
            path.reset()
            for (i in 0 until n) {
                val a = i * TAU / n + t * 0.3f
                val a2 = a + TAU / n / 2
                path.moveTo(fx * 0.3f + cos(a - 0.12f) * r1, cyy + sin(a - 0.12f) * r1)
                path.lineTo(fx * 0.3f + cos(a2 - TAU / n / 2) * r2, cyy + sin(a2 - TAU / n / 2) * r2)
                path.lineTo(fx * 0.3f + cos(a + 0.12f) * r1, cyy + sin(a + 0.12f) * r1)
                path.close()
            }
            shaded(c, path, Colors.lighten(col.accent, 0.3f), col.accent, col.accent2, outline = false)
        }
        else -> Unit
    }
}

internal fun PetRenderer.drawCapTop(c: Canvas, fl: FormLook, pose: PetPose, bw: Float, bh: Float, top: Float, cy: Float, fx: Float) {
    val col = fl.colors
    val t = pose.t
    val x0 = fx * 0.4f
    when (fl.antenna) {
        Antenna.MUSHROOM -> {
            val base = top + bh * 0.3f
            rect.set(x0 - bw * 0.95f, base - u * 0.55f, x0 + bw * 0.95f, base + u * 0.2f)
            path.reset()
            path.arcTo(rect, 180f, 180f)
            path.quadTo(x0, base + u * 0.1f, x0 - bw * 0.95f, base - u * 0.17f)
            path.close()
            shaded(c, path, Colors.lighten(col.accent, 0.4f), col.accent, Colors.darken(col.accent, 0.3f))
            fill.shader = null
            fill.color = Colors.alpha(col.accent2, 0.95f)
            c.save()
            c.clipPath(path)
            c.drawCircle(x0 - bw * 0.4f, base - u * 0.3f, u * 0.08f, fill)
            c.drawCircle(x0 + bw * 0.25f, base - u * 0.4f, u * 0.1f, fill)
            c.drawCircle(x0 + bw * 0.65f, base - u * 0.15f, u * 0.06f, fill)
            c.drawCircle(x0 - bw * 0.05f, base - u * 0.1f, u * 0.05f, fill)
            c.restore()
        }
        Antenna.CHERRY -> {
            val base = top + bh * 0.15f
            for (k in 0..2) {
                val r = u * (0.16f - k * 0.04f)
                val y = base - k * u * 0.1f
                ovalPath(path, x0 - r * 1.3f, y - r, x0 + r * 1.3f, y + r * 0.6f)
                shaded(c, path, Color.WHITE, 0xFFFFF8F0.toInt(), 0xFFE8D8CC.toInt())
            }
            val cyy = base - u * 0.33f
            path.reset()
            path.moveTo(x0, cyy)
            path.quadTo(x0 + u * 0.05f, cyy - u * 0.12f, x0 + u * 0.12f, cyy - u * 0.16f)
            stroke(c, path, 0xFF4E8A3A.toInt(), u * 0.02f)
            ovalPath(path, x0 - u * 0.075f, cyy - u * 0.075f, x0 + u * 0.075f, cyy + u * 0.075f)
            shaded(c, path, 0xFFFF9AAA.toInt(), 0xFFE0304A.toInt(), 0xFF9A1830.toInt())
        }
        Antenna.SHELL_CAP -> {
            val base = top + bh * 0.4f
            path.reset()
            path.moveTo(x0 - bw * 0.8f, base)
            path.quadTo(x0 - bw * 0.7f, top - bh * 0.3f, x0, top - bh * 0.32f)
            path.quadTo(x0 + bw * 0.7f, top - bh * 0.3f, x0 + bw * 0.8f, base)
            val n = 8
            for (i in 0..n) {
                val x = x0 + bw * 0.8f - i * bw * 1.6f / n
                path.lineTo(x, base + if (i % 2 == 0) 0f else u * 0.08f)
            }
            path.close()
            shaded(c, path, Color.WHITE, col.accent, Colors.darken(col.accent, 0.2f))
            fill.shader = null
            fill.color = Colors.alpha(col.accent2, 0.6f)
            c.save()
            c.clipPath(path)
            c.drawCircle(x0 - bw * 0.3f, top, u * 0.05f, fill)
            c.drawCircle(x0 + bw * 0.35f, top + bh * 0.1f, u * 0.04f, fill)
            c.restore()
        }
        Antenna.CHEF -> {
            val base = top + bh * 0.2f
            path.reset()
            path.addRoundRect(x0 - bw * 0.5f, base - u * 0.2f, x0 + bw * 0.5f, base + u * 0.02f, u * 0.03f, u * 0.03f, Path.Direction.CW)
            shaded(c, path, Color.WHITE, 0xFFF6F6FA.toInt(), 0xFFD0D0DC.toInt())
            for (k in -1..1) {
                val r = u * 0.18f
                val px = x0 + k * bw * 0.35f
                ovalPath(path, px - r, base - u * 0.45f - r * 0.6f, px + r, base - u * 0.45f + r * 0.8f)
                shaded(c, path, Color.WHITE, 0xFFFAFAFE.toInt(), 0xFFD8D8E4.toInt())
            }
        }
        Antenna.HELMET -> {
            val r = bw * 1.08f
            val hy = cy - bh * 0.15f
            fill.shader = null
            fill.color = Colors.alpha(0xFFBFE6FF.toInt(), 0.18f)
            c.drawCircle(x0, hy, r, fill)
            ovalPath(path, x0 - r, hy - r, x0 + r, hy + r)
            stroke(c, path, Colors.alpha(Color.WHITE, 0.75f), u * 0.03f)
            gloss(c, x0 - r * 0.45f, hy - r * 0.5f, r * 0.35f, r * 0.15f, 0.8f)
            path.reset()
            path.addRoundRect(x0 - bw * 0.95f, cy + bh * 0.42f, x0 + bw * 0.95f, cy + bh * 0.6f, u * 0.05f, u * 0.05f, Path.Direction.CW)
            shaded(c, path, 0xFFE8ECF6.toInt(), 0xFFB0B8C8.toInt(), 0xFF7A8498.toInt())
        }
        Antenna.WITCH -> {
            val base = top + bh * 0.14f
            path.reset()
            path.moveTo(x0 - bw * 0.55f, base)
            path.quadTo(x0 - bw * 0.1f, base - u * 0.45f, x0 + bw * 0.5f + sin(t * 1.5f) * u * 0.03f, base - u * 0.8f)
            path.quadTo(x0 + bw * 0.2f, base - u * 0.35f, x0 + bw * 0.55f, base)
            path.close()
            shaded(c, path, Colors.lighten(col.accent, 0.3f), col.accent, Colors.darken(col.accent, 0.35f))
            fill.shader = null
            fill.color = col.accent2
            c.save()
            c.clipPath(path)
            c.drawRect(x0 - bw, base - u * 0.14f, x0 + bw, base - u * 0.07f, fill)
            c.restore()
            ovalPath(path, x0 - bw * 0.85f, base - u * 0.07f, x0 + bw * 0.85f, base + u * 0.08f)
            shaded(c, path, Colors.lighten(col.accent, 0.2f), Colors.darken(col.accent, 0.1f), Colors.darken(col.accent, 0.45f))
        }
        Antenna.HALO -> {
            val hy = top - u * 0.12f + sin(t * 2f) * u * 0.03f
            val w = bw * 0.55f
            rect.set(x0 - w, hy - u * 0.08f, x0 + w, hy + u * 0.08f)
            shaderFill(radial(x0, hy, w * 1.4f, intArrayOf(Colors.alpha(0xFFFFE680.toInt(), 0.5f), Colors.alpha(0xFFFFE680.toInt(), 0f))))
            c.drawCircle(x0, hy, w * 1.4f, fill)
            fill.shader = null
            line.shader = null
            line.color = 0xFFFFD24D.toInt()
            line.strokeWidth = u * 0.05f
            c.drawOval(rect, line)
            line.color = 0xFFFFF6C8.toInt()
            line.strokeWidth = u * 0.02f
            c.drawOval(rect, line)
        }
        else -> Unit
    }
}

// ---------------------------------------------------------------------------------- body surface

internal fun PetRenderer.drawPatternPart(c: Canvas, fl: FormLook, pose: PetPose, bw: Float, bh: Float, top: Float, bottom: Float, cy: Float, fx: Float) {
    val col = fl.colors
    val pc = col.accent2
    fill.shader = null
    when (fl.pattern) {
        Pattern.NONE -> Unit
        Pattern.SPOTS -> {
            val pts = floatArrayOf(-0.72f, -0.55f, 0.13f, 0.7f, -0.35f, 0.1f, -0.55f, 0.45f, 0.09f, 0.62f, 0.5f, 0.11f, 0.15f, -0.88f, 0.08f, -0.2f, 0.78f, 0.07f)
            for (i in 0 until 6) {
                fill.color = Colors.alpha(pc, 0.85f)
                c.drawCircle(pts[i * 3] * bw + fx * 0.2f, cy + pts[i * 3 + 1] * bh, pts[i * 3 + 2] * bw * 1.6f, fill)
            }
        }
        Pattern.STRIPES -> for (k in 0..2) {
            val y = top + bh * (0.45f + k * 0.55f)
            path3.reset()
            path3.moveTo(-bw * 1.2f, y + bh * 0.12f)
            path3.quadTo(0f, y - bh * 0.18f, bw * 1.2f, y + bh * 0.12f)
            path3.lineTo(bw * 1.2f, y + bh * 0.3f)
            path3.quadTo(0f, y, -bw * 1.2f, y + bh * 0.3f)
            path3.close()
            fill.color = Colors.alpha(pc, 0.9f)
            c.drawPath(path3, fill)
        }
        Pattern.STARS -> for (i in 0 until 7) {
            val x = (hash(i, 5) - 0.5f) * bw * 1.8f
            val y = top + bh * 0.2f + hash(i, 9) * bh * 1.6f
            if (abs(x - fx) < bw * 0.5f && abs(y - (cy - bh * 0.1f)) < bh * 0.3f) continue
            Shapes.softStar(path3, x, y, u * 0.05f, u * 0.022f, 4, 0f)
            fill.color = Colors.alpha(pc, 0.9f)
            c.drawPath(path3, fill)
        }
        Pattern.PANELS -> {
            path3.reset()
            path3.addRoundRect(-bw * 0.52f + fx * 0.3f, cy + bh * 0.2f, bw * 0.52f + fx * 0.3f, bottom - bh * 0.18f, u * 0.06f, u * 0.06f, Path.Direction.CW)
            stroke(c, path3, Colors.alpha(Colors.darken(col.body, 0.35f), 0.55f), u * 0.022f)
            fill.color = Colors.alpha(pc, 0.9f)
            for (k in 0..2) c.drawCircle(-bw * 0.25f + k * bw * 0.25f + fx * 0.3f, cy + bh * 0.48f, u * 0.035f, fill)
            fill.color = Colors.alpha(Colors.darken(col.body, 0.3f), 0.6f)
            for (s in intArrayOf(-1, 1)) c.drawCircle(s * bw * 0.78f, top + bh * 0.35f, u * 0.025f, fill)
        }
        Pattern.SWIRL -> {
            Shapes.spiral(path3, fx * 0.3f, cy + bh * 0.35f, bw * 0.7f, 2.6f, pose.t * 10f)
            stroke(c, path3, Colors.alpha(pc, 0.55f), u * 0.06f)
        }
        Pattern.BANDAGE -> for (k in 0..4) {
            val y = top + bh * (0.25f + k * 0.42f)
            c.save()
            c.rotate(if (k % 2 == 0) -8f else 7f, 0f, y)
            fill.color = Colors.alpha(Color.WHITE, 0.55f)
            c.drawRect(-bw * 1.5f, y, bw * 1.5f, y + bh * 0.2f, fill)
            path3.reset()
            path3.moveTo(-bw * 1.5f, y)
            path3.lineTo(bw * 1.5f, y)
            stroke(c, path3, Colors.alpha(pc, 0.6f), u * 0.012f)
            c.restore()
        }
        Pattern.SPRINKLES -> {
            val colors = intArrayOf(0xFFFF6A8A.toInt(), 0xFF6AC8FF.toInt(), 0xFFFFE06A.toInt(), 0xFF8AE88A.toInt(), 0xFFB98AFF.toInt())
            for (i in 0 until 14) {
                val x = (hash(i, 2) - 0.5f) * bw * 1.8f
                val y = top + bh * 0.15f + hash(i, 4) * bh * 1.7f
                if (abs(x - fx) < bw * 0.55f && abs(y - cy) < bh * 0.45f) continue
                c.save()
                c.rotate(hash(i, 8) * 180f, x, y)
                fill.color = colors[i % colors.size]
                rect.set(x - u * 0.035f, y - u * 0.012f, x + u * 0.035f, y + u * 0.012f)
                c.drawRoundRect(rect, u * 0.012f, u * 0.012f, fill)
                c.restore()
            }
        }
        Pattern.GALAXY -> {
            shaderFill(radial(-bw * 0.4f, cy + bh * 0.3f, bw * 0.9f, intArrayOf(Colors.alpha(col.accent, 0.45f), Colors.alpha(col.accent, 0f))))
            c.drawCircle(-bw * 0.4f, cy + bh * 0.3f, bw * 0.9f, fill)
            shaderFill(radial(bw * 0.5f, cy - bh * 0.4f, bw * 0.8f, intArrayOf(Colors.alpha(pc, 0.45f), Colors.alpha(pc, 0f))))
            c.drawCircle(bw * 0.5f, cy - bh * 0.4f, bw * 0.8f, fill)
            fill.shader = null
            for (i in 0 until 12) {
                val tw = (sin(pose.t * 3f + i) + 1f) / 2f
                fill.color = Colors.alpha(Color.WHITE, 0.4f + 0.6f * tw)
                c.drawCircle((hash(i, 1) - 0.5f) * bw * 1.9f, top + hash(i, 3) * bh * 2f, u * (0.012f + 0.012f * tw), fill)
            }
        }
        Pattern.CRACKS -> {
            path3.reset()
            for (k in 0..3) {
                var x = (hash(k, 7) - 0.5f) * bw * 1.4f
                var y = cy + (hash(k, 3) - 0.3f) * bh
                path3.moveTo(x, y)
                repeat(3) { j ->
                    x += (hash(k * 5 + j, 11) - 0.5f) * bw * 0.4f
                    y += bh * 0.18f
                    path3.lineTo(x, y)
                }
            }
            val glow = 0.7f + 0.3f * sin(pose.t * 3f)
            stroke(c, path3, Colors.alpha(col.accent, 0.5f * glow), u * 0.07f)
            stroke(c, path3, Colors.alpha(0xFFFFE08A.toInt(), glow), u * 0.025f)
        }
        Pattern.BUTTONS -> for (k in 0..2) {
            val y = cy + bh * (0.25f + k * 0.28f)
            ovalPath(path3, fx * 0.4f - u * 0.05f, y - u * 0.05f, fx * 0.4f + u * 0.05f, y + u * 0.05f)
            shaded(c, path3, Colors.lighten(pc, 0.3f), pc, Colors.darken(pc, 0.3f), outline = false)
        }
        Pattern.BANDS -> for (k in 0..1) {
            val y = cy + bh * (-0.05f + k * 0.55f)
            fill.color = Colors.alpha(if (k == 0) col.accent else pc, 0.9f)
            c.drawRect(-bw * 1.5f, y, bw * 1.5f, y + bh * 0.2f, fill)
            fill.color = Colors.alpha(Color.WHITE, 0.3f)
            c.drawRect(-bw * 1.5f, y, bw * 1.5f, y + bh * 0.05f, fill)
        }
        Pattern.RIBS -> {
            path3.reset()
            for (k in -2..2) {
                if (k == 0) continue
                val x = k * bw * 0.38f
                path3.moveTo(x * 0.3f, top + bh * 0.05f)
                path3.quadTo(x * 1.4f, cy, x * 0.55f, bottom - bh * 0.02f)
            }
            stroke(c, path3, Colors.alpha(Colors.darken(col.body, 0.25f), 0.5f), u * 0.035f)
        }
    }
}

internal fun PetRenderer.drawJelly(c: Canvas, fl: FormLook, bw: Float, bh: Float, top: Float, cy: Float, t: Float) {
    for (i in 0 until 4) {
        val ph = (t * 0.15f + i * 0.25f) % 1f
        val x = (hash(i, 6) - 0.5f) * bw * 1.1f
        val y = cy + bh * 0.8f - ph * bh * 1.6f
        bubble(c, x, y, u * (0.03f + 0.02f * hash(i, 2)), (1f - ph) * 0.8f)
    }
    fill.shader = null
    fill.color = Colors.alpha(Color.WHITE, 0.22f)
    c.drawOval(-bw * 0.8f, top + bh * 0.08f, bw * 0.2f, top + bh * 0.5f, fill)
}

// ---------------------------------------------------------------------------------- face

internal fun PetRenderer.drawFaceplate(c: Canvas, fl: FormLook, bw: Float, bh: Float, cy: Float, fx: Float) {
    val w = bw * 0.78f
    val h = bh * 0.42f
    val y = cy - bh * 0.08f
    path.reset()
    path.addRoundRect(fx - w, y - h, fx + w, y + h * 1.15f, h * 0.7f, h * 0.7f, Path.Direction.CW)
    shaderFill(LinearGradient(0f, y - h, 0f, y + h, 0xFF2A3350.toInt(), 0xFF141A2E.toInt(), Shader.TileMode.CLAMP))
    c.drawPath(path, fill)
    fill.shader = null
    stroke(c, path, Colors.alpha(Colors.lighten(fl.colors.body, 0.4f), 0.7f), u * 0.025f)
    gloss(c, fx - w * 0.5f, y - h * 0.55f, w * 0.35f, h * 0.18f, 0.5f)
}

internal fun PetRenderer.drawMuzzle(c: Canvas, fl: FormLook, pose: PetPose, bw: Float, bh: Float, cy: Float, fx: Float) {
    val col = fl.colors
    val my = cy + bh * 0.26f
    when (fl.muzzle) {
        Muzzle.NONE -> Unit
        Muzzle.SNOUT -> {
            ovalPath(path, fx - u * 0.2f, my - u * 0.13f, fx + u * 0.2f, my + u * 0.13f)
            shaded(c, path, Color.WHITE, col.belly, Colors.darken(col.belly, 0.15f))
            fill.shader = null
            fill.color = Colors.alpha(col.eye, 0.7f)
            c.drawOval(fx - u * 0.08f, my - u * 0.08f, fx - u * 0.04f, my - u * 0.04f, fill)
            c.drawOval(fx + u * 0.04f, my - u * 0.08f, fx + u * 0.08f, my - u * 0.04f, fill)
        }
        Muzzle.NOSE -> {
            path.reset()
            path.moveTo(fx - u * 0.05f, my - u * 0.08f)
            path.quadTo(fx, my - u * 0.1f, fx + u * 0.05f, my - u * 0.08f)
            path.quadTo(fx + u * 0.03f, my - u * 0.02f, fx, my - u * 0.02f)
            path.quadTo(fx - u * 0.03f, my - u * 0.02f, fx - u * 0.05f, my - u * 0.08f)
            path.close()
            shaded(c, path, 0xFF7A6070.toInt(), 0xFF3A2A34.toInt(), 0xFF1A1018.toInt(), outline = false)
        }
        Muzzle.WHISKERS -> for (s in intArrayOf(-1, 1)) {
            val sf = s.toFloat()
            path3.reset()
            for (k in -1..1) {
                path3.moveTo(fx + sf * bw * 0.35f, my + k * u * 0.035f)
                path3.lineTo(fx + sf * bw * 0.72f, my + k * u * 0.07f - u * 0.02f)
            }
            stroke(c, path3, Colors.alpha(Colors.lighten(col.body, 0.6f), 0.8f), u * 0.014f)
        }
        Muzzle.BEAK -> {
            path.reset()
            path.moveTo(fx - u * 0.09f, my - u * 0.05f)
            path.quadTo(fx, my - u * 0.1f, fx + u * 0.09f, my - u * 0.05f)
            path.quadTo(fx + u * 0.02f, my + u * 0.1f, fx, my + u * 0.1f)
            path.quadTo(fx - u * 0.02f, my + u * 0.1f, fx - u * 0.09f, my - u * 0.05f)
            path.close()
            shaded(c, path, Colors.lighten(col.accent, 0.4f), col.accent, Colors.darken(col.accent, 0.3f))
        }
        Muzzle.FANGS -> Unit // drawn after the mouth
        Muzzle.CARROT -> {
            val dir = if (pose.turn < -0.05f) -1f else 1f
            path.reset()
            path.moveTo(fx, my - u * 0.1f)
            path.lineTo(fx + dir * u * 0.3f, my - u * 0.02f)
            path.lineTo(fx, my + u * 0.03f)
            path.close()
            shaded(c, path, Colors.lighten(col.accent, 0.4f), col.accent, Colors.darken(col.accent, 0.3f))
        }
    }
}

internal fun PetRenderer.drawFangs(c: Canvas, fl: FormLook, bh: Float, cy: Float, fx: Float) {
    if (fl.muzzle != Muzzle.FANGS) return
    val my = cy + bh * 0.26f
    for (s in intArrayOf(-1, 1)) {
        val x = fx + s * u * 0.055f
        path3.reset()
        path3.moveTo(x - u * 0.025f, my + u * 0.02f)
        path3.lineTo(x + u * 0.025f, my + u * 0.02f)
        path3.lineTo(x, my + u * 0.085f)
        path3.close()
        fill.shader = null
        fill.color = Color.WHITE
        c.drawPath(path3, fill)
        stroke(c, path3, Colors.alpha(0xFF6E2440.toInt(), 0.5f), u * 0.008f)
    }
}

// ---------------------------------------------------------------------------------- claws & ring

internal fun PetRenderer.drawClaws(c: Canvas, fl: FormLook, pose: PetPose, bw: Float, bh: Float, cy: Float, fx: Float) {
    val col = fl.colors
    for (s in intArrayOf(-1, 1)) {
        val sf = s.toFloat()
        val raise = if (s < 0) pose.armL else pose.armR
        val snap = (sin(pose.t * 4f + s) + 1f) / 2f
        c.save()
        c.translate(sf * bw * 0.98f + fx * 0.2f, cy + bh * 0.1f - raise * bh * 0.5f)
        c.rotate(sf * (20f - raise * 40f))
        val r = u * 0.2f
        val mouth = 40f + snap * 25f
        rect.set(-r, -r, r, r)
        path.reset()
        path.moveTo(0f, 0f)
        path.arcTo(rect, -90f + mouth / 2f, 360f - mouth, false)
        path.close()
        c.save()
        c.translate(sf * u * 0.1f, -u * 0.06f)
        shaded(c, path, Colors.lighten(col.accent, 0.4f), col.accent, Colors.darken(col.accent, 0.3f))
        c.restore()
        c.restore()
    }
}

internal fun PetRenderer.drawRing(c: Canvas, fl: FormLook, bw: Float, bh: Float, cy: Float, front: Boolean) {
    val col = fl.colors
    rect.set(-bw * 1.55f, cy - bh * 0.28f, bw * 1.55f, cy + bh * 0.42f)
    c.save()
    c.rotate(-10f, 0f, cy)
    line.shader = LinearGradient(-bw * 1.5f, 0f, bw * 1.5f, 0f, Colors.lighten(col.accent, 0.4f), Colors.darken(col.accent, 0.2f), Shader.TileMode.CLAMP)
    line.strokeWidth = u * 0.09f
    c.drawArc(rect, if (front) 0f else 180f, 180f, false, line)
    line.shader = null
    line.color = Colors.alpha(Color.WHITE, 0.5f)
    line.strokeWidth = u * 0.02f
    c.drawArc(rect, if (front) 10f else 190f, 160f, false, line)
    c.restore()
}

// ---------------------------------------------------------------------------------- eggs

internal fun PetRenderer.drawEggDecor(c: Canvas, eggLine: EggLine, col: PetColors, ew: Float, eh: Float, t: Float) {
    fill.shader = null
    when (eggLine) {
        EggLine.KNUFFEL -> Unit
        EggLine.WALD -> {
            for (i in 0 until 5) {
                val x = (hash(i, 1) - 0.5f) * ew * 1.5f
                val y = -eh * (0.3f + hash(i, 2) * 1.4f)
                c.save()
                c.rotate(hash(i, 3) * 180f, x, y)
                leaf(c, x, y, ew * 0.2f, ew * 0.08f, col.accent)
                c.restore()
            }
            fill.color = Colors.alpha(col.accent2, 0.35f)
            c.drawRect(-ew * 1.2f, -eh * 0.28f, ew * 1.2f, 0f, fill)
        }
        EggLine.MEER -> for (k in 0..1) {
            path3.reset()
            val y = -eh * (0.55f + k * 0.55f)
            path3.moveTo(-ew * 1.2f, y)
            var x = -ew * 1.2f
            while (x < ew * 1.2f) {
                path3.quadTo(x + ew * 0.15f, y - eh * 0.09f, x + ew * 0.3f, y)
                path3.quadTo(x + ew * 0.45f, y + eh * 0.09f, x + ew * 0.6f, y)
                x += ew * 0.6f
            }
            stroke(c, path3, Colors.alpha(col.accent, 0.85f), eh * 0.07f)
        }
        EggLine.FEUER -> for (i in 0 until 4) {
            val x = (i - 1.5f) * ew * 0.5f
            flame(c, x, -eh * 0.35f, ew * 0.2f * (1f + 0.1f * sin(t * 5f + i)), col.accent, col.accent2, 0.9f)
        }
        EggLine.URZEIT -> for (i in 0 until 6) {
            val x = (hash(i, 4) - 0.5f) * ew * 1.5f
            val y = -eh * (0.3f + hash(i, 6) * 1.4f)
            val r = ew * (0.08f + 0.1f * hash(i, 8))
            ovalPath(path3, x - r * 1.2f, y - r, x + r * 1.2f, y + r)
            shaded(c, path3, Colors.lighten(col.accent, 0.3f), col.accent, Colors.darken(col.accent, 0.2f), outline = false)
        }
        EggLine.TECHNO -> {
            path3.reset()
            for (k in 0..2) {
                val y = -eh * (0.5f + k * 0.4f)
                path3.moveTo(-ew * 1.1f, y)
                path3.lineTo(-ew * 0.3f, y)
                path3.lineTo(-ew * 0.1f, y - eh * 0.12f)
                path3.lineTo(ew * 1.1f, y - eh * 0.12f)
            }
            stroke(c, path3, Colors.alpha(col.accent, 0.8f), ew * 0.05f)
            val pulse = (sin(t * 4f) + 1f) / 2f
            fill.color = Colors.alpha(col.accent, 0.5f + 0.5f * pulse)
            for (k in 0..2) c.drawCircle(-ew * 0.3f, -eh * (0.5f + k * 0.4f), ew * 0.07f, fill)
        }
        EggLine.EINHORN -> {
            val colors = intArrayOf(0xFFFF8FB0.toInt(), 0xFFFFC98A.toInt(), 0xFFFFF08A.toInt(), 0xFF9AE8B0.toInt(), 0xFF9AD0FF.toInt(), 0xFFC4A8FF.toInt())
            for ((i, k) in colors.withIndex()) {
                rect.set(-ew * 1.5f + i * ew * 0.08f, -eh * 1.2f + i * eh * 0.07f, ew * 1.5f - i * ew * 0.08f, eh * 0.6f)
                line.shader = null
                line.color = Colors.alpha(k, 0.9f)
                line.strokeWidth = eh * 0.07f
                c.drawArc(rect, 200f, 140f, false, line)
            }
        }
        EggLine.BLUETE -> for (i in 0 until 4) {
            val x = (hash(i, 5) - 0.5f) * ew * 1.3f
            val y = -eh * (0.4f + hash(i, 7) * 1.2f)
            for (p in 0 until 5) {
                val a = p * TAU / 5
                fill.color = Colors.alpha(col.accent, 0.9f)
                c.drawCircle(x + cos(a) * ew * 0.08f, y + sin(a) * ew * 0.08f, ew * 0.07f, fill)
            }
            fill.color = 0xFFFFE066.toInt()
            c.drawCircle(x, y, ew * 0.05f, fill)
        }
        EggLine.STERN -> for (i in 0 until 7) {
            val x = (hash(i, 9) - 0.5f) * ew * 1.5f
            val y = -eh * (0.25f + hash(i, 1) * 1.55f)
            val tw = (sin(t * 3f + i) + 1f) / 2f
            Shapes.softStar(path3, x, y, ew * (0.07f + 0.03f * tw), ew * 0.03f, 5, 0f)
            fill.color = Colors.alpha(col.accent, 0.6f + 0.4f * tw)
            c.drawPath(path3, fill)
        }
        EggLine.ZUCKER -> {
            c.save()
            c.rotate(-30f)
            var x = -ew * 3f
            var k = 0
            while (x < ew * 3f) {
                fill.color = Colors.alpha(if (k % 2 == 0) col.accent else col.accent2, 0.75f)
                c.drawRect(x, -eh * 3f, x + ew * 0.18f, eh * 3f, fill)
                x += ew * 0.42f
                k++
            }
            c.restore()
        }
        EggLine.FROST -> {
            for (i in 0 until 5) {
                val x = (hash(i, 3) - 0.5f) * ew * 1.4f
                val y = -eh * (0.35f + hash(i, 5) * 1.3f)
                snowflake(c, x, y, ew * 0.12f, Colors.alpha(col.accent, 0.9f), 0f)
            }
            fill.color = Colors.alpha(Color.WHITE, 0.6f)
            c.drawRect(-ew * 1.2f, -eh * 0.22f, ew * 1.2f, 0f, fill)
        }
        EggLine.GRUSEL -> {
            path3.reset()
            for (k in -2..2) {
                if (k == 0) continue
                val x = k * ew * 0.35f
                path3.moveTo(x * 0.3f, -eh * 2f)
                path3.quadTo(x * 1.5f, -eh, x * 0.5f, 0f)
            }
            stroke(c, path3, Colors.alpha(Colors.darken(col.body, 0.25f), 0.55f), ew * 0.05f)
            // jack-o'-lantern face
            fill.color = col.accent
            for (s in intArrayOf(-1, 1)) {
                path3.reset()
                path3.moveTo(s * ew * 0.35f, -eh * 1.15f)
                path3.lineTo(s * ew * 0.18f, -eh * 0.95f)
                path3.lineTo(s * ew * 0.52f, -eh * 0.95f)
                path3.close()
                c.drawPath(path3, fill)
            }
            path3.reset()
            path3.moveTo(-ew * 0.45f, -eh * 0.72f)
            path3.quadTo(0f, -eh * 0.45f, ew * 0.45f, -eh * 0.72f)
            path3.quadTo(0f, -eh * 0.6f, -ew * 0.45f, -eh * 0.72f)
            c.drawPath(path3, fill)
        }
    }
}
