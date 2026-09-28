package de.knuffi.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import de.knuffi.app.data.GameRepository
import de.knuffi.app.render.PetLook
import de.knuffi.app.ui.components.ClayTextButton
import de.knuffi.app.ui.theme.LocalPalette
import de.knuffi.core.Action
import de.knuffi.core.GameState
import kotlin.math.sin
import kotlin.random.Random

@Composable
fun MemorialScreen(state: GameState) {
    val pet = state.pet ?: return
    val p = LocalPalette.current
    var name by remember { mutableStateOf(OnboardingNames.random()) }
    val time = rememberFrameTime()
    val stars = remember { Random(3).let { r -> List(90) { Triple(r.nextFloat(), r.nextFloat(), r.nextFloat()) } } }
    val look = PetLook.of(state)?.copy(sleeping = true, sick = false, dirty = false)
    val days = ((System.currentTimeMillis() - (if (pet.hatchedAt > 0) pet.hatchedAt else pet.bornAt)) / 86_400_000L).coerceAtLeast(0)

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
            val c = Offset(size.width / 2f, size.height * 0.33f)
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
                .statusBarsPadding()
                .navigationBarsPadding()
                .imePadding()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.weight(1f))
            PetPortrait(look, Modifier.size(200.dp), shadow = false) { pose, t ->
                pose.lying = 0f
                pose.lift = 0.35f + sin(t * 1.2f) * 0.1f
                pose.glow = 1f
                pose.eyes = de.knuffi.app.render.EyeShape.CLOSED
                pose.mouth = de.knuffi.app.render.MouthShape.SMILE
            }
            Spacer(Modifier.height(14.dp))
            Text(
                "${pet.name} ist zu den Sternen gereist 🌟",
                style = MaterialTheme.typography.headlineSmall,
                color = Color.White,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                "${pet.form.title} · Level ${pet.level} · $days ${if (days == 1L) "Tag" else "Tage"}",
                style = MaterialTheme.typography.bodyLarge,
                color = p.gold,
            )
            Spacer(Modifier.height(12.dp))
            Text(
                "Im klassischen Modus brauchen Haustiere viel Aufmerksamkeit. ${pet.name} leuchtet jetzt am Sternenhimmel, und ein neues Ei wartet schon auf dich. Deine Münzen und Erfolge bleiben erhalten.",
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White.copy(alpha = 0.85f),
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.weight(1f))
            OutlinedTextField(
                value = name,
                onValueChange = { name = it.take(16) },
                label = { Text("Name für dein neues Haustier") },
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
            Spacer(Modifier.height(14.dp))
            ClayTextButton(
                "Neues Ei ausbrüten",
                { GameRepository.perform(Action.NewEgg(name.ifBlank { OnboardingNames.random() })) },
                emoji = "🥚",
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}
