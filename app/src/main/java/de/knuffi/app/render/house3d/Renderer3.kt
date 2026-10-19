package de.knuffi.app.render.house3d

import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.IdentityHashMap
import kotlin.math.max
import kotlin.math.sqrt

/** RGBA pixels (premultiplied, top row first) of a texture. */
class TexImage(val width: Int, val height: Int, val pixels: ByteBuffer, val repeat: Boolean = false, val mipmap: Boolean = true)

/** Makes the pictures of textures (wallpaper, rugs, paintings …) by key. */
fun interface TextureSource {
    fun texture(key: String): TexImage?
}

/** How a frame is made. */
class RenderOptions(
    /** Only items for which this is true are drawn. */
    val filter: ((Item3) -> Boolean)? = null,
    val shadows: Boolean = true,
    /** Background (ARGB); transparent by default. */
    val clear: Int = 0,
    /** Leave out walls the camera looks at from outside. */
    val cutaway: Boolean = true,
)

/**
 * Draws a [Scene3] with soft light and shadows into an offscreen image and reads it back.
 * Must be used on the thread that owns the GL context.
 */
class Renderer3(private val gl: Gl, private val source: TextureSource) {
    private var ready = false
    private var program = 0
    private var shadowProgram = 0
    private var vao = 0
    private val loc = HashMap<String, Int>()
    private val sloc = HashMap<String, Int>()
    private var white = 0
    private var frame = 0L

    private class GpuMesh(val vbo: Int, val ibo: Int, val count: Int, var used: Long)
    private class GpuTex(val id: Int, var used: Long)

    private val meshes = IdentityHashMap<MeshData, GpuMesh>()
    private val textures = HashMap<String, GpuTex>()
    private val missing = HashSet<String>()

    private var msFbo = 0
    private var msColor = 0
    private var msDepth = 0
    private var outFbo = 0
    private var outColor = 0
    private var tw = 0
    private var th = 0
    private var samples = 0
    private var shadowFbo = 0
    private var shadowTex = 0
    private val shadowSize = 2048
    private var readBuf: ByteBuffer? = null

    private val lightVP = FloatArray(16)
    private val nrm = FloatArray(9)

    /** Throws if the shaders do not work here. */
    fun init() {
        if (ready) return
        program = buildProgram(Shaders3.VERTEX, Shaders3.FRAGMENT)
        shadowProgram = buildProgram(Shaders3.SHADOW_VERTEX, Shaders3.SHADOW_FRAGMENT)
        vao = gl.genVertexArray()
        gl.bindVertexArray(vao)
        white = gl.genTexture()
        gl.bindTexture(white)
        val px = ByteBuffer.allocateDirect(4).order(ByteOrder.nativeOrder())
        px.put(byteArrayOf(-1, -1, -1, -1)).position(0)
        gl.texImageRgba(1, 1, px)
        gl.texParameter(Gl.TEXTURE_MIN_FILTER, Gl.NEAREST)
        gl.texParameter(Gl.TEXTURE_MAG_FILTER, Gl.NEAREST)
        samples = gl.maxSamples().coerceIn(0, 4)
        shadowFbo = gl.genFramebuffer()
        shadowTex = gl.genTexture()
        gl.bindTexture(shadowTex)
        gl.texImageDepth(shadowSize, shadowSize)
        gl.texParameter(Gl.TEXTURE_MIN_FILTER, Gl.LINEAR)
        gl.texParameter(Gl.TEXTURE_MAG_FILTER, Gl.LINEAR)
        gl.texParameter(Gl.TEXTURE_WRAP_S, Gl.CLAMP_TO_EDGE)
        gl.texParameter(Gl.TEXTURE_WRAP_T, Gl.CLAMP_TO_EDGE)
        gl.texParameter(Gl.TEXTURE_COMPARE_MODE, Gl.COMPARE_REF_TO_TEXTURE)
        gl.texParameter(Gl.TEXTURE_COMPARE_FUNC, Gl.LEQUAL)
        gl.bindFramebuffer(Gl.FRAMEBUFFER, shadowFbo)
        gl.attachDepthTexture(shadowTex)
        gl.noColorBuffers()
        check(gl.framebufferComplete()) { "shadow framebuffer" }
        gl.bindFramebuffer(Gl.FRAMEBUFFER, 0)
        ready = true
    }

    private fun buildProgram(vs: String, fs: String): Int {
        fun shader(type: Int, src: String): Int {
            val s = gl.createShader(type)
            gl.shaderSource(s, src)
            gl.compileShader(s)
            check(gl.shaderOk(s)) { "shader: " + gl.shaderLog(s) }
            return s
        }
        val p = gl.createProgram()
        gl.attachShader(p, shader(Gl.VERTEX_SHADER, vs))
        gl.attachShader(p, shader(Gl.FRAGMENT_SHADER, fs))
        gl.bindAttribLocation(p, 0, "aPos")
        gl.bindAttribLocation(p, 1, "aNrm")
        gl.bindAttribLocation(p, 2, "aUv")
        gl.linkProgram(p)
        check(gl.programOk(p)) { "link: " + gl.programLog(p) }
        return p
    }

    private fun u(name: String): Int = loc.getOrPut(name) { gl.uniformLocation(program, name) }
    private fun su(name: String): Int = sloc.getOrPut(name) { gl.uniformLocation(shadowProgram, name) }

    private fun ensureTarget(w: Int, h: Int) {
        if (w == tw && h == th && msFbo != 0) return
        if (msFbo != 0) {
            gl.deleteFramebuffer(msFbo); gl.deleteFramebuffer(outFbo)
            gl.deleteRenderbuffer(msColor); gl.deleteRenderbuffer(msDepth); gl.deleteRenderbuffer(outColor)
        }
        tw = w
        th = h
        msFbo = gl.genFramebuffer()
        msColor = gl.genRenderbuffer()
        msDepth = gl.genRenderbuffer()
        gl.renderbufferStorage(msColor, samples, Gl.RGBA8, w, h)
        gl.renderbufferStorage(msDepth, samples, Gl.DEPTH_COMPONENT24, w, h)
        gl.bindFramebuffer(Gl.FRAMEBUFFER, msFbo)
        gl.attachRenderbuffer(Gl.COLOR_ATTACHMENT0, msColor)
        gl.attachRenderbuffer(Gl.DEPTH_ATTACHMENT, msDepth)
        check(gl.framebufferComplete()) { "framebuffer" }
        outFbo = gl.genFramebuffer()
        outColor = gl.genRenderbuffer()
        gl.renderbufferStorage(outColor, 0, Gl.RGBA8, w, h)
        gl.bindFramebuffer(Gl.FRAMEBUFFER, outFbo)
        gl.attachRenderbuffer(Gl.COLOR_ATTACHMENT0, outColor)
        check(gl.framebufferComplete()) { "resolve framebuffer" }
    }

    private fun mesh(m: MeshData): GpuMesh {
        val have = meshes[m]
        if (have != null) {
            have.used = frame
            return have
        }
        val vb = ByteBuffer.allocateDirect(m.vertices.size * 4).order(ByteOrder.nativeOrder()).asFloatBuffer()
        vb.put(m.vertices).position(0)
        val ib = ByteBuffer.allocateDirect(max(1, m.indices.size) * 4).order(ByteOrder.nativeOrder()).asIntBuffer()
        ib.put(m.indices).position(0)
        val vbo = gl.genBuffer()
        gl.bindBuffer(Gl.ARRAY_BUFFER, vbo)
        gl.bufferData(Gl.ARRAY_BUFFER, m.vertices.size * 4, vb, Gl.STATIC_DRAW)
        val ibo = gl.genBuffer()
        gl.bindBuffer(Gl.ELEMENT_ARRAY_BUFFER, ibo)
        gl.bufferData(Gl.ELEMENT_ARRAY_BUFFER, m.indices.size * 4, ib, Gl.STATIC_DRAW)
        return GpuMesh(vbo, ibo, m.indices.size, frame).also { meshes[m] = it }
    }

    private fun texture(key: String): Int {
        textures[key]?.let {
            it.used = frame
            return it.id
        }
        if (key in missing) return white
        val img = runCatching { source.texture(key) }.getOrNull()
        if (img == null) {
            missing += key
            return white
        }
        val id = gl.genTexture()
        gl.bindTexture(id)
        img.pixels.position(0)
        gl.texImageRgba(img.width, img.height, img.pixels)
        val wrap = if (img.repeat) Gl.REPEAT else Gl.CLAMP_TO_EDGE
        gl.texParameter(Gl.TEXTURE_WRAP_S, wrap)
        gl.texParameter(Gl.TEXTURE_WRAP_T, wrap)
        gl.texParameter(Gl.TEXTURE_MAG_FILTER, Gl.LINEAR)
        if (img.mipmap) {
            gl.generateMipmap()
            gl.texParameter(Gl.TEXTURE_MIN_FILTER, Gl.LINEAR_MIPMAP_LINEAR)
        } else {
            gl.texParameter(Gl.TEXTURE_MIN_FILTER, Gl.LINEAR)
        }
        textures[key] = GpuTex(id, frame)
        return id
    }

    /** Forgets cached textures whose key starts with [prefix] (e.g. after the wallpaper changed). */
    fun dropTextures(prefix: String) {
        val gone = textures.keys.filter { it.startsWith(prefix) }
        for (k in gone) textures.remove(k)?.let { gl.deleteTexture(it.id) }
        missing.removeAll { it.startsWith(prefix) }
    }

    private fun bindMesh(g: GpuMesh) {
        gl.bindVertexArray(vao)
        gl.bindBuffer(Gl.ARRAY_BUFFER, g.vbo)
        gl.bindBuffer(Gl.ELEMENT_ARRAY_BUFFER, g.ibo)
        gl.enableAttrib(0)
        gl.attribPointer(0, 3, 32, 0)
        gl.enableAttrib(1)
        gl.attribPointer(1, 3, 32, 12)
        gl.enableAttrib(2)
        gl.attribPointer(2, 2, 32, 24)
    }

    private fun visible(i: Item3, cam: Camera3, opt: RenderOptions): Boolean {
        if (i.hidden) return false
        if (opt.filter != null && !opt.filter.invoke(i)) return false
        i.linkedTo?.let { if (!visible(it, cam, opt)) return false }
        if (opt.cutaway) {
            val o = i.outward
            val p = i.wallPoint
            if (o != null && p != null) {
                val d = (cam.eye[0] - p[0]) * o[0] + (cam.eye[1] - p[1]) * o[1] + (cam.eye[2] - p[2]) * o[2]
                if (d > 0f) return false
            }
        }
        return true
    }

    /** Renders [scene] seen by [cam]; returns RGBA pixels (top row first, premultiplied). */
    fun render(scene: Scene3, cam: Camera3, opt: RenderOptions = RenderOptions()): ByteBuffer {
        init()
        frame++
        val w = cam.width
        val h = cam.height
        val items = scene.items.filter { visible(it, cam, opt) }
        val light = scene.light

        // ---------------------------------------------------------------- shadow map
        val useShadow = opt.shadows && items.any { it.model.parts.any { p -> p.mat.shadow } }
        if (useShadow) {
            val b = Box3()
            for (i in items) b.add(i.bounds)
            val cx = (b.x0 + b.x1) / 2f; val cy = (b.y0 + b.y1) / 2f; val cz = (b.z0 + b.z1) / 2f
            val radius = sqrt((b.x1 - b.x0).let { it * it } + (b.y1 - b.y0).let { it * it } + (b.z1 - b.z0).let { it * it }) / 2f + 0.1f
            val L = light.dir
            val lv = M4.lookAt(cx + L[0] * radius * 2f, cy + L[1] * radius * 2f, cz + L[2] * radius * 2f, cx, cy, cz, 0f, 0f, -1f)
            // fit the light's box around the scene
            val lb = b.transformed(lv)
            val lp = M4.ortho(lb.x0 - 0.05f, lb.x1 + 0.05f, lb.y0 - 0.05f, lb.y1 + 0.05f, -lb.z1 - 0.1f, -lb.z0 + 0.1f)
            M4.mul(lp, lv, lightVP)
            // the shadow map must not be bound for reading while it is drawn into
            gl.activeTexture(Gl.TEXTURE0 + 1)
            gl.bindTexture(0)
            gl.activeTexture(Gl.TEXTURE0)
            gl.bindFramebuffer(Gl.FRAMEBUFFER, shadowFbo)
            gl.viewport(shadowSize, shadowSize)
            gl.clearDepth()
            gl.enable(Gl.DEPTH_TEST)
            gl.disable(Gl.BLEND)
            gl.enable(Gl.POLYGON_OFFSET_FILL)
            gl.polygonOffset(1.5f, 3f)
            gl.useProgram(shadowProgram)
            gl.uniformMatrix4(su("uLightVP"), lightVP)
            gl.uniform1i(su("uTex"), 0)
            for (i in items) {
                gl.uniformMatrix4(su("uModel"), i.matrix)
                for (p in i.model.parts) {
                    if (!p.mat.shadow || p.mat.alpha < 0.99f) continue
                    val cut = p.mat.texMode == 2 && p.mat.tex != null
                    gl.uniform1i(su("uCut"), if (cut) 1 else 0)
                    gl.activeTexture(Gl.TEXTURE0)
                    gl.bindTexture(if (cut) texture(p.mat.tex!!) else white)
                    val g = mesh(p.mesh)
                    bindMesh(g)
                    gl.drawElements(g.count)
                }
            }
            gl.disable(Gl.POLYGON_OFFSET_FILL)
        }

        // ---------------------------------------------------------------- colour
        ensureTarget(w, h)
        gl.bindFramebuffer(Gl.FRAMEBUFFER, msFbo)
        gl.viewport(w, h)
        val bg = opt.clear
        val ba = ((bg ushr 24) and 0xFF) / 255f
        gl.clear(((bg shr 16) and 0xFF) / 255f * ba, ((bg shr 8) and 0xFF) / 255f * ba, (bg and 0xFF) / 255f * ba, ba)
        gl.enable(Gl.DEPTH_TEST)
        gl.depthMask(true)
        gl.disable(Gl.BLEND)
        gl.useProgram(program)
        gl.uniformMatrix4(u("uVP"), cam.viewProj)
        gl.uniformMatrix4(u("uLightVP"), lightVP)
        gl.uniform3f(u("uEye"), cam.eye[0], cam.eye[1], cam.eye[2])
        gl.uniform3f(u("uLightDir"), light.dir[0], light.dir[1], light.dir[2])
        gl.uniform3f(u("uLightCol"), ch(light.color, 16) * light.strength, ch(light.color, 8) * light.strength, ch(light.color, 0) * light.strength)
        gl.uniform3f(u("uSky"), ch(light.sky, 16) * light.ambient, ch(light.sky, 8) * light.ambient, ch(light.sky, 0) * light.ambient)
        gl.uniform3f(u("uGround"), ch(light.ground, 16) * light.ambient, ch(light.ground, 8) * light.ambient, ch(light.ground, 0) * light.ambient)
        gl.uniform1i(u("uUseShadow"), if (useShadow) 1 else 0)
        gl.uniform2f(u("uShadowTexel"), 1f / shadowSize, 1f / shadowSize)
        gl.uniform1i(u("uTex"), 0)
        gl.uniform1i(u("uShadow"), 1)
        gl.activeTexture(Gl.TEXTURE0 + 1)
        gl.bindTexture(shadowTex)

        val transparent = ArrayList<Pair<Item3, Part>>()
        for (i in items) {
            for (p in i.model.parts) {
                if (p.mat.alpha < 0.99f) {
                    transparent += i to p
                    continue
                }
                drawPart(i, p)
            }
        }
        if (transparent.isNotEmpty()) {
            // far ones first
            transparent.sortByDescending { (i, _) ->
                val b = i.bounds
                val dx = (b.x0 + b.x1) / 2f - cam.eye[0]
                val dy = (b.y0 + b.y1) / 2f - cam.eye[1]
                val dz = (b.z0 + b.z1) / 2f - cam.eye[2]
                dx * dx + dy * dy + dz * dz
            }
            gl.enable(Gl.BLEND)
            gl.blendPremultiplied()
            gl.depthMask(false)
            for ((i, p) in transparent) drawPart(i, p)
            gl.depthMask(true)
            gl.disable(Gl.BLEND)
        }

        // ---------------------------------------------------------------- read back
        gl.bindFramebuffer(Gl.READ_FRAMEBUFFER, msFbo)
        gl.bindFramebuffer(Gl.DRAW_FRAMEBUFFER, outFbo)
        gl.blit(w, h)
        gl.bindFramebuffer(Gl.READ_FRAMEBUFFER, outFbo)
        val need = w * h * 4
        val buf = readBuf?.takeIf { it.capacity() >= need } ?: ByteBuffer.allocateDirect(need).order(ByteOrder.nativeOrder()).also { readBuf = it }
        buf.clear()
        gl.readRgba(w, h, buf)
        gl.bindFramebuffer(Gl.FRAMEBUFFER, 0)
        val err = gl.error()
        check(err == Gl.NO_ERROR) { "gl error $err" }
        evict()
        buf.position(0)
        buf.limit(need)
        return buf
    }

    private fun drawPart(i: Item3, p: Part) {
        val m = p.mat
        gl.uniformMatrix4(u("uModel"), i.matrix)
        gl.uniformMatrix3(u("uNrm"), M4.normal3(i.matrix, nrm))
        gl.uniform4f(u("uColor"), m.r, m.g, m.b, m.alpha)
        gl.uniform1f(u("uGloss"), m.gloss)
        gl.uniform1f(u("uEmissive"), m.emissive)
        val tint = i.tint
        gl.uniform4f(u("uTint"), ch(tint, 16), ch(tint, 8), ch(tint, 0), ((tint ushr 24) and 0xFF) / 255f)
        val tex = m.tex
        gl.uniform1i(u("uTexMode"), if (tex != null) m.texMode.coerceAtLeast(1) else 0)
        gl.activeTexture(Gl.TEXTURE0)
        gl.bindTexture(if (tex != null) texture(tex) else white)
        val g = mesh(p.mesh)
        bindMesh(g)
        gl.drawElements(g.count)
    }

    private fun evict() {
        if (meshes.size > 900) {
            val old = meshes.entries.filter { frame - it.value.used > 30 }
            for (e in old) {
                gl.deleteBuffer(e.value.vbo)
                gl.deleteBuffer(e.value.ibo)
                meshes.remove(e.key)
            }
        }
        if (textures.size > 48) {
            val old = textures.entries.filter { frame - it.value.used > 30 }.map { it.key }
            for (k in old) textures.remove(k)?.let { gl.deleteTexture(it.id) }
        }
    }

    private fun ch(c: Int, shift: Int): Float = ((c shr shift) and 0xFF) / 255f
}
