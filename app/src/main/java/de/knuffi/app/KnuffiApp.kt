package de.knuffi.app

import android.app.Application
import de.knuffi.app.data.GameRepository
import de.knuffi.app.notify.Notifier
import de.knuffi.app.work.PetWorker

class KnuffiApp : Application() {
    override fun onCreate() {
        super.onCreate()
        GameRepository.init(this)
        Notifier.createChannels(this)
        PetWorker.schedule(this)
    }
}
