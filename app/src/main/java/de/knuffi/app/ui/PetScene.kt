package de.knuffi.app.ui

import android.graphics.Typeface
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableFloatState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.res.ResourcesCompat
import de.knuffi.app.R
import de.knuffi.app.data.GameRepository
import de.knuffi.app.debug.DebugScenes
import de.knuffi.app.render.Furnishing
import de.knuffi.app.render.LayoutKind
import de.knuffi.app.render.ParticleSystem
import de.knuffi.app.render.PetDirector
import de.knuffi.app.render.PetLook
import de.knuffi.app.render.PetPose
import de.knuffi.app.render.PetRenderer
import de.knuffi.app.render.RoomRenderer
import de.knuffi.app.render.SceneModel
import de.knuffi.app.render.SceneRenderer
import de.knuffi.app.ui.theme.LocalPalette
import de.knuffi.core.Action
import de.knuffi.core.Engine
import de.knuffi.core.GameState
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/** Speed of game time (the "Spieltempo" setting). 1 = normal. */
val LocalTimeScale = androidx.compose.runtime.staticCompositionLocalOf { 1f }

/** Seconds since this composable entered the composition, updated every frame. */
@Composable
fun rememberFrameTime(running: Boolean = true): MutableFloatState {
    val time = remember { mutableFloatStateOf(0f) }
    val scale = LocalTimeScale.current
    LaunchedEffect(running, scale) {
        if (!running) return@LaunchedEffect
        val offset = time.floatValue
        val start = withFrameNanos { it }
        while (isActive) {
            withFrameNanos { time.floatValue = offset + (it - start) / 1_000_000_000f * scale }
        }
    }
    return time
}

/** Fredoka for canvas text (speech bubbles, particles). */
@Composable
fun rememberCanvasTypeface(): Typeface {
    val context = LocalContext.current
    return remember {
        runCatching { ResourcesCompat.getFont(context, R.font.fredoka_semibold) }.getOrNull() ?: Typeface.DEFAULT_BOLD
    }
}

/** A pet on its own (no room), e.g. for cards, the shop and dialogs. */
@Composable
fun PetPortrait(
    look: PetLook?,
    modifier: Modifier = Modifier,
    animated: Boolean = true,
    sizeFactor: Float = 0.72f,
    groundFactor: Float = 0.93f,
    shadow: Boolean = true,
    silhouette: Int? = null,
    tweak: (PetPose, Float) -> Unit = { _, _ -> },
) {
    val renderer = remember { PetRenderer() }
    val pose = remember { PetPose() }
    val time = rememberFrameTime(animated)
    Canvas(modifier) {
        val l = look ?: return@Canvas
        val t = time.floatValue
        pose.defaults(l, t)
        // Gentle blinking even without a director.
        val blink = (t + 0.7f) % 3.7f
        if (blink < 0.14f && !l.sleeping) pose.eyeOpen = 1f - kotlin.math.sin(blink / 0.14f * Math.PI.toFloat())
        tweak(pose, t)
        drawIntoCanvas { c ->
            renderer.drawStandalone(c.nativeCanvas, size.width, size.height, l, pose, sizeFactor, groundFactor, shadow, silhouette)
        }
    }
}

/** Everything a living scene needs; hoisted so debug code can script the pet. */
class SceneHandle {
    val director = PetDirector()
    val guest = PetDirector(5).apply { place(0.7f); toyConfig.ladder = 0; toyConfig.planes = 0; toyConfig.paint = 0; toyConfig.footprints = 0 }

    val friends = de.knuffi.app.render.FriendPlay()

    /** Scene time of the last frame (for photos). */
    var lastT = 0f
    val particles = ParticleSystem()
    val renderer = SceneRenderer()
    val model = SceneModel()
}

private fun currentHour(): Float {
    val now = LocalTime.now()
    return now.hour + now.minute / 60f
}

/**
 * The living room: room, weather, day/night, props, the pet with its behaviour, particles
 * and speech bubbles. Tap the pet to poke it, swipe over it to stroke, tap elsewhere and it
 * looks (and maybe walks) there.
 */
@Composable
fun PetScene(
    state: GameState,
    modifier: Modifier = Modifier,
    kind: LayoutKind = LayoutKind.HOME,
    interactive: Boolean = true,
    hourOverride: Float? = null,
    showBubble: Boolean = true,
    /** Height in px covered by UI at the top (e.g. the HUD). */
    topInsetPx: Int = 0,
    handle: SceneHandle = remember { SceneHandle() },
    onPetTap: () -> Unit = {},
    onStroke: () -> Unit = {},
) {
    val look = remember(state.pet, state.equipped) { PetLook.of(state) }
    val needs = remember(state.pet) { state.pet?.needs() ?: emptyList() }
    val furniture = remember(state.equipped, state.settings.look) {
        Furnishing.of(state, DebugScenes.eventOverride ?: Engine.activeEvent(System.currentTimeMillis(), ZoneId.systemDefault()))
    }
    val dark = LocalPalette.current.dark
    val typeface = rememberCanvasTypeface()
    val time = rememberFrameTime()
    val tap by rememberUpdatedState(onPetTap)
    val stroke by rememberUpdatedState(onStroke)
    val lookState by rememberUpdatedState(look)
    val d = handle.director
    val ps = handle.particles
    val r = handle.renderer
    val m = handle.model
    val hour = remember { mutableFloatStateOf(currentHour()) }
    val weather = remember(state.weather, state.settings.weatherMode, hour.floatValue.toInt()) {
        RoomRenderer.fromCore(de.knuffi.core.Weather.current(state, System.currentTimeMillis(), ZoneId.systemDefault()).kind)
    }
    val hiddenItem = remember(state.daily.hiddenFound, state.daily.day) {
        if (state.daily.hiddenFound || state.pet?.isEgg != false) null else de.knuffi.core.Hidden.today(state.daily.day)
    }
    val taps = remember { HashMap<String, MutableList<Long>>() }
    val combo = remember { ArrayDeque<Pair<String, Long>>() }
    fun tapped(key: String, n: Int, withinMs: Long): Boolean {
        val now = System.currentTimeMillis()
        val list = taps.getOrPut(key) { mutableListOf() }
        list += now
        list.removeAll { now - it > withinMs }
        if (list.size >= n) {
            list.clear()
            return true
        }
        return false
    }
    val guestLook = remember(state.visit, state.resting, state.equipped) {
        state.visit?.let { v -> state.resting.firstOrNull { it.id == v.petId } }?.let { PetLook.of(it, state).copy(hat = null, face = null, neck = null, back = null, sleeping = false, mood = de.knuffi.core.Mood.HAPPY) }
    }

    LaunchedEffect(typeface) {
        r.setTypeface(typeface)
        ps.typeface = typeface
    }
    LaunchedEffect(Unit) {
        while (isActive) {
            delay(30_000)
            hour.floatValue = currentHour()
        }
    }
    LaunchedEffect(Unit) {
        GameRepository.events.collect { d.onEvent(it, ps, r.headNX, r.headNY) }
    }
    LaunchedEffect(interactive) {
        // Only the real, touchable scene hands out rewards for the screen toys.
        d.onToy = if (interactive && !GameRepository.previewMode) { kind -> GameRepository.perform(Action.Toy(kind)) } else null
        d.onSound = if (interactive) { sfx -> de.knuffi.app.sound.Sounds.play(sfx) } else null
        handle.friends.onMoment = if (interactive && !GameRepository.previewMode) { m -> GameRepository.perform(Action.FriendPlay(m)) } else null
    }
    DisposableEffect(Unit) {
        onDispose { r.release() }
    }

    fun hitPet(x: Float, y: Float): Boolean {
        val b = r.petBounds
        if (b.isEmpty) return false
        val pad = b.width() * 0.15f
        return x >= b.left - pad && x <= b.right + pad && y >= b.top - pad && y <= b.bottom + pad
    }

    val input = if (interactive) {
        Modifier
            .pointerInput(Unit) {
                detectTapGestures { pos ->
                    val nx = pos.x / size.width
                    val ny = pos.y / size.height
                    if (d.tapToys(pos.x, pos.y, ps, size.width.toFloat(), size.height.toFloat())) return@detectTapGestures
                    if (interactive && r.hitHidden(pos.x, pos.y)) {
                        val h = de.knuffi.core.Hidden.today(GameRepository.current.daily.day)
                        ps.burst(de.knuffi.app.render.PKind.SPARKLE, nx, ny, 12, 0.3f, 1f, 0.025f, de.knuffi.app.render.ParticleSystem.SPARKLE_COLORS)
                        d.celebrate("Du hast ${h.name} gefunden! ${h.emoji}")
                        GameRepository.perform(Action.FindHidden)
                        return@detectTapGestures
                    }
                    if (interactive && hitPet(pos.x, pos.y) && d.onLadderTop) {
                        d.celebrate("Gib mir fünf! 🙌")
                        GameRepository.perform(Action.FindSecret(de.knuffi.core.Secret.HIGHFIVE))
                    }
                    if (interactive && !hitPet(pos.x, pos.y)) {
                        val hourNow = hourOverride ?: hour.floatValue
                        val dark = hourNow >= 20f || hourNow < 6f
                        val obj = r.hitObject(pos.x, pos.y, state.room)
                        if (obj != null) {
                            // easter egg: window, lamp, picture in a row unlocks kittens
                            val nowMs = System.currentTimeMillis()
                            combo.addLast(obj to nowMs)
                            while (combo.size > 3) combo.removeFirst()
                            if (combo.map { it.first } == de.knuffi.core.Secrets.catCombo && nowMs - combo.first().second < 6000L) {
                                combo.clear()
                                d.celebrate(if (state.catMode) "Miau! Du kennst unser Geheimzeichen! 😸" else "Miau? Da maunzt doch was … 🐈")
                                ps.burst(de.knuffi.app.render.PKind.HEART, nx, ny, 12, 0.4f, 1.2f, 0.025f, de.knuffi.app.render.ParticleSystem.HEART_COLORS)
                                GameRepository.perform(Action.FindSecret(de.knuffi.core.Secret.CATS))
                            }
                        }
                        when (obj) {
                            "picture" -> if (tapped("picture", 5, 4000L)) {
                                d.celebrate("Das Bild hat mir zugezwinkert! 🖼️✨")
                                ps.burst(de.knuffi.app.render.PKind.STAR, nx, ny, 10, 0.3f, 1f, 0.025f, de.knuffi.app.render.ParticleSystem.STAR_COLORS)
                                GameRepository.perform(Action.FindSecret(de.knuffi.core.Secret.PICTURE))
                            }
                            "window" -> if (dark) {
                                d.celebrate("Eine Sternschnuppe! Wünsch dir was! 🌠")
                                ps.burst(de.knuffi.app.render.PKind.STAR, nx, ny, 14, 0.5f, 1.2f, 0.02f, de.knuffi.app.render.ParticleSystem.STAR_COLORS, gravity = 0.2f)
                                GameRepository.perform(Action.FindSecret(de.knuffi.core.Secret.WISH))
                            }
                            "lamp" -> if (dark && tapped("lamp", 5, 4000L)) {
                                d.celebrate("Disco! 🪩🎶")
                                ps.burst(de.knuffi.app.render.PKind.CONFETTI, nx, ny, 40, 0.6f, 2f, 0.02f, de.knuffi.app.render.ParticleSystem.CONFETTI_COLORS, gravity = 0.4f)
                                GameRepository.perform(Action.FindSecret(de.knuffi.core.Secret.DISCO))
                            }
                        }
                    }
                    if (d.toys.splashes.isNotEmpty()) d.wipe(pos.x, pos.y, 0.34f, ps, size.width.toFloat(), size.height.toFloat())
                    if (hitPet(pos.x, pos.y)) {
                        if (lookState?.isEgg == true) d.onEggTap(ps, nx, ny) else d.onTap()
                        tap()
                    } else {
                        d.focus(nx, ny, r.centerNX, r.centerNY)
                        if (ny > 0.55f) d.pointAt(nx)
                    }
                }
            }
            .pointerInput(Unit) {
                var travelled = 0f
                var lastAngle = Float.NaN
                var spin = 0f
                detectDragGestures(onDragStart = {
                    travelled = 0f
                    lastAngle = Float.NaN
                    spin = 0f
                }) { change, drag ->
                    // circles around the pet make it dizzy (a secret)
                    val b = r.petBounds
                    if (interactive && !b.isEmpty) {
                        val a = kotlin.math.atan2(change.position.y - b.centerY(), change.position.x - b.centerX())
                        if (!lastAngle.isNaN()) {
                            var da = a - lastAngle
                            if (da > Math.PI) da -= (2 * Math.PI).toFloat()
                            if (da < -Math.PI) da += (2 * Math.PI).toFloat()
                            spin += da
                            if (kotlin.math.abs(spin) > 3f * 2f * Math.PI.toFloat()) {
                                spin = 0f
                                d.dizzy()
                                GameRepository.perform(Action.FindSecret(de.knuffi.core.Secret.DIZZY))
                            }
                        }
                        lastAngle = a
                    }
                    val nx = change.position.x / size.width
                    val ny = change.position.y / size.height
                    d.focus(nx, ny, r.centerNX, r.centerNY)
                    if (d.toys.splashes.isNotEmpty()) {
                        d.wipe(change.position.x, change.position.y, drag.getDistance() / (minOf(size.width, size.height) * 0.5f), ps, size.width.toFloat(), size.height.toFloat())
                    }
                    if (hitPet(change.position.x, change.position.y) && lookState?.isEgg != true) {
                        travelled += drag.getDistance()
                        if (travelled > 120f) {
                            travelled = 0f
                            stroke()
                        }
                    }
                }
            }
    } else Modifier

    Canvas(modifier.then(input)) {
        val t = time.floatValue
        handle.lastT = t
        val w = size.width
        val h = size.height
        m.room = state.room
        m.furniture = furniture
        m.hour = hourOverride ?: hour.floatValue
        m.lightsOff = state.pet?.sleeping == true
        m.poops = state.pet?.poops ?: 0
        m.darkUi = dark
        m.weather = weather
        m.look = look
        m.needs = needs
        m.showBubble = showBubble
        m.safeTop = topInsetPx.toFloat()
        m.guest = guestLook
        m.hidden = if (interactive) hiddenItem else null
        m.nameTag = if (kind == LayoutKind.WALLPAPER && state.settings.showNameTag) state.pet?.name else null
        d.toyConfig.from(state.settings)
        r.configure(d, w, h, kind)
        d.update(t, look, needs, ps, r.headNX, r.headNY)
        if (guestLook != null) {
            handle.guest.update(t + 3.7f, guestLook, emptyList(), ps, r.headNX, r.headNY)
            val pet = state.pet
            val v = state.visit
            val level = if (pet != null && v != null) de.knuffi.core.Friends.level(state.friendship[de.knuffi.core.Friends.key(pet.id, v.petId)] ?: 0) else 0
            handle.friends.update(t, d, handle.guest, level, ps)
        } else {
            handle.friends.reset()
        }
        ps.update(t)
        drawIntoCanvas { c -> r.draw(c.nativeCanvas, w, h, kind, m, d, ps, t, handle.guest, handle.friends) }
    }
}
