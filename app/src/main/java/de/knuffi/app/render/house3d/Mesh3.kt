package de.knuffi.app.render.house3d

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/** Interleaved vertices (position, normal, uv: 8 floats) and triangle indices. */
class MeshData(val vertices: FloatArray, val indices: IntArray) {
    val vertexCount: Int get() = vertices.size / 8
    val bounds: Box3 = Box3().also { b ->
        var i = 0
        while (i < vertices.size) {
            b.add(vertices[i], vertices[i + 1], vertices[i + 2])
            i += 8
        }
    }
}

/**
 * Collects triangles. Everything added is moved by the current transform [m] (see [push],
 * [translate], [rotY] …), so complex shapes can be put together from simple ones.
 */
class MeshBuilder {
    private var v = FloatArray(1024)
    private var vn = 0
    private var idx = IntArray(1024)
    private var inN = 0
    private var m = M4.identity()
    private var nm = M4.normal3(m)
    private val stack = ArrayList<FloatArray>()
    private val tmp = FloatArray(4)

    val isEmpty: Boolean get() = inN == 0

    // ------------------------------------------------------------------ transform

    fun push() {
        stack += m.copyOf()
    }

    fun pop() {
        m = stack.removeAt(stack.size - 1)
        nm = M4.normal3(m)
    }

    fun mul(t: FloatArray) {
        m = M4.mul(m, t)
        nm = M4.normal3(m)
    }

    fun translate(x: Float, y: Float, z: Float) = mul(M4.translation(x, y, z))
    fun scale(x: Float, y: Float = x, z: Float = x) = mul(M4.scaling(x, y, z))
    fun rotY(deg: Float) = mul(M4.rotY(deg * DEG))
    fun rotX(deg: Float) = mul(M4.rotX(deg * DEG))
    fun rotZ(deg: Float) = mul(M4.rotZ(deg * DEG))

    inline fun at(x: Float, y: Float, z: Float, block: MeshBuilder.() -> Unit) {
        push()
        translate(x, y, z)
        block()
        pop()
    }

    /** The current transform (a copy). */
    fun current(): FloatArray = m.copyOf()

    fun setTransform(t: FloatArray) {
        m = t.copyOf()
        nm = M4.normal3(m)
    }

    // ------------------------------------------------------------------ raw

    fun vertex(x: Float, y: Float, z: Float, nx: Float, ny: Float, nz: Float, u: Float = 0f, w: Float = 0f): Int {
        if (vn + 8 > v.size) v = v.copyOf(v.size * 2)
        M4.apply(m, x, y, z, tmp)
        v[vn] = tmp[0]; v[vn + 1] = tmp[1]; v[vn + 2] = tmp[2]
        var ox = nm[0] * nx + nm[3] * ny + nm[6] * nz
        var oy = nm[1] * nx + nm[4] * ny + nm[7] * nz
        var oz = nm[2] * nx + nm[5] * ny + nm[8] * nz
        val l = sqrt(ox * ox + oy * oy + oz * oz)
        if (l > 1e-6f) {
            ox /= l; oy /= l; oz /= l
        }
        v[vn + 3] = ox; v[vn + 4] = oy; v[vn + 5] = oz
        v[vn + 6] = u; v[vn + 7] = w
        vn += 8
        return vn / 8 - 1
    }

    fun tri(a: Int, b: Int, c: Int) {
        if (inN + 3 > idx.size) idx = idx.copyOf(idx.size * 2)
        idx[inN++] = a; idx[inN++] = b; idx[inN++] = c
    }

    fun quadIdx(a: Int, b: Int, c: Int, d: Int) {
        tri(a, b, c)
        tri(a, c, d)
    }

    fun build(): MeshData = MeshData(v.copyOf(vn), idx.copyOf(inN))

    /** Appends another mesh (moved by the current transform). */
    fun add(mesh: MeshData) {
        val base = vn / 8
        val s = mesh.vertices
        var i = 0
        while (i < s.size) {
            vertex(s[i], s[i + 1], s[i + 2], s[i + 3], s[i + 4], s[i + 5], s[i + 6], s[i + 7])
            i += 8
        }
        for (k in mesh.indices) {
            if (inN + 1 > idx.size) idx = idx.copyOf(idx.size * 2)
            idx[inN++] = base + k
        }
    }

    // ------------------------------------------------------------------ shapes

    /** A flat quad from the four corners (counter-clockwise seen from the front), uv 0..1. */
    fun quad(
        ax: Float, ay: Float, az: Float, bx: Float, by: Float, bz: Float,
        cx: Float, cy: Float, cz: Float, dx: Float, dy: Float, dz: Float,
        u0: Float = 0f, v0: Float = 0f, u1: Float = 1f, v1: Float = 1f,
    ) {
        val ux = bx - ax; val uy = by - ay; val uz = bz - az
        val wx = dx - ax; val wy = dy - ay; val wz = dz - az
        var nx = uy * wz - uz * wy
        var ny = uz * wx - ux * wz
        var nz = ux * wy - uy * wx
        val l = sqrt(nx * nx + ny * ny + nz * nz).coerceAtLeast(1e-6f)
        nx /= l; ny /= l; nz /= l
        val a = vertex(ax, ay, az, nx, ny, nz, u0, v1)
        val b = vertex(bx, by, bz, nx, ny, nz, u1, v1)
        val c = vertex(cx, cy, cz, nx, ny, nz, u1, v0)
        val d = vertex(dx, dy, dz, nx, ny, nz, u0, v0)
        quadIdx(a, b, c, d)
    }

    /** A box with flat sides. */
    fun box(x0: Float, y0: Float, z0: Float, x1: Float, y1: Float, z1: Float) {
        quad(x0, y0, z1, x1, y0, z1, x1, y1, z1, x0, y1, z1) // front
        quad(x1, y0, z0, x0, y0, z0, x0, y1, z0, x1, y1, z0) // back
        quad(x0, y0, z0, x0, y0, z1, x0, y1, z1, x0, y1, z0) // left
        quad(x1, y0, z1, x1, y0, z0, x1, y1, z0, x1, y1, z1) // right
        quad(x0, y1, z1, x1, y1, z1, x1, y1, z0, x0, y1, z0) // top
        quad(x0, y0, z0, x1, y0, z0, x1, y0, z1, x0, y0, z1) // bottom
    }

    /** A box with rounded edges and corners of radius [r] (soft, toy-like look). */
    fun rbox(x0: Float, y0: Float, z0: Float, x1: Float, y1: Float, z1: Float, r: Float, seg: Int = 4) {
        val rr = min(r, min(x1 - x0, min(y1 - y0, z1 - z0)) / 2f * 0.999f)
        if (rr <= 1e-4f) {
            box(x0, y0, z0, x1, y1, z1)
            return
        }
        val cx = (x0 + x1) / 2f; val cy = (y0 + y1) / 2f; val cz = (z0 + z1) / 2f
        val hx = (x1 - x0) / 2f - rr; val hy = (y1 - y0) / 2f - rr; val hz = (z1 - z0) / 2f - rr
        // coordinates along one axis: rounded part, flat part, rounded part
        fun coords(h: Float): FloatArray {
            val out = FloatArray(seg * 2 + 2)
            for (k in 0..seg) out[k] = -h - rr * cos(k / seg.toFloat() * PI.toFloat() / 2f)
            for (k in 0..seg) out[seg + 1 + k] = h + rr * sin(k / seg.toFloat() * PI.toFloat() / 2f)
            return out
        }
        val ax = coords(hx); val ay = coords(hy); val az = coords(hz)
        // each face: (u axis, v axis, fixed axis, sign)
        for (face in 0 until 6) {
            val base = vn / 8
            val us: FloatArray
            val vs: FloatArray
            when (face / 2) {
                0 -> { us = ax; vs = ay }
                1 -> { us = az; vs = ay }
                else -> { us = ax; vs = az }
            }
            val sign = if (face % 2 == 0) 1f else -1f
            for (j in vs.indices) for (i in us.indices) {
                val u = us[i]
                val w = vs[j]
                var px: Float; var py: Float; var pz: Float
                when (face / 2) {
                    0 -> { px = u; py = w; pz = sign * (hz + rr) }
                    1 -> { px = sign * (hx + rr); py = w; pz = u }
                    else -> { px = u; py = sign * (hy + rr); pz = w }
                }
                val qx = px.coerceIn(-hx, hx); val qy = py.coerceIn(-hy, hy); val qz = pz.coerceIn(-hz, hz)
                var nx = px - qx; var ny = py - qy; var nz = pz - qz
                val l = sqrt(nx * nx + ny * ny + nz * nz).coerceAtLeast(1e-6f)
                nx /= l; ny /= l; nz /= l
                vertex(cx + qx + nx * rr, cy + qy + ny * rr, cz + qz + nz * rr, nx, ny, nz, i / (us.size - 1f), 1f - j / (vs.size - 1f))
            }
            val cols = us.size
            for (j in 0 until vs.size - 1) for (i in 0 until cols - 1) {
                val a = base + j * cols + i
                val b = a + 1
                val c = a + cols + 1
                val d = a + cols
                // keep the winding facing outwards
                val flip = when (face / 2) {
                    0 -> sign < 0
                    1 -> sign > 0
                    else -> sign > 0
                }
                if (flip) quadIdx(a, d, c, b) else quadIdx(a, b, c, d)
            }
        }
    }

    /** A cylinder (or cone) around the y axis from [y0] to [y1]. */
    fun cyl(r0: Float, r1: Float, y0: Float, y1: Float, seg: Int = 24, capBottom: Boolean = true, capTop: Boolean = true) {
        val base = vn / 8
        val slope = (r0 - r1) / (y1 - y0).coerceAtLeast(1e-5f)
        for (i in 0..seg) {
            val a = i / seg.toFloat() * 2f * PI.toFloat()
            val c = cos(a)
            val s = sin(a)
            vertex(c * r0, y0, s * r0, c, slope, s, i / seg.toFloat(), 1f)
            vertex(c * r1, y1, s * r1, c, slope, s, i / seg.toFloat(), 0f)
        }
        for (i in 0 until seg) {
            val a = base + i * 2
            quadIdx(a, a + 2, a + 3, a + 1)
        }
        if (capTop && r1 > 0f) disc(r1, y1, seg, true)
        if (capBottom && r0 > 0f) disc(r0, y0, seg, false)
    }

    /** A flat disc at height [y] facing up (or down). */
    fun disc(r: Float, y: Float, seg: Int = 24, up: Boolean = true, r2: Float = r) {
        val ny = if (up) 1f else -1f
        val c0 = vertex(0f, y, 0f, 0f, ny, 0f, 0.5f, 0.5f)
        for (i in 0..seg) {
            val a = i / seg.toFloat() * 2f * PI.toFloat()
            vertex(cos(a) * r, y, sin(a) * r2, 0f, ny, 0f, 0.5f + cos(a) * 0.5f, 0.5f - sin(a) * 0.5f)
        }
        for (i in 0 until seg) if (up) tri(c0, c0 + 2 + i, c0 + 1 + i) else tri(c0, c0 + 1 + i, c0 + 2 + i)
    }

    /** A ring (flat, facing up) between [rIn] and [rOut]. */
    fun ring(rIn: Float, rOut: Float, y: Float, seg: Int = 32) {
        val base = vn / 8
        for (i in 0..seg) {
            val a = i / seg.toFloat() * 2f * PI.toFloat()
            vertex(cos(a) * rIn, y, sin(a) * rIn, 0f, 1f, 0f)
            vertex(cos(a) * rOut, y, sin(a) * rOut, 0f, 1f, 0f)
        }
        for (i in 0 until seg) {
            val a = base + i * 2
            quadIdx(a, a + 1, a + 3, a + 2)
        }
    }

    /** A sphere (or ellipsoid with [sy], [sz]) around the origin. */
    fun sphere(r: Float, seg: Int = 20, rings: Int = 14, sy: Float = 1f, sz: Float = 1f, from: Float = 0f, to: Float = 1f) {
        val base = vn / 8
        for (j in 0..rings) {
            val phi = PI.toFloat() * (from + (to - from) * j / rings.toFloat())
            val y = -cos(phi)
            val rr = sin(phi)
            for (i in 0..seg) {
                val th = i / seg.toFloat() * 2f * PI.toFloat()
                val x = rr * cos(th)
                val z = rr * sin(th)
                vertex(x * r, y * r * sy, z * r * sz, x / 1f, y / sy, z / sz, i / seg.toFloat(), 1f - j / rings.toFloat())
            }
        }
        for (j in 0 until rings) for (i in 0 until seg) {
            val a = base + j * (seg + 1) + i
            val b = a + seg + 1
            quadIdx(a, a + 1, b + 1, b)
        }
    }

    /** A torus lying flat (around the y axis). */
    fun torus(rMajor: Float, rMinor: Float, seg: Int = 28, sides: Int = 12, arc: Float = 1f) {
        val base = vn / 8
        for (i in 0..seg) {
            val a = i / seg.toFloat() * 2f * PI.toFloat() * arc
            val ca = cos(a); val sa = sin(a)
            for (j in 0..sides) {
                val b = j / sides.toFloat() * 2f * PI.toFloat()
                val cb = cos(b); val sb = sin(b)
                val rr = rMajor + rMinor * cb
                vertex(ca * rr, rMinor * sb, sa * rr, ca * cb, sb, sa * cb)
            }
        }
        for (i in 0 until seg) for (j in 0 until sides) {
            val a = base + i * (sides + 1) + j
            val b = a + sides + 1
            quadIdx(a, b, b + 1, a + 1)
        }
    }

    /** Turns a profile (radius, height pairs from bottom to top) around the y axis. */
    fun lathe(profile: List<Pair<Float, Float>>, seg: Int = 24, capTop: Boolean = false, capBottom: Boolean = false) {
        val n = profile.size
        if (n < 2) return
        val base = vn / 8
        for (k in 0 until n) {
            val (r, y) = profile[k]
            val (rp, yp) = profile[max(0, k - 1)]
            val (rn, yn) = profile[min(n - 1, k + 1)]
            // normal of the profile line at this point (outwards)
            var nr = yn - yp
            var ny = -(rn - rp)
            val l = sqrt(nr * nr + ny * ny).coerceAtLeast(1e-6f)
            nr /= l; ny /= l
            for (i in 0..seg) {
                val a = i / seg.toFloat() * 2f * PI.toFloat()
                vertex(cos(a) * r, y, sin(a) * r, cos(a) * nr, ny, sin(a) * nr, i / seg.toFloat(), 1f - k / (n - 1f))
            }
        }
        for (k in 0 until n - 1) for (i in 0 until seg) {
            val a = base + k * (seg + 1) + i
            val b = a + seg + 1
            quadIdx(a, a + 1, b + 1, b)
        }
        if (capTop) disc(profile.last().first, profile.last().second, seg, true)
        if (capBottom) disc(profile.first().first, profile.first().second, seg, false)
    }

    /**
     * A flat shape (polygon in x/z, counter-clockwise seen from above) pulled up from [y0] to [y1]
     * with straight sides.
     */
    fun extrude(poly: List<Pair<Float, Float>>, y0: Float, y1: Float, sides: Boolean = true, bottom: Boolean = false) {
        val tris = triangulate(poly)
        val top = vn / 8
        // top face, uv from the shape's bounds
        var minX = Float.MAX_VALUE; var maxX = -Float.MAX_VALUE; var minZ = Float.MAX_VALUE; var maxZ = -Float.MAX_VALUE
        for ((x, z) in poly) {
            minX = min(minX, x); maxX = max(maxX, x); minZ = min(minZ, z); maxZ = max(maxZ, z)
        }
        val sx = (maxX - minX).coerceAtLeast(1e-5f)
        val sz = (maxZ - minZ).coerceAtLeast(1e-5f)
        for ((x, z) in poly) vertex(x, y1, z, 0f, 1f, 0f, (x - minX) / sx, (z - minZ) / sz)
        for (k in tris.indices step 3) tri(top + tris[k], top + tris[k + 2], top + tris[k + 1])
        if (bottom) {
            val b = vn / 8
            for ((x, z) in poly) vertex(x, y0, z, 0f, -1f, 0f, (x - minX) / sx, (z - minZ) / sz)
            for (k in tris.indices step 3) tri(b + tris[k], b + tris[k + 1], b + tris[k + 2])
        }
        if (!sides) return
        val n = poly.size
        for (i in 0 until n) {
            val (ax, az) = poly[i]
            val (bx, bz) = poly[(i + 1) % n]
            quad(ax, y0, az, bx, y0, bz, bx, y1, bz, ax, y1, az)
        }
    }

    /** A smooth tube along a path of points (for handles, stems and cables). */
    fun tube(points: List<Triple<Float, Float, Float>>, r: Float, sides: Int = 10) {
        if (points.size < 2) return
        val base = vn / 8
        var px = 0f; var py = 0f; var pz = 1f
        for ((k, p) in points.withIndex()) {
            val a = points[max(0, k - 1)]
            val b = points[min(points.size - 1, k + 1)]
            var tx = b.first - a.first; var ty = b.second - a.second; var tz = b.third - a.third
            val tl = sqrt(tx * tx + ty * ty + tz * tz).coerceAtLeast(1e-6f)
            tx /= tl; ty /= tl; tz /= tl
            // a side vector perpendicular to the direction (kept stable along the tube)
            var ux = py * tz - pz * ty; var uy = pz * tx - px * tz; var uz = px * ty - py * tx
            var ul = sqrt(ux * ux + uy * uy + uz * uz)
            if (ul < 1e-3f) {
                ux = ty * 0f - tz * 1f; uy = tz * 0f - tx * 0f; uz = tx * 1f - ty * 0f
                ul = sqrt(ux * ux + uy * uy + uz * uz).coerceAtLeast(1e-6f)
                if (ul < 1e-3f) { ux = 1f; uy = 0f; uz = 0f; ul = 1f }
            }
            ux /= ul; uy /= ul; uz /= ul
            val wx = ty * uz - tz * uy; val wy = tz * ux - tx * uz; val wz = tx * uy - ty * ux
            px = wx; py = wy; pz = wz
            for (i in 0..sides) {
                val ang = i / sides.toFloat() * 2f * PI.toFloat()
                val c = cos(ang); val s = sin(ang)
                val nx = ux * c + wx * s; val ny = uy * c + wy * s; val nz = uz * c + wz * s
                vertex(p.first + nx * r, p.second + ny * r, p.third + nz * r, nx, ny, nz)
            }
        }
        for (k in 0 until points.size - 1) for (i in 0 until sides) {
            val a = base + k * (sides + 1) + i
            val b = a + sides + 1
            quadIdx(a, b, b + 1, a + 1)
        }
    }

    companion object {
        const val DEG = (PI / 180.0).toFloat()

        /** Ear clipping for a simple polygon (counter-clockwise or clockwise). */
        fun triangulate(poly: List<Pair<Float, Float>>): IntArray {
            val n = poly.size
            if (n < 3) return IntArray(0)
            var area = 0f
            for (i in 0 until n) {
                val (ax, az) = poly[i]
                val (bx, bz) = poly[(i + 1) % n]
                area += ax * bz - bx * az
            }
            val ccw = area > 0f
            val rest = (0 until n).toMutableList()
            val out = ArrayList<Int>()
            var guard = 0
            while (rest.size > 3 && guard++ < n * n) {
                var clipped = false
                for (k in rest.indices) {
                    val i0 = rest[(k + rest.size - 1) % rest.size]
                    val i1 = rest[k]
                    val i2 = rest[(k + 1) % rest.size]
                    val (ax, az) = poly[i0]
                    val (bx, bz) = poly[i1]
                    val (cx, cz) = poly[i2]
                    val cross = (bx - ax) * (cz - az) - (bz - az) * (cx - ax)
                    if (ccw && cross <= 0f || !ccw && cross >= 0f) continue
                    var inside = false
                    for (j in rest) {
                        if (j == i0 || j == i1 || j == i2) continue
                        val (px, pz) = poly[j]
                        if (inTri(px, pz, ax, az, bx, bz, cx, cz)) {
                            inside = true
                            break
                        }
                    }
                    if (inside) continue
                    if (ccw) { out += i0; out += i1; out += i2 } else { out += i0; out += i2; out += i1 }
                    rest.removeAt(k)
                    clipped = true
                    break
                }
                if (!clipped) break
            }
            if (rest.size == 3) {
                if (ccw) { out += rest[0]; out += rest[1]; out += rest[2] } else { out += rest[0]; out += rest[2]; out += rest[1] }
            }
            return out.toIntArray()
        }

        private fun inTri(px: Float, pz: Float, ax: Float, az: Float, bx: Float, bz: Float, cx: Float, cz: Float): Boolean {
            val d1 = (px - bx) * (az - bz) - (ax - bx) * (pz - bz)
            val d2 = (px - cx) * (bz - cz) - (bx - cx) * (pz - cz)
            val d3 = (px - ax) * (cz - az) - (cx - ax) * (pz - az)
            val neg = d1 < 0 || d2 < 0 || d3 < 0
            val pos = d1 > 0 || d2 > 0 || d3 > 0
            return !(neg && pos)
        }

        /** Points of a star shape (x/z) with [n] tips. */
        fun star(n: Int, rOut: Float, rIn: Float, rot: Float = -90f): List<Pair<Float, Float>> =
            (0 until n * 2).map { i ->
                val a = (rot + i * 180f / n) * DEG
                val r = if (i % 2 == 0) rOut else rIn
                cos(a) * r to sin(a) * r
            }

        /** Points of a heart shape (x/z), point towards +z. */
        fun heart(s: Float, n: Int = 40): List<Pair<Float, Float>> =
            (0 until n).map { i ->
                val t = i / n.toFloat() * 2f * PI.toFloat()
                val x = 16f * sin(t) * sin(t) * sin(t)
                val y = 13f * cos(t) - 5f * cos(2 * t) - 2f * cos(3 * t) - cos(4 * t)
                x / 17f * s to -y / 17f * s
            }.reversed()

        /** Points of an ellipse (x/z). */
        fun ellipse(rx: Float, rz: Float, n: Int = 32): List<Pair<Float, Float>> =
            (0 until n).map { i ->
                val a = i / n.toFloat() * 2f * PI.toFloat()
                cos(a) * rx to sin(a) * rz
            }
    }
}
