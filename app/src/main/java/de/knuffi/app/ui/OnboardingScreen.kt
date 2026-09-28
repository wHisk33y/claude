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
    var step by rememberSaveable { mutableIntStateOf(0) }
    var difficulty by rememberSaveable { mutableStateOf(Difficulty.RELAXED) }
    var name by rememberSaveable { mutableStateOf(OnboardingNames.random()) }
    val context = LocalContext.current
    val p = LocalPalette.current

    fun start() {
        GameRepository.perform(Action.Start(difficulty, name.trim().ifBlank { OnboardingNames.random() }))
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
                    1 -> DifficultyPick(difficulty, onPick = { difficulty = it }, onBack = { step = 0 }) { step = 2 }
                    else -> NamePick(name, onName = { name = it }, onBack = { step = 1 }) {
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
            for (i in 0..2) {
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
private fun NamePick(name: String, onName: (String) -> Unit, onBack: () -> Unit, onDone: () -> Unit) {
    val p = LocalPalette.current
    StepHeader("Wie soll es heißen?", "Gib deinem Haustier einen Namen, bevor es schlüpft.", onBack)
    Box(Modifier.size(200.dp), contentAlignment = Alignment.Center) {
        PetPortrait(eggLook, Modifier.fillMaxSize(), sizeFactor = 0.6f) { pose, t ->
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
