package de.knuffi.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlin.random.Random

class QuizTest {
    private val zone: ZoneId = ZoneId.of("Europe/Berlin")
    private val t0: Long = ZonedDateTime.of(2026, 3, 10, 16, 0, 0, 0, zone).toInstant().toEpochMilli()

    @Test
    fun everyQuestionIsWellFormed() {
        val all = Quiz.all
        assertTrue("only ${all.size} questions", all.size >= 550)
        assertEquals("duplicate ids", all.size, all.map { it.id }.toSet().size)
        assertEquals("duplicate questions", all.size, all.map { it.question.lowercase() }.toSet().size)
        for (q in all) {
            val where = "${q.category}: ${q.question}"
            assertTrue(where, q.level in 1..3)
            assertTrue(where, q.question.isNotBlank() && q.answer.isNotBlank() && q.info.length > 20)
            assertTrue(where, q.wrong.all { it.isNotBlank() })
            assertFalse("$where answer among wrong", q.answer in q.wrong)
            assertEquals("$where wrong answers repeat", 3, q.wrong.toSet().size)
            assertTrue("$where info should end with a sentence", q.info.last() in ".!?\"“)")
        }
    }

    @Test
    fun everyTopicHasManyQuestionsOnEveryLevel() {
        for (c in QuizCategory.entries) {
            val qs = Quiz.all.filter { it.category == c }
            assertTrue("$c has ${qs.size}", qs.size >= 35)
            for (l in 1..3) assertTrue("$c level $l", qs.count { it.level == l } >= 6)
        }
    }

    @Test
    fun roundsPreferNewQuestions() {
        val first = Quiz.round(null, QuizLevel.GEMISCHT, emptySet(), Random(1))
        assertEquals(Quiz.ROUND, first.size)
        assertEquals(Quiz.ROUND, first.map { it.id }.toSet().size)
        val pool = Quiz.pool(QuizCategory.TIERE, QuizLevel.LEICHT)
        val seen = pool.drop(3).map { it.id }.toSet()
        val next = Quiz.round(QuizCategory.TIERE, QuizLevel.LEICHT, seen, Random(2))
        // the three new ones come first
        assertTrue(next.take(3).all { it.id !in seen })
        assertTrue(next.all { it.category == QuizCategory.TIERE && it.level == 1 })
    }

    @Test
    fun aRoundGivesCoinsAndCountsTheBest() {
        var s = Engine.perform(GameState(), Action.Start(Difficulty.RELAXED, "Mochi"), t0, zone).state
        repeat(Engine.HATCH_TAPS) { s = Engine.perform(s, Action.HatchTap, t0, zone).state }
        val coins = s.coins
        val q = Quiz.all.first()
        s = Engine.perform(s, Action.QuizAnswered(q.id, true), t0, zone).state
        assertTrue(q.id in s.quiz.seen)
        assertEquals(1, s.quiz.right)
        s = Engine.perform(s, Action.QuizRoundDone(QuizCategory.TIERE, 10, 10), t0, zone).state
        assertTrue(s.coins >= coins + Quiz.coins(10, 10))
        assertEquals(10, s.quiz.best["TIERE"])
        assertEquals(1, s.quiz.perfect)
        s = Engine.perform(s, Action.QuizRoundDone(QuizCategory.TIERE, 6, 10), t0, zone).state
        assertEquals(10, s.quiz.best["TIERE"])
        assertEquals(2, s.quiz.rounds)
    }
}
