package de.knuffi.app.debug

import android.app.Activity
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.graphics.Color
import de.knuffi.app.data.GameRepository
import de.knuffi.app.render.LayoutKind
import de.knuffi.app.screen.PetOverlayService
import de.knuffi.app.ui.PetScene
import de.knuffi.app.ui.theme.LocalPalette
import de.knuffi.core.GameEvent
import de.knuffi.core.ReactionKind
import de.knuffi.core.Settings
import de.knuffi.core.ThemeMode
import kotlinx.coroutines.delay
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
import de.knuffi.core.Catalog
import de.knuffi.core.Destination
import de.knuffi.core.EggLine
import de.knuffi.core.EventCalendar
import de.knuffi.core.Garden
import de.knuffi.core.LookStyle
import de.knuffi.core.PassState
import de.knuffi.core.Plot
import de.knuffi.core.SeasonEvent
import de.knuffi.core.Stickers
import de.knuffi.core.Trip
import de.knuffi.core.WeeklyState
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
import java.time.ZoneId

/**
 * Debug-only launch hooks used by CI to take screenshots of every screen in light and dark mode:
 * `adb shell am start -n de.knuffi.app.debug/de.knuffi.app.MainActivity --es debug_scene shop --es debug_theme dark`.
 * The seeded state is never saved.
 */
object DebugScenes {
    data class Launch(val scene: String, val state: GameState, val hour: Float?)

    /** Pretends a festival is running (room decorations in screenshots). */
    var eventOverride: SeasonEvent? = null

    fun fromIntent(intent: Intent?): Launch? {
        val scene = intent?.getStringExtra("debug_scene") ?: return null
        val theme = when (intent.getStringExtra("debug_theme")) {
            "dark" -> ThemeMode.DARK
            else -> ThemeMode.LIGHT
        }
        val form = intent.getStringExtra("debug_form")?.let { f -> Form.entries.firstOrNull { it.name.equals(f, true) } }
        val hour = intent.getStringExtra("debug_hour")?.toFloatOrNull() ?: when (scene) {
            "night" -> 23f
            "sunset" -> 19.2f
            else -> 11f
        }
        eventOverride = when (scene) {
            "halloween" -> SeasonEvent.HALLOWEEN
            "winter" -> SeasonEvent.WINTERZAUBER
            "easter" -> SeasonEvent.OSTERN
            "summerfest" -> SeasonEvent.SOMMERFEST
            else -> null
        }
        val look = if (intent.getStringExtra("debug_look") == "abenteuer" || scene.startsWith("adventure")) LookStyle.ABENTEUER else LookStyle.ZAUBER
        return Launch(scene, seed(theme, scene, form, look), hour)
    }

    fun initialRoute(scene: String?): Route = when (scene) {
        "catch" -> Route.Game(MiniGame.CATCH)
        "memory" -> Route.Game(MiniGame.MEMORY)
        "whack" -> Route.Game(MiniGame.WHACK)
        "runner" -> Route.Game(MiniGame.RUNNER)
        "bubbles" -> Route.Game(MiniGame.BUBBLES)
        "simon" -> Route.Game(MiniGame.SIMON)
        "settings" -> Route.Settings
        "gallery", "gallery1", "gallery2", "gallery3", "gallery4", "gallery5", "gallery6", "gallery7" -> Route.Gallery
        "widget" -> Route.WidgetPreview
        "wallpaper" -> Route.WallpaperPreview
        "walk" -> Route.Walk
        "kuschelhaus" -> Route.Kuschelhaus
        "album", "stickers" -> Route.Album
        "garden" -> Route.Garden
        "trips" -> Route.Trips
        "pass" -> Route.Pass
        "eventshop" -> Route.EventShop
        else -> Route.Main
    }

    fun initialTab(scene: String?): Tab = when (scene) {
        "games" -> Tab.GAMES
        "shop" -> Tab.SHOP
        "goals" -> Tab.GOALS
        "world" -> Tab.WORLD
        else -> Tab.HOME
    }

    /** Replays care animations over and over so that screenshots catch them. */
    @Composable
    fun Driver(launch: Launch) {
        val context = LocalContext.current
        LaunchedEffect(launch.scene) {
            val event = when (launch.scene) {
                "feed" -> GameEvent.Reaction(ReactionKind.EAT, "cake")
                "bath" -> GameEvent.Reaction(ReactionKind.CLEAN)
                "ball" -> GameEvent.Reaction(ReactionKind.PLAY)
                "heal" -> GameEvent.Reaction(ReactionKind.HEAL, "medicine")
                "love" -> GameEvent.Reaction(ReactionKind.PET)
                else -> null
            }
            if (event != null) {
                delay(2500)
                while (true) {
                    GameRepository.emit(event)
                    delay(if (event is GameEvent.Reaction && event.kind == ReactionKind.PET) 700 else 7000)
                }
            }
            if (launch.scene == "overlay") {
                delay(1500)
                PetOverlayService.start(context)
                delay(1500)
                (context as? Activity)?.moveTaskToBack(true)
            }
        }
    }

    @Composable
    fun WallpaperPreview(state: GameState) {
        Box(Modifier.fillMaxSize()) {
            PetScene(state, Modifier.fillMaxSize(), kind = LayoutKind.WALLPAPER)
            Column(
                Modifier
                    .align(Alignment.TopCenter)
                    .statusBarsPadding()
                    .padding(top = 40.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text("11:00", style = MaterialTheme.typography.displayLarge, color = Color.White)
                Text("Montag, 28. September", style = MaterialTheme.typography.titleMedium, color = Color.White)
            }
        }
    }

    private fun seed(theme: ThemeMode, scene: String, form: Form?, look: LookStyle): GameState {
        val settings = Settings(themeMode = theme, overlayPet = scene == "overlay", look = look, batteryHintSeen = true)
        if (scene == "onboarding") return GameState(settings = settings)
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
            "adventure", "halloween" -> Form.MAGMO
            "winter" -> Form.POLARFUCHS
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
            line = chosenForm.line,
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
        when (scene) {
            "furniture" -> {
                equipped[Slot.WALL] = "wall_stars"
                equipped[Slot.RUG] = "rug_rainbow"
                equipped[Slot.BED] = "bed_canopy"
                equipped[Slot.PLANT] = "plant_sunflower"
                equipped[Slot.LAMP] = "lamp_moon"
                equipped[Slot.PICTURE] = "pic_rocket"
            }
            "furniture2" -> {
                equipped[Slot.WALL] = "wall_wood"
                equipped[Slot.RUG] = "rug_leaf"
                equipped[Slot.BED] = "bed_race"
                equipped[Slot.PLANT] = "plant_cactus"
                equipped[Slot.LAMP] = "lamp_lava"
                equipped[Slot.PICTURE] = "pic_heart"
            }
            "furniture3" -> {
                equipped[Slot.WALL] = "wall_mint"
                equipped[Slot.RUG] = "rug_star"
                equipped[Slot.BED] = "bed_cloud"
                equipped[Slot.PLANT] = "plant_bonsai"
                equipped[Slot.LAMP] = "lamp_mushroom"
                equipped[Slot.PICTURE] = "pic_dragon"
            }
            "halloween" -> equipped[Slot.HAT] = "hat_witch"
            "winter" -> {
                equipped[Slot.HAT] = "hat_santa"
                equipped[Slot.NECK] = "neck_starscarf"
            }
            "adventure" -> {
                equipped[Slot.HAT] = "hat_explorer"
                equipped[Slot.NECK] = "neck_cape"
            }
        }
        val zone = ZoneId.systemDefault()
        val hour = 3_600_000L
        val date = EventCalendar.date(now, zone)
        fun other(f: Form, name: String, ago: Int, shiny: Boolean = false, level: Int = 12) = Pet(
            name = name, form = f, bornAt = now - ago * day, hatchedAt = now - ago * day, level = level, line = f.line, shiny = shiny,
        )
        val resting = listOf(
            other(Form.DELFINO, "Blubbi", 20),
            other(Form.PEGASUS, "Sternchen", 14),
            other(Form.REXI, "Rex", 9, shiny = true),
        )
        val album = buildSet {
            for (f in EggLine.KNUFFEL.forms + EggLine.MEER.forms + EggLine.FEUER.forms) add(f.name)
            for (f in EggLine.EINHORN.forms.take(5) + EggLine.URZEIT.forms.take(5) + EggLine.WALD.forms.take(3)) add(f.name)
            add("REXI*")
            add("LUMI*")
            add(Form.AURELIUS.name)
        }
        val stickers = Stickers.all.withIndex().filter { (i, _) -> i % 3 != 2 }.associate { (i, st) -> st.id to (1 + i % 3) }
        val garden = Garden(
            listOf(
                Plot(seed = "seed_carrot", plantedAt = now - 3 * hour, readyAt = now - 60_000L, waterings = 3),
                Plot(seed = "seed_strawberry", plantedAt = now - hour, readyAt = now + 3 * hour, waterings = 1, lastWatered = now - hour),
                Plot(),
                Plot(seed = "seed_pumpkin", plantedAt = now - 2 * hour, readyAt = now + 5 * hour + 12 * 60_000L),
            ),
        )
        val trips = listOf(
            Trip(resting[0].id, Destination.STRAND, now - 2 * hour - 5 * 60_000L, now - 5 * 60_000L),
            Trip(resting[1].id, Destination.ZAUBERWALD, now - hour, now + 2 * hour),
        )
        val weekly = WeeklyState(
            week = TimeUtil.week(now, zone),
            quests = listOf(
                QuestProgress(QuestType.FEED, 20, 14),
                QuestProgress(QuestType.HARVEST, 10, 10),
                QuestProgress(QuestType.GAMES, 12, 5),
            ),
        )
        return GameState(
            onboarded = true,
            settings = settings,
            resting = resting,
            restSlots = 4,
            eggs = mapOf(EggLine.FEUER to 1, EggLine.EINHORN to 2),
            album = album,
            stickers = stickers,
            garden = garden,
            trips = trips,
            weekly = weekly,
            pass = PassState(EventCalendar.passId(date), 1_900, (1..8).toSet()),
            difficulty = if (scene == "memorial") Difficulty.CLASSIC else Difficulty.RELAXED,
            pet = pet,
            coins = 1234,
            inventory = mapOf(
                "apple" to 3, "cake" to 1, "medicine" to 2, "candy" to 5, "cocoa" to 1, "bath" to 1,
                "seed_carrot" to 3, "seed_pumpkin" to 1, "glitter" to 1, "sticker_pack" to 1,
            ),
            owned = setOf("room_cozy", "room_forest", "room_ocean", "room_space", "room_candy", "hat_bow", "hat_cap", "hat_wizard", "face_round", "face_sun", "neck_scarf", "neck_bell") +
                Catalog.defaultFurniture + equipped.values,
            equipped = equipped,
            counters = Counters(
                feeds = 42, plays = 20, pets = 64, cleans = 9, gamesPlayed = 12, purchases = 7, bestCatch = 240,
                bestMemoryMoves = 14, bestWhack = 31, totalSteps = 45_678, bestDaySteps = 8_000, coinsEarned = 2_345,
                evolutions = 2, hatched = 4, maxLevel = 12, maxStage = 4, maxStreak = 4, quests = 11,
                daysPlayed = 23, harvests = 6, trips = 3, shinies = 1,
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
    fun Gallery(state: GameState, scene: String? = null) {
        val page = scene?.removePrefix("gallery")?.toIntOrNull() ?: 0
        if (page in 1..5) {
            GalleryPage("Formen, Seite $page", Form.entries.chunked(24).getOrElse(page - 1) { emptyList() }.map { it.title to PetLook(it, Mood.HAPPY, line = it.line) })
            return
        }
        if (page == 6) {
            val base = PetLook(Form.HOPSI, Mood.HAPPY)
            val hats = listOf("hat_explorer", "hat_tiara", "hat_pirate", "hat_flowercrown", "hat_straw", "hat_acorn", "hat_bobble", "hat_bunnyears", "hat_captain", "hat_witch", "hat_santa")
            val faces = listOf("face_snorkel", "face_stars")
            val necks = listOf("neck_cape", "neck_flowerchain", "neck_leafscarf", "neck_starscarf", "neck_heart")
            GalleryPage(
                "Neue Kleidung",
                hats.map { it.removePrefix("hat_") to base.copy(hat = it) } +
                    faces.map { it.removePrefix("face_") to base.copy(face = it) } +
                    necks.map { it.removePrefix("neck_") to base.copy(neck = it) },
            )
            return
        }
        if (page == 7) {
            GalleryPage(
                "Eier & Schillernde",
                EggLine.entries.map { it.title to PetLook(Form.EGG, Mood.HAPPY, line = it) } +
                    listOf(Form.LUMI, Form.DELFINO, Form.PYRO, Form.PEGASUS, Form.REXI, Form.GALAXIA, Form.ROBORITTER, Form.TITANIA, Form.KUERBISKOENIG, Form.POLARFUCHS, Form.KARAMELLA, Form.WELTENBAUM)
                        .map { "✨" + it.title to PetLook(it, Mood.HAPPY, line = it.line, shiny = true) },
            )
            return
        }
        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(12.dp),
        ) {
            Text("Alle Formen", style = MaterialTheme.typography.titleLarge, color = LocalPalette.current.text)
            for (row in Form.entries.chunked(4)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    for (f in row) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            PetPortrait(PetLook(f, Mood.HAPPY), Modifier.size(84.dp))
                            Text(f.title, style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            Text("Stimmungen & Extras", style = MaterialTheme.typography.titleLarge, color = LocalPalette.current.text)
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
                            PetPortrait(look, Modifier.size(84.dp))
                            Text(label, style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }
        }
    }

    @Composable
    private fun GalleryPage(title: String, entries: List<Pair<String, PetLook>>) {
        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(12.dp),
        ) {
            Text(title, style = MaterialTheme.typography.titleLarge, color = LocalPalette.current.text)
            for (row in entries.chunked(4)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    for ((label, look) in row) {
                        Column(Modifier.width(88.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            PetPortrait(look, Modifier.size(84.dp), animated = false)
                            Text(label, style = MaterialTheme.typography.labelSmall, color = LocalPalette.current.text, maxLines = 1)
                        }
                    }
                    repeat(4 - row.size) { Spacer(Modifier.width(88.dp)) }
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
            Text("Widget-Vorschau", style = MaterialTheme.typography.titleLarge, color = LocalPalette.current.text)
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
                    color = LocalPalette.current.text,
                )
            }
        }
    }
}
