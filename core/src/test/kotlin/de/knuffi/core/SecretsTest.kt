package de.knuffi.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

class SecretsTest {
    private val zone: ZoneId = ZoneId.of("Europe/Berlin")
    private fun at(h: Int, day: Int = 10, min: Int = 0): Long = ZonedDateTime.of(2026, 3, day, h, min, 0, 0, zone).toInstant().toEpochMilli()

    private fun started(t: Long = at(9)): GameState {
        var s = Engine.perform(GameState(), Action.Start(Difficulty.RELAXED, "Mochi"), t, zone).state
        repeat(Engine.HATCH_TAPS) { s = Engine.perform(s, Action.HatchTap, t, zone).state }
        return s.copy(pet = s.pet!!.copy(level = 8))
    }

    private fun questDone(s: GameState, type: QuestType): Boolean =
        (s.daily.quests + s.weekly.quests).filter { it.type == type }.all { it.done }

    @Test
    fun chatQuestCountsMessages() {
        var s = started()
        s = s.copy(daily = s.daily.copy(quests = listOf(QuestProgress(QuestType.CHAT, 5))))
        repeat(5) { i -> s = Engine.perform(s, Action.Chat("Hallo $i"), at(9) + i * 1000L, zone).state }
        assertTrue(questDone(s, QuestType.CHAT))
    }

    @Test
    fun workQuestCountsShifts() {
        var s = started()
        s = s.copy(daily = s.daily.copy(quests = listOf(QuestProgress(QuestType.WORK, 1))))
        s = Engine.perform(s, Action.ChooseJob(Job.KOCH), at(9), zone).state
        s = Engine.perform(s, Action.Work, at(9) + 1000, zone).state
        assertTrue(questDone(s, QuestType.WORK))
    }

    @Test
    fun magicWordInChatIsASecret() {
        var s = started()
        s = Engine.perform(s, Action.Chat("Abrakadabra!"), at(9), zone).state
        assertTrue(Secret.MAGIC_WORD.name in s.secrets)
        val coins = s.coins
        s = Engine.perform(s, Action.Chat("abrakadabra"), at(9) + 5000, zone).state
        assertEquals("only once", 1, s.secrets.count { it == Secret.MAGIC_WORD.name })
        assertTrue(s.coins < coins + Secret.MAGIC_WORD.coins)
    }

    @Test
    fun midnightIsASecret() {
        val s = Engine.tick(started(at(23)), at(0, 11, 5), zone, userPresent = true).state
        assertTrue(Secret.MIDNIGHT.name in s.secrets)
    }

    @Test
    fun fullMoonCalculation() {
        // 2026-03-03 is a full moon, 2026-03-19 a new moon
        assertTrue(Secrets.fullMoon(LocalDate.of(2026, 3, 3)))
        assertFalse(Secrets.fullMoon(LocalDate.of(2026, 3, 19)))
    }

    @Test
    fun hiddenObjectOncePerDay() {
        var s = started()
        val coins = s.coins
        s = Engine.perform(s, Action.FindHidden, at(9), zone).state
        assertTrue(s.daily.hiddenFound)
        assertTrue(s.coins >= coins + Hidden.COINS)
        val c2 = s.coins
        s = Engine.perform(s, Action.FindHidden, at(10), zone).state
        assertEquals(c2, s.coins)
        s = Engine.tick(s, at(9, 11), zone, userPresent = true).state
        assertFalse(s.daily.hiddenFound)
    }

    @Test
    fun diaryIsWrittenForYesterday() {
        var s = started()
        s = Engine.perform(s, Action.Feed("rice"), at(9), zone).state
        s = Engine.perform(s, Action.GameFinished(MiniGame.CATCH, 200), at(10), zone).state
        s = Engine.tick(s, at(9, 11), zone, userPresent = true).state
        assertEquals(1, s.diary.size)
        assertTrue(s.diary.first().text.contains("Mochi"))
    }

    @Test
    fun friendMomentsNeedAVisit() {
        var s = started()
        val friend = Pet(name = "Bubu", form = Form.FLOSSI, bornAt = at(1, 1), hatchedAt = at(1, 1), line = EggLine.MEER)
        s = s.copy(resting = listOf(friend))
        s = Engine.perform(s, Action.FriendPlay(FriendMoment.BALL), at(9), zone).state
        assertTrue(s.friendship.isEmpty())
        s = Engine.perform(s, Action.Invite(friend.id), at(9), zone).state
        val before = s.friendship.values.sum()
        s = Engine.perform(s, Action.FriendPlay(FriendMoment.BALL), at(9) + 1000, zone).state
        assertEquals(before + 2, s.friendship.values.sum())
    }

    @Test
    fun pacmanHasRewards() {
        val r = Engine.gameReward(MiniGame.PACMAN, 300)
        assertTrue(r.coins in 2..60)
    }

    @Test
    fun heartNamesAreFound() {
        for (t in listOf("Toni", "hallo toni!", "Ich mag Susi.", "ARIK", "Tony und Susie", "arik?")) assertTrue(t, HeartNames.mentioned(t))
        for (t in listOf("Antonia", "Sitz!", "Tonne", "Susanne", "Erik", "")) assertFalse(t, HeartNames.mentioned(t))
    }
}
