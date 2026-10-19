package de.knuffi.app.ui

import android.graphics.Paint
import android.graphics.RectF
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
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
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import de.knuffi.app.data.GameRepository
import de.knuffi.app.debug.DebugScenes
import de.knuffi.app.render.PetLook
import de.knuffi.app.render.PetPose
import de.knuffi.app.render.PetRenderer
import de.knuffi.app.render.house3d.Camera3
import de.knuffi.app.render.house3d.House3
import de.knuffi.app.render.house3d.HouseModel3
import de.knuffi.app.render.house3d.Item3
import de.knuffi.app.render.house3d.Layer3
import de.knuffi.app.render.house3d.M4
import de.knuffi.app.render.house3d.Mat
import de.knuffi.app.render.house3d.ModelBuilder
import de.knuffi.app.render.house3d.OrbitView
import de.knuffi.app.render.house3d.Placed
import de.knuffi.app.render.house3d.RenderOptions
import de.knuffi.app.render.house3d.Room3
import de.knuffi.app.render.house3d.Room3d
import de.knuffi.app.render.house3d.RoomModel
import de.knuffi.app.render.house3d.Scene3
import de.knuffi.app.render.house3d.Specs3
import de.knuffi.app.ui.components.ClayTextButton
import de.knuffi.app.ui.components.GlassPanel
import de.knuffi.app.ui.components.PageHeader
import de.knuffi.app.ui.components.Pill
import de.knuffi.app.ui.theme.LocalPalette
import de.knuffi.core.Action
import de.knuffi.core.Catalog
import de.knuffi.core.Engine
import de.knuffi.core.GameState
import de.knuffi.core.House
import de.knuffi.core.RoomKind
import de.knuffi.core.Slot
import de.knuffi.core.Spot
import java.time.ZoneId
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max

/** True if the house and rooms are shown in 3D right now. */
fun house3dOn(state: GameState): Boolean = state.settings.house3d && Room3d.available

/** Keeps the camera and the picture of a 3D view between frames. */
private class View3State(val orbit: OrbitView) {
    val layer = Layer3()
    var version = 0
}

/** Draws the newest 3D picture and asks for a new one when the camera or the scene changed. */
private fun drawScene(c: android.graphics.Canvas, w: Float, h: Float, st: View3State, scene: Scene3, opt: RenderOptions, paint: Paint) {
    val k = (1100f / max(w, h)).coerceIn(0.4f, 1f)
    val o = st.orbit
    val token = "${st.version}:${o.yaw}:${o.pitch}:${o.dist}:${o.cx}:${o.cz}:${w.toInt()}x${h.toInt()}"
    if (st.layer.requested != token) Room3d.request(st.layer, scene, o.camera((w * k).toInt().coerceAtLeast(1), (h * k).toInt().coerceAtLeast(1)), opt, token)
    st.layer.take()?.let { c.drawBitmap(it, null, RectF(0f, 0f, w, h), paint) }
}

/**
 * The house as a little 3D dollhouse. Drag to turn it, pinch to zoom, tap a room to choose it
 * (or a free plot to build there).
 */
@Composable
fun House3dOverview(
    state: GameState,
    modifier: Modifier,
    selected: RoomKind?,
    walkFrom: List<RoomKind>,
    walk: Float,
    onSelect: (RoomKind) -> Unit,
    onPlot: (RoomKind) -> Unit,
) {
    val p = LocalPalette.current
    val st = remember { View3State(House3.overview()) }
    val time = rememberFrameTime()
    val event = remember { DebugScenes.eventOverride ?: Engine.activeEvent(System.currentTimeMillis(), ZoneId.systemDefault()) }
    val house: HouseModel3 = remember(state.house, state.equipped, state.garden, state.settings.look) {
        val built = House.unlocked(state)
        val level = Engine.playerLevel(state)
        val specs = RoomKind.entries.filter { it in built }.map { Specs3.room(state, it, event) }
        val locked = RoomKind.entries.filter { it !in built && level < it.minLevel }.toSet()
        House3.build(specs, locked).also { st.version++ }
    }
    // the chosen room glows a little
    remember(house, selected) {
        for (r in house.rooms) for (it in r.items) if (it.tag == "floor") it.tint = if (r.kind == selected) 0x30FF6FA8 else 0
        st.version++
    }
    val pets = remember { HashMap<Long, Pair<PetRenderer, PetPose>>() }
    val petLook = remember(state.pet, state.equipped) { PetLook.of(state)?.copy(sleeping = false) }
    val paint = remember { Paint(Paint.FILTER_BITMAP_FLAG) }
    val label = remember { Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER } }
    val pill = remember { Paint(Paint.ANTI_ALIAS_FLAG) }
    val houseNow by rememberUpdatedState(house)
    val onSel by rememberUpdatedState(onSelect)
    val onPl by rememberUpdatedState(onPlot)
    val dark = p.dark

    Canvas(
        modifier
            .pointerInput(Unit) {
                detectTapGestures { pos ->
                    val cam = st.orbit.camera(this.size.width, this.size.height)
                    val hit = cam.onPlane(pos.x, pos.y, 0f) ?: return@detectTapGestures
                    val k = houseNow.roomAt(hit[0], hit[2]) ?: return@detectTapGestures
                    if (houseNow.rooms.any { it.kind == k }) onSel(k) else onPl(k)
                }
            }
            .pointerInput(Unit) {
                detectTransformGestures { _, pan, zoom, rotation ->
                    val o = st.orbit
                    o.yaw -= pan.x * 0.008f + rotation * 0.0175f
                    o.pitch = (o.pitch + pan.y * 0.005f).coerceIn(0.38f, 1.4f)
                    o.dist = (o.dist / zoom).coerceIn(7f, 30f)
                }
            },
    ) {
        val t = time.floatValue
        drawIntoCanvas { canvas ->
            val c = canvas.nativeCanvas
            drawScene(c, size.width, size.height, st, house.scene, RenderOptions(clear = if (dark) 0xFF2E3A4A.toInt() else 0xFFCFEFC0.toInt()), paint)
            val cam = st.orbit.camera(size.width.toInt().coerceAtLeast(1), size.height.toInt().coerceAtLeast(1))
            // the pets: yours (maybe walking through the house) and the ones living here
            data class Who(val id: Long, val look: PetLook, val x: Float, val z: Float, val walking: Boolean, val sleeping: Boolean)
            val who = ArrayList<Who>()
            val pet = state.pet
            if (petLook != null && pet != null) {
                val (x, z) = if (walkFrom.size >= 2) {
                    val v = walk.coerceIn(0f, (walkFrom.size - 1).toFloat())
                    val i = v.toInt().coerceAtMost(walkFrom.size - 2)
                    val f = v - i
                    val a = House3.center(walkFrom[i])
                    val b = House3.center(walkFrom[i + 1])
                    (a.first + (b.first - a.first) * f) to (a.second + (b.second - a.second) * f)
                } else {
                    House3.center(state.house.current).let { (cx, cz) -> cx + 0.2f to cz + 0.35f }
                }
                who += Who(pet.id, petLook, x, z, walkFrom.size >= 2, pet.sleeping)
            }
            for (r in house.rooms) {
                val list = House.residentsIn(state, r.kind)
                for ((i, rp) in list.take(3).withIndex()) {
                    val l = PetLook.of(rp, state).copy(sleeping = false, hat = null, face = null, neck = null, back = null)
                    val ox = ((rp.id % 7) - 3) * 0.18f + (i - 1) * 0.5f
                    val oz = ((rp.id / 7 % 5) - 2) * 0.15f
                    who += Who(rp.id, l, r.cx + ox.coerceIn(-0.9f, 0.9f), r.cz + oz - 0.3f, false, false)
                }
            }
            val placed = who.mapNotNull { w ->
                val feet = cam.project(w.x, 0f, w.z) ?: return@mapNotNull null
                val head = cam.project(w.x, 0.95f, w.z) ?: return@mapNotNull null
                Triple(w, feet, feet[1] - head[1])
            }.sortedByDescending { it.second[2] }
            for ((w, feet, hgt) in placed) {
                val (r, pose) = pets.getOrPut(w.id) { PetRenderer() to PetPose() }
                pose.defaults(w.look, t + (w.id % 10))
                if (w.walking) {
                    val ph = t * 12f
                    pose.strideL = kotlin.math.cos(ph) * 0.8f
                    pose.strideR = -pose.strideL
                    pose.lift = abs(kotlin.math.sin(ph)) * 0.08f
                } else if (w.sleeping) {
                    pose.eyes = de.knuffi.app.render.EyeShape.CLOSED
                }
                r.draw(c, feet[0], feet[1], hgt.coerceAtLeast(8f), w.look, pose)
            }
            // names of the rooms
            label.textSize = size.width * 0.034f
            for (r in house.rooms) {
                val at = cam.project(r.cx, 1.9f, r.cz - 0.6f) ?: continue
                val text = "${r.kind.emoji} ${r.kind.title}"
                val tw = label.measureText(text)
                pill.color = if (r.kind == selected) 0xF0FF6FA8.toInt() else 0xD9FFFFFF.toInt()
                c.drawRoundRect(at[0] - tw / 2f - 14f, at[1] - label.textSize, at[0] + tw / 2f + 14f, at[1] + label.textSize * 0.45f, 40f, 40f, pill)
                label.color = if (r.kind == selected) android.graphics.Color.WHITE else 0xFF3A2A45.toInt()
                c.drawText(text, at[0], at[1], label)
            }
            for (pl in house.plots) {
                val at = cam.project((pl.rect.x0 + pl.rect.x1) / 2f, 0.2f, (pl.rect.z0 + pl.rect.z1) / 2f + 0.55f) ?: continue
                val k = pl.kind
                val text = if (Engine.playerLevel(state) < k.minLevel) "${k.title} · Lv ${k.minLevel}" else "${k.title} · ${k.price} 🪙"
                label.textSize = size.width * 0.028f
                label.color = 0xFF3A2A45.toInt()
                c.drawText(text, at[0], at[1], label)
                label.textSize = size.width * 0.034f
            }
        }
    }
}

// ------------------------------------------------------------------ furnishing

/**
 * Furnishing a room in 3D: drag furniture over the floor (pictures along the wall), turn it,
 * look around by dragging next to it.
 */
@Composable
fun FurnishScreen(state: GameState, room: RoomKind?, onBack: () -> Unit) {
    val p = LocalPalette.current
    val event = remember { DebugScenes.eventOverride ?: Engine.activeEvent(System.currentTimeMillis(), ZoneId.systemDefault()) }
    val spec = remember(state.house, state.equipped, state.garden, room, state.settings.houseMode) { Specs3.room(state, room, event) }
    val preview = remember { mutableStateMapOf<Slot, Spot>() }
    var selected by remember { mutableStateOf<Slot?>(null) }
    val st = remember { View3State(OrbitView(yaw = 0f, pitch = 0.82f, dist = if (room?.outdoor == true) 9.5f else 6.4f, cy = 0.35f)) }
    val time = rememberFrameTime()
    // the stored spots arrived: the preview is not needed any more
    LaunchedEffect(spec) { preview.clear() }
    val model: RoomModel = remember(spec, preview.toMap(), selected) {
        val m = Room3.build(spec.copy(spots = spec.spots + preview), Room3.Mode.HOUSE)
        selected?.let { s -> m.placed[s]?.item?.tint = 0x38FF6FA8 }
        st.version++
        m
    }
    val scene = remember(model) {
        Scene3().also { sc ->
            val g = ModelBuilder()
            g.with(Mat(0xFFFFFFFF.toInt(), 0.05f, tex = "lawn|", shadow = false)) {
                quad(-8f, -0.13f, 8f, 8f, -0.13f, 8f, 8f, -0.13f, -8f, -8f, -0.13f, -8f, -6.6f, -6.6f, 6.6f, 6.6f)
            }
            sc.add(g.build(), M4.identity(), "ground")
            sc.items += model.items
        }
    }
    val modelNow by rememberUpdatedState(model)
    val roomNow by rememberUpdatedState(room)
    val paint = remember { Paint(Paint.FILTER_BITMAP_FLAG) }
    val dark = p.dark

    fun spotOf(pl: Placed): Spot = Room3.roomToSpot(pl.x, if (pl.piece.onWall) pl.y else pl.z, modelNow.w, modelNow.d, pl.piece, pl.turn)

    /** Saves the moved piece (and pins down the others, so nothing else jumps). */
    fun commit(slot: Slot, spot: Spot) {
        val m = modelNow
        for ((s, pl) in m.placed) {
            if (s == slot) continue
            if (s !in m.spec.spots) GameRepository.perform(Action.PlaceFurniture(roomNow, s, spotOf(pl)))
        }
        GameRepository.perform(Action.PlaceFurniture(roomNow, slot, spot))
    }

    fun pick(cam: Camera3, x: Float, y: Float): Placed? {
        val m = modelNow
        val hits = m.placed.values.filter { pl ->
            val r = cam.rect(pl.item.bounds) ?: return@filter false
            x >= r[0] && x <= r[2] && y >= r[1] && y <= r[3]
        }
        // solid furniture before rugs, then the nearest
        return hits.minByOrNull { pl ->
            val b = pl.item.bounds
            val d = cam.project((b.x0 + b.x1) / 2f, (b.y0 + b.y1) / 2f, (b.z0 + b.z1) / 2f)?.get(2) ?: 99f
            d + if (pl.piece.flat) 100f else 0f
        }
    }

    Column(Modifier.fillMaxSize()) {
        PageHeader("Einrichten", onBack) { Pill("${room?.emoji ?: "🏠"} ${room?.title ?: "Kinderzimmer"}", color = p.violet.copy(alpha = 0.16f)) }
        Box(Modifier.fillMaxWidth().weight(1f).padding(horizontal = 12.dp).clip(RoundedCornerShape(28.dp))) {
            Canvas(
                Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        awaitEachGesture {
                            val down = awaitFirstDown(requireUnconsumed = false)
                            val cam = st.orbit.camera(this.size.width, this.size.height)
                            val hit = pick(cam, down.position.x, down.position.y)
                            var grabX = 0f
                            var grabZ = 0f
                            if (hit != null) {
                                if (hit.piece.onWall) {
                                    val (o, d) = cam.ray(down.position.x, down.position.y)
                                    val t = (-modelNow.d / 2f - o[2]) / (if (abs(d[2]) < 1e-4f) 1e-4f else d[2])
                                    grabX = o[0] + d[0] * t - hit.x
                                    grabZ = o[1] + d[1] * t - hit.y
                                } else {
                                    cam.onPlane(down.position.x, down.position.y, 0f)?.let {
                                        grabX = it[0] - hit.x
                                        grabZ = it[2] - hit.z
                                    }
                                }
                                selected = hit.slot
                            }
                            var moved = false
                            var last = down.position
                            var pinch = 0f
                            var newSpot: Spot? = null
                            while (true) {
                                val ev = awaitPointerEvent()
                                val pressed = ev.changes.filter { it.pressed }
                                if (pressed.isEmpty()) break
                                if (pressed.size >= 2) {
                                    val a = pressed[0].position
                                    val b = pressed[1].position
                                    val dist = hypot(a.x - b.x, a.y - b.y)
                                    if (pinch > 0f) st.orbit.dist = (st.orbit.dist * pinch / dist).coerceIn(3f, 16f)
                                    pinch = dist
                                    ev.changes.forEach { it.consume() }
                                    moved = true
                                    continue
                                }
                                pinch = 0f
                                val ch = pressed.first()
                                val pos = ch.position
                                if ((pos - down.position).getDistance() > 10f) moved = true
                                val m = modelNow
                                if (hit != null) {
                                    val piece = hit.piece
                                    val c2 = st.orbit.camera(this.size.width, this.size.height)
                                    if (piece.onWall) {
                                        val (o, d) = c2.ray(pos.x, pos.y)
                                        val t = (-m.d / 2f - o[2]) / (if (abs(d[2]) < 1e-4f) 1e-4f else d[2])
                                        val (x, _) = Room3.clampInside(piece, o[0] + d[0] * t - grabX, 0f, 0f, m.w, m.d)
                                        val y = (o[1] + d[1] * t - grabZ).coerceIn(Room3.PIC_Y0, Room3.PIC_Y1)
                                        if (Room3.fits(m, hit.slot, piece, x, y, 0f)) {
                                            val s = Room3.roomToSpot(x, y, m.w, m.d, piece, 0f)
                                            preview[hit.slot] = s
                                            newSpot = s
                                        }
                                    } else {
                                        val at = c2.onPlane(pos.x, pos.y, 0f)
                                        if (at != null) {
                                            val turn = preview[hit.slot]?.turn ?: m.placed[hit.slot]?.turn ?: hit.turn
                                            val (x, z) = Room3.clampInside(piece, at[0] - grabX, at[2] - grabZ, turn, m.w, m.d)
                                            if (Room3.fits(m, hit.slot, piece, x, z, turn)) {
                                                val s = Room3.roomToSpot(x, z, m.w, m.d, piece, turn)
                                                preview[hit.slot] = s
                                                newSpot = s
                                            }
                                        }
                                    }
                                } else {
                                    st.orbit.yaw -= (pos.x - last.x) * 0.008f
                                    st.orbit.pitch = (st.orbit.pitch + (pos.y - last.y) * 0.005f).coerceIn(0.3f, 1.45f)
                                }
                                last = pos
                                ch.consume()
                            }
                            val s = newSpot
                            if (hit != null && moved && s != null) commit(hit.slot, s)
                            if (hit == null && !moved) selected = null
                        }
                    },
            ) {
                time.floatValue
                drawIntoCanvas { canvas ->
                    drawScene(canvas.nativeCanvas, size.width, size.height, st, scene, RenderOptions(clear = if (dark) 0xFF2E3A4A.toInt() else 0xFFCFEFC0.toInt()), paint)
                }
            }
        }
        GlassPanel(Modifier.fillMaxWidth().padding(12.dp).navigationBarsPadding(), shape = RoundedCornerShape(26.dp)) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                val sel = selected
                val id = sel?.let { model.placed[it]?.piece?.id }
                Text(
                    if (id != null) "${Catalog[id]?.emoji ?: ""} ${Catalog[id]?.name ?: ""}" else "🛋️ Möbel verschieben",
                    style = MaterialTheme.typography.titleMedium,
                    color = p.text,
                )
                Text(
                    if (sel != null) "Zieh es mit dem Finger an einen freien Platz${if (model.placed[sel]?.piece?.onWall == true) " an der Wand" else ""}." else "Tippe ein Möbelstück an und zieh es herum. Wische daneben, um dich umzusehen, und zoome mit zwei Fingern.",
                    style = MaterialTheme.typography.bodySmall,
                    color = p.textMuted,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    val canTurn = sel != null && model.placed[sel]?.piece?.onWall == false
                    ClayTextButton(
                        "Drehen",
                        {
                            val s = selected ?: return@ClayTextButton
                            val pl = model.placed[s] ?: return@ClayTextButton
                            var ok = false
                            for (step in 1..7) {
                                val turn = (pl.turn + 45f * step) % 360f
                                val (x, z) = Room3.clampInside(pl.piece, pl.x, pl.z, turn, model.w, model.d)
                                if (Room3.fits(model, s, pl.piece, x, z, turn)) {
                                    commit(s, Room3.roomToSpot(x, z, model.w, model.d, pl.piece, turn))
                                    ok = true
                                    break
                                }
                            }
                            if (!ok) GameRepository.message("Hier ist kein Platz zum Drehen.")
                        },
                        Modifier.weight(1f),
                        emoji = "↻",
                        small = true,
                        enabled = canTurn,
                        color = p.violet,
                        deep = p.violetDeep,
                    )
                    ClayTextButton(
                        "Neu",
                        {
                            preview.clear()
                            selected = null
                            GameRepository.perform(Action.ResetPlaces(room))
                        },
                        Modifier.weight(1f),
                        emoji = "✨",
                        small = true,
                        color = p.sky,
                        deep = p.skyDeep,
                    )
                    ClayTextButton("Fertig", onBack, Modifier.weight(1f), emoji = "✅", small = true, color = p.mint, deep = p.mintDeep)
                }
            }
        }
    }
}
