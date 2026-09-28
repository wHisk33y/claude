package de.knuffi.app.ui

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import de.knuffi.app.data.GameRepository
import de.knuffi.app.render.PetLook
import de.knuffi.app.ui.components.CoinChip
import de.knuffi.app.ui.components.EmojiBadge
import de.knuffi.app.ui.components.KCard
import de.knuffi.app.ui.components.Pill
import de.knuffi.app.ui.theme.LocalTokens
import de.knuffi.core.Engine
import de.knuffi.core.GameState
import de.knuffi.core.MiniGame
import de.knuffi.core.Mood

@Composable
fun GamesScreen(state: GameState, onStart: (MiniGame) -> Unit) {
    val tokens = LocalTokens.current
    val blocker = Engine.gameBlocker(state)
    Column(
        Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp)) {
            Text("Spielhalle", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f))
            CoinChip(state.coins)
        }
        Spacer(Modifier.height(12.dp))
        KCard(Modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.primaryContainer) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                PetPortrait(
                    PetLook.of(state)?.copy(mood = if (blocker == null) Mood.HAPPY else Mood.SAD),
                    state.style,
                    Modifier.size(84.dp),
                )
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        if (blocker == null) "${state.pet?.name} will spielen!" else "Gerade nicht …",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                    Text(
                        blocker ?: "Jede Runde bringt Münzen und XP, macht glücklich (😊 +12) und kostet etwas Energie (⚡ −10).",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f),
                    )
                }
            }
        }
        Spacer(Modifier.height(6.dp))
        for (game in MiniGame.entries) {
            Spacer(Modifier.height(10.dp))
            val best = when (game) {
                MiniGame.CATCH -> state.counters.bestCatch.takeIf { it > 0 }?.let { "Rekord: $it Punkte" }
                MiniGame.MEMORY -> state.counters.bestMemoryMoves.takeIf { it > 0 }?.let { "Rekord: $it Züge" }
                MiniGame.WHACK -> state.counters.bestWhack.takeIf { it > 0 }?.let { "Rekord: $it Treffer" }
            }
            val maxCoins = when (game) {
                MiniGame.CATCH -> 60
                MiniGame.MEMORY -> 40
                MiniGame.WHACK -> 50
            }
            KCard(
                Modifier.fillMaxWidth(),
                onClick = {
                    if (blocker == null) onStart(game) else GameRepository.message(blocker)
                },
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    EmojiBadge(game.emoji, size = 60.dp, color = tokens.joy.copy(alpha = 0.18f))
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(game.title, style = MaterialTheme.typography.titleMedium)
                        Text(game.description, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.height(6.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Pill("bis zu $maxCoins 🪙", color = tokens.gold.copy(alpha = 0.2f), textColor = MaterialTheme.colorScheme.onSurface)
                            if (best != null) Pill(best)
                        }
                    }
                    Text("▶", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary)
                }
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}
