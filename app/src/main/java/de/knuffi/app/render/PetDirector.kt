package de.knuffi.app.render

import de.knuffi.core.Catalog
import de.knuffi.core.GameEvent
import de.knuffi.core.Mood
import de.knuffi.core.Need
import de.knuffi.core.ReactionKind
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
}

/**
 * The pet's "brain": chooses behaviours, reacts to events and turns everything into a [PetPose].
 * Horizontal positions are normalised to the scene width, heights are in units of the pet size.
 */
class PetDirector(seed: Int = 7) {
    val pose = PetPose()
    val props = Props()
    private val rnd = Random(seed)

    // World configuration
    var minX = 0.24f
    var maxX = 0.7f
    var bowlX = 0.17f
    var bedX = 0.8f
    var hasBowl = true
    var hasBed = true
    var hasTub = true
    var hasBall = true

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
    }

    // ------------------------------------------------------------------ input

    fun say(text: String, seconds: Float = 2.4f) {
        bubbleText = text
        bubbleUntil = now + seconds
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
                ReactionKind.CLEAN -> startCare(Act.Bath())
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
                ReactionKind.SLEEP -> say("Gute Nacht …", 2.5f)
                ReactionKind.WAKE -> Unit
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
            is GameEvent.CoinsGained -> ps.burst(PKind.COIN, headX, headY, min(8, 2 + e.amount / 10), 0.3f, 1f, 0.03f, intArrayOf(0xFFFFC83D.toInt()), gravity = 0.7f, upward = true)
            else -> Unit
        }
    }

    private fun start(a: Act) {
        queue.clear()
        act = a
        actStart = now
        onActStart(a)
    }

    private fun startCare(vararg acts: Act) {
        if (act is Act.Sleeping || act is Act.GoSleep) return
        props.inTub = false
        props.ball = false
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
                say("Wiii!", 1.2f)
            }
            is Act.Wave -> say(listOf("Hallo!", "Hey du!", "Hihi!").random(rnd), 1.6f)
            is Act.Yawn -> say("Gääähn …", 1.6f)
            is Act.Rumble -> say("*knurr*", 1.5f)
            is Act.Sneeze -> Unit
            is Act.Eat -> say(listOf("Mampf!", "Lecker!", "Nom nom!").random(rnd), 1.8f)
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
                say("Juhu, ein Ball!", 1.8f)
            }
            is Act.Heal -> {
                props.pill = a.emoji
                props.pillX = x + 0.35f
                props.pillY = 1.6f
            }
            is Act.Cheer -> jump(0.4f, 0.5f)
            is Act.Surprised -> {
                jump(0.18f, 0.35f)
                say("Oh!", 1.2f)
            }
            is Act.Refuse -> say("Nö!", 1.1f)
            is Act.Giggle -> say("Hihi!", 1.2f)
            is Act.Wake -> say("Guten Morgen!", 2f)
            else -> Unit
        }
    }

    private fun chooseIdle(look: PetLook): Act {
        val r = rnd.nextFloat()
        fun wander(mul: Float = 1f): Act {
            var tx = minX + rnd.nextFloat() * (maxX - minX)
            if (abs(tx - x) < 0.1f) tx = if (x < (minX + maxX) / 2f) maxX - 0.05f else minX + 0.05f
            return Act.Walk(tx, mul)
        }
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
        if (look == null) return
        pose.breath = sin(t * 2f * PI.toFloat() / 3.2f)

        if (look.isEgg) {
            updateEgg(t)
            return
        }

        // Sleep is driven by the game state.
        if (look.sleeping) {
            if (act !is Act.Sleeping && act !is Act.GoSleep && !(act is Act.Walk && queue.firstOrNull() is Act.GoSleep)) {
                props.inTub = false
                props.ball = false
                props.tub = 0f
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
                    z += (1f - z) * min(1f, dt * 6f)
                    yOffset = 0.26f * easeOut(p)
                }
                pose.lying = easeOut(p)
                props.blanket = if (hasBed) ((p - 0.4f) / 0.6f).coerceIn(0f, 1f) else 0f
                pose.nightCap = p
                pose.eyes = if (p > 0.5f) EyeShape.CLOSED else EyeShape.SLEEPY
            }
            is Act.Sleeping -> {
                if (hasBed) {
                    x = bedX
                    z = 1f
                    yOffset = 0.26f
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
                    yOffset = 0.26f * (1f - out)
                    z = 1f - out
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
            bubbleText = when (need) {
                Need.SICK -> "Mir geht's nicht gut …"
                Need.HUNGRY -> "Ich hab Hunger!"
                Need.DIRTY -> "Iiih, ich will baden!"
                Need.BORED -> "Mir ist langweilig …"
                Need.TIRED -> "Ich bin so müde …"
            }
            bubbleUntil = t + 3f
            nextNeedBubble = t + 7f + rnd.nextFloat() * 3f
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
        fun easeOut(p: Float): Float = 1f - (1f - p) * (1f - p)

        fun easeOutBack(p: Float): Float {
            val c1 = 1.70158f
            val c3 = c1 + 1f
            val q = p - 1f
            return 1f + c3 * q * q * q + c1 * q * q
        }
    }
}
