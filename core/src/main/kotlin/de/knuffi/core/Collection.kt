package de.knuffi.core

import kotlinx.serialization.Serializable
import kotlin.random.Random

// ---------------------------------------------------------------------------------- stickers

enum class StickerSet(val title: String, val emoji: String, val reward: Int) {
    TIERE("Tierfreunde", "🐾", 100),
    ESSEN("Leckereien", "🍩", 100),
    NATUR("Natur", "🌻", 100),
    WELTALL("Weltall", "🚀", 120),
    ABENTEUER("Abenteuer", "🗺️", 120),
    FESTE("Feste", "🎉", 200),
}

data class Sticker(val id: String, val name: String, val emoji: String, val set: StickerSet, val rare: Boolean = false)

object Stickers {
    val all: List<Sticker> = listOf(
        Sticker("s_dog", "Hund", "🐶", StickerSet.TIERE),
        Sticker("s_cat", "Katze", "🐱", StickerSet.TIERE),
        Sticker("s_bunny", "Hase", "🐰", StickerSet.TIERE),
        Sticker("s_fox", "Fuchs", "🦊", StickerSet.TIERE),
        Sticker("s_frog", "Frosch", "🐸", StickerSet.TIERE),
        Sticker("s_koala", "Koala", "🐨", StickerSet.TIERE),
        Sticker("s_panda", "Panda", "🐼", StickerSet.TIERE, rare = true),
        Sticker("s_lion", "Löwe", "🦁", StickerSet.TIERE, rare = true),

        Sticker("s_strawberry", "Erdbeere", "🍓", StickerSet.ESSEN),
        Sticker("s_pizza", "Pizza", "🍕", StickerSet.ESSEN),
        Sticker("s_donut", "Donut", "🍩", StickerSet.ESSEN),
        Sticker("s_pretzel", "Brezel", "🥨", StickerSet.ESSEN),
        Sticker("s_melon", "Melone", "🍉", StickerSet.ESSEN),
        Sticker("s_icecream", "Eis", "🍦", StickerSet.ESSEN),
        Sticker("s_cupcake", "Cupcake", "🧁", StickerSet.ESSEN, rare = true),
        Sticker("s_lolli", "Lolli", "🍭", StickerSet.ESSEN, rare = true),

        Sticker("s_sunflower", "Sonnenblume", "🌻", StickerSet.NATUR),
        Sticker("s_mushroom", "Pilz", "🍄", StickerSet.NATUR),
        Sticker("s_cactus", "Kaktus", "🌵", StickerSet.NATUR),
        Sticker("s_leaf", "Ahornblatt", "🍁", StickerSet.NATUR),
        Sticker("s_wave", "Welle", "🌊", StickerSet.NATUR),
        Sticker("s_snow", "Schneeflocke", "❄️", StickerSet.NATUR),
        Sticker("s_rainbow", "Regenbogen", "🌈", StickerSet.NATUR, rare = true),
        Sticker("s_moon", "Mond", "🌙", StickerSet.NATUR, rare = true),

        Sticker("s_rocket", "Rakete", "🚀", StickerSet.WELTALL),
        Sticker("s_planet", "Planet", "🪐", StickerSet.WELTALL),
        Sticker("s_star", "Stern", "⭐", StickerSet.WELTALL),
        Sticker("s_comet", "Komet", "☄️", StickerSet.WELTALL),
        Sticker("s_satellite", "Satellit", "🛰️", StickerSet.WELTALL),
        Sticker("s_ufo", "Ufo", "🛸", StickerSet.WELTALL),
        Sticker("s_alien", "Alien", "👽", StickerSet.WELTALL, rare = true),
        Sticker("s_galaxy", "Galaxie", "🌌", StickerSet.WELTALL, rare = true),

        Sticker("s_map", "Schatzkarte", "🗺️", StickerSet.ABENTEUER),
        Sticker("s_compass", "Kompass", "🧭", StickerSet.ABENTEUER),
        Sticker("s_tent", "Zelt", "⛺", StickerSet.ABENTEUER),
        Sticker("s_castle", "Burg", "🏰", StickerSet.ABENTEUER),
        Sticker("s_robot", "Roboter", "🤖", StickerSet.ABENTEUER),
        Sticker("s_dino", "Dino", "🦖", StickerSet.ABENTEUER),
        Sticker("s_dragon", "Drache", "🐉", StickerSet.ABENTEUER, rare = true),
        Sticker("s_gem", "Kristall", "💎", StickerSet.ABENTEUER, rare = true),

        Sticker("s_firework", "Feuerwerk", "🎆", StickerSet.FESTE),
        Sticker("s_heart", "Herz", "💝", StickerSet.FESTE),
        Sticker("s_chick", "Küken", "🐣", StickerSet.FESTE),
        Sticker("s_beach", "Strandtag", "🏖️", StickerSet.FESTE),
        Sticker("s_pumpkin", "Kürbis", "🎃", StickerSet.FESTE),
        Sticker("s_ghost", "Gespenst", "👻", StickerSet.FESTE),
        Sticker("s_tree", "Tannenbaum", "🎄", StickerSet.FESTE),
        Sticker("s_gift", "Geschenk", "🎁", StickerSet.FESTE),
    )

    private val byId = all.associateBy { it.id }
    operator fun get(id: String): Sticker? = byId[id]

    fun of(set: StickerSet) = all.filter { it.set == set }

    /** The festival sticker that belongs to an event. */
    fun forEvent(e: SeasonEvent): List<Sticker> = when (e) {
        SeasonEvent.NEUJAHR -> listOf(byId.getValue("s_firework"), byId.getValue("s_star"))
        SeasonEvent.VALENTIN -> listOf(byId.getValue("s_heart"))
        SeasonEvent.OSTERN -> listOf(byId.getValue("s_chick"))
        SeasonEvent.SOMMERFEST -> listOf(byId.getValue("s_beach"))
        SeasonEvent.HALLOWEEN -> listOf(byId.getValue("s_pumpkin"), byId.getValue("s_ghost"))
        SeasonEvent.WINTERZAUBER -> listOf(byId.getValue("s_tree"), byId.getValue("s_gift"))
    }

    /** Random sticker from the normal sets; rare ones appear less often. */
    fun roll(rnd: Random): Sticker {
        val pool = all.filter { it.set != StickerSet.FESTE }
        while (true) {
            val s = pool[rnd.nextInt(pool.size)]
            if (!s.rare || rnd.nextFloat() < 0.3f) return s
        }
    }

    fun complete(set: StickerSet, owned: Map<String, Int>): Boolean = of(set).all { (owned[it.id] ?: 0) > 0 }
}

// ---------------------------------------------------------------------------------- trips

@Serializable
enum class Destination(
    val title: String,
    val emoji: String,
    val minLevel: Int,
    val hours: Int,
    val egg: EggLine,
    val description: String,
) {
    WIESE("Blumenwiese", "🌼", 1, 1, EggLine.BLUETE, "Schmetterlinge jagen und Blumen pflücken."),
    STRAND("Sonnenstrand", "🏖️", 3, 2, EggLine.MEER, "Sandburgen bauen und Muscheln sammeln."),
    ZAUBERWALD("Zauberwald", "🌲", 5, 3, EggLine.WALD, "Zwischen alten Bäumen wohnen die Waldgeister."),
    BERGE("Gipfeltour", "🏔️", 8, 4, EggLine.URZEIT, "Hoch hinaus! Oben gibt es Fossilien."),
    HOEHLE("Kristallhöhle", "💎", 11, 6, EggLine.TECHNO, "Glitzernde Kristalle und geheimnisvolle Maschinen."),
    VULKAN("Vulkaninsel", "🌋", 14, 8, EggLine.FEUER, "Heiß, heißer, Vulkan!"),
    WOLKEN("Wolkenschloss", "☁️", 17, 10, EggLine.EINHORN, "Ein Schloss auf Wolken, mit Regenbogenbrücke."),
    MOND("Mondreise", "🌙", 20, 12, EggLine.STERN, "Einmal zum Mond und zurück!"),
    ;

    val durationMs: Long get() = hours * 3_600_000L
}

@Serializable
data class Trip(
    val petId: Long,
    val destination: Destination,
    val startedAt: Long,
    val endsAt: Long,
)

/** What a pet brings home. Generated deterministically from the trip so it never changes. */
data class TripLoot(
    val coins: Int,
    val items: Map<String, Int>,
    val stickers: List<String>,
    val egg: EggLine?,
)

object Trips {
    fun loot(trip: Trip): TripLoot {
        val d = trip.destination
        val rnd = Random(trip.startedAt xor (trip.petId * 31L) xor d.ordinal.toLong())
        val coins = 12 * d.hours + rnd.nextInt(0, 10 * d.hours + 1)
        val items = mutableMapOf<String, Int>()
        val seeds = Catalog.seeds.filter { it.id != Catalog.MAGIC_SEED }
        repeat(1 + rnd.nextInt(0, d.hours / 3 + 1)) {
            val seed = seeds[rnd.nextInt(seeds.size)]
            items[seed.id] = (items[seed.id] ?: 0) + 1
        }
        val foods = Catalog.foods.filter { !it.unlimited && it.sold }
        val food = foods[rnd.nextInt(foods.size)]
        items[food.id] = (items[food.id] ?: 0) + 1
        if (rnd.nextFloat() < 0.04f + 0.01f * d.hours) items[Catalog.MAGIC_SEED] = 1
        val stickers = mutableListOf<String>()
        val stickerChance = (0.4f + 0.05f * d.hours).coerceAtMost(0.9f)
        if (rnd.nextFloat() < stickerChance) stickers += Stickers.roll(rnd).id
        if (d.hours >= 6 && rnd.nextFloat() < 0.5f) stickers += Stickers.roll(rnd).id
        val egg = if (rnd.nextFloat() < 0.02f + 0.01f * d.hours) d.egg else null
        return TripLoot(coins, items, stickers, egg)
    }
}

// ---------------------------------------------------------------------------------- garden

@Serializable
data class Plot(
    val seed: String? = null,
    val plantedAt: Long = 0,
    val readyAt: Long = 0,
    val waterings: Int = 0,
    val lastWatered: Long = 0,
) {
    val empty: Boolean get() = seed == null
    fun ready(now: Long): Boolean = seed != null && now >= readyAt
    fun progress(now: Long): Float =
        if (seed == null || readyAt <= plantedAt) 0f else ((now - plantedAt).toFloat() / (readyAt - plantedAt)).coerceIn(0f, 1f)
}

@Serializable
data class Garden(val plots: List<Plot> = List(Garden.START_PLOTS) { Plot() }) {
    companion object {
        const val START_PLOTS = 3
        const val MAX_PLOTS = 9
        const val MAX_WATERINGS = 3
        const val WATER_COOLDOWN_MS = 20 * 60_000L

        fun plotPrice(current: Int): Int = 80 * current
    }
}

// ---------------------------------------------------------------------------------- season pass

data class PassReward(
    val coins: Int = 0,
    val itemId: String? = null,
    val count: Int = 1,
    val egg: Boolean = false,
) {
    val label: String
        get() = when {
            egg -> "Überraschungs-Ei"
            itemId != null -> "${Catalog[itemId]?.name ?: itemId}${if (count > 1) " ×$count" else ""}"
            else -> "$coins Münzen"
        }
    val emoji: String
        get() = when {
            egg -> "🥚"
            itemId != null -> Catalog[itemId]?.emoji ?: "🎁"
            else -> "🪙"
        }
}

object SeasonPass {
    const val TIERS = 40
    const val XP_PER_TIER = 150

    /** Seasonal exclusives: tier 20 and tier 40. */
    fun exclusives(season: Season): Pair<String, String> = when (season) {
        Season.SPRING -> "hat_flowercrown" to "neck_flowerchain"
        Season.SUMMER -> "hat_straw" to "face_snorkel"
        Season.AUTUMN -> "hat_acorn" to "neck_leafscarf"
        Season.WINTER -> "hat_bobble" to "neck_starscarf"
    }

    fun reward(season: Season, tier: Int): PassReward {
        val (a, b) = exclusives(season)
        return when {
            tier == 20 -> PassReward(itemId = a)
            tier == TIERS -> PassReward(itemId = b)
            tier == 10 || tier == 30 -> PassReward(egg = true)
            tier % 5 == 0 -> PassReward(itemId = Catalog.STICKER_PACK)
            tier % 4 == 2 -> PassReward(itemId = Catalog.SEED_PACK)
            tier % 7 == 3 -> PassReward(itemId = "cake")
            else -> PassReward(coins = 20 + tier * 2)
        }
    }

    fun tier(xp: Int): Int = (xp / XP_PER_TIER).coerceAtMost(TIERS)
}

@Serializable
data class PassState(
    val id: String = "",
    val xp: Int = 0,
    val claimed: Set<Int> = emptySet(),
)

@Serializable
data class EventState(
    val key: String = "",
    val tokens: Int = 0,
    val bought: Map<String, Int> = emptyMap(),
)

/** What can be bought with event tokens. */
data class EventOffer(val id: String, val price: Int, val limit: Int, val egg: EggLine? = null, val sticker: String? = null)

object EventShop {
    fun offers(e: SeasonEvent): List<EventOffer> {
        val list = mutableListOf<EventOffer>()
        list += EventOffer("egg_${e.egg.name.lowercase()}", 40, 2, egg = e.egg)
        list += eventItems(e).map { EventOffer(it, if (Catalog[it]?.cosmetic == true) 25 else 6, if (Catalog[it]?.cosmetic == true) 1 else 10) }
        list += Stickers.forEvent(e).map { EventOffer("sticker_${it.id}", 10, 1, sticker = it.id) }
        return list
    }

    fun eventItems(e: SeasonEvent): List<String> = when (e) {
        SeasonEvent.NEUJAHR -> listOf("face_stars", "cookie_luck")
        SeasonEvent.VALENTIN -> listOf("neck_heart", "choco_heart")
        SeasonEvent.OSTERN -> listOf("hat_bunnyears", "choco_egg")
        SeasonEvent.SOMMERFEST -> listOf("hat_captain", "popsicle")
        SeasonEvent.HALLOWEEN -> listOf("hat_witch", "pumpkin_pie")
        SeasonEvent.WINTERZAUBER -> listOf("hat_santa", "gingerbread")
    }
}
