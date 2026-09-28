package de.knuffi.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.knuffi.app.data.GameRepository
import de.knuffi.app.ui.components.EmojiBadge
import de.knuffi.app.ui.components.KButton
import de.knuffi.app.ui.components.KCard
import de.knuffi.app.ui.components.KProgress
import de.knuffi.app.ui.components.Pill
import de.knuffi.app.ui.components.SectionTitle
import de.knuffi.app.ui.theme.LocalTokens
import de.knuffi.core.Achievement
import de.knuffi.core.Action
import de.knuffi.core.DailyRewards
import de.knuffi.core.Engine
import de.knuffi.core.GameState
import de.knuffi.core.Quests
import de.knuffi.core.TimeUtil

@Composable
fun GoalsScreen(state: GameState) {
    val unlocked = Achievement.entries.count { state.isUnlocked(it) }
    LazyColumn(
        Modifier
            .fillMaxSize()
            .statusBarsPadding(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item { Text("Ziele & Erfolge", style = MaterialTheme.typography.headlineSmall) }
        item { DailyRewardCard(state) }
        item { SectionTitle("Tagesaufgaben", "📋") }
        itemsIndexedQuests(state)
        item {
            SectionTitle("Erfolge  $unlocked / ${Achievement.entries.size}", "🏅")
        }
        items(Achievement.entries.chunked(2)) { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                for (a in row) AchievementCard(a, state, Modifier.weight(1f))
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }
        item { StatsCard(state) }
        if (state.memorials.isNotEmpty()) {
            item { SectionTitle("Sternenhimmel", "🌟") }
            items(state.memorials.reversed()) { m ->
                KCard(Modifier.fillMaxWidth()) {
                    Text("🌟 ${m.name}", style = MaterialTheme.typography.titleMedium)
                    val days = ((m.endedAt - m.bornAt) / 86_400_000L).coerceAtLeast(0)
                    Text("${m.form.title} · Level ${m.level} · $days Tage", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.itemsIndexedQuests(state: GameState) {
    val quests = state.daily.quests
    for ((i, q) in quests.withIndex()) {
        item(key = "quest_$i") {
            val tokens = LocalTokens.current
            KCard(Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    EmojiBadge(q.type.emoji, size = 46.dp)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(q.type.label(q.target), style = MaterialTheme.typography.titleSmall)
                        Spacer(Modifier.height(6.dp))
                        KProgress(q.progress / q.target.toFloat(), tokens.health, Modifier.fillMaxWidth(), height = 8.dp)
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "${TimeUtil.formatNumber(q.progress)} / ${TimeUtil.formatNumber(q.target)} · ${q.type.coins} 🪙 + ${q.type.xp} XP",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Spacer(Modifier.width(10.dp))
                    when {
                        q.claimed -> Text("✅", fontSize = 24.sp)
                        q.done -> KButton("Holen", { GameRepository.perform(Action.ClaimQuest(i)) }, small = true)
                        else -> Unit
                    }
                }
            }
        }
    }
    item(key = "quest_bonus") {
        val allClaimed = quests.isNotEmpty() && quests.all { it.claimed }
        KCard(Modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.secondaryContainer) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("🎉", fontSize = 28.sp)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text("Tagesbonus", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSecondaryContainer)
                    Text(
                        "Schließe alle ${Quests.PER_DAY} Aufgaben ab: +${Quests.BONUS_COINS} 🪙 + ${Quests.BONUS_XP} XP",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                    )
                }
                when {
                    state.daily.bonusClaimed -> Text("✅", fontSize = 24.sp)
                    allClaimed -> KButton("Holen", { GameRepository.perform(Action.ClaimQuestBonus) }, small = true)
                    else -> Unit
                }
            }
        }
    }
}

@Composable
private fun DailyRewardCard(state: GameState) {
    val tokens = LocalTokens.current
    val canClaim = Engine.canClaimDaily(state, System.currentTimeMillis(), GameRepository.zone)
    val streak = state.daily.streak.coerceAtLeast(1)
    val todayIndex = DailyRewards.dayIndex(streak)
    val claimedToday = !canClaim
    KCard(Modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.primaryContainer) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("🔥", fontSize = 30.sp)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text("Tägliche Belohnung", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onPrimaryContainer)
                Text(
                    "Serie: $streak ${if (streak == 1) "Tag" else "Tage"} in Folge",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
        }
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            for (i in 0 until 7) {
                val done = i < todayIndex || (i == todayIndex && claimedToday)
                val current = i == todayIndex
                Column(
                    Modifier
                        .weight(1f)
                        .clip(tokens.pillShape.takeIf { !tokens.pixel } ?: tokens.cardShape)
                        .background(
                            when {
                                done -> tokens.health.copy(alpha = 0.3f)
                                current -> tokens.gold.copy(alpha = 0.45f)
                                else -> MaterialTheme.colorScheme.surface.copy(alpha = 0.6f)
                            },
                        )
                        .then(if (current) Modifier.border(2.dp, tokens.gold, tokens.pillShape) else Modifier)
                        .padding(vertical = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text("T${i + 1}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurface)
                    Text(if (done) "✓" else if (i == 6) "🎁" else "🪙", fontSize = 15.sp)
                    Text(
                        "${DailyRewards.coins[i]}",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        if (canClaim) {
            KButton(
                "Heute ${DailyRewards.coins[todayIndex]} 🪙 abholen",
                {
                    GameRepository.perform(Action.ClaimDaily)
                    GameRepository.message("Tägliche Belohnung: +${DailyRewards.coins[todayIndex]} 🪙")
                },
                emoji = "🎁",
                modifier = Modifier.fillMaxWidth(),
            )
        } else {
            Text(
                "Heute schon abgeholt. Komm morgen wieder! 🌙",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun AchievementCard(a: Achievement, state: GameState, modifier: Modifier = Modifier) {
    val tokens = LocalTokens.current
    val unlocked = state.isUnlocked(a)
    val progress = a.progress(state)
    KCard(modifier, color = if (unlocked) MaterialTheme.colorScheme.tertiaryContainer else MaterialTheme.colorScheme.surface) {
        Box(Modifier.fillMaxWidth()) {
            Text(a.emoji, fontSize = 30.sp, modifier = Modifier.alpha(if (unlocked) 1f else 0.35f))
            if (unlocked) Text("✓", modifier = Modifier.align(Alignment.TopEnd), color = tokens.health, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(6.dp))
        Text(a.title, style = MaterialTheme.typography.titleSmall, maxLines = 1)
        Text(a.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, minLines = 2, maxLines = 2)
        Spacer(Modifier.height(6.dp))
        if (unlocked) {
            Pill("+${a.coins} 🪙 erhalten", color = tokens.health.copy(alpha = 0.2f), textColor = MaterialTheme.colorScheme.onSurface)
        } else {
            KProgress(progress / a.target.toFloat(), tokens.xp, Modifier.fillMaxWidth(), height = 7.dp)
            Text(
                if (a.target > 1) "${TimeUtil.formatNumber(progress)} / ${TimeUtil.formatNumber(a.target)}" else "${a.coins} 🪙",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 3.dp),
            )
        }
    }
}

@Composable
private fun StatsCard(state: GameState) {
    val c = state.counters
    SectionTitle("Statistik", "📊")
    KCard(Modifier.fillMaxWidth()) {
        val rows = listOf(
            "🍙 Gefüttert" to "${c.feeds}×",
            "🎾 Gespielt" to "${c.plays}×",
            "💞 Gestreichelt" to "${c.pets}×",
            "🎮 Minispiele" to "${c.gamesPlayed}",
            "👟 Schritte gesamt" to TimeUtil.formatNumber(c.totalSteps),
            "🪙 Münzen verdient" to TimeUtil.formatNumber(c.coinsEarned),
            "🔥 Längste Serie" to "${c.maxStreak} Tage",
            "🐣 Ausgebrütete Eier" to "${c.hatched}",
        )
        for ((label, value) in rows) {
            Row(Modifier.padding(vertical = 3.dp)) {
                Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
            }
        }
    }
}
