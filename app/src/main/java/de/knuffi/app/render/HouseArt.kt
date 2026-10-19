package de.knuffi.app.render

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import de.knuffi.core.Door
import de.knuffi.core.DoorSide
import de.knuffi.core.RoomKind
import de.knuffi.core.Slot
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/** Where the doors of a room are, in scene units (x across, z depth: 0 front, 1 back wall). */
/** Where the pet goes to walk through a door, in its own coordinates (x: screen fraction, z: depth). */
interface DoorPoints {
    /** The spot behind the door where the pet disappears. */
    fun outX(d: Door): Float
    fun outZ(d: Door): Float

    /** A spot inside the room in front of the door. */
    fun inX(d: Door): Float
    fun inZ(d: Door): Float
}

object DoorGeo : DoorPoints {
    private fun wideX(d: Door): Float = 0.5f + (d.pos - 0.5f) * 0.8f

    /** Screen x of a door in the back wall. */
    fun northX(d: Door): Float = if (d.from.cols == 1) 0.74f else wideX(d)

    fun southX(d: Door): Float = if (d.from.cols == 1) 0.5f else wideX(d)

    override fun outX(d: Door): Float = when (d.side) {
        DoorSide.WEST -> -0.14f
        DoorSide.EAST -> 1.14f
        DoorSide.NORTH -> northX(d)
        DoorSide.SOUTH -> southX(d)
    }

    override fun outZ(d: Door): Float = when (d.side) {
        DoorSide.WEST, DoorSide.EAST -> 0.55f
        DoorSide.NORTH -> 1.08f
        DoorSide.SOUTH -> -0.5f
    }

    override fun inX(d: Door): Float = when (d.side) {
        DoorSide.WEST -> 0.28f
        DoorSide.EAST -> 0.72f
        else -> outX(d).coerceIn(0.26f, 0.74f)
    }

    override fun inZ(d: Door): Float = when (d.side) {
        DoorSide.WEST, DoorSide.EAST -> 0.5f
        DoorSide.NORTH -> 0.62f
        DoorSide.SOUTH -> 0.12f
    }
}

/**
 * Everything the house adds to a room: doors with little signs, the fixtures of the kitchen,
 * bathroom, music room and so on, the garden outside and the frames around furniture
 * while it is being moved.
 */
class HouseArt : Painter() {
    private val emoji = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER }
    private val dash = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }

    /** Door areas from the last drawing (canvas coordinates), for taps. */
    val doorRects = mutableListOf<Pair<Door, RectF>>()

    /** Tappable things of the room ("piano", "cards", "kitchen", "beds", "tub"). */
    val fixtureRects = mutableListOf<Pair<String, RectF>>()

    // ------------------------------------------------------------------ doors

    fun doors(c: Canvas, L: SceneLayout, doors: List<Door>, outdoor: Boolean) {
        doorRects.clear()
        for (d in doors) {
            when (d.side) {
                DoorSide.NORTH -> northDoor(c, L, d, outdoor)
                DoorSide.WEST, DoorSide.EAST -> sideDoor(c, L, d, outdoor)
                DoorSide.SOUTH -> southDoor(c, L, d)
            }
        }
    }

    private fun northDoor(c: Canvas, L: SceneLayout, d: Door, outdoor: Boolean) {
        val w = L.w
        val h = L.h
        val cx = DoorGeo.northX(d) * w
        val bottom = L.floorY + h * 0.004f
        val top = if (outdoor) L.floorY - h * 0.22f else maxOf(L.window.top + L.window.height() * 0.62f, L.floorY - h * 0.23f)
        val hw = w * 0.1f
        val fw = w * 0.018f
        // frame
        box(c, cx - hw - fw, top - fw, cx + hw + fw, bottom, fw * 0.6f, 0xFFFFFFFF.toInt(), 0xFFE2D6E0.toInt(), shadow = h * 0.006f)
        // the door leaf with two panels and a round window
        val leafTop = if (outdoor) 0xFFB8E0FF.toInt() else 0xFFE8B888.toInt()
        val leafBottom = if (outdoor) 0xFF7FB0E0.toInt() else 0xFFB8783E.toInt()
        box(c, cx - hw, top, cx + hw, bottom - h * 0.002f, fw * 0.4f, leafTop, leafBottom)
        line.shader = null
        line.color = Colors.alpha(0xFF6A3E1A.toInt(), 0.35f)
        line.strokeWidth = maxOf(1f, w * 0.003f)
        val ph = (bottom - top)
        c.drawRoundRect(cx - hw * 0.72f, top + ph * 0.42f, cx + hw * 0.72f, bottom - ph * 0.08f, fw * 0.4f, fw * 0.4f, line)
        fill.shader = null
        fill.color = 0xFFCFEFFF.toInt()
        c.drawCircle(cx, top + ph * 0.22f, hw * 0.42f, fill)
        line.color = 0xFFFFFFFF.toInt()
        line.strokeWidth = maxOf(1f, w * 0.005f)
        c.drawCircle(cx, top + ph * 0.22f, hw * 0.42f, line)
        fill.color = 0xFFFFD86A.toInt()
        c.drawCircle(cx + hw * 0.7f, top + ph * 0.6f, w * 0.008f, fill)
        sign(c, cx, top - fw * 2.6f, w, d.to)
        doorRects += d to RectF(cx - hw - fw * 2f, top - fw * 5f, cx + hw + fw * 2f, bottom + h * 0.02f)
    }

    private fun sideDoor(c: Canvas, L: SceneLayout, d: Door, outdoor: Boolean) {
        val w = L.w
        val h = L.h
        val base = L.groundY + (L.backGroundY - L.groundY) * 0.55f
        val ps = L.petSize
        val west = d.side == DoorSide.WEST
        val outer = if (west) 0f else w
        val inner = if (west) w * 0.09f else w - w * 0.09f
        val outerTop = base - ps * 1.32f
        val innerTop = base - ps * 1.12f
        // frame
        path.reset()
        path.moveTo(outer, outerTop - h * 0.012f)
        path.lineTo(inner + (if (west) w * 0.012f else -w * 0.012f), innerTop - h * 0.012f)
        path.lineTo(inner + (if (west) w * 0.012f else -w * 0.012f), base + h * 0.004f)
        path.lineTo(outer, base + h * 0.03f)
        path.close()
        fill.shader = null
        fill.color = 0xFFF6EEF4.toInt()
        c.drawPath(path, fill)
        // leaf (outside: a garden gate)
        path.reset()
        path.moveTo(outer, outerTop)
        path.lineTo(inner, innerTop)
        path.lineTo(inner, base)
        path.lineTo(outer, base + h * 0.024f)
        path.close()
        if (outdoor) {
            shader(LinearGradient(0f, outerTop, 0f, base, 0xFFE8C898.toInt(), 0xFFB8885A.toInt(), Shader.TileMode.CLAMP))
        } else {
            shader(LinearGradient(outer, 0f, inner, 0f, 0xFFB8783E.toInt(), 0xFFE8B888.toInt(), Shader.TileMode.CLAMP))
        }
        c.drawPath(path, fill)
        fill.shader = null
        line.shader = null
        line.color = Colors.alpha(0xFF6A3E1A.toInt(), 0.4f)
        line.strokeWidth = maxOf(1f, w * 0.003f)
        c.drawPath(path, line)
        val mx = (outer + inner) / 2f
        if (outdoor) {
            for (k in 1..3) {
                val lx = outer + (inner - outer) * k / 4f
                c.drawLine(lx, outerTop + (innerTop - outerTop) * k / 4f + h * 0.02f, lx, base + h * 0.02f * (1f - k / 4f), line)
            }
        } else {
            fill.color = 0xFFFFD86A.toInt()
            c.drawCircle(inner + (if (west) -w * 0.016f else w * 0.016f), (innerTop + base) / 2f + h * 0.02f, w * 0.008f, fill)
        }
        sign(c, mx + (if (west) w * 0.035f else -w * 0.035f), (outerTop + innerTop) / 2f + h * 0.02f, w, d.to)
        val l = if (west) 0f else inner - w * 0.04f
        val r = if (west) inner + w * 0.04f else w
        doorRects += d to RectF(l, outerTop - h * 0.04f, r, base + h * 0.05f)
    }

    private fun southDoor(c: Canvas, L: SceneLayout, d: Door) {
        val w = L.w
        val h = L.h
        val cx = DoorGeo.southX(d) * w
        val cy = L.groundY + h * 0.075f
        val rx = w * 0.13f
        val ry = h * 0.022f
        ellipseShadow(c, cx, cy + ry * 0.3f, rx * 1.05f, ry * 1.3f, 0.22f)
        path.reset()
        path.addRoundRect(cx - rx, cy - ry, cx + rx, cy + ry, ry, ry, Path.Direction.CW)
        shaded(c, path, 0xFFD8B890.toInt(), 0xFFB08858.toInt(), 0xFF7A5428.toInt(), outlineW = w * 0.003f)
        line.shader = null
        line.color = Colors.alpha(0xFF5A3A18.toInt(), 0.35f)
        line.strokeWidth = maxOf(1f, w * 0.003f)
        var x = cx - rx * 0.8f
        while (x < cx + rx * 0.8f) {
            c.drawLine(x, cy - ry * 0.6f, x, cy + ry * 0.6f, line)
            x += rx * 0.16f
        }
        emoji.textSize = h * 0.03f
        emoji.color = Color.BLACK
        c.drawText("⬇ ${d.to.emoji}", cx, cy + h * 0.01f, emoji)
        doorRects += d to RectF(cx - rx * 1.2f, cy - ry * 3f, cx + rx * 1.2f, cy + ry * 2.5f)
    }

    /** A little round sign with the emoji of the room behind the door. */
    private fun sign(c: Canvas, x: Float, y: Float, w: Float, to: RoomKind) {
        val r = w * 0.042f
        fill.shader = null
        fill.color = Colors.alpha(0xFF2A1740.toInt(), 0.18f)
        c.drawCircle(x, y + r * 0.12f, r, fill)
        path.reset()
        path.addCircle(x, y, r, Path.Direction.CW)
        shaded(c, path, 0xFFFFFFFF.toInt(), 0xFFFFF2F8.toInt(), 0xFFE0C8D8.toInt(), outlineW = w * 0.003f)
        emoji.textSize = r * 1.15f
        emoji.color = Color.BLACK
        c.drawText(to.emoji, x, y + r * 0.4f, emoji)
    }

    // ------------------------------------------------------------------ fixtures

    /** The things that make a room a kitchen, a bathroom … (drawn in front of the wall). */
    fun fixtures(c: Canvas, L: SceneLayout, kind: RoomKind) {
        fixtureRects.clear()
        when (kind) {
            RoomKind.KITCHEN -> kitchen(c, L)
            RoomKind.BATH -> bathroom(c, L)
            RoomKind.BEDROOM -> bedroom(c, L)
            RoomKind.PLAYROOM -> playroom(c, L)
            RoomKind.MUSIC -> musicRoom(c, L)
            RoomKind.CRAFT -> craftRoom(c, L)
            RoomKind.LIVING, RoomKind.GARDEN -> Unit
        }
    }

    private fun kitchen(c: Canvas, L: SceneLayout) {
        val w = L.w
        val h = L.h
        val fy = L.floorY
        val l = w * 0.03f
        val r = w * 0.47f
        val top = fy - h * 0.095f
        // cupboards
        box(c, l, top, r, fy + h * 0.004f, w * 0.008f, 0xFFFFFFFF.toInt(), 0xFFE6DCE6.toInt(), shadow = h * 0.006f)
        line.shader = null
        line.color = Colors.alpha(0xFFB8A8B8.toInt(), 0.7f)
        line.strokeWidth = maxOf(1f, w * 0.003f)
        val doors = 4
        for (k in 1 until doors) {
            val x = l + (r - l) * k / doors
            c.drawLine(x, top + h * 0.016f, x, fy - h * 0.004f, line)
        }
        fill.shader = null
        fill.color = 0xFFB8A8C8.toInt()
        for (k in 0 until doors) {
            val x = l + (r - l) * (k + 0.5f) / doors
            c.drawRoundRect(x - w * 0.012f, top + h * 0.03f, x + w * 0.012f, top + h * 0.036f, w * 0.004f, w * 0.004f, fill)
        }
        // worktop with a sink and a pot on the stove
        box(c, l - w * 0.008f, top - h * 0.012f, r + w * 0.008f, top + h * 0.004f, w * 0.004f, 0xFFE9B384.toInt(), 0xFFB9774A.toInt())
        val sx = l + (r - l) * 0.62f
        fill.color = 0xFFB8C8D8.toInt()
        c.drawRoundRect(sx - w * 0.05f, top - h * 0.01f, sx + w * 0.05f, top - h * 0.002f, w * 0.006f, w * 0.006f, fill)
        line.color = 0xFFA8B8C8.toInt()
        line.strokeWidth = w * 0.008f
        path.reset()
        path.moveTo(sx + w * 0.03f, top - h * 0.012f)
        path.lineTo(sx + w * 0.03f, top - h * 0.04f)
        path.quadTo(sx + w * 0.03f, top - h * 0.05f, sx + w * 0.01f, top - h * 0.048f)
        c.drawPath(path, line)
        val px = l + (r - l) * 0.2f
        box(c, px - w * 0.045f, top - h * 0.045f, px + w * 0.045f, top - h * 0.012f, w * 0.01f, 0xFFFF8A8A.toInt(), 0xFFD04A4A.toInt())
        box(c, px - w * 0.05f, top - h * 0.05f, px + w * 0.05f, top - h * 0.042f, w * 0.004f, 0xFFFFB0B0.toInt(), 0xFFD06060.toInt())
        // the fridge on the right
        val fl = w * 0.885f
        val fr = w * 0.985f
        val ft = fy - h * 0.34f
        box(c, fl, ft, fr, fy + h * 0.004f, w * 0.014f, 0xFFF4FAFF.toInt(), 0xFFC8D8E8.toInt(), shadow = h * 0.008f)
        line.color = Colors.alpha(0xFF8898A8.toInt(), 0.6f)
        line.strokeWidth = maxOf(1f, w * 0.003f)
        c.drawLine(fl, ft + (fy - ft) * 0.36f, fr, ft + (fy - ft) * 0.36f, line)
        fill.color = 0xFF9AA8B8.toInt()
        c.drawRoundRect(fl + w * 0.012f, ft + h * 0.03f, fl + w * 0.02f, ft + h * 0.08f, w * 0.004f, w * 0.004f, fill)
        c.drawRoundRect(fl + w * 0.012f, ft + (fy - ft) * 0.42f, fl + w * 0.02f, ft + (fy - ft) * 0.42f + h * 0.06f, w * 0.004f, w * 0.004f, fill)
        emoji.textSize = w * 0.035f
        emoji.color = Color.BLACK
        c.drawText("🧲", (fl + fr) / 2f + w * 0.01f, ft + h * 0.06f, emoji)
        fixtureRects += "kitchen" to RectF(l, top - h * 0.06f, r, fy)
        fixtureRects += "kitchen" to RectF(fl, ft, fr, fy)
    }

    private fun bathroom(c: Canvas, L: SceneLayout) {
        val w = L.w
        val h = L.h
        val fy = L.floorY
        val by = L.backGroundY
        // tiles on the lower wall
        fill.shader = null
        fill.color = Colors.alpha(0xFFFFFFFF.toInt(), 0.55f)
        c.drawRect(0f, fy - h * 0.12f, w, fy, fill)
        line.shader = null
        line.color = Colors.alpha(0xFF9AC8E0.toInt(), 0.45f)
        line.strokeWidth = maxOf(1f, w * 0.002f)
        var tx = 0f
        while (tx < w) {
            c.drawLine(tx, fy - h * 0.12f, tx, fy, line)
            tx += w * 0.06f
        }
        var ty = fy - h * 0.12f
        while (ty < fy) {
            c.drawLine(0f, ty, w, ty, line)
            ty += h * 0.03f
        }
        // sink with a mirror
        val sx = w * 0.5f
        box(c, sx - w * 0.06f, fy - h * 0.2f, sx + w * 0.06f, fy - h * 0.13f, w * 0.03f, 0xFFDFF4FF.toInt(), 0xFF9AC8E8.toInt(), shadow = h * 0.004f)
        box(c, sx - w * 0.055f, fy - h * 0.1f, sx + w * 0.055f, fy - h * 0.085f, w * 0.008f, 0xFFFFFFFF.toInt(), 0xFFDCE8F0.toInt())
        box(c, sx - w * 0.012f, fy - h * 0.085f, sx + w * 0.012f, fy, 0f, 0xFFFFFFFF.toInt(), 0xFFDCE8F0.toInt())
        // the bathtub with feet and bubbles
        val l = w * 0.58f
        val r = w * 0.97f
        val top = by - h * 0.105f
        fill.color = 0xFFD8B04A.toInt()
        for (fx in floatArrayOf(l + w * 0.04f, r - w * 0.04f)) c.drawCircle(fx, by - h * 0.004f, w * 0.012f, fill)
        path.reset()
        path.moveTo(l, top)
        path.lineTo(r, top)
        path.quadTo(r, by - h * 0.01f, (l + r) / 2f, by - h * 0.012f)
        path.quadTo(l, by - h * 0.01f, l, top)
        path.close()
        shaded(c, path, 0xFFFFFFFF.toInt(), 0xFFEAF2F8.toInt(), 0xFFB8C8D8.toInt(), outlineW = w * 0.003f)
        box(c, l - w * 0.01f, top - h * 0.008f, r + w * 0.01f, top + h * 0.006f, w * 0.006f, 0xFFFFFFFF.toInt(), 0xFFDCE8F0.toInt())
        fill.color = Colors.alpha(Color.WHITE, 0.95f)
        for (k in 0 until 6) {
            val bx = l + (r - l) * (0.15f + k * 0.14f)
            c.drawCircle(bx, top - h * 0.008f - (k % 2) * h * 0.008f, w * (0.022f + (k % 3) * 0.006f), fill)
        }
        emoji.textSize = w * 0.05f
        emoji.color = Color.BLACK
        c.drawText("🦆", l + (r - l) * 0.7f, top - h * 0.012f, emoji)
        fixtureRects += "tub" to RectF(l, top - h * 0.05f, r, by)
    }

    private fun bedroom(c: Canvas, L: SceneLayout) {
        val w = L.w
        val h = L.h
        val by = L.backGroundY
        // night stand with a little lamp
        val nx = w * 0.57f
        box(c, nx - w * 0.045f, by - h * 0.07f, nx + w * 0.045f, by, w * 0.008f, 0xFFE9B384.toInt(), 0xFFB9774A.toInt(), shadow = h * 0.006f)
        line.shader = null
        line.color = Colors.alpha(0xFF7A4A20.toInt(), 0.4f)
        line.strokeWidth = maxOf(1f, w * 0.002f)
        c.drawLine(nx - w * 0.04f, by - h * 0.035f, nx + w * 0.04f, by - h * 0.035f, line)
        box(c, nx - w * 0.006f, by - h * 0.1f, nx + w * 0.006f, by - h * 0.07f, 0f, 0xFFFFE6A8.toInt(), 0xFFD8B060.toInt())
        path.reset()
        path.moveTo(nx - w * 0.03f, by - h * 0.1f)
        path.lineTo(nx + w * 0.03f, by - h * 0.1f)
        path.lineTo(nx + w * 0.018f, by - h * 0.13f)
        path.lineTo(nx - w * 0.018f, by - h * 0.13f)
        path.close()
        shaded(c, path, 0xFFFFF4C2.toInt(), 0xFFFFC2DA.toInt(), 0xFFD87AA0.toInt(), outlineW = w * 0.002f)
        // a garland of stars and moons on the wall
        starGarland(c, L)
    }

    private fun starGarland(c: Canvas, L: SceneLayout) {
        val w = L.w
        val y0 = L.window.top - L.h * 0.035f
        line.shader = null
        line.color = Colors.alpha(0xFF8A7AA8.toInt(), 0.6f)
        line.strokeWidth = maxOf(1f, w * 0.002f)
        path.reset()
        path.moveTo(w * 0.04f, y0)
        path.quadTo(w * 0.5f, y0 + L.h * 0.05f, w * 0.96f, y0)
        c.drawPath(path, line)
        emoji.textSize = w * 0.04f
        emoji.color = Color.BLACK
        for (k in 0 until 7) {
            val p = (k + 0.5f) / 7f
            val x = w * (0.04f + 0.92f * p)
            val y = y0 + L.h * 0.05f * 4f * p * (1f - p) * 0.5f + w * 0.02f
            c.drawText(if (k % 2 == 0) "⭐" else "🌙", x, y + w * 0.012f, emoji)
        }
    }

    private fun playroom(c: Canvas, L: SceneLayout) {
        val w = L.w
        val h = L.h
        val fy = L.floorY
        // bunting
        val y0 = L.window.top - h * 0.03f
        val colors = intArrayOf(0xFFFF6B9A.toInt(), 0xFFFFD166.toInt(), 0xFF6EC1FF.toInt(), 0xFF7DD9B0.toInt(), 0xFFB39DFF.toInt())
        line.shader = null
        line.color = Colors.alpha(0xFF8A7AA8.toInt(), 0.6f)
        line.strokeWidth = maxOf(1f, w * 0.002f)
        c.drawLine(w * 0.02f, y0, w * 0.98f, y0, line)
        fill.shader = null
        for (k in 0 until 12) {
            val x = w * (0.04f + k * 0.08f)
            path.reset()
            path.moveTo(x - w * 0.025f, y0)
            path.lineTo(x + w * 0.025f, y0)
            path.lineTo(x, y0 + h * 0.035f)
            path.close()
            fill.color = colors[k % colors.size]
            c.drawPath(path, fill)
        }
        // low shelf full of building blocks
        val l = w * 0.04f
        val r = w * 0.44f
        val top = fy - h * 0.08f
        box(c, l, top, r, fy + h * 0.004f, w * 0.008f, 0xFFFFF4E0.toInt(), 0xFFE8D0A8.toInt(), shadow = h * 0.006f)
        box(c, l, top + (fy - top) * 0.48f, r, top + (fy - top) * 0.52f, 0f, 0xFFE8D0A8.toInt(), 0xFFC8A878.toInt())
        for (k in 0 until 9) {
            val bx = l + w * 0.02f + k * w * 0.042f
            val row = k % 2
            val by = if (row == 0) top + (fy - top) * 0.46f else fy - h * 0.004f
            val s = w * 0.03f
            box(c, bx, by - s, bx + s, by, w * 0.004f, Colors.lighten(colors[k % colors.size], 0.2f), colors[k % colors.size])
        }
        // a tower of blocks on top
        for (k in 0 until 3) {
            val s = w * (0.04f - k * 0.006f)
            val bx = w * 0.36f
            val by = top - k * s
            box(c, bx - s / 2f, by - s, bx + s / 2f, by, w * 0.004f, Colors.lighten(colors[(k + 2) % colors.size], 0.2f), colors[(k + 2) % colors.size])
        }
        emoji.textSize = w * 0.06f
        emoji.color = Color.BLACK
        c.drawText("🧸", w * 0.12f, top - h * 0.004f, emoji)
    }

    private fun musicRoom(c: Canvas, L: SceneLayout) {
        val w = L.w
        val h = L.h
        val by = L.backGroundY
        // the piano
        val l = w * 0.56f
        val r = w * 0.93f
        val top = by - h * 0.19f
        box(c, l, top, r, by, w * 0.012f, 0xFF4A3A5E.toInt(), 0xFF2A1E38.toInt(), shadow = h * 0.008f)
        box(c, l - w * 0.012f, top + (by - top) * 0.42f, r + w * 0.012f, top + (by - top) * 0.52f, w * 0.006f, 0xFF5A4A70.toInt(), 0xFF3A2A4E.toInt())
        // keys
        val kt = top + (by - top) * 0.44f
        val kb = top + (by - top) * 0.52f
        fill.shader = null
        fill.color = Color.WHITE
        c.drawRect(l + w * 0.01f, kt, r - w * 0.01f, kb, fill)
        val n = 14
        val kw = (r - l - w * 0.02f) / n
        line.shader = null
        line.color = Colors.alpha(0xFF2A1E38.toInt(), 0.5f)
        line.strokeWidth = maxOf(1f, w * 0.002f)
        fill.color = 0xFF1E1428.toInt()
        for (k in 1 until n) {
            val x = l + w * 0.01f + k * kw
            c.drawLine(x, kt, x, kb, line)
            if (k % 7 != 3 && k % 7 != 0) c.drawRect(x - kw * 0.3f, kt, x + kw * 0.3f, kt + (kb - kt) * 0.6f, fill)
        }
        // music sheet and candles
        box(c, (l + r) / 2f - w * 0.06f, top + h * 0.012f, (l + r) / 2f + w * 0.06f, top + h * 0.06f, w * 0.004f, 0xFFFFFFFF.toInt(), 0xFFF0E8E0.toInt())
        emoji.textSize = w * 0.03f
        emoji.color = Color.BLACK
        c.drawText("🎵🎶", (l + r) / 2f, top + h * 0.045f, emoji)
        // a drum next to it
        val dx = w * 0.47f
        path.reset()
        path.addRoundRect(dx - w * 0.05f, by - h * 0.07f, dx + w * 0.05f, by, w * 0.02f, w * 0.02f, Path.Direction.CW)
        shaded(c, path, 0xFFFF9AB8.toInt(), 0xFFE0406A.toInt(), 0xFF902040.toInt(), outlineW = w * 0.002f)
        fill.color = 0xFFF4EEE8.toInt()
        c.drawOval(dx - w * 0.05f, by - h * 0.078f, dx + w * 0.05f, by - h * 0.062f, fill)
        fixtureRects += "piano" to RectF(l, top, r, by)
        fixtureRects += "piano" to RectF(dx - w * 0.06f, by - h * 0.09f, dx + w * 0.06f, by)
    }

    private fun craftRoom(c: Canvas, L: SceneLayout) {
        val w = L.w
        val h = L.h
        val fy = L.floorY
        // pinboard with cards
        val pl = w * 0.05f
        val pr = w * 0.42f
        val pt = fy - h * 0.2f
        val pb = fy - h * 0.12f
        box(c, pl, pt, pr, pb, w * 0.008f, 0xFFE8C090.toInt(), 0xFFC89860.toInt(), shadow = h * 0.006f)
        val cardColors = intArrayOf(0xFFFFB0C8.toInt(), 0xFFB8D8FF.toInt(), 0xFFFFE08A.toInt(), 0xFFC8B0FF.toInt(), 0xFFA8E8C8.toInt())
        for (k in 0 until 5) {
            val cx = pl + (pr - pl) * (0.12f + k * 0.19f)
            val cy = (pt + pb) / 2f + (if (k % 2 == 0) -h * 0.005f else h * 0.006f)
            c.save()
            c.rotate((k - 2) * 6f, cx, cy)
            box(c, cx - w * 0.026f, cy - h * 0.026f, cx + w * 0.026f, cy + h * 0.026f, w * 0.005f, Color.WHITE, cardColors[k])
            fill.shader = null
            fill.color = 0xFFFF5A6E.toInt()
            c.drawCircle(cx, cy - h * 0.022f, w * 0.006f, fill)
            c.restore()
        }
        // the card table
        val tl = w * 0.04f
        val tr = w * 0.46f
        val tt = fy - h * 0.06f
        box(c, tl + w * 0.02f, tt, tl + w * 0.035f, fy + h * 0.004f, 0f, 0xFFB9774A.toInt(), 0xFF7A4A20.toInt())
        box(c, tr - w * 0.035f, tt, tr - w * 0.02f, fy + h * 0.004f, 0f, 0xFFB9774A.toInt(), 0xFF7A4A20.toInt())
        box(c, tl, tt - h * 0.012f, tr, tt + h * 0.004f, w * 0.004f, 0xFFE9B384.toInt(), 0xFFB9774A.toInt(), shadow = h * 0.004f)
        emoji.textSize = w * 0.045f
        emoji.color = Color.BLACK
        c.drawText("🃏✂️🎴", (tl + tr) / 2f, tt - h * 0.016f, emoji)
        fixtureRects += "cards" to RectF(tl, pt, tr, fy)
    }

    // ------------------------------------------------------------------ garden

    /** The garden: the back of the house with its doors, grass, a swing, a pool and flower beds. */
    fun gardenStatic(c: Canvas, L: SceneLayout, season: de.knuffi.core.Season? = null) {
        fixtureRects.clear()
        val w = L.w
        val h = L.h
        val fy = L.floorY
        val wallTop = fy - h * 0.34f
        val winter = season == de.knuffi.core.Season.WINTER
        val autumn = season == de.knuffi.core.Season.AUTUMN
        val leaves = when {
            winter -> intArrayOf(0xFFFFFFFF.toInt(), 0xFFE0E8F4.toInt(), 0xFF9AA8C0.toInt())
            autumn -> intArrayOf(0xFFFFD08A.toInt(), 0xFFE8883A.toInt(), 0xFFA8501E.toInt())
            else -> intArrayOf(0xFFA8E0A0.toInt(), 0xFF6ABF6A.toInt(), 0xFF3A8A4A.toInt())
        }
        val grassTop = when (season) {
            de.knuffi.core.Season.WINTER -> 0xFFF6FAFF.toInt()
            de.knuffi.core.Season.AUTUMN -> 0xFFC8D07A.toInt()
            de.knuffi.core.Season.SUMMER -> 0xFFB8E46A.toInt()
            else -> 0xFFA8E07A.toInt()
        }
        val grassBottom = when (season) {
            de.knuffi.core.Season.WINTER -> 0xFFC8D8EA.toInt()
            de.knuffi.core.Season.AUTUMN -> 0xFF8A9A4A.toInt()
            de.knuffi.core.Season.SUMMER -> 0xFF6AAE3A.toInt()
            else -> 0xFF5AAE4A.toInt()
        }
        val flowers = when (season) {
            de.knuffi.core.Season.WINTER -> listOf("❄️", "⛄", "❄️", "🌲", "❄️")
            de.knuffi.core.Season.AUTUMN -> listOf("🍂", "🍁", "🎃", "🍄", "🍂")
            de.knuffi.core.Season.SUMMER -> listOf("🌻", "🌼", "🌱", "🌻", "🌿")
            else -> listOf("🌷", "🌼", "🌱", "🌸", "🌿")
        }
        // distant trees behind the house
        for (k in 0 until 6) {
            val tx = w * (0.05f + k * 0.18f)
            path.reset()
            path.addCircle(tx, wallTop - h * 0.02f, w * 0.09f, Path.Direction.CW)
            shaded(c, path, leaves[0], leaves[1], leaves[2], outline = false)
        }
        // the house wall with a roof edge
        vgrad(c, 0f, wallTop, w, fy, 0xFFFFF2D8.toInt(), 0xFFF2DCB8.toInt())
        line.shader = null
        line.color = Colors.alpha(0xFFC8A878.toInt(), 0.35f)
        line.strokeWidth = maxOf(1f, w * 0.002f)
        var y = wallTop + h * 0.03f
        while (y < fy) {
            c.drawLine(0f, y, w, y, line)
            y += h * 0.028f
        }
        box(c, -w * 0.02f, wallTop - h * 0.035f, w * 1.02f, wallTop + h * 0.008f, w * 0.01f, 0xFFE8806A.toInt(), 0xFFB8503E.toInt(), shadow = h * 0.008f)
        if (winter) box(c, -w * 0.02f, wallTop - h * 0.045f, w * 1.02f, wallTop - h * 0.026f, w * 0.01f, 0xFFFFFFFF.toInt(), 0xFFE0EAF6.toInt())
        // windows between the doors
        for (wx in floatArrayOf(0.5f, 0.1f, 0.9f)) {
            val cx = w * wx
            val top = wallTop + h * 0.07f
            box(c, cx - w * 0.06f, top, cx + w * 0.06f, top + h * 0.1f, w * 0.008f, 0xFFFFFFFF.toInt(), 0xFFE8DDE6.toInt(), shadow = h * 0.004f)
            box(c, cx - w * 0.048f, top + h * 0.008f, cx + w * 0.048f, top + h * 0.092f, w * 0.004f, 0xFFCFEFFF.toInt(), 0xFF9ACBEE.toInt())
            line.color = Color.WHITE
            line.strokeWidth = w * 0.006f
            c.drawLine(cx, top + h * 0.008f, cx, top + h * 0.092f, line)
            box(c, cx - w * 0.07f, top + h * 0.1f, cx + w * 0.07f, top + h * 0.112f, w * 0.004f, 0xFFE8806A.toInt(), 0xFFB8503E.toInt())
            emoji.textSize = w * 0.035f
            emoji.color = Color.BLACK
            c.drawText("🌸🌼🌸", cx, top + h * 0.108f, emoji)
        }
        // lawn
        vgrad(c, 0f, fy, w, h, grassTop, grassBottom)
        fill.shader = null
        fill.color = Colors.alpha(Color.WHITE, 0.08f)
        for (k in 0 until 6) {
            val x0 = k * w / 6f
            if (k % 2 == 0) {
                path.reset()
                path.moveTo(L.vpX + (x0 - L.vpX) * ((fy - L.vpY) / (h - L.vpY)), fy)
                path.lineTo(L.vpX + (x0 + w / 6f - L.vpX) * ((fy - L.vpY) / (h - L.vpY)), fy)
                path.lineTo(x0 + w / 6f, h)
                path.lineTo(x0, h)
                path.close()
                c.drawPath(path, fill)
            }
        }
        // the flower border along the house
        box(c, 0f, fy - h * 0.004f, w, fy + h * 0.02f, 0f, 0xFF9A6A42.toInt(), 0xFF7A4A28.toInt())
        emoji.textSize = w * 0.04f
        var fx = w * 0.03f
        var k = 0
        while (fx < w) {
            c.drawText(flowers[k % 5], fx, fy + h * 0.012f, emoji)
            fx += w * 0.075f
            k++
        }
        // the swing (left) and a tree (right)
        val sx = w * 0.14f
        val st = fy - h * 0.06f
        line.color = 0xFF9A6A42.toInt()
        line.strokeWidth = w * 0.014f
        c.drawLine(sx - w * 0.09f, L.backGroundY, sx - w * 0.06f, st, line)
        c.drawLine(sx + w * 0.09f, L.backGroundY, sx + w * 0.06f, st, line)
        c.drawLine(sx - w * 0.075f, st, sx + w * 0.075f, st, line)
        val tx = w * 0.9f
        box(c, tx - w * 0.025f, fy - h * 0.12f, tx + w * 0.025f, L.backGroundY, w * 0.01f, 0xFFB8885A.toInt(), 0xFF7A5428.toInt())
        path.reset()
        path.addCircle(tx, fy - h * 0.16f, w * 0.1f, Path.Direction.CW)
        path.addCircle(tx - w * 0.07f, fy - h * 0.12f, w * 0.07f, Path.Direction.CW)
        path.addCircle(tx + w * 0.06f, fy - h * 0.11f, w * 0.07f, Path.Direction.CW)
        shaded(c, path, leaves[0], leaves[1], leaves[2], outlineW = w * 0.003f)
        if (!winter) {
            fill.color = if (autumn) 0xFFE8503E.toInt() else 0xFFFF5A6E.toInt()
            for ((ax, ay) in listOf(-0.05f to -0.15f, 0.03f to -0.18f, 0.07f to -0.11f, -0.08f to -0.1f)) c.drawCircle(tx + w * ax, fy + h * ay, w * 0.012f, fill)
        }
        // the paddling pool
        val px = w * 0.72f
        val py = L.groundY + h * 0.045f
        ellipseShadow(c, px, py + h * 0.008f, w * 0.19f, h * 0.03f, 0.25f)
        path.reset()
        path.addOval(px - w * 0.18f, py - h * 0.028f, px + w * 0.18f, py + h * 0.028f, Path.Direction.CW)
        shaded(c, path, 0xFFFFC2DA.toInt(), 0xFFFF7EB6.toInt(), 0xFFB8306E.toInt(), outlineW = w * 0.003f)
        fill.color = 0xFF7FD8FF.toInt()
        c.drawOval(px - w * 0.15f, py - h * 0.02f, px + w * 0.15f, py + h * 0.018f, fill)
        // the vegetable beds (front left)
        for (i in 0 until 4) {
            val r = bedRect(L, i)
            path.reset()
            path.addRoundRect(r, w * 0.012f, w * 0.012f, Path.Direction.CW)
            shaded(c, path, 0xFFB8885A.toInt(), 0xFF8A5A30.toInt(), 0xFF5A3418.toInt(), outlineW = w * 0.002f)
            fill.color = 0xFF6A4224.toInt()
            c.drawRoundRect(r.left + w * 0.008f, r.top + h * 0.004f, r.right - w * 0.008f, r.bottom - h * 0.006f, w * 0.008f, w * 0.008f, fill)
        }
        fixtureRects += "beds" to RectF(bedRect(L, 0).left, bedRect(L, 0).top - h * 0.06f, bedRect(L, 3).right, bedRect(L, 3).bottom)
        fixtureRects += "pool" to RectF(px - w * 0.18f, py - h * 0.04f, px + w * 0.18f, py + h * 0.03f)
    }

    private fun bedRect(L: SceneLayout, i: Int): RectF {
        val w = L.w
        val h = L.h
        val row = i / 2
        val col = i % 2
        val top = L.groundY + h * (0.02f + row * 0.05f)
        val left = w * (0.03f + col * 0.17f)
        return RectF(left, top, left + w * 0.15f, top + h * 0.035f)
    }

    /** Swing and plants move, the pool sparkles. [plants] per bed: "" or "emoji|growth 0..1|ready". */
    fun gardenAnimated(c: Canvas, L: SceneLayout, t: Float, plants: List<String>) {
        val w = L.w
        val h = L.h
        val fy = L.floorY
        // swing seat
        val sx = w * 0.14f
        val st = fy - h * 0.06f
        val swing = sin(t * 1.6f) * 0.25f
        val len = (L.backGroundY - st) * 0.82f
        val seatX = sx + sin(swing) * len
        val seatY = st + cos(swing) * len
        line.shader = null
        line.color = 0xFF7A5428.toInt()
        line.strokeWidth = maxOf(1f, w * 0.004f)
        c.drawLine(sx - w * 0.04f, st, seatX - w * 0.04f, seatY, line)
        c.drawLine(sx + w * 0.04f, st, seatX + w * 0.04f, seatY, line)
        box(c, seatX - w * 0.05f, seatY - h * 0.006f, seatX + w * 0.05f, seatY + h * 0.006f, w * 0.004f, 0xFFFF9AB8.toInt(), 0xFFD04A7A.toInt())
        // pool sparkles
        val px = w * 0.72f
        val py = L.groundY + h * 0.045f
        fill.shader = null
        for (k in 0 until 4) {
            val a = (sin(t * 2.4f + k * 1.7f) + 1f) / 2f
            fill.color = Colors.alpha(Color.WHITE, 0.6f * a)
            c.drawOval(px - w * 0.12f + k * w * 0.07f, py - h * 0.008f, px - w * 0.09f + k * w * 0.07f, py, fill)
        }
        // plants in the beds
        for ((i, info) in plants.take(4).withIndex()) {
            if (info.isEmpty()) continue
            val parts = info.split('|')
            val icon = parts.getOrNull(0) ?: continue
            val grow = parts.getOrNull(1)?.toFloatOrNull() ?: 0f
            val ready = parts.getOrNull(2) == "1"
            val r = bedRect(L, i)
            val sway = sin(t * 2f + i) * 4f
            for (k in 0 until 3) {
                val x = r.left + r.width() * (0.2f + k * 0.3f)
                val y = r.top + r.height() * 0.6f
                c.save()
                c.rotate(sway, x, y)
                emoji.textSize = w * (0.025f + 0.025f * grow)
                c.drawText(if (ready || grow > 0.66f) icon else "🌱", x, y, emoji)
                c.restore()
            }
            if (ready) {
                val a = (sin(t * 4f + i) + 1f) / 2f
                fill.color = Colors.alpha(0xFFFFF59D.toInt(), 0.4f + 0.4f * a)
                c.drawCircle(r.centerX(), r.top - h * 0.03f, w * 0.012f, fill)
            }
        }
    }

    // ------------------------------------------------------------------ moving furniture

    /** Area of a piece of furniture before it is moved, or false if the room does not show it. */
    fun furnitureRect(L: SceneLayout, slot: Slot, f: Furnishing, out: RectF): Boolean {
        val w = L.w
        val h = L.h
        when (slot) {
            Slot.PLANT -> out.set(0f, L.floorY - h * 0.27f, w * 0.14f, L.floorY + h * 0.012f)
            Slot.LAMP -> {
                val lx = w * 0.53f
                val ly = L.window.top + L.window.height() * 0.2f
                out.set(lx - w * 0.07f, ly - h * 0.05f, lx + w * 0.07f, ly + h * 0.06f)
            }
            Slot.PICTURE -> out.set(w * 0.6f, L.window.top, w * 0.86f, L.window.top + L.window.height() * 0.55f)
            Slot.RUG -> out.set(w * 0.14f, L.groundY - h * 0.03f, w * 0.86f, L.groundY + h * 0.035f)
            Slot.TOY -> {
                if (f.toy == null) return false
                val s = L.petSize * 0.7f
                val g = L.groundY + h * 0.06f
                out.set(w * 0.1f - s * 0.45f, g - s * 1.0f, w * 0.1f + s * 0.45f, g)
            }
            Slot.DECO -> {
                if (f.deco == null) return false
                val s = L.petSize * 0.8f
                val g = min(L.groundY + h * 0.06f, h * 0.975f)
                val x = w * (if (L.kind == LayoutKind.BOX) 0.84f else 0.81f)
                out.set(x - s * 0.65f, g - s * 1.2f, x + s * 0.65f, g)
            }
            Slot.BED -> {
                if (!f.hasBed) return false
                val s = L.propSize * 0.92f
                out.set(L.bedX * w - s * 0.55f, L.backGroundY - s * 0.85f, L.bedX * w + s * 0.55f, L.backGroundY + s * 0.1f)
            }
            else -> return false
        }
        return true
    }

    /** Dashed frames around the furniture while it is being moved; the [active] one glows. */
    fun editFrames(c: Canvas, L: SceneLayout, f: Furnishing, active: Slot?, t: Float) {
        val w = L.w
        val r = RectF()
        for (slot in de.knuffi.core.House.MOVABLE) {
            if (!furnitureRect(L, slot, f, r)) continue
            r.offset((f.offsets[slot] ?: 0f) * w, 0f)
            val on = slot == active
            dash.pathEffect = DashPathEffect(floatArrayOf(w * 0.02f, w * 0.014f), (t * w * 0.05f) % (w * 0.034f))
            dash.strokeWidth = w * (if (on) 0.007f else 0.004f)
            dash.color = if (on) 0xFFFF6FA8.toInt() else Colors.alpha(Color.WHITE, 0.9f)
            fill.shader = null
            fill.color = Colors.alpha(if (on) 0xFFFF6FA8.toInt() else Color.WHITE, if (on) 0.16f else 0.08f)
            c.drawRoundRect(r, w * 0.02f, w * 0.02f, fill)
            c.drawRoundRect(r, w * 0.02f, w * 0.02f, dash)
            // the little "move" handle
            val hx = r.centerX()
            val hy = r.top - w * 0.03f
            fill.color = if (on) 0xFFFF6FA8.toInt() else 0xFF8A7CC8.toInt()
            c.drawCircle(hx, hy, w * 0.03f, fill)
            emoji.textSize = w * 0.032f
            emoji.color = Color.WHITE
            c.drawText("↔", hx, hy + w * 0.011f, emoji)
        }
    }

    /** The movable piece of furniture at (x, y), the frontmost first. */
    fun hitFurniture(L: SceneLayout, f: Furnishing, x: Float, y: Float): Slot? {
        val r = RectF()
        val order = listOf(Slot.DECO, Slot.TOY, Slot.BED, Slot.PLANT, Slot.LAMP, Slot.PICTURE, Slot.RUG)
        for (slot in order) {
            if (!furnitureRect(L, slot, f, r)) continue
            r.offset((f.offsets[slot] ?: 0f) * L.w, 0f)
            r.inset(-L.w * 0.02f, -L.w * 0.04f)
            if (r.contains(x, y)) return slot
        }
        return null
    }

    @Suppress("unused")
    private fun unused() = PI
}
