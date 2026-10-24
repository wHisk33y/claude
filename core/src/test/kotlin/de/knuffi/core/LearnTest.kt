package de.knuffi.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneId
import java.time.ZonedDateTime

class LearnTest {
    private val zone: ZoneId = ZoneId.of("Europe/Berlin")
    private val t0: Long = ZonedDateTime.of(2026, 3, 10, 16, 0, 0, 0, zone).toInstant().toEpochMilli()
    private val day = 86_400_000L
    private val today = TimeUtil.epochDay(t0, zone)

    private fun started(): GameState {
        var s = Engine.perform(GameState(), Action.Start(Difficulty.RELAXED, "Mochi"), t0, zone).state
        repeat(Engine.HATCH_TAPS) { s = Engine.perform(s, Action.HatchTap, t0, zone).state }
        return s
    }

    @Test
    fun everyTaskIsSolvable() {
        for (subject in Subject.entries) for (grade in Learn.MIN_GRADE..Learn.MAX_GRADE) for (d in 0L until 60L) for (i in 0 until 10) {
            val t = Learn.task(subject, grade, today + d, i)
            val where = "$subject Klasse $grade Tag $d #$i: ${t.question}"
            assertTrue(where, t.question.isNotBlank() && t.hint.isNotBlank() && t.answer.isNotBlank() && t.topic.isNotBlank())
            assertTrue("$where -> ${t.answer}", t.check(t.answer))
            if (t.typed) {
                assertNotNull("$where -> ${t.answer}", Learn.number(t.answer))
            } else {
                assertTrue(where, t.choices.size >= 2)
                assertTrue(where, t.answer in t.choices)
                assertEquals(where, t.choices.size, t.choices.toSet().size)
                for (c in t.choices) if (c != t.answer) assertFalse("$where wrong $c", t.check(c))
            }
        }
    }

    @Test
    fun mathIsTypedTheOthersAreChosen() {
        for (grade in Learn.MIN_GRADE..Learn.MAX_GRADE) {
            assertTrue(Learn.task(Subject.MATHE, grade, today, 0).typed)
            assertFalse(Learn.task(Subject.DEUTSCH, grade, today, 0).typed)
            assertFalse(Learn.task(Subject.PHYSIK, grade, today, 0).typed)
        }
    }

    @Test
    fun noTaskTwiceOnADay() {
        for (subject in Subject.entries) for (grade in Learn.MIN_GRADE..Learn.MAX_GRADE) {
            val qs = (0 until 3).map { Learn.task(subject, grade, today, it).question }
            assertEquals("$subject $grade $qs", qs.size, qs.toSet().size)
        }
    }

    @Test
    fun checkReadsGermanNumbers() {
        val t = LearnTask(Subject.MATHE, "x", "?", "2,5", hint = "h")
        assertTrue(t.check("2,5"))
        assertTrue(t.check("2.5"))
        assertTrue(t.check(" 2,50 "))
        assertTrue(t.check("5/2"))
        assertFalse(t.check("2,6"))
        assertFalse(t.check("abc"))
        assertFalse(t.check(""))
        val f = LearnTask(Subject.MATHE, "x", "?", "3/4", hint = "h")
        assertTrue(f.check("6/8"))
        assertTrue(f.check("0,75"))
        assertFalse(f.check("0,7"))
        val reduced = LearnTask(Subject.MATHE, "x", "?", "3/4", hint = "h", reduced = true)
        assertTrue(reduced.check("3/4"))
        assertFalse(reduced.check("6/8"))
        val neg = LearnTask(Subject.MATHE, "x", "?", "-7", hint = "h")
        assertTrue(neg.check("-7"))
        assertTrue(neg.check("−7"))
        assertFalse(neg.check("7"))
    }

    @Test
    fun gradesFollowTheSlider() {
        val s = Settings(learnGrades = mapOf("MATHE" to 9))
        assertEquals(9, Learn.grade(s, LearnState(), Subject.MATHE))
        assertEquals(Learn.MIN_GRADE, Learn.grade(s, LearnState(), Subject.PHYSIK))
        assertEquals(10, Learn.grade(s, LearnState(adjust = mapOf("MATHE" to 1)), Subject.MATHE))
        assertEquals(Learn.MAX_GRADE, Learn.grade(Settings(learnGrades = mapOf("MATHE" to 13)), LearnState(adjust = mapOf("MATHE" to 1)), Subject.MATHE))
    }

    private fun solveAll(start: GameState, now: Long): Pair<GameState, List<GameEvent>> {
        var s = start
        val events = mutableListOf<GameEvent>()
        for (subject in Subject.entries) repeat(s.settings.learnPerSubject) {
            val o = Engine.perform(s, Action.LearnAnswer(subject, true, "Test"), now, zone)
            s = o.state
            events += o.events
        }
        return s to events
    }

    @Test
    fun answersGiveCoinsAndADoneDay() {
        var s = started()
        val coins = s.coins
        s = Engine.perform(s, Action.LearnAnswer(Subject.MATHE, false, "Brüche"), t0, zone).state
        assertEquals(coins, s.coins)
        assertEquals(1, s.learn.tries["MATHE"])
        s = Engine.perform(s, Action.LearnAnswer(Subject.MATHE, true, "Brüche"), t0, zone).state
        assertEquals(coins + Learn.COINS_LATER, s.coins)
        assertEquals(1, Learn.solved(s.learn, Subject.MATHE, today))
        assertEquals(TopicStat(tasks = 1, firstTry = 0, wrong = 1, lastDay = today), s.learn.stats["MATHE:Brüche"])
        val (done, events) = solveAll(s, t0)
        assertTrue(Learn.allDone(done.settings, done.learn, today))
        assertEquals(1, done.learn.doneDays)
        assertEquals(1, done.learn.streak)
        assertTrue(events.any { it is GameEvent.LearnSolved && it.dayDone })
        // nothing more to earn today
        val after = Engine.perform(done, Action.LearnAnswer(Subject.PHYSIK, true, "Test"), t0, zone).state
        assertEquals(done.coins, after.coins)
    }

    @Test
    fun streakPrizesAndMissedDays() {
        var s = started()
        for (d in 0 until 7) s = solveAll(s, t0 + d * day).first
        assertEquals(7, s.learn.streak)
        assertTrue("hat_mortarboard" in s.owned)
        assertTrue("skin_bookworm" in s.skins)
        assertTrue("skin_bookworm" in s.learn.prizes)
        // one day without learning: the streak breaks and the pet is a little sad
        val later = t0 + 8 * day
        val o = Engine.tick(s, later, zone, userPresent = true)
        s = o.state
        assertEquals(0, s.learn.streak)
        assertTrue(s.learn.missed)
        assertTrue(s.learn.missedTold)
        assertTrue(o.events.any { it is GameEvent.Message && it.text.contains("traurig") })
        assertEquals(7, s.learn.doneDays)
        s = solveAll(s, later).first
        assertEquals(1, s.learn.streak)
        assertFalse(s.learn.missed)
    }

    @Test
    fun switchedOffMeansNothingHappens() {
        var s = started()
        s = solveAll(s, t0).first
        s = s.copy(settings = s.settings.copy(learnEnabled = false))
        val o = Engine.tick(s, t0 + 3 * day, zone, userPresent = true)
        assertFalse(o.state.learn.missed)
        assertTrue(o.events.none { it is GameEvent.Message && it.text.contains("traurig") })
        val coins = o.state.coins
        val after = Engine.perform(o.state, Action.LearnAnswer(Subject.MATHE, true, "Test"), t0 + 3 * day, zone).state
        assertEquals(coins, after.coins)
    }

    @Test
    fun difficultyAdapts() {
        var s = started()
        s = s.copy(settings = s.settings.copy(learnPerSubject = 10, learnGrades = mapOf("MATHE" to 6)))
        repeat(9) { s = Engine.perform(s, Action.LearnAnswer(Subject.MATHE, true, "Test"), t0, zone).state }
        assertEquals(7, Learn.grade(s.settings, s.learn, Subject.MATHE))
        // a new slider value starts without the adjustment
        s = Engine.perform(s, Action.UpdateSettings(s.settings.copy(learnGrades = mapOf("MATHE" to 8))), t0, zone).state
        assertEquals(8, Learn.grade(s.settings, s.learn, Subject.MATHE))
    }

    @Test
    fun learnPrizesExist() {
        for (p in Learn.prizes) {
            if (p.skin) assertNotNull(p.id, Skins[p.id]) else assertNotNull(p.id, Catalog[p.id])
        }
        assertEquals(3, Skins.learnSkins.size)
    }
}
