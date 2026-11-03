package frameluma.app.ui

import android.os.Build
import android.view.View
import android.view.Window
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import org.json.JSONObject

/** One native window; Compose measures controls at the system-provided size and density. */
class RenderWindow(private val activity: ComponentActivity, private val session: AppSession) {
    private var composition: ComposeView? = null

    fun install() {
        composition = ComposeView(activity).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent { FrameLumaApp(session) }
        }
        activity.setContentView(composition)
        hideSystemBars(activity.window)
    }

    fun close() {
        composition?.disposeComposition()
        composition = null
    }

    fun diagnostic() = JSONObject()
        .put("backend", "window")
        .put("ui_backend", "compose_tv")
        .put("width", composition?.width ?: 0)
        .put("height", composition?.height ?: 0)
        .put("display_id", composition?.display?.displayId ?: 0)
        .put("density_dpi", composition?.resources?.displayMetrics?.densityDpi ?: 0)

    companion object {
        @Suppress("DEPRECATION")
        private fun hideSystemBars(window: Window) {
            if (Build.VERSION.SDK_INT >= 30) {
                window.setDecorFitsSystemWindows(false)
                window.insetsController?.apply {
                    hide(WindowInsets.Type.systemBars())
                    systemBarsBehavior = WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                }
            } else {
                window.addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN)
                window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or
                    View.SYSTEM_UI_FLAG_FULLSCREEN or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
                    View.SYSTEM_UI_FLAG_LAYOUT_STABLE or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
                    View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
            }
        }
    }
}
