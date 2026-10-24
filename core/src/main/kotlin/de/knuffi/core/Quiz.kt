package de.knuffi.core

import kotlinx.serialization.Serializable
import kotlin.random.Random

/**
 * Wissensquiz for the whole family: hundreds of questions in many topics and three levels,
 * each with a short explanation that is shown after answering.
 */
@Serializable
enum class QuizCategory(val title: String, val emoji: String) {
    TIERE("Tiere", "🐾"),
    NATUR("Natur & Wetter", "🌿"),
    ERDE("Länder & Erde", "🌍"),
    WELTRAUM("Weltraum", "🚀"),
    KOERPER("Körper & Gesundheit", "🫀"),
    GESCHICHTE("Geschichte", "🏰"),
    TECHNIK("Erfindungen & Technik", "💡"),
    WISSENSCHAFT("Wissenschaft", "🔬"),
    SPORT("Sport", "⚽"),
    MUSIK("Musik & Kunst", "🎨"),
    SPRACHE("Bücher & Sprache", "📚"),
    FILM("Filme & Spiele", "🎬"),
    ESSEN("Essen & Trinken", "🍎"),
    DEUTSCHLAND("Deutschland", "🇩🇪"),
    ZAHLEN("Zahlen & Rekorde", "🔢"),
}

/** How hard the questions are. Level 1 is fine for primary school, level 3 is for grown-ups. */
enum class QuizLevel(val title: String, val emoji: String, val levels: Set<Int>) {
    LEICHT("Leicht", "🌱", setOf(1)),
    MITTEL("Mittel", "🌿", setOf(2)),
    SCHWER("Schwer", "🌳", setOf(3)),
    GEMISCHT("Gemischt", "🎲", setOf(1, 2, 3)),
}

data class QuizQuestion(
    val id: Int,
    val category: QuizCategory,
    val level: Int,
    val question: String,
    val answer: String,
    val wrong: List<String>,
    val info: String,
) {
    /** The answer and the wrong ones, shuffled. */
    fun choices(rnd: Random): List<String> = (wrong + answer).shuffled(rnd)
}

@Serializable
data class QuizState(
    /** Questions already asked (they come again only when all of a topic were asked). */
    val seen: Set<Int> = emptySet(),
    val answered: Int = 0,
    val right: Int = 0,
    val rounds: Int = 0,
    val perfect: Int = 0,
    /** Best result per topic ("ALLE" for all topics). */
    val best: Map<String, Int> = emptyMap(),
)

object Quiz {
    const val ROUND = 10
    const val COINS_RIGHT = 3
    const val COINS_PERFECT = 15
    const val ALL = "ALLE"

    val all: List<QuizQuestion> by lazy { QuizData.raw.flatMap { (cat, text) -> parse(cat, text) } }

    private val byId: Map<Int, QuizQuestion> by lazy { all.associateBy { it.id } }

    operator fun get(id: Int): QuizQuestion? = byId[id]

    fun parse(category: QuizCategory, text: String): List<QuizQuestion> = text.lineSequence()
        .map { it.trim() }
        .filter { it.isNotEmpty() && !it.startsWith("#") }
        .map { line ->
            val f = line.split('|').map { it.trim() }
            require(f.size == 7) { "Quizfrage kaputt (${f.size} Teile): $line" }
            QuizQuestion(
                id = (category.name + ":" + f[1]).hashCode(),
                category = category,
                level = f[0].toInt(),
                question = f[1],
                answer = f[2],
                wrong = listOf(f[3], f[4], f[5]),
                info = f[6],
            )
        }.toList()

    fun pool(category: QuizCategory?, level: QuizLevel): List<QuizQuestion> =
        all.filter { (category == null || it.category == category) && it.level in level.levels }

    /** A round of questions: new ones first, the topics mixed, never the same question twice. */
    fun round(category: QuizCategory?, level: QuizLevel, seen: Set<Int>, rnd: Random, size: Int = ROUND): List<QuizQuestion> {
        val pool = pool(category, level)
        val fresh = pool.filter { it.id !in seen }.shuffled(rnd)
        val old = pool.filter { it.id in seen }.shuffled(rnd)
        val picked = (fresh + old).take(size)
        // easy ones first when the levels are mixed
        return if (level == QuizLevel.GEMISCHT) picked.sortedBy { it.level + rnd.nextFloat() * 1.5f } else picked
    }

    fun coins(score: Int, total: Int): Int = score * COINS_RIGHT + if (total >= ROUND && score == total) COINS_PERFECT else 0

    fun xp(score: Int): Int = 5 + score * 2
}
