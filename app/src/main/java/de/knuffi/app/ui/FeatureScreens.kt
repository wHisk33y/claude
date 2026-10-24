package de.knuffi.app.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.knuffi.app.data.GameRepository
import de.knuffi.app.render.PetLook
import de.knuffi.app.ui.components.ClayTextButton
import de.knuffi.app.ui.components.EmojiTile
import de.knuffi.app.ui.components.GlossyBar
import de.knuffi.app.ui.components.PageHeader
import de.knuffi.app.ui.components.Pill
import de.knuffi.app.ui.components.SectionHeader
import de.knuffi.app.ui.components.SurfaceCard
import de.knuffi.app.ui.components.lighter
import de.knuffi.app.ui.components.staggered
import de.knuffi.app.ui.theme.LocalPalette
import de.knuffi.core.Action
import de.knuffi.core.Catalog
import de.knuffi.core.Chat
import de.knuffi.core.Emotion
import de.knuffi.core.Emotions
import de.knuffi.core.Engine
import de.knuffi.core.EventPrizes
import de.knuffi.core.GameEvent
import de.knuffi.core.GameState
import de.knuffi.core.Item
import de.knuffi.core.Job
import de.knuffi.core.Jobs
import de.knuffi.core.SeasonEvent
import de.knuffi.core.Skins
import de.knuffi.core.Slot
import de.knuffi.core.SpinResult
import de.knuffi.core.Talent
import de.knuffi.core.Talents
import de.knuffi.core.TimeUtil
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val clock = DateTimeFormatter.ofPattern("HH:mm")
private fun timeOf(ms: Long): String = Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault()).format(clock)

// ------------------------------------------------------------------ chat

@Composable
fun ChatScreen(state: GameState, onBack: () -> Unit) {
    val p = LocalPalette.current
    val pet = state.pet ?: return
    val unlocked = pet.level >= Chat.UNLOCK_LEVEL
    var text by remember { mutableStateOf("") }
    val list = rememberLazyListState()
    val messages = state.chat.messages
    val look = remember(state.pet, state.equipped) { PetLook.of(state) }

    LaunchedEffect(Unit) { if (unlocked) GameRepository.perform(Action.OpenChat) }
    LaunchedEffect(messages.size) { if (messages.isNotEmpty()) list.animateScrollToItem(messages.size - 1) }

    fun send(msg: String) {
        if (msg.isBlank()) return
        GameRepository.perform(Action.Chat(msg))
        text = ""
    }

    Column(Modifier.fillMaxSize().imePadding()) {
        PageHeader("Chat mit ${pet.name}", onBack) {
            PetPortrait(look, Modifier.size(44.dp), sizeFactor = 0.85f, shadow = false)
        }
        if (!unlocked) {
            Column(Modifier.fillMaxSize().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                Text("💬", fontSize = 64.sp)
                Spacer(Modifier.height(12.dp))
                Text("Ab Level ${Chat.UNLOCK_LEVEL}", style = MaterialTheme.typography.titleLarge, color = p.text)
                Text(
                    "${pet.name} ist noch zu klein zum Reden. Sammle Erfahrung mit Füttern, Spielen und Minispielen. Noch ${Chat.UNLOCK_LEVEL - pet.level} Level!",
                    style = MaterialTheme.typography.bodyMedium, color = p.textMuted, textAlign = TextAlign.Center,
                )
            }
            return@Column
        }
        LazyColumn(
            Modifier.weight(1f).fillMaxWidth(),
            state = list,
            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (messages.isEmpty()) {
                item {
                    Text(
                        if (pet.sleeping) "${pet.name} schläft gerade. Schreib leise … 💤" else "Schreib ${pet.name} etwas!",
                        style = MaterialTheme.typography.bodyMedium, color = p.textMuted, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
            items(messages) { m ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = if (m.fromPet) Arrangement.Start else Arrangement.End) {
                    val shape = if (m.fromPet) RoundedCornerShape(4.dp, 20.dp, 20.dp, 20.dp) else RoundedCornerShape(20.dp, 4.dp, 20.dp, 20.dp)
                    Column(
                        Modifier
                            .widthIn(max = 290.dp)
                            .clip(shape)
                            .background(if (m.fromPet) p.surface else p.pink)
                            .border(1.dp, if (m.fromPet) p.glassBorder else Color.Transparent, shape)
                            .padding(horizontal = 14.dp, vertical = 9.dp),
                    ) {
                        Text(m.text, style = MaterialTheme.typography.bodyLarge, color = if (m.fromPet) p.text else Color.White)
                        Text(
                            timeOf(m.at),
                            style = MaterialTheme.typography.labelSmall,
                            color = if (m.fromPet) p.textMuted else Color.White.copy(alpha = 0.8f),
                            modifier = Modifier.align(Alignment.End),
                        )
                    }
                }
            }
        }
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            for (q in Chat.quickReplies) {
                Text(
                    q,
                    style = MaterialTheme.typography.labelLarge,
                    color = p.text,
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(p.violet.copy(alpha = 0.16f))
                        .clickable { send(q) }
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                )
            }
        }
        Row(
            Modifier.fillMaxWidth().navigationBarsPadding().padding(start = 12.dp, end = 12.dp, bottom = 10.dp, top = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it.take(200) },
                placeholder = { Text("Schreib etwas …") },
                singleLine = true,
                shape = RoundedCornerShape(24.dp),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = { send(text) }),
                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = p.pink, cursorColor = p.pink, focusedContainerColor = p.surface, unfocusedContainerColor = p.surface),
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(8.dp))
            ClayTextButton("", { send(text) }, emoji = "📨", enabled = text.isNotBlank(), small = true)
        }
    }
}

// ------------------------------------------------------------------ job & talents

@Composable
fun JobScreen(state: GameState, onBack: () -> Unit) {
    val p = LocalPalette.current
    val pet = state.pet ?: return
    val now = rememberNow(30_000L)
    val cat = pet.isCat
    var confirm by remember { mutableStateOf<Job?>(null) }
    Column(Modifier.fillMaxSize()) {
        PageHeader("Beruf & Talente", onBack)
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).navigationBarsPadding().padding(horizontal = 16.dp),
        ) {
            val job = pet.job
            SectionHeader("Beruf", "💼")
            when {
                pet.level < Jobs.UNLOCK_LEVEL -> SurfaceCard(Modifier.fillMaxWidth()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        EmojiTile("🔒", size = 48.dp, color = p.violet)
                        Spacer(Modifier.width(12.dp))
                        Text(
                            "Ab Level ${Jobs.UNLOCK_LEVEL} kann ${pet.name} einen Beruf lernen, arbeiten gehen und Münzen verdienen. Talente sammelt es schon jetzt!",
                            style = MaterialTheme.typography.bodyMedium, color = p.textMuted,
                        )
                    }
                }
                job == null -> SurfaceCard(Modifier.fillMaxWidth()) {
                    Text("Such ${pet.name} einen Beruf aus! Der erste ist kostenlos.", style = MaterialTheme.typography.bodyMedium, color = p.textMuted)
                }
                else -> SurfaceCard(Modifier.fillMaxWidth()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        EmojiTile(job.emoji, size = 56.dp, color = p.gold)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(job.title(cat), style = MaterialTheme.typography.titleLarge, color = p.text)
                            Text("${Jobs.rank(pet.jobLevel)} · Stufe ${pet.jobLevel}", style = MaterialTheme.typography.labelLarge, color = p.goldDeep)
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    val progress = if (pet.jobLevel >= Jobs.MAX_LEVEL) 1f else (pet.jobXp % Jobs.SHIFTS_PER_LEVEL) / Jobs.SHIFTS_PER_LEVEL.toFloat()
                    GlossyBar(progress, p.gold, p.goldDeep)
                    Text(
                        if (pet.jobLevel >= Jobs.MAX_LEVEL) "Höchste Stufe erreicht!" else "Noch ${Jobs.SHIFTS_PER_LEVEL - pet.jobXp % Jobs.SHIFTS_PER_LEVEL} Schichten bis zur Beförderung",
                        style = MaterialTheme.typography.labelSmall, color = p.textMuted,
                    )
                    Spacer(Modifier.height(10.dp))
                    val wage = Jobs.wage(pet.jobLevel, pet.talentLevel(job.talent))
                    Text("Lohn pro Schicht: etwa $wage 🪙 · kostet Energie und macht hungrig", style = MaterialTheme.typography.bodySmall, color = p.textMuted)
                    Spacer(Modifier.height(10.dp))
                    val blocker = Engine.workBlocker(state, now)
                    ClayTextButton(
                        if (blocker == null) "Arbeiten gehen" else blocker,
                        { GameRepository.perform(Action.Work) },
                        emoji = job.tool,
                        enabled = blocker == null,
                        modifier = Modifier.fillMaxWidth(),
                        color = p.gold,
                        deep = p.goldDeep,
                    )
                }
            }
            if (pet.level >= Jobs.UNLOCK_LEVEL) {
                SectionHeader(if (job == null) "Berufe" else "Beruf wechseln (${Jobs.CHANGE_PRICE} 🪙)", "🧑‍🏫")
                for ((i, j) in Job.entries.withIndex()) {
                    val natural = Talents.natural(pet) == j.talent
                    SurfaceCard(
                        Modifier.fillMaxWidth().staggered(i).alpha(if (j == job) 0.55f else 1f),
                        onClick = if (j == job) null else ({ if (job == null) GameRepository.perform(Action.ChooseJob(j)) else confirm = j }),
                        contentPadding = PaddingValues(12.dp),
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            EmojiTile(j.emoji, size = 44.dp, color = if (natural) p.mint else p.violet)
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(j.title(cat), style = MaterialTheme.typography.titleSmall, color = p.text)
                                Text("${j.description} Trainiert ${j.talent.title} ${j.talent.emoji}.", style = MaterialTheme.typography.bodySmall, color = p.textMuted)
                                if (natural) Text("✨ Passt zum Naturtalent: +10 % Lohn", style = MaterialTheme.typography.labelSmall, color = p.mintDeep)
                            }
                            if (j == job) Text("✓", style = MaterialTheme.typography.titleLarge, color = p.pink)
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                }
            }

            SectionHeader("Talente", "🌟")
            val nat = Talents.natural(pet)
            Text(
                "Talente wachsen von allein, wenn ihr Dinge zusammen macht. Naturtalent von ${pet.name}: ${nat.title} ${nat.emoji} (wächst 50 % schneller).",
                style = MaterialTheme.typography.bodySmall, color = p.textMuted,
            )
            Spacer(Modifier.height(8.dp))
            for (t in Talent.entries) {
                val xp = pet.talents[t] ?: 0
                val level = Talents.level(xp)
                SurfaceCard(Modifier.fillMaxWidth(), contentPadding = PaddingValues(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        EmojiTile(t.emoji, size = 40.dp, color = if (t == nat) p.mint else p.sky)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(t.title, style = MaterialTheme.typography.titleSmall, color = p.text, modifier = Modifier.weight(1f))
                                Text("Stufe $level / ${Talents.MAX_LEVEL}", style = MaterialTheme.typography.labelMedium, color = p.textMuted)
                            }
                            Spacer(Modifier.height(4.dp))
                            GlossyBar(Talents.progress(xp), if (t == nat) p.mint else p.sky, if (t == nat) p.mintDeep else p.skyDeep, height = 9.dp)
                            Text(talentSource(t) + " · " + t.bonus, style = MaterialTheme.typography.labelSmall, color = p.textMuted)
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
            }
            Spacer(Modifier.height(24.dp))
        }
    }
    confirm?.let { j ->
        ConfirmDialog(
            title = "Beruf wechseln?",
            text = "${pet.name} wird ${j.title(cat)}. Das kostet ${Jobs.CHANGE_PRICE} Münzen, und die Beförderungen fangen von vorne an. Talente bleiben erhalten.",
            confirm = "Wechseln",
            onConfirm = {
                GameRepository.perform(Action.ChooseJob(j))
                confirm = null
            },
            onDismiss = { confirm = null },
        )
    }
}

private fun talentSource(t: Talent): String = when (t) {
    Talent.KOCHEN -> "Wächst beim Füttern"
    Talent.GAERTNERN -> "Wächst im Garten"
    Talent.BASTELN -> "Wächst bei Memory, Papierfliegern und Farbklecksen"
    Talent.ENTDECKEN -> "Wächst bei Ausflügen und Spaziergängen"
    Talent.MUSIK -> "Wächst bei Melodie und Blubberblasen"
    Talent.SPORT -> "Wächst beim Spielen, Klettern und Laufen"
}

// ------------------------------------------------------------------ wardrobe

@Composable
fun WardrobeScreen(state: GameState, onBack: () -> Unit) {
    val p = LocalPalette.current
    val pet = state.pet ?: return
    val look = remember(state.pet, state.equipped) { PetLook.of(state) }
    Column(Modifier.fillMaxSize()) {
        PageHeader("Garderobe", onBack)
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).navigationBarsPadding().padding(horizontal = 16.dp)) {
            Box(Modifier.fillMaxWidth().height(200.dp), contentAlignment = Alignment.Center) {
                Box(Modifier.size(190.dp).clip(CircleShape).background(Brush.radialGradient(listOf(p.pink.copy(alpha = 0.25f), Color.Transparent))))
                PetPortrait(look, Modifier.size(200.dp), sizeFactor = 0.62f)
            }
            if (pet.isCat) {
                val fur = Skins[pet.fur]
                Text(
                    "Fell: ${fur?.emoji ?: ""} ${fur?.name ?: ""}. ${fur?.description ?: ""}",
                    style = MaterialTheme.typography.bodySmall, color = p.textMuted, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth(),
                )
            }

            SectionHeader("Fest-Skins", "🎭")
            Text("Skins gewinnst du bei den Festen des Jahres: mit Fest-Punkten oder am Glücksrad.", style = MaterialTheme.typography.bodySmall, color = p.textMuted)
            Spacer(Modifier.height(8.dp))
            val skins = listOf<String?>(null) + Skins.costumes.map { it.id }
            for (row in skins.chunked(3)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    for (id in row) {
                        val skin = Skins[id]
                        val owned = id == null || id in state.skins
                        val active = pet.skin == id
                        val shape = RoundedCornerShape(20.dp)
                        Column(
                            Modifier
                                .weight(1f)
                                .clip(shape)
                                .background(if (active) p.pink.copy(alpha = 0.2f) else p.surface)
                                .border(if (active) 2.5.dp else 1.dp, if (active) p.pink else p.glassBorder, shape)
                                .clickable(enabled = owned) { GameRepository.perform(Action.ApplySkin(id)) }
                                .padding(8.dp)
                                .alpha(if (owned) 1f else 0.45f),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            val preview = remember(look, id) { look?.copy(skin = id) }
                            Box(Modifier.size(72.dp), contentAlignment = Alignment.Center) {
                                PetPortrait(preview, Modifier.fillMaxSize(), animated = active, sizeFactor = 0.8f, shadow = false, silhouette = if (owned) null else 0xFF8A80A8.toInt())
                                if (!owned) Text("🔒", fontSize = 22.sp)
                            }
                            Text(skin?.name ?: "Normal", style = MaterialTheme.typography.labelMedium, color = p.text, maxLines = 1)
                            Text(
                                if (owned) (skin?.emoji ?: "✨") else "${skin?.event?.emoji ?: ""} ${skin?.event?.title ?: ""}",
                                style = MaterialTheme.typography.labelSmall, color = p.textMuted, maxLines = 1,
                            )
                        }
                    }
                    repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
                }
                Spacer(Modifier.height(10.dp))
            }

            SectionHeader("Besondere Ausrüstung", "🛡️")
            Text("Nur bei Festen zu gewinnen. Jedes Teil hat einen Bonus, solange es getragen wird.", style = MaterialTheme.typography.bodySmall, color = p.textMuted)
            Spacer(Modifier.height(8.dp))
            val gear = Catalog.items.filter { it.perk != null }
            for (item in gear) {
                GearRow(state, item)
                Spacer(Modifier.height(8.dp))
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun GearRow(state: GameState, item: Item) {
    val p = LocalPalette.current
    val owned = item.id in state.owned
    val slot = item.kind.slot ?: return
    val worn = state.equipped[slot] == item.id
    val perk = item.perk ?: return
    val event = SeasonEvent.entries.firstOrNull { item.id in EventPrizes.gear(it) }
    SurfaceCard(Modifier.fillMaxWidth().alpha(if (owned) 1f else 0.6f), contentPadding = PaddingValues(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            EmojiTile(if (owned) item.emoji else "🔒", size = 46.dp, color = if (worn) p.mint else p.gold)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(item.name, style = MaterialTheme.typography.titleSmall, color = p.text)
                Text("${perk.emoji} ${perk.title}: ${perk.description}", style = MaterialTheme.typography.bodySmall, color = p.mintDeep)
                if (!owned && event != null) Text("Zu gewinnen: ${event.emoji} ${event.title}", style = MaterialTheme.typography.labelSmall, color = p.textMuted)
            }
            if (owned) {
                Spacer(Modifier.width(8.dp))
                ClayTextButton(
                    if (worn) "Ablegen" else "Anziehen",
                    { GameRepository.perform(if (worn) Action.Unequip(slot) else Action.Equip(item.id)) },
                    small = true,
                    color = if (worn) p.violet else p.mint,
                    deep = if (worn) p.violetDeep else p.mintDeep,
                )
            }
        }
    }
}

// ------------------------------------------------------------------ mood

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MoodSheet(state: GameState, onDismiss: () -> Unit) {
    val p = LocalPalette.current
    val pet = state.pet ?: return
    val now = System.currentTimeMillis()
    val e = pet.emotion(now)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = if (p.dark) p.surface else p.bgTop,
        shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp),
    ) {
        Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 18.dp).navigationBarsPadding().padding(bottom = 18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(e.emoji, fontSize = 48.sp)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text("${pet.name} ist ${e.title.lowercase()}", style = MaterialTheme.typography.titleLarge, color = p.text)
                    Text(e.description, style = MaterialTheme.typography.bodyMedium, color = p.textMuted)
                }
            }
            Spacer(Modifier.height(10.dp))
            Pill(Emotions.hint(e), color = (if (e.positive) p.mint else p.orange).copy(alpha = 0.25f))
            if (pet.feeling != null && now < pet.feelingUntil && !Emotions.urgent(Emotions.base(pet, now))) {
                Spacer(Modifier.height(6.dp))
                Text("Hält noch ${TimeUtil.formatDuration(pet.feelingUntil - now)} an.", style = MaterialTheme.typography.labelSmall, color = p.textMuted)
            }
            val w = de.knuffi.core.Weather.current(state, now, GameRepository.zone)
            SectionHeader("Wetter", w.kind.emoji)
            Text("${w.kind.title}, ${w.tempC} °C. ${de.knuffi.core.Weather.hint(w)}", style = MaterialTheme.typography.bodyMedium, color = p.text)
            SectionHeader("Tagesablauf", "🕰️")
            val hour = TimeUtil.hour(now, GameRepository.zone)
            for (r in de.knuffi.core.Routine.entries) {
                val done = r in state.daily.routine
                Row(Modifier.padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(if (done) "✅" else if (r.now(hour)) "⏳" else "⬜", fontSize = 18.sp)
                    Spacer(Modifier.width(8.dp))
                    Text("${r.emoji} ${r.title}", style = MaterialTheme.typography.titleSmall, color = p.text, modifier = Modifier.width(140.dp))
                    Text(r.description, style = MaterialTheme.typography.bodySmall, color = p.textMuted)
                }
            }
            Text(
                if (state.daily.routineClaimed) "Heute alles geschafft! 🎉" else "Alles geschafft = +${de.knuffi.core.Routines.BONUS_COINS} 🪙 und ${de.knuffi.core.Routines.BONUS_XP} XP",
                style = MaterialTheme.typography.labelLarge, color = p.goldDeep, modifier = Modifier.padding(top = 4.dp),
            )
            SectionHeader("Stimmungs-Tagebuch", "📔")
            val log = state.moodLog.takeLast(8).reversed()
            if (log.isEmpty()) Text("Noch keine Einträge.", style = MaterialTheme.typography.bodySmall, color = p.textMuted)
            for (m in log) {
                Row(Modifier.padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(timeOf(m.at), style = MaterialTheme.typography.labelMedium, color = p.textMuted, modifier = Modifier.width(52.dp))
                    Text("${m.emotion.emoji} ${m.emotion.title}", style = MaterialTheme.typography.bodyMedium, color = p.text)
                }
            }
            SectionHeader("Alle Stimmungen", "🎭")
            Text(
                Emotion.entries.filter { it != Emotion.SCHLAEFRIG }.joinToString("  ") { "${it.emoji} ${it.title}" },
                style = MaterialTheme.typography.bodySmall, color = p.textMuted,
            )
        }
    }
}

// ------------------------------------------------------------------ festival prizes & wheel

@Composable
fun EventPrizeSection(state: GameState, event: SeasonEvent) {
    val p = LocalPalette.current
    val today = TimeUtil.epochDay(System.currentTimeMillis(), GameRepository.zone)
    SectionHeader("Zu gewinnen", "🏆")
    Text(
        "Mit jedem ${event.tokenEmoji}, das du verdienst (Ausgeben zählt nicht dagegen), kommst du den Preisen näher. Verdient: ${state.event.earned}",
        style = MaterialTheme.typography.bodySmall, color = p.textMuted,
    )
    Spacer(Modifier.height(8.dp))
    for (prize in EventPrizes.of(event)) {
        val owned = EventPrizes.owned(state, prize)
        SurfaceCard(Modifier.fillMaxWidth(), contentPadding = PaddingValues(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                EmojiTile(prize.emoji, size = 46.dp, color = if (owned) p.mint else p.gold)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text("${prize.name} · ${if (prize.skin) "Skin" else "Ausrüstung"}", style = MaterialTheme.typography.titleSmall, color = p.text)
                    val perk = Catalog[prize.id]?.perk
                    if (perk != null) Text("${perk.emoji} ${perk.description}", style = MaterialTheme.typography.labelSmall, color = p.mintDeep)
                    Spacer(Modifier.height(4.dp))
                    GlossyBar((state.event.earned / prize.tokens.toFloat()).coerceIn(0f, 1f), p.gold, p.goldDeep, height = 9.dp)
                }
                Spacer(Modifier.width(10.dp))
                Text(if (owned) "✅" else "${minOf(state.event.earned, prize.tokens)}/${prize.tokens}", style = MaterialTheme.typography.labelLarge, color = p.text)
            }
        }
        Spacer(Modifier.height(8.dp))
    }
    LuckyWheel(state, event, EventPrizes.canSpin(state, today))
}

@Composable
private fun LuckyWheel(state: GameState, event: SeasonEvent, canSpin: Boolean) {
    val p = LocalPalette.current
    val rotation = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    var result by remember { mutableStateOf<SpinResult?>(null) }
    var spinning by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        GameRepository.events.collect { e -> if (e is GameEvent.Spun) result = e.result }
    }
    SectionHeader("Glücksrad", "🎡")
    SurfaceCard(Modifier.fillMaxWidth()) {
        Box(Modifier.fillMaxWidth().height(220.dp), contentAlignment = Alignment.Center) {
            val colors = listOf(p.pink, p.gold, p.sky, p.mint, p.violet, p.orange, p.pink.lighter(0.3f), p.gold.lighter(0.3f))
            Canvas(Modifier.size(200.dp).rotate(rotation.value)) {
                val n = colors.size
                for (i in 0 until n) drawArc(colors[i], i * 360f / n, 360f / n, true)
                drawCircle(Color.White, radius = size.minDimension * 0.12f)
                drawCircle(Color.White.copy(alpha = 0.6f), style = Stroke(6.dp.toPx()))
            }
            val labels = listOf("🎁", event.tokenEmoji, "🪙", "🎴", event.tokenEmoji, "🪙", "🎁", event.tokenEmoji)
            Box(Modifier.size(200.dp).rotate(rotation.value)) {
                for ((i, l) in labels.withIndex()) {
                    val a = Math.toRadians((i + 0.5) * 360.0 / labels.size)
                    Text(
                        l, fontSize = 22.sp,
                        modifier = Modifier.align(Alignment.Center).padding(0.dp)
                            .then(Modifier.offsetPolar((Math.cos(a) * 66).toFloat(), (Math.sin(a) * 66).toFloat())),
                    )
                }
            }
            Canvas(Modifier.size(220.dp)) {
                // pointer on top
                val cx = size.width / 2f
                val path = androidx.compose.ui.graphics.Path().apply {
                    moveTo(cx - 14.dp.toPx(), 0f)
                    lineTo(cx + 14.dp.toPx(), 0f)
                    lineTo(cx, 26.dp.toPx())
                    close()
                }
                drawPath(path, p.red)
            }
        }
        result?.takeIf { !spinning }?.let {
            Text("${it.emoji} ${it.label}", style = MaterialTheme.typography.titleMedium, color = p.text, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(6.dp))
        }
        ClayTextButton(
            if (canSpin) "Drehen!" else "Morgen wieder drehen",
            {
                if (spinning) return@ClayTextButton
                spinning = true
                result = null
                scope.launch {
                    GameRepository.perform(Action.SpinWheel)
                    rotation.animateTo(rotation.value + 360f * 4 + (0..359).random(), tween(2600, easing = FastOutSlowInEasing))
                    spinning = false
                }
            },
            emoji = "🎡",
            enabled = canSpin && !spinning,
            modifier = Modifier.fillMaxWidth(),
            color = p.gold,
            deep = p.goldDeep,
        )
        Text(
            "Einmal am Tag. Mit etwas Glück gibt es einen Hauptgewinn: einen Skin oder besondere Ausrüstung, die du noch nicht hast!",
            style = MaterialTheme.typography.labelSmall, color = p.textMuted, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
        )
    }
    Spacer(Modifier.height(10.dp))
}

private fun Modifier.offsetPolar(x: Float, y: Float): Modifier = offset(x.dp, y.dp)
