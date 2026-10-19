package de.knuffi.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneId
import java.time.ZonedDateTime

class WorldTest {
    private val zone: ZoneId = ZoneId.of("Europe/Berlin")
    private fun at(h: Int, day: Int = 10): Long = ZonedDateTime.of(2026, 3, day, h, 0, 0, 0, zone).toInstant().toEpochMilli()
    private val hour = 3_600_000L

    private fun started(t: Long = at(9)): GameState {
        var s = Engine.perform(GameState(), Action.Start(Difficulty.RELAXED, "Mochi"), t, zone).state
        repeat(Engine.HATCH_TAPS) { s = Engine.perform(s, Action.HatchTap, t, zone).state }
        return s
    }

    @Test
    fun cookingNeedsIngredientsAndMakesDish() {
        var s = started()
        s = Engine.perform(s, Action.Cook("r_soup"), at(9), zone).state
        assertEquals(0, s.count("dish_soup"))
        s = s.copy(inventory = s.inventory + ("carrot" to 1) + ("pumpkin" to 2))
        s = Engine.perform(s, Action.Cook("r_soup"), at(9), zone).state
        assertEquals(1, s.count("dish_soup"))
        assertEquals(0, s.count("carrot"))
        assertEquals(1, s.count("pumpkin"))
        assertEquals(1, s.counters.cooked)
        assertTrue(s.isUnlocked(Achievement.COOK_1))
    }

    @Test
    fun cookingGameGivesDishes() {
        var s = started()
        s = Engine.perform(s, Action.GameFinished(MiniGame.COOK, 9), at(9), zone).state
        assertTrue(s.count("dish_feast") >= 1)
        assertEquals(9, s.counters.bests["COOK"])
    }

    @Test
    fun lowerIsBetterRecords() {
        var s = started()
        s = Engine.perform(s, Action.GameFinished(MiniGame.PUZZLE, 60), at(9), zone).state
        s = Engine.perform(s, Action.GameFinished(MiniGame.PUZZLE, 80), at(9) + 1000, zone).state
        assertEquals(60, s.counters.bests["PUZZLE"])
        assertTrue(Engine.isRecord(s, MiniGame.PUZZLE, 40))
        assertFalse(Engine.isRecord(s, MiniGame.PUZZLE, 70))
    }

    @Test
    fun everyGameHasRewards() {
        for (g in MiniGame.entries) {
            val r = Engine.gameReward(g, if (g.lowerIsBetter) 30 else 10)
            assertTrue(g.name, r.coins in 1..Engine.gameMaxCoins(g))
        }
    }

    @Test
    fun routineCompletesOverTheDay() {
        var s = started(at(7))
        s = Engine.perform(s, Action.Feed("rice"), at(7), zone).state
        assertTrue(Routine.BREAKFAST in s.daily.routine)
        s = Engine.perform(s, Action.Play, at(12), zone).state
        s = s.copy(pet = s.pet!!.copy(satiety = 30.0))
        s = Engine.perform(s, Action.Feed("rice"), at(18), zone).state
        s = s.copy(pet = s.pet!!.copy(energy = 50.0, sleeping = false))
        val coins = s.coins
        s = Engine.perform(s, Action.ToggleSleep, at(21), zone).state
        assertTrue(s.daily.routineClaimed)
        assertTrue(s.coins >= coins + Routines.BONUS_COINS)
        assertEquals(1, s.counters.routines)
    }

    @Test
    fun storyAdvancesOncePerDayAndFinishes() {
        var s = started().copy(counters = Counters(maxLevel = 4))
        val story = Stories.current(s)!!
        assertEquals("treasure", story.id)
        s = Engine.perform(s, Action.StoryChoose(0), at(9), zone).state
        assertEquals(1, s.story.progress["treasure"])
        s = Engine.perform(s, Action.StoryChoose(0), at(10), zone).state
        assertEquals(1, s.story.progress["treasure"])
        for (d in 1 until story.chapters.size) s = Engine.perform(s, Action.StoryChoose(1), at(9, 10 + d), zone).state
        assertTrue("treasure" in s.story.finished)
        assertTrue("back_treasure" in s.owned)
        assertTrue(s.finds.isNotEmpty())
    }

    @Test
    fun visitsBuildFriendship() {
        var s = started()
        val friend = Pet(name = "Bubu", form = Form.FLOSSI, bornAt = at(1, 1), hatchedAt = at(1, 1), line = EggLine.MEER)
        s = s.copy(resting = listOf(friend))
        s = Engine.perform(s, Action.Invite(friend.id), at(9), zone).state
        assertNotNull(s.visit)
        s = Engine.perform(s, Action.PlayTogether, at(9) + 1000, zone).state
        val key = Friends.key(s.pet!!.id, friend.id)
        assertTrue((s.friendship[key] ?: 0) >= 9)
        s = Engine.tick(s, at(9) + 2 * hour, zone).state
        assertNull(s.visit)
        assertTrue((s.friendship[key] ?: 0) >= 14)
    }

    @Test
    fun tripsBringFinds() {
        var s = started()
        val friend = Pet(name = "Bubu", form = Form.FLOSSI, bornAt = at(1, 1), hatchedAt = at(1, 1), line = EggLine.MEER)
        s = s.copy(resting = listOf(friend))
        s = Engine.perform(s, Action.StartTrip(friend.id, Destination.WIESE), at(9), zone).state
        s = Engine.perform(s, Action.ClaimTrip(friend.id), at(11), zone).state
        assertEquals(1, s.finds.values.sum())
    }

    @Test
    fun simulatedWeatherIsStableAndSeasonal() {
        val a = Weather.simulated(at(9), zone)
        val b = Weather.simulated(at(9) + 60_000, zone)
        assertEquals(a, b.copy(fetchedAt = a.fetchedAt))
        val winter = ZonedDateTime.of(2026, 1, 15, 12, 0, 0, 0, zone).toInstant().toEpochMilli()
        assertTrue(Weather.simulated(winter, zone).tempC < 12)
        assertEquals(WeatherKind.RAIN, Weather.fromWmo(61))
        assertEquals(WeatherKind.SNOW, Weather.fromWmo(73))
    }

    @Test
    fun realWeatherIsUsedWhenFresh() {
        var s = started()
        s = s.copy(settings = s.settings.copy(weatherMode = WeatherMode.REAL))
        s = Engine.perform(s, Action.SetWeather(WeatherState(WeatherKind.STORM, 9, at(9), "Berlin")), at(9), zone).state
        assertEquals(WeatherKind.STORM, Weather.current(s, at(10), zone).kind)
        // Too old: falls back to simulated weather
        assertEquals(Weather.simulated(at(20), zone).kind, Weather.current(s, at(20), zone).kind)
    }

    @Test
    fun levelRewardsAreGiven() {
        var s = started().copy(pet = started().pet!!.copy(level = 14, xp = 0))
        s = Engine.perform(s, Action.GameFinished(MiniGame.CATCH, 600), at(9), zone).state
        repeat(20) { i -> s = Engine.perform(s, Action.GameFinished(MiniGame.CATCH, 600), at(9) + i * 1000L, zone).state }
        assertTrue(s.pet!!.level >= 15)
        assertTrue("neck_goldchain" in s.owned)
    }

    @Test
    fun backAndToySlotsCanBeWornAndRemoved() {
        var s = started().copy(owned = started().owned + "back_backpack" + "toy_box")
        s = Engine.perform(s, Action.Equip("back_backpack"), at(9), zone).state
        s = Engine.perform(s, Action.Equip("toy_box"), at(9), zone).state
        assertEquals("back_backpack", s.equipped[Slot.BACK])
        s = Engine.perform(s, Action.Unequip(Slot.BACK), at(9), zone).state
        s = Engine.perform(s, Action.Unequip(Slot.TOY), at(9), zone).state
        assertNull(s.equipped[Slot.BACK])
        assertNull(s.equipped[Slot.TOY])
    }
}
