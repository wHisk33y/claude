package de.knuffi.app.ui.games

import android.graphics.Canvas as NativeCanvas
import android.graphics.Color as AColor
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.Shader
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import de.knuffi.app.render.Colors
import de.knuffi.app.render.EyeShape
import de.knuffi.app.render.MouthShape
import de.knuffi.app.render.PKind
import de.knuffi.app.render.Particle
import de.knuffi.app.render.ParticleSystem
import de.knuffi.app.render.PetDirector
import de.knuffi.app.render.PetLook
import de.knuffi.app.render.PetPose
import de.knuffi.app.render.PetRenderer
import de.knuffi.app.ui.components.rememberHaptic
import de.knuffi.app.ui.rememberCanvasTypeface
import de.knuffi.app.ui.rememberFrameTime
import de.knuffi.core.GameState
import kotlinx.coroutines.delay
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

private const val EMPTY = 0
private const val PET = 1
private const val CLOUD = 2
private const val GOLDEN = 3

private class Hole {
    var kind = EMPTY
    var start = 0f
    var dur = 0f
    var hitAt = -10f
    var wasHit = false
    var cx = 0f
    var cy = 0f
    var scale = 1f
}

private class WhackLogic {
    val holes = Array(9) { Hole() }
    var score by mutableIntStateOf(0)
    var combo by mutableIntStateOf(0)
    var timeLeft by mutableFloatStateOf(DURATION)
    var over by mutableStateOf(false)
    var hammerX = 0f
    var hammerY = 0f
    var hammerAt = -10f
    var zapAt = -10f
    private var elapsed = 0f
    private var nextSpawn = 0.6f
    private val rnd = Random(System.nanoTime())

    fun update(t: Float, dt: Float) {
        for (h in holes) {
            if (h.kind == EMPTY) continue
            val gone = if (h.wasHit) t - h.hitAt > 0.55f else t - h.start > h.dur
            if (gone) {
                if (!h.wasHit && (h.kind == PET || h.kind == GOLDEN)) combo = 0
                h.kind = EMPTY
            }
        }
        if (over) return
        elapsed += dt
        val left = max(0f, DURATION - elapsed)
        if (abs(left - timeLeft) >= 0.1f || left == 0f) timeLeft = left
        if (elapsed >= DURATION) {
            over = true
            return
        }
        val speed = 1f + elapsed / 14f
        nextSpawn -= dt
        if (nextSpawn <= 0f) {
            nextSpawn = (0.5f + rnd.nextFloat() * 0.35f) / speed
            val free = holes.indices.filter { holes[it].kind == EMPTY }
            if (free.isNotEmpty()) {
                val h = holes[free[rnd.nextInt(free.size)]]
                val r = rnd.nextFloat()
                h.kind = when {
                    r < 0.2f -> CLOUD
                    r < 0.27f -> GOLDEN
                    else -> PET
                }
                h.start = t
                h.dur = max(0.55f, 1.3f / speed + 0.1f) * (if (h.kind == GOLDEN) 0.7f else 1f)
                h.wasHit = false
            }
        }
    }

    fun rise(h: Hole, t: Float): Float {
        if (h.kind == EMPTY) return 0f
        if (h.wasHit) {
            val a = t - h.hitAt
            return if (a < 0.3f) 1f else (1f - (a - 0.3f) / 0.25f).coerceIn(0f, 1f)
        }
        val a = t - h.start
        return when {
            a < 0.15f -> PetDirector.easeOutBack(a / 0.15f)
            a > h.dur - 0.15f -> (h.dur - a) / 0.15f
            else -> 1f
        }.coerceIn(0f, 1f)
    }

    /** Returns true when something was hit. */
    fun tap(x: Float, y: Float, t: Float, w: Float, h: Float, petSize: Float, ps: ParticleSystem, shake: Shake): Boolean {
        hammerX = x
        hammerY = y
        hammerAt = t
        if (over) return false
        var best: Hole? = null
        var bestD = Float.MAX_VALUE
        for (hole in holes) {
            if (hole.kind == EMPTY || hole.wasHit || rise(hole, t) < 0.3f) continue
            val s = petSize * hole.scale
            val cy = hole.cy - s * 0.45f
            val d = abs(x - hole.cx) / s + abs(y - cy) / s
            if (abs(x - hole.cx) < s * 0.6f && y > hole.cy - s * 1.1f && y < hole.cy + s * 0.25f && d < bestD) {
                best = hole
                bestD = d
            }
        }
        val nx = x / w
        val ny = y / h
        val hole = best
        if (hole == null) {
            combo = 0
            ps.burst(PKind.SMOKE, nx, ny, 4, 0.06f, 0.6f, 0.03f, intArrayOf(0x88B08A60.toInt()), drag = 2f)
            return false
        }
        hole.wasHit = true
        hole.hitAt = t
        when (hole.kind) {
            CLOUD -> {
                score = max(0, score - 2)
                combo = 0
                zapAt = t
                shake.kick(t, 22f, 0.35f)
                ps.add(Particle(PKind.TEXT, nx, ny - 0.04f, 0f, -0.08f, 1f, 0.045f, 0xFFFF5A6E.toInt(), text = "Bzzzt! −2"))
                ps.burst(PKind.SPARKLE, nx, ny, 12, 0.5f, 0.6f, 0.02f, intArrayOf(0xFFFFF066.toInt(), 0xFFFFFFFF.toInt()))
            }
            else -> {
                combo++
                val gain = (if (hole.kind == GOLDEN) 3 else 1) + (combo / 5).coerceAtMost(2)
                score += gain
                shake.kick(t, 6f, 0.15f)
                val pow = listOf("POW!", "BÄM!", "ZACK!", "PENG!")[rnd.nextInt(4)]
                ps.add(Particle(PKind.TEXT, nx, ny - 0.05f, 0f, -0.06f, 0.8f, 0.05f, 0xFFFFC23D.toInt(), text = pow))
                ps.add(Particle(PKind.TEXT, nx + 0.06f, ny - 0.1f, 0f, -0.1f, 0.9f, 0.035f, 0xFF7CE0B0.toInt(), text = "+$gain"))
                ps.burst(PKind.STAR, nx, ny, 8, 0.45f, 0.7f, 0.024f, ParticleSystem.STAR_COLORS, gravity = 0.6f)
                if (hole.kind == GOLDEN) ps.burst(PKind.COIN, nx, ny, 6, 0.4f, 1f, 0.03f, intArrayOf(0xFFFFC83D.toInt()), gravity = 0.9f, upward = true)
                if (combo > 0 && combo % 5 == 0) {
                    ps.add(Particle(PKind.TEXT, 0.5f, 0.28f, 0f, -0.04f, 1.2f, 0.06f, 0xFFFF7AB6.toInt(), text = "Combo $combo!"))
                    ps.burst(PKind.CONFETTI, 0.5f, 0.28f, 20, 0.6f, 1.5f, 0.016f, ParticleSystem.CONFETTI_COLORS, gravity = 0.5f, upward = true)
                }
            }
        }
        return true
    }

    companion object {
        const val DURATION = 30f
    }
}

private class WhackPainter {
    val renderer = PetRenderer()
    val pose = PetPose()
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val emoji = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER }
    private val path = Path()

    private fun shade(s: Shader) {
        fill.color = AColor.WHITE
        fill.shader = s
    }

    fun layout(logic: WhackLogic, w: Float, h: Float) {
        val rows = floatArrayOf(0.44f, 0.63f, 0.84f)
        val scales = floatArrayOf(0.8f, 0.9f, 1f)
        for (i in 0 until 9) {
            val r = i / 3
            val c = i % 3
            val spread = 0.29f * scales[r]
            logic.holes[i].cx = w * (0.5f + (c - 1) * spread)
            logic.holes[i].cy = h * rows[r]
            logic.holes[i].scale = scales[r]
        }
    }

    fun drawGarden(c: NativeCanvas, w: Float, h: Float, t: Float) {
        shade(LinearGradient(0f, 0f, 0f, h * 0.3f, intArrayOf(0xFF7CC8FF.toInt(), 0xFFCDEBFF.toInt()), null, Shader.TileMode.CLAMP))
        c.drawRect(0f, 0f, w, h * 0.3f, fill)
        fill.shader = null
        // clouds
        for (i in 0 until 3) {
            val x = ((i * 0.37f + t * 0.01f) % 1.3f) * w - w * 0.15f
            val y = h * (0.1f + i * 0.04f)
            fill.color = 0xF2FFFFFF.toInt()
            c.drawCircle(x, y, w * 0.05f, fill)
            c.drawCircle(x + w * 0.05f, y + w * 0.012f, w * 0.038f, fill)
            c.drawCircle(x - w * 0.05f, y + w * 0.015f, w * 0.035f, fill)
        }
        // bushes behind fence
        for (i in 0 until 7) {
            val bx = w * (i / 6f)
            val r = w * 0.09f
            shade(RadialGradient(bx - r * 0.3f, h * 0.27f - r * 0.4f, r * 1.4f, intArrayOf(0xFF9BE08A.toInt(), 0xFF5EBB63.toInt(), 0xFF3B8E48.toInt()), floatArrayOf(0f, 0.55f, 1f), Shader.TileMode.CLAMP))
            c.drawCircle(bx, h * 0.28f, r, fill)
        }
        fill.shader = null
        // grass
        shade(LinearGradient(0f, h * 0.29f, 0f, h, intArrayOf(0xFF9BE08A.toInt(), 0xFF6CC45A.toInt(), 0xFF4FA848.toInt()), null, Shader.TileMode.CLAMP))
        c.drawRect(0f, h * 0.29f, w, h, fill)
        fill.shader = null
        // white picket fence
        val postW = w * 0.04f
        var x = postW * 0.5f
        fill.color = 0xFFFFFFFF.toInt()
        c.drawRect(0f, h * 0.285f, w, h * 0.296f, fill)
        c.drawRect(0f, h * 0.315f, w, h * 0.326f, fill)
        while (x < w) {
            path.reset()
            path.moveTo(x, h * 0.26f)
            path.lineTo(x + postW / 2f, h * 0.25f)
            path.lineTo(x + postW, h * 0.26f)
            path.lineTo(x + postW, h * 0.34f)
            path.lineTo(x, h * 0.34f)
            path.close()
            fill.color = 0xFFFFFFFF.toInt()
            c.drawPath(path, fill)
            fill.color = 0x22000000
            c.drawRect(x + postW * 0.7f, h * 0.26f, x + postW, h * 0.34f, fill)
            x += postW * 1.8f
        }
        // mowed stripes
        for (i in 0 until 5) {
            fill.color = 0x10FFFFFF
            c.drawRect(0f, h * (0.36f + i * 0.13f), w, h * (0.42f + i * 0.13f), fill)
        }
        // flowers
        for (i in 0 until 12) {
            val fx = ((i * 0.137f + 0.05f) % 1f) * w
            val fy = h * (0.37f + ((i * 0.29f) % 0.6f))
            fill.color = if (i % 3 == 0) 0xFFFF8FB8.toInt() else if (i % 3 == 1) 0xFFFFE066.toInt() else 0xFFFFFFFF.toInt()
            val sway = sin(t * 2f + i) * w * 0.003f
            for (k in 0 until 5) {
                val a = k * 1.2566f
                c.drawCircle(fx + sway + kotlin.math.cos(a) * w * 0.008f, fy + sin(a) * w * 0.008f, w * 0.007f, fill)
            }
            fill.color = 0xFFFFB84D.toInt()
            c.drawCircle(fx + sway, fy, w * 0.006f, fill)
        }
    }

    fun drawHoleBack(c: NativeCanvas, hole: Hole, petSize: Float) {
        val s = petSize * hole.scale
        val rw = s * 0.62f
        val rh = s * 0.2f
        // dirt mound
        fill.color = 0x33000000
        c.drawOval(hole.cx - rw * 1.3f, hole.cy - rh * 0.8f, hole.cx + rw * 1.3f, hole.cy + rh * 1.9f, fill)
        shade(RadialGradient(hole.cx, hole.cy - rh, rw * 1.4f, intArrayOf(0xFFC08A5A.toInt(), 0xFF9A6A40.toInt(), 0xFF7A5030.toInt()), floatArrayOf(0f, 0.6f, 1f), Shader.TileMode.CLAMP))
        c.drawOval(hole.cx - rw * 1.22f, hole.cy - rh * 1.35f, hole.cx + rw * 1.22f, hole.cy + rh * 1.5f, fill)
        fill.shader = null
        // hole
        shade(RadialGradient(hole.cx, hole.cy + rh * 0.3f, rw, intArrayOf(0xFF1E120A.toInt(), 0xFF3A2418.toInt()), null, Shader.TileMode.CLAMP))
        c.drawOval(hole.cx - rw, hole.cy - rh, hole.cx + rw, hole.cy + rh, fill)
        fill.shader = null
    }

    fun drawHoleFront(c: NativeCanvas, hole: Hole, petSize: Float) {
        val s = petSize * hole.scale
        val rw = s * 0.62f
        val rh = s * 0.2f
        // front lip of the mound covering the lower part
        path.reset()
        path.addOval(hole.cx - rw * 1.22f, hole.cy - rh * 0.1f, hole.cx + rw * 1.22f, hole.cy + rh * 1.5f, Path.Direction.CW)
        c.save()
        c.clipRect(hole.cx - rw * 1.3f, hole.cy, hole.cx + rw * 1.3f, hole.cy + rh * 2f)
        shade(LinearGradient(0f, hole.cy, 0f, hole.cy + rh * 1.5f, 0xFFA87448.toInt(), 0xFF7A5030.toInt(), Shader.TileMode.CLAMP))
        c.drawPath(path, fill)
        fill.shader = null
        c.restore()
        // pebbles
        fill.color = 0x55FFFFFF
        c.drawCircle(hole.cx - rw * 0.7f, hole.cy + rh * 0.9f, s * 0.025f, fill)
        c.drawCircle(hole.cx + rw * 0.5f, hole.cy + rh * 1.1f, s * 0.02f, fill)
    }

    fun drawOccupant(c: NativeCanvas, hole: Hole, look: PetLook?, logic: WhackLogic, t: Float, petSize: Float) {
        val rise = logic.rise(hole, t)
        if (rise <= 0f) return
        val s = petSize * hole.scale
        val hitA = t - hole.hitAt
        c.save()
        c.clipRect(hole.cx - s * 1.5f, 0f, hole.cx + s * 1.5f, hole.cy + s * 0.04f)
        val ground = hole.cy + s * 0.95f * (1f - rise) + s * 0.12f
        when (hole.kind) {
            CLOUD -> {
                val cy = ground - s * 0.5f
                val r = s * 0.34f
                val angry = hole.wasHit
                shade(RadialGradient(hole.cx - r * 0.3f, cy - r * 0.4f, r * 2f, intArrayOf(0xFF9A9AB8.toInt(), 0xFF5A5A78.toInt(), 0xFF3A3A55.toInt()), floatArrayOf(0f, 0.6f, 1f), Shader.TileMode.CLAMP))
                c.drawCircle(hole.cx, cy - r * 0.3f, r, fill)
                c.drawCircle(hole.cx - r * 0.9f, cy + r * 0.1f, r * 0.75f, fill)
                c.drawCircle(hole.cx + r * 0.9f, cy + r * 0.1f, r * 0.75f, fill)
                c.drawRoundRect(hole.cx - r * 1.5f, cy - r * 0.1f, hole.cx + r * 1.5f, cy + r * 0.7f, r * 0.4f, r * 0.4f, fill)
                fill.shader = null
                // grumpy face
                fill.color = AColor.WHITE
                c.drawCircle(hole.cx - r * 0.38f, cy, r * 0.17f, fill)
                c.drawCircle(hole.cx + r * 0.38f, cy, r * 0.17f, fill)
                fill.color = 0xFF1E1E2E.toInt()
                c.drawCircle(hole.cx - r * 0.34f, cy + r * 0.04f, r * 0.09f, fill)
                c.drawCircle(hole.cx + r * 0.34f, cy + r * 0.04f, r * 0.09f, fill)
                fill.strokeWidth = r * 0.08f
                c.drawLine(hole.cx - r * 0.6f, cy - r * 0.3f, hole.cx - r * 0.2f, cy - r * 0.18f, fill.apply { style = Paint.Style.STROKE })
                c.drawLine(hole.cx + r * 0.6f, cy - r * 0.3f, hole.cx + r * 0.2f, cy - r * 0.18f, fill)
                fill.style = Paint.Style.FILL
                val flick = if (angry) 1f else (0.5f + 0.5f * sin(t * 25f + hole.cx))
                emoji.textSize = r * 0.9f
                emoji.alpha = (255 * flick).toInt()
                c.drawText("⚡", hole.cx + r * 0.9f, cy + r * 1.2f, emoji)
                emoji.alpha = 255
            }
            else -> {
                val l = look
                if (l != null) {
                    val golden = hole.kind == GOLDEN
                    if (golden) {
                        shade(RadialGradient(hole.cx, ground - s * 0.5f, s * 0.9f, intArrayOf(0xAAFFE066.toInt(), 0x00FFE066), null, Shader.TileMode.CLAMP))
                        c.drawCircle(hole.cx, ground - s * 0.5f, s * 0.9f, fill)
                        fill.shader = null
                    }
                    val p = pose.defaults(l, t + hole.cx)
                    p.armL = 0.7f
                    p.armR = 0.7f
                    p.eyes = if (golden) EyeShape.STAR else EyeShape.HAPPY
                    p.mouth = MouthShape.GRIN
                    p.lookX = sin(t * 3f + hole.cx) * 0.6f
                    if (hole.wasHit) {
                        p.eyes = EyeShape.DIZZY
                        p.mouth = MouthShape.WAVY
                        val sq = if (hitA < 0.2f) sin(hitA / 0.2f * Math.PI.toFloat()) else 0f
                        p.scaleY *= 1f - 0.3f * sq
                        p.scaleX *= 1f + 0.25f * sq
                        p.tilt = sin(hitA * 25f) * 8f
                    }
                    if (golden) p.glow = 1f
                    renderer.draw(c, hole.cx, ground, s, l, p)
                    if (golden) {
                        emoji.textSize = s * 0.3f
                        c.drawText("👑", hole.cx, ground - s * 1.05f, emoji)
                    }
                }
            }
        }
        c.restore()
        // dizzy stars circling
        if (hole.wasHit && hole.kind != CLOUD && hitA < 0.55f) {
            emoji.textSize = s * 0.16f
            for (k in 0 until 3) {
                val a = hitA * 10f + k * 2.1f
                c.drawText("⭐", hole.cx + kotlin.math.cos(a) * s * 0.35f, hole.cy - s * 0.95f + sin(a) * s * 0.08f, emoji)
            }
        }
    }

    fun drawHammer(c: NativeCanvas, logic: WhackLogic, t: Float, petSize: Float) {
        val a = t - logic.hammerAt
        if (a > 0.3f) return
        val swing = if (a < 0.08f) -50f + 70f * (a / 0.08f) else 20f
        emoji.textSize = petSize * 0.6f
        emoji.alpha = (255 * (1f - (a / 0.3f).coerceIn(0f, 1f) * 0.8f)).toInt()
        c.save()
        c.rotate(swing, logic.hammerX + petSize * 0.3f, logic.hammerY + petSize * 0.15f)
        c.drawText("🔨", logic.hammerX + petSize * 0.15f, logic.hammerY - petSize * 0.05f, emoji)
        c.restore()
        emoji.alpha = 255
    }

    fun drawZap(c: NativeCanvas, w: Float, h: Float, t: Float, zapAt: Float) {
        val a = t - zapAt
        if (a > 0.35f) return
        val on = (a * 30f).toInt() % 2 == 0
        fill.color = Colors.alpha(if (on) 0xFFFFFFFF.toInt() else 0xFF3A3A60.toInt(), 0.45f * (1f - a / 0.35f))
        c.drawRect(0f, 0f, w, h, fill)
    }
}

@Composable
fun WhackGame(state: GameState, onFinished: (Int) -> Unit, onBack: () -> Unit) {
    val logic = remember { WhackLogic() }
    val painter = remember { WhackPainter() }
    val ps = remember { ParticleSystem() }
    val shake = remember { Shake() }
    val clock = remember { GameClock() }
    val look = remember { gameLook(state) }
    val time = rememberFrameTime()
    val tf = rememberCanvasTypeface()
    val haptic = rememberHaptic()
    val lastT = remember { floatArrayOf(-1f) }
    val dims = remember { floatArrayOf(1f, 1f, 1f) }

    LaunchedEffect(tf) { ps.typeface = tf }
    LaunchedEffect(logic.over) {
        if (logic.over) {
            clock.finished = true
            delay(900)
            onFinished(logic.score)
        }
    }

    Box(Modifier.fillMaxSize()) {
        Canvas(
            Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectTapGestures { pos ->
                        if (clock.running && !clock.finished) {
                            if (logic.tap(pos.x, pos.y, time.floatValue, dims[0], dims[1], dims[2], ps, shake)) haptic()
                        }
                    }
                },
        ) {
            val t = time.floatValue
            val dt = if (lastT[0] < 0f) 0f else (t - lastT[0]).coerceIn(0f, 0.05f)
            lastT[0] = t
            val w = size.width
            val h = size.height
            val petSize = min(w * 0.3f, h * 0.17f)
            dims[0] = w
            dims[1] = h
            dims[2] = petSize
            painter.layout(logic, w, h)
            if (clock.running) logic.update(t, dt)
            ps.update(t)
            val off = shake.offset(t)
            drawIntoCanvas { canvas ->
                val c = canvas.nativeCanvas
                c.save()
                c.translate(off.x, off.y)
                painter.drawGarden(c, w, h, t)
                for (hole in logic.holes) {
                    painter.drawHoleBack(c, hole, petSize)
                    painter.drawOccupant(c, hole, look, logic, t, petSize)
                    painter.drawHoleFront(c, hole, petSize)
                }
                painter.drawHammer(c, logic, t, petSize)
                painter.drawZap(c, w, h, t, logic.zapAt)
                ps.draw(c, w, h)
                c.restore()
            }
        }
        GameHud(
            onBack = onBack,
            score = "${logic.score}",
            timeFraction = logic.timeLeft / WhackLogic.DURATION,
            timeText = "${logic.timeLeft.toInt()}s",
            combo = if (logic.combo >= 3) logic.combo else 0,
            modifier = Modifier.align(Alignment.TopCenter),
        )
        if (!clock.running && !clock.finished) {
            CountdownOverlay("Tippe dein Haustier an! Nicht die ⛈️") { clock.running = true }
        }
    }
}
