package frameluma.app.ui

import android.os.SystemClock
import android.util.Log
import androidx.compose.foundation.AndroidExternalSurface
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.IntSize
import java.util.concurrent.Executors
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext

/** EGL owns one thread; textures upload once, without per-frame CPU painting or snapshots. */
@Composable
internal fun BackdropSurface(scene: BackdropScene, palette: BackdropPalette, bufferSize: IntSize,
    frameIntervalMs: Long, onReady: () -> Unit, onFailure: () -> Unit) {
    val ready by rememberUpdatedState(onReady)
    val failure by rememberUpdatedState(onFailure)
    AndroidExternalSurface(Modifier.fillMaxSize(), surfaceSize = bufferSize) {
        onSurface { surface, width, height ->
            val guard = Any()
            var alive = true
            var dimensions = IntSize(width, height)
            surface.onChanged { w, h -> synchronized(guard) { dimensions = IntSize(w, h) } }
            surface.onDestroyed {
                synchronized(guard) { alive = false }
                scene.presentedMs = -1
            }
            // A scheduled executor times frame delays on this same thread, without a timer-thread hop.
            val dispatcher = Executors.newSingleThreadScheduledExecutor { task ->
                Thread(task, "FrameLumaBackdrop")
            }.asCoroutineDispatcher()
            try {
                withContext(dispatcher) {
                    BackdropGlRenderer.open(surface, palette).use { renderer ->
                        var announced = false
                        while (isActive) {
                            val start = SystemClock.elapsedRealtime()
                            val drawn = synchronized(guard) {
                                if (!alive || !surface.isValid) false
                                else {
                                    val time = scene.elapsedMs
                                    renderer.draw(time, dimensions)
                                    scene.presentedMs = time
                                    true
                                }
                            }
                            if (!drawn) break
                            if (!announced) {
                                announced = true
                                Log.i("FrameLumaUi", "GPU background ${dimensions.width}x${dimensions.height}")
                                ready()
                            }
                            delay((frameIntervalMs - (SystemClock.elapsedRealtime() - start)).coerceAtLeast(1))
                        }
                    }
                }
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                Log.w("FrameLumaUi", "GPU background unavailable; using cached layers", error)
                failure()
            } finally {
                dispatcher.close()
                scene.presentedMs = -1
            }
        }
    }
}
