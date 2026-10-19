package de.knuffi.app.screen

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Typeface
import android.view.Choreographer
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import androidx.core.content.res.ResourcesCompat
import de.knuffi.app.R
import de.knuffi.app.render.EyeShape
import de.knuffi.app.render.MouthShape
import de.knuffi.app.render.PKind
import de.knuffi.app.render.Particle
import de.knuffi.app.render.ParticleSystem
import de.knuffi.app.render.PetLook
import de.knuffi.app.render.PetPose
import de.knuffi.app.render.PetRenderer
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sign
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

/**
 * The small window that shows the pet over other apps. It owns a tiny behaviour loop:
 * walking along the bottom of the screen, idling, waving, sleeping, being carried and
 * falling down again with a squishy landing.
 */
@SuppressLint("ViewConstructor")
class OverlayPetView(context: Context, private val host: Host) : View(context) {

    interface Host {
        /** Moves the window so that its top-left corner is at (x, y) in screen pixels. */
        fun moveWindow(x: Int, y: Int)

        /** Left, right and ground (feet) line of the area the pet may use. */
        fun bounds(): FloatArray
        fun onStroke()
        fun onHatchTap()
        fun onOpenApp()

        /** Screen toys: shared state, slider values, splashes and rewards. */
        fun toys(): OverlayToys
        fun toyConfig(): de.knuffi.app.render.ToyConfig
        fun addSplash(x: Float, y: Float): Boolean
        fun onToy(kind: de.knuffi.core.ToyKind)
        fun decorChanged(now: Float, visible: Boolean)

        /** Double tap: stick to the top left corner or walk around again. */
        fun onTogglePin()
    }

    private enum class Mode { IDLE, WALK, WAVE, SIT, SLEEP, DRAG, FALL, LOVE, LADDER, PLANE, PAINT, PIN, BUBBLES, MUSIC, READ, SNACK, FLIP, KISS }

    /** Name shown above the pet (empty = no tag). */
    var name: String = ""
    var showName = true

    /** Sits in the top left corner (set by the player). */
    var pinned = false
    private var pinX = 0f
    private var pinY = 0f
    private var topY = 0f
    private var lastTapAt = 0L
    private var sayText: String? = null
    private var sayUntil = 0f
    private val tagPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = android.graphics.Paint.Align.CENTER
        textSize = 12f * resources.displayMetrics.density
    }
    private val tagBg = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)
    private val tagRect = android.graphics.RectF()

    private val density = resources.displayMetrics.density
    val petSize = 76f * density
    val windowW = (petSize * 1.7f).toInt()
    val windowH = (petSize * 1.9f).toInt()
    private val footInWindow = windowH * 0.9f

    private val renderer = PetRenderer()
    private val pose = PetPose()
    private val particles = ParticleSystem()
    private val rnd = Random(System.nanoTime())

    var look: PetLook? = null

    // Feet position in screen coordinates.
    private var x = -1f
    private var y = -1f
    private var vx = 0f
    private var vy = 0f
    private var targetX = 0f
    private var facing = 1f
    private var turn = 0f
    private var mode = Mode.FALL
    private var modeStart = 0f
    private var modeDur = 1f
    private var now = 0f
    private var lastFrameNs = 0L
    private var walkPhase = 0f
    private var sq = 0f
    private var sqV = 0f
    private var swing = 0f
    private var swingV = 0f
    private var nextBlink = 2f
    private var lastZ = 0f
    private var lastStepSign = 0f
    private var groundY = 0f

    // Touch
    private var downRawX = 0f
    private var downRawY = 0f
    private var grabDX = 0f
    private var grabDY = 0f
    private var dragging = false
    private var longPressed = false
    private var lastMoveRawX = 0f
    private var lastMoveRawY = 0f
    private var lastMoveTime = 0L
    private var dragVX = 0f
    private var dragVY = 0f
    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop
    private val longPress = Runnable {
        if (!dragging) {
            longPressed = true
            performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS)
            host.onOpenApp()
        }
    }

    private var running = false
    private val frameCallback = object : Choreographer.FrameCallback {
        override fun doFrame(frameTimeNanos: Long) {
            if (!running) return
            step(frameTimeNanos)
            Choreographer.getInstance().postFrameCallback(this)
        }
    }

    init {
        val tf = runCatching { ResourcesCompat.getFont(context, R.font.fredoka_semibold) }.getOrNull() ?: Typeface.DEFAULT_BOLD
        particles.typeface = tf
    }

    fun start() {
        if (running) return
        running = true
        lastFrameNs = 0L
        Choreographer.getInstance().postFrameCallback(frameCallback)
    }

    fun stop() {
        running = false
        Choreographer.getInstance().removeFrameCallback(frameCallback)
        removeCallbacks(longPress)
    }

    override fun onDetachedFromWindow() {
        stop()
        super.onDetachedFromWindow()
    }

    private fun setMode(m: Mode, dur: Float = 1f) {
        mode = m
        modeStart = now
        modeDur = dur
    }

    private fun step(frameTimeNanos: Long) {
        val dt = if (lastFrameNs == 0L) 0.016f else ((frameTimeNanos - lastFrameNs) / 1e9f).coerceIn(0f, 0.05f)
        lastFrameNs = frameTimeNanos
        now += dt
        val b = host.bounds()
        val left = b[0] + petSize * 0.5f
        val right = b[1] - petSize * 0.5f
        val ground = b[2]
        if (x < 0f) {
            x = left + (right - left) * 0.8f
            y = ground - petSize * 2f
            setMode(Mode.FALL)
        }
        val l = look
        groundY = ground
        pinX = b[0] + petSize * 0.6f
        pinY = b[3] + petSize * 1.15f
        topY = b[3]
        val wantPin = pinned
        if (wantPin && mode != Mode.PIN && mode != Mode.DRAG) {
            host.toys().ladderExtend = 0f
            setMode(Mode.PIN, 1e9f)
        } else if (!wantPin && mode == Mode.PIN) {
            vx = 0f
            vy = 0f
            setMode(Mode.FALL)
        }
        update(dt, l, left, right, ground)
        particles.update(now)
        val busy = host.toys().update(now, dt, density, ground, b[0] + petSize * 0.2f, b[1] - petSize * 0.2f, host.toyConfig())
        host.decorChanged(now, busy)
        host.moveWindow((x - windowW / 2f).toInt(), (y - footInWindow).toInt())
        invalidate()
    }

    private fun chooseNext(l: PetLook?, left: Float, right: Float) {
        if (l != null && l.sleeping) {
            setMode(Mode.SLEEP, 1e9f)
            return
        }
        if (l != null && l.isEgg) {
            setMode(Mode.IDLE, 3f)
            return
        }
        if (l != null && !l.sick && !l.tired && l.mood != de.knuffi.core.Mood.SAD) {
            val cfg = host.toyConfig()
            var t = rnd.nextFloat()
            val ladder = de.knuffi.core.ToyTuning.chance(cfg.ladder)
            val plane = de.knuffi.core.ToyTuning.chance(cfg.planes)
            val paint = de.knuffi.core.ToyTuning.chance(cfg.paint)
            if (t < ladder) {
                setMode(Mode.LADDER, 99f)
                return
            }
            t -= ladder
            if (t < plane && host.toys().flyer == null) {
                setMode(Mode.PLANE, 3.4f)
                return
            }
            t -= plane
            if (t < paint) {
                setMode(Mode.PAINT, 3f)
                return
            }
        }
        if (l != null && !l.sick && !l.tired && l.mood != de.knuffi.core.Mood.SAD && rnd.nextFloat() < 0.25f) {
            hobby(l.isCat)
            return
        }
        val r = rnd.nextFloat()
        when {
            r < 0.5f -> {
                var tx = left + rnd.nextFloat() * (right - left)
                if (abs(tx - x) < petSize) tx = if (x < (left + right) / 2f) right - petSize * 0.2f else left + petSize * 0.2f
                targetX = tx
                setMode(Mode.WALK, 30f)
            }
            r < 0.65f -> setMode(Mode.WAVE, 1.6f)
            r < 0.8f -> setMode(Mode.SIT, 4f + rnd.nextFloat() * 4f)
            else -> setMode(Mode.IDLE, 2f + rnd.nextFloat() * 3f)
        }
    }

    private var hobbyItem = ""

    private fun say(text: String, seconds: Float) {
        sayText = text
        sayUntil = now + seconds
    }

    /** Little hobbies so the floating pet does something different every now and then. */
    private fun hobby(cat: Boolean) {
        val pick = if (cat) listOf(Mode.BUBBLES, Mode.SNACK, Mode.FLIP, Mode.KISS).random(rnd)
        else listOf(Mode.BUBBLES, Mode.MUSIC, Mode.READ, Mode.SNACK, Mode.FLIP, Mode.KISS).random(rnd)
        when (pick) {
            Mode.BUBBLES -> say(if (cat) "Miau! Blasen!" else "Seifenblasen! 🫧", 1.6f)
            Mode.MUSIC -> {
                hobbyItem = listOf("🎸", "🎺", "🥁", "🎻", "🪇").random(rnd)
                say("Ein Lied für dich! 🎶", 1.6f)
            }
            Mode.READ -> say("Ich lese ein bisschen.", 1.6f)
            Mode.SNACK -> {
                hobbyItem = if (cat) listOf("🐟", "🍗", "🧀").random(rnd) else listOf("🍪", "🍓", "🥨", "🍩").random(rnd)
                say(if (cat) "Leckerli! Prrr …" else "Snack-Pause!", 1.4f)
            }
            Mode.FLIP -> {
                vy = -petSize * 4.2f
                say(if (cat) "Mrrrp-hopp!" else "Purzelbaum!", 1.1f)
            }
            Mode.KISS -> say(if (cat) "Prrr … 💕" else "Hab dich lieb! 💕", 1.6f)
            else -> Unit
        }
        setMode(
            pick,
            when (pick) {
                Mode.BUBBLES -> 3.6f
                Mode.MUSIC -> 4f
                Mode.READ -> 4.6f
                Mode.SNACK -> 2.8f
                Mode.FLIP -> 1.2f
                else -> 1.8f
            },
        )
    }

    private fun update(dt: Float, l: PetLook?, left: Float, right: Float, ground: Float) {
        val p = if (l != null) pose.defaults(l, now) else pose.reset()
        val el = now - modeStart
        var walking = false
        when (mode) {
            Mode.DRAG -> {
                // Pendulum swing based on how fast the pet is moved.
                swingV += (-60f * swing - 6f * swingV - dragVX * 0.02f) * dt
                swing += swingV * dt
                p.tilt = (swing * 30f).coerceIn(-35f, 35f)
                p.armL = 1f
                p.armR = 1f
                p.lying = 0f
                p.footL = 0.3f + 0.3f * sin(now * 9f)
                p.footR = 0.3f + 0.3f * sin(now * 9f + 1.5f)
                p.eyes = if (l?.sleeping == true) EyeShape.SLEEPY else EyeShape.WIDE
                p.mouth = MouthShape.O
                p.mouthOpen = 0.6f
                p.scaleY *= 1.06f
                p.scaleX *= 0.95f
            }
            Mode.FALL -> {
                vy += 2600f * density * dt
                x += vx * dt
                y += vy * dt
                vx *= (1f - 0.8f * dt)
                if (x < left) {
                    x = left
                    vx = -vx * 0.5f
                }
                if (x > right) {
                    x = right
                    vx = -vx * 0.5f
                }
                p.tilt = (vx / density * 0.05f).coerceIn(-25f, 25f)
                p.armL = 1f
                p.armR = 1f
                p.eyes = EyeShape.WIDE
                p.mouth = MouthShape.O
                p.lying = 0f
                if (y >= ground) {
                    y = ground
                    val impact = min(1f, vy / (1500f * density))
                    sqV += 8f + 10f * impact
                    vy = 0f
                    if (impact > 0.6f) {
                        particles.burst(PKind.STAR, 0.5f, 0.8f, 5, 0.3f, 0.7f, 0.05f, ParticleSystem.STAR_COLORS, gravity = 0.8f, upward = true)
                    }
                    if (abs(vx) > 200f * density) {
                        vy = -abs(vx) * 0.25f
                        vx *= 0.6f
                    } else {
                        vx = 0f
                        chooseNext(l, left, right)
                    }
                }
            }
            Mode.WALK -> {
                y = ground
                val dx = targetX - x
                val speed = 70f * density
                if (abs(dx) < 3f) {
                    chooseNext(l, left, right)
                } else {
                    x += sign(dx) * min(abs(dx), speed * dt)
                    facing = sign(dx)
                    walking = true
                }
            }
            Mode.WAVE -> {
                y = ground
                p.armR = 1f
                p.armWaveR = sin(el * 14f) * 25f
                p.eyes = EyeShape.HAPPY
                p.mouth = MouthShape.OPEN
                if (el < 0.05f) particles.add(Particle(PKind.EMOJI, 0.7f, 0.18f, 0f, -0.05f, 1.2f, 0.12f, 0, text = "👋"))
                if (el > modeDur) chooseNext(l, left, right)
            }
            Mode.BUBBLES -> {
                y = ground
                p.holding = "🫧"
                p.hold = 1f
                p.mouth = MouthShape.O
                p.mouthOpen = 0.6f
                p.lookX = facing * 0.6f
                p.lookY = -0.5f
                if ((el % 0.3f) < dt && el < modeDur - 0.5f) {
                    particles.add(Particle(PKind.BUBBLE, 0.5f + facing * 0.15f, 0.42f, facing * (0.04f + rnd.nextFloat() * 0.05f), -0.06f - rnd.nextFloat() * 0.06f, 2f, 0.04f + rnd.nextFloat() * 0.03f, 0xFFFFFFFF.toInt(), drag = 0.3f))
                }
                if (el > modeDur) chooseNext(l, left, right)
            }
            Mode.MUSIC -> {
                y = ground
                p.holding = hobbyItem
                p.hold = 1f
                val k = el * 6f
                p.tilt += sin(k) * 7f
                p.eyes = if ((el % 2f) < 1.3f) EyeShape.CLOSED else EyeShape.HAPPY
                p.mouth = MouthShape.SMILE
                if ((el % 0.4f) < dt) particles.add(Particle(PKind.NOTE, 0.4f + rnd.nextFloat() * 0.3f, 0.3f, (rnd.nextFloat() - 0.5f) * 0.05f, -0.1f, 1.4f, 0.07f, listOf(0xFF7C6CFF, 0xFFFF6B9A, 0xFF2EC4A0, 0xFFFFB84D).random(rnd).toInt()))
                if (el > modeDur) chooseNext(l, left, right)
            }
            Mode.READ -> {
                y = ground
                p.holding = "📖"
                p.hold = 1f
                p.lookY = 0.7f
                p.scaleY *= 0.95f
                p.scaleX *= 1.03f
                p.eyes = if ((el % 1.6f) < 0.25f) EyeShape.HAPPY else EyeShape.OPEN
                if (el > 2.8f && el - dt <= 2.8f) say(listOf("Spannend!", "Und dann …?", "Haha!").random(rnd), 1.4f)
                if (el > modeDur) chooseNext(l, left, right)
            }
            Mode.SNACK -> {
                y = ground
                p.holding = hobbyItem
                p.hold = 1f - el / modeDur * 0.4f
                p.mouth = if ((el * 8f).toInt() % 2 == 0) MouthShape.CHEW else MouthShape.CAT
                p.eyes = EyeShape.HAPPY
                if ((el % 0.5f) < dt) particles.add(Particle(PKind.CRUMB, 0.5f + facing * 0.05f, 0.5f, (rnd.nextFloat() - 0.5f) * 0.08f, 0.02f, 0.8f, 0.025f, 0xFFD8A060.toInt(), rot = rnd.nextFloat() * 90f, gravity = 0.4f))
                if (el > modeDur) chooseNext(l, left, right)
            }
            Mode.FLIP -> {
                vy += petSize * 9f * dt
                y = min(ground, y + vy * dt)
                val k = (el / 0.9f).coerceIn(0f, 1f)
                p.tilt += facing * 360f * (k * k * (3f - 2f * k))
                p.eyes = if (k < 1f) EyeShape.CLOSED else EyeShape.STAR
                p.mouth = MouthShape.OPEN
                p.armL = 1f
                p.armR = 1f
                if (el > modeDur || (el > 0.3f && y >= ground)) {
                    y = ground
                    vy = 0f
                    particles.burst(PKind.STAR, 0.5f, 0.6f, 5, 0.3f, 0.7f, 0.05f, ParticleSystem.STAR_COLORS, upward = true)
                    chooseNext(l, left, right)
                }
            }
            Mode.KISS -> {
                y = ground
                p.armR = 0.9f
                p.eyes = EyeShape.HAPPY
                p.mouth = MouthShape.O
                p.blush = 1f
                if (el > 0.5f && el - dt <= 0.5f) particles.burst(PKind.HEART, 0.6f, 0.35f, 4, 0.2f, 1.2f, 0.06f, ParticleSystem.HEART_COLORS, upward = true)
                if (el > modeDur) chooseNext(l, left, right)
            }
            Mode.SIT -> {
                y = ground
                p.scaleY *= 0.92f
                p.scaleX *= 1.05f
                p.lookX = sin(el * 0.7f) * 0.8f
                if (l?.tired == true && el > 2f) p.eyes = EyeShape.SLEEPY
                if (el > modeDur) chooseNext(l, left, right)
            }
            Mode.SLEEP -> {
                y = ground
                if (l == null || !l.sleeping) {
                    setMode(Mode.WAVE, 1.4f)
                } else if (now - lastZ > 1.4f) {
                    lastZ = now
                    particles.add(Particle(PKind.ZZZ, 0.62f, 0.45f, 0.02f, -0.08f, 2f, 0.1f, 0xFF8FA8FF.toInt()))
                }
            }
            Mode.LOVE -> {
                y = ground
                p.eyes = EyeShape.HAPPY
                p.mouth = MouthShape.GRIN
                p.blush = 1f
                p.tilt = sin(el * 8f) * 6f
                if (el > modeDur) chooseNext(l, left, right)
            }
            Mode.PIN -> {
                // flies to the corner and sits there quietly
                val k = min(1f, dt * 6f)
                x += (pinX - x) * k
                y += (pinY - y) * k
                p.scaleY *= 0.94f
                p.scaleX *= 1.04f
                p.lookX = 0.6f
                p.lookY = 0.4f
                if (l?.sleeping == true) {
                    p.eyes = EyeShape.CLOSED
                } else if (el % 6f < 0.4f) {
                    p.eyes = EyeShape.HAPPY
                }
            }
            Mode.LADDER -> {
                val toys = host.toys()
                // The ladder reaches up to just below the status bar.
                val climb = max(petSize * 1.3f, ground - topY - petSize * 1.45f)
                val up = (2.4f * sqrt(climb / (petSize * 1.3f))).coerceAtMost(7f)
                val top = 0.8f + up
                val wave = top + 2.2f
                val down = wave + up * 0.7f
                modeDur = down + 0.8f
                toys.ladderX = x
                toys.ladderGround = ground
                toys.ladderHeight = climb + petSize * 0.45f
                when {
                    el < 0.8f -> {
                        y = ground
                        toys.ladderExtend = el / 0.8f
                        p.armL = 0.9f
                        p.armR = 0.9f
                        p.lookY = -1f
                    }
                    el < top -> {
                        val k = (el - 0.8f) / up
                        y = ground - climb * k * k * (3f - 2f * k)
                        toys.ladderExtend = 1f
                        val w = sin(el * 10f)
                        p.armL = 0.75f + 0.25f * w
                        p.armR = 0.75f - 0.25f * w
                        p.footL = max(0f, w) * 0.8f
                        p.footR = max(0f, -w) * 0.8f
                        p.mouth = MouthShape.TONGUE
                    }
                    el < wave -> {
                        y = ground - climb
                        if (el - dt < top) {
                            particles.add(Particle(PKind.EMOJI, 0.7f, 0.18f, 0f, -0.05f, 1.2f, 0.12f, 0, text = if (l?.isCat == true) "😺" else "👋"))
                            host.onToy(de.knuffi.core.ToyKind.LADDER)
                        }
                        p.armR = 1f
                        p.armWaveR = sin(el * 14f) * 25f
                        p.eyes = EyeShape.HAPPY
                        p.mouth = MouthShape.OPEN
                    }
                    el < down -> {
                        val k = (el - wave) / (down - wave)
                        y = ground - climb * (1f - k)
                        val w = sin(el * 10f)
                        p.armL = 0.7f + 0.2f * w
                        p.armR = 0.7f - 0.2f * w
                    }
                    else -> {
                        y = ground
                        toys.ladderExtend = (1f - (el - down) / 0.8f).coerceIn(0f, 1f)
                        if (el > modeDur) {
                            toys.ladderExtend = 0f
                            chooseNext(l, left, right)
                        }
                    }
                }
            }
            Mode.PLANE -> {
                y = ground
                when {
                    el < 1.6f -> {
                        p.hold = 1f
                        p.holding = "📄"
                        p.lookY = 0.8f
                        p.mouth = MouthShape.TONGUE
                    }
                    el < 2f -> {
                        p.armR = 1f
                        p.eyes = EyeShape.SQUINT
                    }
                    else -> {
                        if (host.toys().flyer == null && el - dt < 2f) {
                            host.toys().flyer = OverlayToys.Flyer(x + facing * petSize * 0.3f, y - petSize * 0.6f, facing * 240f * density, -520f * density, now)
                            sqV += 4f
                        }
                        p.eyes = EyeShape.HAPPY
                        p.mouth = MouthShape.OPEN
                        host.toys().flyer?.let { f -> p.lookX = ((f.x - x) / petSize).coerceIn(-1f, 1f) }
                        if (el > modeDur) chooseNext(l, left, right)
                    }
                }
            }
            Mode.PAINT -> {
                y = ground
                p.hold = 1f
                p.holding = "🖌️"
                p.mouth = MouthShape.TONGUE
                for (at in floatArrayOf(0.7f, 1.4f, 2.1f)) {
                    if (el >= at && el - dt < at) {
                        val dir = if (rnd.nextBoolean()) 1f else -1f
                        val sx = x + dir * petSize * (0.7f + rnd.nextFloat() * 1.6f)
                        val sy = ground - petSize * (0.3f + rnd.nextFloat() * 2.8f)
                        if (host.addSplash(sx, sy)) {
                            p.armR = 1f
                            sqV += 3f
                        }
                    }
                }
                if (el > modeDur) chooseNext(l, left, right)
            }
            Mode.IDLE -> {
                y = ground
                p.tilt += sin(now * 0.9f) * 2f
                if (l?.isEgg == true) p.eggWobble = sin(now * 1.3f) * 3f + if (el % 3f < 0.3f) sin(el * 40f) * 8f else 0f
                if (el > modeDur) chooseNext(l, left, right)
            }
        }
        if (l != null && l.sleeping && mode != Mode.SLEEP && mode != Mode.DRAG && mode != Mode.FALL) setMode(Mode.SLEEP, 1e9f)

        if (walking) {
            walkPhase += dt * 9f
            val s = sin(walkPhase)
            val sg = if (s >= 0f) 1f else -1f
            if (sg != lastStepSign) {
                lastStepSign = sg
                host.toys().addPrint(x + sg * petSize * 0.12f, ground, sg.toInt(), facing, now, l?.isCat == true, host.toyConfig())
            }
            p.footL = max(0f, s) * 0.9f
            p.footR = max(0f, -s) * 0.9f
            p.strideL = kotlin.math.cos(walkPhase) * 0.8f * facing
            p.strideR = -p.strideL
            p.lift += abs(s) * 0.04f
            p.tilt += s * 3f
            p.armWaveL += s * 16f
            p.armWaveR -= s * 16f
        }
        val turnTarget = if (walking) facing * 0.75f else facing * 0.25f
        turn += (turnTarget - turn) * min(1f, dt * 8f)
        if (mode != Mode.SLEEP) p.turn = turn

        // Jelly spring
        sqV += (-190f * sq - 9f * sqV) * dt
        sq += sqV * dt
        p.scaleY *= 1f - 0.2f * sq
        p.scaleX *= 1f + 0.16f * sq

        // Blink
        if (now > nextBlink) nextBlink = now + 2.5f + rnd.nextFloat() * 3f
        val bt = nextBlink - now
        if (bt < 0.15f && p.eyes != EyeShape.CLOSED) p.eyeOpen *= 1f - sin((0.15f - bt) / 0.15f * Math.PI.toFloat())
    }

    override fun onDraw(canvas: Canvas) {
        val l = look ?: return
        val cx = width / 2f
        if (mode != Mode.DRAG && mode != Mode.FALL) renderer.drawShadow(canvas, cx, footInWindow, petSize, pose, l)
        renderer.draw(canvas, cx, footInWindow, petSize, l, pose)
        particles.draw(canvas, width.toFloat(), height.toFloat())
        val say = sayText?.takeIf { now < sayUntil }
        val text = say ?: if (showName && name.isNotEmpty() && !l.isEgg) name else null
        if (text != null) {
            tagPaint.textSize = (if (say != null) 13f else 11.5f) * density
            val tw = tagPaint.measureText(text)
            val ty = (renderer.head.y - 6f * density).coerceAtLeast(tagPaint.textSize + 4f * density)
            tagRect.set(cx - tw / 2f - 8f * density, ty - tagPaint.textSize - 3f * density, cx + tw / 2f + 8f * density, ty + 5f * density)
            tagBg.color = if (say != null) 0xF2FFFFFF.toInt() else 0xCC2A1F4A.toInt()
            canvas.drawRoundRect(tagRect, 20f * density, 20f * density, tagBg)
            tagPaint.color = if (say != null) 0xFF2A1F4A.toInt() else 0xFFFFFFFF.toInt()
            canvas.drawText(text, cx, ty, tagPaint)
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downRawX = event.rawX
                downRawY = event.rawY
                grabDX = event.rawX - x
                grabDY = event.rawY - y
                dragging = false
                longPressed = false
                lastMoveRawX = event.rawX
                lastMoveRawY = event.rawY
                lastMoveTime = event.eventTime
                dragVX = 0f
                dragVY = 0f
                postDelayed(longPress, ViewConfiguration.getLongPressTimeout().toLong() + 150L)
            }
            MotionEvent.ACTION_MOVE -> {
                if (!dragging && hypot(event.rawX - downRawX, event.rawY - downRawY) > touchSlop) {
                    dragging = true
                    removeCallbacks(longPress)
                    host.toys().ladderExtend = 0f
                    setMode(Mode.DRAG)
                    swing = 0f
                    swingV = 0f
                }
                if (dragging) {
                    val dtMs = (event.eventTime - lastMoveTime).coerceAtLeast(1L)
                    val nvx = (event.rawX - lastMoveRawX) / dtMs * 1000f
                    val nvy = (event.rawY - lastMoveRawY) / dtMs * 1000f
                    dragVX = dragVX * 0.6f + nvx * 0.4f
                    dragVY = dragVY * 0.6f + nvy * 0.4f
                    lastMoveRawX = event.rawX
                    lastMoveRawY = event.rawY
                    lastMoveTime = event.eventTime
                    x = event.rawX - grabDX
                    y = event.rawY - grabDY
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                removeCallbacks(longPress)
                if (dragging) {
                    vx = dragVX.coerceIn(-3000f * density, 3000f * density)
                    vy = dragVY.coerceIn(-3000f * density, 3000f * density)
                    setMode(Mode.FALL)
                } else if (!longPressed && event.actionMasked == MotionEvent.ACTION_UP) {
                    tap()
                }
                dragging = false
            }
        }
        return true
    }

    private fun tap() {
        val l = look ?: return
        sqV += 6f
        val t = android.os.SystemClock.uptimeMillis()
        if (t - lastTapAt < 380L && !l.isEgg) {
            lastTapAt = 0L
            host.onTogglePin()
            return
        }
        lastTapAt = t
        if (!l.isEgg && name.isNotEmpty()) {
            sayText = "Ich bin $name!"
            sayUntil = now + 2f
        }
        if (l.isEgg) {
            particles.burst(PKind.CRUMB, 0.5f, 0.6f, 6, 0.3f, 0.7f, 0.03f, intArrayOf(0xFFFFF4E0.toInt(), 0xFFF0D2A8.toInt()), gravity = 1f, upward = true)
            host.onHatchTap()
            return
        }
        particles.burst(PKind.HEART, 0.5f, 0.35f, 3, 0.25f, 1.2f, 0.07f, ParticleSystem.HEART_COLORS, upward = true)
        if (mode != Mode.SLEEP) setMode(Mode.LOVE, 1.3f)
        host.onStroke()
    }
}
