package de.knuffi.core

/** Extras of the shop: a daily deal and a mystery box. */
object Shop {
    const val DEAL_PERCENT = 30
    const val MYSTERY_PRICE = 150

    private val dealPool: List<Item> by lazy {
        Catalog.items.filter { it.sold && it.cosmetic && it.price >= 40 && it.kind != ItemKind.ROOM }.sortedBy { it.id }
    }

    /** Today's special offer (the same for the whole day). */
    fun dealOf(day: Long): Item = dealPool[Math.floorMod(day * 7919L + 13L, dealPool.size.toLong()).toInt()]

    /** Price of [item] on [day], with the daily discount. */
    fun price(item: Item, day: Long): Int =
        if (item.price > 0 && dealOf(day).id == item.id) item.price * (100 - DEAL_PERCENT) / 100 else item.price

    /** Everything the mystery box can still contain. */
    fun mysteryPool(state: GameState, level: Int): List<Item> =
        Catalog.items.filter { it.sold && it.cosmetic && it.price > 0 && it.kind != ItemKind.ROOM && it.id !in state.owned && it.minLevel <= level }
}
