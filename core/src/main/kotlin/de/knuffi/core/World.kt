package de.knuffi.core

import kotlinx.serialization.Serializable
import java.time.Instant
import java.time.ZoneId
import kotlin.random.Random

// ---------------------------------------------------------------------------------- cooking

/** A dish that can be cooked from ingredients. The dish itself is a normal food item. */
data class Recipe(val id: String, val dish: String, val ingredients: Map<String, Int>, val minLevel: Int = 1)

object Recipes {
    val all: List<Recipe> = listOf(
        Recipe("r_soup", "dish_soup", mapOf("carrot" to 1, "pumpkin" to 1)),
        Recipe("r_fruitsalad", "dish_fruitsalad", mapOf("apple" to 1, "strawberry" to 1, "blueberry" to 1)),
        Recipe("r_pancaketower", "dish_pancakes", mapOf("flour" to 1, "milk" to 1, "blueberry" to 1)),
        Recipe("r_tart", "dish_tart", mapOf("flour" to 1, "milk" to 1, "strawberry" to 2), minLevel = 3),
        Recipe("r_pumpkinbread", "dish_bread", mapOf("flour" to 2, "pumpkin" to 1), minLevel = 3),
        Recipe("r_smoothie", "dish_smoothie", mapOf("melon" to 1, "milk" to 1)),
        Recipe("r_pasta", "dish_pasta", mapOf("flour" to 1, "tomato" to 2, "cheese" to 1), minLevel = 4),
        Recipe("r_feast", "dish_feast", mapOf("tuna" to 1, "milk" to 1, "carrot" to 1), minLevel = 5),
        Recipe("r_sushi", "dish_rolls", mapOf("tuna" to 1, "carrot" to 1), minLevel = 4),
        Recipe("r_pizza", "dish_pizza", mapOf("flour" to 1, "tomato" to 1, "cheese" to 2), minLevel = 6),
    )

    private val byId = all.associateBy { it.id }
    operator fun get(id: String): Recipe? = byId[id]

    fun canCook(s: GameState, r: Recipe): Boolean = r.ingredients.all { (id, n) -> s.count(id) >= n }

    /** Dishes from the cooking game: the better the round, the better the food. */
    fun gameDishes(score: Int): List<String> = when {
        score >= 8 -> listOf("dish_feast", "dish_tart")
        score >= 5 -> listOf("dish_soup", "dish_smoothie")
        score >= 2 -> listOf("dish_soup")
        else -> emptyList()
    }
}

// ---------------------------------------------------------------------------------- finds

enum class FindSet(val title: String, val emoji: String, val reward: Int) {
    MUSCHELN("Muscheln", "🐚", 120),
    STEINE("Steine & Kristalle", "💎", 150),
    FEDERN("Federn", "🪶", 120),
    BLAETTER("Blätter & Blüten", "🍁", 100),
}

/** Things pets bring back from trips and walks. */
data class Find(val id: String, val name: String, val emoji: String, val set: FindSet, val rare: Boolean = false)

object Finds {
    val all: List<Find> = listOf(
        Find("f_shell", "Jakobsmuschel", "🐚", FindSet.MUSCHELN),
        Find("f_snail", "Schneckenhaus", "🐌", FindSet.MUSCHELN),
        Find("f_starfish", "Seestern", "⭐", FindSet.MUSCHELN),
        Find("f_coral", "Koralle", "🪸", FindSet.MUSCHELN),
        Find("f_pearl", "Perle", "🫧", FindSet.MUSCHELN, rare = true),
        Find("f_pebble", "Kieselstein", "🪨", FindSet.STEINE),
        Find("f_heartstone", "Herzstein", "❤️", FindSet.STEINE),
        Find("f_amethyst", "Amethyst", "🔮", FindSet.STEINE),
        Find("f_gold", "Goldnugget", "🌟", FindSet.STEINE, rare = true),
        Find("f_diamond", "Diamant", "💎", FindSet.STEINE, rare = true),
        Find("f_feather_blue", "Blaue Feder", "🪶", FindSet.FEDERN),
        Find("f_feather_owl", "Eulenfeder", "🦉", FindSet.FEDERN),
        Find("f_feather_parrot", "Papageienfeder", "🦜", FindSet.FEDERN),
        Find("f_feather_peacock", "Pfauenfeder", "🦚", FindSet.FEDERN, rare = true),
        Find("f_leaf_maple", "Ahornblatt", "🍁", FindSet.BLAETTER),
        Find("f_clover", "Vierblättriges Kleeblatt", "🍀", FindSet.BLAETTER, rare = true),
        Find("f_rose", "Getrocknete Rose", "🌹", FindSet.BLAETTER),
        Find("f_tulip", "Tulpe", "🌷", FindSet.BLAETTER),
        Find("f_acorn", "Eichel", "🌰", FindSet.BLAETTER),
    )
    private val byId = all.associateBy { it.id }
    operator fun get(id: String): Find? = byId[id]
    fun of(set: FindSet) = all.filter { it.set == set }

    fun forDestination(d: Destination): List<FindSet> = when (d) {
        Destination.STRAND -> listOf(FindSet.MUSCHELN)
        Destination.WIESE -> listOf(FindSet.BLAETTER, FindSet.FEDERN)
        Destination.ZAUBERWALD -> listOf(FindSet.BLAETTER, FindSet.FEDERN)
        Destination.BERGE, Destination.HOEHLE -> listOf(FindSet.STEINE)
        Destination.VULKAN -> listOf(FindSet.STEINE)
        Destination.WOLKEN -> listOf(FindSet.FEDERN)
        Destination.MOND -> listOf(FindSet.STEINE, FindSet.MUSCHELN)
    }

    fun roll(sets: List<FindSet>, rnd: Random): Find {
        val pool = all.filter { it.set in sets }
        while (true) {
            val f = pool[rnd.nextInt(pool.size)]
            if (!f.rare || rnd.nextFloat() < 0.25f) return f
        }
    }

    fun complete(set: FindSet, owned: Map<String, Int>) = of(set).all { (owned[it.id] ?: 0) > 0 }
}

// ---------------------------------------------------------------------------------- weather

@Serializable
enum class WeatherKind(val title: String, val emoji: String) {
    SUN("Sonnig", "☀️"),
    CLOUDY("Bewölkt", "☁️"),
    RAIN("Regen", "🌧️"),
    SNOW("Schnee", "❄️"),
    STORM("Gewitter", "⛈️"),
}

@Serializable
enum class WeatherMode(val title: String) { SIMULATED("Simuliert"), REAL("Echtes Wetter") }

@Serializable
data class WeatherState(
    val kind: WeatherKind = WeatherKind.SUN,
    val tempC: Int = 18,
    val fetchedAt: Long = 0,
    val place: String = "",
)

object Weather {
    const val REAL_MAX_AGE_MS = 3 * 3_600_000L

    /** Plausible weather for the season that changes every few hours (always the same for the same time). */
    fun simulated(now: Long, zone: ZoneId): WeatherState {
        val date = Instant.ofEpochMilli(now).atZone(zone)
        val slot = date.toLocalDate().toEpochDay() * 4 + date.hour / 6
        val rnd = Random(slot * 92821L + 7)
        val month = date.monthValue
        val winter = month == 12 || month <= 2
        val summer = month in 6..8
        val r = rnd.nextFloat()
        val kind = when {
            winter && r < 0.3f -> WeatherKind.SNOW
            r < (if (summer) 0.5f else 0.35f) -> WeatherKind.SUN
            r < 0.65f -> WeatherKind.CLOUDY
            r < (if (summer) 0.93f else 0.97f) -> WeatherKind.RAIN
            else -> if (winter) WeatherKind.SNOW else WeatherKind.STORM
        }
        val base = when (month) {
            12, 1, 2 -> 1
            3, 11 -> 8
            4, 10 -> 12
            5, 9 -> 17
            else -> 23
        }
        val dayWarm = if (date.hour in 11..17) 3 else -2
        val temp = base + dayWarm + rnd.nextInt(-3, 4) - if (kind == WeatherKind.RAIN) 2 else 0
        return WeatherState(kind, if (kind == WeatherKind.SNOW) minOf(temp, 1) else temp, now, "")
    }

    /** The weather that counts right now. */
    fun current(s: GameState, now: Long, zone: ZoneId): WeatherState {
        val real = s.weather
        if (s.settings.weatherMode == WeatherMode.REAL && real.fetchedAt > 0 && now - real.fetchedAt < REAL_MAX_AGE_MS) return real
        return simulated(now, zone)
    }

    /** WMO weather codes as returned by Open-Meteo. */
    fun fromWmo(code: Int): WeatherKind = when (code) {
        0, 1 -> WeatherKind.SUN
        2, 3, 45, 48 -> WeatherKind.CLOUDY
        in 51..67, in 80..82 -> WeatherKind.RAIN
        in 71..77, 85, 86 -> WeatherKind.SNOW
        in 95..99 -> WeatherKind.STORM
        else -> WeatherKind.CLOUDY
    }

    fun cold(w: WeatherState): Boolean = w.tempC <= 5

    fun hint(w: WeatherState): String = when {
        w.kind == WeatherKind.RAIN || w.kind == WeatherKind.STORM -> "Regen gießt den Garten: Pflanzen wachsen schneller."
        w.kind == WeatherKind.SUN && w.tempC >= 15 -> "Sonne macht gute Laune: Ausflüge bringen mehr."
        cold(w) -> "Brrr, kalt! Ein Schal oder Mütze wärmt und macht glücklich."
        w.kind == WeatherKind.SNOW -> "Schnee! Kakao wirkt doppelt."
        else -> "Gemütliches Wetter."
    }
}

// ---------------------------------------------------------------------------------- daily routine

@Serializable
enum class Routine(val title: String, val emoji: String, val fromHour: Int, val toHour: Int, val description: String) {
    BREAKFAST("Frühstück", "🥐", 6, 10, "Füttern zwischen 6 und 10 Uhr"),
    PLAYTIME("Spielzeit", "🎾", 10, 18, "Spielen oder ein Minispiel zwischen 10 und 18 Uhr"),
    DINNER("Abendessen", "🍲", 17, 20, "Füttern zwischen 17 und 20 Uhr"),
    BEDTIME("Schlafenszeit", "🌙", 19, 23, "Ins Bett bringen zwischen 19 und 23 Uhr"),
    ;

    fun now(hour: Int): Boolean = hour in fromHour until toHour
}

object Routines {
    const val BONUS_COINS = 40
    const val BONUS_XP = 30
}

// ---------------------------------------------------------------------------------- stories

data class StoryChoice(val text: String, val result: String, val coins: Int = 0, val item: String? = null, val find: String? = null, val joy: Int = 0)

data class StoryChapter(val title: String, val emoji: String, val text: String, val choices: List<StoryChoice>)

data class Story(
    val id: String,
    val title: String,
    val emoji: String,
    val description: String,
    val minLevel: Int,
    val chapters: List<StoryChapter>,
    /** Reward for finishing: coins and an item id (or skin id). */
    val finalCoins: Int,
    val finalItem: String?,
    val finalSkin: String? = null,
)

@Serializable
data class StoryState(
    /** Chapters done per story id. */
    val progress: Map<String, Int> = emptyMap(),
    /** Day of the last chapter (one chapter per day). */
    val lastDay: Long = -1,
    val finished: Set<String> = emptySet(),
    /** Text of the last outcome, shown until the next chapter. */
    val lastResult: String = "",
)

object Stories {
    /** {name} is replaced by the pet's name. */
    val all: List<Story> = listOf(
        Story(
            "treasure", "Die verlorene Schatzkarte", "🗺️", "Eine alte Karte unter dem Bett! Wohin führt sie?", 3,
            listOf(
                StoryChapter(
                    "Die Karte", "📜",
                    "Beim Aufräumen findet {name} unter dem Bett ein vergilbtes Stück Papier. Ein großes rotes X ist darauf gemalt, und daneben steht: „Wo die Sonne zuerst ins Fenster scheint“.",
                    listOf(
                        StoryChoice("Zum Fenster laufen", "Am Fensterbrett liegt ein kleiner goldener Schlüssel. Wofür der wohl ist?", coins = 20),
                        StoryChoice("Die Karte genau ansehen", "{name} entdeckt auf der Rückseite einen Hinweis: „Folge den Federn.“ Und findet gleich eine!", find = "f_feather_blue"),
                    ),
                ),
                StoryChapter(
                    "Der Garten", "🌻",
                    "Die Spur führt in den Garten. Zwischen den Beeten ist die Erde frisch aufgewühlt. Daneben sitzt ein Maulwurf und schaut neugierig.",
                    listOf(
                        StoryChoice("Den Maulwurf fragen", "Der Maulwurf zeigt mit der Pfote zum alten Baum. Als Dank bekommt er eine Karotte. Er schenkt {name} ein paar Samen.", item = "seed_pack"),
                        StoryChoice("Selbst nachgraben", "{name} buddelt und findet … einen hübschen Kieselstein! Aber keinen Schatz.", find = "f_pebble", joy = 5),
                    ),
                ),
                StoryChapter(
                    "Der alte Baum", "🌳",
                    "Im hohlen Stamm des alten Baums glitzert etwas. Aber es ist dunkel da drin, und es raschelt.",
                    listOf(
                        StoryChoice("Mutig hineingreifen", "Es ist nur ein Eichhörnchen! Es hat Nüsse versteckt und teilt eine mit {name}.", find = "f_acorn", joy = 8),
                        StoryChoice("Mit der Taschenlampe leuchten", "Im Licht funkelt ein Amethyst. Daneben ist ein Pfeil in die Rinde geritzt: zum Bach!", find = "f_amethyst"),
                    ),
                ),
                StoryChapter(
                    "Der Bach", "🏞️",
                    "Am Bach liegen glatte Steine. Auf einem ist dasselbe rote X wie auf der Karte. Ein Floß aus Ästen schaukelt am Ufer.",
                    listOf(
                        StoryChoice("Mit dem Floß übersetzen", "Wackelig, aber geschafft! Auf der anderen Seite steht eine kleine Holzkiste.", coins = 30, joy = 6),
                        StoryChoice("Über die Steine hüpfen", "Hüpf, hüpf, platsch! Ein bisschen nass, aber {name} findet eine Muschel und die Kiste.", find = "f_shell", joy = 4),
                    ),
                ),
                StoryChapter(
                    "Die Schatzkiste", "🧰",
                    "Da ist sie: die Schatzkiste! Der goldene Schlüssel passt. Langsam öffnet {name} den Deckel …",
                    listOf(
                        StoryChoice("Den Schatz behalten", "Gold und Glitzer! {name} ist überglücklich. Was für ein Abenteuer!", coins = 100),
                        StoryChoice("Den Schatz mit Freunden teilen", "{name} teilt den Schatz mit allen Freunden. Teilen macht doppelt glücklich!", coins = 60, joy = 20),
                    ),
                ),
            ),
            finalCoins = 150, finalItem = "back_treasure",
        ),
        Story(
            "garden_secret", "Das Geheimnis im Garten", "🌱", "Nachts leuchtet etwas zwischen den Beeten …", 5,
            listOf(
                StoryChapter(
                    "Das Leuchten", "✨",
                    "Mitten in der Nacht sieht {name} durchs Fenster ein Leuchten im Garten. Es tanzt zwischen den Pflanzen hin und her.",
                    listOf(
                        StoryChoice("Leise hinausschleichen", "Draußen ist es ganz still. Das Leuchten ist ein Glühwürmchen, das „Folge mir!“ zu summen scheint.", joy = 6),
                        StoryChoice("Bis morgen warten", "Am Morgen liegt an der Stelle eine glänzende Feder.", find = "f_feather_owl"),
                    ),
                ),
                StoryChapter(
                    "Die Glühwürmchen", "🪲",
                    "Viele Glühwürmchen sitzen auf einer Blume, die {name} noch nie gesehen hat. Sie ist ganz traurig und lässt die Blätter hängen.",
                    listOf(
                        StoryChoice("Die Blume gießen", "Die Blume richtet sich auf und glitzert. Die Glühwürmchen tanzen vor Freude!", joy = 10),
                        StoryChoice("Ein Lied singen", "{name} singt leise. Die Blume öffnet eine Knospe und eine Tulpe kommt heraus.", find = "f_tulip"),
                    ),
                ),
                StoryChapter(
                    "Die Gartenfee", "🧚",
                    "Aus der Blüte steigt eine winzige Fee. „Danke! Ich bin die Hüterin dieses Gartens. Darf ich dir einen Wunsch erfüllen?“",
                    listOf(
                        StoryChoice("Mehr Samen wünschen", "Die Fee lacht und schüttelt einen ganzen Beutel Samen aus ihrem Ärmel.", item = "seed_pack", coins = 20),
                        StoryChoice("Eine Freundin wünschen", "„Dann komme ich dich ab und zu besuchen“, sagt die Fee und schenkt dir ein Kleeblatt.", find = "f_clover", joy = 12),
                    ),
                ),
                StoryChapter(
                    "Das Gartenfest", "🎉",
                    "Am nächsten Abend feiern alle Gartentiere ein Fest. {name} ist der Ehrengast!",
                    listOf(
                        StoryChoice("Tanzen!", "{name} tanzt die ganze Nacht mit den Glühwürmchen. Was für ein Fest!", coins = 50, joy = 15),
                        StoryChoice("Ein Geschenk mitbringen", "{name} bringt eine Karotte mit. Die Fee schenkt dafür ihren Zauberstab.", coins = 40),
                    ),
                ),
            ),
            finalCoins = 200, finalItem = "back_fairywings",
        ),
        Story(
            "moon_trip", "Die Reise zum Mond", "🚀", "Eine Rakete aus Kartons – fliegt die wirklich?", 10,
            listOf(
                StoryChapter(
                    "Die Kartonrakete", "📦",
                    "{name} baut aus alten Kartons eine Rakete. Fehlt nur noch der Treibstoff!",
                    listOf(
                        StoryChoice("Mit Limonade betanken", "Es blubbert und zischt … und die Rakete hebt ab!", joy = 8),
                        StoryChoice("Mit Sternenstaub betanken", "Glitzer überall! Die Rakete schwebt sanft nach oben.", coins = 20),
                    ),
                ),
                StoryChapter(
                    "Im Weltall", "🌌",
                    "Draußen funkeln tausend Sterne. Ein kleiner Komet fliegt vorbei und winkt.",
                    listOf(
                        StoryChoice("Dem Kometen folgen", "Der Komet zeigt {name} den Weg und hinterlässt etwas Sternenstaub.", coins = 30),
                        StoryChoice("Sterne zählen", "Bei 1.000 hört {name} auf. Einer davon glitzert besonders: ein Goldnugget aus dem All!", find = "f_gold"),
                    ),
                ),
                StoryChapter(
                    "Auf dem Mond", "🌙",
                    "Der Mond ist weich wie Käse … und riecht auch ein bisschen so! Ein Mondhase hoppelt heran.",
                    listOf(
                        StoryChoice("Mit dem Mondhasen spielen", "Auf dem Mond hüpft man ganz hoch! {name} lacht und lacht.", joy = 20),
                        StoryChoice("Ein Stück Mond mitnehmen", "Ein kleiner Mondstein glänzt in der Pfote.", find = "f_diamond"),
                    ),
                ),
                StoryChapter(
                    "Heimweg", "🏡",
                    "Zeit für den Heimweg! Unten wartet schon dein Zuhause.",
                    listOf(
                        StoryChoice("Sanft landen", "Butterweich gelandet. Zu Hause ist es doch am schönsten!", coins = 60),
                        StoryChoice("Mit Looping landen", "Wuuusch! Ein perfekter Looping und eine wilde Landung im Garten.", coins = 40, joy = 15),
                    ),
                ),
            ),
            finalCoins = 300, finalItem = "back_jetpack",
        ),
    )

    private val byId = all.associateBy { it.id }
    operator fun get(id: String): Story? = byId[id]

    fun text(raw: String, name: String) = raw.replace("{name}", name)

    /** The story that is currently being told (the first unfinished one you can play). */
    fun current(s: GameState): Story? = all.firstOrNull { it.id !in s.story.finished && Engine.playerLevel(s) >= it.minLevel }

    fun canPlay(s: GameState, today: Long): Boolean = current(s) != null && s.story.lastDay != today
}

// ---------------------------------------------------------------------------------- friends

@Serializable
data class Visit(val petId: Long, val until: Long)

object Friends {
    const val VISIT_MS = 45 * 60_000L
    const val VISIT_COOLDOWN_MS = 3 * 3_600_000L
    const val MAX = 100

    fun key(a: Long, b: Long): String = if (a < b) "$a-$b" else "$b-$a"

    fun level(points: Int): Int = when {
        points >= 80 -> 4
        points >= 50 -> 3
        points >= 25 -> 2
        points >= 8 -> 1
        else -> 0
    }

    val titles = listOf("Kennen sich", "Bekannte", "Freunde", "Gute Freunde", "Beste Freunde")
}

// ---------------------------------------------------------------------------------- level rewards

object LevelRewards {
    /** Special items for reaching high levels. */
    val rewards: Map<Int, String> = mapOf(
        15 to "neck_goldchain",
        20 to "back_cape_gold",
        25 to "hat_goldcap",
        30 to "face_monocle",
        35 to "back_angelwings",
        40 to "toy_trophy",
        45 to "hat_starcrown",
        50 to "back_rainbowwings",
    )
}
