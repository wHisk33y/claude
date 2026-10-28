package de.knuffi.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
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
import androidx.compose.foundation.shape.CircleShape
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
import de.knuffi.app.ui.components.rememberHaptic
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
    var hatchFamily by remember { mutableStateOf<de.knuffi.core.FamilyEgg?>(null) }
    var giveLine by remember { mutableStateOf<EggLine?>(null) }
    var giveFamily by remember { mutableStateOf<de.knuffi.core.FamilyEgg?>(null) }
    var release by remember { mutableStateOf<Pet?>(null) }
    // "Aufräumen": pick several pets and say goodbye to all of them at once.
    var tidying by remember { mutableStateOf(false) }
    var picked by remember { mutableStateOf(emptySet<Long>()) }
    var confirmTidy by remember { mutableStateOf(false) }
    val active = state.pet
    val full = active != null && active.alive && state.resting.size >= state.restSlots
    // Same rules as the engine: travellers stay, the active pet only leaves if someone takes over.
    val free = state.resting.filter { state.onTrip(it.id) == null }
    val stays = free.filter { it.id !in picked }
    val activeLeaves = active != null && active.alive && active.id in picked
    val successor = if (activeLeaves) stays.firstOrNull { !it.isEgg } ?: stays.firstOrNull() else null
    val leaving = listOfNotNull(active?.takeIf { activeLeaves }) + free.filter { it.id in picked }
    fun toggle(id: Long) {
        picked = if (id in picked) picked - id else picked + id
    }
    fun stopTidying() {
        tidying = false
        picked = emptySet()
    }
    BackHandler(enabled = tidying) { stopTidying() }

    Column(Modifier.fillMaxSize()) {
        PageHeader("Kuschelhaus", onBack) { CoinPill(state.coins) }
        LazyColumn(
            Modifier.fillMaxWidth().weight(1f),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (active != null) {
                item {
                    Box(Modifier.fillMaxWidth().staggered(0)) {
                        SurfaceCard(Modifier.fillMaxWidth()) {
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
                        if (tidying) {
                            val canLeave = activeLeaves || (active.alive && stays.isNotEmpty())
                            PickOverlay(
                                picked = activeLeaves,
                                enabled = canLeave,
                                label = when {
                                    activeLeaves -> "Zieht aus"
                                    !canLeave && active.alive -> "Bleibt bei dir"
                                    else -> null
                                },
                                onToggle = { toggle(active.id) },
                            )
                        }
                    }
                }
            }

            if (state.familyEggs.isNotEmpty()) {
                item { SectionHeader("Nachwuchs", "💕") }
                item {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        items(state.familyEggs, key = { it.id }) { egg ->
                            FamilyEggCard(egg, full, onGiveAway = { giveFamily = egg }) { hatchFamily = egg }
                        }
                    }
                }
            }
            if (state.totalEggs > 0) {
                item { SectionHeader("Deine Eier", "🥚") }
                item {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        items(EggLine.entries.filter { state.eggCount(it) > 0 }) { line ->
                            EggCard(state, line, full, onGiveAway = { giveLine = line }) { hatchLine = line }
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
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        if (tidying) {
                            "Tippe alle Haustiere an, die ausziehen sollen. Haustiere auf Ausflug bleiben hier."
                        } else {
                            "Hier ruhen deine anderen Haustiere. Sie werden nicht hungrig und nicht müde. Nur dein aktives Haustier braucht Pflege."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = p.textMuted,
                        modifier = Modifier.weight(1f),
                    )
                    if (!tidying && state.resting.isNotEmpty()) {
                        Spacer(Modifier.width(10.dp))
                        ClayTextButton("Aufräumen", { tidying = true }, emoji = "🧹", small = true, color = Color(0xFF9A8FB0), deep = Color(0xFF6E6484))
                    }
                }
            }
            val slots = state.resting.map<Pet, Pet?> { it } + List((state.restSlots - state.resting.size).coerceAtLeast(0)) { null }
            items(slots.chunked(2)) { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    for (pet in row) {
                        if (pet == null) {
                            EmptySlot(Modifier.weight(1f))
                        } else {
                            Box(Modifier.weight(1f)) {
                                RestingCard(
                                    state, pet, now, Modifier.fillMaxWidth(),
                                    onSwitch = { GameRepository.perform(Action.SwitchPet(pet.id)) },
                                    onTrip = onTrips,
                                    onRelease = { release = pet },
                                )
                                if (tidying) {
                                    val travelling = state.onTrip(pet.id) != null
                                    // The last pet that could take over for a leaving active pet can't leave too.
                                    val lastHope = pet.id == successor?.id && stays.size == 1
                                    PickOverlay(
                                        picked = pet.id in picked && !travelling,
                                        enabled = !travelling && !lastHope,
                                        label = when {
                                            travelling -> "Auf Ausflug"
                                            pet.id == successor?.id -> "Wird aktiv ⭐"
                                            pet.id in picked -> "Zieht aus"
                                            else -> null
                                        },
                                        onToggle = { toggle(pet.id) },
                                    )
                                }
                            }
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
        if (tidying) {
            TidyBar(
                count = leaving.size,
                allPicked = free.isNotEmpty() && free.all { it.id in picked } && !activeLeaves,
                onAll = { picked = if (free.all { it.id in picked } && !activeLeaves) emptySet() else free.map { it.id }.toSet() },
                onCancel = { stopTidying() },
                onRelease = { confirmTidy = true },
            )
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
    hatchFamily?.let { egg ->
        NameDialog(
            title = "💕 ${egg.title}",
            text = if (active != null && active.alive) {
                "${active.name} kuschelt sich solange ins Kuschelhaus. Wie soll das Kleine heißen?"
            } else {
                "Wie soll das Kleine heißen?"
            },
            confirm = if (egg.line.real) "Abholen" else "Ausbrüten",
            onConfirm = {
                GameRepository.perform(Action.HatchFamilyEgg(egg.id, it))
                hatchFamily = null
                onBack()
            },
            onDismiss = { hatchFamily = null },
        )
    }
    giveLine?.let { line ->
        ConfirmDialog(
            title = "${line.emoji} ${line.title} abgeben?",
            text = "Das Ei zieht in die Eier-Gärtnerei, wo es liebevoll versorgt wird und später eine eigene Familie findet. Du bekommst ${de.knuffi.core.Eggs.giveAwayCoins(line)} Münzen.",
            confirm = "Abgeben",
            onConfirm = {
                GameRepository.perform(Action.GiveAwayEgg(line))
                giveLine = null
            },
            onDismiss = { giveLine = null },
        )
    }
    giveFamily?.let { egg ->
        ConfirmDialog(
            title = "💕 ${egg.title} abgeben?",
            text = "Das Ei zieht in die Eier-Gärtnerei, wo es liebevoll versorgt wird und später eine eigene Familie findet. Du bekommst ${de.knuffi.core.Eggs.FAMILY_EGG_COINS} Münzen.",
            confirm = "Abgeben",
            onConfirm = {
                GameRepository.perform(Action.GiveAwayFamilyEgg(egg.id))
                giveFamily = null
            },
            onDismiss = { giveFamily = null },
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
    if (confirmTidy && leaving.isNotEmpty()) {
        ConfirmDialog(
            title = if (leaving.size == 1) "${leaving[0].name} verabschieden?" else "${leaving.size} Haustiere verabschieden?",
            text = goodbyeText(leaving, successor),
            confirm = "Verabschieden",
            onConfirm = {
                GameRepository.perform(Action.ReleasePets(leaving.map { it.id }.toSet()))
                confirmTidy = false
                stopTidying()
            },
            onDismiss = { confirmTidy = false },
            danger = true,
        )
    }
}

private fun namesText(names: List<String>): String = when {
    names.size == 1 -> names[0]
    names.size <= 5 -> names.dropLast(1).joinToString(", ") + " und " + names.last()
    else -> names.take(4).joinToString(", ") + " und ${names.size - 4} weitere"
}

private fun goodbyeText(leaving: List<Pet>, successor: Pet?): String = buildString {
    val one = leaving.size == 1
    append("${namesText(leaving.map { it.name })} ${if (one) "zieht" else "ziehen"} dann zu ${if (one) "einer lieben Familie" else "lieben Familien"}.")
    val stars = leaving.count { !it.isEgg }
    if (stars > 0) append(if (stars == 1) " Am Sternenhimmel leuchtet ein Stern zur Erinnerung." else " Am Sternenhimmel leuchten $stars Sterne zur Erinnerung.")
    val special = leaving.filter { it.shiny || it.form.isLegend }
    if (special.isNotEmpty()) {
        append("\n\nAchtung, ganz besonders: ")
        append(namesText(special.map { "${it.name}${if (it.shiny) " ✨" else ""}${if (it.form.isLegend) " 👑" else ""}" }))
    }
    if (successor != null) append("\n\n${successor.name} wird dein aktives Haustier.")
    append("\n\nDas kann man nicht rückgängig machen.")
}

@Composable
private fun TidyBar(count: Int, allPicked: Boolean, onAll: () -> Unit, onCancel: () -> Unit, onRelease: () -> Unit) {
    val p = LocalPalette.current
    SurfaceCard(
        Modifier.fillMaxWidth().navigationBarsPadding().padding(start = 16.dp, end = 16.dp, bottom = 10.dp, top = 4.dp),
        contentPadding = PaddingValues(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                if (count == 0) "Nichts ausgewählt" else "$count ausgewählt",
                style = MaterialTheme.typography.titleSmall,
                color = p.text,
                modifier = Modifier.weight(1f),
            )
            ClayTextButton(if (allPicked) "Keine" else "Alle", onAll, small = true, color = p.sky, deep = p.skyDeep)
            Spacer(Modifier.width(6.dp))
            ClayTextButton("Fertig", onCancel, small = true, color = Color(0xFF9A8FB0), deep = Color(0xFF6E6484))
        }
        Spacer(Modifier.height(8.dp))
        ClayTextButton(
            if (count <= 1) "Verabschieden" else "$count verabschieden",
            onRelease,
            emoji = "👋",
            enabled = count > 0,
            modifier = Modifier.fillMaxWidth(),
            color = p.red,
            deep = p.redDeep,
        )
    }
}

/** Selection layer on top of a pet card while tidying up the Kuschelhaus. */
@Composable
private fun BoxScope.PickOverlay(picked: Boolean, enabled: Boolean, label: String?, onToggle: () -> Unit) {
    val p = LocalPalette.current
    val haptic = rememberHaptic()
    val shape = RoundedCornerShape(24.dp)
    Box(
        Modifier
            .matchParentSize()
            .clip(shape)
            .background(
                when {
                    picked -> p.red.copy(alpha = 0.16f)
                    !enabled -> p.surface.copy(alpha = 0.6f)
                    else -> Color.Transparent
                },
            )
            .border(if (picked) 3.dp else 2.dp, if (picked) p.red else p.textMuted.copy(alpha = if (enabled) 0.35f else 0.15f), shape)
            // Always swallow taps so the buttons below can't be pressed while tidying.
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                if (enabled) {
                    haptic()
                    onToggle()
                }
            },
    ) {
        if (enabled) {
            Box(
                Modifier
                    .padding(10.dp)
                    .size(30.dp)
                    .clip(CircleShape)
                    .background(if (picked) p.red else p.surface)
                    .border(2.dp, if (picked) p.redDeep else p.textMuted.copy(alpha = 0.5f), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                if (picked) Text("✓", color = Color.White, style = MaterialTheme.typography.titleSmall)
            }
        }
        if (label != null) {
            Pill(
                label,
                Modifier.align(Alignment.Center),
                color = if (picked) p.red else p.surface,
                textColor = if (picked) Color.White else p.text,
            )
        }
    }
}

@Composable
private fun FamilyEggCard(egg: de.knuffi.core.FamilyEgg, full: Boolean, onGiveAway: () -> Unit, onHatch: () -> Unit) {
    val p = LocalPalette.current
    val look = remember(egg.id) { eggPreview(egg.line).let { it.copy(blend = egg.blend, fur = egg.fur ?: it.fur) } }
    SurfaceCard(Modifier.width(170.dp), contentPadding = PaddingValues(12.dp), color = if (p.dark) p.surfaceAlt else Color(0xFFFFEEF6)) {
        Box {
            PetPortrait(look, Modifier.fillMaxWidth().height(96.dp), sizeFactor = 0.8f)
            Pill("💕", Modifier.align(Alignment.TopEnd), color = p.pink.copy(alpha = 0.3f))
        }
        Text(egg.line.title, style = MaterialTheme.typography.titleSmall, color = p.text, maxLines = 1)
        Text("Von ${egg.parentNames.joinToString(" und ")}. Es hat etwas von beiden!", style = MaterialTheme.typography.bodySmall, color = p.textMuted, maxLines = 2, minLines = 2)
        Spacer(Modifier.height(8.dp))
        ClayTextButton(
            if (full) "Kein Platz" else if (egg.line.real) "Abholen" else "Ausbrüten",
            onHatch,
            small = true,
            enabled = !full,
            modifier = Modifier.fillMaxWidth(),
            color = p.pink,
            deep = p.pinkDeep,
        )
        Spacer(Modifier.height(6.dp))
        ClayTextButton("Abgeben", onGiveAway, small = true, emoji = "🌱", modifier = Modifier.fillMaxWidth(), color = p.violet, deep = p.violetDeep)
    }
}

@Composable
private fun EggCard(state: GameState, line: EggLine, full: Boolean, onGiveAway: () -> Unit, onHatch: () -> Unit) {
    val p = LocalPalette.current
    val look = remember(line) { eggPreview(line) }
    SurfaceCard(Modifier.width(150.dp), contentPadding = PaddingValues(12.dp)) {
        Box {
            PetPortrait(look, Modifier.fillMaxWidth().height(96.dp), sizeFactor = 0.8f)
            Pill("×${state.eggCount(line)}", Modifier.align(Alignment.TopEnd), color = p.gold.copy(alpha = 0.3f))
        }
        Text(line.title, style = MaterialTheme.typography.titleSmall, color = p.text, maxLines = 1)
        Text(line.description, style = MaterialTheme.typography.bodySmall, color = p.textMuted, maxLines = 2, minLines = 2)
        Spacer(Modifier.height(8.dp))
        ClayTextButton(
            if (full) "Kein Platz" else if (line.real) "Abholen" else "Ausbrüten",
            onHatch,
            small = true,
            enabled = !full,
            modifier = Modifier.fillMaxWidth(),
            color = p.mint,
            deep = p.mintDeep,
        )
        Spacer(Modifier.height(6.dp))
        ClayTextButton("Abgeben", onGiveAway, small = true, emoji = "🌱", modifier = Modifier.fillMaxWidth(), color = p.violet, deep = p.violetDeep)
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
        val host = state.pet
        if (host != null && !pet.isEgg) {
            val pts = state.friendship[de.knuffi.core.Friends.key(host.id, pet.id)] ?: 0
            val lvl = de.knuffi.core.Friends.level(pts)
            Text("${"💗".repeat(lvl)}${"🤍".repeat(4 - lvl)} ${de.knuffi.core.Friends.titles[lvl]}", style = MaterialTheme.typography.labelSmall, color = p.textMuted, maxLines = 1)
        }
        Spacer(Modifier.height(8.dp))
        if (trip == null) {
            ClayTextButton("Aktivieren", onSwitch, small = true, modifier = Modifier.fillMaxWidth(), color = p.pink, deep = p.pinkDeep)
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                if (!pet.isEgg) {
                    ClayTextButton("🎒", onTrip, small = true, modifier = Modifier.weight(1f), color = p.sky, deep = p.skyDeep)
                }
                if (!pet.isEgg && state.pet?.alive == true && state.pet?.isEgg == false) {
                    val visiting = state.visit?.petId == pet.id || state.visit2?.petId == pet.id
                    ClayTextButton(
                        if (visiting) "↩️" else "🏠",
                        { GameRepository.perform(if (visiting) Action.SendHome(pet.id) else Action.Invite(pet.id)) },
                        small = true,
                        modifier = Modifier.weight(1f),
                        color = if (visiting) p.orange else p.mint,
                        deep = if (visiting) p.orangeDeep else p.mintDeep,
                    )
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
            .height(258.dp)
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
