package de.knuffi.app.render.house3d

import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tan

/** 4 × 4 matrices (column-major float arrays, like OpenGL) and small vector helpers. */
object M4 {
    fun identity(out: FloatArray = FloatArray(16)): FloatArray {
        out.fill(0f)
        out[0] = 1f; out[5] = 1f; out[10] = 1f; out[15] = 1f
        return out
    }

    /** out = a × b (out may not be a or b). */
    fun mul(a: FloatArray, b: FloatArray, out: FloatArray = FloatArray(16)): FloatArray {
        for (c in 0 until 4) for (r in 0 until 4) {
            var s = 0f
            for (k in 0 until 4) s += a[k * 4 + r] * b[c * 4 + k]
            out[c * 4 + r] = s
        }
        return out
    }

    fun translation(x: Float, y: Float, z: Float): FloatArray = identity().also { it[12] = x; it[13] = y; it[14] = z }

    fun scaling(x: Float, y: Float, z: Float): FloatArray = identity().also { it[0] = x; it[5] = y; it[10] = z }

    /** Rotation around the y axis (radians, counter-clockwise seen from above). */
    fun rotY(a: Float): FloatArray = identity().also {
        val c = cos(a)
        val s = sin(a)
        it[0] = c; it[2] = -s; it[8] = s; it[10] = c
    }

    fun rotX(a: Float): FloatArray = identity().also {
        val c = cos(a)
        val s = sin(a)
        it[5] = c; it[6] = s; it[9] = -s; it[10] = c
    }

    fun rotZ(a: Float): FloatArray = identity().also {
        val c = cos(a)
        val s = sin(a)
        it[0] = c; it[1] = s; it[4] = -s; it[5] = c
    }

    /** Moved to (x, y, z) and turned by [turn] radians around y. */
    fun place(x: Float, y: Float, z: Float, turn: Float = 0f, scale: Float = 1f): FloatArray {
        val m = rotY(turn)
        if (scale != 1f) for (i in 0 until 11) if (i % 4 != 3) m[i] *= scale
        m[12] = x; m[13] = y; m[14] = z
        return m
    }

    fun invert(m: FloatArray, out: FloatArray = FloatArray(16)): Boolean {
        val inv = FloatArray(16)
        inv[0] = m[5] * m[10] * m[15] - m[5] * m[11] * m[14] - m[9] * m[6] * m[15] + m[9] * m[7] * m[14] + m[13] * m[6] * m[11] - m[13] * m[7] * m[10]
        inv[4] = -m[4] * m[10] * m[15] + m[4] * m[11] * m[14] + m[8] * m[6] * m[15] - m[8] * m[7] * m[14] - m[12] * m[6] * m[11] + m[12] * m[7] * m[10]
        inv[8] = m[4] * m[9] * m[15] - m[4] * m[11] * m[13] - m[8] * m[5] * m[15] + m[8] * m[7] * m[13] + m[12] * m[5] * m[11] - m[12] * m[7] * m[9]
        inv[12] = -m[4] * m[9] * m[14] + m[4] * m[10] * m[13] + m[8] * m[5] * m[14] - m[8] * m[6] * m[13] - m[12] * m[5] * m[10] + m[12] * m[6] * m[9]
        inv[1] = -m[1] * m[10] * m[15] + m[1] * m[11] * m[14] + m[9] * m[2] * m[15] - m[9] * m[3] * m[14] - m[13] * m[2] * m[11] + m[13] * m[3] * m[10]
        inv[5] = m[0] * m[10] * m[15] - m[0] * m[11] * m[14] - m[8] * m[2] * m[15] + m[8] * m[3] * m[14] + m[12] * m[2] * m[11] - m[12] * m[3] * m[10]
        inv[9] = -m[0] * m[9] * m[15] + m[0] * m[11] * m[13] + m[8] * m[1] * m[15] - m[8] * m[3] * m[13] - m[12] * m[1] * m[11] + m[12] * m[3] * m[9]
        inv[13] = m[0] * m[9] * m[14] - m[0] * m[10] * m[13] - m[8] * m[1] * m[14] + m[8] * m[2] * m[13] + m[12] * m[1] * m[10] - m[12] * m[2] * m[9]
        inv[2] = m[1] * m[6] * m[15] - m[1] * m[7] * m[14] - m[5] * m[2] * m[15] + m[5] * m[3] * m[14] + m[13] * m[2] * m[7] - m[13] * m[3] * m[6]
        inv[6] = -m[0] * m[6] * m[15] + m[0] * m[7] * m[14] + m[4] * m[2] * m[15] - m[4] * m[3] * m[14] - m[12] * m[2] * m[7] + m[12] * m[3] * m[6]
        inv[10] = m[0] * m[5] * m[15] - m[0] * m[7] * m[13] - m[4] * m[1] * m[15] + m[4] * m[3] * m[13] + m[12] * m[1] * m[7] - m[12] * m[3] * m[5]
        inv[14] = -m[0] * m[5] * m[14] + m[0] * m[6] * m[13] + m[4] * m[1] * m[14] - m[4] * m[2] * m[13] - m[12] * m[1] * m[6] + m[12] * m[2] * m[5]
        inv[3] = -m[1] * m[6] * m[11] + m[1] * m[7] * m[10] + m[5] * m[2] * m[11] - m[5] * m[3] * m[10] - m[9] * m[2] * m[7] + m[9] * m[3] * m[6]
        inv[7] = m[0] * m[6] * m[11] - m[0] * m[7] * m[10] - m[4] * m[2] * m[11] + m[4] * m[3] * m[10] + m[8] * m[2] * m[7] - m[8] * m[3] * m[6]
        inv[11] = -m[0] * m[5] * m[11] + m[0] * m[7] * m[9] + m[4] * m[1] * m[11] - m[4] * m[3] * m[9] - m[8] * m[1] * m[7] + m[8] * m[3] * m[5]
        inv[15] = m[0] * m[5] * m[10] - m[0] * m[6] * m[9] - m[4] * m[1] * m[10] + m[4] * m[2] * m[9] + m[8] * m[1] * m[6] - m[8] * m[2] * m[5]
        var det = m[0] * inv[0] + m[1] * inv[4] + m[2] * inv[8] + m[3] * inv[12]
        if (det == 0f) return false
        det = 1f / det
        for (i in 0 until 16) out[i] = inv[i] * det
        return true
    }

    /** The 3 × 3 matrix for normals (inverse transpose of the upper left part). */
    fun normal3(m: FloatArray, out: FloatArray = FloatArray(9)): FloatArray {
        val a = m[0]; val b = m[4]; val c = m[8]
        val d = m[1]; val e = m[5]; val f = m[9]
        val g = m[2]; val h = m[6]; val i = m[10]
        val A = e * i - f * h
        val B = -(d * i - f * g)
        val C = d * h - e * g
        val det = a * A + b * B + c * C
        val k = if (det == 0f) 1f else 1f / det
        // inverse transpose, column-major
        out[0] = A * k; out[1] = (-(b * i - c * h)) * k; out[2] = (b * f - c * e) * k
        out[3] = B * k; out[4] = (a * i - c * g) * k; out[5] = (-(a * f - c * d)) * k
        out[6] = C * k; out[7] = (-(a * h - b * g)) * k; out[8] = (a * e - b * d) * k
        return out
    }

    fun lookAt(ex: Float, ey: Float, ez: Float, cx: Float, cy: Float, cz: Float, ux: Float = 0f, uy: Float = 1f, uz: Float = 0f): FloatArray {
        var fx = cx - ex; var fy = cy - ey; var fz = cz - ez
        var l = sqrt(fx * fx + fy * fy + fz * fz)
        fx /= l; fy /= l; fz /= l
        var sx = fy * uz - fz * uy; var sy = fz * ux - fx * uz; var sz = fx * uy - fy * ux
        l = sqrt(sx * sx + sy * sy + sz * sz)
        sx /= l; sy /= l; sz /= l
        val vx = sy * fz - sz * fy; val vy = sz * fx - sx * fz; val vz = sx * fy - sy * fx
        val m = identity()
        m[0] = sx; m[4] = sy; m[8] = sz
        m[1] = vx; m[5] = vy; m[9] = vz
        m[2] = -fx; m[6] = -fy; m[10] = -fz
        m[12] = -(sx * ex + sy * ey + sz * ez)
        m[13] = -(vx * ex + vy * ey + vz * ez)
        m[14] = fx * ex + fy * ey + fz * ez
        return m
    }

    /**
     * A pinhole projection onto a [w] × [h] image with focal length [f] (pixels) and the centre at
     * ([cx], [cy]) (pixels, y down). The image comes out top row first when read back.
     */
    fun pinhole(f: Float, cx: Float, cy: Float, w: Float, h: Float, near: Float, far: Float): FloatArray {
        val m = FloatArray(16)
        m[0] = 2f * f / w
        m[8] = 1f - 2f * cx / w
        m[5] = -2f * f / h
        m[9] = 1f - 2f * cy / h
        m[10] = -(far + near) / (far - near)
        m[14] = -2f * far * near / (far - near)
        m[11] = -1f
        return m
    }

    /** A perspective projection with vertical field of view [fovY] (radians), top row first when read back. */
    fun perspective(fovY: Float, w: Float, h: Float, near: Float, far: Float): FloatArray {
        val f = h / 2f / tan(fovY / 2f)
        return pinhole(f, w / 2f, h / 2f, w, h, near, far)
    }

    /** Orthographic projection (for the shadow map). */
    fun ortho(l: Float, r: Float, b: Float, t: Float, n: Float, f: Float): FloatArray {
        val m = identity()
        m[0] = 2f / (r - l)
        m[5] = 2f / (t - b)
        m[10] = -2f / (f - n)
        m[12] = -(r + l) / (r - l)
        m[13] = -(t + b) / (t - b)
        m[14] = -(f + n) / (f - n)
        return m
    }

    /** Transforms the point (x, y, z, 1); writes x, y, z, w into [out]. */
    fun apply(m: FloatArray, x: Float, y: Float, z: Float, out: FloatArray) {
        val ox = m[0] * x + m[4] * y + m[8] * z + m[12]
        val oy = m[1] * x + m[5] * y + m[9] * z + m[13]
        val oz = m[2] * x + m[6] * y + m[10] * z + m[14]
        val ow = m[3] * x + m[7] * y + m[11] * z + m[15]
        out[0] = ox; out[1] = oy; out[2] = oz
        if (out.size > 3) out[3] = ow
    }
}

/** An axis aligned box. */
class Box3(
    var x0: Float = Float.MAX_VALUE, var y0: Float = Float.MAX_VALUE, var z0: Float = Float.MAX_VALUE,
    var x1: Float = -Float.MAX_VALUE, var y1: Float = -Float.MAX_VALUE, var z1: Float = -Float.MAX_VALUE,
) {
    val empty: Boolean get() = x0 > x1

    fun add(x: Float, y: Float, z: Float) {
        if (x < x0) x0 = x
        if (y < y0) y0 = y
        if (z < z0) z0 = z
        if (x > x1) x1 = x
        if (y > y1) y1 = y
        if (z > z1) z1 = z
    }

    fun add(b: Box3) {
        if (b.empty) return
        add(b.x0, b.y0, b.z0)
        add(b.x1, b.y1, b.z1)
    }

    /** This box moved by matrix [m] (the box around the moved corners). */
    fun transformed(m: FloatArray): Box3 {
        val out = Box3()
        if (empty) return out
        val p = FloatArray(4)
        for (i in 0 until 8) {
            M4.apply(m, if (i and 1 == 0) x0 else x1, if (i and 2 == 0) y0 else y1, if (i and 4 == 0) z0 else z1, p)
            out.add(p[0], p[1], p[2])
        }
        return out
    }

    fun copy(): Box3 = Box3(x0, y0, z0, x1, y1, z1)
}
