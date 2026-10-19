package de.knuffi.app.ui

import android.graphics.Paint
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import de.knuffi.app.data.GameRepository
import de.knuffi.app.render.PetLook
import de.knuffi.app.render.PetPose
import de.knuffi.app.render.PetRenderer
import de.knuffi.app.ui.components.ClayTextButton
import de.knuffi.app.ui.components.EmojiTile
import de.knuffi.app.ui.components.PageHeader
import de.knuffi.app.ui.components.Pill
import de.knuffi.app.ui.components.SectionHeader
import de.knuffi.app.ui.components.SegmentedControl
import de.knuffi.app.ui.components.SurfaceCard
import de.knuffi.app.ui.components.staggered
import de.knuffi.app.ui.theme.LocalPalette
import de.knuffi.core.Action
import de.knuffi.core.Engine
import de.knuffi.core.GameState
import de.knuffi.core.House
import de.knuffi.core.RoomKind
import kotlinx.coroutines.launch
import kotlin.math.sin

/** Floor colours of the rooms on the plan. */
private fun roomColor(k: RoomKind): Color = when (k) {
    RoomKind.LIVING -> Color(0xFFFFE0C2)
    RoomKind.KITCHEN -> Color(0xFFFFF2B8)
    RoomKind.BEDROOM -> Color(0xFFE6DCFF)
    RoomKind.BATH -> Color(0xFFCFF0FF)
    RoomKind.PLAYROOM -> Color(0xFFFFD6E8)
    RoomKind.MUSIC -> Color(0xFFDDE6FF)
    RoomKind.GARDEN -> Color(0xFFBDEBA8)
    RoomKind.CRAFT -> Color(0xFFFFE6CC)
}

/**
 * The whole house from above: every room with the pets inside. Tap a room and your pet walks
 * there through the doors; tap a free spot to build a new room.
 */
@Composable
fun HouseScreen(state: GameState, onBack: () -> Unit, onEnter: () -> Unit) {
    val p = LocalPalette.current
    val scope = rememberCoroutineScope()
    val walk = remember { Animatable(0f) }
    var route by remember { mutableStateOf<List<RoomKind>>(emptyList()) }
    var askBuild by remember { mutableStateOf<RoomKind?>(null) }
    val renderer = remember { PetRenderer() }
    val pose = remember { PetPose() }
    val emoji = remember { Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER } }
    val time = rememberFrameTime()
    val houseOn = House.enabled(state)
    val unlocked = House.unlocked(state)
    val pet = state.pet
    val petLook = remember(state.pet, state.equipped) { PetLook.of(state)?.copy(sleeping = false) }

    fun go(target: RoomKind) {
        if (route.isNotEmpty()) return
        val s = GameRepository.current
        val cur = s.house.current
        if (target == cur) {
            onEnter()
            return
        }
        val pp = s.pet
        if (pp != null && pp.alive && pp.sleeping && target != RoomKind.BEDROOM) {
            GameRepository.message("${pp.name} schläft gerade im Schlafzimmer. Psst! 😴")
            return
        }
        val path = House.path(cur, target, House.unlocked(s))
        if (path.size < 2) return
        route = path
        scope.launch {
            walk.snapTo(0f)
            walk.animateTo((path.size - 1).toFloat(), tween(650 * (path.size - 1), easing = LinearEasing))
            GameRepository.perform(Action.GoToRoom(target))
            route = emptyList()
            onEnter()
        }
    }

    Column(Modifier.fillMaxSize()) {
        PageHeader("Dein Haus", onBack) { Pill("🪙 ${state.coins}", color = p.gold.copy(alpha = 0.25f)) }
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SegmentedControl(
                listOf("🏠 Ganzes Haus", "🛏️ Ein Zimmer"),
                if (state.settings.houseMode) 0 else 1,
                { GameRepository.perform(Action.UpdateSettings(GameRepository.current.settings.copy(houseMode = it == 0))) },
            )
            if (!houseOn) {
                SurfaceCard(Modifier.fillMaxWidth()) {
                    Text(
                        "Gerade wohnt ${pet?.name ?: "dein Haustier"} im klassischen Kinderzimmer. Schalte oben „Ganzes Haus“ ein: Dann gibt es Küche, Schlafzimmer und mehr, mit Türen dazwischen.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = p.textMuted,
                    )
                }
                return@Column
            }
            Text(
                "Tippe auf ein Zimmer, dann läuft ${pet?.name ?: "dein Haustier"} durch die Türen dorthin. Graue Felder kannst du ausbauen.",
                style = MaterialTheme.typography.bodySmall,
                color = p.textMuted,
            )
            Canvas(
                Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .staggered(0)
                    .pointerInput(unlocked) {
                        detectTapGestures { pos ->
                            val cell = size.width / House.COLS.toFloat()
                            val col = (pos.x / cell).toInt().coerceIn(0, House.COLS - 1)
                            val row = (pos.y / cell).toInt().coerceIn(0, House.ROWS - 1)
                            val k = RoomKind.entries.firstOrNull { r -> House.cells(r).any { it.first == col && it.second == row } } ?: return@detectTapGestures
                            if (k in House.unlocked(GameRepository.current)) go(k) else askBuild = k
                        }
                    },
            ) {
                val t = time.floatValue
                val cell = size.width / House.COLS
                val pad = 5.dp.toPx()
                val wall = 5.dp.toPx()
                fun rect(k: RoomKind): Pair<Offset, Size> {
                    val l = k.col * cell + pad
                    val tp = k.row * cell + pad
                    return Offset(l, tp) to Size(k.cols * cell - pad * 2, cell - pad * 2)
                }
                fun center(k: RoomKind): Offset {
                    val (o, s) = rect(k)
                    return Offset(o.x + s.width / 2f, o.y + s.height / 2f)
                }
                // the plot of land
                drawRoundRect(Color(0xFFCFEFC0), Offset.Zero, size, CornerRadius(18.dp.toPx()))
                for (k in RoomKind.entries) {
                    val (o, s) = rect(k)
                    if (k in unlocked) {
                        drawRoundRect(Color(0x33000000), o + Offset(0f, 3.dp.toPx()), s, CornerRadius(12.dp.toPx()))
                        drawRoundRect(roomColor(k), o, s, CornerRadius(12.dp.toPx()))
                        if (!k.outdoor) drawRoundRect(Color(0xFF6A5A80), o, s, CornerRadius(12.dp.toPx()), style = Stroke(wall))
                        if (k == state.house.current) drawRoundRect(Color(0xFFFF6FA8), o - Offset(2.dp.toPx(), 2.dp.toPx()), Size(s.width + 4.dp.toPx(), s.height + 4.dp.toPx()), CornerRadius(14.dp.toPx()), style = Stroke(3.dp.toPx()))
                    } else {
                        drawRoundRect(Color(0x66FFFFFF), o, s, CornerRadius(12.dp.toPx()))
                        drawRoundRect(Color(0xFF9A8FB0), o, s, CornerRadius(12.dp.toPx()), style = Stroke(2.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(14f, 10f))))
                    }
                }
                // doors between built rooms
                for (k in unlocked) {
                    for (d in House.doors(k, unlocked)) {
                        if (d.to.ordinal < k.ordinal) continue
                        val dc = House.cells(k).firstOrNull { (ac, ar) -> House.cells(d.to).any { (bc, br) -> kotlin.math.abs(bc - ac) + kotlin.math.abs(br - ar) == 1 } } ?: continue
                        val other = House.cells(d.to).first { (bc, br) -> kotlin.math.abs(bc - dc.first) + kotlin.math.abs(br - dc.second) == 1 }
                        val mx = (dc.first + other.first + 1) / 2f * cell
                        val my = (dc.second + other.second + 1) / 2f * cell
                        val horizontal = dc.second == other.second
                        val dw = cell * 0.22f
                        val dh = wall * 2.6f
                        val doorSize = if (horizontal) Size(dh, dw) else Size(dw, dh)
                        drawRoundRect(Color(0xFFE8B888), Offset(mx - doorSize.width / 2f, my - doorSize.height / 2f), doorSize, CornerRadius(3.dp.toPx()))
                    }
                }
                // labels, residents and the pet
                drawIntoCanvas { canvas ->
                    val c = canvas.nativeCanvas
                    for (k in RoomKind.entries) {
                        val (o, s) = rect(k)
                        val built = k in unlocked
                        emoji.color = android.graphics.Color.BLACK
                        emoji.alpha = if (built) 255 else 110
                        emoji.textSize = cell * 0.2f
                        c.drawText(k.emoji, o.x + s.width / 2f, o.y + cell * 0.3f, emoji)
                        emoji.alpha = 255
                        emoji.textSize = cell * 0.085f
                        emoji.color = android.graphics.Color.argb(220, 58, 42, 69)
                        c.drawText(k.title, o.x + s.width / 2f, o.y + s.height - cell * 0.06f, emoji)
                        if (!built) {
                            emoji.textSize = cell * 0.08f
                            c.drawText(if (Engine.playerLevel(state) < k.minLevel) "🔒 Lv ${k.minLevel}" else "➕ ${k.price} 🪙", o.x + s.width / 2f, o.y + s.height / 2f + cell * 0.08f, emoji)
                            continue
                        }
                        val who = House.residentsIn(state, k)
                        for ((i, r) in who.take(4).withIndex()) {
                            val l = PetLook.of(r, state).copy(sleeping = false, hat = null, face = null, neck = null, back = null)
                            pose.defaults(l, t + i)
                            val px = o.x + s.width * (0.2f + 0.2f * i)
                            renderer.draw(c, px, o.y + s.height * 0.78f, cell * 0.2f, l, pose)
                        }
                    }
                    // your pet: in its room or on the way through the doors
                    val l = petLook
                    if (l != null) {
                        val r = route
                        val at = if (r.size >= 2) {
                            val v = walk.value.coerceIn(0f, (r.size - 1).toFloat())
                            val i = v.toInt().coerceAtMost(r.size - 2)
                            val f = v - i
                            val a = center(r[i])
                            val b = center(r[i + 1])
                            Offset(a.x + (b.x - a.x) * f, a.y + (b.y - a.y) * f)
                        } else {
                            center(state.house.current)
                        }
                        pose.defaults(l, t)
                        if (r.size >= 2) {
                            val ph = t * 12f
                            pose.strideL = kotlin.math.cos(ph) * 0.8f
                            pose.strideR = -pose.strideL
                            pose.lift = kotlin.math.abs(sin(ph)) * 0.08f
                        } else if (pet?.sleeping == true) {
                            pose.eyes = de.knuffi.app.render.EyeShape.CLOSED
                        }
                        renderer.draw(c, at.x, at.y + cell * 0.2f, cell * 0.3f, l, pose)
                    }
                }
            }
            SectionHeader("Zimmer", "🚪")
            for ((i, k) in RoomKind.entries.withIndex()) {
                val built = k in unlocked
                val who = House.residentsIn(state, k)
                SurfaceCard(Modifier.fillMaxWidth().staggered(i + 1), onClick = { if (built) go(k) else askBuild = k }) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        EmojiTile(k.emoji, size = 46.dp, color = if (built) p.mint else p.track)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(k.title, style = MaterialTheme.typography.titleSmall, color = p.text)
                            Text(k.description, style = MaterialTheme.typography.bodySmall, color = p.textMuted)
                            if (built) {
                                val names = (if (state.house.current == k) listOfNotNull(pet?.name) else emptyList()) + who.map { it.name }
                                if (names.isNotEmpty()) Text("Hier: ${names.joinToString(", ")}", style = MaterialTheme.typography.labelMedium, color = p.mintDeep)
                            }
                        }
                        Spacer(Modifier.width(8.dp))
                        if (built) {
                            ClayTextButton(if (state.house.current == k) "Ansehen" else "Hingehen", { go(k) }, small = true, color = p.pink, deep = p.pinkDeep)
                        } else {
                            ClayTextButton("${k.price} 🪙", { askBuild = k }, small = true, color = p.gold, deep = p.goldDeep, enabled = Engine.playerLevel(state) >= k.minLevel)
                        }
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
        }
    }

    askBuild?.let { k ->
        val blocker = House.buildBlocker(state, k)
        ConfirmDialog(
            title = "${k.emoji} ${k.title} bauen?",
            text = k.description + "\n\n" + (blocker ?: "Kostet ${k.price} Münzen."),
            confirm = if (blocker == null) "Bauen" else "Okay",
            onConfirm = {
                if (blocker == null) GameRepository.perform(Action.BuildRoom(k))
                askBuild = null
            },
            onDismiss = { askBuild = null },
        )
    }
}
