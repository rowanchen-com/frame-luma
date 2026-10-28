package com.xiaomi.mitv.light.ui

import android.content.Context
import android.graphics.*
import android.os.SystemClock
import android.view.View
import kotlin.math.cos
import kotlin.math.sin

/** Small reusable in-memory canvas; animation exists only while settings are visible. */
class AmbientBackdrop(context: Context) : View(context) {
    private val bitmap = Bitmap.createBitmap(128, 72, Bitmap.Config.ARGB_8888)
    private val buffer = Canvas(bitmap)
    private val transform = Matrix()
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { xfermode = PorterDuffXfermode(PorterDuff.Mode.SCREEN) }
    private val stretch = Paint(Paint.FILTER_BITMAP_FLAG)
    private val bounds = Rect()
    private val shaders = intArrayOf(0xff276c83.toInt(), 0xff34356f.toInt(), 0xff6b2d75.toInt(), 0xff806240.toInt()).map {
        RadialGradient(0f, 0f, 88f, it, Color.TRANSPARENT, Shader.TileMode.CLAMP)
    }
    var active = false
        set(value) { field = value; if (value) invalidate() }

    override fun onDraw(canvas: Canvas) {
        bitmap.eraseColor(0xff101326.toInt())
        val t = SystemClock.uptimeMillis() / 1000.0
        shaders.forEachIndexed { i, shader ->
            transform.setTranslate((64 + 55 * sin(t * .035 + i * 1.7)).toFloat(), (36 + 28 * cos(t * .045 + i)).toFloat())
            shader.setLocalMatrix(transform)
            paint.shader = shader
            buffer.drawRect(0f, 0f, 128f, 72f, paint)
        }
        bounds.set(0, 0, width, height)
        canvas.drawBitmap(bitmap, null, bounds, stretch)
        if (active && windowVisibility == VISIBLE) postInvalidateDelayed(100)
    }
}
