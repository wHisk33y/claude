package de.knuffi.core

import java.time.ZoneId
import kotlin.math.max
import kotlin.math.min
import kotlin.random.Random

sealed interface Action {
    data class Start(val style: VisualStyle, val difficulty: Difficulty, val name: String) : Action
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
    data class ClaimStepTier(val index: Int) : Action
    data class StepReading(val sensorValue: Float) : Action
    data class Rename(val name: String) : Action
    data class SetStyle(val style: VisualStyle) : Action
    data class SetDifficulty(val difficulty: Difficulty) : Action
    data class UpdateSettings(val settings: Settings) : Action
    data class NewEgg(val name: String) : Action
    data class MarkNotified(val key: String) : Action
}

data class GameReward(val coins: Int, val xp: Int)

object Engine {
    const val SIM_STEP_MS = 60_000L
    const val MAX_SIM_MS = 14L * 24 * 3_600_000L
    const val HATCH_TAPS = 5

    private const val FLAG_SATIETY = 1
    private const val FLAG_JOY = 2
    private const val FLAG_HYGIENE = 4
    private const val FLAG_HEALTH = 8

    /** Advances time without any user interaction (worker, widget, app resume). */
    fun tick(state: GameState, now: Long, zone: ZoneId, userPresent: Boolean = false): Outcome {
        val tx = Tx(state)
        tx.simulate(now, zone)
        tx.refreshDay(now, zone, userPresent)
        tx.checkAchievements(now)
        return tx.outcome()
    }

    /** Applies a user action after bringing the simulation up to date. */
    fun perform(state: GameState, action: Action, now: Long, zone: ZoneId, userPresent: Boolean = true): Outcome {
        val tx = Tx(state)
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

    internal class Tx(var s: GameState) {
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

        private fun addCoins(amount: Int) {
            if (amount <= 0) return
            s = s.copy(
                coins = s.coins + amount,
                counters = s.counters.copy(coinsEarned = s.counters.coinsEarned + amount),
            )
            emit(GameEvent.CoinsGained(amount))
        }

        private fun cooldownReady(key: String, now: Long, ms: Long): Boolean {
            val last = s.cooldowns[key] ?: 0L
            if (now - last < ms && now >= last) return false
            s = s.copy(cooldowns = s.cooldowns + (key to now))
            return true
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
                val memorial = Memorial(p.name, p.form, p.level, p.bornAt, t)
                s = s.copy(memorials = s.memorials + memorial)
                emit(GameEvent.Died(memorial))
            }
            checkPerfect()
        }

        private fun step(p0: Pet, dt: Long, t: Long, zone: ZoneId, classic: Boolean): Pet {
            val h = dt / 3_600_000.0
            val m = if (classic) 1.4 else 1.0
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
                satiety -= 2.5 * h * m
                joy -= 0.5 * h * m
                hygiene -= 1.0 * h * m
                energy += 18.0 * h
                if (energy >= 100.0) {
                    energy = 100.0
                    sleeping = false
                }
            } else {
                val night = TimeUtil.isNight(t, zone)
                satiety -= 6.0 * h * m
                joy -= 5.0 * h * m
                energy -= (if (night) 9.0 else 4.5) * h * m
                hygiene -= (2.5 + 3.0 * poops) * h * m
                if (energy < 18.0) {
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
                joy -= 2.0 * h * m
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
            if (!s.counters.perfectCare && p.alive && !p.isEgg &&
                p.satiety > 90 && p.joy > 90 && p.energy > 90 && p.hygiene > 90 && p.health > 90
            ) {
                s = s.copy(counters = s.counters.copy(perfectCare = true))
            }
        }

        // ---------------------------------------------------------------- daily stuff

        fun refreshDay(now: Long, zone: ZoneId, userPresent: Boolean) {
            val today = TimeUtil.epochDay(now, zone)
            var daily = s.daily
            if (daily.day != today) {
                daily = daily.copy(
                    day = today,
                    quests = Quests.roll(today, s.settings.stepsAvailable),
                    bonusClaimed = false,
                )
            }
            var counters = s.counters
            if (userPresent && s.onboarded && daily.lastLoginDay != today) {
                val streak = if (daily.lastLoginDay == today - 1) daily.streak + 1 else 1
                daily = daily.copy(lastLoginDay = today, streak = streak)
                counters = counters.copy(maxStreak = max(counters.maxStreak, streak))
            }
            if (userPresent && s.onboarded && TimeUtil.hour(now, zone) < 4) {
                counters = counters.copy(nightOwl = true)
            }
            var steps = s.steps
            if (steps.day != today) {
                steps = steps.copy(day = today, today = 0, claimedTiers = emptySet())
            }
            s = s.copy(daily = daily, counters = counters, steps = steps)
        }

        private fun questProgress(type: QuestType, amount: Int) {
            var changed = false
            val quests = s.daily.quests.map { q ->
                if (q.type != type || q.claimed) return@map q
                val wasDone = q.done
                val value = when (type.kind) {
                    QuestKind.COUNT -> q.progress + amount
                    QuestKind.MAX -> max(q.progress, amount)
                }.coerceAtMost(q.target)
                if (value == q.progress) return@map q
                changed = true
                if (!wasDone && value >= q.target) emit(GameEvent.QuestDone(type))
                q.copy(progress = value)
            }
            if (changed) s = s.copy(daily = s.daily.copy(quests = quests))
        }

        // ---------------------------------------------------------------- xp & evolution

        private fun addXp(amount: Int, now: Long) {
            val pet = s.pet ?: return
            if (amount <= 0 || !pet.alive || pet.isEgg) return
            emit(GameEvent.XpGained(amount))
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
            s = s.copy(
                pet = s.pet!!.copy(xp = xp, level = level),
                counters = s.counters.copy(maxLevel = max(s.counters.maxLevel, level)),
            )
            maybeEvolve(now)
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
                    ),
                )
                emit(GameEvent.Evolved(from, to))
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
                is Action.Equip -> equip(action.itemId)
                is Action.Unequip -> unequip(action.slot)
                Action.ClaimDaily -> claimDaily(now, zone)
                is Action.ClaimQuest -> claimQuest(action.index, now)
                Action.ClaimQuestBonus -> claimQuestBonus(now)
                is Action.ClaimStepTier -> claimStepTier(action.index)
                is Action.StepReading -> stepReading(action.sensorValue, now)
                is Action.Rename -> updatePet { it.copy(name = cleanName(action.name, it.name)) }
                is Action.SetStyle -> s = s.copy(style = action.style)
                is Action.SetDifficulty -> s = s.copy(difficulty = action.difficulty)
                is Action.UpdateSettings -> {
                    val stepsChanged = action.settings.stepsAvailable != s.settings.stepsAvailable
                    s = s.copy(settings = action.settings)
                    if (stepsChanged && !action.settings.stepsAvailable) {
                        // Replace an impossible step quest.
                        val quests = s.daily.quests
                        if (quests.any { it.type == QuestType.STEPS && !it.claimed }) {
                            val used = quests.map { it.type }.toSet()
                            val replacement = QuestType.entries.first { it != QuestType.STEPS && it !in used }
                            s = s.copy(daily = s.daily.copy(quests = quests.map {
                                if (it.type == QuestType.STEPS && !it.claimed) QuestProgress(replacement, replacement.target) else it
                            }))
                        }
                    }
                }
                is Action.NewEgg -> newEgg(action.name, now)
                is Action.MarkNotified -> s = s.copy(notifyLog = s.notifyLog + (action.key to now))
            }
            checkPerfect()
        }

        private fun cleanName(name: String, fallback: String): String =
            name.trim().take(16).ifBlank { fallback }

        private fun start(a: Action.Start, now: Long, zone: ZoneId) {
            val name = cleanName(a.name, "Knuffi")
            s = GameState(
                onboarded = true,
                style = a.style,
                difficulty = a.difficulty,
                pet = Pet(name = name, bornAt = now),
                coins = Catalog.START_COINS,
                inventory = Catalog.startInventory,
                owned = setOf(Catalog.DEFAULT_ROOM),
                equipped = mapOf(Slot.ROOM to Catalog.DEFAULT_ROOM),
                settings = s.settings,
                lastSimulated = now,
            )
            refreshDay(now, zone, userPresent = true)
        }

        private fun newEgg(name: String, now: Long) {
            val old = s.pet
            if (old != null && old.alive && !old.isEgg) {
                s = s.copy(memorials = s.memorials + Memorial(old.name, old.form, old.level, old.bornAt, now))
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
            s = s.copy(
                pet = pet.copy(
                    form = Form.BABY,
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
        }

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
                nextPoopAt = if (p.nextPoopAt == 0L && p.poops < 3) now + 3L * 3_600_000L else p.nextPoopAt,
            )
            s = s.copy(pet = p, counters = s.counters.copy(feeds = s.counters.feeds + 1))
            emit(GameEvent.Reaction(ReactionKind.EAT, item.id))
            questProgress(QuestType.FEED, 1)
            if (cooldownReady("xp_feed", now, 30_000L)) addXp(4, now)
        }

        private fun useItem(itemId: String, now: Long) {
            val item = Catalog[itemId] ?: return
            if (item.kind == ItemKind.FOOD) {
                feed(itemId, now)
                return
            }
            if (item.kind != ItemKind.CARE) return
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
                pet = pet.applyEffect(Effect(joy = 15, energy = -7, satiety = -4, hygiene = -2)).let {
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
                pet = pet.applyEffect(Effect(joy = 12, energy = -10, satiety = -5, hygiene = -3)).let {
                    it.copy(care = it.care.copy(activity = it.care.activity + 2))
                },
                counters = c.copy(
                    gamesPlayed = c.gamesPlayed + 1,
                    bestCatch = if (game == MiniGame.CATCH) max(c.bestCatch, score) else c.bestCatch,
                    bestWhack = if (game == MiniGame.WHACK) max(c.bestWhack, score) else c.bestWhack,
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
            }
            addCoins(reward.coins)
            addXp(reward.xp, now)
        }

        private fun buy(itemId: String) {
            val item = Catalog[itemId] ?: return
            val level = s.pet?.level ?: 1
            when {
                item.price <= 0 -> return
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
            if (item.consumable) {
                s = s.copy(inventory = s.inventory + (item.id to s.count(item.id) + 1))
            } else {
                s = s.copy(owned = s.owned + item.id)
                equip(item.id)
            }
            emit(GameEvent.Reaction(ReactionKind.BUY, item.id))
            questProgress(QuestType.SHOP, 1)
        }

        private fun equip(itemId: String) {
            val item = Catalog[itemId] ?: return
            val slot = item.kind.slot ?: return
            if (itemId !in s.owned) return
            s = s.copy(equipped = s.equipped + (slot to itemId))
        }

        private fun unequip(slot: Slot) {
            s = if (slot == Slot.ROOM) {
                s.copy(equipped = s.equipped + (Slot.ROOM to Catalog.DEFAULT_ROOM))
            } else {
                s.copy(equipped = s.equipped - slot)
            }
        }

        private fun claimDaily(now: Long, zone: ZoneId) {
            if (!canClaimDaily(s, now, zone)) return
            val idx = DailyRewards.dayIndex(s.daily.streak)
            s = s.copy(daily = s.daily.copy(rewardClaimedDay = TimeUtil.epochDay(now, zone)))
            addCoins(DailyRewards.coins[idx])
            if (idx == 6) {
                s = s.copy(inventory = s.inventory + (DailyRewards.BONUS_ITEM to s.count(DailyRewards.BONUS_ITEM) + 1))
                message("Bonus: ein Törtchen für dich! 🍰")
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
        }

        private fun claimQuestBonus(now: Long) {
            val d = s.daily
            if (d.bonusClaimed || d.quests.isEmpty() || !d.quests.all { it.claimed }) return
            s = s.copy(daily = d.copy(bonusClaimed = true))
            addCoins(Quests.BONUS_COINS)
            addXp(Quests.BONUS_XP, now)
        }

        private fun claimStepTier(index: Int) {
            val tier = StepRewards.tiers.getOrNull(index) ?: return
            if (index in s.steps.claimedTiers || s.steps.today < tier.first) return
            s = s.copy(steps = s.steps.copy(claimedTiers = s.steps.claimedTiers + index))
            addCoins(tier.second)
        }

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
