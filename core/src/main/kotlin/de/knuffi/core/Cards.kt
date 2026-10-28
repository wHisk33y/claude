package de.knuffi.core

import kotlin.random.Random

// ---------------------------------------------------------------------------------- trading cards

enum class CardRarity(val title: String, val emoji: String, val weight: Int, val dust: Int, val craft: Int) {
    COMMON("Gewöhnlich", "⚪", 62, 5, 40),
    RARE("Selten", "🔷", 26, 15, 100),
    EPIC("Episch", "💜", 10, 40, 250),
    LEGEND("Legendär", "🌟", 2, 100, 600),
}

enum class CardStat(val title: String, val emoji: String) {
    POWER("Stärke", "💪"),
    SPEED("Tempo", "⚡"),
    MAGIC("Magie", "✨"),
    CUTE("Niedlich", "💖"),
}

data class CardStats(val power: Int, val speed: Int, val magic: Int, val cute: Int) {
    operator fun get(stat: CardStat): Int = when (stat) {
        CardStat.POWER -> power
        CardStat.SPEED -> speed
        CardStat.MAGIC -> magic
        CardStat.CUTE -> cute
    }

    val total: Int get() = power + speed + magic + cute
}

/**
 * Every creature is also a trading card with four values. Cards come from discovering a creature,
 * from card packs and from duels; spare copies turn into glitter dust to craft missing cards.
 */
object Cards {
    const val PACK = "card_pack"
    const val PACK_SIZE = 5
    const val DUEL_ROUNDS = 5

    val all: List<Form> = Form.entries.filter { it != Form.EGG }
    private val names: Set<String> = all.map { it.name }.toSet()

    fun isCard(name: String): Boolean = name in names

    fun rarity(f: Form): CardRarity = when (f.role) {
        Role.LEGEND -> CardRarity.LEGEND
        Role.ADULT_SPORTY, Role.ADULT_FOODIE, Role.ADULT_BALANCED, Role.SHADOW -> CardRarity.EPIC
        Role.TEEN, Role.TEEN_B, Role.TEEN_SPORTY, Role.TEEN_FOODIE -> CardRarity.RARE
        else -> CardRarity.COMMON
    }

    fun stats(f: Form): CardStats {
        val rnd = Random(f.name.hashCode().toLong() * 31L + 17L)
        val (lo, hi) = when (f.stage) {
            Stage.EGG -> 5 to 20
            Stage.BABY -> 12 to 38
            Stage.CHILD -> 22 to 50
            Stage.TEEN -> 34 to 64
            Stage.ADULT -> 48 to 82
            Stage.PRAECHTIG -> 54 to 86
            Stage.ERFAHREN -> 58 to 89
            Stage.WEISE -> 62 to 92
            Stage.EHRWUERDIG -> 66 to 95
            Stage.URALT -> 70 to 97
            Stage.ZEITLOS -> 74 to 99
        }
        fun roll() = lo + rnd.nextInt(hi - lo + 1)
        var power = roll()
        var speed = roll()
        var magic = roll()
        var cute = roll()
        when (f.role) {
            Role.BABY -> cute += 30
            Role.CHILD, Role.CHILD_B -> cute += 15
            Role.TEEN_SPORTY, Role.ADULT_SPORTY -> { speed += 15; power += 8 }
            Role.TEEN_FOODIE, Role.ADULT_FOODIE -> { power += 12; cute += 8 }
            Role.ADULT_BALANCED -> { magic += 8; cute += 8 }
            Role.SHADOW -> { magic += 15; cute -= 10 }
            Role.LEGEND -> { power += 14; speed += 14; magic += 14; cute += 10 }
            else -> Unit
        }
        when {
            f.line.real -> { cute += 12; magic -= 15 }
            f.line == EggLine.FEUER || f.line == EggLine.URZEIT -> power += 8
            f.line == EggLine.STERN || f.line == EggLine.EINHORN || f.line == EggLine.EDELSTEIN -> magic += 10
            f.line == EggLine.MEER || f.line == EggLine.DSCHUNGEL -> speed += 6
            f.line == EggLine.ZUCKER || f.line == EggLine.BLUETE -> cute += 8
        }
        fun c(v: Int) = v.coerceIn(5, 99)
        return CardStats(c(power), c(speed), c(magic), c(cute))
    }

    fun count(s: GameState, f: Form): Int = s.cards[f.name] ?: 0
    fun owned(s: GameState): List<Form> = all.filter { count(s, it) > 0 }
    fun spare(s: GameState): Int = s.cards.values.sumOf { (it - 1).coerceAtLeast(0) }

    /** Glitter dust all spare copies would give. */
    fun spareDust(s: GameState): Int = all.sumOf { f -> (count(s, f) - 1).coerceAtLeast(0) * rarity(f).dust }

    private fun rollRarity(rnd: Random, boost: Int = 0): CardRarity {
        val weights = CardRarity.entries.map { r -> r.weight + if (r == CardRarity.COMMON) -boost * 3 else boost }
        var x = rnd.nextInt(weights.sum())
        for ((i, w) in weights.withIndex()) {
            x -= w
            if (x < 0) return CardRarity.entries[i]
        }
        return CardRarity.COMMON
    }

    private fun pick(rnd: Random, r: CardRarity): Form {
        val pool = all.filter { rarity(it) == r }
        return pool[rnd.nextInt(pool.size)]
    }

    /** Five cards, at least one of them rare or better. */
    fun openPack(rnd: Random): List<Form> {
        val out = MutableList(PACK_SIZE) { pick(rnd, rollRarity(rnd)) }
        if (out.all { rarity(it) == CardRarity.COMMON }) out[PACK_SIZE - 1] = pick(rnd, CardRarity.RARE)
        return out
    }

    /** The opponent's cards in a duel; stronger players meet stronger opponents. */
    fun opponentDeck(rnd: Random, level: Int): List<Form> {
        val boost = (level / 3).coerceAtMost(8)
        return List(DUEL_ROUNDS) { pick(rnd, rollRarity(rnd, boost)) }
    }

    /** Your cards for a duel: a random choice, the better cards a little more often. */
    fun playerDeck(s: GameState, rnd: Random): List<Form> {
        val owned = owned(s)
        if (owned.isEmpty()) return emptyList()
        val sorted = owned.shuffled(rnd).sortedByDescending { stats(it).total + rnd.nextInt(60) }
        val pool = sorted.take((owned.size / 2).coerceAtLeast(DUEL_ROUNDS))
        return pool.shuffled(rnd).let { p -> List(DUEL_ROUNDS) { p[it % p.size] } }
    }

    fun duelCoins(won: Int): Int = 8 + won * 6
}
