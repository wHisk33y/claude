package de.knuffi.app.render.gl

import android.graphics.Bitmap
import android.graphics.Path
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import java.nio.IntBuffer
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sign
import kotlin.math.sin
import kotlin.math.sqrt

/** Growable vertex/index storage that is re-used from frame to frame (no garbage per frame). */
class MeshBuilder {
    var v = FloatArray(6 * 1024)
    var idx = IntArray(6 * 1024)
    var vCount = 0
    var iCount = 0
    private var vb: FloatBuffer? = null
    private var ib: IntBuffer? = null

    fun reset() {
        vCount = 0
        iCount = 0
    }

    fun vertex(x: Float, y: Float, z: Float, nx: Float, ny: Float, nz: Float): Int {
        if ((vCount + 1) * 6 > v.size) v = v.copyOf(v.size * 2)
        val k = vCount * 6
        v[k] = x; v[k + 1] = y; v[k + 2] = z
        val l = sqrt(nx * nx + ny * ny + nz * nz).coerceAtLeast(1e-6f)
        v[k + 3] = nx / l; v[k + 4] = ny / l; v[k + 5] = nz / l
        return vCount++
    }

    fun tri(a: Int, b: Int, c: Int) {
        if (iCount + 3 > idx.size) idx = idx.copyOf(idx.size * 2)
        idx[iCount++] = a; idx[iCount++] = b; idx[iCount++] = c
    }

    fun build(): Mesh {
        var fb = vb
        if (fb == null || fb.capacity() < vCount * 6) {
            fb = ByteBuffer.allocateDirect(v.size * 4).order(ByteOrder.nativeOrder()).asFloatBuffer()
            vb = fb
        }
        fb!!.clear()
        fb.put(v, 0, vCount * 6).position(0)
        var b = ib
        if (b == null || b.capacity() < max(iCount, 1)) {
            b = ByteBuffer.allocateDirect(idx.size * 4).order(ByteOrder.nativeOrder()).asIntBuffer()
            ib = b
        }
        b!!.clear()
        b.put(idx, 0, iCount).position(0)
        return Mesh(fb, vCount, b, iCount)
    }
}

/**
 * The body as seen from the front, row by row: centre and half width for every height.
 * The 3D body is a lathe of these rows with a (super)elliptic cross-section.
 */
class Profile {
    var rows = 0
    var y = FloatArray(64)
    var c = FloatArray(64)
    var hw = FloatArray(64)
    /** Depth of the body relative to its width. */
    var depth = 0.86f
    /** Cross-section exponent: 2 = round, higher = squarer. */
    var power = 2f
    private val xs = FloatArray(16)

    fun fromPath(p: Path, n: Int = 44) {
        val pts = p.approximate(0.6f)
        var minY = Float.MAX_VALUE
        var maxY = -Float.MAX_VALUE
        var k = 0
        while (k < pts.size) {
            val yy = pts[k + 2]
            if (yy < minY) minY = yy
            if (yy > maxY) maxY = yy
            k += 3
        }
        rows = n + 1
        if (y.size < rows) {
            y = FloatArray(rows); c = FloatArray(rows); hw = FloatArray(rows)
        }
        for (i in 0..n) {
            val f = (1f - cos(Math.PI.toFloat() * i / n)) / 2f
            val yy = minY + (maxY - minY) * f
            y[i] = yy
            if (i == 0 || i == n) {
                // poles: use the neighbouring row's centre
                c[i] = Float.NaN
                hw[i] = 0f
                continue
            }
            var m = 0
            var j = 0
            val cnt = pts.size / 3
            while (j < cnt) {
                val a = j
                val b = (j + 1) % cnt
                val y0 = pts[a * 3 + 2]
                val y1 = pts[b * 3 + 2]
                if ((y0 <= yy && y1 > yy) || (y1 <= yy && y0 > yy)) {
                    val t = (yy - y0) / (y1 - y0)
                    if (m < xs.size) xs[m++] = pts[a * 3 + 1] + t * (pts[b * 3 + 1] - pts[a * 3 + 1])
                }
                j++
            }
            if (m < 2) {
                c[i] = Float.NaN
                hw[i] = 0f
            } else {
                var lo = Float.MAX_VALUE
                var hi = -Float.MAX_VALUE
                for (q in 0 until m) {
                    lo = min(lo, xs[q]); hi = max(hi, xs[q])
                }
                c[i] = (lo + hi) / 2f
                hw[i] = (hi - lo) / 2f
            }
        }
        // fill missing centres
        for (i in 0 until rows) if (c[i].isNaN()) c[i] = if (i == 0) c.firstValid(1) else c[i - 1]
    }

    private fun FloatArray.firstValid(from: Int): Float {
        for (i in from until rows) if (!this[i].isNaN()) return this[i]
        return 0f
    }

    /** Half width and centre at height [yy]. */
    private fun at(yy: Float, outC: FloatArray): Float {
        if (rows < 2 || yy <= y[0] || yy >= y[rows - 1]) {
            outC[0] = if (rows > 0) c[0] else 0f
            return 0f
        }
        var lo = 0
        var hi = rows - 1
        while (hi - lo > 1) {
            val mid = (lo + hi) / 2
            if (y[mid] <= yy) lo = mid else hi = mid
        }
        val t = (yy - y[lo]) / max(1e-4f, y[hi] - y[lo])
        outC[0] = c[lo] + (c[hi] - c[lo]) * t
        return hw[lo] + (hw[hi] - hw[lo]) * t
    }

    private val tmpC = FloatArray(1)

    /** Depth of the front surface at (x, y), 0 outside the body. */
    fun front(x: Float, yy: Float): Float {
        val h = at(yy, tmpC)
        if (h <= 0f) return 0f
        val t = abs(x - tmpC[0]) / h
        if (t >= 1f) return 0f
        return depth * h * (1f - t.pow(power)).pow(1f / power)
    }

    private fun se(v: Float): Float = sign(v) * abs(v).pow(2f / power)

    /** Builds the lathe mesh. [wave] moves the bottom (ghosts). */
    fun lathe(mb: MeshBuilder, seg: Int = 44, wave: ((Float, Float) -> Float)? = null) {
        mb.reset()
        val n = rows
        val px = FloatArray(n * (seg + 1))
        val py = FloatArray(n * (seg + 1))
        val pz = FloatArray(n * (seg + 1))
        for (i in 0 until n) for (j in 0..seg) {
            val th = (2.0 * Math.PI * j / seg).toFloat()
            val k = i * (seg + 1) + j
            val x = c[i] + hw[i] * se(cos(th))
            px[k] = x
            pz[k] = depth * hw[i] * se(sin(th))
            py[k] = y[i] + (wave?.invoke(x, y[i]) ?: 0f)
        }
        for (i in 0 until n) for (j in 0..seg) {
            val k = i * (seg + 1) + j
            var nx: Float
            var ny: Float
            var nz: Float
            if (i == 0 || i == n - 1 || hw[i] < 1e-3f) {
                nx = 0f; nz = 0f; ny = if (i < n / 2) -1f else 1f
            } else {
                val jp = i * (seg + 1) + (if (j == seg) 1 else j + 1)
                val jm = i * (seg + 1) + (if (j == 0) seg - 1 else j - 1)
                val ip = (i + 1) * (seg + 1) + j
                val im = (i - 1) * (seg + 1) + j
                val ax = px[jp] - px[jm]; val ay = py[jp] - py[jm]; val az = pz[jp] - pz[jm]
                val bx = px[ip] - px[im]; val by = py[ip] - py[im]; val bz = pz[ip] - pz[im]
                nx = ay * bz - az * by
                ny = az * bx - ax * bz
                nz = ax * by - ay * bx
                // point outwards
                val ox = px[k] - c[i]; val oz = pz[k]
                if (nx * ox + nz * oz < 0f) {
                    nx = -nx; ny = -ny; nz = -nz
                }
            }
            mb.vertex(px[k], py[k], pz[k], nx, ny, nz)
        }
        for (i in 0 until n - 1) for (j in 0 until seg) {
            val a = i * (seg + 1) + j
            val b = a + seg + 1
            mb.tri(a, b, a + 1)
            mb.tri(a + 1, b, b + 1)
        }
    }
}

/**
 * Turns a painted layer into a soft relief: every opaque island of the bitmap gets puffed up
 * like a cushion (rounded edges, height from the distance to the outline).
 */
class Relief {
    private var pixels = IntArray(0)
    private var a = FloatArray(0)
    private var d = FloatArray(0)
    private var h = FloatArray(0)
    private var vid = IntArray(0)
    private var zc = FloatArray(0)

    /**
     * @param base depth of the middle of the relief at (x, y) (0 = through the body's centre plane)
     * @param radius rounding radius of the edges (= half thickness of thick parts), body units
     * @return false if the layer is empty
     */
    fun build(
        mb: MeshBuilder, bmp: Bitmap,
        left: Float, top: Float, right: Float, bottom: Float,
        grid: Int, radius: Float, backFactor: Float,
        base: (Float, Float) -> Float,
    ): Boolean {
        mb.reset()
        val tw = bmp.width
        val th = bmp.height
        if (pixels.size < tw * th) pixels = IntArray(tw * th)
        bmp.getPixels(pixels, 0, tw, 0, 0, tw, th)
        val gw: Int
        val gh: Int
        if (tw >= th) {
            gw = grid; gh = max(2, grid * th / tw)
        } else {
            gh = grid; gw = max(2, grid * tw / th)
        }
        val cw = (right - left) / gw
        val ch = (bottom - top) / gh
        val cells = gw * gh
        if (a.size < cells) {
            a = FloatArray(cells); d = FloatArray(cells); h = FloatArray(cells)
        }
        var any = false
        for (j in 0 until gh) for (i in 0 until gw) {
            // max alpha in the cell (sampled), so thin lines still count
            var m = 0
            val x0 = i * tw / gw
            val x1 = max(x0 + 1, (i + 1) * tw / gw)
            val y0 = j * th / gh
            val y1 = max(y0 + 1, (j + 1) * th / gh)
            val sx = max(1, (x1 - x0) / 3)
            val sy = max(1, (y1 - y0) / 3)
            var yy = y0
            while (yy < y1) {
                var xx = x0
                while (xx < x1) {
                    val al = pixels[yy * tw + xx] ushr 24
                    if (al > m) m = al
                    xx += sx
                }
                yy += sy
            }
            val k = j * gw + i
            a[k] = m / 255f
            if (m > 60) any = true
        }
        if (!any) return false

        // chamfer distance to the outline (in body units)
        val inf = 1e9f
        val dg = sqrt(cw * cw + ch * ch)
        for (k in 0 until cells) d[k] = if (a[k] > 0.24f) inf else 0f
        for (j in 0 until gh) for (i in 0 until gw) {
            val k = j * gw + i
            if (d[k] == 0f) continue
            var v = d[k]
            if (i > 0) v = min(v, d[k - 1] + cw)
            if (j > 0) {
                v = min(v, d[k - gw] + ch)
                if (i > 0) v = min(v, d[k - gw - 1] + dg)
                if (i < gw - 1) v = min(v, d[k - gw + 1] + dg)
            }
            if (i == 0 || j == 0 || i == gw - 1 || j == gh - 1) v = min(v, min(cw, ch))
            d[k] = v
        }
        for (j in gh - 1 downTo 0) for (i in gw - 1 downTo 0) {
            val k = j * gw + i
            if (d[k] == 0f) continue
            var v = d[k]
            if (i < gw - 1) v = min(v, d[k + 1] + cw)
            if (j < gh - 1) {
                v = min(v, d[k + gw] + ch)
                if (i < gw - 1) v = min(v, d[k + gw + 1] + dg)
                if (i > 0) v = min(v, d[k + gw - 1] + dg)
            }
            d[k] = v
        }
        for (k in 0 until cells) {
            val dd = (d[k] - 0.5f * min(cw, ch)).coerceAtLeast(0f)
            val q = 1f - min(dd / radius, 1f)
            h[k] = if (d[k] == 0f) 0f else radius * sqrt(max(0f, 1f - q * q))
        }

        // corner heights and depths
        val cgw = gw + 1
        val corners = cgw * (gh + 1)
        if (vid.size < corners * 2) vid = IntArray(corners * 2)
        if (zc.size < corners * 3) zc = FloatArray(corners * 3)
        for (j in 0..gh) for (i in 0..gw) {
            var s = 0f
            var cnt = 0
            for (dj in -1..0) for (di in -1..0) {
                val ci = i + di
                val cj = j + dj
                if (ci < 0 || cj < 0 || ci >= gw || cj >= gh) continue
                s += h[cj * gw + ci]
                cnt++
            }
            val k = j * cgw + i
            val hh = s / 4f
            val bz = base(left + i * cw, top + j * ch)
            zc[k * 3] = hh
            zc[k * 3 + 1] = bz
            vid[k] = -1
            vid[corners + k] = -1
        }
        fun active(i: Int, j: Int): Boolean {
            for (dj in -1..1) for (di in -1..1) {
                val ci = i + di
                val cj = j + dj
                if (ci < 0 || cj < 0 || ci >= gw || cj >= gh) continue
                if (a[cj * gw + ci] > 0.24f) return true
            }
            return false
        }
        fun zf(i: Int, j: Int): Float {
            val ii = i.coerceIn(0, gw)
            val jj = j.coerceIn(0, gh)
            val k = jj * cgw + ii
            return zc[k * 3 + 1] + zc[k * 3]
        }
        fun zb(i: Int, j: Int): Float {
            val ii = i.coerceIn(0, gw)
            val jj = j.coerceIn(0, gh)
            val k = jj * cgw + ii
            return zc[k * 3 + 1] - zc[k * 3] * backFactor
        }
        fun vert(i: Int, j: Int, back: Boolean): Int {
            val k = j * cgw + i
            val slot = if (back) corners + k else k
            if (vid[slot] >= 0) return vid[slot]
            val x = left + i * cw
            val y = top + j * ch
            val id = if (!back) {
                val dx = (zf(i + 1, j) - zf(i - 1, j)) / (2f * cw)
                val dy = (zf(i, j + 1) - zf(i, j - 1)) / (2f * ch)
                mb.vertex(x, y, zf(i, j), -dx, -dy, 1f)
            } else {
                val dx = (zb(i + 1, j) - zb(i - 1, j)) / (2f * cw)
                val dy = (zb(i, j + 1) - zb(i, j - 1)) / (2f * ch)
                mb.vertex(x, y, zb(i, j), dx, dy, -1f)
            }
            vid[slot] = id
            return id
        }
        for (j in 0 until gh) for (i in 0 until gw) {
            if (!active(i, j)) continue
            for (back in booleanArrayOf(false, true)) {
                val v00 = vert(i, j, back)
                val v10 = vert(i + 1, j, back)
                val v01 = vert(i, j + 1, back)
                val v11 = vert(i + 1, j + 1, back)
                mb.tri(v00, v01, v10)
                mb.tri(v10, v01, v11)
            }
        }
        return true
    }
}
