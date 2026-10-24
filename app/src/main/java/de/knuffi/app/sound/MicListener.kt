package de.knuffi.app.sound

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import androidx.core.content.ContextCompat
import de.knuffi.core.VoiceKind
import kotlin.math.max
import kotlin.math.sqrt

/**
 * Listens to the microphone and recognises blowing, clapping, singing, shouting and talking from
 * the loudness alone. Nothing is recorded or stored: every 50 ms block is measured and thrown away.
 * Talking (a short sound with a tone, then a pause) is passed to [onSpeech], so the pet can
 * prick up its ears and listen for a command.
 */
class MicListener(private val onSpeech: () -> Unit = {}, private val onVoice: (VoiceKind) -> Unit) {
    @Volatile
    private var running = false
    private var thread: Thread? = null
    private val main = Handler(Looper.getMainLooper())

    /** Starts listening. Returns false without the microphone permission. */
    fun start(context: Context): Boolean {
        if (running) return true
        if (!allowed(context)) return false
        running = true
        thread = Thread({ loop() }, "knuffi-mic").apply {
            isDaemon = true
            start()
        }
        return true
    }

    fun stop() {
        running = false
        thread = null
    }

    private fun emit(kind: VoiceKind) {
        main.post { onVoice(kind) }
    }

    private fun speech() {
        main.post { onSpeech() }
    }

    // The permission is checked in start().
    @SuppressLint("MissingPermission")
    private fun loop() {
        val rate = 16_000
        val min = AudioRecord.getMinBufferSize(rate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
        if (min <= 0) {
            running = false
            return
        }
        val rec = try {
            AudioRecord(MediaRecorder.AudioSource.MIC, rate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, max(min, rate / 2))
        } catch (e: SecurityException) {
            null
        } catch (e: IllegalArgumentException) {
            null
        }
        if (rec == null || rec.state != AudioRecord.STATE_INITIALIZED) {
            rec?.release()
            running = false
            return
        }
        val frame = ShortArray(rate / 20)
        var noise = 250.0
        var prevRatio = 1.0
        var onsetAt = -1L
        var onsetPeak = 0.0
        var loudFrames = 0
        var quietFrames = 0
        var zcrSum = 0.0
        var rmsMax = 0.0
        var handled = false
        var lastEvent = 0L
        try {
            rec.startRecording()
            while (running) {
                val n = rec.read(frame, 0, frame.size)
                // an error code means the microphone is gone: stop instead of spinning
                if (n < 0) break
                if (n == 0) continue
                var sum = 0.0
                var crossings = 0
                var prev = 0
                for (i in 0 until n) {
                    val v = frame[i].toInt()
                    sum += v.toDouble() * v
                    if ((v >= 0) != (prev >= 0)) crossings++
                    prev = v
                }
                val rms = sqrt(sum / n)
                val zcr = crossings.toDouble() / n
                // The background noise is followed slowly, so the room's own hum does not count.
                if (rms < noise * 2.5) noise = noise * 0.97 + rms * 0.03
                val ratio = rms / max(noise, 60.0)
                val now = SystemClock.elapsedRealtime()
                val ready = now - lastEvent > 1_500L

                // A clap: sudden and very short.
                if (ratio > 9.0 && prevRatio < 3.0) {
                    onsetAt = now
                    onsetPeak = rms
                }
                if (onsetAt > 0 && now - onsetAt in 60L..220L && rms < onsetPeak * 0.3) {
                    onsetAt = -1L
                    if (ready) {
                        lastEvent = now
                        emit(VoiceKind.CLAP)
                    }
                    loudFrames = 0
                }
                if (onsetAt > 0 && now - onsetAt > 220L) onsetAt = -1L

                // A sound that lasts: blowing is noisy, singing a long tone, talking short bits of tone
                // with pauses. Only a really loud voice counts as shouting.
                if (ratio > 4.0) {
                    loudFrames++
                    quietFrames = 0
                    zcrSum += zcr
                    rmsMax = max(rmsMax, rms)
                    val avgZcr = zcrSum / loudFrames
                    if (!handled && ready && onsetAt < 0) {
                        if (loudFrames == 8 && avgZcr > 0.13) {
                            handled = true
                            lastEvent = now
                            emit(VoiceKind.BLOW)
                        } else if (loudFrames == 24) {
                            handled = true
                            lastEvent = now
                            emit(if (rmsMax > SHOUT_RMS) VoiceKind.LOUD else VoiceKind.SING)
                        }
                    }
                } else if (loudFrames > 0) {
                    quietFrames++
                    if (quietFrames >= 3) {
                        // the sound is over: a few words?
                        if (!handled && ready && onsetAt < 0 && loudFrames >= 3) {
                            lastEvent = now
                            if (rmsMax > SHOUT_RMS) emit(VoiceKind.LOUD) else speech()
                        }
                        loudFrames = 0
                        quietFrames = 0
                        zcrSum = 0.0
                        rmsMax = 0.0
                        handled = false
                    }
                }
                prevRatio = ratio
            }
        } catch (e: IllegalStateException) {
            // the microphone went away
        } finally {
            try {
                rec.stop()
            } catch (e: IllegalStateException) {
                // never started
            }
            rec.release()
            running = false
        }
    }

    companion object {
        /** A voice this loud (16 bit samples) is shouting, talking is far below. */
        private const val SHOUT_RMS = 14_000.0

        fun allowed(context: Context): Boolean =
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
    }
}
