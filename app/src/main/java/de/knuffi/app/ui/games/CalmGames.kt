package de.knuffi.app.ui.games

import android.graphics.Bitmap
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.knuffi.app.render.EyeShape
import de.knuffi.app.render.LayoutKind
import de.knuffi.app.render.MouthShape
import de.knuffi.app.render.PetDirector
import de.knuffi.app.render.PetPose
import de.knuffi.app.render.PetRenderer
import de.knuffi.app.render.SceneModel
import de.knuffi.app.render.SceneRenderer
import de.knuffi.app.sound.Sfx
import de.knuffi.app.sound.Sounds
import de.knuffi.app.ui.PetPortrait
import de.knuffi.app.ui.components.ClayTextButton
import de.knuffi.app.ui.components.lighter
import de.knuffi.app.ui.components.rememberHaptic
import de.knuffi.app.ui.theme.LocalPalette
import de.knuffi.core.GameState
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.random.Random

/** Soft background and a hint card for the calm games. */
@Composable
private fun CalmFrame(title: String, hint: String, score: String, onBack: () -> Unit, colors: List<Color>, content: @Composable () -> Unit) {
    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(colors))) {
        Column(Modifier.fillMaxSize().navigationBarsPadding(), horizontalAlignment = Alignment.CenterHorizontally) {
            Spacer(Modifier.height(96.dp))
            Text(title, style = MaterialTheme.typography.headlineSmall, color = Color(0xFF3A2E5A))
            Text(hint, style = MaterialTheme.typography.bodyMedium, color = Color(0xFF6A5E8A), textAlign = TextAlign.Center, modifier = Modifier.padding(horizontal = 24.dp))
            Spacer(Modifier.height(12.dp))
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) { content() }
        }
        Box(Modifier.align(Alignment.TopCenter)) { GameHud(onBack = onBack, score = score, light = false) }
    }
}

// ================================================================== Schiebepuzzle

@Composable
fun PuzzleGame(state: GameState, onFinished: (Int) -> Unit, onBack: () -> Unit) {
    val haptic = rememberHaptic()
    val n = 3
    val image = remember { petPicture(state, 600) }
    val rnd = remember { Random(System.nanoTime()) }
    // tiles[pos] = tile number, n*n-1 is the hole
    val tiles = remember {
        val list = MutableList(n * n) { it }
        var hole = n * n - 1
        repeat(120) {
            val moves = neighbours(hole, n)
            val m = moves[rnd.nextInt(moves.size)]
            list[hole] = list[m]
            list[m] = n * n - 1
            hole = m
        }
        mutableStateListOf<Int>().apply { addAll(list) }
    }
    var moves by remember { mutableIntStateOf(0) }
    var solved by remember { mutableStateOf(false) }
    LaunchedEffect(solved) {
        if (solved) {
            Sounds.play(Sfx.FANFARE)
            delay(1400)
            onFinished(moves)
        }
    }
    CalmFrame("Schiebepuzzle", "Tippe ein Teil neben der Lücke an, um es zu verschieben.", "$moves Züge", onBack, listOf(Color(0xFFE8F4FF), Color(0xFFFFE8F4))) {
        BoxWithConstraints(Modifier.fillMaxWidth(0.88f).aspectRatio(1f).shadow(10.dp, RoundedCornerShape(20.dp)).clip(RoundedCornerShape(20.dp)).background(Color(0xFFD8CCEC))) {
            val cell = maxWidth / n
            for (pos in 0 until n * n) {
                val tile = tiles[pos]
                if (tile == n * n - 1 && !solved) continue
                val x by animateFloatAsState((pos % n).toFloat(), spring(dampingRatio = 0.7f, stiffness = 700f), label = "x")
                val y by animateFloatAsState((pos / n).toFloat(), spring(dampingRatio = 0.7f, stiffness = 700f), label = "y")
                Canvas(
                    Modifier
                        .size(cell)
                        .offset(cell * x, cell * y)
                        .padding(2.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .clickable(enabled = !solved) {
                            val hole = tiles.indexOf(n * n - 1)
                            if (pos in neighbours(hole, n)) {
                                tiles[hole] = tile
                                tiles[pos] = n * n - 1
                                moves++
                                haptic()
                                Sounds.play(Sfx.CLICK)
                                if (tiles.withIndex().all { (i, v) -> i == v }) solved = true
                            }
                        },
                ) {
                    val src = IntSize(image.width / n, image.height / n)
                    drawImage(
                        image,
                        srcOffset = IntOffset((tile % n) * src.width, (tile / n) * src.height),
                        srcSize = src,
                        dstSize = IntSize(size.width.roundToInt(), size.height.roundToInt()),
                    )
                    if (!solved) drawRect(Color.White.copy(alpha = 0.6f), style = Stroke(3f))
                }
            }
        }
    }
}

private fun neighbours(i: Int, n: Int): List<Int> = buildList {
    val r = i / n
    val c = i % n
    if (r > 0) add(i - n)
    if (r < n - 1) add(i + n)
    if (c > 0) add(i - 1)
    if (c < n - 1) add(i + 1)
}

/** A square picture of the pet in its room, used for the puzzle. */
private fun petPicture(state: GameState, px: Int): ImageBitmap {
    val bmp = Bitmap.createBitmap(px, px, Bitmap.Config.ARGB_8888)
    val canvas = android.graphics.Canvas(bmp)
    val scene = SceneRenderer()
    val director = PetDirector(3)
    val model = SceneModel().apply {
        room = de.knuffi.app.render.Furnishing.roomId(state)
        look = de.knuffi.app.render.PetLook.of(state)?.copy(mood = de.knuffi.core.Mood.HAPPY, sleeping = false)
        hour = 11f
        showBubble = false
        furniture = de.knuffi.app.render.Furnishing.of(state, null)
    }
    director.update(1f, model.look, emptyList(), de.knuffi.app.render.ParticleSystem(), 0.5f, 0.5f)
    scene.draw(canvas, px.toFloat(), px.toFloat(), LayoutKind.BOX, model, director, null, 1f)
    scene.release()
    return bmp.asImageBitmap()
}

// ================================================================== Ausmalbild (Mandala)

private val PAINT_COLORS = listOf(
    Color(0xFFFF8FB8), Color(0xFFFFB38A), Color(0xFFFFE066), Color(0xFF9BE3A8),
    Color(0xFF7FD3FF), Color(0xFFB99AFF), Color(0xFFFFFFFF), Color(0xFF5A4A8A),
)

private class Region(val ring: Int, val index: Int, val count: Int)

@Composable
fun ColoringGame(state: GameState, onFinished: (Int) -> Unit, onBack: () -> Unit) {
    val haptic = rememberHaptic()
    val rings = remember { intArrayOf(1, 6, 8, 12, 16) }
    val regions = remember { rings.withIndex().flatMap { (r, c) -> List(c) { Region(r, it, c) } } }
    val colors = remember { mutableStateListOf<Color?>().apply { repeat(regions.size) { add(null) } } }
    var selected by remember { mutableStateOf(PAINT_COLORS[0]) }
    val filled = colors.count { it != null }
    val percent = filled * 100 / regions.size
    var finished by remember { mutableStateOf(false) }
    val look = remember { gameLook(state) }
    var complete by remember { mutableStateOf(false) }
    if (percent >= 100 && !finished) {
        finished = true
        complete = true
    }
    LaunchedEffect(complete) {
        if (complete) {
            Sounds.play(Sfx.FANFARE)
            delay(1500)
            onFinished(100)
        }
    }
    CalmFrame("Ausmalbild", "Wähle eine Farbe und tippe auf die Felder. Kein Zeitdruck!", "$percent %", onBack, listOf(Color(0xFFFFF6E8), Color(0xFFF0E8FF))) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Canvas(
                Modifier
                    .fillMaxWidth(0.9f)
                    .aspectRatio(1f)
                    .pointerInput(Unit) {
                        detectTapGestures { pos ->
                            val cx = size.width / 2f
                            val cy = size.height / 2f
                            val r = hypot(pos.x - cx, pos.y - cy) / (size.width / 2f)
                            val ring = (r * rings.size).toInt()
                            if (ring >= rings.size) return@detectTapGestures
                            val ang = ((atan2(pos.y - cy, pos.x - cx) / (2 * PI) + 1.0) % 1.0)
                            val idx = (ang * rings[ring]).toInt().coerceIn(0, rings[ring] - 1)
                            val i = regions.indexOfFirst { it.ring == ring && it.index == idx }
                            if (i >= 0) {
                                colors[i] = selected
                                haptic()
                                Sounds.play(Sfx.POP, 1.2f)
                            }
                        }
                    },
            ) {
                val cx = size.width / 2f
                val cy = size.height / 2f
                val ringW = size.width / 2f / rings.size
                for ((i, reg) in regions.withIndex()) {
                    val r0 = reg.ring * ringW
                    val r1 = (reg.ring + 1) * ringW
                    val sweep = 360f / reg.count
                    val start = reg.index * sweep
                    val path = Path()
                    if (reg.ring == 0) {
                        path.addOval(androidx.compose.ui.geometry.Rect(Offset(cx, cy), r1))
                    } else {
                        path.arcTo(androidx.compose.ui.geometry.Rect(Offset(cx, cy), r1), start, sweep, true)
                        path.arcTo(androidx.compose.ui.geometry.Rect(Offset(cx, cy), r0), start + sweep, -sweep, false)
                        path.close()
                    }
                    drawPath(path, colors[i] ?: Color.White)
                    drawPath(path, Color(0xFF6A5E8A), style = Stroke(2.5f))
                }
                // petal outlines for a mandala look
                for (k in 0 until 12) {
                    val a = k * PI.toFloat() / 6f
                    drawCircle(Color(0xFF6A5E8A).copy(alpha = 0.25f), ringW * 0.5f, Offset(cx + cos(a) * ringW * 3.5f, cy + sin(a) * ringW * 3.5f), style = Stroke(2f))
                }
            }
            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for (c in PAINT_COLORS) {
                    val active = c == selected
                    Box(
                        Modifier
                            .size(if (active) 40.dp else 34.dp)
                            .shadow(if (active) 6.dp else 2.dp, CircleShape)
                            .clip(CircleShape)
                            .background(c)
                            .border(if (active) 3.dp else 1.dp, if (active) Color(0xFF3A2E5A) else Color(0x33000000), CircleShape)
                            .clickable { selected = c },
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                PetPortrait(look, Modifier.size(80.dp), sizeFactor = 0.8f) { pose, _ ->
                    pose.eyes = EyeShape.HAPPY
                    pose.holding = "🖍️"
                    pose.hold = 1f
                }
                if (percent >= 50 && !finished) {
                    ClayTextButton("Fertig ($percent %)", {
                        finished = true
                        onFinished(percent)
                    }, emoji = "🖼️", small = true)
                }
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

// ================================================================== Wollknäuel entwirren

private fun crosses(a: Offset, b: Offset, c: Offset, d: Offset): Boolean {
    fun ccw(p: Offset, q: Offset, r: Offset) = (r.y - p.y) * (q.x - p.x) - (q.y - p.y) * (r.x - p.x)
    val d1 = ccw(a, b, c)
    val d2 = ccw(a, b, d)
    val d3 = ccw(c, d, a)
    val d4 = ccw(c, d, b)
    return ((d1 > 0 && d2 < 0) || (d1 < 0 && d2 > 0)) && ((d3 > 0 && d4 < 0) || (d3 < 0 && d4 > 0))
}

@Composable
fun YarnGame(state: GameState, onFinished: (Int) -> Unit, onBack: () -> Unit) {
    val haptic = rememberHaptic()
    val rnd = remember { Random(System.nanoTime()) }
    val count = 8
    // A planar graph: a ring plus chords from node 0 (never crossing on the circle).
    val edges = remember {
        buildList {
            for (i in 0 until count) add(i to (i + 1) % count)
            for (k in listOf(2, 4, 6)) add(0 to k)
            add(2 to 4)
            add(4 to 6)
        }
    }
    val nodes = remember {
        mutableStateListOf<Offset>().apply {
            // Scramble: shuffled positions on the circle make the threads cross.
            val order = (0 until count).shuffled(rnd)
            for (i in 0 until count) {
                val a = order[i] * 2 * PI / count
                add(Offset(0.5f + 0.38f * cos(a).toFloat(), 0.5f + 0.38f * sin(a).toFloat()))
            }
        }
    }
    var moves by remember { mutableIntStateOf(0) }
    var drag by remember { mutableIntStateOf(-1) }
    var done by remember { mutableStateOf(false) }
    fun crossing(): Set<Int> {
        val out = HashSet<Int>()
        for (i in edges.indices) for (j in i + 1 until edges.size) {
            val (a, b) = edges[i]
            val (c, d) = edges[j]
            if (a == c || a == d || b == c || b == d) continue
            if (crosses(nodes[a], nodes[b], nodes[c], nodes[d])) {
                out += i
                out += j
            }
        }
        return out
    }
    val bad = crossing()
    // Solved as soon as no thread crosses any more (also while the finger still holds a knot).
    if (bad.isEmpty() && !done && (moves > 0 || drag >= 0)) done = true
    LaunchedEffect(done) {
        if (done) {
            drag = -1
            Sounds.play(Sfx.FANFARE)
            delay(1400)
            onFinished(moves.coerceAtLeast(1))
        }
    }
    CalmFrame("Wollknäuel", "Zieh die Knäuel, bis sich kein Faden mehr kreuzt. Rote Fäden kreuzen sich noch.", "${bad.size / 2} Knoten", onBack, listOf(Color(0xFFFFF0F6), Color(0xFFE8F6FF))) {
        Canvas(
            Modifier
                .fillMaxWidth(0.94f)
                .aspectRatio(0.9f)
                .pointerInput(Unit) {
                    detectDragGestures(
                        onDragStart = { pos ->
                            val p = Offset(pos.x / size.width, pos.y / size.height)
                            drag = nodes.indices.minByOrNull { hypot(nodes[it].x - p.x, nodes[it].y - p.y) }?.takeIf { hypot(nodes[it].x - p.x, nodes[it].y - p.y) < 0.09f } ?: -1
                            if (drag >= 0) haptic()
                        },
                        onDragEnd = {
                            if (drag >= 0) {
                                moves++
                                Sounds.play(Sfx.POP, 0.8f)
                            }
                            drag = -1
                        },
                    ) { change, _ ->
                        if (drag >= 0 && !done) nodes[drag] = Offset((change.position.x / size.width).coerceIn(0.05f, 0.95f), (change.position.y / size.height).coerceIn(0.05f, 0.95f))
                    }
                },
        ) {
            for ((i, e) in edges.withIndex()) {
                val a = nodes[e.first]
                val b = nodes[e.second]
                val col = if (i in bad) Color(0xFFE0405A) else Color(0xFF7A5AD6)
                drawLine(col.copy(alpha = 0.35f), Offset(a.x * size.width, a.y * size.height + 3f), Offset(b.x * size.width, b.y * size.height + 3f), strokeWidth = 12f)
                drawLine(col, Offset(a.x * size.width, a.y * size.height), Offset(b.x * size.width, b.y * size.height), strokeWidth = 7f)
            }
            val yarns = listOf(Color(0xFFFF6F91), Color(0xFFFFB84D), Color(0xFF7FD3FF), Color(0xFF9BE3A8), Color(0xFFB99AFF))
            for ((i, n) in nodes.withIndex()) {
                val c = Offset(n.x * size.width, n.y * size.height)
                val r = size.width * (if (i == drag) 0.06f else 0.05f)
                val col = yarns[i % yarns.size]
                drawCircle(Brush.radialGradient(listOf(col.lighter(0.4f), col), center = Offset(c.x - r * 0.3f, c.y - r * 0.3f), radius = r * 1.3f), r, c)
                for (k in -1..1) drawArc(Color.White.copy(alpha = 0.5f), 200f + k * 15f, 140f, false, Offset(c.x - r * 0.8f, c.y - r * (0.4f + 0.15f * k)), Size(r * 1.6f, r * (0.8f - 0.3f * k)), style = Stroke(2.5f))
            }
        }
    }
}

// ================================================================== Leckerli-Reihe (3 gewinnt)

private val TREATS = listOf("🍬", "🍩", "🍪", "🧁", "🍭", "🍓")

@Composable
fun MatchGame(state: GameState, onFinished: (Int) -> Unit, onBack: () -> Unit) {
    val haptic = rememberHaptic()
    val scope = rememberCoroutineScope()
    val rnd = remember { Random(System.nanoTime()) }
    val n = 7
    val kinds = 5
    val grid = remember {
        mutableStateListOf<Int>().apply {
            repeat(n * n) { i ->
                var k: Int
                do {
                    k = rnd.nextInt(kinds)
                } while ((i % n >= 2 && this[i - 1] == k && this[i - 2] == k) || (i / n >= 2 && this[i - n] == k && this[i - 2 * n] == k))
                add(k)
            }
        }
    }
    val pop = remember { mutableStateListOf<Boolean>().apply { repeat(n * n) { add(false) } } }
    var selected by remember { mutableIntStateOf(-1) }
    var score by remember { mutableIntStateOf(0) }
    var movesLeft by remember { mutableIntStateOf(20) }
    var busy by remember { mutableStateOf(false) }
    var over by remember { mutableStateOf(false) }
    val look = remember { gameLook(state) }

    fun matches(): Set<Int> {
        val out = HashSet<Int>()
        for (r in 0 until n) {
            var c = 0
            while (c < n) {
                var e = c
                while (e + 1 < n && grid[r * n + e + 1] == grid[r * n + c] && grid[r * n + c] >= 0) e++
                if (e - c >= 2) for (k in c..e) out += r * n + k
                c = e + 1
            }
        }
        for (c in 0 until n) {
            var r = 0
            while (r < n) {
                var e = r
                while (e + 1 < n && grid[(e + 1) * n + c] == grid[r * n + c] && grid[r * n + c] >= 0) e++
                if (e - r >= 2) for (k in r..e) out += k * n + c
                r = e + 1
            }
        }
        return out
    }

    suspend fun resolve() {
        var chain = 1
        while (true) {
            val m = matches()
            if (m.isEmpty()) break
            for (i in m) pop[i] = true
            Sounds.play(Sfx.POP, 1f + chain * 0.1f)
            delay(220)
            score += m.size * chain
            for (i in m) {
                grid[i] = -1
                pop[i] = false
            }
            // gravity
            for (c in 0 until n) {
                val col = (0 until n).map { grid[it * n + c] }.filter { it >= 0 }
                val missing = n - col.size
                for (r in 0 until n) grid[r * n + c] = if (r < missing) rnd.nextInt(kinds) else col[r - missing]
            }
            delay(180)
            chain++
        }
    }

    fun tap(i: Int) {
        if (busy || over) return
        haptic()
        if (selected < 0) {
            selected = i
            return
        }
        val a = selected
        selected = -1
        if (a == i) return
        val adjacent = (abs(a - i) == 1 && a / n == i / n) || abs(a - i) == n
        if (!adjacent) {
            selected = i
            return
        }
        busy = true
        scope.launch {
            val t = grid[a]
            grid[a] = grid[i]
            grid[i] = t
            delay(150)
            if (matches().isEmpty()) {
                grid[i] = grid[a]
                grid[a] = t
                Sounds.play(Sfx.BONK, 1.4f)
            } else {
                movesLeft--
                resolve()
                if (movesLeft <= 0) {
                    over = true
                    Sounds.play(Sfx.SUCCESS)
                    delay(700)
                    onFinished(score)
                }
            }
            busy = false
        }
    }

    CalmFrame("Leckerli-Reihe", "Tippe zwei Nachbarn an, um sie zu tauschen. Drei gleiche verschwinden!", "$score", onBack, listOf(Color(0xFFFFE8F0), Color(0xFFFFF4D8))) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("Noch $movesLeft Züge", style = MaterialTheme.typography.titleMedium, color = Color(0xFF6A3A5A))
            Spacer(Modifier.height(8.dp))
            Column(
                Modifier
                    .fillMaxWidth(0.94f)
                    .shadow(8.dp, RoundedCornerShape(22.dp))
                    .clip(RoundedCornerShape(22.dp))
                    .background(Color(0xFFFFF8FC))
                    .padding(6.dp),
            ) {
                for (r in 0 until n) {
                    Row(Modifier.fillMaxWidth()) {
                        for (c in 0 until n) {
                            val i = r * n + c
                            val k = grid[i]
                            val sel = i == selected
                            val s by animateFloatAsState(if (pop[i]) 1.4f else if (sel) 1.15f else 1f, spring(dampingRatio = 0.5f), label = "s")
                            Box(
                                Modifier
                                    .weight(1f)
                                    .aspectRatio(1f)
                                    .padding(2.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(if (sel) Color(0xFFFFD6E8) else if ((r + c) % 2 == 0) Color(0xFFFFF0F6) else Color(0xFFFCE4EE))
                                    .clickable { tap(i) },
                                contentAlignment = Alignment.Center,
                            ) {
                                if (k >= 0) Text(TREATS[k], fontSize = 24.sp, modifier = Modifier.graphicsLayer {
                                    scaleX = s
                                    scaleY = s
                                    alpha = if (pop[i]) 0.4f else 1f
                                })
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
            PetPortrait(look, Modifier.size(90.dp), sizeFactor = 0.8f) { pose, _ ->
                pose.eyes = if (busy) EyeShape.STAR else EyeShape.HAPPY
                pose.mouth = if (busy) MouthShape.OPEN else MouthShape.TONGUE
            }
        }
    }
}
