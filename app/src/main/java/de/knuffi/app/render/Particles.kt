package de.knuffi.app.render

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Typeface
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

enum class PKind { HEART, STAR, SPARKLE, ZZZ, BUBBLE, CONFETTI, NOTE, EMOJI, DROP, COIN, SMOKE, CRUMB, TEXT }

class Particle(
    val kind: PKind,
    var x: Float,
    var y: Float,
    var vx: Float,
    var vy: Float,
    val life: Float,
    val size: Float,
    val color: Int,
    val text: String? = null,
    var rot: Float = 0f,
    val vr: Float = 0f,
    val gravity: Float = 0f,
    val grow: Float = 0f,
) {
    var age = 0f
}

/**
 * A tiny particle system. Positions are normalised to the scene (0..1) so the same
 * particles can be drawn at full resolution or into a low resolution pixel buffer.
 */
class ParticleSystem {
    private val items = ArrayList<Particle>()
    private var last = -1f
    val rnd = Random(42)

    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val line = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        typeface = Typeface.DEFAULT_BOLD
    }
    private val path = Path()

    val isEmpty: Boolean get() = items.isEmpty()

    fun clear() = items.clear()

    fun update(t: Float) {
        val dt = if (last < 0f) 0f else (t - last).coerceIn(0f, 0.1f)
        last = t
        if (dt == 0f) return
        val it = items.iterator()
        while (it.hasNext()) {
            val p = it.next()
            p.age += dt
            if (p.age >= p.life) {
                it.remove()
                continue
            }
            p.vy += p.gravity * dt
            p.x += p.vx * dt
            p.y += p.vy * dt
            p.rot += p.vr * dt
        }
    }

    fun add(p: Particle) {
        if (items.size < 220) items += p
    }

    fun burst(
        kind: PKind,
        x: Float,
        y: Float,
        count: Int,
        speed: Float,
        life: Float,
        size: Float,
        colors: IntArray,
        text: String? = null,
        gravity: Float = 0f,
        upward: Boolean = false,
    ) {
        repeat(count) {
            val a = if (upward) (-Math.PI / 2 + (rnd.nextFloat() - 0.5f) * 1.6f) else rnd.nextFloat() * Math.PI * 2
            val sp = speed * (0.5f + rnd.nextFloat() * 0.7f)
            add(
                Particle(
                    kind = kind,
                    x = x + (rnd.nextFloat() - 0.5f) * 0.02f,
                    y = y + (rnd.nextFloat() - 0.5f) * 0.02f,
                    vx = (cos(a) * sp).toFloat(),
                    vy = (sin(a) * sp).toFloat(),
                    life = life * (0.7f + rnd.nextFloat() * 0.5f),
                    size = size * (0.7f + rnd.nextFloat() * 0.6f),
                    color = colors[rnd.nextInt(colors.size)],
                    text = text,
                    rot = rnd.nextFloat() * 360f,
                    vr = (rnd.nextFloat() - 0.5f) * 600f,
                    gravity = gravity,
                ),
            )
        }
    }

    fun draw(c: Canvas, w: Float, h: Float, mode: RenderMode) {
        val aa = mode != RenderMode.PIXEL
        fill.isAntiAlias = aa
        line.isAntiAlias = aa
        text.isAntiAlias = aa
        for (p in items) {
            val f = p.age / p.life
            val alpha = when {
                f < 0.1f -> f / 0.1f
                f > 0.7f -> (1f - f) / 0.3f
                else -> 1f
            }.coerceIn(0f, 1f)
            val x = p.x * w
            val y = p.y * h
            val s = p.size * h * (1f + p.grow * f)
            fill.color = Colors.alpha(p.color, alpha * Color.alpha(p.color) / 255f)
            when (p.kind) {
                PKind.HEART -> {
                    Shapes.heart(path, x, y, s)
                    c.drawPath(path, fill)
                }
                PKind.STAR -> {
                    Shapes.star(path, x, y, s, s * 0.45f, 5, p.rot)
                    c.drawPath(path, fill)
                }
                PKind.SPARKLE -> {
                    Shapes.sparkle(path, x, y, s * (0.7f + 0.3f * sin(f * 12f)))
                    c.drawPath(path, fill)
                }
                PKind.ZZZ, PKind.NOTE, PKind.TEXT -> {
                    text.color = fill.color
                    text.textSize = max(6f, s)
                    c.save()
                    c.rotate(if (p.kind == PKind.TEXT) 0f else sin(p.age * 3f) * 12f, x, y)
                    val str = p.text ?: if (p.kind == PKind.ZZZ) "Z" else "♪"
                    c.drawText(str, x, y + s * 0.35f, text)
                    c.restore()
                }
                PKind.EMOJI -> {
                    text.color = Colors.alpha(Color.BLACK, alpha)
                    text.alpha = (alpha * 255).toInt()
                    text.textSize = max(6f, s)
                    c.drawText(p.text ?: "✨", x, y + s * 0.35f, text)
                }
                PKind.BUBBLE -> {
                    line.color = fill.color
                    line.strokeWidth = max(1f, s * 0.14f)
                    c.drawCircle(x, y, s, line)
                    fill.color = Colors.alpha(Color.WHITE, alpha * 0.8f)
                    c.drawCircle(x - s * 0.35f, y - s * 0.35f, s * 0.22f, fill)
                }
                PKind.CONFETTI -> {
                    c.save()
                    c.rotate(p.rot, x, y)
                    c.drawRect(x - s * 0.5f, y - s * 0.25f, x + s * 0.5f, y + s * 0.25f, fill)
                    c.restore()
                }
                PKind.DROP -> {
                    Shapes.drop(path, x, y, s * 0.5f)
                    c.drawPath(path, fill)
                }
                PKind.COIN -> {
                    fill.color = Colors.alpha(0xFFFFC83D.toInt(), alpha)
                    c.drawCircle(x, y, s * 0.5f, fill)
                    fill.color = Colors.alpha(0xFFFFE9A0.toInt(), alpha)
                    c.drawCircle(x, y, s * 0.3f, fill)
                }
                PKind.SMOKE -> {
                    c.drawCircle(x, y, s * (0.6f + f), fill)
                }
                PKind.CRUMB -> {
                    c.drawRect(x - s * 0.3f, y - s * 0.3f, x + s * 0.3f, y + s * 0.3f, fill)
                }
            }
        }
    }

    companion object {
        val CONFETTI_COLORS = intArrayOf(
            0xFFFF6B9A.toInt(), 0xFFFFD166.toInt(), 0xFF6EC1FF.toInt(), 0xFF7DD9B0.toInt(), 0xFFB39DFF.toInt(), 0xFFFF9E6B.toInt(),
        )
        val HEART_COLORS = intArrayOf(0xFFFF5C9A.toInt(), 0xFFFF8FB8.toInt(), 0xFFFF4F6D.toInt())
        val STAR_COLORS = intArrayOf(0xFFFFD166.toInt(), 0xFFFFE680.toInt(), 0xFFFFB84D.toInt())

        fun clampSpawn(v: Float) = min(0.98f, max(0.02f, v))
    }
}
