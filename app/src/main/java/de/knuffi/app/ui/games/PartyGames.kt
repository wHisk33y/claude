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
import de.knuffi.app.sound.Sfx
import de.knuffi.app.sound.Sounds
import de.knuffi.app.ui.components.rememberHaptic
import de.knuffi.app.ui.rememberCanvasTypeface
import de.knuffi.app.ui.rememberFrameTime
import de.knuffi.core.GameState
import kotlinx.coroutines.delay
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

// ====================================================================================== dance party

private class Note(val lane: Int, val at: Float) {
    var judged = false
    var hit = false
}

private class DanceLogic {
    val song = 45f
    var time = 0f
    var score by mutableIntStateOf(0)
    var combo by mutableIntStateOf(0)
    var best = 0
    var over by mutableStateOf(false)
    val notes = ArrayList<Note>()
    private val rnd = Random(System.nanoTime())
    private var nextBeat = 1.6f
    private var beatNo = 0
    private var lastLane = -1
    private var sameLane = 0

    /** Last judgement shown over the lanes. */
    var label = ""
    var labelAt = -10f
    var labelColor = AColor.WHITE

    /** For the pet: when it was last happy or clumsy. */
    var cheerAt = -10f
    var missAt = -10f

    fun bpm(t: Float): Float = 96f + 30f * (t / song).coerceIn(0f, 1f)

    fun beatPhase(t: Float): Float {
        // phase 0..1 within the current beat (approximate, tempo changes slowly)
        val b = 60f / bpm(t)
        return ((t - 1.6f) / b).let { it - kotlin.math.floor(it) }
    }

    private fun lane(): Int {
        var l = rnd.nextInt(4)
        if (l == lastLane) {
            sameLane++
            if (sameLane >= 2) l = (l + 1 + rnd.nextInt(3)) % 4
        } else sameLane = 0
        lastLane = l
        return l
    }

    fun update(dt: Float, onBeat: () -> Unit, onMiss: () -> Unit) {
        time += dt
        // write the song a little ahead
        while (nextBeat < time + 2.2f && nextBeat < song - 0.5f) {
            val b = 60f / bpm(nextBeat)
            val stage = (nextBeat / song)
            notes += Note(lane(), nextBeat)
            if (beatNo % 4 == 3 && stage > 0.15f) notes += Note(lane(), nextBeat + b / 2f)
            if (stage > 0.55f && beatNo % 8 == 6) {
                val l = (lastLane + 2) % 4
                notes += Note(l, nextBeat)
            }
            nextBeat += b
            beatNo++
        }
        for (n in notes) {
            if (!n.judged && time > n.at + 0.2f) {
                n.judged = true
                combo = 0
                label = "Verpasst"
                labelAt = time
                labelColor = 0xFFB8B0D0.toInt()
                missAt = time
                onMiss()
            }
        }
        notes.removeAll { it.judged && time > it.at + 0.6f }
        if (time >= song && !over) over = true
        if (beatPhase(time) < dt * (bpm(time) / 60f) * 1.01f) onBeat()
    }

    /** Tap on a lane: returns the hit quality (2 perfect, 1 good, 0 nothing). */
    fun tap(lane: Int): Int {
        var bestNote: Note? = null
        var bestD = 1f
        for (n in notes) {
            if (n.judged || n.lane != lane) continue
            val d = abs(n.at - time)
            if (d < bestD) {
                bestD = d
                bestNote = n
            }
        }
        val n = bestNote ?: return 0
        if (bestD > 0.18f) return 0
        n.judged = true
        n.hit = true
        combo++
        best = max(best, combo)
        val q = if (bestD <= 0.07f) 2 else 1
        score += if (q == 2) 3 else 1
        if (combo % 10 == 0) {
            score += 5
            cheerAt = time
        }
        label = if (q == 2) "Perfekt!" else "Gut"
        labelColor = if (q == 2) 0xFFFFE066.toInt() else 0xFF8FF0C8.toInt()
        labelAt = time
        return q
    }
}

private class DancePainter {
    val renderer = PetRenderer()
    val pose = PetPose()
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val line = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER }
    private val emoji = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER }
    private val path = Path()
    val colors = intArrayOf(0xFFFF5AA8.toInt(), 0xFFFFD84D.toInt(), 0xFF4CD8FF.toInt(), 0xFF6AE08A.toInt())
    private val symbols = arrayOf("💖", "⭐", "🎵", "🍀")
    var typeface: android.graphics.Typeface? = null

    fun hitY(h: Float) = h * 0.86f
    fun laneX(w: Float, lane: Int) = w * (0.2f + 0.2f * lane)
    fun laneOf(w: Float, x: Float): Int = ((x / w - 0.1f) / 0.2f).toInt().coerceIn(0, 3)
    fun speed(h: Float) = h * 0.5f

    fun draw(c: NativeCanvas, g: DanceLogic, look: PetLook?, t: Float, w: Float, h: Float, pressed: FloatArray) {
        val beat = g.beatPhase(g.time)
        val pulse = (1f - beat).let { it * it }
        // stage
        fill.alpha = 255
        fill.shader = LinearGradient(0f, 0f, 0f, h, 0xFF2A1458.toInt(), 0xFF5A1E6E.toInt(), Shader.TileMode.CLAMP)
        c.drawRect(0f, 0f, w, h, fill)
        fill.shader = null
        // sweeping spotlights
        for (k in 0 until 4) {
            val a = sin(t * 0.7f + k * 1.7f) * 0.5f
            val x0 = w * (0.15f + 0.23f * k)
            path.reset()
            path.moveTo(x0, 0f)
            path.lineTo(x0 + w * (a - 0.18f), h * 0.75f)
            path.lineTo(x0 + w * (a + 0.18f), h * 0.75f)
            path.close()
            fill.alpha = 255
        fill.shader = LinearGradient(0f, 0f, 0f, h * 0.75f, Colors.alpha(colors[k], 0.32f + 0.15f * pulse), Colors.alpha(colors[k], 0f), Shader.TileMode.CLAMP)
            c.drawPath(path, fill)
            fill.shader = null
        }
        // disco ball
        val bx = w / 2f
        val by = h * 0.07f
        val br = w * 0.07f
        line.color = 0xFFB8B0D0.toInt()
        line.strokeWidth = w * 0.004f
        c.drawLine(bx, 0f, bx, by - br, line)
        fill.alpha = 255
        fill.shader = RadialGradient(bx - br * 0.3f, by - br * 0.3f, br * 1.3f, intArrayOf(AColor.WHITE, 0xFFB8C0E0.toInt(), 0xFF6A6488.toInt()), null, Shader.TileMode.CLAMP)
        c.drawCircle(bx, by, br, fill)
        fill.shader = null
        for (k in 0 until 10) {
            val a = t * 1.2f + k * 0.63f
            val sx = bx + cos(a) * br * 0.7f
            val sy = by + sin(k * 1.3f) * br * 0.6f
            if (cos(a) > -0.2f) {
                fill.color = Colors.alpha(AColor.WHITE, 0.5f + 0.5f * abs(sin(t * 6f + k)))
                c.drawCircle(sx, sy, br * 0.09f, fill)
            }
        }
        // dance floor tiles light up with the beat
        val fy = h * 0.5f
        val rows = 5
        for (r in 0 until rows) {
            val y0 = fy + (h - fy) * r / rows
            val y1 = fy + (h - fy) * (r + 1) / rows
            for (k in 0 until 6) {
                val lit = ((k + r + (g.time * 2f).toInt()) % 3 == 0)
                val col = colors[(k + r) % 4]
                fill.color = Colors.alpha(col, if (lit) 0.18f + 0.25f * pulse else 0.07f)
                c.drawRect(w * k / 6f + 2f, y0 + 2f, w * (k + 1) / 6f - 2f, y1 - 2f, fill)
            }
        }
        // the pet dances in the middle
        if (look != null) {
            val p = pose.defaults(look, t)
            val phase = beat * 2f * PI.toFloat()
            val side = if (((g.time - 1.6f) / (60f / g.bpm(g.time))).toInt() % 2 == 0) 1f else -1f
            p.lift = abs(sin(phase / 2f)) * 0.14f
            p.tilt = side * 8f * sin(phase / 2f)
            p.armL = if (side > 0) 0.95f else 0.35f
            p.armR = if (side > 0) 0.35f else 0.95f
            p.scaleY *= 1f - 0.06f * pulse
            p.scaleX *= 1f + 0.05f * pulse
            p.eyes = EyeShape.HAPPY
            p.mouth = MouthShape.OPEN
            p.blush = 0.9f
            if (g.time - g.cheerAt < 1.2f) p.eyes = EyeShape.STAR
            if (g.time - g.missAt < 0.5f) {
                p.eyes = EyeShape.DIZZY
                p.mouth = MouthShape.WAVY
                p.tilt += sin(t * 30f) * 6f
            }
            val size = min(w * 0.42f, h * 0.24f)
            renderer.drawShadow(c, w / 2f, h * 0.56f, size, p, look)
            renderer.draw(c, w / 2f, h * 0.56f, size, look, p)
        }
        // lanes
        val hy = hitY(h)
        for (k in 0 until 4) {
            val x = laneX(w, k)
            fill.color = Colors.alpha(colors[k], 0.08f)
            c.drawRect(x - w * 0.085f, h * 0.18f, x + w * 0.085f, h, fill)
            // pad
            val press = pressed[k]
            val r = w * 0.075f * (1f + 0.12f * press)
            fill.alpha = 255
        fill.shader = RadialGradient(x, hy, r, intArrayOf(Colors.alpha(colors[k], 0.35f + 0.5f * press), Colors.alpha(colors[k], 0.1f)), null, Shader.TileMode.CLAMP)
            c.drawCircle(x, hy, r, fill)
            fill.shader = null
            line.color = colors[k]
            line.strokeWidth = w * 0.008f
            c.drawCircle(x, hy, r, line)
        }
        // notes
        val sp = speed(h)
        for (n in g.notes) {
            if (n.hit) continue
            val y = hy - (n.at - g.time) * sp
            if (y < h * 0.15f || y > h + w * 0.1f) continue
            val x = laneX(w, n.lane)
            val r = w * 0.058f
            val col = colors[n.lane]
            val a = if (n.judged) 0.35f else 1f
            fill.alpha = 255
        fill.shader = RadialGradient(x - r * 0.3f, y - r * 0.3f, r * 1.4f, intArrayOf(Colors.alpha(AColor.WHITE, a), Colors.alpha(col, a), Colors.alpha(Colors.darken(col, 0.35f), a)), floatArrayOf(0f, 0.5f, 1f), Shader.TileMode.CLAMP)
            c.drawCircle(x, y, r, fill)
            fill.shader = null
            emoji.textSize = r * 1.05f
            emoji.alpha = (255 * a).toInt()
            c.drawText(symbols[n.lane], x, y + r * 0.38f, emoji)
        }
        emoji.alpha = 255
        // judgement and combo
        val age = g.time - g.labelAt
        if (age < 0.6f) {
            text.typeface = typeface
            text.textSize = w * 0.075f * (1f + 0.25f * (1f - age / 0.6f))
            text.color = Colors.alpha(g.labelColor, 1f - age / 0.6f)
            c.drawText(g.label, w / 2f, h * 0.7f, text)
        }
        if (g.combo >= 3) {
            text.typeface = typeface
            text.textSize = w * 0.05f
            text.color = 0xFFFFE6F4.toInt()
            c.drawText("${g.combo}er Kombo!", w / 2f, h * 0.76f, text)
        }
    }
}

@Composable
fun DanceGame(state: GameState, onFinished: (Int) -> Unit, onBack: () -> Unit) {
    val logic = remember { DanceLogic() }
    val painter = remember { DancePainter() }
    val ps = remember { ParticleSystem() }
    val clock = remember { GameClock() }
    val look = remember { gameLook(state) }
    val time = rememberFrameTime()
    val tf = rememberCanvasTypeface()
    val haptic = rememberHaptic()
    val lastT = remember { floatArrayOf(-1f) }
    val pressed = remember { FloatArray(4) }
    val dims = remember { floatArrayOf(1f, 1f) }

    LaunchedEffect(tf) {
        ps.typeface = tf
        painter.typeface = tf
    }
    LaunchedEffect(logic.over) {
        if (logic.over) {
            clock.finished = true
            Sounds.play(Sfx.FANFARE)
            delay(1000)
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
                        if (clock.running && !logic.over) {
                            val w = dims[0]
                            val h = dims[1]
                            val lane = painter.laneOf(w, down.position.x)
                            pressed[lane] = 1f
                            val q = logic.tap(lane)
                            if (q > 0) {
                                haptic()
                                Sounds.play(Sfx.POP, 0.8f + 0.15f * lane + if (q == 2) 0.1f else 0f)
                                val x = painter.laneX(w, lane) / w
                                val y = painter.hitY(h) / h
                                ps.burst(PKind.STAR, x, y, if (q == 2) 8 else 4, 0.12f, 0.7f, 0.022f, intArrayOf(painter.colors[lane], AColor.WHITE), upward = true)
                                if (logic.combo > 0 && logic.combo % 10 == 0) {
                                    Sounds.play(Sfx.SPARKLE)
                                    ps.burst(PKind.CONFETTI, 0.5f, 0.45f, 30, 0.5f, 1.4f, 0.02f, ParticleSystem.CONFETTI_COLORS, gravity = 0.4f, upward = true)
                                }
                            }
                        }
                    }
                },
        ) {
            val t = time.floatValue
            val dt = if (lastT[0] < 0f) 0f else (t - lastT[0]).coerceIn(0f, 0.05f)
            lastT[0] = t
            val w = size.width
            val h = size.height
            dims[0] = w
            dims[1] = h
            if (clock.running && !logic.over) {
                logic.update(dt, onBeat = { if (logic.time > 1.5f) Sounds.play(Sfx.CLICK, 0.7f) }, onMiss = {})
                if ((t * 4f).toInt() % 3 == 0 && Random.nextFloat() < 0.05f) {
                    ps.add(Particle(PKind.NOTE, 0.5f + (Random.nextFloat() - 0.5f) * 0.3f, 0.42f, (Random.nextFloat() - 0.5f) * 0.05f, -0.08f, 1.4f, 0.035f, painter.colors[Random.nextInt(4)]))
                }
            }
            for (k in 0 until 4) pressed[k] = max(0f, pressed[k] - dt * 5f)
            ps.update(t)
            drawIntoCanvas { canvas ->
                val c = canvas.nativeCanvas
                painter.draw(c, logic, look, t, w, h, pressed)
                ps.draw(c, w, h)
            }
        }
        GameHud(
            onBack = onBack,
            score = "${logic.score}",
            timeFraction = 1f - (logic.time / logic.song).coerceIn(0f, 1f),
            combo = logic.combo,
            modifier = Modifier.align(Alignment.TopCenter),
        )
        if (!clock.running && !clock.finished) {
            CountdownOverlay("Tippe auf die Spalte, wenn die Note den Kreis unten erreicht!") { clock.running = true }
        }
    }
}

// ====================================================================================== snowball fight

private enum class TargetKind(val emoji: String, val points: Int) { SNOWMAN("⛄", 1), GOLDEN("🌟", 3), PENGUIN("🐧", 0) }

private class Target(val kind: TargetKind, val row: Int, val x: Float, val born: Float, val stay: Float) {
    var hitAt = -1f
    fun up(t: Float): Float {
        val age = t - born
        if (hitAt >= 0f) return (1f - (t - hitAt) / 0.35f).coerceIn(0f, 1f)
        return when {
            age < 0.25f -> age / 0.25f
            age > stay - 0.25f -> ((stay - age) / 0.25f).coerceIn(0f, 1f)
            else -> 1f
        }
    }
    fun gone(t: Float) = (hitAt >= 0f && t - hitAt > 0.4f) || t - born > stay
}

private class Ball(val sx: Float, val sy: Float, val tx: Float, val ty: Float, val start: Float, val dur: Float) {
    var done = false
}

private class SnowLogic {
    val length = 40f
    var time = 0f
    var score by mutableIntStateOf(0)
    var lives by mutableIntStateOf(3)
    var over by mutableStateOf(false)
    val targets = ArrayList<Target>()
    val balls = ArrayList<Ball>()
    var throwAt = -10f
    var ouchAt = -10f
    private var nextSpawn = 0.6f
    private val rnd = Random(System.nanoTime())

    fun update(dt: Float) {
        time += dt
        val stage = (time / length).coerceIn(0f, 1f)
        if (time >= nextSpawn && time < length - 1f) {
            val busy = targets.filter { it.hitAt < 0f }.map { it.row to it.x }
            var tries = 0
            while (tries++ < 6) {
                val row = rnd.nextInt(3)
                val x = 0.15f + rnd.nextFloat() * 0.7f
                if (busy.any { it.first == row && abs(it.second - x) < 0.18f }) continue
                val r = rnd.nextFloat()
                val kind = when {
                    r < 0.08f + 0.04f * stage -> TargetKind.GOLDEN
                    r < 0.3f -> TargetKind.PENGUIN
                    else -> TargetKind.SNOWMAN
                }
                val stay = (2.1f - 0.9f * stage) * if (kind == TargetKind.GOLDEN) 0.7f else 1f
                targets += Target(kind, row, x, time, stay)
                break
            }
            nextSpawn = time + (0.9f - 0.45f * stage) * (0.7f + rnd.nextFloat() * 0.6f)
        }
        targets.removeAll { it.gone(time) }
        if ((time >= length || lives <= 0) && !over) over = true
    }
}

private class SnowPainter {
    val renderer = PetRenderer()
    val pose = PetPose()
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val emoji = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER }
    private val path = Path()
    private val flakes = Random(5).let { r -> FloatArray(90) { r.nextFloat() } }

    /** Where the walls of row 0 (far), 1, 2 (near) stand. */
    fun wallY(h: Float, row: Int) = h * (0.42f + 0.13f * row)
    fun scale(row: Int) = 0.6f + 0.25f * row
    fun handX(w: Float) = w * 0.56f
    fun handY(h: Float) = h * 0.8f

    fun targetAt(g: SnowLogic, w: Float, h: Float, x: Float, y: Float): Target? {
        var best: Target? = null
        var bestD = Float.MAX_VALUE
        for (tg in g.targets) {
            if (tg.hitAt >= 0f || tg.up(g.time) < 0.5f) continue
            val tx = tg.x * w
            val s = w * 0.12f * scale(tg.row)
            val ty = wallY(h, tg.row) - s * 0.55f
            val d = hypot(x - tx, y - ty)
            if (d < s * 0.75f && d < bestD) {
                bestD = d
                best = tg
            }
        }
        return best
    }

    fun draw(c: NativeCanvas, g: SnowLogic, look: PetLook?, t: Float, w: Float, h: Float) {
        fill.alpha = 255
        fill.shader = LinearGradient(0f, 0f, 0f, h * 0.5f, 0xFF9CCBFF.toInt(), 0xFFE8F4FF.toInt(), Shader.TileMode.CLAMP)
        c.drawRect(0f, 0f, w, h, fill)
        fill.shader = null
        // far hills
        fill.color = 0xFFF4FAFF.toInt()
        c.drawOval(-w * 0.3f, h * 0.3f, w * 0.7f, h * 0.55f, fill)
        fill.color = 0xFFE4F0FC.toInt()
        c.drawOval(w * 0.3f, h * 0.28f, w * 1.3f, h * 0.55f, fill)
        // little pine trees
        for (k in 0 until 6) {
            val x = w * (0.08f + 0.17f * k)
            val y = h * 0.36f + (k % 2) * h * 0.015f
            fill.color = 0xFF4A9A7A.toInt()
            path.reset()
            path.moveTo(x, y - h * 0.07f)
            path.lineTo(x + w * 0.04f, y)
            path.lineTo(x - w * 0.04f, y)
            path.close()
            c.drawPath(path, fill)
            fill.color = AColor.WHITE
            c.drawCircle(x, y - h * 0.065f, w * 0.012f, fill)
        }
        // ground
        fill.alpha = 255
        fill.shader = LinearGradient(0f, h * 0.38f, 0f, h, 0xFFF8FCFF.toInt(), 0xFFD8E8F8.toInt(), Shader.TileMode.CLAMP)
        c.drawRect(0f, h * 0.38f, w, h, fill)
        fill.shader = null
        // rows from far to near: targets behind each wall
        for (row in 0..2) {
            val wy = wallY(h, row)
            for (tg in g.targets) {
                if (tg.row != row) continue
                val up = tg.up(g.time)
                if (up <= 0f) continue
                val s = w * 0.12f * scale(row)
                val x = tg.x * w
                val y = wy - s * 0.55f * up + s * 0.45f * (1f - up)
                c.save()
                if (tg.hitAt >= 0f) c.rotate((g.time - tg.hitAt) * 160f * (if (tg.x > 0.5f) 1f else -1f), x, y)
                if (tg.kind == TargetKind.GOLDEN) {
                    fill.alpha = 255
        fill.shader = RadialGradient(x, y, s, intArrayOf(Colors.alpha(0xFFFFE066.toInt(), 0.6f), Colors.alpha(0xFFFFE066.toInt(), 0f)), null, Shader.TileMode.CLAMP)
                    c.drawCircle(x, y, s, fill)
                    fill.shader = null
                }
                emoji.textSize = s
                c.drawText(tg.kind.emoji, x, y + s * 0.36f, emoji)
                c.restore()
            }
            // the snow wall in front of this row
            val ww = h * 0.06f * scale(row)
            fill.alpha = 255
        fill.shader = LinearGradient(0f, wy - ww * 0.4f, 0f, wy + ww, AColor.WHITE, 0xFFC8DCF0.toInt(), Shader.TileMode.CLAMP)
            path.reset()
            path.moveTo(0f, wy + ww)
            var x = 0f
            while (x <= w) {
                path.lineTo(x, wy + sin(x / w * 9f + row * 2f) * ww * 0.25f)
                x += w / 20f
            }
            path.lineTo(w, wy + ww)
            path.close()
            c.drawPath(path, fill)
            fill.shader = null
        }
        // the pet throws from the front
        if (look != null) {
            val p = pose.defaults(look, t)
            val since = t - g.throwAt
            if (since < 0.35f) {
                val k = since / 0.35f
                p.armR = 1f - k * 0.6f
                p.armWaveR = -40f * (1f - k)
                p.lean()
                p.eyes = EyeShape.HAPPY
                p.mouth = MouthShape.OPEN
            } else {
                p.holding = "⚪"
                p.hold = 0.6f
                p.lookY = -0.6f
            }
            if (t - g.ouchAt < 0.8f) {
                p.eyes = EyeShape.WIDE
                p.mouth = MouthShape.O
                p.mouthOpen = 1f
            }
            val size = min(w * 0.36f, h * 0.2f)
            renderer.drawShadow(c, w * 0.5f, h * 0.97f, size, p, look)
            renderer.draw(c, w * 0.5f, h * 0.97f, size, look, p)
        }
        // flying snowballs
        for (b in g.balls) {
            if (b.done) continue
            val k = ((t - b.start) / b.dur).coerceIn(0f, 1f)
            val x = b.sx + (b.tx - b.sx) * k
            val y = b.sy + (b.ty - b.sy) * k - sin(k * PI.toFloat()) * h * 0.12f
            val r = w * (0.035f - 0.018f * k)
            fill.alpha = 255
        fill.shader = RadialGradient(x - r * 0.3f, y - r * 0.3f, r * 1.3f, intArrayOf(AColor.WHITE, 0xFFE4F0FC.toInt(), 0xFFA8C0D8.toInt()), floatArrayOf(0f, 0.6f, 1f), Shader.TileMode.CLAMP)
            c.drawCircle(x, y, r, fill)
            fill.shader = null
        }
        // falling snow
        fill.color = Colors.alpha(AColor.WHITE, 0.85f)
        for (i in 0 until 30) {
            val fx = (flakes[i * 3] * w + sin(t * 0.8f + i) * w * 0.02f)
            val fy = ((flakes[i * 3 + 1] + t * (0.04f + 0.04f * flakes[i * 3 + 2])) % 1f) * h
            c.drawCircle(fx, fy, w * (0.004f + 0.005f * flakes[i * 3 + 2]), fill)
        }
    }

    private fun PetPose.lean() {
        tilt += -6f
    }
}

@Composable
fun SnowballGame(state: GameState, onFinished: (Int) -> Unit, onBack: () -> Unit) {
    val logic = remember { SnowLogic() }
    val painter = remember { SnowPainter() }
    val ps = remember { ParticleSystem() }
    val shake = remember { Shake() }
    val clock = remember { GameClock() }
    val look = remember { gameLook(state) }
    val time = rememberFrameTime()
    val tf = rememberCanvasTypeface()
    val haptic = rememberHaptic()
    val lastT = remember { floatArrayOf(-1f) }
    val dims = remember { floatArrayOf(1f, 1f) }

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
                        val t = time.floatValue
                        if (clock.running && !logic.over && t - logic.throwAt > 0.25f) {
                            val w = dims[0]
                            val h = dims[1]
                            logic.throwAt = t
                            logic.balls += Ball(painter.handX(w), painter.handY(h), down.position.x, down.position.y, t, 0.42f)
                            Sounds.play(Sfx.WHOOSH)
                        }
                    }
                },
        ) {
            val t = time.floatValue
            val dt = if (lastT[0] < 0f) 0f else (t - lastT[0]).coerceIn(0f, 0.05f)
            lastT[0] = t
            val w = size.width
            val h = size.height
            dims[0] = w
            dims[1] = h
            if (clock.running && !logic.over) {
                logic.update(dt)
                for (b in logic.balls) {
                    if (b.done || t - b.start < b.dur) continue
                    b.done = true
                    val tg = painter.targetAt(logic, w, h, b.tx, b.ty)
                    ps.burst(PKind.SPARKLE, b.tx / w, b.ty / h, 8, 0.08f, 0.6f, 0.02f, intArrayOf(AColor.WHITE, 0xFFD8ECFF.toInt()), gravity = 0.6f)
                    if (tg != null) {
                        tg.hitAt = logic.time
                        when (tg.kind) {
                            TargetKind.PENGUIN -> {
                                logic.lives -= 1
                                logic.ouchAt = t
                                shake.kick(t, w * 0.02f)
                                haptic()
                                Sounds.play(Sfx.BONK)
                                ps.add(Particle(PKind.TEXT, b.tx / w, b.ty / h - 0.04f, 0f, -0.06f, 1.1f, 0.05f, 0xFF4C8DFF.toInt(), "Hey! 🐧"))
                            }
                            else -> {
                                logic.score += tg.kind.points
                                haptic()
                                Sounds.play(if (tg.kind == TargetKind.GOLDEN) Sfx.SPARKLE else Sfx.POP)
                                ps.add(Particle(PKind.TEXT, b.tx / w, b.ty / h - 0.04f, 0f, -0.06f, 0.9f, 0.05f, if (tg.kind == TargetKind.GOLDEN) 0xFFFFB800.toInt() else 0xFF2A5AC8.toInt(), "+${tg.kind.points}"))
                                if (tg.kind == TargetKind.GOLDEN) ps.burst(PKind.STAR, b.tx / w, b.ty / h, 10, 0.2f, 0.9f, 0.025f, ParticleSystem.STAR_COLORS, upward = true)
                            }
                        }
                    }
                }
                logic.balls.removeAll { it.done }
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
            score = "${logic.score}",
            lives = logic.lives,
            maxLives = 3,
            timeFraction = 1f - (logic.time / logic.length).coerceIn(0f, 1f),
            modifier = Modifier.align(Alignment.TopCenter),
        )
        if (!clock.running && !clock.finished) {
            CountdownOverlay("Tippe auf die Schneemänner! Goldene geben 3 Punkte. Die Pinguine wollen nur zuschauen.") { clock.running = true }
        }
    }
}
