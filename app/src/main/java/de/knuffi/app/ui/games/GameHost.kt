package de.knuffi.app.ui.games

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.scaleIn
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.knuffi.app.data.GameRepository
import de.knuffi.app.render.Expression
import de.knuffi.app.render.PetLook
import de.knuffi.app.ui.PetPortrait
import de.knuffi.app.ui.components.KButton
import de.knuffi.app.ui.components.KCard
import de.knuffi.app.ui.components.PageHeader
import de.knuffi.app.ui.components.Pill
import de.knuffi.app.ui.theme.LocalTokens
import de.knuffi.core.Action
import de.knuffi.core.Engine
import de.knuffi.core.GameState
import de.knuffi.core.MiniGame
import de.knuffi.core.Mood

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
fun GameHeader(title: String, onBack: () -> Unit, left: String, right: String) {
    PageHeader(title, onBack) {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Pill(left, color = MaterialTheme.colorScheme.primaryContainer, textColor = MaterialTheme.colorScheme.onPrimaryContainer)
            Pill(right)
        }
    }
}

fun gameLook(state: GameState): PetLook? =
    PetLook.of(state)?.copy(mood = Mood.HAPPY, sleeping = false, sick = false, dirty = false)

@Composable
private fun ResultOverlay(game: MiniGame, score: Int, state: GameState, onCollect: () -> Unit) {
    val tokens = LocalTokens.current
    val reward = Engine.gameReward(game, score)
    val c = state.counters
    val record = when (game) {
        MiniGame.CATCH -> score > c.bestCatch
        MiniGame.MEMORY -> c.bestMemoryMoves == 0 || score < c.bestMemoryMoves
        MiniGame.WHACK -> score > c.bestWhack
    }
    val scoreText = when (game) {
        MiniGame.CATCH -> "$score Punkte"
        MiniGame.MEMORY -> "$score Züge"
        MiniGame.WHACK -> "$score Treffer"
    }
    val appear = remember { Animatable(0.6f) }
    LaunchedEffect(Unit) { appear.animateTo(1f, spring(dampingRatio = 0.45f, stiffness = 220f)) }
    Box(
        Modifier
            .fillMaxSize()
            .background(Color(0xFF0E0A1F).copy(alpha = 0.6f))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {},
        contentAlignment = Alignment.Center,
    ) {
        KCard(Modifier.padding(28.dp).scale(appear.value)) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                PetPortrait(gameLook(state), state.style, Modifier.size(120.dp)) { f, t ->
                    f.expression = Expression.EXCITED
                    val phase = t % 1f
                    if (phase < 0.35f) f.hop = kotlin.math.sin(phase / 0.35f * Math.PI.toFloat()) * 0.5f
                }
                Text("Super gespielt!", style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
                Spacer(Modifier.height(4.dp))
                Text(scoreText, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary)
                AnimatedVisibility(record, enter = fadeIn() + scaleIn()) {
                    Pill("🏅 Neuer Rekord!", Modifier.padding(top = 6.dp), color = tokens.gold.copy(alpha = 0.3f), textColor = MaterialTheme.colorScheme.onSurface)
                }
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("+${reward.coins} 🪙", fontSize = 22.sp, color = MaterialTheme.colorScheme.onSurface)
                    Text("+${reward.xp} XP", fontSize = 22.sp, color = tokens.xp)
                }
                Spacer(Modifier.height(16.dp))
                KButton("Belohnung einsammeln", onCollect, emoji = "🎁", modifier = Modifier.fillMaxWidth())
            }
        }
    }
}
