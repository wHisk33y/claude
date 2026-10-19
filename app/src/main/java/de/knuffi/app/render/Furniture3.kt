package de.knuffi.app.render

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Path
import kotlin.math.min

/** The cosy corner at the front right of the room: sofa, bean bag, piano and friends. */
class DecoFurniture : Painter() {

    /** Where the corner stands (normalised x), so the pet can visit it. */
    fun x(L: SceneLayout): Float = if (L.kind == LayoutKind.BOX) 0.84f else 0.81f

    fun deco(c: Canvas, L: SceneLayout, id: String?) {
        if (id == null) return
        val w = L.w
        val h = L.h
        val x = w * x(L)
        val g = min(L.groundY + h * 0.06f, h * 0.975f)
        val s = L.petSize * 0.8f
        ellipseShadow(c, x, g, s * 0.62f, s * 0.08f, 0.3f)
        when (id) {
            "deco_beanbag" -> beanbag(c, x, g, s)
            "deco_sofa" -> sofa(c, x, g, s)
            "deco_bookshelf" -> bookshelf(c, x, g, s)
            "deco_globe" -> globe(c, x, g, s)
            "deco_piano" -> piano(c, x, g, s)
            "deco_fireplace" -> fireplace(c, x, g, s)
            "deco_tent" -> tent(c, x, g, s)
            "deco_stage" -> stage(c, x, g, s)
        }
    }

    private fun stage(c: Canvas, x: Float, g: Float, s: Float) {
        // the platform
        box(c, x - s * 0.6f, g - s * 0.2f, x + s * 0.6f, g, s * 0.04f, 0xFFB9824E.toInt(), 0xFF7A4E22.toInt(), shadow = s * 0.02f)
        box(c, x - s * 0.62f, g - s * 0.25f, x + s * 0.62f, g - s * 0.18f, s * 0.03f, 0xFFFF8FB8.toInt(), 0xFFC23A7A.toInt())
        // little light bulbs along the edge
        fill.shader = null
        for (k in 0 until 7) {
            fill.color = if (k % 2 == 0) 0xFFFFF3B0.toInt() else 0xFFFFD166.toInt()
            c.drawCircle(x - s * 0.5f + k * s * (1f / 6f), g - s * 0.1f, s * 0.025f, fill)
        }
        // curtains
        for (k in intArrayOf(-1, 1)) {
            val ox = x + k * s * 0.6f
            path.reset()
            path.moveTo(ox, g - s * 1.1f)
            path.lineTo(ox - k * s * 0.3f, g - s * 1.1f)
            path.quadTo(ox - k * s * 0.12f, g - s * 0.62f, ox - k * s * 0.04f, g - s * 0.25f)
            path.lineTo(ox, g - s * 0.25f)
            path.close()
            shaded(c, path, 0xFFFF8FA8.toInt(), 0xFFE0405A.toInt(), 0xFF8A1A30.toInt(), outlineW = s * 0.01f)
        }
        // the top with stars
        box(c, x - s * 0.66f, g - s * 1.2f, x + s * 0.66f, g - s * 1.04f, s * 0.05f, 0xFFFFD86A.toInt(), 0xFFC08000.toInt(), shadow = s * 0.015f)
        fill.shader = null
        fill.color = Colors.alpha(Color.WHITE, 0.9f)
        for (k in 0 until 3) {
            Shapes.softStar(path, x - s * 0.3f + k * s * 0.3f, g - s * 1.12f, s * 0.045f, s * 0.022f, 5, 0f)
            c.drawPath(path, fill)
        }
    }

    private fun beanbag(c: Canvas, x: Float, g: Float, s: Float) {
        path.reset()
        path.moveTo(x - s * 0.5f, g - s * 0.04f)
        path.cubicTo(x - s * 0.62f, g - s * 0.42f, x - s * 0.2f, g - s * 0.72f, x + s * 0.05f, g - s * 0.62f)
        path.cubicTo(x + s * 0.4f, g - s * 0.5f, x + s * 0.6f, g - s * 0.3f, x + s * 0.5f, g - s * 0.04f)
        path.quadTo(x, g + s * 0.04f, x - s * 0.5f, g - s * 0.04f)
        path.close()
        shaded(c, path, 0xFFFFC6DC.toInt(), 0xFFFF7EB6.toInt(), 0xFFB8306E.toInt(), outlineW = s * 0.012f)
        // dent where you sit
        path.reset()
        path.addOval(x - s * 0.26f, g - s * 0.42f, x + s * 0.22f, g - s * 0.2f, Path.Direction.CW)
        fill.shader = null
        fill.color = Colors.alpha(0xFFB8306E.toInt(), 0.22f)
        c.drawPath(path, fill)
        line.shader = null
        line.color = Colors.alpha(Color.WHITE, 0.45f)
        line.strokeWidth = s * 0.015f
        c.drawArc(x - s * 0.4f, g - s * 0.66f, x + s * 0.1f, g - s * 0.1f, 200f, 70f, false, line)
    }

    private fun sofa(c: Canvas, x: Float, g: Float, s: Float) {
        val top = 0xFF9AD0FF.toInt()
        val bottom = 0xFF4A90D0.toInt()
        // legs
        box(c, x - s * 0.5f, g - s * 0.08f, x - s * 0.44f, g, s * 0.01f, 0xFF9A6A3A.toInt(), 0xFF6A4020.toInt())
        box(c, x + s * 0.44f, g - s * 0.08f, x + s * 0.5f, g, s * 0.01f, 0xFF9A6A3A.toInt(), 0xFF6A4020.toInt())
        // back rest
        box(c, x - s * 0.52f, g - s * 0.62f, x + s * 0.52f, g - s * 0.22f, s * 0.1f, top, bottom, shadow = s * 0.02f)
        // seat
        box(c, x - s * 0.56f, g - s * 0.3f, x + s * 0.56f, g - s * 0.07f, s * 0.07f, Colors.lighten(top, 0.1f), bottom)
        // arm rests
        for (k in intArrayOf(-1, 1)) {
            val ax = x + k * s * 0.56f
            box(c, ax - s * 0.1f, g - s * 0.46f, ax + s * 0.1f, g - s * 0.07f, s * 0.09f, Colors.lighten(top, 0.05f), Colors.darken(bottom, 0.1f))
        }
        // cushions
        box(c, x - s * 0.36f, g - s * 0.5f, x - s * 0.08f, g - s * 0.28f, s * 0.07f, 0xFFFFE08A.toInt(), 0xFFFFB84D.toInt())
        box(c, x + s * 0.1f, g - s * 0.5f, x + s * 0.38f, g - s * 0.28f, s * 0.07f, 0xFFFFC6DC.toInt(), 0xFFFF7EB6.toInt())
        fill.shader = null
        fill.color = Colors.alpha(Color.WHITE, 0.8f)
        Shapes.heart(path, x + s * 0.24f, g - s * 0.4f, s * 0.08f)
        c.drawPath(path, fill)
    }

    private fun bookshelf(c: Canvas, x: Float, g: Float, s: Float) {
        val l = x - s * 0.36f
        val r = x + s * 0.36f
        val t = g - s * 1.15f
        box(c, l, t, r, g, s * 0.03f, 0xFFE8B880.toInt(), 0xFF9A6A3A.toInt(), shadow = s * 0.02f)
        fill.shader = null
        fill.color = 0xFF7A4E26.toInt()
        c.drawRect(l + s * 0.05f, t + s * 0.05f, r - s * 0.05f, g - s * 0.05f, fill)
        val colors = intArrayOf(0xFFFF7EB6.toInt(), 0xFF7C6CFF.toInt(), 0xFF2EC4A0.toInt(), 0xFFFFC83D.toInt(), 0xFF4DA8FF.toInt(), 0xFFFF8A5C.toInt())
        val rows = 3
        val rowH = (g - t - s * 0.1f) / rows
        for (row in 0 until rows) {
            val shelfY = t + s * 0.05f + rowH * (row + 1)
            var bx = l + s * 0.07f
            var i = row * 2
            while (bx < r - s * 0.12f) {
                val bw = s * (0.06f + (i % 3) * 0.015f)
                val bh = rowH * (0.62f + (i % 4) * 0.08f)
                val col = colors[i % colors.size]
                if (row == 1 && i % 5 == 3) {
                    // a book leaning over
                    c.save()
                    c.rotate(-14f, bx, shelfY)
                    box(c, bx, shelfY - bh, bx + bw, shelfY, s * 0.008f, Colors.lighten(col, 0.2f), Colors.darken(col, 0.15f))
                    c.restore()
                    bx += bw * 1.4f
                } else {
                    box(c, bx, shelfY - bh, bx + bw, shelfY, s * 0.008f, Colors.lighten(col, 0.2f), Colors.darken(col, 0.15f))
                    bx += bw + s * 0.008f
                }
                i++
            }
            box(c, l + s * 0.04f, shelfY - s * 0.012f, r - s * 0.04f, shelfY + s * 0.025f, s * 0.006f, 0xFFE8B880.toInt(), 0xFFB07A44.toInt())
        }
    }

    private fun globe(c: Canvas, x: Float, g: Float, s: Float) {
        // stand
        box(c, x - s * 0.2f, g - s * 0.06f, x + s * 0.2f, g, s * 0.03f, 0xFFE8B880.toInt(), 0xFF9A6A3A.toInt())
        box(c, x - s * 0.03f, g - s * 0.32f, x + s * 0.03f, g - s * 0.05f, 0f, 0xFFE8B880.toInt(), 0xFF9A6A3A.toInt())
        val cy = g - s * 0.62f
        val r = s * 0.28f
        path.reset()
        path.addCircle(x, cy, r, Path.Direction.CW)
        shaded(c, path, 0xFFB8E4FF.toInt(), 0xFF4DA8FF.toInt(), 0xFF1E5AA8.toInt(), outlineW = s * 0.01f)
        // continents
        fill.shader = null
        fill.color = 0xFF5FCF7A.toInt()
        c.drawOval(x - r * 0.6f, cy - r * 0.5f, x - r * 0.05f, cy + r * 0.1f, fill)
        c.drawOval(x - r * 0.25f, cy + r * 0.05f, x + r * 0.15f, cy + r * 0.7f, fill)
        c.drawOval(x + r * 0.15f, cy - r * 0.6f, x + r * 0.7f, cy - r * 0.05f, fill)
        // meridian ring
        line.shader = null
        line.color = 0xFFC8A060.toInt()
        line.strokeWidth = s * 0.03f
        c.drawArc(x - r * 1.15f, cy - r * 1.15f, x + r * 1.15f, cy + r * 1.15f, 100f, 250f, false, line)
        fill.color = Colors.alpha(Color.WHITE, 0.55f)
        c.drawCircle(x - r * 0.4f, cy - r * 0.45f, r * 0.14f, fill)
    }

    private fun piano(c: Canvas, x: Float, g: Float, s: Float) {
        val l = x - s * 0.5f
        val r = x + s * 0.5f
        // body
        box(c, l, g - s * 0.95f, r, g - s * 0.4f, s * 0.04f, 0xFF4A3A60.toInt(), 0xFF1E1630.toInt(), shadow = s * 0.02f)
        // legs
        box(c, l + s * 0.04f, g - s * 0.42f, l + s * 0.12f, g, s * 0.01f, 0xFF3A2A50.toInt(), 0xFF1E1630.toInt())
        box(c, r - s * 0.12f, g - s * 0.42f, r - s * 0.04f, g, s * 0.01f, 0xFF3A2A50.toInt(), 0xFF1E1630.toInt())
        // key bed
        box(c, l - s * 0.04f, g - s * 0.5f, r + s * 0.04f, g - s * 0.38f, s * 0.02f, 0xFF5A4A70.toInt(), 0xFF2A2040.toInt())
        fill.shader = null
        fill.color = Color.WHITE
        val kt = g - s * 0.5f
        val kb = g - s * 0.44f
        c.drawRect(l + s * 0.02f, kt, r - s * 0.02f, kb, fill)
        line.shader = null
        line.color = 0xFFB8B0C8.toInt()
        line.strokeWidth = s * 0.006f
        val keys = 14
        val kw = (r - l - s * 0.04f) / keys
        for (k in 1 until keys) c.drawLine(l + s * 0.02f + k * kw, kt, l + s * 0.02f + k * kw, kb, line)
        fill.color = 0xFF1E1630.toInt()
        for (k in 1 until keys) {
            if (k % 7 == 3 || k % 7 == 0) continue
            val kx = l + s * 0.02f + k * kw
            c.drawRect(kx - kw * 0.28f, kt, kx + kw * 0.28f, kt + (kb - kt) * 0.6f, fill)
        }
        // sheet music and a little candle light
        box(c, x - s * 0.16f, g - s * 0.8f, x + s * 0.16f, g - s * 0.56f, s * 0.01f, Color.WHITE, 0xFFE8E4F0.toInt())
        line.color = 0xFF7C6CFF.toInt()
        line.strokeWidth = s * 0.008f
        for (k in 0 until 3) {
            val ly = g - s * (0.76f - k * 0.06f)
            c.drawLine(x - s * 0.12f, ly, x + s * 0.12f, ly, line)
        }
        fill.color = 0xFF7C6CFF.toInt()
        c.drawCircle(x - s * 0.05f, g - s * 0.71f, s * 0.018f, fill)
        c.drawCircle(x + s * 0.06f, g - s * 0.65f, s * 0.018f, fill)
        fill.color = 0xFFFFE08A.toInt()
        Shapes.softStar(path, r - s * 0.12f, g - s * 1.02f, s * 0.06f, s * 0.03f, 5, 0f)
        c.drawPath(path, fill)
    }

    private fun fireplace(c: Canvas, x: Float, g: Float, s: Float) {
        val l = x - s * 0.5f
        val r = x + s * 0.5f
        val t = g - s * 0.85f
        // stone frame
        box(c, l, t, r, g, s * 0.03f, 0xFFF0E6DA.toInt(), 0xFFC8B8A8.toInt(), shadow = s * 0.02f)
        line.shader = null
        line.color = Colors.alpha(0xFF9A8A7A.toInt(), 0.35f)
        line.strokeWidth = s * 0.008f
        var y = t + s * 0.14f
        var row = 0
        while (y < g) {
            c.drawLine(l, y, r, y, line)
            var bx = l + if (row % 2 == 0) s * 0.12f else s * 0.2f
            while (bx < r) {
                c.drawLine(bx, y - s * 0.12f, bx, y, line)
                bx += s * 0.24f
            }
            y += s * 0.12f
            row++
        }
        // opening
        path.reset()
        val ol = x - s * 0.3f
        val orr = x + s * 0.3f
        val ot = g - s * 0.55f
        path.moveTo(ol, g)
        path.lineTo(ol, ot + s * 0.12f)
        path.quadTo(x, ot - s * 0.1f, orr, ot + s * 0.12f)
        path.lineTo(orr, g)
        path.close()
        fill.shader = null
        fill.color = 0xFF2A1A20.toInt()
        c.drawPath(path, fill)
        shader(radial(x, g - s * 0.12f, s * 0.35f, intArrayOf(Colors.alpha(0xFFFFB84D.toInt(), 0.8f), Colors.alpha(0xFFFF6A3D.toInt(), 0f))))
        c.drawCircle(x, g - s * 0.12f, s * 0.35f, fill)
        fill.shader = null
        // logs
        box(c, x - s * 0.22f, g - s * 0.08f, x + s * 0.22f, g - s * 0.02f, s * 0.03f, 0xFFB07A44.toInt(), 0xFF6A4020.toInt())
        // flames
        for ((k, f) in floatArrayOf(-0.1f, 0.06f, 0f).withIndex()) {
            val fh = s * (0.26f + 0.08f * k)
            path.reset()
            val fx = x + s * f
            path.moveTo(fx - s * 0.08f, g - s * 0.08f)
            path.quadTo(fx - s * 0.1f, g - s * 0.08f - fh * 0.5f, fx, g - s * 0.08f - fh)
            path.quadTo(fx + s * 0.1f, g - s * 0.08f - fh * 0.5f, fx + s * 0.08f, g - s * 0.08f)
            path.close()
            fill.color = if (k == 2) 0xFFFFE08A.toInt() else 0xFFFF8A3D.toInt()
            c.drawPath(path, fill)
        }
        // mantel with a candle and a little frame
        box(c, l - s * 0.06f, t - s * 0.06f, r + s * 0.06f, t + s * 0.03f, s * 0.02f, 0xFFE8B880.toInt(), 0xFF9A6A3A.toInt(), shadow = s * 0.015f)
        box(c, l + s * 0.1f, t - s * 0.2f, l + s * 0.15f, t - s * 0.06f, s * 0.01f, Color.WHITE, 0xFFE8E0F0.toInt())
        fill.color = 0xFFFFC83D.toInt()
        c.drawOval(l + s * 0.105f, t - s * 0.27f, l + s * 0.145f, t - s * 0.2f, fill)
        box(c, r - s * 0.3f, t - s * 0.24f, r - s * 0.1f, t - s * 0.06f, s * 0.01f, 0xFFFFD86A.toInt(), 0xFFC08000.toInt())
        fill.color = 0xFFFFC6DC.toInt()
        c.drawRect(r - s * 0.27f, t - s * 0.21f, r - s * 0.13f, t - s * 0.09f, fill)
        fill.color = 0xFFFF7EB6.toInt()
        Shapes.heart(path, r - s * 0.2f, t - s * 0.15f, s * 0.06f)
        c.drawPath(path, fill)
    }

    private fun tent(c: Canvas, x: Float, g: Float, s: Float) {
        val half = s * 0.55f
        val top = g - s * 1.05f
        // body
        path.reset()
        path.moveTo(x - half, g)
        path.lineTo(x, top)
        path.lineTo(x + half, g)
        path.close()
        shaded(c, path, 0xFFFFF4E0.toInt(), 0xFFFFE0B8.toInt(), 0xFFD8A878.toInt(), outlineW = s * 0.012f)
        // stripes
        c.save()
        c.clipPath(path)
        fill.shader = null
        fill.color = Colors.alpha(0xFFFF7EB6.toInt(), 0.55f)
        var sx = x - half
        while (sx < x + half) {
            c.drawRect(sx, top, sx + s * 0.08f, g, fill)
            sx += s * 0.2f
        }
        c.restore()
        // door
        path.reset()
        path.moveTo(x - s * 0.2f, g)
        path.lineTo(x, g - s * 0.55f)
        path.lineTo(x + s * 0.2f, g)
        path.close()
        fill.color = 0xFF5A3A50.toInt()
        c.drawPath(path, fill)
        path.reset()
        path.moveTo(x, g - s * 0.55f)
        path.lineTo(x + s * 0.2f, g)
        path.lineTo(x + s * 0.3f, g)
        path.close()
        fill.color = 0xFFFFC6DC.toInt()
        c.drawPath(path, fill)
        // poles and pennant garland
        line.shader = null
        line.color = 0xFF9A6A3A.toInt()
        line.strokeWidth = s * 0.025f
        c.drawLine(x - s * 0.06f, top - s * 0.1f, x + s * 0.06f, top + s * 0.06f, line)
        c.drawLine(x + s * 0.06f, top - s * 0.1f, x - s * 0.06f, top + s * 0.06f, line)
        val colors = intArrayOf(0xFFFFC83D.toInt(), 0xFF4DA8FF.toInt(), 0xFF2EC4A0.toInt(), 0xFFFF7EB6.toInt())
        for (k in 0 until 4) {
            val f = (k + 0.5f) / 4f
            val px = x - half * 0.75f + half * 1.5f * f
            val py = top + s * 0.45f + s * 0.06f * (1f - (2f * f - 1f) * (2f * f - 1f))
            path.reset()
            path.moveTo(px - s * 0.05f, py)
            path.lineTo(px + s * 0.05f, py)
            path.lineTo(px, py + s * 0.1f)
            path.close()
            fill.color = colors[k]
            c.drawPath(path, fill)
        }
    }
}
