package de.knuffi.app.sound

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import de.knuffi.app.data.GameRepository
import de.knuffi.app.ui.HeartRain
import de.knuffi.core.Action
import de.knuffi.core.HeartNames
import de.knuffi.core.Tricks
import kotlinx.coroutines.flow.MutableSharedFlow

/** Something the pet should show while it listens. */
sealed interface VoiceCue {
    /** The ears go up: the pet is listening now. */
    data class Listening(val text: String) : VoiceCue

    /** A line for the speech bubble. */
    data class Say(val text: String) : VoiceCue
}

/** Shared state: while commands are heard, the loudness listener takes a break. */
object VoiceState {
    var listening by mutableStateOf(false)
        internal set

    /** What the pet shows in the room while listening. */
    val cues = MutableSharedFlow<VoiceCue>(extraBufferCapacity = 8)

    /** Set by the screen with the 🎙️ button: starts listening when somebody talks to the pet. */
    internal var wake: (() -> Unit)? = null

    internal fun cue(c: VoiceCue) {
        cues.tryEmit(c)
    }
}

/**
 * Spoken commands for tricks: say "Sitz!" and the pet sits. A learned trick is shown, a trick
 * that is still being learned is practised. Uses the phone's speech recognition (offline if the
 * phone can); nothing is stored.
 *
 * After 🎙️ the pet keeps listening for a while, so several commands can be given in a row.
 * With the microphone reactions on, talking to the pet is enough: it pricks up its ears.
 */
class TrickVoice(private val context: Context) {
    private var recognizer: SpeechRecognizer? = null
    private val main = Handler(Looper.getMainLooper())

    /** Until then the pet keeps listening (elapsed realtime). */
    private var sessionUntil = 0L
    private var auto = false
    private var commands = 0
    private var lastAutoEnd = 0L

    /** The command of this utterance was already carried out (from a partial result). */
    private var done = false

    /** Asks for the microphone; set by the screen that owns this. */
    var requestPermission: () -> Unit = {}

    fun available(): Boolean = SpeechRecognizer.isRecognitionAvailable(context)

    /** Starts (or stops) listening for commands. */
    fun toggle() {
        if (VoiceState.listening) {
            stop()
            return
        }
        if (!available()) {
            GameRepository.message("Spracherkennung gibt es auf diesem Handy leider nicht. 🙉")
            return
        }
        if (!MicListener.allowed(context)) {
            requestPermission()
            return
        }
        listen()
    }

    /** Somebody talks to the pet (heard by the loudness listener): listen for a command. */
    fun wake() {
        if (VoiceState.listening || !available() || !MicListener.allowed(context)) return
        if (SystemClock.elapsedRealtime() - lastAutoEnd < AUTO_PAUSE_MS) return
        val pet = GameRepository.current.pet ?: return
        if (pet.isEgg || !pet.alive || pet.sleeping || pet.tricks.isEmpty()) return
        listen(autoWake = true)
    }

    fun listen(autoWake: Boolean = false) {
        VoiceState.listening = true
        auto = autoWake
        commands = 0
        sessionUntil = SystemClock.elapsedRealtime() + if (autoWake) AUTO_SESSION_MS else SESSION_MS
        val name = GameRepository.current.pet?.name ?: "Ich"
        VoiceState.cue(VoiceCue.Listening(if (autoWake) "👂 Ja? Sag's mir!" else "👂 Ich höre! Sag z. B. „Sitz!“"))
        if (!autoWake) GameRepository.message("👂 $name hört jetzt eine Weile zu. Sag ein Kommando, auch mehrere nacheinander!")
        // Give the loudness listener a moment to let go of the microphone.
        main.postDelayed({ startRecognizer() }, 300L)
    }

    private fun startRecognizer() {
        if (!VoiceState.listening) return
        done = false
        val r = recognizer ?: runCatching { SpeechRecognizer.createSpeechRecognizer(context) }.getOrNull()?.also { recognizer = it }
        if (r == null) {
            end()
            GameRepository.message("Die Spracherkennung konnte nicht starten.")
            return
        }
        r.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) = Unit
            override fun onBeginningOfSpeech() = Unit
            override fun onRmsChanged(rmsdB: Float) = Unit
            override fun onBufferReceived(buffer: ByteArray?) = Unit
            override fun onEndOfSpeech() = Unit
            override fun onEvent(eventType: Int, params: Bundle?) = Unit

            override fun onPartialResults(partialResults: Bundle?) {
                // React as soon as a command word is heard, not only when the sentence is over.
                if (done) return
                val heard = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION).orEmpty()
                if (heard.any { Tricks.match(it) != null }) {
                    done = true
                    handle(heard)
                }
            }

            override fun onError(error: Int) {
                val now = SystemClock.elapsedRealtime()
                when (error) {
                    SpeechRecognizer.ERROR_NO_MATCH, SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> {
                        if (now < sessionUntil) {
                            again(200L)
                        } else {
                            if (commands == 0 && !auto) VoiceState.cue(VoiceCue.Say("Ich hab nichts gehört … 🙉"))
                            end()
                        }
                    }
                    SpeechRecognizer.ERROR_RECOGNIZER_BUSY, SpeechRecognizer.ERROR_CLIENT -> {
                        // the recognizer was still busy: start fresh
                        runCatching { recognizer?.destroy() }
                        recognizer = null
                        if (now < sessionUntil) again(600L) else end()
                    }
                    SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> {
                        end()
                        GameRepository.message("Für die Spracherkennung braucht dein Handy gerade Internet.")
                    }
                    SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> {
                        end()
                        GameRepository.message("Ohne Mikrofon kann ich dich nicht hören.")
                    }
                    else -> {
                        end()
                        if (!auto) GameRepository.message("Das hat nicht geklappt. Versuch es nochmal! 🎙️")
                    }
                }
            }

            override fun onResults(results: Bundle?) {
                val heard = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION).orEmpty()
                if (!done) handle(heard)
                if (SystemClock.elapsedRealtime() < sessionUntil) again(900L) else end()
            }
        })
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "de-DE")
            putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 5)
            putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, context.packageName)
        }
        runCatching { r.startListening(intent) }.onFailure {
            end()
            GameRepository.message("Die Spracherkennung konnte nicht starten.")
        }
    }

    /** Listens for the next command after a short break. */
    private fun again(delayMs: Long) {
        if (!VoiceState.listening) return
        main.postDelayed({ startRecognizer() }, delayMs)
    }

    private fun handle(heard: List<String>) {
        if (heard.isEmpty()) return
        // Easter egg: some names fill the screen with hearts.
        if (heard.any { HeartNames.mentioned(it) }) {
            HeartRain.start()
            VoiceState.cue(VoiceCue.Say("💕💕💕"))
            keepListening()
            return
        }
        val s = GameRepository.current
        val pet = s.pet ?: return
        val trick = heard.firstNotNullOfOrNull { Tricks.match(it) }
        if (trick == null) {
            if (heard.any { Tricks.calledByName(it, pet.name) }) {
                VoiceState.cue(VoiceCue.Listening("👂 Ja? Was soll ich machen?"))
                keepListening()
            } else {
                VoiceState.cue(VoiceCue.Say("„${heard.first()}“? Das kenne ich nicht. 🤔"))
            }
            return
        }
        commands++
        keepListening()
        val blocker = Tricks.blocker(s, trick)
        when {
            blocker != null -> GameRepository.message(blocker)
            Tricks.learned(pet, trick) -> GameRepository.perform(Action.ShowTrick(trick))
            else -> {
                GameRepository.perform(Action.PracticeTrick(trick))
                GameRepository.message("${trick.emoji} ${trick.title} übt ${pet.name} noch (${Tricks.points(GameRepository.current.pet ?: pet, trick)}/${trick.practice}).")
            }
        }
    }

    /** A command came: keep listening a bit longer for the next one. */
    private fun keepListening() {
        sessionUntil = maxOf(sessionUntil, SystemClock.elapsedRealtime() + if (auto) AUTO_SESSION_MS else SESSION_MS)
    }

    private fun end() {
        if (auto) lastAutoEnd = SystemClock.elapsedRealtime()
        VoiceState.listening = false
        main.removeCallbacksAndMessages(null)
    }

    fun stop() {
        end()
        runCatching { recognizer?.cancel() }
    }

    fun destroy() {
        stop()
        runCatching { recognizer?.destroy() }
        recognizer = null
    }

    private companion object {
        /** After 🎙️ the pet listens this long after the last command. */
        const val SESSION_MS = 40_000L

        /** When talking woke it up, it listens a shorter time. */
        const val AUTO_SESSION_MS = 15_000L

        /** After listening by itself the pet takes a break, so chatter around does not keep it listening. */
        const val AUTO_PAUSE_MS = 20_000L
    }
}
