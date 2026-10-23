package de.knuffi.app.data

import android.app.ActivityManager
import android.app.ApplicationExitInfo
import android.content.Context
import android.os.Build
import android.util.Log
import de.knuffi.app.BuildConfig
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Remembers why the app stopped the last time (crash, native crash, freeze) so the player can
 * copy the report from the settings and send it. Everything stays on the phone.
 */
object CrashLog {
    private const val TAG = "CrashLog"
    private const val FILE = "last_crash.txt"
    private const val PREFS = "crashlog"
    private const val MAX = 12_000

    @Volatile
    private var appContext: Context? = null

    /** Catches crashes on every thread (call first in Application.onCreate). */
    fun install(context: Context) {
        val app = context.applicationContext
        appContext = app
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            runCatching { write(app, "Absturz (Thread „${thread.name}“)", stack(error)) }
            previous?.uncaughtException(thread, error)
        }
    }

    /** Problems the app survived (logged, and kept as the report if there is no crash report). */
    fun note(context: Context, where: String, error: Throwable) {
        Log.w(TAG, where, error)
        runCatching {
            val f = File(context.applicationContext.filesDir, FILE)
            if (!f.exists()) write(context.applicationContext, "Abgefangener Fehler: $where", stack(error))
        }
    }

    /**
     * Android also knows why the app ended when our own handler could not run (native crash in the
     * graphics, freeze). Reads that once per event (Android 11 and newer).
     */
    fun checkExitReasons(context: Context) {
        if (Build.VERSION.SDK_INT < 30) return
        runCatching {
            val am = context.getSystemService(ActivityManager::class.java) ?: return
            val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            val seen = prefs.getLong("seen", 0L)
            val list = am.getHistoricalProcessExitReasons(context.packageName, 0, 8)
            var newest = seen
            for (info in list) {
                if (info.timestamp <= seen) continue
                newest = maxOf(newest, info.timestamp)
                val what = when (info.reason) {
                    ApplicationExitInfo.REASON_CRASH -> "Absturz"
                    ApplicationExitInfo.REASON_CRASH_NATIVE -> "Absturz in der Grafik/System (nativ)"
                    ApplicationExitInfo.REASON_ANR -> "App hat nicht mehr reagiert"
                    ApplicationExitInfo.REASON_LOW_MEMORY -> null
                    else -> null
                } ?: continue
                val body = buildString {
                    append("Prozess: ").append(info.processName).append('\n')
                    append("Beschreibung: ").append(info.description ?: "-").append('\n')
                    append("Wichtigkeit: ").append(info.importance).append('\n')
                    if (info.reason == ApplicationExitInfo.REASON_ANR) {
                        runCatching {
                            info.traceInputStream?.bufferedReader()?.use { r -> append(r.readText().take(6000)) }
                        }
                    }
                }
                // keep our own Java stack trace if it belongs to the same crash
                val own = File(context.filesDir, FILE)
                if (info.reason == ApplicationExitInfo.REASON_CRASH && own.exists() && kotlin.math.abs(own.lastModified() - info.timestamp) < 60_000L) continue
                write(context, what, body, info.timestamp)
            }
            if (newest != seen) prefs.edit().putLong("seen", newest).apply()
        }
    }

    /** The last report, or null. */
    fun report(context: Context): String? = runCatching {
        File(context.filesDir, FILE).takeIf { it.exists() }?.readText()
    }.getOrNull()

    fun clear(context: Context) {
        runCatching { File(context.filesDir, FILE).delete() }
    }

    private fun stack(e: Throwable): String {
        val sw = StringWriter()
        e.printStackTrace(PrintWriter(sw))
        return sw.toString()
    }

    private fun write(context: Context, title: String, body: String, at: Long = System.currentTimeMillis()) {
        val date = SimpleDateFormat("dd.MM.yyyy HH:mm:ss", Locale.GERMANY).format(Date(at))
        val text = buildString {
            append("Knuffi-Fehlerbericht\n")
            append(title).append('\n')
            append("Zeit: ").append(date).append('\n')
            append("Version: ").append(BuildConfig.VERSION_NAME).append(" (").append(BuildConfig.VERSION_CODE).append(")\n")
            append("Android: ").append(Build.VERSION.RELEASE).append(" (SDK ").append(Build.VERSION.SDK_INT).append(")\n")
            append("Gerät: ").append(Build.MANUFACTURER).append(' ').append(Build.MODEL).append('\n')
            append('\n')
            append(body)
        }.take(MAX)
        File(context.filesDir, FILE).writeText(text)
    }
}
