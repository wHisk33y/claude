package de.knuffi.app.notify

import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.RemoteInput
import de.knuffi.app.MainActivity
import de.knuffi.app.R
import de.knuffi.app.data.GameRepository
import de.knuffi.app.render.PetBitmaps
import de.knuffi.app.render.PetLook
import de.knuffi.core.Action
import de.knuffi.core.GameState
import de.knuffi.core.Learn
import de.knuffi.core.LearnTask
import de.knuffi.core.Subject
import de.knuffi.core.TimeUtil

/**
 * The Lernzeit in the notifications: the current task can be answered right there
 * (typed with "Antworten" for Mathe, with buttons for Deutsch and Physik).
 */
object LearnNotifier {
    const val CHANNEL = "learn"
    const val ACTION_ANSWER = "de.knuffi.app.action.LEARN_ANSWER"
    const val EXTRA_OPEN = "open"
    const val OPEN_LEARN = "learn"
    private const val KEY_REPLY = "learn_reply"
    private const val EXTRA_CHOICE = "choice"
    private const val EXTRA_SUBJECT = "subject"
    private const val EXTRA_INDEX = "index"
    private const val NOTIFICATION_ID = 4711

    private val praise = listOf("Super!", "Klasse!", "Richtig!", "Stark!", "Genau!", "Toll gemacht!")

    data class Current(val subject: Subject, val index: Int, val task: LearnTask)

    fun createChannel(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java) ?: return
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL, "Lernzeit", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "Die täglichen Aufgaben in Mathe, Deutsch und Physik. Du kannst sie direkt in der Benachrichtigung lösen."
            },
        )
    }

    /** The next unsolved task of today, or null when everything is done (or the Lernzeit is off). */
    fun current(state: GameState, now: Long): Current? {
        val s = state.settings
        if (!s.learnEnabled) return null
        val today = TimeUtil.epochDay(now, GameRepository.zone)
        val subject = Subject.entries.firstOrNull { !Learn.subjectDone(s, state.learn, it, today) } ?: return null
        val index = Learn.solved(state.learn, subject, today)
        return Current(subject, index, Learn.task(subject, Learn.grade(s, state.learn, subject), today, index))
    }

    fun cancel(context: Context) {
        NotificationManagerCompat.from(context).cancel(NOTIFICATION_ID)
    }

    fun openIntent(context: Context): PendingIntent {
        val intent = Intent(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            .putExtra(EXTRA_OPEN, OPEN_LEARN)
        return PendingIntent.getActivity(context, 8, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    }

    /** Shows the current task (with [header] above it, e.g. the reminder or "Richtig!"). */
    @SuppressLint("MissingPermission")
    fun post(context: Context, state: GameState, title: String? = null, header: String? = null, quiet: Boolean = false) {
        if (!Notifier.canPost(context)) return
        val now = System.currentTimeMillis()
        val cur = current(state, now)
        val per = state.settings.learnPerSubject
        val builder = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_knuffi)
            .setContentIntent(openIntent(context))
            .setColor(0xFF5CB8F2.toInt())
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setOnlyAlertOnce(quiet)
            .setSilent(quiet)
        PetLook.of(state)?.let { look ->
            val glasses = if (look.isEgg || look.face != null) look else look.copy(face = "face_round")
            runCatching { builder.setLargeIcon(PetBitmaps.render(glasses, 192)) }
        }
        if (cur == null) {
            val streak = state.learn.streak
            val text = (header?.let { "$it\n" } ?: "") +
                "Alle Aufgaben für heute sind geschafft! 🔥 $streak ${if (streak == 1) "Tag" else "Tage"} am Stück."
            builder.setContentTitle("🎉 Lernzeit geschafft!")
                .setContentText(text)
                .setStyle(NotificationCompat.BigTextStyle().bigText(text))
                .setAutoCancel(true)
            notify(context, builder)
            return
        }
        val t = cur.task
        val choices = shortChoices(t)
        val lettered = choices.any { it.length > 16 }
        val body = buildString {
            if (header != null) append(header).append("\n\n")
            append(t.question)
            if (t.unit.isNotEmpty()) append(" (in ${t.unit})")
            if (!t.typed && lettered) {
                for ((i, c) in choices.withIndex()) append("\n").append('A' + i).append(") ").append(c)
            }
        }
        builder.setContentTitle(title ?: "📚 ${cur.subject.emoji} ${cur.subject.title} · Aufgabe ${cur.index + 1} von $per")
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setAutoCancel(false)
        if (t.typed) {
            val input = RemoteInput.Builder(KEY_REPLY).setLabel("Deine Antwort (z. B. 12 oder 3/4)").build()
            val pi = answerIntent(context, cur, null, mutable = true)
            builder.addAction(
                NotificationCompat.Action.Builder(0, "✏️ Antworten", pi).addRemoteInput(input).setAllowGeneratedReplies(false).build(),
            )
        } else {
            for ((i, c) in choices.withIndex()) {
                builder.addAction(0, if (lettered) "${'A' + i}" else c, answerIntent(context, cur, c, mutable = false))
            }
        }
        notify(context, builder)
    }

    /** At most three answers fit as buttons: the right one and two others, in the task's order. */
    private fun shortChoices(t: LearnTask): List<String> {
        if (t.choices.size <= 3) return t.choices
        val keep = t.choices.filter { it != t.answer }.take(2).toSet() + t.answer
        return t.choices.filter { it in keep }
    }

    private fun answerIntent(context: Context, cur: Current, choice: String?, mutable: Boolean): PendingIntent {
        val intent = Intent(context, ActionReceiver::class.java)
            .setAction(ACTION_ANSWER)
            .putExtra(EXTRA_SUBJECT, cur.subject.name)
            .putExtra(EXTRA_INDEX, cur.index)
        if (choice != null) intent.putExtra(EXTRA_CHOICE, choice)
        val code = 5000 + cur.subject.ordinal * 200 + cur.index * 8 + (choice?.let { cur.task.choices.indexOf(it) + 1 } ?: 0)
        var flags = PendingIntent.FLAG_UPDATE_CURRENT
        flags = flags or if (mutable && Build.VERSION.SDK_INT >= 31) PendingIntent.FLAG_MUTABLE else PendingIntent.FLAG_IMMUTABLE
        return PendingIntent.getBroadcast(context, code, intent, flags)
    }

    @SuppressLint("MissingPermission")
    private fun notify(context: Context, builder: NotificationCompat.Builder) {
        try {
            NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, builder.build())
        } catch (_: SecurityException) {
        }
    }

    /** An answer from the notification. */
    fun handle(context: Context, intent: Intent) {
        val state = GameRepository.current
        val cur = current(state, System.currentTimeMillis())
        if (cur == null) {
            post(context, state, quiet = true)
            return
        }
        val subject = intent.getStringExtra(EXTRA_SUBJECT)
        val index = intent.getIntExtra(EXTRA_INDEX, -1)
        if (subject != cur.subject.name || index != cur.index) {
            // the task was solved in the app in the meantime
            post(context, state, header = "Hier ist deine nächste Aufgabe:", quiet = true)
            return
        }
        val given = RemoteInput.getResultsFromIntent(intent)?.getCharSequence(KEY_REPLY)?.toString()
            ?: intent.getStringExtra(EXTRA_CHOICE)
        if (given.isNullOrBlank()) {
            post(context, state, quiet = true)
            return
        }
        val task = cur.task
        val ok = task.check(given)
        val outcome = GameRepository.perform(Action.LearnAnswer(cur.subject, ok, task.topic), userPresent = true, sync = true)
        val after = outcome.state
        if (ok) {
            val subjectDone = Learn.subjectDone(after.settings, after.learn, cur.subject, TimeUtil.epochDay(System.currentTimeMillis(), GameRepository.zone))
            val firstTry = (state.learn.tries[cur.subject.name] ?: 0) == 0
            val coins = if (subjectDone) "" else " +${if (firstTry) Learn.COINS_FIRST_TRY else Learn.COINS_LATER} 🪙"
            val head = "✅ ${praise.random()} „$given“ stimmt.$coins" + if (subjectDone) "\n${cur.subject.emoji} ${cur.subject.title} ist für heute geschafft! +${Learn.COINS_SUBJECT} 🪙" else ""
            post(context, after, header = head, quiet = true)
        } else {
            post(context, after, title = "📚 ${cur.subject.emoji} Nochmal versuchen", header = "❌ „$given“ stimmt noch nicht.\n💡 ${task.hint}", quiet = true)
        }
    }
}
