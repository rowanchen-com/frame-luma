package frameluma.app.ui

import android.graphics.Bitmap
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Shader
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.graphics.asImageBitmap
import kotlin.math.ceil
import kotlin.math.min

/** Blur geometry once, then recolour the reusable mask. Outlines and text stay at native resolution. */
internal class PreviewGlow(size: Size, path: Path, strokeWidth: Float, blurRadius: Float) {
    private val scale = min(1f, min(256f / size.width, 256f / size.height))
    private val bitmap = Bitmap.createBitmap(
        ceil(size.width * scale).toInt().coerceAtLeast(1),
        ceil(size.height * scale).toInt().coerceAtLeast(1),
        Bitmap.Config.ARGB_8888,
    )
    val image: ImageBitmap = bitmap.asImageBitmap()
    private val width = size.width
    private val height = size.height
    private val mask = Bitmap.createBitmap(bitmap.width, bitmap.height, Bitmap.Config.ARGB_8888).also {
        val maskCanvas = Canvas(it).apply { scale(scale, scale) }
        val maskPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            style = Paint.Style.STROKE
            this.strokeWidth = strokeWidth
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
            maskFilter = BlurMaskFilter(blurRadius.coerceAtLeast(.1f), BlurMaskFilter.Blur.NORMAL)
        }
        maskCanvas.drawPath(path.asAndroidPath(), maskPaint)
    }
    private val pixels = Canvas(bitmap)
    private val canvas = Canvas(bitmap).apply { scale(scale, scale) }
    private val copy = Paint().apply {
        xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC)
    }
    private val paint = Paint().apply {
        xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_IN)
    }

    fun update(shader: Shader) {
        pixels.drawBitmap(mask, 0f, 0f, copy)
        paint.shader = shader
        canvas.drawRect(0f, 0f, width, height, paint)
    }
}
