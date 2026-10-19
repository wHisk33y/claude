package de.knuffi.app.render.house3d

import android.graphics.Bitmap
import android.opengl.EGL14
import android.opengl.EGLConfig
import android.opengl.EGLContext
import android.opengl.EGLDisplay
import android.opengl.EGLSurface
import android.os.Handler
import android.os.HandlerThread
import android.util.Log
import java.nio.ByteBuffer

/**
 * A picture that the 3D renderer fills in the background. Drawing code calls [take] every frame
 * and gets the newest finished image (or the previous one while a new one is being made).
 */
class Layer3 {
    private val lock = Any()
    private var shown: Bitmap? = null
    private var retired: Bitmap? = null
    private var ready: Bitmap? = null
    private var readyToken: Any? = null
    private val free = ArrayList<Bitmap>()

    /** What the shown picture was made for. */
    @Volatile
    var shownToken: Any? = null
        private set

    /** What was asked for last (to avoid asking twice). */
    @Volatile
    var requested: Any? = null

    /** The newest picture (call on the drawing thread). */
    fun take(): Bitmap? = synchronized(lock) {
        val r = ready
        if (r != null) {
            retired?.let { free += it }
            retired = shown
            shown = r
            shownToken = readyToken
            ready = null
        }
        shown
    }

    internal fun obtain(w: Int, h: Int): Bitmap = synchronized(lock) {
        val i = free.indexOfFirst { it.width == w && it.height == h && !it.isRecycled }
        if (i >= 0) return free.removeAt(i)
        while (free.size > 1) free.removeAt(0).recycle()
        Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
    }

    internal fun deliver(b: Bitmap, token: Any?) = synchronized(lock) {
        ready?.let { free += it }
        ready = b
        readyToken = token
    }

    /** Forgets everything (the images are recycled later by the garbage collector). */
    fun clear() = synchronized(lock) {
        shown = null
        retired = null
        ready = null
        free.clear()
        shownToken = null
        requested = null
    }
}

/**
 * The 3D renderer of the house on its own thread with its own OpenGL context. Requests for the
 * same [Layer3] replace each other, so only the newest one is drawn.
 */
object Room3d {
    private const val TAG = "Room3d"

    /** Set from the settings; switching 3D on again gives it a new try after a failure. */
    @Volatile
    var wanted = true
        set(value) {
            if (value && !field) {
                failed = false
                failures = 0
            }
            field = value
        }

    @Volatile
    private var failed = false

    /** Failures in a row; a single hiccup does not switch 3D off. */
    @Volatile
    private var failures = 0

    val available: Boolean get() = wanted && !failed

    /** What went wrong last (shown in the settings when 3D had to be switched off). */
    @Volatile
    var lastProblem: String? = null
        private set

    private class Job(val scene: Scene3, val cam: Camera3, val opt: RenderOptions, val token: Any?, val prepare: (() -> Unit)?)

    private val thread: HandlerThread by lazy { HandlerThread("knuffi-3d").apply { start() } }
    private val handler: Handler by lazy { Handler(thread.looper) }
    private val pending = LinkedHashMap<Layer3, Job>()

    private var display: EGLDisplay = EGL14.EGL_NO_DISPLAY
    private var context: EGLContext = EGL14.EGL_NO_CONTEXT
    private var surface: EGLSurface = EGL14.EGL_NO_SURFACE
    private var renderer: Renderer3? = null

    /** True on software graphics: pictures are made smaller (see [scale]). */
    @Volatile
    var light = false
        private set

    /** How much smaller than the screen the pictures are made. */
    val scale: Float get() = if (light) 0.55f else 1f

    /** Paints the textures (wallpaper, rugs …). Set once by the app. */
    @Volatile
    var textures: TextureSource = TextureSource { null }

    /** Prefixes of textures to forget before the next picture (e.g. after the season changed). */
    private val drop = ArrayList<String>()

    fun dropTextures(prefix: String) {
        synchronized(drop) { drop += prefix }
    }

    /** Asks for a new picture of [scene] in [layer]; [token] describes it (see [Layer3.shownToken]). */
    fun request(layer: Layer3, scene: Scene3, cam: Camera3, opt: RenderOptions, token: Any?, prepare: (() -> Unit)? = null) {
        if (!available) return
        layer.requested = token
        val post: Boolean
        synchronized(pending) {
            post = !pending.containsKey(layer)
            pending[layer] = Job(scene, cam, opt, token, prepare)
        }
        if (post) handler.post { run(layer) }
    }

    private fun run(layer: Layer3) {
        val job = synchronized(pending) { pending.remove(layer) } ?: return
        if (!available) return
        try {
            job.prepare?.invoke()
            if (!ensure()) return
            if (!EGL14.eglMakeCurrent(display, surface, surface, context)) throw IllegalStateException("makeCurrent ${EGL14.eglGetError()}")
            val r = renderer ?: run {
                // software graphics (emulator) and weak phones get a lighter picture
                val name = (android.opengl.GLES30.glGetString(android.opengl.GLES30.GL_RENDERER) ?: "").lowercase()
                light = "swiftshader" in name || "emulator" in name || "llvmpipe" in name || "softpipe" in name
                Log.i(TAG, "3D renderer: $name${if (light) " (light)" else ""}")
                Renderer3(AndroidGl(), TextureSource { key -> textures.texture(key) }, maxSamples = if (light) 0 else 4, shadowSize = if (light) 1024 else 2048)
            }.also { renderer = it }
            val drops = synchronized(drop) { drop.toList().also { drop.clear() } }
            for (d in drops) r.dropTextures(d)
            val buf: ByteBuffer = r.render(job.scene, job.cam, job.opt)
            val bmp = layer.obtain(job.cam.width, job.cam.height)
            buf.position(0)
            bmp.copyPixelsFromBuffer(buf)
            layer.deliver(bmp, job.token)
            failures = 0
        } catch (t: Throwable) {
            failures++
            lastProblem = (t::class.java.simpleName + ": " + (t.message ?: "")).take(160)
            Log.w(TAG, "3D picture failed ($failures)", t)
            // start over with a fresh context; only give up if it keeps failing
            reset()
            if (failures >= 3) {
                Log.w(TAG, "3D house disabled")
                failed = true
            } else {
                synchronized(pending) {
                    if (!pending.containsKey(layer)) {
                        pending[layer] = job
                        handler.postDelayed({ run(layer) }, 300L)
                    }
                }
            }
        }
    }

    /** Throws away the context and everything in it. */
    private fun reset() {
        renderer = null
        runCatching {
            if (display != EGL14.EGL_NO_DISPLAY) {
                EGL14.eglMakeCurrent(display, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT)
                if (surface != EGL14.EGL_NO_SURFACE) EGL14.eglDestroySurface(display, surface)
                if (context != EGL14.EGL_NO_CONTEXT) EGL14.eglDestroyContext(display, context)
            }
        }
        surface = EGL14.EGL_NO_SURFACE
        context = EGL14.EGL_NO_CONTEXT
    }

    private fun ensure(): Boolean {
        if (context != EGL14.EGL_NO_CONTEXT) return true
        display = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
        val ver = IntArray(2)
        if (!EGL14.eglInitialize(display, ver, 0, ver, 1)) throw IllegalStateException("egl init")
        val attribs = intArrayOf(
            EGL14.EGL_RED_SIZE, 8, EGL14.EGL_GREEN_SIZE, 8, EGL14.EGL_BLUE_SIZE, 8, EGL14.EGL_ALPHA_SIZE, 8,
            EGL14.EGL_RENDERABLE_TYPE, 0x40,
            EGL14.EGL_SURFACE_TYPE, EGL14.EGL_PBUFFER_BIT,
            EGL14.EGL_NONE,
        )
        val configs = arrayOfNulls<EGLConfig>(1)
        val n = IntArray(1)
        if (!EGL14.eglChooseConfig(display, attribs, 0, configs, 0, 1, n, 0) || n[0] == 0) throw IllegalStateException("egl config")
        val cfg = configs[0]
        context = EGL14.eglCreateContext(display, cfg, EGL14.EGL_NO_CONTEXT, intArrayOf(EGL14.EGL_CONTEXT_CLIENT_VERSION, 3, EGL14.EGL_NONE), 0)
        if (context == EGL14.EGL_NO_CONTEXT) throw IllegalStateException("egl context")
        surface = EGL14.eglCreatePbufferSurface(display, cfg, intArrayOf(EGL14.EGL_WIDTH, 1, EGL14.EGL_HEIGHT, 1, EGL14.EGL_NONE), 0)
        if (surface == EGL14.EGL_NO_SURFACE) throw IllegalStateException("egl surface")
        return true
    }
}
