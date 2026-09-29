package de.knuffi.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.knuffi.app.data.GameRepository
import de.knuffi.app.render.PetLook
import de.knuffi.app.ui.components.ClayTextButton
import de.knuffi.app.ui.components.CoinPill
import de.knuffi.app.ui.components.EmojiTile
import de.knuffi.app.ui.components.PageHeader
import de.knuffi.app.ui.components.Pill
import de.knuffi.app.ui.components.SectionHeader
import de.knuffi.app.ui.components.SegmentedControl
import de.knuffi.app.ui.components.SurfaceCard
import de.knuffi.app.ui.components.staggered
import de.knuffi.app.ui.theme.LocalPalette
import de.knuffi.core.Action
import de.knuffi.core.EggLine
import de.knuffi.core.Engine
import de.knuffi.core.Form
import de.knuffi.core.GameState
import de.knuffi.core.Mood
import de.knuffi.core.Pet
import de.knuffi.core.StickerSet
import de.knuffi.core.Stickers

// ------------------------------------------------------------------ Kuschelhaus

@Composable
fun KuschelhausScreen(state: GameState, onBack: () -> Unit, onTrips: () -> Unit) {
    val p = LocalPalette.current
    val now = rememberNow(10_000L)
    var hatchLine by remember { mutableStateOf<EggLine?>(null) }
    var release by remember { mutableStateOf<Pet?>(null) }
    val active = state.pet
    val full = active != null && active.alive && state.resting.size >= state.restSlots

    Column(Modifier.fillMaxSize()) {
        PageHeader("Kuschelhaus", onBack) { CoinPill(state.coins) }
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (active != null) {
                item {
                    SurfaceCard(Modifier.fillMaxWidth().staggered(0)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            PetPortrait(PetLook.of(state), Modifier.size(110.dp))
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Pill("⭐ Aktiv", color = p.mint.copy(alpha = 0.25f))
                                Spacer(Modifier.height(4.dp))
                                Text(active.name, style = MaterialTheme.typography.titleLarge, color = p.text)
                                Text(
                                    "${if (active.shiny) "✨ " else ""}${active.form.title} · Level ${active.level}",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = p.textMuted,
                                )
                                Text(active.line.title, style = MaterialTheme.typography.bodySmall, color = p.textMuted)
                            }
                        }
                    }
                }
            }

            if (state.totalEggs > 0) {
                item { SectionHeader("Deine Eier", "🥚") }
                item {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        items(EggLine.entries.filter { state.eggCount(it) > 0 }) { line ->
                            EggCard(state, line, full) { hatchLine = line }
                        }
                    }
                }
            }

            item {
                SectionHeader("Im Kuschelhaus", "🏡") {
                    Pill("${state.resting.size} / ${state.restSlots}", color = p.gold.copy(alpha = 0.25f))
                }
            }
            item {
                Text(
                    "Hier ruhen deine anderen Haustiere. Sie werden nicht hungrig und nicht müde. Nur dein aktives Haustier braucht Pflege.",
                    style = MaterialTheme.typography.bodySmall,
                    color = p.textMuted,
                )
            }
            val slots = state.resting.map<Pet, Pet?> { it } + List((state.restSlots - state.resting.size).coerceAtLeast(0)) { null }
            items(slots.chunked(2)) { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    for (pet in row) {
                        if (pet == null) {
                            EmptySlot(Modifier.weight(1f))
                        } else {
                            RestingCard(
                                state, pet, now, Modifier.weight(1f),
                                onSwitch = { GameRepository.perform(Action.SwitchPet(pet.id)) },
                                onTrip = onTrips,
                                onRelease = { release = pet },
                            )
                        }
                    }
                    if (row.size == 1) Spacer(Modifier.weight(1f))
                }
            }
            if (state.restSlots < Engine.MAX_REST_SLOTS) {
                item {
                    val price = Engine.restSlotPrice(state.restSlots)
                    SurfaceCard(Modifier.fillMaxWidth()) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            EmojiTile("🔨", size = 48.dp, color = p.orange)
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text("Anbauen", style = MaterialTheme.typography.titleSmall, color = p.text)
                                Text("Ein weiterer Kuschelplatz", style = MaterialTheme.typography.bodySmall, color = p.textMuted)
                            }
                            ClayTextButton(
                                "$price 🪙",
                                { GameRepository.perform(Action.BuyRestSlot) },
                                small = true,
                                enabled = state.coins >= price,
                                color = p.gold,
                                deep = p.goldDeep,
                            )
                        }
                    }
                }
            }
            item {
                SurfaceCard(Modifier.fillMaxWidth(), color = if (p.dark) p.surfaceAlt else Color(0xFFFFF6E0)) {
                    Text("💡 Wie bekomme ich neue Eier?", style = MaterialTheme.typography.titleSmall, color = p.text)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Im Shop gibt es Ei-Sorten zu kaufen. Außerdem bekommst du Eier, wenn ein Haustier erwachsen wird, im Jahreszeiten-Pass, bei Festen und manchmal bringen deine Lieblinge eins vom Ausflug mit.",
                        style = MaterialTheme.typography.bodySmall,
                        color = p.textMuted,
                    )
                }
            }
        }
    }

    hatchLine?.let { line ->
        NameDialog(
            title = "${line.emoji} ${line.title} ausbrüten",
            text = if (active != null && active.alive) {
                "${active.name} kuschelt sich solange ins Kuschelhaus. Wie soll das Neue heißen?"
            } else {
                "Wie soll dein neues Haustier heißen?"
            },
            confirm = "Ausbrüten",
            onConfirm = {
                GameRepository.perform(Action.HatchEgg(line, it))
                hatchLine = null
                onBack()
            },
            onDismiss = { hatchLine = null },
        )
    }
    release?.let { pet ->
        ConfirmDialog(
            title = "${pet.name} verabschieden?",
            text = "${pet.name} zieht dann zu einer lieben Familie und bekommt einen Stern am Sternenhimmel. Das kann man nicht rückgängig machen.",
            confirm = "Verabschieden",
            onConfirm = {
                GameRepository.perform(Action.ReleasePet(pet.id))
                release = null
            },
            onDismiss = { release = null },
            danger = true,
        )
    }
}

@Composable
private fun EggCard(state: GameState, line: EggLine, full: Boolean, onHatch: () -> Unit) {
    val p = LocalPalette.current
    val look = remember(line) { PetLook(Form.EGG, Mood.HAPPY, line = line) }
    SurfaceCard(Modifier.width(150.dp), contentPadding = PaddingValues(12.dp)) {
        Box {
            PetPortrait(look, Modifier.fillMaxWidth().height(96.dp), sizeFactor = 0.8f)
            Pill("×${state.eggCount(line)}", Modifier.align(Alignment.TopEnd), color = p.gold.copy(alpha = 0.3f))
        }
        Text(line.title, style = MaterialTheme.typography.titleSmall, color = p.text, maxLines = 1)
        Text(line.description, style = MaterialTheme.typography.bodySmall, color = p.textMuted, maxLines = 2, minLines = 2)
        Spacer(Modifier.height(8.dp))
        ClayTextButton(
            if (full) "Kein Platz" else "Ausbrüten",
            onHatch,
            small = true,
            enabled = !full,
            modifier = Modifier.fillMaxWidth(),
            color = p.mint,
            deep = p.mintDeep,
        )
    }
}

@Composable
private fun RestingCard(
    state: GameState,
    pet: Pet,
    now: Long,
    modifier: Modifier = Modifier,
    onSwitch: () -> Unit,
    onTrip: () -> Unit,
    onRelease: () -> Unit,
) {
    val p = LocalPalette.current
    val trip = state.onTrip(pet.id)
    val look = remember(pet, state.equipped) { PetLook.of(pet, state).copy(sleeping = !pet.isEgg) }
    SurfaceCard(modifier, contentPadding = PaddingValues(12.dp)) {
        Box(Modifier.fillMaxWidth().height(100.dp)) {
            if (trip == null) {
                PetPortrait(look, Modifier.fillMaxSize())
            } else {
                Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                    Text(trip.destination.emoji, fontSize = 40.sp)
                    Text(
                        if (now >= trip.endsAt) "Ist zurück!" else "noch ${countdown(trip.endsAt - now)}",
                        style = MaterialTheme.typography.labelMedium,
                        color = if (now >= trip.endsAt) p.mintDeep else p.textMuted,
                    )
                }
            }
            if (pet.shiny) Text("✨", fontSize = 18.sp, modifier = Modifier.align(Alignment.TopEnd))
        }
        Text(pet.name, style = MaterialTheme.typography.titleSmall, color = p.text, maxLines = 1)
        Text("${pet.form.title} · Lv ${pet.level}", style = MaterialTheme.typography.bodySmall, color = p.textMuted, maxLines = 1)
        Spacer(Modifier.height(8.dp))
        if (trip == null) {
            ClayTextButton("Aktivieren", onSwitch, small = true, modifier = Modifier.fillMaxWidth(), color = p.pink, deep = p.pinkDeep)
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                if (!pet.isEgg) {
                    ClayTextButton("🎒", onTrip, small = true, modifier = Modifier.weight(1f), color = p.sky, deep = p.skyDeep)
                }
                ClayTextButton("👋", onRelease, small = true, modifier = Modifier.weight(1f), color = Color(0xFF9A8FB0), deep = Color(0xFF6E6484))
            }
        } else {
            ClayTextButton("Zu den Ausflügen", onTrip, small = true, modifier = Modifier.fillMaxWidth(), color = p.sky, deep = p.skyDeep)
        }
    }
}

@Composable
private fun EmptySlot(modifier: Modifier = Modifier) {
    val p = LocalPalette.current
    Box(
        modifier
            .height(236.dp)
            .clip(RoundedCornerShape(24.dp))
            .border(2.dp, p.textMuted.copy(alpha = 0.25f), RoundedCornerShape(24.dp))
            .background(p.track.copy(alpha = p.track.alpha * 0.4f)),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("🛏️", fontSize = 32.sp, modifier = Modifier.alpha(0.5f))
            Text("Freier Platz", style = MaterialTheme.typography.labelLarge, color = p.textMuted)
        }
    }
}

// ------------------------------------------------------------------ Album

@Composable
fun AlbumScreen(state: GameState, onBack: () -> Unit) {
    val p = LocalPalette.current
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var detail by remember { mutableStateOf<Form?>(null) }
    val shinies = Form.creatures.count { state.discovered(it, shiny = true) }

    Column(Modifier.fillMaxSize()) {
        PageHeader("Sammelalbum", onBack) {
            Pill("${state.discoveredCount} / ${Form.creatures.size}", color = p.gold.copy(alpha = 0.25f))
        }
        SegmentedControl(listOf("🐾 Wesen", "🎴 Sticker"), tab, { tab = it }, Modifier.padding(horizontal = 16.dp))
        Spacer(Modifier.height(8.dp))
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (tab == 0) {
                item {
                    Text(
                        "Jede Ei-Sorte hat ihre eigene Familie. Was aus deinem Haustier wird, hängt von deiner Pflege ab. Ganz selten schlüpft ein schillerndes ✨ Wesen. $shinies schillernde gefunden.",
                        style = MaterialTheme.typography.bodySmall,
                        color = p.textMuted,
                    )
                }
                for (line in EggLine.entries) {
                    val forms = line.forms
                    val found = forms.count { state.discovered(it) }
                    item(key = "h_${line.name}") {
                        SectionHeader(line.title, line.emoji) {
                            Pill("$found / ${forms.size}", color = if (found == forms.size) p.mint.copy(alpha = 0.3f) else p.violet.copy(alpha = 0.16f))
                        }
                    }
                    items(forms.chunked(4), key = { "r_${line.name}_${it.first().name}" }) { row ->
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            for (form in row) AlbumEntry(state, form, Modifier.weight(1f)) { detail = form }
                            repeat(4 - row.size) { Spacer(Modifier.weight(1f)) }
                        }
                    }
                }
            } else {
                item {
                    Text(
                        "Sticker gibt es auf Ausflügen, als Stickerpäckchen im Shop und bei Festen. Für jedes volle Set gibt es Münzen!",
                        style = MaterialTheme.typography.bodySmall,
                        color = p.textMuted,
                    )
                }
                for (set in StickerSet.entries) {
                    val list = Stickers.of(set)
                    val have = list.count { (state.stickers[it.id] ?: 0) > 0 }
                    item(key = "s_${set.name}") {
                        SectionHeader(set.title, set.emoji) {
                            Pill(
                                if (set.name in state.stickerSetsDone) "✅ +${set.reward} 🪙" else "$have / ${list.size}",
                                color = if (have == list.size) p.mint.copy(alpha = 0.3f) else p.violet.copy(alpha = 0.16f),
                            )
                        }
                    }
                    items(list.chunked(4), key = { "sr_${set.name}_${it.first().id}" }) { row ->
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            for (sticker in row) {
                                val n = state.stickers[sticker.id] ?: 0
                                StickerTile(sticker.emoji, sticker.name, n, sticker.rare, Modifier.weight(1f))
                            }
                            repeat(4 - row.size) { Spacer(Modifier.weight(1f)) }
                        }
                    }
                }
            }
        }
    }

    detail?.let { form ->
        FormDialog(state, form) { detail = null }
    }
}

@Composable
private fun AlbumEntry(state: GameState, form: Form, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val p = LocalPalette.current
    val found = state.discovered(form)
    val shiny = state.discovered(form, shiny = true)
    val look = remember(form, shiny) { PetLook(form, Mood.HAPPY, line = form.line, shiny = shiny) }
    SurfaceCard(
        modifier,
        onClick = if (found) onClick else null,
        contentPadding = PaddingValues(6.dp),
        color = if (form.isLegend && found) (if (p.dark) Color(0xFF4A3A1E) else Color(0xFFFFF1C4)) else p.surface,
    ) {
        Box(Modifier.fillMaxWidth().aspectRatio(1f)) {
            PetPortrait(
                look,
                Modifier.fillMaxSize(),
                animated = false,
                sizeFactor = 0.78f,
                shadow = found,
                silhouette = if (found) null else p.text.copy(alpha = if (p.dark) 0.35f else 0.2f).toArgb(),
            )
            if (shiny) Text("✨", fontSize = 14.sp, modifier = Modifier.align(Alignment.TopEnd))
            if (form.isLegend) Text("👑", fontSize = 13.sp, modifier = Modifier.align(Alignment.TopStart))
        }
        Text(
            if (found) form.title else "???",
            style = MaterialTheme.typography.labelSmall,
            color = if (found) p.text else p.textMuted,
            textAlign = TextAlign.Center,
            maxLines = 1,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun StickerTile(emoji: String, name: String, count: Int, rare: Boolean, modifier: Modifier = Modifier) {
    val p = LocalPalette.current
    val have = count > 0
    val shape = RoundedCornerShape(18.dp)
    Column(
        modifier
            .clip(shape)
            .background(
                if (have) {
                    Brush.verticalGradient(
                        if (rare) listOf(Color(0xFFFFF2B8), Color(0xFFFFC83D)) else listOf(p.surface, p.surfaceAlt),
                    )
                } else {
                    Brush.verticalGradient(listOf(p.track, p.track))
                },
            )
            .border(if (rare) 2.dp else 1.dp, if (rare) p.goldDeep.copy(alpha = if (have) 0.8f else 0.3f) else p.textMuted.copy(alpha = 0.15f), shape)
            .padding(vertical = 8.dp, horizontal = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box {
            Text(if (have) emoji else "❔", fontSize = 30.sp, modifier = Modifier.alpha(if (have) 1f else 0.4f))
            if (count > 1) {
                Text(
                    "×$count",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .clip(RoundedCornerShape(50))
                        .background(p.violet)
                        .padding(horizontal = 4.dp),
                )
            }
        }
        Text(
            if (have) name else "???",
            style = MaterialTheme.typography.labelSmall,
            color = if (have && rare) Color(0xFF6A4A00) else if (have) p.text else p.textMuted,
            maxLines = 1,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun FormDialog(state: GameState, form: Form, onDismiss: () -> Unit) {
    val p = LocalPalette.current
    val shinyFound = state.discovered(form, shiny = true)
    var showShiny by remember { mutableStateOf(false) }
    val look = remember(form, showShiny) { PetLook(form, Mood.HAPPY, line = form.line, shiny = showShiny) }
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { ClayTextButton("Schön!", onDismiss, small = true) },
        dismissButton = {
            if (shinyFound) {
                ClayTextButton(if (showShiny) "Normal" else "✨ Schillernd", { showShiny = !showShiny }, small = true, color = p.gold, deep = p.goldDeep)
            }
        },
        title = {
            Column {
                Text(form.title, color = p.text)
                Text("${form.line.emoji} ${form.line.title} · ${form.stage.title}", style = MaterialTheme.typography.bodySmall, color = p.textMuted)
            }
        },
        text = {
            Column(Modifier.navigationBarsPadding(), horizontalAlignment = Alignment.CenterHorizontally) {
                PetPortrait(look, Modifier.size(200.dp))
                Spacer(Modifier.height(8.dp))
                Text(form.description, style = MaterialTheme.typography.bodyMedium, color = p.textMuted, textAlign = TextAlign.Center)
                if (form.isLegend) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "👑 Legendär: nur mit perfekter Pflege, ohne Pflegefehler.",
                        style = MaterialTheme.typography.labelMedium,
                        color = p.goldDeep,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        },
        containerColor = p.surface,
        shape = RoundedCornerShape(28.dp),
    )
}
