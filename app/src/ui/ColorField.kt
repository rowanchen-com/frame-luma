package frameluma.app.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import android.os.SystemClock
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import kotlin.math.min
import kotlin.math.roundToInt
import kotlinx.coroutines.isActive
import kotlinx.coroutines.delay
import frameluma.app.R
import frameluma.core.UiRenderingProfile

/** Fixed perimeter anchors; colour advances clockwise without rotating the screen image. */
internal object BackdropMotion {
    const val PHASE_COUNT = 8
    const val TRANSITION_MS = 1000L
    const val CYCLE_MS = PHASE_COUNT * TRANSITION_MS
    const val WIDTH = 480
    const val HEIGHT = 270

    fun phase(elapsedMs: Long): Float =
        (elapsedMs.coerceAtLeast(0) % CYCLE_MS) / TRANSITION_MS.toFloat()

    /** Keep the old pair's second image visible until composition commits the next pair. */
    fun pairBlend(phase: Float, first: Int): Float =
        ((phase - first + PHASE_COUNT) % PHASE_COUNT).coerceIn(0f, 1f)

    /** Window resolution through Full HD; bound 4K fill rate without an 8x enlargement. */
    fun surfaceSize(viewport: IntSize, performance: UiPerformance = UiPerformance.STANDARD): IntSize {
        val scale = min(1f, min(performance.maxBackgroundWidth.toFloat() / viewport.width.coerceAtLeast(1),
            performance.maxBackgroundHeight.toFloat() / viewport.height.coerceAtLeast(1)))
        return IntSize((viewport.width * scale).roundToInt().coerceAtLeast(1),
            (viewport.height * scale).roundToInt().coerceAtLeast(1))
    }
}

/** The video plane can appear after its first-frame callback on TV firmware. */
internal object BackdropStartup {
    const val VIDEO_SETTLE_MS = 500L
    const val FADE_MS = 120
}

/** A recreated surface always begins opaque, even if the previous one had finished fading. */
@Composable
internal fun rememberBackdropCover(active: Boolean, ready: Boolean, media: Boolean, generation: Int): State<Float> =
    key(active, media, generation) {
        var settled by remember { mutableStateOf(false) }
        LaunchedEffect(ready) {
            settled = false
            if (ready) {
                if (media) {
                    // Use the same visible-frame clock as the fade. No work remains after handoff.
                    val startedAt = withFrameNanos { it }
                    do { val now = withFrameNanos { it } }
                    while (now - startedAt < BackdropStartup.VIDEO_SETTLE_MS * 1_000_000)
                }
                withFrameNanos { }
                withFrameNanos { }
                settled = true
            }
        }
        val animated = animateFloatAsState(if (ready && settled) 0f else 1f,
            tween(BackdropStartup.FADE_MS), label = "background-handoff")
        remember(ready, settled, animated) {
            derivedStateOf { if (!ready || !settled) 1f else animated.value }
        }
    }

/** Monotonic time without Compose snapshot writes on every background frame. */
internal class BackdropClock {
    private var offset = 0L
    private var startedAt = -1L

    @Synchronized fun elapsedMs(now: Long): Long =
        (offset + if (startedAt >= 0) (now - startedAt).coerceAtLeast(0) else 0) % BackdropMotion.CYCLE_MS

    @Synchronized fun running(active: Boolean, now: Long) {
        if (active == (startedAt >= 0)) return
        offset = elapsedMs(now)
        startedAt = if (active) now else -1L
    }

    @Synchronized fun align(positionMs: Long, now: Long) {
        offset = positionMs.coerceAtLeast(0) % BackdropMotion.CYCLE_MS
        if (startedAt >= 0) startedAt = now
    }
}

internal class BackdropScene {
    var palette by mutableStateOf<BackdropPalette?>(null)
    val clock = BackdropClock()
    @Volatile var presentedMs: Long = -1
    var mediaPosition: (() -> Long)? = null
    fun sampleTimeMs(): Long = mediaPosition?.invoke() ?: presentedMs.takeIf { it >= 0 } ?: elapsedMs
    val elapsedMs: Long get() = clock.elapsedMs(SystemClock.elapsedRealtime())
}

/** The painter and UI sampler share the same cached fields and displayed phase. */
@Composable
internal fun rememberBackdrop(active: Boolean): BackdropScene {
    val scene = remember { BackdropScene() }
    LaunchedEffect(scene) {
        scene.palette = BackdropPalettes.load()
    }
    DisposableEffect(active, scene) {
        scene.clock.running(active, SystemClock.elapsedRealtime())
        onDispose { scene.clock.running(false, SystemClock.elapsedRealtime()) }
    }
    return scene
}

/** Playback is page-owned; opening a modal must not create or destroy the video surface. */
@Composable
internal fun ColorField(scene: BackdropScene, active: Boolean, viewport: IntSize, profile: UiRenderingProfile?, performance: UiPerformance, onRenderer: (String) -> Unit) {
    val palette = scene.palette
    if (palette == null || profile == null) {
        Image(painterResource(R.drawable.backdrop_poster), null, Modifier.fillMaxSize(), contentScale = ContentScale.FillBounds)
        return
    }
    var failed by remember { mutableStateOf(false) }
    var mediaFailed by remember { mutableStateOf(false) }
    val independent = active && !failed
    val media = independent && !mediaFailed
    val embedded = profile.videoSurface == UiRenderingProfile.VideoSurface.EMBEDDED
    val video = performance.video(embedded)
    var ready by remember(independent, media) { mutableStateOf(false) }
    var surfaceEpoch by remember { mutableIntStateOf(0) }
    SideEffect { onRenderer(if (!independent || !ready) "cached_layers" else if (media) {
        if (embedded) "media_embedded_${video.fps}" else "media_surface_${video.fps}"
    } else "gpu_surface") }
    LaunchedEffect(independent, media, surfaceEpoch) {
        if (independent) {
            delay(if (media) 5000 else 2000)
            if (!ready) { if (media) mediaFailed = true else failed = true }
        }
    }
    val cover = rememberBackdropCover(independent, ready, media, surfaceEpoch)
    val bufferSize = remember(viewport, performance) { BackdropMotion.surfaceSize(viewport, performance) }
    if (media) key(video, embedded) { BackdropMedia(scene, embedded, video, { ready = false; surfaceEpoch++ },
        { ready = true }, { mediaFailed = true }) }
    else if (independent) BackdropSurface(scene, palette, bufferSize, performance.fallbackFrameMs, { ready = true }, { failed = true })
    if (cover.value > 0f) {
        Box(Modifier.fillMaxSize().graphicsLayer { alpha = cover.value }) {
            CachedColorField(scene, active, performance.fallbackFrameMs * 1_000_000)
        }
    }
}

@Composable
private fun CachedColorField(scene: BackdropScene, active: Boolean, frameIntervalNs: Long) {
    val images = scene.palette?.images ?: return
    val phase = remember(scene) { mutableFloatStateOf(BackdropMotion.phase(scene.sampleTimeMs())) }
    LaunchedEffect(scene, active, frameIntervalNs) {
        if (!active) return@LaunchedEffect
        var publishedAt = 0L
        while (isActive) withFrameNanos { now ->
            if (now - publishedAt >= frameIntervalNs) {
                phase.floatValue = BackdropMotion.phase(scene.sampleTimeMs())
                publishedAt = now
            }
        }
    }
    val first by remember(phase) { derivedStateOf { phase.floatValue.toInt() } }
    BackgroundImage(images[first], Modifier.graphicsLayer())
    BackgroundImage(
        images[(first + 1) % images.size],
        Modifier.graphicsLayer {
            // A single image can modulate alpha directly; no full-window offscreen texture.
            compositingStrategy = CompositingStrategy.ModulateAlpha
            alpha = BackdropMotion.pairBlend(phase.floatValue, first)
        },
    )
}

/** Only used in a small offscreen capture: recreate the video under the real transparent UI. */
internal fun DrawScope.drawBackdrop(palette: BackdropPalette, elapsedMs: Long) {
    val phase = BackdropMotion.phase(elapsedMs)
    val first = phase.toInt()
    val target = IntSize(size.width.toInt(), size.height.toInt())
    val source = IntSize(BackdropMotion.WIDTH, BackdropMotion.HEIGHT)
    drawImage(palette.images[first], IntOffset.Zero, source, IntOffset.Zero, target,
        filterQuality = FilterQuality.Low)
    drawImage(palette.images[(first + 1) % palette.images.size], IntOffset.Zero, source, IntOffset.Zero, target,
        alpha = phase - first, filterQuality = FilterQuality.Low)
}

@Composable
private fun BackgroundImage(image: ImageBitmap, modifier: Modifier) {
    Box(
        modifier.fillMaxSize().drawWithCache {
            val target = IntSize(size.width.toInt(), size.height.toInt())
            val source = IntSize(BackdropMotion.WIDTH, BackdropMotion.HEIGHT)
            onDrawBehind {
                if (target.width > 0 && target.height > 0) {
                    drawImage(
                        image,
                        srcOffset = IntOffset.Zero,
                        srcSize = source,
                        dstOffset = IntOffset.Zero,
                        dstSize = target,
                        filterQuality = FilterQuality.Low,
                    )
                }
            }
        }
    )
}

internal fun colorFieldFrame(phase: Int): Bitmap {
    require(phase in 0 until BackdropMotion.PHASE_COUNT)
    val width = BackdropMotion.WIDTH
    val height = BackdropMotion.HEIGHT
    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    canvas.drawColor(android.graphics.Color.HSVToColor(floatArrayOf(191f, .45f, .75f)))
    val anchors =
        arrayOf(
            0f to 0f,
            .5f to 0f,
            1f to 0f,
            1f to .5f,
            1f to 1f,
            .5f to 1f,
            0f to 1f,
            0f to .5f,
        )
    val radius = min(width, height) * .92f
    anchors.forEachIndexed { index, (fx, fy) ->
        val x = fx * width
        val y = fy * height
        val hue = ((index - phase + BackdropMotion.PHASE_COUNT) % BackdropMotion.PHASE_COUNT) *
            (360f / BackdropMotion.PHASE_COUNT)
        val color = android.graphics.Color.HSVToColor(floatArrayOf(hue, .75f, .96f))
        val paint =
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                shader =
                    RadialGradient(
                        x,
                        y,
                        radius,
                        color,
                        color and 0x00FFFFFF,
                        Shader.TileMode.CLAMP,
                    )
            }
        canvas.drawCircle(x, y, radius, paint)
    }
    // Every pixel includes the opaque base colour; expose that fact to the bitmap blitter.
    bitmap.density = Bitmap.DENSITY_NONE
    bitmap.setHasAlpha(false)
    return bitmap
}
