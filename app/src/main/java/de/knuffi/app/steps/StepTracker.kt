package de.knuffi.app.steps

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import androidx.core.content.ContextCompat
import de.knuffi.app.data.GameRepository
import de.knuffi.core.Action
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

/** Reads the hardware step counter (steps since boot) and feeds it into the game. */
object StepTracker : SensorEventListener {
    private var registered = false
    private var lastSent = 0L
    private var pending = -1f
    private val handler = Handler(Looper.getMainLooper())
    private var sensorManager: SensorManager? = null

    private val flush = Runnable {
        if (pending >= 0f) {
            val v = pending
            pending = -1f
            lastSent = SystemClock.elapsedRealtime()
            GameRepository.perform(Action.StepReading(v))
        }
    }

    private fun manager(context: Context): SensorManager? =
        context.applicationContext.getSystemService(Context.SENSOR_SERVICE) as? SensorManager

    fun hasSensor(context: Context): Boolean =
        manager(context)?.getDefaultSensor(Sensor.TYPE_STEP_COUNTER) != null

    fun hasPermission(context: Context): Boolean =
        Build.VERSION.SDK_INT < 29 ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACTIVITY_RECOGNITION) == PackageManager.PERMISSION_GRANTED

    fun canRead(context: Context): Boolean = hasSensor(context) && hasPermission(context)

    fun start(context: Context) {
        if (registered || !canRead(context)) return
        val sm = manager(context) ?: return
        val sensor = sm.getDefaultSensor(Sensor.TYPE_STEP_COUNTER) ?: return
        registered = sm.registerListener(this, sensor, SensorManager.SENSOR_DELAY_UI)
        sensorManager = sm
    }

    fun stop() {
        if (!registered) return
        sensorManager?.unregisterListener(this)
        registered = false
        handler.removeCallbacks(flush)
        flush.run()
    }

    override fun onSensorChanged(event: SensorEvent) {
        val value = event.values.firstOrNull() ?: return
        pending = value
        val now = SystemClock.elapsedRealtime()
        handler.removeCallbacks(flush)
        if (now - lastSent > 2000) flush.run() else handler.postDelayed(flush, 2000)
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit

    /** Reads the counter once (used by the background worker). */
    suspend fun readOnce(context: Context, timeoutMs: Long): Float? {
        if (!canRead(context)) return null
        val sm = manager(context) ?: return null
        val sensor = sm.getDefaultSensor(Sensor.TYPE_STEP_COUNTER) ?: return null
        return withTimeoutOrNull(timeoutMs) {
            suspendCancellableCoroutine<Float> { cont ->
                val listener = object : SensorEventListener {
                    override fun onSensorChanged(event: SensorEvent) {
                        sm.unregisterListener(this)
                        if (cont.isActive) cont.resume(event.values.firstOrNull() ?: -1f)
                    }

                    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
                }
                sm.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_NORMAL, Handler(Looper.getMainLooper()))
                cont.invokeOnCancellation { sm.unregisterListener(listener) }
            }
        }?.takeIf { it >= 0f }
    }
}
