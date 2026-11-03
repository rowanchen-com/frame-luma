package frameluma.app.ui

import android.app.ActivityManager
import android.content.Context
import frameluma.app.R
import java.lang.ref.SoftReference
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** UI resource budget only. No model matching, capture policy or light-output timing. */
internal enum class UiPerformance(
    val previewIntervalMs: Long,
    val fallbackFrameMs: Long,
    val maxBackgroundWidth: Int,
    val maxBackgroundHeight: Int,
) {
    STANDARD(300, 33, 1920, 1080),
    LIGHT(500, 50, 1280, 720);

    fun video(embedded: Boolean): BackdropVideo = when {
        this == LIGHT -> BackdropVideo.LIGHT
        embedded -> BackdropVideo.COMPOSITED
        else -> BackdropVideo.OVERLAY
    }

    companion object {
        @Volatile private var cached: UiPerformance? = null

        /** Capacity is stable for this process; run Android queries off the UI thread. */
        fun read(context: Context): UiPerformance = cached ?: synchronized(this) {
            cached ?: run {
                runCatching {
                    val manager = context.getSystemService(ActivityManager::class.java)
                    val info = ActivityManager.MemoryInfo().also(manager::getMemoryInfo)
                    classify(manager.isLowRamDevice, info.totalMem, Runtime.getRuntime().availableProcessors())
                }.getOrDefault(LIGHT).also { cached = it }
            }
        }

        fun classify(lowRam: Boolean, totalBytes: Long, cores: Int): UiPerformance =
            if (lowRam || totalBytes <= 2L * 1024 * 1024 * 1024 || cores <= 2) LIGHT
            else STANDARD
    }
}

/** Same eight-second animation and colour fields; only decoded size/frame count differ. */
internal enum class BackdropVideo(val resource: Int, val width: Int, val height: Int, val fps: Int) {
    OVERLAY(R.raw.background_loop, 1920, 1080, 30),
    COMPOSITED(R.raw.background_loop_composited, 1920, 1080, 24),
    LIGHT(R.raw.background_loop_light, 1280, 720, 20),
}

/** One immutable palette, shared across page recreation; GC may reclaim it under pressure. */
internal object BackdropPalettes {
    private val lock = Mutex()
    private var cached = SoftReference<BackdropPalette>(null)

    suspend fun load(): BackdropPalette = lock.withLock {
        cached.get() ?: withContext(Dispatchers.Default) {
            BackdropPalette(Array(BackdropMotion.PHASE_COUNT) { colorFieldFrame(it) })
        }.also { cached = SoftReference(it) }
    }
}
