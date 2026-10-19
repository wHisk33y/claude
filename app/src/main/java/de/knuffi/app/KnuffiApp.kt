package de.knuffi.app

import android.app.Application
import de.knuffi.app.data.GameRepository
import de.knuffi.app.notify.Notifier
import de.knuffi.app.work.PetWorker
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch

class KnuffiApp : Application() {
    override fun onCreate() {
        super.onCreate()
        GameRepository.init(this)
        Notifier.createChannels(this)
        PetWorker.schedule(this)
        // 3D or drawn pets, everywhere in the app, wallpaper and floating pet
        MainScope().launch {
            GameRepository.state.collect { de.knuffi.app.render.gl.Pet3D.wanted = it.settings.graphics3d }
        }
    }
}
