package frameluma.app.ui

import android.graphics.Bitmap
import androidx.compose.ui.graphics.asImageBitmap
import kotlin.math.floor
import kotlin.math.roundToInt

/** Drawing and sampling share the same opaque source fields and animation phase. */
internal class BackdropPalette(private val frames: Array<Bitmap>) {
    val images = Array(frames.size) { frames[it].asImageBitmap() }

    fun texture(index: Int): Bitmap = frames[index]

    init {
        require(frames.size == BackdropMotion.PHASE_COUNT)
    }

    /** Resolve scaled coordinates once per layout, never read bitmap pixels on each animation tick. */
    fun prepare(points: Array<Array<IntArray>>): BackdropSamples {
        val phases = Array(frames.size) { phase ->
            val bitmap = frames[phase]
            IntArray(points.size) { zone ->
                var red = 0
                var green = 0
                var blue = 0
                for (point in points[zone]) {
                    val rgb = scaledPixel(bitmap, point[0], point[1])
                    red += rgb ushr 16 and 255
                    green += rgb ushr 8 and 255
                    blue += rgb and 255
                }
                ((red / points[zone].size) shl 16) or
                    ((green / points[zone].size) shl 8) or (blue / points[zone].size)
            }
        }
        return BackdropSamples(phases)
    }

    private fun scaledPixel(bitmap: Bitmap, x: Int, y: Int): Int {
        val sx = ((x + .5f) * bitmap.width / UiCapture.WIDTH - .5f).coerceIn(0f, bitmap.width - 1f)
        val sy = ((y + .5f) * bitmap.height / UiCapture.HEIGHT - .5f).coerceIn(0f, bitmap.height - 1f)
        val left = floor(sx).toInt()
        val top = floor(sy).toInt()
        val right = (left + 1).coerceAtMost(bitmap.width - 1)
        val bottom = (top + 1).coerceAtMost(bitmap.height - 1)
        val a = bitmap.getPixel(left, top)
        val b = bitmap.getPixel(right, top)
        val c = bitmap.getPixel(left, bottom)
        val d = bitmap.getPixel(right, bottom)
        val fx = sx - left
        val fy = sy - top
        fun channel(shift: Int): Int {
            val upper = (a ushr shift and 255) * (1 - fx) + (b ushr shift and 255) * fx
            val lower = (c ushr shift and 255) * (1 - fx) + (d ushr shift and 255) * fx
            return (upper * (1 - fy) + lower * fy).roundToInt().coerceIn(0, 255)
        }
        return (channel(16) shl 16) or (channel(8) shl 8) or channel(0)
    }
}

/** Only nine small RGB interpolations per publication for the current nine-zone layout. */
internal class BackdropSamples(private val phases: Array<IntArray>) {
    fun colorsAt(elapsedMs: Long): IntArray {
        val phase = BackdropMotion.phase(elapsedMs)
        val first = phase.toInt()
        val a = phases[first]
        val b = phases[(first + 1) % phases.size]
        val blend = phase - first
        return IntArray(a.size) { zone ->
            fun channel(shift: Int): Int {
                val from = a[zone] ushr shift and 255
                val to = b[zone] ushr shift and 255
                return (from + (to - from) * blend).roundToInt().coerceIn(0, 255)
            }
            (channel(16) shl 16) or (channel(8) shl 8) or channel(0)
        }
    }
}
