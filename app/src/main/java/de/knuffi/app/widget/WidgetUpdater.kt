package de.knuffi.app.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.res.Configuration
import android.graphics.Bitmap
import android.os.Bundle
import android.view.View
import android.widget.RemoteViews
import de.knuffi.app.R
import de.knuffi.app.data.GameRepository
import de.knuffi.app.notify.ActionReceiver
import de.knuffi.app.notify.Notifier
import de.knuffi.app.render.PetBitmaps
import de.knuffi.app.render.PetLook
import de.knuffi.app.work.PetWorker
import de.knuffi.core.GameState
import de.knuffi.core.Mood
import de.knuffi.core.ThemeMode

class PetWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        GameRepository.init(context)
        GameRepository.tick(userPresent = false, sync = true)
        val state = GameRepository.current
        val frames = WidgetUpdater.frames(state)
        for (id in appWidgetIds) WidgetUpdater.update(context, appWidgetManager, id, state, null, frames)
    }

    override fun onAppWidgetOptionsChanged(context: Context, appWidgetManager: AppWidgetManager, appWidgetId: Int, newOptions: Bundle) {
        GameRepository.init(context)
        val state = GameRepository.current
        WidgetUpdater.update(context, appWidgetManager, appWidgetId, state, null, WidgetUpdater.frames(state))
    }

    override fun onEnabled(context: Context) {
        PetWorker.schedule(context)
    }
}

object WidgetUpdater {
    private val frameIds = intArrayOf(R.id.frame0, R.id.frame1, R.id.frame2, R.id.frame3)

    fun updateAll(context: Context, message: String? = null) {
        val mgr = AppWidgetManager.getInstance(context) ?: return
        val ids = mgr.getAppWidgetIds(ComponentName(context, PetWidgetProvider::class.java))
        if (ids.isEmpty()) return
        GameRepository.init(context)
        val state = GameRepository.current
        val frames = frames(state)
        for (id in ids) update(context, mgr, id, state, message, frames)
    }

    fun frames(state: GameState): List<Bitmap>? {
        val look = PetLook.of(state) ?: return null
        return List(PetBitmaps.FRAME_COUNT) { PetBitmaps.render(look, 240, it) }
    }

    fun update(context: Context, mgr: AppWidgetManager, id: Int, state: GameState, message: String?, frames: List<Bitmap>?) {
        val minWidth = mgr.getAppWidgetOptions(id)?.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH) ?: 0
        val small = minWidth in 1..219
        mgr.updateAppWidget(id, build(context, state, small, message, frames))
    }

    fun build(context: Context, state: GameState, small: Boolean, message: String?, frames: List<Bitmap>? = frames(state)): RemoteViews {
        val views = RemoteViews(context.packageName, if (small) R.layout.widget_pet_small else R.layout.widget_pet)
        val (bg, text, subText, button) = if (isDark(context, state)) {
            Quad(R.drawable.widget_bg_dark, 0xFFF4EEFF.toInt(), 0xFFB3A7CC.toInt(), R.drawable.widget_btn_dark)
        } else {
            Quad(R.drawable.widget_bg_light, 0xFF3A2A45.toInt(), 0xFF8C7A96.toInt(), R.drawable.widget_btn_light)
        }
        views.setInt(R.id.widget_root, "setBackgroundResource", bg)
        views.setTextColor(R.id.name, text)
        views.setTextColor(R.id.status, subText)

        val open = Notifier.openAppIntent(context)
        views.setOnClickPendingIntent(R.id.widget_root, open)

        val pet = state.pet
        if (frames != null) {
            for ((i, fid) in frameIds.withIndex()) views.setImageViewBitmap(fid, frames[i % frames.size])
        }
        views.setOnClickPendingIntent(R.id.pet_flipper, ActionReceiver.pendingIntent(context, ActionReceiver.ACTION_PET))

        if (pet == null || !state.onboarded) {
            views.setTextViewText(R.id.name, "Knuffi")
            views.setTextViewText(R.id.status, "Tippe hier, um dein Ei zu finden 🥚")
            views.setViewVisibility(R.id.pet_flipper, View.INVISIBLE)
            views.setOnClickPendingIntent(R.id.pet_flipper, open)
            setButtonsVisible(views, small, false)
            return views
        }
        views.setViewVisibility(R.id.pet_flipper, View.VISIBLE)

        val nameLine = if (pet.isEgg) pet.name else "${pet.name} · Lv ${pet.level}"
        views.setTextViewText(R.id.name, nameLine)
        views.setTextViewText(R.id.status, message ?: status(state))

        if (!small) {
            views.setProgressBar(R.id.bar_satiety, 100, pet.satiety.toInt(), false)
            views.setProgressBar(R.id.bar_joy, 100, pet.joy.toInt(), false)
            views.setProgressBar(R.id.bar_energy, 100, pet.energy.toInt(), false)
            views.setProgressBar(R.id.bar_hygiene, 100, pet.hygiene.toInt(), false)
            for (tid in intArrayOf(R.id.label_satiety, R.id.label_joy, R.id.label_energy, R.id.label_hygiene)) {
                views.setTextColor(tid, subText)
            }
        }

        val active = pet.alive && !pet.isEgg
        setButtonsVisible(views, small, active)
        if (active) {
            val buttons = if (small) {
                listOf(R.id.btn_feed to ActionReceiver.ACTION_FEED, R.id.btn_clean to ActionReceiver.ACTION_CLEAN)
            } else {
                listOf(
                    R.id.btn_feed to ActionReceiver.ACTION_FEED,
                    R.id.btn_play to ActionReceiver.ACTION_PLAY,
                    R.id.btn_clean to ActionReceiver.ACTION_CLEAN,
                    R.id.btn_sleep to ActionReceiver.ACTION_SLEEP,
                )
            }
            for ((vid, action) in buttons) {
                views.setOnClickPendingIntent(vid, ActionReceiver.pendingIntent(context, action))
                views.setInt(vid, "setBackgroundResource", button)
            }
            if (!small) views.setTextViewText(R.id.btn_sleep, if (pet.sleeping) "☀️" else "🌙")
        }
        return views
    }

    private fun isDark(context: Context, state: GameState): Boolean = when (state.settings.themeMode) {
        ThemeMode.DARK -> true
        ThemeMode.LIGHT -> false
        ThemeMode.SYSTEM -> (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
    }

    private fun setButtonsVisible(views: RemoteViews, small: Boolean, visible: Boolean) {
        views.setViewVisibility(R.id.buttons, if (visible) View.VISIBLE else View.GONE)
        if (!small) views.setViewVisibility(R.id.bars, if (visible) View.VISIBLE else View.GONE)
    }

    fun status(state: GameState): String {
        val pet = state.pet ?: return ""
        if (!pet.alive) return "🌟 ${pet.name} ist bei den Sternen"
        if (pet.isEgg) return "Tippe das Ei an, damit es schlüpft! 🥚"
        val need = pet.needs().firstOrNull()
        return when {
            pet.sleeping -> "💤 Schläft tief und fest …"
            need != null -> "${need.emoji} Ist ${need.title}!"
            pet.mood() == Mood.HAPPY -> "😊 Rundum glücklich!"
            else -> "🙂 ${pet.mood().title}"
        }
    }

    private data class Quad(val a: Int, val b: Int, val c: Int, val d: Int)
}
