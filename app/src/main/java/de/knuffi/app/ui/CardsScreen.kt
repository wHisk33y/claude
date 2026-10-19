package de.knuffi.app.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.knuffi.app.data.GameRepository
import de.knuffi.app.render.PetLook
import de.knuffi.app.ui.components.ClayTextButton
import de.knuffi.app.ui.components.PageHeader
import de.knuffi.app.ui.components.Pill
import de.knuffi.app.ui.components.SegmentedControl
import de.knuffi.app.ui.components.SurfaceCard
import de.knuffi.app.ui.theme.LocalPalette
import de.knuffi.core.Action
import de.knuffi.core.CardRarity
import de.knuffi.core.CardStat
import de.knuffi.core.Cards
import de.knuffi.core.Engine
import de.knuffi.core.Form
import de.knuffi.core.GameState
import de.knuffi.core.Mood
import kotlin.random.Random

fun rarityColor(r: CardRarity): Color = when (r) {
    CardRarity.COMMON -> Color(0xFFB8C0D0)
    CardRarity.RARE -> Color(0xFF5AA8FF)
    CardRarity.EPIC -> Color(0xFFB27CFF)
    CardRarity.LEGEND -> Color(0xFFFFC83D)
}

private fun cardLook(f: Form): PetLook = PetLook(f, Mood.HAPPY, line = f.line, level = 10, fur = de.knuffi.core.Skins.defaultFur(f.line))

/** One trading card. [hidden] shows only the back, [missing] a silhouette. */
@Composable
fun CreatureCard(
    form: Form,
    modifier: Modifier = Modifier,
    count: Int = 1,
    hidden: Boolean = false,
    highlight: CardStat? = null,
    width: Dp = 104.dp,
    onClick: (() -> Unit)? = null,
) {
    val rarity = Cards.rarity(form)
    val color = rarityColor(rarity)
    val shape = RoundedCornerShape(14.dp)
    Box(
        modifier
            .width(width)
            .aspectRatio(0.68f)
            .shadow(6.dp, shape)
            .clip(shape)
            .background(
                if (hidden) Brush.linearGradient(listOf(Color(0xFF8A7CFF), Color(0xFFFF7EB6)))
                else Brush.verticalGradient(listOf(Color.White, color.copy(alpha = 0.35f))),
            )
            .border(3.dp, if (hidden) Color.White.copy(alpha = 0.7f) else color, shape)
            .then(if (onClick != null) Modifier.clickable { onClick() } else Modifier),
    ) {
        if (hidden) {
            Text("🃏", fontSize = (width.value * 0.38f).sp, modifier = Modifier.align(Alignment.Center))
            return@Box
        }
        val missing = count <= 0
        val stats = Cards.stats(form)
        Column(Modifier.fillMaxSize().padding(5.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(rarity.emoji, fontSize = 10.sp)
                Spacer(Modifier.weight(1f))
                if (count > 1) Text("×$count", fontSize = 10.sp, color = Color(0xFF3A2A45))
            }
            PetPortrait(
                cardLook(form),
                Modifier.fillMaxWidth().weight(1f),
                animated = false,
                sizeFactor = 0.82f,
                silhouette = if (missing) 0xFF4A3F66.toInt() else null,
            )
            Text(
                if (missing) "???" else form.title,
                fontSize = (width.value * 0.1f).sp,
                color = Color(0xFF3A2A45),
                maxLines = 1,
                textAlign = TextAlign.Center,
            )
            if (!missing) {
                for (st in CardStat.entries) {
                    val on = st == highlight
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(5.dp))
                            .background(if (on) Color(0xFFFFE08A) else Color.Transparent)
                            .padding(horizontal = 3.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(st.emoji, fontSize = (width.value * 0.075f).sp)
                        Spacer(Modifier.weight(1f))
                        Text("${stats[st]}", fontSize = (width.value * 0.085f).sp, color = Color(0xFF3A2A45))
                    }
                }
            }
        }
    }
}

/** The card collection: open packs, swap spare cards for glitter dust, craft missing ones and play duels. */
@Composable
fun CardsScreen(state: GameState, onBack: () -> Unit) {
    val p = LocalPalette.current
    var filter by rememberSaveable { mutableIntStateOf(0) }
    var detail by remember { mutableStateOf<Form?>(null) }
    var duel by remember { mutableStateOf(false) }
    if (duel) {
        CardDuel(state, onDone = { duel = false })
        return
    }
    val owned = Cards.owned(state)
    val shown = when (filter) {
        1 -> owned
        2 -> Cards.all.filter { Cards.count(state, it) == 0 }
        else -> Cards.all
    }
    Column(Modifier.fillMaxSize()) {
        PageHeader("Sammelkarten", onBack) { Pill("✨ ${state.cardDust}", color = p.violet.copy(alpha = 0.2f)) }
        LazyVerticalGrid(
            GridCells.Adaptive(104.dp),
            Modifier.fillMaxSize().navigationBarsPadding(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                SurfaceCard(Modifier.fillMaxWidth(), color = if (p.dark) p.surfaceAlt else Color(0xFFFFF0F6)) {
                    Text("${owned.size} von ${Cards.all.size} Karten", style = MaterialTheme.typography.titleMedium, color = p.text)
                    Text(
                        "Jedes Wesen, das du entdeckst, bekommst du als Karte. Mehr gibt es in Kartentüten (im Shop oder als Duell-Belohnung). Doppelte Karten werden zu Glitzerstaub, daraus bastelst du fehlende.",
                        style = MaterialTheme.typography.bodySmall,
                        color = p.textMuted,
                    )
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ClayTextButton(
                            "Tüte (${state.count(Cards.PACK)})",
                            { GameRepository.perform(Action.UseItem(Cards.PACK)) },
                            Modifier.weight(1f),
                            emoji = "🃏",
                            small = true,
                            enabled = state.count(Cards.PACK) > 0,
                            color = p.pink,
                            deep = p.pinkDeep,
                        )
                        ClayTextButton(
                            "+${Cards.spareDust(state)} ✨",
                            { GameRepository.perform(Action.DustDuplicates) },
                            Modifier.weight(1f),
                            emoji = "♻️",
                            small = true,
                            enabled = Cards.spare(state) > 0,
                            color = p.violet,
                            deep = p.violetDeep,
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    ClayTextButton(
                        "Kartenduell (Quartett)",
                        { duel = true },
                        Modifier.fillMaxWidth(),
                        emoji = "⚔️",
                        enabled = owned.isNotEmpty(),
                        color = p.gold,
                        deep = p.goldDeep,
                    )
                }
            }
            item(span = { GridItemSpan(maxLineSpan) }) {
                SegmentedControl(listOf("Alle", "Meine", "Fehlen"), filter, { filter = it })
            }
            items(shown, key = { it.name }) { f ->
                CreatureCard(f, count = Cards.count(state, f), onClick = { detail = f })
            }
        }
    }
    detail?.let { f ->
        val count = Cards.count(state, f)
        val cost = Cards.rarity(f).craft
        AlertDialog(
            onDismissRequest = { detail = null },
            confirmButton = {
                ClayTextButton(
                    "Basteln: $cost ✨",
                    {
                        GameRepository.perform(Action.CraftCard(f))
                        detail = null
                    },
                    small = true,
                    enabled = state.cardDust >= cost,
                    color = p.violet,
                    deep = p.violetDeep,
                )
            },
            dismissButton = { ClayTextButton("Schließen", { detail = null }, small = true, color = Color(0xFF9A8FB0), deep = Color(0xFF6E6484)) },
            title = { Text(if (count > 0) f.title else "Noch nicht gesammelt", color = p.text) },
            text = {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                    CreatureCard(f, count = count, width = 170.dp)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "${Cards.rarity(f).emoji} ${Cards.rarity(f).title}" + if (count > 0) " · ${f.description}" else "",
                        style = MaterialTheme.typography.bodySmall,
                        color = p.textMuted,
                        textAlign = TextAlign.Center,
                    )
                }
            },
            containerColor = p.surface,
            shape = RoundedCornerShape(28.dp),
        )
    }
}

// ------------------------------------------------------------------ the duel

private class DuelRound(val mine: Form, val theirs: Form)

/**
 * Quartett: both sides show a card, one picks a value, the higher value wins the round.
 * You pick in rounds 1, 3 and 5, your opponent in the others.
 */
@Composable
private fun CardDuel(state: GameState, onDone: () -> Unit) {
    val p = LocalPalette.current
    val rnd = remember { Random(System.nanoTime()) }
    val rounds = remember {
        val mine = Cards.playerDeck(state, rnd)
        val theirs = Cards.opponentDeck(rnd, Engine.playerLevel(state))
        mine.zip(theirs).map { (a, b) -> DuelRound(a, b) }
    }
    val opponent = remember {
        state.resting.filter { !it.isEgg && it.alive }.randomOrNull(rnd)?.name ?: listOf("Bruno", "Kiki", "Momo", "Lotti", "Fips").random(rnd)
    }
    var round by remember { mutableIntStateOf(0) }
    var won by remember { mutableIntStateOf(0) }
    var lost by remember { mutableIntStateOf(0) }
    var chosen by remember { mutableStateOf<CardStat?>(null) }
    var revealed by remember { mutableStateOf(false) }
    var finished by remember { mutableStateOf(false) }
    val flip = remember { Animatable(0f) }
    val r = rounds.getOrNull(round)
    val myTurn = round % 2 == 0

    // The opponent picks its best value on its turn.
    LaunchedEffect(round) {
        flip.snapTo(0f)
        if (r != null && !myTurn && chosen == null) {
            kotlinx.coroutines.delay(900)
            val st = Cards.stats(r.theirs)
            chosen = CardStat.entries.maxByOrNull { st[it] }
        }
    }
    LaunchedEffect(chosen) {
        val c = chosen ?: return@LaunchedEffect
        val rr = rounds.getOrNull(round) ?: return@LaunchedEffect
        flip.animateTo(1f, spring(dampingRatio = 0.6f))
        val a = Cards.stats(rr.mine)[c]
        val b = Cards.stats(rr.theirs)[c]
        if (a > b) won++ else if (b > a) lost++
        revealed = true
    }

    Column(Modifier.fillMaxSize()) {
        PageHeader("Kartenduell", onDone) { Pill("⭐ $won : $lost", color = p.gold.copy(alpha = 0.3f)) }
        Column(
            Modifier.fillMaxSize().padding(horizontal = 16.dp).navigationBarsPadding(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (rounds.isEmpty()) {
                Text("Du brauchst erst ein paar Karten!", style = MaterialTheme.typography.titleMedium, color = p.text)
                return@Column
            }
            if (finished || r == null) {
                val win = won > lost
                LaunchedEffect(Unit) { GameRepository.perform(Action.CardDuelFinished(won, lost)) }
                Spacer(Modifier.height(24.dp))
                Text(if (win) "🏆" else if (won == lost) "🤝" else "💪", fontSize = 72.sp)
                Text(
                    when {
                        win -> "Gewonnen gegen $opponent!"
                        won == lost -> "Unentschieden!"
                        else -> "$opponent hat gewonnen. Nächstes Mal!"
                    },
                    style = MaterialTheme.typography.headlineSmall,
                    color = p.text,
                    textAlign = TextAlign.Center,
                )
                Text("$won : $lost · +${Cards.duelCoins(won)} 🪙 · +${won * 3} ✨", style = MaterialTheme.typography.titleMedium, color = p.mintDeep)
                Spacer(Modifier.weight(1f))
                ClayTextButton("Fertig", onDone, Modifier.fillMaxWidth().padding(bottom = 16.dp), emoji = "✅", color = p.mint, deep = p.mintDeep)
                return@Column
            }
            Text(
                "Runde ${round + 1} von ${rounds.size} · ${if (myTurn) "Du wählst einen Wert!" else "$opponent wählt …"}",
                style = MaterialTheme.typography.titleSmall,
                color = p.textMuted,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Du", style = MaterialTheme.typography.labelLarge, color = p.text)
                    CreatureCard(r.mine, width = 140.dp, highlight = chosen)
                }
                Text("VS", style = MaterialTheme.typography.titleLarge, color = p.pinkDeep)
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(opponent, style = MaterialTheme.typography.labelLarge, color = p.text, maxLines = 1)
                    val turn = if (chosen == null) 0f else flip.value
                    Box(Modifier.graphicsLayer { rotationY = (1f - turn) * 180f; cameraDistance = 12f * density }) {
                        CreatureCard(r.theirs, width = 140.dp, hidden = turn < 0.5f, highlight = chosen)
                    }
                }
            }
            val c = chosen
            if (c == null) {
                if (myTurn) {
                    val st = Cards.stats(r.mine)
                    for (row in CardStat.entries.chunked(2)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            for (s in row) {
                                ClayTextButton("${s.title} ${st[s]}", { chosen = s }, Modifier.weight(1f), emoji = s.emoji, small = true, color = p.violet, deep = p.violetDeep)
                            }
                        }
                    }
                } else {
                    Text("🤔", fontSize = 40.sp)
                }
            } else if (revealed) {
                val a = Cards.stats(r.mine)[c]
                val b = Cards.stats(r.theirs)[c]
                Text(
                    "${c.emoji} ${c.title}: $a gegen $b · " + when {
                        a > b -> "Punkt für dich! 🎉"
                        b > a -> "Punkt für $opponent."
                        else -> "Gleichstand!"
                    },
                    style = MaterialTheme.typography.titleMedium,
                    color = if (a > b) p.mintDeep else if (b > a) p.red else p.text,
                    textAlign = TextAlign.Center,
                )
                ClayTextButton(
                    if (round + 1 >= rounds.size) "Ergebnis" else "Nächste Runde",
                    {
                        revealed = false
                        chosen = null
                        if (round + 1 >= rounds.size) finished = true else round++
                    },
                    Modifier.fillMaxWidth(),
                    emoji = "▶",
                    color = p.pink,
                    deep = p.pinkDeep,
                )
            }
        }
    }
}

/** The cards that came out of a pack, shown one after another. */
@Composable
fun CardsOpenedOverlay(forms: List<Form>, onDone: () -> Unit) {
    val p = LocalPalette.current
    var shownCount by remember { mutableIntStateOf(0) }
    LaunchedEffect(forms) {
        for (i in 1..forms.size) {
            kotlinx.coroutines.delay(320)
            shownCount = i
        }
    }
    Box(
        Modifier
            .fillMaxSize()
            .background(Color(0xCC0E0A1F))
            .clickable(interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }, indication = null) {},
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(20.dp)) {
            Text("🃏 Neue Karten!", style = MaterialTheme.typography.headlineSmall, color = Color.White)
            Spacer(Modifier.height(14.dp))
            for (row in forms.withIndex().chunked(3)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(bottom = 8.dp)) {
                    for ((i, f) in row) {
                        val s = remember { Animatable(0f) }
                        LaunchedEffect(i < shownCount) { if (i < shownCount) s.animateTo(1f, spring(dampingRatio = 0.5f)) }
                        CreatureCard(f, Modifier.graphicsLayer { scaleX = s.value; scaleY = s.value; rotationZ = (1f - s.value) * 30f }, width = 96.dp)
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
            ClayTextButton("Super!", onDone, emoji = "✨", color = p.pink, deep = p.pinkDeep)
        }
    }
}
