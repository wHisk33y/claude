package de.knuffi.app.screen

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import de.knuffi.app.data.GameRepository

/** Brings the floating pet back after a reboot or app update. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED && intent.action != Intent.ACTION_MY_PACKAGE_REPLACED) return
        GameRepository.init(context.applicationContext)
        if (GameRepository.current.settings.overlayPet && ScreenPet.canDrawOverlays(context)) {
            PetOverlayService.start(context)
        }
    }
}
