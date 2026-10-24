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
    /** Coat of a real cat (see [Skins.furs]). */
    val fur: String? = null,
    /** Costume skin won at a festival, worn instead of the normal colours. */
    val skin: String? = null,
    /** A feeling caused by something that happened, lasts until [feelingUntil]. */
    val feeling: Emotion? = null,
    val feelingUntil: Long = 0,
    /** Last time the player did something with the pet (for "lonely"). */
    val lastCare: Long = 0,
    val job: Job? = null,
    val jobXp: Int = 0,
    val lastShift: Long = 0,
    /** Talent points per talent. */
    val talents: Map<Talent, Int> = emptyMap(),
    /** Practice points per trick (see [Tricks]). */
    val tricks: Map<Trick, Int> = emptyMap(),
    /** Mum and dad, if the pet hatched from a family egg. */
    val parents: List<Long> = emptyList(),
    /** Form of the other parent; its colours shine through a little. */
    val blend: String? = null,
) {
    /** Pets are identified by their birth time. */
    val id: Long get() = bornAt
    val isEgg: Boolean get() = form == Form.EGG
    val stage: Stage get() = form.stage
    val average: Double get() = (satiety + joy + energy + hygiene + health) / 5.0
    val lowest: Double get() = minOf(satiety, joy, energy, hygiene, health)
    val xpToNext: Int get() = Leveling.xpToNext(level)
    val isCat: Boolean get() = line == EggLine.KATZE

    /** A real animal (cat, rabbit, hamster or guinea pig). */
    val isReal: Boolean get() = line.real

    /** Rabbit, hamster or guinea pig. */
    val isSmall: Boolean get() = line.small
    val jobLevel: Int get() = Jobs.level(jobXp)

    fun emotion(now: Long): Emotion = Emotions.current(this, now)
    fun talentLevel(t: Talent): Int = Talents.level(talents[t] ?: 0)

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
    val shifts: Int = 0,
    val chats: Int = 0,
    val planes: Int = 0,
    val wipes: Int = 0,
    val ladders: Int = 0,
    val prizes: Int = 0,
    val cooked: Int = 0,
    val finds: Int = 0,
    val visits: Int = 0,
    val storyChapters: Int = 0,
    val photos: Int = 0,
    val routines: Int = 0,
    val secrets: Int = 0,
    val hiddenFound: Int = 0,
    /** Best results of the newer mini games by game name. */
    val bests: Map<String, Int> = emptyMap(),
    val wishes: Int = 0,
    val tricksLearned: Int = 0,
    val trickPractice: Int = 0,
    val trickShows: Int = 0,
    val tickles: Int = 0,
    val touches: Int = 0,
    val carries: Int = 0,
    val roomGames: Int = 0,
    val tournaments: Int = 0,
    val tournamentWins: Int = 0,
    val voice: Int = 0,
    val adventureSteps: Int = 0,
    val monthlies: Int = 0,
    val monthGoals: Int = 0,
    val familyEggs: Int = 0,
    val roomsBuilt: Int = 0,
    val duels: Int = 0,
    val duelWins: Int = 0,
    val notesPlayed: Int = 0,
    val songs: Int = 0,
    val cardsCrafted: Int = 0,
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
    /** Parts of the daily routine done today. */
    val routine: Set<Routine> = emptySet(),
    val routineClaimed: Boolean = false,
    /** The hidden object of the day was found. */
    val hiddenFound: Boolean = false,
    /** What happened today, for the diary (feed, game, pet, chat …). */
    val log: Map<String, Int> = emptyMap(),
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
    /** How much care the pet needs, 0 (very little) to 4 (a lot). See [CareTuning]. */
    val careLevel: Int = CareTuning.NORMAL,
    /** How much the pet sleeps, 0 (hardly) to 4 (a lot). See [CareTuning]. */
    val sleepLevel: Int = CareTuning.NORMAL,
    /** Screen toys, 0 (off) to 4 (very often). See [ToyTuning]. */
    val toyLadder: Int = 2,
    val toyPlanes: Int = 2,
    val toyFootprints: Int = 2,
    val toyPaint: Int = 1,
    /** Speed of the mini games: 0 slow, 1 normal, 2 fast. */
    val gameSpeed: Int = 1,
    /** Sound volume, 0 (off) to 4. */
    val soundVolume: Int = 2,
    val weatherMode: WeatherMode = WeatherMode.SIMULATED,
    /** Place for the real weather (0/0 = use the phone's location). */
    val weatherLat: Double = 0.0,
    val weatherLon: Double = 0.0,
    val weatherPlace: String = "",
    /** Show the pet's name above it on the home screen (wallpaper and floating pet). */
    val showNameTag: Boolean = true,
    /** The floating pet sits still in the top left corner. */
    val overlayPinned: Boolean = false,
    /** Real 3D pets (off = the drawn look, e.g. for older phones). */
    val graphics3d: Boolean = true,
    /** How smoothly the pet moves on the home screen: 0 smooth, 1 balanced, 2 battery saver. */
    val screenPower: Int = 1,
    /** The pet reacts to blowing, clapping and singing (microphone, nothing is recorded). */
    val micReactions: Boolean = false,
    /** The house with several rooms (false: the single classic room). */
    val houseMode: Boolean = true,
    /** Saves a backup into Downloads/Knuffi once a week. */
    val autoBackup: Boolean = true,
    /** The house and its rooms in 3D (false: the drawn rooms). */
    val house3d: Boolean = true,
)

/** Player-adjustable pace of the needs and of sleep. Level 2 is the original balance. */
object CareTuning {
    const val MIN = 0
    const val MAX = 4
    const val NORMAL = 2

    val careTitles = listOf("Sehr wenig", "Wenig", "Normal", "Viel", "Sehr viel")
    val sleepTitles = listOf("Kaum", "Wenig", "Normal", "Viel", "Sehr viel")

    private val needFactors = doubleArrayOf(0.5, 0.75, 1.0, 1.45, 1.9)
    private val tireFactors = doubleArrayOf(0.45, 0.7, 1.0, 1.25, 1.5)
    private val restFactors = doubleArrayOf(2.3, 1.6, 1.0, 0.85, 0.7)

    private fun idx(level: Int) = level.coerceIn(MIN, MAX)

    /** Speed of hunger, boredom, dirt and poops. */
    fun needs(level: Int): Double = needFactors[idx(level)]

    /** Speed at which energy drops while awake (and what playing costs). */
    fun tiring(level: Int): Double = tireFactors[idx(level)]

    /** Speed at which energy comes back while sleeping. */
    fun resting(level: Int): Double = restFactors[idx(level)]

    /** Roughly how long a full sleep lasts, from falling asleep exhausted to waking up rested. */
    fun napHours(level: Int): Double = (100.0 - Engine.FALL_ASLEEP_ENERGY) / (Engine.SLEEP_RECOVERY_PER_HOUR * resting(level))

    /** Roughly how long the pet stays awake during the day after a full sleep. */
    fun awakeHours(level: Int): Double = (100.0 - Engine.FALL_ASLEEP_ENERGY) / (Engine.DAY_TIRING_PER_HOUR * tiring(level))
}

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
    BACK("Rücken"),
    TOY("Spielecke"),
    DECO("Sitzecke"),
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
    val petKind: PetKind = PetKind.FANTASY,
    /** Costume skins won at festivals. */
    val skins: Set<String> = emptySet(),
    val chat: ChatState = ChatState(),
    /** Recent changes of the pet's feeling, newest last. */
    val moodLog: List<MoodEntry> = emptyList(),
    /** Collected finds from trips and stories. */
    val finds: Map<String, Int> = emptyMap(),
    val findSetsDone: Set<String> = emptySet(),
    /** Last real weather from the internet (only used in the real weather mode). */
    val weather: WeatherState = WeatherState(),
    val story: StoryState = StoryState(),
    /** A pet from the Kuschelhaus visiting the active pet. */
    val visit: Visit? = null,
    /** A second visitor (three pets in the room). */
    val visit2: Visit? = null,
    /** What the pet wishes for right now. */
    val wish: Wish? = null,
    val nextWishAt: Long = 0,
    val monthly: MonthlyState = MonthlyState(),
    val adventure: AdventureState = AdventureState(),
    /** Tournament wins per cup name. */
    val cups: Map<String, Int> = emptyMap(),
    /** Friendship points between two pets (see [Friends.key]). */
    val friendship: Map<String, Int> = emptyMap(),
    /** Secrets found (see [Secret]). */
    val secrets: Set<String> = emptySet(),
    /** Easter egg: fantasy players may also adopt kittens. */
    val catsUnlocked: Boolean = false,
    /** The pet's diary, newest last. */
    val diary: List<DiaryEntry> = emptyList(),
    /** One-time rewards and celebrations that already happened. */
    val milestones: Set<String> = emptySet(),
    val cooldowns: Map<String, Long> = emptyMap(),
    val notifyLog: Map<String, Long> = emptyMap(),
    /** The rooms of the house (see [House]). */
    val house: HouseState = HouseState(),
    /** How far the furniture of the current room was moved, in screen widths. */
    val furnitureOffsets: Map<Slot, Float> = emptyMap(),
    /** Eggs from best friends, waiting to be hatched. */
    val familyEggs: List<FamilyEgg> = emptyList(),
    /** Trading cards per form name. */
    val cards: Map<String, Int> = emptyMap(),
    val cardDust: Int = 0,
    /** Songs recorded in the music room. */
    val songs: List<Song> = emptyList(),
    /** Time of the last automatic backup. */
    val lastAutoBackup: Long = 0,
    val lastSimulated: Long = 0,
) {
    fun count(itemId: String): Int = inventory[itemId] ?: 0
    fun eggCount(line: EggLine): Int = eggs[line] ?: 0
    fun isUnlocked(achievement: Achievement): Boolean = achievements.containsKey(achievement.name)
    val room: String get() = equipped[Slot.ROOM] ?: Catalog.DEFAULT_ROOM
    val totalEggs: Int get() = eggs.values.sum()
    val allPets: List<Pet> get() = listOfNotNull(pet) + resting

    /** Pets from the Kuschelhaus that are visiting right now. */
    val visitors: List<Pet> get() = listOfNotNull(visit, visit2).mapNotNull { v -> resting.firstOrNull { it.id == v.petId } }
    fun onTrip(petId: Long): Trip? = trips.firstOrNull { it.petId == petId }
    fun discovered(form: Form, shiny: Boolean = false): Boolean = (if (shiny) "${form.name}*" else form.name) in album
    val discoveredCount: Int get() = Form.creatures.count { it.name in album }
    val catMode: Boolean get() = petKind == PetKind.CAT
}

/** Groups of mini games in the games hall. */
enum class GameGroup(val title: String, val emoji: String) {
    ACTION("Action", "⚡"),
    KNOBELN("Knobeln", "🧠"),
    RUHIG("Ruhig", "🌿"),
}

@Serializable
enum class MiniGame(
    val title: String,
    val emoji: String,
    val description: String,
    /** Calm games have no time pressure. */
    val calm: Boolean = false,
    /** For these games a lower result is better (moves). */
    val lowerIsBetter: Boolean = false,
    val unit: String = "Punkte",
    /** Puzzle and brain games. */
    val puzzle: Boolean = false,
) {
    CATCH("Futterfang", "🧺", "Fange leckeres Essen und weiche den Bomben aus!"),
    MEMORY("Memory", "🃏", "Finde alle Paare mit möglichst wenigen Zügen.", lowerIsBetter = true, unit = "Züge", puzzle = true),
    WHACK("Blitz-Tap", "⚡", "Tippe schnell auf dein Haustier, wenn es auftaucht. Aber nicht auf die Gewitterwolken!", unit = "Treffer"),
    RUNNER("Hüpf-Lauf", "🏃", "Tippe zum Springen! Weiche Hindernissen aus und sammle Münzen.", unit = "Meter"),
    BUBBLES("Blubberblasen", "🫧", "Lass die Blasen in der richtigen Farbe platzen.", unit = "Blasen"),
    SIMON("Melodie", "🎵", "Merke dir die Reihenfolge und spiel sie nach.", unit = "Töne", puzzle = true),
    COOK("Kochstudio", "🍳", "Wirf die Zutaten in der richtigen Reihenfolge in den Topf. Fertige Gerichte kommen in deinen Vorrat!", unit = "Gerichte"),
    MATCH("Leckerli-Reihe", "🍬", "Tausche Leckerlis, bis drei gleiche in einer Reihe liegen.", unit = "Leckerlis", puzzle = true),
    FLIGHT("Papierflug", "✈️", "Tippe, um den Papierflieger steigen zu lassen. Flieg durch die Ringe!", unit = "Ringe"),
    STACK("Kissenturm", "🛏️", "Lass die Kissen genau aufeinanderfallen. Wie hoch wird dein Turm?", unit = "Kissen"),
    FISHING("Angeln", "🎣", "Wirf die Angel aus, wenn ein Fisch darunter schwimmt.", unit = "Punkte"),
    PUZZLE("Schiebepuzzle", "🧩", "Schiebe die Teile, bis das Bild deines Haustiers wieder stimmt. Ganz ohne Zeitdruck.", calm = true, lowerIsBetter = true, unit = "Züge"),
    COLORING("Ausmalbild", "🖍️", "Male ein Mandala in deinen Lieblingsfarben aus. Zum Entspannen.", calm = true, unit = "%"),
    YARN("Wollknäuel", "🧶", "Verschiebe die Knoten, bis sich kein Faden mehr kreuzt. Ganz in Ruhe.", calm = true, lowerIsBetter = true, unit = "Züge"),
    PACMAN("Leckerli-Jagd", "👻", "Wische durchs Labyrinth, futtere alle Leckerlis und weiche den Gespenstern aus. Kekse machen sie harmlos!", unit = "Punkte"),
    SPOT("Unterschiede finden", "🔎", "Zwei Bilder, fast gleich. Tippe unten auf alles, was anders ist!", unit = "Unterschiede", puzzle = true),
    WORDS("Wortsuche", "🔤", "Finde die versteckten Wörter im Buchstabengitter. Wische über die Buchstaben!", unit = "Wörter", puzzle = true),
    SHADOW("Schattenrätsel", "👤", "Wessen Schatten ist das? Wähle das richtige Wesen aus!", unit = "Richtige", puzzle = true),
    SPACE("Sternenflug", "🚀", "Steuere die Rakete, schieße Meteore ab und besiege das Ufo!", unit = "Punkte"),
    JUMPER("Wolkenhüpfer", "☁️", "Hüpfe von Wolke zu Wolke immer höher hinaus. Tippe links oder rechts zum Lenken.", unit = "Meter"),
    RACE("Kartrennen", "🏎️", "Wechsle die Spur, weiche Hindernissen aus und sammle Münzen!", unit = "Meter"),
    ;

    val group: GameGroup get() = when {
        calm -> GameGroup.RUHIG
        puzzle -> GameGroup.KNOBELN
        else -> GameGroup.ACTION
    }
}

enum class ReactionKind { EAT, PLAY, PET, CLEAN, HEAL, SLEEP, WAKE, REFUSE, DRINK, BUY, GAME, WORK, TALK }

sealed interface GameEvent {
    data class LevelUp(val level: Int, val coins: Int) : GameEvent
    data class Evolved(val from: Form, val to: Form) : GameEvent
    data class AchievementUnlocked(val achievement: Achievement) : GameEvent
    data class QuestDone(val type: QuestType, val weekly: Boolean = false, val monthly: Boolean = false) : GameEvent
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
    data class PrizeWon(val name: String, val emoji: String, val skin: Boolean) : GameEvent
    data class FeelingChanged(val emotion: Emotion) : GameEvent
    data class TalentUp(val talent: Talent, val level: Int) : GameEvent
    data class JobUp(val job: Job, val level: Int) : GameEvent
    data class Spun(val result: SpinResult) : GameEvent
    data class SecretFound(val secret: Secret) : GameEvent
    data class HiddenFound(val emoji: String, val name: String) : GameEvent
    data class TrickPracticed(val trick: Trick, val success: Boolean, val learned: Boolean) : GameEvent
    data class TrickShown(val trick: Trick) : GameEvent
    data class Touched(val zone: TouchZone) : GameEvent
    data class WishMade(val wish: Wish) : GameEvent
    data class WishFulfilled(val wish: Wish, val coins: Int) : GameEvent
    data class AdventureStep(val station: Int, val title: String, val reward: String, val emoji: String, val mapDone: Boolean) : GameEvent
    data class TournamentDone(val cup: Cup, val place: Int, val coins: Int, val firstWin: Boolean) : GameEvent
    data class GoalDone(val goal: LongGoal) : GameEvent
    data class CardsOpened(val forms: List<Form>) : GameEvent
    data class RoomBuilt(val room: RoomKind) : GameEvent
    data class RoomEntered(val room: RoomKind) : GameEvent
}

data class Outcome(val state: GameState, val events: List<GameEvent> = emptyList())
