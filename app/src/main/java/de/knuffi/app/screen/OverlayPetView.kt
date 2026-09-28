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
    }

    private enum class Mode { IDLE, WALK, WAVE, SIT, SLEEP, DRAG, FALL, LOVE }

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
        update(dt, l, left, right, ground)
        particles.update(now)
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
