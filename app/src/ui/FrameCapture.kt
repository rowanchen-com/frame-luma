package frameluma.app.ui

import android.os.SystemClock
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.drawscope.draw
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntSize
import frameluma.core.FramePacer
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext

/** A bounded copy policy for our own UI, independent of any display backend or lamp length. */
internal object UiCapture {
    const val WIDTH = 192
    const val HEIGHT = 108
    val PERIOD_MS = FramePacer.DEFAULT_PERIOD_MS

    /** Navigation reduces copy frequency but never suspends fresh colour delivery. */
    private const val INPUT_IDLE_MS = 320L
    const val NAVIGATION_PERIOD_MS = 250

    fun periodMs(inputIdleMs: Long): Int =
        if (inputIdleMs in 0 until INPUT_IDLE_MS) NAVIGATION_PERIOD_MS else PERIOD_MS

    /** Conservative rectangle guard: a sample covered by any control must use real UI capture. */
    fun backdropIsVisible(width: Int, height: Int, points: Array<Array<IntArray>>, overlay: Boolean): Boolean {
        if (overlay || width <= 0 || height <= 0 || points.size !in 1..64) return false
        val workspace = WorkspaceLayout.fit(width, height)
        val margin = 4 * workspace.scale
        val left = (width - workspace.width) / 2f - margin
        val right = (width + workspace.width) / 2f + margin
        val top = (height - workspace.height) / 2f - margin
        val bottom = (height + workspace.height) / 2f + margin
        return points.all { region ->
            region.isNotEmpty() && region.all { point ->
                if (point.size != 2 || point[0] !in 0 until WIDTH || point[1] !in 0 until HEIGHT) false
                else {
                    val x = (point[0] + .5f) * width / WIDTH
                    val y = (point[1] + .5f) * height / HEIGHT
                    x < left || x > right || y < top || y > bottom
                }
            }
        }
    }
}

/** Use shared background colours only when every requested sample is outside the controls. */
@Composable
internal fun Modifier.capturedScreen(
    session: AppSession,
    state: ScreenState,
    active: Boolean,
    scene: BackdropScene,
    viewport: IntSize,
    overlay: Boolean,
): Modifier {
    val palette = scene.palette
    val points = remember(state.layout, state.edgeDepth) {
        if (state.edgeDepth > 0)
            state.layout?.profile()?.points(UiCapture.WIDTH, UiCapture.HEIGHT, state.edgeDepth)
        else null
    }
    val key = remember(state.layout) { state.layout?.json()?.toString().orEmpty() }
    if (!active || !state.enabled || palette == null || points == null) return this
    val direct = remember(viewport, points, overlay) {
        UiCapture.backdropIsVisible(viewport.width, viewport.height, points, overlay)
    }
    val samples = remember(palette, points, direct) { if (direct) palette.prepare(points) else null }
    if (samples == null) return gpuCapturedScreen(session, points, key, scene, palette)
    LaunchedEffect(samples, key) {
        // The composition dispatcher has no timer of its own; Handler delays wake only this thread.
        withContext(Dispatchers.Main) {
            while (isActive) {
                val started = SystemClock.elapsedRealtime()
                session.publish(samples.colorsAt(scene.sampleTimeMs()), key, gpuCopy = false)
                delay((UiCapture.periodMs(started - session.lastInputAt) -
                    (SystemClock.elapsedRealtime() - started)).coerceAtLeast(1))
            }
        }
    }
    return this
}

/** Fallback for overlays and geometries whose sample points intersect the visible controls. */
@Composable
private fun Modifier.gpuCapturedScreen(
    session: AppSession,
    points: Array<Array<IntArray>>,
    key: String,
    scene: BackdropScene,
    palette: BackdropPalette,
): Modifier {
    val native = rememberGraphicsLayer()
    val sample = rememberGraphicsLayer()
    val density = LocalDensity.current
    val recordDensity =
        remember(density.density, density.fontScale) { Density(density.density, density.fontScale) }
    val direction = LocalLayoutDirection.current
    val requested = remember { AtomicBoolean(false) }
    val captured = remember { AtomicBoolean(false) }
    val tick = remember { mutableIntStateOf(0) }
    val pixels = remember { IntArray(UiCapture.WIDTH * UiCapture.HEIGHT) }
    LaunchedEffect(points, key) {
        requested.set(false)
        while (isActive) {
            val started = SystemClock.elapsedRealtime()
            val period = UiCapture.periodMs(started - session.lastInputAt)
            try {
                captured.set(false)
                requested.set(true)
                // A draw state read forces an invalidation even when the tree is idle.
                tick.intValue++
                // One frame schedules the layer record, the next one runs after the draw.
                withFrameNanos { }
                withFrameNanos { }
                if (captured.getAndSet(false)) {
                    try {
                        val image = sample.toImageBitmap()
                        try {
                            image.readPixels(pixels, 0, 0, UiCapture.WIDTH, UiCapture.HEIGHT)
                            session.publish(
                                FrameColors.sample(
                                    pixels,
                                    UiCapture.WIDTH,
                                    UiCapture.HEIGHT,
                                    points,
                                ),
                                key,
                                gpuCopy = true,
                            )
                        } finally {
                            image.asAndroidBitmap().recycle()
                        }
                    } catch (error: Exception) {
                        if (error is kotlinx.coroutines.CancellationException) throw error
                        session.captureFailed(error)
                    }
                }
            } finally {
                requested.set(false)
            }
            delay(
                (period - (SystemClock.elapsedRealtime() - started)).coerceAtLeast(1)
            )
        }
    }
    return drawWithContent {
        tick.intValue
        // Normal frames draw straight to the screen; only capture frames use offscreen layers.
        if (!requested.get()) {
            drawContent()
            return@drawWithContent
        }
        val sourceSize = size
        native.record(
            density = recordDensity,
            layoutDirection = direction,
            size = IntSize(sourceSize.width.toInt(), sourceSize.height.toInt()),
        ) {
            // Retarget the content scope as required by DrawScope.record, but use an
            // immutable density. A mutable DrawScope as Density recurses in nested layers.
            val recording = drawContext
            this@drawWithContent.draw(
                recordDensity,
                direction,
                recording.canvas,
                recording.size,
                recording.graphicsLayer,
            ) {
                this@drawWithContent.drawContent()
            }
        }
        drawLayer(native)
        sample.record(
            density = recordDensity,
            layoutDirection = direction,
            size = IntSize(UiCapture.WIDTH, UiCapture.HEIGHT),
        ) {
            // Media surfaces are outside the Compose capture. Reconstruct only the known
            // background at its playhead, then composite the actual controls and modal scrim.
            drawBackdrop(palette, scene.sampleTimeMs())
            scale(
                UiCapture.WIDTH / sourceSize.width,
                UiCapture.HEIGHT / sourceSize.height,
                Offset.Zero,
            ) {
                drawLayer(native)
            }
        }
        captured.set(true)
    }
}
