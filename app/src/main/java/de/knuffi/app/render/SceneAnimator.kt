package de.knuffi.app.render

import android.graphics.RectF
import de.knuffi.core.Catalog
import de.knuffi.core.Form
import de.knuffi.core.GameEvent
import de.knuffi.core.Mood
import de.knuffi.core.ReactionKind
import java.time.LocalTime
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.sign
import kotlin.math.sin

/**
 * Turns game events and touches into motion: reactions, wandering, particles.
 * All positions are normalised to the scene size.
 */
class SceneAnimator {
    val frame = PetFrame()
    val scene = SceneState()
    val petBounds = RectF(0.35f, 0.4f, 0.65f, 0.9f)

    private var reaction: ReactionKind? = null
    private var reactionAt = -10f
    private var tapAt = -10f
    private var eggTapAt = -10f
    private var lookX = 0f
    private var lookY = 0f
    private var lookUntil = -10f
    private var petX = 0.5f
    private var targetX = 0.5f
    private var nextWanderAt = 2.5f
    private var facing = 1f
    private var lastT = 0f
    private var lastZ = 0f
    private var lastAmbient = 0f
    private var lastStink = 0f
    private var lastHourCheck = -100f
    private var pendingParticles: ((ParticleSystem) -> Unit)? = null
    var hourOverride: Float? = null

    fun hitPet(nx: Float, ny: Float): Boolean {
        val padX = (petBounds.width()) * 0.1f
        return nx >= petBounds.left - padX && nx <= petBounds.right + padX && ny >= petBounds.top && ny <= petBounds.bottom + 0.03f
    }

    fun lookAt(nx: Float, ny: Float, t: Float) {
        val cx = petBounds.centerX()
        val cy = petBounds.centerY()
        lookX = ((nx - cx) * 4f).coerceIn(-1f, 1f)
        lookY = ((ny - cy) * 4f).coerceIn(-1f, 1f)
        lookUntil = t + 2.5f
    }

    fun onTap(t: Float) {
        tapAt = t
    }

    fun onEggTap(t: Float, ps: ParticleSystem) {
        eggTapAt = t
        val x = petBounds.centerX()
        val y = petBounds.top + petBounds.height() * 0.3f
        ps.burst(PKind.CRUMB, x, y, 5, 0.25f, 0.6f, 0.012f, intArrayOf(0xFFFFF4E0.toInt(), 0xFFF0D2A8.toInt()), gravity = 0.9f, upward = true)
    }

    fun onEvent(e: GameEvent, t: Float, ps: ParticleSystem) {
        val cx = petBounds.centerX()
        val headY = petBounds.top + petBounds.height() * 0.3f
        val mouthY = petBounds.top + petBounds.height() * 0.62f
        when (e) {
            is GameEvent.Reaction -> {
                reaction = e.kind
                reactionAt = t
                when (e.kind) {
                    ReactionKind.EAT -> {
                        val emoji = e.itemId?.let { Catalog[it]?.emoji } ?: "🍙"
                        ps.add(Particle(PKind.EMOJI, cx + 0.12f, mouthY - 0.12f, -0.1f, 0.08f, 0.9f, 0.08f, 0xFF000000.toInt(), emoji, grow = -0.6f))
                        ps.burst(PKind.CRUMB, cx, mouthY, 8, 0.25f, 0.7f, 0.01f, intArrayOf(0xFFE8C07A.toInt(), 0xFFFFFFFF.toInt(), 0xFFB07A55.toInt()), gravity = 0.9f, upward = true)
                    }
                    ReactionKind.PET -> ps.burst(PKind.HEART, cx, headY, 3, 0.18f, 1.2f, 0.03f, ParticleSystem.HEART_COLORS, upward = true)
                    ReactionKind.PLAY, ReactionKind.GAME -> {
                        ps.burst(PKind.STAR, cx, headY, 7, 0.35f, 1f, 0.03f, ParticleSystem.STAR_COLORS, upward = true)
                        ps.add(Particle(PKind.NOTE, cx + 0.1f, headY, 0.05f, -0.12f, 1.4f, 0.05f, 0xFF7C6CFF.toInt()))
                    }
                    ReactionKind.CLEAN -> {
                        ps.burst(PKind.BUBBLE, cx, mouthY, 14, 0.25f, 1.4f, 0.02f, intArrayOf(0xCCFFFFFF.toInt(), 0xCCB3E5FF.toInt()), gravity = -0.12f)
                        ps.burst(PKind.SPARKLE, cx, headY, 6, 0.3f, 1f, 0.03f, intArrayOf(0xFFFFFFFF.toInt(), 0xFFB3E5FF.toInt()))
                    }
                    ReactionKind.HEAL, ReactionKind.DRINK -> {
                        ps.burst(PKind.SPARKLE, cx, headY, 8, 0.3f, 1.1f, 0.03f, intArrayOf(0xFF7DD9B0.toInt(), 0xFFFFFFFF.toInt(), 0xFFFFE680.toInt()), upward = true)
                        ps.burst(PKind.HEART, cx, headY, 2, 0.15f, 1.2f, 0.025f, ParticleSystem.HEART_COLORS, upward = true)
                    }
                    ReactionKind.SLEEP -> ps.add(Particle(PKind.ZZZ, cx + 0.08f, headY, 0.03f, -0.07f, 2f, 0.05f, 0xFF9FB4FF.toInt()))
                    ReactionKind.WAKE -> ps.add(Particle(PKind.TEXT, cx + 0.1f, headY - 0.05f, 0f, -0.05f, 0.9f, 0.07f, 0xFFFFB84D.toInt(), "!"))
                    ReactionKind.REFUSE -> ps.add(Particle(PKind.EMOJI, cx + 0.12f, headY - 0.04f, 0f, -0.04f, 1f, 0.06f, 0xFF000000.toInt(), "💢"))
                    ReactionKind.BUY -> ps.burst(PKind.COIN, cx, headY, 6, 0.3f, 1f, 0.03f, intArrayOf(0xFFFFC83D.toInt()), gravity = 0.5f, upward = true)
                }
            }
            GameEvent.Hatched -> {
                reaction = ReactionKind.PLAY
                reactionAt = t
                ps.burst(PKind.CONFETTI, cx, headY, 40, 0.6f, 1.8f, 0.025f, ParticleSystem.CONFETTI_COLORS, gravity = 0.5f, upward = true)
                ps.burst(PKind.SPARKLE, cx, headY, 12, 0.4f, 1.2f, 0.04f, ParticleSystem.STAR_COLORS)
            }
            is GameEvent.LevelUp -> ps.burst(PKind.STAR, cx, headY, 12, 0.45f, 1.4f, 0.035f, ParticleSystem.STAR_COLORS, gravity = 0.3f, upward = true)
            is GameEvent.Evolved -> ps.burst(PKind.SPARKLE, cx, headY, 16, 0.45f, 1.5f, 0.04f, ParticleSystem.STAR_COLORS)
            is GameEvent.CoinsGained -> ps.burst(PKind.COIN, cx, headY, min(8, 2 + e.amount / 10), 0.3f, 1f, 0.028f, intArrayOf(0xFFFFC83D.toInt()), gravity = 0.6f, upward = true)
            else -> Unit
        }
    }

    fun update(t: Float, look: PetLook?, ps: ParticleSystem, walking: Boolean) {
        val dt = (t - lastT).coerceIn(0f, 0.1f)
        lastT = t
        val override = hourOverride
        if (override != null) {
            scene.hour = override
        } else if (t - lastHourCheck > 5f || lastHourCheck < 0f) {
            lastHourCheck = t
            val now = LocalTime.now()
            scene.hour = now.hour + now.minute / 60f
        }
        scene.look = look
        if (look == null) return
        PetMotion.idle(frame, look, t)
        frame.expression = Expression.AUTO

        val isEgg = look.form == Form.EGG
        if (isEgg) {
            val since = t - eggTapAt
            if (since < 0.6f) frame.eggWobble = sin(since * 40f) * 14f * (1f - since / 0.6f)
            petX = 0.5f
            scene.petX = 0.5f
            return
        }

        // Wandering around the room
        val canWalk = walking && !look.sleeping && look.mood != Mood.SICK && look.mood != Mood.SAD && look.mood != Mood.GONE
        if (canWalk) {
            if (t > nextWanderAt) {
                targetX = 0.32f + ps.rnd.nextFloat() * 0.36f
                nextWanderAt = t + 4f + ps.rnd.nextFloat() * 5f
            }
        } else if (!look.sleeping) {
            targetX = 0.5f
        }
        val dx = targetX - petX
        if (abs(dx) > 0.004f && t - reactionAt > 1.6f) {
            petX += sign(dx) * min(abs(dx), 0.07f * dt)
            facing = sign(dx)
            frame.hop = maxOf(frame.hop, abs(sin(t * 8f)) * 0.14f)
        }
        frame.facing = facing
        scene.petX = petX

        // Reactions
        val since = t - reactionAt
        when (reaction) {
            ReactionKind.EAT -> if (since < 1.8f) {
                frame.expression = Expression.EATING
                frame.mouthOpen = abs(sin(since * 9f))
            }
            ReactionKind.PET -> if (since < 1.3f) {
                frame.expression = Expression.LOVE
                if (since < 0.3f) frame.squish = sin(since / 0.3f * Math.PI.toFloat()) * 0.55f
            }
            ReactionKind.PLAY, ReactionKind.GAME -> if (since < 1.6f) {
                frame.expression = Expression.EXCITED
                frame.hop = abs(sin(since * Math.PI.toFloat() / 0.53f)) * 0.9f
            }
            ReactionKind.CLEAN, ReactionKind.HEAL, ReactionKind.DRINK, ReactionKind.BUY -> if (since < 1.4f) {
                frame.expression = Expression.EXCITED
            }
            ReactionKind.REFUSE -> if (since < 0.8f) {
                frame.expression = Expression.REFUSE
                frame.tilt = sin(since * 30f) * 9f * (1f - since / 0.8f)
            }
            ReactionKind.WAKE -> if (since < 1f) frame.expression = Expression.SURPRISED
            else -> Unit
        }
        val tapSince = t - tapAt
        if (tapSince < 0.3f && frame.squish == 0f) frame.squish = sin(tapSince / 0.3f * Math.PI.toFloat()) * 0.5f

        if (t < lookUntil) {
            frame.lookX = lookX
            frame.lookY = lookY
        } else {
            frame.lookX = sin(t * 0.7f) * 0.5f
            frame.lookY = sin(t * 0.45f) * 0.3f
        }

        // Ambient particles
        val headX = petBounds.centerX()
        val headY = petBounds.top + petBounds.height() * 0.25f
        if (look.sleeping && t - lastZ > 1.4f) {
            lastZ = t
            ps.add(Particle(PKind.ZZZ, headX + 0.07f, headY, 0.025f, -0.06f, 2.2f, 0.035f + ps.rnd.nextFloat() * 0.02f, 0xFFB8C6FF.toInt()))
        }
        if (!look.sleeping && look.mood == Mood.HAPPY && t - lastAmbient > 4f) {
            lastAmbient = t
            ps.add(Particle(PKind.SPARKLE, headX + (ps.rnd.nextFloat() - 0.5f) * 0.25f, headY, 0f, -0.05f, 1.2f, 0.025f, 0xFFFFE680.toInt()))
        }
        if (look.dirty && t - lastStink > 1.1f) {
            lastStink = t
            ps.add(Particle(PKind.SMOKE, headX + (ps.rnd.nextFloat() - 0.5f) * 0.2f, petBounds.centerY(), 0f, -0.04f, 1.6f, 0.02f, 0x668FA37A.toInt()))
        }
        if (look.mood == Mood.SICK && t - lastAmbient > 2.5f) {
            lastAmbient = t
            ps.add(Particle(PKind.BUBBLE, headX + 0.1f, headY, 0.01f, -0.05f, 1.6f, 0.015f, 0xAA9ED99A.toInt()))
        }
        pendingParticles?.invoke(ps)
        pendingParticles = null
    }

    fun setBounds(l: Float, t: Float, r: Float, b: Float) {
        if (r > l && b > t) petBounds.set(l, t, r, b)
    }
}
