package de.knuffi.app.render

import de.knuffi.core.FriendMoment
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.sin
import kotlin.random.Random

/**
 * Lets the active pet and a visiting friend do things together: talk, throw a ball, paint each other,
 * dance, play hide and seek and cuddle. Which moments are possible depends on the friendship level.
 */
class FriendPlay(seed: Int = 21) {
    private val rnd = Random(seed)
    var kind: FriendMoment? = null
        private set
    private var start = 0f
    private var next = 6f
    private var painted = 0

    /** Called when a moment starts (the app rewards the friendship). */
    var onMoment: ((FriendMoment) -> Unit)? = null

    // Ball between the two pets (normalised x, height in pet sizes).
    var ballVisible = false
        private set
    var ballX = 0f
        private set
    var ballH = 0f
        private set
    var ballRot = 0f
        private set

    fun duration(k: FriendMoment): Float = when (k) {
        FriendMoment.TALK -> 5.6f
        FriendMoment.BALL -> 7.5f
        FriendMoment.PAINT -> 5.2f
        FriendMoment.DANCE -> 5f
        FriendMoment.HIDE -> 9f
        FriendMoment.CUDDLE -> 6f
        FriendMoment.SQUABBLE -> 6.4f
    }

    fun reset() {
        kind = null
        ballVisible = false
        next = 6f
    }

    fun update(t: Float, host: PetDirector, guest: PetDirector, level: Int, ps: ParticleSystem) {
        val k = kind
        if (k != null && t - start > duration(k) + 0.3f) {
            kind = null
            ballVisible = false
            next = t + 10f + rnd.nextFloat() * 10f
        }
        if (kind == null && t > next) {
            if (host.canDuo && guest.canDuo) {
                val allowed = FriendMoment.entries.filter { it.minLevel <= level }
                val m = allowed[rnd.nextInt(allowed.size)]
                kind = m
                start = t
                painted = 0
                val close = m == FriendMoment.CUDDLE
                val a = if (close) 0.44f else 0.34f
                val b = if (close) 0.58f else 0.66f
                // In hide and seek the guest hides and the host searches.
                host.startDuo(m, lead = m != FriendMoment.HIDE, spot = a, partnerSpot = b, dur = duration(m))
                guest.startDuo(m, lead = m == FriendMoment.HIDE, spot = b, partnerSpot = a, dur = duration(m))
                onMoment?.invoke(m)
            } else {
                next = t + 3f
            }
        }
        val cur = kind ?: return
        val el = t - start
        when (cur) {
            FriendMoment.BALL -> {
                val throwT = 1.1f
                if (el > 1.4f && el < duration(cur) - 0.5f) {
                    val u = (el - 1.4f) / throwT
                    val n = floor(u).toInt()
                    val f = u - n
                    val from = if (n % 2 == 0) host.x else guest.x
                    val to = if (n % 2 == 0) guest.x else host.x
                    ballVisible = true
                    ballX = from + (to - from) * f
                    ballH = 0.55f + sin(f * PI.toFloat()) * 0.9f
                    ballRot += (to - from) * 40f
                } else {
                    ballVisible = false
                }
            }
            FriendMoment.PAINT -> {
                if (el > 1.8f && painted == 0) {
                    painted = 1
                    guest.paintMarks = 1f
                    ps.burst(PKind.DROP, guest.x, 0.7f, 8, 0.2f, 0.7f, 0.016f, Toys.SPLASH_COLORS, gravity = 1f, upward = true)
                }
                if (el > 3f && painted == 1) {
                    painted = 2
                    host.paintMarks = 1f
                    ps.burst(PKind.DROP, host.x, 0.7f, 8, 0.2f, 0.7f, 0.016f, Toys.SPLASH_COLORS, gravity = 1f, upward = true)
                }
            }
            else -> Unit
        }
        if (abs(host.x - guest.x) < 0.2f && cur == FriendMoment.CUDDLE && (el % 0.6f) < 0.05f) {
            ps.add(Particle(PKind.HEART, (host.x + guest.x) / 2f, 0.62f, 0f, -0.05f, 1.4f, 0.03f, 0xFFFF7FAF.toInt()))
        }
    }
}
