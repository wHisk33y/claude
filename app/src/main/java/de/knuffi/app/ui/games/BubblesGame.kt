package de.knuffi.app.ui.games

import android.graphics.Canvas as NativeCanvas
import android.graphics.Color as AColor
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.Shader
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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

/** Bubble colours with a symbol each, so the game also works without telling colours apart. */
private class BubbleColor(val name: String, val color: Int, val symbol: String)

private val BUBBLE_COLORS = listOf(
    BubbleColor("Rot", 0xFFFF5A7A.toInt(), "🍓"),
    BubbleColor("Blau", 0xFF4CA8FF.toInt(), "💧"),
    BubbleColor("Grün", 0xFF4CCB7A.toInt(), "🍀"),
    BubbleColor("Gelb", 0xFFFFC83D.toInt(), "🍋"),
    BubbleColor("Lila", 0xFFB27CFF.toInt(), "🍇"),
)

private class Bubble(var x: Float, var y: Float, val r: Float, val vy: Float, val color: Int, val golden: Boolean, val phase: Float)

private class BubbleLogic {
    var popped by mutableIntStateOf(0)
    var lives by mutableIntStateOf(3)
    var combo by mutableIntStateOf(0)
    var target by mutableIntStateOf(0)
    var timeLeft by mutableFloatStateOf(DURATION)
    var over by mutableStateOf(false)
    var targetAt = 0f
    var popAt = -10f
    var missAt = -10f
    val bubbles = ArrayList<Bubble>()
    private var elapsed = 0f
    private var nextSpawn = 0.2f
    private var nextTarget = TARGET_TIME
    private val rnd = Random(System.nanoTime())

    fun update(t: Float, dt: Float) {
        if (over) return
        elapsed += dt
        val left = max(0f, DURATION - elapsed)
        if (abs(left - timeLeft) >= 0.1f || left == 0f) timeLeft = left
        if (elapsed >= DURATION) {
            over = true
            return
        }
        nextTarget -= dt
        if (nextTarget <= 0f) {
            var n = rnd.nextInt(BUBBLE_COLORS.size)
            if (n == target) n = (n + 1) % BUBBLE_COLORS.size
            target = n
            targetAt = t
            nextTarget = TARGET_TIME
        }
        nextSpawn -= dt
        if (nextSpawn <= 0f) {
            val speedUp = 1f + elapsed / DURATION
            nextSpawn = (0.62f + rnd.nextFloat() * 0.35f) / speedUp
            val golden = rnd.nextFloat() < 0.05f
            val color = if (rnd.nextFloat() < 0.42f) target else rnd.nextInt(BUBBLE_COLORS.size)
            val r = 0.07f + rnd.nextFloat() * 0.035f
            bubbles += Bubble(0.12f + rnd.nextFloat() * 0.76f, 1.1f, r, (0.11f + rnd.nextFloat() * 0.06f) * speedUp, color, golden, rnd.nextFloat() * 6f)
        }
        val it = bubbles.iterator()
        while (it.hasNext()) {
            val b = it.next()
            b.y -= b.vy * dt
            b.x += sin(t * 1.8f + b.phase) * 0.02f * dt
            if (b.y < -0.12f) it.remove()
        }
    }

    /** Handles a tap at normalised coordinates. */
    fun tap(nx: Float, ny: Float, aspect: Float, t: Float, ps: ParticleSystem, shake: Shake) {
        if (over) return
        // aspect = width / height: bubble radius is in widths
        val hit = bubbles.lastOrNull { b ->
            val dx = nx - b.x
            val dy = (ny - b.y) / aspect
            dx * dx + dy * dy < (b.r * 1.15f) * (b.r * 1.15f)
        } ?: return
        bubbles.remove(hit)
        val col = BUBBLE_COLORS[hit.color]
        when {
            hit.golden -> {
                popped += 3
                combo += 1
                popAt = t
                ps.burst(PKind.STAR, hit.x, hit.y, 14, 0.5f, 1f, 0.03f, ParticleSystem.STAR_COLORS, gravity = 0.3f)
                ps.add(Particle(PKind.TEXT, hit.x, hit.y - 0.03f, 0f, -0.08f, 1f, 0.05f, 0xFFFFC23D.toInt(), text = "+3 ⭐"))
            }
            hit.color == target -> {
                popped += 1
                combo += 1
                popAt = t
                ps.burst(PKind.BUBBLE, hit.x, hit.y, 10, 0.35f, 0.7f, 0.018f, intArrayOf(col.color, 0xFFFFFFFF.toInt()))
                ps.burst(PKind.SPARKLE, hit.x, hit.y, 6, 0.3f, 0.6f, 0.015f, ParticleSystem.SPARKLE_COLORS)
                if (combo > 0 && combo % 8 == 0) {
                    lives = min(5, lives + 1)
                    ps.add(Particle(PKind.TEXT, 0.5f, 0.35f, 0f, -0.04f, 1.2f, 0.055f, 0xFFFF5C9A.toInt(), text = "Super-Serie! +1 ❤️"))
                    ps.burst(PKind.HEART, 0.5f, 0.35f, 10, 0.4f, 1.2f, 0.03f, ParticleSystem.HEART_COLORS, upward = true)
                }
            }
            else -> {
                lives -= 1
                combo = 0
                missAt = t
                shake.kick(t, 18f, 0.3f)
                ps.burst(PKind.SMOKE, hit.x, hit.y, 6, 0.1f, 0.8f, 0.05f, intArrayOf(0x886B6B7A.toInt()), drag = 2f)
                ps.add(Particle(PKind.TEXT, hit.x, hit.y - 0.03f, 0f, -0.07f, 1f, 0.045f, 0xFFFF5A6E.toInt(), text = "Falsche Farbe! −❤️"))
                if (lives <= 0) over = true
            }
        }
    }

    companion object {
        const val DURATION = 45f
        const val TARGET_TIME = 8f
    }
}

private class BubblePainter {
    val renderer = PetRenderer()
    val pose = PetPose()
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val emoji = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER }
    private val path = Path()
    private var seaH = -1f
    private var sea: Shader? = null

    fun drawWorld(c: NativeCanvas, w: Float, h: Float, t: Float) {
        if (seaH != h) {
            seaH = h
            sea = LinearGradient(0f, 0f, 0f, h, intArrayOf(0xFF7EE0F0.toInt(), 0xFF2EA8D6.toInt(), 0xFF155C9A.toInt()), floatArrayOf(0f, 0.5f, 1f), Shader.TileMode.CLAMP)
        }
        fill.color = AColor.WHITE
        fill.shader = sea
        c.drawRect(0f, 0f, w, h, fill)
        fill.shader = null
        // light rays from the surface
        for (i in 0 until 5) {
            val x = w * (0.1f + i * 0.22f) + sin(t * 0.4f + i) * w * 0.04f
            path.reset()
            path.moveTo(x - w * 0.03f, 0f)
            path.lineTo(x + w * 0.03f, 0f)
            path.lineTo(x + w * 0.12f, h * 0.8f)
            path.lineTo(x - w * 0.02f, h * 0.8f)
            path.close()
            fill.color = Colors.alpha(AColor.WHITE, 0.07f + 0.03f * sin(t + i))
            c.drawPath(path, fill)
        }
        // sand & plants
        fill.color = AColor.WHITE
        fill.shader = LinearGradient(0f, h * 0.88f, 0f, h, 0xFFF4DCA0.toInt(), 0xFFD8B070.toInt(), Shader.TileMode.CLAMP)
        path.reset()
        path.moveTo(0f, h)
        path.lineTo(0f, h * 0.9f)
        path.quadTo(w * 0.3f, h * 0.86f, w * 0.55f, h * 0.9f)
        path.quadTo(w * 0.8f, h * 0.93f, w, h * 0.88f)
        path.lineTo(w, h)
        path.close()
        c.drawPath(path, fill)
        fill.shader = null
        stroke.strokeCap = Paint.Cap.ROUND
        for (i in 0 until 6) {
            val bx = w * (0.05f + i * 0.18f)
            val by = h * 0.92f
            stroke.strokeWidth = w * 0.018f
            stroke.color = if (i % 2 == 0) 0xFF3EAE6A.toInt() else 0xFF2E8E58.toInt()
            path.reset()
            path.moveTo(bx, by)
            val sway = sin(t * 1.4f + i) * w * 0.03f
            path.quadTo(bx + sway, by - h * 0.08f, bx - sway * 0.5f, by - h * (0.12f + (i % 3) * 0.03f))
            c.drawPath(path, stroke)
        }
        emoji.textSize = w * 0.08f
        c.drawText("🐚", w * 0.84f, h * 0.95f, emoji)
        c.drawText("⭐", w * 0.12f, h * 0.97f, emoji)
    }

    fun drawBubble(c: NativeCanvas, b: Bubble, w: Float, h: Float, t: Float) {
        val x = b.x * w
        val y = b.y * h
        val r = b.r * w * (1f + 0.03f * sin(t * 5f + b.phase))
        val col = if (b.golden) 0xFFFFD84D.toInt() else BUBBLE_COLORS[b.color].color
        fill.color = AColor.WHITE
        fill.shader = RadialGradient(
            x - r * 0.35f, y - r * 0.35f, r * 1.5f,
            intArrayOf(Colors.alpha(AColor.WHITE, 0.85f), Colors.alpha(col, 0.5f), Colors.alpha(col, 0.85f)),
            floatArrayOf(0f, 0.55f, 1f), Shader.TileMode.CLAMP,
        )
        c.drawCircle(x, y, r, fill)
        fill.shader = null
        stroke.strokeWidth = r * 0.07f
        stroke.color = Colors.alpha(AColor.WHITE, 0.7f)
        c.drawCircle(x, y, r, stroke)
        fill.color = Colors.alpha(AColor.WHITE, 0.9f)
        c.drawOval(x - r * 0.6f, y - r * 0.7f, x - r * 0.1f, y - r * 0.35f, fill)
        emoji.textSize = r * 0.9f
        emoji.alpha = 235
        c.drawText(if (b.golden) "⭐" else BUBBLE_COLORS[b.color].symbol, x, y + r * 0.32f, emoji)
        emoji.alpha = 255
    }

    fun drawPet(c: NativeCanvas, look: PetLook, logic: BubbleLogic, t: Float, px: Float, groundY: Float, petSize: Float) {
        val p = pose.defaults(look, t)
        p.mouth = MouthShape.O
        p.mouthOpen = 0.5f + 0.5f * abs(sin(t * 3f))
        p.lookY = -1f
        p.lookX = sin(t * 0.7f) * 0.8f
        p.lift = abs(sin(t * 1.5f)) * 0.08f
        if (t - logic.popAt < 0.35f) {
            p.eyes = EyeShape.HAPPY
            p.mouth = MouthShape.GRIN
            p.armL = 1f
            p.armR = 1f
        }
        if (logic.combo >= 8 && t - logic.popAt < 1f) p.eyes = EyeShape.STAR
        if (t - logic.missAt < 0.8f) {
            p.eyes = EyeShape.DIZZY
            p.mouth = MouthShape.WAVY
        }
        renderer.drawShadow(c, px, groundY, petSize, p, look)
        renderer.draw(c, px, groundY, petSize, look, p)
    }
}

@Composable
fun BubblesGame(state: GameState, onFinished: (Int) -> Unit, onBack: () -> Unit) {
    val logic = remember { BubbleLogic() }
    val painter = remember { BubblePainter() }
    val ps = remember { ParticleSystem() }
    val shake = remember { Shake() }
    val clock = remember { GameClock() }
    val look = remember { gameLook(state) }
    val time = rememberFrameTime()
    val tf = rememberCanvasTypeface()
    val lastT = remember { floatArrayOf(-1f) }
    val aspect = remember { floatArrayOf(0.5f) }

    LaunchedEffect(tf) { ps.typeface = tf }
    LaunchedEffect(logic.over) {
        if (logic.over) {
            clock.finished = true
            delay(900)
            onFinished(logic.popped)
        }
    }

    Box(Modifier.fillMaxSize().clipToBounds()) {
        Canvas(
            Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectTapGestures { pos ->
                        if (clock.running) {
                            logic.tap(pos.x / size.width, pos.y / size.height, aspect[0], time.floatValue, ps, shake)
                        }
                    }
                },
        ) {
            val t = time.floatValue
            val dt = if (lastT[0] < 0f) 0f else (t - lastT[0]).coerceIn(0f, 0.05f)
            lastT[0] = t
            val w = size.width
            val h = size.height
            aspect[0] = w / h
            if (clock.running) logic.update(t, dt)
            ps.update(t)
            val off = shake.offset(t)
            drawIntoCanvas { canvas ->
                val c = canvas.nativeCanvas
                c.save()
                c.translate(off.x, off.y)
                painter.drawWorld(c, w, h, t)
                val petSize = min(w * 0.28f, h * 0.15f)
                val l = look
                if (l != null) painter.drawPet(c, l, logic, t, w * 0.5f, h * 0.95f, petSize)
                for (b in logic.bubbles) painter.drawBubble(c, b, w, h, t)
                ps.draw(c, w, h)
                c.restore()
            }
        }
        Box(Modifier.align(Alignment.TopCenter)) {
            GameHud(
                onBack = onBack,
                score = "${logic.popped}",
                lives = logic.lives,
                maxLives = max(3, logic.lives),
                timeFraction = logic.timeLeft / BubbleLogic.DURATION,
                timeText = "${logic.timeLeft.toInt()}s",
                combo = if (logic.combo >= 3) logic.combo else 0,
            )
        }
        TargetBanner(logic, time.floatValue, Modifier.align(Alignment.TopCenter))
        if (!clock.running && !clock.finished) {
            CountdownOverlay("Lass nur die Blasen in der gesuchten Farbe platzen!") { clock.running = true }
        }
    }
}

@Composable
private fun TargetBanner(logic: BubbleLogic, t: Float, modifier: Modifier = Modifier) {
    val col = BUBBLE_COLORS[logic.target]
    val since = t - logic.targetAt
    val pop = if (since < 0.5f) 1f + 0.25f * sin(since / 0.5f * Math.PI.toFloat()) else 1f
    Row(
        modifier
            .statusBarsPadding()
            .padding(top = 96.dp)
            .graphicsLayer {
                scaleX = pop
                scaleY = pop
            }
            .clip(RoundedCornerShape(50))
            .background(Color(col.color))
            .border(2.dp, Color.White.copy(alpha = 0.8f), RoundedCornerShape(50))
            .padding(horizontal = 18.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(col.symbol, fontSize = 22.sp)
        Spacer(Modifier.width(8.dp))
        Text("Platze: ${col.name}", style = MaterialTheme.typography.titleMedium, color = Color.White)
    }
}
