package de.knuffi.app.ui.games

import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import de.knuffi.app.render.Expression
import de.knuffi.app.render.PetFrame
import de.knuffi.app.render.PetMotion
import de.knuffi.app.render.renderMode
import de.knuffi.app.ui.PetStage
import de.knuffi.app.ui.rememberFrameTime
import de.knuffi.app.ui.theme.LocalTokens
import de.knuffi.core.GameState
import de.knuffi.core.VisualStyle
import kotlinx.coroutines.delay
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

private class Falling(var x: Float, var y: Float, val speed: Float, val emoji: String, val kind: Int, var rot: Float, val vr: Float)

class Popup(val x: Float, var y: Float, val text: String, val color: Int) {
    var age = 0f
}

private class CatchLogic {
    var score by mutableIntStateOf(0)
    var lives by mutableIntStateOf(3)
    var timeLeft by mutableIntStateOf(DURATION.toInt())
    var over by mutableStateOf(false)
    var playerX = 0.5f
    var targetX = 0.5f
    val items = ArrayList<Falling>()
    val popups = ArrayList<Popup>()
    var eatAt = -10f
    var hitAt = -10f
    private var lastT = -1f
    private var elapsed = 0f
    private var nextSpawn = 0.8f
    private val rnd = Random(System.nanoTime())

    fun update(t: Float) {
        val dt = if (lastT < 0f) 0f else (t - lastT).coerceIn(0f, 0.05f)
        lastT = t
        for (p in popups) {
            p.age += dt
            p.y -= 0.12f * dt
        }
        popups.removeAll { it.age > 0.9f }
        if (over) return
        elapsed += dt
        timeLeft = max(0, ceil(DURATION - elapsed).toInt())
        if (elapsed >= DURATION) {
            over = true
            return
        }
        playerX += (targetX - playerX) * min(1f, dt * 14f)
        val speedUp = 1f + elapsed / 22f
        nextSpawn -= dt
        if (nextSpawn <= 0f) {
            nextSpawn = (0.45f + rnd.nextFloat() * 0.45f) / speedUp
            val r = rnd.nextFloat()
            val bombChance = 0.14f + elapsed * 0.004f
            val emoji: String
            val kind: Int
            when {
                r < bombChance -> {
                    emoji = "💣"
                    kind = 2
                }
                r < bombChance + 0.07f -> {
                    emoji = "⭐"
                    kind = 1
                }
                else -> {
                    emoji = GOOD[rnd.nextInt(GOOD.size)]
                    kind = 0
                }
            }
            items += Falling(0.08f + rnd.nextFloat() * 0.84f, -0.05f, 0.22f + rnd.nextFloat() * 0.12f, emoji, kind, rnd.nextFloat() * 360f, (rnd.nextFloat() - 0.5f) * 160f)
        }
        val it = items.iterator()
        while (it.hasNext()) {
            val f = it.next()
            f.y += f.speed * speedUp * dt
            f.rot += f.vr * dt
            if (f.y in CATCH_TOP..CATCH_BOTTOM && abs(f.x - playerX) < 0.11f) {
                it.remove()
                when (f.kind) {
                    0 -> {
                        score += 10
                        eatAt = t
                        popups += Popup(f.x, f.y, "+10", 0xFFFFB300.toInt())
                    }
                    1 -> {
                        score += 30
                        eatAt = t
                        popups += Popup(f.x, f.y, "+30 ⭐", 0xFFFFB300.toInt())
                    }
                    else -> {
                        lives -= 1
                        hitAt = t
                        popups += Popup(f.x, f.y, "−1 ❤️", 0xFFFF4F6D.toInt())
                        if (lives <= 0) over = true
                    }
                }
            } else if (f.y > 1.08f) {
                it.remove()
            }
        }
    }

    companion object {
        const val DURATION = 40f
        const val CATCH_TOP = 0.72f
        const val CATCH_BOTTOM = 0.87f
        val GOOD = listOf("🍎", "🍙", "🍰", "🍦", "🍣", "🍕", "🍓", "🍪")
    }
}

@Composable
fun CatchGame(state: GameState, onFinished: (Int) -> Unit, onBack: () -> Unit) {
    val tokens = LocalTokens.current
    val logic = remember { CatchLogic() }
    val look = remember { gameLook(state) }
    val stage = remember { PetStage(40) }
    val frame = remember { PetFrame() }
    val time = rememberFrameTime()
    val showHint by remember { derivedStateOf { time.floatValue < 2.5f } }
    val emojiPaint = remember { Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER } }
    val popupPaint = remember {
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textAlign = Paint.Align.CENTER
            typeface = Typeface.DEFAULT_BOLD
        }
    }
    val mode = state.style.renderMode
    val sky = when (state.style) {
        VisualStyle.KAWAII -> listOf(Color(0xFFFFE3F1), Color(0xFFE3F0FF))
        VisualStyle.PIXEL -> listOf(Color(0xFF14142A), Color(0xFF2E1B5E))
        VisualStyle.MINIMAL -> listOf(MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.colorScheme.surface)
    }
    val ground = when (state.style) {
        VisualStyle.KAWAII -> Color(0xFFA8E6C1)
        VisualStyle.PIXEL -> Color(0xFF2F6B3A)
        VisualStyle.MINIMAL -> MaterialTheme.colorScheme.secondaryContainer
    }

    LaunchedEffect(logic.over) {
        if (logic.over) {
            delay(700)
            onFinished(logic.score)
        }
    }

    Column(Modifier.fillMaxSize()) {
        GameHeader("Futterfang", onBack, "⭐ ${logic.score}", "❤️×${logic.lives}  ⏱${logic.timeLeft}")
        Box(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .clipToBounds(),
        ) {
            Canvas(
                Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        awaitEachGesture {
                            val down = awaitFirstDown()
                            logic.targetX = (down.position.x / size.width).coerceIn(0.05f, 0.95f)
                            do {
                                val event = awaitPointerEvent()
                                val change = event.changes.firstOrNull()
                                if (change != null) {
                                    logic.targetX = (change.position.x / size.width).coerceIn(0.05f, 0.95f)
                                    change.consume()
                                }
                            } while (event.changes.any { it.pressed })
                        }
                    },
            ) {
                val t = time.floatValue
                logic.update(t)
                val w = size.width
                val h = size.height
                drawRect(Brush.verticalGradient(sky))
                drawRect(ground, topLeft = Offset(0f, h * 0.93f), size = Size(w, h * 0.07f))
                val itemSize = min(w, h) * 0.1f
                val spriteW = min(w, h) * 0.3f
                val l = look
                drawIntoCanvas { c ->
                    val nc = c.nativeCanvas
                    emojiPaint.textSize = itemSize
                    for (f in logic.items) {
                        nc.save()
                        nc.rotate(f.rot, f.x * w, f.y * h)
                        nc.drawText(f.emoji, f.x * w, f.y * h + itemSize * 0.35f, emojiPaint)
                        nc.restore()
                    }
                    if (l != null) {
                        PetMotion.idle(frame, l, t)
                        frame.expression = Expression.AUTO
                        val sinceEat = t - logic.eatAt
                        val sinceHit = t - logic.hitAt
                        if (sinceEat < 0.35f) {
                            frame.expression = Expression.EATING
                            frame.mouthOpen = abs(sin(sinceEat * 18f))
                        }
                        if (sinceHit < 0.6f) {
                            frame.expression = Expression.REFUSE
                            frame.tilt = sin(sinceHit * 40f) * 10f
                        }
                        val moving = logic.targetX - logic.playerX
                        if (abs(moving) > 0.01f) frame.facing = if (moving > 0) 1f else -1f
                        nc.save()
                        nc.translate(logic.playerX * w - spriteW / 2f, h * 0.95f - spriteW)
                        stage.draw(nc, spriteW, spriteW, l, frame, mode, sizeFactor = 0.8f, groundFactor = 0.98f)
                        nc.restore()
                    }
                    popupPaint.textSize = itemSize * 0.55f
                    for (p in logic.popups) {
                        popupPaint.color = p.color
                        popupPaint.alpha = ((1f - p.age / 0.9f) * 255).toInt().coerceIn(0, 255)
                        nc.drawText(p.text, p.x * w, p.y * h, popupPaint)
                    }
                }
                if (t - logic.hitAt < 0.25f) drawRect(Color(0x55FF4F6D))
            }
            if (showHint) {
                Text(
                    "Wische nach links und rechts!\nFange Essen 🍎, meide Bomben 💣",
                    style = MaterialTheme.typography.titleMedium,
                    textAlign = TextAlign.Center,
                    color = if (tokens.pixel) Color.White else MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(24.dp),
                )
            }
        }
        Box(Modifier.navigationBarsPadding())
    }
}
