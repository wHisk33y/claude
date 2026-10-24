package de.knuffi.app.render

import de.knuffi.core.Trick
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.sin

/**
 * The movements of the tricks, the same in the room and on the trick page. Every trick has its own
 * clear shape: Sitz goes low, Männchen tall, Pirouette turns all the way round, the bow leans
 * forward, the roll travels over the floor, the salto flips backwards in the air.
 * [el] is the time in seconds since the trick started.
 */
object TrickMoves {
    private const val TAU = (2 * PI).toFloat()

    fun duration(trick: Trick, success: Boolean): Float = if (!success) 2.1f else when (trick) {
        Trick.SITZ -> 2.3f
        Trick.PFOTE -> 2.4f
        Trick.WINKEN -> 2.2f
        Trick.DREHEN -> 2.1f
        Trick.MAENNCHEN -> 2.5f
        Trick.SPRUNG -> 2.0f
        Trick.ROLLE -> 2.5f
        Trick.VERBEUGUNG -> 2.5f
        Trick.TANZ -> 4.4f
        Trick.SALTO -> 2.4f
    }

    /** When the feet touch the ground again after a jump (for dust and a squash), or null. */
    fun landing(trick: Trick): Float? = when (trick) {
        Trick.SPRUNG -> 1.05f
        Trick.SALTO -> 1.25f
        Trick.SITZ -> 0.28f
        else -> null
    }

    /** How far the pet moves sideways (fraction of the scene width) at [el], in the facing direction. */
    fun shift(trick: Trick, el: Float): Float = when (trick) {
        Trick.ROLLE -> 0.17f * smooth(ramp(el, 0.3f, 1.6f))
        else -> 0f
    }

    private fun ramp(el: Float, from: Float, to: Float): Float = ((el - from) / (to - from)).coerceIn(0f, 1f)
    private fun smooth(x: Float): Float = x * x * (3f - 2f * x)

    /** 0 → 1 in [inS] seconds, holds, and back to 0 in the last [outS] seconds of [dur]. */
    private fun hold(el: Float, inS: Float, outS: Float, dur: Float): Float =
        min(smooth(ramp(el, 0f, inS)), 1f - smooth(ramp(el, dur - outS, dur)))

    /** A hop: up and down once between [from] and [from] + [len]. */
    private fun hop(el: Float, from: Float, len: Float, height: Float): Float {
        val p = (el - from) / len
        return if (p in 0f..1f) 4f * height * p * (1f - p) else 0f
    }

    fun apply(pose: PetPose, trick: Trick, el: Float, success: Boolean, facing: Float = 1f) {
        if (!success) {
            // a clumsy try: it starts, wobbles and gets confused
            pose.tilt += sin(el * 16f) * (if (el < 1.1f) 10f else 4f) + if (el >= 1.1f) 6f else 0f
            pose.armL = 0.5f
            pose.armR = 0.7f
            pose.eyes = if (el < 1.1f) EyeShape.SQUINT else EyeShape.DIZZY
            pose.mouth = if (el < 1.1f) MouthShape.TONGUE else MouthShape.WAVY
            return
        }
        val dur = duration(trick, true)
        when (trick) {
            Trick.SITZ -> {
                // plops down low and wide, looks up at you
                val down = ramp(el, 0f, 0.28f)
                val k = min(easeOutBack(down), 1f - smooth(ramp(el, dur - 0.45f, dur)))
                pose.scaleY *= 1f - 0.26f * k
                pose.scaleX *= 1f + 0.13f * k
                pose.strideL = -0.7f * k
                pose.strideR = 0.7f * k
                pose.armL = -0.1f * k
                pose.armR = -0.1f * k
                pose.lookY = -0.7f * k
                pose.tilt += sin(el * 2.6f) * 5f * k
                pose.eyes = if (k > 0.8f) EyeShape.WIDE else EyeShape.HAPPY
                pose.mouth = MouthShape.SMILE
                pose.turn = 0.001f
            }
            Trick.PFOTE -> {
                // holds one paw out towards you and shakes hands
                val k = hold(el, 0.35f, 0.4f, dur)
                val shake = if (el in 0.6f..1.9f) sin((el - 0.6f) * 13f) * 16f else 0f
                pose.armR = 0.62f * k
                pose.armWaveR = -12f * k + shake
                pose.armL = -0.05f
                pose.tilt += 7f * k
                pose.lookY = -0.35f * k
                pose.lookX = 0.4f * k
                pose.eyes = EyeShape.HAPPY
                pose.mouth = MouthShape.OPEN
                pose.mouthOpen = 0.4f
                pose.blush = 0.95f
                pose.turn = 0.001f
            }
            Trick.WINKEN -> {
                // both arms high, waving and swaying, hopping a little
                val k = hold(el, 0.25f, 0.35f, dur)
                pose.armL = 0.98f * k
                pose.armR = 0.98f * k
                pose.armWaveL = sin(el * 13f) * 28f * k
                pose.armWaveR = -sin(el * 13f) * 28f * k
                pose.tilt += sin(el * 6.5f) * 7f * k
                pose.lift += abs(sin(el * 6.5f)) * 0.04f * k
                pose.eyes = EyeShape.HAPPY
                pose.mouth = MouthShape.OPEN
                pose.mouthOpen = 0.7f
            }
            Trick.DREHEN -> {
                // a real pirouette: once all the way round, arms out like a dancer
                val r = smooth(ramp(el, 0.2f, 1.45f))
                val k = hold(el, 0.2f, 0.3f, dur)
                pose.spin = r * TAU * facing
                pose.armL = 0.72f * k
                pose.armR = 0.72f * k
                pose.lift += sin(r * PI.toFloat()) * 0.06f
                pose.scaleY *= 1f + 0.06f * sin(r * PI.toFloat())
                pose.eyes = if (r in 0.05f..0.95f) EyeShape.CLOSED else EyeShape.STAR
                pose.mouth = MouthShape.SMILE
                pose.turn = 0.001f
                if (el > 1.45f) {
                    val e = ramp(el, 1.45f, 1.65f)
                    pose.armL = 0.72f + 0.25f * e
                    pose.armR = 0.72f + 0.25f * e
                }
            }
            Trick.MAENNCHEN -> {
                // stands up tall on the hind legs, paws in front, balancing
                val k = hold(el, 0.45f, 0.45f, dur)
                pose.lift += 0.16f * k
                pose.scaleY *= 1f + 0.2f * k
                pose.scaleX *= 1f - 0.09f * k
                pose.armL = 0.5f * k
                pose.armR = 0.5f * k
                pose.hold = 0.55f * k
                pose.armWaveL = sin(el * 9f) * 7f * k
                pose.armWaveR = -sin(el * 9f) * 7f * k
                pose.tilt += sin(el * 3.4f) * 5f * k
                pose.lookY = -0.75f * k
                pose.eyes = EyeShape.WIDE
                pose.mouth = MouthShape.OPEN
                pose.mouthOpen = 0.35f
                pose.turn = 0.001f
            }
            Trick.SPRUNG -> {
                // crouches, flies up high with the arms up, lands with a squash
                val crouch = sin(ramp(el, 0f, 0.3f) * PI.toFloat() / 2f) * (1f - ramp(el, 0.3f, 0.36f))
                pose.scaleY *= 1f - 0.24f * crouch
                pose.scaleX *= 1f + 0.15f * crouch
                val p = ramp(el, 0.3f, 1.05f)
                val air = if (el in 0.3f..1.05f) 1f - abs(2f * p - 1f) else 0f
                pose.lift += hop(el, 0.3f, 0.75f, 0.95f)
                pose.scaleY *= 1f + 0.16f * air
                pose.scaleX *= 1f - 0.1f * air
                pose.footL = 0.7f * air
                pose.footR = 0.7f * air
                val land = sin(ramp(el, 1.05f, 1.3f) * PI.toFloat())
                pose.scaleY *= 1f - 0.18f * land
                pose.scaleX *= 1f + 0.12f * land
                pose.armL = if (el < 0.3f) -0.15f else 1f
                pose.armR = pose.armL
                pose.eyes = if (el in 0.3f..1.05f) EyeShape.STAR else EyeShape.HAPPY
                pose.mouth = MouthShape.OPEN
                pose.mouthOpen = 0.6f
            }
            Trick.ROLLE -> {
                // rolls sideways over the floor, then is a bit dizzy
                val crouch = sin(ramp(el, 0f, 0.3f) * PI.toFloat() / 2f) * (1f - ramp(el, 0.3f, 0.4f))
                pose.scaleY *= 1f - 0.16f * crouch
                val r = smooth(ramp(el, 0.3f, 1.6f))
                pose.tilt += facing * 360f * r
                pose.lying = sin(r * PI.toFloat()) * 0.55f
                pose.armL = 0f
                pose.armR = 0f
                pose.eyes = when {
                    r in 0.03f..0.97f -> EyeShape.CLOSED
                    el in 1.6f..2.1f -> EyeShape.DIZZY
                    else -> EyeShape.HAPPY
                }
                pose.mouth = MouthShape.GRIN
                if (el > 1.6f) pose.tilt += sin(el * 11f) * 6f * (1f - ramp(el, 1.6f, 2.2f))
            }
            Trick.VERBEUGUNG -> {
                // a real bow forward, one arm out to the side
                val k = hold(el, 0.55f, 0.55f, dur)
                pose.pitch = 0.6f * k
                pose.armL = 0.7f * k
                pose.armWaveL = -12f * k
                pose.armR = -0.1f
                pose.lookY = 0.9f * k
                pose.eyes = if (k > 0.5f) EyeShape.CLOSED else EyeShape.HAPPY
                pose.mouth = MouthShape.SMILE
                pose.turn = 0.001f
            }
            Trick.TANZ -> {
                val beat = 0.55f
                when {
                    el < 1.65f -> {
                        // side steps on the beat, arms in turn
                        val b = sin(el * PI.toFloat() / beat)
                        pose.tilt += b * 11f
                        pose.lift += abs(b) * 0.07f
                        pose.armL = 0.5f + 0.45f * b
                        pose.armR = 0.5f - 0.45f * b
                        pose.strideL = b * 0.6f
                        pose.strideR = b * 0.6f
                    }
                    el < 2.75f -> {
                        // a spin
                        val r = smooth(ramp(el, 1.65f, 2.65f))
                        pose.spin = r * TAU * facing
                        pose.armL = 0.75f
                        pose.armR = 0.75f
                        pose.lift += sin(r * PI.toFloat()) * 0.05f
                    }
                    el < 3.6f -> {
                        // a shimmy with the arms up
                        pose.scaleX *= 1f + 0.06f * sin(el * 30f)
                        pose.tilt += sin(el * 15f) * 4f
                        pose.armL = 0.92f
                        pose.armR = 0.92f
                        pose.armWaveL = sin(el * 15f) * 16f
                        pose.armWaveR = sin(el * 15f) * 16f
                    }
                    else -> {
                        // the big finish
                        pose.lift += hop(el, 3.6f, 0.3f, 0.22f)
                        pose.armL = 1f
                        pose.armR = 1f
                        pose.tilt += facing * 8f * ramp(el, 3.6f, 3.8f)
                    }
                }
                pose.eyes = if (el >= 3.6f) EyeShape.STAR else EyeShape.HAPPY
                pose.mouth = MouthShape.OPEN
                pose.mouthOpen = 0.5f
                pose.blush = 0.9f
            }
            Trick.SALTO -> {
                // crouch, a high jump and a full flip backwards, landing with arms up
                val crouch = sin(ramp(el, 0f, 0.25f) * PI.toFloat() / 2f) * (1f - ramp(el, 0.25f, 0.3f))
                pose.scaleY *= 1f - 0.24f * crouch
                pose.scaleX *= 1f + 0.14f * crouch
                pose.lift += hop(el, 0.25f, 1.0f, 1.1f)
                val r = smooth(ramp(el, 0.32f, 1.15f))
                pose.tilt -= facing * 360f * r
                pose.scaleY *= 1f - 0.12f * sin(r * PI.toFloat())
                val tuck = sin(r * PI.toFloat())
                pose.armL = if (el < 0.25f) -0.15f else 1f - 0.7f * tuck
                pose.armR = pose.armL
                pose.footL = 0.8f * tuck
                pose.footR = 0.8f * tuck
                val land = sin(ramp(el, 1.25f, 1.45f) * PI.toFloat())
                pose.scaleY *= 1f - 0.18f * land
                pose.scaleX *= 1f + 0.12f * land
                pose.eyes = if (r in 0.05f..0.95f) EyeShape.CLOSED else EyeShape.STAR
                pose.mouth = MouthShape.OPEN
                pose.mouthOpen = 0.6f
            }
        }
    }

    private fun easeOutBack(p: Float): Float {
        val c1 = 1.70158f
        val c3 = c1 + 1f
        return 1f + c3 * (p - 1f) * (p - 1f) * (p - 1f) + c1 * (p - 1f) * (p - 1f)
    }
}
