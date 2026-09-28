package de.knuffi.app.render

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import de.knuffi.core.Need
import kotlin.math.max

/** Everything about the world the scene needs to know for one frame. */
class SceneModel {
    var room: String = "room_cozy"
    var hour: Float = 12f
    var lightsOff: Boolean = false
    var poops: Int = 0
    var darkUi: Boolean = false
    var weather: Weather = Weather.CLEAR
    var look: PetLook? = null
    var needs: List<Need> = emptyList()
    var showBubble: Boolean = true
}

/**
 * Composes the room (static layer cached in a bitmap), props, the pet, lighting,
 * particles and the speech bubble.
 */
class SceneRenderer {
    val layout = SceneLayout()
    val room = RoomRenderer()
    val pet = PetRenderer()
    val props = PropRenderer()
    private var cache: Bitmap? = null
    private var cacheKey = ""
    private val bitmapPaint = Paint(Paint.FILTER_BITMAP_FLAG)
    private var lastPoops = -1

    /** Pet bounds in canvas coordinates. */
    val petBounds = RectF()
    var headNX = 0.5f
        private set
    var headNY = 0.5f
        private set
    var centerNX = 0.5f
        private set
    var centerNY = 0.6f
        private set

    fun setTypeface(tf: Typeface) {
        props.typeface = tf
    }

    private fun poopX(i: Int) = floatArrayOf(0.1f, 0.9f, 0.3f)[i]

    fun configure(d: PetDirector, w: Float, h: Float, kind: LayoutKind) {
        layout.compute(w, h, kind)
        d.minX = layout.minX
        d.maxX = layout.maxX
        d.bowlX = layout.bowlX
        d.bedX = layout.bedX
    }

    fun release() {
        cache?.recycle()
        cache = null
        cacheKey = ""
    }

    private fun ensureCache(w: Float, h: Float, roomId: String, kind: LayoutKind) {
        val key = "${w.toInt()}x${h.toInt()}:$roomId:$kind"
        if (key == cacheKey && cache != null) return
        cache?.recycle()
        val bmp = Bitmap.createBitmap(max(1, w.toInt()), max(1, h.toInt()), Bitmap.Config.ARGB_8888)
        room.drawStatic(Canvas(bmp), layout, roomId)
        cache = bmp
        cacheKey = key
    }

    fun draw(c: Canvas, w: Float, h: Float, kind: LayoutKind, m: SceneModel, d: PetDirector, ps: ParticleSystem?, t: Float) {
        configure(d, w, h, kind)
        val L = layout
        ensureCache(w, h, m.room, kind)
        room.drawBehind(c, L, m.room, m.hour, t, m.weather)
        cache?.let { c.drawBitmap(it, 0f, 0f, bitmapPaint) }
        room.drawAnimated(c, L, m.room, m.hour, t)

        val look = m.look
        val cozy = room.isCozy(m.room)
        val pr = d.props
        val propS = L.propSize
        val bedS = propS * 0.92f
        val backY = L.backGroundY

        // Pet placement with depth
        val z = d.z
        val petSize = L.petSize * (1f - 0.2f * z)
        val floorY = L.groundY + (backY - L.groundY) * z
        val ground = floorY - d.yOffset * petSize
        val px = d.x * w
        val petInFront = z < 0.5f

        // Back props
        props.bedBack(c, L.bedX * w, backY, bedS, cozy)
        props.bowlBack(c, L.bowlX * w, backY, propS)
        pr.food?.let { props.food(c, L.bowlX * w, backY, propS, it, pr.bites, pr.foodPop) }
        if (petInFront) {
            props.bowlFront(c, L.bowlX * w, backY, propS)
            props.bedFront(c, L.bedX * w, backY, bedS, pr.blanket, cozy, t)
        }

        // Poops on the floor
        val poopR = L.petSize * 0.075f
        val poopBottom = L.groundY + L.h * 0.02f
        if (lastPoops > m.poops && ps != null) {
            for (i in m.poops until lastPoops.coerceAtMost(3)) {
                ps.burst(PKind.SPARKLE, poopX(i), poopBottom / h - 0.02f, 8, 0.25f, 1f, 0.02f, ParticleSystem.SPARKLE_COLORS)
                ps.add(Particle(PKind.SMOKE, poopX(i), poopBottom / h - 0.02f, 0f, -0.03f, 0.8f, 0.03f, 0x66FFFFFF))
            }
        }
        lastPoops = m.poops
        for (i in 0 until m.poops.coerceAtMost(3)) props.poop(c, poopX(i) * w, poopBottom, poopR, t + i)

        // Tub (behind the pet part)
        if (pr.tub > 0.01f) props.tubBack(c, pr.tubX * w, L.groundY, L.petSize, pr.tub, t)

        // The pet
        if (look != null) {
            if (d.showsShadow) pet.drawShadow(c, px, floorY, petSize, d.pose, look)
            pet.draw(c, px, ground, petSize, look, d.pose)
            petBounds.set(pet.bounds)
            headNX = pet.head.x / w
            headNY = pet.head.y / h
            centerNX = petBounds.centerX() / w
            centerNY = petBounds.centerY() / h
        }

        if (!petInFront) {
            props.bowlFront(c, L.bowlX * w, backY, propS)
            props.bedFront(c, L.bedX * w, backY, bedS, pr.blanket, cozy, t)
        }
        if (pr.tub > 0.01f) props.tubFront(c, pr.tubX * w, L.groundY, L.petSize, pr.tub, if (pr.inTub) 1f else 0.5f, t)
        if (pr.ball) props.ball(c, pr.bx * w, L.groundY, pr.by * L.petSize, L.petSize * 0.13f, pr.brot, pr.ballAlpha)
        pr.pill?.let { props.floatingItem(c, pr.pillX * w, ground - pr.pillY * petSize, petSize * 0.2f, it, t) }
        for (i in 0 until m.poops.coerceAtMost(3)) props.stink(c, poopX(i) * w, poopBottom, poopR, t, i)

        room.drawLighting(c, L, m.room, m.hour, t, m.lightsOff, m.darkUi)
        ps?.draw(c, w, h)

        val bubble = d.bubble
        if (m.showBubble && bubble != null && look != null) {
            val size = max(L.w * 0.042f, petSize * 0.1f)
            props.speech(c, bubble, pet.head.x, pet.head.y - petSize * 0.04f, w, size, d.bubbleAlpha)
        }
    }
}
