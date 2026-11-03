package frameluma.app.ui

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Bounded RGB reduction from the actual, small GPU copy. No text or full-size bitmap rendering. */
object FrameColors {
    @JvmStatic
    fun sample(
        pixels: IntArray,
        width: Int,
        height: Int,
        points: Array<Array<IntArray>>,
    ): IntArray {
        require(width > 0 && height > 0 && pixels.size == width * height && points.size in 1..64) {
            "Invalid capture buffer"
        }
        return IntArray(points.size) { zone ->
            val region = points[zone]
            require(region.isNotEmpty()) { "Empty sample region" }
            var red = 0
            var green = 0
            var blue = 0
            for (point in region) {
                require(
                    point.size == 2 && point[0] in 0 until width && point[1] in 0 until height
                ) {
                    "Sample outside capture"
                }
                val pixel = pixels[point[1] * width + point[0]]
                red += pixel ushr 16 and 255
                green += pixel ushr 8 and 255
                blue += pixel and 255
            }
            ((red / region.size) shl 16) or ((green / region.size) shl 8) or (blue / region.size)
        }
    }
}

/**
 * Every real UI sample refreshes the light mailbox, so a static picture never looks stale.
 * Only a change in content reaches the preview, as a private copy the producer cannot alter.
 */
internal class UiFrameOutlet(
    private val previewIntervalMs: () -> Long = { PREVIEW_INTERVAL_MS },
    private val mailbox: (IntArray, String) -> Unit,
) {
    private val latest = MutableStateFlow(IntArray(0))
    val colors: StateFlow<IntArray> = latest.asStateFlow()
    private var lastPreviewAt = -1L
    private var lastLayout: String? = null

    /** Returns true when the preview colours changed. */
    fun publish(colors: IntArray, layout: String, now: Long = android.os.SystemClock.elapsedRealtime()): Boolean {
        mailbox(colors, layout)
        if (colors.contentEquals(latest.value)) return false
        // Only the small on-screen preview is paced. Every real capture reaches the lamp above.
        if (layout == lastLayout && lastPreviewAt >= 0 && now - lastPreviewAt in 0 until previewIntervalMs()) return false
        latest.value = colors.copyOf()
        lastPreviewAt = now
        lastLayout = layout
        return true
    }

    companion object { const val PREVIEW_INTERVAL_MS = 300L }
}
