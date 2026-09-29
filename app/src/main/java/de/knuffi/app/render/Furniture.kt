package de.knuffi.app.render

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Path
import android.graphics.Shader
import de.knuffi.core.Catalog
import de.knuffi.core.GameState
import de.knuffi.core.SeasonEvent
import de.knuffi.core.Slot
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** How the "Kinderzimmer" is furnished (plus the decoration of a running event). */
data class Furnishing(
    val wall: String = "wall_rose",
    val rug: String = "rug_round",
    val bed: String = "bed_classic",
    val plant: String = "plant_monstera",
    val lamp: String = "lamp_floor",
    val picture: String = "pic_landscape",
    val event: SeasonEvent? = null,
) {
    val key: String get() = "$wall|$rug|$bed|$plant|$lamp|$picture|${event?.name}"

    companion object {
        fun of(state: GameState, event: SeasonEvent?): Furnishing {
            val d = Catalog.defaultEquipped(state.settings.look)
            fun get(slot: Slot) = state.equipped[slot] ?: d.getValue(slot)
            return Furnishing(get(Slot.WALL), get(Slot.RUG), get(Slot.BED), get(Slot.PLANT), get(Slot.LAMP), get(Slot.PICTURE), event)
        }
    }
}

class WallStyle(
    val top: Int,
    val bottom: Int,
    val wainTop: Int,
    val wainBottom: Int,
    val panelLine: Int,
    val panelFill: Int,
    val curtain: IntArray,
    val curtainLine: Int,
)

class FurnitureRenderer : Painter() {
    private val p3 = Path()

    fun wallStyle(id: String): WallStyle = when (id) {
        "wall_sky" -> WallStyle(
            0xFFEAF5FF.toInt(), 0xFFCFE5FF.toInt(), 0xFFD6E8FF.toInt(), 0xFFB8D2F2.toInt(), 0xFF5A80B8.toInt(), 0xFFC8DCF6.toInt(),
            intArrayOf(0xFF7FB8FF.toInt(), 0xFFA8D2FF.toInt(), 0xFF5A98E8.toInt(), 0xFF98C8FF.toInt(), 0xFF4E8AD8.toInt()), 0xFF2E5A9E.toInt(),
        )
        "wall_mint" -> WallStyle(
            0xFFEAFFF4.toInt(), 0xFFCFF2E0.toInt(), 0xFFCDEFDC.toInt(), 0xFFAEDDC4.toInt(), 0xFF4A9A70.toInt(), 0xFFC0E8D2.toInt(),
            intArrayOf(0xFF7ED8A8.toInt(), 0xFFA8EAC6.toInt(), 0xFF52B884.toInt(), 0xFF96E0B8.toInt(), 0xFF46A874.toInt()), 0xFF2A7A50.toInt(),
        )
        "wall_stars" -> WallStyle(
            0xFF3C428E.toInt(), 0xFF2B2F6E.toInt(), 0xFF4A509C.toInt(), 0xFF363B80.toInt(), 0xFF1E2250.toInt(), 0xFF454B94.toInt(),
            intArrayOf(0xFFB28CFF.toInt(), 0xFFCDB4FF.toInt(), 0xFF8E66E8.toInt(), 0xFFC4A8FF.toInt(), 0xFF7E58D8.toInt()), 0xFF4A2E9E.toInt(),
        )
        "wall_wood" -> WallStyle(
            0xFFF3CF9E.toInt(), 0xFFE0AE78.toInt(), 0xFFC98E5A.toInt(), 0xFFA96F40.toInt(), 0xFF6A4020.toInt(), 0xFFBE8350.toInt(),
            intArrayOf(0xFFE85A5A.toInt(), 0xFFFF8A80.toInt(), 0xFFC83A3A.toInt(), 0xFFF07A70.toInt(), 0xFFB83030.toInt()), 0xFF7A1E1E.toInt(),
        )
        else -> WallStyle(
            0xFFFFF2E8.toInt(), 0xFFFFE0D2.toInt(), 0xFFF9D9E7.toInt(), 0xFFEFC2D6.toInt(), 0xFFB5708F.toInt(), 0xFFF5D0E0.toInt(),
            intArrayOf(0xFFFF8FB8.toInt(), 0xFFFFB3CF.toInt(), 0xFFE86B9E.toInt(), 0xFFFFA8C8.toInt(), 0xFFE0609A.toInt()), 0xFFB23F72.toInt(),
        )
    }

    // ------------------------------------------------------------------ wall

    fun wall(c: Canvas, L: SceneLayout, id: String, wt: Float) {
        val w = L.w
        val h = L.h
        val fy = L.floorY
        val st = wallStyle(id)
        vgrad(c, 0f, 0f, w, fy, st.top, st.bottom)
        fill.shader = null
        when (id) {
            "wall_sky" -> {
                fill.color = Colors.alpha(Color.WHITE, 0.75f)
                var row = 0
                var py = h * 0.05f
                while (py < wt - h * 0.03f) {
                    var px = if (row % 2 == 0) w * 0.08f else w * 0.2f
                    while (px < w) {
                        Shapes.cloud(path, px, py, w * 0.045f)
                        c.drawPath(path, fill)
                        px += w * 0.26f
                    }
                    py += h * 0.07f
                    row++
                }
            }
            "wall_mint" -> {
                fill.color = Colors.alpha(Color.WHITE, 0.35f)
                var sx = 0f
                while (sx < w) {
                    c.drawRect(sx, 0f, sx + w * 0.02f, fy, fill)
                    sx += w * 0.08f
                }
                var row = 0
                var py = h * 0.04f
                while (py < wt - h * 0.03f) {
                    var px = if (row % 2 == 0) w * 0.04f else w * 0.12f
                    while (px < w) {
                        smallLeaf(c, px, py, w * 0.014f, if (row % 2 == 0) -30f else 30f, Colors.alpha(0xFF5FBF8A.toInt(), 0.45f))
                        px += w * 0.16f
                    }
                    py += h * 0.05f
                    row++
                }
            }
            "wall_stars" -> {
                var i = 0
                var py = h * 0.03f
                while (py < wt - h * 0.02f) {
                    var px = (i % 3) * w * 0.05f + w * 0.03f
                    while (px < w) {
                        val big = (i * 7 + (px * 13 / w).toInt()) % 4 == 0
                        fill.color = Colors.alpha(0xFFFFE680.toInt(), if (big) 0.85f else 0.5f)
                        Shapes.softStar(path, px, py, w * (if (big) 0.014f else 0.007f), w * (if (big) 0.006f else 0.003f), 5, i * 20f)
                        c.drawPath(path, fill)
                        px += w * 0.13f
                    }
                    py += h * 0.045f
                    i++
                }
                // a little moon
                path.reset()
                path.addCircle(w * 0.93f, h * 0.06f, w * 0.03f, Path.Direction.CW)
                path2.reset()
                path2.addCircle(w * 0.945f, h * 0.052f, w * 0.026f, Path.Direction.CW)
                path.op(path2, Path.Op.DIFFERENCE)
                fill.color = Colors.alpha(0xFFFFF2B0.toInt(), 0.9f)
                c.drawPath(path, fill)
            }
            "wall_wood" -> {
                line.shader = null
                line.color = Colors.alpha(0xFF8A5530.toInt(), 0.35f)
                line.strokeWidth = maxOf(1f, w * 0.003f)
                var sx = 0f
                var k = 0
                while (sx < w) {
                    c.drawLine(sx, 0f, sx, fy, line)
                    fill.color = Colors.alpha(0xFF8A5530.toInt(), 0.18f)
                    val ky = ((k * 37) % 10) / 10f * wt
                    c.drawOval(sx + w * 0.03f, ky, sx + w * 0.05f, ky + h * 0.012f, fill)
                    fill.color = Colors.alpha(Color.WHITE, 0.12f)
                    c.drawRect(sx + w * 0.004f, 0f, sx + w * 0.015f, fy, fill)
                    sx += w * 0.085f
                    k++
                }
            }
            else -> {
                fill.color = Colors.alpha(Color.WHITE, 0.35f)
                var sx = 0f
                while (sx < w) {
                    c.drawRect(sx, 0f, sx + w * 0.035f, fy, fill)
                    sx += w * 0.1f
                }
                fill.color = Colors.alpha(0xFFFF9EC4.toInt(), 0.22f)
                var row = 0
                var py = h * 0.03f
                while (py < fy - h * 0.14f) {
                    var px = if (row % 2 == 0) w * 0.05f else w * 0.1f
                    while (px < w) {
                        for (k in 0 until 4) {
                            val a = k * PI.toFloat() / 2f
                            c.drawCircle(px + cos(a) * w * 0.006f, py + sin(a) * w * 0.006f, w * 0.005f, fill)
                        }
                        px += w * 0.1f
                    }
                    py += h * 0.045f
                    row++
                }
            }
        }
        // wainscoting
        vgrad(c, 0f, wt, w, fy, st.wainTop, st.wainBottom)
        val panels = 5
        val pw = w / panels
        for (i in 0 until panels) {
            val l = i * pw + pw * 0.12f
            val r = (i + 1) * pw - pw * 0.12f
            val t = wt + (fy - wt) * 0.18f
            val b = fy - (fy - wt) * 0.22f
            fill.color = Colors.alpha(st.panelLine, 0.18f)
            c.drawRoundRect(l, t, r, b, w * 0.01f, w * 0.01f, fill)
            fill.color = Colors.alpha(Color.WHITE, if (id == "wall_stars") 0.12f else 0.45f)
            c.drawRoundRect(l + w * 0.004f, t + w * 0.004f, r + w * 0.004f, b + w * 0.004f, w * 0.01f, w * 0.01f, fill)
            fill.color = st.panelFill
            c.drawRoundRect(l + w * 0.003f, t + w * 0.003f, r, b, w * 0.01f, w * 0.01f, fill)
        }
        val rail = if (id == "wall_wood") 0xFFB9774A.toInt() else if (id == "wall_stars") 0xFF6A70C0.toInt() else 0xFFFFFFFF.toInt()
        box(c, 0f, wt - h * 0.01f, w, wt + h * 0.004f, h * 0.003f, rail, Colors.darken(rail, 0.12f))
        fill.color = Colors.alpha(0xFF3A2040.toInt(), 0.12f)
        c.drawRect(0f, wt + h * 0.004f, w, wt + h * 0.009f, fill)
    }

    private fun smallLeaf(c: Canvas, x: Float, y: Float, s: Float, rot: Float, color: Int) {
        c.save()
        c.rotate(rot, x, y)
        path.reset()
        path.moveTo(x - s, y)
        path.quadTo(x, y - s * 0.8f, x + s, y)
        path.quadTo(x, y + s * 0.8f, x - s, y)
        path.close()
        fill.shader = null
        fill.color = color
        c.drawPath(path, fill)
        c.restore()
    }

    // ------------------------------------------------------------------ rug

    fun rug(c: Canvas, L: SceneLayout, id: String) {
        val w = L.w
        val h = L.h
        val rx = w * 0.36f
        val ry = rx * 0.2f
        val rcx = w * 0.5f
        val rcy = L.groundY + h * 0.005f
        ellipseShadow(c, rcx, rcy + ry * 0.2f, rx * 1.05f, ry * 1.2f, 0.25f)
        when (id) {
            "rug_rainbow" -> {
                val colors = intArrayOf(0xFFFF6A7A.toInt(), 0xFFFFA24D.toInt(), 0xFFFFE066.toInt(), 0xFF6ED88A.toInt(), 0xFF5AB8FF.toInt(), 0xFFA88AFF.toInt(), 0xFFFFF6FA.toInt())
                for ((i, col) in colors.withIndex()) {
                    val k = 1f - i * 0.135f
                    path.reset()
                    path.addOval(rcx - rx * k, rcy - ry * k, rcx + rx * k, rcy + ry * k, Path.Direction.CW)
                    shader(LinearGradient(0f, rcy - ry, 0f, rcy + ry, Colors.lighten(col, 0.15f), Colors.darken(col, 0.08f), Shader.TileMode.CLAMP))
                    c.drawPath(path, fill)
                    fill.shader = null
                }
            }
            "rug_star" -> {
                c.save()
                c.scale(1f, 0.24f, rcx, rcy)
                Shapes.softStar(path, rcx, rcy, rx * 1.02f, rx * 0.56f, 5, 0f)
                shaded(c, path, 0xFFFFF4C2.toInt(), 0xFFFFC83D.toInt(), 0xFFD48A00.toInt(), outlineW = w * 0.006f)
                Shapes.softStar(path, rcx, rcy, rx * 0.6f, rx * 0.33f, 5, 0f)
                fill.color = Colors.alpha(Color.WHITE, 0.35f)
                c.drawPath(path, fill)
                c.restore()
            }
            "rug_leaf" -> {
                c.save()
                c.scale(1f, 0.24f, rcx, rcy)
                path.reset()
                path.moveTo(rcx - rx * 1.05f, rcy)
                path.quadTo(rcx, rcy - rx * 1.05f, rcx + rx * 1.05f, rcy)
                path.quadTo(rcx, rcy + rx * 1.05f, rcx - rx * 1.05f, rcy)
                path.close()
                shaded(c, path, 0xFFB8F0B0.toInt(), 0xFF62C46A.toInt(), 0xFF2E8A3E.toInt(), outlineW = w * 0.006f)
                line.shader = null
                line.color = Colors.alpha(0xFF2E7A3A.toInt(), 0.5f)
                line.strokeWidth = w * 0.012f
                c.drawLine(rcx - rx * 0.95f, rcy, rcx + rx * 0.95f, rcy, line)
                for (k in -3..3) {
                    if (k == 0) continue
                    val x = rcx + k * rx * 0.25f
                    c.drawLine(x, rcy, x + rx * 0.14f, rcy - rx * 0.28f, line)
                    c.drawLine(x, rcy, x + rx * 0.14f, rcy + rx * 0.28f, line)
                }
                c.restore()
            }
            else -> {
                val rugColors = intArrayOf(0xFFFF9EC4.toInt(), 0xFFFFC7DC.toInt(), 0xFFB9A8FF.toInt(), 0xFFFFE3EF.toInt())
                for ((i, col) in rugColors.withIndex()) {
                    val k = 1f - i * 0.2f
                    path.reset()
                    path.addOval(rcx - rx * k, rcy - ry * k, rcx + rx * k, rcy + ry * k, Path.Direction.CW)
                    shader(LinearGradient(0f, rcy - ry, 0f, rcy + ry, Colors.lighten(col, 0.15f), Colors.darken(col, 0.08f), Shader.TileMode.CLAMP))
                    c.drawPath(path, fill)
                    fill.shader = null
                }
            }
        }
    }

    // ------------------------------------------------------------------ picture

    /** Draws the picture inside the frame's inner rectangle (already clipped). */
    fun picture(c: Canvas, id: String, l: Float, t: Float, r: Float, b: Float) {
        val pw = r - l
        val ph = b - t
        when (id) {
            "pic_dragon" -> {
                vgrad(c, l, t, r, b, 0xFFFFD2A8.toInt(), 0xFFFFF0D8.toInt())
                path.reset()
                path.moveTo(l, b)
                path.lineTo(l + pw * 0.25f, t + ph * 0.45f)
                path.lineTo(l + pw * 0.45f, t + ph * 0.7f)
                path.lineTo(l + pw * 0.7f, t + ph * 0.35f)
                path.lineTo(r, t + ph * 0.75f)
                path.lineTo(r, b)
                path.close()
                shader(LinearGradient(0f, t, 0f, b, 0xFFB8A0E0.toInt(), 0xFF7A68B8.toInt(), Shader.TileMode.CLAMP))
                c.drawPath(path, fill)
                fill.shader = null
                val cx = l + pw * 0.48f
                val cy = t + ph * 0.55f
                val s = ph * 0.22f
                // wing
                path.reset()
                path.moveTo(cx, cy - s * 0.2f)
                path.lineTo(cx - s * 0.6f, cy - s * 1.4f)
                path.lineTo(cx - s * 0.3f, cy - s * 0.9f)
                path.lineTo(cx - s * 1.2f, cy - s * 1.1f)
                path.lineTo(cx - s * 0.4f, cy)
                path.close()
                fill.color = 0xFF2E9E6A.toInt()
                c.drawPath(path, fill)
                // tail
                path.reset()
                path.moveTo(cx - s * 0.6f, cy + s * 0.4f)
                path.quadTo(cx - s * 1.6f, cy + s * 0.9f, cx - s * 1.9f, cy + s * 0.2f)
                line.shader = null
                line.color = 0xFF46C080.toInt()
                line.strokeWidth = s * 0.35f
                c.drawPath(path, line)
                path.reset()
                path.addOval(cx - s * 0.9f, cy - s * 0.5f, cx + s * 0.7f, cy + s * 0.7f, Path.Direction.CW)
                shaded(c, path, 0xFFA8F0C8.toInt(), 0xFF46C080.toInt(), 0xFF1E7A4A.toInt(), outlineW = 1f)
                path.reset()
                path.addCircle(cx + s * 0.8f, cy - s * 0.5f, s * 0.45f, Path.Direction.CW)
                shaded(c, path, 0xFFA8F0C8.toInt(), 0xFF46C080.toInt(), 0xFF1E7A4A.toInt(), outlineW = 1f)
                fill.color = 0xFF1A1030.toInt()
                c.drawCircle(cx + s * 0.95f, cy - s * 0.6f, s * 0.08f, fill)
                fill.color = 0xFFFFE066.toInt()
                for (k in 0..2) {
                    Shapes.softTriangle(path, cx - s * 0.5f + k * s * 0.35f, cy - s * 0.4f, s * 0.12f, s * 0.25f)
                    c.drawPath(path, fill)
                }
                // little flame
                path.reset()
                path.moveTo(cx + s * 1.2f, cy - s * 0.45f)
                path.quadTo(cx + s * 1.7f, cy - s * 0.7f, cx + s * 2.1f, cy - s * 0.4f)
                path.quadTo(cx + s * 1.7f, cy - s * 0.2f, cx + s * 1.2f, cy - s * 0.45f)
                fill.color = 0xFFFF8A3D.toInt()
                c.drawPath(path, fill)
            }
            "pic_unicorn" -> {
                vgrad(c, l, t, r, b, 0xFFFFD6EC.toInt(), 0xFFE8DCFF.toInt())
                val colors = intArrayOf(0xFFFF7A9A.toInt(), 0xFFFFC46A.toInt(), 0xFFFFF07A.toInt(), 0xFF8AE8A0.toInt(), 0xFF8AC8FF.toInt(), 0xFFB99AFF.toInt())
                line.shader = null
                line.strokeWidth = ph * 0.06f
                for ((i, k) in colors.withIndex()) {
                    line.color = k
                    val rr = pw * 0.55f - i * ph * 0.06f
                    rect.set(l + pw * 0.5f - rr, b - rr * 0.8f, l + pw * 0.5f + rr, b + rr * 1.2f)
                    c.drawArc(rect, 180f, 180f, false, line)
                }
                val cx = l + pw * 0.5f
                val cy = t + ph * 0.6f
                val s = ph * 0.2f
                path.reset()
                path.addCircle(cx, cy, s, Path.Direction.CW)
                path.addOval(cx + s * 0.3f, cy - s * 0.1f, cx + s * 1.5f, cy + s * 0.75f, Path.Direction.CW)
                shaded(c, path, Color.WHITE, 0xFFF8F4FF.toInt(), 0xFFC8C0E0.toInt(), outlineW = 1f)
                path.reset()
                path.moveTo(cx + s * 0.1f, cy - s * 0.8f)
                path.lineTo(cx + s * 0.55f, cy - s * 2f)
                path.lineTo(cx + s * 0.55f, cy - s * 0.7f)
                path.close()
                shaded(c, path, 0xFFFFF4C2.toInt(), 0xFFFFC83D.toInt(), 0xFFD48A00.toInt(), outlineW = 1f)
                val mane = intArrayOf(0xFFFF8FC8.toInt(), 0xFFB99AFF.toInt(), 0xFF8AC8FF.toInt())
                for (k in 0..2) {
                    fill.color = mane[k]
                    c.drawCircle(cx - s * 0.8f, cy - s * 0.6f + k * s * 0.6f, s * 0.35f, fill)
                }
                fill.color = 0xFF2A1F3D.toInt()
                c.drawCircle(cx + s * 0.3f, cy - s * 0.2f, s * 0.12f, fill)
                fill.color = 0xFFFF9BB5.toInt()
                c.drawCircle(cx + s * 0.4f, cy + s * 0.25f, s * 0.14f, fill)
            }
            "pic_rocket" -> {
                vgrad(c, l, t, r, b, 0xFF1E2458.toInt(), 0xFF3A2E7A.toInt())
                fill.color = Colors.alpha(Color.WHITE, 0.85f)
                for (i in 0 until 14) {
                    val x = l + ((i * 37) % 100) / 100f * pw
                    val y = t + ((i * 53) % 100) / 100f * ph
                    c.drawCircle(x, y, ph * (0.012f + (i % 3) * 0.006f), fill)
                }
                path.reset()
                path.addCircle(l + pw * 0.18f, t + ph * 0.25f, ph * 0.12f, Path.Direction.CW)
                shaded(c, path, 0xFFFFD8A8.toInt(), 0xFFFF9A5A.toInt(), 0xFFB85A2A.toInt(), outlineW = 1f)
                val cx = l + pw * 0.58f
                val cy = t + ph * 0.5f
                val s = ph * 0.2f
                c.save()
                c.rotate(35f, cx, cy)
                path.reset()
                path.moveTo(cx - s * 0.9f, cy + s * 1.2f)
                path.lineTo(cx - s * 0.35f, cy + s * 0.4f)
                path.lineTo(cx - s * 0.35f, cy + s * 1.1f)
                path.close()
                path.moveTo(cx + s * 0.9f, cy + s * 1.2f)
                path.lineTo(cx + s * 0.35f, cy + s * 0.4f)
                path.lineTo(cx + s * 0.35f, cy + s * 1.1f)
                path.close()
                fill.color = 0xFFE0303F.toInt()
                c.drawPath(path, fill)
                path.reset()
                path.moveTo(cx, cy - s * 1.6f)
                path.cubicTo(cx + s * 0.6f, cy - s * 1f, cx + s * 0.45f, cy + s * 0.8f, cx + s * 0.35f, cy + s * 1.1f)
                path.lineTo(cx - s * 0.35f, cy + s * 1.1f)
                path.cubicTo(cx - s * 0.45f, cy + s * 0.8f, cx - s * 0.6f, cy - s * 1f, cx, cy - s * 1.6f)
                path.close()
                shaded(c, path, Color.WHITE, 0xFFE8ECF6.toInt(), 0xFFA8B0C8.toInt(), outlineW = 1f)
                path.reset()
                path.addCircle(cx, cy - s * 0.3f, s * 0.25f, Path.Direction.CW)
                shaded(c, path, 0xFFD0F0FF.toInt(), 0xFF5AB8FF.toInt(), 0xFF2A6AB8.toInt(), outlineW = 1f)
                path.reset()
                path.moveTo(cx - s * 0.25f, cy + s * 1.15f)
                path.quadTo(cx, cy + s * 2.2f, cx + s * 0.25f, cy + s * 1.15f)
                path.close()
                fill.color = 0xFFFFB02E.toInt()
                c.drawPath(path, fill)
                c.restore()
            }
            "pic_heart" -> {
                vgrad(c, l, t, r, b, 0xFFFFE0EC.toInt(), 0xFFFFC2D8.toInt())
                fill.color = Colors.alpha(Color.WHITE, 0.6f)
                for (i in 0 until 8) {
                    val x = l + ((i * 41) % 100) / 100f * pw
                    val y = t + ((i * 67) % 100) / 100f * ph
                    Shapes.heart(path, x, y, ph * 0.12f)
                    c.drawPath(path, fill)
                }
                Shapes.heart(path, l + pw * 0.5f, t + ph * 0.52f, ph * 0.6f)
                shaded(c, path, 0xFFFFB8D6.toInt(), 0xFFFF4F8B.toInt(), 0xFFB81E5A.toInt(), outlineW = 1f)
                fill.color = Colors.alpha(Color.WHITE, 0.8f)
                c.drawCircle(l + pw * 0.43f, t + ph * 0.38f, ph * 0.05f, fill)
            }
            else -> {
                vgrad(c, l, t, r, b, 0xFF9FD8FF.toInt(), 0xFFFFE3F1.toInt())
                fill.color = 0xFFFFD84A.toInt()
                c.drawCircle(r - pw * 0.15f, t + ph * 0.2f, ph * 0.13f, fill)
                path.reset()
                path.moveTo(l, b)
                path.quadTo(l + pw * 0.3f, t + ph * 0.45f, l + pw * 0.6f, b - ph * 0.2f)
                path.quadTo(l + pw * 0.8f, t + ph * 0.5f, r, b - ph * 0.15f)
                path.lineTo(r, b)
                path.close()
                shader(LinearGradient(0f, t, 0f, b, 0xFF8EDB9E.toInt(), 0xFF3F9E5C.toInt(), Shader.TileMode.CLAMP))
                c.drawPath(path, fill)
                fill.shader = null
            }
        }
    }

    // ------------------------------------------------------------------ lamp

    /** Warm glow colour of the lamp at night. */
    fun lampGlow(id: String): Int = when (id) {
        "lamp_mushroom" -> 0xFFFF9AD8.toInt()
        "lamp_moon" -> 0xFFD8E4FF.toInt()
        "lamp_lava" -> 0xFFFF8A5A.toInt()
        else -> 0xFFFFD18A.toInt()
    }

    fun lamp(c: Canvas, L: SceneLayout, id: String, lx: Float, ly: Float) {
        val w = L.w
        val h = L.h
        when (id) {
            "lamp_mushroom" -> {
                line.shader = null
                line.color = 0xFF6A5A7A.toInt()
                line.strokeWidth = maxOf(1f, w * 0.003f)
                c.drawLine(lx, 0f, lx, ly - h * 0.03f, line)
                box(c, lx - w * 0.012f, ly - h * 0.005f, lx + w * 0.012f, ly + h * 0.025f, w * 0.006f, 0xFFFFF4E8.toInt(), 0xFFE0C8B0.toInt())
                rect.set(lx - w * 0.06f, ly - h * 0.04f, lx + w * 0.06f, ly + h * 0.02f)
                path.reset()
                path.arcTo(rect, 180f, 180f)
                path.close()
                shaded(c, path, 0xFFFFC8E8.toInt(), 0xFFFF6FB5.toInt(), 0xFFB83A80.toInt(), outlineW = w * 0.002f)
                fill.color = Colors.alpha(Color.WHITE, 0.9f)
                c.drawCircle(lx - w * 0.025f, ly - h * 0.018f, w * 0.008f, fill)
                c.drawCircle(lx + w * 0.02f, ly - h * 0.026f, w * 0.01f, fill)
                c.drawCircle(lx + w * 0.04f, ly - h * 0.008f, w * 0.006f, fill)
            }
            "lamp_moon" -> {
                line.shader = null
                line.color = 0xFF6A5A7A.toInt()
                line.strokeWidth = maxOf(1f, w * 0.003f)
                c.drawLine(lx, 0f, lx, ly - h * 0.035f, line)
                path.reset()
                path.addCircle(lx, ly, w * 0.045f, Path.Direction.CW)
                path2.reset()
                path2.addCircle(lx + w * 0.025f, ly - w * 0.015f, w * 0.038f, Path.Direction.CW)
                path.op(path2, Path.Op.DIFFERENCE)
                shaded(c, path, Color.WHITE, 0xFFFFF2B0.toInt(), 0xFFD8B860.toInt(), outlineW = w * 0.002f)
                fill.color = Colors.alpha(0xFFD8B860.toInt(), 0.5f)
                c.drawCircle(lx - w * 0.025f, ly + w * 0.01f, w * 0.006f, fill)
            }
            "lamp_lava" -> {
                box(c, lx - w * 0.06f, ly + h * 0.05f, lx + w * 0.06f, ly + h * 0.058f, w * 0.003f, 0xFFE9B384.toInt(), 0xFFB9774A.toInt(), shadow = h * 0.005f)
                val base = ly + h * 0.05f
                path.reset()
                path.moveTo(lx - w * 0.022f, base)
                path.lineTo(lx - w * 0.014f, base - h * 0.018f)
                path.lineTo(lx + w * 0.014f, base - h * 0.018f)
                path.lineTo(lx + w * 0.022f, base)
                path.close()
                shaded(c, path, 0xFFC8D0E0.toInt(), 0xFF8A94B0.toInt(), 0xFF4A5470.toInt(), outlineW = w * 0.002f)
                path.reset()
                path.moveTo(lx - w * 0.014f, base - h * 0.018f)
                path.quadTo(lx - w * 0.028f, base - h * 0.05f, lx - w * 0.01f, base - h * 0.085f)
                path.lineTo(lx + w * 0.01f, base - h * 0.085f)
                path.quadTo(lx + w * 0.028f, base - h * 0.05f, lx + w * 0.014f, base - h * 0.018f)
                path.close()
                shader(LinearGradient(0f, base - h * 0.085f, 0f, base, 0xFFFFB0D8.toInt(), 0xFFFF6A8A.toInt(), Shader.TileMode.CLAMP))
                c.drawPath(path, fill)
                fill.shader = null
                fill.color = 0xFFFFD84D.toInt()
                c.drawCircle(lx - w * 0.004f, base - h * 0.035f, w * 0.008f, fill)
                c.drawCircle(lx + w * 0.005f, base - h * 0.062f, w * 0.006f, fill)
                box(c, lx - w * 0.011f, base - h * 0.095f, lx + w * 0.011f, base - h * 0.083f, w * 0.004f, 0xFFC8D0E0.toInt(), 0xFF4A5470.toInt())
            }
            else -> {
                // floor lamp standing at the wall
                val fy = L.floorY
                ellipseShadow(c, lx, fy, w * 0.04f, h * 0.006f, 0.3f)
                box(c, lx - w * 0.005f, ly + h * 0.01f, lx + w * 0.005f, fy - h * 0.004f, 0f, 0xFFD8A657.toInt(), 0xFF9C6A2A.toInt())
                path.reset()
                path.addOval(lx - w * 0.03f, fy - h * 0.008f, lx + w * 0.03f, fy + h * 0.002f, Path.Direction.CW)
                shaded(c, path, 0xFFFFE08A.toInt(), 0xFFD8A657.toInt(), 0xFF9C6A2A.toInt(), outlineW = w * 0.002f)
                path.reset()
                path.moveTo(lx - w * 0.035f, ly - h * 0.03f)
                path.lineTo(lx + w * 0.035f, ly - h * 0.03f)
                path.lineTo(lx + w * 0.055f, ly + h * 0.012f)
                path.lineTo(lx - w * 0.055f, ly + h * 0.012f)
                path.close()
                shaded(c, path, 0xFFFFF6DF.toInt(), 0xFFFFD9A3.toInt(), 0xFFE0A75E.toInt(), outlineW = w * 0.002f)
            }
        }
    }

    // ------------------------------------------------------------------ plant

    fun plant(c: Canvas, L: SceneLayout, id: String) {
        val w = L.w
        val h = L.h
        val fy = L.floorY
        val px0 = w * 0.06f
        val potTop = fy - h * 0.02f
        when (id) {
            "plant_cactus" -> {
                val top = potTop - h * 0.2f
                val cw = w * 0.032f
                for (s in intArrayOf(-1, 1)) {
                    val ay = potTop - h * (if (s < 0) 0.09f else 0.13f)
                    path.reset()
                    path.moveTo(px0, ay)
                    path.lineTo(px0 + s * w * 0.06f, ay)
                    path.lineTo(px0 + s * w * 0.06f, ay - h * 0.05f)
                    line.shader = null
                    line.color = 0xFF3E9E5A.toInt()
                    line.strokeWidth = cw * 1.1f
                    c.drawPath(path, line)
                    line.color = Colors.alpha(0xFF9BE3A8.toInt(), 0.6f)
                    line.strokeWidth = cw * 0.3f
                    c.drawPath(path, line)
                }
                path.reset()
                path.addRoundRect(px0 - cw, top, px0 + cw, potTop + h * 0.01f, cw, cw, Path.Direction.CW)
                shaded(c, path, 0xFF9BE3A8.toInt(), 0xFF4FB36A.toInt(), 0xFF2B7A40.toInt(), outlineW = w * 0.002f)
                line.color = Colors.alpha(0xFF2B7A40.toInt(), 0.5f)
                line.strokeWidth = maxOf(1f, w * 0.002f)
                c.drawLine(px0, top + cw, px0, potTop, line)
                fill.color = 0xFFFF7EB6.toInt()
                for (k in 0 until 5) {
                    val a = k * (PI * 2 / 5).toFloat()
                    c.drawCircle(px0 + cos(a) * w * 0.01f, top + sin(a) * w * 0.01f, w * 0.009f, fill)
                }
                fill.color = 0xFFFFE066.toInt()
                c.drawCircle(px0, top, w * 0.006f, fill)
                pot(c, px0, potTop, w, h, 0xFF8FB8FF.toInt())
            }
            "plant_sunflower" -> {
                val top = potTop - h * 0.24f
                line.shader = null
                line.color = 0xFF4E9A3A.toInt()
                line.strokeWidth = w * 0.012f
                c.drawLine(px0, potTop, px0 + w * 0.01f, top, line)
                for (s in intArrayOf(-1, 1)) smallLeafShaded(c, px0 + s * w * 0.04f, potTop - h * (if (s < 0) 0.08f else 0.13f), w * 0.04f, s * 20f)
                val fx = px0 + w * 0.01f
                for (k in 0 until 12) {
                    c.save()
                    c.rotate(k * 30f, fx, top)
                    path.reset()
                    path.addOval(fx - w * 0.012f, top - w * 0.07f, fx + w * 0.012f, top - w * 0.02f, Path.Direction.CW)
                    shaded(c, path, 0xFFFFF4A0.toInt(), 0xFFFFC83D.toInt(), 0xFFD48A00.toInt(), outline = false)
                    c.restore()
                }
                path.reset()
                path.addCircle(fx, top, w * 0.03f, Path.Direction.CW)
                shaded(c, path, 0xFFB08050.toInt(), 0xFF6A4020.toInt(), 0xFF3A2010.toInt(), outlineW = w * 0.002f)
                pot(c, px0, potTop, w, h, 0xFFE5773F.toInt())
            }
            "plant_bonsai" -> {
                path.reset()
                path.moveTo(px0 - w * 0.01f, potTop)
                path.quadTo(px0 + w * 0.03f, potTop - h * 0.05f, px0 - w * 0.005f, potTop - h * 0.1f)
                line.shader = null
                line.color = 0xFF7A5230.toInt()
                line.strokeWidth = w * 0.016f
                c.drawPath(path, line)
                val canopy = floatArrayOf(-0.04f, -0.11f, 0.05f, 0.03f, -0.13f, 0.055f, 0f, -0.15f, 0.05f, -0.06f, -0.15f, 0.04f)
                for (i in 0 until 4) {
                    val cx = px0 + canopy[i * 3] * w
                    val cy = potTop + canopy[i * 3 + 1] * h
                    val r = canopy[i * 3 + 2] * w
                    path.reset()
                    path.addOval(cx - r, cy - r * 0.7f, cx + r, cy + r * 0.7f, Path.Direction.CW)
                    shaded(c, path, 0xFFA8E8A0.toInt(), 0xFF4FB36A.toInt(), 0xFF2B7A40.toInt(), outlineW = w * 0.002f)
                }
                pot(c, px0, potTop, w, h, 0xFF5A8AC8.toInt(), shallow = true)
            }
            else -> {
                for (i in 0 until 7) {
                    val a = -90f + (i - 3) * 24f
                    c.save()
                    c.rotate(a, px0, potTop)
                    path.reset()
                    path.addOval(px0, potTop - h * 0.028f, px0 + h * 0.16f, potTop + h * 0.028f, Path.Direction.CW)
                    val lc = if (i % 2 == 0) 0xFF52B86E.toInt() else 0xFF3E9E5A.toInt()
                    shaded(c, path, Colors.lighten(lc, 0.35f), lc, Colors.darken(lc, 0.3f), outlineW = w * 0.002f)
                    line.color = Colors.alpha(Color.WHITE, 0.35f)
                    line.strokeWidth = w * 0.003f
                    c.drawLine(px0 + h * 0.02f, potTop, px0 + h * 0.14f, potTop, line)
                    c.restore()
                }
                pot(c, px0, potTop, w, h, 0xFFE5773F.toInt())
            }
        }
    }

    private fun smallLeafShaded(c: Canvas, x: Float, y: Float, s: Float, rot: Float) {
        c.save()
        c.rotate(rot, x, y)
        path.reset()
        path.moveTo(x - s, y)
        path.quadTo(x, y - s * 0.6f, x + s, y)
        path.quadTo(x, y + s * 0.6f, x - s, y)
        path.close()
        shaded(c, path, 0xFF9BE3A8.toInt(), 0xFF4FB36A.toInt(), 0xFF2B7A40.toInt(), outlineW = 1f)
        c.restore()
    }

    private fun pot(c: Canvas, px0: Float, potTop: Float, w: Float, h: Float, color: Int, shallow: Boolean = false) {
        val ph = h * if (shallow) 0.035f else 0.075f
        val pw = w * if (shallow) 0.09f else 0.07f
        path.reset()
        path.moveTo(px0 - pw, potTop)
        path.lineTo(px0 + pw, potTop)
        path.lineTo(px0 + pw * 0.8f, potTop + ph)
        path.lineTo(px0 - pw * 0.8f, potTop + ph)
        path.close()
        ellipseShadow(c, px0, potTop + ph, pw * 1.15f, h * 0.01f, 0.3f)
        shaded(c, path, Colors.lighten(color, 0.4f), color, Colors.darken(color, 0.35f), outlineW = w * 0.003f)
        box(c, px0 - pw * 1.1f, potTop - h * 0.008f, px0 + pw * 1.1f, potTop + h * 0.01f, w * 0.005f, Colors.lighten(color, 0.4f), Colors.darken(color, 0.2f))
    }

    // ------------------------------------------------------------------ events

    /** A small seasonal decoration on the window sill. */
    fun sillItem(c: Canvas, event: SeasonEvent, x: Float, y: Float, s: Float) {
        when (event) {
            SeasonEvent.NEUJAHR -> {
                box(c, x - s * 0.35f, y - s * 0.45f, x + s * 0.35f, y, s * 0.08f, 0xFFFFB38A.toInt(), 0xFFC45E2C.toInt())
                for (k in 0 until 4) {
                    val a = k * (PI / 2).toFloat() + (PI / 4).toFloat()
                    Shapes.heart(path, x + cos(a) * s * 0.22f, y - s * 0.75f + sin(a) * s * 0.22f, s * 0.35f)
                    shaded(c, path, 0xFFA8F0A0.toInt(), 0xFF3EAE4A.toInt(), 0xFF1E6A2A.toInt(), outlineW = 1f)
                }
            }
            SeasonEvent.VALENTIN -> {
                Shapes.heart(path, x, y - s * 0.4f, s * 0.9f)
                shaded(c, path, 0xFFFFB8D6.toInt(), 0xFFE0304A.toInt(), 0xFF8E1422.toInt(), outlineW = 1f)
                line.shader = null
                line.color = 0xFFFFD84D.toInt()
                line.strokeWidth = s * 0.08f
                c.drawLine(x, y - s * 0.8f, x, y, line)
            }
            SeasonEvent.OSTERN -> {
                val eggs = intArrayOf(0xFFFF8FB8.toInt(), 0xFF8FD3FF.toInt(), 0xFFFFE066.toInt())
                for ((k, col) in eggs.withIndex()) {
                    val ex = x + (k - 1) * s * 0.3f
                    path.reset()
                    path.addOval(ex - s * 0.16f, y - s * 0.62f + (k % 2) * s * 0.06f, ex + s * 0.16f, y - s * 0.2f, Path.Direction.CW)
                    shaded(c, path, Color.WHITE, col, Colors.darken(col, 0.25f), outlineW = 1f)
                }
                path.reset()
                path.moveTo(x - s * 0.6f, y - s * 0.35f)
                path.lineTo(x + s * 0.6f, y - s * 0.35f)
                path.lineTo(x + s * 0.45f, y)
                path.lineTo(x - s * 0.45f, y)
                path.close()
                shaded(c, path, 0xFFF0C890.toInt(), 0xFFC8904A.toInt(), 0xFF8A5A20.toInt(), outlineW = 1f)
            }
            SeasonEvent.SOMMERFEST -> {
                path.reset()
                path.moveTo(x - s * 0.4f, y - s * 0.7f)
                path.lineTo(x + s * 0.4f, y - s * 0.7f)
                path.lineTo(x + s * 0.3f, y)
                path.lineTo(x - s * 0.3f, y)
                path.close()
                shaded(c, path, 0xFFA8E0FF.toInt(), 0xFF3E9EF0.toInt(), 0xFF1E5AA8.toInt(), outlineW = 1f)
                fill.color = 0xFFFFE066.toInt()
                c.drawRect(x - s * 0.36f, y - s * 0.45f, x + s * 0.34f, y - s * 0.35f, fill)
                line.shader = null
                line.color = 0xFFE0303F.toInt()
                line.strokeWidth = s * 0.08f
                c.drawLine(x + s * 0.2f, y - s * 0.6f, x + s * 0.55f, y - s * 1.1f, line)
            }
            SeasonEvent.HALLOWEEN -> {
                for (k in -1..1) {
                    path.reset()
                    path.addOval(x + k * s * 0.22f - s * 0.3f, y - s * 0.62f, x + k * s * 0.22f + s * 0.3f, y, Path.Direction.CW)
                    shaded(c, path, 0xFFFFC88A.toInt(), 0xFFFF8A2A.toInt(), 0xFFB84A10.toInt(), outlineW = 1f)
                }
                box(c, x - s * 0.05f, y - s * 0.78f, x + s * 0.05f, y - s * 0.58f, s * 0.02f, 0xFF6A8A3A.toInt(), 0xFF3A5A1A.toInt())
                fill.color = 0xFF3A1A08.toInt()
                for (sx in intArrayOf(-1, 1)) {
                    path.reset()
                    path.moveTo(x + sx * s * 0.2f, y - s * 0.44f)
                    path.lineTo(x + sx * s * 0.08f, y - s * 0.32f)
                    path.lineTo(x + sx * s * 0.3f, y - s * 0.32f)
                    path.close()
                    c.drawPath(path, fill)
                }
                path.reset()
                path.moveTo(x - s * 0.25f, y - s * 0.22f)
                path.quadTo(x, y - s * 0.05f, x + s * 0.25f, y - s * 0.22f)
                path.quadTo(x, y - s * 0.14f, x - s * 0.25f, y - s * 0.22f)
                c.drawPath(path, fill)
            }
            SeasonEvent.WINTERZAUBER -> {
                for (k in 0..2) {
                    val ty = y - s * (0.3f + k * 0.35f)
                    val hw = s * (0.5f - k * 0.13f)
                    Shapes.softTriangle(path, x, ty + s * 0.15f, hw, s * 0.5f)
                    shaded(c, path, 0xFF8AE0A0.toInt(), 0xFF2E9E5A.toInt(), 0xFF1A5A30.toInt(), outlineW = 1f)
                }
                box(c, x - s * 0.08f, y - s * 0.2f, x + s * 0.08f, y, s * 0.02f, 0xFF9A6A3A.toInt(), 0xFF6A4020.toInt())
                Shapes.softStar(path, x, y - s * 1.35f, s * 0.14f, s * 0.07f, 5, 0f)
                shaded(c, path, Color.WHITE, 0xFFFFD84D.toInt(), 0xFFC89A00.toInt(), outline = false)
                val balls = intArrayOf(0xFFE0303F.toInt(), 0xFF4C8DFF.toInt(), 0xFFFFD84D.toInt())
                for (k in 0..2) {
                    fill.color = balls[k]
                    c.drawCircle(x + (k - 1) * s * 0.2f, y - s * (0.35f + (k % 2) * 0.35f), s * 0.05f, fill)
                }
            }
        }
    }

    /** A garland across the wall, gently swaying. */
    fun garland(c: Canvas, L: SceneLayout, event: SeasonEvent, t: Float) {
        val w = L.w
        val h = L.h
        val y0 = maxOf(h * 0.03f, L.window.top - h * 0.06f)
        val sag = h * 0.035f
        line.shader = null
        line.color = when (event) {
            SeasonEvent.HALLOWEEN -> 0xFF3A2E4A.toInt()
            SeasonEvent.WINTERZAUBER -> 0xFF2E6A3A.toInt()
            else -> 0xFFE8D0A0.toInt()
        }
        line.strokeWidth = maxOf(1.5f, w * 0.004f)
        val spans = 2
        val sw = w / spans
        for (sp in 0 until spans) {
            val x0 = sp * sw
            p3.reset()
            p3.moveTo(x0, y0)
            p3.quadTo(x0 + sw / 2f, y0 + sag * 2f, x0 + sw, y0)
            c.drawPath(p3, line)
        }
        val n = 8
        for (sp in 0 until spans) {
            for (i in 1 until n) {
                val k = i / n.toFloat()
                val x = sp * sw + k * sw
                val y = y0 + sag * 4f * k * (1f - k) + h * 0.004f
                val idx = sp * n + i
                val sway = sin(t * 1.5f + idx) * 6f
                ornament(c, event, x, y, w * 0.022f, idx, t, sway)
            }
        }
    }

    private fun ornament(c: Canvas, event: SeasonEvent, x: Float, y: Float, s: Float, i: Int, t: Float, sway: Float) {
        c.save()
        c.rotate(sway, x, y)
        when (event) {
            SeasonEvent.NEUJAHR -> {
                val col = if (i % 2 == 0) 0xFFFFD84D.toInt() else 0xFFD8DCE8.toInt()
                Shapes.softStar(path, x, y + s * 0.9f, s * 0.8f, s * 0.38f, 5, 0f)
                shaded(c, path, Color.WHITE, col, Colors.darken(col, 0.3f), outlineW = 1f)
            }
            SeasonEvent.VALENTIN -> {
                val col = if (i % 2 == 0) 0xFFFF4F8B.toInt() else 0xFFFFA8C8.toInt()
                Shapes.heart(path, x, y + s * 0.8f, s * 1.5f)
                shaded(c, path, Colors.lighten(col, 0.5f), col, Colors.darken(col, 0.3f), outlineW = 1f)
            }
            SeasonEvent.OSTERN -> {
                val cols = intArrayOf(0xFFFF8FB8.toInt(), 0xFF8FD3FF.toInt(), 0xFFFFE066.toInt(), 0xFFA8E8A0.toInt(), 0xFFC8A8FF.toInt())
                val col = cols[i % cols.size]
                path.reset()
                path.addOval(x - s * 0.55f, y + s * 0.1f, x + s * 0.55f, y + s * 1.6f, Path.Direction.CW)
                shaded(c, path, Color.WHITE, col, Colors.darken(col, 0.25f), outlineW = 1f)
                fill.color = Colors.alpha(Color.WHITE, 0.7f)
                c.drawRect(x - s * 0.55f, y + s * 0.8f, x + s * 0.55f, y + s * 0.95f, fill)
            }
            SeasonEvent.SOMMERFEST -> {
                val cols = intArrayOf(0xFFFF6A7A.toInt(), 0xFFFFC83D.toInt(), 0xFF3EC8A0.toInt(), 0xFF4C8DFF.toInt(), 0xFFB27CFF.toInt())
                path.reset()
                path.moveTo(x - s * 0.7f, y)
                path.lineTo(x + s * 0.7f, y)
                path.lineTo(x, y + s * 1.6f)
                path.close()
                val col = cols[i % cols.size]
                shaded(c, path, Colors.lighten(col, 0.4f), col, Colors.darken(col, 0.3f), outlineW = 1f)
            }
            SeasonEvent.HALLOWEEN -> {
                if (i % 2 == 0) {
                    path.reset()
                    path.addOval(x - s * 0.7f, y + s * 0.2f, x + s * 0.7f, y + s * 1.4f, Path.Direction.CW)
                    shaded(c, path, 0xFFFFC88A.toInt(), 0xFFFF8A2A.toInt(), 0xFFB84A10.toInt(), outlineW = 1f)
                    fill.color = 0xFF3A1A08.toInt()
                    c.drawCircle(x - s * 0.25f, y + s * 0.7f, s * 0.12f, fill)
                    c.drawCircle(x + s * 0.25f, y + s * 0.7f, s * 0.12f, fill)
                } else {
                    val flap = sin(t * 8f + i) * 0.3f
                    path.reset()
                    path.moveTo(x, y + s * 0.5f)
                    path.quadTo(x - s * 0.5f, y + s * (0.1f - flap), x - s * 1.1f, y + s * 0.3f)
                    path.quadTo(x - s * 0.7f, y + s * 0.6f, x - s * 0.5f, y + s * 0.9f)
                    path.quadTo(x - s * 0.25f, y + s * 0.65f, x, y + s * 0.95f)
                    path.quadTo(x + s * 0.25f, y + s * 0.65f, x + s * 0.5f, y + s * 0.9f)
                    path.quadTo(x + s * 0.7f, y + s * 0.6f, x + s * 1.1f, y + s * 0.3f)
                    path.quadTo(x + s * 0.5f, y + s * (0.1f - flap), x, y + s * 0.5f)
                    path.close()
                    fill.shader = null
                    fill.color = 0xFF2E2440.toInt()
                    c.drawPath(path, fill)
                }
            }
            SeasonEvent.WINTERZAUBER -> {
                val cols = intArrayOf(0xFFFF5A6A.toInt(), 0xFFFFD84D.toInt(), 0xFF5AD88A.toInt(), 0xFF5AB8FF.toInt())
                val col = cols[i % cols.size]
                val on = (sin(t * 2.5f + i * 1.3f) + 1f) / 2f
                shader(radial(x, y + s * 0.8f, s * 2.2f, intArrayOf(Colors.alpha(col, 0.5f * on), Colors.alpha(col, 0f))))
                c.drawCircle(x, y + s * 0.8f, s * 2.2f, fill)
                fill.shader = null
                path.reset()
                path.addOval(x - s * 0.4f, y + s * 0.2f, x + s * 0.4f, y + s * 1.3f, Path.Direction.CW)
                shaded(c, path, Color.WHITE, Colors.lerp(Colors.darken(col, 0.3f), Colors.lighten(col, 0.3f), on), Colors.darken(col, 0.4f), outlineW = 1f)
                fill.color = 0xFF4A5470.toInt()
                c.drawRect(x - s * 0.22f, y, x + s * 0.22f, y + s * 0.25f, fill)
            }
        }
        c.restore()
    }
}
