package de.knuffi.app.render

import de.knuffi.core.Emotion

enum class EyeShape { OPEN, HAPPY, CLOSED, HEART, STAR, DIZZY, WIDE, SQUINT, SLEEPY }
enum class MouthShape { CAT, SMILE, OPEN, O, CHEW, FROWN, WAVY, YAWN, TONGUE, FLAT, GRIN }

/** All animation values for one rendered frame of the pet. Units are fractions of the pet size. */
class PetPose {
    var t = 0f

    // Body
    var lift = 0f
    var scaleX = 1f
    var scaleY = 1f
    var tilt = 0f
    var turn = 0f
    var breath = 0f
    var lying = 0f

    // Limbs (0 = rest)
    var footL = 0f
    var footR = 0f
    var strideL = 0f
    var strideR = 0f
    var armL = 0f
    var armR = 0f
    var armWaveL = 0f
    var armWaveR = 0f
    var hold = 0f
    var earLag = 0f

    // Face
    var eyes = EyeShape.OPEN
    var eyeOpen = 1f
    var lookX = 0f
    var lookY = 0f
    var mouth = MouthShape.CAT
    var mouthOpen = 0f
    var blush = 0.55f
    var brows = 0f
    var tear = 0f
    var sweat = 0f

    // Extras
    var sickTint = 0f
    var foam = 0f
    var glow = 0f
    var nightCap = 0f
    var holding: String? = null
    var eggWobble = 0f

    /** Paint a friend put on the fur (0 = none). */
    var paint = 0f

    fun reset(): PetPose {
        t = 0f; lift = 0f; scaleX = 1f; scaleY = 1f; tilt = 0f; turn = 0f; breath = 0f; lying = 0f
        footL = 0f; footR = 0f; strideL = 0f; strideR = 0f; armL = 0f; armR = 0f; armWaveL = 0f; armWaveR = 0f
        hold = 0f; earLag = 0f
        eyes = EyeShape.OPEN; eyeOpen = 1f; lookX = 0f; lookY = 0f; mouth = MouthShape.CAT; mouthOpen = 0f
        blush = 0.55f; brows = 0f; tear = 0f; sweat = 0f
        sickTint = 0f; foam = 0f; glow = 0f; nightCap = 0f; holding = null; eggWobble = 0f; paint = 0f
        return this
    }

    fun copyFrom(o: PetPose): PetPose {
        t = o.t; lift = o.lift; scaleX = o.scaleX; scaleY = o.scaleY; tilt = o.tilt; turn = o.turn; breath = o.breath; lying = o.lying
        footL = o.footL; footR = o.footR; strideL = o.strideL; strideR = o.strideR; armL = o.armL; armR = o.armR
        armWaveL = o.armWaveL; armWaveR = o.armWaveR; hold = o.hold; earLag = o.earLag
        eyes = o.eyes; eyeOpen = o.eyeOpen; lookX = o.lookX; lookY = o.lookY; mouth = o.mouth; mouthOpen = o.mouthOpen
        blush = o.blush; brows = o.brows; tear = o.tear; sweat = o.sweat
        sickTint = o.sickTint; foam = o.foam; glow = o.glow; nightCap = o.nightCap; holding = o.holding; eggWobble = o.eggWobble; paint = o.paint
        return this
    }

    /** A pleasant default pose for static renders (widget, notifications, portraits). */
    fun defaults(look: PetLook, t: Float): PetPose {
        reset()
        this.t = t
        breath = kotlin.math.sin(t * 2f * Math.PI.toFloat() / 3.2f)
        scaleY = 1f + 0.025f * breath
        scaleX = 1f - 0.015f * breath
        applyMood(look)
        return this
    }

    fun applyMood(look: PetLook) {
        val fl = look.formLook
        when {
            look.sleeping -> {
                eyes = EyeShape.CLOSED
                mouth = MouthShape.O
                mouthOpen = 0.3f
                lying = 1f
                nightCap = if (look.hat == null) 1f else 0f
            }
            look.sick -> {
                eyes = EyeShape.SQUINT
                mouth = MouthShape.WAVY
                sickTint = 1f
                sweat = 1f
                brows = -1f
            }
            look.mood == de.knuffi.core.Mood.SAD -> {
                eyes = EyeShape.OPEN
                eyeOpen = 0.85f
                mouth = MouthShape.FROWN
                brows = -1f
                tear = 1f
            }
            look.mood == de.knuffi.core.Mood.HAPPY -> {
                mouth = MouthShape.OPEN
                blush = 0.75f
            }
            look.tired -> {
                eyes = EyeShape.SLEEPY
                mouth = MouthShape.SMILE
            }
            else -> mouth = MouthShape.CAT
        }
        if (fl.eyes == EyeStyle.GRUMPY && !look.sleeping && look.mood != de.knuffi.core.Mood.HAPPY) {
            if (eyes == EyeShape.OPEN) eyes = EyeShape.SQUINT
            if (brows == 0f) brows = 1f
            if (mouth == MouthShape.CAT) mouth = MouthShape.FLAT
        }
        if (look.sleeping || look.sick || look.mood == de.knuffi.core.Mood.SAD) return
        // The finer feeling shows on the face.
        when (look.emotion) {
            Emotion.GRUMMELIG -> {
                brows = 1f
                mouth = MouthShape.FLAT
                blush = 0.2f
            }
            Emotion.EINSAM -> {
                lookY = 0.5f
                eyeOpen = 0.9f
                mouth = MouthShape.FROWN
                brows = -0.6f
            }
            Emotion.GELANGWEILT -> {
                eyes = EyeShape.SLEEPY
                mouth = MouthShape.FLAT
            }
            Emotion.VERSCHMUST -> {
                blush = 1f
                mouth = MouthShape.CAT
            }
            Emotion.VERSPIELT -> mouth = MouthShape.TONGUE
            Emotion.STOLZ -> {
                mouth = MouthShape.GRIN
                blush = 0.7f
            }
            Emotion.AUFGEREGT -> {
                mouth = MouthShape.OPEN
                blush = 0.95f
            }
            Emotion.NEUGIERIG -> {
                mouth = MouthShape.O
                mouthOpen = 0.25f
            }
            Emotion.MUEDE -> eyes = EyeShape.SLEEPY
            else -> Unit
        }
    }
}
