package de.knuffi.app.ui

import android.graphics.Typeface
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableFloatState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.core.content.res.ResourcesCompat
import de.knuffi.app.R
import de.knuffi.app.data.GameRepository
import de.knuffi.app.debug.DebugScenes
import de.knuffi.app.render.Furnishing
import de.knuffi.app.render.LayoutKind
import de.knuffi.app.render.ParticleSystem
import de.knuffi.app.render.PetDirector
import de.knuffi.app.render.PetLook
import de.knuffi.app.render.PetPose
import de.knuffi.app.render.PetRenderer
import de.knuffi.app.render.RoomRenderer
import de.knuffi.app.render.SceneModel
import de.knuffi.app.render.SceneRenderer
import de.knuffi.app.ui.theme.LocalPalette
import de.knuffi.core.Action
import de.knuffi.core.Engine
import de.knuffi.core.GameState
import de.knuffi.core.TouchZone
import de.knuffi.core.Wishes
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/** Speed of game time (the "Spieltempo" setting). 1 = normal. */
val LocalTimeScale = androidx.compose.runtime.staticCompositionLocalOf { 1f }

/** Seconds since this composable entered the composition, updated every frame. */
@Composable
fun rememberFrameTime(running: Boolean = true): MutableFloatState {
    val time = remember { mutableFloatStateOf(0f) }
    val scale = LocalTimeScale.current
    LaunchedEffect(running, scale) {
        if (!running) return@LaunchedEffect
        val offset = time.floatValue
        val start = withFrameNanos { it }
        while (isActive) {
            withFrameNanos { time.floatValue = offset + (it - start) / 1_000_000_000f * scale }
        }
    }
    return time
}

/** Fredoka for canvas text (speech bubbles, particles). */
@Composable
fun rememberCanvasTypeface(): Typeface {
    val context = LocalContext.current
    return remember {
        runCatching { ResourcesCompat.getFont(context, R.font.fredoka_semibold) }.getOrNull() ?: Typeface.DEFAULT_BOLD
    }
}

/** A pet on its own (no room), e.g. for cards, the shop and dialogs. */
@Composable
fun PetPortrait(
    look: PetLook?,
    modifier: Modifier = Modifier,
    animated: Boolean = true,
    sizeFactor: Float = 0.72f,
    groundFactor: Float = 0.93f,
    shadow: Boolean = true,
    silhouette: Int? = null,
    tweak: (PetPose, Float) -> Unit = { _, _ -> },
) {
    val renderer = remember { PetRenderer() }
    val pose = remember { PetPose() }
    val time = rememberFrameTime(animated)
    Canvas(modifier) {
        val l = look ?: return@Canvas
        val t = time.floatValue
        pose.defaults(l, t)
        // Gentle blinking even without a director.
        val blink = (t + 0.7f) % 3.7f
        if (blink < 0.14f && !l.sleeping) pose.eyeOpen = 1f - kotlin.math.sin(blink / 0.14f * Math.PI.toFloat())
        tweak(pose, t)
        drawIntoCanvas { c ->
            renderer.drawStandalone(c.nativeCanvas, size.width, size.height, l, pose, sizeFactor, groundFactor, shadow, silhouette)
        }
    }
}

/** Everything a living scene needs; hoisted so debug code can script the pet. */
class SceneHandle {
    val director = PetDirector()
    val guest = PetDirector(5).apply { place(0.7f); toyConfig.ladder = 0; toyConfig.planes = 0; toyConfig.paint = 0; toyConfig.footprints = 0 }
    val guest2 = PetDirector(11).apply { place(0.3f); toyConfig.ladder = 0; toyConfig.planes = 0; toyConfig.paint = 0; toyConfig.footprints = 0 }

    val friends = de.knuffi.app.render.FriendPlay()

    /** The two guests playing with each other. */
    val friends2 = de.knuffi.app.render.FriendPlay(seed = 33)

    /** Scene time of the last frame (for photos). */
    var lastT = 0f
    val particles = ParticleSystem()
    val renderer = SceneRenderer()
    val model = SceneModel()

    // ------------------------------------------------------------------ the house

    /** Ids of the pets in the two guest places, to notice when someone comes in. */
    val guestIds = arrayOfNulls<Long>(2)
    var shownRoom: de.knuffi.core.RoomKind? = null

    /** The door of the new room the pet came in through (visitors follow through it). */
    var cameIn: de.knuffi.core.Door? = null

    /** A piece of furniture being dragged and its offset, before it is saved. */
    var editPreview: Pair<de.knuffi.core.Slot, Float>? = null
    /** After letting go the preview stays until the saved offset arrives (0 while dragging). */
    var editPreviewUntil = 0L
    private var pendingHop: (() -> Unit)? = null
    private var pendingThen: (() -> Unit)? = null

    val travelling: Boolean get() = director.inDoor || pendingHop != null || pendingThen != null

    /**
     * Takes the pet through the doors into room [target], one room after the other, and runs
     * [then] when it arrived. Eggs and the classic room switch right away.
     */
    fun travel(target: de.knuffi.core.RoomKind, then: (() -> Unit)? = null) {
        val s = GameRepository.current
        if (!de.knuffi.core.House.enabled(s) || target == s.house.current) {
            then?.invoke()
            return
        }
        if (travelling) {
            // Already on the way: wait for the arrival instead of acting in the wrong room.
            s.pet?.let { GameRepository.message("${it.name} ist gerade unterwegs. 🚪") }
            return
        }
        val pet = s.pet
        if (pet != null && pet.alive && pet.sleeping && target != de.knuffi.core.RoomKind.BEDROOM) {
            GameRepository.message("${pet.name} schläft gerade. Psst! 😴")
            return
        }
        val path = de.knuffi.core.House.path(s.house.current, target, de.knuffi.core.House.unlocked(s))
        if (path.size < 2 || pet == null || !pet.alive || pet.isEgg) {
            GameRepository.perform(Action.GoToRoom(target))
            director.appear(0.5f)
            then?.invoke()
            return
        }
        hop(path, 0, then)
    }

    private fun hop(path: List<de.knuffi.core.RoomKind>, i: Int, then: (() -> Unit)?) {
        val from = path[i]
        val to = path[i + 1]
        val door = de.knuffi.core.House.doors(from, de.knuffi.core.House.unlocked(GameRepository.current)).firstOrNull { it.to == to }
        val ok = door != null && director.exitThrough(door) {
            GameRepository.perform(Action.GoToRoom(to))
            val back = de.knuffi.core.House.doors(to, de.knuffi.core.House.unlocked(GameRepository.current)).firstOrNull { it.to == from }
            cameIn = back
            if (back != null) director.enterFrom(back) else director.appear(0.5f)
            if (i + 2 < path.size) pendingHop = { hop(path, i + 1, then) } else pendingThen = then
        }
        if (!ok) {
            GameRepository.perform(Action.GoToRoom(path.last()))
            director.appear(0.5f)
            then?.invoke()
        }
    }

    /** Called every frame: starts the next door walk or the action waiting for the arrival. */
    fun pollTravel() {
        if (director.inDoor) return
        val hopNext = pendingHop
        if (hopNext != null) {
            pendingHop = null
            hopNext()
            return
        }
        val then = pendingThen
        if (then != null) {
            pendingThen = null
            then()
        }
    }
}

private fun currentHour(): Float {
    val now = LocalTime.now()
    return now.hour + now.minute / 60f
}

/**
 * The living room: room, weather, day/night, props, the pet with its behaviour, particles
 * and speech bubbles. Tap the pet to poke it, swipe over it to stroke, tap elsewhere and it
 * looks (and maybe walks) there.
 */
@Composable
fun PetScene(
    state: GameState,
    modifier: Modifier = Modifier,
    kind: LayoutKind = LayoutKind.HOME,
    interactive: Boolean = true,
    hourOverride: Float? = null,
    showBubble: Boolean = true,
    /** Height in px covered by UI at the top (e.g. the HUD). */
    topInsetPx: Int = 0,
    handle: SceneHandle = remember { SceneHandle() },
    onPetTap: () -> Unit = {},
    onStroke: () -> Unit = {},
    /** A tap on a part of the pet (head, ears, belly …). Without it a tap counts as [onPetTap]. */
    onTouch: ((TouchZone) -> Unit)? = null,
    /** A running game in the room, it gets the taps first. */
    roomGame: RoomGameSession? = null,
    /** Furniture can be moved by finger. */
    editMode: Boolean = false,
    /** A tap on something of the room: "piano", "cards", "kitchen", "tub", "beds", "pool". */
    onFixture: ((String) -> Unit)? = null,
) {
    val look = remember(state.pet, state.equipped) { PetLook.of(state) }
    val needs = remember(state.pet) { state.pet?.needs() ?: emptyList() }
    val furniture = remember(state.equipped, state.settings.look, state.house, state.furnitureOffsets, state.garden, state.lastSimulated / 120_000L) {
        Furnishing.of(state, DebugScenes.eventOverride ?: Engine.activeEvent(System.currentTimeMillis(), ZoneId.systemDefault()))
    }
    // the room in 3D (home screen only, if switched on)
    val spec3 = remember(furniture, state.house, state.room, state.settings.house3d, kind) {
        if (kind == LayoutKind.HOME && state.settings.house3d) de.knuffi.app.render.house3d.Specs3.home(state, furniture) else null
    }
    val editState by rememberUpdatedState(editMode)
    val fixtureCb by rememberUpdatedState(onFixture)
    val dark = LocalPalette.current.dark
    val typeface = rememberCanvasTypeface()
    val time = rememberFrameTime()
    val tap by rememberUpdatedState(onPetTap)
    val stroke by rememberUpdatedState(onStroke)
    val touchCb by rememberUpdatedState(onTouch)
    val session by rememberUpdatedState(roomGame)
    val context = LocalContext.current
    val lookState by rememberUpdatedState(look)
    val d = handle.director
    val ps = handle.particles
    val r = handle.renderer
    val m = handle.model
    val hour = remember { mutableFloatStateOf(currentHour()) }
    val weather = remember(state.weather, state.settings.weatherMode, hour.floatValue.toInt()) {
        RoomRenderer.fromCore(de.knuffi.core.Weather.current(state, System.currentTimeMillis(), ZoneId.systemDefault()).kind)
    }
    val hiddenItem = remember(state.daily.hiddenFound, state.daily.day) {
        if (state.daily.hiddenFound || state.pet?.isEgg != false) null else de.knuffi.core.Hidden.today(state.daily.day)
    }
    val taps = remember { HashMap<String, MutableList<Long>>() }
    val combo = remember { ArrayDeque<Pair<String, Long>>() }
    var residentHintShown by remember { androidx.compose.runtime.mutableStateOf(false) }
    fun tapped(key: String, n: Int, withinMs: Long): Boolean {
        val now = System.currentTimeMillis()
        val list = taps.getOrPut(key) { mutableListOf() }
        list += now
        list.removeAll { now - it > withinMs }
        if (list.size >= n) {
            list.clear()
            return true
        }
        return false
    }
    // Visitors first, then the pets from the Kuschelhaus who live in this room of the house.
    val roomGuests = remember(state.visit, state.visit2, state.resting, state.house, state.settings.houseMode) {
        val visitors = state.visitors
        val residents = de.knuffi.core.House.residentsIn(state, state.house.current).filter { r -> visitors.none { it.id == r.id } }
        (visitors + residents).take(2)
    }
    val visitorIds = remember(state.visit, state.visit2) { listOfNotNull(state.visit?.petId, state.visit2?.petId).toSet() }
    val guestLook = remember(roomGuests, state.equipped) {
        roomGuests.getOrNull(0)?.let { PetLook.of(it, state).copy(hat = null, face = null, neck = null, back = null, sleeping = false, mood = de.knuffi.core.Mood.HAPPY) }
    }
    val guestLook2 = remember(roomGuests, state.equipped) {
        roomGuests.getOrNull(1)?.let { PetLook.of(it, state).copy(hat = null, face = null, neck = null, back = null, sleeping = false, mood = de.knuffi.core.Mood.HAPPY) }
    }
    val wishText = remember(state.wish, state.pet?.form) { state.wish?.let { Wishes.text(it, state.pet?.isCat == true) } }
    var lastDropAt by remember { androidx.compose.runtime.mutableLongStateOf(0L) }

    LaunchedEffect(typeface) {
        r.setTypeface(typeface)
        ps.typeface = typeface
    }
    LaunchedEffect(Unit) {
        while (isActive) {
            delay(30_000)
            hour.floatValue = currentHour()
        }
    }
    LaunchedEffect(Unit) {
        GameRepository.events.collect { d.onEvent(it, ps, r.headNX, r.headNY) }
    }
    LaunchedEffect(interactive) {
        // Only the real, touchable scene hands out rewards for the screen toys.
        d.onToy = if (interactive && !GameRepository.previewMode) { kind -> GameRepository.perform(Action.Toy(kind)) } else null
        d.onSound = if (interactive) { sfx -> de.knuffi.app.sound.Sounds.play(sfx) } else null
        handle.friends.onMoment = if (interactive && !GameRepository.previewMode) { m -> GameRepository.perform(Action.FriendPlay(m, handle.guestIds[0] ?: 0L)) } else null
        handle.friends2.onMoment = if (interactive && !GameRepository.previewMode) { m ->
            handle.guestIds[1]?.let { GameRepository.perform(Action.FriendPlay(m, it)) }
        } else null
    }
    // The pet hears blowing, clapping and singing while the app is open (if allowed in the settings).
    val micOn = interactive && state.settings.micReactions && !GameRepository.previewMode && !de.knuffi.app.sound.VoiceState.listening
    LifecycleResumeEffect(micOn) {
        val mic = if (micOn) {
            de.knuffi.app.sound.MicListener(onSpeech = { de.knuffi.app.sound.VoiceState.wake?.invoke() }) { kind ->
                d.onVoice(kind, ps, r.headNX, r.headNY)
                GameRepository.perform(Action.Voice(kind))
            }.also { it.start(context) }
        } else null
        onPauseOrDispose { mic?.stop() }
    }
    LaunchedEffect(interactive) {
        if (!interactive) return@LaunchedEffect
        de.knuffi.app.sound.VoiceState.cues.collect { cue ->
            when (cue) {
                is de.knuffi.app.sound.VoiceCue.Listening -> d.listenUp(cue.text)
                is de.knuffi.app.sound.VoiceCue.Say -> d.say(cue.text, 2.2f)
            }
        }
    }
    DisposableEffect(Unit) {
        onDispose { r.release() }
    }

    fun hitPet(x: Float, y: Float): Boolean {
        val b = r.petBounds
        if (b.isEmpty || d.hidden) return false
        val pad = b.width() * 0.15f
        return x >= b.left - pad && x <= b.right + pad && y >= b.top - pad && y <= b.bottom + pad
    }

    /** Which part of the pet a tap hit. */
    fun zoneAt(x: Float, y: Float): TouchZone {
        val b = r.petBounds
        val rx = (x - b.centerX()) / b.width().coerceAtLeast(1f)
        val ry = ((y - b.top) / b.height().coerceAtLeast(1f)).coerceIn(0f, 1f)
        return when {
            ry < 0.3f -> if (kotlin.math.abs(rx) > 0.26f) TouchZone.EARS else TouchZone.HEAD
            ry < 0.58f -> if (kotlin.math.abs(rx) > 0.2f) TouchZone.CHEEKS else TouchZone.HEAD
            ry < 0.86f -> TouchZone.BELLY
            else -> TouchZone.FEET
        }
    }

    val input = if (interactive) {
        Modifier
            .pointerInput(Unit) {
                detectTapGestures { pos ->
                    val nx = pos.x / size.width
                    val ny = pos.y / size.height
                    // the release after carrying the pet is not a tap
                    if (System.currentTimeMillis() - lastDropAt < 400L) return@detectTapGestures
                    val game = session
                    if (game != null && !game.finished && game.tap(nx, ny, hitPet(pos.x, pos.y), d, ps, r.headNX, r.headNY)) return@detectTapGestures
                    if (d.tapToys(pos.x, pos.y, ps, size.width.toFloat(), size.height.toFloat())) return@detectTapGestures
                    if (editState) return@detectTapGestures
                    // visitors and residents from the Kuschelhaus say who they are
                    val gi = if (interactive) r.hitGuest(pos.x, pos.y) else 0
                    val who = if (gi > 0) roomGuests.getOrNull(gi - 1) else null
                    if (who != null && !hitPet(pos.x, pos.y)) {
                        val gd = if (gi == 1) handle.guest else handle.guest2
                        val cat = who.isCat
                        val text = when {
                            who.id in visitorIds -> if (cat) "Miau! Ich bin ${who.name} und bin zu Besuch! 😺" else "Hallo! Ich bin ${who.name} und bin zu Besuch! 👋"
                            else -> if (cat) "Miau! Ich bin ${who.name} und wohne hier im Haus. 🏠" else "Hallo! Ich bin ${who.name} und wohne hier im Haus. 🏠"
                        }
                        gd.greet(text)
                        if (who.id !in visitorIds && !residentHintShown) {
                            residentHintShown = true
                            GameRepository.message("${who.name} wohnt bei euch im Haus. Wer hier wohnt, legst du unter 🌍 Welt → „Dein Haus“ → „Wer wohnt im Haus?“ fest.")
                        }
                        val b = if (gi == 1) r.guestBounds else r.guest2Bounds
                        ps.burst(de.knuffi.app.render.PKind.HEART, b.centerX() / size.width, b.top / size.height, 3, 0.15f, 1.1f, 0.025f, de.knuffi.app.render.ParticleSystem.HEART_COLORS, upward = true)
                        return@detectTapGestures
                    }
                    if (interactive && r.hitHidden(pos.x, pos.y)) {
                        val h = de.knuffi.core.Hidden.today(GameRepository.current.daily.day)
                        ps.burst(de.knuffi.app.render.PKind.SPARKLE, nx, ny, 12, 0.3f, 1f, 0.025f, de.knuffi.app.render.ParticleSystem.SPARKLE_COLORS)
                        d.celebrate("Du hast ${h.name} gefunden! ${h.emoji}")
                        GameRepository.perform(Action.FindHidden)
                        return@detectTapGestures
                    }
                    // In the house: doors lead to the next room, some things of the room open a page.
                    if (de.knuffi.core.House.enabled(GameRepository.current) && !hitPet(pos.x, pos.y)) {
                        val door = r.hitDoor(pos.x, pos.y)
                        if (door != null) {
                            handle.travel(door.to)
                            return@detectTapGestures
                        }
                        val fixture = r.hitFixture(pos.x, pos.y)
                        val cb = fixtureCb
                        if (fixture != null && cb != null) {
                            cb(fixture)
                            return@detectTapGestures
                        }
                    }
                    if (interactive && hitPet(pos.x, pos.y) && d.onLadderTop) {
                        d.celebrate("Gib mir fünf! 🙌")
                        GameRepository.perform(Action.FindSecret(de.knuffi.core.Secret.HIGHFIVE))
                    }
                    if (interactive && !hitPet(pos.x, pos.y)) {
                        val hourNow = hourOverride ?: hour.floatValue
                        val dark = hourNow >= 20f || hourNow < 6f
                        val obj = r.hitObject(pos.x, pos.y, Furnishing.roomId(GameRepository.current))
                        if (obj != null) {
                            // easter egg: window, lamp, picture in a row unlocks kittens
                            val nowMs = System.currentTimeMillis()
                            combo.addLast(obj to nowMs)
                            while (combo.size > 3) combo.removeFirst()
                            if (combo.map { it.first } == de.knuffi.core.Secrets.catCombo && nowMs - combo.first().second < 6000L) {
                                combo.clear()
                                d.celebrate(if (state.catMode) "Miau! Du kennst unser Geheimzeichen! 😸" else "Miau? Da maunzt doch was … 🐈")
                                ps.burst(de.knuffi.app.render.PKind.HEART, nx, ny, 12, 0.4f, 1.2f, 0.025f, de.knuffi.app.render.ParticleSystem.HEART_COLORS)
                                GameRepository.perform(Action.FindSecret(de.knuffi.core.Secret.CATS))
                            }
                        }
                        when (obj) {
                            "picture" -> if (tapped("picture", 5, 4000L)) {
                                d.celebrate("Das Bild hat mir zugezwinkert! 🖼️✨")
                                ps.burst(de.knuffi.app.render.PKind.STAR, nx, ny, 10, 0.3f, 1f, 0.025f, de.knuffi.app.render.ParticleSystem.STAR_COLORS)
                                GameRepository.perform(Action.FindSecret(de.knuffi.core.Secret.PICTURE))
                            }
                            "window" -> if (dark) {
                                d.celebrate("Eine Sternschnuppe! Wünsch dir was! 🌠")
                                ps.burst(de.knuffi.app.render.PKind.STAR, nx, ny, 14, 0.5f, 1.2f, 0.02f, de.knuffi.app.render.ParticleSystem.STAR_COLORS, gravity = 0.2f)
                                GameRepository.perform(Action.FindSecret(de.knuffi.core.Secret.WISH))
                            }
                            "lamp" -> if (dark && tapped("lamp", 5, 4000L)) {
                                d.celebrate("Disco! 🪩🎶")
                                ps.burst(de.knuffi.app.render.PKind.CONFETTI, nx, ny, 40, 0.6f, 2f, 0.02f, de.knuffi.app.render.ParticleSystem.CONFETTI_COLORS, gravity = 0.4f)
                                GameRepository.perform(Action.FindSecret(de.knuffi.core.Secret.DISCO))
                            }
                        }
                    }
                    if (d.toys.splashes.isNotEmpty()) d.wipe(pos.x, pos.y, 0.34f, ps, size.width.toFloat(), size.height.toFloat())
                    if (hitPet(pos.x, pos.y)) {
                        if (lookState?.isEgg == true) {
                            d.onEggTap(ps, nx, ny)
                            tap()
                        } else {
                            d.onTap()
                            val cb = touchCb
                            if (cb == null) {
                                tap()
                            } else {
                                val zone = zoneAt(pos.x, pos.y)
                                if (d.onTouch(zone, ps, r.headNX, r.headNY)) GameRepository.perform(Action.FindSecret(de.knuffi.core.Secret.TICKLE))
                                cb(zone)
                            }
                        }
                    } else {
                        d.focus(nx, ny, r.centerNX, r.centerNY)
                        if (ny > 0.55f) d.pointAt(nx)
                    }
                }
            }
            .pointerInput(Unit) {
                var travelled = 0f
                var lastAngle = Float.NaN
                var spin = 0f
                detectDragGestures(onDragStart = {
                    travelled = 0f
                    lastAngle = Float.NaN
                    spin = 0f
                }) { change, drag ->
                    if (d.carried || editState) return@detectDragGestures
                    // circles around the pet make it dizzy (a secret)
                    val b = r.petBounds
                    if (interactive && !b.isEmpty) {
                        val a = kotlin.math.atan2(change.position.y - b.centerY(), change.position.x - b.centerX())
                        if (!lastAngle.isNaN()) {
                            var da = a - lastAngle
                            if (da > Math.PI) da -= (2 * Math.PI).toFloat()
                            if (da < -Math.PI) da += (2 * Math.PI).toFloat()
                            spin += da
                            if (kotlin.math.abs(spin) > 3f * 2f * Math.PI.toFloat()) {
                                spin = 0f
                                d.dizzy()
                                GameRepository.perform(Action.FindSecret(de.knuffi.core.Secret.DIZZY))
                            }
                        }
                        lastAngle = a
                    }
                    val nx = change.position.x / size.width
                    val ny = change.position.y / size.height
                    d.focus(nx, ny, r.centerNX, r.centerNY)
                    if (d.toys.splashes.isNotEmpty()) {
                        d.wipe(change.position.x, change.position.y, drag.getDistance() / (minOf(size.width, size.height) * 0.5f), ps, size.width.toFloat(), size.height.toFloat())
                    }
                    if (hitPet(change.position.x, change.position.y) && lookState?.isEgg != true) {
                        travelled += drag.getDistance()
                        if (travelled > 120f) {
                            travelled = 0f
                            stroke()
                        }
                    }
                }
            }
            .pointerInput(Unit) {
                // Hold the pet to pick it up, drag to carry it around and let go (or throw it).
                var carrying = false
                val tracker = VelocityTracker()
                detectDragGesturesAfterLongPress(
                    onDragStart = { pos ->
                        carrying = false
                        val game = session
                        if (!editState && (game == null || game.finished) && !handle.travelling && hitPet(pos.x, pos.y) && lookState?.isEgg != true && d.grab()) {
                            carrying = true
                            tracker.resetTracking()
                            GameRepository.perform(Action.Carry)
                        }
                    },
                    onDragEnd = {
                        if (carrying) {
                            val v = tracker.calculateVelocity()
                            val petPx = r.layout.petSize.coerceAtLeast(1f)
                            d.drop(v.x / size.width, -v.y / petPx)
                            lastDropAt = System.currentTimeMillis()
                        }
                        carrying = false
                    },
                    onDragCancel = {
                        if (carrying) d.drop(0f, 0f)
                        carrying = false
                    },
                ) { change, _ ->
                    if (!carrying) return@detectDragGesturesAfterLongPress
                    change.consume()
                    tracker.addPosition(change.uptimeMillis, change.position)
                    val L = r.layout
                    d.carryTo(change.position.x / size.width, (L.groundY - change.position.y) / L.petSize.coerceAtLeast(1f) - 0.55f)
                }
            }
    } else Modifier

    // Moving furniture: drag a piece sideways, it is saved when you let go.
    val editInput = if (interactive && editMode) {
        Modifier.pointerInput(Unit) {
            var slot: de.knuffi.core.Slot? = null
            var startOff = 0f
            var moved = 0f
            detectDragGestures(
                onDragStart = { pos ->
                    slot = r.hitFurniture(pos.x, pos.y)
                    startOff = slot?.let { GameRepository.current.furnitureOffsets[it] } ?: 0f
                    moved = 0f
                    m.editActive = slot
                    handle.editPreviewUntil = 0L
                },
                onDragEnd = {
                    val sl = slot
                    if (sl != null) GameRepository.perform(Action.MoveFurniture(sl, (startOff + moved).coerceIn(-de.knuffi.core.House.MAX_OFFSET, de.knuffi.core.House.MAX_OFFSET)))
                    handle.editPreviewUntil = System.currentTimeMillis() + 600
                    m.editActive = null
                    slot = null
                },
                onDragCancel = {
                    handle.editPreview = null
                    m.editActive = null
                    slot = null
                },
            ) { change, amount ->
                val sl = slot ?: return@detectDragGestures
                change.consume()
                moved += amount.x / size.width
                handle.editPreview = sl to (startOff + moved).coerceIn(-de.knuffi.core.House.MAX_OFFSET, de.knuffi.core.House.MAX_OFFSET)
            }
        }
    } else Modifier

    Canvas(modifier.then(input).then(editInput)) {
        val t = time.floatValue
        handle.lastT = t
        val w = size.width
        val h = size.height
        m.room = Furnishing.roomId(state)
        m.room3 = if (de.knuffi.app.render.house3d.Room3d.available) spec3 else null
        handle.editPreview?.let { (slot, off) ->
            val until = handle.editPreviewUntil
            if (until != 0L && (System.currentTimeMillis() > until || kotlin.math.abs((furniture.offsets[slot] ?: 0f) - off) < 0.002f)) handle.editPreview = null
        }
        val preview = handle.editPreview
        m.furniture = if (preview != null && editMode) furniture.copy(offsets = furniture.offsets + preview) else furniture
        m.editMode = editMode
        if (!editMode) m.editActive = null
        m.hour = hourOverride ?: hour.floatValue
        m.lightsOff = state.pet?.sleeping == true
        m.poops = state.pet?.poops ?: 0
        m.darkUi = dark
        m.weather = weather
        m.look = look
        m.needs = needs
        m.showBubble = showBubble
        m.safeTop = topInsetPx.toFloat()
        m.guest = guestLook
        m.guest2 = if (guestLook != null) guestLook2 else null
        m.guestName = if (guestLook != null) roomGuests.getOrNull(0)?.name else null
        m.guest2Name = if (guestLook != null && guestLook2 != null) roomGuests.getOrNull(1)?.name else null
        m.hidden = if (interactive) hiddenItem else null
        m.nameTag = if (kind == LayoutKind.WALLPAPER && state.settings.showNameTag) state.pet?.name else null
        d.toyConfig.from(state.settings)
        d.wishText = if (interactive) wishText else null
        r.furnishing = m.furniture
        r.configure(d, w, h, kind)
        // visitors use the same doors as the pet (drawn or 3D)
        handle.guest.doorPoints = d.doorPoints
        handle.guest2.doorPoints = d.doorPoints
        d.update(t, look, needs, ps, r.headNX, r.headNY)
        if (interactive) handle.pollTravel()
        // Guests: visitors follow the pet through the door, newcomers walk in through one.
        if (kind == LayoutKind.HOME && interactive) {
            val roomNow = if (de.knuffi.core.House.enabled(state)) state.house.current else null
            val ids = arrayOf(roomGuests.getOrNull(0)?.id, roomGuests.getOrNull(1)?.id)
            val directors = arrayOf(handle.guest, handle.guest2)
            if (roomNow != handle.shownRoom) {
                val first = handle.shownRoom == null
                handle.shownRoom = roomNow
                for (i in 0..1) {
                    val id = ids[i] ?: continue
                    if (first || roomNow == null) continue
                    val came = handle.cameIn
                    if (id in visitorIds && came != null) directors[i].enterFrom(came) else directors[i].appear(0.3f + 0.4f * i)
                }
                handle.guestIds[0] = ids[0]
                handle.guestIds[1] = ids[1]
                handle.cameIn = null
            } else {
                for (i in 0..1) {
                    val id = ids[i]
                    if (id != handle.guestIds[i]) {
                        val door = furniture.doors.randomOrNull()
                        if (id != null && roomNow != null && door != null) directors[i].enterFrom(door)
                        handle.guestIds[i] = id
                    }
                }
            }
        }
        val game = session
        if (game != null) game.update(t, d, ps, r.headNX, r.headNY, m) else m.hideSpots = emptyList()
        if (guestLook != null) {
            handle.guest.update(t + 3.7f, guestLook, emptyList(), ps, r.headNX, r.headNY)
            val pet = state.pet
            val g1 = roomGuests.getOrNull(0)
            val g2 = roomGuests.getOrNull(1)
            val level = if (pet != null && g1 != null) de.knuffi.core.Friends.level(state.friendship[de.knuffi.core.Friends.key(pet.id, g1.id)] ?: 0) else 0
            handle.friends.update(t, d, handle.guest, level, ps)
            if (guestLook2 != null) {
                handle.guest2.update(t + 7.1f, guestLook2, emptyList(), ps, r.headNX, r.headNY)
                val level2 = if (g1 != null && g2 != null) de.knuffi.core.Friends.level(state.friendship[de.knuffi.core.Friends.key(g1.id, g2.id)] ?: 0) else 0
                handle.friends2.update(t, handle.guest, handle.guest2, level2, ps)
            } else {
                handle.friends2.reset()
            }
        } else {
            handle.friends.reset()
            handle.friends2.reset()
        }
        ps.update(t)
        drawIntoCanvas { c -> r.draw(c.nativeCanvas, w, h, kind, m, d, ps, t, handle.guest, handle.friends, if (guestLook2 != null) handle.guest2 else null) }
    }
}
