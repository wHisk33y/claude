package de.knuffi.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.knuffi.app.data.GameRepository
import de.knuffi.app.render.EyeShape
import de.knuffi.app.render.MouthShape
import de.knuffi.app.render.PetLook
import de.knuffi.app.sound.Sfx
import de.knuffi.app.sound.Sounds
import de.knuffi.app.ui.components.ClayTextButton
import de.knuffi.app.ui.theme.LocalPalette
import de.knuffi.core.Action
import de.knuffi.core.GameState
import de.knuffi.core.Lifespan
import de.knuffi.core.Mood
import kotlinx.coroutines.delay
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

private const val ASCEND_SECONDS = 6f

/**
 * The goodbye of an old pet: a last moment together (diary, cuddles), then it rises with
 * wings and a halo into the evening sky and becomes a star.
 */
@Composable
fun FarewellScreen(state: GameState) {
    val pet = state.pet ?: return
    val p = LocalPalette.current
    val time = rememberFrameTime()
    val t = time.floatValue
    var phase by remember { mutableIntStateOf(0) }
    var cuddleAt by remember { mutableFloatStateOf(-10f) }
    var cuddles by remember { mutableIntStateOf(0) }
    var ascendAt by remember { mutableFloatStateOf(-1f) }
    val stars = remember { Random(11).let { r -> List(110) { Triple(r.nextFloat(), r.nextFloat() * 0.75f, r.nextFloat()) } } }
    val look = remember(state.pet, state.equipped) {
        PetLook.of(state)?.copy(sleeping = false, sick = false, dirty = false, tired = false, hungry = false, mood = Mood.HAPPY)
    }
    val angel = remember(look) { look?.copy(hat = "hat_halo", back = "back_angelwings") }
    val now = remember { System.currentTimeMillis() }
    val days = Lifespan.days(pet, now)
    val entry = state.diary.lastOrNull { it.emoji == "🌟" }?.text ?: Lifespan.farewellText(pet, now)
    val progress = if (phase == 1 && ascendAt >= 0f) ((t - ascendAt) / ASCEND_SECONDS).coerceIn(0f, 1f) else 0f

    LaunchedEffect(phase) {
        if (phase == 1) {
            Sounds.play(Sfx.SPARKLE)
            delay((ASCEND_SECONDS * 1000).toLong() + 2500L)
            GameRepository.perform(Action.SayGoodbye)
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(Color(0xFF0B1033), Color(0xFF2B2363), Color(0xFF6A4A8E), Color(0xFFE79A86)))),
    ) {
        Canvas(Modifier.fillMaxSize()) {
            for ((x, y, k) in stars) {
                val a = (sin(t * (0.8f + k * 1.6f) + k * 9f) + 1f) / 2f
                drawCircle(Color.White.copy(alpha = 0.15f + 0.7f * a), radius = 1f + k * 2.4f, center = Offset(x * size.width, y * size.height))
            }
            // the new star appears where the pet went
            if (progress > 0.75f) {
                val k = ((progress - 0.75f) / 0.25f).coerceIn(0f, 1f)
                val c = Offset(size.width / 2f, size.height * 0.16f)
                val pulse = 1f + 0.15f * sin(t * 4f)
                drawCircle(Brush.radialGradient(listOf(Color(0xCCFFF4C2), Color.Transparent), center = c, radius = 110f * k * pulse), radius = 110f * k * pulse, center = c)
                val star = Path()
                val r1 = 26f * k * pulse
                val r2 = r1 * 0.42f
                for (i in 0 until 10) {
                    val r = if (i % 2 == 0) r1 else r2
                    val ang = -PI.toFloat() / 2f + i * PI.toFloat() / 5f
                    val px = c.x + cos(ang) * r
                    val py = c.y + sin(ang) * r
                    if (i == 0) star.moveTo(px, py) else star.lineTo(px, py)
                }
                star.close()
                drawPath(star, Color(0xFFFFF4C2))
            }
            // a trail of sparkles while it rises
            if (phase == 1) {
                val rnd = Random(7)
                for (i in 0 until 40) {
                    val f = rnd.nextFloat()
                    if (f > progress) continue
                    val y = size.height * (0.62f - 0.46f * f)
                    val x = size.width / 2f + sin(f * 18f + t * 2f) * 40f * (1f - f)
                    val a = (1f - (progress - f) * 2.5f).coerceIn(0f, 1f)
                    drawCircle(Color(0xFFFFF4C2).copy(alpha = a), radius = 3f + 4f * rnd.nextFloat(), center = Offset(x, y))
                }
            }
        }

        if (phase == 0) {
            Column(
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .statusBarsPadding()
                    .navigationBarsPadding()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Spacer(Modifier.height(24.dp))
                Box(contentAlignment = Alignment.Center) {
                    PetPortrait(look, Modifier.size(200.dp), shadow = false) { pose, tt ->
                        val since = tt - cuddleAt
                        pose.eyes = if (since < 2f) EyeShape.HAPPY else EyeShape.OPEN
                        pose.eyeOpen = 0.85f
                        pose.mouth = MouthShape.SMILE
                        pose.blush = if (since < 2f) 1f else 0.6f
                        pose.lookY = -0.3f
                        pose.tilt = sin(tt * 0.9f) * 3f
                    }
                    // hearts after a cuddle
                    val since = t - cuddleAt
                    if (since in 0f..2f) {
                        for (i in 0 until 6) {
                            val a = i * 1.05f
                            Text(
                                "💕",
                                fontSize = 22.sp,
                                modifier = Modifier
                                    .offset(x = (cos(a) * 70f).dp, y = (sin(a) * 50f - since * 40f).dp)
                                    .graphicsLayer { alpha = 1f - since / 2f },
                            )
                        }
                    }
                }
                Spacer(Modifier.height(10.dp))
                Text("${pet.name} ist alt geworden", style = MaterialTheme.typography.headlineSmall, color = Color.White, textAlign = TextAlign.Center)
                Spacer(Modifier.height(4.dp))
                Text(
                    "${pet.form.title} · Level ${pet.level} · $days ${if (days == 1L) "Tag" else "Tage"} zusammen",
                    style = MaterialTheme.typography.bodyLarge, color = p.gold, textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(16.dp))
                Column(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(22.dp))
                        .background(Color.White.copy(alpha = 0.12f))
                        .padding(16.dp),
                ) {
                    Text("📔 Ein letzter Tagebucheintrag", style = MaterialTheme.typography.titleSmall, color = Color.White)
                    Spacer(Modifier.height(6.dp))
                    Text(entry, style = MaterialTheme.typography.bodyLarge, color = Color.White.copy(alpha = 0.92f))
                }
                Spacer(Modifier.height(18.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                    ClayTextButton(
                        if (cuddles == 0) "Noch einmal kuscheln" else "Kuscheln",
                        {
                            cuddleAt = t
                            cuddles++
                            Sounds.play(Sfx.CHIRP)
                        },
                        Modifier.weight(1f), emoji = "💕", small = true, color = p.pink, deep = p.pinkDeep,
                    )
                }
                Spacer(Modifier.height(10.dp))
                ClayTextButton(
                    "Auf Wiedersehen sagen",
                    {
                        ascendAt = t
                        phase = 1
                    },
                    Modifier.fillMaxWidth(), emoji = "🌟", color = p.gold, deep = p.goldDeep,
                )
                Spacer(Modifier.height(10.dp))
                Text(
                    "Nimm dir ruhig Zeit. ${pet.name} wartet, bis du bereit bist.",
                    style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.7f), textAlign = TextAlign.Center,
                )
            }
        } else {
            // the pet rises with wings and a halo and becomes small and bright
            val e = progress * progress * (3f - 2f * progress)
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                PetPortrait(
                    angel,
                    Modifier
                        .size(200.dp)
                        .offset(y = (-330f * e).dp)
                        .graphicsLayer {
                            val s = 1f - 0.88f * e
                            scaleX = s
                            scaleY = s
                            alpha = 1f - ((progress - 0.82f) / 0.18f).coerceIn(0f, 1f)
                        },
                    shadow = false,
                ) { pose, tt ->
                    pose.eyes = EyeShape.CLOSED
                    pose.mouth = MouthShape.SMILE
                    pose.glow = min(1f, 0.3f + progress * 1.2f)
                    pose.armL = 0.4f + 0.2f * sin(tt * 2f)
                    pose.armR = 0.4f + 0.2f * sin(tt * 2f)
                }
                val textAlpha = ((progress - 0.55f) / 0.3f).coerceIn(0f, 1f)
                Column(
                    Modifier
                        .align(Alignment.BottomCenter)
                        .navigationBarsPadding()
                        .padding(32.dp)
                        .graphicsLayer { alpha = textAlpha },
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text("${pet.name} ist jetzt ein Stern ✨", style = MaterialTheme.typography.headlineSmall, color = Color.White, textAlign = TextAlign.Center)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Wenn du nachts in den Himmel schaust, funkelt es dir zu.",
                        style = MaterialTheme.typography.bodyLarge, color = Color.White.copy(alpha = 0.85f), textAlign = TextAlign.Center,
                    )
                }
            }
        }
    }
}
