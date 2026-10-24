package de.knuffi.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import kotlin.random.Random

class ContentTest {
    @Test
    fun festivalsNeverOverlap() {
        for (year in 2026..2032) {
            var d = LocalDate.of(year, 1, 1)
            while (d.year == year) {
                val active = SeasonEvent.entries.count { e ->
                    val (from, to) = EventCalendar.range(e, year)
                    !d.isBefore(from) && !d.isAfter(to)
                }
                assertTrue("$d has $active festivals", active <= 1)
                d = d.plusDays(1)
            }
        }
        assertEquals(SeasonEvent.FASCHING, EventCalendar.active(LocalDate.of(2027, 2, 22)))
        assertEquals(SeasonEvent.LATERNEN, EventCalendar.active(LocalDate.of(2026, 11, 11)))
    }

    @Test
    fun everyFestivalHasItsThings() {
        for (e in SeasonEvent.entries) {
            for (id in EventShop.eventItems(e)) assertNotNull("$e item $id", Catalog[id])
            for (id in EventPrizes.gear(e)) assertNotNull("$e gear $id", Catalog[id])
            assertTrue("$e stickers", Stickers.forEvent(e).isNotEmpty())
            assertTrue("$e offers", EventShop.offers(e).size >= 3)
        }
    }

    @Test
    fun newTripsBringSouvenirs() {
        for (d in listOf(Destination.BAUERNHOF, Destination.TIERPARK, Destination.JAHRMARKT)) {
            val sets = Finds.forDestination(d)
            assertTrue(sets.isNotEmpty())
            val f = Finds.roll(sets, Random(1))
            assertTrue(f.set in sets)
        }
        assertTrue(Finds.forDestination(Destination.JAHRMARKT).contains(FindSet.ANDENKEN))
        assertTrue(Finds.of(FindSet.ANDENKEN).size >= 5)
    }

    @Test
    fun newGamesPayLikeTheOthers() {
        for (g in listOf(MiniGame.TANZ, MiniGame.SCHNEEBALL)) {
            val low = Engine.gameReward(g, 0)
            val high = Engine.gameReward(g, 400)
            assertTrue(low.coins >= 2)
            assertTrue(high.coins <= Engine.gameMaxCoins(g))
            assertTrue(high.coins > low.coins)
        }
        assertEquals(Talent.MUSIK, Talents.of(MiniGame.TANZ))
        assertEquals(Talent.SPORT, Talents.of(MiniGame.SCHNEEBALL))
    }
}
