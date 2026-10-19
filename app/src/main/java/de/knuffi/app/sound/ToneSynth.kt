package de.knuffi.app.sound

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import de.knuffi.core.Instrument
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

/** Piano, xylophone and drums for the music room, made in code (no sound files). */
class ToneSynth {
    @Volatile private var tracks: Map<Instrument, List<AudioTrack?>> = emptyMap()
    @Volatile private var released = false

    init {
        // The sounds are computed in the background so the screen opens without a stutter.
        Thread {
            val made = Instrument.entries.associateWith { inst ->
                (0 until inst.keys).map { k -> runCatching { build(samples(inst, k)) }.getOrNull() }
            }
            synchronized(this) {
                if (released) made.values.forEach { list -> list.forEach { runCatching { it?.release() } } } else tracks = made
            }
        }.apply { isDaemon = true }.start()
    }

    fun play(instrument: Instrument, key: Int) {
        val t = tracks[instrument]?.getOrNull(key) ?: return
        runCatching {
            if (t.playState == AudioTrack.PLAYSTATE_PLAYING) t.stop()
            t.reloadStaticData()
            t.play()
        }
    }

    fun release() {
        synchronized(this) {
            released = true
            for (list in tracks.values) for (t in list) runCatching { t?.release() }
            tracks = emptyMap()
        }
    }

    companion object {
        private const val RATE = 22050
        private val PIANO = floatArrayOf(261.63f, 293.66f, 329.63f, 349.23f, 392.00f, 440.00f, 493.88f, 523.25f)

        private fun samples(inst: Instrument, key: Int): ShortArray = when (inst) {
            Instrument.PIANO -> tone(PIANO[key.coerceIn(0, 7)], 0.8f) { t, f ->
                val env = min(1f, t * 400f) * exp(-t * 3.2f)
                (sin(2 * PI * f * t) * 0.6 + sin(4 * PI * f * t) * 0.25 + sin(6 * PI * f * t) * 0.1 + sin(8 * PI * f * t) * 0.05).toFloat() * env
            }
            Instrument.XYLOPHONE -> tone(PIANO[key.coerceIn(0, 7)] * 2f, 0.5f) { t, f ->
                val env = min(1f, t * 800f) * exp(-t * 9f)
                (sin(2 * PI * f * t) * 0.75 + sin(2 * PI * f * 3.93 * t) * 0.2 * exp(-t * 30.0)).toFloat() * env
            }
            Instrument.DRUMS -> drum(key)
        }

        private inline fun tone(freq: Float, seconds: Float, wave: (Float, Float) -> Float): ShortArray {
            val n = (RATE * seconds).toInt()
            return ShortArray(n) { i ->
                val t = i / RATE.toFloat()
                (wave(t, freq) * 12000f).toInt().coerceIn(-32000, 32000).toShort()
            }
        }

        private fun drum(key: Int): ShortArray {
            val rnd = Random(key + 3)
            return when (key) {
                // bass drum: a falling low tone
                0 -> tone(120f, 0.45f) { t, _ ->
                    val f = 50f + 70f * exp(-t * 18f)
                    sin(2 * PI * f * t).toFloat() * exp(-t * 7f)
                }
                // snare: tone plus noise
                1 -> tone(180f, 0.3f) { t, f -> (sin(2 * PI * f * t).toFloat() * 0.4f + (rnd.nextFloat() * 2f - 1f) * 0.6f) * exp(-t * 14f) }
                // hi-hat: short bright noise
                2 -> tone(0f, 0.12f) { t, _ -> (rnd.nextFloat() * 2f - 1f) * 0.5f * exp(-t * 40f) }
                // tom
                else -> tone(160f, 0.4f) { t, _ ->
                    val f = 100f + 70f * exp(-t * 10f)
                    sin(2 * PI * f * t).toFloat() * exp(-t * 6f)
                }
            }
        }

        private fun build(data: ShortArray): AudioTrack {
            val track = AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_GAME)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build(),
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(RATE)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build(),
                )
                .setBufferSizeInBytes(data.size * 2)
                .setTransferMode(AudioTrack.MODE_STATIC)
                .build()
            track.write(data, 0, data.size)
            return track
        }
    }
}
