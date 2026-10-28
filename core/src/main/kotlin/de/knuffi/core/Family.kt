package de.knuffi.core

import kotlinx.serialization.Serializable
import kotlin.random.Random

// ---------------------------------------------------------------------------------- offspring

/** An egg two best friends got together. It hatches into a pet with traits of both. */
@Serializable
data class FamilyEgg(
    val id: Long,
    val line: EggLine,
    val parents: List<Long>,
    val parentNames: List<String>,
    /** Form of the other parent; its colours shine through a little. */
    val blend: String? = null,
    val fur: String? = null,
    val createdAt: Long = 0,
) {
    val title: String get() = "Nachwuchs von ${parentNames.joinToString(" und ")}"
}

object Family {
    const val COOLDOWN_MS = 5 * 86_400_000L
    const val CHECK_MS = 20 * 3_600_000L
    /** Family eggs are rare (a quarter of what it used to be). */
    const val CHANCE = 0.09f
    const val MAX_WAITING = 2
    const val MIN_LEVEL = 6

    fun eligible(s: GameState, p: Pet): Boolean =
        p.alive && !p.isEgg && p.level >= MIN_LEVEL && p.stage.ordinal >= Stage.TEEN.ordinal && s.onTrip(p.id) == null

    /** Parent and child or brothers and sisters don't become parents together. */
    fun related(a: Pet, b: Pet): Boolean =
        a.id in b.parents || b.id in a.parents || (a.parents.isNotEmpty() && a.parents.toSet() == b.parents.toSet())

    /** Pairs of best friends who could have a baby. */
    fun couples(s: GameState): List<Pair<Pet, Pet>> {
        val pets = s.allPets.filter { eligible(s, it) }
        val out = mutableListOf<Pair<Pet, Pet>>()
        for (i in pets.indices) for (j in i + 1 until pets.size) {
            val a = pets[i]
            val b = pets[j]
            if (related(a, b)) continue
            if (Friends.level(s.friendship[Friends.key(a.id, b.id)] ?: 0) >= 4) out += a to b
        }
        return out
    }

    fun make(a: Pet, b: Pet, now: Long, rnd: Random): FamilyEgg {
        val (main, other) = if (rnd.nextBoolean()) a to b else b to a
        val line = main.line
        val fur = when {
            line.real && other.line == line -> listOfNotNull(main.fur, other.fur).let { if (it.isEmpty()) null else it[rnd.nextInt(it.size)] }
            line.real -> main.fur
            else -> null
        }
        val blend = if (line.real && other.line == line) null else other.form.name
        return FamilyEgg(now, line, listOf(a.id, b.id), listOf(a.name, b.name), blend, fur, now)
    }
}
