package de.knuffi.app.screen

import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.view.Gravity
import android.view.View
import android.view.WindowInsets
import android.view.WindowManager
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import de.knuffi.app.MainActivity
import de.knuffi.app.R
import de.knuffi.app.data.GameRepository
import de.knuffi.app.notify.Notifier
import de.knuffi.app.render.PetLook
import de.knuffi.core.Action
import de.knuffi.core.GameState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

/** Keeps the floating pet alive over other apps while the user has it enabled. */
class PetOverlayService : Service(), OverlayPetView.Host {

    private var windowManager: WindowManager? = null
    private var view: OverlayPetView? = null
    private var params: WindowManager.LayoutParams? = null
    private var scope: CoroutineScope? = null
    private val bounds = FloatArray(4)
    private var lastPinned: Boolean? = null
    private var lastLook: PetLook? = null
    private val toys = OverlayToys()
    private val toyCfg = de.knuffi.app.render.ToyConfig()
    private var decor: OverlayDecorView? = null
    private var decorShown = false
    private val splashViews = ArrayList<SplashView>()
    private val splashRnd = kotlin.random.Random(System.nanoTime())

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        GameRepository.init(applicationContext)
        Notifier.createChannels(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_PIN) {
            val s = GameRepository.current.settings
            GameRepository.perform(Action.UpdateSettings(s.copy(overlayPinned = !s.overlayPinned)))
            return START_STICKY
        }
        if (intent?.action == ACTION_HIDE) {
            GameRepository.perform(Action.UpdateSettings(GameRepository.current.settings.copy(overlayPet = false)))
            stopSelf()
            return START_NOT_STICKY
        }
        startInForeground()
        if (!ScreenPet.canDrawOverlays(this) || !GameRepository.current.settings.overlayPet) {
            stopSelf()
            return START_NOT_STICKY
        }
        if (view == null) attach()
        return START_STICKY
    }

    private fun startInForeground() {
        val name = GameRepository.current.pet?.name ?: "Knuffi"
        val open = PendingIntent.getActivity(
            this, 11,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val hide = PendingIntent.getService(
            this, 12,
            Intent(this, PetOverlayService::class.java).setAction(ACTION_HIDE),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val notification = NotificationCompat.Builder(this, Notifier.CH_OVERLAY)
            .setSmallIcon(R.drawable.ic_stat_knuffi)
            .setContentTitle("$name ist auf deinem Bildschirm")
            .setContentText("Tippen = streicheln, doppelt tippen = anheften, ziehen = tragen, lange drücken = App öffnen")
            .setContentIntent(open)
            .addAction(0, "Ausblenden", hide)
            .addAction(
                0,
                if (GameRepository.current.settings.overlayPinned) "Lösen" else "Oben links anheften",
                PendingIntent.getService(this, 13, Intent(this, PetOverlayService::class.java).setAction(ACTION_PIN), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT),
            )
            .setOngoing(true)
            .setSilent(true)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .build()
        val type = if (Build.VERSION.SDK_INT >= 34) ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE else 0
        ServiceCompat.startForeground(this, NOTIFICATION_ID, notification, type)
    }

    private fun attach() {
        val wm = getSystemService(WindowManager::class.java) ?: return
        windowManager = wm
        val v = OverlayPetView(this, this)
        val lp = WindowManager.LayoutParams(
            v.windowW,
            v.windowH,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 0
            y = 0
        }
        // A full-screen layer for footprints, the ladder and paper planes. It lets every touch through
        // (Android only allows that for overlay windows that are at most 80 % opaque).
        val d = OverlayDecorView(this, toys, toyCfg)
        val dlp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            alpha = 0.8f
        }
        if (runCatching { wm.addView(d, dlp) }.isSuccess) decor = d
        try {
            wm.addView(v, lp)
        } catch (e: Exception) {
            stopSelf()
            return
        }
        view = v
        params = lp
        val s = MainScope()
        scope = s
        s.launch {
            GameRepository.state.combine(ScreenPet.appVisible) { state, appVisible -> state to appVisible }
                .collect { (state, appVisible) -> applyState(state, appVisible) }
        }
    }

    private fun applyState(state: GameState, appVisible: Boolean) {
        val v = view ?: return
        if (!state.settings.overlayPet) {
            stopSelf()
            return
        }
        toyCfg.from(state.settings)
        v.name = state.pet?.name ?: ""
        v.showName = state.settings.showNameTag
        v.pinned = state.settings.overlayPinned
        if (lastPinned != null && lastPinned != state.settings.overlayPinned) startInForeground()
        lastPinned = state.settings.overlayPinned
        val pet = state.pet
        val look = if (pet != null && pet.alive) PetLook.of(state) else null
        if (look != lastLook) {
            lastLook = look
            v.look = look
        }
        val show = !appVisible && look != null
        if (show) {
            if (v.visibility != View.VISIBLE) v.visibility = View.VISIBLE
            v.start()
        } else {
            v.stop()
            v.visibility = View.GONE
        }
        decor?.visibility = if (show) View.VISIBLE else View.GONE
        for (sv in splashViews) sv.visibility = if (show) View.VISIBLE else View.GONE
        if (state.settings.toyPaint <= 0) clearSplashes()
    }

    override fun moveWindow(x: Int, y: Int) {
        val v = view ?: return
        val lp = params ?: return
        if (lp.x == x && lp.y == y) return
        lp.x = x
        lp.y = y
        runCatching { windowManager?.updateViewLayout(v, lp) }
    }

    override fun bounds(): FloatArray {
        val wm = windowManager
        if (wm != null && Build.VERSION.SDK_INT >= 30) {
            val m = wm.currentWindowMetrics
            val insets = m.windowInsets.getInsetsIgnoringVisibility(WindowInsets.Type.navigationBars() or WindowInsets.Type.displayCutout())
            bounds[0] = insets.left.toFloat()
            bounds[1] = (m.bounds.width() - insets.right).toFloat()
            bounds[2] = (m.bounds.height() - insets.bottom).toFloat() - 6f * resources.displayMetrics.density
            bounds[3] = m.windowInsets.getInsetsIgnoringVisibility(WindowInsets.Type.statusBars() or WindowInsets.Type.displayCutout()).top.toFloat()
        } else {
            val dm = resources.displayMetrics
            bounds[0] = 0f
            bounds[1] = dm.widthPixels.toFloat()
            bounds[2] = dm.heightPixels.toFloat() - 6f * dm.density
            bounds[3] = 24f * dm.density
        }
        return bounds
    }

    override fun onStroke() {
        GameRepository.perform(Action.Stroke)
    }

    override fun onHatchTap() {
        GameRepository.perform(Action.HatchTap)
    }

    override fun toys(): OverlayToys = toys

    override fun onTogglePin() {
        val s = GameRepository.current.settings
        GameRepository.perform(Action.UpdateSettings(s.copy(overlayPinned = !s.overlayPinned)))
        GameRepository.message(if (!s.overlayPinned) "${GameRepository.current.pet?.name ?: "Dein Haustier"} wartet oben links. Doppelt tippen zum Lösen." else "Und weiter geht's! 🐾")
    }

    override fun toyConfig(): de.knuffi.app.render.ToyConfig = toyCfg

    override fun decorChanged(now: Float, visible: Boolean) {
        val d = decor ?: return
        d.now = now
        if (visible || decorShown) d.invalidate()
        decorShown = visible
    }

    override fun addSplash(x: Float, y: Float): Boolean {
        val wm = windowManager ?: return false
        if (splashViews.size >= de.knuffi.core.ToyTuning.maxSplashes(toyCfg.paint)) return false
        val color = de.knuffi.app.render.Toys.SPLASH_COLORS[splashRnd.nextInt(de.knuffi.app.render.Toys.SPLASH_COLORS.size)]
        val splash = de.knuffi.app.render.Splash(0.5f, 1f, 0.2f, color, splashRnd.nextInt(1000), 0f)
        lateinit var sv: SplashView
        sv = SplashView(this, splash) { wiped ->
            runCatching { wm.removeView(wiped) }
            splashViews.remove(wiped)
            onToy(de.knuffi.core.ToyKind.PAINT)
        }
        val size = sv.sizePx
        val lp = WindowManager.LayoutParams(
            size, size,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            this.x = (x - size / 2f).toInt().coerceAtLeast(0)
            this.y = (y - size / 2f).toInt().coerceAtLeast(0)
        }
        if (runCatching { wm.addView(sv, lp) }.isFailure) return false
        splashViews += sv
        return true
    }

    private fun clearSplashes() {
        for (sv in splashViews) runCatching { windowManager?.removeView(sv) }
        splashViews.clear()
    }

    override fun onToy(kind: de.knuffi.core.ToyKind) {
        GameRepository.perform(Action.Toy(kind))
    }

    override fun onOpenApp() {
        startActivity(Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP))
    }

    override fun onDestroy() {
        scope?.cancel()
        view?.let { v ->
            v.stop()
            runCatching { windowManager?.removeView(v) }
        }
        view = null
        decor?.let { d -> runCatching { windowManager?.removeView(d) } }
        decor = null
        clearSplashes()
        getSystemService(NotificationManager::class.java)?.cancel(NOTIFICATION_ID)
        super.onDestroy()
    }

    companion object {
        private const val NOTIFICATION_ID = 4711
        private const val ACTION_HIDE = "de.knuffi.app.overlay.HIDE"
        private const val ACTION_PIN = "de.knuffi.app.overlay.PIN"

        fun start(context: Context) {
            if (!ScreenPet.canDrawOverlays(context)) return
            runCatching { ContextCompat.startForegroundService(context, Intent(context, PetOverlayService::class.java)) }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, PetOverlayService::class.java))
        }
    }
}
