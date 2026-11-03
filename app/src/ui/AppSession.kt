package frameluma.app.ui

import android.content.Context
import android.os.SystemClock
import android.util.Log
import frameluma.app.R
import frameluma.core.FramePacer
import frameluma.core.UiRenderingProfile
import frameluma.layouts.LayoutCatalog
import frameluma.runtime.AppCore
import frameluma.runtime.Client
import frameluma.runtime.LightConnections
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class FocusRestore(val token: Int = 0, val index: Int = 0)

/**
 * Activity-owned bridge. Stable state, measured intervals, preview colours and modal drafts are
 * separate flows with separate lifetimes; every mutation below runs on the main thread.
 */
class AppSession(context: Context, private val scope: CoroutineScope) : DashboardActions {
    private val context = context.applicationContext
    private val mutable = MutableStateFlow(ScreenState())
    val state = mutable.asStateFlow()
    private val measured = MutableStateFlow(Timings.NONE)
    /** Output and sweep intervals, changing on screen at most once per timing interval. */
    val timings = measured.asStateFlow()
    private val stack = MutableStateFlow<List<Overlay>>(emptyList())
    val overlays = stack.asStateFlow()
    private val visible = MutableStateFlow(false)
    val active = visible.asStateFlow()
    private val performanceState = MutableStateFlow(UiPerformance.STANDARD)
    internal val performance = performanceState.asStateFlow()
    private val outlet = UiFrameOutlet({ performanceState.value.previewIntervalMs }, AppCore::publishUiFrame)
    /** Preview colours; a frame with unchanged content is not emitted again. */
    val colors = outlet.colors
    private val rendering = MutableStateFlow<UiRenderingProfile?>(null)
    val renderProfile = rendering.asStateFlow()
    private val restoreFlow = MutableStateFlow(FocusRestore())
    val restore = restoreFlow.asStateFlow()
    private var savedFocus = 0
    private var restoreToken = 0
    var focus: Int = 0
    private var captured = 0L
    private var gpuCopies = 0L
    private var captureBackend = "inactive"
    private var backgroundRenderer = "inactive"
    private var captureErrors = 0L
    private var stateChanges = 0L
    private var timingChanges = 0L
    private var previewChanges = 0L
    private val stateListener = Runnable { requestStatus() }
    private val statusSignals = Channel<Unit>(Channel.CONFLATED)
    private var statusJob: Job? = null
    private val timingGate = TimingGate()
    private var timingJob: Job? = null
    private var brightnessJob: Job? = null
    private var brightnessDraft: Int? = null
    private var locatorJob: Job? = null
    private var locating = false
    private var failureLogged = false

    /** Last remote key time; capture lowers its frequency while the user is navigating. */
    @Volatile
    var lastInputAt: Long = 0L
        private set

    fun noteInput() {
        lastInputAt = SystemClock.elapsedRealtime()
    }

    fun backgroundRenderer(value: String) { backgroundRenderer = value }


    fun resume() {
        if (visible.value) return
        visible.value = true
        AppCore.addStateListener(stateListener)
        LightConnections.invalidate()
        AppCore.setUiVisible(true)
        statusJob = scope.launch { followStatus() }
        requestStatus()
        launchWork {
            performanceState.value = UiPerformance.read(context)
            rendering.value = DevicesProfile.read(context)
            Client.send(context, "STATUS")
            if (AppCore.enabled()) Client.flow(context, true)
        }
    }

    fun pause() {
        visible.value = false
        captureBackend = "inactive"
        backgroundRenderer = "inactive"
        AppCore.removeStateListener(stateListener)
        statusJob?.cancel()
        statusJob = null
        timingJob?.cancel()
        timingJob = null
        timingGate.cancel()
        flushBrightness()
        endLocator()
        stack.value = emptyList()
        AppCore.setUiVisible(false)
        Client.refresh(context)
    }

    private fun requestStatus() {
        statusSignals.trySend(Unit)
    }

    /**
     * The visible page's only status reader. Notifications coalesce into one read; settings, USB
     * presence and model policy are read again only after the configuration token changes, and each
     * engine report is parsed once. Without notifications it still wakes when the report goes stale.
     */
    private suspend fun followStatus() {
        var settings: StatusSettings? = null
        var expiresIn = 0L
        while (true) {
            val signalled =
                if (expiresIn > 0) withTimeoutOrNull(expiresIn) { statusSignals.receive() } != null
                else {
                    statusSignals.receive()
                    true
                }
            if (signalled) {
                delay(StatusPolicy.COALESCE_MS)
                statusSignals.tryReceive()
            }
            expiresIn = 0
            try {
                val token = AppCore.configurationToken()
                val cached = settings?.takeIf { it.token == token }
                val next =
                    withContext(Dispatchers.IO) {
                        StatusSnapshot.read(cached ?: StatusSettings.read(context, token))
                    }
                if (token != AppCore.configurationToken()) {
                    // Settings moved during the read; never show the stale snapshot.
                    requestStatus()
                    continue
                }
                settings = next.settings
                val consumedAt = SystemClock.elapsedRealtime()
                val current = next.stateAt(context, consumedAt)
                // A brightness edit still waiting to be saved wins over the stored value.
                changeState(current.copy(brightness = brightnessDraft ?: current.brightness))
                offerTimings(timingsOf(next.report, current.following))
                expiresIn = next.report.expiresIn(consumedAt)
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                reportFailure(error)
            }
        }
    }

    private fun changeState(next: ScreenState) {
        if (next == mutable.value) return
        mutable.value = next
        stateChanges++
    }

    private inline fun editState(change: (ScreenState) -> ScreenState) =
        changeState(change(mutable.value))

    /** Semantic changes show at once; a run of new numbers shows only its newest value per interval. */
    private fun offerTimings(value: Timings) {
        if (timingGate.offer(value, SystemClock.elapsedRealtime())) {
            showTimings()
            return
        }
        val due = timingGate.dueAt
        if (due == null) {
            cancelTimingFallback()
            return
        }
        // One fallback release takes whichever value is newest when it fires.
        if (timingJob?.isActive == true) return
        timingJob = scope.launch {
            delay((due + StatusPolicy.TIMING_FALLBACK_MS - SystemClock.elapsedRealtime()).coerceAtLeast(1))
            if (timingGate.release(SystemClock.elapsedRealtime())) showTimings()
        }
    }

    /** A due value is written in the same main-thread task as a colour sample: one UI frame for both. */
    private fun releaseDueTimings() {
        if (timingGate.release(SystemClock.elapsedRealtime())) showTimings()
    }

    private fun showTimings() {
        cancelTimingFallback()
        measured.value = timingGate.shown
        timingChanges++
    }

    private fun cancelTimingFallback() {
        timingJob?.cancel()
        timingJob = null
    }

    override fun enabled(value: Boolean) {
        if (AppCore.enabled() == value) return
        if (value) AppCore.setEnabled(true) else AppCore.setPower(false)
        editState { it.copy(enabled = value) }
        launchWork { Client.flow(context, value, true) }
    }

    override fun brightness(value: Int) {
        val next = value.coerceIn(0, 100)
        brightnessDraft = next
        editState { it.copy(brightness = next) }
        brightnessJob?.cancel()
        brightnessJob = scope.launch {
            delay(600)
            flushBrightness()
        }
    }

    private fun flushBrightness() {
        brightnessJob?.cancel()
        brightnessJob = null
        brightnessDraft?.let {
            AppCore.setBrightness(it)
            Client.refresh(context)
        }
        brightnessDraft = null
    }

    override fun speed(index: Int) {
        AppCore.setPeriod(FramePacer.speedPeriod(index))
        editState { it.copy(speed = index) }
        launchWork { Client.refresh(context) }
    }

    override fun reverse(value: Boolean) {
        launchWork {
            val layout = AppCore.layout() ?: return@launchWork
            if (layout.reverse != value) AppCore.setLayout(layout.json().put("reverse", value))
            Client.refresh(context)
        }
    }

    override fun focused(index: Int) {
        focus = index
    }

    /** Every sample keeps the light mailbox fresh; the preview hears only changed colours. */
    fun publish(colors: IntArray, key: String, gpuCopy: Boolean) {
        if (!visible.value) return
        captured++
        if (gpuCopy) gpuCopies++
        captureBackend = if (gpuCopy) "gpu_copy" else "cached_background"
        if (outlet.publish(colors, key)) {
            previewChanges++
            releaseDueTimings()
        }
    }

    fun captureFailed(error: Exception) {
        if (captureErrors++ == 0L) Log.w("FrameLumaUi", "Compose frame copy unavailable", error)
    }

    override fun open(panel: Panel) {
        savedFocus = focus
        val title =
            context.getString(
                when (panel) {
                    Panel.CALIBRATION -> R.string.calibration_title
                    Panel.ABOUT -> R.string.about_title
                    Panel.LICENSES -> R.string.licenses_title
                    Panel.RECOVERY -> R.string.recovery_title
                }
            )
        val pending = Overlay.Loading(title)
        stack.update { it + pending }
        scope.launch {
            val prepared =
                try {
                    withContext(Dispatchers.IO) {
                        when (panel) {
                            Panel.ABOUT ->
                                Overlay.About(
                                    context.packageManager
                                        .getPackageInfo(context.packageName, 0)
                                        .versionName
                                        .orEmpty(),
                                    title,
                                )
                            Panel.LICENSES -> Overlay.Licenses(title)
                            Panel.RECOVERY -> Overlay.Recovery(title)
                            Panel.CALIBRATION -> {
                                val layout = AppCore.layout() ?: throw IllegalStateException("请等待控制盒识别完成")
                                val profile = layout.profile()
                                Overlay.Calibration(
                                    CalibrationDraft.from(layout),
                                    localizedZoneLabels(context, profile),
                                    localizedLayoutHint(context, profile),
                                    title = title,
                                )
                            }

                        }
                    }
                } catch (error: Exception) {
                    if (error is CancellationException) throw error
                    Overlay.Failure(title, context.getString(R.string.error_read_back))
                }
            stack.update { pages -> pages.map { if (it === pending) prepared else it } }
        }
    }

    fun readLicense(title: String, file: String) {
        val pending = Overlay.Loading(title)
        stack.update { it + pending }
        scope.launch {
            val page =
                try {
                    Overlay.Reader(
                        title,
                        withContext(Dispatchers.IO) {
                            context.assets.open("licenses/$file").bufferedReader().use {
                                it.readText()
                            }
                        },
                    )
                } catch (error: Exception) {
                    if (error is CancellationException) throw error
                    Overlay.Failure(title, context.getString(R.string.error_license_read))
                }
            stack.update { pages -> pages.map { if (it === pending) page else it } }
        }
    }

    fun back(): Boolean {
        if (stack.value.isEmpty()) return false
        if (stack.value.last() is Overlay.Locator) endLocator()
        stack.update { it.dropLast(1) }
        if (stack.value.isEmpty()) {
            focus = savedFocus
            restoreFlow.value = FocusRestore(++restoreToken, savedFocus)
        }
        return true
    }

    fun editCalibration(value: CalibrationDraft) {
        replaceCalibration { it.copy(draft = value, error = "") }
    }

    fun saveCalibration(close: Boolean = true, then: (() -> Unit)? = null) {
        val page = stack.value.filterIsInstance<Overlay.Calibration>().lastOrNull() ?: return
        scope.launch {
            try {
                withContext(Dispatchers.IO) {
                    AppCore.setLayout(page.draft.options().json())
                    Client.refresh(context)
                }
                if (close) back()
                then?.invoke()
                requestStatus()
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                val message =
                    context.getString(R.string.error_calibration_total, page.draft.units)
                if (stack.value.lastOrNull() is Overlay.Locator)
                    replaceTop<Overlay.Locator> { it.copy(note = message) }
                else replaceCalibration { it.copy(error = message) }
            }
        }
    }

    fun locator() {
        val page = stack.value.lastOrNull() as? Overlay.Calibration ?: return
        stack.update {
            it +
                Overlay.Locator(
                    units = page.draft.units,
                    labels = page.labels,
                    note = context.getString(R.string.locator_note),
                    title = context.getString(R.string.locator_title),
                )
        }
    }

    fun locatorSelection(unit: Int, zone: Int) {
        replaceTop<Overlay.Locator> { it.copy(unit = unit, zone = zone) }
    }

    fun locate(unit: Int, zone: Int) {
        if (!AppCore.enabled() || !AppCore.power()) {
            replaceTop<Overlay.Locator> {
                it.copy(note = context.getString(R.string.error_enable_first))
            }
            return
        }
        try {
            AppCore.locate(unit, zone, 20000)
            Client.refresh(context)
            locating = true
            locatorJob?.cancel()
            locatorJob = scope.launch {
                delay(20000)
                endLocator()
            }
            replaceTop<Overlay.Locator> {
                val note =
                    if (unit > 0)
                        context.getString(R.string.locator_unit_note, unit)
                    else
                        context.getString(
                            R.string.locator_zone_note,
                            it.labels.getOrElse(zone) { "" },
                        )
                it.copy(note = note)
            }
        } catch (error: Exception) {
            replaceTop<Overlay.Locator> {
                it.copy(note = context.getString(R.string.error_locate))
            }
        }
    }

    fun endLocator() {
        locatorJob?.cancel()
        locatorJob = null
        if (locating) {
            locating = false
            AppCore.clearLocator()
            Client.refresh(context)
        }
        replaceTop<Overlay.Locator> {
            it.copy(note = context.getString(R.string.locator_ended))
        }
    }

    fun confirmReset() {
        stack.update { it + Overlay.Reset(context.getString(R.string.reset_title)) }
    }

    fun reset() {
        launchWork {
            AppCore.resetLayout()
            Client.refresh(context)
        }
        stack.update {
            it.filterNot { page -> page is Overlay.Reset || page is Overlay.Calibration }
        }
    }

    private inline fun <reified T : Overlay> replaceTop(change: (T) -> Overlay) {
        stack.update { pages ->
            val top = pages.lastOrNull()
            if (top is T) pages.dropLast(1) + change(top) else pages
        }
    }

    private fun replaceCalibration(change: (Overlay.Calibration) -> Overlay.Calibration) {
        stack.update { pages -> pages.map { if (it is Overlay.Calibration) change(it) else it } }
    }

    private fun launchWork(work: suspend () -> Unit) {
        scope.launch {
            try {
                withContext(Dispatchers.IO) { work() }
                requestStatus()
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                reportFailure(error)
            }
        }
    }

    private fun reportFailure(error: Exception) {
        if (!failureLogged) {
            failureLogged = true
            Log.w("FrameLumaUi", "UI runtime request unavailable", error)
        }
        editState {
            it.copy(message = context.getString(R.string.error_state), connected = false, following = false)
        }
        offerTimings(Timings.NONE)
    }

    fun diagnostics() =
        org.json
            .JSONObject()
            .put("resumed", visible.value)
            .put("state_ready", state.value.layout != null)
            .put("focus", focus)
            .put("focusable_count", 7)
            .put("brightness", state.value.brightness)
            .put("enabled", state.value.enabled)
            .put("period_ms", FramePacer.speedPeriod(state.value.speed))
            .put("reverse", state.value.layout?.reverse ?: false)
            .put(
                "modal",
                org.json
                    .JSONObject()
                    .put("open", stack.value.isNotEmpty())
                    .put("loading", stack.value.lastOrNull() is Overlay.Loading)
                    .put("title", stack.value.lastOrNull()?.title.orEmpty()),
            )
            .put("ui_backend", "compose_tv")
            .put("ui_frames", captured)
            .put("ui_capture_backend", captureBackend)
            .put("background_renderer", backgroundRenderer)
            .put("ui_performance", performanceState.value.name.lowercase())
            .put("ui_gpu_copies", gpuCopies)
            .put("ui_capture_errors", captureErrors)
            .put("ui_state_changes", stateChanges)
            .put("ui_timing_changes", timingChanges)
            .put("ui_preview_changes", previewChanges)
            .put("input_idle_ms", SystemClock.elapsedRealtime() - lastInputAt)

}

private object DevicesProfile {
    fun read(context: Context) =
        try {
            frameluma.runtime.Devices.uiRendering(context)
        } catch (error: Exception) {
            UiRenderingProfile.DEFAULT
        }
}
