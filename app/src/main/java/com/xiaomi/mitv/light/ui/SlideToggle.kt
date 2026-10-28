package com.xiaomi.mitv.light.ui

import android.content.Context
import android.graphics.*
import android.graphics.drawable.Drawable
import android.widget.Switch
import kotlin.math.roundToInt

/** Android Switch owns touch dragging, checked state, animation and accessibility. */
class SlideToggle(context: Context) : Switch(context) {
    init {
        text = ""; showText = false; splitTrack = false
        isFocusable = false; isClickable = true; contentDescription = "灯带随影"
        thumbTextPadding = 0; switchPadding = 0
        thumbTintList = android.content.res.ColorStateList.valueOf(Color.WHITE)
        trackTintList = null
    }
    fun applyUnit(unit: Float) {
        switchMinWidth = (54 * unit).roundToInt()
        thumbDrawable = RoundKnob(24 * unit)
        trackDrawable = ToggleTrack(unit)
    }
    private class ToggleTrack(private val unit: Float) : Drawable() {
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        private val rect = RectF()
        override fun getIntrinsicWidth() = (54 * unit).roundToInt()
        override fun getIntrinsicHeight() = (30 * unit).roundToInt()
        override fun getPadding(padding: Rect): Boolean { padding.set((3 * unit).roundToInt(), 0, (3 * unit).roundToInt(), 0); return true }
        override fun isStateful() = true
        override fun onStateChange(state: IntArray): Boolean { invalidateSelf(); return true }
        override fun draw(canvas: Canvas) {
            rect.set(bounds); paint.color = if (state.contains(android.R.attr.state_checked)) UiTheme.blue else 0x55ffffff
            canvas.drawRoundRect(rect, rect.height() / 2, rect.height() / 2, paint)
        }
        override fun setAlpha(alpha: Int) { }
        override fun setColorFilter(filter: ColorFilter?) { }
        @Deprecated("Deprecated in Android") override fun getOpacity() = PixelFormat.TRANSLUCENT
    }
}

/** Native Switch may stretch drawable bounds: derive a circle from the shorter side. */
class RoundKnob(private val diameter: Float) : Drawable() {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
    override fun getIntrinsicWidth() = diameter.roundToInt()
    override fun getIntrinsicHeight() = diameter.roundToInt()
    override fun draw(canvas: Canvas) {
        val radius = minOf(bounds.width(), bounds.height()) / 2f
        canvas.drawCircle(bounds.exactCenterX(), bounds.exactCenterY(), radius, paint)
    }
    override fun setAlpha(alpha: Int) { }
    override fun setColorFilter(filter: ColorFilter?) { }
    @Deprecated("Deprecated in Android") override fun getOpacity() = PixelFormat.TRANSLUCENT
}
