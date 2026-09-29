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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.knuffi.app.data.GameRepository
import de.knuffi.app.ui.components.ClayTextButton
import de.knuffi.app.ui.components.EmojiTile
import de.knuffi.app.ui.components.GlossyBar
import de.knuffi.app.ui.components.PageHeader
import de.knuffi.app.ui.components.Pill
import de.knuffi.app.ui.components.SurfaceCard
import de.knuffi.app.ui.components.lighter
import de.knuffi.app.ui.theme.LocalPalette
import de.knuffi.core.Action
import de.knuffi.core.Catalog
import de.knuffi.core.EventCalendar
import de.knuffi.core.EventOffer
import de.knuffi.core.EventShop
import de.knuffi.core.GameState
import de.knuffi.core.SeasonPass
import de.knuffi.core.Stickers
import java.time.temporal.ChronoUnit

// ------------------------------------------------------------------ Season pass

@Composable
fun PassScreen(state: GameState, onBack: () -> Unit) {
    val p = LocalPalette.current
    val now = rememberNow(60_000L)
    val today = remember(now / 3_600_000L) { EventCalendar.date(now, GameRepository.zone) }
    val season = EventCalendar.season(today)
    val daysLeft = ChronoUnit.DAYS.between(today, EventCalendar.seasonEnd(today)) + 1
    val tier = SeasonPass.tier(state.pass.xp)
    val inTier = if (tier >= SeasonPass.TIERS) 1f else (state.pass.xp % SeasonPass.XP_PER_TIER) / SeasonPass.XP_PER_TIER.toFloat()
    val (exA, exB) = SeasonPass.exclusives(season)

    Column(Modifier.fillMaxSize()) {
        PageHeader("Jahreszeiten-Pass", onBack)
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                val shape = RoundedCornerShape(28.dp)
                Column(
                    Modifier
                        .fillMaxWidth()
                        .shadow(12.dp, shape, ambientColor = p.violet, spotColor = p.violet)
                        .clip(shape)
                        .background(Brush.linearGradient(listOf(p.violet, p.pink)))
                        .padding(16.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(season.emoji, fontSize = 38.sp)
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text("${season.title}-Pass", style = MaterialTheme.typography.titleLarge, color = Color.White)
                            Text("Noch $daysLeft Tage · Stufe $tier von ${SeasonPass.TIERS}", style = MaterialTheme.typography.bodyMedium, color = Color.White.copy(alpha = 0.9f))
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    GlossyBar(inTier, p.gold, p.goldDeep, Modifier.fillMaxWidth(), height = 12.dp)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        if (tier >= SeasonPass.TIERS) "Alles geschafft! 🎉" else "${state.pass.xp % SeasonPass.XP_PER_TIER} / ${SeasonPass.XP_PER_TIER} Pass-Punkte bis Stufe ${tier + 1}",
                        style = MaterialTheme.typography.labelMedium,
                        color = Color.White,
                    )
                    Spacer(Modifier.height(10.dp))
                    Text(
                        "Pass-Punkte bekommst du fürs Einloggen, Pflegen, Aufgaben, Minispiele, Ernten und Ausflüge. Nur in dieser Jahreszeit: ${Catalog[exA]?.emoji ?: ""} ${Catalog[exA]?.name ?: ""} und ${Catalog[exB]?.emoji ?: ""} ${Catalog[exB]?.name ?: ""}!",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.9f),
                    )
                }
            }
            items((1..SeasonPass.TIERS).toList(), key = { it }) { t ->
                val reward = SeasonPass.reward(season, t)
                val claimed = t in state.pass.claimed
                val reached = t <= tier
                val special = reward.egg || reward.itemId == exA || reward.itemId == exB
                SurfaceCard(
                    Modifier.fillMaxWidth().alpha(if (reached || special) 1f else 0.65f),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
                    color = if (special) (if (p.dark) Color(0xFF4A3A1E) else Color(0xFFFFF4D2)) else p.surface,
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(if (reached) Brush.verticalGradient(listOf(p.violet.lighter(0.3f), p.violet)) else Brush.verticalGradient(listOf(p.track, p.track))),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text("$t", style = MaterialTheme.typography.labelLarge, color = if (reached) Color.White else p.textMuted)
                        }
                        Spacer(Modifier.width(12.dp))
                        Text(reward.emoji, fontSize = 26.sp)
                        Spacer(Modifier.width(10.dp))
                        Text(reward.label, style = MaterialTheme.typography.titleSmall, color = p.text, modifier = Modifier.weight(1f), maxLines = 2)
                        when {
                            claimed -> Text("✅", fontSize = 22.sp)
                            reached -> ClayTextButton("Holen", { GameRepository.perform(Action.ClaimPass(t)) }, small = true, color = p.mint, deep = p.mintDeep)
                            else -> Text("🔒", fontSize = 18.sp, modifier = Modifier.alpha(0.6f))
                        }
                    }
                }
            }
        }
    }
}

// ------------------------------------------------------------------ Event shop

@Composable
fun EventShopScreen(state: GameState, onBack: () -> Unit) {
    val p = LocalPalette.current
    val now = rememberNow(60_000L)
    val today = remember(now / 3_600_000L) { EventCalendar.date(now, GameRepository.zone) }
    val event = EventCalendar.active(today)

    Column(Modifier.fillMaxSize()) {
        PageHeader("Fest-Laden", onBack) {
            if (event != null) Pill("${event.tokenEmoji} ${state.event.tokens}", color = p.gold.copy(alpha = 0.3f))
        }
        if (event == null) {
            val (next, days) = EventCalendar.next(today)
            Column(Modifier.fillMaxSize().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                Text(next.emoji, fontSize = 64.sp)
                Spacer(Modifier.height(12.dp))
                Text("Gerade ist kein Fest", style = MaterialTheme.typography.titleLarge, color = p.text)
                Text("${next.title} beginnt in $days Tagen.", style = MaterialTheme.typography.bodyMedium, color = p.textMuted, textAlign = TextAlign.Center)
            }
        } else {
            EventOffers(state, event, today)
        }
    }
}

@Composable
private fun EventOffers(state: GameState, event: de.knuffi.core.SeasonEvent, today: java.time.LocalDate) {
    val (from, to) = eventColors(event)
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            val shape = RoundedCornerShape(28.dp)
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(shape)
                    .background(Brush.linearGradient(listOf(from, to)))
                    .border(1.5.dp, Color.White.copy(alpha = 0.4f), shape)
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(event.emoji, fontSize = 40.sp)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(event.title, style = MaterialTheme.typography.titleLarge, color = Color.White)
                    Text(
                        "Noch ${EventCalendar.daysLeft(event, today)} Tage. ${event.tokenName} ${event.tokenEmoji} gibt es für Aufgaben, Minispiele, Ernten, Ausflüge und fürs tägliche Vorbeischauen.",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.92f),
                    )
                }
            }
        }
        items(EventShop.offers(event), key = { it.id }) { offer ->
            OfferRow(state, offer, event.tokenEmoji)
        }
    }
}

@Composable
private fun OfferRow(state: GameState, offer: EventOffer, token: String) {
    val p = LocalPalette.current
    val bought = state.event.bought[offer.id] ?: 0
    val soldOut = bought >= offer.limit
    val egg = offer.egg
    val sticker = offer.sticker
    val (emoji, title, subtitle) = when {
        egg != null -> Triple(egg.emoji, egg.title, egg.description)
        sticker != null -> {
            val st = Stickers[sticker]
            Triple(st?.emoji ?: "🎴", "Sticker: ${st?.name ?: ""}", "Ein seltener Fest-Sticker fürs Album.")
        }
        else -> {
            val item = Catalog[offer.id]
            Triple(item?.emoji ?: "🎁", item?.name ?: offer.id, item?.description ?: "")
        }
    }
    SurfaceCard(Modifier.fillMaxWidth().alpha(if (soldOut) 0.6f else 1f)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            EmojiTile(emoji, size = 52.dp, color = p.gold)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleSmall, color = p.text)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = p.textMuted, maxLines = 2)
                if (offer.limit > 1) Text("$bought / ${offer.limit} gekauft", style = MaterialTheme.typography.labelSmall, color = p.textMuted)
            }
            Spacer(Modifier.width(8.dp))
            if (soldOut) {
                Text("✅", fontSize = 22.sp)
            } else {
                ClayTextButton(
                    "${offer.price} $token",
                    { GameRepository.perform(Action.BuyEventOffer(offer.id)) },
                    small = true,
                    enabled = state.event.tokens >= offer.price,
                    color = p.gold,
                    deep = p.goldDeep,
                )
            }
        }
    }
}
