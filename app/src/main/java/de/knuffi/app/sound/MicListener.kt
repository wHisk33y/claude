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
 * Listens to the microphone and recognises blowing, clapping, singing and shouting from the
 * loudness alone. Nothing is recorded or stored: every 50 ms block is measured and thrown away.
 */
class MicListener(private val onVoice: (VoiceKind) -> Unit) {
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
        var zcrSum = 0.0
        var ratioSum = 0.0
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

                // Something long: blowing (noisy), singing (tone) or shouting (very loud).
                if (ratio > 4.0) {
                    loudFrames++
                    zcrSum += zcr
                    ratioSum += ratio
                    if (loudFrames >= 8) {
                        val avgZcr = zcrSum / loudFrames
                        val avgRatio = ratioSum / loudFrames
                        if (ready && onsetAt < 0) {
                            lastEvent = now
                            emit(
                                when {
                                    avgZcr > 0.13 -> VoiceKind.BLOW
                                    avgRatio > 30.0 -> VoiceKind.LOUD
                                    else -> VoiceKind.SING
                                },
                            )
                        }
                        loudFrames = 0
                        zcrSum = 0.0
                        ratioSum = 0.0
                    }
                } else {
                    loudFrames = 0
                    zcrSum = 0.0
                    ratioSum = 0.0
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
        fun allowed(context: Context): Boolean =
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
    }
}
