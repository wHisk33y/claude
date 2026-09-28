package de.knuffi.app.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.knuffi.app.data.GameRepository
import de.knuffi.app.render.PetLook
import de.knuffi.app.ui.components.CoinChip
import de.knuffi.app.ui.components.KButton
import de.knuffi.app.ui.components.KCard
import de.knuffi.app.ui.components.Pill
import de.knuffi.app.ui.components.rememberHaptic
import de.knuffi.app.ui.theme.LocalTokens
import de.knuffi.core.Action
import de.knuffi.core.Catalog
import de.knuffi.core.GameState
import de.knuffi.core.Item
import de.knuffi.core.ItemKind
import de.knuffi.core.Mood
import de.knuffi.core.Slot

private val shopTabs = listOf(
    "🍰 Essen" to listOf(ItemKind.FOOD),
    "💊 Pflege" to listOf(ItemKind.CARE),
    "🎀 Mode" to listOf(ItemKind.HAT, ItemKind.FACE, ItemKind.NECK),
    "🏠 Zimmer" to listOf(ItemKind.ROOM),
)

@Composable
fun ShopScreen(state: GameState) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var selected by remember { mutableStateOf<Item?>(null) }
    val tokens = LocalTokens.current
    val haptic = rememberHaptic()
    val kinds = shopTabs[tab].second
    val shown = Catalog.items.filter { it.kind in kinds && !(it.unlimited) }

    Column(
        Modifier
            .fillMaxSize()
            .statusBarsPadding(),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp)) {
            Text("Shop", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f))
            CoinChip(state.coins)
        }
        Row(
            Modifier
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            shopTabs.forEachIndexed { i, (label, _) ->
                val active = i == tab
                val bg by animateColorAsState(
                    if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                    label = "chip",
                )
                Text(
                    label,
                    color = if (active) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier
                        .clip(tokens.pillShape)
                        .background(bg)
                        .then(if (tokens.pixel) Modifier.border(2.dp, MaterialTheme.colorScheme.outline, tokens.pillShape) else Modifier)
                        .clickable {
                            haptic()
                            tab = i
                        }
                        .padding(horizontal = 16.dp, vertical = 9.dp),
                )
            }
        }
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.weight(1f),
        ) {
            items(shown, key = { it.id }) { item -> ShopItemCard(item, state) { selected = item } }
        }
    }

    selected?.let { item -> ItemDetailSheet(item, state) { selected = null } }
}

private fun statusOf(item: Item, state: GameState): String? {
    val level = state.pet?.level ?: 1
    return when {
        item.cosmetic && item.kind.slot?.let { state.equipped[it] } == item.id -> "✓ Angelegt"
        item.cosmetic && item.id in state.owned -> "Im Besitz"
        level < item.minLevel -> "🔒 Ab Lv ${item.minLevel}"
        item.consumable && state.count(item.id) > 0 -> "× ${state.count(item.id)} im Vorrat"
        else -> null
    }
}

@Composable
private fun ShopItemCard(item: Item, state: GameState, onClick: () -> Unit) {
    val tokens = LocalTokens.current
    val locked = (state.pet?.level ?: 1) < item.minLevel
    KCard(Modifier.fillMaxWidth(), onClick = onClick) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(78.dp)
                .clip(tokens.cardShape)
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center,
        ) {
            Text(item.emoji, fontSize = 42.sp, color = if (locked) Color.Gray else Color.Unspecified)
        }
        Spacer(Modifier.height(8.dp))
        Text(item.name, style = MaterialTheme.typography.titleSmall, maxLines = 1)
        Spacer(Modifier.height(4.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("🪙 ${item.price}", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurface)
        }
        statusOf(item, state)?.let {
            Spacer(Modifier.height(4.dp))
            Pill(it)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ItemDetailSheet(item: Item, state: GameState, onDismiss: () -> Unit) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val level = state.pet?.level ?: 1
    val slot = item.kind.slot
    val owned = item.id in state.owned
    val equipped = slot != null && state.equipped[slot] == item.id
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState, containerColor = MaterialTheme.colorScheme.surface) {
        Column(
            Modifier
                .padding(horizontal = 20.dp)
                .navigationBarsPadding()
                .padding(bottom = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            when {
                item.kind == ItemKind.ROOM -> {
                    val preview = state.copy(equipped = state.equipped + (Slot.ROOM to item.id))
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(190.dp)
                            .clip(LocalTokens.current.cardShape),
                    ) {
                        PetScene(preview, Modifier.fillMaxSize(), interactive = false)
                    }
                }
                item.cosmetic -> {
                    val base = PetLook.of(state)?.copy(mood = Mood.HAPPY, sleeping = false)
                    val look = when (item.kind) {
                        ItemKind.HAT -> base?.copy(hat = item.id)
                        ItemKind.FACE -> base?.copy(face = item.id)
                        ItemKind.NECK -> base?.copy(neck = item.id)
                        else -> base
                    }
                    PetPortrait(look, state.style, Modifier.size(180.dp))
                }
                else -> Text(item.emoji, fontSize = 72.sp)
            }
            Spacer(Modifier.height(8.dp))
            Text(item.name, style = MaterialTheme.typography.headlineSmall)
            Text(item.description, style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (item.consumable) {
                Spacer(Modifier.height(6.dp))
                Pill(effectText(item.effect))
                Text("Im Vorrat: ${state.count(item.id)}", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 6.dp))
            }
            Spacer(Modifier.height(16.dp))
            val act: (Action) -> Unit = { GameRepository.perform(it) }
            when {
                item.cosmetic && owned && slot != null -> {
                    if (equipped) {
                        KButton(
                            if (slot == Slot.ROOM) "Standardzimmer nutzen" else "Ablegen",
                            { act(Action.Unequip(slot)) },
                            emoji = "↩️",
                            modifier = Modifier.fillMaxWidth(),
                            container = MaterialTheme.colorScheme.secondaryContainer,
                            content = MaterialTheme.colorScheme.onSecondaryContainer,
                            enabled = !(slot == Slot.ROOM && item.id == Catalog.DEFAULT_ROOM),
                        )
                    } else {
                        KButton("Anlegen", { act(Action.Equip(item.id)) }, emoji = "✨", modifier = Modifier.fillMaxWidth())
                    }
                }
                level < item.minLevel -> KButton("Ab Level ${item.minLevel}", {}, enabled = false, emoji = "🔒", modifier = Modifier.fillMaxWidth())
                else -> {
                    val affordable = state.coins >= item.price
                    KButton(
                        if (affordable) "Kaufen für ${item.price} 🪙" else "Noch ${item.price - state.coins} 🪙 sparen",
                        { act(Action.Buy(item.id)) },
                        enabled = affordable,
                        emoji = "🛒",
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }
}
