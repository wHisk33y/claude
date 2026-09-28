package de.knuffi.app.work

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import de.knuffi.app.data.GameRepository
import de.knuffi.app.notify.Notifier
import de.knuffi.app.steps.StepTracker
import de.knuffi.app.widget.WidgetUpdater
import de.knuffi.core.Action
import java.util.concurrent.TimeUnit

/** Runs every ~15 minutes: advances time, reads steps, sends reminders, refreshes the widget. */
class PetWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val ctx = applicationContext
        GameRepository.init(ctx)
        if (GameRepository.previewMode) return Result.success()
        if (GameRepository.current.onboarded) {
            runCatching { StepTracker.readOnce(ctx, 4_000) }.getOrNull()?.let {
                GameRepository.perform(Action.StepReading(it), userPresent = false)
            }
        }
        GameRepository.tick(userPresent = false, sync = true)
        runCatching { Notifier.checkAndNotify(ctx) }
        runCatching { WidgetUpdater.updateAll(ctx) }
        return Result.success()
    }

    companion object {
        private const val NAME = "knuffi_tick"

        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<PetWorker>(15, TimeUnit.MINUTES).build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(NAME, ExistingPeriodicWorkPolicy.KEEP, request)
        }
    }
}
