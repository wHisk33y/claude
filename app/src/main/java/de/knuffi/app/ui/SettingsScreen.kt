package de.knuffi.app.ui

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import de.knuffi.app.BuildConfig
import de.knuffi.app.data.GameRepository
import de.knuffi.app.notify.Battery
import de.knuffi.app.notify.Notifier
import de.knuffi.app.render.LayoutKind
import de.knuffi.app.screen.PetOverlayService
import de.knuffi.app.screen.ScreenPet
import de.knuffi.app.ui.components.ClayTextButton
import de.knuffi.app.ui.components.EmojiTile
import de.knuffi.app.ui.components.PageHeader
import de.knuffi.app.ui.components.Pill
import de.knuffi.app.ui.components.SectionHeader
import de.knuffi.app.ui.components.SegmentedControl
import de.knuffi.app.ui.components.SurfaceCard
import de.knuffi.app.ui.theme.LocalPalette
import de.knuffi.core.Action
import de.knuffi.core.CareTuning
import de.knuffi.core.Difficulty
import de.knuffi.core.GameState
import de.knuffi.core.Settings
import de.knuffi.core.LookStyle
import de.knuffi.core.ThemeMode
import de.knuffi.core.TimeUtil
import kotlin.math.roundToInt

private val themeOrder = listOf(ThemeMode.SYSTEM, ThemeMode.LIGHT, ThemeMode.DARK)

@Composable
fun SettingsScreen(state: GameState, onBack: () -> Unit) {
    val context = LocalContext.current
    val p = LocalPalette.current
    var confirmReset by remember { mutableStateOf(false) }
    var confirmClassic by remember { mutableStateOf(false) }
    var confirmNewEgg by remember { mutableStateOf(false) }
    var name by remember { mutableStateOf(state.pet?.name ?: "") }
    val s = state.settings
    var goal by remember { mutableFloatStateOf(s.stepGoal.toFloat()) }
    val notifLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    var wallpaperActive by remember { mutableStateOf(ScreenPet.isWallpaperActive(context)) }
    var batteryOk by remember { mutableStateOf(Battery.isIgnoring(context)) }
    var waitingForOverlayPermission by remember { mutableStateOf(false) }
    fun update(settings: Settings) {
        GameRepository.perform(Action.UpdateSettings(settings))
    }

    LifecycleResumeEffect(Unit) {
        wallpaperActive = ScreenPet.isWallpaperActive(context)
        batteryOk = Battery.isIgnoring(context)
        if (waitingForOverlayPermission) {
            waitingForOverlayPermission = false
            if (ScreenPet.canDrawOverlays(context)) {
                update(GameRepository.state.value.settings.copy(overlayPet = true))
                PetOverlayService.start(context)
            } else {
                GameRepository.message("Ohne die Berechtigung kann ${state.pet?.name ?: "dein Haustier"} nicht über anderen Apps erscheinen.")
            }
        }
        onPauseOrDispose { }
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
            SectionHeader("Darstellung", "🎨")
            SurfaceCard(Modifier.fillMaxWidth()) {
                Text("Farbschema", style = MaterialTheme.typography.titleSmall, color = p.text)
                Spacer(Modifier.height(10.dp))
                SegmentedControl(
                    themeOrder.map { it.title },
                    themeOrder.indexOf(s.themeMode).coerceAtLeast(0),
                    { update(s.copy(themeMode = themeOrder[it])) },
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "„System“ folgt dem Dunkelmodus deines Handys.",
                    style = MaterialTheme.typography.bodySmall,
                    color = p.textMuted,
                )
                Spacer(Modifier.height(16.dp))
                Text("Look", style = MaterialTheme.typography.titleSmall, color = p.text)
                Spacer(Modifier.height(10.dp))
                SegmentedControl(
                    listOf("✨ Zauber", "🐉 Abenteuer"),
                    if (s.look == LookStyle.ABENTEUER) 1 else 0,
                    { update(s.copy(look = if (it == 1) LookStyle.ABENTEUER else LookStyle.ZAUBER)) },
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "Zauber: Rosa und Flieder. Abenteuer: Blau und Grün. Alle Haustiere, Kleider und Möbel gibt es in beiden Looks.",
                    style = MaterialTheme.typography.bodySmall,
                    color = p.textMuted,
                )
            }

            SectionHeader("${state.pet?.name ?: "Knuffi"} auf dem Bildschirm", "📱")
            SurfaceCard(Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier
                            .size(width = 84.dp, height = 150.dp)
                            .shadow(8.dp, RoundedCornerShape(18.dp))
                            .clip(RoundedCornerShape(18.dp))
                            .border(3.dp, if (p.dark) Color(0xFF3A3160) else Color(0xFF2A2340), RoundedCornerShape(18.dp)),
                    ) {
                        PetScene(state, Modifier.fillMaxSize(), kind = LayoutKind.WALLPAPER, interactive = false, showBubble = false)
                    }
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Live-Hintergrund", style = MaterialTheme.typography.titleMedium, color = p.text)
                        Text(
                            "Dein Haustier lebt auf deinem Startbildschirm: mit Tag und Nacht, Wetter und Stimmung. Antippen zum Streicheln.",
                            style = MaterialTheme.typography.bodySmall,
                            color = p.textMuted,
                        )
                        Spacer(Modifier.height(10.dp))
                        if (wallpaperActive) {
                            Pill("✓ Aktiv", color = p.mint.copy(alpha = 0.25f))
                        } else {
                            ClayTextButton(
                                "Festlegen",
                                { if (!ScreenPet.openWallpaperPicker(context)) GameRepository.message("Live-Hintergründe werden auf diesem Gerät nicht unterstützt.") },
                                emoji = "🖼️",
                                small = true,
                                color = p.violet,
                                deep = p.violetDeep,
                            )
                        }
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
            SurfaceCard(Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    EmojiTile("🐾", size = 48.dp, color = p.pink)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Schwebendes Haustier", style = MaterialTheme.typography.titleMedium, color = p.text)
                        Text(
                            "Läuft auch über anderen Apps herum. Tippen = streicheln, ziehen = tragen, lange drücken = App öffnen.",
                            style = MaterialTheme.typography.bodySmall,
                            color = p.textMuted,
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                    KSwitch(s.overlayPet) { on ->
                        if (on) {
                            if (ScreenPet.canDrawOverlays(context)) {
                                update(s.copy(overlayPet = true))
                                PetOverlayService.start(context)
                            } else {
                                waitingForOverlayPermission = true
                                ScreenPet.requestOverlayPermission(context)
                            }
                        } else {
                            update(s.copy(overlayPet = false))
                            PetOverlayService.stop(context)
                        }
                    }
                }
            }

            SectionHeader("Schwierigkeit", "⚔️")
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

            SectionHeader("Pflegebedarf", "🎚️")
            SurfaceCard(Modifier.fillMaxWidth()) {
                LevelSlider(
                    title = "Wie viel Pflege braucht ${state.pet?.name ?: "dein Haustier"}?",
                    level = s.careLevel,
                    labels = CareTuning.careTitles,
                    describe = ::careDescription,
                ) { update(GameRepository.state.value.settings.copy(careLevel = it)) }
                Spacer(Modifier.height(14.dp))
                LevelSlider(
                    title = "Wie viel schläft es?",
                    level = s.sleepLevel,
                    labels = CareTuning.sleepTitles,
                    describe = ::sleepDescription,
                ) { update(GameRepository.state.value.settings.copy(sleepLevel = it)) }
                Spacer(Modifier.height(8.dp))
                Text(
                    "Gilt für alle deine Haustiere und lässt sich jederzeit ändern.",
                    style = MaterialTheme.typography.bodySmall,
                    color = p.textMuted,
                )
            }

            SectionHeader("Benachrichtigungen", "🔔")
            SurfaceCard(Modifier.fillMaxWidth()) {
                SwitchRow("Erinnerungen", "Wenn dein Haustier Hunger hat, krank ist oder dich vermisst", s.notifications) { on ->
                    update(s.copy(notifications = on))
                    if (on && Build.VERSION.SDK_INT >= 33 && !Notifier.hasPermission(context)) {
                        notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    }
                }
                SwitchRow("Nachtruhe", "Keine Erinnerungen zwischen ${s.quietStart} und ${s.quietEnd} Uhr", s.quietHours) {
                    update(s.copy(quietHours = it))
                }
                Spacer(Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Akku-Optimierung", style = MaterialTheme.typography.titleSmall, color = p.text)
                        Text(
                            if (batteryOk) "Ausnahme aktiv: Erinnerungen kommen pünktlich. ✅" else "Manche Handys verzögern Erinnerungen, um Akku zu sparen.",
                            style = MaterialTheme.typography.bodySmall,
                            color = p.textMuted,
                        )
                    }
                    if (!batteryOk) {
                        Spacer(Modifier.width(8.dp))
                        ClayTextButton("Erlauben", { Battery.request(context) }, small = true, color = p.mint, deep = p.mintDeep)
                    }
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    "Du bekommst Bescheid bei Hunger, Krankheit, Schmutz, Langeweile und Müdigkeit, wenn Pflanzen reif sind, ein Ausflug zurück ist, ein Fest beginnt und an Geburtstagen.",
                    style = MaterialTheme.typography.bodySmall,
                    color = p.textMuted,
                )
            }

            SectionHeader("Gassi gehen", "👟")
            SurfaceCard(Modifier.fillMaxWidth()) {
                Text("Tägliches Schrittziel: ${TimeUtil.formatNumber(goal.roundToInt())}", style = MaterialTheme.typography.titleSmall, color = p.text)
                Slider(
                    value = goal,
                    onValueChange = { goal = (it / 500f).roundToInt() * 500f },
                    onValueChangeFinished = { update(s.copy(stepGoal = goal.roundToInt())) },
                    valueRange = 2000f..15000f,
                    steps = 25,
                    colors = SliderDefaults.colors(
                        thumbColor = p.pink,
                        activeTrackColor = p.pink,
                        inactiveTrackColor = p.track,
                        activeTickColor = Color.White.copy(alpha = 0.5f),
                        inactiveTickColor = p.textMuted.copy(alpha = 0.4f),
                    ),
                )
            }

            SectionHeader("Bedienung", "🧩")
            SurfaceCard(Modifier.fillMaxWidth()) {
                SwitchRow("Haptisches Feedback", "Kleine Vibrationen beim Tippen", s.haptics) { update(s.copy(haptics = it)) }
            }

            val pet = state.pet
            if (pet != null) {
                SectionHeader("Dein Haustier", "🐾")
                SurfaceCard(Modifier.fillMaxWidth()) {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it.take(16) },
                        label = { Text("Name") },
                        singleLine = true,
                        shape = RoundedCornerShape(16.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = p.pink,
                            focusedLabelColor = p.pink,
                            cursorColor = p.pink,
                        ),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(10.dp))
                    ClayTextButton(
                        "Namen speichern",
                        {
                            GameRepository.perform(Action.Rename(name))
                            GameRepository.message("Hallo, ${name.trim()}! 👋")
                        },
                        enabled = name.isNotBlank() && name.trim() != pet.name,
                        small = true,
                        emoji = "✏️",
                    )
                    Spacer(Modifier.height(16.dp))
                    Text(
                        "Nochmal von vorne? ${pet.name} bekommt einen Platz im Sternenhimmel und ein neues Ei wartet auf dich. Münzen und Erfolge bleiben erhalten.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = p.textMuted,
                    )
                    Spacer(Modifier.height(8.dp))
                    ClayTextButton("Neues Ei beginnen", { confirmNewEgg = true }, small = true, emoji = "🥚", color = p.violet, deep = p.violetDeep)
                }
            }

            SectionHeader("Daten", "💾")
            SurfaceCard(Modifier.fillMaxWidth()) {
                Text(
                    "Setzt das komplette Spiel zurück: Haustier, Münzen, Erfolge und Einstellungen.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = p.textMuted,
                )
                Spacer(Modifier.height(8.dp))
                ClayTextButton("Alles zurücksetzen", { confirmReset = true }, small = true, emoji = "🗑️", color = p.red, deep = p.redDeep)
            }

            Spacer(Modifier.height(24.dp))
            Text(
                "Knuffi ${BuildConfig.VERSION_NAME} · Mit viel Liebe gemacht 💕\nSchrift: Fredoka (SIL Open Font License)",
                style = MaterialTheme.typography.bodySmall,
                color = p.textMuted,
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
            danger = true,
            onConfirm = {
                confirmReset = false
                PetOverlayService.stop(context)
                GameRepository.reset()
            },
            onDismiss = { confirmReset = false },
        )
    }
}

@Composable
fun ConfirmDialog(title: String, text: String, confirm: String, onConfirm: () -> Unit, onDismiss: () -> Unit, danger: Boolean = false) {
    val p = LocalPalette.current
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            ClayTextButton(confirm, onConfirm, small = true, color = if (danger) p.red else p.pink, deep = if (danger) p.redDeep else p.pinkDeep)
        },
        dismissButton = {
            ClayTextButton("Abbrechen", onDismiss, small = true, color = Color(0xFF9A8FB0), deep = Color(0xFF6E6484))
        },
        title = { Text(title, color = p.text) },
        text = { Text(text, color = p.textMuted) },
        containerColor = p.surface,
        shape = RoundedCornerShape(28.dp),
    )
}

private fun hoursText(h: Double): String {
    val half = (h * 2).roundToInt() / 2.0
    val text = if (half % 1.0 == 0.0) "${half.toInt()}" else "${half.toInt()},5"
    return "$text Std"
}

private fun careDescription(level: Int): String = when (level) {
    0 -> "Hunger, Langeweile und Schmutz kommen nur halb so schnell. Gut, wenn du wenig Zeit hast."
    1 -> "Etwas gemütlicher als normal."
    2 -> "Die normale Mischung."
    3 -> "Dein Haustier braucht öfter Essen, Spiel und Pflege. Es gibt mehr zu tun!"
    else -> "Fast doppelt so viel zu tun wie normal. Für echte Tierprofis!"
}

private fun sleepDescription(level: Int): String =
    "Ein Schläfchen dauert etwa ${hoursText(CareTuning.napHours(level))}. " +
        "Danach bleibt es tagsüber etwa ${hoursText(CareTuning.awakeHours(level))} wach (abends wird es schneller müde)."

/** A five-step slider with the current level as a pill and a short explanation. */
@Composable
private fun LevelSlider(title: String, level: Int, labels: List<String>, describe: (Int) -> String, onChange: (Int) -> Unit) {
    val p = LocalPalette.current
    var value by remember(level) { mutableFloatStateOf(level.toFloat()) }
    val shown = value.roundToInt().coerceIn(CareTuning.MIN, CareTuning.MAX)
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(title, style = MaterialTheme.typography.titleSmall, color = p.text, modifier = Modifier.weight(1f))
        Spacer(Modifier.width(8.dp))
        Pill(labels[shown], color = p.pink.copy(alpha = 0.18f))
    }
    Slider(
        value = value,
        onValueChange = { value = it.roundToInt().toFloat() },
        onValueChangeFinished = { if (value.roundToInt() != level) onChange(value.roundToInt()) },
        valueRange = CareTuning.MIN.toFloat()..CareTuning.MAX.toFloat(),
        steps = CareTuning.MAX - CareTuning.MIN - 1,
        colors = SliderDefaults.colors(
            thumbColor = p.pink,
            activeTrackColor = p.pink,
            inactiveTrackColor = p.track,
            activeTickColor = Color.White.copy(alpha = 0.6f),
            inactiveTickColor = p.textMuted.copy(alpha = 0.4f),
        ),
    )
    Row {
        Text("weniger", style = MaterialTheme.typography.labelSmall, color = p.textMuted, modifier = Modifier.weight(1f))
        Text("mehr", style = MaterialTheme.typography.labelSmall, color = p.textMuted)
    }
    Spacer(Modifier.height(4.dp))
    Text(describe(shown), style = MaterialTheme.typography.bodySmall, color = p.textMuted)
}

@Composable
fun KSwitch(checked: Boolean, onChange: (Boolean) -> Unit) {
    val p = LocalPalette.current
    Switch(
        checked = checked,
        onCheckedChange = onChange,
        colors = SwitchDefaults.colors(
            checkedThumbColor = Color.White,
            checkedTrackColor = p.mint,
            checkedBorderColor = p.mintDeep,
            uncheckedThumbColor = if (p.dark) Color(0xFFB3A7CC) else Color.White,
            uncheckedTrackColor = p.track,
            uncheckedBorderColor = p.textMuted.copy(alpha = 0.4f),
        ),
    )
}

@Composable
private fun SwitchRow(title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    val p = LocalPalette.current
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 6.dp)) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall, color = p.text)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = p.textMuted)
        }
        Spacer(Modifier.width(12.dp))
        KSwitch(checked, onChange)
    }
}

@Composable
fun ChoiceCard(emoji: String, title: String, text: String, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val p = LocalPalette.current
    val shape = RoundedCornerShape(24.dp)
    SurfaceCard(
        modifier
            .fillMaxWidth()
            .then(if (selected) Modifier.border(2.5.dp, p.pink, shape) else Modifier),
        onClick = onClick,
        shape = shape,
        color = if (selected) (if (p.dark) Color(0xFF3A2550) else Color(0xFFFFEAF4)) else p.surface,
        contentPadding = PaddingValues(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Start) {
            EmojiTile(emoji, size = 46.dp, color = if (selected) p.pink else p.violet)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium, color = p.text)
                Text(text, style = MaterialTheme.typography.bodyMedium, color = p.textMuted)
            }
            if (selected) Text("✓", style = MaterialTheme.typography.titleLarge, color = p.pink)
        }
    }
}
