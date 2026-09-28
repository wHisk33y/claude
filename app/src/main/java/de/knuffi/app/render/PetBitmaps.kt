package de.knuffi.app.render

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import de.knuffi.core.Mood
import de.knuffi.core.VisualStyle

/** Renders the pet into plain bitmaps for the widget and for notifications. */
object PetBitmaps {
    private val renderer = PetRenderer()
    private val frame = PetFrame()
    private val nearest = Paint().apply {
        isFilterBitmap = false
        isAntiAlias = false
    }

    const val FRAME_COUNT = 4

    fun render(look: PetLook, style: VisualStyle, size: Int, frameIndex: Int = 0, silhouette: Int? = null): Bitmap = synchronized(this) {
        val f = frame.reset()
        PetMotion.idle(f, look, 0f)
        f.blink = 0f
        f.tilt = 0f
        f.hop = 0f
        f.squish = 0f
        val happy = look.mood == Mood.HAPPY && !look.sleeping
        when (frameIndex % FRAME_COUNT) {
            0 -> f.breath = -0.6f
            1 -> {
                f.breath = 1f
                if (happy) f.hop = 0.35f
            }
            2 -> {
                f.breath = -0.6f
                if (happy) f.squish = 0.25f
            }
            3 -> {
                f.breath = 0.2f
                if (!look.sleeping) f.blink = 1f
            }
        }
        if (look.form == de.knuffi.core.Form.EGG) f.eggWobble = when (frameIndex % FRAME_COUNT) {
            1 -> -6f
            3 -> 6f
            else -> 0f
        }
        f.t = frameIndex * 0.4f

        val out = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val c = Canvas(out)
        val mode = style.renderMode
        if (mode == RenderMode.PIXEL) {
            val lo = 56
            val small = Bitmap.createBitmap(lo, lo, Bitmap.Config.ARGB_8888)
            val sc = Canvas(small)
            renderer.drawShadow(sc, lo / 2f, lo * 0.95f, lo * 0.72f, f, look, mode)
            val shadow = small.copy(Bitmap.Config.ARGB_8888, true)
            small.eraseColor(Color.TRANSPARENT)
            renderer.draw(sc, lo / 2f, lo * 0.95f, lo * 0.72f, look, f, mode, silhouette)
            outline(small, PixelLayer.OUTLINE)
            val merged = Canvas(shadow)
            merged.drawBitmap(small, 0f, 0f, null)
            c.drawBitmap(shadow, Rect(0, 0, lo, lo), Rect(0, 0, size, size), nearest)
            small.recycle()
            shadow.recycle()
        } else {
            val s = size.toFloat()
            renderer.drawShadow(c, s / 2f, s * 0.95f, s * 0.72f, f, look, mode)
            renderer.draw(c, s / 2f, s * 0.95f, s * 0.72f, look, f, mode, silhouette)
        }
        out
    }

    private fun outline(b: Bitmap, color: Int) {
        val w = b.width
        val h = b.height
        val px = IntArray(w * h)
        b.getPixels(px, 0, w, 0, 0, w, h)
        val out = px.copyOf()
        for (y in 0 until h) for (x in 0 until w) {
            val i = y * w + x
            if ((px[i] ushr 24) != 0) continue
            val solid = (x > 0 && (px[i - 1] ushr 24) > 100) ||
                (x < w - 1 && (px[i + 1] ushr 24) > 100) ||
                (y > 0 && (px[i - w] ushr 24) > 100) ||
                (y < h - 1 && (px[i + w] ushr 24) > 100)
            if (solid) out[i] = color
        }
        b.setPixels(out, 0, w, 0, 0, w, h)
    }
}
