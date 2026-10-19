package de.knuffi.app.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import de.knuffi.app.render.PetDirector
import de.knuffi.app.render.ParticleSystem
import de.knuffi.app.render.SceneModel
import de.knuffi.core.HideSpot
import de.knuffi.core.RoomGame
import de.knuffi.core.RoomGames
import kotlin.math.hypot
import kotlin.random.Random

/**
 * A little game right in the room: hide and seek, tag or fetch. It is driven by the scene
 * (every frame) and by taps. The values shown in the HUD are Compose state.
 */
class RoomGameSession(val game: RoomGame, private val petName: String) {
    private enum class Phase { START, HIDING, SEEKING, FOUND, RUNNING, DONE }

    var score by mutableIntStateOf(0)
        private set
    var round by mutableIntStateOf(1)
        private set
    var secondsLeft by mutableIntStateOf(game.seconds)
        private set
    var hint by mutableStateOf<String?>(null)
        private set
    var finished by mutableStateOf(false)
        private set

    /** Ten catches in the air in a row (a secret). */
    var juggled = false
        private set

    private val rnd = Random(System.nanoTime())
    private var phase = Phase.START
    private var phaseAt = 0f
    private var lastT = -1f
    private var timeLeft = game.seconds.toFloat()
    private var spots: List<HideSpot> = emptyList()
    private var target: HideSpot? = null
    private val wrong = HashSet<HideSpot>()
    private var airStreak = 0

    private val candidates = listOf(HideSpot.BED, HideSpot.PLANT, HideSpot.CURTAIN, HideSpot.BOWL, HideSpot.RUG, HideSpot.FLOOR)

    private fun setPhase(p: Phase, t: Float) {
        phase = p
        phaseAt = t
    }

    private fun startHideRound(d: PetDirector, ps: ParticleSystem, headX: Float, headY: Float, t: Float) {
        spots = candidates.shuffled(rnd).take(3)
        target = spots[rnd.nextInt(spots.size)]
        wrong.clear()
        d.hide(ps, headX, headY)
        hint = "Augen zu! $petName versteckt sich … 🙈"
        setPhase(Phase.HIDING, t)
    }

    /** Called every frame from the scene. */
    fun update(t: Float, d: PetDirector, ps: ParticleSystem, headX: Float, headY: Float, m: SceneModel) {
        val dt = if (lastT < 0f) 0f else (t - lastT).coerceIn(0f, 0.1f)
        lastT = t
        if (finished) {
            m.hideSpots = emptyList()
            return
        }
        when (phase) {
            Phase.START -> {
                when (game) {
                    RoomGame.HIDE -> startHideRound(d, ps, headX, headY, t)
                    RoomGame.TAG -> {
                        d.startFlee()
                        hint = "Tippe $petName an, so oft du kannst!"
                        setPhase(Phase.RUNNING, t)
                    }
                    RoomGame.FETCH -> {
                        d.onBallCatch = { air ->
                            score += if (air) 2 else 1
                            airStreak = if (air) airStreak + 1 else 0
                            if (airStreak >= 10) juggled = true
                        }
                        d.startFetch()
                        hint = "Tippe ins Zimmer, um den Ball zu werfen!"
                        setPhase(Phase.RUNNING, t)
                    }
                }
            }
            Phase.HIDING -> if (t - phaseAt > 1.8f) {
                hint = "Wo ist $petName? Tippe auf ein ❓"
                setPhase(Phase.SEEKING, t)
            }
            Phase.SEEKING -> if (!d.inRoomGame) finish(d)
            Phase.FOUND -> if (t - phaseAt > 2.2f) {
                if (round >= RoomGames.HIDE_ROUNDS) {
                    finish(d)
                } else {
                    round++
                    startHideRound(d, ps, headX, headY, t)
                }
            }
            Phase.RUNNING -> {
                if (!d.inRoomGame && t - phaseAt > 0.5f) {
                    finish(d)
                } else {
                    timeLeft -= dt
                    val s = timeLeft.toInt().coerceAtLeast(0)
                    if (s != secondsLeft) secondsLeft = s
                    if (timeLeft <= 0f) finish(d)
                }
            }
            Phase.DONE -> Unit
        }
        m.hideSpots = if (game == RoomGame.HIDE && (phase == Phase.HIDING || phase == Phase.SEEKING)) {
            spots.map { it to if (it in wrong) 0f else 1f }
        } else {
            emptyList()
        }
    }

    /** A tap in the room. Returns true if the game used it. */
    fun tap(nx: Float, ny: Float, onPet: Boolean, d: PetDirector, ps: ParticleSystem, headX: Float, headY: Float): Boolean {
        if (finished) return false
        when (game) {
            RoomGame.HIDE -> {
                if (phase != Phase.SEEKING) return true
                val spot = spots.filter { it !in wrong }.minByOrNull { hypot(it.x - nx, it.y - ny) } ?: return true
                if (hypot(spot.x - nx, spot.y - ny) > 0.16f) return true
                if (spot == target) {
                    score += (3 - wrong.size).coerceAtLeast(1)
                    d.reveal(spot.x, ps, spot.y)
                    hint = if (wrong.isEmpty()) "Sofort gefunden! ⭐" else "Gefunden! 🎉"
                    setPhase(Phase.FOUND, lastT)
                } else {
                    wrong += spot
                    ps.burst(de.knuffi.app.render.PKind.SMOKE, spot.x, spot.y, 5, 0.12f, 0.6f, 0.03f, intArrayOf(0x88FFFFFF.toInt()), gravity = -0.1f)
                    hint = listOf("Hier nicht! 🙈", "Leer! Weitersuchen 👀", "Kalt! 🥶").random(rnd)
                }
                return true
            }
            RoomGame.TAG -> {
                if (onPet && d.caught(ps, headX, headY)) score++
                return true
            }
            RoomGame.FETCH -> {
                if (onPet) return false
                d.throwBall(nx)
                return true
            }
        }
    }

    /** Ends the game early (the exit button). */
    fun stop(d: PetDirector) {
        if (!finished) finish(d)
    }

    private fun finish(d: PetDirector) {
        phase = Phase.DONE
        d.onBallCatch = null
        d.stopRoomGame()
        hint = null
        finished = true
    }
}
