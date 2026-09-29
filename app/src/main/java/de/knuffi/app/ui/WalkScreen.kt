package de.knuffi.app.ui

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.knuffi.app.data.GameRepository
import de.knuffi.app.render.PetLook
import de.knuffi.app.render.PetPose
import de.knuffi.app.render.PetRenderer
import de.knuffi.app.steps.StepTracker
import de.knuffi.app.ui.components.ClayTextButton
import de.knuffi.app.ui.components.EmojiTile
import de.knuffi.app.ui.components.GlassIconButton
import de.knuffi.app.ui.components.SectionHeader
import de.knuffi.app.ui.components.SurfaceCard
import de.knuffi.app.ui.components.lighter
import de.knuffi.app.ui.components.staggered
import de.knuffi.app.ui.theme.LocalPalette
import de.knuffi.core.Action
import de.knuffi.core.GameState
import de.knuffi.core.Mood
import de.knuffi.core.StepRewards
import de.knuffi.core.TimeUtil
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.sin

@Composable
fun WalkScreen(state: GameState, onBack: () -> Unit) {
    val context = LocalContext.current
    val p = LocalPalette.current
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
            .verticalScroll(rememberScrollState()),
    ) {
        Box {
            WalkScene(
                state,
                today,
                Modifier
                    .fillMaxWidth()
                    .height(300.dp)
                    .shadow(10.dp, RoundedCornerShape(bottomStart = 36.dp, bottomEnd = 36.dp))
                    .clip(RoundedCornerShape(bottomStart = 36.dp, bottomEnd = 36.dp)),
            )
            Row(
                Modifier
                    .statusBarsPadding()
                    .padding(start = 12.dp, top = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                GlassIconButton("⬅️", onBack)
                Spacer(Modifier.width(10.dp))
                Text(
                    "Gassi gehen",
                    style = MaterialTheme.typography.headlineMedium.copy(
                        shadow = Shadow(Color(0x88000000), offset = Offset(0f, 3f), blurRadius = 10f),
                    ),
                    color = Color.White,
                )
            }
        }
        Column(Modifier.padding(horizontal = 16.dp)) {
            SurfaceCard(
                Modifier
                    .fillMaxWidth()
                    .offset(y = (-40).dp)
                    .staggered(0),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    StepRing(today, goal, Modifier.size(132.dp))
                    Spacer(Modifier.width(16.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Heute", style = MaterialTheme.typography.labelLarge, color = p.textMuted)
                        Text(TimeUtil.formatNumber(today), style = MaterialTheme.typography.displaySmall, color = p.text)
                        Text("Ziel: ${TimeUtil.formatNumber(goal)} Schritte", style = MaterialTheme.typography.bodyMedium, color = p.textMuted)
                        if (today >= goal) {
                            Spacer(Modifier.height(4.dp))
                            Text("Ziel erreicht! 🎉", style = MaterialTheme.typography.titleSmall, color = p.mintDeep)
                        }
                    }
                }
            }

            Column(Modifier.offset(y = (-28).dp)) {
                if (!hasSensor) {
                    SurfaceCard(Modifier.fillMaxWidth()) {
                        Text("😕 Kein Schrittzähler gefunden", style = MaterialTheme.typography.titleMedium, color = p.text)
                        Text(
                            "Dein Gerät hat leider keinen Schrittzähler-Sensor. Alles andere funktioniert natürlich trotzdem.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = p.textMuted,
                        )
                    }
                } else if (!hasPermission && Build.VERSION.SDK_INT >= 29) {
                    SurfaceCard(Modifier.fillMaxWidth(), color = if (p.dark) p.surfaceAlt else Color(0xFFFFEAF4)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            EmojiTile("👟", size = 46.dp, color = p.pink)
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text("Schritte zählen?", style = MaterialTheme.typography.titleMedium, color = p.text)
                                Text(
                                    "Mit jedem echten Schritt wird ${state.pet?.name ?: "dein Haustier"} fitter und glücklicher.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = p.textMuted,
                                )
                            }
                        }
                        Spacer(Modifier.height(12.dp))
                        ClayTextButton("Erlauben", { launcher.launch(Manifest.permission.ACTIVITY_RECOGNITION) }, emoji = "✅", modifier = Modifier.fillMaxWidth())
                    }
                }

                SectionHeader("Meilensteine", "🎯")
                Milestones(state, today)

                SectionHeader("Deine Bilanz", "📈")
                Row {
                    SurfaceCard(Modifier.weight(1f)) {
                        Text("Gesamt", style = MaterialTheme.typography.labelLarge, color = p.textMuted)
                        Text(TimeUtil.formatNumber(state.counters.totalSteps), style = MaterialTheme.typography.titleLarge, color = p.text)
                    }
                    Spacer(Modifier.width(10.dp))
                    SurfaceCard(Modifier.weight(1f)) {
                        Text("Bester Tag", style = MaterialTheme.typography.labelLarge, color = p.textMuted)
                        Text(TimeUtil.formatNumber(state.counters.bestDaySteps), style = MaterialTheme.typography.titleLarge, color = p.text)
                    }
                }
                Spacer(Modifier.height(10.dp))
                Text(
                    "Je 1.000 Schritte: 😊 +4, ❤️ +2 und 10 XP. Aber Laufen macht auch hungrig und müde!",
                    style = MaterialTheme.typography.bodyMedium,
                    color = p.textMuted,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(bottomBarSpace()))
            }
        }
    }
}

/** Vertical road with the step reward milestones. */
@Composable
private fun Milestones(state: GameState, today: Int) {
    val p = LocalPalette.current
    val tiers = StepRewards.tiers
    Column {
        for ((i, tier) in tiers.withIndex()) {
            val reached = today >= tier.first
            val claimed = i in state.steps.claimedTiers
            val prev = if (i == 0) 0 else tiers[i - 1].first
            val segment = ((today - prev) / (tier.first - prev).toFloat()).coerceIn(0f, 1f)
            Row(Modifier.fillMaxWidth().staggered(i + 1), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.width(44.dp).height(78.dp), contentAlignment = Alignment.Center) {
                    Canvas(Modifier.fillMaxSize()) {
                        val x = size.width / 2f
                        val sw = 6.dp.toPx()
                        drawLine(p.track, Offset(x, 0f), Offset(x, size.height), strokeWidth = sw, cap = StrokeCap.Round)
                        drawLine(p.mint, Offset(x, 0f), Offset(x, size.height / 2f * segment * 2f), strokeWidth = sw, cap = StrokeCap.Round)
                    }
                    Box(
                        Modifier
                            .size(34.dp)
                            .shadow(if (reached) 6.dp else 0.dp, CircleShape)
                            .clip(CircleShape)
                            .background(
                                if (reached) Brush.verticalGradient(listOf(p.mint.lighter(0.3f), p.mintDeep))
                                else Brush.verticalGradient(listOf(p.surfaceAlt, p.surfaceAlt)),
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(if (claimed) "✓" else if (reached) "🎁" else "🚩", fontSize = 15.sp, color = Color.White)
                    }
                }
                Spacer(Modifier.width(8.dp))
                SurfaceCard(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("${TimeUtil.formatNumber(tier.first)} Schritte", style = MaterialTheme.typography.titleSmall, color = p.text)
                            Text(
                                when {
                                    claimed -> "Abgeholt · +${tier.second} 🪙"
                                    reached -> "Geschafft! +${tier.second} 🪙"
                                    else -> "noch ${TimeUtil.formatNumber(tier.first - today)} · +${tier.second} 🪙"
                                },
                                style = MaterialTheme.typography.labelMedium,
                                color = if (reached && !claimed) p.mintDeep else p.textMuted,
                            )
                        }
                        if (reached && !claimed) {
                            ClayTextButton(
                                "Holen",
                                {
                                    GameRepository.perform(Action.ClaimStepTier(i))
                                    GameRepository.message("Schritt-Belohnung: +${tier.second} 🪙")
                                },
                                small = true,
                                color = p.gold,
                                deep = p.goldDeep,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StepRing(today: Int, goal: Int, modifier: Modifier = Modifier) {
    val p = LocalPalette.current
    val fraction by animateFloatAsState((today / goal.toFloat()).coerceIn(0f, 1f), tween(1400), label = "ring")
    Box(modifier, contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val sw = 14.dp.toPx()
            val inset = sw / 2f
            val arc = Size(size.width - sw, size.height - sw)
            drawArc(p.track, 0f, 360f, false, Offset(inset, inset), arc, style = Stroke(sw))
            drawArc(
                Brush.sweepGradient(listOf(p.sky, p.mint, p.gold, p.pink, p.sky)),
                -90f, 360f * fraction, false, Offset(inset, inset), arc,
                style = Stroke(sw, cap = StrokeCap.Round),
            )
            // glossy highlight on the ring
            drawArc(
                Color.White.copy(alpha = 0.35f), -90f, 360f * fraction, false,
                Offset(inset, inset - sw * 0.2f), arc, style = Stroke(sw * 0.25f, cap = StrokeCap.Round),
            )
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("👟", fontSize = 30.sp)
            Text("${(fraction * 100).toInt()}%", style = MaterialTheme.typography.titleMedium, color = p.text)
        }
    }
}

/** Parallax landscape with the pet walking; speeds up when new steps come in. */
@Composable
private fun WalkScene(state: GameState, steps: Int, modifier: Modifier = Modifier) {
    val p = LocalPalette.current
    val look = remember(state.pet, state.equipped) { PetLook.of(state)?.copy(sleeping = false, mood = Mood.HAPPY) }
    val renderer = remember { PetRenderer() }
    val pose = remember { PetPose() }
    val time = rememberFrameTime()
    var boostUntil by remember { mutableFloatStateOf(0f) }
    val motion = remember { FloatArray(3) } // distance, lastT, walk phase
    LaunchedEffect(steps) { boostUntil = time.floatValue + 4f }
    val night = p.dark
    val path = remember { Path() }
    Canvas(modifier) {
        val t = time.floatValue
        val dt = (t - motion[1]).coerceIn(0f, 0.1f)
        motion[1] = t
        val fast = t < boostUntil
        val speed = if (fast) 1f else 0.4f
        motion[0] += dt * speed
        motion[2] += dt * (if (fast) 11f else 7f)
        val d = motion[0]
        val w = size.width
        val h = size.height

        // sky
        drawRect(
            Brush.verticalGradient(
                if (night) listOf(Color(0xFF1B1747), Color(0xFF4A3A8A), Color(0xFF8A5AA8))
                else listOf(Color(0xFF6EC3FF), Color(0xFFA9DEFF), Color(0xFFFFE1EE)),
            ),
        )
        if (night) {
            for (i in 0 until 40) {
                val sx = (i * 97 % 100) / 100f * w
                val sy = (i * 53 % 60) / 100f * h
                val tw = 0.5f + 0.5f * sin(t * 2f + i)
                drawCircle(Color.White.copy(alpha = 0.3f + 0.6f * tw), radius = 1.2.dp.toPx(), center = Offset(sx, sy))
            }
            drawCircle(Brush.radialGradient(listOf(Color(0x66FFF6D5), Color.Transparent), center = Offset(w * 0.8f, h * 0.24f), radius = h * 0.22f), radius = h * 0.22f, center = Offset(w * 0.8f, h * 0.24f))
            drawCircle(Color(0xFFFFF4D0), radius = h * 0.07f, center = Offset(w * 0.8f, h * 0.24f))
            drawCircle(Color(0xFF3E3480), radius = h * 0.06f, center = Offset(w * 0.83f, h * 0.22f))
        } else {
            drawCircle(Brush.radialGradient(listOf(Color(0x99FFF3B0), Color.Transparent), center = Offset(w * 0.8f, h * 0.24f), radius = h * 0.3f), radius = h * 0.3f, center = Offset(w * 0.8f, h * 0.24f))
            drawCircle(Brush.radialGradient(listOf(Color(0xFFFFF7C2), Color(0xFFFFD34D)), center = Offset(w * 0.78f, h * 0.22f), radius = h * 0.09f), radius = h * 0.08f, center = Offset(w * 0.8f, h * 0.24f))
        }
        // clouds
        for (i in 0 until 4) {
            val x = (((i * 0.33f - d * 0.03f) % 1.4f + 1.4f) % 1.4f) * w - w * 0.2f
            val y = h * (0.14f + (i % 3) * 0.08f)
            cloud(x, y, h * (0.05f + 0.01f * (i % 2)), if (night) Color(0xFF6A5AA8) else Color.White)
        }
        fun hills(top: Color, bottom: Color, base: Float, amp: Float, freq: Float, spd: Float) {
            path.reset()
            path.moveTo(0f, h)
            var x = 0f
            while (x <= w + 8f) {
                val y = base + sin((x / w) * freq + d * spd) * amp + sin((x / w) * freq * 2.3f + d * spd * 1.7f) * amp * 0.35f
                path.lineTo(x, y)
                x += 8f
            }
            path.lineTo(w, h)
            path.close()
            drawPath(path, Brush.verticalGradient(listOf(top, bottom), startY = base - amp, endY = h))
        }
        if (night) {
            hills(Color(0xFF3B4C8A), Color(0xFF26305E), h * 0.55f, h * 0.07f, 4f, 0.25f)
            hills(Color(0xFF2F6A6A), Color(0xFF1E3E48), h * 0.66f, h * 0.05f, 6f, 0.6f)
        } else {
            hills(Color(0xFFB6E3C0), Color(0xFF8FCFA6), h * 0.55f, h * 0.07f, 4f, 0.25f)
            hills(Color(0xFF8AD69E), Color(0xFF5DB67E), h * 0.66f, h * 0.05f, 6f, 0.6f)
        }
        // trees with round, shaded crowns
        val spacing = w * 0.34f
        val offset = (d * w * 0.5f) % spacing
        var tx = -offset
        var n = 0
        while (tx < w + spacing) {
            tree(tx, h * 0.74f, h * (0.1f + 0.02f * (n % 2)), night)
            tx += spacing
            n++
        }
        // meadow + path
        drawRect(
            Brush.verticalGradient(if (night) listOf(Color(0xFF2E5A4A), Color(0xFF1C3A34)) else listOf(Color(0xFF7FD08E), Color(0xFF55B46F)), startY = h * 0.76f, endY = h),
            topLeft = Offset(0f, h * 0.76f), size = Size(w, h * 0.24f),
        )
        drawRoundRect(
            Brush.verticalGradient(if (night) listOf(Color(0xFF8C7A6A), Color(0xFF6A5A4E)) else listOf(Color(0xFFF2D8A8), Color(0xFFE0BC84))),
            topLeft = Offset(-20f, h * 0.84f), size = Size(w + 40f, h * 0.1f), cornerRadius = CornerRadius(h * 0.05f),
        )
        // pebbles & flowers moving fastest
        val mSpacing = w * 0.16f
        val mOffset = (d * w * 0.9f) % mSpacing
        var mx = -mOffset
        var k = 0
        while (mx < w + mSpacing) {
            drawOval(Color.White.copy(alpha = 0.35f), topLeft = Offset(mx, h * 0.885f), size = Size(w * 0.03f, h * 0.012f))
            if (k % 2 == 0) {
                val fy = h * 0.965f
                drawLine(Color(0xFF3E8A4E), Offset(mx + w * 0.06f, fy), Offset(mx + w * 0.06f, fy - h * 0.035f), strokeWidth = 2.dp.toPx())
                drawCircle(if (k % 4 == 0) Color(0xFFFF8FB8) else Color(0xFFFFE066), radius = h * 0.014f, center = Offset(mx + w * 0.06f, fy - h * 0.04f))
            }
            mx += mSpacing
            k++
        }

        val l = look ?: return@Canvas
        val ph = motion[2]
        val s = sin(ph)
        pose.defaults(l, t)
        pose.turn = 0.75f
        pose.strideL = kotlin.math.cos(ph) * 0.8f
        pose.strideR = -pose.strideL
        pose.footL = max(0f, s) * 0.9f
        pose.footR = max(0f, -s) * 0.9f
        pose.lift = abs(s) * (if (fast) 0.12f else 0.05f)
        pose.tilt = s * 3f
        pose.armWaveL = s * 18f
        pose.armWaveR = -s * 18f
        pose.eyes = if (fast) de.knuffi.app.render.EyeShape.HAPPY else pose.eyes
        pose.mouth = de.knuffi.app.render.MouthShape.OPEN
        val petSize = h * 0.34f
        drawIntoCanvas {
            renderer.drawShadow(it.nativeCanvas, w * 0.45f, h * 0.9f, petSize, pose, l)
            renderer.draw(it.nativeCanvas, w * 0.45f, h * 0.9f, petSize, l, pose)
        }
        // warm light / vignette
        drawRect(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.25f), Color.Transparent), endY = h * 0.3f))
    }
}

private fun DrawScope.cloud(x: Float, y: Float, r: Float, color: Color) {
    val shade = Color.Black.copy(alpha = 0.06f)
    drawOval(shade, topLeft = Offset(x - r * 1.6f, y + r * 0.1f), size = Size(r * 3.4f, r * 1.2f))
    drawCircle(color, r, Offset(x, y))
    drawCircle(color, r * 0.8f, Offset(x + r * 1.1f, y + r * 0.25f))
    drawCircle(color, r * 0.75f, Offset(x - r * 1.05f, y + r * 0.3f))
    drawOval(color, topLeft = Offset(x - r * 1.7f, y + r * 0.1f), size = Size(r * 3.5f, r * 0.9f))
    drawCircle(Color.White.copy(alpha = 0.5f), r * 0.35f, Offset(x - r * 0.3f, y - r * 0.35f))
}

private fun DrawScope.tree(x: Float, ground: Float, r: Float, night: Boolean) {
    drawOval(Color.Black.copy(alpha = 0.12f), topLeft = Offset(x - r * 0.9f, ground - r * 0.12f), size = Size(r * 1.8f, r * 0.3f))
    drawRoundRect(
        Brush.horizontalGradient(listOf(Color(0xFF9A6A48), Color(0xFF6E4630)), startX = x - r * 0.12f, endX = x + r * 0.12f),
        topLeft = Offset(x - r * 0.12f, ground - r * 1.3f), size = Size(r * 0.24f, r * 1.3f), cornerRadius = CornerRadius(r * 0.1f),
    )
    val light = if (night) Color(0xFF4F8A70) else Color(0xFF9BE08A)
    val mid = if (night) Color(0xFF2F6250) else Color(0xFF5EBB63)
    val dark = if (night) Color(0xFF1E4236) else Color(0xFF3B8E48)
    val c = Offset(x, ground - r * 1.55f)
    drawCircle(Brush.radialGradient(listOf(light, mid, dark), center = Offset(c.x - r * 0.35f, c.y - r * 0.4f), radius = r * 1.5f), radius = r, center = c)
    drawCircle(Brush.radialGradient(listOf(light, mid, dark), center = Offset(c.x - r * 0.7f, c.y - r * 0.1f), radius = r * 1.1f), radius = r * 0.7f, center = Offset(c.x - r * 0.55f, c.y + r * 0.25f))
    drawCircle(Brush.radialGradient(listOf(light, mid, dark), center = Offset(c.x + r * 0.2f, c.y - r * 0.1f), radius = r * 1.1f), radius = r * 0.7f, center = Offset(c.x + r * 0.6f, c.y + r * 0.25f))
    drawCircle(Color.White.copy(alpha = 0.18f), radius = r * 0.3f, center = Offset(c.x - r * 0.3f, c.y - r * 0.45f))
}
