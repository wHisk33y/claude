package de.knuffi.app.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.knuffi.app.data.GameRepository
import de.knuffi.app.ui.components.ActionButton
import de.knuffi.app.ui.components.CoinChip
import de.knuffi.app.ui.components.KButton
import de.knuffi.app.ui.components.KCard
import de.knuffi.app.ui.components.KProgress
import de.knuffi.app.ui.components.Pill
import de.knuffi.app.ui.components.StatGauge
import de.knuffi.app.ui.components.rememberHaptic
import de.knuffi.app.ui.theme.LocalTokens
import de.knuffi.core.Action
import de.knuffi.core.Catalog
import de.knuffi.core.DailyRewards
import de.knuffi.core.Effect
import de.knuffi.core.Engine
import de.knuffi.core.GameState
import de.knuffi.core.Item
import de.knuffi.core.Need
import de.knuffi.core.Pet
import kotlinx.coroutines.delay

private enum class Sheet { FOOD, CARE }

@Composable
fun HomeScreen(state: GameState, debugHour: Float? = null, onOpenSettings: () -> Unit, onOpenShop: () -> Unit) {
    val pet = state.pet ?: return
    val tokens = LocalTokens.current
    var sheet by remember { mutableStateOf<Sheet?>(null) }
    val act: (Action) -> Unit = { GameRepository.perform(it) }

    Column(
        Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(horizontal = 16.dp),
    ) {
        Header(state, pet, onOpenSettings)
        Spacer(Modifier.height(10.dp))
        Box(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .clip(tokens.cardShape)
                .then(tokens.border?.let { Modifier.border(it, tokens.cardShape) } ?: Modifier),
        ) {
            PetScene(
                state,
                Modifier.fillMaxSize(),
                hourOverride = debugHour,
                onPetTap = { act(if (pet.isEgg) Action.HatchTap else Action.Stroke) },
                onStroke = { act(Action.Stroke) },
            )
            ThoughtBubble(pet, Modifier.align(Alignment.TopStart).padding(12.dp))
            if (Engine.canClaimDaily(state, System.currentTimeMillis(), GameRepository.zone)) {
                GiftButton(state, Modifier.align(Alignment.TopEnd).padding(12.dp))
            }
            if (pet.isEgg) {
                EggHint(pet, Modifier.align(Alignment.BottomCenter).padding(12.dp))
            } else if (pet.sleeping) {
                Pill(
                    "💤 ${pet.name} schläft",
                    Modifier.align(Alignment.BottomCenter).padding(10.dp),
                    color = Color(0xCC1B1F4F),
                    textColor = Color.White,
                )
            }
        }
        Spacer(Modifier.height(12.dp))
        if (!pet.isEgg) {
            StatsRow(pet)
            Spacer(Modifier.height(10.dp))
            ActionsRow(pet, state, onFeed = { sheet = Sheet.FOOD }, onCare = { sheet = Sheet.CARE }, act = act)
        } else {
            KCard(Modifier.fillMaxWidth()) {
                Text(
                    "Dein Ei ist bereit! Tippe ${Engine.HATCH_TAPS}-mal darauf, damit ${pet.name} schlüpfen kann.",
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
        Spacer(Modifier.height(10.dp))
    }

    when (sheet) {
        Sheet.FOOD -> ItemSheet(
            title = "Was gibt's zu essen?",
            items = Catalog.foods,
            state = state,
            onDismiss = { sheet = null },
            onUse = {
                act(Action.Feed(it.id))
                sheet = null
            },
            onShop = {
                sheet = null
                onOpenShop()
            },
        )
        Sheet.CARE -> ItemSheet(
            title = "Pflege & Extras",
            items = Catalog.careItems,
            state = state,
            onDismiss = { sheet = null },
            onUse = {
                act(Action.UseItem(it.id))
                sheet = null
            },
            onShop = {
                sheet = null
                onOpenShop()
            },
        )
        null -> Unit
    }
}

@Composable
private fun Header(state: GameState, pet: Pet, onOpenSettings: () -> Unit) {
    val tokens = LocalTokens.current
    val haptic = rememberHaptic()
    Column {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 6.dp)) {
            Column(Modifier.weight(1f)) {
                Text(pet.name, style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.onBackground, maxLines = 1)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Pill(pet.form.title)
                    if (!pet.isEgg) Pill(pet.stage.title, color = MaterialTheme.colorScheme.tertiaryContainer, textColor = MaterialTheme.colorScheme.onTertiaryContainer)
                    if (state.daily.streak > 1) Pill("🔥 ${state.daily.streak}", color = tokens.danger.copy(alpha = 0.15f), textColor = MaterialTheme.colorScheme.onSurface)
                }
            }
            CoinChip(state.coins)
            Spacer(Modifier.width(8.dp))
            Box(
                Modifier
                    .size(42.dp)
                    .clip(if (tokens.pixel) CutCornerShape(3.dp) else CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .clickable {
                        haptic()
                        onOpenSettings()
                    },
                contentAlignment = Alignment.Center,
            ) { Text("⚙️", fontSize = 19.sp) }
        }
        if (!pet.isEgg) {
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Lv ${pet.level}",
                    style = MaterialTheme.typography.titleSmall,
                    color = tokens.xp,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.width(8.dp))
                KProgress(pet.xp / pet.xpToNext.toFloat(), tokens.xp, Modifier.weight(1f), height = 10.dp)
                Spacer(Modifier.width(8.dp))
                Text(
                    "${pet.xp}/${pet.xpToNext} XP",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

private fun thoughtText(need: Need, pet: Pet): String = when (need) {
    Need.SICK -> "Mir geht's nicht gut …"
    Need.HUNGRY -> "Hunger!"
    Need.DIRTY -> if (pet.poops > 0) "Iiih, sauber machen!" else "Ich will baden!"
    Need.BORED -> "Mir ist langweilig!"
    Need.TIRED -> "Ich bin müde …"
}

@Composable
private fun ThoughtBubble(pet: Pet, modifier: Modifier = Modifier) {
    val needs = pet.needs()
    var index by remember { mutableIntStateOf(0) }
    LaunchedEffect(needs.size) {
        while (true) {
            delay(3200)
            index++
        }
    }
    val need = if (needs.isEmpty()) null else needs[index % needs.size]
    val tokens = LocalTokens.current
    AnimatedContent(
        targetState = need,
        transitionSpec = { (scaleIn(initialScale = 0.6f) + fadeIn()) togetherWith (scaleOut(targetScale = 0.6f) + fadeOut()) },
        modifier = modifier,
        label = "thought",
    ) { n ->
        if (n != null) {
            Row(
                Modifier
                    .clip(tokens.cardShape)
                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.92f))
                    .then(if (tokens.pixel) Modifier.border(3.dp, MaterialTheme.colorScheme.outline, tokens.cardShape) else Modifier)
                    .padding(horizontal = 12.dp, vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(n.emoji, fontSize = 18.sp)
                Spacer(Modifier.width(6.dp))
                Text(thoughtText(n, pet), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurface)
            }
        } else {
            Spacer(Modifier.size(1.dp))
        }
    }
}

@Composable
private fun GiftButton(state: GameState, modifier: Modifier = Modifier) {
    val tokens = LocalTokens.current
    val haptic = rememberHaptic()
    val bounce by rememberInfiniteTransition(label = "gift").animateFloat(0f, 1f, infiniteRepeatable(tween(700), RepeatMode.Reverse), label = "bounce")
    val coins = DailyRewards.coins[DailyRewards.dayIndex(state.daily.streak)]
    Column(
        modifier
            .offset(y = (-6 * bounce).dp)
            .clip(tokens.cardShape)
            .background(tokens.gold.copy(alpha = 0.9f))
            .clickable {
                haptic()
                GameRepository.perform(Action.ClaimDaily)
                GameRepository.message("Tägliche Belohnung: +$coins 🪙")
            }
            .padding(horizontal = 12.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("🎁", fontSize = 26.sp, modifier = Modifier.scale(1f + 0.08f * bounce))
        Text("+$coins", style = MaterialTheme.typography.labelMedium, color = Color(0xFF3B2A00), fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun EggHint(pet: Pet, modifier: Modifier = Modifier) {
    val tokens = LocalTokens.current
    Row(
        modifier
            .clip(tokens.pillShape)
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.92f))
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("👆 Tippe aufs Ei ", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurface)
        for (i in 0 until Engine.HATCH_TAPS) {
            Box(
                Modifier
                    .padding(horizontal = 2.dp)
                    .size(10.dp)
                    .clip(if (tokens.pixel) CutCornerShape(0.dp) else CircleShape)
                    .background(if (i < pet.hatchTaps) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.15f)),
            )
        }
    }
}

@Composable
private fun StatsRow(pet: Pet) {
    val tokens = LocalTokens.current
    KCard(Modifier.fillMaxWidth(), contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 6.dp, vertical = 10.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            StatGauge("🍙", "Sattheit", (pet.satiety / 100.0).toFloat(), tokens.satiety)
            StatGauge("😊", "Laune", (pet.joy / 100.0).toFloat(), tokens.joy)
            StatGauge("⚡", "Energie", (pet.energy / 100.0).toFloat(), tokens.energy)
            StatGauge("🫧", "Hygiene", (pet.hygiene / 100.0).toFloat(), tokens.hygiene)
            StatGauge("❤️", "Gesundheit", (pet.health / 100.0).toFloat(), tokens.health)
        }
    }
}

@Composable
private fun ActionsRow(pet: Pet, state: GameState, onFeed: () -> Unit, onCare: () -> Unit, act: (Action) -> Unit) {
    val tokens = LocalTokens.current
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        ActionButton("🍙", "Füttern", onFeed, highlight = pet.satiety < 35, tint = tokens.satiety.copy(alpha = 0.22f))
        ActionButton(
            "🎾", "Spielen", { act(Action.Play) },
            enabled = !pet.sleeping,
            highlight = pet.joy < 35 && !pet.sleeping,
            tint = tokens.joy.copy(alpha = 0.22f),
        )
        ActionButton(
            if (pet.poops > 0) "💩" else "🧽", "Putzen", { act(Action.Clean) },
            highlight = pet.poops > 0 || pet.hygiene < 35,
            tint = tokens.hygiene.copy(alpha = 0.22f),
        )
        ActionButton(
            if (pet.sleeping) "☀️" else "🌙",
            if (pet.sleeping) "Wecken" else "Schlafen",
            { act(Action.ToggleSleep) },
            highlight = !pet.sleeping && pet.energy < 25,
            tint = tokens.energy.copy(alpha = 0.22f),
        )
        ActionButton(
            "💊", "Pflege", onCare,
            highlight = pet.sick,
            tint = tokens.health.copy(alpha = 0.22f),
        )
    }
    if (state.count(Catalog.MEDICINE) == 0 && pet.sick) {
        Text(
            "Keine Medizin mehr! Im Shop gibt es Nachschub.",
            style = MaterialTheme.typography.bodySmall,
            color = tokens.danger,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}

fun effectText(e: Effect): String = buildList {
    if (e.cures) add("heilt 🤒")
    if (e.satiety != 0) add("${sign(e.satiety)} 🍙")
    if (e.joy != 0) add("${sign(e.joy)} 😊")
    if (e.energy != 0) add("${sign(e.energy)} ⚡")
    if (e.hygiene != 0) add("${sign(e.hygiene)} 🫧")
    if (e.health != 0) add("${sign(e.health)} ❤️")
}.joinToString("  ")

private fun sign(v: Int) = if (v > 0) "+$v" else "$v"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ItemSheet(
    title: String,
    items: List<Item>,
    state: GameState,
    onDismiss: () -> Unit,
    onUse: (Item) -> Unit,
    onShop: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val tokens = LocalTokens.current
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            Modifier
                .padding(horizontal = 16.dp)
                .navigationBarsPadding()
                .padding(bottom = 16.dp),
        ) {
            Text(title, style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(12.dp))
            val visible = items.filter { it.unlimited || state.count(it.id) > 0 }
            if (visible.isEmpty()) {
                Text("Dein Vorrat ist leer.", style = MaterialTheme.typography.bodyLarge)
            }
            for (row in visible.chunked(3)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    for (item in row) {
                        KCard(
                            Modifier.weight(1f),
                            onClick = { onUse(item) },
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(10.dp),
                        ) {
                            Text(item.emoji, fontSize = 34.sp, modifier = Modifier.align(Alignment.CenterHorizontally))
                            Text(
                                item.name,
                                style = MaterialTheme.typography.titleSmall,
                                modifier = Modifier.align(Alignment.CenterHorizontally),
                                maxLines = 1,
                            )
                            Text(
                                if (item.unlimited) "∞" else "× ${state.count(item.id)}",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.align(Alignment.CenterHorizontally),
                            )
                            Text(
                                effectText(item.effect),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }
                    repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
                }
                Spacer(Modifier.height(10.dp))
            }
            Spacer(Modifier.height(4.dp))
            KButton(
                "Mehr im Shop",
                onShop,
                emoji = "🛍️",
                modifier = Modifier.fillMaxWidth(),
                container = MaterialTheme.colorScheme.secondaryContainer,
                content = MaterialTheme.colorScheme.onSecondaryContainer,
            )
            if (tokens.pixel) Spacer(Modifier.height(4.dp))
        }
    }
}
