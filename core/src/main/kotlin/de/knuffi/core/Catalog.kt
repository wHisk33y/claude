package de.knuffi.core

enum class ItemKind(val title: String, val slot: Slot?) {
    FOOD("Essen", null),
    CARE("Pflege", null),
    HAT("Kopf", Slot.HAT),
    FACE("Gesicht", Slot.FACE),
    NECK("Hals", Slot.NECK),
    ROOM("Zimmer", Slot.ROOM),
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
) {
    val consumable: Boolean get() = kind == ItemKind.FOOD || kind == ItemKind.CARE
    val cosmetic: Boolean get() = kind.slot != null
    val unlimited: Boolean get() = consumable && price == 0
}

object Catalog {
    const val BASIC_FOOD = "rice"
    const val MEDICINE = "medicine"
    const val DEFAULT_ROOM = "room_cozy"

    val items: List<Item> = listOf(
        // Essen
        Item("rice", "Reisbällchen", "🍙", ItemKind.FOOD, 0, "Einfach, aber sättigend. Immer verfügbar.", effect = Effect(satiety = 20)),
        Item("apple", "Apfel", "🍎", ItemKind.FOOD, 6, "Knackig und gesund.", effect = Effect(satiety = 12, health = 5)),
        Item("salad", "Salat", "🥗", ItemKind.FOOD, 10, "Viele Vitamine für starke Abwehrkräfte.", effect = Effect(satiety = 22, health = 8)),
        Item("candy", "Bonbon", "🍬", ItemKind.FOOD, 4, "Süß! Macht gute Laune.", effect = Effect(satiety = 3, joy = 8, health = -1), snack = true),
        Item("icecream", "Eis", "🍦", ItemKind.FOOD, 9, "Kalt, cremig, glücklich.", effect = Effect(satiety = 6, joy = 15), snack = true),
        Item("burger", "Burger", "🍔", ItemKind.FOOD, 14, "Macht richtig satt, ist aber nicht so gesund.", effect = Effect(satiety = 40, joy = 6, health = -3), snack = true),
        Item("cake", "Törtchen", "🍰", ItemKind.FOOD, 16, "Das Highlight des Tages!", effect = Effect(satiety = 12, joy = 22, health = -2), snack = true),
        Item("pizza", "Pizza", "🍕", ItemKind.FOOD, 18, "Käse, Käse, Käse.", minLevel = 3, effect = Effect(satiety = 38, joy = 12, health = -2), snack = true),
        Item("sushi", "Sushi", "🍣", ItemKind.FOOD, 22, "Feinschmecker-Essen mit allem, was gut tut.", minLevel = 5, effect = Effect(satiety = 32, joy = 10, health = 6)),
        // Pflege
        Item("medicine", "Medizin", "💊", ItemKind.CARE, 20, "Heilt Krankheiten sofort.", effect = Effect(health = 30, cures = true)),
        Item("cocoa", "Kakao", "☕", ItemKind.CARE, 12, "Warmer Energieschub.", minLevel = 2, effect = Effect(energy = 30, joy = 5)),
        Item("juice", "Vitaminsaft", "🧃", ItemKind.CARE, 15, "Stärkt die Gesundheit.", minLevel = 4, effect = Effect(health = 20, satiety = 5)),
        Item("bath", "Schaumbad", "🛁", ItemKind.CARE, 15, "Blubbernder Badespaß: sauber und glücklich.", minLevel = 2, effect = Effect(hygiene = 100, joy = 12)),
        // Kopf
        Item("hat_bow", "Schleife", "🎀", ItemKind.HAT, 40, "Eine niedliche Schleife."),
        Item("hat_flower", "Blümchen", "🌸", ItemKind.HAT, 35, "Frisch gepflückt."),
        Item("hat_party", "Partyhut", "🥳", ItemKind.HAT, 50, "Jeder Tag ist ein Fest!"),
        Item("hat_cap", "Kappe", "🧢", ItemKind.HAT, 60, "Sportlich und lässig.", minLevel = 3),
        Item("hat_tophat", "Zylinder", "🎩", ItemKind.HAT, 120, "Sehr vornehm.", minLevel = 6),
        Item("hat_wizard", "Zauberhut", "🧙", ItemKind.HAT, 160, "Mit echtem Sternenstaub.", minLevel = 8),
        Item("hat_crown", "Krone", "👑", ItemKind.HAT, 300, "Für wahre Royals.", minLevel = 12),
        // Gesicht
        Item("face_round", "Nerdbrille", "👓", ItemKind.FACE, 45, "Sieht klug aus."),
        Item("face_sun", "Sonnenbrille", "😎", ItemKind.FACE, 70, "Einfach cool.", minLevel = 3),
        Item("face_heart", "Herzbrille", "💖", ItemKind.FACE, 90, "Die Welt in Rosa.", minLevel = 5),
        // Hals
        Item("neck_bowtie", "Fliege", "👔", ItemKind.NECK, 50, "Schick für jeden Anlass."),
        Item("neck_scarf", "Schal", "🧣", ItemKind.NECK, 55, "Kuschelig warm.", minLevel = 2),
        Item("neck_bell", "Glöckchen", "🔔", ItemKind.NECK, 65, "Bimmelt bei jedem Hüpfer.", minLevel = 4),
        // Zimmer
        Item("room_cozy", "Gemütliches Zimmer", "🏠", ItemKind.ROOM, 0, "Das erste Zuhause."),
        Item("room_forest", "Zauberwald", "🌳", ItemKind.ROOM, 150, "Glühwürmchen und raschelnde Blätter.", minLevel = 3),
        Item("room_ocean", "Unterwasserwelt", "🐠", ItemKind.ROOM, 180, "Blubb, blubb!", minLevel = 4),
        Item("room_space", "Weltall", "🚀", ItemKind.ROOM, 220, "Schwerelos zwischen den Sternen.", minLevel = 6),
        Item("room_candy", "Candyland", "🍭", ItemKind.ROOM, 250, "Alles aus Zucker!", minLevel = 8),
    )

    private val byId = items.associateBy { it.id }

    operator fun get(id: String): Item? = byId[id]

    fun of(kind: ItemKind): List<Item> = items.filter { it.kind == kind }

    val foods: List<Item> get() = of(ItemKind.FOOD)
    val careItems: List<Item> get() = of(ItemKind.CARE)
    val accessories: List<Item> get() = items.filter { it.kind == ItemKind.HAT || it.kind == ItemKind.FACE || it.kind == ItemKind.NECK }
    val rooms: List<Item> get() = of(ItemKind.ROOM)

    val startInventory: Map<String, Int> = mapOf("apple" to 3, "candy" to 2, MEDICINE to 1)
    const val START_COINS = 50
}
