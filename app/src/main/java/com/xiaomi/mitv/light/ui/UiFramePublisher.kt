package com.xiaomi.mitv.light.ui

import android.app.Activity
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.WindowManager
import com.xiaomi.mitv.light.core.EdgeRegions
import com.xiaomi.mitv.light.tcl.AppCore

interface UiFrameConsumer { fun onUiFrame(colors: IntArray) }

/** Read our own View tree into a small RAM bitmap; no MediaProjection or screenshot files. */
class UiFramePublisher(val activity: Activity) {
    private val handler = Handler(Looper.getMainLooper())
    private val bitmap = Bitmap.createBitmap(192, 108, Bitmap.Config.ARGB_8888)
    private val canvas = Canvas(bitmap)
    private var active = false
    private var previousLayout = ""
    private var points = emptyArray<Array<IntArray>>()
    private var reportedError = false
    private val hadKeepScreenOn = activity.window.attributes.flags and WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON != 0
    private val capture = object : Runnable {
        override fun run() {
            if (!active) return
            try {
                val view = activity.window.decorView
                if (view.width > 0 && view.height > 0 && view.isShown) {
                    val layout = AppCore.layout()
                    val key = layout.json().toString()
                    if (key != previousLayout) {
                        points = EdgeRegions.points(bitmap.width, bitmap.height, layout.samples, layout.depth)
                        previousLayout = key
                    }
                    bitmap.eraseColor(Color.TRANSPARENT)
                    val count = canvas.save()
                    try {
                        canvas.scale(bitmap.width / view.width.toFloat(), bitmap.height / view.height.toFloat())
                        view.draw(canvas)
                    } finally { canvas.restoreToCount(count) }
                    val colors = IntArray(9) { zone ->
                        var red = 0; var green = 0; var blue = 0
                        for (point in points[zone]) {
                            val color = bitmap.getPixel(point[0], point[1])
                            red += Color.red(color); green += Color.green(color); blue += Color.blue(color)
                        }
                        Color.rgb(red / layout.samples, green / layout.samples, blue / layout.samples) and 0xffffff
                    }
                    AppCore.publishUiFrame(colors, key)
                    (activity as? UiFrameConsumer)?.onUiFrame(colors)
                    reportedError = false
                }
            } catch (error: Exception) {
                if (!reportedError) Log.w("FrameLumaUi", "Own view capture unavailable", error)
                reportedError = true
            }
            if (active) handler.postDelayed(this, 150)
        }
    }
    fun start() { active = true; activity.window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON); handler.post(capture) }
    fun close() {
        active = false; handler.removeCallbacksAndMessages(null); bitmap.recycle()
        if (!hadKeepScreenOn) activity.window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }
}
