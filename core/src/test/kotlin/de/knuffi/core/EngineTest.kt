package de.knuffi.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneId
import java.time.ZonedDateTime

class EngineTest {
    private val zone: ZoneId = ZoneId.of("Europe/Berlin")
    private val t0: Long = ZonedDateTime.of(2026, 3, 10, 9, 0, 0, 0, zone).toInstant().toEpochMilli()
    private val hour = 3_600_000L

    private fun started(difficulty: Difficulty = Difficulty.RELAXED): GameState {
        var s = Engine.perform(GameState(), Action.Start(difficulty, "Mochi"), t0, zone).state
        repeat(Engine.HATCH_TAPS) { s = Engine.perform(s, Action.HatchTap, t0, zone).state }
        return s
    }

    @Test
    fun startCreatesEggAndHatches() {
        val egg = Engine.perform(GameState(), Action.Start(Difficulty.CLASSIC, "  Bobo  "), t0, zone)
        assertEquals("Bobo", egg.state.pet!!.name)
        assertEquals(Form.EGG, egg.state.pet!!.form)
        assertEquals(Catalog.START_COINS, egg.state.coins)
        assertEquals(3, egg.state.daily.quests.size)
        assertEquals(1, egg.state.daily.streak)

        var s = egg.state
        var hatched = false
        repeat(Engine.HATCH_TAPS) {
            val o = Engine.perform(s, Action.HatchTap, t0, zone)
            s = o.state
            if (o.events.contains(GameEvent.Hatched)) hatched = true
        }
        assertTrue(hatched)
        assertEquals(Form.BABY, s.pet!!.form)
        assertTrue(s.isUnlocked(Achievement.HATCH))
    }

    @Test
    fun statsDecayOverTime() {
        val s = started()
        val later = Engine.tick(s, t0 + 5 * hour, zone).state
        val p = later.pet!!
        assertTrue(p.satiety < s.pet!!.satiety)
        assertTrue(p.joy < s.pet!!.joy)
        assertTrue(p.alive)
    }

    @Test
    fun relaxedPetNeverDies() {
        val s = started(Difficulty.RELAXED)
        val later = Engine.tick(s, t0 + 10 * 24 * hour, zone).state
        assertTrue(later.pet!!.alive)
        assertTrue(later.pet!!.health >= 5.0)
        assertTrue(later.pet!!.care.mistakes > 0)
    }

    @Test
    fun classicPetDiesWhenNeglected() {
        val s = started(Difficulty.CLASSIC)
        val o = Engine.tick(s, t0 + 6 * 24 * hour, zone)
        assertFalse(o.state.pet!!.alive)
        assertTrue(o.events.any { it is GameEvent.Died })
        assertEquals(1, o.state.memorials.size)

        val fresh = Engine.perform(o.state, Action.NewEgg("Neo"), t0 + 6 * 24 * hour, zone).state
        assertEquals(Form.EGG, fresh.pet!!.form)
        assertTrue(fresh.pet!!.alive)
    }

    @Test
    fun feedingRaisesSatietyAndCountsQuest() {
        var s = started()
        s = Engine.tick(s, t0 + 6 * hour, zone).state
        val before = s.pet!!.satiety
        val o = Engine.perform(s, Action.Feed("rice"), t0 + 6 * hour, zone)
        assertTrue(o.state.pet!!.satiety > before)
        assertEquals(1, o.state.counters.feeds)
        assertTrue(o.events.any { it is GameEvent.Reaction && it.kind == ReactionKind.EAT })
    }

    @Test
    fun feedingUsesInventory() {
        var s = started()
        s = Engine.tick(s, t0 + 6 * hour, zone).state
        assertEquals(3, s.count("apple"))
        s = Engine.perform(s, Action.Feed("apple"), t0 + 6 * hour, zone).state
        assertEquals(2, s.count("apple"))
        val none = Engine.perform(s, Action.Feed("sushi"), t0 + 6 * hour, zone)
        assertTrue(none.events.any { it is GameEvent.Message })
    }

    @Test
    fun levelUpAndEvolution() {
        var s = started()
        var now = t0
        val evolutions = mutableListOf<GameEvent.Evolved>()
        // Play lots of mini games to gain XP quickly.
        repeat(60) {
            now += 10 * 60_000L
            s = s.copy(pet = s.pet!!.copy(energy = 100.0, satiety = 90.0, joy = 90.0, hygiene = 90.0, sick = false, sleeping = false))
            val o = Engine.perform(s, Action.GameFinished(MiniGame.CATCH, 400), now, zone)
            s = o.state
            evolutions += o.events.filterIsInstance<GameEvent.Evolved>()
        }
        assertTrue("level was ${s.pet!!.level}", s.pet!!.level >= Stage.ADULT.minLevel)
        assertEquals(Stage.ADULT, s.pet!!.stage)
        assertEquals(3, evolutions.size)
        // Only mini games → sporty evolution line with good care.
        assertEquals(Form.DRAKO, s.pet!!.form)
        assertTrue(s.isUnlocked(Achievement.EVOLVE_ADULT))
    }

    @Test
    fun foodieBranch() {
        val p = Pet(name = "x", form = Form.LUMI, bornAt = 0, level = 13, care = CareLog(food = 30, activity = 5, moodSum = 800.0, moodHours = 10.0))
        assertEquals(Form.MOCHI_KOENIG, Leveling.nextForm(p))
        val bad = p.copy(care = p.care.copy(mistakes = 7))
        assertEquals(Form.SCHATTLING, Leveling.nextForm(bad))
        val balanced = p.copy(care = p.care.copy(food = 10, activity = 10))
        assertEquals(Form.STELLARIS, Leveling.nextForm(balanced))
    }

    @Test
    fun shopBuyAndEquip() {
        var s = started()
        s = s.copy(coins = 500)
        s = Engine.perform(s, Action.Buy("hat_bow"), t0, zone).state
        assertTrue("hat_bow" in s.owned)
        assertEquals("hat_bow", s.equipped[Slot.HAT])
        assertEquals(460, s.coins)
        val again = Engine.perform(s, Action.Buy("hat_bow"), t0, zone)
        assertEquals(460, again.state.coins)
        // Level locked
        val locked = Engine.perform(s, Action.Buy("hat_crown"), t0, zone)
        assertEquals(460, locked.state.coins)
        s = Engine.perform(s, Action.Unequip(Slot.HAT), t0, zone).state
        assertEquals(null, s.equipped[Slot.HAT])
    }

    @Test
    fun dailyStreakAndReward() {
        var s = started()
        assertTrue(Engine.canClaimDaily(s, t0, zone))
        s = Engine.perform(s, Action.ClaimDaily, t0, zone).state
        assertFalse(Engine.canClaimDaily(s, t0, zone))
        val coinsAfterFirst = s.coins
        // Next day
        s = Engine.perform(s, Action.Stroke, t0 + 24 * hour, zone).state
        assertEquals(2, s.daily.streak)
        s = Engine.perform(s, Action.ClaimDaily, t0 + 24 * hour, zone).state
        assertTrue(s.coins > coinsAfterFirst)
        // Skip a day → streak resets
        s = Engine.perform(s, Action.Stroke, t0 + 72 * hour, zone).state
        assertEquals(1, s.daily.streak)
    }

    @Test
    fun workerTickDoesNotCountAsLogin() {
        var s = started()
        s = Engine.tick(s, t0 + 24 * hour, zone, userPresent = false).state
        assertEquals(1, s.daily.streak)
        assertFalse(Engine.canClaimDaily(s, t0 + 24 * hour, zone))
    }

    @Test
    fun stepsAccumulateAndHandleReboot() {
        var s = started()
        s = Engine.perform(s, Action.StepReading(10_000f), t0, zone).state
        assertEquals(0, s.steps.today)
        s = Engine.perform(s, Action.StepReading(12_500f), t0 + hour, zone).state
        assertEquals(2_500, s.steps.today)
        // reboot: counter restarts from 0
        s = Engine.perform(s, Action.StepReading(600f), t0 + 2 * hour, zone).state
        assertEquals(3_100, s.steps.today)
        assertEquals(3_100L, s.counters.totalSteps)
        s = Engine.perform(s, Action.ClaimStepTier(1), t0 + 2 * hour, zone).state
        assertTrue(1 in s.steps.claimedTiers)
        // Next day resets today's count
        s = Engine.perform(s, Action.StepReading(1_600f), t0 + 26 * hour, zone).state
        assertEquals(1_000, s.steps.today)
        assertTrue(s.steps.claimedTiers.isEmpty())
    }

    @Test
    fun questCompletionAndClaim() {
        var s = started()
        val quests = listOf(
            QuestProgress(QuestType.FEED, 3),
            QuestProgress(QuestType.CLEAN, 1),
            QuestProgress(QuestType.PET, 10),
        )
        s = s.copy(daily = s.daily.copy(quests = quests), pet = s.pet!!.copy(satiety = 20.0, hygiene = 40.0))
        var now = t0
        repeat(3) {
            now += 1000
            s = Engine.perform(s, Action.Feed("rice"), now, zone).state
        }
        assertTrue(s.daily.quests[0].done)
        s = Engine.perform(s, Action.Clean, now, zone).state
        assertTrue(s.daily.quests[1].done)
        repeat(10) {
            now += 2_000
            s = Engine.perform(s, Action.Stroke, now, zone).state
        }
        assertTrue(s.daily.quests[2].done)
        val coins = s.coins
        for (i in 0..2) s = Engine.perform(s, Action.ClaimQuest(i), now, zone).state
        assertTrue(s.coins > coins)
        s = Engine.perform(s, Action.ClaimQuestBonus, now, zone).state
        assertTrue(s.daily.bonusClaimed)
    }

    @Test
    fun sleepingRestoresEnergyAndAutoWakes() {
        var s = started()
        s = s.copy(pet = s.pet!!.copy(energy = 50.0))
        s = Engine.perform(s, Action.ToggleSleep, t0, zone).state
        assertTrue(s.pet!!.sleeping)
        s = Engine.tick(s, t0 + 4 * hour, zone).state
        assertFalse(s.pet!!.sleeping)
        assertTrue(s.pet!!.energy > 90)
    }

    @Test
    fun medicineCures() {
        var s = started()
        s = s.copy(pet = s.pet!!.copy(sick = true, health = 40.0))
        s = Engine.perform(s, Action.UseItem(Catalog.MEDICINE), t0, zone).state
        assertFalse(s.pet!!.sick)
        assertEquals(0, s.count(Catalog.MEDICINE))
        assertTrue(s.isUnlocked(Achievement.HEAL_1))
    }

    @Test
    fun oldSavesWithStyleStillLoad() {
        val old = GameJson.encode(started()).replaceFirst("{", "{\"style\":\"PIXEL\",")
        val back = GameJson.decode(old)
        assertNotNull(back)
        assertEquals(ThemeMode.SYSTEM, back!!.settings.themeMode)
    }

    @Test
    fun serializationRoundTrip() {
        val s = started().copy(equipped = mapOf(Slot.HAT to "hat_bow", Slot.ROOM to "room_cozy"), owned = setOf("hat_bow"))
        val json = GameJson.encode(s)
        val back = GameJson.decode(json)
        assertNotNull(back)
        assertEquals(s, back)
    }

    @Test
    fun linesHaveCompleteFamilyTrees() {
        assertEquals(100, Form.creatures.size)
        for (line in EggLine.entries) {
            assertNotNull("$line baby", Form.find(line, Role.BABY))
            assertNotNull("$line legend", Form.find(line, Role.LEGEND))
            if (line != EggLine.KNUFFEL) {
                for (r in listOf(Role.CHILD, Role.TEEN, Role.TEEN_B, Role.ADULT_SPORTY, Role.ADULT_FOODIE, Role.ADULT_BALANCED)) {
                    assertNotNull("$line $r", Form.find(line, r))
                }
            }
        }
    }

    @Test
    fun evolutionFollowsEggLine() {
        val egg = Pet(name = "x", bornAt = 0, line = EggLine.WALD)
        assertEquals(Form.MOOSI, Leveling.nextForm(egg))
        val baby = egg.copy(form = Form.MOOSI, care = CareLog(moodSum = 700.0, moodHours = 10.0))
        assertEquals(Form.ZWEIGI, Leveling.nextForm(baby))
        val child = egg.copy(form = Form.ZWEIGI, care = CareLog(moodSum = 700.0, moodHours = 10.0))
        assertEquals(Form.FARNI, Leveling.nextForm(child))
        assertEquals(Form.KNORRI, Leveling.nextForm(child.copy(care = CareLog(mistakes = 6))))
        val teen = egg.copy(form = Form.FARNI, care = CareLog(activity = 20, food = 3, moodSum = 700.0, moodHours = 10.0))
        assertEquals(Form.HIRSCHLING, Leveling.nextForm(teen))
        val perfect = teen.copy(care = CareLog(activity = 20, food = 3, moodSum = 85.0 * 80, moodHours = 80.0))
        assertEquals(Form.WELTENBAUM, Leveling.nextForm(perfect))
        assertEquals(Form.SCHATTLING, Leveling.nextForm(teen.copy(care = CareLog(mistakes = 9))))
    }

    @Test
    fun hatchingNewEggMovesPetToKuschelhausAndSwitchingBack() {
        var s = started()
        s = s.copy(eggs = mapOf(EggLine.FEUER to 1))
        val first = s.pet!!
        s = Engine.perform(s, Action.HatchEgg(EggLine.FEUER, "Funki"), t0 + hour, zone).state
        assertEquals(EggLine.FEUER, s.pet!!.line)
        assertTrue(s.pet!!.isEgg)
        assertEquals(listOf(first.id), s.resting.map { it.id })
        assertEquals(0, s.eggCount(EggLine.FEUER))
        repeat(Engine.HATCH_TAPS) { s = Engine.perform(s, Action.HatchTap, t0 + hour, zone).state }
        assertEquals(Form.FUNKI, s.pet!!.form)
        assertTrue(Form.FUNKI.name in s.album)
        // Resting pets don't change while the other one is active.
        val restingBefore = s.resting.first()
        s = Engine.tick(s, t0 + 10 * hour, zone).state
        assertEquals(restingBefore, s.resting.first())
        s = Engine.perform(s, Action.SwitchPet(first.id), t0 + 10 * hour, zone).state
        assertEquals(first.id, s.pet!!.id)
        assertEquals(Form.FUNKI, s.resting.first().form)
    }

    @Test
    fun kuschelhausCapacityIsRespected() {
        var s = started()
        s = s.copy(restSlots = 0, eggs = mapOf(EggLine.MEER to 1))
        s = Engine.perform(s, Action.HatchEgg(EggLine.MEER, "Blubb"), t0, zone).state
        assertEquals(EggLine.KNUFFEL, s.pet!!.line)
        assertEquals(1, s.eggCount(EggLine.MEER))
    }

    @Test
    fun glitterMakesShinyHatch() {
        var s = Engine.perform(GameState(), Action.Start(Difficulty.RELAXED, "Glitzi", LookStyle.ZAUBER, EggLine.EINHORN), t0, zone).state
        s = s.copy(inventory = s.inventory + (Catalog.GLITTER to 1))
        s = Engine.perform(s, Action.UseItem(Catalog.GLITTER), t0, zone).state
        repeat(Engine.HATCH_TAPS) { s = Engine.perform(s, Action.HatchTap, t0, zone).state }
        assertEquals(Form.PONYCHEN, s.pet!!.form)
        assertTrue(s.pet!!.shiny)
        assertTrue(s.discovered(Form.PONYCHEN, shiny = true))
        assertEquals(1, s.counters.shinies)
    }

    @Test
    fun gardenPlantWaterHarvest() {
        var s = started()
        val seeds = s.count("seed_carrot")
        s = Engine.perform(s, Action.Plant(0, "seed_carrot"), t0, zone).state
        assertEquals(seeds - 1, s.count("seed_carrot"))
        assertFalse(s.garden.plots[0].ready(t0))
        val before = s.garden.plots[0].readyAt
        s = Engine.perform(s, Action.Water(0), t0 + 60_000, zone).state
        assertTrue(s.garden.plots[0].readyAt < before)
        val nope = Engine.perform(s, Action.Harvest(0), t0 + 90_000, zone).state
        assertEquals(0, nope.count("carrot"))
        s = Engine.perform(s, Action.Harvest(0), t0 + 3 * hour, zone).state
        assertEquals(2, s.count("carrot"))
        assertTrue(s.garden.plots[0].empty)
        assertEquals(1, s.counters.harvests)
    }

    @Test
    fun tripsBringLoot() {
        var s = started()
        s = s.copy(eggs = mapOf(EggLine.WALD to 1))
        val traveller = s.pet!!.id
        s = Engine.perform(s, Action.HatchEgg(EggLine.WALD, "Moosi"), t0, zone).state
        s = Engine.perform(s, Action.StartTrip(traveller, Destination.WIESE), t0, zone).state
        assertNotNull(s.onTrip(traveller))
        // can't switch to a travelling pet
        val blocked = Engine.perform(s, Action.SwitchPet(traveller), t0, zone).state
        assertEquals(EggLine.WALD, blocked.pet!!.line)
        val coins = s.coins
        val early = Engine.perform(s, Action.ClaimTrip(traveller), t0 + 30 * 60_000, zone).state
        assertNotNull(early.onTrip(traveller))
        val o = Engine.perform(s, Action.ClaimTrip(traveller), t0 + 2 * hour, zone)
        assertTrue(o.state.coins > coins)
        assertEquals(null, o.state.onTrip(traveller))
        assertTrue(o.events.any { it is GameEvent.TripReturned })
        val trip = Trip(traveller, Destination.MOND, 5, 10)
        assertEquals(Trips.loot(trip), Trips.loot(trip))
    }

    @Test
    fun weeklyQuestsAndPass() {
        var s = started()
        assertEquals(3, s.weekly.quests.size)
        assertTrue(s.weekly.quests.all { it.target == it.type.weekly })
        s = s.copy(weekly = s.weekly.copy(quests = listOf(QuestProgress(QuestType.PET, 3))))
        var now = t0
        repeat(3) {
            now += 2_000
            s = Engine.perform(s, Action.Stroke, now, zone).state
        }
        assertTrue(s.weekly.quests[0].done)
        val passXp = s.pass.xp
        s = Engine.perform(s, Action.ClaimWeekly(0), now, zone).state
        assertTrue(s.weekly.quests[0].claimed)
        assertTrue(s.pass.xp > passXp)
        // Pass tier 10 gives an egg
        s = s.copy(pass = s.pass.copy(xp = 10 * SeasonPass.XP_PER_TIER))
        val eggs = s.totalEggs
        s = Engine.perform(s, Action.ClaimPass(10), now, zone).state
        assertEquals(eggs + 1, s.totalEggs)
        val again = Engine.perform(s, Action.ClaimPass(10), now, zone).state
        assertEquals(eggs + 1, again.totalEggs)
        val tooHigh = Engine.perform(s, Action.ClaimPass(11), now, zone).state
        assertFalse(11 in tooHigh.pass.claimed)
    }

    @Test
    fun calendarEvents() {
        assertEquals(java.time.LocalDate.of(2027, 3, 28), EventCalendar.easter(2027))
        assertEquals(java.time.LocalDate.of(2026, 4, 5), EventCalendar.easter(2026))
        assertEquals(SeasonEvent.HALLOWEEN, EventCalendar.active(java.time.LocalDate.of(2026, 10, 31)))
        assertEquals(SeasonEvent.WINTERZAUBER, EventCalendar.active(java.time.LocalDate.of(2026, 12, 24)))
        assertEquals(null, EventCalendar.active(java.time.LocalDate.of(2026, 9, 29)))
        val (next, days) = EventCalendar.next(java.time.LocalDate.of(2026, 9, 29))
        assertEquals(SeasonEvent.HALLOWEEN, next)
        assertEquals(18L, days)
        assertEquals("2026-WINTER", EventCalendar.passId(java.time.LocalDate.of(2027, 2, 10)))
    }

    @Test
    fun eventTokensBuyEventEgg() {
        val halloween = ZonedDateTime.of(2026, 10, 25, 10, 0, 0, 0, zone).toInstant().toEpochMilli()
        var s = Engine.perform(GameState(), Action.Start(Difficulty.RELAXED, "Mochi"), halloween, zone).state
        assertEquals("HALLOWEEN-2026", s.event.key)
        assertTrue(s.event.tokens > 0)
        s = s.copy(event = s.event.copy(tokens = 100))
        s = Engine.perform(s, Action.BuyEventOffer("egg_grusel"), halloween, zone).state
        assertEquals(1, s.eggCount(EggLine.GRUSEL))
        assertEquals(60, s.event.tokens)
        assertEquals(1, s.counters.eventsJoined)
    }

    @Test
    fun stickerPackAndMigration() {
        var s = started().copy(inventory = mapOf(Catalog.STICKER_PACK to 1))
        s = Engine.perform(s, Action.UseItem(Catalog.STICKER_PACK), t0, zone).state
        assertEquals(3, s.stickers.values.sum())
        // Old saves without album/furniture get migrated.
        val old = s.copy(album = emptySet(), owned = setOf("room_cozy"), equipped = mapOf(Slot.ROOM to "room_cozy"))
        val migrated = Engine.tick(old, t0, zone).state
        assertTrue(Form.BABY.name in migrated.album)
        assertTrue("rug_round" in migrated.owned)
        assertEquals("rug_round", migrated.equipped[Slot.RUG])
    }

    @Test
    fun secondEggAppearsAtLevelFour() {
        var s = started()
        var now = t0
        var found = false
        repeat(8) {
            now += 10 * 60_000L
            s = s.copy(pet = s.pet!!.copy(energy = 100.0, satiety = 90.0, joy = 90.0, hygiene = 90.0))
            val o = Engine.perform(s, Action.GameFinished(MiniGame.RUNNER, 900), now, zone)
            s = o.state
            if (o.events.any { it is GameEvent.EggFound }) found = true
        }
        assertTrue(found)
        assertEquals(1, s.totalEggs)
    }

    private fun withLevels(s: GameState, care: Int, sleep: Int) =
        s.copy(settings = s.settings.copy(careLevel = care, sleepLevel = sleep))

    @Test
    fun careLevelChangesHowFastNeedsGrow() {
        val base = started()
        val calm = Engine.tick(withLevels(base, 0, CareTuning.NORMAL), t0 + 4 * hour, zone).state.pet!!
        val normal = Engine.tick(withLevels(base, CareTuning.NORMAL, CareTuning.NORMAL), t0 + 4 * hour, zone).state.pet!!
        val busy = Engine.tick(withLevels(base, CareTuning.MAX, CareTuning.NORMAL), t0 + 4 * hour, zone).state.pet!!
        assertTrue(calm.satiety > normal.satiety && normal.satiety > busy.satiety)
        assertTrue(calm.joy > normal.joy && normal.joy > busy.joy)
        assertTrue(calm.hygiene > normal.hygiene && normal.hygiene > busy.hygiene)
        // Energy only depends on the sleep setting.
        assertEquals(normal.energy, busy.energy, 0.001)
    }

    @Test
    fun sleepLevelChangesNapLengthAndTiredness() {
        val base = started()
        val tired = base.copy(pet = base.pet!!.copy(energy = 17.0))
        fun hoursAsleep(level: Int): Int {
            var s = Engine.tick(withLevels(tired, CareTuning.NORMAL, level), t0 + 60_000L, zone).state
            assertTrue(s.pet!!.sleeping)
            var h = 0
            while (s.pet!!.sleeping && h < 24) {
                h++
                s = Engine.tick(s, t0 + h * hour, zone).state
            }
            return h
        }
        assertTrue(hoursAsleep(0) < hoursAsleep(CareTuning.NORMAL))
        assertTrue(hoursAsleep(CareTuning.NORMAL) < hoursAsleep(CareTuning.MAX))
        assertTrue(CareTuning.napHours(0) < 2.5 && CareTuning.napHours(CareTuning.NORMAL) > 4.0)

        val awakeLow = Engine.tick(withLevels(base, CareTuning.NORMAL, 0), t0 + 4 * hour, zone).state.pet!!
        val awakeNormal = Engine.tick(withLevels(base, CareTuning.NORMAL, CareTuning.NORMAL), t0 + 4 * hour, zone).state.pet!!
        assertTrue(awakeLow.energy > awakeNormal.energy)
    }

    @Test
    fun playingCostsLessEnergyWithLittleSleep() {
        val base = started()
        val low = Engine.perform(withLevels(base, CareTuning.NORMAL, 0), Action.Play, t0, zone).state.pet!!
        val normal = Engine.perform(withLevels(base, CareTuning.NORMAL, CareTuning.NORMAL), Action.Play, t0, zone).state.pet!!
        assertTrue(low.energy > normal.energy)
    }
}
