package de.knuffi.app.ui

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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.knuffi.app.data.GameRepository
import de.knuffi.app.render.EyeShape
import de.knuffi.app.render.MouthShape
import de.knuffi.app.sound.Sfx
import de.knuffi.app.sound.Sounds
import de.knuffi.app.ui.components.ClayButton
import de.knuffi.app.ui.components.ClayTextButton
import de.knuffi.app.ui.components.CoinPill
import de.knuffi.app.ui.components.GlossyBar
import de.knuffi.app.ui.components.PageHeader
import de.knuffi.app.ui.components.Pill
import de.knuffi.app.ui.components.SectionHeader
import de.knuffi.app.ui.components.SegmentedControl
import de.knuffi.app.ui.components.SurfaceCard
import de.knuffi.app.ui.theme.LocalPalette
import de.knuffi.core.Action
import de.knuffi.core.GameState
import de.knuffi.core.Quiz
import de.knuffi.core.QuizCategory
import de.knuffi.core.QuizLevel
import de.knuffi.core.QuizQuestion
import kotlin.math.abs
import kotlin.math.sin
import kotlin.random.Random

private enum class QuizPhase { SETUP, PLAY, RESULT }

private val rightLines = listOf("Richtig! 🎉", "Stimmt genau! ⭐", "Super! 💪", "Klasse gewusst! 🧠", "Bingo! 🎯")
private val wrongLines = listOf("Knapp daneben … 🤔", "Nicht schlimm, jetzt weißt du's! 💡", "Oh, das war knifflig! 🙈")

@Composable
fun QuizScreen(state: GameState, onBack: () -> Unit) {
    var phase by remember { mutableStateOf(QuizPhase.SETUP) }
    var category by remember { mutableStateOf<QuizCategory?>(null) }
    var level by remember { mutableStateOf(QuizLevel.GEMISCHT) }
    val players = remember { mutableStateListOf("Spieler 1") }
    var questions by remember { mutableStateOf<List<QuizQuestion>>(emptyList()) }
    val scores = remember { mutableStateListOf<Int>() }
    val jokers = remember { mutableStateListOf<Boolean>() }

    fun start() {
        val total = if (players.size == 1) Quiz.ROUND else (players.size * 5).coerceAtMost(20)
        questions = Quiz.round(category, level, GameRepository.current.quiz.seen, Random(System.nanoTime()), total)
        scores.clear()
        jokers.clear()
        repeat(players.size) {
            scores += 0
            jokers += true
        }
        phase = QuizPhase.PLAY
    }

    Column(Modifier.fillMaxSize()) {
        PageHeader("Wissensquiz", onBack) { CoinPill(state.coins) }
        when (phase) {
            QuizPhase.SETUP -> QuizSetup(
                state, category, level, players,
                onCategory = { category = it },
                onLevel = { level = it },
                onStart = { start() },
            )
            QuizPhase.PLAY -> QuizPlay(state, questions, players, scores, jokers) { phase = QuizPhase.RESULT }
            QuizPhase.RESULT -> QuizResult(state, category, questions.size, players, scores, onAgain = { start() }, onSetup = { phase = QuizPhase.SETUP })
        }
    }
}

@Composable
private fun QuizSetup(
    state: GameState,
    category: QuizCategory?,
    level: QuizLevel,
    players: MutableList<String>,
    onCategory: (QuizCategory?) -> Unit,
    onLevel: (QuizLevel) -> Unit,
    onStart: () -> Unit,
) {
    val p = LocalPalette.current
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).navigationBarsPadding().padding(horizontal = 16.dp)) {
        SurfaceCard(Modifier.fillMaxWidth(), color = if (p.dark) p.surfaceAlt else Color(0xFFEFF4FF)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                val look = remember(state.pet, state.equipped) { learnLook(state) }
                Box(Modifier.size(92.dp), contentAlignment = Alignment.Center) {
                    if (look == null || look.isEgg) Text("🧠", fontSize = 48.sp)
                    else PetPortrait(look, Modifier.fillMaxSize(), sizeFactor = 0.8f) { pose, t ->
                        pose.holding = "❓"
                        pose.hold = 1f
                        pose.tilt = sin(t * 1.4f) * 6f
                    }
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text("Teste dein Wissen!", style = MaterialTheme.typography.titleMedium, color = p.text)
                    Text(
                        "${Quiz.all.size} Fragen aus ${QuizCategory.entries.size} Themen, für Kinder und Erwachsene. Nach jeder Antwort gibt es eine kurze Erklärung.",
                        style = MaterialTheme.typography.bodySmall, color = p.textMuted,
                    )
                    if (state.quiz.answered > 0) {
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "Schon ${state.quiz.answered} Fragen beantwortet, ${state.quiz.right} davon richtig.",
                            style = MaterialTheme.typography.labelMedium, color = p.violet,
                        )
                    }
                }
            }
        }

        SectionHeader("Thema", "🗂️")
        val items = listOf<QuizCategory?>(null) + QuizCategory.entries
        for (row in items.chunked(2)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for (c in row) {
                    val selected = c == category
                    val best = state.quiz.best[c?.name ?: Quiz.ALL]
                    val shape = RoundedCornerShape(18.dp)
                    Row(
                        Modifier
                            .weight(1f)
                            .clip(shape)
                            .background(if (selected) Brush.linearGradient(listOf(p.sky, p.violet)) else Brush.linearGradient(listOf(p.surface, p.surface)))
                            .border(if (selected) 0.dp else 1.dp, p.glassBorder, shape)
                            .clickable { onCategory(c) }
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(c?.emoji ?: "🎲", fontSize = 22.sp)
                        Spacer(Modifier.width(8.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                c?.title ?: "Alle Themen",
                                style = MaterialTheme.typography.labelLarge,
                                color = if (selected) Color.White else p.text,
                                maxLines = 2,
                            )
                            if (best != null) {
                                Text(
                                    "Bestwert $best",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (selected) Color.White.copy(alpha = 0.85f) else p.textMuted,
                                )
                            }
                        }
                    }
                }
                if (row.size < 2) Spacer(Modifier.weight(1f))
            }
            Spacer(Modifier.height(8.dp))
        }

        SectionHeader("Schwierigkeit", "🎚️")
        SegmentedControl(QuizLevel.entries.map { "${it.emoji} ${it.title}" }, level.ordinal, { onLevel(QuizLevel.entries[it]) }, Modifier.fillMaxWidth())
        Spacer(Modifier.height(4.dp))
        Text(
            when (level) {
                QuizLevel.LEICHT -> "Für Kinder ab der Grundschule."
                QuizLevel.MITTEL -> "Für Schulkinder und alle, die gerne knobeln."
                QuizLevel.SCHWER -> "Für Profis und Erwachsene."
                QuizLevel.GEMISCHT -> "Von leicht bis schwer, die leichten zuerst."
            },
            style = MaterialTheme.typography.bodySmall, color = p.textMuted,
        )

        SectionHeader("Mitspieler", "👥")
        SegmentedControl(listOf("1", "2", "3", "4"), players.size - 1, { n ->
            val want = n + 1
            while (players.size < want) players += "Spieler ${players.size + 1}"
            while (players.size > want) players.removeAt(players.size - 1)
        }, Modifier.fillMaxWidth())
        if (players.size > 1) {
            Spacer(Modifier.height(6.dp))
            Text("Ihr seid abwechselnd dran, jeder bekommt 5 Fragen.", style = MaterialTheme.typography.bodySmall, color = p.textMuted)
            for (i in players.indices) {
                OutlinedTextField(
                    value = players[i],
                    onValueChange = { players[i] = it.take(14) },
                    singleLine = true,
                    label = { Text("Name ${i + 1}") },
                    modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                )
            }
        }
        Spacer(Modifier.height(18.dp))
        ClayTextButton("Los geht's!", onStart, Modifier.fillMaxWidth(), emoji = "🧠", color = p.sky, deep = p.skyDeep)
        Spacer(Modifier.height(28.dp))
    }
}

@Composable
private fun QuizPlay(
    state: GameState,
    questions: List<QuizQuestion>,
    players: List<String>,
    scores: MutableList<Int>,
    jokers: MutableList<Boolean>,
    onDone: () -> Unit,
) {
    val p = LocalPalette.current
    var index by remember { mutableIntStateOf(0) }
    val q = questions.getOrNull(index) ?: run {
        LaunchedEffect(Unit) { onDone() }
        return
    }
    val player = index % players.size
    val choices = remember(index) { q.choices(Random(q.id.toLong() * 31 + index)) }
    var picked by remember(index) { mutableStateOf<String?>(null) }
    var hidden by remember(index) { mutableStateOf(setOf<String>()) }
    var reactedAt by remember { mutableLongStateOf(0L) }
    val right = picked == q.answer

    fun pick(c: String) {
        if (picked != null) return
        picked = c
        reactedAt = System.currentTimeMillis()
        val ok = c == q.answer
        if (ok) scores[player] = scores[player] + 1
        Sounds.play(if (ok) Sfx.SUCCESS else Sfx.BONK)
        GameRepository.perform(Action.QuizAnswered(q.id, ok))
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).navigationBarsPadding().padding(horizontal = 16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Frage ${index + 1} von ${questions.size}", style = MaterialTheme.typography.titleSmall, color = p.text, modifier = Modifier.weight(1f))
            if (players.size == 1) Pill("✅ ${scores[0]}", color = p.mint.copy(alpha = 0.2f))
        }
        Spacer(Modifier.height(6.dp))
        GlossyBar((index + if (picked != null) 1 else 0) / questions.size.toFloat(), p.sky, p.skyDeep, Modifier.fillMaxWidth(), height = 8.dp)
        if (players.size > 1) {
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                for ((i, name) in players.withIndex()) {
                    Pill(
                        "${if (i == player) "👉 " else ""}${name.ifBlank { "Spieler ${i + 1}" }}: ${scores[i]}",
                        color = if (i == player) p.pink.copy(alpha = 0.25f) else p.violet.copy(alpha = 0.12f),
                    )
                }
            }
        }
        Spacer(Modifier.height(10.dp))

        Row(verticalAlignment = Alignment.CenterVertically) {
            val look = remember(state.pet, state.equipped) { learnLook(state) }
            Box(Modifier.size(84.dp), contentAlignment = Alignment.Center) {
                if (look == null || look.isEgg) Text("🧠", fontSize = 44.sp)
                else PetPortrait(look, Modifier.fillMaxSize(), sizeFactor = 0.8f) { pose, t ->
                    val since = (System.currentTimeMillis() - reactedAt) / 1000f
                    when {
                        picked != null && right && since < 1.8f -> {
                            pose.eyes = EyeShape.HAPPY
                            pose.mouth = MouthShape.GRIN
                            pose.armL = 1f
                            pose.armR = 1f
                            pose.armWaveL = sin(t * 14f) * 22f
                            pose.armWaveR = -sin(t * 14f) * 22f
                            pose.lift = 0.06f * abs(sin(t * 9f))
                        }
                        picked != null && !right && since < 1.8f -> {
                            pose.mouth = MouthShape.WAVY
                            pose.brows = 0.6f
                            pose.lookX = 0.4f * sin(t * 5f)
                        }
                        else -> {
                            pose.holding = "❓"
                            pose.hold = 1f
                            pose.lookY = -0.3f
                            pose.tilt = sin(t * 1.3f) * 5f
                        }
                    }
                }
            }
            Spacer(Modifier.width(8.dp))
            Text(
                when {
                    picked == null && players.size > 1 -> "${players[player].ifBlank { "Spieler ${player + 1}" }}, du bist dran!"
                    picked == null -> "Was meinst du? 🤔"
                    right -> rightLines[index % rightLines.size]
                    else -> wrongLines[index % wrongLines.size]
                },
                style = MaterialTheme.typography.bodyMedium, color = p.text,
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(topStart = 6.dp, topEnd = 20.dp, bottomEnd = 20.dp, bottomStart = 20.dp))
                    .background(if (p.dark) p.surfaceAlt else Color.White)
                    .padding(horizontal = 14.dp, vertical = 10.dp),
            )
        }
        Spacer(Modifier.height(10.dp))

        SurfaceCard(Modifier.fillMaxWidth(), contentPadding = PaddingValues(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Pill("${q.category.emoji} ${q.category.title}", color = p.sky.copy(alpha = 0.18f))
                Spacer(Modifier.weight(1f))
                Text("⭐".repeat(q.level), fontSize = 12.sp)
            }
            Spacer(Modifier.height(12.dp))
            Text(q.question, style = MaterialTheme.typography.titleLarge, color = p.text)
        }
        Spacer(Modifier.height(10.dp))
        for (c in choices) {
            val st = when {
                picked == null -> 0
                c == q.answer -> 1
                c == picked -> -1
                else -> 2
            }
            QuizChoice(c, st, enabled = picked == null && c !in hidden, gone = c in hidden) { pick(c) }
            Spacer(Modifier.height(8.dp))
        }

        if (picked == null) {
            if (jokers.getOrElse(player) { false }) {
                ClayTextButton("50:50-Joker", {
                    jokers[player] = false
                    hidden = q.wrong.shuffled(Random(q.id.toLong())).take(2).toSet()
                }, small = true, emoji = "🃏", color = p.gold, deep = p.goldDeep)
            }
        } else {
            SurfaceCard(Modifier.fillMaxWidth(), color = if (p.dark) p.surfaceAlt else if (right) Color(0xFFEAFBF2) else Color(0xFFFFF4DC), contentPadding = PaddingValues(14.dp)) {
                Text(
                    if (right) "✅ Richtig!" else "❌ Leider falsch. Richtig ist: ${q.answer}",
                    style = MaterialTheme.typography.titleSmall, color = if (right) p.mintDeep else p.text,
                )
                Spacer(Modifier.height(6.dp))
                Text("💡 ${q.info}", style = MaterialTheme.typography.bodyMedium, color = p.text)
            }
            Spacer(Modifier.height(12.dp))
            val last = index == questions.size - 1
            ClayTextButton(if (last) "Zum Ergebnis" else "Weiter", { if (last) onDone() else index++ }, Modifier.fillMaxWidth(), emoji = if (last) "🏁" else "➡️", color = p.sky, deep = p.skyDeep)
        }
        Spacer(Modifier.height(28.dp))
    }
}

@Composable
private fun QuizChoice(text: String, state: Int, enabled: Boolean, gone: Boolean, onClick: () -> Unit) {
    val p = LocalPalette.current
    val color = when (state) {
        1 -> p.mint
        -1 -> p.red.copy(alpha = 0.6f)
        else -> if (p.dark) p.surfaceAlt else Color.White
    }
    val deep = when (state) {
        1 -> p.mintDeep
        -1 -> p.redDeep.copy(alpha = 0.6f)
        else -> p.glassBorder
    }
    ClayButton(
        onClick,
        Modifier.fillMaxWidth().alpha(if (gone) 0.25f else if (state == 2) 0.55f else 1f),
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
            color = if (state == 1 || state == -1) Color.White else p.text,
            textAlign = TextAlign.Center,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun QuizResult(
    state: GameState,
    category: QuizCategory?,
    total: Int,
    players: List<String>,
    scores: List<Int>,
    onAgain: () -> Unit,
    onSetup: () -> Unit,
) {
    val p = LocalPalette.current
    val sum = scores.sum()
    LaunchedEffect(Unit) {
        GameRepository.perform(Action.QuizRoundDone(category, sum, total))
        Sounds.play(Sfx.FANFARE)
    }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).navigationBarsPadding().padding(horizontal = 16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        val look = remember(state.pet, state.equipped) { learnLook(state) }
        Box(Modifier.size(140.dp), contentAlignment = Alignment.Center) {
            if (look == null || look.isEgg) Text("🏆", fontSize = 72.sp)
            else PetPortrait(look, Modifier.fillMaxSize(), sizeFactor = 0.8f) { pose, t ->
                pose.eyes = EyeShape.HAPPY
                pose.mouth = MouthShape.GRIN
                pose.armL = 1f
                pose.armR = 1f
                pose.armWaveL = sin(t * 12f) * 22f
                pose.armWaveR = -sin(t * 12f) * 22f
                pose.lift = 0.06f * abs(sin(t * 7f))
            }
        }
        if (players.size == 1) {
            val s = scores.firstOrNull() ?: 0
            Text("$s von $total richtig!", style = MaterialTheme.typography.headlineSmall, color = p.text)
            Spacer(Modifier.height(4.dp))
            Text(
                when {
                    s == total -> "Perfekt! Du bist ein echtes Quiz-Ass! 🏆"
                    s >= total * 0.7f -> "Richtig stark! 🌟"
                    s >= total * 0.4f -> "Gut gemacht! Beim nächsten Mal noch mehr! 💪"
                    else -> "Jetzt weißt du viel Neues. Probier's gleich nochmal! 📚"
                },
                style = MaterialTheme.typography.bodyLarge, color = p.textMuted, textAlign = TextAlign.Center,
            )
        } else {
            Text("Ergebnis", style = MaterialTheme.typography.headlineSmall, color = p.text)
            Spacer(Modifier.height(8.dp))
            val ranking = players.indices.sortedByDescending { scores[it] }
            val top = scores.maxOrNull() ?: 0
            for ((place, i) in ranking.withIndex()) {
                val medal = when {
                    scores[i] == top -> "🥇"
                    place == 1 -> "🥈"
                    place == 2 -> "🥉"
                    else -> "🎖️"
                }
                SurfaceCard(Modifier.fillMaxWidth().padding(vertical = 4.dp), contentPadding = PaddingValues(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(medal, fontSize = 26.sp)
                        Spacer(Modifier.width(10.dp))
                        Text(players[i].ifBlank { "Spieler ${i + 1}" }, style = MaterialTheme.typography.titleMedium, color = p.text, modifier = Modifier.weight(1f))
                        Text("${scores[i]} von ${total / players.size}", style = MaterialTheme.typography.titleSmall, color = p.textMuted)
                    }
                }
            }
            if (scores.count { it == top } > 1) Text("Unentschieden! 🤝", style = MaterialTheme.typography.bodyMedium, color = p.textMuted)
        }
        Spacer(Modifier.height(12.dp))
        Pill("+${Quiz.coins(sum, total)} 🪙   +${Quiz.xp(sum)} XP", color = p.gold.copy(alpha = 0.25f))
        Spacer(Modifier.height(20.dp))
        ClayTextButton("Nochmal spielen", onAgain, Modifier.fillMaxWidth(), emoji = "🔁", color = p.sky, deep = p.skyDeep)
        Spacer(Modifier.height(10.dp))
        ClayTextButton("Anderes Thema", onSetup, Modifier.fillMaxWidth(), emoji = "🗂️", color = p.violet, deep = p.violetDeep)
        Spacer(Modifier.height(28.dp))
    }
}

/** Card for the games hall and the world hub. */
@Composable
fun QuizBanner(state: GameState, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val p = LocalPalette.current
    SurfaceCard(modifier.fillMaxWidth(), onClick = onClick, color = if (p.dark) p.surfaceAlt else Color(0xFFF1EBFF)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(50.dp).clip(RoundedCornerShape(16.dp)).background(Brush.linearGradient(listOf(p.violet, p.sky))),
                contentAlignment = Alignment.Center,
            ) { Text("🧠", fontSize = 28.sp) }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("Wissensquiz", style = MaterialTheme.typography.titleMedium, color = p.text)
                Text(
                    "${Quiz.all.size} Fragen für die ganze Familie · auch zu zweit bis zu viert",
                    style = MaterialTheme.typography.bodySmall, color = p.textMuted, maxLines = 2,
                )
            }
            Text("▶️", fontSize = 22.sp)
        }
    }
}
