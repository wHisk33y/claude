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
    data class FriendPlay(val moment: FriendMoment) : Action
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
    }

    fun gameMaxCoins(game: MiniGame): Int = when (game) {
        MiniGame.CATCH, MiniGame.RUNNER, MiniGame.COOK, MiniGame.MATCH, MiniGame.FLIGHT, MiniGame.STACK, MiniGame.FISHING, MiniGame.PACMAN -> 60
        MiniGame.MEMORY, MiniGame.YARN -> 40
        MiniGame.PUZZLE -> 45
        MiniGame.COLORING -> 30
        MiniGame.WHACK, MiniGame.BUBBLES, MiniGame.SIMON -> 50
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
            // In the cat mode every egg becomes a basket with a kitten.
            val line = if (s.catMode) EggLine.KATZE else found
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
                fur = if (p.isCat && p.fur == null) Skins.DEFAULT_FUR else p.fur,
            )
            val pet = s.pet
            if (pet != null && (pet.lastCare <= 0L || (pet.isCat && pet.fur == null))) s = s.copy(pet = fix(pet))
            if (s.resting.any { it.isCat && it.fur == null }) s = s.copy(resting = s.resting.map { fix(it) })
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
            var daily = s.daily
            if (daily.day != today && daily.day >= 0 && daily.log.isNotEmpty()) writeDiary(daily.day, daily.log)
            if (daily.day != today) {
                daily = daily.copy(
                    hiddenFound = false,
                    log = emptyMap(),
                    day = today,
                    quests = Quests.roll(today, s.settings.stepsAvailable, hasResting, hasJob, canChat),
                    bonusClaimed = false,
                    routine = emptySet(),
                    routineClaimed = false,
                )
            }
            val week = TimeUtil.week(now, zone)
            var weekly = s.weekly
            if (weekly.week != week) {
                weekly = WeeklyState(week = week, quests = Quests.rollWeek(week, s.settings.stepsAvailable, hasResting, hasJob, canChat))
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
            s = s.copy(daily = daily, weekly = weekly, counters = counters, steps = steps, pass = pass, event = event)
            if (newLogin) {
                addPassXp(10)
                addTokens(2)
            }
            if (userPresent && s.onboarded) checkBirthday(now)
            if (s.onboarded) {
                rainWatering(now, today)
                endVisit(now)
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
            if (s.counters.hiddenFound >= 7) findSecret(Secret.HIDDEN_7, now)
        }

        private fun friendMoment(m: FriendMoment, now: Long) {
            val guest = visitor() ?: return
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
                    if (cooldownReady("photo_xp", now, 60_000L)) addXp(3, now)
                }
                is Action.SetWeather -> s = s.copy(weather = action.weather)
                is Action.FindSecret -> findSecret(action.secret, now)
                Action.FindHidden -> findHidden(now)
                is Action.FriendPlay -> friendMoment(action.moment, now)
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
        private fun kitten(name: String, now: Long, fur: String): Pet = Pet(
            name = name,
            form = Form.KAETZCHEN,
            bornAt = now,
            hatchedAt = now,
            stageStartedAt = now,
            line = EggLine.KATZE,
            fur = fur,
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
            val cat = line == EggLine.KATZE
            val pet = if (cat) kitten(cleanName(name, "Mieze"), now, Skins.randomFur(Random(now xor 77L)))
            else Pet(name = cleanName(name, line.title.substringBefore("-")), bornAt = now, line = line)
            s = s.copy(
                pet = pet,
                resting = if (keepActive) s.resting + active!! else s.resting,
                eggs = eggs,
                lastSimulated = now,
            )
            if (keepActive) message("${active!!.name} kuschelt sich ins Kuschelhaus. 🏡")
            if (cat) welcomeKitten(now)
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
        }

        private fun claimQuestBonus(now: Long) {
            val d = s.daily
            if (d.bonusClaimed || d.quests.isEmpty() || !d.quests.all { it.claimed }) return
            s = s.copy(daily = d.copy(bonusClaimed = true))
            addCoins(Quests.BONUS_COINS)
            addXp(Quests.BONUS_XP, now)
            addPassXp(40)
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
            if (s.visit != null) {
                message("Es ist schon jemand zu Besuch.")
                return
            }
            val last = s.cooldowns["visit"] ?: 0L
            if (now - last < Friends.VISIT_COOLDOWN_MS && now >= last) {
                message("Nächster Besuch in ${TimeUtil.formatDuration(Friends.VISIT_COOLDOWN_MS - (now - last))}.")
                return
            }
            s = s.copy(visit = Visit(petId, now + Friends.VISIT_MS), cooldowns = s.cooldowns + ("visit" to now))
            counters { it.copy(visits = it.visits + 1) }
            dayLog("visit")
            addFriendship(host.id, petId, 3)
            message("${guest.name} kommt ${host.name} besuchen! 🏠")
            feel(Emotion.AUFGEREGT, now)
            touch(now)
        }

        fun visitor(): Pet? = s.visit?.let { v -> s.resting.firstOrNull { it.id == v.petId } }

        private fun endVisit(now: Long) {
            val v = s.visit ?: return
            if (now < v.until && visitor() != null && s.pet?.alive == true) return
            val guest = visitor()
            s = s.copy(visit = null)
            val host = s.pet
            if (guest != null && host != null) {
                addFriendship(host.id, guest.id, 5)
                message("${guest.name} geht zurück ins Kuschelhaus. Es war schön! 👋")
            }
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
            val guest = visitor() ?: return
            val host = s.pet ?: return
            if (!cooldownReady("befriend", now, 60_000L)) return
            addFriendship(host.id, guest.id, n)
        }

        private fun playTogether(now: Long) {
            val guest = visitor() ?: return
            val host = activePet() ?: return
            if (!cooldownReady("together", now, 10 * 60_000L)) {
                message("Die beiden ruhen sich kurz aus.")
                return
            }
            s = s.copy(
                pet = host.applyEffect(Effect(joy = 15, energy = tiring(-6))),
                resting = s.resting.map { if (it.id == guest.id) it.applyEffect(Effect(joy = 15)) else it },
            )
            addFriendship(host.id, guest.id, 6)
            emit(GameEvent.Reaction(ReactionKind.PLAY))
            questProgress(QuestType.PLAY, 1)
            routine(Routine.PLAYTIME, now)
            addXp(8, now)
            feel(Emotion.VERSPIELT, now)
            touch(now)
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
