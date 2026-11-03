package frameluma.app.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Border
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Typography
import androidx.tv.material3.lightColorScheme

internal object LumaColors {
    val paper = Color(0xfffaf9f5)
    val surface = Color(0xfffffefa)
    val ink = Color(0xff1b1a18)
    val secondary = Color(0xff4c4740)
    val clay = Color(0xffb55338)
    val onClay = Color(0xfffffcf6)
    val well = Color(0xfff1eee7)
    val line = Color(0xffc2b9a9)
    val separator = Color(0xffe3ded4)
    val online = Color(0xff27874c)
    val offline = Color(0xffbe4036)
    val scrim = Color.Black.copy(alpha = .20f)
    val stand = Color(0xffa9a397)
    val monitor = Color(0xff262522)
}

internal val LocalControlsEnabled = staticCompositionLocalOf { true }

/** The dashboard keeps its cursor visible while a modal temporarily owns actual keyboard focus. */
internal val LocalHighlightedControl = staticCompositionLocalOf<String?> { null }

/** True while the enclosing RemoteSurface shows its focused/disabled container. */
internal val LocalSurfaceRaised = staticCompositionLocalOf { false }

/** Child chrome (segmented track, symbol chip) follows the container with the same timing. */
@Composable
internal fun chromeColor(): Color =
    androidx.compose.animation
        .animateColorAsState(
            if (LocalSurfaceRaised.current) MaterialTheme.colorScheme.surface
            else MaterialTheme.colorScheme.surfaceVariant,
            androidx.compose.animation.core.tween(90),
            label = "chrome",
        )
        .value

private fun type(size: Int, weight: FontWeight = FontWeight.Normal) =
    TextStyle(
        fontFamily = FontFamily.Default,
        fontSize = size.sp,
        lineHeight = (size + 7).sp,
        fontWeight = weight,
    )

private val typography =
    Typography(
        headlineLarge = type(28, FontWeight.SemiBold),
        headlineMedium = type(24, FontWeight.SemiBold),
        titleLarge = type(22, FontWeight.Medium),
        titleMedium = type(20, FontWeight.Medium),
        titleSmall = type(18, FontWeight.Medium),
        bodyLarge = type(18),
        bodyMedium = type(17),
        bodySmall = type(16),
        labelLarge = type(18, FontWeight.Medium),
        labelMedium = type(17),
        labelSmall = type(16),
    )
private val scheme =
    lightColorScheme(
        primary = LumaColors.clay,
        onPrimary = LumaColors.onClay,
        background = LumaColors.paper,
        onBackground = LumaColors.ink,
        surface = LumaColors.surface,
        onSurface = LumaColors.ink,
        surfaceVariant = LumaColors.well,
        onSurfaceVariant = LumaColors.secondary,
        inverseSurface = LumaColors.ink,
        inverseOnSurface = LumaColors.paper,
        border = LumaColors.line,
    )

@Composable
internal fun LumaTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = scheme, typography = typography, content = content)
}

internal enum class ActionTone {
    PRIMARY,
    SECONDARY,
    FIELD,
    READING,
}

/** TV Material owns interaction state; every role uses the same semantic state matrix. */
@Composable
internal fun controlColors(tone: ActionTone) =
    with(MaterialTheme.colorScheme) {
        ClickableSurfaceDefaults.colors(
            containerColor =
                when (tone) {
                    ActionTone.PRIMARY -> primary
                    ActionTone.SECONDARY -> surfaceVariant
                    ActionTone.FIELD -> surface
                    ActionTone.READING -> background
                },
            contentColor = if (tone == ActionTone.PRIMARY) onPrimary else onSurface,
            focusedContainerColor =
                when (tone) {
                    ActionTone.READING -> background
                    ActionTone.PRIMARY -> inverseSurface
                    else -> surfaceVariant
                },
            focusedContentColor = if (tone == ActionTone.PRIMARY) inverseOnSurface else onSurface,
            pressedContainerColor = if (tone == ActionTone.READING) background else primary,
            pressedContentColor = if (tone == ActionTone.READING) onBackground else onPrimary,
            disabledContainerColor = surfaceVariant,
            disabledContentColor = onSurfaceVariant,
        )
    }

@Composable
internal fun controlBorder(tone: ActionTone) =
    ClickableSurfaceDefaults.border(
        border = Border.None,
        focusedBorder =
            Border(
                BorderStroke(
                    if (tone == ActionTone.READING) 1.dp else 2.dp,
                    if (tone == ActionTone.READING) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.inverseSurface,
                ),
                shape = RoundedCornerShape(12.dp),
            ),
        pressedBorder =
            Border(
                BorderStroke(2.dp, MaterialTheme.colorScheme.primary),
                shape = RoundedCornerShape(12.dp),
            ),
    )
