package de.knuffi.app.render.house3d

import de.knuffi.core.Slot
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * A piece of furniture in 3D: its model, the space it takes on the floor ([w] × [d], before
 * turning) and what kind of place it needs.
 */
class Piece(
    val id: String,
    val model: Model3,
    val w: Float,
    val d: Float,
    /** Hangs on the back wall (pictures). */
    val onWall: Boolean = false,
    /** Lies flat on the floor, other furniture may stand on it (rugs). */
    val flat: Boolean = false,
    /** Height of the lying surface (beds) in units. */
    val sleepY: Float = 0f,
    /** Where a lamp's light comes from (model coordinates). */
    val glow: FloatArray? = null,
)

internal fun col(v: Long): Int = v.toInt()

/** Every piece of furniture of the shop as a 3D model. One unit is about the size of a pet. */
object Furniture3 {
    private val cache = HashMap<String, Piece>()

    fun piece(id: String): Piece = synchronized(cache) { cache.getOrPut(id) { make(id) } }

    /** The slot a furniture id belongs to (by its prefix). */
    fun slotOf(id: String): Slot? = when {
        id.startsWith("bed_") -> Slot.BED
        id.startsWith("plant_") -> Slot.PLANT
        id.startsWith("lamp_") -> Slot.LAMP
        id.startsWith("pic_") -> Slot.PICTURE
        id.startsWith("rug_") -> Slot.RUG
        id.startsWith("toy_") -> Slot.TOY
        id.startsWith("deco_") -> Slot.DECO
        else -> null
    }

    private fun mat(c: Long, gloss: Float = 0.2f, emissive: Float = 0f, alpha: Float = 1f, shadow: Boolean = true) =
        Mat(col(c), gloss, emissive = emissive, alpha = alpha, shadow = shadow)

    // common materials
    private val woodLight = mat(0xFFF0C28E, 0.15f)
    private val wood = mat(0xFFD9985E, 0.15f)
    private val woodDark = mat(0xFFA8683A, 0.15f)
    private val white = mat(0xFFFFFFFF, 0.25f)
    private val cream = mat(0xFFFFF4E0, 0.15f)
    private val terracotta = mat(0xFFE58A5C, 0.1f)
    private val soil = mat(0xFF6A4632, 0.05f)
    private val leaf = mat(0xFF5CC26E, 0.3f)
    private val leafDark = mat(0xFF3E9A55, 0.3f)
    private val gold = mat(0xFFFFC83D, 0.7f)
    private val metal = mat(0xFFB8C0CC, 0.6f)
    private val dark = mat(0xFF4A3F66, 0.3f)
    private val glass = mat(0xFFD8F0FF, 0.9f, alpha = 0.32f, shadow = false)

    private fun make(id: String): Piece = when (id) {
        // ------------------------------------------------------------- beds
        "bed_classic" -> bed(id) { b, bc ->
            // wooden frame with head and foot board
            b.block(wood, 0f, 0.05f, 0f, 1.24f, 0.13f, 0.7f, 0.04f)
            for (x in floatArrayOf(-0.56f, 0.56f)) for (z in floatArrayOf(-0.29f, 0.29f)) b.block(woodDark, x, 0f, z, 0.08f, 0.08f, 0.08f, 0.02f)
            b.block(wood, -0.6f, 0.05f, 0f, 0.08f, 0.62f, 0.72f, 0.04f)
            b.sphere(woodLight, -0.6f, 0.69f, -0.3f, 0.055f)
            b.sphere(woodLight, -0.6f, 0.69f, 0.3f, 0.055f)
            b.block(wood, 0.6f, 0.05f, 0f, 0.07f, 0.36f, 0.72f, 0.035f)
            mattress(b, 1.1f, 0.64f, 0.16f, bc)
        }
        "bed_cloud" -> bed(id) { b, bc ->
            val cloud = mat(0xFFFFFFFF, 0.1f)
            for ((x, z, r) in listOf(Triple(-0.45f, 0f, 0.3f), Triple(-0.12f, -0.12f, 0.32f), Triple(0.2f, 0.06f, 0.3f), Triple(0.47f, -0.05f, 0.27f), Triple(0.05f, 0.18f, 0.27f)))
                b.sphere(cloud, x, 0.14f, z, r, sy = 0.55f)
            b.sphere(cloud, -0.62f, 0.42f, -0.12f, 0.24f)
            b.sphere(cloud, -0.5f, 0.55f, 0.1f, 0.2f)
            mattress(b, 1.08f, 0.6f, 0.17f, bc, round = 0.12f)
        }
        "bed_race" -> bed(id) { b, bc ->
            val red = mat(0xFFE8303F, 0.6f)
            b.rbox(red, -0.64f, 0.08f, -0.36f, 0.64f, 0.3f, 0.36f, 0.08f)
            b.rbox(red, 0.42f, 0.08f, -0.36f, 0.7f, 0.42f, 0.36f, 0.1f)
            b.rbox(red, -0.7f, 0.08f, -0.36f, -0.5f, 0.56f, 0.36f, 0.08f)
            b.rbox(mat(0xFF222838, 0.4f), -0.66f, 0.38f, -0.2f, -0.6f, 0.5f, 0.2f, 0.02f)
            for (x in floatArrayOf(-0.42f, 0.42f)) for (z in floatArrayOf(-0.37f, 0.37f)) {
                b.at(x, 0.12f, z) {
                    rotX(90f)
                    with(mat(0xFF2A2A36, 0.2f)) { cyl(0.13f, 0.13f, -0.06f, 0.06f, 20) }
                    with(metal) { cyl(0.06f, 0.06f, -0.065f, 0.065f, 16) }
                }
            }
            b.sphere(mat(0xFFFFF2A0, 0.4f, emissive = 0.8f), 0.7f, 0.3f, -0.22f, 0.05f)
            b.sphere(mat(0xFFFFF2A0, 0.4f, emissive = 0.8f), 0.7f, 0.3f, 0.22f, 0.05f)
            mattress(b, 1.0f, 0.6f, 0.16f, bc)
        }
        "bed_canopy" -> bed(id) { b, bc ->
            val post = mat(0xFFFFFFFF, 0.3f)
            val pink = mat(0xFFFF9EC4, 0.2f)
            b.block(post, 0f, 0.05f, 0f, 1.24f, 0.13f, 0.72f, 0.04f)
            for (x in floatArrayOf(-0.58f, 0.58f)) for (z in floatArrayOf(-0.32f, 0.32f)) {
                b.cyl(post, x, z, 0.035f, 0.035f, 0f, 1.45f, 12)
                b.sphere(gold, x, 1.47f, z, 0.045f)
            }
            b.block(pink, 0f, 1.38f, 0f, 1.26f, 0.06f, 0.74f, 0.03f)
            // curtains, gathered at the posts
            val veil = mat(0xFFFFD6E8, 0.1f, alpha = 0.55f, shadow = false)
            for (x in floatArrayOf(-0.58f, 0.58f)) b.at(x, 0f, -0.32f) { with(veil) { cyl(0.09f, 0.05f, 0.3f, 1.38f, 14) } }
            b.block(veil, 0f, 0.5f, -0.35f, 1.1f, 0.88f, 0.02f, 0.01f)
            b.block(pink, -0.6f, 0.05f, 0f, 0.07f, 0.66f, 0.7f, 0.035f)
            mattress(b, 1.1f, 0.62f, 0.16f, bc)
        }
        "bed_basket" -> bed(id, w = 0.98f, d = 0.82f) { b, bc ->
            val wicker = mat(0xFFE2B070, 0.1f)
            b.at(0f, 0f, 0f) {
                scale(1f, 1f, 0.82f)
                with(wicker) {
                    lathe(listOf(0.38f to 0.02f, 0.44f to 0.08f, 0.47f to 0.22f, 0.48f to 0.3f), 32, capBottom = true)
                    at(0f, 0.3f, 0f) { torus(0.46f, 0.045f, 32, 10) }
                }
                for (k in 0 until 5) with(mat(0xFFC88E4A, 0.1f)) { at(0f, 0.08f + k * 0.045f, 0f) { torus(0.445f + k * 0.007f, 0.012f, 32, 6) } }
            }
            b.sphere(mat(bc[1].toLong() and 0xFFFFFFFFL, 0.15f), 0f, 0.22f, 0f, 0.42f, sy = 0.16f, sz = 0.8f)
            b.sphere(mat(bc[0].toLong() and 0xFFFFFFFFL, 0.15f), -0.24f, 0.29f, 0f, 0.14f, sy = 0.45f, sz = 1.3f)
        }
        "bed_hammock" -> bed(id, w = 1.5f, d = 0.7f) { b, bc ->
            for (x in floatArrayOf(-0.68f, 0.68f)) {
                b.cyl(wood, x, 0f, 0.045f, 0.04f, 0f, 0.95f, 12)
                b.sphere(woodLight, x, 0.96f, 0f, 0.05f)
                b.block(woodDark, x, 0f, 0f, 0.12f, 0.04f, 0.5f, 0.015f)
            }
            // the cloth: a curved strip that sags in the middle
            val cloth = mat(bc[1].toLong() and 0xFFFFFFFFL, 0.15f)
            b.with(cloth) {
                val n = 16
                val base = vertex(0f, 0f, 0f, 0f, 1f, 0f)
                for (i in 0..n) {
                    val t = i / n.toFloat()
                    val x = -0.6f + 1.2f * t
                    val y = 0.24f + 0.42f * (2f * t - 1f) * (2f * t - 1f)
                    for (side in 0..4) {
                        val s = side / 4f
                        val z = -0.3f + 0.6f * s
                        val dip = 0.05f * sin(s * PI.toFloat())
                        vertex(x, y - dip, z, 0f, 1f, 0f, t, s)
                    }
                }
                for (i in 0 until n) for (k in 0 until 4) {
                    val a = base + 1 + i * 5 + k
                    quadIdx(a, a + 5, a + 6, a + 1)
                }
            }
            for (x in floatArrayOf(-0.68f, 0.68f)) for (z in floatArrayOf(-0.28f, 0.28f))
                b.with(mat(0xFFF4E2C0, 0.1f)) { tube(listOf(Triple(x, 0.9f, 0f), Triple(x * 0.88f, 0.67f, z)), 0.012f, 6) }
            b.sphere(mat(bc[0].toLong() and 0xFFFFFFFFL, 0.1f), -0.42f, 0.42f, 0f, 0.14f, sy = 0.5f, sz = 1.4f)
        }

        // ------------------------------------------------------------- plants
        "plant_monstera" -> piece(id, 0.62f, 0.62f) { b ->
            pot(b, 0.2f, 0.36f, terracotta)
            val stems = listOf(-35f to 0.8f, 20f to 0.95f, 100f to 0.75f, 160f to 0.88f, 230f to 0.7f, 300f to 0.85f, 60f to 1.05f)
            for ((ang, len) in stems) {
                val a = ang * MeshBuilder.DEG
                val ex = cos(a) * 0.28f
                val ez = sin(a) * 0.22f + 0.04f
                b.with(leafDark) { tube(listOf(Triple(0f, 0.34f, 0f), Triple(ex * 0.5f, 0.34f + len * 0.6f, ez * 0.5f), Triple(ex, 0.3f + len, ez)), 0.012f, 6) }
                b.at(ex * 1.15f, 0.3f + len, ez * 1.15f) {
                    rotY(-ang)
                    rotZ(-28f)
                    with(if (ang.toInt() % 2 == 0) leaf else mat(0xFF4DB862, 0.3f)) { sphere(0.2f, 18, 10, 0.12f, 0.7f) }
                }
            }
        }
        "plant_cactus" -> piece(id, 0.5f, 0.5f) { b ->
            pot(b, 0.18f, 0.28f, mat(0xFFFF9E7A, 0.15f))
            val green = mat(0xFF5BC07A, 0.35f)
            b.at(0f, 0.26f, 0f) { with(green) { lathe(listOf(0.11f to 0f, 0.13f to 0.1f, 0.13f to 0.55f, 0.1f to 0.68f, 0.0f to 0.74f), 18) } }
            b.at(0.12f, 0.5f, 0f) {
                rotZ(-80f)
                with(green) { lathe(listOf(0.06f to 0f, 0.065f to 0.12f, 0f to 0.16f), 12) }
            }
            b.at(0.24f, 0.5f, 0f) { with(green) { lathe(listOf(0.06f to 0f, 0.065f to 0.18f, 0f to 0.23f), 12) } }
            b.at(-0.12f, 0.62f, 0f) {
                rotZ(80f)
                with(green) { lathe(listOf(0.055f to 0f, 0.06f to 0.1f, 0f to 0.14f), 12) }
            }
            b.at(-0.22f, 0.62f, 0f) { with(green) { lathe(listOf(0.055f to 0f, 0.06f to 0.13f, 0f to 0.17f), 12) } }
            for (k in 0 until 5) {
                val a = k * 72f * MeshBuilder.DEG
                b.sphere(mat(0xFFFF7EB6, 0.3f), cos(a) * 0.05f, 1.0f, sin(a) * 0.05f, 0.05f, sy = 0.6f)
            }
            b.sphere(mat(0xFFFFE16A, 0.3f), 0f, 1.01f, 0f, 0.035f)
        }
        "plant_sunflower" -> piece(id, 0.55f, 0.55f) { b ->
            pot(b, 0.18f, 0.3f, mat(0xFF7FB8FF, 0.2f))
            b.with(leafDark) { tube(listOf(Triple(0f, 0.28f, 0f), Triple(0.04f, 0.8f, 0.02f), Triple(0f, 1.3f, 0.04f)), 0.025f, 8) }
            for ((y, side) in listOf(0.55f to 1f, 0.8f to -1f)) b.at(side * 0.13f, y, 0f) {
                rotZ(side * -35f)
                with(leaf) { sphere(0.14f, 14, 8, 0.15f, 0.6f) }
            }
            b.at(0f, 1.36f, 0.06f) {
                rotX(70f)
                with(mat(0xFF8A5A2A, 0.1f)) { sphere(0.11f, 18, 10, 0.35f) }
                for (k in 0 until 14) {
                    val a = k / 14f * 360f
                    at(0f, 0f, 0f) {
                        rotY(a)
                        with(mat(0xFFFFC83D, 0.3f)) { at(0.17f, 0f, 0f) { sphere(0.075f, 10, 6, 0.18f, 0.42f) } }
                    }
                }
            }
        }
        "plant_bonsai" -> piece(id, 0.66f, 0.46f) { b ->
            b.block(mat(0xFF4A90C8, 0.4f), 0f, 0f, 0f, 0.6f, 0.14f, 0.38f, 0.04f)
            b.box(soil, -0.27f, 0.12f, -0.16f, 0.27f, 0.145f, 0.16f)
            val bark = mat(0xFF8A5A3A, 0.1f)
            b.with(bark) { tube(listOf(Triple(0f, 0.12f, 0f), Triple(0.08f, 0.3f, 0f), Triple(-0.06f, 0.48f, 0.02f), Triple(0.04f, 0.62f, 0f)), 0.04f, 8) }
            b.with(bark) { tube(listOf(Triple(-0.04f, 0.42f, 0f), Triple(-0.2f, 0.5f, 0.02f)), 0.025f, 6) }
            for ((x, y, r) in listOf(Triple(0.04f, 0.7f, 0.17f), Triple(-0.22f, 0.55f, 0.12f), Triple(0.2f, 0.58f, 0.11f), Triple(-0.06f, 0.78f, 0.12f)))
                b.sphere(if (r > 0.15f) leafDark else leaf, x, y, 0f, r, sy = 0.65f)
        }
        "plant_catgrass" -> piece(id, 0.44f, 0.44f) { b ->
            pot(b, 0.17f, 0.2f, mat(0xFFFFB3CF, 0.2f))
            val grass = mat(0xFF7ED957, 0.3f)
            for (k in 0 until 28) {
                val a = k * 137.5f * MeshBuilder.DEG
                val r = 0.03f + (k % 5) * 0.025f
                val x = cos(a) * r
                val z = sin(a) * r
                val h = 0.32f + (k * 7 % 5) * 0.04f
                b.with(if (k % 3 == 0) leafDark else grass) { tube(listOf(Triple(x, 0.19f, z), Triple(x * 1.4f, 0.19f + h * 0.6f, z * 1.4f), Triple(x * 2.2f, 0.19f + h, z * 2.2f)), 0.01f, 4) }
            }
        }
        "plant_tulips" -> piece(id, 0.52f, 0.46f) { b ->
            b.block(mat(0xFFFFFFFF, 0.3f), 0f, 0f, 0f, 0.46f, 0.22f, 0.3f, 0.06f)
            b.box(soil, -0.2f, 0.2f, -0.12f, 0.2f, 0.225f, 0.12f)
            val colors = longArrayOf(0xFFFF5A7A, 0xFFFFC83D, 0xFFFF8FB8, 0xFFB28CFF, 0xFFFF6B3D)
            for (k in 0 until 5) {
                val x = -0.16f + k * 0.08f
                val z = if (k % 2 == 0) -0.04f else 0.05f
                val h = 0.62f + (k % 3) * 0.08f
                b.with(leafDark) { tube(listOf(Triple(x, 0.2f, z), Triple(x + 0.02f, h - 0.05f, z)), 0.012f, 6) }
                b.at(x + 0.02f, h - 0.06f, z) { with(mat(colors[k], 0.35f)) { lathe(listOf(0.02f to 0f, 0.055f to 0.04f, 0.06f to 0.1f, 0.05f to 0.14f), 12, capBottom = true) } }
                b.at(x - 0.03f, 0.32f, z) {
                    rotZ(25f)
                    with(leaf) { sphere(0.08f, 10, 6, 1.8f, 0.3f) }
                }
            }
        }

        // ------------------------------------------------------------- lamps
        "lamp_floor" -> lamp(id, 0.4f, floatArrayOf(0f, 1.4f, 0f)) { b ->
            b.cyl(metal, 0f, 0f, 0.17f, 0.15f, 0f, 0.04f, 24)
            b.cyl(mat(0xFFC8A060, 0.5f), 0f, 0f, 0.018f, 0.018f, 0.04f, 1.4f, 10)
            b.at(0f, 1.32f, 0f) { with(mat(0xFFFFE8B8, 0.1f)) { cyl(0.24f, 0.14f, 0f, 0.26f, 28, capBottom = false, capTop = false) } }
            b.sphere(mat(0xFFFFF4C2, 0.2f, emissive = 0.95f), 0f, 1.38f, 0f, 0.07f)
        }
        "lamp_mushroom" -> lamp(id, 0.5f, floatArrayOf(0f, 0.55f, 0f)) { b ->
            b.at(0f, 0f, 0f) { with(mat(0xFFFFF4E8, 0.2f, emissive = 0.3f)) { lathe(listOf(0.12f to 0f, 0.1f to 0.15f, 0.08f to 0.4f, 0.09f to 0.5f), 18, capBottom = true) } }
            b.at(0f, 0.48f, 0f) { with(mat(0xFFFF6B9A, 0.3f, emissive = 0.55f)) { sphere(0.26f, 24, 12, 0.6f, 1f, 0.5f, 1f) } }
            for (k in 0 until 7) {
                val a = k * 51f * MeshBuilder.DEG
                val r = if (k == 0) 0f else 0.15f
                b.sphere(mat(0xFFFFFFFF, 0.2f, emissive = 0.6f), cos(a) * r, 0.48f + (if (k == 0) 0.155f else 0.115f), sin(a) * r, 0.035f, sy = 0.5f)
            }
        }
        "lamp_moon" -> lamp(id, 0.46f, floatArrayOf(0f, 0.75f, 0f)) { b ->
            b.cyl(dark, 0f, 0f, 0.16f, 0.14f, 0f, 0.05f, 20)
            b.cyl(gold, 0f, 0f, 0.015f, 0.015f, 0.05f, 0.42f, 8)
            b.at(0f, 0.75f, 0f) {
                rotX(90f)
                rotY(-100f)
                with(mat(0xFFFFF0B0, 0.3f, emissive = 0.85f)) { torus(0.24f, 0.09f, 26, 12, 0.58f) }
            }
            b.sphere(mat(0xFFFFF6C8, 0.3f, emissive = 0.9f), 0.2f, 0.98f, 0.02f, 0.035f)
        }
        "lamp_lava" -> lamp(id, 0.36f, floatArrayOf(0f, 0.5f, 0f)) { b ->
            b.at(0f, 0f, 0f) { with(mat(0xFF7A5ACF, 0.5f)) { lathe(listOf(0.14f to 0f, 0.1f to 0.18f, 0.08f to 0.2f), 20, capBottom = true) } }
            b.at(0f, 0.2f, 0f) { with(mat(0xFFFFB0D0, 0.8f, emissive = 0.4f, alpha = 0.55f, shadow = false)) { lathe(listOf(0.08f to 0f, 0.12f to 0.25f, 0.06f to 0.52f), 20, capTop = true) } }
            for ((y, r) in listOf(0.3f to 0.05f, 0.45f to 0.035f, 0.58f to 0.03f)) b.sphere(mat(0xFFFF6A3D, 0.4f, emissive = 0.9f), 0.01f, y, 0f, r)
            b.at(0f, 0.72f, 0f) { with(mat(0xFF7A5ACF, 0.5f)) { lathe(listOf(0.065f to 0f, 0.03f to 0.08f, 0f to 0.09f), 16) } }
        }
        "lamp_star" -> lamp(id, 0.44f, floatArrayOf(0f, 0.75f, 0f)) { b ->
            b.cyl(dark, 0f, 0f, 0.15f, 0.13f, 0f, 0.05f, 20)
            b.cyl(gold, 0f, 0f, 0.015f, 0.015f, 0.05f, 0.55f, 8)
            b.at(0f, 0.78f, 0f) {
                rotX(90f)
                with(mat(0xFFFFE066, 0.5f, emissive = 0.85f)) { extrude(MeshBuilder.star(5, 0.25f, 0.11f), -0.05f, 0.05f, bottom = true) }
            }
        }
        "lamp_lantern" -> lamp(id, 0.4f, floatArrayOf(0f, 0.85f, 0f)) { b ->
            b.cyl(woodDark, 0f, 0f, 0.15f, 0.13f, 0f, 0.05f, 20)
            b.cyl(woodDark, 0f, 0f, 0.016f, 0.016f, 0.05f, 1.12f, 8)
            b.with(woodDark) { tube(listOf(Triple(0f, 1.1f, 0f), Triple(0.12f, 1.14f, 0f), Triple(0.2f, 1.08f, 0f)), 0.012f, 6) }
            b.at(0.2f, 0.82f, 0f) {
                with(mat(0xFFFF5A4A, 0.2f, emissive = 0.75f)) { sphere(0.17f, 20, 12, 1.15f) }
                for (k in -2..2) with(mat(0xFFB8302A, 0.2f)) { at(0f, k * 0.06f, 0f) { torus(0.172f * cos(k * 0.33f), 0.006f, 24, 4) } }
                with(mat(0xFF3A2A28, 0.3f)) { cyl(0.07f, 0.07f, 0.17f, 0.23f, 14) }
                with(mat(0xFF3A2A28, 0.3f)) { cyl(0.07f, 0.07f, -0.23f, -0.17f, 14) }
                with(gold) { cyl(0.01f, 0.01f, -0.36f, -0.23f, 6) }
            }
        }

        // ------------------------------------------------------------- pictures (on the back wall)
        "pic_landscape", "pic_dragon", "pic_unicorn", "pic_rocket", "pic_heart", "pic_cat", "pic_sunset" -> {
            val b = ModelBuilder()
            val fw = 0.66f
            val fh = 0.48f
            val frame = mat(0xFFFFD86A, 0.6f)
            b.rbox(frame, -fw / 2f, -fh / 2f, 0f, fw / 2f, fh / 2f, 0.05f, 0.02f)
            val pad = 0.06f
            b.with(Mat(col(0xFFFFFFFF), 0.1f, tex = "pic:$id")) {
                quad(-fw / 2f + pad, -fh / 2f + pad, 0.052f, fw / 2f - pad, -fh / 2f + pad, 0.052f, fw / 2f - pad, fh / 2f - pad, 0.052f, -fw / 2f + pad, fh / 2f - pad, 0.052f)
            }
            Piece(id, b.build(), fw, 0.06f, onWall = true)
        }

        // ------------------------------------------------------------- rugs (flat, painted from above)
        "rug_round", "rug_rainbow", "rug_star", "rug_leaf", "rug_paw", "rug_heart" -> {
            val (rw, rd) = when (id) {
                "rug_rainbow" -> 1.35f to 0.82f
                "rug_star", "rug_heart", "rug_paw" -> 1.1f to 1.0f
                else -> 1.35f to 0.9f
            }
            val b = ModelBuilder()
            b.with(Mat(col(0xFFFFFFFF), 0.05f, tex = "rug:$id", texMode = 2, shadow = false)) {
                quad(-rw / 2f, 0.006f, rd / 2f, rw / 2f, 0.006f, rd / 2f, rw / 2f, 0.006f, -rd / 2f, -rw / 2f, 0.006f, -rd / 2f)
            }
            Piece(id, b.build(), rw, rd, flat = true)
        }

        else -> toyOrDeco(id)
    }

    private fun toyOrDeco(id: String): Piece = when (id) {
        // ------------------------------------------------------------- toys
        "toy_scratcher" -> piece(id, 0.66f, 0.56f) { b ->
            val carpet = mat(0xFFD8C8E8, 0.05f)
            val rope = mat(0xFFE8D0A0, 0.05f)
            b.block(carpet, 0f, 0f, 0f, 0.62f, 0.07f, 0.52f, 0.03f)
            b.cyl(rope, -0.12f, 0f, 0.06f, 0.06f, 0.07f, 0.95f, 14)
            b.cyl(rope, 0.17f, 0.05f, 0.05f, 0.05f, 0.07f, 0.5f, 14)
            b.block(carpet, 0.17f, 0.5f, 0.05f, 0.34f, 0.05f, 0.3f, 0.02f)
            b.at(-0.12f, 0.95f, 0f) { with(carpet) { lathe(listOf(0.2f to 0f, 0.22f to 0.06f, 0.2f to 0.12f), 22, capBottom = true) } }
            b.sphere(mat(0xFFC8B8D8, 0.05f), -0.12f, 1.02f, 0f, 0.16f, sy = 0.3f)
            b.with(mat(0xFFFFFFFF, 0.2f)) { tube(listOf(Triple(0.3f, 0.5f, 0.15f), Triple(0.31f, 0.36f, 0.16f)), 0.006f, 4) }
            b.sphere(mat(0xFFFF7EB6, 0.3f), 0.31f, 0.33f, 0.16f, 0.045f)
        }
        "toy_box" -> piece(id, 0.7f, 0.46f) { b ->
            val red = mat(0xFFFF6B6B, 0.3f)
            b.block(red, 0f, 0f, 0f, 0.66f, 0.38f, 0.42f, 0.04f)
            b.block(mat(0xFFFFD86A, 0.3f), 0f, 0.12f, 0.215f, 0.5f, 0.06f, 0.02f, 0.01f)
            b.at(0f, 0.38f, -0.21f) {
                rotX(-28f)
                with(mat(0xFFE85050, 0.3f)) { rbox(-0.34f, 0f, 0f, 0.34f, 0.05f, 0.44f, 0.025f) }
            }
            b.block(mat(0xFF4DA8FF, 0.3f), -0.18f, 0.32f, 0.06f, 0.14f, 0.14f, 0.14f, 0.02f)
            b.block(mat(0xFFFFC83D, 0.3f), 0.02f, 0.34f, -0.04f, 0.12f, 0.12f, 0.12f, 0.02f)
            val teddy = mat(0xFFC8925A, 0.1f)
            b.sphere(teddy, 0.18f, 0.48f, 0f, 0.12f)
            b.sphere(teddy, 0.1f, 0.58f, 0f, 0.045f)
            b.sphere(teddy, 0.26f, 0.58f, 0f, 0.045f)
            b.sphere(mat(0xFF3A2A28, 0.4f), 0.15f, 0.5f, 0.11f, 0.014f)
            b.sphere(mat(0xFF3A2A28, 0.4f), 0.21f, 0.5f, 0.11f, 0.014f)
        }
        "toy_horse" -> piece(id, 0.86f, 0.4f) { b ->
            val body = mat(0xFFFFFFFF, 0.3f)
            val mane = mat(0xFFFF8FB8, 0.2f)
            for (z in floatArrayOf(-0.13f, 0.13f)) {
                val arc = (0..12).map { k ->
                    val t = (k / 12f - 0.5f) * 1.1f
                    Triple(sin(t) * 0.8f, 0.03f + 0.8f * (1f - cos(t)), z)
                }
                b.with(mat(0xFFE89A50, 0.3f)) { tube(arc, 0.028f, 8) }
            }
            for (x in floatArrayOf(-0.18f, 0.18f)) for (z in floatArrayOf(-0.1f, 0.1f)) b.with(body) { tube(listOf(Triple(x, 0.36f, z), Triple(x * 1.3f, 0.06f, z * 1.2f)), 0.03f, 8) }
            b.sphere(body, 0f, 0.42f, 0f, 0.26f, sy = 0.45f, sz = 0.5f)
            b.with(body) { tube(listOf(Triple(0.18f, 0.48f, 0f), Triple(0.27f, 0.66f, 0f)), 0.07f, 10) }
            b.sphere(body, 0.32f, 0.7f, 0f, 0.1f, sy = 0.8f, sz = 0.75f)
            b.sphere(body, 0.4f, 0.67f, 0f, 0.06f, sy = 0.8f)
            b.sphere(mat(0xFF3A2A28, 0.4f), 0.36f, 0.73f, 0.065f, 0.014f)
            for (k in 0 until 5) b.sphere(mane, 0.16f + k * 0.035f, 0.56f + k * 0.04f, 0f, 0.045f)
            b.with(mane) { tube(listOf(Triple(-0.24f, 0.45f, 0f), Triple(-0.32f, 0.36f, 0f), Triple(-0.33f, 0.26f, 0f)), 0.035f, 8) }
            b.block(mat(0xFF7C6CFF, 0.3f), -0.02f, 0.5f, 0f, 0.2f, 0.04f, 0.26f, 0.02f)
            b.with(gold) { tube(listOf(Triple(0.3f, 0.82f, -0.08f), Triple(0.3f, 0.82f, 0.08f)), 0.012f, 6) }
        }
        "toy_aquarium" -> piece(id, 0.82f, 0.42f) { b ->
            b.block(wood, 0f, 0f, 0f, 0.8f, 0.42f, 0.4f, 0.03f)
            b.block(woodLight, 0f, 0.42f, 0f, 0.82f, 0.03f, 0.42f, 0.01f)
            b.block(mat(0xFF6EC6FF, 0.6f, alpha = 0.42f, shadow = false), 0f, 0.46f, 0f, 0.72f, 0.38f, 0.3f, 0.01f)
            b.box(mat(0xFFF2D49A, 0.1f), -0.36f, 0.45f, -0.15f, 0.36f, 0.5f, 0.15f)
            for (x in floatArrayOf(-0.25f, 0.2f)) b.with(leaf) { tube(listOf(Triple(x, 0.5f, -0.08f), Triple(x + 0.03f, 0.65f, -0.07f), Triple(x - 0.02f, 0.78f, -0.08f)), 0.018f, 6) }
            for ((x, y, c) in listOf(Triple(-0.12f, 0.66f, 0xFFFF8A3D), Triple(0.12f, 0.58f, 0xFFFFD84D), Triple(0.02f, 0.75f, 0xFFFF6B9A))) {
                b.sphere(mat(c, 0.5f), x, y, 0.02f, 0.05f, sy = 0.7f, sz = 0.45f)
                b.at(x - 0.06f, y, 0.02f) { rotZ(90f); with(mat(c, 0.5f)) { cyl(0.035f, 0f, -0.02f, 0.03f, 8) } }
            }
            b.with(mat(0xFFFFFFFF, 0.6f, alpha = 0.3f, shadow = false)) { box(-0.37f, 0.45f, -0.16f, 0.37f, 0.86f, 0.16f) }
            b.block(dark, 0f, 0.86f, 0f, 0.76f, 0.04f, 0.34f, 0.015f)
        }
        "toy_cave" -> piece(id, 0.7f, 0.66f) { b ->
            val felt = mat(0xFFB28CFF, 0.05f)
            b.at(0f, 0f, 0f) { with(felt) { sphere(0.34f, 26, 14, 1.05f, 0.92f, 0.5f, 1f) } }
            b.at(0f, 0.0f, 0.31f) {
                scale(1f, 1.2f, 0.25f)
                with(mat(0xFF3C2E58, 0.05f)) { sphere(0.18f, 20, 10, 1f, 1f, 0.5f, 1f) }
            }
            b.at(0f, 0f, 0f) { with(mat(0xFF9A72E8, 0.05f)) { torus(0.33f, 0.04f, 30, 8) } }
            b.sphere(mat(0xFFFFD86A, 0.3f), 0f, 0.38f, 0f, 0.05f)
            b.sphere(mat(0xFFFF8FB8, 0.1f), 0.22f, 0.03f, 0.3f, 0.09f, sy = 0.4f)
        }
        "toy_wheel" -> piece(id, 0.66f, 0.42f) { b ->
            b.block(mat(0xFF7C6CFF, 0.3f), 0f, 0f, 0f, 0.6f, 0.05f, 0.38f, 0.02f)
            b.with(metal) { tube(listOf(Triple(-0.22f, 0.04f, -0.12f), Triple(0f, 0.4f, -0.14f), Triple(0.22f, 0.04f, -0.12f)), 0.02f, 6) }
            b.at(0f, 0.43f, 0.02f) {
                rotX(90f)
                with(mat(0xFF4DC8E8, 0.5f)) { torus(0.36f, 0.03f, 36, 8) }
                with(mat(0xFF4DC8E8, 0.5f)) { at(0f, 0.2f, 0f) { torus(0.36f, 0.025f, 36, 8) } }
                for (k in 0 until 18) {
                    val a = k / 18f * 2f * PI.toFloat()
                    with(mat(0xFFFFFFFF, 0.4f)) { tube(listOf(Triple(cos(a) * 0.36f, 0f, sin(a) * 0.36f), Triple(cos(a) * 0.36f, 0.2f, sin(a) * 0.36f)), 0.008f, 4) }
                }
                for (k in 0 until 6) {
                    val a = k / 6f * 2f * PI.toFloat()
                    with(metal) { tube(listOf(Triple(0f, 0f, 0f), Triple(cos(a) * 0.36f, 0f, sin(a) * 0.36f)), 0.01f, 4) }
                }
                with(metal) { cyl(0.04f, 0.04f, -0.02f, 0.02f, 12) }
            }
        }
        "toy_trophy" -> piece(id, 0.66f, 0.38f) { b ->
            cabinet(b, 0.64f, 1.1f, 0.36f, woodDark, mat(0xFF5A3A24, 0.1f))
            for (y in floatArrayOf(0.4f, 0.72f)) b.block(woodLight, 0f, y, 0.02f, 0.56f, 0.025f, 0.3f, 0.005f)
            for ((x, y, s) in listOf(Triple(-0.15f, 0.1f, 1f), Triple(0.13f, 0.1f, 0.8f), Triple(0f, 0.425f, 1.1f), Triple(-0.17f, 0.425f, 0.7f), Triple(0.17f, 0.745f, 0.9f), Triple(-0.1f, 0.745f, 0.8f))) {
                b.at(x, y, 0.04f) {
                    scale(s)
                    with(gold) {
                        cyl(0.05f, 0.05f, 0f, 0.03f, 12)
                        cyl(0.012f, 0.012f, 0.03f, 0.1f, 8)
                        lathe(listOf(0.02f to 0.1f, 0.07f to 0.14f, 0.08f to 0.22f), 14, capBottom = true)
                    }
                }
            }
            b.block(glass, 0f, 0.08f, 0.17f, 0.58f, 0.98f, 0.01f, 0.005f)
        }

        // ------------------------------------------------------------- seats and more
        "deco_beanbag" -> piece(id, 0.82f, 0.74f) { b ->
            val bag = mat(0xFF9A7CFF, 0.15f)
            b.sphere(bag, 0f, 0.2f, 0.04f, 0.4f, sy = 0.55f, sz = 0.9f)
            b.sphere(bag, 0f, 0.38f, -0.16f, 0.3f, sy = 0.85f, sz = 0.6f)
            b.sphere(mat(0xFFB8A4FF, 0.15f), 0f, 0.3f, 0.06f, 0.24f, sy = 0.3f, sz = 0.9f)
        }
        "deco_sofa" -> piece(id, 1.36f, 0.66f) { b ->
            val fabric = mat(0xFFFF8FB8, 0.12f)
            val light = mat(0xFFFFB3CF, 0.12f)
            for (x in floatArrayOf(-0.58f, 0.58f)) for (z in floatArrayOf(-0.24f, 0.24f)) b.cyl(woodDark, x, z, 0.03f, 0.025f, 0f, 0.08f, 10)
            b.rbox(fabric, -0.66f, 0.07f, -0.3f, 0.66f, 0.3f, 0.3f, 0.07f)
            b.rbox(fabric, -0.66f, 0.07f, -0.32f, 0.66f, 0.72f, -0.12f, 0.08f)
            for (x in floatArrayOf(-0.6f, 0.6f)) b.rbox(fabric, x - 0.09f, 0.07f, -0.3f, x + 0.09f, 0.48f, 0.3f, 0.08f)
            for (x in floatArrayOf(-0.25f, 0.25f)) {
                b.rbox(light, x - 0.24f, 0.28f, -0.12f, x + 0.24f, 0.38f, 0.28f, 0.05f)
                b.rbox(light, x - 0.22f, 0.36f, -0.16f, x + 0.22f, 0.66f, -0.06f, 0.06f)
            }
            b.at(-0.38f, 0.5f, 0.0f) { rotZ(20f); rotY(15f); with(mat(0xFFFFD86A, 0.15f)) { rbox(-0.1f, -0.1f, -0.04f, 0.1f, 0.1f, 0.04f, 0.04f) } }
        }
        "deco_bookshelf" -> piece(id, 0.82f, 0.36f) { b ->
            cabinet(b, 0.8f, 1.3f, 0.34f, wood, mat(0xFFB87A48, 0.1f))
            val colors = longArrayOf(0xFFFF7EB6, 0xFF7C6CFF, 0xFF2EC4A0, 0xFFFFC83D, 0xFF4DA8FF, 0xFFFF8A3D)
            for ((row, y) in floatArrayOf(0.06f, 0.46f, 0.86f).withIndex()) {
                b.block(woodLight, 0f, y - 0.03f, 0.02f, 0.72f, 0.03f, 0.32f, 0.005f)
                var x = -0.33f
                var k = row * 2
                while (x < 0.3f) {
                    val bw = 0.05f + (k % 3) * 0.015f
                    val bh = 0.24f + (k % 4) * 0.03f
                    if (k % 7 == 5) {
                        b.at(x + bh * 0.4f, y, 0.02f) { rotZ(-18f); with(mat(colors[k % 6], 0.25f)) { rbox(-bw / 2f, 0f, -0.11f, bw / 2f, bh, 0.11f, 0.008f) } }
                        x += bh * 0.5f
                    } else {
                        b.rbox(mat(colors[k % 6], 0.25f), x, y, -0.09f, x + bw, y + bh, 0.12f, 0.008f)
                        x += bw + 0.008f
                    }
                    k++
                }
            }
            b.sphere(gold, 0.25f, 1.36f, 0f, 0.06f)
        }
        "deco_globe" -> piece(id, 0.5f, 0.5f) { b ->
            b.cyl(woodDark, 0f, 0f, 0.17f, 0.12f, 0f, 0.05f, 22)
            b.cyl(woodDark, 0f, 0f, 0.025f, 0.025f, 0.05f, 0.56f, 10)
            b.at(0f, 0.84f, 0f) {
                rotZ(23f)
                with(mat(0xFF4DA8FF, 0.5f)) { sphere(0.26f, 26, 16) }
                for ((lat, lon, r) in listOf(Triple(20f, 30f, 0.12f), Triple(-25f, 80f, 0.09f), Triple(35f, 160f, 0.14f), Triple(-10f, 250f, 0.1f), Triple(50f, 300f, 0.08f))) {
                    val la = lat * MeshBuilder.DEG
                    val lo = lon * MeshBuilder.DEG
                    with(mat(0xFF6ED48A, 0.4f)) { at(cos(la) * cos(lo) * 0.2f, sin(la) * 0.2f, cos(la) * sin(lo) * 0.2f) { sphere(r, 12, 8) } }
                }
                at(0f, 0f, 0f) { rotX(90f); with(gold) { torus(0.3f, 0.012f, 36, 6, 0.55f) } }
                with(gold) { cyl(0.012f, 0.012f, -0.32f, 0.32f, 6) }
            }
        }
        "deco_piano" -> piano(id)
        "deco_fireplace" -> piece(id, 1.05f, 0.42f) { b ->
            val brick = mat(0xFFE07A5A, 0.1f)
            b.block(brick, 0f, 0f, 0f, 1.0f, 0.9f, 0.4f, 0.03f)
            b.block(mat(0xFFFFF4E8, 0.2f), 0f, 0.9f, 0.01f, 1.1f, 0.07f, 0.44f, 0.02f)
            b.block(mat(0xFF3A2A28, 0.05f), 0f, 0.06f, 0.12f, 0.56f, 0.5f, 0.2f, 0.06f)
            b.block(mat(0xFF8A8A92, 0.1f), 0f, 0f, 0.22f, 0.76f, 0.06f, 0.06f, 0.02f)
            for (x in floatArrayOf(-0.12f, 0.12f)) b.at(x, 0.12f, 0.12f) { rotZ(90f); with(woodDark) { cyl(0.04f, 0.04f, -0.12f, 0.12f, 10) } }
            for ((x, h, c) in listOf(Triple(-0.08f, 0.26f, 0xFFFF8A3D), Triple(0.07f, 0.22f, 0xFFFFC83D), Triple(0f, 0.32f, 0xFFFF5A3D))) b.at(x, 0.14f, 0.13f) { with(mat(c, 0.2f, emissive = 0.95f, shadow = false)) { cyl(0.07f, 0f, 0f, h, 12) } }
            for (k in 0 until 4) b.with(mat(0xFFC8603E, 0.1f)) { box(-0.5f, 0.15f + k * 0.18f, 0.2f, -0.3f, 0.16f + k * 0.18f, 0.205f) }
            b.cyl(gold, -0.36f, 0.05f, 0.04f, 0.03f, 0.97f, 1.07f, 12)
            b.sphere(mat(0xFFFF6B9A, 0.3f), 0.34f, 1.04f, 0.05f, 0.07f)
        }
        "deco_tent" -> piece(id, 1.0f, 0.86f) { b ->
            val a = mat(0xFFFFC83D, 0.15f)
            val c2 = mat(0xFFFF6B9A, 0.15f)
            // a triangular tent made of stripes
            val n = 6
            for (k in 0 until n) {
                val z0 = -0.4f + 0.8f * k / n
                val z1 = -0.4f + 0.8f * (k + 1) / n
                b.with(if (k % 2 == 0) a else c2) {
                    quad(-0.48f, 0f, z1, 0f, 0.92f, z1, 0f, 0.92f, z0, -0.48f, 0f, z0)
                    quad(0f, 0.92f, z1, 0.48f, 0f, z1, 0.48f, 0f, z0, 0f, 0.92f, z0)
                }
            }
            b.with(a) { quad(-0.48f, 0f, -0.4f, 0.48f, 0f, -0.4f, 0f, 0.92f, -0.4f, 0f, 0.92f, -0.4f) }
            // front flaps open
            b.with(c2) { quad(-0.48f, 0f, 0.4f, -0.2f, 0f, 0.48f, 0f, 0.92f, 0.4f, 0f, 0.92f, 0.4f) }
            b.with(c2) { quad(0.2f, 0f, 0.48f, 0.48f, 0f, 0.4f, 0f, 0.92f, 0.4f, 0f, 0.92f, 0.4f) }
            b.with(mat(0xFF4A3F66, 0.05f)) { quad(-0.2f, 0.002f, 0.4f, 0.2f, 0.002f, 0.4f, 0f, 0.6f, 0.4f, 0f, 0.6f, 0.4f) }
            b.cyl(woodDark, 0f, 0.4f, 0.015f, 0.015f, 0.9f, 1.12f, 6)
            b.at(0f, 1.05f, 0.4f) { with(mat(0xFF7C6CFF, 0.2f)) { quad(0f, 0f, 0f, 0.16f, 0.03f, 0f, 0f, 0.07f, 0f, 0f, 0.07f, 0f) } }
            b.sphere(mat(0xFF4DA8FF, 0.1f), 0.12f, 0.07f, 0.1f, 0.12f, sy = 0.45f)
        }
        "deco_stage" -> piece(id, 1.3f, 0.8f) { b ->
            b.at(0f, 0f, 0.05f) {
                scale(1f, 1f, 0.6f)
                with(mat(0xFFE8303F, 0.2f)) { cyl(0.62f, 0.62f, 0f, 0.14f, 40) }
                with(gold) { at(0f, 0.14f, 0f) { torus(0.62f, 0.02f, 40, 6) } }
                for (k in 0 until 10) {
                    val a = k / 10f * 2f * PI.toFloat()
                    with(mat(0xFFFFE066, 0.4f, emissive = 0.7f)) { at(cos(a) * 0.6f, 0.07f, sin(a) * 0.6f) { sphere(0.025f, 8, 6) } }
                }
            }
            val curtain = mat(0xFF9A2A5A, 0.15f)
            for (x in floatArrayOf(-0.6f, 0.6f)) {
                b.cyl(gold, x, -0.32f, 0.025f, 0.025f, 0f, 1.25f, 10)
                b.at(x * 0.82f, 0.14f, -0.3f) { with(curtain) { cyl(0.13f, 0.07f, 0f, 1.08f, 16) } }
            }
            b.block(curtain, 0f, 1.14f, -0.3f, 1.3f, 0.14f, 0.08f, 0.03f)
            b.at(0f, 1.25f, -0.28f) { with(gold) { rotX(90f); extrude(MeshBuilder.star(5, 0.12f, 0.05f), -0.02f, 0.02f, bottom = true) } }
        }
        else -> piece(id, 0.5f, 0.5f) { b -> b.block(mat(0xFFFF8FB8, 0.3f), 0f, 0f, 0f, 0.4f, 0.4f, 0.4f, 0.08f) }
    }

    // ------------------------------------------------------------- helpers

    private fun piece(id: String, w: Float, d: Float, build: (ModelBuilder) -> Unit): Piece {
        val b = ModelBuilder()
        b.scale(SCALE)
        build(b)
        return Piece(id, b.build(), w * SCALE, d * SCALE)
    }

    private fun lamp(id: String, size: Float, glow: FloatArray, build: (ModelBuilder) -> Unit): Piece {
        val b = ModelBuilder()
        b.scale(SCALE)
        build(b)
        return Piece(id, b.build(), size * SCALE, size * SCALE, glow = floatArrayOf(glow[0] * SCALE, glow[1] * SCALE, glow[2] * SCALE))
    }

    /** A bed: [build] gets the builder and the bedding colours (light, base, dark). */
    private fun bed(id: String, w: Float = 1.3f, d: Float = 0.78f, build: (ModelBuilder, IntArray) -> Unit): Piece {
        val b = ModelBuilder()
        b.scale(SCALE)
        build(b, bedColors(id))
        return Piece(id, b.build(), w * SCALE, d * SCALE, sleepY = SLEEP_Y * SCALE)
    }

    /** Furniture is built at the size of the drawn rooms and then made a bit smaller. */
    const val SCALE = 0.8f

    /** Mattress, pillow and a folded blanket at the foot end. */
    private fun mattress(b: ModelBuilder, w: Float, d: Float, y0: Float, bc: IntArray, round: Float = 0.05f) {
        b.rbox(white, -w / 2f, y0, -d / 2f, w / 2f, SLEEP_Y, d / 2f, round)
        b.rbox(cream, -w / 2f + 0.04f, SLEEP_Y - 0.01f, -d / 2f + 0.08f, -w / 2f + 0.3f, SLEEP_Y + 0.08f, d / 2f - 0.08f, 0.04f)
        val blanket = Mat(bc[1], 0.15f)
        b.rbox(blanket, w / 2f - 0.42f, SLEEP_Y - 0.02f, -d / 2f - 0.01f, w / 2f + 0.01f, SLEEP_Y + 0.035f, d / 2f + 0.01f, 0.02f)
    }

    fun bedColors(id: String): IntArray = when (id) {
        "bed_race" -> intArrayOf(col(0xFFA8D0FF), col(0xFF4C8DFF), col(0xFF22509E))
        "bed_canopy" -> intArrayOf(col(0xFFFFD0E4), col(0xFFFF8FB8), col(0xFFC24A7E))
        "bed_cloud" -> intArrayOf(col(0xFFE8F4FF), col(0xFFB0D4FF), col(0xFF6A98D8))
        "bed_basket" -> intArrayOf(col(0xFFFFE0EC), col(0xFFFF8FB8), col(0xFFC24A7E))
        "bed_hammock" -> intArrayOf(col(0xFFFFF0C8), col(0xFFFFC85A), col(0xFFC08A2A))
        else -> intArrayOf(col(0xFFB8C8FF), col(0xFF8C9CF4), col(0xFF5463C7))
    }

    /** An open cabinet (sides, top, bottom and back) standing on the floor, open to the front. */
    private fun cabinet(b: ModelBuilder, w: Float, h: Float, d: Float, outer: Mat, inner: Mat) {
        val t = 0.035f
        b.rbox(outer, -w / 2f, 0f, -d / 2f, -w / 2f + t, h, d / 2f, 0.012f)
        b.rbox(outer, w / 2f - t, 0f, -d / 2f, w / 2f, h, d / 2f, 0.012f)
        b.rbox(outer, -w / 2f, h - t, -d / 2f, w / 2f, h, d / 2f, 0.012f)
        b.rbox(outer, -w / 2f, 0f, -d / 2f, w / 2f, 0.06f, d / 2f, 0.012f)
        b.box(inner, -w / 2f + t, 0.06f, -d / 2f, w / 2f - t, h - t, -d / 2f + 0.02f)
    }

    /** A flower pot of radius [r] and height [h] with soil. */
    private fun pot(b: ModelBuilder, r: Float, h: Float, m: Mat) {
        b.with(m) { lathe(listOf(r * 0.72f to 0f, r * 0.8f to 0.02f, r to h * 0.82f, r * 1.08f to h * 0.84f, r * 1.1f to h, r * 0.98f to h * 1.0f), 24, capBottom = true) }
        b.at(0f, h * 0.94f, 0f) { with(soil) { disc(r * 0.97f, 0f, 24) } }
    }

    private fun piano(id: String): Piece = piece(id, 1.12f, 0.6f) { b ->
        val body = mat(0xFF4A3550, 0.55f)
        b.block(body, 0f, 0f, -0.12f, 1.08f, 1.0f, 0.32f, 0.03f)
        b.block(body, 0f, 0.62f, 0.08f, 1.08f, 0.06f, 0.2f, 0.02f)
        for (x in floatArrayOf(-0.5f, 0.5f)) b.block(body, x, 0f, 0.06f, 0.06f, 0.66f, 0.16f, 0.02f)
        b.block(mat(0xFFFFFFFF, 0.5f), 0f, 0.62f, 0.12f, 0.94f, 0.04f, 0.14f, 0.005f)
        for (k in 0 until 14) {
            if (k % 7 == 2 || k % 7 == 6) continue
            b.block(mat(0xFF1E1A26, 0.5f), -0.45f + k * 0.067f + 0.033f, 0.66f, 0.1f, 0.03f, 0.025f, 0.08f, 0.004f)
        }
        b.block(mat(0xFFFFF4E0, 0.3f), 0f, 0.86f, 0.04f, 0.3f, 0.2f, 0.02f, 0.01f)
        b.block(body, 0f, 0f, 0.42f, 0.6f, 0.36f, 0.24f, 0.03f)
        b.block(mat(0xFFFF8FB8, 0.15f), 0f, 0.36f, 0.42f, 0.56f, 0.05f, 0.22f, 0.02f)
        b.cyl(gold, 0.3f, -0.12f, 0.04f, 0.04f, 1.0f, 1.03f, 12)
        b.at(0.3f, 1.03f, -0.12f) { with(mat(0xFFFFF4C2, 0.2f, emissive = 0.6f)) { cyl(0.025f, 0.025f, 0f, 0.12f, 10) } }
    }

    /** Height of the lying surface of every bed (the pet lies there). */
    const val SLEEP_Y = 0.26f
}
