package de.knuffi.app.debug

import android.content.Context
import android.content.Intent
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import de.knuffi.app.notify.ActionReceiver
import de.knuffi.app.notify.Notifier
import de.knuffi.app.render.PetLook
import de.knuffi.app.ui.PetPortrait
import de.knuffi.app.ui.Route
import de.knuffi.app.ui.Tab
import de.knuffi.app.widget.WidgetUpdater
import de.knuffi.core.Counters
import de.knuffi.core.DailyState
import de.knuffi.core.Difficulty
import de.knuffi.core.Form
import de.knuffi.core.GameState
import de.knuffi.core.MiniGame
import de.knuffi.core.Mood
import de.knuffi.core.Pet
import de.knuffi.core.QuestProgress
import de.knuffi.core.QuestType
import de.knuffi.core.Slot
import de.knuffi.core.StepState
import de.knuffi.core.TimeUtil
import de.knuffi.core.VisualStyle
import java.time.ZoneId

/**
 * Debug-only launch hooks used by CI to take screenshots of every screen in every style:
 * `adb shell am start -n de.knuffi.app.debug/de.knuffi.app.MainActivity --es debug_scene shop --es debug_style pixel`.
 * The seeded state is never saved.
 */
object DebugScenes {
    data class Launch(val scene: String, val state: GameState, val hour: Float?)

    fun fromIntent(intent: Intent?): Launch? {
        val scene = intent?.getStringExtra("debug_scene") ?: return null
        val style = when (intent.getStringExtra("debug_style")) {
            "pixel" -> VisualStyle.PIXEL
            "minimal" -> VisualStyle.MINIMAL
            else -> VisualStyle.KAWAII
        }
        val form = intent.getStringExtra("debug_form")?.let { f -> Form.entries.firstOrNull { it.name.equals(f, true) } }
        val hour = intent.getStringExtra("debug_hour")?.toFloatOrNull() ?: when (scene) {
            "night" -> 23f
            else -> 11f
        }
        return Launch(scene, seed(style, scene, form), hour)
    }

    fun initialRoute(scene: String?): Route = when (scene) {
        "catch" -> Route.Game(MiniGame.CATCH)
        "memory" -> Route.Game(MiniGame.MEMORY)
        "whack" -> Route.Game(MiniGame.WHACK)
        "settings" -> Route.Settings
        "gallery" -> Route.Gallery
        "widget" -> Route.WidgetPreview
        else -> Route.Main
    }

    fun initialTab(scene: String?): Tab = when (scene) {
        "games" -> Tab.GAMES
        "shop" -> Tab.SHOP
        "goals" -> Tab.GOALS
        "walk" -> Tab.WALK
        else -> Tab.HOME
    }

    private fun seed(style: VisualStyle, scene: String, form: Form?): GameState {
        if (scene == "onboarding") return GameState(style = style)
        val now = System.currentTimeMillis()
        val today = TimeUtil.epochDay(now, ZoneId.systemDefault())
        val day = 86_400_000L
        val chosenForm = form ?: when (scene) {
            "egg" -> Form.EGG
            "forest", "ocean" -> Form.FLITZER
            "space" -> Form.DRAKO
            "candy" -> Form.MOCHI_KOENIG
            "sick" -> Form.HOPSI
            "evolution" -> Form.STELLARIS
            else -> Form.LUMI
        }
        var pet = Pet(
            name = "Mochi",
            form = chosenForm,
            bornAt = now - 5 * day,
            hatchedAt = now - 5 * day,
            level = if (chosenForm.stage.ordinal >= 4) 15 else 9,
            xp = 120,
            satiety = 72.0,
            joy = 88.0,
            energy = 64.0,
            hygiene = 70.0,
            health = 92.0,
        )
        pet = when (scene) {
            "sick" -> pet.copy(sick = true, poops = 2, hygiene = 22.0, satiety = 24.0, joy = 30.0)
            "sleep", "night" -> pet.copy(sleeping = true, energy = 35.0)
            "sad" -> pet.copy(joy = 12.0, satiety = 30.0)
            "egg" -> pet.copy(hatchTaps = 2)
            "memorial" -> pet.copy(alive = false)
            else -> pet
        }
        val room = when (scene) {
            "forest" -> "room_forest"
            "ocean" -> "room_ocean"
            "space" -> "room_space"
            "candy" -> "room_candy"
            else -> "room_cozy"
        }
        val equipped = mutableMapOf(Slot.ROOM to room, Slot.HAT to "hat_bow")
        if (scene == "space") {
            equipped[Slot.HAT] = "hat_wizard"
            equipped[Slot.FACE] = "face_sun"
        }
        if (scene == "candy") {
            equipped.remove(Slot.HAT)
            equipped[Slot.NECK] = "neck_bell"
        }
        if (scene == "ocean") {
            equipped[Slot.HAT] = "hat_cap"
            equipped[Slot.NECK] = "neck_scarf"
        }
        return GameState(
            onboarded = true,
            style = style,
            difficulty = if (scene == "memorial") Difficulty.CLASSIC else Difficulty.RELAXED,
            pet = pet,
            coins = 1234,
            inventory = mapOf("apple" to 3, "cake" to 1, "medicine" to 2, "candy" to 5, "cocoa" to 1, "bath" to 1),
            owned = setOf("room_cozy", "room_forest", "room_ocean", "room_space", "room_candy", "hat_bow", "hat_cap", "hat_wizard", "face_round", "face_sun", "neck_scarf", "neck_bell"),
            equipped = equipped,
            counters = Counters(
                feeds = 42, plays = 20, pets = 64, cleans = 9, gamesPlayed = 12, purchases = 7, bestCatch = 240,
                bestMemoryMoves = 14, bestWhack = 31, totalSteps = 45_678, bestDaySteps = 8_000, coinsEarned = 2_345,
                evolutions = 2, hatched = 1, maxLevel = 9, maxStage = 3, maxStreak = 4, quests = 11,
            ),
            achievements = mapOf("HATCH" to now, "FEED_10" to now, "LEVEL_5" to now, "EVOLVE_CHILD" to now, "EVOLVE_TEEN" to now, "STREAK_3" to now, "GAMES_10" to now),
            daily = DailyState(
                day = today,
                quests = listOf(
                    QuestProgress(QuestType.FEED, 3, 2),
                    QuestProgress(QuestType.GAMES, 2, 2),
                    QuestProgress(QuestType.STEPS, 3000, 1800),
                ),
                lastLoginDay = today,
                streak = 4,
                rewardClaimedDay = -1,
            ),
            steps = StepState(lastSensor = 0f, day = today, today = 3_456, claimedTiers = setOf(0)),
            lastSimulated = now,
        )
    }

    fun postSampleNotifications(context: Context, state: GameState) {
        Notifier.post(
            context,
            Notifier.Spec("hungry", Notifier.CH_CARE, "🍙 Mochi hat Hunger!", "Der Bauch knurrt schon ganz laut …", 0, listOf("Füttern" to ActionReceiver.ACTION_FEED)),
            state,
        )
        Notifier.post(
            context,
            Notifier.Spec("daily", Notifier.CH_EVENTS, "🎁 Deine tägliche Belohnung wartet!", "Hol dir heute 30 Münzen und halte deine Serie von 4 Tagen!", 0),
            state,
        )
    }

    @Composable
    fun Gallery(state: GameState) {
        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(12.dp),
        ) {
            Text("Alle Formen", style = MaterialTheme.typography.titleLarge)
            for (row in Form.entries.chunked(4)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    for (f in row) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            PetPortrait(PetLook(f, Mood.HAPPY), state.style, Modifier.size(84.dp))
                            Text(f.title, style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            Text("Stimmungen & Extras", style = MaterialTheme.typography.titleLarge)
            val base = PetLook(Form.HOPSI, Mood.OKAY)
            val variants = listOf(
                "Glücklich" to base.copy(mood = Mood.HAPPY),
                "Traurig" to base.copy(mood = Mood.SAD),
                "Krank" to base.copy(mood = Mood.SICK, sick = true),
                "Schläft" to base.copy(sleeping = true, mood = Mood.SLEEPING),
                "Krone" to PetLook(Form.MAMPFI, Mood.HAPPY, hat = "hat_crown", neck = "neck_bowtie"),
                "Zauber" to PetLook(Form.LUMI, Mood.OKAY, hat = "hat_wizard", face = "face_round"),
                "Cool" to PetLook(Form.GRUMMEL, Mood.OKAY, hat = "hat_cap", face = "face_sun"),
                "Party" to PetLook(Form.BABY, Mood.HAPPY, hat = "hat_party", face = "face_heart", neck = "neck_scarf"),
                "Zylinder" to PetLook(Form.SCHATTLING, Mood.OKAY, hat = "hat_tophat", neck = "neck_bell"),
                "Blume" to PetLook(Form.STACHLI, Mood.OKAY, hat = "hat_flower"),
                "Legende" to PetLook(Form.DRAKO, Mood.HAPPY, level = 30),
                "Ei" to PetLook(Form.EGG, Mood.OKAY, hatchTaps = 4),
            )
            for (row in variants.chunked(4)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    for ((label, look) in row) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            PetPortrait(look, state.style, Modifier.size(84.dp))
                            Text(label, style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }
        }
    }

    @Composable
    fun WidgetPreview(state: GameState) {
        val context = LocalContext.current
        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(16.dp),
        ) {
            Text("Widget-Vorschau", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(12.dp))
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(170.dp),
            ) {
                AndroidView(
                    factory = { FrameLayout(it) },
                    update = { frame ->
                        frame.removeAllViews()
                        val views = WidgetUpdater.build(context, state, small = false, message = null)
                        val v = views.apply(context, frame)
                        frame.addView(v, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
                    },
                    modifier = Modifier.fillMaxSize(),
                )
            }
            Spacer(Modifier.height(16.dp))
            Row {
                Box(Modifier.size(170.dp)) {
                    AndroidView(
                        factory = { FrameLayout(it) },
                        update = { frame ->
                            frame.removeAllViews()
                            val views = WidgetUpdater.build(context, state, small = true, message = "Mampf! 😋")
                            val v = views.apply(context, frame)
                            frame.addView(v, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
                        },
                        modifier = Modifier.fillMaxSize(),
                    )
                }
                Spacer(Modifier.width(12.dp))
                Text(
                    "So sieht Knuffi auf deinem Startbildschirm aus. Die Knöpfe füttern, spielen, putzen und bringen ins Bett.",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
}
