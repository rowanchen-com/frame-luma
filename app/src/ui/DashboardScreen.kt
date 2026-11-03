package frameluma.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.SurfaceDefaults
import androidx.tv.material3.Text
import frameluma.app.R
import frameluma.runtime.LayoutOptions
import kotlinx.coroutines.flow.StateFlow

interface DashboardActions {
    fun enabled(value: Boolean)

    fun brightness(value: Int)

    fun speed(index: Int)

    fun reverse(value: Boolean)

    fun open(panel: Panel)

    fun focused(index: Int)
}

@Composable
internal fun FrameLumaApp(session: AppSession) {
    val state by session.state.collectAsState()
    val active by session.active.collectAsState()
    val overlays by session.overlays.collectAsState()
    val restore by session.restore.collectAsState()
    val rendering by session.renderProfile.collectAsState()
    val performance by session.performance.collectAsState()
    val backdrop = rememberBackdrop(active)
    LumaTheme {
        BoxWithConstraints(
            Modifier.fillMaxSize().onPreviewKeyEvent {
                session.noteInput()
                false
            }
        ) {
            val physical = LocalDensity.current
            val width = with(physical) { maxWidth.roundToPx() }
            val height = with(physical) { maxHeight.roundToPx() }
            val workspace = remember(width, height) { WorkspaceLayout.fit(width, height) }
            val capture = Modifier.capturedScreen(
                session, state, active, backdrop, IntSize(width, height), overlays.isNotEmpty()
            )
            Box(Modifier.fillMaxSize()) {
                ColorField(backdrop, active, IntSize(width, height), rendering, performance, session::backgroundRenderer)
                Box(Modifier.fillMaxSize().then(capture)) {
                    CompositionLocalProvider(
                        LocalDensity provides Density(workspace.scale, physical.fontScale)
                    ) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            CompositionLocalProvider(LocalControlsEnabled provides overlays.isEmpty()) {
                                DashboardScreen(
                                    state,
                                    session,
                                    session.colors,
                                    session.timings,
                                    active && overlays.isEmpty(),
                                    session.focus,
                                    restore,
                                )
                            }
                            overlays.lastOrNull()?.let {
                                OverlayScreen(
                                    it,
                                    session,
                                    (height / workspace.scale * .86f).coerceAtMost(760f),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Recomposes only for [state]: key presses, saved settings and connection changes. Frame colours
 * and measured intervals are flows read by the preview layer and the timing line alone.
 */
@Composable
internal fun DashboardScreen(
    state: ScreenState,
    actions: DashboardActions,
    frames: StateFlow<IntArray>,
    timings: StateFlow<Timings>,
    takeFocus: Boolean,
    initialFocus: Int = 0,
    restore: FocusRestore = FocusRestore(),
) {
    val requests = remember { List(7) { FocusRequester() } }
    val focus = remember { mutableIntStateOf(initialFocus.coerceIn(0, 6)) }
    var restoredToken by remember { mutableIntStateOf(0) }
    val restoring = restore.token > 0 && restore.token != restoredToken
    val highlight = if (restoring) restore.index.coerceIn(0, 6) else focus.intValue
    // Closing an overlay restores the stop that opened it, not the first focusable control.
    LaunchedEffect(takeFocus, restore.token) {
        if (!takeFocus) return@LaunchedEffect
        if (restore.token <= 0) {
            withFrameNanos { }
            requests[focus.intValue].requestFocus()
            return@LaunchedEffect
        }
        val target = restore.index.coerceIn(0, 6)
        repeat(4) {
            withFrameNanos { }
            requests[target].requestFocus()
            withFrameNanos { }
            if (focus.intValue == target) {
                restoredToken = restore.token
                return@LaunchedEffect
            }
        }
        restoredToken = restore.token
    }
    fun onFocus(index: Int) {
        if (restoring && index != restore.index) return
        focus.intValue = index
        actions.focused(index)
    }
    // Stable instances let an unchanged row skip while another control or status line changes.
    val fields = remember(requests) {
        List(4) { index ->
            Modifier.fillMaxWidth().focusRequester(requests[index]).focusProperties {
                up = if (index == 0) FocusRequester.Cancel else requests[index - 1]
                down = requests[if (index == 3) 4 else index + 1]
            }
        }
    }
    val context = LocalContext.current
    val direction =
        state.layout?.let { localizedDirection(context, it.profile(), it.reverse) }.orEmpty()
    val low = stringResource(R.string.speed_low)
    val medium = stringResource(R.string.speed_medium)
    val high = stringResource(R.string.speed_high)
    val speedChoices = remember(low, medium, high) { listOf(low, medium, high) }
    val towardLeft = stringResource(R.string.direction_left)
    val towardRight = stringResource(R.string.direction_right)
    val directionChoices = remember(towardLeft, towardRight) { listOf(towardLeft, towardRight) }
    val stopTags = remember { listOf("power", "brightness", "speed", "direction", "open-CALIBRATION", "open-ABOUT") }
    CompositionLocalProvider(LocalHighlightedControl provides stopTags[highlight]) {
    Surface(
        modifier =
            Modifier.size(WorkspaceLayout.WIDTH.dp, WorkspaceLayout.HEIGHT.dp).testTag("workspace"),
        shape = RoundedCornerShape(WorkspaceLayout.CORNER_RADIUS.dp),
        colors =
            SurfaceDefaults.colors(
                MaterialTheme.colorScheme.background,
                MaterialTheme.colorScheme.onBackground,
            ),
    ) {
        Column(
            Modifier.fillMaxSize().padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        stringResource(R.string.app_name),
                        style = MaterialTheme.typography.headlineLarge,
                    )
                    Text(
                        stringResource(R.string.tagline),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                ConnectionBadge(state.connection, state.connected)
            }
            Separator()
            Row(
                Modifier.fillMaxWidth().weight(1f),
                horizontalArrangement = Arrangement.spacedBy(28.dp),
            ) {
                Column(Modifier.weight(.46f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    PowerControl(
                        state.enabled,
                        actions::enabled,
                        fields[0],
                        focusBorder = false,
                    ) {
                        onFocus(0)
                    }
                    BrightnessControl(
                        state.brightness,
                        actions::brightness,
                        fields[1],
                        focusBorder = false,
                    ) {
                        onFocus(1)
                    }
                    ChoiceControl(
                        stringResource(R.string.label_speed),
                        "speed",
                        speedChoices,
                        state.speed,
                        actions::speed,
                        fields[2],
                        focusBorder = false,
                        onFocused = { onFocus(2) },
                    )
                    ChoiceControl(
                        stringResource(R.string.label_direction),
                        "direction",
                        directionChoices,
                        if (state.layout?.reverse == true) 1 else 0,
                        { actions.reverse(it == 1) },
                        fields[3],
                        direction,
                        focusBorder = false,
                    ) {
                        onFocus(3)
                    }
                }
                Column(Modifier.weight(.54f)) {
                    PreviewPane(
                        state.layout,
                        state.enabled,
                        state.following,
                        state.brightness,
                        state.message,
                        frames,
                        Modifier.fillMaxWidth().weight(1f),
                    )
                    EquipmentDetails(
                        state.lightName,
                        state.lightModel,
                        state.device,
                        state.source,
                        timings,
                    )
                }
            }
            Separator()
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                val entries =
                    listOf(
                        stringResource(R.string.open_calibration) to Panel.CALIBRATION,
                        stringResource(R.string.open_about) to Panel.ABOUT,
                    )
                entries.forEachIndexed { index, (label, panel) ->
                    val stop = index + 4
                    ActionButton(
                        label,
                        "open-$panel",
                        { actions.open(panel) },
                        Modifier.weight(1f)
                            .focusRequester(requests[stop])
                            .focusProperties {
                                up = requests[3]
                                down = FocusRequester.Cancel
                                left = if (index == 0) FocusRequester.Cancel else requests[stop - 1]
                                right =
                                    if (index == 2) FocusRequester.Cancel else requests[stop + 1]
                            },
                        focusBorder = false,
                        onFocused = { onFocus(stop) },
                    )
                }
            }
            FocusHint(focus)
        }
    }
    }
}

@Composable
private fun FocusHint(focus: androidx.compose.runtime.IntState) {
    Text(
        hintText(focus.intValue),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1,
    )
}

@Composable
private fun PreviewPane(
    layout: LayoutOptions?,
    enabled: Boolean,
    following: Boolean,
    brightness: Int,
    message: String,
    frames: StateFlow<IntArray>,
    modifier: Modifier,
) {
    // Collected here but read only while drawing: a new frame repaints the preview layer without
    // composition or layout, and the panel around it is untouched.
    val colors = frames.collectAsState()
    LightPreview(
        layout,
        { colors.value },
        enabled,
        following,
        brightness,
        message,
        modifier.testTag("light-preview"),
    )
}

@Composable
private fun EquipmentDetails(
    lightName: String,
    lightModel: String,
    device: String,
    source: String,
    timings: StateFlow<Timings>,
) {
    Column(Modifier.testTag("equipment-details"), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        DetailRow(stringResource(R.string.detail_light), lightName)
        // Identity is temporarily absent during stop/identify/reconnect.
        // Retain the same line so the weighted preview never changes size.
        Text(
            lightModel.ifEmpty { "—" },
            Modifier.fillMaxWidth().padding(start = 86.dp),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            minLines = 1,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        DetailRow(stringResource(R.string.detail_model), device)
        DetailRow(stringResource(R.string.detail_source), source)
        Separator()
        TimingRow(timings)
    }
}

/** The only reader of measured intervals; a new value recomposes these two lines alone. */
@Composable
private fun TimingRow(timings: StateFlow<Timings>) {
    val value by timings.collectAsState()
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            stringResource(R.string.output_format, Timings.text(value.outputMs)),
            Modifier.weight(1f),
            style = MaterialTheme.typography.bodyMedium,
            minLines = 1,
            maxLines = 1,
        )
        Text(
            stringResource(R.string.sweep_format, Timings.text(value.sweepMs)),
            Modifier.weight(1f),
            style = MaterialTheme.typography.bodyMedium,
            minLines = 1,
            maxLines = 1,
        )
    }
}

@Composable
internal fun Separator() {
    Box(Modifier.fillMaxWidth().height(1.dp).background(LumaColors.separator))
}

@Composable
internal fun DetailRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            label,
            Modifier.width(78.dp).alignByBaseline(),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            value,
            Modifier.weight(1f).alignByBaseline(),
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun ConnectionBadge(label: String, connected: Boolean) {
    Row(
        Modifier.background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(30.dp))
            .padding(12.dp, 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(
            Modifier.size(8.dp)
                .background(
                    if (connected) LumaColors.online else LumaColors.offline,
                    RoundedCornerShape(8.dp),
                )
        )
        Text(label, style = MaterialTheme.typography.labelSmall)
    }
}
