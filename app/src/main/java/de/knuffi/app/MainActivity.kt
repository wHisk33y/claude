package de.knuffi.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import de.knuffi.app.data.GameRepository
import de.knuffi.app.debug.DebugScenes
import de.knuffi.app.notify.Notifier
import de.knuffi.app.screen.PetOverlayService
import de.knuffi.app.screen.ScreenPet
import de.knuffi.app.steps.StepTracker
import de.knuffi.app.ui.AppRoot
import de.knuffi.core.Action
import kotlinx.coroutines.launch
import androidx.lifecycle.lifecycleScope

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        GameRepository.init(this)
        de.knuffi.app.sound.Sounds.init(applicationContext)
        val debug = if (BuildConfig.DEBUG) DebugScenes.fromIntent(intent) else null
        if (BuildConfig.DEBUG && intent.getStringExtra("debug_crash") != null) {
            // debug builds only: checks the crash report in the settings
            Thread({ throw IllegalStateException("Testabsturz für den Fehlerbericht") }, "knuffi-test").start()
        }
        if (debug != null && intent.getStringExtra("debug_persist") != null) {
            // debug builds only: keep the seeded game for real (to test the background services with it)
            GameRepository.restore(debug.state)
            if (debug.state.settings.overlayPet) PetOverlayService.start(this)
        } else if (debug != null) {
            GameRepository.enterPreview(debug.state)
            if (debug.scene == "notify") DebugScenes.postSampleNotifications(this, debug.state)
        } else {
            GameRepository.tick(userPresent = true)
            val state = GameRepository.current
            val hasSensor = StepTracker.hasSensor(this)
            if (state.settings.stepsAvailable != hasSensor) {
                GameRepository.perform(Action.UpdateSettings(state.settings.copy(stepsAvailable = hasSensor)))
            }
            val settings = GameRepository.current.settings
            if (settings.overlayPet) {
                if (ScreenPet.canDrawOverlays(this)) {
                    PetOverlayService.start(this)
                } else {
                    GameRepository.perform(Action.UpdateSettings(settings.copy(overlayPet = false)))
                }
            }
        }
        openFrom(intent)
        setContent { AppRoot(debug) }
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        openFrom(intent)
    }

    /** Notifications can open a page directly (e.g. the Lernzeit). */
    private fun openFrom(intent: android.content.Intent?) {
        if (intent?.getStringExtra(de.knuffi.app.notify.LearnNotifier.EXTRA_OPEN) == de.knuffi.app.notify.LearnNotifier.OPEN_LEARN) {
            de.knuffi.app.ui.AppNav.pending.value = de.knuffi.app.ui.Route.Learn
        }
    }

    override fun onStart() {
        super.onStart()
        ScreenPet.appVisible.value = true
        if (GameRepository.previewMode) return
        GameRepository.tick(userPresent = true)
        StepTracker.start(this)
        Notifier.cancelCare(this)
        lifecycleScope.launch { de.knuffi.app.data.WeatherFetcher.refreshIfNeeded(this@MainActivity) }
    }

    override fun onStop() {
        super.onStop()
        ScreenPet.appVisible.value = false
        StepTracker.stop()
        if (!GameRepository.previewMode) GameRepository.tick(sync = true, updateWidget = true)
    }
}
