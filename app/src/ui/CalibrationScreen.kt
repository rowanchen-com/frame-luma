package frameluma.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import frameluma.app.R

@Composable
internal fun CalibrationScreen(page: Overlay.Calibration, session: AppSession, maxHeight: Float) {
    val first = remember { FocusRequester() }
    var highlight by remember { mutableIntStateOf(0) }
    var lastValid by remember { mutableStateOf(page.draft.options()) }
    val options = remember(page.draft) { runCatching { page.draft.options() }.getOrNull() }
    val context = LocalContext.current
    SideEffect { if (options != null) lastValid = options }
    PanelScaffold(
        page.title,
        session::back,
        width = 760,
        maxHeight = maxHeight,
        description =
            page.error.ifEmpty {
                stringResource(
                    R.string.calibration_allocated,
                    page.draft.total,
                    page.draft.units,
                    page.hint,
                )
            },
        initialFocus = first,
        confirmLabel = stringResource(R.string.save_layout),
        onConfirm = { session.saveCalibration() },
    ) {
        LightPreview(
            options ?: lastValid,
            NoPreviewColors,
            false,
            false,
            0,
            stringResource(R.string.preview_layout),
            Modifier.fillMaxWidth().height(100.dp),
            highlight,
            compact = true,
        )
        ScrollBody {
            page.draft.counts.forEachIndexed { index, value ->
                NumberControl(
                    page.labels[index],
                    value,
                    0..page.draft.units,
                    { new ->
                        session.editCalibration(
                            page.draft.copy(
                                counts = page.draft.counts.toMutableList().also { it[index] = new }
                            )
                        )
                    },
                    if (index == 0) Modifier.focusRequester(first) else Modifier,
                ) {
                    highlight = index
                }
            }
            Text(
                stringResource(R.string.calibration_edges),
                Modifier.padding(top = 14.dp),
                style = MaterialTheme.typography.titleSmall,
            )
            NumberControl(
                stringResource(R.string.head_units),
                page.draft.head,
                0..page.draft.units,
                { session.editCalibration(page.draft.copy(head = it)) },
            ) {
                highlight = -1
            }
            NumberControl(
                stringResource(R.string.tail_units),
                page.draft.tail,
                0..page.draft.units,
                { session.editCalibration(page.draft.copy(tail = it)) },
            ) {
                highlight = -1
            }
            NumberControl(
                stringResource(R.string.offset_units),
                page.draft.offset,
                0 until page.draft.units,
                { session.editCalibration(page.draft.copy(offset = it)) },
            ) {
                highlight = -1
            }
            val directionChoices =
                listOf(R.string.direction_left, R.string.direction_right).map {
                    stringResource(it)
                }
            ChoiceControl(
                stringResource(R.string.label_direction),
                "calibration-direction",
                directionChoices,
                if (page.draft.reverse) 1 else 0,
                { session.editCalibration(page.draft.copy(reverse = it == 1)) },
                detail = localizedDirection(context, lastValid.profile(), page.draft.reverse),
            )
            Text(
                stringResource(R.string.locator_title),
                Modifier.padding(top = 14.dp),
                style = MaterialTheme.typography.titleSmall,
            )
            ActionButton(
                stringResource(R.string.locate_test),
                "open-locator",
                session::locator,
                Modifier.fillMaxWidth(),
            )
            ActionButton(
                stringResource(R.string.reset_layout),
                "open-reset",
                session::confirmReset,
                Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
internal fun LocatorScreen(page: Overlay.Locator, session: AppSession, maxHeight: Float) {
    val first = remember { FocusRequester() }
    PanelScaffold(
        page.title,
        session::back,
        width = 660,
        maxHeight = maxHeight,
        description = page.note,
        initialFocus = first,
    ) {
        ScrollBody {
            NumberControl(
                stringResource(R.string.locator_unit),
                page.unit,
                1..page.units,
                { session.locatorSelection(it, page.zone) },
                Modifier.focusRequester(first),
            )
            ActionButton(
                stringResource(R.string.locate_unit),
                "locate-unit",
                { session.locate(page.unit, -1) },
                Modifier.fillMaxWidth(),
            )
            ActionButton(
                stringResource(R.string.locate_next),
                "locate-next",
                {
                    val next = page.unit % page.units + 1
                    session.locatorSelection(next, page.zone)
                    session.locate(next, -1)
                },
                Modifier.fillMaxWidth(),
            )
            NumberControl(
                stringResource(R.string.screen_zone),
                page.zone,
                1..page.labels.size,
                { session.locatorSelection(page.unit, it) },
            )
            ActionButton(
                stringResource(R.string.save_and_locate),
                "locate-zone",
                { session.saveCalibration(close = false) { session.locate(0, page.zone - 1) } },
                Modifier.fillMaxWidth(),
            )
            ActionButton(
                stringResource(R.string.end_locator),
                "end-locator",
                session::endLocator,
                Modifier.fillMaxWidth(),
            )
        }
    }
}
