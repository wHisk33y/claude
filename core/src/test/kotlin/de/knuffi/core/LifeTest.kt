package de.knuffi.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneId
import java.time.ZonedDateTime

class LifeTest {
    private val zone: ZoneId = ZoneId.of("Europe/Berlin")
    private val t0: Long = ZonedDateTime.of(2026, 3, 10, 16, 0, 0, 0, zone).toInstant().toEpochMilli()

    private fun started(): GameState {
        var s = Engine.perform(GameState(), Action.Start(Difficulty.RELAXED, "Mochi"), t0, zone).state
        repeat(Engine.HATCH_TAPS) { s = Engine.perform(s, Action.HatchTap, t0, zone).state }
        return s
    }

    /** XP without depending on energy or mood (a quiz round). */
    private fun gainXp(s: GameState, at: Long = t0): Outcome = Engine.perform(s, Action.QuizRoundDone(null, 10, 10), at, zone)

    @Test
    fun aNewStageEveryTenLevels() {
        assertEquals(Stage.BABY, Leveling.stageFor(1))
        assertEquals(Stage.ADULT, Leveling.stageFor(19))
        assertEquals(Stage.PRAECHTIG, Leveling.stageFor(20))
        assertEquals(Stage.ERFAHREN, Leveling.stageFor(39))
        assertEquals(Stage.WEISE, Leveling.stageFor(40))
        assertEquals(Stage.ZEITLOS, Leveling.stageFor(80))
        assertEquals(80, Leveling.MAX_LEVEL)
    }

    @Test
    fun anOldAdultCatchesUpWithItsForms() {
        var s = started()
        // like the pet at level 39 that had not changed for a long time
        s = s.copy(pet = s.pet!!.copy(form = Form.DRAKO, level = 39, care = CareLog(activity = 20, food = 2)))
        s = gainXp(s).state
        val p = s.pet!!
        assertEquals(Stage.ERFAHREN, p.form.stage)
        assertEquals(EggLine.KNUFFEL, p.form.line)
        assertTrue(Form.creatures.count { it.line == EggLine.KNUFFEL && it.stage.ordinal > Stage.ADULT.ordinal } == 12)
    }

    @Test
    fun theCareDecidesThePath() {
        val pet = Pet(name = "x", bornAt = 1L, form = Form.MOCHI_KOENIG, level = 20)
        val cozy = Leveling.nextForm(pet.copy(care = CareLog(food = 30, activity = 2)))
        val active = Leveling.nextForm(pet.copy(care = CareLog(food = 2, activity = 30)))
        assertEquals(Role.S20_B, cozy.role)
        assertEquals(Role.S20_A, active.role)
        // without a clear sign the old path goes on
        assertEquals(Role.S20_B, Leveling.nextForm(pet).role)
        val older = Leveling.nextForm(Pet(name = "x", bornAt = 1L, form = Form.PLUESCHKNUFFEL, level = 30))
        assertEquals(Role.S30_B, older.role)
    }

    @Test
    fun everyPetHasItsOwnLifeSpan() {
        val spans = (1L..400L).map { Lifespan.level(Pet(name = "x", bornAt = it * 1000L + 7)) }
        assertTrue(spans.all { it in Lifespan.MIN_LEVEL..Lifespan.MAX_LEVEL })
        assertTrue(spans.toSet().size > 10)
        val young = Pet(name = "x", bornAt = 5, form = Form.DRAKO, level = 40)
        assertEquals(0f, Lifespan.age(young))
        assertTrue(Lifespan.age(young.copy(level = 58)) > 0f)
    }

    @Test
    fun theOldPetSaysGoodbyeAndBecomesAStar() {
        var s = started()
        val first = s.pet!!
        val span = Lifespan.level(first)
        s = s.copy(pet = first.copy(form = Form.STERNENKNUFFEL, level = span - 1, xp = Leveling.xpToNext(span - 1) - 1))
        // a second pet waits in the Kuschelhaus
        val friend = Pet(name = "Bobo", bornAt = t0 + 5, form = Form.HOPSI, level = 5)
        s = s.copy(resting = listOf(friend))
        val o = gainXp(s)
        s = o.state
        assertTrue(s.pet!!.farewell)
        assertEquals(span, s.pet!!.level)
        assertTrue(o.events.any { it is GameEvent.FarewellBegins })
        assertTrue(s.diary.last().text.contains("Stern"))
        // nothing gets worse while saying goodbye
        val later = Engine.tick(s, t0 + 3 * 86_400_000L, zone).state
        assertEquals(s.pet!!.satiety, later.pet!!.satiety, 0.001)
        // no more levels
        assertEquals(span, gainXp(later, t0 + 3 * 86_400_000L).state.pet!!.level)
        val gone = Engine.perform(later, Action.SayGoodbye, t0 + 3 * 86_400_000L, zone)
        s = gone.state
        assertFalse(s.pet!!.alive)
        assertTrue(s.memorials.last().oldAge)
        assertTrue(gone.events.any { it is GameEvent.Ascended })
        // the friend from the Kuschelhaus takes over
        s = Engine.perform(s, Action.SwitchPet(friend.id), t0 + 3 * 86_400_000L + 1000, zone).state
        assertEquals("Bobo", s.pet!!.name)
        assertTrue(s.pet!!.alive)
        assertTrue(s.resting.isEmpty())
    }

    @Test
    fun spareEggsCanBeGivenAway() {
        var s = started()
        s = s.copy(eggs = mapOf(EggLine.WALD to 2))
        val coins = s.coins
        s = Engine.perform(s, Action.GiveAwayEgg(EggLine.WALD), t0, zone).state
        assertEquals(1, s.eggCount(EggLine.WALD))
        assertEquals(coins + Eggs.giveAwayCoins(EggLine.WALD), s.coins)
        s = Engine.perform(s, Action.GiveAwayEgg(EggLine.WALD), t0, zone).state
        assertEquals(0, s.totalEggs)
        val egg = Family.make(Pet(name = "a", bornAt = 1, form = Form.LUMI), Pet(name = "b", bornAt = 2, form = Form.LUMI), t0, kotlin.random.Random(1))
        s = s.copy(familyEggs = listOf(egg))
        s = Engine.perform(s, Action.GiveAwayFamilyEgg(egg.id), t0, zone).state
        assertTrue(s.familyEggs.isEmpty())
    }

    @Test
    fun everyNewFormHasAPlaceInItsLine() {
        for (f in Form.entries.filter { it.stage.ordinal > Stage.ADULT.ordinal }) {
            assertNotNull(f.name, Role.elder(f.stage, f.role.name.endsWith("_B")))
            assertEquals(f.name, f, Form.find(f.line, f.role))
        }
    }
}
