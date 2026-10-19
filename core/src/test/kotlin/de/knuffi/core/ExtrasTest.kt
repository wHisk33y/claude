package de.knuffi.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneId
import java.time.ZonedDateTime

class ExtrasTest {
    private val zone: ZoneId = ZoneId.of("Europe/Berlin")
    private val t0: Long = ZonedDateTime.of(2026, 3, 10, 9, 0, 0, 0, zone).toInstant().toEpochMilli()
    private val hour = 3_600_000L

    private fun started(): GameState {
        var s = Engine.perform(GameState(), Action.Start(Difficulty.RELAXED, "Mochi"), t0, zone).state
        repeat(Engine.HATCH_TAPS) { s = Engine.perform(s, Action.HatchTap, t0, zone).state }
        return s
    }

    private fun withLevel(s: GameState, level: Int): GameState = s.copy(pet = s.pet!!.copy(level = level))

    @Test
    fun catModeStartsWithKittenAndFur() {
        val s = Engine.perform(GameState(), Action.Start(Difficulty.RELAXED, "Minka", kind = PetKind.CAT, fur = "fur_calico"), t0, zone).state
        val p = s.pet!!
        assertTrue(s.catMode)
        assertEquals(Form.KAETZCHEN, p.form)
        assertEquals("fur_calico", p.fur)
        assertTrue(p.isCat)
        assertEquals(1, s.counters.hatched)
        assertTrue(s.discovered(Form.KAETZCHEN))
    }

    @Test
    fun catModeTurnsEggsIntoKittenBaskets() {
        var s = Engine.perform(GameState(), Action.Start(Difficulty.RELAXED, "Minka", kind = PetKind.CAT), t0, zone).state
        s = s.copy(coins = 1000, pet = s.pet!!.copy(level = 6))
        s = Engine.perform(s, Action.Buy("egg_wald"), t0, zone).state
        assertEquals(0, s.eggCount(EggLine.WALD))
        assertEquals(1, s.eggCount(EggLine.KATZE))
        s = Engine.perform(s, Action.HatchEgg(EggLine.KATZE, "Tiger"), t0 + 1000, zone).state
        assertEquals(Form.KAETZCHEN, s.pet!!.form)
        assertNotNull(s.pet!!.fur)
        assertEquals("Minka", s.resting.single().name)
    }

    @Test
    fun catsEvolveIntoCats() {
        var s = Engine.perform(GameState(), Action.Start(Difficulty.RELAXED, "Minka", kind = PetKind.CAT), t0, zone).state
        s = Engine.perform(s, Action.GameFinished(MiniGame.CATCH, 600), t0 + 1000, zone).state
        repeat(40) { i -> s = Engine.perform(s, Action.GameFinished(MiniGame.CATCH, 600), t0 + 2000 + i, zone).state }
        assertTrue(s.pet!!.form.isCat)
        assertTrue(s.pet!!.form.stage.ordinal >= Stage.CHILD.ordinal)
    }

    @Test
    fun creatureCounts() {
        assertEquals(124, Form.creatures.size)
        assertEquals(10, Form.cats.size)
        assertEquals(27, Form.smallAnimals.size)
        for (line in EggLine.entries.filter { it.real }) assertTrue("$line furs", Skins.furs(line).isNotEmpty())
    }

    @Test
    fun feelingsReactToActions() {
        var s = started()
        s = Engine.perform(s, Action.Play, t0 + 1000, zone).state
        assertEquals(Emotion.VERSPIELT, s.pet!!.emotion(t0 + 2000))
        // The feeling fades
        assertTrue(s.pet!!.emotion(t0 + 2 * hour) != Emotion.VERSPIELT)
    }

    @Test
    fun petGetsLonelyAndIsHappyWhenYouComeBack() {
        val s = started()
        val later = Engine.tick(s, t0 + 9 * hour, zone).state
        val p = later.pet!!.copy(satiety = 80.0, joy = 60.0, energy = 80.0, hygiene = 80.0, poops = 0, sick = false)
        assertEquals(Emotion.EINSAM, p.emotion(t0 + 9 * hour))
        val o = Engine.perform(later.copy(pet = p), Action.Stroke, t0 + 9 * hour, zone)
        assertTrue(o.state.pet!!.joy > p.joy + 5)
        assertEquals(Emotion.AUFGEREGT, o.state.pet!!.emotion(t0 + 9 * hour + 1000))
    }

    @Test
    fun jobsNeedLevelAndPayWages() {
        var s = started()
        s = Engine.perform(s, Action.ChooseJob(Job.KOCH), t0, zone).state
        assertNull(s.pet!!.job)
        s = withLevel(s, Jobs.UNLOCK_LEVEL)
        s = Engine.perform(s, Action.ChooseJob(Job.KOCH), t0, zone).state
        assertEquals(Job.KOCH, s.pet!!.job)
        val coins = s.coins
        s = Engine.perform(s, Action.Work, t0 + 1000, zone).state
        assertTrue(s.coins > coins)
        assertEquals(1, s.pet!!.jobXp)
        assertTrue((s.pet!!.talents[Talent.KOCHEN] ?: 0) > 0)
        // Cooldown
        val again = Engine.perform(s, Action.Work, t0 + 2000, zone)
        assertEquals(1, again.state.pet!!.jobXp)
        assertNotNull(Engine.workBlocker(s, t0 + 2000))
        assertNull(Engine.workBlocker(s, t0 + 3 * hour))
    }

    @Test
    fun changingJobCosts() {
        var s = withLevel(started(), 6).copy(coins = 150)
        s = Engine.perform(s, Action.ChooseJob(Job.MUSIKER), t0, zone).state
        assertEquals(150, s.coins)
        s = Engine.perform(s, Action.ChooseJob(Job.SPORTLER), t0, zone).state
        assertEquals(150 - Jobs.CHANGE_PRICE, s.coins)
        assertEquals(Job.SPORTLER, s.pet!!.job)
    }

    @Test
    fun talentsGrowAndLevel() {
        assertEquals(0, Talents.level(0))
        assertEquals(1, Talents.level(Talents.xpFor(1)))
        assertEquals(Talents.MAX_LEVEL, Talents.level(100_000))
        var s = started()
        repeat(10) { i -> s = Engine.perform(s, Action.GameFinished(MiniGame.SIMON, 12), t0 + i * 1000L, zone).state }
        assertTrue(s.pet!!.talentLevel(Talent.MUSIK) >= 2)
    }

    @Test
    fun chatNeedsLevelSixAndRemembersName() {
        var s = started()
        s = Engine.perform(s, Action.Chat("Hallo"), t0, zone).state
        assertTrue(s.chat.messages.isEmpty())
        s = withLevel(s, Chat.UNLOCK_LEVEL)
        s = Engine.perform(s, Action.OpenChat, t0, zone).state
        assertEquals(1, s.chat.messages.size)
        s = Engine.perform(s, Action.Chat("Ich heiße Toni"), t0 + 1000, zone).state
        assertEquals("Toni", s.chat.memory[Chat.MEM_NAME])
        s = Engine.perform(s, Action.Chat("Wie heiße ich?"), t0 + 2000, zone).state
        assertTrue(s.chat.messages.last().text.contains("Toni"))
        s = Engine.perform(s, Action.Chat("Meine Lieblingsfarbe ist Grün"), t0 + 3000, zone).state
        assertEquals("Grün", s.chat.memory[Chat.MEM_COLOR])
    }

    @Test
    fun chatCanStartPlaying() {
        var s = withLevel(started(), 7)
        val plays = s.counters.plays
        s = Engine.perform(s, Action.Chat("Lass uns spielen!"), t0 + 10_000, zone).state
        assertEquals(plays + 1, s.counters.plays)
    }

    @Test
    fun chatUnderstandsCommonQuestions() {
        val s = withLevel(started(), 7)
        val p = s.pet!!
        for (q in listOf("Wie geht's dir?", "Hast du Hunger?", "Erzähl mir einen Witz!", "Was ist dein Lieblingsessen?", "Wie alt bist du?")) {
            val r = Chat.reply(s, p, q, t0, zone)
            assertTrue(q, r.text.isNotBlank())
            assertFalse(q, r.text.startsWith("Hmm, das ist eine gute Frage"))
        }
    }

    @Test
    fun festivalPrizesUnlockWithEarnedTokens() {
        val halloween = ZonedDateTime.of(2026, 10, 20, 12, 0, 0, 0, zone).toInstant().toEpochMilli()
        var s = Engine.perform(GameState(), Action.Start(Difficulty.RELAXED, "Mochi"), halloween, zone).state
        repeat(Engine.HATCH_TAPS) { s = Engine.perform(s, Action.HatchTap, halloween, zone).state }
        assertEquals(SeasonEvent.HALLOWEEN, Engine.eventOf(s))
        val events = mutableListOf<GameEvent>()
        repeat(60) { i ->
            val o = Engine.perform(s, Action.GameFinished(MiniGame.MEMORY, 10), halloween + i * 1000L, zone)
            s = o.state
            events += o.events
        }
        assertTrue(s.event.earned >= 55)
        assertTrue("neck_batbow" in s.owned)
        assertTrue("skin_pumpkin" in s.skins)
        assertTrue("skin_ghost" in s.skins)
        assertEquals(4, events.count { it is GameEvent.PrizeWon })
        // Spending tokens does not take prizes away
        s = Engine.perform(s, Action.BuyEventOffer("hat_witch"), halloween + 100_000, zone).state
        assertTrue("skin_ghost" in s.skins)
    }

    @Test
    fun wheelSpinsOncePerDayAndGuaranteesAPrize() {
        val halloween = ZonedDateTime.of(2026, 10, 18, 12, 0, 0, 0, zone).toInstant().toEpochMilli()
        var s = Engine.perform(GameState(), Action.Start(Difficulty.RELAXED, "Mochi"), halloween, zone).state
        val o = Engine.perform(s, Action.SpinWheel, halloween, zone)
        s = o.state
        assertTrue(o.events.any { it is GameEvent.Spun })
        val again = Engine.perform(s, Action.SpinWheel, halloween + 1000, zone)
        assertFalse(again.events.any { it is GameEvent.Spun })
        var won = s.event.spinWins
        for (d in 1..5) {
            s = Engine.perform(s, Action.SpinWheel, halloween + d * 24 * hour, zone).state
            won = s.event.spinWins
        }
        assertTrue(won >= 1)
    }

    @Test
    fun skinsCanOnlyBeWornWhenOwned() {
        var s = started()
        s = Engine.perform(s, Action.ApplySkin("skin_ghost"), t0, zone).state
        assertNull(s.pet!!.skin)
        s = s.copy(skins = setOf("skin_ghost"))
        s = Engine.perform(s, Action.ApplySkin("skin_ghost"), t0, zone).state
        assertEquals("skin_ghost", s.pet!!.skin)
        s = Engine.perform(s, Action.ApplySkin(null), t0, zone).state
        assertNull(s.pet!!.skin)
    }

    @Test
    fun perksWorkWhileWorn() {
        var s = started().copy(owned = started().owned + "neck_rosechain")
        s = Engine.perform(s, Action.Equip("neck_rosechain"), t0, zone).state
        assertTrue(Perk.HUNGER in Perks.active(s))
        val plain = Engine.tick(started(), t0 + 4 * hour, zone).state.pet!!.satiety
        val perk = Engine.tick(s, t0 + 4 * hour, zone).state.pet!!.satiety
        assertTrue(perk > plain)
    }

    @Test
    fun screenToysGiveSmallRewardsWithCooldown() {
        var s = started()
        val coins = s.coins
        s = Engine.perform(s, Action.Toy(ToyKind.PLANE), t0 + 1000, zone).state
        assertTrue(s.coins > coins)
        val c2 = s.coins
        s = Engine.perform(s, Action.Toy(ToyKind.PLANE), t0 + 2000, zone).state
        assertEquals(c2, s.coins)
        assertEquals(1, s.counters.planes)
    }

    @Test
    fun saveRoundTripKeepsNewFields() {
        var s = withLevel(started(), 7).copy(skins = setOf("skin_frost"), petKind = PetKind.FANTASY)
        s = Engine.perform(s, Action.Chat("Ich heiße Toni"), t0, zone).state
        val back = GameJson.decode(GameJson.encode(s))!!
        assertEquals(s.chat.memory, back.chat.memory)
        assertEquals(s.skins, back.skins)
        assertEquals(s.pet!!.lastCare, back.pet!!.lastCare)
    }
}
