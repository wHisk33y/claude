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
import de.knuffi.app.ui.components.rememberHaptic
import de.knuffi.app.ui.rememberCanvasTypeface
import de.knuffi.app.ui.rememberFrameTime
import de.knuffi.core.GameState
import kotlinx.coroutines.delay
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.random.Random

// ====================================================================== space flight

/** Positions in screen widths (x and y), so distances are round. */
private class SpaceRock(var x: Float, var y: Float, val r: Float, val vx: Float, val vy: Float, val emoji: String, var hp: Int, var rot: Float, val vr: Float)

private class SpaceShot(var x: Float, var y: Float, val vy: Float, val enemy: Boolean)

private class SpaceGem(var x: Float, var y: Float)

private class SpaceLogic {
    var score by mutableIntStateOf(0)
    var lives by mutableIntStateOf(3)
    var timeLeft by mutableFloatStateOf(DURATION)
    var over by mutableStateOf(false)
    var shipX = 0.5f
    var targetX = 0.5f
    val rocks = ArrayList<SpaceRock>()
    val shots = ArrayList<SpaceShot>()
    val gems = ArrayList<SpaceGem>()
    var boss = false
    var bossHp = 0
    var bossMax = 1
    var bossX = 0.5f
    var bossY = -0.3f
    var bossHitAt = -10f
    var hitAt = -10f
    var happyAt = -10f
    private var bosses = 0
    private var elapsed = 0f
    private var nextRock = 1f
    private var nextShot = 0f
    private var nextGem = 4f
    private var nextBoss = 28f
    private var bossShot = 2f
    private val rnd = Random(System.nanoTime())

    fun shipY(height: Float) = height - 0.32f

    fun update(t: Float, dt: Float, height: Float, ps: ParticleSystem, shake: Shake) {
        if (over) return
        elapsed += dt
        val left = max(0f, DURATION - elapsed)
        if (abs(left - timeLeft) >= 0.1f || left == 0f) timeLeft = left
        if (left <= 0f) {
            over = true
            return
        }
        shipX += (targetX - shipX) * min(1f, dt * 12f)
        val sy = shipY(height)

        // automatic fire
        nextShot -= dt
        if (nextShot <= 0f) {
            shots += SpaceShot(shipX, sy - 0.1f, -1.7f, false)
            nextShot = 0.24f
        }
        // meteors (fewer while the ufo is here)
        nextRock -= dt
        if (nextRock <= 0f) {
            val big = rnd.nextFloat() < 0.25f
            val r = if (big) 0.075f + rnd.nextFloat() * 0.02f else 0.04f + rnd.nextFloat() * 0.025f
            rocks += SpaceRock(
                0.08f + rnd.nextFloat() * 0.84f, -0.1f, r,
                (rnd.nextFloat() - 0.5f) * 0.08f, 0.28f + elapsed * 0.004f + rnd.nextFloat() * 0.18f,
                if (rnd.nextFloat() < 0.7f) "🪨" else "☄️", if (big) 2 else 1, rnd.nextFloat() * 360f, (rnd.nextFloat() - 0.5f) * 160f,
            )
            nextRock = max(0.38f, 1.05f - elapsed * 0.009f) * (0.7f + rnd.nextFloat() * 0.6f) * (if (boss) 2.2f else 1f)
        }
        nextGem -= dt
        if (nextGem <= 0f) {
            gems += SpaceGem(0.1f + rnd.nextFloat() * 0.8f, -0.1f)
            nextGem = 4f + rnd.nextFloat() * 3f
        }
        // the ufo
        if (!boss && elapsed >= nextBoss) {
            boss = true
            bosses++
            bossMax = 12 + bosses * 4
            bossHp = bossMax
            bossY = -0.3f
            bossShot = 1.5f
        }
        if (boss) {
            bossY += (0.38f - bossY) * min(1f, dt * 1.5f)
            bossX = 0.5f + 0.34f * sin(elapsed * (0.8f + bosses * 0.15f))
            bossShot -= dt
            if (bossShot <= 0f) {
                shots += SpaceShot(bossX, bossY + 0.08f, 0.62f + bosses * 0.06f, true)
                bossShot = max(0.6f, 1.4f - bosses * 0.15f)
            }
        }

        val invulnerable = t - hitAt < 1.3f
        fun hurt() {
            lives -= 1
            hitAt = t
            shake.kick(t, 20f, 0.35f)
            ps.burst(PKind.STAR, shipX, sy / height, 12, 0.4f, 0.7f, 0.022f, ParticleSystem.STAR_COLORS, gravity = 0.4f)
            if (lives <= 0) over = true
        }

        val sIt = shots.iterator()
        while (sIt.hasNext()) {
            val s = sIt.next()
            s.y += s.vy * dt
            if (s.y < -0.15f || s.y > height + 0.1f) {
                sIt.remove()
                continue
            }
            if (s.enemy) {
                if (!invulnerable && hypot(s.x - shipX, s.y - sy) < 0.06f) {
                    sIt.remove()
                    hurt()
                }
                continue
            }
            if (boss && abs(s.x - bossX) < 0.13f && abs(s.y - bossY) < 0.07f) {
                sIt.remove()
                bossHp--
                bossHitAt = t
                score += 2
                ps.burst(PKind.SPARKLE, s.x, s.y / height, 4, 0.2f, 0.3f, 0.012f, ParticleSystem.SPARKLE_COLORS)
                if (bossHp <= 0) {
                    boss = false
                    score += 150
                    happyAt = t
                    nextBoss = elapsed + 28f
                    ps.burst(PKind.CONFETTI, bossX, bossY / height, 50, 0.7f, 1.6f, 0.014f, ParticleSystem.CONFETTI_COLORS, gravity = 0.3f)
                    ps.add(Particle(PKind.TEXT, bossX, bossY / height, 0f, -0.05f, 1.2f, 0.06f, 0xFFFFC83D.toInt(), text = "Ufo besiegt! +150"))
                }
                continue
            }
            var used = false
            for (r in rocks) {
                if (hypot(s.x - r.x, s.y - r.y) < r.r + 0.015f) {
                    r.hp--
                    used = true
                    if (r.hp <= 0) {
                        val pts = if (r.r > 0.07f) 20 else 10
                        score += pts
                        ps.burst(PKind.SMOKE, r.x, r.y / height, 6, 0.15f, 0.5f, 0.03f, intArrayOf(0x88C8B8A8.toInt()), drag = 2f)
                        ps.burst(PKind.SPARKLE, r.x, r.y / height, 6, 0.3f, 0.4f, 0.012f, ParticleSystem.STAR_COLORS)
                        ps.add(Particle(PKind.TEXT, r.x, r.y / height, 0f, -0.06f, 0.6f, 0.035f, 0xFFFFE680.toInt(), text = "+$pts"))
                    }
                    break
                }
            }
            if (used) sIt.remove()
        }
        rocks.removeAll { it.hp <= 0 }

        val rIt = rocks.iterator()
        while (rIt.hasNext()) {
            val r = rIt.next()
            r.x += r.vx * dt
            r.y += r.vy * dt
            r.rot += r.vr * dt
            if (r.y > height + 0.15f) {
                rIt.remove()
                continue
            }
            if (!invulnerable && hypot(r.x - shipX, r.y - sy) < r.r + 0.055f) {
                rIt.remove()
                hurt()
            }
        }
        val gIt = gems.iterator()
        while (gIt.hasNext()) {
            val g = gIt.next()
            g.y += 0.3f * dt
            if (g.y > height + 0.1f) {
                gIt.remove()
                continue
            }
            if (hypot(g.x - shipX, g.y - sy) < 0.09f) {
                gIt.remove()
                score += 25
                happyAt = t
                ps.burst(PKind.STAR, g.x, g.y / height, 10, 0.35f, 0.6f, 0.02f, ParticleSystem.STAR_COLORS)
                ps.add(Particle(PKind.TEXT, g.x, g.y / height - 0.02f, 0f, -0.06f, 0.7f, 0.04f, 0xFFFFC83D.toInt(), text = "+25 ⭐"))
            }
        }
    }

    companion object {
        const val DURATION = 90f
    }
}

private class SpacePainter {
    val renderer = PetRenderer()
    val pose = PetPose()
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val emoji = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER }
    private val path = Path()
    private val starX = FloatArray(70) { Random(it * 31 + 5).nextFloat() }
    private val starY = FloatArray(70) { Random(it * 17 + 3).nextFloat() }
    private var bgH = -1f
    private var bg: Shader? = null

    fun draw(c: NativeCanvas, logic: SpaceLogic, look: PetLook?, t: Float, w: Float, h: Float) {
        val u = w
        if (bgH != h) {
            bgH = h
            bg = LinearGradient(0f, 0f, 0f, h, intArrayOf(0xFF0E0A2A.toInt(), 0xFF2A1A5E.toInt(), 0xFF51307A.toInt()), floatArrayOf(0f, 0.6f, 1f), Shader.TileMode.CLAMP)
        }
        fill.shader = bg
        c.drawRect(0f, 0f, w, h, fill)
        fill.shader = null
        // star field scrolling past
        for (i in starX.indices) {
            val speed = 0.03f + (i % 3) * 0.03f
            val y = ((starY[i] + t * speed) % 1f) * h
            fill.color = Colors.alpha(AColor.WHITE, 0.35f + 0.5f * abs(sin(t * 2f + i)))
            c.drawCircle(starX[i] * w, y, 1.2f + (i % 3) * 0.9f, fill)
        }
        // a planet
        fill.shader = RadialGradient(w * 0.78f, h * 0.2f, w * 0.2f, intArrayOf(0xFFFFB8D8.toInt(), 0xFFB070C8.toInt()), null, Shader.TileMode.CLAMP)
        c.drawCircle(w * 0.84f, h * 0.18f, w * 0.13f, fill)
        fill.shader = null

        for (g in logic.gems) {
            emoji.textSize = u * 0.08f
            c.drawText("⭐", g.x * u, g.y * u + u * 0.028f, emoji)
        }
        for (r in logic.rocks) {
            emoji.textSize = r.r * 2.2f * u
            c.save()
            c.rotate(r.rot, r.x * u, r.y * u)
            c.drawText(r.emoji, r.x * u, r.y * u + r.r * 0.75f * u, emoji)
            c.restore()
        }
        for (s in logic.shots) {
            fill.color = if (s.enemy) 0xFFFF5AA8.toInt() else 0xFFFFE066.toInt()
            if (s.enemy) {
                c.drawCircle(s.x * u, s.y * u, u * 0.018f, fill)
            } else {
                c.drawRoundRect(s.x * u - u * 0.007f, s.y * u - u * 0.028f, s.x * u + u * 0.007f, s.y * u + u * 0.028f, u * 0.007f, u * 0.007f, fill)
            }
        }
        if (logic.boss || logic.bossY > -0.25f && logic.bossHp > 0) {
            val bx = logic.bossX * u
            val by = logic.bossY * u
            val flash = t - logic.bossHitAt < 0.08f
            emoji.textSize = u * 0.24f
            emoji.alpha = if (flash) 150 else 255
            c.drawText("🛸", bx, by + u * 0.08f, emoji)
            emoji.alpha = 255
            // health bar
            val bw = u * 0.3f
            fill.color = 0x66000000
            c.drawRoundRect(bx - bw / 2f, by - u * 0.12f, bx + bw / 2f, by - u * 0.1f, u * 0.01f, u * 0.01f, fill)
            fill.color = 0xFFFF5A6E.toInt()
            c.drawRoundRect(bx - bw / 2f, by - u * 0.12f, bx - bw / 2f + bw * (logic.bossHp.coerceAtLeast(0) / logic.bossMax.toFloat()), by - u * 0.1f, u * 0.01f, u * 0.01f, fill)
        }
        drawShip(c, logic, look, t, u, logic.shipY(h / w) * u)
    }

    private fun drawShip(c: NativeCanvas, logic: SpaceLogic, look: PetLook?, t: Float, u: Float, sy: Float) {
        val sinceHit = t - logic.hitAt
        if (sinceHit < 1.3f && ((sinceHit * 10f).toInt() % 2 == 1)) return
        val x = logic.shipX * u
        val s = u * 0.11f
        val tiltDeg = ((logic.targetX - logic.shipX) * 120f).coerceIn(-18f, 18f)
        c.save()
        c.rotate(tiltDeg, x, sy)
        // flame
        val fl = 0.8f + 0.3f * sin(t * 40f)
        fill.shader = RadialGradient(x, sy + s * 1.05f, s * 0.6f * fl, intArrayOf(0xFFFFF3B0.toInt(), 0xFFFF9E3D.toInt(), 0x00FF5A3D), floatArrayOf(0f, 0.5f, 1f), Shader.TileMode.CLAMP)
        c.drawOval(x - s * 0.32f, sy + s * 0.6f, x + s * 0.32f, sy + s * (1.1f + 0.5f * fl), fill)
        fill.shader = null
        // fins
        fill.color = 0xFFFF5A8A.toInt()
        path.reset()
        path.moveTo(x - s * 0.45f, sy + s * 0.1f)
        path.lineTo(x - s * 0.85f, sy + s * 0.85f)
        path.lineTo(x - s * 0.3f, sy + s * 0.7f)
        path.close()
        path.moveTo(x + s * 0.45f, sy + s * 0.1f)
        path.lineTo(x + s * 0.85f, sy + s * 0.85f)
        path.lineTo(x + s * 0.3f, sy + s * 0.7f)
        path.close()
        c.drawPath(path, fill)
        // body
        fill.shader = LinearGradient(x - s * 0.5f, 0f, x + s * 0.5f, 0f, intArrayOf(0xFFFFFFFF.toInt(), 0xFFE6E0F0.toInt(), 0xFFB8AECF.toInt()), floatArrayOf(0f, 0.55f, 1f), Shader.TileMode.CLAMP)
        path.reset()
        path.moveTo(x, sy - s * 1.25f)
        path.cubicTo(x + s * 0.62f, sy - s * 0.8f, x + s * 0.55f, sy + s * 0.4f, x + s * 0.36f, sy + s * 0.75f)
        path.lineTo(x - s * 0.36f, sy + s * 0.75f)
        path.cubicTo(x - s * 0.55f, sy + s * 0.4f, x - s * 0.62f, sy - s * 0.8f, x, sy - s * 1.25f)
        path.close()
        c.drawPath(path, fill)
        fill.shader = null
        // the pilot in the glass dome
        val dr = s * 0.36f
        val dy = sy - s * 0.25f
        fill.color = 0xFF9FD8FF.toInt()
        c.drawCircle(x, dy, dr, fill)
        if (look != null) {
            c.save()
            path.reset()
            path.addCircle(x, dy, dr * 0.92f, Path.Direction.CW)
            c.clipPath(path)
            val p = pose.defaults(look, t)
            p.eyes = when {
                t - logic.hitAt < 0.8f -> EyeShape.DIZZY
                t - logic.happyAt < 0.8f -> EyeShape.STAR
                else -> EyeShape.OPEN
            }
            p.mouth = if (t - logic.hitAt < 0.8f) MouthShape.WAVY else MouthShape.SMILE
            p.lookY = -0.6f
            renderer.draw(c, x, dy + dr * 1.25f, dr * 2.1f, look, p)
            c.restore()
        }
        fill.color = 0x66FFFFFF
        c.drawOval(x - dr * 0.6f, dy - dr * 0.75f, x - dr * 0.05f, dy - dr * 0.35f, fill)
        c.restore()
    }
}

@Composable
fun SpaceGame(state: GameState, onFinished: (Int) -> Unit, onBack: () -> Unit) {
    val logic = remember { SpaceLogic() }
    val painter = remember { SpacePainter() }
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
                        logic.targetX = (down.position.x / size.width).coerceIn(0.07f, 0.93f)
                        while (true) {
                            val e = awaitPointerEvent()
                            val ch = e.changes.firstOrNull { it.id == down.id } ?: break
                            if (!ch.pressed) break
                            logic.targetX = (ch.position.x / size.width).coerceIn(0.07f, 0.93f)
                            ch.consume()
                        }
                    }
                },
        ) {
            val t = time.floatValue
            val dt = if (lastT[0] < 0f) 0f else (t - lastT[0]).coerceIn(0f, 0.05f)
            lastT[0] = t
            val w = size.width
            val h = size.height
            if (clock.running) logic.update(t, dt, h / w, ps, shake)
            ps.update(t)
            val off = shake.offset(t)
            drawIntoCanvas { canvas ->
                val c = canvas.nativeCanvas
                c.save()
                c.translate(off.x, off.y)
                painter.draw(c, logic, look, t, w, h)
                ps.draw(c, w, h)
                c.restore()
            }
        }
        GameHud(
            onBack = onBack,
            score = "${logic.score}",
            lives = logic.lives,
            timeFraction = logic.timeLeft / SpaceLogic.DURATION,
            timeText = "${logic.timeLeft.toInt()}s",
            modifier = Modifier.align(Alignment.TopCenter),
        )
        if (!clock.running && !clock.finished) {
            CountdownOverlay("Halte und wische, um die Rakete zu steuern!") { clock.running = true }
        }
    }
}

// ====================================================================== cloud jumper

/** kind: 0 normal, 1 moving, 2 breaks after one jump, 3 trampoline. */
private class JumpCloud(var x: Float, val y: Float, val w: Float, val kind: Int, var vx: Float, var coin: Boolean) {
    var broken = false
    var fall = 0f
}

private class JumperLogic {
    var meters by mutableIntStateOf(0)
    var coins by mutableIntStateOf(0)
    var over by mutableStateOf(false)
    /** x in screen widths, y in screen heights (upwards). */
    var x = 0.5f
    var y = 0.08f
    var vy = JUMP
    var vx = 0f
    var steer = 0f
    var camera = 0f
    var landAt = -10f
    var springAt = -10f
    var coinAt = -10f
    var fellAt = -1f
    val clouds = ArrayList<JumpCloud>()
    private var topY = 0f
    private val rnd = Random(System.nanoTime())

    val score: Int get() = meters + coins * COIN_METERS

    init {
        clouds += JumpCloud(0.5f, 0.05f, 1.2f, 0, 0f, false)
        topY = 0.05f
        spawn()
    }

    private fun spawn() {
        while (topY < camera + 1.4f) {
            val hard = min(1f, camera / 25f)
            topY += 0.11f + rnd.nextFloat() * (0.07f + 0.08f * hard)
            val roll = rnd.nextFloat()
            val kind = when {
                roll < 0.07f -> 3
                roll < 0.07f + 0.1f + 0.12f * hard -> 2
                roll < 0.07f + 0.22f + 0.3f * hard -> 1
                else -> 0
            }
            val cw = 0.24f - 0.06f * hard + rnd.nextFloat() * 0.06f
            val cx = cw / 2f + rnd.nextFloat() * (1f - cw)
            clouds += JumpCloud(cx, topY, cw, kind, if (kind == 1) (if (rnd.nextBoolean()) 0.18f else -0.18f) * (1f + hard) else 0f, kind != 2 && rnd.nextFloat() < 0.3f)
            // a fragile cloud never stands alone: add a safe one right next to it
            if (kind == 2) {
                topY += 0.05f
                val sx = (cx + 0.5f) % 1f
                clouds += JumpCloud(sx.coerceIn(cw / 2f, 1f - cw / 2f), topY, cw, 0, 0f, false)
            }
        }
    }

    fun update(t: Float, dt: Float, ps: ParticleSystem, aspect: Float) {
        if (over) return
        if (fellAt >= 0f) {
            y += vy * dt
            vy -= GRAVITY * dt
            if (t - fellAt > 0.8f) over = true
            return
        }
        vx += (steer * 0.95f - vx) * min(1f, dt * 9f)
        x += vx * dt
        if (x < 0f) x += 1f
        if (x > 1f) x -= 1f
        val prevY = y
        vy -= GRAVITY * dt
        y += vy * dt
        for (cl in clouds) {
            if (cl.kind == 1) {
                cl.x += cl.vx * dt
                if (cl.x < cl.w / 2f || cl.x > 1f - cl.w / 2f) {
                    cl.vx = -cl.vx
                    cl.x = cl.x.coerceIn(cl.w / 2f, 1f - cl.w / 2f)
                }
            }
            if (cl.broken) {
                cl.fall += dt
                continue
            }
            if (vy < 0f && prevY >= cl.y && y <= cl.y && abs(x - cl.x) < cl.w / 2f + 0.03f) {
                y = cl.y
                landAt = t
                if (cl.kind == 3) {
                    vy = SPRING
                    springAt = t
                    ps.burst(PKind.SPARKLE, x, 0.5f, 10, 0.3f, 0.6f, 0.016f, ParticleSystem.SPARKLE_COLORS)
                } else {
                    vy = JUMP
                }
                if (cl.kind == 2) cl.broken = true
            }
            if (cl.coin && abs(x - cl.x) < 0.08f && abs((y + 0.04f) - (cl.y + 0.07f)) < 0.06f) {
                cl.coin = false
                coins++
                coinAt = t
                val sy = 1f - (cl.y + 0.07f - camera)
                ps.burst(PKind.SPARKLE, cl.x, sy, 6, 0.25f, 0.5f, 0.015f, ParticleSystem.STAR_COLORS)
                ps.add(Particle(PKind.TEXT, cl.x, sy - 0.03f, 0f, -0.08f, 0.7f, 0.035f, 0xFFFFC23D.toInt(), text = "+$COIN_METERS"))
            }
        }
        if (y - camera > 0.55f) camera = y - 0.55f
        val m = (camera * METERS_PER_SCREEN).toInt()
        if (m != meters) meters = m
        clouds.removeAll { it.y < camera - 0.15f || it.fall > 1.5f }
        spawn()
        if (y < camera - 0.06f) {
            fellAt = t
            vy = 0f
        }
    }

    companion object {
        const val GRAVITY = 2.4f
        const val JUMP = 1.25f
        const val SPRING = 1.95f
        const val METERS_PER_SCREEN = 30f
        const val COIN_METERS = 5
    }
}

private class JumperPainter {
    val renderer = PetRenderer()
    val pose = PetPose()
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val emoji = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER }

    fun draw(c: NativeCanvas, logic: JumperLogic, look: PetLook?, t: Float, w: Float, h: Float) {
        // the sky gets darker the higher we climb
        val k = (logic.camera / 30f).coerceIn(0f, 1f)
        val top = Colors.lerp(0xFF7EC8FF.toInt(), 0xFF1E1650.toInt(), k)
        val bottom = Colors.lerp(0xFFE6F6FF.toInt(), 0xFF6A4AA8.toInt(), k)
        fill.shader = LinearGradient(0f, 0f, 0f, h, top, bottom, Shader.TileMode.CLAMP)
        c.drawRect(0f, 0f, w, h, fill)
        fill.shader = null
        if (k > 0.2f) {
            for (i in 0 until 40) {
                val sx = Random(i * 13 + 1).nextFloat() * w
                val sy = ((Random(i * 7 + 2).nextFloat() + logic.camera * 0.1f) % 1f) * h
                fill.color = Colors.alpha(AColor.WHITE, (k - 0.2f) * (0.4f + 0.5f * abs(sin(t + i))))
                c.drawCircle(sx, sy, 1.5f + (i % 3), fill)
            }
        }
        // far clouds for depth
        for (i in 0 until 5) {
            val cx = ((i * 0.27f + t * 0.01f) % 1.2f) * w - w * 0.1f
            val cy = ((i * 0.23f + logic.camera * 0.3f) % 1f) * h
            fill.color = Colors.alpha(AColor.WHITE, 0.25f)
            c.drawCircle(cx, cy, w * 0.07f, fill)
            c.drawCircle(cx + w * 0.07f, cy + w * 0.02f, w * 0.05f, fill)
        }
        val cs = w
        for (cl in logic.clouds) {
            val sy = (1f - (cl.y - logic.camera)) * h + cl.fall * cl.fall * h * 0.8f
            if (sy < -40f || sy > h + 60f) continue
            val cx = cl.x * w
            val cw = cl.w * cs
            val ch = cs * 0.05f
            val alpha = if (cl.broken) (1f - cl.fall / 1.5f).coerceIn(0f, 1f) else 1f
            val base = when (cl.kind) {
                1 -> 0xFFDDF1FF.toInt()
                2 -> 0xFFC8C2D8.toInt()
                3 -> 0xFFFFE0F0.toInt()
                else -> 0xFFFFFFFF.toInt()
            }
            fill.color = Colors.alpha(0xFF6A5A90.toInt(), 0.18f * alpha)
            c.drawOval(cx - cw / 2f, sy + ch * 0.1f, cx + cw / 2f, sy + ch * 0.9f, fill)
            fill.color = Colors.alpha(base, alpha)
            c.drawOval(cx - cw / 2f, sy - ch * 0.5f, cx + cw / 2f, sy + ch * 0.6f, fill)
            c.drawCircle(cx - cw * 0.2f, sy - ch * 0.5f, ch * 0.75f, fill)
            c.drawCircle(cx + cw * 0.12f, sy - ch * 0.7f, ch * 0.95f, fill)
            if (cl.kind == 2) {
                stroke.color = Colors.alpha(0xFF8A80A0.toInt(), alpha)
                stroke.strokeWidth = ch * 0.12f
                c.drawLine(cx - cw * 0.05f, sy - ch * 0.6f, cx + cw * 0.04f, sy, stroke)
                c.drawLine(cx + cw * 0.04f, sy, cx - cw * 0.03f, sy + ch * 0.4f, stroke)
            }
            if (cl.kind == 3) {
                fill.color = 0xFFFF6FA8.toInt()
                c.drawRoundRect(cx - cw * 0.18f, sy - ch * 1.1f, cx + cw * 0.18f, sy - ch * 0.75f, ch * 0.2f, ch * 0.2f, fill)
                stroke.color = 0xFFB0408A.toInt()
                stroke.strokeWidth = ch * 0.12f
                for (z in 0 until 3) {
                    val zx = cx - cw * 0.12f + z * cw * 0.12f
                    c.drawLine(zx, sy - ch * 0.75f, zx + cw * 0.04f, sy - ch * 0.4f, stroke)
                }
            }
            if (cl.coin) {
                val coinY = sy - cs * 0.07f
                val r = cs * 0.025f
                val squeeze = abs(sin(t * 4f + cl.x * 20f)).coerceAtLeast(0.2f)
                fill.color = 0xFFFFC83D.toInt()
                c.drawOval(cx - r * squeeze, coinY - r, cx + r * squeeze, coinY + r, fill)
                fill.color = 0xAAFFFFFF.toInt()
                c.drawOval(cx - r * 0.5f * squeeze, coinY - r * 0.7f, cx, coinY - r * 0.2f, fill)
            }
        }
        if (look != null) {
            val size = min(w * 0.17f, h * 0.1f)
            val sy = (1f - (logic.y - logic.camera)) * h
            val p = pose.defaults(look, t)
            val up = logic.vy > 0f
            p.armL = if (up) 1f else 0.6f
            p.armR = if (up) 1f else 0.6f
            p.armWaveL = if (up) 10f else -20f
            p.armWaveR = if (up) 10f else -20f
            p.strideL = if (up) 0.4f else -0.3f
            p.strideR = -p.strideL
            p.eyes = when {
                logic.fellAt >= 0f -> EyeShape.DIZZY
                t - logic.springAt < 0.8f || t - logic.coinAt < 0.3f -> EyeShape.STAR
                up -> EyeShape.HAPPY
                else -> EyeShape.OPEN
            }
            p.mouth = if (logic.fellAt >= 0f) MouthShape.O else if (up) MouthShape.OPEN else MouthShape.SMILE
            p.turn = (logic.vx * 1.5f).coerceIn(-0.8f, 0.8f)
            if (t - logic.springAt < 0.7f) p.tilt = ((t - logic.springAt) / 0.7f) * 360f
            val sinceLand = t - logic.landAt
            if (sinceLand < 0.15f) {
                val q = 1f - sinceLand / 0.15f
                p.scaleY *= 1f - 0.2f * q
                p.scaleX *= 1f + 0.15f * q
            }
            val px = logic.x * w
            renderer.draw(c, px, sy, size, look, p)
            // wrap around the edges
            if (px < size) renderer.draw(c, px + w, sy, size, look, p)
            if (px > w - size) renderer.draw(c, px - w, sy, size, look, p)
        }
    }
}

@Composable
fun JumperGame(state: GameState, onFinished: (Int) -> Unit, onBack: () -> Unit) {
    val logic = remember { JumperLogic() }
    val painter = remember { JumperPainter() }
    val ps = remember { ParticleSystem() }
    val clock = remember { GameClock() }
    val look = remember { gameLook(state) }
    val time = rememberFrameTime()
    val tf = rememberCanvasTypeface()
    val lastT = remember { floatArrayOf(-1f) }

    LaunchedEffect(tf) { ps.typeface = tf }
    LaunchedEffect(logic.over) {
        if (logic.over) {
            clock.finished = true
            delay(700)
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
                        logic.steer = if (down.position.x < size.width / 2f) -1f else 1f
                        while (true) {
                            val e = awaitPointerEvent()
                            val ch = e.changes.firstOrNull { it.id == down.id } ?: break
                            if (!ch.pressed) break
                            logic.steer = if (ch.position.x < size.width / 2f) -1f else 1f
                        }
                        logic.steer = 0f
                    }
                },
        ) {
            val t = time.floatValue
            val dt = if (lastT[0] < 0f) 0f else (t - lastT[0]).coerceIn(0f, 0.05f)
            lastT[0] = t
            val w = size.width
            val h = size.height
            if (clock.running) logic.update(t, dt, ps, h / w)
            ps.update(t)
            drawIntoCanvas { canvas ->
                val c = canvas.nativeCanvas
                painter.draw(c, logic, look, t, w, h)
                ps.draw(c, w, h)
            }
        }
        GameHud(
            onBack = onBack,
            score = "${logic.score} m",
            modifier = Modifier.align(Alignment.TopCenter),
        )
        if (!clock.running && !clock.finished) {
            CountdownOverlay("Halte links oder rechts gedrückt zum Lenken!") { clock.running = true }
        }
    }
}

// ====================================================================== kart race

/** kind: 0 obstacle, 1 coin, 2 boost. [z] goes from 0 (horizon) to 1 (at the kart). */
private class RaceThing(val lane: Int, var z: Float, val kind: Int, val emoji: String) {
    var done = false
}

private class RaceLogic {
    var meters by mutableIntStateOf(0)
    var coins by mutableIntStateOf(0)
    var lives by mutableIntStateOf(3)
    var over by mutableStateOf(false)
    var lane = 1
    var laneX = 1f
    var speed = START
    var distance = 0f
    var hitAt = -10f
    var boostUntil = -10f
    var coinAt = -10f
    val things = ArrayList<RaceThing>()
    private var elapsed = 0f
    private var nextRow = 0.4f
    private val rnd = Random(System.nanoTime())

    val score: Int get() = meters + coins * COIN_METERS

    fun steer(dir: Int) {
        if (over) return
        lane = (lane + dir).coerceIn(0, 2)
    }

    fun update(t: Float, dt: Float, ps: ParticleSystem, shake: Shake, kartY: Float, laneNx: (Float) -> Float) {
        if (over) return
        elapsed += dt
        laneX += (lane - laneX) * min(1f, dt * 12f)
        val boosting = t < boostUntil
        val hurt = t - hitAt < 0.7f
        speed = (START + elapsed * 0.006f) * (if (boosting) 1.6f else 1f) * (if (hurt) 0.6f else 1f)
        val dz = speed * dt
        distance += dz
        val m = (distance * METERS_PER_Z).toInt()
        if (m != meters) meters = m

        nextRow -= dz
        if (nextRow <= 0f) {
            val blocked = if (rnd.nextFloat() < min(0.45f, 0.1f + elapsed * 0.004f)) 2 else 1
            val lanes = (0..2).shuffled(rnd)
            for (i in 0 until blocked) things += RaceThing(lanes[i], 0f, 0, OBSTACLES[rnd.nextInt(OBSTACLES.size)])
            val free = lanes[2]
            when {
                rnd.nextFloat() < 0.12f -> things += RaceThing(free, 0f, 2, "⚡")
                rnd.nextFloat() < 0.6f -> for (k in 0 until 3) things += RaceThing(free, -k * 0.07f, 1, "🪙")
            }
            nextRow = 0.42f + rnd.nextFloat() * 0.3f
        }
        val current = laneX.roundToInt()
        val it = things.iterator()
        while (it.hasNext()) {
            val th = it.next()
            th.z += dz
            if (th.z > 1.15f) {
                it.remove()
                continue
            }
            if (!th.done && th.z in 0.93f..1.03f && th.lane == current) {
                th.done = true
                val nx = laneNx(th.lane.toFloat())
                when (th.kind) {
                    0 -> if (!hurt && t - hitAt > 1.2f) {
                        lives -= 1
                        hitAt = t
                        shake.kick(t, 22f, 0.35f)
                        ps.burst(PKind.STAR, nx, kartY, 10, 0.4f, 0.7f, 0.025f, ParticleSystem.STAR_COLORS, gravity = 0.6f)
                        ps.add(Particle(PKind.TEXT, nx, kartY - 0.08f, 0f, -0.07f, 1f, 0.045f, 0xFFFF5A6E.toInt(), text = "Rumms! −❤️"))
                        if (lives <= 0) over = true
                    }
                    1 -> {
                        coins++
                        coinAt = t
                        ps.burst(PKind.SPARKLE, nx, kartY - 0.04f, 5, 0.25f, 0.5f, 0.015f, ParticleSystem.STAR_COLORS)
                        it.remove()
                    }
                    else -> {
                        boostUntil = t + 2.2f
                        ps.burst(PKind.SPARKLE, nx, kartY, 14, 0.5f, 0.6f, 0.018f, ParticleSystem.SPARKLE_COLORS)
                        ps.add(Particle(PKind.TEXT, nx, kartY - 0.08f, 0f, -0.07f, 0.9f, 0.045f, 0xFF3BB2FF.toInt(), text = "Turbo! ⚡"))
                        it.remove()
                    }
                }
            }
        }
    }

    companion object {
        const val START = 0.62f
        const val METERS_PER_Z = 11f
        const val COIN_METERS = 10
        val OBSTACLES = listOf("🚧", "🪨", "🛢️", "📦", "🦆")
    }
}

private class RacePainter {
    val renderer = PetRenderer()
    val pose = PetPose()
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val emoji = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER }
    private val path = Path()

    fun horizon(h: Float) = h * 0.34f
    fun ground(h: Float) = h * 0.86f
    fun halfWidth(w: Float, z: Float) = w * (0.04f + 0.5f * z * z)
    fun yAt(h: Float, z: Float) = horizon(h) + (ground(h) - horizon(h)) * z * z

    /** Screen x of a lane (0..2, may be fractional) at depth [z]. */
    fun laneX(w: Float, lane: Float, z: Float) = w / 2f + (lane - 1f) * 0.62f * halfWidth(w, z)

    fun draw(c: NativeCanvas, logic: RaceLogic, look: PetLook?, t: Float, w: Float, h: Float) {
        val hy = horizon(h)
        // sky and hills
        fill.shader = LinearGradient(0f, 0f, 0f, hy, 0xFF7EC8FF.toInt(), 0xFFFFE6C8.toInt(), Shader.TileMode.CLAMP)
        c.drawRect(0f, 0f, w, hy + 2f, fill)
        fill.shader = null
        fill.color = 0xFFA8D88A.toInt()
        c.drawOval(-w * 0.2f, hy - h * 0.06f, w * 0.6f, hy + h * 0.06f, fill)
        fill.color = 0xFF94CC78.toInt()
        c.drawOval(w * 0.35f, hy - h * 0.08f, w * 1.3f, hy + h * 0.06f, fill)
        // grass
        fill.shader = LinearGradient(0f, hy, 0f, h, 0xFF8ED06A.toInt(), 0xFF5AA048.toInt(), Shader.TileMode.CLAMP)
        c.drawRect(0f, hy, w, h, fill)
        fill.shader = null
        // grass stripes moving towards us
        for (i in 0 until 8) {
            val z0 = ((i / 8f + logic.distance * 0.6f) % 1f)
            val z1 = z0 + 0.06f
            fill.color = 0x2A000000
            c.drawRect(0f, yAt(h, z0), w, yAt(h, min(1.1f, z1)), fill)
        }
        // road
        path.reset()
        path.moveTo(w / 2f - halfWidth(w, 0f), hy)
        path.lineTo(w / 2f + halfWidth(w, 0f), hy)
        path.lineTo(w / 2f + halfWidth(w, 1.15f), yAt(h, 1.15f))
        path.lineTo(w / 2f - halfWidth(w, 1.15f), yAt(h, 1.15f))
        path.close()
        fill.shader = LinearGradient(0f, hy, 0f, h, 0xFF9C94B0.toInt(), 0xFF6E6684.toInt(), Shader.TileMode.CLAMP)
        c.drawPath(path, fill)
        fill.shader = null
        // kerbs and lane dashes
        for (i in 0 until 10) {
            val z0 = ((i / 10f + logic.distance) % 1f)
            val z1 = min(1.15f, z0 + 0.05f)
            val y0 = yAt(h, z0)
            val y1 = yAt(h, z1)
            for (side in listOf(-1f, 1f)) {
                fill.color = if (i % 2 == 0) 0xFFFF5A6E.toInt() else AColor.WHITE
                path.reset()
                path.moveTo(w / 2f + side * halfWidth(w, z0), y0)
                path.lineTo(w / 2f + side * halfWidth(w, z0) * 1.08f, y0)
                path.lineTo(w / 2f + side * halfWidth(w, z1) * 1.08f, y1)
                path.lineTo(w / 2f + side * halfWidth(w, z1), y1)
                path.close()
                c.drawPath(path, fill)
                if (i % 2 == 0) {
                    fill.color = 0xCCFFFFFF.toInt()
                    val lx0 = w / 2f + side * 0.31f * halfWidth(w, z0)
                    val lx1 = w / 2f + side * 0.31f * halfWidth(w, z1)
                    val lw0 = halfWidth(w, z0) * 0.02f
                    val lw1 = halfWidth(w, z1) * 0.02f
                    path.reset()
                    path.moveTo(lx0 - lw0, y0)
                    path.lineTo(lx0 + lw0, y0)
                    path.lineTo(lx1 + lw1, y1)
                    path.lineTo(lx1 - lw1, y1)
                    path.close()
                    c.drawPath(path, fill)
                }
            }
        }
        // things, far ones first
        val full = min(w * 0.2f, h * 0.12f)
        for (th in logic.things.sortedBy { it.z }) {
            if (th.z < 0f) continue
            val y = yAt(h, th.z)
            val x = laneX(w, th.lane.toFloat(), th.z)
            val s = full * (0.12f + 0.88f * th.z * th.z)
            fill.color = 0x33000000
            c.drawOval(x - s * 0.4f, y - s * 0.06f, x + s * 0.4f, y + s * 0.06f, fill)
            emoji.textSize = s * (if (th.kind == 1) 0.6f else 0.95f)
            val bob = if (th.kind == 0) 0f else abs(sin(t * 5f + th.z * 9f)) * s * 0.1f
            c.drawText(th.emoji, x, y - s * 0.08f - bob, emoji)
        }
        drawKart(c, logic, look, t, w, h, full)
    }

    private fun drawKart(c: NativeCanvas, logic: RaceLogic, look: PetLook?, t: Float, w: Float, h: Float, full: Float) {
        val sinceHit = t - logic.hitAt
        if (sinceHit < 1.2f && ((sinceHit * 10f).toInt() % 2 == 1)) return
        val x = laneX(w, logic.laneX, 1f)
        val y = ground(h)
        val kw = full * 1.15f
        val kh = full * 0.42f
        val shakeY = sin(t * 30f) * kh * 0.03f
        val lean = (logic.lane - logic.laneX) * 14f
        c.save()
        c.rotate(lean, x, y)
        if (t < logic.boostUntil) {
            fill.shader = RadialGradient(x, y + kh * 0.3f, kw * 0.5f, intArrayOf(0xAAFFF3B0.toInt(), 0x00FF9E3D), null, Shader.TileMode.CLAMP)
            c.drawCircle(x, y + kh * 0.3f, kw * 0.5f, fill)
            fill.shader = null
        }
        // the driver
        if (look != null) {
            val p = pose.defaults(look, t)
            p.armL = 0.5f
            p.armR = 0.5f
            p.armWaveL = -30f
            p.armWaveR = -30f
            p.eyes = when {
                sinceHit < 0.8f -> EyeShape.DIZZY
                t < logic.boostUntil || t - logic.coinAt < 0.3f -> EyeShape.STAR
                else -> EyeShape.HAPPY
            }
            p.mouth = if (sinceHit < 0.8f) MouthShape.WAVY else MouthShape.OPEN
            p.tilt = lean * 0.5f
            renderer.draw(c, x, y - kh * 0.35f + shakeY, full * 0.85f, look, p)
        }
        // wheels
        fill.color = 0xFF2A2433.toInt()
        c.drawRoundRect(x - kw * 0.52f, y - kh * 0.25f, x - kw * 0.3f, y + kh * 0.35f, kh * 0.12f, kh * 0.12f, fill)
        c.drawRoundRect(x + kw * 0.3f, y - kh * 0.25f, x + kw * 0.52f, y + kh * 0.35f, kh * 0.12f, kh * 0.12f, fill)
        // body
        fill.shader = LinearGradient(0f, y - kh * 0.6f, 0f, y + kh * 0.3f, 0xFFFF7A9C.toInt(), 0xFFD8406A.toInt(), Shader.TileMode.CLAMP)
        c.drawRoundRect(x - kw * 0.4f, y - kh * 0.6f + shakeY, x + kw * 0.4f, y + kh * 0.25f + shakeY, kh * 0.3f, kh * 0.3f, fill)
        fill.shader = null
        fill.color = 0x66FFFFFF
        c.drawRoundRect(x - kw * 0.32f, y - kh * 0.52f + shakeY, x + kw * 0.32f, y - kh * 0.38f + shakeY, kh * 0.07f, kh * 0.07f, fill)
        // number
        fill.color = AColor.WHITE
        c.drawCircle(x, y - kh * 0.15f + shakeY, kh * 0.22f, fill)
        emoji.textSize = kh * 0.3f
        emoji.color = 0xFFD8406A.toInt()
        c.drawText("1", x, y - kh * 0.05f + shakeY, emoji)
        emoji.color = AColor.BLACK
        c.restore()
    }
}

@Composable
fun RaceGame(state: GameState, onFinished: (Int) -> Unit, onBack: () -> Unit) {
    val logic = remember { RaceLogic() }
    val painter = remember { RacePainter() }
    val ps = remember { ParticleSystem() }
    val shake = remember { Shake() }
    val clock = remember { GameClock() }
    val look = remember { gameLook(state) }
    val time = rememberFrameTime()
    val tf = rememberCanvasTypeface()
    val haptic = rememberHaptic()
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
                        if (clock.running) {
                            logic.steer(if (down.position.x < size.width / 2f) -1 else 1)
                            haptic()
                        }
                    }
                },
        ) {
            val t = time.floatValue
            val dt = if (lastT[0] < 0f) 0f else (t - lastT[0]).coerceIn(0f, 0.05f)
            lastT[0] = t
            val w = size.width
            val h = size.height
            if (clock.running) {
                logic.update(t, dt, ps, shake, painter.ground(h) / h) { lane -> painter.laneX(w, lane, 1f) / w }
            }
            ps.update(t)
            val off = shake.offset(t)
            drawIntoCanvas { canvas ->
                val c = canvas.nativeCanvas
                c.save()
                c.translate(off.x, off.y)
                painter.draw(c, logic, look, t, w, h)
                ps.draw(c, w, h)
                c.restore()
            }
        }
        GameHud(
            onBack = onBack,
            score = "${logic.score} m",
            lives = logic.lives,
            maxLives = 3,
            modifier = Modifier.align(Alignment.TopCenter),
        )
        if (!clock.running && !clock.finished) {
            CountdownOverlay("Tippe links oder rechts, um die Spur zu wechseln!") { clock.running = true }
        }
    }
}
