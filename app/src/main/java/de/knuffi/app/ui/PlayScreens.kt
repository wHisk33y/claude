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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.knuffi.app.data.GameRepository
import de.knuffi.app.render.EyeShape
import de.knuffi.app.render.MouthShape
import de.knuffi.app.render.PetLook
import de.knuffi.app.render.PetPose
import de.knuffi.app.render.PetRenderer
import de.knuffi.app.ui.components.ClayTextButton
import de.knuffi.app.ui.components.CoinPill
import de.knuffi.app.ui.components.EmojiTile
import de.knuffi.app.ui.components.GlossyBar
import de.knuffi.app.ui.components.PageHeader
import de.knuffi.app.ui.components.Pill
import de.knuffi.app.ui.components.SurfaceCard
import de.knuffi.app.ui.components.staggered
import de.knuffi.app.ui.theme.LocalPalette
import de.knuffi.core.Action
import de.knuffi.core.Adventure
import de.knuffi.core.AdventureRegion
import de.knuffi.core.GameEvent
import de.knuffi.core.GameState
import de.knuffi.core.Mood
import de.knuffi.core.Trick
import de.knuffi.core.Tricks
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin

// ------------------------------------------------------------------ tricks

/** A pose for a trick shown on its own (portraits and cards). [el] is the time since the trick started. */
fun trickPose(pose: PetPose, trick: Trick, el: Float, success: Boolean) {
    if (!success) {
        pose.tilt += sin(el * 16f) * 10f
        pose.eyes = if (el > 1.1f) EyeShape.DIZZY else EyeShape.SQUINT
        pose.mouth = if (el > 1.1f) MouthShape.WAVY else MouthShape.TONGUE
        return
    }
    val p = (el / 2.2f).coerceIn(0f, 1f)
    pose.eyes = EyeShape.HAPPY
    pose.mouth = MouthShape.OPEN
    when (trick) {
        Trick.SITZ -> {
            pose.scaleY *= 0.88f
            pose.scaleX *= 1.06f
            pose.mouth = MouthShape.SMILE
        }
        Trick.PFOTE -> {
            pose.armR = 0.55f
            pose.armWaveR = -20f + sin(el * 6f) * 6f
        }
        Trick.WINKEN -> {
            pose.armL = 0.95f
            pose.armR = 0.95f
            pose.armWaveL = sin(el * 14f) * 26f
            pose.armWaveR = -sin(el * 14f) * 26f
        }
        Trick.DREHEN -> {
            pose.turn = sin(el * 9f)
            pose.armL = 0.7f
            pose.armR = 0.7f
            pose.eyes = EyeShape.STAR
        }
        Trick.MAENNCHEN -> {
            pose.lift += 0.18f
            pose.scaleY *= 1.12f
            pose.scaleX *= 0.94f
            pose.armL = 0.45f
            pose.armR = 0.45f
        }
        Trick.SPRUNG -> {
            val ph = el % 1.2f
            if (ph < 0.6f) pose.lift += sin(ph / 0.6f * PI.toFloat()) * 0.7f
            pose.armL = 1f
            pose.armR = 1f
            pose.eyes = EyeShape.STAR
        }
        Trick.ROLLE -> {
            val r = ((el % 2f) / 1.4f).coerceIn(0f, 1f)
            pose.tilt += 360f * (r * r * (3f - 2f * r))
            pose.eyes = if (r in 0.05f..0.95f) EyeShape.CLOSED else EyeShape.HAPPY
            pose.mouth = MouthShape.GRIN
        }
        Trick.VERBEUGUNG -> {
            val k = sin(p * PI.toFloat())
            pose.tilt += 28f * k
            pose.lookY = k
            pose.eyes = if (k > 0.5f) EyeShape.CLOSED else EyeShape.HAPPY
            pose.mouth = MouthShape.SMILE
        }
        Trick.TANZ -> {
            val k = el * 7f
            pose.tilt += sin(k) * 12f
            pose.lift += abs(sin(k)) * 0.1f
            pose.armL = 0.75f + 0.25f * sin(k)
            pose.armR = 0.75f - 0.25f * sin(k)
            pose.turn = sin(k * 0.5f) * 0.7f
        }
        Trick.SALTO -> {
            val ph = el % 1.6f
            val r = (ph / 0.9f).coerceIn(0f, 1f)
            if (ph < 0.9f) pose.lift += sin(r * PI.toFloat()) * 0.7f
            pose.tilt -= 360f * (r * r * (3f - 2f * r))
            pose.armL = 1f
            pose.armR = 1f
            pose.eyes = if (r in 0.05f..0.95f) EyeShape.CLOSED else EyeShape.STAR
        }
    }
}

@Composable
fun TricksScreen(state: GameState, onBack: () -> Unit) {
    val p = LocalPalette.current
    val pet = state.pet
    val time = rememberFrameTime()
    var showing by remember { mutableStateOf<Trick?>(null) }
    var success by remember { mutableStateOf(true) }
    var shownAt by remember { mutableFloatStateOf(-10f) }
    LaunchedEffect(Unit) {
        GameRepository.events.collect { e ->
            when (e) {
                is GameEvent.TrickPracticed -> {
                    showing = e.trick
                    success = e.success
                    shownAt = time.floatValue
                }
                is GameEvent.TrickShown -> {
                    showing = e.trick
                    success = true
                    shownAt = time.floatValue
                }
                else -> Unit
            }
        }
    }
    Column(Modifier.fillMaxSize()) {
        val voice = rememberTrickVoice()
        PageHeader("Kunststücke", onBack) {
            if (pet != null && !pet.isEgg) {
                VoiceButton(voice)
                Spacer(Modifier.width(8.dp))
            }
            CoinPill(state.coins)
        }
        if (pet == null || pet.isEgg) {
            Text(
                "Erst wenn dein Haustier geschlüpft ist, kann es Kunststücke lernen.",
                style = MaterialTheme.typography.bodyLarge,
                color = p.textMuted,
                modifier = Modifier.padding(24.dp),
            )
            return@Column
        }
        val learned = Tricks.learnedCount(pet)
        val blocker = Tricks.blocker(state)
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                SurfaceCard(Modifier.fillMaxWidth().staggered(0), color = if (p.dark) p.surfaceAlt else Color(0xFFFFF4D8)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        val look = remember(state.pet, state.equipped) { PetLook.of(state)?.copy(mood = Mood.HAPPY, sleeping = false, sick = false) }
                        PetPortrait(look, Modifier.size(120.dp), sizeFactor = 0.78f) { pose, _ ->
                            val tr = showing
                            val el = time.floatValue - shownAt
                            if (tr != null && el in 0f..2.6f) trickPose(pose, tr, el, success)
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text("${pet.name} kann $learned von ${Trick.entries.size} Tricks", style = MaterialTheme.typography.titleMedium, color = p.text)
                            Spacer(Modifier.height(4.dp))
                            Text(
                                blocker ?: "Übe jeden Trick ein paar Mal. Mit guter Laune und viel Energie klappt es öfter! Tippe auf 🎙️ und sag das Kommando (z. B. „Sitz!“), dann macht ${pet.name} den Trick.",
                                style = MaterialTheme.typography.bodySmall,
                                color = if (blocker != null) p.red else p.textMuted,
                            )
                            Spacer(Modifier.height(6.dp))
                            Pill("Chance gerade: ${(Tricks.successChance(pet, System.currentTimeMillis()) * 100).toInt()} %", color = p.gold.copy(alpha = 0.25f))
                        }
                    }
                }
            }
            items(Trick.entries.toList(), key = { it.name }) { t ->
                TrickCard(state, t, blocker)
            }
        }
    }
}

@Composable
private fun TrickCard(state: GameState, t: Trick, blocker: String?) {
    val p = LocalPalette.current
    val pet = state.pet ?: return
    val level = pet.level
    val locked = level < t.minLevel
    val done = Tricks.learned(pet, t)
    SurfaceCard(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            EmojiTile(if (locked) "🔒" else t.emoji, size = 48.dp, color = if (done) p.mint else if (locked) p.track else p.gold)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(t.title, style = MaterialTheme.typography.titleSmall, color = p.text)
                    if (done) {
                        Spacer(Modifier.width(6.dp))
                        Pill("✓ kann es", color = p.mint.copy(alpha = 0.3f))
                    }
                }
                Text(if (locked) "Ab Level ${t.minLevel}" else "${t.description} Sag: „${t.command}“", style = MaterialTheme.typography.bodySmall, color = p.textMuted)
                if (!locked && !done) {
                    Spacer(Modifier.height(6.dp))
                    GlossyBar(Tricks.progress(pet, t), p.gold, p.goldDeep, Modifier.fillMaxWidth(), height = 8.dp)
                    Text("${Tricks.points(pet, t)} / ${t.practice} geübt", style = MaterialTheme.typography.labelSmall, color = p.textMuted)
                }
            }
            Spacer(Modifier.width(8.dp))
            if (!locked) {
                if (done) {
                    ClayTextButton("Zeigen", { GameRepository.perform(Action.ShowTrick(t)) }, small = true, enabled = blocker == null, color = p.mint, deep = p.mintDeep)
                } else {
                    ClayTextButton("Üben", { GameRepository.perform(Action.PracticeTrick(t)) }, small = true, enabled = blocker == null, color = p.gold, deep = p.goldDeep)
                }
            }
        }
    }
}

// ------------------------------------------------------------------ adventure map

private fun regionColors(r: AdventureRegion): Pair<Color, Color> = Color(r.top) to Color(r.bottom)

@Composable
fun AdventureScreen(state: GameState, onBack: () -> Unit) {
    val p = LocalPalette.current
    val a = state.adventure
    val time = rememberFrameTime()
    val renderer = remember { PetRenderer() }
    val pose = remember { PetPose() }
    val paint = remember { Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER } }
    val look = remember(state.pet, state.equipped) { PetLook.of(state)?.copy(mood = Mood.HAPPY, sleeping = false, sick = false) }
    val scroll = rememberScrollState()
    val stations = remember(a.map) { (0 until Adventure.STATIONS).map { Adventure.station(a.map, it) } }
    val cost = Adventure.cost(a.station, a.map)
    val next = stations[a.station.coerceIn(0, Adventure.STATIONS - 1)]
    LaunchedEffect(scroll.maxValue, a.station) {
        if (scroll.maxValue in 1 until Int.MAX_VALUE) scroll.animateScrollTo((scroll.maxValue * (1f - a.station / (Adventure.STATIONS - 1f))).toInt().coerceIn(0, scroll.maxValue))
    }
    Column(Modifier.fillMaxSize()) {
        PageHeader("Abenteuerkarte", onBack) { Pill("🧭 ${a.points}", color = p.gold.copy(alpha = 0.3f)) }
        Box(Modifier.weight(1f).fillMaxWidth()) {
            Column(Modifier.fillMaxSize().verticalScroll(scroll)) {
                Canvas(Modifier.fillMaxWidth().height((Adventure.STATIONS * 86 + 120).dp)) {
                    val t = time.floatValue
                    val w = size.width
                    val h = size.height
                    val step = (h - 140f) / (Adventure.STATIONS - 1)
                    fun pos(i: Int): Offset = Offset(w * (0.5f + 0.3f * sin(i * 0.85f)), h - 70f - i * step)
                    // regions as soft colour bands, from the bottom up
                    for (r in AdventureRegion.entries) {
                        val (top, bottom) = regionColors(r)
                        val y0 = pos(r.ordinal * 10 + 9).y - step * 0.5f
                        val y1 = pos(r.ordinal * 10).y + step * 0.5f
                        drawRect(Brush.verticalGradient(listOf(top, bottom), startY = y0, endY = y1), Offset(0f, y0), androidx.compose.ui.geometry.Size(w, y1 - y0))
                        paint.textSize = 30.sp.toPx()
                        paint.alpha = 120
                        drawIntoCanvas { it.nativeCanvas.drawText("${r.emoji} ${r.title}", w * 0.5f, y0 + 40.sp.toPx(), paint) }
                        paint.alpha = 255
                    }
                    // the path
                    for (i in 0 until Adventure.STATIONS - 1) {
                        val s0 = pos(i)
                        val s1 = pos(i + 1)
                        val doneSeg = i < a.station
                        drawLine(if (doneSeg) Color(0xFFFFC83D) else Color.White.copy(alpha = 0.7f), s0, s1, strokeWidth = 10.dp.toPx(), cap = StrokeCap.Round)
                        if (!doneSeg) drawLine(Color(0x33000000), s0, s1, strokeWidth = 2.dp.toPx(), cap = StrokeCap.Round)
                    }
                    // stations
                    for (st in stations) {
                        val c = pos(st.index)
                        val reached = st.index < a.station
                        val current = st.index == a.station
                        val big = st.index % 10 == 9
                        val r = (if (big) 30.dp else 23.dp).toPx() * (if (current) 1f + 0.06f * sin(t * 4f) else 1f)
                        drawCircle(Color(0x33000000), r, c + Offset(0f, 4f))
                        drawCircle(
                            Brush.radialGradient(
                                if (reached) listOf(Color(0xFFFFF2B8), Color(0xFFFFC83D)) else if (current) listOf(Color.White, Color(0xFFFFB7D5)) else listOf(Color.White, Color(0xFFE6E0F0)),
                                center = c - Offset(r * 0.3f, r * 0.3f),
                                radius = r * 1.6f,
                            ),
                            r,
                            c,
                        )
                        if (current) drawCircle(Color(0xFFFF6FA8), r + 5.dp.toPx(), c, style = Stroke(3.dp.toPx()))
                        paint.textSize = r * 0.95f
                        drawIntoCanvas { it.nativeCanvas.drawText(if (reached) "✅" else st.reward.emoji, c.x, c.y + r * 0.33f, paint) }
                        paint.textSize = 11.sp.toPx()
                        paint.color = android.graphics.Color.argb(200, 58, 42, 69)
                        drawIntoCanvas { it.nativeCanvas.drawText("${st.emoji} ${st.title}", c.x, c.y + r + 15.sp.toPx(), paint) }
                        paint.color = android.graphics.Color.BLACK
                    }
                    // the pet stands at the station it reached last
                    val l = look
                    if (l != null) {
                        val at = pos((a.station - 1).coerceAtLeast(0))
                        val home = if (a.station == 0) Offset(at.x, at.y + step * 0.6f) else at
                        pose.defaults(l, t)
                        pose.eyes = EyeShape.HAPPY
                        pose.armR = 0.6f + 0.3f * sin(t * 5f)
                        val ph = t % 1.4f
                        if (ph < 0.4f) pose.lift = sin(ph / 0.4f * PI.toFloat()) * 0.2f
                        // next to the station, on the side towards the middle of the screen
                        val px = if (home.x < w / 2f) home.x + 56.dp.toPx() else home.x - 56.dp.toPx()
                        drawIntoCanvas {
                            renderer.drawShadow(it.nativeCanvas, px, home.y + 8.dp.toPx(), 60.dp.toPx(), pose, l)
                            renderer.draw(it.nativeCanvas, px, home.y + 8.dp.toPx(), 60.dp.toPx(), l, pose)
                        }
                    }
                }
            }
        }
        SurfaceCard(Modifier.fillMaxWidth().navigationBarsPadding().padding(start = 16.dp, end = 16.dp, bottom = 10.dp, top = 6.dp), contentPadding = PaddingValues(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                EmojiTile(next.reward.emoji, size = 48.dp, color = p.gold)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text("Nächste Station: ${next.emoji} ${next.title}", style = MaterialTheme.typography.titleSmall, color = p.text, maxLines = 1)
                    Text(next.reward.label, style = MaterialTheme.typography.bodySmall, color = p.textMuted, maxLines = 2)
                    Spacer(Modifier.height(6.dp))
                    GlossyBar((a.points / cost.toFloat()).coerceIn(0f, 1f), p.gold, p.goldDeep, Modifier.fillMaxWidth(), height = 10.dp)
                    Text("${a.points.coerceAtMost(cost)} / $cost 🧭 · Karte ${a.map + 1}", style = MaterialTheme.typography.labelSmall, color = p.textMuted)
                }
            }
            Spacer(Modifier.height(10.dp))
            ClayTextButton(
                if (a.points >= cost) "Weiterziehen!" else "Noch ${cost - a.points} 🧭 sammeln",
                { GameRepository.perform(Action.AdventureStep) },
                emoji = "🗺️",
                enabled = a.points >= cost,
                modifier = Modifier.fillMaxWidth(),
                color = p.pink,
                deep = p.pinkDeep,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                "Abenteuerpunkte 🧭 gibt es für Aufgaben, Wünsche, Monatsziele, Turniere, Kunststücke, Zimmerspiele und die tägliche Belohnung.",
                style = MaterialTheme.typography.bodySmall,
                color = p.textMuted,
            )
        }
    }
}
