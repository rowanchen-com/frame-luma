package frameluma.app.ui

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import frameluma.app.R
import frameluma.layouts.LayoutProfile

internal fun localizedLayoutName(context: Context, profile: LayoutProfile): String =
    profile.displayName(context.resources.configuration.locales[0])

internal fun localizedLayoutHint(context: Context, profile: LayoutProfile): String =
    profile.installationHint(context.resources.configuration.locales[0])

internal fun localizedDirection(context: Context, profile: LayoutProfile, reverse: Boolean): String =
    profile.directionLabel(reverse, context.resources.configuration.locales[0])

internal fun localizedZoneLabels(context: Context, profile: LayoutProfile): List<String> =
    profile.labels(context.resources.configuration.locales[0]).toList()

@Composable
internal fun hintText(index: Int): String =
    stringResource(
        when (index) {
            0 -> R.string.hint_power
            1 -> R.string.hint_brightness
            2 -> R.string.hint_speed
            3 -> R.string.hint_direction
            4 -> R.string.hint_calibration
            else -> R.string.hint_about
        }
    )
