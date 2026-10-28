package de.knuffi.app.render

import android.graphics.Color
import de.knuffi.core.EggLine
import de.knuffi.core.Emotion
import de.knuffi.core.Form
import de.knuffi.core.GameState
import de.knuffi.core.Mood
import de.knuffi.core.Skin
import de.knuffi.core.SkinAura
import de.knuffi.core.SkinPattern
import de.knuffi.core.Skins
import de.knuffi.core.Slot

class PetColors(
    val body: Int,
    val light: Int,
    val shade: Int,
    val belly: Int,
    val accent: Int,
    val accent2: Int,
    val cheek: Int,
    val iris: Int,
    val eye: Int = 0xFF2A1F3D.toInt(),
    /** Third pattern colour (e.g. the black patches of a calico cat). */
    val extra: Int = accent2,
)

enum class Ears { NONE, BUNNY, CAT, ROUND, FOX, FIN, LEAF, BAT, HORSE, WRAPPER, LOP, PETAL }

/** Things growing out of the top of the head. */
enum class Antenna {
    NONE, SPROUT, GLOW, STAR, UNICORN, FLAME, FLOWER, CRYSTAL, SWIRL, BOLT, STEM, CREST, DROPLET, CLOVER,
    MOON, PEARL, BUD, ANTENNAE, RAYS,

    // Cap-like tops sit in front of the head and are hidden by a worn hat.
    MUSHROOM, CHERRY, SHELL_CAP, CHEF, HELMET, WITCH, HALO,
    ;

    val cap: Boolean get() = ordinal >= MUSHROOM.ordinal
}

enum class Wings { NONE, SMALL, DRAGON, ANGEL, FAIRY, BAT, JET, BEE, FEATHER }
enum class Tail { NONE, DRAGON, FLUFFY, FISH, LEAF, BOLT, CAT, RAINBOW, FLAME, COMET, DINO, KITTY, POM }
enum class EyeStyle { BIG, GRUMPY, GLOW, CAT }
enum class Pattern {
    NONE, SPOTS, STRIPES, STARS, PANELS, SWIRL, BANDAGE, SPRINKLES, GALAXY, CRACKS, BUTTONS, BANDS, RIBS,
    TABBY, CALICO, TUXEDO, POINTS, HEARTS, ZIGZAG, SEEDS, ICING,
}
enum class Back { NONE, PLATES, SHELL, MANE, FRILL, CAPE }
enum class Muzzle { NONE, SNOUT, NOSE, WHISKERS, BEAK, FANGS, CARROT, CAT, BUNNY }
enum class Shape { ROUND, SQUARE, DROP }
enum class Aura { NONE, FLAMES, BUBBLES, SNOW, HEARTS, LEAVES, STARS, SMOKE, BOLTS }

data class FormLook(
    val colors: PetColors,
    val width: Float,
    val height: Float,
    val ears: Ears = Ears.NONE,
    val antenna: Antenna = Antenna.NONE,
    val wings: Wings = Wings.NONE,
    val tail: Tail = Tail.NONE,
    val horns: Boolean = false,
    val spikes: Boolean = false,
    val ghost: Boolean = false,
    val crown: Boolean = false,
    val headband: Boolean = false,
    val eyes: EyeStyle = EyeStyle.BIG,
    val bigBelly: Boolean = false,
    val sparkles: Boolean = false,
    val pattern: Pattern = Pattern.NONE,
    val back: Back = Back.NONE,
    val muzzle: Muzzle = Muzzle.NONE,
    val shape: Shape = Shape.ROUND,
    val aura: Aura = Aura.NONE,
    val antlers: Boolean = false,
    val claws: Boolean = false,
    val faceplate: Boolean = false,
    val jelly: Boolean = false,
    val ring: Boolean = false,
    /** How old the pet looks (0 young, 1 at the end of its life): silvery colours and grey eyebrows. */
    val elder: Float = 0f,
) {
    val feet: Boolean get() = !ghost && shape != Shape.DROP

    fun withColors(c: PetColors, sparkle: Boolean) = FormLook(
        c, width, height, ears, antenna, wings, tail, horns, spikes, ghost, crown, headband, eyes, bigBelly,
        sparkles || sparkle, pattern, back, muzzle, shape, aura, antlers, claws, faceplate, jelly, ring, elder,
    )
}

private fun c(hex: Long): Int = hex.toInt()

/** Builds a full palette from a few key colours. */
private fun pal(body: Long, accent: Long, accent2: Long, iris: Long, belly: Long? = null, eye: Long = 0xFF2A1F3D, cheek: Long = 0xFFFF9BB5, extra: Long? = null): PetColors {
    val b = c(body)
    return PetColors(
        body = b,
        light = Colors.lighten(b, 0.62f),
        shade = Colors.darken(b, 0.3f),
        belly = belly?.let { c(it) } ?: Colors.lighten(b, 0.7f),
        accent = c(accent),
        accent2 = c(accent2),
        cheek = c(cheek),
        iris = c(iris),
        eye = c(eye),
        extra = extra?.let { c(it) } ?: c(accent2),
    )
}

private fun catLook(width: Float, height: Float, bigBelly: Boolean = false, sparkles: Boolean = false) = FormLook(
    pal(0xFFF2A65A, 0xFFFFB3C6, 0xFFD2722A, 0xFFE8A93A, belly = 0xFFFFF0DC),
    width, height,
    ears = Ears.CAT, tail = Tail.KITTY, muzzle = Muzzle.CAT, eyes = EyeStyle.CAT,
    pattern = Pattern.TABBY, bigBelly = bigBelly, sparkles = sparkles,
)

/** Real rabbits: the colours come from the fur. */
private fun bunnyLook(width: Float, height: Float, ears: Ears = Ears.BUNNY, bigBelly: Boolean = false, sparkles: Boolean = false, scruffy: Boolean = false) = FormLook(
    pal(0xFFA8805E, 0xFFFFB3C6, 0xFF7A5A40, 0xFF2A1F2A, belly = 0xFFF2E2CC),
    width, height,
    ears = ears, tail = Tail.POM, muzzle = Muzzle.BUNNY, bigBelly = bigBelly, sparkles = sparkles, spikes = scruffy,
)

/** Real hamsters: round, with tiny ears and big cheeks. */
private fun hamsterLook(width: Float, height: Float, bigBelly: Boolean = true, sparkles: Boolean = false, scruffy: Boolean = false) = FormLook(
    pal(0xFFDDA266, 0xFFFFB3C6, 0xFFB4783E, 0xFF2A1F2A, belly = 0xFFFFF4E2),
    width, height,
    ears = Ears.PETAL, muzzle = Muzzle.BUNNY, bigBelly = bigBelly, sparkles = sparkles, spikes = scruffy,
)

/** Real guinea pigs: long and round, with petal ears and no tail. */
private fun guineaLook(width: Float, height: Float, mane: Boolean = false, sparkles: Boolean = false, scruffy: Boolean = false) = FormLook(
    pal(0xFFFAF6F0, 0xFFFFB3C6, 0xFFD88A3A, 0xFF2A1F2A, belly = 0xFFFFFFFF, extra = 0xFF3A3436),
    width, height,
    ears = Ears.PETAL, muzzle = Muzzle.BUNNY, back = if (mane) Back.MANE else Back.NONE, sparkles = sparkles, spikes = scruffy,
)

object Looks {
    private val looks: Map<Form, FormLook> = mapOf(
        Form.EGG to FormLook(
            PetColors(c(0xFFFFF4E4), c(0xFFFFFFFF), c(0xFFE6C39B), c(0xFFFFFBF4), c(0xFFFF8FB8), c(0xFF8FD3FF), c(0xFFFFB5C8), c(0xFF000000)),
            width = 0.8f, height = 1f,
        ),
        Form.BABY to FormLook(
            PetColors(c(0xFFFFC2DC), c(0xFFFFEAF4), c(0xFFE27FAE), c(0xFFFFF2F8), c(0xFF5FD39F), c(0xFFFF7FAF), c(0xFFFF7FA8), c(0xFF6A4FB3)),
            width = 0.94f, height = 0.8f, antenna = Antenna.SPROUT,
        ),
        Form.HOPSI to FormLook(
            PetColors(c(0xFFCBB6FF), c(0xFFF0E8FF), c(0xFF8C70DD), c(0xFFF7F2FF), c(0xFFFFADCD), c(0xFFFFFFFF), c(0xFFFF8FC0), c(0xFF4E6BDB)),
            width = 0.88f, height = 0.9f, ears = Ears.BUNNY,
        ),
        Form.GRUMMEL to FormLook(
            PetColors(c(0xFFAEBBD2), c(0xFFE0E6F1), c(0xFF6F7D9A), c(0xFFE8ECF3), c(0xFF6A7894), c(0xFF56627A), c(0xFFE7A0B6), c(0xFF3D4A63)),
            width = 0.94f, height = 0.86f, spikes = true, eyes = EyeStyle.GRUMPY,
        ),
        Form.FLITZER to FormLook(
            PetColors(c(0xFF8CCEFF), c(0xFFDAF0FF), c(0xFF4A97D8), c(0xFFE8F7FF), c(0xFFFF5E6C), c(0xFFFFFFFF), c(0xFFFF93AA), c(0xFF2F6BC4)),
            width = 0.86f, height = 0.96f, ears = Ears.CAT, wings = Wings.SMALL, headband = true,
        ),
        Form.MAMPFI to FormLook(
            PetColors(c(0xFFFFC199), c(0xFFFFE9D8), c(0xFFE0845A), c(0xFFFFF2E7), c(0xFFFF8A5C), c(0xFFFFD27D), c(0xFFFF8C8C), c(0xFF8A4B2F)),
            width = 1.1f, height = 0.86f, ears = Ears.ROUND, bigBelly = true,
        ),
        Form.LUMI to FormLook(
            PetColors(c(0xFF95EAC4), c(0xFFDDFFF1), c(0xFF45BD8F), c(0xFFEDFFF7), c(0xFFFFE35C), c(0xFFFFFFFF), c(0xFFFFA0BC), c(0xFF1F8A69)),
            width = 0.88f, height = 0.96f, antenna = Antenna.GLOW,
        ),
        Form.STACHLI to FormLook(
            PetColors(c(0xFFB8A3CF), c(0xFFE6DCF2), c(0xFF836AA3), c(0xFFEDE6F5), c(0xFF5E4B7B), c(0xFF4A3B61), c(0xFFE698BE), c(0xFF5A3D82)),
            width = 0.92f, height = 0.96f, ears = Ears.CAT, spikes = true, eyes = EyeStyle.GRUMPY,
        ),
        Form.DRAKO to FormLook(
            PetColors(c(0xFF84DD96), c(0xFFD2F8DA), c(0xFF3FA75F), c(0xFFF2FFE3), c(0xFFFFC94F), c(0xFF3C9E88), c(0xFFFF97A6), c(0xFF2F7A3F)),
            width = 0.94f, height = 1f, wings = Wings.DRAGON, tail = Tail.DRAGON, horns = true,
        ),
        Form.STELLARIS to FormLook(
            PetColors(c(0xFFB3AEFF), c(0xFFEAE8FF), c(0xFF7670E6), c(0xFFF4F2FF), c(0xFFFFD54F), c(0xFFFFFFFF), c(0xFFFFA6D0), c(0xFF4B3FC7)),
            width = 0.92f, height = 1f, antenna = Antenna.STAR, wings = Wings.ANGEL, sparkles = true,
        ),
        Form.MOCHI_KOENIG to FormLook(
            PetColors(c(0xFFFFEFF4), c(0xFFFFFFFF), c(0xFFE8B2C6), c(0xFFFFFFFF), c(0xFFFFC53A), c(0xFFFF6F91), c(0xFFFF9BB7), c(0xFFA0476A)),
            width = 1.16f, height = 0.86f, ears = Ears.ROUND, crown = true, bigBelly = true,
        ),
        Form.SCHATTLING to FormLook(
            PetColors(c(0xFF6C6890), c(0xFFA3A0CC), c(0xFF433F66), c(0xFF8A86B2), c(0xFFAEEBFF), c(0xFF35314F), c(0xFF9A86CC), c(0xFFAEEBFF), c(0xFFAEEBFF)),
            width = 0.88f, height = 1f, ghost = true, eyes = EyeStyle.GLOW,
        ),
        Form.AURELIUS to FormLook(
            pal(0xFFFFD86B, 0xFFFFF4C2, 0xFFFFFFFF, 0xFFB86B00),
            width = 0.96f, height = 0.96f, ears = Ears.ROUND, wings = Wings.ANGEL, antenna = Antenna.HALO, crown = true, sparkles = true,
        ),

        // Wald
        Form.MOOSI to FormLook(pal(0xFF9ED67A, 0xFF5FB35A, 0xFFFFE08A, 0xFF3E7A2E), 0.96f, 0.8f, antenna = Antenna.CLOVER, pattern = Pattern.SPOTS),
        Form.ZWEIGI to FormLook(pal(0xFF8FCB84, 0xFF6FB04F, 0xFFB08457, 0xFF2F6B3A), 0.9f, 0.92f, ears = Ears.LEAF, antenna = Antenna.SPROUT),
        Form.FARNI to FormLook(pal(0xFFE0A06A, 0xFF7FC46A, 0xFFFFF0DC, 0xFF6B3E1E), 0.88f, 0.96f, ears = Ears.FOX, tail = Tail.FLUFFY, muzzle = Muzzle.NOSE, antenna = Antenna.SPROUT),
        Form.KNORRI to FormLook(pal(0xFFA88A6A, 0xFF6E8F4A, 0xFF7A5E43, 0xFF4A3524), 0.96f, 0.9f, pattern = Pattern.STRIPES, spikes = true, eyes = EyeStyle.GRUMPY, antenna = Antenna.SPROUT),
        Form.HIRSCHLING to FormLook(pal(0xFFC9956A, 0xFF7FC46A, 0xFFF6E3C8, 0xFF5A3418), 0.88f, 1f, ears = Ears.HORSE, antlers = true, pattern = Pattern.SPOTS, muzzle = Muzzle.NOSE),
        Form.PILZBAER to FormLook(pal(0xFFB88A63, 0xFFE8533F, 0xFFFFFFFF, 0xFF4A2E1A), 1.08f, 0.88f, ears = Ears.ROUND, antenna = Antenna.MUSHROOM, muzzle = Muzzle.SNOUT, bigBelly = true),
        Form.WALDHUETER to FormLook(pal(0xFF7CC08F, 0xFFFF9EC2, 0xFF5E9E5A, 0xFF2E6B45), 0.92f, 1f, ears = Ears.LEAF, antenna = Antenna.FLOWER, tail = Tail.LEAF, aura = Aura.LEAVES),
        Form.WELTENBAUM to FormLook(pal(0xFF6FB88A, 0xFFFFE680, 0xFFA6E3FF, 0xFF1F5E3E), 0.96f, 1.02f, ears = Ears.LEAF, antlers = true, back = Back.MANE, aura = Aura.STARS, sparkles = true),

        // Meer
        Form.BLUBB to FormLook(pal(0xFF8ED8FF, 0xFFFFFFFF, 0xFF5AB8F0, 0xFF1F6FA8), 0.9f, 0.95f, shape = Shape.DROP, jelly = true, aura = Aura.BUBBLES),
        Form.FLOSSI to FormLook(pal(0xFF7CC9F0, 0xFFFFB0C8, 0xFF4E9ED6, 0xFF1C5E96), 0.94f, 0.86f, ears = Ears.FIN, tail = Tail.FISH),
        Form.WELLI to FormLook(pal(0xFF5EC3D8, 0xFFFF9A4D, 0xFF2E93B0, 0xFF12607A), 0.88f, 0.98f, antenna = Antenna.CREST, tail = Tail.FISH, headband = true),
        Form.KRABBO to FormLook(pal(0xFFFF8A6E, 0xFFD94C3A, 0xFFFFD2C4, 0xFF7A1E12), 1.08f, 0.82f, back = Back.SHELL, claws = true, eyes = EyeStyle.GRUMPY),
        Form.DELFINO to FormLook(pal(0xFF8FB8E8, 0xFFFFFFFF, 0xFF5A8BCB, 0xFF24497E, belly = 0xFFF2F8FF), 0.86f, 1.02f, antenna = Antenna.CREST, tail = Tail.FISH, muzzle = Muzzle.SNOUT),
        Form.WALBERT to FormLook(pal(0xFF6E9AD6, 0xFFBFE6FF, 0xFF4B72B3, 0xFF1F3F7A, belly = 0xFFE8F4FF), 1.16f, 0.86f, antenna = Antenna.DROPLET, tail = Tail.FISH, bigBelly = true),
        Form.MEERLI to FormLook(pal(0xFFB9A4FF, 0xFF6FE0D0, 0xFFFFC2E0, 0xFF4B3DB8), 0.9f, 1f, antenna = Antenna.PEARL, tail = Tail.FISH, ears = Ears.FIN),
        Form.AQUARION to FormLook(pal(0xFF4FB3D9, 0xFFFFF4E0, 0xFF2E7FB0, 0xFF0E4A6E), 0.96f, 1.02f, ears = Ears.FIN, tail = Tail.FISH, crown = true, aura = Aura.BUBBLES, sparkles = true),

        // Feuer
        Form.FUNKI to FormLook(pal(0xFFFFB24D, 0xFFFF5A36, 0xFFFFE680, 0xFF8A2E00), 0.86f, 0.92f, shape = Shape.DROP, antenna = Antenna.FLAME),
        Form.GLUTI to FormLook(pal(0xFFFF8A4D, 0xFFFFD14D, 0xFFFF4A2E, 0xFF7A1E00), 0.92f, 0.9f, antenna = Antenna.FLAME, tail = Tail.FLAME),
        Form.FLAMMO to FormLook(pal(0xFFFF7A5C, 0xFFFFD166, 0xFFFFA24D, 0xFF7A1E12, belly = 0xFFFFE8C8), 0.92f, 0.96f, wings = Wings.SMALL, horns = true, tail = Tail.DRAGON),
        Form.ASCHO to FormLook(pal(0xFF8C7A86, 0xFFFF6A3D, 0xFF5E5068, 0xFFFF6A3D), 0.94f, 0.92f, eyes = EyeStyle.GRUMPY, aura = Aura.SMOKE, pattern = Pattern.CRACKS, spikes = true),
        Form.PYRO to FormLook(pal(0xFFFF5E4D, 0xFFFFC94D, 0xFFFF8A3D, 0xFF7A0E0E, belly = 0xFFFFE0B8), 0.9f, 1.02f, wings = Wings.DRAGON, horns = true, tail = Tail.DRAGON, aura = Aura.FLAMES),
        Form.MAGMO to FormLook(pal(0xFF8A3D3D, 0xFFFF8A2E, 0xFFFFD14D, 0xFFFFB02E, eye = 0xFFFFC94D), 1.14f, 0.88f, pattern = Pattern.CRACKS, bigBelly = true, aura = Aura.SMOKE, eyes = EyeStyle.GLOW),
        Form.SALAMANDO to FormLook(pal(0xFFFFB03A, 0xFF2E2A3A, 0xFFFF6A3D, 0xFF5A2A00), 0.84f, 1f, pattern = Pattern.SPOTS, tail = Tail.CAT, antenna = Antenna.FLAME),
        Form.PHOENIX to FormLook(pal(0xFFFF6A3D, 0xFFFFE066, 0xFFFF9A2E, 0xFF8A1E00), 0.9f, 1f, wings = Wings.FEATHER, antenna = Antenna.FLAME, tail = Tail.FLAME, aura = Aura.FLAMES, sparkles = true),

        // Urzeit
        Form.DINOLINO to FormLook(pal(0xFF9ADB8A, 0xFFFFF4E4, 0xFF6FBF5A, 0xFF2E6B2A), 0.94f, 0.84f, antenna = Antenna.SHELL_CAP, pattern = Pattern.SPOTS),
        Form.RAPTI to FormLook(pal(0xFF7FD3B0, 0xFFFF9A5C, 0xFF4FAE8C, 0xFF1F5E48), 0.86f, 0.98f, antenna = Antenna.CREST, tail = Tail.DINO, muzzle = Muzzle.SNOUT, pattern = Pattern.STRIPES),
        Form.TRIKI to FormLook(pal(0xFFA7C77A, 0xFFFFE8B8, 0xFFE58B5A, 0xFF4A5E1E), 1.02f, 0.9f, back = Back.FRILL, horns = true, muzzle = Muzzle.SNOUT, tail = Tail.DINO),
        Form.STEGI to FormLook(pal(0xFF8FB0A0, 0xFFE8A25A, 0xFF6E8F80, 0xFF2E4A40), 1.04f, 0.88f, back = Back.PLATES, tail = Tail.DINO, eyes = EyeStyle.GRUMPY),
        Form.REXI to FormLook(pal(0xFF6FCB6A, 0xFFFFE08A, 0xFF4E9E4A, 0xFF1F5A1A, belly = 0xFFE8F8C8), 0.96f, 1.02f, tail = Tail.DINO, muzzle = Muzzle.FANGS, spikes = true),
        Form.BRONTI to FormLook(pal(0xFF9AB6E0, 0xFFFFD6A8, 0xFF6F8FC4, 0xFF28406E), 1.1f, 0.9f, tail = Tail.DINO, pattern = Pattern.SPOTS, bigBelly = true),
        Form.PTERO to FormLook(pal(0xFFD9A07A, 0xFFFF8A5C, 0xFFC77A55, 0xFF5A2E14), 0.86f, 0.96f, wings = Wings.BAT, antenna = Antenna.CREST, muzzle = Muzzle.BEAK),
        Form.URZEITKOENIG to FormLook(pal(0xFF5E9E5A, 0xFFFFD24D, 0xFF3E7A3A, 0xFF1F3A12), 1.02f, 1.02f, back = Back.PLATES, tail = Tail.DINO, crown = true, sparkles = true),

        // Techno
        Form.BIEPI to FormLook(pal(0xFFC9D6E8, 0xFF5AC8FF, 0xFFFF6A8A, 0xFF5AC8FF, eye = 0xFF6FE8FF), 0.9f, 0.84f, shape = Shape.SQUARE, antenna = Antenna.BOLT, faceplate = true, eyes = EyeStyle.GLOW, pattern = Pattern.PANELS),
        Form.SCHRAUBI to FormLook(pal(0xFFFFC96A, 0xFF5AC8FF, 0xFF8A94A8, 0xFF5AC8FF, eye = 0xFF6FE8FF), 0.92f, 0.92f, shape = Shape.SQUARE, antenna = Antenna.BOLT, ears = Ears.ROUND, faceplate = true, eyes = EyeStyle.GLOW, pattern = Pattern.PANELS),
        Form.BLITZBOT to FormLook(pal(0xFF6FA8FF, 0xFFFFE14D, 0xFF3A6FD6, 0xFFFFF36B, eye = 0xFFFFF36B), 0.88f, 0.98f, shape = Shape.SQUARE, antenna = Antenna.BOLT, tail = Tail.BOLT, aura = Aura.BOLTS, faceplate = true, eyes = EyeStyle.GLOW),
        Form.ROSTI to FormLook(pal(0xFFC98A5E, 0xFF8A5A3A, 0xFF6E7A8A, 0xFFFFB85A, eye = 0xFFFFB85A), 0.96f, 0.9f, shape = Shape.SQUARE, antenna = Antenna.BOLT, faceplate = true, eyes = EyeStyle.GLOW, pattern = Pattern.SPOTS),
        Form.RAKETI to FormLook(pal(0xFFE8ECF6, 0xFFFF5A5A, 0xFFB0B8C8, 0xFF6FE8FF, eye = 0xFF6FE8FF), 0.9f, 1f, shape = Shape.SQUARE, wings = Wings.JET, antenna = Antenna.BOLT, faceplate = true, eyes = EyeStyle.GLOW, pattern = Pattern.PANELS),
        Form.MAMPFBOT to FormLook(pal(0xFFB8E0C8, 0xFFFF8A8A, 0xFFFFFFFF, 0xFF6FE8FF, eye = 0xFF6FE8FF), 1.08f, 0.9f, shape = Shape.SQUARE, antenna = Antenna.CHEF, faceplate = true, eyes = EyeStyle.GLOW, bigBelly = true, pattern = Pattern.PANELS),
        Form.ROBORITTER to FormLook(pal(0xFF9AA8C8, 0xFFFF5A6E, 0xFFFFD24D, 0xFF6FE8FF, eye = 0xFF6FE8FF), 0.94f, 1f, shape = Shape.SQUARE, antenna = Antenna.CREST, faceplate = true, eyes = EyeStyle.GLOW, pattern = Pattern.PANELS),
        Form.CHROMDRACHE to FormLook(pal(0xFFB8C4DC, 0xFF6FE8FF, 0xFF8A9AB8, 0xFF6FE8FF, eye = 0xFF6FE8FF), 0.94f, 1.02f, wings = Wings.DRAGON, horns = true, tail = Tail.DRAGON, faceplate = true, eyes = EyeStyle.GLOW, pattern = Pattern.PANELS, aura = Aura.BOLTS, sparkles = true),

        // Einhorn
        Form.PONYCHEN to FormLook(pal(0xFFFFF0F6, 0xFFFFB3D9, 0xFFB9A4FF, 0xFF8A5AD6), 0.92f, 0.88f, ears = Ears.HORSE, antenna = Antenna.UNICORN, back = Back.MANE, tail = Tail.RAINBOW),
        Form.GLITZI to FormLook(pal(0xFFE8DCFF, 0xFFFFD1EC, 0xFF8FD8FF, 0xFF6A4FC8), 0.9f, 0.92f, ears = Ears.HORSE, antenna = Antenna.UNICORN, back = Back.MANE, pattern = Pattern.STARS, sparkles = true),
        Form.REGENBOGENHUF to FormLook(pal(0xFFFFFFFF, 0xFFFF8FB8, 0xFF8FD8FF, 0xFF7A4FD6), 0.9f, 0.98f, ears = Ears.HORSE, antenna = Antenna.UNICORN, back = Back.MANE, tail = Tail.RAINBOW),
        Form.WOLKI to FormLook(pal(0xFFD8E4F4, 0xFFB0C4E8, 0xFFFFFFFF, 0xFF4A5E8A), 1.02f, 0.9f, ears = Ears.HORSE, back = Back.MANE, tail = Tail.FLUFFY, eyes = EyeStyle.GRUMPY),
        Form.PEGASUS to FormLook(pal(0xFFFFFFFF, 0xFF8FD8FF, 0xFFFFE08A, 0xFF3A6FD6), 0.9f, 1f, ears = Ears.HORSE, back = Back.MANE, wings = Wings.ANGEL, tail = Tail.FLUFFY),
        Form.ZUCKERHORN to FormLook(pal(0xFFFFD1E8, 0xFFFFF4A8, 0xFF9AE8FF, 0xFFC23A7A), 1.06f, 0.9f, ears = Ears.HORSE, antenna = Antenna.UNICORN, back = Back.MANE, tail = Tail.FLUFFY, pattern = Pattern.SPRINKLES),
        Form.STERNHORN to FormLook(pal(0xFFF6F2FF, 0xFFFFD24D, 0xFFFF9BD1, 0xFF5A3DC8), 0.92f, 1f, ears = Ears.HORSE, antenna = Antenna.UNICORN, back = Back.MANE, tail = Tail.RAINBOW, sparkles = true),
        Form.AURORA to FormLook(pal(0xFFB8F0E8, 0xFFB98AFF, 0xFF6FE8C8, 0xFF2E6B8A), 0.92f, 1f, ears = Ears.HORSE, antenna = Antenna.UNICORN, back = Back.MANE, wings = Wings.ANGEL, tail = Tail.RAINBOW, aura = Aura.STARS, sparkles = true),

        // Blüte
        Form.KNOSPI to FormLook(pal(0xFFA8E0A0, 0xFFFF9EC2, 0xFF7FC46A, 0xFF2E6B3A), 0.9f, 0.9f, antenna = Antenna.BUD),
        Form.BLUEMI to FormLook(pal(0xFFFFE0EC, 0xFFFF7FAF, 0xFFFFE066, 0xFFB83A7A), 0.9f, 0.92f, antenna = Antenna.FLOWER, ears = Ears.LEAF),
        Form.FEELI to FormLook(pal(0xFFE0F0FF, 0xFFFF9EDC, 0xFFC8B8FF, 0xFF6A4FC8), 0.86f, 0.98f, wings = Wings.FAIRY, antenna = Antenna.GLOW, sparkles = true),
        Form.DORNI to FormLook(pal(0xFF7FB88A, 0xFFE84A6A, 0xFF4E8A5A, 0xFF1F4A2E), 0.94f, 0.94f, spikes = true, antenna = Antenna.FLOWER, eyes = EyeStyle.GRUMPY),
        Form.LIBELLA to FormLook(pal(0xFF7FE0D8, 0xFF4FAEE8, 0xFFB8F0FF, 0xFF12607A), 0.84f, 1.02f, wings = Wings.FAIRY, antenna = Antenna.ANTENNAE, pattern = Pattern.STRIPES),
        Form.HUMMELCHEN to FormLook(pal(0xFFFFD24D, 0xFF3A2E2A, 0xFFE8F4FF, 0xFF3A2E2A), 1.06f, 0.92f, wings = Wings.BEE, pattern = Pattern.STRIPES, antenna = Antenna.ANTENNAE, bigBelly = true),
        Form.ROSALIE to FormLook(pal(0xFFFFD6E8, 0xFFFF6FA8, 0xFFB8F0C8, 0xFFB83A7A), 0.9f, 1f, wings = Wings.FAIRY, antenna = Antenna.FLOWER, aura = Aura.LEAVES),
        Form.TITANIA to FormLook(pal(0xFFF0E0FF, 0xFFFFD24D, 0xFFFFB3E6, 0xFF7A3DC8), 0.9f, 1f, wings = Wings.FAIRY, crown = true, aura = Aura.HEARTS, sparkles = true),

        // Stern
        Form.STERNCHEN to FormLook(pal(0xFFFFE680, 0xFFFFF7C8, 0xFFFFB84D, 0xFFB86B00), 0.92f, 0.9f, antenna = Antenna.STAR, sparkles = true),
        Form.KOMETI to FormLook(pal(0xFF9AB8FF, 0xFFFFE680, 0xFFE0E8FF, 0xFF2E4AB8), 0.9f, 0.94f, tail = Tail.COMET, antenna = Antenna.STAR),
        Form.MONDI to FormLook(pal(0xFF3E4A8A, 0xFFFFF0B0, 0xFF6A78C8, 0xFFFFF0B0, eye = 0xFF1A1F3D), 0.92f, 0.96f, antenna = Antenna.MOON, pattern = Pattern.STARS),
        Form.NEBULI to FormLook(pal(0xFFB090E8, 0xFFFF9AD8, 0xFF7AD8FF, 0xFF4A2E8A), 0.94f, 0.94f, pattern = Pattern.GALAXY, aura = Aura.SMOKE, eyes = EyeStyle.GRUMPY),
        Form.ASTRO to FormLook(pal(0xFFFFFFFF, 0xFFFF8A4D, 0xFF8FD8FF, 0xFF2E4A8A), 0.92f, 1f, antenna = Antenna.HELMET, headband = true),
        Form.SATURNO to FormLook(pal(0xFFFFC98A, 0xFFB98AFF, 0xFFFFE8C8, 0xFF8A4A1E), 1.14f, 0.88f, ring = true, bigBelly = true, pattern = Pattern.BANDS),
        Form.GALAXIA to FormLook(pal(0xFF5A4AB8, 0xFFFF9AD8, 0xFF7AD8FF, 0xFFFFE680, eye = 0xFF1A1F3D), 0.92f, 1f, pattern = Pattern.GALAXY, aura = Aura.STARS, antenna = Antenna.STAR),
        Form.SOLARIS to FormLook(pal(0xFFFFC23D, 0xFFFFF4A8, 0xFFFF7A2E, 0xFFB84A00), 0.94f, 1f, antenna = Antenna.RAYS, aura = Aura.FLAMES, sparkles = true),

        // Zucker
        Form.BONBONI to FormLook(pal(0xFFFF9EC8, 0xFFFFFFFF, 0xFF8FD8FF, 0xFFC23A7A), 0.96f, 0.86f, ears = Ears.WRAPPER, pattern = Pattern.SWIRL),
        Form.LOLLI to FormLook(pal(0xFFFFB0D8, 0xFFFF5A9A, 0xFFFFFFFF, 0xFFB83A7A), 0.9f, 0.94f, antenna = Antenna.SWIRL, pattern = Pattern.SPRINKLES),
        Form.GUMMIBAER to FormLook(pal(0xFFFF6A7A, 0xFFFFB0B8, 0xFFFF3A5A, 0xFF8A1E2E), 0.94f, 0.96f, ears = Ears.ROUND, jelly = true),
        Form.LAKRITZI to FormLook(pal(0xFF3A2E3A, 0xFFFF6AB0, 0xFF5A4A5A, 0xFFFF9AD8, eye = 0xFF120C12), 0.94f, 0.94f, shape = Shape.SQUARE, pattern = Pattern.BANDS, eyes = EyeStyle.GRUMPY),
        Form.POPCORNI to FormLook(pal(0xFFFFF4D8, 0xFFFFD24D, 0xFFFF5A5A, 0xFF8A5A1E), 0.96f, 0.94f, pattern = Pattern.SPOTS, back = Back.MANE),
        Form.TORTELLA to FormLook(pal(0xFFFFE0C8, 0xFFFF6A8A, 0xFFFFFFFF, 0xFF8A3A1E), 1.1f, 0.9f, shape = Shape.SQUARE, pattern = Pattern.BANDS, antenna = Antenna.CHERRY),
        Form.MAKRONI to FormLook(pal(0xFFC8E8C0, 0xFFFFB3D1, 0xFFFFFFFF, 0xFF4A7A3A), 1.14f, 0.82f, pattern = Pattern.BANDS, crown = true),
        Form.KARAMELLA to FormLook(pal(0xFFE8A85A, 0xFFFFE8B8, 0xFFFF7AB0, 0xFF6A3A12), 0.96f, 0.98f, crown = true, pattern = Pattern.SWIRL, aura = Aura.HEARTS, sparkles = true),

        // Frost
        Form.FLOECKCHEN to FormLook(pal(0xFFE8F6FF, 0xFF9AD8FF, 0xFFFFFFFF, 0xFF3A7AB8), 0.92f, 0.9f, antenna = Antenna.CRYSTAL, aura = Aura.SNOW),
        Form.PINGI to FormLook(pal(0xFF3E4A6A, 0xFFFFB84D, 0xFF2A3350, 0xFF1A1F3D, belly = 0xFFFFFFFF), 0.96f, 0.96f, muzzle = Muzzle.BEAK, wings = Wings.SMALL),
        Form.SCHNEEHASE to FormLook(pal(0xFFFFFFFF, 0xFFFFC8DC, 0xFFB8E0FF, 0xFF4A6FD6), 0.9f, 0.92f, ears = Ears.BUNNY, aura = Aura.SNOW),
        Form.EISZAPFI to FormLook(pal(0xFFB8E4FF, 0xFF6FC8FF, 0xFFE8F8FF, 0xFF1F5E96), 0.92f, 0.96f, spikes = true, eyes = EyeStyle.GRUMPY, jelly = true, antenna = Antenna.CRYSTAL),
        Form.POLARIX to FormLook(pal(0xFFF6FAFF, 0xFFB8D8F0, 0xFF9AB8D8, 0xFF2E3A5A), 1.02f, 0.94f, ears = Ears.ROUND, muzzle = Muzzle.SNOUT),
        Form.SCHNEEMO to FormLook(pal(0xFFFFFFFF, 0xFFFF8A3D, 0xFF3A3A4A, 0xFF2A2A3A), 1.08f, 0.92f, muzzle = Muzzle.CARROT, pattern = Pattern.BUTTONS),
        Form.POLARFUCHS to FormLook(pal(0xFFF2F6FF, 0xFF9AC8F0, 0xFFFFFFFF, 0xFF3A6FB8), 0.88f, 0.98f, ears = Ears.FOX, tail = Tail.FLUFFY, muzzle = Muzzle.NOSE),
        Form.BOREAS to FormLook(pal(0xFFD8F0FF, 0xFF7AF0D8, 0xFFB98AFF, 0xFF1F5E96), 0.94f, 1.02f, antlers = true, wings = Wings.ANGEL, back = Back.MANE, aura = Aura.SNOW, sparkles = true),

        // Grusel
        Form.KUERBI to FormLook(pal(0xFFFF9A3D, 0xFF4E8A3A, 0xFFFFC66A, 0xFF3A1E00), 1.12f, 0.86f, pattern = Pattern.RIBS, antenna = Antenna.STEM),
        Form.GEISTI to FormLook(pal(0xFFF6F6FF, 0xFFB8B8E8, 0xFFFFFFFF, 0xFF3A3A6A), 0.88f, 1f, ghost = true),
        Form.FLEDDI to FormLook(pal(0xFF6A5A8A, 0xFFFF9AC8, 0xFF4A3A6A, 0xFFFFE066), 0.9f, 0.92f, ears = Ears.BAT, wings = Wings.BAT, muzzle = Muzzle.FANGS),
        Form.MUMMI to FormLook(pal(0xFFF2E8D8, 0xFFC8B89A, 0xFF8A7A6A, 0xFF6FA87A), 0.9f, 0.98f, pattern = Pattern.BANDAGE, eyes = EyeStyle.GRUMPY),
        Form.VAMPI to FormLook(pal(0xFFD8D0F0, 0xFF8A1E3A, 0xFF2A1F3D, 0xFFC21F3A), 0.9f, 1f, ears = Ears.CAT, back = Back.CAPE, muzzle = Muzzle.FANGS),
        Form.SCHLEIMI to FormLook(pal(0xFF8AE86A, 0xFFD8FF9A, 0xFF4EB83A, 0xFF2A5A1E), 1.08f, 0.9f, shape = Shape.DROP, jelly = true, bigBelly = true, aura = Aura.BUBBLES),
        Form.HEXKATZE to FormLook(pal(0xFF3A3450, 0xFF9A6AE8, 0xFFFFD24D, 0xFFFFD24D, eye = 0xFF1A1426), 0.9f, 0.96f, ears = Ears.CAT, tail = Tail.CAT, antenna = Antenna.WITCH, muzzle = Muzzle.WHISKERS),
        Form.KUERBISKOENIG to FormLook(pal(0xFFFF8A2E, 0xFF9A5AE8, 0xFFFFD24D, 0xFF3A1E00, eye = 0xFFFFE066), 1.1f, 0.94f, pattern = Pattern.RIBS, crown = true, aura = Aura.FLAMES, eyes = EyeStyle.GLOW, sparkles = true),

        // Echte Katzen: the colours come from the fur, see [Looks.compose].
        Form.KAETZCHEN to catLook(0.9f, 0.8f),
        Form.JUNGKATZE to catLook(0.88f, 0.9f),
        Form.WILDFANG to catLook(0.9f, 0.88f),
        Form.HALBSTARKE to catLook(0.86f, 0.98f),
        Form.MOTZKATZE to catLook(0.9f, 0.94f),
        Form.MAEUSEJAEGER to catLook(0.84f, 1.02f),
        Form.PUMMELKATZE to catLook(1.14f, 0.9f, bigBelly = true),
        Form.SCHMUSEKATZE to catLook(0.96f, 0.98f),
        Form.STREUNER to catLook(0.9f, 0.96f),
        Form.GLUECKSKATZE to catLook(0.94f, 1.0f, sparkles = true),

        // Dschungel
        Form.LIANI to FormLook(pal(0xFF8FD67A, 0xFF4FA84A, 0xFFFFD84D, 0xFF2E6B2A), 0.94f, 0.82f, antenna = Antenna.SPROUT, tail = Tail.LEAF, pattern = Pattern.SPOTS),
        Form.KOKOSI to FormLook(pal(0xFFB8E08A, 0xFF8A5A3A, 0xFFFFF0D8, 0xFF3A6B2A), 0.92f, 0.9f, antenna = Antenna.SHELL_CAP, ears = Ears.LEAF),
        Form.TUKANI to FormLook(pal(0xFF3A3A4A, 0xFFFF9A2E, 0xFFFFE04D, 0xFF2E8AD8, belly = 0xFFFFF4D8), 0.88f, 0.96f, muzzle = Muzzle.BEAK, wings = Wings.SMALL, antenna = Antenna.CREST),
        Form.SCHLINGI to FormLook(pal(0xFF7AD8A0, 0xFFFF7AB0, 0xFFFFE066, 0xFF2E8A5A), 0.96f, 0.86f, eyes = EyeStyle.GRUMPY, tail = Tail.CAT, pattern = Pattern.ZIGZAG, antenna = Antenna.CREST),
        Form.JAGUARO to FormLook(pal(0xFFF2B85A, 0xFFFFB3A0, 0xFF5A3A1E, 0xFF6A8A2A, belly = 0xFFFFF4E0), 0.88f, 1f, ears = Ears.CAT, tail = Tail.CAT, pattern = Pattern.SPOTS, muzzle = Muzzle.WHISKERS),
        Form.BANANO to FormLook(pal(0xFF5A5060, 0xFFFFE04D, 0xFFB8A8B8, 0xFF3A2A1A, belly = 0xFFD8C8D0), 1.12f, 0.9f, ears = Ears.ROUND, bigBelly = true, muzzle = Muzzle.SNOUT),
        Form.ORCHIDEA to FormLook(pal(0xFFFF8AC8, 0xFFB98AFF, 0xFF7AE8C8, 0xFF8A2A6A), 0.88f, 1f, wings = Wings.FEATHER, antenna = Antenna.FLOWER, tail = Tail.RAINBOW, muzzle = Muzzle.BEAK),
        Form.SMARAGDLOEWE to FormLook(pal(0xFFF2C46A, 0xFF3AC88A, 0xFFFFE680, 0xFF2E6B3A), 0.96f, 1.02f, back = Back.MANE, ears = Ears.ROUND, tail = Tail.CAT, crown = true, aura = Aura.LEAVES, muzzle = Muzzle.NOSE, sparkles = true),

        // Wüste
        Form.SANDI to FormLook(pal(0xFFF4D8A0, 0xFFE8A85A, 0xFFFFF4D8, 0xFF8A5A2A), 0.9f, 0.9f, shape = Shape.DROP, pattern = Pattern.SPOTS),
        Form.FENNI to FormLook(pal(0xFFF2C890, 0xFFFFB3A0, 0xFFFFF4E4, 0xFF6A3A1A), 0.88f, 0.94f, ears = Ears.FOX, tail = Tail.FLUFFY, muzzle = Muzzle.NOSE),
        Form.SKARABI to FormLook(pal(0xFF3A8A9A, 0xFFFFD24D, 0xFF2A5A7A, 0xFFFFE680), 0.98f, 0.88f, back = Back.SHELL, antenna = Antenna.ANTENNAE),
        Form.KAKTUSI to FormLook(pal(0xFF7AC07A, 0xFFFF8AC8, 0xFF4E8A4E, 0xFF2E5A2E), 0.9f, 1f, spikes = true, antenna = Antenna.FLOWER, eyes = EyeStyle.GRUMPY, pattern = Pattern.STRIPES),
        Form.WIRBELI to FormLook(pal(0xFFE8C88A, 0xFFFFE8B8, 0xFFC89A5A, 0xFF8A5A2A), 0.86f, 1f, aura = Aura.SMOKE, tail = Tail.COMET, antenna = Antenna.SWIRL),
        Form.KAMELIO to FormLook(pal(0xFFD8A870, 0xFFFFE0B0, 0xFFB8885A, 0xFF5A3A1A), 1.14f, 0.9f, ears = Ears.HORSE, bigBelly = true, muzzle = Muzzle.SNOUT),
        Form.SPHINXI to FormLook(pal(0xFFE8C080, 0xFF3A6AD8, 0xFFFFD24D, 0xFF3A6AD8), 0.9f, 1f, ears = Ears.CAT, tail = Tail.CAT, headband = true, muzzle = Muzzle.NOSE),
        Form.PHARAONI to FormLook(pal(0xFFFFD86A, 0xFF3A6AD8, 0xFF3A6AD8, 0xFF8A5A00), 0.94f, 1.02f, crown = true, back = Back.CAPE, ears = Ears.CAT, aura = Aura.STARS, sparkles = true),

        // Edelstein
        Form.KRISTALLI to FormLook(pal(0xFFC8E8FF, 0xFF8AD8FF, 0xFFFFFFFF, 0xFF3A7AB8), 0.88f, 0.9f, shape = Shape.DROP, antenna = Antenna.CRYSTAL, sparkles = true, jelly = true),
        Form.RUBINI to FormLook(pal(0xFFFF6A8A, 0xFFFFD0DC, 0xFFC21F4A, 0xFF8A1E3A), 0.92f, 0.92f, antenna = Antenna.CRYSTAL, jelly = true, pattern = Pattern.STARS),
        Form.SAPHIRO to FormLook(pal(0xFF5A8AE8, 0xFFB8D8FF, 0xFF2E5AB8, 0xFF1A3A8A), 0.88f, 0.98f, ears = Ears.FIN, wings = Wings.SMALL, jelly = true),
        Form.GEODI to FormLook(pal(0xFF9A98A8, 0xFFB98AFF, 0xFF6E6C7A, 0xFFB98AFF), 0.98f, 0.9f, spikes = true, eyes = EyeStyle.GRUMPY, pattern = Pattern.CRACKS),
        Form.DIAMANTO to FormLook(pal(0xFFE0F4FF, 0xFF8AE0FF, 0xFFB8D0F0, 0xFF2E7AB8), 0.92f, 1.02f, wings = Wings.DRAGON, horns = true, tail = Tail.DRAGON, sparkles = true, jelly = true),
        Form.BERNSTEINI to FormLook(pal(0xFFF2A84A, 0xFFFFE0A0, 0xFFC87A2A, 0xFF6A3A0A), 1.12f, 0.9f, ears = Ears.ROUND, bigBelly = true, jelly = true, muzzle = Muzzle.SNOUT),
        Form.OPALIA to FormLook(pal(0xFFF0E8FF, 0xFF8AE8D8, 0xFFFFB8E0, 0xFF6A4AC8), 0.9f, 1f, wings = Wings.FAIRY, antenna = Antenna.CRYSTAL, pattern = Pattern.STARS, sparkles = true),
        Form.PRISMARA to FormLook(pal(0xFFF8F4FF, 0xFFB98AFF, 0xFF8AE8FF, 0xFF5A3DC8), 0.92f, 1.02f, ears = Ears.HORSE, antenna = Antenna.UNICORN, back = Back.MANE, tail = Tail.RAINBOW, aura = Aura.STARS, sparkles = true, jelly = true),

        // Echte Hasen
        Form.HAESCHEN to bunnyLook(0.94f, 0.8f),
        Form.JUNGHASE to bunnyLook(0.9f, 0.88f),
        Form.HOPPELHASE to bunnyLook(0.88f, 0.96f),
        Form.TROTZHASE to bunnyLook(0.92f, 0.92f, ears = Ears.LOP),
        Form.SPRINTHASE to bunnyLook(0.84f, 1.02f),
        Form.PUMMELHASE to bunnyLook(1.12f, 0.9f, ears = Ears.LOP, bigBelly = true),
        Form.KUSCHELHASE to bunnyLook(0.98f, 0.96f, ears = Ears.LOP),
        Form.ZAUSELHASE to bunnyLook(0.92f, 0.94f, scruffy = true),
        Form.MONDHASE to bunnyLook(0.92f, 1f, sparkles = true),

        // Echte Hamster
        Form.HAMSTERBABY to hamsterLook(0.96f, 0.78f),
        Form.JUNGHAMSTER to hamsterLook(0.98f, 0.84f),
        Form.FLITZHAMSTER to hamsterLook(0.92f, 0.9f, bigBelly = false),
        Form.GRUMMELHAMSTER to hamsterLook(1f, 0.86f).copy(eyes = EyeStyle.GRUMPY),
        Form.LAUFRADPROFI to hamsterLook(0.9f, 0.96f, bigBelly = false),
        Form.BACKENHAMSTER to hamsterLook(1.18f, 0.88f),
        Form.KUSCHELHAMSTER to hamsterLook(1.04f, 0.9f),
        Form.ZAUSELHAMSTER to hamsterLook(1f, 0.88f, scruffy = true),
        Form.STERNENHAMSTER to hamsterLook(1.02f, 0.92f, sparkles = true).copy(aura = Aura.STARS),

        // Echte Meerschweinchen
        Form.QUIEKCHEN to guineaLook(1.04f, 0.76f),
        Form.JUNGSCHWEINCHEN to guineaLook(1.08f, 0.8f),
        Form.WIRBELSCHWEINCHEN to guineaLook(1.08f, 0.86f, mane = true),
        Form.BRUMMSCHWEINCHEN to guineaLook(1.1f, 0.84f).copy(eyes = EyeStyle.GRUMPY),
        Form.POPCORNSCHWEINCHEN to guineaLook(1.02f, 0.9f),
        Form.GURKENSCHWEINCHEN to guineaLook(1.2f, 0.86f).copy(bigBelly = true),
        Form.KUSCHELSCHWEINCHEN to guineaLook(1.12f, 0.88f),
        Form.STRUPPELSCHWEINCHEN to guineaLook(1.1f, 0.86f, scruffy = true),
        Form.SEIDENSCHWEINCHEN to guineaLook(1.1f, 0.9f, mane = true, sparkles = true),

        // Ältere Formen (alle 10 Level eine neue Stufe, zwei Wege)
        // Knuffel
        Form.WIRBELKNUFFEL to FormLook(pal(0xFFFF9FCB, 0xFF7FD3FF, 0xFFFFE066, 0xFF6A4FB3), 0.86f, 0.98f, ears = Ears.CAT, tail = Tail.FLUFFY, headband = true, pattern = Pattern.STRIPES),
        Form.PLUESCHKNUFFEL to FormLook(pal(0xFFFFD6E8, 0xFFFFADCD, 0xFFFFFFFF, 0xFFA0476A), 1.12f, 0.86f, ears = Ears.ROUND, bigBelly = true, back = Back.MANE, tail = Tail.POM),
        Form.ABENTEUERKNUFFEL to FormLook(pal(0xFF8CCEFF, 0xFFFF5E6C, 0xFFFFD27D, 0xFF2F6BC4), 0.88f, 1f, ears = Ears.FOX, tail = Tail.FLUFFY, back = Back.CAPE, headband = true),
        Form.HERZKNUFFEL to FormLook(pal(0xFFFFB8D2, 0xFFFF5C9A, 0xFFFFFFFF, 0xFFC21F5A), 1.06f, 0.9f, ears = Ears.ROUND, pattern = Pattern.HEARTS, aura = Aura.HEARTS, bigBelly = true),
        Form.HIMMELSKNUFFEL to FormLook(pal(0xFFC8E4FF, 0xFFFFE680, 0xFFFFFFFF, 0xFF3A6FD6), 0.9f, 1f, ears = Ears.BUNNY, wings = Wings.FEATHER, antenna = Antenna.GLOW),
        Form.MONDKNUFFEL to FormLook(pal(0xFF9C8CE8, 0xFFFFF0B0, 0xFFE8E0FF, 0xFF3A2E8A), 1.04f, 0.92f, ears = Ears.ROUND, antenna = Antenna.MOON, pattern = Pattern.STARS),
        Form.DRACHENKNUFFEL to FormLook(pal(0xFFFF8A9A, 0xFFFFD24D, 0xFFFF5A6A, 0xFF8A1E3A), 0.94f, 1.02f, wings = Wings.DRAGON, horns = true, tail = Tail.DRAGON, aura = Aura.FLAMES),
        Form.KISSENKOENIG to FormLook(pal(0xFFFFF4FA, 0xFFFFC53A, 0xFFFF9BB7, 0xFFA0476A), 1.18f, 0.86f, ears = Ears.ROUND, crown = true, bigBelly = true, back = Back.CAPE, sparkles = true),
        Form.URKNUFFEL to FormLook(pal(0xFFC8C4D8, 0xFFFFD86B, 0xFFE8E4F0, 0xFF5A4F7A), 0.92f, 0.98f, ears = Ears.CAT, back = Back.MANE, muzzle = Muzzle.WHISKERS, antenna = Antenna.GLOW, wings = Wings.SMALL),
        Form.SILBERKNUFFEL to FormLook(pal(0xFFE6E4F0, 0xFFFFB3C6, 0xFFFFFFFF, 0xFF6A6A8A), 1.08f, 0.9f, ears = Ears.ROUND, back = Back.MANE, muzzle = Muzzle.WHISKERS, aura = Aura.HEARTS),
        Form.STERNENKNUFFEL to FormLook(pal(0xFF5A4AB8, 0xFFFFE680, 0xFF9AD8FF, 0xFFFFE680, eye = 0xFF1A1F3D), 0.94f, 1.02f, ears = Ears.CAT, wings = Wings.ANGEL, pattern = Pattern.GALAXY, antenna = Antenna.STAR, aura = Aura.STARS, sparkles = true),
        Form.ENGELSKNUFFEL to FormLook(pal(0xFFFFF8FC, 0xFFFFE680, 0xFFFFC2DC, 0xFF8A6AD6), 1f, 1f, ears = Ears.ROUND, wings = Wings.ANGEL, antenna = Antenna.HALO, aura = Aura.HEARTS, sparkles = true),
        // Wald
        Form.RINDENSPRINGER to FormLook(pal(0xFFC98A5A, 0xFF7FC46A, 0xFFF6E3C8, 0xFF5A3418), 0.86f, 0.98f, ears = Ears.FOX, tail = Tail.FLUFFY, muzzle = Muzzle.NOSE, antenna = Antenna.SPROUT),
        Form.MOOSKISSEN to FormLook(pal(0xFF9ED67A, 0xFF5FB35A, 0xFFFFE08A, 0xFF3E7A2E), 1.1f, 0.86f, ears = Ears.ROUND, bigBelly = true, pattern = Pattern.SPOTS, antenna = Antenna.CLOVER),
        Form.WALDLAEUFER to FormLook(pal(0xFF8FB86A, 0xFFB08457, 0xFFE8F4D0, 0xFF2F6B3A), 0.86f, 1.02f, ears = Ears.LEAF, tail = Tail.LEAF, back = Back.CAPE, headband = true),
        Form.BEERENBAER to FormLook(pal(0xFFA87A5A, 0xFF8A3AB8, 0xFFE8533F, 0xFF4A2E1A), 1.12f, 0.9f, ears = Ears.ROUND, muzzle = Muzzle.SNOUT, bigBelly = true, pattern = Pattern.SEEDS),
        Form.EICHENHIRSCH to FormLook(pal(0xFFB8885A, 0xFF7FC46A, 0xFFF6E3C8, 0xFF4A2E14), 0.9f, 1.04f, ears = Ears.HORSE, antlers = true, muzzle = Muzzle.NOSE, pattern = Pattern.SPOTS, aura = Aura.LEAVES),
        Form.BLUETENBAER to FormLook(pal(0xFF9AB87A, 0xFFFF9EC2, 0xFFFFE066, 0xFF2E5A2E), 1.12f, 0.9f, ears = Ears.ROUND, back = Back.MANE, antenna = Antenna.FLOWER, aura = Aura.LEAVES, muzzle = Muzzle.SNOUT),
        Form.HAINWAECHTER to FormLook(pal(0xFF6FA87A, 0xFFFFD24D, 0xFFA6E3FF, 0xFF1F4A2E), 0.92f, 1.04f, ears = Ears.LEAF, antlers = true, back = Back.CAPE, aura = Aura.LEAVES, eyes = EyeStyle.GLOW),
        Form.PILZKOENIG to FormLook(pal(0xFFC8A07A, 0xFFE8533F, 0xFFFFFFFF, 0xFF4A2E1A), 1.14f, 0.9f, ears = Ears.ROUND, antenna = Antenna.MUSHROOM, back = Back.MANE, bigBelly = true, pattern = Pattern.SPOTS, sparkles = true),
        Form.MOOSGEWEIH to FormLook(pal(0xFF9A9A80, 0xFF6FB05A, 0xFFE0E4D0, 0xFF3A4A2E), 0.92f, 1.02f, ears = Ears.HORSE, antlers = true, back = Back.MANE, muzzle = Muzzle.WHISKERS, aura = Aura.LEAVES),
        Form.KNORRBART to FormLook(pal(0xFF8A7A6A, 0xFF8FC46A, 0xFFD8D0C0, 0xFF3A2E24), 1.08f, 0.9f, ears = Ears.ROUND, muzzle = Muzzle.WHISKERS, pattern = Pattern.STRIPES, antenna = Antenna.SPROUT, back = Back.MANE),
        Form.WALDGEIST to FormLook(pal(0xFFB8F0C8, 0xFFFFF4A8, 0xFF7AE8B0, 0xFF1F6B4A), 0.9f, 1.02f, ghost = true, antlers = true, aura = Aura.LEAVES, eyes = EyeStyle.GLOW, sparkles = true),
        Form.LEBENSBAUM to FormLook(pal(0xFF6FB88A, 0xFFFF9EC2, 0xFFFFE680, 0xFF1F5E3E), 1.04f, 1.04f, ears = Ears.LEAF, antlers = true, back = Back.MANE, aura = Aura.STARS, pattern = Pattern.STARS, sparkles = true),
        // Meer
        Form.WELLENREITER to FormLook(pal(0xFF5EC3D8, 0xFFFFFFFF, 0xFF2E93B0, 0xFF12607A), 0.86f, 1f, antenna = Antenna.CREST, tail = Tail.FISH, headband = true),
        Form.MUSCHELCHEN to FormLook(pal(0xFFFFC8D8, 0xFF8ED8FF, 0xFFFFFFFF, 0xFF8A3A5A), 1.06f, 0.88f, back = Back.SHELL, ears = Ears.FIN, antenna = Antenna.PEARL),
        Form.STROEMLING to FormLook(pal(0xFF4F9AD8, 0xFF8AE8FF, 0xFFE8F8FF, 0xFF1F3F7A), 0.84f, 1.02f, ears = Ears.FIN, tail = Tail.FISH, pattern = Pattern.STRIPES, muzzle = Muzzle.SNOUT),
        Form.PERLENWAL to FormLook(pal(0xFF7AA0E0, 0xFFFFFFFF, 0xFFBFE6FF, 0xFF1F3F7A, belly = 0xFFE8F4FF), 1.18f, 0.86f, antenna = Antenna.PEARL, tail = Tail.FISH, bigBelly = true, pattern = Pattern.BUTTONS),
        Form.TIEFSEEFLITZER to FormLook(pal(0xFF2E3A6A, 0xFF6FE8FF, 0xFF4A5A9A, 0xFF6FE8FF, eye = 0xFF6FE8FF), 0.88f, 1f, antenna = Antenna.GLOW, tail = Tail.FISH, ears = Ears.FIN, eyes = EyeStyle.GLOW, pattern = Pattern.SPOTS),
        Form.KORALLENHUETER to FormLook(pal(0xFFFF9A8A, 0xFFFFE0D8, 0xFFFF6A7A, 0xFF8A2A2A), 1.04f, 0.94f, antenna = Antenna.FLOWER, back = Back.FRILL, tail = Tail.FISH, aura = Aura.BUBBLES),
        Form.MEERESDRACHE to FormLook(pal(0xFF3E9AC8, 0xFFB8F0FF, 0xFF2E6AB0, 0xFF0E3A6E), 0.92f, 1.04f, wings = Wings.DRAGON, horns = true, tail = Tail.DRAGON, back = Back.FRILL, aura = Aura.BUBBLES),
        Form.GEZEITENWAL to FormLook(pal(0xFF5A7AC8, 0xFFFFE680, 0xFFBFE6FF, 0xFF1F2F6A, belly = 0xFFE8F0FF), 1.2f, 0.88f, tail = Tail.FISH, bigBelly = true, crown = true, aura = Aura.BUBBLES, sparkles = true),
        Form.ALTERSEEBAER to FormLook(pal(0xFF8A9AB0, 0xFFFFFFFF, 0xFFC8D8E8, 0xFF2E3A5A), 1f, 0.96f, ears = Ears.ROUND, muzzle = Muzzle.WHISKERS, back = Back.MANE, tail = Tail.FISH, headband = true),
        Form.PERLMUTTGREIS to FormLook(pal(0xFFE8E4F4, 0xFFB8F0E8, 0xFFFFD8E8, 0xFF5A5A8A), 1.06f, 0.92f, back = Back.SHELL, antenna = Antenna.PEARL, muzzle = Muzzle.WHISKERS, sparkles = true),
        Form.OZEANOS to FormLook(pal(0xFF3AA8D8, 0xFFFFF4E0, 0xFF8AF0FF, 0xFF0E4A6E), 0.96f, 1.04f, ears = Ears.FIN, tail = Tail.FISH, crown = true, aura = Aura.BUBBLES, jelly = true, eyes = EyeStyle.GLOW, sparkles = true),
        Form.MONDFLUT to FormLook(pal(0xFF4A5AA8, 0xFFFFF0B0, 0xFF9AD8FF, 0xFFFFF0B0, eye = 0xFF1A1F3D), 1f, 1f, antenna = Antenna.MOON, tail = Tail.FISH, pattern = Pattern.STARS, aura = Aura.BUBBLES, jelly = true, sparkles = true),
        // Feuer
        Form.FUNKENFLITZER to FormLook(pal(0xFFFF8A4D, 0xFFFFE066, 0xFFFF4A2E, 0xFF7A1E00), 0.86f, 0.98f, antenna = Antenna.FLAME, tail = Tail.FLAME, headband = true, pattern = Pattern.ZIGZAG),
        Form.GLUTKISSEN to FormLook(pal(0xFFFFB27A, 0xFFFF6A3D, 0xFFFFE680, 0xFF8A2E00), 1.12f, 0.86f, antenna = Antenna.FLAME, bigBelly = true, ears = Ears.ROUND),
        Form.FLAMMENLAEUFER to FormLook(pal(0xFFFF6A4D, 0xFFFFD166, 0xFFFFA24D, 0xFF7A0E0E), 0.86f, 1.02f, ears = Ears.FOX, tail = Tail.FLAME, antenna = Antenna.FLAME, aura = Aura.FLAMES),
        Form.OFENBAER to FormLook(pal(0xFFB86A4A, 0xFFFF8A2E, 0xFFFFD8A8, 0xFF4A1E0A), 1.14f, 0.9f, ears = Ears.ROUND, muzzle = Muzzle.SNOUT, antenna = Antenna.CHEF, bigBelly = true),
        Form.LAVADRACHE to FormLook(pal(0xFF8A2E2E, 0xFFFF8A2E, 0xFFFFD14D, 0xFFFFB02E, eye = 0xFFFFC94D), 0.92f, 1.02f, wings = Wings.DRAGON, horns = true, tail = Tail.DRAGON, pattern = Pattern.CRACKS, eyes = EyeStyle.GLOW),
        Form.KAMINWAECHTER to FormLook(pal(0xFFC87A5A, 0xFFFFC94D, 0xFF8A4A3A, 0xFF4A1E0A), 1.04f, 0.94f, ears = Ears.ROUND, back = Back.MANE, antenna = Antenna.FLAME, pattern = Pattern.BANDS, aura = Aura.SMOKE),
        Form.FEUERFUERST to FormLook(pal(0xFFFF4D3A, 0xFFFFD24D, 0xFFFF8A3D, 0xFF7A0E0E), 0.92f, 1.04f, wings = Wings.DRAGON, horns = true, crown = true, tail = Tail.FLAME, aura = Aura.FLAMES, back = Back.CAPE),
        Form.SONNENOFEN to FormLook(pal(0xFFFFC23D, 0xFFFF7A2E, 0xFFFFF4A8, 0xFFB84A00), 1.16f, 0.9f, antenna = Antenna.RAYS, bigBelly = true, aura = Aura.FLAMES, ears = Ears.ROUND),
        Form.ASCHEDRACHE to FormLook(pal(0xFF7A747E, 0xFFFF6A3D, 0xFF5E5068, 0xFFFF8A3D, eye = 0xFFFF8A3D), 0.94f, 1.02f, wings = Wings.DRAGON, horns = true, tail = Tail.DRAGON, pattern = Pattern.CRACKS, eyes = EyeStyle.GLOW, aura = Aura.SMOKE, muzzle = Muzzle.WHISKERS),
        Form.GLIMMGREIS to FormLook(pal(0xFFA8908A, 0xFFFF9A5C, 0xFFE8D8D0, 0xFF5A2E1E), 1.08f, 0.9f, ears = Ears.ROUND, muzzle = Muzzle.WHISKERS, antenna = Antenna.FLAME, back = Back.MANE, aura = Aura.SMOKE),
        Form.SONNENDRACHE to FormLook(pal(0xFFFFD86B, 0xFFFFFFFF, 0xFFFF9A2E, 0xFFB86B00), 0.94f, 1.04f, wings = Wings.FEATHER, horns = true, tail = Tail.FLAME, antenna = Antenna.RAYS, aura = Aura.FLAMES, sparkles = true),
        Form.EWIGEFLAMME to FormLook(pal(0xFFFF8A3D, 0xFFFFF4A8, 0xFFFFD24D, 0xFF8A2E00), 0.9f, 1.02f, shape = Shape.DROP, antenna = Antenna.FLAME, jelly = true, aura = Aura.FLAMES, eyes = EyeStyle.GLOW, sparkles = true),
        // Urzeit
        Form.SPRINTRAPTOR to FormLook(pal(0xFF7FD3B0, 0xFFFF9A5C, 0xFF4FAE8C, 0xFF1F5E48), 0.84f, 1.02f, antenna = Antenna.CREST, tail = Tail.DINO, muzzle = Muzzle.SNOUT, pattern = Pattern.STRIPES, headband = true),
        Form.KUSCHELSAURUS to FormLook(pal(0xFFB8E0A0, 0xFFFFC8D8, 0xFF8AC07A, 0xFF2E6B2A), 1.12f, 0.88f, tail = Tail.DINO, bigBelly = true, pattern = Pattern.SPOTS, ears = Ears.ROUND),
        Form.HORNSAURUS to FormLook(pal(0xFFC7A77A, 0xFFFFE8B8, 0xFFE58B5A, 0xFF5A4A1E), 1.04f, 0.92f, back = Back.FRILL, horns = true, muzzle = Muzzle.SNOUT, tail = Tail.DINO, spikes = true),
        Form.BLATTFRESSER to FormLook(pal(0xFF9AB6A0, 0xFF7FC46A, 0xFF6F8F80, 0xFF28403A), 1.14f, 0.9f, tail = Tail.DINO, bigBelly = true, antenna = Antenna.SPROUT, pattern = Pattern.SPOTS),
        Form.FLUGSAURIER to FormLook(pal(0xFFE0A07A, 0xFFFF8A5C, 0xFFC77A55, 0xFF5A2E14), 0.86f, 1f, wings = Wings.BAT, antenna = Antenna.CREST, muzzle = Muzzle.BEAK, aura = Aura.SMOKE),
        Form.PANZERSAURUS to FormLook(pal(0xFF8FA090, 0xFFE8A25A, 0xFF6E8070, 0xFF2E3A30), 1.12f, 0.88f, back = Back.SHELL, spikes = true, tail = Tail.DINO, pattern = Pattern.PANELS),
        Form.DONNERECHSE to FormLook(pal(0xFF5E9E5A, 0xFFFFE08A, 0xFF3E7A3A, 0xFF1F3A12), 1.02f, 1.04f, tail = Tail.DINO, muzzle = Muzzle.FANGS, back = Back.PLATES, aura = Aura.BOLTS, spikes = true),
        Form.RIESENSAURUS to FormLook(pal(0xFF8AA8D8, 0xFFFFD6A8, 0xFF6F8FC4, 0xFF28406E), 1.2f, 0.94f, tail = Tail.DINO, bigBelly = true, pattern = Pattern.SPOTS, back = Back.PLATES),
        Form.FOSSILKOENIG to FormLook(pal(0xFFD8CCB8, 0xFF8A7A6A, 0xFFF2E8D8, 0xFF4A3A2A), 1f, 1.02f, tail = Tail.DINO, crown = true, pattern = Pattern.CRACKS, back = Back.PLATES, muzzle = Muzzle.WHISKERS),
        Form.STEINSAURUS to FormLook(pal(0xFF9A98A0, 0xFF7AC07A, 0xFFC8C4CC, 0xFF3A3840), 1.12f, 0.9f, tail = Tail.DINO, pattern = Pattern.CRACKS, back = Back.SHELL, antenna = Antenna.SPROUT, muzzle = Muzzle.WHISKERS),
        Form.METEORSAURUS to FormLook(pal(0xFF3E3A6A, 0xFFFF8A3D, 0xFF8A7AE8, 0xFFFFE066, eye = 0xFFFFE066), 1f, 1.04f, tail = Tail.COMET, back = Back.PLATES, pattern = Pattern.GALAXY, aura = Aura.STARS, eyes = EyeStyle.GLOW, sparkles = true),
        Form.BERNSTEINSAURUS to FormLook(pal(0xFFF2A84A, 0xFFFFE0A0, 0xFFC87A2A, 0xFF6A3A0A), 1.1f, 0.96f, tail = Tail.DINO, jelly = true, back = Back.PLATES, aura = Aura.STARS, sparkles = true),
        // Techno
        Form.TURBOBOT to FormLook(pal(0xFF6FC8FF, 0xFFFF5A5A, 0xFF3A8AD6, 0xFF6FE8FF, eye = 0xFF6FE8FF), 0.9f, 0.98f, shape = Shape.SQUARE, antenna = Antenna.BOLT, tail = Tail.BOLT, faceplate = true, eyes = EyeStyle.GLOW, pattern = Pattern.ZIGZAG),
        Form.KUSCHELBOT to FormLook(pal(0xFFFFD8E8, 0xFF8AD8FF, 0xFFFFFFFF, 0xFF6FE8FF, eye = 0xFF6FE8FF), 1.1f, 0.88f, antenna = Antenna.BOLT, ears = Ears.ROUND, faceplate = true, eyes = EyeStyle.GLOW, bigBelly = true),
        Form.DUESENBOT to FormLook(pal(0xFFE8ECF6, 0xFF5AC8FF, 0xFFB0B8C8, 0xFF6FE8FF, eye = 0xFF6FE8FF), 0.9f, 1.02f, shape = Shape.SQUARE, wings = Wings.JET, antenna = Antenna.BOLT, faceplate = true, eyes = EyeStyle.GLOW, pattern = Pattern.PANELS),
        Form.TEEBOT to FormLook(pal(0xFFC8E8D8, 0xFFB86A4A, 0xFFFFFFFF, 0xFF6FE8FF, eye = 0xFF6FE8FF), 1.08f, 0.9f, shape = Shape.SQUARE, antenna = Antenna.SWIRL, faceplate = true, eyes = EyeStyle.GLOW, bigBelly = true, pattern = Pattern.BANDS),
        Form.MECHADRACHE to FormLook(pal(0xFF8A9AB8, 0xFFFFD24D, 0xFF6A7A98, 0xFFFF6A3D, eye = 0xFFFF6A3D), 0.94f, 1.02f, wings = Wings.DRAGON, horns = true, tail = Tail.DRAGON, faceplate = true, eyes = EyeStyle.GLOW, pattern = Pattern.PANELS),
        Form.GARTENBOT to FormLook(pal(0xFFA8D88A, 0xFFFF9EC2, 0xFFE8F4E0, 0xFF6FE8FF, eye = 0xFF6FE8FF), 1.04f, 0.94f, shape = Shape.SQUARE, antenna = Antenna.FLOWER, faceplate = true, eyes = EyeStyle.GLOW, pattern = Pattern.PANELS),
        Form.TITANBOT to FormLook(pal(0xFF5A6A8A, 0xFFFFC23D, 0xFF8A9AB8, 0xFFFFC23D, eye = 0xFFFFC23D), 1.06f, 1.04f, shape = Shape.SQUARE, antenna = Antenna.CREST, faceplate = true, eyes = EyeStyle.GLOW, pattern = Pattern.PANELS, back = Back.PLATES, aura = Aura.BOLTS),
        Form.HAUSMEISTERBOT to FormLook(pal(0xFFFFC96A, 0xFF3A6AD8, 0xFF8A94A8, 0xFF6FE8FF, eye = 0xFF6FE8FF), 1.1f, 0.94f, shape = Shape.SQUARE, headband = true, ears = Ears.ROUND, faceplate = true, eyes = EyeStyle.GLOW, pattern = Pattern.PANELS, bigBelly = true),
        Form.RETROBOT to FormLook(pal(0xFFC8B89A, 0xFFFF8A3D, 0xFF8A7A6A, 0xFFFFB85A, eye = 0xFFFFB85A), 0.96f, 1f, shape = Shape.SQUARE, antenna = Antenna.ANTENNAE, faceplate = true, eyes = EyeStyle.GLOW, pattern = Pattern.BUTTONS),
        Form.ROSTGREIS to FormLook(pal(0xFFB8805E, 0xFF8A5A3A, 0xFF7A8A9A, 0xFFFFB85A, eye = 0xFFFFB85A), 1.04f, 0.92f, shape = Shape.SQUARE, antenna = Antenna.BOLT, faceplate = true, eyes = EyeStyle.GLOW, pattern = Pattern.SPOTS, muzzle = Muzzle.WHISKERS),
        Form.QUANTENBOT to FormLook(pal(0xFF3A2E6A, 0xFF6FE8FF, 0xFFB98AFF, 0xFF6FE8FF, eye = 0xFF6FE8FF), 0.94f, 1.04f, shape = Shape.SQUARE, antenna = Antenna.CRYSTAL, faceplate = true, eyes = EyeStyle.GLOW, pattern = Pattern.GALAXY, aura = Aura.BOLTS, jelly = true, sparkles = true),
        Form.STERNENCOMPUTER to FormLook(pal(0xFF2E3A6A, 0xFFFFE680, 0xFF6A78C8, 0xFFFFE680, eye = 0xFFFFE680), 1.08f, 1f, shape = Shape.SQUARE, antenna = Antenna.STAR, ring = true, faceplate = true, eyes = EyeStyle.GLOW, pattern = Pattern.STARS, aura = Aura.STARS, sparkles = true),
        // Einhorn
        Form.GALOPPHORN to FormLook(pal(0xFFFFFFFF, 0xFF8FD8FF, 0xFFFF8FB8, 0xFF3A6FD6), 0.88f, 1f, ears = Ears.HORSE, antenna = Antenna.UNICORN, back = Back.MANE, tail = Tail.RAINBOW, headband = true),
        Form.WATTEHORN to FormLook(pal(0xFFFFE0F0, 0xFFB9A4FF, 0xFFFFFFFF, 0xFFC23A7A), 1.08f, 0.9f, ears = Ears.HORSE, antenna = Antenna.UNICORN, back = Back.MANE, tail = Tail.FLUFFY, bigBelly = true),
        Form.WOLKENRENNER to FormLook(pal(0xFFE8F0FF, 0xFF8FD8FF, 0xFFFFFFFF, 0xFF3A5AA8), 0.88f, 1.02f, ears = Ears.HORSE, antenna = Antenna.UNICORN, back = Back.MANE, tail = Tail.FLUFFY, aura = Aura.SMOKE),
        Form.BLUETENHORN to FormLook(pal(0xFFFFF0F6, 0xFFFF7FAF, 0xFFB8F0C8, 0xFF8A5AD6), 1f, 0.94f, ears = Ears.HORSE, antenna = Antenna.UNICORN, back = Back.MANE, tail = Tail.RAINBOW, aura = Aura.LEAVES),
        Form.HIMMELSROSS to FormLook(pal(0xFFF8FAFF, 0xFFFFE08A, 0xFF8FD8FF, 0xFF3A6FD6), 0.9f, 1.02f, ears = Ears.HORSE, back = Back.MANE, wings = Wings.FEATHER, tail = Tail.FLUFFY, antenna = Antenna.UNICORN),
        Form.TRAUMHORN to FormLook(pal(0xFFC8B8FF, 0xFFFFF0B0, 0xFFFFC2E0, 0xFF5A3DC8), 1.02f, 0.96f, ears = Ears.HORSE, antenna = Antenna.UNICORN, back = Back.MANE, tail = Tail.RAINBOW, pattern = Pattern.STARS),
        Form.REGENBOGENRITTER to FormLook(pal(0xFFFFFFFF, 0xFFFF5A8A, 0xFFFFD24D, 0xFF3A3DC8), 0.92f, 1.04f, ears = Ears.HORSE, antenna = Antenna.UNICORN, back = Back.CAPE, tail = Tail.RAINBOW, headband = true, aura = Aura.STARS),
        Form.HERZHORN to FormLook(pal(0xFFFFD1E8, 0xFFFF5C9A, 0xFFFFFFFF, 0xFFC21F5A), 1.04f, 0.98f, ears = Ears.HORSE, antenna = Antenna.UNICORN, back = Back.MANE, pattern = Pattern.HEARTS, aura = Aura.HEARTS, tail = Tail.FLUFFY),
        Form.SILBERHORN to FormLook(pal(0xFFE0E4EC, 0xFFC8D0E0, 0xFFFFFFFF, 0xFF4A5A7A), 0.92f, 1.02f, ears = Ears.HORSE, antenna = Antenna.UNICORN, back = Back.MANE, tail = Tail.FLUFFY, wings = Wings.FEATHER),
        Form.MONDMAEHNE to FormLook(pal(0xFF9C9CC8, 0xFFFFF0B0, 0xFFE8E8FF, 0xFF2E2E6A), 1.02f, 0.98f, ears = Ears.HORSE, antenna = Antenna.MOON, back = Back.MANE, tail = Tail.FLUFFY, pattern = Pattern.STARS),
        Form.STERNENROSS to FormLook(pal(0xFF3E3A8A, 0xFFFFE680, 0xFF9AD8FF, 0xFFFFE680, eye = 0xFF1A1F3D), 0.94f, 1.04f, ears = Ears.HORSE, antenna = Antenna.UNICORN, back = Back.MANE, wings = Wings.ANGEL, tail = Tail.COMET, pattern = Pattern.GALAXY, aura = Aura.STARS, sparkles = true),
        Form.LICHTHORN to FormLook(pal(0xFFFFFCF0, 0xFFFFE680, 0xFFB8F0FF, 0xFF8A6AD6), 0.98f, 1.02f, ears = Ears.HORSE, antenna = Antenna.UNICORN, back = Back.MANE, tail = Tail.RAINBOW, aura = Aura.STARS, jelly = true, eyes = EyeStyle.GLOW, sparkles = true),
        // Bluete
        Form.BLUETENFLITZ to FormLook(pal(0xFFB8F0C8, 0xFFFF7FAF, 0xFFFFE066, 0xFF2E6B3A), 0.86f, 0.98f, wings = Wings.FAIRY, antenna = Antenna.BUD, pattern = Pattern.STRIPES),
        Form.POLLENPUSCHEL to FormLook(pal(0xFFFFE680, 0xFFFFB84D, 0xFFFFFFFF, 0xFF8A5A00), 1.1f, 0.88f, antenna = Antenna.ANTENNAE, pattern = Pattern.SPOTS, bigBelly = true, back = Back.MANE),
        Form.WINDBLUETE to FormLook(pal(0xFFE0F0FF, 0xFFFF9EDC, 0xFFB8F0C8, 0xFF3A6AB8), 0.86f, 1.02f, wings = Wings.FAIRY, antenna = Antenna.FLOWER, aura = Aura.LEAVES, tail = Tail.LEAF),
        Form.HONIGBLUETE to FormLook(pal(0xFFFFE0A0, 0xFFFF9EC2, 0xFFFFFFFF, 0xFF8A5A1E), 1.06f, 0.92f, wings = Wings.BEE, antenna = Antenna.FLOWER, pattern = Pattern.SPOTS, bigBelly = true),
        Form.SCHMETTERLINGSFEE to FormLook(pal(0xFFF0E0FF, 0xFFFF9A2E, 0xFF7AD8FF, 0xFF6A4FC8), 0.86f, 1.02f, wings = Wings.FAIRY, antenna = Antenna.ANTENNAE, pattern = Pattern.SWIRL, sparkles = true),
        Form.ROSENHERZ to FormLook(pal(0xFFFFD6E0, 0xFFE84A6A, 0xFFB8F0C8, 0xFFB83A5A), 1.02f, 0.96f, antenna = Antenna.FLOWER, pattern = Pattern.HEARTS, aura = Aura.HEARTS, ears = Ears.LEAF),
        Form.LILIENFEE to FormLook(pal(0xFFFFFFFF, 0xFFFFE066, 0xFFB8F0C8, 0xFF4A7A3A), 0.88f, 1.04f, wings = Wings.FAIRY, antenna = Antenna.FLOWER, crown = true, aura = Aura.LEAVES, sparkles = true),
        Form.GARTENHUETERIN to FormLook(pal(0xFFC8E8B0, 0xFFFF7FAF, 0xFFFFE066, 0xFF2E5A2E), 1.06f, 0.98f, wings = Wings.FAIRY, antenna = Antenna.FLOWER, back = Back.MANE, aura = Aura.LEAVES, ears = Ears.LEAF),
        Form.HERBSTFEE to FormLook(pal(0xFFE8A86A, 0xFFC84A2E, 0xFFFFE066, 0xFF6A2E12), 0.88f, 1.02f, wings = Wings.FAIRY, ears = Ears.LEAF, aura = Aura.LEAVES, pattern = Pattern.SPOTS),
        Form.LAVENDELHAUBE to FormLook(pal(0xFFC8B8E8, 0xFF8A6AC8, 0xFFE8E0F0, 0xFF4A3A7A), 1.04f, 0.94f, antenna = Antenna.FLOWER, back = Back.MANE, muzzle = Muzzle.WHISKERS, ears = Ears.LEAF),
        Form.BLUETENGEIST to FormLook(pal(0xFFE8FFE0, 0xFFFFF4A8, 0xFFB8F0C8, 0xFF2E8A5A), 0.88f, 1.02f, ghost = true, wings = Wings.FAIRY, antenna = Antenna.GLOW, aura = Aura.LEAVES, eyes = EyeStyle.GLOW, sparkles = true),
        Form.EWIGEROSE to FormLook(pal(0xFFFF6A8A, 0xFFFFE680, 0xFFFFD0DC, 0xFF8A1E3A), 0.98f, 1.02f, antenna = Antenna.FLOWER, crown = true, wings = Wings.FAIRY, aura = Aura.HEARTS, jelly = true, sparkles = true),
        // Stern
        Form.STERNSCHNUPPE to FormLook(pal(0xFFFFE680, 0xFF9AB8FF, 0xFFFFF7C8, 0xFFB86B00), 0.88f, 0.98f, antenna = Antenna.STAR, tail = Tail.COMET, headband = true),
        Form.KUSCHELSTERN to FormLook(pal(0xFFFFF0B0, 0xFFFFB84D, 0xFFFFFFFF, 0xFFB86B00), 1.08f, 0.88f, antenna = Antenna.STAR, bigBelly = true, ears = Ears.ROUND, sparkles = true),
        Form.KOMETENREITER to FormLook(pal(0xFF7A98FF, 0xFFFFE680, 0xFFE0E8FF, 0xFF2E3A9A), 0.88f, 1f, tail = Tail.COMET, antenna = Antenna.STAR, back = Back.CAPE, pattern = Pattern.STARS),
        Form.MONDKISSEN to FormLook(pal(0xFF5A6AB8, 0xFFFFF0B0, 0xFF8A98E0, 0xFFFFF0B0, eye = 0xFF1A1F3D), 1.08f, 0.88f, antenna = Antenna.MOON, bigBelly = true, pattern = Pattern.STARS, ears = Ears.ROUND),
        Form.STERNENPILOT to FormLook(pal(0xFFFFFFFF, 0xFFFF5A5A, 0xFF8FD8FF, 0xFF2E4A8A), 0.9f, 1.02f, antenna = Antenna.HELMET, wings = Wings.JET, headband = true),
        Form.PLANETENHUETER to FormLook(pal(0xFFFFB88A, 0xFF8AD8FF, 0xFFFFE8C8, 0xFF8A4A1E), 1.12f, 0.9f, ring = true, pattern = Pattern.BANDS, antenna = Antenna.STAR, bigBelly = true),
        Form.POLARSTERN to FormLook(pal(0xFFE8F0FF, 0xFFFFE680, 0xFF9AB8FF, 0xFF2E4AB8), 0.92f, 1.04f, antenna = Antenna.RAYS, wings = Wings.ANGEL, aura = Aura.STARS, sparkles = true),
        Form.NEBELSTERN to FormLook(pal(0xFFC8A0F0, 0xFFFF9AD8, 0xFF7AD8FF, 0xFF4A2E8A), 1.06f, 0.96f, pattern = Pattern.GALAXY, aura = Aura.SMOKE, antenna = Antenna.STAR, jelly = true),
        Form.ALTERKOMET to FormLook(pal(0xFF9AA0C0, 0xFFFFF0B0, 0xFFD0D8F0, 0xFF3A4070), 0.92f, 1f, tail = Tail.COMET, antenna = Antenna.STAR, back = Back.MANE, muzzle = Muzzle.WHISKERS),
        Form.MONDGREIS to FormLook(pal(0xFF6A6A9A, 0xFFFFF0B0, 0xFFC8C8E8, 0xFFFFF0B0, eye = 0xFF1A1F3D), 1.04f, 0.94f, antenna = Antenna.MOON, back = Back.MANE, muzzle = Muzzle.WHISKERS, pattern = Pattern.STARS),
        Form.SUPERNOVA to FormLook(pal(0xFFFFF4E0, 0xFFFF9A2E, 0xFFFFE680, 0xFFFF7A2E, eye = 0xFF8A2E00), 0.96f, 1.04f, antenna = Antenna.RAYS, wings = Wings.FEATHER, aura = Aura.FLAMES, eyes = EyeStyle.GLOW, sparkles = true),
        Form.GALAXIENHERZ to FormLook(pal(0xFF3A2E8A, 0xFFFF9AD8, 0xFF7AD8FF, 0xFFFFE680, eye = 0xFF1A1F3D), 1.06f, 1f, pattern = Pattern.GALAXY, ring = true, aura = Aura.STARS, antenna = Antenna.STAR, sparkles = true),
        // Zucker
        Form.BRAUSEBONBON to FormLook(pal(0xFF8AE8D8, 0xFFFF5A9A, 0xFFFFFFFF, 0xFF1F7A6A), 0.9f, 0.96f, ears = Ears.WRAPPER, pattern = Pattern.SPRINKLES, aura = Aura.BUBBLES),
        Form.MARSHMALLOW to FormLook(pal(0xFFFFF4F8, 0xFFFFC2DC, 0xFFFFFFFF, 0xFFC23A7A), 1.14f, 0.86f, shape = Shape.SQUARE, bigBelly = true),
        Form.ZUCKERSTANGE to FormLook(pal(0xFFFFFFFF, 0xFFFF3A5A, 0xFF8AE8A0, 0xFF8A1E2E), 0.84f, 1.04f, pattern = Pattern.STRIPES, antenna = Antenna.SWIRL, headband = true),
        Form.PUDDING to FormLook(pal(0xFFFFE08A, 0xFF8A4A1E, 0xFFFFFFFF, 0xFF6A3A12), 1.12f, 0.9f, jelly = true, antenna = Antenna.CHERRY, bigBelly = true),
        Form.RAKETENEIS to FormLook(pal(0xFFFF8AC8, 0xFFFFE066, 0xFF8AD8FF, 0xFFC23A7A), 0.88f, 1.02f, wings = Wings.JET, pattern = Pattern.STRIPES, antenna = Antenna.CHERRY),
        Form.SAHNETORTE to FormLook(pal(0xFFFFF0F4, 0xFFFF6A8A, 0xFFFFE0C8, 0xFF8A3A1E), 1.14f, 0.94f, shape = Shape.SQUARE, pattern = Pattern.BANDS, antenna = Antenna.CHERRY, aura = Aura.HEARTS),
        Form.BONBONRITTER to FormLook(pal(0xFFE8A85A, 0xFFFF5A9A, 0xFFFFE8B8, 0xFF6A3A12), 0.92f, 1.04f, back = Back.CAPE, headband = true, pattern = Pattern.SWIRL, ears = Ears.WRAPPER, sparkles = true),
        Form.PRALINENKOENIG to FormLook(pal(0xFF8A5A3A, 0xFFFFD24D, 0xFFFFB3D1, 0xFF3A1E0A), 1.14f, 0.9f, crown = true, bigBelly = true, pattern = Pattern.SPRINKLES, back = Back.CAPE),
        Form.ALTERLEBKUCHEN to FormLook(pal(0xFFC0804A, 0xFFFFFFFF, 0xFFFF6A7A, 0xFF4A2410), 0.94f, 1f, pattern = Pattern.ICING, muzzle = Muzzle.WHISKERS, ears = Ears.ROUND, back = Back.MANE),
        Form.HONIGKUCHEN to FormLook(pal(0xFFD8A060, 0xFFFFE0A0, 0xFFFFFFFF, 0xFF5A3410), 1.08f, 0.92f, pattern = Pattern.ICING, jelly = true, muzzle = Muzzle.WHISKERS, ears = Ears.ROUND),
        Form.ZUCKERSTERN to FormLook(pal(0xFFFFF4FA, 0xFF8AD8FF, 0xFFFFE680, 0xFFB86BC8), 0.94f, 1.02f, antenna = Antenna.STAR, pattern = Pattern.SPRINKLES, aura = Aura.STARS, jelly = true, wings = Wings.FAIRY, sparkles = true),
        Form.ZAUBERKONFEKT to FormLook(pal(0xFFFFC2E0, 0xFFFFE680, 0xFF8AE8FF, 0xFFC23A7A), 1.06f, 1f, crown = true, pattern = Pattern.SWIRL, aura = Aura.HEARTS, jelly = true, ears = Ears.WRAPPER, sparkles = true),
        // Dschungel
        Form.LIANENSCHWINGER to FormLook(pal(0xFF8A6A5A, 0xFF4FA84A, 0xFFE8D8C8, 0xFF3A2A1A), 0.86f, 0.98f, ears = Ears.ROUND, tail = Tail.LEAF, muzzle = Muzzle.SNOUT, antenna = Antenna.SPROUT),
        Form.MANGOMAMPF to FormLook(pal(0xFFFFB84D, 0xFF5FB35A, 0xFFFF8A3D, 0xFF8A4A00), 1.12f, 0.88f, bigBelly = true, antenna = Antenna.SPROUT, ears = Ears.ROUND),
        Form.PANTHERPFOTE to FormLook(pal(0xFF2E2A3A, 0xFF7AE8A0, 0xFF4A4458, 0xFF7AE8A0, eye = 0xFF120C12), 0.86f, 1.02f, ears = Ears.CAT, tail = Tail.CAT, muzzle = Muzzle.WHISKERS, eyes = EyeStyle.GLOW),
        Form.FAULTI to FormLook(pal(0xFFB8A090, 0xFF7A6A5A, 0xFFE8DCD0, 0xFF3A2A1A), 1.08f, 0.9f, ears = Ears.ROUND, muzzle = Muzzle.SNOUT, bigBelly = true, pattern = Pattern.BANDS, antenna = Antenna.SPROUT),
        Form.PARADIESVOGEL to FormLook(pal(0xFFFF6A8A, 0xFF3AC8E8, 0xFFFFE066, 0xFF8A1E3A), 0.88f, 1.02f, wings = Wings.FEATHER, muzzle = Muzzle.BEAK, antenna = Antenna.CREST, tail = Tail.RAINBOW),
        Form.ORCHIDEENAFFE to FormLook(pal(0xFF9A7A8A, 0xFFFF8AC8, 0xFFE8D8E0, 0xFF3A2A3A), 1.04f, 0.94f, ears = Ears.ROUND, antenna = Antenna.FLOWER, muzzle = Muzzle.SNOUT, aura = Aura.LEAVES),
        Form.REGENWALDWAECHTER to FormLook(pal(0xFF3A8A5A, 0xFFFFD24D, 0xFF7AE8A0, 0xFF1F3A1E), 0.94f, 1.04f, ears = Ears.CAT, tail = Tail.CAT, back = Back.MANE, pattern = Pattern.SPOTS, aura = Aura.LEAVES, eyes = EyeStyle.GLOW),
        Form.BANANENKOENIG to FormLook(pal(0xFF5A5060, 0xFFFFE04D, 0xFFB8A8B8, 0xFF3A2A1A, belly = 0xFFD8C8D0), 1.16f, 0.92f, ears = Ears.ROUND, crown = true, bigBelly = true, muzzle = Muzzle.SNOUT, back = Back.CAPE),
        Form.ALTERJAGUAR to FormLook(pal(0xFFC8B090, 0xFF8A8A8A, 0xFF5A4A3A, 0xFF6A8A2A, belly = 0xFFF0E8E0), 0.9f, 1f, ears = Ears.CAT, tail = Tail.CAT, pattern = Pattern.SPOTS, muzzle = Muzzle.WHISKERS, back = Back.MANE),
        Form.SILBERRUECKEN to FormLook(pal(0xFF4A4650, 0xFFC8C8D0, 0xFF8A8890, 0xFF2A2420, belly = 0xFFB8B4BC), 1.16f, 0.94f, ears = Ears.ROUND, bigBelly = true, muzzle = Muzzle.SNOUT, back = Back.MANE),
        Form.DSCHUNGELGEIST to FormLook(pal(0xFFB8F0D0, 0xFFFFE680, 0xFF7AE8A0, 0xFF1F6B4A), 0.9f, 1.02f, ghost = true, ears = Ears.CAT, aura = Aura.LEAVES, eyes = EyeStyle.GLOW, antenna = Antenna.GLOW, sparkles = true),
        Form.SMARAGDHERZ to FormLook(pal(0xFF3AC88A, 0xFFFFE680, 0xFFB8F0D0, 0xFF1F5E3E), 1.04f, 1f, ears = Ears.ROUND, back = Back.MANE, crown = true, jelly = true, aura = Aura.LEAVES, pattern = Pattern.HEARTS, sparkles = true),
        // Wueste
        Form.DUENENFLITZER to FormLook(pal(0xFFF2C890, 0xFFFFB3A0, 0xFFFFF4E4, 0xFF6A3A1A), 0.86f, 0.98f, ears = Ears.FOX, tail = Tail.FLUFFY, muzzle = Muzzle.NOSE, headband = true),
        Form.DATTELCHEN to FormLook(pal(0xFFC88A5A, 0xFFFFE0B0, 0xFF8A5A3A, 0xFF4A2A10), 1.1f, 0.88f, ears = Ears.ROUND, bigBelly = true, antenna = Antenna.STEM),
        Form.WUESTENWIND to FormLook(pal(0xFFE8D0A0, 0xFFFFE8B8, 0xFFC89A5A, 0xFF8A5A2A), 0.86f, 1.02f, antenna = Antenna.SWIRL, tail = Tail.COMET, aura = Aura.SMOKE, ears = Ears.FOX),
        Form.OASENWAECHTER to FormLook(pal(0xFF7AC0B0, 0xFFFFD86A, 0xFFE8F4E0, 0xFF2E6A5A), 1.06f, 0.94f, antenna = Antenna.DROPLET, ears = Ears.ROUND, back = Back.MANE, pattern = Pattern.SPOTS),
        Form.SANDLAEUFER to FormLook(pal(0xFFE8B880, 0xFF3A6AD8, 0xFFFFF0D8, 0xFF5A3A1A), 0.86f, 1.02f, ears = Ears.CAT, tail = Tail.CAT, headband = true, muzzle = Muzzle.NOSE, back = Back.CAPE),
        Form.KARAWANENFREUND to FormLook(pal(0xFFD8A870, 0xFFE84A6A, 0xFFFFE0B0, 0xFF5A3A1A), 1.12f, 0.94f, ears = Ears.HORSE, bigBelly = true, muzzle = Muzzle.SNOUT, back = Back.CAPE, pattern = Pattern.ZIGZAG),
        Form.SONNENSKARABAEUS to FormLook(pal(0xFF2E8AA8, 0xFFFFD24D, 0xFF1F5A7A, 0xFFFFE680), 1f, 0.94f, back = Back.SHELL, antenna = Antenna.RAYS, sparkles = true),
        Form.RAETSELSPHINX to FormLook(pal(0xFFE8C080, 0xFF3A6AD8, 0xFFFFD24D, 0xFF3A6AD8), 1.02f, 1f, ears = Ears.CAT, tail = Tail.CAT, headband = true, muzzle = Muzzle.NOSE, crown = true, back = Back.MANE),
        Form.ALTERWUESTENFUCHS to FormLook(pal(0xFFE0D0B8, 0xFFFFB3A0, 0xFFFFFFFF, 0xFF6A4A2A), 0.88f, 0.98f, ears = Ears.FOX, tail = Tail.FLUFFY, muzzle = Muzzle.WHISKERS, back = Back.MANE),
        Form.SANDGREIS to FormLook(pal(0xFFD8C8A8, 0xFF3A6AD8, 0xFFF2E8D8, 0xFF5A4A2A), 1.04f, 0.94f, pattern = Pattern.BANDAGE, muzzle = Muzzle.WHISKERS, headband = true),
        Form.GOLDENEDUENE to FormLook(pal(0xFFFFD86A, 0xFFFFFFFF, 0xFFFFB02E, 0xFF8A5A00), 0.92f, 1.04f, ears = Ears.FOX, tail = Tail.COMET, antenna = Antenna.SWIRL, aura = Aura.STARS, wings = Wings.FEATHER, sparkles = true),
        Form.STERNENSPHINX to FormLook(pal(0xFF3A3A7A, 0xFFFFD24D, 0xFF6A78C8, 0xFFFFD24D, eye = 0xFF1A1F3D), 1.02f, 1.02f, ears = Ears.CAT, tail = Tail.CAT, crown = true, wings = Wings.ANGEL, pattern = Pattern.STARS, aura = Aura.STARS, sparkles = true),
        // Edelstein
        Form.TOPASFLITZ to FormLook(pal(0xFFFFD86A, 0xFFFFF4C2, 0xFFE8A02E, 0xFF8A5A00), 0.86f, 0.98f, antenna = Antenna.CRYSTAL, jelly = true, tail = Tail.COMET, headband = true),
        Form.ROSENQUARZ to FormLook(pal(0xFFF2C4D6, 0xFFFFE0EC, 0xFFD98AB0, 0xFF9A3A6A), 1.08f, 0.88f, antenna = Antenna.CRYSTAL, jelly = true, ears = Ears.ROUND, bigBelly = true),
        Form.SMARAGDLAEUFER to FormLook(pal(0xFF3AC88A, 0xFFB8F0D0, 0xFF1F8A5A, 0xFF0E4A2E), 0.86f, 1.02f, ears = Ears.FIN, jelly = true, tail = Tail.CAT, pattern = Pattern.STARS),
        Form.ACHATBAER to FormLook(pal(0xFFC88A6A, 0xFFF2E0D0, 0xFF8A5A4A, 0xFF4A2A1A), 1.12f, 0.9f, ears = Ears.ROUND, bigBelly = true, pattern = Pattern.BANDS, jelly = true, muzzle = Muzzle.SNOUT),
        Form.SAPHIRDRACHE to FormLook(pal(0xFF3A6AE8, 0xFFB8D8FF, 0xFF2E4AB8, 0xFF0E2A8A), 0.92f, 1.02f, wings = Wings.DRAGON, horns = true, tail = Tail.DRAGON, jelly = true, sparkles = true),
        Form.PERLMUTTHERZ to FormLook(pal(0xFFF0ECF8, 0xFFB8F0E8, 0xFFFFD8E8, 0xFF6A5AA8), 1.04f, 0.94f, antenna = Antenna.PEARL, pattern = Pattern.HEARTS, jelly = true, back = Back.SHELL),
        Form.KRISTALLRITTER to FormLook(pal(0xFFE0F4FF, 0xFF8AE0FF, 0xFFB8D0F0, 0xFF2E7AB8), 0.94f, 1.04f, antenna = Antenna.CRYSTAL, back = Back.CAPE, headband = true, spikes = true, jelly = true, aura = Aura.STARS),
        Form.KRONJUWEL to FormLook(pal(0xFFC21F4A, 0xFFFFD24D, 0xFFFF8AA8, 0xFF5A0E1E), 1.08f, 0.96f, crown = true, jelly = true, sparkles = true, ears = Ears.ROUND, pattern = Pattern.STARS),
        Form.ALTEROBSIDIAN to FormLook(pal(0xFF2E2A36, 0xFF9A98A8, 0xFF4A4658, 0xFFB98AFF, eye = 0xFFB98AFF), 0.94f, 1f, spikes = true, pattern = Pattern.CRACKS, eyes = EyeStyle.GLOW, muzzle = Muzzle.WHISKERS, jelly = true),
        Form.MONDSTEIN to FormLook(pal(0xFFDCE4F4, 0xFFB8C8E8, 0xFFFFFFFF, 0xFF4A5A8A), 1.04f, 0.94f, antenna = Antenna.MOON, jelly = true, muzzle = Muzzle.WHISKERS, sparkles = true),
        Form.STERNENDIAMANT to FormLook(pal(0xFFF4FAFF, 0xFFFFE680, 0xFF8AE0FF, 0xFF2E7AB8), 0.94f, 1.04f, antenna = Antenna.STAR, wings = Wings.ANGEL, jelly = true, aura = Aura.STARS, horns = true, sparkles = true),
        Form.REGENBOGENKRISTALL to FormLook(pal(0xFFF8F4FF, 0xFFFF8AC8, 0xFF8AE8FF, 0xFF6A4AC8), 1.04f, 1.02f, antenna = Antenna.CRYSTAL, tail = Tail.RAINBOW, jelly = true, aura = Aura.STARS, pattern = Pattern.STARS, ring = true, sparkles = true),
        // Frost
        Form.SCHNEEFLITZER to FormLook(pal(0xFF3E4A6A, 0xFFFFB84D, 0xFF2A3350, 0xFF1A1F3D, belly = 0xFFFFFFFF), 0.9f, 0.96f, muzzle = Muzzle.BEAK, wings = Wings.SMALL, headband = true),
        Form.SCHNEEBALL to FormLook(pal(0xFFFFFFFF, 0xFF9AD8FF, 0xFFE8F6FF, 0xFF3A7AB8), 1.14f, 0.88f, bigBelly = true, aura = Aura.SNOW, ears = Ears.ROUND),
        Form.EISLAEUFER to FormLook(pal(0xFFB8E4FF, 0xFFFF8AC8, 0xFFE8F8FF, 0xFF1F5E96), 0.86f, 1.02f, ears = Ears.FOX, tail = Tail.FLUFFY, headband = true, aura = Aura.SNOW),
        Form.PUDELMUETZE to FormLook(pal(0xFFF6FAFF, 0xFFFF5A6A, 0xFFFFFFFF, 0xFF2E3A5A), 1.06f, 0.92f, ears = Ears.ROUND, pattern = Pattern.STRIPES, headband = true, muzzle = Muzzle.SNOUT),
        Form.FROSTFALKE to FormLook(pal(0xFFD8ECFF, 0xFF6FC8FF, 0xFFFFFFFF, 0xFF1F5E96), 0.88f, 1.02f, wings = Wings.FEATHER, muzzle = Muzzle.BEAK, antenna = Antenna.CREST, aura = Aura.SNOW),
        Form.IGLUBAER to FormLook(pal(0xFFF6FAFF, 0xFFB8D8F0, 0xFF9AB8D8, 0xFF2E3A5A), 1.12f, 0.94f, ears = Ears.ROUND, muzzle = Muzzle.SNOUT, bigBelly = true, pattern = Pattern.PANELS),
        Form.NORDLICHTWOLF to FormLook(pal(0xFFE8F0FF, 0xFF7AF0D8, 0xFFB98AFF, 0xFF1F5E96), 0.92f, 1.04f, ears = Ears.FOX, tail = Tail.FLUFFY, muzzle = Muzzle.NOSE, back = Back.MANE, aura = Aura.STARS, sparkles = true),
        Form.SCHNEEKOENIG to FormLook(pal(0xFFFFFFFF, 0xFF6FC8FF, 0xFFE8F6FF, 0xFF1F3F7A), 1.14f, 0.94f, crown = true, back = Back.CAPE, bigBelly = true, ears = Ears.ROUND, aura = Aura.SNOW),
        Form.GLETSCHERWOLF to FormLook(pal(0xFFC8D8E8, 0xFF8AB8E0, 0xFFF0F6FF, 0xFF2E4A6A), 0.94f, 1.02f, ears = Ears.FOX, tail = Tail.FLUFFY, muzzle = Muzzle.WHISKERS, back = Back.MANE, spikes = true, aura = Aura.SNOW),
        Form.FROSTBART to FormLook(pal(0xFFE8F2FF, 0xFF9AD8FF, 0xFFFFFFFF, 0xFF3A5A8A), 1.06f, 0.92f, muzzle = Muzzle.WHISKERS, back = Back.MANE, antenna = Antenna.CRYSTAL, ears = Ears.ROUND),
        Form.POLARLICHT to FormLook(pal(0xFFB8F0E8, 0xFFB98AFF, 0xFF6FE8C8, 0xFF2E6B8A), 0.94f, 1.04f, ghost = true, wings = Wings.ANGEL, aura = Aura.STARS, eyes = EyeStyle.GLOW, antlers = true, sparkles = true),
        Form.EWIGESEIS to FormLook(pal(0xFFE0F4FF, 0xFF8AE0FF, 0xFFFFFFFF, 0xFF2E7AB8), 1.02f, 1.02f, antenna = Antenna.CRYSTAL, crown = true, jelly = true, aura = Aura.SNOW, pattern = Pattern.STARS, sparkles = true),
        // Grusel
        Form.SPUKFLITZER to FormLook(pal(0xFFE8E8FF, 0xFF9A6AE8, 0xFFFFFFFF, 0xFF3A3A6A), 0.86f, 1f, ghost = true, aura = Aura.SMOKE),
        Form.KUERBISKUCHEN to FormLook(pal(0xFFE8963D, 0xFFFFF0D8, 0xFFC86A1E, 0xFF5A2E00), 1.14f, 0.84f, pattern = Pattern.RIBS, antenna = Antenna.STEM, bigBelly = true, ears = Ears.ROUND),
        Form.NACHTSCHWAERMER to FormLook(pal(0xFF4A3A6A, 0xFFFFE066, 0xFF2E2448, 0xFFFFE066), 0.88f, 0.98f, ears = Ears.BAT, wings = Wings.BAT, eyes = EyeStyle.GLOW, antenna = Antenna.ANTENNAE),
        Form.HEXENKESSEL to FormLook(pal(0xFF3A3A4A, 0xFF8AE86A, 0xFF5A5A6A, 0xFF8AE86A, eye = 0xFF8AE86A), 1.12f, 0.9f, antenna = Antenna.WITCH, bigBelly = true, aura = Aura.BUBBLES, eyes = EyeStyle.GLOW),
        Form.FLEDERMAUSGRAF to FormLook(pal(0xFF5A4A7A, 0xFFC21F3A, 0xFF2A1F3D, 0xFFFFE066), 0.9f, 1.02f, ears = Ears.BAT, wings = Wings.BAT, back = Back.CAPE, muzzle = Muzzle.FANGS),
        Form.ZAUBERKUERBIS to FormLook(pal(0xFFFF9A3D, 0xFF9A6AE8, 0xFFFFC66A, 0xFF3A1E00, eye = 0xFFFFE066), 1.12f, 0.92f, pattern = Pattern.RIBS, antenna = Antenna.WITCH, eyes = EyeStyle.GLOW, sparkles = true),
        Form.SCHATTENRITTER to FormLook(pal(0xFF3A3450, 0xFF9A6AE8, 0xFF2A2438, 0xFFB98AFF, eye = 0xFFB98AFF), 0.94f, 1.04f, back = Back.CAPE, horns = true, eyes = EyeStyle.GLOW, aura = Aura.SMOKE, headband = true),
        Form.SCHLOSSGESPENST to FormLook(pal(0xFFF6F6FF, 0xFFB8B8E8, 0xFFFFFFFF, 0xFF3A3A6A), 1f, 1.02f, ghost = true, crown = true, aura = Aura.SMOKE),
        Form.URALTERVAMPIR to FormLook(pal(0xFFC8C0D8, 0xFF8A1E3A, 0xFF2A1F3D, 0xFFC21F3A), 0.92f, 1.02f, ears = Ears.CAT, back = Back.CAPE, muzzle = Muzzle.FANGS, wings = Wings.BAT, eyes = EyeStyle.GLOW),
        Form.SPINNWEBGREIS to FormLook(pal(0xFFD8D4E0, 0xFF9A98A8, 0xFFF0EEF4, 0xFF4A4658), 1.04f, 0.94f, pattern = Pattern.BANDAGE, muzzle = Muzzle.WHISKERS, ears = Ears.ROUND, aura = Aura.SMOKE),
        Form.GEISTERLICHT to FormLook(pal(0xFFB8F0FF, 0xFF6FE8FF, 0xFFE8FFFF, 0xFF2E8AB8, eye = 0xFF6FE8FF), 0.86f, 1.02f, ghost = true, eyes = EyeStyle.GLOW, aura = Aura.STARS, antenna = Antenna.GLOW, sparkles = true),
        Form.MONDKUERBIS to FormLook(pal(0xFFFFE6A0, 0xFF9A6AE8, 0xFFFFF8D8, 0xFF8A5A00, eye = 0xFFFFE066), 1.12f, 0.96f, pattern = Pattern.RIBS, antenna = Antenna.MOON, eyes = EyeStyle.GLOW, aura = Aura.STARS, sparkles = true),
        // Katze
        Form.STREIFZUEGLERIN to catLook(0.86f, 1f),
        Form.SOFAKATZE to catLook(1.1f, 0.9f, bigBelly = true),
        Form.KLETTERKATZE to catLook(0.84f, 1.04f),
        Form.SCHNURRMEISTERIN to catLook(1.02f, 0.96f).copy(back = Back.MANE),
        Form.GARTENWAECHTERIN to catLook(0.88f, 1.02f),
        Form.FENSTERBANKKATZE to catLook(1.06f, 0.94f, bigBelly = true),
        Form.ERFAHRENEJAEGERIN to catLook(0.9f, 1.04f).copy(back = Back.MANE),
        Form.HAUSKOENIGIN to catLook(1.12f, 0.96f, bigBelly = true).copy(back = Back.MANE),
        Form.GRAUEPFOTE to catLook(0.9f, 1f),
        Form.SONNENPLATZKATZE to catLook(1.08f, 0.92f, bigBelly = true),
        Form.WEISEKATZE to catLook(0.92f, 1.02f).copy(back = Back.MANE),
        Form.HERZENSKATZE to catLook(1.04f, 0.98f).copy(back = Back.MANE),
        // Hase
        Form.WIESENHASE to bunnyLook(0.86f, 1f),
        Form.MOEHRENFREUND to bunnyLook(1.08f, 0.9f, bigBelly = true),
        Form.HAKENSCHLAEGER to bunnyLook(0.84f, 1.04f),
        Form.HEUHASE to bunnyLook(1.04f, 0.92f, ears = Ears.LOP),
        Form.LANGOHR to bunnyLook(0.88f, 1.08f),
        Form.WIDDERHASE to bunnyLook(1.1f, 0.92f, ears = Ears.LOP, bigBelly = true),
        Form.FELDHUETER to bunnyLook(0.9f, 1.04f),
        Form.SAMTHASE to bunnyLook(1.06f, 0.96f, ears = Ears.LOP).copy(back = Back.MANE),
        Form.GRAUESLANGOHR to bunnyLook(0.9f, 1.02f),
        Form.SONNENHASE to bunnyLook(1.1f, 0.9f, ears = Ears.LOP, bigBelly = true),
        Form.WEISERHASE to bunnyLook(0.92f, 1.04f).copy(back = Back.MANE),
        Form.HERZENSHASE to bunnyLook(1.04f, 0.98f, ears = Ears.LOP).copy(back = Back.MANE),
        // Hamster
        Form.SPRINTHAMSTER to hamsterLook(0.9f, 0.94f, bigBelly = false),
        Form.PAUSBAECKCHEN to hamsterLook(1.2f, 0.86f),
        Form.TUNNELGRAEBER to hamsterLook(0.94f, 0.92f, bigBelly = false),
        Form.VORRATSMEISTER to hamsterLook(1.22f, 0.9f),
        Form.KLETTERHAMSTER to hamsterLook(0.9f, 0.98f, bigBelly = false),
        Form.GOLDBAECKCHEN to hamsterLook(1.16f, 0.9f),
        Form.LABYRINTHPROFI to hamsterLook(0.94f, 0.96f, bigBelly = false),
        Form.KUSCHELKUGEL to hamsterLook(1.2f, 0.84f).copy(back = Back.MANE),
        Form.GRAUERFLITZER to hamsterLook(0.94f, 0.94f, bigBelly = false),
        Form.SCHLAFMUETZE to hamsterLook(1.18f, 0.86f),
        Form.WEISERHAMSTER to hamsterLook(1f, 0.96f).copy(back = Back.MANE),
        Form.HERZENSHAMSTER to hamsterLook(1.12f, 0.92f).copy(back = Back.MANE),
        // Meeri
        Form.HUEPFSCHWEINCHEN to guineaLook(1.02f, 0.92f),
        Form.LOEWENZAHNFREUND to guineaLook(1.16f, 0.86f).copy(bigBelly = true),
        Form.RENNSCHWEINCHEN to guineaLook(1f, 0.94f),
        Form.HEUSCHWEINCHEN to guineaLook(1.14f, 0.88f),
        Form.WIRBELWIND to guineaLook(1.08f, 0.9f, mane = true, scruffy = true),
        Form.LANGHAARSCHWEINCHEN to guineaLook(1.14f, 0.88f, mane = true),
        Form.QUIEKMEISTER to guineaLook(1.06f, 0.94f),
        Form.PLUESCHSCHWEINCHEN to guineaLook(1.18f, 0.9f, mane = true).copy(bigBelly = true),
        Form.GRAUNAESCHEN to guineaLook(1.06f, 0.92f),
        Form.GEMUETLICHSCHWEINCHEN to guineaLook(1.2f, 0.86f).copy(bigBelly = true),
        Form.WEISESMEERSCHWEINCHEN to guineaLook(1.1f, 0.94f, mane = true),
        Form.HERZENSSCHWEINCHEN to guineaLook(1.14f, 0.92f, mane = true),
    )

    private val shinyCache = HashMap<Form, FormLook>()
    private val composed = java.util.concurrent.ConcurrentHashMap<String, FormLook>()

    fun of(form: Form): FormLook = looks[form] ?: looks.getValue(Form.BABY)

    private fun pattern(p: SkinPattern): Pattern? = when (p) {
        SkinPattern.NONE -> null
        SkinPattern.TABBY -> Pattern.TABBY
        SkinPattern.CALICO -> Pattern.CALICO
        SkinPattern.TUXEDO -> Pattern.TUXEDO
        SkinPattern.POINTS -> Pattern.POINTS
        SkinPattern.STRIPES -> Pattern.STRIPES
        SkinPattern.SPOTS -> Pattern.SPOTS
        SkinPattern.STARS -> Pattern.STARS
        SkinPattern.GALAXY -> Pattern.GALAXY
        SkinPattern.HEARTS -> Pattern.HEARTS
        SkinPattern.ZIGZAG -> Pattern.ZIGZAG
        SkinPattern.SEEDS -> Pattern.SEEDS
        SkinPattern.ICING -> Pattern.ICING
        SkinPattern.RIBS -> Pattern.RIBS
    }

    private fun aura(a: SkinAura): Aura? = when (a) {
        SkinAura.NONE -> null
        SkinAura.SNOW -> Aura.SNOW
        SkinAura.HEARTS -> Aura.HEARTS
        SkinAura.STARS -> Aura.STARS
        SkinAura.BUBBLES -> Aura.BUBBLES
        SkinAura.LEAVES -> Aura.LEAVES
        SkinAura.FLAMES -> Aura.FLAMES
        SkinAura.SMOKE -> Aura.SMOKE
    }

    /** Paints a skin (cat fur or festival costume) over a form. */
    fun withSkin(base: FormLook, skin: Skin): FormLook {
        val k = base.colors
        val col = pal(
            skin.body,
            skin.accent ?: k.accent.toLong(),
            skin.accent2 ?: k.accent2.toLong(),
            skin.iris ?: k.iris.toLong(),
            belly = skin.belly,
            eye = k.eye.toLong(),
            cheek = k.cheek.toLong(),
            extra = skin.extra,
        )
        return base.copy(
            colors = col,
            // A fur decides the whole coat; a costume keeps the form's own pattern unless it brings one.
            pattern = pattern(skin.pattern) ?: if (skin.fur) Pattern.NONE else base.pattern,
            aura = aura(skin.aura) ?: base.aura,
            sparkles = base.sparkles || skin.sparkle,
        )
    }

    /** The final look of a pet: form, cat fur, festival costume and the shiny variant. */
    fun compose(form: Form, fur: String?, skin: String?, shiny: Boolean, blend: String? = null, age: Float = 0f): FormLook {
        if (age > 0.01f) {
            val key = "${form.name}|$fur|$skin|$shiny|$blend|${(age * 20f).toInt()}"
            composed[key]?.let { return it }
            return aged(compose(form, fur, skin, shiny, blend), age).also { composed[key] = it }
        }
        if (fur == null && skin == null && blend == null) return if (shiny) shiny(form) else of(form)
        val key = "${form.name}|$fur|$skin|$shiny|$blend"
        composed[key]?.let { return it }
        var fl = of(form)
        Skins[fur]?.let { fl = withSkin(fl, it) }
        Skins[skin]?.let { fl = withSkin(fl, it) }
        // Real animals don't change colour when they are rare, they just sparkle.
        if (shiny) fl = if (form.isReal || skin != null) fl.copy(sparkles = true) else shift(fl)
        // A baby from a family egg has a bit of the other parent's colours.
        val other = blend?.let { b -> Form.entries.firstOrNull { it.name == b } }
        if (other != null && skin == null && other != Form.EGG) fl = mix(fl, of(other).colors)
        composed[key] = fl
        return fl
    }

    /** An old pet: the colours become a little silvery. */
    private fun aged(base: FormLook, age: Float): FormLook {
        val k = base.colors
        val f = 0.5f * age
        fun silver(c: Int): Int {
            val l = (0.3f * Color.red(c) + 0.59f * Color.green(c) + 0.11f * Color.blue(c)).toInt().coerceIn(0, 255)
            val grey = Colors.lighten(Color.argb(Color.alpha(c), l, l, l), 0.25f)
            return Colors.lerp(c, grey, f)
        }
        val col = PetColors(silver(k.body), silver(k.light), silver(k.shade), silver(k.belly), k.accent, k.accent2, k.cheek, k.iris, k.eye, k.extra)
        return base.withColors(col, sparkle = base.sparkles).copy(elder = age)
    }

    private fun mix(base: FormLook, o: PetColors): FormLook {
        val k = base.colors
        fun m(a: Int, b: Int, f: Float) = Colors.lerp(a, b, f)
        val col = PetColors(
            m(k.body, o.body, 0.35f), m(k.light, o.light, 0.35f), m(k.shade, o.shade, 0.35f), m(k.belly, o.belly, 0.25f),
            m(k.accent, o.accent, 0.5f), m(k.accent2, o.accent2, 0.5f), k.cheek, m(k.iris, o.iris, 0.5f), k.eye, m(k.extra, o.extra, 0.4f),
        )
        return base.withColors(col, sparkle = base.sparkles)
    }

    /** Shiny variant: hue-shifted colours plus sparkles. */
    fun shiny(form: Form): FormLook = shinyCache.getOrPut(form) { shift(of(form)) }

    private fun shift(base: FormLook): FormLook {
        val k = base.colors
        fun h(color: Int, deg: Float): Int {
            val hsv = FloatArray(3)
            Color.colorToHSV(color, hsv)
            // Almost-white or grey colours get a light tint instead of a hue shift.
            if (hsv[1] < 0.12f) {
                hsv[0] = (200f + deg) % 360f
                hsv[1] = 0.22f
            } else {
                hsv[0] = (hsv[0] + deg) % 360f
            }
            return Color.HSVToColor(Color.alpha(color), hsv)
        }
        val d = 150f
        val col = PetColors(h(k.body, d), h(k.light, d), h(k.shade, d), h(k.belly, d), h(k.accent, 60f), h(k.accent2, 60f), k.cheek, h(k.iris, d), k.eye, h(k.extra, 60f))
        return base.withColors(col, sparkle = true)
    }

    /** Colours and pattern of an egg of each line. */
    fun egg(line: EggLine): PetColors = when (line) {
        EggLine.KNUFFEL -> of(Form.EGG).colors
        EggLine.WALD -> pal(0xFFD8F0C0, 0xFF6FB04F, 0xFFB08457, 0xFF000000)
        EggLine.MEER -> pal(0xFFCDEFFF, 0xFF4FA8E0, 0xFFFFFFFF, 0xFF000000)
        EggLine.FEUER -> pal(0xFFFFD08A, 0xFFFF5A36, 0xFFFFB24D, 0xFF000000)
        EggLine.URZEIT -> pal(0xFFE8E0C8, 0xFF7FAF5A, 0xFFA88A5A, 0xFF000000)
        EggLine.TECHNO -> pal(0xFFD8E0EC, 0xFF3AB0FF, 0xFF8A94A8, 0xFF000000)
        EggLine.EINHORN -> pal(0xFFFFF4FA, 0xFFFF8FB8, 0xFF8FD8FF, 0xFF000000)
        EggLine.BLUETE -> pal(0xFFFFE6F0, 0xFFFF7FAF, 0xFF7FC46A, 0xFF000000)
        EggLine.STERN -> pal(0xFF4A4A9A, 0xFFFFE680, 0xFFB8A8FF, 0xFF000000)
        EggLine.ZUCKER -> pal(0xFFFFE8F4, 0xFFFF6AA8, 0xFF8FD8FF, 0xFF000000)
        EggLine.DSCHUNGEL -> pal(0xFFCDEFB8, 0xFF3FA05A, 0xFFFFD84D, 0xFF000000)
        EggLine.WUESTE -> pal(0xFFF6DDA8, 0xFFE0904A, 0xFFFFF4D8, 0xFF000000)
        EggLine.EDELSTEIN -> pal(0xFFE6ECFF, 0xFFB98AFF, 0xFF8AE0FF, 0xFF000000)
        EggLine.FROST -> pal(0xFFE8F6FF, 0xFF8FD0FF, 0xFFFFFFFF, 0xFF000000)
        EggLine.GRUSEL -> pal(0xFFFFA24D, 0xFF3A2E4A, 0xFF7A4AD6, 0xFF000000)
        EggLine.KATZE -> pal(0xFFE8C08A, 0xFFFF8FB8, 0xFFB07A4A, 0xFF000000)
        EggLine.HASE -> pal(0xFFF0DCC0, 0xFFFFB3C6, 0xFFB8905A, 0xFF000000)
        EggLine.HAMSTER -> pal(0xFFF4D8A8, 0xFFE8A060, 0xFF3A3436, 0xFF000000)
        EggLine.MEERI -> pal(0xFFE8F0C8, 0xFF9AC85A, 0xFFD8B060, 0xFF000000)
    }
}

/** Everything the renderer needs to know about the pet. */
data class PetLook(
    val form: Form,
    val mood: Mood,
    val sleeping: Boolean = false,
    val sick: Boolean = false,
    val hat: String? = null,
    val face: String? = null,
    val neck: String? = null,
    val level: Int = 1,
    val hatchTaps: Int = 0,
    val dirty: Boolean = false,
    val tired: Boolean = false,
    val hungry: Boolean = false,
    val line: EggLine = EggLine.KNUFFEL,
    val shiny: Boolean = false,
    val fur: String? = null,
    val skin: String? = null,
    val emotion: Emotion? = null,
    /** Tool of the current job, shown while working. */
    val tool: String? = null,
    val back: String? = null,
    /** Form of the other parent (family eggs): its colours shine through a little. */
    val blend: String? = null,
    /** How old the pet is (0 young, 1 at the end of its life, see [de.knuffi.core.Lifespan]). */
    val age: Float = 0f,
) {
    val formLook: FormLook get() = Looks.compose(form, fur, skin, shiny, blend, age)
    val isEgg: Boolean get() = form == Form.EGG
    val isCat: Boolean get() = form.isCat

    /** Rabbit, hamster or guinea pig. */
    val isSmall: Boolean get() = form.line.small

    companion object {
        fun of(state: GameState): PetLook? {
            val pet = state.pet ?: return null
            return PetLook(
                form = pet.form,
                mood = pet.mood(),
                sleeping = pet.sleeping,
                sick = pet.sick,
                hat = state.equipped[Slot.HAT],
                face = state.equipped[Slot.FACE],
                neck = state.equipped[Slot.NECK],
                level = pet.level,
                hatchTaps = pet.hatchTaps,
                dirty = pet.hygiene < 30 || pet.poops > 0,
                tired = pet.energy < 30,
                hungry = pet.satiety < 30,
                line = pet.line,
                shiny = pet.shiny,
                fur = pet.fur,
                skin = pet.skin,
                emotion = if (pet.alive && !pet.isEgg) pet.emotion(System.currentTimeMillis()) else null,
                tool = pet.job?.tool,
                back = state.equipped[Slot.BACK],
                blend = pet.blend,
                age = ageOf(pet),
            )
        }

        /** Rounded, so the look does not change all the time. */
        fun ageOf(pet: de.knuffi.core.Pet): Float = (de.knuffi.core.Lifespan.age(pet) * 10f).toInt() / 10f

        /** Look of any pet (e.g. one resting in the Kuschelhaus), wearing the shared outfit. */
        fun of(pet: de.knuffi.core.Pet, state: GameState): PetLook = PetLook(
            form = pet.form,
            mood = pet.mood(),
            sleeping = pet.sleeping,
            sick = pet.sick,
            hat = state.equipped[Slot.HAT],
            face = state.equipped[Slot.FACE],
            neck = state.equipped[Slot.NECK],
            level = pet.level,
            hatchTaps = pet.hatchTaps,
            line = pet.line,
            shiny = pet.shiny,
            fur = pet.fur,
            skin = pet.skin,
            back = state.equipped[Slot.BACK],
            blend = pet.blend,
            age = ageOf(pet),
        )
    }
}

object Colors {
    fun lerp(a: Int, b: Int, f: Float): Int {
        val t = f.coerceIn(0f, 1f)
        return Color.argb(
            (Color.alpha(a) + (Color.alpha(b) - Color.alpha(a)) * t).toInt(),
            (Color.red(a) + (Color.red(b) - Color.red(a)) * t).toInt(),
            (Color.green(a) + (Color.green(b) - Color.green(a)) * t).toInt(),
            (Color.blue(a) + (Color.blue(b) - Color.blue(a)) * t).toInt(),
        )
    }

    fun darken(color: Int, f: Float): Int = lerp(color, Color.argb(Color.alpha(color), 0, 0, 0), f)

    fun lighten(color: Int, f: Float): Int = lerp(color, Color.argb(Color.alpha(color), 255, 255, 255), f)

    fun alpha(color: Int, a: Float): Int =
        Color.argb((a.coerceIn(0f, 1f) * 255).toInt(), Color.red(color), Color.green(color), Color.blue(color))

    fun withAlpha(color: Int, a: Float): Int = alpha(color, a * Color.alpha(color) / 255f)
}
