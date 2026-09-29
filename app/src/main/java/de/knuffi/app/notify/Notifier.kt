package de.knuffi.app.notify

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import de.knuffi.app.MainActivity
import de.knuffi.app.R
import de.knuffi.app.data.GameRepository
import de.knuffi.app.render.PetBitmaps
import de.knuffi.app.render.PetLook
import de.knuffi.core.Action
import de.knuffi.core.Catalog
import de.knuffi.core.DailyRewards
import de.knuffi.core.Difficulty
import de.knuffi.core.EventCalendar
import de.knuffi.core.GameState
import de.knuffi.core.StepRewards
import de.knuffi.core.TimeUtil
import java.time.ZoneId

object Notifier {
    const val CH_CARE = "care"
    const val CH_EVENTS = "events"
    const val CH_OVERLAY = "overlay"

    private const val HOUR = 3_600_000L

    data class Spec(
        val key: String,
        val channel: String,
        val title: String,
        val text: String,
        val cooldownMs: Long,
        val actions: List<Pair<String, String>> = emptyList(),
        val urgent: Boolean = false,
    ) {
        val id: Int get() = 1000 + (key.hashCode() and 0xFFFF)
    }

    fun createChannels(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java) ?: return
        nm.createNotificationChannel(
            NotificationChannel(CH_CARE, "Pflege-Erinnerungen", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "Wenn dein Haustier Hunger hat, krank ist oder dich vermisst."
            },
        )
        nm.createNotificationChannel(
            NotificationChannel(CH_EVENTS, "Belohnungen & Ereignisse", NotificationManager.IMPORTANCE_LOW).apply {
                description = "Tägliche Belohnungen, Schrittziele und Neuigkeiten."
            },
        )
        nm.createNotificationChannel(
            NotificationChannel(CH_OVERLAY, "Haustier auf dem Bildschirm", NotificationManager.IMPORTANCE_MIN).apply {
                description = "Dauerhafte Info, solange dein Haustier über anderen Apps herumläuft."
                setShowBadge(false)
            },
        )
    }

    fun hasPermission(context: Context): Boolean =
        Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    fun canPost(context: Context): Boolean =
        hasPermission(context) && NotificationManagerCompat.from(context).areNotificationsEnabled()

    private fun inQuietHours(hour: Int, start: Int, end: Int): Boolean =
        if (start <= end) hour in start until end else hour >= start || hour < end

    fun evaluate(state: GameState, now: Long, zone: ZoneId): List<Spec> {
        val pet = state.pet ?: return emptyList()
        if (!state.onboarded || !state.settings.notifications || !pet.alive) return emptyList()
        val name = pet.name
        val hour = TimeUtil.hour(now, zone)
        val settings = state.settings
        val quiet = settings.quietHours && inQuietHours(hour, settings.quietStart, settings.quietEnd)
        val list = mutableListOf<Spec>()

        if (pet.isEgg) {
            if (now - pet.bornAt > 2 * HOUR && !quiet) {
                list += Spec("egg", CH_CARE, "🥚 Dein Ei wackelt!", "Etwas möchte schlüpfen. Schau schnell nach!", 12 * HOUR)
            }
        } else {
            if (state.difficulty == Difficulty.CLASSIC && pet.health < 30) {
                list += Spec(
                    "critical", CH_CARE, "⚠️ $name geht es sehr schlecht!",
                    "Im klassischen Modus kann $name zu den Sternen reisen. Bitte kümmere dich jetzt um $name!",
                    2 * HOUR, urgent = true,
                )
            }
            if (!quiet) {
                if (pet.sick) {
                    val actions = if (state.count(Catalog.MEDICINE) > 0) listOf("Medizin geben" to ActionReceiver.ACTION_MEDICINE) else emptyList()
                    list += Spec("sick", CH_CARE, "🤒 $name ist krank!", "$name fühlt sich gar nicht gut und braucht Medizin.", 3 * HOUR, actions)
                }
                if (pet.satiety < 25) {
                    list += Spec(
                        "hungry", CH_CARE, "🍙 $name hat Hunger!", "Der Bauch knurrt schon ganz laut …", 3 * HOUR,
                        listOf("Füttern" to ActionReceiver.ACTION_FEED),
                    )
                }
                if (pet.poops >= 2 || pet.hygiene < 25) {
                    list += Spec(
                        "dirty", CH_CARE, "🫧 Hier riecht's etwas …", "$name braucht dringend ein bisschen Sauberkeit.", 3 * HOUR,
                        listOf("Saubermachen" to ActionReceiver.ACTION_CLEAN),
                    )
                }
                if (!pet.sleeping && pet.joy < 25) {
                    list += Spec("bored", CH_CARE, "🥺 $name vermisst dich", "Spiel doch eine Runde mit $name!", 4 * HOUR)
                }
                if (!pet.sleeping && pet.energy < 22) {
                    list += Spec(
                        "tired", CH_CARE, "💤 $name ist hundemüde", "Zeit fürs Bett?", 4 * HOUR,
                        listOf("Ins Bett bringen" to ActionReceiver.ACTION_SLEEP),
                    )
                }
                val today = TimeUtil.epochDay(now, zone)
                if (hour >= 10 && state.daily.rewardClaimedDay != today && state.daily.lastLoginDay != today) {
                    val nextStreak = if (state.daily.lastLoginDay == today - 1) state.daily.streak + 1 else 1
                    val coins = DailyRewards.coins[DailyRewards.dayIndex(nextStreak)]
                    val streakText = if (nextStreak > 1) " und halte deine Serie von $nextStreak Tagen" else ""
                    list += Spec("daily", CH_EVENTS, "🎁 Deine tägliche Belohnung wartet!", "Hol dir heute $coins Münzen$streakText!", 20 * HOUR)
                }
                val tier = StepRewards.tiers.withIndex().lastOrNull { (i, t) -> state.steps.today >= t.first && i !in state.steps.claimedTiers }
                if (tier != null) {
                    list += Spec(
                        "steps", CH_EVENTS, "👟 Toll gelaufen!",
                        "Du hast ${TimeUtil.formatNumber(tier.value.first)} Schritte geschafft. Hol dir ${tier.value.second} Münzen ab!",
                        6 * HOUR,
                    )
                }
            }
        }
        if (!quiet) list += worldSpecs(state, now, zone)
        return list.filter { now - (state.notifyLog[it.key] ?: 0L) >= it.cooldownMs }.take(2)
    }

    /** Garden, trips, festivals and birthdays. Each fires once for each new occasion. */
    private fun worldSpecs(state: GameState, now: Long, zone: ZoneId): List<Spec> {
        val list = mutableListOf<Spec>()
        val ready = state.garden.plots.filter { it.ready(now) }
        if (ready.isNotEmpty() && ready.maxOf { it.readyAt } > (state.notifyLog["garden"] ?: 0L)) {
            list += Spec(
                "garden", CH_EVENTS, "🌻 Dein Garten ist reif!",
                if (ready.size == 1) "Eine Pflanze wartet auf die Ernte." else "${ready.size} Pflanzen warten auf die Ernte.",
                0L,
            )
        }
        val back = state.trips.filter { now >= it.endsAt }
        if (back.isNotEmpty() && back.maxOf { it.endsAt } > (state.notifyLog["trip"] ?: 0L)) {
            val names = back.mapNotNull { t -> state.allPets.firstOrNull { it.id == t.petId }?.name }
            val who = names.firstOrNull() ?: "Dein Haustier"
            list += Spec(
                "trip", CH_EVENTS, "🎒 $who ist zurück!",
                if (names.size > 1) "${names.joinToString(" und ")} haben etwas vom Ausflug mitgebracht." else "$who hat etwas vom Ausflug mitgebracht. Schau nach!",
                0L,
            )
        }
        val event = EventCalendar.active(now, zone)
        if (event != null) {
            val key = "event_" + EventCalendar.key(event, EventCalendar.date(now, zone))
            if (key !in state.notifyLog) {
                list += Spec(key, CH_EVENTS, "${event.emoji} ${event.title} hat begonnen!", "Sammle ${event.tokenName} ${event.tokenEmoji} und hol dir das ${event.egg.title}!", 0L)
            }
        }
        val pet = state.pet
        if (pet != null && pet.alive && !pet.isEgg && pet.hatchedAt > 0L) {
            val months = pet.ageDays(now) / 30
            if (months >= 1 && "bday:${pet.id}:$months" !in state.milestones && "birthday_$months" !in state.notifyLog) {
                list += Spec(
                    "birthday_$months", CH_EVENTS, "🎂 ${pet.name} hat Geburtstag!",
                    if (months % 12 == 0) "Ihr seid heute ${months / 12} ${if (months == 12) "Jahr" else "Jahre"} zusammen! Komm feiern." else "${pet.name} ist heute $months ${if (months == 1) "Monat" else "Monate"} bei dir. Komm feiern!",
                    0L,
                )
            }
        }
        return list
    }

    /** Checks the current state and posts reminders if necessary. */
    fun checkAndNotify(context: Context) {
        if (!canPost(context)) return
        val now = System.currentTimeMillis()
        val state = GameRepository.current
        val specs = evaluate(state, now, GameRepository.zone)
        for (spec in specs) {
            post(context, spec, state)
            GameRepository.perform(Action.MarkNotified(spec.key), userPresent = false, sync = true)
        }
    }

    fun openAppIntent(context: Context): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        return PendingIntent.getActivity(context, 7, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    }

    @SuppressLint("MissingPermission")
    fun post(context: Context, spec: Spec, state: GameState) {
        if (!canPost(context)) return
        val builder = NotificationCompat.Builder(context, spec.channel)
            .setSmallIcon(R.drawable.ic_stat_knuffi)
            .setContentTitle(spec.title)
            .setContentText(spec.text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(spec.text))
            .setContentIntent(openAppIntent(context))
            .setAutoCancel(true)
            .setColor(0xFFFF7EB6.toInt())
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setPriority(if (spec.urgent) NotificationCompat.PRIORITY_HIGH else NotificationCompat.PRIORITY_DEFAULT)
        PetLook.of(state)?.let { look ->
            runCatching { builder.setLargeIcon(PetBitmaps.render(look, 192)) }
        }
        for ((label, action) in spec.actions) {
            builder.addAction(0, label, ActionReceiver.pendingIntent(context, action, spec.id))
        }
        try {
            NotificationManagerCompat.from(context).notify(spec.id, builder.build())
        } catch (_: SecurityException) {
        }
    }

    fun cancelCare(context: Context) {
        val nm = NotificationManagerCompat.from(context)
        for (key in listOf("hungry", "dirty", "bored", "tired", "sick", "critical", "egg")) {
            nm.cancel(Spec(key, CH_CARE, "", "", 0).id)
        }
    }
}
