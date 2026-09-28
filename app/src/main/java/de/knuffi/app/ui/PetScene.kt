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
import de.knuffi.core.GameState
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import java.time.LocalDate
import java.time.LocalTime

/** Seconds since this composable entered the composition, updated every frame. */
@Composable
fun rememberFrameTime(running: Boolean = true): MutableFloatState {
    val time = remember { mutableFloatStateOf(0f) }
    LaunchedEffect(running) {
        if (!running) return@LaunchedEffect
        val offset = time.floatValue
        val start = withFrameNanos { it }
        while (isActive) {
            withFrameNanos { time.floatValue = offset + (it - start) / 1_000_000_000f }
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
    handle: SceneHandle = remember { SceneHandle() },
    onPetTap: () -> Unit = {},
    onStroke: () -> Unit = {},
) {
    val look = remember(state.pet, state.equipped) { PetLook.of(state) }
    val needs = remember(state.pet) { state.pet?.needs() ?: emptyList() }
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
    val weather = remember {
        val today = LocalDate.now()
        RoomRenderer.weatherFor(today.toEpochDay(), today.monthValue)
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
                detectDragGestures(onDragStart = { travelled = 0f }) { change, drag ->
                    val nx = change.position.x / size.width
                    val ny = change.position.y / size.height
                    d.focus(nx, ny, r.centerNX, r.centerNY)
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
        val w = size.width
        val h = size.height
        m.room = state.room
        m.hour = hourOverride ?: hour.floatValue
        m.lightsOff = state.pet?.sleeping == true
        m.poops = state.pet?.poops ?: 0
        m.darkUi = dark
        m.weather = weather
        m.look = look
        m.needs = needs
        m.showBubble = showBubble
        r.configure(d, w, h, kind)
        d.update(t, look, needs, ps, r.headNX, r.headNY)
        ps.update(t)
        drawIntoCanvas { c -> r.draw(c.nativeCanvas, w, h, kind, m, d, ps, t) }
    }
}
