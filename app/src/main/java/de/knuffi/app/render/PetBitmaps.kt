package de.knuffi.app.render

import android.graphics.Bitmap
import android.graphics.Canvas
import de.knuffi.core.Form
import de.knuffi.core.Mood

/** Renders the pet into plain bitmaps for the widget and notifications. */
object PetBitmaps {
    private val renderer = PetRenderer()
    private val pose = PetPose()

    const val FRAME_COUNT = 4

    fun render(look: PetLook, size: Int, frameIndex: Int = 0): Bitmap = synchronized(this) {
        val p = pose.defaults(look, frameIndex * 0.4f)
        val happy = look.mood == Mood.HAPPY && !look.sleeping
        when (frameIndex % FRAME_COUNT) {
            0 -> p.breath = -0.6f
            1 -> {
                p.breath = 1f
                if (happy) {
                    p.lift = 0.12f
                    p.armL = 0.6f
                    p.armR = 0.6f
                }
            }
            2 -> {
                p.breath = -0.6f
                if (happy) {
                    p.scaleY = 0.93f
                    p.scaleX = 1.06f
                }
            }
            3 -> {
                p.breath = 0.2f
                if (!look.sleeping) p.eyeOpen = 0f
            }
        }
        p.scaleY *= 1f + 0.025f * p.breath
        if (look.form == Form.EGG) p.eggWobble = when (frameIndex % FRAME_COUNT) {
            1 -> -6f
            3 -> 6f
            else -> 0f
        }
        val out = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val c = Canvas(out)
        val s = size.toFloat()
        renderer.drawStandalone(c, s, s, look, p, sizeFactor = 0.7f, groundFactor = 0.94f)
        out
    }
}
