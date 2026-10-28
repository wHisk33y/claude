package de.knuffi.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.knuffi.app.data.GameRepository
import de.knuffi.app.render.PetLook
import de.knuffi.app.ui.components.ClayTextButton
import de.knuffi.app.ui.theme.LocalPalette
import de.knuffi.core.Action
import de.knuffi.core.Friends
import de.knuffi.core.GameState
import de.knuffi.core.Lifespan
import kotlin.math.sin
import kotlin.random.Random

@Composable
fun MemorialScreen(state: GameState) {
    val pet = state.pet ?: return
    val p = LocalPalette.current
    var name by remember { mutableStateOf(OnboardingNames.random()) }
    val time = rememberFrameTime()
    val stars = remember { Random(3).let { r -> List(90) { Triple(r.nextFloat(), r.nextFloat(), r.nextFloat()) } } }
    val look = PetLook.of(state)?.copy(sleeping = true, sick = false, dirty = false, hat = "hat_halo")
    val days = Lifespan.days(pet, System.currentTimeMillis())
    val oldAge = state.memorials.lastOrNull()?.let { it.bornAt == pet.bornAt && it.oldAge } == true
    // who could move in now: pets from the Kuschelhaus (not on a trip) and waiting eggs
    val friends = state.resting.filter { !it.isEgg && state.onTrip(it.id) == null }
        .sortedByDescending { state.friendship[Friends.key(pet.id, it.id)] ?: 0 }
    val eggs = state.eggs.filterValues { it > 0 }.keys.toList()
    val familyEggs = state.familyEggs

    Box(
        Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(Color(0xFF070B26), Color(0xFF221A55), Color(0xFF4D3B86)))),
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val t = time.floatValue
            for ((x, y, k) in stars) {
                val a = (sin(t * (1f + k * 2f) + k * 10f) + 1f) / 2f
                drawCircle(Color.White.copy(alpha = 0.2f + 0.8f * a), radius = 1.2f + k * 2.6f, center = Offset(x * size.width, y * size.height))
            }
            // aurora
            val c = Offset(size.width / 2f, size.height * 0.22f)
            drawCircle(Brush.radialGradient(listOf(Color(0x55FFE8A0), Color.Transparent), center = c, radius = size.width * 0.5f), radius = size.width * 0.5f, center = c)
            // shooting star
            val sp = (t % 6f) / 1.2f
            if (sp < 1f) {
                val sx = size.width * (0.9f - sp * 0.7f)
                val sy = size.height * (0.08f + sp * 0.2f)
                drawLine(
                    Brush.linearGradient(listOf(Color.White, Color.Transparent), start = Offset(sx, sy), end = Offset(sx + 120f, sy - 34f)),
                    Offset(sx, sy), Offset(sx + 120f, sy - 34f), strokeWidth = 3f,
                )
            }
        }
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .statusBarsPadding()
                .navigationBarsPadding()
                .imePadding()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(12.dp))
            PetPortrait(look, Modifier.size(170.dp), shadow = false) { pose, t ->
                pose.lying = 0f
                pose.lift = 0.35f + sin(t * 1.2f) * 0.1f
                pose.glow = 1f
                pose.eyes = de.knuffi.app.render.EyeShape.CLOSED
                pose.mouth = de.knuffi.app.render.MouthShape.SMILE
            }
            Spacer(Modifier.height(10.dp))
            Text("${pet.name} ist zu den Sternen gereist 🌟", style = MaterialTheme.typography.headlineSmall, color = Color.White, textAlign = TextAlign.Center)
            Spacer(Modifier.height(6.dp))
            Text(
                "${pet.form.title} · Level ${pet.level} · $days ${if (days == 1L) "Tag" else "Tage"}",
                style = MaterialTheme.typography.bodyLarge, color = p.gold,
            )
            Spacer(Modifier.height(10.dp))
            Text(
                if (oldAge) "Nach einem langen, glücklichen Leben leuchtet ${pet.name} jetzt am Sternenhimmel. Danke für die schöne Zeit! Deine Münzen, Erfolge und alle anderen Haustiere bleiben erhalten."
                else "Im klassischen Modus brauchen Haustiere viel Aufmerksamkeit. ${pet.name} leuchtet jetzt am Sternenhimmel. Deine Münzen und Erfolge bleiben erhalten.",
                style = MaterialTheme.typography.bodyMedium, color = Color.White.copy(alpha = 0.85f), textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(20.dp))

            if (friends.isNotEmpty()) {
                Text("Wer zieht jetzt zu dir?", style = MaterialTheme.typography.titleMedium, color = Color.White, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(8.dp))
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    for ((i, f) in friends.withIndex()) {
                        val friendLook = remember(f.id, state.equipped) { PetLook.of(f, state) }
                        Column(
                            Modifier
                                .width(110.dp)
                                .clip(RoundedCornerShape(20.dp))
                                .background(Color.White.copy(alpha = if (i == 0) 0.2f else 0.1f))
                                .then(if (i == 0) Modifier.border(2.dp, p.gold, RoundedCornerShape(20.dp)) else Modifier)
                                .clickable { GameRepository.perform(Action.SwitchPet(f.id)) }
                                .padding(10.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            PetPortrait(friendLook, Modifier.size(70.dp), sizeFactor = 0.85f, shadow = false)
                            Text(f.name, style = MaterialTheme.typography.titleSmall, color = Color.White, maxLines = 1)
                            Text("Level ${f.level}", style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.75f))
                            if (i == 0 && (state.friendship[Friends.key(pet.id, f.id)] ?: 0) > 0) {
                                Text("💞 bester Freund", style = MaterialTheme.typography.labelSmall, color = p.gold, maxLines = 1)
                            }
                        }
                    }
                }
                Spacer(Modifier.height(18.dp))
            }

            OutlinedTextField(
                value = name,
                onValueChange = { name = it.take(16) },
                label = { Text("Name für ein neues Haustier") },
                singleLine = true,
                shape = RoundedCornerShape(18.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    focusedLabelColor = Color.White,
                    unfocusedLabelColor = Color.White.copy(alpha = 0.7f),
                    focusedBorderColor = Color.White,
                    unfocusedBorderColor = Color.White.copy(alpha = 0.5f),
                    cursorColor = Color.White,
                ),
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(12.dp))
            if (eggs.isNotEmpty() || familyEggs.isNotEmpty()) {
                Text("Oder ein Ei ausbrüten, das schon wartet:", style = MaterialTheme.typography.bodyMedium, color = Color.White.copy(alpha = 0.85f), modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(8.dp))
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    val newName = name.ifBlank { OnboardingNames.random() }
                    for (line in eggs) {
                        EggChip(line.emoji, line.title, state.eggCount(line)) { GameRepository.perform(Action.HatchEgg(line, newName)) }
                    }
                    for (egg in familyEggs) {
                        EggChip("💕", egg.title, 1) { GameRepository.perform(Action.HatchFamilyEgg(egg.id, newName)) }
                    }
                }
                Spacer(Modifier.height(12.dp))
            }
            ClayTextButton(
                "Neues Ei ausbrüten",
                { GameRepository.perform(Action.NewEgg(name.ifBlank { OnboardingNames.random() })) },
                emoji = "🥚",
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(20.dp))
        }
    }
}

@Composable
private fun EggChip(emoji: String, title: String, count: Int, onClick: () -> Unit) {
    Row(
        Modifier
            .clip(RoundedCornerShape(50))
            .background(Color.White.copy(alpha = 0.14f))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(emoji, fontSize = 20.sp)
        Spacer(Modifier.width(6.dp))
        Text(if (count > 1) "$title ×$count" else title, style = MaterialTheme.typography.labelLarge, color = Color.White, maxLines = 1)
    }
}
