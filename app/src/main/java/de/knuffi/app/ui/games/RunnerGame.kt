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

/** Something on the track. Positions in screen widths (x) and pet sizes (height). */
private class Obstacle(var x: Float, val w: Float, val h: Float, val emoji: String, var passed: Boolean = false)

private class Coin(var x: Float, val y: Float, var taken: Boolean = false)

private class RunnerLogic {
    var meters by mutableIntStateOf(0)
    var lives by mutableIntStateOf(3)
    var coins by mutableIntStateOf(0)
    var over by mutableStateOf(false)
    /** Height of the pet above the ground in pet sizes. */
    var jumpY = 0f
    var vy = 0f
    var jumps = 0
    var distance = 0f
    var speed = START_SPEED
    var hitAt = -10f
    var landAt = -10f
    var coinAt = -10f
    val obstacles = ArrayList<Obstacle>()
    val coinList = ArrayList<Coin>()
    private var nextObstacle = 1.2f
    private var nextCoins = 0.8f
    private var elapsed = 0f
    private val rnd = Random(System.nanoTime())

    val score: Int get() = meters + coins * COIN_METERS

    fun jump(t: Float, ps: ParticleSystem, px: Float, groundNy: Float) {
        if (over) return
        if (jumps >= 2) return
        vy = if (jumps == 0) JUMP else JUMP * 0.85f
        jumps++
        if (jumps == 2) ps.burst(PKind.SPARKLE, px, groundNy - 0.05f, 8, 0.3f, 0.6f, 0.015f, ParticleSystem.SPARKLE_COLORS)
        else ps.burst(PKind.SMOKE, px, groundNy, 5, 0.12f, 0.5f, 0.03f, intArrayOf(0x88E8D0B0.toInt()), drag = 2f)
    }

    fun update(t: Float, dt: Float, ps: ParticleSystem, shake: Shake, px: Float, petW: Float, groundNy: Float, petNy: Float, petHn: Float) {
        if (over) return
        elapsed += dt
        speed = START_SPEED + elapsed * 0.018f
        val dx = speed * dt
        distance += dx
        val m = (distance * METERS_PER_WIDTH).toInt()
        if (m != meters) meters = m

        // physics
        vy -= GRAVITY * dt
        jumpY += vy * dt
        if (jumpY <= 0f) {
            if (jumps > 0 && vy < -1f) landAt = t
            jumpY = 0f
            vy = 0f
            jumps = 0
        }

        // spawn
        nextObstacle -= dx
        if (nextObstacle <= 0f) {
            val kind = rnd.nextInt(OBSTACLES.size)
            val (emoji, w, h) = OBSTACLES[kind]
            obstacles += Obstacle(1.15f, w, h, emoji)
            if (rnd.nextFloat() < min(0.35f, elapsed * 0.006f)) {
                // a pair close together, needs a double jump or good timing
                obstacles += Obstacle(1.15f + w * 0.9f + 0.05f, w, h, emoji)
            }
            nextObstacle = (0.75f + rnd.nextFloat() * 0.8f) * (1f + speed * 0.3f)
        }
        nextCoins -= dx
        if (nextCoins <= 0f) {
            val n = 3 + rnd.nextInt(3)
            val high = rnd.nextFloat() < 0.5f
            for (i in 0 until n) {
                val k = i / (n - 1f)
                val y = if (high) 0.55f + sin(k * Math.PI.toFloat()) * 0.55f else 0.3f
                coinList += Coin(1.2f + i * 0.07f, y)
            }
            nextCoins = 1.3f + rnd.nextFloat() * 1.2f
        }

        // move & collide
        val invulnerable = t - hitAt < 1.2f
        val oIt = obstacles.iterator()
        while (oIt.hasNext()) {
            val o = oIt.next()
            o.x -= dx
            if (o.x < -0.2f) {
                oIt.remove()
                continue
            }
            val overlap = abs(o.x - px) < (o.w + petW) * 0.42f
            if (overlap && !invulnerable && jumpY < o.h * 0.8f) {
                lives -= 1
                hitAt = t
                shake.kick(t, 22f, 0.35f)
                ps.burst(PKind.STAR, px, petNy, 10, 0.4f, 0.7f, 0.025f, ParticleSystem.STAR_COLORS, gravity = 0.6f)
                ps.add(Particle(PKind.TEXT, px, petNy - 0.06f, 0f, -0.07f, 1f, 0.045f, 0xFFFF5A6E.toInt(), text = "Autsch! −❤️"))
                if (lives <= 0) over = true
            }
            if (!o.passed && o.x < px - o.w) {
                o.passed = true
            }
        }
        val cIt = coinList.iterator()
        while (cIt.hasNext()) {
            val c = cIt.next()
            c.x -= dx
            if (c.x < -0.1f) {
                cIt.remove()
                continue
            }
            if (!c.taken && abs(c.x - px) < petW * 0.45f && abs(c.y - (jumpY + 0.45f)) < 0.5f) {
                c.taken = true
                coins += 1
                coinAt = t
                val cy = groundNy - c.y * petHn
                ps.burst(PKind.SPARKLE, c.x, cy, 6, 0.25f, 0.5f, 0.015f, ParticleSystem.STAR_COLORS)
                ps.add(Particle(PKind.TEXT, c.x, cy - 0.04f, 0f, -0.08f, 0.7f, 0.035f, 0xFFFFC23D.toInt(), text = "+$COIN_METERS"))
                cIt.remove()
            }
        }
    }

    companion object {
        const val START_SPEED = 0.42f
        const val GRAVITY = 9f
        const val JUMP = 4.3f
        const val METERS_PER_WIDTH = 12f
        const val COIN_METERS = 10
        /** emoji, width (screen widths), height (pet sizes) */
        val OBSTACLES = listOf(
            Triple("🌵", 0.1f, 0.62f),
            Triple("🪨", 0.12f, 0.42f),
            Triple("🍄", 0.09f, 0.45f),
            Triple("🪵", 0.13f, 0.38f),
            Triple("📦", 0.1f, 0.5f),
        )
    }
}

private class RunnerPainter {
    val renderer = PetRenderer()
    val pose = PetPose()
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val emoji = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER }
    private val path = Path()
    private var skyH = -1f
    private var sky: Shader? = null

    fun drawWorld(c: NativeCanvas, w: Float, h: Float, t: Float, distance: Float, groundY: Float) {
        if (skyH != h) {
            skyH = h
            sky = LinearGradient(0f, 0f, 0f, groundY, intArrayOf(0xFF7EC8FF.toInt(), 0xFFFFD6B8.toInt(), 0xFFFFB38A.toInt()), floatArrayOf(0f, 0.65f, 1f), Shader.TileMode.CLAMP)
        }
        fill.color = AColor.WHITE
        fill.shader = sky
        c.drawRect(0f, 0f, w, h, fill)
        fill.shader = null
        // sun
        val sx = w * 0.78f
        val sy = h * 0.2f
        fill.shader = RadialGradient(sx, sy, w * 0.3f, intArrayOf(0x88FFF3B0.toInt(), 0x00FFF3B0), null, Shader.TileMode.CLAMP)
        c.drawCircle(sx, sy, w * 0.3f, fill)
        fill.shader = RadialGradient(sx - w * 0.03f, sy - w * 0.03f, w * 0.12f, intArrayOf(0xFFFFFBE0.toInt(), 0xFFFFD34D.toInt(), 0xFFFFA83D.toInt()), floatArrayOf(0f, 0.6f, 1f), Shader.TileMode.CLAMP)
        c.drawCircle(sx, sy, w * 0.09f, fill)
        fill.shader = null
        // parallax layers
        layer(c, w, h, groundY - h * 0.16f, h * 0.08f, 2.3f, distance * 0.15f, 0xFFD8A8E0.toInt(), 0xFFB888C8.toInt(), groundY)
        layer(c, w, h, groundY - h * 0.08f, h * 0.05f, 4f, distance * 0.4f, 0xFFFFB88A.toInt(), 0xFFE8905A.toInt(), groundY)
        // clouds
        for (i in 0 until 4) {
            val x = (((i * 0.33f - distance * 0.08f - t * 0.01f) % 1.3f) + 1.3f) % 1.3f * w - w * 0.15f
            val y = h * (0.1f + (i % 2) * 0.08f)
            val r = w * (0.05f + (i % 3) * 0.015f)
            fill.color = Colors.alpha(AColor.WHITE, 0.85f)
            c.drawCircle(x, y, r, fill)
            c.drawCircle(x + r, y + r * 0.2f, r * 0.75f, fill)
            c.drawCircle(x - r, y + r * 0.25f, r * 0.7f, fill)
            c.drawOval(x - r * 1.7f, y, x + r * 1.7f, y + r * 0.8f, fill)
        }
        // track
        fill.color = AColor.WHITE
        fill.shader = LinearGradient(0f, groundY, 0f, h, 0xFFE8B070.toInt(), 0xFFB8783A.toInt(), Shader.TileMode.CLAMP)
        c.drawRect(0f, groundY, w, h, fill)
        fill.shader = null
        fill.color = 0xFF8ED06A.toInt()
        c.drawRect(0f, groundY - h * 0.012f, w, groundY + h * 0.008f, fill)
        fill.color = Colors.alpha(0xFF8A5020.toInt(), 0.35f)
        val stripe = w * 0.14f
        val off = (distance * w) % stripe
        var x = -off
        while (x < w) {
            c.drawRoundRect(x, groundY + h * 0.04f, x + stripe * 0.45f, groundY + h * 0.055f, h * 0.01f, h * 0.01f, fill)
            x += stripe
        }
    }

    private fun layer(c: NativeCanvas, w: Float, h: Float, base: Float, amp: Float, freq: Float, phase: Float, top: Int, bottom: Int, groundY: Float) {
        path.reset()
        path.moveTo(0f, h)
        var x = 0f
        while (x <= w + 10f) {
            path.lineTo(x, base + sin((x / w + phase) * freq) * amp)
            x += 10f
        }
        path.lineTo(w, h)
        path.close()
        fill.color = AColor.WHITE
        fill.shader = LinearGradient(0f, base - amp, 0f, groundY, top, bottom, Shader.TileMode.CLAMP)
        c.drawPath(path, fill)
        fill.shader = null
    }

    fun drawObstacle(c: NativeCanvas, o: Obstacle, w: Float, groundY: Float, petSize: Float) {
        val x = o.x * w
        val size = o.h * petSize * 1.25f
        fill.color = Colors.alpha(AColor.BLACK, 0.18f)
        c.drawOval(x - size * 0.45f, groundY - size * 0.06f, x + size * 0.45f, groundY + size * 0.06f, fill)
        emoji.textSize = size
        c.drawText(o.emoji, x, groundY - size * 0.1f, emoji)
    }

    fun drawCoin(c: NativeCanvas, coin: Coin, w: Float, groundY: Float, petSize: Float, t: Float) {
        val x = coin.x * w
        val y = groundY - coin.y * petSize
        val r = petSize * 0.13f
        val squeeze = abs(sin(t * 4f + coin.x * 20f)).coerceAtLeast(0.2f)
        fill.color = AColor.WHITE
        fill.shader = RadialGradient(x, y, r * 2.2f, intArrayOf(0x66FFE680, 0x00FFE680), null, Shader.TileMode.CLAMP)
        c.drawCircle(x, y, r * 2.2f, fill)
        fill.shader = RadialGradient(x - r * 0.3f, y - r * 0.3f, r * 1.4f, intArrayOf(0xFFFFF6C8.toInt(), 0xFFFFC83D.toInt(), 0xFFD48A00.toInt()), floatArrayOf(0f, 0.55f, 1f), Shader.TileMode.CLAMP)
        c.drawOval(x - r * squeeze, y - r, x + r * squeeze, y + r, fill)
        fill.shader = null
        fill.color = 0xAAFFFFFF.toInt()
        c.drawOval(x - r * 0.5f * squeeze, y - r * 0.7f, x, y - r * 0.2f, fill)
    }

    fun drawPet(c: NativeCanvas, look: PetLook, logic: RunnerLogic, t: Float, px: Float, groundY: Float, petSize: Float) {
        val p = pose.defaults(look, t)
        val air = logic.jumpY > 0.02f
        val ph = t * (10f + logic.speed * 6f)
        p.turn = 0.55f
        p.lift = logic.jumpY * 2f
        if (air) {
            p.armL = 1f
            p.armR = 1f
            p.strideL = 0.5f
            p.strideR = -0.5f
            p.eyes = EyeShape.HAPPY
            p.mouth = MouthShape.OPEN
            p.tilt = if (logic.jumps >= 2) (t * 720f) % 360f else -logic.vy * 2f
        } else {
            p.strideL = kotlin.math.cos(ph) * 0.9f
            p.strideR = -p.strideL
            p.footL = max(0f, sin(ph)) * 0.9f
            p.footR = max(0f, -sin(ph)) * 0.9f
            p.armL = 0.35f + 0.25f * sin(ph)
            p.armR = 0.35f - 0.25f * sin(ph)
            p.lift += abs(sin(ph)) * 0.05f
            p.mouth = MouthShape.SMILE
        }
        val sinceLand = t - logic.landAt
        if (sinceLand < 0.18f) {
            val k = 1f - sinceLand / 0.18f
            p.scaleY *= 1f - 0.18f * k
            p.scaleX *= 1f + 0.14f * k
        }
        if (t - logic.coinAt < 0.3f) p.eyes = EyeShape.STAR
        val sinceHit = t - logic.hitAt
        if (sinceHit < 0.8f) {
            p.eyes = EyeShape.DIZZY
            p.mouth = MouthShape.WAVY
            p.sweat = 1f
        }
        // blink while invulnerable
        if (sinceHit < 1.2f && ((sinceHit * 10f).toInt() % 2 == 1)) return
        renderer.drawShadow(c, px, groundY, petSize, p, look)
        renderer.draw(c, px, groundY, petSize, look, p)
    }
}

@Composable
fun RunnerGame(state: GameState, onFinished: (Int) -> Unit, onBack: () -> Unit) {
    val logic = remember { RunnerLogic() }
    val painter = remember { RunnerPainter() }
    val ps = remember { ParticleSystem() }
    val shake = remember { Shake() }
    val clock = remember { GameClock() }
    val look = remember { gameLook(state) }
    val time = rememberFrameTime()
    val tf = rememberCanvasTypeface()
    val lastT = remember { floatArrayOf(-1f) }
    val geometry = remember { floatArrayOf(0.25f, 0.85f) }

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
                        awaitFirstDown()
                        if (clock.running) logic.jump(time.floatValue, ps, geometry[0], geometry[1])
                    }
                },
        ) {
            val t = time.floatValue
            val dt = if (lastT[0] < 0f) 0f else (t - lastT[0]).coerceIn(0f, 0.05f)
            lastT[0] = t
            val w = size.width
            val h = size.height
            val groundY = h * 0.8f
            val petSize = min(w * 0.3f, h * 0.17f)
            val px = 0.25f
            geometry[0] = px
            geometry[1] = groundY / h
            val petW = petSize * 0.7f / w
            val petNy = (groundY - (logic.jumpY + 0.45f) * petSize) / h
            if (clock.running) logic.update(t, dt, ps, shake, px, petW, groundY / h, petNy, petSize / h)
            ps.update(t)
            val off = shake.offset(t)
            drawIntoCanvas { canvas ->
                val c = canvas.nativeCanvas
                c.save()
                c.translate(off.x, off.y)
                painter.drawWorld(c, w, h, t, logic.distance, groundY)
                for (coin in logic.coinList) painter.drawCoin(c, coin, w, groundY, petSize, t)
                for (o in logic.obstacles) painter.drawObstacle(c, o, w, groundY, petSize)
                val l = look
                if (l != null) painter.drawPet(c, l, logic, t, px * w, groundY, petSize)
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
            CountdownOverlay("Tippe zum Springen, zweimal für einen Doppelsprung!") { clock.running = true }
        }
    }
}
