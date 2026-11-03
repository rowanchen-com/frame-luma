package frameluma.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.key.*
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.SurfaceDefaults
import androidx.tv.material3.Text
import frameluma.app.R
import kotlinx.coroutines.launch

@Composable
internal fun OverlayScreen(page: Overlay, session: AppSession, maxHeight: Float) {
    key(page::class) {
        Box(
            Modifier.fillMaxSize().background(LumaColors.scrim).pointerInput(Unit) {
                detectTapGestures {}
            },
            contentAlignment = Alignment.Center,
        ) {
            when (page) {
                is Overlay.Loading ->
                    PanelScaffold(page.title, session::back, maxHeight = maxHeight) {
                        Text(stringResource(R.string.loading), style = MaterialTheme.typography.bodyLarge)
                    }
                is Overlay.Failure ->
                    PanelScaffold(page.title, session::back, maxHeight = maxHeight) {
                        Text(page.message)
                    }
                is Overlay.About -> {
                    val screen by session.state.collectAsState()
                    AboutScreen(
                        page.version,
                        screen.device,
                        { session.back() },
                        { session.open(Panel.LICENSES) },
                        maxHeight,
                        { session.open(Panel.RECOVERY) },
                    )
                }
                is Overlay.Licenses -> LicenseListScreen(session, maxHeight)
                is Overlay.Recovery -> RecoveryScreen(session, maxHeight)
                is Overlay.Reader ->
                    ReaderScreen(page.title, page.body, { session.back() }, maxHeight)
                is Overlay.Calibration -> CalibrationScreen(page, session, maxHeight)
                is Overlay.Locator -> LocatorScreen(page, session, maxHeight)
                is Overlay.Reset ->
                    PanelScaffold(
                        page.title,
                        session::back,
                        width = 600,
                        maxHeight = maxHeight,
                        confirmLabel = stringResource(R.string.reset_default),
                        onConfirm = session::reset,
                    ) {
                        Text(
                            stringResource(R.string.reset_message),
                            style = MaterialTheme.typography.bodyLarge,
                        )
                    }
            }
        }
    }
}

@Composable
internal fun PanelScaffold(
    title: String,
    onBack: () -> Unit,
    width: Int = 700,
    maxHeight: Float = 700f,
    description: String = "",
    initialFocus: FocusRequester? = null,
    confirmLabel: String? = null,
    onConfirm: () -> Unit = {},
    backLabel: String? = null,
    confirmTone: ActionTone = ActionTone.PRIMARY,
    content: @Composable ColumnScope.() -> Unit,
) {
    val backFocus = remember { FocusRequester() }
    val target = initialFocus ?: backFocus
    LaunchedEffect(target) { target.requestFocus() }
    val resolvedBack =
        backLabel ?: stringResource(if (confirmLabel == null) R.string.back else R.string.cancel)
    Surface(
        Modifier.width(width.dp).heightIn(max = maxHeight.dp).semantics { paneTitle = title },
        shape = RoundedCornerShape(22.dp),
        colors =
            SurfaceDefaults.colors(
                MaterialTheme.colorScheme.background,
                MaterialTheme.colorScheme.onBackground,
            ),
    ) {
        Column(Modifier.padding(26.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(title, style = MaterialTheme.typography.headlineMedium)
            if (description.isNotEmpty())
                Text(
                    description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            Separator()
            Column(Modifier.fillMaxWidth().weight(1f, fill = false), content = content)
            Separator()
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                ActionButton(
                    resolvedBack,
                    "panel-back",
                    onBack,
                    Modifier.weight(1f).focusRequester(backFocus),
                )
                if (confirmLabel != null)
                    ActionButton(
                        confirmLabel,
                        "panel-confirm",
                        onConfirm,
                        Modifier.weight(1f),
                        confirmTone,
                    )
            }
        }
    }
}

@Composable
internal fun AboutScreen(
    version: String,
    model: String,
    onBack: () -> Unit,
    onLicenses: () -> Unit,
    maxHeight: Float = 700f,
    onRecovery: () -> Unit = {},
) {
    PanelScaffold(
        stringResource(R.string.about_title),
        onBack,
        width = 650,
        maxHeight = maxHeight,
        confirmLabel = stringResource(R.string.open_licenses),
        onConfirm = onLicenses,
        backLabel = stringResource(R.string.back),
        confirmTone = ActionTone.SECONDARY,
    ) {
        Column(
            Modifier.fillMaxWidth().padding(vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(stringResource(R.string.brand_title), style = MaterialTheme.typography.titleLarge)
            Text(stringResource(R.string.about_tagline), style = MaterialTheme.typography.bodyLarge)
            Spacer(Modifier.height(4.dp))
            DetailRow(stringResource(R.string.about_version), version)
            DetailRow(stringResource(R.string.about_model), model)
            Text(
                stringResource(R.string.about_note),
                style = MaterialTheme.typography.bodyMedium,
            )
            ActionButton(stringResource(R.string.recovery_title), "open-recovery", onRecovery, Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun RecoveryScreen(session: AppSession, maxHeight: Float) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val managed = remember(context) { frameluma.runtime.Recovery.supported(context) }
    if (!managed) {
        val externalHost = remember(context) {
            runCatching {
                frameluma.runtime.Devices.control(context).execution() ==
                    frameluma.deviceapi.DisplayControl.Execution.EXTERNAL_HOST
            }.getOrDefault(false)
        }
        PanelScaffold(stringResource(R.string.recovery_title), session::back, width = 650,
            maxHeight = maxHeight,
            description = stringResource(if (externalHost) R.string.recovery_host_description else R.string.recovery_service_description)) {
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                DetailRow(stringResource(R.string.recovery_mode_label),
                    stringResource(if (externalHost) R.string.recovery_host_mode else R.string.recovery_service_mode))
                Text(stringResource(R.string.recovery_lifecycle_note), style = MaterialTheme.typography.bodyLarge)
                Text(stringResource(R.string.recovery_native_note), style = MaterialTheme.typography.bodyLarge)
            }
        }
        return
    }
    val scope = rememberCoroutineScope()
    val first = remember { FocusRequester() }
    val options = listOf(
        "enabled" to R.string.recovery_enabled,
        "notification" to R.string.recovery_notification,
        "sync" to R.string.recovery_sync,
        "foreground" to R.string.recovery_foreground,
        "keys" to R.string.recovery_keys,
        "force_stop" to R.string.recovery_force_stop,
    )
    PanelScaffold(stringResource(R.string.recovery_title), session::back, width = 680,
        maxHeight = maxHeight, description = stringResource(R.string.recovery_description), initialFocus = first) {
        Column(Modifier.fillMaxWidth().heightIn(max = 380.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            options.forEachIndexed { index, (key, label) ->
                var enabled by remember(key) { mutableStateOf(frameluma.runtime.Recovery.option(context, key)) }
                val change: (Boolean) -> Unit = { value ->
                    enabled = value
                    scope.launch(kotlinx.coroutines.Dispatchers.IO) { frameluma.runtime.Recovery.option(context, key, value) }
                }
                val state = stringResource(if (enabled) R.string.power_state_on else R.string.power_state_off)
                ActionButton("${stringResource(label)} · $state", "recovery-$key", { change(!enabled) },
                    Modifier.fillMaxWidth().then(if (index == 0) Modifier.focusRequester(first) else Modifier)
                        .horizontalStep { delta, repeat -> if (repeat == 0) change(delta > 0) })
            }
            ActionButton(stringResource(R.string.recovery_accessibility_settings), "recovery-accessibility", {
                runCatching { context.startActivity(android.content.Intent(android.provider.Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)) }
            }, Modifier.fillMaxWidth())
            ActionButton(stringResource(R.string.recovery_notification_settings), "recovery-notifications", {
                runCatching { context.startActivity(android.content.Intent(android.provider.Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)) }
            }, Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun LicenseListScreen(session: AppSession, maxHeight: Float) {
    val first = remember { FocusRequester() }
    val entries =
        listOf(
            stringResource(R.string.license_project) to "Apache-2.0.txt",
            stringResource(R.string.license_third_party) to "THIRD_PARTY_LICENSES.txt",
        )
    PanelScaffold(
        stringResource(R.string.licenses_title),
        session::back,
        width = 640,
        maxHeight = maxHeight,
        description = stringResource(R.string.licenses_desc),
        initialFocus = first,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            entries.forEachIndexed { index, (title, file) ->
                ActionButton(
                    title,
                    "license-$file",
                    { session.readLicense(title, file) },
                    Modifier.fillMaxWidth()
                        .then(if (index == 0) Modifier.focusRequester(first) else Modifier),
                )
            }
        }
    }
}

@Composable
internal fun ReaderScreen(
    title: String,
    body: String,
    onBack: () -> Unit,
    maxHeight: Float = 700f,
) {
    val first = remember { FocusRequester() }
    val scroll = rememberScrollState()
    val scope = rememberCoroutineScope()
    PanelScaffold(
        title,
        onBack,
        width = 720,
        maxHeight = maxHeight,
        description = stringResource(R.string.reader_desc),
        initialFocus = first,
    ) {
        RemoteSurface(
            "license-reader",
            {},
            Modifier.fillMaxWidth()
                .heightIn(max = 360.dp)
                .focusRequester(first)
                .onPreviewKeyEvent { event ->
                    val delta =
                        when (event.key) {
                            Key.DirectionUp -> -1
                            Key.DirectionDown -> 1
                            Key.DirectionLeft -> -2
                            Key.DirectionRight -> 2
                            else -> 0
                        }
                    if (
                        delta == 0 ||
                            (delta == 1 && scroll.value == scroll.maxValue) ||
                            (delta == -1 && scroll.value == 0)
                    )
                        false
                    else {
                        if (event.type == KeyEventType.KeyDown)
                            scope.launch {
                                val step = if (kotlin.math.abs(delta) == 2) 240 else 34
                                scroll.animateScrollTo(
                                    (scroll.value + step * if (delta < 0) -1 else 1).coerceIn(
                                        0,
                                        scroll.maxValue,
                                    )
                                )
                            }
                        true
                    }
                },
            tone = ActionTone.READING,
        ) {
            ScrollBody(Modifier.padding(12.dp), scroll) {
                Text(
                    body,
                    Modifier.widthIn(max = 600.dp),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
}

@Composable
internal fun ScrollBody(
    modifier: Modifier = Modifier,
    scroll: ScrollState = rememberScrollState(),
    content: @Composable ColumnScope.() -> Unit,
) {
    val rail = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = .55f)
    Box(
        modifier.fillMaxWidth().drawWithContent {
            drawContent()
            if (scroll.maxValue > 0) {
                val track = (size.height - 12.dp.toPx()).coerceAtLeast(0f)
                val thumb =
                    (track * size.height / (size.height + scroll.maxValue))
                        .coerceAtLeast(22.dp.toPx())
                        .coerceAtMost(track)
                val top = 6.dp.toPx() + (track - thumb) * scroll.value / scroll.maxValue
                drawRoundRect(
                    rail,
                    Offset(size.width - 3.dp.toPx(), top),
                    androidx.compose.ui.geometry.Size(2.dp.toPx(), thumb),
                    CornerRadius(1.dp.toPx()),
                )
            }
        }
    ) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(scroll).padding(end = 20.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            content = content,
        )
    }
}

