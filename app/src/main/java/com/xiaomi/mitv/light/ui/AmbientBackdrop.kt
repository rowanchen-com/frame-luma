package com.xiaomi.mitv.light.ui

import android.content.Context
import android.graphics.*
import android.os.SystemClock
import android.view.View
import kotlin.math.cos
import kotlin.math.sin

/** FrameLuma's teal/copper light field, reused at 192x108 and drawn only while visible. */
class AmbientBackdrop(context: Context) : View(context) {
    private val bitmap = Bitmap.createBitmap(192, 108, Bitmap.Config.ARGB_8888)
    private val buffer = Canvas(bitmap)
    private val transform = Matrix()
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { xfermode = PorterDuffXfermode(PorterDuff.Mode.SCREEN) }
    private val stretch = Paint(Paint.FILTER_BITMAP_FLAG)
    private val bounds = Rect()
    private val shaders = intArrayOf(0xff2bb99c.toInt(), 0xff2965ad.toInt(), 0xffbd874a.toInt()).map {
        RadialGradient(0f, 0f, 112f, it, Color.TRANSPARENT, Shader.TileMode.CLAMP)
    }
    var active = false
        set(value) { field = value; if (value) invalidate() }
    override fun onDraw(canvas: Canvas) {
        bitmap.eraseColor(0xff09171e.toInt())
        val time = SystemClock.uptimeMillis() / 1000.0
        shaders.forEachIndexed { index, shader ->
            val x = 96 + 100 * sin(time * .24 + index * 2.1)
            val y = 54 + 48 * cos(time * .31 + index * 1.5)
            transform.setTranslate(x.toFloat(), y.toFloat()); shader.setLocalMatrix(transform)
            paint.shader = shader; buffer.drawRect(0f, 0f, 192f, 108f, paint)
        }
        bounds.set(0, 0, width, height); canvas.drawBitmap(bitmap, null, bounds, stretch)
        if (active && windowVisibility == VISIBLE) postInvalidateDelayed(100)
    }
}
