package de.knuffi.app.render.house3d

import de.knuffi.core.DoorSide
import de.knuffi.core.House
import de.knuffi.core.RoomKind
import kotlin.math.abs

/** One room of the house in the 3D overview: where it is and what it contains. */
class HouseRoom3(val kind: RoomKind, val model: RoomModel, val cx: Float, val cz: Float, val items: List<Item3>) {
    /** The floor of the room (world coordinates). */
    val rect: Rect2 = Rect2(cx - model.w / 2f, cz - model.d / 2f, cx + model.w / 2f, cz + model.d / 2f)
}

/** A free spot where a room can be built. */
class Plot3(val kind: RoomKind, val rect: Rect2, val item: Item3)

/** The whole house for the overview. */
class HouseModel3(val scene: Scene3, val rooms: List<HouseRoom3>, val plots: List<Plot3>) {
    fun roomAt(x: Float, z: Float): RoomKind? =
        rooms.firstOrNull { x >= it.rect.x0 - 0.1f && x <= it.rect.x1 + 0.1f && z >= it.rect.z0 - 0.1f && z <= it.rect.z1 + 0.1f }?.kind
            ?: plots.firstOrNull { x >= it.rect.x0 && x <= it.rect.x1 && z >= it.rect.z0 && z <= it.rect.z1 }?.kind
}

object House3 {
    /** Middle of a room in the house (world coordinates). */
    fun center(k: RoomKind): Pair<Float, Float> = (k.col + k.cols / 2f - 1.5f) * Room3.CELL_W to (k.row - 1f) * Room3.CELL_D

    /**
     * Builds the house from the rooms that exist ([specs]); the other cells become plots
     * ([locked]: rooms that cannot be built yet).
     */
    fun build(specs: List<RoomSpec>, locked: Set<RoomKind>): HouseModel3 {
        val scene = Scene3()
        val built = specs.mapNotNull { it.kind }.toSet()
        // the meadow around the house
        val ground = ModelBuilder()
        ground.with(Mat(col(0xFFFFFFFF), 0.05f, tex = "lawn|", shadow = false)) {
            val e = 9f
            quad(-e, -0.13f, e, e, -0.13f, e, e, -0.13f, -e, -e, -0.13f, -e, -e / 1.2f, -e / 1.2f, e / 1.2f, e / 1.2f)
        }
        // stepping stones to the front
        for (k in 0 until 4) ground.at(0.9f + (k % 2) * 0.25f, -0.13f, 4.4f + k * 0.6f) {
            with(Mat(col(0xFFE8E0D8), 0.1f)) { cyl(0.22f, 0.2f, 0f, 0.04f, 16) }
        }
        scene.add(ground.build(), M4.identity(), "ground")
        // bushes and trees around
        for ((x, z, r) in listOf(Triple(-5.2f, -3.6f, 0.6f), Triple(5.3f, -3.2f, 0.7f), Triple(-5.4f, 2.8f, 0.55f), Triple(5.5f, 3.6f, 0.5f), Triple(-2.5f, 5.2f, 0.45f), Triple(3.6f, 5.4f, 0.5f)))
            scene.add(bush(r), M4.translation(x, -0.13f, z), "deco")

        val rooms = ArrayList<HouseRoom3>()
        for (spec in specs) {
            val kind = spec.kind ?: continue
            val exterior = DoorSide.entries.filter { side -> !hasNeighbor(kind, side, built) }.toSet()
            val model = Room3.build(spec, Room3.Mode.HOUSE, wallFlags = Room3.WallFlags(exterior))
            val (cx, cz) = center(kind)
            val m = M4.translation(cx, 0f, cz)
            val moved = HashMap<Item3, Item3>()
            for (it in model.items) {
                val wp = it.wallPoint
                val n = Item3(it.model, M4.mul(m, it.matrix), it.tag, it.outward, wp?.let { p -> floatArrayOf(p[0] + cx, p[1], p[2] + cz) })
                n.hidden = it.hidden
                moved[it] = n
            }
            for ((old, n) in moved) n.linkedTo = old.linkedTo?.let { moved[it] }
            val items = model.items.map { moved.getValue(it) }
            scene.items += items
            rooms += HouseRoom3(kind, model, cx, cz, items)
        }
        val plots = ArrayList<Plot3>()
        for (k in RoomKind.entries) {
            if (k in built) continue
            val (cx, cz) = center(k)
            val w = Room3.width(k)
            val d = Room3.ROOM_D
            val b = ModelBuilder()
            val edge = Mat(col(0xFFFFFFFF), 0.2f, shadow = false)
            val t = 0.05f
            b.box(edge, -w / 2f, -0.13f, -d / 2f, w / 2f, -0.09f, -d / 2f + t)
            b.box(edge, -w / 2f, -0.13f, d / 2f - t, w / 2f, -0.09f, d / 2f)
            b.box(edge, -w / 2f, -0.13f, -d / 2f, -w / 2f + t, -0.09f, d / 2f)
            b.box(edge, w / 2f - t, -0.13f, -d / 2f, w / 2f, -0.09f, d / 2f)
            val sign = if (k in locked) "🔒" else "➕"
            b.with(Mat(col(0xFFFFFFFF), 0.1f, tex = "emoji|$sign", texMode = 2, shadow = false)) {
                quad(-0.4f, -0.08f, 0.4f, 0.4f, -0.08f, 0.4f, 0.4f, -0.08f, -0.4f, -0.4f, -0.08f, -0.4f)
            }
            val item = Item3(b.build(), M4.translation(cx, 0f, cz), "plot:${k.name}")
            scene.items += item
            plots += Plot3(k, Rect2(cx - w / 2f, cz - d / 2f, cx + w / 2f, cz + d / 2f), item)
        }
        return HouseModel3(scene, rooms, plots)
    }

    private fun hasNeighbor(k: RoomKind, side: DoorSide, built: Set<RoomKind>): Boolean {
        for (o in built) {
            if (o == k) continue
            for ((ac, ar) in House.cells(k)) for ((bc, br) in House.cells(o)) {
                val dx = bc - ac
                val dy = br - ar
                if (abs(dx) + abs(dy) != 1) continue
                val s = when {
                    dx == 1 -> DoorSide.EAST
                    dx == -1 -> DoorSide.WEST
                    dy == 1 -> DoorSide.SOUTH
                    else -> DoorSide.NORTH
                }
                if (s == side) return true
            }
        }
        return false
    }

    private fun bush(r: Float): Model3 {
        val b = ModelBuilder()
        b.sphere(Mat(col(0xFF5CC26E), 0.25f), 0f, r * 0.7f, 0f, r)
        b.sphere(Mat(col(0xFF6ACF6A), 0.25f), r * 0.6f, r * 0.55f, r * 0.2f, r * 0.7f)
        b.sphere(Mat(col(0xFF4DB862), 0.25f), -r * 0.5f, r * 0.5f, -r * 0.1f, r * 0.75f)
        b.sphere(Mat(col(0xFFFF8FB8), 0.3f), r * 0.3f, r * 1.4f, r * 0.5f, r * 0.12f)
        return b.build()
    }

    /** The orbit camera that shows the whole house. */
    fun overview(): OrbitView = OrbitView(yaw = -0.35f, pitch = 0.92f, dist = 17.5f)

    /** The orbit camera close to one room (for furnishing it). */
    fun closeUp(k: RoomKind): OrbitView {
        val (cx, cz) = center(k)
        return OrbitView(yaw = 0f, pitch = 0.78f, dist = if (k.cols > 1) 9.5f else 6.2f, cx = cx, cy = 0.4f, cz = cz)
    }
}
