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
}
