package de.knuffi.core

import kotlinx.serialization.Serializable
import java.time.Instant
import java.time.ZoneId
import kotlin.random.Random

@Serializable
data class ChatMessage(val fromPet: Boolean, val text: String, val at: Long)

@Serializable
data class ChatState(
    val messages: List<ChatMessage> = emptyList(),
    /** Things the pet remembers about you, e.g. your name or favourite colour. */
    val memory: Map<String, String> = emptyMap(),
    val bonusDay: Long = -1,
)

/** What a chat answer does besides talking. */
enum class ChatEffect { NONE, PLAY, SLEEP, WAKE, STROKE, HAPPY, LAUGH, SAD, CURIOUS }

data class ChatReply(
    val text: String,
    val effect: ChatEffect = ChatEffect.NONE,
    val remember: Map<String, String> = emptyMap(),
)

/**
 * A small offline chat brain. It understands simple German sentences by keywords and answers
 * depending on the pet's feeling, stats, job, talents and what it remembers about you.
 */
object Chat {
    const val UNLOCK_LEVEL = 6
    const val MAX_MESSAGES = 80

    const val MEM_NAME = "name"
    const val MEM_COLOR = "farbe"
    const val MEM_FOOD = "essen"
    const val MEM_ANIMAL = "tier"
    const val MEM_HOBBY = "hobby"

    val quickReplies = listOf(
        "Wie geht's dir?",
        "Hast du Hunger?",
        "Erzähl mir einen Witz!",
        "Lass uns spielen!",
        "Was machst du beruflich?",
        "Ich hab dich lieb!",
        "Was ist dein Lieblingsessen?",
        "Gute Nacht!",
    )

    private val colors = listOf("Rosa", "Himmelblau", "Mintgrün", "Sonnengelb", "Lila", "Orange", "Regenbogenbunt")
    private val catColors = listOf("Mausgrau", "Sonnenplatz-Gelb", "Wollknäuel-Rot", "Fischblau")

    fun favoriteFood(p: Pet): Item {
        val foods = Catalog.foods.filter { !it.unlimited && it.sold }
        if (p.isCat) return Catalog["tuna"] ?: foods.first()
        return foods[Math.floorMod(p.bornAt / 13L, foods.size.toLong()).toInt()]
    }

    fun favoriteColor(p: Pet): String {
        val list = if (p.isCat) catColors else colors
        return list[Math.floorMod(p.bornAt / 17L, list.size.toLong()).toInt()]
    }

    /** Lower case, umlauts spelled out, only letters, digits and single spaces. */
    fun normalize(text: String): String {
        val sb = StringBuilder()
        for (ch in text.lowercase()) {
            when (ch) {
                'ä' -> sb.append("ae")
                'ö' -> sb.append("oe")
                'ü' -> sb.append("ue")
                'ß' -> sb.append("ss")
                in 'a'..'z', in '0'..'9' -> sb.append(ch)
                else -> sb.append(' ')
            }
        }
        return sb.toString().split(' ').filter { it.isNotBlank() }.joinToString(" ")
    }

    private class Input(raw: String) {
        val n = normalize(raw)
        val words = n.split(' ').toSet()
        fun word(vararg w: String) = w.any { it in words }
        fun phrase(vararg p: String) = p.any { n.contains(it) }
        val question = raw.trim().endsWith("?") || word("wie", "was", "wer", "wo", "warum", "wieso", "weshalb", "wann", "welche", "welcher", "welches", "hast", "bist", "magst", "kannst", "willst")
    }

    private fun <T> List<T>.pick(rnd: Random): T = this[rnd.nextInt(size)]

    /** A sound a cat makes now and then. */
    private fun catSound(rnd: Random): String = listOf("Miau! ", "Mrrrp! ", "Prrr … ", "Miiiau. ", "").pick(rnd)

    private fun hello(now: Long, zone: ZoneId): String {
        val h = Instant.ofEpochMilli(now).atZone(zone).hour
        return when {
            h < 5 -> "Hallo, du Nachteule"
            h < 11 -> "Guten Morgen"
            h < 18 -> "Guten Tag"
            h < 22 -> "Guten Abend"
            else -> "Hallo, du Nachteule"
        }
    }

    /** The first thing the pet says when you open the chat. */
    fun greeting(s: GameState, p: Pet, now: Long, zone: ZoneId): String {
        val rnd = Random(now / 1000L)
        val you = s.chat.memory[MEM_NAME]?.let { ", $it" } ?: ""
        val cat = if (p.isCat) catSound(rnd) else ""
        val hi = hello(now, zone)
        return cat + when (p.emotion(now)) {
            Emotion.EINSAM -> "Da bist du ja endlich$you! Ich hab dich so vermisst. 🥺"
            Emotion.TRAURIG -> "Hallo$you … mir geht es gar nicht gut. Kümmerst du dich um mich?"
            Emotion.KRANK -> "Hatschi! Hallo$you. Ich fühle mich krank …"
            Emotion.MUEDE -> "Gääähn … hallo$you. Ich bin sooo müde."
            Emotion.GRUMMELIG -> if (p.satiety < 30) "Hmpf. Hallo$you. Mein Bauch knurrt!" else "Hmpf. Hallo$you. Ich fühl mich nicht so sauber."
            Emotion.GELANGWEILT -> "Hallo$you! Endlich passiert was. Mir ist sooo langweilig!"
            Emotion.VERSPIELT -> "Hey$you! Spielen wir was? Bitte, bitte!"
            Emotion.VERSCHMUST -> "Hallo$you! Kraulst du mich ein bisschen? 🥰"
            Emotion.STOLZ -> "Hallo$you! Hast du gesehen, was ich geschafft habe? 😎"
            Emotion.AUFGEREGT -> "Hallo$you!!! Heute ist so ein aufregender Tag! 🤩"
            Emotion.NEUGIERIG -> "Hallo$you! Ich hab eine Frage: Was hast du heute gemacht?"
            else -> listOf(
                "Hallo$you! Schön, dass du da bist. 😊",
                "$hi$you! Worüber wollen wir reden?",
                "Hey$you! Ich hab gerade an dich gedacht.",
            ).pick(rnd)
        }
    }

    fun reply(s: GameState, p: Pet, raw: String, now: Long, zone: ZoneId): ChatReply {
        val rnd = Random(now xor raw.hashCode().toLong())
        val i = Input(raw)
        val me = p.name
        val you = s.chat.memory[MEM_NAME]
        val youComma = you?.let { ", $it" } ?: ""
        val emo = p.emotion(now)
        val cat = p.isCat
        val pre = if (cat && rnd.nextFloat() < 0.45f) catSound(rnd) else ""
        fun say(text: String, effect: ChatEffect = ChatEffect.NONE, remember: Map<String, String> = emptyMap()) =
            ChatReply(pre + text, effect, remember)

        if (p.sleeping) {
            return if (i.word("aufwachen", "aufstehen", "wach", "weckt", "wecken") || i.phrase("wach auf", "steh auf")) {
                ChatReply("Hmm? … *gääähn* … Ich bin wach! 😪", ChatEffect.WAKE)
            } else {
                ChatReply(listOf("Zzz … Zzz …", "*schnarch* … Mmh … Kekse …", "Zzz … (${p.name} schläft. Psst!)").pick(rnd))
            }
        }

        // ---------------------------------------------------------------- remembering you
        val nameMatch = Regex("(?i)(?:ich hei(?:ß|ss)e|mein name ist|nenn mich|ich bin die|ich bin der)\\s+([A-Za-zÄÖÜäöüß\\-]{2,16})").find(raw)
        if (nameMatch != null) {
            val n = nameMatch.groupValues[1].replaceFirstChar { it.uppercase() }
            return if (you == n) say("Ich weiß doch, $n! Das vergesse ich nie. 💕")
            else say("Hallo $n! Was für ein schöner Name. Den merke ich mir! 😊", ChatEffect.HAPPY, mapOf(MEM_NAME to n))
        }
        Regex("(?i)meine? lieblings(farbe|essen|tier|hobby|speise)\\s+(?:ist|sind|waere|wäre)\\s+(.{2,24})").find(raw)?.let { m ->
            val key = when (m.groupValues[1].lowercase()) {
                "farbe" -> MEM_COLOR
                "tier" -> MEM_ANIMAL
                "hobby" -> MEM_HOBBY
                else -> MEM_FOOD
            }
            val value = m.groupValues[2].trim().trimEnd('.', '!', '?').replaceFirstChar { it.uppercase() }
            val answer = when (key) {
                MEM_COLOR -> "$value? Schöne Farbe! Meine ist ${favoriteColor(p)}."
                MEM_FOOD -> "Mmmh, $value! Das klingt lecker. Ich esse am liebsten ${favoriteFood(p).label(cat)}."
                MEM_ANIMAL -> if (cat) "$value? Hauptsache keine Hunde! 😼" else "$value? Süß! Aber ich bin dein Lieblingstier, oder?"
                else -> "$value macht bestimmt Spaß! Zeigst du mir das mal?"
            }
            return say("$answer Das merke ich mir!", ChatEffect.CURIOUS, mapOf(key to value))
        }
        if (i.phrase("wie heisse ich", "weisst du wie ich heisse", "kennst du meinen namen", "wer bin ich")) {
            return if (you != null) say("Du bist $you! Mein Lieblingsmensch. 💕") else say("Das hast du mir noch nicht verraten! Sag einfach: „Ich heiße …“")
        }
        if (i.phrase("meine lieblingsfarbe", "mein lieblingsessen", "mein lieblingstier", "mein hobby") && i.question) {
            val key = when {
                i.phrase("farbe") -> MEM_COLOR
                i.phrase("essen") -> MEM_FOOD
                i.phrase("tier") -> MEM_ANIMAL
                else -> MEM_HOBBY
            }
            val v = s.chat.memory[key]
            return if (v != null) say("Na klar weiß ich das: $v! 😊") else say("Hmm, das weiß ich noch nicht. Erzählst du es mir?")
        }

        // ---------------------------------------------------------------- secrets & hidden things
        if (Secrets.catWish(i.n)) {
            return say(
                if (s.catMode) "Miau miau miau! 😸 Wir verstehen uns, oder?" else "Miau? 🐱 Oh! Ich hab eine Überraschung: Im Kuschelhaus wartet ein Kätzchen-Körbchen auf dich! Und im Shop gibt es jetzt auch welche.",
                ChatEffect.LAUGH,
            )
        }
        if (i.words.any { it in Secrets.magicWords }) {
            return say("✨ Puff! ✨ Hast du das gesehen? Ich kann zaubern! 🪄", ChatEffect.LAUGH)
        }
        if (i.word("geheimnis", "geheimnisse", "geheim")) {
            val hint = Secrets.hint(s, rnd)
            return say(if (hint != null) "Psst … ich verrate dir was: $hint 🤫" else "Du kennst schon alle meine Geheimnisse! 😮", ChatEffect.CURIOUS)
        }
        if (i.word("versteck", "versteckt", "suche", "suchen", "verloren") || i.phrase("wo ist")) {
            val today = TimeUtil.epochDay(now, zone)
            val h = Hidden.today(today)
            return say(if (s.daily.hiddenFound) "Wir haben heute schon ${h.name} gefunden! Morgen verstecke ich was Neues. 😉" else "Ich glaube, heute liegt ${h.name} irgendwo ${h.spot.title} … 👀")
        }

        // ---------------------------------------------------------------- feelings of the player
        if (i.phrase("mir geht es schlecht", "mir gehts schlecht", "ich bin traurig", "mir geht es nicht gut", "ich hatte einen schlechten tag", "ich bin muede", "ich bin krank")) {
            val tired = i.phrase("muede")
            return say(
                when {
                    tired -> "Dann ruh dich ein bisschen aus$youComma. Ich kuschel mich zu dir. 🤗"
                    i.phrase("krank") -> "Oh nein! Gute Besserung$youComma! Trink viel Tee. 🍵"
                    cat -> "Komm her, ich schnurre für dich. Prrrrr … Schnurren hilft immer. 💕"
                    else -> "Oh nein$youComma! Komm, ich drück dich ganz fest. Morgen wird bestimmt besser. 🤗"
                },
                ChatEffect.HAPPY,
            )
        }
        if (i.phrase("mir geht es gut", "mir gehts gut", "mir geht es super", "ich bin gluecklich", "ich hatte einen tollen tag", "mir geht es prima")) {
            return say("Juhu! Das freut mich riesig$youComma! 🥳", ChatEffect.HAPPY)
        }

        // ---------------------------------------------------------------- actions
        if (i.word("spielen", "spielst", "spiel", "toben", "ball") || i.phrase("langweilig")) {
            return when {
                p.sick -> say("Ich würde so gern, aber ich bin krank … 🤒")
                p.energy < 12 -> say("Ich bin zu müde zum Spielen. Morgen wieder?")
                cat -> say("Wollknäuel! Wo ist das Wollknäuel?! 🧶", ChatEffect.PLAY)
                else -> say(listOf("Au ja! Fang mich doch! 🎾", "Juhuu, spielen! Ich bin schneller als du!", "Endlich! Los geht's! 🎉").pick(rnd), ChatEffect.PLAY)
            }
        }
        if (i.phrase("gute nacht", "schlaf gut", "geh schlafen", "ab ins bett", "schlafenszeit", "traeum was schoenes")) {
            return if (p.energy > 85) say("Aber ich bin noch gar nicht müde! Noch fünf Minuten?")
            else say(if (cat) "Ich roll mich ein … Prrr … Gute Nacht$youComma! 🌙" else "Gute Nacht$youComma! Träum was Schönes. 🌙", ChatEffect.SLEEP)
        }
        if (i.word("streicheln", "kraulen", "kuscheln", "knuddeln", "drueck", "umarmung", "kuschel") || i.phrase("komm her")) {
            return say(if (cat) "Prrrrrrr … genau da hinter dem Ohr! 😻" else "Hihi, das kitzelt! Mehr davon! 🥰", ChatEffect.STROKE)
        }

        // ---------------------------------------------------------------- about the pet
        if (i.phrase("wie geht", "wie gehts", "alles gut", "alles klar", "wie fuehlst du", "wie ist deine laune", "wie ist deine stimmung", "was ist los")) {
            val need = p.needs().firstOrNull()
            val needText = when (need) {
                Need.SICK -> " Ich glaube, ich brauche Medizin."
                Need.HUNGRY -> " Aber mein Bauch knurrt!"
                Need.DIRTY -> " Ein Bad wäre schön …"
                Need.BORED -> " Mir ist ein bisschen langweilig."
                Need.TIRED -> " Und ich bin müde."
                null -> ""
            }
            return say("Ich bin gerade ${emo.title.lowercase()} ${emo.emoji}.$needText")
        }
        if (i.phrase("lieblingsessen", "was magst du essen", "isst du gern", "frisst du gern", "was schmeckt")) {
            return say("Mein Lieblingsessen ist ${favoriteFood(p).label(cat)} ${favoriteFood(p).icon(cat)}. Das könnte ich jeden Tag essen!")
        }
        if (i.word("hunger", "hungrig", "essen", "futter", "fressen", "durst", "appetit", "mampf") || i.phrase("was isst")) {
            val fav = favoriteFood(p).label(cat)
            return say(
                when {
                    p.satiety < 30 -> "JA! Riesenhunger! Am liebsten $fav! 🤤"
                    p.satiety < 70 -> "Ein kleiner Snack ginge schon … vielleicht $fav?"
                    else -> "Nein danke, ich bin pappsatt! 😮‍💨"
                },
            )
        }
        if (i.phrase("lieblingsfarbe")) return say("Meine Lieblingsfarbe ist ${favoriteColor(p)}! Und deine?")
        if (i.phrase("wie heisst du", "dein name", "wer bist du")) {
            return say(if (cat) "Ich bin $me, eine echte Katze! ${p.form.title}, um genau zu sein. 🐱" else "Ich bin $me! Meine Form heißt ${p.form.title}. ✨")
        }
        if (i.phrase("wie alt", "dein alter", "geburtstag")) {
            val days = p.ageDays(now)
            return say(if (days == 0) "Ich bin heute erst geschlüpft … äh, angekommen!" else "Ich bin schon $days ${if (days == 1) "Tag" else "Tage"} alt! Und Level ${p.level}. 💪")
        }
        if (i.word("level", "stufe", "erfahrung", "xp")) {
            return say("Ich bin Level ${p.level}! Noch ${p.xpToNext - p.xp} Erfahrungspunkte bis zum nächsten Level.")
        }
        if (i.word("beruf", "job", "arbeit", "arbeiten", "arbeitest") || i.phrase("beruflich")) {
            val job = p.job
            return when {
                job == null && p.level < Jobs.UNLOCK_LEVEL -> say("Einen Beruf? Dafür bin ich noch zu klein! Ab Level ${Jobs.UNLOCK_LEVEL} darf ich einen lernen.")
                job == null -> say("Ich hab noch keinen Beruf. Such mir doch einen aus! Ich wäre gern ${Talents.natural(p).let { t -> Job.entries.first { it.talent == t }.title(cat) }}.", ChatEffect.CURIOUS)
                else -> say("Ich bin ${job.title(cat)} ${job.emoji}, schon ${Jobs.rank(p.jobLevel)}! ${job.description}")
            }
        }
        if (i.word("talent", "talente", "begabung") || i.phrase("was kannst du", "worin bist du gut", "kannst du gut")) {
            val best = Talent.entries.maxByOrNull { p.talents[it] ?: 0 }
            val nat = Talents.natural(p)
            val bestText = if (best != null && (p.talents[best] ?: 0) > 0) " Am besten bin ich gerade in ${best.title} ${best.emoji} (Stufe ${p.talentLevel(best)})." else ""
            return say("Mein Naturtalent ist ${nat.title} ${nat.emoji}!$bestText")
        }
        if (i.word("witz", "witze", "lustig", "lachen") || i.phrase("bring mich zum lachen")) {
            return say(jokes(cat).pick(rnd), ChatEffect.LAUGH)
        }
        if (i.phrase("hab dich lieb", "liebe dich", "du bist suess", "du bist toll", "du bist niedlich", "du bist schoen", "du bist der beste", "du bist die beste", "du bist super", "bester freund", "beste freundin")) {
            return say(
                listOf(
                    "Ich hab dich auch lieb$youComma! Ganz doll! 💕",
                    "Hihi, du machst mich ganz rot! 😊💗",
                    if (cat) "*schmiegt sich an dich* Prrrrrr … 💕" else "Du bist der allerbeste Mensch der Welt! 🥰",
                ).pick(rnd),
                ChatEffect.HAPPY,
            )
        }
        if (i.word("doof", "bloed", "dumm", "haesslich", "nervig", "nervst", "stinkst", "bloedmann") || i.phrase("hasse dich", "mag dich nicht")) {
            return say(if (cat) "*faucht* Pffft! 😾 … Das war nicht nett." else "Das war gemein … 😢 Ich hab dich trotzdem lieb.", ChatEffect.SAD)
        }
        if (i.word("danke", "dankeschoen", "merci")) return say("Gern geschehen$youComma! 😊")
        if (i.word("tschuess", "ciao", "bye", "tschau", "servus") || i.phrase("bis spaeter", "bis morgen", "bis bald", "auf wiedersehen")) {
            return say(listOf("Tschüss$youComma! Komm bald wieder! 👋", "Bis bald! Ich warte hier auf dich. 💕").pick(rnd))
        }
        if (i.word("hallo", "hi", "hey", "huhu", "moin", "hallihallo", "tach") || i.phrase("guten morgen", "guten tag", "guten abend", "gruess gott")) {
            return say(listOf("Hallo$youComma! 😊", "Hey hey$youComma! Schön, dass du schreibst!", "Huhu$youComma! Was machen wir heute?").pick(rnd))
        }
        if (i.phrase("wie spaet", "uhrzeit", "welcher tag", "welches datum")) {
            val t = Instant.ofEpochMilli(now).atZone(zone)
            return say("Es ist ${t.hour}:${t.minute.toString().padStart(2, '0')} Uhr. Genau richtig für ein Leckerli! 😋")
        }
        if (i.word("wetter", "regen", "sonne", "schnee")) {
            return say(if (cat) "Ich mag Sonnenplätze am Fenster. Regen finde ich doof!" else "Egal welches Wetter, mit dir ist jeder Tag schön! ☀️")
        }
        if (i.word("maus", "maeuse", "wolle", "wollknaeuel", "kratzbaum", "katzenminze") && cat) {
            return say(listOf("MÄUSE?! Wo?! 🐭", "Kratzbaum ist mein Lieblingsplatz! Gleich nach deinem Schoß.", "Katzenminze! Prrrr … *rollt sich herum*").pick(rnd), ChatEffect.LAUGH)
        }
        if (i.word("hilfe", "help") || i.phrase("was kann ich", "was kannst du verstehen", "worueber")) {
            return say("Frag mich, wie es mir geht, ob ich Hunger habe, nach meinem Beruf oder einem Witz. Sag „Lass uns spielen!“ oder „Gute Nacht!“. Und erzähl mir von dir, z. B. „Ich heiße …“ oder „Meine Lieblingsfarbe ist …“. Ich merke mir das!")
        }
        if (i.word("miau", "miauu", "miauuu", "mau")) {
            return say(if (cat) "Miau miau! (Das heißt: Ich hab dich lieb!) 😺" else "Miau? Bin ich etwa eine Katze? Hihi!", ChatEffect.LAUGH)
        }

        // ---------------------------------------------------------------- fallback
        val fallback = if (i.question) {
            listOf(
                "Hmm, das ist eine gute Frage! Was denkst du denn?",
                "Das weiß ich leider nicht … aber ich weiß, dass ich dich mag! 😊",
                "Puh, schwierig! Frag mich lieber, ob ich Hunger habe. 😋",
            )
        } else {
            when (emo) {
                Emotion.VERSPIELT -> listOf("Hihi! Und jetzt spielen wir, ja?", "Klingt lustig! Fang mich! 🏃")
                Emotion.GRUMMELIG -> listOf("Hmpf. Okay.", "Mhm … ich hab gerade schlechte Laune.")
                Emotion.MUEDE -> listOf("Mhm … *gähn* … erzähl weiter …", "Ich hör dir zu … glaub ich … 😴")
                Emotion.EINSAM -> listOf("Bleib noch ein bisschen bei mir, ja?", "Schön, dass du mit mir redest! 🥺")
                else -> listOf(
                    "Erzähl mir mehr! 😊",
                    "Wirklich? Das ist ja spannend!",
                    "Oh, das wusste ich noch nicht!",
                    "Hihi, du bist lustig!",
                    if (cat) "Miau? (Ich verstehe nicht alles, aber ich mag deine Stimme.)" else "Ich verstehe nicht alles, aber ich hör dir gern zu! 💕",
                )
            }
        }
        val follow = if (rnd.nextFloat() < 0.3f && you == null) " Wie heißt du eigentlich?" else ""
        return say(fallback.pick(rnd) + follow, ChatEffect.CURIOUS)
    }

    private fun jokes(cat: Boolean): List<String> = if (cat) {
        listOf(
            "Was macht eine Katze am Computer? Sie jagt die Maus! 🐭",
            "Warum sitzen Katzen so gern auf Tastaturen? Damit sie den Überblick behalten! ⌨️",
            "Was sagt eine Katze, wenn sie gewinnt? Mi-AU-sgezeichnet! 😹",
        )
    } else {
        listOf(
            "Was ist grün und klopft an die Tür? Ein Klopfsalat! 🥗",
            "Was macht ein Pirat am Computer? Er drückt die Enter-Taste! 🏴‍☠️",
            "Warum können Geister so schlecht lügen? Weil man durch sie hindurchsieht! 👻",
            "Was sitzt auf einem Baum und schreit „Aha“? Ein Uhu mit Sprachfehler! 🦉",
            "Wie nennt man einen Bumerang, der nicht zurückkommt? Stock! 🪵",
        )
    }
}
