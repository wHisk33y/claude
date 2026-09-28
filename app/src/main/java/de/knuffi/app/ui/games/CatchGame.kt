package de.knuffi.app.ui.games

import android.graphics.Canvas as NativeCanvas
import android.graphics.Color as AColor
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.Shader
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import de.knuffi.app.render.Colors
import de.knuffi.app.render.EyeShape
import de.knuffi.app.render.MouthShape
import de.knuffi.app.render.PKind
import de.knuffi.app.render.Particle
import de.knuffi.app.render.ParticleSystem
import de.knuffi.app.render.PetLook
import de.knuffi.app.render.PetPose
import de.knuffi.app.render.PetRenderer
import de.knuffi.app.ui.rememberCanvasTypeface
import de.knuffi.app.ui.rememberFrameTime
import de.knuffi.core.GameState
import kotlinx.coroutines.delay
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

private const val FOOD = 0
private const val STAR = 1
private const val BOMB = 2
private const val MAGNET = 3
private const val SLOW = 4
private const val LIFE = 5
private const val GOLD = 6

private class Drop(var x: Float, var y: Float, val vy: Float, val kind: Int, val emoji: String, var rot: Float, val vr: Float)

private class CatchLogic {
    var score by mutableIntStateOf(0)
    var lives by mutableIntStateOf(3)
    var combo by mutableIntStateOf(0)
    var level by mutableIntStateOf(1)
    var timeLeft by mutableFloatStateOf(DURATION)
    var over by mutableStateOf(false)
    var playerX = 0.5f
    var targetX = 0.5f
    var vx = 0f
    val drops = ArrayList<Drop>()
    var eatAt = -10f
    var hitAt = -10f
    var levelAt = -10f
    var magnetUntil = -1f
    var slowUntil = -1f
    var now = 0f
    private var elapsed = 0f
    private var nextSpawn = 0.5f
    private var nextPower = 8f
    private val rnd = Random(System.nanoTime())

    val multiplier: Int get() = (1 + combo / 5).coerceAtMost(4)

    fun update(t: Float, dt: Float, ps: ParticleSystem, shake: Shake, catchTop: Float, catchBottom: Float, reach: Float) {
        now = t
        if (over) return
        elapsed += dt
        val left = max(0f, DURATION - elapsed)
        if (abs(left - timeLeft) >= 0.1f || left == 0f) timeLeft = left
        if (elapsed >= DURATION) {
            over = true
            return
        }
        val newLevel = 1 + (elapsed / 15f).toInt()
        if (newLevel != level) {
            level = newLevel
            levelAt = t
            ps.add(Particle(PKind.TEXT, 0.5f, 0.4f, 0f, -0.03f, 1.6f, 0.07f, 0xFFFFD166.toInt(), text = "Level $newLevel!"))
            ps.burst(PKind.STAR, 0.5f, 0.4f, 12, 0.5f, 1.3f, 0.025f, ParticleSystem.STAR_COLORS, gravity = 0.3f)
        }

        val old = playerX
        playerX += (targetX - playerX) * min(1f, dt * 14f)
        vx = if (dt > 0f) (playerX - old) / dt else 0f

        val slow = if (t < slowUntil) 0.5f else 1f
        val speedUp = (1f + (level - 1) * 0.28f) * slow
        nextSpawn -= dt
        if (nextSpawn <= 0f) {
            nextSpawn = (0.42f + rnd.nextFloat() * 0.4f) / (1f + (level - 1) * 0.3f)
            val r = rnd.nextFloat()
            val bombChance = 0.12f + level * 0.035f
            val kind = when {
                r < bombChance -> BOMB
                r < bombChance + 0.06f -> STAR
                r < bombChance + 0.08f -> GOLD
                else -> FOOD
            }
            val emoji = when (kind) {
                BOMB -> "💣"
                STAR -> "⭐"
                GOLD -> "🍯"
                else -> GOOD[rnd.nextInt(GOOD.size)]
            }
            drops += Drop(0.08f + rnd.nextFloat() * 0.84f, -0.05f, 0.2f + rnd.nextFloat() * 0.1f, kind, emoji, rnd.nextFloat() * 360f, (rnd.nextFloat() - 0.5f) * 180f)
        }
        nextPower -= dt
        if (nextPower <= 0f) {
            nextPower = 7f + rnd.nextFloat() * 5f
            val kind = listOf(MAGNET, SLOW, LIFE)[rnd.nextInt(3)]
            val emoji = when (kind) {
                MAGNET -> "🧲"
                SLOW -> "⏳"
                else -> "💖"
            }
            drops += Drop(0.1f + rnd.nextFloat() * 0.8f, -0.05f, 0.17f, kind, emoji, 0f, 0f)
        }

        val magnet = t < magnetUntil
        val it = drops.iterator()
        while (it.hasNext()) {
            val d = it.next()
            d.y += d.vy * speedUp * dt
            d.rot += d.vr * dt * slow
            if (magnet && d.kind != BOMB && d.y > 0.2f) {
                d.x += (playerX - d.x) * min(1f, dt * 3f)
            }
            if (d.y in catchTop..catchBottom && abs(d.x - playerX) < reach) {
                it.remove()
                catchDrop(d, t, ps, shake)
            } else if (d.y > 1.02f) {
                it.remove()
                if (d.kind == FOOD || d.kind == GOLD || d.kind == STAR) {
                    if (combo >= 3) ps.add(Particle(PKind.TEXT, d.x, 0.9f, 0f, -0.05f, 0.8f, 0.03f, 0xFFB3A7CC.toInt(), text = "Combo weg"))
                    combo = 0
                }
            }
        }
    }

    private fun catchDrop(d: Drop, t: Float, ps: ParticleSystem, shake: Shake) {
        when (d.kind) {
            BOMB -> {
                lives -= 1
                combo = 0
                hitAt = t
                shake.kick(t, 26f, 0.4f)
                ps.burst(PKind.SMOKE, d.x, d.y, 8, 0.12f, 1.2f, 0.06f, intArrayOf(0xAA6B6B7A.toInt(), 0xAA8A8A99.toInt()), drag = 2f)
                ps.burst(PKind.CONFETTI, d.x, d.y, 26, 0.7f, 0.9f, 0.018f, intArrayOf(0xFFFF6B3D.toInt(), 0xFFFFC23D.toInt(), 0xFFFF3D5A.toInt()), gravity = 0.8f)
                ps.burst(PKind.STAR, d.x, d.y, 6, 0.5f, 0.6f, 0.03f, intArrayOf(0xFFFFE066.toInt()), gravity = 0.5f)
                ps.add(Particle(PKind.TEXT, d.x, d.y - 0.05f, 0f, -0.08f, 1f, 0.045f, 0xFFFF5A6E.toInt(), text = "BOOM! −❤️"))
                if (lives <= 0) over = true
            }
            MAGNET -> {
                magnetUntil = t + 6f
                ps.add(Particle(PKind.TEXT, d.x, d.y - 0.04f, 0f, -0.07f, 1.2f, 0.045f, 0xFFFF7AB6.toInt(), text = "Magnet!"))
                ps.burst(PKind.SPARKLE, d.x, d.y, 12, 0.4f, 1f, 0.02f, ParticleSystem.SPARKLE_COLORS)
            }
            SLOW -> {
                slowUntil = t + 5f
                ps.add(Particle(PKind.TEXT, d.x, d.y - 0.04f, 0f, -0.07f, 1.2f, 0.045f, 0xFF62C0FF.toInt(), text = "Zeitlupe!"))
                ps.burst(PKind.SPARKLE, d.x, d.y, 12, 0.4f, 1f, 0.02f, intArrayOf(0xFFB3E5FF.toInt(), 0xFFFFFFFF.toInt()))
            }
            LIFE -> {
                lives = min(5, lives + 1)
                ps.add(Particle(PKind.TEXT, d.x, d.y - 0.04f, 0f, -0.07f, 1.2f, 0.045f, 0xFFFF5C9A.toInt(), text = "+1 ❤️"))
                ps.burst(PKind.HEART, d.x, d.y, 8, 0.35f, 1.1f, 0.03f, ParticleSystem.HEART_COLORS, upward = true)
            }
            else -> {
                combo += 1
                val base = when (d.kind) {
                    STAR -> 30
                    GOLD -> 50
                    else -> 10
                }
                val gain = base * multiplier
                score += gain
                eatAt = t
                ps.add(Particle(PKind.TEXT, d.x, d.y - 0.04f, 0f, -0.09f, 0.9f, if (gain >= 30) 0.05f else 0.038f, 0xFFFFC23D.toInt(), text = "+$gain"))
                ps.burst(PKind.CRUMB, d.x, d.y, 6, 0.25f, 0.6f, 0.012f, intArrayOf(0xFFFFE9B0.toInt(), 0xFFFFB38A.toInt(), 0xFFFFFFFF.toInt()), gravity = 1.2f, upward = true)
                if (d.kind != FOOD) ps.burst(PKind.STAR, d.x, d.y, 8, 0.4f, 0.9f, 0.022f, ParticleSystem.STAR_COLORS, gravity = 0.4f)
                if (combo > 0 && combo % 5 == 0) {
                    ps.add(Particle(PKind.TEXT, 0.5f, 0.3f, 0f, -0.04f, 1.3f, 0.06f, 0xFFFF7AB6.toInt(), text = "Combo x$multiplier!"))
                    ps.burst(PKind.CONFETTI, 0.5f, 0.3f, 22, 0.6f, 1.6f, 0.016f, ParticleSystem.CONFETTI_COLORS, gravity = 0.5f, upward = true)
                }
            }
        }
    }

    companion object {
        const val DURATION = 45f
        val GOOD = listOf("🍎", "🍙", "🍰", "🍦", "🍣", "🍕", "🍓", "🍪", "🍩", "🍌")
    }
}

/** Draws the meadow, the falling goodies and the pet. */
private class CatchPainter {
    val renderer = PetRenderer()
    val pose = PetPose()
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND }
    private val emoji = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER }
    private val path = Path()
    private var skyKey = -1f
    private var sky: Shader? = null
    private var skyLevel = -1

    private fun skyColors(level: Int): IntArray = when (level) {
        1 -> intArrayOf(0xFF6EC3FF.toInt(), 0xFFB8E4FF.toInt(), 0xFFFFE6F0.toInt())
        2 -> intArrayOf(0xFF7AA8FF.toInt(), 0xFFFFC2D6.toInt(), 0xFFFFE0B8.toInt())
        else -> intArrayOf(0xFF5A4AA8.toInt(), 0xFFE88AB0.toInt(), 0xFFFFC08A.toInt())
    }

    fun drawWorld(c: NativeCanvas, w: Float, h: Float, t: Float, level: Int, groundY: Float) {
        if (skyKey != h || skyLevel != level) {
            skyKey = h
            skyLevel = level
            sky = LinearGradient(0f, 0f, 0f, groundY, skyColors(level), floatArrayOf(0f, 0.6f, 1f), Shader.TileMode.CLAMP)
        }
        fill.color = AColor.WHITE
        fill.shader = sky
        c.drawRect(0f, 0f, w, h, fill)
        fill.shader = null

        // sun
        val sx = w * 0.8f
        val sy = h * (0.12f + (level - 1) * 0.08f).coerceAtMost(0.34f)
        val sr = w * 0.09f
        fill.color = AColor.WHITE
        fill.shader = RadialGradient(sx, sy, sr * 3f, intArrayOf(0x88FFF3B0.toInt(), 0x00FFF3B0), null, Shader.TileMode.CLAMP)
        c.drawCircle(sx, sy, sr * 3f, fill)
        fill.color = AColor.WHITE
        fill.shader = RadialGradient(sx - sr * 0.3f, sy - sr * 0.3f, sr * 1.3f, intArrayOf(0xFFFFFBE0.toInt(), 0xFFFFD34D.toInt(), 0xFFFFA83D.toInt()), floatArrayOf(0f, 0.6f, 1f), Shader.TileMode.CLAMP)
        c.drawCircle(sx, sy, sr, fill)
        fill.shader = null

        // clouds (three depths)
        for (i in 0 until 5) {
            val depth = 0.4f + (i % 3) * 0.3f
            val x = (((i * 0.27f + t * 0.012f * depth) % 1.3f)) * w - w * 0.15f
            val y = h * (0.08f + (i % 3) * 0.07f)
            cloud(c, x, y, w * 0.06f * depth + w * 0.02f, 0.75f + 0.25f * depth)
        }

        // hills
        hill(c, w, h, groundY - h * 0.1f, h * 0.05f, 3.5f, t * 0.02f, 0xFFB6E3C0.toInt(), 0xFF8FCFA6.toInt(), groundY)
        hill(c, w, h, groundY - h * 0.045f, h * 0.03f, 6f, t * 0.04f + 1f, 0xFF8AD69E.toInt(), 0xFF5DB67E.toInt(), groundY)

        // meadow
        fill.color = AColor.WHITE
        fill.shader = LinearGradient(0f, groundY - h * 0.02f, 0f, h, 0xFF7FD08E.toInt(), 0xFF4FA866.toInt(), Shader.TileMode.CLAMP)
        c.drawRect(0f, groundY - h * 0.02f, w, h, fill)
        fill.shader = null
        // grass tufts and flowers
        stroke.strokeWidth = w * 0.006f
        for (i in 0 until 16) {
            val gx = (i + 0.5f) / 16f * w
            val gy = groundY + h * (0.02f + (i % 3) * 0.025f)
            stroke.color = 0xFF3E9A55.toInt()
            val sway = sin(t * 2f + i) * w * 0.004f
            c.drawLine(gx, gy, gx - w * 0.008f + sway, gy - h * 0.018f, stroke)
            c.drawLine(gx, gy, gx + sway, gy - h * 0.024f, stroke)
            c.drawLine(gx, gy, gx + w * 0.008f + sway, gy - h * 0.018f, stroke)
            if (i % 4 == 1) {
                fill.color = if (i % 8 == 1) 0xFFFF8FB8.toInt() else 0xFFFFE066.toInt()
                c.drawCircle(gx + sway, gy - h * 0.028f, w * 0.01f, fill)
                fill.color = 0xFFFFFFFF.toInt()
                c.drawCircle(gx + sway, gy - h * 0.028f, w * 0.004f, fill)
            }
        }
    }

    private fun cloud(c: NativeCanvas, x: Float, y: Float, r: Float, alpha: Float) {
        fill.color = Colors.alpha(0xFFC8DDF0.toInt(), alpha)
        c.drawOval(x - r * 1.7f, y + r * 0.05f, x + r * 1.8f, y + r * 0.95f, fill)
        fill.color = Colors.alpha(AColor.WHITE, alpha)
        c.drawCircle(x, y, r, fill)
        c.drawCircle(x + r * 1.05f, y + r * 0.25f, r * 0.78f, fill)
        c.drawCircle(x - r * 1.0f, y + r * 0.3f, r * 0.72f, fill)
        c.drawOval(x - r * 1.75f, y + r * 0.05f, x + r * 1.75f, y + r * 0.85f, fill)
    }

    private fun hill(c: NativeCanvas, w: Float, h: Float, base: Float, amp: Float, freq: Float, phase: Float, top: Int, bottom: Int, groundY: Float) {
        path.reset()
        path.moveTo(0f, h)
        var x = 0f
        while (x <= w + 10f) {
            path.lineTo(x, base + sin(x / w * freq + phase) * amp)
            x += 10f
        }
        path.lineTo(w, h)
        path.close()
        fill.color = AColor.WHITE
        fill.shader = LinearGradient(0f, base - amp, 0f, groundY, top, bottom, Shader.TileMode.CLAMP)
        c.drawPath(path, fill)
        fill.shader = null
    }

    fun drawDrop(c: NativeCanvas, d: Drop, w: Float, h: Float, size: Float, groundY: Float, t: Float) {
        val x = d.x * w
        val y = d.y * h
        // shadow on the ground grows as it falls
        val k = (d.y / (groundY / h)).coerceIn(0f, 1f)
        fill.color = Colors.alpha(AColor.BLACK, 0.18f * k)
        c.drawOval(x - size * 0.35f * k, groundY - size * 0.08f, x + size * 0.35f * k, groundY + size * 0.08f, fill)

        if (d.kind == MAGNET || d.kind == SLOW || d.kind == LIFE) {
            // glossy bubble
            val r = size * 0.72f
            val bob = sin(t * 4f + d.x * 10f) * size * 0.06f
            val tint = when (d.kind) {
                MAGNET -> 0xFFFF7AB6.toInt()
                SLOW -> 0xFF62C0FF.toInt()
                else -> 0xFFFF5C9A.toInt()
            }
            fill.color = AColor.WHITE
            fill.shader = RadialGradient(x - r * 0.3f, y - r * 0.35f + bob, r * 1.3f, intArrayOf(0xCCFFFFFF.toInt(), Colors.alpha(tint, 0.45f), Colors.alpha(tint, 0.75f)), floatArrayOf(0f, 0.6f, 1f), Shader.TileMode.CLAMP)
            c.drawCircle(x, y + bob, r, fill)
            fill.shader = null
            stroke.strokeWidth = size * 0.05f
            stroke.color = 0xCCFFFFFF.toInt()
            c.drawCircle(x, y + bob, r, stroke)
            emoji.textSize = size * 0.75f
            emoji.alpha = 255
            c.drawText(d.emoji, x, y + bob + size * 0.26f, emoji)
            fill.color = 0xDDFFFFFF.toInt()
            c.drawOval(x - r * 0.55f, y - r * 0.75f + bob, x - r * 0.05f, y - r * 0.45f + bob, fill)
            return
        }
        // soft glow behind goodies
        if (d.kind != BOMB) {
            val glow = if (d.kind == FOOD) 0x55FFFFFF else 0x88FFE680.toInt()
            fill.color = AColor.WHITE
            fill.shader = RadialGradient(x, y, size * 0.9f, intArrayOf(glow, 0x00FFFFFF), null, Shader.TileMode.CLAMP)
            c.drawCircle(x, y, size * 0.9f, fill)
            fill.shader = null
        }
        c.save()
        c.rotate(d.rot, x, y)
        emoji.textSize = size
        emoji.alpha = 255
        c.drawText(d.emoji, x, y + size * 0.35f, emoji)
        c.restore()
        if (d.kind == BOMB) {
            // sparking fuse
            val fx = x + size * 0.3f
            val fy = y - size * 0.45f
            val flick = 0.7f + 0.3f * sin(t * 40f + d.x * 20f)
            fill.color = AColor.WHITE
            fill.shader = RadialGradient(fx, fy, size * 0.3f * flick, intArrayOf(0xFFFFFFFF.toInt(), 0xFFFFE066.toInt(), 0x00FF8A3D), floatArrayOf(0f, 0.35f, 1f), Shader.TileMode.CLAMP)
            c.drawCircle(fx, fy, size * 0.3f * flick, fill)
            fill.shader = null
        }
    }

    fun drawEffects(c: NativeCanvas, w: Float, h: Float, t: Float, logic: CatchLogic, px: Float, groundY: Float, petSize: Float) {
        if (t < logic.magnetUntil) {
            val k = (t * 1.5f) % 1f
            stroke.strokeWidth = petSize * 0.04f
            stroke.color = Colors.alpha(0xFFFF7AB6.toInt(), 0.6f * (1f - k))
            c.drawCircle(px, groundY - petSize * 0.5f, petSize * (0.6f + 0.6f * k), stroke)
        }
        if (t < logic.slowUntil) {
            fill.color = 0x1F62C0FF
            c.drawRect(0f, 0f, w, h, fill)
        }
        val sinceHit = t - logic.hitAt
        if (sinceHit < 0.3f) {
            fill.color = Colors.alpha(0xFFFF4F6D.toInt(), 0.35f * (1f - sinceHit / 0.3f))
            c.drawRect(0f, 0f, w, h, fill)
        }
    }

    fun drawPet(c: NativeCanvas, look: PetLook, logic: CatchLogic, t: Float, px: Float, groundY: Float, petSize: Float, nearestAbove: Drop?) {
        val p = pose.defaults(look, t)
        val moving = logic.vx
        val speed = min(1f, abs(moving) * 3f)
        val ph = t * 14f
        p.turn = (moving * 1.2f).coerceIn(-0.8f, 0.8f)
        if (speed > 0.05f) {
            p.strideL = kotlin.math.cos(ph) * 0.8f * speed
            p.strideR = -p.strideL
            p.footL = max(0f, sin(ph)) * 0.8f * speed
            p.footR = max(0f, -sin(ph)) * 0.8f * speed
            p.tilt = -moving * 12f
        }
        p.armL = 0.9f
        p.armR = 0.9f
        p.eyes = EyeShape.OPEN
        p.mouth = MouthShape.OPEN
        p.mouthOpen = 0.6f
        if (nearestAbove != null) {
            p.lookX = ((nearestAbove.x - logic.playerX) * 6f).coerceIn(-1f, 1f)
            p.lookY = -1f
            if (nearestAbove.kind == BOMB) {
                p.eyes = EyeShape.WIDE
                p.mouth = MouthShape.O
            }
        }
        val sinceEat = t - logic.eatAt
        if (sinceEat < 0.4f) {
            p.mouth = MouthShape.CHEW
            p.mouthOpen = abs(sin(sinceEat * 20f))
            p.eyes = EyeShape.HAPPY
            val sq = sin(sinceEat / 0.4f * Math.PI.toFloat())
            p.scaleY *= 1f - 0.12f * sq
            p.scaleX *= 1f + 0.1f * sq
        }
        if (logic.combo >= 10 && sinceEat < 1f) p.eyes = EyeShape.STAR
        val sinceHit = t - logic.hitAt
        if (sinceHit < 0.9f) {
            p.eyes = EyeShape.DIZZY
            p.mouth = MouthShape.WAVY
            p.tilt = sin(sinceHit * 30f) * 12f
            p.sweat = 1f
        }
        renderer.drawShadow(c, px, groundY, petSize, p, look)
        renderer.draw(c, px, groundY, petSize, look, p)
    }
}

@Composable
fun CatchGame(state: GameState, onFinished: (Int) -> Unit, onBack: () -> Unit) {
    val logic = remember { CatchLogic() }
    val painter = remember { CatchPainter() }
    val ps = remember { ParticleSystem() }
    val shake = remember { Shake() }
    val clock = remember { GameClock() }
    val look = remember { gameLook(state) }
    val time = rememberFrameTime()
    val tf = rememberCanvasTypeface()
    val lastT = remember { floatArrayOf(-1f) }

    LaunchedEffect(tf) { ps.typeface = tf }
    LaunchedEffect(logic.over) {
        if (logic.over) {
            clock.finished = true
            delay(900)
            onFinished(logic.score)
        }
    }

    Box(Modifier.fillMaxSize().clipToBounds()) {
        Canvas(
            Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    awaitEachGesture {
                        val down = awaitFirstDown()
                        logic.targetX = (down.position.x / size.width).coerceIn(0.06f, 0.94f)
                        do {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull()
                            if (change != null) {
                                logic.targetX = (change.position.x / size.width).coerceIn(0.06f, 0.94f)
                                change.consume()
                            }
                        } while (event.changes.any { it.pressed })
                    }
                },
        ) {
            val t = time.floatValue
            val dt = if (lastT[0] < 0f) 0f else (t - lastT[0]).coerceIn(0f, 0.05f)
            lastT[0] = t
            val w = size.width
            val h = size.height
            val groundY = h * 0.9f
            val petSize = min(w * 0.3f, h * 0.17f)
            val catchTop = (groundY - petSize * 1.05f) / h
            val catchBottom = (groundY - petSize * 0.3f) / h
            val reach = petSize * 0.55f / w
            if (clock.running) logic.update(t, dt, ps, shake, catchTop, catchBottom, reach)
            ps.update(t)
            val itemSize = petSize * 0.42f
            val off = shake.offset(t)
            drawIntoCanvas { canvas ->
                val c = canvas.nativeCanvas
                c.save()
                c.translate(off.x, off.y)
                painter.drawWorld(c, w, h, t, logic.level, groundY)
                val px = logic.playerX * w
                var nearest: Drop? = null
                for (d in logic.drops) {
                    painter.drawDrop(c, d, w, h, itemSize, groundY, t)
                    if (d.y < catchBottom && abs(d.x - logic.playerX) < 0.25f && (nearest == null || d.y > nearest.y)) nearest = d
                }
                val l = look
                if (l != null) painter.drawPet(c, l, logic, t, px, groundY, petSize, nearest)
                painter.drawEffects(c, w, h, t, logic, px, groundY, petSize)
                ps.draw(c, w, h)
                c.restore()
            }
        }
        GameHud(
            onBack = onBack,
            score = "${logic.score}",
            lives = logic.lives,
            maxLives = max(3, logic.lives),
            timeFraction = logic.timeLeft / CatchLogic.DURATION,
            timeText = "${logic.timeLeft.toInt()}s",
            combo = logic.multiplier.takeIf { it > 1 } ?: 0,
            modifier = Modifier.align(Alignment.TopCenter),
        )
        if (!clock.running && !clock.finished) {
            CountdownOverlay("Wische, um zu fangen! Meide 💣") { clock.running = true }
        }
    }
}
