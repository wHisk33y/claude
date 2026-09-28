package de.knuffi.app.ui.games

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.knuffi.app.data.GameRepository
import de.knuffi.app.render.EyeShape
import de.knuffi.app.render.MouthShape
import de.knuffi.app.render.PKind
import de.knuffi.app.render.ParticleSystem
import de.knuffi.app.ui.PetPortrait
import de.knuffi.app.ui.components.ClayTextButton
import de.knuffi.app.ui.components.CoinIcon
import de.knuffi.app.ui.rememberCanvasTypeface
import de.knuffi.app.ui.rememberFrameTime
import de.knuffi.app.ui.theme.LocalPalette
import de.knuffi.core.Action
import de.knuffi.core.Engine
import de.knuffi.core.GameState
import de.knuffi.core.MiniGame
import kotlinx.coroutines.delay
import kotlin.math.sin

@Composable
fun GameHost(game: MiniGame, state: GameState, onExit: () -> Unit) {
    var result by remember { mutableStateOf<Int?>(null) }
    // Remember the state at the start so the pet does not change mid game.
    val startState = remember { state }
    BackHandler(enabled = result == null) { onExit() }
    Box(Modifier.fillMaxSize()) {
        val finish: (Int) -> Unit = { if (result == null) result = it }
        when (game) {
            MiniGame.CATCH -> CatchGame(startState, onFinished = finish, onBack = onExit)
            MiniGame.MEMORY -> MemoryGame(startState, onFinished = finish, onBack = onExit)
            MiniGame.WHACK -> WhackGame(startState, onFinished = finish, onBack = onExit)
        }
        result?.let { score ->
            ResultOverlay(game, score, startState) {
                GameRepository.perform(Action.GameFinished(game, score))
                onExit()
            }
        }
    }
}

@Composable
private fun ResultOverlay(game: MiniGame, score: Int, state: GameState, onCollect: () -> Unit) {
    val p = LocalPalette.current
    val reward = Engine.gameReward(game, score)
    val c = state.counters
    val record = when (game) {
        MiniGame.CATCH -> score > c.bestCatch
        MiniGame.MEMORY -> c.bestMemoryMoves == 0 || score < c.bestMemoryMoves
        MiniGame.WHACK -> score > c.bestWhack
    }
    val maxCoins = when (game) {
        MiniGame.CATCH -> 60
        MiniGame.MEMORY -> 40
        MiniGame.WHACK -> 50
    }
    val stars = when {
        reward.coins >= maxCoins * 0.75f -> 3
        reward.coins >= maxCoins * 0.4f -> 2
        else -> 1
    }
    val scoreText = when (game) {
        MiniGame.CATCH -> "$score Punkte"
        MiniGame.MEMORY -> "$score Züge"
        MiniGame.WHACK -> "$score Treffer"
    }
    val appear = remember { Animatable(0.5f) }
    var shownStars by remember { mutableIntStateOf(0) }
    var counting by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        appear.animateTo(1f, spring(dampingRatio = 0.45f, stiffness = 220f))
        for (i in 1..stars) {
            delay(260)
            shownStars = i
        }
        delay(200)
        counting = true
    }
    val coins by animateIntAsState(if (counting) reward.coins else 0, tween(900), label = "coins")
    val xp by animateIntAsState(if (counting) reward.xp else 0, tween(900), label = "xp")
    val particles = remember { ParticleSystem() }
    val time = rememberFrameTime()
    val tf = rememberCanvasTypeface()
    var fired by remember { mutableStateOf(false) }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color(0xB30E0A1F))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {},
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val t = time.floatValue
            if (!fired) {
                fired = true
                particles.typeface = tf
                particles.burst(PKind.CONFETTI, 0.5f, 0.3f, 60, 0.8f, 3f, 0.016f, ParticleSystem.CONFETTI_COLORS, gravity = 0.45f, upward = true)
            }
            particles.update(t)
            drawIntoCanvas { particles.draw(it.nativeCanvas, size.width, size.height) }
        }
        Column(
            Modifier
                .padding(24.dp)
                .scale(appear.value)
                .shadow(24.dp, RoundedCornerShape(32.dp))
                .clip(RoundedCornerShape(32.dp))
                .background(if (p.dark) p.surface else Color.White)
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.Bottom) {
                for (i in 1..3) {
                    val on = i <= shownStars
                    val s = remember { Animatable(0f) }
                    LaunchedEffect(on) { if (on) s.animateTo(1f, spring(dampingRatio = 0.3f, stiffness = 400f)) }
                    Text(
                        if (on) "⭐" else "☆",
                        fontSize = if (i == 2) 52.sp else 40.sp,
                        color = p.textMuted,
                        modifier = Modifier
                            .padding(bottom = if (i == 2) 8.dp else 0.dp)
                            .graphicsLayer {
                                val v = if (on) s.value else 1f
                                scaleX = v
                                scaleY = v
                                rotationZ = if (on) (1f - s.value) * 120f else 0f
                                alpha = if (on) 1f else 0.35f
                            },
                    )
                }
            }
            PetPortrait(gameLook(state), Modifier.size(130.dp)) { pose, t ->
                pose.eyes = if (stars == 3) EyeShape.STAR else EyeShape.HAPPY
                pose.mouth = MouthShape.GRIN
                pose.armL = 0.8f + 0.2f * sin(t * 7f)
                pose.armR = 0.8f
                val phase = t % 0.9f
                if (phase < 0.35f) pose.lift = sin(phase / 0.35f * Math.PI.toFloat()) * 0.25f
            }
            Text(
                when (stars) {
                    3 -> "Fantastisch!"
                    2 -> "Super gespielt!"
                    else -> "Gut gemacht!"
                },
                style = MaterialTheme.typography.headlineSmall,
                color = p.text,
                textAlign = TextAlign.Center,
            )
            Text(scoreText, style = MaterialTheme.typography.titleLarge, color = p.pinkDeep)
            AnimatedVisibility(record, enter = fadeIn() + scaleIn(spring(dampingRatio = 0.4f))) {
                Text(
                    "🏅 Neuer Rekord!",
                    style = MaterialTheme.typography.labelLarge,
                    color = Color(0xFF5A3A00),
                    modifier = Modifier
                        .padding(top = 6.dp)
                        .clip(RoundedCornerShape(50))
                        .background(Brush.verticalGradient(listOf(Color(0xFFFFE9A6), p.gold)))
                        .padding(horizontal = 12.dp, vertical = 3.dp),
                )
            }
            Spacer(Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                RewardChip(p.gold.copy(alpha = 0.2f)) {
                    CoinIcon(size = 22.dp)
                    Spacer(Modifier.width(6.dp))
                    Text("+$coins", style = MaterialTheme.typography.titleLarge, color = p.text)
                }
                RewardChip(p.violet.copy(alpha = 0.2f)) {
                    Text("✨", fontSize = 18.sp)
                    Spacer(Modifier.width(6.dp))
                    Text("+$xp XP", style = MaterialTheme.typography.titleLarge, color = p.text)
                }
            }
            Spacer(Modifier.height(18.dp))
            ClayTextButton("Einsammeln", onCollect, emoji = "🎁", modifier = Modifier.fillMaxWidth(), color = p.gold, deep = p.goldDeep)
        }
    }
}

@Composable
private fun RewardChip(bg: Color, content: @Composable () -> Unit) {
    Row(
        Modifier
            .clip(RoundedCornerShape(18.dp))
            .background(bg)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) { content() }
}
