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

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        GameRepository.init(this)
        val debug = if (BuildConfig.DEBUG) DebugScenes.fromIntent(intent) else null
        if (debug != null) {
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
        setContent { AppRoot(debug) }
    }

    override fun onStart() {
        super.onStart()
        ScreenPet.appVisible.value = true
        if (GameRepository.previewMode) return
        GameRepository.tick(userPresent = true)
        StepTracker.start(this)
        Notifier.cancelCare(this)
    }

    override fun onStop() {
        super.onStop()
        ScreenPet.appVisible.value = false
        StepTracker.stop()
        if (!GameRepository.previewMode) GameRepository.tick(sync = true, updateWidget = true)
    }
}
