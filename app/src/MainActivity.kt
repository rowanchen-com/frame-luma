package frameluma.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.lifecycle.lifecycleScope
import frameluma.app.ui.AppSession
import frameluma.app.ui.RenderWindow

class MainActivity : ComponentActivity() {
    private lateinit var session: AppSession
    private lateinit var rendering: RenderWindow

    override fun dispatchKeyEvent(event: android.view.KeyEvent): Boolean {
        return frameluma.runtime.Recovery.key(this, event) || super.dispatchKeyEvent(event)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        session = AppSession(this, lifecycleScope)
        rendering = RenderWindow(this, session)
        rendering.install()
        onBackPressedDispatcher.addCallback(
            this,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    if (!session.back()) finish()
                }
            },
        )
    }

    override fun onResume() {
        super.onResume()
        session.resume()
    }

    override fun onPause() {
        session.pause()
        super.onPause()
    }

    override fun onDestroy() {
        rendering.close()
        super.onDestroy()
    }

    override fun dump(
        prefix: String,
        fd: java.io.FileDescriptor?,
        writer: java.io.PrintWriter,
        args: Array<out String>?,
    ) {
        super.dump(prefix, fd, writer, args)
        if (::session.isInitialized)
            writer.println(
                prefix +
                    "FrameLumaRemote " +
                    session
                        .diagnostics()
                        .put("window_focus", hasWindowFocus())
                        .put("render", rendering.diagnostic())
            )
    }
}
