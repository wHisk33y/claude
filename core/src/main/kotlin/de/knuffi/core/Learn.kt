package de.knuffi.core

import kotlinx.serialization.Serializable
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.round
import kotlin.math.sqrt
import kotlin.random.Random

/**
 * Lernzeit: every day a few tasks in Mathe, Deutsch and Physik, matched to the school grade
 * (Gymnasium, Klasse 5 to 13). Mathe answers are typed in, Deutsch and Physik are chosen.
 * Everything is generated offline and the same for the whole day.
 */
@Serializable
enum class Subject(val title: String, val emoji: String) {
    MATHE("Mathe", "🔢"),
    DEUTSCH("Deutsch", "📖"),
    PHYSIK("Physik", "🔭"),
}

/** One task. [choices] empty means the answer is typed in. */
data class LearnTask(
    val subject: Subject,
    val topic: String,
    val question: String,
    val answer: String,
    val choices: List<String> = emptyList(),
    val hint: String,
    val unit: String = "",
    /** Typed fractions must be fully reduced (e.g. "Kürze …"). */
    val reduced: Boolean = false,
) {
    val typed: Boolean get() = choices.isEmpty()

    fun check(input: String): Boolean {
        val given = input.trim()
        if (given.isEmpty()) return false
        if (!typed) return given == answer
        val want = Learn.number(answer) ?: return Learn.normalize(given) == Learn.normalize(answer)
        val got = Learn.number(given) ?: return false
        if (reduced && answer.contains('/')) return Learn.normalize(given).replace(" ", "") == answer
        val decimals = answer.substringAfter(',', "").length.coerceAtLeast(answer.substringAfter('.', "").length)
        val tol = when {
            answer.contains('/') -> 0.006
            decimals == 0 -> 1e-6
            else -> 0.5 * Math.pow(10.0, -decimals.toDouble()) + 1e-9
        }
        return abs(got - want) <= tol
    }
}

@Serializable
data class TopicStat(val tasks: Int = 0, val firstTry: Int = 0, val wrong: Int = 0, val lastDay: Long = -1)

@Serializable
data class LearnState(
    val day: Long = -1,
    /** Tasks solved today per subject. */
    val solved: Map<String, Int> = emptyMap(),
    /** Wrong tries at the current task per subject (for the hint). */
    val tries: Map<String, Int> = emptyMap(),
    val doneDays: Int = 0,
    val streak: Int = 0,
    val bestStreak: Int = 0,
    val lastDoneDay: Long = -1,
    /** Automatic adjustment per subject (-1, 0, +1 grades). */
    val adjust: Map<String, Int> = emptyMap(),
    /** Last first-try results per subject, newest last. */
    val recent: Map<String, List<Boolean>> = emptyMap(),
    /** Statistics per "SUBJECT:topic". */
    val stats: Map<String, TopicStat> = emptyMap(),
    val prizes: Set<String> = emptySet(),
    /** The tasks of yesterday were left undone (the pet is a little sad). */
    val missed: Boolean = false,
    /** The pet already told that it is a little sad today. */
    val missedTold: Boolean = false,
    val totalSolved: Int = 0,
)

/** A prize of the Lernzeit: by days with everything done, or by a streak of days in a row. */
data class LearnPrize(val id: String, val skin: Boolean, val days: Int, val streak: Boolean) {
    val label: String get() = if (streak) "$days Tage am Stück" else "$days Lerntage"
}

object Learn {
    const val MIN_GRADE = 5
    const val MAX_GRADE = 13
    const val COINS_FIRST_TRY = 2
    const val COINS_LATER = 1
    const val COINS_SUBJECT = 10
    const val COINS_DAY = 25
    const val XP_DAY = 30

    val prizes: List<LearnPrize> = listOf(
        LearnPrize("hat_mortarboard", skin = false, days = 3, streak = false),
        LearnPrize("skin_bookworm", skin = true, days = 7, streak = true),
        LearnPrize("face_labgoggles", skin = false, days = 14, streak = false),
        LearnPrize("skin_inventor", skin = true, days = 30, streak = true),
        LearnPrize("back_bookbag", skin = false, days = 50, streak = false),
        LearnPrize("skin_stargazer", skin = true, days = 100, streak = true),
    )

    val skinIds: Set<String> get() = prizes.filter { it.skin }.map { it.id }.toSet()

    /** The next prize that is not won yet. */
    fun nextPrize(learn: LearnState): LearnPrize? = prizes.firstOrNull { it.id !in learn.prizes }

    /** How far the player is on the way to [prize] (done, needed). */
    fun progress(learn: LearnState, prize: LearnPrize): Pair<Int, Int> = (if (prize.streak) learn.streak else learn.doneDays).coerceAtMost(prize.days) to prize.days

    fun grade(settings: Settings, learn: LearnState, subject: Subject): Int {
        val base = settings.learnGrades[subject.name] ?: MIN_GRADE
        return (base + (learn.adjust[subject.name] ?: 0)).coerceIn(MIN_GRADE, MAX_GRADE)
    }

    fun solved(learn: LearnState, subject: Subject, today: Long): Int = if (learn.day == today) learn.solved[subject.name] ?: 0 else 0

    fun subjectDone(settings: Settings, learn: LearnState, subject: Subject, today: Long) = solved(learn, subject, today) >= settings.learnPerSubject

    fun allDone(settings: Settings, learn: LearnState, today: Long) = Subject.entries.all { subjectDone(settings, learn, it, today) }

    fun left(settings: Settings, learn: LearnState, today: Long): Int =
        Subject.entries.sumOf { (settings.learnPerSubject - solved(learn, it, today)).coerceAtLeast(0) }

    /** The task number [index] of [subject] on [day] (the same all day long, no task twice). */
    fun task(subject: Subject, grade: Int, day: Long, index: Int): LearnTask {
        val seen = HashSet<String>()
        var last = raw(subject, grade, day, 0, 0)
        for (i in 0..index) {
            var attempt = 0
            last = raw(subject, grade, day, i, 0)
            while (last.question in seen && attempt < 8) last = raw(subject, grade, day, i, ++attempt)
            seen += last.question
        }
        return last
    }

    private fun raw(subject: Subject, grade: Int, day: Long, index: Int, attempt: Int): LearnTask {
        val rnd = Random(day * 7_919L + subject.ordinal * 1_000_003L + index * 104_729L + grade * 31L + attempt * 15_485_863L)
        // mostly this grade, sometimes a repetition from the grades before
        val g = when {
            grade > MIN_GRADE && rnd.nextFloat() < 0.25f -> (grade - 1 - rnd.nextInt(2)).coerceAtLeast(MIN_GRADE)
            else -> grade
        }
        return when (subject) {
            Subject.MATHE -> MathTasks.make(g, rnd)
            Subject.DEUTSCH -> GermanTasks.make(g, rnd)
            Subject.PHYSIK -> PhysicsTasks.make(g, rnd)
        }
    }

    // ------------------------------------------------------------------ numbers

    /** German number formatting: comma, at most [decimals] places, no trailing zeros. */
    fun fmt(v: Double, decimals: Int = 2): String {
        val f = Math.pow(10.0, decimals.toDouble())
        val r = round(v * f) / f
        if (abs(r - round(r)) < 1e-9) return round(r).toLong().toString()
        var s = String.format(java.util.Locale.ROOT, "%.${decimals}f", r).trimEnd('0').trimEnd('.')
        s = s.replace('.', ',')
        return s
    }

    fun normalize(s: String): String = s.trim().lowercase().replace(" ", "").replace('.', ',')

    /** Reads "12", "-3,5", "2.5" or "3/4"; null if it is not a number. */
    fun number(text: String): Double? {
        val t = text.trim().replace(" ", "").replace(',', '.').replace('−', '-')
        if (t.isEmpty()) return null
        if (t.contains('/')) {
            val parts = t.split('/')
            if (parts.size != 2) return null
            val a = parts[0].toDoubleOrNull() ?: return null
            val b = parts[1].toDoubleOrNull() ?: return null
            if (b == 0.0) return null
            return a / b
        }
        return t.toDoubleOrNull()
    }

    /** Exponent as superscript: sup(3) = "³". */
    fun sup(n: Int): String = n.toString().map { c -> if (c == '-') '⁻' else "⁰¹²³⁴⁵⁶⁷⁸⁹"[c - '0'] }.joinToString("")

    /** A number for a sum, negative ones in brackets: (−3). */
    fun term(n: Int): String = if (n < 0) "(−${-n})" else "$n"

    fun gcd(a: Int, b: Int): Int = if (b == 0) abs(a) else gcd(b, a % b)

    fun frac(n: Int, d: Int): String {
        val g = gcd(n, d).coerceAtLeast(1)
        val nn = n / g
        val dd = d / g
        return if (dd == 1) "$nn" else "$nn/$dd"
    }

    /** Multiple choice from a correct value and some wrong ones (unique, shuffled). */
    fun choices(correct: String, wrong: List<String>, rnd: Random, count: Int = 4): List<String> {
        val out = mutableListOf(correct)
        for (w in wrong.shuffled(rnd)) {
            if (out.size >= count) break
            if (w != correct && w !in out) out += w
        }
        return out.shuffled(rnd)
    }

    fun numberChoices(correct: Double, rnd: Random, decimals: Int = 2, unit: String = "", extra: List<Double> = emptyList()): Pair<String, List<String>> {
        fun f(v: Double) = fmt(v, decimals) + if (unit.isEmpty()) "" else " $unit"
        val c = f(correct)
        val near = if (correct < 20) listOf(correct + 1, correct - 1, correct + 2) else emptyList()
        val wrong = (extra + listOf(correct * 10, correct / 10, correct * 2, correct / 2, correct * 3, correct * 1.5) + near)
            .filter { it > 0 && abs(it - correct) > 1e-9 }.map { f(it) }.filter { it != f(0.0) }
        return c to choices(c, wrong, rnd)
    }
}

// ====================================================================================== Mathe

private object MathTasks {
    /** "3x", "x", "−x", "−3x". */
    private fun coef(m: Int, v: String = "x") = when (m) {
        1 -> v
        -1 -> "−$v"
        else -> if (m < 0) "−${-m}$v" else "$m$v"
    }

    /** " + 3", " − 3" (or nothing for 0). */
    private fun plus(b: Int, v: String = "") = when {
        b > 0 -> " + $b$v"
        b < 0 -> " − ${-b}$v"
        else -> ""
    }

    private fun nonZero(r: Random, from: Int, until: Int): Int {
        var v = 0
        while (v == 0) v = from + r.nextInt(until - from)
        return v
    }

    fun make(grade: Int, r: Random): LearnTask {
        val gens: List<(Random) -> LearnTask> = when (grade) {
            5 -> listOf(::multiply, ::bigSum, ::order, ::remainder, ::units, ::rectangle, ::square)
            6 -> listOf(::fracAdd, ::fracMul, ::reduce, ::decimals, ::percentSimple, ::gcdTask, ::order)
            7 -> listOf(::negatives, ::discount, ::ruleOfThree, ::simpleEquation, ::triangleAngle, ::termValue)
            8 -> listOf(::linearEquation, ::slope, ::functionValue, ::binomial, ::circle, ::interest)
            9 -> listOf(::powers, ::roots, ::pythagoras, ::quadratic, ::probability, ::scientific)
            10 -> listOf(::trig, ::vertex, ::growth, ::cylinder, ::sphere, ::logTask)
            11 -> listOf(::derivative, ::extremum, ::logTask, ::tangentSlope, ::exponentialEq)
            12 -> listOf(::integral, ::integralLinear, ::vectorLength, ::dotProduct, ::derivative)
            else -> listOf(::binomialCoef, ::binomialProb, ::expectation, ::inflection, ::integral, ::factorial)
        }
        return gens[r.nextInt(gens.size)](r)
    }

    private fun t(topic: String, q: String, a: String, hint: String, unit: String = "", reduced: Boolean = false) =
        LearnTask(Subject.MATHE, topic, q, a, hint = hint, unit = unit, reduced = reduced)

    // ---- Klasse 5
    fun multiply(r: Random): LearnTask {
        val a = 12 + r.nextInt(240)
        val b = 3 + r.nextInt(27)
        val hint = if (b < 10 || b % 10 == 0) "Multipliziere Stelle für Stelle von rechts und merke dir die Überträge: $a · $b = ${a * b}."
        else "Rechne zuerst $a · ${b / 10 * 10} = ${a * (b / 10 * 10)} und dann $a · ${b % 10} = ${a * (b % 10)}. Zusammen: ${a * b}."
        return t("Schriftlich multiplizieren", "Berechne: $a · $b", "${a * b}", hint)
    }

    fun bigSum(r: Random): LearnTask {
        val a = 1000 + r.nextInt(9000)
        val b = 100 + r.nextInt(5000)
        return if (r.nextBoolean()) {
            t("Große Zahlen", "Berechne: $a + $b", "${a + b}", "Schreibe die Zahlen untereinander und addiere Stelle für Stelle von rechts. Ergebnis: ${a + b}.")
        } else {
            val big = a + b
            t("Große Zahlen", "Berechne: $big − $b", "$a", "Subtrahiere Stelle für Stelle von rechts und denke an den Übertrag. Ergebnis: $a.")
        }
    }

    fun order(r: Random): LearnTask {
        val a = 2 + r.nextInt(20)
        val b = 2 + r.nextInt(9)
        val c = 2 + r.nextInt(9)
        return if (r.nextBoolean()) {
            t("Punkt vor Strich", "Berechne: $a + $b · $c", "${a + b * c}", "Punkt vor Strich: erst $b · $c = ${b * c}, dann $a + ${b * c} = ${a + b * c}.")
        } else {
            t("Klammern", "Berechne: ($a + $b) · $c", "${(a + b) * c}", "Klammern zuerst: $a + $b = ${a + b}, dann · $c = ${(a + b) * c}.")
        }
    }

    fun remainder(r: Random): LearnTask {
        val d = 3 + r.nextInt(7)
        val q = 4 + r.nextInt(12)
        val rest = 1 + r.nextInt(d - 1)
        val n = d * q + rest
        return t("Division mit Rest", "$n : $d = $q Rest ?", "$rest", "$q · $d = ${q * d}. Bis $n fehlen noch $rest.")
    }

    fun units(r: Random): LearnTask = when (r.nextInt(4)) {
        0 -> {
            val m = (1 + r.nextInt(90)) / 10.0
            t("Einheiten", "${Learn.fmt(m)} m = ? cm", Learn.fmt(m * 100), "1 m = 100 cm, also ${Learn.fmt(m)} · 100 = ${Learn.fmt(m * 100)}.", "cm")
        }
        1 -> {
            val h = 1 + r.nextInt(4)
            val min = 5 * r.nextInt(12)
            t("Einheiten", "$h h $min min = ? min", "${h * 60 + min}", "1 h = 60 min: $h · 60 + $min = ${h * 60 + min}.", "min")
        }
        2 -> {
            val g = 250 * (1 + r.nextInt(20))
            t("Einheiten", "$g g = ? kg", Learn.fmt(g / 1000.0, 3), "1 kg = 1000 g, also $g : 1000 = ${Learn.fmt(g / 1000.0, 3)}.", "kg")
        }
        else -> {
            val l = 1 + r.nextInt(9)
            t("Einheiten", "$l l = ? ml", "${l * 1000}", "1 Liter = 1000 Milliliter.", "ml")
        }
    }

    fun rectangle(r: Random): LearnTask {
        val a = 3 + r.nextInt(15)
        val b = 2 + r.nextInt(12)
        return if (r.nextBoolean()) {
            t("Flächeninhalt", "Ein Rechteck ist $a cm lang und $b cm breit. Wie groß ist die Fläche?", "${a * b}", "Fläche = Länge · Breite = $a · $b = ${a * b}.", "cm²")
        } else {
            t("Umfang", "Ein Rechteck ist $a cm lang und $b cm breit. Wie groß ist der Umfang?", "${2 * (a + b)}", "Umfang = 2 · (Länge + Breite) = 2 · ${a + b} = ${2 * (a + b)}.", "cm")
        }
    }

    fun square(r: Random): LearnTask {
        val n = 4 + r.nextInt(17)
        return t("Quadratzahlen", "Berechne: $n²", "${n * n}", "$n² bedeutet $n · $n = ${n * n}.")
    }

    // ---- Klasse 6
    fun fracAdd(r: Random): LearnTask {
        val d = listOf(4, 6, 8, 10, 12)[r.nextInt(5)]
        val a = 1 + r.nextInt(d - 1)
        val b = 1 + r.nextInt(d - 1)
        val res = Learn.frac(a + b, d)
        return t("Brüche addieren", "Berechne: $a/$d + $b/$d (als Bruch, z. B. 3/4)", res, "Gleicher Nenner: Zähler addieren. ${a + b}/$d, gekürzt $res.")
    }

    fun fracMul(r: Random): LearnTask {
        val a = 1 + r.nextInt(5)
        val b = 2 + r.nextInt(6)
        val c = 1 + r.nextInt(5)
        val d = 2 + r.nextInt(6)
        val res = Learn.frac(a * c, b * d)
        return t("Brüche multiplizieren", "Berechne: $a/$b · $c/$d (als Bruch)", res, "Zähler mal Zähler, Nenner mal Nenner: ${a * c}/${b * d}, gekürzt $res.")
    }

    fun reduce(r: Random): LearnTask {
        val n = 1 + r.nextInt(6)
        var d = n + 1 + r.nextInt(7)
        while (Learn.gcd(n, d) != 1) d++
        val k = 2 + r.nextInt(7)
        return t("Brüche kürzen", "Kürze so weit wie möglich: ${n * k}/${d * k}", "$n/$d", "Zähler und Nenner durch $k teilen: ${n * k} : $k = $n und ${d * k} : $k = $d.", reduced = true)
    }

    fun decimals(r: Random): LearnTask {
        val a = (10 + r.nextInt(90)) / 10.0
        val b = (2 + r.nextInt(8)).toDouble()
        return if (r.nextBoolean()) {
            val tenth = Math.round(a * 10).toInt()
            t("Dezimalzahlen", "Berechne: ${Learn.fmt(a)} · ${Learn.fmt(b)}", Learn.fmt(a * b), "Rechne ohne Komma $tenth · ${b.toInt()} = ${tenth * b.toInt()} und setze dann eine Stelle nach dem Komma: ${Learn.fmt(a * b)}.")
        } else {
            val c = (10 + r.nextInt(90)) / 10.0
            t("Dezimalzahlen", "Berechne: ${Learn.fmt(a)} + ${Learn.fmt(c)}", Learn.fmt(a + c), "Komma unter Komma schreiben und addieren: ${Learn.fmt(a + c)}.")
        }
    }

    fun percentSimple(r: Random): LearnTask {
        val p = listOf(10, 20, 25, 50, 75)[r.nextInt(5)]
        val base = 20 * (1 + r.nextInt(15))
        val v = base * p / 100.0
        return t("Prozent", "Wie viel sind $p % von $base?", Learn.fmt(v), "$p % = $p/100. $base · $p : 100 = ${Learn.fmt(v)}.")
    }

    fun gcdTask(r: Random): LearnTask {
        val g = 2 + r.nextInt(9)
        val a = g * (1 + r.nextInt(6))
        var b = g * (1 + r.nextInt(6))
        if (a == b) b += g
        val res = Learn.gcd(a, b)
        return t("Größter gemeinsamer Teiler", "Wie heißt der ggT von $a und $b?", "$res", "Die größte Zahl, durch die sich $a und $b beide teilen lassen, ist $res.")
    }

    // ---- Klasse 7
    fun negatives(r: Random): LearnTask {
        val a = -(1 + r.nextInt(15))
        val b = 1 + r.nextInt(20)
        val c = -(1 + r.nextInt(10))
        val res = a + b - c
        return t("Negative Zahlen", "Berechne: (−${-a}) + $b − (−${-c})", "$res", "Minus eine negative Zahl wird Plus: − (−${-c}) = + ${-c}. Also −${-a} + $b + ${-c} = $res.")
    }

    fun discount(r: Random): LearnTask {
        val price = 20 * (2 + r.nextInt(10))
        val p = listOf(10, 15, 20, 25, 30, 40)[r.nextInt(6)]
        val v = price * (100 - p) / 100.0
        return t("Prozentrechnung", "Eine Jacke kostet $price €. Sie wird um $p % billiger. Was kostet sie jetzt?", Learn.fmt(v), "$p % von $price € sind ${Learn.fmt(price * p / 100.0)} €. $price − ${Learn.fmt(price * p / 100.0)} = ${Learn.fmt(v)}.", "€")
    }

    fun ruleOfThree(r: Random): LearnTask {
        val n = 2 + r.nextInt(4)
        val unitPrice = (5 + r.nextInt(30)) / 10.0
        val m = n + 1 + r.nextInt(6)
        return t("Dreisatz", "$n Hefte kosten ${Learn.fmt(n * unitPrice)} €. Was kosten $m Hefte?", Learn.fmt(m * unitPrice), "Ein Heft kostet ${Learn.fmt(n * unitPrice)} : $n = ${Learn.fmt(unitPrice)} €. Mal $m = ${Learn.fmt(m * unitPrice)} €.", "€")
    }

    fun simpleEquation(r: Random): LearnTask {
        val x = 2 + r.nextInt(18)
        return if (r.nextBoolean()) {
            val a = 3 + r.nextInt(20)
            t("Gleichungen", "Löse: x + $a = ${x + a}", "$x", "Auf beiden Seiten $a abziehen: x = ${x + a} − $a = $x.")
        } else {
            val a = 2 + r.nextInt(8)
            t("Gleichungen", "Löse: $a · x = ${a * x}", "$x", "Beide Seiten durch $a teilen: x = ${a * x} : $a = $x.")
        }
    }

    fun triangleAngle(r: Random): LearnTask {
        val a = 30 + r.nextInt(60)
        val b = 20 + r.nextInt(150 - a - 20)
        return t("Winkel im Dreieck", "In einem Dreieck ist α = $a° und β = $b°. Wie groß ist γ?", "${180 - a - b}", "Die Winkel im Dreieck ergeben zusammen 180°: 180 − $a − $b = ${180 - a - b}.", "°")
    }

    fun termValue(r: Random): LearnTask {
        val x = 2 + r.nextInt(5)
        val a = 2 + r.nextInt(4)
        val b = 1 + r.nextInt(9)
        return t("Terme", "Setze x = $x ein: $a·x² − $b", "${a * x * x - b}", "$a · $x² = $a · ${x * x} = ${a * x * x}, dann − $b = ${a * x * x - b}.")
    }

    // ---- Klasse 8
    fun linearEquation(r: Random): LearnTask {
        val x = -5 + r.nextInt(16)
        val a = 2 + r.nextInt(7)
        val b = -10 + r.nextInt(21)
        val c = a * x + b
        val step = when {
            b > 0 -> "Erst auf beiden Seiten $b abziehen"
            b < 0 -> "Erst auf beiden Seiten ${-b} addieren"
            else -> "Hier gibt es nichts abzuziehen"
        }
        return t("Gleichungen lösen", "Löse: ${a}x${plus(b)} = $c", "$x", "$step: ${a}x = ${c - b}. Dann durch $a teilen: x = $x.")
    }

    fun slope(r: Random): LearnTask {
        val m = -4 + r.nextInt(9)
        val x1 = r.nextInt(4)
        val y1 = -3 + r.nextInt(7)
        val dx = 1 + r.nextInt(4)
        return t("Lineare Funktionen", "Eine Gerade geht durch P($x1|$y1) und Q(${x1 + dx}|${y1 + m * dx}). Wie groß ist die Steigung m?", "$m", "m = (y₂ − y₁) : (x₂ − x₁) = ${m * dx} : $dx = $m.")
    }

    fun functionValue(r: Random): LearnTask {
        val m = nonZero(r, -3, 4)
        val b = -5 + r.nextInt(11)
        val x = -3 + r.nextInt(7)
        return t("Lineare Funktionen", "f(x) = ${coef(m)}${plus(b)}. Berechne f($x).", "${m * x + b}", "x = $x einsetzen: $m · ($x)${plus(b)} = ${m * x + b}.")
    }

    fun binomial(r: Random): LearnTask {
        val base = 10 * (2 + r.nextInt(8))
        val d = 1 + r.nextInt(3)
        val plus = r.nextBoolean()
        val n = if (plus) base + d else base - d
        return t("Binomische Formeln", "Berechne geschickt: $n²", "${n * n}", "(${base} ${if (plus) "+" else "−"} $d)² = ${base * base} ${if (plus) "+" else "−"} ${2 * base * d} + ${d * d} = ${n * n}.")
    }

    fun circle(r: Random): LearnTask {
        val rad = 2 + r.nextInt(9)
        return if (r.nextBoolean()) {
            t("Kreis", "Ein Kreis hat den Radius $rad cm. Wie groß ist seine Fläche? (auf 1 Nachkommastelle, π ≈ 3,14)", Learn.fmt(3.14 * rad * rad, 1), "A = π · r² ≈ 3,14 · ${rad * rad} = ${Learn.fmt(3.14 * rad * rad, 2)}.", "cm²")
        } else {
            t("Kreis", "Ein Kreis hat den Radius $rad cm. Wie groß ist sein Umfang? (π ≈ 3,14)", Learn.fmt(2 * 3.14 * rad, 2), "U = 2 · π · r ≈ 2 · 3,14 · $rad = ${Learn.fmt(2 * 3.14 * rad, 2)}.", "cm")
        }
    }

    fun interest(r: Random): LearnTask {
        val k = 100 * (2 + r.nextInt(20))
        val p = 1 + r.nextInt(5)
        return t("Zinsrechnung", "Du legst $k € ein. Die Bank zahlt $p % Zinsen im Jahr. Wie viele Zinsen bekommst du nach einem Jahr?", Learn.fmt(k * p / 100.0), "Zinsen = Kapital · p : 100 = $k · $p : 100 = ${Learn.fmt(k * p / 100.0)}.", "€")
    }

    // ---- Klasse 9
    fun powers(r: Random): LearnTask {
        val a = 2 + r.nextInt(4)
        val b = 2 + r.nextInt(5)
        return if (r.nextBoolean()) {
            t("Potenzen", "2${Learn.sup(a)} · 2${Learn.sup(b)} = 2 hoch wie viel? (gib den Exponenten an)", "${a + b}", "Gleiche Basis: Exponenten addieren, $a + $b = ${a + b}.")
        } else {
            val base = 2 + r.nextInt(4)
            val e = 2 + r.nextInt(3)
            var v = 1
            repeat(e) { v *= base }
            t("Potenzen", "Berechne: $base${Learn.sup(e)}", "$v", "$base${Learn.sup(e)} bedeutet $e-mal $base miteinander malnehmen: $v.")
        }
    }

    fun roots(r: Random): LearnTask {
        val n = 2 + r.nextInt(19)
        return t("Wurzeln", "Berechne: √${n * n}", "$n", "$n · $n = ${n * n}, also ist √${n * n} = $n.")
    }

    fun pythagoras(r: Random): LearnTask {
        val triples = listOf(intArrayOf(3, 4, 5), intArrayOf(5, 12, 13), intArrayOf(8, 15, 17), intArrayOf(6, 8, 10), intArrayOf(7, 24, 25), intArrayOf(9, 12, 15))
        val tr = triples[r.nextInt(triples.size)]
        return t("Satz des Pythagoras", "Ein rechtwinkliges Dreieck hat die Katheten ${tr[0]} cm und ${tr[1]} cm. Wie lang ist die Hypotenuse?", "${tr[2]}", "c² = a² + b² = ${tr[0] * tr[0]} + ${tr[1] * tr[1]} = ${tr[2] * tr[2]}, also c = √${tr[2] * tr[2]} = ${tr[2]}.", "cm")
    }

    fun quadratic(r: Random): LearnTask {
        val a = 1 + r.nextInt(7)
        val b = a + 1 + r.nextInt(6)
        val p = a + b
        val q = a * b
        return t("Quadratische Gleichungen", "Löse x² − ${p}x + $q = 0. Wie heißt die größere Lösung?", "$b", "Gesucht sind zwei Zahlen mit Summe $p und Produkt $q: $a und $b. Also (x − $a)(x − $b) = 0.")
    }

    fun probability(r: Random): LearnTask = when (r.nextInt(3)) {
        0 -> t("Wahrscheinlichkeit", "Ein Würfel wird einmal geworfen. Wie groß ist die Wahrscheinlichkeit für eine gerade Zahl? (als Bruch)", "1/2", "Gerade Zahlen: 2, 4, 6. Das sind 3 von 6 Möglichkeiten = 1/2.")
        1 -> t("Wahrscheinlichkeit", "Zwei Würfel werden geworfen. Wie groß ist die Wahrscheinlichkeit, dass die Summe 7 ist? (als Bruch)", "1/6", "Es gibt 36 Paare, 6 davon ergeben 7 (1+6, 2+5, …). 6/36 = 1/6.")
        else -> {
            val red = 2 + r.nextInt(6)
            val blue = 2 + r.nextInt(6)
            t("Wahrscheinlichkeit", "In einer Tüte sind $red rote und $blue blaue Kugeln. Wie groß ist die Wahrscheinlichkeit, eine rote zu ziehen? (als Bruch)", Learn.frac(red, red + blue), "Günstige durch mögliche: $red/${red + blue} = ${Learn.frac(red, red + blue)}.")
        }
    }

    fun scientific(r: Random): LearnTask {
        val e = 3 + r.nextInt(6)
        val m = 1 + r.nextInt(9)
        var v = m.toLong()
        repeat(e) { v *= 10 }
        return t("Zehnerpotenzen", "Schreibe als Zahl: $m · 10${Learn.sup(e)}", "$v", "10${Learn.sup(e)} ist eine 1 mit $e Nullen. $m · 10${Learn.sup(e)} = $v.")
    }

    // ---- Klasse 10
    fun trig(r: Random): LearnTask = when (r.nextInt(3)) {
        0 -> {
            val h = 2 * (2 + r.nextInt(8))
            t("Trigonometrie", "Rechtwinkliges Dreieck: Hypotenuse $h cm, Winkel α = 30°. Wie lang ist die Gegenkathete von α?", "${h / 2}", "sin(30°) = 0,5. Gegenkathete = Hypotenuse · sin(α) = $h · 0,5 = ${h / 2}.", "cm")
        }
        1 -> t("Trigonometrie", "Wie groß ist cos(60°)?", "0,5", "Im gleichseitigen Dreieck halbiert die Höhe die Seite: cos(60°) = 1/2 = 0,5.")
        else -> t("Trigonometrie", "Wie groß ist tan(45°)?", "1", "Bei 45° sind beide Katheten gleich lang, tan = Gegenkathete : Ankathete = 1.")
    }

    fun vertex(r: Random): LearnTask {
        val a = nonZero(r, -5, 6)
        val b = nonZero(r, -6, 7)
        val asx = if (a > 0) "x − $a" else "x + ${-a}"
        return if (r.nextBoolean()) {
            t("Quadratische Funktionen", "f(x) = ($asx)²${plus(b)}. Welche x-Koordinate hat der Scheitelpunkt?", "$a", "In der Scheitelpunktform f(x) = (x − d)² + e liegt der Scheitel bei S(d|e). Hier ist d = $a (Vorzeichen umdrehen!).")
        } else {
            t("Quadratische Funktionen", "f(x) = ($asx)²${plus(b)}. Welche y-Koordinate hat der Scheitelpunkt?", "$b", "In der Scheitelpunktform f(x) = (x − d)² + e liegt der Scheitel bei S(d|e). Hier ist e = $b.")
        }
    }

    fun growth(r: Random): LearnTask {
        val start = 100 * (1 + r.nextInt(5))
        val n = 2 + r.nextInt(4)
        var v = start.toLong()
        repeat(n) { v *= 2 }
        return t("Exponentielles Wachstum", "Eine Bakterienkultur mit $start Bakterien verdoppelt sich jede Stunde. Wie viele sind es nach $n Stunden?", "$v", "$start · 2${Learn.sup(n)} = $start · ${1 shl n} = $v.")
    }

    fun cylinder(r: Random): LearnTask {
        val rad = 1 + r.nextInt(5)
        val h = 2 + r.nextInt(9)
        val v = 3.14 * rad * rad * h
        return t("Volumen", "Ein Zylinder hat den Radius $rad cm und die Höhe $h cm. Wie groß ist sein Volumen? (π ≈ 3,14, auf 1 Nachkommastelle)", Learn.fmt(v, 1), "V = π · r² · h ≈ 3,14 · ${rad * rad} · $h = ${Learn.fmt(v, 2)}.", "cm³")
    }

    fun sphere(r: Random): LearnTask {
        val rad = 1 + r.nextInt(4)
        val v = 4.0 / 3.0 * 3.14 * rad * rad * rad
        return t("Volumen", "Wie groß ist das Volumen einer Kugel mit Radius $rad cm? (π ≈ 3,14, auf 1 Nachkommastelle)", Learn.fmt(v, 1), "V = 4/3 · π · r³ ≈ 4/3 · 3,14 · ${rad * rad * rad} = ${Learn.fmt(v, 2)}.", "cm³")
    }

    fun logTask(r: Random): LearnTask = if (r.nextBoolean()) {
        val e = 2 + r.nextInt(8)
        t("Logarithmen", "Berechne: log₂(${1 shl e})", "$e", "2 hoch wie viel ergibt ${1 shl e}? 2^$e = ${1 shl e}, also log₂(${1 shl e}) = $e.")
    } else {
        val e = 1 + r.nextInt(6)
        var v = 1L
        repeat(e) { v *= 10 }
        t("Logarithmen", "Berechne: lg($v)", "$e", "10^$e = $v, also lg($v) = $e.")
    }

    // ---- Klasse 11 – 13
    fun derivative(r: Random): LearnTask {
        val a = 1 + r.nextInt(5)
        val n = 2 + r.nextInt(3)
        val x0 = 1 + r.nextInt(3)
        var p = 1
        repeat(n - 1) { p *= x0 }
        val res = a * n * p
        val inner = if (n == 2) "${a * n}x" else "${a * n}x${Learn.sup(n - 1)}"
        val value = if (n == 2) "${a * n} · $x0" else "${a * n} · $x0${Learn.sup(n - 1)}"
        return t("Ableitungen", "f(x) = ${if (a == 1) "" else "$a"}x${Learn.sup(n)}. Berechne f′($x0).", "$res", "Potenzregel: f′(x) = $inner. f′($x0) = $value = $res.")
    }

    fun extremum(r: Random): LearnTask {
        val a = nonZero(r, -4, 5)
        val c = r.nextInt(10)
        return t("Extremstellen", "f(x) = x²${plus(-2 * a, "x")}${plus(c)}. An welcher Stelle x liegt der Tiefpunkt?", "$a", "Ableiten und null setzen: f′(x) = 2x${plus(-2 * a)} = 0 ⇒ x = $a.")
    }

    fun tangentSlope(r: Random): LearnTask {
        val x0 = -3 + r.nextInt(7)
        val b = nonZero(r, -4, 5)
        return t("Tangentensteigung", "Wie groß ist die Steigung von f(x) = x²${plus(b, "x")} an der Stelle x = $x0?", "${2 * x0 + b}", "Die Steigung ist die Ableitung: f′(x) = 2x${plus(b)}, also f′($x0) = ${2 * x0 + b}.")
    }

    fun exponentialEq(r: Random): LearnTask {
        val base = listOf(2, 3, 5)[r.nextInt(3)]
        val e = 2 + r.nextInt(4)
        var v = 1
        repeat(e) { v *= base }
        return t("Exponentialgleichungen", "Löse: $base hoch x = $v", "$e", "$base${Learn.sup(e)} = $v, also x = $e.")
    }

    fun integral(r: Random): LearnTask {
        val b = 1 + r.nextInt(4)
        val res = b * b * b
        return t("Integralrechnung", "Berechne: ∫ von 0 bis $b über 3x² dx", "$res", "Stammfunktion von 3x² ist x³. ${b}³ − 0³ = $res.")
    }

    fun integralLinear(r: Random): LearnTask {
        val b = 1 + r.nextInt(6)
        val c = 1 + r.nextInt(5)
        return t("Integralrechnung", "Berechne: ∫ von 0 bis $b über (2x + $c) dx", "${b * b + c * b}", "Stammfunktion: x² + ${c}x. Bei $b: ${b * b} + ${c * b} = ${b * b + c * b}.")
    }

    fun vectorLength(r: Random): LearnTask {
        val q = listOf(intArrayOf(1, 2, 2, 3), intArrayOf(2, 3, 6, 7), intArrayOf(1, 4, 8, 9), intArrayOf(2, 6, 9, 11), intArrayOf(4, 4, 7, 9), intArrayOf(2, 10, 11, 15))
        val v = q[r.nextInt(q.size)]
        return t("Vektoren", "Wie lang ist der Vektor (${v[0]} | ${v[1]} | ${v[2]})?", "${v[3]}", "|v| = √(${v[0]}² + ${v[1]}² + ${v[2]}²) = √${v[3] * v[3]} = ${v[3]}.")
    }

    fun dotProduct(r: Random): LearnTask {
        val a = IntArray(3) { -3 + r.nextInt(7) }
        val b = IntArray(3) { -3 + r.nextInt(7) }
        val res = a[0] * b[0] + a[1] * b[1] + a[2] * b[2]
        val sum = (0..2).joinToString(" + ") { "${Learn.term(a[it])} · ${Learn.term(b[it])}" }
        return t("Skalarprodukt", "Berechne das Skalarprodukt von (${a[0]} | ${a[1]} | ${a[2]}) und (${b[0]} | ${b[1]} | ${b[2]}).", "$res", "Komponentenweise malnehmen und addieren: $sum = $res.")
    }

    fun binomialCoef(r: Random): LearnTask {
        val n = 4 + r.nextInt(5)
        val k = 2 + r.nextInt(2)
        var num = 1L
        var den = 1L
        for (i in 0 until k) {
            num *= (n - i)
            den *= (i + 1)
        }
        return t("Kombinatorik", "Wie viele Möglichkeiten gibt es, $k aus $n auszuwählen? ($n über $k)", "${num / den}", "($n über $k) = $n! : ($k! · ${n - k}!) = ${num / den}.")
    }

    fun binomialProb(r: Random): LearnTask {
        val opts = listOf(Triple(2, 1, "0,5"), Triple(3, 1, "0,375"), Triple(4, 2, "0,375"), Triple(4, 1, "0,25"), Triple(3, 0, "0,125"))
        val (n, k, a) = opts[r.nextInt(opts.size)]
        return t("Binomialverteilung", "Eine Münze wird $n-mal geworfen. Wie groß ist P(genau $k-mal Kopf)? (als Dezimalzahl)", a, "P = ($n über $k) · 0,5${Learn.sup(n)}.")
    }

    fun expectation(r: Random): LearnTask {
        val n = 10 * (1 + r.nextInt(10))
        val p = listOf(0.1, 0.2, 0.25, 0.5)[r.nextInt(4)]
        return t("Erwartungswert", "Ein Versuch wird $n-mal wiederholt, die Trefferwahrscheinlichkeit ist ${Learn.fmt(p)}. Wie viele Treffer erwartest du?", Learn.fmt(n * p), "Erwartungswert = n · p = $n · ${Learn.fmt(p)} = ${Learn.fmt(n * p)}.")
    }

    fun inflection(r: Random): LearnTask {
        val a = 1 + r.nextInt(5)
        return t("Kurvendiskussion", "f(x) = x³ − ${3 * a}x². An welcher Stelle x liegt der Wendepunkt?", "$a", "f″(x) = 6x − ${6 * a} = 0 ⇒ x = $a.")
    }

    fun factorial(r: Random): LearnTask {
        val n = 3 + r.nextInt(5)
        var v = 1
        for (i in 2..n) v *= i
        return t("Kombinatorik", "Auf wie viele Arten kann man $n verschiedene Bücher nebeneinander stellen?", "$v", "$n! = ${(1..n).joinToString(" · ")} = $v.")
    }
}

// ====================================================================================== Deutsch

private object GermanTasks {
    private fun t(topic: String, q: String, a: String, wrong: List<String>, hint: String, r: Random) =
        LearnTask(Subject.DEUTSCH, topic, q, a, Learn.choices(a, wrong, r), hint)

    fun make(grade: Int, r: Random): LearnTask {
        val gens: List<(Random) -> LearnTask> = when (grade) {
            5, 6 -> listOf(::wordClass, ::dasDass, ::spelling, ::tense, ::preterite, ::plural, ::caseSimple)
            7, 8 -> listOf(::activePassive, ::cases, ::figuresEasy, ::foreignWords, ::konjunktiv1, ::comma, ::preterite)
            9, 10 -> listOf(::figures, ::konjunktiv2, ::textTypes, ::clauses, ::epochsEasy, ::comma)
            else -> listOf(::epochs, ::authors, ::rhetoric, ::grammarTerms, ::meter, ::rhyme)
        }
        return gens[r.nextInt(gens.size)](r)
    }

    private val nouns = listOf("Haus", "Freude", "Baum", "Schule", "Fahrrad", "Wolke", "Mut", "Brücke", "Spiel", "Freundschaft", "Katze", "Gedanke", "Sonne", "Musik", "Abenteuer")
    private val verbs = listOf("laufen", "denken", "springen", "lesen", "schwimmen", "lachen", "bauen", "fliegen", "rechnen", "träumen", "erzählen", "malen")
    private val adjectives = listOf("schnell", "freundlich", "bunt", "leise", "mutig", "groß", "weich", "klug", "hell", "neugierig", "fröhlich", "warm")
    private val prepositions = listOf("unter", "neben", "hinter", "zwischen", "über", "durch", "ohne", "mit", "gegen", "trotz")

    fun wordClass(r: Random): LearnTask {
        val k = r.nextInt(4)
        val (word, cls) = when (k) {
            0 -> nouns[r.nextInt(nouns.size)] to "Nomen"
            1 -> verbs[r.nextInt(verbs.size)] to "Verb"
            2 -> adjectives[r.nextInt(adjectives.size)] to "Adjektiv"
            else -> prepositions[r.nextInt(prepositions.size)] to "Präposition"
        }
        return t("Wortarten", "Welche Wortart ist „$word“?", cls, listOf("Nomen", "Verb", "Adjektiv", "Präposition"), when (cls) {
            "Nomen" -> "Nomen sind Namen für Dinge, Lebewesen oder Gefühle und werden großgeschrieben."
            "Verb" -> "Verben sagen, was jemand tut oder was passiert. Man kann sie beugen: ich ${word.removeSuffix("en")}e …"
            "Adjektiv" -> "Adjektive beschreiben, wie jemand oder etwas ist. Man kann „sehr“ davorsetzen: sehr $word."
            else -> "Präpositionen zeigen Beziehungen an, z. B. wo etwas ist: unter dem Tisch."
        }, r)
    }

    private val dasSentences = listOf(
        "Ich glaube, ___ es morgen regnet." to "dass",
        "___ Buch liegt auf dem Tisch." to "das",
        "Das ist das Haus, ___ mir am besten gefällt." to "das",
        "Er freut sich, ___ du kommst." to "dass",
        "Weißt du, ___ Pinguine nicht fliegen können?" to "dass",
        "Hast du ___ Lied gehört?" to "das",
        "Das Fahrrad, ___ ich mir wünsche, ist rot." to "das",
        "Es ist schön, ___ wir Ferien haben." to "dass",
        "Ich hoffe, ___ das Spiel bald anfängt." to "dass",
        "___ ist meine Lieblingsfarbe." to "das",
    )

    fun dasDass(r: Random): LearnTask {
        val (s, a) = dasSentences[r.nextInt(dasSentences.size)]
        return t("das oder dass", "Setze ein: $s", a, listOf("das", "dass"), "Kann man „dieses“ oder „welches“ einsetzen, schreibt man „das“. Sonst ist es die Konjunktion „dass“.", r)
    }

    private val spellings = listOf(
        listOf("Fahrrad", "Farrad", "Fahrad"), listOf("Maschine", "Maschiene", "Mashine"), listOf("Rhythmus", "Rythmus", "Rhytmus"),
        listOf("wieder (noch einmal)", "wider (noch einmal)", "wiedder (noch einmal)"), listOf("Gefäß", "Gefäss", "Gefes"),
        listOf("nämlich", "nähmlich", "nemlich"), listOf("Standpunkt", "Stantpunkt", "Standpunckt"), listOf("Libelle", "Lieblle", "Libele"),
        listOf("endlich", "entlich", "endtlich"), listOf("Stadt (Ort)", "Statt (Ort)", "Staadt (Ort)"), listOf("Interesse", "Intresse", "Interresse"),
        listOf("Paket", "Packet", "Pakett"), listOf("Gelände", "Gelende", "Geländte"), listOf("Lexikon", "Lexicon", "Lexiekon"),
    )

    fun spelling(r: Random): LearnTask {
        val s = spellings[r.nextInt(spellings.size)]
        return t("Rechtschreibung", "Welches Wort ist richtig geschrieben?", s[0], s.drop(1), "Richtig ist „${s[0]}“. Merkwörter am besten oft aufschreiben!", r)
    }

    private val tenses = listOf(
        "Ich gehe ins Kino." to "Präsens", "Ich ging ins Kino." to "Präteritum", "Ich bin ins Kino gegangen." to "Perfekt",
        "Ich war ins Kino gegangen." to "Plusquamperfekt", "Ich werde ins Kino gehen." to "Futur I",
        "Wir spielten Fußball." to "Präteritum", "Wir haben Fußball gespielt." to "Perfekt", "Sie liest ein Buch." to "Präsens",
    )

    fun tense(r: Random): LearnTask {
        val (s, a) = tenses[r.nextInt(tenses.size)]
        return t("Zeitformen", "In welcher Zeitform steht: „$s“", a, listOf("Präsens", "Präteritum", "Perfekt", "Plusquamperfekt", "Futur I"), "Perfekt: haben/sein + Partizip (gegangen). Präteritum: eine Form (ging). Plusquamperfekt: war/hatte + Partizip.", r)
    }

    private val irregular = listOf(
        listOf("gehen", "ging", "gehte", "gang"), listOf("sehen", "sah", "sehte", "sach"), listOf("laufen", "lief", "laufte", "lauf"),
        listOf("schwimmen", "schwamm", "schwimmte", "schwomm"), listOf("finden", "fand", "findete", "fund"), listOf("singen", "sang", "singte", "sung"),
        listOf("schreiben", "schrieb", "schreibte", "schrub"), listOf("denken", "dachte", "denkte", "dank"), listOf("bringen", "brachte", "bringte", "brang"),
        listOf("fliegen", "flog", "fliegte", "flug"), listOf("trinken", "trank", "trinkte", "trunk"), listOf("werfen", "warf", "werfte", "wurf"),
    )

    fun preterite(r: Random): LearnTask {
        val v = irregular[r.nextInt(irregular.size)]
        return t("Präteritum", "Wie lautet das Präteritum von „${v[0]}“? (er/sie/es …)", v[1], v.drop(2), "„${v[0]}“ ist unregelmäßig: er ${v[1]}. Solche Verben ändern im Präteritum den Vokal, deshalb muss man sie lernen.", r)
    }

    private val plurals = listOf(
        listOf("Maus", "Mäuse", "Mause", "Mäuser"), listOf("Buch", "Bücher", "Buche", "Buchs"), listOf("Baum", "Bäume", "Baume", "Bäumer"),
        listOf("Kind", "Kinder", "Kinde", "Kinds"), listOf("Auto", "Autos", "Auten", "Autoe"), listOf("Hand", "Hände", "Handen", "Hander"),
        listOf("Museum", "Museen", "Museums", "Museume"), listOf("Bus", "Busse", "Buse", "Büsse"), listOf("Nuss", "Nüsse", "Nusse", "Nüssen"),
    )

    fun plural(r: Random): LearnTask {
        val p = plurals[r.nextInt(plurals.size)]
        val all = p.drop(1)
        return t("Mehrzahl", "Wie heißt die Mehrzahl von „${p[0]}“?", p[1], all.drop(1), "Die Mehrzahl von „${p[0]}“ ist „${p[1]}“.", r)
    }

    private val subjects = listOf(
        Triple("Der kleine Hund bellt laut.", "Der kleine Hund", listOf("bellt", "laut")),
        Triple("Am Morgen trinkt meine Oma Tee.", "meine Oma", listOf("Tee", "Am Morgen")),
        Triple("Die Kinder spielen im Garten.", "Die Kinder", listOf("im Garten", "spielen")),
        Triple("Heute schenkt Lena ihrem Bruder ein Buch.", "Lena", listOf("ihrem Bruder", "ein Buch")),
    )

    fun caseSimple(r: Random): LearnTask {
        val (s, a, w) = subjects[r.nextInt(subjects.size)]
        return t("Satzglieder", "Was ist das Subjekt in: „$s“", a, w, "Frage „Wer oder was …?“. Die Antwort ist das Subjekt: „$a“.", r)
    }

    // ---- Klasse 7/8
    private val voice = listOf(
        "Der Ball wird von Tom geworfen." to "Passiv", "Tom wirft den Ball." to "Aktiv", "Das Fenster wurde geöffnet." to "Passiv",
        "Die Lehrerin erklärt die Aufgabe." to "Aktiv", "Der Kuchen ist gebacken worden." to "Passiv", "Wir bauen ein Baumhaus." to "Aktiv",
    )

    fun activePassive(r: Random): LearnTask {
        val (s, a) = voice[r.nextInt(voice.size)]
        return t("Aktiv und Passiv", "Steht der Satz im Aktiv oder Passiv? „$s“", a, listOf("Aktiv", "Passiv"), "Passiv erkennst du an „werden“ + Partizip (wird geworfen). Im Aktiv handelt das Subjekt selbst.", r)
    }

    private val caseItems = listOf(
        Triple("Ich gebe dem Lehrer das Heft.", "dem Lehrer", "Dativ"), Triple("Ich sehe den Hund.", "den Hund", "Akkusativ"),
        Triple("Das ist das Fahrrad meines Bruders.", "meines Bruders", "Genitiv"), Triple("Der Vogel singt.", "Der Vogel", "Nominativ"),
        Triple("Wir helfen der Nachbarin.", "der Nachbarin", "Dativ"), Triple("Sie kauft einen Apfel.", "einen Apfel", "Akkusativ"),
    )

    fun cases(r: Random): LearnTask {
        val (s, part, a) = caseItems[r.nextInt(caseItems.size)]
        return t("Fälle", "In welchem Fall steht „$part“? „$s“", a, listOf("Nominativ", "Genitiv", "Dativ", "Akkusativ"), "Frageprobe: Wer? (Nominativ), Wessen? (Genitiv), Wem? (Dativ), Wen? (Akkusativ).", r)
    }

    private val figuresEasyList = listOf(
        "Er ist stark wie ein Bär." to "Vergleich", "Die Sonne lacht vom Himmel." to "Personifikation", "Milch macht müde Männer munter." to "Alliteration",
        "Sie hat ein Herz aus Gold." to "Metapher", "Ich habe es dir schon tausendmal gesagt!" to "Übertreibung", "Der Wind flüstert in den Bäumen." to "Personifikation",
    )

    fun figuresEasy(r: Random): LearnTask {
        val (s, a) = figuresEasyList[r.nextInt(figuresEasyList.size)]
        return t("Sprachliche Bilder", "Welches Stilmittel ist das? „$s“", a, listOf("Vergleich", "Personifikation", "Alliteration", "Metapher", "Übertreibung"), "Vergleich: mit „wie“. Personifikation: Dinge handeln wie Menschen. Alliteration: gleiche Anfangsbuchstaben. Metapher: Bild ohne „wie“.", r)
    }

    private val foreign = listOf(
        listOf("Diskussion", "Gespräch, bei dem man Meinungen austauscht", "ein lautes Fest", "eine Rechenaufgabe"),
        listOf("Respekt", "Achtung vor anderen", "große Angst", "ein Werkzeug"), listOf("Hobby", "Freizeitbeschäftigung", "eine Krankheit", "ein Möbelstück"),
        listOf("Expedition", "Forschungsreise", "Ausstellung", "Prüfung"), listOf("Konflikt", "Streit, Auseinandersetzung", "Einladung", "Wettervorhersage"),
        listOf("Kompromiss", "Einigung, bei der beide nachgeben", "ein Gewinn", "ein Fehler"), listOf("Tradition", "Brauch, der weitergegeben wird", "eine Erfindung", "ein Zufall"),
    )

    fun foreignWords(r: Random): LearnTask {
        val f = foreign[r.nextInt(foreign.size)]
        return t("Fremdwörter", "Was bedeutet „${f[0]}“?", f[1], f.drop(2), "„${f[0]}“ bedeutet: ${f[1]}.", r)
    }

    private val k1 = listOf(
        Triple("Er sagt, er ___ krank.", "sei", listOf("ist", "wäre")), Triple("Sie erzählt, sie ___ keine Zeit.", "habe", listOf("hat", "hätte")),
        Triple("Der Reporter berichtet, der Zug ___ pünktlich.", "komme", listOf("kommt", "kam")),
    )

    fun konjunktiv1(r: Random): LearnTask {
        val (s, a, w) = k1[r.nextInt(k1.size)]
        return t("Indirekte Rede", "Setze den Konjunktiv I ein: $s", a, w, "In der indirekten Rede steht der Konjunktiv I: er sei, sie habe, er komme.", r)
    }

    private val commas = listOf(
        listOf("Ich komme, wenn ich Zeit habe.", "Ich komme wenn, ich Zeit habe.", "Ich, komme wenn ich Zeit habe."),
        listOf("Das Buch, das ich lese, ist spannend.", "Das Buch das ich lese, ist spannend.", "Das Buch, das ich lese ist spannend."),
        listOf("Er lachte, weil der Witz lustig war.", "Er lachte weil, der Witz lustig war.", "Er, lachte weil der Witz lustig war."),
        listOf("Ich kaufe Äpfel, Birnen und Bananen.", "Ich kaufe Äpfel, Birnen, und Bananen.", "Ich kaufe, Äpfel Birnen und Bananen."),
    )

    fun comma(r: Random): LearnTask {
        val c = commas[r.nextInt(commas.size)]
        return t("Kommasetzung", "Welcher Satz ist richtig?", c[0], c.drop(1), "Nebensätze (mit wenn, weil, dass, das …) werden durch Kommas abgetrennt. Vor „und“ bei Aufzählungen steht kein Komma.", r)
    }

    // ---- Klasse 9/10
    private val figuresList = listOf(
        "Das ist gut, sehr gut, einfach großartig!" to "Klimax", "Mein Bruder ist ein Fels in der Brandung." to "Metapher",
        "Wer soll das bezahlen?" to "Rhetorische Frage", "Der Tag war heiß, die Nacht war kalt." to "Antithese",
        "Alle für einen, einer für alle." to "Chiasmus", "Bittersüß" to "Oxymoron", "Das Wasser rauscht, das Wasser schwoll." to "Anapher",
    )

    fun figures(r: Random): LearnTask {
        val (s, a) = figuresList[r.nextInt(figuresList.size)]
        return t("Stilmittel", "Welches Stilmittel liegt vor? „$s“", a, listOf("Klimax", "Metapher", "Rhetorische Frage", "Antithese", "Chiasmus", "Oxymoron", "Anapher"), "Klimax: Steigerung. Antithese: Gegensätze. Chiasmus: Überkreuzstellung. Oxymoron: Widerspruch in einem Ausdruck. Anapher: gleicher Satzanfang.", r)
    }

    private val k2 = listOf(
        Triple("Wenn ich Zeit ___, käme ich.", "hätte", listOf("habe", "hatte")), Triple("Wenn es nicht regnen ___, gingen wir raus.", "würde", listOf("wird", "wurde")),
        Triple("Ich ___ gern ein Eis.", "hätte", listOf("habe", "hatte")), Triple("Wenn ich ein Vogel ___, flöge ich.", "wäre", listOf("bin", "war")),
    )

    fun konjunktiv2(r: Random): LearnTask {
        val (s, a, w) = k2[r.nextInt(k2.size)]
        return t("Konjunktiv II", "Setze den Konjunktiv II ein: $s", a, w, "Für Wünsche und Unwirkliches nimmt man den Konjunktiv II: hätte, wäre, würde.", r)
    }

    private val textTypes = listOf(
        "Ein Text, der Pro- und Contra-Argumente abwägt" to "Erörterung", "Eine kurze, sachliche Zusammenfassung im Präsens" to "Inhaltsangabe",
        "Ein meinungsbetonter Zeitungstext" to "Kommentar", "Eine kurze, lehrreiche Geschichte mit Tieren" to "Fabel",
        "Ein Bericht über ein Ereignis mit W-Fragen" to "Bericht",
    )

    fun textTypes(r: Random): LearnTask {
        val (s, a) = textTypes[r.nextInt(textTypes.size)]
        return t("Textsorten", "Welche Textsorte ist das? $s", a, listOf("Erörterung", "Inhaltsangabe", "Kommentar", "Fabel", "Bericht"), "Gesucht war: $a. Erörterung = abwägen, Inhaltsangabe = zusammenfassen, Kommentar = Meinung, Fabel = Tiergeschichte mit Lehre, Bericht = sachlich mit W-Fragen.", r)
    }

    private val clauseItems = listOf(
        "…, der gestern angekommen ist." to "Relativsatz", "…, weil es regnet." to "Kausalsatz", "…, obwohl er müde war." to "Konzessivsatz",
        "…, damit wir pünktlich sind." to "Finalsatz", "…, wenn die Sonne scheint." to "Konditionalsatz",
    )

    fun clauses(r: Random): LearnTask {
        val (s, a) = clauseItems[r.nextInt(clauseItems.size)]
        return t("Nebensätze", "Welche Art von Nebensatz ist: „$s“", a, listOf("Relativsatz", "Kausalsatz", "Konzessivsatz", "Finalsatz", "Konditionalsatz"), "weil = Grund (kausal), obwohl = Einschränkung (konzessiv), damit = Zweck (final), wenn = Bedingung (konditional), der/die/das = Relativsatz.", r)
    }

    private val epochEasy = listOf(
        "Lessing: Nathan der Weise" to "Aufklärung", "Goethe: Die Leiden des jungen Werthers" to "Sturm und Drang",
        "Schiller: Die Räuber" to "Sturm und Drang", "E. T. A. Hoffmann: Der Sandmann" to "Romantik",
    )

    fun epochsEasy(r: Random): LearnTask {
        val (s, a) = epochEasy[r.nextInt(epochEasy.size)]
        return t("Literaturepochen", "Zu welcher Epoche gehört: $s?", a, listOf("Aufklärung", "Sturm und Drang", "Romantik", "Realismus"), "$s gehört zur Epoche $a.", r)
    }

    // ---- Oberstufe
    private val epochList = listOf(
        "Lessing: Emilia Galotti" to "Aufklärung", "Goethe: Faust I" to "Weimarer Klassik", "Schiller: Wilhelm Tell" to "Weimarer Klassik",
        "Büchner: Woyzeck" to "Vormärz", "Fontane: Effi Briest" to "Realismus", "Kafka: Die Verwandlung" to "Moderne",
        "Eichendorff: Aus dem Leben eines Taugenichts" to "Romantik", "Dürrenmatt: Die Physiker" to "Nachkriegsliteratur",
        "Goethe: Die Leiden des jungen Werthers" to "Sturm und Drang",
    )

    fun epochs(r: Random): LearnTask {
        val (s, a) = epochList[r.nextInt(epochList.size)]
        return t("Literaturepochen", "Zu welcher Epoche gehört: $s?", a, epochList.map { it.second }.distinct(), "$s gehört zur Epoche $a.", r)
    }

    private val authorList = listOf(
        "Die Räuber" to "Friedrich Schiller", "Faust" to "Johann Wolfgang von Goethe", "Nathan der Weise" to "Gotthold Ephraim Lessing",
        "Die Verwandlung" to "Franz Kafka", "Effi Briest" to "Theodor Fontane", "Woyzeck" to "Georg Büchner", "Der Besuch der alten Dame" to "Friedrich Dürrenmatt",
    )

    fun authors(r: Random): LearnTask {
        val (w, a) = authorList[r.nextInt(authorList.size)]
        return t("Autoren und Werke", "Wer schrieb „$w“?", a, authorList.map { it.second }, "„$w“ ist von $a.", r)
    }

    private val rhetoricList = listOf(
        "Ich höre Farben." to "Synästhesie", "Er ist von uns gegangen. (für: gestorben)" to "Euphemismus", "Er schläft, sie wacht; er lacht, sie weint." to "Parallelismus",
        "Ein Meer von Tränen" to "Hyperbel", "Ein „Smartphonezombie“" to "Neologismus", "Eile mit Weile" to "Paradoxon",
    )

    fun rhetoric(r: Random): LearnTask {
        val (s, a) = rhetoricList[r.nextInt(rhetoricList.size)]
        return t("Rhetorische Mittel", "Welches rhetorische Mittel liegt vor? „$s“", a, rhetoricList.map { it.second }, "„$s“ ist ein Beispiel für: $a.", r)
    }

    private val terms = listOf(
        "Ein Satzgefüge aus Haupt- und Nebensätzen" to "Hypotaxe", "Aneinandergereihte Hauptsätze" to "Parataxe",
        "Ein unvollständiger Satz, dem Teile fehlen" to "Ellipse", "Ein Erzähler, der alles weiß und in alle Figuren hineinsieht" to "auktorialer Erzähler",
        "Der innere Gedankenstrom einer Figur ohne Redezeichen" to "innerer Monolog",
    )

    fun grammarTerms(r: Random): LearnTask {
        val (s, a) = terms[r.nextInt(terms.size)]
        return t("Fachbegriffe", "Wie nennt man: $s?", a, terms.map { it.second }, "$s nennt man $a.", r)
    }

    private val meters = listOf("xX xX xX (unbetont – betont)" to "Jambus", "Xx Xx Xx (betont – unbetont)" to "Trochäus", "Xxx Xxx (betont – unbetont – unbetont)" to "Daktylus", "xxX xxX (unbetont – unbetont – betont)" to "Anapäst")

    fun meter(r: Random): LearnTask {
        val (s, a) = meters[r.nextInt(meters.size)]
        return t("Metrum", "Welches Versmaß hat das Muster $s?", a, meters.map { it.second }, "$s ist ein $a.", r)
    }

    private val rhymes = listOf("a b a b" to "Kreuzreim", "a a b b" to "Paarreim", "a b b a" to "umarmender Reim", "a a b c c b" to "Schweifreim")

    fun rhyme(r: Random): LearnTask {
        val (s, a) = rhymes[r.nextInt(rhymes.size)]
        return t("Reimschema", "Wie heißt das Reimschema $s?", a, rhymes.map { it.second }, "$s nennt man $a.", r)
    }
}

// ====================================================================================== Physik

private object PhysicsTasks {
    private fun t(topic: String, q: String, a: String, wrong: List<String>, hint: String, r: Random) =
        LearnTask(Subject.PHYSIK, topic, q, a, Learn.choices(a, wrong, r), hint)

    private fun calc(topic: String, q: String, value: Double, unit: String, hint: String, r: Random, decimals: Int = 2, extra: List<Double> = emptyList()): LearnTask {
        val (a, ch) = Learn.numberChoices(value, r, decimals, unit, extra)
        return LearnTask(Subject.PHYSIK, topic, q, a, ch, hint)
    }

    fun make(grade: Int, r: Random): LearnTask {
        val gens: List<(Random) -> LearnTask> = when (grade) {
            5, 6 -> listOf(::states, ::magnets, ::conductors, ::light, ::unitsBasic, ::sound)
            7, 8 -> listOf(::speed, ::density, ::weight, ::ohm, ::series, ::lever, ::optics)
            9, 10 -> listOf(::potential, ::kinetic, ::power, ::electricPower, ::pressure, ::halfLife, ::waves)
            else -> listOf(::acceleration, ::momentum, ::centripetal, ::capacitor, ::photon, ::pendulum, ::concepts)
        }
        return gens[r.nextInt(gens.size)](r)
    }

    // ---- Klasse 5/6
    private val stateQs = listOf(
        Triple("Was passiert beim Schmelzen?", "fest wird flüssig", listOf("flüssig wird gasförmig", "gasförmig wird flüssig", "flüssig wird fest")),
        Triple("Was passiert beim Verdampfen?", "flüssig wird gasförmig", listOf("fest wird flüssig", "gasförmig wird flüssig", "flüssig wird fest")),
        Triple("Bei welcher Temperatur gefriert Wasser?", "0 °C", listOf("100 °C", "−20 °C", "10 °C")),
        Triple("Bei welcher Temperatur siedet Wasser (auf Meereshöhe)?", "100 °C", listOf("0 °C", "50 °C", "200 °C")),
    )

    fun states(r: Random): LearnTask {
        val (q, a, w) = stateQs[r.nextInt(stateQs.size)]
        return t("Aggregatzustände", q, a, w, "Die drei Zustände sind fest, flüssig und gasförmig. Wasser gefriert bei 0 °C und siedet bei 100 °C.", r)
    }

    private val magnetQs = listOf(
        Triple("Was passiert, wenn sich zwei Nordpole nähern?", "Sie stoßen sich ab", listOf("Sie ziehen sich an", "Nichts", "Sie werden heiß")),
        Triple("Welcher Stoff wird von einem Magneten angezogen?", "Eisen", listOf("Holz", "Kupfer", "Kunststoff")),
        Triple("Wohin zeigt der Nordpol einer Kompassnadel?", "nach Norden", listOf("nach Süden", "nach Osten", "nach oben")),
    )

    fun magnets(r: Random): LearnTask {
        val (q, a, w) = magnetQs[r.nextInt(magnetQs.size)]
        return t("Magnetismus", q, a, w, "Gleiche Pole stoßen sich ab, ungleiche ziehen sich an. Nur Eisen, Nickel und Kobalt werden angezogen.", r)
    }

    private val conductorQs = listOf(
        Triple("Welcher Stoff leitet elektrischen Strom gut?", "Kupfer", listOf("Gummi", "Glas", "Holz")),
        Triple("Was ist ein Isolator?", "Ein Stoff, der Strom nicht leitet", listOf("Ein Stoff, der Strom sehr gut leitet", "Eine Batterie", "Ein Schalter")),
        Triple("Wann leuchtet eine Lampe im Stromkreis?", "Wenn der Stromkreis geschlossen ist", listOf("Wenn der Stromkreis offen ist", "Immer", "Nur am Tag")),
    )

    fun conductors(r: Random): LearnTask {
        val (q, a, w) = conductorQs[r.nextInt(conductorQs.size)]
        return t("Elektrischer Strom", q, a, w, "Metalle wie Kupfer leiten Strom, Gummi und Kunststoff nicht. Strom fließt nur im geschlossenen Kreis.", r)
    }

    private val lightQs = listOf(
        Triple("Wie entsteht ein Schatten?", "Ein Körper hält das Licht auf", listOf("Licht wird heller", "Durch Wind", "Durch Schall")),
        Triple("Was ist schneller?", "Licht", listOf("Schall", "Ein Flugzeug", "Ein Rennwagen")),
        Triple("Was macht ein Spiegel mit Licht?", "Er reflektiert es", listOf("Er schluckt es ganz", "Er färbt es grün", "Er macht es lauter")),
    )

    fun light(r: Random): LearnTask {
        val (q, a, w) = lightQs[r.nextInt(lightQs.size)]
        return t("Licht", q, a, w, "Licht breitet sich geradlinig und sehr schnell aus. Undurchsichtige Körper werfen Schatten, Spiegel reflektieren.", r)
    }

    private val unitQs = listOf(
        Triple("In welcher Einheit misst man die Masse?", "Kilogramm (kg)", listOf("Meter (m)", "Sekunde (s)", "Grad Celsius (°C)")),
        Triple("In welcher Einheit misst man die Zeit?", "Sekunde (s)", listOf("Kilogramm (kg)", "Meter (m)", "Liter (l)")),
        Triple("In welcher Einheit misst man die Kraft?", "Newton (N)", listOf("Watt (W)", "Volt (V)", "Kilogramm (kg)")),
        Triple("In welcher Einheit misst man die Temperatur bei uns meistens?", "Grad Celsius (°C)", listOf("Meter (m)", "Newton (N)", "Sekunde (s)")),
    )

    fun unitsBasic(r: Random): LearnTask {
        val (q, a, w) = unitQs[r.nextInt(unitQs.size)]
        return t("Physikalische Größen", q, a, w, "Jede Größe hat ihre Einheit: Masse in kg, Zeit in s, Länge in m, Kraft in N.", r)
    }

    private val soundQs = listOf(
        Triple("Warum hört man im Weltall nichts?", "Es gibt dort keine Luft, die den Schall trägt", listOf("Es ist zu kalt", "Es ist zu dunkel", "Die Sterne sind zu leise")),
        Triple("Was schwingt bei einer Gitarre?", "Die Saite", listOf("Das Licht", "Der Magnet", "Der Strom")),
    )

    fun sound(r: Random): LearnTask {
        val (q, a, w) = soundQs[r.nextInt(soundQs.size)]
        return t("Schall", q, a, w, "Schall entsteht durch Schwingungen und braucht einen Stoff (z. B. Luft), um sich auszubreiten.", r)
    }

    // ---- Klasse 7/8
    fun speed(r: Random): LearnTask {
        val v = 10 * (3 + r.nextInt(10))
        val tt = 1 + r.nextInt(4)
        return calc("Geschwindigkeit", "Ein Auto fährt ${v * tt} km in $tt h. Wie schnell ist es im Durchschnitt?", v.toDouble(), "km/h", "v = s : t = ${v * tt} km : $tt h = $v km/h.", r, extra = listOf((v * tt * tt).toDouble()))
    }

    fun density(r: Random): LearnTask {
        val rho = listOf(1.0, 2.7, 7.9, 0.9, 11.3)[r.nextInt(5)]
        val vol = 2 + r.nextInt(9)
        return calc("Dichte", "Ein Körper hat die Masse ${Learn.fmt(rho * vol)} g und das Volumen $vol cm³. Wie groß ist seine Dichte?", rho, "g/cm³", "ρ = m : V = ${Learn.fmt(rho * vol)} g : $vol cm³ = ${Learn.fmt(rho)} g/cm³.", r)
    }

    fun weight(r: Random): LearnTask {
        val m = 2 + r.nextInt(60)
        return calc("Gewichtskraft", "Wie groß ist die Gewichtskraft auf einen Körper mit $m kg? (g ≈ 10 N/kg)", m * 10.0, "N", "F = m · g = $m kg · 10 N/kg = ${m * 10} N.", r)
    }

    fun ohm(r: Random): LearnTask {
        val rr = 5 * (1 + r.nextInt(20))
        val i = (1 + r.nextInt(8)) / 2.0
        return if (r.nextBoolean()) {
            calc("Ohmsches Gesetz", "Durch einen Widerstand von $rr Ω fließt ${Learn.fmt(i)} A. Wie groß ist die Spannung?", rr * i, "V", "U = R · I = $rr Ω · ${Learn.fmt(i)} A = ${Learn.fmt(rr * i)} V.", r)
        } else {
            calc("Ohmsches Gesetz", "An einem Widerstand liegen ${Learn.fmt(rr * i)} V, es fließen ${Learn.fmt(i)} A. Wie groß ist der Widerstand?", rr.toDouble(), "Ω", "R = U : I = ${Learn.fmt(rr * i)} V : ${Learn.fmt(i)} A = $rr Ω.", r)
        }
    }

    fun series(r: Random): LearnTask {
        val a = 10 * (1 + r.nextInt(9))
        val b = 10 * (1 + r.nextInt(9))
        return calc("Reihenschaltung", "Zwei Widerstände ($a Ω und $b Ω) sind in Reihe geschaltet. Wie groß ist der Gesamtwiderstand?", (a + b).toDouble(), "Ω", "In Reihe addieren sich die Widerstände: $a + $b = ${a + b} Ω.", r, extra = listOf((a * b).toDouble() / (a + b)))
    }

    fun lever(r: Random): LearnTask {
        val f1 = 10 * (1 + r.nextInt(10))
        val a1 = 1 + r.nextInt(4)
        val a2 = a1 + 1 + r.nextInt(4)
        val f2 = f1 * a1.toDouble() / a2
        return calc("Hebelgesetz", "Am Hebel wirkt links $f1 N im Abstand $a1 m. Welche Kraft brauchst du rechts im Abstand $a2 m für Gleichgewicht?", f2, "N", "Kraft · Arm = Kraft · Arm: $f1 · $a1 = F · $a2 ⇒ F = ${Learn.fmt(f2)} N.", r)
    }

    private val opticsQs = listOf(
        Triple("Licht trifft unter 40° auf einen Spiegel. Unter welchem Winkel wird es reflektiert?", "40°", listOf("50°", "80°", "20°")),
        Triple("Licht geht von Luft in Wasser. Wie wird es gebrochen?", "zum Lot hin", listOf("vom Lot weg", "gar nicht", "es wird ganz verschluckt")),
        Triple("Was für eine Linse hat eine Lupe?", "Sammellinse", listOf("Zerstreuungslinse", "Planglas", "Spiegel")),
    )

    fun optics(r: Random): LearnTask {
        val (q, a, w) = opticsQs[r.nextInt(opticsQs.size)]
        return t("Optik", q, a, w, "Einfallswinkel = Reflexionswinkel. Beim Übergang in ein dichteres Medium wird Licht zum Lot hin gebrochen.", r)
    }

    // ---- Klasse 9/10
    fun potential(r: Random): LearnTask {
        val m = 1 + r.nextInt(20)
        val h = 1 + r.nextInt(10)
        return calc("Lageenergie", "Ein Körper mit $m kg wird $h m hochgehoben. Wie viel Lageenergie gewinnt er? (g ≈ 10 N/kg)", m * 10.0 * h, "J", "E = m · g · h = $m · 10 · $h = ${m * 10 * h} J.", r)
    }

    fun kinetic(r: Random): LearnTask {
        val m = 2 * (1 + r.nextInt(10))
        val v = 1 + r.nextInt(10)
        return calc("Bewegungsenergie", "Ein Körper mit $m kg bewegt sich mit $v m/s. Wie groß ist seine Bewegungsenergie?", 0.5 * m * v * v, "J", "E = ½ · m · v² = 0,5 · $m · ${v * v} = ${Learn.fmt(0.5 * m * v * v)} J.", r)
    }

    fun power(r: Random): LearnTask {
        val w = 100 * (1 + r.nextInt(20))
        val tt = 2 * (1 + r.nextInt(10))
        return calc("Leistung", "Ein Motor verrichtet $w J Arbeit in $tt s. Wie groß ist seine Leistung?", w.toDouble() / tt, "W", "P = W : t = $w J : $tt s = ${Learn.fmt(w.toDouble() / tt)} W.", r)
    }

    fun electricPower(r: Random): LearnTask {
        val u = listOf(12, 24, 230)[r.nextInt(3)]
        val i = (1 + r.nextInt(10)) / 2.0
        return calc("Elektrische Leistung", "Ein Gerät an $u V zieht ${Learn.fmt(i)} A. Wie groß ist seine Leistung?", u * i, "W", "P = U · I = $u V · ${Learn.fmt(i)} A = ${Learn.fmt(u * i)} W.", r)
    }

    fun pressure(r: Random): LearnTask {
        val f = 100 * (1 + r.nextInt(20))
        val a = listOf(0.5, 1.0, 2.0, 4.0)[r.nextInt(4)]
        return calc("Druck", "Eine Kraft von $f N wirkt auf ${Learn.fmt(a)} m². Wie groß ist der Druck?", f / a, "Pa", "p = F : A = $f N : ${Learn.fmt(a)} m² = ${Learn.fmt(f / a)} Pa.", r)
    }

    fun halfLife(r: Random): LearnTask {
        val start = 100 * (1 + r.nextInt(8))
        val n = 1 + r.nextInt(3)
        val chain = mutableListOf(start.toDouble())
        repeat(n) { chain += chain.last() / 2 }
        val v = chain.last()
        return calc("Radioaktivität", "Von $start radioaktiven Kernen sind nach $n ${if (n == 1) "Halbwertszeit" else "Halbwertszeiten"} noch wie viele übrig?", v, "", "Nach jeder Halbwertszeit ist noch die Hälfte da: ${chain.joinToString(" → ") { Learn.fmt(it) }}.", r)
    }

    fun waves(r: Random): LearnTask {
        val lam = listOf(0.5, 1.0, 2.0, 4.0)[r.nextInt(4)]
        val f = 10 * (5 + r.nextInt(30))
        return calc("Wellen", "Eine Welle hat die Wellenlänge ${Learn.fmt(lam)} m und die Frequenz $f Hz. Wie schnell breitet sie sich aus?", lam * f, "m/s", "c = λ · f = ${Learn.fmt(lam)} m · $f Hz = ${Learn.fmt(lam * f)} m/s.", r)
    }

    // ---- Oberstufe
    fun acceleration(r: Random): LearnTask {
        val a = 1 + r.nextInt(5)
        val tt = 2 + r.nextInt(6)
        return if (r.nextBoolean()) {
            calc("Beschleunigte Bewegung", "Ein Wagen startet aus dem Stand mit a = $a m/s². Welche Strecke legt er in $tt s zurück?", 0.5 * a * tt * tt, "m", "s = ½ · a · t² = 0,5 · $a · ${tt * tt} = ${Learn.fmt(0.5 * a * tt * tt)} m.", r)
        } else {
            calc("Beschleunigte Bewegung", "Ein Wagen startet aus dem Stand mit a = $a m/s². Wie schnell ist er nach $tt s?", (a * tt).toDouble(), "m/s", "v = a · t = $a · $tt = ${a * tt} m/s.", r)
        }
    }

    fun momentum(r: Random): LearnTask {
        val m = 2 + r.nextInt(50)
        val v = 1 + r.nextInt(10)
        return calc("Impuls", "Ein Körper mit $m kg bewegt sich mit $v m/s. Wie groß ist sein Impuls?", (m * v).toDouble(), "kg·m/s", "p = m · v = $m · $v = ${m * v} kg·m/s.", r)
    }

    fun centripetal(r: Random): LearnTask {
        val m = 1 + r.nextInt(5)
        val v = 2 * (1 + r.nextInt(5))
        val rad = listOf(1, 2, 4)[r.nextInt(3)]
        return calc("Kreisbewegung", "Ein Körper ($m kg) läuft mit $v m/s auf einem Kreis mit Radius $rad m. Wie groß ist die Zentripetalkraft?", m * v * v.toDouble() / rad, "N", "F = m · v² : r = $m · ${v * v} : $rad = ${Learn.fmt(m * v * v.toDouble() / rad)} N.", r)
    }

    fun capacitor(r: Random): LearnTask {
        val c = (1 + r.nextInt(9)) * 10
        val u = 2 * (1 + r.nextInt(6))
        return calc("Kondensator", "Ein Kondensator mit $c µF wird auf $u V aufgeladen. Welche Ladung trägt er?", (c * u).toDouble(), "µC", "Q = C · U = $c µF · $u V = ${c * u} µC.", r)
    }

    private val photonQs = listOf(
        Triple("Wovon hängt beim Photoeffekt die Energie der herausgelösten Elektronen ab?", "von der Frequenz des Lichts", listOf("von der Helligkeit des Lichts", "von der Größe der Platte", "von der Temperatur")),
        Triple("Wie berechnet man die Energie eines Photons?", "E = h · f", listOf("E = m · g · h", "E = ½ m v²", "E = U · I")),
        Triple("Was passiert mit der Zeit für sehr schnell bewegte Uhren?", "Sie vergeht langsamer", listOf("Sie vergeht schneller", "Sie bleibt stehen", "Sie läuft rückwärts")),
    )
    private val photonHints = listOf(
        "Jedes Photon bringt die Energie E = h · f mit. Helleres Licht hat mehr Photonen, aber nicht stärkere.",
        "Ein Photon hat die Energie E = h · f (h ist das Plancksche Wirkungsquantum, f die Frequenz).",
        "Das ist die Zeitdilatation aus der Relativitätstheorie: bewegte Uhren gehen langsamer.",
    )

    fun photon(r: Random): LearnTask {
        val i = r.nextInt(photonQs.size)
        val (q, a, w) = photonQs[i]
        return t("Moderne Physik", q, a, w, photonHints[i], r)
    }

    fun pendulum(r: Random): LearnTask {
        val l = listOf(1.0, 4.0, 0.25)[r.nextInt(3)]
        val tt = 2 * PI * sqrt(l / 9.81)
        return calc("Schwingungen", "Wie groß ist ungefähr die Schwingungsdauer eines Fadenpendels mit ${Learn.fmt(l)} m Länge? (T = 2π√(l/g))", tt, "s", "T = 2π · √(${Learn.fmt(l)} : 9,81) ≈ ${Learn.fmt(tt)} s.", r, decimals = 1)
    }

    private val conceptQs = listOf(
        Triple("Wann wird in einer Spule eine Spannung induziert?", "Wenn sich das Magnetfeld durch die Spule ändert", listOf("Wenn ein Magnet still darin liegt", "Nur bei hoher Temperatur", "Wenn die Spule aus Holz ist")),
        Triple("Was gilt beim vollkommen elastischen Stoß?", "Impuls und Energie bleiben erhalten", listOf("Nur die Masse bleibt erhalten", "Die Energie geht verloren", "Die Körper bleiben aneinander kleben")),
        Triple("Was ist ein Alphateilchen?", "ein Heliumkern", listOf("ein Elektron", "ein Lichtteilchen", "ein Wasserstoffatom")),
    )

    fun concepts(r: Random): LearnTask {
        val (q, a, w) = conceptQs[r.nextInt(conceptQs.size)]
        return t("Grundbegriffe", q, a, w, "Richtig ist: $a.", r)
    }
}
