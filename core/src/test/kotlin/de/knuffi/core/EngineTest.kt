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
        var s = Engine.perform(GameState(), Action.Start(VisualStyle.KAWAII, difficulty, "Mochi"), t0, zone).state
        repeat(Engine.HATCH_TAPS) { s = Engine.perform(s, Action.HatchTap, t0, zone).state }
        return s
    }

    @Test
    fun startCreatesEggAndHatches() {
        val egg = Engine.perform(GameState(), Action.Start(VisualStyle.PIXEL, Difficulty.CLASSIC, "  Bobo  "), t0, zone)
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
    fun serializationRoundTrip() {
        val s = started().copy(equipped = mapOf(Slot.HAT to "hat_bow", Slot.ROOM to "room_cozy"), owned = setOf("hat_bow"))
        val json = GameJson.encode(s)
        val back = GameJson.decode(json)
        assertNotNull(back)
        assertEquals(s, back)
    }
}
