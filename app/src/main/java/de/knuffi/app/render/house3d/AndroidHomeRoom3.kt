package de.knuffi.app.render.house3d

import android.graphics.Bitmap
import android.graphics.PointF
import android.graphics.RectF
import de.knuffi.app.render.DoorPoints
import de.knuffi.app.render.SceneLayout
import de.knuffi.core.Door
import de.knuffi.core.DoorSide
import de.knuffi.core.Slot

/**
 * The 3D room behind the pet on the home screen. The room is rendered in the background into
 * two pictures: everything ([back]) and the furniture standing in front of the pet ([front]),
 * which is drawn over the pet.
 */
class HomeRoom3 {
    private val back = Layer3()
    private val front = Layer3()
    var view: RoomView3? = null
        private set
    var model: RoomModel? = null
        private set
    private var key = ""
    private var lastSpec: RoomSpec? = null
    private var lastDims = 0L
    private var frontKey: String? = null

    /** What stood in front of the pet last time (it stays in front a little longer, no flicker). */
    private val wasInFront = HashSet<Item3>()
    private var camera: Camera3? = null

    /** Rectangles on the screen for taps (pixels). */
    val doorRects = ArrayList<Pair<Door, RectF>>()
    val fixtureRects = ArrayList<Pair<String, RectF>>()
    var pictureRect: RectF? = null
        private set
    var windowRect: RectF? = null
        private set
    var lampPoint: PointF? = null
        private set

    /** Builds the room again when something changed; cheap when nothing did. */
    fun prepare(L: SceneLayout, spec: RoomSpec) {
        val dims = (L.w.toLong() shl 32) or L.h.toLong()
        if (spec === lastSpec && dims == lastDims && model != null) return
        val k = "${L.w.toInt()}x${L.h.toInt()}:${L.floorY.toInt()}:${L.groundY.toInt()}:$spec"
        lastSpec = spec
        lastDims = dims
        if (k == key && model != null) return
        key = k
        val v = RoomView3(L.w, L.h, L.floorY, L.groundY, L.vpY, Room3.width(spec.kind))
        val m = Room3.build(spec, Room3.Mode.HOME, v.wallH, baked = false)
        view = v
        model = m
        frontKey = null
        val cam = v.camera(1f)
        camera = cam
        doorRects.clear()
        fixtureRects.clear()
        for (it in m.items) {
            val tag = it.tag ?: continue
            if (tag.startsWith("door:")) {
                val to = tag.removePrefix("door:")
                val dp = m.doors.firstOrNull { d -> d.door.to.name == to } ?: continue
                cam.rect(it.bounds)?.let { r -> doorRects += dp.door to RectF(r[0], r[1], r[2], r[3]) }
            }
        }
        for (f in m.fixtures) {
            if (f.tag !in TAPPABLE) continue
            cam.rect(f.item.bounds)?.let { r -> fixtureRects += f.tag to RectF(r[0], r[1], r[2], r[3]) }
        }
        pictureRect = m.placed[Slot.PICTURE]?.item?.bounds?.let { b -> cam.rect(b)?.let { RectF(it[0], it[1], it[2], it[3]) } }
        windowRect = m.window?.let { wr ->
            val a = cam.project(wr[0], wr[3], -m.d / 2f)
            val b = cam.project(wr[2], wr[1], -m.d / 2f)
            if (a != null && b != null) RectF(a[0], a[1], b[0], b[1]) else null
        }
        lampPoint = m.placed[Slot.LAMP]?.let { p ->
            val g = p.piece.glow ?: return@let null
            val out = FloatArray(4)
            M4.apply(p.item.matrix, g[0], g[1], g[2], out)
            cam.project(out[0], out[1], out[2])?.let { PointF(it[0], it[1]) }
        }
        val scene = Scene3()
        scene.items += m.items
        val rk = renderScale(L.h)
        Room3d.request(back, scene, v.camera(rk), RenderOptions(cutaway = false), key)
    }

    /** The picture of the whole room, or null while it is not ready. */
    fun backImage(): Bitmap? {
        val b = back.take() ?: return null
        return if (back.shownToken == key) b else if (back.requested == key) b else null
    }

    /** Things standing in front of the pet (at room depth [petZ]) are drawn again over it. */
    fun frontImage(petZ: Float): Bitmap? {
        val m = model ?: return null
        val v = view ?: return null
        val inFront = ArrayList<Item3>()
        fun limit(it: Item3) = petZ + if (it in wasInFront) 0.04f else 0.12f
        for (p in m.placed.values) {
            if (p.piece.flat || p.piece.onWall) continue
            if (p.z > limit(p.item)) inFront += p.item
        }
        for (f in m.fixtures) {
            val b = f.item.bounds
            if (b.y0 > 0.5f) continue
            if ((b.z0 + b.z1) / 2f > limit(f.item)) inFront += f.item
        }
        wasInFront.clear()
        wasInFront += inFront
        val k = inFront.joinToString(",") { it.tag ?: "" } + "@" + key
        if (inFront.isEmpty()) {
            frontKey = k
            return null
        }
        if (k != frontKey) {
            frontKey = k
            val scene = Scene3()
            scene.items += inFront
            Room3d.request(front, scene, v.camera(renderScale(v.h)), RenderOptions(cutaway = false, shadows = false), k)
        }
        val img = front.take()
        return if (front.shownToken == k) img else null
    }

    /** Door spots for the pet in this room. */
    val doorPoints: DoorPoints = object : DoorPoints {
        private fun spot(d: Door, out: Boolean): Pair<Float, Float> {
            val m = model ?: return 0.5f to 0f
            val v = view ?: return 0.5f to 0f
            val dp = m.doors.firstOrNull { it.door.to == d.to } ?: return 0.5f to 0f
            val (x, z) = when (d.side) {
                DoorSide.WEST -> (if (out) -m.w / 2f - 0.45f else -m.w / 2f + 0.5f) to dp.z
                DoorSide.EAST -> (if (out) m.w / 2f + 0.45f else m.w / 2f - 0.5f) to dp.z
                DoorSide.NORTH -> dp.x to (if (out) -m.d / 2f - 0.35f else -m.d / 2f + 0.6f)
                DoorSide.SOUTH -> dp.x to (if (out) m.d / 2f else m.d / 2f - 1.0f)
            }
            return v.toPet(x, z)
        }

        override fun outX(d: Door) = spot(d, true).first
        override fun outZ(d: Door) = spot(d, true).second
        override fun inX(d: Door) = spot(d, false).first
        override fun inZ(d: Door) = spot(d, false).second
    }

    fun release() {
        back.clear()
        front.clear()
        key = ""
        lastSpec = null
        model = null
    }

    companion object {
        val TAPPABLE = setOf("kitchen", "tub", "piano", "cards", "beds", "pool")

        /** The 3D pictures are made a bit smaller than the screen and stretched (much faster). */
        fun renderScale(h: Float): Float = (1500f / h).coerceIn(0.4f, 1f) * Room3d.scale
    }
}
