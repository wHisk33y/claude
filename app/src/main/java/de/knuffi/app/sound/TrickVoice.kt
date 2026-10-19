package de.knuffi.app.sound

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import de.knuffi.app.data.GameRepository
import de.knuffi.core.Action
import de.knuffi.core.Tricks

/** Shared state: while a command is heard, the loudness listener takes a break. */
object VoiceState {
    var listening by mutableStateOf(false)
        internal set
}

/**
 * Spoken commands for tricks: say "Sitz!" and the pet sits. A learned trick is shown, a trick
 * that is still being learned is practised. Uses the phone's speech recognition (offline if the
 * phone can); nothing is stored.
 */
class TrickVoice(private val context: Context) {
    private var recognizer: SpeechRecognizer? = null
    private val main = Handler(Looper.getMainLooper())

    /** Asks for the microphone; set by the screen that owns this. */
    var requestPermission: () -> Unit = {}

    fun available(): Boolean = SpeechRecognizer.isRecognitionAvailable(context)

    /** Starts (or stops) listening for one command. */
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

    fun listen() {
        VoiceState.listening = true
        // Give the loudness listener a moment to let go of the microphone.
        main.postDelayed({ startRecognizer() }, 250L)
    }

    private fun startRecognizer() {
        if (!VoiceState.listening) return
        val r = recognizer ?: runCatching { SpeechRecognizer.createSpeechRecognizer(context) }.getOrNull()?.also { recognizer = it }
        if (r == null) {
            VoiceState.listening = false
            GameRepository.message("Die Spracherkennung konnte nicht starten.")
            return
        }
        r.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) = Unit
            override fun onBeginningOfSpeech() = Unit
            override fun onRmsChanged(rmsdB: Float) = Unit
            override fun onBufferReceived(buffer: ByteArray?) = Unit
            override fun onEndOfSpeech() = Unit
            override fun onPartialResults(partialResults: Bundle?) = Unit
            override fun onEvent(eventType: Int, params: Bundle?) = Unit

            override fun onError(error: Int) {
                VoiceState.listening = false
                val text = when (error) {
                    SpeechRecognizer.ERROR_NO_MATCH, SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "Ich hab nichts gehört. Tippe auf 🎙️ und sag zum Beispiel „Sitz!“"
                    SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Für die Spracherkennung braucht dein Handy gerade Internet."
                    SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Ohne Mikrofon kann ich dich nicht hören."
                    else -> "Das hat nicht geklappt. Versuch es nochmal! 🎙️"
                }
                GameRepository.message(text)
            }

            override fun onResults(results: Bundle?) {
                VoiceState.listening = false
                handle(results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION).orEmpty())
            }
        })
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "de-DE")
            putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 5)
            putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, context.packageName)
        }
        runCatching { r.startListening(intent) }.onFailure {
            VoiceState.listening = false
            GameRepository.message("Die Spracherkennung konnte nicht starten.")
        }
    }

    private fun handle(heard: List<String>) {
        val s = GameRepository.current
        val pet = s.pet ?: return
        val trick = heard.firstNotNullOfOrNull { Tricks.match(it) }
        if (trick == null) {
            GameRepository.message("„${heard.firstOrNull() ?: "…"}“? Das Kommando kenne ich nicht. 🤔")
            return
        }
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

    fun stop() {
        VoiceState.listening = false
        runCatching { recognizer?.cancel() }
    }

    fun destroy() {
        stop()
        runCatching { recognizer?.destroy() }
        recognizer = null
    }
}
