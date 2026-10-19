package de.knuffi.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneId
import java.time.ZonedDateTime

class ShopTest {
    private val zone: ZoneId = ZoneId.of("Europe/Berlin")
    private val t: Long = ZonedDateTime.of(2026, 3, 10, 9, 0, 0, 0, zone).toInstant().toEpochMilli()
    private val day = TimeUtil.epochDay(t, zone)

    private fun started(): GameState {
        var s = Engine.perform(GameState(), Action.Start(Difficulty.RELAXED, "Mochi"), t, zone).state
        repeat(Engine.HATCH_TAPS) { s = Engine.perform(s, Action.HatchTap, t, zone).state }
        return s.copy(pet = s.pet!!.copy(level = 20), coins = 2000)
    }

    @Test
    fun dailyDealIsCheaper() {
        val deal = Shop.dealOf(day)
        val price = Shop.price(deal, day)
        assertTrue(price < deal.price)
        assertEquals(deal, Shop.dealOf(day))
        val base = started().let { it.copy(owned = it.owned - deal.id) }
        val today = Engine.perform(base, Action.Buy(deal.id), t, zone).state
        // a day on which something else is on offer
        var other = t + 86_400_000L
        while (Shop.dealOf(TimeUtil.epochDay(other, zone)).id == deal.id) other += 86_400_000L
        val later = Engine.perform(base, Action.Buy(deal.id), other, zone).state
        assertTrue(deal.id in today.owned)
        assertEquals(deal.price - price, today.coins - later.coins)
    }

    @Test
    fun mysteryBoxGivesSomethingNew() {
        var s = started()
        val owned = s.owned
        s = Engine.perform(s, Action.BuyMystery, t, zone).state
        val new = s.owned - owned
        assertEquals(1, new.size)
        assertEquals(1, s.counters.purchases)
    }

    @Test
    fun decoCornerCanBeBoughtAndRemoved() {
        var s = started()
        s = Engine.perform(s, Action.Buy("deco_sofa"), t, zone).state
        assertEquals("deco_sofa", s.equipped[Slot.DECO])
        s = Engine.perform(s, Action.Unequip(Slot.DECO), t, zone).state
        assertFalse(Slot.DECO in s.equipped)
    }

    @Test
    fun miauUnlocksKittensForFantasyPlayers() {
        var s = started()
        assertFalse(s.catMode)
        val baskets = s.eggCount(EggLine.KATZE)
        s = Engine.perform(s, Action.Chat("Miau miau miau!"), t, zone).state
        assertTrue(s.catsUnlocked)
        assertTrue(Secret.CATS.name in s.secrets)
        assertEquals(baskets + 1, s.eggCount(EggLine.KATZE))
        // only once
        s = Engine.perform(s, Action.Chat("miau miau miau"), t + 5000, zone).state
        assertEquals(baskets + 1, s.eggCount(EggLine.KATZE))
    }

    @Test
    fun tapComboUnlocksKittensToo() {
        var s = started()
        s = Engine.perform(s, Action.FindSecret(Secret.CATS), t, zone).state
        assertTrue(s.catsUnlocked)
        assertTrue(Secrets.catWish("MIAU, Miau ... miau?"))
        assertFalse(Secrets.catWish("miau"))
    }
}
