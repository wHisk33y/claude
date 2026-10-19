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
    BACK("Rücken", Slot.BACK),
    TOY("Spielecke", Slot.TOY),
    DECO("Sitzecke", Slot.DECO),
    INGREDIENT("Zutat", null),
    ;

    val wearable: Boolean get() = this == HAT || this == FACE || this == NECK || this == BACK
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
    /** Bonus while worn (special festival equipment). */
    val perk: Perk? = null,
    /** Name and emoji in the cat mode, if different. */
    val catName: String? = null,
    val catEmoji: String? = null,
) {
    fun label(cat: Boolean): String = if (cat) catName ?: name else name
    fun icon(cat: Boolean): String = if (cat) catEmoji ?: emoji else emoji

    val consumable: Boolean get() = kind == ItemKind.FOOD || kind == ItemKind.CARE
    val stackable: Boolean get() = consumable || kind == ItemKind.SEED || kind == ItemKind.INGREDIENT
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

    private fun gear(id: String, name: String, emoji: String, kind: ItemKind, desc: String, perk: Perk) =
        Item(id, name, emoji, kind, 0, desc, sold = false, perk = perk)

    val items: List<Item> = listOf(
        // Essen
        food("rice", "Reisbällchen", "🍙", 0, "Einfach, aber sättigend. Immer verfügbar.", Effect(satiety = 20)).copy(unlimited = true, catName = "Trockenfutter", catEmoji = "🥣"),
        food("apple", "Apfel", "🍎", 6, "Knackig und gesund.", Effect(satiety = 12, health = 5)),
        food("salad", "Salat", "🥗", 10, "Viele Vitamine für starke Abwehrkräfte.", Effect(satiety = 22, health = 8)),
        food("candy", "Bonbon", "🍬", 4, "Süß! Macht gute Laune.", Effect(satiety = 3, joy = 8, health = -1), snack = true),
        food("icecream", "Eis", "🍦", 9, "Kalt, cremig, glücklich.", Effect(satiety = 6, joy = 15), snack = true),
        food("pancakes", "Pfannkuchen", "🥞", 12, "Mit Ahornsirup. Mmmh!", Effect(satiety = 20, joy = 10), minLevel = 2),
        food("burger", "Burger", "🍔", 14, "Macht richtig satt, ist aber nicht so gesund.", Effect(satiety = 40, joy = 6, health = -3), snack = true),
        food("noodles", "Nudeln", "🍝", 14, "Ein großer Teller voller Energie.", Effect(satiety = 32, energy = 6), minLevel = 3),
        food("cake", "Törtchen", "🍰", 16, "Das Highlight des Tages!", Effect(satiety = 12, joy = 22, health = -2), snack = true),
        food("pizza", "Pizza", "🍕", 18, "Käse, Käse, Käse.", Effect(satiety = 38, joy = 12, health = -2), minLevel = 3, snack = true),
        food("tuna", "Thunfisch", "🐟", 12, "Der Lieblingsfisch aller Katzen. Schmeckt auch Fabelwesen.", Effect(satiety = 26, joy = 12, health = 3), minLevel = 2),
        food("milk", "Katzenmilch", "🥛", 7, "Laktosefrei und lecker.", Effect(satiety = 8, joy = 10), snack = true),
        food("sushi", "Sushi", "🍣", 22, "Feinschmecker-Essen mit allem, was gut tut.", Effect(satiety = 32, joy = 10, health = 6), minLevel = 5),
        food("banana", "Banane", "🍌", 5, "Gelb, süß und gesund.", Effect(satiety = 12, health = 4, energy = 3)),
        food("croissant", "Croissant", "🥐", 8, "Buttrig und knusprig – wie in Paris.", Effect(satiety = 16, joy = 6)),
        food("popcorn", "Popcorn", "🍿", 7, "Perfekt für einen Filmabend.", Effect(satiety = 5, joy = 12), snack = true),
        food("donut", "Donut", "🍩", 10, "Mit rosa Zuckerguss und Streuseln.", Effect(satiety = 8, joy = 18, health = -2), snack = true, minLevel = 2),
        food("waffle", "Waffel", "🧇", 12, "Frisch aus dem Waffeleisen.", Effect(satiety = 20, joy = 10), minLevel = 2),
        food("dango", "Mochi-Spieß", "🍡", 11, "Drei bunte Reisbällchen am Stiel.", Effect(satiety = 14, joy = 14), snack = true, minLevel = 3),
        food("chicken", "Hähnchen", "🍗", 16, "Zart und saftig. Katzen lieben es!", Effect(satiety = 34, joy = 8, health = 2), minLevel = 4),
        food("taco", "Taco", "🌮", 15, "Knusprig gefüllt mit allem Guten.", Effect(satiety = 30, joy = 10), minLevel = 4),
        food("bento", "Bento-Box", "🍱", 24, "Ein ganzes Menü in einer Box.", Effect(satiety = 42, joy = 10, health = 8), minLevel = 6),
        // Gekochte Gerichte (Küche und Kochstudio)
        food("dish_soup", "Karotten-Kürbis-Suppe", "🥣", 0, "Selbst gekocht! Wärmt von innen.", Effect(satiety = 45, health = 12, joy = 6), sold = false),
        food("dish_fruitsalad", "Obstsalat", "🍇", 0, "Bunt und gesund.", Effect(satiety = 30, health = 15, joy = 12), sold = false),
        food("dish_pancakes", "Blaubeer-Pfannkuchen", "🥞", 0, "Ein ganzer Turm!", Effect(satiety = 45, joy = 20), sold = false),
        food("dish_tart", "Erdbeertörtchen", "🧁", 0, "Zum Dahinschmelzen.", Effect(satiety = 25, joy = 35), sold = false),
        food("dish_bread", "Kürbisbrot", "🍞", 0, "Frisch aus dem Ofen.", Effect(satiety = 55, health = 6), sold = false),
        food("dish_smoothie", "Melonen-Smoothie", "🥤", 0, "Erfrischt und macht munter.", Effect(satiety = 15, joy = 18, energy = 15), sold = false),
        food("dish_pasta", "Tomatennudeln", "🍝", 0, "Mit viel Käse. Mamma mia!", Effect(satiety = 60, joy = 15, energy = 8), sold = false),
        food("dish_feast", "Festmahl", "🍲", 0, "Ein Festessen für Feinschmecker.", Effect(satiety = 70, joy = 25, health = 10), sold = false),
        food("dish_rolls", "Thunfisch-Röllchen", "🍱", 0, "Fein gerollt.", Effect(satiety = 40, joy = 15, health = 8), sold = false),
        food("dish_pizza", "Käsepizza", "🫓", 0, "Selbst belegt und knusprig.", Effect(satiety = 60, joy = 25), sold = false),
        food("tomato", "Tomate", "🍅", 0, "Sonnengereift aus dem Garten.", Effect(satiety = 10, health = 8), sold = false),
        // Zutaten
        Item("flour", "Mehl", "🌾", ItemKind.INGREDIENT, 4, "Zum Backen und Kochen in der Küche."),
        Item("cheese", "Käse", "🧀", ItemKind.INGREDIENT, 6, "Für Nudeln und Pizza."),
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
        Item("tea", "Kräutertee", "🍵", ItemKind.CARE, 10, "Beruhigt und tut gut.", minLevel = 3, effect = Effect(energy = 15, health = 10)),
        Item("bubbles", "Seifenblasen", "🫧", ItemKind.CARE, 12, "Blubb! Blasen fangen macht glücklich.", effect = Effect(joy = 22)),
        Item("brush", "Kuschelbürste", "🪮", ItemKind.CARE, 14, "Bürsten macht das Fell weich und glänzend.", minLevel = 2, effect = Effect(hygiene = 40, joy = 6)),
        Item("vitamins", "Vitamin-Gummis", "🍬", ItemKind.CARE, 18, "Kleine Gummibärchen mit großer Wirkung.", minLevel = 5, effect = Effect(health = 25, joy = 4)),
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
        seed("seed_tomato", "Tomatensamen", "🍅", 9, 5, "tomato", 3, Season.SUMMER),
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
        egg(EggLine.KATZE, 400, 3),
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
        wear("hat_catears", "Katzenohren", "🐱", ItemKind.HAT, 75, "Ein Haarreif mit flauschigen Öhrchen.", 2),
        wear("hat_halo", "Heiligenschein", "😇", ItemKind.HAT, 130, "Ein leuchtender Ring über dem Kopf.", 7),
        wear("hat_cowboy", "Cowboyhut", "🤠", ItemKind.HAT, 100, "Yeehaw!", 5),
        wear("hat_flowercrown", "Blütenkranz", "💐", ItemKind.HAT, 0, "Nur im Frühlings-Pass.", sold = false),
        wear("hat_straw", "Strohhut", "👒", ItemKind.HAT, 0, "Nur im Sommer-Pass.", sold = false),
        wear("hat_acorn", "Eichelmütze", "🌰", ItemKind.HAT, 0, "Nur im Herbst-Pass.", sold = false),
        wear("hat_bobble", "Bommelmütze", "🧶", ItemKind.HAT, 0, "Nur im Winter-Pass.", sold = false),
        wear("hat_bunnyears", "Hasenohren", "🐰", ItemKind.HAT, 0, "Nur zum Osterfest.", sold = false),
        wear("hat_captain", "Kapitänsmütze", "⚓", ItemKind.HAT, 0, "Nur zum Sommerfest.", sold = false),
        wear("hat_witch", "Hexenhut", "🧹", ItemKind.HAT, 0, "Nur zu Halloween.", sold = false),
        wear("hat_santa", "Weihnachtsmütze", "🎅", ItemKind.HAT, 0, "Nur zum Winterzauber.", sold = false),
        wear("hat_chef", "Kochmütze", "👨‍🍳", ItemKind.HAT, 70, "Für echte Küchenchefs.", 3),
        wear("hat_beret", "Baskenmütze", "🎨", ItemKind.HAT, 65, "Très chic!", 3),
        wear("hat_headphones", "Kopfhörer", "🎧", ItemKind.HAT, 95, "Musik im Ohr.", 5),
        wear("hat_goldcap", "Goldene Kappe", "🧢", ItemKind.HAT, 0, "Belohnung für Level 25.", sold = false),
        wear("hat_starcrown", "Sternenkrone", "🌠", ItemKind.HAT, 0, "Belohnung für Level 45.", sold = false),
        // Rücken
        wear("back_backpack", "Rucksack", "🎒", ItemKind.BACK, 80, "Für kleine Entdecker.", 3),
        wear("back_butterfly", "Schmetterlingsflügel", "🦋", ItemKind.BACK, 140, "Flattern im Wind.", 6),
        wear("back_shell", "Schneckenhaus", "🐌", ItemKind.BACK, 110, "Immer ein Zuhause dabei.", 5),
        wear("back_treasure", "Schatzrucksack", "💰", ItemKind.BACK, 0, "Aus der Geschichte „Die verlorene Schatzkarte“.", sold = false),
        wear("back_fairywings", "Feenflügel", "🧚", ItemKind.BACK, 0, "Aus der Geschichte „Das Geheimnis im Garten“.", sold = false),
        wear("back_jetpack", "Raketenrucksack", "🚀", ItemKind.BACK, 0, "Aus der Geschichte „Die Reise zum Mond“.", sold = false),
        wear("back_cape_gold", "Goldumhang", "👑", ItemKind.BACK, 0, "Belohnung für Level 20.", sold = false),
        wear("back_angelwings", "Engelsflügel", "😇", ItemKind.BACK, 0, "Belohnung für Level 35.", sold = false),
        wear("back_rainbowwings", "Regenbogenflügel", "🌈", ItemKind.BACK, 0, "Belohnung für Level 50.", sold = false),
        // Besondere Ausrüstung: nur bei Festen zu gewinnen, mit Bonus
        gear("hat_lucky", "Glückszylinder", "🍀", ItemKind.HAT, "Grün mit Kleeblatt. Zu gewinnen beim Neujahrsfest.", Perk.COINS),
        gear("hat_heartband", "Herzchen-Haarreif", "💞", ItemKind.HAT, "Zwei wippende Herzen. Zu gewinnen in der Herzchenwoche.", Perk.JOY),
        gear("hat_eggshell", "Eierschalen-Hut", "🐣", ItemKind.HAT, "Frisch geschlüpft! Zu gewinnen beim Osterfest.", Perk.TALENT),
        gear("hat_sunvisor", "Sonnenschild", "🕶️", ItemKind.HAT, "Für lange Sonnentage. Zu gewinnen beim Sommerfest.", Perk.TRIP),
        gear("hat_pumpkinlamp", "Kürbislaterne", "🏮", ItemKind.HAT, "Leuchtet im Dunkeln. Zu gewinnen an Halloween.", Perk.COINS),
        gear("hat_icecrown", "Eiskrone", "💠", ItemKind.HAT, "Aus funkelndem Eis. Zu gewinnen beim Winterzauber.", Perk.XP),
        // Gesicht
        wear("face_round", "Nerdbrille", "👓", ItemKind.FACE, 45, "Sieht klug aus."),
        wear("face_sun", "Sonnenbrille", "😎", ItemKind.FACE, 70, "Einfach cool.", 3),
        wear("face_heart", "Herzbrille", "💖", ItemKind.FACE, 90, "Die Welt in Rosa.", 5),
        wear("face_snorkel", "Taucherbrille", "🤿", ItemKind.FACE, 0, "Nur im Sommer-Pass.", sold = false),
        wear("face_stars", "Sternenbrille", "🤩", ItemKind.FACE, 0, "Nur zum Neujahrsfest.", sold = false),
        wear("face_aviator", "Fliegerbrille", "🥽", ItemKind.FACE, 85, "Bereit zum Abheben!", 4),
        wear("face_monocle", "Monokel", "🧐", ItemKind.FACE, 0, "Belohnung für Level 30.", sold = false),
        gear("face_starshades", "Sternen-Sonnenbrille", "⭐", ItemKind.FACE, "Glänzt in der Sonne. Zu gewinnen beim Sommerfest.", Perk.WORK),
        // Hals
        wear("neck_bowtie", "Fliege", "👔", ItemKind.NECK, 50, "Schick für jeden Anlass."),
        wear("neck_scarf", "Schal", "🧣", ItemKind.NECK, 55, "Kuschelig warm.", 2),
        wear("neck_bell", "Glöckchen", "🔔", ItemKind.NECK, 65, "Bimmelt bei jedem Hüpfer.", 4),
        wear("neck_cape", "Heldenumhang", "🦸", ItemKind.NECK, 120, "Für kleine Superhelden.", 6),
        wear("neck_flowerchain", "Blumenkette", "🌼", ItemKind.NECK, 0, "Nur im Frühlings-Pass.", sold = false),
        wear("neck_leafscarf", "Laubschal", "🍂", ItemKind.NECK, 0, "Nur im Herbst-Pass.", sold = false),
        wear("neck_starscarf", "Sternenschal", "🌟", ItemKind.NECK, 0, "Nur im Winter-Pass.", sold = false),
        wear("neck_heart", "Herzmedaillon", "💗", ItemKind.NECK, 0, "Nur zur Herzchenwoche.", sold = false),
        wear("neck_pearls", "Perlenkette", "📿", ItemKind.NECK, 90, "Elegant und glänzend.", 4),
        wear("neck_bandana", "Halstuch", "🔴", ItemKind.NECK, 45, "Rot mit weißen Punkten.", 2),
        wear("neck_goldchain", "Goldkette", "🥇", ItemKind.NECK, 0, "Belohnung für Level 15.", sold = false),
        gear("neck_medal", "Glücksmedaille", "🏅", ItemKind.NECK, "Eine goldene Medaille am Band. Zu gewinnen beim Neujahrsfest.", Perk.XP),
        gear("neck_rosechain", "Rosenkette", "🌹", ItemKind.NECK, "Duftet herrlich. Zu gewinnen in der Herzchenwoche.", Perk.HUNGER),
        gear("neck_carrotscarf", "Karottenschal", "🥕", ItemKind.NECK, "Orange und kuschelig. Zu gewinnen beim Osterfest.", Perk.GARDEN),
        gear("neck_batbow", "Fledermaus-Fliege", "🦇", ItemKind.NECK, "Flattert ein bisschen. Zu gewinnen an Halloween.", Perk.ENERGY),
        gear("neck_lights", "Lichterkette", "💡", ItemKind.NECK, "Blinkt in allen Farben. Zu gewinnen beim Winterzauber.", Perk.TALENT),
        // Zimmer
        Item("room_cozy", "Kinderzimmer", "🏠", ItemKind.ROOM, 0, "Das erste Zuhause. Mit Möbeln einrichtbar."),
        Item("room_forest", "Zauberwald", "🌳", ItemKind.ROOM, 150, "Glühwürmchen und raschelnde Blätter.", minLevel = 3),
        Item("room_ocean", "Unterwasserwelt", "🐠", ItemKind.ROOM, 180, "Blubb, blubb!", minLevel = 4),
        Item("room_space", "Weltall", "🚀", ItemKind.ROOM, 220, "Schwerelos zwischen den Sternen.", minLevel = 6),
        Item("room_candy", "Candyland", "🍭", ItemKind.ROOM, 250, "Alles aus Zucker!", minLevel = 8),
        Item("room_beach", "Strandhaus", "🏖️", ItemKind.ROOM, 260, "Mit Meerblick und Hängematte.", minLevel = 9),
        Item("room_castle", "Burgzimmer", "🏰", ItemKind.ROOM, 320, "Steinmauern, Fahnen und ein Thron.", minLevel = 12),
        // Möbel für das Kinderzimmer
        wear("wall_rose", "Rosentapete", "🌸", ItemKind.WALL, 0, "Zarte Streifen und Rosenpunkte."),
        wear("wall_sky", "Himmelstapete", "☁️", ItemKind.WALL, 0, "Hellblau mit kleinen Wolken."),
        wear("wall_mint", "Minztapete", "🌿", ItemKind.WALL, 60, "Frisch wie ein Frühlingsmorgen.", 2),
        wear("wall_stars", "Sternentapete", "✨", ItemKind.WALL, 90, "Ein Himmel voller Sterne.", 4),
        wear("wall_wood", "Holzvertäfelung", "🪵", ItemKind.WALL, 90, "Gemütlich wie in einer Hütte.", 4),
        wear("wall_paws", "Pfotentapete", "🐾", ItemKind.WALL, 80, "Lauter kleine Pfotenabdrücke.", 3),
        wear("wall_sunset", "Abendrot-Tapete", "🌅", ItemKind.WALL, 110, "Warme Farben wie beim Sonnenuntergang.", 6),
        wear("wall_lemon", "Zitronentapete", "🍋", ItemKind.WALL, 70, "Sonnengelb mit kleinen Tupfen.", 3),
        wear("wall_lilac", "Fliedertapete", "💜", ItemKind.WALL, 85, "Zartlila mit Herzchen.", 4),
        wear("rug_round", "Kuschelteppich", "⭕", ItemKind.RUG, 0, "Rund und flauschig."),
        wear("rug_rainbow", "Regenbogenteppich", "🌈", ItemKind.RUG, 70, "In allen Farben.", 2),
        wear("rug_star", "Sternteppich", "⭐", ItemKind.RUG, 70, "Für kleine Sterne.", 3),
        wear("rug_leaf", "Blätterteppich", "🍃", ItemKind.RUG, 70, "Wie eine Waldlichtung.", 3),
        wear("rug_paw", "Pfotenteppich", "🐾", ItemKind.RUG, 75, "Ein Teppich in Pfotenform.", 3),
        wear("rug_heart", "Herzteppich", "💗", ItemKind.RUG, 80, "Zum Kuscheln.", 4),
        wear("bed_classic", "Holzbett", "🛏️", ItemKind.BED, 0, "Ein gemütliches Bett."),
        wear("bed_cloud", "Wolkenbett", "☁️", ItemKind.BED, 150, "Schlafen wie auf Wolken.", 4),
        wear("bed_race", "Rennauto-Bett", "🏎️", ItemKind.BED, 150, "Brumm brumm, gute Nacht!", 4, look = LookStyle.ABENTEUER),
        wear("bed_canopy", "Himmelbett", "👑", ItemKind.BED, 180, "Mit zartem Vorhang.", 5, look = LookStyle.ZAUBER),
        wear("bed_basket", "Katzenkorb", "🧺", ItemKind.BED, 120, "Ein runder Korb mit Kissen. Katzen lieben ihn!", 2),
        wear("bed_hammock", "Hängematte", "🏝️", ItemKind.BED, 160, "Sanftes Schaukeln in den Schlaf.", 6),
        wear("plant_monstera", "Monstera", "🪴", ItemKind.PLANT, 0, "Große grüne Blätter."),
        wear("plant_cactus", "Kaktus", "🌵", ItemKind.PLANT, 50, "Pflegeleicht und stachelig.", 2),
        wear("plant_sunflower", "Sonnenblume", "🌻", ItemKind.PLANT, 60, "Dreht sich zur Sonne.", 3),
        wear("plant_bonsai", "Bonsai", "🌳", ItemKind.PLANT, 80, "Ein Baum im Mini-Format.", 5),
        wear("plant_catgrass", "Katzengras", "🌿", ItemKind.PLANT, 40, "Zum Knabbern für Katzen.", 1),
        wear("plant_tulips", "Tulpentopf", "🌷", ItemKind.PLANT, 65, "Bunte Frühlingsboten.", 3),
        wear("lamp_floor", "Stehlampe", "💡", ItemKind.LAMP, 0, "Warmes Licht."),
        wear("lamp_mushroom", "Pilzlampe", "🍄", ItemKind.LAMP, 70, "Leuchtet wie ein Zauberpilz.", 3),
        wear("lamp_moon", "Mondlampe", "🌙", ItemKind.LAMP, 80, "Ein kleiner Mond fürs Zimmer.", 4),
        wear("lamp_lava", "Lavalampe", "🫧", ItemKind.LAMP, 90, "Blubbernde Farbkleckse.", 5),
        wear("lamp_star", "Sternlampe", "⭐", ItemKind.LAMP, 85, "Wirft Sterne an die Wand.", 4),
        wear("lamp_lantern", "Laterne", "🏮", ItemKind.LAMP, 75, "Warmes, flackerndes Licht.", 3),
        wear("pic_landscape", "Landschaftsbild", "🖼️", ItemKind.PICTURE, 0, "Grüne Hügel und Sonne."),
        wear("pic_dragon", "Drachenbild", "🐉", ItemKind.PICTURE, 60, "Ein mutiger Drache.", 2),
        wear("pic_unicorn", "Einhornbild", "🦄", ItemKind.PICTURE, 60, "Ein Einhorn im Regenbogen.", 2),
        wear("pic_rocket", "Raketenbild", "🚀", ItemKind.PICTURE, 60, "Auf zu den Sternen!", 3),
        wear("pic_heart", "Herzbild", "💖", ItemKind.PICTURE, 60, "Voller Liebe.", 3),
        wear("pic_cat", "Katzenbild", "🐱", ItemKind.PICTURE, 60, "Eine Katze im Sonnenschein.", 2),
        wear("pic_sunset", "Sonnenuntergang", "🌇", ItemKind.PICTURE, 70, "Ein Bild vom Meer am Abend.", 4),
        // Spielecke
        wear("toy_scratcher", "Kratzbaum", "🐈", ItemKind.TOY, 110, "Zum Kratzen, Klettern und Dösen.", 2),
        wear("toy_box", "Spielzeugkiste", "🧸", ItemKind.TOY, 70, "Voller Kuscheltiere und Bauklötze.", 1),
        wear("toy_horse", "Schaukelpferd", "🐴", ItemKind.TOY, 120, "Hüh, hott!", 4),
        wear("toy_aquarium", "Aquarium", "🐠", ItemKind.TOY, 160, "Mit bunten Fischen. Katzen schauen stundenlang zu!", 6),
        wear("toy_cave", "Kuschelhöhle", "🛖", ItemKind.TOY, 90, "Eine weiche Höhle zum Verstecken.", 3),
        // Sitzecke
        wear("deco_beanbag", "Sitzsack", "🫘", ItemKind.DECO, 80, "Weich und gemütlich. Plumps!", 1),
        wear("deco_sofa", "Kuschelsofa", "🛋️", ItemKind.DECO, 140, "Mit dicken Kissen zum Faulenzen.", 3),
        wear("deco_bookshelf", "Bücherregal", "📚", ItemKind.DECO, 120, "Voller Geschichten und Bilderbücher.", 4),
        wear("deco_globe", "Globus", "🌍", ItemKind.DECO, 90, "Wohin geht die nächste Reise?", 2),
        wear("deco_piano", "Klavier", "🎹", ItemKind.DECO, 220, "Plim plim plim! Für kleine Musiker.", 7),
        wear("deco_fireplace", "Kamin", "🔥", ItemKind.DECO, 260, "Knistert so schön an kalten Tagen.", 9),
        wear("deco_tent", "Spielzelt", "⛺", ItemKind.DECO, 150, "Ein Zelt mit Wimpeln für Abenteuer drinnen.", 5),
        wear("toy_trophy", "Pokalvitrine", "🏆", ItemKind.TOY, 0, "Belohnung für Level 40. Zeigt deine Pokale.", sold = false),
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
