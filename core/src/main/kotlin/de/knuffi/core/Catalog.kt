package de.knuffi.core

enum class ItemKind(val title: String, val slot: Slot?) {
    FOOD("Essen", null),
    CARE("Pflege", null),
    SEED("Samen", null),
    EGG("Ei", null),
    HAT("Kopf", Slot.HAT),
    FACE("Gesicht", Slot.FACE),
    NECK("Hals", Slot.NECK),
    ROOM("Zimmer", Slot.ROOM),
    WALL("Tapete", Slot.WALL),
    RUG("Teppich", Slot.RUG),
    BED("Bett", Slot.BED),
    PLANT("Pflanze", Slot.PLANT),
    LAMP("Lampe", Slot.LAMP),
    PICTURE("Bild", Slot.PICTURE),
    ;

    val wearable: Boolean get() = this == HAT || this == FACE || this == NECK
    val furniture: Boolean get() = slot != null && !wearable && this != ROOM
}

data class Effect(
    val satiety: Int = 0,
    val joy: Int = 0,
    val energy: Int = 0,
    val hygiene: Int = 0,
    val health: Int = 0,
    val cures: Boolean = false,
)

data class Item(
    val id: String,
    val name: String,
    val emoji: String,
    val kind: ItemKind,
    val price: Int,
    val description: String,
    val minLevel: Int = 1,
    val effect: Effect = Effect(),
    val snack: Boolean = false,
    /** Can be bought in the normal shop (otherwise: harvest, events, season pass, trips). */
    val sold: Boolean = true,
    /** Never runs out (the basic food). */
    val unlimited: Boolean = false,
    val growHours: Int = 0,
    val yieldId: String? = null,
    val yieldCount: Int = 0,
    val season: Season? = null,
    val line: EggLine? = null,
    val look: LookStyle? = null,
) {
    val consumable: Boolean get() = kind == ItemKind.FOOD || kind == ItemKind.CARE
    val stackable: Boolean get() = consumable || kind == ItemKind.SEED
    val cosmetic: Boolean get() = kind.slot != null
}

object Catalog {
    const val BASIC_FOOD = "rice"
    const val MEDICINE = "medicine"
    const val DEFAULT_ROOM = "room_cozy"
    const val STICKER_PACK = "sticker_pack"
    const val SEED_PACK = "seed_pack"
    const val GLITTER = "glitter"
    const val MAGIC_SEED = "seed_magic"
    const val START_COINS = 50

    private fun food(id: String, name: String, emoji: String, price: Int, desc: String, effect: Effect, minLevel: Int = 1, snack: Boolean = false, sold: Boolean = true) =
        Item(id, name, emoji, ItemKind.FOOD, price, desc, minLevel, effect, snack, sold)

    private fun seed(id: String, name: String, emoji: String, price: Int, hours: Int, yieldId: String, count: Int, season: Season?, sold: Boolean = true) =
        Item(id, name, emoji, ItemKind.SEED, price, "Wächst in $hours Stunden${if (season != null) " (im ${season.title} schneller)" else ""}.", growHours = hours, yieldId = yieldId, yieldCount = count, season = season, sold = sold)

    private fun egg(line: EggLine, price: Int, minLevel: Int, sold: Boolean = true) =
        Item("egg_${line.name.lowercase()}", line.title, line.emoji, ItemKind.EGG, price, line.description, minLevel, sold = sold, line = line, look = line.look)

    private fun wear(id: String, name: String, emoji: String, kind: ItemKind, price: Int, desc: String, minLevel: Int = 1, sold: Boolean = true, look: LookStyle? = null) =
        Item(id, name, emoji, kind, price, desc, minLevel, sold = sold, look = look)

    val items: List<Item> = listOf(
        // Essen
        food("rice", "Reisbällchen", "🍙", 0, "Einfach, aber sättigend. Immer verfügbar.", Effect(satiety = 20)).copy(unlimited = true),
        food("apple", "Apfel", "🍎", 6, "Knackig und gesund.", Effect(satiety = 12, health = 5)),
        food("salad", "Salat", "🥗", 10, "Viele Vitamine für starke Abwehrkräfte.", Effect(satiety = 22, health = 8)),
        food("candy", "Bonbon", "🍬", 4, "Süß! Macht gute Laune.", Effect(satiety = 3, joy = 8, health = -1), snack = true),
        food("icecream", "Eis", "🍦", 9, "Kalt, cremig, glücklich.", Effect(satiety = 6, joy = 15), snack = true),
        food("pancakes", "Pfannkuchen", "🥞", 12, "Mit Ahornsirup. Mmmh!", Effect(satiety = 20, joy = 10), minLevel = 2),
        food("burger", "Burger", "🍔", 14, "Macht richtig satt, ist aber nicht so gesund.", Effect(satiety = 40, joy = 6, health = -3), snack = true),
        food("noodles", "Nudeln", "🍝", 14, "Ein großer Teller voller Energie.", Effect(satiety = 32, energy = 6), minLevel = 3),
        food("cake", "Törtchen", "🍰", 16, "Das Highlight des Tages!", Effect(satiety = 12, joy = 22, health = -2), snack = true),
        food("pizza", "Pizza", "🍕", 18, "Käse, Käse, Käse.", Effect(satiety = 38, joy = 12, health = -2), minLevel = 3, snack = true),
        food("sushi", "Sushi", "🍣", 22, "Feinschmecker-Essen mit allem, was gut tut.", Effect(satiety = 32, joy = 10, health = 6), minLevel = 5),
        // Ernte aus dem Garten
        food("carrot", "Karotte", "🥕", 0, "Frisch aus dem eigenen Garten.", Effect(satiety = 14, health = 6), sold = false),
        food("strawberry", "Erdbeere", "🍓", 0, "Süß und rot, selbst gepflückt.", Effect(satiety = 8, joy = 10, health = 3), sold = false),
        food("blueberry", "Blaubeeren", "🫐", 0, "Kleine Vitaminbomben.", Effect(satiety = 8, health = 10), sold = false),
        food("melon", "Melone", "🍉", 0, "Saftig und erfrischend.", Effect(satiety = 16, joy = 12), sold = false),
        food("pumpkin", "Kürbis", "🎃", 0, "Wird zu einer warmen Suppe.", Effect(satiety = 28, health = 6), sold = false),
        food("seeds", "Sonnenblumenkerne", "🌻", 0, "Ein knuspriger Snack.", Effect(satiety = 5, joy = 6), snack = true, sold = false),
        // Festtags-Leckereien
        food("cookie_luck", "Glückskeks", "🥠", 0, "Was wohl drinsteht?", Effect(satiety = 6, joy = 18), snack = true, sold = false),
        food("choco_heart", "Schokoherz", "🍫", 0, "Mit ganz viel Liebe gemacht.", Effect(satiety = 6, joy = 20), snack = true, sold = false),
        food("choco_egg", "Schoko-Ei", "🥚", 0, "Innen ist eine Überraschung!", Effect(satiety = 8, joy = 18), snack = true, sold = false),
        food("popsicle", "Wassereis", "🍧", 0, "Perfekt für heiße Tage.", Effect(satiety = 4, joy = 16, energy = 6), snack = true, sold = false),
        food("pumpkin_pie", "Kürbiskuchen", "🥧", 0, "Gewürzt mit Zimt.", Effect(satiety = 20, joy = 14), sold = false),
        food("gingerbread", "Lebkuchen", "🍪", 0, "Duftet nach Weihnachten.", Effect(satiety = 12, joy = 16), snack = true, sold = false),
        // Pflege
        Item("medicine", "Medizin", "💊", ItemKind.CARE, 20, "Heilt Krankheiten sofort.", effect = Effect(health = 30, cures = true)),
        Item("cocoa", "Kakao", "☕", ItemKind.CARE, 12, "Warmer Energieschub.", minLevel = 2, effect = Effect(energy = 30, joy = 5)),
        Item("juice", "Vitaminsaft", "🧃", ItemKind.CARE, 15, "Stärkt die Gesundheit.", minLevel = 4, effect = Effect(health = 20, satiety = 5)),
        Item("bath", "Schaumbad", "🛁", ItemKind.CARE, 15, "Blubbernder Badespaß: sauber und glücklich.", minLevel = 2, effect = Effect(hygiene = 100, joy = 12)),
        Item(STICKER_PACK, "Stickertüte", "🎴", ItemKind.CARE, 40, "Drei zufällige Sticker für dein Album."),
        Item(SEED_PACK, "Samentüte", "🌱", ItemKind.CARE, 20, "Drei zufällige Samen für deinen Garten."),
        Item(GLITTER, "Glitzerstaub", "✨", ItemKind.CARE, 0, "Streu ihn auf ein Ei: Es schlüpft garantiert schillernd!", sold = false),
        // Samen
        seed("seed_carrot", "Karottensamen", "🥕", 5, 2, "carrot", 2, Season.SPRING),
        seed("seed_strawberry", "Erdbeersamen", "🍓", 8, 4, "strawberry", 3, Season.SUMMER),
        seed("seed_blueberry", "Blaubeersamen", "🫐", 10, 5, "blueberry", 3, Season.SUMMER),
        seed("seed_sunflower", "Sonnenblumensamen", "🌻", 9, 6, "seeds", 4, Season.SUMMER),
        seed("seed_pumpkin", "Kürbissamen", "🎃", 12, 8, "pumpkin", 2, Season.AUTUMN),
        seed("seed_melon", "Melonensamen", "🍉", 14, 8, "melon", 2, Season.SUMMER),
        seed(MAGIC_SEED, "Zauberblumen-Samen", "🌺", 0, 24, GLITTER, 1, null, sold = false),
        // Eier
        egg(EggLine.KNUFFEL, 350, 3),
        egg(EggLine.WALD, 500, 5),
        egg(EggLine.MEER, 500, 5),
        egg(EggLine.FEUER, 600, 6),
        egg(EggLine.URZEIT, 600, 6),
        egg(EggLine.TECHNO, 600, 6),
        egg(EggLine.EINHORN, 600, 6),
        egg(EggLine.BLUETE, 600, 6),
        egg(EggLine.STERN, 700, 8),
        egg(EggLine.ZUCKER, 700, 8),
        egg(EggLine.FROST, 0, 1, sold = false),
        egg(EggLine.GRUSEL, 0, 1, sold = false),
        // Kopf
        wear("hat_bow", "Schleife", "🎀", ItemKind.HAT, 40, "Eine niedliche Schleife."),
        wear("hat_flower", "Blümchen", "🌸", ItemKind.HAT, 35, "Frisch gepflückt."),
        wear("hat_party", "Partyhut", "🥳", ItemKind.HAT, 50, "Jeder Tag ist ein Fest!"),
        wear("hat_cap", "Kappe", "🧢", ItemKind.HAT, 60, "Sportlich und lässig.", 3),
        wear("hat_explorer", "Entdeckerhut", "🤠", ItemKind.HAT, 80, "Für echte Abenteurer.", 4, look = LookStyle.ABENTEUER),
        wear("hat_tiara", "Diadem", "👸", ItemKind.HAT, 90, "Funkelt wie tausend Sterne.", 4, look = LookStyle.ZAUBER),
        wear("hat_pirate", "Piratenhut", "🏴‍☠️", ItemKind.HAT, 110, "Ahoi, ihr Landratten!", 5, look = LookStyle.ABENTEUER),
        wear("hat_tophat", "Zylinder", "🎩", ItemKind.HAT, 120, "Sehr vornehm.", 6),
        wear("hat_wizard", "Zauberhut", "🧙", ItemKind.HAT, 160, "Mit echtem Sternenstaub.", 8),
        wear("hat_crown", "Krone", "👑", ItemKind.HAT, 300, "Für wahre Royals.", 12),
        wear("hat_flowercrown", "Blütenkranz", "💐", ItemKind.HAT, 0, "Nur im Frühlings-Pass.", sold = false),
        wear("hat_straw", "Strohhut", "👒", ItemKind.HAT, 0, "Nur im Sommer-Pass.", sold = false),
        wear("hat_acorn", "Eichelmütze", "🌰", ItemKind.HAT, 0, "Nur im Herbst-Pass.", sold = false),
        wear("hat_bobble", "Bommelmütze", "🧶", ItemKind.HAT, 0, "Nur im Winter-Pass.", sold = false),
        wear("hat_bunnyears", "Hasenohren", "🐰", ItemKind.HAT, 0, "Nur zum Osterfest.", sold = false),
        wear("hat_captain", "Kapitänsmütze", "⚓", ItemKind.HAT, 0, "Nur zum Sommerfest.", sold = false),
        wear("hat_witch", "Hexenhut", "🧹", ItemKind.HAT, 0, "Nur zu Halloween.", sold = false),
        wear("hat_santa", "Weihnachtsmütze", "🎅", ItemKind.HAT, 0, "Nur zum Winterzauber.", sold = false),
        // Gesicht
        wear("face_round", "Nerdbrille", "👓", ItemKind.FACE, 45, "Sieht klug aus."),
        wear("face_sun", "Sonnenbrille", "😎", ItemKind.FACE, 70, "Einfach cool.", 3),
        wear("face_heart", "Herzbrille", "💖", ItemKind.FACE, 90, "Die Welt in Rosa.", 5),
        wear("face_snorkel", "Taucherbrille", "🤿", ItemKind.FACE, 0, "Nur im Sommer-Pass.", sold = false),
        wear("face_stars", "Sternenbrille", "🤩", ItemKind.FACE, 0, "Nur zum Neujahrsfest.", sold = false),
        // Hals
        wear("neck_bowtie", "Fliege", "👔", ItemKind.NECK, 50, "Schick für jeden Anlass."),
        wear("neck_scarf", "Schal", "🧣", ItemKind.NECK, 55, "Kuschelig warm.", 2),
        wear("neck_bell", "Glöckchen", "🔔", ItemKind.NECK, 65, "Bimmelt bei jedem Hüpfer.", 4),
        wear("neck_cape", "Heldenumhang", "🦸", ItemKind.NECK, 120, "Für kleine Superhelden.", 6),
        wear("neck_flowerchain", "Blumenkette", "🌼", ItemKind.NECK, 0, "Nur im Frühlings-Pass.", sold = false),
        wear("neck_leafscarf", "Laubschal", "🍂", ItemKind.NECK, 0, "Nur im Herbst-Pass.", sold = false),
        wear("neck_starscarf", "Sternenschal", "🌟", ItemKind.NECK, 0, "Nur im Winter-Pass.", sold = false),
        wear("neck_heart", "Herzmedaillon", "💗", ItemKind.NECK, 0, "Nur zur Herzchenwoche.", sold = false),
        // Zimmer
        Item("room_cozy", "Kinderzimmer", "🏠", ItemKind.ROOM, 0, "Das erste Zuhause. Mit Möbeln einrichtbar."),
        Item("room_forest", "Zauberwald", "🌳", ItemKind.ROOM, 150, "Glühwürmchen und raschelnde Blätter.", minLevel = 3),
        Item("room_ocean", "Unterwasserwelt", "🐠", ItemKind.ROOM, 180, "Blubb, blubb!", minLevel = 4),
        Item("room_space", "Weltall", "🚀", ItemKind.ROOM, 220, "Schwerelos zwischen den Sternen.", minLevel = 6),
        Item("room_candy", "Candyland", "🍭", ItemKind.ROOM, 250, "Alles aus Zucker!", minLevel = 8),
        // Möbel für das Kinderzimmer
        wear("wall_rose", "Rosentapete", "🌸", ItemKind.WALL, 0, "Zarte Streifen und Rosenpunkte."),
        wear("wall_sky", "Himmelstapete", "☁️", ItemKind.WALL, 0, "Hellblau mit kleinen Wolken."),
        wear("wall_mint", "Minztapete", "🌿", ItemKind.WALL, 60, "Frisch wie ein Frühlingsmorgen.", 2),
        wear("wall_stars", "Sternentapete", "✨", ItemKind.WALL, 90, "Ein Himmel voller Sterne.", 4),
        wear("wall_wood", "Holzvertäfelung", "🪵", ItemKind.WALL, 90, "Gemütlich wie in einer Hütte.", 4),
        wear("rug_round", "Kuschelteppich", "⭕", ItemKind.RUG, 0, "Rund und flauschig."),
        wear("rug_rainbow", "Regenbogenteppich", "🌈", ItemKind.RUG, 70, "In allen Farben.", 2),
        wear("rug_star", "Sternteppich", "⭐", ItemKind.RUG, 70, "Für kleine Sterne.", 3),
        wear("rug_leaf", "Blätterteppich", "🍃", ItemKind.RUG, 70, "Wie eine Waldlichtung.", 3),
        wear("bed_classic", "Holzbett", "🛏️", ItemKind.BED, 0, "Ein gemütliches Bett."),
        wear("bed_cloud", "Wolkenbett", "☁️", ItemKind.BED, 150, "Schlafen wie auf Wolken.", 4),
        wear("bed_race", "Rennauto-Bett", "🏎️", ItemKind.BED, 150, "Brumm brumm, gute Nacht!", 4, look = LookStyle.ABENTEUER),
        wear("bed_canopy", "Himmelbett", "👑", ItemKind.BED, 180, "Mit zartem Vorhang.", 5, look = LookStyle.ZAUBER),
        wear("plant_monstera", "Monstera", "🪴", ItemKind.PLANT, 0, "Große grüne Blätter."),
        wear("plant_cactus", "Kaktus", "🌵", ItemKind.PLANT, 50, "Pflegeleicht und stachelig.", 2),
        wear("plant_sunflower", "Sonnenblume", "🌻", ItemKind.PLANT, 60, "Dreht sich zur Sonne.", 3),
        wear("plant_bonsai", "Bonsai", "🌳", ItemKind.PLANT, 80, "Ein Baum im Mini-Format.", 5),
        wear("lamp_floor", "Stehlampe", "💡", ItemKind.LAMP, 0, "Warmes Licht."),
        wear("lamp_mushroom", "Pilzlampe", "🍄", ItemKind.LAMP, 70, "Leuchtet wie ein Zauberpilz.", 3),
        wear("lamp_moon", "Mondlampe", "🌙", ItemKind.LAMP, 80, "Ein kleiner Mond fürs Zimmer.", 4),
        wear("lamp_lava", "Lavalampe", "🫧", ItemKind.LAMP, 90, "Blubbernde Farbkleckse.", 5),
        wear("pic_landscape", "Landschaftsbild", "🖼️", ItemKind.PICTURE, 0, "Grüne Hügel und Sonne."),
        wear("pic_dragon", "Drachenbild", "🐉", ItemKind.PICTURE, 60, "Ein mutiger Drache.", 2),
        wear("pic_unicorn", "Einhornbild", "🦄", ItemKind.PICTURE, 60, "Ein Einhorn im Regenbogen.", 2),
        wear("pic_rocket", "Raketenbild", "🚀", ItemKind.PICTURE, 60, "Auf zu den Sternen!", 3),
        wear("pic_heart", "Herzbild", "💖", ItemKind.PICTURE, 60, "Voller Liebe.", 3),
    )

    private val byId = items.associateBy { it.id }

    operator fun get(id: String): Item? = byId[id]

    fun of(kind: ItemKind): List<Item> = items.filter { it.kind == kind }

    fun eggItem(line: EggLine): Item = byId.getValue("egg_${line.name.lowercase()}")

    val foods: List<Item> get() = of(ItemKind.FOOD)
    val careItems: List<Item> get() = of(ItemKind.CARE)
    val seeds: List<Item> get() = of(ItemKind.SEED)
    val accessories: List<Item> get() = items.filter { it.kind.wearable }
    val rooms: List<Item> get() = of(ItemKind.ROOM)
    val furniture: List<Item> get() = items.filter { it.kind.furniture }

    val startInventory: Map<String, Int> = mapOf("apple" to 3, "candy" to 2, MEDICINE to 1, "seed_carrot" to 2, "seed_strawberry" to 1)

    /** Free furniture everybody owns from the start. */
    val defaultFurniture: Set<String> = setOf("wall_rose", "wall_sky", "rug_round", "bed_classic", "plant_monstera", "lamp_floor", "pic_landscape", "pic_dragon", "pic_unicorn")

    fun defaultEquipped(look: LookStyle): Map<Slot, String> = mapOf(
        Slot.ROOM to DEFAULT_ROOM,
        Slot.WALL to if (look == LookStyle.ABENTEUER) "wall_sky" else "wall_rose",
        Slot.RUG to "rug_round",
        Slot.BED to "bed_classic",
        Slot.PLANT to "plant_monstera",
        Slot.LAMP to "lamp_floor",
        Slot.PICTURE to if (look == LookStyle.ABENTEUER) "pic_dragon" else "pic_unicorn",
    )

    /** Growing time of a seed; faster in its season. */
    fun growMs(seed: Item, season: Season): Long {
        val base = seed.growHours * 3_600_000L
        return if (seed.season == season) (base * 0.6).toLong() else base
    }
}
