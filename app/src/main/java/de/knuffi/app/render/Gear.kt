package de.knuffi.app.render

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Path
import android.graphics.Shader
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

// Special festival equipment. Every piece has a perk in the game, so it gets a little shine.

/** Small twinkle that marks special equipment. */
private fun PetRenderer.twinkle(c: Canvas, x: Float, y: Float, t: Float, phase: Float) {
    val tw = (sin(t * 3f + phase) + 1f) / 2f
    Shapes.sparkle(path3, x, y, u * (0.02f + 0.03f * tw))
    fill.shader = null
    fill.color = Colors.alpha(Color.WHITE, 0.35f + 0.6f * tw)
    c.drawPath(path3, fill)
}

internal fun PetRenderer.drawGearHat(c: Canvas, id: String, pose: PetPose, bw: Float, bh: Float, top: Float, fx: Float) {
    val hx = fx * 0.45f
    val t = pose.t
    when (id) {
        "hat_lucky" -> {
            c.save()
            c.rotate(-6f, hx, top)
            val base = top + bh * 0.1f
            path.reset()
            path.addRoundRect(hx - bw * 0.4f, base - u * 0.5f, hx + bw * 0.4f, base, u * 0.04f, u * 0.04f, Path.Direction.CW)
            shaded(c, path, 0xFF7FE09A.toInt(), 0xFF2E9E54.toInt(), 0xFF145A2C.toInt())
            shaderFill(LinearGradient(0f, base - u * 0.16f, 0f, base - u * 0.06f, 0xFFFFE680.toInt(), 0xFFD9A000.toInt(), Shader.TileMode.CLAMP))
            c.drawRect(hx - bw * 0.4f, base - u * 0.16f, hx + bw * 0.4f, base - u * 0.06f, fill)
            fill.shader = null
            ovalPath(path, hx - bw * 0.72f, base - u * 0.07f, hx + bw * 0.72f, base + u * 0.08f)
            shaded(c, path, 0xFF5AC87A.toInt(), 0xFF22874A.toInt(), 0xFF0F4A24.toInt())
            // clover
            val cx = hx + bw * 0.2f
            val cy = base - u * 0.32f
            for (i in 0 until 4) {
                val a = i * (PI / 2).toFloat() + PI.toFloat() / 4f + sin(t * 2f) * 0.1f
                ovalPath(path2, cx + cos(a) * u * 0.05f - u * 0.05f, cy + sin(a) * u * 0.05f - u * 0.05f, cx + cos(a) * u * 0.05f + u * 0.05f, cy + sin(a) * u * 0.05f + u * 0.05f)
                shaded(c, path2, 0xFFB8F5A8.toInt(), 0xFF5ED15A.toInt(), 0xFF2E8A2E.toInt(), outline = false)
            }
            c.restore()
            twinkle(c, hx + bw * 0.45f, base - u * 0.5f, t, 0f)
        }
        "hat_heartband" -> {
            val base = top + bh * 0.24f
            rect.set(hx - bw * 0.72f, base - u * 0.12f, hx + bw * 0.72f, base + u * 0.32f)
            line.shader = null
            line.color = 0xFFFF5C9A.toInt()
            line.strokeWidth = u * 0.055f
            c.drawArc(rect, 195f, 150f, false, line)
            for (s in intArrayOf(-1, 1)) {
                val bob = sin(t * 4f + s) * u * 0.04f
                val sx = hx + s * bw * 0.32f
                val sy = base - u * 0.08f
                val tx = sx + s * u * 0.08f
                val ty = sy - u * 0.32f + bob
                path3.reset()
                path3.moveTo(sx, sy)
                var k = 0f
                while (k <= 1f) {
                    val x = sx + (tx - sx) * k + sin(k * 18f) * u * 0.02f
                    val y = sy + (ty - sy) * k
                    path3.lineTo(x, y)
                    k += 0.05f
                }
                stroke(c, path3, 0xFFB8B0C8.toInt(), u * 0.015f)
                Shapes.heart(path, tx, ty, u * 0.2f)
                shaded(c, path, 0xFFFFB8D6.toInt(), 0xFFFF4F8B.toInt(), 0xFFB81E5A.toInt())
                gloss(c, tx - u * 0.03f, ty - u * 0.03f, u * 0.03f, u * 0.02f, 0.9f)
            }
            twinkle(c, hx, base - u * 0.42f, t, 1f)
        }
        "hat_eggshell" -> {
            val base = top + bh * 0.32f
            path.reset()
            path.moveTo(hx - bw * 0.6f, base)
            path.cubicTo(hx - bw * 0.62f, base - u * 0.55f, hx + bw * 0.62f, base - u * 0.55f, hx + bw * 0.6f, base)
            // jagged lower edge
            var x = hx + bw * 0.6f
            var up = true
            while (x > hx - bw * 0.6f) {
                x -= bw * 0.15f
                path.lineTo(x, base + if (up) u * 0.07f else -u * 0.02f)
                up = !up
            }
            path.close()
            shaded(c, path, Color.WHITE, 0xFFFFF6E6.toInt(), 0xFFE0C8A0.toInt())
            c.save()
            c.clipPath(path)
            fill.shader = null
            fill.color = Colors.alpha(0xFFFFD24D.toInt(), 0.7f)
            c.drawCircle(hx - bw * 0.25f, base - u * 0.28f, u * 0.05f, fill)
            fill.color = Colors.alpha(0xFF8FD3FF.toInt(), 0.7f)
            c.drawCircle(hx + bw * 0.2f, base - u * 0.18f, u * 0.045f, fill)
            fill.color = Colors.alpha(0xFFFF8FB8.toInt(), 0.7f)
            c.drawCircle(hx + bw * 0.05f, base - u * 0.38f, u * 0.035f, fill)
            c.restore()
            gloss(c, hx - bw * 0.25f, base - u * 0.36f, u * 0.08f, u * 0.04f, 0.8f)
            twinkle(c, hx + bw * 0.55f, base - u * 0.4f, t, 2f)
        }
        "hat_sunvisor" -> {
            val base = top + bh * 0.28f
            path.reset()
            path.addRoundRect(hx - bw * 0.7f, base - u * 0.1f, hx + bw * 0.7f, base + u * 0.02f, u * 0.05f, u * 0.05f, Path.Direction.CW)
            shaded(c, path, 0xFFFFE08A.toInt(), 0xFFFFB02E.toInt(), 0xFFC07000.toInt())
            val dir = if (pose.turn < -0.05f) -1f else 1f
            path.reset()
            path.moveTo(hx - bw * 0.55f, base)
            path.quadTo(hx + dir * bw * 0.2f, base + u * 0.2f, hx + dir * bw * 0.95f, base + u * 0.04f)
            path.quadTo(hx + dir * bw * 0.3f, base - u * 0.04f, hx + bw * 0.55f, base)
            path.close()
            shaderFill(LinearGradient(0f, base, 0f, base + u * 0.2f, Colors.alpha(0xFF4FD1C5.toInt(), 0.9f), Colors.alpha(0xFF1E8A9A.toInt(), 0.9f), Shader.TileMode.CLAMP))
            c.drawPath(path, fill)
            fill.shader = null
            outline(c, path, 0xFF0E5A6A.toInt())
            Shapes.softStar(path3, hx, base - u * 0.04f, u * 0.05f, u * 0.024f, 5, 0f)
            fill.color = Color.WHITE
            c.drawPath(path3, fill)
            twinkle(c, hx + dir * bw * 0.8f, base + u * 0.06f, t, 3f)
        }
        "hat_pumpkinlamp" -> {
            val cx = hx + bw * 0.12f
            val cy = top - u * 0.02f + sin(t * 2f) * u * 0.012f
            val r = u * 0.2f
            val glow = 0.6f + 0.4f * sin(t * 5f)
            shaderFill(radial(cx, cy, r * 2.2f, intArrayOf(Colors.alpha(0xFFFFB84D.toInt(), 0.45f * glow), Colors.alpha(0xFFFFB84D.toInt(), 0f))))
            c.drawCircle(cx, cy, r * 2.2f, fill)
            fill.shader = null
            for (k in -1..1) {
                ovalPath(path, cx + k * r * 0.5f - r * 0.55f, cy - r * 0.8f, cx + k * r * 0.5f + r * 0.55f, cy + r * 0.8f)
                shaded(c, path, 0xFFFFC07A.toInt(), 0xFFFF8A2E.toInt(), 0xFFB84A00.toInt())
            }
            fill.color = Colors.lerp(0xFFFFE066.toInt(), 0xFFFFFFFF.toInt(), 0.3f * glow)
            path3.reset()
            path3.moveTo(cx - r * 0.45f, cy - r * 0.15f)
            path3.lineTo(cx - r * 0.2f, cy - r * 0.45f)
            path3.lineTo(cx - r * 0.05f, cy - r * 0.15f)
            path3.close()
            path3.moveTo(cx + r * 0.45f, cy - r * 0.15f)
            path3.lineTo(cx + r * 0.2f, cy - r * 0.45f)
            path3.lineTo(cx + r * 0.05f, cy - r * 0.15f)
            path3.close()
            c.drawPath(path3, fill)
            rect.set(cx - r * 0.45f, cy - r * 0.1f, cx + r * 0.45f, cy + r * 0.45f)
            c.drawArc(rect, 10f, 160f, true, fill)
            path.reset()
            path.moveTo(cx - u * 0.02f, cy - r * 0.75f)
            path.quadTo(cx, cy - r * 1.3f, cx + u * 0.06f, cy - r * 1.35f)
            stroke(c, path, 0xFF4E8A3A.toInt(), u * 0.04f)
            twinkle(c, cx - r * 1.2f, cy - r, t, 4f)
        }
        "hat_icecrown" -> {
            val base = top + bh * 0.1f
            val w = bw * 0.46f
            val h = u * 0.36f
            val x0 = fx * 0.4f
            shaderFill(radial(x0, base - h * 0.5f, w * 1.6f, intArrayOf(Colors.alpha(0xFFBDE6FF.toInt(), 0.5f), Colors.alpha(0xFFBDE6FF.toInt(), 0f))))
            c.drawCircle(x0, base - h * 0.5f, w * 1.6f, fill)
            fill.shader = null
            path.reset()
            path.moveTo(x0 - w, base)
            path.lineTo(x0 - w * 1.05f, base - h * 0.55f)
            path.lineTo(x0 - w * 0.6f, base - h * 0.3f)
            path.lineTo(x0 - w * 0.3f, base - h * 0.85f)
            path.lineTo(x0, base - h * 0.4f)
            path.lineTo(x0 + w * 0.3f, base - h * 0.85f)
            path.lineTo(x0 + w * 0.6f, base - h * 0.3f)
            path.lineTo(x0 + w * 1.05f, base - h * 0.55f)
            path.lineTo(x0 + w, base)
            path.close()
            shaded(c, path, Color.WHITE, 0xFFA8DCFF.toInt(), 0xFF4A90C8.toInt())
            gem(c, x0, base - h * 0.2f, h * 0.12f, 0xFF7AD8FF.toInt())
            gem(c, x0 - w * 0.3f, base - h * 0.72f, h * 0.07f, 0xFFFFFFFF.toInt())
            gem(c, x0 + w * 0.3f, base - h * 0.72f, h * 0.07f, 0xFFFFFFFF.toInt())
            twinkle(c, x0 + w * 0.8f, base - h, t, 5f)
            twinkle(c, x0 - w * 0.9f, base - h * 0.8f, t, 2.5f)
        }
        else -> drawMoreHat(c, id, pose, bw, bh, top, fx)
    }
}

internal fun PetRenderer.drawGearFace(c: Canvas, id: String, pose: PetPose, bw: Float, bh: Float, cy: Float, fx: Float) {
    val re = u * 0.135f
    val ex = bw * 0.36f * (1f - 0.1f * kotlin.math.abs(pose.turn))
    val ey = cy - bh * 0.1f
    when (id) {
        "face_starshades" -> {
            for (s in intArrayOf(-1, 1)) {
                val x = fx + s * ex
                Shapes.softStar(path, x, ey, re * 1.75f, re * 0.95f, 5, 0f)
                shaderFill(LinearGradient(0f, ey - re * 1.6f, 0f, ey + re * 1.4f, 0xFFFFC94D.toInt(), 0xFFFF6A3D.toInt(), Shader.TileMode.CLAMP))
                c.drawPath(path, fill)
                fill.shader = null
                Shapes.softStar(path2, x, ey + re * 0.05f, re * 1.3f, re * 0.68f, 5, 0f)
                shaderFill(LinearGradient(0f, ey - re, 0f, ey + re, 0xFF4A3A68.toInt(), 0xFF140C22.toInt(), Shader.TileMode.CLAMP))
                c.drawPath(path2, fill)
                fill.shader = null
                path3.reset()
                path3.moveTo(x - re * 0.7f, ey - re * 0.1f)
                path3.lineTo(x - re * 0.25f, ey - re * 0.55f)
                stroke(c, path3, Colors.alpha(Color.WHITE, 0.75f), re * 0.18f)
            }
            path.reset()
            path.moveTo(fx - ex + re * 1.2f, ey - re * 0.35f)
            path.quadTo(fx, ey - re * 0.7f, fx + ex - re * 1.2f, ey - re * 0.35f)
            stroke(c, path, 0xFFFF8A3D.toInt(), u * 0.035f)
            twinkle(c, fx + ex + re * 1.4f, ey - re * 1.4f, pose.t, 1.5f)
        }
        else -> drawMoreFace(c, id, pose, bw, bh, cy, fx)
    }
}

internal fun PetRenderer.drawGearNeck(c: Canvas, id: String, pose: PetPose, bw: Float, bh: Float, cy: Float, fx: Float) {
    val neckY = cy + bh * 0.55f
    val t = pose.t
    val x = fx * 0.6f
    when (id) {
        "neck_medal" -> {
            for (s in intArrayOf(-1, 1)) {
                path.reset()
                path.moveTo(x + s * bw * 0.5f, neckY - bh * 0.22f)
                path.lineTo(x + s * u * 0.03f, neckY + u * 0.04f)
                path.lineTo(x + s * u * 0.11f, neckY + u * 0.04f)
                path.lineTo(x + s * bw * 0.62f, neckY - bh * 0.22f)
                path.close()
                shaded(c, path, 0xFFFF8A9A.toInt(), if (s < 0) 0xFFE0304A.toInt() else 0xFF3A5AD6.toInt(), 0xFF6A1422.toInt(), outline = false)
            }
            val swing = sin(t * 3f) * u * 0.012f
            val my = neckY + u * 0.13f
            ovalPath(path, x + swing - u * 0.11f, my - u * 0.11f, x + swing + u * 0.11f, my + u * 0.11f)
            shaded(c, path, 0xFFFFF4C2.toInt(), 0xFFFFC83D.toInt(), 0xFFB87A00.toInt())
            Shapes.softStar(path3, x + swing, my, u * 0.065f, u * 0.03f, 5, 0f)
            fill.shader = null
            fill.color = 0xFFFFF4C2.toInt()
            c.drawPath(path3, fill)
            twinkle(c, x + swing + u * 0.1f, my - u * 0.1f, t, 0.5f)
        }
        "neck_rosechain" -> {
            for (i in 0 until 7) {
                val k = (i - 3) / 3f
                val rx = x + k * bw * 0.72f
                val ry = neckY - bh * 0.12f * k * k + bh * 0.02f
                if (i % 2 == 1) {
                    c.save()
                    c.rotate(k * 40f, rx, ry)
                    leaf(c, rx, ry + u * 0.03f, u * 0.06f, u * 0.025f, 0xFF4E9E4A.toInt())
                    c.restore()
                }
                ovalPath(path, rx - u * 0.055f, ry - u * 0.055f, rx + u * 0.055f, ry + u * 0.055f)
                shaded(c, path, 0xFFFF8A9A.toInt(), 0xFFE0304A.toInt(), 0xFF8A1022.toInt(), outline = false)
                Shapes.spiral(path3, rx, ry, u * 0.04f, 1.6f, i * 40f)
                stroke(c, path3, Colors.alpha(0xFF6A0A18.toInt(), 0.6f), u * 0.01f)
            }
            twinkle(c, x + bw * 0.6f, neckY - bh * 0.2f, t, 1f)
        }
        "neck_carrotscarf" -> {
            val a = 0xFFFF9A3D.toInt()
            val b = 0xFFC2551F.toInt()
            c.save()
            c.clipPath(body)
            shaderFill(LinearGradient(0f, neckY - bh * 0.18f, 0f, neckY + bh * 0.1f, a, b, Shader.TileMode.CLAMP))
            c.drawRect(-bw * 2f, neckY - bh * 0.18f, bw * 2f, neckY + bh * 0.1f, fill)
            fill.shader = null
            fill.color = Colors.alpha(0xFF8A3A12.toInt(), 0.45f)
            var sx = -bw
            while (sx < bw) {
                c.drawRect(sx, neckY - bh * 0.1f, sx + bw * 0.08f, neckY - bh * 0.08f, fill)
                sx += bw * 0.22f
            }
            c.restore()
            c.save()
            c.rotate(-8f + sin(t * 2f) * 5f, fx + bw * 0.35f, neckY)
            path.reset()
            path.addRoundRect(fx + bw * 0.26f, neckY - bh * 0.05f, fx + bw * 0.5f, neckY + bh * 0.55f, u * 0.05f, u * 0.05f, Path.Direction.CW)
            shaded(c, path, Colors.lighten(a, 0.3f), a, b)
            for (k in -1..1) {
                c.save()
                c.rotate(k * 22f, fx + bw * 0.38f, neckY + bh * 0.55f)
                leaf(c, fx + bw * 0.38f, neckY + bh * 0.66f, u * 0.09f, u * 0.03f, 0xFF5ED15A.toInt())
                c.restore()
            }
            c.restore()
            twinkle(c, fx - bw * 0.5f, neckY - bh * 0.2f, t, 2f)
        }
        "neck_batbow" -> {
            val flap = sin(t * 6f) * 8f
            for (s in intArrayOf(-1, 1)) {
                val sf = s.toFloat()
                c.save()
                c.rotate(sf * flap, x, neckY)
                path.reset()
                path.moveTo(x, neckY)
                path.quadTo(x + sf * u * 0.1f, neckY - u * 0.18f, x + sf * u * 0.3f, neckY - u * 0.12f)
                path.quadTo(x + sf * u * 0.24f, neckY - u * 0.04f, x + sf * u * 0.28f, neckY + u * 0.03f)
                path.quadTo(x + sf * u * 0.2f, neckY + u * 0.0f, x + sf * u * 0.18f, neckY + u * 0.08f)
                path.quadTo(x + sf * u * 0.1f, neckY + u * 0.04f, x, neckY)
                path.close()
                shaded(c, path, 0xFF8A6ACF.toInt(), 0xFF3A2270.toInt(), 0xFF140A30.toInt())
                c.restore()
            }
            ovalPath(path, x - u * 0.05f, neckY - u * 0.05f, x + u * 0.05f, neckY + u * 0.05f)
            shaded(c, path, 0xFF6A4AAF.toInt(), 0xFF2A1450.toInt(), 0xFF0A0418.toInt())
            fill.shader = null
            fill.color = 0xFFFFE066.toInt()
            c.drawCircle(x - u * 0.018f, neckY - u * 0.01f, u * 0.01f, fill)
            c.drawCircle(x + u * 0.018f, neckY - u * 0.01f, u * 0.01f, fill)
            twinkle(c, x + u * 0.32f, neckY - u * 0.16f, t, 3f)
        }
        "neck_lights" -> {
            path.reset()
            path.moveTo(x - bw * 0.85f, neckY - bh * 0.24f)
            path.quadTo(x, neckY + bh * 0.12f, x + bw * 0.85f, neckY - bh * 0.24f)
            stroke(c, path, 0xFF2E5A2E.toInt(), u * 0.016f)
            val colors = intArrayOf(0xFFFF5C5C.toInt(), 0xFFFFD24D.toInt(), 0xFF5CD6FF.toInt(), 0xFF7AE07A.toInt(), 0xFFFF7AD0.toInt())
            for (i in 0 until 7) {
                val k = (i - 3) / 3.4f
                val bx = x + k * bw * 0.8f
                val by = neckY - bh * 0.24f * k * k + bh * 0.03f + u * 0.04f
                val on = (sin(t * 4f + i * 1.3f) + 1f) / 2f
                val col = colors[i % colors.size]
                shaderFill(radial(bx, by, u * 0.09f, intArrayOf(Colors.alpha(col, 0.55f * on), Colors.alpha(col, 0f))))
                c.drawCircle(bx, by, u * 0.09f, fill)
                fill.shader = null
                ovalPath(path, bx - u * 0.028f, by - u * 0.04f, bx + u * 0.028f, by + u * 0.04f)
                shaded(c, path, Colors.lighten(col, 0.5f + 0.3f * on), Colors.lerp(Colors.darken(col, 0.3f), col, 0.5f + 0.5f * on), Colors.darken(col, 0.4f), outline = false)
            }
        }
        else -> drawMoreNeck(c, id, pose, bw, bh, cy, fx)
    }
}
