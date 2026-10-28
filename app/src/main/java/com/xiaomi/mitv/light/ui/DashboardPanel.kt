package com.xiaomi.mitv.light.ui

import android.content.Context
import android.graphics.*
import android.os.Build
import android.text.TextUtils
import android.util.TypedValue
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import com.xiaomi.mitv.light.R
import kotlin.math.roundToInt

/** Measure every control into the available window; no scroll or post-layout scaling. */
class CenteredStage(context: Context, private val panel: DashboardPanel) : ViewGroup(context) {
    private var viewport = CenteredViewport.fit(1, 1)
    init { addView(panel) }
    override fun onMeasure(widthSpec: Int, heightSpec: Int) {
        val w = MeasureSpec.getSize(widthSpec); val h = MeasureSpec.getSize(heightSpec)
        viewport = CenteredViewport.fit(w.coerceAtLeast(1), h.coerceAtLeast(1))
        panel.measure(MeasureSpec.makeMeasureSpec(viewport.width, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(viewport.height, MeasureSpec.EXACTLY))
        setMeasuredDimension(w, h)
    }
    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        panel.layout(viewport.left, viewport.top, viewport.left + viewport.width, viewport.top + viewport.height)
    }
}

class DashboardPanel(context: Context) : ViewGroup(context) {
    private data class Slot(val view: View, val x: Float, val y: Float, val w: Float, val h: Float)
    private val slots = mutableListOf<Slot>()
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val bounds = RectF()
    private val edge = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; color = 0x2bffffff }
    private val separators = intArrayOf(79, 137, 195)
    private val footerDividers = intArrayOf(158, 472, 704)
    private var unit = Float.NaN
    init { id = R.id.dashboard_panel; setWillNotDraw(false) }
    fun place(view: View, x: Int, y: Int, w: Int, h: Int) {
        slots.add(Slot(view, x.toFloat(), y.toFloat(), w.toFloat(), h.toFloat())); addView(view)
    }
    override fun onMeasure(widthSpec: Int, heightSpec: Int) {
        val w = MeasureSpec.getSize(widthSpec); val h = MeasureSpec.getSize(heightSpec)
        val nextUnit = w / CenteredViewport.WIDTH.toFloat()
        val resized = nextUnit != unit
        unit = nextUnit
        for (slot in slots) {
            if (resized) {
                scaleText(slot.view)
                if (slot.view is ControlTile) slot.view.applyUnit(unit)
            }
            slot.view.measure(MeasureSpec.makeMeasureSpec((slot.w * unit).roundToInt(), MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec((slot.h * unit).roundToInt(), MeasureSpec.EXACTLY))
        }
        setMeasuredDimension(w, h)
    }
    private fun scaleText(view: View) {
        if (view is SlideToggle) { view.applyUnit(unit); return }
        if (view is BrightnessSlider) view.applyUnit(unit)
        if (view is TextView) {
            val size = (view.getTag(R.id.design_text_size) as? Float ?: 15f) * unit
            view.setTextSize(TypedValue.COMPLEX_UNIT_PX, size)
            view.includeFontPadding = false; view.setSingleLine(); view.ellipsize = TextUtils.TruncateAt.END
            if (Build.VERSION.SDK_INT >= 26) view.setAutoSizeTextTypeUniformWithConfiguration((size * .8f).roundToInt().coerceAtLeast(1), size.roundToInt().coerceAtLeast(2), 1, TypedValue.COMPLEX_UNIT_PX)
        }
        if (view is ViewGroup) for (i in 0 until view.childCount) scaleText(view.getChildAt(i))
    }
    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        for (slot in slots) {
            val x = (slot.x * unit).roundToInt(); val y = (slot.y * unit).roundToInt()
            slot.view.layout(x, y, x + slot.view.measuredWidth, y + slot.view.measuredHeight)
        }
    }
    override fun onDraw(canvas: Canvas) {
        bounds.set(.5f, .5f, width - .5f, height - .5f)
        paint.style = Paint.Style.FILL; paint.color = 0xb0161720.toInt()
        canvas.drawRoundRect(bounds, 18 * unit, 18 * unit, paint)
        edge.style = Paint.Style.STROKE; edge.strokeWidth = unit
        canvas.drawRoundRect(bounds, 18 * unit, 18 * unit, edge)
        paint.color = 0x24ffffff; canvas.drawRect(26 * unit, 324 * unit, width - 26 * unit, 325 * unit, paint)
        paint.color = 0x16ffffff
        for (y in separators) canvas.drawRect(42 * unit, y * unit, 406 * unit, (y + .6f) * unit, paint)
        paint.color = 0x2bffffff
        for (x in footerDividers) canvas.drawRect(x * unit, 340 * unit, (x + .7f) * unit, 387 * unit, paint)
    }
}
