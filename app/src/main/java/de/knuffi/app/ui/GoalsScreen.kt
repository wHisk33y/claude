package de.knuffi.app.ui

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.knuffi.app.data.GameRepository
import de.knuffi.app.ui.components.ClayTextButton
import de.knuffi.app.ui.components.EmojiTile
import de.knuffi.app.ui.components.GlossyBar
import de.knuffi.app.ui.components.Pill
import de.knuffi.app.ui.components.ScreenHeader
import de.knuffi.app.ui.components.SectionHeader
import de.knuffi.app.ui.components.SurfaceCard
import de.knuffi.app.ui.components.lighter
import de.knuffi.app.ui.components.staggered
import de.knuffi.app.ui.theme.LocalPalette
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
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = bottomBarSpace()),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item { ScreenHeader("Ziele", "🏆", "Belohnungen, Aufgaben und Erfolge") }
        item { DailyRewardCard(state, Modifier.staggered(0)) }
        item { SectionHeader("Tagesaufgaben", "📋") }
        quests(state)
        item {
            SectionHeader("Erfolge", "🏅") {
                Pill("$unlocked / ${Achievement.entries.size}", color = LocalPalette.current.gold.copy(alpha = 0.25f))
            }
        }
        items(Achievement.entries.chunked(2)) { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                for (a in row) AchievementCard(a, state, Modifier.weight(1f))
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }
        item { StatsCard(state) }
        if (state.memorials.isNotEmpty()) {
            item { SectionHeader("Sternenhimmel", "🌟") }
            items(state.memorials.reversed()) { m ->
                val p = LocalPalette.current
                SurfaceCard(Modifier.fillMaxWidth(), color = Color(0xFF231C45)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("🌟", fontSize = 28.sp)
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text(m.name, style = MaterialTheme.typography.titleMedium, color = Color.White)
                            val days = ((m.endedAt - m.bornAt) / 86_400_000L).coerceAtLeast(0)
                            Text("${m.form.title} · Level ${m.level} · $days Tage", style = MaterialTheme.typography.bodyMedium, color = p.gold.lighter(0.4f))
                        }
                    }
                }
            }
        }
    }
}

private fun LazyListScope.quests(state: GameState) {
    val quests = state.daily.quests
    for ((i, q) in quests.withIndex()) {
        item(key = "quest_$i") {
            val p = LocalPalette.current
            SurfaceCard(Modifier.fillMaxWidth().staggered(i + 1)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    EmojiTile(q.type.emoji, size = 48.dp, color = if (q.done) p.mint else p.sky)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(q.type.label(q.target), style = MaterialTheme.typography.titleSmall, color = p.text)
                        Spacer(Modifier.height(6.dp))
                        GlossyBar(q.progress / q.target.toFloat(), p.mint, p.mintDeep, Modifier.fillMaxWidth(), height = 10.dp)
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "${TimeUtil.formatNumber(q.progress)} / ${TimeUtil.formatNumber(q.target)} · ${q.type.coins} 🪙 + ${q.type.xp} XP",
                            style = MaterialTheme.typography.labelMedium,
                            color = p.textMuted,
                        )
                    }
                    Spacer(Modifier.width(10.dp))
                    when {
                        q.claimed -> DoneStamp()
                        q.done -> ClayTextButton("Holen", { GameRepository.perform(Action.ClaimQuest(i)) }, small = true, color = p.mint, deep = p.mintDeep)
                        else -> Unit
                    }
                }
            }
        }
    }
    item(key = "quest_bonus") {
        val p = LocalPalette.current
        val allClaimed = quests.isNotEmpty() && quests.all { it.claimed }
        SurfaceCard(Modifier.fillMaxWidth(), color = if (p.dark) Color(0xFF3A2A5E) else Color(0xFFEDE6FF)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                EmojiTile("🎉", size = 48.dp, color = p.violet)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text("Tagesbonus", style = MaterialTheme.typography.titleSmall, color = p.text)
                    Text(
                        "Alle ${Quests.PER_DAY} Aufgaben geschafft: +${Quests.BONUS_COINS} 🪙 + ${Quests.BONUS_XP} XP",
                        style = MaterialTheme.typography.bodySmall,
                        color = p.textMuted,
                    )
                }
                when {
                    state.daily.bonusClaimed -> DoneStamp()
                    allClaimed -> ClayTextButton("Holen", { GameRepository.perform(Action.ClaimQuestBonus) }, small = true, color = p.violet, deep = p.violetDeep)
                    else -> Unit
                }
            }
        }
    }
}

@Composable
private fun DoneStamp() {
    val p = LocalPalette.current
    Box(
        Modifier
            .size(38.dp)
            .graphicsLayer { rotationZ = -12f }
            .clip(CircleShape)
            .background(Brush.verticalGradient(listOf(p.mint.lighter(0.25f), p.mintDeep)))
            .border(2.dp, Color.White.copy(alpha = 0.6f), CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Text("✓", color = Color.White, style = MaterialTheme.typography.titleMedium)
    }
}

@Composable
private fun DailyRewardCard(state: GameState, modifier: Modifier = Modifier) {
    val p = LocalPalette.current
    val canClaim = Engine.canClaimDaily(state, System.currentTimeMillis(), GameRepository.zone)
    val streak = state.daily.streak.coerceAtLeast(1)
    val todayIndex = DailyRewards.dayIndex(streak)
    val bounce by rememberInfiniteTransition(label = "chest").animateFloat(0f, 1f, infiniteRepeatable(tween(600), RepeatMode.Reverse), label = "b")
    val shape = RoundedCornerShape(28.dp)
    Column(
        modifier
            .fillMaxWidth()
            .shadow(12.dp, shape, ambientColor = p.pink, spotColor = p.pink)
            .clip(shape)
            .background(Brush.linearGradient(listOf(Color(0xFFFF7AB6), Color(0xFF9A7BFF))))
            .padding(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("🔥", fontSize = 34.sp, modifier = Modifier.graphicsLayer { scaleX = 1f + 0.06f * bounce; scaleY = 1f + 0.1f * bounce })
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text("Tägliche Belohnung", style = MaterialTheme.typography.titleLarge, color = Color.White)
                Text("$streak ${if (streak == 1) "Tag" else "Tage"} in Folge", style = MaterialTheme.typography.bodyMedium, color = Color.White.copy(alpha = 0.9f))
            }
        }
        Spacer(Modifier.height(14.dp))
        Box {
            // track connecting the days
            Canvas(Modifier.fillMaxWidth().height(58.dp)) {
                val y = size.height * 0.45f
                val step = size.width / 7f
                drawLine(Color.White.copy(alpha = 0.3f), Offset(step / 2f, y), Offset(size.width - step / 2f, y), strokeWidth = 6.dp.toPx(), cap = StrokeCap.Round)
                val doneTo = if (canClaim) todayIndex else todayIndex + 1
                if (doneTo > 0) {
                    drawLine(Color.White, Offset(step / 2f, y), Offset(step / 2f + step * (doneTo - 1).coerceAtLeast(0) + if (canClaim) step * 0.5f else 0f, y), strokeWidth = 6.dp.toPx(), cap = StrokeCap.Round)
                }
            }
            Row(Modifier.fillMaxWidth()) {
                for (i in 0 until 7) {
                    val done = i < todayIndex || (i == todayIndex && !canClaim)
                    val current = i == todayIndex && canClaim
                    Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(
                            Modifier
                                .size(if (i == 6) 40.dp else 34.dp)
                                .graphicsLayer {
                                    if (current) {
                                        translationY = -4.dp.toPx() * bounce
                                        scaleX = 1.08f
                                        scaleY = 1.08f
                                    }
                                }
                                .shadow(if (current) 8.dp else 2.dp, CircleShape)
                                .clip(CircleShape)
                                .background(
                                    when {
                                        done -> Brush.verticalGradient(listOf(Color.White, Color(0xFFE9FFF6)))
                                        current -> Brush.verticalGradient(listOf(p.gold.lighter(0.3f), p.gold))
                                        else -> Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.35f), Color.White.copy(alpha = 0.2f)))
                                    },
                                ),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(if (done) "✅" else if (i == 6) "🎁" else "🪙", fontSize = if (i == 6) 20.sp else 16.sp)
                        }
                        Spacer(Modifier.height(3.dp))
                        Text("${DailyRewards.coins[i]}", style = MaterialTheme.typography.labelSmall, color = Color.White)
                    }
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        if (canClaim) {
            ClayTextButton(
                "Heute ${DailyRewards.coins[todayIndex]} 🪙 abholen",
                {
                    GameRepository.perform(Action.ClaimDaily)
                    GameRepository.message("Tägliche Belohnung: +${DailyRewards.coins[todayIndex]} 🪙")
                },
                emoji = "🎁",
                modifier = Modifier.fillMaxWidth(),
                color = p.gold,
                deep = p.goldDeep,
            )
        } else {
            Text(
                "Heute schon abgeholt. Bis morgen! 🌙",
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun AchievementCard(a: Achievement, state: GameState, modifier: Modifier = Modifier) {
    val p = LocalPalette.current
    val unlocked = state.isUnlocked(a)
    val progress = a.progress(state)
    val fraction = (progress / a.target.toFloat()).coerceIn(0f, 1f)
    SurfaceCard(modifier, contentPadding = PaddingValues(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(52.dp), contentAlignment = Alignment.Center) {
                Canvas(Modifier.fillMaxSize()) {
                    val r = size.minDimension / 2f
                    if (unlocked) {
                        drawCircle(p.goldDeep, radius = r, center = Offset(center.x, center.y + 2.dp.toPx()))
                        drawCircle(Brush.radialGradient(listOf(Color(0xFFFFF2B8), p.gold, p.goldDeep), center = Offset(center.x - r * 0.3f, center.y - r * 0.3f), radius = r * 1.4f), radius = r - 1.dp.toPx())
                        drawCircle(Color.White.copy(alpha = 0.5f), radius = r * 0.78f, style = Stroke(1.5.dp.toPx()))
                    } else {
                        drawCircle(p.track, radius = r)
                        val sw = 4.dp.toPx()
                        drawArc(
                            p.violet, -90f, 360f * fraction, false,
                            topLeft = Offset(sw / 2f, sw / 2f), size = Size(size.width - sw, size.height - sw),
                            style = Stroke(sw, cap = StrokeCap.Round),
                        )
                    }
                }
                Text(a.emoji, fontSize = 24.sp, modifier = Modifier.alpha(if (unlocked) 1f else 0.4f))
            }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(a.title, style = MaterialTheme.typography.titleSmall, color = p.text, maxLines = 1)
                Text(
                    if (unlocked) "+${a.coins} 🪙 erhalten" else if (a.target > 1) "${TimeUtil.formatNumber(progress)} / ${TimeUtil.formatNumber(a.target)}" else "${a.coins} 🪙",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (unlocked) p.mintDeep else p.textMuted,
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(a.description, style = MaterialTheme.typography.bodySmall, color = p.textMuted, minLines = 2, maxLines = 2)
    }
}

@Composable
private fun StatsCard(state: GameState) {
    val p = LocalPalette.current
    val c = state.counters
    Column {
        SectionHeader("Statistik", "📊")
        SurfaceCard(Modifier.fillMaxWidth()) {
            val rows = listOf(
                "🍙" to ("Gefüttert" to "${c.feeds}×"),
                "🎾" to ("Gespielt" to "${c.plays}×"),
                "💞" to ("Gestreichelt" to "${c.pets}×"),
                "🎮" to ("Minispiele" to "${c.gamesPlayed}"),
                "👟" to ("Schritte gesamt" to TimeUtil.formatNumber(c.totalSteps)),
                "🪙" to ("Münzen verdient" to TimeUtil.formatNumber(c.coinsEarned)),
                "🔥" to ("Längste Serie" to "${c.maxStreak} Tage"),
                "🐣" to ("Ausgebrütete Eier" to "${c.hatched}"),
            )
            for ((i, row) in rows.withIndex()) {
                val (emoji, pair) = row
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (i % 2 == 0) p.track.copy(alpha = p.track.alpha * 0.5f) else Color.Transparent)
                        .padding(horizontal = 10.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(emoji, fontSize = 16.sp)
                    Spacer(Modifier.width(10.dp))
                    Text(pair.first, style = MaterialTheme.typography.bodyMedium, color = p.text, modifier = Modifier.weight(1f))
                    Text(pair.second, style = MaterialTheme.typography.titleSmall, color = p.text)
                }
            }
        }
    }
}
