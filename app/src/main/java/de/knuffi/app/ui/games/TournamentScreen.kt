package de.knuffi.app.ui.games

import android.graphics.Canvas as NativeCanvas
import android.graphics.Color as AColor
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.knuffi.app.data.GameRepository
import de.knuffi.app.render.Colors
import de.knuffi.app.render.EyeShape
import de.knuffi.app.render.MouthShape
import de.knuffi.app.render.PKind
import de.knuffi.app.render.ParticleSystem
import de.knuffi.app.render.PetLook
import de.knuffi.app.render.PetPose
import de.knuffi.app.render.PetRenderer
import de.knuffi.app.ui.PetPortrait
import de.knuffi.app.ui.components.ClayTextButton
import de.knuffi.app.ui.components.EmojiTile
import de.knuffi.app.ui.components.PageHeader
import de.knuffi.app.ui.components.Pill
import de.knuffi.app.ui.components.SurfaceCard
import de.knuffi.app.ui.components.rememberHaptic
import de.knuffi.app.ui.components.staggered
import de.knuffi.app.ui.rememberCanvasTypeface
import de.knuffi.app.ui.rememberFrameTime
import de.knuffi.app.ui.theme.LocalPalette
import de.knuffi.core.Action
import de.knuffi.core.Cup
import de.knuffi.core.Discipline
import de.knuffi.core.Engine
import de.knuffi.core.GameState
import de.knuffi.core.Mood
import de.knuffi.core.Rival
import de.knuffi.core.Talent
import de.knuffi.core.Tournament
import java.util.Locale
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

/** Someone taking part. Index 0 is always your own pet. */
private class Racer(val name: String, val look: PetLook?, val speed: Float, val petId: Long?)

private fun rivalLook(r: Rival): PetLook = PetLook(form = r.form, mood = Mood.HAPPY, line = r.form.line, shiny = r.shiny, fur = r.fur, level = 10)

private fun meters(d: Float): String = String.format(Locale.GERMANY, "%.2f m", d)

private val MEDALS = listOf("🥇", "🥈", "🥉", "4.")

@Composable
fun TournamentScreen(state: GameState, onExit: () -> Unit) {
    var cup by remember { mutableStateOf<Cup?>(null) }
    var seed by remember { mutableLongStateOf(System.nanoTime()) }
    var stage by remember { mutableIntStateOf(0) }
    val totals = remember { IntArray(Tournament.RIVALS + 1) }
    var racers by remember { mutableStateOf<List<Racer>>(emptyList()) }
    var sport by remember { mutableIntStateOf(0) }
    BackHandler { onExit() }

    val c = cup
    Box(Modifier.fillMaxSize()) {
        if (c == null) {
            CupSelect(state, seed, onBack = onExit) { picked ->
                val blocker = Engine.gameBlocker(state)
                when {
                    blocker != null -> GameRepository.message(blocker)
                    !Tournament.unlocked(state, picked) -> GameRepository.message("${picked.title} gibt es ab Level ${picked.minLevel}.")
                    else -> {
                        val own = state.pet
                        racers = listOf(Racer(own?.name ?: "Du", gameLook(state), 1f, own?.id)) +
                            Tournament.rivals(state, picked, seed).map { Racer(it.name, rivalLook(it), it.speed, it.petId) }
                        sport = own?.talentLevel(Talent.SPORT) ?: 0
                        totals.fill(0)
                        stage = 0
                        cup = picked
                    }
                }
            }
        } else if (stage < Discipline.entries.size) {
            key(stage) {
                ContestStage(Discipline.entries[stage], c, racers, sport, seed + stage, stage, totals, onExit) { order ->
                    for ((place, idx) in order.withIndex()) totals[idx] += Tournament.placePoints[place.coerceAtMost(Tournament.placePoints.size - 1)]
                    stage++
                }
            }
        } else {
            Podium(c, racers, totals, onExit) {
                cup = null
                stage = 0
                seed = System.nanoTime()
            }
        }
    }
}

// ------------------------------------------------------------------ choosing a cup

@Composable
private fun CupSelect(state: GameState, seed: Long, onBack: () -> Unit, onPick: (Cup) -> Unit) {
    val p = LocalPalette.current
    Column(Modifier.fillMaxSize()) {
        PageHeader("Turnier", onBack)
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SurfaceCard(Modifier.fillMaxWidth().staggered(0), color = if (p.dark) p.surfaceAlt else Color(0xFFFFF0C8)) {
                Text("Drei Disziplinen, ein Pokal!", style = MaterialTheme.typography.titleMedium, color = p.text)
                Spacer(Modifier.height(4.dp))
                for (d in Discipline.entries) {
                    Text("${d.emoji} ${d.title}: ${d.hint}", style = MaterialTheme.typography.bodySmall, color = p.textMuted)
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    "Platz 1 bringt ${Tournament.placePoints[0]} Punkte, Platz 2 ${Tournament.placePoints[1]}, Platz 3 ${Tournament.placePoints[2]}. Wer am Ende die meisten Punkte hat, gewinnt.",
                    style = MaterialTheme.typography.bodySmall,
                    color = p.textMuted,
                )
            }
            for ((i, cup) in Cup.entries.withIndex()) {
                val unlocked = Tournament.unlocked(state, cup)
                val wins = Tournament.wins(state, cup)
                val rivals = remember(cup, seed, state.resting) { Tournament.rivals(state, cup, seed) }
                SurfaceCard(Modifier.fillMaxWidth().staggered(i + 1), onClick = { onPick(cup) }) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        EmojiTile(if (unlocked) cup.emoji else "🔒", size = 52.dp, color = if (unlocked) p.gold else p.track)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(cup.title, style = MaterialTheme.typography.titleMedium, color = p.text)
                            Text(
                                if (unlocked) "Sieg: ${Tournament.coins(cup, 1)} 🪙 · ${if (wins > 0) "schon $wins× gewonnen" else "noch nie gewonnen"}" else "Ab Level ${cup.minLevel}",
                                style = MaterialTheme.typography.bodySmall,
                                color = p.textMuted,
                            )
                        }
                        if (unlocked) ClayTextButton("Start", { onPick(cup) }, small = true, color = p.gold, deep = p.goldDeep)
                    }
                    if (unlocked) {
                        Spacer(Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text("Gegner:", style = MaterialTheme.typography.labelMedium, color = p.textMuted)
                            for (r in rivals) {
                                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                                    PetPortrait(rivalLook(r), Modifier.size(46.dp), animated = false, sizeFactor = 0.8f)
                                    Text(r.name, style = MaterialTheme.typography.labelSmall, color = p.text, maxLines = 1)
                                }
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

// ------------------------------------------------------------------ the contests

private class Contest(val kind: Discipline, val racers: List<Racer>, sport: Int, seed: Long) {
    val n = racers.size
    val pos = FloatArray(n)
    val vel = FloatArray(n)
    val jumpY = FloatArray(n)
    val jumpV = FloatArray(n)
    val finishAt = FloatArray(n) { -1f }
    val stumbleAt = FloatArray(n) { -10f }
    val nextHurdle = IntArray(n)
    var elapsed = 0f
    var over by mutableStateOf(false)
    var message by mutableStateOf<String?>(null)
    private val rnd = Random(seed)
    private val bonus = 1f + 0.012f * sport.coerceAtMost(15)
    private val willStumble = Array(n) { i -> BooleanArray(HURDLES.size) { i > 0 && rnd.nextFloat() < (0.32f - 0.16f * racers[i].speed).coerceIn(0.05f, 0.3f) } }

    // long jump
    var attempt by mutableIntStateOf(0)
    var phase = 0
    var phaseAt = 0f
    var power = 0f
    var flyDist = 0f
    var perfect = false
    val attempts = ArrayList<Float>()
    val rivalBest = FloatArray(n) { i -> if (i == 0) 0f else (4.4f + rnd.nextFloat() * 2.2f) * racers[i].speed }
    val playerBest: Float get() = attempts.maxOrNull() ?: 0f

    fun tap() {
        if (over) return
        when (kind) {
            Discipline.RACE -> vel[0] = min(0.17f, vel[0] + 0.04f * bonus)
            Discipline.HURDLES -> if (jumpY[0] <= 0f && jumpV[0] <= 0f && finishAt[0] < 0f) jumpV[0] = JUMP_V
            Discipline.LONGJUMP -> if (phase == 0 && elapsed - phaseAt > 0.3f) {
                perfect = power > 0.94f
                flyDist = (2f + 6f * power) * bonus + if (perfect) 0.3f else 0f
                phase = 1
                phaseAt = elapsed
            }
        }
    }

    fun update(dt: Float, ps: ParticleSystem, nx: (Int) -> Float, ny: (Int) -> Float) {
        if (over) return
        elapsed += dt
        when (kind) {
            Discipline.RACE -> {
                vel[0] *= exp(-2.2f * dt)
                pos[0] += vel[0] * dt
                for (i in 1 until n) pos[i] += RACE_SPEED * racers[i].speed * (1f + 0.12f * sin(elapsed * 1.7f + i * 2f)) * dt
                finishLine(ps, nx, ny)
            }
            Discipline.HURDLES -> {
                for (i in 0 until n) {
                    val base = if (i == 0) HURDLE_SPEED * bonus else HURDLE_SPEED * racers[i].speed
                    val slow = if (elapsed - stumbleAt[i] < 0.6f) 0.3f else 1f
                    if (finishAt[i] < 0f) pos[i] += base * slow * dt
                    if (jumpY[i] > 0f || jumpV[i] > 0f) {
                        jumpV[i] -= GRAVITY * dt
                        jumpY[i] += jumpV[i] * dt
                        if (jumpY[i] <= 0f) {
                            jumpY[i] = 0f
                            jumpV[i] = 0f
                        }
                    }
                    val k = nextHurdle[i]
                    if (k < HURDLES.size) {
                        // rivals jump by themselves, unless they are unlucky this time
                        if (i > 0 && !willStumble[i][k] && jumpY[i] <= 0f && pos[i] >= HURDLES[k] - base * 0.3f) jumpV[i] = JUMP_V
                        if (pos[i] >= HURDLES[k]) {
                            if (jumpY[i] < 0.2f) {
                                stumbleAt[i] = elapsed
                                ps.burst(PKind.SMOKE, nx(i), ny(i), 5, 0.12f, 0.5f, 0.02f, intArrayOf(0x88E8D0B0.toInt()), drag = 2f)
                                if (i == 0) message = "Autsch, gestolpert!"
                            }
                            nextHurdle[i] = k + 1
                        }
                    }
                }
                finishLine(ps, nx, ny)
            }
            Discipline.LONGJUMP -> {
                val since = elapsed - phaseAt
                when (phase) {
                    0 -> {
                        power = ((1f - cos(since * 3.4f)) / 2f)
                        if (since > 6f) {
                            attempts += 0f
                            message = "Zu spät! Ungültig."
                            phase = 2
                            phaseAt = elapsed
                        }
                    }
                    1 -> if (since > 1.1f) {
                        attempts += flyDist
                        message = "${attempt + 1}. Sprung: ${meters(flyDist)}${if (perfect) " · Perfekt! ⭐" else ""}"
                        ps.burst(PKind.SMOKE, nx(0), ny(0), 8, 0.2f, 0.6f, 0.03f, intArrayOf(0xAAF3D9A0.toInt()), drag = 2f)
                        phase = 2
                        phaseAt = elapsed
                    }
                    else -> if (since > 1.6f) {
                        if (attempt + 1 >= JUMPS) {
                            over = true
                        } else {
                            attempt++
                            phase = 0
                            phaseAt = elapsed
                            message = null
                        }
                    }
                }
            }
        }
    }

    private fun finishLine(ps: ParticleSystem, nx: (Int) -> Float, ny: (Int) -> Float) {
        for (i in 0 until n) {
            if (pos[i] >= 1f) {
                pos[i] = 1f
                if (finishAt[i] < 0f) {
                    finishAt[i] = elapsed
                    if (i == 0) {
                        val place = 1 + (1 until n).count { finishAt[it] >= 0f && finishAt[it] < elapsed }
                        message = "Im Ziel! Platz $place"
                        ps.burst(PKind.CONFETTI, nx(0), ny(0), 30, 0.5f, 1.4f, 0.012f, ParticleSystem.CONFETTI_COLORS, gravity = 0.4f, upward = true)
                    }
                }
            }
        }
        val mine = finishAt[0]
        if (mine >= 0f && ((0 until n).all { finishAt[it] >= 0f } || elapsed - mine > 2.5f)) over = true
        if (elapsed > 45f) over = true
    }

    /** Racer indices from first to last place. */
    fun order(): List<Int> = when (kind) {
        Discipline.LONGJUMP -> (0 until n).sortedByDescending { if (it == 0) playerBest else rivalBest[it] }
        else -> (0 until n).sortedWith(compareBy<Int>({ if (finishAt[it] < 0f) 1 else 0 }, { if (finishAt[it] < 0f) -pos[it] else finishAt[it] }))
    }

    companion object {
        const val RACE_SPEED = 0.085f
        const val HURDLE_SPEED = 0.082f
        const val JUMP_V = 3.6f
        const val GRAVITY = 10f
        const val JUMPS = 3
        val HURDLES = FloatArray(8) { 0.13f + it * 0.105f }
    }
}

private class ContestPainter {
    val renderer = PetRenderer()
    val pose = PetPose()
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    val text = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.LEFT }
    private val center = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER }

    fun stadium(c: NativeCanvas, w: Float, h: Float, t: Float, trackTop: Float) {
        fill.shader = LinearGradient(0f, 0f, 0f, trackTop, 0xFF7EC8FF.toInt(), 0xFFFFE6F2.toInt(), Shader.TileMode.CLAMP)
        c.drawRect(0f, 0f, w, trackTop, fill)
        fill.shader = null
        // the crowd on the stands
        val standTop = trackTop - h * 0.12f
        fill.color = 0xFF8A7CC8.toInt()
        c.drawRect(0f, standTop, w, trackTop, fill)
        val colors = intArrayOf(0xFFFF6B9A.toInt(), 0xFFFFD166.toInt(), 0xFF6EC1FF.toInt(), 0xFF7DD9B0.toInt(), 0xFFFFFFFF.toInt())
        var i = 0
        var y = standTop + h * 0.02f
        while (y < trackTop - h * 0.01f) {
            var x = (i % 2) * w * 0.02f
            while (x < w) {
                val bob = abs(sin(t * 6f + x * 0.05f + i)) * h * 0.006f
                fill.color = colors[((x * 7 + i * 3).toInt() and 0x7fffffff) % colors.size]
                c.drawCircle(x, y - bob, h * 0.008f, fill)
                x += w * 0.04f
            }
            y += h * 0.022f
            i++
        }
        // flags
        for (k in 0 until 6) {
            val fx = w * (0.08f + k * 0.17f)
            stroke.color = 0xFF5A4A80.toInt()
            stroke.strokeWidth = 3f
            c.drawLine(fx, standTop - h * 0.06f, fx, standTop, stroke)
            fill.color = colors[k % colors.size]
            val wave = sin(t * 4f + k) * h * 0.006f
            c.drawRect(fx, standTop - h * 0.06f + wave, fx + w * 0.05f, standTop - h * 0.035f + wave, fill)
        }
    }

    fun runnerPose(look: PetLook, t: Float, speed: Float, air: Float, stumbling: Boolean, happy: Boolean, phase: Float): PetPose {
        val p = pose.defaults(look, t)
        p.turn = 0.55f
        if (speed > 0.001f && air <= 0f) {
            val ph = t * (8f + speed * 80f) + phase
            p.strideL = cos(ph) * 0.9f
            p.strideR = -p.strideL
            p.footL = max(0f, sin(ph)) * 0.9f
            p.footR = max(0f, -sin(ph)) * 0.9f
            p.armL = 0.35f + 0.25f * sin(ph)
            p.armR = 0.35f - 0.25f * sin(ph)
            p.lift += abs(sin(ph)) * 0.05f
        }
        if (air > 0f) {
            p.lift = air
            p.armL = 1f
            p.armR = 1f
            p.strideL = 0.6f
            p.strideR = -0.4f
        }
        p.mouth = MouthShape.SMILE
        if (happy) {
            p.eyes = EyeShape.HAPPY
            p.mouth = MouthShape.GRIN
        }
        if (stumbling) {
            p.eyes = EyeShape.DIZZY
            p.mouth = MouthShape.WAVY
            p.tilt = 25f
        }
        return p
    }

    fun track(c: NativeCanvas, s: Contest, w: Float, h: Float, t: Float, top: Float, bottom: Float, ink: Int) {
        val n = s.n
        val laneH = (bottom - top) / n
        val startX = w * 0.1f
        val finishX = w * 0.9f
        fill.color = 0xFFD8705A.toInt()
        c.drawRect(0f, top, w, bottom, fill)
        fill.color = 0x33FFF3B0
        c.drawRect(0f, top, w, top + laneH, fill)
        stroke.color = 0xCCFFFFFF.toInt()
        stroke.strokeWidth = 3f
        for (i in 0..n) c.drawLine(0f, top + laneH * i, w, top + laneH * i, stroke)
        // start and finish
        c.drawLine(startX, top, startX, bottom, stroke)
        val sq = laneH / 6f
        var yy = top
        var k = 0
        while (yy < bottom) {
            fill.color = if (k % 2 == 0) AColor.WHITE else 0xFF2A2433.toInt()
            c.drawRect(finishX, yy, finishX + sq, min(bottom, yy + sq), fill)
            fill.color = if (k % 2 == 0) 0xFF2A2433.toInt() else AColor.WHITE
            c.drawRect(finishX + sq, yy, finishX + sq * 2, min(bottom, yy + sq), fill)
            yy += sq
            k++
        }
        for (i in 0 until n) {
            val ground = top + laneH * (i + 1) - laneH * 0.12f
            if (s.kind == Discipline.HURDLES) {
                for (hp in Contest.HURDLES) {
                    val hx = startX + hp * (finishX - startX)
                    val hh = laneH * 0.32f
                    stroke.color = 0xFFFFFFFF.toInt()
                    stroke.strokeWidth = laneH * 0.05f
                    c.drawLine(hx, ground, hx, ground - hh, stroke)
                    stroke.color = 0xFFFF5A6E.toInt()
                    stroke.strokeWidth = laneH * 0.07f
                    c.drawLine(hx - laneH * 0.08f, ground - hh, hx + laneH * 0.08f, ground - hh, stroke)
                }
            }
        }
        val size = laneH * 0.82f
        for (i in 0 until n) {
            val r = s.racers[i]
            val ground = top + laneH * (i + 1) - laneH * 0.12f
            val x = startX + s.pos[i] * (finishX - startX)
            text.color = ink
            text.textSize = laneH * 0.2f
            c.drawText(if (i == 0) "⭐ ${r.name}" else r.name, w * 0.01f, top + laneH * i + laneH * 0.24f, text)
            val look = r.look ?: continue
            val finished = s.finishAt[i] >= 0f
            val speed = if (finished) 0f else if (s.kind == Discipline.RACE && i == 0) s.vel[0] else 0.08f
            val stumbling = s.elapsed - s.stumbleAt[i] < 0.6f
            val p = runnerPose(look, t, speed, s.jumpY[i], stumbling, finished, i * 1.3f)
            renderer.drawShadow(c, x, ground, size, p, look)
            renderer.draw(c, x, ground, size, look, p)
        }
    }

    fun longJump(c: NativeCanvas, s: Contest, w: Float, h: Float, t: Float, top: Float, ground: Float, ink: Int) {
        val boardX = w * 0.28f
        val pitEnd = w * 0.96f
        val mPx = (pitEnd - boardX) / 9f
        // runway and pit
        fill.color = 0xFFD8705A.toInt()
        c.drawRect(0f, ground, boardX, ground + h * 0.05f, fill)
        fill.color = 0xFFF3D9A0.toInt()
        c.drawRect(boardX, ground, pitEnd, ground + h * 0.05f, fill)
        fill.color = AColor.WHITE
        c.drawRect(boardX - w * 0.012f, ground, boardX, ground + h * 0.05f, fill)
        fill.color = 0xFF7DBE5A.toInt()
        c.drawRect(0f, ground + h * 0.05f, w, h, fill)
        center.color = ink
        center.textSize = h * 0.022f
        for (m in 2..9) {
            val mx = boardX + m * mPx
            stroke.color = 0x88FFFFFF.toInt()
            stroke.strokeWidth = 2f
            c.drawLine(mx, ground, mx, ground + h * 0.05f, stroke)
            c.drawText("$m m", mx, ground + h * 0.085f, center)
        }
        // the rivals' best jumps as little flags
        for (i in 1 until s.n) {
            val fx = boardX + s.rivalBest[i] * mPx
            stroke.color = 0xFF5A4A80.toInt()
            stroke.strokeWidth = 3f
            c.drawLine(fx, ground, fx, ground - h * 0.06f - i * h * 0.012f, stroke)
            fill.color = intArrayOf(0xFF6EC1FF.toInt(), 0xFF7DD9B0.toInt(), 0xFFB39DFF.toInt())[(i - 1) % 3]
            val fy = ground - h * 0.06f - i * h * 0.012f
            c.drawRect(fx, fy, fx + w * 0.045f, fy + h * 0.02f, fill)
            center.textSize = h * 0.018f
            c.drawText(s.racers[i].name, fx, fy - h * 0.008f, center)
        }
        if (s.playerBest > 0f) {
            val fx = boardX + s.playerBest * mPx
            stroke.color = 0xFFFFC83D.toInt()
            stroke.strokeWidth = 4f
            c.drawLine(fx, ground, fx, ground - h * 0.09f, stroke)
            center.textSize = h * 0.03f
            c.drawText("⭐", fx, ground - h * 0.09f, center)
        }
        // the jumper
        val look = s.racers[0].look ?: return
        val size = min(w * 0.2f, h * 0.12f)
        val since = s.elapsed - s.phaseAt
        val (x, lift) = when (s.phase) {
            0 -> boardX - size * 0.6f to 0f
            1 -> {
                val k = (since / 1.1f).coerceIn(0f, 1f)
                (boardX + s.flyDist * mPx * k) to sin(k * Math.PI.toFloat()) * (0.6f + s.flyDist * 0.12f)
            }
            else -> (boardX + (s.attempts.lastOrNull() ?: 0f) * mPx) to 0f
        }
        val p = runnerPose(look, t, if (s.phase == 0) 0.08f else 0f, lift, false, s.phase == 2 && (s.attempts.lastOrNull() ?: 0f) > 0f, 0f)
        if (s.phase == 2 && (s.attempts.lastOrNull() ?: 0f) <= 0f) {
            p.eyes = EyeShape.SQUINT
            p.mouth = MouthShape.WAVY
        }
        renderer.drawShadow(c, x, ground, size, p, look)
        renderer.draw(c, x, ground, size, look, p)
        // power meter
        if (s.phase == 0) {
            val bw = w * 0.7f
            val bx = (w - bw) / 2f
            val by = top
            val bh = h * 0.03f
            fill.shader = LinearGradient(bx, 0f, bx + bw, 0f, intArrayOf(0xFF7DD9B0.toInt(), 0xFFFFD166.toInt(), 0xFFFF5A6E.toInt()), null, Shader.TileMode.CLAMP)
            c.drawRoundRect(bx, by, bx + bw, by + bh, bh / 2f, bh / 2f, fill)
            fill.shader = null
            val ix = bx + bw * s.power
            fill.color = AColor.WHITE
            c.drawRoundRect(ix - bh * 0.25f, by - bh * 0.4f, ix + bh * 0.25f, by + bh * 1.4f, bh * 0.2f, bh * 0.2f, fill)
            stroke.color = 0xFF2A2433.toInt()
            stroke.strokeWidth = 2f
            c.drawRoundRect(ix - bh * 0.25f, by - bh * 0.4f, ix + bh * 0.25f, by + bh * 1.4f, bh * 0.2f, bh * 0.2f, stroke)
        }
    }
}

@Composable
private fun ContestStage(
    kind: Discipline,
    cup: Cup,
    racers: List<Racer>,
    sport: Int,
    seed: Long,
    stageNo: Int,
    totals: IntArray,
    onExit: () -> Unit,
    onDone: (List<Int>) -> Unit,
) {
    val p = LocalPalette.current
    val contest = remember { Contest(kind, racers, sport, seed) }
    val painter = remember { ContestPainter() }
    val ps = remember { ParticleSystem() }
    val time = rememberFrameTime()
    val tf = rememberCanvasTypeface()
    val haptic = rememberHaptic()
    val lastT = remember { floatArrayOf(-1f) }
    var running by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf<List<Int>?>(null) }
    val geo = remember { FloatArray(4) }
    val ink = if (p.dark) 0xFFF4ECFF.toInt() else 0xFF3A2A45.toInt()

    LaunchedEffect(tf) {
        ps.typeface = tf
        painter.text.typeface = tf
    }
    LaunchedEffect(contest.over) {
        if (contest.over) {
            kotlinx.coroutines.delay(900)
            result = contest.order()
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(if (p.dark) Color(0xFF1E1A33) else Color(0xFFFFF4E8))
            .clipToBounds(),
    ) {
        Canvas(
            Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    awaitEachGesture {
                        awaitFirstDown()
                        if (running) {
                            contest.tap()
                            if (kind != Discipline.RACE) haptic()
                        }
                    }
                },
        ) {
            val t = time.floatValue
            val dt = if (lastT[0] < 0f) 0f else (t - lastT[0]).coerceIn(0f, 0.05f)
            lastT[0] = t
            val w = size.width
            val h = size.height
            val trackTop = h * 0.36f
            val trackBottom = h * 0.86f
            val laneH = (trackBottom - trackTop) / contest.n
            geo[0] = trackTop
            geo[1] = laneH
            if (running) {
                contest.update(
                    dt,
                    ps,
                    { i -> if (kind == Discipline.LONGJUMP) 0.5f else 0.1f + contest.pos[i] * 0.8f },
                    { i -> if (kind == Discipline.LONGJUMP) 0.7f else (trackTop + laneH * (i + 1) - laneH * 0.12f) / h },
                )
            }
            ps.update(t)
            drawIntoCanvas { canvas ->
                val c = canvas.nativeCanvas
                painter.stadium(c, w, h, t, trackTop)
                if (kind == Discipline.LONGJUMP) {
                    painter.longJump(c, contest, w, h, t, trackTop + h * 0.04f, h * 0.72f, ink)
                } else {
                    painter.track(c, contest, w, h, t, trackTop, trackBottom, ink)
                }
                ps.draw(c, w, h)
            }
        }
        // header
        Column(
            Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 12.dp, vertical = 6.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                de.knuffi.app.ui.components.GlassIconButton("✖️", onExit, size = 42.dp)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text("${cup.emoji} ${kind.emoji} ${kind.title}", style = MaterialTheme.typography.titleMedium, color = p.text)
                    Text("Disziplin ${stageNo + 1} von ${Discipline.entries.size}", style = MaterialTheme.typography.labelMedium, color = p.textMuted)
                }
                Pill("⭐ ${totals[0]} Pkt.", color = p.gold.copy(alpha = 0.3f))
            }
            if (kind == Discipline.LONGJUMP && running) {
                Spacer(Modifier.height(6.dp))
                Text(
                    "Sprung ${(contest.attempt + 1).coerceAtMost(Contest.JUMPS)} von ${Contest.JUMPS} · Bester: ${meters(contest.playerBest)}",
                    style = MaterialTheme.typography.labelLarge,
                    color = p.text,
                )
            }
        }
        contest.message?.let { msg ->
            Text(
                msg,
                style = MaterialTheme.typography.titleMedium,
                color = Color.White,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(bottom = 24.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .background(Color(0xAA1B1440))
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            )
        }
        if (!running && result == null) {
            CountdownOverlay(kind.hint) { running = true }
        }
        result?.let { order ->
            ContestResult(kind, racers, order) { onDone(order) }
        }
    }
}

@Composable
private fun ContestResult(kind: Discipline, racers: List<Racer>, order: List<Int>, onNext: () -> Unit) {
    val p = LocalPalette.current
    Box(Modifier.fillMaxSize().background(Color(0x990E0A1F)), contentAlignment = Alignment.Center) {
        SurfaceCard(Modifier.padding(24.dp).fillMaxWidth()) {
            Text("${kind.emoji} ${kind.title}: Ergebnis", style = MaterialTheme.typography.titleLarge, color = p.text)
            Spacer(Modifier.height(10.dp))
            for ((place, idx) in order.withIndex()) {
                val r = racers[idx]
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(if (idx == 0) p.gold.copy(alpha = 0.25f) else Color.Transparent)
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(MEDALS[place.coerceAtMost(MEDALS.size - 1)], fontSize = 22.sp, modifier = Modifier.width(36.dp))
                    PetPortrait(r.look, Modifier.size(40.dp), animated = false, sizeFactor = 0.8f)
                    Spacer(Modifier.width(8.dp))
                    Text(r.name, style = MaterialTheme.typography.titleSmall, color = p.text, modifier = Modifier.weight(1f))
                    Text("+${Tournament.placePoints[place.coerceAtMost(Tournament.placePoints.size - 1)]}", style = MaterialTheme.typography.titleSmall, color = p.mintDeep)
                }
            }
            Spacer(Modifier.height(14.dp))
            ClayTextButton("Weiter", onNext, emoji = "▶", modifier = Modifier.fillMaxWidth(), color = p.mint, deep = p.mintDeep)
        }
    }
}

// ------------------------------------------------------------------ the podium

@Composable
private fun Podium(cup: Cup, racers: List<Racer>, totals: IntArray, onExit: () -> Unit, onAgain: () -> Unit) {
    val p = LocalPalette.current
    val mine = totals[0]
    val place = Tournament.place(mine, (1 until totals.size).map { totals[it] })
    val order = remember { racers.indices.sortedWith(compareBy<Int>({ -totals[it] }, { if (it == 0) 0 else 1 })) }
    val coins = Tournament.coins(cup, place)
    val ps = remember { ParticleSystem() }
    val time = rememberFrameTime()
    val tf = rememberCanvasTypeface()
    LaunchedEffect(Unit) {
        GameRepository.perform(Action.TournamentFinished(cup, place, racers.drop(1).mapNotNull { it.petId }))
        ps.typeface = tf
        if (place == 1) ps.burst(PKind.CONFETTI, 0.5f, 0.3f, 80, 0.9f, 3f, 0.016f, ParticleSystem.CONFETTI_COLORS, gravity = 0.45f, upward = true)
    }
    Box(
        Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(if (p.dark) listOf(Color(0xFF1E1A33), Color(0xFF3A2A5E)) else listOf(Color(0xFFFFF0C8), Color(0xFFFFE0EE)))),
    ) {
        Canvas(Modifier.fillMaxSize()) {
            ps.update(time.floatValue)
            drawIntoCanvas { ps.draw(it.nativeCanvas, size.width, size.height) }
        }
        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("${cup.emoji} ${cup.title}", style = MaterialTheme.typography.headlineSmall, color = p.text)
            Text(
                when (place) {
                    1 -> "Gewonnen! Der Pokal gehört dir! 🏆"
                    2 -> "Super, Platz 2!"
                    3 -> "Toll, Platz 3!"
                    else -> "Platz 4, beim nächsten Mal klappt's!"
                },
                style = MaterialTheme.typography.titleMedium,
                color = p.textMuted,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(16.dp))
            // podium: 2nd, 1st, 3rd
            Row(Modifier.fillMaxWidth().height(250.dp), verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for (slot in listOf(1, 0, 2)) {
                    val idx = order.getOrNull(slot) ?: continue
                    val r = racers[idx]
                    val podiumH = when (slot) {
                        0 -> 110.dp
                        1 -> 80.dp
                        else -> 56.dp
                    }
                    Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                        PetPortrait(r.look, Modifier.size(if (slot == 0) 96.dp else 80.dp), sizeFactor = 0.8f) { pose, t ->
                            if (slot == 0) {
                                pose.eyes = EyeShape.STAR
                                pose.mouth = MouthShape.GRIN
                                pose.armL = 0.9f + 0.1f * sin(t * 7f)
                                pose.armR = 0.9f
                                val ph = t % 0.9f
                                if (ph < 0.35f) pose.lift = sin(ph / 0.35f * Math.PI.toFloat()) * 0.25f
                            } else {
                                pose.eyes = EyeShape.HAPPY
                                pose.mouth = MouthShape.SMILE
                            }
                        }
                        Text(r.name, style = MaterialTheme.typography.labelLarge, color = p.text, maxLines = 1)
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .height(podiumH)
                                .clip(RoundedCornerShape(topStart = 14.dp, topEnd = 14.dp))
                                .background(Brush.verticalGradient(listOf(if (slot == 0) p.gold else if (slot == 1) Color(0xFFD8D8E8) else Color(0xFFE8B080), Color.White.copy(alpha = 0.6f)))),
                            contentAlignment = Alignment.TopCenter,
                        ) {
                            Text(MEDALS[slot], fontSize = 28.sp, modifier = Modifier.padding(top = 6.dp))
                        }
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
            SurfaceCard(Modifier.fillMaxWidth(), contentPadding = PaddingValues(12.dp)) {
                for ((rank, idx) in order.withIndex()) {
                    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("${rank + 1}.", style = MaterialTheme.typography.titleSmall, color = p.textMuted, modifier = Modifier.width(28.dp))
                        Text(if (idx == 0) "⭐ ${racers[idx].name}" else racers[idx].name, style = MaterialTheme.typography.titleSmall, color = p.text, modifier = Modifier.weight(1f))
                        Text("${totals[idx]} Punkte", style = MaterialTheme.typography.titleSmall, color = p.text)
                    }
                }
                Spacer(Modifier.height(8.dp))
                Text("Belohnung: +$coins 🪙 und Sport-Talent", style = MaterialTheme.typography.titleSmall, color = p.mintDeep)
            }
            Spacer(Modifier.weight(1f))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                ClayTextButton("Noch eins", onAgain, emoji = "🔁", modifier = Modifier.weight(1f), color = p.violet, deep = p.violetDeep)
                ClayTextButton("Fertig", onExit, emoji = "✅", modifier = Modifier.weight(1f), color = p.mint, deep = p.mintDeep)
            }
        }
    }
}
