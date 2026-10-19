package de.knuffi.app.screen

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.view.MotionEvent
import android.view.View
import de.knuffi.app.render.Footprint
import de.knuffi.app.render.Splash
import de.knuffi.app.render.ToyConfig
import de.knuffi.app.render.ToyRenderer
import de.knuffi.core.ToyTuning
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.hypot

/** What the floating pet leaves on the screen, in screen pixels. */
class OverlayToys {
    class Print(val x: Float, val y: Float, val fp: Footprint)

    class Flyer(var x: Float, var y: Float, var vx: Float, var vy: Float, val born: Float) {
        var landed = false
        var landedAt = 0f
        var alpha = 1f
    }

    val prints = ArrayList<Print>()
    var ladderX = 0f
    var ladderGround = 0f
    var ladderHeight = 0f
    var ladderExtend = 0f
    var flyer: Flyer? = null
    private var lastPrint = 0f

    fun addPrint(x: Float, y: Float, side: Int, dir: Float, t: Float, cat: Boolean, cfg: ToyConfig) {
        if (cfg.footprints <= 0 || t - lastPrint < 0.14f) return
        lastPrint = t
        prints += Print(x, y, Footprint(0f, 0f, side, t, dir, cat, false))
        while (prints.size > 80) prints.removeAt(0)
    }

    /** Advances the plane. [ground] is the bottom line of the screen. Returns true while something is visible. */
    fun update(t: Float, dt: Float, density: Float, ground: Float, left: Float, right: Float, cfg: ToyConfig): Boolean {
        val life = ToyTuning.footprintSeconds(cfg.footprints)
        prints.removeAll { t - it.fp.born > life }
        flyer?.let { f ->
            if (!f.landed) {
                f.vy += 600f * density * dt
                if (f.vy > 90f * density) f.vy = 90f * density + (f.vy - 90f * density) * 0.2f
                f.x += f.vx * dt
                f.y += f.vy * dt
                if (f.x < left || f.x > right) {
                    f.vx = -f.vx * 0.8f
                    f.x = f.x.coerceIn(left, right)
                }
                if (f.y >= ground) {
                    f.y = ground
                    f.landed = true
                    f.landedAt = t
                }
            } else if (t - f.landedAt > 6f) {
                f.alpha -= dt
                if (f.alpha <= 0f) flyer = null
            }
        }
        return prints.isNotEmpty() || flyer != null || ladderExtend > 0f
    }
}

/** Full-screen, untouchable layer for footprints, the ladder and the paper plane. */
@SuppressLint("ViewConstructor")
class OverlayDecorView(context: Context, private val toys: OverlayToys, private val cfg: ToyConfig) : View(context) {
    private val art = ToyRenderer()
    private val density = resources.displayMetrics.density
    var now = 0f
    var petSize = 76f * density

    override fun onDraw(canvas: Canvas) {
        val life = ToyTuning.footprintSeconds(cfg.footprints).coerceAtLeast(0.1f)
        for (p in toys.prints) art.footprint(canvas, p.fp, p.x, p.y, petSize * 0.07f, (1f - (now - p.fp.born) / life).coerceIn(0f, 1f))
        if (toys.ladderExtend > 0f) art.ladder(canvas, toys.ladderX, toys.ladderGround, toys.ladderHeight, petSize * 0.5f, toys.ladderExtend)
        toys.flyer?.let { f ->
            val angle = if (f.landed) 0f else Math.toDegrees(atan2(f.vy.toDouble(), abs(f.vx).toDouble())).toFloat()
            art.plane(canvas, f.x, f.y - petSize * 0.08f, petSize * 0.24f, angle, if (f.vx < 0f) -1f else 1f, f.alpha)
        }
    }
}

/** A paint splash in its own small window, so that it can be wiped away with a finger. */
@SuppressLint("ViewConstructor")
class SplashView(context: Context, val splash: Splash, private val onWiped: (SplashView) -> Unit) : View(context) {
    private val art = ToyRenderer()
    private var lastX = 0f
    private var lastY = 0f
    private var done = false
    val sizePx = (96f * resources.displayMetrics.density).toInt()

    override fun onDraw(canvas: Canvas) {
        art.splash(canvas, splash, width / 2f, height / 2f, width * 0.3f)
        if (splash.pop < 1f) {
            splash.pop = (splash.pop + 0.08f).coerceAtMost(1f)
            postInvalidateOnAnimation()
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                lastX = event.x
                lastY = event.y
                wipe(0.12f)
            }
            MotionEvent.ACTION_MOVE -> {
                wipe(hypot(event.x - lastX, event.y - lastY) / width * 0.9f)
                lastX = event.x
                lastY = event.y
            }
        }
        return true
    }

    private fun wipe(amount: Float) {
        if (done) return
        splash.amount -= amount
        invalidate()
        if (splash.amount <= 0f) {
            done = true
            onWiped(this)
        }
    }
}
