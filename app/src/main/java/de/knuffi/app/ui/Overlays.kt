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
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.sp
import de.knuffi.app.data.GameRepository
import de.knuffi.app.render.EyeShape
import de.knuffi.app.render.MouthShape
import de.knuffi.app.render.PKind
import de.knuffi.app.render.ParticleSystem
import de.knuffi.app.render.PetLook
import de.knuffi.app.render.PetPose
import de.knuffi.app.render.PetRenderer
import de.knuffi.app.ui.components.ClayTextButton
import de.knuffi.app.ui.components.EmojiTile
import de.knuffi.app.ui.theme.LocalPalette
import de.knuffi.app.notify.Battery
import de.knuffi.core.Action
import de.knuffi.core.Catalog
import de.knuffi.core.EggLine
import de.knuffi.core.Stickers
import de.knuffi.core.TripLoot
import de.knuffi.core.Form
import de.knuffi.core.GameEvent
import de.knuffi.core.GameState
import de.knuffi.core.Mood
import kotlinx.coroutines.delay
import kotlin.math.floor
import kotlin.math.min
import kotlin.math.sin

private sealed interface OverlayItem {
    data class LevelUp(val level: Int, val coins: Int) : OverlayItem
    data class Evolution(val from: Form, val to: Form) : OverlayItem
    data object Hatch : OverlayItem
    data class EggFound(val line: EggLine, val reason: String) : OverlayItem
    data class Birthday(val name: String, val months: Int, val coins: Int) : OverlayItem
    data class TripBack(val name: String, val loot: TripLoot) : OverlayItem
}

private data class ToastItem(val id: Long, val emoji: String, val title: String?, val text: String)

@Composable
fun BoxScope.OverlayHost(state: GameState, debugScene: String?) {
    val overlays = remember { mutableStateListOf<OverlayItem>() }
    val toasts = remember { mutableStateListOf<ToastItem>() }
    var toastCounter by remember { mutableLongStateOf(0L) }

    LaunchedEffect(debugScene) {
        when (debugScene) {
            "levelup" -> overlays.add(OverlayItem.LevelUp(10, 30))
            "evolution" -> overlays.add(OverlayItem.Evolution(Form.LUMI, state.pet?.form ?: Form.STELLARIS))
            "hatch" -> overlays.add(OverlayItem.Hatch)
            "toast" -> toasts.add(ToastItem(-1, "🏆", "Erfolg freigeschaltet!", "Gourmet · +100 🪙"))
            "eggfound" -> overlays.add(OverlayItem.EggFound(EggLine.FEUER, "Dein Haustier ist erwachsen geworden"))
            "birthday" -> overlays.add(OverlayItem.Birthday(state.pet?.name ?: "Knuffi", 3, 100))
            "tripback" -> overlays.add(OverlayItem.TripBack("Mochi", TripLoot(48, mapOf("seed_pumpkin" to 2, "apple" to 1), listOf("s_panda"), EggLine.WALD)))
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
                is GameEvent.QuestDone -> toast(e.type.emoji, if (e.weekly) "Wochenaufgabe geschafft!" else "Tagesaufgabe geschafft!", "Hol dir deine Belohnung unter 🏆 Ziele.")
                is GameEvent.Message -> toast("💬", null, e.text)
                is GameEvent.EggFound -> overlays.add(OverlayItem.EggFound(e.line, e.reason))
                is GameEvent.NewForm -> toast(if (e.shiny) "✨" else "📖", if (e.shiny) "Schillernd! Neu im Album" else "Neu im Album!", e.form.title)
                is GameEvent.StickersGot -> toast("🎴", "Neue Sticker!", e.ids.mapNotNull { Stickers[it]?.emoji }.joinToString(" "))
                is GameEvent.Harvested -> Catalog[e.itemId]?.let { toast(it.emoji, "Geerntet!", "${e.count}× ${it.name}") }
                is GameEvent.TripReturned -> overlays.add(OverlayItem.TripBack(e.petName, e.loot))
                is GameEvent.Birthday -> overlays.add(OverlayItem.Birthday(e.petName, e.months, e.coins))
                is GameEvent.PassTierUp -> toast("🎫", "Pass-Stufe ${e.tier}!", "Deine Belohnung wartet unter 🌍 Welt.")
                else -> Unit
            }
        }
    }

    val current = toasts.firstOrNull()
    LaunchedEffect(current?.id) {
        if (current != null) {
            delay(if (current.title == null) 2200L else 2800L)
            toasts.remove(current)
        }
    }
    var lastToast by remember { mutableStateOf<ToastItem?>(null) }
    if (current != null) lastToast = current
    AnimatedVisibility(
        visible = current != null,
        enter = slideInVertically(spring(dampingRatio = 0.6f, stiffness = 400f)) { -it * 2 } + fadeIn(),
        exit = slideOutVertically { -it * 2 } + fadeOut(),
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
        is OverlayItem.EggFound -> EggFoundOverlay(o.line, o.reason) { overlays.removeAt(0) }
        is OverlayItem.Birthday -> BirthdayOverlay(state, o.name, o.months, o.coins) { overlays.removeAt(0) }
        is OverlayItem.TripBack -> TripBackOverlay(o.name, o.loot) { overlays.removeAt(0) }
    }
    if (overlays.isEmpty()) BatteryHint(state)
}

/** Asks once (after a few days) to exempt the app from battery optimisation, so reminders arrive on time. */
@Composable
private fun BatteryHint(state: GameState) {
    val context = LocalContext.current
    val ignoring = remember { Battery.isIgnoring(context) }
    val show = state.onboarded && state.settings.notifications && !state.settings.batteryHintSeen &&
        state.counters.daysPlayed >= 2 && !ignoring
    if (!show) return
    fun seen() = GameRepository.perform(Action.UpdateSettings(state.settings.copy(batteryHintSeen = true)))
    ConfirmDialog(
        title = "🔋 Erinnerungen nicht verpassen",
        text = "Manche Handys schicken Apps im Hintergrund schlafen. Dann kommen die Meldungen zu spät, wenn ${state.pet?.name ?: "dein Haustier"} Hunger hat. Erlaubst du Knuffi eine Ausnahme vom Akku-Sparen? Knuffi verbraucht dabei kaum Akku.",
        confirm = "Erlauben",
        onConfirm = {
            seen()
            Battery.request(context)
        },
        onDismiss = { seen() },
    )
}

@Composable
private fun EggFoundOverlay(line: EggLine, reason: String, onDone: () -> Unit) {
    val p = LocalPalette.current
    val appear = remember { Animatable(0f) }
    LaunchedEffect(Unit) { appear.animateTo(1f, spring(dampingRatio = 0.5f, stiffness = 170f)) }
    val look = remember(line) { PetLook(Form.EGG, Mood.HAPPY, line = line) }
    Scrim(0.75f) {
        Rays(p.gold, Modifier.size(460.dp))
        ConfettiLayer(Modifier.fillMaxSize(), 40)
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.scale(appear.value).padding(24.dp)) {
            PetPortrait(look, Modifier.size(200.dp), sizeFactor = 0.62f) { pose, t ->
                pose.eggWobble = sin(t * 2.5f) * 6f
                val ph = t % 2f
                if (ph < 0.3f) pose.eggWobble += sin(ph * 60f) * 8f
            }
            ExtrudedTitle("Ein neues Ei!", Color.White, p.goldDeep)
            Spacer(Modifier.height(6.dp))
            Text("${line.emoji} ${line.title}", style = MaterialTheme.typography.titleLarge, color = p.gold)
            Spacer(Modifier.height(4.dp))
            Text(
                "$reason. Ausbrüten kannst du es im Kuschelhaus unter 🌍 Welt.",
                style = MaterialTheme.typography.bodyLarge,
                color = Color.White.copy(alpha = 0.9f),
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(20.dp))
            ClayTextButton("Juhu!", onDone, emoji = "🥚", color = p.gold, deep = p.goldDeep)
        }
    }
}

@Composable
private fun BirthdayOverlay(state: GameState, name: String, months: Int, coins: Int, onDone: () -> Unit) {
    val p = LocalPalette.current
    val appear = remember { Animatable(0f) }
    LaunchedEffect(Unit) { appear.animateTo(1f, spring(dampingRatio = 0.45f, stiffness = 170f)) }
    val look = PetLook.of(state)?.copy(mood = Mood.HAPPY, sleeping = false, hat = "hat_party")
    Scrim(0.8f) {
        Rays(p.pink, Modifier.size(460.dp))
        ConfettiLayer(Modifier.fillMaxSize(), 90)
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.scale(appear.value).padding(24.dp)) {
            Text("🎂", fontSize = 80.sp)
            if (look != null && state.pet?.name == name) {
                PetPortrait(look, Modifier.size(150.dp), sizeFactor = 0.8f) { pose, t ->
                    val ph = t % 0.8f
                    if (ph < 0.4f) pose.lift = sin(ph / 0.4f * Math.PI.toFloat()) * 0.25f
                    pose.eyes = EyeShape.HAPPY
                    pose.mouth = MouthShape.GRIN
                    pose.armL = 1f
                    pose.armR = 0.6f + 0.4f * sin(t * 6f)
                }
            }
            ExtrudedTitle("Alles Gute!", Color.White, p.pinkDeep)
            Spacer(Modifier.height(6.dp))
            Text(
                if (months % 12 == 0) "$name und du seid heute ${months / 12} ${if (months == 12) "Jahr" else "Jahre"} zusammen! 🥳"
                else "$name und du seid heute $months ${if (months == 1) "Monat" else "Monate"} zusammen!",
                style = MaterialTheme.typography.titleMedium,
                color = Color.White,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(10.dp))
            Text(
                "+$coins 🪙",
                style = MaterialTheme.typography.headlineSmall,
                color = Color(0xFF4A3000),
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(Brush.verticalGradient(listOf(Color(0xFFFFE9A6), p.gold)))
                    .padding(horizontal = 18.dp, vertical = 4.dp),
            )
            Spacer(Modifier.height(22.dp))
            ClayTextButton("Feiern!", onDone, emoji = "🎉")
        }
    }
}

@Composable
private fun TripBackOverlay(name: String, loot: TripLoot, onDone: () -> Unit) {
    val p = LocalPalette.current
    val appear = remember { Animatable(0f) }
    LaunchedEffect(Unit) { appear.animateTo(1f, spring(dampingRatio = 0.55f, stiffness = 200f)) }
    Scrim(0.75f) {
        Rays(p.sky, Modifier.size(420.dp))
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.scale(appear.value).padding(24.dp)) {
            Text("🎒", fontSize = 80.sp)
            ExtrudedTitle("Zurück!", Color.White, p.skyDeep)
            Spacer(Modifier.height(4.dp))
            Text("$name hat etwas mitgebracht:", style = MaterialTheme.typography.titleMedium, color = Color.White, textAlign = TextAlign.Center)
            Spacer(Modifier.height(12.dp))
            val entries = buildList {
                add("🪙" to "${loot.coins} Münzen")
                for ((id, n) in loot.items) Catalog[id]?.let { add(it.emoji to "${n}× ${it.name}") }
                for (id in loot.stickers) Stickers[id]?.let { add(it.emoji to "Sticker: ${it.name}") }
                loot.egg?.let { add(it.emoji to it.title) }
            }
            Column(
                Modifier
                    .clip(RoundedCornerShape(24.dp))
                    .background(Color.White.copy(alpha = 0.12f))
                    .padding(horizontal = 18.dp, vertical = 10.dp),
            ) {
                for ((emoji, label) in entries) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 3.dp)) {
                        Text(emoji, fontSize = 24.sp)
                        Spacer(Modifier.width(10.dp))
                        Text(label, style = MaterialTheme.typography.titleSmall, color = Color.White)
                    }
                }
            }
            Spacer(Modifier.height(22.dp))
            ClayTextButton("Danke!", onDone, emoji = "💖", color = p.sky, deep = p.skyDeep)
        }
    }
}

@Composable
private fun ToastCard(t: ToastItem) {
    val p = LocalPalette.current
    val shape = RoundedCornerShape(24.dp)
    Row(
        Modifier
            .fillMaxWidth()
            .shadow(14.dp, shape, ambientColor = p.shadow.copy(alpha = 0.4f), spotColor = p.shadow.copy(alpha = 0.4f))
            .clip(shape)
            .background(if (p.dark) Color(0xF72A2250) else Color(0xFA2E2342))
            .border(1.dp, Color.White.copy(alpha = 0.12f), shape)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        EmojiTile(t.emoji, size = 42.dp, color = if (t.title != null) p.gold else p.pink)
        Spacer(Modifier.width(12.dp))
        Column {
            if (t.title != null) Text(t.title, style = MaterialTheme.typography.titleSmall, color = p.gold)
            Text(t.text, style = MaterialTheme.typography.bodyMedium, color = Color.White)
        }
    }
}

/** Dimmed full screen layer that swallows touches. */
@Composable
private fun Scrim(alpha: Float = 0.6f, content: @Composable BoxScope.() -> Unit) {
    Box(
        Modifier
            .fillMaxSize()
            .background(Brush.radialGradient(listOf(Color(0xFF2A1F5E).copy(alpha = alpha * 0.85f), Color(0xFF0B0820).copy(alpha = alpha))))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {},
        contentAlignment = Alignment.Center,
        content = content,
    )
}

@Composable
private fun ConfettiLayer(modifier: Modifier = Modifier, burstCount: Int = 70, y: Float = 0.35f) {
    val particles = remember { ParticleSystem() }
    val time = rememberFrameTime()
    val tf = rememberCanvasTypeface()
    var fired by remember { mutableStateOf(false) }
    Canvas(modifier) {
        val t = time.floatValue
        if (!fired) {
            fired = true
            particles.typeface = tf
            particles.burst(PKind.CONFETTI, 0.5f, y, burstCount, 0.9f, 3f, 0.018f, ParticleSystem.CONFETTI_COLORS, gravity = 0.45f, upward = true)
            particles.burst(PKind.STAR, 0.5f, y, 14, 0.6f, 2f, 0.025f, ParticleSystem.STAR_COLORS, gravity = 0.2f)
            particles.burst(PKind.SPARKLE, 0.5f, y, 16, 0.4f, 1.6f, 0.02f, ParticleSystem.SPARKLE_COLORS)
        }
        if (t % 0.35f < 0.02f && t < 2.5f) {
            particles.burst(PKind.CONFETTI, particles.rnd.nextFloat(), -0.02f, 3, 0.1f, 3f, 0.016f, ParticleSystem.CONFETTI_COLORS, gravity = 0.3f)
        }
        particles.update(t)
        drawIntoCanvas { particles.draw(it.nativeCanvas, size.width, size.height) }
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
                val path = Path().apply {
                    moveTo(c.x, c.y)
                    lineTo(c.x + (Math.cos(a0) * r).toFloat(), c.y + (Math.sin(a0) * r).toFloat())
                    lineTo(c.x + (Math.cos(a1) * r).toFloat(), c.y + (Math.sin(a1) * r).toFloat())
                    close()
                }
                drawPath(path, Brush.radialGradient(listOf(color.copy(alpha = 0.5f), Color.Transparent), center = c, radius = r))
            }
        }
        drawCircle(Brush.radialGradient(listOf(color.copy(alpha = 0.55f), Color.Transparent), center = c, radius = r * 0.45f), radius = r * 0.45f, center = c)
    }
}

/** Big glossy 3D title text with a darker extrusion underneath. */
@Composable
private fun ExtrudedTitle(text: String, face: Color, side: Color) {
    Box {
        for (i in 4 downTo 1) {
            Text(
                text,
                style = MaterialTheme.typography.displaySmall,
                color = side,
                modifier = Modifier.graphicsLayer { translationY = i * 1.5.dp.toPx() },
            )
        }
        Text(text, style = MaterialTheme.typography.displaySmall, color = face)
    }
}

@Composable
private fun LevelUpOverlay(state: GameState, level: Int, coins: Int, onDone: () -> Unit) {
    val p = LocalPalette.current
    val appear = remember { Animatable(0f) }
    LaunchedEffect(Unit) { appear.animateTo(1f, spring(dampingRatio = 0.45f, stiffness = 180f)) }
    val look = PetLook.of(state)?.copy(mood = Mood.HAPPY, sleeping = false)
    Scrim(0.82f) {
        Rays(p.gold, Modifier.size(460.dp))
        ConfettiLayer(Modifier.fillMaxSize())
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.scale(appear.value).padding(24.dp)) {
            Box(contentAlignment = Alignment.Center) {
                Text("⭐", fontSize = 150.sp, modifier = Modifier.graphicsLayer { rotationZ = (1f - appear.value) * 90f })
                Text("$level", style = MaterialTheme.typography.displayMedium, color = Color(0xFF7A4A00), modifier = Modifier.padding(top = 18.dp))
            }
            PetPortrait(look, Modifier.size(120.dp), sizeFactor = 0.8f) { pose, t ->
                val ph = t % 0.9f
                if (ph < 0.45f) pose.lift = sin(ph / 0.45f * Math.PI.toFloat()) * 0.3f
                pose.armL = 1f
                pose.armR = 1f
                pose.eyes = EyeShape.HAPPY
                pose.mouth = MouthShape.GRIN
            }
            ExtrudedTitle("LEVEL UP!", p.gold, p.goldDeep)
            Spacer(Modifier.height(4.dp))
            Text(
                "${state.pet?.name ?: "Dein Haustier"} ist jetzt Level $level",
                style = MaterialTheme.typography.titleMedium,
                color = Color.White,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(10.dp))
            Text(
                "+$coins 🪙",
                style = MaterialTheme.typography.headlineSmall,
                color = Color(0xFF4A3000),
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(Brush.verticalGradient(listOf(Color(0xFFFFE9A6), p.gold)))
                    .padding(horizontal = 18.dp, vertical = 4.dp),
            )
            Spacer(Modifier.height(22.dp))
            ClayTextButton("Weiter", onDone, emoji = "🎉")
        }
    }
}

@Composable
private fun HatchOverlay(state: GameState, onDone: () -> Unit) {
    val p = LocalPalette.current
    val appear = remember { Animatable(0f) }
    LaunchedEffect(Unit) { appear.animateTo(1f, spring(dampingRatio = 0.5f, stiffness = 160f)) }
    val look = PetLook.of(state)?.copy(mood = Mood.HAPPY)
    Scrim(0.6f) {
        Rays(p.pink, Modifier.size(460.dp))
        ConfettiLayer(Modifier.fillMaxSize(), 50)
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.scale(appear.value).padding(24.dp)) {
            PetPortrait(look, Modifier.size(210.dp)) { pose, t ->
                val phase = t % 1.2f
                if (phase < 0.4f) pose.lift = sin(phase / 0.4f * Math.PI.toFloat()) * 0.4f
                pose.eyes = EyeShape.STAR
                pose.mouth = MouthShape.OPEN
                pose.armL = 0.8f + 0.2f * sin(t * 8f)
                pose.armR = 0.8f
            }
            ExtrudedTitle("Geschlüpft!", Color.White, p.pinkDeep)
            Spacer(Modifier.height(6.dp))
            Text(
                "Willkommen, ${state.pet?.name}! ${if (state.pet?.shiny == true) "Wow, es schillert! ✨ " else ""}Kümmere dich gut um dein neues ${state.pet?.form?.title ?: "Haustier"}. Wie es sich entwickelt, hängt ganz von dir ab.",
                style = MaterialTheme.typography.bodyLarge,
                color = Color.White.copy(alpha = 0.9f),
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(20.dp))
            ClayTextButton("Los geht's!", onDone, emoji = "🐣")
        }
    }
}

@Composable
private fun EvolutionOverlay(state: GameState, from: Form, to: Form, onDone: () -> Unit) {
    val p = LocalPalette.current
    val time = rememberFrameTime()
    val renderer = remember { PetRenderer() }
    val pose = remember { PetPose() }
    val base = PetLook.of(state) ?: return
    val oldLook = base.copy(form = from, mood = Mood.HAPPY, sleeping = false, sick = false)
    val newLook = base.copy(form = to, mood = Mood.HAPPY, sleeping = false, sick = false)
    val sparkles = remember { ParticleSystem() }
    var burst by remember { mutableStateOf(false) }
    val reveal = time.floatValue > 3.7f

    Box(
        Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(Color(0xFF0E0A28), Color(0xFF2E1B5E), Color(0xFF4B2A7A))))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {},
        contentAlignment = Alignment.Center,
    ) {
        if (reveal) Rays(p.gold, Modifier.size(480.dp))
        Canvas(Modifier.fillMaxSize()) {
            val t = time.floatValue
            val w = size.width
            val h = size.height
            // swirling light orbs converging on the pet
            if (t < 3.4f) {
                for (i in 0 until 14) {
                    val a = t * (2f + t * 0.8f) + i * 0.45f
                    val r = min(w, h) * 0.45f * (1f - (t / 3.4f)) + min(w, h) * 0.08f
                    val x = w / 2f + kotlin.math.cos(a) * r
                    val y = h * 0.4f + sin(a) * r * 0.6f
                    drawCircle(Brush.radialGradient(listOf(Color(0xFFFFF3B0), Color.Transparent), center = Offset(x, y), radius = 18f), radius = 18f, center = Offset(x, y))
                }
            }
            val look: PetLook
            var silhouette: Int? = null
            pose.defaults(newLook, t)
            when {
                t < 0.9f -> look = oldLook
                t < 3.3f -> {
                    val prog = (t - 0.9f) / 2.4f
                    val freq = 2f + prog * prog * 16f
                    val k = floor((t - 0.9f) * freq).toInt()
                    look = if (k % 2 == 0) oldLook else newLook
                    silhouette = android.graphics.Color.WHITE
                    val s = sin(t * freq * 3f) * 0.1f
                    pose.scaleY *= 1f - s
                    pose.scaleX *= 1f + s
                    pose.lift = prog * 0.3f
                }
                else -> {
                    look = newLook
                    pose.eyes = EyeShape.STAR
                    pose.mouth = MouthShape.GRIN
                    pose.armL = 1f
                    pose.armR = 1f
                    val ph = (t - 3.3f) % 1f
                    if (ph < 0.4f) pose.lift = sin(ph / 0.4f * Math.PI.toFloat()) * 0.25f
                    if (!burst) {
                        burst = true
                        sparkles.burst(PKind.SPARKLE, 0.5f, 0.32f, 28, 0.4f, 1.8f, 0.018f, ParticleSystem.STAR_COLORS)
                        sparkles.burst(PKind.STAR, 0.5f, 0.32f, 14, 0.45f, 1.6f, 0.02f, ParticleSystem.STAR_COLORS, gravity = 0.2f)
                        sparkles.burst(PKind.CONFETTI, 0.5f, 0.32f, 40, 0.7f, 2.5f, 0.016f, ParticleSystem.CONFETTI_COLORS, gravity = 0.4f, upward = true)
                    }
                }
            }
            sparkles.update(t)
            val petSize = min(w, h) * 0.5f
            drawIntoCanvas { c ->
                val nc = c.nativeCanvas
                renderer.drawShadow(nc, w / 2f, h * 0.5f, petSize, pose, look)
                renderer.draw(nc, w / 2f, h * 0.5f, petSize, look, pose, silhouette)
                sparkles.draw(nc, w, h)
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
                .padding(bottom = 36.dp, start = 20.dp, end = 20.dp),
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .clip(RoundedCornerShape(28.dp))
                    .background(Color(0xCC120C2E))
                    .border(1.dp, Color.White.copy(alpha = 0.15f), RoundedCornerShape(28.dp))
                    .padding(horizontal = 20.dp, vertical = 18.dp),
            ) {
                Text(
                    "${state.pet?.name} ist jetzt ${to.title}!",
                    style = MaterialTheme.typography.headlineSmall,
                    color = p.gold,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(6.dp))
                Text(to.description, style = MaterialTheme.typography.bodyLarge, color = Color.White.copy(alpha = 0.9f), textAlign = TextAlign.Center)
                Text("Stufe: ${to.stage.title}", style = MaterialTheme.typography.bodyMedium, color = Color.White.copy(alpha = 0.7f))
                Spacer(Modifier.height(18.dp))
                ClayTextButton("Juhu!", onDone, emoji = "✨", color = p.gold, deep = p.goldDeep)
            }
        }
    }
}
