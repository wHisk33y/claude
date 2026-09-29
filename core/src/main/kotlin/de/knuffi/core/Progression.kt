package de.knuffi.core

import java.time.Instant
import java.time.ZoneId
import java.util.Locale
import kotlin.random.Random

object TimeUtil {
    fun epochDay(now: Long, zone: ZoneId): Long =
        Instant.ofEpochMilli(now).atZone(zone).toLocalDate().toEpochDay()

    /** Monday-based week number. */
    fun week(now: Long, zone: ZoneId): Long = Math.floorDiv(epochDay(now, zone) + 3, 7L)

    fun hour(now: Long, zone: ZoneId): Int = Instant.ofEpochMilli(now).atZone(zone).hour

    fun isNight(now: Long, zone: ZoneId): Boolean {
        val h = hour(now, zone)
        return h >= 22 || h < 7
    }

    fun formatNumber(n: Long): String = String.format(Locale.GERMANY, "%,d", n)
    fun formatNumber(n: Int): String = formatNumber(n.toLong())

    fun formatDuration(ms: Long): String {
        val m = (ms.coerceAtLeast(0) + 59_999) / 60_000
        return when {
            m >= 60 -> "${m / 60} Std ${if (m % 60 > 0) "${m % 60} Min" else ""}".trim()
            else -> "$m Min"
        }
    }
}

object Leveling {
    const val MAX_LEVEL = 50

    fun xpToNext(level: Int): Int = 40 + 20 * level

    fun levelUpCoins(level: Int): Int = 10 + 2 * level

    /** The form stage a pet should have reached at [level]. */
    fun stageFor(level: Int): Stage = when {
        level >= Stage.ADULT.minLevel -> Stage.ADULT
        level >= Stage.TEEN.minLevel -> Stage.TEEN
        level >= Stage.CHILD.minLevel -> Stage.CHILD
        else -> Stage.BABY
    }

    /** Perfect care over several days as a teenager leads to the legendary form. */
    fun legendReady(care: CareLog): Boolean = care.mistakes == 0 && care.averageMood >= 78 && care.moodHours >= 72

    /** Picks the next form based on the egg line and how the pet was cared for during the current stage. */
    fun nextForm(pet: Pet): Form {
        val care = pet.care
        val bad = care.mistakes >= 5 || care.averageMood < 35
        val sporty = care.activity > care.food * 1.3 + 1
        val foodie = care.food > care.activity * 1.3 + 1
        fun f(role: Role): Form? = Form.find(pet.line, role)
        val next: Form? = when (pet.form.stage) {
            Stage.EGG -> f(Role.BABY)
            Stage.BABY -> {
                val good = care.mistakes <= 3 && care.averageMood >= 45
                if (good) f(Role.CHILD) else f(Role.CHILD_B) ?: f(Role.CHILD)
            }
            Stage.CHILD -> when {
                bad -> f(Role.TEEN_B)
                sporty -> f(Role.TEEN_SPORTY) ?: f(Role.TEEN)
                foodie -> f(Role.TEEN_FOODIE) ?: f(Role.TEEN)
                else -> f(Role.TEEN)
            }
            Stage.TEEN -> when {
                bad -> Form.SCHATTLING
                legendReady(care) -> f(Role.LEGEND)
                sporty -> f(Role.ADULT_SPORTY)
                foodie -> f(Role.ADULT_FOODIE)
                else -> f(Role.ADULT_BALANCED)
            }
            Stage.ADULT -> pet.form
        }
        return next ?: pet.form
    }
}

enum class QuestKind { COUNT, MAX }

/** What a quest needs so that it can be rolled. */
enum class QuestNeed { NONE, STEPS, RESTING }

@kotlinx.serialization.Serializable
enum class QuestType(
    val template: String,
    val emoji: String,
    val target: Int,
    val coins: Int,
    val xp: Int,
    val kind: QuestKind = QuestKind.COUNT,
    /** Target as weekly challenge (0 = not used weekly). */
    val weekly: Int = 0,
    val need: QuestNeed = QuestNeed.NONE,
) {
    FEED("Füttere {n}-mal", "🍙", 3, 15, 15, weekly = 20),
    PLAY("Spiele {n}-mal mit deinem Haustier", "🎾", 3, 15, 15, weekly = 20),
    PET("Streichle {n}-mal", "💞", 10, 10, 10, weekly = 60),
    CLEAN("Mach {n}-mal sauber", "🫧", 1, 10, 10, weekly = 8),
    GAMES("Spiele {n} Minispiele", "🎮", 2, 20, 25, weekly = 12),
    STEPS("Gehe {n} Schritte an einem Tag", "👟", 3000, 25, 30, QuestKind.MAX, weekly = 8000, need = QuestNeed.STEPS),
    CATCH_SCORE("Erreiche {n} Punkte in Futterfang", "🧺", 150, 20, 25, QuestKind.MAX, weekly = 400),
    MEMORY("Löse {n} Memory", "🃏", 1, 20, 20, weekly = 5),
    WHACK_SCORE("Schaffe {n} Treffer bei Blitz-Tap", "⚡", 20, 20, 25, QuestKind.MAX, weekly = 40),
    SHOP("Kaufe {n} Sache im Shop", "🛍️", 1, 10, 10),
    SLEEP("Bring dein Haustier {n}-mal ins Bett", "🌙", 1, 10, 10, weekly = 5),
    HEALTHY("Gib {n}-mal gesundes Essen", "🥗", 2, 15, 15, weekly = 12),
    SNACK("Gib {n} Leckerli", "🍬", 2, 10, 10),
    BATH("Nimm {n} Schaumbad", "🛁", 1, 15, 15),
    HAPPY("Mach dein Haustier rundum glücklich", "😊", 1, 20, 20),
    PLANT("Pflanze {n} Samen", "🌱", 2, 12, 12, weekly = 12),
    WATER("Gieße {n}-mal im Garten", "💧", 3, 10, 10, weekly = 15),
    HARVEST("Ernte {n}-mal", "🧺", 2, 20, 20, weekly = 10),
    TRIP("Schicke {n} Haustier auf einen Ausflug", "🎒", 1, 20, 20, weekly = 5, need = QuestNeed.RESTING),
    OUTFIT("Zieh deinem Haustier etwas an", "🎀", 1, 10, 10),
    XP("Sammle {n} Erfahrungspunkte", "✨", 80, 15, 0, weekly = 600),
    COINS("Verdiene {n} Münzen", "🪙", 80, 0, 20, weekly = 600),
    RUNNER_SCORE("Laufe {n} Meter beim Hüpf-Lauf", "🏃", 300, 20, 25, QuestKind.MAX, weekly = 800),
    BUBBLES_SCORE("Lass {n} Blasen platzen", "🫧", 25, 20, 25, QuestKind.MAX, weekly = 50),
    SIMON_LEVEL("Schaffe {n} Töne bei Melodie", "🎵", 6, 20, 25, QuestKind.MAX, weekly = 10),
    STARS3("Hole 3 Sterne in einem Minispiel", "⭐", 1, 25, 25, weekly = 5),
    STICKER("Sammle {n} Sticker", "🎴", 1, 15, 15, weekly = 8),
    ;

    fun label(target: Int = this.target): String = template.replace("{n}", TimeUtil.formatNumber(target))
}

object Quests {
    const val PER_DAY = 3
    const val PER_WEEK = 3
    const val BONUS_COINS = 30
    const val BONUS_XP = 30
    const val WEEKLY_BONUS_COINS = 150

    private fun allowed(t: QuestType, stepsAvailable: Boolean, hasResting: Boolean) = when (t.need) {
        QuestNeed.NONE -> true
        QuestNeed.STEPS -> stepsAvailable
        QuestNeed.RESTING -> hasResting
    }

    fun roll(day: Long, stepsAvailable: Boolean, hasResting: Boolean = false): List<QuestProgress> {
        val pool = QuestType.entries.filter { allowed(it, stepsAvailable, hasResting) }
        val rnd = Random(day * 7919L + 17L)
        return pool.shuffled(rnd).take(PER_DAY).map { QuestProgress(it, it.target) }
    }

    fun rollWeek(week: Long, stepsAvailable: Boolean, hasResting: Boolean = false): List<QuestProgress> {
        val pool = QuestType.entries.filter { it.weekly > 0 && allowed(it, stepsAvailable, hasResting) }
        val rnd = Random(week * 104729L + 5L)
        return pool.shuffled(rnd).take(PER_WEEK).map { QuestProgress(it, it.weekly) }
    }

    fun weeklyCoins(t: QuestType): Int = maxOf(40, t.coins * 4)
    fun weeklyXp(t: QuestType): Int = maxOf(40, t.xp * 3)
}

object DailyRewards {
    val coins = listOf(15, 20, 25, 30, 40, 50, 100)
    const val BONUS_ITEM = "cake"

    /** 0-based index into the 7-day cycle for a given streak. */
    fun dayIndex(streak: Int): Int = ((streak.coerceAtLeast(1) - 1) % 7)
}

object StepRewards {
    /** Pairs of (steps needed, coins). */
    val tiers: List<Pair<Int, Int>> = listOf(1000 to 10, 3000 to 20, 5000 to 35, 10000 to 70)
}

enum class Achievement(
    val title: String,
    val description: String,
    val emoji: String,
    val coins: Int,
    val target: Int,
    private val metric: (GameState) -> Long,
) {
    HATCH("Hallo Welt!", "Brüte dein erstes Ei aus", "🐣", 20, 1, { it.counters.hatched.toLong() }),
    FEED_10("Leckerschmecker", "Füttere 10-mal", "🍙", 20, 10, { it.counters.feeds.toLong() }),
    FEED_100("Gourmet", "Füttere 100-mal", "🍱", 100, 100, { it.counters.feeds.toLong() }),
    PLAY_25("Spielkamerad", "Spiele 25-mal mit deinem Haustier", "🎾", 50, 25, { it.counters.plays.toLong() }),
    PET_100("Kuschelmonster", "Streichle 100-mal", "💞", 50, 100, { it.counters.pets.toLong() }),
    CLEAN_20("Blitzblank", "Mache 20-mal sauber", "🫧", 40, 20, { it.counters.cleans.toLong() }),
    HEAL_1("Doktor", "Heile dein krankes Haustier", "💊", 20, 1, { it.counters.heals.toLong() }),
    SLEEP_10("Sandmännchen", "Bring dein Haustier 10-mal ins Bett", "🌙", 30, 10, { it.counters.sleeps.toLong() }),
    GAMES_10("Zocker", "Spiele 10 Minispiele", "🎮", 40, 10, { it.counters.gamesPlayed.toLong() }),
    GAMES_50("Arcade-Legende", "Spiele 50 Minispiele", "🕹️", 120, 50, { it.counters.gamesPlayed.toLong() }),
    GAMES_200("Spielekönig", "Spiele 200 Minispiele", "👾", 300, 200, { it.counters.gamesPlayed.toLong() }),
    CATCH_300("Fangkünstler", "Erreiche 300 Punkte in Futterfang", "🧺", 60, 300, { it.counters.bestCatch.toLong() }),
    MEMORY_12("Elefantengedächtnis", "Löse Memory in höchstens 12 Zügen", "🧠", 60, 1, {
        if (it.counters.bestMemoryMoves in 1..12) 1L else 0L
    }),
    WHACK_40("Blitzschnell", "Schaffe 40 Treffer bei Blitz-Tap", "⚡", 60, 40, { it.counters.bestWhack.toLong() }),
    RUNNER_1000("Sprinter", "Laufe 1.000 Meter beim Hüpf-Lauf", "🏃", 80, 1000, { it.counters.bestRunner.toLong() }),
    BUBBLES_60("Blasenmeister", "Lass 60 Blasen in einer Runde platzen", "🫧", 80, 60, { it.counters.bestBubbles.toLong() }),
    SIMON_12("Musikgenie", "Schaffe 12 Töne bei Melodie", "🎵", 80, 12, { it.counters.bestSimon.toLong() }),
    STEPS_DAY_10K("Wandervogel", "Gehe 10.000 Schritte an einem Tag", "🥾", 80, 10_000, { it.counters.bestDaySteps.toLong() }),
    STEPS_100K("Marathon", "Gehe insgesamt 100.000 Schritte", "🏃", 150, 100_000, { it.counters.totalSteps }),
    STEPS_1M("Weltumrunder", "Gehe insgesamt 1.000.000 Schritte", "🌍", 500, 1_000_000, { it.counters.totalSteps }),
    LEVEL_5("Aufsteiger", "Erreiche Level 5", "⭐", 30, 5, { it.counters.maxLevel.toLong() }),
    LEVEL_10("Profi", "Erreiche Level 10", "🌟", 60, 10, { it.counters.maxLevel.toLong() }),
    LEVEL_20("Meister", "Erreiche Level 20", "💫", 150, 20, { it.counters.maxLevel.toLong() }),
    LEVEL_30("Großmeister", "Erreiche Level 30", "🌠", 250, 30, { it.counters.maxLevel.toLong() }),
    LEVEL_50("Unvergesslich", "Erreiche Level 50", "🏆", 500, 50, { it.counters.maxLevel.toLong() }),
    EVOLVE_CHILD("Groß geworden", "Entwickle dich zum Kind", "🌱", 30, 1, {
        if (it.counters.maxStage >= Stage.CHILD.ordinal) 1L else 0L
    }),
    EVOLVE_TEEN("Teenie-Zeit", "Entwickle dich zum Teenager", "🌿", 50, 1, {
        if (it.counters.maxStage >= Stage.TEEN.ordinal) 1L else 0L
    }),
    EVOLVE_ADULT("Ausgewachsen", "Erreiche die Erwachsenenform", "🌳", 100, 1, {
        if (it.counters.maxStage >= Stage.ADULT.ordinal) 1L else 0L
    }),
    HATCH_5("Eier-Experte", "Brüte 5 Eier aus", "🥚", 100, 5, { it.counters.hatched.toLong() }),
    HATCH_20("Eierkönig", "Brüte 20 Eier aus", "🪺", 300, 20, { it.counters.hatched.toLong() }),
    ALBUM_10("Sammler", "Entdecke 10 verschiedene Wesen", "📖", 60, 10, { it.discoveredCount.toLong() }),
    ALBUM_25("Forscher", "Entdecke 25 verschiedene Wesen", "🔎", 150, 25, { it.discoveredCount.toLong() }),
    ALBUM_50("Wesen-Experte", "Entdecke 50 verschiedene Wesen", "🧭", 300, 50, { it.discoveredCount.toLong() }),
    ALBUM_100("Meister aller Wesen", "Entdecke alle 100 Wesen", "👑", 1000, 100, { it.discoveredCount.toLong() }),
    LINES_6("Weltenbummler", "Entdecke Wesen aus 6 Ei-Sorten", "🗺️", 150, 6, { s ->
        Form.creatures.filter { it.name in s.album }.map { it.line }.distinct().size.toLong()
    }),
    SHINY_1("Glitzerfund", "Entdecke ein schillerndes Wesen", "💎", 150, 1, { it.counters.shinies.toLong() }),
    LEGEND_1("Legendär!", "Ziehe ein legendäres Wesen groß", "🐲", 300, 1, { it.counters.legends.toLong() }),
    KUSCHEL_5("Volles Haus", "Habe 5 Haustiere gleichzeitig", "🏡", 120, 5, { it.allPets.size.toLong() }),
    PLANT_1("Grüner Daumen", "Pflanze deinen ersten Samen", "🌱", 20, 1, { it.counters.plantings.toLong() }),
    HARVEST_25("Erntedank", "Ernte 25-mal", "🧺", 100, 25, { it.counters.harvests.toLong() }),
    HARVEST_200("Bauernhof-Profi", "Ernte 200-mal", "🚜", 300, 200, { it.counters.harvests.toLong() }),
    TRIP_1("Kofferpacken", "Schicke ein Haustier auf einen Ausflug", "🎒", 30, 1, { it.counters.trips.toLong() }),
    TRIP_30("Weltreisender", "Mache 30 Ausflüge", "✈️", 250, 30, { it.counters.trips.toLong() }),
    STICKERS_20("Stickerfan", "Sammle 20 verschiedene Sticker", "🎴", 100, 20, { s -> s.stickers.count { it.value > 0 }.toLong() }),
    STICKER_SET("Heft voll!", "Vervollständige ein Stickerset", "📒", 100, 1, { it.stickerSetsDone.size.toLong() }),
    STICKERS_ALL("Stickerkönig", "Sammle alle 48 Sticker", "🏅", 500, 48, { s -> s.stickers.count { it.value > 0 }.toLong() }),
    STREAK_3("Dranbleiber", "Spiele 3 Tage in Folge", "🔥", 30, 3, { it.counters.maxStreak.toLong() }),
    STREAK_7("Wochenheld", "Spiele 7 Tage in Folge", "📅", 80, 7, { it.counters.maxStreak.toLong() }),
    STREAK_30("Unzertrennlich", "Spiele 30 Tage in Folge", "🏅", 300, 30, { it.counters.maxStreak.toLong() }),
    DAYS_30("Treue Seele", "Spiele an 30 Tagen", "📆", 150, 30, { it.counters.daysPlayed.toLong() }),
    DAYS_100("Bester Freund", "Spiele an 100 Tagen", "💯", 400, 100, { it.counters.daysPlayed.toLong() }),
    DAYS_365("Ein ganzes Jahr!", "Spiele an 365 Tagen", "🎂", 1000, 365, { it.counters.daysPlayed.toLong() }),
    WEEKLY_10("Wochen-Champion", "Schließe 10 Wochenaufgaben ab", "🗓️", 150, 10, { it.counters.weeklies.toLong() }),
    PASS_20("Pass-Profi", "Erreiche Stufe 20 im Jahreszeiten-Pass", "🎫", 150, 20, { it.counters.passTiers.toLong() }),
    PASS_40("Jahreszeiten-Meister", "Erreiche Stufe 40 im Jahreszeiten-Pass", "🏵️", 300, 40, { it.counters.passTiers.toLong() }),
    EVENT_1("Festtagsgast", "Mach bei einem Fest mit", "🎉", 50, 1, { it.counters.eventsJoined.toLong() }),
    EVENT_6("Partylöwe", "Mach bei 6 Festen mit", "🥳", 300, 6, { it.counters.eventsJoined.toLong() }),
    BIRTHDAY_1("Alles Gute!", "Feiere den Geburtstag deines Haustiers", "🎂", 50, 1, { it.counters.birthdays.toLong() }),
    OUTFIT_10("Stilikone", "Zieh dein Haustier 10-mal um", "👗", 60, 10, { it.counters.outfits.toLong() }),
    RICH_1000("Sparschwein", "Besitze 1.000 Münzen auf einmal", "🐷", 100, 1000, { it.coins.toLong() }),
    RICH_5000("Schatzkiste", "Besitze 5.000 Münzen auf einmal", "💰", 300, 5000, { it.coins.toLong() }),
    COLLECTOR_5("Modeikone", "Besitze 5 Accessoires", "🎩", 60, 5, { s ->
        s.owned.count { id -> Catalog[id]?.kind?.wearable == true }.toLong()
    }),
    DECORATOR("Innenarchitekt", "Kaufe ein neues Zimmer oder Möbelstück", "🛋️", 40, 1, { s ->
        s.owned.count { id -> id != Catalog.DEFAULT_ROOM && id !in Catalog.defaultFurniture && (Catalog[id]?.kind == ItemKind.ROOM || Catalog[id]?.kind?.furniture == true) }.toLong()
    }),
    QUESTS_20("Aufgabenjäger", "Schließe 20 Tagesaufgaben ab", "📜", 80, 20, { it.counters.quests.toLong() }),
    QUESTS_100("Aufgabenmeister", "Schließe 100 Tagesaufgaben ab", "📚", 250, 100, { it.counters.quests.toLong() }),
    NIGHT_OWL("Nachteule", "Schau zwischen 0 und 4 Uhr nachts vorbei", "🦉", 25, 1, { if (it.counters.nightOwl) 1L else 0L }),
    PERFECT("Rundum glücklich", "Alle Werte gleichzeitig über 90", "🌈", 50, 1, { if (it.counters.perfectCare) 1L else 0L });

    fun progress(state: GameState): Long = metric(state).coerceAtMost(target.toLong())

    fun isMet(state: GameState): Boolean = metric(state) >= target
}
