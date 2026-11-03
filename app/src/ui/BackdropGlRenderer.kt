package frameluma.app.ui

import android.opengl.EGL14
import android.opengl.EGLConfig
import android.opengl.GLES20
import android.opengl.GLUtils
import android.view.Surface
import androidx.compose.ui.unit.IntSize
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** All EGL operations stay on the owning thread. GLES 2 does not require an Android 13 shader API. */
internal class BackdropGlRenderer private constructor() : AutoCloseable {
    private var display = EGL14.EGL_NO_DISPLAY
    private var context = EGL14.EGL_NO_CONTEXT
    private var window = EGL14.EGL_NO_SURFACE
    private var program = 0
    private val textures = IntArray(BackdropMotion.PHASE_COUNT)
    private var blendLocation = 0
    private val vertices = ByteBuffer.allocateDirect(8 * 4).order(ByteOrder.nativeOrder())
        .asFloatBuffer().apply { put(floatArrayOf(-1f, -1f, 1f, -1f, -1f, 1f, 1f, 1f)); position(0) }

    private fun initialize(surface: Surface, palette: BackdropPalette) {
        display = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
        check(display != EGL14.EGL_NO_DISPLAY) { "EGL display unavailable" }
        val version = IntArray(2)
        check(EGL14.eglInitialize(display, version, 0, version, 1)) { "EGL initialize failed" }
        val configs = arrayOfNulls<EGLConfig>(1)
        val count = IntArray(1)
        val attributes = intArrayOf(
            EGL14.EGL_RENDERABLE_TYPE, EGL14.EGL_OPENGL_ES2_BIT,
            EGL14.EGL_SURFACE_TYPE, EGL14.EGL_WINDOW_BIT,
            EGL14.EGL_RED_SIZE, 8, EGL14.EGL_GREEN_SIZE, 8,
            EGL14.EGL_BLUE_SIZE, 8, EGL14.EGL_ALPHA_SIZE, 8,
            EGL14.EGL_DEPTH_SIZE, 0, EGL14.EGL_STENCIL_SIZE, 0, EGL14.EGL_NONE,
        )
        check(EGL14.eglChooseConfig(display, attributes, 0, configs, 0, 1, count, 0) && count[0] > 0) {
            "EGL RGBA8 configuration unavailable"
        }
        context = EGL14.eglCreateContext(display, configs[0], EGL14.EGL_NO_CONTEXT,
            intArrayOf(EGL14.EGL_CONTEXT_CLIENT_VERSION, 2, EGL14.EGL_NONE), 0)
        check(context != EGL14.EGL_NO_CONTEXT) { "EGL context unavailable" }
        window = EGL14.eglCreateWindowSurface(display, configs[0], surface, intArrayOf(EGL14.EGL_NONE), 0)
        check(window != EGL14.EGL_NO_SURFACE) { "EGL surface unavailable" }
        check(EGL14.eglMakeCurrent(display, window, window, context)) { "EGL bind failed" }
        EGL14.eglSwapInterval(display, 1)
        program = GLES20.glCreateProgram()
        val vertex = compile(GLES20.GL_VERTEX_SHADER, VERTEX)
        try {
            val fragment = compile(GLES20.GL_FRAGMENT_SHADER, FRAGMENT)
            try {
                GLES20.glAttachShader(program, vertex)
                GLES20.glAttachShader(program, fragment)
                GLES20.glBindAttribLocation(program, 0, "position")
                GLES20.glLinkProgram(program)
                val status = IntArray(1)
                GLES20.glGetProgramiv(program, GLES20.GL_LINK_STATUS, status, 0)
                check(status[0] != 0) { GLES20.glGetProgramInfoLog(program) }
            } finally { GLES20.glDeleteShader(fragment) }
        } finally { GLES20.glDeleteShader(vertex) }
        GLES20.glUseProgram(program)
        GLES20.glUniform1i(GLES20.glGetUniformLocation(program, "firstField"), 0)
        GLES20.glUniform1i(GLES20.glGetUniformLocation(program, "nextField"), 1)
        blendLocation = GLES20.glGetUniformLocation(program, "blend")
        GLES20.glEnableVertexAttribArray(0)
        GLES20.glVertexAttribPointer(0, 2, GLES20.GL_FLOAT, false, 0, vertices)
        GLES20.glDisable(GLES20.GL_BLEND)
        GLES20.glDisable(GLES20.GL_DEPTH_TEST)
        GLES20.glGenTextures(textures.size, textures, 0)
        for (index in textures.indices) {
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textures[index])
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)
            GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, palette.texture(index), 0)
        }
        check(GLES20.glGetError() == GLES20.GL_NO_ERROR) { "Background texture upload failed" }
    }

    fun draw(elapsedMs: Long, size: IntSize) {
        val phase = BackdropMotion.phase(elapsedMs)
        val first = phase.toInt()
        GLES20.glViewport(0, 0, size.width, size.height)
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textures[first])
        GLES20.glActiveTexture(GLES20.GL_TEXTURE1)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textures[(first + 1) % textures.size])
        GLES20.glUniform1f(blendLocation, phase - first)
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
        check(EGL14.eglSwapBuffers(display, window)) { "Background surface was lost" }
    }

    override fun close() {
        if (display == EGL14.EGL_NO_DISPLAY) return
        if (context != EGL14.EGL_NO_CONTEXT && window != EGL14.EGL_NO_SURFACE &&
            EGL14.eglMakeCurrent(display, window, window, context)) {
            GLES20.glDeleteTextures(textures.size, textures, 0)
            if (program != 0) GLES20.glDeleteProgram(program)
        }
        EGL14.eglMakeCurrent(display, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT)
        if (window != EGL14.EGL_NO_SURFACE) EGL14.eglDestroySurface(display, window)
        if (context != EGL14.EGL_NO_CONTEXT) EGL14.eglDestroyContext(display, context)
        EGL14.eglTerminate(display)
        EGL14.eglReleaseThread()
        display = EGL14.EGL_NO_DISPLAY
    }

    companion object {
        fun open(surface: Surface, palette: BackdropPalette): BackdropGlRenderer {
            val renderer = BackdropGlRenderer()
            try { renderer.initialize(surface, palette); return renderer }
            catch (error: Throwable) { renderer.close(); throw error }
        }

        private fun compile(type: Int, source: String): Int {
            val shader = GLES20.glCreateShader(type)
            GLES20.glShaderSource(shader, source)
            GLES20.glCompileShader(shader)
            val status = IntArray(1)
            GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, status, 0)
            if (status[0] != 0) return shader
            val message = GLES20.glGetShaderInfoLog(shader)
            GLES20.glDeleteShader(shader)
            error(message)
        }

        private const val VERTEX = """
            attribute vec2 position;
            varying vec2 uv;
            void main() {
                uv = vec2((position.x + 1.0) * 0.5, (1.0 - position.y) * 0.5);
                gl_Position = vec4(position, 0.0, 1.0);
            }
        """
        // Filter before final quantization; static sub-LSB dithering breaks contour bands.
        private const val FRAGMENT = """
            #ifdef GL_FRAGMENT_PRECISION_HIGH
            precision highp float;
            #else
            precision mediump float;
            #endif
            uniform sampler2D firstField;
            uniform sampler2D nextField;
            uniform float blend;
            varying vec2 uv;
            float bayer2(vec2 p) { return 2.0 * p.x + 3.0 * p.y - 4.0 * p.x * p.y; }
            void main() {
                vec3 color = mix(texture2D(firstField, uv).rgb, texture2D(nextField, uv).rgb, blend);
                vec2 pixel = floor(gl_FragCoord.xy);
                float rank = 4.0 * bayer2(mod(pixel, 2.0)) + bayer2(floor(mod(pixel, 4.0) / 2.0));
                float dither = ((rank + 0.5) / 16.0 - 0.5) / 255.0;
                gl_FragColor = vec4(clamp(color + dither, 0.0, 1.0), 1.0);
            }
        """
    }
}
