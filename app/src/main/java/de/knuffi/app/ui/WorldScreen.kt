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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.knuffi.app.data.GameRepository
import de.knuffi.app.ui.components.ClayTextButton
import de.knuffi.app.ui.components.EmojiTile
import de.knuffi.app.ui.components.GlossyBar
import de.knuffi.app.ui.components.ScreenHeader
import de.knuffi.app.ui.components.SurfaceCard
import de.knuffi.app.ui.components.staggered
import de.knuffi.app.ui.theme.LocalPalette
import de.knuffi.core.EventCalendar
import de.knuffi.core.Form
import de.knuffi.core.GameState
import de.knuffi.core.SeasonPass
import de.knuffi.core.TimeUtil
import kotlinx.coroutines.delay

/** Current time in milliseconds, refreshed regularly (for countdowns). */
@Composable
fun rememberNow(periodMs: Long = 1_000L): Long {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(periodMs) {
        while (true) {
            delay(periodMs)
            now = System.currentTimeMillis()
        }
    }
    return now
}

/** "noch 2 Std. 5 Min." style countdown text. */
fun countdown(ms: Long): String = TimeUtil.formatDuration(ms.coerceAtLeast(0L))

/** Things waiting for the player in the world hub (for the navigation badge). */
fun worldBadge(state: GameState, now: Long): Boolean =
    state.garden.plots.any { it.ready(now) } ||
        state.trips.any { now >= it.endsAt } ||
        (1..SeasonPass.tier(state.pass.xp)).any { it !in state.pass.claimed } ||
        (state.totalEggs > 0 && state.resting.size < state.restSlots)

@Composable
fun WorldScreen(state: GameState, onOpen: (Route) -> Unit) {
    val now = rememberNow(5_000L)
    val zone = GameRepository.zone
    val today = remember(now / 600_000L) { EventCalendar.date(now, zone) }
    val event = remember(today) { EventCalendar.active(today) }
    val readyPlots = state.garden.plots.count { it.ready(now) }
    val growing = state.garden.plots.count { !it.empty && !it.ready(now) }
    val tripsBack = state.trips.count { now >= it.endsAt }
    val passTier = SeasonPass.tier(state.pass.xp)
    val passOpen = (1..passTier).count { it !in state.pass.claimed }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = bottomBarSpace()),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { ScreenHeader("Welt", "🌍", "Sammeln, gärtnern und entdecken") }
        item { EventBanner(state, today, Modifier.staggered(0)) { onOpen(Route.EventShop) } }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                WorldTile(
                    "🏡", "Kuschelhaus",
                    "${state.allPets.size} Haustiere · ${state.totalEggs} ${if (state.totalEggs == 1) "Ei" else "Eier"}",
                    Color(0xFFFFB38A), Color(0xFFFF7AB6),
                    badge = if (state.totalEggs > 0) "🥚 ${state.totalEggs}" else null,
                    modifier = Modifier.weight(1f).staggered(1),
                ) { onOpen(Route.Kuschelhaus) }
                WorldTile(
                    "📖", "Album",
                    "${state.discoveredCount} / ${Form.creatures.size} Wesen",
                    Color(0xFFB9A8FF), Color(0xFF7FB8FF),
                    progress = state.discoveredCount / Form.creatures.size.toFloat(),
                    modifier = Modifier.weight(1f).staggered(2),
                ) { onOpen(Route.Album) }
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                WorldTile(
                    "🌻", "Garten",
                    when {
                        readyPlots > 0 -> "$readyPlots bereit zur Ernte!"
                        growing > 0 -> "$growing ${if (growing == 1) "Pflanze wächst" else "Pflanzen wachsen"}"
                        else -> "Säe etwas aus!"
                    },
                    Color(0xFF8EE08A), Color(0xFFFFD84D),
                    badge = if (readyPlots > 0) "🧺 $readyPlots" else null,
                    modifier = Modifier.weight(1f).staggered(3),
                ) { onOpen(Route.Garden) }
                WorldTile(
                    "🗺️", "Ausflüge",
                    when {
                        tripsBack > 0 -> "$tripsBack zurück!"
                        state.trips.isNotEmpty() -> "${state.trips.size} unterwegs"
                        else -> "Schick deine Lieblinge los"
                    },
                    Color(0xFF7FD3FF), Color(0xFF34C99F),
                    badge = if (tripsBack > 0) "🎒 $tripsBack" else null,
                    modifier = Modifier.weight(1f).staggered(4),
                ) { onOpen(Route.Trips) }
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                WorldTile(
                    "👟", "Gassi",
                    "${TimeUtil.formatNumber(state.steps.today)} Schritte heute",
                    Color(0xFFFFD166), Color(0xFFFF9A4D),
                    progress = (state.steps.today / state.settings.stepGoal.coerceAtLeast(1).toFloat()).coerceAtMost(1f),
                    modifier = Modifier.weight(1f).staggered(5),
                ) { onOpen(Route.Walk) }
                WorldTile(
                    EventCalendar.season(today).emoji, "Jahreszeiten-Pass",
                    "Stufe $passTier / ${SeasonPass.TIERS}",
                    Color(0xFFFF9EC4), Color(0xFFB27CFF),
                    badge = if (passOpen > 0) "🎁 $passOpen" else null,
                    progress = passTier / SeasonPass.TIERS.toFloat(),
                    modifier = Modifier.weight(1f).staggered(6),
                ) { onOpen(Route.Pass) }
            }
        }
        if (event == null) {
            item {
                val (next, days) = EventCalendar.next(today)
                SurfaceCard(Modifier.fillMaxWidth().staggered(7)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        EmojiTile(next.emoji, size = 48.dp, color = LocalPalette.current.gold)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text("Nächstes Fest: ${next.title}", style = MaterialTheme.typography.titleSmall, color = LocalPalette.current.text)
                            Text(
                                if (days == 1L) "Schon morgen!" else "In $days Tagen · mit eigenem ${next.egg.title}",
                                style = MaterialTheme.typography.bodySmall,
                                color = LocalPalette.current.textMuted,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun WorldTile(
    emoji: String,
    title: String,
    subtitle: String,
    from: Color,
    to: Color,
    modifier: Modifier = Modifier,
    badge: String? = null,
    progress: Float? = null,
    onClick: () -> Unit,
) {
    val p = LocalPalette.current
    val wobble by rememberInfiniteTransition(label = "tile").animateFloat(-1f, 1f, infiniteRepeatable(tween(1800), RepeatMode.Reverse), label = "w")
    SurfaceCard(modifier.height(172.dp), onClick = onClick, contentPadding = PaddingValues(0.dp)) {
        Box(Modifier.fillMaxSize()) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(92.dp)
                    .background(Brush.linearGradient(listOf(from.copy(alpha = if (p.dark) 0.55f else 0.85f), to.copy(alpha = if (p.dark) 0.45f else 0.75f)))),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    emoji,
                    fontSize = 44.sp,
                    modifier = Modifier.graphicsLayer {
                        rotationZ = wobble * 5f
                        translationY = wobble * 2.dp.toPx()
                    },
                )
            }
            Column(
                Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .padding(12.dp),
            ) {
                Text(title, style = MaterialTheme.typography.titleMedium, color = p.text, maxLines = 1)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = p.textMuted, maxLines = 1)
                if (progress != null) {
                    Spacer(Modifier.height(6.dp))
                    GlossyBar(progress, to, to, Modifier.fillMaxWidth(), height = 8.dp)
                }
            }
            if (badge != null) {
                Text(
                    badge,
                    style = MaterialTheme.typography.labelMedium,
                    color = Color.White,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(8.dp)
                        .shadow(4.dp, RoundedCornerShape(50))
                        .clip(RoundedCornerShape(50))
                        .background(p.red)
                        .padding(horizontal = 8.dp, vertical = 3.dp),
                )
            }
        }
    }
}

@Composable
private fun EventBanner(state: GameState, today: java.time.LocalDate, modifier: Modifier = Modifier, onShop: () -> Unit) {
    val event = EventCalendar.active(today) ?: return
    val days = EventCalendar.daysLeft(event, today)
    val shine by rememberInfiniteTransition(label = "event").animateFloat(0f, 1f, infiniteRepeatable(tween(1400), RepeatMode.Reverse), label = "s")
    val colors = eventColors(event)
    val shape = RoundedCornerShape(28.dp)
    Column(
        modifier
            .fillMaxWidth()
            .shadow(12.dp, shape, ambientColor = colors.first, spotColor = colors.first)
            .clip(shape)
            .background(Brush.linearGradient(listOf(colors.first, colors.second)))
            .border(1.5.dp, Color.White.copy(alpha = 0.4f), shape)
            .padding(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(event.emoji, fontSize = 40.sp, modifier = Modifier.graphicsLayer { scaleX = 1f + 0.08f * shine; scaleY = 1f + 0.08f * shine })
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(event.title, style = MaterialTheme.typography.titleLarge, color = Color.White)
                Text(event.description, style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.92f), maxLines = 2)
            }
        }
        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "${event.tokenEmoji} ${state.event.tokens} ${event.tokenName}",
                style = MaterialTheme.typography.titleSmall,
                color = Color.White,
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(Color.White.copy(alpha = 0.22f))
                    .padding(horizontal = 12.dp, vertical = 6.dp),
            )
            Spacer(Modifier.width(8.dp))
            Text(
                if (days <= 1) "Letzter Tag!" else "Noch $days Tage",
                style = MaterialTheme.typography.labelLarge,
                color = Color.White.copy(alpha = 0.9f),
                modifier = Modifier.weight(1f),
            )
            ClayTextButton("Fest-Laden", onShop, small = true, emoji = "🛍️", color = LocalPalette.current.gold, deep = LocalPalette.current.goldDeep)
        }
        Spacer(Modifier.height(6.dp))
        Text(
            "Sammle ${event.tokenName} ${event.tokenEmoji} mit Aufgaben, Minispielen, Ernten und Ausflügen.",
            style = MaterialTheme.typography.bodySmall,
            color = Color.White.copy(alpha = 0.85f),
        )
    }
}

fun eventColors(event: de.knuffi.core.SeasonEvent): Pair<Color, Color> = when (event) {
    de.knuffi.core.SeasonEvent.NEUJAHR -> Color(0xFF5A5AD6) to Color(0xFFE0A030)
    de.knuffi.core.SeasonEvent.VALENTIN -> Color(0xFFFF5A8A) to Color(0xFFFF9AB8)
    de.knuffi.core.SeasonEvent.OSTERN -> Color(0xFF6CCB7A) to Color(0xFFFFC46A)
    de.knuffi.core.SeasonEvent.SOMMERFEST -> Color(0xFF2EB8E0) to Color(0xFFFFB02E)
    de.knuffi.core.SeasonEvent.HALLOWEEN -> Color(0xFF6A3AB8) to Color(0xFFFF8A2A)
    de.knuffi.core.SeasonEvent.WINTERZAUBER -> Color(0xFF3A7AD6) to Color(0xFF7ED8E8)
}

/** Dialog asking for a name (e.g. when an egg is hatched). */
@Composable
fun NameDialog(title: String, text: String, confirm: String, onConfirm: (String) -> Unit, onDismiss: () -> Unit) {
    val p = LocalPalette.current
    var name by remember { mutableStateOf(OnboardingNames.random()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            ClayTextButton(confirm, { onConfirm(name.trim().ifBlank { OnboardingNames.random() }) }, small = true)
        },
        dismissButton = {
            ClayTextButton("Abbrechen", onDismiss, small = true, color = Color(0xFF9A8FB0), deep = Color(0xFF6E6484))
        },
        title = { Text(title, color = p.text) },
        text = {
            Column {
                Text(text, color = p.textMuted, style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it.take(16) },
                    label = { Text("Name") },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = p.pink,
                        focusedLabelColor = p.pink,
                        cursorColor = p.pink,
                        focusedTextColor = p.text,
                        unfocusedTextColor = p.text,
                    ),
                )
                Spacer(Modifier.height(8.dp))
                ClayTextButton("Zufälliger Name", { name = OnboardingNames.random() }, small = true, emoji = "🎲", color = p.violet, deep = p.violetDeep)
            }
        },
        containerColor = p.surface,
        shape = RoundedCornerShape(28.dp),
    )
}
