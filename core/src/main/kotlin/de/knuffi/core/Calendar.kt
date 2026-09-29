package de.knuffi.core

import kotlinx.serialization.Serializable
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit

@Serializable
enum class Season(val title: String, val emoji: String) {
    SPRING("Frühling", "🌷"),
    SUMMER("Sommer", "☀️"),
    AUTUMN("Herbst", "🍂"),
    WINTER("Winter", "❄️"),
}

/** Holidays and festivals with their own decorations, token shop and egg. */
@Serializable
enum class SeasonEvent(
    val title: String,
    val emoji: String,
    val tokenName: String,
    val tokenEmoji: String,
    val description: String,
    val egg: EggLine,
) {
    NEUJAHR("Neujahrsfest", "🎆", "Sterne", "⭐", "Feuerwerk, Glücksbringer und gute Vorsätze!", EggLine.STERN),
    VALENTIN("Herzchenwoche", "💝", "Herzen", "💗", "Die Woche der Freundschaft und Liebe.", EggLine.ZUCKER),
    OSTERN("Osterfest", "🐣", "Ostereier", "🥚", "Bunte Eier, Schokohasen und Frühlingsblumen.", EggLine.BLUETE),
    SOMMERFEST("Sommerfest", "🏖️", "Muscheln", "🐚", "Sonne, Strand und Eis am Stiel!", EggLine.MEER),
    HALLOWEEN("Halloween", "🎃", "Kürbisse", "🎃", "Süßes oder Saures? Gruselig schöne Tage!", EggLine.GRUSEL),
    WINTERZAUBER("Winterzauber", "🎄", "Schneeflocken", "❄️", "Lichterglanz, Plätzchen und Schnee.", EggLine.FROST),
}

object EventCalendar {
    fun date(now: Long, zone: ZoneId): LocalDate = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()

    fun season(d: LocalDate): Season = when (d.monthValue) {
        3, 4, 5 -> Season.SPRING
        6, 7, 8 -> Season.SUMMER
        9, 10, 11 -> Season.AUTUMN
        else -> Season.WINTER
    }

    /** Easter Sunday (Gregorian calendar, anonymous algorithm). */
    fun easter(year: Int): LocalDate {
        val a = year % 19
        val b = year / 100
        val c = year % 100
        val d = b / 4
        val e = b % 4
        val f = (b + 8) / 25
        val g = (b - f + 1) / 3
        val h = (19 * a + b - d - g + 15) % 30
        val i = c / 4
        val k = c % 4
        val l = (32 + 2 * e + 2 * i - h - k) % 7
        val m = (a + 11 * h + 22 * l) / 451
        val month = (h + l - 7 * m + 114) / 31
        val day = ((h + l - 7 * m + 114) % 31) + 1
        return LocalDate.of(year, month, day)
    }

    /** Start and end (inclusive) of an event around [d]'s year. */
    fun range(event: SeasonEvent, year: Int): Pair<LocalDate, LocalDate> = when (event) {
        SeasonEvent.NEUJAHR -> LocalDate.of(year, 1, 1) to LocalDate.of(year, 1, 7)
        SeasonEvent.VALENTIN -> LocalDate.of(year, 2, 8) to LocalDate.of(year, 2, 16)
        SeasonEvent.OSTERN -> easter(year).minusDays(9) to easter(year).plusDays(2)
        SeasonEvent.SOMMERFEST -> LocalDate.of(year, 7, 15) to LocalDate.of(year, 8, 10)
        SeasonEvent.HALLOWEEN -> LocalDate.of(year, 10, 17) to LocalDate.of(year, 11, 2)
        SeasonEvent.WINTERZAUBER -> LocalDate.of(year, 12, 1) to LocalDate.of(year, 12, 31)
    }

    fun active(d: LocalDate): SeasonEvent? = SeasonEvent.entries.firstOrNull { e ->
        val (from, to) = range(e, d.year)
        !d.isBefore(from) && !d.isAfter(to)
    }

    fun active(now: Long, zone: ZoneId): SeasonEvent? = active(date(now, zone))

    /** Unique key for an event instance, e.g. "HALLOWEEN-2026". */
    fun key(event: SeasonEvent, d: LocalDate): String = "${event.name}-${d.year}"

    /** The next event that has not started yet and how many days until it starts. */
    fun next(d: LocalDate): Pair<SeasonEvent, Long> {
        var best: Pair<SeasonEvent, Long>? = null
        for (e in SeasonEvent.entries) {
            for (y in d.year..d.year + 1) {
                val start = range(e, y).first
                if (start.isAfter(d)) {
                    val days = ChronoUnit.DAYS.between(d, start)
                    if (best == null || days < best.second) best = e to days
                    break
                }
            }
        }
        return best!!
    }

    fun daysLeft(event: SeasonEvent, d: LocalDate): Long = ChronoUnit.DAYS.between(d, range(event, d.year).second) + 1

    /** Season pass id, e.g. "2026-AUTUMN". Winter belongs to the year it starts in. */
    fun passId(d: LocalDate): String {
        val s = season(d)
        val year = if (s == Season.WINTER && d.monthValue <= 2) d.year - 1 else d.year
        return "$year-${s.name}"
    }

    fun seasonEnd(d: LocalDate): LocalDate {
        val s = season(d)
        return when (s) {
            Season.SPRING -> LocalDate.of(d.year, 5, 31)
            Season.SUMMER -> LocalDate.of(d.year, 8, 31)
            Season.AUTUMN -> LocalDate.of(d.year, 11, 30)
            Season.WINTER -> if (d.monthValue == 12) LocalDate.of(d.year + 1, 2, 1).plusMonths(1).minusDays(1) else LocalDate.of(d.year, 3, 1).minusDays(1)
        }
    }
}
