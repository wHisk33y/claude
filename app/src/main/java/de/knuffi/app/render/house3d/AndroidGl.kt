package de.knuffi.app.render.house3d

import android.opengl.GLES30
import java.nio.Buffer
import java.nio.ByteBuffer

/** [Gl] on the phone: plain OpenGL ES 3.0. */
class AndroidGl : Gl {
    private val one = IntArray(1)

    override fun createShader(type: Int) = GLES30.glCreateShader(type)
    override fun shaderSource(shader: Int, src: String) = GLES30.glShaderSource(shader, src)
    override fun compileShader(shader: Int) = GLES30.glCompileShader(shader)
    override fun shaderOk(shader: Int): Boolean {
        GLES30.glGetShaderiv(shader, GLES30.GL_COMPILE_STATUS, one, 0)
        return one[0] != 0
    }
    override fun shaderLog(shader: Int): String = GLES30.glGetShaderInfoLog(shader)
    override fun createProgram() = GLES30.glCreateProgram()
    override fun attachShader(program: Int, shader: Int) = GLES30.glAttachShader(program, shader)
    override fun bindAttribLocation(program: Int, index: Int, name: String) = GLES30.glBindAttribLocation(program, index, name)
    override fun linkProgram(program: Int) = GLES30.glLinkProgram(program)
    override fun programOk(program: Int): Boolean {
        GLES30.glGetProgramiv(program, GLES30.GL_LINK_STATUS, one, 0)
        return one[0] != 0
    }
    override fun programLog(program: Int): String = GLES30.glGetProgramInfoLog(program)
    override fun useProgram(program: Int) = GLES30.glUseProgram(program)
    override fun uniformLocation(program: Int, name: String) = GLES30.glGetUniformLocation(program, name)
    override fun uniform1i(loc: Int, v: Int) = GLES30.glUniform1i(loc, v)
    override fun uniform1f(loc: Int, v: Float) = GLES30.glUniform1f(loc, v)
    override fun uniform2f(loc: Int, a: Float, b: Float) = GLES30.glUniform2f(loc, a, b)
    override fun uniform3f(loc: Int, a: Float, b: Float, c: Float) = GLES30.glUniform3f(loc, a, b, c)
    override fun uniform4f(loc: Int, a: Float, b: Float, c: Float, d: Float) = GLES30.glUniform4f(loc, a, b, c, d)
    override fun uniformMatrix4(loc: Int, m: FloatArray) = GLES30.glUniformMatrix4fv(loc, 1, false, m, 0)
    override fun uniformMatrix3(loc: Int, m: FloatArray) = GLES30.glUniformMatrix3fv(loc, 1, false, m, 0)

    override fun genBuffer(): Int {
        GLES30.glGenBuffers(1, one, 0)
        return one[0]
    }
    override fun deleteBuffer(id: Int) {
        one[0] = id
        GLES30.glDeleteBuffers(1, one, 0)
    }
    override fun bindBuffer(target: Int, id: Int) = GLES30.glBindBuffer(target, id)
    override fun bufferData(target: Int, bytes: Int, data: Buffer, usage: Int) = GLES30.glBufferData(target, bytes, data, usage)
    override fun genVertexArray(): Int {
        GLES30.glGenVertexArrays(1, one, 0)
        return one[0]
    }
    override fun bindVertexArray(id: Int) = GLES30.glBindVertexArray(id)
    override fun enableAttrib(index: Int) = GLES30.glEnableVertexAttribArray(index)
    override fun attribPointer(index: Int, size: Int, stride: Int, offset: Int) = GLES30.glVertexAttribPointer(index, size, GLES30.GL_FLOAT, false, stride, offset)
    override fun drawElements(count: Int) = GLES30.glDrawElements(GLES30.GL_TRIANGLES, count, GLES30.GL_UNSIGNED_INT, 0)

    override fun genTexture(): Int {
        GLES30.glGenTextures(1, one, 0)
        return one[0]
    }
    override fun deleteTexture(id: Int) {
        one[0] = id
        GLES30.glDeleteTextures(1, one, 0)
    }
    override fun activeTexture(unit: Int) = GLES30.glActiveTexture(unit)
    override fun bindTexture(id: Int) = GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, id)
    override fun texParameter(name: Int, value: Int) = GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, name, value)
    override fun texImageRgba(w: Int, h: Int, pixels: ByteBuffer) {
        GLES30.glPixelStorei(GLES30.GL_UNPACK_ALIGNMENT, 1)
        GLES30.glTexImage2D(GLES30.GL_TEXTURE_2D, 0, GLES30.GL_RGBA8, w, h, 0, GLES30.GL_RGBA, GLES30.GL_UNSIGNED_BYTE, pixels)
    }
    override fun texImageDepth(w: Int, h: Int) =
        GLES30.glTexImage2D(GLES30.GL_TEXTURE_2D, 0, GLES30.GL_DEPTH_COMPONENT24, w, h, 0, GLES30.GL_DEPTH_COMPONENT, GLES30.GL_UNSIGNED_INT, null)
    override fun generateMipmap() = GLES30.glGenerateMipmap(GLES30.GL_TEXTURE_2D)

    override fun genFramebuffer(): Int {
        GLES30.glGenFramebuffers(1, one, 0)
        return one[0]
    }
    override fun deleteFramebuffer(id: Int) {
        one[0] = id
        GLES30.glDeleteFramebuffers(1, one, 0)
    }
    override fun bindFramebuffer(target: Int, id: Int) = GLES30.glBindFramebuffer(target, id)
    override fun genRenderbuffer(): Int {
        GLES30.glGenRenderbuffers(1, one, 0)
        return one[0]
    }
    override fun deleteRenderbuffer(id: Int) {
        one[0] = id
        GLES30.glDeleteRenderbuffers(1, one, 0)
    }
    override fun renderbufferStorage(id: Int, samples: Int, format: Int, w: Int, h: Int) {
        GLES30.glBindRenderbuffer(GLES30.GL_RENDERBUFFER, id)
        if (samples > 0) GLES30.glRenderbufferStorageMultisample(GLES30.GL_RENDERBUFFER, samples, format, w, h) else GLES30.glRenderbufferStorage(GLES30.GL_RENDERBUFFER, format, w, h)
    }
    override fun attachRenderbuffer(attachment: Int, id: Int) = GLES30.glFramebufferRenderbuffer(GLES30.GL_FRAMEBUFFER, attachment, GLES30.GL_RENDERBUFFER, id)
    override fun attachDepthTexture(id: Int) = GLES30.glFramebufferTexture2D(GLES30.GL_FRAMEBUFFER, GLES30.GL_DEPTH_ATTACHMENT, GLES30.GL_TEXTURE_2D, id, 0)
    override fun framebufferComplete() = GLES30.glCheckFramebufferStatus(GLES30.GL_FRAMEBUFFER) == GLES30.GL_FRAMEBUFFER_COMPLETE
    override fun noColorBuffers() {
        one[0] = GLES30.GL_NONE
        GLES30.glDrawBuffers(1, one, 0)
        GLES30.glReadBuffer(GLES30.GL_NONE)
    }
    override fun blit(w: Int, h: Int) = GLES30.glBlitFramebuffer(0, 0, w, h, 0, 0, w, h, GLES30.GL_COLOR_BUFFER_BIT, GLES30.GL_NEAREST)
    override fun readRgba(w: Int, h: Int, out: ByteBuffer) {
        GLES30.glPixelStorei(GLES30.GL_PACK_ALIGNMENT, 1)
        GLES30.glReadPixels(0, 0, w, h, GLES30.GL_RGBA, GLES30.GL_UNSIGNED_BYTE, out)
    }
    override fun viewport(w: Int, h: Int) = GLES30.glViewport(0, 0, w, h)
    override fun clear(r: Float, g: Float, b: Float, a: Float) {
        GLES30.glClearColor(r, g, b, a)
        GLES30.glDepthMask(true)
        GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT or GLES30.GL_DEPTH_BUFFER_BIT)
    }
    override fun clearDepth() {
        GLES30.glDepthMask(true)
        GLES30.glClear(GLES30.GL_DEPTH_BUFFER_BIT)
    }
    override fun enable(cap: Int) = GLES30.glEnable(cap)
    override fun disable(cap: Int) = GLES30.glDisable(cap)
    override fun depthMask(on: Boolean) = GLES30.glDepthMask(on)
    override fun blendPremultiplied() = GLES30.glBlendFunc(GLES30.GL_ONE, GLES30.GL_ONE_MINUS_SRC_ALPHA)
    override fun polygonOffset(factor: Float, units: Float) = GLES30.glPolygonOffset(factor, units)
    override fun maxSamples(): Int {
        GLES30.glGetIntegerv(GLES30.GL_MAX_SAMPLES, one, 0)
        return one[0]
    }
    override fun error() = GLES30.glGetError()
}
