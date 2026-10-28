package de.knuffi.core

/**
 * The life of a pet: from level 55 it slowly grows old, and when it reaches its own
 * life span (somewhere between level 60 and 80) it says goodbye and becomes a star.
 */
object Lifespan {
    const val ELDER_LEVEL = 55
    const val MIN_LEVEL = 60
    const val MAX_LEVEL = 80

    /** The level at which this pet says goodbye. Fixed for every pet, but different between pets. */
    fun level(p: Pet): Int = MIN_LEVEL + Math.floorMod(p.id * 7_919L + 13L, (MAX_LEVEL - MIN_LEVEL + 1).toLong()).toInt()

    fun isElder(p: Pet): Boolean = !p.isEgg && p.level >= ELDER_LEVEL

    /** How old the pet looks: 0 until level 55, 1 at the end of its life. */
    fun age(p: Pet): Float {
        if (!isElder(p)) return 0f
        val span = (level(p) - ELDER_LEVEL).coerceAtLeast(1)
        return (0.35f + 0.65f * (p.level - ELDER_LEVEL).toFloat() / span).coerceIn(0f, 1f)
    }

    /** Days the pet has been with you. */
    fun days(p: Pet, now: Long): Long = ((now - (if (p.hatchedAt > 0) p.hatchedAt else p.bornAt)) / 86_400_000L).coerceAtLeast(0)

    /** The last diary entry, written on the day of the goodbye. */
    fun farewellText(p: Pet, now: Long): String {
        val days = days(p, now)
        val tricks = Tricks.learnedCount(p)
        val parts = mutableListOf(
            if (p.isCat) "Miau, liebes Tagebuch," else "Liebes Tagebuch,",
            "heute ist ein besonderer Tag. Ich bin ganz schön alt geworden, Level ${p.level}!",
            if (days > 1) "$days Tage waren wir zusammen." else "Wir hatten eine wunderschöne Zeit zusammen.",
        )
        if (tricks > 0) parts += "Ich habe $tricks ${if (tricks == 1) "Kunststück" else "Kunststücke"} gelernt und war so stolz darauf."
        parts += "Ich werde bald ein Stern am Himmel. Wenn du nachts hochschaust, funkle ich dir zu."
        parts += "Danke für alles. Ich hab dich lieb."
        parts += "Dein ${p.name}"
        return parts.joinToString(" ")
    }
}

/** Eggs: free ones are rare, and spare ones can be given away. */
object Eggs {
    /** Chance that a free reward really is an egg (otherwise there are coins). */
    const val FREE_CHANCE = 0.25f

    /** Coins for an egg given to the egg nursery. */
    fun giveAwayCoins(line: EggLine): Int = when {
        line.eventOnly -> 60
        line.real -> 30
        else -> 35
    }

    const val FAMILY_EGG_COINS = 45
}
