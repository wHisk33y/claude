package de.knuffi.app.render

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Path
import android.graphics.Shader
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** Furniture of the second big update: new wallpapers, rugs, pictures, lamps, plants, beds and the toy corner. */
class MoreFurniture : Painter() {
    private val p3 = Path()

    fun wallPattern(c: Canvas, L: SceneLayout, id: String, wt: Float) {
        val w = L.w
        val h = L.h
        fill.shader = null
        when (id) {
            "wall_paws" -> {
                var row = 0
                var py = h * 0.04f
                while (py < wt - h * 0.03f) {
                    var px = if (row % 2 == 0) w * 0.06f else w * 0.15f
                    while (px < w) {
                        paw(c, px, py, w * 0.012f, if (row % 2 == 0) -15f else 20f, Colors.alpha(0xFFB07A5A.toInt(), 0.28f))
                        px += w * 0.18f
                    }
                    py += h * 0.055f
                    row++
                }
            }
            "wall_lemon" -> {
                var row = 0
                var py = h * 0.04f
                while (py < wt - h * 0.03f) {
                    var px = if (row % 2 == 0) w * 0.05f else w * 0.13f
                    while (px < w) {
                        fill.color = Colors.alpha(if ((row + (px / w * 10).toInt()) % 3 == 0) 0xFFFFB84D.toInt() else Color.WHITE, 0.55f)
                        c.drawCircle(px, py, w * 0.012f, fill)
                        px += w * 0.16f
                    }
                    py += h * 0.045f
                    row++
                }
            }
            "wall_lilac" -> {
                var row = 0
                var py = h * 0.05f
                while (py < wt - h * 0.03f) {
                    var px = if (row % 2 == 0) w * 0.07f else w * 0.17f
                    while (px < w) {
                        fill.color = Colors.alpha(0xFFFFFFFF.toInt(), 0.6f)
                        Shapes.heart(p3, px, py, w * 0.03f)
                        c.drawPath(p3, fill)
                        px += w * 0.2f
                    }
                    py += h * 0.06f
                    row++
                }
            }
            "wall_sunset" -> {
                // soft sun and horizontal bands
                shader(radial(w * 0.75f, wt * 0.55f, w * 0.4f, intArrayOf(Colors.alpha(0xFFFFE08A.toInt(), 0.6f), Colors.alpha(0xFFFFE08A.toInt(), 0f))))
                c.drawCircle(w * 0.75f, wt * 0.55f, w * 0.4f, fill)
                fill.shader = null
                fill.color = Colors.alpha(Color.WHITE, 0.15f)
                var y = h * 0.04f
                while (y < wt) {
                    c.drawRect(0f, y, w, y + h * 0.006f, fill)
                    y += h * 0.035f
                }
            }
        }
    }

    fun paw(c: Canvas, x: Float, y: Float, s: Float, rot: Float, color: Int) {
        c.save()
        c.rotate(rot, x, y)
        fill.shader = null
        fill.color = color
        c.drawOval(x - s, y - s * 0.7f, x + s, y + s * 0.9f, fill)
        for (k in 0 until 4) {
            val a = (-0.62f + k * 0.41f) * PI.toFloat() / 2f - PI.toFloat() / 2f
            c.drawCircle(x + cos(a) * s * 1.45f, y + sin(a) * s * 1.45f + s * 0.1f, s * 0.38f, fill)
        }
        c.restore()
    }

    fun rug(c: Canvas, L: SceneLayout, id: String) {
        val w = L.w
        val h = L.h
        val rx = w * 0.36f
        val rcx = w * 0.5f
        val rcy = L.groundY + h * 0.005f
        c.save()
        c.scale(1f, 0.22f, rcx, rcy)
        when (id) {
            "rug_paw" -> {
                path.reset()
                path.addOval(rcx - rx * 0.62f, rcy - rx * 0.45f, rcx + rx * 0.62f, rcy + rx * 0.7f, Path.Direction.CW)
                for (k in 0 until 4) {
                    val a = (-0.62f + k * 0.41f) * PI.toFloat() / 2f - PI.toFloat() / 2f
                    path.addCircle(rcx + cos(a) * rx * 0.95f, rcy + sin(a) * rx * 0.85f, rx * 0.25f, Path.Direction.CW)
                }
                shaded(c, path, 0xFFFFE0EC.toInt(), 0xFFFFA8C8.toInt(), 0xFFD06A98.toInt(), outlineW = w * 0.006f)
            }
            "rug_heart" -> {
                Shapes.heart(path, rcx, rcy, rx * 2.1f)
                shaded(c, path, 0xFFFFC2DA.toInt(), 0xFFFF5C9A.toInt(), 0xFFB82C66.toInt(), outlineW = w * 0.006f)
                Shapes.heart(path, rcx, rcy, rx * 1.3f)
                fill.color = Colors.alpha(Color.WHITE, 0.3f)
                c.drawPath(path, fill)
            }
        }
        c.restore()
    }

    fun picture(c: Canvas, id: String, l: Float, t: Float, r: Float, b: Float) {
        val pw = r - l
        val ph = b - t
        when (id) {
            "pic_cat" -> {
                vgrad(c, l, t, r, b, 0xFFFFE6A8.toInt(), 0xFFFFF6E0.toInt())
                fill.shader = null
                fill.color = 0xFFFFC83D.toInt()
                c.drawCircle(l + pw * 0.8f, t + ph * 0.25f, ph * 0.14f, fill)
                // cat silhouette sitting on a cushion
                val cx = l + pw * 0.42f
                val by = b - ph * 0.12f
                fill.color = 0xFFFF8FB8.toInt()
                c.drawOval(cx - pw * 0.25f, by - ph * 0.06f, cx + pw * 0.25f, by + ph * 0.06f, fill)
                fill.color = 0xFFE89A4A.toInt()
                c.drawOval(cx - pw * 0.15f, by - ph * 0.42f, cx + pw * 0.15f, by, fill)
                c.drawCircle(cx, by - ph * 0.5f, ph * 0.14f, fill)
                for (s in intArrayOf(-1, 1)) {
                    path.reset()
                    path.moveTo(cx + s * ph * 0.12f, by - ph * 0.56f)
                    path.lineTo(cx + s * ph * 0.13f, by - ph * 0.72f)
                    path.lineTo(cx + s * ph * 0.02f, by - ph * 0.62f)
                    path.close()
                    c.drawPath(path, fill)
                }
                line.shader = null
                line.color = 0xFFE89A4A.toInt()
                line.strokeWidth = ph * 0.05f
                path.reset()
                path.moveTo(cx + pw * 0.12f, by - ph * 0.05f)
                path.quadTo(cx + pw * 0.35f, by - ph * 0.1f, cx + pw * 0.3f, by - ph * 0.35f)
                c.drawPath(path, line)
            }
            "pic_sunset" -> {
                vgrad(c, l, t, r, t + ph * 0.6f, 0xFFFF9AB8.toInt(), 0xFFFFD08A.toInt())
                fill.shader = null
                fill.color = 0xFFFFE680.toInt()
                c.drawCircle(l + pw * 0.5f, t + ph * 0.6f, ph * 0.2f, fill)
                vgrad(c, l, t + ph * 0.6f, r, b, 0xFF4A7AD6.toInt(), 0xFF2A4A9A.toInt())
                fill.color = Colors.alpha(0xFFFFE680.toInt(), 0.6f)
                for (k in 0..3) c.drawRect(l + pw * (0.4f - k * 0.02f), t + ph * (0.64f + k * 0.08f), l + pw * (0.6f + k * 0.02f), t + ph * (0.66f + k * 0.08f), fill)
            }
        }
    }

    fun lampGlow(id: String): Int? = when (id) {
        "lamp_star" -> 0xFFFFF0A0.toInt()
        "lamp_lantern" -> 0xFFFFB84D.toInt()
        else -> null
    }

    fun lamp(c: Canvas, L: SceneLayout, id: String, lx: Float, ly: Float, t: Float = 0f) {
        val w = L.w
        val h = L.h
        when (id) {
            "lamp_star" -> {
                line.shader = null
                line.color = 0xFF6A5A7A.toInt()
                line.strokeWidth = maxOf(1f, w * 0.003f)
                c.drawLine(lx, 0f, lx, ly - h * 0.03f, line)
                Shapes.softStar(path, lx, ly, w * 0.05f, w * 0.025f, 5, 0f)
                shaded(c, path, Color.WHITE, 0xFFFFE066.toInt(), 0xFFD0A000.toInt(), outlineW = w * 0.002f)
            }
            "lamp_lantern" -> {
                line.shader = null
                line.color = 0xFF4A3A2A.toInt()
                line.strokeWidth = maxOf(1f, w * 0.003f)
                c.drawLine(lx, 0f, lx, ly - h * 0.04f, line)
                box(c, lx - w * 0.025f, ly - h * 0.045f, lx + w * 0.025f, ly - h * 0.035f, w * 0.004f, 0xFF6A5A4A.toInt(), 0xFF3A2A1A.toInt())
                path.reset()
                path.addRoundRect(lx - w * 0.035f, ly - h * 0.035f, lx + w * 0.035f, ly + h * 0.03f, w * 0.02f, w * 0.02f, Path.Direction.CW)
                shaded(c, path, 0xFFFFD08A.toInt(), 0xFFFF6A3D.toInt(), 0xFFB83A1A.toInt(), outlineW = w * 0.002f)
                fill.shader = null
                fill.color = Colors.alpha(0xFF8A2A10.toInt(), 0.5f)
                for (k in -1..1) c.drawRect(lx + k * w * 0.018f - w * 0.002f, ly - h * 0.033f, lx + k * w * 0.018f + w * 0.002f, ly + h * 0.028f, fill)
                box(c, lx - w * 0.025f, ly + h * 0.03f, lx + w * 0.025f, ly + h * 0.038f, w * 0.004f, 0xFF6A5A4A.toInt(), 0xFF3A2A1A.toInt())
            }
        }
    }

    fun plant(c: Canvas, L: SceneLayout, id: String) {
        val w = L.w
        val h = L.h
        val fy = L.floorY
        val px0 = w * 0.06f
        val potTop = fy - h * 0.02f
        when (id) {
            "plant_catgrass" -> {
                line.shader = null
                for (i in 0 until 22) {
                    val k = i / 21f - 0.5f
                    val len = h * (0.07f + 0.05f * ((i * 37) % 7) / 7f)
                    line.color = if (i % 3 == 0) 0xFF4FB36A.toInt() else 0xFF7FD38A.toInt()
                    line.strokeWidth = w * 0.006f
                    path.reset()
                    path.moveTo(px0 + k * w * 0.06f, potTop)
                    path.quadTo(px0 + k * w * 0.09f, potTop - len * 0.6f, px0 + k * w * 0.14f, potTop - len)
                    c.drawPath(path, line)
                }
                pot(c, px0, potTop, w * 0.08f, h * 0.045f, 0xFFB0D8FF.toInt())
            }
            "plant_tulips" -> {
                val colors = intArrayOf(0xFFFF5C7A.toInt(), 0xFFFFD84D.toInt(), 0xFFB07CFF.toInt(), 0xFFFF8FB8.toInt())
                for (i in 0 until 4) {
                    val x = px0 + (i - 1.5f) * w * 0.022f
                    val top = potTop - h * (0.1f + 0.03f * (i % 2))
                    line.shader = null
                    line.color = 0xFF4FB36A.toInt()
                    line.strokeWidth = w * 0.005f
                    c.drawLine(x, potTop, x, top, line)
                    path.reset()
                    path.moveTo(x - w * 0.016f, top)
                    path.lineTo(x - w * 0.016f, top - h * 0.03f)
                    path.lineTo(x - w * 0.006f, top - h * 0.018f)
                    path.lineTo(x, top - h * 0.034f)
                    path.lineTo(x + w * 0.006f, top - h * 0.018f)
                    path.lineTo(x + w * 0.016f, top - h * 0.03f)
                    path.lineTo(x + w * 0.016f, top)
                    path.quadTo(x, top + h * 0.012f, x - w * 0.016f, top)
                    path.close()
                    shaded(c, path, Colors.lighten(colors[i], 0.4f), colors[i], Colors.darken(colors[i], 0.3f), outlineW = w * 0.002f)
                }
                pot(c, px0, potTop, w * 0.08f, h * 0.05f, 0xFFFFC2A8.toInt())
            }
        }
    }

    private fun pot(c: Canvas, x: Float, potTop: Float, pw: Float, ph: Float, color: Int) {
        path.reset()
        path.moveTo(x - pw / 2f, potTop - ph)
        path.lineTo(x + pw / 2f, potTop - ph)
        path.lineTo(x + pw * 0.38f, potTop + ph * 0.1f)
        path.lineTo(x - pw * 0.38f, potTop + ph * 0.1f)
        path.close()
        shaded(c, path, Colors.lighten(color, 0.4f), color, Colors.darken(color, 0.3f), outlineW = pw * 0.02f)
    }

    /** Back part of the new beds. Returns false if [style] is not one of them. */
    fun bedBack(c: Canvas, x: Float, groundY: Float, s: Float, l: Float, r: Float, style: String, t: Float = 0f): Boolean {
        when (style) {
            "bed_basket" -> {
                val w = r - l
                path.reset()
                path.addOval(l, groundY - s * 0.32f, r, groundY + s * 0.04f, Path.Direction.CW)
                shaded(c, path, 0xFFF0C890.toInt(), 0xFFC8904A.toInt(), 0xFF7A4E22.toInt(), outlineW = s * 0.008f)
                line.shader = null
                line.color = Colors.alpha(0xFF7A4E22.toInt(), 0.4f)
                line.strokeWidth = s * 0.012f
                c.save()
                c.clipPath(path)
                var k = l
                while (k < r) {
                    c.drawLine(k, groundY - s * 0.32f, k + w * 0.08f, groundY + s * 0.04f, line)
                    k += w * 0.07f
                }
                c.restore()
                path.reset()
                path.addOval(l + s * 0.08f, groundY - s * 0.3f, r - s * 0.08f, groundY - s * 0.1f, Path.Direction.CW)
                shaded(c, path, 0xFFFFE0EC.toInt(), 0xFFFFA8C8.toInt(), 0xFFD06A98.toInt(), outline = false)
            }
            "bed_hammock" -> {
                for (px in floatArrayOf(l - s * 0.02f, r + s * 0.02f)) box(c, px - s * 0.035f, groundY - s * 0.8f, px + s * 0.035f, groundY, s * 0.02f, 0xFFD8A060.toInt(), 0xFF8A5A2A.toInt())
                val sway = sin(t * 1.2f) * s * 0.02f
                path.reset()
                path.moveTo(l, groundY - s * 0.62f)
                path.quadTo(x + sway, groundY - s * 0.05f, r, groundY - s * 0.62f)
                path.quadTo(x + sway, groundY - s * 0.28f, l, groundY - s * 0.62f)
                path.close()
                shaded(c, path, 0xFFFFE8A8.toInt(), 0xFFFF9A4D.toInt(), 0xFFC0602A.toInt(), outlineW = s * 0.008f)
                fill.shader = null
                fill.color = Colors.alpha(Color.WHITE, 0.45f)
                c.save()
                c.clipPath(path)
                var k = l
                while (k < r) {
                    c.drawRect(k, groundY - s, k + s * 0.05f, groundY, fill)
                    k += s * 0.16f
                }
                c.restore()
            }
            else -> return false
        }
        return true
    }

    fun bedColors(style: String): IntArray? = when (style) {
        "bed_basket" -> intArrayOf(0xFFFFE0EC.toInt(), 0xFFFF8FB8.toInt(), 0xFFC24A7E.toInt())
        "bed_hammock" -> intArrayOf(0xFFFFF0C8.toInt(), 0xFFFFC85A.toInt(), 0xFFC08A2A.toInt())
        else -> null
    }

    /** The toy corner in the front left of the room. */
    fun toy(c: Canvas, L: SceneLayout, id: String?, trophies: Int) {
        if (id == null) return
        val w = L.w
        val h = L.h
        val x = w * 0.1f
        val g = L.groundY + h * 0.06f
        val s = L.petSize * 0.7f
        ellipseShadow(c, x, g, s * 0.5f, s * 0.08f, 0.3f)
        when (id) {
            "toy_scratcher" -> {
                box(c, x - s * 0.3f, g - s * 0.08f, x + s * 0.3f, g, s * 0.03f, 0xFFD8C8B8.toInt(), 0xFFA89888.toInt())
                box(c, x - s * 0.06f, g - s * 0.95f, x + s * 0.06f, g - s * 0.08f, 0f, 0xFFE8D0A0.toInt(), 0xFFB8986A.toInt())
                line.shader = null
                line.color = Colors.alpha(0xFF8A6A3A.toInt(), 0.5f)
                line.strokeWidth = s * 0.012f
                var y = g - s * 0.9f
                while (y < g - s * 0.1f) {
                    c.drawLine(x - s * 0.06f, y, x + s * 0.06f, y + s * 0.03f, line)
                    y += s * 0.05f
                }
                box(c, x - s * 0.28f, g - s * 0.55f, x + s * 0.18f, g - s * 0.48f, s * 0.03f, 0xFFD8C8B8.toInt(), 0xFFA89888.toInt())
                box(c, x - s * 0.22f, g - s * 1.02f, x + s * 0.26f, g - s * 0.92f, s * 0.04f, 0xFFFFC2DA.toInt(), 0xFFD87AA0.toInt())
                fill.color = 0xFFFF6F91.toInt()
                c.drawCircle(x + s * 0.2f, g - s * 0.4f, s * 0.05f, fill)
                line.color = 0xFFFF6F91.toInt()
                c.drawLine(x + s * 0.18f, g - s * 0.5f, x + s * 0.2f, g - s * 0.44f, line)
            }
            "toy_box" -> {
                box(c, x - s * 0.32f, g - s * 0.4f, x + s * 0.32f, g, s * 0.04f, 0xFF8FD3FF.toInt(), 0xFF4A90D0.toInt())
                fill.shader = null
                fill.color = Colors.alpha(Color.WHITE, 0.7f)
                Shapes.softStar(path, x, g - s * 0.2f, s * 0.08f, s * 0.04f, 5, 0f)
                c.drawPath(path, fill)
                // teddy peeking out
                path.reset()
                path.addCircle(x - s * 0.08f, g - s * 0.48f, s * 0.13f, Path.Direction.CW)
                path.addCircle(x - s * 0.18f, g - s * 0.58f, s * 0.05f, Path.Direction.CW)
                path.addCircle(x + s * 0.02f, g - s * 0.58f, s * 0.05f, Path.Direction.CW)
                shaded(c, path, 0xFFE8B888.toInt(), 0xFFB8804A.toInt(), 0xFF7A4E22.toInt(), outline = false)
                fill.color = 0xFF3A2A1A.toInt()
                c.drawCircle(x - s * 0.12f, g - s * 0.5f, s * 0.015f, fill)
                c.drawCircle(x - s * 0.04f, g - s * 0.5f, s * 0.015f, fill)
                path.reset()
                path.addRect(x + s * 0.08f, g - s * 0.55f, x + s * 0.22f, g - s * 0.41f, Path.Direction.CW)
                shaded(c, path, 0xFFFFE066.toInt(), 0xFFFFB84D.toInt(), 0xFFC07A00.toInt(), outline = false)
            }
            "toy_horse" -> {
                path.reset()
                path.addArc(x - s * 0.4f, g - s * 0.2f, x + s * 0.4f, g + s * 0.1f, 20f, 140f)
                line.shader = null
                line.color = 0xFFC0602A.toInt()
                line.strokeWidth = s * 0.05f
                c.drawPath(path, line)
                path.reset()
                path.addRoundRect(x - s * 0.25f, g - s * 0.42f, x + s * 0.18f, g - s * 0.26f, s * 0.08f, s * 0.08f, Path.Direction.CW)
                shaded(c, path, Color.WHITE, 0xFFF4EEF4.toInt(), 0xFFC8B8C8.toInt())
                path.reset()
                path.addRoundRect(x + s * 0.1f, g - s * 0.62f, x + s * 0.26f, g - s * 0.34f, s * 0.07f, s * 0.07f, Path.Direction.CW)
                shaded(c, path, Color.WHITE, 0xFFF4EEF4.toInt(), 0xFFC8B8C8.toInt())
                fill.color = 0xFFFF8FB8.toInt()
                c.drawOval(x + s * 0.06f, g - s * 0.66f, x + s * 0.16f, g - s * 0.42f, fill)
                fill.color = 0xFF3A2A3A.toInt()
                c.drawCircle(x + s * 0.2f, g - s * 0.55f, s * 0.018f, fill)
                for (lx in floatArrayOf(-0.2f, 0.12f)) box(c, x + s * lx, g - s * 0.27f, x + s * (lx + 0.05f), g - s * 0.12f, 0f, 0xFFE8E0E8.toInt(), 0xFFB8A8B8.toInt())
            }
            "toy_aquarium" -> {
                box(c, x - s * 0.38f, g - s * 0.08f, x + s * 0.38f, g, s * 0.02f, 0xFF8A6A4A.toInt(), 0xFF5A3A1A.toInt())
                path.reset()
                path.addRoundRect(x - s * 0.36f, g - s * 0.55f, x + s * 0.36f, g - s * 0.08f, s * 0.04f, s * 0.04f, Path.Direction.CW)
                shader(LinearGradient(0f, g - s * 0.55f, 0f, g, Colors.alpha(0xFF8FE0FF.toInt(), 0.75f), Colors.alpha(0xFF2A8AC8.toInt(), 0.85f), Shader.TileMode.CLAMP))
                c.drawPath(path, fill)
                fill.shader = null
                line.shader = null
                line.color = Colors.alpha(Color.WHITE, 0.8f)
                line.strokeWidth = s * 0.015f
                c.drawPath(path, line)
                fill.color = 0xFFFF9A3D.toInt()
                c.drawOval(x - s * 0.15f, g - s * 0.35f, x - s * 0.02f, g - s * 0.27f, fill)
                fill.color = 0xFFFFD84D.toInt()
                c.drawOval(x + s * 0.08f, g - s * 0.24f, x + s * 0.2f, g - s * 0.17f, fill)
                fill.color = 0xFF4FB36A.toInt()
                for (k in 0..2) c.drawOval(x + s * (0.2f + k * 0.04f), g - s * (0.3f + k * 0.05f), x + s * (0.24f + k * 0.04f), g - s * 0.09f, fill)
                fill.color = Colors.alpha(Color.WHITE, 0.6f)
                for (k in 0..2) c.drawCircle(x - s * 0.25f, g - s * (0.15f + k * 0.1f), s * 0.02f, fill)
            }
            "toy_cave" -> {
                path.reset()
                path.moveTo(x - s * 0.4f, g)
                path.cubicTo(x - s * 0.45f, g - s * 0.75f, x + s * 0.45f, g - s * 0.75f, x + s * 0.4f, g)
                path.close()
                shaded(c, path, 0xFFD8C8FF.toInt(), 0xFF9A7CE8.toInt(), 0xFF5A3AA8.toInt())
                path.reset()
                path.moveTo(x - s * 0.2f, g)
                path.cubicTo(x - s * 0.22f, g - s * 0.42f, x + s * 0.22f, g - s * 0.42f, x + s * 0.2f, g)
                path.close()
                fill.shader = null
                fill.color = 0xFF3A2A5A.toInt()
                c.drawPath(path, fill)
                paw(c, x - s * 0.2f, g - s * 0.5f, s * 0.04f, -10f, Colors.alpha(Color.WHITE, 0.6f))
                paw(c, x + s * 0.22f, g - s * 0.42f, s * 0.04f, 15f, Colors.alpha(Color.WHITE, 0.6f))
            }
            "toy_trophy" -> {
                box(c, x - s * 0.4f, g - s * 0.9f, x + s * 0.4f, g, s * 0.03f, 0xFFB9824E.toInt(), 0xFF7A4E22.toInt())
                path.reset()
                path.addRect(x - s * 0.34f, g - s * 0.84f, x + s * 0.34f, g - s * 0.06f, Path.Direction.CW)
                fill.shader = null
                fill.color = Colors.alpha(0xFFE8F4FF.toInt(), 0.5f)
                c.drawPath(path, fill)
                for (shelf in 0..1) {
                    val sy = g - s * (0.1f + shelf * 0.38f)
                    box(c, x - s * 0.34f, sy - s * 0.02f, x + s * 0.34f, sy, 0f, 0xFFD8A060.toInt(), 0xFF9A6A2A.toInt())
                    val n = (trophies - shelf * 4).coerceIn(1, 4)
                    for (k in 0 until n) cup(c, x - s * 0.24f + k * s * 0.16f, sy, s * 0.12f)
                }
            }
        }
    }

    fun cup(c: Canvas, x: Float, base: Float, s: Float) {
        path.reset()
        path.moveTo(x - s * 0.4f, base - s)
        path.quadTo(x, base - s * 0.2f, x + s * 0.4f, base - s)
        path.close()
        shaded(c, path, 0xFFFFF4C2.toInt(), 0xFFFFC83D.toInt(), 0xFFC08000.toInt(), outlineW = s * 0.03f)
        box(c, x - s * 0.2f, base - s * 0.25f, x + s * 0.2f, base, s * 0.04f, 0xFFFFD86A.toInt(), 0xFFC08000.toInt())
    }
}
