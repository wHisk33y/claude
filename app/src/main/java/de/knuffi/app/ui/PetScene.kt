package de.knuffi.app.ui

import android.graphics.Canvas as NativeCanvas
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.runtime.Composable
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
import de.knuffi.app.data.GameRepository
import de.knuffi.app.render.FlatColors
import de.knuffi.app.render.ParticleSystem
import de.knuffi.app.render.PetFrame
import de.knuffi.app.render.PetLook
import de.knuffi.app.render.PetMotion
import de.knuffi.app.render.PetRenderer
import de.knuffi.app.render.PixelLayer
import de.knuffi.app.render.RenderMode
import de.knuffi.app.render.SceneAnimator
import de.knuffi.app.render.SceneRenderer
import de.knuffi.app.render.renderMode
import de.knuffi.app.ui.theme.LocalTokens
import de.knuffi.core.GameState
import de.knuffi.core.VisualStyle
import kotlinx.coroutines.isActive
import kotlin.math.min

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

/** Draws a pet on its own (no room) with pixel art support. */
class PetStage(pixelTarget: Int = 64) {
    val renderer = PetRenderer()
    private val pixel = PixelLayer(pixelTarget)

    fun draw(
        c: NativeCanvas,
        w: Float,
        h: Float,
        look: PetLook,
        f: PetFrame,
        mode: RenderMode,
        silhouette: Int? = null,
        sizeFactor: Float = 0.72f,
        groundFactor: Float = 0.95f,
        shadow: Boolean = true,
        centerX: Float = 0.5f,
    ) {
        if (mode == RenderMode.PIXEL) {
            pixel.begin(w, h)
            val lw = pixel.width.toFloat()
            val lh = pixel.height.toFloat()
            val s = min(lw, lh) * sizeFactor
            if (shadow) renderer.drawShadow(pixel.bgCanvas, lw * centerX, lh * groundFactor, s, f, look, mode)
            renderer.draw(pixel.spriteCanvas, lw * centerX, lh * groundFactor, s, look, f, mode, silhouette)
            pixel.outlineSprites(PixelLayer.OUTLINE)
            pixel.compositeSprites()
            pixel.present(c, w, h)
        } else {
            val s = min(w, h) * sizeFactor
            if (shadow) renderer.drawShadow(c, w * centerX, h * groundFactor, s, f, look, mode)
            renderer.draw(c, w * centerX, h * groundFactor, s, look, f, mode, silhouette)
        }
    }
}

@Composable
fun PetPortrait(
    look: PetLook?,
    style: VisualStyle,
    modifier: Modifier = Modifier,
    animated: Boolean = true,
    pixelTarget: Int = 60,
    tweak: (PetFrame, Float) -> Unit = { _, _ -> },
) {
    val stage = remember(pixelTarget) { PetStage(pixelTarget) }
    val frame = remember { PetFrame() }
    val time = rememberFrameTime(animated)
    Canvas(modifier) {
        val t = time.floatValue
        val l = look ?: return@Canvas
        PetMotion.idle(frame, l, t)
        tweak(frame, t)
        drawIntoCanvas { c -> stage.draw(c.nativeCanvas, size.width, size.height, l, frame, style.renderMode) }
    }
}

/**
 * The living room: background, pet, poops, particles, day/night. Reacts to game events
 * and to touches (tap = pet, swipe over the pet = stroke).
 */
@Composable
fun PetScene(
    state: GameState,
    modifier: Modifier = Modifier,
    style: VisualStyle = state.style,
    interactive: Boolean = true,
    walking: Boolean = true,
    hourOverride: Float? = null,
    onPetTap: () -> Unit = {},
    onStroke: () -> Unit = {},
) {
    val look = remember(state.pet, state.equipped) { PetLook.of(state) }
    val mode = style.renderMode
    val renderer = remember { SceneRenderer() }
    val particles = remember { ParticleSystem() }
    val animator = remember { SceneAnimator() }
    val pixel = remember { PixelLayer(132) }
    val flat: FlatColors = LocalTokens.current.flatColors
    val time = rememberFrameTime()
    val tap by rememberUpdatedState(onPetTap)
    val stroke by rememberUpdatedState(onStroke)
    val isEgg = look?.form == de.knuffi.core.Form.EGG
    val eggState by rememberUpdatedState(isEgg)

    LaunchedEffect(Unit) {
        GameRepository.events.collect { animator.onEvent(it, time.floatValue, particles) }
    }

    val input = if (interactive) {
        Modifier
            .pointerInput(Unit) {
                detectTapGestures { pos ->
                    val nx = pos.x / size.width
                    val ny = pos.y / size.height
                    val t = time.floatValue
                    if (animator.hitPet(nx, ny)) {
                        if (eggState) animator.onEggTap(t, particles) else animator.onTap(t)
                        tap()
                    } else {
                        animator.lookAt(nx, ny, t)
                    }
                }
            }
            .pointerInput(Unit) {
                var travelled = 0f
                detectDragGestures(onDragStart = { travelled = 0f }) { change, drag ->
                    val nx = change.position.x / size.width
                    val ny = change.position.y / size.height
                    animator.lookAt(nx, ny, time.floatValue)
                    if (animator.hitPet(nx, ny) && !eggState) {
                        travelled += drag.getDistance()
                        if (travelled > 110f) {
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
        animator.scene.room = state.room
        animator.scene.poops = state.pet?.poops ?: 0
        animator.scene.lightsOff = state.pet?.sleeping == true
        animator.scene.flat = flat
        animator.hourOverride = hourOverride
        animator.update(t, look, particles, walking)
        particles.update(t)
        val scene = animator.scene
        val f = animator.frame
        drawIntoCanvas { canvas ->
            val nc = canvas.nativeCanvas
            if (mode == RenderMode.PIXEL) {
                pixel.begin(w, h)
                val lw = pixel.width.toFloat()
                val lh = pixel.height.toFloat()
                renderer.drawBackground(pixel.bgCanvas, lw, lh, scene, f, t, mode)
                renderer.drawSprites(pixel.spriteCanvas, lw, lh, scene, f, t, mode)
                pixel.outlineSprites(PixelLayer.OUTLINE)
                pixel.compositeSprites()
                renderer.drawOverlay(pixel.bgCanvas, lw, lh, scene, t, mode, particles)
                pixel.present(nc, w, h)
                val b = renderer.petBounds
                animator.setBounds(b.left / lw, b.top / lh, b.right / lw, b.bottom / lh)
            } else {
                renderer.drawBackground(nc, w, h, scene, f, t, mode)
                renderer.drawSprites(nc, w, h, scene, f, t, mode)
                renderer.drawOverlay(nc, w, h, scene, t, mode, particles)
                val b = renderer.petBounds
                animator.setBounds(b.left / w, b.top / h, b.right / w, b.bottom / h)
            }
        }
    }
}
