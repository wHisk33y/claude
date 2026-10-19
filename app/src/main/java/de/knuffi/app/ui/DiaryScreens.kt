package de.knuffi.app.ui

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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.knuffi.app.data.GameRepository
import de.knuffi.app.render.PetLook
import de.knuffi.app.ui.components.EmojiTile
import de.knuffi.app.ui.components.PageHeader
import de.knuffi.app.ui.components.SectionHeader
import de.knuffi.app.ui.components.SurfaceCard
import de.knuffi.app.ui.components.staggered
import de.knuffi.app.ui.theme.LocalPalette
import de.knuffi.core.Diary
import de.knuffi.core.GameState
import de.knuffi.core.Hidden
import de.knuffi.core.Secret
import de.knuffi.core.TimeUtil
import de.knuffi.core.Weather
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

private val dayFormat = DateTimeFormatter.ofPattern("EEEE, d. MMMM", Locale.GERMAN)

@Composable
fun DiaryScreen(state: GameState, onBack: () -> Unit) {
    val p = LocalPalette.current
    val pet = state.pet ?: return
    val now = System.currentTimeMillis()
    val today = TimeUtil.epochDay(now, GameRepository.zone)
    val look = remember(state.pet, state.equipped) { PetLook.of(state) }
    Column(Modifier.fillMaxSize()) {
        PageHeader("Tagebuch von ${pet.name}", onBack)
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).navigationBarsPadding().padding(horizontal = 16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                PetPortrait(look, Modifier.size(90.dp), sizeFactor = 0.8f) { pose, _ ->
                    pose.holding = "✏️"
                    pose.hold = 1f
                    pose.lookY = 0.7f
                }
                Spacer(Modifier.width(10.dp))
                Text(
                    "Jeden Abend schreibt ${pet.name} auf, was ihr zusammen erlebt habt. Ein neuer Eintrag erscheint am nächsten Tag.",
                    style = MaterialTheme.typography.bodySmall, color = p.textMuted, modifier = Modifier.weight(1f),
                )
            }
            SectionHeader("Heute bisher", "✏️")
            val preview = Diary.write(pet.name, pet.isCat, today, state.daily.log, pet.emotion(now), Weather.current(state, now, GameRepository.zone).kind)
            DiaryCard(preview.emoji, LocalDate.ofEpochDay(today).format(dayFormat), preview.text, faded = true)
            if (state.diary.isNotEmpty()) SectionHeader("Frühere Einträge", "📔")
            for ((i, e) in state.diary.reversed().withIndex()) {
                DiaryCard(e.emoji, LocalDate.ofEpochDay(e.day).format(dayFormat), e.text, modifier = Modifier.staggered(i))
                Spacer(Modifier.height(8.dp))
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun DiaryCard(emoji: String, date: String, text: String, faded: Boolean = false, modifier: Modifier = Modifier) {
    val p = LocalPalette.current
    SurfaceCard(modifier.fillMaxWidth().alpha(if (faded) 0.85f else 1f), color = if (p.dark) p.surfaceAlt else Color(0xFFFFFBEF), contentPadding = PaddingValues(14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(emoji, fontSize = 22.sp)
            Spacer(Modifier.width(8.dp))
            Text(date, style = MaterialTheme.typography.titleSmall, color = p.text)
        }
        Spacer(Modifier.height(6.dp))
        Text(text, style = MaterialTheme.typography.bodyLarge, color = p.text)
    }
}

@Composable
fun SecretsScreen(state: GameState, onBack: () -> Unit) {
    val p = LocalPalette.current
    val today = TimeUtil.epochDay(System.currentTimeMillis(), GameRepository.zone)
    val hidden = Hidden.today(today)
    Column(Modifier.fillMaxSize()) {
        PageHeader("Geheimnisse", onBack)
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).navigationBarsPadding().padding(horizontal = 16.dp)) {
            SurfaceCard(Modifier.fillMaxWidth(), color = if (p.dark) p.surfaceAlt else Color(0xFFFFF6E0)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    EmojiTile(if (state.daily.hiddenFound) hidden.emoji else "👀", size = 50.dp, color = if (state.daily.hiddenFound) p.mint else p.gold)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Tägliche Suche", style = MaterialTheme.typography.titleMedium, color = p.text)
                        Text(
                            if (state.daily.hiddenFound) "Heute schon gefunden: ${hidden.name}. Morgen ist etwas Neues versteckt!"
                            else "Im Zimmer ist heute ${hidden.name} versteckt. Schau genau hin und tippe drauf! Tipp: Frag im Chat „Wo ist das Versteck?“.",
                            style = MaterialTheme.typography.bodySmall, color = p.textMuted,
                        )
                        Text("Insgesamt gefunden: ${state.counters.hiddenFound}", style = MaterialTheme.typography.labelSmall, color = p.textMuted)
                    }
                }
            }
            SectionHeader("${state.secrets.size} von ${Secret.entries.size} entdeckt", "🗝️")
            for ((i, s) in Secret.entries.withIndex()) {
                val found = s.name in state.secrets
                SurfaceCard(Modifier.fillMaxWidth().staggered(i), contentPadding = PaddingValues(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        EmojiTile(if (found) s.emoji else "❔", size = 44.dp, color = if (found) p.mint else p.violet)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(if (found) s.title else "Geheimnis Nr. ${i + 1}", style = MaterialTheme.typography.titleSmall, color = p.text)
                            Text(if (found) "Entdeckt! +${s.coins} 🪙" else "Hinweis: ${s.hint}", style = MaterialTheme.typography.bodySmall, color = if (found) p.mintDeep else p.textMuted)
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}
