package de.knuffi.app.render.house3d

import de.knuffi.app.render.Furnishing
import de.knuffi.core.Catalog
import de.knuffi.core.EventCalendar
import de.knuffi.core.GameState
import de.knuffi.core.House
import de.knuffi.core.RoomKind
import de.knuffi.core.SeasonEvent
import de.knuffi.core.Slot

/** Turns the game state into [RoomSpec]s for the 3D rooms. */
object Specs3 {
    /** The room shown on the home screen, from its [Furnishing]. */
    fun home(state: GameState, f: Furnishing): RoomSpec {
        val kind = House.shownRoom(state)
        val furniture = buildMap {
            put(Slot.RUG, f.rug)
            put(Slot.BED, f.bed)
            put(Slot.PLANT, f.plant)
            put(Slot.LAMP, f.lamp)
            put(Slot.PICTURE, f.picture)
            f.toy?.let { put(Slot.TOY, it) }
            f.deco?.let { put(Slot.DECO, it) }
        }
        return RoomSpec(
            kind = kind,
            theme = if (kind == RoomKind.GARDEN) "room_cozy" else state.room,
            wall = f.wall,
            furniture = furniture,
            spots = House.spots(state, kind),
            doors = f.doors,
            showBed = f.hasBed,
            plants = f.plants,
            season = if (kind == RoomKind.GARDEN) f.season else null,
            trophies = f.trophies,
            event = f.event?.name,
        )
    }

    /** Any room of the house (for the overview), [kind] null: the classic room. */
    fun room(state: GameState, kind: RoomKind?, event: SeasonEvent?): RoomSpec {
        if (kind == null || kind == House.shownRoom(state)) return home(state, Furnishing.of(state, event))
        val k: RoomKind = kind
        val d = Catalog.defaultEquipped(state.settings.look)
        val stored = House.furniture(state, k)
        fun get(slot: Slot) = stored[slot] ?: d[slot]
        val furniture = buildMap {
            for (slot in listOf(Slot.RUG, Slot.BED, Slot.PLANT, Slot.LAMP, Slot.PICTURE, Slot.TOY, Slot.DECO)) get(slot)?.let { put(slot, it) }
        }
        val now = System.currentTimeMillis()
        val plants = if (k == RoomKind.GARDEN) {
            state.garden.plots.map { p ->
                val seed = p.seed?.let { Catalog[it] }
                if (seed == null) "" else "${seed.yieldId?.let { Catalog[it]?.emoji } ?: seed.emoji}|${(p.progress(now) * 4).toInt() / 4f}|${if (p.ready(now)) 1 else 0}"
            }
        } else {
            emptyList()
        }
        return RoomSpec(
            kind = k,
            theme = if (k == RoomKind.GARDEN) "room_cozy" else get(Slot.ROOM) ?: "room_cozy",
            wall = get(Slot.WALL) ?: "wall_rose",
            furniture = furniture,
            spots = House.spots(state, k),
            doors = House.doors(k, House.unlocked(state)),
            showBed = k == RoomKind.BEDROOM,
            plants = plants,
            season = if (k == RoomKind.GARDEN) EventCalendar.season(java.time.LocalDate.now()) else null,
            trophies = (state.counters.prizes + state.story.finished.size + state.findSetsDone.size + state.cups.size).coerceAtMost(8),
            event = event?.name,
        )
    }
}
