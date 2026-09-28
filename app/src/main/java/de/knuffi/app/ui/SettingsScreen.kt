package de.knuffi.app.ui

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import de.knuffi.app.data.GameRepository
import de.knuffi.app.notify.Notifier
import de.knuffi.app.render.PetLook
import de.knuffi.app.ui.components.KButton
import de.knuffi.app.ui.components.KCard
import de.knuffi.app.ui.components.PageHeader
import de.knuffi.app.ui.components.SectionTitle
import de.knuffi.app.ui.theme.LocalTokens
import de.knuffi.core.Action
import de.knuffi.core.Difficulty
import de.knuffi.core.Form
import de.knuffi.core.GameState
import de.knuffi.core.Mood
import de.knuffi.core.Settings
import de.knuffi.core.TimeUtil
import de.knuffi.core.VisualStyle
import kotlin.math.roundToInt

@Composable
fun SettingsScreen(state: GameState, onBack: () -> Unit) {
    val context = LocalContext.current
    var confirmReset by remember { mutableStateOf(false) }
    var confirmClassic by remember { mutableStateOf(false) }
    var confirmNewEgg by remember { mutableStateOf(false) }
    var name by remember { mutableStateOf(state.pet?.name ?: "") }
    val s = state.settings
    var goal by remember { mutableFloatStateOf(s.stepGoal.toFloat()) }
    val notifLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    fun update(settings: Settings) {
        GameRepository.perform(Action.UpdateSettings(settings))
    }

    Column(Modifier.fillMaxSize()) {
        PageHeader("Einstellungen", onBack)
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(horizontal = 16.dp),
        ) {
            SectionTitle("Grafikstil", "🎨")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for (style in VisualStyle.entries) {
                    StyleOption(state, style, state.style == style, Modifier.weight(1f)) {
                        GameRepository.perform(Action.SetStyle(style))
                    }
                }
            }

            SectionTitle("Schwierigkeit", "⚔️")
            for (d in Difficulty.entries) {
                ChoiceCard(
                    emoji = if (d == Difficulty.RELAXED) "🌸" else "⚔️",
                    title = d.title,
                    text = d.description,
                    selected = state.difficulty == d,
                ) {
                    if (d == Difficulty.CLASSIC && state.difficulty != Difficulty.CLASSIC) {
                        confirmClassic = true
                    } else {
                        GameRepository.perform(Action.SetDifficulty(d))
                    }
                }
                Spacer(Modifier.height(8.dp))
            }

            SectionTitle("Benachrichtigungen", "🔔")
            KCard(Modifier.fillMaxWidth()) {
                SwitchRow("Erinnerungen", "Wenn dein Haustier Hunger hat, krank ist oder dich vermisst", s.notifications) { on ->
                    update(s.copy(notifications = on))
                    if (on && Build.VERSION.SDK_INT >= 33 && !Notifier.hasPermission(context)) {
                        notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    }
                }
                SwitchRow("Nachtruhe", "Keine Erinnerungen zwischen ${s.quietStart} und ${s.quietEnd} Uhr", s.quietHours) {
                    update(s.copy(quietHours = it))
                }
            }

            SectionTitle("Gassi gehen", "👟")
            KCard(Modifier.fillMaxWidth()) {
                Text("Tägliches Schrittziel: ${TimeUtil.formatNumber(goal.roundToInt())}", style = MaterialTheme.typography.titleSmall)
                Slider(
                    value = goal,
                    onValueChange = { goal = (it / 500f).roundToInt() * 500f },
                    onValueChangeFinished = { update(s.copy(stepGoal = goal.roundToInt())) },
                    valueRange = 2000f..15000f,
                    steps = 25,
                )
            }

            SectionTitle("Bedienung", "🧩")
            KCard(Modifier.fillMaxWidth()) {
                SwitchRow("Haptisches Feedback", "Kleine Vibrationen beim Tippen", s.haptics) { update(s.copy(haptics = it)) }
            }

            val pet = state.pet
            if (pet != null) {
                SectionTitle("Dein Haustier", "🐾")
                KCard(Modifier.fillMaxWidth()) {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it.take(16) },
                        label = { Text("Name") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(10.dp))
                    KButton(
                        "Namen speichern",
                        {
                            GameRepository.perform(Action.Rename(name))
                            GameRepository.message("Hallo, ${name.trim()}! 👋")
                        },
                        enabled = name.isNotBlank() && name.trim() != pet.name,
                        small = true,
                        emoji = "✏️",
                    )
                    Spacer(Modifier.height(14.dp))
                    Text(
                        "Du möchtest nochmal von vorne beginnen? ${pet.name} bekommt einen Platz im Sternenhimmel und ein neues Ei wartet auf dich. Münzen und Erfolge bleiben erhalten.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(8.dp))
                    KButton(
                        "Neues Ei beginnen",
                        { confirmNewEgg = true },
                        small = true,
                        emoji = "🥚",
                        container = MaterialTheme.colorScheme.secondaryContainer,
                        content = MaterialTheme.colorScheme.onSecondaryContainer,
                    )
                }
            }

            SectionTitle("Daten", "💾")
            KCard(Modifier.fillMaxWidth()) {
                Text(
                    "Setzt das komplette Spiel zurück: Haustier, Münzen, Erfolge und Einstellungen.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
                KButton(
                    "Alles zurücksetzen",
                    { confirmReset = true },
                    small = true,
                    emoji = "🗑️",
                    container = MaterialTheme.colorScheme.errorContainer,
                    content = MaterialTheme.colorScheme.onErrorContainer,
                )
            }

            Spacer(Modifier.height(20.dp))
            Text(
                "Knuffi 1.0 · Mit viel Liebe gemacht 💕\nSchriften: Fredoka, VT323 und Press Start 2P (SIL Open Font License)",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(24.dp))
        }
    }

    if (confirmClassic) {
        ConfirmDialog(
            title = "Klassischer Modus?",
            text = "Werte sinken schneller, und wenn ${state.pet?.name ?: "dein Haustier"} zu lange vernachlässigt wird, reist es zu den Sternen. Bereit für die Herausforderung?",
            confirm = "Ja, klassisch!",
            onConfirm = {
                GameRepository.perform(Action.SetDifficulty(Difficulty.CLASSIC))
                confirmClassic = false
            },
            onDismiss = { confirmClassic = false },
        )
    }
    if (confirmNewEgg) {
        ConfirmDialog(
            title = "Neues Ei beginnen?",
            text = "${state.pet?.name} zieht in den Sternenhimmel. Das kann nicht rückgängig gemacht werden.",
            confirm = "Neues Ei",
            onConfirm = {
                GameRepository.perform(Action.NewEgg(OnboardingNames.random()))
                confirmNewEgg = false
                onBack()
            },
            onDismiss = { confirmNewEgg = false },
        )
    }
    if (confirmReset) {
        ConfirmDialog(
            title = "Wirklich alles löschen?",
            text = "Dein Haustier, alle Münzen, Erfolge und Einstellungen gehen verloren.",
            confirm = "Alles löschen",
            onConfirm = {
                confirmReset = false
                GameRepository.reset()
            },
            onDismiss = { confirmReset = false },
        )
    }
}

@Composable
fun ConfirmDialog(title: String, text: String, confirm: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = onConfirm) { Text(confirm) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Abbrechen") } },
        title = { Text(title) },
        text = { Text(text) },
        shape = LocalTokens.current.cardShape,
    )
}

@Composable
private fun SwitchRow(title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 6.dp)) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.width(12.dp))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
fun ChoiceCard(emoji: String, title: String, text: String, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val tokens = LocalTokens.current
    KCard(
        modifier
            .fillMaxWidth()
            .then(if (selected) Modifier.border(3.dp, MaterialTheme.colorScheme.primary, tokens.cardShape) else Modifier),
        onClick = onClick,
        color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(emoji, style = MaterialTheme.typography.headlineMedium)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (selected) Text("✓", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable
fun StyleOption(state: GameState, style: VisualStyle, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val tokens = LocalTokens.current
    val look = (PetLook.of(state)?.takeIf { it.form != Form.EGG } ?: PetLook(Form.BABY, Mood.HAPPY)).copy(sleeping = false, mood = Mood.HAPPY)
    KCard(
        modifier.then(if (selected) Modifier.border(3.dp, MaterialTheme.colorScheme.primary, tokens.cardShape) else Modifier),
        onClick = onClick,
        color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
        contentPadding = androidx.compose.foundation.layout.PaddingValues(8.dp),
    ) {
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            PetPortrait(look, style, Modifier.size(78.dp))
        }
        Text(style.title, style = MaterialTheme.typography.titleSmall, modifier = Modifier.align(Alignment.CenterHorizontally))
        Text(
            style.subtitle,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
