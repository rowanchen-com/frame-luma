package com.xiaomi.mitv.light
import android.app.Application
import android.app.Activity
import android.os.Bundle
import com.xiaomi.mitv.light.tcl.AppCore
import com.xiaomi.mitv.light.tcl.Client
import com.xiaomi.mitv.light.ui.UiFramePublisher

class FrameLumaApplication : Application() {
    private var visible: UiFramePublisher? = null
    override fun onCreate() {
        super.onCreate(); AppCore.initialize(this)
        registerActivityLifecycleCallbacks(object : ActivityLifecycleCallbacks {
            override fun onActivityResumed(activity: Activity) {
                visible?.close(); AppCore.setUiVisible(true)
                visible = UiFramePublisher(activity).also { it.start() }; Client.refresh(activity)
            }
            override fun onActivityPaused(activity: Activity) {
                if (visible?.activity === activity) {
                    visible?.close(); visible = null; AppCore.setUiVisible(false); Client.refresh(activity)
                }
            }
            override fun onActivityCreated(activity: Activity, state: Bundle?) { }
            override fun onActivityStarted(activity: Activity) { }
            override fun onActivityStopped(activity: Activity) { }
            override fun onActivitySaveInstanceState(activity: Activity, state: Bundle) { }
            override fun onActivityDestroyed(activity: Activity) { }
        })
    }
}
