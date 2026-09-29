package de.knuffi.app.render

import android.graphics.Color
import de.knuffi.core.EggLine
import de.knuffi.core.Form
import de.knuffi.core.GameState
import de.knuffi.core.Mood
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
)

enum class Ears { NONE, BUNNY, CAT, ROUND, FOX, FIN, LEAF, BAT, HORSE, WRAPPER }

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
enum class Tail { NONE, DRAGON, FLUFFY, FISH, LEAF, BOLT, CAT, RAINBOW, FLAME, COMET, DINO }
enum class EyeStyle { BIG, GRUMPY, GLOW }
enum class Pattern { NONE, SPOTS, STRIPES, STARS, PANELS, SWIRL, BANDAGE, SPRINKLES, GALAXY, CRACKS, BUTTONS, BANDS, RIBS }
enum class Back { NONE, PLATES, SHELL, MANE, FRILL, CAPE }
enum class Muzzle { NONE, SNOUT, NOSE, WHISKERS, BEAK, FANGS, CARROT }
enum class Shape { ROUND, SQUARE, DROP }
enum class Aura { NONE, FLAMES, BUBBLES, SNOW, HEARTS, LEAVES, STARS, SMOKE, BOLTS }

class FormLook(
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
) {
    val feet: Boolean get() = !ghost && shape != Shape.DROP

    fun withColors(c: PetColors, sparkle: Boolean) = FormLook(
        c, width, height, ears, antenna, wings, tail, horns, spikes, ghost, crown, headband, eyes, bigBelly,
        sparkles || sparkle, pattern, back, muzzle, shape, aura, antlers, claws, faceplate, jelly, ring,
    )
}

private fun c(hex: Long): Int = hex.toInt()

/** Builds a full palette from a few key colours. */
private fun pal(body: Long, accent: Long, accent2: Long, iris: Long, belly: Long? = null, eye: Long = 0xFF2A1F3D, cheek: Long = 0xFFFF9BB5): PetColors {
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
    )
}

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
    )

    private val shinyCache = HashMap<Form, FormLook>()

    fun of(form: Form): FormLook = looks[form] ?: looks.getValue(Form.BABY)

    /** Shiny variant: hue-shifted colours plus sparkles. */
    fun shiny(form: Form): FormLook = shinyCache.getOrPut(form) {
        val base = of(form)
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
        val col = PetColors(h(k.body, d), h(k.light, d), h(k.shade, d), h(k.belly, d), h(k.accent, 60f), h(k.accent2, 60f), k.cheek, h(k.iris, d), k.eye)
        base.withColors(col, sparkle = true)
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
        EggLine.FROST -> pal(0xFFE8F6FF, 0xFF8FD0FF, 0xFFFFFFFF, 0xFF000000)
        EggLine.GRUSEL -> pal(0xFFFFA24D, 0xFF3A2E4A, 0xFF7A4AD6, 0xFF000000)
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
) {
    val formLook: FormLook get() = if (shiny) Looks.shiny(form) else Looks.of(form)
    val isEgg: Boolean get() = form == Form.EGG

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
            )
        }

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
