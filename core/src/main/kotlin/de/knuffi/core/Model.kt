package de.knuffi.core

import kotlinx.serialization.Serializable

@Serializable
enum class ThemeMode(val title: String) {
    SYSTEM("System"),
    LIGHT("Hell"),
    DARK("Dunkel"),
}

@Serializable
enum class Difficulty(val title: String, val description: String) {
    RELAXED(
        "Entspannt",
        "Dein Haustier wird traurig oder krank, wenn du es vernachlässigst, kann aber nicht sterben.",
    ),
    CLASSIC(
        "Klassisch",
        "Werte sinken schneller. Wird es zu lange vernachlässigt, reist es zu den Sternen.",
    ),
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
    val line: EggLine = EggLine.KNUFFEL,
    /** Rare colour variant ("schillernd"). */
    val shiny: Boolean = false,
) {
    /** Pets are identified by their birth time. */
    val id: Long get() = bornAt
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
    val bestRunner: Int = 0,
    val bestBubbles: Int = 0,
    val bestSimon: Int = 0,
    val totalSteps: Long = 0,
    val bestDaySteps: Int = 0,
    val coinsEarned: Long = 0,
    val evolutions: Int = 0,
    val hatched: Int = 0,
    val maxLevel: Int = 1,
    val maxStage: Int = 0,
    val maxStreak: Int = 0,
    val quests: Int = 0,
    val weeklies: Int = 0,
    val nightOwl: Boolean = false,
    val perfectCare: Boolean = false,
    val sleeps: Int = 0,
    val daysPlayed: Int = 0,
    val plantings: Int = 0,
    val harvests: Int = 0,
    val waterings: Int = 0,
    val trips: Int = 0,
    val eggsFound: Int = 0,
    val shinies: Int = 0,
    val legends: Int = 0,
    val birthdays: Int = 0,
    val eventsJoined: Int = 0,
    val passTiers: Int = 0,
    val outfits: Int = 0,
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
data class WeeklyState(
    val week: Long = -1,
    val quests: List<QuestProgress> = emptyList(),
    val bonusClaimed: Boolean = false,
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
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val overlayPet: Boolean = false,
    val look: LookStyle = LookStyle.ZAUBER,
    val batteryHintSeen: Boolean = false,
)

@Serializable
data class Memorial(
    val name: String,
    val form: Form,
    val level: Int,
    val bornAt: Long,
    val endedAt: Long,
    val shiny: Boolean = false,
)

@Serializable
enum class Slot(val title: String) {
    HAT("Kopf"),
    FACE("Gesicht"),
    NECK("Hals"),
    ROOM("Zimmer"),
    WALL("Tapete"),
    RUG("Teppich"),
    BED("Bett"),
    PLANT("Pflanze"),
    LAMP("Lampe"),
    PICTURE("Bild"),
}

@Serializable
data class GameState(
    val version: Int = 3,
    val onboarded: Boolean = false,
    val difficulty: Difficulty = Difficulty.RELAXED,
    /** The active pet. */
    val pet: Pet? = null,
    /** Pets resting in the "Kuschelhaus". They don't change while resting. */
    val resting: List<Pet> = emptyList(),
    val restSlots: Int = 3,
    /** Eggs waiting to be hatched. */
    val eggs: Map<EggLine, Int> = emptyMap(),
    /** Discovered forms ("FORM" and "FORM*" for shiny ones). */
    val album: Set<String> = emptySet(),
    val stickers: Map<String, Int> = emptyMap(),
    val stickerSetsDone: Set<String> = emptySet(),
    val coins: Int = 0,
    val inventory: Map<String, Int> = emptyMap(),
    val owned: Set<String> = emptySet(),
    val equipped: Map<Slot, String> = emptyMap(),
    val counters: Counters = Counters(),
    val achievements: Map<String, Long> = emptyMap(),
    val daily: DailyState = DailyState(),
    val weekly: WeeklyState = WeeklyState(),
    val steps: StepState = StepState(),
    val settings: Settings = Settings(),
    val memorials: List<Memorial> = emptyList(),
    val garden: Garden = Garden(),
    val trips: List<Trip> = emptyList(),
    val pass: PassState = PassState(),
    val event: EventState = EventState(),
    /** One-time rewards and celebrations that already happened. */
    val milestones: Set<String> = emptySet(),
    val cooldowns: Map<String, Long> = emptyMap(),
    val notifyLog: Map<String, Long> = emptyMap(),
    val lastSimulated: Long = 0,
) {
    fun count(itemId: String): Int = inventory[itemId] ?: 0
    fun eggCount(line: EggLine): Int = eggs[line] ?: 0
    fun isUnlocked(achievement: Achievement): Boolean = achievements.containsKey(achievement.name)
    val room: String get() = equipped[Slot.ROOM] ?: Catalog.DEFAULT_ROOM
    val totalEggs: Int get() = eggs.values.sum()
    val allPets: List<Pet> get() = listOfNotNull(pet) + resting
    fun onTrip(petId: Long): Trip? = trips.firstOrNull { it.petId == petId }
    fun discovered(form: Form, shiny: Boolean = false): Boolean = (if (shiny) "${form.name}*" else form.name) in album
    val discoveredCount: Int get() = Form.creatures.count { it.name in album }
}

@Serializable
enum class MiniGame(val title: String, val emoji: String, val description: String) {
    CATCH("Futterfang", "🧺", "Fange leckeres Essen und weiche den Bomben aus!"),
    MEMORY("Memory", "🃏", "Finde alle Paare mit möglichst wenigen Zügen."),
    WHACK("Blitz-Tap", "⚡", "Tippe schnell auf dein Haustier, wenn es auftaucht. Aber nicht auf die Gewitterwolken!"),
    RUNNER("Hüpf-Lauf", "🏃", "Tippe zum Springen! Weiche Hindernissen aus und sammle Münzen."),
    BUBBLES("Blubberblasen", "🫧", "Lass die Blasen in der richtigen Farbe platzen."),
    SIMON("Melodie", "🎵", "Merke dir die Reihenfolge und spiel sie nach."),
}

enum class ReactionKind { EAT, PLAY, PET, CLEAN, HEAL, SLEEP, WAKE, REFUSE, DRINK, BUY, GAME }

sealed interface GameEvent {
    data class LevelUp(val level: Int, val coins: Int) : GameEvent
    data class Evolved(val from: Form, val to: Form) : GameEvent
    data class AchievementUnlocked(val achievement: Achievement) : GameEvent
    data class QuestDone(val type: QuestType, val weekly: Boolean = false) : GameEvent
    data class Message(val text: String) : GameEvent
    data class CoinsGained(val amount: Int) : GameEvent
    data class XpGained(val amount: Int) : GameEvent
    data class Reaction(val kind: ReactionKind, val itemId: String? = null) : GameEvent
    data object Hatched : GameEvent
    data class Died(val memorial: Memorial) : GameEvent
    data class EggFound(val line: EggLine, val reason: String) : GameEvent
    data class NewForm(val form: Form, val shiny: Boolean) : GameEvent
    data class StickersGot(val ids: List<String>) : GameEvent
    data class Harvested(val itemId: String, val count: Int) : GameEvent
    data class TripReturned(val petName: String, val loot: TripLoot) : GameEvent
    data class Birthday(val petName: String, val months: Int, val coins: Int) : GameEvent
    data class PassTierUp(val tier: Int) : GameEvent
    data class TokensGained(val amount: Int) : GameEvent
}

data class Outcome(val state: GameState, val events: List<GameEvent> = emptyList())
