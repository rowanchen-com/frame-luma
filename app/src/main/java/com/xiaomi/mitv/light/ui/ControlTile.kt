package com.xiaomi.mitv.light.ui

import android.content.Context
import android.graphics.*
import android.graphics.drawable.Drawable
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.TextView
import com.xiaomi.mitv.light.R

fun designText(context: Context, text: String, size: Float, color: Int = UiTheme.text) = TextView(context).apply {
    this.text = text; setTextColor(color); gravity = Gravity.CENTER_VERTICAL
    setTag(R.id.design_text_size, size)
}

class TileSurface : Drawable() {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rect = RectF()
    override fun isStateful() = true
    override fun onStateChange(state: IntArray): Boolean { invalidateSelf(); return true }
    override fun draw(canvas: Canvas) {
        val u = bounds.height() / 56f
        val focused = state.contains(android.R.attr.state_focused)
        rect.set(bounds.left + u, bounds.top + u, bounds.right - u, bounds.bottom - u)
        paint.style = Paint.Style.FILL; paint.color = if (focused) 0x14ffffff else Color.TRANSPARENT
        canvas.drawRoundRect(rect, 8 * u, 8 * u, paint)
        paint.style = Paint.Style.STROKE; paint.strokeWidth = if (focused) 1.6f * u else u
        paint.color = if (focused) 0x99dee8f7.toInt() else Color.TRANSPARENT
        canvas.drawRoundRect(rect, 8 * u, 8 * u, paint)
    }
    override fun setAlpha(alpha: Int) { }
    override fun setColorFilter(filter: ColorFilter?) { }
    @Deprecated("Deprecated in Android") override fun getOpacity() = PixelFormat.TRANSLUCENT
}

class ChoiceSurface : Drawable() {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rect = RectF()
    override fun isStateful() = true
    override fun onStateChange(state: IntArray): Boolean { invalidateSelf(); return true }
    override fun draw(canvas: Canvas) {
        if (!state.contains(android.R.attr.state_selected)) return
        val inset = bounds.height() * .23f
        val side = bounds.height() * .055f
        rect.set(bounds.left + side, bounds.top + inset, bounds.right - side, bounds.bottom - inset)
        paint.color = 0xdde8f0ff.toInt(); canvas.drawRoundRect(rect, side * 1.4f, side * 1.4f, paint)
    }
    override fun setAlpha(alpha: Int) { }
    override fun setColorFilter(filter: ColorFilter?) { }
    @Deprecated("Deprecated in Android") override fun getOpacity() = PixelFormat.TRANSLUCENT
}

class SegmentTrack : Drawable() {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x18ffffff }
    private val rect = RectF()
    override fun draw(canvas: Canvas) {
        val inset = bounds.height() * .18f
        rect.set(bounds.left.toFloat(), bounds.top + inset, bounds.right.toFloat(), bounds.bottom - inset)
        canvas.drawRoundRect(rect, inset * .6f, inset * .6f, paint)
    }
    override fun setAlpha(alpha: Int) { }
    override fun setColorFilter(filter: ColorFilter?) { }
    @Deprecated("Deprecated in Android") override fun getOpacity() = PixelFormat.TRANSLUCENT
}

class ControlTile(context: Context, label: String, private val valueWidth: Int = 180) : LinearLayout(context) {
    val value = designText(context, "", 16f).apply { gravity = Gravity.CENTER_VERTICAL or Gravity.END }
    val values = LinearLayout(context).apply { gravity = Gravity.CENTER_VERTICAL or Gravity.END }
    private var unit = 1f
    init {
        orientation = HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
        isFocusable = true; isClickable = true; background = TileSurface()
        addView(designText(context, label, 16f), LayoutParams(0, -1, 1f))
        values.addView(value, LayoutParams(-1, -1)); addView(values, LayoutParams(valueWidth, -1))
    }
    fun applyUnit(value: Float) {
        unit = value; setPadding((18 * value).toInt(), 0, (18 * value).toInt(), 0)
        values.layoutParams = LayoutParams((valueWidth * value).toInt(), -1)
    }
    fun choices(labels: List<String>, action: (Int) -> Unit): List<TextView> {
        values.removeAllViews()
        return labels.mapIndexed { index, label ->
            designText(context, label, 16f).apply {
                gravity = Gravity.CENTER; isFocusable = false; isClickable = true
                setOnClickListener { action(index) }
                values.addView(this, LayoutParams(0, -1, 1f))
            }
        }
    }
}
