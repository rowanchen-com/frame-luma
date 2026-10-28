package com.xiaomi.mitv.light.ui

import android.content.Context
import android.graphics.*
import android.os.SystemClock
import android.view.View
import kotlin.math.sin

/** Soft moving aurora: seven feathered lights, a clockwise orbit and a continuous full-hue cycle. */
class AmbientBackdrop(context: Context) : View(context) {
    private val bitmap = Bitmap.createBitmap(192, 108, Bitmap.Config.ARGB_8888)
    private val buffer = Canvas(bitmap)
    private val transform = Matrix()
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { xfermode = PorterDuffXfermode(PorterDuff.Mode.SCREEN) }
    private val stretch = Paint(Paint.FILTER_BITMAP_FLAG or Paint.DITHER_FLAG)
    private val bounds = Rect()
    private val started = SystemClock.uptimeMillis()
    // The palette is created once. Adjacent entries are crossfaded; no per-frame shader/bitmap creation.
    private val palette = Array(AuroraMotion.PALETTE) { i ->
        val color = Color.HSVToColor(floatArrayOf(i * 360f / AuroraMotion.PALETTE, .77f, .88f))
        RadialGradient(0f, 0f, 1f, intArrayOf(color, color and 0x88ffffff.toInt(), color and 0x00ffffff), floatArrayOf(0f, .42f, 1f), Shader.TileMode.CLAMP)
    }
    var active = false
        set(value) { field = value; if (value) invalidate() }
    override fun onDraw(canvas: Canvas) {
        renderFrame((SystemClock.uptimeMillis() - started) / 1000.0)
        bounds.set(0, 0, width, height); canvas.drawBitmap(bitmap, null, bounds, stretch)
        if (active && windowVisibility == VISIBLE) postInvalidateDelayed(66)
    }
    private fun renderFrame(seconds: Double) {
        bitmap.eraseColor(0xff1a172d.toInt())
        for (i in 0 until AuroraMotion.COUNT) {
            val phase = AuroraMotion.hue(seconds, i) * AuroraMotion.PALETTE / 360f
            val first = phase.toInt() % AuroraMotion.PALETTE; val mix = phase - phase.toInt()
            val x = AuroraMotion.x(seconds, i); val y = AuroraMotion.y(seconds, i)
            val radius = (72 + 10 * sin(seconds * .11 + i * .9)).toFloat()
            transform.setScale(radius * 1.2f, radius)
            transform.postTranslate(x, y)
            palette[first].setLocalMatrix(transform); paint.shader = palette[first]; paint.alpha = (195 * (1 - mix)).toInt()
            buffer.drawRect(0f, 0f, 192f, 108f, paint)
            val second = (first + 1) % AuroraMotion.PALETTE
            palette[second].setLocalMatrix(transform); paint.shader = palette[second]; paint.alpha = (195 * mix).toInt()
            buffer.drawRect(0f, 0f, 192f, 108f, paint)
        }
    }
}
