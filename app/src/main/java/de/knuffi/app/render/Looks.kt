package de.knuffi.app.render

import android.graphics.Color
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
) {
    val feet: Boolean get() = !ghost
}

private fun c(hex: Long): Int = hex.toInt()

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
    val tired: Boolean = false,
    val hungry: Boolean = false,
) {
    val formLook: FormLook get() = Looks.of(form)
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

    fun withAlpha(color: Int, a: Float): Int = alpha(color, a * Color.alpha(color) / 255f)
}
