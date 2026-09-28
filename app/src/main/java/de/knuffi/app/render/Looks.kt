package de.knuffi.app.render

import android.graphics.Color
import de.knuffi.core.Form
import de.knuffi.core.GameState
import de.knuffi.core.Mood
import de.knuffi.core.Slot
import de.knuffi.core.VisualStyle

enum class RenderMode { SMOOTH, FLAT, PIXEL }

val VisualStyle.renderMode: RenderMode
    get() = when (this) {
        VisualStyle.KAWAII -> RenderMode.SMOOTH
        VisualStyle.MINIMAL -> RenderMode.FLAT
        VisualStyle.PIXEL -> RenderMode.PIXEL
    }

class PetColors(
    val body: Int,
    val shade: Int,
    val belly: Int,
    val accent: Int,
    val accent2: Int,
    val cheek: Int,
    val eye: Int = 0xFF2E2440.toInt(),
)

enum class Ears { NONE, BUNNY, CAT, ROUND }
enum class Antenna { NONE, SPROUT, GLOW, STAR }
enum class Wings { NONE, SMALL, DRAGON, ANGEL }
enum class Tail { NONE, DRAGON }
enum class EyeStyle { BIG, GRUMPY, GLOW }

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
)

private fun c(hex: Long): Int = hex.toInt()

object Looks {
    private val looks: Map<Form, FormLook> = mapOf(
        Form.EGG to FormLook(
            PetColors(c(0xFFFFF4E0), c(0xFFF0D2A8), c(0xFFFFFBF2), c(0xFFFF9EC0), c(0xFF9ED8FF), c(0xFFFFB5C8)),
            width = 0.8f, height = 1f,
        ),
        Form.BABY to FormLook(
            PetColors(c(0xFFFFD3E6), c(0xFFF3A2C6), c(0xFFFFF0F7), c(0xFF7DD9B0), c(0xFFFF8FB8), c(0xFFFF8FB5)),
            width = 0.92f, height = 0.78f, antenna = Antenna.SPROUT,
        ),
        Form.HOPSI to FormLook(
            PetColors(c(0xFFDCCBFF), c(0xFFB39DF0), c(0xFFF5EFFF), c(0xFFFFB3D1), c(0xFFFFFFFF), c(0xFFFF9EC4)),
            width = 0.86f, height = 0.9f, ears = Ears.BUNNY,
        ),
        Form.GRUMMEL to FormLook(
            PetColors(c(0xFFB9C5D8), c(0xFF8C9AB3), c(0xFFDFE5EE), c(0xFF76849F), c(0xFF5E6B85), c(0xFFE6A5B8)),
            width = 0.92f, height = 0.84f, spikes = true, eyes = EyeStyle.GRUMPY,
        ),
        Form.FLITZER to FormLook(
            PetColors(c(0xFF9FD9FF), c(0xFF69B6EC), c(0xFFE4F5FF), c(0xFFFF6B6B), c(0xFFFFFFFF), c(0xFFFF9BB0)),
            width = 0.84f, height = 0.96f, ears = Ears.CAT, wings = Wings.SMALL, headband = true,
        ),
        Form.MAMPFI to FormLook(
            PetColors(c(0xFFFFCCA6), c(0xFFF0A274), c(0xFFFFEEDF), c(0xFFFF8A65), c(0xFFFFD27D), c(0xFFFF8F8F)),
            width = 1.08f, height = 0.84f, ears = Ears.ROUND, bigBelly = true,
        ),
        Form.LUMI to FormLook(
            PetColors(c(0xFFA9F0D2), c(0xFF6CD3AB), c(0xFFE8FFF5), c(0xFFFFE66D), c(0xFFFFFFFF), c(0xFFFFA5C0)),
            width = 0.86f, height = 0.96f, antenna = Antenna.GLOW,
        ),
        Form.STACHLI to FormLook(
            PetColors(c(0xFFBBA9CE), c(0xFF8F7AA8), c(0xFFE4DBEE), c(0xFF6A5786), c(0xFF4F4066), c(0xFFE8A0C0)),
            width = 0.9f, height = 0.96f, ears = Ears.CAT, spikes = true, eyes = EyeStyle.GRUMPY,
        ),
        Form.DRAKO to FormLook(
            PetColors(c(0xFF93E4A3), c(0xFF58C375), c(0xFFEEFFDD), c(0xFFFFD166), c(0xFF4FAE98), c(0xFFFF9AA8)),
            width = 0.92f, height = 1f, wings = Wings.DRAGON, tail = Tail.DRAGON, horns = true,
        ),
        Form.STELLARIS to FormLook(
            PetColors(c(0xFFBCB8FF), c(0xFF8C87F0), c(0xFFEFEDFF), c(0xFFFFD95A), c(0xFFFFFFFF), c(0xFFFFA8D0)),
            width = 0.9f, height = 1f, antenna = Antenna.STAR, wings = Wings.ANGEL, sparkles = true,
        ),
        Form.MOCHI_KOENIG to FormLook(
            PetColors(c(0xFFFFF2F6), c(0xFFF0C4D3), c(0xFFFFFFFF), c(0xFFFFC83D), c(0xFFFF6F91), c(0xFFFF9EB8)),
            width = 1.14f, height = 0.86f, ears = Ears.ROUND, crown = true, bigBelly = true,
        ),
        Form.SCHATTLING to FormLook(
            PetColors(c(0xFF706C8E), c(0xFF4E4A6C), c(0xFF8F8AB0), c(0xFFB9F2FF), c(0xFF3A3656), c(0xFF9E8ACB), c(0xFFB9F2FF)),
            width = 0.86f, height = 1f, ghost = true, eyes = EyeStyle.GLOW,
        ),
    )

    fun of(form: Form): FormLook = looks.getValue(form)
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
) {
    val formLook: FormLook get() = Looks.of(form)

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
                dirty = pet.hygiene < 30,
            )
        }
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
}
