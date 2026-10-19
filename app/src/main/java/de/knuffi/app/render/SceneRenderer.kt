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

    /** Pixels at the top that are covered by UI (speech bubbles stay below). */
    var safeTop: Float = 0f
    var furniture: Furnishing = Furnishing()

    /** A pet visiting from the Kuschelhaus. */
    var guest: PetLook? = null

    /** A second visitor. */
    var guest2: PetLook? = null

    /** Hiding places to tap during hide and seek (normalised scene coordinates), with a highlight 0..1. */
    var hideSpots: List<Pair<de.knuffi.core.HideSpot, Float>> = emptyList()

    /** Name tag under the pet (home screen wallpaper). */
    var nameTag: String? = null

    /** Today's hidden object, if not found yet. */
    var hidden: de.knuffi.core.HiddenItem? = null
}

/**
 * Composes the room (static layer cached in a bitmap), props, the pet, lighting,
 * particles and the speech bubble.
 */
class SceneRenderer {
    val layout = SceneLayout()
    val room = RoomRenderer()
    val pet = PetRenderer()
    private val guestPet = PetRenderer()
    private val guestPet2 = PetRenderer()
    val props = PropRenderer()
    val toyArt = ToyRenderer()
    private var cache: Bitmap? = null
    private var cacheKey = ""
    private val bitmapPaint = Paint(Paint.FILTER_BITMAP_FLAG)
    private var lastPoops = -1

    /** Pet bounds in canvas coordinates. */
    val petBounds = RectF()
    private val hiddenRect = RectF()

    /** True if (x, y) hits today's hidden object. */
    fun hitHidden(x: Float, y: Float): Boolean = !hiddenRect.isEmpty && hiddenRect.contains(x, y)

    /** Things in the cosy room that react to taps: "picture", "window", "lamp" or null. */
    fun hitObject(x: Float, y: Float, room: String): String? {
        if (!this.room.isCozy(room)) return null
        val L = layout
        val win = L.window
        val pl = L.w * 0.6f
        val pr = L.w * 0.86f
        val pb = win.top + win.height() * 0.55f
        if (x in pl..pr && y in win.top..pb) return "picture"
        if (win.contains(x, y)) return "window"
        val lx = L.w * 0.53f
        val ly = win.top + win.height() * 0.2f
        if (kotlin.math.hypot(x - lx, y - ly) < L.w * 0.08f) return "lamp"
        return null
    }
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
        // On the home screen the ladder reaches almost up to the clock.
        d.toys.ladderHeight = if (kind == LayoutKind.WALLPAPER) max(1.55f, (layout.groundY - h * 0.2f) / layout.petSize) else 1.55f
    }

    fun release() {
        cache?.recycle()
        cache = null
        cacheKey = ""
    }

    private fun ensureCache(w: Float, h: Float, roomId: String, kind: LayoutKind, f: Furnishing) {
        val key = "${w.toInt()}x${h.toInt()}:$roomId:$kind:${f.key}"
        if (key == cacheKey && cache != null) return
        cache?.recycle()
        val bmp = Bitmap.createBitmap(max(1, w.toInt()), max(1, h.toInt()), Bitmap.Config.ARGB_8888)
        room.drawStatic(Canvas(bmp), layout, roomId, f)
        cache = bmp
        cacheKey = key
    }

    fun draw(c: Canvas, w: Float, h: Float, kind: LayoutKind, m: SceneModel, d: PetDirector, ps: ParticleSystem?, t: Float, guest: PetDirector? = null, friends: FriendPlay? = null, guest2: PetDirector? = null) {
        configure(d, w, h, kind)
        val L = layout
        val f = m.furniture
        ensureCache(w, h, m.room, kind, f)
        room.drawBehind(c, L, m.room, m.hour, t, m.weather)
        cache?.let { c.drawBitmap(it, 0f, 0f, bitmapPaint) }
        room.drawAnimated(c, L, m.room, m.hour, t, f)

        val toys = d.toys
        // Paint on the wall sits behind everything else.
        for (sp in toys.splashes) {
            if (sp.onFloor) continue
            val sx = sp.x * w
            val sy = L.groundY - sp.lift * L.petSize
            sp.sx = sx
            sp.sy = sy
            sp.sr = sp.size * L.petSize
            toyArt.splash(c, sp, sx, sy, sp.sr)
        }

        val look = m.look
        val cozy = room.isCozy(m.room)
        val pr = d.props
        val propS = L.propSize
        val bedS = propS * 0.92f
        val backY = L.backGroundY

        // Footprints and paint on the floor
        val life = de.knuffi.core.ToyTuning.footprintSeconds(d.toyConfig.footprints)
        for (fp in toys.footprints) {
            val fy = L.groundY + (backY - L.groundY) * fp.z + L.petSize * 0.01f
            val age = (t - fp.born) / life.coerceAtLeast(0.1f)
            toyArt.footprint(c, fp, fp.x * w, fy, L.petSize * 0.055f * (1f - 0.2f * fp.z), (1f - age).coerceIn(0f, 1f))
        }
        for (sp in toys.splashes) {
            if (!sp.onFloor) continue
            val sx = sp.x * w
            val sy = L.groundY + L.h * 0.015f
            sp.sx = sx
            sp.sy = sy
            sp.sr = sp.size * L.petSize
            toyArt.splash(c, sp, sx, sy, sp.sr)
        }

        // Pet placement with depth
        val z = d.z
        val petSize = L.petSize * (1f - 0.2f * z)
        val floorY = L.groundY + (backY - L.groundY) * z
        val ground = floorY - d.yOffset * petSize
        val px = d.x * w
        val petInFront = z < 0.5f

        // Back props
        props.bedBack(c, L.bedX * w, backY, bedS, cozy, f.bed)
        props.bowlBack(c, L.bowlX * w, backY, propS)
        pr.food?.let { props.food(c, L.bowlX * w, backY, propS, it, pr.bites, pr.foodPop) }
        if (petInFront) {
            props.bowlFront(c, L.bowlX * w, backY, propS)
            props.bedFront(c, L.bedX * w, backY, bedS, pr.blanket, cozy, t, f.bed)
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

        // Today's hidden object peeks out somewhere
        val hid = m.hidden
        if (hid != null) {
            val hx = hid.spot.x * w
            val hy = hid.spot.y * h
            val hs = L.petSize * 0.16f
            props.peek(c, hid.emoji, hx, hy, hs, t)
            hiddenRect.set(hx - hs * 1.2f, hy - hs * 1.2f, hx + hs * 1.2f, hy + hs * 1.2f)
        } else {
            hiddenRect.setEmpty()
        }

        // The ladder stands behind the pet
        if (toys.ladderExtend > 0.01f) {
            toyArt.ladder(c, toys.ladderX * w, L.groundY, toys.ladderHeight * L.petSize, L.petSize * 0.5f, toys.ladderExtend)
        }

        // A second visiting friend
        val gl2 = m.guest2
        if (gl2 != null && guest2 != null && !guest2.hidden) {
            guest2.minX = L.minX
            guest2.maxX = L.maxX
            val gz = guest2.z
            val gs = L.petSize * 0.8f * (1f - 0.2f * gz)
            val gFloor = L.groundY + (backY - L.groundY) * gz
            if (guest2.showsShadow) guestPet2.drawShadow(c, guest2.x * w, gFloor, gs, guest2.pose, gl2)
            guestPet2.draw(c, guest2.x * w, gFloor - guest2.yOffset * gs, gs, gl2, guest2.pose)
        }

        // A visiting friend walks around behind the pet
        val gl = m.guest
        if (gl != null && guest != null && !guest.hidden) {
            guest.minX = L.minX
            guest.maxX = L.maxX
            val gz = guest.z
            val gs = L.petSize * 0.85f * (1f - 0.2f * gz)
            val gFloor = L.groundY + (backY - L.groundY) * gz
            if (guest.showsShadow) guestPet.drawShadow(c, guest.x * w, gFloor, gs, guest.pose, gl)
            guestPet.draw(c, guest.x * w, gFloor - guest.yOffset * gs, gs, gl, guest.pose)
        }

        // Hiding places during hide and seek
        if (m.hideSpots.isNotEmpty()) {
            for ((spot, glow) in m.hideSpots) props.hideSpot(c, spot.x * w, spot.y * h, L.petSize * 0.17f, glow, t)
        }

        // The pet (not while it hides)
        if (look != null && !d.hidden) {
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
            props.bedFront(c, L.bedX * w, backY, bedS, pr.blanket, cozy, t, f.bed)
        }
        if (pr.tub > 0.01f) props.tubFront(c, pr.tubX * w, L.groundY, L.petSize, pr.tub, if (pr.inTub) 1f else 0.5f, t)
        if (pr.ball) props.ball(c, pr.bx * w, L.groundY, pr.by * L.petSize, L.petSize * 0.13f, pr.brot, pr.ballAlpha, yarn = look?.isCat == true)
        if (friends != null && friends.ballVisible && gl != null) {
            props.ball(c, friends.ballX * w, L.groundY, friends.ballH * L.petSize, L.petSize * 0.12f, friends.ballRot, 1f, yarn = look?.isCat == true)
        }
        if (pr.folding >= 0f && look != null) {
            // paper in the hands that turns into a plane
            val hx = px + d.pose.turn * petSize * 0.08f
            val hy = ground - petSize * 0.36f
            if (pr.folding < 0.5f) {
                val k = 1f - pr.folding
                toyArt.paper(c, hx, hy, petSize * 0.16f * (0.6f + 0.4f * k), pr.folding * 180f)
            } else {
                toyArt.plane(c, hx, hy, petSize * 0.13f, -10f, 1f, 1f)
            }
        }
        toys.plane?.let { pl ->
            val sx = pl.x * w
            val sy = L.groundY - pl.h * L.petSize - L.petSize * 0.04f
            pl.sx = sx
            pl.sy = sy
            pl.sr = L.petSize * 0.2f
            toyArt.plane(c, sx, sy, L.petSize * 0.2f, pl.angle, if (pl.vx < 0f) -1f else 1f, pl.alpha)
        }
        pr.pill?.let { props.floatingItem(c, pr.pillX * w, ground - pr.pillY * petSize, petSize * 0.2f, it, t) }
        for (i in 0 until m.poops.coerceAtMost(3)) props.stink(c, poopX(i) * w, poopBottom, poopR, t, i)

        room.drawLighting(c, L, m.room, m.hour, t, m.lightsOff, m.darkUi, f)
        ps?.draw(c, w, h)
        d.companion?.let { props.flyer(c, it, d.companionX * w, d.companionY * h, L.petSize * 0.17f, t) }

        val tag = m.nameTag
        if (tag != null && look != null && !look.isEgg && !d.hidden) {
            props.nameTag(c, tag, px, floorY + L.petSize * 0.08f, max(L.w * 0.032f, petSize * 0.075f))
        }

        val gb = guest?.bubble
        if (m.showBubble && gl != null && guest != null && gb != null && !guest.hidden) {
            val gs = L.petSize * 0.85f
            props.speech(c, gb, guestPet.head.x, guestPet.head.y - gs * 0.04f, w, max(L.w * 0.04f, gs * 0.1f), guest.bubbleAlpha, m.safeTop)
        }
        val gb2 = guest2?.bubble
        if (m.showBubble && gl2 != null && guest2 != null && gb2 != null && !guest2.hidden) {
            val gs = L.petSize * 0.8f
            props.speech(c, gb2, guestPet2.head.x, guestPet2.head.y - gs * 0.04f, w, max(L.w * 0.04f, gs * 0.1f), guest2.bubbleAlpha, m.safeTop)
        }

        val bubble = d.bubble
        if (m.showBubble && bubble != null && look != null && !d.hidden) {
            val size = max(L.w * 0.042f, petSize * 0.1f)
            props.speech(c, bubble, pet.head.x, pet.head.y - petSize * 0.04f, w, size, d.bubbleAlpha, m.safeTop)
        }
    }
}
