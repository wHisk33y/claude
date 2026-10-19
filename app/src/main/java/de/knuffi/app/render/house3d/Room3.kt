package de.knuffi.app.render.house3d

import de.knuffi.core.Door
import de.knuffi.core.DoorSide
import de.knuffi.core.RoomKind
import de.knuffi.core.Season
import de.knuffi.core.Slot
import de.knuffi.core.Spot
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/** Everything needed to build one room in 3D. */
data class RoomSpec(
    /** The room of the house (null: the classic single room). */
    val kind: RoomKind?,
    /** The look of the room ("room_cozy", "room_forest" …). */
    val theme: String,
    val wall: String,
    /** The furniture by slot (only the slots that are shown). */
    val furniture: Map<Slot, String>,
    val spots: Map<Slot, Spot>,
    val doors: List<Door> = emptyList(),
    val showBed: Boolean = true,
    /** Garden beds: "" or "emoji|growth|ready". */
    val plants: List<String> = emptyList(),
    val season: Season? = null,
    val trophies: Int = 0,
    val event: String? = null,
)

/** A rectangle on the floor (room coordinates). */
data class Rect2(val x0: Float, val z0: Float, val x1: Float, val z1: Float) {
    fun overlaps(o: Rect2, gap: Float = 0f): Boolean = x0 < o.x1 - gap && x1 > o.x0 + gap && z0 < o.z1 - gap && z1 > o.z0 + gap
}

/** A piece of furniture where it stands (room coordinates, turn in degrees). */
class Placed(val slot: Slot, val piece: Piece, val x: Float, val z: Float, val turn: Float, val y: Float, val item: Item3) {
    val rect: Rect2 get() = Room3.footprint(piece, x, z, turn)
}

/** A door: the middle of its opening on the floor (room coordinates). */
class DoorPlace(val door: Door, val x: Float, val z: Float)

class FixturePlace(val tag: String, val item: Item3)

/** A built room: its items, where furniture and doors are, and the floor taken by fixtures. */
class RoomModel(
    val spec: RoomSpec,
    val w: Float,
    val d: Float,
    val items: List<Item3>,
    val placed: Map<Slot, Placed>,
    val doors: List<DoorPlace>,
    val fixtures: List<FixturePlace>,
    /** Floor that furniture may not use (fixtures and the space in front of doors). */
    val blocked: List<Rect2>,
) {
    /** Where the window is on the back wall (x0, y0, x1, y1), if any. */
    val window: FloatArray? get() = if (spec.theme == "room_cozy" && spec.kind != RoomKind.GARDEN) Room3.windowRect(w) else null
}

/**
 * Builds rooms in 3D: floor, walls with door openings, fixtures (kitchen, bathtub …), doors and
 * the furniture at its spot. Room coordinates: x across (0 in the middle), z towards the front,
 * the back wall at z = -d/2, y up.
 */
object Room3 {
    const val CELL_W = 2.6f
    const val CELL_D = 2.8f
    const val WALL_T = 0.1f
    const val ROOM_D = CELL_D - 2 * WALL_T

    /** Height of the wall textures (rooms in the home view never need more). */
    const val TEX_H = 4.8f

    /** Wall height in the overview. */
    const val HOUSE_WALL_H = 2.5f

    /** Low walls in the overview, between rooms. */
    const val LOW_WALL_H = 0.32f

    const val DOOR_H = 1.45f
    const val DOOR_W = 0.72f

    /** Side doors sit this far in front of the back wall (their middle). */
    const val SIDE_DOOR_V = 1.09f

    /** North and south doors: distance from the middle of their cell. */
    const val NS_DOOR_X = 0.75f

    fun width(kind: RoomKind?): Float = (kind?.cols ?: 1) * CELL_W - 2 * WALL_T

    fun windowRect(w: Float): FloatArray = floatArrayOf(-w / 2f + w * 0.07f, 0.95f, -w / 2f + w * 0.43f, 2.42f)

    enum class Mode {
        /** The room the pet lives in, seen from the front: tall walls, no front wall. */
        HOME,

        /** Part of the whole house. */
        HOUSE,
    }

    // ------------------------------------------------------------------ geometry helpers

    fun footprint(p: Piece, x: Float, z: Float, turn: Float): Rect2 {
        val a = turn * MeshBuilder.DEG
        val c = abs(cos(a))
        val s = abs(sin(a))
        val hw = (p.w * c + p.d * s) / 2f
        val hd = (p.w * s + p.d * c) / 2f
        return Rect2(x - hw, z - hd, x + hw, z + hd)
    }

    /** Room coordinates of a spot. Pictures: x along the back wall, d is the height. */
    fun spotToRoom(spot: Spot, w: Float, d: Float, p: Piece): Triple<Float, Float, Float> =
        if (p.onWall) Triple((spot.x - 0.5f) * w, -d / 2f, PIC_Y0 + spot.d * (PIC_Y1 - PIC_Y0)) else Triple((spot.x - 0.5f) * w, -d / 2f + spot.d * d, 0f)

    fun roomToSpot(x: Float, zOrY: Float, w: Float, d: Float, p: Piece, turn: Float): Spot =
        if (p.onWall) Spot(x / w + 0.5f, ((zOrY - PIC_Y0) / (PIC_Y1 - PIC_Y0)).coerceIn(0f, 1f), 0f) else Spot(x / w + 0.5f, (zOrY + d / 2f) / d, turn)

    const val PIC_Y0 = 1.05f
    const val PIC_Y1 = 2.2f

    /** Moves a piece back into the room (keeps its footprint inside the walls). */
    fun clampInside(p: Piece, x: Float, z: Float, turn: Float, w: Float, d: Float): Pair<Float, Float> {
        if (p.onWall) {
            val hw = p.w / 2f
            return x.coerceIn(-w / 2f + hw + 0.02f, w / 2f - hw - 0.02f) to z
        }
        val r = footprint(p, 0f, 0f, turn)
        val front = d / 2f - FRONT_KEEP
        return x.coerceIn(-w / 2f - r.x0 + 0.01f, w / 2f - r.x1 - 0.01f) to z.coerceIn(-d / 2f - r.z0 + 0.01f, front - r.z1)
    }

    /** How much of the floor at the front stays free (it is hidden under the buttons at home). */
    const val FRONT_KEEP = 0.35f

    // ------------------------------------------------------------------ building

    fun build(spec: RoomSpec, mode: Mode, homeWallH: Float = TEX_H, baked: Boolean = mode == Mode.HOUSE, wallFlags: WallFlags = WallFlags()): RoomModel {
        val kind = spec.kind
        val w = width(kind)
        val d = ROOM_D
        val items = ArrayList<Item3>()
        val outdoor = kind == RoomKind.GARDEN
        val doors = spec.doors.map { doorPlace(it, w, d) }

        // ---------------------------------------------------------------- floor
        val floor = ModelBuilder()
        val floorTex = if (outdoor) "lawn|${spec.season?.name}" else "floor|${spec.theme}"
        // the texture repeats every 1.2 units
        floor.with(Mat(col(0xFFFFFFFF), 0.08f, tex = floorTex, shadow = false)) {
            val ext = if (mode == Mode.HOME) 1.6f else 0f
            val fx0 = -w / 2f - ext
            val fx1 = w / 2f + ext
            val fz1 = d / 2f + if (mode == Mode.HOME) 2f else 0f
            quad(fx0, 0f, fz1, fx1, 0f, fz1, fx1, 0f, -d / 2f, fx0, 0f, -d / 2f, fx0 / 1.2f, -d / 2f / 1.2f, fx1 / 1.2f, fz1 / 1.2f)
        }
        if (mode == Mode.HOUSE) {
            // the floor's edge under the walls
            floor.box(Mat(col(0xFFD8C8C0), 0.05f, shadow = false), -w / 2f - WALL_T, -0.12f, -d / 2f - WALL_T, w / 2f + WALL_T, -0.002f, d / 2f + WALL_T)
        }
        items += Item3(floor.build(), M4.identity(), "floor")

        // ---------------------------------------------------------------- walls
        val fullWalls = HashMap<DoorSide, Item3>()
        if (outdoor) {
            items += garden(spec, w, d, mode, baked, doors)
        } else {
            items += walls(spec, w, d, mode, homeWallH, baked, doors, wallFlags, fullWalls)
        }
        /** Things on a wall are shown with the wall (in the house only when it is high). */
        fun attach(item: Item3, side: DoorSide) {
            if (mode == Mode.HOME) return
            val wall = fullWalls[side]
            if (wall == null) item.hidden = true else item.linkedTo = wall
        }

        // ---------------------------------------------------------------- doors
        for (dp in doors) {
            if (mode == Mode.HOUSE) {
                // a threshold shows the way through a low wall; on a high wall the door is there too
                if (!outdoor) {
                    items += threshold(dp, w, d)
                    val wall = fullWalls[dp.door.side]
                    if (wall != null) items += doorItem(dp, w, d, mode, outdoor).also { it.linkedTo = wall }
                }
                continue
            }
            items += doorItem(dp, w, d, mode, outdoor)
        }

        // ---------------------------------------------------------------- fixtures
        val fixtures = ArrayList<FixturePlace>()
        val blocked = ArrayList<Rect2>()
        for (f in Fixtures3.of(kind, w, d, spec, doors)) {
            val it = Item3(f.model, M4.place(f.x, 0f, f.z, f.turn * MeshBuilder.DEG), "fixture:${f.tag}")
            f.wall?.let { side -> attach(it, side) }
            items += it
            fixtures += FixturePlace(f.tag, it)
            f.blocks?.let { blocked += it }
        }
        for (dp in doors) blocked += doorZone(dp, w, d)

        // ---------------------------------------------------------------- furniture
        val placed = LinkedHashMap<Slot, Placed>()
        if (!outdoor) {
            val taken = ArrayList<Rect2>(blocked)
            for (slot in ORDER) {
                val id = spec.furniture[slot] ?: continue
                if (slot == Slot.BED && !spec.showBed) continue
                val piece = Furniture3.piece(id)
                val spot = spec.spots[slot]
                val (x, z, turn, y) = if (spot != null) {
                    val (sx, sz, sy) = spotToRoom(spot, w, d, piece)
                    val (cx, cz) = clampInside(piece, sx, sz, spot.turn, w, d)
                    Quad4(cx, cz, if (piece.onWall) 0f else spot.turn, sy)
                } else {
                    defaultSpot(slot, piece, w, d, kind, taken, doors)
                }
                val item = Item3(piece.model, M4.place(x, if (piece.onWall) y else 0f, if (piece.onWall) -d / 2f else z, turn * MeshBuilder.DEG), "slot:${slot.name}")
                if (piece.onWall) attach(item, DoorSide.NORTH)
                val p = Placed(slot, piece, x, if (piece.onWall) -d / 2f else z, turn, y, item)
                placed[slot] = p
                items += item
                if (!piece.flat && !piece.onWall) taken += p.rect
            }
        }
        return RoomModel(spec, w, d, items, placed, doors, fixtures, blocked)
    }

    data class Quad4(val x: Float, val z: Float, val turn: Float, val y: Float)

    /** The order furniture is set up in (big pieces first). */
    val ORDER = listOf(Slot.RUG, Slot.BED, Slot.DECO, Slot.TOY, Slot.PLANT, Slot.LAMP, Slot.PICTURE)

    /** Where a piece stands when the player has not moved it: the first free place of a list. */
    fun defaultSpot(slot: Slot, piece: Piece, w: Float, d: Float, kind: RoomKind?, taken: List<Rect2>, doors: List<DoorPlace>): Quad4 {
        val back = -d / 2f
        val hw = w / 2f
        if (piece.onWall) {
            val win = windowRect(w)
            val northX = doors.filter { it.door.side == DoorSide.NORTH }.map { it.x }
            val candidates = listOf(0.55f, -0.55f, 0f, 0.8f)
            val x = candidates.firstOrNull { c -> c - piece.w / 2f > win[2] + 0.05f || c + piece.w / 2f < win[0] - 0.05f } ?: 0.55f
            val y = if (northX.any { abs(it - x) < 0.7f }) 2.0f else 1.75f
            return Quad4(x.coerceIn(-hw + piece.w / 2f, hw - piece.w / 2f), back, 0f, y)
        }
        val cands: List<Triple<Float, Float, Float>> = when (slot) {
            Slot.RUG -> listOf(Triple(0f, 0.12f, 0f), Triple(0f, -0.2f, 0f))
            Slot.BED -> listOf(Triple(0.48f, back + 0.42f, 0f), Triple(-0.48f, back + 0.42f, 0f), Triple(hw - 0.42f, -0.2f, -90f), Triple(-hw + 0.42f, -0.2f, 90f), Triple(0f, 0.2f, 0f))
            Slot.DECO -> listOf(Triple(-0.42f, back + 0.36f, 0f), Triple(0.42f, back + 0.36f, 0f), Triple(-hw + 0.36f, 0.25f, 90f), Triple(hw - 0.36f, 0.25f, -90f), Triple(-hw + 0.36f, -0.35f, 90f), Triple(hw - 0.36f, -0.35f, -90f), Triple(0f, 0.6f, 180f))
            Slot.TOY -> listOf(Triple(-hw + 0.45f, 0.62f, 30f), Triple(hw - 0.45f, 0.62f, -30f), Triple(-hw + 0.4f, back + 0.3f, 0f), Triple(hw - 0.4f, back + 0.3f, 0f), Triple(0f, 0.6f, 0f))
            Slot.PLANT -> listOf(Triple(-hw + 0.3f, back + 0.3f, 0f), Triple(hw - 0.3f, back + 0.3f, 0f), Triple(-hw + 0.3f, 0.6f, 0f), Triple(hw - 0.3f, 0.6f, 0f), Triple(-hw + 0.3f, -0.3f, 0f))
            Slot.LAMP -> listOf(Triple(-hw + 0.75f, back + 0.25f, 0f), Triple(hw - 0.25f, back + 0.75f, 0f), Triple(-hw + 0.25f, back + 0.75f, 0f), Triple(hw - 0.75f, back + 0.25f, 0f), Triple(hw - 0.25f, 0.45f, 0f), Triple(-hw + 0.25f, 0.45f, 0f))
            else -> listOf(Triple(0f, 0f, 0f))
        }
        for ((x, z, t) in cands) {
            val (cx, cz) = clampInside(piece, x, z, t, w, d)
            val r = footprint(piece, cx, cz, t)
            if (piece.flat || taken.none { it.overlaps(r, 0.01f) }) return Quad4(cx, cz, t, 0f)
        }
        // somewhere free in a grid as a last resort
        var gz = back + 0.3f
        while (gz < d / 2f - FRONT_KEEP) {
            var gx = -hw + 0.3f
            while (gx < hw - 0.3f) {
                val (cx, cz) = clampInside(piece, gx, gz, 0f, w, d)
                val r = footprint(piece, cx, cz, 0f)
                if (taken.none { it.overlaps(r, 0.01f) }) return Quad4(cx, cz, 0f, 0f)
                gx += 0.2f
            }
            gz += 0.2f
        }
        val (x, z, t) = cands.first()
        val (cx, cz) = clampInside(piece, x, z, t, w, d)
        return Quad4(cx, cz, t, 0f)
    }

    /** True if [piece] may stand at (x, z) turned by [turn] in [room] (not on others, not in front of doors). */
    fun fits(room: RoomModel, slot: Slot, piece: Piece, x: Float, z: Float, turn: Float): Boolean {
        if (piece.flat) return true
        if (piece.onWall) {
            val win = room.window
            val half = piece.w / 2f
            if (win != null && x + half > win[0] && x - half < win[2] && z + 0.3f > win[1] && z - 0.3f < win[3]) return false
            for (dp in room.doors) if (dp.door.side == DoorSide.NORTH && abs(dp.x - x) < half + DOOR_W / 2f && z - 0.3f < DOOR_H + 0.1f) return false
            return true
        }
        val r = footprint(piece, x, z, turn)
        if (room.blocked.any { it.overlaps(r, 0.01f) }) return false
        for ((s, p) in room.placed) {
            if (s == slot || p.piece.flat || p.piece.onWall) continue
            if (p.rect.overlaps(r, 0.01f)) return false
        }
        return true
    }

    fun doorPlace(door: Door, w: Float, d: Float): DoorPlace = when (door.side) {
        DoorSide.WEST -> DoorPlace(door, -w / 2f, -d / 2f + SIDE_DOOR_V)
        DoorSide.EAST -> DoorPlace(door, w / 2f, -d / 2f + SIDE_DOOR_V)
        DoorSide.NORTH -> DoorPlace(door, nsX(door, w), -d / 2f)
        DoorSide.SOUTH -> DoorPlace(door, nsX(door, w), d / 2f)
    }

    /** x of a north or south door: in its cell, a bit to the right of the middle. */
    private fun nsX(door: Door, w: Float): Float {
        val cols = door.from.cols
        val cell = ((door.pos * cols) - 0.5f).toInt().coerceIn(0, cols - 1)
        val cellX = -w / 2f - WALL_T + CELL_W * (cell + 0.5f)
        return cellX + NS_DOOR_X
    }

    private fun doorZone(dp: DoorPlace, w: Float, d: Float): Rect2 = when (dp.door.side) {
        DoorSide.WEST -> Rect2(-w / 2f, dp.z - DOOR_W / 2f - 0.05f, -w / 2f + 0.55f, dp.z + DOOR_W / 2f + 0.05f)
        DoorSide.EAST -> Rect2(w / 2f - 0.55f, dp.z - DOOR_W / 2f - 0.05f, w / 2f, dp.z + DOOR_W / 2f + 0.05f)
        DoorSide.NORTH -> Rect2(dp.x - DOOR_W / 2f - 0.1f, -d / 2f, dp.x + DOOR_W / 2f + 0.1f, -d / 2f + 0.75f)
        DoorSide.SOUTH -> Rect2(dp.x - DOOR_W / 2f - 0.1f, d / 2f - 0.8f, dp.x + DOOR_W / 2f + 0.1f, d / 2f)
    }

    /** Which walls of a room in the house are shown how (set per frame from the camera). */
    class WallFlags(val exterior: Set<DoorSide> = DoorSide.entries.toSet())

    // ------------------------------------------------------------------ walls

    private fun wallMat(key: String) = Mat(col(0xFFFFFFFF), 0.05f, tex = key, texMode = 2, emissive = 0.6f, shadow = false)

    private fun walls(spec: RoomSpec, w: Float, d: Float, mode: Mode, homeH: Float, baked: Boolean, doors: List<DoorPlace>, flags: WallFlags, full: MutableMap<DoorSide, Item3>): List<Item3> {
        val out = ArrayList<Item3>()
        val cozy = spec.theme == "room_cozy"
        val north = doors.any { it.door.side == DoorSide.NORTH }
        val backKey = "back|${spec.theme}|${spec.wall}|${if (cozy && !north) 1 else 0}|${spec.trophies}|${spec.event ?: ""}|${if (baked) "baked" else "home"}|${fmt(w)}"
        val sideKey = "side|${spec.theme}|${spec.wall}|${if (baked) "baked" else "home"}"
        val outer = Mat(col(0xFFF4ECF2), 0.05f, shadow = false)
        val cap = Mat(col(0xFFFFFFFF), 0.1f, shadow = false)
        val sides = listOf(DoorSide.NORTH, DoorSide.WEST, DoorSide.EAST) + if (mode == Mode.HOUSE) listOf(DoorSide.SOUTH) else emptyList()
        for (side in sides) {
            // positions along the wall as it is built (see wallPiece)
            val openings = doors.filter { it.door.side == side }.map {
                when (side) {
                    DoorSide.NORTH -> it.x
                    DoorSide.SOUTH -> -it.x
                    DoorSide.WEST -> -it.z
                    DoorSide.EAST -> it.z
                }
            }
            // in the house: outer walls are high (seen from inside), walls between rooms stay low
            val heights = if (mode == Mode.HOME) listOf(homeH) else if (side in flags.exterior) listOf(HOUSE_WALL_H) else listOf(LOW_WALL_H)
            for (h in heights) {
                val key = if (side == DoorSide.NORTH || side == DoorSide.SOUTH) backKey else sideKey
                val b = ModelBuilder()
                wallPiece(b, side, w, d, h, openings, wallMat(key), outer, cap, mode == Mode.HOUSE)
                val tag = if (mode == Mode.HOME) "wall" else if (h > LOW_WALL_H) "wall:full:${side.name}" else "wall:low:${side.name}"
                val outward = when (side) {
                    DoorSide.NORTH -> floatArrayOf(0f, 0f, -1f)
                    DoorSide.SOUTH -> floatArrayOf(0f, 0f, 1f)
                    DoorSide.WEST -> floatArrayOf(-1f, 0f, 0f)
                    DoorSide.EAST -> floatArrayOf(1f, 0f, 0f)
                }
                val point = when (side) {
                    DoorSide.NORTH -> floatArrayOf(0f, 0f, -d / 2f)
                    DoorSide.SOUTH -> floatArrayOf(0f, 0f, d / 2f)
                    DoorSide.WEST -> floatArrayOf(-w / 2f, 0f, 0f)
                    DoorSide.EAST -> floatArrayOf(w / 2f, 0f, 0f)
                }
                val item = Item3(b.build(), M4.identity(), tag, outward, point)
                if (h > LOW_WALL_H) full[side] = item
                out += item
            }
        }
        // a skirting board along the walls
        val skirt = ModelBuilder()
        val sk = Mat(col(0xFFFFFFFF), 0.2f, shadow = false)
        skirt.box(sk, -w / 2f, 0f, -d / 2f, w / 2f, 0.07f, -d / 2f + 0.025f)
        out += Item3(skirt.build(), M4.identity(), if (mode == Mode.HOME) "wall" else "wall:low:NORTH", floatArrayOf(0f, 0f, -1f), floatArrayOf(0f, 0f, -d / 2f))
        return out
    }

    /**
     * One wall with its openings ([openings]: middles of door openings along the wall). The inner side
     * gets the texture (u across the wall, v from the floor up to [TEX_H]).
     */
    private fun wallPiece(b: ModelBuilder, side: DoorSide, w: Float, d: Float, h: Float, openings: List<Float>, inner: Mat, outer: Mat, cap: Mat, thick: Boolean) {
        // walls are built along a "run" from a0 to a1 and then put in place
        val len = if (side == DoorSide.NORTH || side == DoorSide.SOUTH) w else d
        val segs = ArrayList<FloatArray>() // a0, a1, y0, y1
        val holes = openings.sorted().map { floatArrayOf(it - DOOR_W / 2f, it + DOOR_W / 2f) }
        var a = -len / 2f
        for (hole in holes) {
            if (hole[0] > a) segs += floatArrayOf(a, hole[0], 0f, h)
            if (h > DOOR_H) segs += floatArrayOf(hole[0], hole[1], DOOR_H, h)
            a = hole[1]
        }
        if (a < len / 2f) segs += floatArrayOf(a, len / 2f, 0f, h)
        val t = WALL_T
        for (s in segs) {
            val (a0, a1, y0, y1) = s.toList()
            val u0 = (a0 + len / 2f) / len
            val u1 = (a1 + len / 2f) / len
            val v0 = 1f - y1 / TEX_H
            val v1 = 1f - y0 / TEX_H
            when (side) {
                DoorSide.NORTH -> {
                    val z = -d / 2f
                    b.with(inner) { quad(a0, y0, z, a1, y0, z, a1, y1, z, a0, y1, z, u0, v0, u1, v1) }
                    if (thick) b.box(outer, a0, y0, z - t, a1, y1, z - 0.001f)
                }
                DoorSide.SOUTH -> {
                    val z = d / 2f
                    // seen from inside the texture runs the other way
                    b.with(inner) { quad(-a0, y0, z, -a1, y0, z, -a1, y1, z, -a0, y1, z, u0, v0, u1, v1) }
                    if (thick) b.box(outer, min(-a0, -a1), y0, z + 0.001f, max(-a0, -a1), y1, z + t)
                }
                DoorSide.WEST -> {
                    val x = -w / 2f
                    b.with(inner) { quad(x, y0, -a0, x, y0, -a1, x, y1, -a1, x, y1, -a0, u0, v0, u1, v1) }
                    if (thick) b.box(outer, x - t, y0, min(-a0, -a1), x - 0.001f, y1, max(-a0, -a1))
                }
                DoorSide.EAST -> {
                    val x = w / 2f
                    b.with(inner) { quad(x, y0, a0, x, y0, a1, x, y1, a1, x, y1, a0, u0, v0, u1, v1) }
                    if (thick) b.box(outer, x + 0.001f, y0, a0, x + t, y1, a1)
                }
            }
        }
        if (thick) {
            // white caps on top of the cut walls
            when (side) {
                DoorSide.NORTH -> for (s in segs) if (s[3] == h) b.box(cap, s[0] - 0.002f, h, -d / 2f - t, s[1] + 0.002f, h + 0.015f, -d / 2f)
                DoorSide.SOUTH -> for (s in segs) if (s[3] == h) b.box(cap, min(-s[0], -s[1]), h, d / 2f, max(-s[0], -s[1]), h + 0.015f, d / 2f + t)
                DoorSide.WEST -> for (s in segs) if (s[3] == h) b.box(cap, -w / 2f - t, h, min(-s[0], -s[1]), -w / 2f, h + 0.015f, max(-s[0], -s[1]))
                DoorSide.EAST -> for (s in segs) if (s[3] == h) b.box(cap, w / 2f, h, s[0], w / 2f + t, h + 0.015f, s[1])
            }
        }
    }

    // ------------------------------------------------------------------ doors

    private fun doorItem(dp: DoorPlace, w: Float, d: Float, mode: Mode, outdoor: Boolean): Item3 {
        val b = ModelBuilder()
        val frame = Mat(col(0xFFFFFFFF), 0.3f)
        val leaf = Mat(doorColor(dp.door.to), 0.3f)
        val side = dp.door.side
        if (side == DoorSide.SOUTH && mode == Mode.HOME) {
            // a doormat at the front edge with the room's sign
            b.rbox(Mat(doorColor(dp.door.to), 0.05f, shadow = false), -0.36f, 0f, -0.21f, 0.36f, 0.02f, 0.21f, 0.04f)
            b.rbox(Mat(Colors3.lighten(doorColor(dp.door.to), 0.5f), 0.05f, shadow = false), -0.3f, 0.005f, -0.15f, 0.3f, 0.024f, 0.15f, 0.03f)
            b.with(Mat(col(0xFFFFFFFF), 0.1f, tex = "emoji|${dp.door.to.emoji}", texMode = 2, shadow = false)) {
                quad(-0.13f, 0.03f, 0.13f, 0.13f, 0.03f, 0.13f, 0.13f, 0.03f, -0.13f, -0.13f, 0.03f, -0.13f)
            }
            return Item3(b.build(), M4.place(dp.x, 0f, d / 2f - 0.45f), "door:${dp.door.to.name}")
        }
        // frame around the opening, the door leaf swung open and a sign with the room
        val hw = DOOR_W / 2f
        b.box(frame, -hw - 0.06f, 0f, -0.07f, -hw, DOOR_H + 0.06f, 0.05f)
        b.box(frame, hw, 0f, -0.07f, hw + 0.06f, DOOR_H + 0.06f, 0.05f)
        b.box(frame, -hw - 0.06f, DOOR_H, -0.07f, hw + 0.06f, DOOR_H + 0.07f, 0.05f)
        b.at(hw, 0f, 0.03f, rotY = -75f) {
            box(leaf, -DOOR_W + 0.03f, 0.01f, -0.025f, 0f, DOOR_H - 0.02f, 0.025f)
            sphere(Mat(col(0xFFFFC83D), 0.7f), -DOOR_W + 0.12f, 0.72f, 0.04f, 0.035f)
        }
        // what lies behind: a glimpse of the next room
        b.with(Mat(Colors3.lighten(doorColor(dp.door.to), 0.55f), 0.05f, emissive = 0.4f, shadow = false)) {
            quad(-hw, 0f, -0.1f - WALL_T, hw, 0f, -0.1f - WALL_T, hw, DOOR_H, -0.1f - WALL_T, -hw, DOOR_H, -0.1f - WALL_T)
        }
        if (!outdoor || side != DoorSide.SOUTH) {
            b.rbox(Mat(col(0xFFFFFFFF), 0.3f), -0.2f, DOOR_H + 0.1f, 0.0f, 0.2f, DOOR_H + 0.42f, 0.04f, 0.05f)
            b.with(Mat(col(0xFFFFFFFF), 0.1f, tex = "emoji|${dp.door.to.emoji}", texMode = 2)) {
                quad(-0.14f, DOOR_H + 0.12f, 0.045f, 0.14f, DOOR_H + 0.12f, 0.045f, 0.14f, DOOR_H + 0.4f, 0.045f, -0.14f, DOOR_H + 0.4f, 0.045f)
            }
        }
        val turn = when (side) {
            DoorSide.NORTH -> 0f
            DoorSide.SOUTH -> 180f
            DoorSide.WEST -> 90f
            DoorSide.EAST -> -90f
        }
        return Item3(b.build(), M4.place(dp.x, 0f, dp.z, turn * MeshBuilder.DEG), "door:${dp.door.to.name}")
    }

    /** A strip on the floor where a door leads through a low wall (house view). */
    private fun threshold(dp: DoorPlace, w: Float, d: Float): Item3 {
        val b = ModelBuilder()
        b.box(Mat(doorColor(dp.door.to), 0.2f, shadow = false), -DOOR_W / 2f, 0f, -0.06f, DOOR_W / 2f, 0.012f, 0.1f)
        val turn = when (dp.door.side) {
            DoorSide.NORTH -> 0f
            DoorSide.SOUTH -> 180f
            DoorSide.WEST -> 90f
            DoorSide.EAST -> -90f
        }
        return Item3(b.build(), M4.place(dp.x, 0f, dp.z, turn * MeshBuilder.DEG), "door:${dp.door.to.name}")
    }

    fun doorColor(k: RoomKind): Int = when (k) {
        RoomKind.LIVING -> col(0xFFFFB27A)
        RoomKind.KITCHEN -> col(0xFFFFD86A)
        RoomKind.BEDROOM -> col(0xFFB8A4FF)
        RoomKind.BATH -> col(0xFF7FD4FF)
        RoomKind.PLAYROOM -> col(0xFFFF8FB8)
        RoomKind.MUSIC -> col(0xFF8FA8FF)
        RoomKind.GARDEN -> col(0xFF7ED98A)
        RoomKind.CRAFT -> col(0xFFFFB86A)
    }

    // ------------------------------------------------------------------ garden

    private fun garden(spec: RoomSpec, w: Float, d: Float, mode: Mode, baked: Boolean, doors: List<DoorPlace>): List<Item3> {
        val out = ArrayList<Item3>()
        // the back of the house with windows, painted
        if (mode == Mode.HOME) {
            val b = ModelBuilder()
            b.with(Mat(col(0xFFFFFFFF), 0.05f, tex = "facade|${spec.season?.name}|${fmt(w)}", texMode = 2, emissive = 0.2f, shadow = false)) {
                quad(-w / 2f - 1.6f, 0f, -d / 2f, w / 2f + 1.6f, 0f, -d / 2f, w / 2f + 1.6f, TEX_H, -d / 2f, -w / 2f - 1.6f, TEX_H, -d / 2f)
            }
            out += Item3(b.build(), M4.identity(), "wall")
        }
        // a low fence around the garden (not where doors are)
        val fence = ModelBuilder()
        val wood = Mat(col(0xFFFFFFFF), 0.2f)
        val sides = if (mode == Mode.HOME) listOf(DoorSide.WEST, DoorSide.EAST) else listOf(DoorSide.WEST, DoorSide.EAST, DoorSide.SOUTH, DoorSide.NORTH)
        for (side in sides) {
            val len = if (side == DoorSide.WEST || side == DoorSide.EAST) d else w
            val gaps = doors.filter { it.door.side == side }.map { if (side == DoorSide.WEST || side == DoorSide.EAST) it.z else it.x }
            var a = -len / 2f + 0.05f
            while (a < len / 2f) {
                if (gaps.none { abs(it - a) < DOOR_W / 2f + 0.05f }) {
                    val (x, z) = when (side) {
                        DoorSide.WEST -> -w / 2f to a
                        DoorSide.EAST -> w / 2f to a
                        DoorSide.NORTH -> a to -d / 2f
                        DoorSide.SOUTH -> a to d / 2f
                    }
                    fence.at(x, 0f, z) {
                        with(wood) {
                            rbox(-0.04f, 0f, -0.04f, 0.04f, 0.42f, 0.04f, 0.015f)
                            at(0f, 0.42f, 0f) { cyl(0.04f, 0f, 0f, 0.06f, 4, capBottom = false) }
                        }
                    }
                }
                a += 0.18f
            }
            // rails
            for (y in floatArrayOf(0.14f, 0.32f)) {
                when (side) {
                    DoorSide.WEST, DoorSide.EAST -> {
                        val x = if (side == DoorSide.WEST) -w / 2f else w / 2f
                        fence.box(wood, x - 0.02f, y, -d / 2f, x + 0.02f, y + 0.035f, d / 2f)
                    }
                    else -> {
                        val z = if (side == DoorSide.NORTH) -d / 2f else d / 2f
                        var s0 = -w / 2f
                        for (g in gaps.sorted()) {
                            fence.box(wood, s0, y, z - 0.02f, g - DOOR_W / 2f, y + 0.035f, z + 0.02f)
                            s0 = g + DOOR_W / 2f
                        }
                        fence.box(wood, s0, y, z - 0.02f, w / 2f, y + 0.035f, z + 0.02f)
                    }
                }
            }
        }
        out += Item3(fence.build(), M4.identity(), "fence")
        return out
    }

    private fun fmt(v: Float): String = ((v * 100).toInt()).toString()
}

/** Colour helpers for the 3D code (no Android classes). */
object Colors3 {
    fun lighten(c: Int, k: Float): Int = mix(c, col(0xFFFFFFFF), k)
    fun darken(c: Int, k: Float): Int = mix(c, col(0xFF000000), k)

    fun mix(a: Int, b: Int, k: Float): Int {
        fun ch(v: Int, s: Int) = (v shr s) and 0xFF
        val r = (ch(a, 16) + (ch(b, 16) - ch(a, 16)) * k).toInt()
        val g = (ch(a, 8) + (ch(b, 8) - ch(a, 8)) * k).toInt()
        val bl = (ch(a, 0) + (ch(b, 0) - ch(a, 0)) * k).toInt()
        return (0xFF shl 24) or (r shl 16) or (g shl 8) or bl
    }
}
