package de.knuffi.app.ui

import android.os.SystemClock
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.flow.MutableStateFlow
import kotlin.math.PI
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.random.Random

/** Easter egg: the whole screen fills with hearts for a few seconds (see [de.knuffi.core.HeartNames]). */
object HeartRain {
    const val SECONDS = 5f

    /** When the hearts started (uptime), 0 = none. */
    val startedAt = MutableStateFlow(0L)

    fun start() {
        startedAt.value = SystemClock.uptimeMillis()
        de.knuffi.app.sound.Sounds.play(de.knuffi.app.sound.Sfx.SPARKLE)
    }
}

private class FlyingHeart(val x: Float, val y0: Float, val delay: Float, val speed: Float, val size: Float, val phase: Float, val sway: Float, val spin: Float, val color: Color)

private val heartColors = listOf(
    Color(0xFFFF4F8B), Color(0xFFFF7EB6), Color(0xFFFF3B5C), Color(0xFFFFA8C8),
    Color(0xFFE0245E), Color(0xFFFF9AA2), Color(0xFFC86BFA), Color(0xFFFF6F91),
)

@Composable
fun HeartRainOverlay() {
    val start by HeartRain.startedAt.collectAsStateWithLifecycle()
    if (start == 0L) return
    var t by remember(start) { mutableFloatStateOf(0f) }
    LaunchedEffect(start) {
        val t0 = withFrameMillis { it }
        while (t < HeartRain.SECONDS) {
            t = (withFrameMillis { it } - t0) / 1000f
        }
    }
    if (t >= HeartRain.SECONDS) return
    val hearts = remember(start) {
        val rnd = Random(start)
        List(300) { i ->
            // half of the hearts pop up all over the screen at once, the others keep streaming up from below
            val pop = i < 150
            FlyingHeart(
                x = rnd.nextFloat(),
                y0 = if (pop) rnd.nextFloat() * 1.1f else 1.08f,
                delay = if (pop) rnd.nextFloat() * 1.1f else rnd.nextFloat().pow(1.3f) * 3.4f,
                speed = if (pop) 0.04f + rnd.nextFloat() * 0.12f else 0.4f + rnd.nextFloat() * 0.5f,
                size = 18f + rnd.nextFloat().pow(1.6f) * 72f,
                phase = rnd.nextFloat() * 6.28f,
                sway = 0.01f + rnd.nextFloat() * 0.035f,
                spin = (rnd.nextFloat() - 0.5f) * 40f,
                color = heartColors[rnd.nextInt(heartColors.size)],
            )
        }
    }
    val path = remember { Path() }
    Canvas(Modifier.fillMaxSize()) {
        val fadeIn = min(1f, t / 0.3f)
        val fadeOut = min(1f, (HeartRain.SECONDS - t) / 0.8f)
        val a = (fadeIn * fadeOut).coerceIn(0f, 1f)
        drawRect(Color(0xFFFF7EB6).copy(alpha = 0.16f * a), size = size)
        val unit = 1.dp.toPx()
        for (h in hearts) {
            val age = t - h.delay
            if (age < 0f) continue
            val y = h.y0 - age * h.speed
            if (y < -0.1f) continue
            val cx = (h.x + sin(t * 1.6f + h.phase) * h.sway) * size.width
            val cy = y * size.height
            val s = h.size * unit * (1f + 0.08f * sin(t * 6f + h.phase))
            // a little pop when a heart appears
            val pop = min(1f, age / 0.25f)
            val sc = s * (0.6f + 0.4f * pop)
            rotate(h.spin + sin(t * 2f + h.phase) * 8f, Offset(cx, cy)) {
                path.reset()
                path.moveTo(cx, cy + sc * 0.42f)
                path.cubicTo(cx - sc * 0.95f, cy - sc * 0.2f, cx - sc * 0.5f, cy - sc * 0.95f, cx, cy - sc * 0.42f)
                path.cubicTo(cx + sc * 0.5f, cy - sc * 0.95f, cx + sc * 0.95f, cy - sc * 0.2f, cx, cy + sc * 0.42f)
                path.close()
                drawPath(path, h.color.copy(alpha = 0.92f * a))
                drawOval(
                    Color.White.copy(alpha = 0.45f * a),
                    topLeft = Offset(cx - sc * 0.5f, cy - sc * 0.62f),
                    size = Size(sc * 0.26f, sc * 0.18f),
                )
            }
        }
        // a big heart in the middle that beats
        val beat = 1f + 0.12f * sin(t * 2f * PI.toFloat() * 1.4f).coerceAtLeast(0f)
        val bs = min(size.width, size.height) * 0.22f * beat * min(1f, t / 0.5f)
        val bx = size.width / 2f
        val by = size.height * 0.45f
        path.reset()
        path.moveTo(bx, by + bs * 0.42f)
        path.cubicTo(bx - bs * 0.95f, by - bs * 0.2f, bx - bs * 0.5f, by - bs * 0.95f, bx, by - bs * 0.42f)
        path.cubicTo(bx + bs * 0.5f, by - bs * 0.95f, bx + bs * 0.95f, by - bs * 0.2f, bx, by + bs * 0.42f)
        path.close()
        drawPath(path, Color(0xFFFF3B6E).copy(alpha = 0.95f * a))
        drawOval(Color.White.copy(alpha = 0.5f * a), topLeft = Offset(bx - bs * 0.52f, by - bs * 0.64f), size = Size(bs * 0.28f, bs * 0.2f))
    }
}
