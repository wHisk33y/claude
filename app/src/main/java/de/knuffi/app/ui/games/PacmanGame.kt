package de.knuffi.app.ui.games

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import de.knuffi.app.data.GameRepository
import de.knuffi.app.render.EyeShape
import de.knuffi.app.render.MouthShape
import de.knuffi.app.render.PKind
import de.knuffi.app.render.Particle
import de.knuffi.app.render.ParticleSystem
import de.knuffi.app.render.PetPose
import de.knuffi.app.render.PetRenderer
import de.knuffi.app.sound.Sfx
import de.knuffi.app.sound.Sounds
import de.knuffi.app.ui.rememberCanvasTypeface
import de.knuffi.app.ui.rememberFrameTime
import de.knuffi.core.Action
import de.knuffi.core.GameState
import de.knuffi.core.Secret
import kotlinx.coroutines.delay
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.sin
import kotlin.random.Random

private val MAZE = listOf(
    "#############",
    "#o....#....o#",
    "#.##.###.##.#",
    "#...........#",
    "#.##.#.#.##.#",
    "#....#.#....#",
    "####.#.#.####",
    "#.....G.....#",
    "####.#.#.####",
    "#....#.#....#",
    "#.##.#.#.##.#",
    "#...........#",
    "#.##.###.##.#",
    "#o....P....o#",
    "#############",
)

private enum class Dir(val dx: Int, val dy: Int) { NONE(0, 0), UP(0, -1), DOWN(0, 1), LEFT(-1, 0), RIGHT(1, 0) }

private class Mover(var cx: Int, var cy: Int, val homeX: Int, val homeY: Int) {
    var dir = Dir.NONE
    var progress = 0f // 0..1 towards the next cell
    var eatenUntil = 0f
    val x: Float get() = cx + dir.dx * progress
    val y: Float get() = cy + dir.dy * progress
    fun reset() {
        cx = homeX
        cy = homeY
        dir = Dir.NONE
        progress = 0f
    }
}

@Composable
fun PacmanGame(state: GameState, onFinished: (Int) -> Unit, onBack: () -> Unit) {
    val look = remember { gameLook(state) }
    val renderer = remember { PetRenderer() }
    val pose = remember { PetPose() }
    val time = rememberFrameTime()
    val ps = remember { ParticleSystem() }
    val tf = rememberCanvasTypeface()
    LaunchedEffect(tf) { ps.typeface = tf }
    val paint = remember { emojiPaint() }
    val rnd = remember { Random(System.nanoTime()) }
    val rows = MAZE.size
    val cols = MAZE[0].length
    fun wall(x: Int, y: Int) = y !in 0 until rows || x !in 0 until cols || MAZE[y][x] == '#'

    val dots = remember { HashSet<Int>() }
    val cookies = remember { HashSet<Int>() }
    val start = remember {
        var px = 0
        var py = 0
        var gx = 0
        var gy = 0
        for (y in 0 until rows) for (x in 0 until cols) when (MAZE[y][x]) {
            'P' -> { px = x; py = y }
            'G' -> { gx = x; gy = y }
        }
        intArrayOf(px, py, gx, gy)
    }
    fun refill() {
        dots.clear()
        cookies.clear()
        for (y in 0 until rows) for (x in 0 until cols) when (MAZE[y][x]) {
            '.' -> dots += y * cols + x
            'o' -> cookies += y * cols + x
        }
    }
    val pet = remember { Mover(start[0], start[1], start[0], start[1]) }
    val ghosts = remember { List(3) { Mover(start[2] + it - 1, start[3], start[2] + it - 1, start[3]) } }
    var nextDir by remember { mutableStateOf(Dir.NONE) }
    var score by remember { mutableIntStateOf(0) }
    var lives by remember { mutableIntStateOf(3) }
    var level by remember { mutableIntStateOf(1) }
    var scaredUntil by remember { mutableFloatStateOf(0f) }
    var pauseUntil by remember { mutableFloatStateOf(0f) }
    var last by remember { mutableFloatStateOf(-1f) }
    var started by remember { mutableStateOf(false) }
    var over by remember { mutableStateOf(false) }
    var lostLife by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { refill() }
    LaunchedEffect(over) {
        if (over) {
            delay(1000)
            onFinished(score)
        }
    }

    fun canGo(m: Mover, d: Dir) = d != Dir.NONE && !wall(m.cx + d.dx, m.cy + d.dy)

    fun step(m: Mover, speed: Float, dt: Float, choose: (Mover) -> Dir) {
        if (m.dir == Dir.NONE) {
            m.dir = choose(m)
            if (m.dir == Dir.NONE) return
        }
        m.progress += speed * dt
        while (m.progress >= 1f) {
            m.cx += m.dir.dx
            m.cy += m.dir.dy
            m.progress -= 1f
            val d = choose(m)
            m.dir = d
            if (d == Dir.NONE) {
                m.progress = 0f
                break
            }
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(Color(0xFF2A1F5E), Color(0xFF4A2E8A), Color(0xFF1A1440))))
            .pointerInput(Unit) {
                var acc = Offset.Zero
                detectDragGestures(onDragStart = { acc = Offset.Zero }) { change, drag ->
                    change.consume()
                    acc += drag
                    if (hypot(acc.x, acc.y) > 30f) {
                        nextDir = if (abs(acc.x) > abs(acc.y)) (if (acc.x > 0) Dir.RIGHT else Dir.LEFT) else (if (acc.y > 0) Dir.DOWN else Dir.UP)
                        acc = Offset.Zero
                    }
                }
            },
    ) {
        Column(Modifier.fillMaxSize().navigationBarsPadding(), horizontalAlignment = Alignment.CenterHorizontally) {
            Spacer(Modifier.height(96.dp))
            Text("Level $level · Wische, um die Richtung zu wechseln", style = MaterialTheme.typography.labelLarge, color = Color.White, textAlign = TextAlign.Center)
            Spacer(Modifier.height(8.dp))
            Canvas(Modifier.fillMaxWidth().padding(horizontal = 8.dp).aspectRatio(cols / rows.toFloat())) {
                val t = time.floatValue
                val dt = if (last < 0f) 0f else (t - last).coerceIn(0f, 0.05f)
                last = t
                val scared = t < scaredUntil
                if (started && !over && t > pauseUntil) {
                    // pet
                    step(pet, 4.6f, dt) { m ->
                        when {
                            canGo(m, nextDir) -> nextDir
                            canGo(m, m.dir) -> m.dir
                            else -> Dir.NONE
                        }
                    }
                    // eat
                    val cell = pet.cy * cols + pet.cx
                    if (dots.remove(cell)) {
                        score += 10
                        if (dots.size % 4 == 0) Sounds.play(Sfx.CLICK)
                    }
                    if (cookies.remove(cell)) {
                        score += 50
                        scaredUntil = t + 7f
                        Sounds.play(Sfx.SUCCESS)
                        ps.burst(PKind.SPARKLE, pet.x / cols, pet.y / rows, 8, 0.2f, 0.7f, 0.02f, ParticleSystem.SPARKLE_COLORS)
                    }
                    // ghosts
                    for ((i, g) in ghosts.withIndex()) {
                        if (t < g.eatenUntil) continue
                        val gSpeed = (if (scared) 2.2f else 3.2f + level * 0.25f + i * 0.15f).coerceAtMost(4.4f)
                        step(g, gSpeed, dt) { m ->
                            val options = Dir.entries.filter { it != Dir.NONE && canGo(m, it) && !(it.dx == -m.dir.dx && it.dy == -m.dir.dy && m.dir != Dir.NONE) }
                                .ifEmpty { Dir.entries.filter { it != Dir.NONE && canGo(m, it) } }
                            if (options.isEmpty()) Dir.NONE
                            else if (rnd.nextFloat() < 0.3f + i * 0.1f) options[rnd.nextInt(options.size)]
                            else {
                                fun dist(d: Dir) = hypot((m.cx + d.dx - pet.cx).toFloat(), (m.cy + d.dy - pet.cy).toFloat())
                                if (scared) options.maxBy { dist(it) } else options.minBy { dist(it) }
                            }
                        }
                        if (hypot(g.x - pet.x, g.y - pet.y) < 0.6f) {
                            if (scared) {
                                score += 100
                                g.reset()
                                g.eatenUntil = t + 3f
                                Sounds.play(Sfx.POP)
                                ps.add(Particle(PKind.TEXT, g.x / cols, g.y / rows, 0f, -0.05f, 1f, 0.05f, 0xFFFFE066.toInt(), text = "+100"))
                            } else {
                                lives--
                                lostLife = true
                                Sounds.play(Sfx.BONK)
                                if (lives <= 0) {
                                    over = true
                                } else {
                                    pet.reset()
                                    ghosts.forEach { it.reset() }
                                    nextDir = Dir.NONE
                                    pauseUntil = t + 1.2f
                                }
                                break
                            }
                        }
                    }
                    if (dots.isEmpty() && cookies.isEmpty()) {
                        score += 200
                        if (!lostLife) GameRepository.perform(Action.FindSecret(Secret.PERFECT_PACMAN))
                        level++
                        Sounds.play(Sfx.FANFARE)
                        refill()
                        pet.reset()
                        ghosts.forEach { it.reset() }
                        nextDir = Dir.NONE
                        pauseUntil = t + 1.5f
                    }
                }
                ps.update(t)
                // draw
                val cw = size.width / cols
                val ch = size.height / rows
                drawRoundRect(Color(0xFF1A1440), cornerRadius = CornerRadius(24f))
                for (y in 0 until rows) for (x in 0 until cols) {
                    if (MAZE[y][x] == '#') {
                        drawRoundRect(
                            Brush.verticalGradient(listOf(Color(0xFF8A7CFF), Color(0xFF5A48D6)), startY = y * ch, endY = (y + 1) * ch),
                            Offset(x * cw + cw * 0.08f, y * ch + ch * 0.08f), Size(cw * 0.84f, ch * 0.84f), CornerRadius(cw * 0.3f),
                        )
                    }
                }
                for (c in dots) drawCircle(Color(0xFFFFD6E8), cw * 0.1f, Offset((c % cols + 0.5f) * cw, (c / cols + 0.5f) * ch))
                for (c in cookies) emoji(paint, "🍪", (c % cols + 0.5f) * cw, (c / cols + 0.5f) * ch, cw * (0.7f + 0.08f * sin(t * 6f)))
                for (g in ghosts) {
                    if (t < g.eatenUntil) continue
                    val gx = (g.x + 0.5f) * cw
                    val gy = (g.y + 0.5f) * ch
                    if (scared) drawCircle(Color(0x664FA8FF), cw * 0.5f, Offset(gx, gy))
                    emoji(paint, if (scared) "😱" else "👻", gx, gy, cw * 0.85f, if (scared && scaredUntil - t < 2f && (t * 6).toInt() % 2 == 0) 0.4f else 1f)
                }
                look?.let { l ->
                    pose.defaults(l, t)
                    pose.mouth = if ((t * 8).toInt() % 2 == 0) MouthShape.OPEN else MouthShape.CAT
                    pose.eyes = if (scared) EyeShape.STAR else EyeShape.OPEN
                    pose.turn = pet.dir.dx * 0.6f
                    drawIntoCanvas { renderer.draw(it.nativeCanvas, (pet.x + 0.5f) * cw, (pet.y + 0.95f) * ch, cw * 1.25f, l, pose) }
                }
                drawIntoCanvas { ps.draw(it.nativeCanvas, size.width, size.height) }
            }
        }
        Box(Modifier.align(Alignment.TopCenter)) {
            GameHud(onBack = onBack, score = "$score", lives = lives)
        }
        if (!started) CountdownOverlay("Wische in eine Richtung. Kekse machen die Gespenster harmlos!") { started = true }
    }
}
