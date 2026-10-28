package com.xiaomi.mitv.light.ui

import android.content.Context
import android.graphics.*
import android.view.View
import com.xiaomi.mitv.light.core.EdgeLayout

/** Thin segmented outline with a quiet interior; colors and lengths come from the actual strip layout. */
class StripPreview(context: Context) : View(context) {
    private val screen = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xff1b1922.toInt() }
    private val light = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeCap = Paint.Cap.BUTT; strokeJoin = Paint.Join.MITER
    }
    private val rect = RectF()
    private val point = FloatArray(2)
    private var zones = IntArray(9) { 0x8293b3 }
    private var mapping = IntArray(0)
    private var segments = emptyList<StripOutline.Segment>()
    private var paths = emptyArray<Path>()
    private var reverse = false
    private var stroke = 1f

    fun update(next: IntArray, layout: EdgeLayout, reversed: Boolean) {
        if (next.size != 9) return
        val topology = IntArray(layout.unitCount()) { layout.zoneAt(it) }
        val geometryChanged = !mapping.contentEquals(topology) || reverse != reversed
        val colorsChanged = !zones.contentEquals(next)
        if (!geometryChanged && !colorsChanged) return
        zones = next.copyOf(); reverse = reversed
        if (geometryChanged) {
            mapping = topology; segments = StripOutline.segments(layout)
            paths = Array(segments.size) { Path() }; rebuildPaths()
        }
        invalidate()
    }
    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) { rebuildPaths() }
    private fun rebuildPaths() {
        if (width <= 0 || height <= 0) return
        val w = minOf(width * .88f, height * .86f * 16f / 9)
        val h = w * 9f / 16
        rect.set((width - w) / 2, (height - h) / 2, (width + w) / 2, (height + h) / 2)
        stroke = w * .021f; light.strokeWidth = stroke
        val perimeter = 2 * (w + h)
        val corners = floatArrayOf(w / 2, w / 2 + h, w * 1.5f + h, w * 1.5f + h * 2)
        val gap = maxOf(.7f, w * .002f)
        for (i in segments.indices) {
            val segment = segments[i]
            val length = (segment.end - segment.start) * perimeter
            val at = segment.start * perimeter + minOf(gap / 2, length / 8)
            val end = segment.end * perimeter - minOf(gap / 2, length / 8)
            val path = paths[i]; path.rewind()
            StripOutline.point(at, w, h, reverse, point); path.moveTo(rect.left + point[0], rect.top + point[1])
            for (corner in corners) if (corner > at && corner < end) {
                StripOutline.point(corner, w, h, reverse, point); path.lineTo(rect.left + point[0], rect.top + point[1])
            }
            StripOutline.point(end, w, h, reverse, point); path.lineTo(rect.left + point[0], rect.top + point[1])
        }
    }
    override fun onDraw(canvas: Canvas) {
        canvas.drawRect(rect.left - stroke / 2, rect.top - stroke / 2, rect.right + stroke / 2, rect.bottom + stroke / 2, screen)
        for (i in segments.indices) {
            val zone = segments[i].zone
            light.color = if (zone < 0) 0xff34313d.toInt() else 0xff000000.toInt() or zones[zone]
            canvas.drawPath(paths[i], light)
        }
    }
}
