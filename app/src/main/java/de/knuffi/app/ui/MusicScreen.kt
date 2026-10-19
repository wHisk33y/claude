package de.knuffi.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.knuffi.app.data.GameRepository
import de.knuffi.app.render.EyeShape
import de.knuffi.app.render.MouthShape
import de.knuffi.app.render.PetLook
import de.knuffi.app.sound.ToneSynth
import de.knuffi.app.ui.components.ClayTextButton
import de.knuffi.app.ui.components.PageHeader
import de.knuffi.app.ui.components.Pill
import de.knuffi.app.ui.components.SectionHeader
import de.knuffi.app.ui.components.SegmentedControl
import de.knuffi.app.ui.components.SurfaceCard
import de.knuffi.app.ui.theme.LocalPalette
import de.knuffi.core.Action
import de.knuffi.core.GameState
import de.knuffi.core.Instrument
import de.knuffi.core.Mood
import de.knuffi.core.Music
import de.knuffi.core.Song
import de.knuffi.core.SongNote
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.sin

private val KEY_COLORS = listOf(
    Color(0xFFFF6B7A), Color(0xFFFFA24D), Color(0xFFFFD84D), Color(0xFF7DD98A),
    Color(0xFF4DC8E8), Color(0xFF6E8CFF), Color(0xFFB27CFF), Color(0xFFFF7EB6),
)

/** The music room: play the piano, the xylophone or the drums, record songs and listen to them. */
@Composable
fun MusicScreen(state: GameState, onBack: () -> Unit) {
    val p = LocalPalette.current
    val synth = remember { ToneSynth() }
    val scope = rememberCoroutineScope()
    val time = rememberFrameTime()
    var instrument by remember { mutableStateOf(Instrument.PIANO) }
    var played by remember { mutableIntStateOf(0) }
    var recording by remember { mutableStateOf(false) }
    var recordStart by remember { mutableLongStateOf(0L) }
    val recorded = remember { mutableStateListOf<SongNote>() }
    var playJob by remember { mutableStateOf<Job?>(null) }
    var playingName by remember { mutableStateOf<String?>(null) }
    var noteAt by remember { mutableFloatStateOf(-10f) }
    var lastKey by remember { mutableIntStateOf(0) }
    var pressed by remember { mutableStateOf<Pair<Instrument, Int>?>(null) }
    var askName by remember { mutableStateOf(false) }
    val look = remember(state.pet, state.equipped) { PetLook.of(state)?.copy(mood = Mood.HAPPY, sleeping = false, sick = false) }

    DisposableEffect(Unit) {
        onDispose {
            synth.release()
            // Making music makes happy (rewarded once in a while).
            if (played > 0) GameRepository.perform(Action.MusicPlayed(played))
        }
    }

    fun hit(inst: Instrument, key: Int, fromSong: Boolean = false) {
        synth.play(inst, key)
        played++
        noteAt = time.floatValue
        lastKey = key
        if (recording && !fromSong) {
            val at = (System.currentTimeMillis() - recordStart).toInt()
            if (at > Music.MAX_LENGTH_MS || recorded.size >= Music.MAX_NOTES) {
                recording = false
                GameRepository.message("Die Aufnahme ist voll. Speichere dein Lied! 💾")
            } else {
                recorded += SongNote(inst, key, at)
            }
        }
    }

    fun play(name: String, notes: List<SongNote>) {
        playJob?.cancel()
        playingName = name
        playJob = scope.launch {
            val start = System.currentTimeMillis()
            for (n in notes.sortedBy { it.at }) {
                val wait = n.at - (System.currentTimeMillis() - start)
                if (wait > 0) delay(wait)
                pressed = n.instrument to n.key
                hit(n.instrument, n.key, fromSong = true)
                delay(60)
                pressed = null
            }
            delay(400)
            playingName = null
        }
    }

    Column(Modifier.fillMaxSize()) {
        PageHeader("Musikzimmer", onBack) { Pill("🎵 $played", color = p.violet.copy(alpha = 0.2f)) }
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SurfaceCard(Modifier.fillMaxWidth(), color = if (p.dark) p.surfaceAlt else Color(0xFFEDE6FF)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    PetPortrait(look, Modifier.size(110.dp), sizeFactor = 0.78f) { pose, t ->
                        val since = time.floatValue - noteAt
                        if (since < 0.6f) {
                            val k = 1f - since / 0.6f
                            pose.lift += abs(sin(since * 10f)) * 0.2f * k
                            pose.tilt += (if (lastKey % 2 == 0) 8f else -8f) * k
                            pose.armL = 0.7f + 0.3f * k
                            pose.armR = 0.7f + 0.3f * k
                            pose.mouth = MouthShape.O
                            pose.mouthOpen = 0.6f * k
                            pose.eyes = EyeShape.HAPPY
                        } else {
                            pose.tilt += sin(t * 2f) * 3f
                        }
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            when {
                                playingName != null -> "♪ ${playingName} …"
                                recording -> "⏺ Aufnahme läuft: ${recorded.size} Töne"
                                else -> "${state.pet?.name ?: "Knuffi"} tanzt zu deiner Musik!"
                            },
                            style = MaterialTheme.typography.titleMedium,
                            color = if (recording) p.red else p.text,
                        )
                        Text(
                            "Spiel ein paar Töne, nimm ein Lied auf und hör es dir später wieder an. Musik macht fröhlich und stärkt das Musik-Talent.",
                            style = MaterialTheme.typography.bodySmall,
                            color = p.textMuted,
                        )
                    }
                }
            }
            SegmentedControl(Instrument.entries.map { "${it.emoji} ${it.title}" }, instrument.ordinal, { instrument = Instrument.entries[it] })
            Box(Modifier.fillMaxWidth().height(210.dp)) {
                when (instrument) {
                    Instrument.PIANO -> Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                        for (k in 0 until Instrument.PIANO.keys) {
                            val down = pressed == (Instrument.PIANO to k)
                            Box(
                                Modifier
                                    .weight(1f)
                                    .fillMaxHeight()
                                    .graphicsLayer { translationY = if (down) 4.dp.toPx() else 0f }
                                    .shadow(if (down) 1.dp else 4.dp, RoundedCornerShape(bottomStart = 10.dp, bottomEnd = 10.dp))
                                    .clip(RoundedCornerShape(bottomStart = 10.dp, bottomEnd = 10.dp))
                                    .background(Brush.verticalGradient(listOf(if (down) Color(0xFFE8E0F0) else Color.White, Color(0xFFF0EAF4))))
                                    .border(1.dp, Color(0x22000000), RoundedCornerShape(bottomStart = 10.dp, bottomEnd = 10.dp))
                                    .pointerInput(k) {
                                        detectTapGestures(onPress = {
                                            pressed = Instrument.PIANO to k
                                            hit(Instrument.PIANO, k)
                                            tryAwaitRelease()
                                            pressed = null
                                        })
                                    },
                                contentAlignment = Alignment.BottomCenter,
                            ) {
                                Text(Music.NOTE_NAMES[k], style = MaterialTheme.typography.titleSmall, color = KEY_COLORS[k], modifier = Modifier.padding(bottom = 10.dp))
                            }
                        }
                    }
                    Instrument.XYLOPHONE -> Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(5.dp), verticalAlignment = Alignment.CenterVertically) {
                        for (k in 0 until Instrument.XYLOPHONE.keys) {
                            val down = pressed == (Instrument.XYLOPHONE to k)
                            Box(
                                Modifier
                                    .weight(1f)
                                    .fillMaxHeight(1f - k * 0.07f)
                                    .graphicsLayer {
                                        scaleX = if (down) 0.92f else 1f
                                        scaleY = if (down) 0.96f else 1f
                                    }
                                    .shadow(4.dp, RoundedCornerShape(10.dp))
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(Brush.verticalGradient(listOf(KEY_COLORS[k].copy(alpha = 0.75f), KEY_COLORS[k])))
                                    .pointerInput(k) {
                                        detectTapGestures(onPress = {
                                            pressed = Instrument.XYLOPHONE to k
                                            hit(Instrument.XYLOPHONE, k)
                                            tryAwaitRelease()
                                            pressed = null
                                        })
                                    },
                                contentAlignment = Alignment.Center,
                            ) {
                                Box(Modifier.size(8.dp).clip(RoundedCornerShape(50)).background(Color.White.copy(alpha = 0.8f)))
                            }
                        }
                    }
                    Instrument.DRUMS -> Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        for (row in 0 until 2) {
                            Row(Modifier.fillMaxWidth().weight(1f), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                for (col in 0 until 2) {
                                    val k = row * 2 + col
                                    val down = pressed == (Instrument.DRUMS to k)
                                    Box(
                                        Modifier
                                            .weight(1f)
                                            .fillMaxHeight()
                                            .graphicsLayer {
                                                scaleX = if (down) 0.94f else 1f
                                                scaleY = if (down) 0.94f else 1f
                                            }
                                            .shadow(6.dp, RoundedCornerShape(24.dp))
                                            .clip(RoundedCornerShape(24.dp))
                                            .background(Brush.radialGradient(listOf(Color(0xFFFFF4E8), KEY_COLORS[k * 2])))
                                            .pointerInput(k) {
                                                detectTapGestures(onPress = {
                                                    pressed = Instrument.DRUMS to k
                                                    hit(Instrument.DRUMS, k)
                                                    tryAwaitRelease()
                                                    pressed = null
                                                })
                                            },
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        Text(
                                            "${listOf("🥁", "🪘", "✨", "🔔")[k]}\n${Music.DRUM_NAMES[k]}",
                                            textAlign = TextAlign.Center,
                                            fontSize = 18.sp,
                                            color = Color(0xFF3A2A45),
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ClayTextButton(
                    if (recording) "Stopp" else "Aufnehmen",
                    {
                        if (recording) {
                            recording = false
                        } else {
                            recorded.clear()
                            recordStart = System.currentTimeMillis()
                            recording = true
                        }
                    },
                    Modifier.weight(1f),
                    emoji = if (recording) "⏹" else "⏺",
                    small = true,
                    color = p.red,
                    deep = p.redDeep,
                )
                ClayTextButton(
                    "Anhören",
                    {
                        recording = false
                        play("Deine Aufnahme", recorded.toList())
                    },
                    Modifier.weight(1f),
                    emoji = "▶",
                    small = true,
                    enabled = recorded.isNotEmpty(),
                    color = p.mint,
                    deep = p.mintDeep,
                )
                ClayTextButton(
                    "Speichern",
                    {
                        recording = false
                        askName = true
                    },
                    Modifier.weight(1f),
                    emoji = "💾",
                    small = true,
                    enabled = recorded.size >= 3,
                    color = p.violet,
                    deep = p.violetDeep,
                )
            }
            SectionHeader("Deine Lieder", "🎶") { Pill("${state.songs.size} / ${Music.MAX_SONGS}") }
            if (state.songs.isEmpty()) {
                Text("Noch keine Lieder. Nimm eins auf und speichere es!", style = MaterialTheme.typography.bodyMedium, color = p.textMuted)
            }
            for ((i, song) in state.songs.withIndex().reversed()) {
                SongCard(song, playingName == song.name, onPlay = { play(song.name, song.notes) }, onDelete = { GameRepository.perform(Action.DeleteSong(i)) })
            }
            Spacer(Modifier.height(16.dp))
        }
    }

    if (askName) {
        NameDialog(
            title = "Lied speichern",
            text = "Wie soll dein Lied heißen?",
            confirm = "Speichern",
            onConfirm = { name ->
                GameRepository.perform(Action.SaveSong(Song(name, recorded.toList())))
                askName = false
            },
            onDismiss = { askName = false },
        )
    }
}

@Composable
private fun SongCard(song: Song, playing: Boolean, onPlay: () -> Unit, onDelete: () -> Unit) {
    val p = LocalPalette.current
    SurfaceCard(Modifier.fillMaxWidth(), contentPadding = androidx.compose.foundation.layout.PaddingValues(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(if (playing) "🔊" else song.notes.firstOrNull()?.instrument?.emoji ?: "🎵", fontSize = 26.sp)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(song.name, style = MaterialTheme.typography.titleSmall, color = p.text)
                Text("${song.notes.size} Töne · ${(song.lengthMs / 1000f).let { "%.1f".format(it) }} s", style = MaterialTheme.typography.labelMedium, color = p.textMuted)
            }
            ClayTextButton("▶", onPlay, small = true, color = p.mint, deep = p.mintDeep)
            Spacer(Modifier.width(6.dp))
            ClayTextButton("🗑", onDelete, small = true, color = Color(0xFF9A8FB0), deep = Color(0xFF6E6484))
        }
    }
}
