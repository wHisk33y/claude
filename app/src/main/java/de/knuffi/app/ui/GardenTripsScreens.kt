package de.knuffi.app.ui

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.knuffi.app.data.GameRepository
import de.knuffi.app.render.PetLook
import de.knuffi.app.ui.components.ClayTextButton
import de.knuffi.app.ui.components.CoinPill
import de.knuffi.app.ui.components.EmojiTile
import de.knuffi.app.ui.components.GlossyBar
import de.knuffi.app.ui.components.PageHeader
import de.knuffi.app.ui.components.Pill
import de.knuffi.app.ui.components.SectionHeader
import de.knuffi.app.ui.components.SurfaceCard
import de.knuffi.app.ui.components.staggered
import de.knuffi.app.ui.theme.LocalPalette
import de.knuffi.core.Action
import de.knuffi.core.Catalog
import de.knuffi.core.Destination
import de.knuffi.core.Engine
import de.knuffi.core.EventCalendar
import de.knuffi.core.GameState
import de.knuffi.core.Garden
import de.knuffi.core.Plot

// ------------------------------------------------------------------ Garden

@Composable
fun GardenScreen(state: GameState, onBack: () -> Unit) {
    val p = LocalPalette.current
    val now = rememberNow(1_000L)
    var sowPlot by remember { mutableStateOf<Int?>(null) }
    val season = remember(now / 3_600_000L) { EventCalendar.season(EventCalendar.date(now, GameRepository.zone)) }
    val plots = state.garden.plots

    Column(Modifier.fillMaxSize()) {
        PageHeader("Garten", onBack) { CoinPill(state.coins) }
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                SurfaceCard(Modifier.fillMaxWidth().staggered(0), color = if (p.dark) p.surfaceAlt else Color(0xFFEFFBEA)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(season.emoji, fontSize = 30.sp)
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text("Es ist ${season.title}", style = MaterialTheme.typography.titleSmall, color = p.text)
                            val fast = Catalog.seeds.filter { it.season == season }.joinToString(" ") { it.emoji }
                            Text(
                                if (fast.isEmpty()) "Säen, gießen (bis zu 3×) und ernten!" else "Jetzt wächst besonders schnell: $fast",
                                style = MaterialTheme.typography.bodySmall,
                                color = p.textMuted,
                            )
                        }
                    }
                }
            }
            items(plots.withIndex().toList().chunked(3)) { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    for ((i, plot) in row) {
                        PlotCard(plot, now, Modifier.weight(1f), onSow = { sowPlot = i }, onWater = { GameRepository.perform(Action.Water(i)) }, onHarvest = { GameRepository.perform(Action.Harvest(i)) })
                    }
                    repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
            if (plots.size < Garden.MAX_PLOTS) {
                item {
                    val price = Garden.plotPrice(plots.size)
                    SurfaceCard(Modifier.fillMaxWidth()) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            EmojiTile("🪴", size = 48.dp, color = p.mint)
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text("Neues Beet", style = MaterialTheme.typography.titleSmall, color = p.text)
                                Text("${plots.size} von ${Garden.MAX_PLOTS} Beeten", style = MaterialTheme.typography.bodySmall, color = p.textMuted)
                            }
                            ClayTextButton("$price 🪙", { GameRepository.perform(Action.BuyPlot) }, small = true, enabled = state.coins >= price, color = p.gold, deep = p.goldDeep)
                        }
                    }
                }
            }
            item { SectionHeader("Deine Samen", "🌱") }
            val seeds = Catalog.seeds.filter { state.count(it.id) > 0 }
            if (seeds.isEmpty()) {
                item {
                    Text("Keine Samen mehr. Im Shop gibt es neue, und manchmal bringen deine Haustiere welche vom Ausflug mit.", style = MaterialTheme.typography.bodyMedium, color = p.textMuted)
                }
            }
            items(seeds) { seed ->
                SurfaceCard(Modifier.fillMaxWidth(), contentPadding = PaddingValues(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        EmojiTile(seed.emoji, size = 42.dp, color = p.mint)
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text("${seed.name} ×${state.count(seed.id)}", style = MaterialTheme.typography.titleSmall, color = p.text)
                            val yieldItem = seed.yieldId?.let { Catalog[it] }
                            Text(
                                "${countdown(Catalog.growMs(seed, season))} · ergibt ${seed.yieldCount}× ${yieldItem?.emoji ?: ""} ${yieldItem?.name ?: ""}${if (seed.season == season) " · ${season.emoji} schneller" else ""}",
                                style = MaterialTheme.typography.bodySmall,
                                color = p.textMuted,
                            )
                        }
                    }
                }
            }
        }
    }

    sowPlot?.let { index ->
        SeedDialog(state, onPick = {
            GameRepository.perform(Action.Plant(index, it))
            sowPlot = null
        }, onDismiss = { sowPlot = null })
    }
}

@Composable
private fun PlotCard(plot: Plot, now: Long, modifier: Modifier = Modifier, onSow: () -> Unit, onWater: () -> Unit, onHarvest: () -> Unit) {
    val p = LocalPalette.current
    val seed = plot.seed?.let { Catalog[it] }
    val yieldItem = seed?.yieldId?.let { Catalog[it] }
    val ready = plot.ready(now)
    val progress = if (seed == null) 0f else plot.progress(now)
    val bounce by rememberInfiniteTransition(label = "plant").animateFloat(0f, 1f, infiniteRepeatable(tween(700), RepeatMode.Reverse), label = "b")
    val shape = RoundedCornerShape(22.dp)
    Column(
        modifier
            .clip(shape)
            .background(Brush.verticalGradient(listOf(if (p.dark) Color(0xFF2C3E5A) else Color(0xFFD8F0FF), if (p.dark) Color(0xFF3A5A40) else Color(0xFFBFE8B0))))
            .border(1.dp, Color.White.copy(alpha = 0.4f), shape)
            .padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(Modifier.fillMaxWidth().height(86.dp), contentAlignment = Alignment.BottomCenter) {
            // soil
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(30.dp)
                    .clip(RoundedCornerShape(50))
                    .background(Brush.verticalGradient(listOf(Color(0xFF9A6A42), Color(0xFF6A4424)))),
            )
            val emoji = when {
                seed == null -> ""
                ready -> yieldItem?.emoji ?: "🌾"
                progress < 0.34f -> "🌱"
                progress < 0.7f -> "🌿"
                else -> "🪴"
            }
            if (emoji.isNotEmpty()) {
                Text(
                    emoji,
                    fontSize = (26 + 18 * progress).sp,
                    modifier = Modifier
                        .padding(bottom = 12.dp)
                        .graphicsLayer {
                            if (ready) {
                                translationY = -6.dp.toPx() * bounce
                                rotationZ = (bounce - 0.5f) * 10f
                            }
                        },
                )
            }
            if (plot.waterings > 0 && !ready) {
                Row(Modifier.align(Alignment.TopEnd)) {
                    repeat(plot.waterings) { Text("💧", fontSize = 10.sp) }
                }
            }
        }
        Spacer(Modifier.height(6.dp))
        when {
            seed == null -> ClayTextButton("Säen", onSow, small = true, modifier = Modifier.fillMaxWidth(), color = p.mint, deep = p.mintDeep)
            ready -> ClayTextButton("Ernten", onHarvest, small = true, modifier = Modifier.fillMaxWidth(), color = p.gold, deep = p.goldDeep)
            else -> {
                GlossyBar(progress, p.mint, p.mintDeep, Modifier.fillMaxWidth(), height = 8.dp)
                Text(countdown(plot.readyAt - now), style = MaterialTheme.typography.labelSmall, color = if (p.dark) Color.White else p.text, maxLines = 1)
                val canWater = plot.waterings < Garden.MAX_WATERINGS && now - plot.lastWatered >= Garden.WATER_COOLDOWN_MS
                ClayTextButton(
                    if (plot.waterings >= Garden.MAX_WATERINGS) "✓" else "💧",
                    onWater,
                    small = true,
                    enabled = canWater,
                    modifier = Modifier.fillMaxWidth(),
                    color = p.sky,
                    deep = p.skyDeep,
                )
            }
        }
    }
}

@Composable
private fun SeedDialog(state: GameState, onPick: (String) -> Unit, onDismiss: () -> Unit) {
    val p = LocalPalette.current
    val seeds = Catalog.seeds.filter { state.count(it.id) > 0 }
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { ClayTextButton("Schließen", onDismiss, small = true, color = Color(0xFF9A8FB0), deep = Color(0xFF6E6484)) },
        title = { Text("Was möchtest du säen?", color = p.text) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (seeds.isEmpty()) {
                    Text("Du hast keine Samen. Schau im Shop vorbei! 🛍️", color = p.textMuted)
                }
                for (seed in seeds) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(seed.emoji, fontSize = 26.sp)
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(seed.name, style = MaterialTheme.typography.titleSmall, color = p.text)
                            Text("×${state.count(seed.id)} · ${seed.growHours} Std", style = MaterialTheme.typography.bodySmall, color = p.textMuted)
                        }
                        ClayTextButton("Säen", { onPick(seed.id) }, small = true, color = p.mint, deep = p.mintDeep)
                    }
                }
            }
        },
        containerColor = p.surface,
        shape = RoundedCornerShape(28.dp),
    )
}

// ------------------------------------------------------------------ Trips

@Composable
fun TripsScreen(state: GameState, onBack: () -> Unit) {
    val p = LocalPalette.current
    val now = rememberNow(1_000L)
    var pickFor by remember { mutableStateOf<Destination?>(null) }
    val level = Engine.playerLevel(state)
    val available = state.resting.filter { !it.isEgg && it.alive && state.onTrip(it.id) == null }

    Column(Modifier.fillMaxSize()) {
        PageHeader("Ausflüge", onBack) { Pill("Level $level", color = p.violet.copy(alpha = 0.2f)) }
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                Text(
                    "Haustiere aus dem Kuschelhaus können auf Ausflug gehen. Sie bringen Münzen, Samen, Essen, Sticker und manchmal sogar ein Ei mit!",
                    style = MaterialTheme.typography.bodySmall,
                    color = p.textMuted,
                )
            }
            if (state.trips.isNotEmpty()) {
                item { SectionHeader("Unterwegs", "🎒") }
                items(state.trips, key = { "t_${it.petId}" }) { trip ->
                    val pet = state.allPets.firstOrNull { it.id == trip.petId }
                    val done = now >= trip.endsAt
                    val total = (trip.endsAt - trip.startedAt).coerceAtLeast(1L)
                    SurfaceCard(Modifier.fillMaxWidth(), color = if (done) (if (p.dark) Color(0xFF1E4A3E) else Color(0xFFE6FFF4)) else p.surface) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(64.dp)) {
                                if (pet != null) PetPortrait(PetLook.of(pet, state), Modifier.fillMaxSize(), animated = done)
                                Text(trip.destination.emoji, fontSize = 20.sp, modifier = Modifier.align(Alignment.BottomEnd))
                            }
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text("${pet?.name ?: "?"} · ${trip.destination.title}", style = MaterialTheme.typography.titleSmall, color = p.text)
                                Spacer(Modifier.height(4.dp))
                                GlossyBar(((now - trip.startedAt) / total.toFloat()).coerceIn(0f, 1f), p.sky, p.skyDeep, Modifier.fillMaxWidth(), height = 9.dp)
                                Text(
                                    if (done) "Ist wieder da! 🎉" else "Zurück in ${countdown(trip.endsAt - now)}",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = if (done) p.mintDeep else p.textMuted,
                                )
                            }
                            if (done) {
                                Spacer(Modifier.width(8.dp))
                                ClayTextButton("Abholen", { GameRepository.perform(Action.ClaimTrip(trip.petId)) }, small = true, color = p.mint, deep = p.mintDeep)
                            }
                        }
                    }
                }
            }
            item { SectionHeader("Ziele", "🗺️") }
            items(Destination.entries.toList(), key = { it.name }) { d ->
                val locked = level < d.minLevel
                SurfaceCard(Modifier.fillMaxWidth().alpha(if (locked) 0.6f else 1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            Modifier
                                .size(56.dp)
                                .clip(CircleShape)
                                .background(Brush.linearGradient(listOf(destinationColor(d).copy(alpha = 0.55f), destinationColor(d).copy(alpha = 0.25f)))),
                            contentAlignment = Alignment.Center,
                        ) { Text(if (locked) "🔒" else d.emoji, fontSize = 28.sp) }
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(d.title, style = MaterialTheme.typography.titleSmall, color = p.text)
                            Text(d.description, style = MaterialTheme.typography.bodySmall, color = p.textMuted, maxLines = 2)
                            Text(
                                if (locked) "Ab Level ${d.minLevel}" else "⏱ ${d.hours} Std · selten: ${d.egg.emoji} ${d.egg.title}",
                                style = MaterialTheme.typography.labelMedium,
                                color = if (locked) p.redDeep else p.violetDeep,
                            )
                        }
                        if (!locked) {
                            Spacer(Modifier.width(8.dp))
                            ClayTextButton("Los!", { pickFor = d }, small = true, enabled = available.isNotEmpty(), color = p.sky, deep = p.skyDeep)
                        }
                    }
                }
            }
            if (available.isEmpty() && state.trips.isEmpty()) {
                item {
                    Text(
                        if (state.resting.isEmpty()) "Brüte ein zweites Ei aus: dann kann eins deiner Haustiere auf Ausflug gehen, während du dich um das andere kümmerst."
                        else "Alle Haustiere im Kuschelhaus sind schon unterwegs.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = p.textMuted,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().padding(8.dp),
                    )
                }
            }
        }
    }

    pickFor?.let { d ->
        AlertDialog(
            onDismissRequest = { pickFor = null },
            confirmButton = { ClayTextButton("Abbrechen", { pickFor = null }, small = true, color = Color(0xFF9A8FB0), deep = Color(0xFF6E6484)) },
            title = { Text("Wer geht mit zum Ziel ${d.title}?", color = p.text) },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    for (pet in available) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            PetPortrait(PetLook.of(pet, state), Modifier.size(52.dp), animated = false)
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                Text(pet.name, style = MaterialTheme.typography.titleSmall, color = p.text)
                                Text("${pet.form.title} · Lv ${pet.level}", style = MaterialTheme.typography.bodySmall, color = p.textMuted)
                            }
                            ClayTextButton("Los", {
                                GameRepository.perform(Action.StartTrip(pet.id, d))
                                pickFor = null
                            }, small = true, color = p.sky, deep = p.skyDeep)
                        }
                    }
                }
            },
            containerColor = p.surface,
            shape = RoundedCornerShape(28.dp),
        )
    }
}

private fun destinationColor(d: Destination): Color = when (d) {
    Destination.WIESE -> Color(0xFF7ED87A)
    Destination.STRAND -> Color(0xFFFFD166)
    Destination.ZAUBERWALD -> Color(0xFF6AC88A)
    Destination.BERGE -> Color(0xFF8AA8D8)
    Destination.HOEHLE -> Color(0xFF9A8AB8)
    Destination.VULKAN -> Color(0xFFFF7A4D)
    Destination.WOLKEN -> Color(0xFF9AD8FF)
    Destination.MOND -> Color(0xFFB8A8FF)
}
