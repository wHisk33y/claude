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
        tx.simulate(now, zone)
        tx.refreshDay(now, zone, userPresent)
        tx.checkAchievements(now)
        return tx.outcome()
    }

    /** Applies a user action after bringing the simulation up to date. */
    fun perform(state: GameState, action: Action, now: Long, zone: ZoneId, userPresent: Boolean = true): Outcome {
        val tx = Tx(state, zone)
        tx.migrate(now)
        tx.simulate(now, zone)
        tx.refreshDay(now, zone, userPresent)
        tx.apply(action, now, zone)
        tx.checkAchievements(now)
        return tx.outcome()
    }

    fun gameReward(game: MiniGame, score: Int): GameReward = when (game) {
        MiniGame.CATCH -> GameReward(coins = (score / 10).coerceIn(2, 60), xp = (5 + score / 8).coerceAtMost(50))
        MiniGame.MEMORY -> GameReward(coins = (46 - 2 * (score - 8)).coerceIn(10, 40), xp = 20)
        MiniGame.WHACK -> GameReward(coins = (score * 6 / 5).coerceIn(2, 50), xp = (5 + score).coerceAtMost(50))
        MiniGame.RUNNER -> GameReward(coins = (score / 15).coerceIn(2, 60), xp = (5 + score / 12).coerceAtMost(50))
        MiniGame.BUBBLES -> GameReward(coins = (score * 3 / 2).coerceIn(2, 50), xp = (5 + score).coerceAtMost(50))
        MiniGame.SIMON -> GameReward(coins = (score * 5).coerceIn(2, 50), xp = (score * 5).coerceIn(5, 50))
    }

    fun gameMaxCoins(game: MiniGame): Int = when (game) {
        MiniGame.CATCH, MiniGame.RUNNER -> 60
        MiniGame.MEMORY -> 40
        MiniGame.WHACK, MiniGame.BUBBLES, MiniGame.SIMON -> 50
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

        private fun addEgg(line: EggLine, reason: String) {
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
            while (t < now && p.alive) {
                val dt = min(SIM_STEP_MS, now - t)
                p = step(p, dt, t, zone, classic)
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

        private fun step(p0: Pet, dt: Long, t: Long, zone: ZoneId, classic: Boolean): Pet {
            val h = dt / 3_600_000.0
            val m = if (classic) 1.4 else 1.0
            val n = CareTuning.needs(s.settings.careLevel)
            val tire = CareTuning.tiring(s.settings.sleepLevel)
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
                satiety -= 2.5 * h * m * n
                joy -= 0.5 * h * m * n
                hygiene -= 1.0 * h * m * n
                energy += SLEEP_RECOVERY_PER_HOUR * h * rest
                if (energy >= 100.0) {
                    energy = 100.0
                    sleeping = false
                }
            } else {
                val night = TimeUtil.isNight(t, zone)
                satiety -= 6.0 * h * m * n
                joy -= 5.0 * h * m * n
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
            var daily = s.daily
            if (daily.day != today) {
                daily = daily.copy(
                    day = today,
                    quests = Quests.roll(today, s.settings.stepsAvailable, hasResting),
                    bonusClaimed = false,
                )
            }
            val week = TimeUtil.week(now, zone)
            var weekly = s.weekly
            if (weekly.week != week) {
                weekly = WeeklyState(week = week, quests = Quests.rollWeek(week, s.settings.stepsAvailable, hasResting))
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
            s = s.copy(event = s.event.copy(tokens = s.event.tokens + amount))
            if (first) {
                s = s.copy(milestones = s.milestones + "event:${s.event.key}")
                counters { it.copy(eventsJoined = it.eventsJoined + 1) }
            }
            emit(GameEvent.TokensGained(amount))
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

        private fun addXp(amount: Int, now: Long) {
            val pet = s.pet ?: return
            if (amount <= 0 || !pet.alive || pet.isEgg) return
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
            }
            if (level >= Leveling.MAX_LEVEL) xp = min(xp, Leveling.xpToNext(level))
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
            val lines = EggLine.entries.filter { !it.eventOnly }
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
                is Action.Buy -> buy(action.itemId)
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
                is Action.ReleasePet -> releasePet(action.petId, now)
                Action.BuyRestSlot -> buyRestSlot()
                is Action.Plant -> plant(action.plot, action.seedId, now)
                is Action.Water -> water(action.plot, now)
                is Action.Harvest -> harvest(action.plot, now)
                Action.BuyPlot -> buyPlot()
                is Action.StartTrip -> startTrip(action.petId, action.destination, now)
                is Action.ClaimTrip -> claimTrip(action.petId, now)
                is Action.ClaimPass -> claimPass(action.tier, now)
                is Action.BuyEventOffer -> buyEventOffer(action.offerId, now)
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
            val line = if (a.line.eventOnly) EggLine.KNUFFEL else a.line
            s = GameState(
                onboarded = true,
                difficulty = a.difficulty,
                pet = Pet(name = name, bornAt = now, line = line),
                coins = Catalog.START_COINS,
                inventory = Catalog.startInventory,
                owned = setOf(Catalog.DEFAULT_ROOM) + Catalog.defaultFurniture,
                equipped = Catalog.defaultEquipped(a.look),
                settings = s.settings.copy(look = a.look),
                lastSimulated = now,
            )
            refreshDay(now, zone, userPresent = true)
        }

        /** Used after a pet has gone to the stars in classic mode: a fresh Knuffel egg. */
        private fun newEgg(name: String, now: Long) {
            val old = s.pet
            if (old != null && old.alive && !old.isEgg) {
                s = s.copy(memorials = s.memorials + Memorial(old.name, old.form, old.level, old.bornAt, now, old.shiny))
            }
            s = s.copy(
                pet = Pet(name = cleanName(name, old?.name ?: "Knuffi"), bornAt = now),
                lastSimulated = now,
            )
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
            val pet = Pet(name = cleanName(name, line.title.substringBefore("-")), bornAt = now, line = line)
            s = s.copy(
                pet = pet,
                resting = if (keepActive) s.resting + active!! else s.resting,
                eggs = eggs,
                lastSimulated = now,
            )
            if (keepActive) message("${active!!.name} kuschelt sich ins Kuschelhaus. 🏡")
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

        private fun releasePet(petId: Long, now: Long) {
            val pet = s.resting.firstOrNull { it.id == petId } ?: return
            if (s.onTrip(petId) != null) return
            s = s.copy(
                resting = s.resting.filter { it.id != petId },
                memorials = if (pet.isEgg) s.memorials else s.memorials + Memorial(pet.name, pet.form, pet.level, pet.bornAt, now, pet.shiny),
            )
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
            var p = pet.applyEffect(item.effect)
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
                    addXp(8, now)
                }
                else -> {
                    val active = activePet() ?: return
                    if (item.effect.energy > 0 && active.energy >= 95) {
                        refuse("${active.name} hat schon genug Energie!")
                        return
                    }
                    if (!consume(item)) return
                    s = s.copy(pet = active.applyEffect(item.effect))
                    emit(GameEvent.Reaction(ReactionKind.DRINK, item.id))
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
            s = s.copy(
                pet = pet.applyEffect(Effect(joy = 3)),
                counters = s.counters.copy(pets = s.counters.pets + 1),
            )
            emit(GameEvent.Reaction(ReactionKind.PET))
            questProgress(QuestType.PET, 1)
            if (cooldownReady("xp_stroke", now, 20_000L)) addXp(1, now)
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
            }
            if (stars(game, score) == 3) questProgress(QuestType.STARS3, 1)
            addCoins(reward.coins)
            addXp(reward.xp, now)
            addPassXp(5)
            addTokens(1)
        }

        // ---------------------------------------------------------------- shop

        private fun buy(itemId: String) {
            val item = Catalog[itemId] ?: return
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
                s.coins < item.price -> {
                    message("Dafür fehlen dir noch ${item.price - s.coins} Münzen.")
                    return
                }
            }
            s = s.copy(
                coins = s.coins - item.price,
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
                slot == Slot.HAT || slot == Slot.FACE || slot == Slot.NECK -> s.copy(equipped = s.equipped - slot)
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

        // ---------------------------------------------------------------- garden

        private fun plant(index: Int, seedId: String, now: Long) {
            val plot = s.garden.plots.getOrNull(index) ?: return
            if (!plot.empty) return
            val seed = Catalog[seedId]?.takeIf { it.kind == ItemKind.SEED } ?: return
            if (!consume(seed)) return
            val season = EventCalendar.season(EventCalendar.date(now, zone))
            val planted = Plot(seed = seedId, plantedAt = now, readyAt = now + Catalog.growMs(seed, season))
            s = s.copy(garden = s.garden.copy(plots = s.garden.plots.toMutableList().also { it[index] = planted }))
            counters { it.copy(plantings = it.plantings + 1) }
            questProgress(QuestType.PLANT, 1)
            addPassXp(2)
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
        }

        private fun harvest(index: Int, now: Long) {
            val plot = s.garden.plots.getOrNull(index) ?: return
            if (!plot.ready(now)) return
            val seed = Catalog[plot.seed!!] ?: return
            val yieldId = seed.yieldId ?: return
            val count = seed.yieldCount + if (plot.waterings >= Garden.MAX_WATERINGS && seed.id != Catalog.MAGIC_SEED) 1 else 0
            addItem(yieldId, count)
            s = s.copy(garden = s.garden.copy(plots = s.garden.plots.toMutableList().also { it[index] = Plot() }))
            counters { it.copy(harvests = it.harvests + 1) }
            emit(GameEvent.Harvested(yieldId, count))
            questProgress(QuestType.HARVEST, 1)
            addPassXp(3)
            addTokens(1)
            addXp(5, now)
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
            val loot = Trips.loot(trip)
            s = s.copy(trips = s.trips.filter { it.petId != petId })
            addCoins(loot.coins)
            for ((id, n) in loot.items) addItem(id, n)
            if (loot.stickers.isNotEmpty()) addStickers(loot.stickers)
            loot.egg?.let { addEgg(it, "Mitgebracht vom Ausflug") }
            addPassXp(15)
            addTokens(2)
            emit(GameEvent.TripReturned(pet?.name ?: "Dein Haustier", loot))
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
                    s = s.copy(
                        pet = pet.applyEffect(Effect(joy = 4 * k, satiety = -2 * k, energy = -2 * k, health = 2 * k)).let {
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
