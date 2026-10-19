package de.knuffi.app.sound

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import android.os.SystemClock
import de.knuffi.app.data.GameRepository
import de.knuffi.core.GameEvent
import de.knuffi.core.GameState
import de.knuffi.core.ReactionKind
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.random.Random

/** All sound effects. They are synthesised once, saved as small WAV files and played with a SoundPool. */
enum class Sfx(val gain: Float = 1f) {
    POP, COIN(0.8f), SUCCESS, BONK(0.7f), WHOOSH(0.7f), SPLASH(0.8f), MUNCH, PURR(0.9f), MEOW, CHIRP(0.8f),
    LEVELUP, SPARKLE(0.6f), CLICK(0.35f), BOING(0.8f), FANFARE,
}

object Sounds {
    private const val RATE = 22050
    private const val VERSION = 2
    private var pool: SoundPool? = null
    private val ids = IntArray(Sfx.entries.size)
    private val loaded = BooleanArray(Sfx.entries.size)
    private val lastPlay = LongArray(Sfx.entries.size)

    @Volatile
    private var ready = false

    /** Creates the sounds in the background. Safe to call more than once. */
    fun init(context: Context) {
        if (pool != null) return
        val p = SoundPool.Builder()
            .setMaxStreams(6)
            .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_GAME).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build())
            .build()
        pool = p
        p.setOnLoadCompleteListener { _, sampleId, status ->
            val i = ids.indexOf(sampleId)
            if (i >= 0 && status == 0) loaded[i] = true
        }
        val dir = File(context.cacheDir, "sfx$VERSION").apply { mkdirs() }
        Thread {
            for (s in Sfx.entries) {
                val f = File(dir, "${s.name.lowercase()}.wav")
                if (!f.exists() || f.length() < 100) runCatching { writeWav(f, synth(s)) }
                runCatching { ids[s.ordinal] = p.load(f.path, 1) }
            }
            ready = true
        }.apply { isDaemon = true }.start()
    }

    private fun volume(): Float {
        val level = GameRepository.current.settings.soundVolume.coerceIn(0, 4)
        return level / 4f
    }

    fun play(s: Sfx, rate: Float = 1f) {
        val p = pool ?: return
        if (!ready || !loaded[s.ordinal]) return
        val v = volume() * s.gain
        if (v <= 0.01f) return
        val now = SystemClock.uptimeMillis()
        if (now - lastPlay[s.ordinal] < 60) return
        lastPlay[s.ordinal] = now
        p.play(ids[s.ordinal], v, v, 1, 0, rate.coerceIn(0.5f, 2f))
    }

    /** Sounds for things that happen in the game. */
    fun onEvent(e: GameEvent, state: GameState) {
        val cat = state.pet?.isCat == true
        when (e) {
            is GameEvent.Reaction -> when (e.kind) {
                ReactionKind.EAT -> play(Sfx.MUNCH)
                ReactionKind.PLAY -> play(Sfx.BOING)
                ReactionKind.PET -> if (cat) play(Sfx.PURR) else play(Sfx.CHIRP, 1.2f)
                ReactionKind.CLEAN -> play(if (cat) Sfx.SPARKLE else Sfx.SPLASH)
                ReactionKind.HEAL, ReactionKind.DRINK -> play(Sfx.SPARKLE)
                ReactionKind.REFUSE -> play(Sfx.BONK, 1.3f)
                ReactionKind.TALK -> if (cat) play(Sfx.MEOW, 0.9f + Random.nextFloat() * 0.3f) else play(Sfx.CHIRP, 0.9f + Random.nextFloat() * 0.3f)
                ReactionKind.WORK -> play(Sfx.SUCCESS)
                ReactionKind.BUY -> play(Sfx.COIN)
                ReactionKind.SLEEP, ReactionKind.WAKE, ReactionKind.GAME -> Unit
            }
            is GameEvent.LevelUp -> play(Sfx.LEVELUP)
            GameEvent.Hatched, is GameEvent.Evolved, is GameEvent.PrizeWon -> play(Sfx.FANFARE)
            is GameEvent.Spun -> play(if (e.result.prize != null) Sfx.FANFARE else Sfx.SUCCESS)
            is GameEvent.AchievementUnlocked, is GameEvent.QuestDone, is GameEvent.TalentUp, is GameEvent.JobUp -> play(Sfx.SUCCESS)
            is GameEvent.CoinsGained -> play(Sfx.COIN)
            is GameEvent.EggFound, is GameEvent.Birthday, is GameEvent.SecretFound -> play(Sfx.FANFARE)
            is GameEvent.HiddenFound -> play(Sfx.SPARKLE)
            else -> Unit
        }
    }

    // ------------------------------------------------------------------ synthesis

    private fun buf(seconds: Float) = FloatArray((RATE * seconds).toInt())

    private fun tone(out: FloatArray, start: Float, dur: Float, f0: Float, f1: Float = f0, amp: Float = 0.6f, decay: Float = 6f, harmonics: Float = 0.25f) {
        val s0 = (start * RATE).toInt()
        val n = (dur * RATE).toInt()
        var phase = 0.0
        for (i in 0 until n) {
            val idx = s0 + i
            if (idx >= out.size) break
            val k = i / n.toFloat()
            val f = f0 + (f1 - f0) * k
            phase += 2.0 * PI * f / RATE
            val env = min(1f, i / 80f) * exp(-decay * k)
            val v = sin(phase) + harmonics * sin(2 * phase) + harmonics * 0.4 * sin(3 * phase)
            out[idx] += (v * env * amp).toFloat()
        }
    }

    private fun noise(out: FloatArray, start: Float, dur: Float, amp: Float, smooth: Float, attack: Float = 0.05f, rnd: Random = Random(7)) {
        val s0 = (start * RATE).toInt()
        val n = (dur * RATE).toInt()
        var lp = 0f
        for (i in 0 until n) {
            val idx = s0 + i
            if (idx >= out.size) break
            val k = i / n.toFloat()
            val env = if (k < attack) k / attack else (1f - k).pow(1.5f)
            lp += (rnd.nextFloat() * 2f - 1f - lp) * smooth
            out[idx] += lp * env * amp
        }
    }

    private fun synth(s: Sfx): FloatArray = when (s) {
        Sfx.POP -> buf(0.12f).also { tone(it, 0f, 0.12f, 900f, 260f, 0.8f, 7f, 0.1f) }
        Sfx.COIN -> buf(0.38f).also {
            tone(it, 0f, 0.07f, 988f, 988f, 0.45f, 1f, 0.5f)
            tone(it, 0.07f, 0.3f, 1319f, 1319f, 0.45f, 4f, 0.5f)
        }
        Sfx.SUCCESS -> buf(0.5f).also { o ->
            val notes = floatArrayOf(523f, 659f, 784f, 1047f)
            for ((i, f) in notes.withIndex()) tone(o, i * 0.08f, 0.22f, f, f, 0.4f, 4f, 0.3f)
        }
        Sfx.BONK -> buf(0.2f).also {
            tone(it, 0f, 0.2f, 220f, 90f, 0.8f, 6f, 0.2f)
            noise(it, 0f, 0.06f, 0.3f, 0.3f)
        }
        Sfx.WHOOSH -> buf(0.35f).also { noise(it, 0f, 0.35f, 0.7f, 0.08f, attack = 0.4f) }
        Sfx.SPLASH -> buf(0.45f).also { o ->
            noise(o, 0f, 0.4f, 0.6f, 0.25f, attack = 0.03f)
            val r = Random(3)
            for (i in 0 until 5) tone(o, 0.05f + i * 0.06f, 0.05f, 900f + r.nextFloat() * 900f, 1600f + r.nextFloat() * 800f, 0.2f, 5f, 0f)
        }
        Sfx.MUNCH -> buf(0.5f).also { o ->
            for (i in 0 until 3) noise(o, i * 0.15f, 0.09f, 0.7f, 0.35f, attack = 0.1f, rnd = Random(i))
        }
        Sfx.PURR -> buf(1.2f).also { o ->
            val r = Random(11)
            var lp = 0f
            for (i in o.indices) {
                val t = i / RATE.toFloat()
                lp += (r.nextFloat() * 2f - 1f - lp) * 0.04f
                val am = 0.5f + 0.5f * sin(2 * PI * 24 * t).toFloat()
                val env = min(1f, t * 6f) * min(1f, (1.2f - t) * 4f)
                o[i] = (lp * 3.5f + 0.25f * sin(2 * PI * 50 * t).toFloat()) * am * env * 0.8f
            }
        }
        Sfx.MEOW -> buf(0.6f).also { o ->
            var phase = 0.0
            for (i in o.indices) {
                val k = i / o.size.toFloat()
                val t = i / RATE.toFloat()
                // "mi-aau": rises, then falls
                val f = if (k < 0.35f) 520f + 380f * (k / 0.35f) else 900f - 450f * ((k - 0.35f) / 0.65f)
                phase += 2.0 * PI * (f + 12f * sin(2 * PI * 6 * t)) / RATE
                val env = min(1f, k * 12f) * (1f - k).pow(1.2f)
                // a few harmonics with a vowel-like weighting
                val open = if (k < 0.3f) 0.3f else 1f
                val v = sin(phase) * 0.6 + sin(2 * phase) * 0.45 * open + sin(3 * phase) * 0.3 * open + sin(4 * phase) * 0.12
                o[i] = (v * env * 0.6).toFloat()
            }
        }
        Sfx.CHIRP -> buf(0.3f).also {
            tone(it, 0f, 0.09f, 700f, 1100f, 0.45f, 3f, 0.2f)
            tone(it, 0.12f, 0.12f, 900f, 1400f, 0.45f, 3f, 0.2f)
        }
        Sfx.LEVELUP -> buf(0.9f).also { o ->
            val notes = floatArrayOf(784f, 1047f, 1319f, 1568f)
            for ((i, f) in notes.withIndex()) tone(o, i * 0.11f, if (i == 3) 0.5f else 0.18f, f, f, 0.4f, 3f, 0.35f)
        }
        Sfx.SPARKLE -> buf(0.5f).also { o ->
            val r = Random(5)
            for (i in 0 until 7) {
                val f = 2000f + r.nextFloat() * 2200f
                tone(o, i * 0.05f, 0.12f, f, f, 0.22f, 8f, 0f)
            }
        }
        Sfx.CLICK -> buf(0.04f).also { tone(it, 0f, 0.04f, 1500f, 1200f, 0.5f, 10f, 0f) }
        Sfx.BOING -> buf(0.45f).also { o ->
            var phase = 0.0
            for (i in o.indices) {
                val k = i / o.size.toFloat()
                val t = i / RATE.toFloat()
                val f = 260f + 260f * k + 70f * sin(2 * PI * 14 * t).toFloat() * (1f - k)
                phase += 2.0 * PI * f / RATE
                o[i] = (sin(phase) * min(1f, k * 30f) * (1f - k) * 0.7).toFloat()
            }
        }
        Sfx.FANFARE -> buf(1.1f).also { o ->
            val notes = floatArrayOf(523f, 659f, 784f)
            for ((i, f) in notes.withIndex()) tone(o, i * 0.1f, 0.2f, f, f, 0.35f, 3f, 0.4f)
            for (f in floatArrayOf(523f, 659f, 784f, 1047f)) tone(o, 0.32f, 0.75f, f, f, 0.22f, 2.5f, 0.4f)
            val r = Random(9)
            for (i in 0 until 5) {
                val f = 2500f + r.nextFloat() * 1500f
                tone(o, 0.4f + i * 0.08f, 0.1f, f, f, 0.12f, 8f, 0f)
            }
        }
    }

    private fun writeWav(f: File, data: FloatArray) {
        var peak = 0.01f
        for (v in data) peak = maxOf(peak, kotlin.math.abs(v))
        val gain = if (peak > 0.95f) 0.95f / peak else 1f
        val pcm = ByteBuffer.allocate(data.size * 2).order(ByteOrder.LITTLE_ENDIAN)
        for (v in data) pcm.putShort((v * gain * 32767f).toInt().coerceIn(-32768, 32767).toShort())
        val header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN).apply {
            put("RIFF".toByteArray()); putInt(36 + data.size * 2); put("WAVE".toByteArray())
            put("fmt ".toByteArray()); putInt(16); putShort(1); putShort(1); putInt(RATE); putInt(RATE * 2); putShort(2); putShort(16)
            put("data".toByteArray()); putInt(data.size * 2)
        }
        FileOutputStream(f).use {
            it.write(header.array())
            it.write(pcm.array())
        }
    }
}
