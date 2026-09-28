package de.knuffi.app.notify

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationManagerCompat
import de.knuffi.app.data.GameRepository
import de.knuffi.app.widget.WidgetUpdater
import de.knuffi.core.Action
import de.knuffi.core.Catalog
import de.knuffi.core.GameEvent
import de.knuffi.core.ReactionKind

/** Handles the quick actions from notifications and the home screen widget. */
class ActionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        GameRepository.init(context)
        val state = GameRepository.current
        val pet = state.pet
        val action: Action? = when (intent.action) {
            ACTION_FEED -> Action.Feed(Catalog.BASIC_FOOD)
            ACTION_CLEAN -> Action.Clean
            ACTION_SLEEP -> Action.ToggleSleep
            ACTION_PLAY -> Action.Play
            ACTION_PET -> if (pet?.isEgg == true) Action.HatchTap else Action.Stroke
            ACTION_MEDICINE -> Action.UseItem(Catalog.MEDICINE)
            else -> null
        }
        var message: String? = null
        if (action != null && pet != null) {
            val outcome = GameRepository.perform(action, userPresent = true, sync = true)
            message = describe(outcome.events)
        }
        val notificationId = intent.getIntExtra(EXTRA_NOTIFICATION_ID, -1)
        if (notificationId != -1) NotificationManagerCompat.from(context).cancel(notificationId)
        WidgetUpdater.updateAll(context, message)
    }

    private fun describe(events: List<GameEvent>): String? {
        events.filterIsInstance<GameEvent.Message>().firstOrNull()?.let { return it.text }
        if (events.any { it == GameEvent.Hatched }) return "Geschlüpft! 🐣"
        events.filterIsInstance<GameEvent.LevelUp>().lastOrNull()?.let { return "Level ${it.level}! ⭐" }
        val reaction = events.filterIsInstance<GameEvent.Reaction>().firstOrNull() ?: return null
        return when (reaction.kind) {
            ReactionKind.EAT -> "Mampf! 😋"
            ReactionKind.PLAY -> "Juhu! 🎾"
            ReactionKind.PET -> "Hihi! 💕"
            ReactionKind.CLEAN -> "Blitzblank! ✨"
            ReactionKind.HEAL -> "Schon viel besser! 💊"
            ReactionKind.SLEEP -> "Gute Nacht … 💤"
            ReactionKind.WAKE -> "Guten Morgen! ☀️"
            ReactionKind.REFUSE -> "Nö! 💢"
            else -> null
        }
    }

    companion object {
        const val ACTION_FEED = "de.knuffi.app.action.FEED"
        const val ACTION_CLEAN = "de.knuffi.app.action.CLEAN"
        const val ACTION_SLEEP = "de.knuffi.app.action.SLEEP"
        const val ACTION_PLAY = "de.knuffi.app.action.PLAY"
        const val ACTION_PET = "de.knuffi.app.action.PET"
        const val ACTION_MEDICINE = "de.knuffi.app.action.MEDICINE"
        private const val EXTRA_NOTIFICATION_ID = "notification_id"

        fun pendingIntent(context: Context, action: String, notificationId: Int = -1): PendingIntent {
            val intent = Intent(context, ActionReceiver::class.java)
                .setAction(action)
                .putExtra(EXTRA_NOTIFICATION_ID, notificationId)
            val requestCode = action.hashCode() * 31 + notificationId
            return PendingIntent.getBroadcast(context, requestCode, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        }
    }
}
