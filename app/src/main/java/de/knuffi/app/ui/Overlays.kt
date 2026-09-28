package de.knuffi.app.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.knuffi.app.data.GameRepository
import de.knuffi.app.render.PKind
import de.knuffi.app.render.ParticleSystem
import de.knuffi.app.render.PetFrame
import de.knuffi.app.render.PetLook
import de.knuffi.app.render.PetMotion
import de.knuffi.app.render.RenderMode
import de.knuffi.app.render.renderMode
import de.knuffi.app.ui.components.KButton
import de.knuffi.app.ui.theme.LocalTokens
import de.knuffi.core.Form
import de.knuffi.core.GameEvent
import de.knuffi.core.GameState
import de.knuffi.core.Mood
import kotlin.math.floor
import kotlin.math.min

private sealed interface OverlayItem {
    data class LevelUp(val level: Int, val coins: Int) : OverlayItem
    data class Evolution(val from: Form, val to: Form) : OverlayItem
    data object Hatch : OverlayItem
}

private data class ToastItem(val id: Long, val emoji: String, val title: String?, val text: String)

@Composable
fun BoxScope.OverlayHost(state: GameState, debugScene: String?) {
    val overlays = remember { mutableStateListOf<OverlayItem>() }
    val toasts = remember { mutableStateListOf<ToastItem>() }
    var toastCounter by remember { mutableStateOf(0L) }

    LaunchedEffect(debugScene) {
        when (debugScene) {
            "levelup" -> overlays.add(OverlayItem.LevelUp(10, 30))
            "evolution" -> overlays.add(OverlayItem.Evolution(Form.LUMI, state.pet?.form ?: Form.STELLARIS))
            "hatch" -> overlays.add(OverlayItem.Hatch)
            "toast" -> toasts.add(ToastItem(-1, "🏆", "Erfolg freigeschaltet!", "Gourmet · +100 🪙"))
        }
    }

    LaunchedEffect(Unit) {
        GameRepository.events.collect { e ->
            fun toast(emoji: String, title: String?, text: String) {
                toastCounter++
                if (toasts.size < 6) toasts.add(ToastItem(toastCounter, emoji, title, text))
            }
            when (e) {
                is GameEvent.LevelUp -> {
                    val last = overlays.lastOrNull()
                    if (last is OverlayItem.LevelUp && overlays.size > 1) {
                        overlays[overlays.lastIndex] = OverlayItem.LevelUp(e.level, last.coins + e.coins)
                    } else {
                        overlays.add(OverlayItem.LevelUp(e.level, e.coins))
                    }
                }
                is GameEvent.Evolved -> overlays.add(OverlayItem.Evolution(e.from, e.to))
                GameEvent.Hatched -> overlays.add(OverlayItem.Hatch)
                is GameEvent.AchievementUnlocked -> toast(e.achievement.emoji, "Erfolg freigeschaltet!", "${e.achievement.title} · +${e.achievement.coins} 🪙")
                is GameEvent.QuestDone -> toast(e.type.emoji, "Tagesaufgabe geschafft!", "Hol dir deine Belohnung unter 🏆 Ziele.")
                is GameEvent.Message -> toast("💬", null, e.text)
                else -> Unit
            }
        }
    }

    val current = toasts.firstOrNull()
    LaunchedEffect(current?.id) {
        if (current != null) {
            delay(if (current.title == null) 2200 else 2800)
            toasts.remove(current)
        }
    }
    var lastToast by remember { mutableStateOf<ToastItem?>(null) }
    if (current != null) lastToast = current
    AnimatedVisibility(
        visible = current != null,
        enter = slideInVertically { -it } + fadeIn(),
        exit = slideOutVertically { -it } + fadeOut(),
        modifier = Modifier
            .align(Alignment.TopCenter)
            .statusBarsPadding()
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        lastToast?.let { ToastCard(it) }
    }

    when (val o = overlays.firstOrNull()) {
        null -> Unit
        is OverlayItem.LevelUp -> LevelUpOverlay(state, o.level, o.coins) { overlays.removeAt(0) }
        is OverlayItem.Evolution -> EvolutionOverlay(state, o.from, o.to) { overlays.removeAt(0) }
        OverlayItem.Hatch -> HatchOverlay(state) { overlays.removeAt(0) }
    }
}

private suspend fun delay(ms: Int) = kotlinx.coroutines.delay(ms.toLong())

@Composable
private fun ToastCard(t: ToastItem) {
    val tokens = LocalTokens.current
    Surface(
        shape = tokens.cardShape,
        color = MaterialTheme.colorScheme.inverseSurface,
        shadowElevation = 8.dp,
        border = if (tokens.pixel) tokens.border else null,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(t.emoji, fontSize = 26.sp)
            Spacer(Modifier.width(12.dp))
            Column {
                if (t.title != null) {
                    Text(t.title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.inverseOnSurface)
                }
                Text(t.text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.inverseOnSurface.copy(alpha = 0.9f))
            }
        }
    }
}

/** Dimmed full screen layer that swallows touches. */
@Composable
private fun Scrim(alpha: Float = 0.6f, content: @Composable BoxScope.() -> Unit) {
    Box(
        Modifier
            .fillMaxSize()
            .background(Color(0xFF0E0A1F).copy(alpha = alpha))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {},
        contentAlignment = Alignment.Center,
        content = content,
    )
}

@Composable
private fun ConfettiLayer(modifier: Modifier = Modifier, burstCount: Int = 70) {
    val particles = remember { ParticleSystem() }
    val time = rememberFrameTime()
    val mode = LocalTokens.current.style.renderMode
    var fired by remember { mutableStateOf(false) }
    Canvas(modifier) {
        val t = time.floatValue
        if (!fired) {
            fired = true
            particles.burst(PKind.CONFETTI, 0.5f, 0.35f, burstCount, 0.9f, 3f, 0.018f, ParticleSystem.CONFETTI_COLORS, gravity = 0.45f, upward = true)
            particles.burst(PKind.STAR, 0.5f, 0.35f, 14, 0.6f, 2f, 0.025f, ParticleSystem.STAR_COLORS, gravity = 0.2f)
        }
        if (t % 0.35f < 0.02f && t < 2.5f) {
            particles.burst(PKind.CONFETTI, particles.rnd.nextFloat(), -0.02f, 3, 0.1f, 3f, 0.016f, ParticleSystem.CONFETTI_COLORS, gravity = 0.3f)
        }
        particles.update(t)
        drawIntoCanvas { particles.draw(it.nativeCanvas, size.width, size.height, if (mode == RenderMode.PIXEL) RenderMode.FLAT else mode) }
    }
}

@Composable
private fun Rays(color: Color, modifier: Modifier = Modifier) {
    val rot by rememberInfiniteTransition(label = "rays").animateFloat(
        0f, 360f, infiniteRepeatable(tween(14_000, easing = LinearEasing)), label = "rot",
    )
    Canvas(modifier) {
        val c = Offset(size.width / 2f, size.height / 2f)
        val r = size.minDimension * 0.75f
        rotate(rot, c) {
            for (i in 0 until 12) {
                val a0 = Math.toRadians(i * 30.0)
                val a1 = Math.toRadians(i * 30.0 + 12.0)
                val p = Path().apply {
                    moveTo(c.x, c.y)
                    lineTo(c.x + (Math.cos(a0) * r).toFloat(), c.y + (Math.sin(a0) * r).toFloat())
                    lineTo(c.x + (Math.cos(a1) * r).toFloat(), c.y + (Math.sin(a1) * r).toFloat())
                    close()
                }
                drawPath(p, Brush.radialGradient(listOf(color.copy(alpha = 0.45f), Color.Transparent), center = c, radius = r))
            }
        }
    }
}

@Composable
private fun LevelUpOverlay(state: GameState, level: Int, coins: Int, onDone: () -> Unit) {
    val tokens = LocalTokens.current
    val appear = remember { Animatable(0f) }
    LaunchedEffect(Unit) { appear.animateTo(1f, spring(dampingRatio = 0.45f, stiffness = 180f)) }
    Scrim(0.8f) {
        Rays(tokens.gold, Modifier.size(420.dp))
        ConfettiLayer(Modifier.fillMaxSize())
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.scale(appear.value).padding(24.dp)) {
            Text("⭐", fontSize = 72.sp)
            Text(
                "LEVEL UP!",
                style = MaterialTheme.typography.displaySmall,
                color = tokens.gold,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "${state.pet?.name ?: "Dein Haustier"} ist jetzt Level $level",
                style = MaterialTheme.typography.titleMedium,
                color = Color.White,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(10.dp))
            Text("+$coins 🪙", style = MaterialTheme.typography.headlineSmall, color = Color.White)
            Spacer(Modifier.height(22.dp))
            KButton("Weiter", onDone, emoji = "🎉")
        }
    }
}

@Composable
private fun HatchOverlay(state: GameState, onDone: () -> Unit) {
    val tokens = LocalTokens.current
    val appear = remember { Animatable(0f) }
    LaunchedEffect(Unit) { appear.animateTo(1f, spring(dampingRatio = 0.5f, stiffness = 160f)) }
    val look = PetLook.of(state)?.copy(mood = Mood.HAPPY)
    Scrim(0.55f) {
        Rays(MaterialTheme.colorScheme.primary, Modifier.size(420.dp))
        ConfettiLayer(Modifier.fillMaxSize(), 50)
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.scale(appear.value).padding(24.dp)) {
            PetPortrait(look, state.style, Modifier.size(200.dp)) { f, t ->
                val phase = t % 1.2f
                if (phase < 0.4f) f.hop = kotlin.math.sin(phase / 0.4f * Math.PI.toFloat()) * 0.6f
                f.expression = de.knuffi.app.render.Expression.EXCITED
            }
            Text("Es ist geschlüpft!", style = MaterialTheme.typography.headlineMedium, color = Color.White, textAlign = TextAlign.Center)
            Spacer(Modifier.height(6.dp))
            Text(
                "Willkommen, ${state.pet?.name}! Kümmere dich gut um dein neues Knuffel. Wie es sich entwickelt, hängt ganz von dir ab.",
                style = MaterialTheme.typography.bodyLarge,
                color = Color.White.copy(alpha = 0.9f),
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(20.dp))
            KButton("Los geht's!", onDone, emoji = "🐣", container = tokens.joy)
        }
    }
}

@Composable
private fun EvolutionOverlay(state: GameState, from: Form, to: Form, onDone: () -> Unit) {
    val tokens = LocalTokens.current
    val time = rememberFrameTime()
    val stage = remember { PetStage(72) }
    val frame = remember { PetFrame() }
    val base = PetLook.of(state) ?: return
    val oldLook = base.copy(form = from, mood = Mood.HAPPY, sleeping = false, sick = false)
    val newLook = base.copy(form = to, mood = Mood.HAPPY, sleeping = false, sick = false)
    val mode = state.style.renderMode
    val sparkles = remember { ParticleSystem() }
    var burst by remember { mutableStateOf(false) }
    val reveal = time.floatValue > 3.7f

    Box(
        Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(Color(0xFF120C2E), Color(0xFF2E1B5E))))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {},
        contentAlignment = Alignment.Center,
    ) {
        if (reveal) Rays(tokens.gold, Modifier.size(460.dp))
        Canvas(Modifier.fillMaxSize()) {
            val t = time.floatValue
            val w = size.width
            val h = size.height
            PetMotion.idle(frame, newLook, t)
            val look: PetLook
            var silhouette: Int? = null
            when {
                t < 0.9f -> look = oldLook
                t < 3.3f -> {
                    val p = (t - 0.9f) / 2.4f
                    val freq = 2f + p * p * 16f
                    val k = floor((t - 0.9f) * freq).toInt()
                    look = if (k % 2 == 0) oldLook else newLook
                    silhouette = android.graphics.Color.WHITE
                    frame.hop = 0f
                    frame.squish = kotlin.math.sin(t * freq * 3f) * 0.1f
                }
                else -> {
                    look = newLook
                    frame.expression = de.knuffi.app.render.Expression.EXCITED
                    if (!burst) {
                        burst = true
                        sparkles.burst(PKind.SPARKLE, 0.5f, 0.32f, 24, 0.35f, 1.8f, 0.018f, ParticleSystem.STAR_COLORS)
                        sparkles.burst(PKind.STAR, 0.5f, 0.32f, 12, 0.4f, 1.6f, 0.014f, ParticleSystem.STAR_COLORS, gravity = 0.2f)
                    }
                }
            }
            sparkles.update(t)
            val box = min(w, h) * 0.8f
            drawIntoCanvas { c ->
                val nc = c.nativeCanvas
                nc.save()
                nc.translate((w - box) / 2f, h * 0.42f - box * 0.62f)
                stage.draw(nc, box, box, look, frame, mode, silhouette)
                nc.restore()
                sparkles.draw(nc, w, h, if (mode == RenderMode.PIXEL) RenderMode.FLAT else mode)
            }
            if (t in 3.2f..4.0f) {
                val a = if (t < 3.5f) (t - 3.2f) / 0.3f else 1f - (t - 3.5f) / 0.5f
                drawRect(Color.White.copy(alpha = a.coerceIn(0f, 1f)))
            }
        }
        if (!reveal) {
            Text(
                "Oh? ${state.pet?.name} verändert sich …",
                style = MaterialTheme.typography.titleLarge,
                color = Color.White,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(bottom = 80.dp, start = 24.dp, end = 24.dp),
            )
        }
        AnimatedVisibility(
            visible = reveal,
            enter = fadeIn(tween(500)) + scaleIn(initialScale = 0.8f),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = 36.dp, start = 24.dp, end = 24.dp),
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .background(Color(0xCC120C2E), tokens.cardShape)
                    .padding(horizontal = 20.dp, vertical = 16.dp),
            ) {
                Text(
                    "${state.pet?.name} ist jetzt ${to.title}!",
                    style = MaterialTheme.typography.headlineSmall,
                    color = tokens.gold,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(6.dp))
                Text(to.description, style = MaterialTheme.typography.bodyLarge, color = Color.White.copy(alpha = 0.9f), textAlign = TextAlign.Center)
                Text("Stufe: ${to.stage.title}", style = MaterialTheme.typography.bodyMedium, color = Color.White.copy(alpha = 0.7f))
                Spacer(Modifier.height(18.dp))
                KButton("Juhu!", onDone, emoji = "✨")
            }
        }
    }
}
