package de.knuffi.core

import kotlinx.serialization.Serializable

@Serializable
enum class VisualStyle(val title: String, val subtitle: String) {
    KAWAII("Kawaii", "Pastell, weich & knuddelig"),
    PIXEL("Pixel-Retro", "Wie früher, nur schöner"),
    MINIMAL("Minimal", "Klar, ruhig & modern"),
}

@Serializable
enum class Difficulty(val title: String, val description: String) {
    RELAXED(
        "Entspannt",
        "Dein Haustier wird traurig oder krank, wenn du es vernachlässigst, kann aber nicht sterben.",
    ),
    CLASSIC(
        "Klassisch",
        "Werte sinken schneller. Wird es zu lange vernachlässigt, reist es zu den Sternen und du beginnst mit einem neuen Ei.",
    ),
}

@Serializable
enum class Stage(val title: String, val minLevel: Int) {
    EGG("Ei", 0),
    BABY("Baby", 1),
    CHILD("Kind", 3),
    TEEN("Teenager", 7),
    ADULT("Erwachsen", 13),
}

@Serializable
enum class Form(val stage: Stage, val title: String, val description: String) {
    EGG(Stage.EGG, "Ei", "Etwas bewegt sich darin …"),
    BABY(Stage.BABY, "Knuffel", "Ein winziges, flauschiges Wesen."),
    HOPSI(Stage.CHILD, "Hopsi", "Fröhlich und verspielt, mit langen Hüpfohren."),
    GRUMMEL(Stage.CHILD, "Grummel", "Etwas stachelig … aber mit gutem Herz."),
    FLITZER(Stage.TEEN, "Flitzer", "Sportlich und immer in Bewegung."),
    MAMPFI(Stage.TEEN, "Mampfi", "Rund, gemütlich und immer hungrig."),
    LUMI(Stage.TEEN, "Lumi", "Ausgeglichen, mit leuchtender Antenne."),
    STACHLI(Stage.TEEN, "Stachli", "Launisch und stachelig."),
    DRAKO(Stage.ADULT, "Drako", "Ein stolzer kleiner Drache."),
    STELLARIS(Stage.ADULT, "Stellaris", "Ein Sternenwesen voller Magie."),
    MOCHI_KOENIG(Stage.ADULT, "Mochi-König", "Rund, weich und königlich."),
    SCHATTLING(Stage.ADULT, "Schattling", "Ein kleiner Geist: vernachlässigt, aber treu."),
}

enum class Mood(val title: String) {
    HAPPY("Glücklich"),
    OKAY("Zufrieden"),
    SAD("Traurig"),
    SICK("Krank"),
    SLEEPING("Schläft"),
    GONE("Bei den Sternen"),
}

enum class Need(val title: String, val emoji: String) {
    SICK("krank", "🤒"),
    HUNGRY("hungrig", "🍙"),
    DIRTY("schmutzig", "🫧"),
    BORED("gelangweilt", "🎾"),
    TIRED("müde", "💤"),
}

@Serializable
data class CareLog(
    val mistakes: Int = 0,
    val activity: Int = 0,
    val food: Int = 0,
    val moodSum: Double = 0.0,
    val moodHours: Double = 0.0,
    /** Bitmask of stats that are currently in a critical state (edge detection for care mistakes). */
    val criticalFlags: Int = 0,
) {
    val averageMood: Double get() = if (moodHours < 0.5) 70.0 else moodSum / moodHours
}

@Serializable
data class Pet(
    val name: String,
    val form: Form = Form.EGG,
    val bornAt: Long,
    val hatchedAt: Long = 0,
    val stageStartedAt: Long = bornAt,
    val hatchTaps: Int = 0,
    val satiety: Double = 80.0,
    val joy: Double = 80.0,
    val energy: Double = 90.0,
    val hygiene: Double = 100.0,
    val health: Double = 100.0,
    val xp: Int = 0,
    val level: Int = 1,
    val sleeping: Boolean = false,
    val sick: Boolean = false,
    val poops: Int = 0,
    val nextPoopAt: Long = 0,
    val care: CareLog = CareLog(),
    val alive: Boolean = true,
) {
    val isEgg: Boolean get() = form == Form.EGG
    val stage: Stage get() = form.stage
    val average: Double get() = (satiety + joy + energy + hygiene + health) / 5.0
    val lowest: Double get() = minOf(satiety, joy, energy, hygiene, health)
    val xpToNext: Int get() = Leveling.xpToNext(level)

    fun mood(): Mood = when {
        !alive -> Mood.GONE
        sleeping -> Mood.SLEEPING
        sick -> Mood.SICK
        lowest < 15 || average < 35 -> Mood.SAD
        joy >= 65 && average >= 62 -> Mood.HAPPY
        else -> Mood.OKAY
    }

    /** Current needs, most urgent first. */
    fun needs(): List<Need> {
        if (!alive || isEgg) return emptyList()
        val list = mutableListOf<Pair<Need, Double>>()
        if (sick) list += Need.SICK to -1.0
        if (satiety < 35) list += Need.HUNGRY to satiety
        if (hygiene < 35 || poops > 0) list += Need.DIRTY to (if (poops > 0) minOf(hygiene, 30.0 - poops) else hygiene)
        if (joy < 35 && !sleeping) list += Need.BORED to joy
        if (energy < 25 && !sleeping) list += Need.TIRED to energy
        return list.sortedBy { it.second }.map { it.first }
    }

    fun ageDays(now: Long): Int {
        val start = if (hatchedAt > 0) hatchedAt else bornAt
        return ((now - start) / 86_400_000L).toInt().coerceAtLeast(0)
    }
}

@Serializable
data class Counters(
    val feeds: Int = 0,
    val plays: Int = 0,
    val pets: Int = 0,
    val cleans: Int = 0,
    val heals: Int = 0,
    val gamesPlayed: Int = 0,
    val purchases: Int = 0,
    val bestCatch: Int = 0,
    val bestMemoryMoves: Int = 0,
    val bestWhack: Int = 0,
    val totalSteps: Long = 0,
    val bestDaySteps: Int = 0,
    val coinsEarned: Long = 0,
    val evolutions: Int = 0,
    val hatched: Int = 0,
    val maxLevel: Int = 1,
    val maxStage: Int = 0,
    val maxStreak: Int = 0,
    val quests: Int = 0,
    val nightOwl: Boolean = false,
    val perfectCare: Boolean = false,
    val sleeps: Int = 0,
)

@Serializable
data class QuestProgress(
    val type: QuestType,
    val target: Int,
    val progress: Int = 0,
    val claimed: Boolean = false,
) {
    val done: Boolean get() = progress >= target
}

@Serializable
data class DailyState(
    val day: Long = -1,
    val quests: List<QuestProgress> = emptyList(),
    val bonusClaimed: Boolean = false,
    val lastLoginDay: Long = -1,
    val streak: Int = 0,
    val rewardClaimedDay: Long = -1,
)

@Serializable
data class StepState(
    val lastSensor: Float = -1f,
    val day: Long = -1,
    val today: Int = 0,
    val claimedTiers: Set<Int> = emptySet(),
)

@Serializable
data class Settings(
    val notifications: Boolean = true,
    val quietHours: Boolean = true,
    val quietStart: Int = 22,
    val quietEnd: Int = 8,
    val stepGoal: Int = 5000,
    val stepsAvailable: Boolean = true,
    val haptics: Boolean = true,
)

@Serializable
data class Memorial(
    val name: String,
    val form: Form,
    val level: Int,
    val bornAt: Long,
    val endedAt: Long,
)

@Serializable
enum class Slot(val title: String) {
    HAT("Kopf"),
    FACE("Gesicht"),
    NECK("Hals"),
    ROOM("Zimmer"),
}

@Serializable
data class GameState(
    val version: Int = 1,
    val onboarded: Boolean = false,
    val style: VisualStyle = VisualStyle.KAWAII,
    val difficulty: Difficulty = Difficulty.RELAXED,
    val pet: Pet? = null,
    val coins: Int = 0,
    val inventory: Map<String, Int> = emptyMap(),
    val owned: Set<String> = emptySet(),
    val equipped: Map<Slot, String> = emptyMap(),
    val counters: Counters = Counters(),
    val achievements: Map<String, Long> = emptyMap(),
    val daily: DailyState = DailyState(),
    val steps: StepState = StepState(),
    val settings: Settings = Settings(),
    val memorials: List<Memorial> = emptyList(),
    val cooldowns: Map<String, Long> = emptyMap(),
    val notifyLog: Map<String, Long> = emptyMap(),
    val lastSimulated: Long = 0,
) {
    fun count(itemId: String): Int = inventory[itemId] ?: 0
    fun isUnlocked(achievement: Achievement): Boolean = achievements.containsKey(achievement.name)
    val room: String get() = equipped[Slot.ROOM] ?: Catalog.DEFAULT_ROOM
}

@Serializable
enum class MiniGame(val title: String, val emoji: String, val description: String) {
    CATCH("Futterfang", "🧺", "Fange leckeres Essen und weiche den Bomben aus!"),
    MEMORY("Memory", "🃏", "Finde alle Paare mit möglichst wenigen Zügen."),
    WHACK("Blitz-Tap", "⚡", "Tippe schnell auf dein Haustier, wenn es auftaucht. Aber nicht auf die Gewitterwolken!"),
}

enum class ReactionKind { EAT, PLAY, PET, CLEAN, HEAL, SLEEP, WAKE, REFUSE, DRINK, BUY, GAME }

sealed interface GameEvent {
    data class LevelUp(val level: Int, val coins: Int) : GameEvent
    data class Evolved(val from: Form, val to: Form) : GameEvent
    data class AchievementUnlocked(val achievement: Achievement) : GameEvent
    data class QuestDone(val type: QuestType) : GameEvent
    data class Message(val text: String) : GameEvent
    data class CoinsGained(val amount: Int) : GameEvent
    data class XpGained(val amount: Int) : GameEvent
    data class Reaction(val kind: ReactionKind, val itemId: String? = null) : GameEvent
    data object Hatched : GameEvent
    data class Died(val memorial: Memorial) : GameEvent
}

data class Outcome(val state: GameState, val events: List<GameEvent> = emptyList())
