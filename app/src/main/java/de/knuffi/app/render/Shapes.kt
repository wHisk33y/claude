package de.knuffi.app.render

import android.graphics.Path
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

object Shapes {
    fun heart(p: Path, cx: Float, cy: Float, s: Float) {
        p.reset()
        p.moveTo(cx, cy + s * 0.38f)
        p.cubicTo(cx - s * 0.98f, cy - s * 0.22f, cx - s * 0.48f, cy - s * 0.98f, cx, cy - s * 0.4f)
        p.cubicTo(cx + s * 0.48f, cy - s * 0.98f, cx + s * 0.98f, cy - s * 0.22f, cx, cy + s * 0.38f)
        p.close()
    }

    fun star(p: Path, cx: Float, cy: Float, outer: Float, inner: Float, points: Int = 5, rotationDeg: Float = 0f) {
        p.reset()
        val step = PI / points
        val rot = rotationDeg / 180.0 * PI - PI / 2
        for (i in 0 until points * 2) {
            val r = if (i % 2 == 0) outer else inner
            val a = rot + i * step
            val x = cx + (cos(a) * r).toFloat()
            val y = cy + (sin(a) * r).toFloat()
            if (i == 0) p.moveTo(x, y) else p.lineTo(x, y)
        }
        p.close()
    }

    /** A star with rounded, puffy tips. */
    fun softStar(p: Path, cx: Float, cy: Float, outer: Float, inner: Float, points: Int = 5, rotationDeg: Float = 0f) {
        p.reset()
        val step = PI / points
        val rot = rotationDeg / 180.0 * PI - PI / 2
        for (i in 0 until points) {
            val aTip = rot + 2 * i * step
            val aIn = aTip + step
            val tipX = cx + (cos(aTip) * outer).toFloat()
            val tipY = cy + (sin(aTip) * outer).toFloat()
            val inX = cx + (cos(aIn) * inner).toFloat()
            val inY = cy + (sin(aIn) * inner).toFloat()
            if (i == 0) {
                val prevIn = aTip - step
                p.moveTo(cx + (cos(prevIn) * inner).toFloat(), cy + (sin(prevIn) * inner).toFloat())
            }
            p.quadTo(tipX, tipY, inX, inY)
        }
        p.close()
    }

    /** A four pointed twinkle. */
    fun sparkle(p: Path, cx: Float, cy: Float, r: Float) {
        p.reset()
        val k = r * 0.22f
        p.moveTo(cx, cy - r)
        p.quadTo(cx + k, cy - k, cx + r, cy)
        p.quadTo(cx + k, cy + k, cx, cy + r)
        p.quadTo(cx - k, cy + k, cx - r, cy)
        p.quadTo(cx - k, cy - k, cx, cy - r)
        p.close()
    }

    fun drop(p: Path, cx: Float, cy: Float, r: Float) {
        p.reset()
        p.moveTo(cx, cy - r * 1.8f)
        p.cubicTo(cx + r * 0.3f, cy - r, cx + r, cy - r * 0.4f, cx + r, cy + r * 0.15f)
        p.cubicTo(cx + r, cy + r * 0.8f, cx + r * 0.5f, cy + r * 1.05f, cx, cy + r * 1.05f)
        p.cubicTo(cx - r * 0.5f, cy + r * 1.05f, cx - r, cy + r * 0.8f, cx - r, cy + r * 0.15f)
        p.cubicTo(cx - r, cy - r * 0.4f, cx - r * 0.3f, cy - r, cx, cy - r * 1.8f)
        p.close()
    }

    fun spiral(p: Path, cx: Float, cy: Float, r: Float, turns: Float = 2.2f, rotation: Float = 0f) {
        p.reset()
        val steps = 40
        for (i in 0..steps) {
            val f = i / steps.toFloat()
            val a = rotation + f * turns * 2f * PI.toFloat()
            val rr = r * f
            val x = cx + cos(a) * rr
            val y = cy + sin(a) * rr
            if (i == 0) p.moveTo(x, y) else p.lineTo(x, y)
        }
    }

    /** Puffy cloud made from overlapping circles (added to the path). */
    fun cloud(p: Path, cx: Float, cy: Float, s: Float) {
        p.reset()
        p.addCircle(cx, cy, s * 0.42f, Path.Direction.CW)
        p.addCircle(cx - s * 0.45f, cy + s * 0.12f, s * 0.32f, Path.Direction.CW)
        p.addCircle(cx + s * 0.48f, cy + s * 0.1f, s * 0.34f, Path.Direction.CW)
        p.addCircle(cx + s * 0.18f, cy - s * 0.18f, s * 0.3f, Path.Direction.CW)
        p.addRoundRect(cx - s * 0.75f, cy, cx + s * 0.8f, cy + s * 0.42f, s * 0.2f, s * 0.2f, Path.Direction.CW)
    }

    /** Rounded triangle pointing up from a base centred at (cx, baseY). */
    fun softTriangle(p: Path, cx: Float, baseY: Float, halfW: Float, height: Float, lean: Float = 0f) {
        p.reset()
        val tipX = cx + lean
        val tipY = baseY - height
        p.moveTo(cx - halfW, baseY)
        p.quadTo(cx - halfW * 0.55f + lean * 0.3f, baseY - height * 0.55f, tipX - halfW * 0.12f, tipY + height * 0.08f)
        p.quadTo(tipX, tipY - height * 0.04f, tipX + halfW * 0.12f, tipY + height * 0.08f)
        p.quadTo(cx + halfW * 0.55f + lean * 0.3f, baseY - height * 0.55f, cx + halfW, baseY)
        p.close()
    }
}
