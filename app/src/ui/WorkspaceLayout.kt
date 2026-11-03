package frameluma.app.ui

import kotlin.math.min
import kotlin.math.roundToInt

/** Native Compose density measured once from the window; text is never bitmap-scaled. */
class WorkspaceLayout private constructor(@JvmField val scale: Float) {
    @JvmField val width = (WIDTH * scale).roundToInt()
    @JvmField val height = (HEIGHT * scale).roundToInt()

    companion object {
        const val WIDTH = 1040
        const val HEIGHT = 620
        const val CORNER_RADIUS = 22

        @JvmStatic
        fun fit(width: Int, height: Int): WorkspaceLayout {
            require(width > 0 && height > 0) { "Empty window" }
            val fraction =
                when {
                    height < 900 -> .76f
                    height < 1260 -> .58f
                    height < 1800 -> .56f
                    else -> .54f
                }
            return WorkspaceLayout(min(width * fraction / WIDTH, height * .82f / HEIGHT))
        }
    }
}
