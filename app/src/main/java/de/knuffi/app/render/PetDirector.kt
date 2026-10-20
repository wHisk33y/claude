package de.knuffi.app.render

import de.knuffi.core.Catalog
import de.knuffi.core.Door
import de.knuffi.core.Emotion
import de.knuffi.core.FriendMoment
import de.knuffi.core.GameEvent
import de.knuffi.core.Job
import de.knuffi.core.Mood
import de.knuffi.core.Need
import de.knuffi.core.ReactionKind
import de.knuffi.core.EggLine
import de.knuffi.core.ToyKind
import de.knuffi.core.ToyTuning
import de.knuffi.core.TouchZone
import de.knuffi.core.Trick
import de.knuffi.core.VoiceKind
import de.knuffi.core.Wishes
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sign
import kotlin.math.sin
import kotlin.random.Random

/** Animated props that belong to the pet's current activity. */
class Props {
    var food: String? = null
    var foodPop = 0f
    var bites = 0

    var tub = 0f
    var tubX = 0.45f
    var inTub = false

    var ball = false
    var bx = 0f
    var by = 0f
    var bvx = 0f
    var bvy = 0f
    var brot = 0f
    var ballAlpha = 1f

    var blanket = 0f

    var pill: String? = null
    var pillX = 0f
    var pillY = 0f

    /** Folding a paper plane: 0 = flat sheet, 1 = finished plane, -1 = nothing in the hands. */
    var folding = -1f
}

/**
 * The pet's "brain": chooses behaviours, reacts to events and turns everything into a [PetPose].
 * Horizontal positions are normalised to the scene width, heights are in units of the pet size.
 */
class PetDirector(seed: Int = 7) {
    val pose = PetPose()
    val props = Props()
    val toys = Toys()
    val toyConfig = ToyConfig()

    /** Called when the pet finishes a toy action (ladder climbed, plane caught, splash wiped). */
    var onToy: ((ToyKind) -> Unit)? = null

    /** Plays a sound effect (only set in the app, the wallpaper stays quiet). */
    var onSound: ((de.knuffi.app.sound.Sfx) -> Unit)? = null
    private val rnd = Random(seed)
    private var cat = false

    /** Real animals get brushed instead of bathed. */
    private var real = false

    /** The little sound of a small animal ("Piep!"), null for everyone else. */
    private var squeak: String? = null
    private var emotion: Emotion? = null
    private var lastStepSign = 0f
    private var dirty = false

    /** Paint from a friend on the fur, fades slowly. */
    var paintMarks = 0f

    /** Hiding during hide and seek (not drawn). */
    var hidden = false
        private set

    /** Free for doing something together with a friend. */
    val canDuo: Boolean
        get() = !act.care && act !is Act.Sleeping && act !is Act.GoSleep && act !is Act.Ladder && act !is Act.Duo &&
            act !is Act.PlanePlay && act !is Act.Paint && act !is Act.Wake && act !is Act.Perform && !inRoomGame &&
            act !is Act.Carried && act !is Act.Fall && act !is Act.Door

    /** Hanging from the player's finger. */
    var carried = false
        private set
    private var carryH = 0f
    private var fallV = 0f
    private var fallVX = 0f

    /** Text of the pet's current wish, said in a bubble now and then. */
    var wishText: String? = null
    private var nextWishBubble = 12f

    /** True while a room game (hide and seek, tag, fetch) is running. */
    val inRoomGame: Boolean get() = act is Act.Hiding || act is Act.Flee || act is Act.FetchBall

    /** Called when the pet catches the thrown ball; true if it was still in the air. */
    var onBallCatch: ((Boolean) -> Unit)? = null
    private var ballHeld = false
    private var ballHeldAt = 0f
    private var fleeSafeUntil = 0f
    private var lastClap = -10f
    private val tickles = ArrayDeque<Float>()

    // World configuration
    var minX = 0.24f
    var maxX = 0.7f
    var bowlX = 0.17f
    var bedX = 0.8f

    /** Depth of the bed (1: the back lane; in the 3D rooms wherever it stands). */
    var bedZ = 1f

    /** How high the pet lies in bed (in pet sizes). */
    var bedLift = 0.26f

    /** Where doors are for walking through them (the drawn rooms or the 3D rooms). */
    var doorPoints: DoorPoints = DoorGeo
    var hasBowl = true
    var hasBed = true
    var hasTub = true
    var hasBall = true

    /** 1 = fully visible; lower while walking through a door. */
    var fade = 1f
        private set
    private var onThroughDoor: (() -> Unit)? = null

    /** True while the pet walks through a door. */
    val inDoor: Boolean get() = act is Act.Door

    // Position & motion
    var x = 0.46f
        private set
    var yOffset = 0f
        private set

    /** Depth: 0 = front of the room, 1 = back near the wall (bowl & bed). */
    var z = 0f
        private set
    private var targetZ = 0f
    private var targetX = 0.46f
    private var speed = 0.13f
    private var facing = 1f
    private var turn = 0f
    private var walking = false
    private var walkPhase = 0f
    private var vx = 0f

    // Springs
    private var sq = 0f
    private var sqV = 0f
    private var ear = 0f
    private var earV = 0f

    // Jump
    private var jumpStart = -10f
    private var jumpDur = 0.5f
    private var jumpH = 0.35f
    private var landed = true

    // Behaviour
    private var act: Act = Act.Idle(1.5f)
    private var actStart = 0f
    private val queue = ArrayDeque<Act>()
    private var lastT = -1f
    private var now = 0f
    private val strokes = ArrayDeque<Float>()
    private var eggTapAt = -10f
    private var ambientAt = 0f

    // Face
    private var nextBlink = 2f
    private var blinkAt = -10f
    private var lookX = 0f
    private var lookY = 0f
    private var lookTX = 0f
    private var lookTY = 0f
    private var nextSaccade = 1f
    private var focusUntil = -10f

    // Speech bubble
    var bubble: String? = null
        private set
    var bubbleAlpha = 0f
        private set
    private var bubbleText: String? = null
    private var bubbleUntil = 0f
    private var nextNeedBubble = 5f
    private var needIndex = 0

    val isSleepingInBed: Boolean get() = act is Act.Sleeping
    val showsShadow: Boolean get() = !props.inTub && pose.lying < 0.5f

    sealed class Act(val dur: Float, val care: Boolean = false) {
        class Idle(d: Float) : Act(d)
        class Walk(val target: Float, val speedMul: Float = 1f, val depth: Float = 0f) : Act(12f)
        class Look(d: Float) : Act(d)
        class Hop : Act(0.85f)
        class Dance : Act(2.8f)
        class Spin : Act(1.1f)
        class Wave : Act(1.5f)
        class Yawn : Act(1.8f)
        class Shiver : Act(1.4f)
        class Sneeze : Act(1.3f)
        class Rumble : Act(1.4f)
        class Sad(d: Float) : Act(d)
        class Eat(val emoji: String) : Act(2.8f, true)
        class Bath : Act(4.2f, true)
        class Ball : Act(4.6f, true)
        class GoSleep : Act(1.2f, true)
        class Sleeping : Act(1e9f, true)
        class Wake : Act(1.9f, true)
        class Heal(val emoji: String) : Act(2f, true)
        class Love : Act(1.3f)
        class Giggle : Act(1.5f)
        class Refuse : Act(0.9f)
        class Cheer : Act(1.9f)
        class Surprised : Act(0.9f)

        // Screen toys
        class Ladder(val jumpDown: Boolean) : Act(8.6f)
        class PlanePlay : Act(4.6f)
        class Fetch : Act(1.3f)
        class Paint(val flicks: Int) : Act(3.4f)

        // Jobs, chat and cats
        class Work(val tool: String) : Act(3.8f, true)
        class Talk : Act(1.3f)
        class Groom(val brush: Boolean) : Act(if (brush) 3.6f else 3f, brush)
        class Stretch : Act(2.2f)
        class Duo(val kind: FriendMoment, val lead: Boolean, val spot: Float, val partnerSpot: Float, d: Float) : Act(d)

        // Tricks, touching, carrying, room games and voice
        class Perform(val trick: Trick, val success: Boolean) : Act(if (trick == Trick.TANZ) 3.4f else if (success) 2.4f else 2.1f)
        class EarWiggle : Act(1.4f)
        class Bashful : Act(1.4f)
        class Tickle : Act(1.6f)
        class FootHop : Act(1.1f)
        class Carried : Act(1e9f)
        class Fall : Act(6f)
        class Hiding : Act(1e9f)
        class Flee : Act(1e9f)
        class FetchBall : Act(1e9f)
        class Windy : Act(2.2f)
        class Sing : Act(3.4f)

        // Little hobbies, so the home screen never gets boring
        class Bubbles : Act(3.8f)
        class Music(val instrument: String) : Act(4f)
        class Read : Act(4.8f)
        class Snack(val emoji: String) : Act(2.8f)
        class Flip : Act(1.3f)
        class Butterfly : Act(5.5f)
        class Daydream : Act(3.6f)

        // The house: out of one room through a door, into the next one
        class Door(val door: de.knuffi.core.Door, val exit: Boolean) : Act(if (exit) 10f else 6f) {
            var done = false
        }
    }

    /** Something small flying around the pet (a butterfly), in scene coordinates 0..1. */
    var companion: String? = null
        private set
    var companionX = 0f
        private set
    var companionY = 0f
        private set

    /** True while the pet waves from the top of the ladder. */
    val onLadderTop: Boolean get() = act is Act.Ladder && (now - actStart) in 3.2f..5.6f

    /** A happy reaction with a speech bubble (secrets, found things). */
    /** Says hello when tapped (visitors and residents). */
    fun greet(text: String) {
        if (act is Act.Sleeping || act.care || act is Act.Ladder || act is Act.Duo) {
            say(text, 2.4f)
            return
        }
        start(Act.Wave())
        say(text, 2.6f)
    }

    fun celebrate(text: String) {
        if (act !is Act.Sleeping && !act.care) start(Act.Cheer())
        say(text, 2.2f)
    }

    /** Spun around too often. */
    fun dizzy() {
        if (act is Act.Sleeping || act.care) return
        start(Act.Spin())
        say(line("Mir ist ganz schwindelig! 😵‍💫", "Miau … alles dreht sich!"), 2f)
    }

    // ------------------------------------------------------------------ house

    /**
     * Walks to [door] and through it; [onThrough] is called once the pet is gone.
     * Returns false if the pet cannot go right now (asleep, carried, in a game).
     */
    fun exitThrough(door: Door, onThrough: () -> Unit): Boolean {
        if (act is Act.Sleeping || act is Act.GoSleep || act is Act.Wake || carried || inRoomGame || act.care) return false
        hidden = false
        onThroughDoor = onThrough
        start(Act.Door(door, true))
        return true
    }

    /** Comes into the room through [door] (the door of this room leading back where it came from). */
    fun enterFrom(door: Door) {
        onThroughDoor = null
        act = Act.Idle(0.1f)
        x = doorPoints.outX(door)
        z = doorPoints.outZ(door)
        targetX = x
        targetZ = z
        fade = 0f
        facing = if (doorPoints.inX(door) >= x) 1f else -1f
        start(Act.Door(door, false))
    }

    /** Puts the pet somewhere in the room without walking (e.g. after the room changed by itself). */
    fun appear(nx: Float) {
        place(nx)
        fade = 1f
    }

    /** Starts a moment together with a friend (see [FriendPlay]). */
    fun startDuo(kind: FriendMoment, lead: Boolean, spot: Float, partnerSpot: Float, dur: Float) {
        if (!canDuo) return
        start(Act.Duo(kind, lead, spot, partnerSpot, dur))
    }

    /** A cat says things a bit differently, small animals squeak now and then. */
    private fun line(normal: String, catLine: String): String {
        if (cat) return catLine
        val sq = squeak
        return if (sq != null && rnd.nextFloat() < 0.45f) "$sq $normal" else normal
    }

    // ------------------------------------------------------------------ tricks, touching, carrying

    /** Shows a trick (or a clumsy try while it is still being learned). */
    fun perform(trick: Trick, success: Boolean) {
        if (act is Act.Sleeping || act is Act.GoSleep || carried || inRoomGame) return
        start(Act.Perform(trick, success))
    }

    /**
     * The pet was touched somewhere. Returns true when it was tickled a lot in a short time.
     */
    fun onTouch(zone: TouchZone, ps: ParticleSystem, headX: Float, headY: Float): Boolean {
        if (act is Act.Sleeping) {
            ps.add(Particle(PKind.HEART, headX, headY, 0f, -0.06f, 1.2f, 0.028f, 0xFFFF7FAF.toInt()))
            return false
        }
        if (act.care || carried || inRoomGame || act is Act.GoSleep || act is Act.Wake) {
            sqV += 4f
            return false
        }
        var lots = false
        when (zone) {
            TouchZone.HEAD -> {
                start(Act.Love())
                say(line(listOf("Kopf kraulen ist schön!", "Mehr, bitte! 🥰", "Hach …").random(rnd), "Prrrrr … 😻"), 1.4f)
                ps.burst(PKind.HEART, headX, headY, 3, 0.16f, 1.2f, 0.028f, ParticleSystem.HEART_COLORS, upward = true)
            }
            TouchZone.EARS -> {
                start(Act.EarWiggle())
                say(line("Hihi, meine Ohren!", "Mrrp! Meine Ohren!"), 1.3f)
            }
            TouchZone.CHEEKS -> {
                start(Act.Bashful())
                say(line(listOf("Bussi! 😚", "Hihi, das kitzelt!").random(rnd), "Miau! 😽"), 1.3f)
            }
            TouchZone.BELLY -> {
                tickles.addLast(now)
                while (tickles.isNotEmpty() && now - tickles.first() > 4f) tickles.removeFirst()
                lots = tickles.size >= 6
                if (lots) tickles.clear()
                start(Act.Tickle())
                say(
                    if (lots) line("Hahaha, hör auf! 😂", "Mrrrhahaha! 😹")
                    else line(listOf("Hihi!", "Kitzelig!", "Hahaha!").random(rnd), "Prrrhihi!"),
                    1.2f,
                )
                ps.burst(PKind.SPARKLE, headX, headY + 0.08f, 4, 0.2f, 0.8f, 0.02f, ParticleSystem.SPARKLE_COLORS)
            }
            TouchZone.FEET -> {
                start(Act.FootHop())
                say(line("Meine Füße! 🦶", "Miau! Meine Pfoten!"), 1.2f)
            }
        }
        return lots
    }

    /** Picks the pet up. Returns false if that is not possible right now. */
    fun grab(): Boolean {
        if (act is Act.Sleeping || act is Act.GoSleep || act is Act.Wake || act is Act.Door || act.care || inRoomGame) return false
        start(Act.Carried())
        carried = true
        carryH = 0.3f
        say(line(listOf("Huch! Hochgehoben!", "Wiii, ich fliege!").random(rnd), "Miau?! Runter!"), 1.4f)
        return true
    }

    /** Moves the carried pet: [nx] across the room, [height] in pet sizes above the floor. */
    fun carryTo(nx: Float, height: Float) {
        if (!carried) return
        val nxc = nx.coerceIn(0.06f, 0.94f)
        if (abs(nxc - x) > 0.002f) facing = sign(nxc - x)
        x = nxc
        targetX = nxc
        carryH = height.coerceIn(0f, 3.2f)
    }

    /** Lets go of the pet; it falls down (and may be thrown with [vx], [vy]). */
    fun drop(vx: Float, vy: Float) {
        if (!carried) return
        carried = false
        fallVX = vx.coerceIn(-1.6f, 1.6f)
        fallV = vy.coerceIn(-2f, 5f)
        start(Act.Fall())
    }

    // ------------------------------------------------------------------ room games

    /** Hide and seek: the pet disappears (it is hiding somewhere). */
    fun hide(ps: ParticleSystem, headX: Float, headY: Float) {
        if (act is Act.Sleeping) return
        ps.burst(PKind.SMOKE, x, headY + 0.1f, 8, 0.25f, 0.8f, 0.04f, intArrayOf(0x88FFFFFF.toInt()), gravity = -0.1f)
        start(Act.Hiding())
        hidden = true
    }

    /** The pet pops out of its hiding place at [nx]. */
    fun reveal(nx: Float, ps: ParticleSystem, ny: Float) {
        hidden = false
        x = nx.coerceIn(minX, maxX)
        targetX = x
        z = 0f
        start(Act.Cheer())
        say(line(listOf("Buh! Gefunden! 😆", "Hihi, da bin ich!").random(rnd), "Miau! Erwischt!"), 1.6f)
        ps.burst(PKind.SPARKLE, nx, ny, 12, 0.3f, 1f, 0.02f, ParticleSystem.SPARKLE_COLORS)
    }

    /** Tag: the pet runs around the room. */
    fun startFlee() {
        if (act is Act.Sleeping) return
        start(Act.Flee())
        say(line("Fang mich doch! 😝", "Miau! Fang mich!"), 1.6f)
    }

    /** A tap on the fleeing pet. Returns true if it counts as caught. */
    fun caught(ps: ParticleSystem, headX: Float, headY: Float): Boolean {
        if (act !is Act.Flee || now < fleeSafeUntil) return false
        fleeSafeUntil = now + 0.8f
        sqV += 7f
        jump(0.3f, 0.35f)
        say(line(listOf("Hihi, erwischt!", "Nochmal!", "Du bist schnell!").random(rnd), "Mau! Erwischt!"), 1f)
        ps.burst(PKind.STAR, headX, headY, 6, 0.25f, 0.8f, 0.022f, ParticleSystem.STAR_COLORS, upward = true)
        // run off in the other direction
        targetX = if (x < (minX + maxX) / 2f) maxX - rnd.nextFloat() * 0.1f else minX + rnd.nextFloat() * 0.1f
        return true
    }

    /** Fetch: the pet waits for the ball. */
    fun startFetch() {
        if (act is Act.Sleeping) return
        props.ball = false
        ballHeld = false
        start(Act.FetchBall())
        say(line("Wirf den Ball! 🎾", "Miau! Wirf das Knäuel!"), 1.6f)
    }

    /** Throws the ball towards [targetNX]. Returns false while the pet still has the last one. */
    fun throwBall(targetNX: Float): Boolean {
        if (act !is Act.FetchBall || (props.ball && !ballHeld)) return false
        ballHeld = false
        props.ball = true
        props.ballAlpha = 1f
        props.bx = 0.5f
        props.by = 0.4f
        props.bvx = (targetNX.coerceIn(0.05f, 0.95f) - 0.5f) * 0.95f
        props.bvy = 3.3f + rnd.nextFloat() * 0.5f
        return true
    }

    /** Ends a running room game. */
    fun stopRoomGame() {
        hidden = false
        props.ball = false
        ballHeld = false
        if (inRoomGame) start(Act.Cheer())
    }

    // ------------------------------------------------------------------ voice

    /** Reacts to what the microphone heard. */
    fun onVoice(kind: VoiceKind, ps: ParticleSystem, headX: Float, headY: Float) {
        if (carried || inRoomGame || act.care || act is Act.GoSleep || act is Act.Wake) return
        if (act is Act.Sleeping) {
            if (kind == VoiceKind.CLAP || kind == VoiceKind.LOUD) say("Mmh … *schnarch* … 💤", 1.4f)
            return
        }
        when (kind) {
            VoiceKind.BLOW -> {
                start(Act.Windy())
                say(line("Huiii, Wind! 🌬️", "Pfff! Miau!"), 1.4f)
            }
            VoiceKind.CLAP -> {
                val twice = now - lastClap < 1.4f
                lastClap = now
                if (twice) {
                    start(Act.Dance())
                    say(line("Klatsch, klatsch! Tanzen! 💃", "Miau! Tanz mit!"), 1.6f)
                } else {
                    start(Act.Surprised())
                    queue.addLast(Act.Hop())
                }
            }
            VoiceKind.SING -> {
                start(Act.Sing())
                say(line(listOf("La la laaa! 🎶", "Ich sing mit! 🎵").random(rnd), "Miauuu~ 🎶"), 1.6f)
            }
            VoiceKind.LOUD -> {
                start(Act.Shiver())
                say(line("Huch, so laut! 🙉", "Mau?! So laut!"), 1.4f)
            }
        }
    }

    // ------------------------------------------------------------------ input

    fun say(text: String, seconds: Float = 2.4f) {
        bubbleText = text
        bubbleUntil = now + seconds
    }

    /** Puts the pet somewhere else (e.g. a visitor starts at the other side of the room). */
    fun place(nx: Float) {
        x = nx
        targetX = nx
    }

    fun focus(nx: Float, ny: Float, petCenterX: Float, petCenterY: Float) {
        lookTX = ((nx - petCenterX) * 5f).coerceIn(-1f, 1f)
        lookTY = ((ny - petCenterY) * 5f).coerceIn(-1f, 1f)
        focusUntil = now + 2.5f
    }

    /** Tap on empty space: look there and maybe walk over. */
    fun pointAt(nx: Float) {
        if (act.care || act is Act.Sleeping) return
        if (act is Act.Idle || act is Act.Look || act is Act.Walk) {
            start(Act.Walk(nx.coerceIn(minX, maxX), 1.3f))
        }
    }

    fun onTap() {
        sqV += 5f
    }

    /** A tap that may catch the flying paper plane. Returns true if it was caught. */
    fun tapToys(px: Float, py: Float, ps: ParticleSystem, w: Float, h: Float): Boolean {
        if (!toys.hitPlane(px, py)) return false
        val p = toys.plane ?: return true
        ps.burst(PKind.STAR, p.sx / w, p.sy / h, 8, 0.35f, 0.9f, 0.025f, ParticleSystem.STAR_COLORS, gravity = 0.4f, upward = true)
        if (!act.care && act !is Act.Sleeping) {
            start(Act.Cheer())
            say(line("Gefangen! Super!", "Miau! Gefangen!"), 1.6f)
        }
        onToy?.invoke(ToyKind.PLANE)
        return true
    }

    /** Finger moved over the screen: wipe paint splashes. */
    fun wipe(px: Float, py: Float, strength: Float, ps: ParticleSystem, w: Float, h: Float) {
        val before = toys.splashes.filter { it.amount > 0f }
        val done = toys.wipe(px, py, strength)
        if (done > 0) {
            for (s in before) if (s.amount <= 0f) ps.burst(PKind.SPARKLE, s.sx / w, s.sy / h, 8, 0.25f, 0.9f, 0.02f, ParticleSystem.SPARKLE_COLORS)
            if (!act.care && act !is Act.Sleeping && rnd.nextFloat() < 0.5f) say(line("Blitzblank!", "Prrr, sauber!"), 1.4f)
            repeat(done) { onToy?.invoke(ToyKind.PAINT) }
        }
    }

    fun onEggTap(ps: ParticleSystem, px: Float, py: Float) {
        eggTapAt = now
        ps.burst(PKind.CRUMB, px, py, 6, 0.3f, 0.7f, 0.012f, intArrayOf(0xFFFFF4E0.toInt(), 0xFFF0D2A8.toInt(), 0xFFFF8FB8.toInt()), gravity = 1f, upward = true)
    }

    fun onEvent(e: GameEvent, ps: ParticleSystem, headX: Float, headY: Float) {
        when (e) {
            is GameEvent.Reaction -> when (e.kind) {
                ReactionKind.EAT -> {
                    val emoji = e.itemId?.let { Catalog[it]?.emoji } ?: "🍙"
                    if (hasBowl) {
                        props.food = emoji
                        props.foodPop = 0f
                        props.bites = 0
                        val dir = if (x > bowlX) 1f else -1f
                        startCare(Act.Walk(bowlX + dir * 0.13f, 1.5f, 1f), Act.Eat(emoji))
                    } else {
                        startCare(Act.Eat(emoji))
                    }
                }
                ReactionKind.CLEAN -> startCare(if (real) Act.Groom(brush = true) else Act.Bath())
                ReactionKind.PLAY -> startCare(if (hasBall) Act.Ball() else Act.Cheer())
                ReactionKind.GAME, ReactionKind.BUY -> if (!act.care) start(Act.Cheer())
                ReactionKind.HEAL, ReactionKind.DRINK -> startCare(Act.Heal(e.itemId?.let { Catalog[it]?.emoji } ?: "💊"))
                ReactionKind.PET -> {
                    if (act is Act.Sleeping) {
                        ps.add(Particle(PKind.HEART, headX, headY, 0f, -0.06f, 1.2f, 0.028f, 0xFFFF7FAF.toInt()))
                    } else {
                        strokes.addLast(now)
                        while (strokes.isNotEmpty() && now - strokes.first() > 2.5f) strokes.removeFirst()
                        if (!act.care) start(if (strokes.size >= 3) Act.Giggle() else Act.Love())
                        ps.burst(PKind.HEART, headX, headY, 2, 0.16f, 1.2f, 0.028f, ParticleSystem.HEART_COLORS, upward = true)
                    }
                }
                ReactionKind.REFUSE -> if (!act.care) start(Act.Refuse())
                ReactionKind.SLEEP -> say(line("Gute Nacht …", "Mrrr … Gute Nacht …"), 2.5f)
                ReactionKind.WAKE -> Unit
                ReactionKind.WORK -> {
                    val tool = e.itemId?.let { id -> Job.entries.firstOrNull { it.name == id }?.tool } ?: "🔨"
                    startCare(Act.Work(tool))
                }
                ReactionKind.TALK -> if (!act.care && act !is Act.Sleeping && act !is Act.Ladder) start(Act.Talk())
            }
            GameEvent.Hatched -> {
                start(Act.Cheer())
                queue.addLast(Act.Wave())
                ps.burst(PKind.CONFETTI, headX, headY, 36, 0.6f, 2f, 0.022f, ParticleSystem.CONFETTI_COLORS, gravity = 0.5f, upward = true)
                ps.burst(PKind.SPARKLE, headX, headY, 12, 0.4f, 1.3f, 0.025f, ParticleSystem.SPARKLE_COLORS)
            }
            is GameEvent.LevelUp -> {
                if (!act.care) start(Act.Cheer())
                ps.burst(PKind.STAR, headX, headY, 10, 0.45f, 1.4f, 0.028f, ParticleSystem.STAR_COLORS, gravity = 0.3f, upward = true)
            }
            is GameEvent.Evolved -> {
                start(Act.Surprised())
                queue.addLast(Act.Cheer())
            }
            is GameEvent.FeelingChanged -> ps.add(Particle(PKind.EMOJI, headX + 0.07f, headY - 0.02f, 0f, -0.04f, 1.6f, 0.05f, 0, text = e.emotion.emoji))
            is GameEvent.PrizeWon, is GameEvent.Spun -> {
                if (!act.care && act !is Act.Sleeping) start(Act.Cheer())
                ps.burst(PKind.CONFETTI, headX, headY, 28, 0.55f, 1.8f, 0.022f, ParticleSystem.CONFETTI_COLORS, gravity = 0.5f, upward = true)
            }
            is GameEvent.TalentUp, is GameEvent.JobUp -> {
                if (!act.care && act !is Act.Sleeping) start(Act.Cheer())
                ps.burst(PKind.STAR, headX, headY, 10, 0.45f, 1.4f, 0.028f, ParticleSystem.STAR_COLORS, gravity = 0.3f, upward = true)
            }
            is GameEvent.CoinsGained -> ps.burst(PKind.COIN, headX, headY, min(8, 2 + e.amount / 10), 0.3f, 1f, 0.03f, intArrayOf(0xFFFFC83D.toInt()), gravity = 0.7f, upward = true)
            is GameEvent.TrickPracticed -> {
                perform(e.trick, e.success)
                if (e.learned) ps.burst(PKind.CONFETTI, headX, headY, 30, 0.55f, 1.8f, 0.022f, ParticleSystem.CONFETTI_COLORS, gravity = 0.5f, upward = true)
            }
            is GameEvent.TrickShown -> perform(e.trick, true)
            is GameEvent.WishMade -> if (act !is Act.Sleeping) say(Wishes.text(e.wish, cat), 3.2f)
            is GameEvent.WishFulfilled -> {
                if (!act.care && act !is Act.Sleeping && !inRoomGame) start(Act.Cheer())
                say(line("Juhu! Danke, danke! 💖", "Prrrr! Danke! 💖"), 2f)
                ps.burst(PKind.HEART, headX, headY, 8, 0.35f, 1.4f, 0.03f, ParticleSystem.HEART_COLORS, upward = true)
            }
            is GameEvent.TournamentDone -> if (!act.care && act !is Act.Sleeping) start(if (e.place == 1) Act.Cheer() else Act.Wave())
            else -> Unit
        }
    }

    /** Puts away what a toy left in the pet's hands when something else interrupts it. */
    private fun dropToys() {
        toys.ladderExtend = 0f
        props.folding = -1f
    }

    private fun start(a: Act) {
        // While held in the air the pet only reacts with its face, it keeps hanging on.
        if (carried && a !is Act.Carried && a !is Act.Fall) return
        // Walking through a door is not interrupted.
        val cur = act
        if (cur is Act.Door && !cur.done && a !is Act.Door) return
        queue.clear()
        if (a !is Act.Ladder) dropToys()
        companion = null
        act = a
        actStart = now
        onActStart(a)
    }

    private fun startCare(vararg acts: Act) {
        if (act is Act.Sleeping || act is Act.GoSleep) return
        // care (feeding, bathing …) puts the pet back on the floor
        if (carried) {
            carried = false
            carryH = 0f
        }
        props.inTub = false
        props.ball = false
        dropToys()
        queue.clear()
        act = acts.first()
        actStart = now
        onActStart(act)
        for (i in 1 until acts.size) queue.addLast(acts[i])
    }

    private fun next(look: PetLook) {
        val a = if (queue.isNotEmpty()) queue.removeFirst() else chooseIdle(look)
        act = a
        actStart = now
        onActStart(a)
    }

    private fun onActStart(a: Act) {
        when (a) {
            is Act.Walk -> {
                targetX = a.target
                targetZ = a.depth
                speed = 0.13f * a.speedMul
            }
            is Act.Hop -> jump(0.32f, 0.5f)
            is Act.Spin -> {
                jump(0.25f, 0.45f)
                say(line("Wiii!", "Mrrrp!"), 1.2f)
            }
            is Act.Wave -> {
                say(if (cat) listOf("Miau!", "Mrrp?", "Miiiau!").random(rnd) else listOf(squeak ?: "Hallo!", "Hallo!", "Hey du!", "Hihi!").random(rnd), 1.6f)
                onSound?.invoke(if (cat) de.knuffi.app.sound.Sfx.MEOW else de.knuffi.app.sound.Sfx.CHIRP)
            }
            is Act.Yawn -> say(line("Gääähn …", "Mrrraaau …"), 1.6f)
            is Act.Rumble -> say(line("*knurr*", "Miau?! *Bauch knurrt*"), 1.5f)
            is Act.Sneeze -> Unit
            is Act.Eat -> say(if (cat) listOf("Mampf … prrr", "Miau, lecker!", "Nom nom, prrr!").random(rnd) else listOf("Mampf!", "Lecker!", "Nom nom!").random(rnd), 1.8f)
            is Act.Bath -> {
                props.tubX = x.coerceIn(minX, maxX)
                targetX = props.tubX
                x = props.tubX
                say("Blubb blubb!", 2.2f)
            }
            is Act.Ball -> {
                props.ball = true
                props.ballAlpha = 1f
                props.bx = if (x < 0.5f) maxX + 0.12f else minX - 0.12f
                props.by = 1.4f
                props.bvx = if (x < 0.5f) -0.35f else 0.35f
                props.bvy = 0f
                say(line("Juhu, ein Ball!", "Ein Wollknäuel! Miau!"), 1.8f)
            }
            is Act.Heal -> {
                props.pill = a.emoji
                props.pillX = x + 0.35f
                props.pillY = 1.6f
            }
            is Act.Cheer -> jump(0.4f, 0.5f)
            is Act.Surprised -> {
                jump(0.18f, 0.35f)
                say(line("Oh!", "Mau?!"), 1.2f)
            }
            is Act.Refuse -> say(line("Nö!", "Pfft!"), 1.1f)
            is Act.Giggle -> say(line("Hihi!", "Prrrrr!"), 1.2f)
            is Act.Wake -> say(line("Guten Morgen!", "Mrrp? Morgen!"), 2f)
            is Act.Ladder -> {
                toys.ladderX = x
                facing = if (x < 0.5f) 1f else -1f
            }
            is Act.PlanePlay -> {
                props.folding = 0f
                say(line("Ich bastel was!", "Papier! Rascheln!"), 1.6f)
            }
            is Act.Paint -> say(line("Ich male ein Bild!", "Miau! Pfotenkunst!"), 1.6f)
            is Act.Work -> say(line("An die Arbeit!", "Miau, an die Arbeit!"), 1.6f)
            is Act.Groom -> if (a.brush) say("Bürsten? Prrrrr …", 1.8f)
            is Act.Stretch -> Unit
            is Act.Bubbles -> say(line("Seifenblasen! 🫧", "Miau! Blasen fangen!"), 1.6f)
            is Act.Music -> say(listOf("Ein Lied für dich! 🎶", "Hörst du? 🎵", "La la laaa!").random(rnd), 1.6f)
            is Act.Read -> say(listOf("Ich lese ein bisschen.", "Psst, es wird spannend!", "Ein Buch über Drachen!").random(rnd), 1.8f)
            is Act.Snack -> say(line("Ein kleiner Snack zwischendurch!", "Miau! Leckerli!"), 1.5f)
            is Act.Flip -> {
                jump(0.5f, 0.85f)
                say(line("Purzelbaum!", "Mrrrp-hopp!"), 1.1f)
            }
            is Act.Butterfly -> {
                companion = "🦋"
                companionX = (x + facing * 0.25f).coerceIn(0.1f, 0.9f)
                companionY = 0.45f
                say(line("Oh, ein Schmetterling!", "Miau?! Ein Schmetterling!"), 1.6f)
            }
            is Act.Daydream -> Unit
            is Act.Door -> {
                if (a.exit) {
                    targetX = doorPoints.outX(a.door)
                    targetZ = doorPoints.outZ(a.door)
                } else {
                    targetX = doorPoints.inX(a.door)
                    targetZ = doorPoints.inZ(a.door)
                }
                speed = 0.3f
            }
            is Act.FootHop -> jump(0.28f, 0.36f)
            is Act.Fall -> if (fallV > 1f) say(line("Huiiii!", "Miauuu!"), 1f)
            else -> Unit
        }
    }

    /** A random little hobby. */
    private fun hobby(): Act {
        val pool = if (cat) {
            listOf<() -> Act>({ Act.Bubbles() }, { Act.Snack(listOf("🐟", "🍗", "🧀").random(rnd)) }, { Act.Flip() }, { Act.Butterfly() }, { Act.Butterfly() }, { Act.Daydream() })
        } else if (squeak != null) {
            listOf<() -> Act>({ Act.Snack(listOf("🥕", "🥒", "🌾", "🍎", "🌻").random(rnd)) }, { Act.Snack("🥕") }, { Act.Flip() }, { Act.Butterfly() }, { Act.Daydream() }, { Act.Hop() })
        } else {
            listOf<() -> Act>(
                { Act.Bubbles() },
                { Act.Music(listOf("🎸", "🎺", "🥁", "🎻", "🪇").random(rnd)) },
                { Act.Read() },
                { Act.Snack(listOf("🍪", "🍓", "🥨", "🍬", "🍩").random(rnd)) },
                { Act.Flip() },
                { Act.Butterfly() },
                { Act.Daydream() },
            )
        }
        return pool[rnd.nextInt(pool.size)]()
    }

    private fun chooseIdle(look: PetLook): Act {
        val r = rnd.nextFloat()
        fun wander(mul: Float = 1f): Act {
            var tx = minX + rnd.nextFloat() * (maxX - minX)
            if (abs(tx - x) < 0.1f) tx = if (x < (minX + maxX) / 2f) maxX - 0.05f else minX + 0.05f
            return Act.Walk(tx, mul)
        }
        debugHobby?.let { h ->
            return when (h) {
                "bubbles" -> Act.Bubbles()
                "music" -> Act.Music("🎸")
                "read" -> Act.Read()
                "snack" -> Act.Snack("🍪")
                "flip" -> Act.Flip()
                "butterfly" -> Act.Butterfly()
                else -> Act.Daydream()
            }
        }
        chooseToy(look)?.let { return it }
        if (cat && !look.sick && !look.tired && r < 0.12f) return if (rnd.nextBoolean()) Act.Groom(brush = false) else Act.Stretch()
        when (look.emotion) {
            Emotion.EINSAM -> if (r < 0.35f) {
                say(line("Wo bist du denn?", "Miau? … Miiiau?"), 2.4f)
                return Act.Sad(2.5f)
            }
            Emotion.VERSCHMUST -> if (r < 0.2f) return Act.Love()
            Emotion.GRUMMELIG -> if (r < 0.15f) return Act.Refuse()
            Emotion.NEUGIERIG -> if (r < 0.25f) return Act.Look(2.4f)
            Emotion.VERSPIELT -> if (r < 0.2f) return if (rnd.nextBoolean()) Act.Dance() else Act.Spin()
            else -> Unit
        }
        if (!look.sick && !look.tired && look.mood != Mood.SAD && rnd.nextFloat() < 0.22f) return hobby()
        if (toys.hasMess() && r > 0.94f) say(line("Upps … das war ich nicht! 🎨", "Miau? Ich war das nicht!"), 2f)
        return when {
            look.sick -> when {
                r < 0.25f -> Act.Sneeze()
                r < 0.5f -> Act.Shiver()
                else -> Act.Idle(2.5f)
            }
            look.mood == Mood.SAD -> if (r < 0.55f) Act.Sad(3f) else wander(0.5f)
            look.hungry && r < 0.35f -> Act.Rumble()
            look.tired -> when {
                r < 0.4f -> Act.Yawn()
                r < 0.7f -> wander(0.6f)
                else -> Act.Idle(2.5f)
            }
            look.mood == Mood.HAPPY -> when {
                r < 0.3f -> wander(1.1f)
                r < 0.45f -> Act.Dance()
                r < 0.58f -> Act.Hop()
                r < 0.66f -> Act.Spin()
                r < 0.8f -> Act.Look(2f)
                r < 0.86f -> Act.Wave()
                else -> Act.Idle(1.8f)
            }
            else -> when {
                r < 0.42f -> wander()
                r < 0.65f -> Act.Look(2.2f)
                r < 0.75f -> Act.Hop()
                r < 0.8f -> Act.Yawn()
                else -> Act.Idle(2.2f)
            }
        }
    }

    /** Maybe start one of the screen toys, depending on the sliders and the pet's feeling. */
    private fun chooseToy(look: PetLook): Act? {
        debugToy?.let { kind ->
            return when (kind) {
                ToyKind.LADDER -> {
                    queue.addLast(Act.Ladder(jumpDown = rnd.nextBoolean()))
                    Act.Walk(if (x < 0.5f) minX + 0.02f else maxX - 0.02f, 1.1f)
                }
                ToyKind.PLANE -> if (toys.plane == null) Act.PlanePlay() else Act.Idle(1f)
                ToyKind.PAINT -> Act.Paint(3)
                ToyKind.FOOTPRINTS -> Act.Walk(if (x < 0.5f) maxX else minX, 1f)
            }
        }
        val p = toys.plane
        if (p != null && p.landed && !p.caught && !look.sick) {
            queue.addLast(Act.Fetch())
            return Act.Walk(p.x.coerceIn(minX, maxX), 1.2f)
        }
        if (look.sick || look.tired || look.mood == Mood.SAD || look.sleeping) return null
        val e = look.emotion
        val playful = e == Emotion.VERSPIELT || e == Emotion.AUFGEREGT || e == Emotion.FROEHLICH
        val ladder = ToyTuning.chance(toyConfig.ladder) * (if (e == Emotion.NEUGIERIG || e == Emotion.STOLZ) 1.8f else if (playful) 1.3f else 1f)
        val plane = ToyTuning.chance(toyConfig.planes) * (if (playful) 1.6f else 1f)
        val paint = ToyTuning.chance(toyConfig.paint) * (if (e == Emotion.GRUMMELIG || e == Emotion.GELANGWEILT) 1.8f else 1f)
        var r = rnd.nextFloat()
        if (r < ladder) {
            val tx = if (x < 0.5f) minX + 0.02f else maxX - 0.02f
            queue.addLast(Act.Ladder(jumpDown = rnd.nextFloat() < 0.45f))
            return Act.Walk(tx, 1.1f)
        }
        r -= ladder
        if (r < plane && toys.plane == null) return Act.PlanePlay()
        r -= plane
        if (r < paint && toys.splashes.size < ToyTuning.maxSplashes(toyConfig.paint)) return Act.Paint(1 + rnd.nextInt(3))
        return null
    }

    private fun jump(height: Float, dur: Float) {
        jumpStart = now
        jumpH = height
        jumpDur = dur
        landed = false
    }

    // ------------------------------------------------------------------ update

    fun update(t: Float, look: PetLook?, needs: List<Need>, ps: ParticleSystem, headX: Float, headY: Float) {
        val dt = if (lastT < 0f) 0.016f else (t - lastT).coerceIn(0f, 0.1f)
        lastT = t
        now = t
        pose.reset()
        pose.t = t
        toys.update(t, dt, toyConfig)
        paintMarks = (paintMarks - dt / 90f).coerceAtLeast(0f)
        pose.paint = paintMarks
        if (act !is Act.Duo && act !is Act.Hiding) hidden = false
        if (look == null) return
        cat = look.isCat
        real = look.form.isReal
        squeak = when (look.form.line) {
            EggLine.HASE -> "*mümmel*"
            EggLine.HAMSTER -> "Piep!"
            EggLine.MEERI -> "Quiek!"
            else -> null
        }
        emotion = look.emotion
        dirty = look.dirty
        pose.breath = sin(t * 2f * PI.toFloat() / 3.2f)

        if (look.isEgg) {
            updateEgg(t)
            return
        }

        // Sleep is driven by the game state.
        if (look.sleeping) {
            if (carried) {
                carried = false
                carryH = 0f
            }
            if (act !is Act.Sleeping && act !is Act.GoSleep && !(act is Act.Walk && queue.firstOrNull() is Act.GoSleep)) {
                hidden = false
                props.inTub = false
                props.ball = false
                props.tub = 0f
                props.folding = -1f
                toys.ladderExtend = 0f
                if (hasBed) {
                    start(Act.Walk(bedX, 1.2f, 1f))
                    queue.addLast(Act.GoSleep())
                } else {
                    start(Act.GoSleep())
                }
            }
        } else if (act is Act.Sleeping || act is Act.GoSleep) {
            start(Act.Wake())
        }

        val elapsed = t - actStart
        val a = act
        val finished = when (a) {
            is Act.Walk -> abs(targetX - x) < 0.006f && abs(targetZ - z) < 0.02f
            is Act.Fall -> (carryH <= 0f && fallV == 0f && elapsed > 0.35f) || elapsed >= a.dur
            is Act.Door -> a.done || elapsed >= a.dur
            else -> elapsed >= a.dur
        }
        if (finished) {
            if (a is Act.GoSleep) {
                act = Act.Sleeping()
                actStart = t
            } else {
                next(look)
            }
        }

        pose.applyMood(look)
        if (!look.sleeping) {
            pose.lying = 0f
            pose.nightCap = 0f
        }

        walking = false
        runAct(act, t - actStart, dt, look, ps, headX, headY)
        if (act !is Act.Door && fade < 1f) fade = min(1f, fade + dt * 3f)

        // Movement (x and depth)
        if (walking) {
            val dx = targetX - x
            val step = min(abs(dx), speed * dt)
            x += sign(dx) * step
            val dz = targetZ - z
            z += sign(dz) * min(abs(dz), 1.6f * dt)
            vx = sign(dx) * speed
            if (abs(dx) > 0.002f) facing = sign(dx)
            walkPhase += dt * (6f + speed * 30f)
            val s = sin(walkPhase)
            val sg = if (s >= 0f) 1f else -1f
            if (sg != lastStepSign && yOffset < 0.05f) {
                lastStepSign = sg
                toys.addFootprint(x + sg * 0.022f, z, sg.toInt(), t, facing, cat, dirty, toyConfig)
            }
            pose.footL = max(0f, s) * 0.9f
            pose.footR = max(0f, -s) * 0.9f
            pose.strideL = kotlin.math.cos(walkPhase) * 0.8f * facing
            pose.strideR = -pose.strideL
            pose.lift += abs(s) * 0.035f
            pose.tilt += s * 3f
            pose.armWaveL += s * 16f
            pose.armWaveR -= s * 16f
        } else {
            vx *= 0.8f
        }

        // Turn towards the walking direction (pseudo 3D).
        val turnTarget = when {
            walking -> facing * 0.75f
            pose.turn != 0f -> pose.turn
            else -> facing * 0.25f
        }
        turn += (turnTarget - turn) * min(1f, dt * 8f)
        pose.turn = turn

        // Jump arc
        val jt = t - jumpStart
        if (jt >= 0f && jt < 0.14f) {
            val k = sin(jt / 0.14f * PI.toFloat())
            pose.scaleY -= 0.16f * k
            pose.scaleX += 0.12f * k
        } else if (jt >= 0.14f && jt < 0.14f + jumpDur) {
            val p = (jt - 0.14f) / jumpDur
            pose.lift += 4f * jumpH * p * (1f - p)
            val stretch = 1f - abs(2f * p - 1f)
            pose.scaleY += 0.1f * stretch
            pose.scaleX -= 0.07f * stretch
            pose.armL = max(pose.armL, 0.5f * stretch)
            pose.armR = max(pose.armR, 0.5f * stretch)
        } else if (!landed && jt >= 0.14f + jumpDur) {
            landed = true
            sqV += 7f
        }

        // Jelly spring (squash & stretch) and secondary motion of ears/antennas
        sqV += (-190f * sq - 9f * sqV) * dt
        sq += sqV * dt
        val earTarget = -vx * 70f - pose.lift * 25f
        earV += (120f * (earTarget - ear) - 8f * earV) * dt
        ear += earV * dt
        pose.earLag = ear
        pose.scaleY *= 1f + 0.025f * pose.breath - 0.2f * sq
        pose.scaleX *= 1f - 0.012f * pose.breath + 0.16f * sq

        // Blinking
        if (t > nextBlink) {
            blinkAt = t
            nextBlink = t + if (rnd.nextFloat() < 0.15f) 0.32f else 2.2f + rnd.nextFloat() * 3f
        }
        val bt = t - blinkAt
        if (bt < 0.16f) pose.eyeOpen *= 1f - sin(bt / 0.16f * PI.toFloat())

        // Where the eyes look
        if (t > focusUntil) {
            if (t > nextSaccade) {
                nextSaccade = t + 1.2f + rnd.nextFloat() * 2.2f
                lookTX = (rnd.nextFloat() - 0.5f) * 1.4f + facing * 0.3f
                lookTY = (rnd.nextFloat() - 0.5f) * 0.8f
            }
        }
        lookX += (lookTX - lookX) * min(1f, dt * 10f)
        lookY += (lookTY - lookY) * min(1f, dt * 10f)
        if (pose.lookX == 0f) pose.lookX = lookX
        if (pose.lookY == 0f) pose.lookY = lookY

        updateBubble(t, dt, needs, look)
        ambient(t, look, ps, headX, headY)
    }

    private fun updateEgg(t: Float) {
        val since = t - eggTapAt
        pose.eggWobble = sin(t * 1.3f) * 2.5f
        if (since < 0.6f) pose.eggWobble += sin(since * 40f) * 14f * (1f - since / 0.6f)
        val hop = (t % 3.2f)
        if (hop < 0.3f) pose.eggWobble += sin(hop / 0.3f * PI.toFloat() * 2f) * 6f
        x = 0.5f
        bubble = null
        bubbleAlpha = 0f
    }

    private fun runAct(a: Act, el: Float, dt: Float, look: PetLook, ps: ParticleSystem, headX: Float, headY: Float) {
        yOffset = 0f
        when (a) {
            is Act.Idle -> pose.tilt += sin(now * 0.9f) * 2f
            is Act.Walk -> walking = true
            is Act.Look -> {
                pose.turn = sin(el * 2.4f) * 0.9f
                lookTX = pose.turn
                focusUntil = now + 0.1f
            }
            is Act.Hop -> {
                pose.eyes = EyeShape.HAPPY
                pose.mouth = MouthShape.OPEN
            }
            is Act.Dance -> {
                val k = el * 7f
                pose.tilt += sin(k) * 10f
                pose.lift += abs(sin(k)) * 0.08f
                pose.armL = 0.75f + 0.25f * sin(k)
                pose.armR = 0.75f - 0.25f * sin(k)
                pose.turn = sin(k * 0.5f) * 0.6f
                pose.eyes = EyeShape.HAPPY
                pose.mouth = MouthShape.OPEN
                pose.blush = 0.85f
                if ((el % 0.5f) < dt) ps.add(Particle(PKind.NOTE, headX + (rnd.nextFloat() - 0.5f) * 0.2f, headY, (rnd.nextFloat() - 0.5f) * 0.05f, -0.09f, 1.4f, 0.04f, listOf(0xFF7C6CFF, 0xFFFF6B9A, 0xFF2EC4A0).random(rnd).toInt()))
            }
            is Act.Spin -> {
                pose.turn = sin(el * 16f)
                pose.eyes = EyeShape.HAPPY
                pose.mouth = MouthShape.OPEN
                pose.armL = 0.6f
                pose.armR = 0.6f
            }
            is Act.Wave -> {
                pose.armR = 0.95f
                pose.armWaveR = sin(el * 14f) * 26f
                pose.eyes = EyeShape.HAPPY
                pose.mouth = MouthShape.OPEN
                pose.turn = 0.3f
            }
            is Act.Yawn -> {
                val p = (el / a.dur).coerceIn(0f, 1f)
                val k = sin(p * PI.toFloat())
                pose.armL = k
                pose.armR = k
                pose.scaleY += 0.08f * k
                if (p in 0.15f..0.85f) {
                    pose.mouth = MouthShape.YAWN
                    pose.eyes = EyeShape.CLOSED
                }
            }
            is Act.Shiver -> {
                pose.tilt += sin(el * 42f) * 3f
                pose.scaleX += sin(el * 50f) * 0.02f
                pose.sweat = 1f
                pose.eyes = EyeShape.SQUINT
                pose.brows = -1f
            }
            is Act.Sneeze -> {
                if (el < 0.65f) {
                    val k = el / 0.65f
                    pose.tilt -= 7f * k
                    pose.scaleY += 0.06f * k
                    pose.eyes = EyeShape.SQUINT
                    pose.mouth = MouthShape.O
                    pose.mouthOpen = k
                } else {
                    if (el - dt < 0.65f) {
                        sqV += 9f
                        say("Hatschi!", 1f)
                        ps.burst(PKind.DROP, headX + facing * 0.06f, headY + 0.05f, 6, 0.3f, 0.6f, 0.014f, intArrayOf(0xFFBFE8FF.toInt()), gravity = 1f)
                    }
                    pose.eyes = EyeShape.CLOSED
                    pose.tilt += 6f * (1f - ((el - 0.65f) / 0.65f).coerceIn(0f, 1f))
                }
            }
            is Act.Rumble -> {
                pose.scaleX += sin(el * 48f) * 0.02f
                pose.lookY = 1f
                pose.lookX = 0.01f
                pose.mouth = MouthShape.WAVY
                pose.brows = -1f
            }
            is Act.Sad -> {
                pose.tilt += sin(el * 0.8f) * 3f
                pose.lookY = 0.7f
                pose.tear = 1f
            }
            is Act.Eat -> runEat(a, el, ps, headX, headY)
            is Act.Bath -> runBath(el, dt, ps, headX, headY)
            is Act.Ball -> runBall(el, dt, ps)
            is Act.GoSleep -> {
                val p = (el / a.dur).coerceIn(0f, 1f)
                if (hasBed) {
                    x += (bedX - x) * min(1f, dt * 6f)
                    z += (bedZ - z) * min(1f, dt * 6f)
                    yOffset = bedLift * easeOut(p)
                }
                pose.lying = easeOut(p)
                props.blanket = if (hasBed) ((p - 0.4f) / 0.6f).coerceIn(0f, 1f) else 0f
                pose.nightCap = p
                pose.eyes = if (p > 0.5f) EyeShape.CLOSED else EyeShape.SLEEPY
            }
            is Act.Sleeping -> {
                if (hasBed) {
                    x = bedX
                    z = bedZ
                    yOffset = bedLift
                }
                props.blanket = if (hasBed) 1f else 0f
                pose.lying = 1f
                pose.eyes = EyeShape.CLOSED
                pose.mouth = MouthShape.O
                pose.mouthOpen = 0.25f + 0.15f * sin(now * 1.5f)
                pose.breath = sin(now * 2f * PI.toFloat() / 4.2f) * 1.8f
                pose.turn = 0.15f
            }
            is Act.Wake -> {
                val p = (el / a.dur).coerceIn(0f, 1f)
                props.blanket = (1f - p / 0.3f).coerceIn(0f, 1f) * if (hasBed) 1f else 0f
                val out = ((p - 0.25f) / 0.3f).coerceIn(0f, 1f)
                pose.lying = 1f - easeOut(out)
                if (hasBed) {
                    yOffset = bedLift * (1f - out)
                    z = bedZ * (1f - out)
                    if (out > 0f) x += (0.55f - x) * min(1f, dt * 3f)
                }
                if (p > 0.55f) {
                    val k = sin(((p - 0.55f) / 0.45f) * PI.toFloat())
                    pose.armL = k
                    pose.armR = k
                    pose.scaleY += 0.09f * k
                    pose.mouth = MouthShape.YAWN
                    pose.eyes = EyeShape.CLOSED
                } else {
                    pose.eyes = EyeShape.SLEEPY
                }
            }
            is Act.Heal -> {
                val p = (el / 0.9f).coerceIn(0f, 1f)
                if (props.pill != null) {
                    props.pillX = (x + 0.35f) + (x - (x + 0.35f)) * p
                    props.pillY = 1.6f * (1f - p) + 0.55f + sin(p * PI.toFloat()) * 0.6f
                    pose.mouth = MouthShape.O
                    pose.mouthOpen = 0.8f
                    pose.lookX = 1f
                    pose.lookY = -0.5f
                }
                if (el >= 0.9f && props.pill != null) {
                    props.pill = null
                    sqV += 7f
                    ps.burst(PKind.SPARKLE, headX, headY + 0.03f, 10, 0.3f, 1.1f, 0.022f, intArrayOf(0xFF7DD9B0.toInt(), 0xFFFFFFFF.toInt(), 0xFFFFE680.toInt()), upward = true)
                    say("Schon besser!", 1.6f)
                }
                if (el > 0.9f) {
                    pose.eyes = EyeShape.HAPPY
                    pose.mouth = MouthShape.OPEN
                }
            }
            is Act.Love -> {
                if (el < dt * 1.5f) sqV += 6f
                pose.eyes = EyeShape.HEART
                pose.blush = 1f
                pose.mouth = MouthShape.OPEN
                pose.tilt += sin(el * 10f) * 5f
            }
            is Act.Giggle -> {
                pose.eyes = EyeShape.HAPPY
                pose.mouth = MouthShape.GRIN
                pose.blush = 1f
                pose.tilt += sin(el * 22f) * 7f
                pose.lift += abs(sin(el * 11f)) * 0.04f
            }
            is Act.Refuse -> {
                pose.turn = sin(el * 22f) * 0.8f
                pose.mouth = MouthShape.FLAT
                pose.brows = 1f
                if (el < dt * 1.5f) ps.add(Particle(PKind.EMOJI, headX + 0.08f, headY, 0f, -0.05f, 1f, 0.05f, 0xFF000000.toInt(), "💢"))
            }
            is Act.Cheer -> {
                pose.eyes = EyeShape.STAR
                pose.mouth = MouthShape.OPEN
                pose.armL = 1f
                pose.armR = 1f
                pose.blush = 0.9f
                if (el > 0.85f && el - dt <= 0.85f) jump(0.35f, 0.5f)
            }
            is Act.Surprised -> {
                pose.eyes = EyeShape.WIDE
                pose.mouth = MouthShape.O
                pose.mouthOpen = 1f
            }
            is Act.Ladder -> runLadder(a, el, dt, ps, headX, headY)
            is Act.Bubbles -> {
                pose.holding = "🫧"
                pose.hold = 1f
                pose.mouth = MouthShape.O
                pose.mouthOpen = 0.6f
                pose.blush = 0.8f
                pose.lookX = facing * 0.6f
                pose.lookY = -0.5f
                if ((el % 0.28f) < dt && el < a.dur - 0.6f) {
                    ps.add(Particle(PKind.BUBBLE, headX + facing * 0.07f, headY + 0.03f, facing * (0.03f + rnd.nextFloat() * 0.05f), -0.04f - rnd.nextFloat() * 0.05f, 2.2f, 0.018f + rnd.nextFloat() * 0.018f, 0xFFFFFFFF.toInt(), drag = 0.3f))
                }
                if (el > a.dur - 0.6f) pose.eyes = EyeShape.HAPPY
            }
            is Act.Music -> {
                pose.holding = a.instrument
                pose.hold = 1f
                val k = el * 6f
                pose.tilt += sin(k) * 7f
                pose.lift += abs(sin(k)) * 0.03f
                pose.eyes = if ((el % 2f) < 1.3f) EyeShape.CLOSED else EyeShape.HAPPY
                pose.mouth = MouthShape.SMILE
                pose.footL = max(0f, sin(k)) * 0.5f
                if ((el % 0.4f) < dt) ps.add(Particle(PKind.NOTE, headX + (rnd.nextFloat() - 0.3f) * 0.18f, headY, (rnd.nextFloat() - 0.5f) * 0.05f, -0.08f, 1.5f, 0.035f, listOf(0xFF7C6CFF, 0xFFFF6B9A, 0xFF2EC4A0, 0xFFFFB84D).random(rnd).toInt()))
                if (el > a.dur - 0.4f && el - dt <= a.dur - 0.4f) say("Tadaa! 🎶", 1.2f)
            }
            is Act.Read -> {
                pose.holding = "📖"
                pose.hold = 1f
                pose.lookY = 0.7f
                pose.lookX = facing * 0.2f
                pose.scaleY *= 0.97f
                val page = (el % 1.6f) < 0.25f
                pose.eyes = if (page) EyeShape.HAPPY else EyeShape.OPEN
                pose.tilt += sin(el * 1.4f) * 3f
                if (el > 2.8f && el - dt <= 2.8f) {
                    say(line(listOf("Spannend!", "Und dann …?", "Haha, lustig!", "Oh nein, der Drache!").random(rnd), "Miau … schöne Bilder!"), 1.6f)
                    pose.eyes = EyeShape.WIDE
                }
            }
            is Act.Snack -> {
                pose.holding = a.emoji
                pose.hold = (1f - el / a.dur * 0.4f)
                val chew = (el * 8f).toInt() % 2 == 0
                pose.mouth = if (chew) MouthShape.CHEW else MouthShape.CAT
                pose.eyes = EyeShape.HAPPY
                pose.blush = 0.9f
                if ((el % 0.5f) < dt) ps.add(Particle(PKind.CRUMB, headX + facing * 0.03f, headY + 0.08f, (rnd.nextFloat() - 0.5f) * 0.06f, 0.02f, 0.9f, 0.012f, 0xFFD8A060.toInt(), rot = rnd.nextFloat() * 90f, gravity = 0.3f))
                if (el > a.dur - 0.5f && el - dt <= a.dur - 0.5f) say(line("Mmmh! 😋", "Prrr … lecker!"), 1.2f)
            }
            is Act.Flip -> {
                val p = (el / 0.85f).coerceIn(0f, 1f)
                pose.tilt += facing * 360f * (p * p * (3f - 2f * p))
                pose.armL = 1f - p * 0.5f
                pose.armR = 1f - p * 0.5f
                pose.scaleY *= 1f - 0.12f * sin(p * PI.toFloat())
                pose.eyes = if (p < 1f) EyeShape.CLOSED else EyeShape.STAR
                pose.mouth = MouthShape.OPEN
                if (el > 0.9f && el - dt <= 0.9f) ps.burst(PKind.STAR, headX, headY, 6, 0.25f, 1f, 0.022f, ParticleSystem.STAR_COLORS, upward = true)
            }
            is Act.Butterfly -> {
                val leaving = el > a.dur - 1.2f
                val ty = if (leaving) companionY - dt * 0.25f else 0.42f + sin(el * 2.6f) * 0.06f
                val tx = if (leaving) companionX + dt * 0.12f * facing else (x + sin(el * 1.3f) * 0.22f).coerceIn(0.08f, 0.92f)
                companionX += (tx - companionX) * min(1f, dt * 2.5f)
                companionY += (ty - companionY) * min(1f, dt * 2.5f)
                if (!leaving) {
                    val dx = companionX - x
                    if (abs(dx) > 0.05f) {
                        val step = sign(dx) * min(abs(dx), dt * 0.14f)
                        x = (x + step).coerceIn(minX, maxX)
                        facing = sign(dx)
                        val k = sin(el * 14f)
                        pose.footL = max(0f, k) * 0.6f
                        pose.footR = max(0f, -k) * 0.6f
                    }
                    if ((el % 1.7f) < dt) jump(0.22f, 0.42f)
                    pose.armL = 0.8f
                    pose.armR = 0.8f
                } else if (el - dt <= a.dur - 1.2f) {
                    say(line("Tschüss, Schmetterling!", "Miau … weg ist er."), 1.4f)
                }
                pose.lookX = sign(companionX - x) * 0.8f
                pose.lookY = -0.8f
                pose.eyes = EyeShape.WIDE
                pose.mouth = MouthShape.OPEN
                if (el > a.dur - 0.05f) companion = null
            }
            is Act.Daydream -> {
                pose.lookY = -1f
                pose.lookX = 0.4f * sin(el * 0.6f)
                pose.eyes = EyeShape.HAPPY
                pose.mouth = MouthShape.SMILE
                pose.blush = 0.8f
                pose.tilt += sin(el * 1.2f) * 4f
                if (el > 0.8f && el - dt <= 0.8f) {
                    say(
                        line(
                            listOf("Die Wolke sieht aus wie ein Hase! ☁️", "Ich träume vom Fliegen …", "Ob es heute Regenbogen gibt? 🌈", "Ich denke an Pfannkuchen …").random(rnd),
                            listOf("Miau … die Wolke sieht aus wie ein Fisch.", "Prrr … ich träume von Thunfisch.").random(rnd),
                        ),
                        2.2f,
                    )
                }
            }
            is Act.Duo -> runDuo(a, el, dt, ps, headX, headY)
            is Act.Door -> runDoor(a, dt)
            is Act.PlanePlay -> runPlane(el, dt, ps)
            is Act.Fetch -> {
                val p = toys.plane
                pose.lookY = 1f
                pose.hold = (el / 0.6f).coerceIn(0f, 1f)
                pose.tilt += facing * 10f * pose.hold
                if (p != null && el > 0.6f && !p.caught) {
                    p.caught = true
                    say(line("Hab ihn!", "Miau, meins!"), 1.2f)
                }
                pose.eyes = EyeShape.HAPPY
            }
            is Act.Paint -> runPaint(a, el, dt, ps)
            is Act.Work -> {
                pose.holding = a.tool
                pose.hold = 1f
                val k = sin(el * 11f)
                pose.tilt += k * 6f
                pose.lift += abs(k) * 0.03f
                pose.eyes = if (el % 1.2f < 0.9f) EyeShape.OPEN else EyeShape.HAPPY
                pose.lookY = 0.7f
                pose.mouth = MouthShape.TONGUE
                if ((el % 0.6f) < dt) ps.add(Particle(PKind.SPARKLE, headX + (rnd.nextFloat() - 0.5f) * 0.12f, headY + 0.12f, 0f, -0.05f, 0.9f, 0.016f, 0xFFFFE680.toInt()))
                if (el > a.dur - 0.5f && el - dt <= a.dur - 0.5f) {
                    say(line("Feierabend! 💰", "Miau! Fertig! 💰"), 1.6f)
                    jump(0.3f, 0.45f)
                }
            }
            is Act.Talk -> {
                val k = (el * 9f).toInt() % 2 == 0
                pose.mouth = if (k) MouthShape.OPEN else MouthShape.CAT
                pose.blush = 0.85f
                pose.tilt += sin(el * 6f) * 4f
                pose.lookX = 0f
                pose.lookY = -0.2f
            }
            is Act.Groom -> {
                if (a.brush) {
                    pose.eyes = EyeShape.HAPPY
                    pose.blush = 1f
                    pose.tilt += sin(el * 7f) * 5f
                    if ((el % 0.3f) < dt) ps.add(Particle(PKind.SPARKLE, headX + (rnd.nextFloat() - 0.5f) * 0.2f, headY + 0.08f + rnd.nextFloat() * 0.1f, 0f, -0.04f, 0.9f, 0.018f, 0xFFFFFFFF.toInt()))
                    if (el > 3.1f && el - dt <= 3.1f) say("Prrr … schön glatt!", 1.4f)
                } else {
                    pose.armR = 0.75f
                    pose.armWaveR = sin(el * 9f) * 10f
                    pose.eyes = EyeShape.CLOSED
                    pose.mouth = MouthShape.TONGUE
                    pose.tilt += 8f + sin(el * 9f) * 3f
                    pose.turn = 0.4f
                }
            }
            is Act.Stretch -> {
                val p = sin((el / a.dur).coerceIn(0f, 1f) * PI.toFloat())
                pose.scaleX += 0.22f * p
                pose.scaleY -= 0.18f * p
                pose.hold = p
                pose.eyes = EyeShape.CLOSED
                pose.mouth = if (p > 0.6f) MouthShape.YAWN else MouthShape.CAT
                pose.tilt += facing * 6f * p
            }
            is Act.Perform -> runPerform(a, el, dt, ps, headX, headY)
            is Act.EarWiggle -> {
                earV += sin(el * 26f) * 520f * dt
                pose.eyes = EyeShape.HAPPY
                pose.mouth = MouthShape.GRIN
                pose.blush = 1f
                pose.tilt += sin(el * 12f) * 5f
            }
            is Act.Bashful -> {
                pose.eyes = EyeShape.HAPPY
                pose.mouth = MouthShape.CAT
                pose.blush = 1f
                pose.turn = 0.45f * facing
                pose.tilt += sin(el * 6f) * 6f
                pose.armL = 0.35f
                pose.armR = 0.35f
            }
            is Act.Tickle -> {
                pose.eyes = EyeShape.HAPPY
                pose.mouth = MouthShape.GRIN
                pose.blush = 1f
                pose.tilt += sin(el * 30f) * 9f
                pose.lift += abs(sin(el * 15f)) * 0.05f
                pose.armL = 0.3f + 0.2f * sin(el * 25f)
                pose.armR = 0.3f - 0.2f * sin(el * 25f)
                pose.footL = max(0f, sin(el * 20f)) * 0.5f
                pose.footR = max(0f, -sin(el * 20f)) * 0.5f
            }
            is Act.FootHop -> {
                pose.eyes = EyeShape.WIDE
                pose.mouth = MouthShape.OPEN
                pose.footL = max(0f, sin(el * 22f)) * 0.9f
                pose.footR = max(0f, -sin(el * 22f)) * 0.9f
                pose.armL = 0.7f
                pose.armR = 0.7f
            }
            is Act.Carried -> {
                yOffset = carryH
                z = max(0f, z - dt * 3f)
                targetZ = 0f
                val k = sin(now * 9f)
                pose.footL = max(0f, k) * 0.7f
                pose.footR = max(0f, -k) * 0.7f
                pose.armL = 0.85f
                pose.armR = 0.85f
                pose.armWaveL = k * 12f
                pose.armWaveR = -k * 12f
                pose.eyes = if (carryH > 1.6f) EyeShape.WIDE else EyeShape.HAPPY
                pose.mouth = MouthShape.OPEN
                pose.blush = 1f
                pose.tilt += sin(now * 3f) * 6f
            }
            is Act.Fall -> {
                carryH += fallV * dt
                fallV -= 9f * dt
                x = (x + fallVX * dt).coerceIn(minX, maxX)
                targetX = x
                if (abs(fallVX) > 0.02f) facing = sign(fallVX)
                fallVX *= 1f - min(1f, dt * 1.5f)
                if (carryH <= 0f) {
                    carryH = 0f
                    if (fallV < -3f) {
                        fallV = -fallV * 0.35f
                        sqV += 9f
                        ps.burst(PKind.SMOKE, x, 0.95f, 4, 0.15f, 0.6f, 0.03f, intArrayOf(0x55FFFFFF), gravity = 0f)
                    } else if (fallV <= 0f) {
                        if (fallV < -0.5f) {
                            sqV += 6f
                            say(line(listOf("Gelandet!", "Nochmal! 😆", "Huii, das war lustig!").random(rnd), "Miau! Gelandet!"), 1.2f)
                        }
                        fallV = 0f
                    }
                }
                yOffset = carryH
                pose.eyes = if (carryH > 0.3f) EyeShape.WIDE else EyeShape.HAPPY
                pose.mouth = MouthShape.OPEN
                pose.armL = if (carryH > 0.1f) 1f else 0.5f
                pose.armR = pose.armL
            }
            is Act.Hiding -> hidden = true
            is Act.Flee -> runFlee(dt)
            is Act.FetchBall -> runFetchBall(dt, ps)
            is Act.Windy -> {
                earV += 220f * dt * (if (facing > 0f) 1f else -1f)
                pose.tilt -= facing * (8f + sin(el * 18f) * 4f)
                pose.eyes = EyeShape.SQUINT
                pose.mouth = MouthShape.O
                pose.mouthOpen = 0.5f
                pose.armL = 0.6f + 0.2f * sin(el * 20f)
                pose.armR = 0.6f - 0.2f * sin(el * 20f)
                if ((el % 0.22f) < dt) {
                    val leaf = rnd.nextBoolean()
                    ps.add(Particle(if (leaf) PKind.EMOJI else PKind.BUBBLE, x - facing * 0.3f, headY + (rnd.nextFloat() - 0.5f) * 0.2f, facing * (0.12f + rnd.nextFloat() * 0.08f), -0.01f, 1.4f, if (leaf) 0.03f else 0.016f, 0xFFFFFFFF.toInt(), text = if (leaf) listOf("🍃", "🌸", "🍂").random(rnd) else null, drag = 0.2f))
                }
            }
            is Act.Sing -> {
                val k = el * 5f
                pose.tilt += sin(k) * 8f
                pose.mouth = if ((el * 4f).toInt() % 2 == 0) MouthShape.OPEN else MouthShape.O
                pose.mouthOpen = 0.7f
                pose.eyes = EyeShape.CLOSED
                pose.blush = 0.9f
                pose.armL = 0.5f + 0.3f * sin(k)
                pose.armR = 0.5f - 0.3f * sin(k)
                if ((el % 0.35f) < dt) ps.add(Particle(PKind.NOTE, headX + (rnd.nextFloat() - 0.3f) * 0.18f, headY, (rnd.nextFloat() - 0.5f) * 0.05f, -0.08f, 1.5f, 0.035f, listOf(0xFF7C6CFF, 0xFFFF6B9A, 0xFF2EC4A0, 0xFFFFB84D).random(rnd).toInt()))
            }
        }
    }

    private fun runPerform(a: Act.Perform, el: Float, dt: Float, ps: ParticleSystem, headX: Float, headY: Float) {
        val p = (el / a.dur).coerceIn(0f, 1f)
        if (!a.success) {
            // a clumsy try: it starts, wobbles and gets confused
            if (el < 1.1f) {
                pose.tilt += sin(el * 16f) * 10f
                pose.armL = 0.5f
                pose.armR = 0.7f
                pose.eyes = EyeShape.SQUINT
                pose.mouth = MouthShape.TONGUE
            } else {
                if (el - dt < 1.1f) {
                    say(line(listOf("Hm? Wie ging das noch? 🤔", "Upps! Nochmal!", "Fast!").random(rnd), "Mau? 🤔"), 1.3f)
                    sqV += 4f
                }
                pose.eyes = EyeShape.DIZZY
                pose.mouth = MouthShape.WAVY
                pose.tilt += 6f
            }
            return
        }
        when (a.trick) {
            Trick.SITZ -> {
                val k = easeOut((el / 0.4f).coerceIn(0f, 1f))
                pose.scaleY *= 1f - 0.12f * k
                pose.scaleX *= 1f + 0.06f * k
                pose.eyes = EyeShape.HAPPY
                pose.mouth = MouthShape.SMILE
                pose.turn = 0f
                pose.lookY = -0.3f
            }
            Trick.PFOTE -> {
                pose.armR = 0.55f + 0.05f * sin(el * 8f)
                pose.armWaveR = -20f + sin(el * 6f) * 6f
                pose.turn = 0f
                pose.eyes = EyeShape.HAPPY
                pose.mouth = MouthShape.OPEN
            }
            Trick.WINKEN -> {
                pose.armL = 0.95f
                pose.armR = 0.95f
                pose.armWaveL = sin(el * 14f) * 26f
                pose.armWaveR = -sin(el * 14f) * 26f
                pose.eyes = EyeShape.HAPPY
                pose.mouth = MouthShape.OPEN
            }
            Trick.DREHEN -> {
                pose.turn = sin(el * 9f)
                pose.armL = 0.7f
                pose.armR = 0.7f
                pose.eyes = EyeShape.STAR
                pose.mouth = MouthShape.OPEN
                pose.lift += abs(sin(el * 4.5f)) * 0.05f
            }
            Trick.MAENNCHEN -> {
                val k = sin((el / 0.5f).coerceIn(0f, 1f) * PI.toFloat() / 2f)
                pose.lift += 0.18f * k
                pose.scaleY *= 1f + 0.12f * k
                pose.scaleX *= 1f - 0.06f * k
                pose.armL = 0.45f
                pose.armR = 0.45f
                pose.eyes = EyeShape.HAPPY
                pose.mouth = MouthShape.OPEN
                pose.turn = 0f
            }
            Trick.SPRUNG -> {
                if (el < dt * 1.5f) jump(0.75f, 0.65f)
                pose.armL = 1f
                pose.armR = 1f
                pose.eyes = EyeShape.STAR
                pose.mouth = MouthShape.OPEN
            }
            Trick.ROLLE -> {
                val r = ((el - 0.2f) / 1.2f).coerceIn(0f, 1f)
                pose.tilt += facing * 360f * (r * r * (3f - 2f * r))
                pose.lying = sin(r * PI.toFloat()) * 0.3f
                pose.eyes = if (r in 0.05f..0.95f) EyeShape.CLOSED else EyeShape.HAPPY
                pose.mouth = MouthShape.GRIN
            }
            Trick.VERBEUGUNG -> {
                val k = sin(p * PI.toFloat())
                pose.tilt += facing * 28f * k
                pose.lookY = k
                pose.eyes = if (k > 0.5f) EyeShape.CLOSED else EyeShape.HAPPY
                pose.armR = 0.4f * k
                pose.mouth = MouthShape.SMILE
            }
            Trick.TANZ -> {
                val k = el * 7f
                pose.tilt += sin(k) * 12f
                pose.lift += abs(sin(k)) * 0.1f
                pose.armL = 0.75f + 0.25f * sin(k)
                pose.armR = 0.75f - 0.25f * sin(k)
                pose.turn = sin(k * 0.5f) * 0.7f
                pose.eyes = EyeShape.HAPPY
                pose.mouth = MouthShape.OPEN
                pose.blush = 0.9f
                if ((el % 0.45f) < dt) ps.add(Particle(PKind.NOTE, headX + (rnd.nextFloat() - 0.5f) * 0.2f, headY, (rnd.nextFloat() - 0.5f) * 0.05f, -0.09f, 1.4f, 0.04f, listOf(0xFF7C6CFF, 0xFFFF6B9A, 0xFF2EC4A0).random(rnd).toInt()))
            }
            Trick.SALTO -> {
                if (el < dt * 1.5f) jump(0.7f, 0.9f)
                val r = ((el - 0.14f) / 0.9f).coerceIn(0f, 1f)
                pose.tilt -= facing * 360f * (r * r * (3f - 2f * r))
                pose.armL = 1f
                pose.armR = 1f
                pose.eyes = if (r in 0.05f..0.95f) EyeShape.CLOSED else EyeShape.STAR
                pose.mouth = MouthShape.OPEN
            }
        }
        val end = a.dur - 0.55f
        if (el >= end && el - dt < end) {
            say(line(listOf("Tadaa! ⭐", "Gut, oder? 😎", "Applaus! 👏").random(rnd), "Miau! Tadaa! ⭐"), 1.2f)
            ps.burst(PKind.STAR, headX, headY, 6, 0.3f, 1f, 0.022f, ParticleSystem.STAR_COLORS, upward = true)
        }
    }

    private fun runFlee(dt: Float) {
        z = max(0f, z - dt * 2f)
        targetZ = 0f
        if (now < fleeSafeUntil) {
            pose.eyes = EyeShape.STAR
            pose.mouth = MouthShape.GRIN
            pose.blush = 1f
            return
        }
        if (abs(targetX - x) < 0.02f || rnd.nextFloat() < dt * 0.5f) {
            var tx = minX + rnd.nextFloat() * (maxX - minX)
            if (abs(tx - x) < 0.15f) tx = if (x < (minX + maxX) / 2f) maxX else minX
            targetX = tx
        }
        speed = 0.42f
        walking = abs(targetX - x) > 0.01f
        if (landed && rnd.nextFloat() < dt * 0.6f) jump(0.32f, 0.38f)
        pose.eyes = EyeShape.HAPPY
        pose.mouth = MouthShape.OPEN
        pose.blush = 0.9f
        pose.armL = 0.8f
        pose.armR = 0.8f
    }

    private fun runFetchBall(dt: Float, ps: ParticleSystem) {
        z = max(0f, z - dt * 3f)
        targetZ = 0f
        if (ballHeld) {
            // shows the ball proudly, then drops it for the next throw
            props.bx = x + facing * 0.06f
            props.by = 0.62f
            pose.eyes = EyeShape.HAPPY
            pose.mouth = MouthShape.OPEN
            pose.armL = 0.6f
            pose.armR = 0.6f
            pose.lookY = -0.2f
            if (now - ballHeldAt > 0.9f) {
                props.ball = false
                ballHeld = false
                if (rnd.nextFloat() < 0.4f) say(line("Nochmal! 🎾", "Miau! Nochmal!"), 1f)
            }
            return
        }
        if (!props.ball) {
            pose.lookY = 0.5f
            pose.eyes = EyeShape.WIDE
            pose.mouth = MouthShape.OPEN
            pose.armL = 0.3f + 0.2f * sin(now * 6f)
            pose.armR = pose.armL
            return
        }
        props.bvy -= 5.5f * dt
        props.bx += props.bvx * dt
        props.by += props.bvy * dt
        props.brot += props.bvx * dt * 900f
        if (props.by < 0f) {
            props.by = 0f
            if (abs(props.bvy) > 0.4f) props.bvy = -props.bvy * 0.55f else props.bvy = 0f
            props.bvx *= 0.88f
        }
        val lo = minX - 0.1f
        val hi = maxX + 0.1f
        if (props.bx < lo) {
            props.bx = lo
            props.bvx = abs(props.bvx) * 0.6f
        }
        if (props.bx > hi) {
            props.bx = hi
            props.bvx = -abs(props.bvx) * 0.6f
        }
        targetX = props.bx.coerceIn(minX, maxX)
        speed = 0.38f
        walking = abs(targetX - x) > 0.02f
        pose.eyes = EyeShape.HAPPY
        pose.mouth = MouthShape.OPEN
        pose.lookX = sign(props.bx - x)
        pose.lookY = -0.6f
        if (abs(props.bx - x) < 0.08f && props.by < 0.9f) {
            val air = props.by > 0.15f
            ballHeld = true
            ballHeldAt = now
            props.bvx = 0f
            props.bvy = 0f
            jump(if (air) 0.32f else 0.15f, 0.4f)
            sqV += 4f
            say(if (air) line(listOf("Gefangen! 🎾", "Super Wurf!").random(rnd), "Miau! Gefangen!") else line("Hab ihn!", "Miau! Meins!"), 1f)
            ps.add(Particle(PKind.STAR, props.bx, 0.6f, 0f, -0.05f, 0.6f, 0.02f, 0xFFFFD166.toInt()))
            onBallCatch?.invoke(air)
        }
    }

    private fun runDuo(a: Act.Duo, el: Float, dt: Float, ps: ParticleSystem, headX: Float, headY: Float) {
        val face = if (a.partnerSpot > a.spot) 1f else -1f
        val hider = a.kind == FriendMoment.HIDE && a.lead
        // walk to the meeting spot first
        if (el < 1.3f && !hider) {
            targetX = a.spot
            targetZ = 0f
            speed = 0.28f
            walking = abs(targetX - x) > 0.01f
            if (!walking) {
                facing = face
                pose.turn = face * 0.5f
            }
            return
        }
        facing = face
        pose.turn = face * 0.55f
        when (a.kind) {
            FriendMoment.TALK -> {
                val lines = if (cat) listOf("Miau? Mrrp!", "Prrr, schön dich zu sehen!", "Hast du die Maus gesehen?", "Miau miau! 😸")
                else listOf("Hey! Wie geht's dir?", "Mir geht's super!", "Spielen wir nachher?", "Du bist mein bester Freund! 💕", "Hast du schon gegessen?", "Ich hab heute was Tolles erlebt!")
                val myTurns = if (a.lead) floatArrayOf(1.4f, 4.0f) else floatArrayOf(2.7f)
                for (at in myTurns) if (el >= at && el - dt < at) say(lines[rnd.nextInt(lines.size)], 1.3f)
                val talking = myTurns.any { el in it..it + 1.2f }
                pose.mouth = if (talking && (el * 9f).toInt() % 2 == 0) MouthShape.OPEN else MouthShape.CAT
                pose.eyes = if (talking) EyeShape.HAPPY else EyeShape.OPEN
                pose.blush = 0.8f
            }
            FriendMoment.BALL -> {
                val k = sin(el * 5.7f)
                pose.armL = 0.6f + 0.4f * k
                pose.armR = 0.6f + 0.4f * k
                pose.eyes = EyeShape.HAPPY
                pose.mouth = MouthShape.OPEN
                pose.lift += kotlin.math.max(0f, k) * 0.05f
            }
            FriendMoment.PAINT -> {
                pose.holding = "🖌️"
                pose.hold = 1f
                if ((a.lead && el in 1.5f..2.0f) || (!a.lead && el in 2.7f..3.2f)) {
                    pose.armR = 1f
                    pose.armWaveR = 30f
                }
                if (el > 3.4f) {
                    pose.holding = null
                    pose.hold = 0f
                    pose.eyes = EyeShape.HAPPY
                    pose.mouth = MouthShape.GRIN
                    pose.tilt += sin(el * 20f) * 6f
                    if (el - dt < 3.4f) say(line("Hihi, du bist ganz bunt!", "Miau! Bunte Pfoten!"), 1.4f)
                } else {
                    pose.mouth = MouthShape.TONGUE
                }
            }
            FriendMoment.DANCE -> {
                val k = el * 7f
                pose.tilt += sin(k) * 10f
                pose.lift += abs(sin(k)) * 0.08f
                pose.armL = 0.75f + 0.25f * sin(k)
                pose.armR = 0.75f - 0.25f * sin(k)
                pose.eyes = EyeShape.HAPPY
                pose.mouth = MouthShape.OPEN
                pose.blush = 0.9f
                if ((el % 0.6f) < dt) ps.add(Particle(PKind.NOTE, headX, headY, (rnd.nextFloat() - 0.5f) * 0.05f, -0.09f, 1.3f, 0.035f, listOf(0xFF7C6CFF, 0xFFFF6B9A, 0xFF2EC4A0).random(rnd).toInt()))
            }
            FriendMoment.HIDE -> if (a.lead) {
                // the hider runs off to the side, waits and pops out at the end
                when {
                    el < 2.2f -> {
                        targetX = maxX + 0.35f
                        speed = 0.35f
                        walking = abs(targetX - x) > 0.01f
                        if (el - dt < 0.05f) say(line("Zähl bis zehn!", "Miau! Such mich!"), 1.4f)
                    }
                    el < 7.2f -> hidden = true
                    else -> {
                        if (hidden) {
                            hidden = false
                            x = bedX
                            jump(0.4f, 0.4f)
                            say(line("Buh! Hier bin ich!", "Miau! Buh!"), 1.6f)
                            ps.burst(PKind.SPARKLE, bedX, 0.7f, 10, 0.3f, 1f, 0.02f, ParticleSystem.SPARKLE_COLORS)
                        }
                        pose.eyes = EyeShape.HAPPY
                        pose.mouth = MouthShape.GRIN
                    }
                }
            } else {
                when {
                    el < 2.4f -> {
                        pose.eyes = EyeShape.CLOSED
                        pose.armL = 0.9f
                        pose.armR = 0.9f
                        if (el - dt < 1.4f && el >= 1.4f) say("1, 2, 3 …", 1f)
                    }
                    el < 7.2f -> {
                        if (el - dt < 2.4f) say(line("Wo bist du? 👀", "Miau? Wo bist du?"), 1.6f)
                        if (el - dt < 4.8f && el >= 4.8f) say(line("Hier? … Nein!", "Mau? Nicht hier …"), 1.4f)
                        targetX = if (el < 4.8f) minX + 0.05f else maxX - 0.05f
                        speed = 0.2f
                        walking = abs(targetX - x) > 0.01f
                        pose.lookX = facing
                    }
                    else -> {
                        if (el - dt < 7.3f) say(line("Gefunden! Hihi!", "Miau! Hab dich!"), 1.5f)
                        pose.eyes = if (el < 7.8f) EyeShape.WIDE else EyeShape.HAPPY
                        pose.mouth = MouthShape.OPEN
                    }
                }
            }
            FriendMoment.CUDDLE -> {
                pose.eyes = if (el % 3f < 2.4f) EyeShape.HEART else EyeShape.HAPPY
                pose.blush = 1f
                pose.mouth = MouthShape.CAT
                pose.tilt += face * 12f
                pose.lift = 0f
                if (el - dt < 1.5f && el >= 1.5f && a.lead) say(line("Hab dich lieb! 💕", "Prrrrrr … 💕"), 1.6f)
            }
            FriendMoment.SQUABBLE -> when {
                // a little quarrel about a toy, then they make up again
                el < 3.6f -> {
                    pose.brows = 1f
                    pose.mouth = if ((el * 8f).toInt() % 2 == 0) MouthShape.OPEN else MouthShape.FLAT
                    pose.eyes = EyeShape.SQUINT
                    pose.tilt += face * (6f + sin(el * 14f) * 5f)
                    pose.armL = 0.5f + 0.3f * sin(el * 12f)
                    pose.armR = 0.5f - 0.3f * sin(el * 12f)
                    val turns = if (a.lead) floatArrayOf(1.4f, 2.9f) else floatArrayOf(2.15f)
                    for (at in turns) if (el >= at && el - dt < at) {
                        say(
                            if (cat) listOf("Fauch! Meins!", "Mrrr! Nein, meins!").random(rnd)
                            else listOf("Das ist meins!", "Nein, meins!", "Ich war zuerst da!", "Gar nicht wahr!").random(rnd),
                            1.1f,
                        )
                        if (rnd.nextBoolean()) ps.add(Particle(PKind.EMOJI, headX + face * 0.05f, headY, 0f, -0.05f, 1f, 0.045f, 0xFF000000.toInt(), "💢"))
                    }
                }
                el < 4.3f -> {
                    pose.eyes = EyeShape.OPEN
                    pose.mouth = MouthShape.FROWN
                    pose.lookY = 0.6f
                    if (el - dt < 3.7f && el >= 3.7f && a.lead) say(line("… Tut mir leid.", "Mrrp … 'tschuldigung."), 1.2f)
                }
                else -> {
                    pose.eyes = EyeShape.HEART
                    pose.blush = 1f
                    pose.mouth = MouthShape.CAT
                    pose.tilt += face * 12f
                    if (el - dt < 4.6f && el >= 4.6f && !a.lead) say(line("Wieder Freunde! 💕", "Prrr! 💕"), 1.4f)
                    if ((el % 0.5f) < dt) ps.add(Particle(PKind.HEART, headX, headY, 0f, -0.05f, 1.2f, 0.026f, 0xFFFF7FAF.toInt()))
                }
            }
        }
    }

    private fun runLadder(a: Act.Ladder, el: Float, dt: Float, ps: ParticleSystem, headX: Float, headY: Float) {
        val top = toys.ladderHeight - 0.4f
        x = toys.ladderX
        z = max(0f, z - dt * 3f)
        when {
            el < 0.8f -> {
                // set the ladder up
                toys.ladderExtend = easeOut(el / 0.8f)
                pose.armL = 0.9f
                pose.armR = 0.9f
                pose.lookY = -1f
                pose.eyes = EyeShape.HAPPY
                yOffset = 0f
                if (el - dt <= 0f) say(line("Eine Leiter! Hoch hinaus!", "Miau! Klettern!"), 1.6f)
            }
            el < 3.2f -> {
                val p = (el - 0.8f) / 2.4f
                yOffset = top * (p * p * (3f - 2f * p))
                val k = sin(el * 10f)
                pose.armL = 0.75f + 0.25f * k
                pose.armR = 0.75f - 0.25f * k
                pose.footL = max(0f, k) * 0.8f
                pose.footR = max(0f, -k) * 0.8f
                pose.turn = 0f
                pose.lookY = -0.6f
                pose.mouth = MouthShape.TONGUE
            }
            el < 5.6f -> {
                yOffset = top
                if (el - dt < 3.2f) {
                    say(if (cat) "Miau! Hier oben ist es toll!" else listOf("Huhu! Ich seh alles!", "Was für eine Aussicht!", "Ich bin der König der Leiter!").random(rnd), 2f)
                    ps.burst(PKind.STAR, headX, headY, 6, 0.3f, 1f, 0.02f, ParticleSystem.STAR_COLORS, upward = true)
                    onToy?.invoke(ToyKind.LADDER)
                }
                pose.armR = 0.95f
                pose.armWaveR = sin(el * 14f) * 26f
                pose.armL = 0.7f
                pose.eyes = EyeShape.HAPPY
                pose.mouth = MouthShape.OPEN
                pose.turn = sin(el * 1.6f) * 0.6f
            }
            el < 7.6f -> {
                if (a.jumpDown) {
                    val p = ((el - 5.6f) / 0.45f).coerceIn(0f, 1f)
                    yOffset = top * (1f - p * p)
                    pose.armL = 1f
                    pose.armR = 1f
                    pose.eyes = if (p < 1f) EyeShape.WIDE else EyeShape.STAR
                    pose.mouth = MouthShape.OPEN
                    if (p >= 1f && el - dt < 5.6f + 0.45f) {
                        sqV += 10f
                        say(line("Juhuu!", "Miau! Gelandet!"), 1.2f)
                        ps.burst(PKind.SMOKE, x, 0.95f, 4, 0.15f, 0.6f, 0.03f, intArrayOf(0x55FFFFFF), gravity = 0f)
                    }
                } else {
                    val p = ((el - 5.6f) / 1.8f).coerceIn(0f, 1f)
                    yOffset = top * (1f - p)
                    val k = sin(el * 10f)
                    pose.armL = 0.7f + 0.2f * k
                    pose.armR = 0.7f - 0.2f * k
                    pose.lookY = 1f
                }
            }
            else -> {
                yOffset = 0f
                toys.ladderExtend = 1f - easeOut(((el - 7.6f) / 0.8f).coerceIn(0f, 1f))
                pose.armL = 0.8f
                pose.armR = 0.8f
            }
        }
        if (el >= a.dur - dt) toys.ladderExtend = 0f
    }

    private fun runDoor(a: Act.Door, dt: Float) {
        walking = abs(targetX - x) > 0.006f || abs(targetZ - z) > 0.02f
        val d = a.door
        if (a.exit) {
            // fade out on the last steps to the door
            val dist = abs(doorPoints.outX(d) - x) + abs(doorPoints.outZ(d) - z) * 0.4f
            if (dist < 0.14f) fade = min(fade, dist / 0.14f)
            if (!walking) fade = max(0f, fade - dt * 4f)
            if (fade <= 0.02f && !a.done) {
                fade = 0f
                a.done = true
                val cb = onThroughDoor
                onThroughDoor = null
                cb?.invoke()
            }
        } else {
            fade = min(1f, fade + dt * 2.5f)
            if (!walking && fade >= 1f) a.done = true
        }
        pose.eyes = EyeShape.HAPPY
    }

    private fun runPlane(el: Float, dt: Float, ps: ParticleSystem) {
        when {
            el < 1.7f -> {
                props.folding = (el / 1.6f).coerceIn(0f, 1f)
                pose.hold = 1f
                pose.lookY = 0.8f
                pose.lookX = 0f
                pose.mouth = MouthShape.TONGUE
                pose.tilt += sin(el * 12f) * 2f
            }
            el < 2.2f -> {
                props.folding = 1f
                pose.armR = 1f
                pose.armL = 0.3f
                pose.tilt -= facing * 8f
                pose.eyes = EyeShape.SQUINT
            }
            else -> {
                if (props.folding >= 0f) {
                    props.folding = -1f
                    toys.throwPlane(x, facing, now, rnd)
                    onSound?.invoke(de.knuffi.app.sound.Sfx.WHOOSH)
                    say(line("Fliiieg!", "Miau! Flieg!"), 1.4f)
                    sqV += 4f
                }
                val p = toys.plane
                pose.armR = max(0f, 0.6f - (el - 2.2f))
                pose.eyes = EyeShape.HAPPY
                pose.mouth = MouthShape.OPEN
                if (p != null) {
                    pose.lookX = ((p.x - x) * 4f).coerceIn(-1f, 1f)
                    pose.lookY = (-p.h).coerceIn(-1f, 1f)
                    pose.turn = pose.lookX * 0.5f
                }
            }
        }
    }

    private fun runPaint(a: Act.Paint, el: Float, dt: Float, ps: ParticleSystem) {
        pose.holding = "🖌️"
        pose.hold = 1f
        pose.mouth = MouthShape.TONGUE
        for (i in 0 until a.flicks) {
            val at = 0.7f + i * 0.75f
            if (el >= at && el - dt < at) {
                val dir = if (rnd.nextBoolean()) facing else -facing
                val onWall = rnd.nextFloat() < 0.65f
                val sx = x + dir * (0.1f + rnd.nextFloat() * 0.18f)
                val lift = if (onWall) 0.9f + rnd.nextFloat() * 1.1f else 0f
                if (toys.addSplash(sx, lift, now, rnd, toyConfig)) {
                    onSound?.invoke(de.knuffi.app.sound.Sfx.SPLASH)
                    sqV += 3f
                    pose.armR = 1f
                }
            }
            if (el in at..at + 0.25f) {
                pose.armR = 1f
                pose.armWaveR = 30f
                pose.tilt += facing * 6f
            }
        }
        if (el > a.dur - 0.7f && el - dt <= a.dur - 0.7f) say(line("Ta-daa! Kunst! 🎨", "Miau! Pfotenkunst! 🎨"), 1.6f)
        if (el > a.dur - 0.7f) {
            pose.eyes = EyeShape.HAPPY
            pose.mouth = MouthShape.GRIN
            pose.holding = null
            pose.hold = 0f
        }
    }

    private fun runEat(a: Act.Eat, el: Float, ps: ParticleSystem, headX: Float, headY: Float) {
        props.foodPop = min(1f, props.foodPop + 0.05f)
        if (hasBowl) {
            val dir = sign(bowlX - x).let { if (it == 0f) -1f else it }
            facing = dir
            pose.turn = dir * 0.8f
            pose.tilt += dir * 8f
            pose.lookX = dir
            pose.lookY = 0.8f
        } else {
            pose.holding = a.emoji
            pose.hold = 1f
            pose.lookY = 0.6f
        }
        if (el < 2.1f) {
            val chew = (el / 0.42f).toInt()
            val phase = (el % 0.42f) / 0.42f
            pose.mouth = MouthShape.CHEW
            pose.mouthOpen = if (phase < 0.5f) 1f else 0f
            pose.eyes = EyeShape.HAPPY
            pose.blush = 0.8f
            if (chew > props.bites && chew <= 4) {
                props.bites = chew
                sqV += 2.5f
                val fx = if (hasBowl) bowlX else headX
                ps.burst(PKind.CRUMB, fx, headY + 0.07f, 5, 0.18f, 0.7f, 0.009f, intArrayOf(0xFFE8C07A.toInt(), 0xFFFFFFFF.toInt(), 0xFFB07A55.toInt()), gravity = 1.1f, upward = true)
            }
        } else {
            if (props.food != null) {
                props.food = null
                ps.burst(PKind.HEART, headX, headY, 3, 0.16f, 1.2f, 0.026f, ParticleSystem.HEART_COLORS, upward = true)
            }
            pose.holding = null
            pose.hold = 0f
            pose.eyes = EyeShape.HAPPY
            pose.mouth = MouthShape.TONGUE
            pose.turn = facing * 0.2f
        }
    }

    private fun runBath(el: Float, dt: Float, ps: ParticleSystem, headX: Float, headY: Float) {
        x = props.tubX
        z = max(0f, z - dt * 3f)
        when {
            el < 0.5f -> props.tub = easeOutBack(el / 0.5f)
            el < 3.6f -> props.tub = 1f
            else -> props.tub = 1f - ((el - 3.6f) / 0.6f).coerceIn(0f, 1f)
        }
        if (!hasTub) props.tub = 0f
        if (el >= 0.5f && el - dt < 0.5f) jump(0.45f, 0.4f)
        props.inTub = hasTub && el in 0.72f..3.2f
        if (el >= 3.1f && el - dt < 3.1f) {
            jump(0.4f, 0.4f)
            ps.burst(PKind.DROP, headX, headY + 0.08f, 10, 0.4f, 0.8f, 0.014f, intArrayOf(0xFFBFE8FF.toInt(), 0xFF8FD3FF.toInt()), gravity = 1.2f, upward = true)
            ps.burst(PKind.SPARKLE, headX, headY, 12, 0.35f, 1.2f, 0.024f, ParticleSystem.SPARKLE_COLORS)
        }
        if (el in 0.8f..3.1f) {
            val p = ((el - 0.8f) / 0.8f).coerceIn(0f, 1f)
            pose.foam = if (el < 2.8f) p else 1f - (el - 2.8f) / 0.3f
            pose.eyes = EyeShape.HAPPY
            pose.mouth = MouthShape.OPEN
            pose.tilt += sin(el * 5f) * 6f
            pose.armL = 0.5f + 0.3f * sin(el * 8f)
            pose.armR = 0.5f - 0.3f * sin(el * 8f)
            if ((el % 0.25f) < dt) {
                ps.add(Particle(PKind.BUBBLE, headX + (rnd.nextFloat() - 0.5f) * 0.25f, headY + 0.08f, (rnd.nextFloat() - 0.5f) * 0.05f, -0.09f - rnd.nextFloat() * 0.05f, 1.8f, 0.014f + rnd.nextFloat() * 0.012f, android.graphics.Color.WHITE))
            }
        }
        yOffset = if (props.inTub) -0.02f else 0f
    }

    private fun runBall(el: Float, dt: Float, ps: ParticleSystem) {
        z = max(0f, z - dt * 3f)
        targetZ = 0f
        if (!props.ball) return
        // Ball physics: x normalised, y in pet sizes above the floor
        props.bvy -= 5.5f * dt
        props.bx += props.bvx * dt
        props.by += props.bvy * dt
        props.brot += props.bvx * dt * 900f
        if (props.by < 0f) {
            props.by = 0f
            if (abs(props.bvy) > 0.4f) props.bvy = -props.bvy * 0.62f else props.bvy = 0f
            props.bvx *= 0.92f
        }
        val lo = minX - 0.14f
        val hi = maxX + 0.14f
        if (props.bx < lo) {
            props.bx = lo
            props.bvx = abs(props.bvx)
        }
        if (props.bx > hi) {
            props.bx = hi
            props.bvx = -abs(props.bvx)
        }
        // Chase
        targetX = props.bx.coerceIn(minX, maxX)
        speed = 0.3f
        walking = abs(targetX - x) > 0.02f
        pose.eyes = EyeShape.HAPPY
        pose.mouth = MouthShape.OPEN
        pose.lookX = sign(props.bx - x)
        val near = abs(props.bx - x) < 0.09f
        if (near && props.by < 0.5f && now - jumpStart > 0.6f && el < 3.8f) {
            props.bvx = (if (props.bx >= x) 1f else -1f) * (0.35f + rnd.nextFloat() * 0.25f)
            props.bvy = 2.6f + rnd.nextFloat() * 0.8f
            jump(0.3f, 0.4f)
            sqV += 3f
            ps.add(Particle(PKind.STAR, props.bx, 0.5f, 0f, -0.05f, 0.6f, 0.02f, 0xFFFFD166.toInt()))
        }
        if (el > 3.8f) props.ballAlpha = (1f - (el - 3.8f) / 0.8f).coerceIn(0f, 1f)
        if (el >= 4.55f) props.ball = false
    }

    private fun updateBubble(t: Float, dt: Float, needs: List<Need>, look: PetLook) {
        if (t > bubbleUntil) bubbleText = null
        if (bubbleText == null && t > nextNeedBubble && !act.care && needs.isNotEmpty() && !look.sleeping) {
            val need = needs[needIndex % needs.size]
            needIndex++
            bubbleText = if (cat) when (need) {
                Need.SICK -> "Miau … mir geht's nicht gut."
                Need.HUNGRY -> "Miau! Miau! Futter!"
                Need.DIRTY -> "Bürstest du mich?"
                Need.BORED -> "Mau … spielst du mit mir?"
                Need.TIRED -> "Mrrr … so müde …"
            } else when (need) {
                Need.SICK -> "Mir geht's nicht gut …"
                Need.HUNGRY -> "Ich hab Hunger!"
                Need.DIRTY -> "Iiih, ich will baden!"
                Need.BORED -> "Mir ist langweilig …"
                Need.TIRED -> "Ich bin so müde …"
            }
            bubbleUntil = t + 3f
            nextNeedBubble = t + 7f + rnd.nextFloat() * 3f
        }
        val wish = wishText
        if (bubbleText == null && wish != null && t > nextWishBubble && !act.care && !look.sleeping && !inRoomGame && !carried) {
            bubbleText = wish
            bubbleUntil = t + 3.2f
            nextWishBubble = t + 18f + rnd.nextFloat() * 8f
        }
        val target = if (bubbleText != null) 1f else 0f
        if (bubbleText != null) bubble = bubbleText
        bubbleAlpha += (target - bubbleAlpha) * min(1f, dt * 10f)
        if (bubbleAlpha < 0.02f && bubbleText == null) bubble = null
    }

    private fun ambient(t: Float, look: PetLook, ps: ParticleSystem, headX: Float, headY: Float) {
        if (t - ambientAt < 0.1f) return
        when {
            act is Act.Sleeping && t - ambientAt > 1.6f -> {
                ambientAt = t
                ps.add(Particle(PKind.ZZZ, headX + 0.06f, headY, 0.025f, -0.05f, 2.4f, 0.03f + rnd.nextFloat() * 0.015f, 0xFF9FB2FF.toInt()))
                if (rnd.nextFloat() < 0.3f) ps.add(Particle(PKind.SNOT, headX - 0.02f, headY + 0.07f, 0f, 0f, 2.2f, 0.02f, 0xFFBFE8FF.toInt()))
            }
            look.dirty && !act.care && t - ambientAt > 1.2f -> {
                ambientAt = t
                ps.add(Particle(PKind.SMOKE, headX + (rnd.nextFloat() - 0.5f) * 0.12f, headY + 0.1f, 0f, -0.035f, 1.8f, 0.022f, 0x558FA37A))
            }
            look.mood == Mood.HAPPY && !act.care && t - ambientAt > 4.5f -> {
                ambientAt = t
                ps.add(Particle(PKind.SPARKLE, headX + (rnd.nextFloat() - 0.5f) * 0.2f, headY + 0.02f, 0f, -0.04f, 1.2f, 0.016f, 0xFFFFE680.toInt()))
            }
        }
    }

    companion object {
        /** Debug builds can force a toy so that screenshots catch it. */
        @Volatile
        var debugToy: ToyKind? = null
        var debugHobby: String? = null

        fun easeOut(p: Float): Float = 1f - (1f - p) * (1f - p)

        fun easeOutBack(p: Float): Float {
            val c1 = 1.70158f
            val c3 = c1 + 1f
            val q = p - 1f
            return 1f + c3 * q * q * q + c1 * q * q
        }
    }
}
