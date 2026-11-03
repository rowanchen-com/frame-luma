package frameluma.app.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import frameluma.app.R
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import frameluma.runtime.LayoutOptions
import kotlin.math.atan2
import kotlin.math.min

internal data class PreviewEdge(val zone: Int, val path: Path, val center: Offset)

internal data class PreviewGeometry(val monitor: Rect, val path: Path, val edges: List<PreviewEdge>)

/** Geometry and soft bitmap depend on the layout and size, not each new colour frame. */
internal class PreviewCache {
    private var size = Size.Unspecified
    private var layoutKey = ""
    private var density = 0f
    private var tinted: IntArray? = null
    var shape: PreviewGeometry? = null
        private set
    var glow: PreviewGlow? = null
        private set

    fun prepare(nextSize: Size, nextLayout: LayoutOptions?, nextKey: String, nextDensity: Float, compact: Boolean) {
        if (size == nextSize && layoutKey == nextKey && density == nextDensity) return
        size = nextSize;layoutKey = nextKey;density = nextDensity
        shape = nextLayout?.let { previewGeometry(nextSize, it) }
        glow = if (compact) null else shape?.let {
            PreviewGlow(nextSize, it.path, 16f * nextDensity, 8f * nextDensity)
        }
        tinted = null
    }

    /** Recolours the reusable soft mask only when the colour content differs from the last tint. */
    fun tint(colors: IntArray) {
        val soft = glow ?: return
        val geometry = shape ?: return
        if (tinted?.contentEquals(colors) == true) return
        val brush = perimeterBrush(geometry, colors)
        if (brush is ShaderBrush) soft.update(brush.createShader(size))
        tinted = colors.copyOf()
    }
}

private val NO_COLORS = IntArray(0)

/** For previews that show placement only, such as calibration. */
internal val NoPreviewColors: () -> IntArray = { NO_COLORS }

/** Compose paths share one rounded perimeter; segment lengths follow the actual LED allocation. */
private fun previewGeometry(size: Size, layout: LayoutOptions): PreviewGeometry {
    val width = min(size.width * .80f, size.height * .70f * 16 / 9)
    val height = width * 9 / 16
    val box =
        Rect(
            (size.width - width) / 2,
            (size.height - height) / 2,
            (size.width + width) / 2,
            (size.height + height) / 2,
        )
    val outline = layout.profile().outline().let { if (layout.reverse) it.reversedArray() else it }
    fun point(index: Int) =
        Offset(box.left + outline[index][0] * width, box.top + outline[index][1] * height)
    val path = Path()
    val first = point(0)
    path.moveTo(first.x, first.y)
    for (index in 1 until outline.lastIndex) {
        val before = point(index - 1)
        val corner = point(index)
        val after = point(index + 1)
        val incoming = corner - before
        val outgoing = after - corner
        val a = incoming.getDistance()
        val b = outgoing.getDistance()
        if (a < .01f || b < .01f) path.lineTo(corner.x, corner.y)
        else {
            val radius = min(width * .018f, min(a, b) / 2)
            val start = corner - incoming * (radius / a)
            val end = corner + outgoing * (radius / b)
            path.lineTo(start.x, start.y)
            path.quadraticTo(corner.x, corner.y, end.x, end.y)
        }
    }
    val last = point(outline.lastIndex)
    path.lineTo(last.x, last.y)
    if ((last - first).getDistance() < .01f) path.close()
    val measure = PathMeasure().apply { setPath(path, false) }
    val units = layout.geometry()
    val edges = buildList {
        var start = 0
        while (start < units.unitCount()) {
            val zone = units.zoneAt(start)
            var end = start + 1
            while (end < units.unitCount() && units.zoneAt(end) == zone) end++
            val from = start / units.unitCount().toFloat() * measure.length
            val to = end / units.unitCount().toFloat() * measure.length
            add(
                PreviewEdge(
                    zone,
                    Path().also { measure.getSegment(from, to, it, true) },
                    measure.getPosition((from + to) / 2),
                )
            )
            start = end
        }
    }
    return PreviewGeometry(box, path, edges)
}

/**
 * One continuous angular color field keeps corners and zone transitions free of overlapping halos.
 */
private fun perimeterBrush(shape: PreviewGeometry, colors: IntArray): Brush {
    val center = shape.monitor.center
    val stops =
        shape.edges
            .filter { it.zone in colors.indices }
            .map {
                val delta = it.center - center
                val angle = ((atan2(delta.y, delta.x) / (2 * Math.PI)).toFloat() + 1) % 1f
                angle to Color(colors[it.zone] or (0xff shl 24))
            }
            .sortedBy { it.first }
    if (stops.isEmpty())
        return Brush.sweepGradient(listOf(Color.Transparent, Color.Transparent), center)
    val first = stops.first()
    val last = stops.last()
    val seam =
        androidx.compose.ui.graphics.lerp(
            last.second,
            first.second,
            (1 - last.first) / (1 + first.first - last.first),
        )
    return Brush.sweepGradient(
        *(listOf(0f to seam) + stops + (1f to seam)).toTypedArray(),
        center = center,
    )
}

/**
 * [colors] is invoked only while drawing. A new frame therefore repaints this layer alone: no
 * composition, no layout, and the cached geometry and blurred mask stay as they are.
 */
@Composable
internal fun LightPreview(
    layout: LayoutOptions?,
    colors: () -> IntArray,
    enabled: Boolean,
    following: Boolean,
    brightness: Int,
    message: String,
    modifier: Modifier = Modifier,
    highlight: Int = -1,
    compact: Boolean = false,
) {
    val cache = remember(compact) { PreviewCache() }
    val layoutKey = remember(layout) { layout?.json()?.toString().orEmpty() }
    val gain =
        animateFloatAsState(
            if (enabled && following) brightness / 100f else 0f,
            tween(150),
            label = "preview-gain",
        )
    val surface = MaterialTheme.colorScheme.surface
    val line = MaterialTheme.colorScheme.border
    val primary = MaterialTheme.colorScheme.primary
    Box(
        modifier.graphicsLayer().drawWithCache {
            // Only size, density and layout reach this block; colour reads stay in the draw lambda.
            cache.prepare(size, layout, layoutKey, density, compact)
            val shape = cache.shape
            val soft = cache.glow
            val edgeStroke = Stroke(2.4.dp.toPx(), cap = StrokeCap.Butt, join = StrokeJoin.Round)
            onDrawBehind {
                val geometry = shape ?: return@onDrawBehind
                val box = geometry.monitor
                val current = colors()
                if (soft != null && gain.value > 0) {
                    cache.tint(current)
                    drawImage(soft.image,
                        dstSize = IntSize(size.width.toInt(), size.height.toInt()),
                        alpha = gain.value * .55f,
                        filterQuality = FilterQuality.Low)
                }
                drawRoundRect(
                    LumaColors.stand,
                    Offset(box.center.x - 4.dp.toPx(), box.bottom),
                    Size(8.dp.toPx(), 16.dp.toPx()),
                    CornerRadius(2.dp.toPx()),
                )
                drawRoundRect(
                    LumaColors.stand,
                    Offset(box.center.x - 30.dp.toPx(), box.bottom + 13.dp.toPx()),
                    Size(60.dp.toPx(), 4.dp.toPx()),
                    CornerRadius(2.dp.toPx()),
                )
                drawRoundRect(LumaColors.monitor, box.topLeft, box.size, CornerRadius(7.dp.toPx()))
                val inset = if (compact) 3.dp.toPx() else 5.dp.toPx()
                drawRoundRect(
                    surface,
                    box.topLeft + Offset(inset, inset),
                    Size(box.width - inset * 2, box.height - inset * 2),
                    CornerRadius(3.dp.toPx()),
                )
                geometry.edges.forEach { edge ->
                    val color =
                        when {
                            compact && edge.zone == highlight -> primary
                            edge.zone in current.indices && gain.value > 0 ->
                                Color(current[edge.zone] or (0xff shl 24)).copy(alpha = gain.value)
                            else -> line
                        }
                    drawPath(edge.path, color, style = edgeStroke)
                }
            }
        },
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            val context = LocalContext.current
            val profile = layout?.profile()
            Text(
                if (compact && profile != null && highlight in 0 until layout.counts.size)
                    localizedZoneLabels(context, profile)[highlight]
                else message,
                style =
                    if (compact) MaterialTheme.typography.bodySmall
                    else MaterialTheme.typography.titleMedium,
            )
            if (!compact)
                Text(
                    profile?.let { localizedLayoutName(context, it) }.orEmpty(),
                    style = MaterialTheme.typography.bodySmall,
                )
        }
    }
}
