package de.knuffi.app.render.house3d

import de.knuffi.core.DoorSide
import de.knuffi.core.RoomKind
import de.knuffi.core.Season
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** Something built into a room (kitchen, bathtub, piano …). [tag] is what a tap on it opens. */
class Fixture(
    val tag: String,
    val model: Model3,
    val x: Float,
    val z: Float,
    val turn: Float = 0f,
    val blocks: Rect2? = null,
    /** Hangs on this wall (hidden with the wall in the house view). */
    val wall: DoorSide? = null,
)

/** The fixtures of every room of the house and the garden. */
object Fixtures3 {
    private fun mat(c: Long, gloss: Float = 0.2f, emissive: Float = 0f, alpha: Float = 1f, shadow: Boolean = true) =
        Mat(col(c), gloss, emissive = emissive, alpha = alpha, shadow = shadow)

    private val white = mat(0xFFFFFFFF, 0.35f)
    private val metal = mat(0xFFC8D0DA, 0.7f)
    private val wood = mat(0xFFD9985E, 0.15f)
    private val woodDark = mat(0xFFA8683A, 0.15f)
    private val gold = mat(0xFFFFC83D, 0.7f)

    private fun model(build: (ModelBuilder) -> Unit): Model3 = ModelBuilder().also(build).build()

    /** A model made smaller (garden things next to the pet). */
    private fun small(m: Model3, k: Float): Model3 = ModelBuilder().also { b ->
        b.scale(k)
        b.add(m)
    }.build()

    fun of(kind: RoomKind?, w: Float, d: Float, spec: RoomSpec, doors: List<DoorPlace>): List<Fixture> {
        val back = -d / 2f
        val hw = w / 2f
        return when (kind) {
            RoomKind.KITCHEN -> listOf(
                Fixture("kitchen", fridge(), -hw + 0.27f, back + 0.27f, blocks = Rect2(-hw, back, -hw + 0.54f, back + 0.56f)),
                Fixture("kitchen", counter(), -0.2f, back + 0.26f, blocks = Rect2(-0.68f, back, 0.28f, back + 0.54f)),
                Fixture("cabinets", cabinets(), -0.2f, back, wall = DoorSide.NORTH),
            )
            RoomKind.BATH -> listOf(
                Fixture("tub", tub(), -0.6f, back + 0.33f, blocks = Rect2(-hw, back, 0f, back + 0.68f)),
                Fixture("sink", sink(), 0.72f, back + 0.2f, blocks = Rect2(0.42f, back, 1.02f, back + 0.42f)),
                Fixture("mirror", mirror(), 0.72f, back, wall = DoorSide.NORTH),
            )
            RoomKind.BEDROOM -> listOf(
                Fixture("nightstand", nightstand(), -0.42f, back + 0.18f, blocks = Rect2(-0.6f, back, -0.24f, back + 0.36f)),
                Fixture("garland", garland(w), 0f, back, wall = DoorSide.NORTH),
            )
            RoomKind.PLAYROOM -> listOf(
                Fixture("slide", slide(), -hw + 0.32f, -0.55f, blocks = Rect2(-hw, -1.2f, -hw + 0.62f, 0.15f)),
                Fixture("blocks", blocks(), -0.25f, back + 0.25f, blocks = Rect2(-0.48f, back, -0.02f, back + 0.42f)),
                Fixture("bunting", bunting(w), 0f, back, wall = DoorSide.NORTH),
            )
            RoomKind.MUSIC -> listOf(
                Fixture("piano", Furniture3.piece("deco_piano").model, -0.55f, back + 0.36f, blocks = Rect2(-1.12f, back, 0.02f, back + 0.66f)),
                Fixture("piano", drums(), 0.62f, back + 0.42f, blocks = Rect2(0.22f, back, hw, back + 0.8f)),
                Fixture("notes", notes(), 0f, back, wall = DoorSide.NORTH),
            )
            RoomKind.CRAFT -> listOf(
                Fixture("cards", cardTable(), -0.48f, back + 0.62f, blocks = Rect2(-1.0f, back + 0.25f, 0.04f, back + 1.0f)),
                Fixture("pinboard", pinboard(), -0.5f, back, wall = DoorSide.NORTH),
            )
            RoomKind.GARDEN -> garden(w, d, spec)
            else -> emptyList()
        }
    }

    // ------------------------------------------------------------------ kitchen

    private fun fridge() = model { b ->
        val body = mat(0xFFE8F4FF, 0.55f)
        b.block(body, 0f, 0f, 0f, 0.5f, 1.3f, 0.5f, 0.06f)
        b.block(mat(0xFFD6E8F8, 0.5f), 0f, 0.86f, 0.25f, 0.46f, 0.012f, 0.012f, 0.004f)
        for (y in floatArrayOf(0.55f, 1.0f)) b.block(metal, 0.17f, y, 0.27f, 0.035f, 0.22f, 0.03f, 0.012f)
        b.block(mat(0xFFFF8FB8, 0.3f), -0.1f, 1.05f, 0.255f, 0.1f, 0.1f, 0.01f, 0.01f)
        b.block(mat(0xFFFFD86A, 0.3f), -0.05f, 0.62f, 0.255f, 0.08f, 0.12f, 0.01f, 0.01f)
    }

    private fun counter() = model { b ->
        b.block(mat(0xFF8FD3C8, 0.3f), 0f, 0.04f, 0f, 0.94f, 0.56f, 0.5f, 0.03f)
        b.block(mat(0xFF6AB8AC, 0.2f), 0f, 0f, 0.02f, 0.9f, 0.05f, 0.46f, 0.01f)
        b.block(mat(0xFFFFFFFF, 0.5f), 0f, 0.6f, 0f, 0.98f, 0.05f, 0.54f, 0.015f)
        for (x in floatArrayOf(-0.23f, 0.23f)) {
            b.block(mat(0xFFA6E0D6, 0.3f), x, 0.12f, 0.255f, 0.4f, 0.4f, 0.01f, 0.01f)
            b.block(metal, x, 0.48f, 0.27f, 0.12f, 0.02f, 0.02f, 0.008f)
        }
        // hob with two rings and a pot
        b.block(mat(0xFF3A3448, 0.5f), 0.22f, 0.65f, 0f, 0.4f, 0.01f, 0.36f, 0.004f)
        for (x in floatArrayOf(0.13f, 0.31f)) b.at(x, 0.662f, 0f) { with(mat(0xFFFF6A4A, 0.3f, emissive = 0.3f)) { torus(0.06f, 0.008f, 20, 4) } }
        b.at(0.13f, 0.66f, 0f) { with(mat(0xFFFF8FB8, 0.5f)) { cyl(0.08f, 0.08f, 0f, 0.13f, 18) } }
        b.at(0.13f, 0.79f, 0f) { with(mat(0xFFFFB3CF, 0.5f)) { cyl(0.085f, 0.06f, 0f, 0.02f, 18) } }
        // sink with tap
        b.block(mat(0xFFD0D8E0, 0.6f), -0.24f, 0.62f, 0f, 0.34f, 0.04f, 0.3f, 0.02f)
        b.with(metal) { tube(listOf(Triple(-0.24f, 0.65f, -0.16f), Triple(-0.24f, 0.86f, -0.16f), Triple(-0.24f, 0.88f, -0.08f), Triple(-0.24f, 0.8f, -0.04f)), 0.015f, 8) }
    }

    private fun cabinets() = model { b ->
        b.block(mat(0xFF8FD3C8, 0.3f), 0f, 1.38f, 0.17f, 0.94f, 0.45f, 0.32f, 0.03f)
        for (x in floatArrayOf(-0.23f, 0.23f)) {
            b.block(mat(0xFFA6E0D6, 0.3f), x, 1.42f, 0.335f, 0.42f, 0.37f, 0.01f, 0.01f)
            b.block(metal, x + if (x < 0) 0.15f else -0.15f, 1.48f, 0.345f, 0.02f, 0.1f, 0.02f, 0.008f)
        }
    }

    // ------------------------------------------------------------------ bath

    private fun tub() = model { b ->
        val enamel = mat(0xFFFFFFFF, 0.6f)
        b.at(0f, 0.08f, 0f) {
            scale(1f, 1f, 0.55f)
            with(enamel) { lathe(listOf(0.42f to 0f, 0.5f to 0.15f, 0.54f to 0.4f, 0.56f to 0.42f), 32, capBottom = true) }
        }
        b.at(0f, 0.08f, 0f) {
            scale(1.0f, 1f, 0.55f)
            with(mat(0xFF7FD4FF, 0.8f, alpha = 0.75f, shadow = false)) { disc(0.52f, 0.3f, 32) }
        }
        for ((x, z) in listOf(-0.36f to -0.18f, 0.36f to -0.18f, -0.36f to 0.18f, 0.36f to 0.18f)) b.sphere(gold, x, 0.05f, z, 0.06f)
        for (k in 0 until 6) b.sphere(mat(0xFFFFFFFF, 0.2f), -0.3f + k * 0.12f, 0.42f, -0.05f + (k % 2) * 0.1f, 0.07f)
        b.sphere(mat(0xFFFFD84D, 0.4f), 0.22f, 0.42f, 0.05f, 0.06f, sy = 0.8f)
        b.sphere(mat(0xFFFFD84D, 0.4f), 0.27f, 0.47f, 0.05f, 0.035f)
        b.with(metal) { tube(listOf(Triple(-0.5f, 0.42f, 0f), Triple(-0.5f, 0.62f, 0f), Triple(-0.42f, 0.66f, 0f)), 0.018f, 8) }
    }

    private fun sink() = model { b ->
        b.block(mat(0xFF7FD4FF, 0.3f), 0f, 0f, 0f, 0.5f, 0.6f, 0.36f, 0.04f)
        b.block(mat(0xFFFFFFFF, 0.6f), 0f, 0.6f, 0f, 0.56f, 0.06f, 0.4f, 0.03f)
        b.with(metal) { tube(listOf(Triple(0f, 0.66f, -0.14f), Triple(0f, 0.8f, -0.14f), Triple(0f, 0.8f, -0.05f)), 0.015f, 8) }
        b.with(mat(0xFFFF8FB8, 0.2f)) { tube(listOf(Triple(0.3f, 0.5f, -0.1f), Triple(0.3f, 0.3f, 0f), Triple(0.3f, 0.5f, 0.1f)), 0.03f, 8) }
    }

    private fun mirror() = model { b ->
        b.at(0f, 1.25f, 0.02f) {
            scale(1f, 1.25f, 1f)
            rotX(90f)
            with(mat(0xFFFFD86A, 0.6f)) { torus(0.24f, 0.03f, 32, 8) }
            with(mat(0xFFE8F6FF, 0.95f)) { disc(0.24f, 0f, 32) }
        }
    }

    // ------------------------------------------------------------------ bedroom

    private fun nightstand() = model { b ->
        b.block(mat(0xFFE9B384, 0.2f), 0f, 0f, 0f, 0.34f, 0.42f, 0.32f, 0.04f)
        b.block(mat(0xFFD9985E, 0.2f), 0f, 0.2f, 0.16f, 0.28f, 0.14f, 0.01f, 0.01f)
        b.sphere(gold, 0f, 0.27f, 0.175f, 0.02f)
        b.cyl(mat(0xFF7C6CFF, 0.4f), 0f, 0f, 0.06f, 0.05f, 0.42f, 0.5f, 14)
        b.at(0f, 0.5f, 0f) { with(mat(0xFFFFF0B0, 0.2f, emissive = 0.8f)) { cyl(0.11f, 0.07f, 0.04f, 0.18f, 18) } }
        b.at(-0.08f, 0.42f, 0.05f) { with(mat(0xFFFF8FB8, 0.3f)) { rbox(-0.06f, 0f, -0.04f, 0.06f, 0.025f, 0.04f, 0.008f) } }
    }

    private fun garland(w: Float) = model { b ->
        val n = 11
        val pts = (0..n).map { i ->
            val t = i / n.toFloat()
            Triple(-w / 2f + 0.15f + (w - 0.3f) * t, 2.3f - 0.18f * sin(t * PI.toFloat() * 2f).let { it * it }, 0.03f)
        }
        b.with(mat(0xFF4A8A4A, 0.2f, shadow = false)) { tube(pts, 0.008f, 4) }
        val colors = longArrayOf(0xFFFF6B9A, 0xFFFFD84D, 0xFF7DD98A, 0xFF6EC6FF, 0xFFB28CFF)
        for ((i, p) in pts.withIndex()) if (i in 1 until n) b.sphere(mat(colors[i % 5], 0.4f, emissive = 0.85f, shadow = false), p.first, p.second - 0.05f, 0.04f, 0.035f, sy = 1.3f)
    }

    // ------------------------------------------------------------------ playroom

    private fun slide() = model { b ->
        val frame = mat(0xFF4DA8FF, 0.4f)
        // ladder at the back, platform, chute towards the front
        for (x in floatArrayOf(-0.22f, 0.22f)) {
            b.with(frame) { tube(listOf(Triple(x, 0f, -0.62f), Triple(x, 0.9f, -0.48f)), 0.025f, 8) }
            b.with(frame) { tube(listOf(Triple(x, 0f, -0.25f), Triple(x, 0.86f, -0.25f)), 0.025f, 8) }
        }
        for (k in 1..4) b.with(mat(0xFFFFD84D, 0.3f)) { tube(listOf(Triple(-0.22f, k * 0.18f, -0.62f + k * 0.028f), Triple(0.22f, k * 0.18f, -0.62f + k * 0.028f)), 0.018f, 6) }
        b.block(mat(0xFFFF6B9A, 0.3f), 0f, 0.86f, -0.37f, 0.5f, 0.05f, 0.3f, 0.02f)
        val chute = mat(0xFFFFD84D, 0.6f)
        b.with(chute) {
            val n = 10
            val base = vertex(0f, 0f, 0f, 0f, 1f, 0f)
            for (i in 0..n) {
                val t = i / n.toFloat()
                val z = -0.22f + 0.85f * t
                val y = 0.88f - 0.82f * (t * t * (3f - 2f * t))
                for (s in 0..6) {
                    val a = PI.toFloat() * s / 6f
                    vertex(-cos(a) * 0.2f, y - sin(a) * 0.06f, z, 0f, 1f, 0f)
                }
            }
            for (i in 0 until n) for (s in 0 until 6) {
                val a = base + 1 + i * 7 + s
                quadIdx(a, a + 7, a + 8, a + 1)
            }
        }
    }

    private fun blocks() = model { b ->
        val colors = longArrayOf(0xFFFF6B6B, 0xFF4DA8FF, 0xFFFFD84D, 0xFF7DD98A, 0xFFB28CFF)
        val stack = listOf(Triple(-0.1f, 0f, 0f), Triple(0.07f, 0f, 0.02f), Triple(-0.02f, 0.13f, 0.01f), Triple(0.15f, 0f, -0.12f), Triple(-0.15f, 0f, -0.12f), Triple(0.0f, 0.26f, 0.0f))
        for ((i, p) in stack.withIndex()) b.at(p.first, p.second, p.third, rotY = i * 17f) { block(mat(colors[i % 5], 0.3f), 0f, 0f, 0f, 0.13f, 0.13f, 0.13f, 0.02f) }
        b.at(0.12f, 0.13f, 0.02f) { with(mat(0xFFFF8A3D, 0.3f)) { cyl(0.065f, 0f, 0f, 0.13f, 4) } }
    }

    private fun bunting(w: Float) = model { b ->
        val colors = longArrayOf(0xFFFF6B9A, 0xFFFFD84D, 0xFF7DD98A, 0xFF6EC6FF, 0xFFB28CFF, 0xFFFF8A3D)
        val n = 9
        for (i in 0 until n) {
            val t0 = i / n.toFloat()
            val t1 = (i + 1) / n.toFloat()
            val x0 = -w / 2f + 0.12f + (w - 0.24f) * t0
            val x1 = -w / 2f + 0.12f + (w - 0.24f) * t1
            val y0 = 2.35f - 0.15f * sin(t0 * PI.toFloat())
            val y1 = 2.35f - 0.15f * sin(t1 * PI.toFloat())
            val m = mat(colors[i % 6], 0.2f, shadow = false)
            b.with(m) { quad(x0 + 0.02f, y0, 0.03f, x1 - 0.02f, y1, 0.03f, (x0 + x1) / 2f, (y0 + y1) / 2f - 0.24f, 0.03f, (x0 + x1) / 2f, (y0 + y1) / 2f - 0.24f, 0.03f) }
        }
        b.with(mat(0xFFFFFFFF, 0.2f, shadow = false)) { tube((0..12).map { k -> val t = k / 12f; Triple(-w / 2f + 0.12f + (w - 0.24f) * t, 2.35f - 0.15f * sin(t * PI.toFloat()), 0.03f) }, 0.006f, 4) }
    }

    // ------------------------------------------------------------------ music

    private fun drums() = model { b ->
        val shell = mat(0xFFFF5A7A, 0.6f)
        val skin = mat(0xFFFFF8F0, 0.2f)
        b.at(0f, 0.25f, 0f) {
            rotX(90f)
            with(shell) { cyl(0.24f, 0.24f, -0.14f, 0.14f, 26, capBottom = false, capTop = false) }
            with(skin) { cyl(0.235f, 0.235f, -0.141f, 0.141f, 26) }
            with(gold) { at(0f, 0.14f, 0f) { torus(0.24f, 0.012f, 26, 6) } }
        }
        b.at(0.32f, 0.42f, 0.05f) {
            with(shell) { cyl(0.13f, 0.13f, 0f, 0.12f, 20, capTop = false) }
            with(skin) { disc(0.128f, 0.121f, 20) }
        }
        b.cyl(metal, 0.32f, 0.05f, 0.015f, 0.015f, 0f, 0.42f, 6)
        b.cyl(metal, -0.3f, -0.05f, 0.012f, 0.012f, 0f, 0.82f, 6)
        b.at(-0.3f, 0.82f, -0.05f) { rotZ(8f); with(gold) { cyl(0.2f, 0.015f, 0f, 0.03f, 24) } }
        for (x in floatArrayOf(0.12f, 0.2f)) b.with(woodDark) { tube(listOf(Triple(x, 0.55f, 0.22f), Triple(x + 0.08f, 0.58f, 0.02f)), 0.01f, 6) }
    }

    private fun notes() = model { b ->
        for ((x, y) in listOf(-0.95f to 2.0f, 0.95f to 2.2f, 0.3f to 2.35f)) {
            val m = mat(0xFF7C6CFF, 0.3f, shadow = false)
            b.sphere(m, x, y, 0.03f, 0.06f, sy = 0.75f, sz = 0.4f)
            b.box(m, x + 0.045f, y, 0.02f, x + 0.065f, y + 0.24f, 0.04f)
            b.box(m, x + 0.045f, y + 0.2f, 0.02f, x + 0.15f, y + 0.24f, 0.04f)
        }
    }

    // ------------------------------------------------------------------ craft room

    private fun cardTable() = model { b ->
        b.block(mat(0xFFE9B384, 0.25f), 0f, 0.42f, 0f, 0.92f, 0.05f, 0.62f, 0.03f)
        for (x in floatArrayOf(-0.4f, 0.4f)) for (z in floatArrayOf(-0.25f, 0.25f)) b.cyl(wood, x, z, 0.03f, 0.025f, 0f, 0.42f, 10)
        val colors = longArrayOf(0xFFFF8FB8, 0xFF7FD4FF, 0xFFFFD86A, 0xFFB28CFF, 0xFF7DD98A)
        for (k in 0 until 5) b.at(-0.28f + k * 0.14f, 0.475f, -0.02f + (k % 2) * 0.06f, rotY = -12f + k * 7f) { block(mat(colors[k], 0.3f), 0f, 0f, 0f, 0.1f, 0.008f, 0.14f, 0.003f) }
        b.at(0.3f, 0.475f, 0.14f) { block(mat(0xFF7C6CFF, 0.3f), 0f, 0f, 0f, 0.11f, 0.05f, 0.15f, 0.008f) }
        for (x in floatArrayOf(-0.25f, 0.25f)) {
            b.cyl(mat(0xFFFF8FB8, 0.25f), x, 0.45f, 0.13f, 0.13f, 0.26f, 0.3f, 18)
            b.cyl(wood, x, 0.45f, 0.02f, 0.02f, 0f, 0.26f, 8)
        }
    }

    private fun pinboard() = model { b ->
        b.block(mat(0xFFC8925A, 0.1f), 0f, 1.05f, 0.02f, 0.9f, 0.66f, 0.04f, 0.02f)
        b.block(mat(0xFFE2B98A, 0.05f), 0f, 1.09f, 0.03f, 0.82f, 0.58f, 0.025f, 0.01f)
        val colors = longArrayOf(0xFFFF8FB8, 0xFF7FD4FF, 0xFFFFD86A, 0xFFB28CFF, 0xFF7DD98A, 0xFFFF8A3D)
        for (k in 0 until 6) {
            val x = -0.28f + (k % 3) * 0.28f
            val y = 1.18f + (k / 3) * 0.26f
            b.at(x, y, 0.045f) {
                rotZ(-8f + k * 5f)
                block(mat(colors[k], 0.25f), 0f, 0f, 0f, 0.15f, 0.2f, 0.006f, 0.004f)
                sphere(mat(0xFFFF4A4A, 0.5f), 0f, 0.18f, 0.01f, 0.018f)
            }
        }
    }

    // ------------------------------------------------------------------ garden

    private fun garden(w: Float, d: Float, spec: RoomSpec): List<Fixture> {
        val back = -d / 2f
        val hw = w / 2f
        val out = ArrayList<Fixture>()
        out += Fixture("tree", small(tree(spec.season), 0.85f), -hw + 0.5f, back + 0.35f, blocks = Rect2(-hw, back, -hw + 0.9f, back + 0.75f))
        out += Fixture("swing", small(swing(), 0.75f), -hw + 1.0f, 0.35f, blocks = Rect2(-hw + 0.6f, 0.1f, -hw + 1.4f, 0.6f))
        out += Fixture("pool", small(pool(spec.season), 0.8f), hw - 1.2f, 0.42f, blocks = Rect2(hw - 1.7f, -0.05f, hw - 0.7f, 0.88f))
        val beds = listOf(0.2f, 0.77f, 1.34f)
        for ((i, x) in beds.withIndex()) {
            val plants = spec.plants.drop(i * 2).take(2)
            out += Fixture("beds", bed(plants, spec.season), x, back + 0.33f, blocks = Rect2(x - 0.26f, back, x + 0.26f, back + 0.6f))
        }
        return out
    }

    private fun tree(season: Season?) = model { b ->
        val bark = mat(0xFF8A5A3A, 0.1f)
        b.with(bark) { tube(listOf(Triple(0f, 0f, 0f), Triple(0.03f, 0.7f, 0f), Triple(-0.02f, 1.4f, 0f)), 0.12f, 12) }
        b.with(bark) { tube(listOf(Triple(0f, 1.0f, 0f), Triple(0.3f, 1.35f, 0.05f)), 0.05f, 8) }
        val (c1, c2) = when (season) {
            Season.AUTUMN -> 0xFFF2A03D to 0xFFD8642A
            Season.WINTER -> 0xFFF4F8FF to 0xFFD8E4F2
            Season.SPRING -> 0xFF8ED98A to 0xFF5CC26E
            else -> 0xFF6ACF6A to 0xFF3E9A55
        }
        for ((p, r) in listOf(Triple(0f, 1.85f, 0f) to 0.55f, Triple(0.35f, 1.6f, 0.1f) to 0.4f, Triple(-0.35f, 1.62f, -0.05f) to 0.42f, Triple(0.05f, 2.2f, -0.05f) to 0.38f))
            b.sphere(mat(if (r > 0.45f) c2 else c1, 0.25f), p.first, p.second, p.third, r)
        if (season != Season.WINTER) {
            val fruit = if (season == Season.AUTUMN) 0xFFE8503E else 0xFFFF5A6E
            for ((x, y, z) in listOf(Triple(-0.3f, 1.55f, 0.38f), Triple(0.25f, 1.75f, 0.42f), Triple(0.45f, 1.45f, 0.3f), Triple(-0.1f, 2.0f, 0.45f)))
                b.sphere(mat(fruit, 0.5f), x, y, z, 0.06f)
        }
    }

    private fun swing() = model { b ->
        val frame = mat(0xFFE8B070, 0.2f)
        for (x in floatArrayOf(-0.45f, 0.45f)) {
            b.with(frame) { tube(listOf(Triple(x, 0f, -0.25f), Triple(x, 1.3f, 0f)), 0.035f, 8) }
            b.with(frame) { tube(listOf(Triple(x, 0f, 0.25f), Triple(x, 1.3f, 0f)), 0.035f, 8) }
        }
        b.with(frame) { tube(listOf(Triple(-0.5f, 1.3f, 0f), Triple(0.5f, 1.3f, 0f)), 0.04f, 8) }
        for (x in floatArrayOf(-0.16f, 0.16f)) b.with(mat(0xFFF4E2C0, 0.1f)) { tube(listOf(Triple(x, 1.28f, 0f), Triple(x, 0.42f, 0f)), 0.01f, 5) }
        b.block(mat(0xFFFF6B9A, 0.3f), 0f, 0.38f, 0f, 0.42f, 0.05f, 0.2f, 0.02f)
    }

    private fun pool(season: Season?) = model { b ->
        val frozen = season == Season.WINTER
        b.at(0f, 0f, 0f) {
            scale(1f, 1f, 0.8f)
            with(mat(0xFF6EC6FF, 0.4f)) { torus(0.55f, 0.1f, 36, 12) }
            at(0f, 0.1f, 0f) { with(mat(0xFFFFFFFF, 0.4f)) { torus(0.55f, 0.03f, 36, 6, 1f) } }
            with(mat(if (frozen) 0xFFE8F4FF else 0xFF5AC8FF, 0.9f, alpha = if (frozen) 1f else 0.82f, shadow = false)) { disc(0.55f, 0.07f, 36) }
        }
        if (!frozen) {
            b.sphere(mat(0xFFFFD84D, 0.4f), 0.12f, 0.12f, 0.05f, 0.07f, sy = 0.8f)
            b.sphere(mat(0xFFFFD84D, 0.4f), 0.17f, 0.18f, 0.05f, 0.04f)
            b.at(-0.2f, 0.09f, -0.1f) { with(mat(0xFFFF6B9A, 0.3f)) { torus(0.1f, 0.035f, 20, 8) } }
        }
    }

    /** A raised garden bed with up to two plants ("emoji|growth|ready"). */
    private fun bed(plants: List<String>, season: Season?) = model { b ->
        b.block(mat(0xFFC8925A, 0.15f), 0f, 0f, 0f, 0.5f, 0.2f, 0.5f, 0.03f)
        b.box(mat(if (season == Season.WINTER) 0xFFF4F8FF else 0xFF6A4632, 0.05f), -0.22f, 0.15f, -0.22f, 0.22f, 0.205f, 0.22f)
        for ((i, pl) in plants.withIndex()) {
            if (pl.isEmpty()) continue
            val parts = pl.split("|")
            val emoji = parts.getOrNull(0) ?: continue
            val growth = parts.getOrNull(1)?.toFloatOrNull() ?: 0.5f
            val x = if (i == 0) -0.11f else 0.11f
            if (growth < 0.5f) {
                // a little sprout
                b.with(mat(0xFF7ED957, 0.3f)) { tube(listOf(Triple(x, 0.2f, 0f), Triple(x, 0.2f + 0.1f + growth * 0.2f, 0f)), 0.012f, 5) }
                b.sphere(mat(0xFF7ED957, 0.3f), x - 0.03f, 0.3f + growth * 0.2f, 0f, 0.035f, sy = 0.5f)
                b.sphere(mat(0xFF7ED957, 0.3f), x + 0.03f, 0.3f + growth * 0.2f, 0f, 0.035f, sy = 0.5f)
            } else {
                val s = 0.14f + growth * 0.1f
                // two crossed pictures of the plant, seen from every side
                b.with(Mat(col(0xFFFFFFFF), 0.1f, tex = "emoji|$emoji", texMode = 2)) {
                    quad(x - s, 0.2f, 0f, x + s, 0.2f, 0f, x + s, 0.2f + 2 * s, 0f, x - s, 0.2f + 2 * s, 0f)
                    quad(x, 0.2f, -s, x, 0.2f, s, x, 0.2f + 2 * s, s, x, 0.2f + 2 * s, -s)
                }
            }
        }
    }
}
