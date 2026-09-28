package de.knuffi.app.ui.games

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.knuffi.app.render.PetLook
import de.knuffi.app.ui.components.GlassIconButton
import de.knuffi.app.ui.components.GlossyBar
import de.knuffi.app.ui.components.lighter
import de.knuffi.app.ui.components.rememberHaptic
import de.knuffi.app.ui.theme.LocalPalette
import de.knuffi.core.GameState
import de.knuffi.core.Mood
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.random.Random

fun gameLook(state: GameState): PetLook? =
    PetLook.of(state)?.copy(mood = Mood.HAPPY, sleeping = false, sick = false, dirty = false)

/** Glossy score, lives, time and combo shown over a game. */
@Composable
fun GameHud(
    onBack: () -> Unit,
    score: String,
    modifier: Modifier = Modifier,
    lives: Int? = null,
    maxLives: Int = 3,
    timeFraction: Float? = null,
    timeText: String? = null,
    combo: Int = 0,
    light: Boolean = true,
) {
    val p = LocalPalette.current
    val bump = remember { Animatable(1f) }
    LaunchedEffect(score) {
        bump.snapTo(1.25f)
        bump.animateTo(1f, spring(dampingRatio = 0.35f, stiffness = 500f))
    }
    Column(
        modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 12.dp, vertical = 6.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            GlassIconButton("✖️", onBack, size = 42.dp)
            Spacer(Modifier.width(10.dp))
            Row(
                Modifier
                    .graphicsLayer {
                        scaleX = bump.value
                        scaleY = bump.value
                    }
                    .shadow(8.dp, RoundedCornerShape(50), ambientColor = p.gold, spotColor = p.gold)
                    .clip(RoundedCornerShape(50))
                    .background(Brush.verticalGradient(listOf(p.gold.lighter(0.35f), p.gold)))
                    .border(1.5.dp, Color.White.copy(alpha = 0.6f), RoundedCornerShape(50))
                    .padding(horizontal = 14.dp, vertical = 5.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("⭐", fontSize = 16.sp)
                Spacer(Modifier.width(6.dp))
                Text(score, style = MaterialTheme.typography.titleMedium, color = Color(0xFF5A3A00))
            }
            if (combo >= 2) {
                Spacer(Modifier.width(8.dp))
                Text(
                    "x$combo",
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White,
                    modifier = Modifier
                        .graphicsLayer { rotationZ = -8f }
                        .clip(RoundedCornerShape(12.dp))
                        .background(Brush.linearGradient(listOf(p.pink, p.violet)))
                        .padding(horizontal = 10.dp, vertical = 3.dp),
                )
            }
            Spacer(Modifier.weight(1f))
            if (lives != null) {
                Row {
                    for (i in 0 until maxLives) {
                        val alive = i < lives
                        val s by animateFloatAsState(if (alive) 1f else 0.7f, spring(dampingRatio = 0.3f), label = "heart")
                        Text(
                            if (alive) "❤️" else "🤍",
                            fontSize = 20.sp,
                            modifier = Modifier.graphicsLayer {
                                scaleX = s
                                scaleY = s
                                alpha = if (alive) 1f else 0.5f
                            },
                        )
                    }
                }
            }
        }
        if (timeFraction != null) {
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("⏱", fontSize = 14.sp)
                Spacer(Modifier.width(6.dp))
                val low = timeFraction < 0.2f
                GlossyBar(timeFraction, if (low) p.red else p.mint, if (low) p.redDeep else p.mintDeep, Modifier.weight(1f), height = 12.dp)
                if (timeText != null) {
                    Spacer(Modifier.width(8.dp))
                    Text(
                        timeText,
                        style = MaterialTheme.typography.labelLarge,
                        color = if (light) Color.White else p.text,
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .background(Color.Black.copy(alpha = if (light) 0.25f else 0f))
                            .padding(horizontal = 8.dp, vertical = 1.dp),
                    )
                }
            }
        }
    }
}

/** 3 – 2 – 1 – Los! Calls [onDone] when finished. */
@Composable
fun CountdownOverlay(hint: String, onDone: () -> Unit) {
    val p = LocalPalette.current
    val haptic = rememberHaptic()
    var step by remember { mutableIntStateOf(0) }
    val scale = remember { Animatable(0f) }
    val ring = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        delay(400)
        for (i in 0..3) {
            step = i
            haptic()
            scale.snapTo(0.3f)
            ring.snapTo(0f)
            coroutineScope {
                launch { scale.animateTo(1f, spring(dampingRatio = 0.4f, stiffness = 300f)) }
                launch { ring.animateTo(1f, tween(700)) }
            }
            delay(if (i == 3) 350 else 250)
        }
        onDone()
    }
    Box(
        Modifier
            .fillMaxSize()
            .background(Color(0x55120C2E)),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.size(220.dp)) {
            val r = size.minDimension / 2f * (0.5f + 0.5f * ring.value)
            drawCircle(Color.White.copy(alpha = (1f - ring.value) * 0.8f), radius = r, style = Stroke(10.dp.toPx() * (1f - ring.value) + 1f))
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            val label = if (step < 3) "${3 - step}" else "Los!"
            Box {
                for (i in 4 downTo 1) {
                    Text(
                        label,
                        style = MaterialTheme.typography.displayLarge,
                        fontSize = 96.sp,
                        color = if (step < 3) p.violetDeep else p.pinkDeep,
                        modifier = Modifier.graphicsLayer {
                            scaleX = scale.value
                            scaleY = scale.value
                            translationY = i * 2.dp.toPx()
                        },
                    )
                }
                Text(
                    label,
                    style = MaterialTheme.typography.displayLarge,
                    fontSize = 96.sp,
                    color = Color.White,
                    modifier = Modifier.graphicsLayer {
                        scaleX = scale.value
                        scaleY = scale.value
                    },
                )
            }
            Spacer(Modifier.height(16.dp))
            Text(
                hint,
                style = MaterialTheme.typography.titleMedium,
                color = Color.White,
                modifier = Modifier
                    .clip(RoundedCornerShape(18.dp))
                    .background(Color(0xAA1B1440))
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            )
        }
    }
}

/** Screen shake helper. */
class Shake {
    private var until = 0f
    private var strength = 0f
    private val rnd = Random(9)

    fun kick(t: Float, power: Float, duration: Float = 0.35f) {
        until = t + duration
        strength = power
    }

    fun offset(t: Float): Offset {
        if (t >= until) return Offset.Zero
        val k = (until - t) * strength
        return Offset((rnd.nextFloat() - 0.5f) * 2f * k, (rnd.nextFloat() - 0.5f) * 2f * k)
    }
}

/** Game-wide start/finish state. */
class GameClock {
    var running by mutableStateOf(false)
    var finished by mutableStateOf(false)
}
