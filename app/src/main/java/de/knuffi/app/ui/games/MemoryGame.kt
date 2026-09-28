package de.knuffi.app.ui.games

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.knuffi.app.render.EyeShape
import de.knuffi.app.render.MouthShape
import de.knuffi.app.render.PKind
import de.knuffi.app.render.Particle
import de.knuffi.app.render.ParticleSystem
import de.knuffi.app.ui.PetPortrait
import de.knuffi.app.ui.components.rememberHaptic
import de.knuffi.app.ui.rememberCanvasTypeface
import de.knuffi.app.ui.rememberFrameTime
import de.knuffi.app.ui.theme.LocalPalette
import de.knuffi.core.GameState
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.sin

private val MEMORY_EMOJIS = listOf("🍎", "🍰", "🍦", "⭐", "🎈", "🌸", "🍙", "🎁")
private val CARD_TINTS = listOf(0xFFFFE3EC, 0xFFFFF1D6, 0xFFE3F4FF, 0xFFFFF7C8, 0xFFFFE1F0, 0xFFFFE8F3, 0xFFE8F8EE, 0xFFEDE6FF)

@Composable
fun MemoryGame(state: GameState, onFinished: (Int) -> Unit, onBack: () -> Unit) {
    val p = LocalPalette.current
    val cards = remember { (MEMORY_EMOJIS + MEMORY_EMOJIS).shuffled() }
    val open = remember { mutableStateListOf<Int>() }
    val matched = remember { mutableStateListOf<Int>() }
    val wrong = remember { mutableStateListOf<Int>() }
    var moves by remember { mutableIntStateOf(0) }
    var streak by remember { mutableIntStateOf(0) }
    var peek by remember { mutableStateOf(false) }
    var locked by remember { mutableStateOf(true) }
    var happyAt by remember { mutableFloatStateOf(-10f) }
    var sadAt by remember { mutableFloatStateOf(-10f) }
    var startedAt by remember { mutableFloatStateOf(-1f) }
    val clock = remember { GameClock() }
    val scope = rememberCoroutineScope()
    val haptic = rememberHaptic()
    val time = rememberFrameTime()
    val look = remember { gameLook(state) }
    val ps = remember { ParticleSystem() }
    val tf = rememberCanvasTypeface()
    val centers = remember { Array(16) { Offset.Zero } }
    val origin = remember { floatArrayOf(0f, 0f, 1f, 1f) }

    LaunchedEffect(tf) { ps.typeface = tf }
    LaunchedEffect(clock.running) {
        if (clock.running) {
            peek = true
            delay(1500)
            peek = false
            delay(350)
            locked = false
            startedAt = time.floatValue
        }
    }

    fun burstAt(i: Int, kind: PKind, count: Int, colors: IntArray) {
        val c = centers[i]
        val nx = (c.x - origin[0]) / origin[2]
        val ny = (c.y - origin[1]) / origin[3]
        ps.burst(kind, nx, ny, count, 0.35f, 1f, 0.022f, colors, gravity = 0.3f)
    }

    fun click(i: Int) {
        if (locked || clock.finished || i in open || i in matched) return
        haptic()
        open.add(i)
        if (open.size == 2) {
            moves++
            val a = open[0]
            val b = open[1]
            if (cards[a] == cards[b]) {
                scope.launch {
                    delay(250)
                    matched.add(a)
                    matched.add(b)
                    open.clear()
                    streak++
                    happyAt = time.floatValue
                    burstAt(a, PKind.SPARKLE, 10, ParticleSystem.SPARKLE_COLORS)
                    burstAt(b, PKind.SPARKLE, 10, ParticleSystem.SPARKLE_COLORS)
                    burstAt(a, PKind.STAR, 5, ParticleSystem.STAR_COLORS)
                    burstAt(b, PKind.STAR, 5, ParticleSystem.STAR_COLORS)
                    if (streak >= 2) {
                        val c = centers[b]
                        ps.add(
                            Particle(
                                PKind.TEXT, (c.x - origin[0]) / origin[2], (c.y - origin[1]) / origin[3] - 0.05f, 0f, -0.06f, 1.1f, 0.04f,
                                0xFFFF7AB6.toInt(), text = if (streak >= 3) "Super-Serie x$streak!" else "Doppelt!",
                            ),
                        )
                    }
                    if (matched.size == cards.size) {
                        clock.finished = true
                        ps.burst(PKind.CONFETTI, 0.5f, 0.45f, 60, 0.8f, 2.5f, 0.016f, ParticleSystem.CONFETTI_COLORS, gravity = 0.45f, upward = true)
                        delay(1100)
                        onFinished(moves)
                    }
                }
            } else {
                locked = true
                streak = 0
                scope.launch {
                    delay(650)
                    wrong.add(a)
                    wrong.add(b)
                    sadAt = time.floatValue
                    delay(380)
                    wrong.clear()
                    open.clear()
                    locked = false
                }
            }
        }
    }

    val seconds = if (startedAt < 0f) 0 else ((time.floatValue - startedAt).toInt())
    Box(Modifier.fillMaxSize()) {
        // playful animated background
        Canvas(Modifier.fillMaxSize()) {
            val t = time.floatValue
            drawRect(Brush.verticalGradient(if (p.dark) listOf(Color(0xFF2A1F52), Color(0xFF1A1433)) else listOf(Color(0xFFFFE6F2), Color(0xFFE6E0FF))))
            val step = 64.dp.toPx()
            val shift = (t * 12f) % step
            rotate(-20f) {
                var x = -size.width
                while (x < size.width * 2f) {
                    drawRect(Color.White.copy(alpha = if (p.dark) 0.03f else 0.25f), topLeft = Offset(x + shift, -size.height), size = Size(step / 2f, size.height * 3f))
                    x += step
                }
            }
            for (i in 0 until 10) {
                val fx = ((i * 0.173f + t * 0.01f) % 1f) * size.width
                val fy = ((i * 0.37f + sin(t * 0.5f + i) * 0.02f) % 1f) * size.height
                drawCircle(Color.White.copy(alpha = if (p.dark) 0.06f else 0.35f), radius = (6 + i % 4 * 4).dp.toPx(), center = Offset(fx, fy))
            }
        }
        Column(Modifier.fillMaxSize()) {
            GameHud(
                onBack = onBack,
                score = "$moves Züge",
                timeText = null,
                combo = if (streak >= 2) streak else 0,
                light = false,
            )
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("⏱ ${seconds}s", style = MaterialTheme.typography.titleSmall, color = p.text)
                Text("${matched.size / 2} / 8 Paare", style = MaterialTheme.typography.titleSmall, color = p.text)
            }
            Column(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp),
                verticalArrangement = Arrangement.Center,
            ) {
                for (r in 0 until 4) {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        for (c in 0 until 4) {
                            val i = r * 4 + c
                            MemoryCard(
                                cards[i],
                                tint = Color(CARD_TINTS[MEMORY_EMOJIS.indexOf(cards[i])]),
                                faceUp = peek || i in open || i in matched,
                                matched = i in matched,
                                shaking = i in wrong,
                                index = i,
                                modifier = Modifier
                                    .weight(1f)
                                    .aspectRatio(0.78f)
                                    .onGloballyPositioned { centers[i] = it.boundsInRoot().center },
                            ) { click(i) }
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                }
            }
            Row(
                Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(start = 12.dp, end = 16.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                PetPortrait(look, Modifier.size(84.dp), sizeFactor = 0.8f) { pose, _ ->
                    val t = time.floatValue
                    val h = t - happyAt
                    val s = t - sadAt
                    when {
                        clock.finished || h < 1.1f -> {
                            pose.eyes = if (clock.finished) EyeShape.STAR else EyeShape.HAPPY
                            pose.mouth = MouthShape.GRIN
                            pose.armL = 1f
                            pose.armR = 1f
                            pose.lift = abs(sin(h * 7f)) * 0.3f
                        }
                        s < 1f -> {
                            pose.eyes = EyeShape.SQUINT
                            pose.mouth = MouthShape.WAVY
                            pose.tilt = sin(s * 20f) * 6f
                        }
                        peek -> {
                            pose.eyes = EyeShape.WIDE
                            pose.mouth = MouthShape.O
                        }
                        else -> pose.lookY = 0.6f
                    }
                }
                Spacer(Modifier.width(8.dp))
                Text(
                    when {
                        clock.finished -> "Alle gefunden! 🎉"
                        peek -> "Gut merken …"
                        matched.isEmpty() -> "Finde alle 8 Paare!"
                        streak >= 2 -> "Wow, eine Serie!"
                        else -> "Weiter so!"
                    },
                    style = MaterialTheme.typography.titleMedium,
                    color = p.text,
                    modifier = Modifier
                        .clip(RoundedCornerShape(18.dp))
                        .background(p.glass)
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                )
            }
        }
        Canvas(
            Modifier
                .fillMaxSize()
                .onGloballyPositioned {
                    val pos = it.positionInRoot()
                    origin[0] = pos.x
                    origin[1] = pos.y
                    origin[2] = it.size.width.toFloat().coerceAtLeast(1f)
                    origin[3] = it.size.height.toFloat().coerceAtLeast(1f)
                },
        ) {
            ps.update(time.floatValue)
            drawIntoCanvas { ps.draw(it.nativeCanvas, size.width, size.height) }
        }
        if (!clock.running && !clock.finished) {
            CountdownOverlay("Merke dir die Karten!") { clock.running = true }
        }
    }
}

@Composable
private fun MemoryCard(
    emoji: String,
    tint: Color,
    faceUp: Boolean,
    matched: Boolean,
    shaking: Boolean,
    index: Int,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    val p = LocalPalette.current
    val rot by animateFloatAsState(if (faceUp) 180f else 0f, spring(dampingRatio = 0.62f, stiffness = 260f), label = "flip")
    val lift by animateFloatAsState(if (faceUp && !matched) 1f else 0f, tween(200), label = "lift")
    val pop = remember { Animatable(1f) }
    val shake = remember { Animatable(0f) }
    val appear = remember { Animatable(0f) }
    val density = LocalDensity.current.density
    LaunchedEffect(Unit) {
        delay(index * 35L)
        appear.animateTo(1f, spring(dampingRatio = 0.55f, stiffness = 260f))
    }
    LaunchedEffect(matched) {
        if (matched) {
            pop.snapTo(1.18f)
            pop.animateTo(1f, spring(dampingRatio = 0.3f, stiffness = 320f))
        }
    }
    LaunchedEffect(shaking) {
        if (shaking) {
            for (k in 0 until 6) shake.animateTo(if (k % 2 == 0) 10f else -10f, tween(45))
            shake.animateTo(0f, tween(45))
        }
    }
    val shape = RoundedCornerShape(16.dp)
    val showFront = rot >= 90f
    Box(
        modifier
            .graphicsLayer {
                rotationY = rot
                cameraDistance = 12f * density
                val s = pop.value * (0.6f + 0.4f * appear.value) * (1f + 0.05f * lift)
                scaleX = s
                scaleY = s
                alpha = appear.value.coerceIn(0f, 1f)
                translationX = shake.value * density
                translationY = -6f * lift * density
            }
            .shadow((4 + 8 * lift).dp, shape, ambientColor = p.shadow, spotColor = p.shadow)
            .clip(shape)
            .background(
                if (showFront) {
                    Brush.verticalGradient(listOf(Color.White, tint))
                } else {
                    Brush.linearGradient(listOf(Color(0xFFFF7AB6), Color(0xFF9A7BFF)))
                },
            )
            .then(
                if (showFront && matched) Modifier.border(3.dp, Brush.linearGradient(listOf(p.gold, Color(0xFFFFF3B0), p.gold)), shape)
                else Modifier.border(1.5.dp, Color.White.copy(alpha = 0.6f), shape),
            )
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        if (showFront) {
            Box(
                Modifier
                    .fillMaxSize()
                    .graphicsLayer { rotationY = 180f }
                    .drawBehind {
                        drawCircle(Brush.radialGradient(listOf(Color.White, Color.Transparent)), radius = size.minDimension * 0.42f)
                    },
                contentAlignment = Alignment.Center,
            ) {
                Text(emoji, fontSize = 34.sp)
                if (matched) Text("✓", color = p.mintDeep, fontSize = 13.sp, modifier = Modifier.align(Alignment.TopEnd).padding(5.dp))
            }
        } else {
            Canvas(Modifier.fillMaxSize()) {
                val inset = 6.dp.toPx()
                drawRoundRect(
                    Color.White.copy(alpha = 0.35f),
                    topLeft = Offset(inset, inset),
                    size = Size(size.width - inset * 2, size.height - inset * 2),
                    cornerRadius = CornerRadius(10.dp.toPx()),
                    style = Stroke(1.5.dp.toPx()),
                )
                // polka dots
                val dot = 3.dp.toPx()
                var y = inset * 2.2f
                var row = 0
                while (y < size.height - inset * 1.5f) {
                    var x = inset * 2.2f + if (row % 2 == 0) 0f else dot * 2.5f
                    while (x < size.width - inset * 1.5f) {
                        drawCircle(Color.White.copy(alpha = 0.18f), radius = dot * 0.6f, center = Offset(x, y))
                        x += dot * 5f
                    }
                    y += dot * 4f
                    row++
                }
                drawRoundRect(
                    Color.White.copy(alpha = 0.3f),
                    topLeft = Offset(size.width * 0.15f, inset * 1.2f),
                    size = Size(size.width * 0.7f, size.height * 0.08f),
                    cornerRadius = CornerRadius(size.height),
                )
            }
            Text("🐾", fontSize = 24.sp, color = Color.White.copy(alpha = 0.9f))
        }
    }
}
