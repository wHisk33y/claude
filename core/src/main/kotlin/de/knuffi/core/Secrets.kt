package de.knuffi.core

import kotlinx.serialization.Serializable
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import kotlin.random.Random

// ---------------------------------------------------------------------------------- secrets

/** Hidden surprises. The hint is shown in the secret album before the secret is found. */
@Serializable
enum class Secret(val title: String, val emoji: String, val hint: String, val coins: Int) {
    MAGIC_WORD("Das Zauberwort", "🪄", "Ein Zauberer sagt es, bevor er etwas hext. Schreib es im Chat!", 40),
    SECRET_TALK("Geheimniskrämer", "🤫", "Frag dein Haustier im Chat nach einem Geheimnis.", 20),
    PICTURE("Lebendiges Bild", "🖼️", "Das Bild an der Wand mag es, fünfmal angestupst zu werden.", 30),
    WISH("Sternschnuppe", "🌠", "Nachts lohnt sich ein Blick durchs Fenster. Tippe drauf!", 40),
    DISCO("Disco-Lampe", "🪩", "Wenn es dunkel ist, tippe die Lampe fünfmal an.", 40),
    MIDNIGHT("Geisterstunde", "👻", "Schau genau um Mitternacht vorbei.", 50),
    FULL_MOON("Vollmond", "🌕", "Bei Vollmond passiert etwas Besonderes.", 50),
    DIZZY("Karussell", "😵‍💫", "Zieh mit dem Finger Kreise um dein Haustier.", 30),
    HIGHFIVE("Gib mir fünf!", "🙌", "Tippe dein Haustier an, wenn es ganz oben auf der Leiter steht.", 30),
    PERFECT_PACMAN("Leckermaul", "🍪", "Iss im Pacman-Spiel alle Leckerlis, ohne ein Leben zu verlieren.", 60),
    HIDDEN_7("Spürnase", "🔍", "Finde 7 versteckte Gegenstände im Zimmer.", 80),
    FRIENDS("Seelenverwandte", "💞", "Lass zwei Haustiere beste Freunde werden.", 80),
    CATS("Katzenflüsterer", "🐈", "Schreib im Chat dreimal das Wort, das jede Katze kennt … oder tippe im Zimmer Fenster, Lampe und Bild nacheinander an.", 60),
    SINGER("Superstar", "🎤", "Sing deinem Haustier etwas vor. Dafür muss in den Einstellungen das Mikrofon an sein.", 40),
    TICKLE("Kitzelkönig", "🤭", "Kitzle dein Haustier ganz oft hintereinander am Bauch.", 30),
    JUGGLER("Jongleur", "🎾", "Wirf beim Ballspiel im Zimmer 10 Bälle, ohne dass einer auf den Boden fällt.", 50),
}

object Secrets {
    /** "miau miau miau" (or a polite request) in the chat unlocks kittens. */
    fun catWish(text: String): Boolean {
        val n = Chat.normalize(text)
        return n.split(' ').count { it.startsWith("miau") } >= 3 || n.contains("katzenzauber")
    }

    /** The tap combination in the room: window, lamp, picture. */
    val catCombo: List<String> = listOf("window", "lamp", "picture")

    val magicWords = setOf("abrakadabra", "simsalabim", "hokuspokus")

    /** Days since a known new moon, modulo the lunar cycle. */
    fun moonAge(date: LocalDate): Double {
        val known = LocalDate.of(2000, 1, 6)
        val days = ChronoUnit.DAYS.between(known, date).toDouble()
        return ((days % 29.530588) + 29.530588) % 29.530588
    }

    fun fullMoon(date: LocalDate): Boolean = moonAge(date) in 13.8..15.8

    fun midnight(now: Long, zone: ZoneId): Boolean {
        val t = Instant.ofEpochMilli(now).atZone(zone)
        return t.hour == 0 && t.minute < 15
    }

    /** A hint for one secret that has not been found yet. */
    fun hint(s: GameState, rnd: Random): String? {
        val open = Secret.entries.filter { it.name !in s.secrets }
        if (open.isEmpty()) return null
        return open[rnd.nextInt(open.size)].hint
    }
}

// ---------------------------------------------------------------------------------- heart names

/** Easter egg: writing or saying one of these names fills the whole screen with hearts. */
object HeartNames {
    /** The names, also the way speech recognition likes to write them. */
    private val names = setOf(
        "toni", "tony", "tonie", "tonis",
        "susi", "susie", "suzi", "susy", "suzie", "susis",
        "arik", "ariks", "arick", "aric", "arrik", "arek",
    )

    fun mentioned(text: String): Boolean = text.lowercase().split(Regex("[^\\p{L}]+")).any { it in names }
}

// ---------------------------------------------------------------------------------- daily hidden object

/** Where something can be hidden in the room. Coordinates are normalised to the scene. */
enum class HideSpot(val title: String, val x: Float, val y: Float) {
    BED("beim Bett", 0.9f, 0.6f),
    PLANT("in der Pflanze", 0.07f, 0.52f),
    RUG("unter dem Teppich", 0.66f, 0.79f),
    SHELF("im Regal", 0.68f, 0.31f),
    CURTAIN("hinter dem Vorhang", 0.04f, 0.38f),
    BOWL("neben dem Napf", 0.27f, 0.66f),
    WINDOW("am Fenster", 0.34f, 0.47f),
    FLOOR("vorne am Boden", 0.42f, 0.9f),
}

data class HiddenItem(val emoji: String, val name: String, val spot: HideSpot)

object Hidden {
    private val items = listOf(
        "🧦" to "eine Socke", "🔑" to "einen Schlüssel", "🍪" to "einen Keks", "🧸" to "einen Teddy",
        "💍" to "einen Ring", "🪀" to "ein Jojo", "🖍️" to "einen Buntstift", "🎲" to "einen Würfel",
        "🧩" to "ein Puzzleteil", "🐭" to "eine Spielzeugmaus", "🥜" to "eine Nuss", "🎈" to "einen Luftballon",
    )
    const val COINS = 30

    fun today(day: Long): HiddenItem {
        val r = Random(day * 31337L + 11)
        val (e, n) = items[r.nextInt(items.size)]
        return HiddenItem(e, n, HideSpot.entries[r.nextInt(HideSpot.entries.size)])
    }
}

// ---------------------------------------------------------------------------------- diary

@Serializable
data class DiaryEntry(val day: Long, val text: String, val emoji: String)

object Diary {
    /** Writes the pet's diary entry for a day from what happened. */
    fun write(petName: String, cat: Boolean, day: Long, log: Map<String, Int>, mood: Emotion?, weather: WeatherKind?): DiaryEntry {
        val rnd = Random(day)
        val parts = mutableListOf<String>()
        val open = if (cat) listOf("Miau, liebes Tagebuch!", "Prrr … liebes Tagebuch,") else listOf("Liebes Tagebuch,", "Hallo Tagebuch!", "Heute war was los!")
        parts += open[rnd.nextInt(open.size)]
        weather?.let {
            parts += when (it) {
                WeatherKind.SUN -> "Die Sonne hat geschienen."
                WeatherKind.CLOUDY -> "Es war ein bisschen wolkig."
                WeatherKind.RAIN -> "Draußen hat es geregnet, aber drinnen war es gemütlich."
                WeatherKind.SNOW -> "Es hat geschneit! Alles war weiß."
                WeatherKind.STORM -> "Es hat gedonnert. Huh!"
            }
        }
        fun n(k: String) = log[k] ?: 0
        if (n("feed") > 0) parts += when {
            n("feed") >= 5 -> "Ich habe soooo viel gegessen (${n("feed")}-mal!)."
            cat -> "Mein Napf war voll. Mjam!"
            else -> "Ich habe lecker gegessen."
        }
        if (n("cook") > 0) parts += "Wir haben zusammen gekocht! Es hat super geschmeckt."
        if (n("game") > 0) parts += if (n("game") >= 3) "Wir haben ${n("game")} Spiele gespielt. Ich war richtig gut!" else "Wir haben ein Spiel gespielt."
        if (n("pet") >= 5) parts += if (cat) "Ich wurde ganz viel gekrault. Prrrrr!" else "Ich wurde ganz oft gestreichelt. Hach!"
        if (n("chat") > 0) parts += "Wir haben uns ${if (n("chat") > 5) "lange" else "ein bisschen"} unterhalten."
        if (n("work") > 0) parts += "Ich war arbeiten und habe Münzen verdient."
        if (n("visit") > 0) parts += "Ein Freund war zu Besuch! Wir haben zusammen gespielt."
        if (n("story") > 0) parts += "Ich habe ein neues Abenteuer erlebt!"
        if (n("trip") > 0) parts += "Jemand ist von einem Ausflug zurückgekommen und hat was mitgebracht."
        if (n("hidden") > 0) parts += "Wir haben etwas Verstecktes gefunden!"
        if (n("secret") > 0) parts += "Psst … wir haben ein Geheimnis entdeckt!"
        if (n("bath") > 0) parts += if (cat) "Ich wurde gebürstet. Jetzt glänzt mein Fell." else "Ich hatte ein Schaumbad. Blubb!"
        if (n("learn") > 0) parts += if (n("learn") >= 9) "Wir haben zusammen gelernt, ${n("learn")} Aufgaben! Ich bin jetzt bestimmt auch schlauer." else "Wir haben ein bisschen zusammen gelernt."
        val quiet = parts.size <= 2
        if (quiet) parts += "Heute war ein ruhiger Tag. Ich habe dich vermisst."
        parts += if (quiet) "Morgen spielen wir wieder zusammen, ja?" else when (mood) {
            Emotion.FROEHLICH, Emotion.AUFGEREGT, Emotion.STOLZ -> "Es war ein toller Tag!"
            Emotion.VERSPIELT -> "Morgen spielen wir weiter, ja?"
            Emotion.VERSCHMUST -> "Ich hab dich ganz doll lieb."
            Emotion.GRUMMELIG, Emotion.TRAURIG, Emotion.EINSAM -> "Morgen wird bestimmt besser."
            else -> "Gute Nacht!"
        }
        parts += "Dein $petName"
        val emoji = when {
            n("game") + n("pet") + n("feed") >= 8 -> "🌟"
            n("visit") > 0 -> "💞"
            parts.size <= 4 -> "🌙"
            else -> "📔"
        }
        return DiaryEntry(day, parts.joinToString(" "), emoji)
    }
}

// ---------------------------------------------------------------------------------- friends playing together

enum class FriendMoment(val title: String, val minLevel: Int) {
    TALK("plaudern", 0),
    BALL("Ball zuwerfen", 0),
    PAINT("sich anmalen", 1),
    DANCE("tanzen", 2),
    HIDE("Verstecken spielen", 2),
    CUDDLE("kuscheln", 3),
    SQUABBLE("sich streiten und vertragen", 1),
    BUBBLES("Seifenblasen pusten", 1),
    MUSIC("zusammen musizieren", 2),
}
