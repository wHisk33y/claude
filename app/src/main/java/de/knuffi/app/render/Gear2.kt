package de.knuffi.app.render

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Path
import android.graphics.Shader
import kotlin.math.cos
import kotlin.math.sin

// Clothes of the second big update: back slot, new hats, glasses and necklaces.

/** Items worn on the back, drawn behind the body. */
internal fun PetRenderer.drawBackItem(c: Canvas, id: String, pose: PetPose, bw: Float, bh: Float, top: Float, cy: Float) {
    val t = pose.t
    val flying = pose.lift > 0.05f
    val flap = sin(t * (if (flying) 14f else 2.6f)) * (if (flying) 1f else 0.4f)
    when (id) {
        "back_lanternstick" -> {
            val side = if (pose.turn > 0.05f) -1f else 1f
            val bx = side * bw * 0.7f
            val by = cy + bh * 0.4f
            val tx = side * bw * 1.25f
            val ty = top - u * 0.35f
            line.shader = null
            line.color = 0xFF9A6A3A.toInt()
            line.strokeWidth = u * 0.045f
            c.drawLine(bx, by, tx, ty, line)
            val swing = sin(t * 2.2f) * 10f
            c.save()
            c.rotate(swing, tx, ty)
            line.color = 0xFF5A4A3A.toInt()
            line.strokeWidth = u * 0.015f
            c.drawLine(tx, ty, tx, ty + u * 0.12f, line)
            val ly = ty + u * 0.27f
            val glow = 0.8f + 0.2f * sin(t * 3f)
            shaderFill(radial(tx, ly, u * 0.4f, intArrayOf(Colors.alpha(0xFFFFC85A.toInt(), 0.5f * glow), Colors.alpha(0xFFFFC85A.toInt(), 0f))))
            c.drawCircle(tx, ly, u * 0.4f, fill)
            fill.shader = null
            ovalPath(path, tx - u * 0.15f, ly - u * 0.15f, tx + u * 0.15f, ly + u * 0.15f)
            shaded(c, path, 0xFFFFF0B0.toInt(), 0xFFFF9A3D.toInt(), 0xFFC85A10.toInt())
            fill.color = 0xFF4A3A2A.toInt()
            c.drawRect(tx - u * 0.08f, ly - u * 0.18f, tx + u * 0.08f, ly - u * 0.13f, fill)
            c.drawRect(tx - u * 0.07f, ly + u * 0.13f, tx + u * 0.07f, ly + u * 0.17f, fill)
            c.restore()
        }
        "back_backpack", "back_treasure" -> {
            val treasure = id == "back_treasure"
            val main = if (treasure) 0xFF9A6A3A.toInt() else 0xFF4DA8FF.toInt()
            val side = if (pose.turn > 0.05f) -1f else 1f
            val x = side * bw * 0.55f
            path.reset()
            path.addRoundRect(x - bw * 0.45f, cy - bh * 0.55f, x + bw * 0.45f, cy + bh * 0.55f, u * 0.08f, u * 0.08f, Path.Direction.CW)
            shaded(c, path, Colors.lighten(main, 0.35f), main, Colors.darken(main, 0.35f))
            path.reset()
            path.addRoundRect(x - bw * 0.35f, cy + bh * 0.05f, x + bw * 0.35f, cy + bh * 0.42f, u * 0.05f, u * 0.05f, Path.Direction.CW)
            shaded(c, path, Colors.lighten(main, 0.5f), Colors.lighten(main, 0.15f), Colors.darken(main, 0.2f))
            if (treasure) {
                gem(c, x, cy - bh * 0.25f, u * 0.06f, 0xFFFFC83D.toInt())
                for (k in 0..2) gem(c, x - bw * 0.2f + k * bw * 0.2f, cy - bh * 0.55f, u * 0.035f, intArrayOf(0xFFFF5C7A.toInt(), 0xFF4D8DFF.toInt(), 0xFF5ED17A.toInt())[k])
            }
        }
        "back_jetpack" -> for (s in intArrayOf(-1, 1)) {
            val x = s * bw * 0.62f
            path.reset()
            path.addRoundRect(x - u * 0.1f, cy - bh * 0.5f, x + u * 0.1f, cy + bh * 0.45f, u * 0.08f, u * 0.08f, Path.Direction.CW)
            shaded(c, path, Color.WHITE, 0xFFD8DEEA.toInt(), 0xFF8A94A8.toInt())
            fill.shader = null
            fill.color = 0xFFFF5A5A.toInt()
            c.drawRect(x - u * 0.1f, cy - bh * 0.1f, x + u * 0.1f, cy, fill)
            val fl = 0.7f + 0.3f * sin(t * 25f + s)
            flame(c, x, cy + bh * 0.55f + u * 0.12f * fl, u * 0.09f * fl * (if (flying) 1.6f else 1f), 0xFFFF8A3D.toInt(), 0xFFFFE066.toInt())
        }
        "back_butterfly", "back_fairywings", "back_angelwings", "back_rainbowwings" -> for (s in intArrayOf(-1, 1)) {
            val sf = s.toFloat()
            c.save()
            c.translate(sf * bw * 0.65f, cy - bh * 0.25f)
            c.rotate(sf * (flap * 14f - 8f))
            when (id) {
                "back_angelwings" -> {
                    val lens = floatArrayOf(0.62f, 0.52f, 0.4f)
                    val angles = floatArrayOf(-40f, -14f, 12f)
                    for (i in 0..2) {
                        c.save()
                        c.rotate(sf * angles[i])
                        val l = u * lens[i]
                        ovalPath(path, if (s > 0) 0f else -l, -u * 0.1f, if (s > 0) l else 0f, u * 0.1f)
                        shaded(c, path, Color.WHITE, 0xFFFFFAE8.toInt(), 0xFFE8D8A8.toInt())
                        c.restore()
                    }
                }
                else -> {
                    val colors = when (id) {
                        "back_butterfly" -> intArrayOf(0xFFFF9A3D.toInt(), 0xFF3A2E2A.toInt())
                        "back_fairywings" -> intArrayOf(0xFFB8F0FF.toInt(), 0xFFFF9EDC.toInt())
                        else -> intArrayOf(0xFFFF7A9A.toInt(), 0xFF8AC8FF.toInt())
                    }
                    for (k in 0..1) {
                        path.reset()
                        val big = k == 0
                        val l = u * (if (big) 0.58f else 0.42f)
                        val hgt = u * (if (big) 0.38f else 0.28f)
                        path.moveTo(0f, 0f)
                        path.cubicTo(sf * l * 0.3f, -hgt * (if (big) 1.3f else -0.2f), sf * l, -hgt * (if (big) 1.1f else -0.6f), sf * l * 0.95f, if (big) -hgt * 0.2f else hgt * 0.9f)
                        path.cubicTo(sf * l * 0.8f, if (big) hgt * 0.3f else hgt * 1.3f, sf * l * 0.3f, if (big) hgt * 0.2f else hgt * 0.8f, 0f, 0f)
                        path.close()
                        if (id == "back_rainbowwings") {
                            shaderFill(LinearGradient(0f, -hgt, sf * l, hgt, intArrayOf(0xFFFF7A9A.toInt(), 0xFFFFE07A.toInt(), 0xFF8AE8A0.toInt(), 0xFF8AC8FF.toInt(), 0xFFB99AFF.toInt()), null, Shader.TileMode.CLAMP))
                            c.drawPath(path, fill)
                            fill.shader = null
                            outline(c, path, 0xFF8A6AC8.toInt())
                        } else {
                            val a = if (id == "back_fairywings") 0.75f else 1f
                            shaderFill(radial(sf * l * 0.4f, 0f, l, intArrayOf(Colors.alpha(Color.WHITE, a), Colors.alpha(colors[0], a), Colors.alpha(colors[1], a)), floatArrayOf(0f, 0.6f, 1f)))
                            c.drawPath(path, fill)
                            fill.shader = null
                            outline(c, path, colors[1])
                        }
                    }
                    if (id == "back_fairywings") {
                        val tw = (sin(t * 4f + s) + 1f) / 2f
                        Shapes.sparkle(path3, sf * u * 0.4f, -u * 0.3f, u * (0.03f + 0.03f * tw))
                        fill.color = Colors.alpha(Color.WHITE, 0.5f + 0.5f * tw)
                        c.drawPath(path3, fill)
                    }
                }
            }
            c.restore()
        }
        "back_shell" -> {
            val side = if (pose.turn > 0.05f) -1f else 1f
            val x = side * bw * 0.7f
            val r = bh * 0.6f
            ovalPath(path, x - r, cy - r * 0.9f, x + r, cy + r * 0.9f)
            shaded(c, path, 0xFFFFD8A8.toInt(), 0xFFE0A060.toInt(), 0xFF9A5A2A.toInt())
            Shapes.spiral(path3, x, cy, r * 0.8f, 2.5f, 0f)
            stroke(c, path3, Colors.alpha(0xFF7A4A1E.toInt(), 0.6f), u * 0.025f)
        }
        "back_cape_gold" -> {
            val sway = sin(t * 2f) * bw * 0.08f
            path.reset()
            path.moveTo(-bw * 0.6f, cy + bh * 0.4f)
            path.lineTo(bw * 0.6f, cy + bh * 0.4f)
            path.quadTo(bw * 1.3f + sway, cy + bh * 0.8f, bw * 1.25f + sway, cy + bh * 1.2f)
            path.quadTo(sway, cy + bh * 1.05f, -bw * 1.25f + sway, cy + bh * 1.2f)
            path.quadTo(-bw * 1.3f + sway, cy + bh * 0.8f, -bw * 0.6f, cy + bh * 0.4f)
            path.close()
            shaded(c, path, 0xFFFFF4C2.toInt(), 0xFFFFC83D.toInt(), 0xFFB87A00.toInt())
        }
    }
}

/** Straps and other front parts of back items. */
internal fun PetRenderer.drawBackFront(c: Canvas, id: String, bw: Float, bh: Float, cy: Float, fx: Float) {
    when (id) {
        "back_backpack", "back_treasure", "back_jetpack" -> {
            val col = when (id) {
                "back_treasure" -> 0xFF7A4A22.toInt()
                "back_jetpack" -> 0xFF6E7A96.toInt()
                else -> 0xFF2A70C8.toInt()
            }
            c.save()
            c.clipPath(body)
            for (s in intArrayOf(-1, 1)) {
                path3.reset()
                path3.moveTo(fx + s * bw * 0.55f, cy - bh * 0.9f)
                path3.quadTo(fx + s * bw * 0.45f, cy, fx + s * bw * 0.62f, cy + bh * 0.7f)
                stroke(c, path3, col, u * 0.06f)
            }
            c.restore()
        }
        "back_cape_gold" -> {
            ovalPath(path, fx * 0.6f - u * 0.07f, cy + bh * 0.5f - u * 0.07f, fx * 0.6f + u * 0.07f, cy + bh * 0.5f + u * 0.07f)
            shaded(c, path, 0xFFFFF4C2.toInt(), 0xFFFFC83D.toInt(), 0xFFC08000.toInt())
        }
    }
}

internal fun PetRenderer.drawMoreHat(c: Canvas, id: String, pose: PetPose, bw: Float, bh: Float, top: Float, fx: Float) {
    val hx = fx * 0.45f
    val t = pose.t
    when (id) {
        "hat_chef" -> {
            val base = top + bh * 0.18f
            path.reset()
            path.addRoundRect(hx - bw * 0.42f, base - u * 0.2f, hx + bw * 0.42f, base, u * 0.04f, u * 0.04f, Path.Direction.CW)
            shaded(c, path, Color.WHITE, 0xFFF4F4F8.toInt(), 0xFFC8C8D8.toInt())
            for (k in -1..1) {
                val px = hx + k * bw * 0.28f
                ovalPath(path, px - bw * 0.26f, base - u * 0.55f, px + bw * 0.26f, base - u * 0.12f)
                shaded(c, path, Color.WHITE, 0xFFF8F8FC.toInt(), 0xFFD0D0E0.toInt())
            }
        }
        "hat_beret" -> {
            val base = top + bh * 0.2f
            c.save()
            c.rotate(-12f, hx, base)
            ovalPath(path, hx - bw * 0.7f, base - u * 0.26f, hx + bw * 0.6f, base + u * 0.04f)
            shaded(c, path, 0xFFFF7A8E.toInt(), 0xFFD6304A.toInt(), 0xFF7A1022.toInt())
            path.reset()
            path.moveTo(hx, base - u * 0.26f)
            path.lineTo(hx + u * 0.02f, base - u * 0.36f)
            stroke(c, path, 0xFF7A1022.toInt(), u * 0.035f)
            c.restore()
        }
        "hat_headphones" -> {
            rect.set(hx - bw * 0.95f, top - u * 0.12f, hx + bw * 0.95f, top + bh * 1.1f)
            line.shader = null
            line.color = 0xFF3A3450.toInt()
            line.strokeWidth = u * 0.07f
            c.drawArc(rect, 195f, 150f, false, line)
            for (s in intArrayOf(-1, 1)) {
                val x = hx + s * bw * 0.92f
                val y = top + bh * 0.42f
                path.reset()
                path.addRoundRect(x - u * 0.09f, y - u * 0.14f, x + u * 0.09f, y + u * 0.14f, u * 0.08f, u * 0.08f, Path.Direction.CW)
                shaded(c, path, 0xFFFF9AC0.toInt(), 0xFFFF5C9A.toInt(), 0xFFB82C66.toInt())
            }
            val beat = (sin(t * 8f) + 1f) / 2f
            fill.shader = null
            fill.color = Colors.alpha(0xFF7C6CFF.toInt(), 0.6f * beat)
            c.drawCircle(hx + bw * 1.15f, top + bh * 0.2f - beat * u * 0.1f, u * 0.03f, fill)
        }
        "hat_goldcap" -> {
            val base = top + bh * 0.16f
            rect.set(hx - bw * 0.72f, base - u * 0.36f, hx + bw * 0.72f, base + u * 0.36f)
            path.reset()
            path.arcTo(rect, 180f, 180f)
            path.close()
            shaded(c, path, 0xFFFFF4C2.toInt(), 0xFFFFC83D.toInt(), 0xFFB87A00.toInt())
            val dir = if (pose.turn < -0.05f) -1f else 1f
            ovalPath(path, hx + dir * bw * 0.1f - (if (dir > 0) 0f else bw * 1.1f), base - u * 0.05f, hx + dir * bw * 0.1f + (if (dir > 0) bw * 1.1f else 0f), base + u * 0.08f)
            shaded(c, path, 0xFFFFE08A.toInt(), 0xFFE0A000.toInt(), 0xFF9A6000.toInt())
            Shapes.softStar(path3, hx, base - u * 0.18f, u * 0.07f, u * 0.035f, 5, 0f)
            fill.shader = null
            fill.color = Color.WHITE
            c.drawPath(path3, fill)
        }
        "hat_jester" -> {
            val base = top + bh * 0.18f
            val cols = intArrayOf(0xFFB25AF0.toInt(), 0xFFFFC83D.toInt(), 0xFFFF5A8A.toInt())
            for ((k, a) in floatArrayOf(-1f, 0f, 1f).withIndex()) {
                val bob = sin(t * 3f + k) * u * 0.03f
                val tipX = hx + a * bw * 0.85f
                val tipY = base - u * (if (k == 1) 0.62f else 0.38f) + bob
                path.reset()
                path.moveTo(hx + a * bw * 0.32f - bw * 0.25f, base)
                path.quadTo(hx + a * bw * 0.4f, base - u * 0.45f, tipX, tipY)
                path.quadTo(hx + a * bw * 0.55f, base - u * 0.2f, hx + a * bw * 0.32f + bw * 0.25f, base)
                path.close()
                shaded(c, path, Colors.lighten(cols[k], 0.4f), cols[k], Colors.darken(cols[k], 0.3f))
                ovalPath(path2, tipX - u * 0.06f, tipY - u * 0.06f, tipX + u * 0.06f, tipY + u * 0.06f)
                shaded(c, path2, Color.WHITE, 0xFFFFD84D.toInt(), 0xFFC08000.toInt(), outline = false)
            }
            path.reset()
            path.addRoundRect(hx - bw * 0.62f, base - u * 0.05f, hx + bw * 0.62f, base + u * 0.06f, u * 0.05f, u * 0.05f, Path.Direction.CW)
            shaded(c, path, 0xFFFFF4C2.toInt(), 0xFFFFC83D.toInt(), 0xFFB87A00.toInt())
        }
        "hat_lanternband", "hat_starlantern" -> {
            val star = id == "hat_starlantern"
            val base = top + bh * 0.16f
            if (!star) {
                rect.set(hx - bw * 0.62f, base - u * 0.1f, hx + bw * 0.62f, base + bh * 0.5f)
                line.shader = null
                line.color = 0xFF5A4A3A.toInt()
                line.strokeWidth = u * 0.04f
                c.drawArc(rect, 200f, 140f, false, line)
            }
            val sx = hx + if (star) 0f else bw * 0.25f
            val sway = sin(t * 2.4f) * u * 0.04f
            val ly = base - u * (if (star) 0.32f else 0.42f)
            line.shader = null
            line.color = 0xFF5A4A3A.toInt()
            line.strokeWidth = u * 0.02f
            c.drawLine(sx, base - u * 0.05f, sx + sway, ly + u * 0.12f, line)
            val col = if (star) 0xFFFFE066.toInt() else 0xFFFF9A3D.toInt()
            val glow = 0.75f + 0.25f * sin(t * 3.2f)
            shaderFill(radial(sx + sway, ly, u * 0.36f, intArrayOf(Colors.alpha(col, 0.5f * glow), Colors.alpha(col, 0f))))
            c.drawCircle(sx + sway, ly, u * 0.36f, fill)
            fill.shader = null
            if (star) {
                Shapes.softStar(path, sx + sway, ly, u * 0.16f, u * 0.08f, 5, sin(t) * 8f)
                shaded(c, path, Color.WHITE, col, 0xFFC89A00.toInt())
            } else {
                ovalPath(path, sx + sway - u * 0.13f, ly - u * 0.12f, sx + sway + u * 0.13f, ly + u * 0.12f)
                shaded(c, path, 0xFFFFF0B0.toInt(), col, 0xFFC85A10.toInt())
                fill.color = 0xFF4A3A2A.toInt()
                c.drawRect(sx + sway - u * 0.07f, ly - u * 0.15f, sx + sway + u * 0.07f, ly - u * 0.1f, fill)
            }
        }
        "hat_catears" -> {
            val base = top + bh * 0.16f
            rect.set(hx - bw * 0.62f, base - u * 0.1f, hx + bw * 0.62f, base + bh * 0.5f)
            line.shader = null
            line.color = 0xFFFF7EB6.toInt()
            line.strokeWidth = u * 0.05f
            c.drawArc(rect, 200f, 140f, false, line)
            for (s in intArrayOf(-1, 1)) {
                val ex = hx + s * bw * 0.38f
                path.reset()
                path.moveTo(ex - u * 0.14f, base + u * 0.02f)
                path.lineTo(ex + s * u * 0.04f, base - u * 0.3f)
                path.lineTo(ex + u * 0.14f, base + u * 0.02f)
                path.close()
                shaded(c, path, 0xFF7A6A80.toInt(), 0xFF4A3A50.toInt(), 0xFF2A1E30.toInt())
                path.reset()
                path.moveTo(ex - u * 0.07f, base - u * 0.01f)
                path.lineTo(ex + s * u * 0.03f, base - u * 0.2f)
                path.lineTo(ex + u * 0.07f, base - u * 0.01f)
                path.close()
                fill.shader = null
                fill.color = 0xFFFFA8C8.toInt()
                c.drawPath(path, fill)
            }
        }
        "hat_halo" -> {
            val cy = top - u * 0.16f + sin(t * 2.2f) * u * 0.025f
            fill.shader = radial(hx, cy, bw * 0.9f, intArrayOf(Colors.alpha(0xFFFFF4A0.toInt(), 0.55f), Colors.alpha(0xFFFFF4A0.toInt(), 0f)))
            c.drawOval(hx - bw * 0.9f, cy - u * 0.25f, hx + bw * 0.9f, cy + u * 0.25f, fill)
            fill.shader = null
            line.shader = null
            line.color = 0xFFFFD84D.toInt()
            line.strokeWidth = u * 0.065f
            c.drawOval(hx - bw * 0.5f, cy - u * 0.09f, hx + bw * 0.5f, cy + u * 0.09f, line)
            line.color = 0xFFFFF8D0.toInt()
            line.strokeWidth = u * 0.022f
            c.drawArc(hx - bw * 0.5f, cy - u * 0.09f, hx + bw * 0.5f, cy + u * 0.09f, 200f, 90f, false, line)
        }
        "hat_cowboy" -> {
            val base = top + bh * 0.16f
            c.save()
            c.rotate(-6f, hx, base)
            ovalPath(path, hx - bw * 0.95f, base - u * 0.1f, hx + bw * 0.95f, base + u * 0.06f)
            shaded(c, path, 0xFFE8B070.toInt(), 0xFFB87A3A.toInt(), 0xFF6A4018.toInt())
            path.reset()
            path.moveTo(hx - bw * 0.48f, base - u * 0.03f)
            path.cubicTo(hx - bw * 0.5f, base - u * 0.42f, hx - bw * 0.15f, base - u * 0.36f, hx, base - u * 0.3f)
            path.cubicTo(hx + bw * 0.15f, base - u * 0.36f, hx + bw * 0.5f, base - u * 0.42f, hx + bw * 0.48f, base - u * 0.03f)
            path.close()
            shaded(c, path, 0xFFE8B070.toInt(), 0xFFB87A3A.toInt(), 0xFF6A4018.toInt())
            path.reset()
            path.addRect(hx - bw * 0.47f, base - u * 0.12f, hx + bw * 0.47f, base - u * 0.05f, Path.Direction.CW)
            fill.shader = null
            fill.color = 0xFF6A3018.toInt()
            c.drawPath(path, fill)
            fill.color = 0xFFFFD84D.toInt()
            Shapes.softStar(path, hx, base - u * 0.085f, u * 0.05f, u * 0.025f, 5, 0f)
            c.drawPath(path, fill)
            c.restore()
        }
        "hat_starcrown" -> {
            val base = top + bh * 0.1f
            for (k in -2..2) {
                val x = hx + k * bw * 0.22f
                val h = u * (0.18f + 0.08f * (2 - kotlin.math.abs(k)))
                Shapes.softStar(path, x, base - h, u * 0.09f, u * 0.045f, 5, t * 20f + k * 30f)
                shaded(c, path, Color.WHITE, 0xFFFFE066.toInt(), 0xFFE0A000.toInt(), outline = false)
            }
            path.reset()
            path.addRoundRect(hx - bw * 0.5f, base - u * 0.06f, hx + bw * 0.5f, base + u * 0.02f, u * 0.03f, u * 0.03f, Path.Direction.CW)
            shaded(c, path, 0xFFB8A8FF.toInt(), 0xFF7C6CFF.toInt(), 0xFF3A2E9A.toInt())
        }
    }
}

internal fun PetRenderer.drawMoreFace(c: Canvas, id: String, pose: PetPose, bw: Float, bh: Float, cy: Float, fx: Float) {
    val re = u * 0.135f
    val ex = bw * 0.36f * (1f - 0.1f * kotlin.math.abs(pose.turn))
    val ey = cy - bh * 0.1f
    when (id) {
        "face_mask" -> {
            path.reset()
            path.fillType = Path.FillType.EVEN_ODD
            path.moveTo(fx - ex - re * 1.9f, ey - re * 0.6f)
            path.quadTo(fx - ex, ey - re * 2.1f, fx, ey - re * 0.9f)
            path.quadTo(fx + ex, ey - re * 2.1f, fx + ex + re * 1.9f, ey - re * 0.6f)
            path.quadTo(fx + ex + re * 1.2f, ey + re * 1.4f, fx + re * 0.4f, ey + re * 0.9f)
            path.quadTo(fx, ey + re * 0.6f, fx - re * 0.4f, ey + re * 0.9f)
            path.quadTo(fx - ex - re * 1.2f, ey + re * 1.4f, fx - ex - re * 1.9f, ey - re * 0.6f)
            path.close()
            for (sd in intArrayOf(-1, 1)) path.addOval(fx + sd * ex - re * 1.05f, ey - re * 0.95f, fx + sd * ex + re * 1.05f, ey + re * 0.95f, Path.Direction.CW)
            shaded(c, path, 0xFFE8C8FF.toInt(), 0xFF9A5AF0.toInt(), 0xFF5A2AA8.toInt())
            path.fillType = Path.FillType.WINDING
            // glitter dots and a feather
            fill.shader = null
            fill.color = 0xFFFFD84D.toInt()
            for (k in 0 until 5) c.drawCircle(fx + (k - 2) * re * 0.6f, ey - re * 1.25f + kotlin.math.abs(k - 2) * re * 0.12f, re * 0.12f, fill)
            path.reset()
            path.moveTo(fx + ex + re * 1.5f, ey - re * 0.9f)
            path.quadTo(fx + ex + re * 2.6f, ey - re * 3.0f, fx + ex + re * 1.2f, ey - re * 3.4f)
            path.quadTo(fx + ex + re * 1.6f, ey - re * 2.0f, fx + ex + re * 1.5f, ey - re * 0.9f)
            path.close()
            shaded(c, path, 0xFFFFC6DC.toInt(), 0xFFFF5A8A.toInt(), 0xFFB82C66.toInt(), outline = false)
        }
        "face_aviator" -> {
            path.reset()
            path.moveTo(-bw * 1.02f, ey - re * 0.3f)
            path.lineTo(bw * 1.02f, ey - re * 0.3f)
            stroke(c, path, 0xFF7A4A22.toInt(), u * 0.06f)
            for (s in intArrayOf(-1, 1)) {
                val x = fx + s * ex
                ovalPath(path, x - re * 1.35f, ey - re * 1.2f, x + re * 1.35f, ey + re * 1.2f)
                fill.shader = null
                fill.color = Colors.alpha(0xFF8FD3FF.toInt(), 0.45f)
                c.drawPath(path, fill)
                stroke(c, path, 0xFFB98A55.toInt(), u * 0.05f)
                gloss(c, x - re * 0.4f, ey - re * 0.5f, re * 0.5f, re * 0.2f, 0.9f)
            }
        }
        "face_monocle" -> {
            val x = fx + ex
            ovalPath(path, x - re * 1.4f, ey - re * 1.4f, x + re * 1.4f, ey + re * 1.4f)
            fill.shader = null
            fill.color = Colors.alpha(0xFFE6F4FF.toInt(), 0.25f)
            c.drawPath(path, fill)
            stroke(c, path, 0xFFE0A000.toInt(), u * 0.035f)
            path3.reset()
            path3.moveTo(x + re * 1.2f, ey + re * 0.8f)
            path3.quadTo(x + re * 2f, ey + re * 3f, x + re * 0.6f, ey + re * 4.2f)
            stroke(c, path3, 0xFFE0A000.toInt(), u * 0.012f)
        }
    }
}

internal fun PetRenderer.drawMoreNeck(c: Canvas, id: String, pose: PetPose, bw: Float, bh: Float, cy: Float, fx: Float) {
    val neckY = cy + bh * 0.55f
    val x0 = fx * 0.6f
    when (id) {
        "neck_ruffle" -> {
            val cols = intArrayOf(0xFFFF5A8A.toInt(), 0xFFFFC83D.toInt(), 0xFF4CC8FF.toInt(), 0xFF5AD88A.toInt(), 0xFFB27CFF.toInt())
            for (i in 0 until 9) {
                val k = (i - 4) / 4f
                val x = x0 + k * bw * 0.72f
                val y = neckY - bh * 0.06f + k * k * bh * 0.1f
                val r = u * 0.1f
                ovalPath(path, x - r, y - r * 0.8f, x + r, y + r * 0.8f)
                val col = cols[i % cols.size]
                shaded(c, path, Colors.lighten(col, 0.45f), col, Colors.darken(col, 0.3f))
            }
        }
        "neck_pearls", "neck_goldchain" -> {
            val gold = id == "neck_goldchain"
            for (i in 0 until 11) {
                val k = (i - 5) / 5f
                val x = x0 + k * bw * 0.75f
                val y = neckY - bh * 0.16f * k * k + bh * 0.02f
                val r = u * (if (gold) 0.03f else 0.04f)
                ovalPath(path, x - r, y - r, x + r, y + r)
                if (gold) shaded(c, path, 0xFFFFF4C2.toInt(), 0xFFFFC83D.toInt(), 0xFFB87A00.toInt(), outline = false)
                else shaded(c, path, Color.WHITE, 0xFFF4EEF4.toInt(), 0xFFC8B8C8.toInt(), outline = false)
            }
            if (gold) {
                val sw = sin(pose.t * 3f) * u * 0.01f
                Shapes.heart(path, x0 + sw, neckY + u * 0.06f, u * 0.16f)
                shaded(c, path, 0xFFFFF4C2.toInt(), 0xFFFFC83D.toInt(), 0xFFB87A00.toInt())
            }
        }
        "neck_bandana" -> {
            path.reset()
            path.moveTo(x0 - bw * 0.62f, neckY - bh * 0.18f)
            path.quadTo(x0, neckY - bh * 0.02f, x0 + bw * 0.62f, neckY - bh * 0.18f)
            path.lineTo(x0, neckY + bh * 0.42f)
            path.close()
            shaded(c, path, 0xFFFF7A7A.toInt(), 0xFFE0303F.toInt(), 0xFF8E1422.toInt())
            fill.shader = null
            fill.color = Colors.alpha(Color.WHITE, 0.9f)
            for (i in 0 until 6) {
                val a = i * 1.1f
                c.drawCircle(x0 + cos(a) * bw * 0.25f, neckY + sin(a) * bh * 0.1f, u * 0.018f, fill)
            }
        }
    }
}

