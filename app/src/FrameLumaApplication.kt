package frameluma.app

import android.app.Application
import frameluma.runtime.AppCore

class FrameLumaApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        AppCore.initialize(this)
    }
}
