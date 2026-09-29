package de.knuffi.app.ui

import android.Manifest
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.runtime.remember
import androidx.compose.ui.draw.shadow
import de.knuffi.app.ui.theme.KnuffiTheme
import de.knuffi.core.EggLine
import de.knuffi.core.LookStyle
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.knuffi.app.data.GameRepository
import de.knuffi.app.notify.Notifier
import de.knuffi.app.render.PetLook
import de.knuffi.app.ui.components.ClayTextButton
import de.knuffi.app.ui.components.GlassIconButton
import de.knuffi.app.ui.theme.LocalPalette
import de.knuffi.core.Action
import de.knuffi.core.Difficulty
import de.knuffi.core.Form
import de.knuffi.core.Mood
import kotlin.math.sin

object OnboardingNames {
    private val names = listOf(
        "Mochi", "Bobo", "Luna", "Kiki", "Nugget", "Flocke", "Krümel", "Bubu", "Tofu", "Momo",
        "Pünktchen", "Wolke", "Knöpfchen", "Sushi", "Fips", "Lumpi", "Keks", "Zimt", "Bohne", "Gnocchi",
    )

    fun random(): String = names.random()
}

private val eggLook = PetLook(Form.EGG, Mood.HAPPY)

@Composable
fun OnboardingScreen() {
    var look by rememberSaveable { mutableStateOf(LookStyle.ZAUBER) }
    // The chosen look colours the rest of the onboarding right away.
    KnuffiTheme(LocalPalette.current.dark, adventure = look == LookStyle.ABENTEUER) {
        OnboardingContent(look) { look = it }
    }
}

@Composable
private fun OnboardingContent(look: LookStyle, onLook: (LookStyle) -> Unit) {
    var step by rememberSaveable { mutableIntStateOf(0) }
    var difficulty by rememberSaveable { mutableStateOf(Difficulty.RELAXED) }
    var line by rememberSaveable { mutableStateOf(EggLine.KNUFFEL) }
    var name by rememberSaveable { mutableStateOf(OnboardingNames.random()) }
    val context = LocalContext.current
    val p = LocalPalette.current
    val chosenEgg = remember(line) { PetLook(Form.EGG, Mood.HAPPY, line = line) }

    fun start() {
        GameRepository.perform(Action.Start(difficulty, name.trim().ifBlank { OnboardingNames.random() }, look, line))
    }

    val notifLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { start() }

    BackHandler(enabled = step > 0) { step-- }

    Box(Modifier.fillMaxSize()) {
        DreamyBackground(Modifier.fillMaxSize())
        AnimatedContent(
            targetState = step,
            transitionSpec = {
                val dir = if (targetState > initialState) 1 else -1
                (slideInHorizontally(spring(dampingRatio = 0.85f, stiffness = 300f)) { it / 3 * dir } + fadeIn(tween(320))) togetherWith
                    (slideOutHorizontally(tween(260)) { -it / 3 * dir } + fadeOut(tween(200)))
            },
            label = "onboarding",
        ) { s ->
            Column(
                Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .navigationBarsPadding()
                    .imePadding()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp, vertical = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                when (s) {
                    0 -> Welcome { step = 1 }
                    1 -> LookPick(look, onPick = onLook, onBack = { step = 0 }) { step = 2 }
                    2 -> EggPick(line, look, onPick = { line = it }, onBack = { step = 1 }) { step = 3 }
                    3 -> DifficultyPick(difficulty, onPick = { difficulty = it }, onBack = { step = 2 }) { step = 4 }
                    else -> NamePick(name, chosenEgg, onName = { name = it }, onBack = { step = 3 }) {
                        if (Build.VERSION.SDK_INT >= 33 && !Notifier.hasPermission(context)) {
                            notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        } else {
                            start()
                        }
                    }
                }
                Spacer(Modifier.height(40.dp))
            }
        }
        Row(
            Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            for (i in 0..4) {
                val w by animateDpAsState(if (i == step) 26.dp else 8.dp, spring(dampingRatio = 0.6f), label = "dot")
                Box(
                    Modifier
                        .size(w, 8.dp)
                        .clip(CircleShape)
                        .background(if (i == step) p.pink else p.text.copy(alpha = 0.2f)),
                )
            }
        }
    }
}

/** Soft floating blobs and sparkles behind the onboarding. */
@Composable
private fun DreamyBackground(modifier: Modifier = Modifier) {
    val p = LocalPalette.current
    val time = rememberFrameTime()
    Canvas(modifier) {
        val t = time.floatValue
        val w = size.width
        val h = size.height
        drawRect(p.background)
        val blobs = listOf(
            Triple(0.15f, 0.2f, p.pink),
            Triple(0.85f, 0.35f, p.violet),
            Triple(0.3f, 0.8f, p.sky),
            Triple(0.8f, 0.85f, p.mint),
        )
        for ((i, b) in blobs.withIndex()) {
            val cx = w * (b.first + 0.05f * sin(t * 0.4f + i))
            val cy = h * (b.second + 0.03f * sin(t * 0.3f + i * 2f))
            val r = w * 0.45f
            drawCircle(Brush.radialGradient(listOf(b.third.copy(alpha = if (p.dark) 0.28f else 0.35f), Color.Transparent), center = Offset(cx, cy), radius = r), radius = r, center = Offset(cx, cy))
        }
        for (i in 0 until 18) {
            val x = ((i * 0.137f + t * 0.01f * (1 + i % 3)) % 1f) * w
            val y = (((i * 0.311f) - t * 0.02f * (1 + i % 2)) % 1f + 1f) % 1f * h
            val a = 0.3f + 0.3f * sin(t * 2f + i)
            drawCircle(Color.White.copy(alpha = a), radius = (2 + i % 3).dp.toPx(), center = Offset(x, y))
        }
    }
}

@Composable
private fun ColumnScope.Welcome(onNext: () -> Unit) {
    val p = LocalPalette.current
    Spacer(Modifier.height(30.dp))
    Box(Modifier.size(260.dp), contentAlignment = Alignment.Center) {
        val time = rememberFrameTime()
        Canvas(Modifier.fillMaxSize()) {
            val t = time.floatValue
            val c = Offset(size.width / 2f, size.height * 0.55f)
            drawCircle(Brush.radialGradient(listOf(p.gold.copy(alpha = 0.45f), Color.Transparent), center = c, radius = size.minDimension * 0.5f), radius = size.minDimension * 0.5f, center = c)
            // nest
            drawOval(Color.Black.copy(alpha = 0.12f), topLeft = Offset(size.width * 0.22f, size.height * 0.84f), size = Size(size.width * 0.56f, size.height * 0.08f))
            drawOval(Brush.verticalGradient(listOf(Color(0xFFD9A46B), Color(0xFF9C6A3E)), startY = size.height * 0.74f, endY = size.height * 0.9f), topLeft = Offset(size.width * 0.24f, size.height * 0.74f), size = Size(size.width * 0.52f, size.height * 0.15f))
            for (i in 0 until 7) {
                val x = size.width * (0.28f + i * 0.07f)
                drawLine(Color(0xFF7A4E2A).copy(alpha = 0.6f), Offset(x, size.height * 0.77f), Offset(x + size.width * 0.05f, size.height * 0.86f), strokeWidth = 2.dp.toPx())
            }
            for (i in 0 until 6) {
                val a = t * 0.8f + i * 1.05f
                val r = size.minDimension * 0.4f
                val x = c.x + kotlin.math.cos(a) * r
                val y = c.y - size.height * 0.08f + sin(a) * r * 0.35f
                drawCircle(Color.White.copy(alpha = 0.5f + 0.4f * sin(t * 3f + i)), radius = 3.dp.toPx(), center = Offset(x, y))
            }
        }
        PetPortrait(eggLook, Modifier.fillMaxSize(), sizeFactor = 0.55f, groundFactor = 0.84f, shadow = false) { pose, t ->
            pose.eggWobble = sin(t * 2.2f) * 4f
            val phase = t % 2.6f
            if (phase < 0.4f) pose.eggWobble += sin(phase * 50f) * 9f
        }
    }
    Spacer(Modifier.height(12.dp))
    Text("Willkommen bei Knuffi!", style = MaterialTheme.typography.headlineLarge, color = p.text, textAlign = TextAlign.Center)
    Spacer(Modifier.height(10.dp))
    Text(
        "Ein kleines Ei wartet auf dich. Brüte es aus, füttere und knuddle dein Haustier, spiel mit ihm und geh mit ihm Gassi. Wie es sich entwickelt, hängt ganz von dir ab!",
        style = MaterialTheme.typography.bodyLarge,
        textAlign = TextAlign.Center,
        color = p.textMuted,
    )
    Spacer(Modifier.height(18.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        for (e in listOf("🍙", "🎮", "👟", "🎀", "🏆")) {
            Box(
                Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(p.glass),
                contentAlignment = Alignment.Center,
            ) { Text(e, fontSize = 20.sp) }
        }
    }
    Spacer(Modifier.height(28.dp))
    ClayTextButton("Los geht's", onNext, emoji = "✨", modifier = Modifier.fillMaxWidth())
}

@Composable
private fun StepHeader(title: String, subtitle: String, onBack: () -> Unit) {
    val p = LocalPalette.current
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        GlassIconButton("⬅️", onBack)
        Spacer(Modifier.width(12.dp))
        Text(title, style = MaterialTheme.typography.headlineSmall, color = p.text, modifier = Modifier.weight(1f))
    }
    Spacer(Modifier.height(8.dp))
    Text(subtitle, style = MaterialTheme.typography.bodyLarge, color = p.textMuted, modifier = Modifier.fillMaxWidth())
    Spacer(Modifier.height(18.dp))
}

@Composable
private fun DifficultyPick(selected: Difficulty, onPick: (Difficulty) -> Unit, onBack: () -> Unit, onNext: () -> Unit) {
    StepHeader("Wie streng soll es sein?", "Das kannst du später in den Einstellungen ändern.", onBack)
    for (d in Difficulty.entries) {
        ChoiceCard(
            emoji = if (d == Difficulty.RELAXED) "🌸" else "⚔️",
            title = d.title,
            text = d.description,
            selected = selected == d,
        ) { onPick(d) }
        Spacer(Modifier.height(12.dp))
    }
    Spacer(Modifier.height(16.dp))
    ClayTextButton("Weiter", onNext, emoji = "➡️", modifier = Modifier.fillMaxWidth())
}

@Composable
private fun LookPick(selected: LookStyle, onPick: (LookStyle) -> Unit, onBack: () -> Unit, onNext: () -> Unit) {
    StepHeader(
        "Welcher Look gefällt dir?",
        "Alle Haustiere, Kleider und Möbel gibt es für alle. Der Look bestimmt nur die Farben und womit du startest. Du kannst ihn jederzeit ändern.",
        onBack,
    )
    LookCard(
        LookStyle.ZAUBER, "✨", "Zauber", "Rosa und Flieder. Einhörner, Feen und Blüten.",
        listOf(Color(0xFFFF8FC8), Color(0xFFB99AFF)), selected == LookStyle.ZAUBER,
    ) { onPick(LookStyle.ZAUBER) }
    Spacer(Modifier.height(12.dp))
    LookCard(
        LookStyle.ABENTEUER, "🐉", "Abenteuer", "Blau und Grün. Drachen, Dinos und Roboter.",
        listOf(Color(0xFF4C9BFF), Color(0xFF2EC4A0)), selected == LookStyle.ABENTEUER,
    ) { onPick(LookStyle.ABENTEUER) }
    Spacer(Modifier.height(20.dp))
    ClayTextButton("Weiter", onNext, emoji = "➡️", modifier = Modifier.fillMaxWidth())
}

@Composable
private fun LookCard(style: LookStyle, emoji: String, title: String, text: String, colors: List<Color>, selected: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(28.dp)
    val eggs = remember(style) { EggLine.entries.filter { it.look == style }.map { PetLook(Form.EGG, Mood.HAPPY, line = it) } }
    Column(
        Modifier
            .fillMaxWidth()
            .shadow(if (selected) 14.dp else 4.dp, shape, ambientColor = colors[0], spotColor = colors[0])
            .clip(shape)
            .background(Brush.linearGradient(colors))
            .border(if (selected) 3.dp else 1.dp, Color.White.copy(alpha = if (selected) 0.95f else 0.4f), shape)
            .clickable(onClick = onClick)
            .padding(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(emoji, fontSize = 34.sp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.headlineSmall, color = Color.White)
                Text(text, style = MaterialTheme.typography.bodyMedium, color = Color.White.copy(alpha = 0.92f))
            }
            if (selected) Text("✓", style = MaterialTheme.typography.headlineMedium, color = Color.White)
        }
        Row(Modifier.fillMaxWidth().height(80.dp)) {
            for (egg in eggs) PetPortrait(egg, Modifier.weight(1f).fillMaxHeight(), animated = false, sizeFactor = 0.8f, shadow = false)
        }
    }
}

@Composable
private fun EggPick(selected: EggLine, look: LookStyle, onPick: (EggLine) -> Unit, onBack: () -> Unit, onNext: () -> Unit) {
    val p = LocalPalette.current
    StepHeader("Wähle dein erstes Ei", "Jede Ei-Sorte hat eine eigene Familie mit 8 Wesen. Die anderen Eier kannst du später sammeln.", onBack)
    val lines = remember(look) {
        EggLine.entries.filter { !it.eventOnly }.sortedBy { if (it == EggLine.KNUFFEL) 0 else if (it.look == look) 1 else if (it.look == null) 2 else 3 }
    }
    for (row in lines.chunked(3)) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
            for (l in row) {
                val active = l == selected
                val shape = RoundedCornerShape(22.dp)
                Column(
                    Modifier
                        .weight(1f)
                        .clip(shape)
                        .background(if (active) p.pink.copy(alpha = 0.22f) else p.glass)
                        .border(if (active) 2.5.dp else 1.dp, if (active) p.pink else p.glassBorder, shape)
                        .clickable { onPick(l) }
                        .padding(vertical = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    PetPortrait(remember(l) { PetLook(Form.EGG, Mood.HAPPY, line = l) }, Modifier.size(70.dp), animated = active, sizeFactor = 0.8f) { pose, t ->
                        pose.eggWobble = sin(t * 3f) * 5f
                    }
                    Text(l.title, style = MaterialTheme.typography.labelMedium, color = p.text, maxLines = 1)
                }
            }
            repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
        }
        Spacer(Modifier.height(10.dp))
    }
    Text("${selected.emoji} ${selected.description}", style = MaterialTheme.typography.bodyMedium, color = p.textMuted, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
    Spacer(Modifier.height(18.dp))
    ClayTextButton("Dieses Ei nehmen", onNext, emoji = selected.emoji, modifier = Modifier.fillMaxWidth())
}

@Composable
private fun NamePick(name: String, egg: PetLook, onName: (String) -> Unit, onBack: () -> Unit, onDone: () -> Unit) {
    val p = LocalPalette.current
    StepHeader("Wie soll es heißen?", "Gib deinem Haustier einen Namen, bevor es schlüpft.", onBack)
    Box(Modifier.size(200.dp), contentAlignment = Alignment.Center) {
        PetPortrait(egg, Modifier.fillMaxSize(), sizeFactor = 0.6f) { pose, t ->
            pose.eggWobble = sin(t * 3f) * 5f
        }
    }
    Text(
        name.ifBlank { "…" },
        style = MaterialTheme.typography.headlineSmall,
        color = Color.White,
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(Brush.horizontalGradient(listOf(p.pink, p.violet)))
            .padding(horizontal = 18.dp, vertical = 6.dp),
    )
    Spacer(Modifier.height(18.dp))
    OutlinedTextField(
        value = name,
        onValueChange = { onName(it.take(16)) },
        label = { Text("Name") },
        singleLine = true,
        shape = RoundedCornerShape(18.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = p.pink,
            focusedLabelColor = p.pink,
            cursorColor = p.pink,
            focusedContainerColor = p.glass,
            unfocusedContainerColor = p.glass,
        ),
        modifier = Modifier.fillMaxWidth(),
    )
    Spacer(Modifier.height(10.dp))
    ClayTextButton("Zufälliger Name", { onName(OnboardingNames.random()) }, emoji = "🎲", small = true, color = p.violet, deep = p.violetDeep)
    Spacer(Modifier.height(22.dp))
    ClayTextButton("Ei ins Nest legen", onDone, emoji = "🥚", modifier = Modifier.fillMaxWidth(), enabled = name.isNotBlank())
}
