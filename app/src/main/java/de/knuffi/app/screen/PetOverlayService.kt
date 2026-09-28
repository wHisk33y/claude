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
    private val bounds = FloatArray(3)
    private var lastLook: PetLook? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        GameRepository.init(applicationContext)
        Notifier.createChannels(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
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
            .setContentText("Tippen = streicheln, ziehen = tragen, lange drücken = App öffnen")
            .setContentIntent(open)
            .addAction(0, "Ausblenden", hide)
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
        } else {
            val dm = resources.displayMetrics
            bounds[0] = 0f
            bounds[1] = dm.widthPixels.toFloat()
            bounds[2] = dm.heightPixels.toFloat() - 6f * dm.density
        }
        return bounds
    }

    override fun onStroke() {
        GameRepository.perform(Action.Stroke)
    }

    override fun onHatchTap() {
        GameRepository.perform(Action.HatchTap)
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
        getSystemService(NotificationManager::class.java)?.cancel(NOTIFICATION_ID)
        super.onDestroy()
    }

    companion object {
        private const val NOTIFICATION_ID = 4711
        private const val ACTION_HIDE = "de.knuffi.app.overlay.HIDE"

        fun start(context: Context) {
            if (!ScreenPet.canDrawOverlays(context)) return
            runCatching { ContextCompat.startForegroundService(context, Intent(context, PetOverlayService::class.java)) }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, PetOverlayService::class.java))
        }
    }
}
