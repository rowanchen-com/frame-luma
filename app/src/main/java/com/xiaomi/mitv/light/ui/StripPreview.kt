package com.xiaomi.mitv.light.ui

import android.content.Context
import android.graphics.*
import android.view.View
import com.xiaomi.mitv.light.core.EdgeLayout
import kotlin.math.min

class StripPreview(context: Context) : View(context) {
    private val panel = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xff13252b.toInt() }
    private val brand = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER; typeface = Typeface.create("sans-serif-light", Typeface.NORMAL) }
    private val light = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 5 * resources.displayMetrics.density }
    private val rect = RectF()
    private val path = Path()
    private var colors = IntArray(20) { 0x334050 }
    private var reverse = false

    fun update(next: IntArray, layout: EdgeLayout, reversed: Boolean) {
        if (next.size != 9) return
        val mapped = layout.map(next)
        if (colors.contentEquals(mapped) && reverse == reversed) return
        colors = mapped; reverse = reversed; invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        light.strokeWidth = width * .012f
        val inset = light.strokeWidth
        rect.set(inset, inset, width - inset, height - inset)
        canvas.drawRoundRect(rect, inset, inset, panel)
        brand.color = 0xffb0e8cf.toInt(); brand.textSize = width * .16f
        canvas.drawText("F", width * .5f, height * .55f, brand)
        brand.color = 0xff6b9e90.toInt(); brand.textSize = width * .035f
        canvas.drawText("FRAME  /  LUMA", width * .5f, height * .73f, brand)
        val w = rect.width(); val h = rect.height(); val perimeter = 2 * (w + h)
        val total = colors.size.coerceAtLeast(1)
        val corners = floatArrayOf(w / 2, w / 2 + h, w * 1.5f + h, w * 1.5f + h * 2)
        var at = 0f
        for (unit in colors.indices) {
            val length = perimeter / total
            if (length <= 0) continue
            val end = at + length - 1f
            path.rewind(); point(at, w, h).let { path.moveTo(rect.left + it.first, rect.top + it.second) }
            corners.filter { it > at && it < end }.forEach { d -> point(d, w, h).let { path.lineTo(rect.left + it.first, rect.top + it.second) } }
            point(end, w, h).let { path.lineTo(rect.left + it.first, rect.top + it.second) }
            light.color = 0xff000000.toInt() or colors[unit]; canvas.drawPath(path, light); at += length
        }
    }
    private fun point(distance: Float, w: Float, h: Float): Pair<Float, Float> {
        var d = distance
        val p = when {
            d < w / 2 -> Pair(w / 2 - d, h)
            (d - w / 2) < h -> Pair(0f, h - (d - w / 2))
            (d - w / 2 - h) < w -> Pair(d - w / 2 - h, 0f)
            (d - w * 1.5f - h) < h -> Pair(w, d - w * 1.5f - h)
            else -> Pair(w - min(d - w * 1.5f - h * 2, w / 2), h)
        }
        return Pair(if (reverse) w - p.first else p.first, p.second)
    }
}
