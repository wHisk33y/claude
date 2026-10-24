package de.knuffi.app.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.keyframes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.knuffi.app.data.GameRepository
import de.knuffi.app.notify.LearnNotifier
import de.knuffi.app.render.EyeShape
import de.knuffi.app.render.MouthShape
import de.knuffi.app.render.PetLook
import de.knuffi.app.ui.components.ClayButton
import de.knuffi.app.ui.components.ClayTextButton
import de.knuffi.app.ui.components.EmojiTile
import de.knuffi.app.ui.components.GlassIconButton
import de.knuffi.app.ui.components.GlossyBar
import de.knuffi.app.ui.components.PageHeader
import de.knuffi.app.ui.components.Pill
import de.knuffi.app.ui.components.SectionHeader
import de.knuffi.app.ui.components.SurfaceCard
import de.knuffi.app.ui.components.staggered
import de.knuffi.app.ui.theme.LocalPalette
import de.knuffi.core.Action
import de.knuffi.core.Catalog
import de.knuffi.core.GameState
import de.knuffi.core.Learn
import de.knuffi.core.LearnPrize
import de.knuffi.core.LearnTask
import de.knuffi.core.Settings
import de.knuffi.core.Skins
import de.knuffi.core.Subject
import de.knuffi.core.TimeUtil
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt
import kotlin.math.sin

private val cheers = listOf(
    "Super gemacht! 🎉", "Richtig! Du bist so schlau! 🧠", "Juhu, stimmt genau! ⭐", "Wow, perfekt! 💪",
    "Klasse! Weiter so! 🌟", "Bingo! 🎯", "Ich wusste, dass du das kannst! 💖",
)
private val oops = listOf(
    "Hmm, fast! Schau mal auf den Tipp. 💡", "Nicht schlimm, versuch's nochmal! 🙂", "Knapp daneben! Du schaffst das! 💪",
    "Fehler sind zum Lernen da! 📚",
)
private val idle = listOf("Lass uns zusammen lernen! 📚", "Ich hab meine Brille schon auf! 🤓", "Wir zwei sind ein super Team! 💫")

/** Name and emoji of a Lernzeit prize. */
internal fun prizeLabel(prize: LearnPrize): Pair<String, String> =
    if (prize.skin) (Skins[prize.id]?.name ?: prize.id) to (Skins[prize.id]?.emoji ?: "🎁")
    else (Catalog[prize.id]?.name ?: prize.id) to (Catalog[prize.id]?.emoji ?: "🎁")

/** The pet with glasses (and a book) for the Lernzeit. */
internal fun learnLook(state: GameState): PetLook? {
    val look = PetLook.of(state) ?: return null
    if (look.isEgg) return look
    return if (look.face == "face_labgoggles" || look.face == "face_round") look else look.copy(face = "face_round")
}

@Composable
fun LearnScreen(state: GameState, onBack: () -> Unit, onStats: () -> Unit, onSettings: () -> Unit) {
    val p = LocalPalette.current
    val context = LocalContext.current
    val now = rememberNow(30_000L)
    val today = TimeUtil.epochDay(now, GameRepository.zone)
    val settings = state.settings
    val learn = state.learn
    LaunchedEffect(Unit) { LearnNotifier.cancel(context) }

    Column(Modifier.fillMaxSize()) {
        PageHeader("Lernzeit", onBack) {
            GlassIconButton("📊", onStats, size = 42.dp)
        }
        if (!settings.learnEnabled) {
            Column(Modifier.padding(16.dp)) {
                SurfaceCard(Modifier.fillMaxWidth()) {
                    Text("Die Lernzeit ist ausgeschaltet.", style = MaterialTheme.typography.titleMedium, color = p.text)
                    Spacer(Modifier.height(6.dp))
                    Text("In den Einstellungen kannst du sie jederzeit wieder einschalten.", style = MaterialTheme.typography.bodyMedium, color = p.textMuted)
                    Spacer(Modifier.height(12.dp))
                    ClayTextButton("Einschalten", { GameRepository.perform(Action.UpdateSettings(settings.copy(learnEnabled = true))) }, emoji = "📚")
                }
            }
            return@Column
        }

        val scope = rememberCoroutineScope()
        var subject by remember { mutableStateOf(Subject.entries.firstOrNull { !Learn.subjectDone(settings, learn, it, today) } ?: Subject.MATHE) }
        val per = settings.learnPerSubject
        val solved = Learn.solved(learn, subject, today)
        val grade = Learn.grade(settings, learn, subject)
        val task = remember(subject, grade, today, solved, per) { if (solved >= per) null else Learn.task(subject, grade, today, solved) }
        var celebrating by remember { mutableStateOf<LearnTask?>(null) }
        var reaction by remember { mutableIntStateOf(0) }
        var reactionAt by remember { mutableLongStateOf(0L) }
        var bubble by remember { mutableStateOf(idle.random()) }
        var input by remember(subject, solved) { mutableStateOf("") }
        var wrong by remember(subject, solved) { mutableStateOf(setOf<String>()) }
        val shake = remember { Animatable(0f) }
        val tries = if (learn.day == today) learn.tries[subject.name] ?: 0 else 0
        val shown = celebrating ?: task
        val allDone = Learn.allDone(settings, learn, today) && celebrating == null

        LaunchedEffect(celebrating) {
            if (celebrating == null) return@LaunchedEffect
            delay(1300)
            celebrating = null
            val s = GameRepository.current
            if (Learn.subjectDone(s.settings, s.learn, subject, today)) {
                Subject.entries.firstOrNull { !Learn.subjectDone(s.settings, s.learn, it, today) }?.let { subject = it }
            }
        }

        fun answer(given: String) {
            val t = task ?: return
            if (celebrating != null || given.isBlank()) return
            val ok = t.check(given)
            GameRepository.perform(Action.LearnAnswer(subject, ok, t.topic))
            reaction = if (ok) 1 else -1
            reactionAt = System.currentTimeMillis()
            if (ok) {
                celebrating = t
                bubble = cheers.random()
            } else {
                if (!t.typed) wrong = wrong + given
                bubble = oops.random()
                scope.launch {
                    shake.snapTo(0f)
                    shake.animateTo(0f, keyframes {
                        durationMillis = 420
                        14f at 60
                        -12f at 130
                        9f at 200
                        -6f at 270
                        3f at 340
                    })
                }
            }
        }

        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(horizontal = 16.dp),
        ) {
            // subjects
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for (sub in Subject.entries) {
                    SubjectChip(sub, Learn.solved(learn, sub, today), per, sub == subject, Modifier.weight(1f)) {
                        if (celebrating == null && sub != subject) {
                            subject = sub
                            reaction = 0
                            bubble = idle.random()
                        }
                    }
                }
            }
            Spacer(Modifier.height(10.dp))

            // the pet learns along
            val look = remember(state.pet, state.equipped) { learnLook(state) }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(112.dp), contentAlignment = Alignment.Center) {
                    if (look == null || look.isEgg) {
                        Text("🥚", fontSize = 56.sp)
                    } else {
                        PetPortrait(look, Modifier.fillMaxSize(), sizeFactor = 0.8f) { pose, t ->
                            val since = (System.currentTimeMillis() - reactionAt) / 1000f
                            when {
                                allDone || (reaction == 1 && since < 1.6f) -> {
                                    pose.eyes = EyeShape.HAPPY
                                    pose.mouth = MouthShape.GRIN
                                    pose.mouthOpen = 0.6f
                                    pose.armWaveL = 1f
                                    pose.armWaveR = 1f
                                    pose.lift = 0.06f * kotlin.math.abs(sin(t * 9f))
                                }
                                reaction == -1 && since < 1.6f -> {
                                    pose.mouth = MouthShape.WAVY
                                    pose.brows = 0.6f
                                    pose.lookX = 0.4f * sin(t * 5f)
                                    pose.holding = "📖"
                                    pose.hold = 1f
                                }
                                else -> {
                                    pose.holding = if (subject == Subject.MATHE) "✏️" else "📖"
                                    pose.hold = 1f
                                    pose.lookY = 0.6f
                                    pose.tilt = 0.04f * sin(t * 1.3f)
                                }
                            }
                        }
                    }
                }
                Spacer(Modifier.width(8.dp))
                SpeechCard(if (allDone) "Wir haben alles geschafft! Ich bin so stolz auf dich! 🥳" else bubble, Modifier.weight(1f))
            }
            Spacer(Modifier.height(10.dp))

            when {
                allDone -> DoneCard(state, today, onStats)
                shown == null -> SubjectDoneCard(subject) {
                    Subject.entries.firstOrNull { !Learn.subjectDone(settings, learn, it, today) }?.let { subject = it }
                }
                else -> {
                    Column(Modifier.graphicsLayer { translationX = shake.value.dp.toPx() }) {
                        QuestionCard(shown, subject, grade, solved + if (celebrating != null) 0 else 1, per, solvedNow = celebrating != null)
                        Spacer(Modifier.height(10.dp))
                        if (shown.typed) {
                            AnswerField(if (celebrating != null) shown.answer else input, shown.unit, wrongNow = reaction == -1 && celebrating == null && tries > 0, solvedNow = celebrating != null)
                            Spacer(Modifier.height(10.dp))
                            Keypad(
                                enabled = celebrating == null,
                                onKey = { k ->
                                    reaction = 0
                                    if (input.length < 12) input += k
                                },
                                onDelete = {
                                    reaction = 0
                                    input = input.dropLast(1)
                                },
                                onCheck = { answer(input) },
                            )
                        } else {
                            for (c in shown.choices) {
                                ChoiceButton(
                                    c,
                                    state = when {
                                        celebrating != null && c == shown.answer -> 1
                                        c in wrong -> -1
                                        else -> 0
                                    },
                                    enabled = celebrating == null && c !in wrong,
                                ) { answer(c) }
                                Spacer(Modifier.height(8.dp))
                            }
                        }
                    }
                    if (tries > 0 && celebrating == null) {
                        Spacer(Modifier.height(10.dp))
                        SurfaceCard(Modifier.fillMaxWidth(), color = if (p.dark) p.surfaceAlt else Color(0xFFFFF6DC), contentPadding = PaddingValues(14.dp)) {
                            Text("💡 So geht's", style = MaterialTheme.typography.titleSmall, color = p.text)
                            Spacer(Modifier.height(4.dp))
                            Text(shown.hint, style = MaterialTheme.typography.bodyMedium, color = p.text)
                        }
                    }
                }
            }

            Spacer(Modifier.height(14.dp))
            ProgressCard(state, today)
            Spacer(Modifier.height(10.dp))
            Text(
                "Schwierigkeit und Anzahl der Aufgaben stellst du in den Einstellungen ein.",
                style = MaterialTheme.typography.bodySmall,
                color = p.textMuted,
                modifier = Modifier.clickable { onSettings() }.padding(vertical = 4.dp),
            )
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun SubjectChip(subject: Subject, solved: Int, per: Int, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val p = LocalPalette.current
    val done = solved >= per
    val shape = RoundedCornerShape(18.dp)
    Column(
        modifier
            .clip(shape)
            .background(if (selected) Brush.linearGradient(listOf(p.sky, p.violet)) else Brush.linearGradient(listOf(p.surface, p.surface)))
            .border(if (selected) 0.dp else 1.dp, if (done) p.mint else p.glassBorder, shape)
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(subject.emoji, fontSize = 22.sp)
        Text(subject.title, style = MaterialTheme.typography.labelLarge, color = if (selected) Color.White else p.text)
        Text(
            if (done) "✓ fertig" else "$solved / $per",
            style = MaterialTheme.typography.labelSmall,
            color = if (selected) Color.White.copy(alpha = 0.9f) else if (done) p.mintDeep else p.textMuted,
        )
    }
}

@Composable
private fun SpeechCard(text: String, modifier: Modifier = Modifier) {
    val p = LocalPalette.current
    Box(
        modifier
            .clip(RoundedCornerShape(topStart = 6.dp, topEnd = 20.dp, bottomEnd = 20.dp, bottomStart = 20.dp))
            .background(if (p.dark) p.surfaceAlt else Color.White)
            .border(1.dp, p.glassBorder, RoundedCornerShape(topStart = 6.dp, topEnd = 20.dp, bottomEnd = 20.dp, bottomStart = 20.dp))
            .padding(horizontal = 14.dp, vertical = 10.dp),
    ) {
        Text(text, style = MaterialTheme.typography.bodyMedium, color = p.text)
    }
}

@Composable
private fun QuestionCard(task: LearnTask, subject: Subject, grade: Int, number: Int, per: Int, solvedNow: Boolean) {
    val p = LocalPalette.current
    SurfaceCard(
        Modifier.fillMaxWidth().then(if (solvedNow) Modifier.border(2.5.dp, p.mint, RoundedCornerShape(24.dp)) else Modifier),
        contentPadding = PaddingValues(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Pill("${subject.emoji} ${task.topic}", color = p.sky.copy(alpha = 0.18f))
            Spacer(Modifier.weight(1f))
            Text("Aufgabe ${number.coerceAtMost(per)} von $per · Klasse $grade", style = MaterialTheme.typography.labelSmall, color = p.textMuted)
        }
        Spacer(Modifier.height(12.dp))
        Text(task.question, style = MaterialTheme.typography.titleLarge, color = p.text)
        if (solvedNow) {
            Spacer(Modifier.height(8.dp))
            Text("✅ Richtig!", style = MaterialTheme.typography.titleMedium, color = p.mintDeep)
        }
    }
}

@Composable
private fun AnswerField(text: String, unit: String, wrongNow: Boolean, solvedNow: Boolean) {
    val p = LocalPalette.current
    val shape = RoundedCornerShape(20.dp)
    val border = when {
        solvedNow -> p.mint
        wrongNow -> p.red
        else -> p.sky
    }
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 60.dp)
            .clip(shape)
            .background(if (p.dark) p.surfaceAlt else Color.White)
            .border(2.dp, border, shape)
            .padding(horizontal = 18.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text.ifEmpty { "Deine Antwort" },
            fontSize = 26.sp,
            fontWeight = FontWeight.Bold,
            color = if (text.isEmpty()) p.textMuted.copy(alpha = 0.6f) else p.text,
            modifier = Modifier.weight(1f),
        )
        if (unit.isNotEmpty()) Text(unit, fontSize = 20.sp, color = p.textMuted)
    }
}

@Composable
private fun Keypad(enabled: Boolean, onKey: (String) -> Unit, onDelete: () -> Unit, onCheck: () -> Unit) {
    val p = LocalPalette.current
    val rows = listOf(
        listOf("7", "8", "9", "⌫"),
        listOf("4", "5", "6", "/"),
        listOf("1", "2", "3", "-"),
    )
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        for (row in rows) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for (k in row) {
                    val special = k == "⌫" || k == "/" || k == "-"
                    Key(
                        if (k == "-") "−" else k, Modifier.weight(1f), enabled,
                        color = if (special) p.violet.copy(alpha = 0.85f) else if (p.dark) p.surfaceAlt else Color.White,
                        deep = if (special) p.violetDeep else p.glassBorder,
                        textColor = if (special) Color.White else p.text,
                    ) { if (k == "⌫") onDelete() else onKey(k) }
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Key(",", Modifier.weight(1f), enabled, color = p.violet.copy(alpha = 0.85f), deep = p.violetDeep, textColor = Color.White) { onKey(",") }
            Key("0", Modifier.weight(1f), enabled, color = if (p.dark) p.surfaceAlt else Color.White, deep = p.glassBorder, textColor = p.text) { onKey("0") }
            Key("Prüfen ✓", Modifier.weight(2.15f), enabled, color = p.mint, deep = p.mintDeep, textColor = Color.White, small = true) { onCheck() }
        }
    }
}

@Composable
private fun Key(label: String, modifier: Modifier, enabled: Boolean, color: Color, deep: Color, textColor: Color, small: Boolean = false, onClick: () -> Unit) {
    ClayButton(
        onClick,
        modifier.height(58.dp),
        color = color,
        deep = deep,
        shape = RoundedCornerShape(18.dp),
        enabled = enabled,
        depth = 4.dp,
        contentPadding = PaddingValues(4.dp),
    ) {
        Text(label, fontSize = if (small) 19.sp else 24.sp, fontWeight = FontWeight.Bold, color = textColor, textAlign = TextAlign.Center)
    }
}

@Composable
private fun ChoiceButton(text: String, state: Int, enabled: Boolean, onClick: () -> Unit) {
    val p = LocalPalette.current
    val color = when (state) {
        1 -> p.mint
        -1 -> p.red.copy(alpha = 0.55f)
        else -> if (p.dark) p.surfaceAlt else Color.White
    }
    val deep = when (state) {
        1 -> p.mintDeep
        -1 -> p.redDeep.copy(alpha = 0.6f)
        else -> p.glassBorder
    }
    ClayButton(
        onClick,
        Modifier.fillMaxWidth(),
        color = color,
        deep = deep,
        shape = RoundedCornerShape(20.dp),
        enabled = enabled || state != 0,
        depth = 4.dp,
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
    ) {
        Text(
            (if (state == 1) "✓ " else if (state == -1) "✗ " else "") + text,
            style = MaterialTheme.typography.titleMedium,
            color = if (state != 0) Color.White else p.text,
            textAlign = TextAlign.Center,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun SubjectDoneCard(subject: Subject, onNext: () -> Unit) {
    val p = LocalPalette.current
    SurfaceCard(Modifier.fillMaxWidth()) {
        Text("✓ ${subject.title} ist für heute geschafft!", style = MaterialTheme.typography.titleMedium, color = p.mintDeep)
        Spacer(Modifier.height(10.dp))
        ClayTextButton("Weiter mit dem nächsten Fach", onNext, emoji = "➡️", color = p.sky, deep = p.skyDeep)
    }
}

@Composable
private fun DoneCard(state: GameState, today: Long, onStats: () -> Unit) {
    val p = LocalPalette.current
    val learn = state.learn
    SurfaceCard(Modifier.fillMaxWidth(), color = if (p.dark) p.surfaceAlt else Color(0xFFEFFFF6)) {
        Text("🎉 Lernzeit für heute geschafft!", style = MaterialTheme.typography.titleLarge, color = p.text)
        Spacer(Modifier.height(6.dp))
        Text(
            "Du hast alle Aufgaben in Mathe, Deutsch und Physik gelöst. Dafür gab es +${Learn.COINS_DAY} 🪙 und ${Learn.XP_DAY} Erfahrung extra. Morgen warten neue Aufgaben!",
            style = MaterialTheme.typography.bodyMedium, color = p.textMuted,
        )
        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("🔥 ${learn.streak} ${if (learn.streak == 1) "Tag" else "Tage"} am Stück", style = MaterialTheme.typography.titleMedium, color = p.orangeDeep)
            Spacer(Modifier.weight(1f))
            ClayTextButton("Übersicht", onStats, emoji = "📊", small = true, color = p.sky, deep = p.skyDeep)
        }
    }
}

@Composable
private fun ProgressCard(state: GameState, today: Long) {
    val p = LocalPalette.current
    val learn = state.learn
    val total = Subject.entries.size * state.settings.learnPerSubject
    val done = total - Learn.left(state.settings, learn, today)
    SurfaceCard(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Heute: $done von $total Aufgaben", style = MaterialTheme.typography.titleSmall, color = p.text, modifier = Modifier.weight(1f))
            if (learn.streak > 0) Pill("🔥 ${learn.streak}", color = p.orange.copy(alpha = 0.2f))
        }
        Spacer(Modifier.height(8.dp))
        GlossyBar(done / total.coerceAtLeast(1).toFloat(), p.mint, p.mintDeep, Modifier.fillMaxWidth())
        val next = Learn.nextPrize(learn)
        Spacer(Modifier.height(12.dp))
        if (next != null) {
            val (name, emoji) = prizeLabel(next)
            val (have, need) = Learn.progress(learn, next)
            Row(verticalAlignment = Alignment.CenterVertically) {
                EmojiTile(emoji, size = 44.dp, color = p.gold)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text("Nächster Preis: $name", style = MaterialTheme.typography.titleSmall, color = p.text)
                    Text(
                        "${if (next.skin) "Exklusiver Skin" else "Ausrüstung mit Bonus"} für ${next.label} · noch ${need - have}",
                        style = MaterialTheme.typography.bodySmall, color = p.textMuted,
                    )
                    Spacer(Modifier.height(4.dp))
                    GlossyBar(have / need.toFloat(), p.gold, p.goldDeep, Modifier.fillMaxWidth(), height = 8.dp)
                }
            }
        } else {
            Text("🏆 Du hast alle Lernpreise gewonnen! Unglaublich!", style = MaterialTheme.typography.titleSmall, color = p.text)
        }
        Spacer(Modifier.height(8.dp))
        Text(
            "Pro Aufgabe gibt es Münzen (beim ersten Versuch doppelt), für jedes Fach +${Learn.COINS_SUBJECT} 🪙 und für den ganzen Tag +${Learn.COINS_DAY} 🪙.",
            style = MaterialTheme.typography.bodySmall, color = p.textMuted,
        )
    }
}

// ------------------------------------------------------------------ overview for parents

@Composable
fun LearnStatsScreen(state: GameState, onBack: () -> Unit) {
    val p = LocalPalette.current
    val learn = state.learn
    val settings = state.settings
    Column(Modifier.fillMaxSize()) {
        PageHeader("Lern-Übersicht", onBack)
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).navigationBarsPadding().padding(horizontal = 16.dp)) {
            Text("Hier sehen Eltern (und du), wie es beim Lernen läuft und welche Themen noch Übung brauchen.", style = MaterialTheme.typography.bodySmall, color = p.textMuted)
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatBox("📅", "${learn.doneDays}", "Lerntage", Modifier.weight(1f))
                StatBox("🔥", "${learn.streak}", "am Stück", Modifier.weight(1f))
                StatBox("🏅", "${learn.bestStreak}", "Rekord", Modifier.weight(1f))
                StatBox("✏️", "${learn.totalSolved}", "Aufgaben", Modifier.weight(1f))
            }
            for ((i, subject) in Subject.entries.withIndex()) {
                SectionHeader("${subject.title}", subject.emoji)
                val prefix = subject.name + ":"
                val topics = learn.stats.filterKeys { it.startsWith(prefix) }.map { it.key.removePrefix(prefix) to it.value }.sortedByDescending { it.second.tasks }
                val tasks = topics.sumOf { it.second.tasks }
                val first = topics.sumOf { it.second.firstTry }
                val base = settings.learnGrades[subject.name] ?: Learn.MIN_GRADE
                val adjust = learn.adjust[subject.name] ?: 0
                SurfaceCard(Modifier.fillMaxWidth().staggered(i)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Klasse ${Learn.grade(settings, learn, subject)}", style = MaterialTheme.typography.titleMedium, color = p.text)
                            Text(
                                when {
                                    adjust > 0 -> "Eingestellt: Klasse $base · läuft super, daher etwas schwerer"
                                    adjust < 0 -> "Eingestellt: Klasse $base · etwas leichter, bis es wieder besser klappt"
                                    else -> "Eingestellt: Klasse $base"
                                },
                                style = MaterialTheme.typography.bodySmall, color = p.textMuted,
                            )
                        }
                        if (tasks > 0) Pill("${(first * 100f / tasks).roundToInt()} % auf Anhieb", color = p.mint.copy(alpha = 0.2f))
                    }
                    if (topics.isEmpty()) {
                        Spacer(Modifier.height(8.dp))
                        Text("Noch keine Aufgaben gelöst.", style = MaterialTheme.typography.bodySmall, color = p.textMuted)
                    }
                    for ((topic, stat) in topics) {
                        Spacer(Modifier.height(10.dp))
                        val rate = if (stat.tasks > 0) stat.firstTry / stat.tasks.toFloat() else 0f
                        val weak = stat.tasks >= 3 && rate < 0.6f
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text((if (weak) "⚠️ " else "") + topic, style = MaterialTheme.typography.labelLarge, color = p.text, modifier = Modifier.weight(1f), maxLines = 1)
                            Text("${stat.tasks} ${if (stat.tasks == 1) "Aufgabe" else "Aufgaben"} · ${(rate * 100).roundToInt()} % auf Anhieb", style = MaterialTheme.typography.labelSmall, color = p.textMuted)
                        }
                        Spacer(Modifier.height(3.dp))
                        GlossyBar(rate, if (weak) p.orange else p.mint, if (weak) p.orangeDeep else p.mintDeep, Modifier.fillMaxWidth(), height = 7.dp)
                    }
                    if (topics.any { it.second.tasks >= 3 && it.second.firstTry / it.second.tasks.toFloat() < 0.6f }) {
                        Spacer(Modifier.height(8.dp))
                        Text("⚠️ = braucht noch Übung (weniger als 60 % beim ersten Versuch richtig)", style = MaterialTheme.typography.bodySmall, color = p.textMuted)
                    }
                }
            }
            SectionHeader("Lernpreise", "🏆")
            for (prize in Learn.prizes) {
                val (name, emoji) = prizeLabel(prize)
                val won = prize.id in learn.prizes
                val (have, need) = Learn.progress(learn, prize)
                SurfaceCard(Modifier.fillMaxWidth(), contentPadding = PaddingValues(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        EmojiTile(if (won) emoji else "🔒", size = 44.dp, color = if (won) p.mint else p.violet)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(name, style = MaterialTheme.typography.titleSmall, color = p.text)
                            Text(
                                "${if (prize.skin) "Skin" else "Ausrüstung"} · ${prize.label}" + if (won) " · gewonnen! ✅" else " · $have / $need",
                                style = MaterialTheme.typography.bodySmall, color = if (won) p.mintDeep else p.textMuted,
                            )
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun StatBox(emoji: String, value: String, label: String, modifier: Modifier) {
    val p = LocalPalette.current
    SurfaceCard(modifier, contentPadding = PaddingValues(vertical = 10.dp, horizontal = 4.dp)) {
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(emoji, fontSize = 20.sp)
            Text(value, style = MaterialTheme.typography.titleLarge, color = p.text)
            Text(label, style = MaterialTheme.typography.labelSmall, color = p.textMuted, maxLines = 1)
        }
    }
}

// ------------------------------------------------------------------ entry points and settings

/** The Lernzeit as a pill under the HUD (only while there is something left to do). */
@Composable
fun LearnPill(state: GameState, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val today = TimeUtil.epochDay(rememberNow(60_000L), GameRepository.zone)
    val s = state.settings
    if (!s.learnEnabled || Learn.allDone(s, state.learn, today)) return
    val total = Subject.entries.size * s.learnPerSubject
    val done = total - Learn.left(s, state.learn, today)
    val p = LocalPalette.current
    Row(
        modifier
            .clip(RoundedCornerShape(50))
            .background(Brush.horizontalGradient(listOf(p.sky.copy(alpha = 0.92f), p.mint.copy(alpha = 0.92f))))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(if (state.learn.missed) "🥺" else "📚", fontSize = 18.sp)
        Spacer(Modifier.width(6.dp))
        Text("Lernzeit $done/$total", style = MaterialTheme.typography.labelLarge, color = Color.White, maxLines = 1)
        if (state.learn.streak > 0) {
            Spacer(Modifier.width(6.dp))
            Text("🔥${state.learn.streak}", style = MaterialTheme.typography.labelMedium, color = Color.White)
        }
    }
}

/** Big card for the world hub. */
@Composable
fun LearnBanner(state: GameState, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val p = LocalPalette.current
    val s = state.settings
    if (!s.learnEnabled) return
    val today = TimeUtil.epochDay(rememberNow(60_000L), GameRepository.zone)
    val total = Subject.entries.size * s.learnPerSubject
    val done = total - Learn.left(s, state.learn, today)
    val all = done >= total
    SurfaceCard(modifier.fillMaxWidth(), onClick = onClick, color = if (p.dark) p.surfaceAlt else Color(0xFFEAF6FF)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            EmojiTile(if (all) "🎓" else "📚", size = 50.dp, color = if (all) p.mint else p.sky)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("Lernzeit", style = MaterialTheme.typography.titleMedium, color = p.text)
                Text(
                    if (all) "Heute geschafft! 🔥 ${state.learn.streak} ${if (state.learn.streak == 1) "Tag" else "Tage"} am Stück"
                    else Subject.entries.joinToString(" · ") { "${it.emoji} ${Learn.solved(state.learn, it, today)}/${s.learnPerSubject}" },
                    style = MaterialTheme.typography.bodySmall, color = p.textMuted, maxLines = 1,
                )
                Spacer(Modifier.height(6.dp))
                GlossyBar(done / total.coerceAtLeast(1).toFloat(), p.sky, p.skyDeep, Modifier.fillMaxWidth(), height = 8.dp)
            }
        }
    }
}

/** The Lernzeit part of the settings. */
@Composable
fun LearnSettings(s: Settings, update: (Settings) -> Unit, onStats: () -> Unit) {
    val p = LocalPalette.current
    SwitchRow(
        "Lernzeit",
        "Jeden Tag Aufgaben in Mathe, Deutsch und Physik, mit Preisen, die es nur hier gibt. Aus = keine Aufgaben, keine Erinnerungen und kein trauriges Haustier.",
        s.learnEnabled,
    ) { update(s.copy(learnEnabled = it)) }
    if (!s.learnEnabled) return
    Spacer(Modifier.height(6.dp))
    for (subject in Subject.entries) {
        IntSlider(
            "${subject.emoji} ${subject.title}",
            s.learnGrades[subject.name] ?: Learn.MIN_GRADE,
            Learn.MIN_GRADE..Learn.MAX_GRADE,
            { "Klasse $it" },
        ) { update(GameRepository.state.value.settings.let { cur -> cur.copy(learnGrades = cur.learnGrades + (subject.name to it)) }) }
    }
    Text(
        "Klasse 5 bis 13 (Gymnasium). Läuft es richtig gut oder gar nicht, passt sich die Schwierigkeit von selbst ein bisschen an.",
        style = MaterialTheme.typography.bodySmall, color = p.textMuted,
    )
    Spacer(Modifier.height(10.dp))
    IntSlider("Aufgaben pro Fach", s.learnPerSubject, 3..10, { "$it" }) { update(GameRepository.state.value.settings.copy(learnPerSubject = it)) }
    IntSlider("Erinnerung ab", s.learnHour, 6..21, { "$it Uhr" }) { update(GameRepository.state.value.settings.copy(learnHour = it)) }
    SwitchRow("Abends nochmal erinnern", "Wenn die Aufgaben um ${maxOf(s.learnHour + 3, 18)} Uhr noch nicht erledigt sind", s.learnEvening) { update(s.copy(learnEvening = it)) }
    Spacer(Modifier.height(4.dp))
    ClayTextButton("Lern-Übersicht für Eltern", onStats, emoji = "📊", small = true, color = p.sky, deep = p.skyDeep)
}

@Composable
private fun IntSlider(title: String, value: Int, range: IntRange, label: (Int) -> String, onChange: (Int) -> Unit) {
    val p = LocalPalette.current
    var v by remember(value) { mutableFloatStateOf(value.toFloat()) }
    val shown = v.roundToInt().coerceIn(range.first, range.last)
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(title, style = MaterialTheme.typography.titleSmall, color = p.text, modifier = Modifier.weight(1f))
        Pill(label(shown), color = p.sky.copy(alpha = 0.2f))
    }
    Slider(
        value = v,
        onValueChange = { v = it.roundToInt().toFloat() },
        onValueChangeFinished = { if (v.roundToInt() != value) onChange(v.roundToInt()) },
        valueRange = range.first.toFloat()..range.last.toFloat(),
        steps = (range.last - range.first - 1).coerceAtLeast(0),
        colors = SliderDefaults.colors(
            thumbColor = p.sky,
            activeTrackColor = p.sky,
            inactiveTrackColor = p.track,
            activeTickColor = Color.White.copy(alpha = 0.6f),
            inactiveTickColor = p.textMuted.copy(alpha = 0.4f),
        ),
    )
}
