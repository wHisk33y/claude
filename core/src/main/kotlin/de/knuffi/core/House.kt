package de.knuffi.core

import kotlinx.serialization.Serializable
import kotlin.math.abs
import kotlin.random.Random

// ---------------------------------------------------------------------------------- rooms

/**
 * The rooms of the house on a 3 × 3 floor plan ([col], [row], [cols] cells wide).
 * Every room has a purpose: the pet eats in the kitchen, sleeps in the bedroom and so on.
 */
@Serializable
enum class RoomKind(
    val title: String,
    val emoji: String,
    val col: Int,
    val row: Int,
    val cols: Int,
    val price: Int,
    val minLevel: Int,
    val description: String,
) {
    LIVING("Wohnzimmer", "🛋️", 1, 1, 1, 0, 1, "Zum Kuscheln, Spielen und Faulenzen."),
    KITCHEN("Küche", "🍳", 2, 1, 1, 0, 1, "Hier steht der Napf. Zum Essen geht es in die Küche."),
    BEDROOM("Schlafzimmer", "🛏️", 1, 0, 1, 0, 1, "Hier steht das Bett. Zum Schlafen geht es hierher."),
    BATH("Badezimmer", "🛁", 2, 0, 1, 250, 3, "Mit großer Badewanne: Hier wird gebadet und gebürstet."),
    PLAYROOM("Spielzimmer", "🧸", 0, 1, 1, 350, 5, "Mit Rutsche und Bauklötzen. Zimmerspiele bringen hier mehr Punkte."),
    MUSIC("Musikzimmer", "🎹", 0, 0, 1, 450, 7, "Klavier, Xylophon und Trommeln zum Musizieren."),
    GARDEN("Garten", "🌳", 1, 2, 2, 600, 9, "Draußen mit Beeten, Schaukel und Planschbecken."),
    CRAFT("Bastelzimmer", "✂️", 0, 2, 1, 700, 11, "Mit Kartentisch für deine Sammelkarten."),
    ;

    val outdoor: Boolean get() = this == GARDEN
}

@Serializable
enum class DoorSide { WEST, EAST, NORTH, SOUTH;

    val opposite: DoorSide get() = when (this) {
        WEST -> EAST
        EAST -> WEST
        NORTH -> SOUTH
        SOUTH -> NORTH
    }
}

/** A door from room [from] to room [to]. [pos] is where it sits along the wall (0..1 across the room). */
data class Door(val from: RoomKind, val to: RoomKind, val side: DoorSide, val pos: Float)

/**
 * Where a piece of furniture stands in a 3D room: [x] across the room (0 left, 1 right),
 * [d] from the back wall (0) to the front (1) and [turn] in degrees. Pictures hang on the back
 * wall, for them [d] is the height (0 low, 1 high).
 */
@Serializable
data class Spot(val x: Float, val d: Float, val turn: Float = 0f)

/** Furniture of one room (and how far each piece was moved, in screen widths). */
@Serializable
data class RoomState(
    val kind: RoomKind,
    val furniture: Map<Slot, String> = emptyMap(),
    val offsets: Map<Slot, Float> = emptyMap(),
)

@Serializable
data class HouseState(
    /** Rooms already built. The furniture of the room the pet is in lives in [GameState.equipped]. */
    val rooms: List<RoomState> = emptyList(),
    val current: RoomKind = RoomKind.LIVING,
    /** True while [GameState.equipped] holds the furniture of [current] (house mode). */
    val active: Boolean = false,
    /** The single room of the classic mode, kept while the house is used. */
    val classic: Map<Slot, String> = emptyMap(),
    val classicOffsets: Map<Slot, Float> = emptyMap(),
    /** Where the pets from the Kuschelhaus are right now. */
    val residents: Map<Long, RoomKind> = emptyMap(),
    val lastWander: Long = 0,
    /** The pets from the Kuschelhaus that live in the house (the others stay in the Kuschelhaus). */
    val home: Set<Long> = emptySet(),
    /** Furniture placed by hand in the 3D rooms (missing pieces stand where they start). */
    val spots: Map<RoomKind, Map<Slot, Spot>> = emptyMap(),
    /** The same for the classic single room. */
    val classicSpots: Map<Slot, Spot> = emptyMap(),
)

object House {
    const val COLS = 3
    const val ROWS = 3
    const val WANDER_MS = 25 * 60_000L

    val START: List<RoomKind> = listOf(RoomKind.LIVING, RoomKind.KITCHEN, RoomKind.BEDROOM)

    /** The slots that belong to a room (clothes are not part of it). */
    val SLOTS: List<Slot> = listOf(Slot.ROOM, Slot.WALL, Slot.RUG, Slot.BED, Slot.PLANT, Slot.LAMP, Slot.PICTURE, Slot.TOY, Slot.DECO)

    /** Furniture that can be moved around by finger. */
    val MOVABLE: List<Slot> = listOf(Slot.RUG, Slot.PLANT, Slot.LAMP, Slot.PICTURE, Slot.TOY, Slot.DECO, Slot.BED)
    const val MAX_OFFSET = 0.3f

    fun cells(k: RoomKind): List<Pair<Int, Int>> = (0 until k.cols).map { (k.col + it) to k.row }

    fun unlocked(s: GameState): Set<RoomKind> = s.house.rooms.map { it.kind }.toSet()

    /** True when the house with several rooms is used (and not the single classic room). */
    fun enabled(s: GameState): Boolean = s.settings.houseMode && s.house.active

    fun doors(k: RoomKind, unlocked: Set<RoomKind>): List<Door> {
        val out = mutableListOf<Door>()
        for (o in RoomKind.entries) {
            if (o == k || o !in unlocked) continue
            for ((ac, ar) in cells(k)) for ((bc, br) in cells(o)) {
                val dx = bc - ac
                val dy = br - ar
                if (abs(dx) + abs(dy) != 1) continue
                val side = when {
                    dx == 1 -> DoorSide.EAST
                    dx == -1 -> DoorSide.WEST
                    dy == 1 -> DoorSide.SOUTH
                    else -> DoorSide.NORTH
                }
                if (out.none { it.to == o }) out += Door(k, o, side, (ac - k.col + 0.5f) / k.cols)
            }
        }
        return out
    }

    fun neighbors(k: RoomKind, unlocked: Set<RoomKind>): List<RoomKind> = doors(k, unlocked).map { it.to }

    /** The rooms on the way from [from] to [to] (both included), or an empty list. */
    fun path(from: RoomKind, to: RoomKind, unlocked: Set<RoomKind>): List<RoomKind> {
        if (from == to) return listOf(from)
        val prev = HashMap<RoomKind, RoomKind>()
        val queue = ArrayDeque(listOf(from))
        val seen = hashSetOf(from)
        while (queue.isNotEmpty()) {
            val r = queue.removeFirst()
            if (r == to) break
            for (n in neighbors(r, unlocked)) {
                if (seen.add(n)) {
                    prev[n] = r
                    queue.addLast(n)
                }
            }
        }
        if (to !in seen) return emptyList()
        val out = mutableListOf(to)
        var c = to
        while (c != from) {
            c = prev.getValue(c)
            out += c
        }
        return out.reversed()
    }

    /** Reason why [k] cannot be built right now, or null. */
    fun buildBlocker(s: GameState, k: RoomKind): String? {
        val un = unlocked(s)
        return when {
            !enabled(s) -> "Schalte in den Einstellungen das ganze Haus ein."
            k in un -> "${k.title} gibt es schon."
            Engine.playerLevel(s) < k.minLevel -> "${k.title} gibt es ab Level ${k.minLevel}."
            un.none { it in adjacent(k) } -> "Bau erst ein Zimmer daneben."
            s.coins < k.price -> "Dafür fehlen dir noch ${k.price - s.coins} Münzen."
            else -> null
        }
    }

    private fun adjacent(k: RoomKind): List<RoomKind> = neighbors(k, RoomKind.entries.toSet())

    /** The room an action takes the pet to (null: it stays where it is). */
    fun roomFor(action: Action, s: GameState): RoomKind? {
        if (!enabled(s)) return null
        val pet = s.pet ?: return null
        if (!pet.alive || pet.isEgg) return null
        val target = when (action) {
            is Action.Feed -> RoomKind.KITCHEN
            is Action.UseItem -> Catalog[action.itemId]?.let {
                when {
                    it.kind == ItemKind.FOOD -> RoomKind.KITCHEN
                    it.kind == ItemKind.CARE && it.effect.hygiene >= 30 -> RoomKind.BATH
                    else -> null
                }
            }
            Action.Clean -> RoomKind.BATH
            Action.ToggleSleep -> if (!pet.sleeping) RoomKind.BEDROOM else null
            else -> null
        } ?: return null
        return target.takeIf { it in unlocked(s) && it != s.house.current }
    }

    /** Furniture a new room starts with. */
    fun defaults(kind: RoomKind, look: LookStyle, base: Map<Slot, String>): Map<Slot, String> {
        val d = Catalog.defaultEquipped(look)
        fun get(slot: Slot) = base[slot] ?: d.getValue(slot)
        val otherWall = if (get(Slot.WALL) == "wall_rose") "wall_sky" else "wall_rose"
        return when (kind) {
            RoomKind.LIVING -> House.SLOTS.mapNotNull { sl -> (base[sl] ?: d[sl])?.let { sl to it } }.toMap()
            RoomKind.KITCHEN, RoomKind.BATH, RoomKind.CRAFT -> d + (Slot.WALL to otherWall) + (Slot.PICTURE to "pic_landscape")
            RoomKind.BEDROOM -> d + (Slot.WALL to get(Slot.WALL)) + (Slot.BED to get(Slot.BED))
            else -> d
        }
    }

    fun furnitureOf(equipped: Map<Slot, String>): Map<Slot, String> = equipped.filterKeys { it in SLOTS }

    private fun withFurniture(equipped: Map<Slot, String>, furniture: Map<Slot, String>): Map<Slot, String> =
        equipped.filterKeys { it !in SLOTS } + furniture

    private fun room(s: GameState, k: RoomKind): RoomState? = s.house.rooms.firstOrNull { it.kind == k }

    private fun storeCurrent(s: GameState): HouseState {
        val h = s.house
        val cur = RoomState(h.current, furnitureOf(s.equipped), s.furnitureOffsets)
        return h.copy(rooms = h.rooms.map { if (it.kind == h.current) cur else it })
    }

    /** Switches between the classic single room and the house, as the settings say. */
    fun syncMode(s: GameState): GameState {
        val wanted = s.settings.houseMode && s.onboarded
        val h = s.house
        if (wanted && !h.active) {
            val classic = furnitureOf(s.equipped)
            val rooms = h.rooms.ifEmpty {
                START.map { k -> RoomState(k, defaults(k, s.settings.look, classic), if (k == RoomKind.LIVING) s.furnitureOffsets else emptyMap()) }
            }
            val current = h.current.takeIf { c -> rooms.any { it.kind == c } } ?: RoomKind.LIVING
            val target = rooms.first { it.kind == current }
            return s.copy(
                equipped = withFurniture(s.equipped, target.furniture),
                furnitureOffsets = target.offsets,
                house = h.copy(rooms = rooms, current = current, active = true, classic = classic, classicOffsets = s.furnitureOffsets),
            )
        }
        if (!wanted && h.active) {
            val stored = storeCurrent(s)
            val classic = h.classic.ifEmpty { furnitureOf(s.equipped) }
            return s.copy(
                equipped = withFurniture(s.equipped, classic),
                furnitureOffsets = h.classicOffsets,
                house = stored.copy(active = false),
            )
        }
        return s
    }

    /** Moves the pet (and the view) into room [target]. */
    fun goTo(s: GameState, target: RoomKind): GameState {
        if (!enabled(s) || target == s.house.current) return s
        val next = room(s, target) ?: return s
        val stored = storeCurrent(s)
        return s.copy(
            equipped = withFurniture(s.equipped, next.furniture),
            furnitureOffsets = next.offsets,
            house = stored.copy(current = target),
        )
    }

    /** Builds a new room with default furniture. */
    fun build(s: GameState, k: RoomKind): GameState {
        if (k in unlocked(s)) return s
        val base = furnitureOf(s.equipped)
        return s.copy(house = s.house.copy(rooms = s.house.rooms + RoomState(k, defaults(k, s.settings.look, base))))
    }

    /** Furniture of any room (the current one is read from [GameState.equipped]). */
    fun furniture(s: GameState, k: RoomKind): Map<Slot, String> =
        if (enabled(s) && k == s.house.current) furnitureOf(s.equipped) else room(s, k)?.furniture ?: emptyMap()

    /** The pets from the Kuschelhaus that walk around in room [k]. */
    fun residentsIn(s: GameState, k: RoomKind): List<Pet> {
        if (!enabled(s)) return emptyList()
        return s.resting.filter { s.house.residents[it.id] == k }
    }

    /** The pets from the Kuschelhaus that may live in the house (chosen by the player). */
    fun mayLiveHere(s: GameState, p: Pet): Boolean = !p.isEgg && p.alive && p.id in s.house.home

    /** Gives every pet living in the house a room and lets them wander from time to time. */
    fun updateResidents(s: GameState, now: Long): GameState {
        if (!enabled(s)) return s
        val un = unlocked(s).toList().sortedBy { it.ordinal }
        if (un.isEmpty()) return s
        val visitors = listOfNotNull(s.visit, s.visit2).map { it.petId }.toSet()
        val home = s.resting.filter { (mayLiveHere(s, it) || it.id in visitors) && !it.isEgg && it.alive && s.onTrip(it.id) == null }
        val ids = home.map { it.id }.toSet()
        var res = s.house.residents.filter { (id, room) -> id in ids && room in un }
        for (p in home) if (p.id !in res) res = res + (p.id to un[Random(p.id).nextInt(un.size)])
        for (id in visitors) if (id in ids) res = res + (id to s.house.current)
        var last = s.house.lastWander
        if (last <= 0L) {
            last = now
        } else if (now - last >= WANDER_MS) {
            val bucket = now / WANDER_MS
            res = res.mapValues { (id, room) ->
                if (id in visitors) return@mapValues room
                val rnd = Random(id xor (bucket * 7919L))
                val n = neighbors(room, un.toSet())
                if (n.isNotEmpty() && rnd.nextFloat() < 0.4f) n[rnd.nextInt(n.size)] else room
            }
            last = now
        }
        if (res == s.house.residents && last == s.house.lastWander) return s
        return s.copy(house = s.house.copy(residents = res, lastWander = last))
    }

    // ------------------------------------------------------------------ 3D furniture

    /** Furniture that can be placed freely in a 3D room. */
    val PLACEABLE: List<Slot> = listOf(Slot.RUG, Slot.BED, Slot.PLANT, Slot.LAMP, Slot.PICTURE, Slot.TOY, Slot.DECO)

    /** The hand-placed furniture of a room ([room] null: the classic room). */
    fun spots(s: GameState, room: RoomKind?): Map<Slot, Spot> =
        if (room == null) s.house.classicSpots else s.house.spots[room] ?: emptyMap()

    /** The room shown at the moment (null: the classic room). */
    fun shownRoom(s: GameState): RoomKind? = if (enabled(s)) s.house.current else null

    fun place(s: GameState, room: RoomKind?, slot: Slot, spot: Spot): GameState {
        if (slot !in PLACEABLE) return s
        if (room != null && room !in unlocked(s)) return s
        val turn = ((spot.turn % 360f) + 360f) % 360f
        val clean = Spot(spot.x.coerceIn(0f, 1f), spot.d.coerceIn(0f, 1f), turn)
        val h = s.house
        return s.copy(
            house = if (room == null) {
                h.copy(classicSpots = h.classicSpots + (slot to clean))
            } else {
                h.copy(spots = h.spots + (room to ((h.spots[room] ?: emptyMap()) + (slot to clean))))
            },
        )
    }

    fun resetPlaces(s: GameState, room: RoomKind?): GameState {
        val h = s.house
        return s.copy(house = if (room == null) h.copy(classicSpots = emptyMap()) else h.copy(spots = h.spots - room))
    }

    /** Keeps everything consistent: the right mode, a sleeping pet in its bed, residents in rooms. */
    fun normalize(s: GameState, now: Long): GameState {
        var st = syncMode(s)
        if (!enabled(st)) return st
        if (st.house.current !in unlocked(st)) st = st.copy(house = st.house.copy(current = RoomKind.LIVING))
        val pet = st.pet
        if (pet != null && pet.alive && pet.sleeping && st.house.current != RoomKind.BEDROOM) st = goTo(st, RoomKind.BEDROOM)
        return updateResidents(st, now)
    }
}
