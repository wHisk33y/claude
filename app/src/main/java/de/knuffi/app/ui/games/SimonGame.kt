package de.knuffi.app.ui.games

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.knuffi.app.render.EyeShape
import de.knuffi.app.render.MouthShape
import de.knuffi.app.render.PKind
import de.knuffi.app.render.Particle
import de.knuffi.app.render.ParticleSystem
import de.knuffi.app.ui.PetPortrait
import de.knuffi.app.ui.components.lighter
import de.knuffi.app.ui.components.rememberHaptic
import de.knuffi.app.ui.rememberCanvasTypeface
import de.knuffi.app.ui.rememberFrameTime
import de.knuffi.core.GameState
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.random.Random

private class Pad(val color: Color, val symbol: String)

private val PADS = listOf(
    Pad(Color(0xFFFF5A7A), "🥁"),
    Pad(Color(0xFF4CA8FF), "🎺"),
    Pad(Color(0xFF4CCB7A), "🎹"),
    Pad(Color(0xFFFFC83D), "🎸"),
)

private enum class SimonPhase { WAIT, LISTEN, PLAY, RIGHT, WRONG, OVER }

/** Four soft notes (C, E, G, C) generated once and replayed. */
private class Synth {
    private val tracks: List<AudioTrack?> = FREQS.map { f -> runCatching { build(f) }.getOrNull() }

    fun play(i: Int) {
        val t = tracks.getOrNull(i) ?: return
        runCatching {
            if (t.playState == AudioTrack.PLAYSTATE_PLAYING) t.stop()
            t.reloadStaticData()
            t.play()
        }
    }

    fun release() {
        for (t in tracks) runCatching { t?.release() }
    }

    companion object {
        private val FREQS = listOf(523.25f, 659.25f, 783.99f, 1046.5f)
        private const val RATE = 22050

        private fun build(freq: Float): AudioTrack {
            val n = (RATE * 0.35f).toInt()
            val data = ShortArray(n) { i ->
                val t = i / RATE.toFloat()
                val env = min(1f, i / 250f) * (1f - i / n.toFloat()).pow(1.6f)
                val v = sin(2 * PI * freq * t) * 0.7 + sin(4 * PI * freq * t) * 0.18 + sin(6 * PI * freq * t) * 0.06
                (v * env * 11000).toInt().toShort()
            }
            val track = AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_GAME)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build(),
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(RATE)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build(),
                )
                .setBufferSizeInBytes(n * 2)
                .setTransferMode(AudioTrack.MODE_STATIC)
                .build()
            track.write(data, 0, n)
            return track
        }
    }
}

@Composable
fun SimonGame(state: GameState, onFinished: (Int) -> Unit, onBack: () -> Unit) {
    val look = remember { gameLook(state) }
    val synth = remember { Synth() }
    DisposableEffect(Unit) { onDispose { synth.release() } }
    val haptic = rememberHaptic()
    val scope = rememberCoroutineScope()
    val ps = remember { ParticleSystem() }
    val tf = rememberCanvasTypeface()
    val time = rememberFrameTime()
    val shake = remember { Shake() }
    val rnd = remember { Random(System.nanoTime()) }
    val seq = remember { mutableStateListOf<Int>() }
    var phase by remember { mutableStateOf(SimonPhase.WAIT) }
    var lit by remember { mutableIntStateOf(-1) }
    var inputIndex by remember { mutableIntStateOf(0) }
    var score by remember { mutableIntStateOf(0) }
    var lives by remember { mutableIntStateOf(2) }
    var round by remember { mutableIntStateOf(0) }
    var started by remember { mutableStateOf(false) }

    LaunchedEffect(tf) { ps.typeface = tf }

    fun note(i: Int) {
        synth.play(i)
        val x = if (i % 2 == 0) 0.3f else 0.7f
        ps.add(Particle(PKind.TEXT, x, 0.42f, (rnd.nextFloat() - 0.5f) * 0.05f, -0.12f, 1.1f, 0.05f, PADS[i].color.lighter(0.5f).toArgb(), text = if (rnd.nextBoolean()) "♪" else "♫"))
    }

    // Plays the melody, then hands over to the player.
    LaunchedEffect(round) {
        if (round == 0) return@LaunchedEffect
        phase = SimonPhase.LISTEN
        delay(700)
        val on = max(230L, 560L - seq.size * 28L)
        for (i in seq) {
            lit = i
            note(i)
            delay(on)
            lit = -1
            delay(on / 2)
        }
        inputIndex = 0
        phase = SimonPhase.PLAY
    }

    fun press(i: Int) {
        if (phase != SimonPhase.PLAY) return
        haptic()
        note(i)
        lit = i
        scope.launch {
            delay(180)
            if (lit == i && phase != SimonPhase.LISTEN) lit = -1
        }
        if (seq[inputIndex] == i) {
            inputIndex++
            if (inputIndex == seq.size) {
                score = seq.size
                phase = SimonPhase.RIGHT
                ps.burst(PKind.STAR, 0.5f, 0.3f, 12, 0.45f, 1f, 0.03f, ParticleSystem.STAR_COLORS, gravity = 0.3f)
                scope.launch {
                    delay(800)
                    seq.add(rnd.nextInt(PADS.size))
                    round++
                }
            }
        } else {
            lives--
            shake.kick(time.floatValue, 20f, 0.35f)
            phase = if (lives <= 0) SimonPhase.OVER else SimonPhase.WRONG
            scope.launch {
                if (phase == SimonPhase.OVER) {
                    delay(900)
                    onFinished(score)
                } else {
                    delay(1100)
                    round++
                }
            }
        }
    }

    Box(Modifier.fillMaxSize()) {
        // stage with spotlights
        Canvas(Modifier.fillMaxSize()) {
            val t = time.floatValue
            ps.update(t)
            drawRect(Brush.verticalGradient(listOf(Color(0xFF2A1A5E), Color(0xFF6A3AA8), Color(0xFFE88AB0))))
            for (i in 0 until 3) {
                val x = size.width * (0.2f + i * 0.3f) + sin(t * 0.8f + i * 2f) * size.width * 0.08f
                drawCircle(
                    Brush.radialGradient(listOf(Color.White.copy(alpha = 0.16f), Color.Transparent), center = Offset(x, size.height * 0.35f), radius = size.width * 0.35f),
                    radius = size.width * 0.35f,
                    center = Offset(x, size.height * 0.35f),
                )
            }
            val off = shake.offset(t)
            drawIntoCanvas {
                it.nativeCanvas.save()
                it.nativeCanvas.translate(off.x, off.y)
                ps.draw(it.nativeCanvas, size.width, size.height)
                it.nativeCanvas.restore()
            }
        }
        Column(
            Modifier
                .fillMaxSize()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(96.dp))
            val singing = lit >= 0
            PetPortrait(look, Modifier.size(170.dp), sizeFactor = 0.8f) { pose, t ->
                when {
                    phase == SimonPhase.WRONG || phase == SimonPhase.OVER -> {
                        pose.eyes = EyeShape.DIZZY
                        pose.mouth = MouthShape.WAVY
                    }
                    phase == SimonPhase.RIGHT -> {
                        pose.eyes = EyeShape.STAR
                        pose.mouth = MouthShape.GRIN
                        pose.armL = 1f
                        pose.armR = 1f
                        val ph = t % 0.6f
                        if (ph < 0.3f) pose.lift = sin(ph / 0.3f * PI.toFloat()) * 0.2f
                    }
                    singing -> {
                        pose.mouth = MouthShape.OPEN
                        pose.eyes = EyeShape.HAPPY
                        pose.armL = 0.9f
                        pose.armR = 0.9f
                        pose.tilt = if (lit % 2 == 0) -8f else 8f
                    }
                    phase == SimonPhase.PLAY -> {
                        pose.eyes = EyeShape.OPEN
                        pose.mouth = MouthShape.SMILE
                        pose.lookY = 1f
                    }
                    else -> {
                        pose.eyes = EyeShape.HAPPY
                        pose.tilt = sin(t * 3f) * 5f
                    }
                }
            }
            Text(
                when (phase) {
                    SimonPhase.WAIT -> ""
                    SimonPhase.LISTEN -> "Hör gut zu … 👂"
                    SimonPhase.PLAY -> "Du bist dran! ${inputIndex} / ${seq.size}"
                    SimonPhase.RIGHT -> "Richtig! 🎉"
                    SimonPhase.WRONG -> "Hoppla! Nochmal anhören …"
                    SimonPhase.OVER -> "Vorbei!"
                },
                style = MaterialTheme.typography.titleLarge,
                color = Color.White,
                modifier = Modifier.padding(vertical = 8.dp),
            )
            Spacer(Modifier.weight(1f))
            for (row in 0..1) {
                Row(horizontalArrangement = Arrangement.spacedBy(14.dp), modifier = Modifier.fillMaxWidth()) {
                    for (col in 0..1) {
                        val i = row * 2 + col
                        SimonPad(PADS[i], lit == i, phase == SimonPhase.PLAY, Modifier.weight(1f)) { press(i) }
                    }
                }
                Spacer(Modifier.height(14.dp))
            }
            Spacer(Modifier.height(10.dp))
        }
        Box(Modifier.align(Alignment.TopCenter)) {
            GameHud(onBack = onBack, score = "$score", lives = lives, maxLives = 2)
        }
        if (!started) {
            CountdownOverlay("Merke dir die Melodie und spiel sie nach!") {
                started = true
                seq.add(rnd.nextInt(PADS.size))
                round = 1
            }
        }
    }
}

@Composable
private fun SimonPad(pad: Pad, lit: Boolean, enabled: Boolean, modifier: Modifier = Modifier, onPress: () -> Unit) {
    val glow by animateFloatAsState(if (lit) 1f else 0f, spring(dampingRatio = 0.6f, stiffness = 900f), label = "pad")
    val press by rememberUpdatedState(onPress)
    val shape = RoundedCornerShape(30.dp)
    Box(
        modifier
            .aspectRatio(1.25f)
            .graphicsLayer {
                val s = 1f + 0.06f * glow
                scaleX = s
                scaleY = s
            }
            .shadow((6 + 14 * glow).dp, shape, ambientColor = pad.color, spotColor = pad.color)
            .clip(shape)
            .background(
                Brush.verticalGradient(
                    listOf(
                        pad.color.lighter(0.25f + 0.45f * glow).copy(alpha = if (enabled || lit) 1f else 0.75f),
                        pad.color.copy(alpha = if (enabled || lit) 1f else 0.7f),
                    ),
                ),
            )
            .border(2.dp, Color.White.copy(alpha = 0.35f + 0.6f * glow), shape)
            .pointerInput(Unit) { detectTapGestures(onPress = { press() }) },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .align(Alignment.TopCenter)
                .padding(top = 8.dp)
                .fillMaxWidth(0.6f)
                .height(10.dp)
                .clip(RoundedCornerShape(50))
                .background(Color.White.copy(alpha = 0.3f)),
        )
        Text(pad.symbol, fontSize = (40 + 8 * glow).sp)
    }
}
