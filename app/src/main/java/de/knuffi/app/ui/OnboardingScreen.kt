package de.knuffi.app.ui

import android.Manifest
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.knuffi.app.data.GameRepository
import de.knuffi.app.notify.Notifier
import de.knuffi.app.render.PetLook
import de.knuffi.app.ui.components.KButton
import de.knuffi.app.ui.components.KCard
import de.knuffi.app.ui.components.rememberHaptic
import de.knuffi.app.ui.theme.KnuffiTheme
import de.knuffi.app.ui.theme.LocalTokens
import de.knuffi.core.Action
import de.knuffi.core.Difficulty
import de.knuffi.core.Form
import de.knuffi.core.Mood
import de.knuffi.core.VisualStyle

object OnboardingNames {
    private val names = listOf(
        "Mochi", "Pixel", "Bobo", "Luna", "Kiki", "Nugget", "Flocke", "Krümel", "Bubu", "Tofu",
        "Momo", "Pünktchen", "Wolke", "Knöpfchen", "Sushi", "Fips", "Lumpi", "Keks", "Zimt", "Bohne",
    )

    fun random(): String = names.random()
}

private val eggLook = PetLook(Form.EGG, Mood.HAPPY)
private val babyLook = PetLook(Form.BABY, Mood.HAPPY)

@Composable
fun OnboardingScreen(initialStyle: VisualStyle = VisualStyle.KAWAII) {
    var step by rememberSaveable { mutableIntStateOf(0) }
    var style by rememberSaveable { mutableStateOf(initialStyle) }
    var difficulty by rememberSaveable { mutableStateOf(Difficulty.RELAXED) }
    var name by rememberSaveable { mutableStateOf(OnboardingNames.random()) }
    val context = LocalContext.current

    fun start() {
        GameRepository.perform(Action.Start(style, difficulty, name))
    }

    val notifLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { start() }

    BackHandler(enabled = step > 0) { step-- }

    KnuffiTheme(style) {
        Box(
            Modifier
                .fillMaxSize()
                .background(LocalTokens.current.background),
        ) {
            AnimatedContent(
                targetState = step,
                transitionSpec = {
                    val dir = if (targetState > initialState) 1 else -1
                    (slideInHorizontally(tween(320)) { it / 4 * dir } + fadeIn(tween(320))) togetherWith
                        (slideOutHorizontally(tween(260)) { -it / 4 * dir } + fadeOut(tween(200)))
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
                        0 -> Welcome(style) { step = 1 }
                        1 -> StylePick(style, onPick = { style = it }) { step = 2 }
                        2 -> DifficultyPick(difficulty, onPick = { difficulty = it }) { step = 3 }
                        else -> NamePick(style, name, onName = { name = it }) {
                            if (Build.VERSION.SDK_INT >= 33 && !Notifier.hasPermission(context)) {
                                notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                            } else {
                                start()
                            }
                        }
                    }
                }
            }
            Row(
                Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(bottom = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                for (i in 0..3) {
                    Box(
                        Modifier
                            .size(if (i == step) 22.dp else 8.dp, 8.dp)
                            .clip(CircleShape)
                            .background(if (i == step) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f)),
                    )
                }
            }
        }
    }
}

@Composable
private fun Welcome(style: VisualStyle, onNext: () -> Unit) {
    Spacer(Modifier.height(24.dp))
    PetPortrait(eggLook, style, Modifier.size(220.dp)) { f, t ->
        f.eggWobble = kotlin.math.sin(t * 3f) * 6f
        val phase = t % 2.4f
        if (phase < 0.35f) f.eggWobble = kotlin.math.sin(phase * 60f) * 10f
    }
    Spacer(Modifier.height(16.dp))
    Text("Willkommen bei Knuffi!", style = MaterialTheme.typography.headlineMedium, textAlign = TextAlign.Center)
    Spacer(Modifier.height(10.dp))
    Text(
        "Ein kleines Ei wartet darauf, von dir ausgebrütet zu werden. Füttere dein Haustier, spiel mit ihm, geh mit ihm Gassi und sieh zu, wie es sich entwickelt. Welche Form es am Ende annimmt, hängt ganz von dir ab!",
        style = MaterialTheme.typography.bodyLarge,
        textAlign = TextAlign.Center,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Spacer(Modifier.height(28.dp))
    KButton("Los geht's", onNext, emoji = "✨", modifier = Modifier.fillMaxWidth())
    Spacer(Modifier.height(24.dp))
}

@Composable
private fun StylePick(selected: VisualStyle, onPick: (VisualStyle) -> Unit, onNext: () -> Unit) {
    val haptic = rememberHaptic()
    Text("Wähle deinen Stil", style = MaterialTheme.typography.headlineMedium, textAlign = TextAlign.Center)
    Spacer(Modifier.height(6.dp))
    Text(
        "So sieht deine ganze App aus. Du kannst ihn später jederzeit ändern.",
        style = MaterialTheme.typography.bodyLarge,
        textAlign = TextAlign.Center,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Spacer(Modifier.height(16.dp))
    for (style in VisualStyle.entries) {
        val active = style == selected
        val bg = when (style) {
            VisualStyle.KAWAII -> Brush.linearGradient(listOf(Color(0xFFFFE3F1), Color(0xFFE9E1FF)))
            VisualStyle.PIXEL -> Brush.linearGradient(listOf(Color(0xFF14142A), Color(0xFF2E1B5E)))
            VisualStyle.MINIMAL -> Brush.linearGradient(listOf(Color(0xFFF3F1FA), Color(0xFFE4E1EC)))
        }
        val textColor = if (style == VisualStyle.PIXEL) Color(0xFFB8FF9E) else Color(0xFF3B2A40)
        val tokens = LocalTokens.current
        Row(
            Modifier
                .fillMaxWidth()
                .padding(vertical = 6.dp)
                .clip(tokens.cardShape)
                .background(bg)
                .border(if (active) 4.dp else 1.dp, if (active) MaterialTheme.colorScheme.primary else Color(0x33000000), tokens.cardShape)
                .clickable {
                    haptic()
                    onPick(style)
                }
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            PetPortrait(babyLook, style, Modifier.size(92.dp))
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(style.title, style = MaterialTheme.typography.titleLarge, color = textColor)
                Text(style.subtitle, style = MaterialTheme.typography.bodyMedium, color = textColor.copy(alpha = 0.8f))
            }
            if (active) Text("✓", fontSize = 26.sp, color = MaterialTheme.colorScheme.primary)
        }
    }
    Spacer(Modifier.height(18.dp))
    KButton("Weiter", onNext, emoji = "👉", modifier = Modifier.fillMaxWidth())
    Spacer(Modifier.height(24.dp))
}

@Composable
private fun DifficultyPick(selected: Difficulty, onPick: (Difficulty) -> Unit, onNext: () -> Unit) {
    Spacer(Modifier.height(12.dp))
    Text("Wie streng soll es sein?", style = MaterialTheme.typography.headlineMedium, textAlign = TextAlign.Center)
    Spacer(Modifier.height(6.dp))
    Text(
        "Kannst du jederzeit in den Einstellungen ändern.",
        style = MaterialTheme.typography.bodyLarge,
        textAlign = TextAlign.Center,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Spacer(Modifier.height(18.dp))
    for (d in Difficulty.entries) {
        ChoiceCard(
            emoji = if (d == Difficulty.RELAXED) "🌸" else "⚔️",
            title = d.title,
            text = d.description,
            selected = d == selected,
        ) { onPick(d) }
        Spacer(Modifier.height(10.dp))
    }
    Spacer(Modifier.height(18.dp))
    KButton("Weiter", onNext, emoji = "👉", modifier = Modifier.fillMaxWidth())
    Spacer(Modifier.height(24.dp))
}

@Composable
private fun NamePick(style: VisualStyle, name: String, onName: (String) -> Unit, onDone: () -> Unit) {
    val haptic = rememberHaptic()
    Spacer(Modifier.height(8.dp))
    PetPortrait(eggLook, style, Modifier.size(170.dp)) { f, t -> f.eggWobble = kotlin.math.sin(t * 2.5f) * 5f }
    Spacer(Modifier.height(10.dp))
    Text("Wie soll es heißen?", style = MaterialTheme.typography.headlineMedium, textAlign = TextAlign.Center)
    Spacer(Modifier.height(16.dp))
    KCard(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = name,
                onValueChange = { onName(it.take(16)) },
                label = { Text("Name") },
                singleLine = true,
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(10.dp))
            Box(
                Modifier
                    .size(52.dp)
                    .clip(LocalTokens.current.buttonShape)
                    .background(MaterialTheme.colorScheme.secondaryContainer)
                    .clickable {
                        haptic()
                        onName(OnboardingNames.random())
                    },
                contentAlignment = Alignment.Center,
            ) { Text("🎲", fontSize = 24.sp) }
        }
    }
    Spacer(Modifier.height(24.dp))
    KButton("Ei ausbrüten", onDone, emoji = "🥚", enabled = name.isNotBlank(), modifier = Modifier.fillMaxWidth())
    Spacer(Modifier.height(24.dp))
}
