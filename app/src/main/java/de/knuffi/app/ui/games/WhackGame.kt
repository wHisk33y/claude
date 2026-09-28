package de.knuffi.app.ui.games

import android.graphics.Canvas as NativeCanvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import de.knuffi.app.render.Colors
import de.knuffi.app.render.Expression
import de.knuffi.app.render.PetFrame
import de.knuffi.app.render.PetMotion
import de.knuffi.app.render.PetRenderer
import de.knuffi.app.render.PixelLayer
import de.knuffi.app.render.RenderMode
import de.knuffi.app.render.renderMode
import de.knuffi.app.ui.components.rememberHaptic
import de.knuffi.app.ui.rememberFrameTime
import de.knuffi.core.GameState
import de.knuffi.core.VisualStyle
import kotlinx.coroutines.delay
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min
import kotlin.random.Random

private class Hole {
    var kind = 0 // 0 = empty, 1 = pet, 2 = storm cloud
    var start = 0f
    var dur = 0f
    var hitAt = -10f
    var wasHit = false
}

private class WhackLogic {
    val holes = Array(9) { Hole() }
    var score by mutableIntStateOf(0)
    var timeLeft by mutableIntStateOf(DURATION.toInt())
    var over by mutableStateOf(false)
    val popups = ArrayList<Popup>()
    private var lastT = -1f
    private var elapsed = 0f
    private var nextSpawn = 0.9f
    private val rnd = Random(System.nanoTime())

    fun update(t: Float) {
        val dt = if (lastT < 0f) 0f else (t - lastT).coerceIn(0f, 0.05f)
        lastT = t
        for (p in popups) {
            p.age += dt
            p.y -= 0.1f * dt
        }
        popups.removeAll { it.age > 0.9f }
        for (h in holes) {
            if (h.kind == 0) continue
            val gone = if (h.wasHit) t - h.hitAt > 0.3f else t - h.start > h.dur
            if (gone) h.kind = 0
        }
        if (over) return
        elapsed += dt
        timeLeft = max(0, ceil(DURATION - elapsed).toInt())
        if (elapsed >= DURATION) {
            over = true
            return
        }
        val speed = 1f + elapsed / 15f
        nextSpawn -= dt
        if (nextSpawn <= 0f) {
            nextSpawn = (0.55f + rnd.nextFloat() * 0.35f) / speed
            val free = holes.indices.filter { holes[it].kind == 0 }
            if (free.isNotEmpty()) {
                val h = holes[free[rnd.nextInt(free.size)]]
                h.kind = if (rnd.nextFloat() < 0.22f) 2 else 1
                h.start = t
                h.dur = max(0.6f, 1.25f / speed + 0.1f)
                h.wasHit = false
            }
        }
    }

    fun rise(h: Hole, t: Float): Float {
        if (h.kind == 0) return 0f
        if (h.wasHit) return (1f - (t - h.hitAt) / 0.3f).coerceIn(0f, 1f)
        val a = t - h.start
        return when {
            a < 0.14f -> a / 0.14f
            a > h.dur - 0.14f -> (h.dur - a) / 0.14f
            else -> 1f
        }.coerceIn(0f, 1f)
    }

    fun tap(i: Int, t: Float, cx: Float, cy: Float): Boolean {
        val h = holes.getOrNull(i) ?: return false
        if (h.kind == 0 || h.wasHit || over || rise(h, t) < 0.3f) return false
        h.wasHit = true
        h.hitAt = t
        if (h.kind == 1) {
            score += 1
            popups += Popup(cx, cy, "+1", 0xFFFFB300.toInt())
        } else {
            score = max(0, score - 2)
            popups += Popup(cx, cy, "−2", 0xFFFF4F6D.toInt())
        }
        return true
    }

    companion object {
        const val DURATION = 30f
    }
}

private class Board(val left: Float, val top: Float, val cell: Float) {
    fun cellAt(x: Float, y: Float): Int {
        val c = ((x - left) / cell).toInt()
        val r = ((y - top) / cell).toInt()
        return if (c in 0..2 && r in 0..2 && x >= left && y >= top) r * 3 + c else -1
    }
}

private fun boardFor(w: Float, h: Float): Board {
    val size = min(w, h * 0.92f)
    return Board((w - size) / 2f, (h - size) / 2f, size / 3f)
}

@Composable
fun WhackGame(state: GameState, onFinished: (Int) -> Unit, onBack: () -> Unit) {
    val logic = remember { WhackLogic() }
    val look = remember { gameLook(state) }
    val renderer = remember { PetRenderer() }
    val frame = remember { PetFrame() }
    val pixel = remember { PixelLayer(120) }
    val time = rememberFrameTime()
    val haptic = rememberHaptic()
    val showHint by remember { derivedStateOf { time.floatValue < 2.5f } }
    val mode = state.style.renderMode
    val bgColors = when (state.style) {
        VisualStyle.KAWAII -> intArrayOf(0xFFC9F2DA.toInt(), 0xFF9FE3BD.toInt())
        VisualStyle.PIXEL -> intArrayOf(0xFF1E3B2A.toInt(), 0xFF14281D.toInt())
        VisualStyle.MINIMAL -> intArrayOf(MaterialTheme.colorScheme.surfaceVariant.toArgb(), MaterialTheme.colorScheme.secondaryContainer.toArgb())
    }
    val fill = remember { Paint(Paint.ANTI_ALIAS_FLAG) }
    val text = remember {
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textAlign = Paint.Align.CENTER
            typeface = Typeface.DEFAULT_BOLD
        }
    }
    val oval = remember { RectF() }

    LaunchedEffect(logic.over) {
        if (logic.over) {
            delay(700)
            onFinished(logic.score)
        }
    }

    fun drawBoard(bg: NativeCanvas, sprites: NativeCanvas, w: Float, h: Float, t: Float) {
        val aa = mode != RenderMode.PIXEL
        fill.isAntiAlias = aa
        text.isAntiAlias = aa
        fill.shader = null
        fill.color = bgColors[0]
        bg.drawRect(0f, 0f, w, h, fill)
        val b = boardFor(w, h)
        for (i in 0 until 9) {
            val col = i % 3
            val row = i / 3
            val cx = b.left + (col + 0.5f) * b.cell
            val holeY = b.top + (row + 0.78f) * b.cell
            val hw = b.cell * 0.36f
            val hh = b.cell * 0.11f
            fill.color = bgColors[1]
            oval.set(cx - hw * 1.15f, holeY - hh * 1.3f, cx + hw * 1.15f, holeY + hh * 1.4f)
            bg.drawOval(oval, fill)
            fill.color = 0xFF3B2A2A.toInt()
            oval.set(cx - hw, holeY - hh, cx + hw, holeY + hh)
            bg.drawOval(oval, fill)

            val hole = logic.holes[i]
            val r = logic.rise(hole, t)
            if (r <= 0f || look == null) continue
            val s = b.cell * 0.62f
            val ground = holeY + s * 0.08f + (1f - r) * s * 1.25f
            sprites.save()
            sprites.clipRect(cx - b.cell / 2f, b.top + row * b.cell - b.cell * 0.3f, cx + b.cell / 2f, holeY)
            if (hole.kind == 1) {
                PetMotion.idle(frame, look, t + i)
                frame.hop = 0f
                frame.expression = if (hole.wasHit) Expression.LOVE else Expression.EXCITED
                if (hole.wasHit) frame.squish = 0.35f
                renderer.draw(sprites, cx, ground, s, look, frame, mode)
            } else {
                text.textSize = s * 0.75f
                text.alpha = 255
                sprites.drawText("⛈️", cx, ground - s * 0.3f, text)
            }
            sprites.restore()
        }
        text.textSize = b.cell * 0.22f
        for (p in logic.popups) {
            text.color = p.color
            text.alpha = ((1f - p.age / 0.9f) * 255).toInt().coerceIn(0, 255)
            bg.drawText(p.text, p.x * w, p.y * h, text)
        }
        text.color = Colors.alpha(0xFF000000.toInt(), 1f)
    }

    Column(Modifier.fillMaxSize()) {
        GameHeader("Blitz-Tap", onBack, "⚡ ${logic.score}", "⏱ ${logic.timeLeft}")
        Box(
            Modifier
                .weight(1f)
                .fillMaxWidth(),
        ) {
            Canvas(
                Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        detectTapGestures { pos ->
                            val w = size.width.toFloat()
                            val h = size.height.toFloat()
                            val b = boardFor(w, h)
                            val i = b.cellAt(pos.x, pos.y)
                            if (i >= 0 && logic.tap(i, time.floatValue, pos.x / w, pos.y / h)) haptic()
                        }
                    },
            ) {
                val t = time.floatValue
                logic.update(t)
                val w = size.width
                val h = size.height
                drawIntoCanvas { c ->
                    val nc = c.nativeCanvas
                    if (mode == RenderMode.PIXEL) {
                        pixel.begin(w, h)
                        val lw = pixel.width.toFloat()
                        val lh = pixel.height.toFloat()
                        drawBoard(pixel.bgCanvas, pixel.spriteCanvas, lw, lh, t)
                        pixel.outlineSprites(PixelLayer.OUTLINE)
                        pixel.compositeSprites()
                        pixel.present(nc, w, h)
                    } else {
                        drawBoard(nc, nc, w, h, t)
                    }
                }
            }
            if (showHint) {
                Text(
                    "Tippe auf dein Haustier!\nAber nicht auf die Gewitterwolken ⛈️",
                    style = MaterialTheme.typography.titleMedium,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(16.dp),
                )
            }
        }
        Box(Modifier.navigationBarsPadding())
    }
}
