package de.knuffi.core

import kotlinx.serialization.Serializable
import kotlin.random.Random

// ---------------------------------------------------------------------------------- tricks

/** Little tricks a pet can learn by practising. Every pet learns on its own. */
@Serializable
enum class Trick(val title: String, val emoji: String, val description: String, val minLevel: Int, val practice: Int, val command: String) {
    SITZ("Sitz", "🪑", "Setzt sich brav hin und wartet.", 1, 4, "Sitz!"),
    PFOTE("Pfote geben", "🐾", "Gibt dir ganz höflich die Pfote.", 1, 5, "Gib Pfötchen!"),
    WINKEN("Winken", "👋", "Winkt dir mit beiden Armen zu.", 2, 5, "Wink mal!"),
    DREHEN("Pirouette", "🌀", "Dreht sich einmal elegant im Kreis.", 3, 6, "Dreh dich!"),
    MAENNCHEN("Männchen", "🧍", "Stellt sich ganz groß auf die Hinterbeine.", 4, 7, "Mach Männchen!"),
    SPRUNG("Hochsprung", "⬆️", "Springt so hoch es nur kann.", 5, 7, "Hopp!"),
    ROLLE("Rolle", "🔄", "Rollt sich einmal über den Boden.", 6, 8, "Rolle!"),
    VERBEUGUNG("Verbeugung", "🙇", "Verbeugt sich wie ein echter Star.", 7, 8, "Verbeugen!"),
    TANZ("Tanzen", "💃", "Tanzt eine kleine Choreografie.", 8, 10, "Tanz für mich!"),
    SALTO("Salto", "🤸", "Ein Salto rückwärts! Nur für Profis.", 12, 12, "Salto!"),
}

object Tricks {
    const val PRACTICE_COOLDOWN_MS = 2_500L
    const val SHOW_REWARD_COOLDOWN_MS = 45_000L

    fun points(p: Pet, t: Trick): Int = p.tricks[t] ?: 0
    fun learned(p: Pet, t: Trick): Boolean = points(p, t) >= t.practice
    fun progress(p: Pet, t: Trick): Float = (points(p, t).toFloat() / t.practice).coerceIn(0f, 1f)
    fun learnedCount(p: Pet): Int = Trick.entries.count { learned(p, it) }
    fun learnedTricks(p: Pet): List<Trick> = Trick.entries.filter { learned(p, it) }

    /** Chance that a practice round works: happy, rested and sporty pets learn faster. */
    fun successChance(p: Pet, now: Long): Float {
        var c = 0.5f + ((p.joy - 50.0) / 250.0).toFloat() + ((p.energy - 50.0) / 400.0).toFloat()
        c += 0.02f * p.talentLevel(Talent.SPORT)
        when (p.emotion(now)) {
            Emotion.VERSPIELT, Emotion.AUFGEREGT, Emotion.STOLZ -> c += 0.12f
            Emotion.GRUMMELIG, Emotion.MUEDE, Emotion.GELANGWEILT -> c -= 0.12f
            else -> Unit
        }
        return c.coerceIn(0.25f, 0.92f)
    }

    /** Coins for learning a trick. */
    fun learnCoins(t: Trick): Int = 10 + t.practice * 3

    /** Words that call a trick, checked in this order (the longer tricks first). */
    private val words: List<Pair<Trick, List<String>>> = listOf(
        Trick.SALTO to listOf("salto", "überschlag", "ueberschlag"),
        Trick.ROLLE to listOf("rolle", "roll dich", "rollen", "kuller"),
        Trick.VERBEUGUNG to listOf("verbeug", "verneig", "diener", "bück dich"),
        Trick.TANZ to listOf("tanz"),
        Trick.MAENNCHEN to listOf("männchen", "maennchen", "mach dich groß", "steh auf"),
        Trick.PFOTE to listOf("pfote", "pfötchen", "pfoetchen", "gib fünf", "high five"),
        Trick.WINKEN to listOf("wink", "tschüss", "tschüs", "hallo"),
        Trick.SPRUNG to listOf("hopp", "spring", "sprung", "hüpf"),
        Trick.DREHEN to listOf("dreh", "pirouette", "im kreis"),
        Trick.SITZ to listOf("sitz", "setz dich", "hinsetzen", "platz"),
    )

    /** The trick a spoken sentence asks for ("Sitz!", "Gib Pfötchen!" …), or null. */
    fun match(text: String): Trick? {
        val t = text.lowercase().trim()
        if (t.isEmpty()) return null
        return words.firstOrNull { (_, ws) -> ws.any { it in t } }?.first
    }

    /** Reason why the pet cannot practise right now, or null. */
    fun blocker(s: GameState, t: Trick? = null): String? {
        val p = s.pet ?: return "Du hast noch kein Haustier."
        return when {
            !p.alive || p.isEgg -> "Das geht gerade nicht."
            p.sleeping -> "${p.name} schläft gerade."
            p.sick -> "${p.name} ist krank und braucht Ruhe."
            p.energy < 8 -> "${p.name} ist zu müde zum Üben."
            t != null && p.level < t.minLevel -> "Diesen Trick gibt es ab Level ${t.minLevel}."
            else -> null
        }
    }
}

// ---------------------------------------------------------------------------------- touching

/** Where the pet was touched. */
@Serializable
enum class TouchZone(val title: String) {
    HEAD("Kopf"),
    EARS("Ohren"),
    CHEEKS("Bäckchen"),
    BELLY("Bauch"),
    FEET("Füße"),
}

// ---------------------------------------------------------------------------------- room games

/** Little games right in the room, without an extra game screen. */
@Serializable
enum class RoomGame(val title: String, val emoji: String, val description: String, val seconds: Int) {
    HIDE("Verstecken", "🙈", "Dein Haustier versteckt sich im Zimmer. Tippe auf das richtige Versteck!", 0),
    TAG("Fangen", "🏃", "Dein Haustier flitzt durchs Zimmer. Tippe es an, so oft du kannst!", 25),
    FETCH("Ball werfen", "🎾", "Tippe ins Zimmer, um den Ball zu werfen. Dein Haustier fängt ihn!", 30),
}

object RoomGames {
    /** Rounds of hide and seek. */
    const val HIDE_ROUNDS = 3

    fun coins(score: Int): Int = (4 + score * 2).coerceAtMost(30)
    fun xp(score: Int): Int = (8 + score * 2).coerceAtMost(30)

    /** Reason why a room game cannot start now, or null. */
    fun blocker(s: GameState): String? {
        val p = s.pet ?: return "Du hast noch kein Haustier."
        return when {
            !p.alive || p.isEgg -> "Das geht gerade nicht."
            p.sleeping -> "${p.name} schläft gerade. Psst!"
            p.sick -> "${p.name} ist zu krank zum Spielen."
            p.energy < 10 -> "${p.name} ist zu müde zum Spielen."
            else -> null
        }
    }
}

// ---------------------------------------------------------------------------------- voice

/** What the microphone heard. Nothing is recorded, only the loudness is measured. */
@Serializable
enum class VoiceKind { BLOW, CLAP, SING, LOUD }

// ---------------------------------------------------------------------------------- wishes

@Serializable
enum class WishKind(val emoji: String) {
    FOOD("🍽️"),
    GAME("🎮"),
    PLAY("🎾"),
    CUDDLE("🥰"),
    BATH("🛁"),
    TRICK("🌟"),
    VISIT("🏠"),
    COOK("🍳"),
    HARVEST("🧺"),
    OUTFIT("🎀"),
    PHOTO("📸"),
    ROOM_GAME("🙈"),
}

/** Something the pet wishes for right now. Fulfilling it makes the pet very happy. */
@Serializable
data class Wish(
    val kind: WishKind,
    val target: String = "",
    val need: Int = 1,
    val progress: Int = 0,
    val createdAt: Long = 0,
    val until: Long = 0,
)

object Wishes {
    const val DURATION_MS = 2 * 3_600_000L
    const val GAP_MS = 150 * 60_000L
    const val FIRST_DELAY_MS = 15 * 60_000L
    const val COINS = 30
    const val XP = 25

    private val bunnyFoods = listOf("hay", "cucumber", "dandelion", "apple", "banana")
    private val catFoods = listOf("tuna", "milk", "chicken")

    /** Emoji and short name of the wish target. */
    fun label(w: Wish, cat: Boolean = false): String = when (w.kind) {
        WishKind.FOOD -> Catalog[w.target]?.let { "${it.icon(cat)} ${it.label(cat)}" } ?: "🍽️ Essen"
        WishKind.GAME -> MiniGame.entries.firstOrNull { it.name == w.target }?.let { "${it.emoji} ${it.title}" } ?: "🎮 Ein Minispiel"
        WishKind.PLAY -> "🎾 Ballspielen"
        WishKind.CUDDLE -> "🥰 Kuscheln (${w.progress}/${w.need})"
        WishKind.BATH -> if (cat) "🪮 Bürsten" else "🛁 Baden"
        WishKind.TRICK -> "🌟 Ein Kunststück zeigen"
        WishKind.VISIT -> "🏠 Besuch von einem Freund"
        WishKind.COOK -> "🍳 Zusammen kochen"
        WishKind.HARVEST -> "🧺 Im Garten ernten"
        WishKind.OUTFIT -> "🎀 Sich schick machen"
        WishKind.PHOTO -> "📸 Ein Foto"
        WishKind.ROOM_GAME -> RoomGame.entries.firstOrNull { it.name == w.target }?.let { "${it.emoji} ${it.title}" } ?: "🙈 Zimmerspiel"
    }

    /** What the pet says. */
    fun text(w: Wish, cat: Boolean = false): String = when (w.kind) {
        WishKind.FOOD -> "Ich hätte sooo gern ${label(w, cat)}!"
        WishKind.GAME -> "Spielst du ${label(w, cat)} mit mir?"
        WishKind.PLAY -> "Wirfst du mir den Ball? 🎾"
        WishKind.CUDDLE -> "Ich will ganz viel gekuschelt werden! 🥰"
        WishKind.BATH -> if (cat) "Bürstest du mich? 🪮" else "Ich möchte ein Schaumbad! 🛁"
        WishKind.TRICK -> "Darf ich dir ein Kunststück zeigen? 🌟"
        WishKind.VISIT -> "Ich vermisse meine Freunde. Lädst du jemanden ein? 🏠"
        WishKind.COOK -> "Kochen wir zusammen was Leckeres? 🍳"
        WishKind.HARVEST -> "Ernten wir was im Garten? 🧺"
        WishKind.OUTFIT -> "Ich will mich schick machen! 🎀"
        WishKind.PHOTO -> "Machst du ein Foto von mir? 📸"
        WishKind.ROOM_GAME -> "Spielen wir ${label(w, cat)}?"
    }

    /** Picks a wish that can be fulfilled right now, or null. */
    fun roll(s: GameState, now: Long, rnd: Random): Wish? {
        val p = s.pet ?: return null
        if (!p.alive || p.isEgg) return null
        val level = Engine.playerLevel(s)
        val pool = mutableListOf<Pair<Wish, Int>>()
        fun add(kind: WishKind, weight: Int, target: String = "", need: Int = 1) {
            pool += Wish(kind, target, need, 0, now, now + DURATION_MS) to weight
        }
        val foods = Catalog.foods.filter { it.sold && !it.unlimited && it.price > 0 && it.minLevel <= level }
        val favourites = when {
            p.line.small -> bunnyFoods
            p.isCat -> catFoods
            else -> emptyList()
        }.filter { id -> foods.any { it.id == id } }
        val food = if (favourites.isNotEmpty() && rnd.nextFloat() < 0.6f) favourites[rnd.nextInt(favourites.size)] else foods.takeIf { it.isNotEmpty() }?.let { it[rnd.nextInt(it.size)].id }
        if (food != null) add(WishKind.FOOD, 4, food)
        val games = MiniGame.entries
        add(WishKind.GAME, 2, games[rnd.nextInt(games.size)].name)
        add(WishKind.PLAY, 2)
        add(WishKind.CUDDLE, 2, need = 5)
        add(WishKind.BATH, if (p.hygiene < 70) 2 else 1)
        add(WishKind.PHOTO, 1)
        add(WishKind.ROOM_GAME, 2, RoomGame.entries[rnd.nextInt(RoomGame.entries.size)].name)
        if (Tricks.learnedCount(p) > 0) add(WishKind.TRICK, 2)
        if (s.resting.any { !it.isEgg && it.alive && s.onTrip(it.id) == null } && (s.visit == null || s.visit2 == null)) add(WishKind.VISIT, 1)
        if (Recipes.all.any { it.minLevel <= level && Recipes.canCook(s, it) }) add(WishKind.COOK, 1)
        if (s.garden.plots.any { !it.empty }) add(WishKind.HARVEST, 1)
        if (s.owned.any { Catalog[it]?.kind?.wearable == true }) add(WishKind.OUTFIT, 1)
        val total = pool.sumOf { it.second }
        var r = rnd.nextInt(total)
        for ((w, weight) in pool) {
            r -= weight
            if (r < 0) return w
        }
        return pool.last().first
    }
}

// ---------------------------------------------------------------------------------- adventure map

@Serializable
data class AdventureState(
    /** Adventure points to spend on the next steps. */
    val points: Int = 0,
    /** Stations already reached on the current map. */
    val station: Int = 0,
    /** Number of maps finished. */
    val map: Int = 0,
)

enum class AdventureRegion(val title: String, val emoji: String, val top: Long, val bottom: Long) {
    MEADOW("Blumenwiese", "🌼", 0xFFB8F0A0, 0xFFFFF4C2),
    FOREST("Zauberwald", "🌲", 0xFF7FC48A, 0xFFD8F0C8),
    CLOUDS("Wolkenschloss", "🏰", 0xFFB8D8FF, 0xFFFFE6F6),
}

/** What waits at a station of the adventure map. */
data class StationReward(
    val coins: Int = 0,
    val itemId: String? = null,
    val count: Int = 1,
    val egg: EggLine? = null,
    val chest: Boolean = false,
) {
    val emoji: String
        get() = when {
            egg != null -> egg.emoji
            chest -> "🎁"
            itemId != null -> Catalog[itemId]?.emoji ?: "🎁"
            else -> "🪙"
        }
    val label: String
        get() = buildList {
            if (egg != null) add(egg.title)
            if (itemId != null) add("${Catalog[itemId]?.name ?: itemId}${if (count > 1) " ×$count" else ""}")
            if (coins > 0) add("$coins Münzen")
        }.joinToString(" + ")
}

data class Station(val index: Int, val title: String, val emoji: String, val region: AdventureRegion, val reward: StationReward)

object Adventure {
    const val STATIONS = 30
    private val names = listOf(
        listOf("Gänseblümchen" to "🌼", "Bienenstock" to "🐝", "Pusteblume" to "🌬️", "Schmetterlingsweg" to "🦋", "Kleeblattfeld" to "🍀", "Picknickdecke" to "🧺", "Regenbogenbrücke" to "🌈", "Mohnblumen" to "🌺", "Windmühle" to "🌀", "Wiesenschatz" to "💰"),
        listOf("Waldrand" to "🌳", "Pilzkreis" to "🍄", "Eulenbaum" to "🦉", "Glühwürmchen" to "✨", "Mooshügel" to "🌿", "Fuchsbau" to "🦊", "Wasserfall" to "💧", "Hexenhäuschen" to "🏚️", "Feentanz" to "🧚", "Waldschatz" to "💎"),
        listOf("Wolkentreppe" to "☁️", "Himmelsballon" to "🎈", "Regenbogen" to "🌈", "Sternenwiese" to "⭐", "Wolkenschaf" to "🐑", "Windspiel" to "🎐", "Schlosstor" to "🚪", "Thronsaal" to "👑", "Turmspitze" to "🗼", "Wolkenschatz" to "🏆"),
    )
    private val smallItems = listOf("cake", "icecream", "pizza", "bath", "cocoa", "bubbles", "brush", "donut", "waffle", "juice")
    private val newLines = listOf(EggLine.DSCHUNGEL, EggLine.WUESTE, EggLine.EDELSTEIN)

    /** Adventure points needed to reach station [index] (0-based). */
    fun cost(index: Int, map: Int): Int = 15 + (index % 10) + 3 * (index / 10) + 2 * map.coerceAtMost(5)

    fun region(index: Int): AdventureRegion = AdventureRegion.entries[(index / 10).coerceIn(0, 2)]

    fun station(map: Int, index: Int): Station {
        val region = region(index)
        val (title, emoji) = names[region.ordinal][index % 10]
        val rnd = Random(map * 1009L + index * 31L + 7L)
        val bonus = map.coerceAtMost(5)
        val reward = when {
            index == STATIONS - 1 -> StationReward(coins = 300 + 50 * bonus, itemId = Catalog.GLITTER, count = 2, egg = newLines[(map + 2) % newLines.size])
            index % 10 == 9 -> StationReward(coins = 100 + 20 * bonus, egg = newLines[(map + index / 10) % newLines.size])
            index % 5 == 4 -> StationReward(coins = 40 + 10 * bonus, itemId = Catalog.STICKER_PACK, chest = true)
            index % 3 == 2 -> StationReward(itemId = smallItems[rnd.nextInt(smallItems.size)], count = 2)
            index % 7 == 3 -> StationReward(itemId = Catalog.SEED_PACK, count = 2)
            else -> StationReward(coins = 20 + index + 5 * bonus)
        }
        return Station(index, title, emoji, region, reward)
    }

    /** The next station to reach, or null if the map is done (never: a new map starts automatically). */
    fun next(s: GameState): Station = station(s.adventure.map, s.adventure.station.coerceIn(0, STATIONS - 1))

    fun canStep(s: GameState): Boolean = s.adventure.points >= cost(s.adventure.station, s.adventure.map)
}

// ---------------------------------------------------------------------------------- tournaments

/** Cups of the tournament. Higher cups have faster rivals and better prizes. */
@Serializable
enum class Cup(val title: String, val emoji: String, val minLevel: Int, val skill: Float, val coins: Int) {
    BRONZE("Bronze-Pokal", "🥉", 2, 0.72f, 40),
    SILBER("Silber-Pokal", "🥈", 6, 0.86f, 80),
    GOLD("Gold-Pokal", "🥇", 12, 1.0f, 150),
    DIAMANT("Diamant-Pokal", "💎", 20, 1.14f, 250),
}

/** The three disciplines of a tournament. */
enum class Discipline(val title: String, val emoji: String, val hint: String) {
    RACE("Wettrennen", "🏁", "Tippe so schnell du kannst, damit dein Haustier rennt!"),
    HURDLES("Hürdenlauf", "🚧", "Tippe, um über die Hürden zu springen!"),
    LONGJUMP("Weitsprung", "🦘", "Tippe, wenn die Kraftanzeige ganz oben ist!"),
}

/** A rival in the tournament: a pet from the Kuschelhaus or a guest from the neighbourhood. */
data class Rival(val name: String, val form: Form, val petId: Long?, val fur: String?, val shiny: Boolean, val speed: Float)

object Tournament {
    const val RIVALS = 3
    private val guests = listOf("Flocke", "Bruno", "Pünktchen", "Kiki", "Momo", "Lotti", "Fips", "Wuschel", "Nala", "Pepe")

    /** Points per place in one discipline (1st..4th). */
    val placePoints = intArrayOf(5, 3, 2, 1)

    fun unlocked(s: GameState, cup: Cup): Boolean = Engine.playerLevel(s) >= cup.minLevel

    fun wins(s: GameState, cup: Cup): Int = s.cups[cup.name] ?: 0

    /** Up to three rivals: pets from the Kuschelhaus first, then guests. */
    fun rivals(s: GameState, cup: Cup, seed: Long): List<Rival> {
        val rnd = Random(seed)
        val out = mutableListOf<Rival>()
        val home = s.resting.filter { !it.isEgg && it.alive && s.onTrip(it.id) == null }.shuffled(rnd)
        for (p in home.take(RIVALS)) out += Rival(p.name, p.form, p.id, p.fur, p.shiny, cup.skill * (0.92f + rnd.nextFloat() * 0.16f) * (1f + 0.01f * p.level.coerceAtMost(20)))
        val forms = Form.creatures.filter { it.stage == Stage.ADULT || it.stage == Stage.TEEN }
        val names = guests.shuffled(rnd)
        var i = 0
        while (out.size < RIVALS) {
            out += Rival(names[i % names.size], forms[rnd.nextInt(forms.size)], null, null, false, cup.skill * (0.9f + rnd.nextFloat() * 0.2f))
            i++
        }
        return out
    }

    /** Coins for a final place (1..4). */
    fun coins(cup: Cup, place: Int): Int = when (place) {
        1 -> cup.coins
        2 -> cup.coins * 6 / 10
        3 -> cup.coins * 4 / 10
        else -> cup.coins / 4
    }

    /** Final place from the points of all rounds: [mine] against the rivals' totals. */
    fun place(mine: Int, rivals: List<Int>): Int = 1 + rivals.count { it > mine }
}

// ---------------------------------------------------------------------------------- monthly goals

@Serializable
data class MonthlyState(
    /** Month index (year * 12 + month). */
    val month: Int = -1,
    val goals: List<QuestProgress> = emptyList(),
    val bonusClaimed: Boolean = false,
)

// ---------------------------------------------------------------------------------- long-term goals

/** Big goals for the long run with special rewards. */
enum class LongGoal(
    val title: String,
    val description: String,
    val emoji: String,
    val target: Int,
    val coins: Int,
    val egg: EggLine? = null,
    val itemId: String? = null,
    val itemCount: Int = 1,
    private val metric: (GameState) -> Int,
) {
    ZOO("Kleiner Zoo", "Habe 6 Haustiere gleichzeitig", "🏡", 6, 200, egg = EggLine.DSCHUNGEL, metric = { it.allPets.size }),
    DISCOVER_30("Wesen-Sammler", "Entdecke 30 verschiedene Wesen", "📖", 30, 200, itemId = Catalog.GLITTER, metric = { it.discoveredCount }),
    DISCOVER_60("Wesen-Forscher", "Entdecke 60 verschiedene Wesen", "🔭", 60, 400, egg = EggLine.EDELSTEIN, metric = { it.discoveredCount }),
    SMALL_3("Kleintierfreund", "Hab einen Hasen, einen Hamster und ein Meerschweinchen entdeckt", "🐰", 3, 150, itemId = "toy_wheel", metric = { s ->
        listOf(EggLine.HASE, EggLine.HAMSTER, EggLine.MEERI).count { line -> line.forms.any { s.discovered(it) } }
    }),
    TRICKS_5("Zirkusstar", "Bring einem Haustier 5 Kunststücke bei", "🎪", 5, 250, itemId = "deco_stage", metric = { s ->
        s.allPets.maxOfOrNull { Tricks.learnedCount(it) } ?: 0
    }),
    WISHES_25("Wunscherfüller", "Erfülle 25 Wünsche", "💭", 25, 300, egg = EggLine.WUESTE, metric = { it.counters.wishes }),
    CHAMPION("Champion", "Gewinne 3 Turniere", "🏆", 3, 400, itemId = Catalog.GLITTER, metric = { it.counters.tournamentWins }),
    EXPLORER("Kartenleser", "Schaffe eine ganze Abenteuerkarte", "🗺️", 1, 500, itemId = Catalog.GLITTER, itemCount = 2, metric = { it.adventure.map }),
    MONTHS_3("Monatsheld", "Schaffe 3-mal alle Monatsziele", "📅", 3, 600, egg = EggLine.EDELSTEIN, metric = { it.counters.monthlies }),
    ;

    fun progress(s: GameState): Int = metric(s).coerceAtMost(target)
    fun done(s: GameState): Boolean = metric(s) >= target
    fun claimed(s: GameState): Boolean = "goal:$name" in s.milestones

    val rewardLabel: String
        get() = buildList {
            if (egg != null) add("${egg.emoji} ${egg.title}")
            if (itemId != null) add("${Catalog[itemId]?.emoji ?: "🎁"} ${Catalog[itemId]?.name ?: itemId}${if (itemCount > 1) " ×$itemCount" else ""}")
            add("$coins 🪙")
        }.joinToString(" · ")
}
