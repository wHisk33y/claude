package de.knuffi.app.render.house3d

import java.nio.Buffer
import java.nio.ByteBuffer

/**
 * The few OpenGL ES 3 calls the house renderer needs. On the phone they go to GLES30; keeping them
 * behind this interface lets the same renderer be checked on a desktop computer.
 */
interface Gl {
    fun createShader(type: Int): Int
    fun shaderSource(shader: Int, src: String)
    fun compileShader(shader: Int)
    fun shaderOk(shader: Int): Boolean
    fun shaderLog(shader: Int): String
    fun createProgram(): Int
    fun attachShader(program: Int, shader: Int)
    fun bindAttribLocation(program: Int, index: Int, name: String)
    fun linkProgram(program: Int)
    fun programOk(program: Int): Boolean
    fun programLog(program: Int): String
    fun useProgram(program: Int)
    fun uniformLocation(program: Int, name: String): Int
    fun uniform1i(loc: Int, v: Int)
    fun uniform1f(loc: Int, v: Float)
    fun uniform2f(loc: Int, a: Float, b: Float)
    fun uniform3f(loc: Int, a: Float, b: Float, c: Float)
    fun uniform4f(loc: Int, a: Float, b: Float, c: Float, d: Float)
    fun uniformMatrix4(loc: Int, m: FloatArray)
    fun uniformMatrix3(loc: Int, m: FloatArray)

    fun genBuffer(): Int
    fun deleteBuffer(id: Int)
    fun bindBuffer(target: Int, id: Int)
    fun bufferData(target: Int, bytes: Int, data: Buffer, usage: Int)
    fun genVertexArray(): Int
    fun bindVertexArray(id: Int)
    fun enableAttrib(index: Int)
    fun attribPointer(index: Int, size: Int, stride: Int, offset: Int)
    fun drawElements(count: Int)

    fun genTexture(): Int
    fun deleteTexture(id: Int)
    fun activeTexture(unit: Int)
    fun bindTexture(id: Int)
    fun texParameter(name: Int, value: Int)
    fun texImageRgba(w: Int, h: Int, pixels: ByteBuffer)
    fun texImageDepth(w: Int, h: Int)
    fun generateMipmap()

    fun genFramebuffer(): Int
    fun deleteFramebuffer(id: Int)
    fun bindFramebuffer(target: Int, id: Int)
    fun genRenderbuffer(): Int
    fun deleteRenderbuffer(id: Int)
    fun renderbufferStorage(id: Int, samples: Int, format: Int, w: Int, h: Int)
    fun attachRenderbuffer(attachment: Int, id: Int)
    fun attachDepthTexture(id: Int)
    fun framebufferComplete(): Boolean
    fun noColorBuffers()
    fun blit(w: Int, h: Int)
    fun readRgba(w: Int, h: Int, out: ByteBuffer)

    fun viewport(w: Int, h: Int)
    fun clear(r: Float, g: Float, b: Float, a: Float)
    fun clearDepth()
    fun enable(cap: Int)
    fun disable(cap: Int)
    fun depthMask(on: Boolean)
    fun blendPremultiplied()
    fun polygonOffset(factor: Float, units: Float)
    fun maxSamples(): Int
    fun error(): Int

    companion object {
        const val VERTEX_SHADER = 0x8B31
        const val FRAGMENT_SHADER = 0x8B30
        const val ARRAY_BUFFER = 0x8892
        const val ELEMENT_ARRAY_BUFFER = 0x8893
        const val STATIC_DRAW = 0x88E4
        const val FRAMEBUFFER = 0x8D40
        const val READ_FRAMEBUFFER = 0x8CA8
        const val DRAW_FRAMEBUFFER = 0x8CA9
        const val COLOR_ATTACHMENT0 = 0x8CE0
        const val DEPTH_ATTACHMENT = 0x8D00
        const val RGBA8 = 0x8058
        const val DEPTH_COMPONENT24 = 0x81A6
        const val DEPTH_TEST = 0x0B71
        const val BLEND = 0x0BE2
        const val CULL_FACE = 0x0B44
        const val POLYGON_OFFSET_FILL = 0x8037
        const val SAMPLE_ALPHA_TO_COVERAGE = 0x809E
        const val TEXTURE_MIN_FILTER = 0x2801
        const val TEXTURE_MAG_FILTER = 0x2800
        const val TEXTURE_WRAP_S = 0x2802
        const val TEXTURE_WRAP_T = 0x2803
        const val TEXTURE_COMPARE_MODE = 0x884C
        const val TEXTURE_COMPARE_FUNC = 0x884D
        const val COMPARE_REF_TO_TEXTURE = 0x884E
        const val LEQUAL = 0x0203
        const val LINEAR = 0x2601
        const val NEAREST = 0x2600
        const val LINEAR_MIPMAP_LINEAR = 0x2703
        const val CLAMP_TO_EDGE = 0x812F
        const val REPEAT = 0x2901
        const val TEXTURE0 = 0x84C0
        const val NO_ERROR = 0
    }
}
