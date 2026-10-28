package com.xiaomi.mitv.light.ui

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.view.Gravity
import android.view.MotionEvent
import android.widget.LinearLayout
import android.widget.SeekBar
import com.xiaomi.mitv.light.R
import kotlin.math.ceil

class BrightnessSlider(context: Context, private val changed: (Int) -> Unit) : LinearLayout(context) {
    private val slider = object : SeekBar(context) {
        private fun acceptPosition(x: Float) {
            val next = PercentTrack.at(x, width, paddingLeft, paddingRight)
            if (progress != next) { progress = next; value.text = "$next%"; changed(next) }
        }
        override fun onTouchEvent(event: MotionEvent): Boolean {
            if (!isEnabled) return false
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    parent?.requestDisallowInterceptTouchEvent(true)
                    tracking = true; isPressed = true; acceptPosition(event.x); return true
                }
                MotionEvent.ACTION_MOVE -> { acceptPosition(event.x); return true }
                MotionEvent.ACTION_UP -> {
                    acceptPosition(event.x); isPressed = false; tracking = false
                    parent?.requestDisallowInterceptTouchEvent(false); performClick(); return true
                }
                MotionEvent.ACTION_CANCEL -> {
                    isPressed = false; tracking = false; parent?.requestDisallowInterceptTouchEvent(false); return true
                }
            }
            return super.onTouchEvent(event)
        }
        override fun performClick(): Boolean = super.performClick()
    }.apply {
        id = R.id.brightness_seek
        max = 100; isFocusable = false; splitTrack = false
        progressTintList = ColorStateList.valueOf(0xffe3edff.toInt())
        progressBackgroundTintList = ColorStateList.valueOf(0x55ffffff)
        thumbTintList = ColorStateList.valueOf(Color.WHITE)
        contentDescription = "灯带亮度"
    }
    private var tracking = false
    private val value = designText(context, "", 14f).apply { id = R.id.brightness_value; gravity = Gravity.CENTER_VERTICAL or Gravity.END }
    init {
        orientation = HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
        addView(slider, LayoutParams(0, -1, 1f)); addView(value, LayoutParams(44, -1))
        slider.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(bar: SeekBar, progress: Int, fromUser: Boolean) {
                if (fromUser) { value.text = "$progress%"; changed(progress) }
            }
            override fun onStartTrackingTouch(bar: SeekBar) { }
            override fun onStopTrackingTouch(bar: SeekBar) { }
        })
    }
    fun applyUnit(unit: Float) {
        value.layoutParams = LayoutParams((44 * unit).toInt(), -1)
        val diameter = 18 * unit
        slider.thumb = RoundKnob(diameter)
        slider.thumbOffset = (diameter / 2).toInt()
        val inset = ceil(diameter / 2 + 5 * unit).toInt()
        slider.setPadding(inset, 0, inset, 0)
    }
    fun show(brightness: Int) {
        if (tracking) { value.text = "${slider.progress}%"; return }
        val bounded = brightness.coerceIn(0, 100)
        if (slider.progress != bounded) slider.progress = bounded
        value.text = "$bounded%"
    }
}
