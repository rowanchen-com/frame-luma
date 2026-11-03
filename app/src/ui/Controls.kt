package frameluma.app.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.input.key.*
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Border
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import frameluma.app.R
import kotlin.math.roundToInt

@Composable
internal fun RemoteSurface(
    tag: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tone: ActionTone = ActionTone.FIELD,
    onFocused: () -> Unit = {},
    focusBorder: Boolean = true,
    content: @Composable BoxScope.() -> Unit,
) {
    // A modal owns input; the dashboard's retained cursor is separate from actual keyboard focus.
    val interactive = LocalControlsEnabled.current
    var focused by remember { mutableStateOf(false) }
    val highlighted = LocalHighlightedControl.current?.let { it == tag } ?: focused
    val focusProgress =
        animateFloatAsState(if (highlighted) 1f else 0f, tween(90), label = "focus-arrival")
    val scheme = MaterialTheme.colorScheme
    val idleContainer =
        when (tone) {
            ActionTone.PRIMARY -> scheme.primary
            ActionTone.SECONDARY -> scheme.surfaceVariant
            ActionTone.FIELD -> scheme.surface
            ActionTone.READING -> scheme.background
        }
    val focusContainer =
        when (tone) {
            ActionTone.READING -> scheme.background
            ActionTone.PRIMARY -> scheme.inverseSurface
            ActionTone.SECONDARY -> scheme.surface
            ActionTone.FIELD -> scheme.surfaceVariant
        }
    val idleContent = if (tone == ActionTone.PRIMARY) scheme.onPrimary else scheme.onSurface
    val focusContent =
        when (tone) {
            ActionTone.PRIMARY -> scheme.inverseOnSurface
            ActionTone.SECONDARY -> scheme.primary
            else -> scheme.onSurface
        }
    val container by
        animateColorAsState(
            if (highlighted) focusContainer else idleContainer,
            tween(90),
            label = "surface",
        )
    val contentColor by
        animateColorAsState(
            if (highlighted) focusContent else idleContent,
            tween(90),
            label = "content",
        )
    val accent = if (tone == ActionTone.PRIMARY) scheme.inverseOnSurface else scheme.primary
    Surface(
        onClick = { if (interactive) onClick() },
        enabled = true,
        modifier =
            modifier
                .testTag(tag)
                .focusProperties { canFocus = interactive }
                .onFocusChanged {
                    focused = it.isFocused
                    if (it.isFocused && interactive) onFocused()
                }
                .drawWithContent {
                    drawContent()
                    val progress = focusProgress.value
                    if (progress <= 0f) return@drawWithContent
                    if (focusBorder) {
                        val width = if (tone == ActionTone.READING) 1.dp.toPx() else 2.dp.toPx()
                        drawRoundRect(
                            accent.copy(alpha = progress),
                            Offset(width / 2, width / 2),
                            Size(size.width - width, size.height - width),
                            CornerRadius(12.dp.toPx()),
                            style = Stroke(width),
                        )
                    }
                },
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(12.dp)),
        colors =
            ClickableSurfaceDefaults.colors(
                containerColor = container,
                contentColor = contentColor,
                focusedContainerColor = container,
                focusedContentColor = contentColor,
                pressedContainerColor =
                    when (tone) {
                        ActionTone.READING -> scheme.background
                        ActionTone.SECONDARY -> container
                        else -> scheme.primary
                    },
                pressedContentColor =
                    when (tone) {
                        ActionTone.READING -> scheme.onBackground
                        ActionTone.SECONDARY -> contentColor
                        else -> scheme.onPrimary
                    },
                disabledContainerColor = scheme.surfaceVariant,
                disabledContentColor = scheme.onSurfaceVariant,
            ),
        border =
            ClickableSurfaceDefaults.border(
                border = Border.None,
                focusedBorder = Border.None,
                pressedBorder = Border.None,
            ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1f, pressedScale = 1f),
        content = {
            CompositionLocalProvider(LocalSurfaceRaised provides highlighted) { content() }
        },
    )
}

internal fun Modifier.horizontalStep(action: (Int, Int) -> Unit) = onPreviewKeyEvent { event ->
    val delta =
        when (event.key) {
            Key.DirectionLeft -> -1
            Key.DirectionRight -> 1
            else -> 0
        }
    if (delta == 0) false
    else {
        if (event.type == KeyEventType.KeyDown) action(delta, event.nativeKeyEvent.repeatCount)
        true
    }
}

@Composable
internal fun ActionButton(
    label: String,
    tag: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tone: ActionTone = ActionTone.SECONDARY,
    focusBorder: Boolean = true,
    onFocused: () -> Unit = {},
) {
    RemoteSurface(tag, onClick, modifier.heightIn(min = 48.dp), tone, onFocused, focusBorder) {
        Text(
            label,
            Modifier.align(Alignment.Center).padding(horizontal = 20.dp, vertical = 10.dp),
            style = MaterialTheme.typography.labelLarge,
            maxLines = 1,
        )
    }
}

@Composable
internal fun PowerControl(
    enabled: Boolean,
    onChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    focusBorder: Boolean = true,
    onFocused: () -> Unit = {},
) {
    val title = stringResource(R.string.label_power)
    val subOn = stringResource(R.string.power_on_sub)
    val subOff = stringResource(R.string.power_off_sub)
    val stateOn = stringResource(R.string.power_state_on)
    val stateOff = stringResource(R.string.power_state_off)
    RemoteSurface(
        "power",
        { onChange(!enabled) },
        modifier
            .horizontalStep { delta, repeat -> if (repeat == 0) onChange(delta > 0) }
            .semantics {
                role = Role.Switch
                stateDescription = if (enabled) stateOn else stateOff
            },
        onFocused = onFocused,
        focusBorder = focusBorder,
    ) {
        Row(
            Modifier.fillMaxWidth().padding(18.dp, 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(title, style = MaterialTheme.typography.titleLarge)
                Text(
                    if (enabled) subOn else subOff,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            val progress =
                animateFloatAsState(if (enabled) 1f else 0f, tween(150), label = "power-thumb")
            val track =
                if (enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.border
            val thumb = MaterialTheme.colorScheme.onPrimary
            Canvas(Modifier.size(50.dp, 29.dp)) {
                drawRoundRect(track, cornerRadius = CornerRadius(size.height / 2))
                val radius = (size.height - 6.dp.toPx()) / 2
                drawCircle(
                    thumb,
                    radius,
                    Offset(
                        3.dp.toPx() +
                            radius +
                            (size.width - 6.dp.toPx() - radius * 2) * progress.value,
                        size.height / 2,
                    ),
                )
            }
        }
    }
}

@Composable
internal fun BrightnessControl(
    value: Int,
    onChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
    focusBorder: Boolean = true,
    onFocused: () -> Unit = {},
) {
    val requester = remember { FocusRequester() }
    val change by rememberUpdatedState(onChange)
    val progress = animateFloatAsState(value / 100f, tween(110), label = "brightness-fill")
    val title = stringResource(R.string.label_brightness)
    RemoteSurface(
        "brightness",
        {},
        modifier
            .focusRequester(requester)
            .horizontalStep { delta, repeat ->
                onChange((value + delta * if (repeat >= 6) 5 else 1).coerceIn(0, 100))
            }
            .semantics {
                progressBarRangeInfo = ProgressBarRangeInfo(value.toFloat(), 0f..100f, 99)
                setProgress {
                    onChange(it.roundToInt().coerceIn(0, 100))
                    true
                }
            },
        onFocused = onFocused,
        focusBorder = focusBorder,
    ) {
        Column(
            Modifier.fillMaxWidth().padding(18.dp, 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            val track = MaterialTheme.colorScheme.border
            val fill = MaterialTheme.colorScheme.primary
            val onFill = MaterialTheme.colorScheme.onPrimary
            val onTrack = MaterialTheme.colorScheme.onSurface
            Box(
                Modifier.fillMaxWidth()
                    .height(34.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .pointerInput(Unit) {
                        detectTapGestures {
                            requester.requestFocus()
                            change((it.x / size.width * 100).roundToInt().coerceIn(0, 100))
                        }
                    }
                    .pointerInput(Unit) {
                        detectHorizontalDragGestures(
                            onDragStart = {
                                requester.requestFocus()
                                change((it.x / size.width * 100).roundToInt().coerceIn(0, 100))
                            }
                        ) { event, _ ->
                            event.consume()
                            change(
                                (event.position.x / size.width * 100).roundToInt().coerceIn(0, 100)
                            )
                        }
                    }
                    .drawWithContent {
                        drawRoundRect(track, cornerRadius = CornerRadius(size.height / 2))
                        drawRect(
                            fill,
                            size = Size(size.width * progress.value, size.height),
                        )
                        drawContent()
                    },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    "$value%",
                    Modifier.fillMaxWidth().drawWithContent {
                        clipRect(left = size.width * progress.value) {
                            this@drawWithContent.drawContent()
                        }
                    },
                    color = onTrack,
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.labelMedium,
                )
                Text(
                    "$value%",
                    Modifier.fillMaxWidth().drawWithContent {
                        clipRect(right = size.width * progress.value) {
                            this@drawWithContent.drawContent()
                        }
                    },
                    color = onFill,
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.labelMedium,
                )
            }
        }
    }
}

@Composable
internal fun ChoiceControl(
    title: String,
    tag: String,
    choices: List<String>,
    selected: Int,
    onChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
    detail: String = "",
    focusBorder: Boolean = true,
    onFocused: () -> Unit = {},
) {
    RemoteSurface(
        tag,
        { if (choices.size == 2) onChange(1 - selected) },
        modifier.horizontalStep { delta, repeat ->
            if (repeat == 0) onChange((selected + delta).coerceIn(0, choices.lastIndex))
        },
        onFocused = onFocused,
        focusBorder = focusBorder,
    ) {
        Row(
            Modifier.fillMaxWidth().padding(18.dp, 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleSmall)
                if (detail.isNotEmpty()) Text(detail, style = MaterialTheme.typography.bodySmall)
            }
            Row(
                Modifier.clip(RoundedCornerShape(10.dp))
                    .background(chromeColor())
                    .padding(3.dp)
            ) {
                choices.forEachIndexed { index, label ->
                    val chosen = selected == index
                    Text(
                        label,
                        Modifier.clip(RoundedCornerShape(8.dp))
                            .background(
                                if (chosen) MaterialTheme.colorScheme.primary else Color.Transparent
                            )
                            .pointerInput(onChange, index) { detectTapGestures { onChange(index) } }
                            .padding(horizontal = 14.dp, vertical = 6.dp),
                        color =
                            if (chosen) MaterialTheme.colorScheme.onPrimary
                            else MaterialTheme.colorScheme.onSurface,
                        style = MaterialTheme.typography.labelMedium,
                    )
                }
            }
        }
    }
}

@Composable
internal fun NumberControl(
    label: String,
    value: Int,
    range: IntRange,
    onChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
    focusBorder: Boolean = true,
    onFocused: () -> Unit = {},
) {
    val requester = remember { FocusRequester() }
    val increase = stringResource(R.string.increase)
    val decrease = stringResource(R.string.decrease)
    RemoteSurface(
        "step-$label",
        {},
        modifier
            .fillMaxWidth()
            .focusRequester(requester)
            .horizontalStep { delta, _ -> onChange((value + delta).coerceIn(range)) }
            .semantics {
                progressBarRangeInfo =
                    ProgressBarRangeInfo(
                        value.toFloat(),
                        range.first.toFloat()..range.last.toFloat(),
                    )
                setProgress {
                    onChange(it.roundToInt().coerceIn(range))
                    true
                }
            },
        onFocused = onFocused,
        focusBorder = focusBorder,
    ) {
        Row(Modifier.padding(18.dp, 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
            @Composable
            fun symbol(text: String, delta: Int) {
                Box(
                    Modifier.size(40.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(chromeColor())
                        .pointerInput(value) {
                            detectTapGestures {
                                requester.requestFocus()
                                onChange((value + delta).coerceIn(range))
                            }
                        }
                        .semantics {
                            contentDescription = label + if (delta < 0) decrease else increase
                            onClick {
                                onChange((value + delta).coerceIn(range))
                                true
                            }
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text,
                        color = MaterialTheme.colorScheme.onSurface,
                        style = MaterialTheme.typography.titleMedium,
                    )
                }
            }
            symbol("−", -1)
            Text(
                value.toString(),
                Modifier.width(58.dp),
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.titleSmall,
            )
            symbol("+", 1)
        }
    }
}
