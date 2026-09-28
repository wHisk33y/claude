package de.knuffi.core

import java.time.Instant
import java.time.ZoneId
import java.util.Locale
import kotlin.random.Random

object TimeUtil {
    fun epochDay(now: Long, zone: ZoneId): Long =
        Instant.ofEpochMilli(now).atZone(zone).toLocalDate().toEpochDay()

    fun hour(now: Long, zone: ZoneId): Int = Instant.ofEpochMilli(now).atZone(zone).hour

    fun isNight(now: Long, zone: ZoneId): Boolean {
        val h = hour(now, zone)
        return h >= 22 || h < 7
    }

    fun formatNumber(n: Long): String = String.format(Locale.GERMANY, "%,d", n)
    fun formatNumber(n: Int): String = formatNumber(n.toLong())
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

    /** Picks the next form based on how the pet was cared for during the current stage. */
    fun nextForm(pet: Pet): Form {
        val care = pet.care
        val bad = care.mistakes >= 5 || care.averageMood < 35
        val sporty = care.activity > care.food * 1.3 + 1
        val foodie = care.food > care.activity * 1.3 + 1
        return when (pet.form.stage) {
            Stage.EGG -> Form.BABY
            Stage.BABY -> if (care.mistakes <= 3 && care.averageMood >= 45) Form.HOPSI else Form.GRUMMEL
            Stage.CHILD -> when {
                bad -> Form.STACHLI
                sporty -> Form.FLITZER
                foodie -> Form.MAMPFI
                else -> Form.LUMI
            }
            Stage.TEEN -> when {
                bad -> Form.SCHATTLING
                sporty -> Form.DRAKO
                foodie -> Form.MOCHI_KOENIG
                else -> Form.STELLARIS
            }
            Stage.ADULT -> pet.form
        }
    }
}

enum class QuestKind { COUNT, MAX }

@kotlinx.serialization.Serializable
enum class QuestType(
    val template: String,
    val emoji: String,
    val target: Int,
    val coins: Int,
    val xp: Int,
    val kind: QuestKind = QuestKind.COUNT,
) {
    FEED("Füttere {n}-mal", "🍙", 3, 15, 15),
    PLAY("Spiele {n}-mal mit deinem Haustier", "🎾", 3, 15, 15),
    PET("Streichle {n}-mal", "💞", 10, 10, 10),
    CLEAN("Mach {n}-mal sauber", "🫧", 1, 10, 10),
    GAMES("Spiele {n} Minispiele", "🎮", 2, 20, 25),
    STEPS("Gehe {n} Schritte", "👟", 3000, 25, 30, QuestKind.MAX),
    CATCH_SCORE("Erreiche {n} Punkte in Futterfang", "🧺", 150, 20, 25, QuestKind.MAX),
    MEMORY("Löse {n} Memory", "🃏", 1, 20, 20),
    WHACK_SCORE("Schaffe {n} Treffer bei Blitz-Tap", "⚡", 20, 20, 25, QuestKind.MAX),
    SHOP("Kaufe {n} Sache im Shop", "🛍️", 1, 10, 10),
    SLEEP("Bring dein Haustier {n}-mal ins Bett", "🌙", 1, 10, 10);

    fun label(target: Int = this.target): String = template.replace("{n}", TimeUtil.formatNumber(target))
}

object Quests {
    const val PER_DAY = 3
    const val BONUS_COINS = 30
    const val BONUS_XP = 30

    fun roll(day: Long, stepsAvailable: Boolean): List<QuestProgress> {
        val pool = QuestType.entries.filter { stepsAvailable || it != QuestType.STEPS }
        val rnd = Random(day * 7919L + 17L)
        return pool.shuffled(rnd).take(PER_DAY).map { QuestProgress(it, it.target) }
    }
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
    CATCH_300("Fangkünstler", "Erreiche 300 Punkte in Futterfang", "🧺", 60, 300, { it.counters.bestCatch.toLong() }),
    MEMORY_12("Elefantengedächtnis", "Löse Memory in höchstens 12 Zügen", "🧠", 60, 1, {
        if (it.counters.bestMemoryMoves in 1..12) 1L else 0L
    }),
    WHACK_40("Blitzschnell", "Schaffe 40 Treffer bei Blitz-Tap", "⚡", 60, 40, { it.counters.bestWhack.toLong() }),
    STEPS_DAY_10K("Wandervogel", "Gehe 10.000 Schritte an einem Tag", "🥾", 80, 10_000, { it.counters.bestDaySteps.toLong() }),
    STEPS_100K("Marathon", "Gehe insgesamt 100.000 Schritte", "🏃", 150, 100_000, { it.counters.totalSteps }),
    LEVEL_5("Aufsteiger", "Erreiche Level 5", "⭐", 30, 5, { it.counters.maxLevel.toLong() }),
    LEVEL_10("Profi", "Erreiche Level 10", "🌟", 60, 10, { it.counters.maxLevel.toLong() }),
    LEVEL_20("Meister", "Erreiche Level 20", "💫", 150, 20, { it.counters.maxLevel.toLong() }),
    EVOLVE_CHILD("Groß geworden", "Entwickle dich zum Kind", "🌱", 30, 1, {
        if (it.counters.maxStage >= Stage.CHILD.ordinal) 1L else 0L
    }),
    EVOLVE_TEEN("Teenie-Zeit", "Entwickle dich zum Teenager", "🌿", 50, 1, {
        if (it.counters.maxStage >= Stage.TEEN.ordinal) 1L else 0L
    }),
    EVOLVE_ADULT("Ausgewachsen", "Erreiche die Erwachsenenform", "🌳", 100, 1, {
        if (it.counters.maxStage >= Stage.ADULT.ordinal) 1L else 0L
    }),
    STREAK_3("Dranbleiber", "Spiele 3 Tage in Folge", "🔥", 30, 3, { it.counters.maxStreak.toLong() }),
    STREAK_7("Wochenheld", "Spiele 7 Tage in Folge", "📅", 80, 7, { it.counters.maxStreak.toLong() }),
    STREAK_30("Unzertrennlich", "Spiele 30 Tage in Folge", "🏅", 300, 30, { it.counters.maxStreak.toLong() }),
    RICH_1000("Sparschwein", "Besitze 1.000 Münzen auf einmal", "🐷", 100, 1000, { it.coins.toLong() }),
    COLLECTOR_5("Modeikone", "Besitze 5 Accessoires", "🎩", 60, 5, { s ->
        s.owned.count { id -> Catalog[id]?.let { it.kind == ItemKind.HAT || it.kind == ItemKind.FACE || it.kind == ItemKind.NECK } == true }.toLong()
    }),
    DECORATOR("Innenarchitekt", "Kaufe ein neues Zimmer", "🛋️", 40, 1, { s ->
        s.owned.count { id -> id != Catalog.DEFAULT_ROOM && Catalog[id]?.kind == ItemKind.ROOM }.toLong()
    }),
    QUESTS_20("Aufgabenjäger", "Schließe 20 Tagesaufgaben ab", "📜", 80, 20, { it.counters.quests.toLong() }),
    NIGHT_OWL("Nachteule", "Schau zwischen 0 und 4 Uhr nachts vorbei", "🦉", 25, 1, { if (it.counters.nightOwl) 1L else 0L }),
    PERFECT("Rundum glücklich", "Alle Werte gleichzeitig über 90", "🌈", 50, 1, { if (it.counters.perfectCare) 1L else 0L });

    fun progress(state: GameState): Long = metric(state).coerceAtMost(target.toLong())

    fun isMet(state: GameState): Boolean = metric(state) >= target
}
