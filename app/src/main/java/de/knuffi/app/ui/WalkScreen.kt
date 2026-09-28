package de.knuffi.app.ui

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.knuffi.app.data.GameRepository
import de.knuffi.app.render.PetFrame
import de.knuffi.app.render.PetLook
import de.knuffi.app.render.PetMotion
import de.knuffi.app.render.renderMode
import de.knuffi.app.steps.StepTracker
import de.knuffi.app.ui.components.KButton
import de.knuffi.app.ui.components.KCard
import de.knuffi.app.ui.components.SectionTitle
import de.knuffi.app.ui.theme.LocalTokens
import de.knuffi.core.Action
import de.knuffi.core.GameState
import de.knuffi.core.Mood
import de.knuffi.core.StepRewards
import de.knuffi.core.TimeUtil
import de.knuffi.core.VisualStyle
import kotlin.math.abs
import kotlin.math.sin

@Composable
fun WalkScreen(state: GameState) {
    val context = LocalContext.current
    val tokens = LocalTokens.current
    var hasPermission by remember { mutableStateOf(StepTracker.hasPermission(context)) }
    val hasSensor = remember { StepTracker.hasSensor(context) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        hasPermission = granted
        if (granted) StepTracker.start(context)
    }
    val goal = state.settings.stepGoal
    val today = state.steps.today

    Column(
        Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
    ) {
        Text("Gassi gehen", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(top = 8.dp))
        Spacer(Modifier.height(10.dp))
        WalkScene(
            state,
            today,
            Modifier
                .fillMaxWidth()
                .height(170.dp)
                .clip(tokens.cardShape)
                .then(tokens.border?.let { Modifier.border(it, tokens.cardShape) } ?: Modifier),
        )
        Spacer(Modifier.height(16.dp))
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            StepRing(today, goal, Modifier.size(220.dp))
        }
        Spacer(Modifier.height(8.dp))

        if (!hasSensor) {
            KCard(Modifier.fillMaxWidth()) {
                Text("😕 Kein Schrittzähler gefunden", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Dein Gerät hat leider keinen Schrittzähler-Sensor. Alle anderen Funktionen kannst du natürlich trotzdem nutzen.",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        } else if (!hasPermission && Build.VERSION.SDK_INT >= 29) {
            KCard(Modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.primaryContainer) {
                Text("👟 Schritte zählen?", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onPrimaryContainer)
                Text(
                    "Erlaube den Zugriff auf deine körperlichen Aktivitäten. Dann wird ${state.pet?.name ?: "dein Haustier"} mit jedem echten Schritt fitter und glücklicher.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
                Spacer(Modifier.height(10.dp))
                KButton("Erlauben", { launcher.launch(Manifest.permission.ACTIVITY_RECOGNITION) }, emoji = "✅", modifier = Modifier.fillMaxWidth())
            }
        }

        SectionTitle("Schritt-Belohnungen", "🎯")
        for (row in StepRewards.tiers.withIndex().toList().chunked(2)) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                for ((i, tier) in row) {
                    val reached = today >= tier.first
                    val claimed = i in state.steps.claimedTiers
                    KCard(Modifier.weight(1f), color = if (reached && !claimed) tokens.gold.copy(alpha = 0.25f) else MaterialTheme.colorScheme.surface) {
                        Text(
                            "${TimeUtil.formatNumber(tier.first)} 👟",
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Text("+${tier.second} 🪙", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.height(8.dp))
                        when {
                            claimed -> Text("✅ Abgeholt", style = MaterialTheme.typography.labelLarge, color = tokens.health)
                            reached -> KButton("Holen", {
                                GameRepository.perform(Action.ClaimStepTier(i))
                                GameRepository.message("Schritt-Belohnung: +${tier.second} 🪙")
                            }, small = true, modifier = Modifier.fillMaxWidth())
                            else -> Text(
                                "noch ${TimeUtil.formatNumber(tier.first - today)}",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
        }

        SectionTitle("Deine Bilanz", "📈")
        KCard(Modifier.fillMaxWidth()) {
            Row {
                Column(Modifier.weight(1f)) {
                    Text("Gesamt", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(TimeUtil.formatNumber(state.counters.totalSteps), style = MaterialTheme.typography.titleLarge)
                }
                Column(Modifier.weight(1f)) {
                    Text("Bester Tag", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(TimeUtil.formatNumber(state.counters.bestDaySteps), style = MaterialTheme.typography.titleLarge)
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(
                "Je 1.000 Schritte: 😊 +4, ❤️ +2 und 10 XP. Aber es macht auch hungrig und müde!",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun StepRing(today: Int, goal: Int, modifier: Modifier = Modifier) {
    val tokens = LocalTokens.current
    val fraction by animateFloatAsState((today / goal.toFloat()).coerceIn(0f, 1f), tween(1200), label = "ring")
    val track = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)
    val colors = listOf(tokens.hygiene, tokens.health, tokens.energy, tokens.hygiene)
    Box(modifier, contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val sw = if (tokens.pixel) 18.dp.toPx() else 16.dp.toPx()
            val inset = sw / 2f
            val arc = Size(size.width - sw, size.height - sw)
            drawArc(track, 0f, 360f, false, Offset(inset, inset), arc, style = Stroke(sw))
            if (tokens.pixel) {
                val segments = 20
                val filled = (fraction * segments).toInt()
                for (i in 0 until segments) {
                    drawArc(
                        if (i < filled) tokens.health else track,
                        -90f + i * 18f + 1.5f,
                        15f,
                        false,
                        Offset(inset, inset),
                        arc,
                        style = Stroke(sw),
                    )
                }
            } else {
                drawArc(
                    Brush.sweepGradient(colors),
                    -90f,
                    360f * fraction,
                    false,
                    Offset(inset, inset),
                    arc,
                    style = Stroke(sw, cap = StrokeCap.Round),
                )
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("👟", fontSize = 26.sp)
            Text(TimeUtil.formatNumber(today), style = MaterialTheme.typography.headlineMedium)
            Text(
                "von ${TimeUtil.formatNumber(goal)} Schritten",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            if (today >= goal) Text("Ziel erreicht! 🎉", style = MaterialTheme.typography.labelLarge, color = tokens.health)
        }
    }
}

@Composable
private fun WalkScene(state: GameState, steps: Int, modifier: Modifier = Modifier) {
    val look = remember(state.pet, state.equipped) { PetLook.of(state)?.copy(sleeping = false, mood = Mood.HAPPY) }
    val stage = remember { PetStage(48) }
    val frame = remember { PetFrame() }
    val time = rememberFrameTime()
    var boostUntil by remember { mutableFloatStateOf(0f) }
    val motion = remember { FloatArray(2) } // [distance, lastT]
    LaunchedEffect(steps) { boostUntil = time.floatValue + 4f }
    val style = state.style
    val mode = style.renderMode
    val sky = when (style) {
        VisualStyle.KAWAII -> listOf(Color(0xFF9FD8FF), Color(0xFFFFE3F1))
        VisualStyle.PIXEL -> listOf(Color(0xFF2B2B6E), Color(0xFF6B4BA8))
        VisualStyle.MINIMAL -> listOf(MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.surface)
    }
    val far = when (style) {
        VisualStyle.KAWAII -> Color(0xFFB8E6C8)
        VisualStyle.PIXEL -> Color(0xFF3E7A4E)
        VisualStyle.MINIMAL -> MaterialTheme.colorScheme.secondaryContainer
    }
    val near = when (style) {
        VisualStyle.KAWAII -> Color(0xFF8ED9A5)
        VisualStyle.PIXEL -> Color(0xFF2F6B3A)
        VisualStyle.MINIMAL -> MaterialTheme.colorScheme.tertiaryContainer
    }
    val path = remember { Path() }
    Canvas(modifier) {
        val t = time.floatValue
        val dt = (t - motion[1]).coerceIn(0f, 0.1f)
        motion[1] = t
        val fast = t < boostUntil
        motion[0] += dt * (if (fast) 1f else 0.35f)
        val distance = motion[0]
        val w = size.width
        val h = size.height
        drawRect(Brush.verticalGradient(sky))
        drawCircle(Color(0xFFFFE066), radius = h * 0.1f, center = Offset(w * 0.82f, h * 0.22f))
        // clouds
        for (i in 0 until 3) {
            val x = ((i * 0.4f - distance * 0.05f) % 1.3f + 1.3f) % 1.3f * w - w * 0.15f
            val y = h * (0.15f + i * 0.08f)
            drawCircle(Color.White.copy(alpha = 0.85f), h * 0.06f, Offset(x, y))
            drawCircle(Color.White.copy(alpha = 0.85f), h * 0.045f, Offset(x + h * 0.07f, y + h * 0.015f))
            drawCircle(Color.White.copy(alpha = 0.85f), h * 0.04f, Offset(x - h * 0.065f, y + h * 0.02f))
        }
        fun hills(color: Color, base: Float, amp: Float, freq: Float, speed: Float) {
            path.reset()
            path.moveTo(0f, h)
            var x = 0f
            while (x <= w + 8f) {
                val y = base + sin((x / w) * freq + distance * speed) * amp
                path.lineTo(x, y)
                x += 8f
            }
            path.lineTo(w, h)
            path.close()
            drawPath(path, color)
        }
        hills(far, h * 0.6f, h * 0.08f, 5f, 0.6f)
        // trees
        val spacing = w * 0.3f
        val offset = (distance * w * 0.35f) % spacing
        var tx = -offset
        while (tx < w + spacing) {
            drawRect(Color(0xFF8B5E3C), Offset(tx - 4.dp.toPx(), h * 0.62f), Size(8.dp.toPx(), h * 0.16f))
            drawCircle(near.copy(alpha = 1f), h * 0.11f, Offset(tx, h * 0.58f))
            tx += spacing
        }
        hills(near, h * 0.8f, h * 0.03f, 9f, 1.2f)
        // path markers
        val mOffset = (distance * w * 0.6f) % (w * 0.12f)
        var mx = -mOffset
        while (mx < w) {
            drawRect(Color.White.copy(alpha = 0.5f), Offset(mx, h * 0.92f), Size(w * 0.05f, h * 0.015f))
            mx += w * 0.12f
        }
        val l = look ?: return@Canvas
        PetMotion.idle(frame, l, t)
        frame.facing = 1f
        frame.hop = abs(sin(t * (if (fast) 10f else 6f))) * (if (fast) 0.35f else 0.18f)
        val box = h * 0.7f
        drawIntoCanvas { c ->
            val nc = c.nativeCanvas
            nc.save()
            nc.translate(w * 0.5f - box / 2f, h * 0.96f - box)
            stage.draw(nc, box, box, l, frame, mode)
            nc.restore()
        }
    }
}
