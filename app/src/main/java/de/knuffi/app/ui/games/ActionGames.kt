package de.knuffi.app.ui.games

import android.graphics.Paint
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.knuffi.app.render.EyeShape
import de.knuffi.app.render.MouthShape
import de.knuffi.app.render.PKind
import de.knuffi.app.render.Particle
import de.knuffi.app.render.ParticleSystem
import de.knuffi.app.render.PetPose
import de.knuffi.app.render.PetRenderer
import de.knuffi.app.render.ToyRenderer
import de.knuffi.app.sound.Sfx
import de.knuffi.app.sound.Sounds
import de.knuffi.app.ui.PetPortrait
import de.knuffi.app.ui.components.lighter
import de.knuffi.app.ui.components.rememberHaptic
import de.knuffi.app.ui.rememberCanvasTypeface
import de.knuffi.app.ui.rememberFrameTime
import de.knuffi.core.GameState
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

/** Draws an emoji on a canvas. */
internal fun DrawScope.emoji(paint: Paint, text: String, x: Float, y: Float, size: Float, alpha: Float = 1f) {
    paint.textSize = size
    paint.alpha = (alpha.coerceIn(0f, 1f) * 255).toInt()
    drawIntoCanvas { it.nativeCanvas.drawText(text, x, y + size * 0.35f, paint) }
    paint.alpha = 255
}

internal fun emojiPaint() = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER }

// ================================================================== Kochstudio

private val INGREDIENTS = listOf("🥕", "🎃", "🍓", "🫐", "🍎", "🧀", "🍅", "🥛", "🌾", "🐟", "🍯", "🥚")
private val DISHES = listOf("🥣", "🍲", "🥞", "🧁", "🍝", "🍕", "🥗", "🍱")

@Composable
fun CookGame(state: GameState, onFinished: (Int) -> Unit, onBack: () -> Unit) {
    val look = remember { gameLook(state)?.copy(hat = "hat_chef") }
    val haptic = rememberHaptic()
    val scope = rememberCoroutineScope()
    val time = rememberFrameTime()
    val ps = remember { ParticleSystem() }
    val tf = rememberCanvasTypeface()
    LaunchedEffect(tf) { ps.typeface = tf }
    val rnd = remember { Random(System.nanoTime()) }
    val duration = 60f
    var started by remember { mutableStateOf(false) }
    var startAt by remember { mutableFloatStateOf(0f) }
    var penalty by remember { mutableFloatStateOf(0f) }
    var score by remember { mutableIntStateOf(0) }
    var combo by remember { mutableIntStateOf(0) }
    val recipe = remember { mutableStateListOf<String>() }
    var step by remember { mutableIntStateOf(0) }
    val shelf = remember { mutableStateListOf<String>() }
    var mood by remember { mutableIntStateOf(0) } // 1 happy, -1 oops
    var done by remember { mutableStateOf(false) }
    var dish by remember { mutableStateOf<String?>(null) }

    fun newRecipe() {
        val n = (3 + score / 2).coerceAtMost(6)
        val pick = INGREDIENTS.shuffled(rnd).take(8)
        recipe.clear()
        repeat(n) { recipe += pick[rnd.nextInt(min(5, pick.size))] }
        shelf.clear()
        shelf.addAll(pick.shuffled(rnd))
        step = 0
    }

    val t = time.floatValue
    val left = if (started) (duration - (t - startAt) - penalty).coerceAtLeast(0f) else duration
    LaunchedEffect(left <= 0f, started) {
        if (started && left <= 0f && !done) {
            done = true
            delay(600)
            onFinished(score)
        }
    }

    fun tap(i: Int) {
        if (!started || done) return
        val ing = shelf.getOrNull(i) ?: return
        haptic()
        if (ing == recipe.getOrNull(step)) {
            step++
            Sounds.play(Sfx.POP)
            ps.add(Particle(PKind.EMOJI, 0.5f, 0.42f, 0f, -0.05f, 0.8f, 0.07f, 0, text = ing))
            mood = 1
            if (step >= recipe.size) {
                score++
                combo++
                Sounds.play(Sfx.SUCCESS)
                dish = DISHES[rnd.nextInt(DISHES.size)]
                ps.burst(PKind.STAR, 0.5f, 0.42f, 10, 0.4f, 1f, 0.025f, ParticleSystem.STAR_COLORS, gravity = 0.3f, upward = true)
                scope.launch {
                    delay(700)
                    dish = null
                    newRecipe()
                }
            }
        } else {
            combo = 0
            penalty += 3f
            mood = -1
            Sounds.play(Sfx.BONK)
            ps.burst(PKind.SMOKE, 0.5f, 0.45f, 5, 0.15f, 0.8f, 0.04f, intArrayOf(0x88808080.toInt()), gravity = -0.2f)
        }
    }

    Box(Modifier.fillMaxSize()) {
        val paint = remember { emojiPaint() }
        Canvas(Modifier.fillMaxSize()) {
            ps.update(t)
            drawRect(Brush.verticalGradient(listOf(Color(0xFFFFF1DC), Color(0xFFFFD6B0))))
            // tiles
            val tile = size.width / 8f
            for (y in 0..((size.height * 0.45f) / tile).toInt()) for (x in 0..8) {
                if ((x + y) % 2 == 0) drawRect(Color(0x14C2551F), Offset(x * tile, y * tile), Size(tile, tile))
            }
            // counter
            drawRoundRect(Brush.verticalGradient(listOf(Color(0xFFB9824E), Color(0xFF8A5A30))), Offset(-20f, size.height * 0.5f), Size(size.width + 40f, size.height * 0.08f), CornerRadius(20f))
            // pot
            val px = size.width * 0.5f
            val py = size.height * 0.46f
            val pr = size.width * 0.16f
            drawOval(Color(0x33000000), Offset(px - pr * 1.1f, py + pr * 0.55f), Size(pr * 2.2f, pr * 0.4f))
            drawRoundRect(Brush.verticalGradient(listOf(Color(0xFFB8C4DC), Color(0xFF6E7A96))), Offset(px - pr, py - pr * 0.4f), Size(pr * 2f, pr * 1.1f), CornerRadius(pr * 0.3f))
            drawOval(Brush.radialGradient(listOf(Color(0xFFFFB86A), Color(0xFFE07A2E)), center = Offset(px, py - pr * 0.4f), radius = pr), Offset(px - pr * 0.92f, py - pr * 0.6f), Size(pr * 1.84f, pr * 0.4f))
            for (k in 0 until 3) {
                val ph = (t * 0.7f + k * 0.33f) % 1f
                drawCircle(Color.White.copy(alpha = 0.5f * (1f - ph)), radius = pr * 0.12f * (0.5f + ph), center = Offset(px + (k - 1) * pr * 0.4f + sin(t * 3f + k) * 6f, py - pr * 0.6f - ph * pr * 1.2f))
            }
            dish?.let { emoji(paint, it, px, py - pr * 1.4f, pr * 1.2f) }
            drawIntoCanvas { ps.draw(it.nativeCanvas, size.width, size.height) }
        }
        Column(Modifier.fillMaxSize().navigationBarsPadding().padding(horizontal = 16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Spacer(Modifier.height(140.dp))
            // recipe card
            Row(
                Modifier
                    .shadow(6.dp, RoundedCornerShape(20.dp))
                    .clip(RoundedCornerShape(20.dp))
                    .background(Color.White)
                    .padding(horizontal = 14.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("📋 ", fontSize = 20.sp)
                for ((i, r) in recipe.withIndex()) {
                    val doneStep = i < step
                    Text(
                        r,
                        fontSize = if (i == step) 30.sp else 24.sp,
                        modifier = Modifier
                            .padding(horizontal = 3.dp)
                            .graphicsLayer { alpha = if (doneStep) 0.3f else 1f },
                    )
                }
            }
            Spacer(Modifier.weight(1f))
            PetPortrait(look, Modifier.size(120.dp), sizeFactor = 0.8f) { pose, tt ->
                pose.mouth = if (mood < 0) MouthShape.WAVY else MouthShape.OPEN
                pose.eyes = if (mood < 0) EyeShape.DIZZY else EyeShape.HAPPY
                pose.armR = 0.6f + 0.3f * sin(tt * 10f)
            }
            Spacer(Modifier.height(170.dp))
            for (row in 0..1) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    for (col in 0..3) {
                        val i = row * 4 + col
                        Box(
                            Modifier
                                .weight(1f)
                                .aspectRatio(1f)
                                .shadow(4.dp, RoundedCornerShape(18.dp))
                                .clip(RoundedCornerShape(18.dp))
                                .background(Brush.verticalGradient(listOf(Color.White, Color(0xFFFFEAD6))))
                                .border(2.dp, Color(0xFFE8B888), RoundedCornerShape(18.dp))
                                .pointerInput(i) { detectTapGestures { tap(i) } },
                            contentAlignment = Alignment.Center,
                        ) { Text(shelf.getOrNull(i) ?: "", fontSize = 32.sp) }
                    }
                }
                Spacer(Modifier.height(10.dp))
            }
            Spacer(Modifier.height(16.dp))
        }
        Box(Modifier.align(Alignment.TopCenter)) {
            GameHud(onBack = onBack, score = "$score", timeFraction = left / duration, timeText = "${left.toInt()}s", combo = combo, light = false)
        }
        if (!started) {
            CountdownOverlay("Tippe die Zutaten in der Reihenfolge des Rezepts!") {
                newRecipe()
                startAt = time.floatValue
                started = true
            }
        }
    }
}

// ================================================================== Papierflug

private class Ring(var x: Float, val y: Float, val gap: Float, var passed: Boolean = false, val cloud: Boolean = false)

@Composable
fun FlightGame(state: GameState, onFinished: (Int) -> Unit, onBack: () -> Unit) {
    val time = rememberFrameTime()
    val art = remember { ToyRenderer() }
    val haptic = rememberHaptic()
    val ps = remember { ParticleSystem() }
    val tf = rememberCanvasTypeface()
    LaunchedEffect(tf) { ps.typeface = tf }
    val rnd = remember { Random(System.nanoTime()) }
    val rings = remember { mutableListOf<Ring>() }
    var y by remember { mutableFloatStateOf(0.45f) }
    var vy by remember { mutableFloatStateOf(0f) }
    var last by remember { mutableFloatStateOf(-1f) }
    var spawnAt by remember { mutableFloatStateOf(0f) }
    var score by remember { mutableIntStateOf(0) }
    var lives by remember { mutableIntStateOf(3) }
    var started by remember { mutableStateOf(false) }
    var over by remember { mutableStateOf(false) }
    var hurtUntil by remember { mutableFloatStateOf(0f) }
    val shake = remember { Shake() }
    val paint = remember { emojiPaint() }
    LaunchedEffect(over) {
        if (over) {
            delay(900)
            onFinished(score)
        }
    }
    Box(
        Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectTapGestures {
                    if (started && !over) {
                        vy = -0.55f
                        haptic()
                        Sounds.play(Sfx.WHOOSH)
                    }
                }
            },
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val t = time.floatValue
            val dt = if (last < 0f) 0f else (t - last).coerceIn(0f, 0.05f)
            last = t
            val speed = 0.28f + score * 0.006f
            if (started && !over) {
                vy += 1.3f * dt
                y += vy * dt
                if (y < 0.05f || y > 0.95f) {
                    y = y.coerceIn(0.05f, 0.95f)
                    vy = 0f
                    if (t > hurtUntil) {
                        lives--
                        hurtUntil = t + 1.5f
                        shake.kick(t, 18f)
                        Sounds.play(Sfx.BONK)
                        if (lives <= 0) over = true
                    }
                }
                if (t > spawnAt) {
                    spawnAt = t + 1.7f - min(0.6f, score * 0.02f)
                    val cloud = score > 3 && rnd.nextFloat() < 0.35f
                    rings += Ring(1.15f, 0.2f + rnd.nextFloat() * 0.6f, 0.11f, cloud = cloud)
                }
                val px = 0.25f
                for (r in rings) {
                    r.x -= speed * dt
                    if (!r.passed && abs(r.x - px) < 0.03f) {
                        r.passed = true
                        val hit = abs(y - r.y) < r.gap
                        if (r.cloud) {
                            if (hit && t > hurtUntil) {
                                lives--
                                hurtUntil = t + 1.5f
                                shake.kick(t, 18f)
                                Sounds.play(Sfx.BONK)
                                if (lives <= 0) over = true
                            }
                        } else if (hit) {
                            score++
                            Sounds.play(Sfx.COIN)
                            ps.burst(PKind.STAR, px, r.y, 8, 0.25f, 0.8f, 0.025f, ParticleSystem.STAR_COLORS, gravity = 0.2f)
                        }
                    }
                }
                rings.removeAll { it.x < -0.2f }
            }
            ps.update(t)
            val off = shake.offset(t)
            drawRect(Brush.verticalGradient(listOf(Color(0xFF7FC8FF), Color(0xFFCDEBFF), Color(0xFFFFE6F2))))
            for (k in 0 until 5) {
                val cx = (((k * 0.27f - t * 0.03f * (1 + k % 2)) % 1.2f) + 1.2f) % 1.2f - 0.1f
                drawCircle(Color.White.copy(alpha = 0.6f), radius = size.width * 0.08f, center = Offset(cx * size.width, size.height * (0.15f + 0.17f * k)))
                drawCircle(Color.White.copy(alpha = 0.6f), radius = size.width * 0.06f, center = Offset(cx * size.width + size.width * 0.07f, size.height * (0.15f + 0.17f * k) + 8f))
            }
            for (r in rings) {
                val cx = r.x * size.width + off.x
                val cy = r.y * size.height + off.y
                val rr = r.gap * size.height
                if (r.cloud) {
                    emoji(paint, "⛈️", cx, cy, rr * 1.6f)
                } else {
                    drawOval(if (r.passed) Color(0x8800C853) else Color(0xFFFFC83D), Offset(cx - rr * 0.35f, cy - rr), Size(rr * 0.7f, rr * 2f), style = Stroke(size.width * 0.018f))
                }
            }
            val blink = t < hurtUntil && ((t * 10).toInt() % 2 == 0)
            if (!blink) drawIntoCanvas { art.plane(it.nativeCanvas, size.width * 0.25f + off.x, y * size.height + off.y, size.width * 0.07f, (vy * 40f).coerceIn(-30f, 30f), 1f, 1f) }
            drawIntoCanvas { ps.draw(it.nativeCanvas, size.width, size.height) }
        }
        Box(Modifier.align(Alignment.TopCenter)) {
            GameHud(onBack = onBack, score = "$score", lives = lives, light = false)
        }
        if (!started) CountdownOverlay("Tippe, um zu steigen! Flieg durch die goldenen Ringe.") { started = true }
    }
}

// ================================================================== Kissenturm

private class Pillow(val x: Float, val w: Float, val color: Color)

private val PILLOW_COLORS = listOf(Color(0xFFFF8FB8), Color(0xFF8FD3FF), Color(0xFFFFD84D), Color(0xFF9BE3A8), Color(0xFFB99AFF), Color(0xFFFFB38A))

@Composable
fun StackGame(state: GameState, onFinished: (Int) -> Unit, onBack: () -> Unit) {
    val look = remember { gameLook(state) }
    val renderer = remember { PetRenderer() }
    val pose = remember { PetPose() }
    val time = rememberFrameTime()
    val haptic = rememberHaptic()
    val ps = remember { ParticleSystem() }
    val tf = rememberCanvasTypeface()
    LaunchedEffect(tf) { ps.typeface = tf }
    val stack = remember { mutableStateListOf(Pillow(0.5f, 0.5f, PILLOW_COLORS[0])) }
    var started by remember { mutableStateOf(false) }
    var over by remember { mutableStateOf(false) }
    var phase0 by remember { mutableFloatStateOf(0f) }
    var cam by remember { mutableFloatStateOf(0f) }
    var combo by remember { mutableIntStateOf(0) }
    val score = stack.size - 1
    LaunchedEffect(over) {
        if (over) {
            delay(1100)
            onFinished(score)
        }
    }
    fun moving(t: Float): Float {
        val top = stack.last()
        val speed = 0.85f + min(score, 25) * 0.035f
        val range = 0.5f - top.w / 2f + 0.15f
        return 0.5f + sin((t - phase0) * speed) * range
    }
    Box(
        Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectTapGestures {
                    if (!started || over) return@detectTapGestures
                    haptic()
                    val t = time.floatValue
                    val top = stack.last()
                    val x = moving(t)
                    val l = max(x - top.w / 2f, top.x - top.w / 2f)
                    val r = min(x + top.w / 2f, top.x + top.w / 2f)
                    if (r - l <= 0.01f) {
                        over = true
                        Sounds.play(Sfx.BONK)
                        return@detectTapGestures
                    }
                    val off = abs(x - top.x)
                    val perfect = off < 0.045f
                    // Nearly perfect drops only lose half of the overhang.
                    val near = !perfect && off < 0.09f
                    var w = when {
                        perfect -> top.w
                        near -> top.w - off * 0.5f
                        else -> r - l
                    }
                    val cx = if (perfect || near) top.x else (l + r) / 2f
                    // Three perfect drops in a row make the pillows a bit wider again.
                    if (perfect && (combo + 1) % 3 == 0) w = min(0.5f, w + 0.05f)
                    stack += Pillow(cx, w, PILLOW_COLORS[stack.size % PILLOW_COLORS.size])
                    phase0 = t + if (stack.size % 2 == 0) 0f else PI.toFloat()
                    if (perfect) {
                        combo++
                        Sounds.play(Sfx.SUCCESS)
                        ps.burst(PKind.SPARKLE, cx, 0.55f, 10, 0.3f, 0.8f, 0.025f, ParticleSystem.SPARKLE_COLORS)
                    } else {
                        combo = 0
                        Sounds.play(Sfx.POP)
                    }
                }
            },
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val t = time.floatValue
            ps.update(t)
            val ph = size.height * 0.06f
            val targetCam = max(0f, (stack.size - 6) * ph)
            cam += (targetCam - cam) * 0.1f
            drawRect(Brush.verticalGradient(listOf(Color(0xFF3A2E6A), Color(0xFF8A6AC8), Color(0xFFFFC2DC))))
            for (i in 0 until 30) {
                val sx = ((i * 0.37f) % 1f) * size.width
                val sy = ((i * 0.61f) % 0.6f) * size.height
                drawCircle(Color.White.copy(alpha = 0.3f + 0.3f * sin(t * 2f + i)), 2.5f, Offset(sx, sy))
            }
            val ground = size.height * 0.88f + cam
            drawRect(Color(0xFF6E4A2E), Offset(0f, ground), Size(size.width, size.height))
            fun pillow(p: Pillow, i: Int) {
                val yTop = ground - (i + 1) * ph
                val l = (p.x - p.w / 2f) * size.width
                drawRoundRect(Brush.verticalGradient(listOf(p.color.lighter(0.4f), p.color), startY = yTop, endY = yTop + ph), Offset(l, yTop), Size(p.w * size.width, ph * 0.95f), CornerRadius(ph * 0.45f))
                drawRoundRect(Color.White.copy(alpha = 0.35f), Offset(l + ph * 0.3f, yTop + ph * 0.12f), Size(p.w * size.width * 0.5f, ph * 0.18f), CornerRadius(ph * 0.1f))
            }
            for ((i, p) in stack.withIndex()) pillow(p, i)
            if (started && !over) {
                val top = stack.last()
                pillow(Pillow(moving(t), top.w, PILLOW_COLORS[stack.size % PILLOW_COLORS.size]), stack.size)
            }
            // the pet sits on top of the tower
            val l = look
            if (l != null) {
                val topP = stack.last()
                pose.defaults(l, t)
                pose.eyes = if (over) EyeShape.DIZZY else EyeShape.HAPPY
                pose.mouth = if (over) MouthShape.WAVY else MouthShape.OPEN
                pose.armL = 0.8f
                pose.armR = 0.8f
                val px = topP.x * size.width
                val py = ground - stack.size * ph - (if (started && !over) ph else 0f)
                drawIntoCanvas { renderer.draw(it.nativeCanvas, px, py, size.width * 0.22f, l, pose) }
            }
            drawIntoCanvas { ps.draw(it.nativeCanvas, size.width, size.height) }
        }
        Box(Modifier.align(Alignment.TopCenter)) {
            GameHud(onBack = onBack, score = "$score", combo = combo)
        }
        if (!started) CountdownOverlay("Tippe, um das Kissen fallen zu lassen!") {
            phase0 = time.floatValue
            started = true
        }
    }
}

// ================================================================== Angeln

private class Fish(var x: Float, val y: Float, val speed: Float, val kind: Int) {
    var caught = false
}

private val FISH = listOf("🐟" to 5, "🐠" to 10, "🐡" to 15, "🦐" to 8, "👢" to 0)

@Composable
fun FishingGame(state: GameState, onFinished: (Int) -> Unit, onBack: () -> Unit) {
    val look = remember { gameLook(state) }
    val renderer = remember { PetRenderer() }
    val pose = remember { PetPose() }
    val time = rememberFrameTime()
    val haptic = rememberHaptic()
    val ps = remember { ParticleSystem() }
    val tf = rememberCanvasTypeface()
    LaunchedEffect(tf) { ps.typeface = tf }
    val rnd = remember { Random(System.nanoTime()) }
    val fish = remember { mutableListOf<Fish>() }
    val paint = remember { emojiPaint() }
    var started by remember { mutableStateOf(false) }
    var startAt by remember { mutableFloatStateOf(0f) }
    var score by remember { mutableIntStateOf(0) }
    var hookX by remember { mutableFloatStateOf(0.5f) }
    var hookY by remember { mutableFloatStateOf(0f) } // 0 = up, 1 = bottom
    var hookDir by remember { mutableIntStateOf(0) } // 1 down, -1 up
    var catch by remember { mutableStateOf<Fish?>(null) }
    var last by remember { mutableFloatStateOf(-1f) }
    var done by remember { mutableStateOf(false) }
    val duration = 60f
    val t0 = time.floatValue
    val left = if (started) (duration - (t0 - startAt)).coerceAtLeast(0f) else duration
    LaunchedEffect(left <= 0f, started) {
        if (started && left <= 0f && !done) {
            done = true
            delay(500)
            onFinished(score)
        }
    }
    Box(
        Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectDragGestures { change, _ -> if (hookDir == 0) hookX = (change.position.x / size.width).coerceIn(0.08f, 0.92f) }
            }
            .pointerInput(Unit) {
                detectTapGestures { pos ->
                    if (!started || done) return@detectTapGestures
                    if (hookDir == 0) {
                        hookX = (pos.x / size.width).coerceIn(0.08f, 0.92f)
                        hookDir = 1
                        haptic()
                        Sounds.play(Sfx.SPLASH)
                    }
                }
            },
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val t = time.floatValue
            val dt = if (last < 0f) 0f else (t - last).coerceIn(0f, 0.05f)
            last = t
            val waterTop = size.height * 0.32f
            val waterH = size.height * 0.62f
            if (started && !done) {
                if (fish.size < 7 && rnd.nextFloat() < dt * 2f) {
                    val dir = if (rnd.nextBoolean()) 1f else -1f
                    val kind = when (val r = rnd.nextFloat()) {
                        in 0f..0.45f -> 0
                        in 0.45f..0.7f -> 3
                        in 0.7f..0.85f -> 1
                        in 0.85f..0.93f -> 2
                        else -> 4
                    }
                    fish += Fish(if (dir > 0) -0.1f else 1.1f, 0.15f + rnd.nextFloat() * 0.8f, dir * (0.1f + rnd.nextFloat() * 0.15f) * (1f + kind * 0.15f), kind)
                }
                for (f in fish) if (!f.caught) f.x += f.speed * dt
                fish.removeAll { (it.x < -0.2f || it.x > 1.2f) && !it.caught }
                if (hookDir != 0) {
                    hookY += hookDir * dt * (if (hookDir > 0) 0.9f else 1.3f)
                    if (hookDir > 0 && catch == null) {
                        for (f in fish) {
                            if (!f.caught && abs(f.x - hookX) < 0.07f && abs(f.y - hookY) < 0.06f) {
                                f.caught = true
                                catch = f
                                hookDir = -1
                                break
                            }
                        }
                    }
                    if (hookY >= 1f) hookDir = -1
                    if (hookY <= 0f) {
                        hookY = 0f
                        hookDir = 0
                        catch?.let { f ->
                            val pts = FISH[f.kind].second
                            score += pts
                            fish.remove(f)
                            if (pts > 0) {
                                Sounds.play(Sfx.COIN)
                                ps.add(Particle(PKind.TEXT, hookX, 0.28f, 0f, -0.06f, 1f, 0.05f, 0xFFFFC83D.toInt(), text = "+$pts"))
                                ps.burst(PKind.DROP, hookX, 0.32f, 6, 0.2f, 0.6f, 0.015f, intArrayOf(0xFFBFE8FF.toInt()), gravity = 1f, upward = true)
                            } else {
                                Sounds.play(Sfx.BONK)
                                ps.add(Particle(PKind.TEXT, hookX, 0.28f, 0f, -0.06f, 1f, 0.045f, 0xFF8A8A8A.toInt(), text = "Ein Stiefel!"))
                            }
                        }
                        catch = null
                    }
                }
            }
            ps.update(t)
            drawRect(Brush.verticalGradient(listOf(Color(0xFFFFE0B8), Color(0xFFFFC8D8)), endY = waterTop))
            drawCircle(Color(0xFFFFF4B0), size.width * 0.12f, Offset(size.width * 0.82f, size.height * 0.1f))
            drawRect(Brush.verticalGradient(listOf(Color(0xFF4FB8E8), Color(0xFF1A5E9A)), startY = waterTop, endY = size.height), Offset(0f, waterTop), Size(size.width, size.height - waterTop))
            for (k in 0 until 3) {
                val wy = waterTop + k * 6f
                for (i in 0..12) drawCircle(Color.White.copy(alpha = 0.25f), 6f, Offset((i / 12f) * size.width + sin(t * 2f + i + k) * 8f, wy))
            }
            // boat + pet
            val bx = hookX * size.width
            drawRoundRect(Color(0xFFB9824E), Offset(bx - size.width * 0.16f, waterTop - size.height * 0.04f), Size(size.width * 0.32f, size.height * 0.05f), CornerRadius(30f))
            look?.let { l ->
                pose.defaults(l, t)
                pose.eyes = if (catch != null) EyeShape.STAR else EyeShape.OPEN
                pose.lookY = 1f
                pose.armR = 0.6f
                drawIntoCanvas { renderer.draw(it.nativeCanvas, bx - size.width * 0.06f, waterTop - size.height * 0.035f, size.width * 0.2f, l, pose) }
            }
            // line & hook
            val hy = waterTop + hookY * waterH
            drawLine(Color(0xFF3A3A4A), Offset(bx + size.width * 0.06f, waterTop - size.height * 0.08f), Offset(bx, hy), strokeWidth = 3f)
            drawCircle(Color(0xFFFF5A5A), 9f, Offset(bx, waterTop))
            emoji(paint, "🪝", bx, hy, size.width * 0.05f)
            for (f in fish) {
                val fx = if (f.caught) bx else f.x * size.width
                val fy = if (f.caught) hy + size.width * 0.04f else waterTop + f.y * waterH
                emoji(paint, FISH[f.kind].first, fx, fy, size.width * 0.075f)
            }
            drawIntoCanvas { ps.draw(it.nativeCanvas, size.width, size.height) }
        }
        Box(Modifier.align(Alignment.TopCenter)) {
            GameHud(onBack = onBack, score = "$score", timeFraction = left / duration, timeText = "${left.toInt()}s", light = false)
        }
        if (!started) CountdownOverlay("Tippe, um die Angel auszuwerfen. Goldfische bringen viele Punkte!") {
            startAt = time.floatValue
            started = true
        }
    }
}
