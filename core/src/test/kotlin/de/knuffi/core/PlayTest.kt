package de.knuffi.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneId
import java.time.ZonedDateTime

class PlayTest {
    private val zone: ZoneId = ZoneId.of("Europe/Berlin")
    private fun at(h: Int, day: Int = 10, min: Int = 0): Long = ZonedDateTime.of(2026, 3, day, h, min, 0, 0, zone).toInstant().toEpochMilli()
    private val hour = 3_600_000L
    private val t0 = at(9)

    private fun started(t: Long = t0, kind: PetKind = PetKind.FANTASY): GameState {
        var s = Engine.perform(GameState(), Action.Start(Difficulty.RELAXED, "Mochi", kind = kind), t, zone).state
        repeat(Engine.HATCH_TAPS) { s = Engine.perform(s, Action.HatchTap, t, zone).state }
        return s
    }

    private fun withLevel(s: GameState, level: Int) = s.copy(pet = s.pet!!.copy(level = level), counters = s.counters.copy(maxLevel = level))

    // ------------------------------------------------------------------ new species

    @Test
    fun smallAnimalBasketGivesBabyWithFur() {
        var s = started()
        s = s.copy(eggs = mapOf(EggLine.HASE to 1))
        s = Engine.perform(s, Action.HatchEgg(EggLine.HASE, "Hoppel"), t0 + 1000, zone).state
        val bunny = s.pet!!
        assertEquals(Form.HAESCHEN, bunny.form)
        assertTrue(bunny.isSmall && bunny.isReal)
        assertNotNull(Skins[bunny.fur])
        assertTrue(Skins.bunnyFurs.any { it.id == bunny.fur })
        assertTrue(s.discovered(Form.HAESCHEN))
        // A bad life makes a scruffy bunny, never a ghost.
        val teen = bunny.copy(form = Form.HOPPELHASE, care = CareLog(mistakes = 8))
        assertEquals(Form.ZAUSELHASE, Leveling.nextForm(teen))
    }

    @Test
    fun catModeKeepsSmallAnimalBaskets() {
        var s = Engine.perform(GameState(), Action.Start(Difficulty.RELAXED, "Minka", kind = PetKind.CAT), t0, zone).state
        s = s.copy(coins = 1000, counters = s.counters.copy(maxLevel = 5))
        s = Engine.perform(s, Action.Buy("egg_hamster"), t0 + 1000, zone).state
        assertEquals(1, s.eggCount(EggLine.HAMSTER))
        s = Engine.perform(s, Action.Buy("egg_wald"), t0 + 2000, zone).state
        assertEquals(1, s.eggCount(EggLine.KATZE))
    }

    @Test
    fun newFantasyLinesHatch() {
        var s = started()
        s = s.copy(eggs = mapOf(EggLine.EDELSTEIN to 1))
        s = Engine.perform(s, Action.HatchEgg(EggLine.EDELSTEIN, "Glitzer"), t0 + 1000, zone).state
        repeat(Engine.HATCH_TAPS) { s = Engine.perform(s, Action.HatchTap, t0 + 2000, zone).state }
        assertEquals(Form.KRISTALLI, s.pet!!.form)
        assertTrue(Catalog["egg_dschungel"] != null && Catalog["egg_wueste"] != null)
    }

    // ------------------------------------------------------------------ tricks

    @Test
    fun practisingTeachesATrick() {
        var s = started()
        val t = Trick.SITZ
        var now = t0
        var learnedEvent = false
        repeat(60) {
            now += 5_000L
            s = s.copy(pet = s.pet!!.copy(energy = 90.0, joy = 90.0))
            val o = Engine.perform(s, Action.PracticeTrick(t), now, zone)
            s = o.state
            if (o.events.any { it is GameEvent.TrickPracticed && it.learned }) learnedEvent = true
        }
        assertTrue(learnedEvent)
        assertTrue(Tricks.learned(s.pet!!, t))
        assertEquals(1, s.counters.tricksLearned)
        assertTrue(s.isUnlocked(Achievement.TRICK_1))
        // Showing a learned trick
        val o = Engine.perform(s, Action.ShowTrick(t), now + 60_000L, zone)
        assertTrue(o.events.any { it is GameEvent.TrickShown })
        assertEquals(s.counters.trickShows + 1, o.state.counters.trickShows)
    }

    @Test
    fun tricksNeedLevelAndEnergy() {
        var s = started()
        val o = Engine.perform(s, Action.PracticeTrick(Trick.SALTO), t0 + 5000, zone)
        assertTrue(o.events.any { it is GameEvent.Message && it.text.contains("Level") })
        s = s.copy(pet = s.pet!!.copy(energy = 3.0))
        val tired = Engine.perform(s, Action.PracticeTrick(Trick.SITZ), t0 + 5000, zone)
        assertEquals(0, Tricks.points(tired.state.pet!!, Trick.SITZ))
    }

    // ------------------------------------------------------------------ touching & room games

    @Test
    fun ticklingCountsAndMakesHappy() {
        var s = started()
        s = s.copy(pet = s.pet!!.copy(joy = 40.0))
        s = Engine.perform(s, Action.Touch(TouchZone.BELLY), t0 + 2000, zone).state
        s = Engine.perform(s, Action.Touch(TouchZone.HEAD), t0 + 4000, zone).state
        assertEquals(1, s.counters.tickles)
        assertEquals(2, s.counters.touches)
        assertTrue(s.pet!!.joy > 45.0)
    }

    @Test
    fun roomGameGivesRewards() {
        var s = started()
        val coins = s.coins
        s = Engine.perform(s, Action.RoomGameFinished(RoomGame.HIDE, 3), t0 + 2000, zone).state
        assertTrue(s.coins > coins)
        assertEquals(1, s.counters.roomGames)
        assertEquals(1, s.adventure.points)
    }

    // ------------------------------------------------------------------ wishes

    @Test
    fun wishesAppearAndCanBeFulfilled() {
        var s = started()
        // First tick only schedules the first wish.
        s = Engine.tick(s, t0 + 1000, zone, userPresent = true).state
        assertNull(s.wish)
        assertTrue(s.nextWishAt > t0)
        val o = Engine.tick(s, s.nextWishAt + 1000, zone, userPresent = true)
        s = o.state
        val wish = s.wish
        assertNotNull(wish)
        assertTrue(o.events.any { it is GameEvent.WishMade })
        // Fulfil it by hand: force a food wish and feed.
        s = s.copy(wish = Wish(WishKind.FOOD, "apple", createdAt = t0, until = s.nextWishAt + 10 * hour), inventory = s.inventory + ("apple" to 2))
        s = s.copy(pet = s.pet!!.copy(satiety = 30.0))
        val coins = s.coins
        val fed = Engine.perform(s, Action.Feed("apple"), s.nextWishAt - 1000, zone)
        assertTrue(fed.events.any { it is GameEvent.WishFulfilled })
        assertNull(fed.state.wish)
        assertTrue(fed.state.coins >= coins + Wishes.COINS)
        assertEquals(1, fed.state.counters.wishes)
    }

    @Test
    fun cuddleWishNeedsSeveralTouches() {
        var s = started()
        s = s.copy(wish = Wish(WishKind.CUDDLE, need = 3, createdAt = t0, until = t0 + 2 * hour), nextWishAt = t0 + 3 * hour)
        s = Engine.perform(s, Action.Touch(TouchZone.HEAD), t0 + 1000, zone).state
        s = Engine.perform(s, Action.Touch(TouchZone.HEAD), t0 + 3000, zone).state
        assertEquals(2, s.wish!!.progress)
        s = Engine.perform(s, Action.Touch(TouchZone.BELLY), t0 + 5000, zone).state
        assertNull(s.wish)
    }

    @Test
    fun oldWishesFadeAway() {
        var s = started()
        s = s.copy(wish = Wish(WishKind.PLAY, createdAt = t0, until = t0 + hour), nextWishAt = t0 + 3 * hour)
        s = Engine.tick(s, t0 + 2 * hour, zone, userPresent = true).state
        assertNull(s.wish)
    }

    // ------------------------------------------------------------------ monthly & long-term goals

    @Test
    fun monthlyGoalsRollAndClaim() {
        var s = started()
        assertEquals(Quests.PER_MONTH, s.monthly.goals.size)
        assertEquals(TimeUtil.month(t0, zone), s.monthly.month)
        val q = s.monthly.goals[0]
        s = s.copy(monthly = s.monthly.copy(goals = s.monthly.goals.map { it.copy(progress = it.target) }))
        val coins = s.coins
        s = Engine.perform(s, Action.ClaimMonthly(0), t0 + 1000, zone).state
        assertTrue(s.coins >= coins + Quests.monthlyCoins(q.type))
        s = Engine.perform(s, Action.ClaimMonthly(1), t0 + 2000, zone).state
        s = Engine.perform(s, Action.ClaimMonthly(2), t0 + 3000, zone).state
        val eggs = s.totalEggs
        val before = s.coins
        s = Engine.perform(s, Action.ClaimMonthlyBonus, t0 + 4000, zone).state
        assertTrue(s.monthly.bonusClaimed)
        // an egg only sometimes, otherwise extra coins
        assertTrue(s.totalEggs == eggs + 1 || s.coins >= before + Quests.MONTHLY_BONUS_COINS + 80)
        assertEquals(1, s.counters.monthlies)
        // Next month brings new goals.
        val next = Engine.tick(s, at(9, day = 10) + 32L * 24 * hour, zone).state
        assertFalse(next.monthly.bonusClaimed)
    }

    @Test
    fun longGoalsCanBeClaimedOnce() {
        var s = started()
        val goal = LongGoal.WISHES_25
        assertFalse(goal.done(s))
        s = s.copy(counters = s.counters.copy(wishes = 25))
        assertTrue(goal.done(s))
        val eggs = s.totalEggs
        s = Engine.perform(s, Action.ClaimGoal(goal), t0 + 1000, zone).state
        assertTrue(goal.claimed(s))
        assertEquals(eggs + 1, s.totalEggs)
        val again = Engine.perform(s, Action.ClaimGoal(goal), t0 + 2000, zone).state
        assertEquals(s.totalEggs, again.totalEggs)
    }

    @Test
    fun furnitureGoalGivesNewItem() {
        var s = started()
        val p = s.pet!!
        s = s.copy(pet = p.copy(tricks = Trick.entries.take(5).associateWith { it.practice }))
        s = Engine.perform(s, Action.ClaimGoal(LongGoal.TRICKS_5), t0 + 1000, zone).state
        assertTrue("deco_stage" in s.owned)
    }

    // ------------------------------------------------------------------ adventure map

    @Test
    fun adventureMapSteps() {
        var s = started()
        val blocked = Engine.perform(s, Action.AdventureStep, t0 + 1000, zone)
        assertEquals(0, blocked.state.adventure.station)
        s = s.copy(adventure = AdventureState(points = 500))
        var eggs = s.totalEggs
        for (i in 0 until 10) s = Engine.perform(s, Action.AdventureStep, t0 + 2000 + i, zone).state
        assertEquals(10, s.adventure.station)
        // only the end of the map brings an egg now
        assertEquals(eggs, s.totalEggs)
        assertEquals(10, s.counters.adventureSteps)
        // Finishing the whole map starts a new one.
        s = s.copy(adventure = AdventureState(points = 500, station = Adventure.STATIONS - 1))
        eggs = s.totalEggs
        val o = Engine.perform(s, Action.AdventureStep, t0 + 5000, zone)
        assertEquals(1, o.state.adventure.map)
        assertEquals(0, o.state.adventure.station)
        assertTrue(o.events.any { it is GameEvent.AdventureStep && it.mapDone })
        assertEquals(eggs + 1, o.state.totalEggs)
    }

    @Test
    fun adventureCostsGrow() {
        assertTrue(Adventure.cost(0, 0) < Adventure.cost(25, 0))
        assertTrue(Adventure.cost(0, 0) < Adventure.cost(0, 3))
        assertEquals(Adventure.station(1, 4), Adventure.station(1, 4))
    }

    // ------------------------------------------------------------------ tournaments

    @Test
    fun tournamentRewardsAndCups() {
        var s = withLevel(started(), 3)
        val friend = s.pet!!.copy(name = "Flitz", bornAt = t0 - hour)
        s = s.copy(resting = listOf(friend))
        val rivals = Tournament.rivals(s, Cup.BRONZE, 1)
        assertEquals(Tournament.RIVALS, rivals.size)
        assertEquals(friend.id, rivals.first().petId)
        val coins = s.coins
        val o = Engine.perform(s, Action.TournamentFinished(Cup.BRONZE, 1, listOf(friend.id)), t0 + 1000, zone)
        s = o.state
        assertTrue(s.coins >= coins + Cup.BRONZE.coins)
        assertEquals(1, Tournament.wins(s, Cup.BRONZE))
        assertTrue(o.events.any { it is GameEvent.TournamentDone && it.firstWin })
        assertTrue(s.isUnlocked(Achievement.TOURNEY_1))
        assertTrue((s.friendship[Friends.key(s.pet!!.id, friend.id)] ?: 0) > 0)
        // Locked cups give nothing.
        val gold = Engine.perform(s, Action.TournamentFinished(Cup.GOLD, 1), t0 + 2000, zone).state
        assertEquals(0, Tournament.wins(gold, Cup.GOLD))
        assertEquals(2, Tournament.place(10, listOf(12, 8, 3)))
    }

    // ------------------------------------------------------------------ visitors

    @Test
    fun twoFriendsCanVisit() {
        var s = started()
        val a = s.pet!!.copy(name = "Anni", bornAt = t0 - hour)
        val b = s.pet!!.copy(name = "Bo", bornAt = t0 - 2 * hour)
        s = s.copy(resting = listOf(a, b))
        s = Engine.perform(s, Action.Invite(a.id), t0 + 1000, zone).state
        s = Engine.perform(s, Action.Invite(b.id), t0 + 2000, zone).state
        assertEquals(a.id, s.visit!!.petId)
        assertEquals(b.id, s.visit2!!.petId)
        assertEquals(2, s.visitors.size)
        s = Engine.perform(s, Action.PlayTogether, t0 + 3000, zone).state
        assertTrue((s.friendship[Friends.key(a.id, b.id)] ?: 0) > 0)
        s = Engine.perform(s, Action.SendHome(a.id), t0 + 4000, zone).state
        assertEquals(b.id, s.visit!!.petId)
        assertNull(s.visit2)
        // The visit ends on its own.
        s = Engine.tick(s, t0 + 2 * hour, zone).state
        assertNull(s.visit)
    }

    @Test
    fun releasingAVisitorEndsTheVisit() {
        var s = started()
        val a = s.pet!!.copy(name = "Anni", bornAt = t0 - hour)
        val b = s.pet!!.copy(name = "Bo", bornAt = t0 - 2 * hour)
        s = s.copy(resting = listOf(a, b), visit = Visit(a.id, t0 + hour), visit2 = Visit(b.id, t0 + hour))
        s = Engine.perform(s, Action.ReleasePet(b.id), t0 + 1000, zone).state
        assertNull(s.visit2)
        assertEquals(a.id, s.visit!!.petId)
    }

    // ------------------------------------------------------------------ voice

    @Test
    fun singingFindsASecret() {
        var s = started()
        s = Engine.perform(s, Action.Voice(VoiceKind.SING), t0 + 1000, zone).state
        assertTrue(Secret.SINGER.name in s.secrets)
        assertEquals(1, s.counters.voice)
        assertTrue(s.isUnlocked(Achievement.VOICE_1))
    }

    // ------------------------------------------------------------------ new games

    @Test
    fun newGamesHaveRewardsAndGroups() {
        for (g in listOf(MiniGame.SPOT, MiniGame.WORDS, MiniGame.SHADOW, MiniGame.SPACE, MiniGame.JUMPER, MiniGame.RACE)) {
            val r = Engine.gameReward(g, 40)
            assertTrue(g.name, r.coins in 1..Engine.gameMaxCoins(g))
        }
        assertEquals(GameGroup.KNOBELN, MiniGame.SHADOW.group)
        assertEquals(GameGroup.ACTION, MiniGame.SPACE.group)
        assertEquals(GameGroup.RUHIG, MiniGame.COLORING.group)
        var s = started()
        s = s.copy(daily = s.daily.copy(quests = listOf(QuestProgress(QuestType.PUZZLES, 2), QuestProgress(QuestType.ACTION, 2))))
        s = Engine.perform(s, Action.GameFinished(MiniGame.WORDS, 5), t0 + 1000, zone).state
        s = Engine.perform(s, Action.GameFinished(MiniGame.RACE, 300), t0 + 2000, zone).state
        assertEquals(1, s.daily.quests[0].progress)
        assertEquals(1, s.daily.quests[1].progress)
    }

    @Test
    fun saveGameWithNewFieldsRoundTrips() {
        var s = started()
        s = s.copy(
            wish = Wish(WishKind.GAME, "SPACE", createdAt = 1, until = 2),
            adventure = AdventureState(12, 3, 1),
            cups = mapOf(Cup.SILBER.name to 2),
            pet = s.pet!!.copy(tricks = mapOf(Trick.ROLLE to 3)),
        )
        val back = GameJson.decode(GameJson.encode(s))
        assertEquals(s, back)
        // Old saves without the new fields still load.
        val old = GameJson.decode("""{"onboarded":true,"pet":{"name":"Alt","bornAt":5}}""")
        assertNotNull(old)
        assertNull(old!!.wish)
        assertEquals(0, old.adventure.points)
    }
}
