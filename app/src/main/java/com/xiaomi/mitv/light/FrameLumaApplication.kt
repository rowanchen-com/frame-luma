package com.xiaomi.mitv.light
import android.app.Application
import com.xiaomi.mitv.light.tcl.AppCore

class FrameLumaApplication : Application() {
    override fun onCreate() { super.onCreate(); AppCore.initialize(this) }
}
