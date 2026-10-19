package de.knuffi.core

import kotlinx.serialization.Serializable
import kotlin.random.Random

// ---------------------------------------------------------------------------------- pet kind

/** What the player chose at the start: fantasy creatures from eggs or a real cat. */
@Serializable
enum class PetKind(val title: String, val emoji: String) {
    FANTASY("Fabelwesen", "🥚"),
    CAT("Echte Katze", "🐱"),
}

// ---------------------------------------------------------------------------------- skins

/** Patterns a skin or fur can paint on the body. The app maps them to its own drawing code. */
@Serializable
enum class SkinPattern { NONE, TABBY, CALICO, TUXEDO, POINTS, STRIPES, SPOTS, STARS, GALAXY, HEARTS, ZIGZAG, SEEDS, ICING, RIBS }

/** Extra effects around the pet that come with a skin. */
@Serializable
enum class SkinAura { NONE, SNOW, HEARTS, STARS, BUBBLES, LEAVES, FLAMES, SMOKE }

/**
 * A colour scheme for the pet. Colours are ARGB values. Missing colours are taken from the form.
 * [fur] skins are cat coats, all others are costumes that can be won at festivals.
 */
data class Skin(
    val id: String,
    val name: String,
    val emoji: String,
    val description: String,
    val body: Long,
    val accent: Long? = null,
    val accent2: Long? = null,
    val iris: Long? = null,
    val belly: Long? = null,
    val extra: Long? = null,
    val pattern: SkinPattern = SkinPattern.NONE,
    val aura: SkinAura = SkinAura.NONE,
    val sparkle: Boolean = false,
    val glow: Boolean = false,
    val fur: Boolean = false,
    val event: SeasonEvent? = null,
)

object Skins {
    private const val PINK_EAR = 0xFFFFB3C6

    /** Cat coats. The first one is the default. */
    val furs: List<Skin> = listOf(
        Skin("fur_tabby_red", "Rot getigert", "🧡", "Orange mit dunklen Streifen. Ein echter Sonnenschein.", 0xFFF2A65A, PINK_EAR, 0xFFD2722A, 0xFFE8A93A, belly = 0xFFFFF0DC, pattern = SkinPattern.TABBY, fur = true),
        Skin("fur_tabby_grey", "Grau getigert", "🩶", "Silbergrau mit eleganten Streifen.", 0xFFA9ABB4, PINK_EAR, 0xFF5F616C, 0xFF7FB85A, belly = 0xFFE9EAEE, pattern = SkinPattern.TABBY, fur = true),
        Skin("fur_black", "Schwarz", "🖤", "Glänzend schwarz wie die Nacht.", 0xFF35313C, 0xFFE89AAE, 0xFF1F1C24, 0xFFD8D04A, belly = 0xFF45404D, fur = true),
        Skin("fur_white", "Weiß", "🤍", "Schneeweiß und flauschig.", 0xFFF8F6F3, PINK_EAR, 0xFFE4E0DA, 0xFF5AA8E8, belly = 0xFFFFFFFF, fur = true),
        Skin("fur_tuxedo", "Schwarz-weiß", "🎩", "Trägt immer einen kleinen Frack.", 0xFF302D36, 0xFFE89AAE, 0xFFFFFFFF, 0xFF8BC34A, belly = 0xFFFFFFFF, pattern = SkinPattern.TUXEDO, fur = true),
        Skin("fur_calico", "Dreifarbig", "🍀", "Weiß mit roten und schwarzen Flecken. Bringt Glück!", 0xFFFAF6F0, PINK_EAR, 0xFFE8913A, 0xFFD9A23A, belly = 0xFFFFFFFF, extra = 0xFF3A3436, pattern = SkinPattern.CALICO, fur = true),
        Skin("fur_blue", "Blaugrau", "💙", "Plüschiges, blaugraues Fell mit kupferfarbenen Augen.", 0xFF8E9AAD, 0xFFD9A0B4, 0xFF6E7A8E, 0xFFE8963A, belly = 0xFFB3BCCB, fur = true),
        Skin("fur_siam", "Siam", "💎", "Cremefarben mit dunklen Ohren, Pfoten und Schwanz.", 0xFFF3E7D3, 0xFFC9A0A0, 0xFF5A4636, 0xFF4A90E8, belly = 0xFFFFF8EC, pattern = SkinPattern.POINTS, fur = true),
        Skin("fur_cream", "Creme", "🍦", "Hell wie Vanillepudding, mit zarten Streifen.", 0xFFF3DDBB, PINK_EAR, 0xFFE0B27E, 0xFFE0A030, belly = 0xFFFFF6E8, pattern = SkinPattern.TABBY, fur = true),
        Skin("fur_brown", "Braun getigert", "🤎", "Wie eine kleine Wildkatze aus dem Wald.", 0xFFA07E5C, PINK_EAR, 0xFF5A3E2A, 0xFFB8C24A, belly = 0xFFE8D8C0, pattern = SkinPattern.TABBY, fur = true),
    )

    /** Costumes that can be won at the festivals (two per festival). */
    val costumes: List<Skin> = listOf(
        Skin("skin_firework", "Feuerwerk", "🎆", "Nachtblau mit funkelnden Raketen.", 0xFF2E3A7A, 0xFFFFD24D, 0xFFFF7AB0, 0xFFFFE680, pattern = SkinPattern.GALAXY, aura = SkinAura.STARS, sparkle = true, event = SeasonEvent.NEUJAHR),
        Skin("skin_gold", "Goldglanz", "🥇", "Glänzt wie ein Goldtaler. Ein seltener Gewinn!", 0xFFFFD45A, 0xFFFFF2B8, 0xFFFFB02E, 0xFFB86B00, belly = 0xFFFFF4C8, sparkle = true, glow = true, event = SeasonEvent.NEUJAHR),
        Skin("skin_heart", "Herzchen", "💗", "Rosa mit lauter kleinen Herzen.", 0xFFFFB8D2, 0xFFFF5C9A, 0xFFFF4F8B, 0xFFC21F5A, belly = 0xFFFFE8F0, pattern = SkinPattern.HEARTS, aura = SkinAura.HEARTS, event = SeasonEvent.VALENTIN),
        Skin("skin_rose", "Rosenquarz", "🌹", "Ein schimmernder Edelstein-Look.", 0xFFF2C4D6, 0xFFFFE0EC, 0xFFD98AB0, 0xFF9A3A6A, belly = 0xFFFFF0F6, sparkle = true, glow = true, event = SeasonEvent.VALENTIN),
        Skin("skin_easter", "Osterei", "🥚", "Pastellbunt mit Zickzack-Muster.", 0xFFB8E8D0, 0xFFFFB3D1, 0xFFFFD84D, 0xFF3A8A6A, belly = 0xFFF0FFF8, pattern = SkinPattern.ZIGZAG, event = SeasonEvent.OSTERN),
        Skin("skin_bunny", "Schokohase", "🍫", "Zartbitter mit Sahnebauch. Zum Anbeißen!", 0xFF8A5A3C, 0xFFFFB3C6, 0xFFFFE8C8, 0xFF5A2E14, belly = 0xFFF6E3C8, pattern = SkinPattern.ICING, sparkle = true, event = SeasonEvent.OSTERN),
        Skin("skin_tropic", "Tropenfisch", "🐠", "Türkis mit orangen Streifen.", 0xFF4FD1C5, 0xFFFFE066, 0xFFFF8A3D, 0xFF0E6A7A, belly = 0xFFE8FFFC, pattern = SkinPattern.STRIPES, aura = SkinAura.BUBBLES, event = SeasonEvent.SOMMERFEST),
        Skin("skin_melon", "Wassermelone", "🍉", "Saftig rot mit schwarzen Kernen.", 0xFFFF7A8A, 0xFF7AD06A, 0xFF2A2A2A, 0xFF2E6B2A, belly = 0xFFFFC2CA, pattern = SkinPattern.SEEDS, sparkle = true, event = SeasonEvent.SOMMERFEST),
        Skin("skin_pumpkin", "Kürbis", "🎃", "Orange mit Rillen. Huhu!", 0xFFFF9A3D, 0xFF4E8A3A, 0xFFE0701E, 0xFF3A1E00, belly = 0xFFFFC98A, pattern = SkinPattern.RIBS, aura = SkinAura.LEAVES, event = SeasonEvent.HALLOWEEN),
        Skin("skin_ghost", "Gespenst", "👻", "Fast durchsichtig und leuchtet im Dunkeln.", 0xFFEEF0FF, 0xFFB8B8E8, 0xFFD8DCFF, 0xFF6A6AD6, belly = 0xFFFFFFFF, aura = SkinAura.SMOKE, sparkle = true, glow = true, event = SeasonEvent.HALLOWEEN),
        Skin("skin_frost", "Eiskristall", "❄️", "Eisblau und funkelnd wie Raureif.", 0xFFBDE6FF, 0xFFFFFFFF, 0xFFFFFFFF, 0xFF2E7FB0, belly = 0xFFEFF9FF, pattern = SkinPattern.STARS, aura = SkinAura.SNOW, sparkle = true, event = SeasonEvent.WINTERZAUBER),
        Skin("skin_ginger", "Lebkuchen", "🍪", "Braun gebacken mit Zuckerguss.", 0xFFC0804A, 0xFFFF6A7A, 0xFFFFFFFF, 0xFF4A2410, belly = 0xFFE0A870, pattern = SkinPattern.ICING, event = SeasonEvent.WINTERZAUBER),
    )

    val all: List<Skin> = furs + costumes
    private val byId = all.associateBy { it.id }

    operator fun get(id: String?): Skin? = id?.let { byId[it] }

    val DEFAULT_FUR: String get() = furs.first().id

    fun forEvent(e: SeasonEvent): List<Skin> = costumes.filter { it.event == e }

    /** Random coat for an adopted kitten. */
    fun randomFur(rnd: Random): String = furs[rnd.nextInt(furs.size)].id
}

// ---------------------------------------------------------------------------------- perks

/** Bonus of a special piece of equipment while it is worn. */
@Serializable
enum class Perk(val title: String, val emoji: String, val description: String) {
    COINS("Glückspilz", "🍀", "+15 % Münzen aus Minispielen"),
    XP("Schlaukopf", "🧠", "+10 % Erfahrung"),
    JOY("Sonnenschein", "☀️", "Die Laune sinkt 20 % langsamer"),
    HUNGER("Genießer", "🍽️", "Der Hunger kommt 20 % langsamer"),
    ENERGY("Ausgeschlafen", "🔋", "Die Energie sinkt 20 % langsamer"),
    TALENT("Talentförderer", "🌟", "+30 % Talentpunkte"),
    TRIP("Weltenbummler", "🧭", "+30 % Münzen von Ausflügen"),
    WORK("Fleißbiene", "🐝", "+20 % Lohn bei der Arbeit"),
    GARDEN("Grüner Daumen", "🌿", "Pflanzen wachsen 20 % schneller"),
}

object Perks {
    /** Perks of everything the pet is wearing right now. */
    fun active(s: GameState): Set<Perk> {
        val out = HashSet<Perk>()
        for (slot in arrayOf(Slot.HAT, Slot.FACE, Slot.NECK)) {
            val id = s.equipped[slot] ?: continue
            Catalog[id]?.perk?.let { out += it }
        }
        return out
    }
}

// ---------------------------------------------------------------------------------- emotions

/** The finer mood of the pet. It changes with the stats and with what you do together. */
@Serializable
enum class Emotion(val title: String, val emoji: String, val description: String, val positive: Boolean) {
    FROEHLICH("Fröhlich", "😄", "Rundum zufrieden und gut gelaunt.", true),
    VERSPIELT("Verspielt", "😜", "Hat Lust auf Unsinn und Spiele.", true),
    VERSCHMUST("Verschmust", "🥰", "Möchte gestreichelt werden.", true),
    STOLZ("Stolz", "😎", "Hat gerade etwas Tolles geschafft.", true),
    NEUGIERIG("Neugierig", "🧐", "Will alles genau wissen.", true),
    AUFGEREGT("Aufgeregt", "🤩", "Kann kaum still sitzen vor Freude.", true),
    ENTSPANNT("Entspannt", "😌", "Ganz gemütlich und ruhig.", true),
    GELANGWEILT("Gelangweilt", "🥱", "Es passiert einfach nichts …", false),
    GRUMMELIG("Grummelig", "😤", "Etwas stimmt nicht. Hunger? Schmutz?", false),
    EINSAM("Einsam", "🥺", "Vermisst dich.", false),
    TRAURIG("Traurig", "😢", "Braucht dringend Zuwendung.", false),
    MUEDE("Müde", "😴", "Die Augen fallen schon zu.", false),
    KRANK("Kränklich", "🤒", "Fühlt sich nicht gut.", false),
    SCHLAEFRIG("Schläft", "💤", "Träumt süß.", true),
}

@Serializable
data class MoodEntry(val emotion: Emotion, val at: Long)

object Emotions {
    const val LONELY_AFTER_MS = 8 * 3_600_000L
    private const val TRIGGER_MS = 45 * 60_000L

    /** How long a triggered feeling lasts. */
    fun triggerMs(e: Emotion): Long = when (e) {
        Emotion.STOLZ, Emotion.AUFGEREGT -> TRIGGER_MS
        Emotion.VERSPIELT, Emotion.VERSCHMUST, Emotion.NEUGIERIG -> 30 * 60_000L
        else -> 20 * 60_000L
    }

    /** The feeling that comes from the stats alone. */
    fun base(p: Pet, now: Long): Emotion = when {
        !p.alive || p.isEgg -> Emotion.ENTSPANNT
        p.sleeping -> Emotion.SCHLAEFRIG
        p.sick -> Emotion.KRANK
        p.lowest < 15 || p.average < 35 -> Emotion.TRAURIG
        p.energy < 25 -> Emotion.MUEDE
        p.satiety < 25 || p.hygiene < 25 || p.poops >= 2 -> Emotion.GRUMMELIG
        p.lastCare > 0 && now - p.lastCare > LONELY_AFTER_MS -> Emotion.EINSAM
        p.joy < 32 -> Emotion.GELANGWEILT
        p.joy >= 70 && p.average >= 70 -> Emotion.FROEHLICH
        else -> Emotion.ENTSPANNT
    }

    /** Urgent feelings cannot be covered by a happy moment. */
    fun urgent(e: Emotion): Boolean = e == Emotion.KRANK || e == Emotion.TRAURIG || e == Emotion.SCHLAEFRIG

    /** The feeling right now: a recent moment or the base feeling. */
    fun current(p: Pet, now: Long): Emotion {
        val base = base(p, now)
        if (urgent(base)) return base
        val trig = p.feeling
        if (trig != null && now < p.feelingUntil) return trig
        return base
    }

    /** Multiplier for coins from mini games. */
    fun coinFactor(e: Emotion): Double = when (e) {
        Emotion.FROEHLICH, Emotion.STOLZ -> 1.1
        Emotion.VERSPIELT -> 1.15
        Emotion.GRUMMELIG, Emotion.TRAURIG -> 0.9
        else -> 1.0
    }

    fun xpFactor(e: Emotion): Double = when (e) {
        Emotion.AUFGEREGT, Emotion.NEUGIERIG -> 1.15
        Emotion.MUEDE -> 0.9
        else -> 1.0
    }

    /** Short line for the HUD. */
    fun hint(e: Emotion): String = when (e) {
        Emotion.FROEHLICH -> "+10 % Münzen in Minispielen"
        Emotion.VERSPIELT -> "+15 % Münzen in Minispielen"
        Emotion.STOLZ -> "+10 % Münzen in Minispielen"
        Emotion.AUFGEREGT, Emotion.NEUGIERIG -> "+15 % Erfahrung"
        Emotion.VERSCHMUST -> "Streicheln macht doppelt glücklich"
        Emotion.EINSAM -> "Etwas Zeit zusammen hilft sofort"
        Emotion.GRUMMELIG -> "Essen oder Putzen hilft"
        Emotion.GELANGWEILT -> "Spielen hilft"
        Emotion.MUEDE -> "Zeit fürs Bett"
        Emotion.TRAURIG -> "Braucht dringend Pflege"
        Emotion.KRANK -> "Medizin hilft"
        Emotion.ENTSPANNT -> "Alles im grünen Bereich"
        Emotion.SCHLAEFRIG -> "Psst …"
    }
}

// ---------------------------------------------------------------------------------- talents & jobs

@Serializable
enum class Talent(val title: String, val emoji: String, val bonus: String) {
    KOCHEN("Kochen", "🍳", "Essen wirkt stärker"),
    GAERTNERN("Gärtnern", "🌱", "Pflanzen wachsen schneller"),
    BASTELN("Basteln", "✂️", "Mehr Münzen bei Memory und Bildschirm-Spielereien"),
    ENTDECKEN("Entdecken", "🧭", "Ausflüge bringen mehr Münzen"),
    MUSIK("Musik", "🎵", "Mehr Münzen bei Melodie und Blubberblasen"),
    SPORT("Sport", "⚽", "Mehr Münzen bei Futterfang, Hüpf-Lauf und Blitz-Tap"),
}

@Serializable
enum class Job(
    val title: String,
    val catTitle: String,
    val emoji: String,
    val tool: String,
    val talent: Talent,
    val description: String,
) {
    KOCH("Koch", "Feinschmeckerin", "🧑‍🍳", "🍳", Talent.KOCHEN, "Rührt in Töpfen und probiert alles."),
    GAERTNER("Gärtner", "Gartenkatze", "🧑‍🌾", "🌻", Talent.GAERTNERN, "Pflanzt, gießt und erntet."),
    KUENSTLER("Künstler", "Kratzkünstlerin", "🧑‍🎨", "🖌️", Talent.BASTELN, "Malt Bilder und bastelt Papierflieger."),
    ENTDECKER("Entdecker", "Streifzug-Katze", "🧭", "🗺️", Talent.ENTDECKEN, "Erkundet jeden Winkel der Welt."),
    MUSIKER("Musiker", "Schnurr-Sängerin", "🧑‍🎤", "🎸", Talent.MUSIK, "Singt, trommelt und tanzt."),
    SPORTLER("Sportler", "Mäusejägerin", "🏃", "⚽", Talent.SPORT, "Trainiert jeden Tag."),
    ;

    fun title(cat: Boolean): String = if (cat) catTitle else title
}

object Jobs {
    const val UNLOCK_LEVEL = 5
    const val MAX_LEVEL = 10
    const val SHIFTS_PER_LEVEL = 4
    const val SHIFT_COOLDOWN_MS = 2 * 3_600_000L
    const val CHANGE_PRICE = 100

    fun level(jobXp: Int): Int = (1 + jobXp / SHIFTS_PER_LEVEL).coerceAtMost(MAX_LEVEL)

    fun rank(level: Int): String = when {
        level >= 10 -> "Legende"
        level >= 8 -> "Meister"
        level >= 5 -> "Profi"
        level >= 3 -> "Geselle"
        else -> "Lehrling"
    }

    fun wage(jobLevel: Int, talentLevel: Int): Int = 14 + 5 * jobLevel + 2 * talentLevel
}

object Talents {
    const val MAX_LEVEL = 10

    /** XP needed in total to reach [level]. */
    fun xpFor(level: Int): Int = 20 * level * (level + 1) / 2

    fun level(xp: Int): Int {
        var l = 0
        while (l < MAX_LEVEL && xp >= xpFor(l + 1)) l++
        return l
    }

    /** Progress inside the current level, 0..1. */
    fun progress(xp: Int): Float {
        val l = level(xp)
        if (l >= MAX_LEVEL) return 1f
        val from = xpFor(l)
        val to = xpFor(l + 1)
        return ((xp - from).toFloat() / (to - from)).coerceIn(0f, 1f)
    }

    /** Every pet is born with one natural talent that grows faster. */
    fun natural(p: Pet): Talent = Talent.entries[Math.floorMod(p.bornAt / 7L + 3L, Talent.entries.size.toLong()).toInt()]

    fun of(game: MiniGame): Talent = when (game) {
        MiniGame.CATCH, MiniGame.RUNNER, MiniGame.WHACK, MiniGame.FLIGHT, MiniGame.STACK -> Talent.SPORT
        MiniGame.MEMORY, MiniGame.PUZZLE, MiniGame.COLORING, MiniGame.YARN, MiniGame.MATCH -> Talent.BASTELN
        MiniGame.BUBBLES, MiniGame.SIMON -> Talent.MUSIK
        MiniGame.COOK -> Talent.KOCHEN
        MiniGame.FISHING, MiniGame.PACMAN -> Talent.ENTDECKEN
    }

    fun level(p: Pet, t: Talent): Int = level(p.talents[t] ?: 0)

    /** Coin multiplier of a mini game from the matching talent. */
    fun gameFactor(p: Pet, game: MiniGame): Double = 1.0 + 0.04 * level(p, of(game))
}

// ---------------------------------------------------------------------------------- screen toys

/** Things the pet does on the screen. Each one has its own slider in the settings. */
@Serializable
enum class ToyKind(val title: String, val emoji: String) {
    LADDER("Leiter", "🪜"),
    PLANE("Papierflieger", "✈️"),
    FOOTPRINTS("Fußspuren", "🐾"),
    PAINT("Farbkleckse", "🎨"),
}

object ToyTuning {
    const val MIN = 0
    const val MAX = 4
    val titles = listOf("Aus", "Selten", "Manchmal", "Oft", "Sehr oft")
    val footprintTitles = listOf("Aus", "Kurz", "Mittel", "Lang", "Sehr lang")

    /** Chance that an idle moment turns into this toy. */
    fun chance(level: Int): Float = floatArrayOf(0f, 0.04f, 0.08f, 0.14f, 0.22f)[level.coerceIn(MIN, MAX)]

    /** How long footprints stay visible (seconds). */
    fun footprintSeconds(level: Int): Float = floatArrayOf(0f, 8f, 25f, 75f, 300f)[level.coerceIn(MIN, MAX)]

    /** How many paint splashes can be on the screen at once. */
    fun maxSplashes(level: Int): Int = intArrayOf(0, 2, 3, 5, 8)[level.coerceIn(MIN, MAX)]

    fun level(s: Settings, kind: ToyKind): Int = when (kind) {
        ToyKind.LADDER -> s.toyLadder
        ToyKind.PLANE -> s.toyPlanes
        ToyKind.FOOTPRINTS -> s.toyFootprints
        ToyKind.PAINT -> s.toyPaint
    }

    fun with(s: Settings, kind: ToyKind, level: Int): Settings {
        val l = level.coerceIn(MIN, MAX)
        return when (kind) {
            ToyKind.LADDER -> s.copy(toyLadder = l)
            ToyKind.PLANE -> s.copy(toyPlanes = l)
            ToyKind.FOOTPRINTS -> s.copy(toyFootprints = l)
            ToyKind.PAINT -> s.copy(toyPaint = l)
        }
    }
}

// ---------------------------------------------------------------------------------- festival prizes

/** Something that can be won at a festival. */
data class EventPrize(val id: String, val tokens: Int, val skin: Boolean) {
    val name: String get() = if (skin) Skins[id]?.name ?: id else Catalog[id]?.name ?: id
    val emoji: String get() = if (skin) Skins[id]?.emoji ?: "🎁" else Catalog[id]?.emoji ?: "🎁"
}

/** Result of one turn of the festival wheel. */
data class SpinResult(val label: String, val emoji: String, val prize: String? = null, val coins: Int = 0, val tokens: Int = 0, val sticker: String? = null)

object EventPrizes {
    /** Tokens you need to earn (not spend) during a festival for each prize. */
    val thresholds = intArrayOf(8, 20, 35, 55)

    fun gear(e: SeasonEvent): List<String> = when (e) {
        SeasonEvent.NEUJAHR -> listOf("hat_lucky", "neck_medal")
        SeasonEvent.VALENTIN -> listOf("hat_heartband", "neck_rosechain")
        SeasonEvent.OSTERN -> listOf("hat_eggshell", "neck_carrotscarf")
        SeasonEvent.SOMMERFEST -> listOf("hat_sunvisor", "face_starshades")
        SeasonEvent.HALLOWEEN -> listOf("neck_batbow", "hat_pumpkinlamp")
        SeasonEvent.WINTERZAUBER -> listOf("neck_lights", "hat_icecrown")
    }

    /** The four prizes of a festival in the order they unlock. */
    fun of(e: SeasonEvent): List<EventPrize> {
        val g = gear(e)
        val s = Skins.forEvent(e).map { it.id }
        return listOf(
            EventPrize(g[0], thresholds[0], skin = false),
            EventPrize(s[0], thresholds[1], skin = true),
            EventPrize(g[1], thresholds[2], skin = false),
            EventPrize(s[1], thresholds[3], skin = true),
        )
    }

    fun owned(s: GameState, prize: EventPrize): Boolean = if (prize.skin) prize.id in s.skins else prize.id in s.owned

    /** The wheel: small prizes most of the time, sometimes one of the festival prizes. */
    fun spin(s: GameState, e: SeasonEvent, rnd: Random): SpinResult {
        val missing = of(e).filter { !owned(s, it) }
        val pity = s.event.spins >= 4 && missing.isNotEmpty() && s.event.spinWins == 0
        val r = rnd.nextFloat()
        if (missing.isNotEmpty() && (pity || r < 0.12f)) {
            val p = missing[rnd.nextInt(missing.size)]
            return SpinResult("Hauptgewinn: ${p.name}!", p.emoji, prize = p.id)
        }
        return when {
            r < 0.42f -> {
                val n = 3 + rnd.nextInt(4)
                SpinResult("$n ${e.tokenName}", e.tokenEmoji, tokens = n)
            }
            r < 0.7f -> {
                val c = 25 + 5 * rnd.nextInt(6)
                SpinResult("$c Münzen", "🪙", coins = c)
            }
            r < 0.86f -> {
                val st = Stickers.forEvent(e).let { it[rnd.nextInt(it.size)] }
                SpinResult("Sticker: ${st.name}", st.emoji, sticker = st.id)
            }
            else -> {
                val n = 8 + rnd.nextInt(5)
                SpinResult("$n ${e.tokenName}", e.tokenEmoji, tokens = n)
            }
        }
    }

    fun nextPrize(s: GameState, e: SeasonEvent): EventPrize? = of(e).firstOrNull { it.id !in s.event.prizes && !owned(s, it) }

    fun canSpin(s: GameState, today: Long): Boolean = s.event.key.isNotEmpty() && s.event.spinDay != today
}
