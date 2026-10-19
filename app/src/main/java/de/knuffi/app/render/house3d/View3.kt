package de.knuffi.app.render.house3d

import kotlin.math.max
import kotlin.math.min

/**
 * The camera of a room on the home screen. It is placed so that the 3D floor lies exactly where
 * the drawn scene expects it: the back wall meets the floor at [floorY], the pet's front lane
 * (depth 0) is at [groundY] and the horizon at [horizonY] (all in screen pixels).
 *
 * The pet keeps its 2D coordinates: x as a fraction of the screen width and a depth z (0 front
 * lane, 1 back lane). This class converts between those and room coordinates.
 */
class RoomView3(
    val w: Float,
    val h: Float,
    floorY: Float,
    groundY: Float,
    val horizonY: Float,
    /** Width of the room (units). */
    roomW: Float,
    val roomD: Float = Room3.ROOM_D,
) {
    /** How much of the room's width fills the screen at the front lane. */
    val viewW: Float = min(roomW, 3.4f)

    /** Pets are drawn this much smaller in big rooms (the garden). */
    val petScale: Float = 2.4f / viewW

    /** Pixels per unit at the front lane. */
    val s0: Float = 1.04f * w / viewW

    /** Distance from the camera to the front lane and to the back wall (units). */
    val d0: Float
    val dBack: Float
    val f: Float
    val camY: Float
    val camZ: Float

    init {
        val r = max(1.05f, (groundY - horizonY) / max(1f, floorY - horizonY))
        d0 = LANE0 / (r - 1f)
        dBack = d0 + LANE0
        f = s0 * d0
        camY = (groundY - horizonY) / s0
        camZ = -roomD / 2f + dBack
    }

    /** The wall height needed to reach the top of the screen. */
    val wallH: Float get() = min(Room3.TEX_H, camY + horizonY * dBack / f + 0.05f)

    /** Distance from the camera of the pet's depth [z]. */
    fun depthOf(z: Float): Float = d0 + z * (LANE0 - LANE1)

    /** The pet's depth for a distance from the camera. */
    fun zOf(dist: Float): Float = (dist - d0) / (LANE0 - LANE1)

    /** Screen y of the floor at the pet's depth [z]. */
    fun floorY(z: Float): Float = horizonY + f * camY / depthOf(z).coerceAtLeast(0.2f)

    /** Size factor of the pet at depth [z] (1 at the front lane). */
    fun scale(z: Float): Float = d0 / depthOf(z).coerceAtLeast(0.2f)

    /** Room coordinates (x, z) of a pet at screen fraction [px] and depth [pz]. */
    fun toRoom(px: Float, pz: Float): Pair<Float, Float> {
        val dist = depthOf(pz)
        return (px * w - w / 2f) * dist / f to camZ - dist
    }

    /** Screen fraction x and pet depth of a room point. */
    fun toPet(x: Float, z: Float): Pair<Float, Float> {
        val dist = (camZ - z).coerceAtLeast(0.2f)
        return (w / 2f + f * x / dist) / w to zOf(dist)
    }

    /** The camera for an image [k] times the screen size. */
    fun camera(k: Float): Camera3 {
        val iw = max(1, (w * k).toInt())
        val ih = max(1, (h * k).toInt())
        val proj = M4.pinhole(f * k, w / 2f * k, horizonY * k, iw.toFloat(), ih.toFloat(), 0.1f, 60f)
        val view = M4.translation(0f, -camY, -camZ)
        return Camera3(view, proj, floatArrayOf(0f, camY, camZ), iw, ih)
    }

    companion object {
        /** Distance of the pet's front lane (depth 0) and back lane (depth 1) from the back wall. */
        const val LANE0 = 1.5f
        const val LANE1 = 0.75f
    }
}

/** An orbiting camera around a point, for the whole house and for furnishing a room. */
class OrbitView(
    var yaw: Float = -0.5f,
    var pitch: Float = 0.85f,
    var dist: Float = 12f,
    var cx: Float = 0f,
    var cy: Float = 0f,
    var cz: Float = 0f,
    var fov: Float = 0.62f,
) {
    fun camera(w: Int, h: Int): Camera3 {
        val ex = cx + kotlin.math.sin(yaw) * kotlin.math.cos(pitch) * dist
        val ey = cy + kotlin.math.sin(pitch) * dist
        val ez = cz + kotlin.math.cos(yaw) * kotlin.math.cos(pitch) * dist
        val view = M4.lookAt(ex, ey, ez, cx, cy, cz)
        val proj = M4.perspective(fov, w.toFloat(), h.toFloat(), 0.1f, 200f)
        return Camera3(view, proj, floatArrayOf(ex, ey, ez), w, h)
    }

    fun copy(): OrbitView = OrbitView(yaw, pitch, dist, cx, cy, cz, fov)
}
