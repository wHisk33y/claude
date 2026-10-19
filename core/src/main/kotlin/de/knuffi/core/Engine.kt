package de.knuffi.core

import java.time.ZoneId
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.random.Random

sealed interface Action {
    data class Start(
        val difficulty: Difficulty,
        val name: String,
        val look: LookStyle = LookStyle.ZAUBER,
        val line: EggLine = EggLine.KNUFFEL,
        val kind: PetKind = PetKind.FANTASY,
        val fur: String? = null,
    ) : Action
    data object HatchTap : Action
    data class Feed(val itemId: String) : Action
    data class UseItem(val itemId: String) : Action
    data object Play : Action
    data object Stroke : Action
    data object Clean : Action
    data object ToggleSleep : Action
    data class GameFinished(val game: MiniGame, val score: Int) : Action
    data class Buy(val itemId: String) : Action
    data object BuyMystery : Action
    data class Equip(val itemId: String) : Action
    data class Unequip(val slot: Slot) : Action
    data object ClaimDaily : Action
    data class ClaimQuest(val index: Int) : Action
    data object ClaimQuestBonus : Action
    data class ClaimWeekly(val index: Int) : Action
    data object ClaimWeeklyBonus : Action
    data class ClaimStepTier(val index: Int) : Action
    data class StepReading(val sensorValue: Float) : Action
    data class Rename(val name: String) : Action
    data class SetDifficulty(val difficulty: Difficulty) : Action
    data class UpdateSettings(val settings: Settings) : Action
    data class NewEgg(val name: String) : Action
    data class MarkNotified(val key: String) : Action

    // Collection
    data class HatchEgg(val line: EggLine, val name: String) : Action
    data class SwitchPet(val petId: Long) : Action
    data class ReleasePet(val petId: Long) : Action
    /** Says goodbye to several pets at once. The active pet may be among them if another one can take over. */
    data class ReleasePets(val petIds: Set<Long>) : Action
    data object BuyRestSlot : Action

    // Garden
    data class Plant(val plot: Int, val seedId: String) : Action
    data class Water(val plot: Int) : Action
    data class Harvest(val plot: Int) : Action
    data object BuyPlot : Action

    // Trips
    data class StartTrip(val petId: Long, val destination: Destination) : Action
    data class ClaimTrip(val petId: Long) : Action

    // Season pass & events
    data class ClaimPass(val tier: Int) : Action
    data class BuyEventOffer(val offerId: String) : Action
    data object SpinWheel : Action

    // Skins, jobs, chat & screen toys
    data class ApplySkin(val skinId: String?) : Action
    data class ChooseJob(val job: Job) : Action
    data object Work : Action
    data object OpenChat : Action
    data class Chat(val text: String) : Action
    data class Toy(val kind: ToyKind) : Action

    // Kitchen, stories, friends, photos, weather
    data class Cook(val recipeId: String) : Action
    data class StoryChoose(val choice: Int) : Action
    data class Invite(val petId: Long) : Action
    data object PlayTogether : Action
    data object PhotoTaken : Action
    data class SetWeather(val weather: WeatherState) : Action

    // Secrets, hidden objects, friends
    data class FindSecret(val secret: Secret) : Action
    data object FindHidden : Action
    data class FriendPlay(val moment: FriendMoment, val petId: Long = 0L) : Action
    data class SendHome(val petId: Long) : Action

    // Tricks, touching, room games and voice
    data class PracticeTrick(val trick: Trick) : Action
    data class ShowTrick(val trick: Trick) : Action
    data class Touch(val zone: TouchZone) : Action
    data object Carry : Action
    data class RoomGameFinished(val game: RoomGame, val score: Int) : Action
    data class Voice(val kind: VoiceKind) : Action

    // Monthly goals, adventure map, long-term goals, tournaments
    data class ClaimMonthly(val index: Int) : Action
    data object ClaimMonthlyBonus : Action
    data object AdventureStep : Action
    data class ClaimGoal(val goal: LongGoal) : Action
    data class TournamentFinished(val cup: Cup, val place: Int, val rivals: List<Long> = emptyList()) : Action

    // House, family, cards, music and backups
    data class GoToRoom(val room: RoomKind) : Action
    data class BuildRoom(val room: RoomKind) : Action
    data class MoveFurniture(val slot: Slot, val dx: Float) : Action
    data object ResetFurniture : Action
    data class HatchFamilyEgg(val id: Long, val name: String) : Action
    data object DustDuplicates : Action
    data class CraftCard(val form: Form) : Action
    data class CardDuelFinished(val won: Int, val lost: Int) : Action
    data class MusicPlayed(val notes: Int) : Action
    data class SaveSong(val song: Song) : Action
    data class DeleteSong(val index: Int) : Action
    data class BackupMade(val at: Long) : Action
}

data class GameReward(val coins: Int, val xp: Int)

object Engine {
    const val SIM_STEP_MS = 60_000L
    const val MAX_SIM_MS = 14L * 24 * 3_600_000L
    const val HATCH_TAPS = 5
    const val MAX_REST_SLOTS = 12
    const val SHINY_ODDS = 16
    const val FALL_ASLEEP_ENERGY = 18.0
    const val SLEEP_RECOVERY_PER_HOUR = 18.0
    const val DAY_TIRING_PER_HOUR = 4.5

    private const val FLAG_SATIETY = 1
    private const val FLAG_JOY = 2
    private const val FLAG_HYGIENE = 4
    private const val FLAG_HEALTH = 8

    /** Advances time without any user interaction (worker, widget, app resume). */
    fun tick(state: GameState, now: Long, zone: ZoneId, userPresent: Boolean = false): Outcome {
        val tx = Tx(state, zone)
        tx.migrate(now)
        val feeling = tx.emotionNow(now)
        tx.simulate(now, zone)
        tx.refreshDay(now, zone, userPresent)
        tx.normalizeHouse(now)
        tx.checkAchievements(now)
        tx.trackEmotion(feeling, now)
        return tx.outcome()
    }

    /** Applies a user action after bringing the simulation up to date. */
    fun perform(state: GameState, action: Action, now: Long, zone: ZoneId, userPresent: Boolean = true): Outcome {
        val tx = Tx(state, zone)
        tx.migrate(now)
        val feeling = tx.emotionNow(now)
        tx.simulate(now, zone)
        tx.refreshDay(now, zone, userPresent)
        tx.apply(action, now, zone)
        tx.normalizeHouse(now)
        tx.checkAchievements(now)
        tx.trackEmotion(feeling, now)
        return tx.outcome()
    }

    fun gameReward(game: MiniGame, score: Int): GameReward = when (game) {
        MiniGame.CATCH -> GameReward(coins = (score / 10).coerceIn(2, 60), xp = (5 + score / 8).coerceAtMost(50))
        MiniGame.MEMORY -> GameReward(coins = (46 - 2 * (score - 8)).coerceIn(10, 40), xp = 20)
        MiniGame.WHACK -> GameReward(coins = (score * 6 / 5).coerceIn(2, 50), xp = (5 + score).coerceAtMost(50))
        MiniGame.RUNNER -> GameReward(coins = (score / 15).coerceIn(2, 60), xp = (5 + score / 12).coerceAtMost(50))
        MiniGame.BUBBLES -> GameReward(coins = (score * 3 / 2).coerceIn(2, 50), xp = (5 + score).coerceAtMost(50))
        MiniGame.SIMON -> GameReward(coins = (score * 5).coerceIn(2, 50), xp = (score * 5).coerceIn(5, 50))
        MiniGame.COOK -> GameReward(coins = (score * 6).coerceIn(2, 60), xp = (5 + score * 4).coerceAtMost(50))
        MiniGame.MATCH -> GameReward(coins = (score / 4).coerceIn(2, 60), xp = (5 + score / 5).coerceAtMost(50))
        MiniGame.FLIGHT -> GameReward(coins = (score * 3).coerceIn(2, 60), xp = (5 + score * 2).coerceAtMost(50))
        MiniGame.STACK -> GameReward(coins = (score * 4).coerceIn(2, 60), xp = (5 + score * 3).coerceAtMost(50))
        MiniGame.FISHING -> GameReward(coins = (score / 2).coerceIn(2, 60), xp = (5 + score / 3).coerceAtMost(50))
        MiniGame.PUZZLE -> GameReward(coins = if (score <= 0) 5 else (60 - score / 2).coerceIn(15, 45), xp = 25)
        MiniGame.COLORING -> GameReward(coins = (score * 30 / 100).coerceIn(5, 30), xp = if (score >= 100) 20 else 8)
        MiniGame.YARN -> GameReward(coins = if (score <= 0) 5 else (55 - score).coerceIn(15, 40), xp = 25)
        MiniGame.PACMAN -> GameReward(coins = (score / 10).coerceIn(2, 60), xp = (5 + score / 15).coerceAtMost(50))
        MiniGame.SPOT -> GameReward(coins = (score * 4).coerceIn(2, 60), xp = (5 + score * 3).coerceAtMost(50))
        MiniGame.WORDS -> GameReward(coins = (score * 5).coerceIn(2, 50), xp = (5 + score * 4).coerceAtMost(50))
        MiniGame.SHADOW -> GameReward(coins = (score * 5).coerceIn(2, 50), xp = (5 + score * 4).coerceAtMost(50))
        MiniGame.SPACE -> GameReward(coins = (score / 12).coerceIn(2, 60), xp = (5 + score / 15).coerceAtMost(50))
        MiniGame.JUMPER -> GameReward(coins = (score / 10).coerceIn(2, 60), xp = (5 + score / 12).coerceAtMost(50))
        MiniGame.RACE -> GameReward(coins = (score / 15).coerceIn(2, 60), xp = (5 + score / 18).coerceAtMost(50))
    }

    fun gameMaxCoins(game: MiniGame): Int = when (game) {
        MiniGame.CATCH, MiniGame.RUNNER, MiniGame.COOK, MiniGame.MATCH, MiniGame.FLIGHT, MiniGame.STACK, MiniGame.FISHING, MiniGame.PACMAN,
        MiniGame.SPOT, MiniGame.SPACE, MiniGame.JUMPER, MiniGame.RACE -> 60
        MiniGame.MEMORY, MiniGame.YARN -> 40
        MiniGame.PUZZLE -> 45
        MiniGame.COLORING -> 30
        MiniGame.WHACK, MiniGame.BUBBLES, MiniGame.SIMON, MiniGame.WORDS, MiniGame.SHADOW -> 50
    }

    /** Best result of a game so far, or null. */
    fun best(state: GameState, game: MiniGame): Int? = when (game) {
        MiniGame.CATCH -> state.counters.bestCatch
        MiniGame.MEMORY -> state.counters.bestMemoryMoves
        MiniGame.WHACK -> state.counters.bestWhack
        MiniGame.RUNNER -> state.counters.bestRunner
        MiniGame.BUBBLES -> state.counters.bestBubbles
        MiniGame.SIMON -> state.counters.bestSimon
        else -> state.counters.bests[game.name] ?: 0
    }.takeIf { it > 0 }

    fun isRecord(state: GameState, game: MiniGame, score: Int): Boolean {
        val b = best(state, game) ?: return score > 0
        return if (game.lowerIsBetter) score in 1 until b else score > b
    }

    /** 1 to 3 stars for a finished round. */
    fun stars(game: MiniGame, score: Int): Int {
        val ratio = gameReward(game, score).coins / gameMaxCoins(game).toFloat()
        return when {
            ratio >= 0.75f -> 3
            ratio >= 0.4f -> 2
            else -> 1
        }
    }

    /** Reason why a mini game cannot be started right now, or null. */
    fun gameBlocker(state: GameState): String? {
        val pet = state.pet ?: return "Du hast noch kein Haustier."
        return when {
            !pet.alive -> "${pet.name} ist nicht mehr da."
            pet.isEgg -> "Das Ei muss erst schlüpfen."
            pet.sleeping -> "${pet.name} schläft gerade. Psst!"
            pet.sick -> "${pet.name} ist krank und muss sich erst erholen."
            pet.energy < 15 -> "${pet.name} ist zu müde zum Spielen."
            else -> null
        }
    }

    fun canClaimDaily(state: GameState, now: Long, zone: ZoneId): Boolean {
        val today = TimeUtil.epochDay(now, zone)
        return state.daily.lastLoginDay == today && state.daily.rewardClaimedDay != today
    }

    fun restSlotPrice(slots: Int): Int = 250 * (slots - 2)

    /** Highest level any pet reached; unlocks trip destinations and shop items. */
    fun playerLevel(state: GameState): Int = max(state.counters.maxLevel, state.pet?.level ?: 1)

    /** Currently running event, if the calendar says so. */
    fun activeEvent(now: Long, zone: ZoneId): SeasonEvent? = EventCalendar.active(now, zone)

    /** The festival the saved event state belongs to. */
    fun eventOf(state: GameState): SeasonEvent? =
        state.event.key.substringBefore('-').let { k -> SeasonEvent.entries.firstOrNull { it.name == k } }

    /** Reason why the pet cannot go to work right now, or null. */
    fun workBlocker(state: GameState, now: Long): String? {
        val pet = state.pet ?: return "Du hast noch kein Haustier."
        return when {
            pet.isEgg || !pet.alive -> "Das geht gerade nicht."
            pet.job == null -> "${pet.name} hat noch keinen Beruf."
            pet.sleeping -> "${pet.name} schläft gerade."
            pet.sick -> "${pet.name} ist krank und bleibt heute zu Hause."
            pet.energy < 20 -> "${pet.name} ist zu müde zum Arbeiten."
            pet.satiety < 15 -> "Mit leerem Bauch arbeitet es sich schlecht."
            now - pet.lastShift < Jobs.SHIFT_COOLDOWN_MS && now >= pet.lastShift ->
                "Nächste Schicht in ${TimeUtil.formatDuration(Jobs.SHIFT_COOLDOWN_MS - (now - pet.lastShift))}."
            else -> null
        }
    }

    internal class Tx(var s: GameState, private val zone: ZoneId) {
        val events = mutableListOf<GameEvent>()

        fun outcome() = Outcome(s, events.toList())

        private fun emit(e: GameEvent) {
            events += e
        }

        private fun message(text: String) = emit(GameEvent.Message(text))

        private inline fun updatePet(block: (Pet) -> Pet) {
            val p = s.pet ?: return
            s = s.copy(pet = block(p))
        }

        private fun counters(block: (Counters) -> Counters) {
            s = s.copy(counters = block(s.counters))
        }

        private fun addCoins(amount: Int) {
            if (amount <= 0) return
            s = s.copy(
                coins = s.coins + amount,
                counters = s.counters.copy(coinsEarned = s.counters.coinsEarned + amount),
            )
            emit(GameEvent.CoinsGained(amount))
            questProgress(QuestType.COINS, amount)
        }

        private fun addItem(id: String, count: Int = 1) {
            if (count <= 0) return
            s = s.copy(inventory = s.inventory + (id to s.count(id) + count))
        }

        private fun addEgg(found: EggLine, reason: String) {
            // In the cat mode every fantasy egg becomes a basket with a kitten.
            val line = if (s.catMode && found.fantasy) EggLine.KATZE else found
            s = s.copy(eggs = s.eggs + (line to s.eggCount(line) + 1))
            counters { it.copy(eggsFound = it.eggsFound + 1) }
            emit(GameEvent.EggFound(line, reason))
        }

        private fun cooldownReady(key: String, now: Long, ms: Long): Boolean {
            val last = s.cooldowns[key] ?: 0L
            if (now - last < ms && now >= last) return false
            s = s.copy(cooldowns = s.cooldowns + (key to now))
            return true
        }

        // ---------------------------------------------------------------- migration

        /** Fills in data that older saves don't have yet. */
        fun migrate(now: Long) {
            if (!s.onboarded) return
            var album = s.album
            for (p in s.allPets) if (!p.isEgg) album = album + p.form.name + (if (p.shiny) setOf("${p.form.name}*") else emptySet())
            for (m in s.memorials) if (m.form != Form.EGG) album = album + m.form.name
            var owned = s.owned
            var equipped = s.equipped
            if (Catalog.defaultFurniture.any { it !in owned }) {
                owned = owned + Catalog.defaultFurniture
                for ((slot, id) in Catalog.defaultEquipped(s.settings.look)) if (slot !in equipped) equipped = equipped + (slot to id)
            }
            if (album != s.album || owned != s.owned || equipped != s.equipped) {
                s = s.copy(album = album, owned = owned, equipped = equipped, version = 3)
            }
            if (s.equipped.values.any { Catalog[it] == null }) s = s.copy(equipped = s.equipped.filterValues { Catalog[it] != null })
            fun fix(p: Pet): Pet = p.copy(
                lastCare = if (p.lastCare <= 0L) now else p.lastCare,
                fur = if (p.isReal && p.fur == null) Skins.defaultFur(p.line) else p.fur,
            )
            val pet = s.pet
            if (pet != null && (pet.lastCare <= 0L || (pet.isReal && pet.fur == null))) s = s.copy(pet = fix(pet))
            if (s.resting.any { it.isReal && it.fur == null }) s = s.copy(resting = s.resting.map { fix(it) })
            if (s.visit2 != null && s.visit == null) s = s.copy(visit = s.visit2, visit2 = null)
            val missingCards = s.album.filter { Cards.isCard(it) && (s.cards[it] ?: 0) == 0 }
            if (missingCards.isNotEmpty()) s = s.copy(cards = s.cards + missingCards.associateWith { 1 })
            if ("cards:welcome" !in s.milestones) {
                s = s.copy(milestones = s.milestones + "cards:welcome", inventory = s.inventory + (Cards.PACK to s.count(Cards.PACK) + 2))
            }
        }

        // ---------------------------------------------------------------- feelings

        fun emotionNow(now: Long): Emotion? = s.pet?.takeIf { it.alive && !it.isEgg }?.emotion(now)

        /** Logs and announces a change of the pet's feeling. */
        fun trackEmotion(before: Emotion?, now: Long) {
            val after = emotionNow(now) ?: return
            if (after == before) return
            s = s.copy(moodLog = (s.moodLog + MoodEntry(after, now)).takeLast(20))
            emit(GameEvent.FeelingChanged(after))
        }

        /** Something happened that colours the pet's feeling for a while. */
        private fun feel(e: Emotion, now: Long) {
            updatePet { it.copy(feeling = e, feelingUntil = now + Emotions.triggerMs(e)) }
        }

        /** The player spent time with the pet. A lonely pet is extra happy. */
        private fun touch(now: Long) {
            val p = s.pet ?: return
            if (!p.alive || p.isEgg) return
            val lonely = p.lastCare > 0 && now - p.lastCare > Emotions.LONELY_AFTER_MS && !p.sleeping
            s = s.copy(pet = p.copy(lastCare = now, joy = if (lonely) min(100.0, p.joy + 10) else p.joy))
            if (lonely) {
                message("${p.name} freut sich riesig, dass du wieder da bist! 💕")
                feel(Emotion.AUFGEREGT, now)
            }
        }

        // ---------------------------------------------------------------- talents

        private fun talentGain(p: Pet, t: Talent, amount: Int): Int {
            var a = amount.toDouble()
            if (Talents.natural(p) == t) a *= 1.5
            if (Perk.TALENT in Perks.active(s)) a *= 1.3
            return max(1, a.roundToInt())
        }

        private fun addTalent(t: Talent, amount: Int, now: Long) {
            val p = s.pet ?: return
            if (amount <= 0 || !p.alive || p.isEgg) return
            val before = p.talentLevel(t)
            val xp = (p.talents[t] ?: 0) + talentGain(p, t, amount)
            val np = p.copy(talents = p.talents + (t to xp))
            s = s.copy(pet = np)
            val after = np.talentLevel(t)
            if (after > before) {
                emit(GameEvent.TalentUp(t, after))
                feel(Emotion.STOLZ, now)
            }
        }

        private fun addTalentTo(petId: Long, t: Talent, amount: Int, now: Long) {
            if (s.pet?.id == petId) {
                addTalent(t, amount, now)
                return
            }
            s = s.copy(resting = s.resting.map { p -> if (p.id == petId) p.copy(talents = p.talents + (t to (p.talents[t] ?: 0) + talentGain(p, t, amount))) else p })
        }

        // ---------------------------------------------------------------- simulation

        fun simulate(now: Long, zone: ZoneId) {
            val pet = s.pet
            if (pet == null || !pet.alive || pet.isEgg || s.lastSimulated <= 0L) {
                s = s.copy(lastSimulated = now)
                return
            }
            var t = max(s.lastSimulated, now - MAX_SIM_MS)
            if (now <= t) return
            var p: Pet = pet
            val classic = s.difficulty == Difficulty.CLASSIC
            val perks = Perks.active(s)
            val w = Weather.current(s, now, zone)
            val warm = s.equipped[Slot.NECK] != null || s.equipped[Slot.HAT] != null
            val weatherJoy = when {
                Weather.cold(w) && !warm -> 1.15
                w.kind == WeatherKind.SUN && w.tempC >= 15 -> 0.9
                else -> 1.0
            }
            while (t < now && p.alive) {
                val dt = min(SIM_STEP_MS, now - t)
                p = step(p, dt, t, zone, classic, perks, weatherJoy)
                t += dt
            }
            s = s.copy(pet = p, lastSimulated = now)
            if (!p.alive) {
                val memorial = Memorial(p.name, p.form, p.level, p.bornAt, t, p.shiny)
                s = s.copy(memorials = s.memorials + memorial)
                emit(GameEvent.Died(memorial))
            }
            checkPerfect()
        }

        private fun step(p0: Pet, dt: Long, t: Long, zone: ZoneId, classic: Boolean, perks: Set<Perk>, weatherJoy: Double = 1.0): Pet {
            val h = dt / 3_600_000.0
            val m = if (classic) 1.4 else 1.0
            val n = CareTuning.needs(s.settings.careLevel)
            val tire = CareTuning.tiring(s.settings.sleepLevel) * (if (Perk.ENERGY in perks) 0.8 else 1.0)
            val hungerF = if (Perk.HUNGER in perks) 0.8 else 1.0
            val joyF = (if (Perk.JOY in perks) 0.8 else 1.0) * weatherJoy
            val rest = CareTuning.resting(s.settings.sleepLevel)
            var p = p0
            var satiety = p.satiety
            var joy = p.joy
            var energy = p.energy
            var hygiene = p.hygiene
            var health = p.health
            var sleeping = p.sleeping
            var sick = p.sick
            var poops = p.poops
            var nextPoopAt = p.nextPoopAt

            if (sleeping) {
                satiety -= 2.5 * h * m * n * hungerF
                joy -= 0.5 * h * m * n * joyF
                hygiene -= 1.0 * h * m * n
                energy += SLEEP_RECOVERY_PER_HOUR * h * rest
                if (energy >= 100.0) {
                    energy = 100.0
                    sleeping = false
                }
            } else {
                val night = TimeUtil.isNight(t, zone)
                satiety -= 6.0 * h * m * n * hungerF
                joy -= 5.0 * h * m * n * joyF
                energy -= (if (night) 2 * DAY_TIRING_PER_HOUR else DAY_TIRING_PER_HOUR) * h * m * tire
                hygiene -= (2.5 + 3.0 * poops) * h * m * n
                if (energy < FALL_ASLEEP_ENERGY) {
                    // Falls asleep on its own when exhausted.
                    sleeping = true
                }
            }

            if (nextPoopAt in 1..(t + dt)) {
                poops = min(3, poops + 1)
                nextPoopAt = 0L
            }

            if (sick) {
                health -= 2.5 * h * m
                joy -= 2.0 * h * m * n
            }
            val critical = listOf(satiety, joy, hygiene).count { it < 15.0 }
            if (critical > 0) {
                health -= 1.2 * critical * h * m
            } else if (!sick && satiety > 50 && joy > 50 && hygiene > 50) {
                health += 1.5 * h
            }

            if (!sick && (hygiene < 25.0 || poops >= 3 || health < 35.0)) {
                val chance = 0.12 * h * (if (classic) 1.5 else 1.0)
                if (Random(t / SIM_STEP_MS * 31L + p.bornAt).nextDouble() < chance) sick = true
            }

            satiety = satiety.coerceIn(0.0, 100.0)
            joy = joy.coerceIn(0.0, 100.0)
            energy = energy.coerceIn(0.0, 100.0)
            hygiene = hygiene.coerceIn(0.0, 100.0)
            health = health.coerceIn(if (classic) 0.0 else 5.0, 100.0)

            // Care mistakes: counted once each time a stat drops into the critical zone.
            var flags = p.care.criticalFlags
            var mistakes = p.care.mistakes
            fun edge(value: Double, flag: Int) {
                if (value < 10.0 && flags and flag == 0) {
                    flags = flags or flag
                    mistakes++
                } else if (value > 30.0 && flags and flag != 0) {
                    flags = flags and flag.inv()
                }
            }
            edge(satiety, FLAG_SATIETY)
            edge(joy, FLAG_JOY)
            edge(hygiene, FLAG_HYGIENE)
            edge(health, FLAG_HEALTH)

            val mood = (satiety + joy + energy + hygiene + health) / 5.0
            val care = p.care.copy(
                mistakes = mistakes,
                criticalFlags = flags,
                moodSum = p.care.moodSum + mood * h,
                moodHours = p.care.moodHours + h,
            )
            p = p.copy(
                satiety = satiety,
                joy = joy,
                energy = energy,
                hygiene = hygiene,
                health = health,
                sleeping = sleeping,
                sick = sick,
                poops = poops,
                nextPoopAt = nextPoopAt,
                care = care,
                alive = !(classic && health <= 0.0),
            )
            return p
        }

        private fun checkPerfect() {
            val p = s.pet ?: return
            if (p.alive && !p.isEgg && p.mood() == Mood.HAPPY) questProgress(QuestType.HAPPY, 1)
            if (!s.counters.perfectCare && p.alive && !p.isEgg &&
                p.satiety > 90 && p.joy > 90 && p.energy > 90 && p.hygiene > 90 && p.health > 90
            ) {
                counters { it.copy(perfectCare = true) }
            }
        }

        // ---------------------------------------------------------------- daily stuff

        fun refreshDay(now: Long, zone: ZoneId, userPresent: Boolean) {
            val today = TimeUtil.epochDay(now, zone)
            val hasResting = s.resting.any { !it.isEgg }
            val hasJob = s.pet?.job != null
            val canChat = (s.pet?.level ?: 0) >= Chat.UNLOCK_LEVEL
            val hasTrick = s.pet?.let { Tricks.learnedCount(it) > 0 } == true
            var daily = s.daily
            if (daily.day != today && daily.day >= 0 && daily.log.isNotEmpty()) writeDiary(daily.day, daily.log)
            if (daily.day != today) {
                daily = daily.copy(
                    hiddenFound = false,
                    log = emptyMap(),
                    day = today,
                    quests = Quests.roll(today, s.settings.stepsAvailable, hasResting, hasJob, canChat, hasTrick),
                    bonusClaimed = false,
                    routine = emptySet(),
                    routineClaimed = false,
                )
            }
            val week = TimeUtil.week(now, zone)
            var weekly = s.weekly
            if (weekly.week != week) {
                weekly = WeeklyState(week = week, quests = Quests.rollWeek(week, s.settings.stepsAvailable, hasResting, hasJob, canChat, hasTrick))
            }
            val month = TimeUtil.month(now, zone)
            var monthly = s.monthly
            if (monthly.month != month) {
                monthly = MonthlyState(month = month, goals = Quests.rollMonth(month, hasResting, hasJob, hasTrick))
            }
            val date = EventCalendar.date(now, zone)
            var pass = s.pass
            val passId = EventCalendar.passId(date)
            if (pass.id != passId) pass = PassState(id = passId)
            var event = s.event
            val active = EventCalendar.active(date)
            val key = active?.let { EventCalendar.key(it, date) } ?: ""
            if (event.key != key) event = EventState(key = key)

            var counters = s.counters
            var newLogin = false
            if (userPresent && s.onboarded && daily.lastLoginDay != today) {
                val streak = if (daily.lastLoginDay == today - 1) daily.streak + 1 else 1
                daily = daily.copy(lastLoginDay = today, streak = streak)
                counters = counters.copy(maxStreak = max(counters.maxStreak, streak), daysPlayed = counters.daysPlayed + 1)
                newLogin = true
            }
            if (userPresent && s.onboarded && TimeUtil.hour(now, zone) < 4) {
                counters = counters.copy(nightOwl = true)
            }
            var steps = s.steps
            if (steps.day != today) {
                steps = steps.copy(day = today, today = 0, claimedTiers = emptySet())
            }
            s = s.copy(daily = daily, weekly = weekly, monthly = monthly, counters = counters, steps = steps, pass = pass, event = event)
            if (newLogin) {
                addPassXp(10)
                addTokens(2)
            }
            if (userPresent && s.onboarded) checkBirthday(now)
            if (s.onboarded) {
                rainWatering(now, today)
                endVisit(now)
                wishTick(now, userPresent)
                if (userPresent) familyTick(now)
            }
            if (userPresent && s.onboarded && s.pet?.alive == true && s.pet?.isEgg == false) {
                if (Secrets.midnight(now, zone)) findSecret(Secret.MIDNIGHT, now)
                if (Secrets.fullMoon(EventCalendar.date(now, zone))) findSecret(Secret.FULL_MOON, now)
            }
        }

        private fun writeDiary(day: Long, log: Map<String, Int>) {
            val p = s.pet ?: return
            if (p.isEgg || s.diary.any { it.day == day }) return
            val dayStart = java.time.LocalDate.ofEpochDay(day).atStartOfDay(zone).toInstant().toEpochMilli()
            val mood = s.moodLog.filter { it.at in dayStart until dayStart + 86_400_000L }.groupingBy { it.emotion }.eachCount().maxByOrNull { it.value }?.key
            val w = Weather.simulated(dayStart + 13 * 3_600_000L, zone).kind
            s = s.copy(diary = (s.diary + Diary.write(p.name, p.isCat, day, log, mood, w)).takeLast(60))
        }

        /** Counts something that happened today for the diary. */
        private fun dayLog(key: String, n: Int = 1) {
            s = s.copy(daily = s.daily.copy(log = s.daily.log + (key to (s.daily.log[key] ?: 0) + n)))
        }

        private fun findSecret(secret: Secret, now: Long) {
            val p = s.pet ?: return
            if (p.isEgg || !p.alive || secret.name in s.secrets) return
            s = s.copy(secrets = s.secrets + secret.name)
            counters { it.copy(secrets = it.secrets + 1) }
            emit(GameEvent.SecretFound(secret))
            addCoins(secret.coins)
            if (secret == Secret.CATS && !s.catsUnlocked) {
                s = s.copy(catsUnlocked = true)
                if (!s.catMode) {
                    addEgg(EggLine.KATZE, "Geschenk vom Katzenflüsterer")
                    message("Miau! Ab jetzt gibt es im Shop auch Katzenkörbchen – und eins bekommst du geschenkt! 🐈")
                }
            }
            dayLog("secret")
            feel(Emotion.AUFGEREGT, now)
        }

        private fun findHidden(now: Long) {
            val p = s.pet ?: return
            if (p.isEgg || !p.alive || s.daily.hiddenFound) return
            val h = Hidden.today(TimeUtil.epochDay(now, zone))
            s = s.copy(daily = s.daily.copy(hiddenFound = true))
            counters { it.copy(hiddenFound = it.hiddenFound + 1) }
            emit(GameEvent.HiddenFound(h.emoji, h.name))
            addCoins(Hidden.COINS)
            addXp(10, now)
            dayLog("hidden")
            feel(Emotion.NEUGIERIG, now)
            touch(now)
            questProgress(QuestType.HIDDEN, 1)
            if (s.counters.hiddenFound >= 7) findSecret(Secret.HIDDEN_7, now)
        }

        private fun friendMoment(m: FriendMoment, petId: Long, now: Long) {
            val byId = if (petId != 0L) {
                s.visitors.firstOrNull { it.id == petId } ?: House.residentsIn(s, s.house.current).firstOrNull { it.id == petId }
            } else {
                null
            }
            val guest = byId ?: visitor() ?: return
            val host = s.pet ?: return
            if (!cooldownReady("moment", now, 90_000L)) return
            s = s.copy(
                pet = host.applyEffect(Effect(joy = 3)),
                resting = s.resting.map { if (it.id == guest.id) it.applyEffect(Effect(joy = 3)) else it },
            )
            addFriendship(host.id, guest.id, 2)
            dayLog("visit")
        }

        /** Rainy days water the garden by themselves (once a day). */
        private fun rainWatering(now: Long, today: Long) {
            val w = Weather.current(s, now, zone)
            if (w.kind != WeatherKind.RAIN && w.kind != WeatherKind.STORM) return
            val key = "rain:$today"
            if (key in s.milestones) return
            if (s.garden.plots.none { !it.empty && !it.ready(now) }) return
            s = s.copy(
                milestones = s.milestones + key,
                garden = s.garden.copy(plots = s.garden.plots.map { if (!it.empty && !it.ready(now)) it.copy(readyAt = now + ((it.readyAt - now) * 0.8).toLong()) else it }),
            )
            message("Der Regen hat deinen Garten gegossen. 🌧️🌱")
        }

        private fun checkBirthday(now: Long) {
            val p = s.pet ?: return
            if (!p.alive || p.isEgg || p.hatchedAt <= 0L) return
            val months = p.ageDays(now) / 30
            if (months < 1) return
            val key = "bday:${p.id}:$months"
            if (key in s.milestones) return
            val coins = min(20 + 10 * months, 200)
            s = s.copy(milestones = s.milestones + key)
            counters { it.copy(birthdays = it.birthdays + 1) }
            addCoins(coins)
            addItem("cake")
            emit(GameEvent.Birthday(p.name, months, coins))
            feel(Emotion.AUFGEREGT, now)
        }

        private fun questProgress(type: QuestType, amount: Int) {
            if (amount <= 0) return
            fun advance(list: List<QuestProgress>, weekly: Boolean): List<QuestProgress>? {
                var changed = false
                val out = list.map { q ->
                    if (q.type != type || q.claimed) return@map q
                    val value = when (type.kind) {
                        QuestKind.COUNT -> q.progress + amount
                        QuestKind.MAX -> max(q.progress, amount)
                    }.coerceAtMost(q.target)
                    if (value == q.progress) return@map q
                    changed = true
                    if (!q.done && value >= q.target) emit(GameEvent.QuestDone(type, weekly))
                    q.copy(progress = value)
                }
                return if (changed) out else null
            }
            advance(s.daily.quests, false)?.let { s = s.copy(daily = s.daily.copy(quests = it)) }
            advance(s.weekly.quests, true)?.let { s = s.copy(weekly = s.weekly.copy(quests = it)) }
            // Monthly goals are counted like the weekly ones but announced separately.
            var monthChanged = false
            val month = s.monthly.goals.map { q ->
                if (q.type != type || q.claimed || q.done) return@map q
                val value = (if (type.kind == QuestKind.COUNT) q.progress + amount else max(q.progress, amount)).coerceAtMost(q.target)
                if (value == q.progress) return@map q
                monthChanged = true
                if (value >= q.target) emit(GameEvent.QuestDone(type, monthly = true))
                q.copy(progress = value)
            }
            if (monthChanged) s = s.copy(monthly = s.monthly.copy(goals = month))
        }

        private fun addPassXp(amount: Int) {
            if (amount <= 0 || s.pass.id.isEmpty()) return
            val before = SeasonPass.tier(s.pass.xp)
            val xp = min(s.pass.xp + amount, SeasonPass.TIERS * SeasonPass.XP_PER_TIER)
            s = s.copy(pass = s.pass.copy(xp = xp))
            val after = SeasonPass.tier(xp)
            for (t in (before + 1)..after) emit(GameEvent.PassTierUp(t))
            if (after > s.counters.passTiers) counters { it.copy(passTiers = after) }
        }

        private fun addTokens(amount: Int) {
            if (amount <= 0 || s.event.key.isEmpty()) return
            val first = "event:${s.event.key}" !in s.milestones
            s = s.copy(event = s.event.copy(tokens = s.event.tokens + amount, earned = s.event.earned + amount))
            if (first) {
                s = s.copy(milestones = s.milestones + "event:${s.event.key}")
                counters { it.copy(eventsJoined = it.eventsJoined + 1) }
            }
            emit(GameEvent.TokensGained(amount))
            checkPrizes()
        }

        /** Hands out festival prizes once enough tokens have been earned. */
        private fun checkPrizes() {
            val e = eventOf(s) ?: return
            for (prize in EventPrizes.of(e)) {
                if (s.event.earned >= prize.tokens && prize.id !in s.event.prizes) grantPrize(prize.id, prize.skin)
            }
        }

        private fun grantPrize(id: String, skin: Boolean) {
            s = s.copy(event = s.event.copy(prizes = s.event.prizes + id))
            val already = if (skin) id in s.skins else id in s.owned
            if (already) return
            s = if (skin) s.copy(skins = s.skins + id) else s.copy(owned = s.owned + id)
            counters { it.copy(prizes = it.prizes + 1) }
            val name = if (skin) Skins[id]?.name ?: id else Catalog[id]?.name ?: id
            val emoji = if (skin) Skins[id]?.emoji ?: "🎁" else Catalog[id]?.emoji ?: "🎁"
            emit(GameEvent.PrizeWon(name, emoji, skin))
            feel(Emotion.AUFGEREGT, s.lastSimulated)
        }

        private fun addStickers(ids: List<String>) {
            if (ids.isEmpty()) return
            var stickers = s.stickers
            for (id in ids) stickers = stickers + (id to (stickers[id] ?: 0) + 1)
            s = s.copy(stickers = stickers)
            emit(GameEvent.StickersGot(ids))
            questProgress(QuestType.STICKER, ids.size)
            for (set in StickerSet.entries) {
                if (set.name !in s.stickerSetsDone && Stickers.complete(set, s.stickers)) {
                    s = s.copy(stickerSetsDone = s.stickerSetsDone + set.name)
                    message("Stickerset „${set.title}“ komplett! +${set.reward} 🪙")
                    addCoins(set.reward)
                }
            }
        }

        // ---------------------------------------------------------------- xp & evolution

        private fun addXp(base: Int, now: Long) {
            val pet = s.pet ?: return
            if (base <= 0 || !pet.alive || pet.isEgg) return
            var f = Emotions.xpFactor(pet.emotion(now))
            if (Perk.XP in Perks.active(s)) f *= 1.1
            val amount = max(1, (base * f).roundToInt())
            emit(GameEvent.XpGained(amount))
            questProgress(QuestType.XP, amount)
            var xp = pet.xp + amount
            var level = pet.level
            while (level < Leveling.MAX_LEVEL && xp >= Leveling.xpToNext(level)) {
                xp -= Leveling.xpToNext(level)
                level++
                val coins = Leveling.levelUpCoins(level)
                emit(GameEvent.LevelUp(level, coins))
                addCoins(coins)
                if (level == Jobs.UNLOCK_LEVEL) message("Level ${Jobs.UNLOCK_LEVEL}! ${pet.name} kann jetzt einen Beruf lernen. 💼")
                if (level == Chat.UNLOCK_LEVEL) message("Level ${Chat.UNLOCK_LEVEL}! Ab jetzt kannst du mit ${pet.name} chatten. 💬")
                LevelRewards.rewards[level]?.let { id ->
                    val key = "lvl:$level"
                    if (key !in s.milestones && id !in s.owned) {
                        s = s.copy(owned = s.owned + id, milestones = s.milestones + key)
                        val item = Catalog[id]
                        message("Level $level: ${item?.name ?: id} ${item?.emoji ?: ""} freigeschaltet!")
                    }
                }
            }
            if (level >= Leveling.MAX_LEVEL) xp = min(xp, Leveling.xpToNext(level))
            if (level > pet.level) {
                s = s.copy(pet = s.pet!!.copy(feeling = Emotion.STOLZ, feelingUntil = now + Emotions.triggerMs(Emotion.STOLZ)))
            }
            val oldMax = s.counters.maxLevel
            s = s.copy(
                pet = s.pet!!.copy(xp = xp, level = level),
                counters = s.counters.copy(maxLevel = max(s.counters.maxLevel, level)),
            )
            if (oldMax < 4 && s.counters.maxLevel >= 4 && "egg_second" !in s.milestones) {
                s = s.copy(milestones = s.milestones + "egg_second")
                addEgg(randomEggLine(Random(now)), "Ein zweites Ei ist aufgetaucht!")
            }
            maybeEvolve(now)
        }

        /** Picks a (non-event) egg line, preferring lines that haven't been discovered and the chosen look. */
        private fun randomEggLine(rnd: Random): EggLine {
            val lines = EggLine.entries.filter { !it.eventOnly && it.fantasy }
            val weights = lines.map { line ->
                var w = 1.0
                if (line.forms.none { it.name in s.album }) w *= 2.5
                if (line.look == s.settings.look) w *= 1.6
                if (line == EggLine.KNUFFEL) w *= 0.5
                w
            }
            var r = rnd.nextDouble() * weights.sum()
            for ((i, w) in weights.withIndex()) {
                r -= w
                if (r <= 0) return lines[i]
            }
            return lines.last()
        }

        private fun discover(form: Form, shiny: Boolean) {
            if (form == Form.EGG) return
            var album = s.album
            val newForm = form.name !in album
            album = album + form.name
            val newShiny = shiny && "${form.name}*" !in album
            if (shiny) album = album + "${form.name}*"
            s = s.copy(album = album)
            if (newForm && (s.cards[form.name] ?: 0) == 0) s = s.copy(cards = s.cards + (form.name to 1))
            if (newShiny) counters { it.copy(shinies = it.shinies + 1) }
            if (newForm || newShiny) emit(GameEvent.NewForm(form, shiny))
        }

        private fun maybeEvolve(now: Long) {
            var pet = s.pet ?: return
            // Evolve one stage at a time; big XP jumps can trigger several evolutions.
            while (pet.alive && !pet.isEgg && pet.stage.ordinal < Leveling.stageFor(pet.level).ordinal) {
                val from = pet.form
                val to = Leveling.nextForm(pet)
                if (to == from) break
                pet = pet.copy(form = to, stageStartedAt = now, care = CareLog())
                s = s.copy(
                    pet = pet,
                    counters = s.counters.copy(
                        evolutions = s.counters.evolutions + 1,
                        maxStage = max(s.counters.maxStage, to.stage.ordinal),
                        legends = s.counters.legends + if (to.isLegend) 1 else 0,
                    ),
                )
                emit(GameEvent.Evolved(from, to))
                discover(to, pet.shiny)
                if (to.stage == Stage.ADULT) {
                    val key = "adult:${pet.id}"
                    if (key !in s.milestones) {
                        s = s.copy(milestones = s.milestones + key)
                        addEgg(randomEggLine(Random(now xor pet.id)), "${pet.name} ist erwachsen und hat ein Ei gefunden!")
                    }
                }
            }
        }

        fun checkAchievements(now: Long) {
            for (a in Achievement.entries) {
                if (s.achievements.containsKey(a.name)) continue
                if (a.isMet(s)) {
                    s = s.copy(achievements = s.achievements + (a.name to now))
                    emit(GameEvent.AchievementUnlocked(a))
                    addCoins(a.coins)
                }
            }
        }

        // ---------------------------------------------------------------- actions

        fun apply(action: Action, now: Long, zone: ZoneId) {
            // Eating happens in the kitchen, bathing in the bath and sleeping in the bedroom.
            House.roomFor(action, s)?.let { s = House.goTo(s, it) }
            when (action) {
                is Action.Start -> start(action, now, zone)
                Action.HatchTap -> hatchTap(now)
                is Action.Feed -> feed(action.itemId, now)
                is Action.UseItem -> useItem(action.itemId, now)
                Action.Play -> play(now)
                Action.Stroke -> stroke(now)
                Action.Clean -> clean(now)
                Action.ToggleSleep -> toggleSleep()
                is Action.GameFinished -> gameFinished(action.game, action.score, now)
                is Action.Buy -> buy(action.itemId, now)
                Action.BuyMystery -> buyMystery(now)
                is Action.Equip -> equip(action.itemId, counted = true)
                is Action.Unequip -> unequip(action.slot)
                Action.ClaimDaily -> claimDaily(now, zone)
                is Action.ClaimQuest -> claimQuest(action.index, now)
                Action.ClaimQuestBonus -> claimQuestBonus(now)
                is Action.ClaimWeekly -> claimWeekly(action.index, now)
                Action.ClaimWeeklyBonus -> claimWeeklyBonus(now)
                is Action.ClaimStepTier -> claimStepTier(action.index)
                is Action.StepReading -> stepReading(action.sensorValue, now)
                is Action.Rename -> updatePet { it.copy(name = cleanName(action.name, it.name)) }
                is Action.SetDifficulty -> s = s.copy(difficulty = action.difficulty)
                is Action.UpdateSettings -> updateSettings(action.settings)
                is Action.NewEgg -> newEgg(action.name, now)
                is Action.MarkNotified -> s = s.copy(notifyLog = s.notifyLog + (action.key to now))
                is Action.HatchEgg -> hatchEgg(action.line, action.name, now)
                is Action.SwitchPet -> switchPet(action.petId, now)
                is Action.ReleasePet -> releasePets(setOf(action.petId), now)
                is Action.ReleasePets -> releasePets(action.petIds, now)
                Action.BuyRestSlot -> buyRestSlot()
                is Action.Plant -> plant(action.plot, action.seedId, now)
                is Action.Water -> water(action.plot, now)
                is Action.Harvest -> harvest(action.plot, now)
                Action.BuyPlot -> buyPlot()
                is Action.StartTrip -> startTrip(action.petId, action.destination, now)
                is Action.ClaimTrip -> claimTrip(action.petId, now)
                is Action.ClaimPass -> claimPass(action.tier, now)
                is Action.BuyEventOffer -> buyEventOffer(action.offerId, now)
                Action.SpinWheel -> spinWheel(now)
                is Action.ApplySkin -> applySkin(action.skinId, now)
                is Action.ChooseJob -> chooseJob(action.job, now)
                Action.Work -> work(now)
                Action.OpenChat -> openChat(now)
                is Action.Chat -> chat(action.text, now)
                is Action.Toy -> toy(action.kind, now)
                is Action.Cook -> cook(action.recipeId, now)
                is Action.StoryChoose -> storyChoose(action.choice, now)
                is Action.Invite -> invite(action.petId, now)
                Action.PlayTogether -> playTogether(now)
                Action.PhotoTaken -> {
                    counters { it.copy(photos = it.photos + 1) }
                    questProgress(QuestType.PHOTO, 1)
                    wishProgress(WishKind.PHOTO, now)
                    if (cooldownReady("photo_xp", now, 60_000L)) addXp(3, now)
                }
                is Action.SetWeather -> s = s.copy(weather = action.weather)
                is Action.FindSecret -> findSecret(action.secret, now)
                Action.FindHidden -> findHidden(now)
                is Action.FriendPlay -> friendMoment(action.moment, action.petId, now)
                is Action.SendHome -> sendHome(action.petId)
                is Action.PracticeTrick -> practiceTrick(action.trick, now)
                is Action.ShowTrick -> showTrick(action.trick, now)
                is Action.Touch -> touchZone(action.zone, now)
                Action.Carry -> carry(now)
                is Action.RoomGameFinished -> roomGameFinished(action.game, action.score, now)
                is Action.Voice -> voice(action.kind, now)
                is Action.ClaimMonthly -> claimMonthly(action.index, now)
                Action.ClaimMonthlyBonus -> claimMonthlyBonus(now)
                Action.AdventureStep -> adventureStep(now)
                is Action.ClaimGoal -> claimGoal(action.goal)
                is Action.TournamentFinished -> tournamentFinished(action.cup, action.place, action.rivals, now)
                is Action.GoToRoom -> goToRoom(action.room)
                is Action.BuildRoom -> buildRoom(action.room, now)
                is Action.MoveFurniture -> moveFurniture(action.slot, action.dx)
                Action.ResetFurniture -> s = s.copy(furnitureOffsets = emptyMap())
                is Action.HatchFamilyEgg -> hatchFamilyEgg(action.id, action.name, now)
                Action.DustDuplicates -> dustDuplicates()
                is Action.CraftCard -> craftCard(action.form)
                is Action.CardDuelFinished -> cardDuel(action.won, action.lost, now)
                is Action.MusicPlayed -> musicPlayed(action.notes, now)
                is Action.SaveSong -> saveSong(action.song, now)
                is Action.DeleteSong -> if (action.index in s.songs.indices) s = s.copy(songs = s.songs.filterIndexed { i, _ -> i != action.index })
                is Action.BackupMade -> s = s.copy(lastAutoBackup = action.at)
            }
            checkPerfect()
        }

        private fun updateSettings(settings: Settings) {
            val stepsChanged = settings.stepsAvailable != s.settings.stepsAvailable
            val lookChanged = settings.look != s.settings.look
            s = s.copy(settings = settings)
            if (lookChanged) {
                // Switch the free default wallpaper along with the look.
                val wall = s.equipped[Slot.WALL]
                if (wall == "wall_rose" || wall == "wall_sky") {
                    s = s.copy(equipped = s.equipped + (Slot.WALL to Catalog.defaultEquipped(settings.look).getValue(Slot.WALL)))
                }
            }
            if (stepsChanged && !settings.stepsAvailable) {
                // Replace an impossible step quest.
                fun fix(list: List<QuestProgress>, weekly: Boolean): List<QuestProgress> {
                    if (list.none { it.type == QuestType.STEPS && !it.claimed }) return list
                    val used = list.map { it.type }.toSet()
                    val replacement = QuestType.entries.first { it.need == QuestNeed.NONE && it !in used && (!weekly || it.weekly > 0) }
                    return list.map {
                        if (it.type == QuestType.STEPS && !it.claimed) QuestProgress(replacement, if (weekly) replacement.weekly else replacement.target) else it
                    }
                }
                s = s.copy(daily = s.daily.copy(quests = fix(s.daily.quests, false)), weekly = s.weekly.copy(quests = fix(s.weekly.quests, true)))
            }
        }

        private fun cleanName(name: String, fallback: String): String =
            name.trim().take(16).ifBlank { fallback }

        private fun start(a: Action.Start, now: Long, zone: ZoneId) {
            val name = cleanName(a.name, "Knuffi")
            val line = if (a.line.eventOnly || !a.line.fantasy) EggLine.KNUFFEL else a.line
            val cat = a.kind == PetKind.CAT
            s = GameState(
                onboarded = true,
                difficulty = a.difficulty,
                petKind = a.kind,
                pet = if (cat) kitten(name, now, Skins[a.fur]?.takeIf { it.fur }?.id ?: Skins.DEFAULT_FUR) else Pet(name = name, bornAt = now, line = line),
                coins = Catalog.START_COINS,
                inventory = Catalog.startInventory,
                owned = setOf(Catalog.DEFAULT_ROOM) + Catalog.defaultFurniture,
                equipped = Catalog.defaultEquipped(a.look),
                settings = s.settings.copy(look = a.look),
                lastSimulated = now,
            )
            if (cat) welcomeKitten(now)
            refreshDay(now, zone, userPresent = true)
        }

        /** A real cat starts as a kitten right away, there is no egg. */
        private fun kitten(name: String, now: Long, fur: String): Pet = animalBaby(EggLine.KATZE, name, now, fur)

        /** Real animals (cats and small animals) start as babies right away. */
        private fun animalBaby(line: EggLine, name: String, now: Long, fur: String?): Pet = Pet(
            name = name,
            form = Form.find(line, Role.BABY) ?: Form.KAETZCHEN,
            bornAt = now,
            hatchedAt = now,
            stageStartedAt = now,
            line = line,
            fur = fur ?: Skins.defaultFur(line),
            shiny = Random(now).nextInt(SHINY_ODDS * 2) == 0,
            satiety = 70.0,
            joy = 85.0,
            lastCare = now,
        )

        private fun welcomeKitten(now: Long) {
            val p = s.pet ?: return
            counters { it.copy(hatched = it.hatched + 1, maxStage = max(it.maxStage, Stage.BABY.ordinal)) }
            emit(GameEvent.Hatched)
            discover(p.form, p.shiny)
            feel(Emotion.NEUGIERIG, now)
        }

        /** Used after a pet has gone to the stars in classic mode: a fresh Knuffel egg. */
        private fun newEgg(name: String, now: Long) {
            val old = s.pet
            if (old != null && old.alive && !old.isEgg) {
                s = s.copy(memorials = s.memorials + Memorial(old.name, old.form, old.level, old.bornAt, now, old.shiny))
            }
            val newName = cleanName(name, old?.name ?: "Knuffi")
            s = s.copy(
                pet = if (s.catMode) kitten(newName, now, Skins.randomFur(Random(now))) else Pet(name = newName, bornAt = now),
                lastSimulated = now,
            )
            if (s.catMode) welcomeKitten(now)
        }

        private fun hatchTap(now: Long) {
            val pet = s.pet ?: return
            if (!pet.isEgg) return
            val taps = pet.hatchTaps + 1
            if (taps < HATCH_TAPS) {
                s = s.copy(pet = pet.copy(hatchTaps = taps))
                return
            }
            val shiny = pet.shiny || Random(now xor pet.bornAt).nextInt(SHINY_ODDS) == 0
            val baby = Leveling.nextForm(pet)
            s = s.copy(
                pet = pet.copy(
                    form = baby,
                    shiny = shiny,
                    hatchTaps = taps,
                    hatchedAt = now,
                    stageStartedAt = now,
                    satiety = 70.0,
                    joy = 85.0,
                    energy = 90.0,
                    hygiene = 100.0,
                    health = 100.0,
                ),
                lastSimulated = now,
                counters = s.counters.copy(
                    hatched = s.counters.hatched + 1,
                    maxStage = max(s.counters.maxStage, Stage.BABY.ordinal),
                ),
            )
            emit(GameEvent.Hatched)
            discover(baby, shiny)
            touch(now)
            feel(Emotion.NEUGIERIG, now)
        }

        // ---------------------------------------------------------------- collection

        private fun hatchEgg(line: EggLine, name: String, now: Long) {
            if (s.eggCount(line) <= 0) return
            val active = s.pet
            val keepActive = active != null && active.alive
            if (keepActive && s.resting.size >= s.restSlots) {
                message("Das Kuschelhaus ist voll. Bau einen neuen Platz an!")
                return
            }
            val left = s.eggCount(line) - 1
            val eggs = if (left <= 0) s.eggs - line else s.eggs + (line to left)
            val real = line.real
            val pet = if (real) animalBaby(line, cleanName(name, babyName(line)), now, Skins.randomFur(line, Random(now xor 77L)))
            else Pet(name = cleanName(name, line.title.substringBefore("-")), bornAt = now, line = line)
            s = s.copy(
                pet = pet,
                resting = if (keepActive) s.resting + active!! else s.resting,
                eggs = eggs,
                lastSimulated = now,
            )
            if (keepActive) message("${active!!.name} kuschelt sich ins Kuschelhaus. 🏡")
            if (real) welcomeKitten(now)
        }

        private fun babyName(line: EggLine): String = when (line) {
            EggLine.KATZE -> "Mieze"
            EggLine.HASE -> "Hoppel"
            EggLine.HAMSTER -> "Krümel"
            EggLine.MEERI -> "Quieki"
            else -> line.title.substringBefore("-")
        }

        private fun switchPet(petId: Long, now: Long) {
            val target = s.resting.firstOrNull { it.id == petId } ?: return
            if (s.onTrip(petId) != null) {
                message("${target.name} ist gerade auf Ausflug.")
                return
            }
            val active = s.pet
            val rest = s.resting.filter { it.id != petId } + listOfNotNull(active?.takeIf { it.alive })
            s = s.copy(pet = target, resting = rest, lastSimulated = now)
            message("Hallo ${target.name}! 👋")
        }

        private fun releasePets(petIds: Set<Long>, now: Long) {
            val active = s.pet?.takeIf { it.alive && it.id in petIds }
            // The active pet can only leave if someone else takes over (preferably a hatched one).
            val stays = s.resting.filter { it.id !in petIds && s.onTrip(it.id) == null }
            val successor = if (active == null) null else stays.firstOrNull { !it.isEgg } ?: stays.firstOrNull()
            if (active != null && successor == null) message("${active.name} bleibt bei dir. Ganz ohne Haustier geht es nicht.")
            val leaving = listOfNotNull(active?.takeIf { successor != null }) +
                s.resting.filter { it.id in petIds && s.onTrip(it.id) == null }
            if (leaving.isEmpty()) return
            val gone = leaving.map { it.id }.toSet()
            s = s.copy(
                pet = successor ?: s.pet,
                resting = s.resting.filter { it.id !in gone && it.id != successor?.id },
                memorials = s.memorials + leaving.filter { !it.isEgg }.map { Memorial(it.name, it.form, it.level, it.bornAt, now, it.shiny) },
                visit = s.visit?.takeIf { successor == null && it.petId !in gone },
                visit2 = s.visit2?.takeIf { successor == null && it.petId !in gone },
                friendship = s.friendship.filterKeys { key -> key.split("-").none { id -> id.toLongOrNull()?.let { it in gone } == true } },
                lastSimulated = if (successor != null) now else s.lastSimulated,
            )
            val names = leaving.map { it.name }
            message(
                if (names.size == 1) "${names[0]} ist zu einer lieben Familie gezogen. 💫"
                else "${names.size} Haustiere sind zu lieben Familien gezogen. 💫",
            )
            if (successor != null) message("Hallo ${successor.name}! Jetzt bist du dran. 👋")
        }

        private fun buyRestSlot() {
            if (s.restSlots >= MAX_REST_SLOTS) return
            val price = restSlotPrice(s.restSlots)
            if (s.coins < price) {
                message("Dafür fehlen dir noch ${price - s.coins} Münzen.")
                return
            }
            s = s.copy(coins = s.coins - price, restSlots = s.restSlots + 1)
            message("Das Kuschelhaus hat jetzt ${s.restSlots} Plätze! 🏡")
        }

        // ---------------------------------------------------------------- care

        /** Returns the pet if it can do things right now, otherwise emits a refusal. */
        private fun activePet(allowSleeping: Boolean = false): Pet? {
            val pet = s.pet ?: return null
            if (!pet.alive || pet.isEgg) return null
            if (pet.sleeping && !allowSleeping) {
                refuse("${pet.name} schläft gerade. Psst! 💤")
                return null
            }
            return pet
        }

        private fun refuse(text: String) {
            message(text)
            emit(GameEvent.Reaction(ReactionKind.REFUSE))
        }

        /** Energy cost of an activity, scaled by the chosen sleep need. */
        private fun tiring(energy: Int): Int = (energy * CareTuning.tiring(s.settings.sleepLevel)).roundToInt()

        private fun Pet.applyEffect(e: Effect): Pet = copy(
            satiety = (satiety + e.satiety).coerceIn(0.0, 100.0),
            joy = (joy + e.joy).coerceIn(0.0, 100.0),
            energy = (energy + e.energy).coerceIn(0.0, 100.0),
            hygiene = (hygiene + e.hygiene).coerceIn(0.0, 100.0),
            health = (health + e.health).coerceIn(5.0, 100.0),
            sick = if (e.cures) false else sick,
        )

        private fun consume(item: Item): Boolean {
            if (item.unlimited) return true
            val have = s.count(item.id)
            if (have <= 0) {
                message("Du hast kein ${item.name} mehr. Im Shop gibt's Nachschub!")
                return false
            }
            val inv = if (have == 1) s.inventory - item.id else s.inventory + (item.id to have - 1)
            s = s.copy(inventory = inv)
            return true
        }

        private fun feed(itemId: String, now: Long) {
            val item = Catalog[itemId]?.takeIf { it.kind == ItemKind.FOOD } ?: return
            val pet = activePet() ?: return
            if (pet.satiety >= 95.0 && !item.snack) {
                refuse("${pet.name} ist pappsatt! 😮‍💨")
                return
            }
            if (pet.satiety >= 99.0) {
                refuse("Kein Platz mehr im Bauch!")
                return
            }
            if (!consume(item)) return
            val cook = 1.0 + 0.03 * pet.talentLevel(Talent.KOCHEN)
            val e = item.effect
            val wasHungry = pet.satiety < 35
            var p = pet.applyEffect(e.copy(satiety = (e.satiety * cook).roundToInt(), joy = (e.joy * cook).roundToInt()))
            val care = p.care.copy(food = p.care.food + if (item.snack) 2 else 1)
            p = p.copy(
                care = care,
                nextPoopAt = if (p.nextPoopAt == 0L && p.poops < 3) now + (3 * 3_600_000L / CareTuning.needs(s.settings.careLevel)).toLong() else p.nextPoopAt,
            )
            s = s.copy(pet = p, counters = s.counters.copy(feeds = s.counters.feeds + 1))
            emit(GameEvent.Reaction(ReactionKind.EAT, item.id))
            questProgress(QuestType.FEED, 1)
            if (item.snack) questProgress(QuestType.SNACK, 1)
            if (item.effect.health > 0 && !item.snack) questProgress(QuestType.HEALTHY, 1)
            if (cooldownReady("xp_feed", now, 30_000L)) addXp(4, now)
            touch(now)
            routine(Routine.BREAKFAST, now)
            routine(Routine.DINNER, now)
            dayLog("feed")
            addTalent(Talent.KOCHEN, if (item.effect.health > 0 && !item.snack) 4 else 2, now)
            if (wasHungry || item.id == Chat.favoriteFood(pet).id) feel(Emotion.FROEHLICH, now)
            wishProgress(WishKind.FOOD, now, item.id)
        }

        private fun useItem(itemId: String, now: Long) {
            val item = Catalog[itemId] ?: return
            if (item.kind == ItemKind.FOOD) {
                feed(itemId, now)
                return
            }
            if (item.kind != ItemKind.CARE) return
            when (item.id) {
                Catalog.STICKER_PACK -> {
                    if (!consume(item)) return
                    val rnd = Random(now xor s.counters.coinsEarned)
                    addStickers(List(3) { Stickers.roll(rnd).id })
                    return
                }
                Cards.PACK -> {
                    if (!consume(item)) return
                    val got = Cards.openPack(Random(now xor (s.counters.coinsEarned.toLong() shl 7)))
                    addCards(got)
                    emit(GameEvent.CardsOpened(got))
                    return
                }
                Catalog.SEED_PACK -> {
                    if (!consume(item)) return
                    val rnd = Random(now)
                    val seeds = Catalog.seeds.filter { it.sold }
                    val got = List(3) { seeds[rnd.nextInt(seeds.size)] }
                    for (seed in got) addItem(seed.id)
                    message("Neue Samen: ${got.joinToString(" ") { it.emoji }}")
                    return
                }
                Catalog.GLITTER -> {
                    val pet = s.pet
                    if (pet == null || !pet.isEgg) {
                        message("Glitzerstaub wirkt nur auf ein Ei, das noch nicht geschlüpft ist.")
                        return
                    }
                    if (pet.shiny) {
                        message("Dieses Ei glitzert schon!")
                        return
                    }
                    if (!consume(item)) return
                    s = s.copy(pet = pet.copy(shiny = true))
                    message("Das Ei funkelt jetzt in allen Farben! ✨")
                    return
                }
            }
            val pet = s.pet ?: return
            if (!pet.alive || pet.isEgg) return
            when (item.id) {
                Catalog.MEDICINE -> {
                    if (!pet.sick && pet.health >= 90) {
                        refuse("${pet.name} ist kerngesund! 💪")
                        return
                    }
                    if (!consume(item)) return
                    val wasSick = pet.sick
                    s = s.copy(
                        pet = pet.applyEffect(item.effect),
                        counters = if (wasSick) s.counters.copy(heals = s.counters.heals + 1) else s.counters,
                    )
                    emit(GameEvent.Reaction(ReactionKind.HEAL, item.id))
                    touch(now)
                    if (wasSick) addXp(10, now)
                }
                "bath" -> {
                    if (pet.sleeping) {
                        refuse("${pet.name} schläft gerade. Psst! 💤")
                        return
                    }
                    if (!consume(item)) return
                    s = s.copy(
                        pet = pet.applyEffect(item.effect).copy(poops = 0),
                        counters = s.counters.copy(cleans = s.counters.cleans + 1),
                    )
                    emit(GameEvent.Reaction(ReactionKind.CLEAN, item.id))
                    questProgress(QuestType.CLEAN, 1)
                    questProgress(QuestType.BATH, 1)
                    dayLog("bath")
                    touch(now)
                    addXp(8, now)
                    wishProgress(WishKind.BATH, now)
                }
                else -> {
                    val active = activePet() ?: return
                    if (item.effect.energy > 0 && active.energy >= 95) {
                        refuse("${active.name} hat schon genug Energie!")
                        return
                    }
                    if (!consume(item)) return
                    val snowy = item.id == "cocoa" && Weather.current(s, now, zone).kind == WeatherKind.SNOW
                    s = s.copy(pet = active.applyEffect(if (snowy) item.effect.copy(energy = item.effect.energy * 2) else item.effect))
                    emit(GameEvent.Reaction(ReactionKind.DRINK, item.id))
                    touch(now)
                    if (item.effect.hygiene > 0) wishProgress(WishKind.BATH, now)
                    if (cooldownReady("xp_care", now, 30_000L)) addXp(3, now)
                }
            }
        }

        private fun play(now: Long) {
            val pet = activePet() ?: return
            if (pet.sick) {
                refuse("${pet.name} ist zu krank zum Spielen. 🤒")
                return
            }
            if (pet.energy < 10) {
                refuse("${pet.name} ist zu müde zum Spielen.")
                return
            }
            val counted = cooldownReady("play", now, 8_000L)
            s = s.copy(
                pet = pet.applyEffect(Effect(joy = 15, energy = tiring(-7), satiety = -4, hygiene = -2)).let {
                    if (counted) it.copy(care = it.care.copy(activity = it.care.activity + 1)) else it
                },
                counters = if (counted) s.counters.copy(plays = s.counters.plays + 1) else s.counters,
            )
            emit(GameEvent.Reaction(ReactionKind.PLAY))
            if (counted) questProgress(QuestType.PLAY, 1)
            if (cooldownReady("xp_play", now, 45_000L)) addXp(6, now)
            touch(now)
            routine(Routine.PLAYTIME, now)
            if (counted) {
                addTalent(Talent.SPORT, 2, now)
                feel(Emotion.VERSPIELT, now)
                befriendVisitor(2, now)
            }
            wishProgress(WishKind.PLAY, now)
        }

        private fun stroke(now: Long) {
            val pet = s.pet ?: return
            if (!pet.alive || pet.isEgg) return
            if (!cooldownReady("stroke", now, 1_200L)) {
                emit(GameEvent.Reaction(ReactionKind.PET))
                return
            }
            if (pet.sleeping) {
                // Gentle strokes while sleeping don't wake it up.
                s = s.copy(pet = pet.applyEffect(Effect(joy = 1)))
                emit(GameEvent.Reaction(ReactionKind.PET))
                return
            }
            val cuddly = pet.emotion(now) == Emotion.VERSCHMUST
            s = s.copy(
                pet = pet.applyEffect(Effect(joy = if (cuddly) 6 else 3)),
                counters = s.counters.copy(pets = s.counters.pets + 1),
            )
            emit(GameEvent.Reaction(ReactionKind.PET))
            questProgress(QuestType.PET, 1)
            if (cooldownReady("xp_stroke", now, 20_000L)) addXp(1, now)
            touch(now)
            if (s.counters.pets % 6 == 0) feel(Emotion.VERSCHMUST, now)
            befriendVisitor(1, now)
            dayLog("pet")
            wishProgress(WishKind.CUDDLE, now)
        }

        private fun clean(now: Long) {
            val pet = s.pet ?: return
            if (!pet.alive || pet.isEgg) return
            if (pet.poops == 0 && pet.hygiene >= 90) {
                refuse("Schon blitzsauber! ✨")
                return
            }
            s = s.copy(
                pet = pet.copy(poops = 0, hygiene = 100.0, nextPoopAt = if (pet.poops >= 3) 0L else pet.nextPoopAt),
                counters = s.counters.copy(cleans = s.counters.cleans + 1),
            )
            emit(GameEvent.Reaction(ReactionKind.CLEAN))
            questProgress(QuestType.CLEAN, 1)
            if (cooldownReady("xp_clean", now, 60_000L)) addXp(4, now)
            touch(now)
            wishProgress(WishKind.BATH, now)
        }

        private fun toggleSleep() {
            val pet = s.pet ?: return
            if (!pet.alive || pet.isEgg) return
            if (pet.sleeping) {
                val grumpy = pet.energy < 40
                s = s.copy(pet = pet.copy(sleeping = false, joy = if (grumpy) max(0.0, pet.joy - 5) else pet.joy))
                emit(GameEvent.Reaction(ReactionKind.WAKE))
                if (grumpy) message("${pet.name} ist noch ganz verschlafen … 😪")
            } else {
                if (pet.energy > 85) {
                    refuse("${pet.name} ist noch gar nicht müde!")
                    return
                }
                s = s.copy(
                    pet = pet.copy(sleeping = true),
                    counters = s.counters.copy(sleeps = s.counters.sleeps + 1),
                )
                emit(GameEvent.Reaction(ReactionKind.SLEEP))
                questProgress(QuestType.SLEEP, 1)
                routine(Routine.BEDTIME, s.lastSimulated)
            }
        }

        private fun gameFinished(game: MiniGame, score: Int, now: Long) {
            val pet = s.pet ?: return
            if (!pet.alive || pet.isEgg) return
            val reward = gameReward(game, score)
            val c = s.counters
            s = s.copy(
                pet = pet.applyEffect(Effect(joy = 12, energy = tiring(-10), satiety = -5, hygiene = -3)).let {
                    it.copy(care = it.care.copy(activity = it.care.activity + 2))
                },
                counters = c.copy(
                    gamesPlayed = c.gamesPlayed + 1,
                    bestCatch = if (game == MiniGame.CATCH) max(c.bestCatch, score) else c.bestCatch,
                    bestWhack = if (game == MiniGame.WHACK) max(c.bestWhack, score) else c.bestWhack,
                    bestRunner = if (game == MiniGame.RUNNER) max(c.bestRunner, score) else c.bestRunner,
                    bestBubbles = if (game == MiniGame.BUBBLES) max(c.bestBubbles, score) else c.bestBubbles,
                    bestSimon = if (game == MiniGame.SIMON) max(c.bestSimon, score) else c.bestSimon,
                    bestMemoryMoves = if (game == MiniGame.MEMORY && score > 0) {
                        if (c.bestMemoryMoves == 0) score else min(c.bestMemoryMoves, score)
                    } else c.bestMemoryMoves,
                ),
            )
            emit(GameEvent.Reaction(ReactionKind.GAME))
            questProgress(QuestType.GAMES, 1)
            when (game.group) {
                GameGroup.KNOBELN -> questProgress(QuestType.PUZZLES, 1)
                GameGroup.ACTION -> questProgress(QuestType.ACTION, 1)
                GameGroup.RUHIG -> Unit
            }
            when (game) {
                MiniGame.CATCH -> questProgress(QuestType.CATCH_SCORE, score)
                MiniGame.MEMORY -> questProgress(QuestType.MEMORY, 1)
                MiniGame.WHACK -> questProgress(QuestType.WHACK_SCORE, score)
                MiniGame.RUNNER -> questProgress(QuestType.RUNNER_SCORE, score)
                MiniGame.BUBBLES -> questProgress(QuestType.BUBBLES_SCORE, score)
                MiniGame.SIMON -> questProgress(QuestType.SIMON_LEVEL, score)
                else -> Unit
            }
            val stars = stars(game, score)
            if (stars == 3) questProgress(QuestType.STARS3, 1)
            addCoins(gameCoins(pet, game, reward.coins, now))
            addXp(reward.xp, now)
            addPassXp(5)
            addTokens(1)
            touch(now)
            addTalent(Talents.of(game), 4 + 3 * stars, now)
            feel(if (stars == 3) Emotion.STOLZ else Emotion.VERSPIELT, now)
            routine(Routine.PLAYTIME, now)
            dayLog("game")
            val old = s.counters.bests[game.name]
            val best = when {
                old == null || old == 0 -> score
                game.lowerIsBetter -> if (score > 0) min(old, score) else old
                else -> max(old, score)
            }
            counters { it.copy(bests = it.bests + (game.name to best)) }
            wishProgress(WishKind.GAME, now, game.name)
            if (game == MiniGame.COOK) {
                val dishes = Recipes.gameDishes(score)
                for (d in dishes) addItem(d)
                if (dishes.isNotEmpty()) {
                    counters { it.copy(cooked = it.cooked + dishes.size) }
                    questProgress(QuestType.COOK, dishes.size)
                    message("Frisch gekocht: " + dishes.joinToString(" ") { Catalog[it]?.emoji ?: "" })
                }
            }
        }

        /** Coins of a finished mini game with feeling, talent and equipment bonus. */
        private fun gameCoins(pet: Pet, game: MiniGame, base: Int, now: Long): Int {
            var f = Emotions.coinFactor(pet.emotion(now)) * Talents.gameFactor(pet, game)
            if (Perk.COINS in Perks.active(s)) f *= 1.15
            return max(1, (base * f).roundToInt())
        }

        // ---------------------------------------------------------------- shop

        private fun buyMystery(now: Long) {
            val pool = Shop.mysteryPool(s, playerLevel(s))
            if (pool.isEmpty()) {
                message("In der Wunderkiste ist gerade nichts Neues für dich.")
                return
            }
            if (s.coins < Shop.MYSTERY_PRICE) {
                message("Dafür fehlen dir noch ${Shop.MYSTERY_PRICE - s.coins} Münzen.")
                return
            }
            val item = pool[Random(now xor s.counters.purchases.toLong()).nextInt(pool.size)]
            s = s.copy(
                coins = s.coins - Shop.MYSTERY_PRICE,
                counters = s.counters.copy(purchases = s.counters.purchases + 1),
                owned = s.owned + item.id,
            )
            equip(item.id, counted = false)
            message("Überraschung! In der Wunderkiste war: ${item.label(s.catMode)} ${item.icon(s.catMode)}")
            emit(GameEvent.Reaction(ReactionKind.BUY, item.id))
        }

        private fun buy(itemId: String, now: Long) {
            val item = Catalog[itemId] ?: return
            val price = Shop.price(item, TimeUtil.epochDay(now, zone))
            val level = playerLevel(s)
            when {
                item.price <= 0 || !item.sold -> return
                item.cosmetic && itemId in s.owned -> {
                    message("Das hast du schon!")
                    return
                }
                level < item.minLevel -> {
                    message("Wird ab Level ${item.minLevel} freigeschaltet.")
                    return
                }
                s.coins < price -> {
                    message("Dafür fehlen dir noch ${price - s.coins} Münzen.")
                    return
                }
            }
            s = s.copy(
                coins = s.coins - price,
                counters = s.counters.copy(purchases = s.counters.purchases + 1),
            )
            when {
                item.kind == ItemKind.EGG -> addEgg(item.line ?: EggLine.KNUFFEL, "Gekauft im Shop")
                item.stackable -> addItem(item.id)
                else -> {
                    s = s.copy(owned = s.owned + item.id)
                    equip(item.id, counted = false)
                }
            }
            emit(GameEvent.Reaction(ReactionKind.BUY, item.id))
            questProgress(QuestType.SHOP, 1)
            if (item.cosmetic || item.kind == ItemKind.EGG) feel(Emotion.NEUGIERIG, s.lastSimulated)
        }

        private fun equip(itemId: String, counted: Boolean) {
            val item = Catalog[itemId] ?: return
            val slot = item.kind.slot ?: return
            if (itemId !in s.owned) return
            if (s.equipped[slot] == itemId) return
            s = s.copy(equipped = s.equipped + (slot to itemId))
            if (item.kind.wearable) {
                counters { it.copy(outfits = it.outfits + 1) }
                questProgress(QuestType.OUTFIT, 1)
                wishProgress(WishKind.OUTFIT, s.lastSimulated)
            }
        }

        private fun unequip(slot: Slot) {
            s = when {
                slot == Slot.ROOM -> s.copy(equipped = s.equipped + (Slot.ROOM to Catalog.DEFAULT_ROOM))
                slot == Slot.HAT || slot == Slot.FACE || slot == Slot.NECK || slot == Slot.BACK || slot == Slot.TOY || slot == Slot.DECO -> s.copy(equipped = s.equipped - slot)
                else -> s.copy(equipped = s.equipped + (slot to Catalog.defaultEquipped(s.settings.look).getValue(slot)))
            }
        }

        // ---------------------------------------------------------------- rewards

        private fun claimDaily(now: Long, zone: ZoneId) {
            if (!canClaimDaily(s, now, zone)) return
            val idx = DailyRewards.dayIndex(s.daily.streak)
            s = s.copy(daily = s.daily.copy(rewardClaimedDay = TimeUtil.epochDay(now, zone)))
            addCoins(DailyRewards.coins[idx])
            addAdventure(3)
            if (idx == 6) {
                addItem(DailyRewards.BONUS_ITEM)
                addItem(Catalog.STICKER_PACK)
                message("Bonus: ein Törtchen und eine Stickertüte! 🍰🎴")
            }
        }

        private fun claimQuest(index: Int, now: Long) {
            val q = s.daily.quests.getOrNull(index) ?: return
            if (!q.done || q.claimed) return
            val quests = s.daily.quests.toMutableList().also { it[index] = q.copy(claimed = true) }
            s = s.copy(
                daily = s.daily.copy(quests = quests),
                counters = s.counters.copy(quests = s.counters.quests + 1),
            )
            addCoins(q.type.coins)
            addXp(q.type.xp, now)
            addPassXp(20)
            addTokens(3)
            addAdventure(4)
        }

        private fun claimQuestBonus(now: Long) {
            val d = s.daily
            if (d.bonusClaimed || d.quests.isEmpty() || !d.quests.all { it.claimed }) return
            s = s.copy(daily = d.copy(bonusClaimed = true))
            addCoins(Quests.BONUS_COINS)
            addXp(Quests.BONUS_XP, now)
            addPassXp(40)
            addAdventure(6)
        }

        private fun claimWeekly(index: Int, now: Long) {
            val q = s.weekly.quests.getOrNull(index) ?: return
            if (!q.done || q.claimed) return
            val quests = s.weekly.quests.toMutableList().also { it[index] = q.copy(claimed = true) }
            s = s.copy(weekly = s.weekly.copy(quests = quests), counters = s.counters.copy(weeklies = s.counters.weeklies + 1))
            addCoins(Quests.weeklyCoins(q.type))
            addXp(Quests.weeklyXp(q.type), now)
            addPassXp(60)
            addTokens(5)
            addAdventure(10)
        }

        private fun claimWeeklyBonus(now: Long) {
            val w = s.weekly
            if (w.bonusClaimed || w.quests.isEmpty() || !w.quests.all { it.claimed }) return
            s = s.copy(weekly = w.copy(bonusClaimed = true))
            addCoins(Quests.WEEKLY_BONUS_COINS)
            addItem(Catalog.STICKER_PACK)
            addItem(Catalog.SEED_PACK)
            addPassXp(100)
            addXp(50, now)
            addAdventure(15)
        }

        private fun claimStepTier(index: Int) {
            val tier = StepRewards.tiers.getOrNull(index) ?: return
            if (index in s.steps.claimedTiers || s.steps.today < tier.first) return
            s = s.copy(steps = s.steps.copy(claimedTiers = s.steps.claimedTiers + index))
            addCoins(tier.second)
        }

        private fun claimPass(tier: Int, now: Long) {
            if (tier < 1 || tier > SeasonPass.tier(s.pass.xp) || tier in s.pass.claimed) return
            val date = EventCalendar.date(now, zone)
            val reward = SeasonPass.reward(EventCalendar.season(date), tier)
            s = s.copy(pass = s.pass.copy(claimed = s.pass.claimed + tier))
            addCoins(reward.coins)
            if (reward.egg) addEgg(randomEggLine(Random(now xor tier.toLong())), "Belohnung aus dem Jahreszeiten-Pass")
            val id = reward.itemId ?: return
            val item = Catalog[id] ?: return
            if (item.stackable) {
                addItem(id, reward.count)
            } else if (id !in s.owned) {
                s = s.copy(owned = s.owned + id)
                message("Neu: ${item.name} ${item.emoji}")
            }
        }

        private fun buyEventOffer(offerId: String, now: Long) {
            val active = activeEvent(now, zone) ?: return
            if (s.event.key.isEmpty()) return
            val offer = EventShop.offers(active).firstOrNull { it.id == offerId } ?: return
            val bought = s.event.bought[offerId] ?: 0
            if (bought >= offer.limit) {
                message("Davon hast du schon genug.")
                return
            }
            if (s.event.tokens < offer.price) {
                message("Dafür brauchst du noch ${offer.price - s.event.tokens} ${active.tokenName}.")
                return
            }
            s = s.copy(event = s.event.copy(tokens = s.event.tokens - offer.price, bought = s.event.bought + (offerId to bought + 1)))
            when {
                offer.egg != null -> addEgg(offer.egg, "Vom ${active.title}")
                offer.sticker != null -> addStickers(listOf(offer.sticker))
                else -> {
                    val item = Catalog[offer.id] ?: return
                    if (item.stackable) addItem(item.id) else {
                        s = s.copy(owned = s.owned + item.id)
                        equip(item.id, counted = true)
                    }
                    emit(GameEvent.Reaction(ReactionKind.BUY, item.id))
                }
            }
        }

        private fun spinWheel(now: Long) {
            val e = eventOf(s) ?: return
            val today = TimeUtil.epochDay(now, zone)
            if (!EventPrizes.canSpin(s, today)) {
                message("Das Glücksrad dreht sich morgen wieder! 🎡")
                return
            }
            val result = EventPrizes.spin(s, e, Random(now xor 0x5EEDL))
            s = s.copy(event = s.event.copy(spinDay = today, spins = s.event.spins + 1, spinWins = s.event.spinWins + if (result.prize != null) 1 else 0))
            emit(GameEvent.Spun(result))
            when {
                result.prize != null -> grantPrize(result.prize, Skins[result.prize] != null)
                result.coins > 0 -> addCoins(result.coins)
                result.tokens > 0 -> addTokens(result.tokens)
                result.sticker != null -> addStickers(listOf(result.sticker))
            }
            touch(now)
        }

        // ---------------------------------------------------------------- skins

        private fun applySkin(skinId: String?, now: Long) {
            val pet = s.pet ?: return
            if (!pet.alive || pet.isEgg) return
            if (skinId != null && (skinId !in s.skins || Skins[skinId]?.fur == true)) return
            if (pet.skin == skinId) return
            s = s.copy(pet = pet.copy(skin = skinId))
            if (skinId != null) {
                feel(Emotion.NEUGIERIG, now)
                questProgress(QuestType.OUTFIT, 1)
                wishProgress(WishKind.OUTFIT, now)
            }
        }

        // ---------------------------------------------------------------- jobs

        private fun chooseJob(job: Job, now: Long) {
            val pet = s.pet ?: return
            if (!pet.alive || pet.isEgg) return
            if (pet.level < Jobs.UNLOCK_LEVEL) {
                message("Einen Beruf gibt es ab Level ${Jobs.UNLOCK_LEVEL}.")
                return
            }
            if (pet.job == job) return
            val price = if (pet.job == null) 0 else Jobs.CHANGE_PRICE
            if (s.coins < price) {
                message("Ein Berufswechsel kostet $price Münzen.")
                return
            }
            s = s.copy(coins = s.coins - price, pet = pet.copy(job = job, jobXp = 0))
            message("${pet.name} ist jetzt ${job.title(pet.isCat)} ${job.emoji}!")
            feel(Emotion.AUFGEREGT, now)
        }

        private fun work(now: Long) {
            val blocker = workBlocker(s, now)
            if (blocker != null) {
                refuse(blocker)
                return
            }
            val pet = s.pet!!
            val job = pet.job!!
            val before = pet.jobLevel
            var wage = Jobs.wage(before, pet.talentLevel(job.talent)).toDouble()
            if (Perk.WORK in Perks.active(s)) wage *= 1.2
            if (Talents.natural(pet) == job.talent) wage *= 1.1
            val worked = pet.applyEffect(Effect(energy = tiring(-15), satiety = -8, hygiene = -4, joy = 5))
                .copy(jobXp = pet.jobXp + 1, lastShift = now)
            s = s.copy(pet = worked, counters = s.counters.copy(shifts = s.counters.shifts + 1))
            emit(GameEvent.Reaction(ReactionKind.WORK, job.name))
            questProgress(QuestType.WORK, 1)
            dayLog("work")
            addCoins(wage.roundToInt())
            addXp(12, now)
            addPassXp(10)
            addTokens(1)
            addTalent(job.talent, 8, now)
            touch(now)
            val after = Jobs.level(worked.jobXp)
            if (after > before) {
                emit(GameEvent.JobUp(job, after))
                message("Beförderung! ${pet.name} ist jetzt ${Jobs.rank(after)} (Stufe $after). 🎉")
                feel(Emotion.STOLZ, now)
            }
        }

        // ---------------------------------------------------------------- chat

        private fun canChat(): Pet? {
            val pet = s.pet ?: return null
            if (!pet.alive || pet.isEgg) return null
            if (pet.level < Chat.UNLOCK_LEVEL) {
                message("Ab Level ${Chat.UNLOCK_LEVEL} kann ${pet.name} mit dir chatten.")
                return null
            }
            return pet
        }

        private fun addChat(fromPet: Boolean, text: String, now: Long) {
            val msgs = (s.chat.messages + ChatMessage(fromPet, text, now)).takeLast(Chat.MAX_MESSAGES)
            s = s.copy(chat = s.chat.copy(messages = msgs))
        }

        private fun openChat(now: Long) {
            val pet = canChat() ?: return
            val last = s.chat.messages.lastOrNull()
            if (pet.sleeping) return
            if (last == null || now - last.at > 2 * 3_600_000L) addChat(true, Chat.greeting(s, pet, now, zone), now)
        }

        private fun chat(raw: String, now: Long) {
            val pet = canChat() ?: return
            val text = raw.trim().take(200)
            if (text.isEmpty()) return
            addChat(false, text, now)
            val reply = Chat.reply(s, pet, text, now, zone)
            addChat(true, reply.text, now)
            if (reply.remember.isNotEmpty()) s = s.copy(chat = s.chat.copy(memory = s.chat.memory + reply.remember))
            counters { it.copy(chats = it.chats + 1) }
            questProgress(QuestType.CHAT, 1)
            dayLog("chat")
            emit(GameEvent.Reaction(ReactionKind.TALK))
            val norm = Chat.normalize(text).split(' ')
            if (norm.any { it in Secrets.magicWords }) findSecret(Secret.MAGIC_WORD, now)
            if (norm.any { it == "geheimnis" || it == "geheimnisse" || it == "geheim" }) findSecret(Secret.SECRET_TALK, now)
            if (Secrets.catWish(text)) findSecret(Secret.CATS, now)
            if (!pet.sleeping) {
                touch(now)
                if (cooldownReady("chat_joy", now, 20_000L)) updatePet { it.applyEffect(Effect(joy = 2)) }
                val today = TimeUtil.epochDay(now, zone)
                if (s.chat.bonusDay != today) {
                    s = s.copy(chat = s.chat.copy(bonusDay = today))
                    addXp(8, now)
                }
            }
            when (reply.effect) {
                ChatEffect.PLAY -> play(now)
                ChatEffect.SLEEP -> if (s.pet?.sleeping == false) toggleSleep()
                ChatEffect.WAKE -> if (s.pet?.sleeping == true) toggleSleep()
                ChatEffect.STROKE -> stroke(now)
                ChatEffect.HAPPY -> {
                    updatePet { it.applyEffect(Effect(joy = 4)) }
                    feel(Emotion.VERSCHMUST, now)
                }
                ChatEffect.LAUGH -> {
                    updatePet { it.applyEffect(Effect(joy = 3)) }
                    feel(Emotion.VERSPIELT, now)
                }
                ChatEffect.SAD -> {
                    updatePet { it.applyEffect(Effect(joy = -4)) }
                    feel(Emotion.GRUMMELIG, now)
                }
                ChatEffect.CURIOUS -> if (s.pet?.feeling == null || now >= (s.pet?.feelingUntil ?: 0L)) feel(Emotion.NEUGIERIG, now)
                ChatEffect.NONE -> Unit
            }
        }

        // ---------------------------------------------------------------- screen toys

        private fun toy(kind: ToyKind, now: Long) {
            val pet = s.pet ?: return
            if (!pet.alive || pet.isEgg) return
            if (!cooldownReady("toy_${kind.name}", now, 12_000L)) return
            val craft = pet.talentLevel(Talent.BASTELN)
            when (kind) {
                ToyKind.PLANE -> {
                    counters { it.copy(planes = it.planes + 1) }
                    updatePet { it.applyEffect(Effect(joy = 3)) }
                    addTalent(Talent.BASTELN, 2, now)
                    addCoins(2 + craft / 3)
                    feel(Emotion.VERSPIELT, now)
                }
                ToyKind.PAINT -> {
                    counters { it.copy(wipes = it.wipes + 1) }
                    updatePet { it.applyEffect(Effect(joy = 1)) }
                    addTalent(Talent.BASTELN, 1, now)
                    addCoins(1 + craft / 4)
                }
                ToyKind.LADDER -> {
                    counters { it.copy(ladders = it.ladders + 1) }
                    updatePet { it.applyEffect(Effect(joy = 2, energy = tiring(-2))) }
                    addTalent(Talent.SPORT, 2, now)
                    feel(Emotion.STOLZ, now)
                }
                ToyKind.FOOTPRINTS -> Unit
            }
        }

        // ---------------------------------------------------------------- routine

        private fun routine(r: Routine, now: Long) {
            val p = s.pet ?: return
            if (!p.alive || p.isEgg) return
            if (r in s.daily.routine || !r.now(TimeUtil.hour(now, zone))) return
            val done = s.daily.routine + r
            s = s.copy(daily = s.daily.copy(routine = done))
            message("${r.emoji} ${r.title} geschafft! (${done.size}/${Routine.entries.size})")
            if (done.size == Routine.entries.size && !s.daily.routineClaimed) {
                s = s.copy(daily = s.daily.copy(routineClaimed = true))
                counters { it.copy(routines = it.routines + 1) }
                questProgress(QuestType.ROUTINE, 1)
                message("Ganzer Tagesablauf geschafft! +${Routines.BONUS_COINS} 🪙")
                addCoins(Routines.BONUS_COINS)
                addXp(Routines.BONUS_XP, now)
                feel(Emotion.STOLZ, now)
            }
        }

        // ---------------------------------------------------------------- kitchen

        private fun cook(recipeId: String, now: Long) {
            val r = Recipes[recipeId] ?: return
            val pet = s.pet
            if (pet == null || pet.isEgg || !pet.alive) return
            if (playerLevel(s) < r.minLevel) {
                message("Dieses Rezept gibt es ab Level ${r.minLevel}.")
                return
            }
            if (!Recipes.canCook(s, r)) {
                message("Dafür fehlen noch Zutaten.")
                return
            }
            var inv = s.inventory
            for ((id, n) in r.ingredients) {
                val left = (inv[id] ?: 0) - n
                inv = if (left <= 0) inv - id else inv + (id to left)
            }
            s = s.copy(inventory = inv)
            addItem(r.dish)
            counters { it.copy(cooked = it.cooked + 1) }
            questProgress(QuestType.COOK, 1)
            dayLog("cook")
            addTalent(Talent.KOCHEN, 6, now)
            if (cooldownReady("xp_cook", now, 20_000L)) addXp(6, now)
            val dish = Catalog[r.dish]
            emit(GameEvent.Reaction(ReactionKind.WORK, Job.KOCH.name))
            message("${dish?.emoji ?: "🍲"} ${dish?.name ?: "Gericht"} ist fertig!")
            touch(now)
            wishProgress(WishKind.COOK, now)
        }

        // ---------------------------------------------------------------- finds

        private fun addFinds(ids: List<String>) {
            if (ids.isEmpty()) return
            var finds = s.finds
            for (id in ids) finds = finds + (id to (finds[id] ?: 0) + 1)
            s = s.copy(finds = finds, counters = s.counters.copy(finds = s.counters.finds + ids.size))
            message("Fundstück: " + ids.mapNotNull { Finds[it]?.let { f -> "${f.emoji} ${f.name}" } }.joinToString(", "))
            for (set in FindSet.entries) {
                if (set.name !in s.findSetsDone && Finds.complete(set, s.finds)) {
                    s = s.copy(findSetsDone = s.findSetsDone + set.name)
                    message("Sammlung „${set.title}“ komplett! +${set.reward} 🪙")
                    addCoins(set.reward)
                }
            }
        }

        // ---------------------------------------------------------------- stories

        private fun storyChoose(choice: Int, now: Long) {
            val pet = s.pet ?: return
            if (pet.isEgg || !pet.alive) return
            val today = TimeUtil.epochDay(now, zone)
            val story = Stories.current(s) ?: return
            if (!Stories.canPlay(s, today)) {
                message("Das nächste Kapitel gibt es morgen. 📖")
                return
            }
            val idx = s.story.progress[story.id] ?: 0
            val ch = story.chapters.getOrNull(idx) ?: return
            val c = ch.choices.getOrNull(choice) ?: return
            val text = Stories.text(c.result, pet.name)
            s = s.copy(
                story = s.story.copy(progress = s.story.progress + (story.id to idx + 1), lastDay = today, lastResult = text),
                counters = s.counters.copy(storyChapters = s.counters.storyChapters + 1),
            )
            dayLog("story")
            addAdventure(3)
            addCoins(c.coins)
            c.item?.let { addItem(it) }
            c.find?.let { addFinds(listOf(it)) }
            if (c.joy > 0) updatePet { it.applyEffect(Effect(joy = c.joy)) }
            addXp(15, now)
            feel(Emotion.NEUGIERIG, now)
            touch(now)
            if (idx + 1 >= story.chapters.size) {
                s = s.copy(story = s.story.copy(finished = s.story.finished + story.id))
                addCoins(story.finalCoins)
                story.finalItem?.let { id ->
                    if (id !in s.owned) s = s.copy(owned = s.owned + id)
                    message("Geschichte beendet! Belohnung: ${Catalog[id]?.name ?: id} ${Catalog[id]?.emoji ?: ""}")
                }
                feel(Emotion.STOLZ, now)
            }
        }

        // ---------------------------------------------------------------- friends

        private fun invite(petId: Long, now: Long) {
            val guest = s.resting.firstOrNull { it.id == petId } ?: return
            val host = s.pet ?: return
            if (guest.isEgg || !guest.alive || host.isEgg || !host.alive) return
            if (s.onTrip(petId) != null) {
                message("${guest.name} ist gerade auf Ausflug.")
                return
            }
            if (s.visit?.petId == petId || s.visit2?.petId == petId) {
                message("${guest.name} ist doch schon da!")
                return
            }
            // Up to two friends can visit at the same time, each place has its own pause.
            val second = s.visit != null
            if (second && s.visit2 != null) {
                message("Es sind schon zwei Freunde zu Besuch.")
                return
            }
            val key = if (second) "visit2" else "visit"
            val last = s.cooldowns[key] ?: 0L
            if (now - last < Friends.VISIT_COOLDOWN_MS && now >= last) {
                message("Nächster Besuch in ${TimeUtil.formatDuration(Friends.VISIT_COOLDOWN_MS - (now - last))}.")
                return
            }
            val v = Visit(petId, now + Friends.VISIT_MS)
            s = if (second) s.copy(visit2 = v, cooldowns = s.cooldowns + (key to now)) else s.copy(visit = v, cooldowns = s.cooldowns + (key to now))
            counters { it.copy(visits = it.visits + 1) }
            dayLog("visit")
            addFriendship(host.id, petId, 3)
            message(if (second) "${guest.name} kommt auch noch dazu! Jetzt seid ihr zu dritt. 🎉" else "${guest.name} kommt ${host.name} besuchen! 🏠")
            questProgress(QuestType.VISIT, 1)
            wishProgress(WishKind.VISIT, now)
            feel(Emotion.AUFGEREGT, now)
            touch(now)
        }

        fun visitor(): Pet? = s.visit?.let { v -> s.resting.firstOrNull { it.id == v.petId } }

        fun visitor2(): Pet? = s.visit2?.let { v -> s.resting.firstOrNull { it.id == v.petId } }

        private fun endVisit(now: Long) {
            val host = s.pet
            fun ends(v: Visit?): Boolean = v != null && (now >= v.until || s.resting.none { it.id == v.petId } || host?.alive != true)
            fun goodbye(v: Visit) {
                val g = s.resting.firstOrNull { it.id == v.petId } ?: return
                if (host == null) return
                addFriendship(host.id, g.id, 5)
                message("${g.name} geht zurück ins Kuschelhaus. Es war schön! 👋")
            }
            val v2 = s.visit2
            if (v2 != null && ends(v2)) {
                s = s.copy(visit2 = null)
                goodbye(v2)
            }
            val v1 = s.visit
            if (v1 != null && ends(v1)) {
                // The second guest moves up to the first place.
                s = s.copy(visit = s.visit2, visit2 = null)
                goodbye(v1)
            }
        }

        private fun sendHome(petId: Long) {
            val v = listOfNotNull(s.visit, s.visit2).firstOrNull { it.petId == petId } ?: return
            s = if (s.visit?.petId == petId) s.copy(visit = s.visit2, visit2 = null) else s.copy(visit2 = null)
            val g = s.resting.firstOrNull { it.id == v.petId } ?: return
            val host = s.pet ?: return
            addFriendship(host.id, g.id, 3)
            message("${g.name} winkt und geht zurück ins Kuschelhaus. 👋")
        }

        private fun addFriendship(a: Long, b: Long, n: Int) {
            val key = Friends.key(a, b)
            val before = s.friendship[key] ?: 0
            val after = min(Friends.MAX, before + n)
            s = s.copy(friendship = s.friendship + (key to after))
            if (Friends.level(after) >= 4) findSecret(Secret.FRIENDS, s.lastSimulated)
            if (Friends.level(after) > Friends.level(before)) {
                val other = s.allPets.firstOrNull { it.id == (if (s.pet?.id == a) b else a) }
                message("${other?.name ?: "Ihr"}: ${Friends.titles[Friends.level(after)]}! 💞")
            }
        }

        private fun befriendVisitor(n: Int, now: Long) {
            val guests = s.visitors
            val host = s.pet ?: return
            if (guests.isEmpty() || !cooldownReady("befriend", now, 60_000L)) return
            for (g in guests) addFriendship(host.id, g.id, n)
        }

        private fun playTogether(now: Long) {
            val guests = s.visitors
            if (guests.isEmpty()) return
            val host = activePet() ?: return
            if (!cooldownReady("together", now, 10 * 60_000L)) {
                message(if (guests.size > 1) "Die drei ruhen sich kurz aus." else "Die beiden ruhen sich kurz aus.")
                return
            }
            val ids = guests.map { it.id }.toSet()
            s = s.copy(
                pet = host.applyEffect(Effect(joy = 15, energy = tiring(-6))),
                resting = s.resting.map { if (it.id in ids) it.applyEffect(Effect(joy = 15)) else it },
            )
            for (g in guests) addFriendship(host.id, g.id, 6)
            if (guests.size > 1) addFriendship(guests[0].id, guests[1].id, 6)
            emit(GameEvent.Reaction(ReactionKind.PLAY))
            questProgress(QuestType.PLAY, 1)
            routine(Routine.PLAYTIME, now)
            addXp(8, now)
            feel(Emotion.VERSPIELT, now)
            touch(now)
        }

        // ---------------------------------------------------------------- tricks

        private fun practiceTrick(t: Trick, now: Long) {
            Tricks.blocker(s, t)?.let {
                refuse(it)
                return
            }
            val pet = s.pet ?: return
            if (Tricks.learned(pet, t)) {
                showTrick(t, now)
                return
            }
            if (!cooldownReady("trick_practice", now, Tricks.PRACTICE_COOLDOWN_MS)) return
            val success = Random(now xor pet.id xor (t.ordinal * 7919L)).nextFloat() < Tricks.successChance(pet, now)
            val points = Tricks.points(pet, t) + if (success) 1 else 0
            val learned = success && points >= t.practice
            val practised = pet.applyEffect(Effect(energy = tiring(-2), joy = if (success) 3 else 1))
            s = s.copy(pet = practised.copy(tricks = pet.tricks + (t to points), care = practised.care.copy(activity = practised.care.activity + 1)))
            counters { it.copy(trickPractice = it.trickPractice + 1) }
            emit(GameEvent.TrickPracticed(t, success, learned))
            questProgress(QuestType.TRAIN, 1)
            touch(now)
            addTalent(Talent.SPORT, 1, now)
            if (cooldownReady("xp_trick", now, 20_000L)) addXp(3, now)
            if (learned) {
                counters { it.copy(tricksLearned = it.tricksLearned + 1) }
                message("${pet.name} kann jetzt „${t.title}“! ${t.emoji}")
                addCoins(Tricks.learnCoins(t))
                addXp(20, now)
                addAdventure(5)
                feel(Emotion.STOLZ, now)
            }
        }

        private fun showTrick(t: Trick, now: Long) {
            val pet = activePet() ?: return
            if (!Tricks.learned(pet, t)) {
                message("${pet.name} muss „${t.title}“ erst noch üben.")
                return
            }
            if (pet.sick) {
                refuse("${pet.name} ist zu krank für Kunststücke.")
                return
            }
            if (!cooldownReady("trick_show", now, 2_500L)) return
            s = s.copy(pet = pet.applyEffect(Effect(joy = 4, energy = tiring(-1))))
            counters { it.copy(trickShows = it.trickShows + 1) }
            emit(GameEvent.TrickShown(t))
            questProgress(QuestType.TRICK_SHOW, 1)
            wishProgress(WishKind.TRICK, now)
            touch(now)
            if (cooldownReady("trick_reward", now, Tricks.SHOW_REWARD_COOLDOWN_MS)) {
                addCoins(2 + t.practice / 3)
                addXp(4, now)
            }
            feel(Emotion.STOLZ, now)
            befriendVisitor(1, now)
        }

        // ---------------------------------------------------------------- touching

        private fun touchZone(zone: TouchZone, now: Long) {
            val pet = s.pet ?: return
            if (!pet.alive || pet.isEgg) return
            if (!cooldownReady("touch", now, 800L)) return
            if (pet.sleeping) {
                s = s.copy(pet = pet.applyEffect(Effect(joy = 1)))
                return
            }
            val cuddly = pet.emotion(now) == Emotion.VERSCHMUST
            val base = when (zone) {
                TouchZone.BELLY -> 4
                TouchZone.HEAD, TouchZone.CHEEKS -> 3
                else -> 2
            }
            s = s.copy(pet = pet.applyEffect(Effect(joy = if (cuddly) base * 2 else base)))
            counters { it.copy(touches = it.touches + 1, pets = it.pets + 1, tickles = it.tickles + if (zone == TouchZone.BELLY) 1 else 0) }
            emit(GameEvent.Touched(zone))
            questProgress(QuestType.PET, 1)
            if (zone == TouchZone.BELLY) questProgress(QuestType.TICKLE, 1)
            wishProgress(WishKind.CUDDLE, now)
            if (cooldownReady("xp_touch", now, 20_000L)) addXp(1, now)
            touch(now)
            befriendVisitor(1, now)
            dayLog("pet")
            if (s.counters.touches % 8 == 0) feel(if (zone == TouchZone.BELLY) Emotion.VERSPIELT else Emotion.VERSCHMUST, now)
        }

        private fun carry(now: Long) {
            val pet = s.pet ?: return
            if (!pet.alive || pet.isEgg || pet.sleeping) return
            if (!cooldownReady("carry", now, 8_000L)) return
            s = s.copy(pet = pet.applyEffect(Effect(joy = 3)))
            counters { it.copy(carries = it.carries + 1) }
            questProgress(QuestType.CARRY, 1)
            touch(now)
        }

        // ---------------------------------------------------------------- room games

        private fun roomGameFinished(g: RoomGame, score: Int, now: Long) {
            val pet = s.pet ?: return
            // a game stopped right away (nothing found, caught or fetched) brings nothing
            if (!pet.alive || pet.isEgg || score <= 0) return
            val sc = score.coerceIn(0, 50)
            val played = pet.applyEffect(Effect(joy = 16 + sc, energy = tiring(-8), satiety = -4, hygiene = -2))
            s = s.copy(pet = played.copy(care = played.care.copy(activity = played.care.activity + 2)))
            counters { it.copy(roomGames = it.roomGames + 1, plays = it.plays + 1) }
            questProgress(QuestType.PLAY, 1)
            questProgress(QuestType.ROOM_GAME, 1)
            addCoins(RoomGames.coins(sc))
            addXp(RoomGames.xp(sc), now)
            addPassXp(4)
            addAdventure(1)
            touch(now)
            routine(Routine.PLAYTIME, now)
            addTalent(Talent.SPORT, 3, now)
            feel(Emotion.VERSPIELT, now)
            befriendVisitor(2, now)
            dayLog("game")
            wishProgress(WishKind.ROOM_GAME, now, g.name)
            if (g == RoomGame.FETCH) wishProgress(WishKind.PLAY, now)
        }

        // ---------------------------------------------------------------- voice

        private fun voice(kind: VoiceKind, now: Long) {
            val pet = s.pet ?: return
            if (!pet.alive || pet.isEgg) return
            if (!cooldownReady("voice", now, 6_000L)) return
            counters { it.copy(voice = it.voice + 1) }
            if (pet.sleeping) return
            s = s.copy(pet = pet.applyEffect(Effect(joy = if (kind == VoiceKind.LOUD) 0 else 2)))
            touch(now)
            if (kind == VoiceKind.SING) {
                feel(Emotion.FROEHLICH, now)
                addTalent(Talent.MUSIK, 1, now)
                findSecret(Secret.SINGER, now)
            }
        }

        // ---------------------------------------------------------------- wishes

        /** Now and then the pet wishes for something while you are there. Old wishes fade away. */
        private fun wishTick(now: Long, userPresent: Boolean) {
            val p = s.pet ?: return
            val w = s.wish
            if (w != null) {
                if (now > w.until || !p.alive || p.isEgg) {
                    s = s.copy(wish = null, nextWishAt = now + Wishes.GAP_MS)
                    if (p.alive && !p.isEgg) message("${p.name}s Wunsch ist verflogen. Vielleicht ein andermal! 💭")
                }
                return
            }
            if (!userPresent || !p.alive || p.isEgg || p.sleeping) return
            if (s.nextWishAt <= 0L) {
                s = s.copy(nextWishAt = now + Wishes.FIRST_DELAY_MS)
                return
            }
            if (now < s.nextWishAt) return
            val wish = Wishes.roll(s, now, Random(now xor p.id))
            if (wish == null) {
                s = s.copy(nextWishAt = now + 30 * 60_000L)
                return
            }
            s = s.copy(wish = wish, nextWishAt = now + Wishes.GAP_MS)
            emit(GameEvent.WishMade(wish))
        }

        private fun wishProgress(kind: WishKind, now: Long, target: String? = null) {
            val w = s.wish ?: return
            if (w.kind != kind) return
            if (w.target.isNotEmpty() && w.target != target) return
            val done = w.progress + 1
            if (done < w.need) {
                s = s.copy(wish = w.copy(progress = done))
                return
            }
            val pet = s.pet ?: return
            s = s.copy(wish = null, nextWishAt = now + Wishes.GAP_MS, pet = pet.applyEffect(Effect(joy = 15)))
            counters { it.copy(wishes = it.wishes + 1) }
            emit(GameEvent.WishFulfilled(w, Wishes.COINS))
            addCoins(Wishes.COINS)
            addXp(Wishes.XP, now)
            addPassXp(10)
            addAdventure(6)
            questProgress(QuestType.WISH, 1)
            feel(Emotion.AUFGEREGT, now)
        }

        // ---------------------------------------------------------------- monthly & long-term goals

        private fun claimMonthly(index: Int, now: Long) {
            val q = s.monthly.goals.getOrNull(index) ?: return
            if (!q.done || q.claimed) return
            val goals = s.monthly.goals.toMutableList().also { it[index] = q.copy(claimed = true) }
            s = s.copy(monthly = s.monthly.copy(goals = goals))
            counters { it.copy(monthGoals = it.monthGoals + 1) }
            addCoins(Quests.monthlyCoins(q.type))
            addXp(Quests.monthlyXp(q.type), now)
            addPassXp(120)
            addTokens(8)
            addAdventure(20)
        }

        private fun claimMonthlyBonus(now: Long) {
            val m = s.monthly
            if (m.bonusClaimed || m.goals.isEmpty() || !m.goals.all { it.claimed }) return
            s = s.copy(monthly = m.copy(bonusClaimed = true))
            counters { it.copy(monthlies = it.monthlies + 1) }
            addCoins(Quests.MONTHLY_BONUS_COINS)
            addItem(Catalog.GLITTER)
            addEgg(rareEggLine(Random(now)), "Belohnung für alle Monatsziele")
            addAdventure(40)
            addXp(80, now)
            feel(Emotion.STOLZ, now)
        }

        /** One of the special egg lines, preferring one that has not been discovered yet. */
        private fun rareEggLine(rnd: Random): EggLine {
            val lines = listOf(EggLine.DSCHUNGEL, EggLine.WUESTE, EggLine.EDELSTEIN, EggLine.STERN, EggLine.ZUCKER)
            val fresh = lines.filter { l -> l.forms.none { it.name in s.album } }
            val pool = fresh.ifEmpty { lines }
            return pool[rnd.nextInt(pool.size)]
        }

        private fun claimGoal(g: LongGoal) {
            if (g.claimed(s) || !g.done(s)) return
            s = s.copy(milestones = s.milestones + "goal:${g.name}")
            addCoins(g.coins)
            g.egg?.let { addEgg(it, "Belohnung: ${g.title}") }
            g.itemId?.let { id ->
                val item = Catalog[id]
                if (item != null && item.stackable) {
                    addItem(id, g.itemCount)
                } else if (item != null && id !in s.owned) {
                    s = s.copy(owned = s.owned + id)
                    message("Neu: ${item.name} ${item.emoji}")
                }
            }
            emit(GameEvent.GoalDone(g))
        }

        // ---------------------------------------------------------------- house

        fun normalizeHouse(now: Long) {
            val before = s.house.current
            s = House.normalize(s, now)
            if (House.enabled(s) && s.house.current != before) emit(GameEvent.RoomEntered(s.house.current))
        }

        private fun goToRoom(k: RoomKind) {
            if (!House.enabled(s) || k == s.house.current || k !in House.unlocked(s)) return
            val p = s.pet
            if (p != null && p.alive && p.sleeping && k != RoomKind.BEDROOM) {
                message("${p.name} schläft gerade im Schlafzimmer. Psst!")
                return
            }
            s = House.goTo(s, k)
            emit(GameEvent.RoomEntered(k))
        }

        private fun buildRoom(k: RoomKind, now: Long) {
            val blocker = House.buildBlocker(s, k)
            if (blocker != null) {
                message(blocker)
                return
            }
            s = House.build(s.copy(coins = s.coins - k.price), k)
            counters { it.copy(roomsBuilt = it.roomsBuilt + 1) }
            emit(GameEvent.RoomBuilt(k))
            addXp(20, now)
            feel(Emotion.AUFGEREGT, now)
        }

        private fun moveFurniture(slot: Slot, dx: Float) {
            if (slot !in House.MOVABLE) return
            val v = dx.coerceIn(-House.MAX_OFFSET, House.MAX_OFFSET)
            s = s.copy(furnitureOffsets = if (kotlin.math.abs(v) < 0.004f) s.furnitureOffsets - slot else s.furnitureOffsets + (slot to v))
        }

        // ---------------------------------------------------------------- family

        private fun familyTick(now: Long) {
            if (s.familyEggs.size >= Family.MAX_WAITING) return
            val last = s.cooldowns["family_check"] ?: 0L
            if ((now - last) in 0L until Family.CHECK_MS) return
            s = s.copy(cooldowns = s.cooldowns + ("family_check" to now))
            val lastEgg = s.cooldowns["family_egg"] ?: 0L
            if ((now - lastEgg) in 0L until Family.COOLDOWN_MS) return
            val couples = Family.couples(s)
            if (couples.isEmpty()) return
            val rnd = Random(now xor 0x5EEDL)
            if (rnd.nextFloat() >= Family.CHANCE) return
            val (a, b) = couples[rnd.nextInt(couples.size)]
            val egg = Family.make(a, b, now, rnd)
            s = s.copy(familyEggs = s.familyEggs + egg, cooldowns = s.cooldowns + ("family_egg" to now))
            counters { it.copy(familyEggs = it.familyEggs + 1) }
            emit(GameEvent.EggFound(egg.line, egg.title + " 💕"))
        }

        private fun hatchFamilyEgg(id: Long, name: String, now: Long) {
            val egg = s.familyEggs.firstOrNull { it.id == id } ?: return
            val active = s.pet
            val keepActive = active != null && active.alive
            if (keepActive && s.resting.size >= s.restSlots) {
                message("Das Kuschelhaus ist voll. Bau einen neuen Platz an!")
                return
            }
            val base = if (egg.line.real) {
                animalBaby(egg.line, cleanName(name, babyName(egg.line)), now, egg.fur)
            } else {
                Pet(name = cleanName(name, egg.line.title.substringBefore("-")), bornAt = now, line = egg.line)
            }
            s = s.copy(
                pet = base.copy(parents = egg.parents, blend = egg.blend),
                resting = if (keepActive) s.resting + active!! else s.resting,
                familyEggs = s.familyEggs.filter { it.id != id },
                lastSimulated = now,
            )
            if (keepActive) message("${active!!.name} kuschelt sich ins Kuschelhaus. 🏡")
            if (egg.line.real) welcomeKitten(now)
        }

        // ---------------------------------------------------------------- cards

        private fun addCards(forms: List<Form>) {
            var c = s.cards
            for (f in forms) c = c + (f.name to (c[f.name] ?: 0) + 1)
            s = s.copy(cards = c)
        }

        private fun dustDuplicates() {
            var dust = 0
            val cards = s.cards.mapValues { (name, n) ->
                val f = Form.entries.firstOrNull { it.name == name }
                if (n > 1 && f != null) {
                    dust += (n - 1) * Cards.rarity(f).dust
                    1
                } else {
                    n
                }
            }
            if (dust == 0) {
                message("Du hast keine doppelten Karten.")
                return
            }
            s = s.copy(cards = cards, cardDust = s.cardDust + dust)
            message("Doppelte Karten getauscht: +$dust Glitzerstaub ✨")
        }

        private fun craftCard(form: Form) {
            if (form == Form.EGG) return
            val cost = Cards.rarity(form).craft
            if (s.cardDust < cost) {
                message("Dafür brauchst du $cost Glitzerstaub.")
                return
            }
            s = s.copy(cardDust = s.cardDust - cost)
            addCards(listOf(form))
            counters { it.copy(cardsCrafted = it.cardsCrafted + 1) }
            message("${form.title} ist jetzt in deiner Sammlung! 🎴")
        }

        private fun cardDuel(won: Int, lost: Int, now: Long) {
            val w = won.coerceIn(0, Cards.DUEL_ROUNDS)
            val l = lost.coerceIn(0, Cards.DUEL_ROUNDS)
            val win = w > l
            counters { it.copy(duels = it.duels + 1, duelWins = it.duelWins + if (win) 1 else 0) }
            addCoins(Cards.duelCoins(w))
            s = s.copy(cardDust = s.cardDust + w * 3)
            addXp(6 + w * 2, now)
            addTalent(Talent.BASTELN, 2 + w, now)
            if (win && cooldownReady("duel_pack", now, 20 * 3_600_000L)) {
                addItem(Cards.PACK)
                message("Erster Sieg heute: eine Kartentüte! 🃏")
            }
            updatePet { if (it.alive && !it.isEgg) it.applyEffect(Effect(joy = if (win) 10 else 5)) else it }
            feel(if (win) Emotion.STOLZ else Emotion.VERSPIELT, now)
            dayLog("game")
            touch(now)
        }

        // ---------------------------------------------------------------- music

        private fun musicPlayed(notes: Int, now: Long) {
            val n = notes.coerceIn(0, Music.MAX_NOTES)
            if (n <= 0) return
            counters { it.copy(notesPlayed = it.notesPlayed + n) }
            if (n < Music.MIN_NOTES) return
            updatePet { if (it.alive && !it.isEgg && !it.sleeping) it.applyEffect(Effect(joy = (n / 4).coerceAtMost(15))) else it }
            addTalent(Talent.MUSIK, (n / 6).coerceIn(1, 8), now)
            if (cooldownReady("music", now, Music.REWARD_COOLDOWN_MS)) {
                addCoins(5 + (n / 10).coerceAtMost(10))
                addXp(5, now)
            }
            feel(Emotion.FROEHLICH, now)
            dayLog("music")
            touch(now)
        }

        private fun saveSong(song: Song, now: Long) {
            val notes = song.notes.filter { it.at in 0..Music.MAX_LENGTH_MS }.take(Music.MAX_NOTES)
            if (notes.size < 3) {
                message("Spiel erst ein paar Töne, dann kannst du das Lied speichern.")
                return
            }
            val name = song.name.trim().take(20).ifEmpty { "Lied ${s.counters.songs + 1}" }
            s = s.copy(songs = (s.songs + Song(name, notes, now)).takeLast(Music.MAX_SONGS))
            counters { it.copy(songs = it.songs + 1) }
            message("Dein Lied „$name“ ist gespeichert. 🎶")
        }

        // ---------------------------------------------------------------- adventure map

        private fun addAdventure(n: Int) {
            if (n <= 0 || !s.onboarded) return
            s = s.copy(adventure = s.adventure.copy(points = (s.adventure.points + n).coerceAtMost(999)))
        }

        private fun adventureStep(now: Long) {
            val a = s.adventure
            val cost = Adventure.cost(a.station, a.map)
            if (a.points < cost) {
                message("Dafür brauchst du noch ${cost - a.points} Abenteuerpunkte. 🧭")
                return
            }
            val st = Adventure.station(a.map, a.station)
            val reached = a.station + 1
            val done = reached >= Adventure.STATIONS
            s = s.copy(adventure = AdventureState(points = a.points - cost, station = if (done) 0 else reached, map = if (done) a.map + 1 else a.map))
            counters { it.copy(adventureSteps = it.adventureSteps + 1) }
            val r = st.reward
            addCoins(r.coins)
            r.itemId?.let { addItem(it, r.count) }
            r.egg?.let { addEgg(it, "Gefunden auf der Abenteuerkarte") }
            emit(GameEvent.AdventureStep(st.index, st.title, r.label, r.emoji, done))
            if (done) message("Du hast die ganze Abenteuerkarte geschafft! Eine neue Karte wartet schon. 🗺️")
            feel(Emotion.AUFGEREGT, now)
        }

        // ---------------------------------------------------------------- tournaments

        private fun tournamentFinished(cup: Cup, place: Int, rivals: List<Long>, now: Long) {
            val pet = s.pet ?: return
            if (!pet.alive || pet.isEgg || !Tournament.unlocked(s, cup)) return
            val pl = place.coerceIn(1, Tournament.RIVALS + 1)
            val coins = Tournament.coins(cup, pl)
            val firstWin = pl == 1 && Tournament.wins(s, cup) == 0
            val played = pet.applyEffect(Effect(joy = if (pl == 1) 20 else 12, energy = tiring(-12), satiety = -6, hygiene = -3))
            s = s.copy(pet = played.copy(care = played.care.copy(activity = played.care.activity + 3)))
            counters { it.copy(tournaments = it.tournaments + 1, tournamentWins = it.tournamentWins + if (pl == 1) 1 else 0) }
            if (pl == 1) s = s.copy(cups = s.cups + (cup.name to Tournament.wins(s, cup) + 1))
            emit(GameEvent.TournamentDone(cup, pl, coins, firstWin))
            questProgress(QuestType.TOURNAMENT, 1)
            addCoins(coins)
            addXp(15 + (5 - pl) * 8, now)
            addPassXp(10)
            addTokens(2)
            addAdventure(if (pl == 1) 6 else 3)
            addTalent(Talent.SPORT, 6 + (4 - pl) * 2, now)
            for (id in rivals.distinct()) if (s.resting.any { it.id == id }) addFriendship(pet.id, id, 2)
            touch(now)
            routine(Routine.PLAYTIME, now)
            dayLog("game")
            feel(if (pl == 1) Emotion.STOLZ else Emotion.VERSPIELT, now)
            if (firstWin) message("${cup.emoji} ${cup.title} gewonnen! Er steht jetzt in deiner Vitrine.")
        }

        // ---------------------------------------------------------------- garden

        private fun plant(index: Int, seedId: String, now: Long) {
            val plot = s.garden.plots.getOrNull(index) ?: return
            if (!plot.empty) return
            val seed = Catalog[seedId]?.takeIf { it.kind == ItemKind.SEED } ?: return
            if (!consume(seed)) return
            val season = EventCalendar.season(EventCalendar.date(now, zone))
            var grow = 1.0 - 0.03 * (s.pet?.talentLevel(Talent.GAERTNERN) ?: 0)
            if (Perk.GARDEN in Perks.active(s)) grow *= 0.8
            val planted = Plot(seed = seedId, plantedAt = now, readyAt = now + (Catalog.growMs(seed, season) * grow).toLong())
            s = s.copy(garden = s.garden.copy(plots = s.garden.plots.toMutableList().also { it[index] = planted }))
            counters { it.copy(plantings = it.plantings + 1) }
            questProgress(QuestType.PLANT, 1)
            addPassXp(2)
            addTalent(Talent.GAERTNERN, 2, now)
        }

        private fun water(index: Int, now: Long) {
            val plot = s.garden.plots.getOrNull(index) ?: return
            if (plot.empty || plot.ready(now)) return
            if (plot.waterings >= Garden.MAX_WATERINGS) {
                message("Genug gegossen! Jetzt heißt es warten. 🌱")
                return
            }
            if (now - plot.lastWatered < Garden.WATER_COOLDOWN_MS) {
                message("Die Erde ist noch feucht. Später wieder gießen!")
                return
            }
            val remaining = plot.readyAt - now
            val watered = plot.copy(readyAt = now + (remaining * 0.75).toLong(), waterings = plot.waterings + 1, lastWatered = now)
            s = s.copy(garden = s.garden.copy(plots = s.garden.plots.toMutableList().also { it[index] = watered }))
            counters { it.copy(waterings = it.waterings + 1) }
            questProgress(QuestType.WATER, 1)
            addTalent(Talent.GAERTNERN, 1, now)
        }

        private fun harvest(index: Int, now: Long) {
            val plot = s.garden.plots.getOrNull(index) ?: return
            if (!plot.ready(now)) return
            val seed = Catalog[plot.seed!!] ?: return
            val yieldId = seed.yieldId ?: return
            val green = (s.pet?.talentLevel(Talent.GAERTNERN) ?: 0) >= 5 && seed.id != Catalog.MAGIC_SEED
            val count = seed.yieldCount + (if (plot.waterings >= Garden.MAX_WATERINGS && seed.id != Catalog.MAGIC_SEED) 1 else 0) + (if (green) 1 else 0)
            addItem(yieldId, count)
            s = s.copy(garden = s.garden.copy(plots = s.garden.plots.toMutableList().also { it[index] = Plot() }))
            counters { it.copy(harvests = it.harvests + 1) }
            emit(GameEvent.Harvested(yieldId, count))
            questProgress(QuestType.HARVEST, 1)
            addPassXp(3)
            addTokens(1)
            addXp(5, now)
            addTalent(Talent.GAERTNERN, 5, now)
            wishProgress(WishKind.HARVEST, now)
        }

        private fun buyPlot() {
            val n = s.garden.plots.size
            if (n >= Garden.MAX_PLOTS) return
            val price = Garden.plotPrice(n)
            if (s.coins < price) {
                message("Dafür fehlen dir noch ${price - s.coins} Münzen.")
                return
            }
            s = s.copy(coins = s.coins - price, garden = s.garden.copy(plots = s.garden.plots + Plot()))
            message("Ein neues Beet! 🌱")
        }

        // ---------------------------------------------------------------- trips

        private fun startTrip(petId: Long, destination: Destination, now: Long) {
            val pet = s.resting.firstOrNull { it.id == petId } ?: return
            if (pet.isEgg || !pet.alive) return
            if (s.onTrip(petId) != null) return
            if (playerLevel(s) < destination.minLevel) {
                message("${destination.title} wird ab Level ${destination.minLevel} freigeschaltet.")
                return
            }
            s = s.copy(trips = s.trips + Trip(petId, destination, now, now + destination.durationMs))
            counters { it.copy(trips = it.trips + 1) }
            questProgress(QuestType.TRIP, 1)
            message("${pet.name} packt den Rucksack: ab zum Ziel ${destination.title}! ${destination.emoji}")
        }

        private fun claimTrip(petId: Long, now: Long) {
            val trip = s.onTrip(petId) ?: return
            if (now < trip.endsAt) return
            val pet = s.allPets.firstOrNull { it.id == petId }
            val raw = Trips.loot(trip)
            var f = 1.0 + 0.05 * (pet?.talentLevel(Talent.ENTDECKEN) ?: 0)
            if (Perk.TRIP in Perks.active(s)) f *= 1.3
            val w = Weather.current(s, now, zone)
            if (w.kind == WeatherKind.SUN && w.tempC >= 15) f *= 1.1
            val loot = raw.copy(coins = (raw.coins * f).roundToInt())
            s = s.copy(trips = s.trips.filter { it.petId != petId })
            addCoins(loot.coins)
            addTalentTo(petId, Talent.ENTDECKEN, 6 + 2 * trip.destination.hours, now)
            val frnd = Random(trip.startedAt xor 0xF1DL)
            val found = List(if (trip.destination.hours >= 6) 2 else 1) { Finds.roll(Finds.forDestination(trip.destination), frnd).id }
            addFinds(found)
            for ((id, n) in loot.items) addItem(id, n)
            if (loot.stickers.isNotEmpty()) addStickers(loot.stickers)
            loot.egg?.let { addEgg(it, "Mitgebracht vom Ausflug") }
            addPassXp(15)
            addTokens(2)
            dayLog("trip")
            emit(GameEvent.TripReturned(pet?.name ?: "Dein Haustier", loot))
            feel(Emotion.NEUGIERIG, now)
        }

        // ---------------------------------------------------------------- steps

        private fun stepReading(value: Float, now: Long) {
            if (value < 0f) return
            val st = s.steps
            if (st.lastSensor < 0f) {
                s = s.copy(steps = st.copy(lastSensor = value))
                return
            }
            // The hardware counter resets on reboot; then the new value is the delta.
            val delta = (if (value >= st.lastSensor) value - st.lastSensor else value).toLong().coerceIn(0L, 60_000L).toInt()
            if (delta == 0) {
                s = s.copy(steps = st.copy(lastSensor = value))
                return
            }
            val oldTotal = s.counters.totalSteps
            val newTotal = oldTotal + delta
            val today = st.today + delta
            s = s.copy(
                steps = st.copy(lastSensor = value, today = today),
                counters = s.counters.copy(
                    totalSteps = newTotal,
                    bestDaySteps = max(s.counters.bestDaySteps, today),
                ),
            )
            questProgress(QuestType.STEPS, today)
            val pet = s.pet
            if (pet != null && pet.alive && !pet.isEgg) {
                val thousands = (newTotal / 1000 - oldTotal / 1000).toInt()
                if (thousands > 0) {
                    val k = min(thousands, 10)
                    addTalent(Talent.SPORT, 2 * k, now)
                    addTalent(Talent.ENTDECKEN, 2 * k, now)
                    s = s.copy(
                        pet = s.pet!!.applyEffect(Effect(joy = 4 * k, satiety = -2 * k, energy = -2 * k, health = 2 * k)).let {
                            it.copy(care = it.care.copy(activity = it.care.activity + k))
                        },
                    )
                }
                val xp = (newTotal / 100 - oldTotal / 100).toInt()
                addXp(min(xp, 200), now)
            }
        }
    }
}
