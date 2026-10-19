package de.knuffi.app.render.house3d

/**
 * What a surface looks like: [color] (ARGB), how shiny it is, an optional texture (looked up by
 * key, see [TextureSource]) and whether it glows by itself.
 */
data class Mat(
    val color: Int,
    val gloss: Float = 0.25f,
    val tex: String? = null,
    /** 0: no texture, 1: texture times colour, 2: like 1 but see-through where the texture is. */
    val texMode: Int = if (tex != null) 1 else 0,
    val emissive: Float = 0f,
    /** Less than 1: see-through (glass, water). */
    val alpha: Float = 1f,
    /** Casts a shadow. */
    val shadow: Boolean = true,
) {
    val r: Float get() = ((color shr 16) and 0xFF) / 255f
    val g: Float get() = ((color shr 8) and 0xFF) / 255f
    val b: Float get() = (color and 0xFF) / 255f
}

class Part(val mesh: MeshData, val mat: Mat)

/** A thing made of parts in its own coordinates (y up, standing on y = 0, front towards +z). */
class Model3(val parts: List<Part>) {
    val bounds: Box3 = Box3().also { b -> for (p in parts) b.add(p.mesh.bounds) }
}

/**
 * Builds a [Model3]: shapes are added with a material and the shared transform, all shapes of the
 * same material end up in one mesh.
 */
class ModelBuilder {
    private val groups = LinkedHashMap<Mat, MeshBuilder>()
    private var t = M4.identity()
    private val stack = ArrayList<FloatArray>()

    fun push() {
        stack += t.copyOf()
    }

    fun pop() {
        t = stack.removeAt(stack.size - 1)
    }

    fun mul(m: FloatArray) {
        t = M4.mul(t, m)
    }

    fun translate(x: Float, y: Float, z: Float) = mul(M4.translation(x, y, z))
    fun scale(x: Float, y: Float = x, z: Float = x) = mul(M4.scaling(x, y, z))
    fun rotY(deg: Float) = mul(M4.rotY(deg * MeshBuilder.DEG))
    fun rotX(deg: Float) = mul(M4.rotX(deg * MeshBuilder.DEG))
    fun rotZ(deg: Float) = mul(M4.rotZ(deg * MeshBuilder.DEG))

    inline fun at(x: Float, y: Float, z: Float, rotY: Float = 0f, block: ModelBuilder.() -> Unit) {
        push()
        translate(x, y, z)
        if (rotY != 0f) rotY(rotY)
        block()
        pop()
    }

    /** Adds shapes of material [mat] with [block], moved by the current transform. */
    fun with(mat: Mat, block: MeshBuilder.() -> Unit) {
        val mb = groups.getOrPut(mat) { MeshBuilder() }
        mb.setTransform(t)
        mb.block()
    }

    fun box(mat: Mat, x0: Float, y0: Float, z0: Float, x1: Float, y1: Float, z1: Float) = with(mat) { box(x0, y0, z0, x1, y1, z1) }

    fun rbox(mat: Mat, x0: Float, y0: Float, z0: Float, x1: Float, y1: Float, z1: Float, r: Float, seg: Int = 4) =
        with(mat) { rbox(x0, y0, z0, x1, y1, z1, r, seg) }

    /** A rounded box given by its centre (x, z), bottom [y0] and size. */
    fun block(mat: Mat, x: Float, y0: Float, z: Float, w: Float, h: Float, d: Float, r: Float = 0.03f) =
        rbox(mat, x - w / 2f, y0, z - d / 2f, x + w / 2f, y0 + h, z + d / 2f, r)

    fun cyl(mat: Mat, x: Float, z: Float, r0: Float, r1: Float, y0: Float, y1: Float, seg: Int = 24, caps: Boolean = true) =
        at(x, 0f, z) { with(mat) { cyl(r0, r1, y0, y1, seg, caps, caps) } }

    fun sphere(mat: Mat, x: Float, y: Float, z: Float, r: Float, sy: Float = 1f, sz: Float = 1f, seg: Int = 20, rings: Int = 14) =
        at(x, y, z) { with(mat) { sphere(r, seg, rings, sy, sz) } }

    fun add(mat: Mat, mesh: MeshData) = with(mat) { add(mesh) }

    /** Adds a whole model (with its own materials). */
    fun add(model: Model3) {
        for (p in model.parts) with(p.mat) { add(p.mesh) }
    }

    fun build(): Model3 = Model3(groups.filterValues { !it.isEmpty }.map { (mat, mb) -> Part(mb.build(), mat) })
}

/** A model placed in the world. */
class Item3(
    val model: Model3,
    val matrix: FloatArray,
    /** What it is (for tapping and for the layers), e.g. "slot:BED", "door:KITCHEN", "fixture:piano". */
    val tag: String? = null,
    /**
     * For walls: the direction pointing out of the room. Seen from that side (from outside) the
     * wall is left out, so the camera can look into the room.
     */
    val outward: FloatArray? = null,
    /** A point on that wall. */
    val wallPoint: FloatArray? = null,
) {
    /** Highlight colour mixed over the item (alpha = amount), 0 for none. */
    var tint: Int = 0

    /** Left out of the picture. */
    var hidden: Boolean = false

    /** Only shown while this item (its wall) is shown. */
    var linkedTo: Item3? = null
    val bounds: Box3 by lazy { model.bounds.transformed(matrix) }
}

class Light3(
    /** Direction towards the sun (normalised). */
    val dir: FloatArray = norm(floatArrayOf(-0.45f, 1f, 0.6f)),
    val color: Int = 0xFFFFF4E6.toInt(),
    val strength: Float = 0.72f,
    val sky: Int = 0xFFDCE6FF.toInt(),
    val ground: Int = 0xFFB8A8A0.toInt(),
    val ambient: Float = 0.62f,
) {
    companion object {
        fun norm(v: FloatArray): FloatArray {
            val l = kotlin.math.sqrt(v[0] * v[0] + v[1] * v[1] + v[2] * v[2])
            return floatArrayOf(v[0] / l, v[1] / l, v[2] / l)
        }
    }
}

class Scene3 {
    val items = ArrayList<Item3>()
    var light = Light3()

    fun add(model: Model3, matrix: FloatArray, tag: String? = null): Item3 = Item3(model, matrix, tag).also { items += it }

    val bounds: Box3 get() = Box3().also { b -> for (i in items) b.add(i.bounds) }
}

/** Where the camera is and how it sees (view and projection matrices, eye position). */
class Camera3(val view: FloatArray, val proj: FloatArray, val eye: FloatArray, val width: Int, val height: Int) {
    val viewProj: FloatArray = M4.mul(proj, view)
    private val inv: FloatArray = FloatArray(16).also { M4.invert(viewProj, it) }

    /** Screen position (pixels, y down) and depth of a world point; null behind the camera. */
    fun project(x: Float, y: Float, z: Float): FloatArray? {
        val p = FloatArray(4)
        M4.apply(viewProj, x, y, z, p)
        if (p[3] <= 1e-5f) return null
        val nx = p[0] / p[3]
        val ny = p[1] / p[3]
        // the projection already flips y, so ny = -1 is the top
        return floatArrayOf((nx + 1f) / 2f * width, (ny + 1f) / 2f * height, p[3])
    }

    /** The ray through a screen point: origin and direction (world). */
    fun ray(px: Float, py: Float): Pair<FloatArray, FloatArray> {
        val nx = px / width * 2f - 1f
        val ny = py / height * 2f - 1f
        val a = FloatArray(4)
        val b = FloatArray(4)
        M4.apply(inv, nx, ny, -1f, a)
        M4.apply(inv, nx, ny, 1f, b)
        for (i in 0 until 3) {
            a[i] /= a[3]
            b[i] /= b[3]
        }
        val d = floatArrayOf(b[0] - a[0], b[1] - a[1], b[2] - a[2])
        val l = kotlin.math.sqrt(d[0] * d[0] + d[1] * d[1] + d[2] * d[2])
        return floatArrayOf(a[0], a[1], a[2]) to floatArrayOf(d[0] / l, d[1] / l, d[2] / l)
    }

    /** Where the ray through a screen point meets the plane y = [h]; null if it does not. */
    fun onPlane(px: Float, py: Float, h: Float = 0f): FloatArray? {
        val (o, d) = ray(px, py)
        if (kotlin.math.abs(d[1]) < 1e-5f) return null
        val t = (h - o[1]) / d[1]
        if (t < 0f) return null
        return floatArrayOf(o[0] + d[0] * t, h, o[2] + d[2] * t)
    }

    /** The screen rectangle around a box (left, top, right, bottom), or null. */
    fun rect(b: Box3): FloatArray? {
        var l = Float.MAX_VALUE; var t = Float.MAX_VALUE; var r = -Float.MAX_VALUE; var bt = -Float.MAX_VALUE
        var any = false
        for (i in 0 until 8) {
            val p = project(if (i and 1 == 0) b.x0 else b.x1, if (i and 2 == 0) b.y0 else b.y1, if (i and 4 == 0) b.z0 else b.z1) ?: continue
            any = true
            l = minOf(l, p[0]); t = minOf(t, p[1]); r = maxOf(r, p[0]); bt = maxOf(bt, p[1])
        }
        return if (any) floatArrayOf(l, t, r, bt) else null
    }
}
