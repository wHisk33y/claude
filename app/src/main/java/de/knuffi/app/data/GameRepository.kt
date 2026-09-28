package de.knuffi.app.data

import android.content.Context
import de.knuffi.app.widget.WidgetUpdater
import de.knuffi.core.Action
import de.knuffi.core.Engine
import de.knuffi.core.GameEvent
import de.knuffi.core.GameJson
import de.knuffi.core.GameState
import de.knuffi.core.Outcome
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.ZoneId

/**
 * Single source of truth for the game. Lives as long as the process and is shared by
 * the activity, the widget, the notification actions and the background worker.
 */
object GameRepository {
    private const val PREFS = "knuffi_state"
    private const val KEY = "state"

    private lateinit var appContext: Context
    private val lock = Any()
    private val _state = MutableStateFlow(GameState())
    val state: StateFlow<GameState> = _state.asStateFlow()
    private val _events = MutableSharedFlow<GameEvent>(extraBufferCapacity = 64)
    val events: SharedFlow<GameEvent> = _events.asSharedFlow()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var widgetJob: Job? = null

    @Volatile
    var previewMode = false
        private set

    @Volatile
    private var initialized = false

    val zone: ZoneId get() = ZoneId.systemDefault()
    val current: GameState get() = _state.value

    fun init(context: Context) {
        if (initialized) return
        synchronized(lock) {
            if (initialized) return
            appContext = context.applicationContext
            val prefs = appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            _state.value = prefs.getString(KEY, null)?.let { GameJson.decode(it) } ?: GameState()
            initialized = true
        }
    }

    fun perform(action: Action, userPresent: Boolean = true, sync: Boolean = false): Outcome =
        mutate(updateWidget = true, sync = sync) { Engine.perform(it, action, System.currentTimeMillis(), zone, userPresent) }

    fun tick(userPresent: Boolean = false, sync: Boolean = false, updateWidget: Boolean = false): Outcome =
        mutate(updateWidget = updateWidget, sync = sync) { Engine.tick(it, System.currentTimeMillis(), zone, userPresent) }

    /** Shows a short message as a toast in the app. */
    fun message(text: String) {
        _events.tryEmit(GameEvent.Message(text))
    }

    /** Replays an event without changing the state (debug scenes use it to trigger animations). */
    fun emit(event: GameEvent) {
        _events.tryEmit(event)
    }

    /** Wipes everything and starts over with the onboarding. */
    fun reset() {
        mutate(updateWidget = true, sync = true) { Outcome(GameState()) }
    }

    /** Shows a fake state without ever saving it (used for screenshots in debug builds). */
    fun enterPreview(state: GameState) {
        previewMode = true
        _state.value = state
    }

    private fun mutate(updateWidget: Boolean, sync: Boolean, f: (GameState) -> Outcome): Outcome {
        val outcome = synchronized(lock) {
            val o = f(_state.value)
            _state.value = o.state
            if (!previewMode && initialized) save(o.state, sync)
            o
        }
        for (e in outcome.events) _events.tryEmit(e)
        if (updateWidget && !previewMode && initialized) scheduleWidgetUpdate()
        return outcome
    }

    private fun save(state: GameState, sync: Boolean) {
        val editor = appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY, GameJson.encode(state))
        if (sync) editor.commit() else editor.apply()
    }

    fun scheduleWidgetUpdate(delayMs: Long = 350) {
        if (!initialized) return
        widgetJob?.cancel()
        widgetJob = scope.launch {
            delay(delayMs)
            runCatching { WidgetUpdater.updateAll(appContext) }
        }
    }
}
