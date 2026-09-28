package de.knuffi.app.ui

import android.graphics.Paint
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.knuffi.app.data.GameRepository
import de.knuffi.app.render.PetLook
import de.knuffi.app.render.PetPose
import de.knuffi.app.render.PetRenderer
import de.knuffi.app.ui.components.ClayTextButton
import de.knuffi.app.ui.components.CoinPill
import de.knuffi.app.ui.components.GlossyBar
import de.knuffi.app.ui.components.Pill
import de.knuffi.app.ui.components.ScreenHeader
import de.knuffi.app.ui.components.SurfaceCard
import de.knuffi.app.ui.components.staggered
import de.knuffi.app.ui.theme.LocalPalette
import de.knuffi.core.Engine
import de.knuffi.core.GameState
import de.knuffi.core.MiniGame
import de.knuffi.core.Mood
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

private data class GameTheme(val top: Color, val bottom: Color, val button: Color, val buttonDeep: Color)

private fun themeFor(game: MiniGame) = when (game) {
    MiniGame.CATCH -> GameTheme(Color(0xFF7FD3FF), Color(0xFFB9F0C8), Color(0xFFFF9A4D), Color(0xFFDB6F26))
    MiniGame.MEMORY -> GameTheme(Color(0xFFB9A8FF), Color(0xFFFFC2E0), Color(0xFF8C7BFF), Color(0xFF6150D6))
    MiniGame.WHACK -> GameTheme(Color(0xFF6FD69A), Color(0xFFFFE08A), Color(0xFF34C99F), Color(0xFF1E9C78))
}

@Composable
fun GamesScreen(state: GameState, onStart: (MiniGame) -> Unit) {
    val p = LocalPalette.current
    val blocker = Engine.gameBlocker(state)
    val pet = state.pet
    val look = PetLook.of(state)?.copy(mood = if (blocker == null) Mood.HAPPY else Mood.OKAY, sleeping = false)
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
    ) {
        ScreenHeader("Spielhalle", "🎮", "Spielen macht glücklich und bringt Münzen.") { CoinPill(state.coins) }
        Spacer(Modifier.height(8.dp))
        SurfaceCard(Modifier.fillMaxWidth().staggered(0), color = if (p.dark) p.surfaceAlt else Color(0xFFFFEAF4)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                PetPortrait(look, Modifier.size(92.dp), sizeFactor = 0.8f) { pose, t ->
                    if (blocker == null) {
                        val ph = t % 1.6f
                        if (ph < 0.5f) pose.lift = sin(ph / 0.5f * Math.PI.toFloat()) * 0.18f
                        pose.armL = 0.7f + 0.3f * sin(t * 6f)
                        pose.armR = 0.7f
                    }
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        if (blocker == null) "${pet?.name} will spielen!" else "Gerade nicht …",
                        style = MaterialTheme.typography.titleMedium,
                        color = p.text,
                    )
                    Text(
                        blocker ?: "Jede Runde: 😊 +12, ⚡ −10, dazu Münzen und XP.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = p.textMuted,
                    )
                    if (pet != null && !pet.isEgg) {
                        Spacer(Modifier.height(8.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("⚡", fontSize = 14.sp)
                            Spacer(Modifier.width(6.dp))
                            GlossyBar((pet.energy / 100.0).toFloat(), p.energy, p.goldDeep, Modifier.weight(1f), height = 10.dp)
                        }
                    }
                }
            }
        }
        for ((i, game) in MiniGame.entries.withIndex()) {
            Spacer(Modifier.height(14.dp))
            GameCard(state, game, look, blocker, Modifier.staggered(i + 1)) { onStart(game) }
        }
        Spacer(Modifier.height(bottomBarSpace()))
    }
}

@Composable
private fun GameCard(state: GameState, game: MiniGame, look: PetLook?, blocker: String?, modifier: Modifier = Modifier, onStart: () -> Unit) {
    val p = LocalPalette.current
    val theme = themeFor(game)
    val best = when (game) {
        MiniGame.CATCH -> state.counters.bestCatch.takeIf { it > 0 }?.let { "🏅 $it Punkte" }
        MiniGame.MEMORY -> state.counters.bestMemoryMoves.takeIf { it > 0 }?.let { "🏅 $it Züge" }
        MiniGame.WHACK -> state.counters.bestWhack.takeIf { it > 0 }?.let { "🏅 $it Treffer" }
    }
    val maxCoins = when (game) {
        MiniGame.CATCH -> 60
        MiniGame.MEMORY -> 40
        MiniGame.WHACK -> 50
    }
    val start = { if (blocker == null) onStart() else GameRepository.message(blocker) }
    SurfaceCard(modifier.fillMaxWidth(), onClick = start, contentPadding = PaddingValues(0.dp)) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(150.dp)
                .clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)),
        ) {
            GamePreview(game, look, theme, Modifier.fillMaxSize())
            Text(
                game.emoji,
                fontSize = 22.sp,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(10.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .padding(2.dp),
            )
        }
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(game.title, style = MaterialTheme.typography.titleLarge, color = p.text)
                    Text(game.description, style = MaterialTheme.typography.bodyMedium, color = p.textMuted)
                }
            }
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Pill("bis $maxCoins 🪙", color = p.gold.copy(alpha = 0.22f))
                if (best != null) Pill(best)
                Spacer(Modifier.weight(1f))
                ClayTextButton("Los", start, emoji = "▶", color = theme.button, deep = theme.buttonDeep, small = true, enabled = blocker == null)
            }
        }
    }
}

/** Little animated scenes that show what each game is about. */
@Composable
private fun GamePreview(game: MiniGame, look: PetLook?, theme: GameTheme, modifier: Modifier = Modifier) {
    val time = rememberFrameTime()
    val renderer = remember { PetRenderer() }
    val pose = remember { PetPose() }
    val emoji = remember { Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER } }
    Canvas(modifier) {
        val t = time.floatValue
        drawRect(Brush.verticalGradient(listOf(theme.top, theme.bottom)))
        // soft bokeh circles
        for (i in 0 until 6) {
            val x = size.width * ((i * 0.19f + t * 0.01f * (i % 3 + 1)) % 1.1f)
            val y = size.height * (0.15f + 0.12f * (i % 3))
            drawCircle(Color.White.copy(alpha = 0.18f), radius = size.height * (0.08f + 0.03f * (i % 2)), center = Offset(x, y))
        }
        when (game) {
            MiniGame.CATCH -> drawCatch(t, look, renderer, pose, emoji)
            MiniGame.MEMORY -> drawMemory(t, emoji)
            MiniGame.WHACK -> drawWhack(t, look, renderer, pose, emoji)
        }
    }
}

private fun DrawScope.emojiAt(paint: Paint, text: String, x: Float, y: Float, size: Float) {
    paint.textSize = size
    drawIntoCanvas { it.nativeCanvas.drawText(text, x, y + size * 0.35f, paint) }
}

private fun DrawScope.drawPet(renderer: PetRenderer, pose: PetPose, look: PetLook?, x: Float, ground: Float, s: Float, t: Float, tweak: PetPose.() -> Unit = {}) {
    val l = look ?: return
    pose.defaults(l, t)
    pose.tweak()
    drawIntoCanvas {
        renderer.drawShadow(it.nativeCanvas, x, ground, s, pose, l)
        renderer.draw(it.nativeCanvas, x, ground, s, l, pose)
    }
}

private fun DrawScope.drawCatch(t: Float, look: PetLook?, renderer: PetRenderer, pose: PetPose, paint: Paint) {
    val w = size.width
    val h = size.height
    // grass
    drawRoundRect(Color(0xFF7CCB6B), topLeft = Offset(-20f, h * 0.82f), size = Size(w + 40f, h * 0.4f), cornerRadius = CornerRadius(40f))
    val foods = listOf("🍎", "🍓", "🍩", "🍌")
    for (i in foods.indices) {
        val speed = 0.35f + 0.1f * i
        val prog = ((t * speed + i * 0.27f) % 1f)
        val x = w * (0.18f + 0.22f * i)
        emojiAt(paint, foods[i], x + sin(t * 2f + i) * 6f, -h * 0.1f + prog * h * 0.95f, h * 0.17f)
    }
    val px = w * (0.5f + 0.32f * sin(t * 1.3f))
    drawPet(renderer, pose, look, px, h * 0.92f, h * 0.48f, t) {
        armL = 1f
        armR = 1f
        mouth = de.knuffi.app.render.MouthShape.OPEN
        lookY = -1f
        strideL = sin(t * 9f) * 0.6f
        strideR = -strideL
        turn = cos(t * 1.3f) * 0.5f
    }
}

private fun DrawScope.drawMemory(t: Float, paint: Paint) {
    val w = size.width
    val h = size.height
    val faces = listOf("🍎", "⭐", "🍎", "💎", "⭐", "💎")
    val cw = h * 0.36f
    val ch = h * 0.34f
    val gap = h * 0.06f
    val totalW = 3 * cw + 2 * gap
    val x0 = (w - totalW) / 2f
    val y0 = (h - (2 * ch + gap)) / 2f
    for (i in 0 until 6) {
        val col = i % 3
        val row = i / 3
        val cx = x0 + col * (cw + gap) + cw / 2f
        val cy = y0 + row * (ch + gap) + ch / 2f
        val phase = ((t * 0.5f + i * 0.17f) % 2f)
        val open = phase in 0.5f..1.3f
        val flip = when {
            phase < 0.4f -> 1f
            phase < 0.5f -> abs(cos((phase - 0.4f) / 0.1f * Math.PI.toFloat() / 2f))
            phase < 1.3f -> abs(sin(((phase - 0.5f) / 0.1f).coerceAtMost(1f) * Math.PI.toFloat() / 2f))
            phase < 1.4f -> abs(cos((phase - 1.3f) / 0.1f * Math.PI.toFloat() / 2f))
            else -> abs(sin(((phase - 1.4f) / 0.1f).coerceAtMost(1f) * Math.PI.toFloat() / 2f))
        }
        val bob = sin(t * 2f + i) * 2f
        withTransform({ scale(flip.coerceAtLeast(0.04f), 1f, Offset(cx, cy)) }) {
            drawRoundRect(Color(0x33000000), topLeft = Offset(cx - cw / 2f, cy - ch / 2f + 5f + bob), size = Size(cw, ch), cornerRadius = CornerRadius(cw * 0.18f))
            if (open) {
                drawRoundRect(Color.White, topLeft = Offset(cx - cw / 2f, cy - ch / 2f + bob), size = Size(cw, ch), cornerRadius = CornerRadius(cw * 0.18f))
                emojiAt(paint, faces[i], cx, cy + bob, ch * 0.5f)
            } else {
                drawRoundRect(
                    Brush.linearGradient(listOf(Color(0xFFFF7AB6), Color(0xFF8C7BFF)), start = Offset(cx - cw / 2f, cy - ch / 2f), end = Offset(cx + cw / 2f, cy + ch / 2f)),
                    topLeft = Offset(cx - cw / 2f, cy - ch / 2f + bob), size = Size(cw, ch), cornerRadius = CornerRadius(cw * 0.18f),
                )
                drawCircle(Color.White.copy(alpha = 0.35f), radius = cw * 0.16f, center = Offset(cx, cy + bob))
                drawRoundRect(Color.White.copy(alpha = 0.3f), topLeft = Offset(cx - cw * 0.35f, cy - ch * 0.42f + bob), size = Size(cw * 0.7f, ch * 0.1f), cornerRadius = CornerRadius(ch))
            }
        }
    }
}

private fun DrawScope.drawWhack(t: Float, look: PetLook?, renderer: PetRenderer, pose: PetPose, paint: Paint) {
    val w = size.width
    val h = size.height
    drawRoundRect(Color(0xFF6CC45A), topLeft = Offset(-20f, h * 0.55f), size = Size(w + 40f, h * 0.6f), cornerRadius = CornerRadius(60f))
    val holes = floatArrayOf(0.2f, 0.5f, 0.8f)
    val active = ((t / 1.1f).toInt()) % 3
    val ph = (t % 1.1f) / 1.1f
    val rise = when {
        ph < 0.25f -> ph / 0.25f
        ph < 0.7f -> 1f
        ph < 0.9f -> 1f - (ph - 0.7f) / 0.2f
        else -> 0f
    }
    for ((i, hx) in holes.withIndex()) {
        val cx = w * hx
        val cy = h * 0.8f
        val rw = h * 0.3f
        val rh = h * 0.1f
        drawOval(Color(0xFF8A5A3C), topLeft = Offset(cx - rw * 1.1f, cy - rh * 1.2f), size = Size(rw * 2.2f, rh * 2.6f))
        drawOval(Color(0xFF3A2418), topLeft = Offset(cx - rw, cy - rh), size = Size(rw * 2f, rh * 2f))
        if (i == active && rise > 0f) {
            val s = h * 0.5f
            clipRect(0f, 0f, w, cy) {
                drawPet(renderer, pose, look, cx, cy + s * 0.55f * (1f - rise) + s * 0.08f, s, t) {
                    eyes = de.knuffi.app.render.EyeShape.HAPPY
                    mouth = de.knuffi.app.render.MouthShape.GRIN
                    armL = 1f
                    armR = 1f
                }
            }
            if (ph in 0.35f..0.6f) emojiAt(paint, "💥", cx + rw * 0.8f, cy - s * 0.9f, h * 0.2f)
        }
    }
    // lightning cloud drifting over
    val cloudX = w * ((t * 0.08f) % 1.3f) - w * 0.15f
    emojiAt(paint, "⛈️", cloudX, h * 0.2f, h * 0.22f)
}
