package de.knuffi.app.screen

import android.app.WallpaperManager
import android.content.res.Configuration
import android.graphics.Canvas
import android.graphics.Typeface
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.service.wallpaper.WallpaperService
import android.view.SurfaceHolder
import androidx.core.content.res.ResourcesCompat
import de.knuffi.app.R
import de.knuffi.app.data.GameRepository
import de.knuffi.app.render.LayoutKind
import de.knuffi.app.render.ParticleSystem
import de.knuffi.app.render.PetDirector
import de.knuffi.app.render.PetLook
import de.knuffi.app.render.RoomRenderer
import de.knuffi.app.render.SceneModel
import de.knuffi.app.render.SceneRenderer
import de.knuffi.core.Action
import de.knuffi.core.GameState
import de.knuffi.core.ThemeMode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalTime

/** Live wallpaper: the pet lives in its room on the home screen. */
class KnuffiWallpaperService : WallpaperService() {

    override fun onCreateEngine(): Engine = PetEngine()

    private inner class PetEngine : Engine() {
        private val handler = Handler(Looper.getMainLooper())
        private val director = PetDirector(seed = 11)
        private val particles = ParticleSystem()
        private val scene = SceneRenderer()
        private val model = SceneModel()
        private var scope: CoroutineScope? = null
        private var visible = false
        private var width = 0f
        private var height = 0f
        private val start = SystemClock.elapsedRealtime()
        private var lastTick = 0L
        private var lastHourUpdate = 0L
        private var lastState: GameState? = null
        private val frame = Runnable { drawFrame() }

        override fun onCreate(surfaceHolder: SurfaceHolder) {
            super.onCreate(surfaceHolder)
            GameRepository.init(applicationContext)
            val tf = runCatching { ResourcesCompat.getFont(this@KnuffiWallpaperService, R.font.fredoka_semibold) }.getOrNull() ?: Typeface.DEFAULT_BOLD
            scene.setTypeface(tf)
            particles.typeface = tf
            val today = LocalDate.now()
            model.weather = RoomRenderer.weatherFor(today.toEpochDay(), today.monthValue)
            scope = MainScope().also { s ->
                s.launch {
                    GameRepository.events.collect { if (visible) director.onEvent(it, particles, scene.headNX, scene.headNY) }
                }
            }
        }

        override fun onVisibilityChanged(visible: Boolean) {
            this.visible = visible
            handler.removeCallbacks(frame)
            if (visible) {
                if (!GameRepository.previewMode) GameRepository.tick()
                lastTick = SystemClock.elapsedRealtime()
                handler.post(frame)
            }
        }

        override fun onSurfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
            super.onSurfaceChanged(holder, format, width, height)
            this.width = width.toFloat()
            this.height = height.toFloat()
            scene.release()
            handler.removeCallbacks(frame)
            if (visible) handler.post(frame)
        }

        override fun onSurfaceDestroyed(holder: SurfaceHolder) {
            visible = false
            handler.removeCallbacks(frame)
            super.onSurfaceDestroyed(holder)
        }

        override fun onDestroy() {
            handler.removeCallbacks(frame)
            scope?.cancel()
            scene.release()
            super.onDestroy()
        }

        override fun onCommand(action: String?, x: Int, y: Int, z: Int, extras: Bundle?, resultRequested: Boolean): Bundle? {
            if (action == WallpaperManager.COMMAND_TAP) handleTap(x.toFloat(), y.toFloat())
            return null
        }

        private fun handleTap(x: Float, y: Float) {
            if (width <= 0f || height <= 0f) return
            val b = scene.petBounds
            val pad = b.width() * 0.2f
            val look = model.look
            if (look != null && !b.isEmpty && x in (b.left - pad)..(b.right + pad) && y in (b.top - pad)..(b.bottom + pad)) {
                if (look.isEgg) {
                    director.onEggTap(particles, x / width, y / height)
                    GameRepository.perform(Action.HatchTap)
                } else {
                    director.onTap()
                    GameRepository.perform(Action.Stroke)
                }
            } else {
                director.focus(x / width, y / height, scene.centerNX, scene.centerNY)
                if (y / height > 0.55f) director.pointAt(x / width)
            }
        }

        private fun darkMode(state: GameState): Boolean = when (state.settings.themeMode) {
            ThemeMode.DARK -> true
            ThemeMode.LIGHT -> false
            ThemeMode.SYSTEM -> (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
        }

        private fun drawFrame() {
            if (!visible || width <= 0f || height <= 0f) return
            val nowMs = SystemClock.elapsedRealtime()
            val t = (nowMs - start) / 1000f
            if (nowMs - lastTick > 60_000L) {
                lastTick = nowMs
                if (!GameRepository.previewMode) GameRepository.tick()
            }
            if (nowMs - lastHourUpdate > 20_000L || lastHourUpdate == 0L) {
                lastHourUpdate = nowMs
                val now = LocalTime.now()
                model.hour = now.hour + now.minute / 60f
            }
            val state = GameRepository.current
            if (state !== lastState) {
                lastState = state
                model.look = PetLook.of(state)?.takeIf { state.pet?.alive == true }
                model.needs = state.pet?.needs() ?: emptyList()
                model.room = state.room
                model.poops = state.pet?.poops ?: 0
                model.lightsOff = state.pet?.sleeping == true
                model.darkUi = darkMode(state)
            }
            var canvas: Canvas? = null
            val holder = surfaceHolder
            try {
                canvas = try {
                    holder.lockHardwareCanvas()
                } catch (_: Exception) {
                    holder.lockCanvas()
                }
                if (canvas != null) {
                    scene.configure(director, width, height, LayoutKind.WALLPAPER)
                    director.update(t, model.look, model.needs, particles, scene.headNX, scene.headNY)
                    particles.update(t)
                    scene.draw(canvas, width, height, LayoutKind.WALLPAPER, model, director, particles, t)
                }
            } catch (_: Exception) {
                // Surface went away mid frame; the next visibility change restarts drawing.
            } finally {
                if (canvas != null) runCatching { holder.unlockCanvasAndPost(canvas) }
            }
            handler.removeCallbacks(frame)
            if (visible) handler.postDelayed(frame, FRAME_MS)
        }
    }

    private companion object {
        const val FRAME_MS = 33L
    }
}
