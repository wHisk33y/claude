package de.knuffi.app.render

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Path
import android.graphics.Shader
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

// Wearables added with the seasons, events and looks. Same paints and helpers as PetRenderer.

internal fun PetRenderer.drawCapeBehind(c: Canvas, pose: PetPose, bw: Float, bh: Float, cy: Float) {
    val sway = sin(pose.t * 2f) * bw * 0.08f
    path.reset()
    path.moveTo(-bw * 0.6f, cy + bh * 0.4f)
    path.lineTo(bw * 0.6f, cy + bh * 0.4f)
    path.quadTo(bw * 1.3f + sway, cy + bh * 0.8f, bw * 1.25f + sway, cy + bh * 1.2f)
    path.quadTo(sway, cy + bh * 1.05f, -bw * 1.25f + sway, cy + bh * 1.2f)
    path.quadTo(-bw * 1.3f + sway, cy + bh * 0.8f, -bw * 0.6f, cy + bh * 0.4f)
    path.close()
    shaded(c, path, 0xFFFF8A8A.toInt(), 0xFFE0303F.toInt(), 0xFF8E1422.toInt())
}

internal fun PetRenderer.drawExtraNeck(c: Canvas, id: String, pose: PetPose, bw: Float, bh: Float, cy: Float, fx: Float) {
    val neckY = cy + bh * 0.55f
    val t = pose.t
    when (id) {
        "neck_cape" -> {
            val x = fx * 0.6f
            for (s in intArrayOf(-1, 1)) {
                path.reset()
                path.moveTo(x, neckY)
                path.lineTo(x + s * bw * 0.55f, neckY - bh * 0.12f)
                stroke(c, path, 0xFFB81E2E.toInt(), u * 0.05f)
            }
            ovalPath(path, x - u * 0.075f, neckY - u * 0.075f, x + u * 0.075f, neckY + u * 0.075f)
            shaded(c, path, 0xFFFFF4C2.toInt(), 0xFFFFC83D.toInt(), 0xFFC08000.toInt())
            Shapes.softStar(path3, x, neckY, u * 0.05f, u * 0.024f, 5, 0f)
            fill.shader = null
            fill.color = 0xFFE0303F.toInt()
            c.drawPath(path3, fill)
        }
        "neck_flowerchain" -> {
            val colors = intArrayOf(0xFFFFD84D.toInt(), 0xFFFF8FB8.toInt(), 0xFFFFFFFF.toInt(), 0xFFB99AFF.toInt())
            for (i in 0 until 7) {
                val k = (i - 3) / 3f
                val x = fx * 0.6f + k * bw * 0.72f
                val y = neckY - bh * 0.12f * k * k + bh * 0.02f
                for (p in 0 until 5) {
                    val a = p * (PI * 2 / 5).toFloat() + i
                    fill.shader = null
                    fill.color = colors[i % colors.size]
                    c.drawCircle(x + cos(a) * u * 0.045f, y + sin(a) * u * 0.045f, u * 0.04f, fill)
                }
                fill.color = if (colors[i % colors.size] == 0xFFFFD84D.toInt()) 0xFFFF9A3D.toInt() else 0xFFFFD84D.toInt()
                c.drawCircle(x, y, u * 0.028f, fill)
            }
        }
        "neck_leafscarf", "neck_starscarf" -> {
            val leaf = id == "neck_leafscarf"
            val a = if (leaf) 0xFFFFA24D.toInt() else 0xFF4C6BD6.toInt()
            val b = if (leaf) 0xFFC2551F.toInt() else 0xFF22307A.toInt()
            c.save()
            c.clipPath(body)
            shaderFill(LinearGradient(0f, neckY - bh * 0.18f, 0f, neckY + bh * 0.1f, a, b, Shader.TileMode.CLAMP))
            c.drawRect(-bw * 2f, neckY - bh * 0.18f, bw * 2f, neckY + bh * 0.1f, fill)
            fill.shader = null
            var sx = -bw * 0.9f
            var i = 0
            while (sx < bw) {
                if (leaf) {
                    leaf(c, sx, neckY - bh * 0.04f, u * 0.05f, u * 0.022f, if (i % 2 == 0) 0xFFFFD84D.toInt() else 0xFF8A3A12.toInt())
                } else {
                    Shapes.softStar(path3, sx, neckY - bh * 0.04f, u * 0.04f, u * 0.018f, 5, 0f)
                    fill.color = 0xFFFFE680.toInt()
                    c.drawPath(path3, fill)
                }
                sx += bw * 0.3f
                i++
            }
            c.restore()
            c.save()
            c.rotate(-8f + sin(t * 2f) * 5f, fx + bw * 0.35f, neckY)
            path.reset()
            path.addRoundRect(fx + bw * 0.26f, neckY - bh * 0.05f, fx + bw * 0.5f, neckY + bh * 0.6f, u * 0.05f, u * 0.05f, Path.Direction.CW)
            shaded(c, path, Colors.lighten(a, 0.3f), a, b)
            fill.shader = null
            fill.color = Colors.alpha(Color.WHITE, 0.6f)
            for (k in 0..2) c.drawRect(fx + bw * (0.28f + k * 0.08f), neckY + bh * 0.52f, fx + bw * (0.3f + k * 0.08f), neckY + bh * 0.66f, fill)
            c.restore()
        }
        "neck_heart" -> {
            path.reset()
            path.moveTo(fx * 0.6f - bw * 0.55f, neckY - bh * 0.2f)
            path.quadTo(fx * 0.6f, neckY + bh * 0.1f, fx * 0.6f + bw * 0.55f, neckY - bh * 0.2f)
            stroke(c, path, 0xFFE8C46A.toInt(), u * 0.022f)
            val swing = sin(t * 3f) * u * 0.015f
            Shapes.heart(path, fx * 0.6f + swing, neckY + u * 0.04f, u * 0.2f)
            shaded(c, path, 0xFFFFB8D6.toInt(), 0xFFFF4F8B.toInt(), 0xFFB81E5A.toInt())
            gloss(c, fx * 0.6f + swing - u * 0.04f, neckY, u * 0.04f, u * 0.025f, 0.9f)
        }
    }
}

internal fun PetRenderer.drawExtraFace(c: Canvas, id: String, pose: PetPose, bw: Float, bh: Float, cy: Float, fx: Float) {
    val re = u * 0.135f
    val ex = bw * 0.36f * (1f - 0.1f * kotlin.math.abs(pose.turn))
    val ey = cy - bh * 0.1f
    when (id) {
        "face_snorkel" -> {
            path.reset()
            path.moveTo(-bw * 1.02f, ey - re * 0.2f)
            path.lineTo(bw * 1.02f, ey - re * 0.2f)
            stroke(c, path, 0xFF2E3A5A.toInt(), u * 0.05f)
            path.reset()
            path.addRoundRect(fx - ex - re * 1.5f, ey - re * 1.25f, fx + ex + re * 1.5f, ey + re * 1.2f, re * 0.9f, re * 0.9f, Path.Direction.CW)
            fill.shader = null
            fill.color = Colors.alpha(0xFFA8E4FF.toInt(), 0.35f)
            c.drawPath(path, fill)
            stroke(c, path, 0xFFFF7A3D.toInt(), u * 0.05f)
            gloss(c, fx - ex - re * 0.4f, ey - re * 0.6f, re * 0.7f, re * 0.25f, 0.9f)
            val side = if (pose.turn > 0.05f) -1f else 1f
            val sx = fx + side * (ex + re * 1.6f)
            path.reset()
            path.moveTo(sx, ey + re * 0.6f)
            path.lineTo(sx + side * u * 0.05f, ey - re * 1.2f)
            path.lineTo(sx + side * u * 0.05f, ey - re * 3.2f)
            stroke(c, path, 0xFFFFD84D.toInt(), u * 0.06f)
            path.reset()
            path.moveTo(sx + side * u * 0.05f, ey - re * 2f)
            path.lineTo(sx + side * u * 0.05f, ey - re * 2.4f)
            stroke(c, path, 0xFFFF7A3D.toInt(), u * 0.07f)
        }
        "face_stars" -> {
            for (s in intArrayOf(-1, 1)) {
                val x = fx + s * ex
                Shapes.softStar(path, x, ey, re * 1.7f, re * 0.95f, 5, 0f)
                fill.shader = null
                fill.color = Colors.alpha(0xFFFFF3B0.toInt(), 0.3f)
                c.drawPath(path, fill)
                line.shader = null
                line.color = 0xFFFFC83D.toInt()
                line.strokeWidth = u * 0.04f
                c.drawPath(path, line)
                line.color = Colors.alpha(Color.WHITE, 0.7f)
                line.strokeWidth = u * 0.012f
                c.drawPath(path, line)
            }
            path.reset()
            path.moveTo(fx - ex + re * 1.1f, ey - re * 0.3f)
            path.quadTo(fx, ey - re * 0.7f, fx + ex - re * 1.1f, ey - re * 0.3f)
            stroke(c, path, 0xFFE0A000.toInt(), u * 0.035f)
        }
    }
}

internal fun PetRenderer.drawExtraHat(c: Canvas, id: String, pose: PetPose, bw: Float, bh: Float, top: Float, fx: Float) {
    val hx = fx * 0.45f
    val t = pose.t
    when (id) {
        "hat_explorer", "hat_straw" -> {
            val straw = id == "hat_straw"
            val base = top + bh * 0.2f
            val main = if (straw) 0xFFF2CF6A.toInt() else 0xFFB98A55.toInt()
            ovalPath(path, hx - bw * 1.08f, base - u * 0.1f, hx + bw * 1.08f, base + u * 0.1f)
            shaded(c, path, Colors.lighten(main, 0.35f), main, Colors.darken(main, 0.35f))
            path.reset()
            path.moveTo(hx - bw * 0.55f, base)
            path.cubicTo(hx - bw * 0.58f, base - u * 0.45f, hx + bw * 0.58f, base - u * 0.45f, hx + bw * 0.55f, base)
            path.close()
            shaded(c, path, Colors.lighten(main, 0.4f), main, Colors.darken(main, 0.3f))
            val band = if (straw) 0xFFFF5C7A.toInt() else 0xFF5A3A22.toInt()
            c.save()
            c.clipPath(path)
            fill.shader = null
            fill.color = band
            c.drawRect(hx - bw, base - u * 0.14f, hx + bw, base - u * 0.04f, fill)
            if (straw) {
                path3.reset()
                var x = hx - bw * 0.6f
                while (x < hx + bw * 0.6f) {
                    path3.moveTo(x, base - u * 0.4f)
                    path3.lineTo(x + u * 0.05f, base)
                    x += u * 0.07f
                }
                stroke(c, path3, Colors.alpha(Colors.darken(main, 0.3f), 0.35f), u * 0.01f)
            }
            c.restore()
            if (straw) {
                for (s in intArrayOf(-1, 1)) {
                    val x = hx + bw * 0.5f
                    ovalPath(path3, x + s * u * 0.06f - u * 0.05f, base - u * 0.14f, x + s * u * 0.06f + u * 0.05f, base - u * 0.04f)
                    fill.color = 0xFFFF5C7A.toInt()
                    c.drawPath(path3, fill)
                }
            }
        }
        "hat_tiara" -> {
            val base = top + bh * 0.16f
            val w = bw * 0.5f
            path.reset()
            path.moveTo(hx - w, base)
            path.quadTo(hx - w * 0.6f, base - u * 0.14f, hx - w * 0.3f, base - u * 0.12f)
            path.lineTo(hx, base - u * 0.3f)
            path.lineTo(hx + w * 0.3f, base - u * 0.12f)
            path.quadTo(hx + w * 0.6f, base - u * 0.14f, hx + w, base)
            path.quadTo(hx, base - u * 0.07f, hx - w, base)
            path.close()
            shaded(c, path, 0xFFFFFFFF.toInt(), 0xFFE8E4F8.toInt(), 0xFFA8A0C8.toInt())
            gem(c, hx, base - u * 0.17f, u * 0.06f, 0xFFFF6FB5.toInt())
            gem(c, hx - w * 0.55f, base - u * 0.07f, u * 0.035f, 0xFF8FD3FF.toInt())
            gem(c, hx + w * 0.55f, base - u * 0.07f, u * 0.035f, 0xFF8FD3FF.toInt())
            val tw = (sin(t * 3f) + 1f) / 2f
            Shapes.sparkle(path3, hx + w * 0.2f, base - u * 0.32f, u * (0.03f + 0.03f * tw))
            fill.shader = null
            fill.color = Colors.alpha(Color.WHITE, 0.5f + 0.5f * tw)
            c.drawPath(path3, fill)
        }
        "hat_pirate" -> {
            val base = top + bh * 0.2f
            path.reset()
            path.moveTo(hx - bw * 0.95f, base)
            path.quadTo(hx - bw * 0.9f, base - u * 0.3f, hx - bw * 0.5f, base - u * 0.22f)
            path.quadTo(hx, base - u * 0.55f, hx + bw * 0.5f, base - u * 0.22f)
            path.quadTo(hx + bw * 0.9f, base - u * 0.3f, hx + bw * 0.95f, base)
            path.quadTo(hx, base - u * 0.1f, hx - bw * 0.95f, base)
            path.close()
            shaded(c, path, 0xFF5A5470.toInt(), 0xFF2A2638.toInt(), 0xFF100E18.toInt())
            path3.reset()
            path3.moveTo(hx - bw * 0.9f, base - u * 0.02f)
            path3.quadTo(hx, base - u * 0.13f, hx + bw * 0.9f, base - u * 0.02f)
            stroke(c, path3, 0xFFFFC83D.toInt(), u * 0.025f)
            val sy = base - u * 0.27f
            fill.shader = null
            fill.color = Color.WHITE
            c.drawCircle(hx, sy, u * 0.07f, fill)
            c.drawRect(hx - u * 0.04f, sy + u * 0.03f, hx + u * 0.04f, sy + u * 0.09f, fill)
            fill.color = 0xFF2A2638.toInt()
            c.drawCircle(hx - u * 0.028f, sy - u * 0.005f, u * 0.018f, fill)
            c.drawCircle(hx + u * 0.028f, sy - u * 0.005f, u * 0.018f, fill)
        }
        "hat_flowercrown" -> {
            val colors = intArrayOf(0xFFFF8FB8.toInt(), 0xFFFFD84D.toInt(), 0xFFB99AFF.toInt(), 0xFFFFFFFF.toInt(), 0xFF8FD3FF.toInt())
            val base = top + bh * 0.2f
            for (i in 0 until 7) {
                val k = (i - 3) / 3f
                val x = hx + k * bw * 0.72f
                val y = base + bh * 0.12f * k * k - u * 0.02f
                leaf(c, x + u * 0.06f, y + u * 0.02f, u * 0.05f, u * 0.02f, 0xFF5FBF5A.toInt())
                for (p in 0 until 5) {
                    val a = p * (PI * 2 / 5).toFloat() + t * 0.3f + i
                    val px = x + cos(a) * u * 0.05f
                    val py = y + sin(a) * u * 0.05f
                    ovalPath(path3, px - u * 0.042f, py - u * 0.042f, px + u * 0.042f, py + u * 0.042f)
                    val col = colors[i % colors.size]
                    shaded(c, path3, Color.WHITE, col, Colors.darken(col, 0.2f), outline = false)
                }
                fill.shader = null
                fill.color = 0xFFFFB02E.toInt()
                c.drawCircle(x, y, u * 0.03f, fill)
            }
        }
        "hat_acorn" -> {
            val base = top + bh * 0.3f
            rect.set(hx - bw * 0.8f, base - u * 0.5f, hx + bw * 0.8f, base + u * 0.2f)
            path.reset()
            path.arcTo(rect, 180f, 180f)
            path.quadTo(hx, base + u * 0.06f, hx - bw * 0.8f, base - u * 0.15f)
            path.close()
            shaded(c, path, 0xFFD8A870.toInt(), 0xFFA8733F.toInt(), 0xFF6A4420.toInt())
            c.save()
            c.clipPath(path)
            path3.reset()
            var x = hx - bw
            while (x < hx + bw) {
                path3.moveTo(x, base - u * 0.5f)
                path3.lineTo(x + u * 0.35f, base + u * 0.1f)
                path3.moveTo(x + u * 0.35f, base - u * 0.5f)
                path3.lineTo(x, base + u * 0.1f)
                x += u * 0.1f
            }
            stroke(c, path3, Colors.alpha(0xFF5A3A1A.toInt(), 0.4f), u * 0.012f)
            c.restore()
            path3.reset()
            path3.moveTo(hx, base - u * 0.33f)
            path3.quadTo(hx + u * 0.03f, base - u * 0.45f, hx + u * 0.08f, base - u * 0.48f)
            stroke(c, path3, 0xFF6A4420.toInt(), u * 0.04f)
        }
        "hat_bobble" -> {
            val base = top + bh * 0.3f
            rect.set(hx - bw * 0.82f, base - u * 0.52f, hx + bw * 0.82f, base + u * 0.3f)
            path.reset()
            path.arcTo(rect, 180f, 180f)
            path.close()
            shaded(c, path, 0xFF9CC2FF.toInt(), 0xFF4C6BD6.toInt(), 0xFF22307A.toInt())
            c.save()
            c.clipPath(path)
            fill.shader = null
            fill.color = Colors.alpha(Color.WHITE, 0.85f)
            c.drawRect(hx - bw, base - u * 0.3f, hx + bw, base - u * 0.22f, fill)
            c.drawRect(hx - bw, base - u * 0.16f, hx + bw, base - u * 0.1f, fill)
            c.restore()
            path.reset()
            path.addRoundRect(hx - bw * 0.85f, base - u * 0.08f, hx + bw * 0.85f, base + u * 0.06f, u * 0.06f, u * 0.06f, Path.Direction.CW)
            shaded(c, path, Color.WHITE, 0xFFE8ECF8.toInt(), 0xFFB0B8D0.toInt())
            val py = base - u * 0.56f + sin(t * 3f) * u * 0.01f
            ovalPath(path, hx - u * 0.1f, py - u * 0.1f, hx + u * 0.1f, py + u * 0.1f)
            shaded(c, path, Color.WHITE, 0xFFF4F6FF.toInt(), 0xFFC0C8E0.toInt())
        }
        "hat_bunnyears" -> {
            val base = top + bh * 0.2f
            for (s in intArrayOf(-1, 1)) {
                c.save()
                c.translate(hx + s * bw * 0.3f, base)
                c.rotate(s * (14f + sin(t * 2f + s) * 4f) + pose.earLag * 0.6f)
                ovalPath(path, -u * 0.09f, -u * 0.55f, u * 0.09f, 0f)
                shaded(c, path, Color.WHITE, 0xFFF8F4FF.toInt(), 0xFFC8C0E0.toInt())
                ovalPath(path2, -u * 0.045f, -u * 0.48f, u * 0.045f, -u * 0.08f)
                shaded(c, path2, 0xFFFFD0E0.toInt(), 0xFFFF9ABB.toInt(), 0xFFE07098.toInt(), outline = false)
                c.restore()
            }
            rect.set(hx - bw * 0.7f, base - u * 0.1f, hx + bw * 0.7f, base + u * 0.3f)
            line.shader = null
            line.color = 0xFFFF6FA8.toInt()
            line.strokeWidth = u * 0.05f
            c.drawArc(rect, 200f, 140f, false, line)
        }
        "hat_captain" -> {
            val base = top + bh * 0.22f
            path.reset()
            path.moveTo(hx - bw * 0.62f, base)
            path.cubicTo(hx - bw * 0.9f, base - u * 0.32f, hx + bw * 0.9f, base - u * 0.32f, hx + bw * 0.62f, base)
            path.close()
            shaded(c, path, Color.WHITE, 0xFFF4F6FA.toInt(), 0xFFB8C0D0.toInt())
            path.reset()
            path.addRoundRect(hx - bw * 0.62f, base - u * 0.08f, hx + bw * 0.62f, base + u * 0.03f, u * 0.03f, u * 0.03f, Path.Direction.CW)
            shaded(c, path, 0xFF5A6AA8.toInt(), 0xFF22307A.toInt(), 0xFF101840.toInt())
            val dir = if (pose.turn < -0.05f) -1f else 1f
            ovalPath(path, hx - bw * 0.5f + dir * bw * 0.05f, base - u * 0.02f, hx + bw * 0.5f + dir * bw * 0.05f, base + u * 0.09f)
            shaded(c, path, 0xFF3A4478.toInt(), 0xFF141C4A.toInt(), 0xFF080C24.toInt())
            gem(c, hx, base - u * 0.14f, u * 0.05f, 0xFFFFC83D.toInt())
        }
        "hat_witch" -> {
            val base = top + bh * 0.14f
            path.reset()
            path.moveTo(hx - bw * 0.56f, base)
            path.quadTo(hx - bw * 0.1f, base - u * 0.5f, hx + bw * 0.5f + sin(t * 1.5f) * u * 0.03f, base - u * 0.9f)
            path.quadTo(hx + bw * 0.2f, base - u * 0.4f, hx + bw * 0.56f, base)
            path.close()
            shaded(c, path, 0xFF8A6ACF.toInt(), 0xFF4A2E8A.toInt(), 0xFF22124A.toInt())
            c.save()
            c.clipPath(path)
            fill.shader = null
            fill.color = 0xFFFF9A3D.toInt()
            c.drawRect(hx - bw, base - u * 0.16f, hx + bw, base - u * 0.07f, fill)
            c.restore()
            path3.reset()
            path3.addRect(hx - u * 0.06f, base - u * 0.17f, hx + u * 0.06f, base - u * 0.06f, Path.Direction.CW)
            stroke(c, path3, 0xFFFFD84D.toInt(), u * 0.02f)
            ovalPath(path, hx - bw * 0.85f, base - u * 0.07f, hx + bw * 0.85f, base + u * 0.08f)
            shaded(c, path, 0xFF6A4AAF.toInt(), 0xFF3A2270.toInt(), 0xFF1A0E3A.toInt())
        }
        "hat_santa" -> {
            c.save()
            c.translate(hx + bw * 0.05f, top + bh * 0.22f)
            c.rotate(-14f + sin(t) * 3f)
            path.reset()
            path.moveTo(-bw * 0.62f, 0f)
            path.quadTo(-bw * 0.1f, -u * 0.6f, -bw * 0.8f, -u * 0.5f)
            path.quadTo(-bw * 0.2f, -u * 0.25f, bw * 0.62f, 0f)
            path.close()
            shaded(c, path, 0xFFFF8A8A.toInt(), 0xFFE0303F.toInt(), 0xFF8E1422.toInt())
            path.reset()
            path.addRoundRect(-bw * 0.7f, -u * 0.08f, bw * 0.7f, u * 0.08f, u * 0.08f, u * 0.08f, Path.Direction.CW)
            shaded(c, path, Color.WHITE, 0xFFF4F6FF.toInt(), 0xFFC8CCE0.toInt())
            ovalPath(path, -bw * 0.8f - u * 0.09f, -u * 0.59f, -bw * 0.8f + u * 0.09f, -u * 0.41f)
            shaded(c, path, Color.WHITE, 0xFFF4F6FF.toInt(), 0xFFC8CCE0.toInt())
            c.restore()
        }
    }
}
