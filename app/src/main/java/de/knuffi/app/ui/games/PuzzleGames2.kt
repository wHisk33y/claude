package de.knuffi.app.ui.games

import android.graphics.Canvas as NativeCanvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
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
import de.knuffi.app.render.Colors
import de.knuffi.app.render.EyeShape
import de.knuffi.app.render.MouthShape
import de.knuffi.app.render.PKind
import de.knuffi.app.render.Particle
import de.knuffi.app.render.ParticleSystem
import de.knuffi.app.render.PetLook
import de.knuffi.app.render.PetPose
import de.knuffi.app.render.PetRenderer
import de.knuffi.app.ui.PetPortrait
import de.knuffi.app.ui.components.SurfaceCard
import de.knuffi.app.ui.components.rememberHaptic
import de.knuffi.app.ui.rememberCanvasTypeface
import de.knuffi.app.ui.rememberFrameTime
import de.knuffi.app.ui.theme.LocalPalette
import de.knuffi.core.Form
import de.knuffi.core.GameState
import de.knuffi.core.Mood
import kotlinx.coroutines.delay
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

// ====================================================================== spot the difference

private enum class DiffKind { GONE, SWAP, BIG, COLOR }

/** One thing in the picture. Positions are fractions of the panel, [size] of the panel height. */
private class SpotItem(val x: Float, val y: Float, val size: Float, val emoji: String?, val color: Int)

private class SpotDiff(val item: Int, val kind: DiffKind, val alt: String, val altColor: Int) {
    var found = false
}

private class SpotRound(val theme: Int, val items: List<SpotItem>, val diffs: List<SpotDiff>)

private class SpotLogic {
    var found by mutableIntStateOf(0)
    var roundNo by mutableIntStateOf(0)
    var timeLeft by mutableFloatStateOf(TIME)
    var over by mutableStateOf(false)
    var round: SpotRound = make(0)
        private set
    var happyAt = -10f
    var wrongAt = -10f
    private var elapsed = 0f
    private var nextRoundAt = -1f
    private val rnd = Random(System.nanoTime())

    fun make(index: Int): SpotRound {
        val r = Random(System.nanoTime() + index * 7919L)
        val theme = index % THEMES.size
        val pool = POOLS[theme].shuffled(r)
        val cells = ArrayList<Pair<Float, Float>>()
        for (row in 0 until 2) for (col in 0 until 4) cells += (0.14f + col * 0.24f) to (0.2f + row * 0.26f)
        for (x in listOf(0.09f, 0.26f, 0.74f, 0.91f)) cells += x to 0.84f
        val items = cells.mapIndexed { i, (cx, cy) ->
            val jx = (r.nextFloat() - 0.5f) * 0.07f
            val jy = (r.nextFloat() - 0.5f) * 0.06f
            val dot = i % 4 == 1 && i < 8
            SpotItem(cx + jx, cy + jy, 0.13f + r.nextFloat() * 0.05f, if (dot) null else pool[i % pool.size], DOT_COLORS[r.nextInt(DOT_COLORS.size)])
        }
        val unused = (POOLS[theme] + EXTRAS).filter { e -> items.none { it.emoji == e } }.shuffled(r)
        val picks = items.indices.shuffled(r).take(PER_ROUND)
        val diffs = picks.mapIndexed { k, i ->
            val it = items[i]
            val kind = if (it.emoji == null) listOf(DiffKind.COLOR, DiffKind.COLOR, DiffKind.GONE, DiffKind.BIG)[r.nextInt(4)] else listOf(DiffKind.SWAP, DiffKind.SWAP, DiffKind.GONE, DiffKind.BIG)[r.nextInt(4)]
            val altColor = DOT_COLORS.filter { c -> c != it.color }.let { l -> l[r.nextInt(l.size)] }
            SpotDiff(i, kind, unused.getOrElse(k) { "🎁" }, altColor)
        }
        return SpotRound(theme, items, diffs)
    }

    fun update(t: Float, dt: Float) {
        if (over) return
        elapsed += dt
        val left = max(0f, TIME - elapsed)
        if (abs(left - timeLeft) >= 0.1f || left == 0f) timeLeft = left
        if (left <= 0f) {
            over = true
            return
        }
        if (nextRoundAt in 0f..t) {
            nextRoundAt = -1f
            if (roundNo + 1 >= ROUNDS) {
                over = true
            } else {
                roundNo++
                round = make(roundNo)
            }
        }
    }

    /** A tap at pixel [x], [y]. [geo]: left, top of panel 1, top of panel 2, width, height. */
    fun tap(x: Float, y: Float, geo: FloatArray, t: Float, ps: ParticleSystem, w: Float, h: Float) {
        if (over || nextRoundAt >= 0f) return
        val left = geo[0]
        val pw = geo[3]
        val ph = geo[4]
        val top = when {
            y in geo[1]..geo[1] + ph -> geo[1]
            y in geo[2]..geo[2] + ph -> geo[2]
            else -> return
        }
        if (x !in left..left + pw) return
        var best: SpotDiff? = null
        var bestD = Float.MAX_VALUE
        for (d in round.diffs) {
            if (d.found) continue
            val it = round.items[d.item]
            val dist = hypot(left + it.x * pw - x, top + it.y * ph - y)
            val reach = it.size * ph * 0.75f + ph * 0.05f
            if (dist < reach && dist < bestD) {
                best = d
                bestD = dist
            }
        }
        val d = best
        if (d != null) {
            d.found = true
            found++
            happyAt = t
            val it = round.items[d.item]
            for (pt in listOf(geo[1], geo[2])) {
                ps.burst(PKind.SPARKLE, (left + it.x * pw) / w, (pt + it.y * ph) / h, 10, 0.3f, 0.6f, 0.016f, ParticleSystem.STAR_COLORS)
            }
            ps.add(Particle(PKind.TEXT, x / w, y / h - 0.03f, 0f, -0.07f, 0.8f, 0.04f, 0xFF3BB273.toInt(), text = "Gefunden!"))
            if (round.diffs.all { it.found }) {
                nextRoundAt = t + 1.4f
                ps.burst(PKind.CONFETTI, 0.5f, 0.45f, 40, 0.7f, 2f, 0.014f, ParticleSystem.CONFETTI_COLORS, gravity = 0.45f, upward = true)
            }
        } else {
            wrongAt = t
            elapsed += PENALTY
            ps.add(Particle(PKind.TEXT, x / w, y / h, 0f, -0.05f, 0.7f, 0.05f, 0xFFFF5A6E.toInt(), text = "✗ −${PENALTY.toInt()}s"))
        }
    }

    val roundFound: Int get() = round.diffs.count { it.found }

    companion object {
        const val TIME = 100f
        const val ROUNDS = 3
        const val PER_ROUND = 5
        const val PENALTY = 3f

        /** sky top, sky bottom, ground */
        val THEMES = listOf(
            intArrayOf(0xFF8FD3FF.toInt(), 0xFFE6F6FF.toInt(), 0xFF8ED06A.toInt()),
            intArrayOf(0xFFFFC8A8.toInt(), 0xFFFFF1D6.toInt(), 0xFFF3D9A0.toInt()),
            intArrayOf(0xFF4B3C8C.toInt(), 0xFF9A7CD8.toInt(), 0xFF6A56B0.toInt()),
        )
        val POOLS = listOf(
            listOf("🌷", "🌻", "🍄", "🦋", "🐝", "🌳", "🍎", "🐞", "🌼", "🐌", "🪺", "🌈"),
            listOf("🐚", "🦀", "⛱️", "🐠", "🍉", "🪣", "⭐", "🌴", "🐙", "🍦", "⛵", "🏐"),
            listOf("🌙", "⭐", "🦉", "🏮", "🪐", "☁️", "🔭", "🧸", "🎈", "🕯️", "👻", "🍭"),
        )
        val EXTRAS = listOf("🍓", "🎁", "🐸", "🍩", "🐤", "🌸", "🎀", "🍒")
        val DOT_COLORS = intArrayOf(0xFFFF6B9A.toInt(), 0xFFFFD166.toInt(), 0xFF6EC1FF.toInt(), 0xFF7DD9B0.toInt(), 0xFFB39DFF.toInt())
    }
}

private class SpotPainter {
    val renderer = PetRenderer()
    val pose = PetPose()
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val emoji = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER }
    private val rect = RectF()
    private val path = Path()

    fun panel(c: NativeCanvas, logic: SpotLogic, changed: Boolean, left: Float, top: Float, pw: Float, ph: Float, look: PetLook?, t: Float) {
        val round = logic.round
        val theme = SpotLogic.THEMES[round.theme]
        val radius = ph * 0.08f
        rect.set(left, top, left + pw, top + ph)
        fill.color = Colors.alpha(android.graphics.Color.BLACK, 0.12f)
        c.drawRoundRect(rect.left, rect.top + ph * 0.015f, rect.right, rect.bottom + ph * 0.015f, radius, radius, fill)
        c.save()
        path.reset()
        path.addRoundRect(rect, radius, radius, Path.Direction.CW)
        c.clipPath(path)
        fill.shader = LinearGradient(0f, top, 0f, top + ph * 0.72f, theme[0], theme[1], Shader.TileMode.CLAMP)
        c.drawRect(rect, fill)
        fill.shader = null
        fill.color = theme[2]
        c.drawOval(left - pw * 0.2f, top + ph * 0.7f, left + pw * 1.2f, top + ph * 1.3f, fill)
        // the pet in the middle (always the same in both pictures)
        if (look != null) {
            val p = pose.defaults(look, t)
            p.eyes = if (t - logic.happyAt < 0.8f) EyeShape.STAR else if (t - logic.wrongAt < 0.6f) EyeShape.SQUINT else EyeShape.OPEN
            p.mouth = if (t - logic.happyAt < 0.8f) MouthShape.GRIN else MouthShape.SMILE
            p.lookX = sin(t * 0.7f) * 0.6f
            val size = ph * 0.34f
            renderer.drawShadow(c, left + pw * 0.5f, top + ph * 0.93f, size, p, look)
            renderer.draw(c, left + pw * 0.5f, top + ph * 0.93f, size, look, p)
        }
        for ((i, it) in round.items.withIndex()) {
            val diff = if (changed) round.diffs.firstOrNull { d -> d.item == i } else null
            var size = it.size * ph
            var e = it.emoji
            var color = it.color
            if (diff != null) {
                when (diff.kind) {
                    DiffKind.GONE -> continue
                    DiffKind.SWAP -> e = diff.alt
                    DiffKind.BIG -> size *= 1.45f
                    DiffKind.COLOR -> color = diff.altColor
                }
            }
            val x = left + it.x * pw
            val y = top + it.y * ph
            if (e == null) balloon(c, x, y, size, color, t + i) else {
                emoji.textSize = size
                c.drawText(e, x, y + size * 0.35f, emoji)
            }
        }
        // found differences get a ring in both pictures
        for (d in round.diffs) {
            if (!d.found) continue
            val it = round.items[d.item]
            val r = it.size * ph * (if (d.kind == DiffKind.BIG) 0.9f else 0.7f)
            stroke.strokeWidth = ph * 0.014f
            stroke.color = 0xFF3BB273.toInt()
            c.drawCircle(left + it.x * pw, top + it.y * ph, r, stroke)
        }
        c.restore()
    }

    private fun balloon(c: NativeCanvas, x: Float, y: Float, size: Float, color: Int, t: Float) {
        val bob = sin(t * 1.6f) * size * 0.04f
        stroke.strokeWidth = size * 0.03f
        stroke.color = 0x99FFFFFF.toInt()
        c.drawLine(x, y + size * 0.42f + bob, x, y + size * 0.85f, stroke)
        fill.color = color
        c.drawOval(x - size * 0.34f, y - size * 0.42f + bob, x + size * 0.34f, y + size * 0.42f + bob, fill)
        fill.color = 0x88FFFFFF.toInt()
        c.drawOval(x - size * 0.2f, y - size * 0.3f + bob, x - size * 0.04f, y - size * 0.1f + bob, fill)
    }
}

@Composable
fun SpotGame(state: GameState, onFinished: (Int) -> Unit, onBack: () -> Unit) {
    val logic = remember { SpotLogic() }
    val painter = remember { SpotPainter() }
    val ps = remember { ParticleSystem() }
    val clock = remember { GameClock() }
    val look = remember { gameLook(state) }
    val time = rememberFrameTime()
    val tf = rememberCanvasTypeface()
    val haptic = rememberHaptic()
    val p = LocalPalette.current
    val lastT = remember { floatArrayOf(-1f) }
    val geo = remember { FloatArray(5) }

    LaunchedEffect(tf) { ps.typeface = tf }
    LaunchedEffect(logic.over) {
        if (logic.over) {
            clock.finished = true
            delay(900)
            onFinished(logic.found)
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(Color(0xFFFFF1F7), Color(0xFFE9E1FF))))
            .clipToBounds(),
    ) {
        Canvas(
            Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    awaitEachGesture {
                        val down = awaitFirstDown()
                        if (clock.running) {
                            val before = logic.found
                            logic.tap(down.position.x, down.position.y, geo, time.floatValue, ps, size.width.toFloat(), size.height.toFloat())
                            if (logic.found != before) haptic()
                        }
                    }
                },
        ) {
            val t = time.floatValue
            val dt = if (lastT[0] < 0f) 0f else (t - lastT[0]).coerceIn(0f, 0.05f)
            lastT[0] = t
            val w = size.width
            val h = size.height
            val top = h * 0.15f
            val gap = 12.dp.toPx()
            val side = 14.dp.toPx()
            val ph = (h - top - gap - 40.dp.toPx()) / 2f
            val pw = w - side * 2f
            geo[0] = side
            geo[1] = top
            geo[2] = top + ph + gap
            geo[3] = pw
            geo[4] = ph
            if (clock.running) logic.update(t, dt)
            ps.update(t)
            drawIntoCanvas { canvas ->
                val c = canvas.nativeCanvas
                painter.panel(c, logic, false, side, top, pw, ph, look, t)
                painter.panel(c, logic, true, side, top + ph + gap, pw, ph, look, t)
                ps.draw(c, w, h)
            }
        }
        GameHud(
            onBack = onBack,
            score = "${logic.found}",
            timeFraction = logic.timeLeft / SpotLogic.TIME,
            timeText = "${logic.timeLeft.toInt()}s",
            light = false,
            modifier = Modifier.align(Alignment.TopCenter),
        )
        Text(
            "Bild ${logic.roundNo + 1} von ${SpotLogic.ROUNDS} · ${logic.roundFound.coerceAtMost(SpotLogic.PER_ROUND)} / ${SpotLogic.PER_ROUND} gefunden",
            style = MaterialTheme.typography.labelLarge,
            color = p.text,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = 10.dp),
        )
        if (!clock.running && !clock.finished) {
            CountdownOverlay("Finde in jedem Bild 5 Unterschiede!") { clock.running = true }
        }
    }
}

// ====================================================================== word search

private class WordPlacement(val word: String, val r: Int, val c: Int, val dr: Int, val dc: Int) {
    fun cell(i: Int): Pair<Int, Int> = (r + dr * i) to (c + dc * i)
}

private class WordsLogic {
    val grid = Array(N) { CharArray(N) { 'A' } }
    var words: List<WordPlacement> = emptyList()
        private set
    val found = HashSet<String>()
    var score by mutableIntStateOf(0)
    var roundNo by mutableIntStateOf(0)
    var timeLeft by mutableFloatStateOf(TIME)
    var over by mutableStateOf(false)

    /** Bumped whenever the grid changes, so the word list recomposes. */
    var version by mutableIntStateOf(0)
    var selStart: Pair<Int, Int>? = null
    var selEnd: Pair<Int, Int>? = null
    var happyAt = -10f
    var wrongAt = -10f
    private var elapsed = 0f
    private var nextRoundAt = -1f
    private val rnd = Random(System.nanoTime())
    private val used = HashSet<String>()

    init {
        build()
    }

    private fun build() {
        for (row in grid) row.fill(' ')
        val placed = ArrayList<WordPlacement>()
        val candidates = WORDS.filter { it !in used }.ifEmpty { WORDS }.shuffled(rnd)
        for (word in candidates) {
            if (placed.size >= PER_ROUND) break
            if (word.length > N) continue
            for (attempt in 0 until 120) {
                val (dr, dc) = DIRS[rnd.nextInt(DIRS.size)]
                val r = rnd.nextInt(N - dr * (word.length - 1))
                val c = rnd.nextInt(N - dc * (word.length - 1))
                val fits = word.indices.all { i ->
                    val ch = grid[r + dr * i][c + dc * i]
                    ch == ' ' || ch == word[i]
                }
                if (fits) {
                    for (i in word.indices) grid[r + dr * i][c + dc * i] = word[i]
                    placed += WordPlacement(word, r, c, dr, dc)
                    used += word
                    break
                }
            }
        }
        for (r in 0 until N) for (c in 0 until N) if (grid[r][c] == ' ') grid[r][c] = FILL[rnd.nextInt(FILL.length)]
        words = placed
        found.clear()
        selStart = null
        selEnd = null
        version++
    }

    fun update(t: Float, dt: Float) {
        if (over) return
        elapsed += dt
        val left = max(0f, TIME - elapsed)
        if (abs(left - timeLeft) >= 0.1f || left == 0f) timeLeft = left
        if (left <= 0f) {
            over = true
            return
        }
        if (nextRoundAt in 0f..t) {
            nextRoundAt = -1f
            if (roundNo + 1 >= ROUNDS) {
                over = true
            } else {
                roundNo++
                build()
            }
        }
    }

    /** Snaps the end of the selection onto a straight line (row, column or diagonal). */
    fun drag(cell: Pair<Int, Int>) {
        val s = selStart ?: return
        var dr = cell.first - s.first
        var dc = cell.second - s.second
        when {
            dr == 0 || dc == 0 || abs(dr) == abs(dc) -> Unit
            abs(dr) > 2 * abs(dc) -> dc = 0
            abs(dc) > 2 * abs(dr) -> dr = 0
            else -> {
                val len = max(abs(dr), abs(dc))
                dr = len * (if (dr < 0) -1 else 1)
                dc = len * (if (dc < 0) -1 else 1)
            }
        }
        val er = (s.first + dr).coerceIn(0, N - 1)
        val ec = (s.second + dc).coerceIn(0, N - 1)
        // keep the line straight after clamping
        val ldr = er - s.first
        val ldc = ec - s.second
        selEnd = if (ldr == 0 || ldc == 0 || abs(ldr) == abs(ldc)) er to ec else {
            val len = min(abs(ldr), abs(ldc))
            (s.first + len * (if (ldr < 0) -1 else 1)) to (s.second + len * (if (ldc < 0) -1 else 1))
        }
    }

    fun selectionCells(): List<Pair<Int, Int>> {
        val s = selStart ?: return emptyList()
        val e = selEnd ?: return listOf(s)
        val dr = (e.first - s.first).coerceIn(-1, 1)
        val dc = (e.second - s.second).coerceIn(-1, 1)
        val len = max(abs(e.first - s.first), abs(e.second - s.second))
        return (0..len).map { (s.first + dr * it) to (s.second + dc * it) }
    }

    /** Finishes the selection. Returns the word found, or null. */
    fun release(t: Float): String? {
        if (over || nextRoundAt >= 0f) {
            selStart = null
            selEnd = null
            return null
        }
        val cells = selectionCells()
        selStart = null
        selEnd = null
        if (cells.size < 2) return null
        val text = cells.joinToString("") { grid[it.first][it.second].toString() }
        val hit = words.firstOrNull { it.word !in found && (it.word == text || it.word == text.reversed()) }
        if (hit == null) {
            wrongAt = t
            return null
        }
        found += hit.word
        score++
        happyAt = t
        version++
        if (words.all { it.word in found }) nextRoundAt = t + 1.4f
        return hit.word
    }

    companion object {
        const val N = 8
        const val TIME = 150f
        const val ROUNDS = 2
        const val PER_ROUND = 5
        val DIRS = listOf(0 to 1, 1 to 0, 1 to 1, 0 to 1, 1 to 0)
        const val FILL = "ABDEFGHIKLMNOPRSTUW"
        val WORDS = listOf(
            "HASE", "KATZE", "BALL", "STERN", "WOLKE", "APFEL", "KEKS", "BLUME", "MOND", "SONNE",
            "HERZ", "TORTE", "FISCH", "MAUS", "BAUM", "PILZ", "LAMM", "EULE", "IGEL", "BIENE",
            "KUCHEN", "SPIEL", "TANZ", "NEST", "BETT", "MILCH", "HONIG", "REGEN", "DRACHE", "PONY",
        )
        val COLORS = intArrayOf(0x88FF6B9A.toInt(), 0x886EC1FF.toInt(), 0x887DD9B0.toInt(), 0x88FFD166.toInt(), 0x88B39DFF.toInt())
    }
}

private class WordsPainter {
    val renderer = PetRenderer()
    val pose = PetPose()
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val line = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }
    val text = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER }

    fun draw(c: NativeCanvas, logic: WordsLogic, gl: Float, gt: Float, cell: Float, look: PetLook?, t: Float, w: Float, h: Float, ink: Int, card: Int) {
        val n = WordsLogic.N
        fill.color = Colors.alpha(android.graphics.Color.BLACK, 0.1f)
        c.drawRoundRect(gl - cell * 0.2f, gt - cell * 0.2f + cell * 0.08f, gl + cell * (n + 0.2f), gt + cell * (n + 0.2f) + cell * 0.08f, cell * 0.5f, cell * 0.5f, fill)
        fill.color = card
        c.drawRoundRect(gl - cell * 0.2f, gt - cell * 0.2f, gl + cell * (n + 0.2f), gt + cell * (n + 0.2f), cell * 0.5f, cell * 0.5f, fill)
        // found words as coloured capsules
        line.strokeWidth = cell * 0.78f
        for ((i, wp) in logic.words.withIndex()) {
            if (wp.word !in logic.found) continue
            line.color = WordsLogic.COLORS[i % WordsLogic.COLORS.size]
            val a = wp.cell(0)
            val b = wp.cell(wp.word.length - 1)
            c.drawLine(gl + (a.second + 0.5f) * cell, gt + (a.first + 0.5f) * cell, gl + (b.second + 0.5f) * cell, gt + (b.first + 0.5f) * cell, line)
        }
        val sel = logic.selectionCells()
        if (sel.isNotEmpty()) {
            line.color = if (t - logic.wrongAt < 0.3f) 0x88FF5A6E.toInt() else 0x99FFC83D.toInt()
            val a = sel.first()
            val b = sel.last()
            c.drawLine(gl + (a.second + 0.5f) * cell, gt + (a.first + 0.5f) * cell, gl + (b.second + 0.5f) * cell, gt + (b.first + 0.5f) * cell, line)
        }
        text.textSize = cell * 0.56f
        text.color = ink
        for (r in 0 until n) for (col in 0 until n) {
            c.drawText(logic.grid[r][col].toString(), gl + (col + 0.5f) * cell, gt + (r + 0.5f) * cell + cell * 0.2f, text)
        }
        // the word list
        val words = logic.words
        val listTop = gt + cell * (n + 0.9f)
        val colW = (cell * n) / 2f
        text.textSize = cell * 0.5f
        for ((i, wp) in words.withIndex()) {
            val x = gl + colW * (i % 2) + colW / 2f
            val y = listTop + (i / 2) * cell * 0.85f
            val done = wp.word in logic.found
            text.color = if (done) Colors.alpha(ink, 0.35f) else ink
            c.drawText(wp.word, x, y, text)
            if (done) {
                line.strokeWidth = cell * 0.06f
                line.color = Colors.alpha(ink, 0.5f)
                val half = text.measureText(wp.word) / 2f
                c.drawLine(x - half, y - cell * 0.16f, x + half, y - cell * 0.16f, line)
            }
        }
        // the pet cheers
        if (look != null) {
            val p = pose.defaults(look, t)
            val since = t - logic.happyAt
            if (since < 1f) {
                p.eyes = EyeShape.HAPPY
                p.mouth = MouthShape.GRIN
                p.armL = 1f
                p.armR = 1f
                p.lift = sin((since / 1f).coerceIn(0f, 1f) * Math.PI.toFloat()) * 0.3f
            } else if (t - logic.wrongAt < 0.6f) {
                p.eyes = EyeShape.SQUINT
                p.mouth = MouthShape.WAVY
            } else {
                p.mouth = MouthShape.SMILE
                p.lookY = -0.5f
            }
            val size = min(w * 0.22f, h * 0.12f)
            val ground = h - size * 0.25f - 24f
            renderer.drawShadow(c, w * 0.85f, ground, size, p, look)
            renderer.draw(c, w * 0.85f, ground, size, look, p)
        }
    }
}

@Composable
fun WordsGame(state: GameState, onFinished: (Int) -> Unit, onBack: () -> Unit) {
    val logic = remember { WordsLogic() }
    val painter = remember { WordsPainter() }
    val ps = remember { ParticleSystem() }
    val clock = remember { GameClock() }
    val look = remember { gameLook(state) }
    val time = rememberFrameTime()
    val tf = rememberCanvasTypeface()
    val haptic = rememberHaptic()
    val p = LocalPalette.current
    val lastT = remember { floatArrayOf(-1f) }
    // grid left, top, cell size
    val geo = remember { FloatArray(3) }
    val ink = if (p.dark) 0xFFF4ECFF.toInt() else 0xFF3A2A45.toInt()
    val card = if (p.dark) 0xFF2E2648.toInt() else 0xFFFFFFFF.toInt()

    fun cellAt(x: Float, y: Float): Pair<Int, Int> {
        val n = WordsLogic.N
        val c = ((x - geo[0]) / geo[2]).toInt().coerceIn(0, n - 1)
        val r = ((y - geo[1]) / geo[2]).toInt().coerceIn(0, n - 1)
        return r to c
    }

    LaunchedEffect(tf) {
        ps.typeface = tf
        painter.text.typeface = tf
    }
    LaunchedEffect(logic.over) {
        if (logic.over) {
            clock.finished = true
            delay(900)
            onFinished(logic.score)
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(if (p.dark) listOf(Color(0xFF1E1A33), Color(0xFF2A2145)) else listOf(Color(0xFFE6F6FF), Color(0xFFFFF1F7))))
            .clipToBounds(),
    ) {
        Canvas(
            Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectDragGestures(
                        onDragStart = { o ->
                            if (clock.running) {
                                logic.selStart = cellAt(o.x, o.y)
                                logic.selEnd = logic.selStart
                            }
                        },
                        onDragEnd = {
                            val w = logic.release(time.floatValue)
                            if (w != null) {
                                haptic()
                                ps.burst(PKind.CONFETTI, 0.5f, 0.4f, 24, 0.5f, 1.4f, 0.012f, ParticleSystem.CONFETTI_COLORS, gravity = 0.4f, upward = true)
                                ps.add(Particle(PKind.TEXT, 0.5f, 0.12f, 0f, -0.04f, 1.1f, 0.05f, 0xFF3BB273.toInt(), text = "$w! ⭐"))
                            }
                        },
                        onDragCancel = {
                            logic.selStart = null
                            logic.selEnd = null
                        },
                    ) { change, _ ->
                        change.consume()
                        if (logic.selStart != null) logic.drag(cellAt(change.position.x, change.position.y))
                    }
                },
        ) {
            val t = time.floatValue
            val dt = if (lastT[0] < 0f) 0f else (t - lastT[0]).coerceIn(0f, 0.05f)
            lastT[0] = t
            val w = size.width
            val h = size.height
            val gridSize = min(w - 40.dp.toPx(), h * 0.56f)
            val cell = gridSize / WordsLogic.N
            val gl = (w - gridSize) / 2f
            val gt = h * 0.15f
            geo[0] = gl
            geo[1] = gt
            geo[2] = cell
            if (clock.running) logic.update(t, dt)
            ps.update(t)
            drawIntoCanvas { canvas ->
                val c = canvas.nativeCanvas
                painter.draw(c, logic, gl, gt, cell, look, t, w, h, ink, card)
                ps.draw(c, w, h)
            }
        }
        GameHud(
            onBack = onBack,
            score = "${logic.score}",
            timeFraction = logic.timeLeft / WordsLogic.TIME,
            timeText = "${logic.timeLeft.toInt()}s",
            light = false,
            modifier = Modifier.align(Alignment.TopCenter),
        )
        Text(
            "Gitter ${logic.roundNo + 1} von ${WordsLogic.ROUNDS} · wische von Anfang bis Ende eines Wortes",
            style = MaterialTheme.typography.labelMedium,
            color = p.textMuted,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .navigationBarsPadding()
                .padding(start = 16.dp, end = 110.dp, bottom = 12.dp),
        )
        if (!clock.running && !clock.finished) {
            CountdownOverlay("Wische über die Buchstaben, um Wörter zu finden!") { clock.running = true }
        }
    }
}

// ====================================================================== shadow quiz

private class ShadowQuestion(val answer: Form, val options: List<Form>)

private fun shadowQuestion(rnd: Random, avoid: Set<Form>): ShadowQuestion {
    val all = Form.entries.filter { it != Form.EGG }
    val pool = all.filter { it !in avoid }.ifEmpty { all }.shuffled(rnd)
    val answer = pool.first()
    val others = all.shuffled(rnd).filter { it.line != answer.line && it.title != answer.title }.distinctBy { it.line }.take(3)
    return ShadowQuestion(answer, (others + answer).shuffled(rnd))
}

private fun formLook(f: Form): PetLook = PetLook(form = f, mood = Mood.HAPPY, line = f.line, level = 10)

@Composable
fun ShadowGame(state: GameState, onFinished: (Int) -> Unit, onBack: () -> Unit) {
    val p = LocalPalette.current
    val haptic = rememberHaptic()
    val rnd = remember { Random(System.nanoTime()) }
    val asked = remember { HashSet<Form>() }
    var round by remember { mutableIntStateOf(0) }
    var score by remember { mutableIntStateOf(0) }
    var question by remember { mutableStateOf(shadowQuestion(rnd, emptySet())) }
    var chosen by remember { mutableStateOf<Form?>(null) }
    var timedOut by remember { mutableStateOf(false) }
    var timeLeft by remember { mutableFloatStateOf(ROUND_TIME) }
    var running by remember { mutableStateOf(false) }
    var finished by remember { mutableStateOf(false) }
    val revealed = chosen != null || timedOut
    val ownLook = remember { gameLook(state) }

    LaunchedEffect(running, round) {
        if (!running) return@LaunchedEffect
        asked += question.answer
        timeLeft = ROUND_TIME
        while (chosen == null && timeLeft > 0f) {
            delay(100)
            timeLeft = max(0f, timeLeft - 0.1f)
        }
        if (chosen == null) timedOut = true
        delay(1600)
        if (round + 1 >= ROUNDS) {
            // keep running = true: it is a key of this effect
            finished = true
            delay(400)
            onFinished(score)
        } else {
            question = shadowQuestion(rnd, asked)
            chosen = null
            timedOut = false
            round++
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(if (p.dark) listOf(Color(0xFF1B1636), Color(0xFF3A2A5E)) else listOf(Color(0xFFFFF4D8), Color(0xFFFFE0EE)))),
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(top = 88.dp, start = 16.dp, end = 16.dp)
                .navigationBarsPadding(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                when {
                    !revealed -> "Wessen Schatten ist das?"
                    chosen == question.answer -> "Richtig! Das ist ${question.answer.title} 🎉"
                    else -> "Das war ${question.answer.title}!"
                },
                style = MaterialTheme.typography.titleLarge,
                color = p.text,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(8.dp))
            Box(
                Modifier
                    .size(210.dp)
                    .clip(CircleShape)
                    .background(Brush.radialGradient(listOf(Color.White.copy(alpha = 0.95f), Color(0xFFFFE9A6).copy(alpha = 0.6f), Color.Transparent))),
                contentAlignment = Alignment.Center,
            ) {
                PetPortrait(
                    formLook(question.answer),
                    Modifier.fillMaxSize(),
                    sizeFactor = 0.62f,
                    silhouette = if (revealed) null else 0xFF2A2140.toInt(),
                ) { pose, t ->
                    if (revealed && chosen == question.answer) {
                        pose.eyes = EyeShape.HAPPY
                        pose.mouth = MouthShape.GRIN
                        val ph = t % 0.9f
                        if (ph < 0.35f) pose.lift = sin(ph / 0.35f * Math.PI.toFloat()) * 0.25f
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            for (row in question.options.chunked(2)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    for (f in row) {
                        val right = f == question.answer
                        val color = when {
                            revealed && right -> p.mint.copy(alpha = 0.55f)
                            revealed && f == chosen -> p.red.copy(alpha = 0.45f)
                            else -> p.surface
                        }
                        SurfaceCard(
                            Modifier.weight(1f).padding(bottom = 10.dp),
                            onClick = if (running && !revealed) ({
                                chosen = f
                                if (right) {
                                    score++
                                    haptic()
                                }
                            }) else null,
                            color = color,
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(8.dp),
                        ) {
                            PetPortrait(formLook(f), Modifier.fillMaxWidth().height(86.dp), sizeFactor = 0.7f, animated = revealed && right)
                            Text(
                                f.title,
                                style = MaterialTheme.typography.titleSmall,
                                color = p.text,
                                textAlign = TextAlign.Center,
                                maxLines = 1,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }
                }
            }
            Spacer(Modifier.weight(1f))
            if (ownLook != null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    PetPortrait(ownLook, Modifier.size(64.dp), sizeFactor = 0.8f) { pose, _ ->
                        if (revealed) {
                            pose.eyes = if (chosen == question.answer) EyeShape.STAR else EyeShape.SQUINT
                            pose.mouth = if (chosen == question.answer) MouthShape.GRIN else MouthShape.WAVY
                        } else {
                            pose.lookY = -0.6f
                        }
                    }
                    Text(
                        "Frage ${round + 1} von $ROUNDS",
                        style = MaterialTheme.typography.labelLarge,
                        color = p.textMuted,
                    )
                }
                Spacer(Modifier.height(8.dp))
            }
        }
        GameHud(
            onBack = onBack,
            score = "$score",
            timeFraction = timeLeft / ROUND_TIME,
            timeText = "${timeLeft.toInt()}s",
            light = false,
            modifier = Modifier.align(Alignment.TopCenter),
        )
        if (!running && !finished) {
            CountdownOverlay("Erkennst du die Wesen an ihrem Schatten?") { running = true }
        }
    }
}

private const val ROUND_TIME = 10f
private const val ROUNDS = 10
