package de.knuffi.app.ui

import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.knuffi.app.data.GameRepository
import de.knuffi.app.render.LayoutKind
import de.knuffi.app.render.PetLook
import de.knuffi.app.ui.components.ClayTextButton
import de.knuffi.app.ui.components.CoinIcon
import de.knuffi.app.ui.components.CoinPill
import de.knuffi.app.ui.components.Pill
import de.knuffi.app.ui.components.rememberHaptic
import de.knuffi.app.ui.components.ScreenHeader
import de.knuffi.app.ui.components.SurfaceCard
import de.knuffi.app.ui.components.staggered
import de.knuffi.app.ui.theme.LocalPalette
import de.knuffi.core.Action
import de.knuffi.core.Catalog
import de.knuffi.core.EggLine
import de.knuffi.core.Form
import de.knuffi.core.GameState
import de.knuffi.core.Item
import de.knuffi.core.ItemKind
import de.knuffi.core.Mood
import de.knuffi.core.Shop
import de.knuffi.core.Slot
import de.knuffi.core.TimeUtil
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.ui.text.style.TextDecoration

private data class ShopTab(val label: String, val kinds: List<ItemKind>, val top: Color, val bottom: Color)

private val shopTabs = listOf(
    ShopTab("🍙 Essen", listOf(ItemKind.FOOD, ItemKind.INGREDIENT), Color(0xFFFFD6A8), Color(0xFFFFB7C9)),
    ShopTab("🧴 Pflege", listOf(ItemKind.CARE), Color(0xFFB8F0DC), Color(0xFFA8D8FF)),
    ShopTab("🥚 Eier & Tiere", listOf(ItemKind.EGG), Color(0xFFFFF1C4), Color(0xFFFFD0E4)),
    ShopTab("🌱 Garten", listOf(ItemKind.SEED), Color(0xFFD0F5C8), Color(0xFFFFF0B0)),
    ShopTab("🎀 Mode", listOf(ItemKind.HAT, ItemKind.FACE, ItemKind.NECK, ItemKind.BACK), Color(0xFFE2D6FF), Color(0xFFFFC8E4)),
    ShopTab(
        "🛋️ Zimmer",
        listOf(ItemKind.ROOM, ItemKind.WALL, ItemKind.RUG, ItemKind.BED, ItemKind.PLANT, ItemKind.LAMP, ItemKind.PICTURE, ItemKind.TOY, ItemKind.DECO),
        Color(0xFFBDE6FF),
        Color(0xFFD9CCFF),
    ),
)

@Composable
private fun ShopTabs(selected: Int, onSelect: (Int) -> Unit) {
    val p = LocalPalette.current
    val haptic = rememberHaptic()
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(vertical = 4.dp)) {
        itemsIndexed(shopTabs) { i, t ->
            val active = i == selected
            Text(
                t.label,
                style = MaterialTheme.typography.labelLarge,
                color = if (active) Color.White else p.text,
                modifier = Modifier
                    .shadow(if (active) 6.dp else 0.dp, RoundedCornerShape(50), ambientColor = p.pink, spotColor = p.pink)
                    .clip(RoundedCornerShape(50))
                    .background(if (active) Brush.linearGradient(listOf(p.pink, p.violet)) else Brush.linearGradient(listOf(p.track, p.track)))
                    .clickable {
                        haptic()
                        onSelect(i)
                    }
                    .padding(horizontal = 14.dp, vertical = 9.dp),
            )
        }
    }
}

@Composable
fun ShopScreen(state: GameState) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var selected by remember { mutableStateOf<Item?>(null) }
    val current = shopTabs[tab]
    val shown = Catalog.items.filter {
        it.kind in current.kinds && !it.unlimited && (it.sold || it.id in state.owned) &&
            (it.kind != ItemKind.EGG || it.line?.small == true || if (state.catMode) it.line == EggLine.KATZE else it.line != EggLine.KATZE || state.catsUnlocked)
    }
    val navSpace = bottomBarSpace()

    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = navSpace),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        item(span = { GridItemSpan(2) }) {
            Column {
                ScreenHeader("Shop", "🛍️", "Leckereien, Eier, Mode und Möbel") { CoinPill(state.coins) }
                Spacer(Modifier.height(8.dp))
                ShopSpecials(state) { selected = it }
                Spacer(Modifier.height(10.dp))
                ShopTabs(tab) { tab = it }
                if (current.kinds.first() == ItemKind.EGG) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        if (state.catMode) "Körbchen mit Kätzchen, Hasen, Hamstern und Meerschweinchen. Abholen kannst du sie im Kuschelhaus unter 🌍 Welt." else "Jede Ei-Sorte hat ihre eigene Familie. Dazu gibt es echte Hasen, Hamster und Meerschweinchen im Körbchen. Ausgebrütet und abgeholt wird im Kuschelhaus unter 🌍 Welt.",
                        style = MaterialTheme.typography.bodySmall,
                        color = LocalPalette.current.textMuted,
                    )
                }
            }
        }
        itemsIndexed(shown, key = { _, it -> it.id }) { i, item ->
            ShopTile(item, state, current, Modifier.staggered(i)) { selected = item }
        }
    }

    selected?.let { item -> ItemDetailSheet(item, state) { selected = null } }
}

private fun shopDay(): Long = TimeUtil.epochDay(System.currentTimeMillis(), GameRepository.zone)

/** Daily deal and mystery box above the tabs. */
@Composable
private fun ShopSpecials(state: GameState, onOpen: (Item) -> Unit) {
    val p = LocalPalette.current
    val deal = remember(shopDay()) { Shop.dealOf(shopDay()) }
    val dealPrice = Shop.price(deal, shopDay())
    val dealOwned = deal.id in state.owned
    val level = state.pet?.level ?: 1
    val mysteryLeft = remember(state.owned, level) { Shop.mysteryPool(state, level).size }
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.height(IntrinsicSize.Min)) {
        SurfaceCard(
            Modifier.weight(1f).fillMaxHeight(),
            onClick = { onOpen(deal) },
            color = if (p.dark) p.surfaceAlt else Color(0xFFFFF3D6),
            contentPadding = PaddingValues(12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(deal.icon(state.catMode), fontSize = 30.sp)
                Spacer(Modifier.width(6.dp))
                Text(
                    "−${Shop.DEAL_PERCENT} %",
                    style = MaterialTheme.typography.labelMedium,
                    color = Color.White,
                    modifier = Modifier.clip(RoundedCornerShape(50)).background(p.red).padding(horizontal = 7.dp, vertical = 2.dp),
                )
            }
            Text("Angebot des Tages", style = MaterialTheme.typography.labelMedium, color = p.textMuted)
            Text(deal.label(state.catMode), style = MaterialTheme.typography.titleSmall, color = p.text, maxLines = 1)
            if (dealOwned) {
                Text("Im Besitz", style = MaterialTheme.typography.labelLarge, color = p.mintDeep)
            } else {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("${deal.price}", style = MaterialTheme.typography.labelMedium.copy(textDecoration = TextDecoration.LineThrough), color = p.textMuted)
                    Spacer(Modifier.width(6.dp))
                    CoinIcon(size = 16.dp)
                    Spacer(Modifier.width(3.dp))
                    Text("$dealPrice", style = MaterialTheme.typography.titleSmall, color = p.text)
                }
            }
        }
        SurfaceCard(
            Modifier.weight(1f).fillMaxHeight(),
            onClick = if (mysteryLeft > 0 && state.coins >= Shop.MYSTERY_PRICE) ({ GameRepository.perform(Action.BuyMystery) }) else null,
            color = if (p.dark) p.surfaceAlt else Color(0xFFEDE6FF),
            contentPadding = PaddingValues(12.dp),
        ) {
            Text("🎁", fontSize = 30.sp)
            Text("Wunderkiste", style = MaterialTheme.typography.titleSmall, color = p.text)
            Text(
                if (mysteryLeft > 0) "Ein zufälliges Teil, das du noch nicht hast. Antippen zum Öffnen!" else "Du hast schon alles!",
                style = MaterialTheme.typography.bodySmall,
                color = p.textMuted,
            )
            if (mysteryLeft > 0) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CoinIcon(size = 16.dp)
                    Spacer(Modifier.width(3.dp))
                    Text("${Shop.MYSTERY_PRICE}", style = MaterialTheme.typography.titleSmall, color = if (state.coins >= Shop.MYSTERY_PRICE) p.text else p.red)
                }
            }
        }
    }
}

/** An egg, or a kitten for the cat basket. */
fun eggPreview(line: EggLine): PetLook = when {
    line == EggLine.KATZE -> PetLook(Form.KAETZCHEN, Mood.HAPPY, line = EggLine.KATZE, fur = "fur_tabby_grey")
    line.real -> PetLook(Form.find(line, de.knuffi.core.Role.BABY) ?: Form.KAETZCHEN, Mood.HAPPY, line = line, fur = de.knuffi.core.Skins.defaultFur(line))
    else -> PetLook(Form.EGG, Mood.HAPPY, line = line)
}

private fun lookWith(state: GameState, item: Item): PetLook? {
    val base = PetLook.of(state)?.takeUnless { it.isEgg }?.copy(mood = Mood.HAPPY, sleeping = false, sick = false) ?: return null
    return when (item.kind) {
        ItemKind.HAT -> base.copy(hat = item.id)
        ItemKind.FACE -> base.copy(face = item.id)
        ItemKind.NECK -> base.copy(neck = item.id)
        ItemKind.BACK -> base.copy(back = item.id)
        else -> base
    }
}

@Composable
private fun ShopTile(item: Item, state: GameState, tab: ShopTab, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val p = LocalPalette.current
    val level = state.pet?.level ?: 1
    val locked = level < item.minLevel
    val slot = item.kind.slot
    val owned = item.cosmetic && item.id in state.owned
    val equipped = slot != null && state.equipped[slot] == item.id
    val eggLine = item.line
    val tryOn = when {
        item.kind.wearable -> lookWith(state, item)
        item.kind == ItemKind.EGG && eggLine != null -> eggPreview(eggLine)
        else -> null
    }
    val eggs = if (eggLine != null && item.kind == ItemKind.EGG) state.eggCount(eggLine) else 0
    val price = Shop.price(item, shopDay())
    val float by rememberInfiniteTransition(label = "float").animateFloat(
        -1f, 1f, infiniteRepeatable(tween(1600 + (item.id.hashCode() and 0x1FF)), RepeatMode.Reverse), label = "f",
    )
    SurfaceCard(modifier.fillMaxWidth(), onClick = onClick, contentPadding = PaddingValues(8.dp)) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(112.dp)
                .clip(RoundedCornerShape(18.dp))
                .background(
                    Brush.linearGradient(
                        if (p.dark) listOf(tab.top.copy(alpha = 0.32f), tab.bottom.copy(alpha = 0.22f)) else listOf(tab.top, tab.bottom),
                    ),
                ),
            contentAlignment = Alignment.Center,
        ) {
            // floor shadow
            Canvas(Modifier.fillMaxSize()) {
                drawOval(
                    Color.Black.copy(alpha = 0.12f),
                    topLeft = Offset(size.width * 0.3f, size.height * 0.8f),
                    size = Size(size.width * 0.4f * (1f - 0.08f * float), size.height * 0.1f),
                )
                drawCircle(Color.White.copy(alpha = 0.25f), radius = size.height * 0.36f, center = Offset(size.width / 2f, size.height * 0.46f))
            }
            if (tryOn != null) {
                PetPortrait(
                    tryOn,
                    Modifier
                        .fillMaxSize()
                        .graphicsLayer { alpha = if (locked) 0.45f else 1f },
                    animated = false,
                    sizeFactor = 0.78f,
                    groundFactor = 0.9f,
                    shadow = false,
                )
            } else {
                Text(
                    item.icon(state.catMode),
                    fontSize = 50.sp,
                    modifier = Modifier.graphicsLayer {
                        translationY = float * 4.dp.toPx()
                        rotationZ = float * 3f
                        alpha = if (locked) 0.45f else 1f
                    },
                )
            }
            if (locked) {
                Text(
                    "🔒 Lv ${item.minLevel}",
                    style = MaterialTheme.typography.labelMedium,
                    color = Color.White,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(6.dp)
                        .clip(RoundedCornerShape(50))
                        .background(Color(0xCC2A2040))
                        .padding(horizontal = 8.dp, vertical = 2.dp),
                )
            } else if (equipped || owned || (item.stackable && state.count(item.id) > 0) || eggs > 0) {
                Text(
                    when {
                        equipped -> "✓ An"
                        owned -> "Deins"
                        eggs > 0 -> "×$eggs"
                        else -> "×${state.count(item.id)}"
                    },
                    style = MaterialTheme.typography.labelMedium,
                    color = Color.White,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(6.dp)
                        .clip(RoundedCornerShape(50))
                        .background(if (equipped) p.mintDeep else p.violet)
                        .padding(horizontal = 8.dp, vertical = 2.dp),
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(item.label(state.catMode), style = MaterialTheme.typography.titleSmall, color = p.text, maxLines = 1, modifier = Modifier.padding(horizontal = 4.dp))
        Spacer(Modifier.height(4.dp))
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 4.dp)) {
            if (owned) {
                Text("Im Besitz", style = MaterialTheme.typography.labelLarge, color = p.mintDeep)
            } else {
                CoinIcon(size = 18.dp)
                Spacer(Modifier.width(5.dp))
                Text(
                    "$price",
                    style = MaterialTheme.typography.titleSmall,
                    color = if (state.coins >= price) p.text else p.red,
                )
                if (price != item.price) {
                    Spacer(Modifier.width(6.dp))
                    Text("−${Shop.DEAL_PERCENT} %", style = MaterialTheme.typography.labelMedium, color = p.red)
                }
            }
        }
    }
}

@Composable
private fun Rays(color: Color, modifier: Modifier = Modifier) {
    val rot by rememberInfiniteTransition(label = "rays").animateFloat(
        0f, 360f, infiniteRepeatable(tween(16_000, easing = LinearEasing)), label = "rot",
    )
    Canvas(modifier) {
        val c = Offset(size.width / 2f, size.height / 2f)
        val r = size.minDimension * 0.7f
        rotate(rot, c) {
            for (i in 0 until 12) {
                val a0 = Math.toRadians(i * 30.0)
                val a1 = Math.toRadians(i * 30.0 + 13.0)
                val path = Path().apply {
                    moveTo(c.x, c.y)
                    lineTo(c.x + (Math.cos(a0) * r).toFloat(), c.y + (Math.sin(a0) * r).toFloat())
                    lineTo(c.x + (Math.cos(a1) * r).toFloat(), c.y + (Math.sin(a1) * r).toFloat())
                    close()
                }
                drawPath(path, Brush.radialGradient(listOf(color.copy(alpha = 0.45f), Color.Transparent), center = c, radius = r))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ItemDetailSheet(item: Item, state: GameState, onDismiss: () -> Unit) {
    val p = LocalPalette.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val level = state.pet?.level ?: 1
    val slot = item.kind.slot
    val owned = item.id in state.owned
    val equipped = slot != null && state.equipped[slot] == item.id
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = if (p.dark) p.surface else p.bgTop,
        shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp),
    ) {
        Column(
            Modifier.verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .navigationBarsPadding()
                .padding(bottom = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            val eggLine = item.line
            when {
                item.kind == ItemKind.ROOM || item.kind.furniture -> {
                    val preview = if (item.kind == ItemKind.ROOM) {
                        state.copy(equipped = state.equipped + (Slot.ROOM to item.id))
                    } else {
                        state.copy(equipped = state.equipped + (Slot.ROOM to Catalog.DEFAULT_ROOM) + (slot!! to item.id))
                    }
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(210.dp)
                            .clip(RoundedCornerShape(26.dp)),
                    ) {
                        PetScene(preview, Modifier.fillMaxSize(), kind = LayoutKind.BOX, interactive = false, showBubble = false)
                    }
                }
                item.kind == ItemKind.EGG && eggLine != null -> {
                    Box(Modifier.size(200.dp), contentAlignment = Alignment.Center) {
                        Rays(p.gold, Modifier.fillMaxSize())
                        PetPortrait(remember(eggLine) { eggPreview(eggLine) }, Modifier.fillMaxSize(), sizeFactor = 0.6f, groundFactor = 0.88f)
                    }
                }
                item.cosmetic -> {
                    Box(Modifier.size(220.dp), contentAlignment = Alignment.Center) {
                        Rays(p.pink, Modifier.fillMaxSize())
                        Canvas(Modifier.fillMaxSize()) {
                            // pedestal
                            val w = size.width
                            val h = size.height
                            drawOval(Color.Black.copy(alpha = 0.12f), topLeft = Offset(w * 0.22f, h * 0.86f), size = Size(w * 0.56f, h * 0.1f))
                            drawOval(Brush.verticalGradient(listOf(p.violet.copy(alpha = 0.7f), p.violetDeep)), topLeft = Offset(w * 0.24f, h * 0.8f), size = Size(w * 0.52f, h * 0.12f))
                            drawOval(Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.9f), p.violet.copy(alpha = 0.5f))), topLeft = Offset(w * 0.24f, h * 0.78f), size = Size(w * 0.52f, h * 0.1f))
                        }
                        PetPortrait(lookWith(state, item), Modifier.fillMaxSize(), sizeFactor = 0.62f, groundFactor = 0.84f, shadow = false) { pose, t ->
                            pose.eyes = de.knuffi.app.render.EyeShape.HAPPY
                            pose.turn = kotlin.math.sin(t * 0.9f) * 0.5f
                            pose.armL = 0.4f + 0.4f * kotlin.math.sin(t * 3f)
                        }
                    }
                }
                else -> Box(Modifier.size(150.dp), contentAlignment = Alignment.Center) {
                    Rays(p.gold, Modifier.fillMaxSize())
                    Text(item.icon(state.catMode), fontSize = 76.sp)
                }
            }
            Spacer(Modifier.height(10.dp))
            Text(item.label(state.catMode), style = MaterialTheme.typography.headlineSmall, color = p.text)
            Text(item.description, style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center, color = p.textMuted)
            if (item.kind == ItemKind.EGG && eggLine != null) {
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Pill("${eggLine.forms.size} Wesen in dieser Familie", color = p.violet.copy(alpha = 0.18f))
                    Pill("Vorrat: ${state.eggCount(eggLine)}")
                }
            }
            if (item.kind == ItemKind.SEED) {
                Spacer(Modifier.height(10.dp))
                val y = item.yieldId?.let { Catalog[it] }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Pill("⏱ ${item.growHours} Std", color = p.mint.copy(alpha = 0.2f))
                    Pill("Ernte: ${item.yieldCount}× ${y?.emoji ?: ""}")
                    Pill("Vorrat: ${state.count(item.id)}")
                }
            }
            if (item.consumable) {
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Pill(effectText(item.effect), color = p.mint.copy(alpha = 0.2f))
                    Pill("Vorrat: ${state.count(item.id)}")
                }
            }
            Spacer(Modifier.height(18.dp))
            val act: (Action) -> Unit = { GameRepository.perform(it) }
            when {
                item.cosmetic && owned && slot != null -> {
                    if (equipped) {
                        ClayTextButton(
                            when {
                                slot == Slot.ROOM -> "Standardzimmer nutzen"
                                item.kind.furniture -> "Standard nutzen"
                                else -> "Ablegen"
                            },
                            { act(Action.Unequip(slot)) },
                            emoji = "↩️",
                            modifier = Modifier.fillMaxWidth(),
                            color = p.violet,
                            deep = p.violetDeep,
                            enabled = !(slot == Slot.ROOM && item.id == Catalog.DEFAULT_ROOM) &&
                                !(item.kind.furniture && Catalog.defaultEquipped(state.settings.look)[slot] == item.id),
                        )
                    } else {
                        ClayTextButton("Anlegen", { act(Action.Equip(item.id)) }, emoji = "✨", modifier = Modifier.fillMaxWidth(), color = p.mint, deep = p.mintDeep)
                    }
                }
                level < item.minLevel -> ClayTextButton("Ab Level ${item.minLevel}", {}, enabled = false, emoji = "🔒", modifier = Modifier.fillMaxWidth())
                else -> {
                    val price = Shop.price(item, shopDay())
                    val affordable = state.coins >= price
                    ClayTextButton(
                        if (affordable) "Kaufen für $price 🪙" else "Noch ${price - state.coins} 🪙 sparen",
                        { act(Action.Buy(item.id)) },
                        enabled = affordable,
                        emoji = "🛒",
                        modifier = Modifier.fillMaxWidth(),
                        color = p.gold,
                        deep = p.goldDeep,
                    )
                }
            }
        }
    }
}
