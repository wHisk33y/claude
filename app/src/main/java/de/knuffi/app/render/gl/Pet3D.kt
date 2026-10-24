package de.knuffi.app.render.gl

import android.graphics.Bitmap
import android.opengl.EGL14
import android.opengl.EGLConfig
import android.opengl.EGLContext
import android.opengl.EGLDisplay
import android.opengl.EGLSurface
import android.opengl.GLES30
import android.opengl.GLUtils
import android.opengl.Matrix
import android.util.Log
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import java.nio.IntBuffer

/**
 * Small offscreen OpenGL ES 3 renderer for the real 3D pet.
 *
 * The pet is rendered into an offscreen framebuffer and read back into a [Bitmap], so the
 * rest of the app (Compose canvas, live wallpaper, floating pet, widgets) keeps drawing with
 * the plain Android canvas. One GL context is shared by all threads and guarded by a lock.
 */
object Pet3D {
    private const val TAG = "Pet3D"
    private const val EGL_OPENGL_ES3_BIT = 0x40
    const val MAX_SIZE = 1024

    /** Set from the settings (3D or drawn look). */
    @Volatile
    var wanted = true

    @Volatile
    private var failed = false

    val available: Boolean get() = wanted && !failed

    /** True if 3D failed on this device. */
    val broken: Boolean get() = failed

    private val lock = Any()
    private var display: EGLDisplay = EGL14.EGL_NO_DISPLAY
    private var context: EGLContext = EGL14.EGL_NO_CONTEXT
    private var surface: EGLSurface = EGL14.EGL_NO_SURFACE

    private var program = 0
    private val loc = HashMap<String, Int>()
    private var vbo = 0
    private var ibo = 0
    private val textures = IntArray(3)
    private val fbo = IntArray(2)
    private val rbo = IntArray(3)
    private var fbW = 0
    private var fbH = 0
    private var samples = 4
    private var readBuf: ByteBuffer? = null

    private val sphere: Mesh by lazy { Mesh.sphere(24, 16) }

    /** One frame for the GL renderer. Coordinates are the pet's body space (canvas px, y down, z towards the viewer). */
    class Frame {
        var left = 0f
        var top = 0f
        var right = 1f
        var bottom = 1f
        var width = 1
        var height = 1
        var yaw = 0f

        /** Leaning forward in radians (a bow). */
        var pitch = 0f
        var depth = 1f
        var bodyBottom = 0f
        var bodyH = 1f
        var tint = FloatArray(4)
        var bodyColor = FloatArray(3)
        var lightColor = FloatArray(3)
        /** fur, glitter, jelly, 1 / fibre size */
        val surface = FloatArray(4)
        /** Colour of the main light and of the light all around (time of day). */
        val key = floatArrayOf(1f, 1f, 1f)
        val amb = floatArrayOf(1f, 1f, 1f)
        /** Soft jiggle: amplitude, wave number, time. */
        val wobble = FloatArray(3)
        var time = 0f
        var body: Mesh? = null
        var decal: Bitmap? = null
        var back: Mesh? = null
        var backTex: Bitmap? = null
        var front: Mesh? = null
        var frontTex: Bitmap? = null
        val parts = ArrayList<Part>()
    }

    /** A sphere scaled, rotated and moved into place (feet, arms, paws). */
    class Part(val model: FloatArray, val color: FloatArray, val gloss: Float = 0.6f)

    /** Shared scratch memory for building a frame (only used under the lock). */
    class Scratch {
        val frame = Frame()
        val profile = Profile()
        val relief = Relief()
        val bodyMesh = MeshBuilder()
        val backMesh = MeshBuilder()
        val frontMesh = MeshBuilder()
        private val cache = Array(3) { LinkedHashMap<Long, Bitmap>() }
        private val canvas = android.graphics.Canvas()
        private val pool = ArrayList<FloatArray>()
        private var used = 0

        /** A cleared bitmap of exactly w x h for layer [i] (0 decal, 1 back, 2 front), with a canvas drawing into it. */
        fun layer(i: Int, w: Int, h: Int): Pair<Bitmap, android.graphics.Canvas> {
            val key = (w.toLong() shl 32) or h.toLong()
            val m = cache[i]
            var b = m.remove(key)
            if (b == null) {
                b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                if (m.size >= 4) m.remove(m.keys.first())?.recycle()
            } else {
                b.eraseColor(0)
            }
            m[key] = b!!
            canvas.setBitmap(b)
            return b to canvas
        }

        fun matrix(): FloatArray {
            if (used == pool.size) pool += FloatArray(16)
            return pool[used++]
        }

        internal fun begin() {
            used = 0
            frame.parts.clear()
            frame.body = null
            frame.decal = null
            frame.back = null
            frame.backTex = null
            frame.front = null
            frame.frontTex = null
            frame.tint.fill(0f)
            frame.surface.fill(0f)
            frame.key.fill(1f)
            frame.amb.fill(1f)
            frame.wobble.fill(0f)
            frame.time = 0f
        }
    }

    private val scratch = Scratch()

    // Time budget: when many pets are on screen they share the GPU time and update less often.
    private var windowStart = 0L
    private var spent = 0L
    private const val WINDOW_NS = 100_000_000L
    private const val BUDGET_NS = 38_000_000L

    /** True if a pet that already has a picture may render a new one now. */
    fun mayUpdate(): Boolean {
        val now = System.nanoTime()
        synchronized(this) {
            if (now - windowStart > WINDOW_NS) {
                windowStart = now
                spent = 0L
            }
            return spent < BUDGET_NS
        }
    }

    private fun spend(ns: Long) {
        synchronized(this) { spent += ns }
    }

    /**
     * Builds a frame with [build] (called under the lock with shared scratch memory) and renders it
     * into [out] (re-used if it has the right size). Returns null if 3D is not possible.
     */
    fun render(out: Bitmap?, build: (Scratch) -> Frame?): Bitmap? {
        if (!available) return null
        val start = System.nanoTime()
        try {
            return renderLocked(out, build)
        } finally {
            spend(System.nanoTime() - start)
        }
    }

    private fun renderLocked(out: Bitmap?, build: (Scratch) -> Frame?): Bitmap? {
        synchronized(lock) {
            try {
                if (!ensureContext()) return null
                scratch.begin()
                val f = build(scratch) ?: return null
                if (!EGL14.eglMakeCurrent(display, surface, surface, context)) {
                    fail("makeCurrent ${EGL14.eglGetError()}")
                    return null
                }
                try {
                    return draw(f, out)
                } finally {
                    EGL14.eglMakeCurrent(display, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT)
                }
            } catch (t: Throwable) {
                fail(t.toString())
                return null
            }
        }
    }

    private fun fail(msg: String) {
        Log.w(TAG, "3D disabled: $msg")
        failed = true
    }

    // ------------------------------------------------------------------ setup

    private fun ensureContext(): Boolean {
        if (context != EGL14.EGL_NO_CONTEXT) return true
        display = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
        val ver = IntArray(2)
        if (!EGL14.eglInitialize(display, ver, 0, ver, 1)) return failInit("init")
        val attribs = intArrayOf(
            EGL14.EGL_RED_SIZE, 8, EGL14.EGL_GREEN_SIZE, 8, EGL14.EGL_BLUE_SIZE, 8, EGL14.EGL_ALPHA_SIZE, 8,
            EGL14.EGL_RENDERABLE_TYPE, EGL_OPENGL_ES3_BIT,
            EGL14.EGL_SURFACE_TYPE, EGL14.EGL_PBUFFER_BIT,
            EGL14.EGL_NONE,
        )
        val configs = arrayOfNulls<EGLConfig>(1)
        val n = IntArray(1)
        if (!EGL14.eglChooseConfig(display, attribs, 0, configs, 0, 1, n, 0) || n[0] == 0) return failInit("config")
        val cfg = configs[0]
        context = EGL14.eglCreateContext(display, cfg, EGL14.EGL_NO_CONTEXT, intArrayOf(EGL14.EGL_CONTEXT_CLIENT_VERSION, 3, EGL14.EGL_NONE), 0)
        if (context == EGL14.EGL_NO_CONTEXT) return failInit("context")
        surface = EGL14.eglCreatePbufferSurface(display, cfg, intArrayOf(EGL14.EGL_WIDTH, 1, EGL14.EGL_HEIGHT, 1, EGL14.EGL_NONE), 0)
        if (surface == EGL14.EGL_NO_SURFACE) return failInit("surface")
        if (!EGL14.eglMakeCurrent(display, surface, surface, context)) return failInit("current")
        try {
            program = buildProgram(Shaders.VERTEX, Shaders.FRAGMENT)
            val ids = IntArray(2)
            GLES30.glGenBuffers(2, ids, 0)
            vbo = ids[0]
            ibo = ids[1]
            GLES30.glGenTextures(3, textures, 0)
            for (t in textures) {
                GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, t)
                GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MIN_FILTER, GLES30.GL_LINEAR)
                GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MAG_FILTER, GLES30.GL_LINEAR)
                GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_S, GLES30.GL_CLAMP_TO_EDGE)
                GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_T, GLES30.GL_CLAMP_TO_EDGE)
            }
            GLES30.glGenFramebuffers(2, fbo, 0)
            GLES30.glGenRenderbuffers(3, rbo, 0)
            val max = IntArray(1)
            GLES30.glGetIntegerv(GLES30.GL_MAX_SAMPLES, max, 0)
            samples = max[0].coerceIn(0, 4)
        } finally {
            EGL14.eglMakeCurrent(display, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT)
        }
        return true
    }

    private fun failInit(what: String): Boolean {
        fail("egl $what ${EGL14.eglGetError()}")
        context = EGL14.EGL_NO_CONTEXT
        return false
    }

    private fun buildProgram(vs: String, fs: String): Int {
        fun shader(type: Int, src: String): Int {
            val s = GLES30.glCreateShader(type)
            GLES30.glShaderSource(s, src)
            GLES30.glCompileShader(s)
            val ok = IntArray(1)
            GLES30.glGetShaderiv(s, GLES30.GL_COMPILE_STATUS, ok, 0)
            if (ok[0] == 0) throw IllegalStateException("shader: " + GLES30.glGetShaderInfoLog(s))
            return s
        }
        val p = GLES30.glCreateProgram()
        GLES30.glAttachShader(p, shader(GLES30.GL_VERTEX_SHADER, vs))
        GLES30.glAttachShader(p, shader(GLES30.GL_FRAGMENT_SHADER, fs))
        GLES30.glBindAttribLocation(p, 0, "aPos")
        GLES30.glBindAttribLocation(p, 1, "aNrm")
        GLES30.glLinkProgram(p)
        val ok = IntArray(1)
        GLES30.glGetProgramiv(p, GLES30.GL_LINK_STATUS, ok, 0)
        if (ok[0] == 0) throw IllegalStateException("link: " + GLES30.glGetProgramInfoLog(p))
        return p
    }

    private fun u(name: String): Int = loc.getOrPut(name) { GLES30.glGetUniformLocation(program, name) }

    private fun ensureTarget(w: Int, h: Int) {
        if (w <= fbW && h <= fbH) return
        fbW = maxOf(w, fbW, 256).coerceAtMost(MAX_SIZE)
        fbH = maxOf(h, fbH, 256).coerceAtMost(MAX_SIZE)
        // multisampled colour + depth
        GLES30.glBindRenderbuffer(GLES30.GL_RENDERBUFFER, rbo[0])
        GLES30.glRenderbufferStorageMultisample(GLES30.GL_RENDERBUFFER, samples, GLES30.GL_RGBA8, fbW, fbH)
        GLES30.glBindRenderbuffer(GLES30.GL_RENDERBUFFER, rbo[1])
        GLES30.glRenderbufferStorageMultisample(GLES30.GL_RENDERBUFFER, samples, GLES30.GL_DEPTH_COMPONENT24, fbW, fbH)
        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, fbo[0])
        GLES30.glFramebufferRenderbuffer(GLES30.GL_FRAMEBUFFER, GLES30.GL_COLOR_ATTACHMENT0, GLES30.GL_RENDERBUFFER, rbo[0])
        GLES30.glFramebufferRenderbuffer(GLES30.GL_FRAMEBUFFER, GLES30.GL_DEPTH_ATTACHMENT, GLES30.GL_RENDERBUFFER, rbo[1])
        // resolve target
        GLES30.glBindRenderbuffer(GLES30.GL_RENDERBUFFER, rbo[2])
        GLES30.glRenderbufferStorage(GLES30.GL_RENDERBUFFER, GLES30.GL_RGBA8, fbW, fbH)
        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, fbo[1])
        GLES30.glFramebufferRenderbuffer(GLES30.GL_FRAMEBUFFER, GLES30.GL_COLOR_ATTACHMENT0, GLES30.GL_RENDERBUFFER, rbo[2])
        val status = GLES30.glCheckFramebufferStatus(GLES30.GL_FRAMEBUFFER)
        if (status != GLES30.GL_FRAMEBUFFER_COMPLETE) throw IllegalStateException("fbo $status")
    }

    // ------------------------------------------------------------------ drawing

    private fun draw(f: Frame, out: Bitmap?): Bitmap {
        val w = f.width.coerceIn(1, MAX_SIZE)
        val h = f.height.coerceIn(1, MAX_SIZE)
        ensureTarget(w, h)
        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, fbo[0])
        GLES30.glViewport(0, 0, w, h)
        GLES30.glClearColor(0f, 0f, 0f, 0f)
        GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT or GLES30.GL_DEPTH_BUFFER_BIT)
        GLES30.glEnable(GLES30.GL_DEPTH_TEST)
        GLES30.glDepthFunc(GLES30.GL_LEQUAL)
        GLES30.glDisable(GLES30.GL_CULL_FACE)
        GLES30.glDisable(GLES30.GL_BLEND)
        GLES30.glUseProgram(program)

        GLES30.glUniform4f(u("uRegion"), f.left, f.top, f.right, f.bottom)
        GLES30.glUniform2f(u("uYaw"), kotlin.math.cos(f.yaw), kotlin.math.sin(f.yaw))
        GLES30.glUniform2f(u("uPitch"), kotlin.math.cos(f.pitch), kotlin.math.sin(f.pitch))
        GLES30.glUniform1f(u("uZRange"), maxOf(f.right - f.left, f.bottom - f.top))
        GLES30.glUniform2f(u("uFloor"), f.bodyBottom, f.bodyH)
        GLES30.glUniform4fv(u("uTint"), 1, f.tint, 0)
        GLES30.glUniform3fv(u("uRim"), 1, f.lightColor, 0)
        GLES30.glUniform1i(u("uTex"), 0)
        GLES30.glUniform4fv(u("uSurface"), 1, f.surface, 0)
        GLES30.glUniform3fv(u("uKey"), 1, f.key, 0)
        GLES30.glUniform3fv(u("uAmb"), 1, f.amb, 0)
        GLES30.glUniform3fv(u("uWobble"), 1, f.wobble, 0)
        GLES30.glUniform1f(u("uTime"), f.time)

        // body with the painted face, belly and pattern
        f.body?.let { body ->
            upload(textures[0], f.decal)
            setMaterial(if (f.decal != null) 1 else 0, f.bodyColor, 0.75f, 1f)
            setModel(IDENTITY)
            drawMesh(body)
        }
        // feet, arms
        for (p in f.parts) {
            setMaterial(0, p.color, p.gloss, 1f)
            setModel(p.model)
            drawMesh(sphere)
        }
        setModel(IDENTITY)
        // soft relief parts behind and in front of the body (ears, wings, hats, ...)
        if (samples > 0) GLES30.glEnable(GLES30.GL_SAMPLE_ALPHA_TO_COVERAGE)
        f.back?.let { m ->
            upload(textures[1], f.backTex)
            setMaterial(2, f.bodyColor, 0.5f, 0.55f)
            drawMesh(m)
        }
        f.front?.let { m ->
            upload(textures[2], f.frontTex)
            setMaterial(2, f.bodyColor, 0.5f, 0.55f)
            drawMesh(m)
        }
        GLES30.glDisable(GLES30.GL_SAMPLE_ALPHA_TO_COVERAGE)

        // resolve and read back
        GLES30.glBindFramebuffer(GLES30.GL_READ_FRAMEBUFFER, fbo[0])
        GLES30.glBindFramebuffer(GLES30.GL_DRAW_FRAMEBUFFER, fbo[1])
        GLES30.glBlitFramebuffer(0, 0, w, h, 0, 0, w, h, GLES30.GL_COLOR_BUFFER_BIT, GLES30.GL_NEAREST)
        GLES30.glBindFramebuffer(GLES30.GL_READ_FRAMEBUFFER, fbo[1])
        val need = w * h * 4
        val buf = readBuf?.takeIf { it.capacity() >= need } ?: ByteBuffer.allocateDirect(need).order(ByteOrder.nativeOrder()).also { readBuf = it }
        buf.clear()
        GLES30.glPixelStorei(GLES30.GL_PACK_ALIGNMENT, 1)
        GLES30.glReadPixels(0, 0, w, h, GLES30.GL_RGBA, GLES30.GL_UNSIGNED_BYTE, buf)
        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, 0)
        val err = GLES30.glGetError()
        if (err != GLES30.GL_NO_ERROR) throw IllegalStateException("gl error $err")
        val bmp = out?.takeIf { it.width == w && it.height == h && it.isMutable } ?: Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        buf.position(0)
        buf.limit(need)
        bmp.copyPixelsFromBuffer(buf)
        return bmp
    }

    private val IDENTITY = FloatArray(16).also { Matrix.setIdentityM(it, 0) }
    private val nrm = FloatArray(16)
    private val inv = FloatArray(16)
    private val nrm3 = FloatArray(9)

    private fun setModel(m: FloatArray) {
        GLES30.glUniformMatrix4fv(u("uModel"), 1, false, m, 0)
        Matrix.invertM(inv, 0, m, 0)
        Matrix.transposeM(nrm, 0, inv, 0)
        nrm3[0] = nrm[0]; nrm3[1] = nrm[1]; nrm3[2] = nrm[2]
        nrm3[3] = nrm[4]; nrm3[4] = nrm[5]; nrm3[5] = nrm[6]
        nrm3[6] = nrm[8]; nrm3[7] = nrm[9]; nrm3[8] = nrm[10]
        GLES30.glUniformMatrix3fv(u("uNrm"), 1, false, nrm3, 0)
    }

    private fun setMaterial(mode: Int, color: FloatArray, gloss: Float, lightAmount: Float) {
        GLES30.glUniform1i(u("uMode"), mode)
        GLES30.glUniform3fv(u("uBase"), 1, color, 0)
        GLES30.glUniform1f(u("uGloss"), gloss)
        GLES30.glUniform1f(u("uLit"), lightAmount)
    }

    private fun upload(tex: Int, bmp: Bitmap?) {
        GLES30.glActiveTexture(GLES30.GL_TEXTURE0)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, tex)
        if (bmp != null) GLUtils.texImage2D(GLES30.GL_TEXTURE_2D, 0, bmp, 0)
    }

    private fun drawMesh(m: Mesh) {
        if (m.indexCount == 0) return
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, vbo)
        GLES30.glBufferData(GLES30.GL_ARRAY_BUFFER, m.vertexCount * 6 * 4, m.vertices.position(0), GLES30.GL_STREAM_DRAW)
        GLES30.glBindBuffer(GLES30.GL_ELEMENT_ARRAY_BUFFER, ibo)
        GLES30.glBufferData(GLES30.GL_ELEMENT_ARRAY_BUFFER, m.indexCount * 4, m.indices.position(0), GLES30.GL_STREAM_DRAW)
        GLES30.glEnableVertexAttribArray(0)
        GLES30.glVertexAttribPointer(0, 3, GLES30.GL_FLOAT, false, 24, 0)
        GLES30.glEnableVertexAttribArray(1)
        GLES30.glVertexAttribPointer(1, 3, GLES30.GL_FLOAT, false, 24, 12)
        GLES30.glDrawElements(GLES30.GL_TRIANGLES, m.indexCount, GLES30.GL_UNSIGNED_INT, 0)
    }
}

/** Interleaved positions and normals with 32 bit indices. */
class Mesh(val vertices: FloatBuffer, val vertexCount: Int, val indices: IntBuffer, val indexCount: Int) {
    companion object {
        fun of(v: FloatArray, vCount: Int, idx: IntArray, iCount: Int): Mesh {
            val vb = ByteBuffer.allocateDirect(vCount * 6 * 4).order(ByteOrder.nativeOrder()).asFloatBuffer()
            vb.put(v, 0, vCount * 6).position(0)
            val ib = ByteBuffer.allocateDirect(maxOf(iCount, 1) * 4).order(ByteOrder.nativeOrder()).asIntBuffer()
            ib.put(idx, 0, iCount).position(0)
            return Mesh(vb, vCount, ib, iCount)
        }

        /** Unit sphere (radius 1). */
        fun sphere(seg: Int, rings: Int): Mesh {
            val v = FloatArray((seg + 1) * (rings + 1) * 6)
            var k = 0
            for (r in 0..rings) {
                val phi = Math.PI * r / rings
                for (s in 0..seg) {
                    val th = 2 * Math.PI * s / seg
                    val x = (kotlin.math.sin(phi) * kotlin.math.cos(th)).toFloat()
                    val y = (-kotlin.math.cos(phi)).toFloat()
                    val z = (kotlin.math.sin(phi) * kotlin.math.sin(th)).toFloat()
                    v[k++] = x; v[k++] = y; v[k++] = z
                    v[k++] = x; v[k++] = y; v[k++] = z
                }
            }
            val idx = IntArray(seg * rings * 6)
            var i = 0
            for (r in 0 until rings) for (s in 0 until seg) {
                val a = r * (seg + 1) + s
                val b = a + seg + 1
                idx[i++] = a; idx[i++] = b; idx[i++] = a + 1
                idx[i++] = a + 1; idx[i++] = b; idx[i++] = b + 1
            }
            return of(v, (seg + 1) * (rings + 1), idx, i)
        }
    }
}
