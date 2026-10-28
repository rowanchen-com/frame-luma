package com.xiaomi.mitv.light.ui

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.SeekBar

class BrightnessSlider(context: Context, private val changed: (Int) -> Unit) : LinearLayout(context) {
    private val slider = SeekBar(context).apply {
        max = 99; isFocusable = false; splitTrack = false
        progressTintList = ColorStateList.valueOf(0xffe3edff.toInt())
        progressBackgroundTintList = ColorStateList.valueOf(0x55ffffff)
        thumbTintList = ColorStateList.valueOf(Color.WHITE)
        contentDescription = "灯带亮度"
    }
    private val value = designText(context, "", 14f).apply { gravity = Gravity.CENTER_VERTICAL or Gravity.END }
    init {
        orientation = HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
        addView(slider, LayoutParams(0, -1, 1f)); addView(value, LayoutParams(44, -1))
        slider.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(bar: SeekBar, progress: Int, fromUser: Boolean) {
                if (fromUser) { value.text = "${progress + 1}%"; changed(progress + 1) }
            }
            override fun onStartTrackingTouch(bar: SeekBar) { }
            override fun onStopTrackingTouch(bar: SeekBar) { }
        })
    }
    fun applyUnit(unit: Float) {
        value.layoutParams = LayoutParams((44 * unit).toInt(), -1)
        slider.thumb = RoundKnob(18 * unit)
        slider.setPadding((7 * unit).toInt(), 0, (7 * unit).toInt(), 0)
    }
    fun show(brightness: Int) {
        if (!slider.isPressed && slider.progress != brightness - 1) slider.progress = brightness - 1
        value.text = "$brightness%"
    }
}
