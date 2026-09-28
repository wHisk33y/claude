package de.knuffi.app.ui.games

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.knuffi.app.render.Expression
import de.knuffi.app.ui.PetPortrait
import de.knuffi.app.ui.components.rememberHaptic
import de.knuffi.app.ui.rememberFrameTime
import de.knuffi.app.ui.theme.LocalTokens
import de.knuffi.core.GameState
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val MEMORY_EMOJIS = listOf("🍎", "🍰", "🍦", "⭐", "🎈", "🌸", "🍙", "🎁")

@Composable
fun MemoryGame(state: GameState, onFinished: (Int) -> Unit, onBack: () -> Unit) {
    val cards = remember { (MEMORY_EMOJIS + MEMORY_EMOJIS).shuffled() }
    val open = remember { mutableStateListOf<Int>() }
    val matched = remember { mutableStateListOf<Int>() }
    var moves by remember { mutableIntStateOf(0) }
    var locked by remember { mutableStateOf(false) }
    var done by remember { mutableStateOf(false) }
    var happyAt by remember { mutableFloatStateOf(-10f) }
    val scope = rememberCoroutineScope()
    val haptic = rememberHaptic()
    val time = rememberFrameTime(!done)
    val seconds by remember { derivedStateOf { time.floatValue.toInt() } }
    val look = remember { gameLook(state) }

    fun click(i: Int) {
        if (locked || done || i in open || i in matched) return
        haptic()
        open.add(i)
        if (open.size == 2) {
            moves++
            val a = open[0]
            val b = open[1]
            if (cards[a] == cards[b]) {
                matched.add(a)
                matched.add(b)
                open.clear()
                happyAt = time.floatValue
                if (matched.size == cards.size) {
                    done = true
                    scope.launch {
                        delay(800)
                        onFinished(moves)
                    }
                }
            } else {
                locked = true
                scope.launch {
                    delay(750)
                    open.clear()
                    locked = false
                }
            }
        }
    }

    Column(Modifier.fillMaxSize()) {
        GameHeader("Memory", onBack, "Züge: $moves", "⏱ ${seconds}s")
        Column(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.Center,
        ) {
            for (r in 0 until 4) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    for (c in 0 until 4) {
                        val i = r * 4 + c
                        MemoryCard(
                            cards[i],
                            faceUp = i in open || i in matched,
                            matched = i in matched,
                            modifier = Modifier
                                .weight(1f)
                                .aspectRatio(0.8f),
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
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            PetPortrait(look, state.style, Modifier.size(72.dp)) { f, t ->
                if (t - happyAt < 1.2f) {
                    f.expression = Expression.EXCITED
                    f.hop = kotlin.math.abs(kotlin.math.sin((t - happyAt) * 7f)) * 0.4f
                }
            }
            Text(
                when {
                    done -> "Geschafft! 🎉"
                    matched.isEmpty() -> "Finde alle 8 Paare!"
                    else -> "${matched.size / 2} von 8 Paaren gefunden"
                },
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(start = 12.dp),
            )
        }
    }
}

@Composable
private fun MemoryCard(emoji: String, faceUp: Boolean, matched: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val tokens = LocalTokens.current
    val rot by animateFloatAsState(if (faceUp) 180f else 0f, tween(380), label = "flip")
    val pop by animateFloatAsState(if (matched) 1f else 0f, spring(dampingRatio = 0.35f), label = "pop")
    val density = LocalDensity.current.density
    val front = MaterialTheme.colorScheme.surface
    val back = Brush.linearGradient(listOf(MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.secondary))
    Box(
        modifier
            .graphicsLayer {
                rotationY = rot
                cameraDistance = 14f * density
                val s = 1f + 0.08f * pop * (if (matched) 1f else 0f)
                scaleX = s
                scaleY = s
                alpha = if (matched) 0.85f else 1f
            }
            .clip(tokens.cardShape)
            .then(if (rot >= 90f) Modifier.background(front) else Modifier.background(back))
            .border(if (tokens.pixel) 3.dp else 1.5.dp, MaterialTheme.colorScheme.outline, tokens.cardShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        if (rot >= 90f) {
            Text(emoji, fontSize = 32.sp, modifier = Modifier.graphicsLayer { rotationY = 180f })
        } else {
            Text(
                if (tokens.pixel) "?" else "✦",
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onPrimary,
            )
        }
    }
}
