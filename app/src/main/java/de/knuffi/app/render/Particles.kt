package de.knuffi.app.render

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.Shader
import android.graphics.Typeface
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin
import kotlin.random.Random

enum class PKind { HEART, STAR, SPARKLE, ZZZ, BUBBLE, CONFETTI, NOTE, EMOJI, DROP, COIN, SMOKE, CRUMB, TEXT, LEAF, SNOT }

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
    val drag: Float = 0f,
) {
    var age = 0f
}

/**
 * Particle system with glossy, soft particles. Positions are normalised to the scene (0..1)
 * so the same particles work in the room, the wallpaper and overlays.
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
    private val outline = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        typeface = Typeface.DEFAULT_BOLD
        style = Paint.Style.STROKE
        strokeJoin = Paint.Join.ROUND
    }
    private val path = Path()

    var typeface: Typeface
        get() = text.typeface
        set(value) {
            text.typeface = value
            outline.typeface = value
        }

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
            if (p.drag > 0f) {
                val k = (1f - p.drag * dt).coerceIn(0f, 1f)
                p.vx *= k
                p.vy *= k
            }
            p.x += p.vx * dt
            p.y += p.vy * dt
            p.rot += p.vr * dt
        }
    }

    fun add(p: Particle) {
        if (items.size < 260) items += p
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
        drag: Float = 0f,
    ) {
        repeat(count) {
            val a = if (upward) (-Math.PI / 2 + (rnd.nextFloat() - 0.5f) * 1.7f) else rnd.nextFloat() * Math.PI * 2
            val sp = speed * (0.45f + rnd.nextFloat() * 0.75f)
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
                    vr = (rnd.nextFloat() - 0.5f) * 540f,
                    gravity = gravity,
                    drag = drag,
                ),
            )
        }
    }

    private fun shader(s: Shader) {
        fill.color = Color.WHITE
        fill.shader = s
    }

    private fun radial(x: Float, y: Float, r: Float, colors: IntArray, stops: FloatArray? = null) =
        RadialGradient(x, y, max(r, 0.5f), colors, stops, Shader.TileMode.CLAMP)

    fun draw(c: Canvas, w: Float, h: Float) {
        for (p in items) {
            val f = p.age / p.life
            val alpha = when {
                f < 0.08f -> f / 0.08f
                f > 0.7f -> (1f - f) / 0.3f
                else -> 1f
            }.coerceIn(0f, 1f)
            val x = p.x * w
            val y = p.y * h
            val pop = if (f < 0.12f) 0.6f + 0.4f * (f / 0.12f) + 0.15f * sin(f / 0.12f * Math.PI.toFloat()) else 1f
            val s = p.size * h * (1f + p.grow * f) * pop
            fill.shader = null
            when (p.kind) {
                PKind.HEART -> {
                    Shapes.heart(path, x, y, s)
                    shader(radial(x - s * 0.3f, y - s * 0.35f, s * 1.3f, intArrayOf(Colors.alpha(Colors.lighten(p.color, 0.55f), alpha), Colors.alpha(p.color, alpha), Colors.alpha(Colors.darken(p.color, 0.25f), alpha)), floatArrayOf(0f, 0.5f, 1f)))
                    c.drawPath(path, fill)
                    fill.shader = null
                    fill.color = Colors.alpha(Color.WHITE, 0.85f * alpha)
                    c.drawCircle(x - s * 0.38f, y - s * 0.3f, s * 0.14f, fill)
                }
                PKind.STAR -> {
                    Shapes.softStar(path, x, y, s, s * 0.5f, 5, p.rot)
                    shader(radial(x - s * 0.25f, y - s * 0.3f, s * 1.3f, intArrayOf(Colors.alpha(0xFFFFFBE0.toInt(), alpha), Colors.alpha(p.color, alpha), Colors.alpha(Colors.darken(p.color, 0.25f), alpha)), floatArrayOf(0f, 0.5f, 1f)))
                    c.drawPath(path, fill)
                    fill.shader = null
                }
                PKind.SPARKLE -> {
                    val tw = 0.75f + 0.25f * sin(f * 14f)
                    shader(radial(x, y, s * 2f, intArrayOf(Colors.alpha(p.color, 0.5f * alpha), Colors.alpha(p.color, 0f))))
                    c.drawCircle(x, y, s * 2f, fill)
                    fill.shader = null
                    Shapes.sparkle(path, x, y, s * tw)
                    fill.color = Colors.alpha(Colors.lighten(p.color, 0.6f), alpha)
                    c.drawPath(path, fill)
                }
                PKind.ZZZ, PKind.NOTE, PKind.TEXT -> {
                    val str = p.text ?: if (p.kind == PKind.ZZZ) "Z" else "♪"
                    text.textSize = max(6f, s)
                    outline.textSize = text.textSize
                    outline.strokeWidth = s * 0.16f
                    outline.color = Colors.alpha(if (p.kind == PKind.TEXT) Colors.darken(p.color, 0.55f) else Color.WHITE, alpha * 0.9f)
                    text.color = Colors.alpha(p.color, alpha)
                    c.save()
                    c.rotate(if (p.kind == PKind.TEXT) 0f else sin(p.age * 3f) * 14f, x, y)
                    c.drawText(str, x, y + s * 0.35f, outline)
                    c.drawText(str, x, y + s * 0.35f, text)
                    c.restore()
                }
                PKind.EMOJI -> {
                    text.textSize = max(6f, s)
                    text.color = Color.BLACK
                    text.alpha = (alpha * 255).toInt()
                    c.save()
                    c.rotate(sin(p.age * 5f) * 8f, x, y)
                    c.drawText(p.text ?: "✨", x, y + s * 0.35f, text)
                    c.restore()
                }
                PKind.BUBBLE -> {
                    shader(radial(x, y, s, intArrayOf(Colors.alpha(Color.WHITE, 0.05f * alpha), Colors.alpha(0xFFBFE8FF.toInt(), 0.18f * alpha), Colors.alpha(0xFFE9C8FF.toInt(), 0.55f * alpha)), floatArrayOf(0f, 0.75f, 1f)))
                    c.drawCircle(x, y, s, fill)
                    fill.shader = null
                    fill.color = Colors.alpha(Color.WHITE, 0.9f * alpha)
                    c.drawOval(x - s * 0.55f, y - s * 0.6f, x - s * 0.15f, y - s * 0.35f, fill)
                }
                PKind.CONFETTI -> {
                    c.save()
                    c.translate(x, y)
                    c.rotate(p.rot)
                    c.scale(abs(cos(p.age * 7f + p.rot)).coerceAtLeast(0.15f), 1f)
                    fill.color = Colors.alpha(p.color, alpha)
                    c.drawRoundRect(-s * 0.5f, -s * 0.28f, s * 0.5f, s * 0.28f, s * 0.1f, s * 0.1f, fill)
                    fill.color = Colors.alpha(Color.WHITE, 0.35f * alpha)
                    c.drawRect(-s * 0.5f, -s * 0.28f, s * 0.5f, -s * 0.12f, fill)
                    c.restore()
                }
                PKind.DROP -> {
                    Shapes.drop(path, x, y, s * 0.5f)
                    shader(radial(x - s * 0.15f, y - s * 0.2f, s * 0.8f, intArrayOf(Colors.alpha(Color.WHITE, alpha), Colors.alpha(p.color, alpha))))
                    c.drawPath(path, fill)
                    fill.shader = null
                }
                PKind.COIN -> {
                    val flip = abs(cos(p.age * 6f)).coerceAtLeast(0.2f)
                    c.save()
                    c.scale(flip, 1f, x, y)
                    shader(radial(x - s * 0.15f, y - s * 0.2f, s * 0.7f, intArrayOf(Colors.alpha(0xFFFFF4C2.toInt(), alpha), Colors.alpha(0xFFFFC83D.toInt(), alpha), Colors.alpha(0xFFD18B00.toInt(), alpha)), floatArrayOf(0f, 0.55f, 1f)))
                    c.drawCircle(x, y, s * 0.5f, fill)
                    fill.shader = null
                    line.color = Colors.alpha(0xFFB87800.toInt(), alpha)
                    line.strokeWidth = s * 0.06f
                    c.drawCircle(x, y, s * 0.34f, line)
                    c.restore()
                }
                PKind.SMOKE -> {
                    val r = s * (0.6f + f * 1.2f)
                    shader(radial(x, y, r, intArrayOf(Colors.alpha(p.color, alpha * Color.alpha(p.color) / 255f), Colors.alpha(p.color, 0f))))
                    c.drawCircle(x, y, r, fill)
                    fill.shader = null
                }
                PKind.CRUMB -> {
                    fill.color = Colors.alpha(p.color, alpha)
                    c.save()
                    c.rotate(p.rot, x, y)
                    c.drawRoundRect(x - s * 0.3f, y - s * 0.25f, x + s * 0.3f, y + s * 0.25f, s * 0.12f, s * 0.12f, fill)
                    c.restore()
                }
                PKind.LEAF -> {
                    c.save()
                    c.translate(x, y)
                    c.rotate(p.rot)
                    fill.color = Colors.alpha(p.color, alpha)
                    c.drawOval(-s * 0.5f, -s * 0.22f, s * 0.5f, s * 0.22f, fill)
                    c.restore()
                }
                PKind.SNOT -> {
                    val r = s * (0.3f + 0.7f * (0.5f + 0.5f * sin(p.age * 2.5f)))
                    shader(radial(x - r * 0.3f, y - r * 0.3f, r * 1.2f, intArrayOf(Colors.alpha(Color.WHITE, 0.6f * alpha), Colors.alpha(0xFFBFE8FF.toInt(), 0.35f * alpha))))
                    c.drawCircle(x, y, r, fill)
                    fill.shader = null
                }
            }
        }
    }

    companion object {
        val CONFETTI_COLORS = intArrayOf(
            0xFFFF6B9A.toInt(), 0xFFFFD166.toInt(), 0xFF6EC1FF.toInt(), 0xFF7DD9B0.toInt(), 0xFFB39DFF.toInt(), 0xFFFF9E6B.toInt(),
        )
        val HEART_COLORS = intArrayOf(0xFFFF5C9A.toInt(), 0xFFFF7FAF.toInt(), 0xFFFF4F6D.toInt())
        val STAR_COLORS = intArrayOf(0xFFFFD166.toInt(), 0xFFFFE680.toInt(), 0xFFFFB84D.toInt())
        val SPARKLE_COLORS = intArrayOf(0xFFFFF3B0.toInt(), 0xFFFFFFFF.toInt(), 0xFFB3E5FF.toInt())
    }
}
